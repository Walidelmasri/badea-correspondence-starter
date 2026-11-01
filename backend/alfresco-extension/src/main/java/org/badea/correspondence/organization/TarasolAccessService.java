package org.badea.correspondence.organization;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

/**
 * Tarasol-owned organizational appointments and scoped team access.
 * Every mutation is audited in the existing four Tarasol tables, under a
 * JDBC transaction with a unit row lock to serialize conflicting changes.
 * Does not modify the Alfresco repository or any BADEA_ADDONS object.
 */
public final class TarasolAccessService {
    private static final Set<String> CAPACITIES = Set.of(
        "MANAGER", "ACTING_MANAGER", "OFFICE_MEMBER", "CONSULTANT");
    private static final Set<String> DELEGATION_TYPES = Set.of("LEAVE_COVER", "CASE_HANDOFF");
    private final DataSource dataSource;
    private final EmployeeDirectory employees;

    public TarasolAccessService(DataSource dataSource, EmployeeDirectory employees) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.employees = Objects.requireNonNull(employees, "employees");
    }

    public record Assignment(long id, String unitCode, String employeeId,
        String capacity, String startsAt, String endsAt, String revokedAt, boolean active) { }
    public record TeamEntry(long id, long managerAssignmentId, String employeeId,
        String type, String caseTechnicalId, String startsAt, String endsAt,
        String revokedAt, boolean active) { }
    public record AccessSnapshot(List<Assignment> appointments,
        List<TeamEntry> secretaries, List<TeamEntry> delegations) { }
    public record AppointmentChange(String action, String unitCode, long assignmentId,
        String employeeId, String capacity, OffsetDateTime startsAt,
        OffsetDateTime endsAt, String appointmentRef, String reason) { }
    public record TeamChange(String action, String unitCode, long managerAssignmentId,
        long recordId, String employeeId, String delegationType, String caseTechnicalId,
        OffsetDateTime startsAt, OffsetDateTime endsAt, String reason) { }

    public AccessSnapshot read(String unitCode, String managerEmployeeId, boolean administrator) {
        requireUnitCode(unitCode);
        try (Connection c = dataSource.getConnection()) {
            List<Assignment> appointments = new ArrayList<>();
            String sql = """
                SELECT ASSIGNMENT_ID, UNIT_CODE, EMP_ID, CAPACITY, STARTS_AT, ENDS_AT, REVOKED_AT,
                CASE WHEN REVOKED_AT IS NULL AND STARTS_AT <= SYSTIMESTAMP
                    AND (ENDS_AT IS NULL OR ENDS_AT > SYSTIMESTAMP) THEN 1 ELSE 0 END AS IS_ACTIVE
                FROM TARASOL_UNIT_ASSIGNMENTS WHERE UNIT_CODE = ? ORDER BY ASSIGNMENT_ID DESC
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(8);
                ps.setString(1, unitCode);
                try (ResultSet rows = ps.executeQuery()) {
                    while (rows.next()) {
                        if (administrator || managerEmployeeId.equals(rows.getString("EMP_ID"))) {
                            appointments.add(new Assignment(rows.getLong("ASSIGNMENT_ID"),
                                rows.getString("UNIT_CODE"), rows.getString("EMP_ID"),
                                rows.getString("CAPACITY"), rows.getString("STARTS_AT"),
                                rows.getString("ENDS_AT"), rows.getString("REVOKED_AT"), rows.getInt("IS_ACTIVE") == 1));
                        }
                    }
                }
            }
            List<TeamEntry> secretaries = administrator ? listTeam(c, unitCode, null, true) :
                listTeam(c, unitCode, managerEmployeeId, true);
            List<TeamEntry> delegations = administrator ? listTeam(c, unitCode, null, false) :
                listTeam(c, unitCode, managerEmployeeId, false);
            return new AccessSnapshot(List.copyOf(appointments),
                List.copyOf(secretaries), List.copyOf(delegations));
        } catch (SQLException ex) {
            throw storageError(ex);
        }
    }

    private List<TeamEntry> listTeam(Connection c, String unit, String actor, boolean secretary)
            throws SQLException {
        String table = secretary ? "TARASOL_SECRETARY_ASSIGNMENTS" : "TARASOL_DELEGATIONS";
        String id = secretary ? "SECRETARY_ASSIGNMENT_ID" : "DELEGATION_ID";
        String target = secretary ? "SECRETARY_EMP_ID" : "DELEGATE_EMP_ID";
        String extra = secretary ? "NULL AS DELEGATION_TYPE, NULL AS CASE_TECHNICAL_ID" :
            "d.DELEGATION_TYPE, d.CASE_TECHNICAL_ID";
        String sql = "SELECT d." + id + " AS ID, d.MANAGER_ASSIGNMENT_ID, d." + target
            + " AS TARGET_EMP_ID, " + extra + ", d.STARTS_AT, d.ENDS_AT, d.REVOKED_AT,"
            + " CASE WHEN d.REVOKED_AT IS NULL AND d.STARTS_AT <= SYSTIMESTAMP"
            + " AND (d.ENDS_AT IS NULL OR d.ENDS_AT > SYSTIMESTAMP)"
            + " AND a.REVOKED_AT IS NULL AND a.STARTS_AT <= SYSTIMESTAMP"
            + " AND (a.ENDS_AT IS NULL OR a.ENDS_AT > SYSTIMESTAMP)"
            + " AND a.CAPACITY IN ('MANAGER','ACTING_MANAGER')"
            + " AND u.STATUS = 'A' THEN 1 ELSE 0 END AS IS_ACTIVE FROM "
            + table + " d JOIN TARASOL_UNIT_ASSIGNMENTS a ON a.ASSIGNMENT_ID = d.MANAGER_ASSIGNMENT_ID "
            + "AND a.UNIT_CODE = d.UNIT_CODE "
            + "JOIN TARASOL_ORG_UNITS u ON u.UNIT_CODE = d.UNIT_CODE "
            + "WHERE d.UNIT_CODE = ?" + (actor == null ? "" : " AND a.EMP_ID = ?") +
            " ORDER BY d." + id + " DESC";
        List<TeamEntry> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(8);
            ps.setString(1, unit);
            if (actor != null) ps.setString(2, actor);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    result.add(new TeamEntry(rows.getLong("ID"), rows.getLong("MANAGER_ASSIGNMENT_ID"),
                        rows.getString("TARGET_EMP_ID"), rows.getString("DELEGATION_TYPE"),
                        rows.getString("CASE_TECHNICAL_ID"), rows.getString("STARTS_AT"),
                        rows.getString("ENDS_AT"), rows.getString("REVOKED_AT"), rows.getInt("IS_ACTIVE") == 1));
                }
            }
        }
        return result;
    }

    public long changeAppointment(AppointmentChange change, EmployeeId administrator) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(administrator, "administrator");
        requireUnitCode(change.unitCode());
        if ("APPOINT".equals(change.action()) || "REPLACE_MANAGER".equals(change.action())) {
            if ("REPLACE_MANAGER".equals(change.action())
                    && (!"MANAGER".equals(change.capacity()) || change.assignmentId() <= 0)) {
                throw invalid("Replacement requires a current official manager and new manager capacity");
            }
            if (!CAPACITIES.contains(change.capacity())) throw invalid("Unknown capacity");
            requireActiveEmployee(change.employeeId());
            requireDates(change.startsAt(), change.endsAt(), false);
            if (change.appointmentRef() != null && change.appointmentRef().length() > 120)
                throw invalid("Appointment reference too long");
        } else if (!"REVOKE".equals(change.action())) {
            throw invalid("Unsupported administrative action");
        }
        return transaction(c -> {
            lockUnit(c, change.unitCode());
            if ("REVOKE".equals(change.action())) {
                return revoke(c, "TARASOL_UNIT_ASSIGNMENTS", "ASSIGNMENT_ID",
                    change.assignmentId(), change.unitCode(), administrator.value(), change.reason());
            }
            if ("REPLACE_MANAGER".equals(change.action())) {
                requireCurrentOfficialManager(c, change.unitCode(), change.assignmentId());
                revoke(c, "TARASOL_UNIT_ASSIGNMENTS", "ASSIGNMENT_ID",
                    change.assignmentId(), change.unitCode(), administrator.value(), change.reason());
            }
            checkManagementOverlap(c, change);
            String sql = """
                INSERT INTO TARASOL_UNIT_ASSIGNMENTS
                 (UNIT_CODE, EMP_ID, CAPACITY, STARTS_AT, ENDS_AT,
                  APPOINTMENT_REF, APPROVED_BY_EMP_ID, CREATED_BY_EMP_ID)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"ASSIGNMENT_ID"})) {
                ps.setQueryTimeout(8);
                ps.setString(1, change.unitCode());
                ps.setString(2, change.employeeId());
                ps.setString(3, change.capacity());
                bindDate(ps, 4, change.startsAt());
                bindDate(ps, 5, change.endsAt());
                ps.setString(6, change.appointmentRef());
                ps.setString(7, administrator.value());
                ps.setString(8, administrator.value());
                ps.executeUpdate();
                try (ResultSet ids = ps.getGeneratedKeys()) {
                    if (ids.next()) return ids.getLong(1);
                }
                throw new SQLException("Oracle did not return appointment identity");
            }
        });
    }

    public long changeTeam(TeamChange change, EmployeeId manager) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(manager, "manager");
        requireUnitCode(change.unitCode());
        if (!Set.of("ASSIGN_SECRETARY", "REVOKE_SECRETARY",
                "GRANT_DELEGATION", "REVOKE_DELEGATION").contains(change.action())) {
            throw invalid("Unsupported team action");
        }
        boolean creation = change.action().startsWith("ASSIGN_") || change.action().startsWith("GRANT_");
        if (creation) {
            requireActiveEmployee(change.employeeId());
            if (manager.value().equals(change.employeeId())) throw invalid("Self-assignment is not allowed");
            requireDates(change.startsAt(), change.endsAt(), change.action().equals("GRANT_DELEGATION"));
            if (change.action().equals("GRANT_DELEGATION")) {
                if (!DELEGATION_TYPES.contains(change.delegationType())) throw invalid("Invalid delegation type");
                if ("CASE_HANDOFF".equals(change.delegationType())) {
                    if (change.caseTechnicalId() == null
                            || !change.caseTechnicalId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                        throw invalid("A valid case technical ID is required");
                    }
                } else if (change.caseTechnicalId() != null) {
                    throw invalid("Leave coverage must not select a case");
                }
            }
        }
        return transaction(c -> {
            lockUnit(c, change.unitCode());
            requireCurrentManager(c, change.unitCode(), change.managerAssignmentId(), manager.value());
            if (!creation) {
                boolean secretary = change.action().equals("REVOKE_SECRETARY");
                return revokeTeam(c, secretary, change.recordId(), change.managerAssignmentId(),
                    manager.value(), change.reason());
            }
            boolean secretary = change.action().equals("ASSIGN_SECRETARY");
            ensureNoDuplicateTeam(c, change, secretary);
            String table = secretary ? "TARASOL_SECRETARY_ASSIGNMENTS" : "TARASOL_DELEGATIONS";
            String id = secretary ? "SECRETARY_ASSIGNMENT_ID" : "DELEGATION_ID";
            String target = secretary ? "SECRETARY_EMP_ID" : "DELEGATE_EMP_ID";
            String sql = "INSERT INTO " + table + " (UNIT_CODE, MANAGER_ASSIGNMENT_ID, "
                + target + ", STARTS_AT, ENDS_AT, APPROVED_BY_EMP_ID, CREATED_BY_EMP_ID"
                + (secretary ? "" : ", DELEGATION_TYPE, CASE_TECHNICAL_ID")
                + ") VALUES (?, ?, ?, ?, ?, ?, ?" + (secretary ? "" : ", ?, ?") + ")";
            try (PreparedStatement ps = c.prepareStatement(sql, new String[]{id})) {
                ps.setQueryTimeout(8);
                ps.setString(1, change.unitCode());
                ps.setLong(2, change.managerAssignmentId());
                ps.setString(3, change.employeeId());
                bindDate(ps, 4, change.startsAt());
                bindDate(ps, 5, change.endsAt());
                ps.setString(6, manager.value());
                ps.setString(7, manager.value());
                if (!secretary) {
                    ps.setString(8, change.delegationType());
                    ps.setString(9, change.caseTechnicalId());
                }
                ps.executeUpdate();
                try (ResultSet ids = ps.getGeneratedKeys()) {
                    if (ids.next()) return ids.getLong(1);
                }
                throw new SQLException("Oracle did not return team assignment identity");
            }
        });
    }

    private void requireActiveEmployee(String employeeId) {
        if (employeeId == null || employeeId.isBlank()
                || employees.findActiveByEmployeeId(new EmployeeId(employeeId)).isEmpty()) {
            throw invalid("Employee must be active in BADEA directory");
        }
    }

    private static void requireCurrentOfficialManager(Connection c, String unit, long id)
            throws SQLException {
        String sql = """
            SELECT 1 FROM TARASOL_UNIT_ASSIGNMENTS
            WHERE ASSIGNMENT_ID = ? AND UNIT_CODE = ? AND CAPACITY = 'MANAGER'
              AND REVOKED_AT IS NULL AND STARTS_AT <= SYSTIMESTAMP
              AND (ENDS_AT IS NULL OR ENDS_AT > SYSTIMESTAMP)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(8);
            ps.setLong(1, id);
            ps.setString(2, unit);
            try (ResultSet rows = ps.executeQuery()) {
                if (!rows.next()) throw invalid("Official manager is no longer active");
            }
        }
    }

    private static void checkManagementOverlap(Connection c, AppointmentChange change) throws SQLException {
        String sql = """
            SELECT EMP_ID, CAPACITY FROM TARASOL_UNIT_ASSIGNMENTS
            WHERE UNIT_CODE = ? AND REVOKED_AT IS NULL
              AND (? IS NULL OR STARTS_AT < ?)
              AND (ENDS_AT IS NULL OR ENDS_AT > ?)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(8);
            ps.setString(1, change.unitCode());
            bindDate(ps, 2, change.endsAt());
            bindDate(ps, 3, change.endsAt());
            bindDate(ps, 4, change.startsAt());
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    String currentCapacity = rows.getString("CAPACITY");
                    boolean same = change.employeeId().equals(rows.getString("EMP_ID"));
                    boolean managerCollision = ("MANAGER".equals(change.capacity())
                            && ("MANAGER".equals(currentCapacity) || "ACTING_MANAGER".equals(currentCapacity)))
                        || ("ACTING_MANAGER".equals(change.capacity()) && "MANAGER".equals(currentCapacity));
                    if ((same && change.capacity().equals(currentCapacity)) || managerCollision) {
                        throw invalid("Appointment conflicts with another non-revoked appointment in this unit");
                    }
                }
            }
        }
    }

    private static void ensureNoDuplicateTeam(Connection c, TeamChange change, boolean secretary)
            throws SQLException {
        String table = secretary ? "TARASOL_SECRETARY_ASSIGNMENTS" : "TARASOL_DELEGATIONS";
        String target = secretary ? "SECRETARY_EMP_ID" : "DELEGATE_EMP_ID";
        String sql = "SELECT 1 FROM " + table +
            " WHERE MANAGER_ASSIGNMENT_ID = ? AND " + target + " = ? AND REVOKED_AT IS NULL"
            + " AND (? IS NULL OR STARTS_AT < ?) AND (ENDS_AT IS NULL OR ENDS_AT > ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(8);
            ps.setLong(1, change.managerAssignmentId());
            ps.setString(2, change.employeeId());
            bindDate(ps, 3, change.endsAt());
            bindDate(ps, 4, change.endsAt());
            bindDate(ps, 5, change.startsAt());
            try (ResultSet rows = ps.executeQuery()) {
                if (rows.next()) throw invalid("Overlapping secretary/delegation assignment already exists");
            }
        }
    }

    private static void requireCurrentManager(Connection c, String unit, long assignmentId,
            String employeeId) throws SQLException {
        if (assignmentId <= 0) throw invalid("Manager assignment must be selected");
        String sql = """
            SELECT 1 FROM TARASOL_UNIT_ASSIGNMENTS
            WHERE ASSIGNMENT_ID = ? AND UNIT_CODE = ? AND EMP_ID = ?
              AND CAPACITY IN ('MANAGER','ACTING_MANAGER')
              AND REVOKED_AT IS NULL AND STARTS_AT <= SYSTIMESTAMP
              AND (ENDS_AT IS NULL OR ENDS_AT > SYSTIMESTAMP)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(8);
            ps.setLong(1, assignmentId);
            ps.setString(2, unit);
            ps.setString(3, employeeId);
            try (ResultSet rows = ps.executeQuery()) {
                if (!rows.next()) throw new CorrespondenceAuthorizationException(
                    "No active managerial appointment authorizes this team change");
            }
        }
    }

    private static long revokeTeam(Connection c, boolean secretary, long recordId, long assignmentId,
            String actor, String reason) throws SQLException {
        if (recordId <= 0) throw invalid("Record ID required");
        String table = secretary ? "TARASOL_SECRETARY_ASSIGNMENTS" : "TARASOL_DELEGATIONS";
        String id = secretary ? "SECRETARY_ASSIGNMENT_ID" : "DELEGATION_ID";
        String sql = "UPDATE " + table + " SET REVOKED_AT = SYSTIMESTAMP,"
            + " REVOKED_BY_EMP_ID = ?, REVOKE_REASON = ? WHERE " + id + " = ?"
            + " AND MANAGER_ASSIGNMENT_ID = ? AND REVOKED_AT IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(8);
            ps.setString(1, actor);
            ps.setString(2, normalizeReason(reason));
            ps.setLong(3, recordId);
            ps.setLong(4, assignmentId);
            if (ps.executeUpdate() != 1) throw invalid("Record was already revoked or is not yours");
            return recordId;
        }
    }

    private static long revoke(Connection c, String table, String column, long id,
            String unit, String actor, String reason) throws SQLException {
        if (id <= 0) throw invalid("Appointment ID required");
        String sql = "UPDATE " + table + " SET REVOKED_AT = SYSTIMESTAMP,"
            + " REVOKED_BY_EMP_ID = ?, REVOKE_REASON = ? WHERE " + column + " = ?"
            + " AND UNIT_CODE = ? AND REVOKED_AT IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(8);
            ps.setString(1, actor);
            ps.setString(2, normalizeReason(reason));
            ps.setLong(3, id);
            ps.setString(4, unit);
            if (ps.executeUpdate() != 1) throw invalid("Appointment already revoked or wrong unit");
            return id;
        }
    }

    private static void lockUnit(Connection c, String unit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT UNIT_CODE FROM TARASOL_ORG_UNITS WHERE UNIT_CODE = ? AND STATUS = 'A' FOR UPDATE")) {
            ps.setQueryTimeout(8);
            ps.setString(1, unit);
            try (ResultSet rows = ps.executeQuery()) {
                if (!rows.next()) throw invalid("Organizational unit is inactive or missing");
            }
        }
    }

    private static void requireUnitCode(String code) {
        if (code == null || !code.matches("[0-9]{3}")) throw invalid("Invalid unit code");
    }

    private static void requireDates(OffsetDateTime start, OffsetDateTime end, boolean endRequired) {
        if (start == null || (endRequired && end == null)
                || (end != null && !end.isAfter(start))) {
            throw invalid("Invalid appointment or delegation interval");
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw invalid("A revocation reason (maximum 500 characters) is required");
        return reason.trim();
    }

    private static void bindDate(PreparedStatement ps, int index, OffsetDateTime value)
            throws SQLException {
        if (value == null) ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else ps.setObject(index, value);
    }

    @FunctionalInterface private interface SqlWork<T> { T run(Connection c) throws SQLException; }
    private <T> T transaction(SqlWork<T> work) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { c.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                throw error;
            }
        } catch (SQLException ex) {
            throw storageError(ex);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalStateException storageError(SQLException error) {
        return new IllegalStateException("Tarasol organizational storage operation failed", error);
    }
}

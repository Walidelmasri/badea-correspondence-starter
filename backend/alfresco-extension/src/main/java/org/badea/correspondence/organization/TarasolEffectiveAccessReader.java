package org.badea.correspondence.organization;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeId;

/**
 * Read-only, fail-closed Tarasol assignment checks. Deliberately separate from
 * Alfresco group resolution: being a secretary or delegate must NOT promote an
 * employee to the unrestricted DIRECTOR group or department mailbox.
 *
 * Callers must source correspondence ID, originating/assigned unit, and creation
 * time from the Alfresco repository, never from an HTTP request body.
 */
public final class TarasolEffectiveAccessReader {
    private static final Log LOGGER = LogFactory.getLog(TarasolEffectiveAccessReader.class);
    private static final String ACTIVE_PARENT = """
        JOIN TARASOL_UNIT_ASSIGNMENTS manager
          ON manager.ASSIGNMENT_ID = access.MANAGER_ASSIGNMENT_ID
         AND manager.UNIT_CODE = access.UNIT_CODE
        JOIN TARASOL_ORG_UNITS unit ON unit.UNIT_CODE = access.UNIT_CODE
        WHERE unit.STATUS = 'A'
          AND manager.CAPACITY IN ('MANAGER', 'ACTING_MANAGER')
          AND manager.REVOKED_AT IS NULL
          AND manager.STARTS_AT <= SYSTIMESTAMP
          AND (manager.ENDS_AT IS NULL OR manager.ENDS_AT > SYSTIMESTAMP)
          AND access.REVOKED_AT IS NULL
          AND access.STARTS_AT <= SYSTIMESTAMP
          AND (access.ENDS_AT IS NULL OR access.ENDS_AT > SYSTIMESTAMP)
        """;

    private static final String SECRETARY_UNITS_SQL = """
        SELECT DISTINCT access.UNIT_CODE, unit.NAME_EN, unit.NAME_AR
        FROM TARASOL_SECRETARY_ASSIGNMENTS access
        JOIN TARASOL_ORG_UNITS unit ON unit.UNIT_CODE = access.UNIT_CODE
        WHERE unit.STATUS = 'A'
          AND access.REVOKED_AT IS NULL
          AND access.STARTS_AT <= SYSTIMESTAMP
          AND (access.ENDS_AT IS NULL OR access.ENDS_AT > SYSTIMESTAMP)
          AND access.SECRETARY_EMP_ID = ?
        ORDER BY access.UNIT_CODE
        """;

    private static final String SECRETARY_SQL = """
        SELECT access.SECRETARY_ASSIGNMENT_ID
        FROM TARASOL_SECRETARY_ASSIGNMENTS access
        JOIN TARASOL_ORG_UNITS unit ON unit.UNIT_CODE = access.UNIT_CODE
        WHERE unit.STATUS = 'A'
          AND access.REVOKED_AT IS NULL
          AND access.STARTS_AT <= SYSTIMESTAMP
          AND (access.ENDS_AT IS NULL OR access.ENDS_AT > SYSTIMESTAMP)
          AND access.SECRETARY_EMP_ID = ?
          AND access.UNIT_CODE = ?
        """;

    private static final String DELEGATION_UNITS_SQL = """
        SELECT DISTINCT access.UNIT_CODE, unit.NAME_EN, unit.NAME_AR
        FROM TARASOL_DELEGATIONS access
        """ + ACTIVE_PARENT + """
          AND access.DELEGATE_EMP_ID = ?
          AND manager.EMP_ID <> access.DELEGATE_EMP_ID
        ORDER BY access.UNIT_CODE
        """;

    private static final String DELEGATION_SQL = """
        SELECT access.DELEGATION_ID, access.MANAGER_ASSIGNMENT_ID,
               access.DELEGATION_TYPE
        FROM TARASOL_DELEGATIONS access
        """ + ACTIVE_PARENT + """
          AND access.DELEGATE_EMP_ID = ?
          AND access.UNIT_CODE = ?
          AND manager.EMP_ID <> access.DELEGATE_EMP_ID
          AND (
            (access.DELEGATION_TYPE = 'CASE_HANDOFF'
                AND access.CASE_TECHNICAL_ID = ?)
            OR (access.DELEGATION_TYPE = 'LEAVE_COVER'
                AND access.CASE_TECHNICAL_ID IS NULL
                AND access.STARTS_AT <= ?)
          )
        ORDER BY access.DELEGATION_ID
        """;

    private static final String HANDOFF_CASES_SQL = """
        SELECT DISTINCT access.CASE_TECHNICAL_ID
        FROM TARASOL_DELEGATIONS access
        """ + ACTIVE_PARENT + """
          AND access.DELEGATE_EMP_ID = ?
          AND access.UNIT_CODE = ?
          AND manager.EMP_ID <> access.DELEGATE_EMP_ID
          AND access.DELEGATION_TYPE = 'CASE_HANDOFF'
          AND access.CASE_TECHNICAL_ID IS NOT NULL
        ORDER BY access.CASE_TECHNICAL_ID
        """;

    /** Enumeration is restricted to active exact-case handoffs, not all unit correspondence. */
    public List<String> findCurrentCaseHandoffs(EmployeeId actor, DepartmentCode unit) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(unit, "unit");
        if (!isActiveEmployee(actor)) return List.of();
        List<String> ids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(HANDOFF_CASES_SQL)) {
            query.setQueryTimeout(8);
            query.setString(1, actor.value());
            query.setString(2, unit.value());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next() && ids.size() < 100) {
                    String id = rows.getString("CASE_TECHNICAL_ID");
                    if (id != null) ids.add(id);
                }
            }
            return List.copyOf(ids);
        } catch (SQLException failure) {
            throw new IllegalStateException("Tarasol delegated case discovery unavailable", failure);
        }
    }

    private final DataSource dataSource;
    private final EmployeeDirectory employees;

    public TarasolEffectiveAccessReader(DataSource dataSource, EmployeeDirectory employees) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.employees = Objects.requireNonNull(employees, "employees");
    }

    public record SecretaryUnit(String code, String nameEnglish, String nameArabic) { }

    /** A secretary belongs to an active unit, irrespective of manager vacancies or leave. */
    public List<SecretaryUnit> findCurrentSecretaryUnits(EmployeeId actor) {
        Objects.requireNonNull(actor, "actor");
        if (!isActiveEmployee(actor)) {
            // Do not log employee identifiers or credentials.
            LOGGER.warn("Tarasol secretary profile: active employee directory lookup returned no row");
            return List.of();
        }
        List<SecretaryUnit> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SECRETARY_UNITS_SQL)) {
            ps.setQueryTimeout(8);
            ps.setString(1, actor.value());
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    result.add(new SecretaryUnit(rows.getString("UNIT_CODE"),
                        rows.getString("NAME_EN"), rows.getString("NAME_AR")));
                }
            }
            if (result.isEmpty()) {
                LOGGER.warn("Tarasol secretary profile: no effective Oracle secretary unit returned");
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new IllegalStateException("Tarasol secretary authorization unavailable", failure);
        }
    }

    /** Only authorizes secretary initiation/submission, NOT manager approval. */
    public boolean hasActiveSecretaryAssignment(EmployeeId actor, DepartmentCode unit) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(unit, "unit");
        if (!isActiveEmployee(actor)) return false;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SECRETARY_SQL)) {
            ps.setQueryTimeout(8);
            ps.setString(1, actor.value());
            ps.setString(2, unit.value());
            try (ResultSet result = ps.executeQuery()) {
                return result.next();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Tarasol secretary authorization unavailable", failure);
        }
    }

    /** Navigation only. The individual case must still pass findActiveDelegation. */
    public List<SecretaryUnit> findCurrentDelegationUnits(EmployeeId actor) {
        Objects.requireNonNull(actor, "actor");
        if (!isActiveEmployee(actor)) return List.of();
        List<SecretaryUnit> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(DELEGATION_UNITS_SQL)) {
            ps.setQueryTimeout(8);
            ps.setString(1, actor.value());
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    result.add(new SecretaryUnit(rows.getString("UNIT_CODE"),
                        rows.getString("NAME_EN"), rows.getString("NAME_AR")));
                }
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new IllegalStateException("Tarasol delegation directory unavailable", failure);
        }
    }

    public record Delegation(long id, long parentManagerAssignmentId, String type) { }

    /**
     * Returns only a grant currently valid for the authenticated employee and
     * repository-sourced case. Exact CASE_HANDOFF is an explicit grant even for
     * an older case; LEAVE_COVER grants no automatic historical access.
     * This is a scoped lookup, NOT an unrestricted Director role.
     */
    public Optional<Delegation> findActiveDelegation(
            EmployeeId actor,
            DepartmentCode unit,
            String persistedTechnicalId,
            OffsetDateTime persistedCreatedAt) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(persistedCreatedAt, "persistedCreatedAt");
        if (persistedTechnicalId == null || !persistedTechnicalId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Invalid persisted correspondence ID");
        }
        if (!isActiveEmployee(actor)) return Optional.empty();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(DELEGATION_SQL)) {
            ps.setQueryTimeout(8);
            ps.setString(1, actor.value());
            ps.setString(2, unit.value());
            ps.setString(3, persistedTechnicalId);
            ps.setObject(4, persistedCreatedAt);
            try (ResultSet result = ps.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new Delegation(result.getLong("DELEGATION_ID"),
                    result.getLong("MANAGER_ASSIGNMENT_ID"),
                    result.getString("DELEGATION_TYPE")));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Tarasol delegated authorization unavailable", failure);
        }
    }

    private boolean isActiveEmployee(EmployeeId actor) {
        // The employee may have left after an assignment was originally created.
        return employees.findActiveByEmployeeId(actor).isPresent();
    }
}

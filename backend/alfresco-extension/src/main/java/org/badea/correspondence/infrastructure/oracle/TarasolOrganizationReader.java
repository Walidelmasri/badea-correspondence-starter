package org.badea.correspondence.infrastructure.oracle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryException;
import org.badea.correspondence.directory.EmployeeId;

/**
 * Read-only Stage 1 adapter for Tarasol-owned organization tables.
 * Not registered in Spring, used by no live mailbox or authorization endpoint.
 */
public final class TarasolOrganizationReader {
    private static final String UNITS_SQL = """
        SELECT UNIT_CODE, NAME_EN, NAME_AR, NAME_FR, UNIT_TYPE
        FROM TARASOL_ORG_UNITS
        WHERE STATUS = 'A'
        ORDER BY UNIT_CODE
        """;

    private static final String ASSIGNMENTS_SQL = """
        SELECT a.ASSIGNMENT_ID, a.UNIT_CODE, a.EMP_ID, a.CAPACITY
        FROM TARASOL_UNIT_ASSIGNMENTS a
        JOIN TARASOL_ORG_UNITS u ON u.UNIT_CODE = a.UNIT_CODE
        WHERE a.EMP_ID = ?
          AND u.STATUS = 'A'
          AND a.REVOKED_AT IS NULL
          AND a.STARTS_AT <= SYSTIMESTAMP
          AND (a.ENDS_AT IS NULL OR a.ENDS_AT > SYSTIMESTAMP)
        ORDER BY a.UNIT_CODE, a.ASSIGNMENT_ID
        """;

    // Read-only Stage 2A parity probe. Multiple acting managers are returned
    // independently; no legacy single-manager API is used for authorization.
    private static final String ACTIVE_UNIT_MANAGERS_SQL = """
        SELECT a.ASSIGNMENT_ID, a.UNIT_CODE, a.EMP_ID, a.CAPACITY
        FROM TARASOL_UNIT_ASSIGNMENTS a
        JOIN TARASOL_ORG_UNITS u ON u.UNIT_CODE = a.UNIT_CODE
        WHERE a.UNIT_CODE = ?
          AND u.STATUS = 'A'
          AND a.CAPACITY IN ('MANAGER', 'ACTING_MANAGER')
          AND a.REVOKED_AT IS NULL
          AND a.STARTS_AT <= SYSTIMESTAMP
          AND (a.ENDS_AT IS NULL OR a.ENDS_AT > SYSTIMESTAMP)
        ORDER BY a.ASSIGNMENT_ID
        """;

    private static final String MANAGER_WORKSPACES_SQL = """
        SELECT a.ASSIGNMENT_ID, a.UNIT_CODE, a.CAPACITY,
               u.NAME_EN, u.NAME_AR
        FROM TARASOL_UNIT_ASSIGNMENTS a
        JOIN TARASOL_ORG_UNITS u ON u.UNIT_CODE = a.UNIT_CODE
        WHERE a.EMP_ID = ?
          AND a.CAPACITY IN ('MANAGER', 'ACTING_MANAGER')
          AND u.STATUS = 'A'
          AND a.REVOKED_AT IS NULL
          AND a.STARTS_AT <= SYSTIMESTAMP
          AND (a.ENDS_AT IS NULL OR a.ENDS_AT > SYSTIMESTAMP)
        ORDER BY a.UNIT_CODE, a.ASSIGNMENT_ID
        """;

    private final DataSource dataSource;

    public TarasolOrganizationReader(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public List<ManagerWorkspace> findCurrentManagerWorkspaces(EmployeeId employeeId) {
        Objects.requireNonNull(employeeId, "employeeId");
        List<ManagerWorkspace> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(MANAGER_WORKSPACES_SQL)) {
            query.setQueryTimeout(8);
            query.setString(1, employeeId.value());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(new ManagerWorkspace(
                        rows.getLong("ASSIGNMENT_ID"),
                        new DepartmentCode(rows.getString("UNIT_CODE")),
                        rows.getString("NAME_EN"),
                        rows.getString("NAME_AR"),
                        rows.getString("CAPACITY")
                    ));
                }
            }
        } catch (SQLException failure) {
            throw new DepartmentDirectoryException(
                "Failed to load Tarasol manager workspaces", failure);
        }
        return List.copyOf(result);
    }

    public record ManagerWorkspace(
        long assignmentId, DepartmentCode code, String nameEnglish,
        String nameArabic, String capacity
    ) {
        public ManagerWorkspace {
            if (assignmentId < 1) throw new IllegalArgumentException("Invalid assignment ID");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(nameEnglish, "nameEnglish");
            if (!"MANAGER".equals(capacity) && !"ACTING_MANAGER".equals(capacity)) {
                throw new IllegalArgumentException("Not a manager capacity");
            }
        }
    }

    public List<Unit> findActiveUnits() {
        List<Unit> units = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(UNITS_SQL)) {
            query.setQueryTimeout(8);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    units.add(new Unit(
                        new DepartmentCode(rows.getString("UNIT_CODE")),
                        rows.getString("NAME_EN"),
                        rows.getString("NAME_AR"),
                        rows.getString("NAME_FR"),
                        rows.getString("UNIT_TYPE")
                    ));
                }
            }
        } catch (SQLException failure) {
            throw new DepartmentDirectoryException(
                "Failed to read Tarasol organizational units", failure);
        }
        return List.copyOf(units);
    }

    public List<Assignment> findActiveAssignments(EmployeeId employeeId) {
        Objects.requireNonNull(employeeId, "employeeId");
        List<Assignment> assignments = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(ASSIGNMENTS_SQL)) {
            query.setQueryTimeout(8);
            query.setString(1, employeeId.value());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    assignments.add(new Assignment(
                        rows.getLong("ASSIGNMENT_ID"),
                        new DepartmentCode(rows.getString("UNIT_CODE")),
                        new EmployeeId(rows.getString("EMP_ID")),
                        rows.getString("CAPACITY")
                    ));
                }
            }
        } catch (SQLException failure) {
            throw new DepartmentDirectoryException(
                "Failed to read Tarasol unit assignments", failure);
        }
        return List.copyOf(assignments);
    }

    /**
     * All current managers for the requested unit, including multiple acting
     * managers when a vacancy exists. No role is granted by this method.
     */
    public List<Assignment> findActiveManagerAssignmentsForUnit(DepartmentCode unitCode) {
        Objects.requireNonNull(unitCode, "unitCode");
        List<Assignment> assignments = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(ACTIVE_UNIT_MANAGERS_SQL)) {
            query.setQueryTimeout(8);
            query.setString(1, unitCode.value());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    assignments.add(new Assignment(
                        rows.getLong("ASSIGNMENT_ID"),
                        new DepartmentCode(rows.getString("UNIT_CODE")),
                        new EmployeeId(rows.getString("EMP_ID")),
                        rows.getString("CAPACITY")
                    ));
                }
            }
        } catch (SQLException failure) {
            throw new DepartmentDirectoryException(
                "Failed to read Tarasol unit manager assignments", failure);
        }
        return List.copyOf(assignments);
    }

    public record Unit(
        DepartmentCode code,
        String nameEnglish,
        String nameArabic,
        String nameFrench,
        String type
    ) {
        public Unit {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(nameEnglish, "nameEnglish");
            Objects.requireNonNull(type, "type");
        }
    }

    public record Assignment(
        long assignmentId,
        DepartmentCode unitCode,
        EmployeeId employeeId,
        String capacity
    ) {
        public Assignment {
            if (assignmentId < 1) {
                throw new IllegalArgumentException("assignmentId must be positive");
            }
            Objects.requireNonNull(unitCode, "unitCode");
            Objects.requireNonNull(employeeId, "employeeId");
            Objects.requireNonNull(capacity, "capacity");
        }
    }
}

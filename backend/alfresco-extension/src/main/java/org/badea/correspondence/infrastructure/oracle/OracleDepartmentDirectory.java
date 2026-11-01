package org.badea.correspondence.infrastructure.oracle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectory;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DepartmentDirectoryException;

public final class OracleDepartmentDirectory
        implements DepartmentDirectory {

    private static final String SELECT_ALL_SQL = """
            SELECT
                UNIT_CODE AS DEPARTMENT_CODE,
                NAME_EN AS NAME,
                NAME_AR AS NAME_ARABIC
            FROM TARASOL_ORG_UNITS
            ORDER BY UNIT_CODE
            """;

    private static final String SELECT_BY_CODE_SQL = """
            SELECT
                UNIT_CODE AS DEPARTMENT_CODE,
                NAME_EN AS NAME,
                NAME_AR AS NAME_ARABIC
            FROM TARASOL_ORG_UNITS
            WHERE UNIT_CODE = ?
            """;

    private static final String SELECT_ACTIVE_HEAD_SQL = """
            SELECT a.EMP_ID AS DEPT_MANAGER_ID
            FROM TARASOL_UNIT_ASSIGNMENTS a
            JOIN TARASOL_ORG_UNITS u ON u.UNIT_CODE = a.UNIT_CODE
            WHERE a.UNIT_CODE = ? AND u.STATUS = 'A'
              AND a.CAPACITY IN ('MANAGER', 'ACTING_MANAGER')
              AND a.REVOKED_AT IS NULL
              AND a.STARTS_AT <= SYSTIMESTAMP
              AND (a.ENDS_AT IS NULL OR a.ENDS_AT > SYSTIMESTAMP)
            ORDER BY a.ASSIGNMENT_ID
            """;

    @Override
    public java.util.List<org.badea.correspondence.directory.EmployeeId> findActiveManagerEmployeeIds(
            DepartmentCode code) {
        Objects.requireNonNull(code, "code");
        var managers = new java.util.ArrayList<org.badea.correspondence.directory.EmployeeId>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_HEAD_SQL)) {
            statement.setQueryTimeout(8);
            statement.setString(1, code.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String raw = rows.getString("DEPT_MANAGER_ID");
                    if (raw == null || raw.isBlank()) {
                        throw new DepartmentDirectoryException("Missing Tarasol manager employee ID for " + code.value());
                    }
                    managers.add(new org.badea.correspondence.directory.EmployeeId(raw.trim()));
                }
            }
        } catch (SQLException failure) {
            throw new DepartmentDirectoryException("Failed to resolve active Tarasol managers", failure);
        }
        // Inconsistent duplicate assignments must never cause duplicate notifications.
        if (new java.util.HashSet<>(managers).size() != managers.size()) {
            throw new DepartmentDirectoryException("Duplicate active Tarasol manager for " + code.value());
        }
        return List.copyOf(managers);
    }

    @Override
    public java.util.Optional<org.badea.correspondence.directory.EmployeeId> findActiveManagerEmployeeId(
            DepartmentCode code) {
        var managers = findActiveManagerEmployeeIds(code);
        if (managers.size() > 1) {
            throw new DepartmentDirectoryException(
                "Multiple active Tarasol managers for unit " + code.value() + "; use the multi-manager API");
        }
        return managers.stream().findFirst();
    }

    private final DataSource dataSource;

    public OracleDepartmentDirectory(
            DataSource dataSource) {

        this.dataSource =
                Objects.requireNonNull(
                        dataSource,
                        "dataSource must not be null");
    }

    @Override
    public List<DepartmentDirectoryEntry>
            findAllCorrespondenceDepartments() {

        List<DepartmentDirectoryEntry> departments =
                new ArrayList<>();

        try (
                Connection connection =
                        dataSource.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                SELECT_ALL_SQL);

                ResultSet resultSet =
                        statement.executeQuery()) {

            while (resultSet.next()) {

                DepartmentDirectoryEntry department =
                        mapCorrespondenceDepartment(
                                resultSet);

                if (department != null) {
                    departments.add(
                            department);
                }
            }

            return List.copyOf(
                    departments);

        } catch (SQLException exception) {

            throw new DepartmentDirectoryException(
                    "Failed to load correspondence departments from Oracle.",
                    exception);
        }
    }

    @Override
    public Optional<DepartmentDirectoryEntry> findByCode(
            DepartmentCode code) {

        Objects.requireNonNull(
                code,
                "code must not be null");

        try (
                Connection connection =
                        dataSource.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                SELECT_BY_CODE_SQL)) {

            statement.setString(
                    1,
                    code.value());

            try (
                    ResultSet resultSet =
                            statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                DepartmentDirectoryEntry department =
                        mapRequiredDepartment(
                                resultSet);

                if (resultSet.next()) {
                    throw new DepartmentDirectoryException(
                            "Multiple departments exist for department code: "
                                    + code.value());
                }

                return Optional.of(
                        department);
            }

        } catch (SQLException exception) {

            throw new DepartmentDirectoryException(
                    "Failed to load department "
                            + code.value()
                            + " from Oracle.",
                    exception);
        }
    }

    private static DepartmentDirectoryEntry
            mapCorrespondenceDepartment(
                    ResultSet resultSet)
                    throws SQLException {

        String rawCode =
                resultSet.getString(
                        "DEPARTMENT_CODE");

        DepartmentCode code;

        try {
            code =
                    new DepartmentCode(
                            rawCode);
        } catch (IllegalArgumentException exception) {

            /* Legacy nonnumeric codes are not correspondence units. */
            return null;
        }

        return new DepartmentDirectoryEntry(
                code,
                requiredText(
                        resultSet.getString(
                                "NAME"),
                        "NAME",
                        code),
                optionalText(
                        resultSet.getString(
                                "NAME_ARABIC")));
    }

    private static DepartmentDirectoryEntry
            mapRequiredDepartment(
                    ResultSet resultSet)
                    throws SQLException {

        String rawCode =
                resultSet.getString(
                        "DEPARTMENT_CODE");

        DepartmentCode code;

        try {
            code =
                    new DepartmentCode(
                            rawCode);
        } catch (IllegalArgumentException exception) {

            throw new DepartmentDirectoryException(
                    "Oracle returned an invalid correspondence department code: "
                            + rawCode,
                    exception);
        }

        return new DepartmentDirectoryEntry(
                code,
                requiredText(
                        resultSet.getString(
                                "NAME"),
                        "NAME",
                        code),
                optionalText(
                        resultSet.getString(
                                "NAME_ARABIC")));
    }

    private static String requiredText(
            String value,
            String columnName,
            DepartmentCode code) {

        if (value == null
                || value.isBlank()) {

            throw new DepartmentDirectoryException(
                    "Department "
                            + code.value()
                            + " has no valid "
                            + columnName
                            + ".");
        }

        return value.trim();
    }

    private static String optionalText(
            String value) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }
}
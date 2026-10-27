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
                DEPARTMENT_CODE,
                NAME,
                NAME_ARABIC
            FROM BADEA_ADDONS.COR_DEPARTMENTS
            ORDER BY DEPARTMENT_CODE
            """;

    private static final String SELECT_BY_CODE_SQL = """
            SELECT
                DEPARTMENT_CODE,
                NAME,
                NAME_ARABIC
            FROM BADEA_ADDONS.COR_DEPARTMENTS
            WHERE DEPARTMENT_CODE = ?
            """;

    private static final String SELECT_ACTIVE_HEAD_SQL = """
            SELECT DEPT_MANAGER_ID
            FROM BADEA_ADDONS.COR_DEPARTMENTS
            WHERE DEPARTMENT_CODE = ? AND STATUS = 'A'
            """;

    @Override
    public java.util.Optional<org.badea.correspondence.directory.EmployeeId> findActiveManagerEmployeeId(
            DepartmentCode code) {
        Objects.requireNonNull(code, "code");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE_HEAD_SQL)) {
            statement.setString(1, code.value());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return java.util.Optional.empty();
                String rawId = rows.getString("DEPT_MANAGER_ID");
                if (rows.next()) {
                    throw new DepartmentDirectoryException("Multiple active managers for department " + code.value());
                }
                if (rawId == null || rawId.isBlank()) return java.util.Optional.empty();
                try {
                    return java.util.Optional.of(new org.badea.correspondence.directory.EmployeeId(rawId.trim()));
                } catch (IllegalArgumentException invalid) {
                    throw new DepartmentDirectoryException("Invalid active manager ID for department " + code.value(), invalid);
                }
            }
        } catch (SQLException failure) {
            throw new DepartmentDirectoryException("Failed to resolve active department manager", failure);
        }
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

            /*
             * COR_DEPARTMENTS also contains legacy alphabetic
             * department codes. They are not part of the current
             * correspondence-routing domain.
             */
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
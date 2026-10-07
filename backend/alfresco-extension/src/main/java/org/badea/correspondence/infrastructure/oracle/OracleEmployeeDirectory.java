package org.badea.correspondence.infrastructure.oracle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.badea.correspondence.directory.EmployeeId;

/**
 * Oracle-backed implementation of BADEA's employee directory.
 *
 * <p>Authentication remains Alfresco/AD-owned. This component only resolves
 * the authenticated directory username against BADEA's Oracle employee
 * directory and returns the canonical EMP_ID and employee metadata.</p>
 */
public final class OracleEmployeeDirectory implements EmployeeDirectory {

    private static final String FIND_ACTIVE_BY_USERNAME_SQL = """
        SELECT
            e.EMP_ID AS EMPLOYEE_ID,
            e.USERID AS DIRECTORY_USER_ID,
            e.NAME_ENG AS EMPLOYEE_NAME_ENGLISH,
            e.NAME_ARABIC AS EMPLOYEE_NAME_ARABIC,
            e.DEPT_CODE AS EMPLOYEE_DEPARTMENT_CODE,
            d.DEPARTMENT_CODE AS DEPARTMENT_CODE,
            d.NAME AS DEPARTMENT_NAME_ENGLISH,
            d.NAME_ARABIC AS DEPARTMENT_NAME_ARABIC
        FROM BADEA_ADDONS.EMPLOYEES e
        LEFT JOIN BADEA_ADDONS.COR_DEPARTMENTS d
            ON d.DEPARTMENT_CODE = e.DEPT_CODE
        WHERE e.ACTIVE = 1
          AND LOWER(
                CASE
                    WHEN INSTR(TRIM(e.USERID), '@') > 0
                    THEN SUBSTR(
                        TRIM(e.USERID),
                        1,
                        INSTR(TRIM(e.USERID), '@') - 1
                    )
                    ELSE TRIM(e.USERID)
                END
              ) = ?
        """;

    private final DataSource dataSource;

    public OracleEmployeeDirectory(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(
            dataSource,
            "Data source must not be null."
        );
    }

    @Override
    public Optional<EmployeeDirectoryEntry> findActiveByUsername(
        DirectoryUsername username
    ) {
        Objects.requireNonNull(
            username,
            "Directory username must not be null."
        );

        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(FIND_ACTIVE_BY_USERNAME_SQL)
        ) {
            statement.setString(1, username.value());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                EmployeeDirectoryEntry employee =
                    mapEmployee(resultSet, username);

                if (resultSet.next()) {
                    throw new EmployeeDirectoryException(
                        "Multiple active employee directory entries were "
                            + "found for username: "
                            + username.value()
                    );
                }

                return Optional.of(employee);
            }
        } catch (SQLException exception) {
            throw new EmployeeDirectoryException(
                "Failed to query the employee directory.",
                exception
            );
        }
    }

    private EmployeeDirectoryEntry mapEmployee(
        ResultSet resultSet,
        DirectoryUsername resolvedUsername
    ) throws SQLException {
        try {
            EmployeeId employeeId = new EmployeeId(
                resultSet.getString("EMPLOYEE_ID")
            );

            String employeeNameEnglish =
                resultSet.getString("EMPLOYEE_NAME_ENGLISH");

            String employeeNameArabic =
                resultSet.getString("EMPLOYEE_NAME_ARABIC");

            Optional<DepartmentDirectoryEntry> department =
                mapDepartment(resultSet, employeeId);

            return new EmployeeDirectoryEntry(
                employeeId,
                resolvedUsername,
                employeeNameEnglish,
                employeeNameArabic,
                department
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new EmployeeDirectoryException(
                "Employee directory contains invalid data for username: "
                    + resolvedUsername.value(),
                exception
            );
        }
    }

    private Optional<DepartmentDirectoryEntry> mapDepartment(
        ResultSet resultSet,
        EmployeeId employeeId
    ) throws SQLException {
        String employeeDepartmentCode =
            trimToNull(
                resultSet.getString("EMPLOYEE_DEPARTMENT_CODE")
            );

        if (employeeDepartmentCode == null) {
            return Optional.empty();
        }

        DepartmentCode departmentCode;

        try {
            departmentCode = new DepartmentCode(
                employeeDepartmentCode
            );
        } catch (IllegalArgumentException exception) {
            /*
             * BADEA's historical directory contains legacy non-numeric
             * department codes. They do not represent a current routable
             * department.
             */
            return Optional.empty();
        }

        String joinedDepartmentCode =
            trimToNull(
                resultSet.getString("DEPARTMENT_CODE")
            );

        if (joinedDepartmentCode == null) {
            throw new EmployeeDirectoryException(
                "Employee "
                    + employeeId.value()
                    + " references current department "
                    + departmentCode.value()
                    + " but no matching department exists."
            );
        }

        DepartmentDirectoryEntry department =
            new DepartmentDirectoryEntry(
                departmentCode,
                resultSet.getString(
                    "DEPARTMENT_NAME_ENGLISH"
                ),
                resultSet.getString(
                    "DEPARTMENT_NAME_ARABIC"
                )
            );

        return Optional.of(department);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
            ? null
            : trimmed;
    }
}
package org.badea.correspondence.infrastructure.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryException;
import org.junit.jupiter.api.Test;

class OracleEmployeeDirectoryTest {

    @Test
    void returnsEmptyWhenNoActiveEmployeeMatches()
        throws SQLException {

        JdbcFixture jdbc = createJdbcFixture();

        when(jdbc.resultSet().next())
            .thenReturn(false);

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(jdbc.dataSource());

        Optional<EmployeeDirectoryEntry> result =
            directory.findActiveByUsername(
                new DirectoryUsername("adil.amin")
            );

        assertTrue(result.isEmpty());

        verify(jdbc.statement())
            .setString(1, "adil.amin");
    }

    @Test
    void resolvesActiveEmployeeWithCurrentDepartment()
        throws SQLException {

        JdbcFixture jdbc = createJdbcFixture();

        when(jdbc.resultSet().next())
            .thenReturn(true, false);

        stubEmployee(
            jdbc.resultSet(),
            "17257",
            "Adil Mustafa Amin",
            "عادل مصطفى امين",
            "117",
            "117",
            "Information Technology",
            "تقنية المعلومات"
        );

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(jdbc.dataSource());

        EmployeeDirectoryEntry employee =
            directory.findActiveByUsername(
                new DirectoryUsername("adil.amin")
            ).orElseThrow();

        assertEquals(
            "17257",
            employee.employeeId().value()
        );

        assertEquals(
            "adil.amin",
            employee.username().value()
        );

        assertEquals(
            "Adil Mustafa Amin",
            employee.nameEnglish()
        );

        assertEquals(
            "عادل مصطفى امين",
            employee.nameArabic()
        );

        DepartmentDirectoryEntry department =
            employee.department().orElseThrow();

        assertEquals(
            "117",
            department.code().value()
        );

        assertEquals(
            "Information Technology",
            department.nameEnglish()
        );

        assertEquals(
            "تقنية المعلومات",
            department.nameArabic()
        );
    }

    @Test
    void allowsActiveEmployeeWithoutDepartment()
        throws SQLException {

        JdbcFixture jdbc = createJdbcFixture();

        when(jdbc.resultSet().next())
            .thenReturn(true, false);

        stubEmployee(
            jdbc.resultSet(),
            "0685",
            "Example Employee",
            null,
            null,
            null,
            null,
            null
        );

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(jdbc.dataSource());

        EmployeeDirectoryEntry employee =
            directory.findActiveByUsername(
                new DirectoryUsername("employee.user")
            ).orElseThrow();

        assertTrue(employee.department().isEmpty());
    }

    @Test
    void treatsLegacyNonNumericDepartmentAsNotCurrent()
        throws SQLException {

        JdbcFixture jdbc = createJdbcFixture();

        when(jdbc.resultSet().next())
            .thenReturn(true, false);

        stubEmployee(
            jdbc.resultSet(),
            "0685",
            "Example Employee",
            null,
            "HRA",
            null,
            null,
            null
        );

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(jdbc.dataSource());

        EmployeeDirectoryEntry employee =
            directory.findActiveByUsername(
                new DirectoryUsername("employee.user")
            ).orElseThrow();

        assertTrue(employee.department().isEmpty());
    }

    @Test
    void rejectsCurrentDepartmentWithoutMatchingDepartmentRow()
        throws SQLException {

        JdbcFixture jdbc = createJdbcFixture();

        when(jdbc.resultSet().next())
            .thenReturn(true, false);

        stubEmployee(
            jdbc.resultSet(),
            "17257",
            "Adil Mustafa Amin",
            "عادل مصطفى امين",
            "117",
            null,
            null,
            null
        );

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(jdbc.dataSource());

        EmployeeDirectoryException exception =
            assertThrows(
                EmployeeDirectoryException.class,
                () -> directory.findActiveByUsername(
                    new DirectoryUsername("adil.amin")
                )
            );

        assertEquals(
            "Employee 17257 references current department 117 "
                + "but no matching department exists.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsMultipleActiveEmployeesForSameUsername()
        throws SQLException {

        JdbcFixture jdbc = createJdbcFixture();

        when(jdbc.resultSet().next())
            .thenReturn(true, true);

        stubEmployee(
            jdbc.resultSet(),
            "17257",
            "Adil Mustafa Amin",
            "عادل مصطفى امين",
            "117",
            "117",
            "Information Technology",
            "تقنية المعلومات"
        );

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(jdbc.dataSource());

        EmployeeDirectoryException exception =
            assertThrows(
                EmployeeDirectoryException.class,
                () -> directory.findActiveByUsername(
                    new DirectoryUsername("adil.amin")
                )
            );

        assertEquals(
            "Multiple active employee directory entries were found "
                + "for username: adil.amin",
            exception.getMessage()
        );
    }

    @Test
    void wrapsSqlFailureAsDirectoryFailure()
        throws SQLException {

        DataSource dataSource = mock(DataSource.class);

        SQLException sqlException =
            new SQLException("Database unavailable.");

        when(dataSource.getConnection())
            .thenThrow(sqlException);

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(dataSource);

        EmployeeDirectoryException exception =
            assertThrows(
                EmployeeDirectoryException.class,
                () -> directory.findActiveByUsername(
                    new DirectoryUsername("adil.amin")
                )
            );

        assertEquals(
            "Failed to query the employee directory.",
            exception.getMessage()
        );

        assertSame(
            sqlException,
            exception.getCause()
        );
    }

    @Test
    void rejectsNullDataSource() {
        assertThrows(
            NullPointerException.class,
            () -> new OracleEmployeeDirectory(null)
        );
    }

    @Test
    void rejectsNullUsername() {
        DataSource dataSource = mock(DataSource.class);

        OracleEmployeeDirectory directory =
            new OracleEmployeeDirectory(dataSource);

        assertThrows(
            NullPointerException.class,
            () -> directory.findActiveByUsername(null)
        );
    }

    private static JdbcFixture createJdbcFixture()
        throws SQLException {

        DataSource dataSource =
            mock(DataSource.class);

        Connection connection =
            mock(Connection.class);

        PreparedStatement statement =
            mock(PreparedStatement.class);

        ResultSet resultSet =
            mock(ResultSet.class);

        when(dataSource.getConnection())
            .thenReturn(connection);

        when(connection.prepareStatement(anyString()))
            .thenReturn(statement);

        when(statement.executeQuery())
            .thenReturn(resultSet);

        return new JdbcFixture(
            dataSource,
            connection,
            statement,
            resultSet
        );
    }

    private static void stubEmployee(
        ResultSet resultSet,
        String employeeId,
        String employeeNameEnglish,
        String employeeNameArabic,
        String employeeDepartmentCode,
        String joinedDepartmentCode,
        String departmentNameEnglish,
        String departmentNameArabic
    ) throws SQLException {

        when(resultSet.getString("EMPLOYEE_ID"))
            .thenReturn(employeeId);

        when(
            resultSet.getString(
                "EMPLOYEE_NAME_ENGLISH"
            )
        ).thenReturn(employeeNameEnglish);

        when(
            resultSet.getString(
                "EMPLOYEE_NAME_ARABIC"
            )
        ).thenReturn(employeeNameArabic);

        when(
            resultSet.getString(
                "EMPLOYEE_DEPARTMENT_CODE"
            )
        ).thenReturn(employeeDepartmentCode);

        when(
            resultSet.getString(
                "DEPARTMENT_CODE"
            )
        ).thenReturn(joinedDepartmentCode);

        when(
            resultSet.getString(
                "DEPARTMENT_NAME_ENGLISH"
            )
        ).thenReturn(departmentNameEnglish);

        when(
            resultSet.getString(
                "DEPARTMENT_NAME_ARABIC"
            )
        ).thenReturn(departmentNameArabic);
    }

    private record JdbcFixture(
        DataSource dataSource,
        Connection connection,
        PreparedStatement statement,
        ResultSet resultSet
    ) {
    }
}
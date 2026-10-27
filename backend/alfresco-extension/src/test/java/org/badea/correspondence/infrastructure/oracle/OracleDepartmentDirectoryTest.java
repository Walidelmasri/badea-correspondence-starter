package org.badea.correspondence.infrastructure.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DepartmentDirectoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OracleDepartmentDirectoryTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;

    private OracleDepartmentDirectory directory;

    @BeforeEach
    void setUp()
            throws SQLException {

        dataSource =
                mock(
                        DataSource.class);

        connection =
                mock(
                        Connection.class);

        statement =
                mock(
                        PreparedStatement.class);

        resultSet =
                mock(
                        ResultSet.class);

        when(dataSource.getConnection())
                .thenReturn(
                        connection);

        directory =
                new OracleDepartmentDirectory(
                        dataSource);
    }

    @Test
    void listsNumericCorrespondenceDepartmentsAndSkipsLegacyCodes()
            throws SQLException {

        when(connection.prepareStatement(
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        statement);

        when(statement.executeQuery())
                .thenReturn(
                        resultSet);

        when(resultSet.next())
                .thenReturn(
                        true,
                        true,
                        true,
                        false);

        when(resultSet.getString(
                "DEPARTMENT_CODE"))
                .thenReturn(
                        "101",
                        "AUB",
                        "117");

        when(resultSet.getString(
                "NAME"))
                .thenReturn(
                        "Office of the President",
                        "Digital Transformation Department");

        when(resultSet.getString(
                "NAME_ARABIC"))
                .thenReturn(
                        "مكتب الرئيس",
                        "إدارة التحول الرقمي");

        List<DepartmentDirectoryEntry> departments =
                directory
                        .findAllCorrespondenceDepartments();

        assertEquals(
                2,
                departments.size());

        assertEquals(
                new DepartmentCode("101"),
                departments.get(0).code());

        assertEquals(
                "Office of the President",
                departments.get(0).nameEnglish());

        assertEquals(
                new DepartmentCode("117"),
                departments.get(1).code());

        assertEquals(
                "Digital Transformation Department",
                departments.get(1).nameEnglish());
    }

    @Test
    void findsDepartmentByCode()
            throws SQLException {

        when(connection.prepareStatement(
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        statement);

        when(statement.executeQuery())
                .thenReturn(
                        resultSet);

        when(resultSet.next())
                .thenReturn(
                        true,
                        false);

        when(resultSet.getString(
                "DEPARTMENT_CODE"))
                .thenReturn(
                        "117");

        when(resultSet.getString(
                "NAME"))
                .thenReturn(
                        "Digital Transformation Department");

        when(resultSet.getString(
                "NAME_ARABIC"))
                .thenReturn(
                        "إدارة التحول الرقمي");

        Optional<DepartmentDirectoryEntry> department =
                directory.findByCode(
                        new DepartmentCode("117"));

        assertTrue(
                department.isPresent());

        assertEquals(
                new DepartmentCode("117"),
                department.get().code());

        assertEquals(
                "Digital Transformation Department",
                department.get().nameEnglish());

        verify(statement)
                .setString(
                        1,
                        "117");
    }

    @Test
    void returnsEmptyWhenDepartmentDoesNotExist()
            throws SQLException {

        when(connection.prepareStatement(
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        statement);

        when(statement.executeQuery())
                .thenReturn(
                        resultSet);

        when(resultSet.next())
                .thenReturn(
                        false);

        Optional<DepartmentDirectoryEntry> department =
                directory.findByCode(
                        new DepartmentCode("999"));

        assertTrue(
                department.isEmpty());
    }

    @Test
    void resolvesActiveManagerByCorrespondenceDepartment() throws SQLException {
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("DEPT_MANAGER_ID")).thenReturn("17258");
        var manager = directory.findActiveManagerEmployeeId(new DepartmentCode("117"));
        assertEquals("17258", manager.orElseThrow().value());
        verify(statement).setString(1, "117");
    }

    @Test
    void refusesAmbiguousManagerMappings() throws SQLException {
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true);
        when(resultSet.getString("DEPT_MANAGER_ID")).thenReturn("17258");
        assertThrows(DepartmentDirectoryException.class,
                () -> directory.findActiveManagerEmployeeId(new DepartmentCode("117")));
    }

    @Test
    void convertsSqlFailureToDepartmentDirectoryException()
            throws SQLException {

        when(dataSource.getConnection())
                .thenThrow(
                        new SQLException(
                                "Oracle unavailable"));

        assertThrows(
                DepartmentDirectoryException.class,
                () -> directory
                        .findAllCorrespondenceDepartments());
    }
}
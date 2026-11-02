package org.badea.correspondence.infrastructure.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentDirectoryException;
import org.badea.correspondence.directory.EmployeeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TarasolOrganizationReaderTest {
    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet rows;
    private TarasolOrganizationReader reader;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        rows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        reader = new TarasolOrganizationReader(dataSource);
    }

    @Test
    void readsActiveCatalogWithoutAccessingLegacyDirectory() throws SQLException {
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("UNIT_CODE")).thenReturn("118");
        when(rows.getString("NAME_EN")).thenReturn("Human Resources Department");
        when(rows.getString("NAME_AR")).thenReturn("إدارة الموارد البشرية");
        when(rows.getString("UNIT_TYPE")).thenReturn("DEPARTMENT");
        var units = reader.findActiveUnits();
        assertEquals(1, units.size());
        assertEquals("118", units.get(0).code().value());
        verify(statement).setQueryTimeout(8);
    }

    @Test
    void resolvesMultipleConcurrentUnitAssignmentsWithoutPickingOne() throws SQLException {
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getLong("ASSIGNMENT_ID")).thenReturn(7L, 11L);
        when(rows.getString("UNIT_CODE")).thenReturn("101", "118");
        when(rows.getString("EMP_ID")).thenReturn("24428", "24428");
        when(rows.getString("CAPACITY")).thenReturn("MANAGER", "ACTING_MANAGER");
        var assignments = reader.findActiveAssignments(new EmployeeId("24428"));
        assertEquals(2, assignments.size());
        assertEquals("101", assignments.get(0).unitCode().value());
        assertEquals("118", assignments.get(1).unitCode().value());
        verify(statement).setString(1, "24428");
        verify(statement).setQueryTimeout(8);
    }

    @Test
    void readsVerifiedManagerWorkspacesWithSingleBoundedQuery() throws SQLException {
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getLong("ASSIGNMENT_ID")).thenReturn(1L, 3L);
        when(rows.getString("UNIT_CODE")).thenReturn("101", "118");
        when(rows.getString("NAME_EN")).thenReturn("Office of President", "Human Resources");
        when(rows.getString("NAME_AR")).thenReturn("مكتب الرئيس", "الموارد البشرية");
        when(rows.getString("CAPACITY")).thenReturn("MANAGER", "ACTING_MANAGER");

        var workspaces = reader.findCurrentManagerWorkspaces(new EmployeeId("18284"));
        assertEquals(2, workspaces.size());
        assertEquals("101", workspaces.get(0).code().value());
        assertEquals("118", workspaces.get(1).code().value());
        assertEquals("ACTING_MANAGER", workspaces.get(1).capacity());
        verify(statement).setString(1, "18284");
        verify(statement).setQueryTimeout(8);
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("TARASOL_UNIT_ASSIGNMENTS"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.getValue().contains("COR_DEPARTMENTS"));
    }

    @Test
    void returnsEveryConcurrentActingManagerForOneUnit() throws SQLException {
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getLong("ASSIGNMENT_ID")).thenReturn(17L, 18L);
        when(rows.getString("UNIT_CODE")).thenReturn("118", "118");
        when(rows.getString("EMP_ID")).thenReturn("24428", "18284");
        when(rows.getString("CAPACITY")).thenReturn("ACTING_MANAGER", "ACTING_MANAGER");
        var assignments = reader.findActiveManagerAssignmentsForUnit(
            new org.badea.correspondence.directory.DepartmentCode("118"));
        assertEquals(2, assignments.size());
        assertEquals("24428", assignments.get(0).employeeId().value());
        assertEquals("18284", assignments.get(1).employeeId().value());
        verify(statement).setString(1, "118");
        verify(statement).setQueryTimeout(8);
    }

    @Test
    void vacantUnitHasNoAssumedManager() throws SQLException {
        when(rows.next()).thenReturn(false);
        assertEquals(0, reader.findActiveManagerAssignmentsForUnit(
            new org.badea.correspondence.directory.DepartmentCode("118")).size());
    }

    @Test
    void unavailableDatabaseFailsClosedInsteadOfReturningAnEmptyDirectory() throws SQLException {
        when(statement.executeQuery()).thenThrow(new SQLException("unavailable"));
        assertThrows(DepartmentDirectoryException.class, reader::findActiveUnits);
    }

    @Test
    void noAssignmentMeansNoTarasolAuthority() throws SQLException {
        when(rows.next()).thenReturn(false);
        assertEquals(0, reader.findActiveAssignments(new EmployeeId("99999")).size());
    }
}

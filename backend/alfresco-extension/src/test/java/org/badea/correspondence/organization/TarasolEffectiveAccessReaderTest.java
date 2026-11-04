package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.sql.DataSource;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TarasolEffectiveAccessReaderTest {
    private final EmployeeId actor = new EmployeeId("00123");
    private final DepartmentCode unit = new DepartmentCode("118");
    private final DataSource ds = mock(DataSource.class);
    private final EmployeeDirectory employees = mock(EmployeeDirectory.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet rows = mock(ResultSet.class);
    private final TarasolEffectiveAccessReader reader = new TarasolEffectiveAccessReader(ds, employees);

    @BeforeEach
    void setup() throws SQLException {
        when(ds.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(employees.findActiveByEmployeeId(actor)).thenReturn(Optional.of(
            new EmployeeDirectoryEntry(actor, new DirectoryUsername("test.user"),
                "Test User", null, Optional.empty())));
    }

    @Test
    void secretaryAssignmentIsIndependentOfManagerAndCannotGrantManagerRole() throws SQLException {
        when(rows.next()).thenReturn(true);
        assertTrue(reader.hasActiveSecretaryAssignment(actor, unit));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("unit.STATUS = 'A'"));
        assertFalse(sql.getValue().contains("manager.REVOKED_AT"));
        assertTrue(sql.getValue().contains("grant_row.SECRETARY_EMP_ID = ?"));
        verify(statement).setString(1, "00123");
        verify(statement).setString(2, "118");
    }

    @Test
    void inactiveEmployeeNeverReachesAssignmentDatabase() throws SQLException {
        when(employees.findActiveByEmployeeId(actor)).thenReturn(Optional.empty());
        assertFalse(reader.hasActiveSecretaryAssignment(actor, unit));
        verifyNoInteractions(ds);
    }

    @Test
    void caseSpecificDelegationBindsExactCaseAndChecksRevocation() throws SQLException {
        when(rows.next()).thenReturn(true);
        when(rows.getLong("DELEGATION_ID")).thenReturn(5L);
        when(rows.getLong("MANAGER_ASSIGNMENT_ID")).thenReturn(8L);
        when(rows.getString("DELEGATION_TYPE")).thenReturn("CASE_HANDOFF");
        var created = OffsetDateTime.parse("2026-11-01T09:00:00+03:00");
        var caseId = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
        var grant = reader.findActiveDelegation(actor, unit, caseId, created);
        assertEquals(5L, grant.orElseThrow().id());
        assertEquals("CASE_HANDOFF", grant.orElseThrow().type());
        verify(statement).setString(3, caseId);
        verify(statement).setObject(4, created);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("grant_row.REVOKED_AT IS NULL"));
        assertTrue(sql.getValue().contains("manager.REVOKED_AT IS NULL"));
        assertTrue(sql.getValue().contains("grant_row.STARTS_AT <= ?"));
        assertTrue(sql.getValue().contains("manager.EMP_ID <> grant_row.DELEGATE_EMP_ID"));
    }

    @Test
    void delegationNavigationRequiresCurrentUnrevokedParentAndActiveEmployee() throws SQLException {
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("UNIT_CODE")).thenReturn("118");
        when(rows.getString("NAME_EN")).thenReturn("Human Resources");
        assertEquals("118", reader.findCurrentDelegationUnits(actor).get(0).code());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("manager.REVOKED_AT IS NULL"));
        assertTrue(sql.getValue().contains("grant_row.REVOKED_AT IS NULL"));
        assertTrue(sql.getValue().contains("manager.EMP_ID <> grant_row.DELEGATE_EMP_ID"));
    }

    @Test
    void badOrUnknownCaseCannotProduceDelegation() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> reader.findActiveDelegation(
            actor, unit, "not-a-case", OffsetDateTime.now()));
        verifyNoInteractions(ds);
    }

    @Test
    void temporaryDatabaseFailureDoesNotBecomePermission() throws SQLException {
        when(ds.getConnection()).thenThrow(new SQLException("database offline"));
        assertThrows(IllegalStateException.class, () -> reader.hasActiveSecretaryAssignment(actor, unit));
    }
}

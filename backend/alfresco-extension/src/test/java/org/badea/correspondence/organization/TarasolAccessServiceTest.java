package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.sql.DataSource;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectory;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.junit.jupiter.api.Test;

class TarasolAccessServiceTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final EmployeeDirectory employees = mock(EmployeeDirectory.class);
    private final TarasolAccessService service = new TarasolAccessService(dataSource, employees);
    private static final EmployeeId ADMIN = new EmployeeId("24428");
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-01T10:00:00+03:00");
    private static final OffsetDateTime END = START.plusDays(30);

    private void activeEmployee(String id) {
        var entry = new EmployeeDirectoryEntry(new EmployeeId(id),
            new DirectoryUsername("employee"), "Employee", null, Optional.empty());
        when(employees.findActiveByEmployeeId(new EmployeeId(id))).thenReturn(Optional.of(entry));
    }

    private void lockedUnit() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet locked = mock(ResultSet.class);
        when(connection.prepareStatement(contains("FOR UPDATE"))).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(locked);
        when(locked.next()).thenReturn(true);
    }

    @Test
    void employeeSelectorUsesActiveDirectoryAndKeepsCanonicalEmployeeId() throws SQLException {
        var person = new EmployeeDirectoryEntry(new EmployeeId("018284"),
            new DirectoryUsername("ndjack"), "Mohamed N'Diack", "محمد", Optional.empty());
        when(employees.searchActive("Mohamed")).thenReturn(java.util.List.of(person));
        when(employees.findActiveByEmployeeId(new EmployeeId("018284"))).thenReturn(Optional.of(person));

        var byName = service.searchActiveEmployees("  Mohamed  ");
        var byId = service.searchActiveEmployees("018284");

        assertEquals("018284", byName.get(0).employeeId());
        assertEquals("Mohamed N'Diack", byName.get(0).nameEnglish());
        assertEquals("018284", byId.get(0).employeeId());
        verify(dataSource, never()).getConnection();
    }

    @Test
    void existingAppointmentNamesUseDirectoryWithoutChangingHistoricalEmployeeIds() {
        var person = new EmployeeDirectoryEntry(new EmployeeId("18284"),
            new DirectoryUsername("ndjack"), "Mohamed N'Diack", null, Optional.empty());
        when(employees.findActiveByEmployeeId(new EmployeeId("18284"))).thenReturn(Optional.of(person));
        var snapshot = new TarasolAccessService.AccessSnapshot(java.util.List.of(
            new TarasolAccessService.Assignment(2, "118", "18284", "ACTING_MANAGER",
                "2026-11-01", null, null, true),
            new TarasolAccessService.Assignment(1, "118", "99999", "MANAGER",
                "2025-01-01", "2025-07-01", null, false)),
            java.util.List.of(), java.util.List.of());

        var names = service.employeeLabels(snapshot);
        assertEquals("Mohamed N'Diack", names.get("18284").nameEnglish());
        org.junit.jupiter.api.Assertions.assertFalse(names.containsKey("99999"));
        assertEquals("99999", snapshot.appointments().get(1).employeeId());
    }

    @Test
    void anotherActingManagerIsAllowedWhenOfficialPositionIsVacant() throws SQLException {
        activeEmployee("18284");
        lockedUnit();
        PreparedStatement check = mock(PreparedStatement.class);
        ResultSet empty = mock(ResultSet.class);
        when(connection.prepareStatement(contains("SELECT EMP_ID, CAPACITY"))).thenReturn(check);
        when(check.executeQuery()).thenReturn(empty);
        when(empty.next()).thenReturn(false);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet ids = mock(ResultSet.class);
        when(connection.prepareStatement(startsWith("INSERT INTO TARASOL_UNIT_ASSIGNMENTS"),
            any(String[].class))).thenReturn(insert);
        when(insert.getGeneratedKeys()).thenReturn(ids);
        when(ids.next()).thenReturn(true);
        when(ids.getLong(1)).thenReturn(51L);

        long result = service.changeAppointment(new TarasolAccessService.AppointmentChange(
            "APPOINT", "118", 0, "18284", "ACTING_MANAGER", START, END, "ACTING-118", null), ADMIN);

        assertEquals(51L, result);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void preventsAppointingActingManagerWhileOfficialManagerExists() throws SQLException {
        activeEmployee("18284");
        lockedUnit();
        PreparedStatement check = mock(PreparedStatement.class);
        ResultSet occupied = mock(ResultSet.class);
        when(connection.prepareStatement(contains("SELECT EMP_ID, CAPACITY"))).thenReturn(check);
        when(check.executeQuery()).thenReturn(occupied);
        when(occupied.next()).thenReturn(true);
        when(occupied.getString("CAPACITY")).thenReturn("MANAGER");
        when(occupied.getString("EMP_ID")).thenReturn("99999");

        assertThrows(IllegalArgumentException.class,
            () -> service.changeAppointment(new TarasolAccessService.AppointmentChange(
                "APPOINT", "118", 0, "18284", "ACTING_MANAGER", START, END, null, null), ADMIN));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void refusesSecretaryChangesAfterManagerAppointmentExpires() throws SQLException {
        activeEmployee("24428");
        lockedUnit();
        PreparedStatement authority = mock(PreparedStatement.class);
        ResultSet notCurrent = mock(ResultSet.class);
        when(connection.prepareStatement(contains("CAPACITY IN ('MANAGER','ACTING_MANAGER')")))
            .thenReturn(authority);
        when(authority.executeQuery()).thenReturn(notCurrent);
        when(notCurrent.next()).thenReturn(false);

        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.changeTeam(new TarasolAccessService.TeamChange(
                "ASSIGN_SECRETARY", "118", 91, 0, "24428", null, null, START, null, null),
                new EmployeeId("18284")));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void invalidEmployeeIsRejectedWithoutOpeningOracleWriteTransaction() throws SQLException {
        when(employees.findActiveByEmployeeId(new EmployeeId("00000")))
            .thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> service.changeAppointment(new TarasolAccessService.AppointmentChange(
                "APPOINT", "118", 0, "00000", "MANAGER", START, null, null, null), ADMIN));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void rejectsTemporaryDelegationWithoutExpiration() throws SQLException {
        activeEmployee("24428");
        assertThrows(IllegalArgumentException.class,
            () -> service.changeTeam(new TarasolAccessService.TeamChange(
                "GRANT_DELEGATION", "118", 91, 0, "24428", "LEAVE_COVER", null,
                START, null, null), new EmployeeId("18284")));
        verify(dataSource, never()).getConnection();
    }
}

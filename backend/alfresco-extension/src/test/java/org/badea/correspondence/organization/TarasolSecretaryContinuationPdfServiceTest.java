package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryContinuationPdfStore;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.junit.jupiter.api.Test;

class TarasolSecretaryContinuationPdfServiceTest {
    private static final String CASE = "217a0e57-bc77-4475-a06f-c72aaa0949c4";
    private static final String REQUEST = "12345678-1234-1234-1234-123456789abc";
    private final DepartmentCode unit = new DepartmentCode("117");
    private final EmployeeDirectoryEntry secretary = new EmployeeDirectoryEntry(
        new EmployeeId("99999"), new DirectoryUsername("sec.test"),
        "Secretary", "سكرتير", Optional.empty());
    private final EmployeeDirectoryEntry manager = new EmployeeDirectoryEntry(
        new EmployeeId("200"), new DirectoryUsername("manager.test"),
        "Manager", "مدير", Optional.empty());
    private final TarasolSecretaryRoundTwoRequestService secretaryRequests =
        mock(TarasolSecretaryRoundTwoRequestService.class);
    private final TarasolSecretaryRoundContinuationApprovalService managerApprovals =
        mock(TarasolSecretaryRoundContinuationApprovalService.class);
    private final AlfrescoSecretaryContinuationPdfStore store =
        mock(AlfrescoSecretaryContinuationPdfStore.class);
    private final TarasolSecretaryContinuationPdfService service =
        new TarasolSecretaryContinuationPdfService(secretaryRequests, managerApprovals, store);

    private AlfrescoSecretaryRoundTwoRequestRepository.Request request(String status) {
        return new AlfrescoSecretaryRoundTwoRequestRepository.Request(REQUEST, CASE,
            unit.value(), "99999", "Reason", 4L, status,
            Instant.parse("2026-11-04T12:00:00Z"));
    }

    @Test void secretaryCannotMutateReturnedOrStaleRequest() {
        when(secretaryRequests.current(secretary, unit, CASE)).thenReturn(request("RETURNED"));
        assertThrows(OfficeCycleConflictException.class,
            () -> service.upload(secretary, unit, CASE, REQUEST, "x.pdf", new byte[]{1}));
        verifyNoInteractions(store);
    }

    @Test void managerMustHaveLiveAppointmentAndMatchingRequestToPreview() {
        when(managerApprovals.pending(manager, unit, CASE)).thenReturn(null);
        assertThrows(OfficeCycleConflictException.class,
            () -> service.managerList(manager, unit, CASE, REQUEST));
        verifyNoInteractions(store);
    }

    @Test void managerCanReadPendingDocumentsButCannotUseSecretaryWritePath() {
        when(managerApprovals.pending(manager, unit, CASE)).thenReturn(request("PENDING"));
        service.managerList(manager, unit, CASE, REQUEST);
        verify(store).list(CASE, unit, REQUEST, null);
        verify(store, never()).delete(any(), any(), any(), any(), any());
        verify(store, never()).upload(any(), any(), any(), any(), any(), any());
    }

    @Test void secretaryCanListOnlyHerCurrentPendingRequest() {
        when(secretaryRequests.current(secretary, unit, CASE)).thenReturn(request("PENDING"));
        service.secretaryList(secretary, unit, CASE, REQUEST);
        verify(store).list(CASE, unit, REQUEST, "99999");
        assertThrows(OfficeCycleConflictException.class,
            () -> service.secretaryList(secretary, unit,
                CASE, "33333333-3333-4333-8333-333333333333"));
    }
}

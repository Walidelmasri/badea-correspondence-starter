package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoSecretaryRoundTwoRequestRepositoryTest {
    private static final String ID = "217a0e57-bc77-4475-a06f-c72aaa0949c4";
    private final NodeService nodes = mock(NodeService.class);
    private final CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
    private final NodeRef parent = mock(NodeRef.class);
    private final NodeRef assignment = mock(NodeRef.class);
    private final ChildAssociationRef assignmentAssoc = mock(ChildAssociationRef.class);
    private final AlfrescoSecretaryRoundTwoRequestRepository repo =
        new AlfrescoSecretaryRoundTwoRequestRepository(nodes, locator,
            Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC));

    @BeforeEach void setup() {
        when(locator.locate(ID)).thenReturn(parent);
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("state")))
            .thenReturn("WITH_DIRECTOR");
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("assignedRole")))
            .thenReturn("DIRECTOR");
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("technicalId")))
            .thenReturn(ID);
        when(nodes.getProperty(parent, AlfrescoCorrespondenceCycleReader.CURRENT)).thenReturn(2L);
        when(assignmentAssoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(assignmentAssoc.getChildRef()).thenReturn(assignment);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(assignmentAssoc));
        when(nodes.getType(assignment)).thenReturn(AlfrescoDepartmentAssignmentReader.TYPE);
        when(nodes.getProperty(assignment, AlfrescoCorrespondenceCycleReader.ASSIGNMENT))
            .thenReturn(2L);
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentId")))
            .thenReturn("assignment-1");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentDepartmentCode")))
            .thenReturn("117");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentRecipientType")))
            .thenReturn("TO");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentStatus")))
            .thenReturn("COMPLETED");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentByEmployeeId")))
            .thenReturn("manager-1");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentAt")))
            .thenReturn(Date.from(Instant.parse("2026-11-03T12:00:00Z")));
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentCompletedByEmployeeId")))
            .thenReturn("manager-1");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentCompletedAt")))
            .thenReturn(Date.from(Instant.parse("2026-11-04T12:00:00Z")));
    }

    @Test void createsCycleBoundRequestWithoutAdvancingCorrespondence() {
        var created = mock(ChildAssociationRef.class);
        when(created.getChildRef()).thenReturn(mock(NodeRef.class));
        when(nodes.createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS), any(), any(), anyMap()))
            .thenReturn(created);
        var result = repo.submit(ID, new DepartmentCode("117"), "99999", "Reason");
        assertEquals("PENDING", result.status());
        assertEquals(2L, result.expectedCycle());
        assertEquals("117", result.unitCode());
        verify(nodes).setProperty(eq(parent),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")),
            eq(result.requestId()));
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
        verify(nodes).createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS), any(),
            eq(AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoRequest")), anyMap());
    }

    @Test void retryOfSamePendingRequestReturnsExistingIdWithoutWriting() {
        var old = mock(NodeRef.class);
        var oldAssoc = mock(ChildAssociationRef.class);
        when(oldAssoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(oldAssoc.getChildRef()).thenReturn(old);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(assignmentAssoc, oldAssoc));
        when(nodes.getType(old)).thenReturn(
            AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoRequest"));
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus")))
            .thenReturn("PENDING");
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestId")))
            .thenReturn("request-old");
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestUnit")))
            .thenReturn("117");
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestOwner")))
            .thenReturn("99999");
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReason")))
            .thenReturn("Reason");
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestCycle")))
            .thenReturn(2L);
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestAt")))
            .thenReturn(Date.from(Instant.parse("2026-11-04T12:00:00Z")));
        assertEquals("request-old", repo.submit(ID, new DepartmentCode("117"),
            "99999", "Reason").requestId());
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
        assertThrows(OfficeCycleConflictException.class,
            () -> repo.submit(ID, new DepartmentCode("117"), "99999", "Different reason"));
    }

    @Test void incompleteToAssignmentCannotStartAnotherRound() {
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentStatus")))
            .thenReturn("PENDING");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentCompletedByEmployeeId")))
            .thenReturn(null);
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentCompletedAt")))
            .thenReturn(null);
        assertThrows(OfficeCycleConflictException.class,
            () -> repo.submit(ID, new DepartmentCode("117"), "99999", "Reason"));
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }

    @Test void anotherUnitCannotStartRoundUsingSharedTechnicalId() {
        assertThrows(OfficeCycleConflictException.class,
            () -> repo.submit(ID, new DepartmentCode("118"), "99999", "Reason"));
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }

    @Test void alreadyMovedCorrespondenceCannotCreateRequest() {
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("state")))
            .thenReturn("WITH_PRESIDENT_OFFICE");
        assertThrows(OfficeCycleConflictException.class,
            () -> repo.submit(ID, new DepartmentCode("117"), "99999", "Reason"));
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }

    private static final String REQUEST_ID = "12345678-1234-1234-1234-123456789abc";
    private final NodeRef followUp = mock(NodeRef.class);
    private final ChildAssociationRef followUpAssoc = mock(ChildAssociationRef.class);

    private void pendingAtCycle(long cycle) {
        when(nodes.getProperty(parent, AlfrescoCorrespondenceCycleReader.CURRENT)).thenReturn(cycle);
        when(nodes.getProperty(assignment, AlfrescoCorrespondenceCycleReader.ASSIGNMENT))
            .thenReturn(cycle);
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")))
            .thenReturn(REQUEST_ID);
        when(followUpAssoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(followUpAssoc.getChildRef()).thenReturn(followUp);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(assignmentAssoc, followUpAssoc));
        when(nodes.getType(followUp)).thenReturn(
            AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoRequest"));
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestId")))
            .thenReturn(REQUEST_ID);
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestUnit")))
            .thenReturn("117");
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestOwner")))
            .thenReturn("99999");
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReason")))
            .thenReturn("Please continue");
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestCycle")))
            .thenReturn(cycle);
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus")))
            .thenReturn("PENDING");
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestAt")))
            .thenReturn(Date.from(Instant.parse("2026-11-04T12:00:00Z")));
    }

    @Test void managerCanReadOnlyCurrentCycleAndUnitPendingRequest() {
        pendingAtCycle(3L);
        assertEquals(3L, repo.pending(ID, new DepartmentCode("117")).expectedCycle());
        assertThrows(OfficeCycleConflictException.class, () ->
            repo.pending(ID, new DepartmentCode("118")));
    }

    @Test void approvalRunsExistingContinuationAndClearsPendingRequest() {
        pendingAtCycle(3L);
        var continuation = mock(java.util.function.LongSupplier.class);
        when(continuation.getAsLong()).thenReturn(4L);
        var decision = repo.approve(ID, new DepartmentCode("117"), REQUEST_ID,
            "200", continuation);
        assertEquals("APPROVED", decision.status());
        assertEquals(4L, decision.workflowCycle());
        verify(continuation).getAsLong();
        verify(nodes).setProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus"), "APPROVED");
        verify(nodes).setProperty(parent,
            AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId"), null);
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
    }

    @Test void returnRequiresReasonAndLeavesCurrentCycleUntouched() {
        pendingAtCycle(2L);
        assertThrows(IllegalArgumentException.class, () -> repo.returnForRevision(ID,
            new DepartmentCode("117"), REQUEST_ID, "200", "   "));
        var decision = repo.returnForRevision(ID, new DepartmentCode("117"), REQUEST_ID,
            "200", "Needs new attachment");
        assertEquals("RETURNED", decision.status());
        assertEquals(2L, decision.workflowCycle());
        verify(nodes).setProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnReason"),
            "Needs new attachment");
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
    }

    @Test void returnedRequestExposesManagerReasonOnlyToMatchingOwnerLookup() {
        pendingAtCycle(4L);
        when(nodes.getProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus"))).thenReturn("RETURNED");
        when(nodes.getProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnReason")))
            .thenReturn("Replace attachment");
        var returned = repo.current(ID, new DepartmentCode("117"), "99999");
        assertEquals("RETURNED", returned.status());
        assertEquals("Replace attachment", returned.returnReason());
        assertNull(repo.current(ID, new DepartmentCode("117"), "another-secretary"));
    }

    @Test void staleRequestCannotAdvanceRoundOrBeDecidedTwice() {
        pendingAtCycle(3L);
        var continuation = mock(java.util.function.LongSupplier.class);
        assertThrows(OfficeCycleConflictException.class, () -> repo.approve(ID,
            new DepartmentCode("117"), "ffffffff-ffff-ffff-ffff-ffffffffffff", "200", continuation));
        verifyNoInteractions(continuation);
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")))
            .thenReturn(null);
        assertThrows(OfficeCycleConflictException.class, () -> repo.returnForRevision(ID,
            new DepartmentCode("117"), REQUEST_ID, "200", "Return reason"));
        verify(nodes, never()).setProperty(eq(followUp),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus")), any());
    }

    @Test void approvalWorksOnLaterRoundsAndRejectsIncorrectResultCycle() {
        pendingAtCycle(6L);
        var continuation = mock(java.util.function.LongSupplier.class);
        when(continuation.getAsLong()).thenReturn(8L);
        assertThrows(OfficeCycleConflictException.class, () -> repo.approve(ID,
            new DepartmentCode("117"), REQUEST_ID, "200", continuation));
        verify(nodes, never()).setProperty(eq(followUp),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus")), any());
        when(continuation.getAsLong()).thenReturn(7L);
        assertEquals(7L, repo.approve(ID, new DepartmentCode("117"), REQUEST_ID,
            "200", continuation).workflowCycle());
    }
}

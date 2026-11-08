package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
        var eventCreated = mock(ChildAssociationRef.class);
        when(eventCreated.getChildRef()).thenReturn(mock(NodeRef.class));
        when(nodes.createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS), any(),
            eq(AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoDecisionEvent")), anyMap()))
            .thenReturn(eventCreated);
    }

    @Test void eligibilityDoesNotWriteOrGrantDirectorPendingAction() {
        var eligible = repo.eligibility(ID, new DepartmentCode("117"), "99999");
        assertTrue(eligible.canPrepare());
        assertFalse(eligible.canReadRequest());
        assertNull(eligible.requestStatus());
        assertEquals(3L, eligible.nextCycle());
        verify(nodes, never()).setProperty(any(), any(), any());
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentStatus")))
            .thenReturn("PENDING");
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentCompletedByEmployeeId")))
            .thenReturn(null);
        when(nodes.getProperty(assignment, AlfrescoDepartmentAssignmentReader.q("assignmentCompletedAt")))
            .thenReturn(null);
        var notReady = repo.eligibility(ID, new DepartmentCode("117"), "99999");
        assertFalse(notReady.canPrepare());
        assertFalse(notReady.canReadRequest());
        assertEquals("DEPARTMENT_NOT_READY", notReady.reasonCode());
        assertEquals(3L, notReady.nextCycle());
    }

    @Test void createsCycleBoundRequestWithoutAdvancingCorrespondence() {
        var created = mock(ChildAssociationRef.class);
        when(created.getChildRef()).thenReturn(mock(NodeRef.class));
        when(nodes.createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS), any(), any(), anyMap()))
            .thenReturn(created);
        var result = repo.submit(ID, new DepartmentCode("117"), "99999", "Reason");
        assertEquals("PREPARING", result.status());
        assertEquals(2L, result.expectedCycle());
        assertEquals("117", result.unitCode());
        verify(nodes).setProperty(eq(parent),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")),
            isNull());
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
        verify(nodes).createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS), any(),
            eq(AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoRequest")), anyMap());
    }

    @Test void retryOfSamePreparingRequestReturnsExistingIdWithoutWriting() {
        var old = mock(NodeRef.class);
        var oldAssoc = mock(ChildAssociationRef.class);
        when(oldAssoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(oldAssoc.getChildRef()).thenReturn(old);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(assignmentAssoc, oldAssoc));
        when(nodes.getType(old)).thenReturn(
            AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoRequest"));
        when(nodes.getProperty(old, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus")))
            .thenReturn("PREPARING");
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
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")))
            .thenReturn(null);
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

    private void returnedAtCycle(long cycle) {
        pendingAtCycle(cycle);
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")))
            .thenReturn(null);
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus")))
            .thenReturn("RETURNED");
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnVersion")))
            .thenReturn(1L);
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnReason")))
            .thenReturn("Replace incorrect PDF");
    }

    private void preparingAtCycle(long cycle) {
        pendingAtCycle(cycle);
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")))
            .thenReturn(null);
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus")))
            .thenReturn("PREPARING");
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnVersion")))
            .thenReturn(0L);
    }

    @Test void eligibilityTracksOwnedPreparationPendingAndReturnWithoutCreatingNodes() {
        preparingAtCycle(4L);
        var unit = new DepartmentCode("117");
        var preparing = repo.eligibility(ID, unit, "99999");
        assertFalse(preparing.canPrepare());
        assertTrue(preparing.canReadRequest());
        assertEquals("PREPARING", preparing.requestStatus());
        assertEquals(5L, preparing.nextCycle());
        pendingAtCycle(4L);
        var pending = repo.eligibility(ID, unit, "99999");
        assertFalse(pending.canPrepare());
        assertTrue(pending.canReadRequest());
        assertEquals("PENDING", pending.requestStatus());
        assertFalse(repo.eligibility(ID, unit, "different").canReadRequest());
        returnedAtCycle(4L);
        var returned = repo.eligibility(ID, unit, "99999");
        assertFalse(returned.canPrepare());
        assertTrue(returned.canReadRequest());
        assertEquals("RETURNED", returned.requestStatus());
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }

    @Test void preparingExplanationCanBeRevisedAndDoesNotPublishToManager() {
        preparingAtCycle(4L);
        assertEquals("PREPARING", repo.revise(ID, new DepartmentCode("117"),
            "99999", REQUEST_ID, 0L, "Updated draft").status());
        verify(nodes).setProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReason"), "Updated draft");
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")), any());
    }

    @Test void preparedRequestPublishedOnlyAfterExplicitOwnerSubmission() {
        preparingAtCycle(4L);
        repo.submitPrepared(ID, new DepartmentCode("117"), "99999", REQUEST_ID);
        verify(nodes).setProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus"), "PENDING");
        verify(nodes).setProperty(parent,
            AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId"), REQUEST_ID);
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
    }

    @Test void preparedRequestCannotBeSubmittedByAnotherSecretaryOrAfterAlreadyPending() {
        preparingAtCycle(4L);
        assertThrows(org.badea.correspondence.security.CorrespondenceAuthorizationException.class,
            () -> repo.submitPrepared(ID, new DepartmentCode("117"), "other", REQUEST_ID));
        pendingAtCycle(4L);
        assertThrows(OfficeCycleConflictException.class, () -> repo.submitPrepared(ID,
            new DepartmentCode("117"), "99999", REQUEST_ID));
    }

    @Test void returnedExplanationCanBeRevisedWithoutClearingManagerDecision() {
        returnedAtCycle(5L);
        var revised = repo.revise(ID, new DepartmentCode("117"), "99999", REQUEST_ID, 1L,
            "  Corrected follow-up  ");
        assertEquals("RETURNED", revised.status());
        assertEquals("Replace incorrect PDF", revised.returnReason());
        assertEquals(1L, revised.returnVersion());
        verify(nodes).setProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReason"), "Corrected follow-up");
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")), any());
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
    }

    @Test void sameReturnedRequestResubmitsForManagerWithoutCreatingAnotherRequest() {
        returnedAtCycle(4L);
        repo.resubmit(ID, new DepartmentCode("117"), "99999", REQUEST_ID, 1L);
        verify(nodes).setProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus"), "PENDING");
        verify(nodes).setProperty(parent,
            AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId"), REQUEST_ID);
        verify(nodes, never()).createNode(eq(parent), any(), any(),
            eq(AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoRequest")), anyMap());
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
    }

    @Test void replayWrongOwnerOrWrongReturnVersionCannotReviseOrResubmit() {
        returnedAtCycle(4L);
        assertThrows(org.badea.correspondence.security.CorrespondenceAuthorizationException.class,
            () -> repo.revise(ID, new DepartmentCode("117"), "another-owner", REQUEST_ID,
                1L, "Revision"));
        assertThrows(OfficeCycleConflictException.class,
            () -> repo.resubmit(ID, new DepartmentCode("117"), "99999", REQUEST_ID, 0L));
        assertThrows(OfficeCycleConflictException.class,
            () -> repo.revise(ID, new DepartmentCode("117"), "99999", REQUEST_ID,
                2L, "Revision"));
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")), any());
    }

    @Test void staleCycleOrNewPendingRequestPreventsReturnedRevisionAndResubmission() {
        returnedAtCycle(4L);
        when(nodes.getProperty(parent, AlfrescoCorrespondenceCycleReader.CURRENT)).thenReturn(5L);
        assertThrows(OfficeCycleConflictException.class, () -> repo.resubmit(ID,
            new DepartmentCode("117"), "99999", REQUEST_ID, 1L));
        when(nodes.getProperty(parent, AlfrescoCorrespondenceCycleReader.CURRENT)).thenReturn(4L);
        when(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId")))
            .thenReturn("22222222-2222-4222-8222-222222222222");
        assertThrows(OfficeCycleConflictException.class, () -> repo.revise(ID,
            new DepartmentCode("117"), "99999", REQUEST_ID, 1L, "Revision"));
        verify(nodes, never()).setProperty(eq(followUp),
            eq(AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReason")), any());
    }

    @Test void repeatedRequestCannotResubmitAfterAlreadyPendingOrSecondReturn() {
        pendingAtCycle(4L);
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnVersion")))
            .thenReturn(1L);
        assertThrows(OfficeCycleConflictException.class, () -> repo.resubmit(ID,
            new DepartmentCode("117"), "99999", REQUEST_ID, 1L));
        returnedAtCycle(4L);
        when(nodes.getProperty(followUp, AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnVersion")))
            .thenReturn(2L);
        assertThrows(OfficeCycleConflictException.class, () -> repo.resubmit(ID,
            new DepartmentCode("117"), "99999", REQUEST_ID, 1L));
    }

    @Test void anotherNewRequestCannotReplaceReturnedRequestForSameRound() {
        returnedAtCycle(4L);
        assertThrows(OfficeCycleConflictException.class, () -> repo.submit(ID,
            new DepartmentCode("117"), "another-secretary", "Different request"));
        verify(nodes, never()).createNode(eq(parent), any(), any(),
            eq(AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoRequest")), anyMap());
    }

    @Test void returnEditResubmitApproveAdvancesExactlyOnceWithoutLosingTheOriginalReturn() {
        pendingAtCycle(3L);
        var marker = AlfrescoDepartmentAssignmentReader.q("roundTwoPendingRequestId");
        var status = AlfrescoDepartmentAssignmentReader.q("roundTwoRequestStatus");
        var explanation = AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReason");
        var reason = AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnReason");
        var version = AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnVersion");
        var updatedAt = AlfrescoDepartmentAssignmentReader.q("roundTwoRequestAt");
        Map<org.alfresco.service.namespace.QName, Serializable> state = new HashMap<>();
        state.put(status, "PENDING");
        state.put(explanation, "Please continue");
        state.put(version, 0L);
        state.put(updatedAt, Date.from(Instant.parse("2026-11-04T12:00:00Z")));
        for (var key : List.of(status, explanation, reason, version, updatedAt)) {
            when(nodes.getProperty(followUp, key)).thenAnswer(call -> state.get(key));
        }
        doAnswer(call -> {
            state.put(call.getArgument(1), call.getArgument(2));
            return null;
        }).when(nodes).setProperty(eq(followUp), any(), nullable(Serializable.class));

        AtomicReference<Serializable> pointer = new AtomicReference<>(REQUEST_ID);
        when(nodes.getProperty(parent, marker)).thenAnswer(call -> pointer.get());
        doAnswer(call -> {
            pointer.set(call.getArgument(2));
            return null;
        }).when(nodes).setProperty(eq(parent), eq(marker), nullable(Serializable.class));

        var returned = repo.returnForRevision(ID, new DepartmentCode("117"),
            REQUEST_ID, "200", "New version of the report required");
        assertEquals("RETURNED", returned.status());
        assertNull(pointer.get());
        assertEquals(1L, repo.current(ID, new DepartmentCode("117"), "99999").returnVersion());
        var revised = repo.revise(ID, new DepartmentCode("117"), "99999", REQUEST_ID,
            1L, "Revised report attached");
        assertEquals("RETURNED", revised.status());
        assertEquals("Revised report attached", revised.reason());
        assertEquals("New version of the report required", revised.returnReason());

        var pending = repo.resubmit(ID, new DepartmentCode("117"), "99999", REQUEST_ID, 1L);
        assertEquals("PENDING", pending.status());
        assertEquals(REQUEST_ID, pointer.get());
        assertEquals("New version of the report required", pending.returnReason());
        assertEquals("Revised report attached", repo.pending(ID, new DepartmentCode("117")).reason());
        assertThrows(OfficeCycleConflictException.class, () -> repo.resubmit(ID,
            new DepartmentCode("117"), "99999", REQUEST_ID, 1L));

        var next = repo.approve(ID, new DepartmentCode("117"), REQUEST_ID,
            "200", () -> 4L);
        assertEquals(4L, next.workflowCycle());
        assertNull(pointer.get());
        assertEquals("APPROVED", state.get(status));
        assertThrows(OfficeCycleConflictException.class, () -> repo.approve(ID,
            new DepartmentCode("117"), REQUEST_ID, "200", () -> 5L));
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
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
        verify(nodes).setProperty(followUp,
            AlfrescoDepartmentAssignmentReader.q("roundTwoRequestReturnVersion"), 1L);
        verify(nodes).createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS), any(),
            eq(AlfrescoDepartmentAssignmentReader.q("secretaryRoundTwoDecisionEvent")), anyMap());
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

package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceMailboxListerTest {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final DepartmentCode DEPARTMENT = new DepartmentCode("117");

    private NodeService nodes;
    private PermissionService permissions;
    private CorrespondenceFolderLocator folders;
    private NodeRef root;
    private NodeRef folder;
    private NodeRef document;
    private AlfrescoCorrespondenceMailboxLister lister;

    @BeforeEach
    void setUp() {
        nodes = mock(NodeService.class);
        permissions = mock(PermissionService.class);
        folders = mock(CorrespondenceFolderLocator.class);
        root = node("11111111-1111-1111-1111-111111111111");
        folder = node("22222222-2222-2222-2222-222222222222");
        document = node("33333333-3333-3333-3333-333333333333");

        ChildAssociationRef folderAssoc = association(folder);
        ChildAssociationRef documentAssoc = association(document);
        when(folders.locateCorrespondenceRoot()).thenReturn(root);
        when(folders.locateDepartmentFolder(DEPARTMENT)).thenReturn(folder);
        when(nodes.getChildAssocs(root)).thenReturn(List.of(folderAssoc));
        when(nodes.getChildAssocs(folder)).thenReturn(List.of(documentAssoc));
        when(permissions.hasPermission(folder, PermissionService.READ))
                .thenReturn(AccessStatus.ALLOWED);
        when(permissions.hasPermission(document, PermissionService.READ))
                .thenReturn(AccessStatus.ALLOWED);
        when(nodes.getType(folder)).thenReturn(ContentModel.TYPE_FOLDER);
        when(nodes.getType(document)).thenReturn(qname("correspondence"));
        prop("technicalId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        prop("subject", "Subject");
        prop("documentNumber", "DOC-1");
        prop("originDepartmentCode", "117");
        prop("state", "WITH_PRESIDENT_OFFICE");
        prop("assignedRole", "PRESIDENT_OFFICE");
        prop("createdByNameEnglish", "Director");
        when(nodes.getProperty(document, ContentModel.PROP_CREATED))
                .thenReturn(Date.from(Instant.parse("2026-10-18T08:00:00Z")));
        when(nodes.getProperty(document, ContentModel.PROP_MODIFIED))
                .thenReturn(Date.from(Instant.parse("2026-10-18T09:00:00Z")));

        lister = new AlfrescoCorrespondenceMailboxLister(nodes, permissions, folders);
    }

    @Test
    void secretaryCanReadDirectorInboxButNotClaimDirectorAction() {
        prop("state", "WITH_DIRECTOR");
        prop("assignedRole", "DIRECTOR");
        prop("assignedDepartmentCode", "117");
        var director = lister.list("inbox", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "manager", "needs_action", 0, 10);
        assertEquals(1, director.total());
        assertTrue(director.items().getFirst().needsAction());

        var secretary = lister.listSecretaryInbox(DEPARTMENT, "99999", "needs_action", 0, 10);
        assertEquals(0, secretary.total());
        var all = lister.listSecretaryInbox(DEPARTMENT, "99999", "all", 0, 10);
        assertEquals(1, all.total());
        assertFalse(all.items().getFirst().needsAction());
        assertEquals(0, lister.listSecretaryInbox(DEPARTMENT, "99999", "handled", 0, 10).total());
    }

    @Test
    void onlyOwnerReturnedCurrentCycleContinuationIsSecretaryActionable() {
        prop("state", "WITH_DIRECTOR");
        prop("assignedRole", "DIRECTOR");
        prop("assignedDepartmentCode", "117");
        NodeRef assignment = node("55555555-5555-5555-5555-555555555555");
        NodeRef request = node("66666666-6666-6666-6666-666666666666");
        ChildAssociationRef assignmentAssoc = association(assignment);
        ChildAssociationRef requestAssoc = association(request);
        when(nodes.getChildAssocs(document)).thenReturn(List.of(assignmentAssoc, requestAssoc));
        when(nodes.getType(assignment)).thenReturn(AlfrescoDepartmentAssignmentReader.TYPE);
        when(nodes.getProperty(assignment, qname("assignmentId"))).thenReturn("a1");
        when(nodes.getProperty(assignment, qname("assignmentDepartmentCode"))).thenReturn("117");
        when(nodes.getProperty(assignment, qname("assignmentRecipientType"))).thenReturn("TO");
        when(nodes.getProperty(assignment, qname("assignmentStatus"))).thenReturn("COMPLETED");
        when(nodes.getProperty(assignment, qname("assignmentByEmployeeId"))).thenReturn("manager");
        when(nodes.getProperty(assignment, qname("assignmentCompletedByEmployeeId"))).thenReturn("manager");
        when(nodes.getProperty(assignment, qname("assignmentAt")))
                .thenReturn(Date.from(Instant.parse("2026-11-04T08:00:00Z")));
        when(nodes.getProperty(assignment, qname("assignmentCompletedAt")))
                .thenReturn(Date.from(Instant.parse("2026-11-04T09:00:00Z")));
        when(nodes.getType(request)).thenReturn(qname("secretaryRoundTwoRequest"));
        when(nodes.getProperty(request, qname("roundTwoRequestOwner"))).thenReturn("99999");
        when(nodes.getProperty(request, qname("roundTwoRequestUnit"))).thenReturn("117");
        when(nodes.getProperty(request, qname("roundTwoRequestStatus"))).thenReturn("RETURNED");
        when(nodes.getProperty(request, qname("roundTwoRequestCycle"))).thenReturn(1L);
        assertEquals(1, lister.listSecretaryInbox(DEPARTMENT, "99999", "needs_action", 0, 10).total());
        assertEquals(1, lister.listSecretaryInbox(DEPARTMENT, "99999", "returned", 0, 10).total());
        assertTrue(lister.listSecretaryInbox(DEPARTMENT, "99999", "all", 0, 10)
                .items().getFirst().needsAction());
        assertEquals(0, lister.listSecretaryInbox(DEPARTMENT, "other", "needs_action", 0, 10).total());
        when(nodes.getProperty(request, qname("roundTwoRequestStatus"))).thenReturn("PENDING");
        assertEquals(0, lister.listSecretaryInbox(DEPARTMENT, "99999", "needs_action", 0, 10).total());
        when(nodes.getProperty(request, qname("roundTwoRequestStatus"))).thenReturn("RETURNED");
        when(nodes.getProperty(request, qname("roundTwoRequestCycle"))).thenReturn(2L);
        assertEquals(0, lister.listSecretaryInbox(DEPARTMENT, "99999", "needs_action", 0, 10).total());
        when(nodes.getProperty(request, qname("roundTwoRequestCycle"))).thenReturn(1L);
        when(nodes.getProperty(assignment, qname("assignmentStatus"))).thenReturn("PENDING");
        when(nodes.getProperty(assignment, qname("assignmentCompletedByEmployeeId"))).thenReturn(null);
        when(nodes.getProperty(assignment, qname("assignmentCompletedAt"))).thenReturn(null);
        assertEquals(0, lister.listSecretaryInbox(DEPARTMENT, "99999", "needs_action", 0, 10).total());
    }

    @Test
    void exactCaseReadMatchesDirectorUnitInboxAndNeverExposesDrafts() {
        String id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        prop("state", "WITH_DIRECTOR");
        prop("assignedRole", "DIRECTOR");
        prop("assignedDepartmentCode", "117");
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "99999", "all", 0, 10).total());
        assertTrue(lister.containsDirectorInboxCase(id, DEPARTMENT));
        assertFalse(lister.containsDirectorInboxCase(id, new DepartmentCode("118")));
        assertFalse(lister.containsDirectorInboxCase("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", DEPARTMENT));
        prop("state", "DRAFT");
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "99999", "all", 0, 10).total());
        assertFalse(lister.containsDirectorInboxCase(id, DEPARTMENT));
    }

    @Test
    void exactCaseReadRejectsAnUnreceivedOfficeCaseAndMalformedIds() {
        String id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "99999", "all", 0, 10).total());
        assertFalse(lister.containsDirectorInboxCase(id, DEPARTMENT));
        assertThrows(IllegalArgumentException.class,
                () -> lister.containsDirectorInboxCase("not-an-id", DEPARTMENT));
    }

    @Test
    void directorSentContainsOnlyTheirOwnSubmittedCorrespondence() {
        action("10001", "DIRECTOR", "PRESIDENT_OFFICE", null);
        var page = lister.list("sent", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "10001", "all", 0, 25);
        assertEquals(1, page.total());
        assertEquals("Subject", page.items().getFirst().subject());
        assertEquals("WITH_PRESIDENT_OFFICE", page.items().getFirst().state());
        verify(folders).locateCorrespondenceRoot();
    }

    @Test
    void directorSentExcludesUnsubmittedDrafts() {
        prop("state", "DRAFT");
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, 0, 25).total());
    }

    @Test
    void directorSentExcludesAnotherEmployeeEvenInSameDepartment() {
        action("10002", "DIRECTOR", "PRESIDENT_OFFICE", null);
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "10001", "all", 0, 25).total());
        assertEquals(1, lister.list("sent", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "10002", "all", 0, 25).total());
    }

    @Test
    void officeInboxIsSharedAcrossAuthorizedOfficeEmployees() {
        prop("createdByCorrespondenceRole", "PRESIDENT_OFFICE");
        prop("createdByEmployeeId", "10001");
        for (String employeeId : List.of("10001", "10002")) {
            assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                    null, employeeId, "all", 0, 25).total());
        }
    }

    @Test
    void officeSentIsPersonalEvenThoughInboxIsShared() {
        prop("createdByCorrespondenceRole", "PRESIDENT_OFFICE");
        prop("createdByEmployeeId", "10001");
        action("10001", "PRESIDENT_OFFICE", "PRESIDENT_OFFICE", null);
        assertEquals(1, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10002", "all", 0, 25).total());
        // The same item still appears in the shared Office Inbox for both.
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10002", "all", 0, 25).total());
    }

    @Test
    void officeInboxNeverShowsDirectorDraft() {
        prop("state", "DRAFT");
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25).total());
    }

    @Test
    void executiveDraftsOnlyAppearInTheirCreatorsDraftsList() {
        prop("state", "DRAFT");
        prop("createdByCorrespondenceRole", "PRESIDENT_OFFICE");
        prop("createdByEmployeeId", "10001");
        assertEquals(1, lister.list("drafts", CorrespondenceActorRole.PRESIDENT_OFFICE,
                DEPARTMENT, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("drafts", CorrespondenceActorRole.PRESIDENT_OFFICE,
                DEPARTMENT, "10002", "all", 0, 25).total());
        assertEquals(0, lister.list("drafts", CorrespondenceActorRole.PRESIDENT,
                DEPARTMENT, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("drafts", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                DEPARTMENT, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                DEPARTMENT, "10001", "all", 0, 25).total());
    }

    @Test
    void presidentInboxShowsOnlyPresidentAssignment() {
        prop("state", "WITH_PRESIDENT");
        prop("assignedRole", "PRESIDENT");
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT, null, 0, 25).total());
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25).total());
    }

    @Test
    void presidentInboxIsSharedButPresidentSentRemainsPersonal() {
        prop("state", "WITH_PRESIDENT");
        prop("assignedRole", "PRESIDENT");
        action("10001", "PRESIDENT_OFFICE", "PRESIDENT", null);
        for (String presidentEmployee : List.of("10002", "10003")) {
            assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT,
                    null, presidentEmployee, "all", 0, 25).total());
            assertEquals(0, lister.list("sent", CorrespondenceActorRole.PRESIDENT,
                    null, presidentEmployee, "all", 0, 25).total());
        }
        assertEquals(1, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10002", "all", 0, 25).total());
    }

    @Test
    void directorInboxMustMatchAssignedDepartment() {
        prop("state", "WITH_DIRECTOR");
        prop("assignedRole", "DIRECTOR");
        prop("assignedDepartmentCode", "118");
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, 0, 25).total());
        prop("assignedDepartmentCode", "117");
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, 0, 25).total());
    }

    @Test
    void hidesNodesWithoutAlfrescoReadPermission() {
        when(permissions.hasPermission(document, PermissionService.READ))
                .thenReturn(AccessStatus.DENIED);
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25).total());
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, 0, 25).total());
    }

    @Test
    void doesNotLeakCorrespondenceFromUnreadableDepartmentFolder() {
        when(permissions.hasPermission(folder, PermissionService.READ))
                .thenReturn(AccessStatus.DENIED);
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25).total());
    }

    @Test
    void presidentCannotMistakeMissingDepartmentReadPermissionsForAnEmptyInbox() {
        when(permissions.hasPermission(folder, PermissionService.READ))
                .thenReturn(AccessStatus.DENIED);
        assertThrows(CorrespondenceMailboxPermissionException.class,
                () -> lister.list("inbox", CorrespondenceActorRole.PRESIDENT,
                        null, "president", "all", 0, 25));
        // Other role semantics and document permissions remain unchanged.
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "office", "all", 0, 25).total());
    }

    @Test
    void presidentCannotReceiveSilentlyEmptyInboxWhenDocumentAclDeniesRead() {
        prop("state", "WITH_PRESIDENT");
        prop("assignedRole", "PRESIDENT");
        when(permissions.hasPermission(document, PermissionService.READ))
                .thenReturn(AccessStatus.DENIED);
        assertThrows(CorrespondenceMailboxPermissionException.class,
                () -> lister.list("inbox", CorrespondenceActorRole.PRESIDENT,
                        null, "president", "all", 0, 25));
    }

    @Test
    void officeAndPresidentSentAreAvailableWithoutSecurityBypass() {
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.PRESIDENT,
                null, "10001", "all", 0, 25).total());
    }

    @Test
    void refusesDirectorWithNoDepartment() {
        assertThrows(CorrespondenceAuthorizationException.class,
                () -> lister.list("inbox", CorrespondenceActorRole.DIRECTOR, null, 0, 25));
    }

    @Test
    void rejectsBadPagination() {
        assertThrows(IllegalArgumentException.class,
                () -> lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, -1, 25));
        assertThrows(IllegalArgumentException.class,
                () -> lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 101));
    }

    @Test
    void historicalOfficeInboxRemainsVisibleAfterForwardAndIsNotActionable() {
        action("10001", "PRESIDENT_OFFICE", "PRESIDENT", null);
        // The Office received this correspondence before forwarding it to the President.
        NodeRef directorAction = node("55555555-5555-5555-5555-555555555555");
        ChildAssociationRef inboundAssoc = association(directorAction);
        ChildAssociationRef outboundAssoc = association(
                node("44444444-4444-4444-4444-444444444444"));
        when(nodes.getChildAssocs(document)).thenReturn(List.of(inboundAssoc, outboundAssoc));
        when(nodes.getType(directorAction)).thenReturn(qname("workflowAction"));
        when(nodes.getProperty(directorAction, qname("actorEmployeeId")))
                .thenReturn("10003");
        when(nodes.getProperty(directorAction, qname("actorRole")))
                .thenReturn("DIRECTOR");
        when(nodes.getProperty(directorAction, qname("targetRole")))
                .thenReturn("PRESIDENT_OFFICE");
        when(nodes.getProperty(directorAction, qname("actedAt")))
                .thenReturn(Date.from(Instant.parse("2026-10-18T07:00:00Z")));
        prop("state", "WITH_PRESIDENT");
        prop("assignedRole", "PRESIDENT");
        var all = lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "all", 0, 25);
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "needs_action", 0, 25).total());
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "handled", 0, 25).total());
        assertEquals(1, all.total());
        assertFalse(all.items().getFirst().needsAction());
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10002", "all", 0, 25).total());
        assertEquals(1, lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "all", 0, 25).total());
    }

    @Test
    void presidentSentAndReceivedRemainVisibleAfterReturn() {
        action("10001", "PRESIDENT", "PRESIDENT_OFFICE", null);
        assertEquals(1, lister.list("sent", CorrespondenceActorRole.PRESIDENT,
                null, "10001", "all", 0, 25).total());
        // President had to receive previously; set an inbound action as well.
        NodeRef second = node("55555555-5555-5555-5555-555555555555");
        ChildAssociationRef sentAssoc = association(
                node("44444444-4444-4444-4444-444444444444"));
        ChildAssociationRef receivedAssoc = association(second);
        when(nodes.getChildAssocs(document)).thenReturn(List.of(sentAssoc, receivedAssoc));
        when(nodes.getType(second)).thenReturn(qname("workflowAction"));
        when(nodes.getProperty(second,qname("actorEmployeeId"))).thenReturn("10001");
        when(nodes.getProperty(second,qname("actorRole"))).thenReturn("PRESIDENT_OFFICE");
        when(nodes.getProperty(second,qname("targetRole"))).thenReturn("PRESIDENT");
        when(nodes.getProperty(second,qname("actedAt")))
                .thenReturn(Date.from(Instant.parse("2026-10-18T07:00:00Z")));
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT,
                null, "10001", "all", 0, 25).total());
    }

    @Test
    void unreadInboxIsFilteredServerSideWithCorrectTotal() {
        var receipts = mock(AlfrescoCorrespondenceReadReceipts.class);
        lister = new AlfrescoCorrespondenceMailboxLister(nodes, permissions, folders, receipts);
        when(receipts.latestIncoming(document, CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", false)).thenReturn(7L);
        when(receipts.isUnread(document, "10001", 7L)).thenReturn(true);
        var unread = lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "unread", 0, 25);
        assertEquals(1, unread.total());
        assertTrue(unread.items().getFirst().unread());
        assertEquals(7L, unread.items().getFirst().incomingSequence());
        when(receipts.isUnread(document, "10001", 7L)).thenReturn(false);
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "unread", 0, 25).total());
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE,
                null, "10001", "all", 0, 25).total());
    }

    @Test
    void presidentDraftRequiresSavedReviewSinceLatestAssignment() {
        action("10001", "PRESIDENT_OFFICE", "PRESIDENT", null);
        prop("state", "WITH_PRESIDENT");
        prop("assignedRole", "PRESIDENT");
        prop("presidentReviewUpdatedByEmployeeId", "10001");
        when(nodes.getProperty(document,qname("presidentReviewRevision"))).thenReturn(2L);
        when(nodes.getProperty(document,qname("presidentReviewUpdatedAt")))
                .thenReturn(Date.from(Instant.parse("2026-10-18T09:00:00Z")));
        assertEquals(1, lister.list("drafts", CorrespondenceActorRole.PRESIDENT,
                null, "10001", "all", 0, 25).total());
        assertEquals(0, lister.list("drafts", CorrespondenceActorRole.PRESIDENT,
                null, "10002", "all", 0, 25).total());
        when(nodes.getProperty(document,qname("presidentReviewUpdatedAt")))
                .thenReturn(Date.from(Instant.parse("2026-10-18T07:00:00Z")));
        assertEquals(0, lister.list("drafts", CorrespondenceActorRole.PRESIDENT,
                null, "10001", "all", 0, 25).total());
    }

    @Test
    void returnedSecretaryDraftsShareOneBoundedPageWithRoutedInbox() {
        // Ten normal cases plus two returned personal drafts: never 12 visible
        // against a footer saying 1-10 of 10.
        prop("state", "WITH_DIRECTOR");
        prop("assignedRole", "DIRECTOR");
        prop("assignedDepartmentCode", "117");
        java.util.ArrayList<ChildAssociationRef> children = new java.util.ArrayList<>();
        children.add(association(document));
        for (int i = 1; i < 10; i++) {
            NodeRef normal = node(String.format("00000000-0000-0000-0000-%012d", i));
            children.add(association(normal));
            when(nodes.getType(normal)).thenReturn(qname("correspondence"));
            when(permissions.hasPermission(normal, PermissionService.READ))
                .thenReturn(AccessStatus.ALLOWED);
            when(nodes.getProperty(normal, qname("technicalId")))
                .thenReturn(String.format("10000000-0000-0000-0000-%012d", i));
            when(nodes.getProperty(normal, qname("subject"))).thenReturn("Normal " + i);
            when(nodes.getProperty(normal, qname("state"))).thenReturn("WITH_DIRECTOR");
            when(nodes.getProperty(normal, qname("originDepartmentCode"))).thenReturn("117");
            when(nodes.getProperty(normal, qname("assignedRole"))).thenReturn("DIRECTOR");
            when(nodes.getProperty(normal, qname("assignedDepartmentCode"))).thenReturn("117");
            when(nodes.getProperty(normal, qname("createdByNameEnglish"))).thenReturn("Manager");
            when(nodes.getProperty(normal, ContentModel.PROP_CREATED))
                .thenReturn(Date.from(Instant.parse("2026-11-09T08:00:00Z")));
            when(nodes.getProperty(normal, ContentModel.PROP_MODIFIED))
                .thenReturn(Date.from(Instant.parse("2026-11-09T09:00:00Z")));
        }
        for (int i = 1; i <= 2; i++) {
            NodeRef returned = node(String.format("20000000-0000-0000-0000-%012d", i));
            children.add(association(returned));
            when(nodes.getType(returned)).thenReturn(qname("correspondence"));
            when(permissions.hasPermission(returned, PermissionService.READ))
                .thenReturn(AccessStatus.ALLOWED);
            when(nodes.getProperty(returned, qname("technicalId")))
                .thenReturn(String.format("30000000-0000-0000-0000-%012d", i));
            when(nodes.getProperty(returned, qname("subject"))).thenReturn("Returned " + i);
            when(nodes.getProperty(returned, qname("state"))).thenReturn("DRAFT");
            when(nodes.getProperty(returned, qname("originDepartmentCode"))).thenReturn("117");
            when(nodes.getProperty(returned, qname("secretaryOwnerEmployeeId"))).thenReturn("99999");
            when(nodes.getProperty(returned, qname("secretaryApprovalStatus")))
                .thenReturn("RETURNED");
            when(nodes.getProperty(returned, qname("createdByNameEnglish"))).thenReturn("Secretary");
            when(nodes.getProperty(returned, ContentModel.PROP_CREATED))
                .thenReturn(Date.from(Instant.parse("2026-11-11T08:00:00Z")));
            when(nodes.getProperty(returned, ContentModel.PROP_MODIFIED))
                .thenReturn(Date.from(Instant.parse("2026-11-11T09:00:00Z")));
        }
        // Do not put the mock action construction inside another Mockito stubbing.
        NodeRef returnedOne = node("20000000-0000-0000-0000-000000000001");
        NodeRef action = node("40000000-0000-0000-0000-000000000001");
        ChildAssociationRef actionAssoc = association(action);
        when(nodes.getChildAssocs(returnedOne)).thenReturn(List.of(actionAssoc));
        when(nodes.getType(action)).thenReturn(qname("workflowAction"));
        when(nodes.getProperty(action, qname("actionType"))).thenReturn("RETURN_TO_SECRETARY");
        when(nodes.getProperty(action, qname("actionSequence"))).thenReturn(2L);
        when(nodes.getProperty(action, qname("explanation")))
            .thenReturn("Please replace the PDF");
        when(nodes.getChildAssocs(folder)).thenReturn(children);

        var first = lister.listSecretaryInbox(DEPARTMENT, "99999", "all", 0, 10);
        var second = lister.listSecretaryInbox(DEPARTMENT, "99999", "all", 10, 10);
        assertEquals(12, first.total());
        assertEquals(12, second.total());
        assertEquals(10, first.items().size());
        assertEquals(2, second.items().size());
        assertEquals(2, first.items().stream()
                .filter(row -> "RETURNED".equals(row.approvalStatus())).count());
        assertEquals("Please replace the PDF", first.items().stream()
                .filter(row -> "Returned 1".equals(row.subject()))
                .findFirst().orElseThrow().returnReason());
        assertEquals(2, lister.listSecretaryInbox(DEPARTMENT,
                "99999", "returned", 0, 10).total());
        assertEquals(2, lister.listSecretaryInbox(DEPARTMENT,
                "99999", "needs_action", 0, 10).total());
        assertEquals(0, lister.listSecretaryInbox(DEPARTMENT,
                "99999", "unread", 0, 10).total());
        assertEquals(10, lister.listSecretaryInbox(DEPARTMENT,
                "other-secretary", "all", 0, 10).total());
        assertEquals(10, lister.list("inbox", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "manager", "all", 0, 10).total());
        assertThrows(IllegalArgumentException.class, () ->
            lister.list("inbox", CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT, "manager", "returned", 0, 10));
    }

    private void action(String employee, String role, String target, String targetDepartment) {
        NodeRef action = node("44444444-4444-4444-4444-444444444444");
        ChildAssociationRef actionAssoc = association(action);
        when(nodes.getChildAssocs(document)).thenReturn(List.of(actionAssoc));
        when(nodes.getType(action)).thenReturn(qname("workflowAction"));
        when(nodes.getProperty(action, qname("actorEmployeeId"))).thenReturn(employee);
        when(nodes.getProperty(action, qname("actorRole"))).thenReturn(role);
        when(nodes.getProperty(action, qname("targetRole"))).thenReturn(target);
        if (targetDepartment != null)
            when(nodes.getProperty(action, qname("targetDepartmentCode")))
                    .thenReturn(targetDepartment);
        when(nodes.getProperty(action, qname("actedAt")))
                .thenReturn(Date.from(Instant.parse("2026-10-18T08:00:00Z")));
    }

    private void prop(String name, String value) {
        when(nodes.getProperty(document, qname(name))).thenReturn(value);
    }

    private static NodeRef node(String id) {
        return new NodeRef("workspace://SpacesStore/" + id);
    }

    private static ChildAssociationRef association(NodeRef child) {
        ChildAssociationRef assoc = mock(ChildAssociationRef.class);
        when(assoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(assoc.getChildRef()).thenReturn(child);
        return assoc;
    }

    private static QName qname(String name) {
        return QName.createQName(NS, name);
    }
}

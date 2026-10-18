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
    void directorSentContainsSubmittedCorrespondenceFromOwnDepartment() {
        var page = lister.list("sent", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, 0, 25);
        assertEquals(1, page.total());
        assertEquals("Subject", page.items().getFirst().subject());
        assertEquals("WITH_PRESIDENT_OFFICE", page.items().getFirst().state());
        verify(folders).locateDepartmentFolder(DEPARTMENT);
    }

    @Test
    void directorSentExcludesUnsubmittedDrafts() {
        prop("state", "DRAFT");
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, 0, 25).total());
    }

    @Test
    void directorSentExcludesAnotherDepartment() {
        prop("originDepartmentCode", "118");
        assertEquals(0, lister.list("sent", CorrespondenceActorRole.DIRECTOR, DEPARTMENT, 0, 25).total());
    }

    @Test
    void officeInboxShowsAssignedOfficeCorrespondence() {
        var page = lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25);
        assertEquals(1, page.total());
    }

    @Test
    void officeInboxNeverShowsDirectorDraft() {
        prop("state", "DRAFT");
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25).total());
    }

    @Test
    void presidentInboxShowsOnlyPresidentAssignment() {
        prop("state", "WITH_PRESIDENT");
        prop("assignedRole", "PRESIDENT");
        assertEquals(1, lister.list("inbox", CorrespondenceActorRole.PRESIDENT, null, 0, 25).total());
        assertEquals(0, lister.list("inbox", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25).total());
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
    void rejectsSentForRolesWithoutImplementedHistoricalAccess() {
        assertThrows(CorrespondenceAuthorizationException.class,
                () -> lister.list("sent", CorrespondenceActorRole.PRESIDENT_OFFICE, null, 0, 25));
        assertThrows(CorrespondenceAuthorizationException.class,
                () -> lister.list("sent", CorrespondenceActorRole.PRESIDENT, null, 0, 25));
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

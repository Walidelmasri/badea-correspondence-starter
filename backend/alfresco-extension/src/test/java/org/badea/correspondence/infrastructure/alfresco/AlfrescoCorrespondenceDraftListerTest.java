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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceDraftListerTest {

    private static final String NAMESPACE_URI = "http://www.badea.org/model/correspondence/1.0";

    private static final DepartmentCode DEPARTMENT = new DepartmentCode("117");

    private NodeService nodeService;
    private PermissionService permissionService;
    private CorrespondenceFolderLocator folderLocator;

    private NodeRef departmentFolder;
    private NodeRef correspondence;

    private AlfrescoCorrespondenceDraftLister lister;

    @BeforeEach
    void setUp() {

        nodeService = mock(NodeService.class);
        permissionService = mock(PermissionService.class);
        folderLocator = mock(CorrespondenceFolderLocator.class);

        departmentFolder = node("11111111-1111-1111-1111-111111111111");
        correspondence = node("22222222-2222-2222-2222-222222222222");

        when(folderLocator.locateDepartmentFolder(DEPARTMENT))
                .thenReturn(departmentFolder);

        ChildAssociationRef draftAssociation = association(correspondence);

        when(nodeService.getChildAssocs(departmentFolder))
                .thenReturn(List.of(draftAssociation));

        draft(
                correspondence,
                "draft-1",
                "Test Draft",
                "117",
                "2026-10-18T08:00:00Z");

        when(permissionService.hasPermission(
                correspondence,
                PermissionService.READ))
                .thenReturn(AccessStatus.ALLOWED);

        lister = new AlfrescoCorrespondenceDraftLister(
                nodeService,
                permissionService,
                folderLocator);
    }

    @Test
    void listsDraftsFromOwnDepartment() {

        var page = lister.list(DEPARTMENT, 0, 25);

        assertEquals(1, page.total());
        assertEquals(1, page.items().size());

        assertEquals(
                "draft-1",
                page.items().getFirst().technicalId());

        assertEquals(
                "Test Draft",
                page.items().getFirst().subject());

        verify(folderLocator)
                .locateDepartmentFolder(DEPARTMENT);
    }

    @Test
    void excludesExecutivesDraftsEvenFromSameDepartment() {
        when(nodeService.getProperty(correspondence,
                qname("createdByCorrespondenceRole"))).thenReturn("PRESIDENT_OFFICE");
        assertEquals(0, lister.list(DEPARTMENT, 0, 25).total());
    }

    @Test
    void excludesSubmittedCorrespondence() {

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn("WITH_PRESIDENT_OFFICE");

        var page = lister.list(DEPARTMENT, 0, 25);

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.total());
    }

    @Test
    void excludesCorrespondenceWithDifferentOriginDepartment() {

        when(nodeService.getProperty(
                correspondence,
                qname("originDepartmentCode")))
                .thenReturn("118");

        var page = lister.list(DEPARTMENT, 0, 25);

        assertTrue(page.items().isEmpty());
    }

    @Test
    void excludesCorrespondenceWithoutReadPermission() {

        when(permissionService.hasPermission(
                correspondence,
                PermissionService.READ))
                .thenReturn(AccessStatus.DENIED);

        var page = lister.list(DEPARTMENT, 0, 25);

        assertTrue(page.items().isEmpty());
    }

    @Test
    void returnsNewestDraftFirstAndPaginates() {

        NodeRef second = node("33333333-3333-3333-3333-333333333333");

        draft(
                second,
                "draft-2",
                "More Recent Draft",
                "117",
                "2026-10-18T09:00:00Z");

        when(permissionService.hasPermission(
                second,
                PermissionService.READ))
                .thenReturn(AccessStatus.ALLOWED);

        ChildAssociationRef firstAssociation = association(correspondence);

        ChildAssociationRef secondAssociation = association(second);

        when(nodeService.getChildAssocs(departmentFolder))
                .thenReturn(List.of(
                        firstAssociation,
                        secondAssociation));

        var firstPage = lister.list(DEPARTMENT, 0, 1);

        assertEquals(2, firstPage.total());
        assertEquals(1, firstPage.items().size());

        assertEquals(
                "draft-2",
                firstPage.items().getFirst().technicalId());

        var secondPage = lister.list(DEPARTMENT, 1, 1);

        assertEquals(
                "draft-1",
                secondPage.items().getFirst().technicalId());
    }

    @Test
    void rejectsInvalidPagination() {

        assertThrows(
                IllegalArgumentException.class,
                () -> lister.list(DEPARTMENT, -1, 25));

        assertThrows(
                IllegalArgumentException.class,
                () -> lister.list(DEPARTMENT, 0, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> lister.list(DEPARTMENT, 0, 101));
    }

    private void draft(
            NodeRef node,
            String technicalId,
            String subject,
            String department,
            String modifiedAt) {

        when(nodeService.getType(node))
                .thenReturn(qname("correspondence"));

        when(nodeService.getProperty(
                node,
                qname("technicalId")))
                .thenReturn(technicalId);

        when(nodeService.getProperty(
                node,
                qname("state")))
                .thenReturn("DRAFT");

        when(nodeService.getProperty(
                node,
                qname("originDepartmentCode")))
                .thenReturn(department);

        when(nodeService.getProperty(
                node,
                qname("subject")))
                .thenReturn(subject);

        when(nodeService.getProperty(
                node,
                ContentModel.PROP_CREATED))
                .thenReturn(Date.from(
                        Instant.parse("2026-10-18T07:00:00Z")));

        when(nodeService.getProperty(
                node,
                ContentModel.PROP_MODIFIED))
                .thenReturn(Date.from(
                        Instant.parse(modifiedAt)));
    }

    private static ChildAssociationRef association(NodeRef node) {

        ChildAssociationRef association = mock(ChildAssociationRef.class);

        when(association.getChildRef())
                .thenReturn(node);

        when(association.getTypeQName())
                .thenReturn(ContentModel.ASSOC_CONTAINS);

        return association;
    }

    private static NodeRef node(String id) {
        return new NodeRef(
                "workspace://SpacesStore/" + id);
    }

    private static QName qname(String localName) {
        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}
package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;

import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceDraftDocumentDeleterTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private static final String DOCUMENT_ID =
            "22222222-2222-2222-2222-222222222222";

    private static final String OTHER_DOCUMENT_ID =
            "33333333-3333-3333-3333-333333333333";

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final DepartmentCode DEPARTMENT =
            new DepartmentCode("117");

    private NodeService nodeService;
    private PermissionService permissionService;
    private CorrespondenceNodeLocator nodeLocator;

    private NodeRef correspondence;
    private NodeRef document;

    private AlfrescoCorrespondenceDraftDocumentDeleter deleter;

    @BeforeEach
    void setUp() {

        nodeService = mock(NodeService.class);
        permissionService = mock(PermissionService.class);
        nodeLocator = mock(CorrespondenceNodeLocator.class);

        correspondence = node(
                "11111111-1111-1111-1111-111111111111");

        document = node(DOCUMENT_ID);

        deleter =
                new AlfrescoCorrespondenceDraftDocumentDeleter(
                        nodeService,
                        permissionService,
                        nodeLocator);

        when(nodeLocator.locate(TECHNICAL_ID))
                .thenReturn(correspondence);

        when(nodeService.getType(correspondence))
                .thenReturn(qname("correspondence"));

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn("DRAFT");

        when(nodeService.getProperty(
                correspondence,
                qname("originDepartmentCode")))
                .thenReturn("117");

        when(permissionService.hasPermission(
                correspondence,
                PermissionService.WRITE))
                .thenReturn(AccessStatus.ALLOWED);

        ChildAssociationRef association =
                sourceAssociation(document);

        when(nodeService.getChildAssocs(correspondence))
                .thenReturn(List.of(association));

        when(nodeService.exists(document))
                .thenReturn(true);

        when(nodeService.getType(document))
                .thenReturn(ContentModel.TYPE_CONTENT);

        when(permissionService.hasPermission(
                document,
                PermissionService.DELETE))
                .thenReturn(AccessStatus.ALLOWED);
    }

    @Test
    void deletesOriginalPdfFromOwnDepartmentDraft() {

        deleteAsDirector();

        verify(nodeService).deleteNode(document);
    }

    @Test
    void allowsReplacementDirectorFromSameDepartment() {

        /*
         * Authorization is department-based, not tied
         * to the employee who originally created the draft.
         */
        deleteAsDirector();

        verify(nodeService).deleteNode(document);
    }

    @Test
    void rejectsPresident() {

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> deleter.deleteDocument(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.PRESIDENT,
                        DEPARTMENT));

        verifyNoDeletion();
    }

    @Test
    void rejectsPresidentOffice() {

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> deleter.deleteDocument(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.PRESIDENT_OFFICE,
                        DEPARTMENT));

        verifyNoDeletion();
    }

    @Test
    void rejectsAnotherDepartment() {

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> deleter.deleteDocument(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("118")));

        verifyNoDeletion();
    }

    @Test
    void rejectsSubmittedCorrespondence() {

        when(nodeService.getProperty(
                correspondence,
                qname("state")))
                .thenReturn("WITH_PRESIDENT_OFFICE");

        assertThrows(
                CorrespondenceAuthorizationException.class,
                this::deleteAsDirector);

        verifyNoDeletion();
    }

    @Test
    void rejectsMissingCorrespondenceWritePermission() {

        when(permissionService.hasPermission(
                correspondence,
                PermissionService.WRITE))
                .thenReturn(AccessStatus.DENIED);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                this::deleteAsDirector);

        verifyNoDeletion();
    }

    @Test
    void rejectsMissingDocumentDeletePermission() {

        when(permissionService.hasPermission(
                document,
                PermissionService.DELETE))
                .thenReturn(AccessStatus.DENIED);

        assertThrows(
                CorrespondenceAuthorizationException.class,
                this::deleteAsDirector);

        verifyNoDeletion();
    }

    @Test
    void rejectsDocumentNotBelongingToCorrespondence() {

        assertThrows(
                CorrespondenceDocumentNotFoundException.class,
                () -> deleter.deleteDocument(
                        TECHNICAL_ID,
                        OTHER_DOCUMENT_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        DEPARTMENT));

        verifyNoDeletion();
    }

    @Test
    void rejectsWorkflowHistoryNode() {

        when(nodeService.getType(document))
                .thenReturn(qname("workflowAction"));

        assertThrows(
                CorrespondenceDocumentNotFoundException.class,
                this::deleteAsDirector);

        verifyNoDeletion();
    }

    @Test
    void rejectsGeneratedDocumentAssociation() {

        ChildAssociationRef generated =
                association(
                        document,
                        qname("generated-action-sheet"));

        when(nodeService.getChildAssocs(correspondence))
                .thenReturn(List.of(generated));

        assertThrows(
                CorrespondenceDocumentNotFoundException.class,
                this::deleteAsDirector);

        verifyNoDeletion();
    }

    @Test
    void rejectsDocumentThatNoLongerExists() {

        when(nodeService.exists(document))
                .thenReturn(false);

        assertThrows(
                CorrespondenceDocumentNotFoundException.class,
                this::deleteAsDirector);

        verifyNoDeletion();
    }

    @Test
    void rejectsInvalidDocumentId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> deleter.deleteDocument(
                        TECHNICAL_ID,
                        "../other-document",
                        CorrespondenceActorRole.DIRECTOR,
                        DEPARTMENT));

        verifyNoDeletion();
    }

    private void deleteAsDirector() {

        deleter.deleteDocument(
                TECHNICAL_ID,
                DOCUMENT_ID,
                CorrespondenceActorRole.DIRECTOR,
                DEPARTMENT);
    }

    private void verifyNoDeletion() {
        verify(nodeService, never())
                .deleteNode(any(NodeRef.class));
    }

    private static ChildAssociationRef sourceAssociation(
            NodeRef document) {

        return association(
                document,
                qname(
                        "source-document-"
                                + "44444444-4444-4444-4444-444444444444"));
    }

    private static ChildAssociationRef association(
            NodeRef document,
            QName associationName) {

        ChildAssociationRef result =
                mock(ChildAssociationRef.class);

        when(result.getTypeQName())
                .thenReturn(ContentModel.ASSOC_CONTAINS);

        when(result.getQName())
                .thenReturn(associationName);

        when(result.getChildRef())
                .thenReturn(document);

        return result;
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
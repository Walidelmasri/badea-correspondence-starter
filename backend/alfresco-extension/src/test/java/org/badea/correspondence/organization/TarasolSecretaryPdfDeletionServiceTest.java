package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.*;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceNodeLocator;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.junit.jupiter.api.Test;

class TarasolSecretaryPdfDeletionServiceTest {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final String CASE = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private static final String PDF = "9fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private final DepartmentCode unit = new DepartmentCode("118");
    private final EmployeeDirectoryEntry secretary = new EmployeeDirectoryEntry(
        new EmployeeId("300"), new DirectoryUsername("secretary.user"), "Secretary", null, Optional.empty());
    private final TarasolSecretaryWorkflowService workflow = mock(TarasolSecretaryWorkflowService.class);
    private final CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
    private final NodeService nodes = mock(NodeService.class);
    private final TarasolSecretaryPdfDeletionService service =
        new TarasolSecretaryPdfDeletionService(workflow, locator, nodes);
    private final NodeRef parent = mock(NodeRef.class);
    private final NodeRef attachment = mock(NodeRef.class);

    private void editable() {
        when(locator.locate(CASE)).thenReturn(parent);
        when(nodes.getType(parent)).thenReturn(QName.createQName(NS, "correspondence"));
        when(nodes.getProperty(parent, QName.createQName(NS, "state"))).thenReturn("DRAFT");
        when(nodes.getProperty(parent, QName.createQName(NS, "secretaryOwnerEmployeeId"))).thenReturn("300");
        when(nodes.getProperty(parent, QName.createQName(NS, "originDepartmentCode"))).thenReturn("118");
        when(nodes.getProperty(parent, QName.createQName(NS, "secretaryApprovalStatus"))).thenReturn("RETURNED");
        var association = mock(ChildAssociationRef.class);
        when(association.getQName()).thenReturn(QName.createQName(NS, "source-document-original"));
        when(association.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(association.getChildRef()).thenReturn(attachment);
        when(attachment.getId()).thenReturn(PDF);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(association));
        when(nodes.exists(attachment)).thenReturn(true);
        when(nodes.getType(attachment)).thenReturn(ContentModel.TYPE_CONTENT);
        ContentData pdf = mock(ContentData.class);
        when(pdf.getMimetype()).thenReturn("application/pdf");
        when(nodes.getProperty(attachment, ContentModel.PROP_CONTENT)).thenReturn(pdf);
    }

    @Test void removesOnlyAuthorizedPersonalSourcePdf() {
        editable();
        service.delete(secretary, unit, CASE, PDF);
        verify(workflow).authorizePdfUpload(secretary, unit, CASE);
        verify(nodes).deleteNode(attachment);
    }

    @Test void rejectedSecretaryGrantDoesNotResolveAnyAlfrescoNodes() {
        doThrow(new CorrespondenceAuthorizationException("revoked"))
            .when(workflow).authorizePdfUpload(secretary, unit, CASE);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.delete(secretary, unit, CASE, PDF));
        verifyNoInteractions(locator, nodes);
    }

    @Test void pendingApprovalDoesNotPermitDeletion() {
        editable();
        when(nodes.getProperty(parent, QName.createQName(NS, "secretaryApprovalStatus")))
            .thenReturn("PENDING");
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.delete(secretary, unit, CASE, PDF));
        verify(nodes, never()).deleteNode(any());
    }

    @Test void cannotDeleteAnotherDocumentOrGeneratedPdf() {
        editable();
        String foreign = "8fc41b5b-aceb-48fc-9f36-367e4a36e207";
        assertThrows(CorrespondenceDocumentNotFoundException.class,
            () -> service.delete(secretary, unit, CASE, foreign));
        verify(nodes, never()).deleteNode(any());
    }

    @Test void malformedIdFailsBeforeAuthorization() {
        assertThrows(IllegalArgumentException.class,
            () -> service.delete(secretary, unit, "../../other", PDF));
        verifyNoInteractions(workflow, locator, nodes);
    }
}

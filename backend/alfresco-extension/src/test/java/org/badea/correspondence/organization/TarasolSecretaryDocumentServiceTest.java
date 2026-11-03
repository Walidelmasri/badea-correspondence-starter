package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsRepository;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CorrespondenceDocumentContentRepository;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceNodeLocator;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.Test;

class TarasolSecretaryDocumentServiceTest {
    private static final String CASE = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private static final String PDF = "9fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private static final String OTHER = "8fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private final TarasolSecretaryWorkflowService workflow = mock(TarasolSecretaryWorkflowService.class);
    private final CorrespondenceDetailsRepository details = mock(CorrespondenceDetailsRepository.class);
    private final CorrespondenceDocumentContentRepository content = mock(CorrespondenceDocumentContentRepository.class);
    private final CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final TarasolSecretaryDocumentService service = new TarasolSecretaryDocumentService(
        workflow, details, content, locator, permissions);
    private final DepartmentCode unit = new DepartmentCode("118");
    private final EmployeeDirectoryEntry employee = new EmployeeDirectoryEntry(
        new EmployeeId("200"), new DirectoryUsername("test.user"), "Test User", null, Optional.empty());

    private void approvedRead(boolean manager) {
        var node = mock(NodeRef.class);
        when(locator.locate(CASE)).thenReturn(node);
        when(details.findByTechnicalId(CASE)).thenReturn(new CorrespondenceDetails(
            CASE, CorrespondenceState.DRAFT, "Report", null, null, null, null,
            "118", "Secretary", null,
            List.of(new CorrespondenceDocument(PDF, "report.pdf", "application/pdf", 12L),
                new CorrespondenceDocument(OTHER, "notes.txt", "text/plain", 7L))));
    }

    @Test
    void previewReturnsOnlyPdfChildrenOfAuthorizedDraft() {
        approvedRead(true);
        when(content.openByNodeId(PDF)).thenReturn(new ByteArrayInputStream(new byte[] {1,2}));
        assertEquals(1, service.list(employee, unit, CASE, true).size());
        var result = service.open(employee, unit, CASE, PDF, true);
        assertEquals("report.pdf", result.document().filename());
        verify(workflow, times(2)).authorizePreview(employee, unit, CASE, true);
        verify(content).openByNodeId(PDF);
        assertThrows(CorrespondenceDocumentNotFoundException.class,
            () -> service.open(employee, unit, CASE, OTHER, true));
        verify(content, never()).openByNodeId(OTHER);
    }

    @Test
    void rejectedWorkflowPermissionNeverConsultsDocuments() {
        doThrow(new CorrespondenceAuthorizationException("denied"))
            .when(workflow).authorizePreview(employee, unit, CASE, false);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.list(employee, unit, CASE, false));
        verifyNoInteractions(details, content, locator, permissions);
    }

    @Test
    void explicitAuthorizedSecretaryPreviewDoesNotRequireOriginFolderRead() {
        approvedRead(false);
        assertEquals(1, service.list(employee, unit, CASE, false).size());
        verify(workflow).authorizePreview(employee, unit, CASE, false);
        verify(permissions, never()).hasPermission(any(), eq(PermissionService.READ));
    }

    @Test
    void malformedIdsFailClosedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
            () -> service.list(employee, unit, "../../", false));
        verifyNoInteractions(workflow, details, content);
    }
    @Test
    void approvedOwnCorrespondenceIsReadOnlyButItsPdfCanStillBePreviewed() {
        when(workflow.isApprovedOwnedCase(employee, unit, CASE)).thenReturn(true);
        when(details.findByTechnicalId(CASE)).thenReturn(new CorrespondenceDetails(
            CASE, CorrespondenceState.WITH_PRESIDENT_OFFICE, "Sent report", null, null,
            null, null, "118", "Secretary", null,
            List.of(new CorrespondenceDocument(PDF, "report.pdf", "application/pdf", 12L))));
        assertEquals(1, service.list(employee, unit, CASE, false).size());
        verify(workflow).authorizePreview(employee, unit, CASE, false);
        verify(workflow).isApprovedOwnedCase(employee, unit, CASE);
    }

}

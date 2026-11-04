package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoSecretaryContinuationPdfStoreTest {
    private static final String CASE = "217a0e57-bc77-4475-a06f-c72aaa0949c4";
    private static final String REQUEST = "12345678-1234-1234-1234-123456789abc";
    private static final String DOC = "22222222-2222-4222-8222-222222222222";
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final DepartmentCode UNIT = new DepartmentCode("117");
    private final NodeService nodes = mock(NodeService.class);
    private final ContentService content = mock(ContentService.class);
    private final CorrespondenceNodeLocator locator = mock(CorrespondenceNodeLocator.class);
    private final AlfrescoSecretaryRoundTwoRequestRepository requests =
        mock(AlfrescoSecretaryRoundTwoRequestRepository.class);
    private final AlfrescoSecretaryContinuationPdfStore store =
        new AlfrescoSecretaryContinuationPdfStore(nodes, content, locator, requests);
    private final NodeRef parent = mock(NodeRef.class);
    private final NodeRef request = mock(NodeRef.class);
    private final NodeRef document = mock(NodeRef.class);
    private final ChildAssociationRef requestAssoc = mock(ChildAssociationRef.class);

    private static QName q(String name) { return QName.createQName(NS, name); }
    private static byte[] validPdf() { return "%PDF-1.7\nhello".getBytes(java.nio.charset.StandardCharsets.US_ASCII); }

    @BeforeEach void setup() {
        when(locator.locate(CASE)).thenReturn(parent);
        when(requests.pending(CASE, UNIT)).thenReturn(
            new AlfrescoSecretaryRoundTwoRequestRepository.Request(REQUEST, CASE,
                "117", "99999", "Follow up", 3L, "PENDING",
                Instant.parse("2026-11-04T12:00:00Z")));
        when(requests.editable(CASE, UNIT, "99999", REQUEST)).thenReturn(
            new AlfrescoSecretaryRoundTwoRequestRepository.Request(REQUEST, CASE,
                "117", "99999", "Follow up", 3L, "PENDING",
                Instant.parse("2026-11-04T12:00:00Z")));
        when(nodes.getProperty(parent, q("roundTwoPendingRequestId"))).thenReturn(REQUEST);
        when(requestAssoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(requestAssoc.getChildRef()).thenReturn(request);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(requestAssoc));
        when(nodes.getType(request)).thenReturn(q("secretaryRoundTwoRequest"));
        when(nodes.getProperty(request, q("roundTwoRequestId"))).thenReturn(REQUEST);
        when(nodes.getChildAssocs(request)).thenReturn(List.of());
        when(document.getId()).thenReturn(DOC);
    }

    private void preparing() {
        when(requests.editable(CASE, UNIT, "99999", REQUEST)).thenReturn(
            new AlfrescoSecretaryRoundTwoRequestRepository.Request(REQUEST, CASE,
                "117", "99999", "Follow up", 3L, "PREPARING",
                Instant.parse("2026-11-04T12:00:00Z")));
        when(nodes.getProperty(parent, q("roundTwoPendingRequestId"))).thenReturn(null);
    }

    @Test void uploadStaysUnderRequestAndNeverAddsSourcePdfToCorrespondence() {
        preparing();
        ChildAssociationRef created = mock(ChildAssociationRef.class);
        ContentWriter writer = mock(ContentWriter.class);
        when(created.getChildRef()).thenReturn(document);
        when(nodes.createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS), any(QName.class),
            eq(q("secretaryStagedContinuationPdf")), anyMap())).thenReturn(created);
        when(content.getWriter(document, ContentModel.PROP_CONTENT, true)).thenReturn(writer);
        var pdf = store.upload(CASE, UNIT, "99999", REQUEST, "follow-up.pdf", validPdf());
        assertEquals(DOC, pdf.nodeId());
        assertEquals(4L, pdf.workflowCycle());
        verify(writer).setMimetype("application/pdf");
        verify(writer).putContent(any(InputStream.class));
        verify(nodes, never()).createNode(eq(parent), any(), any(), eq(ContentModel.TYPE_CONTENT), anyMap());
        verify(nodes, never()).addAspect(eq(document), any(), anyMap());
        verify(nodes).setProperty(request, q("roundTwoRequestDocumentsRevision"), 1L);
        verify(nodes, never()).setProperty(eq(parent),
            eq(AlfrescoCorrespondenceCycleReader.CURRENT), any());
    }

    @Test void rejectionOfWrongOwnerOccursBeforeAnyDocumentWrite() {
        when(requests.editable(CASE, UNIT, "wrong-owner", REQUEST)).thenThrow(
            new CorrespondenceAuthorizationException("Wrong secretary"));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> store.upload(CASE, UNIT, "wrong-owner", REQUEST, "x.pdf", validPdf()));
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }

    @Test void requestMustStillBePendingAtTheExpectedCycle() {
        when(nodes.getProperty(parent, q("roundTwoPendingRequestId"))).thenReturn(null);
        assertThrows(OfficeCycleConflictException.class,
            () -> store.upload(CASE, UNIT, "99999", REQUEST, "x.pdf", validPdf()));
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }

    private void stagedPdf() {
        ChildAssociationRef assoc = mock(ChildAssociationRef.class);
        ContentData metadata = mock(ContentData.class);
        when(assoc.getTypeQName()).thenReturn(ContentModel.ASSOC_CONTAINS);
        when(assoc.getQName()).thenReturn(q("secretary-followup-" + DOC));
        when(assoc.getChildRef()).thenReturn(document);
        when(nodes.getChildAssocs(parent)).thenReturn(List.of(requestAssoc, assoc));
        when(nodes.getType(document)).thenReturn(q("secretaryStagedContinuationPdf"));
        when(nodes.getProperty(document, q("stagedContinuationRequestId"))).thenReturn(REQUEST);
        when(nodes.getProperty(document, q("stagedContinuationCycle"))).thenReturn(4L);
        when(nodes.getProperty(document, ContentModel.PROP_NAME)).thenReturn("proposed.pdf");
        when(nodes.getProperty(document, ContentModel.PROP_CONTENT)).thenReturn(metadata);
        when(metadata.getMimetype()).thenReturn("application/pdf");
        when(metadata.getSize()).thenReturn(125L);
    }

    @Test void managerReviewRevisionRejectsChangedFileSetBeforePromotion() {
        stagedPdf();
        when(nodes.getProperty(request, q("roundTwoRequestDocumentsRevision")))
            .thenReturn(2L);
        assertEquals(2L, store.reviewedFiles(CASE, UNIT, REQUEST).documentsRevision());
        assertEquals(1, store.reviewedFiles(CASE, UNIT, REQUEST).items().size());
        store.assertReviewedFiles(CASE, UNIT, REQUEST, 2L);
        assertThrows(OfficeCycleConflictException.class,
            () -> store.assertReviewedFiles(CASE, UNIT, REQUEST, 1L));
        verify(nodes, never()).createNode(eq(parent), any(), any(), eq(ContentModel.TYPE_CONTENT), anyMap());
    }

    @Test void secretaryCannotAddOrDeletePdfsWhileManagerReviewIsPending() {
        stagedPdf();
        assertThrows(OfficeCycleConflictException.class,
            () -> store.delete(CASE, UNIT, "99999", REQUEST, DOC));
        assertThrows(OfficeCycleConflictException.class,
            () -> store.upload(CASE, UNIT, "99999", REQUEST, "late.pdf", validPdf()));
        assertEquals(1, store.list(CASE, UNIT, REQUEST, "99999").size());
        verify(nodes, never()).deleteNode(any());
    }

    @Test void managerPromotionCreatesOfficialCopiesOnlyAfterApproval() {
        stagedPdf();
        ContentReader reader = mock(ContentReader.class);
        ContentWriter writer = mock(ContentWriter.class);
        NodeRef official = mock(NodeRef.class);
        ChildAssociationRef created = mock(ChildAssociationRef.class);
        when(content.getReader(document, ContentModel.PROP_CONTENT)).thenReturn(reader);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenAnswer(inv -> new ByteArrayInputStream(validPdf()));
        when(created.getChildRef()).thenReturn(official);
        when(nodes.createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS),
            argThat(qname -> qname.getLocalName().startsWith("source-document-")),
            eq(ContentModel.TYPE_CONTENT), anyMap())).thenReturn(created);
        when(content.getWriter(official, ContentModel.PROP_CONTENT, true)).thenReturn(writer);
        store.promote(CASE, UNIT, REQUEST, 4L);
        verify(nodes).createNode(eq(parent), eq(ContentModel.ASSOC_CONTAINS),
            argThat(qname -> qname.getLocalName().startsWith("source-document-")),
            eq(ContentModel.TYPE_CONTENT), anyMap());
        verify(nodes).addAspect(official, q("sourceDocumentCycleMetadata"),
            java.util.Map.of(q("sourceDocumentCycle"), 4L));
        verify(writer).putContent(any(InputStream.class));
        verify(nodes).deleteNode(document);
        verify(nodes, never()).deleteNode(request);
    }

    @Test void cannotPromoteToEarlierOrLaterRound() {
        stagedPdf();
        assertThrows(OfficeCycleConflictException.class,
            () -> store.promote(CASE, UNIT, REQUEST, 3L));
        assertThrows(OfficeCycleConflictException.class,
            () -> store.promote(CASE, UNIT, REQUEST, 5L));
        verify(nodes, never()).createNode(eq(parent), any(), any(), eq(ContentModel.TYPE_CONTENT), anyMap());
    }

    @Test void invalidStagedContentStopsPromotionBeforeCreatingOfficialDocuments() {
        stagedPdf();
        assertThrows(CorrespondenceRepositoryException.class,
            () -> store.promote(CASE, UNIT, REQUEST, 4L));
        verify(nodes, never()).createNode(eq(parent), any(), any(),
            eq(ContentModel.TYPE_CONTENT), anyMap());
        verify(nodes, never()).deleteNode(any());
    }

    @Test void pdfBelongingToAnotherRequestCannotBeDeletedOrPromoted() {
        stagedPdf();
        preparing();
        when(nodes.getProperty(document, q("stagedContinuationRequestId")))
            .thenReturn("55555555-5555-4555-8555-555555555555");
        assertThrows(CorrespondenceDocumentNotFoundException.class,
            () -> store.delete(CASE, UNIT, "99999", REQUEST, DOC));
        // Manager promotion is a separate pending-only path.
        when(nodes.getProperty(parent, q("roundTwoPendingRequestId"))).thenReturn(REQUEST);
        store.promote(CASE, UNIT, REQUEST, 4L);
        verify(nodes, never()).createNode(eq(parent), any(), any(),
            eq(ContentModel.TYPE_CONTENT), anyMap());
        verify(nodes, never()).deleteNode(any());
    }

    @Test void deletingOriginalCorrespondenceDocumentIsImpossible() {
        preparing();
        // No request child association with the purported original PDF's ID.
        assertThrows(CorrespondenceDocumentNotFoundException.class,
            () -> store.delete(CASE, UNIT, "99999", REQUEST, DOC));
        verify(nodes, never()).deleteNode(any());
    }

    @Test void onlyThePendingRequestOwnerCanDeleteStagedPdf() {
        stagedPdf();
        preparing();
        when(requests.editable(CASE, UNIT, "wrong-owner", REQUEST)).thenThrow(
            new CorrespondenceAuthorizationException("Wrong secretary"));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> store.delete(CASE, UNIT, "wrong-owner", REQUEST, DOC));
        verify(nodes, never()).deleteNode(any());
    }

    @Test void validRequestOwnerCanDeleteStagedPdfWithoutTouchingCorrespondence() {
        stagedPdf();
        preparing();
        store.delete(CASE, UNIT, "99999", REQUEST, DOC);
        verify(nodes).deleteNode(document);
        verify(nodes, never()).deleteNode(parent);
        verify(nodes).setProperty(request, q("roundTwoRequestDocumentsRevision"), 1L);
    }

    @Test void returnedRequestOwnerCanListAndDeleteStagedPdfsWithoutReopeningManagerApproval() {
        stagedPdf();
        when(requests.editable(CASE, UNIT, "99999", REQUEST)).thenReturn(
            new AlfrescoSecretaryRoundTwoRequestRepository.Request(REQUEST, CASE,
                "117", "99999", "Follow up", 3L, "RETURNED",
                Instant.parse("2026-11-04T12:00:00Z"), "Wrong PDF", 1L));
        when(nodes.getProperty(parent, q("roundTwoPendingRequestId"))).thenReturn(null);
        assertEquals(1, store.list(CASE, UNIT, REQUEST, "99999").size());
        store.delete(CASE, UNIT, "99999", REQUEST, DOC);
        verify(nodes).deleteNode(document);
        verify(nodes).setProperty(parent, q("roundTwoPendingRequestId"), null);
        verify(nodes, never()).setProperty(parent, q("roundTwoPendingRequestId"), REQUEST);
        verify(nodes, never()).deleteNode(request);
    }

    @Test void pdfMagicAndPathTraversalAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> store.upload(CASE, UNIT, "99999", REQUEST, "../x.pdf", validPdf()));
        assertThrows(IllegalArgumentException.class,
            () -> store.upload(CASE, UNIT, "99999", REQUEST, "x.pdf", "not pdf".getBytes()));
        verify(nodes, never()).createNode(any(), any(), any(), any(), anyMap());
    }
}

package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;

import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;

import org.alfresco.service.namespace.QName;

import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

class AlfrescoCorrespondenceDraftDocumentUploaderTest {

        private static final String TECHNICAL_ID = "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

        private static final String NAMESPACE_URI = "http://www.badea.org/model/correspondence/1.0";

        private static final DepartmentCode DEPARTMENT = new DepartmentCode("117");

        private static final EmployeeId CREATOR = new EmployeeId("10001");

        private static final byte[] PDF_BYTES = "%PDF-1.7\nTest PDF".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII);

        private NodeService nodeService;
        private ContentService contentService;
        private PermissionService permissionService;
        private CorrespondenceNodeLocator nodeLocator;

        private NodeRef correspondence;
        private NodeRef document;

        private ContentWriter writer;

        private AlfrescoCorrespondenceDraftDocumentUploader uploader;

        @BeforeEach
        void setUp() {

                nodeService = mock(NodeService.class);
                contentService = mock(ContentService.class);
                permissionService = mock(PermissionService.class);
                nodeLocator = mock(CorrespondenceNodeLocator.class);

                correspondence = new NodeRef(
                                "workspace://SpacesStore/"
                                                + "11111111-1111-1111-1111-111111111111");

                document = new NodeRef(
                                "workspace://SpacesStore/"
                                                + "22222222-2222-2222-2222-222222222222");

                writer = mock(ContentWriter.class);

                uploader = new AlfrescoCorrespondenceDraftDocumentUploader(
                                nodeService,
                                contentService,
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

                when(nodeService.getProperty(
                                correspondence,
                                qname("createdByEmployeeId")))
                                .thenReturn("10001");

                when(permissionService.hasPermission(
                                correspondence,
                                PermissionService.WRITE))
                                .thenReturn(AccessStatus.ALLOWED);
        }

        @Test
        void uploadsPdfForItsCreator() throws Exception {

                ChildAssociationRef association = mock(ChildAssociationRef.class);

                when(association.getChildRef())
                                .thenReturn(document);

                when(nodeService.createNode(
                                eq(correspondence),
                                eq(ContentModel.ASSOC_CONTAINS),
                                any(QName.class),
                                eq(ContentModel.TYPE_CONTENT),
                                anyMap()))
                                .thenReturn(association);

                when(contentService.getWriter(
                                document,
                                ContentModel.PROP_CONTENT,
                                true))
                                .thenReturn(writer);

                CorrespondenceDocument result = uploadAsCreator();

                assertEquals(document.getId(), result.nodeId());
                assertEquals("main-letter.pdf", result.filename());
                assertEquals("application/pdf", result.mimeType());
                assertEquals(PDF_BYTES.length, result.sizeBytes());

                verify(writer).setMimetype("application/pdf");

                ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);

                verify(writer).putContent(streamCaptor.capture());

                assertArrayEquals(
                                PDF_BYTES,
                                streamCaptor.getValue().readAllBytes());
        }

        @Test
        void replacementDirectorInSameDepartmentCanUpload() {

                ChildAssociationRef association = mock(ChildAssociationRef.class);

                when(association.getChildRef())
                                .thenReturn(document);

                when(nodeService.createNode(
                                eq(correspondence),
                                eq(ContentModel.ASSOC_CONTAINS),
                                any(QName.class),
                                eq(ContentModel.TYPE_CONTENT),
                                anyMap()))
                                .thenReturn(association);

                when(contentService.getWriter(
                                document,
                                ContentModel.PROP_CONTENT,
                                true))
                                .thenReturn(writer);

                CorrespondenceDocument result = uploader.uploadPdf(
                                TECHNICAL_ID,
                                CorrespondenceActorRole.DIRECTOR,
                                DEPARTMENT,
                                new EmployeeId("different-employee"),
                                "main-letter.pdf",
                                PDF_BYTES);

                assertEquals(document.getId(), result.nodeId());

                verify(writer).setMimetype("application/pdf");
        }

        @Test
        void rejectsUploadAfterSubmission() {

                when(nodeService.getProperty(
                                correspondence,
                                qname("state")))
                                .thenReturn("WITH_PRESIDENT_OFFICE");

                assertThrows(
                                CorrespondenceAuthorizationException.class,
                                this::uploadAsCreator);

                verifyNoDocumentCreated();
        }

        @Test
        void uploadsPdfForExecutiveCreator() {
                when(nodeService.getProperty(correspondence,
                                qname("createdByCorrespondenceRole")))
                                .thenReturn("PRESIDENT_OFFICE");
                ChildAssociationRef association = mock(ChildAssociationRef.class);
                when(association.getChildRef()).thenReturn(document);
                when(nodeService.createNode(eq(correspondence),
                                eq(ContentModel.ASSOC_CONTAINS), any(QName.class),
                                eq(ContentModel.TYPE_CONTENT), anyMap()))
                                .thenReturn(association);
                when(contentService.getWriter(document,
                                ContentModel.PROP_CONTENT, true)).thenReturn(writer);

                CorrespondenceDocument result = uploader.uploadPdf(
                                TECHNICAL_ID, CorrespondenceActorRole.PRESIDENT_OFFICE,
                                DEPARTMENT, CREATOR, "executive.pdf", PDF_BYTES);

                assertEquals(document.getId(), result.nodeId());
                verify(writer).setMimetype("application/pdf");
        }

        @Test
        void allowsPresidentCreatorToUploadPdf() {
                when(nodeService.getProperty(correspondence,
                                qname("createdByCorrespondenceRole")))
                                .thenReturn("PRESIDENT");
                ChildAssociationRef association = mock(ChildAssociationRef.class);
                when(association.getChildRef()).thenReturn(document);
                when(nodeService.createNode(eq(correspondence),
                                eq(ContentModel.ASSOC_CONTAINS), any(QName.class),
                                eq(ContentModel.TYPE_CONTENT), anyMap()))
                                .thenReturn(association);
                when(contentService.getWriter(document,
                                ContentModel.PROP_CONTENT, true)).thenReturn(writer);

                CorrespondenceDocument result = uploader.uploadPdf(
                                TECHNICAL_ID, CorrespondenceActorRole.PRESIDENT,
                                DEPARTMENT, CREATOR, "president.pdf", PDF_BYTES);
                assertEquals("president.pdf", result.filename());
        }

        @Test
        void rejectsAnotherEmployeeUploadingExecutiveDraft() {
                when(nodeService.getProperty(correspondence,
                                qname("createdByCorrespondenceRole")))
                                .thenReturn("PRESIDENT_OFFICE");
                assertThrows(CorrespondenceAuthorizationException.class,
                                () -> uploader.uploadPdf(TECHNICAL_ID,
                                                CorrespondenceActorRole.PRESIDENT_OFFICE,
                                                DEPARTMENT, new EmployeeId("10002"),
                                                "executive.pdf", PDF_BYTES));
                verifyNoDocumentCreated();
        }

        @Test
        void rejectsCrossRoleExecutiveDraftUpload() {
                when(nodeService.getProperty(correspondence,
                                qname("createdByCorrespondenceRole")))
                                .thenReturn("PRESIDENT_OFFICE");
                assertThrows(CorrespondenceAuthorizationException.class,
                                () -> uploader.uploadPdf(TECHNICAL_ID,
                                                CorrespondenceActorRole.PRESIDENT,
                                                DEPARTMENT, CREATOR,
                                                "executive.pdf", PDF_BYTES));
                verifyNoDocumentCreated();
        }

        @Test
        void rejectsSubmittedExecutiveDraftUpload() {
                when(nodeService.getProperty(correspondence,
                                qname("createdByCorrespondenceRole")))
                                .thenReturn("PRESIDENT");
                when(nodeService.getProperty(correspondence,
                                qname("state"))).thenReturn("WITH_PRESIDENT_OFFICE");
                assertThrows(CorrespondenceAuthorizationException.class,
                                () -> uploader.uploadPdf(TECHNICAL_ID,
                                                CorrespondenceActorRole.PRESIDENT,
                                                DEPARTMENT, CREATOR,
                                                "executive.pdf", PDF_BYTES));
                verifyNoDocumentCreated();
        }

        @Test
        void rejectsExecutiveDraftWithoutAlfrescoWritePermission() {
                when(nodeService.getProperty(correspondence,
                                qname("createdByCorrespondenceRole")))
                                .thenReturn("PRESIDENT");
                when(permissionService.hasPermission(correspondence,
                                PermissionService.WRITE)).thenReturn(AccessStatus.DENIED);
                assertThrows(CorrespondenceAuthorizationException.class,
                                () -> uploader.uploadPdf(TECHNICAL_ID,
                                                CorrespondenceActorRole.PRESIDENT,
                                                DEPARTMENT, CREATOR,
                                                "executive.pdf", PDF_BYTES));
                verifyNoDocumentCreated();
        }

        @Test
        void rejectsPresidentUploadingIntoDirectorDraft() {

                assertThrows(
                                CorrespondenceAuthorizationException.class,
                                () -> uploader.uploadPdf(
                                                TECHNICAL_ID,
                                                CorrespondenceActorRole.PRESIDENT,
                                                DEPARTMENT,
                                                CREATOR,
                                                "main-letter.pdf",
                                                PDF_BYTES));

                verifyNoDocumentCreated();
        }

        @Test
        void rejectsMissingAlfrescoWritePermission() {

                when(permissionService.hasPermission(
                                correspondence,
                                PermissionService.WRITE))
                                .thenReturn(AccessStatus.DENIED);

                assertThrows(
                                CorrespondenceAuthorizationException.class,
                                this::uploadAsCreator);

                verifyNoDocumentCreated();
        }

        @Test
        void rejectsNonPdfContent() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> uploader.uploadPdf(
                                                TECHNICAL_ID,
                                                CorrespondenceActorRole.DIRECTOR,
                                                DEPARTMENT,
                                                CREATOR,
                                                "main-letter.pdf",
                                                new byte[] { 1, 2, 3 }));

                verifyNoDocumentCreated();
        }

        @Test
        void rejectsOversizedPdf() {

                byte[] oversized = new byte[AlfrescoCorrespondenceDraftDocumentUploader.MAX_PDF_BYTES + 1];

                assertThrows(
                                IllegalArgumentException.class,
                                () -> uploader.uploadPdf(
                                                TECHNICAL_ID,
                                                CorrespondenceActorRole.DIRECTOR,
                                                DEPARTMENT,
                                                CREATOR,
                                                "main-letter.pdf",
                                                oversized));

                verifyNoDocumentCreated();
        }

        @Test
        void rejectsPathTraversalFilename() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> uploader.uploadPdf(
                                                TECHNICAL_ID,
                                                CorrespondenceActorRole.DIRECTOR,
                                                DEPARTMENT,
                                                CREATOR,
                                                "../document.pdf",
                                                PDF_BYTES));

                verifyNoDocumentCreated();
        }

        private CorrespondenceDocument uploadAsCreator() {

                return uploader.uploadPdf(
                                TECHNICAL_ID,
                                CorrespondenceActorRole.DIRECTOR,
                                DEPARTMENT,
                                CREATOR,
                                "main-letter.pdf",
                                PDF_BYTES);
        }

        private void verifyNoDocumentCreated() {

                verify(nodeService, never()).createNode(
                                any(NodeRef.class),
                                any(QName.class),
                                any(QName.class),
                                any(QName.class),
                                anyMap());
        }

        private static QName qname(String localName) {

                return QName.createQName(
                                NAMESPACE_URI,
                                localName);
        }
}
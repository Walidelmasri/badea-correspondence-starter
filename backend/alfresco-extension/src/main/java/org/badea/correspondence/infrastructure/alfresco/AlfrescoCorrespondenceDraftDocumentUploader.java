package org.badea.correspondence.infrastructure.alfresco;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
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
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Adds original PDF documents to correspondence drafts.
 *
 * Authorization is checked against the persisted correspondence
 * and the authenticated employee before creating any nodes.
 *
 * The caller must execute this operation within an Alfresco
 * write transaction.
 */
public final class AlfrescoCorrespondenceDraftDocumentUploader {

    public static final int MAX_PDF_BYTES =
            25 * 1024 * 1024;

    private static final String PDF_MIME_TYPE =
            "application/pdf";

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName TYPE_CORRESPONDENCE =
            qname("correspondence");

    private static final QName PROP_STATE =
            qname("state");

    private static final QName PROP_ORIGIN_DEPARTMENT_CODE =
            qname("originDepartmentCode");

    private static final QName PROP_CREATED_BY_EMPLOYEE_ID =
            qname("createdByEmployeeId");

    private static final QName PROP_CREATION_ROLE =
            qname("createdByCorrespondenceRole");
    private static final QName PROP_SECRETARY_OWNER = qname("secretaryOwnerEmployeeId");
    private static final QName PROP_SECRETARY_STATUS = qname("secretaryApprovalStatus");

    private static final QName SOURCE_DOCUMENT_CYCLE_ASPECT =
            qname("sourceDocumentCycleMetadata");
    private static final QName SOURCE_DOCUMENT_CYCLE =
            qname("sourceDocumentCycle");

    private final NodeService nodeService;

    private final ContentService contentService;

    private final PermissionService permissionService;

    private final CorrespondenceNodeLocator nodeLocator;

    public AlfrescoCorrespondenceDraftDocumentUploader(
            NodeService nodeService,
            ContentService contentService,
            PermissionService permissionService,
            CorrespondenceNodeLocator nodeLocator) {

        this.nodeService = Objects.requireNonNull(
                nodeService,
                "nodeService");

        this.contentService = Objects.requireNonNull(
                contentService,
                "contentService");

        this.permissionService = Objects.requireNonNull(
                permissionService,
                "permissionService");

        this.nodeLocator = Objects.requireNonNull(
                nodeLocator,
                "nodeLocator");
    }

    public CorrespondenceDocument uploadPdf(
            String technicalId,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode,
            EmployeeId actorEmployeeId,
            String filename,
            byte[] pdfBytes) {
        // Departmental correspondence lives under its originating unit, which
        // can differ from the recipient Director's unit. A non-draft upload is
        // therefore handled under a narrowly scoped system context, with TO
        // assignment and state checked by the repository before any write.
        if (actorRole == CorrespondenceActorRole.DIRECTOR) {
            boolean distributed = AuthenticationUtil.runAsSystem(() -> {
                NodeRef node = nodeLocator.locate(technicalId);
                return CorrespondenceState.WITH_DIRECTOR.name().equals(
                    nodeService.getProperty(node, PROP_STATE));
            });
            if (distributed) return AuthenticationUtil.runAsSystem(() ->
                uploadPdfInternal(technicalId, actorRole, actorDepartmentCode,
                    actorEmployeeId, filename, pdfBytes, false));
        }
        return uploadPdfInternal(technicalId, actorRole, actorDepartmentCode,
                actorEmployeeId, filename, pdfBytes, false);
    }

    /**
     * Stage a completed TO Director's follow-up PDFs for the NEXT cycle.
     * A repeated identical upload returns the original node, making retries
     * safe after a lost HTTP response or a failed continuation. The cycle
     * transition remains separate and only starts after every upload succeeds.
     */
    public CorrespondenceDocument uploadDirectorFollowUpPdf(
            String technicalId,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode,
            EmployeeId actorEmployeeId,
            String filename,
            byte[] pdfBytes) {
        if (actorRole != CorrespondenceActorRole.DIRECTOR
                || actorDepartmentCode == null || actorEmployeeId == null) throw forbidden();
        return AuthenticationUtil.runAsSystem(() -> uploadPdfInternal(technicalId,
            actorRole, actorDepartmentCode, actorEmployeeId, filename, pdfBytes, true));
    }

    private CorrespondenceDocument uploadPdfInternal(
            String technicalId,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode,
            EmployeeId actorEmployeeId,
            String filename,
            byte[] pdfBytes,
            boolean nextCycle) {
        Objects.requireNonNull(
                technicalId,
                "technicalId");

        Objects.requireNonNull(
                actorRole,
                "actorRole");

        Objects.requireNonNull(
                actorEmployeeId,
                "actorEmployeeId");

        String validatedFilename =
                validateFilename(filename);

        validatePdf(pdfBytes);

        /*
         * Resolve through the existing correspondence locator.
         * Do not accept an arbitrary Alfresco folder node ID.
         */
        NodeRef correspondence =
                nodeLocator.locate(technicalId);

        /*
         * Confirm the resolved node is the expected type.
         */
        if (!TYPE_CORRESPONDENCE.equals(
                nodeService.getType(correspondence))) {

            throw new CorrespondenceRepositoryException(
                    "The resolved node is not correspondence.");
        }

        if (nextCycle) {
            authorizeDirectorContinuationUpload(correspondence, actorDepartmentCode);
        } else {
            authorizeDraftUpload(correspondence, actorRole,
                actorDepartmentCode, actorEmployeeId);
        }

        long currentCycle = new AlfrescoCorrespondenceCycleReader(nodeService)
                .current(correspondence);
        if (nextCycle && currentCycle == Long.MAX_VALUE) {
            throw new IllegalStateException("Workflow cycle counter exhausted");
        }
        long workflowCycle = currentCycle + (nextCycle ? 1 : 0);
        if (nextCycle) {
            CorrespondenceDocument previous = findStagedFollowUp(
                correspondence, validatedFilename, pdfBytes, workflowCycle);
            if (previous != null) return previous;
        }

        /*
         * A unique association name provides stable document
         * identity without depending on the uploaded filename.
         */
        QName associationQName =
                qname(
                        "source-document-" + UUID.randomUUID());

        Map<QName, Serializable> properties =
                new HashMap<>();

        properties.put(
                ContentModel.PROP_NAME,
                validatedFilename);

        ChildAssociationRef created =
                nodeService.createNode(
                        correspondence,
                        ContentModel.ASSOC_CONTAINS,
                        associationQName,
                        ContentModel.TYPE_CONTENT,
                        properties);

        if (created == null || created.getChildRef() == null) {

            throw new CorrespondenceRepositoryException(
                    "Alfresco did not return the created document.");
        }

        NodeRef document =
                created.getChildRef();

        // Tag the actual Alfresco source PDF, not an inferred frontend filename.
        // The upload Web Script runs inside an Alfresco write transaction.
        nodeService.addAspect(document, SOURCE_DOCUMENT_CYCLE_ASPECT,
                Map.of(SOURCE_DOCUMENT_CYCLE, Long.valueOf(workflowCycle)));

        ContentWriter writer =
                contentService.getWriter(
                        document,
                        ContentModel.PROP_CONTENT,
                        true);

        if (writer == null) {

            throw new CorrespondenceRepositoryException(
                    "Alfresco did not provide a document writer.");
        }

        writer.setMimetype(PDF_MIME_TYPE);

        writer.putContent(
                new ByteArrayInputStream(pdfBytes));

        return new CorrespondenceDocument(
                document.getId(),
                validatedFilename,
                PDF_MIME_TYPE,
                pdfBytes.length,
                workflowCycle);
    }

    private void authorizeDirectorContinuationUpload(NodeRef correspondence,
            DepartmentCode unit) {
        if (!CorrespondenceState.WITH_DIRECTOR.name().equals(
                nodeService.getProperty(correspondence, PROP_STATE))
                || !CorrespondenceActorRole.DIRECTOR.name().equals(
                    nodeService.getProperty(correspondence, qname("assignedRole")))) {
            throw forbidden();
        }
        var assignments = new AlfrescoDepartmentAssignmentReader(nodeService)
                .readCurrent(correspondence);
        if (assignments.isEmpty() || assignments.stream().anyMatch(item ->
                item.recipientType() == org.badea.correspondence.application.DepartmentRecipientType.TO
                && item.status() != org.badea.correspondence.application.DepartmentAssignmentStatus.COMPLETED)
                || assignments.stream().noneMatch(item ->
                    item.recipientType() == org.badea.correspondence.application.DepartmentRecipientType.TO
                    && item.status() == org.badea.correspondence.application.DepartmentAssignmentStatus.COMPLETED
                    && unit.equals(item.departmentCode()))) {
            throw forbidden();
        }
    }

    /** Return an existing matching staged PDF without creating a duplicate. */
    private CorrespondenceDocument findStagedFollowUp(NodeRef correspondence,
            String filename, byte[] pdfBytes, long cycle) {
        for (ChildAssociationRef assoc : nodeService.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(assoc.getTypeQName())
                    || assoc.getQName() == null
                    || !NAMESPACE_URI.equals(assoc.getQName().getNamespaceURI())
                    || !assoc.getQName().getLocalName().startsWith("source-document-")) continue;
            NodeRef child = assoc.getChildRef();
            if (!ContentModel.TYPE_CONTENT.equals(nodeService.getType(child))
                    || !Long.valueOf(cycle).equals(
                        nodeService.getProperty(child, SOURCE_DOCUMENT_CYCLE))
                    || !filename.equals(nodeService.getProperty(child, ContentModel.PROP_NAME))) continue;
            ContentReader reader = contentService.getReader(child, ContentModel.PROP_CONTENT);
            if (reader == null || !reader.exists()) continue;
            try (InputStream stream = reader.getContentInputStream()) {
                if (stream != null && Arrays.equals(pdfBytes, stream.readAllBytes())) {
                    return new CorrespondenceDocument(child.getId(), filename, PDF_MIME_TYPE,
                        pdfBytes.length, cycle);
                }
            } catch (IOException failure) {
                throw new CorrespondenceRepositoryException("Cannot verify staged PDF idempotency", failure);
            }
        }
        return null;
    }

    private void authorizeDraftUpload(
            NodeRef correspondence,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode,
            EmployeeId actorEmployeeId) {

        if (actorDepartmentCode == null) {
            throw forbidden();
        }

        // Dedicated secretary endpoint has already checked a current Oracle
        // grant. Recheck persisted owner, status and unit before writing.
        if (actorRole == CorrespondenceActorRole.SECRETARY) {
            if (!CorrespondenceState.DRAFT.name().equals(nodeService.getProperty(correspondence, PROP_STATE))
                    || !actorDepartmentCode.value().equals(nodeService.getProperty(correspondence, PROP_ORIGIN_DEPARTMENT_CODE))
                    || !actorEmployeeId.value().equals(nodeService.getProperty(correspondence, PROP_SECRETARY_OWNER))
                    || !java.util.Set.of("DRAFT", "RETURNED").contains(
                        nodeService.getProperty(correspondence, PROP_SECRETARY_STATUS))
                    || permissionService.hasPermission(correspondence, PermissionService.WRITE) != AccessStatus.ALLOWED) {
                throw forbidden();
            }
            return;
        }

        // Executive drafts are writable only by their recorded creator.
        // Keep the existing Director department/assignment policy unchanged.
        if (actorRole == CorrespondenceActorRole.PRESIDENT
                || actorRole == CorrespondenceActorRole.PRESIDENT_OFFICE) {
            requireExecutiveDraftOwner(
                    correspondence, actorRole, actorDepartmentCode, actorEmployeeId);
            return;
        }
        if (actorRole != CorrespondenceActorRole.DIRECTOR) {
            throw forbidden();
        }

        Serializable state =
                nodeService.getProperty(
                        correspondence,
                        PROP_STATE);

        // Supplementary PDFs are appended to the same correspondence while a
        // Director is completing a current TO assignment. Neither the historical
        // documents nor completed assignments are overwritten.
        if (CorrespondenceState.WITH_DIRECTOR.name().equals(state)) {
            boolean assigned = new AlfrescoDepartmentAssignmentReader(nodeService)
                    .readCurrent(correspondence).stream().anyMatch(item ->
                            item.recipientType() == org.badea.correspondence.application.DepartmentRecipientType.TO
                            && item.departmentCode().equals(actorDepartmentCode)
                            && item.status() == org.badea.correspondence.application.DepartmentAssignmentStatus.PENDING);
            if (!assigned || permissionService.hasPermission(correspondence,
                    PermissionService.WRITE) != AccessStatus.ALLOWED) throw forbidden();
            return;
        }
        if (!CorrespondenceState.DRAFT.name().equals(state)) {
            throw forbidden();
        }
        // Do not let an ordinary Director bypass the secretary approval queue.
        if (nodeService.getProperty(correspondence, PROP_SECRETARY_OWNER) != null) {
            throw forbidden();
        }

        Serializable originDepartment =
                nodeService.getProperty(
                        correspondence,
                        PROP_ORIGIN_DEPARTMENT_CODE);

        if (!actorDepartmentCode.value().equals(originDepartment)) {
            throw forbidden();
        }

        // All unsent drafts are personal, including ordinary Director drafts.
        if (!actorEmployeeId.value().equals(nodeService.getProperty(
                correspondence, PROP_CREATED_BY_EMPLOYEE_ID))) {
            throw forbidden();
        }

        /*
         * Application-level authorization does not
         * replace Alfresco repository permissions.
         */
        AccessStatus permission =
                permissionService.hasPermission(
                        correspondence,
                        PermissionService.WRITE);

        if (permission != AccessStatus.ALLOWED) {
            throw forbidden();
        }
    }

    private void requireExecutiveDraftOwner(
            NodeRef correspondence,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode,
            EmployeeId actorEmployeeId) {

        if (!CorrespondenceState.DRAFT.name().equals(
                nodeService.getProperty(correspondence, PROP_STATE))) {
            throw forbidden();
        }
        if (!actorDepartmentCode.value().equals(
                nodeService.getProperty(correspondence, PROP_ORIGIN_DEPARTMENT_CODE))) {
            throw forbidden();
        }
        if (!actorEmployeeId.value().equals(
                nodeService.getProperty(correspondence, PROP_CREATED_BY_EMPLOYEE_ID))) {
            throw forbidden();
        }
        if (!actorRole.name().equals(
                nodeService.getProperty(correspondence, PROP_CREATION_ROLE))) {
            throw forbidden();
        }
        if (permissionService.hasPermission(
                correspondence, PermissionService.WRITE) != AccessStatus.ALLOWED) {
            throw forbidden();
        }
    }

    private static String validateFilename(String filename) {

        if (filename == null) {
            throw new IllegalArgumentException(
                    "PDF filename is required.");
        }

        String value = filename.trim();

        if (value.isEmpty() || value.length() > 180) {

            throw new IllegalArgumentException(
                    "PDF filename must contain between 1 and 180 characters.");
        }

        if (value.indexOf('/') >= 0 ||
                value.indexOf('\\') >= 0 ||
                value.equals(".") ||
                value.equals("..")) {

            throw new IllegalArgumentException(
                    "PDF filename contains invalid path characters.");
        }

        for (int index = 0; index < value.length(); index++) {

            if (Character.isISOControl(value.charAt(index))) {

                throw new IllegalArgumentException(
                        "PDF filename contains control characters.");
            }
        }

        if (!value.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || value.length() <= 4) {

            throw new IllegalArgumentException(
                    "Only PDF filenames are supported.");
        }

        return value;
    }

    private static void validatePdf(byte[] bytes) {

        if (bytes == null || bytes.length == 0) {

            throw new IllegalArgumentException(
                    "PDF content is required.");
        }

        if (bytes.length > MAX_PDF_BYTES) {

            throw new IllegalArgumentException(
                    "PDF exceeds the 25 MB upload limit.");
        }

        /*
         * Basic format check, not an antivirus or
         * comprehensive PDF security scanner.
         */
        if (bytes.length < 5 ||
                bytes[0] != '%' ||
                bytes[1] != 'P' ||
                bytes[2] != 'D' ||
                bytes[3] != 'F' ||
                bytes[4] != '-') {

            throw new IllegalArgumentException(
                    "Uploaded content is not a PDF.");
        }
    }

    private static CorrespondenceAuthorizationException forbidden() {

        return new CorrespondenceAuthorizationException(
                "The authenticated employee is not authorized "
                        + "to upload documents to this draft.");
    }

    private static QName qname(String localName) {

        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}
package org.badea.correspondence.infrastructure.alfresco;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.OfficeCycleConflictException;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

/**
 * Private request-level PDFs. Only an approved, REQUIRED-transaction continuation
 * promotes these nodes to the correspondence as source PDFs for the next cycle.
 * The original correspondence's documents are never modified here.
 */
public final class AlfrescoSecretaryContinuationPdfStore {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName REQUEST_TYPE = q("secretaryRoundTwoRequest");
    private static final QName STAGED_TYPE = q("secretaryStagedContinuationPdf");
    private static final QName STAGED_REQUEST_ID = q("stagedContinuationRequestId");
    private static final QName STAGED_CYCLE = q("stagedContinuationCycle");
    private static final QName REQUEST_ID = q("roundTwoRequestId");
    private static final QName PENDING_ID = q("roundTwoPendingRequestId");
    private static final QName DOCUMENT_REVISION = q("roundTwoRequestDocumentsRevision");
    private static final QName SOURCE_CYCLE_ASPECT = q("sourceDocumentCycleMetadata");
    private static final QName SOURCE_CYCLE = q("sourceDocumentCycle");
    private static final String STAGED_PREFIX = "secretary-followup-";
    private static final String PDF = "application/pdf";
    private static final int MAX_PDFS = 25;
    private final NodeService nodes;
    private final ContentService content;
    private final CorrespondenceNodeLocator locator;
    private final AlfrescoSecretaryRoundTwoRequestRepository requests;

    public record Pdf(String nodeId, String filename, long sizeBytes, long workflowCycle) { }
    public record Preview(Pdf pdf, InputStream stream) { }
    private record Located(NodeRef parent, NodeRef request,
            AlfrescoSecretaryRoundTwoRequestRepository.Request metadata) { }

    public AlfrescoSecretaryContinuationPdfStore(NodeService nodes, ContentService content,
            CorrespondenceNodeLocator locator, AlfrescoSecretaryRoundTwoRequestRepository requests) {
        this.nodes = Objects.requireNonNull(nodes);
        this.content = Objects.requireNonNull(content);
        this.locator = Objects.requireNonNull(locator);
        this.requests = Objects.requireNonNull(requests);
    }

    public Pdf upload(String technicalId, DepartmentCode unit, String ownerId,
            String requestId, String filename, byte[] bytes) {
        String cleanName = filename(filename);
        validatePdf(bytes);
        Located located = requireRequest(technicalId, unit, requestId, ownerId);
        var children = staged(located.parent(), requestId);
        for (NodeRef existing : children) {
            Pdf metadata = metadata(existing, located.metadata().expectedCycle() + 1);
            if (!cleanName.equals(metadata.filename()) || metadata.sizeBytes() != bytes.length) continue;
            ContentReader reader = content.getReader(existing, ContentModel.PROP_CONTENT);
            if (reader == null || !reader.exists()) continue;
            try (InputStream stream = reader.getContentInputStream()) {
                if (stream != null && Arrays.equals(bytes, stream.readAllBytes())) return metadata;
            } catch (IOException failure) {
                throw new CorrespondenceRepositoryException("Cannot compare staged PDF", failure);
            }
        }
        if (children.size() >= MAX_PDFS) {
            throw new IllegalArgumentException("At most 25 follow-up PDFs are supported");
        }
        String id = UUID.randomUUID().toString();
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, cleanName);
        properties.put(STAGED_REQUEST_ID, requestId);
        properties.put(STAGED_CYCLE, located.metadata().expectedCycle() + 1);
        // The correspondence is a cm:folder. The request node is cm:content
        // and cannot safely host child associations. The staged subtype is
        // invisible to readers requiring exact cm:content source-document type.
        ChildAssociationRef created = nodes.createNode(located.parent(), ContentModel.ASSOC_CONTAINS,
            q(STAGED_PREFIX + id), STAGED_TYPE, properties);
        if (created == null || created.getChildRef() == null)
            throw new CorrespondenceRepositoryException("Could not persist staged PDF");
        NodeRef document = created.getChildRef();
        ContentWriter writer = content.getWriter(document, ContentModel.PROP_CONTENT, true);
        if (writer == null) throw new CorrespondenceRepositoryException("PDF writer not available");
        writer.setMimetype(PDF);
        writer.putContent(new ByteArrayInputStream(bytes));
        bumpRevision(located);
        return new Pdf(document.getId(), cleanName, bytes.length,
            located.metadata().expectedCycle() + 1);
    }

    public List<Pdf> list(String technicalId, DepartmentCode unit, String requestId,
            String secretaryOwnerOrNull) {
        Located located = requireRequest(technicalId, unit, requestId, secretaryOwnerOrNull);
        List<Pdf> result = new ArrayList<>();
        for (NodeRef child : staged(located.parent(), requestId)) {
            result.add(metadata(child, located.metadata().expectedCycle() + 1));
        }
        result.sort(java.util.Comparator.comparing(Pdf::filename, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Pdf::nodeId));
        return List.copyOf(result);
    }

    public Preview open(String technicalId, DepartmentCode unit, String requestId,
            String secretaryOwnerOrNull, String documentId) {
        Located located = requireRequest(technicalId, unit, requestId, secretaryOwnerOrNull);
        NodeRef child = requireDocument(located.parent(), requestId, documentId);
        Pdf pdf = metadata(child, located.metadata().expectedCycle() + 1);
        ContentReader reader = content.getReader(child, ContentModel.PROP_CONTENT);
        if (reader == null || !reader.exists())
            throw new CorrespondenceRepositoryException("Staged PDF content unavailable");
        InputStream stream = reader.getContentInputStream();
        if (stream == null) throw new CorrespondenceRepositoryException("Staged PDF stream unavailable");
        return new Preview(pdf, stream);
    }

    public void delete(String technicalId, DepartmentCode unit, String ownerId,
            String requestId, String documentId) {
        Located located = requireRequest(technicalId, unit, requestId, ownerId);
        NodeRef child = requireDocument(located.parent(), requestId, documentId);
        nodes.deleteNode(child);
        bumpRevision(located);
    }

    /** Call BEFORE continueCycle and within the SAME REQUIRED Alfresco transaction. */
    public void promote(String technicalId, DepartmentCode unit, String requestId,
            long nextCycle) {
        Located located = requireRequest(technicalId, unit, requestId, null);
        if (nextCycle != located.metadata().expectedCycle() + 1)
            throw new OfficeCycleConflictException("Cannot promote PDFs into a different round");
        List<NodeRef> documents = staged(located.parent(), requestId);
        // Validate the complete batch before creating official source documents.
        for (NodeRef child : documents) {
            metadata(child, nextCycle);
            ContentReader reader = content.getReader(child, ContentModel.PROP_CONTENT);
            if (reader == null || !reader.exists())
                throw new CorrespondenceRepositoryException("Staged PDF missing content");
        }
        for (NodeRef stagedDocument : documents) {
            Pdf pdf = metadata(stagedDocument, nextCycle);
            Map<QName, Serializable> properties = new HashMap<>();
            properties.put(ContentModel.PROP_NAME, pdf.filename());
            ChildAssociationRef created = nodes.createNode(located.parent(),
                ContentModel.ASSOC_CONTAINS, q("source-document-" + UUID.randomUUID()),
                ContentModel.TYPE_CONTENT, properties);
            if (created == null || created.getChildRef() == null)
                throw new CorrespondenceRepositoryException("Cannot promote follow-up PDF");
            NodeRef official = created.getChildRef();
            nodes.addAspect(official, SOURCE_CYCLE_ASPECT, Map.of(SOURCE_CYCLE, nextCycle));
            ContentWriter writer = content.getWriter(official, ContentModel.PROP_CONTENT, true);
            if (writer == null)
                throw new CorrespondenceRepositoryException("Cannot write promoted PDF");
            writer.setMimetype(PDF);
            ContentReader reader = content.getReader(stagedDocument, ContentModel.PROP_CONTENT);
            if (reader == null || !reader.exists())
                throw new CorrespondenceRepositoryException("Staged PDF content disappeared");
            try (InputStream stream = reader.getContentInputStream()) {
                if (stream == null)
                    throw new CorrespondenceRepositoryException("Staged PDF stream missing");
                writer.putContent(stream);
            } catch (IOException failure) {
                throw new CorrespondenceRepositoryException("Cannot promote follow-up PDF", failure);
            }
            nodes.deleteNode(stagedDocument);
        }
        // The continuation + APPROVED decision must occur in this REQUIRED transaction.
    }

    private Located requireRequest(String technicalId, DepartmentCode unit,
            String requestId, String ownerOrNull) {
        if (!canonical(technicalId) || !canonical(requestId))
            throw new IllegalArgumentException("Canonical correspondence/request ID required");
        var selected = ownerOrNull == null
            ? requests.pending(technicalId, unit)
            : requests.editable(technicalId, unit, ownerOrNull, requestId);
        if (selected == null || !requestId.equals(selected.requestId())
                || (ownerOrNull == null && !"PENDING".equals(selected.status()))
                || (ownerOrNull != null && !"PENDING".equals(selected.status())
                    && !"RETURNED".equals(selected.status())))
            throw new OfficeCycleConflictException("Continuation request is not editable");
        if (ownerOrNull != null && !ownerOrNull.equals(selected.ownerEmployeeId()))
            throw new CorrespondenceAuthorizationException("Not the request's secretary creator");
        NodeRef parent = locator.locate(technicalId);
        Object marker = nodes.getProperty(parent, PENDING_ID);
        if (("PENDING".equals(selected.status()) && !requestId.equals(marker))
                || ("RETURNED".equals(selected.status()) && marker != null))
            throw new OfficeCycleConflictException("Continuation request has changed");
        for (ChildAssociationRef association : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (REQUEST_TYPE.equals(nodes.getType(child))
                    && requestId.equals(nodes.getProperty(child, REQUEST_ID)))
                return new Located(parent, child, selected);
        }
        throw new OfficeCycleConflictException("Pending request record unavailable");
    }

    private List<NodeRef> staged(NodeRef parent, String requestId) {
        List<NodeRef> found = new ArrayList<>();
        for (ChildAssociationRef link : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(link.getTypeQName())
                    || link.getQName() == null
                    || !NS.equals(link.getQName().getNamespaceURI())
                    || !link.getQName().getLocalName().startsWith(STAGED_PREFIX)) continue;
            NodeRef node = link.getChildRef();
            if (!STAGED_TYPE.equals(nodes.getType(node)))
                throw new CorrespondenceRepositoryException("Invalid staged PDF node type");
            Object linkedRequestId = nodes.getProperty(node, STAGED_REQUEST_ID);
            if (requestId.equals(linkedRequestId)) found.add(node);
        }
        return found;
    }

    private NodeRef requireDocument(NodeRef parent, String requestId, String documentId) {
        if (!canonical(documentId)) throw new IllegalArgumentException("Canonical document ID required");
        for (NodeRef child : staged(parent, requestId)) {
            if (documentId.equals(child.getId())) return child;
        }
        throw new CorrespondenceDocumentNotFoundException();
    }

    private Pdf metadata(NodeRef child, long nextCycle) {
        Object name = nodes.getProperty(child, ContentModel.PROP_NAME);
        Object stored = nodes.getProperty(child, ContentModel.PROP_CONTENT);
        Object cycle = nodes.getProperty(child, STAGED_CYCLE);
        if (!(cycle instanceof Number number) || number.longValue() != nextCycle
                || !(name instanceof String filename) || filename.isBlank()
                || !(stored instanceof ContentData data) || !PDF.equalsIgnoreCase(data.getMimetype())
                || data.getSize() <= 0 || data.getSize() > AlfrescoCorrespondenceDraftDocumentUploader.MAX_PDF_BYTES)
            throw new CorrespondenceRepositoryException("Invalid staged PDF metadata");
        return new Pdf(child.getId(), filename, data.getSize(), nextCycle);
    }

    private void bumpRevision(Located located) {
        Object current = nodes.getProperty(located.request(), DOCUMENT_REVISION);
        long revision = current == null ? 0 : ((Number) current).longValue();
        if (revision < 0 || revision == Long.MAX_VALUE)
            throw new CorrespondenceRepositoryException("Invalid request document revision");
        nodes.setProperty(located.request(), DOCUMENT_REVISION, revision + 1);
        // Parent write serializes upload/delete with an in-flight manager decision.
        nodes.setProperty(located.parent(), PENDING_ID,
            "PENDING".equals(located.metadata().status()) ? located.metadata().requestId() : null);
    }

    private static String filename(String raw) {
        if (raw == null) throw new IllegalArgumentException("PDF filename required");
        String name = raw.trim();
        if (name.length() < 5 || name.length() > 180 || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid PDF filename");
        return name;
    }

    private static void validatePdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5
                || bytes.length > AlfrescoCorrespondenceDraftDocumentUploader.MAX_PDF_BYTES
                || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-')
            throw new IllegalArgumentException("PDF required (maximum 25 MiB)");
    }

    private static boolean canonical(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static QName q(String name) { return QName.createQName(NS, name); }
}

package org.badea.correspondence.infrastructure.alfresco;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.CorrespondenceGeneratedDocument;
import org.badea.correspondence.application.PresidentGeneratedDocumentStore;
import org.badea.correspondence.directory.EmployeeId;

/** Generated PDFs are append-only typed children, never original attachments. */
public final class AlfrescoPresidentGeneratedDocumentStore implements PresidentGeneratedDocumentStore {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    static final QName TYPE = q("generatedDocument");
    static final QName REVISION = q("generatedReviewRevision");
    static final QName OFFICE_REVISION = q("generatedOfficeRevision");
    static final QName KIND = q("generatedArtifactType");
    static final QName SOURCE = q("generatedSourceDocumentNodeId");
    static final QName CREATOR = q("generatedByEmployeeId");
    static final QName CREATED = q("generatedAt");
    static final QName HASH = q("generatedSha256");

    private final NodeService nodes;
    private final ContentService content;
    private final CorrespondenceNodeLocator locator;
    private final Clock clock;

    public AlfrescoPresidentGeneratedDocumentStore(NodeService nodes, ContentService content,
            CorrespondenceNodeLocator locator, Clock clock) {
        this.nodes = Objects.requireNonNull(nodes);
        this.content = Objects.requireNonNull(content);
        this.locator = Objects.requireNonNull(locator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<CorrespondenceGeneratedDocument> find(String technicalId, long revision,
            String artifactType, String sourceNodeId) {
        NodeRef parent = locator.locate(technicalId);
        CorrespondenceGeneratedDocument found = null;
        for (ChildAssociationRef association : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (!TYPE.equals(nodes.getType(child))) continue;
            if (!Objects.equals(revision, asRevision(nodes.getProperty(child, REVISION)))
                    || !artifactType.equals(nodes.getProperty(child, KIND))
                    || !Objects.equals(sourceNodeId, nodes.getProperty(child, SOURCE))) continue;
            if (found != null) throw new CorrespondenceRepositoryException("Duplicate generated PDF artifacts");
            found = describe(child);
        }
        return Optional.ofNullable(found);
    }

    @Override
    public void save(String technicalId, long revision, String artifactType,
            String sourceNodeId, String filename, byte[] pdf, EmployeeId employee) {
        if (revision < 1 || pdf == null || pdf.length < 5 ||
                pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-') {
            throw new IllegalArgumentException("Generated content is not a valid PDF header");
        }
        if (find(technicalId, revision, artifactType, sourceNodeId).isPresent()) {
            throw new CorrespondenceRepositoryException("Generated PDF already exists for this review revision");
        }
        String name = "president-" + artifactType.toLowerCase(java.util.Locale.ROOT) +
                "-r" + revision + "-" + (sourceNodeId == null ? "explanation" : sourceNodeId);
        persist(technicalId, revision, null, artifactType, sourceNodeId, filename, pdf, employee, name);
    }

    /** Official dispatch identity includes the Office revision, even if President review is unchanged. */
    public Optional<CorrespondenceGeneratedDocument> findOffice(String technicalId,
            long reviewRevision, long officeRevision, String artifactType) {
        requireOfficeIdentity(reviewRevision, officeRevision, artifactType);
        NodeRef parent = locator.locate(technicalId);
        CorrespondenceGeneratedDocument found = null;
        for (ChildAssociationRef association : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (!TYPE.equals(nodes.getType(child))) continue;
            if (!Objects.equals(reviewRevision, asRevision(nodes.getProperty(child, REVISION)))
                    || !Objects.equals(officeRevision, asRevision(nodes.getProperty(child, OFFICE_REVISION)))
                    || !artifactType.equals(nodes.getProperty(child, KIND))
                    || nodes.getProperty(child, SOURCE) != null) continue;
            if (found != null) throw new CorrespondenceRepositoryException("Duplicate Office PDF artifacts");
            found = describe(child);
        }
        return Optional.ofNullable(found);
    }

    public void saveOffice(String technicalId, long reviewRevision, long officeRevision,
            String artifactType, String filename, byte[] pdf, EmployeeId employee) {
        requireOfficeIdentity(reviewRevision, officeRevision, artifactType);
        requirePdf(pdf);
        if (findOffice(technicalId, reviewRevision, officeRevision, artifactType).isPresent())
            throw new CorrespondenceRepositoryException("Generated Office PDF already exists for this distribution");
        String name = "office-" + artifactType.toLowerCase(java.util.Locale.ROOT)
                + "-r" + reviewRevision + "-o" + officeRevision;
        persist(technicalId, reviewRevision, officeRevision, artifactType,
                null, filename, pdf, employee, name);
    }

    private static void requireOfficeIdentity(long review, long office, String artifactType) {
        if (review < 1 || office < 1 || !("OFFICIAL_ACTION_SHEET".equals(artifactType)
                || "CORRESPONDENCE_PACKET".equals(artifactType)))
            throw new IllegalArgumentException("Invalid Office PDF identity");
    }

    private static void requirePdf(byte[] pdf) {
        if (pdf == null || pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P'
                || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-')
            throw new IllegalArgumentException("Generated content is not a valid PDF header");
    }

    private void persist(String technicalId, long reviewRevision, Long officeRevision,
            String artifactType, String sourceNodeId, String filename, byte[] pdf,
            EmployeeId employee, String name) {
        NodeRef correspondence = locator.locate(technicalId);
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, filename);
        properties.put(REVISION, reviewRevision);
        if (officeRevision != null) properties.put(OFFICE_REVISION, officeRevision);
        properties.put(KIND, artifactType);
        if (sourceNodeId != null) properties.put(SOURCE, sourceNodeId);
        properties.put(CREATOR, employee.value());
        properties.put(CREATED, Date.from(clock.instant()));
        properties.put(HASH, sha256(pdf));
        ChildAssociationRef created = nodes.createNode(correspondence,
                ContentModel.ASSOC_CONTAINS, q(name), TYPE, properties);
        if (created == null || created.getChildRef() == null) {
            throw new CorrespondenceRepositoryException("Generated document node was not created");
        }
        ContentWriter writer = content.getWriter(created.getChildRef(), ContentModel.PROP_CONTENT, true);
        if (writer == null) throw new CorrespondenceRepositoryException("Generated PDF writer missing");
        writer.setMimetype("application/pdf");
        writer.putContent(new ByteArrayInputStream(pdf));
    }

    static CorrespondenceGeneratedDocument describe(NodeRef child, NodeService nodes) {
        Serializable contentValue = nodes.getProperty(child, ContentModel.PROP_CONTENT);
        Serializable rev = nodes.getProperty(child, REVISION);
        Serializable kind = nodes.getProperty(child, KIND);
        Serializable name = nodes.getProperty(child, ContentModel.PROP_NAME);
        Serializable source = nodes.getProperty(child, SOURCE);
        Serializable checksum = nodes.getProperty(child, HASH);
        Serializable creator = nodes.getProperty(child, CREATOR);
        Serializable created = nodes.getProperty(child, CREATED);
        if (!(contentValue instanceof ContentData data) ||
                !"application/pdf".equals(data.getMimetype()) || !(rev instanceof Number number)
                || number.longValue() < 1 || !(kind instanceof String type)
                || !(name instanceof String filename) || !(checksum instanceof String hash)
                || hash.length() != 64 || !(creator instanceof String) || !(created instanceof Date)) {
            throw new CorrespondenceRepositoryException("Invalid generated PDF audit metadata");
        }
        Serializable office = nodes.getProperty(child, OFFICE_REVISION);
        return new CorrespondenceGeneratedDocument(child.getId(), filename,
                data.getMimetype(), data.getSize(), type,
                source instanceof String text ? text : null, number.longValue(),
                office instanceof Number officeNumber ? officeNumber.longValue() : null);
    }

    private CorrespondenceGeneratedDocument describe(NodeRef child) {
        return describe(child, nodes);
    }

    private static Long asRevision(Serializable value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static QName q(String name) { return QName.createQName(NS, name); }
}

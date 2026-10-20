package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.OfficeCompletionRepository;
import org.badea.correspondence.application.OfficeCompletionState;
import org.badea.correspondence.application.OfficeCompletionStateConflictException;
import org.badea.correspondence.application.SaveOfficeCompletionCommand;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

/** Append-only snapshots; HEAD revision belongs to parent correspondence. */
public final class AlfrescoOfficeCompletionRepository implements OfficeCompletionRepository {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final QName TYPE = q("officeCompletionSnapshot");
    private static final QName P_REVISION = q("officeCompletionCurrentRevision");
    private static final QName P_SNAPSHOT_REVISION = q("officeSnapshotRevision");
    private static final QName P_SNAPSHOT_CYCLE = q("officeSnapshotCycle");
    private static final QName P_PRESIDENT_REVISION = q("officePresidentReviewRevision");
    private static final QName P_EMPLOYEE = q("officeActorEmployeeId");
    private static final QName P_ROLE = q("officeActorRole");
    private static final QName P_NAME_EN = q("officeActorNameEnglish");
    private static final QName P_NAME_AR = q("officeActorNameArabic");
    private static final QName P_AT = q("officeActedAt");
    private static final QName P_HASH = q("officeSnapshotSha256");

    private final NodeService nodes;
    private final ContentService content;
    private final PermissionService permissions;
    private final CorrespondenceNodeLocator locator;
    private final Clock clock;

    public AlfrescoOfficeCompletionRepository(NodeService nodes, ContentService content,
            PermissionService permissions, CorrespondenceNodeLocator locator, Clock clock) {
        this.nodes = Objects.requireNonNull(nodes);
        this.content = Objects.requireNonNull(content);
        this.permissions = Objects.requireNonNull(permissions);
        this.locator = Objects.requireNonNull(locator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public Optional<OfficeCompletionState> find(String id) {
        NodeRef correspondence = locate(id);
        require(correspondence, PermissionService.READ);
        long revision = currentRevision(correspondence);
        if (revision == 0L) return Optional.empty();
        NodeRef snapshot = latestSnapshot(correspondence, revision);
        long activeCycle = new AlfrescoCorrespondenceCycleReader(nodes).current(correspondence);
        long snapshotCycle = AlfrescoCorrespondenceCycleReader.number(
                nodes.getProperty(snapshot, P_SNAPSHOT_CYCLE), "officeSnapshotCycle");
        if (snapshotCycle != activeCycle) return Optional.empty();
        ContentReader reader = content.getReader(snapshot,ContentModel.PROP_CONTENT);
        if (reader == null || !reader.exists()) {
            throw new CorrespondenceRepositoryException("Missing Office completion snapshot content");
        }
        String json = reader.getContentString();
        if (json == null || !sha256(json).equals(nodes.getProperty(snapshot,P_HASH))) {
            throw new CorrespondenceRepositoryException("Office completion checksum mismatch");
        }
        Serializable date = nodes.getProperty(snapshot,P_AT);
        Serializable president = nodes.getProperty(snapshot,P_PRESIDENT_REVISION);
        Serializable employee = nodes.getProperty(snapshot,P_EMPLOYEE);
        Serializable name = nodes.getProperty(snapshot,P_NAME_EN);
        Serializable actorRole = nodes.getProperty(snapshot,P_ROLE);
        Serializable arabic = nodes.getProperty(snapshot,P_NAME_AR);
        if (!(date instanceof Date at) || !(president instanceof Number presidentRev)
                || !(employee instanceof String employeeId) || !(name instanceof String english)
                || !"PRESIDENT_OFFICE".equals(actorRole)) {
            throw new CorrespondenceRepositoryException("Office completion audit metadata is incomplete");
        }
        return Optional.of(new OfficeCompletionState(revision,presidentRev.longValue(),json,
                employeeId,english,arabic instanceof String ar ? ar : null,at.toInstant()));
    }

    @Override public long headRevision(String id) {
        NodeRef correspondence = locate(id);
        require(correspondence, PermissionService.READ);
        return currentRevision(correspondence);
    }

    @Override public OfficeCompletionState save(SaveOfficeCompletionCommand command) {
        Objects.requireNonNull(command);
        NodeRef correspondence = locate(command.technicalId());
        require(correspondence, PermissionService.WRITE);
        long old = currentRevision(correspondence);
        if (old != command.expectedRevision()) {
            throw new OfficeCompletionStateConflictException("Office completion revision conflict");
        }
        long next = old + 1L;
        Date at = Date.from(clock.instant());
        Map<QName,Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, String.format("office-completion-revision-%010d.json", next));
        properties.put(P_SNAPSHOT_REVISION, next);
        properties.put(P_SNAPSHOT_CYCLE, new AlfrescoCorrespondenceCycleReader(nodes).current(correspondence));
        properties.put(P_PRESIDENT_REVISION, command.expectedPresidentReviewRevision());
        properties.put(P_EMPLOYEE, command.actorEmployeeId().value());
        properties.put(P_ROLE, "PRESIDENT_OFFICE");
        properties.put(P_NAME_EN, command.actorNameEnglish());
        if (command.actorNameArabic()!=null) properties.put(P_NAME_AR,command.actorNameArabic());
        properties.put(P_AT,at);
        properties.put(P_HASH,sha256(command.json()));
        ChildAssociationRef created=nodes.createNode(correspondence,ContentModel.ASSOC_CONTAINS,
                q("office-completion-revision-"+next),TYPE,properties);
        if (created == null || created.getChildRef() == null) {
            throw new CorrespondenceRepositoryException("Office audit snapshot was not created");
        }
        ContentWriter writer=content.getWriter(created.getChildRef(),ContentModel.PROP_CONTENT,true);
        if (writer == null) throw new CorrespondenceRepositoryException("Office audit writer missing");
        writer.setMimetype("application/json");
        writer.setEncoding("UTF-8");
        writer.putContent(command.json());
        // Written in the same required Alfresco transaction as the audit child.
        // The parent update participates in Alfresco optimistic concurrency checks.
        nodes.setProperty(correspondence,P_REVISION,next);
        return new OfficeCompletionState(next,command.expectedPresidentReviewRevision(),
                command.json(),command.actorEmployeeId().value(), command.actorNameEnglish(),
                command.actorNameArabic(), at.toInstant());
    }

    private NodeRef locate(String id) {
        NodeRef correspondence=locator.locate(id);
        if (!q("correspondence").equals(nodes.getType(correspondence))) {
            throw new CorrespondenceRepositoryException("Unexpected correspondence node type");
        }
        return correspondence;
    }
    private void require(NodeRef node,String permission) {
        if (permissions.hasPermission(node,permission) != AccessStatus.ALLOWED) {
            throw new CorrespondenceAuthorizationException("Alfresco permission denied for Office completion");
        }
    }
    private long currentRevision(NodeRef parent) {
        Serializable value = nodes.getProperty(parent,P_REVISION);
        if (value == null) return 0L;
        if (!(value instanceof Number number) || number.longValue()<0L) {
            throw new CorrespondenceRepositoryException("Invalid Office completion HEAD revision");
        }
        return number.longValue();
    }
    private NodeRef latestSnapshot(NodeRef parent,long revision) {
        NodeRef found = null;
        for (ChildAssociationRef child : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(child.getTypeQName())) continue;
            NodeRef ref=child.getChildRef();
            if (!TYPE.equals(nodes.getType(ref))) continue;
            Serializable rev=nodes.getProperty(ref,P_SNAPSHOT_REVISION);
            if (!(rev instanceof Number n)) {
                throw new CorrespondenceRepositoryException("Invalid Office snapshot revision metadata");
            }
            if (n.longValue()==revision) {
                if (found!=null) throw new CorrespondenceRepositoryException("Duplicate Office completion revision");
                found=ref;
            }
        }
        if (found==null) throw new CorrespondenceRepositoryException("Missing Office completion HEAD snapshot");
        return found;
    }
    private static String sha256(String text) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static QName q(String local) { return QName.createQName(NS,local); }
}

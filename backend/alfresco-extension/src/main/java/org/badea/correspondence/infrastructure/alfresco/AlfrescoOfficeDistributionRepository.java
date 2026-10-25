package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.badea.correspondence.infrastructure.pdf.PdfBoxCorrespondencePacketBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.DepartmentAssignment;
import org.badea.correspondence.application.OfficeDispatchCommand;
import org.badea.correspondence.application.OfficeDistributionPlan;
import org.badea.correspondence.application.OfficeDistributionRepository;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/** All writes execute inside one REQUIRED Alfresco Web Script transaction. */
public final class AlfrescoOfficeDistributionRepository implements OfficeDistributionRepository {
    private static final QName DISPATCH = AlfrescoDepartmentAssignmentReader.q("assignmentDispatchId");
    private final NodeService nodes;
    private final ContentService content;
    private final CorrespondenceNodeLocator locator;
    private final AlfrescoCorrespondenceRepository transitions;
    private final AlfrescoPresidentGeneratedDocumentStore documents;
    private final AlfrescoDepartmentAssignmentReader assignments;

    public AlfrescoOfficeDistributionRepository(NodeService nodes, ContentService content,
            CorrespondenceNodeLocator locator,
            AlfrescoCorrespondenceRepository transitions, AlfrescoPresidentGeneratedDocumentStore documents) {
        this.nodes = Objects.requireNonNull(nodes);
        this.content = Objects.requireNonNull(content);
        this.locator = Objects.requireNonNull(locator);
        this.transitions = Objects.requireNonNull(transitions);
        this.documents = Objects.requireNonNull(documents);
        this.assignments = new AlfrescoDepartmentAssignmentReader(nodes);
    }

    @Override public Optional<String> dispatchId(String technicalId, long presidentRevision, long officeRevision) {
        NodeRef parent = locator.locate(technicalId);
        String id = dispatchKey(technicalId, presidentRevision, officeRevision);
        for (ChildAssociationRef association : nodes.getChildAssocs(parent)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef node = association.getChildRef();
            if (!AlfrescoDepartmentAssignmentReader.TYPE.equals(nodes.getType(node))) continue;
            if (id.equals(nodes.getProperty(node, DISPATCH))) return Optional.of(id);
        }
        return Optional.empty();
    }

    @Override public String distribute(OfficeDispatchCommand command, OfficeDistributionPlan plan,
            byte[] sheet, List<DepartmentAssignment> recipients) {
        NodeRef parent = locator.locate(command.technicalId());
        String id = dispatchKey(command.technicalId(), plan.presidentRevision(), plan.officeRevision());
        if (dispatchId(command.technicalId(), plan.presidentRevision(), plan.officeRevision()).isPresent()) return id;
        if (nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("state")) == null
                || !"WITH_PRESIDENT_OFFICE".equals(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("state")))) {
            throw new IllegalStateException("Correspondence state changed during Office dispatch");
        }
        if (!assignments.readCurrent(parent).isEmpty())
            throw new IllegalStateException("An earlier active distribution must be completed or closed");

        Object officialNumber = nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("documentNumber"));
        // Older test correspondence may carry legacy manual numbers. Never silently
        // assign a new number at distribution time or alter archived history.
        String reference = officialNumber instanceof String value ? value : null;
        if (reference == null || !reference.matches("[0-9]+/[0-9]{8}/[1-9][0-9]*")) {
            // Until older correspondence is formally migrated, retain its original
            // action sheet without creating a misleading newly stamped packet.
            reference = null;
        }
        byte[] packet = reference == null ? null : new PdfBoxCorrespondencePacketBuilder()
                .build(reference, sheet, distributionDocuments(parent, plan.presidentRevision()));
        documents.save(command.technicalId(), plan.presidentRevision(), "OFFICIAL_ACTION_SHEET", null,
                "official-arabic-action-sheet.pdf", sheet, command.actorEmployeeId());
        var saved = documents.find(command.technicalId(), plan.presidentRevision(), "OFFICIAL_ACTION_SHEET", null)
                .orElseThrow(() -> new IllegalStateException("Official action sheet not persisted"));
        nodes.setProperty(new NodeRef("workspace://SpacesStore/" + saved.nodeId()),
                AlfrescoDepartmentAssignmentReader.q("generatedOfficeRevision"), plan.officeRevision());
        if (packet != null) {
            documents.save(command.technicalId(), plan.presidentRevision(), "CORRESPONDENCE_PACKET", null,
                    "correspondence-packet.pdf", packet, command.actorEmployeeId());
            var savedPacket = documents.find(command.technicalId(), plan.presidentRevision(),
                    "CORRESPONDENCE_PACKET", null)
                    .orElseThrow(() -> new IllegalStateException("Correspondence PDF packet not persisted"));
            nodes.setProperty(new NodeRef("workspace://SpacesStore/" + savedPacket.nodeId()),
                    AlfrescoDepartmentAssignmentReader.q("generatedOfficeRevision"), plan.officeRevision());
        }
        for (DepartmentAssignment assignment : recipients) createAssignment(parent, id, plan, assignment);
        TransitionCorrespondenceCommand transition = new TransitionCorrespondenceCommand(
                command.technicalId(), CorrespondenceActorRole.PRESIDENT_OFFICE,
                CorrespondenceAction.DISTRIBUTE_TO_DEPARTMENTS, command.actorEmployeeId(), null,
                command.actorNameEnglish(), command.actorNameArabic(), null,
                "Distribution to " + recipients.size() + " To/Copy departments", null);
        transitions.applyTransition(transition, CorrespondenceState.WITH_PRESIDENT_OFFICE,
                CorrespondenceState.WITH_DIRECTOR, CorrespondenceActorRole.DIRECTOR);
        return id;
    }

    @Override public List<DepartmentAssignment> assignments(String technicalId) {
        return assignments.read(locator.locate(technicalId));
    }

    @Override public DepartmentAssignment complete(String technicalId, String assignmentId,
            org.badea.correspondence.directory.EmployeeId employeeId,
            String employeeNameEnglish,org.badea.correspondence.directory.DepartmentCode department,
            String responseText) {
        if (responseText == null || responseText.isBlank() || responseText.length() > 10000)
            throw new IllegalArgumentException("A response of up to 10000 characters is required");
        NodeRef parent = locator.locate(technicalId);
        DepartmentAssignment current = null;
        NodeRef assignmentNode = null;
        for (var association : nodes.getChildAssocs(parent)) {
            NodeRef node = association.getChildRef();
            if (!AlfrescoDepartmentAssignmentReader.TYPE.equals(nodes.getType(node))) continue;
            if (assignmentId.equals(nodes.getProperty(node, AlfrescoDepartmentAssignmentReader.q("assignmentId")))) {
                assignmentNode = node;
                break;
            }
        }
        if (assignmentNode == null) throw new IllegalArgumentException("Assignment not found");
        for (DepartmentAssignment item : assignments.read(parent)) {
            if (item.assignmentId().equals(assignmentId)) current = item;
        }
        if (current == null || !new AlfrescoCorrespondenceCycleReader(nodes)
                    .isCurrent(parent, assignmentNode)
                || !current.departmentCode().equals(department)
                || current.recipientType() != org.badea.correspondence.application.DepartmentRecipientType.TO)
            throw new org.badea.correspondence.security.CorrespondenceAuthorizationException("Not an actionable assignment");
        if (current.status() == org.badea.correspondence.application.DepartmentAssignmentStatus.COMPLETED) {
            if (employeeId.value().equals(current.completedByEmployeeId())
                    && responseText.equals(current.responseText())) return current;
            throw new IllegalStateException("Assignment already completed; reload correspondence");
        }
        if (!"WITH_DIRECTOR".equals(nodes.getProperty(parent, AlfrescoDepartmentAssignmentReader.q("state"))))
            throw new IllegalStateException("Assignment no longer active");
        var now = java.time.Instant.now();
        nodes.setProperty(assignmentNode,AlfrescoDepartmentAssignmentReader.q("assignmentStatus"),"COMPLETED");
        nodes.setProperty(assignmentNode,AlfrescoDepartmentAssignmentReader.q("assignmentCompletedByEmployeeId"), employeeId.value());
        nodes.setProperty(assignmentNode,AlfrescoDepartmentAssignmentReader.q("assignmentCompletedAt"), Date.from(now));
        nodes.setProperty(assignmentNode,AlfrescoDepartmentAssignmentReader.q("assignmentResponseText"), responseText);
        transitions.recordDepartmentCompletion(new TransitionCorrespondenceCommand(
                technicalId,CorrespondenceActorRole.DIRECTOR,
                CorrespondenceAction.COMPLETE_DEPARTMENT_ASSIGNMENT,employeeId,
                department,employeeNameEnglish,null,department,responseText,null));
        return new DepartmentAssignment(current.assignmentId(),technicalId,department,
                current.recipientType(), org.badea.correspondence.application.DepartmentAssignmentStatus.COMPLETED,
                current.assignedByEmployeeId(),current.assignedAt(),employeeId.value(),now,responseText);
    }

    /** Prefer the latest finalized annotations through this presidential revision, once per source PDF. */
    List<byte[]> distributionDocuments(NodeRef correspondence, long reviewRevision) {
        var sources = new ArrayList<NodeRef>();
        var approved = new HashMap<String, NodeRef>();
        var bestRevision = new HashMap<String, Long>();
        for (ChildAssociationRef association : nodes.getChildAssocs(correspondence)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(association.getTypeQName())) continue;
            NodeRef child = association.getChildRef();
            if (ContentModel.TYPE_CONTENT.equals(nodes.getType(child))) {
                sources.add(child);
            } else if (AlfrescoPresidentGeneratedDocumentStore.TYPE.equals(nodes.getType(child))
                    && "ANNOTATED_PDF".equals(nodes.getProperty(child, AlfrescoPresidentGeneratedDocumentStore.KIND))
                    && nodes.getProperty(child, AlfrescoPresidentGeneratedDocumentStore.REVISION) instanceof Number rev
                    && rev.longValue() <= reviewRevision && rev.longValue() > 0
                    && nodes.getProperty(child, AlfrescoPresidentGeneratedDocumentStore.SOURCE) instanceof String sourceId) {
                // An earlier cycle's finalized annotations remain authoritative
                // until a newer finalized rendition of that source is available.
                Long previous = bestRevision.get(sourceId);
                if (previous != null && previous == rev.longValue())
                    throw new CorrespondenceRepositoryException("Duplicate annotated PDF revision");
                if (previous == null || previous < rev.longValue()) {
                    bestRevision.put(sourceId, rev.longValue());
                    approved.put(sourceId, child);
                }
            }
        }
        sources.sort(Comparator.comparing(child -> String.valueOf(nodes.getProperty(child, ContentModel.PROP_NAME))));
        var result = new ArrayList<byte[]>();
        for (NodeRef source : sources) {
            NodeRef selected = approved.getOrDefault(source.getId(), source);
            ContentReader reader = content.getReader(selected, ContentModel.PROP_CONTENT);
            if (reader == null || !reader.exists())
                throw new CorrespondenceRepositoryException("Missing source or annotated PDF for packet");
            try (InputStream stream = reader.getContentInputStream()) {
                if (stream == null) throw new CorrespondenceRepositoryException("Missing PDF content stream");
                result.add(stream.readAllBytes());
            } catch (IOException failure) {
                throw new CorrespondenceRepositoryException("Could not read distribution PDF content", failure);
            }
        }
        return List.copyOf(result);
    }

    private void createAssignment(NodeRef parent, String dispatchId,
            OfficeDistributionPlan plan, DepartmentAssignment assignment) {
        Map<QName, Serializable> props = new HashMap<>();
        props.put(ContentModel.PROP_NAME, "assignment-" + assignment.assignmentId());
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentId"), assignment.assignmentId());
        props.put(DISPATCH, dispatchId);
        props.put(AlfrescoCorrespondenceCycleReader.ASSIGNMENT,
                new AlfrescoCorrespondenceCycleReader(nodes).current(parent));
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentDepartmentCode"), assignment.departmentCode().value());
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentRecipientType"), assignment.recipientType().name());
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentStatus"), assignment.status().name());
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentByEmployeeId"), assignment.assignedByEmployeeId());
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentAt"), Date.from(assignment.assignedAt()));
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentPresidentRevision"), plan.presidentRevision());
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentOfficeRevision"), plan.officeRevision());
        ChildAssociationRef created = nodes.createNode(parent, ContentModel.ASSOC_CONTAINS,
                AlfrescoDepartmentAssignmentReader.q("assignment-" + assignment.assignmentId()),
                AlfrescoDepartmentAssignmentReader.TYPE, props);
        if (created == null || created.getChildRef() == null)
            throw new CorrespondenceRepositoryException("Department assignment creation failed");
    }

    private static String dispatchKey(String id, long president, long office) {
        return UUID.nameUUIDFromBytes((id + ":" + president + ":" + office)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}

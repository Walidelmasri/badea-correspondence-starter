package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
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
    private final CorrespondenceNodeLocator locator;
    private final AlfrescoCorrespondenceRepository transitions;
    private final AlfrescoPresidentGeneratedDocumentStore documents;
    private final AlfrescoDepartmentAssignmentReader assignments;

    public AlfrescoOfficeDistributionRepository(NodeService nodes, CorrespondenceNodeLocator locator,
            AlfrescoCorrespondenceRepository transitions, AlfrescoPresidentGeneratedDocumentStore documents) {
        this.nodes = Objects.requireNonNull(nodes);
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
        if (!assignments.read(parent).isEmpty())
            throw new IllegalStateException("An earlier active distribution must be completed or closed");
        documents.save(command.technicalId(), plan.presidentRevision(), "OFFICIAL_ACTION_SHEET", null,
                "official-arabic-action-sheet.pdf", sheet, command.actorEmployeeId());
        var saved = documents.find(command.technicalId(), plan.presidentRevision(), "OFFICIAL_ACTION_SHEET", null)
                .orElseThrow(() -> new IllegalStateException("Official action sheet not persisted"));
        nodes.setProperty(new NodeRef("workspace://SpacesStore/" + saved.nodeId()),
                AlfrescoDepartmentAssignmentReader.q("generatedOfficeRevision"), plan.officeRevision());
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
        if (current == null || !current.departmentCode().equals(department)
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

    private void createAssignment(NodeRef parent, String dispatchId,
            OfficeDistributionPlan plan, DepartmentAssignment assignment) {
        Map<QName, Serializable> props = new HashMap<>();
        props.put(ContentModel.PROP_NAME, "assignment-" + assignment.assignmentId());
        props.put(AlfrescoDepartmentAssignmentReader.q("assignmentId"), assignment.assignmentId());
        props.put(DISPATCH, dispatchId);
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

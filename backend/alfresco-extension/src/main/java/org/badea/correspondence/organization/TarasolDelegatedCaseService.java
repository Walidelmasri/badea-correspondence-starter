package org.badea.correspondence.organization;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.badea.correspondence.application.CorrespondenceAccessContext;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsRepository;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CorrespondenceDocumentContentRepository;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CorrespondenceTransitionRepository;
import org.badea.correspondence.application.CorrespondenceTransitionService;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.CorrespondenceNodeLocator;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Exclusive entry point for individual delegated cases. Never used by the
 * ordinary mailbox, Director workspace resolver or generic document endpoints.
 * Oracle eligibility, persisted Alfresco case metadata AND native Alfresco ACLs
 * must all be valid for each read/write, including after revocation.
 */
public final class TarasolDelegatedCaseService {
    private final TarasolEffectiveAccessReader access;
    private final CorrespondenceTransitionRepository transitions;
    private final CorrespondenceDetailsRepository details;
    private final CorrespondenceDocumentContentRepository content;
    private final CorrespondenceTransitionService workflow;
    private final CorrespondenceNodeLocator locator;
    private final NodeService nodes;
    private final PermissionService permissions;

    public TarasolDelegatedCaseService(TarasolEffectiveAccessReader access,
            CorrespondenceTransitionRepository transitions,
            CorrespondenceDetailsRepository details,
            CorrespondenceDocumentContentRepository content,
            CorrespondenceTransitionService workflow, CorrespondenceNodeLocator locator,
            NodeService nodes, PermissionService permissions) {
        this.access = Objects.requireNonNull(access);
        this.transitions = Objects.requireNonNull(transitions);
        this.details = Objects.requireNonNull(details);
        this.content = Objects.requireNonNull(content);
        this.workflow = Objects.requireNonNull(workflow);
        this.locator = Objects.requireNonNull(locator);
        this.nodes = Objects.requireNonNull(nodes);
        this.permissions = Objects.requireNonNull(permissions);
    }

    /** No role/group escalation; exact correspondence selected by technical UUID. */
    public CorrespondenceDetails read(EmployeeDirectoryEntry employee,
            DepartmentCode unit, String technicalId) {
        authorize(employee, unit, technicalId, false);
        return details.findByTechnicalId(technicalId);
    }

    public record Preview(CorrespondenceDocument document, InputStream stream) { }

    public Preview openPdf(EmployeeDirectoryEntry employee, DepartmentCode unit,
            String technicalId, String documentNodeId) {
        validateId(documentNodeId);
        CorrespondenceDetails saved = read(employee, unit, technicalId);
        var document = saved.documents().stream()
            .filter(d -> d.nodeId().equals(documentNodeId)
                    && "application/pdf".equalsIgnoreCase(d.mimeType()))
            .findFirst().orElseThrow(CorrespondenceDocumentNotFoundException::new);
        return new Preview(document, content.openByNodeId(document.nodeId()));
    }

    /** Delegation never gives Office/President powers or permits a draft send. */
    public CorrespondenceState act(EmployeeDirectoryEntry employee, DepartmentCode unit,
            String technicalId, CorrespondenceAction action) {
        if (action != CorrespondenceAction.SEND_TO_OFFICE
                && action != CorrespondenceAction.MARK_DONE) {
            throw new CorrespondenceAuthorizationException("Delegation action is outside the permitted scope");
        }
        ValidatedCase authorization = authorize(employee, unit, technicalId, true);
        CorrespondenceAccessContext persisted = authorization.context();
        if (persisted.state() != CorrespondenceState.WITH_DIRECTOR
                || persisted.assignedRole() != CorrespondenceActorRole.DIRECTOR
                || !unit.equals(persisted.assignedDepartmentCode())
                || !persisted.assignments().isEmpty()) {
            throw new CorrespondenceAuthorizationException("This case has no delegated parent Director action");
        }
        return workflow.transition(new TransitionCorrespondenceCommand(technicalId,
            CorrespondenceActorRole.DIRECTOR, action, employee.employeeId(), unit,
            employee.nameEnglish(), employee.nameArabic(), null,
            "Tarasol delegated action; delegation ID " + authorization.grant().id()
                + "; manager assignment ID " + authorization.grant().parentManagerAssignmentId(),
            null));
    }

    private record ValidatedCase(CorrespondenceAccessContext context,
            TarasolEffectiveAccessReader.Delegation grant) { }

    private ValidatedCase authorize(EmployeeDirectoryEntry employee,
            DepartmentCode unit, String technicalId, boolean write) {
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(unit, "unit");
        validateId(technicalId);
        var node = locator.locate(technicalId);
        var context = transitions.getAccessContext(technicalId);
        // The delegated unit must be the current Director recipient, or the
        // originating unit for an explicitly granted historical case handoff.
        boolean currentDirector = context.state() == CorrespondenceState.WITH_DIRECTOR
            && context.assignedRole() == CorrespondenceActorRole.DIRECTOR
            && unit.equals(context.assignedDepartmentCode());
        boolean originUnit = unit.equals(context.originDepartmentCode());
        if (!currentDirector && !originUnit) throw denied();
        var created = nodes.getProperty(node, ContentModel.PROP_CREATED);
        if (!(created instanceof Date savedAt)) throw denied();
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(savedAt.toInstant(), ZoneOffset.UTC);
        var grant = access.findActiveDelegation(employee.employeeId(), unit, technicalId, createdAt)
            .orElseThrow(TarasolDelegatedCaseService::denied);
        if (!currentDirector && !"CASE_HANDOFF".equals(grant.type())) throw denied();
        if (permissions.hasPermission(node, PermissionService.READ) != AccessStatus.ALLOWED
                || (write && permissions.hasPermission(node, PermissionService.WRITE)
                    != AccessStatus.ALLOWED)) throw denied();
        return new ValidatedCase(context, grant);
    }

    private static void validateId(String text) {
        try {
            if (text == null || !UUID.fromString(text).toString().equals(text)) {
                throw new IllegalArgumentException("Canonical correspondence ID required");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Canonical correspondence ID required", invalid);
        }
    }

    private static CorrespondenceAuthorizationException denied() {
        return new CorrespondenceAuthorizationException("Delegation does not authorize this case");
    }
}

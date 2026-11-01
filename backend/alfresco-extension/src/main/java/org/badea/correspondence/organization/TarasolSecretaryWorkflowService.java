package org.badea.correspondence.organization;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceRepository;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/**
 * Isolated secretary approval lifecycle. A secretary has no generic Director
 * authority. Managers are revalidated from Oracle AND Alfresco on every action.
 * Storage transitions must run in a REQUIRED Alfresco transaction and storage
 * exceptions must escape the Web Script to force rollback.
 */
public final class TarasolSecretaryWorkflowService {
    private final TarasolEffectiveAccessReader access;
    private final TarasolOrganizationReader organization;
    private final CorrespondenceActorRoleResolver roles;
    private final AlfrescoCorrespondenceRepository correspondence;

    public TarasolSecretaryWorkflowService(TarasolEffectiveAccessReader access,
            TarasolOrganizationReader organization,
            CorrespondenceActorRoleResolver roles,
            AlfrescoCorrespondenceRepository correspondence) {
        this.access = Objects.requireNonNull(access);
        this.organization = Objects.requireNonNull(organization);
        this.roles = Objects.requireNonNull(roles);
        this.correspondence = Objects.requireNonNull(correspondence);
    }

    public String create(EmployeeDirectoryEntry secretary, DepartmentCode unit,
            String subject, String bodyText) {
        requireSecretary(secretary, unit);
        if (subject == null || subject.isBlank() || subject.length() > 500) {
            throw new IllegalArgumentException("Subject must be 1-500 characters");
        }
        return correspondence.createSecretaryDraft(new CreateCorrespondenceCommand(
            unit, subject, bodyText, null, null, null,
            secretary.employeeId(), secretary.nameEnglish(), secretary.nameArabic(),
            CorrespondenceActorRole.DIRECTOR, null));
    }

    public List<AlfrescoCorrespondenceRepository.SecretaryDraft> mine(
            EmployeeDirectoryEntry secretary, DepartmentCode unit) {
        requireSecretary(secretary, unit);
        return correspondence.listSecretaryDrafts(unit, secretary.employeeId().value(), false);
    }

    public List<AlfrescoCorrespondenceRepository.SecretaryDraft> pending(
            EmployeeDirectoryEntry manager, DepartmentCode unit) {
        requireManager(manager, unit);
        return correspondence.listSecretaryDrafts(unit, null, true);
    }

    /** Manager preview is allowed only while pending; personal preview is owner-only. */
    public void authorizePreview(EmployeeDirectoryEntry actor, DepartmentCode unit,
            String technicalId, boolean managerView) {
        if (managerView) requireManager(actor, unit);
        else requireSecretary(actor, unit);
        var draft = correspondence.readSecretaryDraft(technicalId);
        if (!draft.unitCode().equals(unit.value())) throw denied();
        if (managerView) {
            if (!"PENDING".equals(draft.approvalStatus())) throw denied();
        } else if (!draft.ownerEmployeeId().equals(actor.employeeId().value())) {
            throw denied();
        }
    }

    public void authorizePdfUpload(EmployeeDirectoryEntry secretary, DepartmentCode unit,
            String technicalId) {
        requireSecretary(secretary, unit);
        var draft = correspondence.readSecretaryDraft(technicalId);
        if (!draft.unitCode().equals(unit.value())
                || !draft.ownerEmployeeId().equals(secretary.employeeId().value())
                || (!"DRAFT".equals(draft.approvalStatus())
                    && !"RETURNED".equals(draft.approvalStatus()))) throw denied();
    }

    public void submit(EmployeeDirectoryEntry secretary, DepartmentCode unit,
            String technicalId) {
        requireSecretary(secretary, unit);
        var draft = correspondence.readSecretaryDraft(technicalId);
        if (!draft.unitCode().equals(unit.value())
                || !draft.ownerEmployeeId().equals(secretary.employeeId().value())) {
            throw denied();
        }
        if (!"DRAFT".equals(draft.approvalStatus()) && !"RETURNED".equals(draft.approvalStatus())) {
            throw new IllegalArgumentException("This secretary draft has already been submitted");
        }
        correspondence.changeSecretaryStatus(command(technicalId, secretary, unit,
            CorrespondenceActorRole.SECRETARY, CorrespondenceAction.SUBMIT_FOR_MANAGER_APPROVAL,
            "101".equals(unit.value()) ? null : unit, null), draft.approvalStatus(), "PENDING",
            "101".equals(unit.value()) ? CorrespondenceActorRole.PRESIDENT_OFFICE
                : CorrespondenceActorRole.DIRECTOR);
    }

    public void revise(EmployeeDirectoryEntry secretary, DepartmentCode unit,
            String technicalId, String subject, String bodyText) {
        requireSecretary(secretary, unit);
        var draft = correspondence.readSecretaryDraft(technicalId);
        if (!draft.unitCode().equals(unit.value())
                || !draft.ownerEmployeeId().equals(secretary.employeeId().value())) throw denied();
        if (subject == null || subject.isBlank() || subject.length() > 500) {
            throw new IllegalArgumentException("Subject must be 1-500 characters");
        }
        correspondence.reviseSecretaryDraft(command(technicalId, secretary, unit,
            CorrespondenceActorRole.SECRETARY, CorrespondenceAction.REVISE_SECRETARY_DRAFT,
            unit, null), subject, bodyText);
    }

    public void returnForRevision(EmployeeDirectoryEntry manager, DepartmentCode unit,
            String technicalId, String explanation) {
        var workspace = requireManager(manager, unit);
        var draft = correspondence.readSecretaryDraft(technicalId);
        if (!draft.unitCode().equals(unit.value())) throw denied();
        if (!"PENDING".equals(draft.approvalStatus())) {
            throw new IllegalArgumentException("This draft is not pending approval");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("A return explanation is required");
        }
        correspondence.changeSecretaryStatus(command(technicalId, manager, unit,
            workspace.role(), CorrespondenceAction.RETURN_TO_SECRETARY,
            null, explanation), "PENDING", "RETURNED", CorrespondenceActorRole.SECRETARY);
    }

    public CorrespondenceState approveAndSend(EmployeeDirectoryEntry manager,
            DepartmentCode unit, String technicalId) {
        var workspace = requireManager(manager, unit);
        var draft = correspondence.readSecretaryDraft(technicalId);
        if (!draft.unitCode().equals(unit.value())) throw denied();
        if (!"PENDING".equals(draft.approvalStatus())) {
            throw new IllegalArgumentException("This draft is not pending approval");
        }

        CorrespondenceActorRole actorRole = workspace.role();
        boolean officeOrigin = "101".equals(unit.value());
        boolean directPresident;
        if (officeOrigin) {
            if (actorRole != CorrespondenceActorRole.PRESIDENT_OFFICE) throw denied();
            directPresident = true;
        } else {
            if (actorRole != CorrespondenceActorRole.DIRECTOR) throw denied();
            // Organizational authority is checked again at the point of send;
            // a dual manager cannot use a stale Office appointment.
            Set<CorrespondenceActorRole> alfrescoRoles = roles.resolveCurrentUserRoles();
            var current = organization.findCurrentManagerWorkspaces(manager.employeeId());
            directPresident = alfrescoRoles.contains(CorrespondenceActorRole.DIRECTOR)
                && alfrescoRoles.contains(CorrespondenceActorRole.PRESIDENT_OFFICE)
                && !alfrescoRoles.contains(CorrespondenceActorRole.PRESIDENT)
                && current.stream().anyMatch(w -> "101".equals(w.code().value()));
        }
        CorrespondenceAction action = directPresident
            ? CorrespondenceAction.SEND_TO_PRESIDENT : CorrespondenceAction.SEND_TO_OFFICE;
        CorrespondenceState toState = directPresident
            ? CorrespondenceState.WITH_PRESIDENT : CorrespondenceState.WITH_PRESIDENT_OFFICE;
        CorrespondenceActorRole recipient = directPresident
            ? CorrespondenceActorRole.PRESIDENT : CorrespondenceActorRole.PRESIDENT_OFFICE;

        correspondence.approveSecretaryDraft(command(technicalId, manager, unit,
            actorRole, action, null, null), toState, recipient);
        return toState;
    }

    private void requireSecretary(EmployeeDirectoryEntry actor, DepartmentCode unit) {
        if (!access.hasActiveSecretaryAssignment(actor.employeeId(), unit)) throw denied();
    }

    private CorrespondenceActorRoleResolver.Workspace requireManager(
            EmployeeDirectoryEntry actor, DepartmentCode unit) {
        var workspace = roles.resolveWorkspace(unit.value(), actor);
        if (!unit.equals(workspace.unit())
                || (workspace.role() != CorrespondenceActorRole.DIRECTOR
                    && workspace.role() != CorrespondenceActorRole.PRESIDENT_OFFICE)) throw denied();
        return workspace;
    }

    private static TransitionCorrespondenceCommand command(String id,
            EmployeeDirectoryEntry actor, DepartmentCode unit,
            CorrespondenceActorRole role, CorrespondenceAction action,
            DepartmentCode target, String explanation) {
        return new TransitionCorrespondenceCommand(id, role, action,
            actor.employeeId(), unit, actor.nameEnglish(), actor.nameArabic(),
            target, explanation, null);
    }

    private static CorrespondenceAuthorizationException denied() {
        return new CorrespondenceAuthorizationException("Tarasol approval access is not authorized");
    }
}

package org.badea.correspondence.organization;

import java.util.Objects;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.badea.correspondence.application.OfficeCycleContinuationService;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Manager-only decision for any pending secretary continuation cycle. */
public final class TarasolSecretaryRoundContinuationApprovalService {
    private final TarasolOrganizationReader organization;
    private final CorrespondenceActorRoleResolver roles;
    private final AlfrescoSecretaryRoundTwoRequestRepository requests;
    private final OfficeCycleContinuationService cycles;

    public TarasolSecretaryRoundContinuationApprovalService(TarasolOrganizationReader organization,
            CorrespondenceActorRoleResolver roles,
            AlfrescoSecretaryRoundTwoRequestRepository requests,
            OfficeCycleContinuationService cycles) {
        this.organization = Objects.requireNonNull(organization);
        this.roles = Objects.requireNonNull(roles);
        this.requests = Objects.requireNonNull(requests);
        this.cycles = Objects.requireNonNull(cycles);
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Request pending(
            EmployeeDirectoryEntry manager, DepartmentCode unit, String technicalId) {
        managerRole(manager, unit);
        return AuthenticationUtil.runAsSystem(() -> requests.pending(technicalId, unit));
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Decision approve(
            EmployeeDirectoryEntry manager, DepartmentCode unit,
            String technicalId, String requestId) {
        CorrespondenceActorRole role = managerRole(manager, unit);
        return AuthenticationUtil.runAsSystem(() -> {
            var pending = requests.pending(technicalId, unit);
            if (pending == null || !pending.requestId().equals(requestId))
                throw new org.badea.correspondence.application.OfficeCycleConflictException("Pending request changed");
            if (manager.employeeId().value().equals(pending.ownerEmployeeId()))
                throw new CorrespondenceAuthorizationException("Request creator cannot approve own continuation");
            CorrespondenceAction action = role == CorrespondenceActorRole.DIRECTOR
                ? CorrespondenceAction.DIRECTOR_CONTINUE_CORRESPONDENCE
                : CorrespondenceAction.CONTINUE_CORRESPONDENCE;
            var command = new TransitionCorrespondenceCommand(technicalId, role, action,
                manager.employeeId(), unit, manager.nameEnglish(), manager.nameArabic(),
                null, pending.reason(), null);
            return requests.approve(technicalId, unit, requestId,
                manager.employeeId().value(), () -> cycles.continueCycle(command));
        });
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Decision returnForRevision(
            EmployeeDirectoryEntry manager, DepartmentCode unit, String technicalId,
            String requestId, String reason) {
        managerRole(manager, unit);
        return AuthenticationUtil.runAsSystem(() -> requests.returnForRevision(technicalId, unit,
            requestId, manager.employeeId().value(), reason));
    }

    private CorrespondenceActorRole managerRole(EmployeeDirectoryEntry manager, DepartmentCode unit) {
        Objects.requireNonNull(manager);
        Objects.requireNonNull(unit);
        var workspace = roles.resolveWorkspace(unit.value(), manager);
        if (!unit.equals(workspace.unit())
                || (workspace.role() != CorrespondenceActorRole.DIRECTOR
                    && workspace.role() != CorrespondenceActorRole.PRESIDENT_OFFICE)
                || organization.findCurrentManagerWorkspaces(manager.employeeId()).stream()
                    .noneMatch(appointment -> unit.equals(appointment.code())))
            throw new CorrespondenceAuthorizationException("Active manager authority required");
        if (workspace.role() == CorrespondenceActorRole.PRESIDENT_OFFICE
                && !"101".equals(unit.value()))
            throw new CorrespondenceAuthorizationException("Office manager can act only for unit 101");
        return workspace.role();
    }
}

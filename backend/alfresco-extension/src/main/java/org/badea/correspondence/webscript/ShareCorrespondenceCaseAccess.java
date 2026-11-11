package org.badea.correspondence.webscript;

import java.util.Objects;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.organization.TarasolSecretaryInboxReadService;
import org.badea.correspondence.organization.TarasolSecretaryPersonalSentReadService;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceWorkspaceContext;
import org.badea.correspondence.security.InstitutionalMailboxReadScope;

/** Resolve the same per-case read entitlement as the caller's actual mailbox.
 * No share mutation is run within the privileged institutional read scope.
 */
public final class ShareCorrespondenceCaseAccess {
    private final CurrentEmployeeService employees;
    private final CorrespondenceActorRoleResolver roles;
    private final CorrespondenceDetailsService details;
    private final TarasolSecretaryInboxReadService secretaryInbox;
    private final TarasolSecretaryPersonalSentReadService secretarySent;

    public ShareCorrespondenceCaseAccess(CurrentEmployeeService employees,
            CorrespondenceActorRoleResolver roles, CorrespondenceDetailsService details,
            TarasolSecretaryInboxReadService secretaryInbox,
            TarasolSecretaryPersonalSentReadService secretarySent) {
        this.employees = Objects.requireNonNull(employees);
        this.roles = Objects.requireNonNull(roles);
        this.details = Objects.requireNonNull(details);
        this.secretaryInbox = Objects.requireNonNull(secretaryInbox);
        this.secretarySent = Objects.requireNonNull(secretarySent);
    }

    public record Approved(EmployeeDirectoryEntry employee, CorrespondenceDetails caseDetails) { }

    public Approved require(org.springframework.extensions.webscripts.WebScriptRequest request,
            String technicalId) {
        EmployeeDirectoryEntry actor = employees.getCurrentEmployee();
        String unitValue = request.getParameter("shareSecretaryUnit");
        String box = request.getParameter("shareSecretaryBox");
        if (unitValue != null || box != null) {
            if (unitValue == null || !unitValue.matches("[0-9]{3}")
                    || !("inbox".equals(box) || "sent".equals(box))) {
                throw new IllegalArgumentException("Invalid secretary share workspace");
            }
            DepartmentCode unit = new DepartmentCode(unitValue);
            // Both readers independently verify the active secretary assignment and
            // the individual case's existing Inbox/Sent visibility before returning it.
            CorrespondenceDetails authorized = "inbox".equals(box)
                    ? secretaryInbox.details(actor, unit, technicalId)
                    : secretarySent.details(actor, unit, technicalId);
            return new Approved(actor, authorized);
        }
        var workspace = CorrespondenceWorkspaceContext.resolve(roles,
                request.getParameter("workspaceUnit"), actor);
        CorrespondenceDetails authorized = InstitutionalMailboxReadScope.readValidatedWorkspace(
                workspace.role(), () -> details.getDetails(technicalId, workspace.role(),
                        workspace.unit(), actor.employeeId().value()));
        return new Approved(actor, authorized);
    }
}

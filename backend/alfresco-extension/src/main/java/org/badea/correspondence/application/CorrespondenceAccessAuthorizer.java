package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class CorrespondenceAccessAuthorizer {
    public void authorizeRead(CorrespondenceAccessContext correspondence,
            CorrespondenceActorRole actorRole, DepartmentCode actorDepartmentCode) {
        authorizeRead(correspondence, actorRole, actorDepartmentCode, null);
    }

    /** Read access is distinct from permission to perform the next workflow action. */
    public void authorizeRead(CorrespondenceAccessContext correspondence,
            CorrespondenceActorRole actorRole, DepartmentCode actorDepartmentCode,
            String authenticatedEmployeeId) {
        Objects.requireNonNull(correspondence, "correspondence must not be null");
        Objects.requireNonNull(actorRole, "actorRole must not be null");

        // Preserve the established origin-department permission, including its drafts.
        if (actorRole == CorrespondenceActorRole.DIRECTOR
                && actorDepartmentCode != null
                && actorDepartmentCode.equals(correspondence.originDepartmentCode())) return;

        try {
            authorizeTransition(correspondence, actorRole, actorDepartmentCode);
            return;
        } catch (CorrespondenceAuthorizationException notAssigned) {
            // A historical read may still be legitimate, but never a workflow mutation.
        }
        if (correspondence.state() != CorrespondenceState.DRAFT) {
            for (CorrespondenceParticipation action : correspondence.participation()) {
                if (action.receivedBy(actorRole, actorDepartmentCode)
                        || action.sentBy(actorRole, authenticatedEmployeeId)) return;
            }
        }
        throw forbidden();
    }

    public void authorizeTransition(
            CorrespondenceAccessContext correspondence,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode) {

        Objects.requireNonNull(
                correspondence,
                "correspondence must not be null");

        Objects.requireNonNull(
                actorRole,
                "actorRole must not be null");

        switch (actorRole) {
            case PRESIDENT ->
                authorizePresident(
                        correspondence);

            case PRESIDENT_OFFICE ->
                authorizePresidentOffice(correspondence);

            case DIRECTOR ->
                authorizeDirector(
                        correspondence,
                        actorDepartmentCode);
        }
    }

    private static void authorizePresident(
            CorrespondenceAccessContext correspondence) {

        if (correspondence.state() != CorrespondenceState.WITH_PRESIDENT) {

            throw forbidden();
        }

        if (correspondence.assignedRole() != CorrespondenceActorRole.PRESIDENT) {

            throw forbidden();
        }
    }

    private static void authorizePresidentOffice(
            CorrespondenceAccessContext correspondence) {

        if (correspondence.state() != CorrespondenceState.WITH_PRESIDENT_OFFICE) {

            throw forbidden();
        }

        if (correspondence.assignedRole() != CorrespondenceActorRole.PRESIDENT_OFFICE) {

            throw forbidden();
        }
    }

    private static void authorizeDirector(
            CorrespondenceAccessContext correspondence,
            DepartmentCode actorDepartmentCode) {

        requireDepartment(
                actorDepartmentCode);

        if (correspondence.state() == CorrespondenceState.DRAFT) {

            if (correspondence.originDepartmentCode()
                    .equals(actorDepartmentCode)) {

                return;
            }

            throw forbidden();
        }

        if (correspondence.state() == CorrespondenceState.WITH_DIRECTOR) {

            if (correspondence.assignedRole() != CorrespondenceActorRole.DIRECTOR) {

                throw forbidden();
            }

            if (actorDepartmentCode.equals(
                    correspondence.assignedDepartmentCode())) {

                return;
            }
        }

        throw forbidden();
    }

    private static void requireDepartment(
            DepartmentCode actorDepartmentCode) {

        if (actorDepartmentCode == null) {
            throw new CorrespondenceAuthorizationException(
                    "Authenticated employee has no department required for this correspondence action.");
        }
    }

    private static CorrespondenceAuthorizationException forbidden() {

        return new CorrespondenceAuthorizationException(
                "Authenticated user is not authorized to act on this correspondence.");
    }
}
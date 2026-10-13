package org.badea.correspondence.application;

import java.util.Objects;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

public final class CorrespondenceAccessAuthorizer {
    /**
     * For the current active-workflow read endpoint, visibility is
     * restricted to the role and department assigned to the
     * correspondence.
     *
     * Historical access rules will be separate when the archive
     * and Sent correspondence features are implemented.
     */
    public void authorizeRead(
            CorrespondenceAccessContext correspondence,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode) {

        authorizeTransition(
                correspondence,
                actorRole,
                actorDepartmentCode);
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
                    authorizePresidentOffice(
                            correspondence,
                            actorDepartmentCode);

            case DIRECTOR ->
                    authorizeDirector(
                            correspondence,
                            actorDepartmentCode);
        }
    }

    private static void authorizePresident(
            CorrespondenceAccessContext correspondence) {

        if (correspondence.state()
                != CorrespondenceState.WITH_PRESIDENT) {

            throw forbidden();
        }

        if (correspondence.assignedRole()
                != CorrespondenceActorRole.PRESIDENT) {

            throw forbidden();
        }
    }

    private static void authorizePresidentOffice(
            CorrespondenceAccessContext correspondence,
            DepartmentCode actorDepartmentCode) {

        if (correspondence.state()
                == CorrespondenceState.WITH_PRESIDENT_OFFICE) {

            if (correspondence.assignedRole()
                    != CorrespondenceActorRole.PRESIDENT_OFFICE) {

                throw forbidden();
            }

            return;
        }

        if (correspondence.state()
                == CorrespondenceState.DRAFT) {

            requireDepartment(
                    actorDepartmentCode);

            if (correspondence.originDepartmentCode()
                    .equals(actorDepartmentCode)) {

                return;
            }
        }

        throw forbidden();
    }

    private static void authorizeDirector(
            CorrespondenceAccessContext correspondence,
            DepartmentCode actorDepartmentCode) {

        requireDepartment(
                actorDepartmentCode);

        if (correspondence.state()
                == CorrespondenceState.DRAFT) {

            if (correspondence.originDepartmentCode()
                    .equals(actorDepartmentCode)) {

                return;
            }

            throw forbidden();
        }

        if (correspondence.state()
                == CorrespondenceState.WITH_DIRECTOR) {

            if (correspondence.assignedRole()
                    != CorrespondenceActorRole.DIRECTOR) {

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
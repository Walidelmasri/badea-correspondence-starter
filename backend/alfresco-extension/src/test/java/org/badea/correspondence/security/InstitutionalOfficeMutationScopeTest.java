package org.badea.correspondence.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class InstitutionalOfficeMutationScopeTest {
    @Test
    void onlyAnAuthenticatedOfficeRoleMayRequestTheMutationScope() {
        for (CorrespondenceActorRole role : CorrespondenceActorRole.values()) {
            if (role == CorrespondenceActorRole.PRESIDENT_OFFICE) continue;
            try (MockedStatic<AuthenticationUtil> privilege = mockStatic(AuthenticationUtil.class)) {
                assertThrows(CorrespondenceAuthorizationException.class,
                        () -> InstitutionalOfficeMutationScope.execute(role, () -> "must not run"));
                privilege.verifyNoInteractions();
            }
        }
    }

    @Test
    void eligibleOfficeActionRunsWithinSystemScope() {
        try (MockedStatic<AuthenticationUtil> privilege = mockStatic(AuthenticationUtil.class)) {
            privilege.when(() -> AuthenticationUtil.runAsSystem(any())).thenAnswer(invocation -> {
                AuthenticationUtil.RunAsWork<?> work = invocation.getArgument(0);
                return work.doWork();
            });
            assertEquals("saved", InstitutionalOfficeMutationScope.execute(
                    CorrespondenceActorRole.PRESIDENT_OFFICE, () -> "saved"));
            privilege.verify(() -> AuthenticationUtil.runAsSystem(any()), times(1));
        }
    }
}

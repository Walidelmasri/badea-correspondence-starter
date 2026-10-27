package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.PresidentSignatureStore;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class PresidentSignatureWebScriptTest {
    @Test void neitherRegistrationNorStatusAreAvailableToOfficeUsers() throws Exception {
        var roles = mock(CorrespondenceActorRoleResolver.class);
        var employee = mock(CurrentEmployeeService.class);
        var store = mock(PresidentSignatureStore.class);
        var request = mock(WebScriptRequest.class);
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        for (boolean write : new boolean[]{false, true}) {
            var response = mock(WebScriptResponse.class);
            var out = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(out));
            new PresidentSignatureWebScript(roles, employee, store, write).execute(request, response);
            verify(response).setStatus(Status.STATUS_FORBIDDEN);
            assertTrue(out.toString().contains("PRESIDENT_ONLY"));
        }
        verifyNoInteractions(employee, store);
    }

    @Test void authorizedPresidentCanCheckStatusWithoutExposingStoredStrokes() throws Exception {
        var roles = mock(CorrespondenceActorRoleResolver.class);
        var employee = mock(CurrentEmployeeService.class);
        var store = mock(PresidentSignatureStore.class);
        var request = mock(WebScriptRequest.class);
        var response = mock(WebScriptResponse.class);
        var out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT);
        when(store.status()).thenReturn(new PresidentSignatureStore.Status(true, 2, null));
        new PresidentSignatureWebScript(roles, employee, store, false).execute(request, response);
        verify(response).setStatus(Status.STATUS_OK);
        assertTrue(out.toString().contains("\"configured\":true"));
        assertTrue(!out.toString().contains("strokes"));
        verifyNoInteractions(employee);
    }
}

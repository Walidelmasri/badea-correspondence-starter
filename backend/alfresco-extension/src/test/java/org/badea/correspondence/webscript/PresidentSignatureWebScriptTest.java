package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.PresidentSignatureDrawing;
import org.badea.correspondence.application.PresidentSignatureStore;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class PresidentSignatureWebScriptTest {
    private static final EmployeeDirectoryEntry PRESIDENT = new EmployeeDirectoryEntry(
            new EmployeeId("99999"), new DirectoryUsername("president.user"),
            "President Test", null, Optional.empty());

    @Test void neitherRegistrationNorStatusAreAvailableToOfficeUsers() throws Exception {
        var roles = mock(CorrespondenceActorRoleResolver.class, org.mockito.Mockito.CALLS_REAL_METHODS);
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

    @Test void presidentOnlyReceivesRasterPreviewNotRawStrokes() throws Exception {
        var roles = mock(CorrespondenceActorRoleResolver.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var employee = mock(CurrentEmployeeService.class);
        var store = mock(PresidentSignatureStore.class);
        var request = mock(WebScriptRequest.class);
        var response = mock(WebScriptResponse.class);
        var out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT);
        when(employee.getCurrentEmployee()).thenReturn(PRESIDENT);
        when(store.status()).thenReturn(new PresidentSignatureStore.Status(true, 2, null));
        var signature = new PresidentSignatureDrawing(List.of(List.of(
                new PresidentSignatureDrawing.Point(.1f, .1f),
                new PresidentSignatureDrawing.Point(.2f, .5f),
                new PresidentSignatureDrawing.Point(.7f, .6f),
                new PresidentSignatureDrawing.Point(.9f, .9f))));
        when(store.previewForOwner("99999")).thenReturn(Optional.of(signature));
        new PresidentSignatureWebScript(roles, employee, store, false).execute(request, response);
        var json = new JSONObject(out.toString());
        verify(response).setStatus(Status.STATUS_OK);
        verify(response).setHeader("Cache-Control", "private, no-store");
        assertTrue(json.getBoolean("configured"));
        assertTrue(json.getString("previewDataUrl").startsWith("data:image/png;base64,"));
        assertFalse(out.toString().contains("strokes"));
    }

    @Test void previouslyOwnedSignatureCannotBePreviewedByAnotherPresident() throws Exception {
        var roles = mock(CorrespondenceActorRoleResolver.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var employee = mock(CurrentEmployeeService.class);
        var store = mock(PresidentSignatureStore.class);
        var request = mock(WebScriptRequest.class);
        var response = mock(WebScriptResponse.class);
        var out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT);
        when(employee.getCurrentEmployee()).thenReturn(PRESIDENT);
        when(store.status()).thenReturn(new PresidentSignatureStore.Status(true, 2, null));
        when(store.previewForOwner("99999")).thenReturn(Optional.empty());
        new PresidentSignatureWebScript(roles, employee, store, false).execute(request, response);
        assertTrue(new JSONObject(out.toString()).isNull("previewDataUrl"));
    }

    @Test void deactivationUsesPresidentIdentityAndExpectedRevision() throws Exception {
        var roles = mock(CorrespondenceActorRoleResolver.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var employee = mock(CurrentEmployeeService.class);
        var store = mock(PresidentSignatureStore.class);
        var request = mock(WebScriptRequest.class);
        var response = mock(WebScriptResponse.class);
        var content = mock(Content.class);
        var out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(request.getContent()).thenReturn(content);
        when(content.getContent()).thenReturn("{\"action\":\"deactivate\",\"expectedRevision\":3}");
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT);
        when(employee.getCurrentEmployee()).thenReturn(PRESIDENT);
        when(store.deactivate("99999", 3)).thenReturn(new PresidentSignatureStore.Status(false, 4, null));
        new PresidentSignatureWebScript(roles, employee, store, true).execute(request, response);
        verify(store).deactivate("99999", 3);
        verify(store, never()).register(any(), anyString(), anyLong());
        assertFalse(new JSONObject(out.toString()).getBoolean("configured"));
        verify(response).setStatus(Status.STATUS_OK);
    }

    @Test void wrongWorkspaceIsRejectedBeforeReadingSignature() throws Exception {
        var roles = mock(CorrespondenceActorRoleResolver.class);
        var employee = mock(CurrentEmployeeService.class);
        var store = mock(PresidentSignatureStore.class);
        var request = mock(WebScriptRequest.class);
        var response = mock(WebScriptResponse.class);
        var out = new StringWriter();
        when(request.getParameter("workspaceUnit")).thenReturn("117");
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        new PresidentSignatureWebScript(roles, employee, store, false).execute(request, response);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        verifyNoInteractions(roles, employee, store);
    }
}

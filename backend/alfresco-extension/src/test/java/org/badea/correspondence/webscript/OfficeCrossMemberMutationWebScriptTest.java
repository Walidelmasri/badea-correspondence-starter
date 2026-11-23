package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.OfficeCompletionService;
import org.badea.correspondence.application.OfficeCompletionState;
import org.badea.correspondence.application.OfficeCompletionStateConflictException;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.application.OfficeDispatchCommand;
import org.badea.correspondence.application.OfficeDistributionService;
import org.badea.correspondence.application.SaveOfficeCompletionCommand;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class OfficeCrossMemberMutationWebScriptTest {
    private static final String ID = "453ae576-a392-4150-ac69-c532f9487a23";
    private static final EmployeeDirectoryEntry ACTOR_Y = new EmployeeDirectoryEntry(
            new EmployeeId("20002"), new DirectoryUsername("office.y"),
            "Office Y", "مكتب الرئيس واي", Optional.empty());

    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final CorrespondenceActorRoleResolver roles = mock(CorrespondenceActorRoleResolver.class);
    private final OfficeCompletionService completion = mock(OfficeCompletionService.class);
    private final OfficeDistributionService distribution = mock(OfficeDistributionService.class);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final WebScriptResponse response = mock(WebScriptResponse.class);
    private final StringWriter output = new StringWriter();

    @BeforeEach
    void prepare() throws Exception {
        when(employees.getCurrentEmployee()).thenReturn(ACTOR_Y);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        Match match = mock(Match.class);
        when(request.getServiceMatch()).thenReturn(match);
        when(match.getTemplateVars()).thenReturn(Map.of("technicalId", ID));
        Content body = mock(Content.class);
        when(request.getContent()).thenReturn(body);
        when(body.getContent()).thenReturn("""
                {"expectedRevision":0,"expectedPresidentReviewRevision":3,
                 "state":{"schemaVersion":1}}
                """);
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
    }

    @Test
    void aDifferentAuthorizedOfficeMemberCanSaveAndIsRecordedAsTheActor() throws Exception {
        when(completion.save(any(), eq(CorrespondenceActorRole.PRESIDENT_OFFICE)))
                .thenReturn(new OfficeCompletionState(1, 3, "{\"schemaVersion\":1}",
                        "20002", "Office Y", "مكتب الرئيس واي", Instant.parse("2026-11-23T09:00:00Z")));
        try (MockedStatic<AuthenticationUtil> privilege = executeWithMockSystemAccount()) {
            new SaveOfficeCompletionWebScript(employees, roles, completion).execute(request, response);
            privilege.verify(() -> AuthenticationUtil.runAsSystem(any()), times(1));
        }
        var captured = ArgumentCaptor.forClass(SaveOfficeCompletionCommand.class);
        verify(completion).save(captured.capture(), eq(CorrespondenceActorRole.PRESIDENT_OFFICE));
        assertEquals("20002", captured.getValue().actorEmployeeId().value());
        assertEquals("Office Y", captured.getValue().actorNameEnglish());
        verify(response).setStatus(Status.STATUS_OK);
    }

    @Test
    void saveRejectsUnauthorizedRoleWithoutElevatingOrWriting() throws Exception {
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.DIRECTOR);
        try (MockedStatic<AuthenticationUtil> privilege = mockStatic(AuthenticationUtil.class)) {
            new SaveOfficeCompletionWebScript(employees, roles, completion).execute(request, response);
            privilege.verifyNoInteractions();
        }
        verifyNoInteractions(completion);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertTrue(output.toString().contains("CORRESPONDENCE_FORBIDDEN"));
    }

    @Test
    void aDifferentAuthorizedOfficeMemberCanDispatchWithTheirOwnAuditIdentity() throws Exception {
        Content body = request.getContent();
        when(body.getContent()).thenReturn("""
                {"expectedPresidentReviewRevision":3,"expectedOfficeRevision":1}
                """);
        when(distribution.distribute(any(), eq(CorrespondenceActorRole.PRESIDENT_OFFICE)))
                .thenReturn("dispatch-1");
        try (MockedStatic<AuthenticationUtil> privilege = executeWithMockSystemAccount()) {
            new OfficeDispatchWebScript(employees, roles, distribution).execute(request, response);
            privilege.verify(() -> AuthenticationUtil.runAsSystem(any()), times(1));
        }
        var captured = ArgumentCaptor.forClass(OfficeDispatchCommand.class);
        verify(distribution).distribute(captured.capture(), eq(CorrespondenceActorRole.PRESIDENT_OFFICE));
        assertEquals("20002", captured.getValue().actorEmployeeId().value());
        verify(response).setStatus(Status.STATUS_OK);
    }

    @Test
    void saveStillRejectsTheWrongWorkflowStateAfterRoleVerification() throws Exception {
        when(completion.save(any(), eq(CorrespondenceActorRole.PRESIDENT_OFFICE)))
                .thenThrow(new CorrespondenceAuthorizationException("Not returned by President"));
        try (MockedStatic<AuthenticationUtil> privilege = executeWithMockSystemAccount()) {
            new SaveOfficeCompletionWebScript(employees, roles, completion).execute(request, response);
        }
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
    }

    @Test
    void saveStillReportsStaleRevisionConflicts() throws Exception {
        when(completion.save(any(), eq(CorrespondenceActorRole.PRESIDENT_OFFICE)))
                .thenThrow(new OfficeCompletionStateConflictException("Stale Office completion"));
        try (MockedStatic<AuthenticationUtil> privilege = executeWithMockSystemAccount()) {
            new SaveOfficeCompletionWebScript(employees, roles, completion).execute(request, response);
        }
        verify(response).setStatus(409);
    }

    @Test
    void dispatchStillRejectsIncorrectWorkflowStateAfterRoleVerification() throws Exception {
        Content body = request.getContent();
        when(body.getContent()).thenReturn("""
                {"expectedPresidentReviewRevision":3,"expectedOfficeRevision":1}
                """);
        when(distribution.distribute(any(), eq(CorrespondenceActorRole.PRESIDENT_OFFICE)))
                .thenThrow(new CorrespondenceAuthorizationException("Not returned by President"));
        try (MockedStatic<AuthenticationUtil> privilege = executeWithMockSystemAccount()) {
            new OfficeDispatchWebScript(employees, roles, distribution).execute(request, response);
        }
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
    }

    @Test
    void dispatchRejectsUnauthorizedRoleWithoutElevatingOrWriting() throws Exception {
        Content body = request.getContent();
        when(body.getContent()).thenReturn("""
                {"expectedPresidentReviewRevision":3,"expectedOfficeRevision":1}
                """);
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.SECRETARY);
        try (MockedStatic<AuthenticationUtil> privilege = mockStatic(AuthenticationUtil.class)) {
            new OfficeDispatchWebScript(employees, roles, distribution).execute(request, response);
            privilege.verifyNoInteractions();
        }
        verifyNoInteractions(distribution);
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        assertTrue(output.toString().contains("CORRESPONDENCE_FORBIDDEN"));
    }

    private static MockedStatic<AuthenticationUtil> executeWithMockSystemAccount() {
        MockedStatic<AuthenticationUtil> scope = mockStatic(AuthenticationUtil.class);
        scope.when(() -> AuthenticationUtil.runAsSystem(any())).thenAnswer(invocation -> {
            AuthenticationUtil.RunAsWork<?> work = invocation.getArgument(0);
            return work.doWork();
        });
        return scope;
    }
}

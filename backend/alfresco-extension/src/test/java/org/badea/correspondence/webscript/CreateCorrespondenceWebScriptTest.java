package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.application.CurrentEmployeeService;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;

import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceRepository;

import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class CreateCorrespondenceWebScriptTest {

    private CurrentEmployeeService currentEmployeeService;

    private CorrespondenceActorRoleResolver actorRoleResolver;

    private AlfrescoCorrespondenceRepository repository;

    private CreateCorrespondenceWebScript webScript;

    @BeforeEach
    void setUp() {

        currentEmployeeService =
                mock(CurrentEmployeeService.class);

        actorRoleResolver =
                mock(CorrespondenceActorRoleResolver.class);

        repository =
                mock(AlfrescoCorrespondenceRepository.class);

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(CorrespondenceActorRole.DIRECTOR);

        webScript =
                new CreateCorrespondenceWebScript(
                        currentEmployeeService,
                        actorRoleResolver,
                        repository);
    }

    @Test
    void createsDraftUsingAuthenticatedEmployeeDepartment()
            throws Exception {

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employeeWithDepartment());

        when(repository.createDraft(
                any(CreateCorrespondenceCommand.class)))
                .thenReturn(
                        "550e8400-e29b-41d4-a716-446655440000");

        TestResponse response =
                execute("""
                        {
                          "subject": "Quarterly Strategy Review",
                          "bodyText": "Please review.",
                          "documentNumber": "DOC-2026-001",
                          "documentDate": "2026-10-08",
                          "sourceText": "Strategy Department"
                        }
                        """);

        assertEquals(
                Status.STATUS_CREATED,
                response.status());

        JSONObject result =
                new JSONObject(response.body());

        assertEquals(
                "550e8400-e29b-41d4-a716-446655440000",
                result.getString("technicalId"));

        assertEquals(
                "DRAFT",
                result.getString("state"));

        ArgumentCaptor<CreateCorrespondenceCommand> captor =
                ArgumentCaptor.forClass(
                        CreateCorrespondenceCommand.class);

        verify(repository).createDraft(
                captor.capture());

        CreateCorrespondenceCommand command =
                captor.getValue();

        assertEquals(
                new DepartmentCode("111"),
                command.originDepartmentCode());

        assertEquals(
                new EmployeeId("18280"),
                command.createdByEmployeeId());

        assertEquals(
                "Quarterly Strategy Review",
                command.subject());
    }

    @Test
    void rejectsEmployeeWithoutDepartment()
            throws Exception {

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employeeWithoutDepartment());

        TestResponse response =
                execute("""
                        {
                          "subject": "Test correspondence"
                        }
                        """);

        assertEquals(
                Status.STATUS_CONFLICT,
                response.status());

        verify(repository, never())
                .createDraft(
                        any(CreateCorrespondenceCommand.class));
    }

    @Test
    void rejectsMissingSubject()
            throws Exception {

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employeeWithDepartment());

        TestResponse response =
                execute("""
                        {
                          "bodyText": "No subject"
                        }
                        """);

        assertEquals(
                Status.STATUS_BAD_REQUEST,
                response.status());

        verify(repository, never())
                .createDraft(
                        any(CreateCorrespondenceCommand.class));
    }

    @Test
    void rejectsPresidentOfficeCreatingDirectorDraft()
            throws Exception {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(
                        CorrespondenceActorRole.PRESIDENT_OFFICE);

        TestResponse response =
                execute("""
                        {
                          "subject": "Unauthorized draft"
                        }
                        """);

        assertForbidden(response);
    }

    @Test
    void rejectsPresidentUsingDirectorDraftWorkflow()
            throws Exception {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(
                        CorrespondenceActorRole.PRESIDENT);

        TestResponse response =
                execute("""
                        {
                          "subject": "Unauthorized draft"
                        }
                        """);

        assertForbidden(response);
    }

    @Test
    void rejectsUserWithoutCorrespondenceRole()
            throws Exception {

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "No correspondence role."));

        TestResponse response =
                execute("""
                        {
                          "subject": "Unauthorized draft"
                        }
                        """);

        assertForbidden(response);
    }

    private void assertForbidden(
            TestResponse response) {

        assertEquals(
                Status.STATUS_FORBIDDEN,
                response.status());

        JSONObject json =
                new JSONObject(response.body());

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                json.getString("code"));

        verify(repository, never())
                .createDraft(
                        any(CreateCorrespondenceCommand.class));
    }

    private static EmployeeDirectoryEntry employeeWithDepartment() {

        DepartmentDirectoryEntry department =
                new DepartmentDirectoryEntry(
                        new DepartmentCode("111"),
                        "Strategy Department",
                        "إدارة الاستراتيجية");

        return new EmployeeDirectoryEntry(
                new EmployeeId("18280"),
                new DirectoryUsername("almunder.salih"),
                "Almunder Salih Melod Sahboun",
                "المنذر صالح ميلود سحبون",
                Optional.of(department));
    }

    private static EmployeeDirectoryEntry employeeWithoutDepartment() {

        return new EmployeeDirectoryEntry(
                new EmployeeId("18280"),
                new DirectoryUsername("almunder.salih"),
                "Almunder Salih Melod Sahboun",
                "المنذر صالح ميلود سحبون",
                Optional.empty());
    }

    private TestResponse execute(String body)
            throws Exception {

        WebScriptRequest request =
                mock(WebScriptRequest.class);

        WebScriptResponse response =
                mock(WebScriptResponse.class);

        Content content =
                mock(Content.class);

        when(request.getContent())
                .thenReturn(content);

        when(content.getContent())
                .thenReturn(body);

        StringWriter output =
                new StringWriter();

        when(response.getWriter())
                .thenReturn(
                        new PrintWriter(output));

        webScript.execute(
                request,
                response);

        ArgumentCaptor<Integer> statusCaptor =
                ArgumentCaptor.forClass(Integer.class);

        verify(response).setStatus(
                statusCaptor.capture());

        return new TestResponse(
                statusCaptor.getValue(),
                output.toString());
    }

    private record TestResponse(
            int status,
            String body) {
    }
}
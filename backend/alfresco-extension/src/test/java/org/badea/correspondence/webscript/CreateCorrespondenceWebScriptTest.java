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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class CreateCorrespondenceWebScriptTest {

    private CurrentEmployeeService currentEmployeeService;
    private AlfrescoCorrespondenceRepository repository;

    private CreateCorrespondenceWebScript webScript;

    @BeforeEach
    void setUp() {

        currentEmployeeService =
                mock(CurrentEmployeeService.class);

        repository =
                mock(AlfrescoCorrespondenceRepository.class);

        webScript =
                new CreateCorrespondenceWebScript(
                        currentEmployeeService,
                        repository);
    }

    @Test
    void createsDraftUsingAuthenticatedEmployeeDepartment()
            throws Exception {

        DepartmentDirectoryEntry department =
                new DepartmentDirectoryEntry(
                        new DepartmentCode("111"),
                        "Strategy Department",
                        "إدارة الاستراتيجية");

        EmployeeDirectoryEntry employee =
                new EmployeeDirectoryEntry(
                        new EmployeeId("18280"),
                        new DirectoryUsername("almunder.salih"),
                        "Almunder Salih Melod Sahboun",
                        "المنذر صالح ميلود سحبون",
                        Optional.of(department));

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee);

        when(repository.createDraft(
                any(CreateCorrespondenceCommand.class)))
                .thenReturn(
                        "550e8400-e29b-41d4-a716-446655440000");

        TestResponse testResponse =
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
                testResponse.status);

        verify(repository)
                .createDraft(
                        any(CreateCorrespondenceCommand.class));
    }

    @Test
    void rejectsEmployeeWithoutDepartment()
            throws Exception {

        EmployeeDirectoryEntry employee =
                new EmployeeDirectoryEntry(
                        new EmployeeId("18280"),
                        new DirectoryUsername("almunder.salih"),
                        "Almunder Salih Melod Sahboun",
                        "المنذر صالح ميلود سحبون",
                        Optional.empty());

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee);

        TestResponse testResponse =
                execute("""
                        {
                          "subject": "Test correspondence"
                        }
                        """);

        assertEquals(
                Status.STATUS_CONFLICT,
                testResponse.status);

        verify(repository, never())
                .createDraft(
                        any(CreateCorrespondenceCommand.class));
    }

    @Test
    void rejectsMissingSubject()
            throws Exception {

        DepartmentDirectoryEntry department =
                new DepartmentDirectoryEntry(
                        new DepartmentCode("111"),
                        "Strategy Department",
                        "إدارة الاستراتيجية");

        EmployeeDirectoryEntry employee =
                new EmployeeDirectoryEntry(
                        new EmployeeId("18280"),
                        new DirectoryUsername("almunder.salih"),
                        "Almunder Salih Melod Sahboun",
                        "المنذر صالح ميلود سحبون",
                        Optional.of(department));

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(employee);

        TestResponse testResponse =
                execute("""
                        {
                          "bodyText": "No subject"
                        }
                        """);

        assertEquals(
                Status.STATUS_BAD_REQUEST,
                testResponse.status);

        verify(repository, never())
                .createDraft(
                        any(CreateCorrespondenceCommand.class));
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

        var statusCaptor =
                org.mockito.ArgumentCaptor
                        .forClass(Integer.class);

        verify(response)
                .setStatus(
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
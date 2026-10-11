package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;

import org.badea.correspondence.application.CorrespondenceTransitionService;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class TransitionCorrespondenceWebScriptTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private CurrentEmployeeService currentEmployeeService;
    private CorrespondenceActorRoleResolver actorRoleResolver;
    private CorrespondenceTransitionService transitionService;

    private WebScriptRequest request;
    private WebScriptResponse response;

    private StringWriter responseBody;
    private PrintWriter responseWriter;

    private TransitionCorrespondenceWebScript webScript;

    @BeforeEach
    void setUp()
            throws IOException {

        currentEmployeeService =
                mock(
                        CurrentEmployeeService.class);

        actorRoleResolver =
                mock(
                        CorrespondenceActorRoleResolver.class);

        transitionService =
                mock(
                        CorrespondenceTransitionService.class);

        request =
                mock(
                        WebScriptRequest.class);

        response =
                mock(
                        WebScriptResponse.class);

        responseBody =
                new StringWriter();

        responseWriter =
                new PrintWriter(
                        responseBody);

        when(response.getWriter())
                .thenReturn(
                        responseWriter);

        webScript =
                new TransitionCorrespondenceWebScript(
                        currentEmployeeService,
                        actorRoleResolver,
                        transitionService);
    }

    @Test
    void derivesActorIdentityRoleAndDepartmentServerSide()
            throws IOException {

        configureRequest(
                """
                {
                  "action": "SEND_TO_OFFICE",
                  "actorRole": "PRESIDENT",
                  "actorEmployeeId": "99999",
                  "actorDepartmentCode": "101"
                }
                """);

        EmployeeDirectoryEntry employee =
                employee(
                        "117");

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenReturn(
                        CorrespondenceActorRole.DIRECTOR);

        when(currentEmployeeService.getCurrentEmployee())
                .thenReturn(
                        employee);

        when(transitionService.transition(
                any(TransitionCorrespondenceCommand.class)))
                .thenReturn(
                        CorrespondenceState.WITH_PRESIDENT_OFFICE);

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        ArgumentCaptor<TransitionCorrespondenceCommand>
                commandCaptor =
                ArgumentCaptor.forClass(
                        TransitionCorrespondenceCommand.class);

        verify(transitionService)
                .transition(
                        commandCaptor.capture());

        TransitionCorrespondenceCommand command =
                commandCaptor.getValue();

        assertEquals(
                TECHNICAL_ID,
                command.technicalId());

        assertEquals(
                CorrespondenceActorRole.DIRECTOR,
                command.actorRole());

        assertEquals(
                CorrespondenceAction.SEND_TO_OFFICE,
                command.action());

        assertEquals(
                "18280",
                command.actorEmployeeId().value());

        assertEquals(
                new DepartmentCode("117"),
                command.actorDepartmentCode());

        assertEquals(
                "Test Employee",
                command.actorNameEnglish());

        assertEquals(
                "موظف اختبار",
                command.actorNameArabic());

        assertNull(
                command.targetDepartmentCode());

        /*
         * The request deliberately attempted to claim PRESIDENT,
         * employee 99999 and department 101.
         *
         * None of those client-supplied identity values are used.
         * The command contains only server-resolved values.
         */

        verify(response)
                .setStatus(
                        Status.STATUS_OK);

        verify(response)
                .setContentType(
                        "application/json;charset=UTF-8");

        JSONObject result =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                TECHNICAL_ID,
                result.getString(
                        "technicalId"));

        assertEquals(
                CorrespondenceState.WITH_PRESIDENT_OFFICE.name(),
                result.getString(
                        "state"));
    }

    @Test
    void returnsForbiddenWhenUserHasNoCorrespondenceRole()
            throws IOException {

        configureRequest(
                """
                {
                  "action": "SEND_TO_OFFICE"
                }
                """);

        when(actorRoleResolver.resolveCurrentUserRole())
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "No correspondence role."));

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        Status.STATUS_FORBIDDEN);

        verify(
                currentEmployeeService,
                never())
                .getCurrentEmployee();

        verify(
                transitionService,
                never())
                .transition(
                        any(TransitionCorrespondenceCommand.class));

        JSONObject error =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                "CORRESPONDENCE_FORBIDDEN",
                error.getString(
                        "code"));
    }

    @Test
    void rejectsRequestWithoutActionBeforeResolvingIdentity()
            throws IOException {

        configureRequest(
                """
                {}
                """);

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        Status.STATUS_BAD_REQUEST);

        verify(
                actorRoleResolver,
                never())
                .resolveCurrentUserRole();

        verify(
                currentEmployeeService,
                never())
                .getCurrentEmployee();

        verify(
                transitionService,
                never())
                .transition(
                        any(TransitionCorrespondenceCommand.class));

        JSONObject error =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                "INVALID_REQUEST",
                error.getString(
                        "code"));
    }

    private void configureRequest(
            String json)
            throws IOException {

        Match match =
                mock(
                        Match.class);

        Content content =
                mock(
                        Content.class);

        when(request.getServiceMatch())
                .thenReturn(
                        match);

        when(match.getTemplateVars())
                .thenReturn(
                        Map.of(
                                "technicalId",
                                TECHNICAL_ID));

        when(request.getContent())
                .thenReturn(
                        content);

        when(content.getContent())
                .thenReturn(
                        json);
    }

    private static EmployeeDirectoryEntry employee(
            String departmentCode) {

        EmployeeDirectoryEntry employee =
                mock(
                        EmployeeDirectoryEntry.class);

        DepartmentDirectoryEntry department =
                mock(
                        DepartmentDirectoryEntry.class);

        when(employee.employeeId())
                .thenReturn(
                        new EmployeeId(
                                "18280"));

        when(employee.nameEnglish())
                .thenReturn(
                        "Test Employee");

        when(employee.nameArabic())
                .thenReturn(
                        "موظف اختبار");

        when(employee.department())
                .thenReturn(
                        Optional.of(
                                department));

        when(department.code())
                .thenReturn(
                        new DepartmentCode(
                                departmentCode));

        return employee;
    }
}
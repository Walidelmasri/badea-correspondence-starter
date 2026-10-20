package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceHistoryReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.webscripts.Match;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class GetCorrespondenceHistoryWebScriptTest {
    private static final String ID="11111111-1111-1111-1111-111111111111";
    private final CurrentEmployeeService employees=mock(CurrentEmployeeService.class);
    private final CorrespondenceActorRoleResolver roles=mock(CorrespondenceActorRoleResolver.class);
    private final CorrespondenceDetailsService details=mock(CorrespondenceDetailsService.class);
    private final AlfrescoCorrespondenceHistoryReader reader=mock(AlfrescoCorrespondenceHistoryReader.class);
    private final WebScriptRequest request=mock(WebScriptRequest.class);
    private final WebScriptResponse response=mock(WebScriptResponse.class);
    private final StringWriter output=new StringWriter();
    private final PrintWriter writer=new PrintWriter(output);
    private final GetCorrespondenceHistoryWebScript endpoint=
        new GetCorrespondenceHistoryWebScript(employees,roles,details,reader);

    @BeforeEach
    void setUp() throws Exception {
        Match match=mock(Match.class);
        when(request.getServiceMatch()).thenReturn(match);
        when(match.getTemplateVars()).thenReturn(Map.of("technicalId",ID));
        when(response.getWriter()).thenReturn(writer);
        when(roles.resolveCurrentUserRole()).thenReturn(CorrespondenceActorRole.PRESIDENT_OFFICE);
        when(employees.getCurrentEmployee()).thenReturn(new EmployeeDirectoryEntry(
            new EmployeeId("10001"),new DirectoryUsername("office.user"),
            "Office User",null,Optional.of(new DepartmentDirectoryEntry(
                new DepartmentCode("117"),"Department",null))));
    }

    @Test
    void returnsImmutableHistoryForAuthorizedReader() throws Exception {
        var action=new AlfrescoCorrespondenceHistoryReader.ActionEntry(
            1,"SEND_TO_OFFICE","DIRECTOR","Director",null,
            "DRAFT","WITH_PRESIDENT_OFFICE",Instant.parse("2026-10-18T10:00:00Z"),null);
        when(reader.list(ID)).thenReturn(List.of(action));
        endpoint.execute(request,response);
        writer.flush();
        verify(details).getDetails(ID,CorrespondenceActorRole.PRESIDENT_OFFICE,
            new DepartmentCode("117"), "10001");
        verify(response).setStatus(Status.STATUS_OK);
        JSONObject result=new JSONObject(output.toString());
        assertEquals(1,result.getInt("total"));
        assertEquals("DIRECTOR",result.getJSONArray("items").getJSONObject(0).getString("actorRole"));
    }

    @Test
    void neverReadsHistoryAfterAuthorizationFailure() throws Exception {
        when(details.getDetails(ID,CorrespondenceActorRole.PRESIDENT_OFFICE,
            new DepartmentCode("117"), "10001"))
            .thenThrow(new CorrespondenceAuthorizationException("Access denied"));
        endpoint.execute(request,response);
        writer.flush();
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        verifyNoInteractions(reader);
    }

    @Test
    void neverReadsHistoryIfRoleResolutionFails() throws Exception {
        when(roles.resolveCurrentUserRole())
            .thenThrow(new CorrespondenceAuthorizationException("Invalid role"));
        endpoint.execute(request,response);
        writer.flush();
        verify(response).setStatus(Status.STATUS_FORBIDDEN);
        verifyNoInteractions(reader);
    }
}

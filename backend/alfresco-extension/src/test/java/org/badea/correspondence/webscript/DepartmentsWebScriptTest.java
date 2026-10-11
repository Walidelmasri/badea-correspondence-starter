package org.badea.correspondence.webscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.badea.correspondence.application.CorrespondenceDepartmentService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DepartmentDirectoryException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

class DepartmentsWebScriptTest {

    private CorrespondenceDepartmentService departmentService;

    private WebScriptRequest request;
    private WebScriptResponse response;

    private StringWriter responseBody;
    private PrintWriter responseWriter;

    private DepartmentsWebScript webScript;

    @BeforeEach
    void setUp()
            throws IOException {

        departmentService =
                mock(
                        CorrespondenceDepartmentService.class);

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
                new DepartmentsWebScript(
                        departmentService);
    }

    @Test
    void returnsCorrespondenceDepartments()
            throws IOException {

        when(departmentService.listDepartments())
                .thenReturn(
                        List.of(
                                new DepartmentDirectoryEntry(
                                        new DepartmentCode("101"),
                                        "Office of the President",
                                        "مكتب الرئيس"),
                                new DepartmentDirectoryEntry(
                                        new DepartmentCode("117"),
                                        "Digital Transformation Department",
                                        "إدارة التحول الرقمي")));

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        Status.STATUS_OK);

        verify(response)
                .setContentType(
                        "application/json;charset=UTF-8");

        JSONArray result =
                new JSONArray(
                        responseBody.toString());

        assertEquals(
                2,
                result.length());

        JSONObject first =
                result.getJSONObject(
                        0);

        assertEquals(
                "101",
                first.getString(
                        "code"));

        assertEquals(
                "Office of the President",
                first.getString(
                        "nameEnglish"));

        assertEquals(
                "مكتب الرئيس",
                first.getString(
                        "nameArabic"));

        JSONObject second =
                result.getJSONObject(
                        1);

        assertEquals(
                "117",
                second.getString(
                        "code"));

        assertEquals(
                "Digital Transformation Department",
                second.getString(
                        "nameEnglish"));

        assertEquals(
                "إدارة التحول الرقمي",
                second.getString(
                        "nameArabic"));

        verify(departmentService)
                .listDepartments();
    }

    @Test
    void returnsServiceUnavailableWhenDepartmentDirectoryFails()
            throws IOException {

        when(departmentService.listDepartments())
                .thenThrow(
                        new DepartmentDirectoryException(
                                "Oracle unavailable"));

        webScript.execute(
                request,
                response);

        responseWriter.flush();

        verify(response)
                .setStatus(
                        Status.STATUS_SERVICE_UNAVAILABLE);

        verify(response)
                .setContentType(
                        "application/json;charset=UTF-8");

        JSONObject error =
                new JSONObject(
                        responseBody.toString());

        assertEquals(
                "DEPARTMENT_DIRECTORY_UNAVAILABLE",
                error.getString(
                        "code"));

        assertEquals(
                "The department directory is currently unavailable.",
                error.getString(
                        "message"));
    }
}
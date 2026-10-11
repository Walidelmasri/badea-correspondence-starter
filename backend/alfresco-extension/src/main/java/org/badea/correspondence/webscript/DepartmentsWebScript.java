package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.badea.correspondence.application.CorrespondenceDepartmentService;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DepartmentDirectoryException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

public final class DepartmentsWebScript
        extends AbstractWebScript {

    private final CorrespondenceDepartmentService departmentService;

    public DepartmentsWebScript(
            CorrespondenceDepartmentService departmentService) {

        this.departmentService =
                Objects.requireNonNull(
                        departmentService,
                        "departmentService must not be null");
    }

    @Override
    public void execute(
            WebScriptRequest request,
            WebScriptResponse response)
            throws IOException {

        try {
            List<DepartmentDirectoryEntry> departments =
                    departmentService.listDepartments();

            JSONArray result =
                    new JSONArray();

            for (DepartmentDirectoryEntry department : departments) {

                JSONObject item =
                        new JSONObject();

                item.put(
                        "code",
                        department.code().value());

                item.put(
                        "nameEnglish",
                        department.nameEnglish());

                if (department.nameArabic() != null) {
                    item.put(
                            "nameArabic",
                            department.nameArabic());
                } else {
                    item.put(
                            "nameArabic",
                            JSONObject.NULL);
                }

                result.put(
                        item);
            }

            response.setStatus(
                    Status.STATUS_OK);

            response.setContentType(
                    "application/json;charset=UTF-8");

            response.getWriter()
                    .write(
                            result.toString());

        } catch (DepartmentDirectoryException exception) {

            JSONObject error =
                    new JSONObject();

            error.put(
                    "code",
                    "DEPARTMENT_DIRECTORY_UNAVAILABLE");

            error.put(
                    "message",
                    "The department directory is currently unavailable.");

            response.setStatus(
                    Status.STATUS_SERVICE_UNAVAILABLE);

            response.setContentType(
                    "application/json;charset=UTF-8");

            response.getWriter()
                    .write(
                            error.toString());
        }
    }
}
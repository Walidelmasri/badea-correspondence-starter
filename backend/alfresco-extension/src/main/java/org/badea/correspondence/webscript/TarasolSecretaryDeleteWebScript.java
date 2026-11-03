package org.badea.correspondence.webscript;

import java.io.IOException;
import java.util.Objects;
import org.badea.correspondence.application.CorrespondenceDocumentNotFoundException;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.organization.TarasolSecretaryPdfDeletionService;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/** Isolated authenticated DELETE; never routes through the generic Director deleter. */
public final class TarasolSecretaryDeleteWebScript extends AbstractWebScript {
    private final CurrentEmployeeService employees;
    private final TarasolSecretaryPdfDeletionService deletion;

    public TarasolSecretaryDeleteWebScript(CurrentEmployeeService employees,
            TarasolSecretaryPdfDeletionService deletion) {
        this.employees = Objects.requireNonNull(employees);
        this.deletion = Objects.requireNonNull(deletion);
    }

    @Override public void execute(WebScriptRequest request, WebScriptResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String unitText = request.getParameter("unitCode");
            if (unitText == null || !unitText.matches("[0-9]{3}")) {
                throw new IllegalArgumentException("Valid unitCode required");
            }
            var vars = request.getServiceMatch().getTemplateVars();
            deletion.delete(employees.getCurrentEmployee(), new DepartmentCode(unitText),
                    vars.get("technicalId"), vars.get("nodeId"));
            response.setStatus(204);
        } catch (CorrespondenceAuthorizationException denied) {
            error(response, Status.STATUS_FORBIDDEN, "ACCESS_DENIED");
        } catch (CorrespondenceDocumentNotFoundException missing) {
            error(response, Status.STATUS_NOT_FOUND, "DOCUMENT_NOT_FOUND");
        } catch (IllegalArgumentException invalid) {
            error(response, Status.STATUS_BAD_REQUEST, "INVALID_REQUEST");
        }
        // Let persistence failures escape the Alfresco write transaction for rollback.
    }

    private static void error(WebScriptResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new JSONObject().put("code", code).toString());
    }
}

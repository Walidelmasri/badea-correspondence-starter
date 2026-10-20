package org.badea.correspondence.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.badea.correspondence.directory.DepartmentCode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Pure snapshot merger. President-authored fields are immutable inputs. */
public final class OfficeDistributionPlanner {
    private final CorrespondenceDepartmentService departments;

    public OfficeDistributionPlanner(CorrespondenceDepartmentService departments) {
        this.departments = Objects.requireNonNull(departments);
    }

    public OfficeDistributionPlan plan(PresidentReviewState president, OfficeCompletionState office) {
        Objects.requireNonNull(president);
        Objects.requireNonNull(office);
        if (office.presidentReviewRevision() != president.revision()) {
            throw new IllegalArgumentException("Office completion references an outdated presidential review");
        }
        try {
            JSONObject p = new JSONObject(president.json());
            JSONObject o = new JSONObject(OfficeCompletionJsonValidator.validate(office.json()));
            if (p.getInt("schemaVersion") != 1) {
                throw new IllegalArgumentException("Unsupported President review schema");
            }
            DistributionRecipients recipients = DistributionRecipients.merge(
                    codes(p.getJSONArray("selectedRecipientIds")),
                    codes(p.getJSONArray("selectedCopyRecipientIds")),
                    codes(o.getJSONArray("selectedRecipientIds")),
                    codes(o.getJSONArray("selectedCopyRecipientIds")));
            for (DepartmentCode department : recipients.to()) departments.requireDepartment(department);
            for (DepartmentCode department : recipients.copy()) departments.requireDepartment(department);
            LinkedHashSet<String> actions = new LinkedHashSet<>();
            actions.addAll(strings(p.getJSONArray("selectedActionCodes")));
            actions.addAll(strings(o.getJSONArray("selectedActionCodes")));
            return new OfficeDistributionPlan(
                    president.revision(), office.revision(), recipients, new ArrayList<>(actions),
                    p.optString("typedExplanation", ""), o.getString("typedExplanation"),
                    o.getString("officeNotes"),o.getString("otherRecipient"),o.getString("otherAction"),
                    new PresidentInkReviewParser().parse(president.json()).explanationPages());
        } catch (JSONException invalid) {
            throw new IllegalArgumentException("Invalid Office/President distribution snapshots", invalid);
        }
    }

    private static List<DepartmentCode> codes(JSONArray array) {
        List<DepartmentCode> result = new ArrayList<>();
        if (array.length() > 100) throw new IllegalArgumentException("Too many departments");
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.get(i);
            if (!(raw instanceof String text)) throw new IllegalArgumentException("Invalid department code");
            result.add(new DepartmentCode(text));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array.length() > 100) throw new IllegalArgumentException("Too many action codes");
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.get(i);
            if (!(raw instanceof String text) || text.isBlank() || text.length() > 100) {
                throw new IllegalArgumentException("Invalid action code");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }
}

package org.badea.correspondence.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

/** Atomic Office distribution of one correspondence to multiple independent departments. */
public final class OfficeDistributionService {
    private final CorrespondenceTransitionRepository correspondence;
    private final PresidentReturnEvidence history;
    private final PresidentReviewStateRepository president;
    private final OfficeCompletionRepository office;
    private final CorrespondenceDetailsRepository details;
    private final CorrespondenceDepartmentService departments;
    private final OfficeDistributionPlanner planner;
    private final OfficeActionSheetRenderer renderer;
    private final OfficeDistributionRepository persistence;
    private final Clock clock;

    public OfficeDistributionService(CorrespondenceTransitionRepository correspondence,
            PresidentReturnEvidence history, PresidentReviewStateRepository president,
            OfficeCompletionRepository office, CorrespondenceDetailsRepository details,
            CorrespondenceDepartmentService departments, OfficeDistributionPlanner planner,
            OfficeActionSheetRenderer renderer, OfficeDistributionRepository persistence, Clock clock) {
        this.correspondence = Objects.requireNonNull(correspondence);
        this.history = Objects.requireNonNull(history);
        this.president = Objects.requireNonNull(president);
        this.office = Objects.requireNonNull(office);
        this.details = Objects.requireNonNull(details);
        this.departments = Objects.requireNonNull(departments);
        this.planner = Objects.requireNonNull(planner);
        this.renderer = Objects.requireNonNull(renderer);
        this.persistence = Objects.requireNonNull(persistence);
        this.clock = Objects.requireNonNull(clock);
    }

    public String distribute(OfficeDispatchCommand command, CorrespondenceActorRole role) {
        Objects.requireNonNull(command);
        if (role != CorrespondenceActorRole.PRESIDENT_OFFICE) throw forbidden();
        // A retry after a lost HTTP response is safe only for the identical frozen revisions.
        var already = persistence.dispatchId(command.technicalId(),
                command.expectedPresidentRevision(), command.expectedOfficeRevision());
        if (already.isPresent()) return already.get();
        CorrespondenceAccessContext context = correspondence.getAccessContext(command.technicalId());
        if (context.state() != CorrespondenceState.WITH_PRESIDENT_OFFICE
                || context.assignedRole() != CorrespondenceActorRole.PRESIDENT_OFFICE
                || !history.latestActionIsPresidentReturn(command.technicalId())) throw forbidden();
        PresidentReviewState review = president.find(command.technicalId())
                .orElseThrow(() -> new IllegalArgumentException("Presidential review missing"));
        OfficeCompletionState completion = office.find(command.technicalId())
                .orElseThrow(() -> new IllegalArgumentException("Office completion not saved"));
        if (review.revision() != command.expectedPresidentRevision()
                || completion.revision() != command.expectedOfficeRevision()) {
            throw new OfficeCompletionStateConflictException("Dispatch source revisions changed; reload Office Completion");
        }
        OfficeDistributionPlan plan = planner.plan(review, completion);
        var to = new ArrayList<DepartmentDirectoryEntry>();
        var copy = new ArrayList<DepartmentDirectoryEntry>();
        for (DepartmentCode code : plan.recipients().to()) to.add(departments.requireDepartment(code));
        for (DepartmentCode code : plan.recipients().copy()) copy.add(departments.requireDepartment(code));
        CorrespondenceDetails source = details.findByTechnicalId(command.technicalId());
        byte[] pdf = renderer.render(source, plan, to, copy);
        if (pdf == null || pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P'
                || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-') {
            throw new IllegalStateException("Official action sheet renderer produced invalid PDF");
        }
        Instant now = clock.instant();
        var assignments = new ArrayList<DepartmentAssignment>();
        for (var entry : to) assignments.add(newAssignment(command, entry.code(), DepartmentRecipientType.TO, now));
        for (var entry : copy) assignments.add(newAssignment(command, entry.code(), DepartmentRecipientType.COPY, now));
        // Repository persists sheet, assignments, and workflow history in one REQUIRED Alfresco transaction.
        // Any runtime failure must escape the web script to force rollback (including PDF bytes).
        return persistence.distribute(command, plan, pdf, List.copyOf(assignments));
    }

    private DepartmentAssignment newAssignment(OfficeDispatchCommand command,
            DepartmentCode code, DepartmentRecipientType type, Instant now) {
        String key = command.technicalId() + ":" + command.expectedPresidentRevision()
                + ":" + command.expectedOfficeRevision() + ":" + code.value();
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        return new DepartmentAssignment(id, command.technicalId(), code, type,
                DepartmentAssignmentStatus.PENDING, command.actorEmployeeId().value(),
                now, null, null, null);
    }

    private static CorrespondenceAuthorizationException forbidden() {
        return new CorrespondenceAuthorizationException("Office dispatch is not permitted at this stage");
    }
}

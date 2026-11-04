package org.badea.correspondence.organization;

import java.util.List;
import java.util.Objects;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CorrespondenceDocumentContentService;
import org.badea.correspondence.application.CorrespondenceDocumentStream;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceHistoryReader;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Read the existing correspondence detail, history and PDF representations.
 * A secretary may access only cases in their currently authorized unit Inbox.
 */
public final class TarasolSecretaryInboxReadService {
    private final TarasolSecretarySharedInboxService inbox;
    private final CorrespondenceDetailsService details;
    private final CorrespondenceDocumentContentService documents;
    private final AlfrescoCorrespondenceHistoryReader history;

    public TarasolSecretaryInboxReadService(TarasolSecretarySharedInboxService inbox,
            CorrespondenceDetailsService details,
            CorrespondenceDocumentContentService documents,
            AlfrescoCorrespondenceHistoryReader history) {
        this.inbox = Objects.requireNonNull(inbox);
        this.details = Objects.requireNonNull(details);
        this.documents = Objects.requireNonNull(documents);
        this.history = Objects.requireNonNull(history);
    }

    public CorrespondenceDetails details(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId) {
        return inbox.readCase(actor, unit, technicalId,
                () -> details.getDetails(technicalId, CorrespondenceActorRole.DIRECTOR,
                        unit, actor.employeeId().value()));
    }

    public List<AlfrescoCorrespondenceHistoryReader.ActionEntry> history(
            EmployeeDirectoryEntry actor, DepartmentCode unit, String technicalId) {
        return inbox.readCase(actor, unit, technicalId, () -> history.list(technicalId));
    }

    public CorrespondenceDocumentStream document(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId, String nodeId) {
        return inbox.readCase(actor, unit, technicalId,
                () -> documents.open(technicalId, nodeId,
                        CorrespondenceActorRole.DIRECTOR, unit, actor.employeeId().value()));
    }
}

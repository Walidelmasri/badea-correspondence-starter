package org.badea.correspondence.organization;

import java.util.Objects;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoSecretaryRoundTwoRequestRepository;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;

/** Requests only; manager approval must use a distinct authorized endpoint. */
public final class TarasolSecretaryRoundTwoRequestService {
    private final TarasolEffectiveAccessReader access;
    private final AuthorityService authorities;
    private final AuthenticatedUserProvider username;
    private final TarasolSecretarySharedInboxService inbox;
    private final AlfrescoSecretaryRoundTwoRequestRepository requests;

    public TarasolSecretaryRoundTwoRequestService(TarasolEffectiveAccessReader access,
            AuthorityService authorities, AuthenticatedUserProvider username,
            TarasolSecretarySharedInboxService inbox,
            AlfrescoSecretaryRoundTwoRequestRepository requests) {
        this.access = Objects.requireNonNull(access);
        this.authorities = Objects.requireNonNull(authorities);
        this.username = Objects.requireNonNull(username);
        this.inbox = Objects.requireNonNull(inbox);
        this.requests = Objects.requireNonNull(requests);
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Request submit(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId, String reason) {
        authorize(actor, unit);
        if (reason == null || reason.isBlank() || reason.trim().length() > 2000)
            throw new IllegalArgumentException("Follow-up reason must contain 1-2000 characters");
        return inbox.readCase(actor, unit, technicalId, () ->
            requests.submit(technicalId, unit, actor.employeeId().value(), reason.trim()));
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Request current(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId) {
        authorize(actor, unit);
        return inbox.readCase(actor, unit, technicalId, () ->
            requests.current(technicalId, unit, actor.employeeId().value()));
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Request revise(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId, String requestId,
            long returnVersion, String reason) {
        authorize(actor, unit);
        return inbox.readCase(actor, unit, technicalId, () ->
            requests.revise(technicalId, unit, actor.employeeId().value(),
                requestId, returnVersion, reason));
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Request submitPrepared(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId, String requestId) {
        authorize(actor, unit);
        return inbox.readCase(actor, unit, technicalId, () ->
            requests.submitPrepared(technicalId, unit, actor.employeeId().value(), requestId));
    }

    public AlfrescoSecretaryRoundTwoRequestRepository.Request resubmit(EmployeeDirectoryEntry actor,
            DepartmentCode unit, String technicalId, String requestId, long returnVersion) {
        authorize(actor, unit);
        return inbox.readCase(actor, unit, technicalId, () ->
            requests.resubmit(technicalId, unit, actor.employeeId().value(),
                requestId, returnVersion));
    }

    private void authorize(EmployeeDirectoryEntry actor, DepartmentCode unit) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(unit);
        if (!TarasolSecretaryGroupPolicy.isMember(authorities, username)
                || !access.hasActiveSecretaryAssignment(actor.employeeId(), unit))
            throw new CorrespondenceAuthorizationException("Secretary follow-up access denied");
    }
}

package org.badea.correspondence.organization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import org.badea.correspondence.application.CreateCorrespondenceCommand;
import org.badea.correspondence.application.TransitionCorrespondenceCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.alfresco.service.cmr.security.AuthorityService;
import org.badea.correspondence.security.AuthenticatedUserProvider;
import org.badea.correspondence.infrastructure.alfresco.AlfrescoCorrespondenceRepository;
import org.badea.correspondence.infrastructure.oracle.TarasolOrganizationReader;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TarasolSecretaryWorkflowServiceTest {
    private static final String CASE = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private final DepartmentCode hr = new DepartmentCode("118");
    private final DepartmentCode office = new DepartmentCode("101");
    private final TarasolEffectiveAccessReader access = mock(TarasolEffectiveAccessReader.class);
    private final TarasolOrganizationReader organization = mock(TarasolOrganizationReader.class);
    private final CorrespondenceActorRoleResolver roles = mock(CorrespondenceActorRoleResolver.class);
    private final AlfrescoCorrespondenceRepository repo = mock(AlfrescoCorrespondenceRepository.class);
    private final AuthorityService authorityService = mock(AuthorityService.class);
    private final AuthenticatedUserProvider authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
    private final TarasolSecretaryWorkflowService service =
        new TarasolSecretaryWorkflowService(access, organization, roles, repo,
            authorityService, authenticatedUserProvider);

    private void secretaryGroup() {
        when(authenticatedUserProvider.getUsername()).thenReturn("test.secretary");
        when(authorityService.getAuthoritiesForUser("test.secretary"))
            .thenReturn(Set.of(TarasolSecretaryGroupPolicy.AUTHORITY));
    }

    private EmployeeDirectoryEntry employee(String id) {
        return new EmployeeDirectoryEntry(new EmployeeId(id), new DirectoryUsername("emp." + id),
            "Employee " + id, null, Optional.empty());
    }

    private void pending(DepartmentCode unit, String owner) {
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, unit.value(), "Annual report", "Please approve", owner, "PENDING"));
    }

    private void manager(DepartmentCode unit, EmployeeDirectoryEntry actor, CorrespondenceActorRole role) {
        when(roles.resolveWorkspace(unit.value(), actor))
            .thenReturn(new CorrespondenceActorRoleResolver.Workspace(role, unit));
        when(organization.findCurrentManagerWorkspaces(actor.employeeId()))
            .thenReturn(List.of(workspace(unit)));
    }

    private TarasolOrganizationReader.ManagerWorkspace workspace(DepartmentCode unit) {
        return new TarasolOrganizationReader.ManagerWorkspace(Long.parseLong(unit.value()),
            unit, "Test " + unit.value(), null, "MANAGER");
    }

    @Test
    void personalDraftMetadataRequiresSecretaryGroupUnitAndCreator() {
        var secretary = employee("300");
        var saved = new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, hr.value(), "Report", "Body", "300", "RETURNED");
        when(repo.readSecretaryDraft(CASE)).thenReturn(saved);
        var metadata = new AlfrescoCorrespondenceRepository.SecretaryDraftMetadata(
            "DOC-117", LocalDate.of(2026, 11, 4), "Human Resources");
        when(repo.readSecretaryDraftMetadata(CASE)).thenReturn(metadata);

        // No group is still denied, even with an active Oracle assignment.
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.personalDraft(secretary, hr, CASE));
        verify(repo, never()).readSecretaryDraft(CASE);

        secretaryGroup();
        var details = service.personalDraft(secretary, hr, CASE);
        assertEquals(metadata, details.metadata());
        assertEquals("300", details.draft().ownerEmployeeId());
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, hr.value(), "Report", "Body", "99999", "DRAFT"));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.personalDraft(secretary, hr, CASE));
        verify(repo, times(1)).readSecretaryDraftMetadata(CASE);
    }

    @Test
    void ordinaryDirectorApprovesAndRoutesToOffice() {
        var actor = employee("200");
        pending(hr, "300");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        when(roles.resolveCurrentUserRoles()).thenReturn(Set.of(CorrespondenceActorRole.DIRECTOR));
        assertEquals(CorrespondenceState.WITH_PRESIDENT_OFFICE,
            service.approveAndSend(actor, hr, CASE));
        ArgumentCaptor<TransitionCorrespondenceCommand> action =
            ArgumentCaptor.forClass(TransitionCorrespondenceCommand.class);
        verify(repo).approveSecretaryDraft(action.capture(), eq(CorrespondenceState.WITH_PRESIDENT_OFFICE),
            eq(CorrespondenceActorRole.PRESIDENT_OFFICE));
        assertEquals(CorrespondenceAction.SEND_TO_OFFICE, action.getValue().action());
        assertEquals(actor.employeeId(), action.getValue().actorEmployeeId());
    }

    @Test
    void eligibleDualManagerApprovesDirectToPresident() {
        var actor = employee("18284");
        pending(hr, "300");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        when(roles.resolveCurrentUserRoles()).thenReturn(Set.of(
            CorrespondenceActorRole.DIRECTOR, CorrespondenceActorRole.PRESIDENT_OFFICE));
        when(organization.findCurrentManagerWorkspaces(actor.employeeId()))
            .thenReturn(List.of(workspace(hr), workspace(office)));
        assertEquals(CorrespondenceState.WITH_PRESIDENT, service.approveAndSend(actor, hr, CASE));
        verify(repo).approveSecretaryDraft(argThat(c ->
            c.action() == CorrespondenceAction.SEND_TO_PRESIDENT &&
            c.actorRole() == CorrespondenceActorRole.DIRECTOR &&
            c.actorEmployeeId().equals(actor.employeeId())),
            eq(CorrespondenceState.WITH_PRESIDENT), eq(CorrespondenceActorRole.PRESIDENT));
    }

    @Test
    void officeSecretaryIsApprovedByOfficeManagerAndSentDirectlyToPresident() {
        var actor = employee("18284");
        pending(office, "300");
        manager(office, actor, CorrespondenceActorRole.PRESIDENT_OFFICE);
        assertEquals(CorrespondenceState.WITH_PRESIDENT,
            service.approveAndSend(actor, office, CASE));
        verify(repo).approveSecretaryDraft(argThat(c ->
                c.actorRole() == CorrespondenceActorRole.PRESIDENT_OFFICE &&
                c.action() == CorrespondenceAction.SEND_TO_PRESIDENT),
            eq(CorrespondenceState.WITH_PRESIDENT), eq(CorrespondenceActorRole.PRESIDENT));
    }

    @Test
    void ordinaryOfficeMemberCannotApproveSecretaryDraftOrSeeManagerQueue() {
        var actor = employee("300");
        manager(office, actor, CorrespondenceActorRole.PRESIDENT_OFFICE);
        when(organization.findCurrentManagerWorkspaces(actor.employeeId()))
            .thenReturn(List.of());
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.pending(actor, office));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.approveAndSend(actor, office, CASE));
        verifyNoInteractions(repo);
    }

    @Test
    void priorOfficeAppointmentIsNotEnoughToSendDirectlyToPresident() {
        var actor = employee("18284");
        pending(hr, "300");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        when(roles.resolveCurrentUserRoles()).thenReturn(Set.of(
            CorrespondenceActorRole.DIRECTOR, CorrespondenceActorRole.PRESIDENT_OFFICE));
        when(organization.findCurrentManagerWorkspaces(actor.employeeId()))
            .thenReturn(List.of(workspace(hr)));
        assertEquals(CorrespondenceState.WITH_PRESIDENT_OFFICE,
            service.approveAndSend(actor, hr, CASE));
    }

    @Test
    void incorrectUnitOrStaleApprovalCannotBeApproved() {
        var actor = employee("18284");
        manager(hr, actor, CorrespondenceActorRole.DIRECTOR);
        pending(office, "300");
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.approveAndSend(actor, hr, CASE));
        verify(repo, never()).approveSecretaryDraft(any(), any(), any());
    }

    @Test
    void secretaryCannotCreateOrSubmitWithoutActiveAssignment() {
        secretaryGroup();
        var secretary = employee("300");
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.create(secretary, hr, "Request", "Body"));
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.submit(secretary, hr, CASE));
        verifyNoInteractions(repo);
    }

    @Test
    void oracleSecretaryAssignmentWithoutAlfrescoGroupIsDenied() {
        var secretary = employee("300");
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.create(secretary, hr, "Request", "Body"));
        verifyNoInteractions(repo);
    }

    @Test
    void secretaryGroupWithoutActiveOracleUnitIsDenied() {
        secretaryGroup();
        var secretary = employee("300");
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.page(secretary, hr, false, 0, 20));
        verifyNoInteractions(repo);
    }

    @Test
    void secretarySubmitAndManagerReturnPreserveActualActors() {
        var secretary = employee("300");
        var manager = employee("200");
        secretaryGroup();
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, hr.value(), "Report", "Body", secretary.employeeId().value(), "DRAFT"),
            new AlfrescoCorrespondenceRepository.SecretaryDraft(
                CASE, hr.value(), "Report", "Body", secretary.employeeId().value(), "PENDING"));
        manager(hr, manager, CorrespondenceActorRole.DIRECTOR);
        service.submit(secretary, hr, CASE);
        verify(repo).changeSecretaryStatus(argThat(c ->
            c.actorEmployeeId().equals(secretary.employeeId()) &&
            c.action() == CorrespondenceAction.SUBMIT_FOR_MANAGER_APPROVAL),
            eq("DRAFT"), eq("PENDING"), eq(CorrespondenceActorRole.DIRECTOR));
        service.returnForRevision(manager, hr, CASE, "Please revise the figures");
        verify(repo).changeSecretaryStatus(argThat(c ->
            c.actorEmployeeId().equals(manager.employeeId()) &&
            c.action() == CorrespondenceAction.RETURN_TO_SECRETARY &&
            "Please revise the figures".equals(c.explanation())),
            eq("PENDING"), eq("RETURNED"), eq(CorrespondenceActorRole.SECRETARY));
    }
    @Test
    void secretaryCanSeeOnlyOwnPagedSubmittedAndApprovedCases() {
        var secretary = employee("300");
        secretaryGroup();
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        var sent = new AlfrescoCorrespondenceRepository.SecretaryDraft(CASE, hr.value(),
            "Sent report", "Body", "300", "APPROVED");
        when(repo.pageSecretaryCases(hr, "300", false, 0, 20))
            .thenReturn(new AlfrescoCorrespondenceRepository.SecretaryPage(List.of(sent), 1));
        var page = service.page(secretary, hr, false, 0, 20);
        assertEquals(1, page.total());
        assertEquals("APPROVED", page.items().getFirst().approvalStatus());
        verify(repo).pageSecretaryCases(hr, "300", false, 0, 20);
    }

    @Test
    void expiredSecretaryCannotListPersonalSentCases() {
        var secretary = employee("300");
        assertThrows(CorrespondenceAuthorizationException.class,
            () -> service.page(secretary, hr, false, 0, 20));
        verifyNoInteractions(repo);
    }


    @Test
    void secretaryFullDraftUsesRealCreatorAndOriginatingDepartment() {
        secretaryGroup();
        var secretary = employee("300");
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        when(repo.createSecretaryDraft(any(CreateCorrespondenceCommand.class))).thenReturn(CASE);
        var date = LocalDate.of(2026, 11, 4);

        assertEquals(CASE, service.create(secretary, hr, "Request", "Full body",
            "HR-2026-123", date, "Human Resources"));

        ArgumentCaptor<CreateCorrespondenceCommand> captured =
            ArgumentCaptor.forClass(CreateCorrespondenceCommand.class);
        verify(repo).createSecretaryDraft(captured.capture());
        var command = captured.getValue();
        assertEquals(hr, command.originDepartmentCode());
        assertEquals(secretary.employeeId(), command.createdByEmployeeId());
        assertEquals(CorrespondenceActorRole.DIRECTOR, command.createdByRole());
        assertEquals("HR-2026-123", command.documentNumber());
        assertEquals(date, command.documentDate());
        assertEquals("Human Resources", command.sourceText());
        assertEquals("Full body", command.bodyText());
    }

    @Test
    void fullRevisionRetainsOwnerAuthorizationAndForwardsDocumentMetadata() {
        secretaryGroup();
        var secretary = employee("300");
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, hr.value(), "Old", "Old body", "300", "RETURNED"));

        var date = LocalDate.of(2026, 10, 31);
        service.revise(secretary, hr, CASE, "Revised", "Updated body",
            "HR-2026-124", date, "Human Resources", true);
        verify(repo).reviseSecretaryDraft(argThat(action ->
            action.actorEmployeeId().equals(secretary.employeeId())
                && action.action() == CorrespondenceAction.REVISE_SECRETARY_DRAFT),
            eq("Revised"), eq("Updated body"), eq("HR-2026-124"), eq(date), eq("Human Resources"));
    }

    @Test
    void legacyRevisionDoesNotClearExistingDocumentMetadata() {
        secretaryGroup();
        var secretary = employee("300");
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, hr.value(), "Old", "Old body", "300", "DRAFT"));

        service.revise(secretary, hr, CASE, "Revised", "Updated body");
        verify(repo).reviseSecretaryDraft(any(TransitionCorrespondenceCommand.class),
            eq("Revised"), eq("Updated body"));
        verify(repo, never()).reviseSecretaryDraft(any(TransitionCorrespondenceCommand.class),
            anyString(), anyString(), any(), any(), any());
    }

    @Test
    void secretaryCannotReviseAnotherCreatorsFullCorrespondence() {
        secretaryGroup();
        var secretary = employee("300");
        when(access.hasActiveSecretaryAssignment(secretary.employeeId(), hr)).thenReturn(true);
        when(repo.readSecretaryDraft(CASE)).thenReturn(new AlfrescoCorrespondenceRepository.SecretaryDraft(
            CASE, hr.value(), "Old", "Body", "999", "RETURNED"));

        assertThrows(CorrespondenceAuthorizationException.class, () ->
            service.revise(secretary, hr, CASE, "Revised", "Body",
                "HR-100", LocalDate.now(), "HR", true));
        verify(repo, never()).reviseSecretaryDraft(any(), anyString(), anyString(),
            any(), any(), any());
    }
}

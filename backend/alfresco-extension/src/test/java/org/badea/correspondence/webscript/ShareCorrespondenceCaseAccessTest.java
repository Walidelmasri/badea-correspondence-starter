package org.badea.correspondence.webscript;

import java.util.List;
import java.util.Optional;
import org.badea.correspondence.application.CorrespondenceDetails;
import org.badea.correspondence.application.CorrespondenceDetailsService;
import org.badea.correspondence.application.CorrespondenceDocument;
import org.badea.correspondence.application.CurrentEmployeeService;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DirectoryUsername;
import org.badea.correspondence.directory.EmployeeDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.organization.TarasolSecretaryInboxReadService;
import org.badea.correspondence.organization.TarasolSecretaryPersonalSentReadService;
import org.badea.correspondence.security.CorrespondenceActorRoleResolver;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.Test;
import org.springframework.extensions.webscripts.WebScriptRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class ShareCorrespondenceCaseAccessTest {
    private static final String ID = "7fc41b5b-aceb-48fc-9f36-367e4a36e207";
    private final CurrentEmployeeService employees = mock(CurrentEmployeeService.class);
    private final CorrespondenceActorRoleResolver roles = mock(CorrespondenceActorRoleResolver.class);
    private final CorrespondenceDetailsService details = mock(CorrespondenceDetailsService.class);
    private final TarasolSecretaryInboxReadService inbox = mock(TarasolSecretaryInboxReadService.class);
    private final TarasolSecretaryPersonalSentReadService sent = mock(TarasolSecretaryPersonalSentReadService.class);
    private final ShareCorrespondenceCaseAccess access = new ShareCorrespondenceCaseAccess(
            employees, roles, details, inbox, sent);
    private final WebScriptRequest request = mock(WebScriptRequest.class);
    private final EmployeeDirectoryEntry secretary = new EmployeeDirectoryEntry(
            new EmployeeId("100"), new DirectoryUsername("secretary"),
            "Secretary", null, Optional.empty());
    private final CorrespondenceDetails visibleCase = new CorrespondenceDetails(
            ID, CorrespondenceState.DRAFT, "Test", "", null, null, null,
            "117", "Secretary", null, List.of(new CorrespondenceDocument(
                    "f1bc7ab2-b55b-48ab-ae18-676e35615d74", "a.pdf", "application/pdf", 100)));

    @Test void secretarySentUsesPersonalOwnerAuthorizationInsteadOfManagerWorkspace() {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getParameter("shareSecretaryUnit")).thenReturn("117");
        when(request.getParameter("shareSecretaryBox")).thenReturn("sent");
        when(sent.details(secretary, new DepartmentCode("117"), ID)).thenReturn(visibleCase);
        assertSame(visibleCase, access.require(request, ID).caseDetails());
        verify(sent).details(secretary, new DepartmentCode("117"), ID);
        verifyNoInteractions(roles, details, inbox);
    }

    @Test void secretaryInboxUsesActiveUnitAndSharedInboxAuthorization() {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getParameter("shareSecretaryUnit")).thenReturn("117");
        when(request.getParameter("shareSecretaryBox")).thenReturn("inbox");
        when(inbox.details(secretary, new DepartmentCode("117"), ID)).thenReturn(visibleCase);
        assertSame(visibleCase, access.require(request, ID).caseDetails());
        verify(inbox).details(secretary, new DepartmentCode("117"), ID);
        verifyNoInteractions(roles, details, sent);
    }

    @Test void invalidSecretaryScopeDoesNotFallThroughToDirectorAuthorization() {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getParameter("shareSecretaryUnit")).thenReturn("117");
        when(request.getParameter("shareSecretaryBox")).thenReturn("wrong");
        assertThrows(IllegalArgumentException.class, () -> access.require(request, ID));
        verifyNoInteractions(roles, details, inbox, sent);
    }

    @Test void readerDenialIsNotConvertedToManagerPrivileges() {
        when(employees.getCurrentEmployee()).thenReturn(secretary);
        when(request.getParameter("shareSecretaryUnit")).thenReturn("119");
        when(request.getParameter("shareSecretaryBox")).thenReturn("inbox");
        when(inbox.details(secretary, new DepartmentCode("119"), ID))
                .thenThrow(new CorrespondenceAuthorizationException("Not assigned"));
        assertThrows(CorrespondenceAuthorizationException.class, () -> access.require(request, ID));
        verifyNoInteractions(roles, details, sent);
    }
}

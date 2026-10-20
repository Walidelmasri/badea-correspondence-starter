package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficeDistributionServiceTest {
    private final CorrespondenceTransitionRepository correspondence=mock(CorrespondenceTransitionRepository.class);
    private final PresidentReturnEvidence returns=mock(PresidentReturnEvidence.class);
    private final PresidentReviewStateRepository president=mock(PresidentReviewStateRepository.class);
    private final OfficeCompletionRepository office=mock(OfficeCompletionRepository.class);
    private final CorrespondenceDetailsRepository details=mock(CorrespondenceDetailsRepository.class);
    private final CorrespondenceDepartmentService departments=mock(CorrespondenceDepartmentService.class);
    private final OfficeActionSheetRenderer pdf=mock(OfficeActionSheetRenderer.class);
    private final OfficeDistributionRepository storage=mock(OfficeDistributionRepository.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-20T08:00:00Z"), ZoneOffset.UTC);
    private final OfficeDistributionService service=new OfficeDistributionService(correspondence, returns,president,office,
            details,departments,new OfficeDistributionPlanner(departments),pdf,storage,clock);
    private final OfficeDispatchCommand command=new OfficeDispatchCommand("b9ff7420-3ae3-4b19-9a35-42fe2ba29c91",
            5,2,new EmployeeId("99"),"Office Clerk",null);

    @BeforeEach void arrange() {
        when(storage.dispatchId(command.technicalId(),5,2)).thenReturn(Optional.empty());
        when(correspondence.getAccessContext(command.technicalId())).thenReturn(new CorrespondenceAccessContext(
                CorrespondenceState.WITH_PRESIDENT_OFFICE,new DepartmentCode("117"),
                CorrespondenceActorRole.PRESIDENT_OFFICE,null));
        when(returns.latestActionIsPresidentReturn(command.technicalId())).thenReturn(true);
        when(president.find(command.technicalId())).thenReturn(Optional.of(new PresidentReviewState(5,
                "{\"schemaVersion\":1,\"selectedRecipientIds\":[\"117\"],\"selectedCopyRecipientIds\":[\"118\"],\"selectedActionCodes\":[\"study-and-opinion\"],\"typedExplanation\":\"President\"}")));
        when(office.find(command.technicalId())).thenReturn(Optional.of(new OfficeCompletionState(2,5,
                "{\"schemaVersion\":1,\"selectedRecipientIds\":[\"118\"],\"selectedCopyRecipientIds\":[],\"selectedActionCodes\":[],\"otherRecipient\":\"\",\"otherAction\":\"\",\"typedExplanation\":\"Office\",\"officeNotes\":\"Note\"}",
                "99","Office Clerk",null,clock.instant())));
        when(departments.requireDepartment(new DepartmentCode("117"))).thenReturn(new DepartmentDirectoryEntry(new DepartmentCode("117"),"IT","إدارة التحول الرقمي"));
        when(departments.requireDepartment(new DepartmentCode("118"))).thenReturn(new DepartmentDirectoryEntry(new DepartmentCode("118"),"Finance","إدارة الموارد البشرية"));
        when(details.findByTechnicalId(command.technicalId())).thenReturn(new CorrespondenceDetails(command.technicalId(),
                CorrespondenceState.WITH_PRESIDENT_OFFICE,"Example",null,null,null,null,"117","Author",null,List.of()));
    }

    @Test void dispatchMergesToCopyAndWritesOneImmutableHandoff() {
        when(pdf.render(any(),any(),anyList(),anyList())).thenReturn("%PDF-example".getBytes());
        when(storage.distribute(eq(command),any(),any(),anyList())).thenAnswer(invocation -> {
            OfficeDistributionPlan plan=invocation.getArgument(1);
            List<DepartmentAssignment> actions=invocation.getArgument(3);
            assertEquals(2,plan.recipients().to().size());
            assertTrue(plan.recipients().copy().isEmpty());
            assertEquals(2,actions.size());
            assertTrue(actions.stream().allMatch(a->a.recipientType()==DepartmentRecipientType.TO));
            return "dispatch-1";
        });
        assertEquals("dispatch-1",service.distribute(command,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verify(storage).distribute(eq(command),any(),any(),anyList());
    }

    @Test void forbidsPrematureDispatch() {
        when(returns.latestActionIsPresidentReturn(command.technicalId())).thenReturn(false);
        assertThrows(CorrespondenceAuthorizationException.class,()->service.distribute(command,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(pdf);
    }

    @Test void staleOfficeRevisionNeverGeneratesPdf() {
        var stale=new OfficeDispatchCommand(command.technicalId(),5,3,command.actorEmployeeId(),command.actorNameEnglish(),null);
        when(storage.dispatchId(stale.technicalId(),5,3)).thenReturn(Optional.empty());
        assertThrows(OfficeCompletionStateConflictException.class,()->service.distribute(stale,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(pdf);
    }

    @Test void repeatRequestUsesPersistedDispatchIdentityWithoutWriting() {
        when(storage.dispatchId(command.technicalId(),5,2)).thenReturn(Optional.of("existing-dispatch"));
        assertEquals("existing-dispatch",service.distribute(command,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(pdf);
    }
}

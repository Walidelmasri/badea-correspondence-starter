package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficeCompletionServiceTest {
    private static final String ID="453ae576-a392-4150-ac69-c532f9487a23";
    private static final String STATE="""
            {"schemaVersion":1,"selectedRecipientIds":["117","118"],
             "selectedCopyRecipientIds":[],"selectedActionCodes":["follow-up"],
             "otherRecipient":"","otherAction":"","typedExplanation":"",
             "officeNotes":"Filed by Office"}
            """;
    private CorrespondenceTransitionRepository correspondence;
    private PresidentReturnEvidence evidence;
    private PresidentReviewStateRepository president;
    private OfficeCompletionRepository repository;
    private OfficeCompletionService service;
    private SaveOfficeCompletionCommand command;

    @BeforeEach void setup() {
        correspondence=mock(CorrespondenceTransitionRepository.class);
        evidence=mock(PresidentReturnEvidence.class);
        president=mock(PresidentReviewStateRepository.class);
        repository=mock(OfficeCompletionRepository.class);
        service=new OfficeCompletionService(correspondence,evidence,president,repository);
        when(correspondence.getAccessContext(ID)).thenReturn(context(CorrespondenceState.WITH_PRESIDENT_OFFICE,CorrespondenceActorRole.PRESIDENT_OFFICE));
        when(evidence.latestActionIsPresidentReturn(ID)).thenReturn(true);
        when(president.find(ID)).thenReturn(Optional.of(new PresidentReviewState(4,"{\"schemaVersion\":1}")));
        command=new SaveOfficeCompletionCommand(ID,0,4,new EmployeeId("99999"),"Office User",null,STATE);
    }
    @Test void canLoadOnlyAfterPresidentReturn() {
        service.load(ID,CorrespondenceActorRole.PRESIDENT_OFFICE);
        verify(repository).find(ID);
    }
    @Test void canSaveOfficeSnapshotWithoutChangingPresidentReview() {
        service.save(command,CorrespondenceActorRole.PRESIDENT_OFFICE);
        verify(repository).save(any(SaveOfficeCompletionCommand.class));
        verify(president,never()).save(any());
    }
    @Test void presidentCannotWriteOfficeCompletion() {
        assertThrows(CorrespondenceAuthorizationException.class,
                ()->service.save(command,CorrespondenceActorRole.PRESIDENT));
        verifyNoInteractions(repository);
    }
    @Test void otherDirectorsCannotWriteOfficeCompletion() {
        assertThrows(CorrespondenceAuthorizationException.class,
                ()->service.save(command,CorrespondenceActorRole.DIRECTOR));
        verifyNoInteractions(repository);
    }
    @Test void cannotWriteBeforePresidentReturn() {
        when(evidence.latestActionIsPresidentReturn(ID)).thenReturn(false);
        assertThrows(CorrespondenceAuthorizationException.class,
                ()->service.save(command,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(repository);
    }
    @Test void cannotWriteAfterAssignmentChanges() {
        when(correspondence.getAccessContext(ID)).thenReturn(context(CorrespondenceState.WITH_DIRECTOR,CorrespondenceActorRole.DIRECTOR));
        assertThrows(CorrespondenceAuthorizationException.class,
                ()->service.save(command,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(repository);
    }
    @Test void rejectsStalePresidentReviewRevision() {
        SaveOfficeCompletionCommand stale=new SaveOfficeCompletionCommand(ID,0,3,new EmployeeId("99999"),"Office User",null,STATE);
        assertThrows(OfficeCompletionStateConflictException.class,
                ()->service.save(stale,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(repository);
    }
    @Test void rejectsPresidentInkOrUnrecognizedFields() {
        SaveOfficeCompletionCommand malicious=new SaveOfficeCompletionCommand(ID,0,4,new EmployeeId("99999"),"Office User",null,STATE.replace("\"officeNotes\":", "\"pdfInkDocuments\":[],\"officeNotes\":"));
        assertThrows(IllegalArgumentException.class,
                ()->service.save(malicious,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(repository);
    }
    @Test void rejectsDuplicateDepartmentSelections() {
        SaveOfficeCompletionCommand invalid=new SaveOfficeCompletionCommand(ID,0,4,new EmployeeId("99999"),"Office User",null,STATE.replace("\"117\",\"118\"","\"117\",\"117\""));
        assertThrows(IllegalArgumentException.class,
                ()->service.save(invalid,CorrespondenceActorRole.PRESIDENT_OFFICE));
        verifyNoInteractions(repository);
    }
    private static CorrespondenceAccessContext context(CorrespondenceState state,CorrespondenceActorRole role) {
        return new CorrespondenceAccessContext(state,new DepartmentCode("117"),role,null);
    }
}

package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class PresidentSignatureFreezeAtHandoffTest {
    private static final String CASE_ID = "142ba8df-569d-4aa3-9e40-498514c2f3ac";
    private final PresidentReviewStateRepository reviews = mock(PresidentReviewStateRepository.class);
    private final CorrespondenceDetailsRepository details = mock(CorrespondenceDetailsRepository.class);
    private final CorrespondenceDocumentContentRepository sources = mock(CorrespondenceDocumentContentRepository.class);
    private final PresidentPdfRenderer renderer = mock(PresidentPdfRenderer.class);
    private final PresidentGeneratedDocumentStore generated = mock(PresidentGeneratedDocumentStore.class);
    private final PresidentSignatureStore signatures = mock(PresidentSignatureStore.class);
    private final TransitionCorrespondenceCommand handoff = new TransitionCorrespondenceCommand(
            CASE_ID, CorrespondenceActorRole.PRESIDENT, CorrespondenceAction.SEND_TO_OFFICE,
            new EmployeeId("30001"), null, "President Test", null, null, null, null);

    private PresidentDocumentFinalizationService service() {
        return new PresidentDocumentFinalizationService(reviews, details, sources, renderer,
                generated, new PresidentInkReviewParser(), signatures);
    }

    @Test void selectedSlotIsFrozenWithExactRevisionBeforeOfficeReceivesCase() {
        when(reviews.find(CASE_ID)).thenReturn(Optional.of(new PresidentReviewState(9,
                "{\"schemaVersion\":1,\"selectedSignatureSlot\":2,\"selectedSignatureRevision\":3}")));
        when(details.findByTechnicalId(CASE_ID)).thenReturn(new CorrespondenceDetails(CASE_ID,
                org.badea.correspondence.workflow.CorrespondenceState.WITH_PRESIDENT,
                "Test",null,null,null,null,"117","Creator",null, List.of()));
        service().finalizeSubmission(handoff);
        verify(signatures).freezeForReview(CASE_ID, "30001", 9, 2, 3);
        verifyNoInteractions(generated, renderer, sources);
    }

    @Test void missingChoiceRetainsHistoricalUnsignedBehavior() {
        when(reviews.find(CASE_ID)).thenReturn(Optional.of(new PresidentReviewState(1,
                "{\"schemaVersion\":1}")));
        when(details.findByTechnicalId(CASE_ID)).thenReturn(new CorrespondenceDetails(CASE_ID,
                org.badea.correspondence.workflow.CorrespondenceState.WITH_PRESIDENT,
                "Test",null,null,null,null,"117","Creator",null, List.of()));
        service().finalizeSubmission(handoff);
        verifyNoInteractions(signatures);
    }

    @Test void invalidSignatureSlotIsNeverSilentlyApplied() {
        when(reviews.find(CASE_ID)).thenReturn(Optional.of(new PresidentReviewState(1,
                "{\"schemaVersion\":1,\"selectedSignatureSlot\":3,\"selectedSignatureRevision\":2}")));
        assertThrows(IllegalArgumentException.class, () -> service().finalizeSubmission(handoff));
        verifyNoInteractions(generated);
    }
}

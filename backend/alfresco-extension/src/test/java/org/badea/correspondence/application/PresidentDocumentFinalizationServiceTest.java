package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.workflow.CorrespondenceAction;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PresidentDocumentFinalizationServiceTest {
    private final String id = "11111111-1111-1111-1111-111111111111";
    private final String documentId = "22222222-2222-2222-2222-222222222222";
    private PresidentReviewStateRepository reviews;
    private CorrespondenceDetailsRepository details;
    private CorrespondenceDocumentContentRepository source;
    private PresidentPdfRenderer renderer;
    private PresidentGeneratedDocumentStore output;
    private PresidentDocumentFinalizationService service;

    @BeforeEach void setup() {
        reviews = mock(PresidentReviewStateRepository.class);
        details = mock(CorrespondenceDetailsRepository.class);
        source = mock(CorrespondenceDocumentContentRepository.class);
        renderer = mock(PresidentPdfRenderer.class);
        output = mock(PresidentGeneratedDocumentStore.class);
        service = new PresidentDocumentFinalizationService(reviews, details, source,
                renderer, output, new PresidentInkReviewParser());
    }

    private TransitionCorrespondenceCommand command() {
        return new TransitionCorrespondenceCommand(id, CorrespondenceActorRole.PRESIDENT,
                CorrespondenceAction.SEND_TO_OFFICE, new EmployeeId("99999"), null,
                "President", null, null, null, null);
    }

    @Test void noReviewIsValidAndProducesNoFiles() {
        when(reviews.find(id)).thenReturn(Optional.empty());
        service.finalizeSubmission(command());
        verifyNoInteractions(output, renderer);
    }

    @Test void persistsAnnotatedAndHandwrittenArtifactsOncePerRevision() {
        when(reviews.find(id)).thenReturn(Optional.of(new PresidentReviewState(7, """
          {"schemaVersion":1,
           "pdfInkDocuments":[{"documentKey":"22222222-2222-2222-2222-222222222222",
               "strokes":[{"pageNumber":1,"tool":"pen","points":[
                 {"x":0.2,"y":0.5,"pressure":0.5}]}]}],
           "explanationInkDocument":{"schemaVersion":1,"pages":[{"id":"p1",
               "strokes":[{"tool":"pen","points":[{"x":0.3,"y":0.4,"pressure":0.5}]}]}]}}
          """)));
        when(details.findByTechnicalId(id)).thenReturn(new CorrespondenceDetails(id,
                org.badea.correspondence.workflow.CorrespondenceState.WITH_PRESIDENT,
                "subject", null, null, null, null, "117", "Director", null,
                List.of(new CorrespondenceDocument(documentId, "input.pdf", "application/pdf", 5))));
        when(source.openByNodeId(documentId)).thenReturn(new ByteArrayInputStream("%PDF-".getBytes()));
        when(renderer.annotate(any(byte[].class), anyList())).thenReturn("%PDF-annotated".getBytes());
        when(renderer.explanation(anyList())).thenReturn("%PDF-explanation".getBytes());
        service.finalizeSubmission(command());
        verify(output).save(eq(id), eq(7L), eq("ANNOTATED_PDF"), eq(documentId),
                anyString(), any(byte[].class), any(EmployeeId.class));
        verify(output).save(eq(id), eq(7L), eq("HANDWRITTEN_EXPLANATION"), isNull(),
                anyString(), any(byte[].class), any(EmployeeId.class));
        // On retry the exact same persisted revision can never be regenerated.
        when(output.find(eq(id), eq(7L), eq("ANNOTATED_PDF"), eq(documentId))).thenReturn(
                Optional.of(new CorrespondenceGeneratedDocument("33333333-3333-3333-3333-333333333333",
                        "annotated.pdf", "application/pdf", 5, "ANNOTATED_PDF", documentId, 7)));
        when(output.find(eq(id), eq(7L), eq("HANDWRITTEN_EXPLANATION"), isNull())).thenReturn(
                Optional.of(new CorrespondenceGeneratedDocument("44444444-4444-4444-4444-444444444444",
                        "explanation.pdf", "application/pdf", 5, "HANDWRITTEN_EXPLANATION", null, 7)));
        clearInvocations(output, renderer, source);
        service.finalizeSubmission(command());
        verify(output, never()).save(anyString(), anyLong(), anyString(), any(),
                anyString(), any(byte[].class), any(EmployeeId.class));
        verifyNoInteractions(renderer, source);
    }
}

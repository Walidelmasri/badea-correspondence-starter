package org.badea.correspondence.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

/** Called only after workflow eligibility checks, inside the transition transaction. */
public final class PresidentDocumentFinalizationService implements PresidentDocumentFinalizer {
    private static final long MAX_SOURCE_BYTES = 100L * 1024L * 1024L;
    private static final String ANNOTATED = "ANNOTATED_PDF";
    private static final String EXPLANATION = "HANDWRITTEN_EXPLANATION";

    private final PresidentReviewStateRepository reviews;
    private final CorrespondenceDetailsRepository details;
    private final CorrespondenceDocumentContentRepository sources;
    private final PresidentPdfRenderer renderer;
    private final PresidentGeneratedDocumentStore generated;
    private final PresidentInkReviewParser parser;

    public PresidentDocumentFinalizationService(PresidentReviewStateRepository reviews,
            CorrespondenceDetailsRepository details, CorrespondenceDocumentContentRepository sources,
            PresidentPdfRenderer renderer, PresidentGeneratedDocumentStore generated,
            PresidentInkReviewParser parser) {
        this.reviews = Objects.requireNonNull(reviews);
        this.details = Objects.requireNonNull(details);
        this.sources = Objects.requireNonNull(sources);
        this.renderer = Objects.requireNonNull(renderer);
        this.generated = Objects.requireNonNull(generated);
        this.parser = Objects.requireNonNull(parser);
    }

    @Override
    public void finalizeSubmission(TransitionCorrespondenceCommand command) {
        if (command.actorRole() != CorrespondenceActorRole.PRESIDENT) {
            throw new IllegalArgumentException("Only President submission may finalize reviews");
        }
        // No review is valid: every presidential field is optional.
        var snapshot = reviews.find(command.technicalId());
        if (snapshot.isEmpty()) return;
        PresidentReviewState revision = snapshot.get();
        PresidentInkReview ink = parser.parse(revision.json());
        var original = details.findByTechnicalId(command.technicalId()).documents();
        Set<String> known = new HashSet<>();
        for (CorrespondenceDocument document : original) known.add(document.nodeId());
        if (!known.containsAll(ink.documentStrokes().keySet())) {
            throw new IllegalArgumentException("Review references an attachment outside this correspondence");
        }
        for (CorrespondenceDocument document : original) {
            List<PresidentInkReview.Stroke> strokes =
                    ink.documentStrokes().getOrDefault(document.nodeId(), List.of());
            if (strokes.isEmpty()) continue;
            if (!"application/pdf".equals(document.mimeType()) || document.sizeBytes() > MAX_SOURCE_BYTES) {
                throw new IllegalArgumentException("Unsupported PDF attachment for finalization");
            }
            if (generated.find(command.technicalId(), revision.revision(), ANNOTATED, document.nodeId()).isPresent()) {
                continue;
            }
            byte[] source;
            try (InputStream input = sources.openByNodeId(document.nodeId())) {
                source = input.readNBytes((int) MAX_SOURCE_BYTES + 1);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load President's original PDF", exception);
            }
            if (source.length > MAX_SOURCE_BYTES) {
                throw new IllegalArgumentException("PDF exceeds finalization size limit");
            }
            byte[] flattened = renderer.annotate(source, strokes);
            generated.save(command.technicalId(), revision.revision(), ANNOTATED,
                    document.nodeId(), "annotated-" + document.nodeId() + ".pdf",
                    flattened, command.actorEmployeeId());
        }
        boolean hasHandwriting = ink.explanationPages().stream().anyMatch(page -> !page.isEmpty());
        if (hasHandwriting && generated.find(command.technicalId(), revision.revision(), EXPLANATION, null).isEmpty()) {
            byte[] explanation = renderer.explanation(ink.explanationPages());
            generated.save(command.technicalId(), revision.revision(), EXPLANATION,
                    null, "president-handwritten-explanation.pdf", explanation,
                    command.actorEmployeeId());
        }
    }
}

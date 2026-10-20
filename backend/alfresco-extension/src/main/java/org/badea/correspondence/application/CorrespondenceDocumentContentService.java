package org.badea.correspondence.application;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

public final class CorrespondenceDocumentContentService {

    private final CorrespondenceDetailsService detailsService;

    private final CorrespondenceDocumentContentRepository
            contentRepository;

    public CorrespondenceDocumentContentService(
            CorrespondenceDetailsService detailsService,
            CorrespondenceDocumentContentRepository contentRepository) {

        this.detailsService =
                Objects.requireNonNull(
                        detailsService,
                        "detailsService must not be null");

        this.contentRepository =
                Objects.requireNonNull(
                        contentRepository,
                        "contentRepository must not be null");
    }

    public CorrespondenceDocumentStream open(
            String technicalId,
            String documentNodeId,
            CorrespondenceActorRole actorRole,
            DepartmentCode actorDepartmentCode) {

        Objects.requireNonNull(
                technicalId,
                "technicalId must not be null");

        Objects.requireNonNull(
                actorRole,
                "actorRole must not be null");

        return open(technicalId, documentNodeId, actorRole, actorDepartmentCode, null);
    }

    public CorrespondenceDocumentStream open(String technicalId, String documentNodeId,
            CorrespondenceActorRole actorRole, DepartmentCode actorDepartmentCode,
            String authenticatedEmployeeId) {
        Objects.requireNonNull(technicalId, "technicalId must not be null");
        Objects.requireNonNull(actorRole, "actorRole must not be null");

        String validatedNodeId =
                validateNodeId(documentNodeId);

        /*
         * Authorization happens before opening any document content.
         *
         * This service uses the existing correspondence-details service,
         * which validates the current workflow assignment.
         */
        CorrespondenceDetails correspondence =
                detailsService.getDetails(
                        technicalId,
                        actorRole,
                        actorDepartmentCode,
                        authenticatedEmployeeId);

        /*
         * Only files returned as direct source-document children
         * of this correspondence are accessible.
         *
         * An arbitrary valid Alfresco node ID is insufficient.
         */
        CorrespondenceDocument document =
                java.util.stream.Stream.concat(
                        correspondence.documents().stream(),
                        correspondence.generatedDocuments().stream()
                                .map(generated -> new CorrespondenceDocument(
                                        generated.nodeId(), generated.filename(),
                                        generated.mimeType(), generated.sizeBytes())))
                        .filter(candidate ->
                                candidate.nodeId()
                                        .equals(validatedNodeId))
                        .findFirst()
                        .orElseThrow(
                                CorrespondenceDocumentNotFoundException::new);

        InputStream content =
                contentRepository.openByNodeId(
                        document.nodeId());

        return new CorrespondenceDocumentStream(
                document.filename(),
                document.mimeType(),
                content);
    }

    private static String validateNodeId(
            String nodeId) {

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Document node ID is required.");
        }

        try {
            UUID parsed = UUID.fromString(nodeId);

            if (!parsed.toString().equals(nodeId)) {
                throw new IllegalArgumentException(
                        "Document node ID must be a canonical UUID.");
            }

            return nodeId;

        } catch (IllegalArgumentException exception) {

            throw new IllegalArgumentException(
                    "Document node ID must be a valid canonical UUID.",
                    exception);
        }
    }
}
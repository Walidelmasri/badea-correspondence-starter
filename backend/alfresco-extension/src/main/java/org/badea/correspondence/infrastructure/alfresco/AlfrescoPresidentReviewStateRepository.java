package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.PresidentReviewState;
import org.badea.correspondence.application.PresidentReviewStateConflictException;
import org.badea.correspondence.application.PresidentReviewStateRepository;
import org.badea.correspondence.application.SavePresidentReviewStateCommand;

public final class AlfrescoPresidentReviewStateRepository
        implements PresidentReviewStateRepository {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final QName PROP_PRESIDENT_REVIEW_STATE =
            qname(
                    "presidentReviewState");

    private static final QName PROP_PRESIDENT_REVIEW_CYCLE =
            qname("presidentReviewCycle");

    private static final QName PROP_PRESIDENT_REVIEW_REVISION =
            qname(
                    "presidentReviewRevision");

    private static final QName PROP_PRESIDENT_REVIEW_UPDATED_BY =
            qname(
                    "presidentReviewUpdatedByEmployeeId");

    private static final QName PROP_PRESIDENT_REVIEW_UPDATED_AT =
            qname(
                    "presidentReviewUpdatedAt");

    private final NodeService nodeService;
    private final ContentService contentService;
    private final CorrespondenceNodeLocator nodeLocator;
    private final Clock clock;

    public AlfrescoPresidentReviewStateRepository(
            NodeService nodeService,
            ContentService contentService,
            CorrespondenceNodeLocator nodeLocator,
            Clock clock) {

        this.nodeService =
                Objects.requireNonNull(
                        nodeService,
                        "nodeService must not be null");

        this.contentService =
                Objects.requireNonNull(
                        contentService,
                        "contentService must not be null");

        this.nodeLocator =
                Objects.requireNonNull(
                        nodeLocator,
                        "nodeLocator must not be null");

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null");
    }

    @Override
    public Optional<PresidentReviewState> find(
            String technicalId) {

        Objects.requireNonNull(
                technicalId,
                "technicalId must not be null");

        if (technicalId.isBlank()) {
            throw new IllegalArgumentException(
                    "technicalId must not be blank");
        }

        NodeRef correspondence =
                nodeLocator.locate(
                        technicalId);

        ContentReader reader =
                contentService.getReader(
                        correspondence,
                        PROP_PRESIDENT_REVIEW_STATE);

        if (reader == null
                || !reader.exists()) {

            return Optional.empty();
        }

        String json =
                reader.getContentString();

        if (json == null
                || json.isBlank()) {

            return Optional.empty();
        }

        return Optional.of(
                new PresidentReviewState(
                        readRevision(
                                correspondence),
                        json));
    }

    @Override
    public PresidentReviewState save(
            SavePresidentReviewStateCommand command) {

        Objects.requireNonNull(
                command,
                "command must not be null");

        NodeRef correspondence =
                nodeLocator.locate(
                        command.technicalId());

        long currentRevision =
                readRevision(
                        correspondence);

        if (currentRevision
                != command.expectedRevision()) {

            throw new PresidentReviewStateConflictException(
                    "President review state changed before it could be saved. "
                            + "Expected revision "
                            + command.expectedRevision()
                            + " but found "
                            + currentRevision
                            + ".");
        }

        long nextRevision =
                currentRevision + 1L;

        ContentWriter writer =
                contentService.getWriter(
                        correspondence,
                        PROP_PRESIDENT_REVIEW_STATE,
                        true);

        if (writer == null) {
            throw new CorrespondenceRepositoryException(
                    "Alfresco did not provide a writer for President review state.");
        }

        writer.setMimetype(
                "application/json");

        writer.setEncoding(
                "UTF-8");

        writer.putContent(
                command.json());

        nodeService.setProperty(
                correspondence,
                PROP_PRESIDENT_REVIEW_REVISION,
                nextRevision);
        nodeService.setProperty(correspondence, PROP_PRESIDENT_REVIEW_CYCLE,
                new AlfrescoCorrespondenceCycleReader(nodeService).current(correspondence));

        nodeService.setProperty(
                correspondence,
                PROP_PRESIDENT_REVIEW_UPDATED_BY,
                command.actorEmployeeId()
                        .value());

        nodeService.setProperty(
                correspondence,
                PROP_PRESIDENT_REVIEW_UPDATED_AT,
                Date.from(
                        clock.instant()));

        return new PresidentReviewState(
                nextRevision,
                command.json());
    }

    private long readRevision(
            NodeRef correspondence) {

        Serializable value =
                nodeService.getProperty(
                        correspondence,
                        PROP_PRESIDENT_REVIEW_REVISION);

        /*
         * Existing correspondence nodes predate President
         * review persistence, so missing revision means 0.
         */
        if (value == null) {
            return 0L;
        }

        if (!(value instanceof Number number)) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has an invalid President review revision.");
        }

        long revision =
                number.longValue();

        if (revision < 0L) {

            throw new CorrespondenceRepositoryException(
                    "Correspondence has a negative President review revision.");
        }

        return revision;
    }

    private static QName qname(
            String localName) {

        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}
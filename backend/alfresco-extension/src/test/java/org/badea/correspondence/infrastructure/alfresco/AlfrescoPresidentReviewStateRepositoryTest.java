package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.PresidentReviewState;
import org.badea.correspondence.application.PresidentReviewStateConflictException;
import org.badea.correspondence.application.SavePresidentReviewStateCommand;
import org.badea.correspondence.directory.EmployeeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoPresidentReviewStateRepositoryTest {

    private static final String NAMESPACE_URI =
            "http://www.badea.org/model/correspondence/1.0";

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private static final QName STATE_PROPERTY =
            qname(
                    "presidentReviewState");

    private static final QName REVISION_PROPERTY =
            qname(
                    "presidentReviewRevision");

    private static final QName UPDATED_BY_PROPERTY =
            qname(
                    "presidentReviewUpdatedByEmployeeId");

    private static final QName UPDATED_AT_PROPERTY =
            qname(
                    "presidentReviewUpdatedAt");

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-11T08:30:00Z");

    private NodeService nodeService;
    private ContentService contentService;
    private CorrespondenceNodeLocator nodeLocator;

    private NodeRef correspondence;

    private AlfrescoPresidentReviewStateRepository repository;

    @BeforeEach
    void setUp() {

        nodeService =
                mock(
                        NodeService.class);

        contentService =
                mock(
                        ContentService.class);

        nodeLocator =
                mock(
                        CorrespondenceNodeLocator.class);

        correspondence =
                new NodeRef(
                        "workspace://SpacesStore/11111111-1111-1111-1111-111111111111");

        when(nodeLocator.locate(
                TECHNICAL_ID))
                .thenReturn(
                        correspondence);

        repository =
                new AlfrescoPresidentReviewStateRepository(
                        nodeService,
                        contentService,
                        nodeLocator,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC));
    }

    @Test
    void returnsEmptyWhenReviewStateHasNeverBeenSaved() {

        when(contentService.getReader(
                correspondence,
                STATE_PROPERTY))
                .thenReturn(
                        null);

        Optional<PresidentReviewState> result =
                repository.find(
                        TECHNICAL_ID);

        assertTrue(
                result.isEmpty());
    }

    @Test
    void readsExistingReviewState() {

        ContentReader reader =
                mock(
                        ContentReader.class);

        String json =
                """
                {
                  "schemaVersion": 1,
                  "selectedRecipientIds": ["117"]
                }
                """;

        when(contentService.getReader(
                correspondence,
                STATE_PROPERTY))
                .thenReturn(
                        reader);

        when(reader.exists())
                .thenReturn(
                        true);

        when(reader.getContentString())
                .thenReturn(
                        json);

        when(nodeService.getProperty(
                correspondence,
                REVISION_PROPERTY))
                .thenReturn(
                        4L);

        PresidentReviewState result =
                repository.find(
                                TECHNICAL_ID)
                        .orElseThrow();

        assertEquals(
                4L,
                result.revision());

        assertEquals(
                json,
                result.json());
    }

    @Test
    void savesFirstReviewStateAsRevisionOne() {

        ContentWriter writer =
                mock(
                        ContentWriter.class);

        String json =
                """
                {
                  "schemaVersion": 1,
                  "selectedRecipientIds": []
                }
                """;

        when(nodeService.getProperty(
                correspondence,
                REVISION_PROPERTY))
                .thenReturn(
                        null);

        when(contentService.getWriter(
                correspondence,
                STATE_PROPERTY,
                true))
                .thenReturn(
                        writer);

        SavePresidentReviewStateCommand command =
                new SavePresidentReviewStateCommand(
                        TECHNICAL_ID,
                        0L,
                        new EmployeeId(
                                "18280"),
                        json);

        PresidentReviewState result =
                repository.save(
                        command);

        assertEquals(
                1L,
                result.revision());

        assertEquals(
                json,
                result.json());

        verify(writer)
                .setMimetype(
                        "application/json");

        verify(writer)
                .setEncoding(
                        "UTF-8");

        verify(writer)
                .putContent(
                        json);

        verify(nodeService)
                .setProperty(
                        correspondence,
                        REVISION_PROPERTY,
                        1L);

        verify(nodeService)
                .setProperty(
                        correspondence,
                        UPDATED_BY_PROPERTY,
                        "18280");

        verify(nodeService)
                .setProperty(
                        correspondence,
                        UPDATED_AT_PROPERTY,
                        Date.from(
                                NOW));
    }

    @Test
    void rejectsStaleRevisionWithoutWritingAnything() {

        when(nodeService.getProperty(
                correspondence,
                REVISION_PROPERTY))
                .thenReturn(
                        5L);

        SavePresidentReviewStateCommand command =
                new SavePresidentReviewStateCommand(
                        TECHNICAL_ID,
                        4L,
                        new EmployeeId(
                                "18280"),
                        """
                        {
                          "schemaVersion": 1
                        }
                        """);

        assertThrows(
                PresidentReviewStateConflictException.class,
                () -> repository.save(
                        command));

        verify(
                contentService,
                never())
                .getWriter(
                        any(),
                        any(),
                        any(Boolean.class));
    }

    private static QName qname(
            String localName) {

        return QName.createQName(
                NAMESPACE_URI,
                localName);
    }
}
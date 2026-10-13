package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.badea.correspondence.workflow.CorrespondenceState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrespondenceDocumentContentServiceTest {

    private static final String TECHNICAL_ID =
            "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

    private static final String DOCUMENT_ID =
            "22222222-2222-2222-2222-222222222222";

    private static final String UNRELATED_DOCUMENT_ID =
            "33333333-3333-3333-3333-333333333333";

    private CorrespondenceDetailsService detailsService;

    private CorrespondenceDocumentContentRepository
            contentRepository;

    private CorrespondenceDocumentContentService service;

    @BeforeEach
    void setUp() {

        detailsService =
                mock(CorrespondenceDetailsService.class);

        contentRepository =
                mock(CorrespondenceDocumentContentRepository.class);

        service =
                new CorrespondenceDocumentContentService(
                        detailsService,
                        contentRepository);
    }

    @Test
    void opensAuthorizedDocumentBelongingToCorrespondence() {

        CorrespondenceDetails details =
                correspondenceDetails();

        InputStream stream =
                new ByteArrayInputStream(
                        new byte[] { 1, 2, 3 });

        when(detailsService.getDetails(
                TECHNICAL_ID,
                CorrespondenceActorRole.PRESIDENT,
                null))
                .thenReturn(details);

        when(contentRepository.openByNodeId(DOCUMENT_ID))
                .thenReturn(stream);

        CorrespondenceDocumentStream result =
                service.open(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.PRESIDENT,
                        null);

        assertEquals(
                "main-letter.pdf",
                result.filename());

        assertEquals(
                "application/pdf",
                result.mimeType());

        assertSame(stream, result.content());

        verify(contentRepository)
                .openByNodeId(DOCUMENT_ID);
    }

    @Test
    void rejectsDocumentNotBelongingToCorrespondence() {

        when(detailsService.getDetails(
                TECHNICAL_ID,
                CorrespondenceActorRole.PRESIDENT,
                null))
                .thenReturn(correspondenceDetails());

        assertThrows(
                CorrespondenceDocumentNotFoundException.class,
                () -> service.open(
                        TECHNICAL_ID,
                        UNRELATED_DOCUMENT_ID,
                        CorrespondenceActorRole.PRESIDENT,
                        null));

        verify(contentRepository, never())
                .openByNodeId(UNRELATED_DOCUMENT_ID);
    }

    @Test
    void rejectsDocumentAccessWhenCorrespondenceIsUnauthorized() {

        when(detailsService.getDetails(
                TECHNICAL_ID,
                CorrespondenceActorRole.DIRECTOR,
                new DepartmentCode("117")))
                .thenThrow(
                        new CorrespondenceAuthorizationException(
                                "Access denied."));

        assertThrows(
                CorrespondenceAuthorizationException.class,
                () -> service.open(
                        TECHNICAL_ID,
                        DOCUMENT_ID,
                        CorrespondenceActorRole.DIRECTOR,
                        new DepartmentCode("117")));

        verify(contentRepository, never())
                .openByNodeId(DOCUMENT_ID);
    }

    @Test
    void rejectsInvalidDocumentIdentity() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.open(
                        TECHNICAL_ID,
                        "../other-document",
                        CorrespondenceActorRole.PRESIDENT,
                        null));
    }

    private static CorrespondenceDetails correspondenceDetails() {

        return new CorrespondenceDetails(
                TECHNICAL_ID,
                CorrespondenceState.WITH_PRESIDENT,
                "Quarterly Strategy Review",
                null,
                null,
                null,
                null,
                "117",
                "Test Employee",
                null,
                List.of(
                        new CorrespondenceDocument(
                                DOCUMENT_ID,
                                "main-letter.pdf",
                                "application/pdf",
                                4096L)));
    }
}
package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;

import org.badea.correspondence.application.UpdateCorrespondenceDraftCommand;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoCorrespondenceDraftUpdaterTest {

        private static final String TECHNICAL_ID = "4519dccf-c777-4514-b8b8-ac4c713ebd2b";

        private static final String NAMESPACE_URI = "http://www.badea.org/model/correspondence/1.0";

        private NodeService nodeService;
        private PermissionService permissionService;
        private CorrespondenceNodeLocator locator;
        private NodeRef correspondence;
        private AlfrescoCorrespondenceDraftUpdater updater;

        @BeforeEach
        void setUp() {
                nodeService = mock(NodeService.class);
                permissionService = mock(PermissionService.class);
                locator = mock(CorrespondenceNodeLocator.class);

                correspondence = new NodeRef(
                                "workspace://SpacesStore/"
                                                + "11111111-1111-1111-1111-111111111111");

                when(locator.locate(TECHNICAL_ID))
                                .thenReturn(correspondence);

                when(nodeService.getProperty(correspondence, qname("state")))
                                .thenReturn("DRAFT");

                when(nodeService.getProperty(
                                correspondence,
                                qname("originDepartmentCode")))
                                .thenReturn("117");

                when(nodeService.getProperty(
                                correspondence,
                                qname("createdByEmployeeId")))
                                .thenReturn("10001");

                when(permissionService.hasPermission(
                                correspondence,
                                PermissionService.WRITE))
                                .thenReturn(AccessStatus.ALLOWED);

                updater = new AlfrescoCorrespondenceDraftUpdater(
                                nodeService,
                                permissionService,
                                locator);
        }

        @Test
        void creatorCanUpdateDraft() {
                updater.updateDraft(command());

                verify(nodeService).setProperty(
                                correspondence,
                                qname("subject"),
                                "Updated subject");

                verify(nodeService).setProperty(
                                correspondence,
                                qname("bodyText"),
                                "Updated description");

                verify(nodeService).setProperty(
                                correspondence,
                                qname("documentDate"),
                                java.sql.Date.valueOf("2026-10-13"));

                verify(nodeService, times(5))
                                .setProperty(
                                                eq(correspondence),
                                                any(QName.class),
                                                any());
        }

        @Test
        void replacementDirectorInSameDepartmentCanUpdateDraft() {

                UpdateCorrespondenceDraftCommand command = new UpdateCorrespondenceDraftCommand(
                                TECHNICAL_ID,
                                CorrespondenceActorRole.DIRECTOR,
                                new DepartmentCode("117"),
                                new EmployeeId("99999"),
                                "Updated subject",
                                null,
                                null,
                                null,
                                null);

                updater.updateDraft(command);

                verify(nodeService).setProperty(
                                correspondence,
                                qname("subject"),
                                "Updated subject");
        }

        @Test
        void submittedCorrespondenceCannotBeEditedAsDraft() {
                when(nodeService.getProperty(
                                correspondence,
                                qname("state")))
                                .thenReturn("WITH_PRESIDENT_OFFICE");

                assertThrows(
                                CorrespondenceAuthorizationException.class,
                                () -> updater.updateDraft(command()));

                verifyNoWrites();
        }

        @Test
        void directorFromAnotherDepartmentCannotUpdateDraft() {
                when(nodeService.getProperty(
                                correspondence,
                                qname("originDepartmentCode")))
                                .thenReturn("118");

                assertThrows(
                                CorrespondenceAuthorizationException.class,
                                () -> updater.updateDraft(command()));

                verifyNoWrites();
        }

        @Test
        void presidentCannotUseDirectorDraftUpdater() {
                UpdateCorrespondenceDraftCommand command = new UpdateCorrespondenceDraftCommand(
                                TECHNICAL_ID,
                                CorrespondenceActorRole.PRESIDENT,
                                new DepartmentCode("117"),
                                new EmployeeId("10001"),
                                "Updated subject",
                                null,
                                null,
                                null,
                                null);

                assertThrows(
                                CorrespondenceAuthorizationException.class,
                                () -> updater.updateDraft(command));

                verifyNoWrites();
        }

        @Test
        void executiveCreatorCanUpdateOwnDraft() {
                when(nodeService.getProperty(correspondence, qname("createdByCorrespondenceRole")))
                        .thenReturn("PRESIDENT_OFFICE");
                updater.updateDraft(executive(CorrespondenceActorRole.PRESIDENT_OFFICE,
                        "10001"));
                verify(nodeService).setProperty(correspondence, qname("executiveComposeJson"),
                        "{\"recipient\":\"Legal Affairs\"}");
        }

        @Test
        void executiveFromAnotherEmployeeCannotEditDraft() {
                when(nodeService.getProperty(correspondence, qname("createdByCorrespondenceRole")))
                        .thenReturn("PRESIDENT_OFFICE");
                assertThrows(CorrespondenceAuthorizationException.class,
                        () -> updater.updateDraft(executive(
                                CorrespondenceActorRole.PRESIDENT_OFFICE, "99999")));
                verifyNoWrites();
        }

        @Test
        void presidentCannotEditOfficeDraft() {
                when(nodeService.getProperty(correspondence, qname("createdByCorrespondenceRole")))
                        .thenReturn("PRESIDENT_OFFICE");
                assertThrows(CorrespondenceAuthorizationException.class,
                        () -> updater.updateDraft(executive(CorrespondenceActorRole.PRESIDENT,
                                "10001")));
                verifyNoWrites();
        }

        @Test
        void executiveCannotEditSubmittedDraft() {
                when(nodeService.getProperty(correspondence, qname("state")))
                        .thenReturn("WITH_PRESIDENT_OFFICE");
                when(nodeService.getProperty(correspondence, qname("createdByCorrespondenceRole")))
                        .thenReturn("PRESIDENT");
                assertThrows(CorrespondenceAuthorizationException.class,
                        () -> updater.updateDraft(executive(CorrespondenceActorRole.PRESIDENT,
                                "10001")));
                verifyNoWrites();
        }

        private UpdateCorrespondenceDraftCommand executive(
                CorrespondenceActorRole role, String employeeId) {
                return new UpdateCorrespondenceDraftCommand(
                        TECHNICAL_ID, role, new DepartmentCode("117"),
                        new EmployeeId(employeeId), "Updated subject", null, null,
                        null, null, "{\"recipient\":\"Legal Affairs\"}");
        }

        @Test
        void alfrescoWritePermissionIsRequired() {
                when(permissionService.hasPermission(
                                correspondence,
                                PermissionService.WRITE))
                                .thenReturn(AccessStatus.DENIED);

                assertThrows(
                                CorrespondenceAuthorizationException.class,
                                () -> updater.updateDraft(command()));

                verifyNoWrites();
        }

        @Test
        void optionalFieldsCanBeCleared() {
                UpdateCorrespondenceDraftCommand command = new UpdateCorrespondenceDraftCommand(
                                TECHNICAL_ID,
                                CorrespondenceActorRole.DIRECTOR,
                                new DepartmentCode("117"),
                                new EmployeeId("10001"),
                                "Updated subject",
                                "",
                                "",
                                null,
                                "");

                updater.updateDraft(command);

                verify(nodeService).setProperty(
                                correspondence,
                                qname("bodyText"),
                                null);

                verify(nodeService).setProperty(
                                correspondence,
                                qname("documentNumber"),
                                null);

                verify(nodeService).setProperty(
                                correspondence,
                                qname("documentDate"),
                                null);

                verify(nodeService).setProperty(
                                correspondence,
                                qname("sourceText"),
                                null);
        }

        private UpdateCorrespondenceDraftCommand command() {
                return new UpdateCorrespondenceDraftCommand(
                                TECHNICAL_ID,
                                CorrespondenceActorRole.DIRECTOR,
                                new DepartmentCode("117"),
                                new EmployeeId("10001"),
                                "Updated subject",
                                "Updated description",
                                "DOC-2026-001",
                                LocalDate.of(2026, 10, 13),
                                "BADEA");
        }

        private void verifyNoWrites() {
                verify(nodeService, never()).setProperty(
                                eq(correspondence),
                                any(QName.class),
                                any());
        }

        private static QName qname(String name) {
                return QName.createQName(NAMESPACE_URI, name);
        }
}
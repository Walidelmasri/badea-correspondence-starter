package org.badea.correspondence.infrastructure.alfresco;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.*;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.application.*;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlfrescoOfficeCompletionRepositoryTest {
    private NodeService nodes;
    private ContentService content;
    private PermissionService permissions;
    private CorrespondenceNodeLocator locator;
    private AlfrescoOfficeCompletionRepository repository;
    private NodeRef parent;
    private ContentWriter writer;
    private static final String ID="453ae576-a392-4150-ac69-c532f9487a23";
    private static final String NS="http://www.badea.org/model/correspondence/1.0";
    @BeforeEach void setup() {
        nodes=mock(NodeService.class); content=mock(ContentService.class);
        permissions=mock(PermissionService.class); locator=mock(CorrespondenceNodeLocator.class);
        parent=new NodeRef("workspace://SpacesStore/11111111-1111-1111-1111-111111111111");
        writer=mock(ContentWriter.class);
        when(locator.locate(ID)).thenReturn(parent);
        when(nodes.getType(parent)).thenReturn(q("correspondence"));
        when(permissions.hasPermission(parent,PermissionService.READ)).thenReturn(AccessStatus.ALLOWED);
        when(permissions.hasPermission(parent,PermissionService.WRITE)).thenReturn(AccessStatus.ALLOWED);
        repository=new AlfrescoOfficeCompletionRepository(nodes,content,permissions,locator,
                Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"),ZoneOffset.UTC));
    }
    @Test void newOfficeCompletionHasNoHistory() { assertTrue(repository.find(ID).isEmpty()); }
    @Test void deniesMissingAlfrescoWritePermission() {
        when(permissions.hasPermission(parent,PermissionService.WRITE)).thenReturn(AccessStatus.DENIED);
        assertThrows(CorrespondenceAuthorizationException.class,()->repository.save(command()));
        verify(nodes,never()).createNode(any(),any(),any(),any(),anyMap());
    }
    @Test void rejectsStaleRevisionBeforeCreatingAuditNode() {
        when(nodes.getProperty(parent,q("officeCompletionCurrentRevision"))).thenReturn(3L);
        assertThrows(OfficeCompletionStateConflictException.class,()->repository.save(command()));
        verify(nodes,never()).createNode(any(),any(),any(),any(),anyMap());
    }
    @Test void createsAppendOnlyRevisionAndAdvancesHead() {
        NodeRef audit=new NodeRef("workspace://SpacesStore/22222222-2222-2222-2222-222222222222");
        ChildAssociationRef child=mock(ChildAssociationRef.class);
        when(child.getChildRef()).thenReturn(audit);
        when(nodes.createNode(eq(parent),eq(ContentModel.ASSOC_CONTAINS),
                eq(q("office-completion-revision-1")),eq(q("officeCompletionSnapshot")),anyMap()))
                .thenReturn(child);
        when(content.getWriter(audit,ContentModel.PROP_CONTENT,true)).thenReturn(writer);
        OfficeCompletionState result=repository.save(command());
        assertEquals(1L,result.revision());
        verify(writer).putContent("{\"schemaVersion\":1}");
        verify(nodes).setProperty(parent,q("officeCompletionCurrentRevision"),1L);
    }
    private static QName q(String s) {return QName.createQName(NS,s);}
    private static SaveOfficeCompletionCommand command() {
        return new SaveOfficeCompletionCommand(ID,0,3,new EmployeeId("99999"),
                "Office User",null,"{\"schemaVersion\":1}");
    }
}

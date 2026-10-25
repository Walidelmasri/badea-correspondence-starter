package org.badea.correspondence.infrastructure.alfresco;

import org.alfresco.model.QuickShareModel;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.cmr.quickshare.QuickShareService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

final class AlfrescoCorrespondenceSharedLinkRepositoryTest {
    @Test void refusesRevokeIfNativeAlfrescoSaysNo() {
        NodeService nodes = mock(NodeService.class);
        QuickShareService links = mock(QuickShareService.class);
        String id = "f1bc7ab2-b55b-48ab-ae18-676e35615d74";
        NodeRef ref = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        when(nodes.getProperty(ref, QuickShareModel.PROP_QSHARE_SHAREDID)).thenReturn("shared_token_12");
        when(nodes.getProperty(ref, QuickShareModel.PROP_QSHARE_SHAREDBY)).thenReturn("originator");
        when(links.canDeleteSharedLink(ref, "originator")).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> new AlfrescoCorrespondenceSharedLinkRepository(nodes, links).revoke(id));
        verify(links, never()).unshareContent(anyString());
    }

    @Test void allowsRevokeIfNativeAlfrescoApproves() {
        NodeService nodes = mock(NodeService.class);
        QuickShareService links = mock(QuickShareService.class);
        String id = "f1bc7ab2-b55b-48ab-ae18-676e35615d74";
        NodeRef ref = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
        when(nodes.getProperty(ref, QuickShareModel.PROP_QSHARE_SHAREDID)).thenReturn("shared_token_12");
        when(nodes.getProperty(ref, QuickShareModel.PROP_QSHARE_SHAREDBY)).thenReturn("originator");
        when(links.canDeleteSharedLink(ref, "originator")).thenReturn(true);
        new AlfrescoCorrespondenceSharedLinkRepository(nodes, links).revoke(id);
        verify(links).unshareContent("shared_token_12");
    }
}

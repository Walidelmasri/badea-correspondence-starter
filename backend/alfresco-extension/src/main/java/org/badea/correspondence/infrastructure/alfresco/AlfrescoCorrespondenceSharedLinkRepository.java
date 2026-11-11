package org.badea.correspondence.infrastructure.alfresco;

import java.util.Objects;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.model.QuickShareModel;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.cmr.quickshare.QuickShareService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.badea.correspondence.application.CorrespondenceSharedLinkRepository;

/** Privileged QuickShare creation/read is invoked only after validated case+PDF access.
 * Native unshare permission remains enforced on revocation.
 */
public final class AlfrescoCorrespondenceSharedLinkRepository
        implements CorrespondenceSharedLinkRepository {
    private final NodeService nodes;
    private final QuickShareService quickShare;

    public AlfrescoCorrespondenceSharedLinkRepository(
            NodeService nodes, QuickShareService quickShare) {
        this.nodes = Objects.requireNonNull(nodes);
        this.quickShare = Objects.requireNonNull(quickShare);
    }

    @Override public String getOrCreate(String nodeId) {
        NodeRef node = nodeRef(nodeId);
        return AuthenticationUtil.runAsSystem(() -> {
            String existing = (String) nodes.getProperty(node, QuickShareModel.PROP_QSHARE_SHAREDID);
            return existing != null && !existing.isBlank()
                    ? existing : quickShare.shareContent(node, null).getId();
        });
    }

    @Override public String requireExisting(String nodeId) {
        String id = AuthenticationUtil.runAsSystem(() ->
                (String) nodes.getProperty(nodeRef(nodeId), QuickShareModel.PROP_QSHARE_SHAREDID));
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Create a share link before emailing it.");
        }
        return id;
    }

    @Override public java.util.Optional<String> findExisting(String nodeId) {
        String id = AuthenticationUtil.runAsSystem(() ->
                (String) nodes.getProperty(nodeRef(nodeId), QuickShareModel.PROP_QSHARE_SHAREDID));
        return id == null || id.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(id);
    }

    @Override public void revoke(String nodeId) {
        NodeRef node = nodeRef(nodeId);
        String existing = requireExisting(nodeId);
        // The calling ShareService has already validated the user's read access
        // to this exact correspondence PDF before invoking this operation.
        AuthenticationUtil.runAsSystem(() -> {
            String sharedBy = (String) nodes.getProperty(node, QuickShareModel.PROP_QSHARE_SHAREDBY);
            if (!quickShare.canDeleteSharedLink(node, sharedBy)) {
                throw new AccessDeniedException("Alfresco denied share-link revocation.");
            }
            quickShare.unshareContent(existing);
            return null;
        });
    }

    private static NodeRef nodeRef(String nodeId) {
        return new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, nodeId);
    }
}

package org.badea.correspondence.infrastructure.alfresco;

import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.reference.CorrespondenceReference;

/**
 * Reserves a department-local sequence by creating a uniquely named child node.
 * Must run within the SAME required Alfresco transaction as DRAFT submission.
 * Concurrent duplicate association names must cause one transaction to fail:
 * never swallow a duplicate-creation failure or retry inside the same transaction.
 * The caller can safely retry after reloading the correspondence state.
 */
final class AlfrescoCorrespondenceReferenceAllocator {
    private static final String NS = "http://www.badea.org/model/correspondence/1.0";
    private static final String PREFIX = "ref-sequence-";
    private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Riyadh");
    private final NodeService nodes;
    private final CorrespondenceFolderLocator folders;
    private final Clock clock;

    AlfrescoCorrespondenceReferenceAllocator(NodeService nodes,
            CorrespondenceFolderLocator folders, Clock clock) {
        this.nodes = Objects.requireNonNull(nodes);
        this.folders = Objects.requireNonNull(folders);
        this.clock = Objects.requireNonNull(clock);
    }

    String reserve(DepartmentCode departmentCode) {
        NodeRef folder = folders.locateDepartmentFolder(departmentCode);
        long greatest = 0;
        for (ChildAssociationRef child : nodes.getChildAssocs(folder)) {
            if (!ContentModel.ASSOC_CONTAINS.equals(child.getTypeQName())) continue;
            String name = child.getQName().getLocalName();
            if (!name.startsWith(PREFIX)) continue;
            String suffix = name.substring(PREFIX.length());
            if (!suffix.matches("[1-9][0-9]*")) continue;
            try { greatest = Math.max(greatest, Long.parseLong(suffix)); }
            catch (NumberFormatException invalid) { throw new CorrespondenceRepositoryException("Invalid reference sequence reservation"); }
        }
        if (greatest == Long.MAX_VALUE) throw new CorrespondenceRepositoryException("Reference counter exhausted");
        long next = greatest + 1;
        String nodeName = PREFIX + next;
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, nodeName);
        // Alfresco's primary child association uniqueness is the concurrency fence.
        // Rollback of the required transaction rolls back this reservation too.
        ChildAssociationRef reserved = nodes.createNode(folder, ContentModel.ASSOC_CONTAINS,
                QName.createQName(NS, nodeName), ContentModel.TYPE_FOLDER, properties);
        if (reserved == null || reserved.getChildRef() == null)
            throw new CorrespondenceRepositoryException("Reference reservation not persisted");
        LocalDate day = LocalDate.ofInstant(clock.instant(), BUSINESS_TIME_ZONE);
        return new CorrespondenceReference(departmentCode.value(), day, next).toString();
    }
}

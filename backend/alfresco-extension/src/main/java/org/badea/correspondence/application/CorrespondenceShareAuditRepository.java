package org.badea.correspondence.application;

/** Append-only Alfresco correspondence share audit; independent of workflow history. */
public interface CorrespondenceShareAuditRepository {
    record Entry(String technicalId, String nodeId, String event, String actorEmployeeId,
            String recipient, String sharedId, String comment) {}
    void append(Entry entry);
}

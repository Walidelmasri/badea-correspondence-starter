package org.badea.correspondence.application;

/** Native Alfresco public links; authorization is enforced by the caller first. */
public interface CorrespondenceSharedLinkRepository {
    String getOrCreate(String documentNodeId);
    String requireExisting(String documentNodeId);
    void revoke(String documentNodeId);
}

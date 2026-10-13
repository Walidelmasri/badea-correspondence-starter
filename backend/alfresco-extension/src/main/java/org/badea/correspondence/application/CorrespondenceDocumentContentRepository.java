package org.badea.correspondence.application;

import java.io.InputStream;

public interface CorrespondenceDocumentContentRepository {

    InputStream openByNodeId(String nodeId);
}
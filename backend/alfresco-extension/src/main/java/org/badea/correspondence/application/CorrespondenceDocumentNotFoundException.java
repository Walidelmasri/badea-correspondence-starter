package org.badea.correspondence.application;

public final class CorrespondenceDocumentNotFoundException
        extends RuntimeException {

    public CorrespondenceDocumentNotFoundException() {
        super("The requested correspondence document was not found.");
    }
}
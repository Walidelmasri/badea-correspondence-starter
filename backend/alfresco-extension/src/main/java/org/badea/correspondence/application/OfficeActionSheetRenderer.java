package org.badea.correspondence.application;

import java.util.List;
import org.badea.correspondence.directory.DepartmentDirectoryEntry;

/** PDF rendering port; no repository or role decisions in the renderer. */
public interface OfficeActionSheetRenderer {
    byte[] render(CorrespondenceDetails correspondence, OfficeDistributionPlan plan,
            List<DepartmentDirectoryEntry> to, List<DepartmentDirectoryEntry> copy,
            OfficeActionSheetPrintContext printContext);
}

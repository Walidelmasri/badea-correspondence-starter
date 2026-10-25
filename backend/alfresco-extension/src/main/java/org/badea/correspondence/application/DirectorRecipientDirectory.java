package org.badea.correspondence.application;

import java.util.List;
import java.util.Map;
import org.badea.correspondence.directory.DepartmentCode;

/** Resolves verified current Director names for an official Office distribution. */
public interface DirectorRecipientDirectory {
    Map<DepartmentCode, String> requireDirectorNames(List<DepartmentCode> departments);
}

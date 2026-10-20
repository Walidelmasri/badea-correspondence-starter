package org.badea.correspondence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.badea.correspondence.directory.DepartmentCode;
import org.badea.correspondence.directory.EmployeeId;
import org.badea.correspondence.security.CorrespondenceAuthorizationException;
import org.badea.correspondence.workflow.CorrespondenceActorRole;
import org.junit.jupiter.api.Test;

class DepartmentAssignmentServiceTest {
    @Test void copyRecipientCannotCompleteAnotherDepartmentAction() {
        var correspondence=mock(CorrespondenceTransitionRepository.class);
        var storage=mock(OfficeDistributionRepository.class);
        var service=new DepartmentAssignmentService(correspondence,new CorrespondenceAccessAuthorizer(),storage);
        var copy=new DepartmentAssignment("copy","id",new DepartmentCode("117"),
                DepartmentRecipientType.COPY,DepartmentAssignmentStatus.PENDING,"office",Instant.now(),null,null,null);
        when(storage.assignments("id")).thenReturn(List.of(copy));
        assertThrows(CorrespondenceAuthorizationException.class,()->service.complete("id","copy",
                CorrespondenceActorRole.DIRECTOR,new DepartmentCode("117"),new EmployeeId("7"),"Director","Response"));
        verify(storage,never()).complete(any(),any(),any(),any(),any(),any());
    }
}

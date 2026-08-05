package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.ResolvedSourceFile;
import java.io.IOException;

/** Platform boundary for opening a source and reporting evidence about that open handle. */
@FunctionalInterface
interface SourceReadOperations {
    SourceReadHandle open(ApprovedSourceRoots roots, ResolvedSourceFile planned)
            throws IOException;
}

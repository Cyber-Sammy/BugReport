package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.ResolvedSourceFile;
import com.cybersammy.bugreport.core.source.SourcePathResolver;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Java NIO source opener with Windows handle stabilization and a portable fallback. */
final class NioSourceReadOperations implements SourceReadOperations {
    static final NioSourceReadOperations INSTANCE = new NioSourceReadOperations();

    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .startsWith("windows");

    private NioSourceReadOperations() {}

    @Override
    public SourceReadHandle open(
            ApprovedSourceRoots roots, ResolvedSourceFile planned) throws IOException {
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        options.add(LinkOption.NOFOLLOW_LINKS);
        SourceReadIdentityAssurance assurance = SourceReadIdentityAssurance.PATH_REVALIDATED;
        if (WINDOWS) {
            options.add(ExtendedOpenOption.NOSHARE_DELETE);
            assurance = SourceReadIdentityAssurance.HANDLE_STABILIZED;
        }

        FileChannel channel = FileChannel.open(planned.localPath(), options);
        try {
            ResolvedSourceFile opened = SourcePathResolver.resolveRegularFile(
                    roots, planned.root(), planned.relativePath());
            return new SourceReadHandle(channel, opened, assurance);
        } catch (RuntimeException exception) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }
}

package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.source.ResolvedSourceFile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Objects;

/** Open source channel paired with the strongest identity evidence from its opener. */
record SourceReadHandle(
        FileChannel channel,
        ResolvedSourceFile openedFile,
        SourceReadIdentityAssurance identityAssurance)
        implements AutoCloseable {
    SourceReadHandle {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(openedFile, "openedFile");
        Objects.requireNonNull(identityAssurance, "identityAssurance");
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}

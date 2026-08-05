package com.cybersammy.bugreport.core.workspace;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Narrow filesystem boundary used to test creation failure and rollback behavior. */
interface WorkspaceFileOperations {
    BasicFileAttributes readAttributes(Path path, boolean followLinks) throws IOException;

    Path realPath(Path path, boolean followLinks) throws IOException;

    FileStore fileStore(Path path) throws IOException;

    void createPrivateDirectory(Path path) throws IOException;

    void writeNewPrivateMarker(Path path, byte[] contents) throws IOException;

    byte[] readBounded(Path path, int maximumBytes) throws IOException;

    boolean deleteIfExists(Path path) throws IOException;
}

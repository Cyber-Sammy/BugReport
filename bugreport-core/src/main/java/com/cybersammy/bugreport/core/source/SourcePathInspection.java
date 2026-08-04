package com.cybersammy.bugreport.core.source;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Internal boundary around filesystem observations that may change between calls. */
interface SourcePathInspection {
    BasicFileAttributes readAttributes(Path path, boolean followLinks) throws IOException;

    Path realPath(Path path, boolean followLinks) throws IOException;

    FileStore fileStore(Path path) throws IOException;
}

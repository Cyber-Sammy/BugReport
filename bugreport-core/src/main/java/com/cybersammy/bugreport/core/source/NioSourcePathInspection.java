package com.cybersammy.bugreport.core.source;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Production NIO implementation of source-path observations. */
final class NioSourcePathInspection implements SourcePathInspection {
    static final NioSourcePathInspection INSTANCE = new NioSourcePathInspection();

    private NioSourcePathInspection() {}

    @Override
    public BasicFileAttributes readAttributes(Path path, boolean followLinks) throws IOException {
        return Files.readAttributes(
                path,
                BasicFileAttributes.class,
                followLinks ? new LinkOption[0] : new LinkOption[] {LinkOption.NOFOLLOW_LINKS});
    }

    @Override
    public Path realPath(Path path, boolean followLinks) throws IOException {
        return followLinks
                ? path.toRealPath()
                : path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public FileStore fileStore(Path path) throws IOException {
        return Files.getFileStore(path);
    }
}

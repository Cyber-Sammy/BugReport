package com.cybersammy.bugreport.core.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Production NIO operations for report-workspace creation. */
final class NioWorkspaceFileOperations implements WorkspaceFileOperations {
    static final NioWorkspaceFileOperations INSTANCE = new NioWorkspaceFileOperations();

    private NioWorkspaceFileOperations() {}

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

    @Override
    public void createDirectory(Path path) throws IOException {
        Files.createDirectory(path);
    }

    @Override
    public void writeNewMarker(Path path, byte[] contents) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(contents);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    @Override
    public byte[] readBounded(Path path, int maximumBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return input.readNBytes(maximumBytes + 1);
        }
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return Files.deleteIfExists(path);
    }
}

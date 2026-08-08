package com.cybersammy.bugreport.core.history;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Atomic, bounded history storage below one existing platform-trusted directory. */
public final class FileReportHistoryStore {
    public static final String HISTORY_FILENAME = "history.json";
    private final Path directory;
    private final Path file;

    public FileReportHistoryStore(Path trustedDirectory) {
        directory = Objects.requireNonNull(trustedDirectory, "trustedDirectory").toAbsolutePath().normalize();
        requireDirectory(directory);
        file = directory.resolve(HISTORY_FILENAME).normalize();
        if (!directory.equals(file.getParent())) throw new IllegalArgumentException("History filename escaped its directory");
    }

    /** Returns an empty recovered index for malformed bytes, preserving the file for manual recovery. */
    public synchronized HistoryIndexLoad load() {
        requireDirectory(directory);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new HistoryIndexLoad(ReportHistoryIndex.empty(), false);
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return new HistoryIndexLoad(ReportHistoryIndex.empty(), true);
        try { DecodedHistoryIndex decoded=ReportHistoryJsonCodec.decodeRecovering(readBounded()); return new HistoryIndexLoad(decoded.index(), decoded.skippedEntries()>0); }
        catch (IOException | IllegalArgumentException exception) { return new HistoryIndexLoad(ReportHistoryIndex.empty(), true); }
    }

    /** Atomically persists one canonical index; a failed replacement leaves the previous file untouched. */
    public synchronized void save(ReportHistoryIndex index) {
        byte[] encoded=ReportHistoryJsonCodec.encode(Objects.requireNonNull(index,"index")); requireDirectory(directory);
        if (Files.exists(file,LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))) throw new IllegalStateException("History path is unsafe");
        Path temporary=null;
        try { temporary=Files.createTempFile(directory,".bugreport-history-",".tmp"); try(FileChannel c=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){ByteBuffer b=ByteBuffer.wrap(encoded);while(b.hasRemaining())c.write(b);c.force(true);} Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); temporary=null; }
        catch(AtomicMoveNotSupportedException exception){throw new IllegalStateException("History storage does not support atomic replacement",exception);}
        catch(IOException exception){throw new IllegalStateException("Could not persist report history",exception);}
        finally { if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){} }
    }
    private byte[] readBounded()throws IOException{if(Files.size(file)>ReportHistoryJsonCodec.MAX_ENCODED_BYTES)throw new HistoryFormatException("History file exceeds storage bound");try(InputStream in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] b=in.readNBytes(ReportHistoryJsonCodec.MAX_ENCODED_BYTES+1);if(b.length>ReportHistoryJsonCodec.MAX_ENCODED_BYTES)throw new HistoryFormatException("History file exceeds storage bound");return b;}}
    private static void requireDirectory(Path path){try{if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("History directory must be real");Path current=path.getRoot();for(Path part:path){current=current.resolve(part);if(Files.isSymbolicLink(current))throw new IllegalArgumentException("History directory must not traverse symbolic links");}if(!path.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(path.toRealPath()))throw new IllegalArgumentException("History directory must not traverse filesystem redirection");}catch(IOException exception){throw new IllegalArgumentException("History directory must be trusted",exception);}}
}

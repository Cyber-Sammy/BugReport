package com.cybersammy.bugreport.core.packaging;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Objects;

/** Supported filesystem mechanisms for proving owner-only access to a report archive. */
enum PrivateArchivePermissionModel {
    POSIX,
    ACL;

    static PrivateArchivePermissionModel select(FileStore store) throws IOException {
        Objects.requireNonNull(store, "store");
        return select(
                store.supportsFileAttributeView(PosixFileAttributeView.class),
                store.supportsFileAttributeView(AclFileAttributeView.class));
    }

    static PrivateArchivePermissionModel select(boolean supportsPosix, boolean supportsAcl)
            throws IOException {
        if (supportsPosix) {
            return POSIX;
        }
        if (supportsAcl) {
            return ACL;
        }
        throw new IOException("Filesystem does not support a private archive permission model");
    }
}

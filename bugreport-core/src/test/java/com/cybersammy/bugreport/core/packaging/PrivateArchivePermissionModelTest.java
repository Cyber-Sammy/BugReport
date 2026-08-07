package com.cybersammy.bugreport.core.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

final class PrivateArchivePermissionModelTest {
    @Test
    void selectsPosixWhenItIsAvailable() throws Exception {
        assertEquals(PrivateArchivePermissionModel.POSIX,
                PrivateArchivePermissionModel.select(true, true));
    }

    @Test
    void selectsAclWhenPosixIsUnavailable() throws Exception {
        assertEquals(PrivateArchivePermissionModel.ACL,
                PrivateArchivePermissionModel.select(false, true));
    }

    @Test
    void rejectsFilesystemWithoutPrivatePermissionModel() {
        IOException failure = assertThrows(
                IOException.class, () -> PrivateArchivePermissionModel.select(false, false));

        assertEquals(
                "Filesystem does not support a private archive permission model",
                failure.getMessage());
    }
}

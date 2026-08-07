package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Exact deterministic identity used only to bind transport consent to package bytes. */
record PackagePlanFingerprint(Sha256Checksum checksum) {
    static PackagePlanFingerprint of(ReportPackagePlan plan) {
        ReportPackagePlan trustedPlan = Objects.requireNonNull(plan, "plan");
        MessageDigest digest = sha256();
        trustedPlan.entries().forEach(entry -> {
            update(digest, entry.archivePath());
            update(digest, entry.kind().name());
            update(digest, Long.toString(entry.uncompressedBytes()));
            update(digest, entry.checksum().value());
            update(digest, entry.workspaceArtifactName().orElse(""));
        });
        return new PackagePlanFingerprint(
                new Sha256Checksum(HexFormat.of().formatHex(digest.digest())));
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        }
    }
}

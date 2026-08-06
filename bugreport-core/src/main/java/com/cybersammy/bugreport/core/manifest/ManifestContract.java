package com.cybersammy.bugreport.core.manifest;

import java.util.Objects;
import java.util.regex.Pattern;

final class ManifestContract {
    private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,127}");
    private static final Pattern MEDIA_TYPE =
            Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*");
    private static final Pattern ARCHIVE_PATH =
            Pattern.compile("content/[a-z0-9][a-z0-9._-]{0,159}");

    private ManifestContract() {}

    static String requireToken(String value, String description) {
        String validated = Objects.requireNonNull(value, description);
        if (!TOKEN.matcher(validated).matches()) {
            throw new IllegalArgumentException(description + " is not canonical");
        }
        return validated;
    }

    static String requireVersion(String value, String description) {
        String validated = Objects.requireNonNull(value, description);
        if (!VERSION.matcher(validated).matches()) {
            throw new IllegalArgumentException(description + " is not a bounded version");
        }
        return validated;
    }

    static String requireMediaType(String value) {
        String validated = Objects.requireNonNull(value, "mediaType");
        if (!MEDIA_TYPE.matcher(validated).matches()) {
            throw new IllegalArgumentException("Manifest media type is not canonical");
        }
        return validated;
    }

    static String requireArchivePath(String value) {
        String validated = Objects.requireNonNull(value, "archivePath");
        if (!ARCHIVE_PATH.matcher(validated).matches()) {
            throw new IllegalArgumentException("Manifest archive path is not canonical");
        }
        return validated;
    }
}

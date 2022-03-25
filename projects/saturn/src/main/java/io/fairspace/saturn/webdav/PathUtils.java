package io.fairspace.saturn.webdav;

import static org.apache.commons.lang3.StringUtils.strip;
import static org.apache.http.client.utils.URLEncodedUtils.formatSegments;

public class PathUtils {
    public static final int MAX_COLLECTION_NAME_LENGTH = 127;

    public static String normalizePath(String path) {
        return strip(path, "/");
    }

    public static String encodePath(String path) {
        return normalizePath(formatSegments(splitPath(path)));
    }

    public static String[] splitPath(String path) {
        return normalizePath(path).split("/");
    }

    public static String name(String path) {
        var parts = splitPath(path);
        return (parts.length == 0) ? "" :  parts[parts.length - 1];
    }
}

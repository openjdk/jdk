/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.net.http;

import java.io.IOException;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a form field used by {@link MultipartFormData}.
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc7578/">RFC 7578</a>
 */
public final class FormField {

    private final String name;
    private final BodyPublisher value;
    private final String contentType;
    private final String filename;

    public static FormField ofValue(String name, String value) {
        return new FormField(name, BodyPublishers.ofString(value), null, null);
    }

    public static FormField ofValue(String name, BodyPublisher value) {
        return new FormField(name, value, null, null);
    }

    public static FormField ofValue(String name, BodyPublisher value, String contentType, String filename) {
        return new FormField(name, value, contentType, filename);
    }

    public static FormField ofFile(String name, Path path) throws IOException {

        Objects.requireNonNull(path);
        Objects.requireNonNull(path.getFileName());

        BodyPublisher value = BodyPublishers.ofFile(path);
        String filename = path.getFileName().toString();
        String contentType = Files.probeContentType(path);
        if (!isOnlyAsciiPrintable(contentType)) {
            contentType = "application/octet-stream"; // unusable file type -> generic file type
        }

        return new FormField(name, value, contentType, filename);
    }

    private FormField(String name, BodyPublisher value, String contentType, String filename) {

        if (!isOnlyAsciiPrintable(contentType)) {
            throw new IllegalArgumentException("Invalid content type"); // similar to browser validation
        }
        if (contentType != null && contentType.isEmpty()) {
            contentType = null;
        }
        if (contentType == null && filename != null) {
            contentType = "application/octet-stream"; // generic file type
        }

        this.name = Objects.requireNonNull(name);
        this.value = Objects.requireNonNull(value);
        this.contentType = contentType;
        this.filename = filename;
    }

    public String name() {
        return name;
    }

    public BodyPublisher value() {
        return value;
    }

    public String contentType() {
        return contentType;
    }

    public String filename() {
        return filename;
    }

    @Override
    public String toString() {
        return name;
    }

    private static boolean isOnlyAsciiPrintable(String s) {
        if (s != null) {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                char ch = s.charAt(i);
                if (ch < 32 || ch > 126) {
                    return false;
                }
            }
        }
        return true;
    }
}

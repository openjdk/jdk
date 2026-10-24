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

import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.Objects;
import java.util.UUID;

/**
 * Supports sending {@code multipart/form-data} HTTP requests.
 *
 * <p>The following example sends a file in the request:
 *
 * {@snippet :
 *   Path path = Path.of("file.pdf");
 *   FormField field = FormField.ofFile("my-file", path);
 *   MultipartFormData form = new MultipartFormData(field);
 *   HttpRequest request = HttpRequest.newBuilder()
 *       .uri(URI.create("https://example.com/"))
 *       .header("Content-Type", form.contentType())
 *       .POST(form.body())
 *       .build(); }
 *
 * <p>The following example sends non-file fields in the request:
 *
 * {@snippet :
 *   MultipartFormData form = new MultipartFormData(
 *       FormField.ofValue("name", "John Doe"),
 *       FormField.ofValue("age", "42"),
 *       FormField.ofValue("active", "true"));
 *   HttpRequest request = HttpRequest.newBuilder()
 *       .uri(URI.create("https://example.com/"))
 *       .header("Content-Type", form.contentType())
 *       .POST(form.body())
 *       .build(); }
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc7578/">RFC 7578</a>
 */
public final class MultipartFormData {

    private final String contentType;
    private final BodyPublisher body;

    public MultipartFormData(FormField... fields) {

        Objects.requireNonNull(fields);

        int i = 0;
        int size = 1 + (fields.length * 2);
        BodyPublisher[] publishers = new BodyPublisher[size];
        String boundary = "----JavaFormBoundary-" + UUID.randomUUID();

        for (FormField f : fields) {
            BodyPublisher header = BodyPublishers.ofString(
                (i > 0 ? "\r\n" : "") +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"" + escapeName(f.name()) + "\"" +
                (f.filename() != null ? "; filename=\"" + escapeName(f.filename()) + "\"" : "") + "\r\n" +
                (f.contentType() != null ? "Content-Type: " + f.contentType() + "\r\n" : "") +
                "\r\n");
            publishers[i++] = header;
            publishers[i++] = f.value();
        }

        publishers[i] = BodyPublishers.ofString((i > 0 ? "\r\n" : "") + "--" + boundary + "--\r\n");

        this.contentType = "multipart/form-data; boundary=" + boundary;
        this.body = BodyPublishers.concat(publishers);
    }

    public String contentType() {
        return contentType;
    }

    public BodyPublisher body() {
        return body;
    }

    private static String escapeName(String name) {
        // matches browser escaping behavior
        return name.replace("\"", "%22")
                   .replace("\r", "%0D")
                   .replace("\n", "%0A");
    }
}

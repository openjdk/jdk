/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313887 8129632 8129633
 * @summary Unit test for probeContentType method
 * @library ../..
 * @build Basic SimpleFileTypeDetector
 * @run main/othervm Basic
 */

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Uses Files.probeContentType to probe html file, custom file type, and minimal
 * set of file extension to content type mappings.
 */
public class Basic {

    private static final boolean IS_UNIX =
        ! System.getProperty("os.name").startsWith("Windows");

    static Path createHtmlFile() throws IOException {
        Path file = Files.createTempFile("foo", ".html");
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write("<html><body>foo</body></html>".getBytes());
        }

        return file;
    }

    static Path createGrapeFile() throws IOException {
        return Files.createTempFile("red", ".grape");
    }

    private static int checkContentTypes(String expected, String actual) {
        assert expected != null;
        assert actual != null;

        if (!expected.equals(actual)) {
            if (IS_UNIX) {
                Path userMimeTypes =
                    Paths.get(System.getProperty("user.home"), ".mime.types");
                if (!Files.exists(userMimeTypes)) {
                    System.out.println(userMimeTypes + " does not exist");
                } else if (!Files.isReadable(userMimeTypes)) {
                    System.out.println(userMimeTypes + " is not readable");
                } else {
                    System.out.println(userMimeTypes + " contents:");
                    try (Stream<String> lines = Files.lines(userMimeTypes)) {
                        lines.forEach(System.out::println);
                        System.out.println("");
                    } catch (IOException ioe) {
                        System.err.println("Problem reading "
                                           + userMimeTypes);
                    }
                }

                Path etcMimeTypes = Paths.get("/etc/mime.types");
                if (!Files.exists(etcMimeTypes)) {
                    System.out.println(etcMimeTypes + " does not exist");
                } else if (!Files.isReadable(etcMimeTypes)) {
                    System.out.println(etcMimeTypes + " is not readable");
                } else {
                    System.out.println(etcMimeTypes + " contents:");
                    try (Stream<String> lines = Files.lines(etcMimeTypes)) {
                        lines.forEach(System.out::println);
                        System.out.println("");
                    } catch (IOException ioe) {
                        System.err.println("Problem reading "
                                           + etcMimeTypes);
                    }
                }
            }

            System.err.println("Expected \"" + expected
                               + "\" but obtained \""
                               + actual + "\"");

            return 1;
        }

        return 0;
    }

    static int checkOSXContentTypes(String[] extensions, String[] expectedTypes)
        throws IOException {
        if (extensions.length != expectedTypes.length) {
            throw new IllegalArgumentException
                ("Parameter array lengths differ");
        }

        int failures = 0;
        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            Path file = Files.createTempFile("foo", "." + extension);
            try {
                String type = Files.probeContentType(file);
                if (type == null) {
                    System.err.println("Content type of " + extension
                            + " cannot be determined");
                    failures++;
                } else {
                    if (!type.equals(expectedTypes[i])) {
                        System.err.println("Content type: " + type
                                + "; expected: " + expectedTypes[i]);
                        failures++;
                    }
                }
            } finally {
                Files.delete(file);
            }
        }

        return failures;
    }

    public static void main(String[] args) throws IOException {
        int failures = 0;

        // exercise default file type detector
        Path file = createHtmlFile();
        try {
            String type = Files.probeContentType(file);
            if (type == null) {
                System.err.println("Content type cannot be determined - test skipped");
            } else {
                failures += checkContentTypes("text/html", type);
            }
        } finally {
            Files.delete(file);
        }

        // exercise custom file type detector
        file = createGrapeFile();
        try {
            String type = Files.probeContentType(file);
            if (type == null)
                throw new RuntimeException("Custom file type detector not installed?");
            failures += checkContentTypes("grape/unknown", type);
        } finally {
            Files.delete(file);
        }

        // Verify that common file extensions are mapped to the correct content
        // types on Mac OS X only which has consistent Uniform Type Identifiers.
        if (System.getProperty("os.name").contains("OS X")) {
            String[] extensions = new String[]{
                "jpg", "mp3", "mp4", "pdf", "png"
            };
            String[] expectedTypes = new String[]{
                "image/jpeg", "audio/mpeg", "video/mp4", "application/pdf",
                "image/png"
            };
            failures += checkOSXContentTypes(extensions, expectedTypes);
        }

        if (failures > 0) {
            throw new RuntimeException("Test failed!");
        }
    }
}

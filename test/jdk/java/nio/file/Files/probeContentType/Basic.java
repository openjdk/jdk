/*
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 8129632 8129633 8162624 8146215 8162745 8273655
 * @summary Unit test for probeContentType method
 * @library ../..
 * @build Basic SimpleFileTypeDetector
 * @run main/othervm Basic
 */

import java.io.*;
import java.nio.file.*;
import java.util.List;
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

    private static void checkMimeTypesFile(Path mimeTypes) {
        if (!Files.exists(mimeTypes)) {
            System.out.println(mimeTypes + " does not exist");
        } else if (!Files.isReadable(mimeTypes)) {
            System.out.println(mimeTypes + " is not readable");
        } else {
            System.out.println(mimeTypes + " contents:");
            try (Stream<String> lines = Files.lines(mimeTypes)) {
                lines.forEach(System.out::println);
                System.out.println("");
            } catch (IOException ioe) {
                System.err.printf("Problem reading %s: %s%n",
                                  mimeTypes, ioe.getMessage());
            }
        }
    }

    private static int checkContentTypes(String expected, String actual) {
        assert expected != null;
        assert actual != null;

        if (!expected.equals(actual)) {
            if (IS_UNIX) {
                Path userMimeTypes =
                    Paths.get(System.getProperty("user.home"), ".mime.types");
                checkMimeTypesFile(userMimeTypes);

                Path etcMimeTypes = Paths.get("/etc/mime.types");
                checkMimeTypesFile(etcMimeTypes);
            }

            System.err.println("Expected \"" + expected
                               + "\" but obtained \""
                               + actual + "\"");

            return 1;
        }

        return 0;
    }

    static int checkContentTypes(ExType[] exTypes)
        throws IOException {
        int failures = 0;
        for (int i = 0; i < exTypes.length; i++) {
            String extension = exTypes[i].extension();
            List<String> expectedTypes = exTypes[i].expectedTypes();
            Path file = Files.createTempFile("foo", "." + extension);
            try {
                String type = Files.probeContentType(file);
                if (type == null) {
                    System.err.println("Content type of " + extension
                            + " cannot be determined");
                    failures++;
                } else if (!expectedTypes.contains(type)) {
                    System.err.printf("Content type: %s; expected: %s%n",
                        type, expectedTypes);
                    failures++;
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
            if (type == null) {
                System.err.println("Custom file type detector not installed?");
                failures++;
            } else {
                failures += checkContentTypes("grape/unknown", type);
            }
        } finally {
            Files.delete(file);
        }

        // Verify that certain extensions are mapped to the correct type.
        var exTypes = new ExType[] {
                new ExType("adoc", List.of("text/plain")),
                new ExType("bz2", List.of("application/bz2", "application/x-bzip2")),
                new ExType("css", List.of("text/css")),
                new ExType("csv", List.of("text/csv")),
                new ExType("doc", List.of("application/msword")),
                new ExType("docx", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
                new ExType("gz", List.of("application/gzip", "application/x-gzip")),
                new ExType("jar", List.of("application/java-archive", "application/x-java-archive")),
                new ExType("jpg", List.of("image/jpeg")),
                new ExType("js", List.of("text/javascript", "application/javascript")),
                new ExType("json", List.of("application/json")),
                new ExType("markdown", List.of("text/markdown")),
                new ExType("md", List.of("text/markdown")),
                new ExType("mp3", List.of("audio/mpeg")),
                new ExType("mp4", List.of("video/mp4")),
                new ExType("odp", List.of("application/vnd.oasis.opendocument.presentation")),
                new ExType("ods", List.of("application/vnd.oasis.opendocument.spreadsheet")),
                new ExType("odt", List.of("application/vnd.oasis.opendocument.text")),
                new ExType("pdf", List.of("application/pdf")),
                new ExType("php", List.of("text/plain", "text/php")),
                new ExType("png", List.of("image/png")),
                new ExType("ppt", List.of("application/vnd.ms-powerpoint")),
                new ExType("pptx",List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
                new ExType("py", List.of("text/plain", "text/x-python-script")),
                new ExType("rar", List.of("application/vnd.rar")),
                new ExType("rtf", List.of("application/rtf", "text/rtf")),
                new ExType("webm", List.of("video/webm")),
                new ExType("webp", List.of("image/webp")),
                new ExType("xls", List.of("application/vnd.ms-excel")),
                new ExType("xlsx", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                new ExType("7z", List.of("application/x-7z-compressed")),
        };
        failures += checkContentTypes(exTypes);

        if (failures > 0) {
            throw new RuntimeException("Test failed!");
        }
    }

    record ExType(String extension, List<String> expectedTypes) { }
}

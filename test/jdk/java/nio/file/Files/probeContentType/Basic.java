/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 8129632 8129633 8162624 8146215 8162745 8273655 8274171 8287237 8297609
 * @modules java.base/jdk.internal.util
 * @summary Unit test for probeContentType method
 * @library ../..
 * @build Basic SimpleFileTypeDetector
 * @run main/othervm Basic
 */

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jdk.internal.util.OperatingSystem;
import jdk.internal.util.OSVersion;

/**
 * Uses Files.probeContentType to probe html file, custom file type, and minimal
 * set of file extension to content type mappings.
 */
public class Basic {
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
            if (!OperatingSystem.isWindows()) {
                Path userMimeTypes =
                    Path.of(System.getProperty("user.home"), ".mime.types");
                checkMimeTypesFile(userMimeTypes);

                Path etcMimeTypes = Path.of("/etc/mime.types");
                checkMimeTypesFile(etcMimeTypes);
            }

            System.err.println("Expected \"" + expected
                               + "\" but obtained \""
                               + actual + "\"");

            return 1;
        }

        return 0;
    }

    static int checkContentTypes(List<ExType> exTypes)
        throws IOException {
        int failures = 0;
        for (ExType exType : exTypes) {
            String extension = exType.extension();
            List<String> expectedTypes = exType.expectedTypes();
            Path file = Files.createTempFile("foo", "." + extension);
            try {
                String type = Files.probeContentType(file);
                if (type == null) {
                    System.err.println("Content type of " + extension
                            + " cannot be determined");
                    failures++;
                } else if (!expectedTypes.contains(type)) {
                    System.err.printf("For extension %s we got content type: %s; expected: %s%n",
                            extension, type, expectedTypes);
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
        List<ExType> exTypes = new ArrayList<ExType>();

        // extensions with consistent content type
        exTypes.add(new ExType("adoc", List.of("text/plain")));
        exTypes.add(new ExType("css", List.of("text/css")));
        exTypes.add(new ExType("doc", List.of("application/msword")));
        exTypes.add(new ExType("docx", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
        exTypes.add(new ExType("gz", List.of("application/gzip", "application/x-gzip")));
        exTypes.add(new ExType("jar", List.of("application/java-archive", "application/x-java-archive", "application/jar")));
        exTypes.add(new ExType("jpg", List.of("image/jpeg")));
        exTypes.add(new ExType("js", List.of("text/plain", "text/javascript", "application/javascript")));
        exTypes.add(new ExType("json", List.of("application/json")));
        exTypes.add(new ExType("markdown", List.of("text/markdown")));
        exTypes.add(new ExType("md", List.of("text/markdown", "application/x-genesis-rom")));
        exTypes.add(new ExType("mp3", List.of("audio/mpeg")));
        exTypes.add(new ExType("mp4", List.of("video/mp4")));
        exTypes.add(new ExType("odp", List.of("application/vnd.oasis.opendocument.presentation")));
        exTypes.add(new ExType("ods", List.of("application/vnd.oasis.opendocument.spreadsheet")));
        exTypes.add(new ExType("odt", List.of("application/vnd.oasis.opendocument.text")));
        exTypes.add(new ExType("pdf", List.of("application/pdf")));
        exTypes.add(new ExType("php", List.of("text/plain", "text/php", "application/x-php")));
        exTypes.add(new ExType("png", List.of("image/png")));
        exTypes.add(new ExType("ppt", List.of("application/vnd.ms-powerpoint")));
        exTypes.add(new ExType("pptx", List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")));
        exTypes.add(new ExType("py", List.of("text/plain", "text/x-python", "text/x-python-script")));
        exTypes.add(new ExType("webm", List.of("video/webm")));
        exTypes.add(new ExType("webp", List.of("image/webp")));
        exTypes.add(new ExType("xls", List.of("application/vnd.ms-excel")));
        exTypes.add(new ExType("xlsx", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        exTypes.add(new ExType("wasm", List.of("application/wasm")));

        // extensions with content type that differs on Windows 11+
        if (OperatingSystem.isWindows() &&
            (System.getProperty("os.name").endsWith("11") ||
                new OSVersion(10, 0).compareTo(OSVersion.current()) > 0)) {
            System.out.println("Windows 11+ detected: using different types");
            exTypes.add(new ExType("bz2", List.of("application/bz2", "application/x-bzip2", "application/x-bzip", "application/x-compressed")));
            exTypes.add(new ExType("csv", List.of("text/csv", "application/vnd.ms-excel")));
            exTypes.add(new ExType("rar", List.of("application/rar", "application/vnd.rar", "application/x-rar", "application/x-rar-compressed", "application/x-compressed")));
            exTypes.add(new ExType("rtf", List.of("application/rtf", "text/rtf", "application/msword")));
            exTypes.add(new ExType("7z", List.of("application/x-7z-compressed", "application/x-compressed")));
        } else {
            exTypes.add(new ExType("bz2", List.of("application/bz2", "application/x-bzip2", "application/x-bzip")));
            exTypes.add(new ExType("csv", List.of("text/csv")));
            exTypes.add(new ExType("rar", List.of("application/rar", "application/vnd.rar", "application/x-rar", "application/x-rar-compressed")));
            exTypes.add(new ExType("rtf", List.of("application/rtf", "text/rtf")));
            exTypes.add(new ExType("7z", List.of("application/x-7z-compressed")));
        }

        failures += checkContentTypes(exTypes);

        // Verify type is found when the extension is in a fragment component
        Path pathWithFragement = Path.of("SomePathWith#aFragement.png");
        String contentType = Files.probeContentType(pathWithFragement);
        if (contentType == null || !contentType.equals("image/png")) {
            System.err.printf("For %s expected \"png\" but got %s%n",
                pathWithFragement, contentType);
            failures++;
        }

        if (failures > 0) {
            throw new RuntimeException("Test failed!");
        }
    }

    record ExType(String extension, List<String> expectedTypes) { }
}

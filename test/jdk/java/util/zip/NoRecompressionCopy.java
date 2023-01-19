/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @test
 * @summary Check that entries can be copied from a ZipFile to a ZipOutputStream uncompressed
 */

public class NoRecompressionCopy {

    public static void main(String[] args) throws Exception {

        Path jar = createJarFile();

        Path plainCopy = Path.of("zip-plain-copy.jar");
        Path noRecompressionCopy = Path.of("zip-copy-no-recompression.jar");

        copyZipPlain(jar, plainCopy);
        checkCopy(jar, plainCopy);

        copyZipNoRecompression(jar, noRecompressionCopy);
        checkCopy(jar, noRecompressionCopy);


        if (false) {
            runPerformanceComparison(plainCopy, noRecompressionCopy);
        }
    }

    private static void runPerformanceComparison(Path plainCopy, Path noRecompressionCopy) throws IOException {
        Path bigJar = Path.of("../../../../src/utils/IdealGraphVisualizer/application/target/idealgraphvisualizer/idealgraphvisualizer/modules/ext/com.sun.hotspot.igv.View/xalan/xalan.jar");

        for (int i = 0; i < 100; i++) {
            copyZipPlain(bigJar, plainCopy);
            copyZipNoRecompression(bigJar, noRecompressionCopy);
        }


        {
            long before = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                copyZipPlain(bigJar, null);
            }
            System.out.println("copyZipPlain took: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before));
        }

        {
            long before = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                copyZipNoRecompression(bigJar, null);
            }
            System.out.println("copyZipNoRecompression took: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before));
        }
    }

    private static void copyZipNoRecompression(Path jar, Path output) throws IOException {
        try(ZipFile zf = new ZipFile(jar.toFile());
            ZipOutputStream out = new ZipOutputStream(getOut(output))) {
            final Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                try(InputStream in = zf.getInputStream(entry)) {
                    out.putNextEntry(entry);
                    in.transferTo(out);
                }
            }
        }
    }

    private static void copyZipPlain(Path jar, Path output) throws IOException {
        try(ZipFile zf = new ZipFile(jar.toFile());
            ZipOutputStream out = new ZipOutputStream(getOut(output))) {
            final Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                try(InputStream in = zf.getInputStream(entry)) {
                    out.putNextEntry(entry);
                    copy(in, out);
                }
            }
        }
    }

    private static OutputStream getOut(Path output) throws IOException {
        return output == null ? OutputStream.nullOutputStream() : new BufferedOutputStream(Files.newOutputStream(output));
    }

    private static void copy(InputStream in, ZipOutputStream out) throws IOException {
        byte[] buffer = new byte[8129];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static void checkCopy(Path source, Path copy) throws Exception {
        try(ZipFile szf = new ZipFile(source.toFile());
            ZipFile czf = new ZipFile(copy.toFile())) {
            Set<String> expectedEntries = szf.stream().map(ZipEntry::getName).collect(Collectors.toSet());
            Set<String> actualEntries = czf.stream().map(ZipEntry::getName).collect(Collectors.toSet());

            if (!expectedEntries.equals(actualEntries)) {
                throw new Exception("Unexpected entries, expected %s, got %s".formatted(expectedEntries, actualEntries));
            }

            for (String name : expectedEntries)  {
                compareStreams(name, szf, czf);
            }
        }
    }

    private static void compareStreams(String name, ZipFile szf, ZipFile czf) throws Exception {
        byte[] d1 = digest(name, szf);
        byte[] d2 = digest(name, czf);
        if (!Arrays.equals(d1, d2)) {
            throw new Exception("Contents of file %s are not equal in copies ZIP".formatted(name));
        }
    }

    private static byte[] digest(String name, ZipFile szf) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try(InputStream in = szf.getInputStream(szf.getEntry(name));
            OutputStream out = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            in.transferTo(out);
        }

        return digest.digest();
    }

    private static Path createJarFile() throws IOException {
        Path j = Path.of("a.zip");
        try(ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(j))) {
            out.putNextEntry(new ZipEntry("a.txt"));
            out.write("a".getBytes(StandardCharsets.UTF_8));
        }
        return j;
    }
}

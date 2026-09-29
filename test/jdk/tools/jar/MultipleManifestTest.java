/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8335912
 * @summary test extract jar with multpile manifest files
 * @library /test/lib
 * @modules jdk.jartool
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 * @run junit/othervm MultipleManifestTest
 */

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jdk.test.lib.util.FileUtils;

@TestInstance(Lifecycle.PER_CLASS)
class MultipleManifestTest {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private final String nl = System.lineSeparator();
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final PrintStream jarOut = new PrintStream(baos);

    static final Path zip = Path.of("MultipleManifestTest.jar");
    static final String jdkVendor = System.getProperty("java.vendor");
    static final String jdkVersion = System.getProperty("java.version");
    static final String MANIFEST1 = "Manifest-Version: 1.0"
            + System.lineSeparator()
            + "Created-By: " + jdkVersion + " (" + jdkVendor + ")";
    static final String MANIFEST2 = "Manifest-Version: 2.0"
            + System.lineSeparator()
            + "Created-By: " + jdkVersion + " (" + jdkVendor + ")";
    static final String MANIFEST3 = "Manifest-Version: 3.0"
            + System.lineSeparator()
            + "Created-By: " + jdkVersion + " (" + jdkVendor + ")";
    private static final String META_INF = "META-INF/";

    /**
     * Delete the ZIP file produced by this test
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterAll
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Create a JAR with the Manifest as the 1st, 2nd and 4th entry
     *
     * @throws IOException if an error occurs
     */
    @BeforeAll
    public void writeManifestAsFirstSecondAndFourthEntry() throws IOException {
        int locPosA, locPosB, cenPos;
        System.out.printf("%n%n*****Creating Jar with the Manifest as the 1st, 2nd and 4th entry*****%n%n");
        var out = new ByteArrayOutputStream(1024);
        try (var zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
            zos.write(MANIFEST1.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            locPosA = out.size();
            zos.putNextEntry(new ZipEntry(META_INF + "AANIFEST.MF"));
            zos.write(MANIFEST2.getBytes(StandardCharsets.UTF_8));
            zos.putNextEntry(new ZipEntry("entry1.txt"));
            zos.write("entry1".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            locPosB = out.size();
            zos.putNextEntry(new ZipEntry(META_INF + "BANIFEST.MF"));
            zos.write(MANIFEST3.getBytes(StandardCharsets.UTF_8));
            zos.putNextEntry(new ZipEntry("entry2.txt"));
            zos.write("hello entry2".getBytes(StandardCharsets.UTF_8));
            zos.flush();
            cenPos = out.size();
        }
        var template = out.toByteArray();
        // ISO_8859_1 to keep the 8-bit value
        var s = new String(template, StandardCharsets.ISO_8859_1);
        // change META-INF/AANIFEST.MF to META-INF/MANIFEST.MF
        var loc = s.indexOf("AANIFEST.MF", locPosA);
        var cen = s.indexOf("AANIFEST.MF", cenPos);
        template[loc] = template[cen] = (byte) 'M';
        // change META-INF/BANIFEST.MF to META-INF/MANIFEST.MF
        loc = s.indexOf("BANIFEST.MF", locPosB);
        cen = s.indexOf("BANIFEST.MF", cenPos);
        template[loc] = template[cen] = (byte) 'M';
        Files.write(zip, template);
    }

    @AfterEach
    public void removeExtractedFiles() {
        rm("META-INF entry1.txt entry2.txt");
    }

    /**
     * Extract by default should have the last manifest.
     */
    @Test
    public void testOverwrite() throws IOException {
        jar("xvf " + zip.toString());
        println();
        Assertions.assertEquals("3.0", getManifestVersion());
        String output = " inflated: META-INF/MANIFEST.MF" + nl +
                " inflated: META-INF/MANIFEST.MF" + nl +
                " inflated: entry1.txt" + nl +
                " inflated: META-INF/MANIFEST.MF" + nl +
                " inflated: entry2.txt" + nl;
        assertOutputContains(output);
    }

    /**
     * Extract with k option should have first manifest.
     */
    @Test
    public void testKeptOldFile() throws IOException {
        jar("xkvf " + zip.toString());
        println();
        Assertions.assertEquals("1.0", getManifestVersion());
        String output = " inflated: META-INF/MANIFEST.MF" + nl +
                "  skipped: META-INF/MANIFEST.MF exists" + nl +
                " inflated: entry1.txt" + nl +
                "  skipped: META-INF/MANIFEST.MF exists" + nl +
                " inflated: entry2.txt" + nl;
        assertOutputContains(output);
    }

    private String getManifestVersion() throws IOException {
        try (var is = Files.newInputStream(Path.of(JarFile.MANIFEST_NAME))) {
            var manifest = new Manifest(is);
            return manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION);
        }
    }

    private void jar(String cmdline) throws IOException {
        System.out.println("jar " + cmdline);
        baos.reset();

        // the run method catches IOExceptions, we need to expose them
        ByteArrayOutputStream baes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(baes);
        PrintStream saveErr = System.err;
        System.setErr(err);
        try {
            int rc = JAR_TOOL.run(jarOut, err, cmdline.split(" +"));
            if (rc != 0) {
                throw new IOException(baes.toString());
            }
        } finally {
            System.setErr(saveErr);
        }
    }

    private void assertOutputContains(String expected) {
        Assertions.assertTrue(baos.toString().contains(expected));
    }

    private void println() throws IOException {
        System.out.println(new String(baos.toByteArray()));
    }

    private Stream<Path> mkpath(String... args) {
        return Arrays.stream(args).map(d -> Path.of(".", d.split("/")));
    }

    private void rm(String cmdline) {
        System.out.println("rm -rf " + cmdline);
        mkpath(cmdline.split(" +")).forEach(p -> {
            try {
                if (Files.isDirectory(p)) {
                    FileUtils.deleteFileTreeWithRetry(p);
                } else {
                    FileUtils.deleteFileIfExistsWithRetry(p);
                }
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }
}
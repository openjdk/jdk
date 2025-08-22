/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8345431
 * @summary test validator to report malformed jar file
 * @library /test/lib
 * @modules jdk.jartool
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 * @run junit/othervm ValidatorTest
 */

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jdk.test.lib.util.FileUtils;

class ValidatorTest {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private final String nl = System.lineSeparator();
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final PrintStream jarOut = new PrintStream(baos);

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

    private void writeManifestAsFirstSecondAndFourthEntry(Path path, boolean useCen, boolean useLoc) throws IOException {
        int locPosA, cenPos;
        System.out.printf("%n%n*****Creating Jar with duplicate Manifest*****%n%n");
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
            zos.putNextEntry(new ZipEntry(META_INF + "BANIFEST.MF"));
            zos.write(MANIFEST3.getBytes(StandardCharsets.UTF_8));
            zos.putNextEntry(new ZipEntry("entry2.txt"));
            zos.write("hello entry2".getBytes(StandardCharsets.UTF_8));
            zos.flush();
            cenPos = out.size();
        }
        var template = out.toByteArray();
        // ISO_8859_1 to keep the 8-bit value to avoid mess index in the byte array
        var s = new String(template, StandardCharsets.ISO_8859_1);
        // change META-INF/AANIFEST.MF to META-INF/MANIFEST.MF
        if (useCen) {
            var cen = s.indexOf("AANIFEST.MF", cenPos);
            template[cen] = (byte) 'M';
            // change META-INF/BANIFEST.MF to META-INF/MANIFEST.MF
            cen = s.indexOf("BANIFEST.MF", cenPos);
            template[cen] = (byte) 'M';
        }
        if (useLoc) {
            var loc = s.indexOf("AANIFEST.MF", locPosA);
            template[loc] = (byte) 'M';
        }
        Files.write(path, template);
    }

    private void createMismatchOrderJar(Path path) throws IOException {
        int locPosA, locPosB;
        System.out.printf("%n%n*****Creating Jar with the swap entry name*****%n%n");
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
        }
        var template = out.toByteArray();
        // ISO_8859_1 to keep the 8-bit value to avoid mess index in the byte array
        var s = new String(template, StandardCharsets.ISO_8859_1);
        // change META-INF/AANIFEST.MF to META-INF/BANIFEST.MF
        var loc = s.indexOf("AANIFEST.MF", locPosA);
        template[loc] = (byte) 'B';
        // change META-INF/BANIFEST.MF to META-INF/AANIFEST.MF
        loc = s.indexOf("BANIFEST.MF", locPosB);
        template[loc] = (byte) 'A';

        Files.write(path, template);
    }

    record EntryNameTestCase(String entryName, boolean isValid) {}

    private static Stream<EntryNameTestCase> zipEntryPaths() {
        return Stream.of(
                new EntryNameTestCase("../../c:////d:/tmp/testentry0", false),
                new EntryNameTestCase("..\\..\\c:\\d:\\tmp\\testentry1", false),
                new EntryNameTestCase("////c:/tmp/testentry2", false),
                new EntryNameTestCase("////c:/d:/tmp/testentry3", false),
                new EntryNameTestCase("c://///d:/tmp/testentry4", false),
                new EntryNameTestCase("//tmp/tmp2/testentry5", false),
                new EntryNameTestCase("///tmp/abc", false),
                new EntryNameTestCase("C:\\Documents\\tennis\\CardioTennis.pdf", false),
                new EntryNameTestCase("\\Program Files\\Custom Utilities\\tennis.exe", false),
                new EntryNameTestCase("myhome\\Hello.txt", false),
                new EntryNameTestCase("Hello.txt", true),
                new EntryNameTestCase("./Hello.txt", true),
                new EntryNameTestCase("../Hello.txt", false),
                new EntryNameTestCase(".\\Hello.txt", false),
                new EntryNameTestCase("..\\Hello.txt", false),
                new EntryNameTestCase("C:\\Hello.txt", false),
                new EntryNameTestCase("D:/Hello.txt", false),
                new EntryNameTestCase("foo\\bar.txt", false),
                new EntryNameTestCase("foo/bar.txt", true),
                new EntryNameTestCase("foo/../bar.txt", false),
                new EntryNameTestCase("foo/./bar.txt", true),
                new EntryNameTestCase("..", false),
                new EntryNameTestCase(".", false),
                new EntryNameTestCase("/home/foo.txt", false),
                new EntryNameTestCase("./home/foo.txt", true),
                new EntryNameTestCase("../home/foo.txt", false),
                new EntryNameTestCase("foo/bar/..", false),
                new EntryNameTestCase("foo/bar/.", true),
                new EntryNameTestCase("/foo/bar/../../myhome/bin", false),
                new EntryNameTestCase("/foo/bar/././myhome/bin", false),
                new EntryNameTestCase("myHome/..valid", true),
                new EntryNameTestCase("myHome/.valid", true),
                new EntryNameTestCase("..valid", true),
                new EntryNameTestCase(".valid", true)
        );
    }

    private List<String> createInvalidEntryJar(Path path) throws IOException {
        System.out.printf("%n%n*****Creating Jar with the invalid entry names*****%n%n");
        var out = new ByteArrayOutputStream(1024);
        List<String> invalidEntryNames;
        try (var zos = new ZipOutputStream(out)) {
            invalidEntryNames = zipEntryPaths()
                .filter(testCase -> {
                        try {
                            zos.putNextEntry(new ZipEntry(testCase.entryName()));
                            var content = "Content of " + testCase.entryName();
                            zos.write(content.getBytes(StandardCharsets.UTF_8));
                            return !testCase.isValid();
                        } catch (IOException ioe) {
                            throw new UncheckedIOException(ioe);
                        }
                    })
                .map(EntryNameTestCase::entryName).toList();
            zos.flush();
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        }
        Files.write(path, out.toByteArray());
        return invalidEntryNames;
    }

    @Test
    public void testValidJar() throws IOException {
        var zip = Path.of("Regular.jar");
        writeManifestAsFirstSecondAndFourthEntry(zip, false, false);
        jar("--validate --file " + zip.toString());
    }

    @Test
    public void testMultiManifestJar() throws IOException {
        var zip = Path.of("MultipleManifestTest.jar");
        writeManifestAsFirstSecondAndFourthEntry(zip, true, true);
        try {
            jar("--validate --file " + zip.toString());
            fail("Expecting non-zero exit code");
        } catch (IOException e) {
            var err = e.getMessage();
            System.out.println(err);
            assertTrue(err.contains("Warning: There were 3 central directory entries found for META-INF/MANIFEST.MF"));
            assertTrue(err.contains("Warning: There were 2 local file headers found for META-INF/MANIFEST.MF"));
            assertTrue(err.contains("Warning: An equivalent entry for the local file header META-INF/BANIFEST.MF was not found in the central directory"));
        }
    }

    @Test
    public void testOnlyLocModified() throws IOException {
        Path f = Path.of("LocHacked.jar");
        writeManifestAsFirstSecondAndFourthEntry(f, false, true);
        try {
            jar("--validate --file " + f.toString());
            fail("Expecting non-zero exit code");
        } catch (IOException e) {
            var err = e.getMessage();
            System.out.println(err);
            assertTrue(err.contains("Warning: There were 2 local file headers found for META-INF/MANIFEST.MF"));
            assertTrue(err.contains("Warning: An equivalent for the central directory entry META-INF/AANIFEST.MF was not found in the local file headers"));
            // Order is base on the central directory, expecting AANIFEST.MF but see next entry
            assertTrue(err.contains("Warning: Central directory and local file header entries are not in the same order"));
        }
    }

    @Test
    public void testOnlyCenModified() throws IOException {
        Path f = Path.of("CenHacked.jar");
        writeManifestAsFirstSecondAndFourthEntry(f, true, false);
        try {
            jar("--validate --file " + f.toString());
            fail("Expecting non-zero exit code");
        } catch (IOException e) {
            var err = e.getMessage();
            System.out.println(err);
            assertTrue(err.contains("Warning: There were 3 central directory entries found for META-INF/MANIFEST.MF"));
            assertTrue(err.contains("Warning: An equivalent entry for the local file header META-INF/AANIFEST.MF was not found in the central directory"));
            assertTrue(err.contains("Warning: An equivalent entry for the local file header META-INF/BANIFEST.MF was not found in the central directory"));
            assertFalse(err.contains("Warning: Central directory and local file header entries are not in the same order"));
        }
    }

    @Test
    public void testMismatchOrder() throws IOException {
        Path f = Path.of("SwappedEntry.jar");
        createMismatchOrderJar(f);
        try {
            jar("--validate --file " + f.toString());
            fail("Expecting non-zero exit code");
        } catch (IOException e) {
            var err = e.getMessage();
            System.out.println(err);
            assertTrue(err.contains("Warning: Central directory and local file header entries are not in the same order"));
        }
    }

    @Test
    public void testInvalidEntryName() throws IOException {
        Path f = Path.of("InvalidEntry.jar");
        var invalidEntryNames = createInvalidEntryJar(f);
        try {
            jar("--validate --file " + f.toString());
            fail("Expecting non-zero exit code");
        } catch (IOException e) {
            var err = e.getMessage();
            System.out.println(err);
            for (var entryName : invalidEntryNames) {
                assertTrue(err.contains("Warning: entry name " + entryName + " is not valid"), "missing warning for " + entryName);
            }
        }
    }

    // return stderr output
    private String jar(String cmdline) throws IOException {
        System.out.println("jar " + cmdline);
        baos.reset();

        // the run method catches IOExceptions, we need to expose them
        ByteArrayOutputStream baes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(baes);
        PrintStream saveErr = System.err;
        System.setErr(err);
        try {
            int rc = JAR_TOOL.run(jarOut, err, cmdline.split(" +"));
            System.out.println("exit code: " + rc);
            if (rc != 0) {
                assertTrue(rc > 0);
                throw new IOException(baes.toString());
            } else {
                return baes.toString();
            }
        } finally {
            System.setErr(saveErr);
        }
    }
}
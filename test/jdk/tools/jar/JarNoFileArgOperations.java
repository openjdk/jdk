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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8345506
 * @summary verifies that the "jar" operations that are expected to work
 *          without the "--file" option work as expected
 * @library /test/lib
 * @run junit JarNoFileArgOperations
 */
public class JarNoFileArgOperations {

    private static final Path SCRATCH_DIR = Path.of(".");
    private static final Path JAR_TOOL = Path.of(JDKToolFinder.getJDKTool("jar")).toAbsolutePath();
    private static final String JAR_ENTRY_NAME = "foobarhello.txt";

    private static Path SIMPLE_JAR;

    private static void makeSimpleJar(final Path path) throws IOException {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (OutputStream fos = Files.newOutputStream(path);
             JarOutputStream jaros = new JarOutputStream(fos, manifest)) {
            jaros.putNextEntry(new ZipEntry(JAR_ENTRY_NAME));
            jaros.write("foobar-8345506".getBytes(US_ASCII));
            jaros.closeEntry();
        }
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        final Path jarFile = Files.createTempFile(SCRATCH_DIR, "8345506", ".jar");
        makeSimpleJar(jarFile);
        SIMPLE_JAR = jarFile;
        System.out.println("created JAR file " + jarFile);
    }

    /*
     * Launches "jar --validate" by streaming the JAR file content through the "jar" tool
     * process' STDIN and expects that the command completes normally.
     */
    @Test
    public void testValidate() throws Exception {
        System.out.println("launching jar --validate");
        final ProcessBuilder pb = new ProcessBuilder()
                .command(JAR_TOOL.toString(), "--validate")
                // stream the JAR file content to the jar command through the process' STDIN
                .redirectInput(SIMPLE_JAR.toFile());
        final OutputAnalyzer oa = ProcessTools.executeCommand(pb);
        oa.shouldHaveExitValue(0);
    }

    /*
     * Launches "jar --list" and "jar -t" by streaming the JAR file content through the "jar" tool
     * process' STDIN and expects that the command completes normally.
     */
    @Test
    public void testListing() throws Exception {
        for (String opt : new String[]{"-t", "--list"}) {
            final ProcessBuilder pb = new ProcessBuilder()
                    .command(JAR_TOOL.toString(), opt)
                    // stream the JAR file content to the jar command through the process' STDIN
                    .redirectInput(SIMPLE_JAR.toFile());
            final OutputAnalyzer oa = ProcessTools.executeCommand(pb);
            oa.shouldHaveExitValue(0);
            // verify the listing contained the JAR entry name
            oa.contains(JAR_ENTRY_NAME);
        }
    }

    /*
     * Launches "jar --extract" and "jar -x" by streaming the JAR file content through
     * the "jar" tool process' STDIN and expects that the command completes normally.
     */
    @Test
    public void testExtract() throws Exception {
        for (String opt : new String[]{"-x", "--extract"}) {
            final Path tmpDestDir = Files.createTempDirectory(SCRATCH_DIR, "8345506");
            final ProcessBuilder pb = new ProcessBuilder()
                    .command(JAR_TOOL.toString(), opt, "--dir", tmpDestDir.toString())
                    // stream the JAR file content to the jar command through the process' STDIN
                    .redirectInput(SIMPLE_JAR.toFile());
            final OutputAnalyzer oa = ProcessTools.executeCommand(pb);
            oa.shouldHaveExitValue(0);
            // verify the file content was extracted
            assertTrue(Files.exists(tmpDestDir.resolve(JAR_ENTRY_NAME)),
                    JAR_ENTRY_NAME + " wasn't extracted to " + tmpDestDir);
        }
    }

    /*
     * Launches "jar --update" and "jar -u" by streaming the JAR file content through
     * the "jar" tool process' STDIN and expects that the command completes normally.
     */
    @Test
    public void testUpdate() throws Exception {
        for (String opt : new String[]{"-u", "--update"}) {
            // the updated JAR will be written out to this file
            final Path destUpdatedJar = Files.createTempFile(SCRATCH_DIR, "8345506", ".jar");
            // an arbitrary file that will be added to the JAR file as
            // part of the update operation
            final Path fileToAdd = Files.createTempFile(SCRATCH_DIR, "8345506", ".txt");
            final String expectedNewEntry = fileToAdd.getFileName().toString();
            final ProcessBuilder pb = new ProcessBuilder()
                    .command(JAR_TOOL.toString(), opt, expectedNewEntry)
                    // stream the JAR file content to the jar command through the process' STDIN
                    .redirectInput(SIMPLE_JAR.toFile())
                    // redirect the updated JAR to a file so that its contents can be verified
                    // later
                    .redirectOutput(destUpdatedJar.toFile());
            final OutputAnalyzer oa = ProcessTools.executeProcess(pb);
            oa.shouldHaveExitValue(0);
            System.out.println("updated JAR file at " + destUpdatedJar.toAbsolutePath());
            // verify, by listing the updated JAR file contents,
            // that the JAR file has been updated to include the new file
            try (final JarFile jar = new JarFile(destUpdatedJar.toFile())) {
                jar.stream()
                        .map(ZipEntry::getName)
                        .filter((name) -> name.equals(expectedNewEntry))
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("missing entry " + expectedNewEntry
                                        + " in updated JAR file " + destUpdatedJar)
                        );
            }
        }
    }
}

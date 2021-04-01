/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.JarBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

/**
 * @test
 * @bug 8173970
 * @summary jar tool should allow extracting to specific directory
 * @library /test/lib
 * @run testng JarExtractTest
 */
public class JarExtractTest {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
            .orElseThrow(() ->
                    new RuntimeException("jar tool not found")
            );

    private static final byte[] FILE_CONTENT = "Hello world!!!".getBytes(StandardCharsets.UTF_8);
    private static final String LEADING_SLASH_PRESERVED_ENTRY = "/tmp/8173970/f1.txt";
    // the jar that will get extracted in the tests
    private static Path testJarPath;
    private static Collection<Path> filesToDelete = new ArrayList<>();

    @BeforeClass
    public static void createTestJar() throws Exception {
        Files.deleteIfExists(Path.of(LEADING_SLASH_PRESERVED_ENTRY));

        final String tmpDir = Files.createTempDirectory("8173970-").toString();
        testJarPath = Paths.get(tmpDir, "8173970-test.jar");
        final JarBuilder builder = new JarBuilder(testJarPath.toString());
        // d1
        //  |--- d2
        //  |    |--- d3
        //  |    |    |--- f2.txt
        //  |
        //  |--- d4
        //  ...
        //  f1.txt

        builder.addEntry("d1/", new byte[0]);
        builder.addEntry("f1.txt", FILE_CONTENT);
        builder.addEntry("d1/d2/d3/f2.txt", FILE_CONTENT);
        builder.addEntry("d1/d4/", new byte[0]);
        builder.build();

        filesToDelete.add(Path.of(LEADING_SLASH_PRESERVED_ENTRY));
    }

    @AfterClass
    public void cleanup() {
        for (final Path p : filesToDelete) {
            try {
                System.out.println("Deleting file/dir " + p);
                Files.delete(p);
            } catch (IOException ioe) {
                //ignore
            }
        }
    }

    /**
     * Creates and returns various relative paths, to which the jar will be extracted in the tests
     */
    @DataProvider(name = "relExtractLocations")
    private Object[][] provideRelativeExtractLocations() throws Exception {
        // create some dirs so that they already exist when the jar is being extracted
        final String existing1 = "." + File.separator + "8173970-existing-1";
        Files.createDirectories(Paths.get(existing1));
        final String existing2 = "." + File.separator + "foo" + File.separator + "8173970-existing-2";
        Files.createDirectories(Paths.get(existing2));
        final Path dirOutsideScratchDir = Files.createTempDirectory(Paths.get(".."), "8173970");
        // we need to explicitly delete this dir after the tests end
        filesToDelete.add(dirOutsideScratchDir);
        final String existing3 = dirOutsideScratchDir.toString() + File.separator + "8173970-existing-3";
        Files.createDirectories(Paths.get(existing3));

        final String anotherDirOutsideScratchDir = ".." + File.separator + "8173970-non-existent";
        filesToDelete.add(Paths.get(anotherDirOutsideScratchDir));

        return new Object[][]{
                {"."}, // current dir
                {"." + File.separator + "8173970-extract-1"}, // (explicitly) relative to current dir
                {"8173970-extract-2"}, // (implicitly) relative to current dir
                {anotherDirOutsideScratchDir}, // sibling to current dir
                // some existing dirs
                {existing1},
                {existing2},
                {existing3},
                // a non-existent dir within an existing dir
                {existing1 + File.separator + "non-existing" + File.separator + "foo"}
        };
    }

    /**
     * Creates and returns various absolute paths, to which the jar will be extracted in the tests
     */
    @DataProvider(name = "absExtractLocations")
    private Object[][] provideAbsoluteExtractLocations() throws Exception {
        final Object[][] relative = provideRelativeExtractLocations();
        final Object[][] abs = new Object[relative.length][1];
        int i = 0;
        for (final Object[] p : relative) {
            abs[i++][0] = Paths.get((String) p[0]).toAbsolutePath().toString();
        }
        return abs;
    }

    /**
     * Creates and returns various normalized paths, to which the jar will be extracted in the tests
     */
    @DataProvider(name = "absNormalizedExtractLocations")
    private Object[][] provideAbsoluteNormalizedExtractLocations() throws Exception {
        final Object[][] relative = provideAbsoluteExtractLocations();
        final Object[][] abs = new Object[relative.length][1];
        int i = 0;
        for (final Object[] p : relative) {
            abs[i++][0] = Paths.get((String) p[0]).toAbsolutePath().normalize().toString();
        }
        return abs;
    }

    /**
     * Extracts a jar to various relative paths, using the -C/--dir option and then
     * verifies that the extracted content is at the expected locations with the correct
     * content
     */
    @Test(dataProvider = "relExtractLocations")
    public void testExtractToRelativeDir(final String dest) throws Exception {
        testExtract(dest);
    }

    /**
     * Extracts a jar to various absolute paths, using the -C/--dir option and then
     * verifies that the extracted content is at the expected locations with the correct
     * content
     */
    @Test(dataProvider = "absExtractLocations")
    public void testExtractToAbsoluteDir(final String dest) throws Exception {
        testExtract(dest);
    }

    /**
     * Extracts a jar to various normalized paths (i.e. no {@code .} or @{code ..} in the path components),
     * using the -C/--dir option and then verifies that the extracted content is at the expected locations
     * with the correct content
     */
    @Test(dataProvider = "absNormalizedExtractLocations")
    public void testExtractToAbsoluteNormalizedDir(final String dest) throws Exception {
        testExtract(dest);
    }

    /**
     * Test that extracting a jar with {@code jar -x -f --dir} works as expected
     */
    @Test
    public void testExtractLongForm() throws Exception {
        final String dest = "foo-bar";
        System.out.println("Extracting " + testJarPath + " to " + dest);
        final int exitCode = JAR_TOOL.run(System.out, System.err, "-x", "-f", testJarPath.toString(),
                "--dir", dest);
        Assert.assertEquals(exitCode, 0, "Failed to extract " + testJarPath + " to " + dest);
        verifyExtractedContent(dest);
    }

    /**
     * Verifies that the {@code jar --help} output contains the --dir option
     */
    @Test
    public void testHelpOutput() {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final int exitCode = JAR_TOOL.run(new PrintStream(outStream), System.err, "--help");
        Assert.assertEquals(exitCode, 0, "jar --help command failed");
        final String output = outStream.toString();
        // this message is expected to be the one from the jar --help output which is sourced from
        // jar.properties
        final String expectedMsg = "--dir                    Directory into which the jar will be extracted";
        Assert.assertTrue(output.contains(expectedMsg), "jar --help didn't contain --dir option");
    }

    /**
     * Tests that {@code jar -x -f} command works fine even when the -C or --dir option
     * isn't specified
     */
    @Test
    public void testExtractWithoutOutputDir() throws Exception {
        final int exitCode = JAR_TOOL.run(System.out, System.err, "-x", "-f", testJarPath.toString());
        Assert.assertEquals(exitCode, 0, "Failed to extract " + testJarPath);
        // the content would have been extracted to current dir
        verifyExtractedContent(".");
    }

    /**
     * Tests that extracting a jar using {@code -P} flag and without any explicit destination
     * directory works correctly if the jar contains entries with leading slashes and/or {@code ..}
     * parts preserved.
     */
    @Test
    public void testExtractNoDestDirWithPFlag() throws Exception {
        // create a jar which has leading slash (/) and dot-dot (..) preserved in entry names
        final Path jarPath = createJarWithPFlagSemantics();
        // extract with -P flag without any explicit destination directory (expect the extraction to work fine)
        final String[] args = new String[]{"-xvfP", jarPath.toString()};
        printJarCommand(args);
        final int exitCode = JAR_TOOL.run(System.out, System.err, args);
        Assert.assertEquals(exitCode, 0, "Failed to extract " + jarPath);
        final String dest = ".";
        Assert.assertTrue(Files.isDirectory(Paths.get(dest)), dest + " is not a directory");
        final Path d1 = Paths.get(dest, "d1");
        Assert.assertTrue(Files.isDirectory(d1), d1 + " directory is missing or not a directory");
        final Path d2 = Paths.get(dest, "d1", "d2");
        Assert.assertTrue(Files.isDirectory(d2), d2 + " directory is missing or not a directory");
        final Path f1 = Paths.get(LEADING_SLASH_PRESERVED_ENTRY);
        Assert.assertTrue(Files.isRegularFile(f1), f1 + " is missing or not a file");
        Assert.assertEquals(Files.readAllBytes(f1), FILE_CONTENT, "Unexpected content in file " + f1);
        final Path f2 = Paths.get("d1/d2/../f2.txt");
        Assert.assertTrue(Files.isRegularFile(f2), f2 + " is missing or not a file");
        Assert.assertEquals(Files.readAllBytes(f2), FILE_CONTENT, "Unexpected content in file " + f2);
    }

    /**
     * Tests that the {@code -P} option cannot be used during jar extraction when the {@code -C} and/or
     * {@code --dir} option is used
     */
    @Test
    public void testExtractWithDirPFlagNotAllowed() throws Exception {
        // this error message is expected to be the one from the jar --help output which is sourced from
        // jar.properties
        final String expectedErrMsg = "You may not specify '-Px' with the '-C' or '--dir' options";
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final List<String[]> cmdArgs = new ArrayList<>();
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "-C", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "--dir", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "-C", "."});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "--dir", "."});
        cmdArgs.add(new String[]{"-xvfP", testJarPath.toString(), "-C", tmpDir});
        for (final String[] args : cmdArgs) {
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            printJarCommand(args);
            int exitCode = JAR_TOOL.run(System.out, new PrintStream(err), args);
            Assert.assertNotEquals(exitCode, 0, "jar extraction was expected to fail but didn't");
            // verify it did indeed fail due to the right reason
            Assert.assertTrue(err.toString(StandardCharsets.UTF_8).contains(expectedErrMsg));
        }
    }

    /**
     * Tests that {@code jar -xvf <jarname> -C <dir>} works fine too
     */
    @Test
    public void testLegacyCompatibilityMode() throws Exception {
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final String[] args = new String[]{"-xvf", testJarPath.toString(), "-C", tmpDir};
        printJarCommand(args);
        final int exitCode = JAR_TOOL.run(System.out, System.err, args);
        Assert.assertEquals(exitCode, 0, "Failed to extract " + testJarPath);
        verifyExtractedContent(tmpDir);
    }

    /**
     * Tests that when multiple directories are specified for extracting the jar, the jar extraction
     * fails
     */
    @Test
    public void testExtractFailWithMultipleDir() throws Exception {
        // this error message is expected to be the one from the jar --help output which is sourced from
        // jar.properties
        final String expectedErrMsg = "You may not specify the '-C' or '--dir' option more than once with the '-x' option";
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final List<String[]> cmdArgs = new ArrayList<>();
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-C", tmpDir, "-C", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "--dir", tmpDir, "--dir", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "--dir", tmpDir, "-C", tmpDir});
        for (final String[] args : cmdArgs) {
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            printJarCommand(args);
            int exitCode = JAR_TOOL.run(System.out, new PrintStream(err), args);
            Assert.assertNotEquals(exitCode, 0, "jar extraction was expected to fail but didn't");
            // verify it did indeed fail due to the right reason
            Assert.assertTrue(err.toString(StandardCharsets.UTF_8).contains(expectedErrMsg));
        }
    }

    /**
     * Tests that extracting only specific files from a jar, into a specific destination directory,
     * works as expected
     */
    @Test
    public void testExtractPartialContent() throws Exception {
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final String[] cmdArgs = new String[]{"-x", "-f", testJarPath.toString(), "--dir", tmpDir,
                "f1.txt", "d1/d2/d3/f2.txt"};
        printJarCommand(cmdArgs);
        final int exitCode = JAR_TOOL.run(System.out, System.err, cmdArgs);
        Assert.assertEquals(exitCode, 0, "Failed to extract " + testJarPath);
        // make sure only the specific files were extracted
        final Stream<Path> paths = Files.walk(Path.of(tmpDir));
        // files/dirs count expected to be found when the location to which the jar was extracted
        // is walked.
        // 1) The top level dir being walked 2) f1.txt file 3) d1 dir 4) d1/d2 dir
        // 5) d1/d2/d3 dir 6) d1/d2/d3/f2.txt file
        final int numExpectedFiles = 6;
        Assert.assertEquals(paths.count(), numExpectedFiles, "Unexpected number of files/dirs in " + tmpDir);
        final Path f1 = Paths.get(tmpDir, "f1.txt");
        Assert.assertTrue(Files.isRegularFile(f1), f1.toString() + " wasn't extracted from " + testJarPath);
        Assert.assertEquals(Files.readAllBytes(f1), FILE_CONTENT, "Unexpected content in file " + f1);
        final Path d1 = Paths.get(tmpDir, "d1");
        Assert.assertTrue(Files.isDirectory(d1), d1.toString() + " wasn't extracted from " + testJarPath);
        Assert.assertEquals(Files.walk(d1, 1).count(), 2, "Unexpected number " +
                "of files/dirs in " + d1);
        final Path d2 = Paths.get(d1.toString(), "d2");
        Assert.assertTrue(Files.isDirectory(d2), d2.toString() + " wasn't extracted from " + testJarPath);
        Assert.assertEquals(Files.walk(d2, 1).count(), 2, "Unexpected number " +
                "of files/dirs in " + d2);
        final Path d3 = Paths.get(d2.toString(), "d3");
        Assert.assertTrue(Files.isDirectory(d3), d3.toString() + " wasn't extracted from " + testJarPath);
        Assert.assertEquals(Files.walk(d3, 1).count(), 2, "Unexpected number " +
                "of files/dirs in " + d3);
        final Path f2 = Paths.get(d3.toString(), "f2.txt");
        Assert.assertTrue(Files.isRegularFile(f2), f2.toString() + " wasn't extracted from " + testJarPath);
        Assert.assertEquals(Files.readAllBytes(f2), FILE_CONTENT, "Unexpected content in file " + f2);
    }

    /**
     * Extracts the jar file using {@code jar -x -f <jarfile> -C <dest>}
     */
    private void testExtract(final String dest) throws Exception {
        final String[] args = new String[]{"-x", "-f", testJarPath.toString(), "-C", dest};
        printJarCommand(args);
        final int exitCode = JAR_TOOL.run(System.out, System.err, args);
        Assert.assertEquals(exitCode, 0, "Failed to extract " + testJarPath + " to " + dest);
        verifyExtractedContent(dest);
    }

    /**
     * Verifies that the extracted jar content matches what was present in the original jar
     */
    private void verifyExtractedContent(final String dest) throws IOException {
        Assert.assertTrue(Files.isDirectory(Paths.get(dest)), dest + " is not a directory");
        final Path d1 = Paths.get(dest, "d1");
        Assert.assertTrue(Files.isDirectory(d1), d1 + " directory is missing or not a directory");
        final Path d2 = Paths.get(dest, "d1", "d2");
        Assert.assertTrue(Files.isDirectory(d2), d2 + " directory is missing or not a directory");
        final Path d3 = Paths.get(dest, "d1", "d2", "d3");
        Assert.assertTrue(Files.isDirectory(d3), d3 + " directory is missing or not a directory");
        final Path d4 = Paths.get(dest, "d1", "d4");
        Assert.assertTrue(Files.isDirectory(d4), d4 + " directory is missing or not a directory");
        // d1/d4 is expected to be empty directory
        final List<Path> d4Children;
        try (final Stream<Path> s = Files.walk(d4, 1)) {
            d4Children = s.toList();
        }
        Assert.assertEquals(d4Children.size(), 1, "Directory " + d4
                + " has unexpected files/dirs: " + d4Children);
        final Path f1 = Paths.get(dest, "f1.txt");
        Assert.assertTrue(Files.isRegularFile(f1), f1 + " is missing or not a file");
        Assert.assertEquals(Files.readAllBytes(f1), FILE_CONTENT, "Unexpected content in file " + f1);
        final Path f2 = Paths.get(d3.toString(), "f2.txt");
        Assert.assertTrue(Files.isRegularFile(f2), f2 + " is missing or not a file");
        Assert.assertEquals(Files.readAllBytes(f2), FILE_CONTENT, "Unexpected content in file " + f2);
    }

    /**
     * Creates a jar whose entries have a leading slash and the dot-dot character preserved.
     * This is the same as creating a jar using {@code jar -cfP somejar.jar <file1> <file2> ...}
     */
    private static Path createJarWithPFlagSemantics() throws IOException {
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final Path jarPath = Paths.get(tmpDir, "8173970-test-withpflag.jar");
        final JarBuilder builder = new JarBuilder(jarPath.toString());
        builder.addEntry("d1/", new byte[0]);
        builder.addEntry("d1/d2/", new byte[0]);
        builder.addEntry(LEADING_SLASH_PRESERVED_ENTRY, FILE_CONTENT);
        builder.addEntry("d1/d2/../f2.txt", FILE_CONTENT);
        builder.build();
        return jarPath;
    }

    private static void printJarCommand(final String[] cmdArgs) {
        System.out.println("Running 'jar " + String.join(" ", cmdArgs) + "'");
    }
}
/*
 * Copyright (c) 2020 Microsoft Corporation. All rights reserved.
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
 *
 */

import java.lang.ProcessBuilder;

import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.AfterTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/* @test
 * @summary Test Files' public APIs with drives created using the subst command on Windows.
 * @requires (os.family == "windows")
 * @library /test/lib ..
 * @build SubstDrive
 * @run testng SubstDrive
 */
public class SubstDrive {

    private static final Path SUBST_DRIVE = Path.of("X:");
    private static Path TEST_TEMP_FOLDER;

    /**
     * Create a temporary directory where all subsequently created temp
     * directories will be in. This folder and all of its contents will be
     * deleted when the test finishes.
     */
    @BeforeTest
    public void createRootTempDirectory() throws IOException {
        TEST_TEMP_FOLDER = TestUtil.createTemporaryDirectory();
        System.out.printf("Test folder is at %s\n", TEST_TEMP_FOLDER);
    }

    /**
     * Delete the root temporary directory together with all of its contents
     * when all tests finish.
     */
    @AfterTest
    public void removeRootTempDirectory() throws IOException {
        TestUtil.removeAll(TEST_TEMP_FOLDER);
    }

    /**
     * Each test method maps drive `SUBST_DRIVE` to a temporary folder,
     * unmap the drive after every test so that subsequent ones can reuse
     * the drive.
     */
    @AfterMethod
    public void deleteSubstDrive() throws IOException {
        Stream<String> substitutedDrives = substList();
        // Only delete `SUBST_DRIVE` if it is currently being substituted
        if (substitutedDrives.anyMatch(e -> e.contains(SUBST_DRIVE.toString()))) {
            substDelete(SUBST_DRIVE);
        }
    }

    /**
     * Test whether files can be created in the substituted drive.
     */
    @Test
    public void testCreateAndDeleteFile() throws IOException {
        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        substCreate(SUBST_DRIVE, tempDirectory);

        String fileContents = "Hello world!";
        Path p = Path.of(SUBST_DRIVE.toString(), "testFile.txt");
        Files.createFile(p);

        Assert.assertTrue(Files.exists(p));

        Files.writeString(p, fileContents);
        Assert.assertEquals(Files.readString(p), fileContents);
    }

    /**
     * Test if we can delete the substituted drive (essentially just a folder).
     */
    @Test
    public void testDeleteSubstitutedDrive() throws IOException {
        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        substCreate(SUBST_DRIVE, tempDirectory);

        Assert.assertTrue(Files.exists(tempDirectory));
        Files.delete(SUBST_DRIVE);
        Assert.assertTrue(Files.notExists(tempDirectory));
    }

    /**
     * Test if the attributes returned by the Files' APIs are consistent when
     * using the actual path and the substituted path.
     */
    @Test
    public void testAttributes() throws IOException {
        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        substCreate(SUBST_DRIVE, tempDirectory);

        Assert.assertTrue(Files.isSameFile(tempDirectory, SUBST_DRIVE));

        Assert.assertEquals(
            Files.isExecutable(tempDirectory),
            Files.isExecutable(SUBST_DRIVE));

        Assert.assertEquals(
            Files.isReadable(tempDirectory),
            Files.isReadable(SUBST_DRIVE));

        Assert.assertEquals(
            Files.isDirectory(tempDirectory),
            Files.isDirectory(SUBST_DRIVE));

        Assert.assertEquals(
            Files.isHidden(tempDirectory),
            Files.isHidden(SUBST_DRIVE));

        Assert.assertEquals(
            Files.isRegularFile(tempDirectory),
            Files.isRegularFile(SUBST_DRIVE));

        Assert.assertEquals(
            Files.isSymbolicLink(tempDirectory),
            Files.isSymbolicLink(SUBST_DRIVE));

        Assert.assertEquals(
            Files.getOwner(tempDirectory),
            Files.getOwner(SUBST_DRIVE));

        Assert.assertEquals(
            Files.isWritable(tempDirectory),
            Files.isWritable(SUBST_DRIVE));
    }

    /**
     * Test if setting attributes for a substituted path works the same way
     * as it would for a real path.
     */
    @Test
    public void testGetSetAttributes() throws IOException {
        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        substCreate(SUBST_DRIVE, tempDirectory);

        Files.setAttribute(SUBST_DRIVE, "dos:hidden", true);
        Assert.assertTrue(Files.isHidden(SUBST_DRIVE));
        Assert.assertTrue(Files.isHidden(tempDirectory));

        Files.setAttribute(tempDirectory, "dos:hidden", false);
        Assert.assertFalse(Files.isHidden(SUBST_DRIVE));
        Assert.assertFalse(Files.isHidden(tempDirectory));

        Map<String, Object> attr1 = Files.readAttributes(SUBST_DRIVE, "*");
        Map<String, Object> attr2 = Files.readAttributes(tempDirectory, "*");
        Assert.assertEquals(attr1, attr2);
    }

    /**
     * Test if the FileStores returned from using substituted path and real path
     * are the same.
     */
    @Test
    public void testFileStore() throws IOException {
        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        substCreate(SUBST_DRIVE, tempDirectory);

        FileStore fileStore1 = Files.getFileStore(tempDirectory);
        FileStore fileStore2 = Files.getFileStore(SUBST_DRIVE);

        Assert.assertEquals(
            fileStore1.getTotalSpace(),
            fileStore2.getTotalSpace());

        Assert.assertEquals(
            fileStore1.getBlockSize(),
            fileStore2.getBlockSize());

        Assert.assertEquals(
            fileStore1.getUnallocatedSpace(),
            fileStore2.getUnallocatedSpace());

        Assert.assertEquals(
            fileStore1.getUsableSpace(),
            fileStore2.getUsableSpace());

        Assert.assertEquals(
            fileStore1.name(),
            fileStore2.name());

        Assert.assertEquals(
            fileStore1.type(),
            fileStore2.type());

        Assert.assertEquals(
            SUBST_DRIVE.getFileSystem().getRootDirectories(),
            tempDirectory.getFileSystem().getRootDirectories());
    }

    /**
     * Test if Files.copy works correctly on a substituted drive, and that
     * all of the attributes are the same.
     */
    @Test
    public void testMoveAndCopySubstDrive() throws IOException {
        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        Path tempDirectoryCopy = Path.of(tempDirectory.toString() + "_copy");

        substCreate(SUBST_DRIVE, tempDirectory);

        Files.copy(SUBST_DRIVE, tempDirectoryCopy);

        Assert.assertEquals(
            Files.isExecutable(SUBST_DRIVE),
            Files.isExecutable(tempDirectoryCopy));

        Assert.assertEquals(
            Files.isReadable(SUBST_DRIVE),
            Files.isReadable(tempDirectoryCopy));

        Assert.assertEquals(
            Files.isDirectory(SUBST_DRIVE),
            Files.isDirectory(tempDirectoryCopy));

        Assert.assertEquals(
            Files.isHidden(SUBST_DRIVE),
            Files.isHidden(tempDirectoryCopy));

        Assert.assertEquals(
            Files.isRegularFile(SUBST_DRIVE),
            Files.isRegularFile(tempDirectoryCopy));

        Assert.assertEquals(
            Files.isWritable(SUBST_DRIVE),
            Files.isWritable(tempDirectoryCopy));

        Assert.assertEquals(
            Files.getOwner(SUBST_DRIVE),
            Files.getOwner(tempDirectoryCopy));
    }

    /**
     * Test if .toRealPath() works consistently when both the resolved symlink
     * and its target are in the same substituted drive.
     * https://bugs.openjdk.java.net/browse/JDK-8213216
     * Note: requires administrator privileges.
     */
    @Test
    public void testRealPath() throws IOException {
        if (!TestUtil.supportsLinks(TEST_TEMP_FOLDER)) {
            return;
        }

        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        substCreate(SUBST_DRIVE, tempDirectory);

        Path substDir = Files.createDirectory(SUBST_DRIVE.resolve("folder"));
        Path dir = tempDirectory.resolve("folder");

        Path substFile = Files.createFile(substDir.resolve("foo"));
        Path substLink = substDir.resolve("link");

        Path file = dir.resolve("foo");
        Path link = dir.resolve("link");

        Files.createSymbolicLink(substLink, substFile.toAbsolutePath());

        Assert.assertEquals(file.toRealPath(), link.toRealPath());
        Assert.assertEquals(substFile.toRealPath(), substLink.toRealPath());
    }

    /**
     * Test if the attributes of a resolved symlink are the same as its target's
     * Note: requires administrator privileges.
     */
    @Test
    public void testGetResolvedSymlinkAttribute() throws IOException {
        if (!TestUtil.supportsLinks(TEST_TEMP_FOLDER)) {
            return;
        }

        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        substCreate(SUBST_DRIVE, tempDirectory);

        Path tempFile = Path.of(SUBST_DRIVE.toString(), "test.txt");
        String contents = "Hello world!";
        Files.writeString(tempFile, contents);
        Assert.assertEquals(Files.readString(tempFile), contents);

        Path link = Path.of(SUBST_DRIVE.toString(), "link");
        Files.createSymbolicLink(link, tempFile);

        Assert.assertEquals(Files.readString(link), contents);
        Assert.assertEquals(Files.isExecutable(link), Files.isExecutable(tempFile));
        Assert.assertEquals(Files.isReadable(link), Files.isReadable(tempFile));
        Assert.assertEquals(Files.isDirectory(link), Files.isDirectory(tempFile));
        Assert.assertEquals(Files.isHidden(link), Files.isHidden(tempFile));
        Assert.assertEquals(Files.isRegularFile(link), Files.isRegularFile(tempFile));
        Assert.assertEquals(Files.isWritable(link), Files.isWritable(tempFile));
        Assert.assertEquals(Files.getOwner(link), Files.getOwner(tempFile));
    }

    /**
     * Test if files and folders can be created, moved, and cut when the
     * substituted drive is a symlink.
     * Note: requires administrator privileges.
     */
    @Test
    public void testSubstWithSymlinkedFolder() throws IOException {
        if (!TestUtil.supportsLinks(TEST_TEMP_FOLDER)) {
            return;
        }

        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        Path tempLink = Path.of(tempDirectory.toString() + "_link");
        Files.createSymbolicLink(tempLink, tempDirectory);

        substCreate(SUBST_DRIVE, tempLink);

        Assert.assertEquals(
            Files.readAttributes(SUBST_DRIVE, "*"),
            Files.readAttributes(tempDirectory, "*"));

        Assert.assertTrue(Files.isWritable(SUBST_DRIVE));

        Path tempFile = Files.createTempFile(SUBST_DRIVE, "prefix", "suffix");
        String contents = "Hello world!";
        Files.writeString(tempFile, contents);
        Assert.assertEquals(Files.readString(tempFile), contents);

        Path tempDirectory2 = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        Path copy = Path.of(tempDirectory2.toString(), "copied");
        Files.copy(tempFile, copy);

        Assert.assertTrue(Files.exists(copy));
        Assert.assertEquals(Files.readString(copy), contents);

        Path cut = Path.of(tempDirectory2.toString(), "cut");
        Files.move(tempFile, cut);
        Assert.assertTrue(Files.notExists(tempFile));
        Assert.assertTrue(Files.exists(cut));
        Assert.assertEquals(Files.readString(cut), contents);
    }

    /**
     * When the substituted drive is a symlink, test if it has the same
     * attributes as its target.
     * Note: requires administrator privileges.
     */
    @Test
    public void testMoveAndCopyFilesToSymlinkedDrive() throws IOException {
        if (!TestUtil.supportsLinks(TEST_TEMP_FOLDER)) {
            return;
        }

        Path tempDirectory = TestUtil.createTemporaryDirectory(TEST_TEMP_FOLDER);
        Path tempLink = Path.of(tempDirectory.toString() + "_link");
        Files.createSymbolicLink(tempLink, tempDirectory);

        substCreate(SUBST_DRIVE, tempLink);

        Assert.assertEquals(
            Files.readAttributes(SUBST_DRIVE, "*"),
            Files.readAttributes(tempDirectory, "*"));

        Assert.assertTrue(Files.isWritable(SUBST_DRIVE));
    }

    /**
     * Run a command and optionally prints stdout contents to
     * `customOutputStream`.
     */
    private void runCmd(ProcessBuilder pb, PrintStream customOutputStream) {
        try {
            PrintStream ps = customOutputStream != null ?
                                    customOutputStream :
                                    System.out;
            OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(pb)
                                            .outputTo(ps)
                                            .errorTo(System.err);

            int exitCode = outputAnalyzer.getExitValue();
            Assert.assertEquals(
                exitCode /* actual value */,
                0        /* expected value */,
                String.format(
                    "Command `%s` failed with exit code %d",
                    pb.command(),
                    exitCode
                )
            );

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Helper to map a path to a drive letter using subst.
     * For reference, see:
     * https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/subst
     */
    private void substCreate(Path drive, Path path) {
        runCmd(
            new ProcessBuilder(
                "cmd", "/c", "subst", drive.toString(), path.toString()),
            null /* customOutputStream */);
    }

    /**
     * Delete a drive mapping using subst.
     * For reference, see:
     * https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/subst
     */
    private void substDelete(Path drive) throws IOException {
        runCmd(
            new ProcessBuilder(
                "cmd", "/c", "subst", drive.toString(), "/D"),
            null /* customOutputStream */);
    }

    /**
     * Return a list of strings that represents all the currently mapped drives.
     * For instance, with the following output of subst:
     *      A:\: => path1
     *      B:\: => path2
     *      T:\: => path3
     *      X:\: => path4
     * The function returns: ["A:\", "B:\", "T:\", "X:\"]
     * For reference, see:
     * https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/subst
     */
    private Stream<String> substList() throws UnsupportedEncodingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String utf8 = StandardCharsets.UTF_8.name();
        try (PrintStream ps = new PrintStream(baos, true, utf8)) {
            // subst without any arguments returns a list of drives that
            // are being substituted
            runCmd(new ProcessBuilder("cmd", "/c", "subst"), ps);
            String stdout = baos.toString(utf8);
            return stdout
                    // split lines
                    .lines()
                    // only examine lines with "=>"
                    .filter(line -> line.contains("=>"))
                    // split each line into 2 components and take the first one
                    .map(line -> line.split("=>")[0].trim());
        }
    }
}

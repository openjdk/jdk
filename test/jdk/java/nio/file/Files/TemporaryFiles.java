/*
 * Copyright (c) 2008, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6838333 7006126 7023034 8391574
 * @summary Unit test for Files.createTempXXX
 * @run junit ${test.main.class}
 */

import java.io.IOException;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class TemporaryFiles {

    // system-wide tmp dir
    static final Path SYS_TMPDIR = Path.of(System.getProperty("java.io.tmpdir"));

    // local test tmp dir
    static Path TEST_TMPDIR;

    @BeforeAll
    static void setup() throws Exception {
        TEST_TMPDIR = Files.createTempDirectory(Path.of("."), "adir");
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.delete(TEST_TMPDIR);
    }

    /**
     * Returns directory, prefix, and suffix combinations for temporary file tests.
     */
    static Stream<Arguments> tempFiles() {
        return Stream.of(
                Arguments.arguments(null, null, null),
                Arguments.arguments(null, "blah", null),
                Arguments.arguments(null, "", null),
                Arguments.arguments(null, null, ".dat"),
                Arguments.arguments(null, null, ""),
                Arguments.arguments(null, "blah", ".dat"),
                Arguments.arguments(TEST_TMPDIR, null, null),
                Arguments.arguments(TEST_TMPDIR, "blah", null),
                Arguments.arguments(TEST_TMPDIR, "", null),
                Arguments.arguments(TEST_TMPDIR, null, ".dat"),
                Arguments.arguments(TEST_TMPDIR, null, ""),
                Arguments.arguments(TEST_TMPDIR, "blah", ".dat")
        );
    }

    @ParameterizedTest
    @MethodSource("tempFiles")
    void testTempFile(Path dir, String prefix, String suffix) throws Exception {
        Path file = (dir == null) ?
            Files.createTempFile(prefix, suffix) :
            Files.createTempFile(dir, prefix, suffix);
        try {
            // check file name
            String name = file.getFileName().toString();
            if (prefix != null && !prefix.isEmpty()) {
                assertTrue(name.startsWith(prefix), "Should start with " + prefix);
            }
            if (suffix == null || !suffix.isEmpty()) {
                String expectedSuffix = (suffix != null) ? suffix : ".tmp";
                assertTrue(name.endsWith(expectedSuffix), "Should end with " + expectedSuffix);
            }

            // check file is in expected directory
            Path expectedDir = (dir != null) ? dir : SYS_TMPDIR;
            assertEquals(expectedDir, file.getParent(), "Not in expected directory");

            // check file can be opened for reading and writing
            Files.newByteChannel(file, READ).close();
            Files.newByteChannel(file, WRITE).close();
            Files.newByteChannel(file, READ, WRITE).close();

            // check file permissions are 0600 or more secure
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
                perms.remove(PosixFilePermission.OWNER_READ);
                perms.remove(PosixFilePermission.OWNER_WRITE);
                assertTrue(perms.isEmpty(), "Temporary file is not secure");
            }
        } finally {
            Files.delete(file);
        }
    }

    /**
     * Returns directory and prefix combinations for temporary directory tests.
     */
    static Stream<Arguments> tempDirectories() {
        return Stream.of(
                Arguments.arguments(null, null),
                Arguments.arguments(null, "blah"),
                Arguments.arguments(null, ""),
                Arguments.arguments(TEST_TMPDIR, null),
                Arguments.arguments(TEST_TMPDIR, "blah"),
                Arguments.arguments(TEST_TMPDIR, "")
        );
    }

    @ParameterizedTest
    @MethodSource("tempDirectories")
    void testTempDirectory(Path dir, String prefix) throws Exception {
        Path subdir = (dir == null) ?
            Files.createTempDirectory(prefix) :
            Files.createTempDirectory(dir, prefix);
        try {
            // check directory name
            if (prefix != null && !prefix.isEmpty()) {
                String name = subdir.getFileName().toString();
                assertTrue(name.startsWith(prefix), "Should start with " + prefix);
            }

            // check directory is in expected directory
            Path expectedDir = (dir != null) ? dir : SYS_TMPDIR;
            assertEquals(expectedDir, subdir.getParent(), "Not in expected directory");

            // check directory is readable (and empty)
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(subdir)) {
                assertFalse(stream.iterator().hasNext(), "Temporary directory not empty");
            }

            // check directory is writable
            Path file = Files.createFile(subdir.resolve("foo"));
            try {
                Files.newByteChannel(file, READ, WRITE).close();
            } finally {
                Files.delete(file);
            }

            // check file permissions are 0700 or more secure
            if (Files.getFileStore(subdir).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(subdir);
                perms.remove(PosixFilePermission.OWNER_READ);
                perms.remove(PosixFilePermission.OWNER_WRITE);
                perms.remove(PosixFilePermission.OWNER_EXECUTE);
                assertTrue(perms.isEmpty(), "Temporary directory is not secure");
            }
        } finally {
            Files.delete(subdir);
        }
    }

    /**
     * Returns file permissions to restrict perissions of temporay file/directory.
     */
    static Stream<String> permissions() {
        return Stream.of(
                "---------",
                "r--------",
                "-w-------",
                "--x------",
                "rwx------",
                "---r-----",
                "----w----",
                "-----x---",
                "---rwx---",
                "------r--",
                "-------w-",
                "--------x",
                "------rwx",
                "r--r-----",
                "r--r--r--",
                "rw-rw----",
                "rwxrwx---",
                "rw-rw-r--",
                "r-xr-x---",
                "r-xr-xr-x",
                "rwxrwxrwx"
        );
    }

    /**
     * Checks that the actual permissions are not less secure than the requested.
     */
    void checkSecure(Set<PosixFilePermission> requested, Set<PosixFilePermission> actual) {
        assertTrue(actual.stream().allMatch(requested::contains), () ->
                "Actual permissions: " + PosixFilePermissions.toString(actual) +
                ", requested: " + PosixFilePermissions.toString(requested) +
                " - file is less secure than requested");
    }

    @ParameterizedTest
    @MethodSource("permissions")
    @DisabledOnOs(OS.WINDOWS)
    void testPosixAttributes(String permsAsString) throws Exception {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permsAsString);
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);

        if (Files.getFileStore(SYS_TMPDIR).supportsFileAttributeView("posix")) {
            Path file = Files.createTempFile("blah", ".tmp", attr);
            try {
                checkSecure(perms, Files.getPosixFilePermissions(file));
            } finally {
                Files.delete(file);
            }
            Path dir = Files.createTempDirectory("blah", attr);
            try {
                checkSecure(perms, Files.getPosixFilePermissions(dir));
            } finally {
                Files.delete(dir);
            }
        }

        if (Files.getFileStore(TEST_TMPDIR).supportsFileAttributeView("posix")) {
            Path file = Files.createTempFile(TEST_TMPDIR, "blah", ".tmp", attr);
            try {
                checkSecure(perms, Files.getPosixFilePermissions(file));
            } finally {
                Files.delete(file);
            }
            Path dir = Files.createTempDirectory(TEST_TMPDIR, "blah", attr);
            try {
                checkSecure(perms, Files.getPosixFilePermissions(dir));
            } finally {
                Files.delete(dir);
            }
        }
    }

    /**
     * Test Files.createTempXXX with an attribute that cannot be set.
     */
    @Test
    void testUnknownAttribute() {
        var attr  = new FileAttribute<String>() {
            @Override public String name()  { return "unknown"; }
            @Override public String value() { return "foo"; }
        };
        assertThrows(UnsupportedOperationException.class, () -> Files.createTempFile("blah", ".dat", attr));
        assertThrows(UnsupportedOperationException.class, () -> Files.createTempDirectory("blah", attr));
    }

    /**
     * Test Files.createTempXXX with a directory that does not exist.
     */
    @Test
    void testDirDoesNotExist() {
        Path dir = Path.of("DoesNotExist");
        assertTrue(Files.notExists(dir));
        assertThrows(IOException.class, () -> Files.createTempFile(dir, null, null));
        assertThrows(IOException.class, () -> Files.createTempFile(dir, "blah", null));
        assertThrows(IOException.class, () -> Files.createTempFile(dir, null, ".dat"));
        assertThrows(IOException.class, () -> Files.createTempFile(dir, "blah", ".dat"));
        assertThrows(IOException.class, () -> Files.createTempDirectory(dir, null));
        assertThrows(IOException.class, () -> Files.createTempDirectory(dir, "blah"));
    }

    /**
     * Returns prefixes that should be rejected.
     */
    static Stream<String> badPrefixes() {
        return Stream.of(
                "/blah",
                "../blah",
                "dir/blah",
                "foo\0bar"
        );
    }

    @ParameterizedTest
    @MethodSource("badPrefixes")
    void testBadPrefix(String prefix) {
        Path dir = Path.of(".");
        assertThrows(IllegalArgumentException.class, () -> Files.createTempFile(prefix, null));
        assertThrows(IllegalArgumentException.class, () -> Files.createTempFile(dir, prefix, null));
        assertThrows(IllegalArgumentException.class, () -> Files.createTempDirectory(prefix));
        assertThrows(IllegalArgumentException.class, () -> Files.createTempDirectory(dir, prefix));
    }

    /**
     * Returns prefixes that should be rejected on Windows.
     */
    static Stream<String> badWindowsPrefixes() {
        return Stream.of(
                "\\\\server",
                "\\\\server\\share",
                "\\\\?\\UNC\\server\\share",
                "C:\\temp\\blah",
                "C:temp\\blah",
                "C:blah"
        );
    }

    /**
     * Tests prefixes that should be rejected on Windows.
     */
    @ParameterizedTest
    @MethodSource("badWindowsPrefixes")
    @EnabledOnOs(OS.WINDOWS)
    void testBadWindowsPrefixes(String prefix) {
        testBadPrefix(prefix);
    }

    /**
     * Returns suffixes that should be rejected.
     */
    static Stream<String> badSuffixes() {
        return Stream.of(
                ".dat/foo",
                "foo\0bar"
        );
    }

    @ParameterizedTest
    @MethodSource("badSuffixes")
    void testBadSuffix(String suffix) {
        Path dir = Path.of(".");
        assertThrows(IllegalArgumentException.class, () -> Files.createTempFile("blah", suffix));
        assertThrows(IllegalArgumentException.class, () -> Files.createTempFile(dir, "blah", suffix));
    }

    /**
     * Returns suffixes that should be rejected on Windows.
     */
    static Stream<String> badWindowsSuffixes() {
        return Stream.of(
                ".dat\\foo",
                ":"
        );
    }

    @ParameterizedTest
    @MethodSource("badWindowsSuffixes")
    @EnabledOnOs(OS.WINDOWS)
    void testBadWindowsSuffix(String suffix) {
        testBadSuffix(suffix);
    }

    /**
     * Test nulls.
     */
    @Test
    void testNulls() {
        assertThrows(NullPointerException.class,
                () -> Files.createTempFile("blah", ".tmp", (FileAttribute<?>[]) null));
        assertThrows(NullPointerException.class,
                () -> Files.createTempFile("blah", ".tmp", new FileAttribute<?>[] { null }));
        assertThrows(NullPointerException.class,
                () -> Files.createTempDirectory("blah", (FileAttribute<?>[]) null));
        assertThrows(NullPointerException.class,
                () -> Files.createTempDirectory("blah", new FileAttribute<?>[] { null }));
        assertThrows(NullPointerException.class,
                () -> Files.createTempFile((Path)null, "blah", ".tmp"));
        assertThrows(NullPointerException.class,
                () -> Files.createTempDirectory((Path)null, "blah"));
    }
}

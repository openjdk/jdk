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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8350881
 * @summary Unit tests for ZipFileSystem and associated classes.
 * @modules jdk.zipfs
 * @run junit ZipFileSystemTest
 * @run junit/othervm ZipFileSystemTest
 */
/**
 /*
 * Modernized JUnit 5 tests for ZipFileSystem. Any new tests (regression or
 * otherwise) should go in here, or in a similar JUnit based test classes.
 *
 * <p>Please do not add new tests to places like {@link ZFSTests} or
 * {@link ZipFSTester}. If migrating tests to this class, please use JUnit
 * features such as {@link TempDir @TempDir} etc.
 */
public class ZipFileSystemTest {
    @Test
    void noSuchFileFailure(@TempDir Path tempDir) throws IOException {
        // Temporary directory is always empty.
        Path noSuchFile = tempDir.resolve("no_such.zip");
        // No such file.
        assertThrows(NoSuchFileException.class,
                () -> newZipFileSystem(noSuchFile).close());
        assertThrows(NoSuchFileException.class,
                () -> newZipFileSystem(noSuchFile, ZipFSOpts.READ_ONLY).close());
        assertThrows(NoSuchFileException.class,
                () -> newZipFileSystem(noSuchFile, ZipFSOpts.READ_WRITE).close());
    }

    @Test
    void badArgumentsFailure(@TempDir Path tempDir) throws IOException {
        // Disallowed combinations of options (trumps file existence check).
        Path noSuchFile = tempDir.resolve("no_such.zip");
        assertThrows(IllegalArgumentException.class,
                () -> newZipFileSystem(noSuchFile, ZipFSOpts.CREATE, ZipFSOpts.READ_ONLY).close());
        assertThrows(IllegalArgumentException.class,
                () -> FileSystems.newFileSystem(noSuchFile, Map.of("accessMode", "badValue")).close());
    }

    @Test
    void readOnlyZipFileFailure(@TempDir Path tempDir) throws IOException {
        // Underlying file is read-only.
        Path readOnlyZip = Utils.createJarFile(tempDir.resolve("read_only.zip"), Map.of("file.txt", "Hello World"));
        try {
            readOnlyZip.toFile().setReadOnly();
            assertThrows(IOException.class, () -> newZipFileSystem(readOnlyZip, ZipFSOpts.READ_WRITE).close());
        } finally {
            Files.delete(readOnlyZip);
        }
    }

    @Test
    void multiReleaseJarFailure(@TempDir Path tempDir) throws IOException {
        // Multi-release JARs, when opened with a specified version are inherently read-only.
        Path multiReleaseJar = Utils.createJarFile(tempDir.resolve("multi_release.jar"), Map.of(
                // Newline required for attribute to be read from Manifest file.
                "META-INF/MANIFEST.MF", "Multi-Release: true\n",
                "META-INF/versions/1/file.txt", "First version",
                "META-INF/versions/2/file.txt", "Second version",
                "file.txt", "Default version"));
        // Check the JAR can be opened in read/write mode when no version is specified.
        Map<String, Object> env = ZipFSOpts.toEnvMap(ZipFSOpts.READ_WRITE);

        try (FileSystem fs = FileSystems.newFileSystem(multiReleaseJar, env)) {
            if (!"Default version".equals(Files.readString(fs.getPath("file.txt"), UTF_8))) {
                throw new RuntimeException("unexpected file content");
            }
        }

        // But once the version is set, it can only be opened read-only.
        env.put("releaseVersion", 2);
        assertThrows(IOException.class, () -> FileSystems.newFileSystem(multiReleaseJar, env).close());

        ZipFSOpts.READ_ONLY.setIn(env);
        try (FileSystem fs = FileSystems.newFileSystem(multiReleaseJar, env)) {
            if (!"Second version".equals(Files.readString(fs.getPath("file.txt"), UTF_8))) {
                throw new RuntimeException("unexpected file content");
            }
            if (!fs.isReadOnly()) {
                throw new RuntimeException("expected read-only file system");
            }
        }
    }

    @Test
    public void readOnlyFileSystem(@TempDir Path tempDir) throws IOException {
        Path testZip = Utils.createJarFile(tempDir.resolve("test.zip"), Map.of(
                "file1.txt", "Some content...",
                "file2.txt", "More content..."));
        try (FileSystem zfs = newZipFileSystem(testZip, ZipFSOpts.READ_ONLY, ZipFSOpts.POSIX)) {
            assertTrue(zfs.isReadOnly(), "File system should be read-only");
            Path root = zfs.getPath("/");

            // Rather than calling something like "addOwnerRead(root)", we walk all
            // files to ensure that all operations fail, not some arbitrary first one.
            Set<PosixFilePermission> badPerms = Set.of(OTHERS_EXECUTE, OTHERS_WRITE);
            FileTime anyTime = FileTime.from(Instant.now());
            try (Stream<Path> paths = Files.walk(root)) {
                paths.forEach(file -> {
                    assertFalse(Files.isWritable(file), "File should not be writable: " + file);
                    assertSame(zfs, file.getFileSystem());
                    assertThrows(
                            AccessDeniedException.class,
                            () -> zfs.provider().checkAccess(file, AccessMode.WRITE));
                    assertThrows(
                            ReadOnlyFileSystemException.class,
                            () -> zfs.provider().setAttribute(file, "zip:permissions", badPerms));

                    // These fail because there is not corresponding File for a zip path (they will
                    // currently fail for read-write ZIP file systems too, but we sanity-check here).
                    assertThrows(
                            UnsupportedOperationException.class,
                            () -> Files.setLastModifiedTime(file, anyTime));
                    assertThrows(
                            UnsupportedOperationException.class,
                            () -> Files.setAttribute(file, "zip:permissions", badPerms));
                    assertThrows(
                            UnsupportedOperationException.class,
                            () -> Files.setPosixFilePermissions(file, badPerms));
                });
            }
        }
    }


    private static FileSystem newZipFileSystem(Path path, ZipFSOpts... opts) throws IOException {
        return FileSystems.newFileSystem(path, ZipFSOpts.toEnvMap(opts));
    }
}

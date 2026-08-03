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
 * @bug 4313887 6838333 8343020 8357425
 * @summary Unit test for java.nio.file.SecureDirectoryStream
 * @requires (os.family == "linux" | os.family == "mac" | os.family == "aix")
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 * @run junit SecureDS
 */

import java.nio.file.*;
import static java.nio.file.Files.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;

import jdk.test.lib.Platform;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SecureDS {
    static boolean supportsSymbolicLinks;

    @ParameterizedTest
    @ValueSource(strings = {"tmp","cwd"})
    public void testSecureDS(String mode) throws IOException {
        Path dir;
        if (mode.equals("cwd")) {
            dir = TestUtil.createTemporaryDirectory(System.getProperty("user.dir"));
        } else {
            dir = TestUtil.createTemporaryDirectory();
        }
        try {
            DirectoryStream<Path> stream = newDirectoryStream(dir);
            stream.close();
            assumeTrue(stream instanceof SecureDirectoryStream);

            supportsSymbolicLinks = TestUtil.supportsSymbolicLinks(dir);

            // run tests
            doBasicTests(dir);
            doMoveTests(dir);
            doSetPermissions(dir);
            miscTests(dir);

        } finally {
            TestUtil.removeAll(dir);
        }
    }

    // Exercise each of SecureDirectoryStream's method (except move)
    static void doBasicTests(Path dir) throws IOException {
        Path dir1 = createDirectory(dir.resolve("dir1"));
        Path dir2 = dir.resolve("dir2");

        // create a file, directory, and two sym links in the directory
        Path fileEntry = Paths.get("myfile");
        createFile(dir1.resolve(fileEntry));
        Path dirEntry = Paths.get("mydir");
        createDirectory(dir1.resolve(dirEntry));
        // myfilelink -> myfile
        Path link1Entry = Paths.get("myfilelink");
        if (supportsSymbolicLinks)
            createSymbolicLink(dir1.resolve(link1Entry), fileEntry);
        // mydirlink -> mydir
        Path link2Entry = Paths.get("mydirlink");
        if (supportsSymbolicLinks)
            createSymbolicLink(dir1.resolve(link2Entry), dirEntry);

        // open directory and then move it so that it is no longer accessible
        // via its original path.
        SecureDirectoryStream<Path> stream =
            (SecureDirectoryStream<Path>)newDirectoryStream(dir1);
        move(dir1, dir2);

        // Test: iterate over all entries
        int count = 0;
        for (Path entry: stream) { count++; }
        assertEquals((supportsSymbolicLinks ? 4 : 2), count);

        // Test: getFileAttributeView to access directory's attributes
        assertTrue(stream
            .getFileAttributeView(BasicFileAttributeView.class)
                .readAttributes()
                    .isDirectory());

        // Test: getFileAttributeView to access attributes of entries
        assertTrue(stream
            .getFileAttributeView(fileEntry, BasicFileAttributeView.class)
                .readAttributes()
                    .isRegularFile());
        assertTrue(stream
            .getFileAttributeView(fileEntry, BasicFileAttributeView.class, NOFOLLOW_LINKS)
                .readAttributes()
                    .isRegularFile());
        assertTrue(stream
            .getFileAttributeView(dirEntry, BasicFileAttributeView.class)
                .readAttributes()
                    .isDirectory());
        assertTrue(stream
            .getFileAttributeView(dirEntry, BasicFileAttributeView.class, NOFOLLOW_LINKS)
                .readAttributes()
                    .isDirectory());
        if (supportsSymbolicLinks) {
            assertTrue(stream
                .getFileAttributeView(link1Entry, BasicFileAttributeView.class)
                    .readAttributes()
                        .isRegularFile());
            assertTrue(stream
                .getFileAttributeView(link1Entry, BasicFileAttributeView.class, NOFOLLOW_LINKS)
                    .readAttributes()
                        .isSymbolicLink());
            assertTrue(stream
                .getFileAttributeView(link2Entry, BasicFileAttributeView.class)
                    .readAttributes()
                        .isDirectory());
            assertTrue(stream
                .getFileAttributeView(link2Entry, BasicFileAttributeView.class, NOFOLLOW_LINKS)
                    .readAttributes()
                        .isSymbolicLink());
        }

        // Test: newByteChannel
        Set<StandardOpenOption> opts = Collections.emptySet();
        stream.newByteChannel(fileEntry, opts).close();
        if (supportsSymbolicLinks) {
            stream.newByteChannel(link1Entry, opts).close();
            assertThrows(IOException.class, () -> {
                Set<OpenOption> mixed = new HashSet<>();
                mixed.add(READ);
                mixed.add(NOFOLLOW_LINKS);
                stream.newByteChannel(link1Entry, mixed).close();
            });
        }

        // Test: newDirectoryStream
        stream.newDirectoryStream(dirEntry).close();
        stream.newDirectoryStream(dirEntry, LinkOption.NOFOLLOW_LINKS).close();
        if (supportsSymbolicLinks) {
            stream.newDirectoryStream(link2Entry).close();
            assertThrows(IOException.class, () -> {
                stream.newDirectoryStream(link2Entry, LinkOption.NOFOLLOW_LINKS)
                    .close();
            });
        }

        // Test: delete
        if (supportsSymbolicLinks) {
            stream.deleteFile(link1Entry);
            stream.deleteFile(link2Entry);
        }
        stream.deleteDirectory(dirEntry);
        stream.deleteFile(fileEntry);

        // clean-up
        stream.close();
        delete(dir2);
    }

    // Exercise setting permisions on the SecureDirectoryStream's view
    static void doSetPermissions(Path dir) throws IOException {
        Path aDir = createDirectory(dir.resolve("dir"));

        Set<PosixFilePermission> noperms = EnumSet.noneOf(PosixFilePermission.class);
        Set<PosixFilePermission> permsDir = getPosixFilePermissions(aDir);

        try (SecureDirectoryStream<Path> stream =
             (SecureDirectoryStream<Path>)newDirectoryStream(aDir);) {

            // Test setting permission on directory with no permissions
            setPosixFilePermissions(aDir, noperms);
            assertEquals(noperms, getPosixFilePermissions(aDir));
            PosixFileAttributeView view = stream.getFileAttributeView(PosixFileAttributeView.class);
            view.setPermissions(permsDir);
            assertEquals(permsDir, getPosixFilePermissions(aDir));

            if (supportsSymbolicLinks) {
                // Create a file and a link to the file
                Path fileEntry = Path.of("file");
                Path file = createFile(aDir.resolve(fileEntry));
                Set<PosixFilePermission> permsFile = getPosixFilePermissions(file);
                Path linkEntry = Path.of("link");
                Path link = createSymbolicLink(aDir.resolve(linkEntry), fileEntry);
                Set<PosixFilePermission> permsLink = getPosixFilePermissions(link, NOFOLLOW_LINKS);

                // Test following link to file
                view = stream.getFileAttributeView(link, PosixFileAttributeView.class);
                view.setPermissions(noperms);
                assertEquals(noperms, getPosixFilePermissions(file));
                assertEquals(permsLink, getPosixFilePermissions(link, NOFOLLOW_LINKS));
                view.setPermissions(permsFile);
                assertEquals(permsFile, getPosixFilePermissions(file));
                assertEquals(permsLink, getPosixFilePermissions(link, NOFOLLOW_LINKS));
                // Symbolic link permissions do not apply on Linux
                if (!Platform.isLinux()) {
                    // Test not following link to file
                    view = stream.getFileAttributeView(link, PosixFileAttributeView.class, NOFOLLOW_LINKS);
                    view.setPermissions(noperms);
                    assertEquals(permsFile, getPosixFilePermissions(file));
                    assertEquals(noperms, getPosixFilePermissions(link, NOFOLLOW_LINKS));
                    view.setPermissions(permsLink);
                    assertEquals(permsFile, getPosixFilePermissions(file));
                    assertEquals(permsLink, getPosixFilePermissions(link, NOFOLLOW_LINKS));
                }

                delete(link);
                delete(file);
            }

            // clean-up
            delete(aDir);
        }
    }

    // Exercise SecureDirectoryStream's move method
    static void doMoveTests(Path dir) throws IOException {
        Path dir1 = createDirectory(dir.resolve("dir1"));
        Path dir2 = createDirectory(dir.resolve("dir2"));

        // create dir1/myfile, dir1/mydir, dir1/mylink
        Path fileEntry = Paths.get("myfile");
        createFile(dir1.resolve(fileEntry));
        Path dirEntry = Paths.get("mydir");
        createDirectory(dir1.resolve(dirEntry));
        Path linkEntry = Paths.get("mylink");
        if (supportsSymbolicLinks)
            createSymbolicLink(dir1.resolve(linkEntry), Paths.get("missing"));

        // target name
        Path target = Paths.get("newfile");

        // open stream to both directories
        SecureDirectoryStream<Path> stream1 =
            (SecureDirectoryStream<Path>)newDirectoryStream(dir1);
        SecureDirectoryStream<Path> stream2 =
            (SecureDirectoryStream<Path>)newDirectoryStream(dir2);

        // Test: move dir1/myfile -> dir2/newfile
        stream1.move(fileEntry, stream2, target);
        assertTrue(notExists(dir1.resolve(fileEntry)));
        assertTrue(exists(dir2.resolve(target)));
        stream2.deleteFile(target);

        // Test: move dir1/mydir -> dir2/newfile
        stream1.move(dirEntry, stream2, target);
        assertTrue(notExists(dir1.resolve(dirEntry)));
        assertTrue(exists(dir2.resolve(target)));
        stream2.deleteDirectory(target);

        // Test: move dir1/mylink -> dir2/newfile
        if (supportsSymbolicLinks) {
            stream1.move(linkEntry, stream2, target);
            assertTrue(isSymbolicLink(dir2.resolve(target)));
            stream2.deleteFile(target);
        }

        // Test: move between devices
        String testDirAsString = System.getProperty("test.dir");
        if (testDirAsString != null) {
            Path testDir = Paths.get(testDirAsString);
            if (!getFileStore(dir1).equals(getFileStore(testDir))) {
                SecureDirectoryStream<Path> ts =
                    (SecureDirectoryStream<Path>)newDirectoryStream(testDir);
                createFile(dir1.resolve(fileEntry));
                assertThrows(AtomicMoveNotSupportedException.class, () -> {
                    stream1.move(fileEntry, ts, target);
                });
                ts.close();
                stream1.deleteFile(fileEntry);
            }
        }

        // Test: move to cwd
        final String TEXT = "Sous le pont Mirabeau coule la Seine";
        Path file = Path.of("file");
        Path filepath = dir.resolve(file);
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path result = cwd.resolve(file);
        Files.writeString(filepath, TEXT);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir);) {
            if (ds instanceof SecureDirectoryStream<Path> sds) {
                try {
                    sds.move(file, null, file);
                } catch (AtomicMoveNotSupportedException e) {
                    assumeTrue(Files.getFileStore(cwd).equals(Files.getFileStore(dir)));
                    // re-throw if move between same volume
                    throw e;
                }
                assertEquals(TEXT, Files.readString(result), result + " content incorrect");
            } else {
                fail("Not a SecureDirectoryStream");
            }
        } finally {
            boolean fileDeleted = Files.deleteIfExists(filepath);
            if (!fileDeleted)
                Files.deleteIfExists(result);
            // clean-up
            delete(dir1);
            delete(dir2);
        }
    }

    // null and ClosedDirectoryStreamException
    static void miscTests(Path dir) throws IOException {
        Path file = Paths.get("file");
        createFile(dir.resolve(file));

        SecureDirectoryStream<Path> stream =
            (SecureDirectoryStream<Path>)newDirectoryStream(dir);

        // NullPointerException
        assertThrows(NullPointerException.class, () -> {
            stream.getFileAttributeView(null);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.getFileAttributeView(null, BasicFileAttributeView.class);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.getFileAttributeView(file, null);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.newByteChannel(null, EnumSet.of(CREATE,WRITE));
        });
        assertThrows(NullPointerException.class, () -> {
            stream.newByteChannel(null, EnumSet.of(CREATE,WRITE,null));
        });
        assertThrows(NullPointerException.class, () -> {
            stream.newByteChannel(file, null);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.move(null, stream, file);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.move(file, stream, null);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.newDirectoryStream(null);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.deleteFile(null);
        });
        assertThrows(NullPointerException.class, () -> {
            stream.deleteDirectory(null);
        });

        // close stream
        stream.close();
        stream.close();     // should be no-op

        // ClosedDirectoryStreamException
        assertThrows(ClosedDirectoryStreamException.class, () -> {
            stream.newDirectoryStream(file);
        });
        assertThrows(ClosedDirectoryStreamException.class, () -> {
            stream.newByteChannel(file, EnumSet.of(READ));
        });
        assertThrows(ClosedDirectoryStreamException.class, () -> {
            stream.move(file, stream, file);
        });
        assertThrows(ClosedDirectoryStreamException.class, () -> {
            stream.deleteFile(file);
        });

        // clean-up
        delete(dir.resolve(file));
    }
}

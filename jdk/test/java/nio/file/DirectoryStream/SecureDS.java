/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4313887
 * @summary Unit test for java.nio.file.SecureDirectoryStream
 * @library ..
 */

import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.nio.channels.*;
import java.io.IOException;
import java.util.*;

public class SecureDS {
    static boolean supportsLinks;

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            DirectoryStream stream = dir.newDirectoryStream();
            stream.close();
            if (!(stream instanceof SecureDirectoryStream)) {
                System.out.println("SecureDirectoryStream not supported.");
                return;
            }

            supportsLinks = TestUtil.supportsLinks(dir);

            // run tests
            doBasicTests(dir);
            doMoveTests(dir);
            miscTests(dir);

        } finally {
            TestUtil.removeAll(dir);
        }
    }

    // Exercise each of SecureDirectoryStream's method (except move)
    static void doBasicTests(Path dir) throws IOException {
        Path dir1 = dir.resolve("dir1").createDirectory();
        Path dir2 = dir.resolve("dir2");

        // create a file, directory, and two sym links in the directory
        Path fileEntry = Paths.get("myfile");
        dir1.resolve(fileEntry).createFile();
        Path dirEntry = Paths.get("mydir");
        dir1.resolve(dirEntry).createDirectory();
        // myfilelink -> myfile
        Path link1Entry = Paths.get("myfilelink");
        if (supportsLinks)
            dir1.resolve(link1Entry).createSymbolicLink(fileEntry);
        // mydirlink -> mydir
        Path link2Entry = Paths.get("mydirlink");
        if (supportsLinks)
            dir1.resolve(link2Entry).createSymbolicLink(dirEntry);

        // open directory and then move it so that it is no longer accessible
        // via its original path.
        SecureDirectoryStream stream =
            (SecureDirectoryStream)dir1.newDirectoryStream();
        dir1.moveTo(dir2);

        // Test: iterate over all entries
        int count = 0;
        for (Path entry: stream) { count++; }
        assertTrue(count == (supportsLinks ? 4 : 2));

        // Test: getFileAttributeView to access directory's attributes
        assertTrue(stream
            .getFileAttributeView(BasicFileAttributeView.class)
                .readAttributes()
                    .isDirectory());

        // Test: dynamic access to directory's attributes
        BasicFileAttributeView view = stream.
            getFileAttributeView(BasicFileAttributeView.class);
        Map<String,?> attrs = view.readAttributes("*");
        assertTrue((Boolean)attrs.get("isDirectory"));
        attrs = view.readAttributes("isRegularFile", "size");
        assertTrue(!(Boolean)attrs.get("isRegularFile"));
        assertTrue((Long)attrs.get("size") >= 0);
        int linkCount = (Integer)view.getAttribute("linkCount");
        assertTrue(linkCount > 0);
        view.setAttribute("lastModifiedTime", 0L);

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
        if (supportsLinks) {
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

        // Test: dynamic access to entry attributes
        view = stream
             .getFileAttributeView(fileEntry, PosixFileAttributeView.class, NOFOLLOW_LINKS);
        if (view != null) {
            attrs = view.readAttributes("owner", "size");
            UserPrincipal owner = (UserPrincipal)attrs.get("owner");
            assertTrue(owner != null);
            assertTrue((Long)attrs.get("size") >= 0L);
            view.setAttribute("lastAccessTime", 0L);
        }

        // Test: newByteChannel
        Set<StandardOpenOption> opts = Collections.emptySet();
        stream.newByteChannel(fileEntry, opts).close();
        if (supportsLinks) {
            stream.newByteChannel(link1Entry, opts).close();
            try {
                Set<OpenOption> mixed = new HashSet<OpenOption>();
                mixed.add(READ);
                mixed.add(NOFOLLOW_LINKS);
                stream.newByteChannel(link1Entry, mixed).close();
                shouldNotGetHere();
            } catch (IOException x) { }
        }

        // Test: newDirectoryStream
        stream.newDirectoryStream(dirEntry, true, null).close();
        stream.newDirectoryStream(dirEntry, false, null).close();
        if (supportsLinks) {
            stream.newDirectoryStream(link2Entry, true, null).close();
            try {
                stream.newDirectoryStream(link2Entry, false, null).close();
                shouldNotGetHere();
            } catch (IOException x) { }
        }

        // Test: delete
        if (supportsLinks) {
            stream.deleteFile(link1Entry);
            stream.deleteFile(link2Entry);
        }
        stream.deleteDirectory(dirEntry);
        stream.deleteFile(fileEntry);

        // Test: remove
        // (requires resetting environment to get new iterator)
        stream.close();
        dir2.moveTo(dir1);
        dir1.resolve(fileEntry).createFile();
        stream = (SecureDirectoryStream)dir1.newDirectoryStream();
        dir1.moveTo(dir2);
        Iterator<Path> iter = stream.iterator();
        int removed = 0;
        while (iter.hasNext()) {
            iter.next();
            iter.remove();
            removed++;
        }
        assertTrue(removed == 1);

        // clean-up
        stream.close();
        dir2.delete();
    }

    // Exercise SecureDirectoryStream's move method
    static void doMoveTests(Path dir) throws IOException {
        Path dir1 = dir.resolve("dir1").createDirectory();
        Path dir2 = dir.resolve("dir2").createDirectory();

        // create dir1/myfile, dir1/mydir, dir1/mylink
        Path fileEntry = Paths.get("myfile");
        dir1.resolve(fileEntry).createFile();
        Path dirEntry = Paths.get("mydir");
        dir1.resolve(dirEntry).createDirectory();
        Path linkEntry = Paths.get("mylink");
        if (supportsLinks)
            dir1.resolve(linkEntry).createSymbolicLink(Paths.get("missing"));

        // target name
        Path target = Paths.get("newfile");

        // open stream to both directories
        SecureDirectoryStream stream1 =
            (SecureDirectoryStream)dir1.newDirectoryStream();
        SecureDirectoryStream stream2 =
            (SecureDirectoryStream)dir2.newDirectoryStream();

        // Test: move dir1/myfile -> dir2/newfile
        stream1.move(fileEntry, stream2, target);
        assertTrue(dir1.resolve(fileEntry).notExists());
        assertTrue(dir2.resolve(target).exists());
        stream2.deleteFile(target);

        // Test: move dir1/mydir -> dir2/newfile
        stream1.move(dirEntry, stream2, target);
        assertTrue(dir1.resolve(dirEntry).notExists());
        assertTrue(dir2.resolve(target).exists());
        stream2.deleteDirectory(target);

        // Test: move dir1/mylink -> dir2/newfile
        if (supportsLinks) {
            stream1.move(linkEntry, stream2, target);
            assertTrue(dir2.resolve(target)
                .getFileAttributeView(BasicFileAttributeView.class, NOFOLLOW_LINKS)
                .readAttributes()
                .isSymbolicLink());
            stream2.deleteFile(target);
        }

        // Test: move between devices
        String testDirAsString = System.getProperty("test.dir");
        if (testDirAsString != null) {
            Path testDir = Paths.get(testDirAsString);
            if (!dir1.getFileStore().equals(testDir.getFileStore())) {
                SecureDirectoryStream ts =
                    (SecureDirectoryStream)testDir.newDirectoryStream();
                dir1.resolve(fileEntry).createFile();
                try {
                    stream1.move(fileEntry, ts, target);
                    shouldNotGetHere();
                } catch (AtomicMoveNotSupportedException x) { }
                ts.close();
                stream1.deleteFile(fileEntry);
            }
        }

        // clean-up
        dir1.delete();
        dir2.delete();
    }

    // null and ClosedDirectoryStreamException
    static void miscTests(Path dir) throws IOException {
        Path file = Paths.get("file");
        dir.resolve(file).createFile();

        SecureDirectoryStream stream =
            (SecureDirectoryStream)dir.newDirectoryStream();

        // NullPointerException
        try {
            stream.getFileAttributeView(null);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.getFileAttributeView(null, BasicFileAttributeView.class);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.getFileAttributeView(file, null);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.newByteChannel(null, EnumSet.of(CREATE,WRITE));
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.newByteChannel(null, EnumSet.of(CREATE,WRITE,null));
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.newByteChannel(file, null);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.move(null, stream, file);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.move(file, null, file);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.move(file, stream, null);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.newDirectoryStream(null, true, null);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.deleteFile(null);
            shouldNotGetHere();
        } catch (NullPointerException x) { }
        try {
            stream.deleteDirectory(null);
            shouldNotGetHere();
        } catch (NullPointerException x) { }

        // close stream
        stream.close();
        stream.close();     // should be no-op

        // ClosedDirectoryStreamException
        try {
            stream.newDirectoryStream(file, true, null);
            shouldNotGetHere();
        } catch (ClosedDirectoryStreamException x) { }
        try {
            stream.newByteChannel(file, EnumSet.of(READ));
            shouldNotGetHere();
        } catch (ClosedDirectoryStreamException x) { }
        try {
            stream.move(file, stream, file);
            shouldNotGetHere();
        } catch (ClosedDirectoryStreamException x) { }
        try {
            stream.deleteFile(file);
            shouldNotGetHere();
        } catch (ClosedDirectoryStreamException x) { }

        // clean-up
        dir.resolve(file).delete();
    }

    static void assertTrue(boolean b) {
        if (!b) throw new RuntimeException("Assertion failed");
    }

    static void shouldNotGetHere() {
        assertTrue(false);
    }
}

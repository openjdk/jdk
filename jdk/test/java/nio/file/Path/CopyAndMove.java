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
 * @bug 4313887 6838333
 * @summary Unit test for java.nio.file.Path copyTo/moveTo methods
 * @library ..
 */

import java.nio.ByteBuffer;
import java.nio.file.*;
import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;

public class CopyAndMove {
    static final Random rand = new Random();
    static boolean heads() { return rand.nextBoolean(); }
    static boolean supportsLinks;

    public static void main(String[] args) throws Exception {
        Path dir1 = TestUtil.createTemporaryDirectory();
        try {
            supportsLinks = TestUtil.supportsLinks(dir1);

            // Exercise copyTo
            doCopyTests(dir1);

            // Exercise moveTo
            // if test.dir differs to temporary file system then can test
            // moving between devices
            String testDir = System.getProperty("test.dir");
            Path dir2 = (testDir != null) ? Paths.get(testDir) : dir1;
            doMoveTests(dir1, dir2);

        } finally {
            TestUtil.removeAll(dir1);
        }
    }

    static void checkBasicAttributes(BasicFileAttributes attrs1,
                                     BasicFileAttributes attrs2)
    {
        // check file type
        assertTrue(attrs1.isRegularFile() == attrs2.isRegularFile());
        assertTrue(attrs1.isDirectory() == attrs2.isDirectory());
        assertTrue(attrs1.isSymbolicLink() == attrs2.isSymbolicLink());
        assertTrue(attrs1.isOther() == attrs2.isOther());

        // check last modified time
        long time1 = attrs1.lastModifiedTime().toMillis();
        long time2 = attrs2.lastModifiedTime().toMillis();
        assertTrue(time1 == time2);

        // check size
        if (attrs1.isRegularFile())
            assertTrue(attrs1.size() == attrs2.size());
    }

    static void checkPosixAttributes(PosixFileAttributes attrs1,
                                     PosixFileAttributes attrs2)
    {
        assertTrue(attrs1.permissions().equals(attrs2.permissions()));
        assertTrue(attrs1.owner().equals(attrs2.owner()));
        assertTrue(attrs1.group().equals(attrs2.group()));
    }

    static void checkDosAttributes(DosFileAttributes attrs1,
                                   DosFileAttributes attrs2)
    {
        assertTrue(attrs1.isReadOnly() == attrs2.isReadOnly());
        assertTrue(attrs1.isHidden() == attrs2.isHidden());
        assertTrue(attrs1.isArchive() == attrs2.isArchive());
        assertTrue(attrs1.isSystem() == attrs2.isSystem());
    }

    static void checkUserDefinedFileAttributes(Map<String,ByteBuffer> attrs1,
                                     Map<String,ByteBuffer> attrs2)
    {
        assert attrs1.size() == attrs2.size();
        for (String name: attrs1.keySet()) {
            ByteBuffer bb1 = attrs1.get(name);
            ByteBuffer bb2 = attrs2.get(name);
            assertTrue(bb2 != null);
            assertTrue(bb1.equals(bb2));
        }
    }

    static Map<String,ByteBuffer> readUserDefinedFileAttributes(Path file)
        throws IOException
    {
        UserDefinedFileAttributeView view = file
            .getFileAttributeView(UserDefinedFileAttributeView.class);
        Map<String,ByteBuffer> result = new HashMap<String,ByteBuffer>();
        for (String name: view.list()) {
            int size = view.size(name);
            ByteBuffer bb = ByteBuffer.allocate(size);
            int n = view.read(name, bb);
            assertTrue(n == size);
            bb.flip();
            result.put(name, bb);
        }
        return result;
    }

    // move source to target with verification
    static void moveAndVerify(Path source, Path target, CopyOption... options)
        throws IOException
    {
        // read attributes before file is moved
        BasicFileAttributes basicAttributes = null;
        PosixFileAttributes posixAttributes = null;
        DosFileAttributes dosAttributes = null;
        Map<String,ByteBuffer> namedAttributes = null;

        // get file attributes of source file
        String os = System.getProperty("os.name");
        if (os.equals("SunOS") || os.equals("Linux")) {
            posixAttributes = Attributes.readPosixFileAttributes(source, NOFOLLOW_LINKS);
            basicAttributes = posixAttributes;
        }
        if (os.startsWith("Windows")) {
            dosAttributes = Attributes.readDosFileAttributes(source, NOFOLLOW_LINKS);
            basicAttributes = dosAttributes;
        }
        if (basicAttributes == null)
            basicAttributes = Attributes.readBasicFileAttributes(source, NOFOLLOW_LINKS);

        // hash file contents if regular file
        int hash = (basicAttributes.isRegularFile()) ? computeHash(source) : 0;

        // record link target if symbolic link
        Path linkTarget = null;
        if (basicAttributes.isSymbolicLink())
            linkTarget = source.readSymbolicLink();

        // read named attributes if available (and file is not a sym link)
        if (!basicAttributes.isSymbolicLink() &&
            source.getFileStore().supportsFileAttributeView("xattr"))
        {
            namedAttributes = readUserDefinedFileAttributes(source);
        }

        // move file
        source.moveTo(target, options);

        // verify source does not exist
        assertTrue(source.notExists());

        // verify file contents
        if (basicAttributes.isRegularFile()) {
            if (computeHash(target) != hash)
                throw new RuntimeException("Failed to verify move of regular file");
        }

        // verify link target
        if (basicAttributes.isSymbolicLink()) {
            if (!target.readSymbolicLink().equals(linkTarget))
                throw new RuntimeException("Failed to verify move of symbolic link");
        }

        // verify basic attributes
        checkBasicAttributes(basicAttributes,
            Attributes.readBasicFileAttributes(target, NOFOLLOW_LINKS));

        // verify POSIX attributes
        if (posixAttributes != null && !basicAttributes.isSymbolicLink()) {
            checkPosixAttributes(posixAttributes,
                Attributes.readPosixFileAttributes(target, NOFOLLOW_LINKS));
        }

        // verify DOS attributes
        if (dosAttributes != null && !basicAttributes.isSymbolicLink()) {
            checkDosAttributes(dosAttributes,
                Attributes.readDosFileAttributes(target, NOFOLLOW_LINKS));
        }

        // verify named attributes
        if (namedAttributes != null &&
            target.getFileStore().supportsFileAttributeView("xattr"))
        {
            checkUserDefinedFileAttributes(namedAttributes, readUserDefinedFileAttributes(target));
        }
    }

    /**
     * Tests all possible ways to invoke moveTo
     */
    static void doMoveTests(Path dir1, Path dir2) throws IOException {
        Path source, target, entry;

        boolean sameDevice = dir1.getFileStore().equals(dir2.getFileStore());

        // -- regular file --

        /**
         * Test: move regular file, target does not exist
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1);
        moveAndVerify(source, target);
        target.delete();

        /**
         * Test: move regular file, target exists
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1).createFile();
        try {
            moveAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        target.delete();
        target.createDirectory();
        try {
            moveAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        source.delete();
        target.delete();

        /**
         * Test: move regular file, target does not exist
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1);
        moveAndVerify(source, target, REPLACE_EXISTING);
        target.delete();

        /**
         * Test: move regular file, target exists
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1).createFile();
        moveAndVerify(source, target, REPLACE_EXISTING);
        target.delete();

        /**
         * Test: move regular file, target exists and is empty directory
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1).createDirectory();
        moveAndVerify(source, target, REPLACE_EXISTING);
        target.delete();

        /**
         * Test: move regular file, target exists and is non-empty directory
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1).createDirectory();
        entry = target.resolve("foo").createFile();
        try {
            moveAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        entry.delete();
        source.delete();
        target.delete();

        /**
         * Test atomic move of regular file (same file store)
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1);
        moveAndVerify(source, target, ATOMIC_MOVE);
        target.delete();

        /**
         * Test atomic move of regular file (different file store)
         */
        if (!sameDevice) {
            source = createSourceFile(dir1);
            target = getTargetFile(dir2);
            try {
                moveAndVerify(source, target, ATOMIC_MOVE);
                throw new RuntimeException("AtomicMoveNotSupportedException expected");
            } catch (AtomicMoveNotSupportedException x) {
            }
            source.delete();
        }

        // -- directories --

        /*
         * Test: move empty directory, target does not exist
         */
        source = createSourceDirectory(dir1);
        target = getTargetFile(dir1);
        moveAndVerify(source, target);
        target.delete();

        /**
         * Test: move empty directory, target exists
         */
        source = createSourceDirectory(dir1);
        target = getTargetFile(dir1).createFile();
        try {
            moveAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        target.delete();
        target.createDirectory();
        try {
            moveAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        source.delete();
        target.delete();

        /**
         * Test: move empty directory, target does not exist
         */
        source = createSourceDirectory(dir1);
        target = getTargetFile(dir1);
        moveAndVerify(source, target, REPLACE_EXISTING);
        target.delete();

        /**
         * Test: move empty directory, target exists
         */
        source = createSourceDirectory(dir1);
        target = getTargetFile(dir1).createFile();
        moveAndVerify(source, target, REPLACE_EXISTING);
        target.delete();

        /**
         * Test: move empty, target exists and is empty directory
         */
        source = createSourceDirectory(dir1);
        target = getTargetFile(dir1).createDirectory();
        moveAndVerify(source, target, REPLACE_EXISTING);
        target.delete();

        /**
         * Test: move empty directory, target exists and is non-empty directory
         */
        source = createSourceDirectory(dir1);
        target = getTargetFile(dir1).createDirectory();
        entry = target.resolve("foo").createFile();
        try {
            moveAndVerify(source, target, REPLACE_EXISTING);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        entry.delete();
        source.delete();
        target.delete();

        /**
         * Test: move non-empty directory (same file system)
         */
        source = createSourceDirectory(dir1);
        source.resolve("foo").createFile();
        target = getTargetFile(dir1);
        moveAndVerify(source, target);
        target.resolve("foo").delete();
        target.delete();

        /**
         * Test: move non-empty directory (different file store)
         */
        if (!sameDevice) {
            source = createSourceDirectory(dir1);
            source.resolve("foo").createFile();
            target = getTargetFile(dir2);
            try {
                moveAndVerify(source, target);
                throw new RuntimeException("IOException expected");
            } catch (IOException x) {
            }
            source.resolve("foo").delete();
            source.delete();
        }

        /**
         * Test atomic move of directory (same file store)
         */
        source = createSourceDirectory(dir1);
        source.resolve("foo").createFile();
        target = getTargetFile(dir1);
        moveAndVerify(source, target, ATOMIC_MOVE);
        target.resolve("foo").delete();
        target.delete();

        // -- symbolic links --

        /**
         * Test: Move symbolic link to file, target does not exist
         */
        if (supportsLinks) {
            Path tmp = createSourceFile(dir1);
            source = dir1.resolve("link").createSymbolicLink(tmp);
            target = getTargetFile(dir1);
            moveAndVerify(source, target);
            target.delete();
            tmp.delete();
        }

        /**
         * Test: Move symbolic link to directory, target does not exist
         */
        if (supportsLinks) {
            source = dir1.resolve("link").createSymbolicLink(dir2);
            target = getTargetFile(dir1);
            moveAndVerify(source, target);
            target.delete();
        }

        /**
         * Test: Move broken symbolic link, target does not exists
         */
        if (supportsLinks) {
            Path tmp = Paths.get("doesnotexist");
            source = dir1.resolve("link").createSymbolicLink(tmp);
            target = getTargetFile(dir1);
            moveAndVerify(source, target);
            target.delete();
        }

        /**
         * Test: Move symbolic link, target exists
         */
        if (supportsLinks) {
            source = dir1.resolve("link").createSymbolicLink(dir2);
            target = getTargetFile(dir1).createFile();
            try {
                moveAndVerify(source, target);
                throw new RuntimeException("FileAlreadyExistsException expected");
            } catch (FileAlreadyExistsException x) {
            }
            source.delete();
            target.delete();
        }

        /**
         * Test: Move regular file, target exists
         */
        if (supportsLinks) {
            source = dir1.resolve("link").createSymbolicLink(dir2);
            target = getTargetFile(dir1).createFile();
            moveAndVerify(source, target, REPLACE_EXISTING);
            target.delete();
        }

        /**
         * Test: move symbolic link, target exists and is empty directory
         */
        if (supportsLinks) {
            source = dir1.resolve("link").createSymbolicLink(dir2);
            target = getTargetFile(dir1).createDirectory();
            moveAndVerify(source, target, REPLACE_EXISTING);
            target.delete();
        }

        /**
         * Test: symbolic link, target exists and is non-empty directory
         */
        if (supportsLinks) {
            source = dir1.resolve("link").createSymbolicLink(dir2);
            target = getTargetFile(dir1).createDirectory();
            entry = target.resolve("foo").createFile();
            try {
                moveAndVerify(source, target);
                throw new RuntimeException("FileAlreadyExistsException expected");
            } catch (FileAlreadyExistsException x) {
            }
            entry.delete();
            source.delete();
            target.delete();
        }

        /**
         * Test atomic move of symbolic link (same file store)
         */
        if (supportsLinks) {
            source = dir1.resolve("link").createSymbolicLink(dir1);
            target = getTargetFile(dir1).createFile();
            moveAndVerify(source, target, REPLACE_EXISTING);
            target.delete();
        }

        // -- misc. tests --

        /**
         * Test nulls
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1);
        try {
            source.moveTo(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
        try {
            source.moveTo(target, (CopyOption[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
        try {
            CopyOption[] opts = { REPLACE_EXISTING, null };
            source.moveTo(target, opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
        source.delete();

        /**
         * Test UOE
         */
        source = createSourceFile(dir1);
        target = getTargetFile(dir1);
        try {
            source.moveTo(target, new CopyOption() { });
        } catch (UnsupportedOperationException x) { }
        try {
            source.moveTo(target, REPLACE_EXISTING,  new CopyOption() { });
        } catch (UnsupportedOperationException x) { }
        source.delete();
    }

    // copy source to target with verification
    static void copyAndVerify(Path source, Path target, CopyOption... options)
        throws IOException
    {
        source.copyTo(target, options);

        // get attributes of source and target file to verify copy
        boolean followLinks = true;
        LinkOption[] linkOptions = new LinkOption[0];
        boolean copyAttributes = false;
        for (CopyOption opt : options) {
            if (opt == NOFOLLOW_LINKS) {
                followLinks = false;
                linkOptions = new LinkOption[] { NOFOLLOW_LINKS };
            }
            if (opt == COPY_ATTRIBUTES)
                copyAttributes = true;
        }
        BasicFileAttributes basicAttributes = Attributes
            .readBasicFileAttributes(source, linkOptions);

        // check hash if regular file
        if (basicAttributes.isRegularFile())
            assertTrue(computeHash(source) == computeHash(target));

        // check link target if symbolic link
        if (basicAttributes.isSymbolicLink())
            assert( source.readSymbolicLink().equals(target.readSymbolicLink()));

        // check that attributes are copied
        if (copyAttributes && followLinks) {
            checkBasicAttributes(basicAttributes,
                Attributes.readBasicFileAttributes(source, linkOptions));

            // check POSIX attributes are copied
            String os = System.getProperty("os.name");
            if (os.equals("SunOS") || os.equals("Linux")) {
                checkPosixAttributes(
                    Attributes.readPosixFileAttributes(source, linkOptions),
                    Attributes.readPosixFileAttributes(target, linkOptions));
            }

            // check DOS attributes are copied
            if (os.startsWith("Windows")) {
                checkDosAttributes(
                    Attributes.readDosFileAttributes(source, linkOptions),
                    Attributes.readDosFileAttributes(target, linkOptions));
            }

            // check named attributes are copied
            if (followLinks &&
                source.getFileStore().supportsFileAttributeView("xattr") &&
                target.getFileStore().supportsFileAttributeView("xattr"))
            {
                checkUserDefinedFileAttributes(readUserDefinedFileAttributes(source),
                                     readUserDefinedFileAttributes(target));
            }
        }
    }

    /**
     * Tests all possible ways to invoke copyTo
     */
    static void doCopyTests(Path dir) throws IOException {
        Path source, target, link, entry;

        // -- regular file --

        /**
         * Test: move regular file, target does not exist
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir);
        copyAndVerify(source, target);
        source.delete();
        target.delete();

        /**
         * Test: copy regular file, target exists
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir).createFile();
        try {
            copyAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        target.delete();
        target.createDirectory();
        try {
            copyAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        source.delete();
        target.delete();

        /**
         * Test: copy regular file, target does not exist
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir);
        copyAndVerify(source, target, REPLACE_EXISTING);
        source.delete();
        target.delete();

        /**
         * Test: copy regular file, target exists
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir).createFile();
        copyAndVerify(source, target, REPLACE_EXISTING);
        source.delete();
        target.delete();

        /**
         * Test: copy regular file, target exists and is empty directory
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir).createDirectory();
        copyAndVerify(source, target, REPLACE_EXISTING);
        source.delete();
        target.delete();

        /**
         * Test: copy regular file, target exists and is non-empty directory
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir).createDirectory();
        entry = target.resolve("foo").createFile();
        try {
            copyAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        entry.delete();
        source.delete();
        target.delete();

        /**
         * Test: copy regular file + attributes
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir);
        copyAndVerify(source, target, COPY_ATTRIBUTES);
        source.delete();
        target.delete();


        // -- directory --

        /*
         * Test: copy directory, target does not exist
         */
        source = createSourceDirectory(dir);
        target = getTargetFile(dir);
        copyAndVerify(source, target);
        source.delete();
        target.delete();

        /**
         * Test: copy directory, target exists
         */
        source = createSourceDirectory(dir);
        target = getTargetFile(dir).createFile();
        try {
            copyAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        target.delete();
        target.createDirectory();
        try {
            copyAndVerify(source, target);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        source.delete();
        target.delete();

        /**
         * Test: copy directory, target does not exist
         */
        source = createSourceDirectory(dir);
        target = getTargetFile(dir);
        copyAndVerify(source, target, REPLACE_EXISTING);
        source.delete();
        target.delete();

        /**
         * Test: copy directory, target exists
         */
        source = createSourceDirectory(dir);
        target = getTargetFile(dir).createFile();
        copyAndVerify(source, target, REPLACE_EXISTING);
        source.delete();
        target.delete();

        /**
         * Test: copy directory, target exists and is empty directory
         */
        source = createSourceDirectory(dir);
        target = getTargetFile(dir).createDirectory();
        copyAndVerify(source, target, REPLACE_EXISTING);
        source.delete();
        target.delete();

        /**
         * Test: copy directory, target exists and is non-empty directory
         */
        source = createSourceDirectory(dir);
        target = getTargetFile(dir).createDirectory();
        entry = target.resolve("foo").createFile();
        try {
            copyAndVerify(source, target, REPLACE_EXISTING);
            throw new RuntimeException("FileAlreadyExistsException expected");
        } catch (FileAlreadyExistsException x) {
        }
        entry.delete();
        source.delete();
        target.delete();

        /*
         * Test: copy directory + attributes
         */
        source = createSourceDirectory(dir);
        target = getTargetFile(dir);
        copyAndVerify(source, target, COPY_ATTRIBUTES);
        source.delete();
        target.delete();

        // -- symbolic links --

        /**
         * Test: Follow link
         */
        if (supportsLinks) {
            source = createSourceFile(dir);
            link = dir.resolve("link").createSymbolicLink(source);
            target = getTargetFile(dir);
            copyAndVerify(link, target);
            link.delete();
            source.delete();
        }

        /**
         * Test: Copy link (to file)
         */
        if (supportsLinks) {
            source = createSourceFile(dir);
            link = dir.resolve("link").createSymbolicLink(source);
            target = getTargetFile(dir);
            copyAndVerify(link, target, NOFOLLOW_LINKS);
            link.delete();
            source.delete();
        }

        /**
         * Test: Copy link (to directory)
         */
        if (supportsLinks) {
            source = dir.resolve("mydir").createDirectory();
            link = dir.resolve("link").createSymbolicLink(source);
            target = getTargetFile(dir);
            copyAndVerify(link, target, NOFOLLOW_LINKS);
            link.delete();
            source.delete();
        }

        /**
         * Test: Copy broken link
         */
        if (supportsLinks) {
            assertTrue(source.notExists());
            link = dir.resolve("link").createSymbolicLink(source);
            target = getTargetFile(dir);
            copyAndVerify(link, target, NOFOLLOW_LINKS);
            link.delete();
        }

        /**
         * Test: Copy link to UNC (Windows only)
         */
        if (supportsLinks &&
            System.getProperty("os.name").startsWith("Windows"))
        {
            Path unc = Paths.get("\\\\rialto\\share\\file");
            link = dir.resolve("link").createSymbolicLink(unc);
            target = getTargetFile(dir);
            copyAndVerify(link, target, NOFOLLOW_LINKS);
            link.delete();
        }

        // -- misc. tests --

        /**
         * Test nulls
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir);
        try {
            source.copyTo(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
        try {
            source.copyTo(target, (CopyOption[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
        try {
            CopyOption[] opts = { REPLACE_EXISTING, null };
            source.copyTo(target, opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
        source.delete();

        /**
         * Test UOE
         */
        source = createSourceFile(dir);
        target = getTargetFile(dir);
        try {
            source.copyTo(target, new CopyOption() { });
        } catch (UnsupportedOperationException x) { }
        try {
            source.copyTo(target, REPLACE_EXISTING,  new CopyOption() { });
        } catch (UnsupportedOperationException x) { }
        source.delete();
    }


    static void assertTrue(boolean value) {
        if (!value)
            throw new RuntimeException("Assertion failed");
    }

    // computes simple hash of the given file
    static int computeHash(Path file) throws IOException {
        int h = 0;

        InputStream in = file.newInputStream();
        try {
            byte[] buf = new byte[1024];
            int n;
            do {
                n = in.read(buf);
                for (int i=0; i<n; i++) {
                    h = 31*h + (buf[i] & 0xff);
                }
            } while (n > 0);
        } finally {
            in.close();
        }
        return h;
    }

    // create file of random size in given directory
    static Path createSourceFile(Path dir) throws IOException {
        String name = "source" + Integer.toString(rand.nextInt());
        Path file = dir.resolve(name).createFile();
        byte[] bytes = new byte[rand.nextInt(128*1024)];
        rand.nextBytes(bytes);
        OutputStream out = file.newOutputStream();
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        randomizeAttributes(file);
        return file;
    }

    // create directory in the given directory
    static Path createSourceDirectory(Path dir) throws IOException {
        String name = "sourcedir" + Integer.toString(rand.nextInt());
        Path subdir = dir.resolve(name).createDirectory();
        randomizeAttributes(subdir);
        return subdir;
    }

    // "randomize" the file attributes of the given file.
    static void randomizeAttributes(Path file) throws IOException {
        String os = System.getProperty("os.name");
        boolean isWindows = os.startsWith("Windows");
        boolean isUnix = os.equals("SunOS") || os.equals("Linux");
        boolean isDirectory = Attributes.readBasicFileAttributes(file, NOFOLLOW_LINKS)
            .isDirectory();

        if (isUnix) {
            Set<PosixFilePermission> perms = Attributes
                .readPosixFileAttributes(file, NOFOLLOW_LINKS).permissions();
            PosixFilePermission[] toChange = {
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_EXECUTE
            };
            for (PosixFilePermission perm: toChange) {
                if (heads()) {
                    perms.add(perm);
                } else {
                    perms.remove(perm);
                }
            }
            Attributes.setPosixFilePermissions(file, perms);
        }

        if (isWindows) {
            DosFileAttributeView view = file
                .getFileAttributeView(DosFileAttributeView.class, NOFOLLOW_LINKS);
            // only set or unset the hidden attribute
            view.setHidden(heads());
        }

        boolean addUserDefinedFileAttributes = heads() &&
            file.getFileStore().supportsFileAttributeView("xattr");

        // remove this when copying a direcory copies its named streams
        if (isWindows && isDirectory) addUserDefinedFileAttributes = false;

        if (addUserDefinedFileAttributes) {
            UserDefinedFileAttributeView view = file
                .getFileAttributeView(UserDefinedFileAttributeView.class);
            int n = rand.nextInt(16);
            while (n > 0) {
                byte[] value = new byte[1 + rand.nextInt(100)];
                view.write("user." + Integer.toString(n), ByteBuffer.wrap(value));
                n--;
            }
        }
    }

    // create name for file in given directory
    static Path getTargetFile(Path dir) throws IOException {
        String name = "target" + Integer.toString(rand.nextInt());
        return dir.resolve(name);
    }
 }

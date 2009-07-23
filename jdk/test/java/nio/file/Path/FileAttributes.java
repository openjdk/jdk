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
 * @summary Unit test for java.nio.file.Path
 * @library ..
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Exercises getAttribute/setAttribute/readAttributes methods.
 */

public class FileAttributes {

    static void assertTrue(boolean okay) {
        if (!okay)
            throw new RuntimeException("Assertion Failed");
    }

    static void checkEqual(Object o1, Object o2) {
        if (o1 == null) {
            assertTrue(o2 == null);
        } else {
            assertTrue (o1.equals(o2));
        }
    }

    // checks that two time values are within 1s of each other
    static void checkNearEqual(FileTime t1, FileTime t2) {
        long diff = Math.abs(t1.toMillis() - t2.toMillis());
        assertTrue(diff <= 1000);
    }

    // Exercise getAttribute/setAttribute/readAttributes on basic attributes
    static void checkBasicAttributes(FileRef file, BasicFileAttributes attrs)
        throws IOException
    {
        // getAttribute
        checkEqual(attrs.size(), file.getAttribute("size"));
        checkEqual(attrs.lastModifiedTime(), file.getAttribute("basic:lastModifiedTime"));
        checkEqual(attrs.lastAccessTime(), file.getAttribute("lastAccessTime"));
        checkEqual(attrs.creationTime(), file.getAttribute("basic:creationTime"));
        assertTrue((Boolean)file.getAttribute("isRegularFile"));
        assertTrue(!(Boolean)file.getAttribute("basic:isDirectory"));
        assertTrue(!(Boolean)file.getAttribute("isSymbolicLink"));
        assertTrue(!(Boolean)file.getAttribute("basic:isOther"));
        checkEqual(attrs.fileKey(), file.getAttribute("basic:fileKey"));

        // setAttribute
        FileTime modTime = attrs.lastModifiedTime();
        file.setAttribute("basic:lastModifiedTime", FileTime.fromMillis(0L));
        checkEqual(Attributes.readBasicFileAttributes(file).lastModifiedTime(),
                   FileTime.fromMillis(0L));
        file.setAttribute("lastModifiedTime", modTime);
        checkEqual(Attributes.readBasicFileAttributes(file).lastModifiedTime(), modTime);

        Map<String,?> map;
        map = file.readAttributes("*");
        assertTrue(map.size() >= 9);
        checkEqual(attrs.isRegularFile(), map.get("isRegularFile")); // check one

        map = file.readAttributes("basic:*");
        assertTrue(map.size() >= 9);
        checkEqual(attrs.lastAccessTime(), map.get("lastAccessTime")); // check one

        map = file.readAttributes("size,lastModifiedTime");
        assertTrue(map.size() == 2);
        checkEqual(attrs.size(), map.get("size"));
        checkEqual(attrs.lastModifiedTime(), map.get("lastModifiedTime"));

        map = file.readAttributes(
            "basic:lastModifiedTime,lastAccessTime,ShouldNotExist");
        assertTrue(map.size() == 2);
        checkEqual(attrs.lastModifiedTime(), map.get("lastModifiedTime"));
        checkEqual(attrs.lastAccessTime(), map.get("lastAccessTime"));
    }

    // Exercise getAttribute/setAttribute/readAttributes on posix attributes
    static void checkPosixAttributes(FileRef file, PosixFileAttributes attrs)
        throws IOException
    {
        checkBasicAttributes(file, attrs);

        // getAttribute
        checkEqual(attrs.permissions(), file.getAttribute("posix:permissions"));
        checkEqual(attrs.owner(), file.getAttribute("posix:owner"));
        checkEqual(attrs.group(), file.getAttribute("posix:group"));

        // setAttribute
        Set<PosixFilePermission> orig = attrs.permissions();
        Set<PosixFilePermission> newPerms = new HashSet<PosixFilePermission>(orig);
        newPerms.remove(PosixFilePermission.OTHERS_READ);
        newPerms.remove(PosixFilePermission.OTHERS_WRITE);
        newPerms.remove(PosixFilePermission.OTHERS_EXECUTE);
        file.setAttribute("posix:permissions", newPerms);
        checkEqual(Attributes.readPosixFileAttributes(file).permissions(), newPerms);
        file.setAttribute("posix:permissions", orig);
        checkEqual(Attributes.readPosixFileAttributes(file).permissions(), orig);
        file.setAttribute("posix:owner", attrs.owner());
        file.setAttribute("posix:group", attrs.group());

        // readAttributes
        Map<String,?> map;
        map = file.readAttributes("posix:*");
        assertTrue(map.size() >= 12);
        checkEqual(attrs.permissions(), map.get("permissions")); // check one

        map = file.readAttributes("posix:size,owner,ShouldNotExist");
        assertTrue(map.size() == 2);
        checkEqual(attrs.size(), map.get("size"));
        checkEqual(attrs.owner(), map.get("owner"));
    }

    // Exercise getAttribute/readAttributes on unix attributes
    static void checkUnixAttributes(FileRef file) throws IOException {
        // getAttribute
        int mode = (Integer)file.getAttribute("unix:mode");
        long ino = (Long)file.getAttribute("unix:ino");
        long dev = (Long)file.getAttribute("unix:dev");
        long rdev = (Long)file.getAttribute("unix:rdev");
        int nlink = (Integer)file.getAttribute("unix:nlink");
        int uid = (Integer)file.getAttribute("unix:uid");
        int gid = (Integer)file.getAttribute("unix:gid");
        FileTime ctime = (FileTime)file.getAttribute("unix:ctime");

        // readAttributes
        Map<String,?> map;
        map = file.readAttributes("unix:*");
        assertTrue(map.size() >= 20);

        map = file.readAttributes("unix:size,uid,gid,ShouldNotExist");
        assertTrue(map.size() == 3);
        checkEqual(map.get("size"),
                   Attributes.readBasicFileAttributes(file).size());
    }

    // Exercise getAttribute/setAttribute on dos attributes
    static void checkDosAttributes(FileRef file, DosFileAttributes attrs)
        throws IOException
    {
        checkBasicAttributes(file, attrs);

        // getAttribute
        checkEqual(attrs.isReadOnly(), file.getAttribute("dos:readonly"));
        checkEqual(attrs.isHidden(), file.getAttribute("dos:hidden"));
        checkEqual(attrs.isSystem(), file.getAttribute("dos:system"));
        checkEqual(attrs.isArchive(), file.getAttribute("dos:archive"));

        // setAttribute
        boolean value;

        value = attrs.isReadOnly();
        file.setAttribute("dos:readonly", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isReadOnly(), !value);
        file.setAttribute("dos:readonly", value);
        checkEqual(Attributes.readDosFileAttributes(file).isReadOnly(), value);

        value = attrs.isHidden();
        file.setAttribute("dos:hidden", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isHidden(), !value);
        file.setAttribute("dos:hidden", value);
        checkEqual(Attributes.readDosFileAttributes(file).isHidden(), value);

        value = attrs.isSystem();
        file.setAttribute("dos:system", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isSystem(), !value);
        file.setAttribute("dos:system", value);
        checkEqual(Attributes.readDosFileAttributes(file).isSystem(), value);

        value = attrs.isArchive();
        file.setAttribute("dos:archive", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isArchive(), !value);
        file.setAttribute("dos:archive", value);
        checkEqual(Attributes.readDosFileAttributes(file).isArchive(), value);

        // readAttributes
        Map<String,?> map;
        map = file.readAttributes("dos:*");
        assertTrue(map.size() >= 13);
        checkEqual(attrs.isReadOnly(), map.get("readonly")); // check one

        map = file.readAttributes("dos:size,hidden,ShouldNotExist");
        assertTrue(map.size() == 2);
        checkEqual(attrs.size(), map.get("size"));
        checkEqual(attrs.isHidden(), map.get("hidden"));
    }

    static void miscTests(Path file) throws IOException {
        // NPE tests
        try {
            file.getAttribute(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
        try {
            file.getAttribute("isRegularFile", (LinkOption[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
        try {
            file.setAttribute(null, 0L);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) { }
    }

    static void doTests(Path dir) throws IOException {
        Path file = dir.resolve("foo").createFile();
        FileStore store = file.getFileStore();
        try {
            checkBasicAttributes(file,
                Attributes.readBasicFileAttributes(file));

            if (store.supportsFileAttributeView("posix"))
                checkPosixAttributes(file,
                    Attributes.readPosixFileAttributes(file));

            if (store.supportsFileAttributeView("unix"))
                checkUnixAttributes(file);

            if (store.supportsFileAttributeView("dos"))
                checkDosAttributes(file,
                    Attributes.readDosFileAttributes(file));

            miscTests(file);
        } finally {
            file.delete();
        }
    }


    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            doTests(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}

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
 * @summary Unit test for java.nio.file.attribute.Attributes
 * @library ../..
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Exercises getAttribute/setAttribute/readAttributes methods.
 */

public class Basic {

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

    // Exercise getAttribute/setAttribute/readAttributes on basic attributes
    static void checkBasicAttributes(FileRef file, BasicFileAttributes attrs)
        throws IOException
    {
        // getAttribute
        checkEqual(attrs.size(), Attributes.getAttribute(file, "size"));
        checkEqual(attrs.lastModifiedTime(),
                   Attributes.getAttribute(file, "basic:lastModifiedTime"));
        checkEqual(attrs.lastAccessTime(),
                   Attributes.getAttribute(file, "lastAccessTime"));
        checkEqual(attrs.creationTime(),
                   Attributes.getAttribute(file, "basic:creationTime"));
        assertTrue((Boolean)Attributes.getAttribute(file, "isRegularFile"));
        assertTrue(!(Boolean)Attributes.getAttribute(file, "basic:isDirectory"));
        assertTrue(!(Boolean)Attributes.getAttribute(file, "isSymbolicLink"));
        assertTrue(!(Boolean)Attributes.getAttribute(file, "basic:isOther"));
        checkEqual(attrs.linkCount(),
                   (Integer)Attributes.getAttribute(file, "linkCount"));
        checkEqual(attrs.fileKey(), Attributes.getAttribute(file, "basic:fileKey"));

        // setAttribute
        if (attrs.resolution() == TimeUnit.MILLISECONDS) {
            long modTime = attrs.lastModifiedTime();
            Attributes.setAttribute(file, "basic:lastModifiedTime", 0L);
            assertTrue(Attributes.readBasicFileAttributes(file).lastModifiedTime() == 0L);
            Attributes.setAttribute(file, "lastModifiedTime", modTime);
            assertTrue(Attributes.readBasicFileAttributes(file).lastModifiedTime() == modTime);
        }

        // readAttributes
        Map<String,?> map;
        map = Attributes.readAttributes(file, "*");
        assertTrue(map.size() >= 11);
        checkEqual(attrs.isRegularFile(), map.get("isRegularFile")); // check one

        map = Attributes.readAttributes(file, "basic:*");
        assertTrue(map.size() >= 11);
        checkEqual(attrs.lastAccessTime(), map.get("lastAccessTime")); // check one

        map = Attributes.readAttributes(file, "size,lastModifiedTime");
        assertTrue(map.size() == 2);
        checkEqual(attrs.size(), map.get("size"));
        checkEqual(attrs.lastModifiedTime(), map.get("lastModifiedTime"));

        map = Attributes.readAttributes(file,
            "basic:lastModifiedTime,lastAccessTime,linkCount,ShouldNotExist");
        assertTrue(map.size() == 3);
        checkEqual(attrs.lastModifiedTime(), map.get("lastModifiedTime"));
        checkEqual(attrs.lastAccessTime(), map.get("lastAccessTime"));
        checkEqual(attrs.lastAccessTime(), map.get("lastAccessTime"));
    }

    // Exercise getAttribute/setAttribute/readAttributes on posix attributes
    static void checkPosixAttributes(FileRef file, PosixFileAttributes attrs)
        throws IOException
    {
        checkBasicAttributes(file, attrs);

        // getAttribute
        checkEqual(attrs.permissions(),
                   Attributes.getAttribute(file, "posix:permissions"));
        checkEqual(attrs.owner(),
                   Attributes.getAttribute(file, "posix:owner"));
        checkEqual(attrs.group(),
                   Attributes.getAttribute(file, "posix:group"));

        // setAttribute
        Set<PosixFilePermission> orig = attrs.permissions();
        Set<PosixFilePermission> newPerms = new HashSet<PosixFilePermission>(orig);
        newPerms.remove(PosixFilePermission.OTHERS_READ);
        newPerms.remove(PosixFilePermission.OTHERS_WRITE);
        newPerms.remove(PosixFilePermission.OTHERS_EXECUTE);
        Attributes.setAttribute(file, "posix:permissions", newPerms);
        checkEqual(Attributes.readPosixFileAttributes(file).permissions(), newPerms);
        Attributes.setAttribute(file, "posix:permissions", orig);
        checkEqual(Attributes.readPosixFileAttributes(file).permissions(), orig);
        Attributes.setAttribute(file, "posix:owner", attrs.owner());
        Attributes.setAttribute(file, "posix:group", attrs.group());

        // readAttributes
        Map<String,?> map;
        map = Attributes.readAttributes(file, "posix:*");
        assertTrue(map.size() >= 14);
        checkEqual(attrs.permissions(), map.get("permissions")); // check one

        map = Attributes.readAttributes(file, "posix:size,owner,ShouldNotExist");
        assertTrue(map.size() == 2);
        checkEqual(attrs.size(), map.get("size"));
        checkEqual(attrs.owner(), map.get("owner"));
    }

    // Exercise getAttribute/setAttribute/readAttributes on unix attributes
    static void checkUnixAttributes(FileRef file) throws IOException {
        // getAttribute
        int mode = (Integer)Attributes.getAttribute(file, "unix:mode");
        long ino = (Long)Attributes.getAttribute(file, "unix:ino");
        long dev = (Long)Attributes.getAttribute(file, "unix:dev");
        long rdev = (Long)Attributes.getAttribute(file, "unix:rdev");
        int uid = (Integer)Attributes.getAttribute(file, "unix:uid");
        int gid = (Integer)Attributes.getAttribute(file, "unix:gid");
        long ctime = (Long)Attributes.getAttribute(file, "unix:ctime");

        // readAttributes
        Map<String,?> map;
        map = Attributes.readAttributes(file, "unix:*");
        assertTrue(map.size() >= 21);

        map = Attributes.readAttributes(file, "unix:size,uid,gid,ShouldNotExist");
        assertTrue(map.size() == 3);
        checkEqual(map.get("size"),
                   Attributes.readBasicFileAttributes(file).size());
    }

    // Exercise getAttribute/setAttribute/readAttributes on dos attributes
    static void checkDosAttributes(FileRef file, DosFileAttributes attrs)
        throws IOException
    {
        checkBasicAttributes(file, attrs);

        // getAttribute
        checkEqual(attrs.isReadOnly(),
                   Attributes.getAttribute(file, "dos:readonly"));
        checkEqual(attrs.isHidden(),
                   Attributes.getAttribute(file, "dos:hidden"));
        checkEqual(attrs.isSystem(),
                   Attributes.getAttribute(file, "dos:system"));
        checkEqual(attrs.isArchive(),
                   Attributes.getAttribute(file, "dos:archive"));

        // setAttribute
        boolean value;

        value = attrs.isReadOnly();
        Attributes.setAttribute(file, "dos:readonly", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isReadOnly(), !value);
        Attributes.setAttribute(file, "dos:readonly", value);
        checkEqual(Attributes.readDosFileAttributes(file).isReadOnly(), value);

        value = attrs.isHidden();
        Attributes.setAttribute(file, "dos:hidden", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isHidden(), !value);
        Attributes.setAttribute(file, "dos:hidden", value);
        checkEqual(Attributes.readDosFileAttributes(file).isHidden(), value);

        value = attrs.isSystem();
        Attributes.setAttribute(file, "dos:system", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isSystem(), !value);
        Attributes.setAttribute(file, "dos:system", value);
        checkEqual(Attributes.readDosFileAttributes(file).isSystem(), value);

        value = attrs.isArchive();
        Attributes.setAttribute(file, "dos:archive", !value);
        checkEqual(Attributes.readDosFileAttributes(file).isArchive(), !value);
        Attributes.setAttribute(file, "dos:archive", value);
        checkEqual(Attributes.readDosFileAttributes(file).isArchive(), value);

        // readAttributes
        Map<String,?> map;
        map = Attributes.readAttributes(file, "dos:*");
        assertTrue(map.size() >= 15);
        checkEqual(attrs.isReadOnly(), map.get("readonly")); // check one

        map = Attributes.readAttributes(file, "dos:size,hidden,ShouldNotExist");
        assertTrue(map.size() == 2);
        checkEqual(attrs.size(), map.get("size"));
        checkEqual(attrs.isHidden(), map.get("hidden"));
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

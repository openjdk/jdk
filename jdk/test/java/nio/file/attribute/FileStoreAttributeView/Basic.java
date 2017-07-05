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
 * @summary Unit test for java.nio.file.attribute.FileStoreAttributeView
 * @library ../..
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Simple unit test for FileStoreAttributeView that checks that the disk space
 * attribtues are "close" to the equivalent values reported by java.io.File.
 */

public class Basic {

    static final long K = 1024L;
    static final long G = 1024L * 1024L * 1024L;

    /**
     * Print out the disk space information for the given file system
     */
    static void printFileStore(FileStore fs) throws IOException {
        FileStoreSpaceAttributeView view =
            fs.getFileStoreAttributeView(FileStoreSpaceAttributeView.class);
        FileStoreSpaceAttributes attrs = view.readAttributes();

        long total = attrs.totalSpace() / K;
        long used = (attrs.totalSpace() - attrs.unallocatedSpace()) / K;
        long avail = attrs.usableSpace() / K;

        String s = fs.toString();
        if (s.length() > 20) {
            System.out.println(s);
            s = "";
        }
        System.out.format("%-20s %12d %12d %12d\n", s, total, used, avail);
    }

    /**
     * Check that two values are within 1GB of each other
     */
    static void checkWithin1GB(long value1, long value2) {
        long diff = Math.abs(value1 - value2);
        if (diff > G)
            throw new RuntimeException("values differ by more than 1GB");
    }

    /**
     * Check disk space on the file system of the given file
     */
    static void checkSpace(Path file) throws IOException {
        System.out.println(" -- check space -- ");
        System.out.println(file);

        FileStore fs = file.getFileStore();
        System.out.format("Filesystem: %s\n", fs);

        // get values reported by java.io.File
        File f = new File(file.toString());
        long total = f.getTotalSpace();
        long free = f.getFreeSpace();
        long usable = f.getUsableSpace();
        System.out.println("java.io.File");
        System.out.format("    Total: %d\n", total);
        System.out.format("     Free: %d\n", free);
        System.out.format("   Usable: %d\n", usable);

        // get values reported by the FileStoreSpaceAttributeView
        FileStoreSpaceAttributes attrs = fs
            .getFileStoreAttributeView(FileStoreSpaceAttributeView.class)
            .readAttributes();
        System.out.println("java.nio.file.FileStoreSpaceAttributeView:");
        System.out.format("    Total: %d\n", attrs.totalSpace());
        System.out.format("     Free: %d\n", attrs.unallocatedSpace());
        System.out.format("   Usable: %d\n", attrs.usableSpace());

        // check values are "close"
        checkWithin1GB(total, attrs.totalSpace());
        checkWithin1GB(free, attrs.unallocatedSpace());
        checkWithin1GB(usable, attrs.usableSpace());

        // get values by name (and in bulk)
        FileStoreAttributeView view = fs.getFileStoreAttributeView("space");
        checkWithin1GB(total, (Long)view.getAttribute("totalSpace"));
        checkWithin1GB(free, (Long)view.getAttribute("unallocatedSpace"));
        checkWithin1GB(usable, (Long)view.getAttribute("usableSpace"));
        Map<String,?> map = view.readAttributes("*");
        checkWithin1GB(total, (Long)map.get("totalSpace"));
        checkWithin1GB(free, (Long)map.get("unallocatedSpace"));
        checkWithin1GB(usable, (Long)map.get("usableSpace"));
        map = view.readAttributes("totalSpace", "unallocatedSpace", "usableSpace");
        checkWithin1GB(total, (Long)map.get("totalSpace"));
        checkWithin1GB(free, (Long)map.get("unallocatedSpace"));
        checkWithin1GB(usable, (Long)map.get("usableSpace"));
    }

    /**
     * Check (Windows-specific) volume attributes
     */
    static void checkVolumeAttributes() throws IOException {
        System.out.println(" -- volumes -- ");
        for (FileStore store: FileSystems.getDefault().getFileStores()) {
            FileStoreAttributeView view = store.getFileStoreAttributeView("volume");
            if (view == null)
                continue;
            Map<String,?> attrs = view.readAttributes("*");
            int vsn = (Integer)attrs.get("vsn");
            boolean compressed = (Boolean)attrs.get("compressed");
            boolean removable = (Boolean)attrs.get("removable");
            boolean cdrom = (Boolean)attrs.get("cdrom");
            String type;
            if (removable) type = "removable";
            else if (cdrom) type = "cdrom";
            else type = "unknown";
            System.out.format("%s (%s) vsn:%x compressed:%b%n", store.name(),
                type, vsn, compressed);
        }

    }

    public static void main(String[] args) throws IOException {
        // print out the disk space information for all file systems
        FileSystem fs = FileSystems.getDefault();
        for (FileStore store: fs.getFileStores()) {
            printFileStore(store);
        }

        Path dir = TestUtil.createTemporaryDirectory();
        try {
            // check space using directory
            checkSpace(dir);

            // check space using file
            Path file = dir.resolve("foo").createFile();
            checkSpace(file);

            // volume attributes (Windows specific)
            checkVolumeAttributes();

        } finally {
            TestUtil.removeAll(dir);
        }
    }
}

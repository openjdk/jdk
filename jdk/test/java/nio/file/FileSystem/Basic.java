/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6838333
 * @summary Unit test for java.nio.file.FileSystem
 * @library ..
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;

/**
 * Simple santity checks for java.nio.file.FileSystem
 */
public class Basic {

    static void check(boolean okay, String msg) {
        if (!okay)
            throw new RuntimeException(msg);
    }

    static void checkSupported(FileSystem fs, String... views) {
        for (String view: views) {
            check(fs.supportedFileAttributeViews().contains(view),
                "support for '" + view + "' expected");
        }
    }

    public static void main(String[] args) throws IOException {
        FileSystem fs = FileSystems.getDefault();

        // close should throw UOE
        try {
            fs.close();
            throw new RuntimeException("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException e) { }
        check(fs.isOpen(), "should be open");

        check(!fs.isReadOnly(), "should provide read-write access");

        check(fs.provider().getScheme().equals("file"),
            "should use 'file' scheme");

        // santity check method - need to re-visit this in future as I/O errors
        // are possible
        for (FileStore store: fs.getFileStores()) {
            System.out.println(store);
        }

        // sanity check supportedFileAttributeViews
        checkSupported(fs, "basic");
        String os = System.getProperty("os.name");
        if (os.equals("SunOS"))
            checkSupported(fs, "posix", "unix", "owner", "acl", "user");
        if (os.equals("Linux"))
            checkSupported(fs, "posix", "unix", "owner", "dos", "user");
        if (os.contains("OS X"))
            checkSupported(fs, "posix", "unix", "owner");
        if (os.equals("Windows"))
            checkSupported(fs, "owner", "dos", "acl", "user");
    }
}

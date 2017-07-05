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
 * @summary Unit test for java.nio.file.attribute.BasicFileAttributeView
 * @library ../..
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.*;

public class Basic {

    static void check(boolean okay, String msg) {
        if (!okay)
            throw new RuntimeException(msg);
    }

    static void checkAttributesOfDirectory(Path dir)
        throws IOException
    {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(dir);
        check(attrs.isDirectory(), "is a directory");
        check(!attrs.isRegularFile(), "is not a regular file");
        check(!attrs.isSymbolicLink(), "is not a link");
        check(!attrs.isOther(), "is not other");

        // last-modified-time should match java.io.File
        File f = new File(dir.toString());
        check(f.lastModified() == attrs.lastModifiedTime().toMillis(),
              "last-modified time should be the same");
    }

    static void checkAttributesOfFile(Path dir, Path file)
        throws IOException
    {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(file);
        check(attrs.isRegularFile(), "is a regular file");
        check(!attrs.isDirectory(), "is not a directory");
        check(!attrs.isSymbolicLink(), "is not a link");
        check(!attrs.isOther(), "is not other");

        // size and last-modified-time should match java.io.File
        File f = new File(file.toString());
        check(f.length() == attrs.size(), "size should be the same");
        check(f.lastModified() == attrs.lastModifiedTime().toMillis(),
              "last-modified time should be the same");

        // copy last-modified time and file create time from directory to file,
        // re-read attribtues, and check they match
        BasicFileAttributeView view =
            file.getFileAttributeView(BasicFileAttributeView.class);
        BasicFileAttributes dirAttrs = Attributes.readBasicFileAttributes(dir);
        view.setTimes(dirAttrs.lastModifiedTime(), null, null);
        if (dirAttrs.creationTime() != null) {
            view.setTimes(null, null, dirAttrs.creationTime());
        }
        attrs = view.readAttributes();
        check(attrs.lastModifiedTime().equals(dirAttrs.lastModifiedTime()),
            "last-modified time should be equal");
        if (dirAttrs.creationTime() != null) {
            check(attrs.creationTime().equals(dirAttrs.creationTime()),
                "create time should be the same");
        }

        // security tests
        check (!(attrs instanceof PosixFileAttributes),
            "should not be able to cast to PosixFileAttributes");
    }

    static void checkAttributesOfLink(Path link)
        throws IOException
    {
        BasicFileAttributes attrs = Attributes
            .readBasicFileAttributes(link, LinkOption.NOFOLLOW_LINKS);
        check(attrs.isSymbolicLink(), "is a link");
        check(!attrs.isDirectory(), "is a directory");
        check(!attrs.isRegularFile(), "is not a regular file");
        check(!attrs.isOther(), "is not other");
    }

    static void attributeReadWriteTests(Path dir)
        throws IOException
    {
        // create file
        Path file = dir.resolve("foo");
        OutputStream out = file.newOutputStream();
        try {
            out.write("this is not an empty file".getBytes("UTF-8"));
        } finally {
            out.close();
        }

        // check attributes of directory and file
        checkAttributesOfDirectory(dir);
        checkAttributesOfFile(dir, file);

        // symbolic links may be supported
        Path link = dir.resolve("link");
        try {
            link.createSymbolicLink( file );
        } catch (UnsupportedOperationException x) {
            return;
        } catch (IOException x) {
            return;
        }
        checkAttributesOfLink(link);
    }

    public static void main(String[] args) throws IOException {
        // create temporary directory to run tests
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            attributeReadWriteTests(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}

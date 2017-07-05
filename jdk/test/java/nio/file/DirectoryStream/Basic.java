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
 * @summary Unit test for java.nio.file.DirectoryStream
 * @library ..
 */

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public class Basic {
    static boolean found;

    static void doTest(final Path dir) throws IOException {
        DirectoryStream<Path> stream;

        // test that directory is empty
        stream = dir.newDirectoryStream();
        try {
            if (stream.iterator().hasNext())
                throw new RuntimeException("directory not empty");
        } finally {
            stream.close();
        }

        // create file in directory
        final Path foo = Paths.get("foo");
        dir.resolve(foo).createFile();

        // iterate over directory and check there is one entry
        stream = dir.newDirectoryStream();
        found = false;
        try {
            for (Path entry: stream) {
                if (entry.getName().equals(foo)) {
                    if (found)
                        throw new RuntimeException("entry already found");
                    found = true;
                } else {
                    throw new RuntimeException("entry " + entry.getName() +
                        " not expected");
                }
            }
        } finally {
            stream.close();
        }
        if (!found)
            throw new RuntimeException("entry not found");

        // check filtering: f* should match foo
        DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
            private PathMatcher matcher =
                dir.getFileSystem().getPathMatcher("glob:f*");
            public boolean accept(Path file) {
                return matcher.matches(file);
            }
        };
        stream = dir.newDirectoryStream(filter);
        try {
            for (Path entry: stream) {
                if (!entry.getName().equals(foo))
                    throw new RuntimeException("entry not expected");
            }
        } finally {
            stream.close();
        }

        // check filtering: z* should not match any files
        filter = new DirectoryStream.Filter<Path>() {
            private PathMatcher matcher =
                dir.getFileSystem().getPathMatcher("glob:z*");
            public boolean accept(Path file) {
                return matcher.matches(file);
            }
        };
        stream = dir.newDirectoryStream(filter);
        try {
            if (stream.iterator().hasNext())
                throw new RuntimeException("no matching entries expected");
        } finally {
            stream.close();
        }

        // check that IOExceptions throws by filters are propagated
        filter = new DirectoryStream.Filter<Path>() {
            public boolean accept(Path file) throws IOException {
                throw new IOException();
            }
        };
        stream = dir.newDirectoryStream(filter);
        try {
            stream.iterator().hasNext();
            throw new RuntimeException("ConcurrentModificationException expected");
        } catch (ConcurrentModificationException x) {
            Throwable t = x.getCause();
            if (!(t instanceof IOException))
                throw new RuntimeException("Cause is not IOException as expected");
        } finally {
            stream.close();
        }

        // check that exception or error thrown by filter is not thrown
        // by newDirectoryStream or iterator method.
        stream = dir.newDirectoryStream(new DirectoryStream.Filter<Path>() {
            public boolean accept(Path file) {
                throw new RuntimeException("Should not be visible");
            }
        });
        try {
            stream.iterator();
        } finally {
            stream.close();
        }

        // test NotDirectoryException
        try {
            dir.resolve(foo).newDirectoryStream();
            throw new RuntimeException("NotDirectoryException not thrown");
        } catch (NotDirectoryException x) {
        }

        // test iterator remove method
        stream = dir.newDirectoryStream();
        Iterator<Path> i = stream.iterator();
        while (i.hasNext()) {
            Path entry = i.next();
            if (!entry.getName().equals(foo))
                throw new RuntimeException("entry not expected");
            i.remove();
        }
        stream.close();

        // test IllegalStateException
        stream =  dir.newDirectoryStream();
        i = stream.iterator();
        try {
            stream.iterator();
            throw new RuntimeException("IllegalStateException not thrown as expected");
        } catch (IllegalStateException x) {
        }
        stream.close();
        try {
            stream.iterator();
            throw new RuntimeException("IllegalStateException not thrown as expected");
        } catch (IllegalStateException x) {
        }
        try {
            i.hasNext();
            throw new RuntimeException("ConcurrentModificationException not thrown as expected");
        } catch (ConcurrentModificationException x) {
            Throwable t = x.getCause();
            if (!(t instanceof IllegalStateException))
                throw new RuntimeException("Cause is not IllegalStateException as expected");
        }
        try {
            i.next();
            throw new RuntimeException("IllegalStateException not thrown as expected");
        } catch (ConcurrentModificationException x) {
            Throwable t = x.getCause();
            if (!(t instanceof IllegalStateException))
                throw new RuntimeException("Cause is not IllegalStateException as expected");
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            doTest(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}

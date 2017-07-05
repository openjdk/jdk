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
 * @bug 4313887 6873621
 * @summary Unit test for java.nio.file.FileStore
 * @library ..
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;

public class Basic {

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            doTests(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }

    static void assertTrue(boolean okay) {
        if (!okay)
            throw new RuntimeException("Assertion failed");
    }

    static void doTests(Path dir) throws IOException {
        /**
         * Test: Directory should be on FileStore that is writable
         */
        assertTrue(!dir.getFileStore().isReadOnly());

        /**
         * Test: Two files should have the same FileStore
         */
        FileStore store1 = dir.resolve("foo").createFile().getFileStore();
        FileStore store2 = dir.resolve("bar").createFile().getFileStore();
        assertTrue(store1.equals(store2));
        assertTrue(store2.equals(store1));
        assertTrue(store1.hashCode() == store2.hashCode());

        /**
         * Test: File and FileStore attributes
         */
        assertTrue(store1.supportsFileAttributeView("basic"));
        assertTrue(store1.supportsFileAttributeView(BasicFileAttributeView.class));
        assertTrue(store1.supportsFileAttributeView("posix") ==
            store1.supportsFileAttributeView(PosixFileAttributeView.class));
        assertTrue(store1.supportsFileAttributeView("dos") ==
            store1.supportsFileAttributeView(DosFileAttributeView.class));
        assertTrue(store1.supportsFileAttributeView("acl") ==
            store1.supportsFileAttributeView(AclFileAttributeView.class));
        assertTrue(store1.supportsFileAttributeView("user") ==
            store1.supportsFileAttributeView(UserDefinedFileAttributeView.class));

        /**
         * Test: Enumerate all FileStores
         */
        FileStore prev = null;
        for (FileStore store: FileSystems.getDefault().getFileStores()) {
            System.out.format("%s (name=%s type=%s)\n", store, store.name(),
                store.type());

            // check space attributes
            Attributes.readFileStoreSpaceAttributes(store);

            // two distinct FileStores should not be equal
            assertTrue(!store.equals(prev));
            prev = store;
        }
    }
}

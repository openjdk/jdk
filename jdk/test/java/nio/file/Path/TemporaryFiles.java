/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for File.createTemporaryXXX (to be be moved to test/java/io/File)
 * @library ..
 */

import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import java.nio.file.attribute.*;
import java.io.File;
import java.io.IOException;
import java.util.Set;

public class TemporaryFiles {

    static void checkInTempDirectory(Path file) {
        Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir"));
        if (!file.getParent().equals(tmpdir))
            throw new RuntimeException("Not in temporary directory");
    }

    static void checkFile(Path file) throws IOException {
        // check file is in temporary directory
        checkInTempDirectory(file);

        // check that file can be opened for reading and writing
        file.newByteChannel(READ).close();
        file.newByteChannel(WRITE).close();
        file.newByteChannel(READ,WRITE).close();

        // check file permissions are 0600 or more secure
        if (file.getFileStore().supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> perms = Attributes
                .readPosixFileAttributes(file).permissions();
            perms.remove(PosixFilePermission.OWNER_READ);
            perms.remove(PosixFilePermission.OWNER_WRITE);
            if (!perms.isEmpty())
                throw new RuntimeException("Temporary file is not secure");
        }
    }

    static void checkDirectory(Path dir) throws IOException {
        // check directory is in temporary directory
        checkInTempDirectory(dir);

        // check directory is empty
        DirectoryStream<Path> stream = dir.newDirectoryStream();
        try {
            if (stream.iterator().hasNext())
                throw new RuntimeException("Tempory directory not empty");
        } finally {
            stream.close();
        }

        // check file permissions are 0700 or more secure
        if (dir.getFileStore().supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> perms = Attributes
                .readPosixFileAttributes(dir).permissions();
            perms.remove(PosixFilePermission.OWNER_READ);
            perms.remove(PosixFilePermission.OWNER_WRITE);
            perms.remove(PosixFilePermission.OWNER_EXECUTE);
            if (!perms.isEmpty())
                throw new RuntimeException("Temporary directory is not secure");
        }
    }

    public static void main(String[] args) throws IOException {
        Path file = File.createTemporaryFile("blah", null).toPath();
        try {
            checkFile(file);
        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }
}

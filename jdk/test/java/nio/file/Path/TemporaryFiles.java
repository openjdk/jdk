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

import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import java.nio.file.attribute.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

public class TemporaryFiles {

    static void checkFile(Path file) throws IOException {
        // check file is in temporary directory
        Path tmpdir = Paths.get(System.getProperty("java.io.tmpdir"));
        if (!file.getParent().equals(tmpdir))
            throw new RuntimeException("Not in temporary directory");

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

    public static void main(String[] args) throws IOException {
        Path file = File.createTempFile("blah", null, false).toPath();
        try {
            checkFile(file);
        } finally {
            TestUtil.deleteUnchecked(file);
        }

        // temporary file with deleteOnExit
        file = File.createTempFile("blah", "tmp", true).toPath();
        checkFile(file);
        // write path to temporary file to file so that calling script can
        // check that it is deleted
        OutputStream out = Paths.get(args[0]).newOutputStream();
        try {
            out.write(file.toString().getBytes());
        } finally {
            out.close();
        }
    }
}

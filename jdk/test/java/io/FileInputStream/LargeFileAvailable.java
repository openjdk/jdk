/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6402006
 * @summary Test if available returns correct value when reading
 *          a large file.
 */

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import static java.nio.file.StandardOpenOption.*;

public class LargeFileAvailable {
    private static final long FILESIZE = 7405576182L;
    public static void main(String args[]) throws Exception {
        File file = createLargeFile(FILESIZE);
        try (FileInputStream fis = new FileInputStream(file)) {
            if (file.length() != FILESIZE) {
                throw new RuntimeException("unexpected file size = " + file.length());
            }

            long bigSkip = 3110608882L;
            long remaining = FILESIZE;
            remaining -= skipBytes(fis, bigSkip, remaining);
            remaining -= skipBytes(fis, 10L, remaining);
            remaining -= skipBytes(fis, bigSkip, remaining);
            if (fis.available() != (int) remaining) {
                 throw new RuntimeException("available() returns " +
                     fis.available() +
                     " but expected " + remaining);
            }
        } finally {
            file.delete();
        }
    }

    // Skip toSkip number of bytes and expect that the available() method
    // returns avail number of bytes.
    private static long skipBytes(InputStream is, long toSkip, long avail)
            throws IOException {
        long skip = is.skip(toSkip);
        if (skip != toSkip) {
            throw new RuntimeException("skip() returns " + skip +
                " but expected " + toSkip);
        }
        long remaining = avail - skip;
        int expected = remaining >= Integer.MAX_VALUE
                           ? Integer.MAX_VALUE
                           : (int) remaining;

        System.out.println("Skipped " + skip + " bytes " +
            " available() returns " + expected +
            " remaining=" + remaining);
        if (is.available() != expected) {
            throw new RuntimeException("available() returns " +
                is.available() + " but expected " + expected);
        }
        return skip;
    }

    private static File createLargeFile(long filesize) throws Exception {
        // Create a large file as a sparse file if possible
        File largefile = File.createTempFile("largefile", null);
        // re-create as a sparse file
        largefile.toPath().delete();
        try (FileChannel fc =
                FileChannel.open(largefile.toPath(),
                                 CREATE_NEW, WRITE, SPARSE)) {
            ByteBuffer bb = ByteBuffer.allocate(1).put((byte)1);
            bb.rewind();
            int rc = fc.write(bb, filesize-1);
            if (rc != 1) {
                throw new RuntimeException("Failed to write 1 byte to the large file");
            }
        }
        return largefile;
    }
}

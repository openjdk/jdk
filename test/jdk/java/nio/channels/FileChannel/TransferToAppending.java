/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8292562
 * @summary Test transferTo and transferFrom when target is appending
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main TransferToAppending
 * @key randomness
 */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.util.Random;
import jdk.test.lib.RandomFactory;

import static java.nio.file.StandardOpenOption.*;

public class TransferToAppending {
    private static final int MIN_SIZE =   128;
    private static final int MAX_SIZE = 32768;
    private static final Random RND = RandomFactory.getRandom();

    public static void main(String... args) throws IOException {
        // Create files in size range [MIN_SIZE,MAX_SIZE)
        // filled with random bytes
        Path source = createFile("src");
        Path target = createFile("tgt");

        try (FileChannel src = FileChannel.open(source, READ, WRITE);
             FileChannel tgt = FileChannel.open(target, WRITE, APPEND);) {
            // Set source range to a subset of the source
            long size = Files.size(source);
            long position = RND.nextInt((int)size);
            long count = RND.nextInt((int)(size - position));
            long tgtSize = Files.size(target);

            // Transfer subrange to target
            long nbytes = src.transferTo(position, count, tgt);

            long expectedSize = tgtSize + nbytes;

            if (Files.size(target) != expectedSize) {
                String msg = String.format("Bad size: expected %d, actual %d%n",
                                  expectedSize, Files.size(target));
                throw new RuntimeException(msg);
            }

            tgt.close();

            // Load subrange of source
            ByteBuffer bufSrc = ByteBuffer.allocate((int)nbytes);
            src.read(bufSrc, position);

            try (FileChannel res = FileChannel.open(target, READ, WRITE)) {
                // Load appended range of target
                ByteBuffer bufTgt = ByteBuffer.allocate((int)nbytes);
                res.read(bufTgt, tgtSize);

                // Subranges of values should be equal
                if (bufSrc.mismatch(bufTgt) != -1) {
                    throw new RuntimeException("Range of values unequal");
                }
            }
        } finally {
            Files.delete(source);
            Files.delete(target);
        }
    }

    private static Path createFile(String name) throws IOException {
        Path path = Files.createTempFile(name, ".dat");
        try (FileChannel fc = FileChannel.open(path, CREATE, READ, WRITE)) {
            int size = Math.max(RND.nextInt(MAX_SIZE), 128);
            byte[] b = new byte[size];
            RND.nextBytes(b);
            fc.write(ByteBuffer.wrap(b));
        }
        return path;
    }
}

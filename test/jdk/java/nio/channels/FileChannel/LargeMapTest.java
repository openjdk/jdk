/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.RandomFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.*;

/*
 * @test
 * @bug 8286637
 * @summary Ensure that memory mapping beyond 32-bit range does not cause an
 *          EXCEPTION_ACCESS_VIOLATION.
 * @requires vm.bits == 64
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory FileChannelUtils
 * @run main/othervm LargeMapTest
 * @key randomness
 */
public class LargeMapTest {
    private static final long LENGTH = (1L << 32) + 512;
    private static final int  EXTRA  = 1024;
    private static final long BASE   = LENGTH - EXTRA;
    private static final Random GEN  = RandomFactory.getRandom();

    public static void main(String[] args) throws IOException {
        System.out.println(System.getProperty("sun.arch.data.model"));
        System.out.println(System.getProperty("os.arch"));
        System.out.println(System.getProperty("java.version"));

        System.out.println("  Writing large file...");
        long t0 = System.nanoTime();
        Path p = FileChannelUtils.createSparseTempFile("test", ".dat");
        p.toFile().deleteOnExit();
        ByteBuffer bb;
        try (FileChannel fc = FileChannel.open(p, WRITE)) {
            fc.position(BASE);
            byte[] b = new byte[EXTRA];
            GEN.nextBytes(b);
            bb = ByteBuffer.wrap(b);
            fc.write(bb);
            long t1 = System.nanoTime();
            System.out.printf("  Wrote large file in %d ns (%d ms) %n",
                    t1 - t0, TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        }
        bb.rewind();

        try (FileChannel fc = FileChannel.open(p, READ, WRITE)) {
            MemorySegment mappedMemorySegment =
                fc.map(FileChannel.MapMode.READ_WRITE, 0, p.toFile().length(),
                       Arena.ofAuto());
            MemorySegment target = mappedMemorySegment.asSlice(BASE, EXTRA);
            if (!target.asByteBuffer().equals(bb)) {
                throw new RuntimeException("Expected buffers to be equal");
            }
        }
    }
}

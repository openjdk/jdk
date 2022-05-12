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

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/*
 * @test
 * @enablePreview
 * @modules jdk.incubator.foreign
 * @bug 8286637
 * @requires os.family == "windows"
 * @summary Ensure that memory mapping beyond 32-bit range does not cause an
 *          EXCEPTION_ACCESS_VIOLATION.
 * @run main/othervm LargeMapTest
 */
public class LargeMapTest {
    private static final String FILE = "test.dat";
    private static final long LENGTH = 8000000000L;
    private static final long OFFSET = 3704800000L;
    private static final int  BUFSIZ = 100000;

    public static void main(String[] args) throws IOException {
        System.out.println(System.getProperty("sun.arch.data.model"));
        System.out.println(System.getProperty("os.arch"));
        System.out.println(System.getProperty("java.version"));

        Path p = Path.of(FILE);
        p.toFile().deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(FILE, "rw");) {
            raf.setLength(LENGTH); //~8gb
        }

        long offset = OFFSET;
        ByteBuffer bb = ByteBuffer.allocateDirect(BUFSIZ);

        MemorySegment mbb = MemorySegment.ofByteBuffer(bb);
        MemorySegment mappedMemorySegment = MemorySegment.mapFile(p, 0,
            p.toFile().length(), FileChannel.MapMode.READ_WRITE,
            ResourceScope.newSharedScope());

        final int interval = BUFSIZ*1000;
        while (offset < LENGTH) {
            if (offset % interval == 0)
                System.out.println("offset: " + offset);
            MemorySegment target = mappedMemorySegment.asSlice(offset, BUFSIZ);
            offset = offset + BUFSIZ;
            target.copyFrom(mbb);
        }
    }
}

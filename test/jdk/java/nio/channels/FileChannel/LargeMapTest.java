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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;

/*
 * @test
 * @enablePreview
 * @bug 8286637
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
        try (FileChannel fc = FileChannel.open(p, CREATE, WRITE)) {
            fc.position(LENGTH - 1);
            fc.write(ByteBuffer.wrap(new byte[] {27}));
        }

        long offset = OFFSET;
        ByteBuffer bb = ByteBuffer.allocateDirect(BUFSIZ);

        try (FileChannel fc = FileChannel.open(p, READ, WRITE);) {
            MemorySegment mbb = MemorySegment.ofByteBuffer(bb);
            MemorySegment mappedMemorySegment =
                fc.map(FileChannel.MapMode.READ_WRITE, 0, p.toFile().length(),
                       MemorySession.openImplicit());

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
}

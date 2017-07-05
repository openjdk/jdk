/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4475533 4698138 4638365 4796221
 * @summary Test FileChannel write
 * @run main/othervm Write
 */

import java.nio.channels.*;
import java.nio.*;
import java.io.*;

public class Write {

   public static void main(String[] args) throws Exception {
       test1(); // for bug 4475533
       test2();
       test3(); // for bug 4698138

       // This test is not suitable for automated testing at this time.
       // I am commenting it out but it will be easy to manually
       // test for a regression in this area. See also 4796221.
       //test4(); // for bug 4638365
   }

    // Test to see that offset > length does not throw exception
    static void test1() throws Exception {
        ByteBuffer[] dsts = new ByteBuffer[4];
        for (int i=0; i<4; i++)
            dsts[i] = ByteBuffer.allocateDirect(10);

        File testFile = File.createTempFile("test1", null);
        try {
            FileOutputStream fos = new FileOutputStream(testFile);
            FileChannel fc = fos.getChannel();
            fc.write(dsts, 2, 1);
            fos.close();
        } finally {
            testFile.delete();
        }
    }

    // Test to see that the appropriate buffers are updated
    static void test2() throws Exception {
        File testFile = File.createTempFile("test2", null);
        testFile.delete();
        ByteBuffer[] srcs = new ByteBuffer[4];
        for (int i=0; i<4; i++)
            srcs[i] = ByteBuffer.allocateDirect(10);

        srcs[0].put((byte)1); srcs[0].flip();
        srcs[1].put((byte)2); srcs[1].flip();
        srcs[2].put((byte)3); srcs[2].flip();
        srcs[3].put((byte)4); srcs[3].flip();

        FileOutputStream fos = new FileOutputStream(testFile);
        FileChannel fc = fos.getChannel();
        try {
            fc.write(srcs, 1, 2);
        } finally {
            fc.close();
        }

        FileInputStream fis = new FileInputStream(testFile);
        fc = fis.getChannel();
        try {
            ByteBuffer bb = ByteBuffer.allocateDirect(10);
            fc.read(bb);
            bb.flip();
            if (bb.get() != 2)
                throw new RuntimeException("Write failure");
            if (bb.get() != 3)
                throw new RuntimeException("Write failure");
            try {
                bb.get();
                throw new RuntimeException("Write failure");
            } catch (BufferUnderflowException bufe) {
                // correct result
            }
        } finally {
            fc.close();
        }

        // eagerly clean-up
        testFile.delete();
    }

    // Test write to a negative position (bug 4698138).
    static void test3() throws Exception {
        File testFile = File.createTempFile("test1", null);
        testFile.deleteOnExit();
        ByteBuffer dst = ByteBuffer.allocate(10);
        FileOutputStream fos = new FileOutputStream(testFile);
        FileChannel fc = fos.getChannel();
        try {
            fc.write(dst, -1);
            throw new RuntimeException("Expected IAE not thrown");
        } catch (IllegalArgumentException iae) {
            // Correct result
        } finally {
            fos.close();
        }
    }

    private static final int TEST4_NUM_BUFFERS = 3;

    private static final int TEST4_BUF_CAP = Integer.MAX_VALUE / 2;

    /**
     * Test to see that vector write can return > Integer.MAX_VALUE
     *
     * Note that under certain circumstances disk space problems occur
     * with this test. It typically relies upon adequate disk space and/or
     * a Solaris disk space optimization where empty files take up less
     * space than their logical size.
     *
     * Note that if this test fails it is not necessarily a violation of
     * spec: the value returned by fc.write can be smaller than the number
     * of bytes requested to write. It is testing an optimization that allows
     * for larger return values.
     */
    static void test4() throws Exception {
        // Only works on 64 bit Solaris
        String osName = System.getProperty("os.name");
        if (!osName.startsWith("SunOS"))
            return;
        String dataModel = System.getProperty("sun.arch.data.model");
        if (!dataModel.startsWith("64"))
            return;

        File testFile = File.createTempFile("test4", null);
        testFile.deleteOnExit();

        FileChannel[] fcs = new FileChannel[TEST4_NUM_BUFFERS];

        ByteBuffer[] dsts = new ByteBuffer[TEST4_NUM_BUFFERS];
        // Map these buffers from a file so we don't run out of memory
        for (int i=0; i<TEST4_NUM_BUFFERS; i++) {
            File f = File.createTempFile("test4." + i, null);
            f.deleteOnExit();
            prepTest4File(f);
            FileInputStream fis = new FileInputStream(f);
            FileChannel fc = fis.getChannel();
            MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0,
                                          TEST4_BUF_CAP);
            dsts[i] = mbb;
        }

        FileOutputStream fos = new FileOutputStream(testFile);
        FileChannel fc = fos.getChannel();
        try {
            long bytesWritten = fc.write(dsts);
            if (bytesWritten < Integer.MAX_VALUE) {
                // Note: this is not a violation of the spec
                throw new RuntimeException("Test 4 failed but wrote " +
                                           bytesWritten);
            }
        } finally {
            fc.close();
            fos.close();
        }
    }

    static void prepTest4File(File blah) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(blah, "rw");
        FileChannel fc = raf.getChannel();
        fc.write(ByteBuffer.wrap("Use the source!".getBytes()),
                 TEST4_BUF_CAP);
        fc.close();
        raf.close();
    }

}

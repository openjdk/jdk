/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4563125
 * @summary Test size method of FileChannel
 * @run main/othervm Size
 */

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.util.Random;


/**
 * Testing FileChannel's size method.
 */

public class Size {

    private static Random generator = new Random();

    private static File blah;

    public static void main(String[] args) throws Exception {
        test1();
        test2();
    }

    private static void test1() throws Exception {
        blah = File.createTempFile("blah", null);
        blah.deleteOnExit();
        for(int i=0; i<100; i++) {
            long testSize = generator.nextInt(1000);
            initTestFile(blah, testSize);
            FileInputStream fis = new FileInputStream(blah);
            FileChannel c = fis.getChannel();
            if (c.size() != testSize)
                throw new RuntimeException("Size failed");
            c.close();
            fis.close();
        }
        blah.delete();
    }

    // Test for bug 4563125
    private static void test2() throws Exception {
        // Windows and Linux can't handle the really large file sizes for a truncate
        // or a positional write required by the test for 4563125
        String osName = System.getProperty("os.name");
        if (osName.startsWith("SunOS") || osName.startsWith("Mac OS")) {
            blah = File.createTempFile("blah", null);
            long testSize = ((long)Integer.MAX_VALUE) * 2;
            initTestFile(blah, 10);
            RandomAccessFile raf = new RandomAccessFile(blah, "rw");
            FileChannel fc = raf.getChannel();
            fc.size();
            fc.map(FileChannel.MapMode.READ_WRITE, testSize, 10);
            if (fc.size() != testSize + 10)
                throw new RuntimeException("Size failed " + fc.size());
            fc.close();
            raf.close();
            blah.delete();
        }
    }

    /**
     * Creates file blah of specified size in bytes.
     *
     */
    private static void initTestFile(File blah, long size) throws Exception {
        if (blah.exists())
            blah.delete();
        FileOutputStream fos = new FileOutputStream(blah);
        BufferedWriter awriter
            = new BufferedWriter(new OutputStreamWriter(fos, "8859_1"));

        for(int i=0; i<size; i++) {
            awriter.write("e");
        }
        awriter.flush();
        awriter.close();
    }
}

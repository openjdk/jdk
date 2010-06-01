/*
 * Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6191269
 * @summary Test truncate method of FileChannel
 */

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.channels.FileChannel;
import java.util.Random;


/**
 * Testing FileChannel's truncate method.
 */

public class Truncate {

    private static Random generator = new Random();

    private static File blah;

    public static void main(String[] args) throws Exception {
        blah = File.createTempFile("blah", null);
        blah.deleteOnExit();
        for(int i=0; i<100; i++) {
            long testSize = generator.nextInt(1000) + 10;
            initTestFile(blah, testSize);
            RandomAccessFile fis = new RandomAccessFile(blah, "rw");
            FileChannel c = fis.getChannel();
            if (c.size() != testSize)
                throw new RuntimeException("Size failed");

            long position = generator.nextInt((int)testSize);
            c.position(position);

            long newSize = generator.nextInt((int)testSize);
            c.truncate(newSize);

            if (c.size() != newSize)
                throw new RuntimeException("Truncate failed");

            if (position > newSize) {
                if (c.position() != newSize)
                    throw new RuntimeException("Position greater than size");
            } else {
                if (c.position() != position)
                    throw new RuntimeException("Truncate changed position");
            }

            c.close();
            fis.close();
        }
        blah.delete();
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

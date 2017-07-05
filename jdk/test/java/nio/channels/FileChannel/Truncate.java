/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6191269 6709457
 * @summary Test truncate method of FileChannel
 */

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import static java.nio.file.StandardOpenOption.*;
import java.util.Random;


/**
 * Testing FileChannel's truncate method.
 */

public class Truncate {
    private static final Random generator = new Random();

    public static void main(String[] args) throws Exception {
        File blah = File.createTempFile("blah", null);
        blah.deleteOnExit();
        try {
            basicTest(blah);
            appendTest(blah);
        } finally {
            blah.delete();
        }
    }

    /**
     * Basic test of asserts in truncate's specification.
     */
    static void basicTest(File blah) throws Exception {
        for(int i=0; i<100; i++) {
            long testSize = generator.nextInt(1000) + 10;
            initTestFile(blah, testSize);

            try (FileChannel fc = (i < 50) ?
                 new RandomAccessFile(blah, "rw").getChannel() :
                 FileChannel.open(blah.toPath(), READ, WRITE))
                {
                    if (fc.size() != testSize)
                        throw new RuntimeException("Size failed");

                    long position = generator.nextInt((int)testSize);
                    fc.position(position);

                    long newSize = generator.nextInt((int)testSize);
                    fc.truncate(newSize);

                    if (fc.size() != newSize)
                        throw new RuntimeException("Truncate failed");

                    if (position > newSize) {
                        if (fc.position() != newSize)
                            throw new RuntimeException("Position greater than size");
                    } else {
                        if (fc.position() != position)
                            throw new RuntimeException("Truncate changed position");
                    };
                }
        }
    }

    /**
     * Test behavior of truncate method when file is opened for append
     */
    static void appendTest(File blah) throws Exception {
        for (int i=0; i<10; i++) {
            long testSize = generator.nextInt(1000) + 10;
            initTestFile(blah, testSize);
            try (FileChannel fc = (i < 5) ?
                 new FileOutputStream(blah, true).getChannel() :
                 FileChannel.open(blah.toPath(), APPEND))
                {
                    // truncate file
                    long newSize = generator.nextInt((int)testSize);
                    fc.truncate(newSize);
                    if (fc.size() != newSize)
                        throw new RuntimeException("Truncate failed");

                    // write one byte
                    ByteBuffer buf = ByteBuffer.allocate(1);
                    buf.put((byte)'x');
                    buf.flip();
                    fc.write(buf);
                    if (fc.size() != (newSize+1))
                        throw new RuntimeException("Unexpected size");
                }
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

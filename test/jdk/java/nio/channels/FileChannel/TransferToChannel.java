/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4652496 8274112
 * @summary Test transferTo with different target channels
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main TransferToChannel
 * @run main/othervm -Djdk.nio.enableFastFileTransfer TransferToChannel
 * @key randomness
 */

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import jdk.test.lib.RandomFactory;

public class TransferToChannel {

    private static final Random RAND = RandomFactory.getRandom();

    // Chunk size should be larger than FileChannelImpl.TRANSFER_SIZE (8192)
    // for a good test
    private static final int CHUNK_SIZE = 9*1024;

    // File size should be a value much larger than CHUNK_SIZE
    private static final int FILE_SIZE = 1000*1024;

    // The minimum direct transfer size should be less than the file size
    // but still substantial
    private static final int MIN_DIRECT_TRANSFER_SIZE = FILE_SIZE/2;

    private static File file;
    private static File outFile;
    private static FileChannel in;

    public static void main(String[] args) throws Exception {
        file = File.createTempFile("readingin", null);
        outFile = File.createTempFile("writingout", null);
        file.deleteOnExit();
        outFile.deleteOnExit();
        generateBigFile(file);
        FileInputStream fis = new FileInputStream(file);
        in = fis.getChannel();
        test1();
        test2();
        test3();
        in.close();
        file.delete();
        outFile.delete();
    }

    private static void test1() throws Exception {
        for (int i=0; i<10; i++) {
            transferFileToUserChannel();
            System.gc();
            System.err.println("Transferred file to user channel...");
        }
    }

    private static void test2() throws Exception {
        for (int i=0; i<10; i++) {
            transferFileToTrustedChannel();
            System.gc();
            System.err.println("Transferred file to trusted channel...");
        }
    }

    private static void test3() throws Exception {
        for (int i=0; i<10; i++) {
            transferFileDirectly();
            System.gc();
            System.err.println("Transferred file directly...");
        }
    }

    private static void transferFileToUserChannel() throws Exception {
        long remainingBytes = in.size();
        long size = remainingBytes;
        WritableByteChannel wbc = new WritableByteChannel() {
            Random rand = new Random(0);
            public int write(ByteBuffer src) throws IOException {
                int read = src.remaining();
                byte[] incoming = new byte[read];
                src.get(incoming);
                checkData(incoming, read);
                return read == 0 ? -1 : read;
            }
            public boolean isOpen() {
                return true;
            }
            public void close() throws IOException {
            }
            void checkData(byte[] incoming, int size) {
                byte[] expected = new byte[size];
                rand.nextBytes(expected);
                if (!Arrays.equals(incoming, expected))
                    throw new RuntimeException("Data corrupted");
            }
        };
        while (remainingBytes > 0) {
            long bytesTransferred = in.transferTo(size - remainingBytes,
                              Math.min(CHUNK_SIZE, remainingBytes), wbc);
            if (bytesTransferred >= 0)
                remainingBytes -= bytesTransferred;
            else
                throw new Exception("transfer failed");
        }
    }

    private static void transferFileToTrustedChannel() throws Exception {
        long remainingBytes = in.size();
        long size = remainingBytes;
        FileOutputStream fos = new FileOutputStream(outFile);
        FileChannel out = fos.getChannel();
        while (remainingBytes > 0) {
            long bytesTransferred = in.transferTo(size - remainingBytes,
                                                  CHUNK_SIZE, out);
            if (bytesTransferred >= 0)
                remainingBytes -= bytesTransferred;
            else
                throw new Exception("transfer failed");
        }
        out.close();
    }

    private static void transferFileDirectly() throws Exception {
        outFile.delete();
        final long size = in.size();
        final long position = RAND.nextInt((int)size - MIN_DIRECT_TRANSFER_SIZE);
        try (FileOutputStream fos = new FileOutputStream(outFile);
             FileChannel out = fos.getChannel()) {

            assert out.position() == 0;
            long pos = position;
            while (pos < size) {
                long bytesTransferred = in.transferTo(pos, Long.MAX_VALUE, out);
                if (bytesTransferred >= 0)
                    pos += bytesTransferred;
                else {
                    throw new Exception("transfer failed at " + pos +
                        " / " + size);
                }
            }
        }

        byte[] expected = Files.readAllBytes(file.toPath());
        byte[] actual = Files.readAllBytes(outFile.toPath());
        if (!Arrays.equals(expected, (int)position, (int)size,
                           actual, 0, (int)(size - position)))
            throw new Exception("Actual bytes do not match expected bytes");
    }

    private static void generateBigFile(File file) throws Exception {
        OutputStream out = new BufferedOutputStream(
                           new FileOutputStream(file));
        byte[] randomBytes = new byte[1024];
        Random rand = new Random(0);
        int numWritten = 0;
        while (numWritten < FILE_SIZE) {
            int nwrite = Math.min(randomBytes.length, FILE_SIZE - numWritten);
            rand.nextBytes(randomBytes);
            out.write(randomBytes, 0, nwrite);
            numWritten += nwrite;
        }
        out.flush();
        out.close();
    }
}

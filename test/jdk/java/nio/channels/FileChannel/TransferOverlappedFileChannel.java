/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8140241
 * @summary Test transferring to and from same file channel
 */
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Random;

public class TransferOverlappedFileChannel {

    public static void main(String[] args) throws Exception {
        File file = File.createTempFile("readingin", null);
        file.deleteOnExit();
        generateBigFile(file);
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        try (FileChannel channel = raf.getChannel()) {
            transferToNoOverlap(file, channel);
            transferToOverlap(file, channel);
            transferFromNoOverlap(file, channel);
            transferFromOverlap(file, channel);
        } finally {
            file.delete();
        }
    }

    private static void transferToNoOverlap(File file, FileChannel channel)
        throws IOException {
        final long length = file.length();

        // position at three quarters
        channel.position(length*3/4);
        // copy last quarter to third quarter
        // (copied and overwritten regions do NOT overlap)
        // So: 1 2 3 4 -> 1 2 4 4
        channel.transferTo(length / 2, length / 4, channel);
        System.out.println("transferToNoOverlap: OK");
    }

    private static void transferToOverlap(File file, FileChannel channel)
        throws IOException {
        final long length = file.length();

        // position at half
        channel.position(length/2);
        // copy last half to second quarter
        // (copied and overwritten regions DO overlap)
        // So: 1 2 3 4 -> 1 3 4 4
        channel.transferTo(length / 4, length / 2, channel);
        System.out.println("transferToOverlap: OK");
    }

    private static void transferFromNoOverlap(File file, FileChannel channel)
        throws IOException {
        final long length = file.length();

        // position at three quarters
        channel.position(length*3/4);
        // copy last quarter to third quarter
        // (copied and overwritten regions do NOT overlap)
        // So: 1 2 3 4 -> 1 2 4 4
        channel.transferFrom(channel, length / 2, length / 4);
        System.out.println("transferFromNoOverlap: OK");
    }

    private static void transferFromOverlap(File file, FileChannel channel)
        throws IOException {
        final long length = file.length();

        // position at half
        channel.position(length/2);
        // copy last half to second quarter
        // (copied and overwritten regions DO overlap)
        // So: 1 2 3 4 -> 1 3 4 4
        channel.transferFrom(channel, length / 4, length / 2);
        System.out.println("transferFromOverlap: OK");
    }

    private static void generateBigFile(File file) throws Exception {
        try (OutputStream out = new BufferedOutputStream(
                new FileOutputStream(file))) {
            byte[] randomBytes = new byte[1024];
            Random rand = new Random(0);
            rand.nextBytes(randomBytes);
            for (int i = 0; i < 1024; i++) {
                out.write(randomBytes);
            }
            out.flush();
        }
    }
}

/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8164900
 * @summary Test for ExtendedOpenOption.DIRECT flag
 * @requires (os.family == "linux" | os.family == "aix")
 * @library /test/lib
 * @modules java.base/sun.nio.ch:+open java.base/java.io:+open
 * @build jdk.test.lib.Platform
 * @run main/native DirectIOTest
 */

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import jdk.test.lib.Platform;
import java.nio.file.FileStore;
import java.nio.file.StandardOpenOption;
import com.sun.nio.file.ExtendedOpenOption;

public class DirectIOTest {

    private static final int BASE_SIZE = 4096;
    private static final int TRIES = 3;

    public static int getFD(FileChannel channel) throws Exception {
        Field fFdFd = channel.getClass().getDeclaredField("fd");
        fFdFd.setAccessible(true);
        FileDescriptor fd = (FileDescriptor) fFdFd.get(channel);

        Field fFd = FileDescriptor.class.getDeclaredField("fd");
        fFd.setAccessible(true);
        return fFd.getInt(fd);
    }

    private static void testWrite(Path p, long blockSize) throws Exception {
        try (FileChannel fc = FileChannel.open(p,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                ExtendedOpenOption.DIRECT)) {
            int fd = getFD(fc);

            int bs = (int)blockSize;
            int size = Math.max(BASE_SIZE, bs);
            int alignment = bs;
            ByteBuffer src = ByteBuffer.allocateDirect(size + alignment - 1)
                                       .alignedSlice(alignment);
            assert src.capacity() != 0;
            for (int j = 0; j < size; j++) {
                src.put((byte)0);
            }

            // If there is AV or other FS tracing software, it may cache the file
            // contents on first access, even though we have asked for DIRECT here.
            // Do several attempts to make test more resilient.

            for (int t = 0; t < TRIES; t++) {
                flushFileCache(size, fd);
                src.flip();
                fc.position(0);
                fc.write(src);
                if (!isFileInCache(size, fd)) {
                    return;
                }
            }

            throw new RuntimeException("DirectIO is not working properly with " +
                                       "write. File still exists in cache!");
        }
    }

    private static void testRead(Path p, long blockSize) throws Exception {
        try (FileChannel fc = FileChannel.open(p,
                StandardOpenOption.READ,
                ExtendedOpenOption.DIRECT)) {
            int fd = getFD(fc);

            int bs = (int)blockSize;
            int size = Math.max(BASE_SIZE, bs);
            int alignment = bs;
            ByteBuffer dest = ByteBuffer.allocateDirect(size + alignment - 1)
                                        .alignedSlice(alignment);
            assert dest.capacity() != 0;

            // If there is AV or other FS tracing software, it may cache the file
            // contents on first access, even though we have asked for DIRECT here.
            // Do several attempts to make test more resilient.

            for (int t = 0; t < TRIES; t++) {
                flushFileCache(size, fd);
                dest.clear();
                fc.position(0);
                fc.read(dest);
                if (!isFileInCache(size, fd)) {
                    return;
                }
            }

            throw new RuntimeException("DirectIO is not working properly with " +
                                       "read. File still exists in cache!");
        }
    }

    public static Path createTempFile() throws IOException {
        return Files.createTempFile(
            Paths.get(System.getProperty("test.dir", ".")), "test", null);
    }

    private static native boolean flushFileCache(int size, int fd);
    private static native boolean isFileInCache(int size, int fd);

    public static void main(String[] args) throws Exception {
        Path p = createTempFile();
        long blockSize = Files.getFileStore(p).getBlockSize();

        System.loadLibrary("DirectIO");

        try {
            testWrite(p, blockSize);
            testRead(p, blockSize);
        } finally {
            Files.delete(p);
        }
    }
}

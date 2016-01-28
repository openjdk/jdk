/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;

import java.nio.ByteBuffer;

import java.nio.channels.FileChannel;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.util.List;
import java.util.Random;

/*
 * @test
 * @build TestMaxCachedBufferSize
 * @run main/othervm TestMaxCachedBufferSize
 * @run main/othervm -Djdk.nio.maxCachedBufferSize=0 TestMaxCachedBufferSize
 * @run main/othervm -Djdk.nio.maxCachedBufferSize=2000 TestMaxCachedBufferSize
 * @run main/othervm -Djdk.nio.maxCachedBufferSize=100000 TestMaxCachedBufferSize
 * @run main/othervm -Djdk.nio.maxCachedBufferSize=10000000 TestMaxCachedBufferSize
 *
 * @summary Test the implementation of the jdk.nio.maxCachedBufferSize property.
 */
public class TestMaxCachedBufferSize {
    private static final int DEFAULT_ITERS = 10 * 1000;
    private static final int DEFAULT_THREAD_NUM = 4;

    private static final int SMALL_BUFFER_MIN_SIZE =  4 * 1024;
    private static final int SMALL_BUFFER_MAX_SIZE = 64 * 1024;
    private static final int SMALL_BUFFER_DIFF_SIZE =
                                 SMALL_BUFFER_MAX_SIZE - SMALL_BUFFER_MIN_SIZE;

    private static final int LARGE_BUFFER_MIN_SIZE =      512 * 1024;
    private static final int LARGE_BUFFER_MAX_SIZE = 4 * 1024 * 1024;
    private static final int LARGE_BUFFER_DIFF_SIZE =
                                 LARGE_BUFFER_MAX_SIZE - LARGE_BUFFER_MIN_SIZE;

    private static final int LARGE_BUFFER_FREQUENCY = 100;

    private static final String FILE_NAME_PREFIX = "nio-out-file-";
    private static final int VERBOSE_PERIOD = 5 * 1000;

    private static int iters = DEFAULT_ITERS;
    private static int threadNum = DEFAULT_THREAD_NUM;

    private static BufferPoolMXBean getDirectPool() {
        final List<BufferPoolMXBean> pools =
                  ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (BufferPoolMXBean pool : pools) {
            if (pool.getName().equals("direct")) {
                return pool;
            }
        }
        throw new Error("could not find direct pool");
    }
    private static final BufferPoolMXBean directPool = getDirectPool();

    // Each worker will do write operations on a file channel using
    // buffers of various sizes. The buffer size is randomly chosen to
    // be within a small or a large range. This way we can control
    // which buffers can be cached (all, only the small ones, or none)
    // by setting the jdk.nio.maxCachedBufferSize property.
    private static class Worker implements Runnable {
        private final int id;
        private final Random random = new Random();
        private long smallBufferCount = 0;
        private long largeBufferCount = 0;

        private int getWriteSize() {
            int minSize = 0;
            int diff = 0;
            if (random.nextInt() % LARGE_BUFFER_FREQUENCY != 0) {
                // small buffer
                minSize = SMALL_BUFFER_MIN_SIZE;
                diff = SMALL_BUFFER_DIFF_SIZE;
                smallBufferCount += 1;
            } else {
                // large buffer
                minSize = LARGE_BUFFER_MIN_SIZE;
                diff = LARGE_BUFFER_DIFF_SIZE;
                largeBufferCount += 1;
            }
            return minSize + random.nextInt(diff);
        }

        private void loop() {
            final String fileName = String.format("%s%d", FILE_NAME_PREFIX, id);

            try {
                for (int i = 0; i < iters; i += 1) {
                    final int writeSize = getWriteSize();

                    // This will allocate a HeapByteBuffer. It should not
                    // be a direct buffer, otherwise the write() method on
                    // the channel below will not create a temporary
                    // direct buffer for the write.
                    final ByteBuffer buffer = ByteBuffer.allocate(writeSize);

                    // Put some random data on it.
                    while (buffer.hasRemaining()) {
                        buffer.put((byte) random.nextInt());
                    }
                    buffer.rewind();

                    final Path file = Paths.get(fileName);
                    try (FileChannel outChannel = FileChannel.open(file, CREATE, TRUNCATE_EXISTING, WRITE)) {
                        // The write() method will create a temporary
                        // direct buffer for the write and attempt to cache
                        // it. It's important that buffer is not a
                        // direct buffer, otherwise the temporary buffer
                        // will not be created.
                        long res = outChannel.write(buffer);
                    }

                    if ((i + 1) % VERBOSE_PERIOD == 0) {
                        System.out.printf(
                          " Worker %3d | %8d Iters | Small %8d Large %8d | Direct %4d / %7dK\n",
                          id, i + 1, smallBufferCount, largeBufferCount,
                          directPool.getCount(), directPool.getTotalCapacity() / 1024);
                    }
                }
            } catch (IOException e) {
                throw new Error("I/O error", e);
            }
        }

        @Override
        public void run() {
            loop();
        }

        public Worker(int id) {
            this.id = id;
        }
    }

    public static void checkDirectBuffers(long expectedCount, long expectedMax) {
        final long directCount = directPool.getCount();
        final long directTotalCapacity = directPool.getTotalCapacity();
        System.out.printf("Direct %d / %dK\n",
                          directCount, directTotalCapacity / 1024);

        // Note that directCount could be < expectedCount. This can
        // happen if a GC occurs after one of the worker threads exits
        // since its thread-local DirectByteBuffer could be cleaned up
        // before we reach here.
        if (directCount > expectedCount) {
            throw new Error(String.format(
                "inconsistent direct buffer total count, expected = %d, found = %d",
                expectedCount, directCount));
        }

        if (directTotalCapacity > expectedMax) {
            throw new Error(String.format(
                "inconsistent direct buffer total capacity, expectex max = %d, found = %d",
                expectedMax, directTotalCapacity));
        }
    }

    public static void main(String[] args) {
        final String maxBufferSizeStr = System.getProperty("jdk.nio.maxCachedBufferSize");
        final long maxBufferSize =
            (maxBufferSizeStr != null) ? Long.valueOf(maxBufferSizeStr) : Long.MAX_VALUE;

        // We assume that the max cannot be equal to a size of a
        // buffer that can be allocated (makes sanity checking at the
        // end easier).
        if ((SMALL_BUFFER_MIN_SIZE <= maxBufferSize &&
                                     maxBufferSize <= SMALL_BUFFER_MAX_SIZE) ||
            (LARGE_BUFFER_MIN_SIZE <= maxBufferSize &&
                                     maxBufferSize <= LARGE_BUFFER_MAX_SIZE)) {
            throw new Error(String.format("max buffer size = %d not allowed",
                                          maxBufferSize));
        }

        System.out.printf("Threads %d | Iterations %d | MaxBufferSize %d\n",
                          threadNum, iters, maxBufferSize);
        System.out.println();

        final Thread[] threads = new Thread[threadNum];
        for (int i = 0; i < threadNum; i += 1) {
            threads[i] = new Thread(new Worker(i));
            threads[i].start();
        }

        try {
            for (int i = 0; i < threadNum; i += 1) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            throw new Error("join() interrupted!", e);
        }

        // There is an assumption here that, at this point, only the
        // cached DirectByteBuffers should be active. Given we
        // haven't used any other DirectByteBuffers in this test, this
        // should hold.
        //
        // Also note that we can only do the sanity checking at the
        // end and not during the run given that, at any time, there
        // could be buffers currently in use by some of the workers
        // that will not be cached.

        System.out.println();
        if (maxBufferSize < SMALL_BUFFER_MAX_SIZE) {
            // The max buffer size is smaller than all buffers that
            // were allocated. No buffers should have been cached.
            checkDirectBuffers(0, 0);
        } else if (maxBufferSize < LARGE_BUFFER_MIN_SIZE) {
            // The max buffer size is larger than all small buffers
            // but smaller than all large buffers that were
            // allocated. Only small buffers could have been cached.
            checkDirectBuffers(threadNum,
                               (long) threadNum * (long) SMALL_BUFFER_MAX_SIZE);
        } else {
            // The max buffer size is larger than all buffers that
            // were allocated. All buffers could have been cached.
            checkDirectBuffers(threadNum,
                               (long) threadNum * (long) LARGE_BUFFER_MAX_SIZE);
        }
    }
}

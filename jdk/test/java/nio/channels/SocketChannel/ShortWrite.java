/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7176630
 * @summary Check for short writes on SocketChannels configured in blocking mode
 */

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.Random;
import java.util.zip.CRC32;

public class ShortWrite {

    static final Random rand = new Random();

    /**
     * Returns a checksum on the remaining bytes in the given buffer.
     */
    static long computeChecksum(ByteBuffer bb) {
        CRC32 crc32 = new CRC32();
        crc32.update(bb);
        return crc32.getValue();
    }

    /**
     * A task that reads the expected number of bytes and returns the CRC32
     * of those bytes.
     */
    static class Reader implements Callable<Long> {
        final SocketChannel sc;
        final ByteBuffer buf;

        Reader(SocketChannel sc, int expectedSize) {
            this.sc = sc;
            this.buf = ByteBuffer.allocate(expectedSize);
        }

        public Long call() throws Exception {
            while (buf.hasRemaining()) {
                int n = sc.read(buf);
                if (n == -1)
                    throw new RuntimeException("Premature EOF encountered");
            }
            buf.flip();
            return computeChecksum(buf);
        }
    }

    /**
     * Run test with a write of the given number of bytes.
     */
    static void test(ExecutorService pool,
                     SocketChannel source,
                     SocketChannel sink,
                     int size)
        throws Exception
    {
        System.out.println(size);

        // random bytes in the buffer
        ByteBuffer buf = ByteBuffer.allocate(size);
        rand.nextBytes(buf.array());

        // submit task to read the bytes
        Future<Long> result = pool.submit(new Reader(sink, size));

        // write the bytes
        int n = source.write(buf);
        if (n != size)
            throw new RuntimeException("Short write detected");

        // check the bytes that were received match
        buf.rewind();
        long expected = computeChecksum(buf);
        long actual = result.get();
        if (actual != expected)
            throw new RuntimeException("Checksum did not match");
    }


    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                ssc.bind(new InetSocketAddress(0));
                InetAddress lh = InetAddress.getLocalHost();
                int port = ssc.socket().getLocalPort();
                SocketAddress sa = new InetSocketAddress(lh, port);

                try (SocketChannel source = SocketChannel.open(sa);
                     SocketChannel sink = ssc.accept())
                {
                    // run tests on sizes around 128k as that is the problem
                    // area on Windows.
                    int BOUNDARY = 128 * 1024;
                    for (int size=(BOUNDARY-2); size<=(BOUNDARY+2); size++) {
                        test(pool, source, sink, size);
                    }

                    // run tests on random sizes
                    for (int i=0; i<20; i++) {
                        int size = rand.nextInt(1024*1024);
                        test(pool, source, sink, size);
                    }
                }
            }

        } finally {
            pool.shutdown();
        }
    }
}

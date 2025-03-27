/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.net;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

/**
 * Tests the overheads of I/O API.
 * This test is known to depend heavily on network conditions and paltform.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class UnixSocketChannelReadWrite {

    private ServerSocketChannel ssc;
    private SocketChannel s1, s2;
    private ReadThread rt;
    private ByteBuffer bb = ByteBuffer.allocate(1);

    private volatile Path socket;

    private ServerSocketChannel getServerSocketChannel() throws IOException {
        socket = Files.createTempDirectory(UnixSocketChannelReadWrite.class.getSimpleName()).resolve("sock");
        UnixDomainSocketAddress addr = UnixDomainSocketAddress.of(socket);
        ServerSocketChannel c = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        c.bind(addr);
        return c;
    }

    @Setup(Level.Trial)
    public void beforeRun() throws IOException {
        ssc = getServerSocketChannel();
        s1 = SocketChannel.open(ssc.getLocalAddress());
        s2 = ssc.accept();

        rt = new ReadThread(s2);
        rt.start();

        bb.put((byte) 47);
        bb.flip();
    }

    @TearDown(Level.Trial)
    public void afterRun() throws IOException, InterruptedException {
        s1.close();
        s2.close();
        ssc.close();
        Files.delete(socket);
        Files.delete(socket.getParent());
        rt.join();
    }

    @Benchmark
    public void test() throws IOException {
        s1.write(bb);
        bb.flip();
    }

    static class ReadThread extends Thread {
        private SocketChannel sc;

        public ReadThread(SocketChannel s2) {
            this.sc = s2;
        }

        public void run() {
            try {
                ByteBuffer bb = ByteBuffer.allocate(1);
                while (sc.read(bb) > 0) {
                    bb.flip();
                }
            } catch (ClosedChannelException ex) {
                // shutdown time
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}

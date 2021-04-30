/*
 * Copyright (c) 2021, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.openjdk.bench.java.nio.channels;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for Selector.select(Consumer) when there is one channel ready.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SelectOne {
    private Selector sel;
    private List<SocketChannel> clients;
    private List<SocketChannel> peers;

    private final int nready = 1;  // one channel ready for reading

    @Param({"1", "10", "100", "1000", "10000"})
    private int nchannels;  // number of registered channels

    @Setup
    public void setup() throws IOException {
        sel = Selector.open();
        clients = new ArrayList<SocketChannel>();
        peers = new ArrayList<SocketChannel>();

        ServerSocketChannel listener = ServerSocketChannel.open();
        listener.bind(new InetSocketAddress(0));
        SocketAddress remote = listener.getLocalAddress();

        for (int i = 0; i < nchannels; i++) {
            SocketChannel sc = SocketChannel.open(remote);
            sc.configureBlocking(false);
            sc.register(sel, SelectionKey.OP_READ);
            clients.add(sc);

            SocketChannel peer = listener.accept();
            peers.add(peer);
        }

        for (int i = nready - 1; i >= 0; i--) {
            SocketChannel peer = peers.get(i);
            peer.write(ByteBuffer.allocate(1));
        }
    }

    @TearDown
    public void teardown() throws IOException {
        for (SocketChannel sc: clients) {
            sc.close();
        }
        for (SocketChannel sc: peers) {
            sc.close();
        }
        if (sel != null) {
            sel.close();
        }
    }

    @Benchmark
    public void testSelectOne() throws IOException {
        int nselected = sel.select(k -> { });
        if (nselected != 1) {
            throw new RuntimeException();
        }
    }
}

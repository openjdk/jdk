/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.httpclient.test.lib.quic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;

/**
 * A {@code ConnectedBidiStream} represents a {@link
 * jdk.internal.net.http.quic.streams.QuicBidiStream}
 * which has a reader and writer task/loop started for it
 */
public class ConnectedBidiStream implements AutoCloseable {

    private final QuicBidiStream bidiStream;
    private final QuicStreamReader quicStreamReader;
    private final QuicStreamWriter quicStreamWriter;
    private final BlockingQueue<ByteBuffer> incomingData;
    private final Semaphore writeSemaphore = new Semaphore(1);
    private final OutputStream outputStream;
    private final QueueInputStream inputStream;
    private final SequentialScheduler readScheduler;
    private volatile boolean closed;

    ConnectedBidiStream(final QuicBidiStream bidiStream) {
        Objects.requireNonNull(bidiStream);
        this.bidiStream = bidiStream;
        incomingData = new ArrayBlockingQueue<>(1024, true);
        this.quicStreamReader = bidiStream.connectReader(
                readScheduler = SequentialScheduler.lockingScheduler(new ReaderLoop()));
        this.inputStream = new QueueInputStream(this.incomingData, QuicStreamReader.EOF, quicStreamReader);
        this.quicStreamWriter = bidiStream.connectWriter(
                SequentialScheduler.lockingScheduler(() -> {
                    System.out.println("Server writer task called");
                    writeSemaphore.release();
                }));
        this.outputStream = new OutStream(this.quicStreamWriter, writeSemaphore);
        // TODO: start the reader when inputStream() is called instead?
        this.quicStreamReader.start();
    }

    public InputStream inputStream() {
        return this.inputStream;
    }

    public OutputStream outputStream() {
        return this.outputStream;
    }

    public QuicBidiStream underlyingBidiStream() {
        return this.bidiStream;
    }

    @Override
    public void close() throws Exception {
        this.closed = true;
        // TODO: use runOrSchedule(executor)?
        this.readScheduler.runOrSchedule();
    }


    private final class ReaderLoop implements Runnable {

        private volatile boolean alreadyLogged;

        @Override
        public void run() {
            try {
                if (quicStreamReader == null) return;
                while (true) {
                    final var bb = quicStreamReader.poll();
                    if (closed) {
                        return;
                    }
                    if (bb == null) {
                        return;
                    }
                    incomingData.add(bb);
                    if (bb == QuicStreamReader.EOF) {
                        break;
                    }
                }
            } catch (Throwable e) {
                if (closed && e instanceof IOException) {
                    // the stream has been closed so we ignore any IOExceptions
                    return;
                }
                System.err.println("Error in " + getClass());
                e.printStackTrace();
                var in = inputStream;
                if (in != null) {
                    in.error(e);
                }
            }
        }
    }

}

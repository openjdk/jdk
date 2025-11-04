/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.net.http.http3.streams;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * A class that reads VL integers from a QUIC stream.
 * <p>
 * After constructing an instance of this class, the application
 * is can call {@link #readInt()} to read one VL integer off the stream.
 * When the read operation completes, the application can call {@code readInt}
 * again, or call {@link #stop()} to disconnect the reader.
 */
final class QuicStreamIntReader {
    private final SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(this::dispatch);
    private final QuicReceiverStream stream;
    private final QuicStreamReader reader;
    private final Logger debug;
    private CompletableFuture<Long> cf;
    private ByteBuffer vlongBuf; // accumulate bytes until stream type can be decoded

    /**
     * Creates a {@code QuicStreamIntReader} for the given stream.
     * @param stream a receiver stream with no connected reader
     * @param debug a logger
     */
    public QuicStreamIntReader(QuicReceiverStream stream, Logger debug) {
        this.stream = stream;
        this.reader = stream.connectReader(scheduler);
        this.debug = debug;
        debug.log("int reader created for stream " + stream.streamId());
    }

    // The read loop. Attempts to read a VL int, and completes the CF when done.
    private void dispatch() {
        if (cf == null) return; // not reading anything at the moment
        try {
            ByteBuffer buffer;
            while ((buffer = reader.peek()) != null) {
                if (buffer == QuicStreamReader.EOF) {
                    debug.log("stream %s EOF, cannot complete!",
                            stream.streamId());
                    CompletableFuture<Long> cf0;
                    synchronized (this) {
                        cf0 = cf;
                        cf = null;
                    }
                    cf0.complete(-1L);
                    return;
                }
                if (buffer.remaining() == 0) {
                    var polled = reader.poll();
                    assert buffer == polled;
                    continue;
                }
                if (vlongBuf == null) {
                    long vlong = VariableLengthEncoder.decode(buffer);
                    if (vlong >= 0) {
                        // happy case: we have enough bytes in the buffer
                        if (buffer.remaining() == 0) {
                            var polled = reader.poll();
                            assert buffer == polled;
                        }
                        CompletableFuture<Long> cf0;
                        synchronized (this) {
                            cf0 = cf;
                            cf = null;
                        }
                        cf0.complete(vlong);
                        return;
                    }
                    // we don't have enough bytes: start accumulating them
                    int vlongSize = VariableLengthEncoder.peekEncodedValueSize(buffer, buffer.position());
                    assert vlongSize > 0 && vlongSize <= VariableLengthEncoder.MAX_INTEGER_LENGTH
                            : vlongSize + " is out of bound for a variable integer size (should be in [1..8]";
                    assert buffer.remaining() < vlongSize;
                    vlongBuf = ByteBuffer.allocate(vlongSize);
                    vlongBuf.put(buffer);
                    assert buffer.remaining() == 0;
                    var polled = reader.poll();
                    assert polled == buffer;
                    // continue and wait for more
                } else {
                    // there wasn't enough bytes the first time around, accumulate
                    // missing bytes
                    int missing = vlongBuf.remaining();
                    int available = Math.min(missing, buffer.remaining());
                    for (int i = 0; i < available; i++) {
                        vlongBuf.put(buffer.get());
                    }
                    // if we have exhausted the buffer, poll it.
                    if (!buffer.hasRemaining()) {
                        var polled = reader.poll();
                        assert polled == buffer;
                    }
                    // if we have all bytes, we can proceed and decode the stream type
                    if (!vlongBuf.hasRemaining()) {
                        vlongBuf.flip();
                        long vlong = VariableLengthEncoder.decode(vlongBuf);
                        assert !vlongBuf.hasRemaining();
                        vlongBuf = null;
                        assert vlong >= 0;
                        CompletableFuture<Long> cf0;
                        synchronized (this) {
                            cf0 = cf;
                            cf = null;
                        }
                        cf0.complete(vlong);
                        return;
                    } // otherwise, wait for more
                }
            }
        } catch (Throwable throwable) {
            CompletableFuture<Long> cf0;
            synchronized (this) {
                cf0 = cf;
                cf = null;
            }
            cf0.completeExceptionally(throwable);
        }
    }

    /**
     * Stops and disconnects this reader. This operation must not be done when a read operation
     * is in progress. If cancelling a read operation is intended, use
     * {@link QuicReceiverStream#requestStopSending(long)}.
     * @throws IllegalStateException if a read operation is currently in progress.
     */
    public synchronized void stop() {
        if (cf != null) {
            // if a read is in progress, some bytes might have been read
            // off the stream already, and stopping the reader could corrupt the data.
            throw new IllegalStateException("Reading in progress");
        }
        if (!reader.connected()) return;
        stream.disconnectReader(reader);
        scheduler.stop();
    }

    /**
     * Starts a read operation to decode a single number.
     * @return a {@link CompletableFuture<Long>} that will be completed
     * with the decoded number, or -1 if the stream is terminated before
     * the complete number could be read, or an exception
     * if the stream is reset or decoding fails.
     * @throws IllegalStateException if the reader is stopped, or if a read
     * operation is already in progress
     */
    public synchronized CompletableFuture<Long> readInt() {
        if (cf != null) {
            throw new IllegalStateException("Read in progress");
        }
        if (!reader.connected()) {
            throw new IllegalStateException("Reader stopped");
        }
        var cf0 = cf = new MinimalFuture<>();
        reader.start();
        scheduler.runOrSchedule();
        return cf0;
    }
}

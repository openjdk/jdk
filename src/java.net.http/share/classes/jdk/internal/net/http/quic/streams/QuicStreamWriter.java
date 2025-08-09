/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.streams;

import java.io.IOException;
import java.nio.ByteBuffer;

import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.quic.frames.StreamFrame;
import jdk.internal.net.http.quic.streams.QuicSenderStream.SendingStreamState;

/**
 * An abstract class to model a writer plugged into
 * a QuicStream to which data can be written. The data
 * is wrapped in {@link StreamFrame}
 * before being written.
 */
public abstract class QuicStreamWriter {

    // The scheduler to invoke when flow credit
    // become available.
    final SequentialScheduler scheduler;

    /**
     * Creates a new instance of a QuicStreamWriter.
     * @param scheduler A sequential scheduler that will
     *                  push data into this writer.
     */
    public QuicStreamWriter(SequentialScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * {@return the sending state of the stream}
     *
     * @apiNote
     * This method returns the state of the {@link QuicSenderStream}
     * to which this writer is {@linkplain
     * QuicSenderStream#connectWriter(SequentialScheduler) connected}.
     *
     * @throws IllegalStateException if this writer is {@linkplain
     *    QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *    to its stream
     */
    public abstract SendingStreamState sendingState();

    /**
     * Pushes a ByteBuffer to be scheduled for writing on the stream.
     * The ByteBuffer will be wrapped in a StreamFrame before being
     * sent. Data that cannot be sent due to a lack of flow
     * credit will be buffered.
     *
     * @param buffer A byte buffer to schedule for writing
     * @param last   Whether that's the last data that will be sent
     *               through this stream.
     *
     * @throws IOException if the state of the stream isn't
     *    {@link SendingStreamState#READY} or {@link SendingStreamState#SEND}
     * @throws IllegalStateException if this writer is {@linkplain
     *    QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *    to its stream
     */
    public abstract void scheduleForWriting(ByteBuffer buffer, boolean last)
            throws IOException;

    /**
     * Queues a {@code ByteBuffer} on the writing queue for this stream.
     * The consumer will not be woken up. More data should be submitted
     * using {@link #scheduleForWriting(ByteBuffer, boolean)} in order
     * to wake the consumer.
     *
     * @apiNote
     * Use this method as a hint that more data will be
     * upcoming shortly that might be aggregated with
     * the data being queued in order to reduce the number
     * of packets that will be sent to the peer.
     * This is useful when a small number of bytes
     * need to be written to the stream before actual stream
     * data. Typically, this can be used for writing the
     * HTTP/3 stream type for a unidirectional HTTP/3 stream
     * before starting to send stream data.
     *
     * @param buffer A byte buffer to schedule for writing
     *
     * @throws IOException if the state of the stream isn't
     *    {@link SendingStreamState#READY} or {@link SendingStreamState#SEND}
     * @throws IllegalStateException if this writer is {@linkplain
     *    QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *    to its stream
     */
    public abstract void queueForWriting(ByteBuffer buffer)
            throws IOException;

    /**
     * Indicates how many bytes the writer is
     * prepared to received for sending.
     * When that value grows from 0, and if the queue has
     * no pending data, the {@code scheduler}
     * is triggered to elicit more calls to
     * {@link #scheduleForWriting(ByteBuffer,boolean)}.
     *
     * @apiNote This information is used to avoid
     * buffering too much data while waiting for flow
     * credit on the underlying stream. When flow credit
     * is available, the {@code scheduler} loop is
     * invoked to resume writing. The scheduler can then
     * call this method to figure out how much data to
     * request from upstream.
     *
     * @throws IllegalStateException if this writer is {@linkplain
     *    QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *    to its stream
     */
    public abstract long credit();

    /**
     * Abruptly resets the stream.
     *
     * @param errorCode the application error code
     *
     * @throws IllegalStateException if this writer is {@linkplain
     *    QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *    to its stream
     */
    public abstract void reset(long errorCode) throws IOException;

    /**
     * {@return the stream this writer is connected to, or {@code null}
     * if this writer isn't currently {@linkplain #connected() connected}}
     */
    public abstract QuicSenderStream stream();

    /**
     * {@return true if this writer is connected to its stream}
     * @see QuicSenderStream#connectWriter(SequentialScheduler)
     * @see QuicSenderStream#disconnectWriter(QuicStreamWriter)
     */
    public abstract boolean connected();

    /**
     * {@return true if STOP_SENDING was received}
     */
    public boolean stopSendingReceived() {
        return connected() ?  stream().stopSendingReceived() : false;
    }

}

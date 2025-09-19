/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Queue;

import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.quic.streams.QuicReceiverStream.ReceivingStreamState;

/**
 * An abstract class to model a reader plugged into
 * a QuicStream from which data can be read
 */
public abstract class QuicStreamReader {

    /**
     * A sentinel inserted into the queue after the FIN it has been received.
     */
    public static final ByteBuffer EOF = ByteBuffer.wrap(new byte[0]).asReadOnlyBuffer();

    // The scheduler to invoke when data becomes
    // available.
    final SequentialScheduler scheduler;

    /**
     * Creates a new instance of a QuicStreamReader.
     * The given scheduler will not be invoked until the reader
     * is {@linkplain #start() started}.
     *
     * @param scheduler A sequential scheduler that will
     *                  poll data out of this reader.
     */
    public QuicStreamReader(SequentialScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * {@return the receiving state of the stream}
     *
     * @apiNote
     * This method returns the state of the {@link QuicReceiverStream}
     * to which this writer is {@linkplain
     * QuicReceiverStream#connectReader(SequentialScheduler) connected}.
     *
     * @throws IllegalStateException if this reader is {@linkplain
     *    QuicReceiverStream#disconnectReader(QuicStreamReader) no longer connected}
     *    to its stream
     *
     */
    public abstract  ReceivingStreamState receivingState();

    /**
     * {@return the ByteBuffer at the head of the queue,
     *  or null if no data is available}. If the end of the stream is
     *  reached then {@link #EOF} is returned.
     *
     * @implSpec
     * This method behave just like {@link Queue#poll()}.
     *
     * @throws IOException if the stream was closed locally or
     *    reset by the peer
     * @throws IllegalStateException if this reader is {@linkplain
     *    QuicReceiverStream#disconnectReader(QuicStreamReader) no longer connected}
     *    to its stream
     */
    public abstract ByteBuffer poll() throws IOException;

    /**
     * {@return the ByteBuffer at the head of the queue,
     *  or null if no data is available}
     *
     * @implSpec
     * This method behave just like {@link Queue#peek()}.
     *
     * @throws IOException if the stream was reset by the peer
     * @throws IllegalStateException if this reader is {@linkplain
     *    QuicReceiverStream#disconnectReader(QuicStreamReader) no longer connected}
     *    to its stream
     */
    public abstract ByteBuffer peek() throws IOException;

    /**
     * {@return the stream this reader is connected to, or {@code null}
     *  if this reader is not currently {@linkplain #connected() connected}}
     */
    public abstract QuicReceiverStream stream();


    /**
     * {@return true if this reader is connected to its stream}
     * @see QuicReceiverStream#connectReader(SequentialScheduler)
     * @see QuicReceiverStream#disconnectReader(QuicStreamReader)
     */
    public abstract boolean connected();

    /**
     * {@return true if this reader has been {@linkplain #start() started}}
     */
    public abstract boolean started();

    /**
     * Starts the reader. The {@linkplain
     * QuicReceiverStream#connectReader(SequentialScheduler) scheduler}
     * will not be invoked until the reader is {@linkplain #start() started}.
     */
    public abstract void start();

    /**
     * {@return whether reset was received or read by this reader}
     */
    public boolean isReset() {
        return stream().receivingState().isReset();
    }
}

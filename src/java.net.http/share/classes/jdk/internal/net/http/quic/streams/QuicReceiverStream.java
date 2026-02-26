/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.common.SequentialScheduler;

/**
 * An interface that represents the receiving part of a stream.
 * <p> From RFC 9000:
 * <quote>
 * On the receiving part of a stream, an application protocol can:
 * <ul>
 *   <li> read data; and </li>
 *   <li> abort reading of the stream and request closure, possibly
 *        resulting in a STOP_SENDING frame (Section 19.5). </li>
 * </ul>
 * </quote>
 */
public non-sealed interface QuicReceiverStream extends QuicStream {

    /**
     * An enum that models the state of the receiving part of a stream.
     */
    enum ReceivingStreamState implements QuicStream.StreamState {
        /**
         * The initial state for the receiving part of a
         * stream is "Recv".
         * <p>
         * In the "Recv" state, the endpoint receives STREAM
         * and STREAM_DATA_BLOCKED frames. Incoming data is buffered
         * and can be reassembled into the correct order for delivery
         * to the application. As data is consumed by the application
         * and buffer space becomes available, the endpoint sends
         * MAX_STREAM_DATA frames to allow the peer to send more data.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RECV,
        /**
         * When a STREAM frame with a FIN bit is received, the final size of
         * the stream is known; see Section 4.5. The receiving part of the
         * stream then enters the "Size Known" state. In this state, the
         * endpoint no longer needs to send MAX_STREAM_DATA frames; it only
         * receives any retransmissions of stream data.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        SIZE_KNOWN,
        /**
         * Once all data for the stream has been received, the receiving part
         * enters the "Data Recvd" state. This might happen as a result of
         * receiving the same STREAM frame that causes the transition to
         * "Size Known". After all data has been received, any STREAM or
         * STREAM_DATA_BLOCKED frames for the stream can be discarded.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_RECVD,
        /**
         * The "Data Recvd" state persists until stream data has been delivered
         * to the application. Once stream data has been delivered, the stream
         * enters the "Data Read" state, which is a terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_READ,
        /**
         * Receiving a RESET_STREAM frame in the "Recv" or "Size Known" state
         * causes the stream to enter the "Reset Recvd" state. This might
         * cause the delivery of stream data to the application to be
         * interrupted.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_RECVD,
        /**
         * Once the application receives the signal indicating that the
         * stream was reset, the receiving part of the stream transitions to
         * the "Reset Read" state, which is a terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_READ;

        @Override
        public boolean isTerminal() {
            return this == DATA_READ || this == RESET_READ;
        }

        /**
         * {@return true if this state indicates that the stream has been reset by the sender}
         */
        public boolean isReset() { return this == RESET_RECVD || this == RESET_READ; }
    }

    /**
     * {@return the receiving state of the stream}
     */
    ReceivingStreamState receivingState();

    /**
     * Connects an {@linkplain QuicStreamReader#started() unstarted} reader
     * to the receiver end of this stream.
     * @param scheduler A sequential scheduler that will be invoked
     *                  when the reader is started and new data becomes available for reading
     * @return a {@code QuicStreamReader} to read data from this
     *         stream.
     * @throws IllegalStateException if a reader is already connected.
     */
    QuicStreamReader connectReader(SequentialScheduler scheduler);

    /**
     * Disconnect the reader, so that a new reader can be connected.
     *
     * @apiNote
     * This can be useful for handing the stream over after having read
     * or peeked at some bytes.
     *
     * @param reader the reader to be disconnected
     * @throws IllegalStateException if the given reader is not currently
     *         connected to the stream
     */
    void disconnectReader(QuicStreamReader reader);

    /**
     * Cancels the reading side of this stream by sending
     * a STOP_SENDING frame.
     *
     * @param errorCode the application error code
     *
     */
    void requestStopSending(long errorCode);

    /**
     * {@return the amount of data that has been received so far}
     * @apiNote This may include data that has not been read by the
     * application yet, but does not count any data that may have
     * been received twice.
     */
    long dataReceived();

    /**
     * {@return the maximum amount of data that can be received on
     * this stream}
     *
     * @apiNote This corresponds to the maximum amount of data that
     * the peer has been allowed to send.
     */
    long maxStreamData();

    /**
     * {@return the error code for this stream, or {@code -1}}
     */
    long rcvErrorCode();

    default boolean isStopSendingRequested() { return false; }

    @Override
    default boolean hasError() {
        return rcvErrorCode() >= 0;
    }
}

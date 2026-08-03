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

import java.io.IOException;

import jdk.internal.net.http.common.SequentialScheduler;

/**
 * An interface that represents the sending part of a stream.
 * <p> From RFC 9000:
 * <quote>
 * On the sending part of a stream, an application protocol can:
 * <ul>
 *   <li> write data, understanding when stream flow control credit
 *        (Section 4.1) has successfully been reserved to send the
 *        written data; </li>
 *   <li> end the stream (clean termination), resulting in a STREAM frame
 *        (Section 19.8) with the FIN bit set; and </li>
 *   <li> reset the stream (abrupt termination), resulting in a RESET_STREAM
 *        frame (Section 19.4) if the stream was not already in a terminal
 *        state. </li>
 * </ul>
 * </quote>
 */
public non-sealed interface QuicSenderStream extends QuicStream {

    /**
     * An enum that models the state of the sending part of a stream.
     */
    enum SendingStreamState implements QuicStream.StreamState {
        /**
         * The "Ready" state represents a newly created stream that is able
         * to accept data from the application. Stream data might be
         * buffered in this state in preparation for sending.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        READY,
        /**
         * In the "Send" state, an endpoint transmits -- and retransmits as
         * necessary -- stream data in STREAM frames. The endpoint respects
         * the flow control limits set by its peer and continues to accept
         * and process MAX_STREAM_DATA frames. An endpoint in the "Send" state
         * generates STREAM_DATA_BLOCKED frames if it is blocked from sending
         * by stream flow control limits (Section 4.1).
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        SEND,
        /**
         * After the application indicates that all stream data has been sent
         * and a STREAM frame containing the FIN bit is sent, the sending part
         * of the stream enters the "Data Sent" state. From this state, the
         * endpoint only retransmits stream data as necessary. The endpoint
         * does not need to check flow control limits or send STREAM_DATA_BLOCKED
         * frames for a stream in this state. MAX_STREAM_DATA frames might be received
         * until the peer receives the final stream offset. The endpoint can safely
         * ignore any MAX_STREAM_DATA frames it receives from its peer for a
         * stream in this state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_SENT,
        /**
         * From any state that is one of "Ready", "Send", or "Data Sent", an
         * application can signal that it wishes to abandon transmission of
         * stream data. Alternatively, an endpoint might receive a STOP_SENDING
         * frame from its peer. In either case, the endpoint sends a RESET_STREAM
         * frame, which causes the stream to enter the "Reset Sent" state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_SENT,
        /**
         * Once all stream data has been successfully acknowledged, the sending
         * part of the stream enters the "Data Recvd" state, which is a
         * terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_RECVD,
        /**
         * Once a packet containing a RESET_STREAM has been acknowledged, the
         * sending part of the stream enters the "Reset Recvd" state, which
         * is a terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_RECVD;

        @Override
        public boolean isTerminal() {
            return this == DATA_RECVD || this == RESET_RECVD;
        }

        /**
         * {@return true if a stream in this state can be used for sending, that is,
         *  if this state is either {@link #READY} or {@link #SEND}}.
         */
        public boolean isSending() { return this == READY || this == SEND; }

        /**
         * {@return true if this state indicates that the stream has been reset by the sender}
         */
        public boolean isReset() { return this == RESET_SENT || this == RESET_RECVD; }
    }

    /**
     * {@return the sending state of the stream}
     */
    SendingStreamState sendingState();

    /**
     * Connects a writer to the sending end of this stream.
     * @param scheduler A sequential scheduler that will
     *                  push data on the returned  {@linkplain
     *                  QuicStreamWriter#QuicStreamWriter(SequentialScheduler)
     *                  writer}.
     * @return a {@code QuicStreamWriter} to write data to this
     *         stream.
     * @throws IllegalStateException if a writer is already connected.
     */
    QuicStreamWriter connectWriter(SequentialScheduler scheduler);

    /**
     * Disconnect the writer, so that a new writer can be connected.
     *
     * @apiNote
     * This can be useful for handing the stream over after having written
     * some bytes.
     *
     * @param writer the writer to be disconnected
     * @throws IllegalStateException if the given writer is not currently
     *         connected to the stream
     */
    public void disconnectWriter(QuicStreamWriter writer);

    /**
     * Abruptly closes the writing side of a stream by sending
     * a RESET_STREAM frame.
     * @param errorCode the application error code
     */
    void reset(long errorCode) throws IOException;

    /**
     * {@return the amount of data that has been sent}
     * @apiNote
     * This may include data that has not been acknowledged.
     */
    long dataSent();

    /**
     * {@return the error code for this stream, or {@code -1}}
     */
    long sndErrorCode();

    /**
     * {@return true if STOP_SENDING was received}
     */
    boolean stopSendingReceived();

    @Override
    default boolean hasError() {
        return sndErrorCode() >= 0;
    }


}

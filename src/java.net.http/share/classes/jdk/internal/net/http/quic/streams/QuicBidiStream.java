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

/**
 * An interface that represents a bidirectional stream.
 * A bidirectional stream implements both {@link QuicSenderStream}
 * and {@link QuicReceiverStream}.
 */
public non-sealed interface QuicBidiStream extends QuicStream, QuicReceiverStream, QuicSenderStream {

    /**
     * The state of a bidirectional stream can be obtained by combining
     * the state of its sending part and receiving part.
     * <quote>
     *    A bidirectional stream is composed of sending and receiving
     *    parts. Implementations can represent states of the bidirectional
     *    stream as composites of sending and receiving stream states.
     *    The simplest model presents the stream as "open" when either
     *    sending or receiving parts are in a non-terminal state and
     *    "closed" when both sending and receiving streams are in
     *    terminal states.
     * </quote>
     * See RFC 9000, [Section 3.4]
     * (https://www.rfc-editor.org/rfc/rfc9000#name-bidirectional-stream-states)
     */
    enum BidiStreamState implements QuicStream.StreamState {
        /**
         * A bidirectional stream is considered "idle" if no
         * data has been sent or received on that stream.
         */
        IDLE,
        /**
         * A bidirectional stream is considered "open" until all data
         * has been received, or all data has been sent, and no reset
         * has been sent or received.
         */
        OPENED,
        /**
         * A bidirectional stream is considered locally half closed
         * if the sending part is locally closed:
         * all data has been sent and acknowledged, or a reset has
         * been sent, but the receiving part is still receiving.
         */
        HALF_CLOSED_LOCAL,
        /**
         * A bidirectional stream is considered remotely half closed
         * if the receiving part is closed:
         * all data has been read or received on the receiving part,
         * or reset has been read or received on the receiving part, but
         * the sending part is still sending.
         */
        HALF_CLOSED_REMOTE,
        /**
         * A bidirectional stream is considered closed when both parts
         * have been reset or all data has been sent and acknowledged
         * and all data has been received.
         */
        CLOSED;

        /**
         * @inheritDoc
         * @apiNote
         * A bidirectional stream may be considered closed (which is a terminal state),
         * even if the sending or receiving part of a stream haven't reached a terminal
         * state. Typically, if the sending part has sent a RESET frame, the stream
         * may be considered closed even if the acknowledgement hasn't been received
         * yet.
         */
        @Override
        public boolean isTerminal() {
            return this == CLOSED;
        }
    }

    /**
     * {@return a composed simplified state computed from the state of
     *  the receiving part and sending part of the stream}
     *  <p>
     *  See RFC 9000, [Section 3.4]
     *  (https://www.rfc-editor.org/rfc/rfc9000#name-bidirectional-stream-states)
     */
    default BidiStreamState getBidiStreamState() {
        return switch (sendingState()) {
            case READY -> switch (receivingState()) {
                case RECV -> dataReceived() == 0
                        ? BidiStreamState.IDLE
                        : BidiStreamState.OPENED;
                case SIZE_KNOWN -> BidiStreamState.OPENED;
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ
                        -> BidiStreamState.HALF_CLOSED_REMOTE;
            };
            case SEND, DATA_SENT -> switch (receivingState()) {
                case RECV, SIZE_KNOWN -> BidiStreamState.OPENED;
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ
                        -> BidiStreamState.HALF_CLOSED_REMOTE;
            };
            case DATA_RECVD, RESET_RECVD, RESET_SENT -> switch (receivingState()) {
                case RECV, SIZE_KNOWN -> BidiStreamState.HALF_CLOSED_LOCAL;
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ
                        -> BidiStreamState.CLOSED;
            };
        };
    }

    @Override
    default StreamState state() { return getBidiStreamState(); }

    @Override
    default boolean hasError() {
        return rcvErrorCode() >= 0 || sndErrorCode() >= 0;
    }
}

/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.frames;

import java.nio.ByteBuffer;

import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.http.quic.VariableLengthEncoder;

/**
 * A QUIC Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public abstract sealed class QuicFrame permits
        AckFrame,
        DataBlockedFrame,
        ConnectionCloseFrame, CryptoFrame,
        HandshakeDoneFrame,
        MaxDataFrame, MaxStreamDataFrame, MaxStreamsFrame,
        NewConnectionIDFrame, NewTokenFrame,
        PaddingFrame, PathChallengeFrame, PathResponseFrame, PingFrame,
        ResetStreamFrame, RetireConnectionIDFrame,
        StreamsBlockedFrame, StreamDataBlockedFrame, StreamFrame, StopSendingFrame {

    public static final long MAX_VL_INTEGER = (1L << 62) - 1;
    /**
     * Frame types
     */
    public static final int PADDING=0x00;
    public static final int PING=0x01;
    public static final int ACK=0x02;
    public static final int RESET_STREAM=0x04;
    public static final int STOP_SENDING=0x05;
    public static final int CRYPTO=0x06;
    public static final int NEW_TOKEN=0x07;
    public static final int STREAM=0x08;
    public static final int MAX_DATA=0x10;
    public static final int MAX_STREAM_DATA=0x11;
    public static final int MAX_STREAMS=0x12;
    public static final int DATA_BLOCKED=0x14;
    public static final int STREAM_DATA_BLOCKED=0x15;
    public static final int STREAMS_BLOCKED=0x16;
    public static final int NEW_CONNECTION_ID=0x18;
    public static final int RETIRE_CONNECTION_ID=0x19;
    public static final int PATH_CHALLENGE=0x1a;
    public static final int PATH_RESPONSE=0x1b;
    public static final int CONNECTION_CLOSE=0x1c;
    public static final int HANDSHAKE_DONE=0x1e;
    private static final int MAX_KNOWN_FRAME_TYPE = HANDSHAKE_DONE;
    private final int frameType;

    /**
     * Concrete Frame types normally have two constructors which call this
     *
     * XXXFrame(ByteBuffer, int firstByte) which is called for incoming frames
     * after reading the first byte to determine the type. The firstByte is also
     * supplied to the constructor because it can contain additional state information
     *
     * XXXFrame(...) which is called to instantiate outgoing frames
     * @param type the first byte of the frame, which encodes the frame type.
     */
    QuicFrame(int type) {
        frameType = type;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    /**
     * decode given ByteBuffer and return a QUICFrame
     */
    public static QuicFrame decode(ByteBuffer buffer) throws QuicTransportException {
        long frameTypeLong = VariableLengthEncoder.decode(buffer);
        if (frameTypeLong < 0) {
            throw new QuicTransportException("Error decoding frame type",
                    null, 0, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        if (frameTypeLong > Integer.MAX_VALUE) {
            throw new QuicTransportException("Unrecognized frame",
                    null, frameTypeLong, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        int frameType = (int)frameTypeLong;
        var frame = switch (maskType(frameType)) {
            case ACK -> new AckFrame(buffer, frameType);
            case STREAM -> new StreamFrame(buffer, frameType);
            case RESET_STREAM -> new ResetStreamFrame(buffer, frameType);
            case PADDING -> new PaddingFrame(buffer, frameType);
            case PING -> new PingFrame(buffer, frameType);
            case STOP_SENDING -> new StopSendingFrame(buffer, frameType);
            case CRYPTO -> new CryptoFrame(buffer, frameType);
            case NEW_TOKEN -> new NewTokenFrame(buffer, frameType);
            case DATA_BLOCKED -> new DataBlockedFrame(buffer, frameType);
            case MAX_DATA -> new MaxDataFrame(buffer, frameType);
            case MAX_STREAMS -> new MaxStreamsFrame(buffer, frameType);
            case MAX_STREAM_DATA -> new MaxStreamDataFrame(buffer, frameType);
            case STREAM_DATA_BLOCKED -> new StreamDataBlockedFrame(buffer, frameType);
            case STREAMS_BLOCKED -> new StreamsBlockedFrame(buffer, frameType);
            case NEW_CONNECTION_ID -> new NewConnectionIDFrame(buffer, frameType);
            case RETIRE_CONNECTION_ID -> new RetireConnectionIDFrame(buffer, frameType);
            case PATH_CHALLENGE -> new PathChallengeFrame(buffer, frameType);
            case PATH_RESPONSE -> new PathResponseFrame(buffer, frameType);
            case CONNECTION_CLOSE -> new ConnectionCloseFrame(buffer, frameType);
            case HANDSHAKE_DONE -> new HandshakeDoneFrame(buffer, frameType);
            default -> throw new QuicTransportException("Unrecognized frame",
                    null, frameType, QuicTransportErrors.FRAME_ENCODING_ERROR);
        };
        assert frameClassOf(maskType(frameType)) == frame.getClass();
        assert frameTypeOf(frame.getClass()) == maskType(frameType);
        assert frame.getTypeField() == frameType : "frame type mismatch: "
                + frameType + "!=" + frame.getTypeField()
                + " for frame: " + frame;
        return frame;
    }

    public static Class<? extends QuicFrame> frameClassOf(int frameType) {
        return switch (maskType(frameType)) {
            case ACK -> AckFrame.class;
            case STREAM -> StreamFrame.class;
            case RESET_STREAM -> ResetStreamFrame.class;
            case PADDING -> PaddingFrame.class;
            case PING -> PingFrame.class;
            case STOP_SENDING -> StopSendingFrame.class;
            case CRYPTO -> CryptoFrame.class;
            case NEW_TOKEN -> NewTokenFrame.class;
            case DATA_BLOCKED -> DataBlockedFrame.class;
            case MAX_DATA -> MaxDataFrame.class;
            case MAX_STREAMS -> MaxStreamsFrame.class;
            case MAX_STREAM_DATA -> MaxStreamDataFrame.class;
            case STREAM_DATA_BLOCKED -> StreamDataBlockedFrame.class;
            case STREAMS_BLOCKED -> StreamsBlockedFrame.class;
            case NEW_CONNECTION_ID -> NewConnectionIDFrame.class;
            case RETIRE_CONNECTION_ID -> RetireConnectionIDFrame.class;
            case PATH_CHALLENGE -> PathChallengeFrame.class;
            case PATH_RESPONSE -> PathResponseFrame.class;
            case CONNECTION_CLOSE -> ConnectionCloseFrame.class;
            case HANDSHAKE_DONE -> HandshakeDoneFrame.class;
            default -> throw new IllegalArgumentException("Unrecognised frame");
        };
    }

    public static int frameTypeOf(Class<? extends QuicFrame> frameClass) {
        // we don't have class pattern matching yet - so switch
        // on the class name instead
        return switch (frameClass.getSimpleName()) {
            case "AckFrame" -> ACK;
            case "StreamFrame" -> STREAM;
            case "ResetStreamFrame" -> RESET_STREAM;
            case "PaddingFrame" -> PADDING;
            case "PingFrame" -> PING;
            case "StopSendingFrame" -> STOP_SENDING;
            case "CryptoFrame" -> CRYPTO;
            case "NewTokenFrame" -> NEW_TOKEN;
            case "DataBlockedFrame" -> DATA_BLOCKED;
            case "MaxDataFrame" -> MAX_DATA;
            case "MaxStreamsFrame" -> MAX_STREAMS;
            case "MaxStreamDataFrame" -> MAX_STREAM_DATA;
            case "StreamDataBlockedFrame" -> STREAM_DATA_BLOCKED;
            case "StreamsBlockedFrame" -> STREAMS_BLOCKED;
            case "NewConnectionIDFrame" -> NEW_CONNECTION_ID;
            case "RetireConnectionIDFrame" -> RETIRE_CONNECTION_ID;
            case "PathChallengeFrame" -> PATH_CHALLENGE;
            case "PathResponseFrame" -> PATH_RESPONSE;
            case "ConnectionCloseFrame" -> CONNECTION_CLOSE;
            case "HandshakeDoneFrame" -> HANDSHAKE_DONE;
            default -> throw new IllegalArgumentException("Unrecognised frame");
        };
    }

    /**
     * Writes src to dest, preserving position in src
     */
    protected static void putByteBuffer(ByteBuffer dest, ByteBuffer src) {
        dest.put(src.asReadOnlyBuffer());
    }

    /**
     * Throws a QuicTransportException if the given buffer does not have enough bytes
     * to finish decoding the frame
     *
     * @param buffer source buffer
     * @param expected minimum number of bytes required
     * @param type frame type to include in exception
     * @throws QuicTransportException if the buffer is shorter than {@code expected}
     */
    protected static void validateRemainingLength(ByteBuffer buffer, int expected, long type)
            throws QuicTransportException
    {
       if (buffer.remaining() < expected) {
           throw new QuicTransportException("Error decoding frame",
                   null, type, QuicTransportErrors.FRAME_ENCODING_ERROR);
       }
    }

    /**
     * depending on the frame type, additional bits can be encoded
     * in frameType(). This masks them out to return a unique value
     * for each frame type.
     */
    private static int maskType(int type) {
        if (type >= ACK && type < RESET_STREAM)
            return ACK;
        if (type >= STREAM && type < MAX_DATA)
            return STREAM;
        if (type >= MAX_STREAMS && type < DATA_BLOCKED)
            return MAX_STREAMS;
        if (type >= STREAMS_BLOCKED && type < NEW_CONNECTION_ID)
            return STREAMS_BLOCKED;
        if (type >= CONNECTION_CLOSE && type < HANDSHAKE_DONE)
            return CONNECTION_CLOSE;
        // all others are unique
        return type;
    }

    /**
     * {@return true if this frame is <em>ACK-eliciting</em>}
     * A frame is <em>ACK-eliciting</em> if it is anything
     * other than {@link QuicFrame#ACK},
     * {@link QuicFrame#PADDING} or
     * {@link QuicFrame#CONNECTION_CLOSE}
     * (or its variant).
     */
    public boolean isAckEliciting() { return true; }

    /**
     * {@return the minimum number of bytes needed to encode this frame}
     */
    public abstract int size();

    protected final long decodeVLField(ByteBuffer buffer, String name) throws QuicTransportException {
        long v = VariableLengthEncoder.decode(buffer);
        if (v < 0) {
            throw new QuicTransportException("Error decoding field: " + name,
                    null, getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        return v;
    }

    protected final int decodeVLFieldAsInt(ByteBuffer buffer, String name) throws QuicTransportException {
        long l = decodeVLField(buffer, name);
        int intval = (int)l;
        if (((long)intval) != l) {
            throw new QuicTransportException(name + ":field too long",
                    null, getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        return intval;
    }

    protected static int requireVLRange(int val, String message) {
        if (val < 0) {
            throw new IllegalArgumentException(message + " " + val + " not in range");
        }
        return val;
    }

    protected static long requireVLRange(long val, String fieldName) {
        if (val < 0 || val > MAX_VL_INTEGER) {
            throw new IllegalArgumentException(
                    String.format("%s not in VL range: %s", fieldName, val));
        }
        return val;
    }

    protected static void encodeVLField(ByteBuffer buffer, long val, String name) {
        try {
            VariableLengthEncoder.encode(buffer, val);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error encoding " + name, e);
        }
    }

    protected static int getVLFieldLengthFor(long val) {
        return VariableLengthEncoder.getEncodedSize(val);
    }

    /**
     * The type of this frame, ie. one of values above, which means it
     * excludes the additional information that is encoded into the first field
     * of some QUIC frames. That additional info has to be maintained by the sub
     * class and used by its encode() method to generate the first field for outgoing frames.
     *
     * see maskType() below
     */
    public int frameType() {
        return frameType;
    }

    /**
     * Encode this QUIC Frame into given ByteBuffer
     */
    public abstract void encode(ByteBuffer buffer);

    /**
     * {@return the type field that was / should be encoded}
     * This is the {@linkplain #frameType() frame type} with
     * possibly some additional bits set, depending on the
     * frame.
     * @implSpec
     * The default implementation of this method is to return
     * {@link #frameType()}.
     */
    public long getTypeField() { return frameType(); }

    /**
     * Tells whether this particular frame is valid in the given
     * packet type.
     *
     * <p> From <a href="https://www.rfc-editor.org/rfc/rfc9000#section-12.5">
     *     RFC 9000, section 12.5. Frames and Number Spaces:</a>
     * <blockquote>
     * Some frames are prohibited in different packet number space
     * The rules here generalize those of TLS, in that frames associated
     * with establishing the connection can usually appear in packets
     * in any packet number space, whereas those associated with transferring
     * data can only appear in the application data packet number space:
     *
     * <ul>
     *   <li> PADDING, PING, and CRYPTO frames MAY appear in any packet number
     *        space.</li>
     *   <li> CONNECTION_CLOSE frames signaling errors at the QUIC layer (type 0x1c)
     *        MAY appear in any packet number space.</li>
     *   <li> CONNECTION_CLOSE frames signaling application errors (type 0x1d)
     *        MUST only appear in the application data packet number space.
     *   <li> ACK frames MAY appear in any packet number space but can only
     *        acknowledge packets that appeared in that packet number space.
     *        However, as noted below, 0-RTT packets cannot contain ACK frames.</li>
     *   <li> All other frame types MUST only be sent in the application data
     *        packet number space.</li>
     * </ul>
     *
     * Note that it is not possible to send the following frames in 0-RTT
     * packets for various reasons: ACK, CRYPTO, HANDSHAKE_DONE, NEW_TOKEN,
     * PATH_RESPONSE, and RETIRE_CONNECTION_ID. A server MAY treat receipt
     * of these frames in 0-RTT packets as a connection error of
     * type PROTOCOL_VIOLATION.
     * </blockquote>
     *
     * @param packetType the packet type
     * @return true if the frame can be embedded in a packet of that type
     */
    public boolean isValidIn(QuicPacket.PacketType packetType) {
        return switch (frameType) {
            case PADDING, PING -> true;
            case ACK, CRYPTO -> switch (packetType) {
                case VERSIONS, ZERORTT -> false;
                default -> true;
            };
            case CONNECTION_CLOSE -> {
                if ((getTypeField() & 0x1D) == 0x1C) yield true;
                yield QuicPacket.PacketNumberSpace.of(packetType) == QuicPacket.PacketNumberSpace.APPLICATION;
            }
            case HANDSHAKE_DONE, NEW_TOKEN, PATH_RESPONSE,
                    RETIRE_CONNECTION_ID -> switch (packetType) {
                case ZERORTT -> false;
                default -> QuicPacket.PacketNumberSpace.of(packetType) == QuicPacket.PacketNumberSpace.APPLICATION;
            };
            default -> QuicPacket.PacketNumberSpace.of(packetType) == QuicPacket.PacketNumberSpace.APPLICATION;
        };
    }
}

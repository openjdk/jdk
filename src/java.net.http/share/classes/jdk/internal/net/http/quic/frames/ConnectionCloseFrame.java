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

import jdk.internal.net.quic.QuicTransportException;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import jdk.internal.net.quic.QuicTransportErrors;

/**
 * A CONNECTION_CLOSE Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class ConnectionCloseFrame extends QuicFrame {

    /**
     * This variant indicates an error originating from the higher
     * level protocol, for instance, HTTP/3.
     */
    public static final int CONNECTION_CLOSE_VARIANT = 0x1d;
    private final long errorCode;
    private final long errorFrameType;
    private final boolean variant;
    private final byte[] reason;
    private String cachedToString;
    private String cachedReason;

    /**
     * An immutable ConnectionCloseFrame of type 0x1c with no reason phrase
     * and an error of type APPLICATION_ERROR.
     * @apiNote
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#section-10.2.3">
     *     RFC 9000 - section 10.2.3</a>:
     * <blockquote>
     * A CONNECTION_CLOSE of type 0x1d MUST be replaced by a CONNECTION_CLOSE
     * of type 0x1c when sending the frame in Initial or Handshake packets.
     * Otherwise, information about the application state might be revealed.
     * Endpoints MUST clear the value of the Reason Phrase field and SHOULD
     * use the APPLICATION_ERROR code when converting to a CONNECTION_CLOSE
     * of type 0x1c.
     * </blockquote>
     */
    public static final ConnectionCloseFrame APPLICATION_ERROR =
            new ConnectionCloseFrame(QuicTransportErrors.APPLICATION_ERROR.code(), 0,"");

    /**
     * Incoming CONNECTION_CLOSE frame returned by QuicFrame.decode()
     *
     * @param buffer
     * @param type
     * @throws QuicTransportException if the frame was malformed
     */
    ConnectionCloseFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(CONNECTION_CLOSE);
        errorCode = decodeVLField(buffer, "errorCode");
        if (type == CONNECTION_CLOSE) {
            variant = false;
            errorFrameType = decodeVLField(buffer, "errorFrameType");
        } else {
            assert type == CONNECTION_CLOSE_VARIANT;
            errorFrameType = -1;
            variant = true;
        }
        int reasonLength = decodeVLFieldAsInt(buffer, "reasonLength");
        validateRemainingLength(buffer, reasonLength, type);
        reason = new byte[reasonLength];
        buffer.get(reason, 0, reasonLength);
    }

    /**
     * Outgoing CONNECTION_CLOSE frame (variant with errorFrameType - 0x1c).
     * This indicates a {@linkplain jdk.internal.net.quic.QuicTransportErrors
     * quic transport error}.
     */
    public ConnectionCloseFrame(long errorCode, long errorFrameType, String reason) {
        super(CONNECTION_CLOSE);
        this.errorCode = requireVLRange(errorCode, "errorCode");
        this.errorFrameType = requireVLRange(errorFrameType, "errorFrameType");
        this.variant = false;
        this.cachedReason = reason;
        this.reason = getReasonBytes(reason);
    }

    /**
     * Outgoing CONNECTION_CLOSE frame (variant without errorFrameType).
     * This indicates an error originating from the higher level protocol,
     * for instance {@linkplain jdk.internal.net.http.http3.Http3Error HTTP/3}.
     */
    public ConnectionCloseFrame(long errorCode, String reason) {
        super(CONNECTION_CLOSE);
        this.errorCode = requireVLRange(errorCode, "errorCode");
        this.errorFrameType = -1;
        this.variant = true;
        this.cachedReason = reason;
        this.reason = getReasonBytes(reason);
    }

    private static byte[] getReasonBytes(String reason) {
        return reason != null ?
                reason.getBytes(StandardCharsets.UTF_8) :
                new byte[0];
    }

    /**
     * {@return a ConnectionCloseFrame suitable for inclusion in
     *   an Initial or Handshake packet}
     */
    public ConnectionCloseFrame clearApplicationState() {
        return this.variant ? APPLICATION_ERROR : this;
    }

    @Override
    public long getTypeField() {
        return variant ? CONNECTION_CLOSE_VARIANT : CONNECTION_CLOSE;
    }

    @Override
    public boolean isAckEliciting() {
        return false;
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, getTypeField(), "type");
        encodeVLField(buffer, errorCode, "errorCode");
        if (!variant) {
            encodeVLField(buffer, errorFrameType, "errorFrameType");
        }
        encodeVLField(buffer, reason.length, "reasonLength");
        if (reason.length > 0) {
            buffer.put(reason);
        }
        assert buffer.position() - pos == size();
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(getTypeField())
                + getVLFieldLengthFor(errorCode)
                + (variant ? 0 : getVLFieldLengthFor(errorFrameType))
                + getVLFieldLengthFor(reason.length)
                + reason.length;
    }

    public long errorCode() {
        return errorCode;
    }

    public long errorFrameType() {
        return errorFrameType;
    }

    public boolean variant() {
        return variant;
    }

    public boolean isQuicTransportCode() {
        return !variant;
    }

    public boolean isApplicationCode() {
        return variant;
    }

    public byte[] reason() {
        return reason;
    }

    public String reasonString() {
        if (cachedReason != null) return cachedReason;
        if (reason == null) return null;
        if (reason.length == 0) return "";
        return cachedReason = new String(reason, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        if (cachedToString == null) {
            final StringBuilder sb = new StringBuilder("ConnectionCloseFrame[type=0x");
            final long type = getTypeField();
            sb.append(Long.toHexString(type))
                    .append(", errorCode=0x").append(Long.toHexString(errorCode));
            // CRYPTO_ERROR codes ranging 0x0100-0x01ff
            if (type == 0x1c) {
                if (errorCode >= 0x0100 && errorCode <= 0x01ff) {
                    // this represents a CRYPTO_ERROR which as per RFC-9001, section 4.8:
                    // A TLS alert is converted into a QUIC connection error. The AlertDescription
                    // value is added to 0x0100 to produce a QUIC error code from the range reserved for
                    // CRYPTO_ERROR; ... The resulting value is sent in a QUIC CONNECTION_CLOSE
                    // frame of type 0x1c

                    // find the tls alert code from the error code, by substracting 0x0100 from
                    // the error code
                    sb.append(", tlsAlertDescription=").append(errorCode - 0x0100);
                }
                sb.append(", errorFrameType=0x").append(Long.toHexString(errorFrameType));
            }
            if (cachedReason == null) {
                cachedReason = new String(reason, StandardCharsets.UTF_8);
            }
            sb.append(", reason=").append(cachedReason).append("]");

            cachedToString = sb.toString();
        }
        return cachedToString;
    }
}

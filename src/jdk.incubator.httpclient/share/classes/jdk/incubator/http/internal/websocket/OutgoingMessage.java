/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.websocket;

import jdk.incubator.http.internal.websocket.Frame.Opcode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.security.SecureRandom;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static jdk.incubator.http.internal.common.Utils.EMPTY_BYTEBUFFER;
import static jdk.incubator.http.internal.websocket.Frame.MAX_HEADER_SIZE_BYTES;
import static jdk.incubator.http.internal.websocket.Frame.Opcode.BINARY;
import static jdk.incubator.http.internal.websocket.Frame.Opcode.CLOSE;
import static jdk.incubator.http.internal.websocket.Frame.Opcode.CONTINUATION;
import static jdk.incubator.http.internal.websocket.Frame.Opcode.PING;
import static jdk.incubator.http.internal.websocket.Frame.Opcode.PONG;
import static jdk.incubator.http.internal.websocket.Frame.Opcode.TEXT;

/*
 * A stateful object that represents a WebSocket message being sent to the
 * channel.
 *
 * Data provided to the constructors is copied. Otherwise we would have to deal
 * with mutability, security, masking/unmasking, readonly status, etc. So
 * copying greatly simplifies the implementation.
 *
 * In the case of memory-sensitive environments an alternative implementation
 * could use an internal pool of buffers though at the cost of extra complexity
 * and possible performance degradation.
 */
abstract class OutgoingMessage {

    // Share per WebSocket?
    private static final SecureRandom maskingKeys = new SecureRandom();

    protected ByteBuffer[] frame;
    protected int offset;

    /*
     * Performs contextualization. This method is not a part of the constructor
     * so it would be possible to defer the work it does until the most
     * convenient moment (up to the point where sentTo is invoked).
     */
    protected boolean contextualize(Context context) {
        // masking and charset decoding should be performed here rather than in
        // the constructor (as of today)
        if (context.isCloseSent()) {
            throw new IllegalStateException("Close sent");
        }
        return true;
    }

    protected boolean sendTo(RawChannel channel) throws IOException {
        while ((offset = nextUnwrittenIndex()) != -1) {
            long n = channel.write(frame, offset, frame.length - offset);
            if (n == 0) {
                return false;
            }
        }
        return true;
    }

    private int nextUnwrittenIndex() {
        for (int i = offset; i < frame.length; i++) {
            if (frame[i].hasRemaining()) {
                return i;
            }
        }
        return -1;
    }

    static final class Text extends OutgoingMessage {

        private final ByteBuffer payload;
        private final boolean isLast;

        Text(CharSequence characters, boolean isLast) {
            CharsetEncoder encoder = UTF_8.newEncoder(); // Share per WebSocket?
            try {
                payload = encoder.encode(CharBuffer.wrap(characters));
            } catch (CharacterCodingException e) {
                throw new IllegalArgumentException(
                        "Malformed UTF-8 text message");
            }
            this.isLast = isLast;
        }

        @Override
        protected boolean contextualize(Context context) {
            super.contextualize(context);
            if (context.isPreviousBinary() && !context.isPreviousLast()) {
                throw new IllegalStateException("Unexpected text message");
            }
            frame = getDataMessageBuffers(
                    TEXT, context.isPreviousLast(), isLast, payload, payload);
            context.setPreviousBinary(false);
            context.setPreviousText(true);
            context.setPreviousLast(isLast);
            return true;
        }
    }

    static final class Binary extends OutgoingMessage {

        private final ByteBuffer payload;
        private final boolean isLast;

        Binary(ByteBuffer payload, boolean isLast) {
            this.payload = requireNonNull(payload);
            this.isLast = isLast;
        }

        @Override
        protected boolean contextualize(Context context) {
            super.contextualize(context);
            if (context.isPreviousText() && !context.isPreviousLast()) {
                throw new IllegalStateException("Unexpected binary message");
            }
            ByteBuffer newBuffer = ByteBuffer.allocate(payload.remaining());
            frame = getDataMessageBuffers(
                    BINARY, context.isPreviousLast(), isLast, payload, newBuffer);
            context.setPreviousText(false);
            context.setPreviousBinary(true);
            context.setPreviousLast(isLast);
            return true;
        }
    }

    static final class Ping extends OutgoingMessage {

        Ping(ByteBuffer payload) {
            frame = getControlMessageBuffers(PING, payload);
        }
    }

    static final class Pong extends OutgoingMessage {

        Pong(ByteBuffer payload) {
            frame = getControlMessageBuffers(PONG, payload);
        }
    }

    static final class Close extends OutgoingMessage {

        Close() {
            frame = getControlMessageBuffers(CLOSE, EMPTY_BYTEBUFFER);
        }

        Close(int statusCode, CharSequence reason) {
            ByteBuffer payload = ByteBuffer.allocate(125)
                    .putChar((char) statusCode);
            CoderResult result = UTF_8.newEncoder()
                    .encode(CharBuffer.wrap(reason),
                            payload,
                            true);
            if (result.isOverflow()) {
                throw new IllegalArgumentException("Long reason");
            } else if (result.isError()) {
                try {
                    result.throwException();
                } catch (CharacterCodingException e) {
                    throw new IllegalArgumentException(
                            "Malformed UTF-8 reason", e);
                }
            }
            payload.flip();
            frame = getControlMessageBuffers(CLOSE, payload);
        }

        @Override
        protected boolean contextualize(Context context) {
            if (context.isCloseSent()) {
                return false;
            } else {
                context.setCloseSent();
                return true;
            }
        }
    }

    private static ByteBuffer[] getControlMessageBuffers(Opcode opcode,
                                                         ByteBuffer payload) {
        assert opcode.isControl() : opcode;
        int remaining = payload.remaining();
        if (remaining > 125) {
            throw new IllegalArgumentException
                    ("Long message: " + remaining);
        }
        ByteBuffer frame = ByteBuffer.allocate(MAX_HEADER_SIZE_BYTES + remaining);
        int mask = maskingKeys.nextInt();
        new Frame.HeaderWriter()
                .fin(true)
                .opcode(opcode)
                .payloadLen(remaining)
                .mask(mask)
                .write(frame);
        Frame.Masker.transferMasking(payload, frame, mask);
        frame.flip();
        return new ByteBuffer[]{frame};
    }

    private static ByteBuffer[] getDataMessageBuffers(Opcode type,
                                                      boolean isPreviousLast,
                                                      boolean isLast,
                                                      ByteBuffer payloadSrc,
                                                      ByteBuffer payloadDst) {
        assert !type.isControl() && type != CONTINUATION : type;
        ByteBuffer header = ByteBuffer.allocate(MAX_HEADER_SIZE_BYTES);
        int mask = maskingKeys.nextInt();
        new Frame.HeaderWriter()
                .fin(isLast)
                .opcode(isPreviousLast ? type : CONTINUATION)
                .payloadLen(payloadDst.remaining())
                .mask(mask)
                .write(header);
        header.flip();
        Frame.Masker.transferMasking(payloadSrc, payloadDst, mask);
        payloadDst.flip();
        return new ByteBuffer[]{header, payloadDst};
    }

    /*
     * An instance of this class is passed sequentially between messages, so
     * every message in a sequence can check the context it is in and update it
     * if necessary.
     */
    public static class Context {

        boolean previousLast = true;
        boolean previousBinary;
        boolean previousText;
        boolean closeSent;

        private boolean isPreviousText() {
            return this.previousText;
        }

        private void setPreviousText(boolean value) {
            this.previousText = value;
        }

        private boolean isPreviousBinary() {
            return this.previousBinary;
        }

        private void setPreviousBinary(boolean value) {
            this.previousBinary = value;
        }

        private boolean isPreviousLast() {
            return this.previousLast;
        }

        private void setPreviousLast(boolean value) {
            this.previousLast = value;
        }

        private boolean isCloseSent() {
            return closeSent;
        }

        private void setCloseSent() {
            closeSent = true;
        }
    }
}

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

import jdk.incubator.http.WebSocket.MessagePart;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.websocket.Frame.Opcode;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static jdk.incubator.http.internal.common.Utils.dump;
import static jdk.incubator.http.internal.websocket.StatusCodes.NO_STATUS_CODE;
import static jdk.incubator.http.internal.websocket.StatusCodes.isLegalToReceiveFromServer;

/*
 * Consumes frame parts and notifies a message consumer, when there is
 * sufficient data to produce a message, or part thereof.
 *
 * Data consumed but not yet translated is accumulated until it's sufficient to
 * form a message.
 */
/* Non-final for testing purposes only */
class FrameConsumer implements Frame.Consumer {

    private final MessageStreamConsumer output;
    private final UTF8AccumulatingDecoder decoder = new UTF8AccumulatingDecoder();
    private boolean fin;
    private Opcode opcode, originatingOpcode;
    private MessagePart part = MessagePart.WHOLE;
    private long payloadLen;
    private long unconsumedPayloadLen;
    private ByteBuffer binaryData;

    FrameConsumer(MessageStreamConsumer output) {
        this.output = requireNonNull(output);
    }

    /* Exposed for testing purposes only */
    MessageStreamConsumer getOutput() {
        return output;
    }

    @Override
    public void fin(boolean value) {
        Log.logTrace("Reading fin: {0}", value);
        fin = value;
    }

    @Override
    public void rsv1(boolean value) {
        Log.logTrace("Reading rsv1: {0}", value);
        if (value) {
            throw new FailWebSocketException("Unexpected rsv1 bit");
        }
    }

    @Override
    public void rsv2(boolean value) {
        Log.logTrace("Reading rsv2: {0}", value);
        if (value) {
            throw new FailWebSocketException("Unexpected rsv2 bit");
        }
    }

    @Override
    public void rsv3(boolean value) {
        Log.logTrace("Reading rsv3: {0}", value);
        if (value) {
            throw new FailWebSocketException("Unexpected rsv3 bit");
        }
    }

    @Override
    public void opcode(Opcode v) {
        Log.logTrace("Reading opcode: {0}", v);
        if (v == Opcode.PING || v == Opcode.PONG || v == Opcode.CLOSE) {
            if (!fin) {
                throw new FailWebSocketException("Fragmented control frame  " + v);
            }
            opcode = v;
        } else if (v == Opcode.TEXT || v == Opcode.BINARY) {
            if (originatingOpcode != null) {
                throw new FailWebSocketException(
                        format("Unexpected frame %s (fin=%s)", v, fin));
            }
            opcode = v;
            if (!fin) {
                originatingOpcode = v;
            }
        } else if (v == Opcode.CONTINUATION) {
            if (originatingOpcode == null) {
                throw new FailWebSocketException(
                        format("Unexpected frame %s (fin=%s)", v, fin));
            }
            opcode = v;
        } else {
            throw new FailWebSocketException("Unknown opcode " + v);
        }
    }

    @Override
    public void mask(boolean value) {
        Log.logTrace("Reading mask: {0}", value);
        if (value) {
            throw new FailWebSocketException("Masked frame received");
        }
    }

    @Override
    public void payloadLen(long value) {
        Log.logTrace("Reading payloadLen: {0}", value);
        if (opcode.isControl()) {
            if (value > 125) {
                throw new FailWebSocketException(
                        format("%s's payload length %s", opcode, value));
            }
            assert Opcode.CLOSE.isControl();
            if (opcode == Opcode.CLOSE && value == 1) {
                throw new FailWebSocketException("Incomplete status code");
            }
        }
        payloadLen = value;
        unconsumedPayloadLen = value;
    }

    @Override
    public void maskingKey(int value) {
        // `FrameConsumer.mask(boolean)` is where a masked frame is detected and
        // reported on; `FrameConsumer.mask(boolean)` MUST be invoked before
        // this method;
        // So this method (`maskingKey`) is not supposed to be invoked while
        // reading a frame that has came from the server. If this method is
        // invoked, then it's an error in implementation, thus InternalError
        throw new InternalError();
    }

    @Override
    public void payloadData(ByteBuffer data) {
        Log.logTrace("Reading payloadData: data={0}", data);
        unconsumedPayloadLen -= data.remaining();
        boolean isLast = unconsumedPayloadLen == 0;
        if (opcode.isControl()) {
            if (binaryData != null) { // An intermediate or the last chunk
                binaryData.put(data);
            } else if (!isLast) { // The first chunk
                int remaining = data.remaining();
                // It shouldn't be 125, otherwise the next chunk will be of size
                // 0, which is not what Reader promises to deliver (eager
                // reading)
                assert remaining < 125 : dump(remaining);
                binaryData = ByteBuffer.allocate(125).put(data);
            } else { // The only chunk
                binaryData = ByteBuffer.allocate(data.remaining()).put(data);
            }
        } else {
            part = determinePart(isLast);
            boolean text = opcode == Opcode.TEXT || originatingOpcode == Opcode.TEXT;
            if (!text) {
                output.onBinary(data.slice(), part);
                data.position(data.limit()); // Consume
            } else {
                boolean binaryNonEmpty = data.hasRemaining();
                CharBuffer textData;
                try {
                    textData = decoder.decode(data, part == MessagePart.WHOLE || part == MessagePart.LAST);
                } catch (CharacterCodingException e) {
                    throw new FailWebSocketException(
                            "Invalid UTF-8 in frame " + opcode, StatusCodes.NOT_CONSISTENT)
                            .initCause(e);
                }
                if (!(binaryNonEmpty && !textData.hasRemaining())) {
                    // If there's a binary data, that result in no text, then we
                    // don't deliver anything
                    output.onText(textData, part);
                }
            }
        }
    }

    @Override
    public void endFrame() {
        if (opcode.isControl()) {
            binaryData.flip();
        }
        switch (opcode) {
            case CLOSE:
                char statusCode = NO_STATUS_CODE;
                String reason = "";
                if (payloadLen != 0) {
                    int len = binaryData.remaining();
                    assert 2 <= len && len <= 125 : dump(len, payloadLen);
                    statusCode = binaryData.getChar();
                    if (!isLegalToReceiveFromServer(statusCode)) {
                        throw new FailWebSocketException(
                                "Illegal status code: " + statusCode);
                    }
                    try {
                        reason = UTF_8.newDecoder().decode(binaryData).toString();
                    } catch (CharacterCodingException e) {
                        throw new FailWebSocketException("Illegal close reason")
                                .initCause(e);
                    }
                }
                output.onClose(statusCode, reason);
                break;
            case PING:
                output.onPing(binaryData);
                binaryData = null;
                break;
            case PONG:
                output.onPong(binaryData);
                binaryData = null;
                break;
            default:
                assert opcode == Opcode.TEXT || opcode == Opcode.BINARY
                        || opcode == Opcode.CONTINUATION : dump(opcode);
                if (fin) {
                    // It is always the last chunk:
                    // either TEXT(FIN=TRUE)/BINARY(FIN=TRUE) or CONT(FIN=TRUE)
                    originatingOpcode = null;
                }
                break;
        }
        payloadLen = 0;
        opcode = null;
    }

    private MessagePart determinePart(boolean isLast) {
        boolean lastChunk = fin && isLast;
        switch (part) {
            case LAST:
            case WHOLE:
                return lastChunk ? MessagePart.WHOLE : MessagePart.FIRST;
            case FIRST:
            case PART:
                return lastChunk ? MessagePart.LAST : MessagePart.PART;
            default:
                throw new InternalError(String.valueOf(part));
        }
    }
}

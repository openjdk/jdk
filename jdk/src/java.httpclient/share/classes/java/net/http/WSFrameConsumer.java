/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.net.http.WSFrame.Opcode;
import java.net.http.WebSocket.MessagePart;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.TRACE;
import static java.net.http.WSUtils.dump;
import static java.net.http.WSUtils.logger;
import static java.net.http.WebSocket.CloseCode.NOT_CONSISTENT;
import static java.net.http.WebSocket.CloseCode.of;
import static java.util.Objects.requireNonNull;

/*
 * Consumes frame parts and notifies a message consumer, when there is
 * sufficient data to produce a message, or part thereof.
 *
 * Data consumed but not yet translated is accumulated until it's sufficient to
 * form a message.
 */
final class WSFrameConsumer implements WSFrame.Consumer {

    private final AtomicInteger invocationOrder = new AtomicInteger();

    private final WSMessageConsumer output;
    private final WSCharsetToolkit.Decoder decoder = new WSCharsetToolkit.Decoder();
    private boolean fin;
    private Opcode opcode, originatingOpcode;
    private MessagePart part = MessagePart.WHOLE;
    private long payloadLen;
    private WSShared<ByteBuffer> binaryData;

    WSFrameConsumer(WSMessageConsumer output) {
        this.output = requireNonNull(output);
    }

    @Override
    public void fin(boolean value) {
        assert invocationOrder.compareAndSet(0, 1) : dump(invocationOrder, value);
        if (logger.isLoggable(TRACE)) {
            // Checked for being loggable because of autoboxing of 'value'
            logger.log(TRACE, "Reading fin: {0}", value);
        }
        fin = value;
    }

    @Override
    public void rsv1(boolean value) {
        assert invocationOrder.compareAndSet(1, 2) : dump(invocationOrder, value);
        if (logger.isLoggable(TRACE)) {
            logger.log(TRACE, "Reading rsv1: {0}", value);
        }
        if (value) {
            throw new WSProtocolException("5.2.", "rsv1 bit is set unexpectedly");
        }
    }

    @Override
    public void rsv2(boolean value) {
        assert invocationOrder.compareAndSet(2, 3) : dump(invocationOrder, value);
        if (logger.isLoggable(TRACE)) {
            logger.log(TRACE, "Reading rsv2: {0}", value);
        }
        if (value) {
            throw new WSProtocolException("5.2.", "rsv2 bit is set unexpectedly");
        }
    }

    @Override
    public void rsv3(boolean value) {
        assert invocationOrder.compareAndSet(3, 4) : dump(invocationOrder, value);
        if (logger.isLoggable(TRACE)) {
            logger.log(TRACE, "Reading rsv3: {0}", value);
        }
        if (value) {
            throw new WSProtocolException("5.2.", "rsv3 bit is set unexpectedly");
        }
    }

    @Override
    public void opcode(Opcode v) {
        assert invocationOrder.compareAndSet(4, 5) : dump(invocationOrder, v);
        logger.log(TRACE, "Reading opcode: {0}", v);
        if (v == Opcode.PING || v == Opcode.PONG || v == Opcode.CLOSE) {
            if (!fin) {
                throw new WSProtocolException("5.5.", "A fragmented control frame " + v);
            }
            opcode = v;
        } else if (v == Opcode.TEXT || v == Opcode.BINARY) {
            if (originatingOpcode != null) {
                throw new WSProtocolException
                        ("5.4.", format("An unexpected frame %s (fin=%s)", v, fin));
            }
            opcode = v;
            if (!fin) {
                originatingOpcode = v;
            }
        } else if (v == Opcode.CONTINUATION) {
            if (originatingOpcode == null) {
                throw new WSProtocolException
                        ("5.4.", format("An unexpected frame %s (fin=%s)", v, fin));
            }
            opcode = v;
        } else {
            throw new WSProtocolException("5.2.", "An unknown opcode " + v);
        }
    }

    @Override
    public void mask(boolean value) {
        assert invocationOrder.compareAndSet(5, 6) : dump(invocationOrder, value);
        if (logger.isLoggable(TRACE)) {
            logger.log(TRACE, "Reading mask: {0}", value);
        }
        if (value) {
            throw new WSProtocolException
                    ("5.1.", "Received a masked frame from the server");
        }
    }

    @Override
    public void payloadLen(long value) {
        assert invocationOrder.compareAndSet(6, 7) : dump(invocationOrder, value);
        if (logger.isLoggable(TRACE)) {
            logger.log(TRACE, "Reading payloadLen: {0}", value);
        }
        if (opcode.isControl()) {
            if (value > 125) {
                throw new WSProtocolException
                        ("5.5.", format("A control frame %s has a payload length of %s",
                                opcode, value));
            }
            assert Opcode.CLOSE.isControl();
            if (opcode == Opcode.CLOSE && value == 1) {
                throw new WSProtocolException
                        ("5.5.1.", "A Close frame's status code is only 1 byte long");
            }
        }
        payloadLen = value;
    }

    @Override
    public void maskingKey(int value) {
        assert false : dump(invocationOrder, value);
    }

    @Override
    public void payloadData(WSShared<ByteBuffer> data, boolean isLast) {
        assert invocationOrder.compareAndSet(7, isLast ? 8 : 7)
                : dump(invocationOrder, data, isLast);
        if (logger.isLoggable(TRACE)) {
            logger.log(TRACE, "Reading payloadData: data={0}, isLast={1}", data, isLast);
        }
        if (opcode.isControl()) {
            if (binaryData != null) {
                binaryData.put(data);
                data.dispose();
            } else if (!isLast) {
                // The first chunk of the message
                int remaining = data.remaining();
                // It shouldn't be 125, otherwise the next chunk will be of size
                // 0, which is not what Reader promises to deliver (eager
                // reading)
                assert remaining < 125 : dump(remaining);
                WSShared<ByteBuffer> b = WSShared.wrap(ByteBuffer.allocate(125)).put(data);
                data.dispose();
                binaryData = b; // Will be disposed by the user
            } else {
                // The only chunk; will be disposed by the user
                binaryData = data.position(data.limit()); // FIXME: remove this hack
            }
        } else {
            part = determinePart(isLast);
            boolean text = opcode == Opcode.TEXT || originatingOpcode == Opcode.TEXT;
            if (!text) {
                output.onBinary(part, data);
            } else {
                boolean binaryNonEmpty = data.hasRemaining();
                WSShared<CharBuffer> textData;
                try {
                    textData = decoder.decode(data, part == MessagePart.WHOLE || part == MessagePart.LAST);
                } catch (CharacterCodingException e) {
                    throw new WSProtocolException
                            ("5.6.", "Invalid UTF-8 sequence in frame " + opcode, NOT_CONSISTENT, e);
                }
                if (!(binaryNonEmpty && !textData.hasRemaining())) {
                    // If there's a binary data, that result in no text, then we
                    // don't deliver anything
                    output.onText(part, textData);
                }
            }
        }
    }

    @Override
    public void endFrame() {
        assert invocationOrder.compareAndSet(8, 0) : dump(invocationOrder);
        if (opcode.isControl()) {
            binaryData.flip();
        }
        switch (opcode) {
            case CLOSE:
                WebSocket.CloseCode cc;
                String reason;
                if (payloadLen == 0) {
                    cc = null;
                    reason = "";
                } else {
                    ByteBuffer b = binaryData.buffer();
                    int len = b.remaining();
                    assert 2 <= len && len <= 125 : dump(len, payloadLen);
                    try {
                        cc = of(b.getChar());
                        reason = WSCharsetToolkit.decode(b).toString();
                    } catch (IllegalArgumentException e) {
                        throw new WSProtocolException
                                ("5.5.1", "Incorrect status code", e);
                    } catch (CharacterCodingException e) {
                        throw new WSProtocolException
                                ("5.5.1", "Close reason is a malformed UTF-8 sequence", e);
                    }
                }
                binaryData.dispose(); // Manual dispose
                output.onClose(cc, reason);
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

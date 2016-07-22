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

import java.nio.ByteBuffer;

import static java.lang.String.format;
import static java.net.http.WSFrame.Opcode.ofCode;
import static java.net.http.WSUtils.dump;

/*
 * A collection of utilities for reading, writing, and masking frames.
 */
final class WSFrame {

    private WSFrame() { }

    static final int MAX_HEADER_SIZE_BYTES = 2 + 8 + 4;

    enum Opcode {

        CONTINUATION   (0x0),
        TEXT           (0x1),
        BINARY         (0x2),
        NON_CONTROL_0x3(0x3),
        NON_CONTROL_0x4(0x4),
        NON_CONTROL_0x5(0x5),
        NON_CONTROL_0x6(0x6),
        NON_CONTROL_0x7(0x7),
        CLOSE          (0x8),
        PING           (0x9),
        PONG           (0xA),
        CONTROL_0xB    (0xB),
        CONTROL_0xC    (0xC),
        CONTROL_0xD    (0xD),
        CONTROL_0xE    (0xE),
        CONTROL_0xF    (0xF);

        private static final Opcode[] opcodes;

        static {
            Opcode[] values = values();
            opcodes = new Opcode[values.length];
            for (Opcode c : values) {
                assert opcodes[c.code] == null
                        : WSUtils.dump(c, c.code, opcodes[c.code]);
                opcodes[c.code] = c;
            }
        }

        private final byte code;
        private final char shiftedCode;
        private final String description;

        Opcode(int code) {
            this.code = (byte) code;
            this.shiftedCode = (char) (code << 8);
            this.description = format("%x (%s)", code, name());
        }

        boolean isControl() {
            return (code & 0x8) != 0;
        }

        static Opcode ofCode(int code) {
            return opcodes[code & 0xF];
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /*
     * A utility to mask payload data.
     */
    static final class Masker {

        private final ByteBuffer acc = ByteBuffer.allocate(8);
        private final int[] maskBytes = new int[4];
        private int offset;
        private long maskLong;

        /*
         * Sets up the mask.
         */
        Masker mask(int value) {
            acc.clear().putInt(value).putInt(value).flip();
            for (int i = 0; i < maskBytes.length; i++) {
                maskBytes[i] = acc.get(i);
            }
            offset = 0;
            maskLong = acc.getLong(0);
            return this;
        }

        /*
         * Reads as many bytes as possible from the given input buffer, writing
         * the resulting masked bytes to the given output buffer.
         *
         * src.remaining() <= dst.remaining() // TODO: do we need this restriction?
         * 'src' and 'dst' can be the same ByteBuffer
         */
        Masker applyMask(ByteBuffer src, ByteBuffer dst) {
            if (src.remaining() > dst.remaining()) {
                throw new IllegalArgumentException(dump(src, dst));
            }
            begin(src, dst);
            loop(src, dst);
            end(src, dst);
            return this;
        }

        // Applying the remaining of the mask (strictly not more than 3 bytes)
        // byte-wise
        private void begin(ByteBuffer src, ByteBuffer dst) {
            if (offset > 0) {
                for (int i = src.position(), j = dst.position();
                     offset < 4 && i <= src.limit() - 1 && j <= dst.limit() - 1;
                     i++, j++, offset++) {
                    dst.put(j, (byte) (src.get(i) ^ maskBytes[offset]));
                    dst.position(j + 1);
                    src.position(i + 1);
                }
                offset &= 3;
            }
        }

        private void loop(ByteBuffer src, ByteBuffer dst) {
            int i = src.position();
            int j = dst.position();
            final int srcLim = src.limit() - 8;
            final int dstLim = dst.limit() - 8;
            for (; i <= srcLim && j <= dstLim; i += 8, j += 8) {
                dst.putLong(j, (src.getLong(i) ^ maskLong));
            }
            if (i > src.limit()) {
                src.position(i - 8);
            } else {
                src.position(i);
            }
            if (j > dst.limit()) {
                dst.position(j - 8);
            } else {
                dst.position(j);
            }
        }

        // Applying the mask to the remaining bytes byte-wise (don't make any
        // assumptions on how many, hopefully not more than 7 for 64bit arch)
        private void end(ByteBuffer src, ByteBuffer dst) {
            for (int i = src.position(), j = dst.position();
                 i <= src.limit() - 1 && j <= dst.limit() - 1;
                 i++, j++, offset = (offset + 1) & 3) { // offset cycle through 0..3
                dst.put(j, (byte) (src.get(i) ^ maskBytes[offset]));
                src.position(i + 1);
                dst.position(j + 1);
            }
        }
    }

    /*
     * A builder of frame headers, capable of writing to a given buffer.
     *
     * The builder does not enforce any protocol-level rules, it simply writes
     * a header structure to the buffer. The order of calls to intermediate
     * methods is not significant.
     */
    static final class HeaderBuilder {

        private char firstChar;
        private long payloadLen;
        private int maskingKey;
        private boolean mask;

        HeaderBuilder fin(boolean value) {
            if (value) {
                firstChar |=  0b10000000_00000000;
            } else {
                firstChar &= ~0b10000000_00000000;
            }
            return this;
        }

        HeaderBuilder rsv1(boolean value) {
            if (value) {
                firstChar |=  0b01000000_00000000;
            } else {
                firstChar &= ~0b01000000_00000000;
            }
            return this;
        }

        HeaderBuilder rsv2(boolean value) {
            if (value) {
                firstChar |=  0b00100000_00000000;
            } else {
                firstChar &= ~0b00100000_00000000;
            }
            return this;
        }

        HeaderBuilder rsv3(boolean value) {
            if (value) {
                firstChar |=  0b00010000_00000000;
            } else {
                firstChar &= ~0b00010000_00000000;
            }
            return this;
        }

        HeaderBuilder opcode(Opcode value) {
            firstChar = (char) ((firstChar & 0xF0FF) | value.shiftedCode);
            return this;
        }

        HeaderBuilder payloadLen(long value) {
            payloadLen = value;
            firstChar &= 0b11111111_10000000; // Clear previous payload length leftovers
            if (payloadLen < 126) {
                firstChar |= payloadLen;
            } else if (payloadLen < 65535) {
                firstChar |= 126;
            } else {
                firstChar |= 127;
            }
            return this;
        }

        HeaderBuilder mask(int value) {
            firstChar |= 0b00000000_10000000;
            maskingKey = value;
            mask = true;
            return this;
        }

        HeaderBuilder noMask() {
            firstChar &= ~0b00000000_10000000;
            mask = false;
            return this;
        }

        /*
         * Writes the header to the given buffer.
         *
         * The buffer must have at least MAX_HEADER_SIZE_BYTES remaining. The
         * buffer's position is incremented by the number of bytes written.
         */
        void build(ByteBuffer buffer) {
            buffer.putChar(firstChar);
            if (payloadLen >= 126) {
                if (payloadLen < 65535) {
                    buffer.putChar((char) payloadLen);
                } else {
                    buffer.putLong(payloadLen);
                }
            }
            if (mask) {
                buffer.putInt(maskingKey);
            }
        }
    }

    /*
     * A consumer of frame parts.
     *
     * Guaranteed to be called in the following order by the Frame.Reader:
     *
     *     fin rsv1 rsv2 rsv3 opcode mask payloadLength maskingKey? payloadData+ endFrame
     */
    interface Consumer {

        void fin(boolean value);

        void rsv1(boolean value);

        void rsv2(boolean value);

        void rsv3(boolean value);

        void opcode(Opcode value);

        void mask(boolean value);

        void payloadLen(long value);

        void maskingKey(int value);

        /*
         * Called when a part of the payload is ready to be consumed.
         *
         * Though may not yield a complete payload in a single invocation, i.e.
         *
         *     data.remaining() < payloadLen
         *
         * the sum of `data.remaining()` passed to all invocations of this
         * method will be equal to 'payloadLen', reported in
         * `void payloadLen(long value)`
         *
         * No unmasking is done.
         */
        void payloadData(WSShared<ByteBuffer> data, boolean isLast);

        void endFrame(); // TODO: remove (payloadData(isLast=true)) should be enough
    }

    /*
     * A Reader of Frames.
     *
     * No protocol-level rules are enforced, only frame structure.
     */
    static final class Reader {

        private static final int AWAITING_FIRST_BYTE  =  1;
        private static final int AWAITING_SECOND_BYTE =  2;
        private static final int READING_16_LENGTH    =  4;
        private static final int READING_64_LENGTH    =  8;
        private static final int READING_MASK         = 16;
        private static final int READING_PAYLOAD      = 32;

        // A private buffer used to simplify multi-byte integers reading
        private final ByteBuffer accumulator = ByteBuffer.allocate(8);
        private int state = AWAITING_FIRST_BYTE;
        private boolean mask;
        private long payloadLength;

        /*
         * Reads at most one frame from the given buffer invoking the consumer's
         * methods corresponding to the frame elements found.
         *
         * As much of the frame's payload, if any, is read. The buffers position
         * is updated to reflect the number of bytes read.
         *
         * Throws WSProtocolException if the frame is malformed.
         */
        void readFrame(WSShared<ByteBuffer> shared, Consumer consumer) {
            ByteBuffer input = shared.buffer();
            loop:
            while (true) {
                byte b;
                switch (state) {
                    case AWAITING_FIRST_BYTE:
                        if (!input.hasRemaining()) {
                            break loop;
                        }
                        b = input.get();
                        consumer.fin( (b & 0b10000000) != 0);
                        consumer.rsv1((b & 0b01000000) != 0);
                        consumer.rsv2((b & 0b00100000) != 0);
                        consumer.rsv3((b & 0b00010000) != 0);
                        consumer.opcode(ofCode(b));
                        state = AWAITING_SECOND_BYTE;
                        continue loop;
                    case AWAITING_SECOND_BYTE:
                        if (!input.hasRemaining()) {
                            break loop;
                        }
                        b = input.get();
                        consumer.mask(mask = (b & 0b10000000) != 0);
                        byte p1 = (byte) (b & 0b01111111);
                        if (p1 < 126) {
                            assert p1 >= 0 : p1;
                            consumer.payloadLen(payloadLength = p1);
                            state = mask ? READING_MASK : READING_PAYLOAD;
                        } else if (p1 < 127) {
                            state = READING_16_LENGTH;
                        } else {
                            state = READING_64_LENGTH;
                        }
                        continue loop;
                    case READING_16_LENGTH:
                        if (!input.hasRemaining()) {
                            break loop;
                        }
                        b = input.get();
                        if (accumulator.put(b).position() < 2) {
                            continue loop;
                        }
                        payloadLength = accumulator.flip().getChar();
                        if (payloadLength < 126) {
                            throw notMinimalEncoding(payloadLength, 2);
                        }
                        consumer.payloadLen(payloadLength);
                        accumulator.clear();
                        state = mask ? READING_MASK : READING_PAYLOAD;
                        continue loop;
                    case READING_64_LENGTH:
                        if (!input.hasRemaining()) {
                            break loop;
                        }
                        b = input.get();
                        if (accumulator.put(b).position() < 8) {
                            continue loop;
                        }
                        payloadLength = accumulator.flip().getLong();
                        if (payloadLength < 0) {
                            throw negativePayload(payloadLength);
                        } else if (payloadLength < 65535) {
                            throw notMinimalEncoding(payloadLength, 8);
                        }
                        consumer.payloadLen(payloadLength);
                        accumulator.clear();
                        state = mask ? READING_MASK : READING_PAYLOAD;
                        continue loop;
                    case READING_MASK:
                        if (!input.hasRemaining()) {
                            break loop;
                        }
                        b = input.get();
                        if (accumulator.put(b).position() != 4) {
                            continue loop;
                        }
                        consumer.maskingKey(accumulator.flip().getInt());
                        accumulator.clear();
                        state = READING_PAYLOAD;
                        continue loop;
                    case READING_PAYLOAD:
                        // This state does not require any bytes to be available
                        // in the input buffer in order to proceed
                        boolean fullyRead;
                        int limit;
                        if (payloadLength <= input.remaining()) {
                            limit = input.position() + (int) payloadLength;
                            payloadLength = 0;
                            fullyRead = true;
                        } else {
                            limit = input.limit();
                            payloadLength -= input.remaining();
                            fullyRead = false;
                        }
                        // FIXME: consider a case where payloadLen != 0,
                        // but input.remaining() == 0
                        //
                        // There shouldn't be an invocation of payloadData with
                        // an empty buffer, as it would be an artifact of
                        // reading
                        consumer.payloadData(shared.share(input.position(), limit), fullyRead);
                        // Update the position manually, since reading the
                        // payload doesn't advance buffer's position
                        input.position(limit);
                        if (fullyRead) {
                            consumer.endFrame();
                            state = AWAITING_FIRST_BYTE;
                        }
                        break loop;
                    default:
                        throw new InternalError(String.valueOf(state));
                }
            }
        }

        private static WSProtocolException negativePayload(long payloadLength) {
            return new WSProtocolException
                    ("5.2.", format("Negative 64-bit payload length %s", payloadLength));
        }

        private static WSProtocolException notMinimalEncoding(long payloadLength, int numBytes) {
            return new WSProtocolException
                    ("5.2.", format("Payload length (%s) is not encoded with minimal number (%s) of bytes",
                            payloadLength, numBytes));
        }
    }
}

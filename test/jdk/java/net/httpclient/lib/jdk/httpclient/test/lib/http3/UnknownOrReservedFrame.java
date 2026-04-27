/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.httpclient.test.lib.http3;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Random;

import jdk.internal.net.http.http3.frames.AbstractHttp3Frame;
import jdk.internal.net.http.quic.VariableLengthEncoder;

/**
 * A test-only HTTP3 frame used to exercise the case where (client) implementation
 * is expected to ignore unknown/reserved frames, as expected by RFC-9114, section
 * 7.2.8 and section 9
 */
final class UnknownOrReservedFrame extends AbstractHttp3Frame {

    private static final Random random = new Random(getSeed());

    private final int payloadLength;
    private final byte[] payload;

    public UnknownOrReservedFrame() {
        super(generateRandomFrameType());
        this.payloadLength = random.nextInt(13); // arbitrary upper bound
        this.payload = new byte[this.payloadLength];
        random.nextBytes(this.payload);
    }

    @Override
    public long length() {
        return this.payloadLength;
    }

    ByteBuffer toByteBuffer() {
        final int frameSize = (int) this.size(); // cast is OK - value expected to be within range
        final ByteBuffer buf = ByteBuffer.allocate(frameSize);
        // write the type of the frame
        VariableLengthEncoder.encode(buf, this.type());
        // write the length of the payload
        VariableLengthEncoder.encode(buf, this.payloadLength);
        // write the payload
        buf.put(this.payload);
        buf.flip();
        return buf;
    }

    private static long generateRandomFrameType() {
        final boolean useReservedFrameType = random.nextBoolean();
        if (useReservedFrameType) {
            final int N = random.nextInt(100); // arbitrary upper bound
            // RFC-9114, section 7.2.8: Frame types of the format 0x1f * N + 0x21 for non-negative
            // integer values of N are reserved to exercise the requirement
            // that unknown types be ignored
            return 0x1F * N + 0x21;
        }
        // arbitrary lower bound of 0x45
        return random.nextLong(0x45, VariableLengthEncoder.MAX_ENCODED_INTEGER);
    }

    private static long getSeed() {
        Long seed = Long.getLong("seed");
        return seed != null ? seed : System.nanoTime() ^ new Random().nextLong();
    }

    static Optional<UnknownOrReservedFrame> tryGenerateFrame() {
        // an arbitrary decision to create a new unknown/reserved frame
        if (random.nextInt() % 8 == 0) {
            return Optional.of(new UnknownOrReservedFrame());
        }
        return Optional.empty();
    }
}

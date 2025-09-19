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

/**
 * PaddingFrames. Since padding frames comprise a single zero byte
 * this class actually represents sequences of PaddingFrames.
 * When decoding, the class consumes all the zero bytes that are
 * available and when encoding, the number of required padding bytes
 * is specified.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class PaddingFrame extends QuicFrame {

    private final int size;

    /**
     * Incoming
     */
    PaddingFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(PADDING);
        int count = 1;
        while (buffer.hasRemaining()) {
            if (buffer.get() == 0) {
                count++;
            } else {
                int pos = buffer.position();
                buffer.position(pos - 1);
                break;
            }
        }
        size = count;
    }

    /**
     * Outgoing
     * @param size the number of padding frames that should be written
     *             to the buffer. Each frame is one byte long.
     */
    public PaddingFrame(int size) {
        super(PADDING);
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        this.size = size;
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        for (int i=0; i<size; i++) {
            buffer.put((byte)0); // would benefit from a fill operation here?
        }
    }

    /**
     * The number of PADDING frames represented
     */
    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isAckEliciting() { return false; }

    @Override
    public String toString() {
        return "Padding(" + size + ")";
    }
}

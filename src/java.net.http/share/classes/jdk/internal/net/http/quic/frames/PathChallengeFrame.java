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
 * A PATH_CHALLENGE Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class PathChallengeFrame extends QuicFrame {

    public static final int LENGTH = 8;
    private final ByteBuffer data;

    /**
     * Incoming PATH_CHALLENGE frame returned by QuicFrame.decode()
     *
     * @param buffer
     * @param type
     * @throws QuicTransportException if the frame was malformed
     */
    PathChallengeFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(PATH_CHALLENGE);
        validateRemainingLength(buffer, LENGTH, type);
        int position = buffer.position();
        data = buffer.slice(position, LENGTH);
        buffer.position(position + LENGTH);
    }

    /**
     * Outgoing PATH_CHALLENGE frame
     */
    public PathChallengeFrame(ByteBuffer data) {
        super(PATH_CHALLENGE);
        if (data.remaining() != LENGTH)
            throw new IllegalArgumentException("challenge data must be 8 bytes");
        this.data = data.slice();
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, PATH_CHALLENGE, "type");
        putByteBuffer(buffer, data);
        assert buffer.position() - pos == size();
    }

    /**
     */
    public ByteBuffer data() {
        return data;
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(PATH_CHALLENGE) + LENGTH;
    }
}

/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.quic.QuicConnectionImpl;

/**
 * A collection of utilities methods to analyze and work with
 * quic streams.
 */
public final class QuicStreams {
    private QuicStreams() { throw new InternalError("should not come here"); }

    public static final int TYPE_MASK = 0x03;
    public static final int UNI_MASK  = 0x02;
    public static final int SRV_MASK  = 0x01;

    public static int streamType(long streamId) {
        return (int) streamId & TYPE_MASK;
    }

    public static boolean isBidirectional(long streamId) {
        return ((int) streamId & UNI_MASK) == 0;
    }

    public static boolean isUnidirectional(long streamId) {
        return ((int) streamId & UNI_MASK) == UNI_MASK;
    }

    public static boolean isBidirectional(int streamType) {
        return (streamType & UNI_MASK) == 0;
    }

    public static boolean isUnidirectional(int streamType) {
        return (streamType & UNI_MASK) == UNI_MASK;
    }

    public static boolean isClientInitiated(long streamId) {
        return ((int) streamId & SRV_MASK) == 0;
    }

    public static boolean isServerInitiated(long streamId) {
        return ((int) streamId & SRV_MASK) == SRV_MASK;
    }

    public static boolean isClientInitiated(int streamType) {
        return  (streamType & SRV_MASK) == 0;
    }

    public static boolean isServerInitiated(int streamType) {
        return (streamType & SRV_MASK) == SRV_MASK;
    }

    public static AbstractQuicStream createStream(QuicConnectionImpl connection, long streamId) {
        int type = streamType(streamId);
        boolean isClient = connection.isClientConnection();
        return switch (type) {
            case 0x00, 0x01 -> new QuicBidiStreamImpl(connection, streamId);
            case 0x02 -> isClient ? new QuicSenderStreamImpl(connection, streamId)
                    : new QuicReceiverStreamImpl(connection, streamId);
            case 0x03 -> isClient ? new QuicReceiverStreamImpl(connection, streamId)
                    : new QuicSenderStreamImpl(connection, streamId);
            default -> throw new IllegalArgumentException("bad stream type %s for stream %s"
                    .formatted(type, streamId));
        };
    }

}

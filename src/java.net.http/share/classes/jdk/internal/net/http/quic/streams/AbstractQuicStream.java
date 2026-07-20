/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * An abstract class to model a QuicStream.
 * A quic stream can be either unidirectional
 * or bidirectional. A unidirectional stream can
 * be opened for reading or for writing.
 * Concrete subclasses of {@code AbstractQuicStream} should
 * implement {@link QuicSenderStream} (unidirectional {@link
 * StreamMode#WRITE_ONLY} stream), or {@link QuicReceiverStream}
 * (unidirectional {@link StreamMode#READ_ONLY} stream), or
 * both (bidirectional {@link StreamMode#READ_WRITE} stream).
 */
abstract sealed class AbstractQuicStream implements QuicStream
        permits QuicBidiStreamImpl, QuicSenderStreamImpl, QuicReceiverStreamImpl {

    private final QuicConnectionImpl connection;
    private final long streamId;
    private final StreamMode mode;

    AbstractQuicStream(QuicConnectionImpl connection, long streamId) {
        this.mode = mode(connection, streamId);
        this.streamId = streamId;
        this.connection = connection;
    }

    private static StreamMode mode(QuicConnectionImpl connection, long streamId) {
        if (QuicStreams.isBidirectional(streamId)) return StreamMode.READ_WRITE;
        if (connection.isClientConnection()) {
            return QuicStreams.isClientInitiated(streamId)
                    ? StreamMode.WRITE_ONLY : StreamMode.READ_ONLY;
        } else {
            return QuicStreams.isClientInitiated(streamId)
                    ? StreamMode.READ_ONLY : StreamMode.WRITE_ONLY;
        }
    }

    /**
     * {@return the {@code QuicConnectionImpl} instance this stream
     * belongs to}
     */
    final QuicConnectionImpl connection() {
        return connection;
    }

    @Override
    public final long streamId() {
        return streamId;
    }

    @Override
    public final StreamMode mode() {
        return mode;
    }

    @Override
    public final boolean isClientInitiated() {
        return QuicStreams.isClientInitiated(type());
    }

    @Override
    public final boolean isServerInitiated() {
        return QuicStreams.isServerInitiated(type());
    }

    @Override
    public final boolean isBidirectional() {
        return QuicStreams.isBidirectional(type());
    }

    @Override
    public final boolean isLocalInitiated() {
        return connection().isClientConnection() == isClientInitiated();
    }

    @Override
    public final boolean isRemoteInitiated() {
        return connection().isClientConnection() != isClientInitiated();
    }

    @Override
    public final int type() {
        return QuicStreams.streamType(streamId);
    }

    /**
     * {@return true if this stream isn't expecting anything
     *  from the peer and can be removed from the streams map}
     */
    public abstract boolean isDone();

}

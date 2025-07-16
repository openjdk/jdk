/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * An interface to model a QuicStream.
 * A quic stream can be either unidirectional
 * or bidirectional. A unidirectional stream can
 * be opened for reading or for writing.
 * Concrete subclasses of {@code QuicStream} should
 * implement {@link QuicSenderStream} (unidirectional {@link
 * StreamMode#WRITE_ONLY} stream), or {@link QuicReceiverStream}
 * (unidirectional {@link StreamMode#READ_ONLY} stream), or
 * {@link QuicBidiStream} (bidirectional {@link StreamMode#READ_WRITE} stream).
 */
public sealed interface QuicStream
        permits QuicSenderStream, QuicReceiverStream, QuicBidiStream, AbstractQuicStream {

    /**
     * An interface that unifies the three different stream states.
     * @apiNote
     * This is mostly used for logging purposes, to log the
     * combined state of a stream.
     */
     sealed interface StreamState permits
            QuicReceiverStream.ReceivingStreamState,
            QuicSenderStream.SendingStreamState,
            QuicBidiStream.BidiStreamState {
        String name();

        /**
         * {@return true if this is a terminal state}
         */
        boolean isTerminal();
    }

    /**
     * The stream operation mode.
     * One of {@link #READ_ONLY}, {@link #WRITE_ONLY}, or {@link #READ_WRITE}.
     */
     enum StreamMode {
        READ_ONLY, WRITE_ONLY, READ_WRITE;

        /**
         * {@return true if this operation mode allows reading data from the stream}
         */
        public boolean isReadable() {
            return this != WRITE_ONLY;
        }

        /**
         * {@return true if this operation mode allows writing data to the stream}
         */
        public boolean isWritable() {
            return this != READ_ONLY;
        }
    }

    /**
     * {@return the stream ID of this stream}
     */
    long streamId();

    /**
     * {@return this stream operation mode}
     * One of {@link StreamMode#READ_ONLY}, {@link StreamMode#WRITE_ONLY},
     * or {@link StreamMode#READ_WRITE}.
     */
    StreamMode mode();

    /**
     * {@return whether this stream is client initiated}
     */
    boolean isClientInitiated();

    /**
     * {@return whether this stream is server initiated}
     */
    boolean isServerInitiated();

    /**
     * {@return whether this stream is bidirectional}
     */
    boolean isBidirectional();

    /**
     * {@return true if this stream is local initiated}
     */
    boolean isLocalInitiated();

    /**
     * {@return true if this stream is remote initiated}
     */
    boolean isRemoteInitiated();

    /**
     * The type of this stream, as an int. This is a number between
     * 0 and 3 inclusive, and corresponds to the last two lowest bits
     * of the stream ID.
     * <ul>
     *     <li> 0x00: bidirectional, client initiated</li>
     *     <li> 0x01: bidirectional, server initiated</li>
     *     <li> 0x02: unidirectional, client initiated</li>
     *     <li> 0x03: unidirectional, server initiated</li>
     * </ul>
     * @return the type of this stream, as an int
     */
    int type();

    /**
     * {@return the combined stream state}
     *
     * @apiNote
     * This is mostly used for logging purposes, to log the
     * combined state of a stream.
     */
    StreamState state();

    /**
     * {@return true if the stream has errors}
     * For a {@linkplain QuicBidiStream bidirectional} stream,
     * this method returns true if either its sending part or
     * its receiving part was closed with a non-zero error code.
     */
    boolean hasError();

}

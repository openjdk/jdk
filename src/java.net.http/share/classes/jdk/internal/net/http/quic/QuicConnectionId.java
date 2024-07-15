/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.nio.ByteBuffer;
import java.util.HexFormat;

/**
 * Models a Quic Connection id.
 * QuicConnectionId instance are typically created by a Quic client or server.
 */
public abstract class QuicConnectionId implements Comparable<QuicConnectionId> {

    /**
     * The maximum length, in bytes, of a connection id.
     * This is supposed to be version-specific, but for now, we
     * are going to treat that as a universal constant.
     */
    public static final int MAX_CONNECTION_ID_LENGTH = 20;

    /**
     * Returns the length of this connection id, in bytes;
     * @return the length of this connection id
     */
    public abstract int length();

    /**
     * Returns this connection id bytes as a read-only buffer.
     * @return A new read only buffer containing this connection id bytes.
     */
    public abstract ByteBuffer asReadOnlyBuffer();

    /**
     * Returns this connection id bytes as a byte array.
     * @return A new byte array containing this connection id bytes.
     */
    public byte[] getBytes() {
        byte[] connId = new byte[length()];
        asReadOnlyBuffer().get(connId);
        return connId;
    }

    /**
     * Compare this connection id bytes with the bytes in the
     * given byte buffer.
     * <p> The given byte buffer is expected to have
     * its {@linkplain ByteBuffer#position() position} set at the start
     * of the connection id, and its {@linkplain ByteBuffer#limit() limit}
     * at the end. In other words, {@code Buffer.remaining()} should
     * indicate the connection id length.
     * <p> This method does not advance the buffer position.
     *
     * @implSpec  This is equivalent to: <pre>{@code
     *  this.asReadOnlyBuffer().comparesTo(idbytes)
     *  }</pre>
     *
     * @param idbytes A byte buffer containing the id bytes of another
     *                connection id.
     * @return {@code -1}, {@code 0}, or {@code 1} if this connection's id
     *         bytes are less, equal, or greater than the provided bytes.
     */
    public int compareBytes(ByteBuffer idbytes) {
        return asReadOnlyBuffer().compareTo(idbytes);
    }

    /**
     * Tells whether the given byte buffer matches this connection id.
     * The given byte buffer is expected to have
     * its {@linkplain ByteBuffer#position() position} set at the start
     * of the connection id, and its {@linkplain ByteBuffer#limit() limit}
     * at the end. In other words, {@code Buffer.remaining()} should
     * indicate the connection id length.
     * <p> This method does not advance the buffer position.
     *
     * @implSpec
     * This is equivalent to: <pre>{@code
     *  this.asReadOnlyBuffer().mismatch(idbytes) == -1
     *  }</pre>
     *
     * @param idbytes A buffer that delimits a connection id.
     * @return true if the bytes in the given buffer match this
     * connection id bytes.
     */
    public boolean matches(ByteBuffer idbytes) {
        return asReadOnlyBuffer().mismatch(idbytes) == -1;
    }

    /**
     * Tells whether the given bytes matches this connection id.
     *
     * @implSpec
     * This is equivalent to calling
     * {@link #matches(byte[], int, int)
     * matches(ByteBuffer.wrap(idbytes, 0, idbytes.length)}
     *
     * @param idbytes bytes identifying a connection id.
     * @return true if the given bytes exactly match this
     * connection id bytes.
     */
    public boolean matches(byte[] idbytes) {
        return matches(idbytes, 0, idbytes.length);
    }

    /**
     * Tells whether the given bytes matches this connection id.
     *
     * @implSpec
     * This is equivalent to calling
     * {@link #matches(ByteBuffer) matches(ByteBuffer.wrap(buffer, offset, length))
     *
     * @param buffer a buffer containing bytes identifying a connection id.
     * @param offset offset of the fisrt byte to match
     * @param length number of bytes to match
     * @return true if the bytes starting at the given position and ending
     *  after the given length exactly match this connection id bytes.
     */
    public boolean matches(byte[] buffer, int offset, int length) {
        return matches(ByteBuffer.wrap(buffer, offset, length));
    }

    @Override
    public int compareTo(QuicConnectionId o) {
        return this.compareBytes(o.asReadOnlyBuffer());
    }


    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuicConnectionId that) {
            return compareTo(that) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return asReadOnlyBuffer().hashCode();
    }

    /**
     * {@return an hexadecimal string representing this connection id bytes,
     * as returned by {@code HexFormat.of().formatHex(getBytes())}}
     */
    public String toHexString() {
        return HexFormat.of().formatHex(getBytes());
    }

}

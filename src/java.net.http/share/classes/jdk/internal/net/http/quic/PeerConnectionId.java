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
package jdk.internal.net.http.quic;

import java.nio.ByteBuffer;
import java.util.HexFormat;


/**
 * A free-form connection ID to wrap the connection ID bytes
 * sent by the peer.
 * Client and server might impose some structure on the
 * connection ID bytes. For instance, they might choose to
 * encode the connection ID length in the connection ID bytes.
 * This class makes no assumption on the structure of the
 * connection id bytes.
 */
public final class PeerConnectionId extends QuicConnectionId {
    private final byte[] statelessResetToken;

    /**
     * A new {@link QuicConnectionId} represented by the given bytes.
     * @param connId The connection ID bytes.
     */
    public PeerConnectionId(final byte[] connId) {
        super(ByteBuffer.wrap(connId.clone()));
        this.statelessResetToken = null;
    }

    /**
     * A new {@link QuicConnectionId} represented by the given bytes.
     * @param connId The connection ID bytes.
     * @param statelessResetToken The stateless reset token to be associated with this connection id.
     *                            Can be null.
     * @throws IllegalArgumentException If the {@code statelessResetToken} is non-null and if its
     *                                  length isn't 16 bytes
     *
     */
    public PeerConnectionId(final ByteBuffer connId, final byte[] statelessResetToken) {
        super(cloneBuffer(connId));
        if (statelessResetToken != null) {
            if (statelessResetToken.length != 16) {
                throw new IllegalArgumentException("Invalid stateless reset token length "
                        + statelessResetToken.length);
            }
            this.statelessResetToken = statelessResetToken.clone();
        } else {
            this.statelessResetToken = null;
        }
    }

    private static ByteBuffer cloneBuffer(ByteBuffer src) {
        final byte[] idBytes = new byte[src.remaining()];
        src.get(idBytes);
        return ByteBuffer.wrap(idBytes);
    }

    /**
     * {@return the stateless reset token associated with this connection id. returns null if no
     * token exists}
     */
    public byte[] getStatelessResetToken() {
        return this.statelessResetToken == null ? null : this.statelessResetToken.clone();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(length:" + length() + ')';
    }
}

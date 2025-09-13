/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.frames.NewConnectionIDFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.RetireConnectionIDFrame;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.quic.QuicTransportException;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import static jdk.internal.net.quic.QuicTransportErrors.PROTOCOL_VIOLATION;

/**
 * Manages the connection ids advertised by the local endpoint of a connection.
 * - Produces outgoing NEW_CONNECTION_ID frames,
 * - handles incoming RETIRE_CONNECTION_ID frames,
 * - registers produced connection IDs with the QuicEndpoint
 * Handshake connection ID is created and registered by QuicConnection.
 */
final class LocalConnIdManager {
    private final Logger debug;
    private final QuicConnectionImpl connection;
    private long nextConnectionIdSequence;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean closed; // when true, no more connection IDs are registered

    // the connection ids (there can be more than one) with which the endpoint identifies this connection.
    // the key of this Map is a (RFC defined) sequence number for the connection id
    private final NavigableMap<Long, QuicConnectionId> localConnectionIds =
            Collections.synchronizedNavigableMap(new TreeMap<>());

    LocalConnIdManager(final QuicConnectionImpl connection, final String dbTag,
                       QuicConnectionId handshakeConnectionId) {
        this.debug = Utils.getDebugLogger(() -> dbTag);
        this.connection = connection;
        this.localConnectionIds.put(nextConnectionIdSequence++, handshakeConnectionId);
    }

    private QuicConnectionId newConnectionId() {
        return connection.endpoint().idFactory().newConnectionId();

    }

    private byte[] statelessTokenFor(QuicConnectionId cid) {
        return connection.endpoint().idFactory().statelessTokenFor(cid);
    }

    void handleRetireConnectionIdFrame(final QuicConnectionId incomingPacketDestConnId,
                                       final QuicPacket.PacketType packetType,
                                       final RetireConnectionIDFrame retireFrame)
            throws QuicTransportException {
        if (debug.on()) {
            debug.log("Received RETIRE_CONNECTION_ID frame: %s", retireFrame);
        }
        final QuicConnectionId toRetire;
        lock.lock();
        try {
            final long seqNumber = retireFrame.sequenceNumber();
            if (seqNumber >= nextConnectionIdSequence) {
                // RFC-9000, section 19.16: Receipt of a RETIRE_CONNECTION_ID frame containing a
                // sequence number greater than any previously sent to the peer MUST be treated
                // as a connection error of type PROTOCOL_VIOLATION
                throw new QuicTransportException("Invalid sequence number " + seqNumber
                        + " in RETIRE_CONNECTION_ID frame",
                        packetType.keySpace().orElse(null),
                        retireFrame.getTypeField(), PROTOCOL_VIOLATION);
            }
            toRetire = this.localConnectionIds.get(seqNumber);
            if (toRetire == null) {
                return;
            }
            if (toRetire.equals(incomingPacketDestConnId)) {
                // RFC-9000, section 19.16: The sequence number specified in a RETIRE_CONNECTION_ID
                // frame MUST NOT refer to the Destination Connection ID field of the packet in which
                // the frame is contained. The peer MAY treat this as a connection error of type
                // PROTOCOL_VIOLATION.
                throw new QuicTransportException("Invalid connection id in RETIRE_CONNECTION_ID frame",
                        packetType.keySpace().orElse(null),
                        retireFrame.getTypeField(), PROTOCOL_VIOLATION);
            }
            // forget this id from our local store
            this.localConnectionIds.remove(seqNumber);
            this.connection.endpoint().removeConnectionId(toRetire, connection);
        } finally {
            lock.unlock();
        }
        if (debug.on()) {
            debug.log("retired connection id " + toRetire);
        }
    }

    public QuicFrame nextFrame(int remaining) {
        if (localConnectionIds.size() >= 2) {
            return null;
        }
        int cidlen = connection.endpoint().idFactory().connectionIdLength();
        if (cidlen == 0) {
            return null;
        }
        // frame:
        // type - 1 byte
        // sequence number - var int
        // retire prior to - 1 byte (always zero)
        // connection id: <length> + 1 byte
        // stateless reset token - 16 bytes
        int len = 19 + cidlen + VariableLengthEncoder.getEncodedSize(nextConnectionIdSequence);
        if (len > remaining) {
            return null;
        }
        NewConnectionIDFrame newCidFrame;
        QuicConnectionId cid = newConnectionId();
        byte[] token = statelessTokenFor(cid);
        lock.lock();
        try {
            if (closed) return null;
            newCidFrame = new NewConnectionIDFrame(nextConnectionIdSequence++, 0,
                    cid.asReadOnlyBuffer(), ByteBuffer.wrap(token));
            this.localConnectionIds.put(newCidFrame.sequenceNumber(), cid);
            this.connection.endpoint().addConnectionId(cid, connection);
            if (debug.on()) {
                debug.log("Sending NEW_CONNECTION_ID frame");
            }
            return newCidFrame;
        } finally {
            lock.unlock();
        }
    }

    public List<QuicConnectionId> connectionIds() {
        lock.lock();
        try {
            // copy to avoid ConcurrentModificationException
            return List.copyOf(localConnectionIds.values());
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        closed = true;
        lock.unlock();
    }
}

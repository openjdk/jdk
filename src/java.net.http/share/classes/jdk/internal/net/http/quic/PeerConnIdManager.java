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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.frames.NewConnectionIDFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.RetireConnectionIDFrame;
import jdk.internal.net.http.quic.packets.InitialPacket;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import static jdk.internal.net.http.quic.QuicConnectionId.MAX_CONNECTION_ID_LENGTH;
import static jdk.internal.net.quic.QuicTransportErrors.PROTOCOL_VIOLATION;

/**
 * Manages the connection ids advertized by a peer of a (client) connection.
 * The implementation in this class is only applicable for client connections
 */
final class PeerConnIdManager {
    private final Logger debug;
    private final QuicConnectionImpl connection;
    private final String logTag;

    private enum State {
        INITIAL_PKT_NOT_RECEIVED_FROM_PEER,
        RETRY_PKT_RECEIVED_FROM_PEER,
        PEER_CONN_ID_FINALIZED
    }

    private volatile State state = State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER;

    private QuicConnectionId clientSelectedDestConnId;
    private QuicConnectionId peerDecidedRetryConnId;
    // the connection ids (there can be more than one) with which the peer identifies this connection.
    // the key of this Map is a (RFC defined) sequence number for the connection id
    private final NavigableMap<Long, PeerConnectionId> peerConnectionIds =
            Collections.synchronizedNavigableMap(new TreeMap<>());
    // the largest retirePriorTo value received across multiple NEW_CONNECTION_ID frames
    private volatile long largestReceivedRetirePriorTo = -1; // -1 implies none received so far

    PeerConnIdManager(final QuicConnectionImpl connection, final String dbTag) {
        if (!connection.isClientConnection()) {
            throw new IllegalArgumentException("PeerConnIdManager isn't meant for" +
                    " server connection " + connection);
        }
        this.debug = Utils.getDebugLogger(() -> dbTag);
        this.logTag = connection.logTag();
        this.connection = connection;
    }

    void originalDestConnId(final QuicConnectionId peerConnId) {
        final var st = this.state;
        if (st != State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER) {
            throw new IllegalStateException("Cannot associate a client selected peer id" +
                    " in current state " + st);
        }
        this.clientSelectedDestConnId = peerConnId;
    }

    QuicConnectionId originalDestConnId() {
        final var id = this.clientSelectedDestConnId;
        if (id == null) {
            throw new IllegalArgumentException("Original (peer) connection id not yet set");
        }
        return id;
    }

    void retryConnId(final QuicConnectionId peerConnId) {
        final var st = this.state;
        if (st != State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER) {
            throw new IllegalStateException("Cannot associate a peer id, from retry packet," +
                    " in current state " + st);
        }
        this.peerDecidedRetryConnId = peerConnId;
        this.state = State.RETRY_PKT_RECEIVED_FROM_PEER;
    }

    /**
     * Returns the connectionId the server included in the Source Connection ID field of a
     * Retry packet. May be null.
     *
     * @return the connection id sent in the server's retry packet
     */
    QuicConnectionId retryConnId() {
        return this.peerDecidedRetryConnId;
    }

    /**
     * The peer in its INITIAL packet would have sent a connection id representing itself. That
     * connection id may not be the same that we might have sent in the INITIAL packet. If it isn't
     * the same, then we switch the peer connection id, that we keep track off, to the one that
     * the peer has chosen.
     *
     * @param initialPacket the INITIAL packet from the peer
     */
    void finalizeHandshakePeerConnId(final InitialPacket initialPacket) throws QuicTransportException {
        final QuicConnectionId sourceId = initialPacket.sourceId();
        final var st = this.state;
        if (st == State.PEER_CONN_ID_FINALIZED) {
            // we have already finalized the peer connection id, through a previous INITIAL
            // packet receipt (there can be more than one INITIAL packets).
            // now we just verify that this INITIAL packet too has the finalized peer connection
            // id and if it doesn't then we throw an exception
            final QuicConnectionId handshakePeerConnId = this.peerConnectionIds.get(0L);
            assert handshakePeerConnId != null : "Handshake peer connection id is unavailable";
            if (!handshakePeerConnId.equals(sourceId)) {
                throw new QuicTransportException("Invalid source connection id in INITIAL packet",
                        QuicTLSEngine.KeySpace.INITIAL, 0, PROTOCOL_VIOLATION);
            }
            return;
        }
        // this is the first INITIAL packet from the peer, so we finalize the peer connection id
        final PeerConnectionId handshakePeerConnId = new PeerConnectionId(sourceId.getBytes());
        // at this point we have either switched to a new peer connection id (chosen by the peer)
        // or have agreed to use the one we chose for the peer. In either case, we register this
        // as the handshake peer connection id with sequence number 0.
        // RFC-9000, section 5.1.1: The initial connection ID issued by an endpoint is sent in
        // the Source Connection ID field of the long packet header during the handshake.
        // The sequence number of the initial connection ID is 0.
        this.peerConnectionIds.put(0L, handshakePeerConnId);
        this.state = State.PEER_CONN_ID_FINALIZED;
        if (debug.on()) {
            debug.log("scid: %s finalized handshake peerConnectionId as: %s",
                    connection.localConnectionId().toHexString(),
                    handshakePeerConnId.toHexString());
        }
    }

    void handlePreferredAddress(final ByteBuffer preferredConnId,
                                final byte[] preferredStatelessResetToken) {
        final PeerConnectionId peerConnId = new PeerConnectionId(preferredConnId,
                preferredStatelessResetToken);
        // keep track of this peer connection id
        // RFC-9000, section 5.1.1:  If the preferred_address transport parameter is sent,
        // the sequence number of the supplied connection ID is 1
        this.peerConnectionIds.put(1L, peerConnId);
    }

    void handshakeStatelessResetToken(final byte[] statelessResetToken) {
        final QuicConnectionId handshakeConnId = this.peerConnectionIds.get(0L);
        if (handshakeConnId == null) {
            throw new IllegalStateException("No handshake peer connection available");
        }
        // recreate the conn id with the stateless token
        this.peerConnectionIds.put(0L, new PeerConnectionId(handshakeConnId.asReadOnlyBuffer(),
                statelessResetToken));
    }

    QuicConnectionId getPeerConnId() {
        final var st = this.state;
        return switch (st) {
            case INITIAL_PKT_NOT_RECEIVED_FROM_PEER -> clientSelectedDestConnId;
            case RETRY_PKT_RECEIVED_FROM_PEER -> peerDecidedRetryConnId;
            case PEER_CONN_ID_FINALIZED -> {
                final Map.Entry<Long, PeerConnectionId> entry = this.peerConnectionIds.firstEntry();
                final QuicConnectionId connId = entry == null ? null : entry.getValue();
                if (connId == null) {
                    throw new IllegalStateException("No peer connection id available for connection "
                            + connection);
                }
                yield connId;
            }
        };
    }

    private QuicConnectionId getPeerConnId(final long sequenceNum) {
        return this.peerConnectionIds.get(sequenceNum);
    }

    void handleNewConnectionIdFrame(final QuicPacket.PacketType packetType,
                                    final NewConnectionIDFrame newCid)
            throws QuicTransportException, IOException {
        if (debug.on()) {
            debug.log("Received NEW_CONNECTION_ID frame: %s", newCid);
        }
        final long sequenceNumber = newCid.sequenceNumber();
        assert sequenceNumber >= 0 : "negative sequence number disallowed in new connection id frame";
        final long retirePriorTo = newCid.retirePriorTo();
        if (retirePriorTo > sequenceNumber) {
            // RFC 9000, section 19.15: Receiving a value in the Retire Prior To field that is greater
            // than that in the Sequence Number field MUST be treated as a connection error of
            // type FRAME_ENCODING_ERROR
            throw new QuicTransportException("Invalid retirePriorTo " + retirePriorTo,
                    packetType.keySpace().orElse(null),
                    newCid.getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        final ByteBuffer connectionId = newCid.connectionId();
        final int connIdLength = connectionId.remaining();
        if (connIdLength < 1 || connIdLength > MAX_CONNECTION_ID_LENGTH) {
            // RFC-9000, section 19.15: Values less than 1 and greater than 20 are invalid and
            // MUST be treated as a connection error of type FRAME_ENCODING_ERROR
            throw new QuicTransportException("Invalid connection id length " + connIdLength,
                    packetType.keySpace().orElse(null),
                    newCid.getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        final ByteBuffer statelessResetToken = newCid.statelessResetToken();
        assert statelessResetToken.remaining() == QuicConnectionImpl.RESET_TOKEN_LENGTH;
        // see if we have received any connection ids for this same sequence number.
        // this is possible if the packet containing the new connection id frame was retransmitted.
        // the connection id for such a (duplicate) sequence number is expected to be the same.
        // RFC-9000, section 19.15: if a sequence number is used for different connection IDs,
        // the endpoint MAY treat that receipt as a connection error of type PROTOCOL_VIOLATION
        final QuicConnectionId previousConnIdForSeqNum = getPeerConnId(sequenceNumber);
        if (previousConnIdForSeqNum != null) {
            if (previousConnIdForSeqNum.matches(connectionId)) {
                // frame with same sequence number and connection id, probably a retransmission.
                // ignore this frame
                if (Log.trace()) {
                    Log.logTrace("{0} Ignoring (duplicate) new connection id frame with" +
                            " sequence number {1}", logTag, sequenceNumber);
                }
                if (debug.on()) {
                    debug.log("Ignoring (duplicate) new connection id frame with" +
                            " sequence number %d", sequenceNumber);
                }
                return;
            }
            // mismatch, throw protocol violation error
            throw new QuicTransportException("Invalid connection id in (duplicated)" +
                    " new connection id frame with sequence number " + sequenceNumber,
                    packetType.keySpace().orElse(null),
                    newCid.getTypeField(), PROTOCOL_VIOLATION);
        }
        final QuicEndpoint endpoint = this.connection.endpoint();
        final List<RetireConnectionIDFrame> retireConnIdFrames = retirePriorTo(retirePriorTo);
        final long numCurrentActivePeerConnIds = this.peerConnectionIds.size();
        if (numCurrentActivePeerConnIds == this.connection.getLocalActiveConnIDLimit()) {
            // even after removing any connection ids to retire, we have reached the maximum active
            // conn id limit that we advertised to our peer
            // RFC-9000, section 5.1.1: After processing a NEW_CONNECTION_ID frame and adding and
            // retiring active connection IDs, if the number of active connection IDs exceeds
            // the value advertised in its active_connection_id_limit transport parameter,
            // an endpoint MUST close the connection with an error of type CONNECTION_ID_LIMIT_ERROR
            throw new QuicTransportException("Active connection id limit reached",
                    packetType.keySpace().orElse(null), newCid.getTypeField(),
                    QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR);
        }
        final byte[] statelessResetTokenBytes = new byte[QuicConnectionImpl.RESET_TOKEN_LENGTH];
        statelessResetToken.get(statelessResetTokenBytes);
        // link the peer issued stateless reset token to this connection
        endpoint.associateStatelessResetToken(statelessResetTokenBytes, this.connection);
        // we first retire and then add new connection id. this ordering is necessary and is
        // explained in the RFC.
        // RFC-9000, section 5.1.2: Upon receipt of an increased Retire Prior To field, the peer
        // MUST stop using the corresponding connection IDs and retire them with
        // RETIRE_CONNECTION_ID frames before adding the newly provided connection ID to the
        // set of active connection IDs.
        if (retireConnIdFrames != null) {
            if (debug.on()) {
                debug.log("Sending %d RETIRE_CONNECTION_ID frame(s) to retire prior to %d",
                        retireConnIdFrames.size(), retirePriorTo);
            }
            for (QuicFrame f : retireConnIdFrames) {
                this.connection.sendFrame(f);
            }
            // TODO: implement the RFC-9000, section 5.1.2 part which states:
            // An endpoint SHOULD limit the number of connection IDs it has retired locally for
            // which RETIRE_CONNECTION_ID frames have not yet been acknowledged....
        }
        final PeerConnectionId newPeerConnId = new PeerConnectionId(connectionId, statelessResetTokenBytes);
        final var previous = this.peerConnectionIds.putIfAbsent(sequenceNumber, newPeerConnId);
        assert previous == null : "A peer connection id already exists for sequence number "
                + sequenceNumber;
    }

    void handleRetireConnectionIdFrame(final QuicConnectionId incomingPacketDestConnId,
                                       final QuicPacket.PacketType packetType,
                                       final RetireConnectionIDFrame retireFrame)
            throws QuicTransportException {
        if (debug.on()) {
            debug.log("Received RETIRE_CONNECTION_ID frame: %s", retireFrame);
        }
        final long seqNumber = retireFrame.sequenceNumber();
        final Map.Entry<Long, PeerConnectionId> largest = this.peerConnectionIds.lastEntry();
        if (largest != null && seqNumber > largest.getKey()) {
            // RFC-9000, section 19.16: Receipt of a RETIRE_CONNECTION_ID frame containing a
            // sequence number greater than any previously sent to the peer MUST be treated
            // as a connection error of type PROTOCOL_VIOLATION
            throw new QuicTransportException("Invalid sequence number " + seqNumber
                    + " in RETIRE_CONNECTION_ID frame",
                    packetType.keySpace().orElse(null),
                    retireFrame.getTypeField(), PROTOCOL_VIOLATION);
        }
        final PeerConnectionId toRetire = this.peerConnectionIds.get(seqNumber);
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
        this.peerConnectionIds.remove(seqNumber);
        // the QuicEndpoint only stores local connection ids and peer issued stateless reset tokens.
        // So when we are retiring a connection id, we remove the stateless reset token that the
        // endpoint is holding on to.
        final byte[] statelessResetToken = toRetire.getStatelessResetToken();
        if (statelessResetToken != null) {
            this.connection.endpoint().forgetStatelessResetToken(statelessResetToken);
        }
        if (debug.on()) {
            debug.log("retired connection id " + toRetire);
        }
    }

    private List<RetireConnectionIDFrame> retirePriorTo(final long priorTo) {
        // RFC-9000, section 19.15: A receiver MUST ignore any Retire Prior To fields that do not
        // increase the largest received Retire Prior To value
        if (priorTo <= largestReceivedRetirePriorTo) {
            // nothing to do
            return Collections.emptyList();
        }
        this.largestReceivedRetirePriorTo = priorTo;
        // remove/retire (in preparation of sending a RETIRE_CONNECTION_ID frames)
        // TODO: this needs a better data structure
        final Iterator<Map.Entry<Long, PeerConnectionId>> entries = this.peerConnectionIds
                .entrySet().iterator();
        List<RetireConnectionIDFrame> retireConnIdFrames = new ArrayList<>();
        while (entries.hasNext()) {
            final var entry = entries.next();
            final long seqNumToRetire = entry.getKey();
            if (seqNumToRetire < priorTo) {
                entries.remove();
                retireConnIdFrames.add(new RetireConnectionIDFrame(seqNumToRetire));
                // Note that the QuicEndpoint only stores local connection ids and doesn't store peer
                // connection ids. It does however store the peer-issued stateless reset token of a
                // peer connection id, so we let the endpoint know that the stateless reset token needs
                // to be forgotten since the corresponding peer connection id is being retired
                final byte[] resetTokenToForget = entry.getValue().getStatelessResetToken();
                if (resetTokenToForget != null) {
                    this.connection.endpoint().forgetStatelessResetToken(resetTokenToForget);
                }
            }
        }
        return retireConnIdFrames;
    }
}

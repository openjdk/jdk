/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.frames.NewConnectionIDFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.RetireConnectionIDFrame;
import jdk.internal.net.http.quic.packets.InitialPacket;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import static jdk.internal.net.http.quic.QuicConnectionId.MAX_CONNECTION_ID_LENGTH;
import static jdk.internal.net.quic.QuicTransportErrors.PROTOCOL_VIOLATION;

/**
 * Manages the connection ids advertised by a peer of a connection.
 * - Handles incoming NEW_CONNECTION_ID frames,
 * - produces outgoing RETIRE_CONNECTION_ID frames,
 * - registers received stateless reset tokens with the QuicEndpoint
 * Additionally on the client side:
 * - handles incoming transport parameters (preferred_address, stateless_reset_token)
 * - stores original and retry peer IDs
 */
// TODO implement voluntary switching of connection IDs
final class PeerConnIdManager {
    private final Logger debug;
    private final QuicConnectionImpl connection;
    private final String logTag;
    private final boolean isClient;

    private enum State {
        INITIAL_PKT_NOT_RECEIVED_FROM_PEER,
        RETRY_PKT_RECEIVED_FROM_PEER,
        PEER_CONN_ID_FINALIZED
    }

    private volatile State state = State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER;

    private QuicConnectionId clientSelectedDestConnId;
    private QuicConnectionId peerDecidedRetryConnId;
    // sequence number of active connection ID
    private long activeConnIdSeq = -1;
    private QuicConnectionId activeConnId;

    // the connection ids (there can be more than one) with which the peer identifies this connection.
    // the key of this Map is a (RFC defined) sequence number for the connection id
    private final NavigableMap<Long, PeerConnectionId> peerConnectionIds =
            Collections.synchronizedNavigableMap(new TreeMap<>());
    // the connection id sequence numbers that we haven't received yet.
    // We need to know which sequence numbers are retired, and which are not assigned yet
    private final NavigableSet<Long> gaps =
            Collections.synchronizedNavigableSet(new TreeSet<>());
    // the connection id sequence numbers that are awaiting retirement.
    private final Queue<Long> toRetire = new ArrayDeque<>();
    // the largest retirePriorTo value received across NEW_CONNECTION_ID frames
    private volatile long largestReceivedRetirePriorTo = -1; // -1 implies none received so far
    // the largest sequenceNumber value received across NEW_CONNECTION_ID frames
    private volatile long largestReceivedSequenceNumber;
    private final ReentrantLock lock = new ReentrantLock();

    PeerConnIdManager(final QuicConnectionImpl connection, final String dbTag) {
        this.isClient = connection.isClientConnection();
        this.debug = Utils.getDebugLogger(() -> dbTag);
        this.logTag = connection.logTag();
        this.connection = connection;
    }

    /**
     * Save the client-selected original server connection ID
     *
     * @param peerConnId the client-selected original server connection ID
     */
    void originalServerConnId(final QuicConnectionId peerConnId) {
        lock.lock();
        try {
            final var st = this.state;
            if (st != State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER) {
                throw new IllegalStateException("Cannot associate a client selected peer id" +
                        " in current state " + st);
            }
            this.clientSelectedDestConnId = peerConnId;
            this.activeConnId = peerConnId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return the client-selected original server connection ID}
     */
    QuicConnectionId originalServerConnId() {
        lock.lock();
        try {
            final var id = this.clientSelectedDestConnId;
            if (id == null) {
                throw new IllegalArgumentException("Original (peer) connection id not yet set");
            }
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save the server-selected retry connection ID
     *
     * @param peerConnId the server-selected retry connection ID
     */
    void retryConnId(final QuicConnectionId peerConnId) {
        if (!isClient) {
            throw new IllegalStateException("Should not be used on the server");
        }
        lock.lock();
        try {
            final var st = this.state;
            if (st != State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER) {
                throw new IllegalStateException("Cannot associate a peer id, from retry packet," +
                        " in current state " + st);
            }
            this.peerDecidedRetryConnId = peerConnId;
            this.activeConnId = peerConnId;
            this.state = State.RETRY_PKT_RECEIVED_FROM_PEER;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the connectionId the server included in the Source Connection ID field of a
     * Retry packet. May be null.
     *
     * @return the connection id sent in the server's retry packet
     */
    QuicConnectionId retryConnId() {
        lock.lock();
        try {
            return this.peerDecidedRetryConnId;
        } finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
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
            this.activeConnIdSeq = 0;
            this.activeConnId = handshakePeerConnId;
            if (debug.on()) {
                debug.log("scid: %s finalized handshake peerConnectionId as: %s",
                        connection.localConnectionId().toHexString(),
                        handshakePeerConnId.toHexString());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save the connection ID from the preferred address QUIC transport parameter
     *
     * @param preferredConnId              preferred connection ID
     * @param preferredStatelessResetToken preferred stateless reset token
     */
    void handlePreferredAddress(final ByteBuffer preferredConnId,
                                final byte[] preferredStatelessResetToken) {
        if (!isClient) {
            throw new IllegalStateException("Should not be used on the server");
        }
        lock.lock();
        try {
            final PeerConnectionId peerConnId = new PeerConnectionId(preferredConnId,
                    preferredStatelessResetToken);
            // keep track of this peer connection id
            // RFC-9000, section 5.1.1:  If the preferred_address transport parameter is sent,
            // the sequence number of the supplied connection ID is 1
            assert largestReceivedSequenceNumber == 0;
            this.peerConnectionIds.put(1L, peerConnId);
            largestReceivedSequenceNumber = 1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save the stateless reset token QUIC transport parameter
     *
     * @param statelessResetToken stateless reset token
     */
    void handshakeStatelessResetToken(final byte[] statelessResetToken) {
        if (!isClient) {
            throw new IllegalStateException("Should not be used on the server");
        }
        lock.lock();
        try {
            final QuicConnectionId handshakeConnId = this.peerConnectionIds.get(0L);
            if (handshakeConnId == null) {
                throw new IllegalStateException("No handshake peer connection available");
            }
            // recreate the conn id with the stateless token
            this.peerConnectionIds.put(0L, new PeerConnectionId(handshakeConnId.asReadOnlyBuffer(),
                    statelessResetToken));
            // register with the endpoint
            connection.endpoint().associateStatelessResetToken(statelessResetToken, connection);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return the list of stateless reset tokens associated with active peer connection IDs}
     */
    public List<byte[]> activeResetTokens() {
        lock.lock();
        try {
            // we only support one active connection ID at the time
            PeerConnectionId cid = peerConnectionIds.get(activeConnIdSeq);
            byte[] statelessResetToken = null;
            if (cid != null) {
                statelessResetToken = cid.getStatelessResetToken();
            }
            if (statelessResetToken != null) {
                return List.of(statelessResetToken);
            } else {
                return List.of();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@return the active peer connection ID}
     */
    QuicConnectionId getPeerConnId() {
        lock.lock();
        try {
            if (activeConnIdSeq < largestReceivedRetirePriorTo) {
                // stop using the old connection ID
                switchConnectionId();
            }
            return activeConnId;
        } finally {
            lock.unlock();
        }
    }

    private QuicConnectionId getPeerConnId(final long sequenceNum) {
        assert lock.isHeldByCurrentThread();
        return this.peerConnectionIds.get(sequenceNum);
    }

    /**
     * Process the incoming NEW_CONNECTION_ID frame.
     *
     * @param newCid the NEW_CONNECTION_ID frame
     * @throws QuicTransportException if the frame violates the protocol
     */
    void handleNewConnectionIdFrame(final NewConnectionIDFrame newCid)
            throws QuicTransportException {
        if (debug.on()) {
            debug.log("Received NEW_CONNECTION_ID frame: %s", newCid);
        }
        // pre-checks
        final long sequenceNumber = newCid.sequenceNumber();
        assert sequenceNumber >= 0 : "negative sequence number disallowed in new connection id frame";
        final long retirePriorTo = newCid.retirePriorTo();
        if (retirePriorTo > sequenceNumber) {
            // RFC 9000, section 19.15: Receiving a value in the Retire Prior To field that is greater
            // than that in the Sequence Number field MUST be treated as a connection error of
            // type FRAME_ENCODING_ERROR
            throw new QuicTransportException("Invalid retirePriorTo " + retirePriorTo,
                    QuicTLSEngine.KeySpace.ONE_RTT,
                    newCid.getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        final ByteBuffer connectionId = newCid.connectionId();
        final int connIdLength = connectionId.remaining();
        if (connIdLength < 1 || connIdLength > MAX_CONNECTION_ID_LENGTH) {
            // RFC-9000, section 19.15: Values less than 1 and greater than 20 are invalid and
            // MUST be treated as a connection error of type FRAME_ENCODING_ERROR
            throw new QuicTransportException("Invalid connection id length " + connIdLength,
                    QuicTLSEngine.KeySpace.ONE_RTT,
                    newCid.getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        final ByteBuffer statelessResetToken = newCid.statelessResetToken();
        assert statelessResetToken.remaining() == QuicConnectionImpl.RESET_TOKEN_LENGTH;
        lock.lock();
        try {
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
                        QuicTLSEngine.KeySpace.ONE_RTT,
                        newCid.getTypeField(), PROTOCOL_VIOLATION);
            }
            if ((sequenceNumber <= largestReceivedSequenceNumber && !gaps.contains(sequenceNumber))
                    || sequenceNumber < largestReceivedRetirePriorTo) {
                if (Log.trace()) {
                    Log.logTrace("{0} Ignoring (retired) new connection id frame with" +
                            " sequence number {1}", logTag, sequenceNumber);
                }
                if (debug.on()) {
                    debug.log("Ignoring (retired) new connection id frame with" +
                            " sequence number %d", sequenceNumber);
                }
                return;
            }
            long numConnIdsToAdd = Math.max(sequenceNumber - largestReceivedSequenceNumber, 0);
            final long numCurrentActivePeerConnIds = this.peerConnectionIds.size() + this.gaps.size();
            // we can temporarily store up to 3x the active connection ID limit,
            // including active and retired IDs.
            if (numCurrentActivePeerConnIds + numConnIdsToAdd + toRetire.size()
                    > 3 * this.connection.getLocalActiveConnIDLimit()) {
                // RFC-9000, section 5.1.1: After processing a NEW_CONNECTION_ID frame and adding and
                // retiring active connection IDs, if the number of active connection IDs exceeds
                // the value advertised in its active_connection_id_limit transport parameter,
                // an endpoint MUST close the connection with an error of type CONNECTION_ID_LIMIT_ERROR
                throw new QuicTransportException("Connection id limit reached",
                        QuicTLSEngine.KeySpace.ONE_RTT, newCid.getTypeField(),
                        QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR);
            }
            // end pre-checks
            // if we reached here, the number of connection IDs is less than twice the active limit.
            // Insert gaps for the sequence numbers we haven't seen yet
            insertGaps(sequenceNumber);
            // Update the list of sequence numbers to retire
            retirePriorTo(retirePriorTo);
            // insert the new connection ID
            final byte[] statelessResetTokenBytes = new byte[QuicConnectionImpl.RESET_TOKEN_LENGTH];
            statelessResetToken.get(statelessResetTokenBytes);
            final PeerConnectionId newPeerConnId = new PeerConnectionId(connectionId, statelessResetTokenBytes);
            final var previous = this.peerConnectionIds.putIfAbsent(sequenceNumber, newPeerConnId);
            assert previous == null : "A peer connection id already exists for sequence number "
                    + sequenceNumber;
            // post-checks
            // now we can accurately check the number of active and retired connection IDs
            if (peerConnectionIds.size() + gaps.size()
                    > this.connection.getLocalActiveConnIDLimit()) {
                // RFC-9000, section 5.1.1: After processing a NEW_CONNECTION_ID frame and adding and
                // retiring active connection IDs, if the number of active connection IDs exceeds
                // the value advertised in its active_connection_id_limit transport parameter,
                // an endpoint MUST close the connection with an error of type CONNECTION_ID_LIMIT_ERROR
                throw new QuicTransportException("Active connection id limit reached",
                        QuicTLSEngine.KeySpace.ONE_RTT, newCid.getTypeField(),
                        QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR);
            }
            if (toRetire.size() > 2 * this.connection.getLocalActiveConnIDLimit()) {
                // RFC-9000, section 5.1.2:
                // An endpoint SHOULD limit the number of connection IDs it has retired locally for
                // which RETIRE_CONNECTION_ID frames have not yet been acknowledged.
                // An endpoint SHOULD allow for sending and tracking a number
                // of RETIRE_CONNECTION_ID frames of at least twice the value
                // of the active_connection_id_limit transport parameter
                throw new QuicTransportException("Retired connection id limit reached: " + toRetire,
                        QuicTLSEngine.KeySpace.ONE_RTT, newCid.getTypeField(),
                        QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR);
            }
            if (this.largestReceivedRetirePriorTo < retirePriorTo) {
                this.largestReceivedRetirePriorTo = retirePriorTo;
            }
            if (this.largestReceivedSequenceNumber < sequenceNumber) {
                this.largestReceivedSequenceNumber = sequenceNumber;
            }
        } finally {
            lock.unlock();
        }
    }

    private void switchConnectionId() {
        assert lock.isHeldByCurrentThread();
        // the caller is expected to retire the active connection id prior to calling this
        assert !peerConnectionIds.containsKey(activeConnIdSeq);
        Map.Entry<Long, PeerConnectionId> entry = peerConnectionIds.ceilingEntry(largestReceivedRetirePriorTo);
        activeConnIdSeq = entry.getKey();
        activeConnId = entry.getValue();
        // link the peer issued stateless reset token to this connection
        final QuicEndpoint endpoint = this.connection.endpoint();
        endpoint.associateStatelessResetToken(entry.getValue().getStatelessResetToken(), this.connection);

        if (Log.trace()) {
            Log.logTrace("{0} Switching to connection ID {1}", logTag, activeConnIdSeq);
        }
        if (debug.on()) {
            debug.log("Switching to connection ID %d", activeConnIdSeq);
        }
    }

    private void insertGaps(long sequenceNumber) {
        assert lock.isHeldByCurrentThread();
        for (long i = largestReceivedSequenceNumber + 1; i < sequenceNumber; i++) {
            gaps.add(i);
        }
    }

    private void retirePriorTo(final long priorTo) {
        assert lock.isHeldByCurrentThread();
        // remove/retire (in preparation of sending a RETIRE_CONNECTION_ID frames)
        for (Iterator<Map.Entry<Long, PeerConnectionId>> iterator = peerConnectionIds.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Long, PeerConnectionId> entry = iterator.next();
            final long seqNumToRetire = entry.getKey();
            if (seqNumToRetire >= priorTo) {
                break;
            }
            iterator.remove();
            toRetire.add(seqNumToRetire);
            // Note that the QuicEndpoint only stores local connection ids and doesn't store peer
            // connection ids. It does however store the peer-issued stateless reset token of a
            // peer connection id, so we let the endpoint know that the stateless reset token needs
            // to be forgotten since the corresponding peer connection id is being retired
            final byte[] resetTokenToForget = entry.getValue().getStatelessResetToken();
            if (resetTokenToForget != null) {
                this.connection.endpoint().forgetStatelessResetToken(resetTokenToForget);
            }
        }
        for (Iterator<Long> iterator = gaps.iterator(); iterator.hasNext(); ) {
            Long gap = iterator.next();
            if (gap >= priorTo) {
                return;
            }
            iterator.remove();
            toRetire.add(gap);
        }
    }

    /**
     * Produce a queued RETIRE_CONNECTION_ID frame, if it fits in the packet
     *
     * @param remaining bytes remaining in the packet
     * @return a RetireConnectionIdFrame, or null if none is queued or remaining is too low
     */
    public QuicFrame nextFrame(int remaining) {
        // retire connection id:
        // type - 1 byte
        // sequence number - var int
        if (remaining < 9) {
            return null;
        }
        lock.lock();
        try {
            final Long seqNumToRetire = toRetire.poll();
            if (seqNumToRetire != null) {
                if (seqNumToRetire == activeConnIdSeq) {
                    // can't send this connection ID yet, we will send it in the next packet
                    toRetire.add(seqNumToRetire);
                    return null;
                }
                return new RetireConnectionIDFrame(seqNumToRetire);
            }
            return null;
        } finally {
            lock.unlock();
        }
    }
}

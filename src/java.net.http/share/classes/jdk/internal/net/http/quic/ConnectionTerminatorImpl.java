/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.quic.QuicConnectionImpl.HandshakeFlow;
import jdk.internal.net.http.quic.QuicConnectionImpl.ProtectionRecord;
import jdk.internal.net.http.quic.TerminationCause.AppLayerClose;
import jdk.internal.net.http.quic.TerminationCause.SilentTermination;
import jdk.internal.net.http.quic.TerminationCause.TransportError;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicTLSEngine.KeySpace;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import static jdk.internal.net.http.quic.QuicConnectionImpl.QuicConnectionState.CLOSED;
import static jdk.internal.net.http.quic.QuicConnectionImpl.QuicConnectionState.CLOSING;
import static jdk.internal.net.http.quic.QuicConnectionImpl.QuicConnectionState.DRAINING;
import static jdk.internal.net.http.quic.TerminationCause.appLayerClose;
import static jdk.internal.net.http.quic.TerminationCause.forSilentTermination;
import static jdk.internal.net.http.quic.TerminationCause.forTransportError;
import static jdk.internal.net.quic.QuicTransportErrors.NO_ERROR;

final class ConnectionTerminatorImpl implements ConnectionTerminator {

    private final QuicConnectionImpl connection;
    private final Logger debug;
    private final String logTag;
    private final AtomicReference<TerminationCause> terminationCause = new AtomicReference<>();
    private final CompletableFuture<TerminationCause> futureTC = new MinimalFuture<>();

    ConnectionTerminatorImpl(final QuicConnectionImpl connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.debug = connection.debug;
        this.logTag = connection.logTag();
    }

    @Override
    public void markActive() {
        this.connection.idleTimeoutManager.markActive();
    }

    @Override
    public boolean tryReserveForUse() {
        return this.connection.idleTimeoutManager.tryReserveForUse();
    }

    @Override
    public void appLayerMaxIdle(final Duration maxIdle, final Supplier<Boolean> trafficGenerationCheck) {
        this.connection.idleTimeoutManager.appLayerMaxIdle(maxIdle, trafficGenerationCheck);
    }

    @Override
    public void terminate(final TerminationCause cause) {
        Objects.requireNonNull(cause);
        try {
            doTerminate(cause);
        } catch (Throwable t) {
            // make sure we do fail the handshake CompletableFuture(s)
            // even when the connection termination itself failed. that way
            // the dependent CompletableFuture(s) tasks don't keep waiting forever
            failHandshakeCFs(t);
        }
    }

    TerminationCause getTerminationCause() {
        return this.terminationCause.get();
    }

    private void doTerminate(final TerminationCause cause) {
        final ConnectionCloseFrame frame;
        KeySpace keySpace;
        switch (cause) {
            case SilentTermination st -> {
                silentTerminate(st);
                return;
            }
            case TransportError te -> {
                frame = new ConnectionCloseFrame(te.getCloseCode(), te.frameType,
                        te.getPeerVisibleReason()); // 0x1c
                keySpace = te.keySpace;
            }
            case TerminationCause.InternalError ie -> {
                frame = new ConnectionCloseFrame(ie.getCloseCode(), 0,
                        ie.getPeerVisibleReason()); // 0x1c
                keySpace = null;
            }
            case AppLayerClose alc -> {
                // application layer triggered connection close
                frame = new ConnectionCloseFrame(alc.getCloseCode(),
                        alc.getPeerVisibleReason()); // 0x1d
                keySpace = null;
            }
        }
        if (keySpace == null) {
            // TODO: review this
            keySpace = connection.getTLSEngine().getCurrentSendKeySpace();
        }
        immediateClose(frame, keySpace, cause);
    }

    void incomingConnectionCloseFrame(final ConnectionCloseFrame frame) {
        Objects.requireNonNull(frame);
        if (debug.on()) {
            debug.log("Received close frame: %s", frame);
        }
        drain(frame);
    }

    void incomingStatelessReset() {
        // if local endpoint is a client, then our peer is a server
        final boolean peerIsServer = connection.isClientConnection();
        if (Log.errors()) {
            Log.logError("{0}: stateless reset from peer ({1})", connection.logTag(),
                    (peerIsServer ? "server" : "client"));
        }
        var label = "quic:" + connection.uniqueId();
        final SilentTermination st = forSilentTermination("stateless reset from peer ("
                + (peerIsServer ? "server" : "client") + ") on " + label);
        terminate(st);
    }

    /**
     * Called only when the connection is expected to be discarded without being required
     * to inform the peer.
     * Discards all state, no CONNECTION_CLOSE is sent, nor does the connection enter closing
     * or discarding state.
     */
    private void silentTerminate(final SilentTermination terminationCause) {
        // shutdown the idle timeout manager since we no longer bother with idle timeout
        // management for this connection
        connection.idleTimeoutManager.shutdown();
        // mark the connection state as closed (we don't enter closing or draining state
        // during silent termination)
        if (!markClosed(terminationCause)) {
            // previously already closed
            return;
        }
        if (Log.quic()) {
            Log.logQuic("{0} silently terminating connection due to: {1}",
                    logTag, terminationCause.getLogMsg());
        } else if (debug.on()) {
            debug.log("silently terminating connection due to: " + terminationCause.getLogMsg());
        }
        if (debug.on() || Log.quic()) {
            String message = connection.loggableState();
            if (message != null) {
                Log.logQuic("{0} connection state: {1}", logTag, message);
                debug.log("connection state: %s", message);
            }
        }
        failHandshakeCFs();
        // remove from the endpoint
        unregisterConnFromEndpoint();
        discardConnectionState();
        // terminate the streams
        connection.streams.terminate(terminationCause);
    }

    CompletableFuture<TerminationCause> futureTerminationCause() {
        return this.futureTC;
    }

    private void unregisterConnFromEndpoint() {
        final QuicEndpoint endpoint = this.connection.endpoint();
        if (endpoint == null) {
            // this can happen if the connection is being terminated before
            // an endpoint has been established (which is OK)
            return;
        }
        endpoint.removeConnection(this.connection);
    }

    private void immediateClose(final ConnectionCloseFrame closeFrame,
                                final KeySpace keySpace,
                                final TerminationCause terminationCause) {
        assert closeFrame != null : "connection close frame is null";
        assert keySpace != null : "keyspace is null";
        final String logMsg = terminationCause.getLogMsg();
        // if the connection has already been closed (for example: through silent termination)
        // then the local state of the connection is already discarded and thus
        // there's nothing more we can do with the connection.
        if (connection.stateHandle().isMarked(CLOSED)) {
            return;
        }
        // switch to closing state
        if (!markClosing(terminationCause)) {
            // has previously already gone into closing state
            return;
        }
        // shutdown the idle timeout manager since we no longer bother with idle timeout
        // management for a closing connection
        connection.idleTimeoutManager.shutdown();

        if (connection.stateHandle().draining()) {
            if (Log.quic()) {
                Log.logQuic("{0} skipping immediate close, since connection is already" +
                        " in draining state", logTag, logMsg);
            } else if (debug.on()) {
                debug.log("skipping immediate close, since connection is already" +
                        " in draining state");
            }
            // we are already (in the subsequent) draining state, no need to anything more
            return;
        }
        try {
            final String closeCodeHex = (terminationCause.isAppLayer() ? "(app layer) " : "") +
                    "0x" + Long.toHexString(closeFrame.errorCode());
            if (Log.quic()) {
                Log.logQuic("{0} entering closing state, code {1} - {2}", logTag, closeCodeHex, logMsg);
            } else if (debug.on()) {
                debug.log("entering closing state, code " + closeCodeHex + " - " + logMsg);
            }
            pushConnectionCloseFrame(keySpace, closeFrame);
        } catch (Exception e) {
            if (Log.errors()) {
                Log.logError("{0} removing connection from endpoint after failure to send" +
                        " CLOSE_CONNECTION: {1}", logTag, e);
            } else if (debug.on()) {
                debug.log("removing connection from endpoint after failure to send" +
                        " CLOSE_CONNECTION");
            }
            // we failed to send a CONNECTION_CLOSE frame. this implies that the QuicEndpoint
            // won't detect that the QuicConnectionImpl has transitioned to closing connection
            // and thus won't remap it to closing. we thus discard such connection from the
            // endpoint.
            unregisterConnFromEndpoint();
        }
        failHandshakeCFs();
        discardConnectionState();
        connection.streams.terminate(terminationCause);
        if (Log.quic()) {
            Log.logQuic("{0} connection has now transitioned to closing state", logTag);
        } else if (debug.on()) {
            debug.log("connection has now transitioned to closing state");
        }
    }

    private void drain(final ConnectionCloseFrame incomingFrame) {
        // if the connection has already been closed (for example: through silent termination)
        // then the local state of the connection is already discarded and thus
        // there's nothing more we can do with the connection.
        if (connection.stateHandle().isMarked(CLOSED)) {
            return;
        }
        final boolean isAppLayerClose = incomingFrame.variant();
        final String closeCodeString = isAppLayerClose ?
                "[app]" + connection.quicInstance().appErrorToString(incomingFrame.errorCode()) :
                QuicTransportErrors.toString(incomingFrame.errorCode());
        final String reason = incomingFrame.reasonString();
        final String peer = connection.isClientConnection() ? "server" : "client";
        final String msg = "Connection closed by " + peer + " peer: " +
                closeCodeString +
                (reason == null || reason.isEmpty() ? "" : (" " + reason));
        final TerminationCause terminationCause;
        if (isAppLayerClose) {
            terminationCause = appLayerClose(incomingFrame.errorCode(), msg)
                    .peerVisibleReason(reason);
        } else {
            terminationCause = forTransportError(incomingFrame.errorCode(), msg,
                    incomingFrame.errorFrameType())
                    .peerVisibleReason(reason);
        }
        // switch to draining state
        if (!markDraining(terminationCause)) {
            // has previously already gone into draining state
            return;
        }
        // shutdown the idle timeout manager since we no longer bother with idle timeout
        // management for a closing connection
        connection.idleTimeoutManager.shutdown();

        if (Log.quic()) {
            Log.logQuic("{0} entering draining state, {1}", logTag,
                    terminationCause.getLogMsg());
        } else if (debug.on()) {
            debug.log("entering draining state, "
                    + terminationCause.getLogMsg());
        }
        // RFC-9000, section 10.2.2:
        // An endpoint that receives a CONNECTION_CLOSE frame MAY send a single packet containing
        // a CONNECTION_CLOSE frame before entering the draining state, using a NO_ERROR code if
        // appropriate. An endpoint MUST NOT send further packets.
        // if we had previously marked our state as closing, then that implies
        // we would have already sent a connection close frame. we won't send
        // another when draining in such a case.
        if (markClosing(terminationCause)) {
            try {
                if (Log.quic()) {
                    Log.logQuic("{0} sending CONNECTION_CLOSE frame before entering draining state",
                            logTag);
                } else if (debug.on()) {
                    debug.log("sending CONNECTION_CLOSE frame before entering draining state");
                }
                final ConnectionCloseFrame outgoingFrame =
                        new ConnectionCloseFrame(NO_ERROR.code(), incomingFrame.getTypeField(), null);
                final KeySpace currentKeySpace = connection.getTLSEngine().getCurrentSendKeySpace();
                pushConnectionCloseFrame(currentKeySpace, outgoingFrame);
            } catch (Exception e) {
                // just log and ignore, since sending the CONNECTION_CLOSE when entering
                // draining state is optional
                if (Log.errors()) {
                    Log.logError(logTag + " Failed to send CONNECTION_CLOSE frame," +
                            " when entering draining state: {0}", e);
                } else if (debug.on()) {
                    debug.log("failed to send CONNECTION_CLOSE frame, when entering" +
                            " draining state: " + e);
                }
            }
        }
        failHandshakeCFs();
        discardConnectionState();
        connection.streams.terminate(terminationCause);
        if (Log.quic()) {
            Log.logQuic("{0} connection has now transitioned to draining state", logTag);
        } else if (debug.on()) {
            debug.log("connection has now transitioned to draining state");
        }
    }

    private void discardConnectionState() {
        // close packet spaces
        connection.packetNumberSpaces().close();
        // close the incoming packets buffered queue
        connection.closeIncoming();
    }

    private void failHandshakeCFs() {
        final TerminationCause tc = this.terminationCause.get();
        assert tc != null : "termination cause is null";
        failHandshakeCFs(tc.getCloseCause());
    }

    private void failHandshakeCFs(final Throwable cause) {
        final HandshakeFlow handshakeFlow = connection.handshakeFlow();
        handshakeFlow.failHandshakeCFs(cause);
    }

    private boolean markClosing(final TerminationCause terminationCause) {
        return mark(CLOSING, terminationCause);
    }

    private boolean markDraining(final TerminationCause terminationCause) {
        return mark(DRAINING, terminationCause);
    }

    private boolean markClosed(final TerminationCause terminationCause) {
        return mark(CLOSED, terminationCause);
    }

    private boolean mark(final int mask, final TerminationCause cause) {
        assert cause != null : "termination cause is null";
        final boolean causeSet = this.terminationCause.compareAndSet(null, cause);
        // first mark the state appropriately, before completing the futureTerminationCause
        // completable future, so that any dependent actions on the completable future
        // will see the right state
        final boolean marked = this.connection.stateHandle().mark(mask);
        if (causeSet) {
            this.futureTC.completeAsync(() -> cause, connection.quicInstance().executor());
        }
        return marked;
    }

    /**
     * CONNECTION_CLOSE frame is not congestion controlled (RFC-9002 section 3
     * and RFC-9000 section 12.4, table 3), nor is it queued or scheduled for sending.
     * This method constructs a {@link QuicPacket} containing the {@code frame} and immediately
     * {@link QuicConnectionImpl#pushDatagram(ProtectionRecord) pushes the datagram} through
     * the connection.
     *
     * @param keySpace the KeySpace to use for sending the packet
     * @param frame    the CONNECTION_CLOSE frame
     * @throws QuicKeyUnavailableException if the keys for the KeySpace aren't available
     * @throws QuicTransportException      for any QUIC transport exception when sending the packet
     */
    private void pushConnectionCloseFrame(final KeySpace keySpace,
                                          final ConnectionCloseFrame frame)
            throws QuicKeyUnavailableException, QuicTransportException {
        // ConnectionClose frame is allowed in Initial, Handshake, 0-RTT, 1-RTT spaces.
        // for Initial and Handshake space, the frame is expected to be of type 0x1c.
        // see RFC-9000, section 12.4, Table 3 for additional details
        final ConnectionCloseFrame toSend = switch (keySpace) {
            case ONE_RTT, ZERO_RTT -> frame;
            case INITIAL, HANDSHAKE -> {
                // RFC 9000 - section 10.2.3:
                // A CONNECTION_CLOSE of type 0x1d MUST be replaced by a CONNECTION_CLOSE
                // of type 0x1c when sending the frame in Initial or Handshake packets.
                // Otherwise, information about the application state might be revealed.
                // Endpoints MUST clear the value of the Reason Phrase field and SHOULD
                // use the APPLICATION_ERROR code when converting to a CONNECTION_CLOSE
                // of type 0x1c.
                yield frame.clearApplicationState();
            }
            default -> {
                throw new IllegalStateException("cannot send a connection close frame" +
                        " in keyspace: " + keySpace);
            }
        };
        final QuicPacket packet = connection.newQuicPacket(keySpace, List.of(toSend));
        final ProtectionRecord protectionRecord = ProtectionRecord.single(packet,
                connection::allocateDatagramForEncryption);
        // while sending the packet containing the CONNECTION_CLOSE frame, the pushDatagram will
        // remap the QuicConnectionImpl in QuicEndpoint.
        connection.pushDatagram(protectionRecord);
    }

    /**
     * Returns a {@link ByteBuffer} which contains an encrypted QUIC packet containing
     * a {@linkplain ConnectionCloseFrame CONNECTION_CLOSE frame}. The CONNECTION_CLOSE
     * frame will have a frame type of {@code 0x1c} and error code of {@code NO_ERROR}.
     * <p>
     * This method should only be invoked when the {@link QuicEndpoint} is being closed
     * and the endpoint wants to send out a {@code CONNECTION_CLOSE} frame on a best-effort
     * basis (in a fire and forget manner).
     *
     * @return the datagram containing the QUIC packet with a CONNECTION_CLOSE frame
     * @throws QuicKeyUnavailableException
     * @throws QuicTransportException
     */
    ByteBuffer makeConnectionCloseDatagram()
            throws QuicKeyUnavailableException, QuicTransportException {
        // in theory we don't need this assert, but given the knowledge that this method
        // should only be invoked by a closing QuicEndpoint, we have this assert here to
        // prevent misuse of this makeConnectionCloseDatagram() method
        assert connection.endpoint().isClosed() : "QUIC endpoint isn't closed";
        final ConnectionCloseFrame connCloseFrame = new ConnectionCloseFrame(NO_ERROR.code(),
                QuicFrame.CONNECTION_CLOSE, null);
        final KeySpace keySpace = connection.getTLSEngine().getCurrentSendKeySpace();
        // we don't want the connection's ByteBuffer pooling infrastructure
        // (through the QuicConnectionImpl::allocateDatagramForEncryption) for
        // this packet, so we use a simple custom allocator.
        final Function<QuicPacket, ByteBuffer> allocator = (pkt) -> ByteBuffer.allocate(pkt.size());
        final QuicPacket packet = connection.newQuicPacket(keySpace, List.of(connCloseFrame));
        final ProtectionRecord encrypted = ProtectionRecord.single(packet, allocator)
                .encrypt(connection.codingContext());
        final ByteBuffer datagram = encrypted.datagram();
        final int firstPacketOffset = encrypted.firstPacketOffset();
        // flip the datagram
        datagram.limit(datagram.position());
        datagram.position(firstPacketOffset);
        return datagram;
    }
}

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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.quic.QuicConnectionImpl.HandshakeFlow;
import jdk.internal.net.http.quic.QuicConnectionImpl.ProtectionRecord;
import jdk.internal.net.http.quic.TerminationCause.AppLayerClose;
import jdk.internal.net.http.quic.TerminationCause.SilentTermination;
import jdk.internal.net.http.quic.TerminationCause.TransportError;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.quic.QuicTLSEngine.KeySpace;
import jdk.internal.net.quic.QuicTransportErrors;
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
    public void keepAlive() {
        this.connection.idleTimeoutManager.keepAlive();
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
        // TODO: flush packets on streams that are ready for sending
        // (don't allow new streams etc) before entering
        // immediate close
        immediateClose(frame, keySpace, cause);
    }

    void incomingConnectionCloseFrame(final QuicPacket packet, final ConnectionCloseFrame frame) {
        Objects.requireNonNull(frame);
        if (debug.on()) {
            debug.log("Received close frame: %s", frame);
        }
        drain(frame);
    }

    void incomingStatelessReset() {
        final SilentTermination st = forSilentTermination("stateless reset from peer");
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

        // TODO: review this
        // TODO: does this need some lock?
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
            final PacketNumberSpace pktSpace = PacketNumberSpace.of(keySpace);
            final ConnectionCloseFrame adaptedFrame = adaptCloseFrame(pktSpace, closeFrame);
            final QuicPacket packet = connection.makeConnectionClosePacket(adaptedFrame, keySpace);
            final ProtectionRecord protectionRecord = ProtectionRecord.closing(packet,
                    connection::allocateDatagramForEncryption);
            // a successful encrypt will remap the QuicConnectionImpl in QuicEndpoint to a
            // closing connection
            connection.encrypt(protectionRecord);
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
        final String closeCodeHex = (isAppLayerClose ? "(app layer) " : "") +
                "0x" + Long.toHexString(incomingFrame.errorCode());
        final String reason = incomingFrame.reasonString();
        final String peer = connection.isClientConnection() ? "server" : "client";
        final String msg = "Connection closed by " + peer + " peer" +
                (reason == null || reason.isEmpty() ? "" : (": " + reason));
        final TerminationCause terminationCause;
        if (isAppLayerClose) {
            terminationCause = appLayerClose(incomingFrame.errorCode())
                    .peerVisibleReason(reason)
                    .loggedAs(msg);
        } else {
            final Optional<QuicTransportErrors> err = QuicTransportErrors.ofCode(
                    incomingFrame.errorCode());
            assert err.isPresent() : "unexpected error code in incoming connection close frame: "
                    + closeCodeHex;
            terminationCause = forTransportError(err.get())
                    .peerVisibleReason(reason)
                    .loggedAs(msg);
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
            Log.logQuic("{0} entering draining state, code {1} - {2}", logTag, closeCodeHex,
                    terminationCause.getLogMsg());
        } else if (debug.on()) {
            debug.log("entering draining state, code " + closeCodeHex + " - "
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
                final KeySpace keySpace = connection.getTLSEngine().getCurrentSendKeySpace();
                final QuicPacket packet = connection.makeConnectionClosePacket(outgoingFrame, keySpace);
                final ProtectionRecord protectionRecord = ProtectionRecord.closed(packet,
                        connection::allocateDatagramForEncryption);
                connection.encrypt(protectionRecord);
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
        // remap the connection to a draining connection
        final QuicEndpoint endpoint = this.connection.endpoint();
        assert endpoint != null : "QUIC endpoint is null";
        endpoint.draining(connection);
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

    /**
     * From RFC 9000 - section 10.2.3:
     * <quote>
     * A CONNECTION_CLOSE of type 0x1d MUST be replaced by a CONNECTION_CLOSE
     * of type 0x1c when sending the frame in Initial or Handshake packets.
     * Otherwise, information about the application state might be revealed.
     * Endpoints MUST clear the value of the Reason Phrase field and SHOULD
     * use the APPLICATION_ERROR code when converting to a CONNECTION_CLOSE
     * of type 0x1c.
     * </quote>
     */
    private static ConnectionCloseFrame adaptCloseFrame(final PacketNumberSpace pktSpace,
                                                        final ConnectionCloseFrame closeFrame) {
        return switch (pktSpace) {
            case APPLICATION -> closeFrame;
            default -> closeFrame.clearApplicationState();
        };
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
}

/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.http.WSOpeningHandshake.Result;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static java.net.http.WSUtils.logger;
import static java.util.Objects.requireNonNull;

/*
 * A WebSocket client.
 *
 * Consists of two independent parts; a transmitter responsible for sending
 * messages, and a receiver which notifies the listener of incoming messages.
 */
final class WS implements WebSocket {

    private final String subprotocol;
    private final RawChannel channel;
    private final WSTransmitter transmitter;
    private final WSReceiver receiver;
    private final Listener listener;
    private final Object stateLock = new Object();
    private volatile State state = State.CONNECTED;
    private final CompletableFuture<Void> whenClosed = new CompletableFuture<>();

    static CompletableFuture<WebSocket> newInstanceAsync(WSBuilder b) {
        CompletableFuture<Result> result = new WSOpeningHandshake(b).performAsync();
        Listener listener = b.getListener();
        Executor executor = b.getClient().executorService();
        return result.thenApply(r -> {
            WS ws = new WS(listener, r.subprotocol, r.channel, executor);
            ws.start();
            return ws;
        });
    }

    private WS(Listener listener, String subprotocol, RawChannel channel,
               Executor executor) {
        this.listener = wrapListener(listener);
        this.channel = channel;
        this.subprotocol = subprotocol;
        Consumer<Throwable> errorHandler = error -> {
            if (error == null) {
                throw new InternalError();
            }
            // If the channel is closed, we need to update the state, to denote
            // there's no point in trying to continue using WebSocket
            if (!channel.isOpen()) {
                synchronized (stateLock) {
                    tryChangeState(State.ERROR);
                }
            }
        };
        transmitter = new WSTransmitter(this, executor, channel, errorHandler);
        receiver = new WSReceiver(this.listener, this, executor, channel);
    }

    private void start() {
        receiver.start();
    }

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence message, boolean isLast) {
        requireNonNull(message, "message");
        synchronized (stateLock) {
            checkState();
            return transmitter.sendText(message, isLast);
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer message, boolean isLast) {
        requireNonNull(message, "message");
        synchronized (stateLock) {
            checkState();
            return transmitter.sendBinary(message, isLast);
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        requireNonNull(message, "message");
        synchronized (stateLock) {
            checkState();
            return transmitter.sendPing(message);
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        requireNonNull(message, "message");
        synchronized (stateLock) {
            checkState();
            return transmitter.sendPong(message);
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(CloseCode code, CharSequence reason) {
        requireNonNull(code, "code");
        requireNonNull(reason, "reason");
        synchronized (stateLock) {
            return doSendClose(() -> transmitter.sendClose(code, reason));
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendClose() {
        synchronized (stateLock) {
            return doSendClose(() -> transmitter.sendClose());
        }
    }

    private CompletableFuture<WebSocket> doSendClose(Supplier<CompletableFuture<WebSocket>> s) {
        checkState();
        boolean closeChannel = false;
        synchronized (stateLock) {
            if (state == State.CLOSED_REMOTELY) {
                closeChannel = tryChangeState(State.CLOSED);
            } else {
                tryChangeState(State.CLOSED_LOCALLY);
            }
        }
        CompletableFuture<WebSocket> sent = s.get();
        if (closeChannel) {
            sent.whenComplete((v, t) -> {
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.log(ERROR, "Error transitioning to state " + State.CLOSED, e);
                }
            });
        }
        return sent;
    }

    @Override
    public void request(long n) {
        if (n < 0L) {
            throw new IllegalArgumentException("The number must not be negative: " + n);
        }
        receiver.request(n);
    }

    @Override
    public String getSubprotocol() {
        return subprotocol;
    }

    @Override
    public boolean isClosed() {
        return state.isTerminal();
    }

    @Override
    public void abort() throws IOException {
        synchronized (stateLock) {
            tryChangeState(State.ABORTED);
        }
        channel.close();
    }

    @Override
    public String toString() {
        return super.toString() + "[" + state + "]";
    }

    private void checkState() {
        if (state.isTerminal() || state == State.CLOSED_LOCALLY) {
            throw new IllegalStateException("WebSocket is closed [" + state + "]");
        }
    }

    /*
     * Wraps the user's listener passed to the constructor into own listener to
     * intercept transitions to terminal states (onClose and onError) and to act
     * upon exceptions and values from the user's listener.
     */
    private Listener wrapListener(Listener listener) {
        return new Listener() {

            // Listener's method MUST be invoked in a happen-before order
            private final Object visibilityLock = new Object();

            @Override
            public void onOpen(WebSocket webSocket) {
                synchronized (visibilityLock) {
                    listener.onOpen(webSocket);
                }
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence message,
                                             MessagePart part) {
                synchronized (visibilityLock) {
                    return listener.onText(webSocket, message, part);
                }
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer message,
                                               MessagePart part) {
                synchronized (visibilityLock) {
                    return listener.onBinary(webSocket, message, part);
                }
            }

            @Override
            public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                synchronized (visibilityLock) {
                    return listener.onPing(webSocket, message);
                }
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                synchronized (visibilityLock) {
                    return listener.onPong(webSocket, message);
                }
            }

            @Override
            public void onClose(WebSocket webSocket, Optional<CloseCode> code, String reason) {
                synchronized (stateLock) {
                    if (state == State.CLOSED_REMOTELY || state.isTerminal()) {
                        throw new InternalError("Unexpected onClose in state " + state);
                    } else if (state == State.CLOSED_LOCALLY) {
                        try {
                            channel.close();
                        } catch (IOException e) {
                            logger.log(ERROR, "Error transitioning to state " + State.CLOSED, e);
                        }
                        tryChangeState(State.CLOSED);
                    } else if (state == State.CONNECTED) {
                        tryChangeState(State.CLOSED_REMOTELY);
                    }
                }
                synchronized (visibilityLock) {
                    listener.onClose(webSocket, code, reason);
                }
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                // An error doesn't necessarily mean the connection must be
                // closed automatically
                if (!channel.isOpen()) {
                    synchronized (stateLock) {
                        tryChangeState(State.ERROR);
                    }
                } else if (error instanceof ProtocolException
                        && error.getCause() instanceof WSProtocolException) {
                    WSProtocolException cause = (WSProtocolException) error.getCause();
                    logger.log(WARNING, "Failing connection {0}, reason: ''{1}''",
                            webSocket, cause.getMessage());
                    CloseCode cc = cause.getCloseCode();
                    transmitter.sendClose(cc, "").whenComplete((v, t) -> {
                        synchronized (stateLock) {
                            tryChangeState(State.ERROR);
                        }
                        try {
                            channel.close();
                        } catch (IOException e) {
                            logger.log(ERROR, e);
                        }
                    });
                }
                synchronized (visibilityLock) {
                    listener.onError(webSocket, error);
                }
            }
        };
    }

    private boolean tryChangeState(State newState) {
        assert Thread.holdsLock(stateLock);
        if (state.isTerminal()) {
            return false;
        }
        state = newState;
        if (newState.isTerminal()) {
            whenClosed.complete(null);
        }
        return true;
    }

    CompletionStage<Void> whenClosed() {
        return whenClosed;
    }

    /*
     * WebSocket connection internal state.
     */
    private enum State {

        /*
         * Initial WebSocket state. The WebSocket is connected (i.e. remains in
         * this state) unless proven otherwise. For example, by reading or
         * writing operations on the channel.
         */
        CONNECTED,

        /*
         * A Close message has been received by the client. No more messages
         * will be received.
         */
        CLOSED_REMOTELY,

        /*
         * A Close message has been sent by the client. No more messages can be
         * sent.
         */
        CLOSED_LOCALLY,

        /*
         * Close messages has been both sent and received (closing handshake)
         * and TCP connection closed. Closed _cleanly_ in terms of RFC 6455.
         */
        CLOSED,

        /*
         * The connection has been aborted by the client. Closed not _cleanly_
         * in terms of RFC 6455.
         */
        ABORTED,

        /*
         * The connection has been terminated due to a protocol or I/O error.
         * Might happen during sending or receiving.
         */
        ERROR;

        /*
         * Returns `true` if this state is terminal. If WebSocket has transited
         * to such a state, if remains in it forever.
         */
        boolean isTerminal() {
            return this == CLOSED || this == ABORTED || this == ERROR;
        }
    }
}

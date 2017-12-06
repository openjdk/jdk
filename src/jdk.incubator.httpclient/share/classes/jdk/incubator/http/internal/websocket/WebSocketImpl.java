/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.websocket;

import jdk.incubator.http.WebSocket;
import jdk.incubator.http.internal.common.Demand;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Pair;
import jdk.incubator.http.internal.common.SequentialScheduler;
import jdk.incubator.http.internal.common.SequentialScheduler.DeferredCompleter;
import jdk.incubator.http.internal.common.Utils;
import jdk.incubator.http.internal.websocket.OpeningHandshake.Result;
import jdk.incubator.http.internal.websocket.OutgoingMessage.Binary;
import jdk.incubator.http.internal.websocket.OutgoingMessage.Close;
import jdk.incubator.http.internal.websocket.OutgoingMessage.Context;
import jdk.incubator.http.internal.websocket.OutgoingMessage.Ping;
import jdk.incubator.http.internal.websocket.OutgoingMessage.Pong;
import jdk.incubator.http.internal.websocket.OutgoingMessage.Text;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ProtocolException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static jdk.incubator.http.internal.common.MinimalFuture.failedFuture;
import static jdk.incubator.http.internal.common.Pair.pair;
import static jdk.incubator.http.internal.websocket.StatusCodes.CLOSED_ABNORMALLY;
import static jdk.incubator.http.internal.websocket.StatusCodes.NO_STATUS_CODE;
import static jdk.incubator.http.internal.websocket.StatusCodes.isLegalToSendFromClient;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.BINARY;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.CLOSE;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.ERROR;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.IDLE;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.OPEN;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.PING;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.PONG;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.TEXT;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.State.WAITING;

/*
 * A WebSocket client.
 */
public final class WebSocketImpl implements WebSocket {

    enum State {
        OPEN,
        IDLE,
        WAITING,
        TEXT,
        BINARY,
        PING,
        PONG,
        CLOSE,
        ERROR;
    }

    private volatile boolean inputClosed;
    private volatile boolean outputClosed;

    private final AtomicReference<State> state = new AtomicReference<>(OPEN);

    /* Components of calls to Listener's methods */
    private MessagePart part;
    private ByteBuffer binaryData;
    private CharSequence text;
    private int statusCode;
    private String reason;
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    private final URI uri;
    private final String subprotocol;
    private final Listener listener;

    private final AtomicBoolean outstandingSend = new AtomicBoolean();
    private final SequentialScheduler sendScheduler = new SequentialScheduler(new SendTask());
    private final Queue<Pair<OutgoingMessage, CompletableFuture<WebSocket>>>
            queue = new ConcurrentLinkedQueue<>();
    private final Context context = new OutgoingMessage.Context();
    private final Transmitter transmitter;
    private final Receiver receiver;
    private final SequentialScheduler receiveScheduler = new SequentialScheduler(new ReceiveTask());
    private final Demand demand = new Demand();

    public static CompletableFuture<WebSocket> newInstanceAsync(BuilderImpl b) {
        Function<Result, WebSocket> newWebSocket = r -> {
            WebSocket ws = newInstance(b.getUri(),
                                       r.subprotocol,
                                       b.getListener(),
                                       r.transport);
            // Make sure we don't release the builder until this lambda
            // has been executed. The builder has a strong reference to
            // the HttpClientFacade, and we want to keep that live until
            // after the raw channel is created and passed to WebSocketImpl.
            Reference.reachabilityFence(b);
            return ws;
        };
        OpeningHandshake h;
        try {
            h = new OpeningHandshake(b);
        } catch (Throwable e) {
            return failedFuture(e);
        }
        return h.send().thenApply(newWebSocket);
    }

    /* Exposed for testing purposes */
    static WebSocket newInstance(URI uri,
                                 String subprotocol,
                                 Listener listener,
                                 TransportSupplier transport) {
        WebSocketImpl ws = new WebSocketImpl(uri, subprotocol, listener, transport);
        // This initialisation is outside of the constructor for the sake of
        // safe publication of WebSocketImpl.this
        ws.signalOpen();
        return ws;
    }

    private WebSocketImpl(URI uri,
                          String subprotocol,
                          Listener listener,
                          TransportSupplier transport) {
        this.uri = requireNonNull(uri);
        this.subprotocol = requireNonNull(subprotocol);
        this.listener = requireNonNull(listener);
        this.transmitter = transport.transmitter();
        this.receiver = transport.receiver(new SignallingMessageConsumer());
    }

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence message, boolean isLast) {
        return enqueueExclusively(new Text(message, isLast));
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer message, boolean isLast) {
        return enqueueExclusively(new Binary(message, isLast));
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        return enqueue(new Ping(message));
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        return enqueue(new Pong(message));
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
        if (!isLegalToSendFromClient(statusCode)) {
            return failedFuture(
                    new IllegalArgumentException("statusCode: " + statusCode));
        }
        Close msg;
        try {
            msg = new Close(statusCode, reason);
        } catch (IllegalArgumentException e) {
            return failedFuture(e);
        }
        outputClosed = true;
        return enqueueClose(msg);
    }

    /*
     * Sends a Close message, then shuts down the transmitter since no more
     * messages are expected to be sent after this.
     */
    private CompletableFuture<WebSocket> enqueueClose(Close m) {
        // TODO: MUST be a CF created once and shared across sendClose, otherwise
        // a second sendClose may prematurely close the channel
        return enqueue(m)
                .orTimeout(60, TimeUnit.SECONDS)
                .whenComplete((r, error) -> {
                    try {
                        transmitter.close();
                    } catch (IOException e) {
                        Log.logError(e);
                    }
                    if (error instanceof TimeoutException) {
                        try {
                            receiver.close();
                        } catch (IOException e) {
                            Log.logError(e);
                        }
                    }
                });
    }

    /*
     * Accepts the given message into the outgoing queue in a mutually-exclusive
     * fashion in respect to other messages accepted through this method. No
     * further messages will be accepted until the returned CompletableFuture
     * completes. This method is used to enforce "one outstanding send
     * operation" policy.
     */
    private CompletableFuture<WebSocket> enqueueExclusively(OutgoingMessage m) {
        if (!outstandingSend.compareAndSet(false, true)) {
            return failedFuture(new IllegalStateException("Send pending"));
        }
        return enqueue(m).whenComplete((r, e) -> outstandingSend.set(false));
    }

    private CompletableFuture<WebSocket> enqueue(OutgoingMessage m) {
        CompletableFuture<WebSocket> cf = new MinimalFuture<>();
        boolean added = queue.add(pair(m, cf));
        if (!added) {
            // The queue is supposed to be unbounded
            throw new InternalError();
        }
        sendScheduler.runOrSchedule();
        return cf;
    }

    /*
     * This is a message sending task. It pulls messages from the queue one by
     * one and sends them. It may be run in different threads, but never
     * concurrently.
     */
    private class SendTask implements SequentialScheduler.RestartableTask {

        @Override
        public void run(DeferredCompleter taskCompleter) {
            Pair<OutgoingMessage, CompletableFuture<WebSocket>> p = queue.poll();
            if (p == null) {
                taskCompleter.complete();
                return;
            }
            OutgoingMessage message = p.first;
            CompletableFuture<WebSocket> cf = p.second;
            try {
                if (!message.contextualize(context)) { // Do not send the message
                    cf.complete(null);
                    repeat(taskCompleter);
                    return;
                }
                Consumer<Exception> h = e -> {
                    if (e == null) {
                        cf.complete(WebSocketImpl.this);
                    } else {
                        cf.completeExceptionally(e);
                    }
                    repeat(taskCompleter);
                };
                transmitter.send(message, h);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
                repeat(taskCompleter);
            }
        }

        private void repeat(DeferredCompleter taskCompleter) {
            taskCompleter.complete();
            // More than a single message may have been enqueued while
            // the task has been busy with the current message, but
            // there is only a single signal recorded
            sendScheduler.runOrSchedule();
        }
    }

    @Override
    public void request(long n) {
        if (demand.increase(n)) {
            receiveScheduler.runOrSchedule();
        }
    }

    @Override
    public String getSubprotocol() {
        return subprotocol;
    }

    @Override
    public boolean isOutputClosed() {
        return outputClosed;
    }

    @Override
    public boolean isInputClosed() {
        return inputClosed;
    }

    @Override
    public void abort() {
        inputClosed = true;
        outputClosed = true;
        receiveScheduler.stop();
        close();
    }

    @Override
    public String toString() {
        return super.toString()
                + "[uri=" + uri
                + (!subprotocol.isEmpty() ? ", subprotocol=" + subprotocol : "")
                + "]";
    }

    /*
     * The assumptions about order is as follows:
     *
     *     - state is never changed more than twice inside the `run` method:
     *       x --(1)--> IDLE --(2)--> y (otherwise we're loosing events, or
     *       overwriting parts of messages creating a mess since there's no
     *       queueing)
     *     - OPEN is always the first state
     *     - no messages are requested/delivered before onOpen is called (this
     *       is implemented by making WebSocket instance accessible first in
     *       onOpen)
     *     - after the state has been observed as CLOSE/ERROR, the scheduler
     *       is stopped
     */
    private class ReceiveTask extends SequentialScheduler.CompleteRestartableTask {

        // Receiver only asked here and nowhere else because we must make sure
        // onOpen is invoked first and no messages become pending before onOpen
        // finishes

        @Override
        public void run() {
            while (true) {
                State s = state.get();
                try {
                    switch (s) {
                        case OPEN:
                            processOpen();
                            tryChangeState(OPEN, IDLE);
                            break;
                        case TEXT:
                            processText();
                            tryChangeState(TEXT, IDLE);
                            break;
                        case BINARY:
                            processBinary();
                            tryChangeState(BINARY, IDLE);
                            break;
                        case PING:
                            processPing();
                            tryChangeState(PING, IDLE);
                            break;
                        case PONG:
                            processPong();
                            tryChangeState(PONG, IDLE);
                            break;
                        case CLOSE:
                            processClose();
                            return;
                        case ERROR:
                            processError();
                            return;
                        case IDLE:
                            if (demand.tryDecrement()
                                    && tryChangeState(IDLE, WAITING)) {
                                receiver.request(1);
                            }
                            return;
                        case WAITING:
                            // For debugging spurious signalling: when there was a
                            // signal, but apparently nothing has changed
                            return;
                        default:
                            throw new InternalError(String.valueOf(s));
                    }
                } catch (Throwable t) {
                    signalError(t);
                }
            }
        }

        private void processError() throws IOException {
            receiver.close();
            receiveScheduler.stop();
            Throwable err = error.get();
            if (err instanceof FailWebSocketException) {
                int code1 = ((FailWebSocketException) err).getStatusCode();
                err = new ProtocolException().initCause(err);
                enqueueClose(new Close(code1, ""))
                        .whenComplete(
                                (r, e) -> {
                                    if (e != null) {
                                        Log.logError(e);
                                    }
                                });
            }
            listener.onError(WebSocketImpl.this, err);
        }

        private void processClose() throws IOException {
            receiver.close();
            receiveScheduler.stop();
            CompletionStage<?> readyToClose;
            readyToClose = listener.onClose(WebSocketImpl.this, statusCode, reason);
            if (readyToClose == null) {
                readyToClose = MinimalFuture.completedFuture(null);
            }
            int code;
            if (statusCode == NO_STATUS_CODE || statusCode == CLOSED_ABNORMALLY) {
                code = NORMAL_CLOSURE;
            } else {
                code = statusCode;
            }
            readyToClose.whenComplete((r, e) -> {
                enqueueClose(new Close(code, ""))
                        .whenComplete((r1, e1) -> {
                            if (e1 != null) {
                                Log.logError(e1);
                            }
                        });
            });
        }

        private void processPong() {
            listener.onPong(WebSocketImpl.this, binaryData);
        }

        private void processPing() {
            // Let's make a full copy of this tiny data. What we want here
            // is to rule out a possibility the shared data we send might be
            // corrupted by processing in the listener.
            ByteBuffer slice = binaryData.slice();
            ByteBuffer copy = ByteBuffer.allocate(binaryData.remaining())
                    .put(binaryData)
                    .flip();
            // Non-exclusive send;
            CompletableFuture<WebSocket> pongSent = enqueue(new Pong(copy));
            pongSent.whenComplete(
                    (r, e) -> {
                        if (e != null) {
                            signalError(Utils.getCompletionCause(e));
                        }
                    }
            );
            listener.onPing(WebSocketImpl.this, slice);
        }

        private void processBinary() {
            listener.onBinary(WebSocketImpl.this, binaryData, part);
        }

        private void processText() {
            listener.onText(WebSocketImpl.this, text, part);
        }

        private void processOpen() {
            listener.onOpen(WebSocketImpl.this);
        }
    }

    private void signalOpen() {
        receiveScheduler.runOrSchedule();
    }

    private void signalError(Throwable error) {
        inputClosed = true;
        outputClosed = true;
        if (!this.error.compareAndSet(null, error) || !trySetState(ERROR)) {
            Log.logError(error);
        } else {
            close();
        }
    }

    private void close() {
        try {
            try {
                receiver.close();
            } finally {
                transmitter.close();
            }
        } catch (Throwable t) {
            Log.logError(t);
        }
    }

    /*
     * Signals a Close event (might not correspond to anything happened on the
     * channel, i.e. might be synthetic).
     */
    private void signalClose(int statusCode, String reason) {
        inputClosed = true;
        this.statusCode = statusCode;
        this.reason = reason;
        if (!trySetState(CLOSE)) {
            Log.logTrace("Close: {0}, ''{1}''", statusCode, reason);
        } else {
            try {
                receiver.close();
            } catch (Throwable t) {
                Log.logError(t);
            }
        }
    }

    private class SignallingMessageConsumer implements MessageStreamConsumer {

        @Override
        public void onText(CharSequence data, MessagePart part) {
            receiver.acknowledge();
            text = data;
            WebSocketImpl.this.part = part;
            tryChangeState(WAITING, TEXT);
        }

        @Override
        public void onBinary(ByteBuffer data, MessagePart part) {
            receiver.acknowledge();
            binaryData = data;
            WebSocketImpl.this.part = part;
            tryChangeState(WAITING, BINARY);
        }

        @Override
        public void onPing(ByteBuffer data) {
            receiver.acknowledge();
            binaryData = data;
            tryChangeState(WAITING, PING);
        }

        @Override
        public void onPong(ByteBuffer data) {
            receiver.acknowledge();
            binaryData = data;
            tryChangeState(WAITING, PONG);
        }

        @Override
        public void onClose(int statusCode, CharSequence reason) {
            receiver.acknowledge();
            signalClose(statusCode, reason.toString());
        }

        @Override
        public void onComplete() {
            receiver.acknowledge();
            signalClose(CLOSED_ABNORMALLY, "");
        }

        @Override
        public void onError(Throwable error) {
            signalError(error);
        }
    }

    private boolean trySetState(State newState) {
        while (true) {
            State currentState = state.get();
            if (currentState == ERROR || currentState == CLOSE) {
                return false;
            } else if (state.compareAndSet(currentState, newState)) {
                receiveScheduler.runOrSchedule();
                return true;
            }
        }
    }

    private boolean tryChangeState(State expectedState, State newState) {
        State witness = state.compareAndExchange(expectedState, newState);
        if (witness == expectedState) {
            receiveScheduler.runOrSchedule();
            return true;
        }
        // This should be the only reason for inability to change the state from
        // IDLE to WAITING: the state has changed to terminal
        if (witness != ERROR && witness != CLOSE) {
            throw new InternalError();
        }
        return false;
    }
}

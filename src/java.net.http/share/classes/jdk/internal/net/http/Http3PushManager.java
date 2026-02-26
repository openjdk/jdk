/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler.PushId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;

import static jdk.internal.net.http.Http3ClientProperties.MAX_HTTP3_PUSH_STREAMS;

/**
 * Manages HTTP/3 push promises for an HTTP/3 connection.
 * <p>
 * This class maintains a bounded collection of recent push promises,
 * together with the current state of the promise: pending, processed, or
 * cancelled. When a new {@link jdk.internal.net.http.http3.frames.PushPromiseFrame}
 * is received, and entry is added in the map, and the state of the promise
 * is updated as it goes.
 * When the map is full, old entries (lowest pushId) are expunged from
 * the map. No promise will be accepted if its pushId is lower than the
 * lowest pushId in the map.
 *
 * @apiNote
 * When a PushPromiseFrame is received, {@link
 * #onPushPromiseFrame(Http3ExchangeImpl, long, HttpHeaders)}
 * is called. This arranges for an entry to be added to the map, unless there's
 * already one. Also, the first Http3ExchangeImpl for which this method is called
 * for a given pushId gets to handle the PushPromise: its {@link
 * java.net.http.HttpResponse.PushPromiseHandler} will be invoked to accept the promise
 * and handle the body.
 * <p>
 * When a new PushStream is opened, {@link #onPushPromiseStream(QuicReceiverStream, long)}
 * is called. When both {@code onPushPromiseFrame} and {@code onPushPromiseStream} have
 * been called for a given {@code pushId}, an {@link Http3PushPromiseStream} is created
 * and started to receive the body.
 * <p>
 * {@link Http3ExchangeImpl} that receive a push promise frame, but don't get to handle
 * the body (because it's already been delegated to another stream) should call
 * {@link #whenAccepted(long)} to figure out when it is safe to invoke {@link
 * PushGroup#acceptPushPromiseId(PushId)}.
 * <p>
 * {@link #cancelPushPromise(long, Throwable, CancelPushReason)} can be called to cancel
 * a push promise. {@link #pushPromiseProcessed(long)} should be called when the body
 * has been fully processed.
 */
final class Http3PushManager {

    private final Logger debug = Utils.getDebugLogger(this::dbgTag);

    private final ReentrantLock promiseLock = new ReentrantLock();
    private final ConcurrentHashMap<Long, PushPromise> promises = new ConcurrentHashMap<>();
    private final CompletableFuture<Boolean> DENIED = MinimalFuture.completedFuture(Boolean.FALSE);
    private final CompletableFuture<Boolean> ACCEPTED = MinimalFuture.completedFuture(Boolean.TRUE);

    private final AtomicLong maxPushId = new AtomicLong();
    private final AtomicLong maxPushReceived = new AtomicLong();
    private final AtomicLong minPushId = new AtomicLong();
    // the max history we keep in the promiseMap. We start expunging old
    // entries from the map when the size of the map exceeds this value
    private static final long MAX_PUSH_HISTORY_SIZE = (3*MAX_HTTP3_PUSH_STREAMS)/2;
    // the maxPushId increments, we send on MAX_PUSH_ID frame
    // with a maxPushId incremented by that amount.
    // Ideally should be <= to MAX_PUSH_HISTORY_SIZE, to avoid
    // filling up the history right after the first MAX_PUSH_ID
    private static final long MAX_PUSH_ID_INCREMENTS = MAX_HTTP3_PUSH_STREAMS;
    private final Http3Connection connection;

    // number of pending promises
    private final AtomicInteger pendingPromises = new AtomicInteger();
    // push promises are considered blocked if we have failed to send
    // the last MAX_PUSH_ID update due to pendingPromises
    // count having reached MAX_HTTP3_PUSH_STREAMS
    private volatile boolean pushPromisesBlocked;


    Http3PushManager(Http3Connection connection) {
        this.connection = connection;
    }

    String dbgTag() {
        return connection.dbgTag();
    }

    public void cancelAllPromises(IOException closeCause, Http3Error error) {
        for (var promise : promises.entrySet()) {
            var pushId = promise.getKey();
            var pp = promise.getValue();
            switch (pp) {
                case ProcessedPushPromise ignored -> {}
                case CancelledPushPromise ignored -> {}
                case PendingPushPromise<?> ppp -> {
                    cancelPendingPushPromise(ppp, closeCause);
                }
            }
        }
    }

    // Different actions needs to be carried out when cancelling a
    // push promise, depending on the state of the promise and the
    // cancellation reason.
    enum CancelPushReason {
        NO_HANDLER,       // the exchange has no PushGroup
        PUSH_CANCELLED,   // the PromiseHandler cancelled the push,
        // or an error occurred handling the promise
        CANCEL_RECEIVED;  // received CANCEL_PUSH from server
    }

    /**
     * A PushPromise can be a PendingPushPromise, until the push
     * response is completely received, or a ProcessedPushPromise,
     * which replace the PendingPushPromise after the response body
     * has been delivered. If the PushPromise is cancelled before
     * accepting it or receiving a body, CancelledPushPromise will
     * be recorded and replace the PendingPushPromise.
     */
    private sealed interface PushPromise
            permits PendingPushPromise, ProcessedPushPromise, CancelledPushPromise {
    }

    /**
     * Represent a PushPromise whose body as already been delivered
     */
    private record ProcessedPushPromise(PushId pushId, HttpHeaders promiseHeaders)
            implements PushPromise { }

    /**
     * Represent a PushPromise that has been cancelled. No body will be delivered.
     */
    private record CancelledPushPromise(PushId pushId) implements PushPromise { }

    // difficult to say what will come first - the push promise,
    // or the push stream?
    // The first push promise frame received will register the
    // exchange with this class - and trigger the parsing of
    // the request/response when the stream is available.
    // The other will trigger a simple call to register the
    // push id.
    // Probably we also need some timer to clean
    // up the map if the stream doesn't manifest after a while.
    // We maintain minPushID, where any frame
    // containing a push id < to the min will be discarded,
    // and any stream with a pushId < will also be discarded.

    /**
     * Represents a PushPromise whose body has not been delivered
     * yet.
     * @param <T> the type of the body
     */
    private static final class PendingPushPromise<T> implements PushPromise {
        // called when the first push promise frame is received
        PendingPushPromise(Http3ExchangeImpl<T> exchange, long pushId, HttpHeaders promiseHeaders) {
            this.accepted = new MinimalFuture<>();
            this.exchange = Objects.requireNonNull(exchange);
            this.promiseHeaders = Objects.requireNonNull(promiseHeaders);
            this.pushId = pushId;
        }

        // called when the push promise stream is opened
        PendingPushPromise(QuicReceiverStream stream, long pushId) {
            this.accepted = new MinimalFuture<>();
            this.stream = Objects.requireNonNull(stream);
            this.pushId = pushId;
        }

        // volatiles should not be required since we only modify/read
        // those within a lock. Final fields should ensure safe publication
        final long pushId;                         // the push id
        QuicReceiverStream stream;                 // the quic promise stream
        Http3ExchangeImpl<T> exchange;             // the exchange that will create the body subscriber
        Http3PushPromiseStream<T> promiseStream;   // the HTTP/3 stream to process the quic stream
        HttpHeaders promiseHeaders;                // the push promise request headers
        CompletableFuture<HttpResponse<T>> responseCF;
        HttpRequestImpl pushReq;
        BodyHandler<T> handler;
        final CompletableFuture<Boolean> accepted; // whether the push promise was accepted

        public long pushId() { return pushId; }

        public boolean ready() {
            if (stream == null) return false;
            if (exchange == null) return false;
            if (promiseHeaders == null) return false;
            if (!accepted.isDone()) return false;
            if (responseCF == null) return false;
            if (pushReq == null) return false;
            if (handler == null) return false;
            return true;
        }

        @Override
        public String toString() {
            return "PendingPushPromise{" +
                    "pushId=" + pushId +
                    ", stream=" + stream +
                    ", exchange=" + dbgTag(exchange) +
                    ", promiseStream=" + dbgTag(promiseStream) +
                    ", promiseHeaders=" + promiseHeaders +
                    ", accepted=" + accepted +
                    '}';
        }

        String dbgTag(Http3ExchangeImpl<?> exchange) {
            return exchange == null ? null : exchange.dbgTag();
        }

        String dbgTag(Http3PushPromiseStream<?> promiseStream) {
            return promiseStream == null ? null : promiseStream.dbgTag();
        }
    }

    /**
     * {@return the maximum pushId that can be accepted from the peer}
     * This corresponds to the pushId that has been included in the last
     * MAX_PUSH_ID frame sent to the peer. A pushId greater than this
     * value must be rejected, and cause the connection to close with
     * error.
     *
     * @apiNote due to internal constraints it is possible that the
     * MAX_PUSH_ID frame has not been sent yet, but the {@code Http3PushManager}
     * will behave as if the peer had received that frame.
     *
     * @see Http3Connection#checkMaxPushId(long)
     * @see #checkMaxPushId(long)
     */
    long getMaxPushId() {
        return maxPushId.get();
    }

    /**
     * {@return the minimum pushId that can be accepted from the peer}
     * Any pushId strictly less than this value must be ignored.
     *
     * @apiNote The minimum pushId represents the smallest pushId that
     * was recorded in our history. For smaller pushId, no history has
     * been kept, due to history size constraints. Any pushId strictly
     * less than this value must be ignored.
     */
    long getMinPushId() {
        return minPushId.get();
    }

    /**
     * Called when a new push promise stream is created by the peer, and
     * the pushId has been read.
     * @param pushStream the new push promise stream
     * @param pushId the pushId
     */
    void onPushPromiseStream(QuicReceiverStream pushStream, long pushId) {
        assert pushId >= 0;
        if (!connection.acceptLargerPushPromise(pushStream, pushId))  return;
        PendingPushPromise<?> promise = addPushPromise(pushStream, pushId);
        if (promise != null) {
            assert promise.stream == pushStream;
            // if stream is avoilable start parsing?
            tryReceivePromise(promise);
        }
    }

    /**
     * Checks whether a MAX_PUSH_ID frame needs to be sent,
     * and send it.
     * Called from {@link Http3Connection#checkSendMaxPushId()}.
     */
    void checkSendMaxPushId() {
        if (MAX_PUSH_ID_INCREMENTS <= 0) return;
        long pendingCount =  pendingPromises.get();
        long availableSlots = MAX_HTTP3_PUSH_STREAMS - pendingCount;
        if (availableSlots <= 0) {
            pushPromisesBlocked = true;
            if (debug.on()) debug.log("Push promises blocked: availableSlots=%s", pendingCount);
            return;
        }
        long maxPushIdSent = maxPushId.get();
        long maxPushIdReceived = maxPushReceived.get();
        long half = Math.max(1, MAX_PUSH_ID_INCREMENTS /2);
        if (maxPushIdSent - maxPushIdReceived < half) {
            // do not send a maxPushId that would consume more
            // than our available slots
            long increment = Math.min(availableSlots, MAX_PUSH_ID_INCREMENTS);
            long update = maxPushIdSent + increment;
            boolean updated = false;
            try {
                // let's update the counter before sending the frame,
                // otherwise there's a chance we can receive a frame
                // before updating the counter.
                do {
                    if (maxPushId.compareAndSet(maxPushIdSent, update)) {
                        if (debug.on()) {
                            debug.log("MAX_PUSH_ID updated: %s (%s -> %s), increment %s, pending %s, availableSlots %s",
                                    update, maxPushIdSent, update, increment,
                                    promises.values().stream().filter(PendingPushPromise.class::isInstance)
                                            .map(p -> (PendingPushPromise<?>) p)
                                            .map(PendingPushPromise::pushId).toList(),
                                    availableSlots);
                        }
                        updated = true;
                        break;
                    }
                    maxPushIdSent = maxPushId.get();
                } while (maxPushIdSent < update);
                if (updated) {
                    if (pushPromisesBlocked) {
                        if (debug.on()) debug.log("Push promises unblocked: maxPushIdSent=%s", update);
                        pushPromisesBlocked = false;
                    }
                    connection.sendMaxPushId(update);
                }
            } catch (IOException io) {
                debug.log("Failed to send MAX_PUSH_ID(%s): %s", update, io);
            }
        }
    }

    /**
     * Called when a PushPromiseFrame has been decoded.
     *
     * @apiNote
     * This method calls {@link Http3ExchangeImpl#acceptPushPromise(long, HttpRequestImpl)}
     * and {@link Http3ExchangeImpl#onPushRequestAccepted(long, CompletableFuture)}
     * for the first exchange that receives the {@link
     * jdk.internal.net.http.http3.frames.PushPromiseFrame}
     *
     * @param exchange        The HTTP/3 exchange that received the frame
     * @param pushId          The pushId contained in the frame
     * @param promiseHeaders  The push promise headers contained in the frame
     *
     * @return true if the exchange should take care of creating the HttpResponse body,
     *              false otherwise
     *
     * @see Http3Connection#onPushPromiseFrame(Http3ExchangeImpl, long, HttpHeaders)
     */
    <U> boolean onPushPromiseFrame(Http3ExchangeImpl<U> exchange, long pushId, HttpHeaders promiseHeaders)
            throws IOException {
        if (!connection.acceptLargerPushPromise(null, pushId)) return false;
        PendingPushPromise<?> promise = addPushPromise(exchange, pushId, promiseHeaders);
        if (promise == null) {
            return false;
        }
        // A PendingPushPromise is returned only if there was no
        // PushPromise present. If a PendingPushPromise is returned
        // it should therefore have its exchange already set to the
        // current exchange.
        assert promise.exchange == exchange;
        HttpRequestImpl pushReq = HttpRequestImpl.createPushRequest(
                exchange.getExchange().request(), promiseHeaders);
        var acceptor = exchange.acceptPushPromise(pushId, pushReq);
        if (acceptor == null) {
            // nothing to do: the push should already have been cancelled.
            return false;
        }
        @SuppressWarnings("unchecked")
        var pppU = (PendingPushPromise<U>) promise;
        var responseCF = pppU.responseCF;
        assert responseCF == null;
        boolean cancelled = false;
        promiseLock.lock();
        try {
            promise.pushReq = pushReq;
            pppU.responseCF = responseCF = acceptor.cf();
            // recheck to verify the push hasn't been cancelled already
            var check = promises.get(pushId);
            if (check instanceof CancelledPushPromise || check == null) {
                cancelled = true;
            } else {
                assert promise == check;
                pppU.handler = acceptor.bodyHandler();
            }
        } finally {
            promiseLock.unlock();
        }
        if (!cancelled) {
            exchange.onPushRequestAccepted(pushId, responseCF);
            promise.accepted.complete(true);
            // if stream is available start parsing?
            tryReceivePromise(promise);
            return true;
        } else {
            cancelPendingPushPromise(promise, null);
            // should be a no-op - in theory it should already
            // have been completed
            promise.accepted.complete(false);
            return false;
        }
    }

    /**
     * {@return a completable future that will be completed when a pushId has been
     * accepted by the exchange in charge of creating the response body}
     *
     * The completable future is complete with {@code true} if the pushId is
     * accepted, and with {@code false} if the pushId was rejected or cancelled.
     *
     * This method is intended to be called when {@link
     * #onPushPromiseFrame(Http3ExchangeImpl, long, HttpHeaders)}, returns false,
     * indicating that the push promise is being delegated to another request/response
     * exchange.
     * On completion of the future returned here, if the future is completed
     * with {@code true}, the caller is expected to call {@link
     * PushGroup#acceptPushPromiseId(PushId)} in order  to notify the {@link
     * java.net.http.HttpResponse.PushPromiseHandler} of the received {@code pushId}.
     *
     * @see Http3Connection#whenPushAccepted(long)
     * @param pushId  the pushId
     */
    CompletableFuture<Boolean> whenAccepted(long pushId) {
        var promise = promises.get(pushId);
        if (promise instanceof PendingPushPromise<?> pp) {
            return pp.accepted;
        } else if (promise instanceof ProcessedPushPromise) {
            return ACCEPTED;
        } else { // CancelledPushPromise or null
            return DENIED;
        }
    }


    /**
     * Cancel a push promise. In case of concurrent requests receiving the
     * same pushId, where one has a PushPromiseHandler and the other doesn't,
     * we will cancel the push only if reason != CANCEL_RECEIVED, or no request
     * stream has already accepted the push.
     *
     * @param pushId the promise pushId
     * @param cause  the cause (can be null)
     * @param reason reason for cancelling
     */
    void cancelPushPromise(long pushId, Throwable cause, CancelPushReason reason) {
        boolean sendCancelPush = false;
        PendingPushPromise<?> pending = null;
        if (cause != null) {
            debug.log("PushPromise cancelled: pushId=" + pushId, cause);
        } else {
            debug.log("PushPromise cancelled: pushId=%s", pushId);
            String msg = "cancelPushPromise(pushId="+pushId+")";
            debug.log(msg);
        }
        if (reason == CancelPushReason.CANCEL_RECEIVED) {
            if (checkMaxPushId(pushId) != null) {
                // pushId >= max connection will be closed
                return;
            }
        }
        promiseLock.lock();
        try {
            var promise = promises.get(pushId);
            long min = minPushId.get();
            if (promise == null) {
                if (pushId > maxPushReceived.get()) maxPushReceived.set(pushId);
                checkExpungePromiseMap();
                if (pushId >= min) {
                    var cancelled = new CancelledPushPromise(connection.newPushId(pushId));
                    promises.put(pushId, cancelled);
                    sendCancelPush = reason != CancelPushReason.CANCEL_RECEIVED;
                }
            } else if (promise instanceof CancelledPushPromise) {
                // nothing to do
            } else if (promise instanceof ProcessedPushPromise) {
                // nothing we can do?
            } else if (promise instanceof PendingPushPromise<?> ppp) {
                // only cancel if never accepted, or force cancel requested
                if (ppp.promiseStream == null || reason != CancelPushReason.NO_HANDLER) {
                    var cancelled = new CancelledPushPromise(connection.newPushId(pushId));
                    promises.put(pushId, cancelled);
                    long pendingCount = pendingPromises.decrementAndGet();
                    long ppc;
                    assert (ppc = promises.values().stream().filter(PendingPushPromise.class::isInstance).count()) == pendingCount
                            : "bad pending promise count: expected %s but found %s".formatted(pendingCount, ppc);
                    ppp.accepted.complete(false); // NO OP if already completed
                    pending = ppp;
                    // send cancel push; do not send if we received
                    // a CancelPushFrame from the peer
                    // also do not update MAX_PUSH_ID here - MAX_PUSH_ID will
                    // be updated when starting the next request/response exchange that accepts
                    // push promises.
                    sendCancelPush = reason != CancelPushReason.CANCEL_RECEIVED;
                }
            }
        } finally {
            promiseLock.unlock();
        }
        if (sendCancelPush) {
            connection.sendCancelPush(pushId, cause);
        }
        if (pending != null) {
            cancelPendingPushPromise(pending, cause);
        }
    }

    private void cancelPendingPushPromise(PendingPushPromise<?> ppp, Throwable cause) {
        var ps = ppp.stream;
        var http3 = ppp.promiseStream;
        var responseCF = ppp.responseCF;
        if (ps != null) {
            ps.requestStopSending(Http3Error.H3_REQUEST_CANCELLED.code());
        }
        if (http3 != null || responseCF != null) {
            IOException io;
            if (cause == null) {
                io = new IOException("Push promise cancelled: " + ppp.pushId);
            } else {
                io = Utils.toIOException(cause);
            }
            if (http3 != null) {
                http3.cancel(io);
            } else if (responseCF != null) {
                responseCF.completeExceptionally(io);
            }
        }
    }

    /**
     * Called when a push promise response body has been successfully received.
     * @param pushId the pushId
     */
    void pushPromiseProcessed(long pushId) {
        promiseLock.lock();
        try {
            var promise = promises.get(pushId);
            if (promise instanceof PendingPushPromise<?> ppp) {
                var processed = new ProcessedPushPromise(connection.newPushId(pushId),
                        ppp.promiseHeaders);
                promises.put(pushId, processed);
                var pendingCount = pendingPromises.decrementAndGet();
                long ppc;
                assert (ppc = promises.values().stream().filter(PendingPushPromise.class::isInstance).count()) == pendingCount
                        : "bad pending promise count: expected %s but found %s".formatted(pendingCount, ppc);
                // do not update MAX_PUSH_ID here - MAX_PUSH_ID will
                // be updated when starting the next request/response exchange that accepts
                // push promises.
            }
        } finally {
            promiseLock.unlock();
        }
    }

    /**
     * Checks whether the given pushId exceed the maximum pushId allowed
     * to the peer, and if so, closes the connection.
     * @param pushId the pushId
     * @return an {@code IOException} that can be used to complete a completable
     *         future if the maximum pushId is exceeded, {@code null}
     *         otherwise
     */
    IOException checkMaxPushId(long pushId) {
        return connection.checkMaxPushId(pushId);
    }

    // Checks whether an Http3PushPromiseStream can be created now
    private <U> void tryReceivePromise(PendingPushPromise<U> promise) {
        debug.log("tryReceivePromise: " + promise);
        promiseLock.lock();
        Http3PushPromiseStream<U> http3PushPromiseStream = null;
        IOException failed = null;
        try {
            if (promise.ready() && promise.promiseStream == null) {
                promise.promiseStream = http3PushPromiseStream =
                    createPushExchange(promise);
            } else {
                debug.log("tryReceivePromise: Can't create Http3PushPromiseStream for pushId=%s yet",
                        promise.pushId);
            }
        } catch (IOException io) {
            failed = io;
        } finally {
            promiseLock.unlock();
        }
        if (failed != null) {
            cancelPushPromise(promise.pushId, failed, CancelPushReason.PUSH_CANCELLED);
            return;
        }
        if (http3PushPromiseStream != null) {
            // HTTP/3 push promises are not ref-counted
            // If we were to change that it could be necessary to
            // temporarly increment ref-counting here, until the stream
            // read loop effectively starts.
            http3PushPromiseStream.start();
        }
    }

    // try to create and start an Http3PushPromiseStream when all bits have
    // been received
    private <U> Http3PushPromiseStream<U> createPushExchange(PendingPushPromise<U> promise)
            throws IOException {
        assert promise.ready() : "promise is not ready: " + promise;
        Http3ExchangeImpl<U> parent = promise.exchange;
        HttpRequestImpl pushReq = promise.pushReq;
        QuicReceiverStream quicStream = promise.stream;
        Exchange<U> pushExch = new Exchange<>(pushReq, parent.exchange.multi);
        Http3PushPromiseStream<U> pushStream = new Http3PushPromiseStream<>(pushExch,
                parent.http3Connection(), this,
                quicStream, promise.responseCF, promise.handler, parent, promise.pushId);
        pushExch.exchImpl = pushStream;
        return pushStream;
    }

    // The first exchange that gets the PushPromise gets a PushPromise object,
    //     others get null
    // TODO: ideally we should start a timer to cancel a push promise if
    //       the stream doesn't materialize after a while.
    //       Note that the callers can always start their own timeouts using
    //       the CompletableFutures we returned to them.
    private <U> PendingPushPromise<U> addPushPromise(Http3ExchangeImpl<U> exchange,
                                                     long pushId,
                                                     HttpHeaders promiseHeaders) {
        PushPromise promise = promises.get(pushId);
        boolean cancelStream = false;
        if (promise == null) {
            promiseLock.lock();
            try {
                promise = promises.get(pushId);
                if (promise == null) {
                    if (checkMaxPushId(pushId) == null) {
                        if (pushId >= minPushId.get()) {
                            if (pushId > maxPushReceived.get()) maxPushReceived.set(pushId);
                            checkExpungePromiseMap();
                            var pp = new PendingPushPromise<>(exchange, pushId, promiseHeaders);
                            promises.put(pushId, pp);
                            long pendingCount = pendingPromises.incrementAndGet();
                            long ppc;
                            assert (ppc = promises.values().stream().filter(PendingPushPromise.class::isInstance).count()) == pendingCount
                                    : "bad pending promise count: expected %s but found %s".formatted(pendingCount, ppc);
                            return pp;
                        } else {
                            // pushId < minPushId
                            cancelStream = true;
                        }
                    } else return null;
                }
            } finally {
                promiseLock.unlock();
            }
        }
        if (cancelStream) {
            // we don't have the stream;
            // the stream will be canceled if it comes later
            // do not send push cancel frame (already cancelled, or abandoned)
            return null;
        }
        if (promise instanceof PendingPushPromise<?> ppp) {
            var pe = ppp.exchange;
            if (pe == null) {
                promiseLock.lock();
                try {
                    if (ppp.exchange == null) {
                        assert ppp.promiseHeaders == null;
                        @SuppressWarnings("unchecked")
                        var pppU = (PendingPushPromise<U>) ppp;
                        pppU.exchange = exchange;
                        pppU.promiseHeaders = promiseHeaders;
                        return pppU;
                    }
                } finally {
                    promiseLock.unlock();
                }
            }
            var previousHeaders = ppp.promiseHeaders;
            if (previousHeaders != null && !previousHeaders.equals(promiseHeaders)) {
                connection.protocolError(
                        new ProtocolException("push headers do not match with previous promise for " + pushId));
            }
        } else if (promise instanceof ProcessedPushPromise ppp) {
            if (!ppp.promiseHeaders().equals(promiseHeaders)) {
                connection.protocolError(
                        new ProtocolException("push headers do not match with previous promise for " + pushId));
            }
        } else if (promise instanceof CancelledPushPromise) {
            // already cancelled - nothing to do
        }
        return null;
    }

    // TODO: the packet opening the push promise stream might reach us before
    //       the push promise headers are processed. We could start a timer
    //       here to cancel the push promise if the PushPromiseFrame doesn't materialize
    //       after a while.
    private <U> PendingPushPromise<U> addPushPromise(QuicReceiverStream stream, long pushId) {
        PushPromise promise = promises.get(pushId);
        boolean cancelStream = false;
        if (promise == null) {
            promiseLock.lock();
            try {
                promise = promises.get(pushId);
                if (promise == null) {
                    if (checkMaxPushId(pushId) == null) {
                        if (pushId >= minPushId.get()) {
                            if (pushId > maxPushReceived.get()) maxPushReceived.set(pushId);
                            checkExpungePromiseMap();
                            var pp = new PendingPushPromise<U>(stream, pushId);
                            promises.put(pushId, pp);
                            long pendingCount = pendingPromises.incrementAndGet();
                            long ppc;
                            assert (ppc = promises.values().stream().filter(PendingPushPromise.class::isInstance).count()) == pendingCount
                                    : "bad pending promise count: expected %s but found %s".formatted(pendingCount, ppc);
                            return pp;
                        } else {
                            // pushId < minPushId
                            cancelStream = true;
                        }
                    } else return null; // maxPushId exceeded, connection closed
                }
            } finally {
                promiseLock.unlock();
            }
        }
        if (cancelStream) {
            // do not send push cancel frame (already cancelled, or abandoned)
            stream.requestStopSending(Http3Error.H3_REQUEST_CANCELLED.code());
            return null;
        }
        if (promise instanceof PendingPushPromise<?> ppp) {
            var ps = ppp.stream;
            if (ps == null) {
                promiseLock.lock();
                try {
                    if ((ps = ppp.stream) == null) {
                        ps = ppp.stream = stream;
                    }
                } finally {
                    promiseLock.unlock();
                }
            }
            if (ps == stream) {
                @SuppressWarnings("unchecked")
                var pp = ((PendingPushPromise<U>) ppp);
                return pp;
            } else {
                // Error! cancel stream...
                var io = new ProtocolException("HTTP/3 pushId %s already used on this connection".formatted(pushId));
                connection.connectionError(io, Http3Error.H3_ID_ERROR);
            }
        } else if (promise instanceof ProcessedPushPromise) {
            var io = new ProtocolException("HTTP/3 pushId %s already used on this connection".formatted(pushId));
            connection.connectionError(io, Http3Error.H3_ID_ERROR);
        } else {
            // already cancelled?
            // Error! cancel stream...
            // connection.sendCancelPush(pushId, null);
            stream.requestStopSending(Http3Error.H3_REQUEST_CANCELLED.code());
        }
        return null;
    }

    // We only keep MAX_PUSH_HISTORY_SIZE entries in the map.
    // If the map has more than MAX_PUSH_HISTORY_SIZE entries, we start expunging
    // pushIds starting at minPushId. This method makes room for at least
    // on push promise in the map
    private void checkExpungePromiseMap() {
        assert promiseLock.isHeldByCurrentThread();
        while (promises.size() >= MAX_PUSH_HISTORY_SIZE) {
            long min = minPushId.getAndIncrement();
            var pp = promises.remove(min);
            if (pp instanceof PendingPushPromise<?> ppp) {
                var pendingCount = pendingPromises.decrementAndGet();
                long ppc;
                assert (ppc = promises.values().stream().filter(PendingPushPromise.class::isInstance).count()) == pendingCount
                        : "bad pending promise count: expected %s but found %s".formatted(pendingCount, ppc);
                var http3 = ppp.promiseStream;
                IOException io = null;
                if (http3 != null) {
                    http3.cancel(io = new IOException("PushPromise cancelled"));
                }
                if (io == null) {
                    io = new IOException("PushPromise cancelled");
                }
                connection.sendCancelPush(ppp.pushId, io);
                var ps = ppp.stream;
                if (ps != null) {
                    ps.requestStopSending(Http3Error.H3_REQUEST_CANCELLED.code());
                }
            }
        }
    }

}

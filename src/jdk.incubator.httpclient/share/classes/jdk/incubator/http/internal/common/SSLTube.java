/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.common;

import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import jdk.incubator.http.internal.common.SubscriberWrapper.SchedulingAction;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;

/**
 * An implementation of FlowTube that wraps another FlowTube in an
 * SSL flow.
 * <p>
 * The following diagram shows a typical usage of the SSLTube, where
 * the SSLTube wraps a SocketTube on the right hand side, and is connected
 * to an HttpConnection on the left hand side.
 *
 * <preformatted>{@code
 *                  +----------  SSLTube -------------------------+
 *                  |                                             |
 *                  |                    +---SSLFlowDelegate---+  |
 *  HttpConnection  |                    |                     |  |   SocketTube
 *    read sink  <- SSLSubscriberW.   <- Reader <- upstreamR.() <---- read source
 *  (a subscriber)  |                    |    \         /      |  |  (a publisher)
 *                  |                    |     SSLEngine       |  |
 *  HttpConnection  |                    |    /         \      |  |   SocketTube
 *  write source -> SSLSubscriptionW. -> upstreamW.() -> Writer ----> write sink
 *  (a publisher)   |                    |                     |  |  (a subscriber)
 *                  |                    +---------------------+  |
 *                  |                                             |
 *                  +---------------------------------------------+
 * }</preformatted>
 */
public class SSLTube implements FlowTube {

    static final boolean DEBUG = Utils.DEBUG; // revisit: temporary developer's flag.
    final System.Logger debug =
            Utils.getDebugLogger(this::dbgString, DEBUG);

    private final FlowTube tube;
    private final SSLSubscriberWrapper readSubscriber;
    private final SSLSubscriptionWrapper writeSubscription;
    private final SSLFlowDelegate sslDelegate;
    private final SSLEngine engine;
    private volatile boolean finished;

    public SSLTube(SSLEngine engine, Executor executor, FlowTube tube) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(executor);
        this.tube = Objects.requireNonNull(tube);
        writeSubscription = new SSLSubscriptionWrapper();
        readSubscriber = new SSLSubscriberWrapper();
        this.engine = engine;
        sslDelegate = new SSLTubeFlowDelegate(engine,
                                              executor,
                                              readSubscriber,
                                              tube);
    }

    final class SSLTubeFlowDelegate extends SSLFlowDelegate {
        SSLTubeFlowDelegate(SSLEngine engine, Executor executor,
                            SSLSubscriberWrapper readSubscriber,
                            FlowTube tube) {
            super(engine, executor, readSubscriber, tube);
        }
        protected SchedulingAction enterReadScheduling() {
            readSubscriber.processPendingSubscriber();
            return SchedulingAction.CONTINUE;
        }
        void connect(Flow.Subscriber<? super List<ByteBuffer>> downReader,
                     Flow.Subscriber<? super List<ByteBuffer>> downWriter) {
            assert downWriter == tube;
            assert downReader == readSubscriber;

            // Connect the read sink first. That's the left-hand side
            // downstream subscriber from the HttpConnection (or more
            // accurately, the SSLSubscriberWrapper that will wrap it
            // when SSLTube::connectFlows is called.
            reader.subscribe(downReader);

            // Connect the right hand side tube (the socket tube).
            //
            // The SSLFlowDelegate.writer publishes ByteBuffer to
            // the SocketTube for writing on the socket, and the
            // SSLFlowDelegate::upstreamReader subscribes to the
            // SocketTube to receive ByteBuffers read from the socket.
            //
            // Basically this method is equivalent to:
            //     // connect the read source:
            //     //   subscribe the SSLFlowDelegate upstream reader
            //     //   to the socket tube publisher.
            //     tube.subscribe(upstreamReader());
            //     // connect the write sink:
            //     //   subscribe the socket tube write subscriber
            //     //   with the SSLFlowDelegate downstream writer.
            //     writer.subscribe(tube);
            tube.connectFlows(FlowTube.asTubePublisher(writer),
                              FlowTube.asTubeSubscriber(upstreamReader()));

            // Finally connect the write source. That's the left
            // hand side publisher which will push ByteBuffer for
            // writing and encryption to the SSLFlowDelegate.
            // The writeSubscription is in fact the SSLSubscriptionWrapper
            // that will wrap the subscription provided by the
            // HttpConnection publisher when SSLTube::connectFlows
            // is called.
            upstreamWriter().onSubscribe(writeSubscription);
        }
    }

    public CompletableFuture<String> getALPN() {
        return sslDelegate.alpn();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> s) {
        readSubscriber.dropSubscription();
        readSubscriber.setDelegate(s);
        s.onSubscribe(readSubscription);
    }

    /**
     * Tells whether, or not, this FlowTube has finished receiving data.
     *
     * @return true when one of this FlowTube Subscriber's OnError or onComplete
     * methods have been invoked
     */
    @Override
    public boolean isFinished() {
        return finished;
    }

    private volatile Flow.Subscription readSubscription;

    // The DelegateWrapper wraps a subscribed {@code Flow.Subscriber} and
    // tracks the subscriber's state. In particular it makes sure that
    // onComplete/onError are not called before onSubscribed.
    final static class DelegateWrapper implements FlowTube.TubeSubscriber {
        private final FlowTube.TubeSubscriber delegate;
        private final System.Logger debug;
        volatile boolean subscribedCalled;
        volatile boolean subscribedDone;
        volatile boolean completed;
        volatile Throwable error;
        DelegateWrapper(Flow.Subscriber<? super List<ByteBuffer>> delegate,
                        System.Logger debug) {
            this.delegate = FlowTube.asTubeSubscriber(delegate);
            this.debug = debug;
        }

        @Override
        public void dropSubscription() {
            if (subscribedCalled && !completed) {
                delegate.dropSubscription();
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            assert subscribedCalled;
            delegate.onNext(item);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            onSubscribe(delegate::onSubscribe, subscription);
        }

        private void onSubscribe(Consumer<Flow.Subscription> method,
                                 Flow.Subscription subscription) {
            subscribedCalled = true;
            method.accept(subscription);
            Throwable x;
            boolean finished;
            synchronized (this) {
                subscribedDone = true;
                x = error;
                finished = completed;
            }
            if (x != null) {
                debug.log(Level.DEBUG,
                          "Subscriber completed before subscribe: forwarding %s",
                          (Object)x);
                delegate.onError(x);
            } else if (finished) {
                debug.log(Level.DEBUG,
                          "Subscriber completed before subscribe: calling onComplete()");
                delegate.onComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (completed) {
                debug.log(Level.DEBUG,
                          "Subscriber already completed: ignoring %s",
                          (Object)t);
                return;
            }
            boolean subscribed;
            synchronized (this) {
                if (completed) return;
                error = t;
                completed = true;
                subscribed = subscribedDone;
            }
            if (subscribed) {
                delegate.onError(t);
            } else {
                debug.log(Level.DEBUG,
                          "Subscriber not yet subscribed: stored %s",
                          (Object)t);
            }
        }

        @Override
        public void onComplete() {
            if (completed) return;
            boolean subscribed;
            synchronized (this) {
                if (completed) return;
                completed = true;
                subscribed = subscribedDone;
            }
            if (subscribed) {
                debug.log(Level.DEBUG, "DelegateWrapper: completing subscriber");
                delegate.onComplete();
            } else {
                debug.log(Level.DEBUG,
                          "Subscriber not yet subscribed: stored completed=true");
            }
        }

        @Override
        public String toString() {
            return "DelegateWrapper:" + delegate.toString();
        }

    }

    // Used to read data from the SSLTube.
    final class SSLSubscriberWrapper implements FlowTube.TubeSubscriber {
        private AtomicReference<DelegateWrapper> pendingDelegate =
                new AtomicReference<>();
        private volatile DelegateWrapper subscribed;
        private volatile boolean onCompleteReceived;
        private final AtomicReference<Throwable> errorRef
                = new AtomicReference<>();

        // setDelegate can be called asynchronously when the SSLTube flow
        // is connected. At this time the permanent subscriber (this class)
        // may already be subscribed (readSubscription != null) or not.
        // 1. If it's already subscribed (readSubscription != null), we
        //    are going to signal the SSLFlowDelegate reader, and make sure
        //    onSubscribed is called within the reader flow
        // 2. If it's not yet subscribed (readSubscription == null), then
        //    we're going to wait for onSubscribe to be called.
        //
        void setDelegate(Flow.Subscriber<? super List<ByteBuffer>> delegate) {
            debug.log(Level.DEBUG, "SSLSubscriberWrapper (reader) got delegate: %s",
                      delegate);
            assert delegate != null;
            DelegateWrapper delegateWrapper = new DelegateWrapper(delegate, debug);
            DelegateWrapper previous;
            Flow.Subscription subscription;
            boolean handleNow;
            synchronized (this) {
                previous = pendingDelegate.getAndSet(delegateWrapper);
                subscription = readSubscription;
                handleNow = this.errorRef.get() != null || finished;
            }
            if (previous != null) {
                previous.dropSubscription();
            }
            if (subscription == null) {
                debug.log(Level.DEBUG, "SSLSubscriberWrapper (reader) no subscription yet");
                return;
            }
            if (handleNow || !sslDelegate.resumeReader()) {
                processPendingSubscriber();
            }
        }

        // Can be called outside of the flow if an error has already been
        // raise. Otherwise, must be called within the SSLFlowDelegate
        // downstream reader flow.
        // If there is a subscription, and if there is a pending delegate,
        // calls dropSubscription() on the previous delegate (if any),
        // then subscribe the pending delegate.
        void processPendingSubscriber() {
            Flow.Subscription subscription;
            DelegateWrapper delegateWrapper, previous;
            synchronized (this) {
                delegateWrapper = pendingDelegate.get();
                if (delegateWrapper == null) return;
                subscription = readSubscription;
                previous = subscribed;
            }
            if (subscription == null) {
                debug.log(Level.DEBUG,
                         "SSLSubscriberWrapper (reader) %s",
                         "processPendingSubscriber: no subscription yet");
                return;
            }
            delegateWrapper = pendingDelegate.getAndSet(null);
            if (delegateWrapper == null) return;
            if (previous != null) {
                previous.dropSubscription();
            }
            onNewSubscription(delegateWrapper, subscription);
        }

        @Override
        public void dropSubscription() {
            DelegateWrapper subscriberImpl = subscribed;
            if (subscriberImpl != null) {
                subscriberImpl.dropSubscription();
            }
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onSubscribe(%s)",
                      subscription);
            onSubscribeImpl(subscription);
        }

        // called in the reader flow, from onSubscribe.
        private void onSubscribeImpl(Flow.Subscription subscription) {
            assert subscription != null;
            DelegateWrapper subscriberImpl, pending;
            synchronized (this) {
                readSubscription = subscription;
                subscriberImpl = subscribed;
                pending = pendingDelegate.get();
            }

            if (subscriberImpl == null && pending == null) {
                debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onSubscribeImpl: %s",
                      "no delegate yet");
                return;
            }

            if (pending == null) {
                // There is no pending delegate, but we have a previously
                // subscribed delegate. This is obviously a re-subscribe.
                // We are in the downstream reader flow, so we should call
                // onSubscribe directly.
                debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onSubscribeImpl: %s",
                      "resubscribing");
                onNewSubscription(subscriberImpl, subscription);
            } else {
                // We have some pending subscriber: subscribe it now that we have
                // a subscription. If we already had a previous delegate then
                // it will get a dropSubscription().
                debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onSubscribeImpl: %s",
                      "subscribing pending");
                processPendingSubscriber();
            }
        }

        private void onNewSubscription(DelegateWrapper subscriberImpl,
                                       Flow.Subscription subscription) {
            assert subscriberImpl != null;
            assert subscription != null;

            Throwable failed;
            boolean completed;
            // reset any demand that may have been made by the previous
            // subscriber
            sslDelegate.resetReaderDemand();
            // send the subscription to the subscriber.
            subscriberImpl.onSubscribe(subscription);

            // The following twisted logic is just here that we don't invoke
            // onError before onSubscribe. It also prevents race conditions
            // if onError is invoked concurrently with setDelegate.
            synchronized (this) {
                failed = this.errorRef.get();
                completed = finished;
                subscribed = subscriberImpl;
            }
            if (failed != null) {
                subscriberImpl.onError(failed);
            } else if (completed) {
                subscriberImpl.onComplete();
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            subscribed.onNext(item);
        }

        public void onErrorImpl(Throwable throwable) {
            // The following twisted logic is just here that we don't invoke
            // onError before onSubscribe. It also prevents race conditions
            // if onError is invoked concurrently with setDelegate.
            // See setDelegate.

            errorRef.compareAndSet(null, throwable);
            Throwable failed = errorRef.get();
            finished = true;
            debug.log(Level.DEBUG, "%s: onErrorImpl: %s", this, throwable);
            DelegateWrapper subscriberImpl;
            synchronized (this) {
                subscriberImpl = subscribed;
            }
            if (subscriberImpl != null) {
                subscriberImpl.onError(failed);
            } else {
                debug.log(Level.DEBUG, "%s: delegate null, stored %s", this, failed);
            }
            // now if we have any pending subscriber, we should forward
            // the error to them immediately as the read scheduler will
            // already be stopped.
            processPendingSubscriber();
        }

        @Override
        public void onError(Throwable throwable) {
            assert !finished && !onCompleteReceived;
            onErrorImpl(throwable);
        }

        private boolean handshaking() {
            HandshakeStatus hs = engine.getHandshakeStatus();
            return !(hs == NOT_HANDSHAKING || hs == FINISHED);
        }

        private boolean handshakeFailed() {
            // sslDelegate can be null if we reach here
            // during the initial handshake, as that happens
            // within the SSLFlowDelegate constructor.
            // In that case we will want to raise an exception.
            return handshaking()
                    && (sslDelegate == null
                    || !sslDelegate.closeNotifyReceived());
        }

        @Override
        public void onComplete() {
            assert !finished && !onCompleteReceived;
            onCompleteReceived = true;
            DelegateWrapper subscriberImpl;
            synchronized(this) {
                subscriberImpl = subscribed;
            }

            if (handshakeFailed()) {
                debug.log(Level.DEBUG,
                        "handshake: %s, inbound done: %s outbound done: %s",
                        engine.getHandshakeStatus(),
                        engine.isInboundDone(),
                        engine.isOutboundDone());
                onErrorImpl(new SSLHandshakeException(
                        "Remote host terminated the handshake"));
            } else if (subscriberImpl != null) {
                finished = true;
                subscriberImpl.onComplete();
            }
            // now if we have any pending subscriber, we should complete
            // them immediately as the read scheduler will already be stopped.
            processPendingSubscriber();
        }
    }

    @Override
    public void connectFlows(TubePublisher writePub,
                             TubeSubscriber readSub) {
        debug.log(Level.DEBUG, "connecting flows");
        readSubscriber.setDelegate(readSub);
        writePub.subscribe(this);
    }

    /** Outstanding write demand from the SSL Flow Delegate. */
    private final Demand writeDemand = new Demand();

    final class SSLSubscriptionWrapper implements Flow.Subscription {

        volatile Flow.Subscription delegate;

        void setSubscription(Flow.Subscription sub) {
            long demand = writeDemand.get(); // FIXME: isn't it a racy way of passing the demand?
            delegate = sub;
            debug.log(Level.DEBUG, "setSubscription: demand=%d", demand);
            if (demand > 0)
                sub.request(demand);
        }

        @Override
        public void request(long n) {
            writeDemand.increase(n);
            debug.log(Level.DEBUG, "request: n=%d", n);
            Flow.Subscription sub = delegate;
            if (sub != null && n > 0) {
                sub.request(n);
            }
        }

        @Override
        public void cancel() {
            // TODO:  no-op or error?
        }
    }

    /* Subscriber - writing side */
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        Objects.requireNonNull(subscription);
        Flow.Subscription x = writeSubscription.delegate;
        if (x != null)
            x.cancel();

        writeSubscription.setSubscription(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        Objects.requireNonNull(item);
        boolean decremented = writeDemand.tryDecrement();
        assert decremented : "Unexpected writeDemand: ";
        debug.log(Level.DEBUG,
                "sending %d  buffers to SSL flow delegate", item.size());
        sslDelegate.upstreamWriter().onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        Objects.requireNonNull(throwable);
        sslDelegate.upstreamWriter().onError(throwable);
    }

    @Override
    public void onComplete() {
        sslDelegate.upstreamWriter().onComplete();
    }

    @Override
    public String toString() {
        return dbgString();
    }

    final String dbgString() {
        return "SSLTube(" + tube + ")";
    }

}

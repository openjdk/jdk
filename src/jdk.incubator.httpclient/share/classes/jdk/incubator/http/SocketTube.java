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

package jdk.incubator.http;

import java.io.EOFException;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.incubator.http.internal.common.Demand;
import jdk.incubator.http.internal.common.FlowTube;
import jdk.incubator.http.internal.common.SequentialScheduler;
import jdk.incubator.http.internal.common.SequentialScheduler.DeferredCompleter;
import jdk.incubator.http.internal.common.SequentialScheduler.RestartableTask;
import jdk.incubator.http.internal.common.Utils;

/**
 * A SocketTube is a terminal tube plugged directly into the socket.
 * The read subscriber should call {@code subscribe} on the SocketTube before
 * the SocketTube can be subscribed to the write publisher.
 */
final class SocketTube implements FlowTube {

    static final boolean DEBUG = Utils.DEBUG; // revisit: temporary developer's flag
    final System.Logger  debug = Utils.getDebugLogger(this::dbgString, DEBUG);
    static final AtomicLong IDS = new AtomicLong();

    private final HttpClientImpl client;
    private final SocketChannel channel;
    private final Supplier<ByteBuffer> buffersSource;
    private final Object lock = new Object();
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final InternalReadPublisher readPublisher;
    private final InternalWriteSubscriber writeSubscriber;
    private final long id = IDS.incrementAndGet();

    public SocketTube(HttpClientImpl client, SocketChannel channel,
                      Supplier<ByteBuffer> buffersSource) {
        this.client = client;
        this.channel = channel;
        this.buffersSource = buffersSource;
        this.readPublisher = new InternalReadPublisher();
        this.writeSubscriber = new InternalWriteSubscriber();
    }

//    private static Flow.Subscription nopSubscription() {
//        return new Flow.Subscription() {
//            @Override public void request(long n) { }
//            @Override public void cancel() { }
//        };
//    }

    /**
     * Returns {@code true} if this flow is finished.
     * This happens when this flow internal read subscription is completed,
     * either normally (EOF reading) or exceptionally  (EOF writing, or
     * underlying socket closed, or some exception occurred while reading or
     * writing to the socket).
     *
     * @return {@code true} if this flow is finished.
     */
    public boolean isFinished() {
        InternalReadPublisher.InternalReadSubscription subscription =
                readPublisher.subscriptionImpl;
        return subscription != null && subscription.completed
                || subscription == null && errorRef.get() != null;
    }

    // ===================================================================== //
    //                       Flow.Publisher                                  //
    // ======================================================================//

    /**
     * {@inheritDoc }
     * @apiNote This method should be called first. In particular, the caller
     *          must ensure that this method must be called by the read
     *          subscriber before the write publisher can call {@code onSubscribe}.
     *          Failure to adhere to this contract may result in assertion errors.
     */
    @Override
    public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> s) {
        Objects.requireNonNull(s);
        assert s instanceof TubeSubscriber : "Expected TubeSubscriber, got:" + s;
        readPublisher.subscribe(s);
    }


    // ===================================================================== //
    //                       Flow.Subscriber                                 //
    // ======================================================================//

    /**
     * {@inheritDoc }
     * @apiNote The caller must ensure that {@code subscribe} is called by
     *          the read subscriber before {@code onSubscribe} is called by
     *          the write publisher.
     *          Failure to adhere to this contract may result in assertion errors.
     */
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        writeSubscriber.onSubscribe(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        writeSubscriber.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        writeSubscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
        writeSubscriber.onComplete();
    }

    // ===================================================================== //
    //                           Events                                      //
    // ======================================================================//

    /**
     * A restartable task used to process tasks in sequence.
     */
    private static class SocketFlowTask implements RestartableTask {
        final Runnable task;
        private final Object monitor = new Object();
        SocketFlowTask(Runnable task) {
            this.task = task;
        }
        @Override
        public final void run(DeferredCompleter taskCompleter) {
            try {
                // non contentious synchronized for visibility.
                synchronized(monitor) {
                    task.run();
                }
            } finally {
                taskCompleter.complete();
            }
        }
    }

    // This is best effort - there's no guarantee that the printed set
    // of values is consistent. It should only be considered as
    // weakly accurate - in particular in what concerns the events states,
    // especially when displaying a read event state from a write event
    // callback and conversely.
    void debugState(String when) {
        if (debug.isLoggable(Level.DEBUG)) {
            StringBuilder state = new StringBuilder();

            InternalReadPublisher.InternalReadSubscription sub =
                    readPublisher.subscriptionImpl;
            InternalReadPublisher.ReadEvent readEvent =
                    sub == null ? null : sub.readEvent;
            Demand rdemand = sub == null ? null : sub.demand;
            InternalWriteSubscriber.WriteEvent writeEvent =
                    writeSubscriber.writeEvent;
            AtomicLong wdemand = writeSubscriber.writeDemand;
            int rops = readEvent == null ? 0 : readEvent.interestOps();
            long rd = rdemand == null ? 0 : rdemand.get();
            int wops = writeEvent == null ? 0 : writeEvent.interestOps();
            long wd = wdemand == null ? 0 : wdemand.get();

            state.append(when).append(" Reading: [ops=")
                    .append(rops).append(", demand=").append(rd)
                    .append(", stopped=")
                    .append((sub == null ? false : sub.readScheduler.isStopped()))
                    .append("], Writing: [ops=").append(wops)
                    .append(", demand=").append(wd)
                    .append("]");
            debug.log(Level.DEBUG, state.toString());
        }
    }

    /**
     * A repeatable event that can be paused or resumed by changing
     * its interestOps.
     * When the event is fired, it is first paused before being signaled.
     * It is the responsibility of the code triggered by {@code signalEvent}
     * to resume the event if required.
     */
    private static abstract class SocketFlowEvent extends AsyncEvent {
        final SocketChannel channel;
        final int defaultInterest;
        volatile int interestOps;
        volatile boolean registered;
        SocketFlowEvent(int defaultInterest, SocketChannel channel) {
            super(AsyncEvent.REPEATING);
            this.defaultInterest = defaultInterest;
            this.channel = channel;
        }
        final boolean registered() {return registered;}
        final void resume() {
            interestOps = defaultInterest;
            registered = true;
        }
        final void pause() {interestOps = 0;}
        @Override
        public final SelectableChannel channel() {return channel;}
        @Override
        public final int interestOps() {return interestOps;}

        @Override
        public final void handle() {
            pause();       // pause, then signal
            signalEvent(); // won't be fired again until resumed.
        }
        @Override
        public final void abort(IOException error) {
            debug().log(Level.DEBUG, () -> "abort: " + error);
            pause();              // pause, then signal
            signalError(error);   // should not be resumed after abort (not checked)
        }

        protected abstract void signalEvent();
        protected abstract void signalError(Throwable error);
        abstract System.Logger debug();
    }

    // ===================================================================== //
    //                              Writing                                  //
    // ======================================================================//

    // This class makes the assumption that the publisher will call
    // onNext sequentially, and that onNext won't be called if the demand
    // has not been incremented by request(1).
    // It has a 'queue of 1' meaning that it will call request(1) in
    // onSubscribe, and then only after its 'current' buffer list has been
    // fully written and current set to null;
    private final class InternalWriteSubscriber
            implements Flow.Subscriber<List<ByteBuffer>> {

        volatile Flow.Subscription subscription;
        volatile List<ByteBuffer> current;
        volatile boolean completed;
        final WriteEvent writeEvent = new WriteEvent(channel, this);
        final AtomicLong writeDemand = new AtomicLong();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Flow.Subscription previous = this.subscription;
            this.subscription = subscription;
            debug.log(Level.DEBUG, "subscribed for writing");
            if (current == null) {
                if (previous == subscription || previous == null) {
                    if (writeDemand.compareAndSet(0, 1)) {
                        subscription.request(1);
                    }
                } else {
                    writeDemand.set(1);
                    subscription.request(1);
                }
            }
        }

        @Override
        public void onNext(List<ByteBuffer> bufs) {
            assert current == null; // this is a queue of 1.
            assert subscription != null;
            current = bufs;
            tryFlushCurrent(client.isSelectorThread()); // may be in selector thread
            // For instance in HTTP/2, a received SETTINGS frame might trigger
            // the sending of a SETTINGS frame in turn which might cause
            // onNext to be called from within the same selector thread that the
            // original SETTINGS frames arrived on. If rs is the read-subscriber
            // and ws is the write-subscriber then the following can occur:
            // ReadEvent -> rs.onNext(bytes) -> process server SETTINGS -> write
            // client SETTINGS -> ws.onNext(bytes) -> tryFlushCurrent
            debugState("leaving w.onNext");
        }

        // we don't use a SequentialScheduler here: we rely on
        // onNext() being called sequentially, and not being called
        // if we haven't call request(1)
        // onNext is usually called from within a user/executor thread.
        // we will perform the initial writing in that thread.
        // if for some reason, not all data can be written, the writeEvent
        // will be resumed, and the rest of the data will be written from
        // the selector manager thread when the writeEvent is fired.
        // If we are in the selector manager thread, then we will use the executor
        // to call request(1), ensuring that onNext() won't be called from
        // within the selector thread.
        // If we are not in the selector manager thread, then we don't care.
        void tryFlushCurrent(boolean inSelectorThread) {
            List<ByteBuffer> bufs = current;
            if (bufs == null) return;
            try {
                assert inSelectorThread == client.isSelectorThread() :
                       "should " + (inSelectorThread ? "" : "not ")
                        + " be in the selector thread";
                long remaining = Utils.remaining(bufs);
                debug.log(Level.DEBUG, "trying to write: %d", remaining);
                long written = writeAvailable(bufs);
                debug.log(Level.DEBUG, "wrote: %d", remaining);
                if (written == -1) {
                    signalError(new EOFException("EOF reached while writing"));
                    return;
                }
                assert written <= remaining;
                if (remaining - written == 0) {
                    current = null;
                    writeDemand.decrementAndGet();
                    Runnable requestMore = this::requestMore;
                    if (inSelectorThread) {
                        assert client.isSelectorThread();
                        client.theExecutor().execute(requestMore);
                    } else {
                        assert !client.isSelectorThread();
                        requestMore.run();
                    }
                } else {
                    resumeWriteEvent(inSelectorThread);
                }
            } catch (Throwable t) {
                signalError(t);
                subscription.cancel();
            }
        }

        void requestMore() {
            try {
                if (completed) return;
                long d =  writeDemand.get();
                if (writeDemand.compareAndSet(0,1)) {
                    debug.log(Level.DEBUG, "write: requesting more...");
                    subscription.request(1);
                } else {
                    debug.log(Level.DEBUG, "write: no need to request more: %d", d);
                }
            } catch (Throwable t) {
                debug.log(Level.DEBUG, () ->
                        "write: error while requesting more: " + t);
                signalError(t);
                subscription.cancel();
            } finally {
                debugState("leaving requestMore: ");
            }
        }

        @Override
        public void onError(Throwable throwable) {
            signalError(throwable);
        }

        @Override
        public void onComplete() {
            completed = true;
            // no need to pause the write event here: the write event will
            // be paused if there is nothing more to write.
            List<ByteBuffer> bufs = current;
            long remaining = bufs == null ? 0 : Utils.remaining(bufs);
            debug.log(Level.DEBUG,  "write completed, %d yet to send", remaining);
            debugState("InternalWriteSubscriber::onComplete");
        }

        void resumeWriteEvent(boolean inSelectorThread) {
            debug.log(Level.DEBUG, "scheduling write event");
            resumeEvent(writeEvent, this::signalError);
        }

//        void pauseWriteEvent() {
//            debug.log(Level.DEBUG, "pausing write event");
//            pauseEvent(writeEvent, this::signalError);
//        }

        void signalWritable() {
            debug.log(Level.DEBUG, "channel is writable");
            tryFlushCurrent(true);
        }

        void signalError(Throwable error) {
            debug.log(Level.DEBUG, () -> "write error: " + error);
            completed = true;
            readPublisher.signalError(error);
        }

        // A repeatable WriteEvent which is paused after firing and can
        // be resumed if required - see SocketFlowEvent;
        final class WriteEvent extends SocketFlowEvent {
            final InternalWriteSubscriber sub;
            WriteEvent(SocketChannel channel, InternalWriteSubscriber sub) {
                super(SelectionKey.OP_WRITE, channel);
                this.sub = sub;
            }
            @Override
            protected final void signalEvent() {
                try {
                    client.eventUpdated(this);
                    sub.signalWritable();
                } catch(Throwable t) {
                    sub.signalError(t);
                }
            }

            @Override
            protected void signalError(Throwable error) {
                sub.signalError(error);
            }

            @Override
            System.Logger debug() {
                return debug;
            }

        }

    }

    // ===================================================================== //
    //                              Reading                                  //
    // ===================================================================== //

    // The InternalReadPublisher uses a SequentialScheduler to ensure that
    // onNext/onError/onComplete are called sequentially on the caller's
    // subscriber.
    // However, it relies on the fact that the only time where
    // runOrSchedule() is called from a user/executor thread is in signalError,
    // right after the errorRef has been set.
    // Because the sequential scheduler's task always checks for errors first,
    // and always terminate the scheduler on error, then it is safe to assume
    // that if it reaches the point where it reads from the channel, then
    // it is running in the SelectorManager thread. This is because all
    // other invocation of runOrSchedule() are triggered from within a
    // ReadEvent.
    //
    // When pausing/resuming the event, some shortcuts can then be taken
    // when we know we're running in the selector manager thread
    // (in that case there's no need to call client.eventUpdated(readEvent);
    //
    private final class InternalReadPublisher
            implements Flow.Publisher<List<ByteBuffer>> {
        private final InternalReadSubscription subscriptionImpl
                = new InternalReadSubscription();
        AtomicReference<ReadSubscription> pendingSubscription = new AtomicReference<>();
        private volatile ReadSubscription subscription;

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> s) {
            Objects.requireNonNull(s);

            TubeSubscriber sub = FlowTube.asTubeSubscriber(s);
            ReadSubscription target = new ReadSubscription(subscriptionImpl, sub);
            ReadSubscription previous = pendingSubscription.getAndSet(target);

            if (previous != null && previous != target) {
                debug.log(Level.DEBUG,
                        () -> "read publisher: dropping pending subscriber: "
                        + previous.subscriber);
                previous.errorRef.compareAndSet(null, errorRef.get());
                previous.signalOnSubscribe();
                if (subscriptionImpl.completed) {
                    previous.signalCompletion();
                } else {
                    previous.subscriber.dropSubscription();
                }
            }

            debug.log(Level.DEBUG, "read publisher got subscriber");
            subscriptionImpl.signalSubscribe();
            debugState("leaving read.subscribe: ");
        }

        void signalError(Throwable error) {
            if (!errorRef.compareAndSet(null, error)) {
                return;
            }
            subscriptionImpl.handleError();
        }

        final class ReadSubscription implements Flow.Subscription {
            final InternalReadSubscription impl;
            final TubeSubscriber  subscriber;
            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            volatile boolean subscribed;
            volatile boolean cancelled;
            volatile boolean completed;

            public ReadSubscription(InternalReadSubscription impl,
                                    TubeSubscriber subscriber) {
                this.impl = impl;
                this.subscriber = subscriber;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public void request(long n) {
                if (!cancelled) {
                    impl.request(n);
                } else {
                    debug.log(Level.DEBUG,
                              "subscription cancelled, ignoring request %d", n);
                }
            }

            void signalCompletion() {
                assert subscribed || cancelled;
                if (completed || cancelled) return;
                synchronized (this) {
                    if (completed) return;
                    completed = true;
                }
                Throwable error = errorRef.get();
                if (error != null) {
                    debug.log(Level.DEBUG, () ->
                        "forwarding error to subscriber: "
                        + error);
                    subscriber.onError(error);
                } else {
                    debug.log(Level.DEBUG, "completing subscriber");
                    subscriber.onComplete();
                }
            }

            void signalOnSubscribe() {
                if (subscribed || cancelled) return;
                synchronized (this) {
                    if (subscribed || cancelled) return;
                    subscribed = true;
                }
                subscriber.onSubscribe(this);
                debug.log(Level.DEBUG, "onSubscribe called");
                if (errorRef.get() != null) {
                    signalCompletion();
                }
            }
        }

        final class InternalReadSubscription implements Flow.Subscription {

            private final Demand demand = new Demand();
            final SequentialScheduler readScheduler;
            private volatile boolean completed;
            private final ReadEvent readEvent;
            private final AsyncEvent subscribeEvent;

            InternalReadSubscription() {
                readScheduler = new SequentialScheduler(new SocketFlowTask(this::read));
                subscribeEvent = new AsyncTriggerEvent(this::signalError,
                                                       this::handleSubscribeEvent);
                readEvent = new ReadEvent(channel, this);
            }

            /*
             * This method must be invoked before any other method of this class.
             */
            final void signalSubscribe() {
                if (readScheduler.isStopped() || completed) {
                    // if already completed or stopped we can handle any
                    // pending connection directly from here.
                    debug.log(Level.DEBUG,
                              "handling pending subscription while completed");
                    handlePending();
                } else {
                    try {
                        debug.log(Level.DEBUG,
                                  "registering subscribe event");
                        client.registerEvent(subscribeEvent);
                    } catch (Throwable t) {
                        signalError(t);
                        handlePending();
                    }
                }
            }

            final void handleSubscribeEvent() {
                assert client.isSelectorThread();
                debug.log(Level.DEBUG, "subscribe event raised");
                readScheduler.runOrSchedule();
                if (readScheduler.isStopped() || completed) {
                    // if already completed or stopped we can handle any
                    // pending connection directly from here.
                    debug.log(Level.DEBUG,
                              "handling pending subscription when completed");
                    handlePending();
                }
            }


            /*
             * Although this method is thread-safe, the Reactive-Streams spec seems
             * to not require it to be as such. It's a responsibility of the
             * subscriber to signal demand in a thread-safe manner.
             *
             * https://github.com/reactive-streams/reactive-streams-jvm/blob/dd24d2ab164d7de6c316f6d15546f957bec29eaa/README.md
             * (rules 2.7 and 3.4)
             */
            @Override
            public final void request(long n) {
                if (n > 0L) {
                    boolean wasFulfilled = demand.increase(n);
                    if (wasFulfilled) {
                        debug.log(Level.DEBUG, "got some demand for reading");
                        resumeReadEvent();
                        // if demand has been changed from fulfilled
                        // to unfulfilled register read event;
                    }
                } else {
                    signalError(new IllegalArgumentException("non-positive request"));
                }
                debugState("leaving request("+n+"): ");
            }

            @Override
            public final void cancel() {
                pauseReadEvent();
                readScheduler.stop();
            }

            private void resumeReadEvent() {
                debug.log(Level.DEBUG, "resuming read event");
                resumeEvent(readEvent, this::signalError);
            }

            private void pauseReadEvent() {
                debug.log(Level.DEBUG, "pausing read event");
                pauseEvent(readEvent, this::signalError);
            }


            final void handleError() {
                assert errorRef.get() != null;
                readScheduler.runOrSchedule();
            }

            final void signalError(Throwable error) {
                if (!errorRef.compareAndSet(null, error)) {
                    return;
                }
                debug.log(Level.DEBUG, () -> "got read error: " + error);
                readScheduler.runOrSchedule();
            }

            final void signalReadable() {
                readScheduler.runOrSchedule();
            }

            /** The body of the task that runs in SequentialScheduler. */
            final void read() {
                // It is important to only call pauseReadEvent() when stopping
                // the scheduler. The event is automatically paused before
                // firing, and trying to pause it again could cause a race
                // condition between this loop, which calls tryDecrementDemand(),
                // and the thread that calls request(n), which will try to resume
                // reading.
                try {
                    while(!readScheduler.isStopped()) {
                        if (completed) return;

                        // make sure we have a subscriber
                        if (handlePending()) {
                            debug.log(Level.DEBUG, "pending subscriber subscribed");
                            return;
                        }

                        // If an error was signaled, we might not be in the
                        // the selector thread, and that is OK, because we
                        // will just call onError and return.
                        ReadSubscription current = subscription;
                        TubeSubscriber subscriber = current.subscriber;
                        Throwable error = errorRef.get();
                        if (error != null) {
                            completed = true;
                            // safe to pause here because we're finished anyway.
                            pauseReadEvent();
                            debug.log(Level.DEBUG, () -> "Sending error " + error
                                  + " to subscriber " + subscriber);
                            current.errorRef.compareAndSet(null, error);
                            current.signalCompletion();
                            readScheduler.stop();
                            debugState("leaving read() loop with error: ");
                            return;
                        }

                        // If we reach here then we must be in the selector thread.
                        assert client.isSelectorThread();
                        if (demand.tryDecrement()) {
                            // we have demand.
                            try {
                                List<ByteBuffer> bytes = readAvailable();
                                if (bytes == EOF) {
                                    if (!completed) {
                                        debug.log(Level.DEBUG, "got read EOF");
                                        completed = true;
                                        // safe to pause here because we're finished
                                        // anyway.
                                        pauseReadEvent();
                                        current.signalCompletion();
                                        readScheduler.stop();
                                    }
                                    debugState("leaving read() loop after EOF: ");
                                    return;
                                } else if (Utils.remaining(bytes) > 0) {
                                    // the subscriber is responsible for offloading
                                    // to another thread if needed.
                                    debug.log(Level.DEBUG, () -> "read bytes: "
                                            + Utils.remaining(bytes));
                                    assert !current.completed;
                                    subscriber.onNext(bytes);
                                    // we could continue looping until the demand
                                    // reaches 0. However, that would risk starving
                                    // other connections (bound to other socket
                                    // channels) - as other selected keys activated
                                    // by the selector manager thread might be
                                    // waiting for this event to terminate.
                                    // So resume the read event and return now...
                                    resumeReadEvent();
                                    debugState("leaving read() loop after onNext: ");
                                    return;
                                } else {
                                    // nothing available!
                                    debug.log(Level.DEBUG, "no more bytes available");
                                    // re-increment the demand and resume the read
                                    // event. This ensures that this loop is
                                    // executed again when the socket becomes
                                    // readable again.
                                    demand.increase(1);
                                    resumeReadEvent();
                                    debugState("leaving read() loop with no bytes");
                                    return;
                                }
                            } catch (Throwable x) {
                                signalError(x);
                                continue;
                            }
                        } else {
                            debug.log(Level.DEBUG, "no more demand for reading");
                            // the event is paused just after firing, so it should
                            // still be paused here, unless the demand was just
                            // incremented from 0 to n, in which case, the
                            // event will be resumed, causing this loop to be
                            // invoked again when the socket becomes readable:
                            // This is what we want.
                            // Trying to pause the event here would actually
                            // introduce a race condition between this loop and
                            // request(n).
                            debugState("leaving read() loop with no demand");
                            break;
                        }
                    }
                } catch (Throwable t) {
                    debug.log(Level.DEBUG, "Unexpected exception in read loop", t);
                    signalError(t);
                } finally {
                    handlePending();
                }
            }

            boolean handlePending() {
                ReadSubscription pending = pendingSubscription.getAndSet(null);
                if (pending == null) return false;
                debug.log(Level.DEBUG, "handling pending subscription for %s",
                          pending.subscriber);
                ReadSubscription current = subscription;
                if (current != null && current != pending && !completed) {
                    current.subscriber.dropSubscription();
                }
                debug.log(Level.DEBUG, "read demand reset to 0");
                subscriptionImpl.demand.reset(); // subscriber will increase demand if it needs to.
                pending.errorRef.compareAndSet(null, errorRef.get());
                if (!readScheduler.isStopped()) {
                    subscription = pending;
                } else {
                    debug.log(Level.DEBUG, "socket tube is already stopped");
                }
                debug.log(Level.DEBUG, "calling onSubscribe");
                pending.signalOnSubscribe();
                if (completed) {
                    pending.errorRef.compareAndSet(null, errorRef.get());
                    pending.signalCompletion();
                }
                return true;
            }
        }


        // A repeatable ReadEvent which is paused after firing and can
        // be resumed if required - see SocketFlowEvent;
        final class ReadEvent extends SocketFlowEvent {
            final InternalReadSubscription sub;
            ReadEvent(SocketChannel channel, InternalReadSubscription sub) {
                super(SelectionKey.OP_READ, channel);
                this.sub = sub;
            }
            @Override
            protected final void signalEvent() {
                try {
                    client.eventUpdated(this);
                    sub.signalReadable();
                } catch(Throwable t) {
                    sub.signalError(t);
                }
            }

            @Override
            protected final void signalError(Throwable error) {
                sub.signalError(error);
            }

            @Override
            System.Logger debug() {
                return debug;
            }
        }

    }

    // ===================================================================== //
    //                   Socket Channel Read/Write                           //
    // ===================================================================== //
    static final int MAX_BUFFERS = 3;
    static final List<ByteBuffer> EOF = List.of();

    private List<ByteBuffer> readAvailable() throws IOException {
        ByteBuffer buf = buffersSource.get();
        assert buf.hasRemaining();

        int read;
        int pos = buf.position();
        List<ByteBuffer> list = null;
        while (buf.hasRemaining()) {
            while ((read = channel.read(buf)) > 0) {
               if (!buf.hasRemaining()) break;
            }

            // nothing read;
            if (buf.position() == pos) {
                // An empty list signal the end of data, and should only be
                // returned if read == -1.
                // If we already read some data, then we must return what we have
                // read, and -1 will be returned next time the caller attempts to
                // read something.
                if (list == null && read == -1) {  // eof
                    list = EOF;
                    break;
                }
            }
            buf.limit(buf.position());
            buf.position(pos);
            if (list == null) {
                list = List.of(buf);
            } else {
                if (!(list instanceof ArrayList)) {
                    list = new ArrayList<>(list);
                }
                list.add(buf);
            }
            if (read <= 0 || list.size() == MAX_BUFFERS) break;
            buf = buffersSource.get();
            pos = buf.position();
            assert buf.hasRemaining();
        }
        return list;
    }

    private long writeAvailable(List<ByteBuffer> bytes) throws IOException {
        ByteBuffer[] srcs = bytes.toArray(Utils.EMPTY_BB_ARRAY);
        final long remaining = Utils.remaining(srcs);
        long written = 0;
        while (remaining > written) {
            long w = channel.write(srcs);
            if (w == -1 && written == 0) return -1;
            if (w == 0) break;
            written += w;
        }
        return written;
    }

    private void resumeEvent(SocketFlowEvent event,
                             Consumer<Throwable> errorSignaler) {
        boolean registrationRequired;
        synchronized(lock) {
            registrationRequired = !event.registered();
            event.resume();
        }
        try {
            if (registrationRequired) {
                client.registerEvent(event);
             } else {
                client.eventUpdated(event);
            }
        } catch(Throwable t) {
            errorSignaler.accept(t);
        }
   }

    private void pauseEvent(SocketFlowEvent event,
                            Consumer<Throwable> errorSignaler) {
        synchronized(lock) {
            event.pause();
        }
        try {
            client.eventUpdated(event);
        } catch(Throwable t) {
            errorSignaler.accept(t);
        }
    }

    @Override
    public void connectFlows(TubePublisher writePublisher,
                             TubeSubscriber readSubscriber) {
        debug.log(Level.DEBUG, "connecting flows");
        this.subscribe(readSubscriber);
        writePublisher.subscribe(this);
    }


    @Override
    public String toString() {
        return dbgString();
    }

    final String dbgString() {
        return "SocketTube("+id+")";
    }
}

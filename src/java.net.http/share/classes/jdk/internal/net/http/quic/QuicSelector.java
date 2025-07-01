/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.QuicEndpoint.QuicVirtualThreadedEndpoint;
import jdk.internal.net.http.quic.QuicEndpoint.QuicSelectableEndpoint;


/**
 * A QUIC selector to select over one or several quic transport
 * endpoints.
 */
public abstract sealed class QuicSelector<T extends QuicEndpoint> implements Runnable, AutoCloseable
            permits QuicSelector.QuicNioSelector, QuicSelector.QuicVirtualThreadPoller {

    /**
     * The maximum timeout passed to Selector::select.
     */
    public static final long IDLE_PERIOD_MS = 1500;

    private static final TimeLine source = TimeSource.source();
    final Logger debug = Utils.getDebugLogger(this::name);

    private final String name;
    private volatile boolean done;
    private final QuicInstance instance;
    private final QuicSelectorThread thread;
    private final QuicTimerQueue timerQueue;

    private QuicSelector(QuicInstance instance, String name) {
        this.instance = instance;
        this.name = name;
        this.timerQueue = new QuicTimerQueue(this::wakeup, debug);
        this.thread = new QuicSelectorThread(this);
    }

    public String name() {
        return name;
    }

    // must be overridden by subclasses
    public void register(T endpoint) throws ClosedChannelException {
        if (debug.on()) debug.log("attaching %s", endpoint);
    }

    // must be overridden by subclasses
    public void wakeup() {
        if (debug.on()) debug.log("waking up selector");
    }

    public QuicTimerQueue timer() {
        return timerQueue;
    }

    /**
     * A {@link QuicSelector} implementation based on blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * Virtual Threads to poll the channels.
     * This implementation is tied to {@link QuicVirtualThreadedEndpoint} instances.
     */
    static final class QuicVirtualThreadPoller extends QuicSelector<QuicVirtualThreadedEndpoint> {

        static final boolean usePlatformThreads =
                Utils.getBooleanProperty("jdk.internal.httpclient.quic.poller.usePlatformThreads", false);

        static final class EndpointTask implements Runnable {

            final QuicVirtualThreadedEndpoint endpoint;
            final ConcurrentLinkedQueue<EndpointTask> endpoints;
            EndpointTask(QuicVirtualThreadedEndpoint endpoint,
                         ConcurrentLinkedQueue<EndpointTask> endpoints) {
                this.endpoint = endpoint;
                this.endpoints = endpoints;
            }

            public void run() {
                try {
                    endpoint.channelReadLoop();
                } finally {
                    endpoints.remove(this);
                }
            }
        }

        private final Object waiter = new Object();
        private final ConcurrentLinkedQueue<EndpointTask> endpoints = new ConcurrentLinkedQueue<>();
        private final ReentrantLock stateLock = new ReentrantLock();
        private final ExecutorService virtualThreadService;

        private volatile long wakeups;


        private QuicVirtualThreadPoller(QuicInstance instance, String name) {
            super(instance, name);
            virtualThreadService = usePlatformThreads
                    ? Executors.newThreadPerTaskExecutor(Thread.ofPlatform()
                        .name(name + "-pt-worker", 1).factory())
                    : Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                            .name(name + "-vt-worker-", 1).factory());
            if (debug.on()) debug.log("created");
        }

        ExecutorService readLoopExecutor() {
            return virtualThreadService;
        }

        @Override
        public void register(QuicVirtualThreadedEndpoint endpoint) throws ClosedChannelException {
            super.register(endpoint);
            endpoint.attach(this);
        }

        public Future<?> startReading(QuicVirtualThreadedEndpoint endpoint) {
            EndpointTask task;
            stateLock.lock();
            try {
                if (done()) throw new ClosedSelectorException();
                task = new EndpointTask(endpoint, endpoints);
                endpoints.add(task);
                return virtualThreadService.submit(task);
            } finally {
                stateLock.unlock();
            }
        }

        void markDone() {
            // use stateLock to prevent startReading
            // to be called *after* shutdown.
            stateLock.lock();
            try {
                super.shutdown();
            } finally {
                stateLock.unlock();
            }
        }

        @Override
        public void shutdown() {
            markDone();
            try {
                virtualThreadService.shutdown();
            } finally {
                wakeup();
            }
        }

        @Override
        public void wakeup() {
            super.wakeup();
            synchronized (waiter) {
                wakeups++;
                waiter.notify();
            }
        }

        @Override
        public void run() {
            try {
                if (debug.on()) debug.log("started");
                long waited = 0;
                while (!done()) {
                    var wakeups = this.wakeups;
                    long timeout = Math.min(computeNextDeadLine(), IDLE_PERIOD_MS);
                    if (Log.quicTimer()) {
                        Log.logQuic(String.format("%s: wait(%s) wakeups:%s (+%s), waited:%s",
                                name(), timeout,  this.wakeups, this.wakeups - wakeups, waited));
                    } else if (debug.on()) {
                        debug.log("wait(%s) wakeups:%s (+%s), waited: %s",
                                timeout, this.wakeups, this.wakeups - wakeups, waited);
                    }
                    long wwaited = -1;
                    synchronized (waiter) {
                        if (done()) return;
                        if (wakeups == this.wakeups) {
                            var start = System.nanoTime();
                            waiter.wait(timeout);
                            var stop = System.nanoTime();
                            wwaited = waited = (stop - start) / 1000_000;
                        } else waited = 0;
                    }
                    if (wwaited != -1 && wwaited < timeout) {
                        if (Log.quicTimer()) {
                            Log.logQuic(String.format("%s: waked up early: waited %s, timeout %s",
                                    name(), waited, timeout));
                        }
                    }
                }
            } catch (Throwable t) {
                if (done()) return;
                if (debug.on()) debug.log("Selector failed", t);
                if (Log.errors()) {
                    Log.logError("QuicVirtualThreadPoller: selector exiting due to " + t);
                    Log.logError(t);
                }
                abort(t);
            } finally {
                if (debug.on()) debug.log("exiting");
                if (!done()) markDone();
                timer().stop();
                endpoints.removeIf(this::close);
                virtualThreadService.close();
            }
        }

        boolean close(EndpointTask task) {
            try {
                task.endpoint.close();
            } catch (Throwable e) {
                if (debug.on()) {
                    debug.log("Failed to close endpoint %s: %s", task.endpoint.name(), e);
                }
            }
            return true;
        }

        boolean abort(EndpointTask task, Throwable error) {
            try {
                task.endpoint.abort(error);
            } catch (Throwable e) {
                if (debug.on()) {
                    debug.log("Failed to close endpoint %s: %s", task.endpoint.name(), e);
                }
            }
            return true;
        }

        @Override
        public void abort(Throwable t) {
            super.shutdown();
            endpoints.removeIf(task -> abort(task, t));
            super.abort(t);
        }
    }

    /**
     * A {@link QuicSelector} implementation based on non-blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * NIO {@link Selector}.
     * This implementation is tied to {@link QuicSelectableEndpoint} instances.
     */
    static final class QuicNioSelector extends QuicSelector<QuicSelectableEndpoint> {
        final Selector selector;

        private QuicNioSelector(QuicInstance instance, Selector selector, String name) {
            super(instance, name);
            this.selector = selector;
            if (debug.on()) debug.log("created");
        }


        public void register(QuicSelectableEndpoint endpoint) throws ClosedChannelException {
            super.register(endpoint);
            endpoint.attach(selector);
            selector.wakeup();
        }

        public void wakeup() {
            super.wakeup();
            selector.wakeup();
        }

        /**
         * Shuts down the {@code QuicSelector} by marking the
         * {@linkplain QuicSelector#shutdown() selector done},
         * and {@linkplain Selector#wakeup() waking up the
         * selector thread}.
         * Upon waking up, the selector thread will invoke
         * {@link Selector#close()}.
         * This method doesn't wait for the selector thread to terminate.
         * @see #awaitTermination(long, TimeUnit)
         */
        public void shutdown() {
            super.shutdown();
            selector.wakeup();
        }

        @Override
        public void run() {
            try {
                if (debug.on()) debug.log("started");
                while (!done()) {
                    long timeout = Math.min(computeNextDeadLine(), IDLE_PERIOD_MS);
                    // selected = 0 indicates that no key had its ready ops changed:
                    // it doesn't mean that no key is ready. Therefore - if a key
                    // was ready to read, and is again ready to read, it doesn't
                    // increment the selected count.
                    if (debug.on()) debug.log("select(%s)", timeout);
                    int selected = selector.select(timeout);
                    var selectedKeys = selector.selectedKeys();
                    if (debug.on()) {
                        debug.log("Selected: changes=%s, keys=%s", selected, selectedKeys.size());
                    }

                    // We do not synchronize on selectedKeys: selectedKeys is only
                    // modified in this thread, whether directly, by calling selectedKeys.clear() below,
                    // or indirectly, by calling selector.close() below.
                    for (var key : selectedKeys) {
                        QuicSelectableEndpoint endpoint = (QuicSelectableEndpoint) key.attachment();
                        if (debug.on()) {
                            debug.log("selected(%s): %s", Utils.readyOps(key), endpoint);
                        }
                        try {
                            endpoint.selected(key.readyOps());
                        } catch (CancelledKeyException x) {
                            if (debug.on()) {
                                debug.log("Key for %s cancelled: %s", endpoint.name(), x);
                            }
                        }
                    }
                    // need to clear the selected keys. select won't do that.
                    selectedKeys.clear();
                }
            } catch (Throwable t) {
                if (done()) return;
                if (debug.on()) debug.log("Selector failed", t);
                if (Log.errors()) {
                    Log.logError("QuicNioSelector: selector exiting due to " + t);
                    Log.logError(t);
                }
                abort(t);
            } finally {
                if (debug.on()) debug.log("exiting");
                timer().stop();

                try {
                    selector.close();
                } catch (IOException io) {
                    if (debug.on()) debug.log("failed to close selector: " + io);
                }
            }
        }

        boolean abort(SelectionKey key, Throwable error) {
            try {
                QuicSelectableEndpoint endpoint = (QuicSelectableEndpoint) key.attachment();
                endpoint.abort(error);
            } catch (Throwable e) {
                if (debug.on()) {
                    debug.log("Failed to close endpoint associated with key %s: %s", key, error);
                }
            }
            return true;
        }

        @Override
        public void abort(Throwable error) {
            super.shutdown();
            try {
                if (selector.isOpen()) {
                    for (var k : selector.keys()) {
                        abort(k, error);
                    }
                }
            } catch (ClosedSelectorException cse) {
                // ignore
            } finally {
                super.abort(error);
            }
        }
    }

    public long computeNextDeadLine() {
        Deadline now = source.instant();
        Deadline deadline = timerQueue.processEventsAndReturnNextDeadline(now, instance.executor());
        if (deadline.equals(Deadline.MAX)) return IDLE_PERIOD_MS;
        if (deadline.equals(Deadline.MIN)) {
            if (Log.quicTimer()) {
                Log.logQuic(String.format("%s: %s millis until %s", name, 1, "now"));
            }
            return 1;
        }
        now = source.instant();
        long millis = now.until(deadline, ChronoUnit.MILLIS);
        // millis could be 0 if the next deadline is within 1ms of now.
        // in that case, round up millis to 1ms since returning 0
        // means the selector would block indefinitely
        if (Log.quicTimer()) {
            Log.logQuic(String.format("%s: %s millis until %s",
                    name, (millis <= 0L ? 1L : millis), deadline));
        }
        return millis <= 0L ? 1L : millis;
    }

    public void start() {
        thread.start();
    }

    /**
     * Shuts down the {@code QuicSelector} by invoking {@link Selector#close()}.
     * This method doesn't wait for the selector thread to terminate.
     * @see #awaitTermination(long, TimeUnit)
     */
    public void shutdown() {
        if (debug.on()) debug.log("closing");
        done = true;
    }

    boolean done() {
        return done;
    }

    /**
     * Awaits termination of the selector thread, up until
     * the given timeout has elapsed.
     * If the current thread is the selector thread, returns
     * immediately without waiting.
     *
     * @param timeout the maximum time to wait for termination
     * @param unit    the timeout unit
     */
    public void awaitTermination(long timeout, TimeUnit unit) {
        if (Thread.currentThread() == thread) {
            return;
        }
        try {
            thread.join(unit.toMillis(timeout));
        } catch (InterruptedException ie) {
            if (debug.on()) debug.log("awaitTermination interrupted: " + ie);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes this {@code QuicSelector}.
     * This method calls {@link #shutdown()} and then {@linkplain
     * #awaitTermination(long, TimeUnit) waits for the selector thread
     * to terminate}, up to two {@link #IDLE_PERIOD_MS}.
     */
    @Override
    public void close() {
        shutdown();
        awaitTermination(IDLE_PERIOD_MS * 2, TimeUnit.MILLISECONDS);
    }

    @Override
    public String toString() {
        return name;
    }

    // Called in case of RejectedExecutionException, or shutdownNow;
    public void abort(Throwable t) {
        shutdown();
    }

    static class QuicSelectorThread extends Thread {
        QuicSelectorThread(QuicSelector<?> selector)  {
            super(null, selector,
                    "Thread(%s)".formatted(selector.name()),
                    0, false);
            this.setDaemon(true);
        }
    }

    /**
     * {@return a new instance of {@code QuicNioSelector}}
     * <p>
     * A {@code QuicNioSelector} is an implementation of {@link QuicSelector}
     * based on non blocking {@linkplain DatagramChannel Datagram Channels} and
     * using an underlying {@linkplain Selector NIO Selector}.
     * <p>
     * The returned implementation can only be used with
     * {@link QuicSelectableEndpoint} endpoints.
     *
     * @param quicInstance the quic instance
     * @param name the selector name
     * @throws IOException if an IOException occurs when creating the underlying {@link Selector}
     */
    public static QuicSelector<? extends QuicEndpoint> createQuicNioSelector(QuicInstance quicInstance, String name)
            throws IOException {
        Selector selector = Selector.open();
        return new QuicNioSelector(quicInstance, selector, name);
    }

    /**
     * {@return a new instance of {@code QuicVirtualThreadPoller}}
     * A {@code QuicVirtualThreadPoller} is an implementation of
     * {@link QuicSelector} based on blocking {@linkplain DatagramChannel
     * Datagram Channels} and using {@linkplain Thread#ofVirtual()
     * Virtual Threads} to poll the datagram channels.
     * <p>
     * The returned implementation can only be used with
     * {@link QuicVirtualThreadedEndpoint} endpoints.
     *
     * @param quicInstance the quic instance
     * @param name the selector name
     */
    public static QuicSelector<? extends QuicEndpoint> createQuicVirtualThreadPoller(QuicInstance quicInstance, String name) {
        return new QuicVirtualThreadPoller(quicInstance, name);
    }
}

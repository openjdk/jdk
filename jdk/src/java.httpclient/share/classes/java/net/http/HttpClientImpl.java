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
 */
package java.net.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;

import static java.net.http.Utils.BUFSIZE;

/**
 * Client implementation. Contains all configuration information and also
 * the selector manager thread which allows async events to be registered
 * and delivered when they occur. See AsyncEvent.
 */
class HttpClientImpl extends HttpClient implements BufferHandler {

    private static final ThreadFactory defaultFactory =
            (r -> new Thread(null, r, "HttpClient_worker", 0, true));

    private final CookieManager cookieManager;
    private final Redirect followRedirects;
    private final ProxySelector proxySelector;
    private final Authenticator authenticator;
    private final Version version;
    private boolean pipelining = false;
    private final ConnectionPool connections;
    private final ExecutorWrapper executor;
    // Security parameters
    private final SSLContext sslContext;
    private final SSLParameters sslParams;
    private final SelectorManager selmgr;
    private final FilterFactory filters;
    private final Http2ClientImpl client2;
    private final LinkedList<TimeoutEvent> timeouts;

    public static HttpClientImpl create(HttpClientBuilderImpl builder) {
        HttpClientImpl impl = new HttpClientImpl(builder);
        impl.start();
        return impl;
    }

    private HttpClientImpl(HttpClientBuilderImpl builder) {
        if (builder.sslContext == null) {
            try {
                sslContext = SSLContext.getDefault();
            } catch (NoSuchAlgorithmException ex) {
                throw new InternalError(ex);
            }
        } else {
            sslContext = builder.sslContext;
        }
        ExecutorService ex = builder.executor;
        if (ex == null) {
            ex = Executors.newCachedThreadPool((r) -> {
                Thread t = defaultFactory.newThread(r);
                t.setDaemon(true);
                return t;
            });
        } else {
            ex = builder.executor;
        }
        client2 = new Http2ClientImpl(this);
        executor = ExecutorWrapper.wrap(ex);
        cookieManager = builder.cookieManager;
        followRedirects = builder.followRedirects == null ?
                Redirect.NEVER : builder.followRedirects;
        this.proxySelector = builder.proxy;
        authenticator = builder.authenticator;
        version = builder.version;
        sslParams = builder.sslParams;
        connections = new ConnectionPool();
        connections.start();
        timeouts = new LinkedList<>();
        try {
            selmgr = new SelectorManager();
        } catch (IOException e) {
            // unlikely
            throw new InternalError(e);
        }
        selmgr.setDaemon(true);
        filters = new FilterFactory();
        initFilters();
    }

    private void start() {
        selmgr.start();
    }

    /**
     * Wait for activity on given exchange (assuming blocking = false).
     * It's a no-op if blocking = true. In particular, the following occurs
     * in the SelectorManager thread.
     *
     *  1) mark the connection non-blocking
     *  2) add to selector
     *  3) If selector fires for this exchange then
     *  4)   - mark connection as blocking
     *  5)   - call AsyncEvent.handle()
     *
     * If exchange needs to block again, then call registerEvent() again
     */
    void registerEvent(AsyncEvent exchange) throws IOException {
        selmgr.register(exchange);
    }

    Http2ClientImpl client2() {
        return client2;
    }

    /**
     * We keep one size of buffer on free list. That size may increase
     * depending on demand. If that happens we dispose of free buffers
     * that are smaller than new size.
     */
    private final LinkedList<ByteBuffer> freelist = new LinkedList<>();
    int currentSize = BUFSIZE;

    @Override
    public synchronized ByteBuffer getBuffer(int size) {

        ByteBuffer buf;
        if (size == -1)
            size = currentSize;

        if (size > currentSize)
            currentSize = size;

        while (!freelist.isEmpty()) {
            buf = freelist.removeFirst();
            if (buf.capacity() < currentSize)
                continue;
            buf.clear();
            return buf;
        }
        return ByteBuffer.allocate(size);
    }

    @Override
    public synchronized void returnBuffer(ByteBuffer buffer) {
        freelist.add(buffer);
    }

    @Override
    public synchronized void setMinBufferSize(int n) {
        currentSize = Math.max(n, currentSize);
    }

    // Main loop for this client's selector
    private final class SelectorManager extends Thread {

        private final Selector selector;
        private volatile boolean closed;
        private final List<AsyncEvent> readyList;
        private final List<AsyncEvent> registrations;

        SelectorManager() throws IOException {
            super(null, null, "SelectorManager", 0, false);
            readyList = new ArrayList<>();
            registrations = new ArrayList<>();
            selector = Selector.open();
        }

        // This returns immediately. So caller not allowed to send/receive
        // on connection.

        synchronized void register(AsyncEvent e) throws IOException {
            registrations.add(e);
            selector.wakeup();
        }

        void wakeupSelector() {
            selector.wakeup();
        }

        synchronized void shutdown() {
            closed = true;
            try {
                selector.close();
            } catch (IOException ignored) { }
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    synchronized (this) {
                        for (AsyncEvent exchange : registrations) {
                            SelectableChannel c = exchange.channel();
                            try {
                                c.configureBlocking(false);
                                SelectionKey key = c.keyFor(selector);
                                SelectorAttachment sa;
                                if (key == null) {
                                    sa = new SelectorAttachment(c, selector);
                                } else {
                                    sa = (SelectorAttachment) key.attachment();
                                }
                                sa.register(exchange);
                            } catch (IOException e) {
                                Log.logError("HttpClientImpl: " + e);
                                c.close();
                                // let the exchange deal with it
                                handleEvent(exchange);
                            }
                        }
                        registrations.clear();
                    }
                    long timeval = getTimeoutValue();
                    long now = System.currentTimeMillis();
                    //debugPrint(selector);
                    int n = selector.select(timeval);
                    if (n == 0) {
                        signalTimeouts(now);
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();

                    for (SelectionKey key : keys) {
                        SelectorAttachment sa = (SelectorAttachment) key.attachment();
                        int eventsOccurred = key.readyOps();
                        sa.events(eventsOccurred).forEach(readyList::add);
                        sa.resetInterestOps(eventsOccurred);
                    }
                    selector.selectNow(); // complete cancellation
                    selector.selectedKeys().clear();

                    for (AsyncEvent exchange : readyList) {
                        if (exchange.blocking()) {
                            exchange.channel().configureBlocking(true);
                        }
                        executor.synchronize();
                        handleEvent(exchange); // will be delegated to executor
                    }
                    readyList.clear();
                }
            } catch (Throwable e) {
                if (!closed) {
                    // This terminates thread. So, better just print stack trace
                    String err = Utils.stackTrace(e);
                    Log.logError("HttpClientImpl: fatal error: " + err);
                }
            } finally {
                shutdown();
            }
        }

        void debugPrint(Selector selector) {
            System.err.println("Selector: debugprint start");
            Set<SelectionKey> keys = selector.keys();
            for (SelectionKey key : keys) {
                SelectableChannel c = key.channel();
                int ops = key.interestOps();
                System.err.printf("selector chan:%s ops:%d\n", c, ops);
            }
            System.err.println("Selector: debugprint end");
        }

        void handleEvent(AsyncEvent e) {
            if (closed) {
                e.abort();
            } else {
                e.handle();
            }
        }
    }

    /**
     * Tracks multiple user level registrations associated with one NIO
     * registration (SelectionKey). In this implementation, registrations
     * are one-off and when an event is posted the registration is cancelled
     * until explicitly registered again.
     *
     * <p> No external synchronization required as this class is only used
     * by the SelectorManager thread. One of these objects required per
     * connection.
     */
    private static class SelectorAttachment {
        private final SelectableChannel chan;
        private final Selector selector;
        private final ArrayList<AsyncEvent> pending;
        private int interestOps;

        SelectorAttachment(SelectableChannel chan, Selector selector) {
            this.pending = new ArrayList<>();
            this.chan = chan;
            this.selector = selector;
        }

        void register(AsyncEvent e) throws ClosedChannelException {
            int newOps = e.interestOps();
            boolean reRegister = (interestOps & newOps) != newOps;
            interestOps |= newOps;
            pending.add(e);
            if (reRegister) {
                // first time registration happens here also
                chan.register(selector, interestOps, this);
            }
        }

        /**
         * Returns a Stream<AsyncEvents> containing only events that are
         * registered with the given {@code interestOps}.
         */
        Stream<AsyncEvent> events(int interestOps) {
            return pending.stream()
                    .filter(ev -> (ev.interestOps() & interestOps) != 0);
        }

        /**
         * Removes any events with the given {@code interestOps}, and if no
         * events remaining, cancels the associated SelectionKey.
         */
        void resetInterestOps(int interestOps) {
            int newOps = 0;

            Iterator<AsyncEvent> itr = pending.iterator();
            while (itr.hasNext()) {
                AsyncEvent event = itr.next();
                int evops = event.interestOps();
                if (event.repeating()) {
                    newOps |= evops;
                    continue;
                }
                if ((evops & interestOps) != 0) {
                    itr.remove();
                } else {
                    newOps |= evops;
                }
            }

            this.interestOps = newOps;
            SelectionKey key = chan.keyFor(selector);
            if (newOps == 0) {
                key.cancel();
            } else {
                key.interestOps(newOps);
            }
        }
    }

    /**
     * Creates a HttpRequest associated with this group.
     *
     * @throws IllegalStateException
     *         if the group has been stopped
     */
    @Override
    public HttpRequestBuilderImpl request() {
        return new HttpRequestBuilderImpl(this, null);
    }

    /**
     * Creates a HttpRequest associated with this group.
     *
     * @throws IllegalStateException
     *         if the group has been stopped
     */
    @Override
    public HttpRequestBuilderImpl request(URI uri) {
        return new HttpRequestBuilderImpl(this, uri);
    }

    @Override
    public SSLContext sslContext() {
        Utils.checkNetPermission("getSSLContext");
        return sslContext;
    }

    @Override
    public Optional<SSLParameters> sslParameters() {
        return Optional.ofNullable(sslParams);
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.ofNullable(authenticator);
    }

    @Override
    public ExecutorService executorService() {
        return executor.userExecutor();
    }

    ExecutorWrapper executorWrapper() {
        return executor;
    }

    @Override
    public boolean pipelining() {
        return this.pipelining;
    }

    ConnectionPool connectionPool() {
        return connections;
    }

    @Override
    public Redirect followRedirects() {
        return followRedirects;
    }


    @Override
    public Optional<CookieManager> cookieManager() {
        return Optional.ofNullable(cookieManager);
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.ofNullable(this.proxySelector);
    }

    @Override
    public Version version() {
        return version;
    }

    //private final HashMap<String, Boolean> http2NotSupported = new HashMap<>();

    boolean getHttp2Allowed() {
        return version.equals(Version.HTTP_2);
    }

    private void initFilters() {
        addFilter(AuthenticationFilter.class);
        addFilter(RedirectFilter.class);
    }

    private void addFilter(Class<? extends HeaderFilter> f) {
        filters.addFilter(f);
    }

    final List<HeaderFilter> filterChain() {
        return filters.getFilterChain();
    }

    // Timer controls. Timers are implemented through timed Selector.select()
    // calls.
    synchronized void registerTimer(TimeoutEvent event) {
        long elapse = event.timevalMillis();
        ListIterator<TimeoutEvent> iter = timeouts.listIterator();
        long listval = 0;
        event.delta = event.timeval; // in case list empty
        TimeoutEvent next;
        while (iter.hasNext()) {
            next = iter.next();
            listval += next.delta;
            if (elapse < listval) {
                listval -= next.delta;
                event.delta = elapse - listval;
                next.delta -= event.delta;
                iter.previous();
                break;
            } else if (!iter.hasNext()) {
                event.delta = event.timeval - listval;
            }
        }
        iter.add(event);
        selmgr.wakeupSelector();
    }

    private synchronized void signalTimeouts(long then) {
        if (timeouts.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long duration = now - then;
        ListIterator<TimeoutEvent> iter = timeouts.listIterator();
        TimeoutEvent event = iter.next();
        long delta = event.delta;
        if (duration < delta) {
            event.delta -= duration;
            return;
        }
        event.handle();
        iter.remove();
        while (iter.hasNext()) {
            event = iter.next();
            if (event.delta == 0) {
                event.handle();
                iter.remove();
            } else {
                event.delta += delta;
                break;
            }
        }
    }

    synchronized void cancelTimer(TimeoutEvent event) {
        ListIterator<TimeoutEvent> iter = timeouts.listIterator();
        while (iter.hasNext()) {
            TimeoutEvent ev = iter.next();
            if (event == ev) {
                if (iter.hasNext()) {
                    // adjust
                    TimeoutEvent next = iter.next();
                    next.delta += ev.delta;
                    iter.previous();
                }
                iter.remove();
            }
        }
    }

    // used for the connection window
    int getReceiveBufferSize() {
        return Utils.getIntegerNetProperty(
                "java.net.httpclient.connectionWindowSize", 256 * 1024
        );
    }

    // returns 0 meaning block forever, or a number of millis to block for
    private synchronized long getTimeoutValue() {
        if (timeouts.isEmpty()) {
            return 0;
        } else {
            return timeouts.get(0).delta;
        }
    }
}

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

package jdk.incubator.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.Utils;
import jdk.incubator.http.internal.websocket.BuilderImpl;

/**
 * Client implementation. Contains all configuration information and also
 * the selector manager thread which allows async events to be registered
 * and delivered when they occur. See AsyncEvent.
 */
class HttpClientImpl extends HttpClient {

    // Define the default factory as a static inner class
    // that embeds all the necessary logic to avoid
    // the risk of using a lambda that might keep a reference on the
    // HttpClient instance from which it was created (helps with
    // heapdump analysis).
    private static final class DefaultThreadFactory implements ThreadFactory {
        private DefaultThreadFactory() {}
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(null, r, "HttpClient_worker", 0, true);
            t.setDaemon(true);
            return t;
        }
        static final ThreadFactory INSTANCE = new DefaultThreadFactory();
    }

    private final CookieManager cookieManager;
    private final Redirect followRedirects;
    private final ProxySelector proxySelector;
    private final Authenticator authenticator;
    private final Version version;
    private final ConnectionPool connections;
    private final Executor executor;
    // Security parameters
    private final SSLContext sslContext;
    private final SSLParameters sslParams;
    private final SelectorManager selmgr;
    private final FilterFactory filters;
    private final Http2ClientImpl client2;

    /** A Set of, deadline first, ordered timeout events. */
    private final TreeSet<TimeoutEvent> timeouts;

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
        Executor ex = builder.executor;
        if (ex == null) {
            ex = Executors.newCachedThreadPool(DefaultThreadFactory.INSTANCE);
        } else {
            ex = builder.executor;
        }
        client2 = new Http2ClientImpl(this);
        executor = ex;
        cookieManager = builder.cookieManager;
        followRedirects = builder.followRedirects == null ?
                Redirect.NEVER : builder.followRedirects;
        this.proxySelector = builder.proxy;
        authenticator = builder.authenticator;
        if (builder.version == null) {
            version = HttpClient.Version.HTTP_2;
        } else {
            version = builder.version;
        }
        if (builder.sslParams == null) {
            sslParams = getDefaultParams(sslContext);
        } else {
            sslParams = builder.sslParams;
        }
        connections = new ConnectionPool();
        connections.start();
        timeouts = new TreeSet<>();
        try {
            selmgr = new SelectorManager(this);
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

    private static SSLParameters getDefaultParams(SSLContext ctx) {
        SSLParameters params = ctx.getSupportedSSLParameters();
        params.setProtocols(new String[]{"TLSv1.2"});
        return params;
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

    /**
     * Only used from RawChannel to disconnect the channel from
     * the selector
     */
    void cancelRegistration(SocketChannel s) {
        selmgr.cancel(s);
    }


    Http2ClientImpl client2() {
        return client2;
    }

    /*
    @Override
    public ByteBuffer getBuffer() {
        return pool.getBuffer();
    }

    // SSL buffers are larger. Manage separately

    int size = 16 * 1024;

    ByteBuffer getSSLBuffer() {
        return ByteBuffer.allocate(size);
    }

    /**
     * Return a new buffer that's a bit bigger than the given one
     *
     * @param buf
     * @return
     *
    ByteBuffer reallocSSLBuffer(ByteBuffer buf) {
        size = buf.capacity() * 12 / 10; // 20% bigger
        return ByteBuffer.allocate(size);
    }

    synchronized void returnSSLBuffer(ByteBuffer buf) {
        if (buf.capacity() >= size)
           sslBuffers.add(0, buf);
    }

    @Override
    public void returnBuffer(ByteBuffer buffer) {
        pool.returnBuffer(buffer);
    }
    */

    @Override
    public <T> HttpResponse<T>
    send(HttpRequest req, HttpResponse.BodyHandler<T> responseHandler)
        throws IOException, InterruptedException
    {
        MultiExchange<Void,T> mex = new MultiExchange<>(req, this, responseHandler);
        return mex.response();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>>
    sendAsync(HttpRequest req, HttpResponse.BodyHandler<T> responseHandler)
    {
        MultiExchange<Void,T> mex = new MultiExchange<>(req, this, responseHandler);
        return mex.responseAsync()
                  .thenApply((HttpResponseImpl<T> b) -> (HttpResponse<T>) b);
    }

    @Override
    public <U, T> CompletableFuture<U>
    sendAsync(HttpRequest req, HttpResponse.MultiProcessor<U, T> responseHandler) {
        MultiExchange<U,T> mex = new MultiExchange<>(req, this, responseHandler);
        return mex.multiResponseAsync();
    }

    // new impl. Should get rid of above
    /*
    static class BufferPool implements BufferHandler {

        final LinkedList<ByteBuffer> freelist = new LinkedList<>();

        @Override
        public synchronized ByteBuffer getBuffer() {
            ByteBuffer buf;

            while (!freelist.isEmpty()) {
                buf = freelist.removeFirst();
                buf.clear();
                return buf;
            }
            return ByteBuffer.allocate(BUFSIZE);
        }

        @Override
        public synchronized void returnBuffer(ByteBuffer buffer) {
            assert buffer.capacity() > 0;
            freelist.add(buffer);
        }
    }

    static BufferPool pool = new BufferPool();

    static BufferHandler pool() {
        return pool;
    }
*/
    // Main loop for this client's selector
    private final static class SelectorManager extends Thread {

        private static final long NODEADLINE = 3000L;
        private final Selector selector;
        private volatile boolean closed;
        private final List<AsyncEvent> readyList;
        private final List<AsyncEvent> registrations;

        // Uses a weak reference to the HttpClient owning this
        // selector: a strong reference prevents its garbage
        // collection while the thread is running.
        // We want the thread to exit gracefully when the
        // HttpClient that owns it gets GC'ed.
        WeakReference<HttpClientImpl> ownerRef;

        SelectorManager(HttpClientImpl ref) throws IOException {
            super(null, null, "SelectorManager", 0, false);
            ownerRef = new WeakReference<>(ref);
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

        synchronized void cancel(SocketChannel e) {
            SelectionKey key = e.keyFor(selector);
            if (key != null) {
                key.cancel();
            }
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
                    HttpClientImpl client;
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

                    // Check whether client is still alive, and if not,
                    // gracefully stop this thread
                    if ((client = ownerRef.get()) == null) {
                        Log.logTrace("HttpClient no longer referenced. Exiting...");
                        return;
                    }
                    long millis = client.purgeTimeoutsAndReturnNextDeadline();
                    client = null; // don't hold onto the client ref

                    //debugPrint(selector);
                    // Don't wait for ever as it might prevent the thread to
                    // stop gracefully. millis will be 0 if no deadline was found.
                    int n = selector.select(millis == 0 ? NODEADLINE : millis);
                    if (n == 0) {
                        // Check whether client is still alive, and if not,
                        // gracefully stop this thread
                        if ((client = ownerRef.get()) == null) {
                            Log.logTrace("HttpClient no longer referenced. Exiting...");
                            return;
                        }
                        client.purgeTimeoutsAndReturnNextDeadline();
                        client = null; // don't hold onto the client ref
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
    public Executor executor() {
        return executor;
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
    public WebSocket.Builder newWebSocketBuilder(URI uri,
                                                 WebSocket.Listener listener) {
        return new BuilderImpl(this, uri, listener);
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
        if (this.cookieManager != null) {
            addFilter(CookieFilter.class);
        }
    }

    private void addFilter(Class<? extends HeaderFilter> f) {
        filters.addFilter(f);
    }

    final List<HeaderFilter> filterChain() {
        return filters.getFilterChain();
    }

    // Timer controls.
    // Timers are implemented through timed Selector.select() calls.

    synchronized void registerTimer(TimeoutEvent event) {
        Log.logTrace("Registering timer {0}", event);
        timeouts.add(event);
        selmgr.wakeupSelector();
    }

    synchronized void cancelTimer(TimeoutEvent event) {
        Log.logTrace("Canceling timer {0}", event);
        timeouts.remove(event);
    }

    /**
     * Purges ( handles ) timer events that have passed their deadline, and
     * returns the amount of time, in milliseconds, until the next earliest
     * event. A return value of 0 means that there are no events.
     */
    private long purgeTimeoutsAndReturnNextDeadline() {
        long diff = 0L;
        List<TimeoutEvent> toHandle = null;
        int remaining = 0;
        // enter critical section to retrieve the timeout event to handle
        synchronized(this) {
            if (timeouts.isEmpty()) return 0L;

            Instant now = Instant.now();
            Iterator<TimeoutEvent> itr = timeouts.iterator();
            while (itr.hasNext()) {
                TimeoutEvent event = itr.next();
                diff = now.until(event.deadline(), ChronoUnit.MILLIS);
                if (diff <= 0) {
                    itr.remove();
                    toHandle = (toHandle == null) ? new ArrayList<>() : toHandle;
                    toHandle.add(event);
                } else {
                    break;
                }
            }
            remaining = timeouts.size();
        }

        // can be useful for debugging
        if (toHandle != null && Log.trace()) {
            Log.logTrace("purgeTimeoutsAndReturnNextDeadline: handling "
                    + (toHandle == null ? 0 : toHandle.size()) + " events, "
                    + "remaining " + remaining
                    + ", next deadline: " + (diff < 0 ? 0L : diff));
        }

        // handle timeout events out of critical section
        if (toHandle != null) {
            Throwable failed = null;
            for (TimeoutEvent event : toHandle) {
                try {
                   Log.logTrace("Firing timer {0}", event);
                   event.handle();
                } catch (Error | RuntimeException e) {
                    // Not expected. Handle remaining events then throw...
                    // If e is an OOME or SOE it might simply trigger a new
                    // error from here - but in this case there's not much we
                    // could do anyway. Just let it flow...
                    if (failed == null) failed = e;
                    else failed.addSuppressed(e);
                    Log.logTrace("Failed to handle event {0}: {1}", event, e);
                }
            }
            if (failed instanceof Error) throw (Error) failed;
            if (failed instanceof RuntimeException) throw (RuntimeException) failed;
        }

        // return time to wait until next event. 0L if there's no more events.
        return diff < 0 ? 0L : diff;
    }

    // used for the connection window
    int getReceiveBufferSize() {
        return Utils.getIntegerNetProperty(
                "jdk.httpclient.connectionWindowSize", 256 * 1024
        );
    }
}

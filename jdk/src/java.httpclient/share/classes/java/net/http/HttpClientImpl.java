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

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.URI;
import static java.net.http.Utils.BUFSIZE;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import java.nio.channels.Selector;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.security.NoSuchAlgorithmException;
import java.util.ListIterator;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Client implementation. Contains all configuration information and also
 * the selector manager thread which allows async events to be registered
 * and delivered when they occur. See AsyncEvent.
 */
class HttpClientImpl extends HttpClient implements BufferHandler {

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
    private static final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
    private final LinkedList<TimeoutEvent> timeouts;

    //@Override
    void debugPrint() {
        selmgr.debugPrint();
        client2.debugPrint();
    }

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
        selmgr.setName("HttpSelector");
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
     *  If exchange needs to block again, then call registerEvent() again
     */
    void registerEvent(AsyncEvent exchange) throws IOException {
        selmgr.register(exchange);
    }

    Http2ClientImpl client2() {
        return client2;
    }

    LinkedList<ByteBuffer> freelist = new LinkedList<>();

    @Override
    public synchronized ByteBuffer getBuffer() {
        if (freelist.isEmpty()) {
            return ByteBuffer.allocate(BUFSIZE);
        }
        return freelist.removeFirst();
    }

    @Override
    public synchronized void returnBuffer(ByteBuffer buffer) {
        buffer.clear();
        freelist.add(buffer);
    }


    // Main loop for this client's selector

    class SelectorManager extends Thread {

        final Selector selector;
        boolean closed;

        final List<AsyncEvent> readyList;
        final List<AsyncEvent> registrations;

        List<AsyncEvent> debugList;

        SelectorManager() throws IOException {
            readyList = new LinkedList<>();
            registrations = new LinkedList<>();
            debugList = new LinkedList<>();
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
            } catch (IOException e) {}
        }

        private List<AsyncEvent> copy(List<AsyncEvent> list) {
            LinkedList<AsyncEvent> c = new LinkedList<>();
            for (AsyncEvent e : list) {
                c.add(e);
            }
            return c;
        }

        synchronized void debugPrint() {
            System.err.println("Selecting on:");
            for (AsyncEvent e : debugList) {
                System.err.println(opvals(e.interestOps()));
            }
        }

        String opvals(int i) {
            StringBuilder sb = new StringBuilder();
            if ((i & OP_READ) != 0)
                sb.append("OP_READ ");
            if ((i & OP_CONNECT) != 0)
                sb.append("OP_CONNECT ");
            if ((i & OP_WRITE) != 0)
                sb.append("OP_WRITE ");
            return sb.toString();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (this) {
                        debugList = copy(registrations);
                        for (AsyncEvent exchange : registrations) {
                            SelectableChannel c = exchange.channel();
                            try {
                                c.configureBlocking(false);
                                c.register(selector,
                                           exchange.interestOps(),
                                           exchange);
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
                    int n = selector.select(timeval);
                    if (n == 0) {
                        signalTimeouts(now);
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();

                    for (SelectionKey key : keys) {
                        if (key.isReadable() || key.isConnectable() || key.isWritable()) {
                            key.cancel();
                            AsyncEvent exchange = (AsyncEvent) key.attachment();
                            readyList.add(exchange);
                        }
                    }
                    selector.selectNow(); // complete cancellation
                    selector.selectedKeys().clear();

                    for (AsyncEvent exchange : readyList) {
                        if (exchange instanceof AsyncEvent.Blocking) {
                            exchange.channel().configureBlocking(true);
                        } else {
                            assert exchange instanceof AsyncEvent.NonBlocking;
                        }
                        executor.synchronize();
                        handleEvent(exchange); // will be delegated to executor
                    }
                    readyList.clear();
                }
            } catch (Throwable e) {
                if (!closed) {
                    System.err.println("HttpClientImpl terminating on error");
                    // This terminates thread. So, better just print stack trace
                    String err = Utils.stackTrace(e);
                    Log.logError("HttpClientImpl: fatal error: " + err);
                }
            }
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
     * Creates a HttpRequest associated with this group.
     *
     * @throws IllegalStateException if the group has been stopped
     */
    @Override
    public HttpRequestBuilderImpl request() {
        return new HttpRequestBuilderImpl(this, null);
    }

    /**
     * Creates a HttpRequest associated with this group.
     *
     * @throws IllegalStateException if the group has been stopped
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

    //void setHttp2NotSupported(String host) {
        //http2NotSupported.put(host, false);
    //}

    final void initFilters() {
        addFilter(AuthenticationFilter.class);
        addFilter(RedirectFilter.class);
    }

    final void addFilter(Class<? extends HeaderFilter> f) {
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
                event.delta = event.timeval - listval ;
            }
        }
        iter.add(event);
        //debugPrintList("register");
        selmgr.wakeupSelector();
    }

    void debugPrintList(String s) {
        System.err.printf("%s: {", s);
        for (TimeoutEvent e : timeouts) {
            System.err.printf("(%d,%d) ", e.delta, e.timeval);
        }
        System.err.println("}");
    }

    synchronized void signalTimeouts(long then) {
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
        //debugPrintList("signalTimeouts");
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
                "sun.net.httpclient.connectionWindowSize", 256 * 1024
        );
    }

    // returns 0 meaning block forever, or a number of millis to block for
    synchronized long getTimeoutValue() {
        if (timeouts.isEmpty()) {
            return 0;
        } else {
            return timeouts.get(0).delta;
        }
    }
}

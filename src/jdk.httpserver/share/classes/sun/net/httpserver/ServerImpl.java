/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import sun.net.httpserver.HttpConnection.State;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static sun.net.httpserver.Utils.isValidName;

/**
 * Provides implementation for both HTTP and HTTPS
 */
class ServerImpl {

    private String protocol;
    private boolean https;
    private Executor executor;
    private HttpsConfigurator httpsConfig;
    private SSLContext sslContext;
    private ContextList contexts;
    private InetSocketAddress address;
    private ServerSocketChannel schan;
    private Selector selector;
    private SelectionKey listenerKey;
    private final Set<HttpConnection> idleConnections;
    // connections which have been accepted() by the server but which haven't
    // yet sent any byte on the connection yet
    private final Set<HttpConnection> newlyAcceptedConnections;
    private final Set<HttpConnection> allConnections;
    /* following two are used to keep track of the times
     * when a connection/request is first received
     * and when we start to send the response
     */
    private final Set<HttpConnection> reqConnections;
    private final Set<HttpConnection> rspConnections;
    private List<Event> events;
    private final Object lolock = new Object();
    private final CountDownLatch finishedLatch = new CountDownLatch(1);
    private volatile boolean terminating = false;
    private boolean bound = false;
    private boolean started = false;
    private HttpServer wrapper;

    // schedule for the timer task that's responsible for idle connection management
    static final long IDLE_TIMER_TASK_SCHEDULE = ServerConfig.getIdleTimerScheduleMillis();
    static final int MAX_CONNECTIONS = ServerConfig.getMaxConnections();
    static final int MAX_IDLE_CONNECTIONS = ServerConfig.getMaxIdleConnections();
    // schedule for the timer task that's responsible for request/response timeout management
    static final long REQ_RSP_TIMER_SCHEDULE = ServerConfig.getReqRspTimerScheduleMillis();
    static final long MAX_REQ_TIME = getTimeMillis(ServerConfig.getMaxReqTime());
    static final long MAX_RSP_TIME = getTimeMillis(ServerConfig.getMaxRspTime());
    static final boolean reqRspTimeoutEnabled = MAX_REQ_TIME != -1 || MAX_RSP_TIME != -1;
    // the maximum idle duration for a connection which is currently idle but has served
    // some request in the past
    static final long IDLE_INTERVAL = ServerConfig.getIdleIntervalMillis();
    // the maximum idle duration for a newly accepted connection which hasn't yet received
    // the first byte of data on that connection
    static final long NEWLY_ACCEPTED_CONN_IDLE_INTERVAL;
    static {
        // the idle duration of a newly accepted connection is considered to be the least of the
        // configured idle interval and the configured max request time (if any).
        NEWLY_ACCEPTED_CONN_IDLE_INTERVAL = MAX_REQ_TIME > 0
                ? Math.min(IDLE_INTERVAL, MAX_REQ_TIME)
                : IDLE_INTERVAL;
    }

    private Timer timer, timer1;
    private final Logger logger;
    private Thread dispatcherThread;

    ServerImpl(
        HttpServer wrapper, String protocol, InetSocketAddress addr, int backlog
    ) throws IOException {

        this.protocol = protocol;
        this.wrapper = wrapper;
        this.logger = System.getLogger("com.sun.net.httpserver");
        ServerConfig.checkLegacyProperties(logger);
        https = protocol.equalsIgnoreCase("https");
        this.address = addr;
        contexts = new ContextList();
        schan = ServerSocketChannel.open();
        if (addr != null) {
            ServerSocket socket = schan.socket();
            socket.bind(addr, backlog);
            bound = true;
        }
        selector = Selector.open();
        schan.configureBlocking(false);
        listenerKey = schan.register(selector, SelectionKey.OP_ACCEPT);
        dispatcher = new Dispatcher();
        idleConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        allConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        reqConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        rspConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        newlyAcceptedConnections = Collections.synchronizedSet(new HashSet<>());
        timer = new Timer("idle-timeout-task", true);
        timer.schedule(new IdleTimeoutTask(), IDLE_TIMER_TASK_SCHEDULE, IDLE_TIMER_TASK_SCHEDULE);
        if (reqRspTimeoutEnabled) {
            timer1 = new Timer("req-rsp-timeout-task", true);
            timer1.schedule(new ReqRspTimeoutTask(), REQ_RSP_TIMER_SCHEDULE, REQ_RSP_TIMER_SCHEDULE);
            logger.log(Level.DEBUG, "HttpServer request/response timeout task schedule ms: ",
                    REQ_RSP_TIMER_SCHEDULE);
            logger.log(Level.DEBUG, "MAX_REQ_TIME:  "+MAX_REQ_TIME);
            logger.log(Level.DEBUG, "MAX_RSP_TIME:  "+MAX_RSP_TIME);
        }
        events = new ArrayList<>();
        logger.log(Level.DEBUG, "HttpServer created "+protocol+" "+ addr);
    }

    public void bind(InetSocketAddress addr, int backlog) throws IOException {
        if (bound) {
            throw new BindException("HttpServer already bound");
        }
        if (addr == null) {
            throw new NullPointerException("null address");
        }
        ServerSocket socket = schan.socket();
        socket.bind(addr, backlog);
        bound = true;
    }

    public void start() {
        if (!bound || started || finished()) {
            throw new IllegalStateException("server in wrong state");
        }
        if (executor == null) {
            executor = new DefaultExecutor();
        }
        dispatcherThread = new Thread(null, dispatcher, "HTTP-Dispatcher", 0, false);
        started = true;
        dispatcherThread.start();
    }

    public void setExecutor(Executor executor) {
        if (started) {
            throw new IllegalStateException("server already started");
        }
        this.executor = executor;
    }

    private static class DefaultExecutor implements Executor {
        public void execute(Runnable task) {
            task.run();
        }
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setHttpsConfigurator(HttpsConfigurator config) {
        if (config == null) {
            throw new NullPointerException("null HttpsConfigurator");
        }
        if (started) {
            throw new IllegalStateException("server already started");
        }
        this.httpsConfig = config;
        sslContext = config.getSSLContext();
    }

    public HttpsConfigurator getHttpsConfigurator() {
        return httpsConfig;
    }

    private final boolean finished() {
        // if the latch is 0, the server is finished
        return finishedLatch.getCount() == 0;
    }

    public final boolean isFinishing() {
        return finished();
    }

    /**
     * This method stops the server by adding a stop request event and
     * waiting for the server until the event is triggered or until the maximum delay is triggered.
     * <p>
     * This ensures that the server is stopped immediately after all exchanges are complete. HttpConnections will be forcefully closed if active exchanges do not
     * complete within the imparted delay.
     *
     * @param delay maximum delay to wait for exchanges completion, in seconds
     */
    public void stop(int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("negative delay parameter");
        }

        logger.log(Level.TRACE, "stopping");
        // posting a stop event, which will flip finished flag if it finishes
        // before the timeout in this method
        terminating = true;

        addEvent(new Event.StopRequested());

        try { schan.close(); } catch (IOException e) {}
        selector.wakeup();

        try {
            // waiting for the duration of the delay, unless released before
            finishedLatch.await(delay, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            logger.log(Level.TRACE, "Error in awaiting the delay");

        } finally {

            logger.log(Level.TRACE, "closing connections");
            finishedLatch.countDown();
            selector.wakeup();
            synchronized (allConnections) {
                for (HttpConnection c : allConnections) {
                    c.close();
                }
            }
            allConnections.clear();
            idleConnections.clear();
            newlyAcceptedConnections.clear();
            timer.cancel();
            if (reqRspTimeoutEnabled) {
                timer1.cancel();
            }
            logger.log(Level.TRACE, "connections closed");

            if (dispatcherThread != null && dispatcherThread != Thread.currentThread()) {
                logger.log(Level.TRACE, "waiting for dispatcher thread");
                try {
                    dispatcherThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.log(Level.TRACE, "ServerImpl.stop: ", e);
                }
            }
            logger.log(Level.TRACE, "server stopped");
        }
    }

    Dispatcher dispatcher;

    public synchronized HttpContextImpl createContext(String path, HttpHandler handler) {
        if (handler == null || path == null) {
            throw new NullPointerException("null handler, or path parameter");
        }
        HttpContextImpl context = new HttpContextImpl(protocol, path, handler, this);
        contexts.add(context);
        logger.log(Level.DEBUG, "context created: " + path);
        return context;
    }

    public synchronized HttpContextImpl createContext(String path) {
        if (path == null) {
            throw new NullPointerException("null path parameter");
        }
        HttpContextImpl context = new HttpContextImpl(protocol, path, null, this);
        contexts.add(context);
        logger.log(Level.DEBUG, "context created: " + path);
        return context;
    }

    public synchronized void removeContext(String path) throws IllegalArgumentException {
        if (path == null) {
            throw new NullPointerException("null path parameter");
        }
        contexts.remove(protocol, path);
        logger.log(Level.DEBUG, "context removed: " + path);
    }

    public synchronized void removeContext(HttpContext context) throws IllegalArgumentException {
        if (!(context instanceof HttpContextImpl)) {
            throw new IllegalArgumentException("wrong HttpContext type");
        }
        contexts.remove((HttpContextImpl)context);
        logger.log(Level.DEBUG, "context removed: " + context.getPath());
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress) schan.socket().getLocalSocketAddress();
    }

    void addEvent(Event r) {
        synchronized (lolock) {
            events.add(r);
            selector.wakeup();
        }
    }

    /* main server listener task */

    /**
     * The Dispatcher is responsible for accepting any connections and then using those connections
     * to processing any incoming requests. A connection is represented as an instance of
     * sun.net.httpserver.HttpConnection.
     *
     * Connection states:
     *  An instance of HttpConnection goes through the following states:
     *
     *  - NEWLY_ACCEPTED: A connection is marked as newly accepted as soon as the Dispatcher
     *    accept()s a connection. A newly accepted connection is added to a newlyAcceptedConnections
     *    collection. A newly accepted connection also gets added to the allConnections collection.
     *    The newlyAcceptedConnections isn't checked for any size limits, however, if the server is
     *    configured with a maximum connection limit, then the elements in the
     *    newlyAcceptedConnections will never exceed that configured limit (no explicit size checks
     *    are done on the newlyAcceptedConnections collection, since the maximum connection limit
     *    applies to connections across different connection states). A connection in NEWLY_ACCEPTED
     *    state is considered idle and is eligible for idle connection management.
     *
     *  - REQUEST: A connection is marked to be in REQUEST state when the request processing starts
     *    on that connection. This typically happens when the first byte of data is received on a
     *    NEWLY_ACCEPTED connection or when new data arrives on a connection which was previously
     *    in IDLE state. When a connection is in REQUEST state, it implies that the connection is
     *    active and thus isn't eligible for idle connection management. If the server is configured
     *    with a maximum request timeout, then connections in REQUEST state are eligible
     *    for Request/Response timeout management.
     *
     *  - RESPONSE: A connection is marked to be in RESPONSE state when the server has finished
     *    reading the request. A connection is RESPONSE state is considered active and isn't eligible
     *    for idle connection management. If the server is configured with a maximum response timeout,
     *    then connections in RESPONSE state are eligible for Request/Response timeout management.
     *
     *  - IDLE: A connection is marked as IDLE when a request/response cycle (successfully) completes
     *    on that particular connection. Idle connections are held in a idleConnections collection.
     *    The idleConnections collection is limited in size and the size is decided by a server
     *    configuration. Connections in IDLE state get added to the idleConnections collection only
     *    if that collection hasn't reached the configured limit. If a connection has reached IDLE
     *    state and there's no more room in the idleConnections collection, then such a connection
     *    gets closed. Connections in idleConnections collection are eligible for idle connection
     *    management.
     *
     * Idle connection management:
     *  A timer task is responsible for closing idle connections. Each connection that is in a state
     *  which is eligible for idle timeout management (see above section on connection states)
     *  will have a corresponding idle expiration time associated with it. The idle timeout management
     *  task will check the expiration time of each such connection against the current time and will
     *  close the connection if the current time is either equal to or past the expiration time.
     *
     * Request/Response timeout management:
     *  The server can be optionally configured with a maximum request timeout and/or maximum response
     *  timeout. If either of these timeouts have been configured, then an additional timer task is
     *  run by the server. This timer task is then responsible for closing connections which have
     *  been in REQUEST or RESPONSE state for a period of time that exceeds the respective configured
     *  timeouts.
     *
     * Maximum connection limit management:
     *  The server can be optionally configured with a maximum connection limit. A value of 0 or
     *  negative integer is ignored and considered to represent no connection limit. In case of a
     *  positive integer value, any newly accepted connections will be first checked against the
     *  current count of established connections (held by the allConnections collection) and if the
     *  configured limit has reached, then the newly accepted connection will be closed immediately
     *  (even before setting its state to NEWLY_ACCEPTED or adding it to the newlyAcceptedConnections
     *  collection).
     *
     */
    class Dispatcher implements Runnable {

        private void handleEvent(Event r) {

            // Stopping marking the state as finished if stop is requested,
            // termination is in progress and exchange count is 0
            if (r instanceof Event.StopRequested) {
                logger.log(Level.TRACE, "Handling {0} event",
                        r.getClass().getSimpleName());

                // checking if terminating is set to true
                final boolean terminatingCopy = terminating;
                assert terminatingCopy;

                if (getExchangeCount() == 0 && reqConnections.isEmpty()) {
                    finishedLatch.countDown();
                } else {
                    logger.log(Level.TRACE, "Some requests are still pending");
                }
                return;
            }

            ExchangeImpl t = r.exchange;
            HttpConnection c = t.getConnection();

            try {
                if (r instanceof Event.ExchangeFinished) {

                    logger.log(Level.TRACE, "Handling {0} event",
                                r.getClass().getSimpleName());
                    int exchanges = t.endExchange();
                    if (terminating && exchanges == 0 && reqConnections.isEmpty()) {
                        finishedLatch.countDown();
                    }
                    LeftOverInputStream is = t.getOriginalInputStream();
                    if (!is.isEOF()) {
                        t.close = true;
                        if (c.getState() == State.REQUEST) {
                            requestCompleted(c);
                        }
                    }
                    responseCompleted(c);
                    if (t.close) {
                        c.close();
                        allConnections.remove(c);
                    } else {
                        if (is.isDataBuffered()) {
                            /* don't re-enable the interestops, just handle it */
                            requestStarted(c);
                            handle(c.getChannel(), c);
                        } else {
                            connsToRegister.add(c);
                        }
                    }
                }
            } catch (IOException e) {
                logger.log(
                    Level.TRACE, "Dispatcher (1)", e
                );
                c.close();
            }
        }

        final ArrayList<HttpConnection> connsToRegister = new ArrayList<>();

        void reRegister(HttpConnection c) {
            /* re-register with selector */
            try {
                SocketChannel chan = c.getChannel();
                chan.configureBlocking(false);
                SelectionKey key = chan.register(selector, SelectionKey.OP_READ);
                key.attach(c);
                c.selectionKey = key;
                markIdle(c);
            } catch (IOException e) {
                dprint(e);
                logger.log(Level.TRACE, "Dispatcher (8)", e);
                c.close();
            }
        }

        public void run() {
            // finished() will be true when there are no active exchange after terminating
             while (!finished()) {
                try {
                    List<Event> list = null;
                    synchronized (lolock) {
                        if (!events.isEmpty()) {
                            list = events;
                            events = new ArrayList<>();
                        }
                    }

                    if (list != null) {
                        for (Event r: list) {
                            handleEvent(r);
                        }
                    }

                    for (HttpConnection c : connsToRegister) {
                        reRegister(c);
                    }
                    connsToRegister.clear();

                    selector.select(1000);

                    /* process the selected list now  */
                    Set<SelectionKey> selected = selector.selectedKeys();
                    // create a copy of the selected keys so that we can iterate over it
                    // and at the same time not worry about the underlying Set being
                    // modified (leading to ConcurrentModificationException) due to
                    // any subsequent select operations that we invoke on the
                    // selector (in this same thread).
                    for (final SelectionKey key : selected.toArray(SelectionKey[]::new)) {
                        // remove the key from the original selected keys (live) Set
                        selected.remove(key);
                        if (key.equals(listenerKey)) {
                            if (terminating) {
                                continue;
                            }
                            SocketChannel chan = schan.accept();
                            // optimist there's a channel
                            if (chan != null) {
                                if (MAX_CONNECTIONS > 0 && allConnections.size() >= MAX_CONNECTIONS) {
                                    // we've hit max limit of current open connections, so we go
                                    // ahead and close this connection without processing it
                                    try {
                                        chan.close();
                                    } catch (IOException ignore) {
                                    }
                                    // move on to next selected key
                                    continue;
                                }
                                // Set TCP_NODELAY, if appropriate
                                if (ServerConfig.noDelay()) {
                                    chan.socket().setTcpNoDelay(true);
                                }
                                chan.configureBlocking(false);
                                SelectionKey newkey =
                                    chan.register(selector, SelectionKey.OP_READ);
                                HttpConnection c = new HttpConnection();
                                c.selectionKey = newkey;
                                c.setChannel(chan);
                                newkey.attach(c);
                                markNewlyAccepted(c);
                                allConnections.add(c);
                            }
                        } else {
                            try {
                                if (key.isReadable()) {
                                    SocketChannel chan = (SocketChannel)key.channel();
                                    HttpConnection conn = (HttpConnection)key.attachment();

                                    key.cancel();
                                    chan.configureBlocking(true);
                                    // check if connection is being closed
                                    if (newlyAcceptedConnections.remove(conn)
                                            || idleConnections.remove(conn)) {
                                        // was either a newly accepted connection or an idle
                                        // connection. In either case, we mark that the request
                                        // has now started on this connection.
                                        requestStarted(conn);
                                        handle(chan, conn);
                                    }
                                } else {
                                    assert false : "Unexpected non-readable key:" + key;
                                }
                            } catch (CancelledKeyException e) {
                                handleException(key, null);
                            } catch (IOException e) {
                                handleException(key, e);
                            }
                        }
                    }
                    // call the selector just to process the cancelled keys
                    selector.selectNow();
                } catch (IOException e) {
                    logger.log(Level.TRACE, "Dispatcher (4)", e);
                } catch (Exception e) {
                    logger.log(Level.TRACE, "Dispatcher (7)", e);
                }
            }
            try { selector.close(); } catch (Exception e) {}
        }

        private void handleException(SelectionKey key, Exception e) {
            HttpConnection conn = (HttpConnection)key.attachment();
            if (e != null) {
                logger.log(Level.TRACE, "Dispatcher (2)", e);
            }
            closeConnection(conn);
        }

        public void handle(SocketChannel chan, HttpConnection conn)
        {
            try {
                Exchange t = new Exchange(chan, protocol, conn);
                executor.execute(t);
            } catch (HttpError e1) {
                logger.log(Level.TRACE, "Dispatcher (4)", e1);
                closeConnection(conn);
            } catch (IOException e) {
                logger.log(Level.TRACE, "Dispatcher (5)", e);
                closeConnection(conn);
            } catch (Throwable e) {
                logger.log(Level.TRACE, "Dispatcher (6)", e);
                closeConnection(conn);
            }
        }
    }

    static boolean debug = ServerConfig.debugEnabled();

    static synchronized void dprint(String s) {
        if (debug) {
            System.out.println(s);
        }
    }

    static synchronized void dprint(Exception e) {
        if (debug) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    Logger getLogger() {
        return logger;
    }

    private void closeConnection(HttpConnection conn) {
        conn.close();
        allConnections.remove(conn);
        switch (conn.getState()) {
            case REQUEST:
                reqConnections.remove(conn);
                break;
            case RESPONSE:
                rspConnections.remove(conn);
                break;
            case IDLE:
                idleConnections.remove(conn);
                break;
            case NEWLY_ACCEPTED:
                newlyAcceptedConnections.remove(conn);
                break;
        }
        assert !reqConnections.remove(conn);
        assert !rspConnections.remove(conn);
        assert !idleConnections.remove(conn);
        assert !newlyAcceptedConnections.remove(conn);
    }

        /* per exchange task */

    class Exchange implements Runnable {
        SocketChannel chan;
        HttpConnection connection;
        HttpContextImpl context;
        InputStream rawin;
        OutputStream rawout;
        String protocol;
        ExchangeImpl tx;
        HttpContextImpl ctx;
        boolean rejected = false;

        Exchange(SocketChannel chan, String protocol, HttpConnection conn) throws IOException {
            this.chan = chan;
            this.connection = conn;
            this.protocol = protocol;
        }

        public void run() {
            /* context will be null for new connections */
            logger.log(Level.TRACE, "exchange started");

            if (dispatcherThread == Thread.currentThread()) {
                try {
                    // call selector to process cancelled keys
                    selector.selectNow();
                } catch (IOException ioe) {
                    logger.log(Level.DEBUG, "processing of cancelled keys failed: closing");
                    closeConnection(connection);
                    return;
                }
            }

            context = connection.getHttpContext();
            boolean newconnection;
            SSLEngine engine = null;
            String requestLine = null;
            SSLStreams sslStreams = null;
            try {
                if (context != null) {
                    this.rawin = connection.getInputStream();
                    this.rawout = connection.getRawOutputStream();
                    newconnection = false;
                } else {
                    /* figure out what kind of connection this is */
                    newconnection = true;
                    if (https) {
                        if (sslContext == null) {
                            logger.log(Level.WARNING,
                                "SSL connection received. No https context created");
                            throw new HttpError("No SSL context established");
                        }
                        sslStreams = new SSLStreams(ServerImpl.this, sslContext, chan);
                        rawin = sslStreams.getInputStream();
                        rawout = sslStreams.getOutputStream();
                        engine = sslStreams.getSSLEngine();
                        connection.sslStreams = sslStreams;
                    } else {
                        rawin = new BufferedInputStream(
                            new Request.ReadStream(
                                ServerImpl.this, chan
                        ));
                        rawout = new Request.WriteStream(
                            ServerImpl.this, chan
                        );
                    }
                    rawout = new BufferedOutputStream(rawout);
                    connection.raw = rawin;
                    connection.rawout = rawout;
                }

                Request req;
                try {
                    req = new Request(rawin, rawout, newconnection && !https);
                } catch (ProtocolException pe) {
                    logger.log(Level.DEBUG, "closing due to: " + pe);
                    reject(Code.HTTP_BAD_REQUEST, "", pe.getMessage());
                    return;
                }

                requestLine = req.requestLine();
                if (requestLine == null) {
                    /* connection closed */
                    logger.log(Level.DEBUG, "no request line: closing");
                    closeConnection(connection);
                    return;
                }
                logger.log(Level.DEBUG, "Exchange request line: {0}", requestLine);
                int space = requestLine.indexOf(' ');
                if (space == -1) {
                    reject(Code.HTTP_BAD_REQUEST,
                            requestLine, "Bad request line");
                    return;
                }
                String method = requestLine.substring(0, space);
                int start = space+1;
                space = requestLine.indexOf(' ', start);
                if (space == -1) {
                    reject(Code.HTTP_BAD_REQUEST,
                            requestLine, "Bad request line");
                    return;
                }
                String uriStr = requestLine.substring(start, space);
                URI uri;
                try {
                    uri = new URI(uriStr);
                } catch (URISyntaxException e3) {
                    reject(Code.HTTP_BAD_REQUEST,
                            requestLine, "URISyntaxException thrown");
                    return;
                }
                start = space+1;
                String version = requestLine.substring(start);
                Headers headers = req.headers();
                /* check key for illegal characters */
                for (var k : headers.keySet()) {
                    if (!isValidName(k)) {
                        reject(Code.HTTP_BAD_REQUEST, requestLine,
                                "Header key contains illegal characters");
                        return;
                    }
                }
                /* checks for unsupported combinations of lengths and encodings */
                if (headers.containsKey("Content-Length") &&
                        (headers.containsKey("Transfer-encoding") || headers.get("Content-Length").size() > 1)) {
                    reject(Code.HTTP_BAD_REQUEST, requestLine,
                            "Conflicting or malformed headers detected");
                    return;
                }
                long clen = 0L;
                String headerValue = null;
                List<String> teValueList = headers.get("Transfer-encoding");
                if (teValueList != null && !teValueList.isEmpty()) {
                    headerValue = teValueList.get(0);
                }
                if (headerValue != null) {
                    if (headerValue.equalsIgnoreCase("chunked") && teValueList.size() == 1) {
                        clen = -1L;
                    } else {
                        reject(Code.HTTP_NOT_IMPLEMENTED,
                                requestLine, "Unsupported Transfer-Encoding value");
                        return;
                    }
                } else {
                    headerValue = headers.getFirst("Content-Length");
                    if (headerValue != null) {
                        try {
                            clen = Long.parseLong(headerValue);
                        } catch (NumberFormatException e2) {
                            reject(Code.HTTP_BAD_REQUEST,
                                    requestLine, "NumberFormatException thrown");
                            return;
                        }
                        if (clen < 0) {
                            reject(Code.HTTP_BAD_REQUEST, requestLine,
                                    "Illegal Content-Length value");
                            return;
                        }
                    }
                    if (clen == 0) {
                        requestCompleted(connection);
                    }
                }
                ctx = contexts.findContext(protocol, uri.getPath());
                if (ctx == null) {
                    reject(Code.HTTP_NOT_FOUND,
                            requestLine, "No context found for request");
                    return;
                }
                connection.setContext(ctx);
                if (ctx.getHandler() == null) {
                    reject(Code.HTTP_INTERNAL_ERROR,
                            requestLine, "No handler for context");
                    return;
                }
                tx = new ExchangeImpl(
                    method, uri, req, clen, connection
                );
                try {

                    String chdr = headers.getFirst("Connection");
                    Headers rheaders = tx.getResponseHeaders();

                    if (chdr != null && chdr.equalsIgnoreCase("close")) {
                        tx.close = true;
                    }
                    if (version.equalsIgnoreCase("http/1.0")) {
                        tx.http10 = true;
                        if (chdr == null) {
                            tx.close = true;
                            rheaders.set("Connection", "close");
                        } else if (chdr.equalsIgnoreCase("keep-alive")) {
                            rheaders.set("Connection", "keep-alive");
                            int idleSeconds = (int) (ServerConfig.getIdleIntervalMillis() / 1000);
                            String val = "timeout=" + idleSeconds;
                            rheaders.set("Keep-Alive", val);
                        }
                    }

                    if (newconnection) {
                        connection.setParameters(
                                rawin, rawout, chan, engine, sslStreams,
                                sslContext, protocol, ctx, rawin
                        );
                    }
                    /* check if client sent an Expect 100 Continue.
                     * In that case, need to send an interim response.
                     * In future API may be modified to allow app to
                     * be involved in this process.
                     */
                    String exp = headers.getFirst("Expect");
                    if (exp != null && exp.equalsIgnoreCase("100-continue")) {
                        logReply(100, requestLine, null);
                        sendReply(
                                Code.HTTP_CONTINUE, false, null
                        );
                    }
                    /* uf is the list of filters seen/set by the user.
                     * sf is the list of filters established internally
                     * and which are not visible to the user. uc and sc
                     * are the corresponding Filter.Chains.
                     * They are linked together by a LinkHandler
                     * so that they can both be invoked in one call.
                     */
                    final List<Filter> sf = ctx.getSystemFilters();
                    final List<Filter> uf = ctx.getFilters();

                    final Filter.Chain sc = new Filter.Chain(sf, ctx.getHandler());
                    final Filter.Chain uc = new Filter.Chain(uf, new LinkHandler(sc));

                    /* set up the two stream references */
                    tx.getRequestBody();
                    tx.getResponseBody();
                    if (https) {
                        uc.doFilter(new HttpsExchangeImpl(tx));
                    } else {
                        uc.doFilter(new HttpExchangeImpl(tx));
                    }
                } catch (Throwable t) {
                    // release the exchange.
                    logger.log(Level.TRACE, "ServerImpl.Exchange", t);
                    if (!tx.writefinished()) {
                        closeConnection(connection);
                    }
                    tx.postExchangeFinished(false);
                }
            } catch (Exception e) {
                logger.log(Level.TRACE, "ServerImpl.Exchange", e);
                if (tx == null || !tx.writefinished()) {
                    closeConnection(connection);
                }
            } catch (Throwable t) {
                logger.log(Level.TRACE, "ServerImpl.Exchange (5)", t);
                throw t;
            }
        }

        /* used to link to 2 or more Filter.Chains together */

        class LinkHandler implements HttpHandler {
            Filter.Chain nextChain;

            LinkHandler(Filter.Chain nextChain) {
                this.nextChain = nextChain;
            }

            public void handle(HttpExchange exchange) throws IOException {
                nextChain.doFilter(exchange);
            }
        }

        void reject(int code, String requestStr, String message) {
            rejected = true;
            logReply(code, requestStr, message);
            sendReply(
                code, true, "<h1>"+code+Code.msg(code)+"</h1>"+message
            );
        }

        void sendReply(
            int code, boolean closeNow, String text)
        {
            try {
                StringBuilder builder = new StringBuilder(512);
                builder.append("HTTP/1.1 ")
                    .append(code).append(Code.msg(code)).append("\r\n");

                if (text != null && text.length() != 0) {
                    builder.append("Content-Length: ")
                        .append(text.length()).append("\r\n")
                        .append("Content-Type: text/html\r\n");
                } else {
                    builder.append("Content-Length: 0\r\n");
                    text = "";
                }
                if (closeNow) {
                    builder.append("Connection: close\r\n");
                }
                builder.append("\r\n").append(text);
                String s = builder.toString();
                byte[] b = s.getBytes(ISO_8859_1);
                rawout.write(b);
                rawout.flush();
                if (closeNow) {
                    closeConnection(connection);
                }
            } catch (IOException e) {
                logger.log(Level.TRACE, "ServerImpl.sendReply", e);
                closeConnection(connection);
            }
        }

    }

    void logReply(int code, String requestStr, String text) {
        if (!logger.isLoggable(Level.DEBUG)) {
            return;
        }
        if (text == null) {
            text = "";
        }
        String r;
        if (requestStr.length() > 80) {
           r = requestStr.substring(0, 80) + "<TRUNCATED>";
        } else {
           r = requestStr;
        }
        String message = r + " [" + code + " " +
                    Code.msg(code) + "] ("+text+")";
        logger.log(Level.DEBUG, message);
    }

    private int exchangeCount = 0;

    synchronized void startExchange() {
        exchangeCount ++;
    }

    synchronized int getExchangeCount() {
        return exchangeCount;
    }

    synchronized int endExchange() {
        exchangeCount --;
        assert exchangeCount >= 0;
        return exchangeCount;
    }

    HttpServer getWrapper() {
        return wrapper;
    }

    void requestStarted(HttpConnection c) {
        c.reqStartedTime = System.currentTimeMillis();
        c.setState(State.REQUEST);
        reqConnections.add(c);
    }

    void markIdle(HttpConnection c) {
        boolean close = false;

        synchronized(idleConnections) {
            if (idleConnections.size() >= MAX_IDLE_CONNECTIONS) {
                // closing the connection here could block
                // instead set boolean and close outside the synchronized block
                close = true;
            } else {
                c.idleStartTime = System.currentTimeMillis();
                c.setState(State.IDLE);
                idleConnections.add(c);
            }
        }

        if (close) {
            c.close();
            allConnections.remove(c);
        }
    }

    void markNewlyAccepted(HttpConnection c) {
        c.idleStartTime = System.currentTimeMillis();
        c.setState(State.NEWLY_ACCEPTED);
        newlyAcceptedConnections.add(c);
    }

    // called after a request has been completely read
    // by the server. This stops the timer which would
    // close the connection if the request doesn't arrive
    // quickly enough. It then starts the timer
    // that ensures the client reads the response in a timely
    // fashion.

    void requestCompleted(HttpConnection c) {
        State s = c.getState();
        assert s == State.REQUEST : "State is not REQUEST ("+s+")";
        reqConnections.remove(c);
        c.rspStartedTime = System.currentTimeMillis();
        rspConnections.add(c);
        c.setState(State.RESPONSE);
    }

    // called after response has been sent
    void responseCompleted(HttpConnection c) {
        State s = c.getState();
        assert s == State.RESPONSE : "State is not RESPONSE ("+s+")";
        rspConnections.remove(c);
        c.setState(State.IDLE);
    }

    /**
     * Responsible for closing connections that have been idle.
     * TimerTask run every CLOCK_TICK ms
     */
    class IdleTimeoutTask extends TimerTask {
        public void run() {
            closeConnections(idleConnections, IDLE_INTERVAL);
            // if any newly accepted connection has been idle (i.e. no byte has been sent on that
            // connection during the configured idle timeout period) then close it as well
            closeConnections(newlyAcceptedConnections, NEWLY_ACCEPTED_CONN_IDLE_INTERVAL);
        }

        private void closeConnections(Set<HttpConnection> connections, long idleInterval) {
            long currentTime = System.currentTimeMillis();
            ArrayList<HttpConnection> toClose = new ArrayList<>();

            connections.forEach(c -> {
                if (currentTime - c.idleStartTime >= idleInterval) {
                    toClose.add(c);
                }
            });
            for (HttpConnection c : toClose) {
                // check if connection still idle
                if (currentTime - c.idleStartTime >= idleInterval &&
                        connections.remove(c)) {
                    allConnections.remove(c);
                    c.close();
                    if (logger.isLoggable(Level.TRACE)) {
                        logger.log(Level.TRACE, "Closed idle connection " + c);
                    }
                }
            }
        }
    }

    /**
     * Responsible for closing connections which have timed out while in REQUEST or RESPONSE state
     */
    class ReqRspTimeoutTask extends TimerTask {

        // runs every TIMER_MILLIS
        public void run() {
            ArrayList<HttpConnection> toClose = new ArrayList<>();
            final long currentTime = System.currentTimeMillis();
            synchronized (reqConnections) {
                if (MAX_REQ_TIME != -1) {
                    for (HttpConnection c : reqConnections) {
                        if (currentTime - c.reqStartedTime >= MAX_REQ_TIME) {
                            toClose.add(c);
                        }
                    }
                    for (HttpConnection c : toClose) {
                        logger.log(Level.DEBUG, "closing: no request: " + c);
                        reqConnections.remove(c);
                        allConnections.remove(c);
                        c.close();
                    }
                }
            }
            toClose = new ArrayList<>();
            synchronized (rspConnections) {
                if (MAX_RSP_TIME != -1) {
                    for (HttpConnection c : rspConnections) {
                        if (currentTime - c.rspStartedTime >= MAX_RSP_TIME) {
                            toClose.add(c);
                        }
                    }
                    for (HttpConnection c : toClose) {
                        logger.log(Level.DEBUG, "closing: no response: " + c);
                        rspConnections.remove(c);
                        allConnections.remove(c);
                        c.close();
                    }
                }
            }
        }
    }

    /**
     * Converts and returns the passed {@code secs} as milli seconds. If the passed {@code secs}
     * is negative or zero or if the conversion from seconds to milli seconds results in a negative
     * number, then this method returns -1.
     */
    private static long getTimeMillis(long secs) {
        if (secs <= 0) {
            return -1;
        }
        final long milli = secs * 1000;
        // this handles potential numeric overflow that may have happened during conversion
        return milli > 0 ? milli : -1;
    }
}

/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.net.*;
import java.io.*;
import java.nio.*;
import java.security.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.net.ssl.*;
import com.sun.net.httpserver.*;
import com.sun.net.httpserver.spi.*;

/**
 * Provides implementation for both HTTP and HTTPS
 */
class ServerImpl implements TimeSource {

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
    private Set<HttpConnection> idleConnections;
    private Set<HttpConnection> allConnections;
    private List<Event> events;
    private Object lolock = new Object();
    private volatile boolean finished = false;
    private volatile boolean terminating = false;
    private boolean bound = false;
    private boolean started = false;
    private volatile long time;  /* current time */
    private volatile long ticks; /* number of clock ticks since server started */
    private HttpServer wrapper;

    final static int CLOCK_TICK = ServerConfig.getClockTick();
    final static long IDLE_INTERVAL = ServerConfig.getIdleInterval();
    final static int MAX_IDLE_CONNECTIONS = ServerConfig.getMaxIdleConnections();

    private Timer timer;
    private Logger logger;

    ServerImpl (
        HttpServer wrapper, String protocol, InetSocketAddress addr, int backlog
    ) throws IOException {

        this.protocol = protocol;
        this.wrapper = wrapper;
        this.logger = Logger.getLogger ("com.sun.net.httpserver");
        https = protocol.equalsIgnoreCase ("https");
        this.address = addr;
        contexts = new ContextList();
        schan = ServerSocketChannel.open();
        if (addr != null) {
            ServerSocket socket = schan.socket();
            socket.bind (addr, backlog);
            bound = true;
        }
        selector = Selector.open ();
        schan.configureBlocking (false);
        listenerKey = schan.register (selector, SelectionKey.OP_ACCEPT);
        dispatcher = new Dispatcher();
        idleConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        allConnections = Collections.synchronizedSet (new HashSet<HttpConnection>());
        time = System.currentTimeMillis();
        timer = new Timer ("server-timer", true);
        timer.schedule (new ServerTimerTask(), CLOCK_TICK, CLOCK_TICK);
        events = new LinkedList<Event>();
        logger.config ("HttpServer created "+protocol+" "+ addr);
    }

    public void bind (InetSocketAddress addr, int backlog) throws IOException {
        if (bound) {
            throw new BindException ("HttpServer already bound");
        }
        if (addr == null) {
            throw new NullPointerException ("null address");
        }
        ServerSocket socket = schan.socket();
        socket.bind (addr, backlog);
        bound = true;
    }

    public void start () {
        if (!bound || started || finished) {
            throw new IllegalStateException ("server in wrong state");
        }
        if (executor == null) {
            executor = new DefaultExecutor();
        }
        Thread t = new Thread (dispatcher);
        started = true;
        t.start();
    }

    public void setExecutor (Executor executor) {
        if (started) {
            throw new IllegalStateException ("server already started");
        }
        this.executor = executor;
    }

    private static class DefaultExecutor implements Executor {
        public void execute (Runnable task) {
            task.run();
        }
    }

    public Executor getExecutor () {
        return executor;
    }

    public void setHttpsConfigurator (HttpsConfigurator config) {
        if (config == null) {
            throw new NullPointerException ("null HttpsConfigurator");
        }
        if (started) {
            throw new IllegalStateException ("server already started");
        }
        this.httpsConfig = config;
        sslContext = config.getSSLContext();
    }

    public HttpsConfigurator getHttpsConfigurator () {
        return httpsConfig;
    }

    public void stop (int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException ("negative delay parameter");
        }
        terminating = true;
        try { schan.close(); } catch (IOException e) {}
        selector.wakeup();
        long latest = System.currentTimeMillis() + delay * 1000;
        while (System.currentTimeMillis() < latest) {
            delay();
            if (finished) {
                break;
            }
        }
        finished = true;
        selector.wakeup();
        synchronized (allConnections) {
            for (HttpConnection c : allConnections) {
                c.close();
            }
        }
        allConnections.clear();
        idleConnections.clear();
        timer.cancel();
    }

    Dispatcher dispatcher;

    public synchronized HttpContextImpl createContext (String path, HttpHandler handler) {
        if (handler == null || path == null) {
            throw new NullPointerException ("null handler, or path parameter");
        }
        HttpContextImpl context = new HttpContextImpl (protocol, path, handler, this);
        contexts.add (context);
        logger.config ("context created: " + path);
        return context;
    }

    public synchronized HttpContextImpl createContext (String path) {
        if (path == null) {
            throw new NullPointerException ("null path parameter");
        }
        HttpContextImpl context = new HttpContextImpl (protocol, path, null, this);
        contexts.add (context);
        logger.config ("context created: " + path);
        return context;
    }

    public synchronized void removeContext (String path) throws IllegalArgumentException {
        if (path == null) {
            throw new NullPointerException ("null path parameter");
        }
        contexts.remove (protocol, path);
        logger.config ("context removed: " + path);
    }

    public synchronized void removeContext (HttpContext context) throws IllegalArgumentException {
        if (!(context instanceof HttpContextImpl)) {
            throw new IllegalArgumentException ("wrong HttpContext type");
        }
        contexts.remove ((HttpContextImpl)context);
        logger.config ("context removed: " + context.getPath());
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress)schan.socket().getLocalSocketAddress();
    }

    Selector getSelector () {
        return selector;
    }

    void addEvent (Event r) {
        synchronized (lolock) {
            events.add (r);
            selector.wakeup();
        }
    }

    int resultSize () {
        synchronized (lolock) {
            return events.size ();
        }
    }


    /* main server listener task */

    class Dispatcher implements Runnable {

        private void handleEvent (Event r) {
            ExchangeImpl t = r.exchange;
            HttpConnection c = t.getConnection();
            try {
                if (r instanceof WriteFinishedEvent) {

                    int exchanges = endExchange();
                    if (terminating && exchanges == 0) {
                        finished = true;
                    }
                    SocketChannel chan = c.getChannel();
                    LeftOverInputStream is = t.getOriginalInputStream();
                    if (!is.isEOF()) {
                        t.close = true;
                    }
                    if (t.close || idleConnections.size() >= MAX_IDLE_CONNECTIONS) {
                        c.close();
                        allConnections.remove (c);
                    } else {
                        if (is.isDataBuffered()) {
                            /* don't re-enable the interestops, just handle it */
                            handle (c.getChannel(), c);
                        } else {
                            /* re-enable interestops */
                            SelectionKey key = c.getSelectionKey();
                            if (key.isValid()) {
                                key.interestOps (
                                    key.interestOps()|SelectionKey.OP_READ
                                );
                            }
                            c.time = getTime() + IDLE_INTERVAL;
                            idleConnections.add (c);
                        }
                    }
                }
            } catch (IOException e) {
                logger.log (
                    Level.FINER, "Dispatcher (1)", e
                );
                c.close();
            }
        }

        public void run() {
            while (!finished) {
                try {

                    /* process the events list first */

                    while (resultSize() > 0) {
                        Event r;
                        synchronized (lolock) {
                            r = events.remove(0);
                            handleEvent (r);
                        }
                    }

                    selector.select(1000);

                    /* process the selected list now  */

                    Set<SelectionKey> selected = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selected.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove ();
                        if (key.equals (listenerKey)) {
                            if (terminating) {
                                continue;
                            }
                            SocketChannel chan = schan.accept();
                            if (chan == null) {
                                continue; /* cancel something ? */
                            }
                            chan.configureBlocking (false);
                            SelectionKey newkey = chan.register (selector, SelectionKey.OP_READ);
                            HttpConnection c = new HttpConnection ();
                            c.selectionKey = newkey;
                            c.setChannel (chan);
                            newkey.attach (c);
                            allConnections.add (c);
                        } else {
                            try {
                                if (key.isReadable()) {
                                    boolean closed;
                                    SocketChannel chan = (SocketChannel)key.channel();
                                    HttpConnection conn = (HttpConnection)key.attachment();
                                    // interestOps will be restored at end of read
                                    key.interestOps (0);
                                    handle (chan, conn);
                                } else {
                                    assert false;
                                }
                            } catch (IOException e) {
                                HttpConnection conn = (HttpConnection)key.attachment();
                                logger.log (
                                    Level.FINER, "Dispatcher (2)", e
                                );
                                conn.close();
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.log (Level.FINER, "Dispatcher (3)", e);
                }
            }
        }

        public void handle (SocketChannel chan, HttpConnection conn)
        throws IOException
        {
            try {
                Exchange t = new Exchange (chan, protocol, conn);
                executor.execute (t);
            } catch (HttpError e1) {
                logger.log (Level.FINER, "Dispatcher (4)", e1);
                conn.close();
            } catch (IOException e) {
                logger.log (Level.FINER, "Dispatcher (5)", e);
                conn.close();
            }
        }
    }

    static boolean debug = ServerConfig.debugEnabled ();

    static synchronized void dprint (String s) {
        if (debug) {
            System.out.println (s);
        }
    }

    static synchronized void dprint (Exception e) {
        if (debug) {
            System.out.println (e);
            e.printStackTrace();
        }
    }

    Logger getLogger () {
        return logger;
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

        Exchange (SocketChannel chan, String protocol, HttpConnection conn) throws IOException {
            this.chan = chan;
            this.connection = conn;
            this.protocol = protocol;
        }

        public void run () {
            /* context will be null for new connections */
            context = connection.getHttpContext();
            boolean newconnection;
            SSLEngine engine = null;
            String requestLine = null;
            SSLStreams sslStreams = null;
            try {
                if (context != null ) {
                    this.rawin = connection.getInputStream();
                    this.rawout = connection.getRawOutputStream();
                    newconnection = false;
                } else {
                    /* figure out what kind of connection this is */
                    newconnection = true;
                    if (https) {
                        if (sslContext == null) {
                            logger.warning ("SSL connection received. No https contxt created");
                            throw new HttpError ("No SSL context established");
                        }
                        sslStreams = new SSLStreams (ServerImpl.this, sslContext, chan);
                        rawin = sslStreams.getInputStream();
                        rawout = sslStreams.getOutputStream();
                        engine = sslStreams.getSSLEngine();
                        connection.sslStreams = sslStreams;
                    } else {
                        rawin = new BufferedInputStream(
                            new Request.ReadStream (
                                ServerImpl.this, chan
                        ));
                        rawout = new Request.WriteStream (
                            ServerImpl.this, chan
                        );
                    }
                    connection.raw = rawin;
                    connection.rawout = rawout;
                }
                Request req = new Request (rawin, rawout);
                requestLine = req.requestLine();
                if (requestLine == null) {
                    /* connection closed */
                    connection.close();
                    allConnections.remove(connection);
                    return;
                }
                int space = requestLine.indexOf (' ');
                if (space == -1) {
                    reject (Code.HTTP_BAD_REQUEST,
                            requestLine, "Bad request line");
                    return;
                }
                String method = requestLine.substring (0, space);
                int start = space+1;
                space = requestLine.indexOf(' ', start);
                if (space == -1) {
                    reject (Code.HTTP_BAD_REQUEST,
                            requestLine, "Bad request line");
                    return;
                }
                String uriStr = requestLine.substring (start, space);
                URI uri = new URI (uriStr);
                start = space+1;
                String version = requestLine.substring (start);
                Headers headers = req.headers();
                String s = headers.getFirst ("Transfer-encoding");
                long clen = 0L;
                if (s !=null && s.equalsIgnoreCase ("chunked")) {
                    clen = -1L;
                } else {
                    s = headers.getFirst ("Content-Length");
                    if (s != null) {
                        clen = Long.parseLong(s);
                    }
                }
                ctx = contexts.findContext (protocol, uri.getPath());
                if (ctx == null) {
                    reject (Code.HTTP_NOT_FOUND,
                            requestLine, "No context found for request");
                    return;
                }
                connection.setContext (ctx);
                if (ctx.getHandler() == null) {
                    reject (Code.HTTP_INTERNAL_ERROR,
                            requestLine, "No handler for context");
                    return;
                }
                tx = new ExchangeImpl (
                    method, uri, req, clen, connection
                );
                String chdr = headers.getFirst("Connection");
                Headers rheaders = tx.getResponseHeaders();

                if (chdr != null && chdr.equalsIgnoreCase ("close")) {
                    tx.close = true;
                }
                if (version.equalsIgnoreCase ("http/1.0")) {
                    tx.http10 = true;
                    if (chdr == null) {
                        tx.close = true;
                        rheaders.set ("Connection", "close");
                    } else if (chdr.equalsIgnoreCase ("keep-alive")) {
                        rheaders.set ("Connection", "keep-alive");
                        int idle=(int)ServerConfig.getIdleInterval()/1000;
                        int max=(int)ServerConfig.getMaxIdleConnections();
                        String val = "timeout="+idle+", max="+max;
                        rheaders.set ("Keep-Alive", val);
                    }
                }

                if (newconnection) {
                    connection.setParameters (
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
                if (exp != null && exp.equalsIgnoreCase ("100-continue")) {
                    logReply (100, requestLine, null);
                    sendReply (
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
                List<Filter> sf = ctx.getSystemFilters();
                List<Filter> uf = ctx.getFilters();

                Filter.Chain sc = new Filter.Chain(sf, ctx.getHandler());
                Filter.Chain uc = new Filter.Chain(uf, new LinkHandler (sc));

                /* set up the two stream references */
                tx.getRequestBody();
                tx.getResponseBody();
                if (https) {
                    uc.doFilter (new HttpsExchangeImpl (tx));
                } else {
                    uc.doFilter (new HttpExchangeImpl (tx));
                }

            } catch (IOException e1) {
                logger.log (Level.FINER, "ServerImpl.Exchange (1)", e1);
                connection.close();
            } catch (NumberFormatException e3) {
                reject (Code.HTTP_BAD_REQUEST,
                        requestLine, "NumberFormatException thrown");
            } catch (URISyntaxException e) {
                reject (Code.HTTP_BAD_REQUEST,
                        requestLine, "URISyntaxException thrown");
            } catch (Exception e4) {
                logger.log (Level.FINER, "ServerImpl.Exchange (2)", e4);
                connection.close();
            }
        }

        /* used to link to 2 or more Filter.Chains together */

        class LinkHandler implements HttpHandler {
            Filter.Chain nextChain;

            LinkHandler (Filter.Chain nextChain) {
                this.nextChain = nextChain;
            }

            public void handle (HttpExchange exchange) throws IOException {
                nextChain.doFilter (exchange);
            }
        }

        void reject (int code, String requestStr, String message) {
            rejected = true;
            logReply (code, requestStr, message);
            sendReply (
                code, true, "<h1>"+code+Code.msg(code)+"</h1>"+message
            );
            /* connection is already closed by sendReply, now remove it */
            allConnections.remove(connection);
        }

        void sendReply (
            int code, boolean closeNow, String text)
        {
            try {
                String s = "HTTP/1.1 " + code + Code.msg(code) + "\r\n";
                if (text != null && text.length() != 0) {
                    s = s + "Content-Length: "+text.length()+"\r\n";
                    s = s + "Content-Type: text/html\r\n";
                } else {
                    s = s + "Content-Length: 0\r\n";
                    text = "";
                }
                if (closeNow) {
                    s = s + "Connection: close\r\n";
                }
                s = s + "\r\n" + text;
                byte[] b = s.getBytes("ISO8859_1");
                rawout.write (b);
                rawout.flush();
                if (closeNow) {
                    connection.close();
                }
            } catch (IOException e) {
                logger.log (Level.FINER, "ServerImpl.sendReply", e);
                connection.close();
            }
        }

    }

    void logReply (int code, String requestStr, String text) {
        if (text == null) {
            text = "";
        }
        String message = requestStr + " [" + code + " " +
                    Code.msg(code) + "] ("+text+")";
        logger.fine (message);
    }

    long getTicks() {
        return ticks;
    }

    public long getTime() {
        return time;
    }

    void delay () {
        Thread.yield();
        try {
            Thread.sleep (200);
        } catch (InterruptedException e) {}
    }

    private int exchangeCount = 0;

    synchronized void startExchange () {
        exchangeCount ++;
    }

    synchronized int endExchange () {
        exchangeCount --;
        assert exchangeCount >= 0;
        return exchangeCount;
    }

    HttpServer getWrapper () {
        return wrapper;
    }

    /**
     * TimerTask run every CLOCK_TICK ms
     */
    class ServerTimerTask extends TimerTask {
        public void run () {
            LinkedList<HttpConnection> toClose = new LinkedList<HttpConnection>();
            time = System.currentTimeMillis();
            ticks ++;
            synchronized (idleConnections) {
                for (HttpConnection c : idleConnections) {
                    if (c.time <= time) {
                        toClose.add (c);
                    }
                }
                for (HttpConnection c : toClose) {
                    idleConnections.remove (c);
                    allConnections.remove (c);
                    c.close();
                }
            }
        }
    }
}

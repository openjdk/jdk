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

import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import com.sun.net.httpserver.*;
import static com.sun.net.httpserver.HttpExchange.RSPBODY_EMPTY;
import static com.sun.net.httpserver.HttpExchange.RSPBODY_CHUNKED;

class ExchangeImpl {

    Headers reqHdrs, rspHdrs;
    Request req;
    String method;
    private boolean writefinished;
    URI uri;
    HttpConnection connection;
    long reqContentLen;
    long rspContentLen;
    /* raw streams which access the socket directly */
    InputStream ris;
    OutputStream ros;
    Thread thread;
    /* close the underlying connection when this exchange finished */
    boolean close;
    boolean closed;
    boolean http10 = false;

    /* for formatting the Date: header */
    private static final DateTimeFormatter FORMATTER;
    private static final boolean perExchangeAttributes =
        !System.getProperty("jdk.httpserver.attributes", "")
              .equals("context");
    static {
        String pattern = "EEE, dd MMM yyyy HH:mm:ss zzz";
        FORMATTER = DateTimeFormatter.ofPattern(pattern, Locale.US)
                                     .withZone(ZoneId.of("GMT"));
    }

    private static final String HEAD = "HEAD";

    /* streams which take care of the HTTP protocol framing
     * and are passed up to higher layers
     */
    InputStream uis;
    OutputStream uos;
    LeftOverInputStream uis_orig; // uis may have be a user supplied wrapper
    PlaceholderOutputStream uos_orig;

    boolean sentHeaders; /* true after response headers sent */
    final Map<String, Object> attributes;
    int rcode = -1;
    HttpPrincipal principal;
    ServerImpl server;

    // Used to control that ServerImpl::endExchange is called
    // exactly once for this exchange. ServerImpl::endExchange decrements
    // the refcount that was incremented by calling ServerImpl::startExchange
    // in this ExchangeImpl constructor.
    private final AtomicBoolean ended = new AtomicBoolean();

    // Used to ensure that the Event.ExchangeFinished is posted only
    // once for this exchange. The Event.ExchangeFinished is what will
    // eventually cause the ServerImpl::finishedLatch to be triggered,
    // once the number of active exchanges reaches 0 and ServerImpl::stop
    // has been requested.
    private final AtomicBoolean finished = new AtomicBoolean();

    ExchangeImpl(
        String m, URI u, Request req, long len, HttpConnection connection
    ) throws IOException {
        this.req = req;
        this.reqHdrs = Headers.of(req.headers());
        this.rspHdrs = new Headers();
        this.method = m;
        this.uri = u;
        this.connection = connection;
        this.reqContentLen = len;
        this.attributes = perExchangeAttributes
            ? new ConcurrentHashMap<>()
            : getHttpContext().getAttributes();
        /* ros only used for headers, body written directly to stream */
        this.ros = req.outputStream();
        this.ris = req.inputStream();
        server = getServerImpl();
        server.startExchange();
    }

    /**
     * When true, writefinished indicates that all bytes expected
     * by the client have been written to the response body
     * outputstream, and that the response body outputstream has
     * been closed. When all bytes have also been pulled from
     * the request body input stream, this makes it possible to
     * reuse the connection for the next request.
     */
    synchronized boolean writefinished() {
        return writefinished;
    }

    /**
     * Calls ServerImpl::endExchange if not already called for this
     * exchange. ServerImpl::endExchange must be called exactly once
     * per exchange, and this method ensures that it is not called
     * more than once for this exchange.
     * @return the new (or current) value of the exchange count.
     */
    int endExchange() {
        // only call server.endExchange(); once per exchange
        if (ended.compareAndSet(false, true)) {
            return server.endExchange();
        }
        return server.getExchangeCount();
    }

    /**
     * Posts the ExchangeFinished event if not already posted.
     * If `writefinished` is true, marks the exchange as {@link
     * #writefinished()} so that the connection can be reused.
     * @param writefinished whether all bytes expected by the
     *                      client have been writen out to the
     *                      response body output stream.
     */
    void postExchangeFinished(boolean writefinished) {
        // only post ExchangeFinished once per exchange
        if (finished.compareAndSet(false, true)) {
            if (writefinished) {
                synchronized (this) {
                    assert this.writefinished == false;
                    this.writefinished = true;
                }
            }
            Event e = new Event.ExchangeFinished(this);
            getHttpContext().getServerImpl().addEvent(e);
        }
    }

    public Headers getRequestHeaders() {
        return reqHdrs;
    }

    public Headers getResponseHeaders() {
        return rspHdrs;
    }

    public URI getRequestURI() {
        return uri;
    }

    public String getRequestMethod() {
        return method;
    }

    public HttpContextImpl getHttpContext() {
        return connection.getHttpContext();
    }

    private boolean isHeadRequest() {
        return HEAD.equals(getRequestMethod());
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        /* close the underlying connection if,
         * a) the streams not set up yet, no response can be sent, or
         * b) if the wrapper output stream is not set up, or
         * c) if the close of the input/output stream fails
         */
        try {
            if (uis_orig == null || uos == null) {
                connection.close();
                return;
            }
            if (!uos_orig.isWrapped()) {
                connection.close();
                return;
            }
            if (!uis_orig.isClosed()) {
                uis_orig.close();
            }
            uos.close();
        } catch (IOException e) {
            connection.close();
        } finally {
            postExchangeFinished(false);
        }
    }

    public InputStream getRequestBody() {
        if (uis != null) {
            return uis;
        }
        if (reqContentLen == -1L) {
            uis_orig = new ChunkedInputStream(this, ris);
            uis = uis_orig;
        } else {
            uis_orig = new FixedLengthInputStream(this, ris, reqContentLen);
            uis = uis_orig;
        }
        return uis;
    }

    LeftOverInputStream getOriginalInputStream() {
        return uis_orig;
    }

    public int getResponseCode() {
        return rcode;
    }

    public OutputStream getResponseBody() {
        /* TODO. Change spec to remove restriction below. Filters
         * cannot work with this restriction
         *
         * if (!sentHeaders) {
         *    throw new IllegalStateException("headers not sent");
         * }
         */
        if (uos == null) {
            uos_orig = new PlaceholderOutputStream(null);
            uos = uos_orig;
        }
        return uos;
    }


    /* returns the place holder stream, which is the stream
     * returned from the 1st call to getResponseBody()
     * The "real" ouputstream is then placed inside this
     */
    PlaceholderOutputStream getPlaceholderResponseBody() {
        getResponseBody();
        return uos_orig;
    }

    private static final byte[] CRLF = new byte[] {0x0D, 0x0A};

    public void sendResponseHeaders(int rCode, long contentLen)
    throws IOException
    {
        final Logger logger = server.getLogger();
        if (sentHeaders) {
            throw new IOException("headers already sent");
        }
        this.rcode = rCode;
        String statusLine = "HTTP/1.1 " + rCode + Code.msg(rCode);
        ByteArrayOutputStream tmpout = new ByteArrayOutputStream();
        PlaceholderOutputStream o = getPlaceholderResponseBody();
        tmpout.write(bytes(statusLine, false, 0), 0, statusLine.length());
        tmpout.write(CRLF);
        boolean noContentToSend = false; // assume there is content
        boolean noContentLengthHeader = false; // must not send Content-length is set
        rspHdrs.set("Date", FORMATTER.format(Instant.now()));

        /* check for response type that is not allowed to send a body */

        if ((rCode >= 100 && rCode < 200) /* informational */
            ||(rCode == 204)           /* no content */
            ||(rCode == 304))          /* not modified */
        {
            if (contentLen != RSPBODY_EMPTY) {
                String msg = "sendResponseHeaders: rCode = " + rCode
                    + ": forcing contentLen = RSPBODY_EMPTY";
                logger.log(Level.WARNING, msg);
            }
            contentLen = RSPBODY_EMPTY;
            noContentLengthHeader = (rCode != 304);
        }

        if (isHeadRequest() || rCode == 304) {
            /* HEAD requests or 304 responses should not set a content length by passing it
             * through this API, but should instead manually set the required
             * headers.*/
            if (contentLen >= 0) {
                String msg =
                    "sendResponseHeaders: being invoked with a content length for a HEAD request";
                logger.log(Level.WARNING, msg);
            }
            noContentToSend = true;
            contentLen = 0;
            o.setWrappedStream(new FixedLengthOutputStream(this, ros, contentLen));
        } else { /* not a HEAD request or 304 response */
            if (contentLen == RSPBODY_CHUNKED) {
                if (http10) {
                    o.setWrappedStream(new UndefLengthOutputStream(this, ros));
                    close = true;
                } else {
                    rspHdrs.set("Transfer-encoding", "chunked");
                    o.setWrappedStream(new ChunkedOutputStream(this, ros));
                }
            } else {
                if (contentLen == RSPBODY_EMPTY) {
                    noContentToSend = true;
                    contentLen = 0;
                }
                if (!noContentLengthHeader) {
                    rspHdrs.set("Content-length", Long.toString(contentLen));
                }
                o.setWrappedStream(new FixedLengthOutputStream(this, ros, contentLen));
            }
        }

        // A custom handler can request that the connection be
        // closed after the exchange by supplying Connection: close
        // to the response header. Nothing to do if the exchange is
        // already set up to be closed.
        if (!close) {
            Stream<String> conheader =
                    Optional.ofNullable(rspHdrs.get("Connection"))
                    .map(List::stream).orElse(Stream.empty());
            if (conheader.anyMatch("close"::equalsIgnoreCase)) {
                logger.log(Level.DEBUG, "Connection: close requested by handler");
                close = true;
            }
        }

        write(rspHdrs, tmpout);
        this.rspContentLen = contentLen;
        tmpout.writeTo(ros);
        sentHeaders = true;
        logger.log(Level.TRACE, "Sent headers: noContentToSend=" + noContentToSend);
        if (noContentToSend) {
            ros.flush();
            close();
        }
        server.logReply(rCode, req.requestLine(), null);
    }

    void write(Headers map, OutputStream os) throws IOException {
        Set<Map.Entry<String, List<String>>> entries = map.entrySet();
        for (Map.Entry<String, List<String>> entry : entries) {
            String key = entry.getKey();
            byte[] buf;
            List<String> values = entry.getValue();
            for (String val : values) {
                int i = key.length();
                buf = bytes(key, true, 2);
                buf[i++] = ':';
                buf[i++] = ' ';
                os.write(buf, 0, i);
                buf = bytes(val, false, 2);
                i = val.length();
                buf[i++] = '\r';
                buf[i++] = '\n';
                os.write(buf, 0, i);
            }
        }
        os.write('\r');
        os.write('\n');
    }

    private byte[] rspbuf = new byte[128]; // used by bytes()

    /**
     * convert string to byte[], using rspbuf
     * Make sure that at least "extra" bytes are free at end
     * of rspbuf. Reallocate rspbuf if not big enough.
     * caller must check return value to see if rspbuf moved
     *
     * Header values are supposed to be limited to 7-bit ASCII
     * but 8-bit has to be allowed (for ISO_8859_1). For efficiency
     * we just down cast 16 bit Java chars to byte. We don't allow
     * any character that can't be encoded in 8 bits.
     */
    private byte[] bytes(String s, boolean isKey, int extra) throws IOException {
        Utils.checkHeader(s, !isKey);
        int slen = s.length();
        if (slen+extra > rspbuf.length) {
            int diff = slen + extra - rspbuf.length;
            rspbuf = new byte [2* (rspbuf.length + diff)];
        }
        char c[] = s.toCharArray();
        for (int i=0; i<c.length; i++) {
            rspbuf[i] = (byte)c[i];
        }
        return rspbuf;
    }

    public InetSocketAddress getRemoteAddress() {
        Socket s = connection.getChannel().socket();
        InetAddress ia = s.getInetAddress();
        int port = s.getPort();
        return new InetSocketAddress(ia, port);
    }

    public InetSocketAddress getLocalAddress() {
        Socket s = connection.getChannel().socket();
        InetAddress ia = s.getLocalAddress();
        int port = s.getLocalPort();
        return new InetSocketAddress(ia, port);
    }

    public String getProtocol() {
        String reqline = req.requestLine();
        int index = reqline.lastIndexOf(' ');
        return reqline.substring(index+1);
    }

    public SSLSession getSSLSession() {
        SSLEngine e = connection.getSSLEngine();
        if (e == null) {
            return null;
        }
        return e.getSession();
    }

    public Object getAttribute(String name) {
        return attributes.get(Objects.requireNonNull(name, "null name parameter"));
    }

    public void setAttribute(String name, Object value) {
        var key = Objects.requireNonNull(name, "null name parameter");
        if (value != null) {
            attributes.put(key, value);
        } else {
            attributes.remove(key);
        }
    }

    public void setStreams(InputStream i, OutputStream o) {
        assert uis != null;
        if (i != null) {
            uis = i;
        }
        if (o != null) {
            uos = o;
        }
    }

    /**
     * PP
     */
    HttpConnection getConnection() {
        return connection;
    }

    ServerImpl getServerImpl() {
        return getHttpContext().getServerImpl();
    }

    public HttpPrincipal getPrincipal() {
        return principal;
    }

    void setPrincipal(HttpPrincipal principal) {
        this.principal = principal;
    }

    static ExchangeImpl get(HttpExchange t) {
        if (t instanceof HttpExchangeImpl) {
            return ((HttpExchangeImpl)t).getExchangeImpl();
        } else {
            assert t instanceof HttpsExchangeImpl;
            return ((HttpsExchangeImpl)t).getExchangeImpl();
        }
    }
}

/**
 * An OutputStream which wraps another stream
 * which is supplied either at creation time, or sometime later.
 * If a caller/user tries to write to this stream before
 * the wrapped stream has been provided, then an IOException will
 * be thrown.
 */
class PlaceholderOutputStream extends java.io.OutputStream {

    OutputStream wrapped;

    PlaceholderOutputStream(OutputStream os) {
        wrapped = os;
    }

    void setWrappedStream(OutputStream os) {
        wrapped = os;
    }

    boolean isWrapped() {
        return wrapped != null;
    }

    private void checkWrap() throws IOException {
        if (wrapped == null) {
            throw new IOException("response headers not sent yet");
        }
    }

    public void write(int b) throws IOException {
        checkWrap();
        wrapped.write(b);
    }

    public void write(byte b[]) throws IOException {
        checkWrap();
        wrapped.write(b);
    }

    public void write(byte b[], int off, int len) throws IOException {
        checkWrap();
        wrapped.write(b, off, len);
    }

    public void flush() throws IOException {
        checkWrap();
        wrapped.flush();
    }

    public void close() throws IOException {
        checkWrap();
        wrapped.close();
    }
}

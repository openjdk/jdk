/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.http.HttpHeaders;
import sun.net.NetProperties;
import sun.net.util.IPAddressUtil;

import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLPermission;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * Miscellaneous utilities
 */
public final class Utils {

    public static final boolean ASSERTIONSENABLED;
    static {
        boolean enabled = false;
        assert enabled = true;
        ASSERTIONSENABLED = enabled;
    }
//    public static final boolean TESTING;
//    static {
//        if (ASSERTIONSENABLED) {
//            PrivilegedAction<String> action = () -> System.getProperty("test.src");
//            TESTING = AccessController.doPrivileged(action) != null;
//        } else TESTING = false;
//    }
    public static final boolean DEBUG = // Revisit: temporary dev flag.
            getBooleanProperty(DebugLogger.HTTP_NAME, false);
    public static final boolean DEBUG_HPACK = // Revisit: temporary dev flag.
            getBooleanProperty(DebugLogger.HPACK_NAME, false);
    public static final boolean TESTING = DEBUG;

    /**
     * Allocated buffer size. Must never be higher than 16K. But can be lower
     * if smaller allocation units preferred. HTTP/2 mandates that all
     * implementations support frame payloads of at least 16K.
     */
    private static final int DEFAULT_BUFSIZE = 16 * 1024;

    public static final int BUFSIZE = getIntegerNetProperty(
            "jdk.httpclient.bufsize", DEFAULT_BUFSIZE
    );

    private static final Set<String> DISALLOWED_HEADERS_SET = Set.of(
            "authorization", "connection", "cookie", "content-length",
            "date", "expect", "from", "host", "origin", "proxy-authorization",
            "referer", "user-agent", "upgrade", "via", "warning");

    public static final Predicate<String>
        ALLOWED_HEADERS = header -> !Utils.DISALLOWED_HEADERS_SET.contains(header);

    public static ByteBuffer getBuffer() {
        return ByteBuffer.allocate(BUFSIZE);
    }

    public static Throwable getCompletionCause(Throwable x) {
        if (!(x instanceof CompletionException)
                && !(x instanceof ExecutionException)) return x;
        final Throwable cause = x.getCause();
        return cause == null ? x : cause;
    }

    public static IOException getIOException(Throwable t) {
        if (t instanceof IOException) {
            return (IOException) t;
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            return getIOException(cause);
        }
        return new IOException(t);
    }

    private Utils() { }

    /**
     * Returns the security permissions required to connect to the proxy, or
     * {@code null} if none is required or applicable.
     */
    public static URLPermission permissionForProxy(InetSocketAddress proxyAddress) {
        if (proxyAddress == null)
            return null;

        StringBuilder sb = new StringBuilder();
        sb.append("socket://")
          .append(proxyAddress.getHostString()).append(":")
          .append(proxyAddress.getPort());
        String urlString = sb.toString();
        return new URLPermission(urlString, "CONNECT");
    }

    /**
     * Returns the security permission required for the given details.
     */
    public static URLPermission permissionForServer(URI uri,
                                                    String method,
                                                    Stream<String> headers) {
        String urlString = new StringBuilder()
                .append(uri.getScheme()).append("://")
                .append(uri.getAuthority())
                .append(uri.getPath()).toString();

        StringBuilder actionStringBuilder = new StringBuilder(method);
        String collected = headers.collect(joining(","));
        if (!collected.isEmpty()) {
            actionStringBuilder.append(":").append(collected);
        }
        return new URLPermission(urlString, actionStringBuilder.toString());
    }


    // ABNF primitives defined in RFC 7230
    private static final boolean[] tchar      = new boolean[256];
    private static final boolean[] fieldvchar = new boolean[256];

    static {
        char[] allowedTokenChars =
                ("!#$%&'*+-.^_`|~0123456789" +
                 "abcdefghijklmnopqrstuvwxyz" +
                 "ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();
        for (char c : allowedTokenChars) {
            tchar[c] = true;
        }
        for (char c = 0x21; c < 0xFF; c++) {
            fieldvchar[c] = true;
        }
        fieldvchar[0x7F] = false; // a little hole (DEL) in the range
    }

    /*
     * Validates a RFC 7230 field-name.
     */
    public static boolean isValidName(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255 || !tchar[c]) {
                return false;
            }
        }
        return !token.isEmpty();
    }

    /**
     * If the address was created with a domain name, then return
     * the domain name string. If created with a literal IP address
     * then return null. We do this to avoid doing a reverse lookup
     * Used to populate the TLS SNI parameter. So, SNI is only set
     * when a domain name was supplied.
     */
    public static String getServerName(InetSocketAddress addr) {
        String host = addr.getHostString();
        if (IPAddressUtil.textToNumericFormatV4(host) != null)
            return null;
        if (IPAddressUtil.textToNumericFormatV6(host) != null)
            return null;
        return host;
    }

    /*
     * Validates a RFC 7230 field-value.
     *
     * "Obsolete line folding" rule
     *
     *     obs-fold = CRLF 1*( SP / HTAB )
     *
     * is not permitted!
     */
    public static boolean isValidValue(String token) {
        boolean accepted = true;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255) {
                return false;
            }
            if (accepted) {
                if (c == ' ' || c == '\t') {
                    accepted = false;
                } else if (!fieldvchar[c]) {
                    return false; // forbidden byte
                }
            } else {
                if (c != ' ' && c != '\t') {
                    if (fieldvchar[c]) {
                        accepted = true;
                    } else {
                        return false; // forbidden byte
                    }
                }
            }
        }
        return accepted;
    }

    public static int getIntegerNetProperty(String name, int defaultValue) {
        return AccessController.doPrivileged((PrivilegedAction<Integer>) () ->
                NetProperties.getInteger(name, defaultValue));
    }

    static String getNetProperty(String name) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () ->
                NetProperties.get(name));
    }

    static boolean getBooleanProperty(String name, boolean def) {
        return AccessController.doPrivileged((PrivilegedAction<Boolean>) () ->
                Boolean.parseBoolean(System.getProperty(name, String.valueOf(def))));
    }

    public static SSLParameters copySSLParameters(SSLParameters p) {
        SSLParameters p1 = new SSLParameters();
        p1.setAlgorithmConstraints(p.getAlgorithmConstraints());
        p1.setCipherSuites(p.getCipherSuites());
        // JDK 8 EXCL START
        p1.setEnableRetransmissions(p.getEnableRetransmissions());
        p1.setMaximumPacketSize(p.getMaximumPacketSize());
        // JDK 8 EXCL END
        p1.setEndpointIdentificationAlgorithm(p.getEndpointIdentificationAlgorithm());
        p1.setNeedClientAuth(p.getNeedClientAuth());
        String[] protocols = p.getProtocols();
        if (protocols != null) {
            p1.setProtocols(protocols.clone());
        }
        p1.setSNIMatchers(p.getSNIMatchers());
        p1.setServerNames(p.getServerNames());
        p1.setUseCipherSuitesOrder(p.getUseCipherSuitesOrder());
        p1.setWantClientAuth(p.getWantClientAuth());
        return p1;
    }

    /**
     * Set limit to position, and position to mark.
     */
    public static void flipToMark(ByteBuffer buffer, int mark) {
        buffer.limit(buffer.position());
        buffer.position(mark);
    }

    public static String stackTrace(Throwable t) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        String s = null;
        try {
            PrintStream p = new PrintStream(bos, true, "US-ASCII");
            t.printStackTrace(p);
            s = bos.toString("US-ASCII");
        } catch (UnsupportedEncodingException ex) {
            throw new InternalError(ex); // Can't happen
        }
        return s;
    }

    /**
     * Copies as much of src to dst as possible.
     * Return number of bytes copied
     */
    public static int copy(ByteBuffer src, ByteBuffer dst) {
        int srcLen = src.remaining();
        int dstLen = dst.remaining();
        if (srcLen > dstLen) {
            int diff = srcLen - dstLen;
            int limit = src.limit();
            src.limit(limit - diff);
            dst.put(src);
            src.limit(limit);
        } else {
            dst.put(src);
        }
        return srcLen - src.remaining();
    }

    /** Threshold beyond which data is no longer copied into the current
     * buffer, if that buffer has enough unused space. */
    private static final int COPY_THRESHOLD = 8192;

    /**
     * Adds the data from buffersToAdd to currentList. Either 1) appends the
     * data from a particular buffer to the last buffer in the list ( if
     * there is enough unused space ), or 2) adds it to the list.
     *
     * @return the number of bytes added
     */
    public static long accumulateBuffers(List<ByteBuffer> currentList,
                                         List<ByteBuffer> buffersToAdd) {
        long accumulatedBytes = 0;
        for (ByteBuffer bufferToAdd : buffersToAdd) {
            int remaining = bufferToAdd.remaining();
            if (remaining <= 0)
                continue;
            int listSize = currentList.size();
            if (listSize == 0) {
                currentList.add(bufferToAdd);
                accumulatedBytes = remaining;
                continue;
            }

            ByteBuffer lastBuffer = currentList.get(listSize - 1);
            int freeSpace = lastBuffer.capacity() - lastBuffer.limit();
            if (remaining <= COPY_THRESHOLD && freeSpace >= remaining) {
                // append the new data to the unused space in the last buffer
                int position = lastBuffer.position();
                int limit = lastBuffer.limit();
                lastBuffer.position(limit);
                lastBuffer.limit(limit + remaining);
                lastBuffer.put(bufferToAdd);
                lastBuffer.position(position);
            } else {
                currentList.add(bufferToAdd);
            }
            accumulatedBytes += remaining;
        }
        return accumulatedBytes;
    }

    public static ByteBuffer copy(ByteBuffer src) {
        ByteBuffer dst = ByteBuffer.allocate(src.remaining());
        dst.put(src);
        dst.flip();
        return dst;
    }

    public static String dump(Object... objects) {
        return Arrays.toString(objects);
    }

    public static String stringOf(Collection<?> source) {
        // We don't know anything about toString implementation of this
        // collection, so let's create an array
        return Arrays.toString(source.toArray());
    }

    public static long remaining(ByteBuffer[] bufs) {
        long remain = 0;
        for (ByteBuffer buf : bufs) {
            remain += buf.remaining();
        }
        return remain;
    }

    public static boolean hasRemaining(List<ByteBuffer> bufs) {
        synchronized (bufs) {
            for (ByteBuffer buf : bufs) {
                if (buf.hasRemaining())
                    return true;
            }
        }
        return false;
    }

    public static long remaining(List<ByteBuffer> bufs) {
        long remain = 0;
        synchronized (bufs) {
            for (ByteBuffer buf : bufs) {
                remain += buf.remaining();
            }
        }
        return remain;
    }

    public static int remaining(List<ByteBuffer> bufs, int max) {
        long remain = 0;
        synchronized (bufs) {
            for (ByteBuffer buf : bufs) {
                remain += buf.remaining();
                if (remain > max) {
                    throw new IllegalArgumentException("too many bytes");
                }
            }
        }
        return (int) remain;
    }

    public static long remaining(ByteBufferReference[] refs) {
        long remain = 0;
        for (ByteBufferReference ref : refs) {
            remain += ref.get().remaining();
        }
        return remain;
    }

    public static int remaining(ByteBufferReference[] refs, int max) {
        long remain = 0;
        for (ByteBufferReference ref : refs) {
            remain += ref.get().remaining();
            if (remain > max) {
                throw new IllegalArgumentException("too many bytes");
            }
        }
        return (int) remain;
    }

    public static int remaining(ByteBuffer[] refs, int max) {
        long remain = 0;
        for (ByteBuffer b : refs) {
            remain += b.remaining();
            if (remain > max) {
                throw new IllegalArgumentException("too many bytes");
            }
        }
        return (int) remain;
    }

    public static void close(Closeable... closeables) {
        for (Closeable c : closeables) {
            try {
                c.close();
            } catch (IOException ignored) { }
        }
    }

    // Put all these static 'empty' singletons here
    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    public static final ByteBuffer[] EMPTY_BB_ARRAY = new ByteBuffer[0];
    public static final List<ByteBuffer> EMPTY_BB_LIST = List.of();
    public static final ByteBufferReference[] EMPTY_BBR_ARRAY = new ByteBufferReference[0];

    /**
     * Returns a slice of size {@code amount} from the given buffer. If the
     * buffer contains more data than {@code amount}, then the slice's capacity
     * ( and, but not just, its limit ) is set to {@code amount}. If the buffer
     * does not contain more data than {@code amount}, then the slice's capacity
     * will be the same as the given buffer's capacity.
     */
    public static ByteBuffer sliceWithLimitedCapacity(ByteBuffer buffer, int amount) {
        final int index = buffer.position() + amount;
        final int limit = buffer.limit();
        if (index != limit) {
            // additional data in the buffer
            buffer.limit(index);  // ensures that the slice does not go beyond
        } else {
            // no additional data in the buffer
            buffer.limit(buffer.capacity());  // allows the slice full capacity
        }

        ByteBuffer newb = buffer.slice();
        buffer.position(index);
        buffer.limit(limit);    // restore the original buffer's limit
        newb.limit(amount);     // slices limit to amount (capacity may be greater)
        return newb;
    }

    /**
     * Get the Charset from the Content-encoding header. Defaults to
     * UTF_8
     */
    public static Charset charsetFrom(HttpHeaders headers) {
        String encoding = headers.firstValue("Content-encoding")
                .orElse("UTF_8");
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }

    public static UncheckedIOException unchecked(IOException e) {
        return new UncheckedIOException(e);
    }

    /**
     * Get a logger for debug HTTP traces.
     *
     * The logger should only be used with levels whose severity is
     * {@code <= DEBUG}. By default, this logger will forward all messages
     * logged to an internal logger named "jdk.internal.httpclient.debug".
     * In addition, if the property -Djdk.internal.httpclient.debug=true is set,
     * it will print the messages on stderr.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     *
     * @return A logger for HTTP internal debug traces
     */
    public static Logger getDebugLogger(Supplier<String> dbgTag) {
        return getDebugLogger(dbgTag, DEBUG);
    }

    /**
     * Get a logger for debug HTTP traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.debug".
     * In addition, if the message severity level is >= to
     * the provided {@code errLevel} it will print the messages on stderr.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stderr in
     *          addition to forwarding to the internal logger, use
     *          {@code getDebugLogger(this::dbgTag, Level.ALL);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, true);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getDebugLogger(this::dbgTag, Level.OFF);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, false);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     * @param errLevel The level above which messages will be also printed on
     *               stderr (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HTTP internal debug traces
     */
    static Logger getDebugLogger(Supplier<String> dbgTag, Level errLevel) {
        return DebugLogger.createHttpLogger(dbgTag, Level.OFF, errLevel);
    }

    /**
     * Get a logger for debug HTTP traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.debug".
     * In addition, the provided boolean {@code on==true}, it will print the
     * messages on stderr.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stderr in
     *          addition to forwarding to the internal logger, use
     *          {@code getDebugLogger(this::dbgTag, true);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, Level.ALL);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getDebugLogger(this::dbgTag, false);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, Level.OFF);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     * @param on  Whether messages should also be printed on
     *               stderr (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HTTP internal debug traces
     */
    public static Logger getDebugLogger(Supplier<String> dbgTag, boolean on) {
        Level errLevel = on ? Level.ALL : Level.OFF;
        return getDebugLogger(dbgTag, errLevel);
    }

    /**
     * Get a logger for debug HPACK traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.hpack.debug".
     * In addition, if the message severity level is >= to
     * the provided {@code outLevel} it will print the messages on stdout.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stdout in
     *          addition to forwarding to the internal logger, use
     *          {@code getHpackLogger(this::dbgTag, Level.ALL);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, true);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getHpackLogger(this::dbgTag, Level.OFF);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, false);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "Http2Connection(SocketTube(3))/hpack.Decoder(3)")
     * @param outLevel The level above which messages will be also printed on
     *               stdout (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HPACK internal debug traces
     */
    public static Logger getHpackLogger(Supplier<String> dbgTag, Level outLevel) {
        Level errLevel = Level.OFF;
        return DebugLogger.createHpackLogger(dbgTag, outLevel, errLevel);
    }

    /**
     * Get a logger for debug HPACK traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.hpack.debug".
     * In addition, the provided boolean {@code on==true}, it will print the
     * messages on stdout.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stdout in
     *          addition to forwarding to the internal logger, use
     *          {@code getHpackLogger(this::dbgTag, true);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, Level.ALL);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getHpackLogger(this::dbgTag, false);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, Level.OFF);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "Http2Connection(SocketTube(3))/hpack.Decoder(3)")
     * @param on  Whether messages should also be printed on
     *            stdout (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HPACK internal debug traces
     */
    public static Logger getHpackLogger(Supplier<String> dbgTag, boolean on) {
        Level outLevel = on ? Level.ALL : Level.OFF;
        return getHpackLogger(dbgTag, outLevel);
    }
}

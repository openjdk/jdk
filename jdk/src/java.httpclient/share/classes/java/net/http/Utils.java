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
 * questions.
 */

package java.net.http;

import sun.net.NetProperties;

import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.NetPermission;
import java.net.URI;
import java.net.URLPermission;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongBinaryOperator;
import java.util.function.Predicate;

/**
 * Miscellaneous utilities
 */
final class Utils {

    /**
     * Allocated buffer size. Must never be higher than 16K. But can be lower
     * if smaller allocation units preferred. HTTP/2 mandates that all
     * implementations support frame payloads of at least 16K.
     */
    public static final int BUFSIZE = 16 * 1024;

    private static final Set<String> DISALLOWED_HEADERS_SET = Set.of(
            "authorization", "connection", "cookie", "content-length",
            "date", "expect", "from", "host", "origin", "proxy-authorization",
            "referer", "user-agent", "upgrade", "via", "warning");

    static final Predicate<String>
        ALLOWED_HEADERS = header -> !Utils.DISALLOWED_HEADERS_SET.contains(header);

    static final Predicate<String>
        ALL_HEADERS = header -> true;

    static InetSocketAddress getAddress(HttpRequestImpl req) {
        URI uri = req.uri();
        if (uri == null) {
            return req.authority();
        }
        int port = uri.getPort();
        if (port == -1) {
            if (uri.getScheme().equalsIgnoreCase("https")) {
                port = 443;
            } else {
                port = 80;
            }
        }
        String host = uri.getHost();
        if (req.proxy() == null) {
            return new InetSocketAddress(host, port);
        } else {
            return InetSocketAddress.createUnresolved(host, port);
        }
    }

    /**
     * Puts position to limit and limit to capacity so we can resume reading
     * into this buffer, but if required > 0 then limit may be reduced so that
     * no more than required bytes are read next time.
     */
    static void resumeChannelRead(ByteBuffer buf, int required) {
        int limit = buf.limit();
        buf.position(limit);
        int capacity = buf.capacity() - limit;
        if (required > 0 && required < capacity) {
            buf.limit(limit + required);
        } else {
            buf.limit(buf.capacity());
        }
    }

    private Utils() { }

    /**
     * Validates a RFC7230 token
     */
    static void validateToken(String token, String errormsg) {
        int length = token.length();
        for (int i = 0; i < length; i++) {
            int c = token.codePointAt(i);
            if (c >= 0x30 && c <= 0x39 // 0 - 9
                    || (c >= 0x61 && c <= 0x7a) // a - z
                    || (c >= 0x41 && c <= 0x5a) // A - Z
                    || (c >= 0x21 && c <= 0x2e && c != 0x22 && c != 0x27 && c != 0x2c)
                    || (c >= 0x5e && c <= 0x60)
                    || (c == 0x7c) || (c == 0x7e)) {
            } else {
                throw new IllegalArgumentException(errormsg);
            }
        }
    }

    /**
     * Returns the security permission required for the given details.
     * If method is CONNECT, then uri must be of form "scheme://host:port"
     */
    static URLPermission getPermission(URI uri,
                                       String method,
                                       Map<String, List<String>> headers) {
        StringBuilder sb = new StringBuilder();

        String urlstring, actionstring;

        if (method.equals("CONNECT")) {
            urlstring = uri.toString();
            actionstring = "CONNECT";
        } else {
            sb.append(uri.getScheme())
                    .append("://")
                    .append(uri.getAuthority())
                    .append(uri.getPath());
            urlstring = sb.toString();

            sb = new StringBuilder();
            sb.append(method);
            if (headers != null && !headers.isEmpty()) {
                sb.append(':');
                Set<String> keys = headers.keySet();
                boolean first = true;
                for (String key : keys) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(key);
                    first = false;
                }
            }
            actionstring = sb.toString();
        }
        return new URLPermission(urlstring, actionstring);
    }

    static void checkNetPermission(String target) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null)
            return;
        NetPermission np = new NetPermission(target);
        sm.checkPermission(np);
    }

    static int getIntegerNetProperty(String name, int defaultValue) {
        return AccessController.doPrivileged((PrivilegedAction<Integer>) () ->
                NetProperties.getInteger(name, defaultValue));
    }

    static String getNetProperty(String name) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () ->
                NetProperties.get(name));
    }

    static SSLParameters copySSLParameters(SSLParameters p) {
        SSLParameters p1 = new SSLParameters();
        p1.setAlgorithmConstraints(p.getAlgorithmConstraints());
        p1.setCipherSuites(p.getCipherSuites());
        p1.setEnableRetransmissions(p.getEnableRetransmissions());
        p1.setEndpointIdentificationAlgorithm(p.getEndpointIdentificationAlgorithm());
        p1.setMaximumPacketSize(p.getMaximumPacketSize());
        p1.setNeedClientAuth(p.getNeedClientAuth());
        String[] protocols = p.getProtocols();
        if (protocols != null)
            p1.setProtocols(protocols.clone());
        p1.setSNIMatchers(p.getSNIMatchers());
        p1.setServerNames(p.getServerNames());
        p1.setUseCipherSuitesOrder(p.getUseCipherSuitesOrder());
        p1.setWantClientAuth(p.getWantClientAuth());
        return p1;
    }

    /**
     * Set limit to position, and position to mark.
     */
    static void flipToMark(ByteBuffer buffer, int mark) {
        buffer.limit(buffer.position());
        buffer.position(mark);
    }

    static String stackTrace(Throwable t) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        String s = null;
        try {
            PrintStream p = new PrintStream(bos, true, "US-ASCII");
            t.printStackTrace(p);
            s = bos.toString("US-ASCII");
        } catch (UnsupportedEncodingException ex) {
            // can't happen
        }
        return s;
    }

    /**
     * Copies as much of src to dst as possible.
     */
    static void copy(ByteBuffer src, ByteBuffer dst) {
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
    }

    static ByteBuffer copy(ByteBuffer src) {
        ByteBuffer dst = ByteBuffer.allocate(src.remaining());
        dst.put(src);
        dst.flip();
        return dst;
    }

    //
    // Helps to trim long names (packages, nested/inner types) in logs/toString
    //
    static String toStringSimple(Object o) {
        return o.getClass().getSimpleName() + "@" +
                Integer.toHexString(System.identityHashCode(o));
    }

    //
    // 1. It adds a number of remaining bytes;
    // 2. Standard Buffer-type toString for CharBuffer (since it adheres to the
    // contract of java.lang.CharSequence.toString() which is both not too
    // useful and not too private)
    //
    static String toString(Buffer b) {
        return toStringSimple(b)
                + "[pos=" + b.position()
                + " lim=" + b.limit()
                + " cap=" + b.capacity()
                + " rem=" + b.remaining() + "]";
    }

    static String toString(CharSequence s) {
        return s == null
                ? "null"
                : toStringSimple(s) + "[len=" + s.length() + "]";
    }

    static String dump(Object... objects) {
        return Arrays.toString(objects);
    }

    static final System.Logger logger = System.getLogger("java.net.http.WebSocket");

    static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    static String webSocketSpecViolation(String section, String detail) {
        return "RFC 6455 " + section + " " + detail;
    }

    static void logResponse(HttpResponseImpl r) {
        if (!Log.requests()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        String method = r.request().method();
        URI uri = r.uri();
        String uristring = uri == null ? "" : uri.toString();
        sb.append('(').append(method).append(" ").append(uristring).append(") ").append(Integer.toString(r.statusCode()));
        Log.logResponse(sb.toString());
    }

    static int remaining(ByteBuffer[] bufs) {
        int remain = 0;
        for (ByteBuffer buf : bufs)
            remain += buf.remaining();
        return remain;
    }

    // assumes buffer was written into starting at position zero
    static void unflip(ByteBuffer buf) {
        buf.position(buf.limit());
        buf.limit(buf.capacity());
    }

    static void close(Closeable... chans) {
        for (Closeable chan : chans) {
            try {
                chan.close();
            } catch (IOException e) {
            }
        }
    }

    static ByteBuffer[] reduce(ByteBuffer[] bufs, int start, int number) {
        if (start == 0 && number == bufs.length)
            return bufs;
        ByteBuffer[] nbufs = new ByteBuffer[number];
        int j = 0;
        for (int i=start; i<start+number; i++)
            nbufs[j++] = bufs[i];
        return nbufs;
    }

    static String asString(ByteBuffer buf) {
        byte[] b = new byte[buf.remaining()];
        buf.get(b);
        return new String(b, StandardCharsets.US_ASCII);
    }

    // Put all these static 'empty' singletons here
    @SuppressWarnings("rawtypes")
    static CompletableFuture[] EMPTY_CFARRAY = new CompletableFuture[0];

    static ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    static ByteBuffer[] EMPTY_BB_ARRAY = new ByteBuffer[0];
}

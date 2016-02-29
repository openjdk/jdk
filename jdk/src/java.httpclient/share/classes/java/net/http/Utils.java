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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.NetPermission;
import java.net.URI;
import java.net.URLPermission;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLParameters;
import sun.net.NetProperties;

/**
 * Miscellaneous utilities
 */
class Utils {

    /**
     * Allocated buffer size. Must never be higher than 16K. But can be lower
     * if smaller allocation units preferred. HTTP/2 mandates that all
     * implementations support frame payloads of at least 16K.
     */
    public static final int BUFSIZE = 16 * 1024;

    /** Validates a RFC7230 token */
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
     * Return sthe security permission required for the given details.
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
                    .append(uri.getHost())
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
        return AccessController.doPrivileged((PrivilegedAction<Integer>)() ->
            NetProperties.getInteger(name, defaultValue) );
    }

    static String getNetProperty(String name) {
        return AccessController.doPrivileged((PrivilegedAction<String>)() ->
            NetProperties.get(name) );
    }

    static SSLParameters copySSLParameters(SSLParameters p) {
        SSLParameters p1 = new SSLParameters();
        p1.setAlgorithmConstraints(p.getAlgorithmConstraints());
        p1.setCipherSuites(p.getCipherSuites());
        p1.setEnableRetransmissions(p.getEnableRetransmissions());
        p1.setEndpointIdentificationAlgorithm(p.getEndpointIdentificationAlgorithm());
        p1.setMaximumPacketSize(p.getMaximumPacketSize());
        p1.setNeedClientAuth(p.getNeedClientAuth());
        p1.setProtocols(p.getProtocols().clone());
        p1.setSNIMatchers(p.getSNIMatchers());
        p1.setServerNames(p.getServerNames());
        p1.setUseCipherSuitesOrder(p.getUseCipherSuitesOrder());
        p1.setWantClientAuth(p.getWantClientAuth());
        return p1;
    }


    /** Resumes reading into the given buffer. */
    static void unflip(ByteBuffer buf) {
        buf.position(buf.limit());
        buf.limit(buf.capacity());
    }

    /**
     * Set limit to position, and position to mark.
     *
     *
     * @param buffer
     * @param mark
     */
    static void flipToMark(ByteBuffer buffer, int mark) {
        buffer.limit(buffer.position());
        buffer.position(mark);
    }

    /** Compact and leave ready for reading. */
    static void compact(List<ByteBuffer> buffers) {
        for (ByteBuffer b : buffers) {
            b.compact();
            b.flip();
        }
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

    /** Copies as much of src to dst as possible. */
    static void copy (ByteBuffer src, ByteBuffer dst) {
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

    static String combine(String[] s) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String s1 : s) {
            if (!first) {
                sb.append(", ");
                first = false;
            }
            sb.append(s1);
        }
        sb.append(']');
        return sb.toString();
    }
}

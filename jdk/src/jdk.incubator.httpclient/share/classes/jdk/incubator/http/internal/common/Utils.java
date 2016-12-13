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

package jdk.incubator.http.internal.common;

import jdk.internal.misc.InnocuousThread;
import sun.net.NetProperties;

import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.NetPermission;
import java.net.URI;
import java.net.URLPermission;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * Miscellaneous utilities
 */
public final class Utils {

    /**
     * Allocated buffer size. Must never be higher than 16K. But can be lower
     * if smaller allocation units preferred. HTTP/2 mandates that all
     * implementations support frame payloads of at least 16K.
     */
    public static final int DEFAULT_BUFSIZE = 16 * 1024;

    public static final int BUFSIZE = getIntegerNetProperty(
            "jdk.httpclient.bufsize", DEFAULT_BUFSIZE
    );

    private static final Set<String> DISALLOWED_HEADERS_SET = Set.of(
            "authorization", "connection", "cookie", "content-length",
            "date", "expect", "from", "host", "origin", "proxy-authorization",
            "referer", "user-agent", "upgrade", "via", "warning");

    public static final Predicate<String>
        ALLOWED_HEADERS = header -> !Utils.DISALLOWED_HEADERS_SET.contains(header);

    public static final Predicate<String>
        ALL_HEADERS = header -> true;

    public static ByteBuffer getBuffer() {
        return ByteBuffer.allocate(BUFSIZE);
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

    /**
     * We use the same buffer for reading all headers and dummy bodies in an Exchange.
     */
    public static ByteBuffer getExchangeBuffer() {
        ByteBuffer buf = getBuffer();
        // Force a read the first time it is used
        buf.limit(0);
        return buf;
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

    public static ExecutorService innocuousThreadPool() {
        return Executors.newCachedThreadPool(
                (r) -> InnocuousThread.newThread("DefaultHttpClient", r));
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

    /**
     * Returns the security permission required for the given details.
     * If method is CONNECT, then uri must be of form "scheme://host:port"
     */
    public static URLPermission getPermission(URI uri,
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

    public static void checkNetPermission(String target) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return;
        }
        NetPermission np = new NetPermission(target);
        sm.checkPermission(np);
    }

    public static int getIntegerNetProperty(String name, int defaultValue) {
        return AccessController.doPrivileged((PrivilegedAction<Integer>) () ->
                NetProperties.getInteger(name, defaultValue));
    }

    static String getNetProperty(String name) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () ->
                NetProperties.get(name));
    }

    public static SSLParameters copySSLParameters(SSLParameters p) {
        SSLParameters p1 = new SSLParameters();
        p1.setAlgorithmConstraints(p.getAlgorithmConstraints());
        p1.setCipherSuites(p.getCipherSuites());
        p1.setEnableRetransmissions(p.getEnableRetransmissions());
        p1.setEndpointIdentificationAlgorithm(p.getEndpointIdentificationAlgorithm());
        p1.setMaximumPacketSize(p.getMaximumPacketSize());
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
            // can't happen
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

    // copy up to amount from src to dst, but no more
    public static int copyUpTo(ByteBuffer src, ByteBuffer dst, int amount) {
        int toCopy = Math.min(src.remaining(), Math.min(dst.remaining(), amount));
        copy(src, dst, toCopy);
        return toCopy;
    }

    /**
     * Copy amount bytes from src to dst. at least amount must be
     * available in both dst and in src
     */
    public static void copy(ByteBuffer src, ByteBuffer dst, int amount) {
        int excess = src.remaining() - amount;
        assert excess >= 0;
        if (excess > 0) {
            int srclimit = src.limit();
            src.limit(srclimit - excess);
            dst.put(src);
            src.limit(srclimit);
        } else {
            dst.put(src);
        }
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

    public static int remaining(ByteBuffer[] bufs) {
        int remain = 0;
        for (ByteBuffer buf : bufs) {
            remain += buf.remaining();
        }
        return remain;
    }

    public static int remaining(List<ByteBuffer> bufs) {
        int remain = 0;
        for (ByteBuffer buf : bufs) {
            remain += buf.remaining();
        }
        return remain;
    }

    public static int remaining(ByteBufferReference[] refs) {
        int remain = 0;
        for (ByteBufferReference ref : refs) {
            remain += ref.get().remaining();
        }
        return remain;
    }

    // assumes buffer was written into starting at position zero
    static void unflip(ByteBuffer buf) {
        buf.position(buf.limit());
        buf.limit(buf.capacity());
    }

    public static void close(Closeable... closeables) {
        for (Closeable c : closeables) {
            try {
                c.close();
            } catch (IOException ignored) { }
        }
    }

    public static void close(Throwable t, Closeable... closeables) {
        for (Closeable c : closeables) {
            try {
                ExceptionallyCloseable.close(t, c);
            } catch (IOException ignored) { }
        }
    }

    /**
     * Returns an array with the same buffers, but starting at position zero
     * in the array.
     */
    public static ByteBuffer[] reduce(ByteBuffer[] bufs, int start, int number) {
        if (start == 0 && number == bufs.length) {
            return bufs;
        }
        ByteBuffer[] nbufs = new ByteBuffer[number];
        int j = 0;
        for (int i=start; i<start+number; i++) {
            nbufs[j++] = bufs[i];
        }
        return nbufs;
    }

    static String asString(ByteBuffer buf) {
        byte[] b = new byte[buf.remaining()];
        buf.get(b);
        return new String(b, StandardCharsets.US_ASCII);
    }

    /**
     * Returns a single threaded executor which uses one invocation
     * of the parent executor to execute tasks (in sequence).
     *
     * Use a null valued Runnable to terminate.
     */
    // TODO: this is a blocking way of doing this;
    public static Executor singleThreadExecutor(Executor parent) {
        BlockingQueue<Optional<Runnable>> queue = new LinkedBlockingQueue<>();
        parent.execute(() -> {
            while (true) {
                try {
                    Optional<Runnable> o = queue.take();
                    if (!o.isPresent()) {
                        return;
                    }
                    o.get().run();
                } catch (InterruptedException ex) {
                    return;
                }
            }
        });
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                queue.offer(Optional.ofNullable(command));
            }
        };
    }

    private static void executeInline(Runnable r) {
        r.run();
    }

    static Executor callingThreadExecutor() {
        return Utils::executeInline;
    }

    // Put all these static 'empty' singletons here
    @SuppressWarnings("rawtypes")
    public static final CompletableFuture[] EMPTY_CFARRAY = new CompletableFuture[0];

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    public static final ByteBuffer[] EMPTY_BB_ARRAY = new ByteBuffer[0];

    public static ByteBuffer slice(ByteBuffer buffer, int amount) {
        ByteBuffer newb = buffer.slice();
        newb.limit(amount);
        buffer.position(buffer.position() + amount);
        return newb;
    }

}

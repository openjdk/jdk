/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.common;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectionKey;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.net.http.common.DebugLogger.LoggerConfig;
import jdk.internal.net.http.HttpRequestImpl;

import sun.net.NetProperties;
import sun.net.www.HeaderParser;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.net.Authenticator.RequestorType.PROXY;
import static java.net.Authenticator.RequestorType.SERVER;

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
//        TESTING = ASSERTIONSENABLED ? System.getProperty("test.src") != null : false;
//    }
    public static final LoggerConfig DEBUG_CONFIG =
            getLoggerConfig(DebugLogger.HTTP_NAME, LoggerConfig.OFF);
    public static final LoggerConfig DEBUG_WS_CONFIG =
            getLoggerConfig(DebugLogger.WS_NAME, LoggerConfig.OFF);
    public static final LoggerConfig DEBUG_HPACK_CONFIG =
            getLoggerConfig(DebugLogger.HPACK_NAME, LoggerConfig.OFF);

    public static final boolean DEBUG = DEBUG_CONFIG.on(); // Revisit: temporary dev flag.
    public static final boolean DEBUG_WS = DEBUG_WS_CONFIG.on(); // Revisit: temporary dev flag.
    public static final boolean TESTING = DEBUG;

    public static final boolean isHostnameVerificationDisabled = // enabled by default
            hostnameVerificationDisabledValue();

    private static LoggerConfig getLoggerConfig(String loggerName, LoggerConfig def) {
        var prop = System.getProperty(loggerName);
        if (prop == null) return def;
        var config = LoggerConfig.OFF;
        for (var s : prop.split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            int len = s.length();
            switch (len) {
                case 3 -> {
                    if (s.regionMatches(true, 0, "err", 0, 3)) {
                        config = config.withErrLevel(Level.ALL);
                        continue;
                    }
                    if (s.regionMatches(true, 0, "out", 0, 3)) {
                        config = config.withOutLevel(Level.ALL);
                        continue;
                    }
                    if (s.regionMatches(true, 0, "log", 0, 3)) {
                        config = config.withLogLevel(Level.ALL);
                    }
                }
                case 4 -> {
                    if (s.regionMatches(true, 0, "true", 0, 4)) {
                        config = config.withErrLevel(Level.ALL).withLogLevel(Level.ALL);
                    }
                }
                default -> { continue; }
            }
        }
        return config;
    }

    private static boolean hostnameVerificationDisabledValue() {
        String prop = getProperty("jdk.internal.httpclient.disableHostnameVerification");
        if (prop == null)
            return false;
        return prop.isEmpty() ? true : Boolean.parseBoolean(prop);
    }

    // A threshold to decide whether to slice or copy.
    // see sliceOrCopy
    public static final int SLICE_THRESHOLD = 32;

    /**
     * The capacity of ephemeral {@link ByteBuffer}s allocated to pass data to and from the client.
     * It is ensured to have a value between 1 and 2^14 (16,384).
     */
    public static final int BUFSIZE = getIntegerNetProperty(
            "jdk.httpclient.bufsize", 1,
            // We cap at 2^14 (16,384) for two main reasons:
            // - The initial frame size is 2^14 (RFC 9113)
            // - SSL record layer fragments data in chunks of 2^14 bytes or less (RFC 5246)
            1 << 14,
            // We choose 2^14 (16,384) as the default, because:
            // 1. It maximizes throughput within the limits described above
            // 2. It is small enough to not create a GC bottleneck when it is partially filled
            1 << 14,
            true);

    public static final BiPredicate<String,String> ACCEPT_ALL = (x,y) -> true;

    private static final Set<String> DISALLOWED_HEADERS_SET = getDisallowedHeaders();

    private static Set<String> getDisallowedHeaders() {
        Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headers.addAll(Set.of("connection", "content-length", "expect", "host", "upgrade",
                "alt-used"));

        String v = getNetProperty("jdk.httpclient.allowRestrictedHeaders");
        if (v != null) {
            // any headers found are removed from set.
            String[] tokens = v.trim().split(",");
            for (String token : tokens) {
                headers.remove(token);
            }
            return Collections.unmodifiableSet(headers);
        } else {
            return Collections.unmodifiableSet(headers);
        }
    }

    public static final BiPredicate<String, String>
            ALLOWED_HEADERS = (header, unused) -> !DISALLOWED_HEADERS_SET.contains(header);

    private static final Set<String> DISALLOWED_REDIRECT_HEADERS_SET = getDisallowedRedirectHeaders();

    private static Set<String> getDisallowedRedirectHeaders() {
        Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headers.addAll(Set.of("Authorization", "Cookie", "Origin", "Referer", "Host"));

        return Collections.unmodifiableSet(headers);
    }

    public static final BiPredicate<String, String>
            ALLOWED_REDIRECT_HEADERS = (header, _) -> !DISALLOWED_REDIRECT_HEADERS_SET.contains(header);

    public static final BiPredicate<String, String> VALIDATE_USER_HEADER =
            (name, value) -> {
                assert name != null : "null header name";
                assert value != null : "null header value";
                if (!isValidName(name)) {
                    throw newIAE("invalid header name: \"%s\"", name);
                }
                if (!Utils.ALLOWED_HEADERS.test(name, null)) {
                    throw newIAE("restricted header name: \"%s\"", name);
                }
                if (!isValidValue(value)) {
                    throw newIAE("invalid header value for %s: \"%s\"", name, value);
                }
                return true;
            };

    public enum UseVTForSelector { ALWAYS, NEVER, DEFAULT }

    public static UseVTForSelector useVTForSelector(String property, String defval) {
        String useVtForSelector = System.getProperty(property, defval);
        return Stream.of(UseVTForSelector.values())
                .filter((v) -> v.name().equalsIgnoreCase(useVtForSelector))
                .findFirst().orElse(UseVTForSelector.DEFAULT);
    }

    public static <T extends Throwable> T addSuppressed(T x, Throwable suppressed) {
        if (x != suppressed && suppressed != null) {
            var sup = x.getSuppressed();
            if (sup != null && sup.length > 0) {
                if (Arrays.asList(sup).contains(suppressed)) {
                    return x;
                }
            }
            sup = suppressed.getSuppressed();
            if (sup != null && sup.length > 0) {
                if (Arrays.asList(sup).contains(x)) {
                    return x;
                }
            }
            x.addSuppressed(suppressed);
        }
        return x;
    }

    /**
     * {@return a string comparing the given deadline with now, typically
     *  something like "due since Nms" or "due in Nms"}
     *
     * @apiNote
     * This method recognize deadlines set to Instant.MIN
     * and Instant.MAX as special cases meaning "due" and
     * "not scheduled".
     *
     * @param now       now
     * @param deadline  the deadline
     */
    public static String debugDeadline(Deadline now, Deadline deadline) {
        boolean isDue = deadline.compareTo(now) <= 0;
        try {
            if (isDue) {
                if (deadline.equals(Deadline.MIN)) {
                    return "due (Deadline.MIN)";
                } else {
                    return "due since " + deadline.until(now, ChronoUnit.MILLIS) + "ms";
                }
            } else if (deadline.equals(Deadline.MAX)) {
                return "not scheduled (Deadline.MAX)";
            } else {
                return "due in " + now.until(deadline, ChronoUnit.MILLIS) + "ms";
            }
        } catch (ArithmeticException x) {
            return isDue ? "due since too long" : "due in the far future";
        }
    }

    public record ProxyHeaders(HttpHeaders userHeaders, HttpHeaders systemHeaders) {}

    public static final BiPredicate<String, String> PROXY_TUNNEL_RESTRICTED()  {
        return (k,v) -> !"host".equalsIgnoreCase(k);
    }

    private static final Predicate<String> IS_HOST = "host"::equalsIgnoreCase;
    private static final Predicate<String> IS_PROXY_HEADER = (k) ->
            k != null && k.length() > 6 && "proxy-".equalsIgnoreCase(k.substring(0,6));
    private static final Predicate<String> NO_PROXY_HEADER =
            IS_PROXY_HEADER.negate();
    private static final Predicate<String> ALL_HEADERS = (s) -> true;

    private static final Set<String> PROXY_AUTH_DISABLED_SCHEMES;
    private static final Set<String> PROXY_AUTH_TUNNEL_DISABLED_SCHEMES;
    static {
        String proxyAuthDisabled =
                getNetProperty("jdk.http.auth.proxying.disabledSchemes");
        String proxyAuthTunnelDisabled =
                getNetProperty("jdk.http.auth.tunneling.disabledSchemes");
        PROXY_AUTH_DISABLED_SCHEMES =
                proxyAuthDisabled == null ? Set.of() :
                        Stream.of(proxyAuthDisabled.split(","))
                                .map(String::trim)
                                .filter((s) -> !s.isEmpty())
                                .collect(Collectors.toUnmodifiableSet());
        PROXY_AUTH_TUNNEL_DISABLED_SCHEMES =
                proxyAuthTunnelDisabled == null ? Set.of() :
                        Stream.of(proxyAuthTunnelDisabled.split(","))
                                .map(String::trim)
                                .filter((s) -> !s.isEmpty())
                                .collect(Collectors.toUnmodifiableSet());
    }

    public static <T> CompletableFuture<T> wrapForDebug(Logger logger, String name, CompletableFuture<T> cf) {
        if (logger.on()) {
            return cf.handle((r,t) -> {
                logger.log("%s completed %s", name, t == null ? "successfully" : t );
                return cf;
            }).thenCompose(Function.identity());
        } else {
            return cf;
        }
    }

    private static final String WSPACES = " \t\r\n";
    private static final boolean isAllowedForProxy(String name,
                                                   String value,
                                                   Set<String> disabledSchemes,
                                                   Predicate<String> allowedKeys) {
        if (!allowedKeys.test(name)) return false;
        if (disabledSchemes.isEmpty()) return true;
        if (name.equalsIgnoreCase("proxy-authorization")) {
            if (value.isEmpty()) return false;
            for (String scheme : disabledSchemes) {
                int slen = scheme.length();
                int vlen = value.length();
                if (vlen == slen) {
                    if (value.equalsIgnoreCase(scheme)) {
                        return false;
                    }
                } else if (vlen > slen) {
                    if (value.substring(0,slen).equalsIgnoreCase(scheme)) {
                        int c = value.codePointAt(slen);
                        if (WSPACES.indexOf(c) > -1
                                || Character.isSpaceChar(c)
                                || Character.isWhitespace(c)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public static final BiPredicate<String, String> PROXY_TUNNEL_FILTER =
            (s,v) -> isAllowedForProxy(s, v, PROXY_AUTH_TUNNEL_DISABLED_SCHEMES,
                    // Allows Proxy-* and Host headers when establishing the tunnel.
                    IS_PROXY_HEADER.or(IS_HOST));
    public static final BiPredicate<String, String> PROXY_FILTER =
            (s,v) -> isAllowedForProxy(s, v, PROXY_AUTH_DISABLED_SCHEMES,
                    ALL_HEADERS);
    public static final BiPredicate<String, String> NO_PROXY_HEADERS_FILTER =
            (n,v) -> Utils.NO_PROXY_HEADER.test(n);

    /**
     * Check the user headers to see if the Authorization or ProxyAuthorization
     * were set. We need to set special flags in the request if so. Otherwise
     * we can't distinguish user set from Authenticator set headers
     */
    public static void setUserAuthFlags(HttpRequestImpl request, HttpHeaders userHeaders) {
        if (userHeaders.firstValue("Authorization").isPresent()) {
            request.setUserSetAuthFlag(SERVER, true);
        }
        if (userHeaders.firstValue("Proxy-Authorization").isPresent()) {
            request.setUserSetAuthFlag(PROXY, true);
        }
    }

    public static boolean proxyHasDisabledSchemes(boolean tunnel) {
        return tunnel ? ! PROXY_AUTH_TUNNEL_DISABLED_SCHEMES.isEmpty()
                      : ! PROXY_AUTH_DISABLED_SCHEMES.isEmpty();
    }

    /**
     * Creates a new {@link Proxy} instance for the given proxy iff it is
     * neither null, {@link Proxy#NO_PROXY Proxy.NO_PROXY}, nor already a
     * {@code Proxy} instance.
     */
    public static Proxy copyProxy(Proxy proxy) {
        return proxy == null || proxy.getClass() == Proxy.class
                ? proxy
                : new Proxy(proxy.type(), proxy.address());
    }

    // WebSocket connection Upgrade headers
    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_UPGRADE    = "Upgrade";

    public static final void setWebSocketUpgradeHeaders(HttpRequestImpl request) {
        request.setSystemHeader(HEADER_UPGRADE, "websocket");
        request.setSystemHeader(HEADER_CONNECTION, "Upgrade");
    }

    private static final ConcurrentHashMap<Integer, String> opsMap = new ConcurrentHashMap<>();
    static {
        opsMap.put(0, "None");
    }

    public static String interestOps(SelectionKey key) {
        if (key == null) return "null-key";
        try {
            return describeOps(key.interestOps());
        } catch (CancelledKeyException x) {
            return "cancelled-key";
        }
    }

    public static String readyOps(SelectionKey key) {
        if (key == null) return "null-key";
        try {
            return describeOps(key.readyOps());
        } catch (CancelledKeyException x) {
            return "cancelled-key";
        }
    }

    public static String describeOps(int interestOps) {
        String ops = opsMap.get(interestOps);
        if (ops != null) return ops;
        StringBuilder opsb = new StringBuilder();
        int mask = SelectionKey.OP_READ
                | SelectionKey.OP_WRITE
                | SelectionKey.OP_CONNECT
                | SelectionKey.OP_ACCEPT;
        if ((interestOps & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
            opsb.append("R");
        }
        if ((interestOps & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
            opsb.append("W");
        }
        if ((interestOps & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
            opsb.append("A");
        }
        if ((interestOps & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT) {
            opsb.append("C");
        }
        if ((interestOps | mask) != mask) {
            opsb.append("("+interestOps+")");
        }
        ops = opsb.toString();
        opsMap.put(interestOps, ops);
        return ops;
    }

    public static IllegalArgumentException newIAE(String message, Object... args) {
        return new IllegalArgumentException(format(message, args));
    }

    /**
     * {@return a new {@link ByteBuffer} instance of {@link #BUFSIZE} capacity}
     */
    public static ByteBuffer getBuffer() {
        return ByteBuffer.allocate(BUFSIZE);
    }

    /**
     * {@return a new {@link ByteBuffer} instance whose capacity is set to the
     * smaller of the specified {@code maxCapacity} and the default
     * ({@value BUFSIZE})}
     *
     * @param maxCapacity a buffer capacity, in bytes
     * @throws IllegalArgumentException if {@code maxCapacity < 0}
     */
    public static ByteBuffer getBufferWithAtMost(long maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException(
                    // Match the message produced by `ByteBuffer::createCapacityException`
                    "capacity < 0: (%s < 0)".formatted(maxCapacity));
        }
        int effectiveCapacity = (int) Math.min(maxCapacity, BUFSIZE);
        return ByteBuffer.allocate(effectiveCapacity);
    }

    public static Throwable getCompletionCause(Throwable x) {
        Throwable cause = x;
        while ((cause instanceof CompletionException)
                || (cause instanceof ExecutionException)) {
            cause = cause.getCause();
        }
        if (cause == null && cause != x) {
            throw new InternalError("Unexpected null cause", x);
        }
        return cause;
    }

    public static Throwable getCancelCause(Throwable x) {
        Throwable cause = getCompletionCause(x);
        if (cause instanceof ConnectionExpiredException) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static IOException toIOException(Throwable cause) {
        if (cause == null) return null;
        if (cause instanceof CompletionException ce) {
            cause = ce.getCause();
        } else if (cause instanceof ExecutionException ee) {
            cause = ee.getCause();
        }
        if (cause instanceof IOException io) {
            return io;
        } else if (cause instanceof UncheckedIOException uio) {
            return uio.getCause();
        }
        return new IOException(cause.getMessage(), cause);
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
     * Adds a more specific exception detail message, based on the given
     * exception type and the message supplier. This is primarily to present
     * more descriptive messages in IOExceptions that may be visible to calling
     * code.
     *
     * @return a possibly new exception that has as its detail message, the
     *         message from the messageSupplier, and the given throwable as its
     *         cause. Otherwise returns the given throwable
     */
    public static Throwable wrapWithExtraDetail(Throwable t,
                                                Supplier<String> messageSupplier) {
        if (!(t instanceof IOException))
            return t;

        if (t instanceof SSLHandshakeException)
            return t;  // no need to decorate

        String msg = messageSupplier.get();
        if (msg == null)
            return t;

        if (t instanceof ConnectionExpiredException) {
            if (t.getCause() instanceof SSLHandshakeException)
                return t;  // no need to decorate
            IOException ioe = new IOException(msg, t.getCause());
            t = new ConnectionExpiredException(ioe);
        } else {
            IOException ioe = new IOException(msg, t);
            t = ioe;
        }
        return t;
    }

    private Utils() { }

    private static final boolean[] LOWER_CASE_CHARS = new boolean[128];

    // ABNF primitives defined in RFC 7230
    private static final boolean[] tchar      = new boolean[256];
    private static final boolean[] fieldvchar = new boolean[256];

    static {
        char[] lcase = ("!#$%&'*+-.^_`|~0123456789" +
                "abcdefghijklmnopqrstuvwxyz").toCharArray();
        for (char c : lcase) {
            tchar[c] = true;
            LOWER_CASE_CHARS[c] = true;
        }
        char[] ucase = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();
        for (char c : ucase) {
            tchar[c] = true;
        }
        for (char c = 0x21; c <= 0xFF; c++) {
            fieldvchar[c] = true;
        }
        fieldvchar[0x7F] = false; // a little hole (DEL) in the range
    }

    public static boolean isValidLowerCaseName(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255 || !LOWER_CASE_CHARS[c]) {
                return false;
            }
        }
        return !token.isEmpty();
    }

    /*
     * Validates an RFC 7230 field-name.
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
     * Validates an RFC 7230 field-value.
     *
     * "Obsolete line folding" rule
     *
     *     obs-fold = CRLF 1*( SP / HTAB )
     *
     * is not permitted!
     */
    public static boolean isValidValue(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255) {
                return false;
            }
            if (c == ' ' || c == '\t') {
                continue;
            } else if (!fieldvchar[c]) {
                return false; // forbidden byte
            }
        }
        return true;
    }

    public static int getIntegerNetProperty(String name, int defaultValue) {
        return NetProperties.getInteger(name, defaultValue);
    }

    public static String getNetProperty(String name) {
        return NetProperties.get(name);
    }

    public static boolean getBooleanProperty(String name, boolean def) {
        return Boolean.parseBoolean(System.getProperty(name, String.valueOf(def)));
    }

    public static String getProperty(String name) {
        return System.getProperty(name);
    }

    public static int getIntegerProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }

    public static long getLongProperty(String name, long defaultValue) {
        return Long.parseLong(System.getProperty(name, String.valueOf(defaultValue)));
    }

    public static int getIntegerNetProperty(String property, int min, int max, int defaultValue, boolean log) {
        int value =  Utils.getIntegerNetProperty(property, defaultValue);
        // use default value if misconfigured
        if (value < min || value > max) {
            if (log && Log.errors()) {
                Log.logError("Property value for {0}={1} not in [{2}..{3}]: " +
                        "using default={4}", property, value, min, max, defaultValue);
            }
            value = defaultValue;
        }
        return value;
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
        if (p.getNeedClientAuth()) {
            p1.setNeedClientAuth(true);
        }
        if (p.getWantClientAuth()) {
            p1.setWantClientAuth(true);
        }
        String[] protocols = p.getProtocols();
        if (protocols != null) {
            p1.setProtocols(protocols.clone());
        }
        p1.setSNIMatchers(p.getSNIMatchers());
        p1.setServerNames(p.getServerNames());
        p1.setUseCipherSuitesOrder(p.getUseCipherSuitesOrder());
        p1.setSignatureSchemes(p.getSignatureSchemes());
        p1.setNamedGroups(p.getNamedGroups());
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
        PrintStream p = new PrintStream(bos, true, US_ASCII);
        t.printStackTrace(p);
        return bos.toString(US_ASCII);
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

    public static ByteBuffer copyAligned(ByteBuffer src) {
        int len = src.remaining();
        int size = ((len + 7) >> 3) << 3;
        assert size >= len;
        ByteBuffer dst = ByteBuffer.allocate(size);
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
        if (bufs == null) return 0;
        long remain = 0;
        for (ByteBuffer buf : bufs) {
            remain += buf.remaining();
        }
        return remain;
    }

    public static boolean hasRemaining(List<ByteBuffer> bufs) {
        if (bufs == null) return false;
        for (ByteBuffer buf : bufs) {
            if (buf.hasRemaining())
                return true;
        }
        return false;
    }

    public static boolean hasRemaining(ByteBuffer[] bufs) {
        if (bufs == null) return false;
        for (ByteBuffer buf : bufs) {
            if (buf.hasRemaining())
                return true;
        }
        return false;
    }

    public static long remaining(List<ByteBuffer> bufs) {
        if (bufs == null) return 0L;
        long remain = 0;
        for (ByteBuffer buf : bufs) {
            remain += buf.remaining();
        }
        return remain;
    }

    //

    /**
     * Reads as much bytes as possible from the buffer list, and
     * write them in the provided {@code data} byte array.
     * Returns the number of bytes read and written to the byte array.
     * This method advances the position in the byte buffers it reads
     * @param bufs A list of byte buffer
     * @param data A byte array to write into
     * @param offset Where to start writing in the byte array
     * @return the amount of bytes read and written to the byte array
     */
    public static int read(List<ByteBuffer> bufs, byte[] data, int offset) {
        int pos = offset;
        for (ByteBuffer buf : bufs) {
            if (pos >= data.length) break;
            int read = Math.min(buf.remaining(), data.length - pos);
            if (read <= 0) continue;
            buf.get(data, pos, read);
            pos += read;
        }
        return pos - offset;
    }

    /**
     * Returns the next buffer that has remaining bytes, or null.
     * @param iterator an iterator
     * @return the next buffer that has remaining bytes, or null
     */
    public static ByteBuffer next(Iterator<ByteBuffer> iterator) {
        ByteBuffer next = null;
        while (iterator.hasNext() && !(next = iterator.next()).hasRemaining());
        return next == null || !next.hasRemaining() ? null : next;
    }

    /**
     * Compute the relative consolidated position in bytes at which the two
     * input mismatch, or -1 if there is no mismatch.
     * @apiNote This method behaves as {@link ByteBuffer#mismatch(ByteBuffer)}.
     * @param these a first list of byte buffers
     * @param those a second list of byte buffers
     * @return  the relative consolidated position in bytes at which the two
     *          input mismatch, or -1L if there is no mismatch.
     */
    public static long mismatch(List<ByteBuffer> these, List<ByteBuffer> those) {
        if (these.isEmpty()) return those.isEmpty() ? -1 : 0;
        if (those.isEmpty()) return 0;
        Iterator<ByteBuffer> lefti = these.iterator(), righti = those.iterator();
        ByteBuffer left = next(lefti), right = next(righti);
        long parsed = 0;
        while (left != null || right != null) {
            int m = left == null || right == null ? 0 : left.mismatch(right);
            if (m == -1) {
                parsed = parsed + left.remaining();
                assert right.remaining() == left.remaining();
                if ((left = next(lefti)) != null) {
                    if ((right = next(righti)) != null) {
                        continue;
                    }
                    return parsed;
                }
                return (right = next(righti)) != null ? parsed : -1;
            }
            if (m == 0) return parsed;
            parsed = parsed + m;
            if (m < left.remaining()) {
                if (m < right.remaining()) {
                    return parsed;
                }
                if ((right = next(righti)) != null) {
                    left = left.slice(m, left.remaining() - m);
                    continue;
                }
                return parsed;
            }
            assert m < right.remaining();
            if ((left = next(lefti)) != null) {
                right = right.slice(m, right.remaining() - m);
                continue;
            }
            return parsed;
        }
        return -1L;
    }

    public static long synchronizedRemaining(List<ByteBuffer> bufs) {
        if (bufs == null) return 0L;
        synchronized (bufs) {
            return remaining(bufs);
        }
    }

    public static long remaining(List<ByteBuffer> bufs, long max) {
        if (bufs == null) return 0;
        long remain = 0;
        for (ByteBuffer buf : bufs) {
            int size = buf.remaining();
            if (max - remain < size) {
                throw new IllegalArgumentException("too many bytes");
            }
            remain += size;
        }
        return remain;
    }

    public static int remaining(List<ByteBuffer> bufs, int max) {
        // safe cast since max is an int
        return (int) remaining(bufs, (long) max);
    }

    public static long remaining(ByteBuffer[] refs, long max) {
        if (refs == null) return 0;
        long remain = 0;
        for (ByteBuffer b : refs) {
            int size = b.remaining();
            if (max - remain < size) {
                throw new IllegalArgumentException("too many bytes");
            }
            remain += size;
        }
        return remain;
    }

    public static int remaining(ByteBuffer[] refs, int max) {
        // safe cast since max is an int
        return (int) remaining(refs, (long) max);
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
     * Creates a slice of a buffer, possibly copying the data instead
     * of slicing.
     * If the buffer capacity is less than the {@linkplain #SLICE_THRESHOLD
     * default slice threshold}, or if the capacity minus the length to slice
     * is less than the {@linkplain #SLICE_THRESHOLD threshold}, returns a slice.
     * Otherwise, copy so as not to retain a reference to a big buffer
     * for a small slice.
     * @param src the original buffer
     * @param start where to start copying/slicing from src
     * @param len   how many byte to slice/copy
     * @return a new ByteBuffer for the given slice
     */
    public static ByteBuffer sliceOrCopy(ByteBuffer src, int start, int len) {
        return sliceOrCopy(src, start, len, SLICE_THRESHOLD);
    }

    /**
     * Creates a slice of a buffer, possibly copying the data instead
     * of slicing.
     * If the buffer capacity minus the length to slice is less than the threshold,
     * returns a slice.
     * Otherwise, copy so as not to retain a reference to a buffer
     * that contains more bytes than needed.
     * @param src the original buffer
     * @param start where to start copying/slicing from src
     * @param len   how many byte to slice/copy
     * @param threshold a threshold to decide whether to slice or copy
     * @return a new ByteBuffer for the given slice
     */
    public static ByteBuffer sliceOrCopy(ByteBuffer src, int start, int len, int threshold) {
        assert src.hasArray();
        int cap = src.array().length;
        if (cap - len < threshold) {
            return src.slice(start, len);
        } else {
            byte[] b = new byte[len];
            if (len > 0) {
                src.get(start, b, 0, len);
            }
            return ByteBuffer.wrap(b);
        }
    }

    /**
     * Get the Charset from the Content-encoding header. Defaults to
     * UTF_8
     */
    public static Charset charsetFrom(HttpHeaders headers) {
        String type = headers.firstValue("Content-type")
                .orElse("text/html; charset=utf-8");
        int i = type.indexOf(";");
        if (i >= 0) type = type.substring(i+1);
        try {
            HeaderParser parser = new HeaderParser(type);
            String value = parser.findValue("charset");
            if (value == null) return StandardCharsets.UTF_8;
            return Charset.forName(value);
        } catch (Throwable x) {
            if (Log.trace()) {
                Log.logTrace("Can't find charset in \"{0}\" ({1})", type, x);
            }
            return StandardCharsets.UTF_8;
        }
    }

    public static UncheckedIOException unchecked(IOException e) {
        return new UncheckedIOException(e);
    }

    /**
     * Get a logger for debug HTTP traces.
     * <p>
     * The logger should only be used with levels whose severity is
     * {@code <= DEBUG}.
     * <p>
     * The output of this logger is controlled by the system property
     * -Djdk.internal.httpclient.debug. The value of the property is
     * a comma separated list of tokens. The following tokens are
     * recognized:
     * <ul>
     *   <li> err: the messages will be logged on System.err</li>
     *   <li> out: the messages will be logged on System.out</li>
     *   <li> log: the messages will be forwarded to an internal
     *        System.Logger named "jdk.internal.httpclient.debug"</li>
     *   <li> true: this is equivalent to "err,log":  the messages will be logged
     *        both on System.err, and forwarded to the internal logger.</li>
     * </ul>
     *
     * This logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     *
     * @return A logger for HTTP internal debug traces
     */
    public static Logger getDebugLogger(Supplier<String> dbgTag) {
        return DebugLogger.createHttpLogger(dbgTag, DEBUG_CONFIG);
    }


    /**
     * Get a logger for debug HTTP traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * If {@code on} is false, returns a logger that doesn't log anything.
     * Otherwise, returns a logger equivalent to {@link #getDebugLogger(Supplier)}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     * @param on  Whether the logger is enabled.
     *
     * @return A logger for HTTP internal debug traces
     */
    public static Logger getDebugLogger(Supplier<String> dbgTag, boolean on) {
        LoggerConfig config = on ? DEBUG_CONFIG : LoggerConfig.OFF;
        return DebugLogger.createHttpLogger(dbgTag, config);
    }

    /**
     * Get a logger for debug HPACK traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger has a configuration equivalent to that
     * returned by {@link #getHpackLogger(Supplier)}. This original
     * configuration is amended by the provided {@code errLevel} in
     * the following way: if the message severity level is >= to
     * the provided {@code errLevel} the message will additionally
     * be printed on stderr.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "Http2Connection(SocketTube(3))/hpack.Decoder(3)")
     * @param errLevel The level above which messages will be also printed on
     *               stderr (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HPACK internal debug traces
     */
    public static Logger getHpackLogger(Supplier<String> dbgTag, Level errLevel) {
        return DebugLogger.createHpackLogger(dbgTag, DEBUG_HPACK_CONFIG.withErrLevel(errLevel));
    }

    /**
     * Get a logger for debug HPACK traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * The logger should only be used with levels whose severity is
     * {@code <= DEBUG}.
     * <p>
     * The output of this logger is controlled by the system property
     * -Djdk.internal.httpclient.hpack.debug. The value of the property is
     * a comma separated list of tokens. The following tokens are
     * recognized:
     * <ul>
     *   <li> err: the messages will be logged on System.err</li>
     *   <li> out: the messages will be logged on System.out</li>
     *   <li> log: the messages will be forwarded to an internal
     *        System.Logger named "jdk.internal.httpclient.hpack.debug"</li>
     *   <li> true: this is equivalent to "err,log":  the messages will be logged
     *        both on System.err, and forwarded to the internal logger.</li>
     * </ul>
     *
     * This logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "Http2Connection(SocketTube(3))/hpack.Decoder(3)")
     *
     * @return A logger for HPACK internal debug traces
     */
    public static Logger getHpackLogger(Supplier<String> dbgTag) {
        return DebugLogger.createHpackLogger(dbgTag, DEBUG_HPACK_CONFIG);
    }

    /**
     * Get a logger for debug WebSocket traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     * <p>
     * The logger should only be used with levels whose severity is
     * {@code <= DEBUG}.
     * <p>
     * The output of this logger is controlled by the system property
     * -Djdk.internal.httpclient.websocket.debug. The value of the property is
     * a comma separated list of tokens. The following tokens are
     * recognized:
     * <ul>
     *   <li> err: the messages will be logged on System.err</li>
     *   <li> out: the messages will be logged on System.out</li>
     *   <li> log: the messages will be forwarded to an internal
     *        System.Logger named "jdk.internal.httpclient.websocket.debug"</li>
     *   <li> true: this is equivalent to "err,log":  the messages will be logged
     *        both on System.err, and forwarded to the internal logger.</li>
     * </ul>
     *
     * This logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "WebSocket(3)")
     *
     * @return A logger for WebSocket internal debug traces
     */
    public static Logger getWebSocketLogger(Supplier<String> dbgTag) {
        return DebugLogger.createWebSocketLogger(dbgTag, DEBUG_WS_CONFIG);
    }

    /**
     * SSLSessions returned to user are wrapped in an immutable object
     */
    public static SSLSession immutableSession(SSLSession session) {
        if (session instanceof ExtendedSSLSession)
            return new ImmutableExtendedSSLSession((ExtendedSSLSession)session);
        else
            return new ImmutableSSLSession(session);
    }

    /**
     * Enabled by default. May be disabled for testing. Use with care
     */
    public static boolean isHostnameVerificationDisabled() {
        return isHostnameVerificationDisabled;
    }

    public static InetSocketAddress resolveAddress(InetSocketAddress address) {
        if (address != null && address.isUnresolved()) {
            // The default proxy selector may select a proxy whose  address is
            // unresolved. We must resolve the address before connecting to it.
            address = new InetSocketAddress(address.getHostString(), address.getPort());
        }
        return address;
    }

    public static Throwable toConnectException(Throwable e) {
        if (e == null) return null;
        e = getCompletionCause(e);
        if (e instanceof ConnectException) return e;
        if (e instanceof SecurityException) return e;
        if (e instanceof SSLException) return e;
        if (e instanceof Error) return e;
        if (e instanceof HttpTimeoutException) return e;
        Throwable cause = e;
        e = new ConnectException(e.getMessage());
        e.initCause(cause);
        return e;
    }

    /**
     * Returns the smallest (closest to zero) positive number {@code m} (which
     * is also a power of 2) such that {@code n <= m}.
     * <pre>{@code
     *          n  pow2Size(n)
     * -----------------------
     *          0           1
     *          1           1
     *          2           2
     *          3           4
     *          4           4
     *          5           8
     *          6           8
     *          7           8
     *          8           8
     *          9          16
     *         10          16
     *        ...         ...
     * 2147483647  1073741824
     * } </pre>
     *
     * The result is capped at {@code 1 << 30} as beyond that int wraps.
     *
     * @param n
     *         capacity
     *
     * @return the size of the array
     * @apiNote Used to size arrays in circular buffers (rings), usually in
     * order to squeeze extra performance substituting {@code %} operation for
     * {@code &}, which is up to 2 times faster.
     */
    public static int pow2Size(int n) {
        if (n < 0) {
            throw new IllegalArgumentException();
        } else if (n == 0) {
            return 1;
        } else if (n >= (1 << 30)) { // 2^31 is a negative int
            return 1 << 30;
        } else {
            return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
        }
    }

    /**
     * Creates HTTP/2 HTTP/3 pseudo headers for the given request.
     * @param request the request
     * @return pseudo headers for that request
     */
    public static HttpHeaders createPseudoHeaders(HttpRequest request) {
        HttpHeadersBuilder hdrs = new HttpHeadersBuilder();
        String method = request.method();
        hdrs.setHeader(":method", method);
        URI uri = request.uri();
        hdrs.setHeader(":scheme", uri.getScheme());
        String host = uri.getHost();
        int port = uri.getPort();
        assert host != null;
        if (port != -1) {
            hdrs.setHeader(":authority", host + ":" + port);
        } else {
            hdrs.setHeader(":authority", host);
        }
        String query = uri.getRawQuery();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            if (method.equalsIgnoreCase("OPTIONS")) {
                path = "*";
            } else {
                path = "/";
            }
        }
        if (query != null) {
            path += "?" + query;
        }
        hdrs.setHeader(":path", Utils.encode(path));
        return hdrs.build();
    }
    // -- toAsciiString-like support to encode path and query URI segments

    public static int readStatusCode(HttpHeaders headers, String errorPrefix) throws ProtocolException {
        var s = headers.firstValue(":status").orElse(null);
        if (s == null) {
            throw new ProtocolException(errorPrefix + "missing status code");
        }
        Throwable t = null;
        int i = 0;
        try {
            i = Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            t = nfe;
        }
        if (t != null || i < 100 || i > 999) {
            var pe = new ProtocolException(errorPrefix + "invalid status code: " + s);
            pe.initCause(t);
            throw pe;
        }
        return i;
    }

    public static long readContentLength(HttpHeaders headers, String errorPrefix, long defaultIfMissing) throws ProtocolException {
        var k = "Content-Length";
        var s = headers.firstValue(k).orElse(null);
        if (s == null) {
            return defaultIfMissing;
        }
        Throwable t = null;
        long i = 0;
        try {
            i = Long.parseLong(s);
        } catch (NumberFormatException nfe) {
            t = nfe;
        }
        if (t != null || i < 0) {
            var pe = new ProtocolException("%sinvalid \"%s\": %s".formatted(errorPrefix, k, s));
            pe.initCause(t);
            throw pe;
        }
        return i;
    }

    // Encodes all characters >= \u0080 into escaped, normalized UTF-8 octets,
    // assuming that s is otherwise legal
    //
    public static String encode(String s) {
        int n = s.length();
        if (n == 0)
            return s;

        // First check whether we actually need to encode
        for (int i = 0;;) {
            if (s.charAt(i) >= '\u0080')
                break;
            if (++i >= n)
                return s;
        }

        String ns = Normalizer.normalize(s, Normalizer.Form.NFC);
        ByteBuffer bb = null;
        try {
            bb = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(ns));
        } catch (CharacterCodingException x) {
            assert false : x;
        }

        HexFormat format = HexFormat.of().withUpperCase();
        StringBuilder sb = new StringBuilder();
        while (bb.hasRemaining()) {
            int b = bb.get() & 0xff;
            if (b >= 0x80) {
                sb.append('%');
                format.toHexDigits(sb, (byte)b);
            } else {
                sb.append((char) b);
            }
        }
        return sb.toString();
    }

    /**
     * {@return the content of the buffer as an hexadecimal string}
     * This method doesn't move the buffer position or limit.
     * @param buffer a byte buffer
     */
    public static String asHexString(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) return "";
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(buffer.position(), bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Converts a ByteBuffer containing bytes encoded using
     * the given {@linkplain Charset charset} into a
     * string. This method does not throw but will replace
     * unrecognized sequences with the replacement character.
     * The bytes in the buffer are consumed.
     *
     * @apiNote
     * This method is intended for debugging purposes only,
     * since buffers are not guaranteed to be split at character
     * boundaries.
     *
     * @param buffer a buffer containing bytes encoded using
     *               a charset
     * @param charset the charset to use to decode the bytes
     *                into a string
     *
     * @return a string built from the bytes contained
     * in the buffer decoded using the given charset
     */
    public static String asString(ByteBuffer buffer, Charset charset) {
        var decoded = charset.decode(buffer);
        char[] chars = new char[decoded.length()];
        decoded.get(chars);
        return new String(chars);
    }

    /**
     * Converts a ByteBuffer containing UTF-8 bytes into a
     * string. This method does not throw but will replace
     * unrecognized sequences with the replacement character.
     * The bytes in the buffer are consumed.
     *
     * @apiNote
     * This method is intended for debugging purposes only,
     * since buffers are not guaranteed to be split at character
     * boundaries.
     *
     * @param buffer a buffer containing UTF-8 bytes
     *
     * @return a string built from the decoded UTF-8 bytes contained
     * in the buffer
     */
    public static String asString(ByteBuffer buffer) {
       return asString(buffer, StandardCharsets.UTF_8);
    }

    public static String millis(Instant now, Instant deadline) {
        if (Instant.MAX.equals(deadline)) return "not scheduled";
        try {
            long delay = now.until(deadline, ChronoUnit.MILLIS);
            return delay + " ms";
        } catch (ArithmeticException a) {
            return "too far away";
        }
    }

    public static String millis(Deadline now, Deadline deadline) {
        return millis(now.asInstant(), deadline.asInstant());
    }

    public static ExecutorService safeExecutor(ExecutorService delegate,
                                 BiConsumer<Runnable, Throwable> errorHandler) {
        Executor overflow =  new CompletableFuture<Void>().defaultExecutor();
        return new SafeExecutorService(delegate, overflow, errorHandler);
    }

    public static sealed class SafeExecutor<E extends Executor> implements Executor
            permits SafeExecutorService {
        final E delegate;
        final BiConsumer<Runnable, Throwable> errorHandler;
        final Executor overflow;

        public SafeExecutor(E delegate, Executor overflow, BiConsumer<Runnable, Throwable> errorHandler) {
            this.delegate = delegate;
            this.overflow = overflow;
            this.errorHandler = errorHandler;
        }

        @Override
        public void execute(Runnable command) {
            ensureExecutedAsync(command);
        }

        private void ensureExecutedAsync(Runnable command) {
            try {
                delegate.execute(command);
            } catch (RejectedExecutionException t) {
                errorHandler.accept(command, t);
                overflow.execute(command);
            }
        }

    }

    public static final class SafeExecutorService extends SafeExecutor<ExecutorService>
            implements ExecutorService {

        public SafeExecutorService(ExecutorService delegate,
                            Executor overflow,
                            BiConsumer<Runnable, Throwable> errorHandler) {
            super(delegate, overflow, errorHandler);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(task);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(task, result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(task);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegate.invokeAll(tasks);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                             long timeout, TimeUnit unit)
                throws InterruptedException {
            return delegate.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                               long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(tasks);
        }
    }

    public static <T extends NetworkChannel> T configureChannelBuffers(Consumer<String> logSink, T chan,
                                                                int receiveBufSize, int sendBufSize) {

        if (logSink != null) {
            int bufsize = getSoReceiveBufferSize(logSink, chan);
            logSink.accept("Initial receive buffer size is: %d".formatted(bufsize));
            bufsize = getSoSendBufferSize(logSink, chan);
            logSink.accept("Initial send buffer size is: %d".formatted(bufsize));
        }
        if (trySetReceiveBufferSize(logSink, chan, receiveBufSize)) {
            if (logSink != null) {
                int bufsize = getSoReceiveBufferSize(logSink, chan);
                logSink.accept("Receive buffer size configured: %d".formatted(bufsize));
            }
        }
        if (trySetSendBufferSize(logSink, chan, sendBufSize)) {
            if (logSink != null) {
                int bufsize = getSoSendBufferSize(logSink, chan);
                logSink.accept("Send buffer size configured: %d".formatted(bufsize));
            }
        }
        return chan;
    }

    public static boolean trySetReceiveBufferSize(Consumer<String> logSink, NetworkChannel chan, int bufsize) {
        try {
            if (bufsize > 0) {
                chan.setOption(StandardSocketOptions.SO_RCVBUF, bufsize);
                return true;
            }
        } catch (IOException x) {
            if (logSink != null)
                logSink.accept("Failed to set receive buffer size to %d on %s"
                        .formatted(bufsize, chan));
        }
        return false;
    }

    public static boolean trySetSendBufferSize(Consumer<String> logSink, NetworkChannel chan, int bufsize) {
        try {
            if (bufsize > 0) {
                chan.setOption(StandardSocketOptions.SO_SNDBUF, bufsize);
                return true;
            }
        } catch (IOException x) {
            if (logSink != null)
                logSink.accept("Failed to set send buffer size to %d on %s"
                        .formatted(bufsize, chan));
        }
        return false;
    }

    public static int getSoReceiveBufferSize(Consumer<String> logSink, NetworkChannel chan) {
        try {
            return chan.getOption(StandardSocketOptions.SO_RCVBUF);
        } catch (IOException x) {
            if (logSink != null)
                logSink.accept("Failed to get initial receive buffer size on %s".formatted(chan));
        }
        return 0;
    }

    public static int getSoSendBufferSize(Consumer<String> logSink, NetworkChannel chan) {
        try {
            return chan.getOption(StandardSocketOptions.SO_SNDBUF);
        } catch (IOException x) {
            if (logSink!= null)
                logSink.accept("Failed to get initial receive buffer size on %s".formatted(chan));
        }
        return 0;
    }


    /**
     * Try to figure out whether local and remote addresses are compatible.
     * Used to diagnose potential communication issues early.
     * This is a best effort, and there is no guarantee that all potential
     * conflicts will be detected.
     * @param local local address
     * @param peer  peer address
     * @return a message describing the conflict, if any, or {@code null} if no
     *         conflict was detected.
     */
    public static String addressConflict(SocketAddress local, SocketAddress peer) {
        if (local == null || peer == null) return null;
        if (local.equals(peer)) {
            return "local endpoint and remote endpoint are bound to the same IP address and port";
        }
        if (!(local instanceof InetSocketAddress li) || !(peer instanceof InetSocketAddress pi)) {
            return null;
        }
        var laddr = li.getAddress();
        var paddr = pi.getAddress();
        if (!laddr.isAnyLocalAddress() && !paddr.isAnyLocalAddress()) {
            if (laddr.getClass() != paddr.getClass()) { // IPv4 vs IPv6
                if ((laddr instanceof Inet6Address laddr6 && !laddr6.isIPv4CompatibleAddress())
                    || (paddr instanceof Inet6Address paddr6 && !paddr6.isIPv4CompatibleAddress())) {
                    return "local endpoint IP (%s) and remote endpoint IP (%s) don't match"
                            .formatted(laddr.getClass().getSimpleName(),
                                    paddr.getClass().getSimpleName());
                }
            }
        }
        if (li.getPort() != pi.getPort()) return null;
        if (li.getAddress().isAnyLocalAddress() && pi.getAddress().isLoopbackAddress()) {
            return "local endpoint (wildcard) and remote endpoint (loopback) ports conflict";
        }
        if (pi.getAddress().isAnyLocalAddress() && li.getAddress().isLoopbackAddress()) {
            return "local endpoint (loopback) and remote endpoint (wildcard) ports conflict";
        }
        return null;
    }

    /**
     * {@return the exception the given {@code cf} was completed with,
     * or a {@link CancellationException} if the given {@code cf} was
     * cancelled}
     *
     * @param cf a {@code CompletableFuture} exceptionally completed
     * @throws IllegalArgumentException if the given cf was not
     *    {@linkplain CompletableFuture#isCompletedExceptionally()
     *    completed exceptionally}
     */
    public static Throwable exceptionNow(CompletableFuture<?> cf) {
        if (cf.isCompletedExceptionally()) {
            if (cf.isCancelled()) {
                try {
                    cf.join();
                } catch (CancellationException x) {
                    return x;
                } catch (CompletionException x) {
                    return x.getCause();
                }
            } else {
                return cf.exceptionNow();
            }
        }
        throw new IllegalArgumentException("cf is not completed exceptionally");
    }
}

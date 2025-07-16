/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.net.http.HttpHeaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jdk.internal.net.http.frame.DataFrame;
import jdk.internal.net.http.frame.Http2Frame;
import jdk.internal.net.http.frame.WindowUpdateFrame;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.HandshakeDoneFrame;
import jdk.internal.net.http.quic.frames.PaddingFrame;
import jdk.internal.net.http.quic.frames.PingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;
import jdk.internal.net.http.quic.packets.PacketSpace;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;

/**
 * -Djdk.httpclient.HttpClient.log=
 *          errors,requests,headers,
 *          frames[:control:data:window:all..],content,ssl,trace,channel,
 *          quic[:control:processed:retransmit:ack:crypto:data:cc:hs:dbb:ping:all]
 *
 * Any of errors, requests, headers or content are optional.
 *
 * Other handlers may be added. All logging is at level INFO
 *
 * Logger name is "jdk.httpclient.HttpClient"
 */
// implements System.Logger in order to be skipped when printing the caller's
// information
public abstract class Log implements System.Logger {

    static final String logProp = "jdk.httpclient.HttpClient.log";

    public static final int OFF      = 0x00;
    public static final int ERRORS   = 0x01;
    public static final int REQUESTS = 0x02;
    public static final int HEADERS  = 0x04;
    public static final int CONTENT  = 0x08;
    public static final int FRAMES   = 0x10;
    public static final int SSL      = 0x20;
    public static final int TRACE    = 0x40;
    public static final int CHANNEL  = 0x80;
    public static final int QUIC     = 0x0100;
    public static final int HTTP3    = 0x0200;
    static int logging;

    // Frame types: "control", "data", "window", "all"
    public static final int CONTROL = 1; // all except DATA and WINDOW_UPDATES
    public static final int DATA = 2;
    public static final int WINDOW_UPDATES = 4;
    public static final int ALL = CONTROL| DATA | WINDOW_UPDATES;
    static int frametypes;

    // Quic message types
    public static final int QUIC_CONTROL    = 1;
    public static final int QUIC_PROCESSED  = 2;
    public static final int QUIC_RETRANSMIT = 4;
    public static final int QUIC_DATA       = 8;
    public static final int QUIC_CRYPTO     = 16;
    public static final int QUIC_ACK        = 32;
    public static final int QUIC_PING       = 64;
    public static final int QUIC_CC         = 128;
    public static final int QUIC_TIMER      = 256;
    public static final int QUIC_DIRECT_BUFFER_POOL = 512;
    public static final int QUIC_HANDSHAKE = 1024;
    public static final int QUIC_ALL = QUIC_CONTROL
            | QUIC_PROCESSED | QUIC_RETRANSMIT
            | QUIC_DATA | QUIC_CRYPTO
            | QUIC_ACK | QUIC_PING | QUIC_CC
            | QUIC_TIMER | QUIC_DIRECT_BUFFER_POOL
            | QUIC_HANDSHAKE;
    static int quictypes;


    static final System.Logger logger;

    static {
        String s = Utils.getNetProperty(logProp);
        if (s == null) {
            logging = OFF;
        } else {
            String[] vals = s.split(",");
            for (String val : vals) {
                switch (val.toLowerCase(Locale.US)) {
                    case "errors":
                        logging |= ERRORS;
                        break;
                    case "requests":
                        logging |= REQUESTS;
                        break;
                    case "headers":
                        logging |= HEADERS;
                        break;
                    case "quic":
                        logging |= QUIC;
                        break;
                    case "http3":
                        logging |= HTTP3;
                        break;
                    case "content":
                        logging |= CONTENT;
                        break;
                    case "ssl":
                        logging |= SSL;
                        break;
                    case "channel":
                        logging |= CHANNEL;
                        break;
                    case "trace":
                        logging |= TRACE;
                        break;
                    case "all":
                        logging |= CONTENT | HEADERS | REQUESTS | FRAMES | ERRORS | TRACE | SSL | CHANNEL | QUIC | HTTP3;
                        frametypes |= ALL;
                        quictypes |= QUIC_ALL;
                        break;
                    default:
                        // ignore bad values
                }
                if (val.startsWith("frames:") || val.equals("frames")) {
                    logging |= FRAMES;
                    String[] types = val.split(":");
                    if (types.length == 1) {
                        frametypes = CONTROL | DATA | WINDOW_UPDATES;
                    } else {
                        for (String type : types) {
                            switch (type.toLowerCase(Locale.US)) {
                                case "control":
                                    frametypes |= CONTROL;
                                    break;
                                case "data":
                                    frametypes |= DATA;
                                    break;
                                case "window":
                                    frametypes |= WINDOW_UPDATES;
                                    break;
                                case "all":
                                    frametypes = ALL;
                                    break;
                                default:
                                    // ignore bad values
                            }
                        }
                    }
                }
                if (val.startsWith("quic:") || val.equals("quic")) {
                    logging |= QUIC;
                    String[] types = val.split(":");
                    if (types.length == 1) {
                        quictypes = QUIC_ALL & ~QUIC_TIMER & ~QUIC_DIRECT_BUFFER_POOL;
                    } else {
                        for (String type : types) {
                            switch (type.toLowerCase(Locale.US)) {
                                case "control":
                                    quictypes |= QUIC_CONTROL;
                                    break;
                                case "data":
                                    quictypes |= QUIC_DATA;
                                    break;
                                case "processed":
                                    quictypes |= QUIC_PROCESSED;
                                    break;
                                case "retransmit":
                                    quictypes |= QUIC_RETRANSMIT;
                                    break;
                                case "crypto":
                                    quictypes |= QUIC_CRYPTO;
                                    break;
                                case "cc":
                                    quictypes |= QUIC_CC;
                                    break;
                                case "hs":
                                    quictypes |= QUIC_HANDSHAKE;
                                    break;
                                case "ack":
                                    quictypes |= QUIC_ACK;
                                    break;
                                case "ping":
                                    quictypes |= QUIC_PING;
                                    break;
                                case "timer":
                                    quictypes |= QUIC_TIMER;
                                    break;
                                case "dbb":
                                    quictypes |= QUIC_DIRECT_BUFFER_POOL;
                                    break;
                                case "all":
                                    quictypes = QUIC_ALL;
                                    break;
                                default:
                                    // ignore bad values
                            }
                        }
                    }
                }
            }
        }
        if (logging != OFF) {
            logger = System.getLogger("jdk.httpclient.HttpClient");
        } else {
            logger = null;
        }
    }
    public static boolean errors() {
        return (logging & ERRORS) != 0;
    }

    public static boolean requests() {
        return (logging & REQUESTS) != 0;
    }

    public static boolean headers() {
        return (logging & HEADERS) != 0;
    }

    public static boolean trace() {
        return (logging & TRACE) != 0;
    }

    public static boolean ssl() {
        return (logging & SSL) != 0;
    }

    public static boolean frames() {
        return (logging & FRAMES) != 0;
    }

    public static boolean channel() {
        return (logging & CHANNEL) != 0;
    }

    public static boolean altsvc() { return headers(); }

    public static boolean quicRetransmit() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_RETRANSMIT) != 0;
    }

    // not called directly - but impacts isLogging(QuicFrame)
    public static boolean quicHandshake() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_HANDSHAKE) != 0;
    }

    public static boolean quicProcessed() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_PROCESSED) != 0;
    }

    // not called directly - but impacts isLogging(QuicFrame)
    public static boolean quicData() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_DATA) != 0;
    }

    public static boolean quicCrypto() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_CRYPTO) != 0;
    }

    public static boolean quicCC() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_CC) != 0;
    }

    public static boolean quicControl() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_CONTROL) != 0;
    }

    public static boolean quicTimer() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_TIMER) != 0;
    }
    public static boolean quicDBB() {
        return (logging & QUIC) != 0 && (quictypes & QUIC_DIRECT_BUFFER_POOL) != 0;
    }

    public static boolean quic() {
        return (logging & QUIC) != 0;
    }

    public static boolean http3() {
        return (logging & HTTP3) != 0;
    }

    public static void logHttp3(String s, Object... s1) {
        if (http3()) {
            logger.log(Level.INFO, "HTTP3: " + s, s1);
        }
    }

    private static boolean isLogging(QuicFrame frame) {
        if (frame instanceof StreamFrame sf)
            return (quictypes & QUIC_DATA) != 0
                    || (quictypes & QUIC_CONTROL) != 0 && sf.isLast()
                    || (quictypes & QUIC_CONTROL) != 0 && sf.offset() == 0;
        if (frame instanceof AckFrame)
            return (quictypes & QUIC_ACK) != 0;
        if (frame instanceof CryptoFrame)
            return (quictypes & QUIC_CRYPTO) != 0
                    || (quictypes & QUIC_HANDSHAKE) != 0;
        if (frame instanceof PingFrame)
            return (quictypes & QUIC_PING) != 0;
        if (frame instanceof PaddingFrame) return false;
        if (frame instanceof HandshakeDoneFrame && quicHandshake())
            return true;
        return (quictypes & QUIC_CONTROL) != 0;
    }

    private static final EnumSet<PacketType> HS_TYPES = EnumSet.complementOf(
            EnumSet.of(PacketType.ONERTT));

    private static boolean quicPacketLoggable(QuicPacket packet) {
        return (logging & QUIC) != 0
                && (quictypes == QUIC_ALL
                || quicHandshake() && HS_TYPES.contains(packet.packetType())
                || stream(packet.frames()).anyMatch(Log::isLogging));
    }

    public static boolean quicPacketOutLoggable(QuicPacket packet) {
        return quicPacketLoggable(packet);
    }

    private static <T> Stream<T> stream(Collection<T> list) {
        return list == null ? Stream.empty() : list.stream();
    }

    public static boolean quicPacketInLoggable(QuicPacket packet) {
        return quicPacketLoggable(packet);
    }

    public static void logQuic(String s, Object... s1) {
        if (quic()) {
            logger.log(Level.INFO, "QUIC: " + s, s1);
        }
    }

    public static void logQuicPacketOut(String connectionTag, QuicPacket packet) {
        if (quicPacketOutLoggable(packet)) {
            logger.log(Level.INFO, "QUIC: {0} OUT: {1}",
                    connectionTag, packet.prettyPrint());
        }
    }

    public static void logQuicPacketIn(String connectionTag, QuicPacket packet) {
        if (quicPacketInLoggable(packet)) {
            logger.log(Level.INFO, "QUIC: {0} IN: {1}",
                    connectionTag, packet.prettyPrint());
        }
    }

    public static void logError(String s, Object... s1) {
        if (errors()) {
            logger.log(Level.INFO, "ERROR: " + s, s1);
        }
    }

    public static void logError(Throwable t) {
        if (errors()) {
            String s = Utils.stackTrace(t);
            logger.log(Level.INFO, "ERROR: " + s);
        }
    }

    public static void logSSL(String s, Object... s1) {
        if (ssl()) {
            logger.log(Level.INFO, "SSL: " + s, s1);
        }
    }

    public static void logSSL(Supplier<String> msgSupplier) {
        if (ssl()) {
            logger.log(Level.INFO, "SSL: " + msgSupplier.get());
        }
    }

    public static void logChannel(String s, Object... s1) {
        if (channel()) {
            logger.log(Level.INFO, "CHANNEL: " + s, s1);
        }
    }

    public static void logChannel(Supplier<String> msgSupplier) {
        if (channel()) {
            logger.log(Level.INFO, "CHANNEL: " + msgSupplier.get());
        }
    }

    public static void logTrace(String s, Object... s1) {
        if (trace()) {
            String format = "MISC: " + s;
            logger.log(Level.INFO, format, s1);
        }
    }

    public static void logRequest(String s, Object... s1) {
        if (requests()) {
            logger.log(Level.INFO, "REQUEST: " + s, s1);
        }
    }

    public static void logResponse(Supplier<String> supplier) {
        if (requests()) {
            logger.log(Level.INFO, "RESPONSE: " + supplier.get());
        }
    }

    public static void logHeaders(String s, Object... s1) {
        if (headers()) {
            logger.log(Level.INFO, "HEADERS: " + s, s1);
        }
    }

    public static void logAltSvc(String s, Object... s1) {
        if (altsvc()) {
            logger.log(Level.INFO, "ALTSVC: " + s, s1);
        }
    }

    public static boolean loggingFrame(Class<? extends Http2Frame> clazz) {
        if (frametypes == ALL) {
            return true;
        }
        if (clazz == DataFrame.class) {
            return (frametypes & DATA) != 0;
        } else if (clazz == WindowUpdateFrame.class) {
            return (frametypes & WINDOW_UPDATES) != 0;
        } else {
            return (frametypes & CONTROL) != 0;
        }
    }

    public static void logFrames(Http2Frame f, String direction) {
        if (frames() && loggingFrame(f.getClass())) {
            logger.log(Level.INFO, "FRAME: " + direction + ": " + f.toString());
        }
    }

    public static void logParams(SSLParameters p) {
        if (!Log.ssl()) {
            return;
        }

        if (p == null) {
            Log.logSSL("SSLParameters: Null params");
            return;
        }

        final StringBuilder sb = new StringBuilder("SSLParameters:");
        final List<Object> params = new ArrayList<>();
        if (p.getCipherSuites() != null) {
            for (String cipher : p.getCipherSuites()) {
                sb.append("\n    cipher: {")
                        .append(params.size()).append("}");
                params.add(cipher);
            }
        }

        // SSLParameters.getApplicationProtocols() can't return null
        // JDK 8 EXCL START
        for (String approto : p.getApplicationProtocols()) {
            sb.append("\n    application protocol: {")
                    .append(params.size()).append("}");
            params.add(approto);
        }
        // JDK 8 EXCL END

        if (p.getProtocols() != null) {
            for (String protocol : p.getProtocols()) {
                sb.append("\n    protocol: {")
                        .append(params.size()).append("}");
                params.add(protocol);
            }
        }

        if (p.getEndpointIdentificationAlgorithm() != null) {
            sb.append("\n    endpointIdAlg: {")
                .append(params.size()).append("}");
            params.add(p.getEndpointIdentificationAlgorithm());
        }

        if (p.getServerNames() != null) {
            for (SNIServerName sname : p.getServerNames()) {
                sb.append("\n    server name: {")
                        .append(params.size()).append("}");
                params.add(sname.toString());
            }
        }
        sb.append('\n');

        Log.logSSL(sb.toString(), params.toArray());
    }

    public static void dumpHeaders(StringBuilder sb, String prefix, HttpHeaders headers) {
        if (headers != null) {
            Map<String,List<String>> h = headers.map();
            Set<Map.Entry<String,List<String>>> entries = h.entrySet();
            String sep = "";
            for (Map.Entry<String,List<String>> entry : entries) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    // should not happen
                    sb.append(sep);
                    sb.append(prefix).append(key).append(':');
                    sep = "\n";
                    continue;
                }
                for (String value : values) {
                    sb.append(sep);
                    sb.append(prefix).append(key).append(':');
                    sb.append(' ').append(value);
                    sep = "\n";
                }
            }
            sb.append('\n');
        }
    }


    // not instantiable
    private Log() {}
}

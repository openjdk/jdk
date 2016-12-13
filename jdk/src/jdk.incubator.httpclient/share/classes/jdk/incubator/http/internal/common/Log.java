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

import java.io.IOException;
import jdk.incubator.http.HttpHeaders;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import jdk.incubator.http.internal.frame.DataFrame;
import jdk.incubator.http.internal.frame.Http2Frame;
import jdk.incubator.http.internal.frame.WindowUpdateFrame;

/**
 * -Djava.net.HttpClient.log=
 *          errors,requests,headers,
 *          frames[:control:data:window:all..],content,ssl,trace
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

    public static final int OFF = 0;
    public static final int ERRORS = 0x1;
    public static final int REQUESTS = 0x2;
    public static final int HEADERS = 0x4;
    public static final int CONTENT = 0x8;
    public static final int FRAMES = 0x10;
    public static final int SSL = 0x20;
    public static final int TRACE = 0x40;
    static int logging;

    // Frame types: "control", "data", "window", "all"
    public static final int CONTROL = 1; // all except DATA and WINDOW_UPDATES
    public static final int DATA = 2;
    public static final int WINDOW_UPDATES = 4;
    public static final int ALL = CONTROL| DATA | WINDOW_UPDATES;
    static int frametypes;

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
                    case "content":
                        logging |= CONTENT;
                        break;
                    case "ssl":
                        logging |= SSL;
                        break;
                    case "trace":
                        logging |= TRACE;
                        break;
                    case "all":
                        logging |= CONTENT|HEADERS|REQUESTS|FRAMES|ERRORS|TRACE|SSL;
                        break;
                }
                if (val.startsWith("frames")) {
                    logging |= FRAMES;
                    String[] types = val.split(":");
                    if (types.length == 1) {
                        frametypes = CONTROL | DATA | WINDOW_UPDATES;
                    } else {
                        for (String type : types) {
                            switch (type.toLowerCase()) {
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

    public static void logTrace(String s, Object... s1) {
        if (trace()) {
            String format = "TRACE: " + s;
            logger.log(Level.INFO, format, s1);
        }
    }

    public static void logRequest(String s, Object... s1) {
        if (requests()) {
            logger.log(Level.INFO, "REQUEST: " + s, s1);
        }
    }

    public static void logResponse(String s, Object... s1) {
        if (requests()) {
            logger.log(Level.INFO, "RESPONSE: " + s, s1);
        }
    }

    public static void logHeaders(String s, Object... s1) {
        if (headers()) {
            logger.log(Level.INFO, "HEADERS: " + s, s1);
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

    public static void dumpHeaders(StringBuilder sb, String prefix, HttpHeaders headers) {
        if (headers != null) {
            Map<String,List<String>> h = headers.map();
            Set<Map.Entry<String,List<String>>> entries = h.entrySet();
            for (Map.Entry<String,List<String>> entry : entries) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                sb.append(prefix).append(key).append(":");
                for (String value : values) {
                    sb.append(' ').append(value);
                }
                sb.append('\n');
            }
        }
    }


    // not instantiable
    private Log() {}
}

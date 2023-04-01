/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javacserver.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Utility class only for javacserver logging.
 *
 * Logging in javacserver has special requirements when running in server/client
 * mode. Most of the log messages is generated server-side, but the server
 * is typically spawned by the client in the background, so the user usually
 * does not see the server stdout/stderr. For this reason log messages needs
 * to relayed back to the client that performed the request that generated the
 * log message. To support this use case this class maintains a per-thread log
 * instance so that each connected client can have its own instance that
 * relays messages back to the requesting client.
 *
 * On the client-side there will typically just be one Log instance.
 */
public class Log {
    public enum Level {
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE;
    }

    private static Log stdOutErr = new Log(new PrintWriter(System.out), new PrintWriter(System.err));
    private static ThreadLocal<Log> logger = new ThreadLocal<>();

    protected PrintWriter err; // Used for error and warning messages
    protected PrintWriter out; // Used for other messages
    protected Level level = Level.INFO;

    public Log(Writer out, Writer err) {
        this.out = out == null ? null : new PrintWriter(out, true);
        this.err = err == null ? null : new PrintWriter(err, true);
    }

    public static void setLogForCurrentThread(Log log) {
        logger.set(log);
    }

    public static void setLogLevel(Level l) {
        get().level = l;
    }

    public static void debug(String msg) {
        log(Level.DEBUG, msg);
    }

    public static void debug(Throwable t) {
        log(Level.DEBUG, t);
    }

    public static void error(String msg) {
        log(Level.ERROR, msg);
    }

    public static void error(Throwable t) {
        log(Level.ERROR, t);
    }

    public static void log(Level l, String msg) {
        get().printLogMsg(l, msg);
    }

    public static void log(Level l, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw, true));
        log(l, sw.toString());
    }

    public static boolean isDebugging() {
        return get().isLevelLogged(Level.DEBUG);
    }

    protected boolean isLevelLogged(Level l) {
        return l.ordinal() <= level.ordinal();
    }

    public static Log get() {
        Log log = logger.get();
        return log != null ? log : stdOutErr;
    }

    protected void printLogMsg(Level msgLevel, String msg) {
        if (isLevelLogged(msgLevel)) {
            PrintWriter pw = msgLevel.ordinal() <= Level.WARN.ordinal() ? err : out;
            pw.println(msg);
        }
    }
}

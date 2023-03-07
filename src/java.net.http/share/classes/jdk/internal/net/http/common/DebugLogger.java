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

import java.io.PrintStream;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * A {@code System.Logger} that forwards all messages to an underlying
 * {@code System.Logger}, after adding some decoration. The logger also has the
 * ability to additionally send the logged messages to System.err or System.out,
 * whether the underlying logger is activated or not. In addition instance of
 * {@code DebugLogger} support both {@link String#format(String, Object...)} and
 * {@link java.text.MessageFormat#format(String, Object...)} formatting.
 * String-like formatting is enabled by the presence of "%s" or "%d" in the
 * format string. MessageFormat-like formatting is enabled by the presence of
 * "{0" or "{1".
 *
 * <p> See {@link Utils#getDebugLogger(Supplier)} and
 * {@link Utils#getHpackLogger(Supplier)}.
 */
final class DebugLogger implements Logger {

    /**
     * A DebugLogger configuration is composed of three levels.
     * The three levels can be configured independently, but are
     * typically either Level.ALL or Level.OFF.
     *
     * @param outLevel the level above which messages will be directly
     *                printed to {@link System#out}
     * @param errLevel the level above which messages will be directly
     *                printed to {@link System#err}
     * @param logLevel the level above which messages will be forwarded
     *               to an underlying {@link System.Logger}
     */
    public record LoggerConfig(Level outLevel, Level errLevel, Level logLevel) {
        public LoggerConfig {
            Objects.requireNonNull(outLevel);
            Objects.requireNonNull(errLevel);
            Objects.requireNonNull(logLevel);
        }

        // true if at least on of the three levels is not Level.OFF
        public boolean on() {
            return minSeverity() <= Level.OFF.getSeverity();
        }
        // The minimal severity of the three level. Messages below
        // that severity will not be logged anywhere.
        public int minSeverity() {
            return Math.min(outLevel.getSeverity(),
                    Math.min(errLevel.getSeverity(), logLevel.getSeverity()));
        }
        // Whether the given level can be logged with the given logger
        public boolean levelEnabledFor(Level level, System.Logger logger) {
            if (level == Level.OFF) return false;
            int severity = level.getSeverity();
            if (severity >= errLevel.getSeverity()) return true;
            if (severity >= outLevel.getSeverity()) return true;
            if (severity >= logLevel.getSeverity()) return logger.isLoggable(level);
            return false;
        }
        // The same configuration, but with the given {@link #errLevel}
        public LoggerConfig withErrLevel(Level errLevel) {
            return new LoggerConfig(outLevel, errLevel, logLevel);
        }
        // The same configuration, but with the given {@link #outLevel}
        public LoggerConfig withOutLevel(Level outLevel) {
            return new LoggerConfig(outLevel, errLevel, logLevel);
        }
        // The same configuration, but with the given {@link #logLevel}
        public LoggerConfig withLogLevel(Level logLevel) {
            return new LoggerConfig(outLevel, errLevel, logLevel);
        }

        /** Logs on {@link System#err} only, does not forward to System.Logger **/
        public static final LoggerConfig STDERR = new LoggerConfig(Level.OFF, Level.ALL, Level.OFF);
        /** Logs on {@link System#out} only, does not forward to System.Logger **/
        public static final LoggerConfig STDOUT = new LoggerConfig(Level.OFF, Level.ALL, Level.OFF);
        /** Forward to System.Logger, doesn't log directly to System.out or System.err **/
        public static final LoggerConfig LOG = new LoggerConfig(Level.OFF, Level.OFF, Level.ALL);
        /** does not log anywhere **/
        public static final LoggerConfig OFF = new LoggerConfig(Level.OFF, Level.OFF, Level.OFF);
    };

    // deliberately not in the same subtree than standard loggers.
    static final String HTTP_NAME  = "jdk.internal.httpclient.debug";
    static final String WS_NAME  = "jdk.internal.httpclient.websocket.debug";
    static final String HPACK_NAME = "jdk.internal.httpclient.hpack.debug";
    static final System.Logger HTTP = System.getLogger(HTTP_NAME);
    static final System.Logger WS = System.getLogger(WS_NAME);
    static final System.Logger HPACK = System.getLogger(HPACK_NAME);
    private static final DebugLogger NO_HTTP_LOGGER =
            new DebugLogger(HTTP, "HTTP"::toString, LoggerConfig.OFF);
    private static final DebugLogger NO_WS_LOGGER =
            new DebugLogger(HTTP, "WS"::toString, LoggerConfig.OFF);
    private static final DebugLogger NO_HPACK_LOGGER =
            new DebugLogger(HTTP, "HPACK"::toString, LoggerConfig.OFF);
    static final long START_NANOS = System.nanoTime();

    private final Supplier<String> dbgTag;
    private final LoggerConfig config;
    private final int minSeverity;
    private final System.Logger logger;
    private final boolean debugOn;
    private final boolean traceOn;

    /**
     * Create a logger for debug traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     * <p>
     * By default, this logger will print message whose severity is
     * above the severity configured in the logger {@code config}
     * <ul>
     *     <li>If {@code config.outLevel()} is not Level.OFF, messages
     *     whose severity are at or above that severity will be directly
     *     printed on System.out</li>
     *     <li>If {@code config.errLevel()} is not Level.OFF, messages
     *     whose severity are at or above that severity will be directly
     *     printed on System.err</li>
     *     <li>If {@code config.logLevel()} is not Level.OFF, messages
     *     whose severity are at or above that severity will be forwarded
     *     to the supplied {@code logger}.</li>
     * </ul>
     * <p>
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stderr in
     *          addition to forwarding to the internal logger, use
     *          {@code new DebugLogger(logger, this::dbgTag,
     *                                 LoggerConfig.LOG.withErrLevel(Level.ALL));}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code new DebugLogger(logger, this::dbgTag, LoggerConfig.LOG);}.
     *
     * @param logger The internal logger to which messages will be forwarded.
     *               This should be either {@link #WS}, {@link #HPACK}, or {@link #HTTP};
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     * @param config The levels above which messages will be printed to the
     *               corresponding destination.
     *
     * @return A logger for HTTP internal debug traces
     */
    private DebugLogger(System.Logger logger,
                Supplier<String> dbgTag,
                LoggerConfig config) {
        this.dbgTag = dbgTag;
        this.config = Objects.requireNonNull(config);
        this.logger = Objects.requireNonNull(logger);
        this.minSeverity = config.minSeverity();
        // support only static configuration.
        this.debugOn = isEnabled(Level.DEBUG);
        this.traceOn = isEnabled(Level.TRACE);
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    private boolean isEnabled(Level level) {
        int severity = level.getSeverity();
        if (severity < minSeverity) return false;
        return levelEnabledFor(level, config, logger);
    }

    @Override
    public final boolean on() {
        return debugOn;
    }

    static boolean levelEnabledFor(Level level, LoggerConfig config,
                            System.Logger logger) {
        return config.levelEnabledFor(level, logger);
    }

    @Override
    public boolean isLoggable(Level level) {
        // fast path, we assume these guys never change.
        // support only static configuration.
        if (level == Level.DEBUG) return debugOn;
        if (level == Level.TRACE) return traceOn;
        return isEnabled(level);
    }

    @Override
    public void log(Level level, ResourceBundle unused,
                    String format, Object... params) {
        // fast path, we assume these guys never change.
        // support only static configuration.
        if (level == Level.DEBUG && !debugOn) return;
        if (level == Level.TRACE && !traceOn) return;
        int severity = level.getSeverity();
        if (severity < minSeverity) return;

        var errLevel = config.errLevel();
        if (errLevel != Level.OFF
                && errLevel.getSeverity() <= severity) {
            print(System.err, level, format, params, null);
        }
        var outLevel = config.outLevel();
        if (outLevel != Level.OFF
                && outLevel.getSeverity() <= severity) {
            print(System.out, level, format, params, null);
        }
        var logLevel = config.logLevel();
        if (logLevel != Level.OFF
                && logLevel.getSeverity() <= severity
                && logger.isLoggable(level)) {
            logger.log(level, unused,
                    getFormat(new StringBuilder(), format, params).toString(),
                    params);
        }
    }

    @Override
    public void log(Level level, ResourceBundle unused, String msg,
                    Throwable thrown) {
        // fast path, we assume these guys never change.
        if (level == Level.DEBUG && !debugOn) return;
        if (level == Level.TRACE && !traceOn) return;
        int severity = level.getSeverity();
        if (severity < minSeverity) return;

        var errLevel = config.errLevel();
        if (errLevel != Level.OFF
                && errLevel.getSeverity() <= severity) {
            print(System.err, level, msg, null, thrown);
        }
        var outLevel = config.outLevel();
        if (outLevel != Level.OFF
                && outLevel.getSeverity() <= severity) {
            print(System.out, level, msg, null, thrown);
        }
        var logLevel = config.logLevel();
        if (logLevel != Level.OFF
                && logLevel.getSeverity() <= severity
                && logger.isLoggable(level)) {
            logger.log(level, unused,
                    getFormat(new StringBuilder(), msg, null).toString(),
                    thrown);
        }
    }

    private void print(PrintStream out, Level level, String msg,
                       Object[] params, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(level.name()).append(':').append(' ');
        sb = format(sb, msg, params);
        if (t != null) sb.append(' ').append(t.toString());
        out.println(sb.toString());
        if (t != null) {
            t.printStackTrace(out);
        }
    }

    private StringBuilder decorate(StringBuilder sb, String msg) {
        String tag = dbgTag == null ? null : dbgTag.get();
        String res = msg == null ? "" : msg;
        long elapsed = System.nanoTime() - START_NANOS;
        long millis = elapsed / 1000_000;
        long secs   = millis / 1000;
        sb.append('[').append(Thread.currentThread().getName()).append(']')
                .append(' ').append('[');
        if (secs > 0) {
            sb.append(secs).append('s');
        }
        millis = millis % 1000;
        if (millis > 0) {
            if (secs > 0) sb.append(' ');
            sb.append(millis).append("ms");
        }
        sb.append(']').append(' ');
        if (tag != null) {
            sb.append(tag).append(' ');
        }
        sb.append(res);
        return sb;
    }

    private StringBuilder getFormat(StringBuilder sb, String format, Object[] params) {
        if (format == null || params == null || params.length == 0) {
            return decorate(sb, format);
        } else if (format.contains("{0}") || format.contains("{1}")) {
            return decorate(sb, format);
        } else if (format.contains("%s") || format.contains("%d")) {
            try {
                return decorate(sb, String.format(format, params));
            } catch (Throwable t) {
                return decorate(sb, format);
            }
        } else {
            return decorate(sb, format);
        }
    }

    private StringBuilder format(StringBuilder sb, String format, Object[] params) {
        if (format == null || params == null || params.length == 0) {
            return decorate(sb, format);
        } else if (format.contains("{0}") || format.contains("{1}")) {
            return decorate(sb, java.text.MessageFormat.format(format, params));
        } else if (format.contains("%s") || format.contains("%d")) {
            try {
                return decorate(sb, String.format(format, params));
            } catch (Throwable t) {
                return decorate(sb, format);
            }
        } else {
            return decorate(sb, format);
        }
    }

    public static DebugLogger createHttpLogger(Supplier<String> dbgTag,
                                               LoggerConfig config) {
        if (levelEnabledFor(Level.DEBUG, config, HTTP)) {
            return new DebugLogger(HTTP, dbgTag, config);
        } else {
            // return a shared instance if debug logging is not enabled.
            return NO_HTTP_LOGGER;
        }
    }

    public static DebugLogger createWebSocketLogger(Supplier<String> dbgTag,
                                                    LoggerConfig config) {
        if (levelEnabledFor(Level.DEBUG, config, WS)) {
            return new DebugLogger(WS, dbgTag, config);
        } else {
            // return a shared instance if logging is not enabled.
            return NO_WS_LOGGER;
        }
    }

    public static DebugLogger createHpackLogger(Supplier<String> dbgTag, LoggerConfig config) {
        if (levelEnabledFor(Level.DEBUG, config, HPACK)) {
            return new DebugLogger(HPACK, dbgTag, config);
        } else {
            // return a shared instance if logging is not enabled.
            return NO_HPACK_LOGGER;
        }
    }
}

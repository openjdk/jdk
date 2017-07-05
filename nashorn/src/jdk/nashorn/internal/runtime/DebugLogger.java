/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Wrapper class for Logging system. This is how you are supposed to register a logger and use it
 */

public final class DebugLogger {
    private final Logger  logger;
    private final boolean isEnabled;

    private int indent;

    private static final int INDENT_SPACE = 4;

    /**
     * Constructor
     *
     * @param loggerName name of logger - this is the unique key with which it can be identified
     */
    public DebugLogger(final String loggerName) {
        this(loggerName, null);
    }

    /**
     * Constructor
     *
     * A logger can be paired with a property, e.g. {@code --log:codegen:info} is equivalent to {@code -Dnashorn.codegen.log}
     *
     * @param loggerName name of logger - this is the unique key with which it can be identified
     * @param property   system property activating the logger on {@code info} level
     */
    public DebugLogger(final String loggerName, final String property) {
        if (property != null && Options.getBooleanProperty(property)) {
            this.logger = Logging.getOrCreateLogger(loggerName, Level.INFO);
        } else {
            this.logger = Logging.getLogger(loggerName);
        }
        assert logger != null;
        this.isEnabled = getLevel() != Level.OFF;
    }

    /**
     * Do not currently support chaining this with parent logger. Logger level null
     * means disabled
     * @return level
     */
    private Level getLevel() {
        return logger.getLevel() == null ? Level.OFF : logger.getLevel();
    }

    /**
     * Get the output writer for the logger. Loggers always default to
     * stderr for output as they are used mainly to output debug info
     *
     * Can be inherited so this should not be static.
     *
     * @return print writer for log output.
     */
    @SuppressWarnings("static-method")
    public PrintWriter getOutputStream() {
        return Context.getCurrentErr();
    }

    /**
     * Check if the logger is enabled
     * @return true if enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * If you want to change the indent level of your logger, call indent with a new position.
     * Positions start at 0 and are increased by one for a new "tab"
     *
     * @param pos indent position
     */
    public void indent(final int pos) {
        if (isEnabled) {
           indent += pos * INDENT_SPACE;
        }
    }

    /**
     * Add an indent position
     */
    public void indent() {
        indent += INDENT_SPACE;
    }

    /**
     * Unindent a position
     */
    public void unindent() {
        indent -= INDENT_SPACE;
        if (indent < 0) {
            indent = 0;
        }
    }

    /**
     * Check if the logger is above of the level of detail given
     * @see java.util.logging.Level
     *
     * @param level logging level
     * @return true if level is above the given one
     */
    public boolean levelAbove(final Level level) {
        return getLevel().intValue() > level.intValue();
    }

    /**
     * Shorthand for outputting a log string as log level {@link java.util.logging.Level#FINEST} on this logger
     * @param str the string to log
     */
    public void finest(final String str) {
        log(Level.FINEST, str);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINEST} on this logger
     * @param objs object array to log - use this to perform lazy concatenation to avoid unconditional toString overhead
     */
    public void finest(final Object... objs) {
        log(Level.FINEST, objs);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINER} on this logger
     * @param str the string to log
     */
    public void finer(final String str) {
        log(Level.FINER, str);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINER} on this logger
     * @param objs object array to log - use this to perform lazy concatenation to avoid unconditional toString overhead
     */
    public void finer(final Object... objs) {
        log(Level.FINER, objs);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINE} on this logger
     * @param str the string to log
     */
    public void fine(final String str) {
        log(Level.FINE, str);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINE} on this logger
     * @param objs object array to log - use this to perform lazy concatenation to avoid unconditional toString overhead
     */
    public void fine(final Object... objs) {
        log(Level.FINE, objs);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#CONFIG} on this logger
     * @param str the string to log
     */
    public void config(final String str) {
        log(Level.CONFIG, str);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#CONFIG} on this logger
     * @param objs object array to log - use this to perform lazy concatenation to avoid unconditional toString overhead
     */
    public void config(final Object... objs) {
        log(Level.CONFIG, objs);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#INFO} on this logger
     * @param str the string to log
     */
    public void info(final String str) {
        log(Level.INFO, str);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINE} on this logger
     * @param objs object array to log - use this to perform lazy concatenation to avoid unconditional toString overhead
     */
    public void info(final Object... objs) {
        log(Level.INFO, objs);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#WARNING} on this logger
     * @param str the string to log
     */
    public void warning(final String str) {
        log(Level.WARNING, str);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINE} on this logger
     * @param objs object array to log - use this to perform lazy concatenation to avoid unconditional toString overhead
     */
    public void warning(final Object... objs) {
        log(Level.WARNING, objs);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#SEVERE} on this logger
     * @param str the string to log
     */
    public void severe(final String str) {
        log(Level.SEVERE, str);
    }

    /**
     * Shorthand for outputting a log string as log level
     * {@link java.util.logging.Level#FINE} on this logger
     * @param objs object array to log - use this to perform lazy concatenation to avoid unconditional toString overhead
     */
    public void severe(final Object... objs) {
        log(Level.SEVERE, objs);
    }

    /**
     * Output log line on this logger at a given level of verbosity
     * @see java.util.logging.Level
     *
     * @param level minimum log level required for logging to take place
     * @param str   string to log
     */
    public void log(final Level level, final String str) {
        if (isEnabled) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0 ; i < indent ; i++) {
                sb.append(' ');
            }
            sb.append(str);
            logger.log(level, sb.toString());
        }
    }

    /**
     * Output log line on this logger at a given level of verbosity
     * @see java.util.logging.Level
     *
     * @param level minimum log level required for logging to take place
     * @param objs  objects for which to invoke toString and concatenate to log
     */
    public void log(final Level level, final Object... objs) {
        if (isEnabled) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0 ; i < indent ; i++) {
                sb.append(' ');
            }
            for (final Object obj : objs) {
                sb.append(obj);
            }
            logger.log(level, sb.toString());
        }
    }
}

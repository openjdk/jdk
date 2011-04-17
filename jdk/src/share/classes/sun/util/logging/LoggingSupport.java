/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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


package sun.util.logging;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Date;

/**
 * Internal API to support JRE implementation to detect if the java.util.logging
 * support is available but with no dependency on the java.util.logging
 * classes.  This LoggingSupport class provides several static methods to
 * access the java.util.logging functionality that requires the caller
 * to ensure that the logging support is {@linkplain #isAvailable available}
 * before invoking it.
 *
 * @see sun.util.logging.PlatformLogger if you want to log messages even
 * if the logging support is not available
 */
public class LoggingSupport {
    private LoggingSupport() { }

    private static final LoggingProxy proxy =
        AccessController.doPrivileged(new PrivilegedAction<LoggingProxy>() {
            public LoggingProxy run() {
                try {
                    // create a LoggingProxyImpl instance when
                    // java.util.logging classes exist
                    Class<?> c = Class.forName("java.util.logging.LoggingProxyImpl", true, null);
                    Field f = c.getDeclaredField("INSTANCE");
                    f.setAccessible(true);
                    return (LoggingProxy) f.get(null);
                } catch (ClassNotFoundException cnf) {
                    return null;
                } catch (NoSuchFieldException e) {
                    throw new AssertionError(e);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }});

    /**
     * Returns true if java.util.logging support is available.
     */
    public static boolean isAvailable() {
        return proxy != null;
    }

    private static void ensureAvailable() {
        if (proxy == null)
            throw new AssertionError("Should not here");
    }

    public static java.util.List<String> getLoggerNames() {
        ensureAvailable();
        return proxy.getLoggerNames();
    }
    public static String getLoggerLevel(String loggerName) {
        ensureAvailable();
        return proxy.getLoggerLevel(loggerName);
    }

    public static void setLoggerLevel(String loggerName, String levelName) {
        ensureAvailable();
        proxy.setLoggerLevel(loggerName, levelName);
    }

    public static String getParentLoggerName(String loggerName) {
        ensureAvailable();
        return proxy.getParentLoggerName(loggerName);
    }

    public static Object getLogger(String name) {
        ensureAvailable();
        return proxy.getLogger(name);
    }

    public static Object getLevel(Object logger) {
        ensureAvailable();
        return proxy.getLevel(logger);
    }

    public static void setLevel(Object logger, Object newLevel) {
        ensureAvailable();
        proxy.setLevel(logger, newLevel);
    }

    public static boolean isLoggable(Object logger, Object level) {
        ensureAvailable();
        return proxy.isLoggable(logger,level);
    }

    public static void log(Object logger, Object level, String msg) {
        ensureAvailable();
        proxy.log(logger, level, msg);
    }

    public static void log(Object logger, Object level, String msg, Throwable t) {
        ensureAvailable();
        proxy.log(logger, level, msg, t);
    }

    public static void log(Object logger, Object level, String msg, Object... params) {
        ensureAvailable();
        proxy.log(logger, level, msg, params);
    }

    public static Object parseLevel(String levelName) {
        ensureAvailable();
        return proxy.parseLevel(levelName);
    }

    public static String getLevelName(Object level) {
        ensureAvailable();
        return proxy.getLevelName(level);
    }

    private static final String DEFAULT_FORMAT =
        "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n";

    private static final String FORMAT_PROP_KEY = "java.util.logging.SimpleFormatter.format";
    public static String getSimpleFormat() {
        return getSimpleFormat(true);
    }

    // useProxy if true will cause initialization of
    // java.util.logging and read its configuration
    static String getSimpleFormat(boolean useProxy) {
        String format =
            AccessController.doPrivileged(
                new PrivilegedAction<String>() {
                    public String run() {
                        return System.getProperty(FORMAT_PROP_KEY);
                    }
                });

        if (useProxy && proxy != null && format == null) {
            format = proxy.getProperty(FORMAT_PROP_KEY);
        }

        if (format != null) {
            try {
                // validate the user-defined format string
                String.format(format, new Date(), "", "", "", "", "");
            } catch (IllegalArgumentException e) {
                // illegal syntax; fall back to the default format
                format = DEFAULT_FORMAT;
            }
        } else {
            format = DEFAULT_FORMAT;
        }
        return format;
    }

}

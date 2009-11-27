/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package sun.util.logging;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

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
}

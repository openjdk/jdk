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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.LoggingPermission;

/**
 * Logging system for getting loggers for arbitrary subsystems as
 * specified on the command line. Supports all standard log levels
 *
 */
public final class Logging {

    private Logging() {
    }

    /** Loggers */

    private static final Logger disabledLogger = Logger.getLogger("disabled");

    private static AccessControlContext createLoggerControlAccCtxt() {
        final Permissions perms = new Permissions();
        perms.add(new LoggingPermission("control", null));
        return new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, perms) });
    }

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                Logging.disabledLogger.setLevel(Level.OFF);
                return null;
            }
        }, createLoggerControlAccCtxt());
    }

    /** Maps logger name to loggers. Names are typically per package */
    private static final Map<String, Logger> loggers = new HashMap<>();

    private static String lastPart(final String packageName) {
        final String[] parts = packageName.split("\\.");
        if (parts.length == 0) {
            return packageName;
        }
        return parts[parts.length - 1];
    }

    /**
     * Get a logger for a given class, generating a logger name based on the
     * class name
     *
     * @param name the name to use as key for the logger
     * @return the logger
     */
    public static Logger getLogger(final String name) {
        final Logger logger = Logging.loggers.get(name);
        if (logger != null) {
            return logger;
        }
        return Logging.disabledLogger;
    }

    /**
     * Get a logger for a given name or create it if not already there, typically
     * used for mapping system properties to loggers
     *
     * @param name the name to use as key
     * @param level log lever to reset existing logger with, or create new logger with
     * @return the logger
     */
    public static Logger getOrCreateLogger(final String name, final Level level) {
        final Logger logger = Logging.loggers.get(name);
        if (logger == null) {
            return instantiateLogger(name, level);
        }
        logger.setLevel(level);
        return logger;
    }

    /**
     * Initialization function that is called to instantiate the logging system. It takes
     * logger names (keys) and logging labels respectively
     *
     * @param map a map where the key is a logger name and the value a logging level
     * @throws IllegalArgumentException if level or names cannot be parsed
     */
    public static void initialize(final Map<String, String> map) throws IllegalArgumentException {
        try {
            for (final Entry<String, String> entry : map.entrySet()) {
                Level level;

                final String key   = entry.getKey();
                final String value = entry.getValue();
                if ("".equals(value)) {
                    level = Level.INFO;
                } else {
                    level = Level.parse(value.toUpperCase(Locale.ENGLISH));
                }

                final String name = Logging.lastPart(key);
                final Logger logger = instantiateLogger(name, level);

                Logging.loggers.put(name, logger);
            }
        } catch (final IllegalArgumentException | SecurityException e) {
            throw e;
        }
    }

    private static Logger instantiateLogger(final String name, final Level level) {
        final Logger logger = java.util.logging.Logger.getLogger(name);
        for (final Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }

        logger.setLevel(level);
        logger.setUseParentHandlers(false);
        final Handler c = new ConsoleHandler();

        c.setFormatter(new Formatter() {
            @Override
            public String format(final LogRecord record) {
                final StringBuilder sb = new StringBuilder();

                sb.append('[')
                   .append(record.getLoggerName())
                   .append("] ")
                   .append(record.getMessage())
                   .append('\n');

                return sb.toString();
            }
        });
        logger.addHandler(c);
        c.setLevel(level);

        return logger;
    }

}

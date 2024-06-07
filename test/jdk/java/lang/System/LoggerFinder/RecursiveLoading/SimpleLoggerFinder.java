/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package loggerfinder;

import java.lang.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class SimpleLoggerFinder extends System.LoggerFinder {

    public static final CopyOnWriteArrayList<Object> LOGS = new CopyOnWriteArrayList<>();
    static {
        try {
            long sleep = new Random().nextLong(1000L) + 1L;
            // simulate a slow load service
            Thread.sleep(sleep);
            System.getLogger("dummy")
                    .log(System.Logger.Level.INFO,
                            "Logger finder service load sleep value: " + sleep);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<String, SimpleLogger> loggers = new ConcurrentHashMap<>();
    public SimpleLoggerFinder() {
        System.getLogger("dummy")
                .log(System.Logger.Level.INFO,
                        "Logger finder service created");
    }

    @Override
    public System.Logger getLogger(String name, Module module) {
        return loggers.computeIfAbsent(name, SimpleLogger::new);
    }

    private static class SimpleLogger implements System.Logger {
        private final java.util.logging.Logger logger;

        private static final class SimpleHandler extends Handler {
            @Override
            public void publish(LogRecord record) {
                LOGS.add(record);
            }
            @Override public void flush() { }
            @Override public void close() { }
        }

        public SimpleLogger(String name) {
            logger = Logger.getLogger(name);
            logger.addHandler(new SimpleHandler());
        }

        @Override
        public String getName() {
            return logger.getName();
        }

        java.util.logging.Level level(Level level) {
            return switch (level) {
                case ALL -> java.util.logging.Level.ALL;
                case DEBUG -> java.util.logging.Level.FINE;
                case TRACE -> java.util.logging.Level.FINER;
                case INFO -> java.util.logging.Level.INFO;
                case WARNING -> java.util.logging.Level.WARNING;
                case ERROR -> java.util.logging.Level.SEVERE;
                case OFF -> java.util.logging.Level.OFF;
            };
        }

        @Override
        public boolean isLoggable(Level level) {
            return logger.isLoggable(level(level));
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            var julLevel = level(level);
            if (!logger.isLoggable(julLevel)) return;
            if (bundle != null) {
                logger.logrb(julLevel, bundle, msg, thrown);
            } else {
                logger.log(julLevel, msg, thrown);
            }
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            var julLevel = level(level);
            if (!logger.isLoggable(julLevel)) return;
            if (params == null) {
                if (bundle == null) {
                    logger.log(julLevel, format);
                } else {
                    logger.logrb(julLevel, bundle, format);
                }
            } else {
                if (bundle == null) {
                    logger.log(julLevel, format, params);
                } else {
                    logger.logrb(julLevel, bundle, format, params);
                }
            }
        }
    }
}


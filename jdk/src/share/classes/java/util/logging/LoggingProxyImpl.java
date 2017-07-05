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

package java.util.logging;

import sun.util.logging.LoggingProxy;

/**
 * Implementation of LoggingProxy when java.util.logging classes exist.
 */
class LoggingProxyImpl implements LoggingProxy {
    static final LoggingProxy INSTANCE = new LoggingProxyImpl();

    private LoggingProxyImpl() { }

    @Override
    public Object getLogger(String name) {
        return Logger.getLogger(name);
    }

    @Override
    public Object getLevel(Object logger) {
        return ((Logger) logger).getLevel();
    }

    @Override
    public void setLevel(Object logger, Object newLevel) {
        ((Logger) logger).setLevel((Level) newLevel);
    }

    @Override
    public boolean isLoggable(Object logger, Object level) {
        return ((Logger) logger).isLoggable((Level) level);
    }

    @Override
    public void log(Object logger, Object level, String msg) {
        ((Logger) logger).log((Level) level, msg);
    }

    @Override
    public void log(Object logger, Object level, String msg, Throwable t) {
        ((Logger) logger).log((Level) level, msg, t);
    }

    @Override
    public void log(Object logger, Object level, String msg, Object... params) {
        ((Logger) logger).log((Level) level, msg, params);
    }

    @Override
    public java.util.List<String> getLoggerNames() {
        return LogManager.getLoggingMXBean().getLoggerNames();
    }

    @Override
    public String getLoggerLevel(String loggerName) {
        return LogManager.getLoggingMXBean().getLoggerLevel(loggerName);
    }

    @Override
    public void setLoggerLevel(String loggerName, String levelName) {
        LogManager.getLoggingMXBean().setLoggerLevel(loggerName, levelName);
    }

    @Override
    public String getParentLoggerName(String loggerName) {
        return LogManager.getLoggingMXBean().getParentLoggerName(loggerName);
    }

    @Override
    public Object parseLevel(String levelName) {
        return Level.parse(levelName);
    }

    @Override
    public String getLevelName(Object level) {
        return ((Level) level).getName();
    }
}

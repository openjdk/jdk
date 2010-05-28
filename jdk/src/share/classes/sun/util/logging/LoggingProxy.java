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

/**
 * A proxy interface for the java.util.logging support.
 *
 * @see sun.util.logging.LoggingSupport
 */
public interface LoggingProxy {
    // Methods to bridge java.util.logging.Logger methods
    public Object getLogger(String name);

    public Object getLevel(Object logger);

    public void setLevel(Object logger, Object newLevel);

    public boolean isLoggable(Object logger, Object level);

    public void log(Object logger, Object level, String msg);

    public void log(Object logger, Object level, String msg, Throwable t);

    public void log(Object logger, Object level, String msg, Object... params);

    // Methods to bridge java.util.logging.LoggingMXBean methods
    public java.util.List<String> getLoggerNames();

    public String getLoggerLevel(String loggerName);

    public void setLoggerLevel(String loggerName, String levelName);

    public String getParentLoggerName(String loggerName);

    // Methods to bridge Level.parse() and Level.getName() method
    public Object parseLevel(String levelName);

    public String getLevelName(Object level);
}

/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

/**
 * Logging is the implementation class of LoggingMXBean.
 *
 * The <tt>LoggingMXBean</tt> interface provides a standard
 * method for management access to the individual
 * java.util.Logger objects available at runtime.
 *
 * @author Ron Mann
 * @author Mandy Chung
 * @since 1.5
 *
 * @see javax.management
 * @see java.util.Logger
 * @see java.util.LogManager
 */
class Logging implements LoggingMXBean {

    private static LogManager logManager = LogManager.getLogManager();

    /** Constructor of Logging which is the implementation class
     *  of LoggingMXBean.
     */
    Logging() {
    }

    public List<String> getLoggerNames() {
        Enumeration loggers = logManager.getLoggerNames();
        ArrayList<String> array = new ArrayList<String>();

        for (; loggers.hasMoreElements();) {
            array.add((String) loggers.nextElement());
        }
        return array;
    }

    private static String EMPTY_STRING = "";
    public String getLoggerLevel(String loggerName) {
        Logger l = logManager.getLogger(loggerName);
        if (l == null) {
            return null;
        }

        Level level = l.getLevel();
        if (level == null) {
            return EMPTY_STRING;
        } else {
            return level.getName();
        }
    }

    public void setLoggerLevel(String loggerName, String levelName) {
        if (loggerName == null) {
            throw new NullPointerException("loggerName is null");
        }

        Logger logger = logManager.getLogger(loggerName);

        if (logger == null) {
            throw new IllegalArgumentException("Logger " + loggerName +
                "does not exist");
        }

        Level level = null;
        if (levelName != null) {
            // parse will throw IAE if logLevel is invalid
            level = Level.parse(levelName);
        }

        logger.setLevel(level);
    }

    public String getParentLoggerName( String loggerName ) {
        Logger l = logManager.getLogger( loggerName );
        if (l == null) {
            return null;
        }

        Logger p = l.getParent();
        if (p == null) {
            // root logger
            return EMPTY_STRING;
        } else {
            return p.getName();
        }
    }
}

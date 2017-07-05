/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.X11;

import java.util.logging.*;
import java.text.*;
import java.util.*;
import java.io.*;

/**
 * Formatter class providing ANSI output. Based on java.util.logging.SimpleFormatter sources.
 */

public class XAWTFormatter extends java.util.logging.Formatter {
    Date dat = new Date();
    private final static String format = "{0,date} {0,time}";
    private MessageFormat formatter;

    private Object args[] = new Object[1];

    // Line separator string.  This is the value of the line.separator
    // property at the moment that the SimpleFormatter was created.
    private String lineSeparator = (String) java.security.AccessController.doPrivileged(
               new sun.security.action.GetPropertyAction("line.separator"));

    boolean displayFullRecord = false;
    boolean useANSI = false;
    boolean showDate = true;
    boolean showLevel = true;
    boolean swapMethodClass = false;
    public XAWTFormatter() {
        displayFullRecord = "true".equals(LogManager.getLogManager().getProperty("XAWTFormatter.displayFullRecord"));
        useANSI = "true".equals(LogManager.getLogManager().getProperty("XAWTFormatter.useANSI"));
        showDate = !"false".equals(LogManager.getLogManager().getProperty("XAWTFormatter.showDate"));
        showLevel = !"false".equals(LogManager.getLogManager().getProperty("XAWTFormatter.showLevel"));
        swapMethodClass = "true".equals(LogManager.getLogManager().getProperty("XAWTFormatter.swapMethodClass"));
    }

    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    public synchronized String format(LogRecord record) {
        StringBuffer sb = new StringBuffer();
        if (useANSI) {
            Level lev = record.getLevel();
            if (Level.FINEST.equals(lev)) {
                sb.append("[36m");
            } else if (Level.FINER.equals(lev)) {
                sb.append("[32m");
            } else if (Level.FINE.equals(lev)) {
                sb.append("[34m");
            }
        }
        if (displayFullRecord) {
            if (showDate) {
                // Minimize memory allocations here.
                dat.setTime(record.getMillis());
                args[0] = dat;
                StringBuffer text = new StringBuffer();
                if (formatter == null) {
                    formatter = new MessageFormat(format);
                }
                formatter.format(args, text, null);
                sb.append(text);
                sb.append(" ");
            } else {
                sb.append("    ");
            }
            if (swapMethodClass) {
                if (record.getSourceMethodName() != null) {
                    sb.append(" [35m");
                    sb.append(record.getSourceMethodName());
                    sb.append("[30m ");
                }
                if (record.getSourceClassName() != null) {
                    sb.append(record.getSourceClassName());
                } else {
                    sb.append(record.getLoggerName());
                }
            } else {
                if (record.getSourceClassName() != null) {
                    sb.append(record.getSourceClassName());
                } else {
                    sb.append(record.getLoggerName());
                }
                if (record.getSourceMethodName() != null) {
                    sb.append(" [35m");
                    sb.append(record.getSourceMethodName());
                    sb.append("[30m");
                }
            }
            sb.append(lineSeparator);
        }
        if (useANSI) {
            Level lev = record.getLevel();
            if (Level.FINEST.equals(lev)) {
                sb.append("[36m");
            } else if (Level.FINER.equals(lev)) {
                sb.append("[32m");
            } else if (Level.FINE.equals(lev)) {
                sb.append("[34m");
            }
        }
        if (showLevel) {
            sb.append(record.getLevel().getLocalizedName());
            sb.append(": ");
        }
        String message = formatMessage(record);
        sb.append(message);
        sb.append(lineSeparator);
        if (record.getThrown() != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                sb.append(sw.toString());
            } catch (Exception ex) {
            }
        }
        if (useANSI) {
            sb.append("[30m");
        }
        return sb.toString();
    }
}

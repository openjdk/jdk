/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac;

import java.io.PrintStream;

/**
 * Utility class only for sjavac logging.
 * The log level can be set using for example --log=DEBUG on the sjavac command line.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Log {
    private static PrintStream out, err;

    public final static int WARN = 1;
    public final static int INFO = 2;
    public final static int DEBUG = 3;
    public final static int TRACE = 4;
    private static int level = WARN;

    static public void trace(String msg) {
        if (level >= TRACE) {
            out.println(msg);
        }
    }

    static public void debug(String msg) {
        if (level >= DEBUG) {
            out.println(msg);
        }
    }

    static public void info(String msg) {
        if (level >= INFO) {
            out.println(msg);
        }
    }

    static public void warn(String msg) {
        err.println(msg);
    }

    static public void error(String msg) {
        err.println(msg);
    }

    static public void setLogLevel(String l, PrintStream o, PrintStream e)
        throws ProblemException {
        out = o;
        err = e;
        switch (l) {
            case "warn": level = WARN; break;
            case "info": level = INFO; break;
            case "debug": level = DEBUG; break;
            case "trace": level = TRACE; break;
            default:
                throw new ProblemException("No such log level \"" + l + "\"");
        }
    }

    static public boolean isTracing() {
        return level >= TRACE;
    }

    static public boolean isDebugging() {
        return level >= DEBUG;
    }
}

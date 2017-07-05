/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.activation.registries;

import java.io.*;
import java.util.logging.*;

/**
 * Logging related methods.
 */
public class LogSupport {
    private static boolean debug = false;
    private static Logger logger;
    private static final Level level = Level.FINE;

    static {
        try {
            debug = Boolean.getBoolean("javax.activation.debug");
        } catch (Throwable t) {
            // ignore any errors
        }
        logger = Logger.getLogger("javax.activation");
    }

    /**
     * Constructor.
     */
    private LogSupport() {
        // private constructor, can't create instances
    }

    public static void log(String msg) {
        if (debug)
            System.out.println(msg);
        logger.log(level, msg);
    }

    public static void log(String msg, Throwable t) {
        if (debug)
            System.out.println(msg + "; Exception: " + t);
        logger.log(level, msg, t);
    }

    public static boolean isLoggable() {
        return debug || logger.isLoggable(level);
    }
}

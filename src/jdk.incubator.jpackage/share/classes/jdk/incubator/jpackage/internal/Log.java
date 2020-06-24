/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.PrintWriter;

/**
 * Log
 *
 * General purpose logging mechanism.
 */
public class Log {
    public static class Logger {
        private boolean verbose = false;
        private PrintWriter out = null;
        private PrintWriter err = null;

        // verbose defaults to true unless environment variable JPACKAGE_DEBUG
        // is set to true.
        // Then it is only set to true by using --verbose jpackage option

        public Logger() {
            verbose = ("true".equals(System.getenv("JPACKAGE_DEBUG")));
        }

        public void setVerbose() {
            verbose = true;
        }

        public boolean isVerbose() {
            return verbose;
        }

        public void setPrintWriter(PrintWriter out, PrintWriter err) {
            this.out = out;
            this.err = err;
        }

        public void flush() {
            if (out != null) {
                out.flush();
            }

            if (err != null) {
                err.flush();
            }
        }

        public void info(String msg) {
            if (out != null) {
                out.println(msg);
            } else {
                System.out.println(msg);
            }
        }

        public void error(String msg) {
            if (err != null) {
                err.println(msg);
            } else {
                System.err.println(msg);
            }
        }

        public void verbose(Throwable t) {
            if (out != null && verbose) {
                t.printStackTrace(out);
            } else if (verbose) {
                t.printStackTrace(System.out);
            }
        }

        public void verbose(String msg) {
            if (out != null && verbose) {
                out.println(msg);
            } else if (verbose) {
                System.out.println(msg);
            }
        }
    }

    private static Logger delegate = null;

    public static void setLogger(Logger logger) {
        delegate = (logger != null) ? logger : new Logger();
    }

    public static void flush() {
        if (delegate != null) {
            delegate.flush();
        }
    }

    public static void info(String msg) {
        if (delegate != null) {
           delegate.info(msg);
        }
    }

    public static void error(String msg) {
        if (delegate != null) {
            delegate.error(msg);
        }
    }

    public static void setVerbose() {
        if (delegate != null) {
            delegate.setVerbose();
        }
    }

    public static boolean isVerbose() {
        return (delegate != null) ? delegate.isVerbose() : false;
    }

    public static void verbose(String msg) {
        if (delegate != null) {
           delegate.verbose(msg);
        }
    }

    public static void verbose(Throwable t) {
        if (delegate != null) {
           delegate.verbose(t);
        }
    }
}

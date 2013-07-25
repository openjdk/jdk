/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.media.sound;

/**
 * Printer allows you to set up global debugging status and print
 * messages accordingly.
 *
 * @author David Rivas
 * @author Kara Kytle
 */
final class Printer {

    static final boolean err = false;
    static final boolean debug = false;
    static final boolean trace = false;
    static final boolean verbose = false;
    static final boolean release = false;

    static final boolean SHOW_THREADID = false;
    static final boolean SHOW_TIMESTAMP = false;

    /*static void setErrorPrint(boolean on) {

      err = on;
      }

      static void setDebugPrint(boolean on) {

      debug = on;
      }

      static void setTracePrint(boolean on) {

      trace = on;
      }

      static void setVerbosePrint(boolean on) {

      verbose = on;
      }

      static void setReleasePrint(boolean on) {

      release = on;
      }*/

    /**
     * Suppresses default constructor, ensuring non-instantiability.
     */
    private Printer() {
    }

    public static void err(String str) {

        if (err)
            println(str);
    }

    public static void debug(String str) {

        if (debug)
            println(str);
    }

    public static void trace(String str) {

        if (trace)
            println(str);
    }

    public static void verbose(String str) {

        if (verbose)
            println(str);
    }

    public static void release(String str) {

        if (release)
            println(str);
    }

    private static long startTime = 0;

    public static void println(String s) {
        String prepend = "";
        if (SHOW_THREADID) {
            prepend = "thread "  + Thread.currentThread().getId() + " " + prepend;
        }
        if (SHOW_TIMESTAMP) {
            if (startTime == 0) {
                startTime = System.nanoTime() / 1000000l;
            }
            prepend = prepend + ((System.nanoTime()/1000000l) - startTime) + "millis: ";
        }
        System.out.println(prepend + s);
    }

    public static void println() {
        System.out.println();
    }

}

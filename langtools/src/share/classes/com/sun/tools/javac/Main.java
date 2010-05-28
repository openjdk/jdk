/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac;

import java.io.PrintWriter;
import java.lang.reflect.*;


/**
 * The programmatic interface for the Java Programming Language
 * compiler, javac.
 *
 * <p>Except for the two methods
 * {@link #compile(java.lang.String[])}
 * {@link #compile(java.lang.String[],java.io.PrintWriter)},
 * nothing described in this source file is part of any supported
 * API.  If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.
 */
public class Main {

    static {
        ClassLoader loader = Main.class.getClassLoader();
        if (loader != null)
            loader.setPackageAssertionStatus("com.sun.tools.javac", true);
    }

    /** Unsupported command line interface.
     * @param args   The command line parameters.
     */
    public static void main(String[] args) throws Exception {
      if (args.length > 0 && args[0].equals("-Xjdb")) {
        String[] newargs = new String[args.length + 2];
        Class<?> c = Class.forName("com.sun.tools.example.debug.tty.TTY");
        Method method = c.getDeclaredMethod ("main", new Class<?>[] {args.getClass()});
        method.setAccessible(true);
        System.arraycopy(args, 1, newargs, 3, args.length - 1);
        newargs[0] = "-connect";
        newargs[1] = "com.sun.jdi.CommandLineLaunch:options=-esa -ea:com.sun.tools...";
        newargs[2] = "com.sun.tools.javac.Main";
        method.invoke(null, new Object[] { newargs });
      } else {
        System.exit(compile(args));
      }
    }

    /** Programmatic interface to the Java Programming Language
     * compiler, javac.
     *
     * @param args The command line arguments that would normally be
     * passed to the javac program as described in the man page.
     * @return an integer equivalent to the exit value from invoking
     * javac, see the man page for details.
     */
    public static int compile(String[] args) {
        com.sun.tools.javac.main.Main compiler =
            new com.sun.tools.javac.main.Main("javac");
        return compiler.compile(args);
    }



    /** Programmatic interface to the Java Programming Language
     * compiler, javac.
     *
     * @param args The command line arguments that would normally be
     * passed to the javac program as described in the man page.
     * @param out PrintWriter to which the compiler's diagnostic
     * output is directed.
     * @return an integer equivalent to the exit value from invoking
     * javac, see the man page for details.
     */
    public static int compile(String[] args, PrintWriter out) {
        com.sun.tools.javac.main.Main compiler =
            new com.sun.tools.javac.main.Main("javac", out);
        return compiler.compile(args);
    }
}

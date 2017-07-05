/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.ws;

import com.sun.tools.internal.ws.wscompile.WsgenTool;

/**
 * WsGen tool entry point.
 *
 * @author Vivek Pandey
 * @author Kohsuke Kawaguchi
 */
public class WsGen {
    /**
     * CLI entry point. Use {@link Invoker} to
     * load tools.jar
     */
    public static void main(String[] args) throws Throwable {
        System.exit(Invoker.invoke("com.sun.tools.internal.ws.wscompile.WsgenTool", args));
    }

    /**
     * Entry point for tool integration.
     *
     * <p>
     * This does the same as {@link #main(String[])} except
     * it doesn't invoke {@link System#exit(int)}. This method
     * also doesn't play with classloaders. It's the caller's
     * responsibility to set up the classloader to load all jars
     * needed to run the tool, including <tt>$JAVA_HOME/lib/tools.jar</tt>
     *
     * @return
     *      0 if the tool runs successfully.
     */
    public static int doMain(String[] args) throws Throwable {
        return new WsgenTool(System.out).run(args) ? 0 : 1;
    }
}

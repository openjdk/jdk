/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.tools.internal.xjc.api.util.APTClassLoader;
import com.sun.tools.internal.xjc.api.util.ToolsJarNotFoundException;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Invokes JAX-WS tools in a special class loader that can pick up APT classes,
 * even if it's not available in the tool launcher classpath.
 *
 * @author Kohsuke Kawaguchi
 */
final class Invoker {
    /**
     * List of packages that need to be loaded in {@link APTClassLoader}.
     */
    private static final String[] prefixes = {
        "com.sun.tools.internal.jxc.",
        "com.sun.tools.internal.xjc.",
        "com.sun.tools.apt.",
        "com.sun.tools.internal.ws.",
        "com.sun.tools.javac.",
        "com.sun.tools.javadoc.",
        "com.sun.mirror."
    };

    static void main(String toolName, String[] args) throws Throwable {
        ClassLoader oldcc = Thread.currentThread().getContextClassLoader();
        try {
            APTClassLoader cl = new APTClassLoader(Invoker.class.getClassLoader(),prefixes);
            Thread.currentThread().setContextClassLoader(cl);

            Class compileTool = cl.loadClass("com.sun.tools.internal.ws.wscompile.CompileTool");
            Constructor ctor = compileTool.getConstructor(OutputStream.class,String.class);
            Object tool = ctor.newInstance(System.out,toolName);
            Method runMethod = compileTool.getMethod("run",String[].class);
            boolean r = (Boolean)runMethod.invoke(tool,new Object[]{args});
            System.exit(r ? 0 : 1);
        } catch (ToolsJarNotFoundException e) {
            System.err.println(e.getMessage());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            Thread.currentThread().setContextClassLoader(oldcc);
        }

        System.exit(1);
    }
}

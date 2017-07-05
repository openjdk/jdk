/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wscompile;

import com.sun.istack.internal.tools.ParallelWorldClassLoader;
import com.sun.tools.internal.ws.resources.JavacompilerMessages;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URISyntaxException;

/**
 * A helper class to invoke javac.
 *
 * @author WS Development Team
 */
class JavaCompilerHelper{
    static File getJarFile(Class clazz) {
        URL url = null;
        try {
            url = ParallelWorldClassLoader.toJarUrl(clazz.getResource('/'+clazz.getName().replace('.','/')+".class"));
            return new File(url.toURI());
        } catch (ClassNotFoundException e) {
            // if we can't figure out where JAXB/JAX-WS API are, we couldn't have been executing this code.
            throw new Error(e);
        } catch (MalformedURLException e) {
            // if we can't figure out where JAXB/JAX-WS API are, we couldn't have been executing this code.
            throw new Error(e);
        } catch (URISyntaxException e) {
            // url.toURI() is picky and doesn't like ' ' in URL, so this is the fallback
            return new File(url.getPath());
        }
    }

    static boolean compile(String[] args, OutputStream out, ErrorReceiver receiver){
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            /* try to use the new compiler */
            Class comSunToolsJavacMainClass =
                    cl.loadClass("com.sun.tools.javac.Main");
            try {
                Method compileMethod =
                        comSunToolsJavacMainClass.getMethod(
                                "compile",
                                compileMethodSignature);
                    Object result =
                            compileMethod.invoke(
                                    null, args, new PrintWriter(out));
                    return result instanceof Integer && (Integer) result == 0;
            } catch (NoSuchMethodException e2) {
                receiver.error(JavacompilerMessages.JAVACOMPILER_NOSUCHMETHOD_ERROR("getMethod(\"compile\", Class[])"), e2);
            } catch (IllegalAccessException e) {
                receiver.error(e);
            } catch (InvocationTargetException e) {
                receiver.error(e);
            }
        } catch (ClassNotFoundException e) {
            receiver.error(JavacompilerMessages.JAVACOMPILER_CLASSPATH_ERROR("com.sun.tools.javac.Main"), e);
        } catch (SecurityException e) {
            receiver.error(e);
        }
        return false;
    }

    private static final Class[] compileMethodSignature = {String[].class, PrintWriter.class};
}

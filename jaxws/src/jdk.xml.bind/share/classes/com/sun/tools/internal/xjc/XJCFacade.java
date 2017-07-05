/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;

/**
 * A shabby driver to invoke XJC1 or XJC2 depending on the command line switch.
 *
 * <p>
 * This class is compiled with -source 1.2 so that we can report a nice
 * user-friendly "you require Tiger" error message.
 *
 * @author Kohsuke Kawaguchi
 */
public class XJCFacade {

    private static final String JDK6_REQUIRED = "XJC requires JDK 6.0 or later. Please download it from http://www.oracle.com/technetwork/java/javase/downloads";

    public static void main(String[] args) throws Throwable {
        String v = "2.0";      // by default, we go 2.0

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-source")) {
                if (i + 1 < args.length) {
                    v = parseVersion(args[i + 1]);
                }
            }
        }

        ClassLoader oldContextCl = SecureLoader.getContextClassLoader();
        try {
            ClassLoader cl = ClassLoaderBuilder.createProtectiveClassLoader(SecureLoader.getClassClassLoader(XJCFacade.class), v);
            SecureLoader.setContextClassLoader(cl);
            Class<?> driver = cl.loadClass("com.sun.tools.internal.xjc.Driver");
            Method mainMethod = driver.getDeclaredMethod("main", new Class[]{String[].class});
            try {
                mainMethod.invoke(null, new Object[]{args});
            } catch (InvocationTargetException e) {
                if (e.getTargetException() != null) {
                    throw e.getTargetException();
                }
            }
        } catch (UnsupportedClassVersionError e) {
            System.err.println(JDK6_REQUIRED);
        } finally {
            ClassLoader cl = SecureLoader.getContextClassLoader();
            SecureLoader.setContextClassLoader(oldContextCl);

            //close/cleanup all classLoaders but the one which loaded this class
            while (cl != null && !oldContextCl.equals(cl)) {
                if (cl instanceof Closeable) {
                    //JDK7+, ParallelWorldClassLoader
                    ((Closeable) cl).close();
                } else {
                    if (cl instanceof URLClassLoader) {
                        //JDK6 - API jars are loaded by instance of URLClassLoader
                        //so use proprietary API to release holded resources
                        try {
                            Class<?> clUtil = oldContextCl.loadClass("sun.misc.ClassLoaderUtil");
                            Method release = clUtil.getDeclaredMethod("releaseLoader", URLClassLoader.class);
                            release.invoke(null, cl);
                        } catch (ClassNotFoundException ex) {
                            //not Sun JDK 6, ignore
                            System.err.println(JDK6_REQUIRED);
                        }
                    }
                }
                cl = SecureLoader.getParentClassLoader(cl);
            }
        }
    }

    public static String parseVersion(String version) {
        // no other versions supported as of now
        return "2.0";
    }
}

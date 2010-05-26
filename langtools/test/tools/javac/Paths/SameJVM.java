/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * Support routines to allow running `javac' or `jar' within the same JVM.
 */

import java.io.*;
import java.net.*;
import java.lang.reflect.*;

class SameJVM {

    private static ClassLoader toolsClassLoader() {
        File javaHome   = new File(System.getProperty("java.home"));
        File classesDir = new File(javaHome, "classes");
        File libDir     = new File(javaHome.getParentFile(), "lib");
        File toolsJar   = new File(libDir, "tools.jar");
        try {
            return new URLClassLoader(
                new URL[] {classesDir.toURL(), toolsJar.toURL()});
        } catch (MalformedURLException e) { throw new AssertionError(e); }
    }
    private static final ClassLoader cl = toolsClassLoader();

    static void javac(String... args) throws Exception {
        Class c = Class.forName("com.sun.tools.javac.Main", true, cl);
        int status = (Integer)
            c.getMethod("compile", new Class[] {String[].class})
            .invoke(c.newInstance(), new Object[] {args});
        if (status != 0)
            throw new Exception("javac failed: status=" + status);
    }

    static void jar(String... args) throws Exception {
        Class c = Class.forName("sun.tools.jar.Main", true, cl);
        Object instance = c.getConstructor(
            new Class[] {PrintStream.class, PrintStream.class, String.class})
            .newInstance(System.out, System.err, "jar");
        boolean result = (Boolean)
            c.getMethod("run", new Class[] {String[].class})
            .invoke(instance, new Object[] {args});
        if (! result)
            throw new Exception("jar failed");
    }
}

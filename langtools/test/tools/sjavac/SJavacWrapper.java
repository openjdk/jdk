/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test all aspects of sjavac.
 *
 * @bug 8004658
 * @summary Add internal smart javac wrapper to solve JEP 139
 *
 * @run main SJavacWrapper
 */

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;


public
class SJavacWrapper {

    public static void main(String... args) throws Exception {
        URL url = SJavacWrapper.class.getClassLoader().getResource("com/sun/tools/sjavac/Main.class");
        if (url == null) {
            // No sjavac in the classpath.
            System.out.println("sjavac not available: pass by default");
            return;
        }

        File testSrc = new File(System.getProperty("test.src"));
        File sjavac_java = new File(testSrc, "SJavac.java");
        File testClasses = new File(System.getProperty("test.classes"));
        File sjavac_class = new File(testClasses, "SJavac.class");
        if (sjavac_class.lastModified() < sjavac_java.lastModified()) {
            String[] javac_args = { "-d", testClasses.getPath(), sjavac_java.getPath() };
            System.err.println("Recompiling SJavac.java");
            int rc = com.sun.tools.javac.Main.compile(javac_args);
            if (rc != 0)
                throw new Exception("compilation failed");
        }

        Class<?> sjavac = Class.forName("SJavac");
        Method sjavac_main = sjavac.getMethod("main", String[].class);
        sjavac_main.invoke(null, new Object[] { args });
    }

}

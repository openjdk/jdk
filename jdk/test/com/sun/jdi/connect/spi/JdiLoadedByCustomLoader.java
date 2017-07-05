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
 * Creates a URLClassLoader from 2 file URLs. The first
 * file URL is constructed from the given argument. The
 * second is the SDK tools.jar. Once created the test
 * attempts to load another test case (ListConnectors)
 * using the class loader and then it invokes the list()
 * method.
 */
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.lang.reflect.Method;

public class JdiLoadedByCustomLoader {

    public static void main(String args[]) throws Exception {
        // create files from given arguments and tools.jar
        File f1 = new File(args[0]);
        String home = System.getProperty("java.home");
        String tools = ".." + File.separatorChar + "lib" +
            File.separatorChar + "tools.jar";
        File f2 = (new File(home, tools)).getCanonicalFile();

        // create class loader
        URL[] urls = { f1.toURL(), f2.toURL() };
        URLClassLoader cl = new URLClassLoader(urls);

        // load ListConnectors using the class loader
        // and then invoke the list method.
        Class c = Class.forName("ListConnectors", true, cl);
        Method m = c.getDeclaredMethod("list");
        Object o = c.newInstance();
        m.invoke(o);
    }
}

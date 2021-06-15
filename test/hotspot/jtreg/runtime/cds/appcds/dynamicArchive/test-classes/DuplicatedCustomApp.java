/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.*;
import java.net.*;
import sun.hotspot.WhiteBox;

public class DuplicatedCustomApp {
    static WhiteBox wb = WhiteBox.getWhiteBox();
    public static void main(String args[]) throws Exception {
        String path = args[0];
        URL url = new File(path).toURI().toURL();
        URL[] urls = new URL[] {url};
        System.out.println(path);
        System.out.println(url);

        int num_loops = 1;
        if (args.length > 1) {
            num_loops = Integer.parseInt(args[1]);
        }

        for (int i = 0; i < num_loops; i++) {
            System.out.println("============================ LOOP = " + i);
            URLClassLoader urlClassLoader = new URLClassLoader(urls);
            test(i, urlClassLoader, "CustomLoadee");
            test(i, urlClassLoader, "CustomLoadee2");
        }
    }

    private static void test(int i, URLClassLoader urlClassLoader, String name) throws Exception {
        Class c = urlClassLoader.loadClass(name);
        c.newInstance(); // make sure the class is linked.
        boolean is_shared = wb.isSharedClass(c);

        System.out.println("Class = " + c + ", loaded from " + (is_shared ? "CDS" : "Jar"));
        System.out.println("Loader = " + c.getClassLoader());

        // [1] Check that CustomLoadee is defined by the correct loader
        if (c.getClassLoader() != urlClassLoader) {
            throw new RuntimeException("c.getClassLoader() == " + c.getClassLoader() +
                                       ", expected == " + urlClassLoader);
        }


        // There's only one copy of the shared class of <name> in the
        // CDS archive. 
        if (i == 0) {
            // The first time we must be able to load it from CDS.
            if (wb.isSharedClass(DuplicatedCustomApp.class)) {
                if (!is_shared) {
                    throw new RuntimeException("Must be loaded from CDS");
                }
            }
        } else {
            // All subsequent times, we must load this from JAR file.
            if (is_shared) {
                throw new RuntimeException("Must be loaded from JAR");
            }
        }
    }
}

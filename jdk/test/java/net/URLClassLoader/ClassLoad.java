/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4151665
 * @summary Test for FileNotFoundException when loading bogus class
 */

import java.net.*;
import java.io.*;

public class ClassLoad {
     public static void main(String[] args) throws Exception {
         boolean error = true;
         try {
             URL url = new URL(args.length >= 1 ? args[0] : "http://jini.east/");
             String name = args.length >= 2 ? args[1] : "foo.bar.Baz";
             ClassLoader loader = new URLClassLoader(new URL[] { url });
             Class c = loader.loadClass(name);
             System.out.println("Loaded class \"" + c.getName() + "\".");
         } catch (ClassNotFoundException ex) {
             error = false;
         }
         if (error)
             throw new RuntimeException("No ClassNotFoundException generated");
    }
}

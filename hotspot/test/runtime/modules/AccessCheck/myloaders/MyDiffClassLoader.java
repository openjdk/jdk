/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package myloaders;

import java.io.*;
import java.lang.module.ModuleReference;

// Declare a MyDiffClassLoader class to be used to map modules to.
// This class loader will also be used to load classes within modules.
public class MyDiffClassLoader extends ClassLoader
{
    public static MyDiffClassLoader loader1 = new MyDiffClassLoader();
    public static MyDiffClassLoader loader2 = new MyDiffClassLoader();

    public Class loadClass(String name) throws ClassNotFoundException {
        if (!name.equals("p1.c1") &&
            !name.equals("p1.c1ReadEdgeDiffLoader") &&
            !name.equals("p1.c1Loose") &&
            !name.equals("p2.c2") &&
            !name.equals("p3.c3") &&
            !name.equals("p3.c3ReadEdgeDiffLoader") &&
            !name.equals("c4") &&
            !name.equals("c5") &&
            !name.equals("p6.c6")) {
            return super.loadClass(name);
        }
        if ((name.equals("p2.c2") || name.equals("c4") || name.equals("p6.c6")) &&
            (this == MyDiffClassLoader.loader1)) {
            return MyDiffClassLoader.loader2.loadClass(name);
        }

        byte[] data = getClassData(name);
        return defineClass(name, data, 0, data.length);
    }
    byte[] getClassData(String name) {
        try {
           String TempName = name.replaceAll("\\.", "/");
           String currentDir = System.getProperty("test.classes");
           String filename = currentDir + File.separator + TempName + ".class";
           FileInputStream fis = new FileInputStream(filename);
           byte[] b = new byte[5000];
           int cnt = fis.read(b, 0, 5000);
           byte[] c = new byte[cnt];
           for (int i=0; i<cnt; i++) c[i] = b[i];
              return c;
        } catch (IOException e) {
           return null;
        }
    }

    public void register(ModuleReference mref) { }
}

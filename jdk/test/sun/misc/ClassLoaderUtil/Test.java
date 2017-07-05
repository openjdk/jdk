/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6349735
 * @summary  Internal API for closing jar files belonging to URLClassLoader instances
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class Test {

    static File copy (File src, String destDir) throws Exception {
        FileInputStream fis = new FileInputStream (src);
        File dest = new File (destDir, src.getName());
        FileOutputStream fos = new FileOutputStream (dest);
        byte buf[] = new byte [1024];
        int c;
        while ((c=fis.read(buf)) != -1) {
            fos.write (buf, 0, c);
        }
        fis.close();
        fos.close();
        return dest;
    }

    public static void main(String[] args) throws Exception {
        String srcPath = System.getProperty("test.src");
        String destPath = System.getProperty("test.classes");
        if (destPath == null || "".equals(destPath)) {
            throw new RuntimeException ("Not running test");
        }
        File file = new File (srcPath, "test.jar");
        file = copy (file, destPath);
        URL url = file.toURL();
        URLClassLoader loader = new URLClassLoader (new URL [] {url});
        Class clazz = Class.forName ("Foo", true,  loader);
        Object obj = clazz.newInstance();
        List<String> jarsclosed = new LinkedList<String>();
        sun.misc.ClassLoaderUtil.releaseLoader (loader, jarsclosed);
        for (String jar: jarsclosed) {
            System.out.println ("Successfully closed " + jar);
        }
        if (!file.delete()) {
            throw new RuntimeException ("failed to delete jar file");
        }
    }
}

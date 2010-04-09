/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


/*
 * @test
 * @bug 6942649
 * @summary add hidden option to identify location and version of javac classes
 */

import java.io.*;

public class T6942649 {
    public static void main(String... args) throws Exception {
        new T6942649().run();
    }

    void run() throws Exception {
        test("-XDshowClass", "com.sun.tools.javac.Main");
        test("-XDshowClass=com.sun.tools.javac.util.Log", "com.sun.tools.javac.util.Log");
    }

    void test(String opt, String clazz) throws Exception {
        System.err.println("test " + opt);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(new String[] { opt }, pw);
        pw.close();
        String out = sw.toString();
        System.err.println("javac rc=" + rc + "\n" + out);
        if (!out.contains(clazz))
            throw new Exception("class name not found in output");
        int lastDot = clazz.lastIndexOf(".");
        if (!out.contains(clazz.substring(lastDot + 1) + ".class"))
            throw new Exception("location of class not found in output");
        if (!out.contains("MD5 checksum: "))
            throw new Exception("checksum not found in output");
    }
}

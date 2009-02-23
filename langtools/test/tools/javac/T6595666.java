/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6595666
 * @summary fix -Werror
 */

import java.io.*;
import java.util.*;

public class T6595666 {
    void m() {
        // the following line must create warnings with -Xlint, because of unchecked conversion
        List<Integer> list = new ArrayList();
    }

    public static void main(String... args) throws Exception {
        File testSrc = new File(System.getProperty("test.src", "."));

        String basename = T6595666.class.getName();
        File srcFile = new File(testSrc, basename+".java");
        File classFile = new File(basename+".class");
        classFile.delete();
        if (classFile.exists())
            throw new Exception("setup error, can't delete " + classFile);

        compile(1, "-d", ".", "-Xlint", "-Werror", srcFile.getPath());
        if (classFile.exists())
            throw new Exception("failed: found " + classFile);

        compile(0, "-d", ".", "-Xlint", srcFile.getPath());
        if (!classFile.exists())
            throw new Exception("failed: " + classFile + " not found");
    }

    private static void compile(int rc, String... args) throws Exception {
        System.err.println("compile: " + Arrays.asList(args));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc2 = com.sun.tools.javac.Main.compile(args, pw);
        pw.close();
        System.err.println(sw);
        if (rc != rc2)
            throw new Exception("bad exit code; expected " + rc + ", found " + rc2);
    }
}

/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8361481
 * @modules jdk.compiler
 * @summary Flexible Constructor Bodies generates a compilation error when compiling a user supplied java.lang.Object class
 */

import java.io.*;
import java.util.*;

public class T8361481 {
    static String testSrc = System.getProperty("test.src", ".");

    public static void main(String... args) throws Exception {
        new T8361481().run();
    }

    public void run() throws Exception {
        // compile modified Object.java, using patch-module to avoid errors
        File x = new File(testSrc, "x");
        String[] jcArgs = { "-d", ".", "--patch-module", "java.base=" + x.getAbsolutePath(),
                new File(new File(new File(x, "java"), "lang"), "Object.java").getPath()};
        compile(jcArgs);
    }

    void compile(String... args) {
        int rc = com.sun.tools.javac.Main.compile(args);
        if (rc != 0)
            throw new Error("javac failed: " + Arrays.asList(args) + ": " + rc);
    }
}


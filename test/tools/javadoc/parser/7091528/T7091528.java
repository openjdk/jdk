/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     7091528
 * @summary javadoc attempts to parse .class files
 * @compile p/C1.java p/q/C2.java
 * @run main T7091528
 */

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

public class T7091528 {
    public static void main(String... args) {
        new T7091528().run();
    }

    void run() {
        File testSrc = new File(System.getProperty("test.src"));
        File testClasses = new File(System.getProperty("test.classes"));
        String[] args = {
            "-d", ".",
            "-sourcepath", testClasses + File.pathSeparator + testSrc,
            "-subpackages",
            "p"
        };

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String doclet = com.sun.tools.doclets.standard.Standard.class.getName();
        int rc = com.sun.tools.javadoc.Main.execute("javadoc", pw, pw, pw, doclet, args);
        pw.close();

        String out = sw.toString();
        if (!out.isEmpty()) {
            System.err.println(out);
        }

        if (rc != 0)
            System.err.println("javadoc failed: exit code = " + rc);

        if (out.matches("(?s).*p/[^ ]+\\.class.*"))
            throw new Error("reading .class files");

        if (!new File("index.html").exists())
            throw new Error("index.html not found");
    }
}

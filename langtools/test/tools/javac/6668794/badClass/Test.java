/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * @bug 6668794 6668796
 * @summary javac puts localized text in raw diagnostics
 *      bad diagnostic "bad class file" given for source files
 */

import java.io.*;
import java.util.*;
import javax.tools.*;

public class Test {
    public static void main(String[] args) throws Exception {
        new Test().run();
    }

    void run() throws Exception {

        // compile q.A then move it to p.A
        compile("A.java");

        File p = new File("p");
        p.mkdirs();
        new File("q/A.class").renameTo(new File("p/A.class"));

        // compile B against p.A
        String[] out = compile("B.java");
        if (out.length == 0)
            throw new Error("no diagnostics generated");

        String expected = "B.java:6:6: compiler.err.cant.access: p.A, " +
            "(compiler.misc.bad.class.file.header: A.class, " +
            "(compiler.misc.class.file.wrong.class: q.A))";

        if (!out[0].equals(expected)) {
            System.err.println("expected: " + expected);
            System.err.println("   found: " + out[0]);
            throw new Error("test failed");
        }
    }

    String[] compile(String file) {
        String[] options = {
            "-XDrawDiagnostics",
            "-d", ".",
            "-classpath", ".",
            new File(testSrc, file).getPath()
        };

        System.err.println("compile: " + Arrays.asList(options));
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(options, out);
        out.close();

        String outText = sw.toString();
        System.err.println(outText);

        return sw.toString().split("[\\r\\n]+");
    }

    File testSrc = new File(System.getProperty("test.src", "."));
}

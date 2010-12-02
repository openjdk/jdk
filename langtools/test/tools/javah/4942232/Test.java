/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4942232
 * @summary missing param class processes without error
 * @build ParamClassTest Test
 * @run main Test
 */

import java.io.*;
import java.util.*;

public class Test {
    public static void main(String... args) throws Exception {
        new Test().run();
    }

    void run() throws Exception {
        File testSrc = new File(System.getProperty("test.src"));
        File testClasses = new File(System.getProperty("test.classes"));

        // standard use of javah on valid class file
        String[] test1Args = {
            "-d", mkdir("test1/out").getPath(),
            "-classpath", testClasses.getPath(),
            "ParamClassTest"
        };
        test(test1Args, 0);

        // extended use of javah on valid source file
        String[] test2Args = {
            "-d", mkdir("test2/out").getPath(),
            "-classpath", testSrc.getPath(),
            "ParamClassTest"
        };
        test(test2Args, 0);

        // javah on class file with missing referents
        File test3Classes = mkdir("test3/classes");
        copy(new File(testClasses, "ParamClassTest.class"), test3Classes);
        String[] test3Args = {
            "-d", mkdir("test3/out").getPath(),
            "-classpath", test3Classes.getPath(),
            "ParamClassTest"
        };
        test(test3Args, 1);

        // javah on source file with missing referents
        File test4Src = mkdir("test4/src");
        String paramClassTestSrc = readFile(new File(testSrc, "ParamClassTest.java"));
        writeFile(new File(test4Src, "ParamClassTest.java"),
                paramClassTestSrc.replaceAll("class Param \\{\\s+\\}", ""));
        String[] test4Args = {
            "-d", mkdir("test4/out").getPath(),
            "-classpath", test4Src.getPath(),
            "ParamClassTest"
        };
        test(test4Args, 15);

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    void test(String[] args, int expect) {
        System.err.println("test: " + Arrays.asList(args));
        int rc = javah(args);
        if (rc != expect)
            error("Unexpected return code: " + rc + "; expected: " + expect);
    }

    int javah(String... args) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javah.Main.run(args, pw);
        pw.close();
        String out = sw.toString();
        if (!out.isEmpty())
            System.err.println(out);
        return rc;
    }

    File mkdir(String path) {
        File f = new File(path);
        f.mkdirs();
        return f;
    }

    void copy(File from, File to) throws IOException {
        if (to.isDirectory())
            to = new File(to, from.getName());
        try (DataInputStream in = new DataInputStream(new FileInputStream(from));
                FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[(int) from.length()];
            in.readFully(buf);
            out.write(buf);
        }
    }

    String readFile(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[(int) f.length()];
            in.readFully(buf);
            return new String(buf);
        }
    }

    void writeFile(File f, String body) throws IOException {
        try (FileWriter out = new FileWriter(f)) {
            out.write(body);
        }
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;
}

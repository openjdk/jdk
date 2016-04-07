/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7126832
 * @modules jdk.compiler/com.sun.tools.javah
 * @compile java.java
 * @summary com.sun.tools.javac.api.ClientCodeWrapper$WrappedJavaFileManager cannot be cast
 * @run main T7126832
 */

import java.io.*;
import java.util.*;

public class T7126832 {
    public static void main(String... args) throws Exception {
        new T7126832().run();
    }

    void run() throws Exception {
        Locale prev = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            // Verify that a .java file is correctly diagnosed
            File ff = writeFile(new File("JavahTest.java"), "class JavahTest {}");
            test(Arrays.asList(ff.getPath()), 1, "Could not find class file for 'JavahTest.java'.");

            // Verify that a class named 'xx.java' is accepted.
            // Note that ./xx/java.class exists, so this should work ok
            test(Arrays.asList("xx.java"), 0, null);

            if (errors > 0) {
                throw new Exception(errors + " errors occurred");
            }
        } finally {
           Locale.setDefault(prev);
        }
    }

    void test(List<String> args, int expectRC, String expectOut) {
        System.err.println("Test: " + args
                + " rc:" + expectRC
                + ((expectOut != null) ? " out:" + expectOut : ""));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = 0;
        String out = null;
        try {
            rc = com.sun.tools.javah.Main.run(args.toArray(new String[args.size()]), pw);
            out = sw.toString();
        } catch(Exception ee) {
            rc = 1;
            out = ee.toString();;
        }
        pw.close();
        if (!out.isEmpty()) {
            System.err.println(out);
        }
        if (rc != expectRC) {
            error("Unexpected exit code: " + rc + "; expected: " + expectRC);
        }
        if (expectOut != null && !out.contains(expectOut)) {
            error("Expected string not found: " + expectOut);
        }

        System.err.println();
    }

    File writeFile(File ff, String ss) throws IOException {
        if (ff.getParentFile() != null)
            ff.getParentFile().mkdirs();

        try (FileWriter out = new FileWriter(ff)) {
            out.write(ss);
        }
        return ff;
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;
}


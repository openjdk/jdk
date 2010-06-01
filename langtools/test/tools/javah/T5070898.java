/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5070898
 * @summary javah command doesn't throw correct exit code in case of error
 */

import java.io.*;
import java.util.*;
import javax.tools.*;

public class T5070898
{
    public static void main(String... args) throws Exception {
        new T5070898().run();
    }

    public void run() throws Exception {
        writeFile();
        compileFile();

        int rc = runJavah();
        System.err.println("exit code: " + rc);
        if (rc == 0)
            throw new Exception("unexpected exit code: " + rc);
    }

    void writeFile() throws Exception {
        String content =
            "package test;\n" +
            "public class JavahTest{\n" +
            "    public static void main(String args){\n" +
            "        System.out.println(\"Test Message\");" +
            "    }\n" +
            "    private static native Object nativeTest();\n" +
            "}\n";
        FileWriter out = new FileWriter("JavahTest.java");
        try {
            out.write(content);
        } finally {
            out.close();
        }
    }

    void compileFile() throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        int rc = javac.run(null, null, null, "JavahTest.java");
        if (rc != 0)
            throw new Exception("compilation failed");
    }

    int runJavah() throws Exception {
        List<String> cmd = new ArrayList<String>();
        File java_home = new File(System.getProperty("java.home"));
        if (java_home.getName().equals("jre"))
            java_home = java_home.getParentFile();
        cmd.add(new File(new File(java_home, "bin"), "javah").getPath());

        // ensure we run with the same bootclasspath as this test,
        // in case this test is being run with -Xbootclasspath
        cmd.add("-J-Xbootclasspath:" + System.getProperty("sun.boot.class.path"));

        cmd.add("JavahTest");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().remove("CLASSPATH");
        Process p = pb.start();
        p.getOutputStream().close();

        String line;
        DataInputStream in = new DataInputStream(p.getInputStream());
        try {
        while ((line = in.readLine()) != null)
            System.err.println(line);
        } finally {
            in.close();
        }

        return p.waitFor();
    }
}

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
 * @bug 6893943 6937318
 * @summary exit code from javah with no args is 0
 */

import java.io.*;
import java.util.*;

public class T6893943 {
    static final String[] NO_ARGS = { };
    static final String[] HELP = { "-help" };
    static final String NEWLINE = System.getProperty("line.separator");

    public static void main(String... args) throws Exception {
        new T6893943().run();
    }

    void run() throws Exception {
        testSimpleAPI(NO_ARGS, 1);
        testSimpleAPI(HELP, 0);
        testCommand(NO_ARGS, 1);
        testCommand(HELP, 0);
    }

    void testSimpleAPI(String[] args, int expect_rc) throws Exception {
        System.err.println("Test simple api: " + Arrays.asList(args));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javah.Main.run(args, pw);
        pw.close();
        expect("testSimpleAPI", sw.toString(), rc, expect_rc);
    }

    void testCommand(String[] args, int expect_rc) throws Exception {
        System.err.println("Test command: " + Arrays.asList(args));
        File javaHome = new File(System.getProperty("java.home"));
        if (javaHome.getName().equals("jre"))
            javaHome = javaHome.getParentFile();

        List<String> command = new ArrayList<String>();
        command.add(new File(new File(javaHome, "bin"), "javah").getPath());
        command.add("-J-Xbootclasspath:" + System.getProperty("sun.boot.class.path"));
        command.addAll(Arrays.asList(args));
        //System.err.println("command: " + command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().close();
        StringWriter sw = new StringWriter();
        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while ((line = in.readLine()) != null)
            sw.write(line + NEWLINE);
        int rc = p.waitFor();
        expect("testCommand", sw.toString(), rc, expect_rc);
    }

    void expect(String name, String out, int actual_rc, int expect_rc) throws Exception {
        if (out.isEmpty())
            throw new Exception("No output from javah");

        if (!out.startsWith("Usage:")) {
            System.err.println(out);
            throw new Exception("Unexpected output from javah");
        }

        if (actual_rc != expect_rc)
            throw new Exception(name + ": unexpected exit: " + actual_rc + ", expected: " + expect_rc);
    }
}

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
 * @bug 6893943
 * @summary exit code from javah with no args is 0
 */

import java.io.*;
import java.util.*;

public class T6893943 {
    public static void main(String... args) throws Exception {
        new T6893943().run();
    }

    void run() throws Exception {
        testSimpleAPI();
        testCommand();
    }

    void testSimpleAPI() throws Exception {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.err));
        int rc = com.sun.tools.javah.Main.run(new String[] { }, pw);
        expect("testSimpleAPI", rc, 1);
    }

    void testCommand() throws Exception {
        File javaHome = new File(System.getProperty("java.home"));
        if (javaHome.getName().equals("jre"))
            javaHome = javaHome.getParentFile();

        List<String> command = new ArrayList<String>();
        command.add(new File(new File(javaHome, "bin"), "javah").getPath());
        command.add("-J-Xbootclasspath:" + System.getProperty("sun.boot.class.path"));
        //System.err.println("command: " + command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().close();
        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while ((line = in.readLine()) != null)
            System.err.println("javah: " + line);
        int rc = p.waitFor();
        expect("testCommand", rc, 1);
    }

    void expect(String name, int actual, int expect) throws Exception {
        if (actual != expect)
            throw new Exception(name + ": unexpected exit: " + actual + ", expected: " + expect);
    }
}

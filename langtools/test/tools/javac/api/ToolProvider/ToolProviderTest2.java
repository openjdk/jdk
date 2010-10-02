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
 * @bug 6604599
 * @summary ToolProvider should be less compiler-specific
 */

import java.io.*;
import javax.tools.*;

// control for ToolProviderTest1 -- verify that using ToolProvider to
// access the compiler does trigger loading com.sun.tools.javac.*
public class ToolProviderTest2 {
    public static void main(String... args) throws Exception {
        if (args.length > 0) {
            System.err.println(ToolProvider.getSystemJavaCompiler());
            return;
        }

        new ToolProviderTest2().run();
    }

    void run() throws Exception {
        File javaHome = new File(System.getProperty("java.home"));
        if (javaHome.getName().equals("jre"))
            javaHome = javaHome.getParentFile();
        File javaExe = new File(new File(javaHome, "bin"), "java");
        String classpath = System.getProperty("java.class.path");

        String[] cmd = {
            javaExe.getPath(),
            "-verbose:class",
            "-classpath", classpath,
            ToolProviderTest2.class.getName(),
            "javax.tools.ToolProvider"
        };

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        boolean found = false;
        while ((line = r.readLine()) != null) {
            System.err.println(line);
            if (line.contains("com.sun.tools.javac."))
                found = true;
        }
        int rc = p.waitFor();
        if (rc != 0)
            error("Unexpected exit code: " + rc);

        if (!found)
            System.err.println("expected class name not found");

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;
}

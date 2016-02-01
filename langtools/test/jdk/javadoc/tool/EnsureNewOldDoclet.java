/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8035473
 * @summary make sure the new doclet is invoked by default, and -Xold
 */

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Dummy javadoc comment.
 */
public class EnsureNewOldDoclet {

    final File javadoc;
    final File testSrc;
    final String thisClassName;

    final static Pattern Expected1 = Pattern.compile("^Standard Doclet \\(Next\\) version.*");
    final static Pattern Expected2 = Pattern.compile("^Standard Doclet version.*");

    public EnsureNewOldDoclet() {
        File javaHome = new File(System.getProperty("java.home"));
        if (javaHome.getName().endsWith("jre"))
            javaHome = javaHome.getParentFile();
        javadoc = new File(new File(javaHome, "bin"), "javadoc");
        testSrc = new File(System.getProperty("test.src"));
        thisClassName = EnsureNewOldDoclet.class.getName();
    }

    public static void main(String... args) throws Exception {
        EnsureNewOldDoclet test = new EnsureNewOldDoclet();
        test.run1();
        test.run2();
    }

    // make sure new doclet is invoked by default
    void run1() throws Exception {
        List<String> output = doTest(javadoc.getPath(),
                "-J-Xbootclasspath:" + System.getProperty("sun.boot.class.path"),
                "-classpath", ".", // insulates us from ambient classpath
                "-Xdoclint:none",
                "-package",
                new File(testSrc, thisClassName + ".java").getPath());
        System.out.println(output);
        for (String x : output) {
            if (Expected1.matcher(x).matches()) {
                return;
            }
        }
        throw new Exception("run1: Expected string not found:");
    }

    // make sure the old doclet is invoked with -Xold
    void run2() throws Exception {
        List<String> output = doTest(javadoc.getPath(),
                "-Xold",
                "-J-Xbootclasspath:" + System.getProperty("sun.boot.class.path"),
                "-classpath", ".", // insulates us from ambient classpath
                "-Xdoclint:none",
                "-package",
                new File(testSrc, thisClassName + ".java").getPath());

        for (String x : output) {
            if (Expected2.matcher(x).matches()) {
                throw new Exception("run2: Expected string not found");
            }
            return;
        }
    }

    /**
     * More dummy comments.
     */
    List<String> doTest(String... args) throws Exception {
        List<String> output = new ArrayList<>();
        // run javadoc in separate process to ensure doclet executed under
        // normal user conditions w.r.t. classloader
        Process p = new ProcessBuilder()
                .command(args)
                .redirectErrorStream(true)
                .start();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = in.readLine();
            while (line != null) {
                output.add(line.trim());
                line = in.readLine();
            }
        }
        int rc = p.waitFor();
        if (rc != 0)
            throw new Exception("javadoc failed, rc:" + rc);
        return output;
    }
}

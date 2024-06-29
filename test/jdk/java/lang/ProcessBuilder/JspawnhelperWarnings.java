/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8325567 8325621
 * @requires (os.family == "linux") | (os.family == "aix") | (os.family == "mac")
 * @library /test/lib
 * @run driver JspawnhelperWarnings
 */

import java.nio.file.Paths;
import java.util.Arrays;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class JspawnhelperWarnings {

    private static void tryWithNArgs(int nArgs) throws Exception {
        System.out.println("Running jspawnhelper with " + nArgs + " args");
        String[] args = new String[nArgs + 1];
        Arrays.fill(args, "1");
        args[0] = Paths.get(System.getProperty("java.home"), "lib", "jspawnhelper").toString();
        Process p = ProcessTools.startProcess("jspawnhelper", new ProcessBuilder(args));
        OutputAnalyzer oa = new OutputAnalyzer(p);
        oa.shouldHaveExitValue(1);
        oa.shouldContain("This command is not for general use");
        if (nArgs != 2) {
            oa.shouldContain("Incorrect number of arguments");
        } else {
            oa.shouldContain("Incorrect Java version");
        }
    }

    private static void testVersion() throws Exception {
        String[] args = new String[3];
        args[0] = Paths.get(System.getProperty("java.home"), "lib", "jspawnhelper").toString();
        args[1] = "wrongVersion";
        args[2] = "1:1:1";
        Process p = ProcessTools.startProcess("jspawnhelper", new ProcessBuilder(args));
        OutputAnalyzer oa = new OutputAnalyzer(p);
        oa.shouldHaveExitValue(1);
        oa.shouldContain("This command is not for general use");
        oa.shouldContain("Incorrect Java version: wrongVersion");
    }

    public static void main(String[] args) throws Exception {
        for (int nArgs = 0; nArgs < 10; nArgs++) {
            tryWithNArgs(nArgs);
        }

        testVersion();
    }
}

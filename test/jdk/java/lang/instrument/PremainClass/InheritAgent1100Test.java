/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 6289149
 * @summary test config (1,1,0,0): inherited 2-arg and inherited 1-arg in agent class
 *
 * @library /test/lib
 * @modules java.instrument
 * @build DummyMain InheritAgent1100 InheritAgent1100Super InheritAgent1100Test
 * @run shell MakeJAR.sh PremainClass InheritAgent1100 InheritAgent1100Super
 * @run main/othervm InheritAgent1100Test
 */
public class InheritAgent1100Test {
    // Use a javaagent without the premain() method but inherited from super class.
    // Verify that we get the correct exception.
    public static void main(String[] a) throws Exception {
        String testArgs = String.format(
                "-javaagent:InheritAgent1100.jar -classpath %s DummyMain",
                System.getProperty("test.classes", "."));

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                Utils.addTestJavaOpts(testArgs.split("\\s+")));
        System.out.println("testjvm.cmd:" + Utils.getCommandLine(pb));

        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        System.out.println("testjvm.stdout:" + output.getStdout());
        System.out.println("testjvm.stderr:" + output.getStderr());

        output.stderrShouldContain("java.lang.NoSuchMethodException");
        if (0 == output.getExitValue()) {
            throw new RuntimeException("Expected error but got exit value 0");
        }
    }
}

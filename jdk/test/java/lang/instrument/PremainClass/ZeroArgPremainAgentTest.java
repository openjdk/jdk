/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.Utils;

/*
 * @test
 * @bug 6289149
 * @summary test when the agent's class has a zero arg premain() function.
 * @library /lib/testlibrary
 * @run build jdk.testlibrary.* DummyMain
 * @run shell ../MakeJAR3.sh ZeroArgPremainAgent
 * @run main ZeroArgPremainAgentTest
 */
public class ZeroArgPremainAgentTest {
    // Use a javaagent with a zero argument premain() function.
    // Verify that we get the correct exception.
    public static void main(String[] a) throws Exception {
        String testArgs = String.format(
                "-javaagent:ZeroArgPremainAgent.jar -classpath %s DummyMain",
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

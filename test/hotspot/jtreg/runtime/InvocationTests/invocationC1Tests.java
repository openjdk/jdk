/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test id=special
 * @bug 8226956
 * @summary Run invocation tests against C1 compiler
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @compile invokespecial/Checker.java invokespecial/ClassGenerator.java invokespecial/Generator.java
 *
 * @run driver/timeout=1800 invocationC1Tests special
 */

/*
 * @test id=virtual
 * @bug 8226956
 * @summary Run invocation tests against C1 compiler
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @compile invokevirtual/Checker.java invokevirtual/ClassGenerator.java invokevirtual/Generator.java
 *
 * @run driver/timeout=1800 invocationC1Tests virtual
 */

/*
 * @test id=interface
 * @bug 8226956
 * @summary Run invocation tests against C1 compiler
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @compile invokeinterface/Checker.java invokeinterface/ClassGenerator.java invokeinterface/Generator.java
 *
 * @run driver/timeout=1800 invocationC1Tests interface
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

public class invocationC1Tests {

    public static void runTest(String whichTests, String classFileVersion) throws Throwable {
        System.out.println("\nC1 invocation tests, Tests: " + whichTests +
                           ", class file version: " + classFileVersion);
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xmx128M",
            "-Xcomp", "-XX:TieredStopAtLevel=1",
            "--add-exports", "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
            whichTests, "--classfile_version=" + classFileVersion);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        try {
            output.shouldContain("EXECUTION STATUS: PASSED");
            output.shouldHaveExitValue(0);
        } catch (Throwable e) {
            System.out.println(
                "\nNote that an entry such as 'B.m/C.m' in the failure chart means that" +
                " the test case failed because method B.m was invoked but the test " +
                "expected method C.m to be invoked. Similarly, a result such as 'AME/C.m'" +
                " means that an AbstractMethodError exception was thrown but the test" +
                " case expected method C.m to be invoked.");
            System.out.println(
                "\nAlso note that passing --dump to invoke*.Generator will" +
                " dump the generated classes (for debugging purposes).\n");

            throw e;
        }
    }

    public static void main(String args[]) throws Throwable {
        if (args.length < 1) {
            throw new IllegalArgumentException("Should provide the test name");
        }
        String testName = args[0];

        // Get current major class file version and test with it.
        byte klassbuf[] = InMemoryJavaCompiler.compile("blah", "public class blah { }");
        int major_version = klassbuf[6] << 8 | klassbuf[7];

        switch (testName) {
            case "special":
                runTest("invokespecial.Generator", String.valueOf(major_version));
                break;
            case "virtual":
                runTest("invokevirtual.Generator", String.valueOf(major_version));
                break;
            case "interface":
                runTest("invokeinterface.Generator", String.valueOf(major_version));
                break;
            default:
                throw new IllegalArgumentException("Unknown test name: " + testName);
        }
    }
}

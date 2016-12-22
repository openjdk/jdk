/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 * @requires vm.bits == "64" & os.arch == "amd64" & os.family == "linux"
 * @build compiler.aot.cli.SingleAOTOptionTest
 *        compiler.aot.AotCompiler
 * @run driver compiler.aot.AotCompiler -libname libSingleAOTOptionTest.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.print()V
 *      -extraopt -XX:+UseCompressedOops
 * @run driver compiler.aot.cli.SingleAOTOptionTest -XX:+UseCompressedOops
 *      -XX:AOTLibrary=./libSingleAOTOptionTest.so
 * @run main compiler.aot.cli.SingleAOTOptionTest
 *      -XX:+UseCompressedOops -XX:+UseAOT
 * @run driver compiler.aot.AotCompiler -libname libSingleAOTOptionTest.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.print()V
 *      -extraopt -XX:-UseCompressedOops
 * @run driver compiler.aot.cli.SingleAOTOptionTest -XX:-UseCompressedOops
 *      -XX:AOTLibrary=./libSingleAOTOptionTest.so
 * @run driver compiler.aot.cli.SingleAOTOptionTest
 *      -XX:-UseCompressedOops -XX:+UseAOT
 * @summary check if specifying only one aot option handled properly
 */

package compiler.aot.cli;

import compiler.aot.HelloWorldPrinter;
import jdk.test.lib.process.ExitCode;
import jdk.test.lib.cli.CommandLineOptionTest;

public class SingleAOTOptionTest {
    private static final String[] EXPECTED_MESSAGES = new String[] {
        HelloWorldPrinter.MESSAGE
    };
    private static final String[] UNEXPECTED_MESSAGES = null;

    public static void main(String args[]) {
        if (args.length == 2) {
            new SingleAOTOptionTest().runTest(args[0], args[1]);
        } else {
            throw new Error("Test expects 2 parameters");
        }
    }

    private void runTest(String arg1, String arg2) {
        try {
            String exitCodeErrorMessage = String.format("Unexpected exit code "
                    + "using %s and %s", arg1, arg2);
            String outputErrorMessage = String.format("Unexpected output using"
                    + " %s and %s", arg1, arg2);
            boolean addTestVMOptions = true;
            CommandLineOptionTest.verifyJVMStartup(EXPECTED_MESSAGES,
                    UNEXPECTED_MESSAGES, exitCodeErrorMessage,
                    outputErrorMessage, ExitCode.OK, addTestVMOptions, arg1,
                    arg2, HelloWorldPrinter.class.getName());
        } catch (Throwable t) {
            throw new Error("Problems executing test: " + t, t);
        }
    }

}

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
 * @library /test/lib / /testlibrary
 * @modules java.base/jdk.internal.misc
 * @requires vm.bits == "64" & os.arch == "amd64" & os.family == "linux"
 * @build compiler.aot.cli.SingleAOTLibraryTest
 *        compiler.aot.AotCompiler
 * @run driver compiler.aot.AotCompiler -libname libSingleAOTLibraryTest.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.print()V
 *      -extraopt -XX:+UseCompressedOops
 * @run driver compiler.aot.cli.SingleAOTLibraryTest -XX:+UseCompressedOops
 * @run driver compiler.aot.AotCompiler -libname libSingleAOTLibraryTest.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.print()V
 *      -extraopt -XX:-UseCompressedOops
 * @run driver compiler.aot.cli.SingleAOTLibraryTest -XX:-UseCompressedOops
 * @summary check if single aot library is loaded successfully
 */

package compiler.aot.cli;

import compiler.aot.HelloWorldPrinter;
import jdk.test.lib.process.ExitCode;
import jdk.test.lib.cli.CommandLineOptionTest;

public final class SingleAOTLibraryTest {
    private static final String[] EXPECTED_MESSAGES = new String[] {
        "libSingleAOTLibraryTest.so  aot library",
        HelloWorldPrinter.MESSAGE
    };
    private static final String[] UNEXPECTED_MESSAGES = null;
    public static void main(String args[]) {
        if (args.length == 1) {
            new SingleAOTLibraryTest().runTest(args[0]);
        } else {
            throw new Error("Test expects 1 parameter");
        }
    }

    private void runTest(String arg) {
        try {
            boolean addTestVMOptions = true;
            CommandLineOptionTest.verifyJVMStartup(EXPECTED_MESSAGES,
                    UNEXPECTED_MESSAGES, "Unexpected exit code using " + arg,
                    "Unexpected output using " + arg, ExitCode.OK,
                    addTestVMOptions, "-XX:+UseAOT", "-XX:+PrintAOT", arg,
                    "-XX:AOTLibrary=./libSingleAOTLibraryTest.so",
                    HelloWorldPrinter.class.getName());
        } catch (Throwable t) {
            throw new Error("Problems executing test: " + t, t);
        }
    }
}

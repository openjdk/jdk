/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.aot
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 * @build compiler.aot.cli.MultipleAOTLibraryTest
 *        compiler.aot.AotCompiler
 * @run driver compiler.aot.AotCompiler
 *      -libname libMultipleAOTLibraryTest1.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.*
 *      -extraopt -XX:+UseCompressedOops
 * @run driver compiler.aot.AotCompiler
 *      -libname libMultipleAOTLibraryTest2.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.print()V
 *      -extraopt -XX:+UseCompressedOops
 * @run driver compiler.aot.cli.MultipleAOTLibraryTest -XX:+UseCompressedOops
 * @run driver compiler.aot.AotCompiler -libname libMultipleAOTLibraryTest1.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.*
 *      -extraopt -XX:-UseCompressedOops
 * @run driver compiler.aot.AotCompiler -libname libMultipleAOTLibraryTest2.so
 *      -class compiler.aot.HelloWorldPrinter
 *      -compile compiler.aot.HelloWorldPrinter.print()V
 *      -extraopt -XX:-UseCompressedOops
 * @run driver compiler.aot.cli.MultipleAOTLibraryTest -XX:-UseCompressedOops
 * @summary check if multiple aot libraries are loaded successfully
 */

package compiler.aot.cli;

import compiler.aot.HelloWorldPrinter;
import java.util.Arrays;
import jdk.test.lib.process.ExitCode;
import jdk.test.lib.cli.CommandLineOptionTest;

public final class MultipleAOTLibraryTest {
    private final static String EXPECTED_OUTPUT[] = new String[] {
                "libMultipleAOTLibraryTest1.so  aot library",
                "libMultipleAOTLibraryTest2.so  aot library",
                HelloWorldPrinter.MESSAGE
    };
    private final static String UNEXPECTED_OUTPUT[] = null;

    public static void main(String args[]) {
        new MultipleAOTLibraryTest().runTest(args);
    }

    private void runTest(String args[]) {
        try {
            boolean addTestVMOptions = true;
            String[] allArgs = Arrays.copyOf(args, args.length + 4);
            allArgs[args.length] = "-XX:AOTLibrary="
                    + "./libMultipleAOTLibraryTest1.so:"
                    + "./libMultipleAOTLibraryTest2.so";
            allArgs[args.length + 1] = "-XX:+PrintAOT";
            allArgs[args.length + 2] = "-XX:+UseAOT";
            allArgs[args.length + 3] = HelloWorldPrinter.class.getName();
            CommandLineOptionTest.verifyJVMStartup(EXPECTED_OUTPUT,
                    UNEXPECTED_OUTPUT, "Unexpected exit code",
                    "Unexpected output", ExitCode.OK, addTestVMOptions,
                    allArgs);
        } catch (Throwable t) {
            throw new Error("Problems executing test: " + t, t);
        }
    }
}

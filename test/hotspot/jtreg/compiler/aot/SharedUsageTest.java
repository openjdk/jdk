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
 * @build compiler.aot.SharedUsageTest
 *        compiler.aot.AotCompiler
 * @run driver compiler.aot.AotCompiler -libname libSharedUsageTest.so
 *      -class compiler.aot.SharedUsageTest
 *      -extraopt -XX:-UseCompressedOops
 * @run main/othervm -XX:+UseAOT -XX:AOTLibrary=./libSharedUsageTest.so
 *      -XX:-UseCompressedOops
 *      -Dcompiler.aot.SharedUsageTest.parent=true
 *      compiler.aot.SharedUsageTest
 * @summary check if .so can be successfully shared with 2 java processes
 */

package compiler.aot;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ExitCode;
import jdk.test.lib.Utils;
import jdk.test.lib.cli.CommandLineOptionTest;

public class SharedUsageTest {
    private static final String HELLO_MSG = "HelloWorld";
    private static final boolean ADD_TEST_VM_OPTION = false;
    private static boolean shouldBeFalseInParent = false;
    private static final boolean IS_PARENT = Boolean.getBoolean(
        "compiler.aot.SharedUsageTest.parent");

    public static void main(String args[]) throws Throwable {
        Asserts.assertFalse(shouldBeFalseInParent,
                "A test invariant is broken");
        if (IS_PARENT) {
            /* An output of .so being used is verified after launch.
               A respective message is triggered by PrintAOT option. */
            CommandLineOptionTest.verifyJVMStartup(
                    new String[]{"libSharedUsageTest.so  aot library",
                        HELLO_MSG}, null, "Unexpected exit code",
                    "Unexpected output", ExitCode.OK, ADD_TEST_VM_OPTION,
                    "-XX:+UseAOT", "-XX:+PrintAOT",
                    "-Dtest.jdk=" + Utils.TEST_JDK,
                    "-XX:AOTLibrary=./libSharedUsageTest.so",
                    SharedUsageTest.class.getName());
            Asserts.assertFalse(shouldBeFalseInParent, "A static member got "
                    + "unexpectedly changed");
        } else {
            shouldBeFalseInParent = true;
            Asserts.assertTrue(shouldBeFalseInParent, "A static member wasn't"
                    + "changed as expected");
            System.out.println(HELLO_MSG);
        }
    }
}

/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8375548
 * @enablePreview
 * @library / /test/lib
 * @summary Testing that compiler replay correctly loads sub classes of ObjArrayKlass.
 * @requires vm.flagless & vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" &
 *           vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+TieredCompilation
 *                   compiler.ciReplay.TestValueClassArrays
 */

package compiler.ciReplay;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

public class TestValueClassArrays extends DumpReplayBase {
    private static final String LOG_FILE = "hotspot.log";
    private static final String ERROR_FILE = "error.log";
    private final PrintIdeal printIdeal;
    private final String[] defaultReplayRunFlags;

    public static void main(String[] args) {
        new TestValueClassArrays().runTest("-XX:CompileCommand=dontinline,*::*",
                                           "--enable-preview",
                                           "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                                           "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                                           TIERED_DISABLED_VM_OPTION);
    }

    private TestValueClassArrays() {
        printIdeal = new PrintIdeal(LOG_FILE);
        defaultReplayRunFlags = defaultReplayRunFlags();
    }


    private String[] defaultReplayRunFlags() {
        List<String> vmFlags = printIdeal.vmFlags();
        Collections.addAll(vmFlags,
                           "-XX:+ReplayIgnoreInitErrors",
                           "-XX:CompileCommand=dontinline,*::*",
                           "--enable-preview",
                           "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                           "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                           "-XX:ErrorFile=" + ERROR_FILE,
                           TIERED_DISABLED_VM_OPTION
        );
        return vmFlags.toArray(new String[0]);
    }

    @Override
    public void testAction() {
        testArrayAccessSpeculation();
        testNoProfilingNoSpeculation();
        testInvalidArrayProperty();
        remove(LOG_FILE);
        remove(ERROR_FILE);
    }

    /**
     * Check that we emit traps for speculation on array stores and loads as captured in the profiling.
     */
    private void testArrayAccessSpeculation() {
        positiveTest(defaultReplayRunFlags);
        printIdeal.parse();
        int countClassCheck = printIdeal.count("class_check");
        Asserts.assertEQ(countClassCheck, 8, "not found all class checks for speculation on array accesses");
    }

    /**
     * Check that we do not emit traps for speculation on array stores and loads since we disabled array profiling.
     */
    private void testNoProfilingNoSpeculation() {
        String[] noProfilingFlags = Arrays.copyOf(defaultReplayRunFlags, defaultReplayRunFlags.length + 1);
        noProfilingFlags[defaultReplayRunFlags.length] = "-XX:-UseArrayLoadStoreProfile";
        positiveTest(noProfilingFlags);
        printIdeal.parse();
        String empty = printIdeal.find("class_check");
        Asserts.assertTrue(empty.isEmpty(), "should not find UCTs for speculation without array profiling");
    }

    /**
     * Replace the "array property" in the "ciMethodData" with an invalid value -1 to make compiler replay fail
     * with an assertion.
     */
    private void testInvalidArrayProperty() {
        invalidateCiMethodDataInReplayFile();
        negativeTest(defaultReplayRunFlags);
        ErrorFile errorFile = new ErrorFile(ERROR_FILE);
        Asserts.assertTrue(errorFile.find("failed: invalid flags set"));
    }

    private void invalidateCiMethodDataInReplayFile() {
        ReplayFile replayFile = new ReplayFile(getReplayFileName());
        String ciMethodData = replayFile.findLineStartingWith(
                "ciMethodData compiler/ciReplay/TestValueClassArrays$Test test");
        Pattern pattern = Pattern.compile("(\\[Lcompiler/ciReplay/TestValueClassArrays\\$Test\\$A;) (\\d+)");
        Matcher matcher = pattern.matcher(ciMethodData);
        Asserts.assertTrue(matcher.find(), "must find array_property");
        String replacement = matcher.group(1) + " -1";
        String newCiMethodData = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        replayFile.replaceLineStartingWith(ciMethodData, newCiMethodData);
    }

    @Override
    public String getTestClass() {
        return Test.class.getName();
    }


    private static class Test {
        static A[] oArrDefault = new A[2];
        static A[] oArrNullableAtomicArray = (A[]) ValueClass.newNullableAtomicArray(A.class, 2);
        static A[] oArrNullRestrictedAtomicArray = (A[]) ValueClass.newNullRestrictedAtomicArray(A.class, 2, new A(1));
        static A[] oArrNullRestrictedNonAtomicArray = (A[]) ValueClass.newNullRestrictedNonAtomicArray(A.class, 2, new A(2));

        static A o1, o2, o3, o4;
        static A a = new A(10);

        public static void main(String[] args) {
            oArrDefault[0] = new A(3);
            oArrNullableAtomicArray[0] = new A(4);
            oArrNullRestrictedAtomicArray[0] = new A(5);
            oArrNullRestrictedNonAtomicArray[0] = new A(6);
            for (int i = 0; i < 10000; i++) {
                test();
            }
        }

        static void test() {
            o1 = oArrDefault[0];
            oArrDefault[1] = a;
            o2 = oArrNullableAtomicArray[0];
            oArrNullableAtomicArray[1] = a;
            o3 = oArrNullRestrictedAtomicArray[0];
            oArrNullRestrictedAtomicArray[1] = a;
            o4 = oArrNullRestrictedNonAtomicArray[0];
            oArrNullRestrictedNonAtomicArray[1] = a;
        }

        static value class A {
            int x;
            public A(int x) {
                this.x = x;
            }
        }
    }
}

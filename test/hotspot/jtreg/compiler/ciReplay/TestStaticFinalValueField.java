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
 * @enablePreview
 * @library / /test/lib
 * @summary Replaying compilation with value static final fields results in a crash
 * @requires vm.flagless & vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" &
 *           vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   ${test.main.class}
 */

package compiler.ciReplay;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestStaticFinalValueField extends DumpReplayBase {
    public static void main(String[] args) {
        runTest(TestNestedValueStaticField.class);
        runTest(TestNestedValueStaticField.class, "-XX:-UseFieldFlattening");

        runTest(TestValueArrayStaticField.class);
        runTest(TestValueArrayStaticField.class, "-XX:-UseFieldFlattening", "-XX:-UseArrayFlattening");
    }

    @LooselyConsistentValue
    static value class ValueClass1 {
        @NullRestricted
        ValueClass2 v1;

        static final ValueClass1 DEFAULT = new ValueClass1(ValueClass2.DEFAULT);

        ValueClass1(ValueClass2 v1) {
            this.v1 = v1;
        }
    }

    @LooselyConsistentValue
    static value class ValueClass2 {
        short d1;

        ValueClass2(int i) {
            this.d1 = (short)i;
        }

        static final ValueClass2 DEFAULT = new ValueClass2(0);
    }

    static class TestNestedValueStaticField {
        @NullRestricted
        static final ValueClass1 vc1FinalNR = ValueClass1.DEFAULT;

        @NullRestricted
        static ValueClass1 vc1NonFinalNR = ValueClass1.DEFAULT;

        static ValueClass1 vc1 = ValueClass1.DEFAULT;

        @NullRestricted
        static final ValueClass2 vc2FinalNR = ValueClass2.DEFAULT;

        @NullRestricted
        static final ValueClass2 vc2NonFinalNR = ValueClass2.DEFAULT;

        static ValueClass2 vc2 = ValueClass2.DEFAULT;

        public static void main(String[] args) {
            for (int i = 0; i < 20_000; i++) {
                test();
            }
        }

        public static void test() {}
    }

    static class TestValueArrayStaticField {
        static final ValueClass1[] va = (ValueClass1[]) ValueClass.newNullRestrictedNonAtomicArray(ValueClass1.class, 2, ValueClass1.DEFAULT);
        static final ValueClass2[] vb = (ValueClass2[]) ValueClass.newNullRestrictedNonAtomicArray(ValueClass2.class, 3, ValueClass2.DEFAULT);

        public static void main(String[] args) {
            for (int i = 0; i < 20_000; i++) {
                test();
            }
        }

        public static void test() {}
    }

    // ******************************************************************************************

    private final String[] jvmArgs;
    private final Class<?> testClass;

    private TestStaticFinalValueField(Class<?> testClass, String... jvmArgs) {
        this.testClass = testClass;
        this.jvmArgs = jvmArgs;
    }

    private String[] runJvmArgs() {
        List<String> options = new ArrayList<>();
        options.addAll(Arrays.asList("--enable-preview",
                "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED"));
        options.addAll(Arrays.asList(jvmArgs));
        options.add(TIERED_DISABLED_VM_OPTION);
        return options.toArray(new String[0]);
    }

    private String[] replayJvmArgs() {
        List<String> options = new ArrayList<>();
        options.addAll(Arrays.asList(runJvmArgs()));
        options.add("-XX:+ReplayIgnoreInitErrors");
        return options.toArray(new String[0]);
    }

    @Override
    public void testAction() {
        positiveTest(replayJvmArgs());
    }

    @Override
    public String getTestClass() {
        return testClass.getName();
    }

    static void runTest(Class<?> testClass, String... jvmArgs) {
        TestStaticFinalValueField testCase = new TestStaticFinalValueField(testClass, jvmArgs);
        testCase.runTest(testCase.runJvmArgs());
    }
}
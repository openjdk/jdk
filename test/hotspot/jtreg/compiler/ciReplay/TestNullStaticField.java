/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8333805
 * @library / /test/lib
 * @summary Replaying compilation with null static final fields results in a crash
 * @requires vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" & vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.ciReplay.TestNullStaticField
 */

package compiler.ciReplay;

public class TestNullStaticField extends DumpReplayBase {

    public static void main(String[] args) {
        new TestNullStaticField().runTest(TIERED_DISABLED_VM_OPTION);
    }

    @Override
    public void testAction() {
        positiveTest(TIERED_DISABLED_VM_OPTION, "-XX:+ReplayIgnoreInitErrors");
    }

    @Override
    public String getTestClass() {
        return TestClassNullStaticField.class.getName();
    }

}

class TestClassNullStaticField {

    static final Object[] staticNullArrayField = null;
    static final Object[][] staticNullMultiArrayField = null;
    static final Object staticNullObjectField = null;
    static final String staticNullStringField = null;
    static final int[] staticNullIntArrayField = null;
    static final Object[] staticNotNullArrayField = new A[10];
    static final Object[][] staticNotNullMultiArrayField = new A[10][10];
    static final Object staticNotNullObjectField = new A();
    static final String staticNotNullStringField = "Not null";
    static final int[] staticNotNullIntArrayField = new int[10];

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test();
        }
    }
    public static void test() {

    }

    private static class A {
    }
}


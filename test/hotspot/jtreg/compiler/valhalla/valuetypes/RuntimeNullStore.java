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

package compiler.valhalla.valuetypes;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.LooselyConsistentValue;

import jdk.test.lib.Asserts;
import jdk.test.lib.helpers.StrictInit;

import compiler.lib.ir_framework.CompLevel;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.ForceInline;

import compiler.valhalla.valuetypes.ValueTypes;

/*
 * @test
 * @summary Test C1 runtime stores to nullable non-atomic flat fields
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @requires os.simpleArch == "aarch64" | os.simpleArch == "x64"
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile RuntimeNullStore.java
 * @run driver jdk.test.lib.helpers.StrictProcessor compiler.valhalla.valuetypes.RuntimeNullStore$Holder
 * @run main/othervm compiler.valhalla.valuetypes.RuntimeNullStore
 */

@LooselyConsistentValue
value class FlatNonAtomicClass {
    // Four longs cannot use an atomic flat layout.
    public long a, b, c, d;

    FlatNonAtomicClass(long a, long b, long c, long d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }
}

public class RuntimeNullStore {
    static class Holder {
        // Strict init final required for the field's layout to be non-atomic flat
        @StrictInit
        final FlatNonAtomicClass value;

        // We need this putfield to be seen by C1 when compiling test below
        @ForceInline
        Holder(FlatNonAtomicClass value) {
            this.value = value;
            super();
        }
    }


    @Test(compLevel = CompLevel.C1_SIMPLE)
    public Holder test(FlatNonAtomicClass setToThis) {
        // We use the constructor to exercise the putfield compilation
        return new Holder(setToThis);
    }

    @Run(test = "test")
    public void test_verifier() throws NoSuchFieldException {
        // The value field in Holder must be flattened for this test
        // to work.
        var field = Holder.class.getDeclaredField("value");
        Unsafe unsafe = Unsafe.getUnsafe();
        Asserts.assertTrue(unsafe.isFlatField(field));
        Asserts.assertTrue(unsafe.hasNullMarker(field));

        // We construct the value and check that it seems to work
        FlatNonAtomicClass expected = new FlatNonAtomicClass(0L, 1L, 2L, 4L);
        Asserts.assertNull(test(null).value);

        FlatNonAtomicClass actual = test(expected).value;
        Asserts.assertNotNull(actual);
        Asserts.assertEQ(actual.a, expected.a);
        Asserts.assertEQ(actual.b, expected.b);
        Asserts.assertEQ(actual.c, expected.c);
        Asserts.assertEQ(actual.d, expected.d);

        Asserts.assertNull(test(null).value);
    }


    public static void main(String[] args) throws Exception {
        Scenario[] scenarios = {
            new Scenario(0,
                "-XX:+TieredCompilation",
                "-XX:TieredStopAtLevel=1")
        };
        ValueTypes.getFramework()
            .addScenarios(scenarios)
            .addFlags(
                "--enable-preview",
                "--add-exports",
                "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                "--add-exports",
                "java.base/jdk.internal.misc=ALL-UNNAMED",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+UseFieldFlattening",
                "-XX:+UseNullableNonAtomicValueFlattening",
                "-XX:+C1UseNonAtomicFlatFieldStoreRuntime",
                "-XX:+C1UseNonAtomicFlatFieldStoreRuntimeALot")
            .start();
    }

}

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

package jdk.jfr.event.oldobject;

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8389454
 * @summary Verify old-object paths through atomic and nullable flat layouts
 * @requires vm.hasJFR & vm.flagless & vm.bits == "64"
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          jdk.jfr/jdk.jfr.internal.test
 * @enablePreview
 * @build jdk.jfr.event.oldobject.TestFlatArrayInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:-UseNullFreeNonAtomicValueFlattening -XX:-UseNullableNonAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseNullableAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatAtomicInformation null-free
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:-UseNullFreeNonAtomicValueFlattening -XX:-UseNullableNonAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseNullableAtomicValueFlattening -XX:-UseCompressedOops jdk.jfr.event.oldobject.TestFlatAtomicInformation null-free
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:-UseNullFreeNonAtomicValueFlattening -XX:-UseNullableNonAtomicValueFlattening -XX:+UseNullFreeAtomicValueFlattening -XX:+UseNullableAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatAtomicInformation nullable
 */
public class TestFlatAtomicInformation {
    // A single reference fits the eight-byte atomic layout limit in both oop
    // modes. Adding a null marker fits only with compressed oops.
    static value class Value {
        final byte[] ref;

        Value(byte[] ref) {
            this.ref = ref;
        }
    }

    static class NullRestrictedHolder {
        @NullRestricted final Value value;

        NullRestrictedHolder(byte[] ref) {
            value = new Value(ref);
            super();
        }
    }

    static class NullableHolder {
        final Value value;

        NullableHolder(byte[] ref) {
            value = new Value(ref);
            super();
        }
    }

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        boolean nullable = args[0].equals("nullable");
        // Disabling both non-atomic layouts makes isFlatArray/isFlatField
        // sufficient to confirm that the selected layout is atomic.
        Object holder = nullable ? new NullableHolder(null) : new NullRestrictedHolder(null);
        Class<?> holderType = holder.getClass();
        Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(holderType.getDeclaredField("value")));
        int[] bounds = {0, TestFlatArrayInformation.LENGTH - 1};
        for (int index : bounds) {
            TestFlatArrayInformation.check("atomic array " + args[0] + " index=" + index,
                () -> allocateArray(nullable, index), Value[].class, ref -> {
                    RecordedObject value = TestFlatArrayInformation.verifyField(ref, "ref", Value.class, true);
                    TestFlatArrayInformation.verifyArray(value.getValue("referrer"), index, Value[].class);
                });
        }

        TestFlatArrayInformation.check("atomic field " + args[0],
            () -> {
                byte[] sample = new byte[OldObjects.MIN_SIZE];
                TestFlatArrayInformation.KEEP.add(nullable ? new NullableHolder(sample) : new NullRestrictedHolder(sample));
            }, holderType, ref -> {
                RecordedObject value = TestFlatArrayInformation.verifyField(ref, "ref", Value.class, true);
                TestFlatArrayInformation.verifyField(value.getValue("referrer"), "value", holderType, false);
            });
    }

    private static void allocateArray(boolean nullable, int index) {
        Value[] array = (Value[]) (nullable ? ValueClass.newNullableAtomicArray(Value.class, TestFlatArrayInformation.LENGTH)
            : ValueClass.newNullRestrictedAtomicArray(Value.class, TestFlatArrayInformation.LENGTH, new Value(null)));
        Asserts.assertTrue(ValueClass.isFlatArray(array), "Test requires a flat array");
        Asserts.assertTrue(ValueClass.isAtomicArray(array));
        Asserts.assertEquals(ValueClass.isNullRestrictedArray(array), !nullable);
        array[index] = new Value(new byte[OldObjects.MIN_SIZE]);
        TestFlatArrayInformation.KEEP.add(array);
    }
}

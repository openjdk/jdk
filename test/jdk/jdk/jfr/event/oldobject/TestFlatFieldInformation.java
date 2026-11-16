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
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Verify old-object paths through inherited and nested flat fields
 * @requires vm.hasJFR & vm.flagless & vm.bits == "64"
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          jdk.jfr/jdk.jfr.internal.test
 * @enablePreview
 * @build jdk.jfr.event.oldobject.TestFlatArrayInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatFieldInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:-UseCompressedOops jdk.jfr.event.oldobject.TestFlatFieldInformation
 */
public class TestFlatFieldInformation {
    @LooselyConsistentValue
    static value class Inner {
        final long padding;
        final byte[] ref;
        Inner(byte[] ref) {
            this.padding = 42;
            this.ref = ref;
        }
    }

    @LooselyConsistentValue
    static value class Outer {
        @NullRestricted final Inner nested;
        Outer(byte[] ref) {
            this.nested = new Inner(ref);
        }
    }

    static class Base {
        @NullRestricted final Outer value;
        Base(byte[] ref) {
            this.value = new Outer(ref);
            super();
        }
    }

    static class Holder extends Base {
        Holder(byte[] ref) {
            super(ref);
        }
    }

    static class Ordinary {
        final byte[] ref;
        Ordinary(byte[] ref) {
            this.ref = ref;
        }
    }

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        // Initialize the layouts before inspecting flattening.
        new Holder(null);
        Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(Base.class.getDeclaredField("value")));
        Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(Outer.class.getDeclaredField("nested")));
        TestFlatArrayInformation.check("nested inherited flat fields",
                () -> TestFlatArrayInformation.KEEP.add(new Holder(new byte[OldObjects.MIN_SIZE])),
                Holder.class, ref -> verify(ref, "value", "nested", "ref"));
        TestFlatArrayInformation.check("nested flat array element",
                TestFlatFieldInformation::allocateArray, Outer[].class, ref -> {
                    Asserts.assertNull(ref.getValue("field"));
                    Asserts.assertEquals(ref.getInt("array.index"), TestFlatArrayInformation.LENGTH - 1);
                    Asserts.assertEquals(ref.getInt("array.size"), TestFlatArrayInformation.LENGTH);
                    verifyPath(ref, "nested", "ref");
                });
        TestFlatArrayInformation.check("ordinary reference field",
                () -> TestFlatArrayInformation.KEEP.add(new Ordinary(new byte[OldObjects.MIN_SIZE])),
                Ordinary.class, ref -> verify(ref, "ref"));
    }

    private static void allocateArray() {
        Outer[] array = (Outer[]) ValueClass.newNullRestrictedNonAtomicArray(
                Outer.class, TestFlatArrayInformation.LENGTH, new Outer(null));
        Asserts.assertTrue(ValueClass.isFlatArray(array));
        array[array.length - 1] = new Outer(new byte[OldObjects.MIN_SIZE]);
        TestFlatArrayInformation.KEEP.add(array);
    }

    private static void verify(RecordedObject ref, String... names) {
        Asserts.assertNull(ref.getValue("array"));
        RecordedObject legacy = ref.getValue("field");
        Asserts.assertNotNull(legacy);
        Asserts.assertEquals(legacy.getString("name"), names[0]);
        verifyPath(ref, names);
    }

    private static void verifyPath(RecordedObject ref, String... names) {
        Object[] path = ref.getValue("fieldPath");
        Asserts.assertEquals(path.length, names.length);
        for (int i = 0; i < names.length; i++) {
            RecordedObject field = (RecordedObject) path[i];
            Asserts.assertEquals(field.getString("name"), names[i]);
            Asserts.assertTrue(java.lang.reflect.Modifier.isFinal(field.getShort("modifiers")));
        }
    }
}

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
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.Asserts;
import jdk.test.lib.helpers.StrictInit;

/**
 * @test
 * @bug 8389454
 * @summary Verify old-object paths through nullable non-atomic flattened fields
 * @requires vm.hasJFR & vm.flagless & vm.bits == "64"
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          jdk.jfr/jdk.jfr.internal.test
 * @enablePreview
 * @build jdk.jfr.event.oldobject.TestFlatNullableFieldInformation
 *        jdk.jfr.event.oldobject.TestFlatArrayInformation
 * @run driver jdk.test.lib.helpers.StrictProcessor jdk.jfr.event.oldobject.TestFlatNullableFieldInformation$Holder
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseFieldFlattening -XX:+UseNullableNonAtomicValueFlattening -XX:-UseNullableAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatNullableFieldInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseFieldFlattening -XX:+UseNullableNonAtomicValueFlattening -XX:-UseNullableAtomicValueFlattening -XX:-UseCompressedOops jdk.jfr.event.oldobject.TestFlatNullableFieldInformation
 */
public class TestFlatNullableFieldInformation {
    @LooselyConsistentValue
    static value class Value {
        // Exceed the atomic layout size limit in both oop modes.
        final long padding;
        final byte[] ref;

        Value(byte[] ref) {
            padding = 42;
            this.ref = ref;
        }
    }

    static class Holder {
        @StrictInit final Value value;

        Holder(Value value) {
            this.value = value;
            super();
        }
    }

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        new Holder(new Value(null));
        Asserts.assertNull(new Holder(null).value);
        // The field is nullable, and nullable atomic flattening is disabled.
        // A flat field therefore uses NULLABLE_NON_ATOMIC_FLAT.
        Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(Holder.class.getDeclaredField("value")));

        TestFlatArrayInformation.check("nullable non-atomic flat field",
            TestFlatNullableFieldInformation::allocate, Holder.class, ref -> {
                RecordedObject value = TestFlatArrayInformation.verifyField(ref, "ref", Value.class, true);
                TestFlatArrayInformation.verifyField(value.getValue("referrer"), "value", Holder.class, false);
            });
    }

    private static void allocate() {
        TestFlatArrayInformation.KEEP.add(new Holder(null));
        TestFlatArrayInformation.KEEP.add(new Holder(new Value(new byte[OldObjects.MIN_SIZE])));
    }
}

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

import java.util.HashMap;
import java.util.Map;
import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @bug 8389454
 * @summary Verify shared and distinct identities of flattened old-object holders
 * @requires vm.hasJFR & vm.flagless & vm.bits == "64"
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          jdk.jfr/jdk.jfr.internal.test
 *          jdk.management
 * @enablePreview
 * @build jdk.jfr.event.oldobject.TestFlatArrayInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatLogicalIdentity
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:-UseCompressedOops jdk.jfr.event.oldobject.TestFlatLogicalIdentity
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:+UseCompressedOops -XX:ObjectAlignmentInBytes=16 jdk.jfr.event.oldobject.TestFlatLogicalIdentity
 */
public class TestFlatLogicalIdentity {
    @LooselyConsistentValue
    static value class Pair {
        final byte[] left;
        final byte[] right;
        Pair(byte[] left, byte[] right) {
            this.left = left;
            this.right = right;
        }
    }

    @LooselyConsistentValue
    static value class Outer {
        @NullRestricted final Pair nested;
        Outer(int firstRole) {
            this.nested = new Pair(new byte[OldObjects.MIN_SIZE + firstRole],
                                   new byte[OldObjects.MIN_SIZE + firstRole + 1]);
        }
        Outer() {
            this.nested = new Pair(null, null);
        }
    }

    static class Base {
        @NullRestricted final Outer value;
        Base() {
            this.value = new Outer(0);
            super();
        }
    }

    static class Holder extends Base {
        // Same name and local field index as Base.value, but a different occurrence.
        @NullRestricted final Outer value;
        Holder() {
            this.value = new Outer(2);
            super();
        }
    }

    private record Occurrence(long holder, int position, boolean nested) {}

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        new Holder();
        for (Class<?> type : new Class<?>[] {Base.class, Holder.class}) {
            Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(type.getDeclaredField("value")));
        }
        Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(Outer.class.getDeclaredField("nested")));
        check(false);
        check(true);
    }

    private static void allocate(boolean arrayMode) {
        if (arrayMode) {
            Outer[] array =
            (Outer[]) ValueClass.newNullRestrictedNonAtomicArray(Outer.class, TestFlatArrayInformation.LENGTH, new Outer());
            Asserts.assertTrue(ValueClass.isFlatArray(array));
            array[0] = new Outer(0);
            array[array.length - 1] = new Outer(2);
            TestFlatArrayInformation.KEEP.add(array);
        } else {
            TestFlatArrayInformation.KEEP.add(new Holder());
        }
    }

    private static void remember(Map<Occurrence, Long> ids, Map<Long, Occurrence> owners,
                                 Occurrence occurrence, RecordedObject value) {
        long id = value.getLong("address");
        Long previous = ids.putIfAbsent(occurrence, id);
        if (previous != null) {
            Asserts.assertEquals(previous.longValue(), id, "Same flattened occurrence must reuse its identity");
        }
        Occurrence previousOwner = owners.putIfAbsent(id, occurrence);
        if (previousOwner != null) {
            Asserts.assertEquals(previousOwner, occurrence, "Different occurrences must have distinct identities");
        }
    }

    private static void check(boolean arrayMode) throws Exception {
        String ownerType = (arrayMode ? Outer[].class : Holder.class).getName();
        // Each accepted recording must contain both references from both occurrences
        // in one physical holder. Partial sampling evidence causes a retry.
        for (int attempt = 0; ; attempt++) {
            TestFlatArrayInformation.KEEP.clear();
            try (Recording recording = new Recording()) {
                recording.enable(EventNames.OldObjectSample).withoutStackTrace().with("cutoff", "infinity");
                recording.start();
                for (int i = 0; i < 16; i++) {
                    allocate(arrayMode);
                }
                recording.stop();
                Map<Occurrence, Long> ids = new HashMap<>();
                Map<Long, Occurrence> owners = new HashMap<>();
                Map<Long, Integer> seen = new HashMap<>();
                for (var event : Events.fromRecording(recording)) {
                    RecordedObject sample = event.getValue("object");
                    if (!sample.getClass("type").getName().equals(byte[].class.getName())) {
                        continue;
                    }
                    int role = event.getInt("arrayElements") - OldObjects.MIN_SIZE;
                    if (role < 0 || role > 3) {
                        continue;
                    }
                    RecordedObject ref = sample.getValue("referrer");
                    if (ref == null || !ref.getClass("object.type").getName().equals(Pair.class.getName())) {
                        continue;
                    }
                    RecordedObject pair = TestFlatArrayInformation.verifyField(ref,
                            (role & 1) == 0 ? "left" : "right", Pair.class, true);
                    RecordedObject outer = TestFlatArrayInformation.verifyField(pair.getValue("referrer"),
                            "nested", Outer.class, true);
                    RecordedObject outerRef = outer.getValue("referrer");
                    if (outerRef == null || !outerRef.getClass("object.type").getName().equals(ownerType)) {
                        continue;
                    }
                    int position = role / 2;
                    RecordedObject holder;
                    if (arrayMode) {
                        TestFlatArrayInformation.verifyArray(outerRef,
                                position == 0 ? 0 : TestFlatArrayInformation.LENGTH - 1, Outer[].class);
                        holder = outerRef.getValue("object");
                    } else {
                        holder = TestFlatArrayInformation.verifyField(outerRef, "value", Holder.class, false);
                    }
                    long address = holder.getLong("address");
                    Asserts.assertEquals(address & TestFlatArrayInformation.ALIGNMENT_MASK, 0L);
                    remember(ids, owners, new Occurrence(address, position, true), pair);
                    remember(ids, owners, new Occurrence(address, position, false), outer);
                    seen.merge(address, 1 << role, (a, b) -> a | b);
                }
                if (seen.containsValue(15)) {
                    TestFlatArrayInformation.KEEP.clear();
                    return;
                }
            }
            System.out.println("Retrying logical identities, array=" + arrayMode + ", attempt=" + (attempt + 1));
        }
    }
}

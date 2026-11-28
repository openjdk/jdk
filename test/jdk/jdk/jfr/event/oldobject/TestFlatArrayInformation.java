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

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @bug 8389454
 * @summary Verify old-object reference indices inside flat array elements
 * @requires vm.hasJFR & vm.flagless & vm.bits == "64"
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          jdk.jfr/jdk.jfr.internal.test
 *          jdk.management
 * @enablePreview
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatArrayInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:-UseCompressedOops jdk.jfr.event.oldobject.TestFlatArrayInformation
 */
public class TestFlatArrayInformation {
    static final int LENGTH = 8;
    static final long ALIGNMENT_MASK =
        Long.parseLong(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).getVMOption("ObjectAlignmentInBytes").getValue()) - 1;
    static final List<Object> RETAINED_OBJECTS = new ArrayList<>();

    @LooselyConsistentValue
    static value class Element {
        final long padding;
        final byte[] left;
        final byte[] right;
        Element(byte[] left, byte[] right) {
            this.padding = 42;
            this.left = left;
            this.right = right;
        }
    }

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        // Exercise different reference offsets at both ends of the array.
        for (int index : new int[] {0, LENGTH - 1}) {
            for (boolean right : new boolean[] {false, true}) {
                recordAndVerifyReferencePath("flat array index=" + index + " right=" + right,
                      () -> allocate(index, right), Element[].class,
                      ref -> {
                          RecordedObject element = verifyFieldInFlattenedValue(ref, right ? "right" : "left", Element.class);
                          verifyArrayElementReference(element.getValue("referrer"), index, Element[].class);
                      });
            }
        }
    }

    static RecordedObject verifyFieldInFlattenedValue(RecordedObject referrer, String fieldName, Class<?> containingType) {
        RecordedObject flattenedValue = verifyFieldReference(referrer, fieldName, containingType);
        verifySyntheticAddress(flattenedValue);
        return flattenedValue;
    }

    static RecordedObject verifyFieldInObject(RecordedObject referrer, String fieldName, Class<?> containingType) {
        RecordedObject containingObject = verifyFieldReference(referrer, fieldName, containingType);
        Asserts.assertTrue(containingObject.getLong("address") != 0, "Containing object must have a nonzero address");
        return containingObject;
    }

    private static RecordedObject verifyFieldReference(RecordedObject referrer, String fieldName, Class<?> containingType) {
        Asserts.assertNotNull(referrer);
        Asserts.assertNull(referrer.getValue("array"));
        Asserts.assertEquals(referrer.getInt("skip"), 0);
        RecordedObject field = referrer.getValue("field");
        Asserts.assertNotNull(field);
        Asserts.assertEquals(field.getString("name"), fieldName);
        Asserts.assertTrue(java.lang.reflect.Modifier.isFinal(field.getShort("modifiers")));
        RecordedObject containingObject = referrer.getValue("object");
        Asserts.assertEquals(containingObject.getClass("type").getName(), containingType.getName());
        return containingObject;
    }

    static void verifySyntheticAddress(RecordedObject object) {
        long syntheticAddress = object.getLong("address");
        Asserts.assertTrue(syntheticAddress != 0);
        long marker = syntheticAddress >>> 32;
        Asserts.assertTrue(marker == 0x401BA5EDL || marker == 0x401C0DEDL,
                "Synthetic address must be in a LogicalEdgeStore ID range");
        Asserts.assertTrue((syntheticAddress & ALIGNMENT_MASK) != 0,
                "Synthetic address must not alias an aligned object address");
        Asserts.assertNull(object.getString("description"));
    }

    static void verifyArrayElementReference(RecordedObject referrer, int index, Class<?> arrayType) {
        Asserts.assertNotNull(referrer);
        Asserts.assertNull(referrer.getValue("field"));
        Asserts.assertEquals(referrer.getInt("skip"), 0);
        Asserts.assertEquals(referrer.getInt("array.index"), index);
        Asserts.assertEquals(referrer.getInt("array.size"), LENGTH);
        RecordedObject array = referrer.getValue("object");
        Asserts.assertEquals(array.getClass("type").getName(), arrayType.getName());
        Asserts.assertTrue(array.getLong("address") != 0);
    }

    private static void allocate(int index, boolean right) {
        Element[] array = (Element[]) ValueClass.newNullRestrictedNonAtomicArray(Element.class,
                                                                                 LENGTH,
                                                                                 new Element(null, null));
        Asserts.assertTrue(ValueClass.isFlatArray(array), "Test requires a flat array");
        byte[] sample = new byte[OldObjects.MIN_SIZE];
        array[index] = right ? new Element(null, sample) : new Element(sample, null);
        RETAINED_OBJECTS.add(array);
    }

    static void recordAndVerifyReferencePath(String label, Runnable allocate, Class<?> expectedContainingType,
                                             Consumer<RecordedObject> verifyReferencePath) throws Exception {
        // Sampling is probabilistic; let the jtreg timeout bound retries.
        for (int attempt = 0; ; attempt++) {
            RETAINED_OBJECTS.clear();
            try (Recording recording = new Recording()) {
                recording.enable(EventNames.OldObjectSample).withoutStackTrace().with("cutoff", "infinity");
                recording.start();
                for (int allocation = 0; allocation < 32; allocation++) {
                    allocate.run();
                }
                recording.stop();
                int verifiedPathCount = 0;
                for (var event : Events.fromRecording(recording)) {
                    RecordedObject sample = event.getValue("object");
                    if (!sample.getClass("type").getName().equals(byte[].class.getName())) {
                        continue;
                    }
                    RecordedObject ref = sample.getValue("referrer");
                    if (ref == null) {
                        continue;
                    }
                    RecordedObject holder = ref.getValue("object");
                    // Follow the reference chain to the expected containing object or array.
                    for (int depth = 0; holder != null && depth < 16 &&
                         !holder.getClass("type").getName().equals(expectedContainingType.getName()); depth++) {
                        RecordedObject outer = holder.getValue("referrer");
                        holder = outer == null ? null : outer.getValue("object");
                    }
                    if (holder == null || !holder.getClass("type").getName().equals(expectedContainingType.getName())) {
                        continue;
                    }
                    verifyReferencePath.accept(ref);
                    verifiedPathCount++;
                }
                if (verifiedPathCount != 0) {
                    RETAINED_OBJECTS.clear();
                    return;
                }
            }
            System.out.println("Retrying " + label + ", attempt " + (attempt + 1));
        }
    }
}

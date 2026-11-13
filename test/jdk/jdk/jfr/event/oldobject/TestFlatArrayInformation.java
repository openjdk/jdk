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
 * @enablePreview
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatArrayInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:-UseCompressedOops jdk.jfr.event.oldobject.TestFlatArrayInformation
 */
public class TestFlatArrayInformation {
    static final int LENGTH = 8;
    static final List<Object> KEEP = new ArrayList<>();

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
                check("flat array index=" + index + " right=" + right,
                      () -> allocate(index, right), Element[].class,
                      ref -> {
                          RecordedObject element = verifyField(ref, right ? "right" : "left", Element.class, true);
                          verifyArray(element.getValue("referrer"), index, Element[].class);
                      });
            }
        }
    }

    static RecordedObject verifyField(RecordedObject ref, String name, Class<?> type, boolean embedded) {
        Asserts.assertNotNull(ref);
        Asserts.assertNull(ref.getValue("array"));
        Asserts.assertEquals(ref.getInt("skip"), 0);
        RecordedObject field = ref.getValue("field");
        Asserts.assertNotNull(field);
        Asserts.assertEquals(field.getString("name"), name);
        Asserts.assertTrue(java.lang.reflect.Modifier.isFinal(field.getShort("modifiers")));
        RecordedObject holder = ref.getValue("object");
        Asserts.assertEquals(holder.getClass("type").getName(), type.getName());
        if (embedded) {
            Asserts.assertEquals(holder.getLong("address"), 0L);
            Asserts.assertNull(holder.getString("description"));
        } else {
            Asserts.assertTrue(holder.getLong("address") != 0);
        }
        return holder;
    }

    static void verifyArray(RecordedObject ref, int index, Class<?> type) {
        Asserts.assertNotNull(ref);
        Asserts.assertNull(ref.getValue("field"));
        Asserts.assertEquals(ref.getInt("skip"), 0);
        Asserts.assertEquals(ref.getInt("array.index"), index);
        Asserts.assertEquals(ref.getInt("array.size"), LENGTH);
        RecordedObject array = ref.getValue("object");
        Asserts.assertEquals(array.getClass("type").getName(), type.getName());
        Asserts.assertTrue(array.getLong("address") != 0);
    }

    private static void allocate(int index, boolean right) {
        Element[] array = (Element[]) ValueClass.newNullRestrictedNonAtomicArray(Element.class,
                                                                                 LENGTH,
                                                                                 new Element(null, null));
        Asserts.assertTrue(ValueClass.isFlatArray(array), "Test requires a flat array");
        byte[] sample = new byte[OldObjects.MIN_SIZE];
        array[index] = right ? new Element(null, sample) : new Element(sample, null);
        KEEP.add(array);
    }

    static void check(String label, Runnable allocate, Class<?> owner,
                      Consumer<RecordedObject> verify) throws Exception {
        // Sampling is probabilistic; let the jtreg timeout bound retries.
        for (int attempt = 0; ; attempt++) {
            KEEP.clear();
            try (Recording recording = new Recording()) {
                recording.enable(EventNames.OldObjectSample).withoutStackTrace().with("cutoff", "infinity");
                recording.start();
                for (int i = 0; i < 32; i++) {
                    allocate.run();
                }
                recording.stop();
                int found = 0;
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
                         !holder.getClass("type").getName().equals(owner.getName()); depth++) {
                        RecordedObject outer = holder.getValue("referrer");
                        holder = outer == null ? null : outer.getValue("object");
                    }
                    if (holder == null || !holder.getClass("type").getName().equals(owner.getName())) {
                        continue;
                    }
                    verify.accept(ref);
                    found++;
                }
                if (found != 0) {
                    KEEP.clear();
                    return;
                }
            }
            System.out.println("Retrying " + label + ", attempt " + (attempt + 1));
        }
    }
}

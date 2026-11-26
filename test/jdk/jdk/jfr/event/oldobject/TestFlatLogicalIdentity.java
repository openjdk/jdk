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

import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;

/**
 * @test
 * @bug 8389454
 * @summary Verify LogicalEdgeStore reuses synthetic addresses for the same flattened value, separates different values, and does not reuse addresses across dumps
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
        Outer(int firstSampleIndex) {
            this.nested = new Pair(new byte[OldObjects.MIN_SIZE + firstSampleIndex],
                                   new byte[OldObjects.MIN_SIZE + firstSampleIndex + 1]);
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
        // Same name and local field index as Base.value, but a separate flattened value.
        @NullRestricted final Outer value;
        Holder() {
            this.value = new Outer(2);
            super();
        }
    }

    private record FlatValueLocation(long containingObjectAddress, String fieldOrArrayPath) {}

    private record DumpResult(Set<Long> syntheticAddresses, boolean sampledAllFourReferencePaths,
                              Instant newestEventTime) {}

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        new Holder();
        for (Class<?> type : new Class<?>[] {Base.class, Holder.class}) {
            Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(type.getDeclaredField("value")));
        }
        Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(Outer.class.getDeclaredField("nested")));
        testInstanceFields();
        testArrayElements();
    }

    private static void testInstanceFields() throws Exception {
        verifySyntheticAddressesAcrossDumps(false);
    }

    private static void testArrayElements() throws Exception {
        verifySyntheticAddressesAcrossDumps(true);
    }

    private static void allocate(boolean arrayMode) {
        if (arrayMode) {
            Outer[] array =
            (Outer[]) ValueClass.newNullRestrictedNonAtomicArray(Outer.class, TestFlatArrayInformation.LENGTH, new Outer());
            Asserts.assertTrue(ValueClass.isFlatArray(array));
            array[0] = new Outer(0);
            array[array.length - 1] = new Outer(2);
            TestFlatArrayInformation.RETAINED_OBJECTS.add(array);
        } else {
            TestFlatArrayInformation.RETAINED_OBJECTS.add(new Holder());
        }
    }

    private static void verifySyntheticAddress(Map<FlatValueLocation, Long> addressByLocation,
                                               Map<Long, FlatValueLocation> locationByAddress,
                                               FlatValueLocation location, RecordedObject value) {
        long syntheticAddress = value.getLong("address");
        Long previousAddress = addressByLocation.putIfAbsent(location, syntheticAddress);
        if (previousAddress != null) {
            Asserts.assertEquals(previousAddress.longValue(), syntheticAddress,
                "Paths through left and right must reuse the synthetic address for " + location);
        }
        FlatValueLocation previousLocation = locationByAddress.putIfAbsent(syntheticAddress, location);
        if (previousLocation != null) {
            Asserts.assertEquals(previousLocation, location,
                "Different flattened values must not share synthetic address " + syntheticAddress);
        }
    }

    private static void verifySyntheticAddressesAcrossDumps(boolean arrayMode) throws Exception {
        Path dump = Files.createTempFile("flat-logical-identity-", ".jfr");
        try {
            for (int attempt = 0; ; attempt++) {
                if (attempt > 0) {
                    System.out.println("Retrying logical identities, array=" + arrayMode + ", attempt=" + attempt);
                }
                TestFlatArrayInformation.RETAINED_OBJECTS.clear();
                try (Recording recording = new Recording()) {
                    recording.enable(EventNames.OldObjectSample).withoutStackTrace().with("cutoff", "infinity");
                    recording.start();
                    var firstDump = dumpAndVerify(recording, dump, arrayMode, Instant.MIN);
                    if (!firstDump.sampledAllFourReferencePaths()) {
                        continue;
                    }
                    var secondDump = dumpAndVerify(recording, dump, arrayMode, firstDump.newestEventTime());
                    if (secondDump.syntheticAddresses().isEmpty()) {
                        continue;
                    }
                    Asserts.assertTrue(Collections.disjoint(firstDump.syntheticAddresses(), secondDump.syntheticAddresses()),
                        "LogicalEdgeStore must not reuse synthetic addresses from the first dump");
                    System.out.println("Verified distinct synthetic addresses across dumps, array="
                        + arrayMode + ", first=" + firstDump.syntheticAddresses().size()
                        + ", second=" + secondDump.syntheticAddresses().size());
                    return;
                }
            }
        } finally {
            TestFlatArrayInformation.RETAINED_OBJECTS.clear();
            Files.deleteIfExists(dump);
        }
    }

    private static DumpResult dumpAndVerify(Recording recording, Path dump,
                                           boolean arrayMode, Instant previousDumpLastEventTime) throws Exception {
        List<Object> retainedHolders = TestFlatArrayInformation.RETAINED_OBJECTS;
        for (int allocation = 0; allocation < 16; allocation++) {
            allocate(arrayMode);
        }
        recording.dump(dump);
        Reference.reachabilityFence(retainedHolders);
        List<RecordedEvent> events = RecordingFile.readAllEvents(dump).stream()
            .filter(event -> event.getEventType().getName().equals(EventNames.OldObjectSample))
            .filter(event -> event.getStartTime().isAfter(previousDumpLastEventTime))
            .toList();
        return verifySyntheticAddressesWithinDump(events, arrayMode);
    }

    private static DumpResult verifySyntheticAddressesWithinDump(List<RecordedEvent> events, boolean arrayMode) {
        String ownerType = (arrayMode ? Outer[].class : Holder.class).getName();
        Map<FlatValueLocation, Long> addressByLocation = new HashMap<>();
        Map<Long, FlatValueLocation> locationByAddress = new HashMap<>();
        Map<Long, Set<String>> referencePathsByContainingObject = new HashMap<>();
        String firstValuePath = arrayMode ? "[0]" : "Base.value";
        String secondValuePath = arrayMode ? "[" + (TestFlatArrayInformation.LENGTH - 1) + "]" : "Holder.value";
        Set<String> expectedReferencePaths = Set.of(firstValuePath + ".nested.left", firstValuePath + ".nested.right",
                                                   secondValuePath + ".nested.left", secondValuePath + ".nested.right");
        for (var event : events) {
            RecordedObject sample = event.getValue("object");
            if (!sample.getClass("type").getName().equals(byte[].class.getName())) {
                continue;
            }
            int sampleIndex = event.getInt("arrayElements") - OldObjects.MIN_SIZE;
            if (sampleIndex < 0 || sampleIndex > 3) {
                continue;
            }
            RecordedObject ref = sample.getValue("referrer");
            if (ref == null || !ref.getClass("object.type").getName().equals(Pair.class.getName())) {
                continue;
            }
            String referenceField = sampleIndex % 2 == 0 ? "left" : "right";
            RecordedObject pair = TestFlatArrayInformation.verifyFieldInFlattenedValue(ref, referenceField, Pair.class);
            RecordedObject outer =
                TestFlatArrayInformation.verifyFieldInFlattenedValue(pair.getValue("referrer"), "nested", Outer.class);
            RecordedObject outerRef = outer.getValue("referrer");
            if (outerRef == null || !outerRef.getClass("object.type").getName().equals(ownerType)) {
                continue;
            }
            String valuePath = sampleIndex < 2 ? firstValuePath : secondValuePath;
            RecordedObject holder;
            if (arrayMode) {
                TestFlatArrayInformation.verifyArrayElementReference(outerRef, sampleIndex < 2 ? 0 : TestFlatArrayInformation.LENGTH - 1, Outer[].class);
                holder = outerRef.getValue("object");
            } else {
                holder = TestFlatArrayInformation.verifyFieldInObject(outerRef, "value", Holder.class);
            }
            long containingObjectAddress = holder.getLong("address");
            Asserts.assertEquals(containingObjectAddress & TestFlatArrayInformation.ALIGNMENT_MASK, 0L);
            verifySyntheticAddress(addressByLocation, locationByAddress,
                new FlatValueLocation(containingObjectAddress, valuePath + ".nested"), pair);
            verifySyntheticAddress(addressByLocation, locationByAddress,
                new FlatValueLocation(containingObjectAddress, valuePath), outer);
            referencePathsByContainingObject.computeIfAbsent(containingObjectAddress, address -> new HashSet<>())
                .add(valuePath + ".nested." + referenceField);
        }
        Instant newestEventTime = events.stream().map(RecordedEvent::getStartTime).max(Instant::compareTo).orElse(Instant.MIN);
        return new DumpResult(Set.copyOf(locationByAddress.keySet()),
            referencePathsByContainingObject.containsValue(expectedReferencePaths), newestEventTime);
    }
}

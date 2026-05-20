/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=young
 * @requires vm.gc.Shenandoah
 * @summary Confirm that young referents are cleared.
 * @bug 8373203
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGenerationalMinTenuringAge=1 -XX:ShenandoahGenerationalMaxTenuringAge=1
 *      -Xmx128M -Xms128M -ea
 *      gc.shenandoah.generational.TestGenerationalReferenceProcessing young
 */

/*
 * @test id=old
 * @requires vm.gc.Shenandoah
 * @summary Confirm that old referents are cleared.
 * @bug 8373203
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGenerationalMinTenuringAge=1 -XX:ShenandoahGenerationalMaxTenuringAge=1
 *      -Xmx128M -Xms128M -ea
 *      gc.shenandoah.generational.TestGenerationalReferenceProcessing old
 */

package gc.shenandoah.generational;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.function.Supplier;

import jdk.test.whitebox.WhiteBox;

public class TestGenerationalReferenceProcessing {
    static final int OLD = 0;
    static final int YOUNG = 1;

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static class LeakedObject {}

    private static final int REGION_SIZE = WB.shenandoahRegionSize();
    private static final int REGION_COUNT = WB.shenandoahRegionCount();
    private static final int OBJECT_SIZE = (int)WB.getObjectSize(new LeakedObject());

    // We don't want to fill too much of the heap, or the heuristics will trigger GCs instead of our test
    private static final int REGIONS_TO_FILL = REGION_COUNT / 12;
    private static final int OBJECTS_PER_REGION = REGION_SIZE / OBJECT_SIZE / 2;
    private static final int OBJECT_COUNT = OBJECTS_PER_REGION * REGIONS_TO_FILL;

    private static final Collection<WeakReference<?>> WEAK_REFS = new HashSet<>(OBJECT_COUNT);
    private static final List<LeakedObject> REFERENTS = new ArrayList<>(OBJECT_COUNT);
    private static final ReferenceQueue<LeakedObject> REF_QUEUE = new ReferenceQueue<>();

    private static final int MINIMUM_CROSS_GENERATIONAL_REFERENCE_COUNT = 50;

    static class ReferenceClassifier {
        private final Object[][] references;

        ReferenceClassifier() {
            references = new Object[][]{
                    {new HashSet<WeakReference<?>>(), new HashSet<WeakReference<?>>()},
                    {new HashSet<WeakReference<?>>(), new HashSet<WeakReference<?>>()}
            };
        }

        void classify() {
            clear();

            for (var weakRef : TestGenerationalReferenceProcessing.WEAK_REFS) {
                var referent = weakRef.get();
                if (referent != null) {
                    int row = WB.isObjectInOldGen(weakRef) ? OLD : YOUNG;
                    int column = WB.isObjectInOldGen(referent) ? OLD : YOUNG;
                    getReferences(row, column).add(weakRef);
                }
            }
        }

        private void clear() {
            getReferences(OLD, OLD).clear();
            getReferences(OLD, YOUNG).clear();
            getReferences(YOUNG, OLD).clear();
            getReferences(YOUNG, YOUNG).clear();
        }

        HashSet<WeakReference<?>> getReferences(int reference, int referent) {
            assert reference == OLD || reference == YOUNG;
            assert referent == OLD || referent == YOUNG;
            return (HashSet<WeakReference<?>>)references[reference][referent];
        }

        @Override
        public String toString() {
            return String.format("OO: %d, OY: %d, YO: %d, YY: %d",
                    getReferences(OLD, OLD).size(), getReferences(OLD, YOUNG).size(),
                    getReferences(YOUNG, OLD).size(), getReferences(YOUNG, YOUNG).size());
        }
    }

    public static void usage() {
        System.out.println("Call with generation to test: young|old");
        throw new IllegalArgumentException("Missing required argument: young|old");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            usage();
        }

        if ("young".equals(args[0])) {
            testCollectCrossGenerationalReferents(OLD, YOUNG);
        } else if ("old".equals(args[0])) {
            testCollectCrossGenerationalReferents(YOUNG, OLD);
        } else {
            usage();
        }
    }

    private static String name(int generation) {
        return generation == OLD ? "old" : "young";
    }

    private static void testCollectCrossGenerationalReferents(int referenceGen, int referentGen) {
        ReferenceClassifier classifier = new ReferenceClassifier();

        useMemoryUntil(() -> {
                classifier.classify();
                return classifier.getReferences(referenceGen, referentGen).size() > MINIMUM_CROSS_GENERATIONAL_REFERENCE_COUNT;
        });

        assert !classifier.getReferences(referenceGen, referentGen).isEmpty() : "Conditions for test not met: " + classifier;

        System.out.println("Before clearing all referents: " + classifier);
        drainReferenceQueueAndClearReferents();

        if (referentGen == YOUNG) {
            WB.youngGC();
        } else {
            // Print address of old references before old GC.
            var oldToOld = classifier.getReferences(referentGen, referentGen);
            printReferences(OLD, OLD, oldToOld);
            WB.shenandoahOldGC();
        }

        int cleared = removeClearedWeakReferences();
        classifier.classify();
        System.out.println("After " + name(referentGen) + " GC, cleared: " + cleared + ", referents: " + classifier);

        assertReferencesCleared(referentGen, referentGen, classifier);
        assertReferencesCleared(referenceGen, referentGen, classifier);
    }

    private static void assertReferencesCleared(int referenceGen, int referentGen, ReferenceClassifier classifier) {
        var references = classifier.getReferences(referenceGen, referentGen);
        if (references.isEmpty()) {
            return;
        }

        // Addresses here could be relocated and may not match logs from old gen collection
        printReferences(referenceGen, referentGen, references);
        throw new AssertionError(name(referenceGen) + " to " + name(referentGen) + " referents should have been cleared");
    }

    private static void printReferences(int referenceGen, int referentGen, HashSet<WeakReference<?>> references) {
        final int max_references = 10;
        int references_shown = 0;
        for (var reference : references) {
            if (references_shown >= max_references) {
                break;
            }

            ++references_shown;
            System.out.printf("reference: 0x%x in %s refers to 0x%x in %s\n",
                    WB.getObjectAddress(reference), name(referenceGen),
                    WB.getObjectAddress(reference.get()), name(referentGen));
        }
    }

    private static int removeClearedWeakReferences() {
        int cleared = 0;
        Reference<?> weak;
        while ((weak = REF_QUEUE.poll()) != null) {
            WEAK_REFS.remove(weak);
            ++cleared;
        }
        return cleared;
    }

    private static void drainReferenceQueueAndClearReferents() {
        // Drain the reference queue of any incidental weak references from outside the test
        while (REF_QUEUE.poll() != null);

        // Make all our referents unreachable now
        REFERENTS.clear();
    }

    private static void useMemoryUntil(Supplier<Boolean> exitCondition) {
        // This is not an exact science here. We want to create weak references
        // with referents in a different region. We also don't want to allocate
        // everything up front, or else they will all end up in old together, and
        // we won't get a good mix of cross generational pointers.
        for (int i = 0; i < REGIONS_TO_FILL; i += 4) {
            allocateReferents(2);
            allocateReferences(2);

            WB.youngGC();
            if (exitCondition.get()) {
                break;
            }
        }
    }

    private static void allocateReferents(int regions) {
        for (int j = 0; j < regions; j++) {
            for (int i = 0; i < OBJECTS_PER_REGION; ++i) {
                var leakedObject = new LeakedObject();
                REFERENTS.add(leakedObject);
                byte[] garbage = new byte[OBJECT_SIZE];
                garbage[i % garbage.length] = (byte) i;
            }
        }
    }

    private static void allocateReferences(int regions) {
        // Fill up regions that are equal parts garbage and references
        // We want to create cross region references to increase the chances
        // of cross generational references.
        int referentCount = REFERENTS.size() - 1;
        for (int j = 0; j < regions; j++) {
            for (int i = 0; i < OBJECTS_PER_REGION; ++i) {
                var leakedObject = REFERENTS.get(referentCount - i);
                var ref = new WeakReference<>(leakedObject, REF_QUEUE);
                WEAK_REFS.add(ref);
                byte[] garbage = new byte[OBJECT_SIZE];
                garbage[i % garbage.length] = (byte) i;
            }
        }
    }
}

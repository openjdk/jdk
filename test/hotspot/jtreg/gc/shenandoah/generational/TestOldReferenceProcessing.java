package gc.shenandoah.generational;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.function.Supplier;

import jdk.test.whitebox.WhiteBox;

/*
 * @test id=young
 * @requires vm.gc.Shenandoah
 * @summary Confirm that young non-strong references are collected.
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGenerationalMinTenuringAge=1 -XX:ShenandoahGenerationalMaxTenuringAge=1
 *      -Xmx128M -Xms128M -Xlog:gc*=info:/tmp/test.log -ea
 *      gc.shenandoah.generational.TestOldReferenceProcessing young
 */

/*
 * @test id=old
 * @requires vm.gc.Shenandoah
 * @summary Confirm that young non-strong references are collected.
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGenerationalMinTenuringAge=1 -XX:ShenandoahGenerationalMaxTenuringAge=1
 *      -XX:ShenandoahIgnoreGarbageThreshold=0 -XX:ShenandoahOldGarbageThreshold=0 -XX:ShenandoahGarbageThreshold=0
 *      -Xmx128M -Xms128M -Xlog:gc*=info:/tmp/test.log -ea
 *      gc.shenandoah.generational.TestOldReferenceProcessing old
 */
public class TestOldReferenceProcessing {
    static final int OLD = 0;
    static final int YOUNG = 1;

    private static WhiteBox WB = WhiteBox.getWhiteBox();

    private static class LeakedObject extends Object {}

    private static final int REGION_SIZE = WB.shenandoahRegionSize();
    private static final int REGION_COUNT = WB.shenandoahRegionCount();
    private static final int OBJECT_SIZE = (int)WB.getObjectSize(new LeakedObject());
    private static final int WEAK_REF_SIZE = (int)WB.getObjectSize(new WeakReference<>(new LeakedObject()));
    private static final int GARBAGE_SIZE = (int)WB.getObjectSize(new byte[OBJECT_SIZE + WEAK_REF_SIZE]);
    private static final int REGIONS_TO_FILL = REGION_COUNT / 8;
    private static final int REFERENTS_PER_REGION = REGION_SIZE / (OBJECT_SIZE + WEAK_REF_SIZE);
    private static final int OBJECTS_PER_REGION = REGION_SIZE / (OBJECT_SIZE + WEAK_REF_SIZE + GARBAGE_SIZE);
    private static final int YOUNG_GC_INTERVAL = 16;

    private static final List<WeakReference<?>> WEAK_REFS = new ArrayList<>(REFERENTS_PER_REGION * REGIONS_TO_FILL);
    private static final List<LeakedObject> REFERENTS = new ArrayList<>(REFERENTS_PER_REGION * REGIONS_TO_FILL);
    private static final ReferenceQueue<LeakedObject> refQueue = new ReferenceQueue<>();

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

            for (int j = 0; j < TestOldReferenceProcessing.WEAK_REFS.size(); ++j) {
                var weakRef = TestOldReferenceProcessing.WEAK_REFS.get(j);
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
            assert(reference == OLD || reference == YOUNG);
            assert(referent == OLD || referent == YOUNG);
            return (HashSet<WeakReference<?>>)references[reference][referent];
        }

        @Override
        public String toString() {
            return String.format("OO: %d, OY: %d, YO: %d, YY: %d",
                    getReferences(OLD, OLD).size(), getReferences(OLD, YOUNG).size(),
                    getReferences(YOUNG, OLD).size(), getReferences(YOUNG, YOUNG).size());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Call with generation to test: young|old");
            return;
        }

        if ("young".equals(args[0])) {
            testCollectCrossGenerationalReferents(OLD, YOUNG);
        } else if ("old".equals(args[0])) {
            testCollectCrossGenerationalReferents(YOUNG, OLD);
        }
    }

    private static String name(int generation) {
        return generation == OLD ? "old" : "young";
    }

    private static void testCollectCrossGenerationalReferents(int referenceGen, int referentGen) {
        ReferenceClassifier classifier = new ReferenceClassifier();

        useMemoryUntil(() -> {
                classifier.classify();
                return classifier.getReferences(referenceGen, referentGen).size() > 50;
        });

        assert !classifier.getReferences(referenceGen, referentGen).isEmpty() : "Conditions for test not met";

        drainReferenceQueueAndClearReferents();

        System.out.println("Before clearing all referents: " + classifier);
        if (referentGen == YOUNG) {
            WB.youngGC();
        } else {
            WB.shenandoahOldGC();
            WB.youngGC();
        }

        int cleared = removeClearedWeakReferences();
        classifier.classify();
        System.out.println("After " + name(referentGen) + " GC, cleared: " + cleared + ", referents: " + classifier);

        assertReferencesCleared(referentGen, referentGen, classifier);
        assertReferencesCleared(referenceGen, referentGen, classifier);
    }

    private static void assertReferencesCleared(int referenceGen, int referentGen, ReferenceClassifier classifier) {
        var references = classifier.getReferences(referenceGen, referentGen);
        assert references.isEmpty() : name(referenceGen) + " to " + name(referentGen) + " referents should have been cleared";
    }

    private static int removeClearedWeakReferences() {
        int cleared = 0;
        Reference<?> weak;
        while ((weak = refQueue.poll()) != null) {
            WEAK_REFS.remove(weak);
            ++cleared;
        }
        return cleared;
    }

    private static void drainReferenceQueueAndClearReferents() {
        while (refQueue.poll() != null);
        REFERENTS.clear();
    }

    private static void useMemoryUntil(Supplier<Boolean> exitCondition) {
        for (int i = 0; i < REGIONS_TO_FILL; ++i) {
            allocateRegion();

            if (i % YOUNG_GC_INTERVAL == 0) {
                WB.youngGC();

                if (exitCondition.get()) {
                    break;
                }
            }
        }
    }

    private static void allocateRegion() {
        for (int j = 0; j < OBJECTS_PER_REGION; ++j) {
            var leakedObject = new LeakedObject();
            var ref = new WeakReference<>(leakedObject, refQueue);
            REFERENTS.add(leakedObject);
            WEAK_REFS.add(ref);
            byte[] garbage = new byte[OBJECT_SIZE + WEAK_REF_SIZE];
            garbage[j % garbage.length] = (byte) j;
        }
    }
}
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
 * @test id=generational
 * @bug 8393426
 * @requires vm.gc.Shenandoah
 * @summary The remembered set scan must not inadvertently keep old referents on dirty cards alive.
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -Xms512m -Xmx512m
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:+UnlockExperimentalVMOptions
 *      -XX:-DisableExplicitGC -XX:+ExplicitGCInvokesConcurrent -XX:ShenandoahGenerationalMinPIPUsage=0
 *      gc.shenandoah.generational.TestOldRefsOnDirtyCardAreCleared
 */

package gc.shenandoah.generational;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import jdk.test.whitebox.WhiteBox;

public final class TestOldRefsOnDirtyCardAreCleared {
    // Use a subclass so that we can reliably dirty the card for this weak reference
    static final class WeakReferenceWithYoungPointer extends WeakReference<Object> {
        public Object youngPointer;
        public WeakReferenceWithYoungPointer(Object object) {
            super(object);
        }
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static final List<WeakReferenceWithYoungPointer> WEAK_REFS = new ArrayList<>();
    private static final List<Object> STRONG = new ArrayList<>();

    // These will be intentionally strongly reachable to be sure the reference is NOT cleared
    private static final Object RETAIN_REFERENT = new Object();
    private static final WeakReferenceWithYoungPointer RETAIN_REF = new WeakReferenceWithYoungPointer(RETAIN_REFERENT);

    // Put a safety net on the number of System.gcs that will be used for tenuring.
    private static final int MAX_FULL_GCS = 5;

    // Multiple references helps the test avoid the false positive scenario where
    // the `youngPointer` ends up on a different card.
    private static final int REF_COUNT = 8;

    private static boolean allAreInOld(List<?> objects) {
        for (Object obj : objects) {
            if (!WB.isObjectInOldGen(obj)) {
                return false;
            }
        }
        return true;
    }

    private static boolean testConditionsMet() {
        return allAreInOld(WEAK_REFS) && allAreInOld(STRONG)
            && WB.isObjectInOldGen(RETAIN_REFERENT)
            && WB.isObjectInOldGen(RETAIN_REF);
    }

    public static void main(String[] args) throws Exception {
        // Step 1. Make references with strongly reachable referents.
        for (int i = 0; i < REF_COUNT; ++i) {
            Object obj = new Object();
            WeakReferenceWithYoungPointer wr = new WeakReferenceWithYoungPointer(obj);
            WEAK_REFS.add(wr);
            STRONG.add(obj);
        }

        // Step 2. Run global GCs until all the references and their referents are promoted
        int tries = 0;
        while (!testConditionsMet()) {
            if (tries >= MAX_FULL_GCS) {
                throw new RuntimeException("Test condition unmet: weak refs and referents not promoted");
            }
            WB.fullGC();
            ++tries;
        }

        // Step 3. Drop the strong references to the referents
        STRONG.clear();

        // Step 4. Dirty the cards for all the weak references
        for (WeakReferenceWithYoungPointer ref : WEAK_REFS) {
            ref.youngPointer = new Object();
        }
        RETAIN_REF.youngPointer = new Object();

        // Step 5. Run an old GC cycle. This should clear out the weak referents
        WB.shenandoahOldGC();

        // Step 6. Fail if any references still have referents (should be zero)
        for (int i = 0, n = WEAK_REFS.size(); i < n; ++i) {
            WeakReferenceWithYoungPointer wr = WEAK_REFS.get(i);
            Object referent = wr.get();
            if (referent != null) {
                throw new RuntimeException("Uncleared weak ref = " + wr +
                                           ", referent = " + referent + ", at index = " + i);
            }
        }

        // Step 7. Verify that are strongly reachable referent was not cleared
        if (RETAIN_REF.get() == null) {
            throw new RuntimeException("Strongly reachable referent was cleared: " + RETAIN_REF);
        }
    }
}

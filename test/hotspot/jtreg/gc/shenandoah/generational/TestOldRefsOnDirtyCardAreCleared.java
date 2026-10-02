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
package gc.shenandoah.generational;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import jdk.test.whitebox.WhiteBox;

/*
 * @test id=generational
 * @bug 8393426
 * @requires vm.gc.Shenandoah
 * @summary The remembered set scan must not inadvertently keep old referents on dirty cards alive.
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -Xms512m -Xmx512m
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:-DisableExplicitGC -XX:+ExplicitGCInvokesConcurrent
 *      gc.shenandoah.generational.TestOldRefsOnDirtyCardAreCleared
 */
public class TestOldRefsOnDirtyCardAreCleared {
    // Use a subclass so that we can reliably dirty the card for this weak reference
    static class WeakReferenceWithYoungPointer extends WeakReference<Object> {
        public Object youngPointer;
        public WeakReferenceWithYoungPointer(Object object) {
            super(object);
        }
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static final List<WeakReferenceWithYoungPointer> WEAK_REFS = new ArrayList<>();
    private static final List<Object> STRONG = new ArrayList<>();

    private static boolean allAreInOld(List<?> objects) {
        for (Object obj : objects) {
            if (!WB.isObjectInOldGen(obj)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        final int REF_COUNT = 8;

        // Step 1. Make references with strongly reachable referents. Making multiples here
        // helps the test avoid the false positive scenario where the `youngPointer` ends up
        // on a different card.
        for (int i = 0; i < REF_COUNT; ++i) {
            Object obj = new Object();
            WeakReferenceWithYoungPointer wr = new WeakReferenceWithYoungPointer(obj);
            WEAK_REFS.add(wr);
            STRONG.add(obj);
        }

        // Step 2. Run full GCs until all the references and their referents are promoted
        final int MAX_FULL_GCS = 5;
        int tries = 0;
        while (!allAreInOld(WEAK_REFS) || !allAreInOld(STRONG)) {
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

        // Step 5. Run an old GC cycle. This should clear out the weak referents
        WB.shenandoahOldGC();

        // Step 6. Check how many weak references still have referents (should be zero)
        WEAK_REFS.removeIf(w -> w.get() == null);
        if (!WEAK_REFS.isEmpty()) {
            throw new RuntimeException("Uncleared Weak Refs=" + WEAK_REFS.size());
        }
    }
}

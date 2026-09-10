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
 * @test id=default
 * @bug 8390662
 * @summary Check the HotCodeCollector can relocate nmethods containing
 *          virtual call sites. Inline cache access requires the IC lock
 *          under GCs with concurrent class unloading (ZGC, Shenandoah).
 * @requires vm.compiler2.enabled & vm.opt.SegmentedCodeCache != false
 * @run main/othervm -XX:+SegmentedCodeCache -XX:+UnlockExperimentalVMOptions -XX:+HotCodeHeap
 *                   -XX:+NMethodRelocation -XX:HotCodeIntervalSeconds=0 -XX:HotCodeSampleSeconds=5
 *                   -XX:HotCodeStablePercent=-1 -XX:HotCodeSamplePercent=100 -XX:HotCodeStartupDelaySeconds=0
 *                   compiler.hotcode.HotCodeCollectorVirtualCalls
 */

/*
 * @test id=ZGC
 * @bug 8390662
 * @requires vm.compiler2.enabled & vm.opt.SegmentedCodeCache != false & vm.gc.Z
 * @run main/othervm -XX:+UseZGC
 *                   -XX:+SegmentedCodeCache -XX:+UnlockExperimentalVMOptions -XX:+HotCodeHeap
 *                   -XX:+NMethodRelocation -XX:HotCodeIntervalSeconds=0 -XX:HotCodeSampleSeconds=5
 *                   -XX:HotCodeStablePercent=-1 -XX:HotCodeSamplePercent=100 -XX:HotCodeStartupDelaySeconds=0
 *                   compiler.hotcode.HotCodeCollectorVirtualCalls
 */

/*
 * @test id=Shenandoah
 * @bug 8390662
 * @requires vm.compiler2.enabled & vm.opt.SegmentedCodeCache != false & vm.gc.Shenandoah
 * @run main/othervm -XX:+UseShenandoahGC
 *                   -XX:+SegmentedCodeCache -XX:+UnlockExperimentalVMOptions -XX:+HotCodeHeap
 *                   -XX:+NMethodRelocation -XX:HotCodeIntervalSeconds=0 -XX:HotCodeSampleSeconds=5
 *                   -XX:HotCodeStablePercent=-1 -XX:HotCodeSamplePercent=100 -XX:HotCodeStartupDelaySeconds=0
 *                   compiler.hotcode.HotCodeCollectorVirtualCalls
 */

package compiler.hotcode;

public class HotCodeCollectorVirtualCalls {

    private static final long RUN_MILLIS = 60_000;

    private abstract static class Base {
        abstract int f(int x);
    }

    private static class A extends Base {
        int f(int x) { return x + 1; }
    }

    private static class B extends Base {
        int f(int x) { return x + 2; }
    }

    private static class C extends Base {
        int f(int x) { return x + 3; }
    }

    private static class D extends Base {
        int f(int x) { return x + 4; }
    }

    // Four receiver classes make the call site megamorphic.
    private static final Base[] RECEIVERS = { new A(), new B(), new C(), new D() };

    private static int hot(int i) {
        int s = 0;
        for (int j = 0; j < 1000; j++) {
            s += RECEIVERS[(i + j) & 3].f(j);
        }
        return s;
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        long s = 0;
        while (System.currentTimeMillis() - start < RUN_MILLIS) {
            s += hot((int) s);
        }
        System.out.println("Checksum: " + s);
    }
}

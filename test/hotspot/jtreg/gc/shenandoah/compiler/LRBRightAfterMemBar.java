/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8237007
 * @summary Shenandoah: assert(_base == Tuple) failure during C2 compilation
 * @key gc
 * @requires vm.flavor == "server"
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 *
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-BackgroundCompilation -XX:+UseShenandoahGC LRBRightAfterMemBar
 *
 */

public class LRBRightAfterMemBar {
    private static Object field1;
    private static Object field2;
    static volatile int barrier;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(true, true, new Object());
            test(false, false, new Object());
        }
    }

    private static Object test(boolean flag, boolean flag2, Object o2) {
        for (int i = 0; i < 10; i++) {
            barrier = 0x42; // Membar
            if (o2 == null) { // hoisted out of loop
            }
            // The following line is converted to a CMove with an out
            // of loop control once the null check above is
            // hoisted. The CMove is pinned right after the membar and
            // assigned the membar as control.
            Object o = flag ? field1 : field2;
            if (flag2) {
                return o;
            }
        }

        return null;
    }
}

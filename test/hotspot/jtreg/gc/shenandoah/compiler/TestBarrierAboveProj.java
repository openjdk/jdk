/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

/*
 * @test
 * bug 8281811
 * @summary assert(_base == Tuple) failed: Not a Tuple after JDK-8280799
 * @requires vm.gc.Shenandoah
 * @run main/othervm -XX:+UseShenandoahGC -XX:-BackgroundCompilation -XX:LoopMaxUnroll=1 TestBarrierAboveProj
 */


public class TestBarrierAboveProj {
    private static C objField = new C();
    private static final Object[] arrayField = new Object[1000];
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1();
            test2();
        }
    }

    private static float test1() {
        float v = 1;
        for (int i = 1; i < 1000; i++) {
            if (objField == arrayField[i]) {
                return v;
            }
            v *= 2;
        }
        return v;
    }

    private static float test2() {
        float v = 1;
        volatileField = 0x42;
        for (int i = 1; i < 1000; i++) {
            if (objField == arrayField[i]) {
                return v;
            }
            v *= 2;
        }
        return v;
    }

    private static class C {
        public float floatField;
    }
}

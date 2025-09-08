/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8342496
 * @summary C2/Shenandoah: SEGV in compiled code when running jcstress
 * @requires vm.flavor == "server"
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:+UseShenandoahGC -XX:LoopMaxUnroll=0
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:+StressLCM -XX:StressSeed=270847015
 *                   TestLoadBypassesNullCheck
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:+UseShenandoahGC -XX:LoopMaxUnroll=0
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:+StressLCM
 *                   TestLoadBypassesNullCheck
 *
 */

public class TestLoadBypassesNullCheck {
    private static A fieldA = new A();
    private static Object fieldO = new Object();
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1();
        }
        fieldA = null;
        try {
            test1();
        } catch (NullPointerException npe) {
        }
    }

    private static boolean test1() {
        for (int i = 0; i < 1000; i++) {
            volatileField = 42;
            A a = fieldA;
            Object o = a.fieldO;
            if (o == fieldO) {
                return true;
            }
        }
        return false;
    }

    private static class A {
        public Object fieldO;
    }
}

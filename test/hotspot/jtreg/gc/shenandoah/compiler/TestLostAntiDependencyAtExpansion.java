/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8358334
 * @summary C2/Shenandoah: incorrect execution with Unsafe
 * @requires vm.gc.Shenandoah
 * @modules java.base/jdk.internal.misc:+open
 *
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:+UseShenandoahGC
 *                   TestLostAntiDependencyAtExpansion
 *
 *
 */

import jdk.internal.misc.Unsafe;

public class TestLostAntiDependencyAtExpansion {
    static final jdk.internal.misc.Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] args) {
        long addr = UNSAFE.allocateMemory(8);
        for (int i = 0; i < 20_000; i++) {
            UNSAFE.putLong(addr, 42L);
            long res = test1(addr);
            if (res != 42L) {
                throw new RuntimeException("Incorrect result: " + res);
            }
        }
    }

    static class A {
        long field;
    }

    static A a = new A();

    private static long test1(long addr) {
        long tmp = UNSAFE.getLong(addr);

        UNSAFE.putLong(addr, 0L);

        return tmp + a.field;
    }

}

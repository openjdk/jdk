
/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug JDK-8391971
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:-UseOnStackReplacement ${test.main.class}
 */

package compiler.splitif;
import jdk.internal.misc.Unsafe;

public class TestSplitIfLoadStore {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    int intField;
    static int intField2;
    static final long intFieldOffset;
    static {
        try {
            intFieldOffset = UNSAFE.objectFieldOffset(TestSplitIfLoadStore.class.getDeclaredField("intField"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    static void main() {
        TestSplitIfLoadStore test = new TestSplitIfLoadStore();
        for (int i = 0; i < 20_000; i++) {
            test1(test, 0);
            test1(test, 100);
        }
    }

    private static void test1(TestSplitIfLoadStore test, int v) {
        if (v < 42) {
            intField2 = 42;
        } else {
            intField2 = 0x42;
        }
        UNSAFE.compareAndSetInt(test, intFieldOffset, 0, 1);
        if (v < 42) {
            intField2 = 42;
        } else {
            intField2 = 0x42;
        }
    }
}

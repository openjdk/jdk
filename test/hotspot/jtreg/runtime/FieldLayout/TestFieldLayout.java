/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;

/*
 * @test
 * @bug 8341471
 * @summary Reversed field layout caused by unstable sorting
 * @modules java.base/jdk.internal.misc
 * @run main/othervm TestFieldLayout
 */

public class TestFieldLayout {

    private static final Unsafe U = Unsafe.getUnsafe();

    public static void main(String[] args) throws Exception {

        boolean endResult = true;
        long previous = 0;

        for (Field f : Test.class.getDeclaredFields()) {
            long current = U.objectFieldOffset(f);
            if (current < previous) {
                System.out.printf("FAILED: field %s offset %d previous %d\n",
                                  f.getName(), current, previous);
                endResult = false;
            }
            previous = current;
        }

        System.out.println(endResult ? "Test PASSES" : "Test FAILS");
        if (!endResult) {
            throw new Error("Test failed");
        }
    }

    public class Test {
        char a000;
        char a001;
        char a002;
        char a003;
        char a004;
        char a005;
        char a006;
        char a007;
        char a008;
        char a009;
        char a00a;
        char a00b;
    }

}


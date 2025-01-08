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
 * @bug 8346184
 * @summary C2: assert(has_node(i)) failed during split thru phi
 *
 * @run main/othervm -XX:-BackgroundCompilation TestLoadSplitThruPhiNull
 * @run main/othervm -XX:-BackgroundCompilation -XX:-ReduceFieldZeroing TestLoadSplitThruPhiNull
 * @run main TestLoadSplitThruPhiNull
 *
 */

public class TestLoadSplitThruPhiNull {
    private static Object[] fieldArray;
    private static Object fieldObject;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(true);
            test1(false);
        }
    }

    private static Object test1(boolean flag) {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {

                }
            }
        }
        Object[] array = new Object[10];
        fieldArray = array;
        int i;
        for (i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {

            }
        }
        Object v = array[i-10];
        if (flag) {
            array[0] = new Object();
        }
        return array[i-10];
    }
}

/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8275276
 * @summary Test loop unswitching with flat array checks.
 * @enablePreview
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,TestLoopUnswitchingWithFlatArrayCheck::test compiler.valhalla.inlinetypes.TestLoopUnswitchingWithFlatArrayCheck
 */

package compiler.valhalla.inlinetypes;

public class TestLoopUnswitchingWithFlatArrayCheck {

    Object[] objectArray = new Object[1000];

    boolean test(Object o) {
        return false;
    }

    private Object test(int start, int end) {
        Object res = null;
        Object[] array = objectArray;
        if (array == null) {
           return null;
        }
        for (int i = start; ; i++) {
            if (test(array[i])) {
                continue;
            }
            if (i == end) {
                break;
            }
        }
        for (int i = 0; i < 100; i++) {
            for (; i < 100; i++) {
                res = array[i];
            }
        }
        return res;
    }

    public static void main(String[] args) {
        TestLoopUnswitchingWithFlatArrayCheck t = new TestLoopUnswitchingWithFlatArrayCheck();
        t.test(0, 10);
    }
}

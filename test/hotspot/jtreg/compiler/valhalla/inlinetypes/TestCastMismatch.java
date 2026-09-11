/*
 * Copyright (c) 2023, 2024, Arm Limited. All rights reserved.
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
 * @bug 8309650
 * @summary Fix mismatch inline type issue during method calls
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   compiler.valhalla.inlinetypes.TestCastMismatch
 */

package compiler.valhalla.inlinetypes;

import java.util.Random;
import jdk.test.lib.Utils;

public class TestCastMismatch {
    private static int LOOP_COUNT = 50000;

    private static final Random RD = Utils.getRandomInstance();

    public static MultiValues add(MultiValues v1, MultiValues v2) {
        return v1.factory(v1.value1() + v2.value1(), v1.value2() + v2.value2());
    }

    public static void main(String[] args) {
        Point p1 = new Point(RD.nextInt(), RD.nextInt());
        Point p2 = new Point(RD.nextInt(), RD.nextInt());
        for (int i = 0; i < LOOP_COUNT; i++) {
            p1 = (Point) add(p1, p2);
        }

        System.out.println("PASS");
    }

    static abstract value class MultiValues {
        public abstract int value1();
        public abstract int value2();
        public abstract MultiValues factory(int value1, int value2);
    }

    static value class Point extends MultiValues {
        private int x;
        private int y;

        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int value1() {
            return x;
        }

        @Override
        public int value2() {
            return y;
        }

        @Override
        public Point factory(int value1, int value2) {
            return new Point(value1, value2);
        }
    }
}


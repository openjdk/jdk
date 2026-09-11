/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.valhalla.inlinetypes;

/*
 * @test
 * @bug 8370769
 * @summary verify that value object expansion should not be unbounded.
 * @enablePreview
 * @run main/othervm -XX:MaxNodeLimit=10000 -XX:NodeLimitFudgeFactor=200 -Xcomp
 *                   -XX:CompileOnly=${test.main.class}::test -XX:CompileCommand=dontinline,*V0::*
 *                   ${test.main.class}
 */
public class TestExplosiveValueClass {
    private static value class V0 {
        V1 x0;
        V1 x1;
        V1 x2;
        V1 x3;
        V1 x4;
        V1 x5;
        V1 x6;
        V1 x7;
        V1 x8;
        V1 x9;

        V0() {
            x0 = new V1();
            x1 = new V1();
            x2 = new V1();
            x3 = new V1();
            x4 = new V1();
            x5 = new V1();
            x6 = new V1();
            x7 = new V1();
            x8 = new V1();
            x9 = new V1();
            super();
        }
    }

    private static value class V1 {
        V2 x0;
        V2 x1;
        V2 x2;
        V2 x3;
        V2 x4;
        V2 x5;
        V2 x6;
        V2 x7;
        V2 x8;
        V2 x9;

        V1() {
            x0 = new V2();
            x1 = new V2();
            x2 = new V2();
            x3 = new V2();
            x4 = new V2();
            x5 = new V2();
            x6 = new V2();
            x7 = new V2();
            x8 = new V2();
            x9 = new V2();
            super();
        }
    }

    private static value class V2 {
        V3 x0;
        V3 x1;
        V3 x2;
        V3 x3;
        V3 x4;
        V3 x5;
        V3 x6;
        V3 x7;
        V3 x8;
        V3 x9;

        V2() {
            x0 = new V3();
            x1 = new V3();
            x2 = new V3();
            x3 = new V3();
            x4 = new V3();
            x5 = new V3();
            x6 = new V3();
            x7 = new V3();
            x8 = new V3();
            x9 = new V3();
            super();
        }
    }

    private static value class V3 {
        V4 x0;
        V4 x1;
        V4 x2;
        V4 x3;
        V4 x4;
        V4 x5;
        V4 x6;
        V4 x7;
        V4 x8;
        V4 x9;

        V3() {
            x0 = new V4();
            x1 = new V4();
            x2 = new V4();
            x3 = new V4();
            x4 = new V4();
            x5 = new V4();
            x6 = new V4();
            x7 = new V4();
            x8 = new V4();
            x9 = new V4();
            super();
        }
    }

    private static value class V4 {
        int x0;
        int x1;
        int x2;
        int x3;
        int x4;
        int x5;
        int x6;
        int x7;
        int x8;
        int x9;

        static int counter = 0;

        V4() {
            x0 = counter++;
            x1 = counter++;
            x2 = counter++;
            x3 = counter++;
            x4 = counter++;
            x5 = counter++;
            x6 = counter++;
            x7 = counter++;
            x8 = counter++;
            x9 = counter++;
            super();
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }

    private static V0 test() {
        return new V0();
    }
}

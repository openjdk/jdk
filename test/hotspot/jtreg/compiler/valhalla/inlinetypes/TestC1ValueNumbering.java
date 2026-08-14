/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test value numbering behaves correctly with flat fields.
 * @library /testlibrary /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1 -ea
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestC1ValueNumbering::*
 *                   compiler.valhalla.inlinetypes.TestC1ValueNumbering
 */

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

public class TestC1ValueNumbering {
    public TestC1ValueNumbering() {
        p = new Point(0, 0);
        super();
    }

    @LooselyConsistentValue
    static value class Point {
        int x;
        int y;

        public Point() {
            x = 0;
            y = 0;
        }

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @NullRestricted
    Point p;

    // Notes on test 1:
    // 1 - asserts are important create several basic blocks (asserts create branches)
    // 2 - local variables x, y must be read in the same block as the putfield
    static void test1() {
        Point p = new Point(4,5);
        TestC1ValueNumbering test = new TestC1ValueNumbering();
        assert test.p.x == 0;
        assert test.p.y == 0;
        test.p = p;
        int x = test.p.x;
        int y = test.p.y;
        Asserts.assertEQ(x, 4, "Bad field value");
        Asserts.assertEQ(y, 5, "Bad field value");
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            test1();
        }
    }
}

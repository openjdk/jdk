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

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8384805
 * @summary Test strict final field of an abstract class
 * @enablePreview
 * @library /test/lib /
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileOnly=${test.main.class}::test
 *                   -XX:CompileCommand=dontinline,${test.main.class}::call ${test.main.class}
 */
public class TestAbstractStrictFinalField {
    private static value abstract class P {
        int v;

        P(int v) {
            this.v = v;
            super();
        }
    }

    private static value class C1 extends P {
        C1(int v) {
            super(v);
        }
    }

    private static value class C2 extends P {
        C2(int v) {
            super(v);
        }
    }

    private static class Q {
        int v;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20000; i++) {
            Asserts.assertEQ(1, test(true, 1));
            Asserts.assertEQ(1, test(false, 1));
        }
    }

    private static int test(boolean b, int v) {
        P p;
        if (b) {
            p = new C1(v);
        } else {
            p = new C2(v);
        }

        Q q = new Q();
        return p.v;
    }
}

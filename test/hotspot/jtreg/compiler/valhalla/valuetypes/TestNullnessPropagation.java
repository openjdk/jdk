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

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;
import compiler.valhalla.valuetypes.ValueTypes;
import jdk.test.lib.Asserts;

import static compiler.lib.ir_framework.IRNode.LSHIFT_L;

/*
 * @test
 * @bug 8395611
 * @summary Test nullness propagation during parsing
 * @library /test/lib  /
 * @enablePreview
 * @run driver ${test.main.class}
 */

public class TestNullnessPropagation {

    public static void main(String[] args) {
        ValueTypes.getFramework()
                   .addFlags("--enable-preview")
                   .start();
    }

    static value class MyValue {
        Object o = new Object();
    }

    static class Holder {
        MyValue v;
    }

    // Why IR verification is looking for a LSHIFT_L node?
    // Without the nullness propagation fix, C2 reconstructs the value v using
    // a formulae like this one:
    //   (I2L(null_marker) << 32) | (I2L(L2I(CastP2X(EncodeP(v.o_ref)))) & 0ffff_ffff)
    // The value of the null marker dynamically read from a buffered value or
    // a method argument has to be shifted to be put at the right place.
    // With the nullness propagation fixed, C2 uses a ConL node to directly get
    // the null_marker bit set in place, and no shift operation is needed.
    @Test
    @IR(failOn = {LSHIFT_L})
    public Holder test0(MyValue v) {
        if (v == null) {
            return null;
        }
        Holder h = new Holder();
        h.v = v;
        return h;
    }

    @Run(test = "test0")
    public void test0_verifier() {
        MyValue v = new MyValue();
        Holder h = test0(v);
        Asserts.assertNotNull(h);
        Asserts.assertEQ(h.v, v);
        Holder nh = test0(null);
        Asserts.assertNull(nh);
    }

    // Same IR verification rule as for test0
    @Test
    @IR(failOn = {LSHIFT_L})
    public Holder test1(MyValue v) {
        if (v != null) {
            Holder h = new Holder();
            h.v = v;
            return h;
        }
        return null;
    }

    @Run(test = "test1")
    public void test1_verifier() {
        MyValue v = new MyValue();
        Holder h = test1(v);
        Asserts.assertNotNull(h);
        Asserts.assertEQ(h.v, v);
        Holder nh = test1(null);
        Asserts.assertNull(nh);
    }
}
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

/*
 * @test
 * @bug 8388441
 * @summary Test acmp optimization when operands become known after macro expansion
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:-TieredCompilation -Xbatch ${test.main.class}
 */

import jdk.test.lib.Asserts;

public class TestSubstitutabilityExpansionAfterMacro {
    record Box(Object value) { }
    value record MyValue(Object value) { }

    static boolean equals(Object a, Object b) {
        return a == b;
    }

    // Below tests trigger InlineTypeNode::can_emit_substitutability_check only
    // after macro expansion.

    // EA leaves the right operand as Phi(InlineType<MyValue>, LoadN<Object>).
    // The cast creates a buffered InlineTypeNode whose oop is a cast of that phi.
    // After InlineTypeNode removal after macro expansion, both operands are equivalent.
    static boolean test1(boolean b) {
        Box box = b ? new Box(new MyValue(null)) : new Box(null);
        if (box.value == null) {
            box = new Box(new MyValue(null));
        }
        return equals((MyValue) box.value, box.value);
    }

    // InlineTypeNode removal changes Phi(InlineType<Integer>(oop=null), exact Object)
    // to Phi(null, exact Object) as the right operand. The phi then becomes an exact
    // nullable Object and can_be_inline_type() changes to false after macro expansion.
    static boolean test2(Object obj, boolean b) {
        return equals(obj, b ? (Integer) null : new Object());
    }

    // Both operands are phis of an InlineTypeNode and its oop. After InlineTypeNode
    // removal, both phis collapse to the same oop and the operands become equivalent
    // after macro expansion.
    static boolean test3(Object obj, boolean b) {
        Object left = b ? (MyValue) obj : obj;
        Object right = b ? obj : (MyValue) obj;
        // Use local acmp for fresh profiling
        return left == right;
    }

    public static void main(String[] args) {
        // Warmup and profile acmp with identity operands
        for (int i = 0; i < 10_000; i++) {
            equals(args, args);
        }
        Object obj = new Object();
        MyValue val = new MyValue(obj);
        for (int i = 0; i < 50_000; i++) {
            boolean b = (i & 1) == 0;
            Asserts.assertEQ(test1(b), true);
            Asserts.assertEQ(test2(b ? null : obj, b), b);
            Asserts.assertEQ(test3(val, b), true);
        }
    }
}


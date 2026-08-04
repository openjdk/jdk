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

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @summary Test loads from a dead InlineTypeNode with top field values.
 * @bug 8384202
 * @requires vm.compiler2.enabled
 * @requires (vm.opt.PreloadClasses == null | vm.opt.PreloadClasses == "true")
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class}
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+StressIGVN -XX:StressSeed=8 -XX:+AlwaysIncrementalInline
 *                   ${test.main.class}
 */
public value class TestDeadInlineTypeFieldOffset {
    MyValue valueField1;
    @NullRestricted
    MyValue valueField2;

    TestDeadInlineTypeFieldOffset(MyValue valueField1, MyValue valueField2) {
        this.valueField1 = valueField1;
        this.valueField2 = valueField2;
    }

    TestDeadInlineTypeFieldOffset test1() {
        return new TestDeadInlineTypeFieldOffset(null, valueField2);
    }

    TestDeadInlineTypeFieldOffset test2() {
        return new TestDeadInlineTypeFieldOffset(valueField1, null);
    }

    @LooselyConsistentValue
    static value class NestedValue {
        int x;
        int y;

        NestedValue(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @LooselyConsistentValue
    static value class MyValue {
        NestedValue v1;
        @NullRestricted
        NestedValue v2;

        MyValue(NestedValue v1, NestedValue v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    static TestDeadInlineTypeFieldOffset test(TestDeadInlineTypeFieldOffset vt) {
        vt = vt.test1();
        try {
            vt = vt.test2();
            throw new RuntimeException();
        } catch (NullPointerException e) {
            // Expected.
        }
        return vt;
    }

    public static void main(String[] args) {
        MyValue val = new MyValue(null, new NestedValue(5, 6));
        TestDeadInlineTypeFieldOffset vt = new TestDeadInlineTypeFieldOffset(null, val);
        for (int i = 0; i < 10_000; ++i) {
            vt = test(vt);
        }
    }
}


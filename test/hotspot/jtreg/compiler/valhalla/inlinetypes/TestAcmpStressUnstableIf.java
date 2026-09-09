/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8367244
 * @summary Ensure the stress counter is wired correctly with StressUnstableIf for acmp.
 * @enablePreview
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler/valhalla/inlinetypes/TestAcmpStressUnstableIf.test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressUnstableIfTraps -XX:StressSeed=3862475856
 *                   compiler.valhalla.inlinetypes.TestAcmpStressUnstableIf
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler/valhalla/inlinetypes/TestAcmpStressUnstableIf.test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressUnstableIfTraps
 *                   compiler.valhalla.inlinetypes.TestAcmpStressUnstableIf
 * @run main compiler.valhalla.inlinetypes.TestAcmpStressUnstableIf
 */

package compiler.valhalla.inlinetypes;

public class TestAcmpStressUnstableIf {
    static value class MyValue {
        int x;

        public MyValue(int x) {
            this.x = x;
        }
    }

    public static void main(String[] args) {
        MyValue val = new MyValue(123456);
        MyValue val_copy = new MyValue(123456);
        MyValue val_diff = new MyValue(123456 + 1);

        test(val, val_copy, val_diff);
    }

    public static void test(MyValue val, MyValue val_copy, MyValue val_diff) {
        for (int i = 0; i < 30_000; ++i) {
            if (val != val_copy) {
                return;
            }
            if (val == val_diff) {
                return;
            }
        }
    }
}

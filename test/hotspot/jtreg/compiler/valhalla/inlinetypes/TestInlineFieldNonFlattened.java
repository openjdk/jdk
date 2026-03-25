/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.*;
import java.util.Random;

import jdk.test.lib.Utils;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @bug 8311219
 * @summary VM option "UseFieldFlattening" does not work well.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:-TieredCompilation
 *                   -XX:-UseFieldFlattening
 *                   compiler.valhalla.inlinetypes.TestInlineFieldNonFlattened
 */

public class TestInlineFieldNonFlattened {
    static class MyClass {
        @NullRestricted
        public final MyValue v1;

        public MyValue v2;

        public MyClass(MyValue v) {
            v2 = v;
            v1 = new MyValue(5);
            super();
        }
    }

    @LooselyConsistentValue
    static value class MyValue {
        public int field;

        public MyValue(int f) {
            field = f;
        }
    }

    private static final Random RD = Utils.getRandomInstance();

    static MyClass c;

    static {
        c = new MyClass(new MyValue(RD.nextInt(100)));
    }

    static int f;

    @Test
    @IR(counts = {IRNode.LOAD_N, "2"})
    public static void testNonFlattenedField() {
        f = c.v2.field;
    }

    @Test
    @IR(counts = {IRNode.LOAD_N, "2"})
    public static void testNonFlattenedFinalField() {
        f = c.v1.field;
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--enable-preview",
                               "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                               "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                               "-XX:-TieredCompilation",
                               "-XX:-UseFieldFlattening")
                     .start();
    }
}


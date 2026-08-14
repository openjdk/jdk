/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8260363
 * @summary C2 compilation fails with assert(n->Opcode() != Op_Phi) failed: cannot match
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:-BackgroundCompilation compiler.valhalla.inlinetypes.TestFlatArrayAliasesCardMark
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

@LooselyConsistentValue
value class Test0 {
    int x = 42;
    short[] array = new short[7];
}

public class TestFlatArrayAliasesCardMark {
    int f = 1;

    public void method1(Test0[] array) {
        for (int i = 0; i < 100; ++i) {
            array[0] = array[0];
            for (int j = 0; j < 10; ++j) {
                for (int k = 0; k < 10; ++k) {
                    f = 42;
                }
            }
        }
    }

    public static void main(String[] args) {
        TestFlatArrayAliasesCardMark t = new TestFlatArrayAliasesCardMark();
        Test0[] array = (Test0[])ValueClass.newNullRestrictedNonAtomicArray(Test0.class, 1, new Test0());
        array[0] = new Test0();

        for (int l1 = 0; l1 < 10_000; ++l1) {
            t.method1(array);
        }
    }
}

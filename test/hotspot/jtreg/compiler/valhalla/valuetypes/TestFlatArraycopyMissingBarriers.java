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
 * @bug 8397827
 * @summary Test ZGC barriers when C2 copies flat arrays containing oops.
 * @requires vm.gc.Z & vm.compiler2.enabled
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UseZGC -Xms256m -Xmx256m -Xbatch -XX:-TieredCompilation
 *                   -XX:PerMethodTrapLimit=0 -XX:CompileOnly=${test.main.class}::test
 *                   -XX:CompileCommand=exclude,${test.main.class}::copyWithObj
 *                   ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ${test.main.class}
 */


package compiler.valhalla.valuetypes;

import jdk.internal.value.ValueClass;
import jdk.test.whitebox.WhiteBox;

public class TestFlatArraycopyMissingBarriers {
    private static final int COPY_LENGTH = 64;
    private static final int ARRAY_LENGTH = 300_000;
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static final V[] SRC_ARRAY = (V[])ValueClass.newNullRestrictedNonAtomicArray(V.class, ARRAY_LENGTH, new V(null));
    private static final V[] DEST_ARRAY =(V[])ValueClass.newNullRestrictedNonAtomicArray(V.class, ARRAY_LENGTH, new V(null));

    public static void main(String[] args) throws Exception {
        System.gc(); // Moves SRC_ARRAY and DEST_ARRAY to old gen
        for (int i = 0; i < 10_000; i++) {
            test(); // Compile
        }

        // Clear remembered-set state
        WB.youngGC();

        Object obj = new Object(); // Young object
        SRC_ARRAY[0] = new V(obj); // SRC_ARRAY[0].obj: young object

        // Copies SRC_ARRAY[0] to DEST_ARRAY[0] = V(obj) but without barriers
        // -> DEST_ARRAY[0].obj: young object but not recorded due to missing barriers
        test();

        for (int i = 0; i < 5; i++) {
            // Allocate some garbage and do 5 complete GC cycles to ensure that young 'obj' is moved. When this happens
            // the young DEST_ARRAY[0].obj is forgotten to be updated. A later old-mark phase then crashes with a
            // segfault when treating DEST_ARRAY[0].obj as an oop.
            allocateGarbage();
            System.gc();
        }
    }

    private static void test() {
        System.arraycopy(SRC_ARRAY, 0, DEST_ARRAY, 0, COPY_LENGTH);
    }

    private static void allocateGarbage() {
        Object[] arr = new Object[100_000];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new Object();
        }
    }

    static value class V {
        Object obj;

        V(Object obj) {
            this.obj = obj;
        }
    }
}

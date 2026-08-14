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
 * @bug 8353180
 * @summary Test that meeting TypeAry*Ptr works with new layouts and does not trigger "not monotonic" assert.
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm -Xcomp -XX:CompileOnly=*TestMeetingAryPtr*::test* -XX:+UnlockDiagnosticVMOptions -XX:+StressCCP
 *                   -XX:RepeatCompilation=100 compiler.valhalla.inlinetypes.TestMeetingAryPtr
 * @run main         compiler.valhalla.inlinetypes.TestMeetingAryPtr
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;

public class TestMeetingAryPtr {
    static V vFld = new V(34);

    static V[] vArrFlat = new V[100];
    static final V[] vArrFinalNullable = (V[]) ValueClass.newNullableAtomicArray(V.class, 100);
    static final V[] vArrFinalNullFree = (V[]) ValueClass.newNullRestrictedNonAtomicArray(V.class, 100, new V(0));
    static final V[] vArrFinalNullFree2 = (V[]) ValueClass.newNullRestrictedNonAtomicArray(V.class, 100, new V(0));

    static boolean flag;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            testNonConstant();
            testConstantDifferentNullNess();
            testConstantSameNullNess();
            flag = !flag;
        }
    }

    static void testNonConstant() {
        for (int i = 0; i < 100; ++i) {
            vFld = vArrFlat[i];
        }
    }

    static void testConstantDifferentNullNess() {
        // Meeting:      ConP(flat+nullable)   ConP(flat+null-free)
        V[] arr = flag ? vArrFinalNullable   : vArrFinalNullFree;
        // Phi for arr after meet: flat+maybe-null-free+exact
        // -> Wrongly set to exact even though it's maybe null free only.
        //    This causes an assertion failure during CCP.
        vFld = arr[2];
    }

    static void testConstantSameNullNess() {
        V[] arr = flag ? vArrFinalNullable : vArrFinalNullFree2;
        vFld = arr[2];
    }
}

value class V  {
    int x;

    V(int x) {
        this.x = x;
    }
}

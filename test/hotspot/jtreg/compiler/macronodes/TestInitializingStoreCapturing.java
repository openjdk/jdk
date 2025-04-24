/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8327012 8327963
 * @summary Test that initializing store gets captured, i.e. moved before the InitializeNode
 *          and made into a raw-store.
 * @library /test/lib /
 * @run driver compiler.macronodes.TestInitializingStoreCapturing
 */

package compiler.macronodes;

import compiler.lib.ir_framework.*;

public class TestInitializingStoreCapturing {

    static class A {
        float value;
        A(float v) { value = v; }
    };

    static public void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.STORE_F, "= 0"})
    static A testInitializeField() {
        return new A(4.2f);
    }

    @Test
    @IR(counts = {IRNode.STORE_F, "= 0"})
    static float[] testInitializeArray() {
        return new float[] {4.2f};
    }
}

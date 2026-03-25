/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @bug 8364191
 * @summary Test the removal of allocations of objects with atomic flat fields
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestScalarReplaceFlatFields
 */
public class TestScalarReplaceFlatFields {
    @DontInline
    private static void call() {}

    static value class V0 {
        byte v1;
        byte v2;

        V0(int v1, int v2) {
            this.v1 = (byte) v1;
            this.v2 = (byte) v2;
        }
    }

    static value class V1 {
        V0 v;
        short s;

        V1(V0 v, int s) {
            this.v = v;
            this.s = (short) s;
        }
    }

    static class Holder {
        @NullRestricted
        V1 v1;
        V1 v2;

        Holder(V1 v1, V1 v2) {
            this.v1 = v1;
            this.v2 = v2;
            super();
        }
    }

    @Test
    @IR(failOn = IRNode.ALLOC)
    @Arguments(values = {Argument.RANDOM_EACH})
    private static int testField(int v) {
        V1 v1 = new V1(null, v);
        V1 v2 = new V1(new V0(v, v), v);
        Holder h = new Holder(v1, v2);
        call();
        return h.v1.s;
    }

    @Test
    @IR(failOn = IRNode.ALLOC)
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    private static int testArray(int v1, int v2) {
        V1[] array = new V1[2];
        array[0] = new V1(null, v1);
        array[1] = new V1(new V0(v1, v2), v2);
        call();
        return array[1].v.v1;
    }

    public static void main(String[] args) {
        InlineTypes.getFramework()
                .addScenarios(InlineTypes.DEFAULT_SCENARIOS)
                .start();
    }

    // TODO 8376254: C1 bailouts if the type of the nullable flat field is uninitialized
    static final V0 LOAD_V0 = new V0(0, 0);
}

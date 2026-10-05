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

package compiler.vectorapi;

import jdk.incubator.vector.ShortVector;

/*
 * @test
 * @bug 8393338
 * @summary Out-of-range constant vector slice index must throw, not crash C2.
 * @modules jdk.incubator.vector
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.vectorapi.TestVectorSliceOutOfBounds::test
 *                   -XX:CompileCommand=dontinline,java.util.Objects::checkIndex
 *                   -XX:CompileCommand=dontinline,jdk.internal.util.Preconditions::checkIndex
 *                   compiler.vectorapi.TestVectorSliceOutOfBounds
 */

public class TestVectorSliceOutOfBounds {
    static Object test(short[] a, short[] b) {
        try {
            ShortVector.fromArray(ShortVector.SPECIES_512, a, 0)
                       .slice(-271, ShortVector.fromArray(ShortVector.SPECIES_512, b, 0))
                       .intoArray(a, 0);
            throw new RuntimeException("missing IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            return e;
        }
    }

    public static void main(String[] args) {
        short[] a = new short[32];
        ShortVector.fromArray(ShortVector.SPECIES_512, a, 0)
                   .slice(0, ShortVector.fromArray(ShortVector.SPECIES_512, a, 0));
        Object result = test(a, a);
        if (!(result instanceof IndexOutOfBoundsException)) {
            throw new RuntimeException("expected IndexOutOfBoundsException, got " + result);
        }
    }
}

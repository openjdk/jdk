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
 * @bug 8387799
 * @summary Test that a nullable receiver is not endlessly retried for virtual late inlining
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:CompileCommand=delayinline,${test.main.class}::lateInlined
 *                   ${test.main.class}
 */

package compiler.inlining;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.test.lib.Asserts;

public class TestLateInlineNullableReceiver {
    static final VectorMask<Float> MASK = FloatVector.SPECIES_128.maskAll(true);
    // Another (unused) mask to prevent dervirtualization of the 'trueCount' call
    static final VectorMask<Float> OTHER_MASK = FloatVector.SPECIES_64.maskAll(true);

    static VectorMask<?> lateInlined(Object value) {
        return (VectorMask<?>) MASK.getClass().cast(value);
    }

    static int test(Object value) {
        // XOR a vector and make sure it's live at below virtual call
        IntVector vector = IntVector.fromArray(IntVector.SPECIES_128, new int[4], 0).lanewise(VectorOperators.XOR, 0);

        // After late inlining, 'value' is exact but still nullable. C2 will then
        // attempt to strength reduce the 'trueCount' virtual call to a static call.
        int result = lateInlined(value).trueCount();

        return result + vector.lane(0); // Keep the vector live
    }

    public static void main(String[] args) {
        for (int i = 0; i < 200; i++) {
            Asserts.assertEquals(test(MASK), 4);
        }
    }
}


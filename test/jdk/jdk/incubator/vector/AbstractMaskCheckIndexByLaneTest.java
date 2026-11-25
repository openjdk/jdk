/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/*
 * @test
 * @summary Test function AbstractMask.checkIndexByLane
 * @modules jdk.incubator.vector/jdk.incubator.vector:open
 * @author Andreas Chmielewski <a.chmielewski@ibm.com>
 * @author Francisco Ferrari Bihurriet <fferrari@redhat.com>
 */

public class AbstractMaskCheckIndexByLaneTest {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_64;
    private static final ByteVector IOTA_BYTE_LANES =
            ByteVector.broadcast(SPECIES, (byte) 0).addIndex(1);
    private static final TestCase[] TEST_CASES = {
            // =================================================================
            // All active lanes are within bounds.
            // Expected: success (false)
            // =================================================================
            new TestCase(false, 0, 16, 1,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(false, 8, 16, 1,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(false, 0, 128, 8,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(false, 64, 128, 8,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(false, 0, (long) Integer.MAX_VALUE + 16, 1,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(false, (long) Integer.MAX_VALUE + 8,
                    (long) Integer.MAX_VALUE + 16, 1,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(false, 0, (long) Integer.MAX_VALUE + 128, 8,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(false, (long) Integer.MAX_VALUE + 64,
                    (long) Integer.MAX_VALUE + 128, 8,
                    mask(true, true, true, true, true, true, true, true)
            ),

            // =================================================================
            // Upper-bound violation caused by an active lane.
            // At least one active lane resolves to index >= length.
            // Expected: failure (true)
            // =================================================================
            new TestCase(true, 9, 16, 1,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(true, 65, 128, 8,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(true, (long) Integer.MAX_VALUE + 9,
                    (long) Integer.MAX_VALUE + 16, 1,
                    mask(true, true, true, true, true, true, true, true)
            ),
            new TestCase(true, (long) Integer.MAX_VALUE + 65,
                    (long) Integer.MAX_VALUE + 128, 8,
                    mask(true, true, true, true, true, true, true, true)
            ),

            // =================================================================
            // Upper-bound violation exists, but offending lanes are masked off.
            // Out-of-bounds lanes are present but inactive.
            // Expected: success (false)
            // =================================================================
            new TestCase(false, 9, 16, 1,
                    mask(true, true, true, true, true, true, true, false)
            ),
            new TestCase(false, 65, 128, 8,
                    mask(true, true, true, true, true, true, true, false)
            ),
            new TestCase(false, (long) Integer.MAX_VALUE + 9,
                    (long) Integer.MAX_VALUE + 16, 1,
                    mask(true, true, true, true, true, true, true, false)
            ),
            new TestCase(false, (long) Integer.MAX_VALUE + 65,
                    (long) Integer.MAX_VALUE + 128, 8,
                    mask(true, true, true, true, true, true, true, false)
            ),

            // =================================================================
            // No active lanes.
            // Bounds are not checked because every lane is inactive.
            // Expected: success (false)
            // =================================================================
            new TestCase(false, 0, 16, 1,
                    mask(false, false, false, false, false, false, false, false)
            ),
            new TestCase(false, 0, 128, 8,
                    mask(false, false, false, false, false, false, false, false)
            ),
            new TestCase(false, 0, (long) Integer.MAX_VALUE + 16, 1,
                    mask(false, false, false, false, false, false, false, false)
            ),
            new TestCase(false, 0, (long) Integer.MAX_VALUE + 128, 8,
                    mask(false, false, false, false, false, false, false, false)
            ),

            // =================================================================
            // Offset lies beyond the accessible region, but all lanes are
            // masked off.
            // Expected: success (false)
            // =================================================================
            new TestCase(false, 17, 16, 1,
                    mask(false, false, false, false, false, false, false, false)
            ),
            new TestCase(false, 129, 128, 8,
                    mask(false, false, false, false, false, false, false, false)
            ),
            new TestCase(false, (long) Integer.MAX_VALUE + 17,
                    (long) Integer.MAX_VALUE + 16, 1,
                    mask(false, false, false, false, false, false, false, false)
            ),
            new TestCase(false, (long) Integer.MAX_VALUE + 129,
                    (long) Integer.MAX_VALUE + 128, 8,
                    mask(false, false, false, false, false, false, false, false)
            ),

            // =================================================================
            // Offset lies beyond the accessible region and at least one lane
            // is active.
            // Expected: failure (true)
            // =================================================================
            new TestCase(true, 17, 16, 1,
                    mask(true, false, false, false, false, false, false, false)
            ),
            new TestCase(true, 129, 128, 8,
                    mask(true, false, false, false, false, false, false, false)
            ),
            new TestCase(true, (long) Integer.MAX_VALUE + 17,
                    (long) Integer.MAX_VALUE + 16, 1,
                    mask(true, false, false, false, false, false, false, false)
            ),
            new TestCase(true, (long) Integer.MAX_VALUE + 129,
                    (long) Integer.MAX_VALUE + 128, 8,
                    mask(true, false, false, false, false, false, false, false)
            ),

            // =================================================================
            // Negative offset, but all invalid lanes are masked off.
            // Expected: success (false)
            // =================================================================
            new TestCase(false, -3, 16, 1,
                    mask(false, false, false, true, true, true, true, true)
            ),
            new TestCase(false, -3, 128, 8,
                    mask(false, true, true, true, true, true, true, true)
            ),
            new TestCase(false, -3, (long) Integer.MAX_VALUE + 16, 1,
                    mask(false, false, false, true, true, true, true, true)
            ),
            new TestCase(false, -3, 128 + (long) Integer.MAX_VALUE, 8,
                    mask(false, true, true, true, true, true, true, true)
            ),

            // =================================================================
            // Negative offset with at least one invalid lane active.
            // Expected: failure (true)
            // =================================================================
            new TestCase(true, -3, 16, 1,
                    mask(false, false, true, false, false, false, false, false)
            ),
            new TestCase(true, -3, 128, 8,
                    mask(true, false, false, false, false, false, false, false)
            ),
            new TestCase(true, -3, (long) Integer.MAX_VALUE + 16, 1,
                    mask(false, false, true, true, true, true, true, true)
            ),
            new TestCase(true, -3, 128 + (long) Integer.MAX_VALUE, 8,
                    mask(true, false, false, false, false, false, false, false)
            ),

            // =================================================================
            // Special cases
            // =================================================================
            new TestCase(false, 1L << 33, 2, 2,
                    mask(false, false, false, false, false, false, false, false)
            ),
            new TestCase(true, 1L << 33, 2, 2,
                    mask(true, false, false, false, false, false, false, false)
            ),
    };

    static void main(String[] args) throws Exception {
        Method m = Class.forName("jdk.incubator.vector.AbstractMask")
                .getDeclaredMethod("checkIndexByLane",
                        long.class, long.class, Vector.class, int.class);
        m.setAccessible(true);

        for (TestCase tc : TEST_CASES) {
            tc.run(m);
        }
    }

    private static VectorMask<Byte> mask(boolean... bits) {
        return VectorMask.fromValues(SPECIES, bits);
    }

    record TestCase(boolean expectIOOBE, long offset, long length, int esize,
                    VectorMask<Byte> mask) {

        void run(Method m) throws Exception {
            try {
                m.invoke(mask, offset, length, IOTA_BYTE_LANES, esize);
                if (expectIOOBE) {
                    throw new AssertionError("Expected IndexOutOfBoundsException");
                }
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                    throw e;
                }
                if (!expectIOOBE) {
                    throw new AssertionError("Unexpected IndexOutOfBoundsException",
                            e.getCause());
                }
            }
        }
    }
}

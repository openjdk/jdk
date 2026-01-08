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

package compiler.intrinsics;

/*
 * @test
 * @bug 8359344
 * @summary Intrinsic storeMasked can add some control flow before bailing out, leaving a malformed CFG.
 * @modules jdk.incubator.vector
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -XX:TypeProfileLevel=222 -Xbatch
 *                   -XX:CompileCommand=compileonly,jdk.incubator.vector.Long*::intoArray0
 *                   -XX:+AbortVMOnCompilationFailure
 *                    compiler.intrinsics.VectorIntoArrayInvalidControlFlow
 *
 * @run main compiler.intrinsics.VectorIntoArrayInvalidControlFlow
 */

import jdk.incubator.vector.*;

public class VectorIntoArrayInvalidControlFlow {
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_128;
    private static final LongVector longVector;
    private static final long[] longArray = new long[L_SPECIES.length()];
    private static final boolean[] longMask = new boolean[L_SPECIES.length()];
    private static final VectorMask<Long> longVectorMask;

    static {
        for (int i = 0; i < L_SPECIES.length(); i++) {
            longArray[i] = i + 1;
            longMask[i] = L_SPECIES.length() > 1 && i % 2 == 0;
        }
        longVector = LongVector.fromArray(L_SPECIES, longArray, 0);
        longVectorMask = VectorMask.fromArray(L_SPECIES, longMask, 0);
    }

    static long[] test() {
        long[] res = new long[L_SPECIES.length()];
        for(int j = 0; j < 10_000; j++) {
            longVector.intoArray(res, 0, longVectorMask);
        }
        return res;
    }

    static public void main(String[] args) {
        test();
    }
}
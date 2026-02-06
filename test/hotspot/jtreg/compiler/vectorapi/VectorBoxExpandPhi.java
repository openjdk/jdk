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

import jdk.incubator.vector.*;

/*
 * @test
 * @bug 8374903
 * @summary C2 crashes when VectorBox Phi and vector Phi have different regions
 * @modules jdk.incubator.vector
 * @library /test/lib
 *
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,compiler.vectorapi.VectorBoxExpandPhi::test compiler.vectorapi.VectorBoxExpandPhi
 */
public class VectorBoxExpandPhi {
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test();
        }
    }

    public static Object test() {
        var v0 = DoubleVector.broadcast(DoubleVector.SPECIES_128, 1.0);
        var v1 = (FloatVector)v0.convertShape(VectorOperators.Conversion.ofCast(double.class, float.class), FloatVector.SPECIES_64, 0);
        var v2 = (FloatVector)v1.convertShape(VectorOperators.Conversion.ofReinterpret(float.class, float.class), FloatVector.SPECIES_64, 0);
        return v2;
    }
}

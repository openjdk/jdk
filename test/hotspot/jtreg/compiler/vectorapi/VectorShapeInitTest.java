/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8262096
 * @summary Test the initialization of vector shapes
 * @modules jdk.incubator.vector
 * @run main/othervm -XX:MaxVectorSize=8 compiler.vectorapi.VectorShapeInitTest
 * @run main/othervm -XX:MaxVectorSize=4 compiler.vectorapi.VectorShapeInitTest
 */

public class VectorShapeInitTest {
    static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_64;
    static double[] a = new double[64];
    static double[] r = new double[64];

    public static void main(String[] args) {
        DoubleVector av = DoubleVector.fromArray(SPECIES, a, 0);
        av.lanewise(VectorOperators.ABS).intoArray(r, 0);
    }
}

/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.ShortVector;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8278948
 * @summary Intermediate integer promotion vector length encoding is calculated incorrectly on x86
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:CompileThreshold=100 -XX:UseAVX=1
 *                   compiler.vectorapi.Test8278948
 */
public class Test8278948 {
    static final int INVOCATIONS = 10000;

    static final Random random = Utils.getRandomInstance();
    static final byte[] BYTES = new byte[8];
    static final short[] SHORTS = new short[4];
    static final double[] DOUBLES = new double[4];


    public static void main(String[] args) {
        for (int i = 0; i < INVOCATIONS; i++) {
            for (int j = 0; j < DOUBLES.length; j++) {
                BYTES[j] = (byte)random.nextInt();
            }
            bytesToDoubles();
            for (int j = 0; j < DOUBLES.length; j++) {
                Asserts.assertEquals((double)BYTES[j], DOUBLES[j]);
            }

            for (int j = 0; j < DOUBLES.length; j++) {
                SHORTS[j] = (short)random.nextInt();
            }
            shortsToDoubles();
            for (int j = 0; j < DOUBLES.length; j++) {
                Asserts.assertEquals((double)SHORTS[j], DOUBLES[j]);
            }
        }
    }

    static void bytesToDoubles() {
        ((DoubleVector)ByteVector.fromArray(ByteVector.SPECIES_64, BYTES, 0)
                .castShape(DoubleVector.SPECIES_256, 0))
                .intoArray(DOUBLES, 0);
    }

    static void shortsToDoubles() {
        ((DoubleVector)ShortVector.fromArray(ShortVector.SPECIES_64, SHORTS, 0)
                .castShape(DoubleVector.SPECIES_256, 0))
                .intoArray(DOUBLES, 0);
    }
}

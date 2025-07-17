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

/**
* @test
* @bug 8332119
* @summary Incorrect IllegalArgumentException for C2 compiled permute kernel
* @modules jdk.incubator.vector
* @library /test/lib /
* @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:-TieredCompilation -XX:CompileOnly=TestTwoVectorPermute::micro compiler.vectorapi.TestTwoVectorPermute
* @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:-TieredCompilation compiler.vectorapi.TestTwoVectorPermute
* @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:TieredStopAtLevel=3 compiler.vectorapi.TestTwoVectorPermute
*/
package compiler.vectorapi;


import jdk.incubator.vector.*;
import java.util.Arrays;
import java.util.Random;

public class TestTwoVectorPermute {
   public static final VectorSpecies<Float> FSP = FloatVector.SPECIES_256;

   public static void validate(float[] res, float[] shuf, float[] src1, float[] src2) {
       for (int i = 0; i < res.length; i++) {
           float expected = Float.NaN;
           int shuf_index = (int)shuf[i];
           // Exceptional index.
           if (shuf_index < 0 || shuf_index >= FSP.length()) {
               int wrapped_index = (shuf_index & (FSP.length() - 1));
               if (Integer.compareUnsigned(shuf_index, FSP.length()) > 0) {
                   wrapped_index -= FSP.length();
               }
               wrapped_index = wrapped_index < 0 ? wrapped_index + FSP.length() : wrapped_index;
               expected = src2[wrapped_index];
           } else {
               expected = src1[shuf_index];
           }
           if (res[i] != expected) {
              throw new AssertionError("Result mismatch at " + i + " index, (actual = " + res[i] + ") != ( expected " +   expected + " )");
           }
       }
   }

   public static void micro(float[] res, float[] shuf, float[] src1, float[] src2) {
       VectorShuffle<Float> vshuf = FloatVector.fromArray(FSP, shuf, 0).toShuffle();
       VectorShuffle<Float> vshuf_wrapped = vshuf.wrapIndexes();
       FloatVector.fromArray(FSP, src1, 0)
         .rearrange(vshuf_wrapped)
         .blend(FloatVector.fromArray(FSP, src2, 0)
                           .rearrange(vshuf_wrapped),
                           vshuf.laneIsValid().not())
         .intoArray(res, 0);
   }

   public static void main(String [] args) {
       float [] res  = new float[FSP.length()];
       float [] shuf = new float[FSP.length()];
       float [] src1 = new float[FSP.length()];
       float [] src2 = new float[FSP.length()];

       for (int i = 0; i < FSP.length(); i++) {
           shuf[i] = i * 2;
       }
       for (int i = 0; i < FSP.length(); i++) {
           src1[i] = i;
           src2[i] = i + FSP.length();
       }
       for (int i = 0; i < 10000; i++) {
           micro(res, shuf, src1, src2);
       }
       validate(res, shuf, src1, src2);
       for (int i = 0; i < FSP.length(); i++) {
           shuf[i] = -i * 2;
       }
       for (int i = 0; i < 10000; i++) {
           micro(res, shuf, src1, src2);
       }
       validate(res, shuf, src1, src2);
       System.out.println("PASSED");
   }
}

/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import java.util.concurrent.TimeUnit;
import java.util.Random;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector", "-XX:UseAVX=2"})
public class ColumnFilterBenchmark {
    @Param({"1024", "2047", "4096"})
    int size;

    float [] floatinCol;
    float [] floatoutCol;
    float fpivot;

    double [] doubleinCol;
    double [] doubleoutCol;
    double dpivot;

    int [] intinCol;
    int [] intoutCol;
    int ipivot;

    long [] longinCol;
    long [] longoutCol;
    long lpivot;

    static final VectorSpecies<Float> fspecies = FloatVector.SPECIES_256;
    static final VectorSpecies<Double> dspecies = DoubleVector.SPECIES_256;
    static final VectorSpecies<Integer> ispecies = IntVector.SPECIES_256;
    static final VectorSpecies<Long> lspecies = LongVector.SPECIES_256;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(2048);

        floatinCol = new float[size];
        floatoutCol = new float[size];
        fpivot = (float) (size / 2);
        doubleinCol = new double[size];
        doubleoutCol = new double[size];
        dpivot = (double) (size / 2);
        intinCol = new int[size];
        intoutCol = new int[size];
        ipivot = size / 2;
        longinCol = new long[size];
        longoutCol = new long[size];
        lpivot = size / 2;

        for (int i = 4; i < size; i++) {
            floatinCol[i] = r.nextFloat() * size;
            doubleinCol[i] = r.nextDouble() * size;
            intinCol[i] = r.nextInt(size);
            longinCol[i] = (long)intinCol[i];
        }
    }

    @Benchmark
    public void fuzzyFilterIntColumn() {
       int i = 0;
       int j = 0;
       long maskctr = 1;
       int endIndex = ispecies.loopBound(size);
       for (; i < endIndex; i += ispecies.length()) {
           IntVector vec = IntVector.fromArray(ispecies, intinCol, i);
           VectorMask<Integer> pred = VectorMask.fromLong(ispecies, maskctr++);
           vec.compress(pred).intoArray(intoutCol, j);
           j += pred.trueCount();
       }
   }

   @Benchmark
   public void fuzzyFilterLongColumn() {
       int i = 0;
       int j = 0;
       long maskctr = 1;
       int endIndex = lspecies.loopBound(size);
       for (; i < endIndex; i += lspecies.length()) {
           LongVector vec = LongVector.fromArray(lspecies, longinCol, i);
           VectorMask<Long> pred = VectorMask.fromLong(lspecies, maskctr++);
           vec.compress(pred).intoArray(longoutCol, j);
           j += pred.trueCount();
       }
   }

    @Benchmark
    public void filterIntColumn() {
       int i = 0;
       int j = 0;
       int endIndex = ispecies.loopBound(size);
       for (; i < endIndex; i += ispecies.length()) {
           IntVector vec = IntVector.fromArray(ispecies, intinCol, i);
           VectorMask<Integer> pred = vec.compare(VectorOperators.GT, ipivot);
           vec.compress(pred).intoArray(intoutCol, j);
           j += pred.trueCount();
       }
       for (; i < endIndex; i++) {
           if (intinCol[i] > ipivot) {
               intoutCol[j++] = intinCol[i];
           }
       }
   }

   @Benchmark
   public void filterLongColumn() {
       int i = 0;
       int j = 0;
       int endIndex = lspecies.loopBound(size);
       for (; i < endIndex; i += lspecies.length()) {
           LongVector vec = LongVector.fromArray(lspecies, longinCol, i);
           VectorMask<Long> pred = vec.compare(VectorOperators.GT, lpivot);
           vec.compress(pred).intoArray(longoutCol, j);
           j += pred.trueCount();
       }
       for (; i < endIndex; i++) {
           if (longinCol[i] > lpivot) {
               longoutCol[j++] = longinCol[i];
           }
       }
   }

   @Benchmark
   public void filterFloatColumn() {
       int i = 0;
       int j = 0;
       int endIndex = fspecies.loopBound(size);
       for (; i < endIndex; i += fspecies.length()) {
           FloatVector vec = FloatVector.fromArray(fspecies, floatinCol, i);
           VectorMask<Float> pred = vec.compare(VectorOperators.GT, fpivot);
           vec.compress(pred).intoArray(floatoutCol, j);
           j += pred.trueCount();
       }
       for (; i < endIndex; i++) {
           if (floatinCol[i] > fpivot) {
               floatoutCol[j++] = floatinCol[i];
           }
       }
   }

   @Benchmark
   public void filterDoubleColumn() {
       int i = 0;
       int j = 0;
       int endIndex = dspecies.loopBound(size);
       for (; i < endIndex; i += dspecies.length()) {
           DoubleVector vec = DoubleVector.fromArray(dspecies, doubleinCol, i);
           VectorMask<Double> pred = vec.compare(VectorOperators.GT, dpivot);
           vec.compress(pred).intoArray(doubleoutCol, j);
           j += pred.trueCount();
       }
       for (; i < endIndex; i++) {
           if (doubleinCol[i] > dpivot) {
               doubleoutCol[j++] = doubleinCol[i];
           }
       }
   }
}

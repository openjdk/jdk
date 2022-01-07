/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.runtime;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark assesses Record.toString which is implemented by ObjectMethods::makeToString
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ObjectMethods {
    record R0() {}
    record R1(int i) {}
    record R10(int i1,int i2,int i3,int i4,int i5,int i6,int i7,int i8,int i9,int i10) {}
    record R100(int i1,int i2,int i3,int i4,int i5,int i6,int i7,int i8,int i9,int i10,
                int i11,int i12,int i13,int i14,int i15,int i16,int i17,int i18,int i19,int i20,
                int i21,int i22,int i23,int i24,int i25,int i26,int i27,int i28,int i29,int i30,
                int i31,int i32,int i33,int i34,int i35,int i36,int i37,int i38,int i39,int i40,
                int i41,int i42,int i43,int i44,int i45,int i46,int i47,int i48,int i49,int i50,
                int i51,int i52,int i53,int i54,int i55,int i56,int i57,int i58,int i59,int i60,
                int i61,int i62,int i63,int i64,int i65,int i66,int i67,int i68,int i69,int i70,
                int i71,int i72,int i73,int i74,int i75,int i76,int i77,int i78,int i79,int i80,
                int i81,int i82,int i83,int i84,int i85,int i86,int i87,int i88,int i89,int i90,
                int i91,int i92,int i93,int i94,int i95,int i96,int i97,int i98,int i99,int i100) {}
    record R254(int i1,int i2,int i3,int i4,int i5,int i6,int i7,int i8,int i9,int i10,
                int i11,int i12,int i13,int i14,int i15,int i16,int i17,int i18,int i19,int i20,
                int i21,int i22,int i23,int i24,int i25,int i26,int i27,int i28,int i29,int i30,
                int i31,int i32,int i33,int i34,int i35,int i36,int i37,int i38,int i39,int i40,
                int i41,int i42,int i43,int i44,int i45,int i46,int i47,int i48,int i49,int i50,
                int i51,int i52,int i53,int i54,int i55,int i56,int i57,int i58,int i59,int i60,
                int i61,int i62,int i63,int i64,int i65,int i66,int i67,int i68,int i69,int i70,
                int i71,int i72,int i73,int i74,int i75,int i76,int i77,int i78,int i79,int i80,
                int i81,int i82,int i83,int i84,int i85,int i86,int i87,int i88,int i89,int i90,
                int i91,int i92,int i93,int i94,int i95,int i96,int i97,int i98,int i99,int i100,
                int i101,int i102,int i103,int i104,int i105,int i106,int i107,int i108,int i109,int i110,
                int i111,int i112,int i113,int i114,int i115,int i116,int i117,int i118,int i119,int i120,
                int i121,int i122,int i123,int i124,int i125,int i126,int i127,int i128,int i129,int i130,
                int i131,int i132,int i133,int i134,int i135,int i136,int i137,int i138,int i139,int i140,
                int i141,int i142,int i143,int i144,int i145,int i146,int i147,int i148,int i149,int i150,
                int i151,int i152,int i153,int i154,int i155,int i156,int i157,int i158,int i159,int i160,
                int i161,int i162,int i163,int i164,int i165,int i166,int i167,int i168,int i169,int i170,
                int i171,int i172,int i173,int i174,int i175,int i176,int i177,int i178,int i179,int i180,
                int i181,int i182,int i183,int i184,int i185,int i186,int i187,int i188,int i189,int i190,
                int i191,int i192,int i193,int i194,int i195,int i196,int i197,int i198,int i199, int i200,
                int i201,int i202,int i203,int i204,int i205,int i206,int i207,int i208,int i209,int i210,
                int i211,int i212,int i213,int i214,int i215,int i216,int i217,int i218,int i219,int i220,
                int i221,int i222,int i223,int i224,int i225,int i226,int i227,int i228,int i229,int i230,
                int i231,int i232,int i233,int i234,int i235,int i236,int i237,int i238,int i239,int i240,
                int i241,int i242,int i243,int i244,int i245,int i246,int i247,int i248,int i249,int i250,
                int i251,int i252,int i253,int i254) {}

    R0 r0;
    R1 r1;
    R10 r10;
    R100 r100;
    R254 r254;

    @Setup
    public void prepare() {
        r0 = new R0();
        r1 = new R1(1);
        r10 = new R10(1,2,3,4,5,6,7,8,9,10);
        r100 = new R100(1,2,3,4,5,6,7,8,9,10,
                        11,12,13,14,15,16,17,18,19,20,
                        21,22,23,24,25,26,27,28,29,30,
                        31,32,33,34,35,36,37,38,39,40,
                        41,42,43,44,45,46,47,48,49,50,
                        51,52,53,54,55,56,57,58,59,60,
                        61,62,63,64,65,66,67,68,69,70,
                        71,72,73,74,75,76,77,78,79,80,
                        81,82,83,84,85,86,87,88,89,90,
                        91,92,93,94,95,96,97,98,99,100);
        r254 = new R254(1,2,3,4,5,6,7,8,9,10,
                        11,12,13,14,15,16,17,18,19,20,
                        21,22,23,24,25,26,27,28,29,30,
                        31,32,33,34,35,36,37,38,39,40,
                        41,42,43,44,45,46,47,48,49,50,
                        51,52,53,54,55,56,57,58,59,60,
                        61,62,63,64,65,66,67,68,69,70,
                        71,72,73,74,75,76,77,78,79,80,
                        81,82,83,84,85,86,87,88,89,90,
                        91,92,93,94,95,96,97,98,99,100,
                        101,102,103,104,105,106,107,108,109,110,
                        111,112,113,114,115,116,117,118,119,120,
                        121,122,123,124,125,126,127,128,129,130,
                        131,132,133,134,135,136,137,138,139,140,
                        141,142,143,144,145,146,147,148,149,150,
                        151,152,153,154,155,156,157,158,159,160,
                        161,162,163,164,165,166,167,168,169,170,
                        171,172,173,174,175,176,177,178,179,180,
                        181,182,183,184,185,186,187,188,189,190,
                        191,192,193,194,195,196,197,198,199, 200,
                        201,202,203,204,205,206,207,208,209,210,
                        211,212,213,214,215,216,217,218,219,220,
                        221,222,223,224,225,226,227,228,229,230,
                        231,232,233,234,235,236,237,238,239,240,
                        241,242,243,244,245,246,247,248,249,250,
                        251,252,253,254);
    }

    @Benchmark
    public String toString0() {
        return r0.toString();
    }

    @Benchmark
    public String toString1() {
        return r1.toString();
    }

    @Benchmark
    public String toString10() {
        return r10.toString();
    }

    @Benchmark
    public String toString100() {
        return r100.toString();
    }

    @Benchmark
    public String toString254() {
        return r254.toString();
    }
}

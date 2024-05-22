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
package org.openjdk.bench.vm.lang;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 5)
public class SecondarySupersLookupTake2 {
    interface J  {}
    interface I01 {}
    interface I02 extends I01 {}
    interface I03 extends I02 {}
    interface I04 extends I03 {}
    interface I05 extends I04 {}
    interface I06 extends I05 {}
    interface I07 extends I06 {}
    interface I08 extends I07 {}
    interface I09 extends I08 {}
    interface I10 extends I09 {}
    interface I11 extends I10 {}
    interface I12 extends I11 {}
    interface I13 extends I12 {}
    interface I14 extends I13 {}
    interface I15 extends I14 {}
    interface I16 extends I15 {}
    interface I17 extends I16 {}
    interface I18 extends I17 {}
    interface I19 extends I18 {}
    interface I20 extends I19 {}
    interface I21 extends I20 {}
    interface I22 extends I21 {}
    interface I23 extends I22 {}
    interface I24 extends I23 {}
    interface I25 extends I24 {}
    interface I26 extends I25 {}
    interface I27 extends I26 {}
    interface I28 extends I27 {}
    interface I29 extends I28 {}
    interface I30 extends I29 {}
    interface I31 extends I30 {}
    interface I32 extends I31 {}
    interface I33 extends I32 {}
    interface I34 extends I33 {}
    interface I35 extends I34 {}
    interface I36 extends I35 {}
    interface I37 extends I36 {}
    interface I38 extends I37 {}
    interface I39 extends I38 {}
    interface I40 extends I39 {}
    interface I41 extends I40 {}
    interface I42 extends I41 {}
    interface I43 extends I42 {}
    interface I44 extends I43 {}
    interface I45 extends I44 {}
    interface I46 extends I45 {}
    interface I47 extends I46 {}
    interface I48 extends I47 {}
    interface I49 extends I48 {}
    interface I50 extends I49 {}
    interface I51 extends I50 {}
    interface I52 extends I51 {}
    interface I53 extends I52 {}
    interface I54 extends I53 {}
    interface I55 extends I54 {}
    interface I56 extends I55 {}
    interface I57 extends I56 {}
    interface I58 extends I57 {}
    interface I59 extends I58 {}
    interface I60 extends I59 {}
    interface I61 extends I60 {}
    interface I62 extends I61 {}
    interface I63 extends I62 {}
    interface I64 extends I63 {}

    final Object obj00 = new Object();
    final Object obj01 = new I01() {};
    final Object obj02 = new I02() {};
    final Object obj03 = new I03() {};
    final Object obj04 = new I04() {};
    final Object obj05 = new I05() {};
    final Object obj06 = new I06() {};
    final Object obj07 = new I07() {};
    final Object obj08 = new I08() {};
    final Object obj09 = new I09() {};
    final Object obj10 = new I10() {};
    final Object obj16 = new I16() {};
    final Object obj20 = new I20() {};
    final Object obj30 = new I30() {};
    final Object obj32 = new I32() {};
    final Object obj40 = new I40() {};
    final Object obj50 = new I50() {};
    final Object obj55 = new I55() {};
    final Object obj56 = new I56() {};
    final Object obj57 = new I57() {};
    final Object obj58 = new I58() {};
    final Object obj59 = new I59() {};
    final Object obj60 = new I60() {};
    final Object obj61 = new I61() {};
    final Object obj62 = new I62() {};
    final Object obj63 = new I63() {};
    final Object obj64 = new I64() {};

    static Class<?> getSuper(int idx) {
        int i = Math.abs(idx) % 10;
        switch (i) {
            case 0: return I01.class;
            case 1: return I02.class;
            case 2: return I03.class;
            case 3: return I04.class;
            case 4: return I05.class;
            case 5: return I06.class;
            case 6: return I07.class;
            case 7: return I08.class;
            case 8: return I09.class;
            case 9: return I10.class;
        }
        throw new InternalError("" + i);
    }

    @Setup
    public void warmup() {
        for (int i = 0; i < 20_000; i++) {
            Class<?> s = getSuper(i);
            test(s.isInstance(obj01), s, s.isInstance(obj01));
            test(s.isInstance(obj02), s, s.isInstance(obj02));
            test(s.isInstance(obj03), s, s.isInstance(obj03));
            test(s.isInstance(obj04), s, s.isInstance(obj04));
            test(s.isInstance(obj05), s, s.isInstance(obj05));
            test(s.isInstance(obj06), s, s.isInstance(obj06));
            test(s.isInstance(obj07), s, s.isInstance(obj07));
            test(s.isInstance(obj08), s, s.isInstance(obj08));
            test(s.isInstance(obj09), s, s.isInstance(obj09));
        }
    }

    private static void test(boolean isInstance, Class<?> cls, boolean expected) {
        if (isInstance != expected) {
            throw new InternalError(cls + " " + cls + " " + expected);
        }
    }
    @Benchmark
    public void testPositive01() {
        test(obj01 instanceof I01, I01.class, true);
    }
    @Benchmark public void testPositive02() {
        test(obj02 instanceof I02, I02.class, true);
    }
    @Benchmark public void testPositive03() {
        test(obj03 instanceof I03, I03.class, true);
    }
    @Benchmark public void testPositive04() {
        test(obj04 instanceof I04, I04.class, true);
    }
    @Benchmark public void testPositive05() {
        test(obj05 instanceof I05, I05.class, true);
    }
    @Benchmark public void testPositive06() {
        test(obj06 instanceof I06, I06.class, true);
    }
    @Benchmark public void testPositive07() {
        test(obj07 instanceof I07, I07.class, true);
    }
    @Benchmark public void testPositive08() {
        test(obj08 instanceof I08, I08.class, true);
    }
    @Benchmark public void testPositive09() {
        test(obj09 instanceof I09, I09.class, true);
    }
    @Benchmark public void testPositive10() {
        test(obj10 instanceof I10, I10.class, true);
    }
    @Benchmark public void testPositive16() {
        test(obj16 instanceof I16, I16.class, true);
    }
    @Benchmark public void testPositive20() {
        test(obj20 instanceof I20, I20.class, true);
    }
    @Benchmark public void testPositive30() {
        test(obj30 instanceof I30, I30.class, true);
    }
    @Benchmark public void testPositive32() {
        test(obj32 instanceof I32, I32.class, true);
    }
    @Benchmark public void testPositive40() {
        test(obj40 instanceof I40, I40.class, true);
    }
    @Benchmark public void testPositive50() {
        test(obj50 instanceof I50, I50.class, true);
    }
    @Benchmark public void testPositive60() {
        test(obj60 instanceof I60, I60.class, true);
    }
    @Benchmark public void testPositive63() {
        test(obj63 instanceof I63, I63.class, true);
    }
    @Benchmark public void testPositive64() {
        test(obj64 instanceof I64, I64.class, true);
    }

    @Benchmark public void testNegative00() {
        test(obj00 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative01() {
        test(obj01 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative02() {
        test(obj02 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative03() {
        test(obj03 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative04() {
        test(obj04 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative05() {
        test(obj05 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative06() {
        test(obj06 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative07() {
        test(obj07 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative08() {
        test(obj08 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative09() {
        test(obj09 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative10() {
        test(obj10 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative16() {
        test(obj16 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative20() {
        test(obj20 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative30() {
        test(obj30 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative32() {
        test(obj32 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative40() {
        test(obj40 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative50() {
        test(obj50 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative55() {
        test(obj55 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative56() {
        test(obj56 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative57() {
        test(obj57 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative58() {
        test(obj58 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative59() {
        test(obj59 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative60() {
        test(obj60 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative61() {
        test(obj61 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative62() {
        test(obj62 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative63() {
        test(obj63 instanceof J, J.class, false);
    }
    @Benchmark public void testNegative64() {
        test(obj64 instanceof J, J.class, false);
    }
}

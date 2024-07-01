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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/* @test
 * @bug 8335060
 * @summary unit tests of TypeConvertingMethodAdapter
 * @run junit TypeConvertingTest
 */
public class TypeConvertingTest {

    static void smallBooleanC(boolean b) {
        assertTrue(b);
    }

    static void bigBooleanC(Boolean b) {
        assertTrue(b);
    }

    static void smallByteC(byte b) {
        assertEquals(1, b);
    }

    static void bigByteC(Byte b) {
        assertEquals((byte)1, b);
    }

    static void smallShortC(short s) {
        assertEquals(1, s);
    }

    static void bigShortC(Short s) {
        assertEquals((short)1, s);
    }

    static void smallCharC(char c) {
        assertEquals(1, c);
    }

    static void bigCharC(Character c) {
        assertEquals((char)1, c);
    }

    static void smallIntC(int i) {
        assertEquals(1, i);
    }

    static void bigIntC(Integer i) {
        assertEquals(1, i);
    }

    static void smallLongC(long l) {
        assertEquals(1, l);
    }

    static void bigLongC(Long l) {
        assertEquals(1, l);
    }

    static void smallFloatC(float f) {
        assertEquals(1.0f, f);
    }

    static void bigFloatC(Float f) {
        assertEquals(1.0f, f);
    }

    static void smallDoubleC(double d) {
        assertEquals(1.0, d);
    }

    static void bigDoubleC(Double d) {
        assertEquals(1.0, d);
    }

    static void numberC(Number n) {
        assertEquals(1, n.intValue());
    }


    static boolean smallBooleanS() {return true;}

    static Boolean bigBooleanS() {return true;}

    static byte smallByteS() {return 1;}

    static Byte bigByteS() {return 1;}

    static short smallShortS() {return 1;}

    static Short bigShortS() {return 1;}

    static char smallCharS() {return 1;}

    static Character bigCharS() {return 1;}

    static int smallIntS() {return 1;}

    static Integer bigIntS() {return 1;}

    static long smallLongS() {return 1;}

    static Long bigLongS() {return 1l;}

    static float smallFloatS() {return 1;}

    static Float bigFloatS() {return 1f;}

    static double smallDoubleS() {return 1;}

    static Double bigDoubleS() {return 1d;}

    static Number numberS() {return 1;}


    interface GenericC<T> {
        void m(T t);
    }

    interface SmallBooleanC {
        void m(boolean b);
    }

    interface BigBooleanC {
        void m(Boolean b);
    }

    interface SmallByteC {
        void m(byte b);
    }

    interface BigByteC {
        void m(Byte b);
    }

    interface SmallShortC {
        void m(short s);
    }

    interface BigShortC {
        void m(Short s);
    }

    interface SmallCharC {
        void m(char c);
    }

    interface BigCharC {
        void m(Character c);
    }

    interface SmallIntC {
        void m(int i);
    }

    interface BigIntC {
        void m(Integer i);
    }

    interface SmallLongC {
        void m(long l);
    }

    interface BigLongC {
        void m(Long l);
    }

    interface SmallFloatC {
        void m(float f);
    }

    interface BigFloatC {
        void m(Float f);
    }

    interface SmallDoubleC {
        void m(double d);
    }

    interface BigDoubleC {
        void m(Double d);
    }

    interface BigNumberC {
        void m(Number n);
    }


    interface GenericS<T> {
        T m();
    }

    interface SmallBooleanS {
        boolean m();
    }

    interface BigBooleanS {
        Boolean m();
    }

    interface SmallByteS {
        byte m();
    }

    interface BigByteS {
        Byte m();
    }

    interface SmallShortS {
        short m();
    }

    interface BigShortS {
        Short m();
    }

    interface SmallCharS {
        char m();
    }

    interface BigCharS {
        Character m();
    }

    interface SmallIntS {
        int m();
    }

    interface BigIntS {
        Integer m();
    }

    interface SmallLongS {
        long m();
    }

    interface BigLongS {
        Long m();
    }

    interface SmallFloatS {
        float m();
    }

    interface BigFloatS {
        Float m();
    }

    interface SmallDoubleS {
        double m();
    }

    interface BigDoubleS {
        Double m();
    }

    interface BigNumberS {
        Number m();
    }


    static void testGenericBoolean(GenericC<Boolean> t) {
        t.m(true);
    }

    static void testGenericByte(GenericC<Byte> t) {
        t.m((byte)1);
    }

    static void testGenericShort(GenericC<Short> t) {
        t.m((short)1);
    }

    static void testGenericChar(GenericC<Character> t) {
        t.m((char)1);
    }

    static void testGenericInt(GenericC<Integer> t) {
        t.m(1);
    }

    static void testGenericLong(GenericC<Long> t) {
        t.m(1l);
    }

    static void testGenericFloat(GenericC<Float> t) {
        t.m(1.0f);
    }

    static void testGenericDouble(GenericC<Double> t) {
        t.m(1.0d);
    }

    static void testGenericNumber(GenericC<Number> t) {
        t.m(1);
    }

    static void testSmallBoolean(SmallBooleanC t) {
        t.m(true);
    }

    static void testSmallByte(SmallByteC t) {
        t.m((byte)1);
    }

    static void testSmallShort(SmallShortC t) {
        t.m((short)1);
    }

    static void testSmallChar(SmallCharC t) {
        t.m((char)1);
    }

    static void testSmallInt(SmallIntC t) {
        t.m(1);
    }

    static void testSmallLong(SmallLongC t) {
        t.m(1l);
    }

    static void testSmallFloat(SmallFloatC t) {
        t.m(1.0f);
    }

    static void testSmallDouble(SmallDoubleC t) {
        t.m(1.0d);
    }

    static void testBigBoolean(BigBooleanC t) {
        t.m(true);
    }

    static void testBigByte(BigByteC t) {
        t.m((byte)1);
    }

    static void testBigShort(BigShortC t) {
        t.m((short)1);
    }

    static void testBigChar(BigCharC t) {
        t.m((char)1);
    }

    static void testBigInt(BigIntC t) {
        t.m(1);
    }

    static void testBigLong(BigLongC t) {
        t.m(1l);
    }

    static void testBigFloat(BigFloatC t) {
        t.m(1.0f);
    }

    static void testBigDouble(BigDoubleC t) {
        t.m(1.0d);
    }

    static void testBigNumber(BigNumberC t) {
        t.m(1);
    }


    static void testGenericBoolean(GenericS<Boolean> t) {
        assertEquals(true, t.m());
    }

    static void testGenericByte(GenericS<Byte> t) {
        assertEquals((byte)1, t.m());
    }

    static void testGenericShort(GenericS<Short> t) {
        assertEquals((short)1, t.m());
    }

    static void testGenericChar(GenericS<Character> t) {
        assertEquals((char)1, t.m());
    }

    static void testGenericInt(GenericS<Integer> t) {
        assertEquals(1, t.m());
    }

    static void testGenericLong(GenericS<Long> t) {
        assertEquals(1, t.m());
    }

    static void testGenericFloat(GenericS<Float> t) {
        assertEquals(1.0f, t.m());
    }

    static void testGenericDouble(GenericS<Double> t) {
        assertEquals(1.0d, t.m());
    }

    static void testGenericNumber(GenericS<Number> t) {
        assertEquals(1, t.m().intValue());
    }

    static void testSmallBoolean(SmallBooleanS t) {
        assertEquals(true, t.m());
    }

    static void testSmallByte(SmallByteS t) {
        assertEquals(1, t.m());
    }

    static void testSmallShort(SmallShortS t) {
        assertEquals(1, t.m());
    }

    static void testSmallChar(SmallCharS t) {
        assertEquals(1, t.m());
    }

    static void testSmallInt(SmallIntS t) {
        assertEquals(1, t.m());
    }

    static void testSmallLong(SmallLongS t) {
        assertEquals(1, t.m());
    }

    static void testSmallFloat(SmallFloatS t) {
        assertEquals(1.0f, t.m());
    }

    static void testSmallDouble(SmallDoubleS t) {
        assertEquals(1.0d, t.m());
    }

    static void testBigBoolean(BigBooleanS t) {
        assertEquals(true, t.m());
    }

    static void testBigByte(BigByteS t) {
        assertEquals((byte)1, t.m());
    }

    static void testBigShort(BigShortS t) {
        assertEquals((short)1, t.m());
    }

    static void testBigChar(BigCharS t) {
        assertEquals((char)1, t.m());
    }

    static void testBigInt(BigIntS t) {
        assertEquals(1, t.m());
    }

    static void testBigLong(BigLongS t) {
        assertEquals(1, t.m());
    }

    static void testBigFloat(BigFloatS t) {
        assertEquals(1.0f, t.m());
    }

    static void testBigDouble(BigDoubleS t) {
        assertEquals(1.0f, t.m());
    }

    static void testBigNumber(BigNumberS t) {
        assertEquals(1, t.m().intValue());
    }


    @Test
    void testGenericBoolean() {
        testGenericBoolean(TypeConvertingTest::smallBooleanC);
        testGenericBoolean(TypeConvertingTest::bigBooleanC);

        testGenericBoolean(TypeConvertingTest::smallBooleanS);
        testGenericBoolean(TypeConvertingTest::bigBooleanS);
    }

    @Test
    void testGenericByte() {
        testGenericByte(TypeConvertingTest::smallByteC);
        testGenericByte(TypeConvertingTest::bigByteC);
        testGenericByte(TypeConvertingTest::smallShortC);
        testGenericByte(TypeConvertingTest::smallIntC);
        testGenericByte(TypeConvertingTest::smallLongC);
        testGenericByte(TypeConvertingTest::smallFloatC);
        testGenericByte(TypeConvertingTest::smallDoubleC);
        testGenericByte(TypeConvertingTest::numberC);

        testGenericByte(TypeConvertingTest::smallByteS);
        testGenericByte(TypeConvertingTest::bigByteS);
    }

    @Test
    void testGenericShort() {
        testGenericShort(TypeConvertingTest::smallShortC);
        testGenericShort(TypeConvertingTest::bigShortC);
        testGenericShort(TypeConvertingTest::smallIntC);
        testGenericShort(TypeConvertingTest::smallLongC);
        testGenericShort(TypeConvertingTest::smallFloatC);
        testGenericShort(TypeConvertingTest::smallDoubleC);
        testGenericShort(TypeConvertingTest::numberC);

        testGenericShort(TypeConvertingTest::smallShortS);
        testGenericShort(TypeConvertingTest::bigShortS);
    }

    @Test
    void testGenericChar() {
        testGenericChar(TypeConvertingTest::smallCharC);
        testGenericChar(TypeConvertingTest::bigCharC);
        testGenericChar(TypeConvertingTest::smallIntC);
        testGenericChar(TypeConvertingTest::smallLongC);
        testGenericChar(TypeConvertingTest::smallFloatC);
        testGenericChar(TypeConvertingTest::smallDoubleC);

        testGenericChar(TypeConvertingTest::smallCharS);
        testGenericChar(TypeConvertingTest::bigCharS);
    }

    @Test
    void testGenericInt() {
        testGenericInt(TypeConvertingTest::smallIntC);
        testGenericInt(TypeConvertingTest::bigIntC);
        testGenericInt(TypeConvertingTest::smallLongC);
        testGenericInt(TypeConvertingTest::smallFloatC);
        testGenericInt(TypeConvertingTest::smallDoubleC);
        testGenericInt(TypeConvertingTest::numberC);

        testGenericInt(TypeConvertingTest::smallIntS);
        testGenericInt(TypeConvertingTest::bigIntS);
    }

    @Test
    void testGenericLong() {
        testGenericLong(TypeConvertingTest::smallLongC);
        testGenericLong(TypeConvertingTest::bigLongC);
        testGenericLong(TypeConvertingTest::smallFloatC);
        testGenericLong(TypeConvertingTest::smallDoubleC);
        testGenericLong(TypeConvertingTest::numberC);

        testGenericLong(TypeConvertingTest::smallLongS);
        testGenericLong(TypeConvertingTest::bigLongS);
    }

    @Test
    void testGenericFloat() {
        testGenericFloat(TypeConvertingTest::smallFloatC);
        testGenericFloat(TypeConvertingTest::bigFloatC);
        testGenericFloat(TypeConvertingTest::smallDoubleC);
        testGenericFloat(TypeConvertingTest::numberC);

        testGenericFloat(TypeConvertingTest::smallFloatS);
        testGenericFloat(TypeConvertingTest::bigFloatS);
    }

    @Test
    void testGenericDouble() {
        testGenericDouble(TypeConvertingTest::smallDoubleC);
        testGenericDouble(TypeConvertingTest::bigDoubleC);
        testGenericDouble(TypeConvertingTest::numberC);

        testGenericDouble(TypeConvertingTest::smallDoubleS);
        testGenericDouble(TypeConvertingTest::bigDoubleS);
    }

    @Test
    void testGenericNumber() {
        testGenericNumber(TypeConvertingTest::numberC);

        testGenericNumber(TypeConvertingTest::numberS);
    }

    @Test
    void testSmallBoolean() {
        testSmallBoolean(TypeConvertingTest::smallBooleanC);
        testSmallBoolean(TypeConvertingTest::bigBooleanC);

        testSmallBoolean(TypeConvertingTest::smallBooleanS);
        testSmallBoolean(TypeConvertingTest::bigBooleanS);
    }

    @Test
    void testSmallByte() {
        testSmallByte(TypeConvertingTest::smallByteC);
        testSmallByte(TypeConvertingTest::bigByteC);
        testSmallByte(TypeConvertingTest::smallShortC);
        testSmallByte(TypeConvertingTest::smallIntC);
        testSmallByte(TypeConvertingTest::smallLongC);
        testSmallByte(TypeConvertingTest::smallFloatC);
        testSmallByte(TypeConvertingTest::smallDoubleC);
        testSmallByte(TypeConvertingTest::numberC);

        testSmallByte(TypeConvertingTest::smallByteS);
        testSmallByte(TypeConvertingTest::bigByteS);
    }

    @Test
    void testSmallShort() {
        testSmallShort(TypeConvertingTest::smallShortC);
        testSmallShort(TypeConvertingTest::bigShortC);
        testSmallShort(TypeConvertingTest::smallIntC);
        testSmallShort(TypeConvertingTest::smallLongC);
        testSmallShort(TypeConvertingTest::smallFloatC);
        testSmallShort(TypeConvertingTest::smallDoubleC);
        testSmallShort(TypeConvertingTest::numberC);

        testSmallShort(TypeConvertingTest::smallShortS);
        testSmallShort(TypeConvertingTest::bigShortS);
    }

    @Test
    void testSmallChar() {
        testSmallChar(TypeConvertingTest::smallCharC);
        testSmallChar(TypeConvertingTest::bigCharC);
        testSmallChar(TypeConvertingTest::smallIntC);
        testSmallChar(TypeConvertingTest::smallLongC);
        testSmallChar(TypeConvertingTest::smallFloatC);
        testSmallChar(TypeConvertingTest::smallDoubleC);

        testSmallChar(TypeConvertingTest::smallCharS);
        testSmallChar(TypeConvertingTest::bigCharS);
    }

    @Test
    void testSmallInt() {
        testSmallInt(TypeConvertingTest::smallIntC);
        testSmallInt(TypeConvertingTest::bigIntC);
        testSmallInt(TypeConvertingTest::smallLongC);
        testSmallInt(TypeConvertingTest::smallFloatC);
        testSmallInt(TypeConvertingTest::smallDoubleC);
        testSmallInt(TypeConvertingTest::numberC);

        testSmallInt(TypeConvertingTest::smallIntS);
        testSmallInt(TypeConvertingTest::bigIntS);
    }

    @Test
    void testSmallLong() {
        testSmallLong(TypeConvertingTest::smallLongC);
        testSmallLong(TypeConvertingTest::bigLongC);
        testSmallLong(TypeConvertingTest::smallFloatC);
        testSmallLong(TypeConvertingTest::smallDoubleC);
        testSmallLong(TypeConvertingTest::numberC);

        testSmallLong(TypeConvertingTest::smallLongS);
        testSmallLong(TypeConvertingTest::bigLongS);
    }

    @Test
    void testSmallFloat() {
        testSmallFloat(TypeConvertingTest::smallFloatC);
        testSmallFloat(TypeConvertingTest::bigFloatC);
        testSmallFloat(TypeConvertingTest::smallDoubleC);
        testSmallFloat(TypeConvertingTest::numberC);

        testSmallFloat(TypeConvertingTest::smallFloatS);
        testSmallFloat(TypeConvertingTest::bigFloatS);
    }

    @Test
    void testSmallDouble() {
        testSmallDouble(TypeConvertingTest::smallDoubleC);
        testSmallDouble(TypeConvertingTest::bigDoubleC);
        testSmallDouble(TypeConvertingTest::numberC);

        testSmallDouble(TypeConvertingTest::smallDoubleS);
        testSmallDouble(TypeConvertingTest::bigDoubleS);
    }

    @Test
    void testBigBoolean() {
        testBigBoolean(TypeConvertingTest::smallBooleanC);
        testBigBoolean(TypeConvertingTest::bigBooleanC);

        testBigBoolean(TypeConvertingTest::smallBooleanS);
        testBigBoolean(TypeConvertingTest::bigBooleanS);
    }

    @Test
    void testBigByte() {
        testBigByte(TypeConvertingTest::smallByteC);
        testBigByte(TypeConvertingTest::bigByteC);
        testBigByte(TypeConvertingTest::smallShortC);
        testBigByte(TypeConvertingTest::smallIntC);
        testBigByte(TypeConvertingTest::smallLongC);
        testBigByte(TypeConvertingTest::smallFloatC);
        testBigByte(TypeConvertingTest::smallDoubleC);
        testBigByte(TypeConvertingTest::numberC);

        testBigByte(TypeConvertingTest::smallByteS);
        testBigByte(TypeConvertingTest::bigByteS);
    }

    @Test
    void testBigShort() {
        testBigShort(TypeConvertingTest::smallShortC);
        testBigShort(TypeConvertingTest::bigShortC);
        testBigShort(TypeConvertingTest::smallIntC);
        testBigShort(TypeConvertingTest::smallLongC);
        testBigShort(TypeConvertingTest::smallFloatC);
        testBigShort(TypeConvertingTest::smallDoubleC);
        testBigShort(TypeConvertingTest::numberC);

        testBigShort(TypeConvertingTest::smallShortS);
        testBigShort(TypeConvertingTest::bigShortS);
    }

    @Test
    void testBigChar() {
        testBigChar(TypeConvertingTest::smallCharC);
        testBigChar(TypeConvertingTest::bigCharC);
        testBigChar(TypeConvertingTest::smallIntC);
        testBigChar(TypeConvertingTest::smallLongC);
        testBigChar(TypeConvertingTest::smallFloatC);
        testBigChar(TypeConvertingTest::smallDoubleC);

        testBigChar(TypeConvertingTest::smallCharS);
        testBigChar(TypeConvertingTest::bigCharS);
    }

    @Test
    void testBigInt() {
        testBigInt(TypeConvertingTest::smallIntC);
        testBigInt(TypeConvertingTest::bigIntC);
        testBigInt(TypeConvertingTest::smallLongC);
        testBigInt(TypeConvertingTest::smallFloatC);
        testBigInt(TypeConvertingTest::smallDoubleC);
        testBigInt(TypeConvertingTest::numberC);

        testBigInt(TypeConvertingTest::smallIntS);
        testBigInt(TypeConvertingTest::bigIntS);
    }

    @Test
    void testBigLong() {
        testBigLong(TypeConvertingTest::smallLongC);
        testBigLong(TypeConvertingTest::bigLongC);
        testBigLong(TypeConvertingTest::smallFloatC);
        testBigLong(TypeConvertingTest::smallDoubleC);
        testBigLong(TypeConvertingTest::numberC);

        testBigLong(TypeConvertingTest::smallLongS);
        testBigLong(TypeConvertingTest::bigLongS);
    }

    @Test
    void testBigFloat() {
        testBigFloat(TypeConvertingTest::smallFloatC);
        testBigFloat(TypeConvertingTest::bigFloatC);
        testBigFloat(TypeConvertingTest::smallDoubleC);
        testBigFloat(TypeConvertingTest::numberC);

        testBigFloat(TypeConvertingTest::smallFloatS);
        testBigFloat(TypeConvertingTest::bigFloatS);
    }

    @Test
    void testBigDouble() {
        testBigDouble(TypeConvertingTest::smallDoubleC);
        testBigDouble(TypeConvertingTest::bigDoubleC);
        testBigDouble(TypeConvertingTest::numberC);

        testBigDouble(TypeConvertingTest::smallDoubleS);
        testBigDouble(TypeConvertingTest::bigDoubleS);
    }

    @Test
    void testBigNumber() {
        testBigNumber(TypeConvertingTest::numberC);

        testBigNumber(TypeConvertingTest::numberS);
    }
}

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;

public class MemoryArithmeticTest extends GraalCompilerTest {

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        return super.getCode(method, graph, true, installAsDefault, options);
    }

    /**
     * Called before a test is executed.
     */
    @Override
    protected void before(ResolvedJavaMethod method) {
        // don't let any null exception tracking change the generated code.
        method.reprofile();
    }

    /**
     * A dummy field used by some tests to create side effects.
     */
    protected static int count;

    static class FieldObject {
        boolean booleanValue;
        byte byteValue;
        short shortValue;
        char charValue;
        int intValue;
        float floatValue;
        long longValue;
        double doubleValue;
        Object objectValue;
    }

    static FieldObject maxObject = new FieldObject();
    static FieldObject minObject;

    static final boolean booleanTestValue1 = false;
    static final byte byteTestValue1 = 0;
    static final short shortTestValue1 = 0;
    static final char charTestValue1 = 0;
    static final int intTestValue1 = 0;
    static final float floatTestValue1 = 0;
    static final long longTestValue1 = 0;
    static final double doubleTestValue1 = 0;
    static final Object objectTestValue1 = null;

    static final boolean booleanTestValue2 = true;
    static final byte byteTestValue2 = Byte.MAX_VALUE;
    static final short shortTestValue2 = Short.MAX_VALUE;
    static final char charTestValue2 = Character.MAX_VALUE;
    static final int intTestValue2 = Integer.MAX_VALUE;
    static final float floatTestValue2 = Float.MAX_VALUE;
    static final long longTestValue2 = Long.MAX_VALUE;
    static final double doubleTestValue2 = Double.MAX_VALUE;
    static final Object objectTestValue2 = "String";

    static {
        maxObject.booleanValue = true;
        maxObject.byteValue = Byte.MAX_VALUE;
        maxObject.shortValue = Short.MAX_VALUE;
        maxObject.charValue = Character.MAX_VALUE;
        maxObject.intValue = Integer.MAX_VALUE;
        maxObject.floatValue = Float.MAX_VALUE;
        maxObject.longValue = Long.MAX_VALUE;
        maxObject.doubleValue = Double.MAX_VALUE;
        maxObject.objectValue = "String";
    }

    public static Object testBooleanCompare(FieldObject f, boolean booleanValue) {
        if (f.booleanValue == booleanValue) {
            return f;
        }
        return null;
    }

    public static Object testBooleanCompareConstant1(FieldObject f) {
        if (f.booleanValue == booleanTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testBooleanCompareConstant2(FieldObject f) {
        if (f.booleanValue == booleanTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testBooleanCompares() {
        FieldObject f = new FieldObject();
        test("testBooleanCompare", f, booleanTestValue1);
        test("testBooleanCompareConstant1", f);
        test("testBooleanCompareConstant2", f);
    }

    @Test
    public void testBooleanNullCompares() {
        test("testBooleanCompare", null, booleanTestValue1);
    }

    @Test
    public void testBooleanNullCompares1() {
        test("testBooleanCompareConstant1", (Object) null);
    }

    @Test
    public void testBooleanNullCompares2() {
        test("testBooleanCompareConstant2", (Object) null);
    }

    public static Object testByteCompare(FieldObject f, byte byteValue) {
        if (f.byteValue == byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareConstant1(FieldObject f) {
        if (f.byteValue == byteTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareConstant2(FieldObject f) {
        if (f.byteValue == byteTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteCompares() {
        FieldObject f = new FieldObject();
        test("testByteCompare", f, byteTestValue1);
        test("testByteCompareConstant1", f);
        test("testByteCompareConstant2", f);
    }

    @Test
    public void testByteNullCompares() {
        test("testByteCompare", null, byteTestValue1);
    }

    @Test
    public void testByteNullCompares1() {
        test("testByteCompareConstant1", (Object) null);
    }

    @Test
    public void testByteNullCompares2() {
        test("testByteCompareConstant2", (Object) null);
    }

    public static Object testByteCompareLess(FieldObject f, byte byteValue) {
        if (f.byteValue < byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareLessConstant1(FieldObject f) {
        if (f.byteValue < byteTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareLessConstant2(FieldObject f) {
        if (f.byteValue < byteTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteComparesLess() {
        FieldObject f = new FieldObject();
        test("testByteCompareLess", f, byteTestValue1);
        test("testByteCompareLessConstant1", f);
        test("testByteCompareLessConstant2", f);
    }

    @Test
    public void testByteNullComparesLess() {
        test("testByteCompareLess", null, byteTestValue1);
    }

    @Test
    public void testByteNullComparesLess1() {
        test("testByteCompareLessConstant1", (Object) null);
    }

    @Test
    public void testByteNullComparesLess2() {
        test("testByteCompareLessConstant2", (Object) null);
    }

    public static Object testByteSwappedCompareLess(FieldObject f, byte byteValue) {
        if (byteValue < f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareLessConstant1(FieldObject f) {
        if (byteTestValue1 < f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareLessConstant2(FieldObject f) {
        if (byteTestValue2 < f.byteValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteSwappedComparesLess() {
        FieldObject f = new FieldObject();
        test("testByteSwappedCompareLess", f, byteTestValue1);
        test("testByteSwappedCompareLessConstant1", f);
        test("testByteSwappedCompareLessConstant2", f);
    }

    @Test
    public void testByteNullSwappedComparesLess() {
        test("testByteSwappedCompareLess", null, byteTestValue1);
    }

    @Test
    public void testByteNullSwappedComparesLess1() {
        test("testByteSwappedCompareLessConstant1", (Object) null);
    }

    @Test
    public void testByteNullSwappedComparesLess2() {
        test("testByteSwappedCompareLessConstant2", (Object) null);
    }

    public static Object testByteCompareLessEqual(FieldObject f, byte byteValue) {
        if (f.byteValue <= byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareLessEqualConstant1(FieldObject f) {
        if (f.byteValue <= byteTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareLessEqualConstant2(FieldObject f) {
        if (f.byteValue <= byteTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testByteCompareLessEqual", f, byteTestValue1);
        test("testByteCompareLessEqualConstant1", f);
        test("testByteCompareLessEqualConstant2", f);
    }

    @Test
    public void testByteNullComparesLessEqual() {
        test("testByteCompareLessEqual", null, byteTestValue1);
    }

    @Test
    public void testByteNullComparesLessEqual1() {
        test("testByteCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testByteNullComparesLessEqual2() {
        test("testByteCompareLessEqualConstant2", (Object) null);
    }

    public static Object testByteSwappedCompareLessEqual(FieldObject f, byte byteValue) {
        if (byteValue <= f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareLessEqualConstant1(FieldObject f) {
        if (byteTestValue1 <= f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareLessEqualConstant2(FieldObject f) {
        if (byteTestValue2 <= f.byteValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteSwappedComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testByteSwappedCompareLessEqual", f, byteTestValue1);
        test("testByteSwappedCompareLessEqualConstant1", f);
        test("testByteSwappedCompareLessEqualConstant2", f);
    }

    @Test
    public void testByteNullSwappedComparesLessEqual() {
        test("testByteSwappedCompareLessEqual", null, byteTestValue1);
    }

    @Test
    public void testByteNullSwappedComparesLessEqual1() {
        test("testByteSwappedCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testByteNullSwappedComparesLessEqual2() {
        test("testByteSwappedCompareLessEqualConstant2", (Object) null);
    }

    public static Object testByteCompareGreater(FieldObject f, byte byteValue) {
        if (f.byteValue > byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareGreaterConstant1(FieldObject f) {
        if (f.byteValue > byteTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareGreaterConstant2(FieldObject f) {
        if (f.byteValue > byteTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteComparesGreater() {
        FieldObject f = new FieldObject();
        test("testByteCompareGreater", f, byteTestValue1);
        test("testByteCompareGreaterConstant1", f);
        test("testByteCompareGreaterConstant2", f);
    }

    @Test
    public void testByteNullComparesGreater() {
        test("testByteCompareGreater", null, byteTestValue1);
    }

    @Test
    public void testByteNullComparesGreater1() {
        test("testByteCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testByteNullComparesGreater2() {
        test("testByteCompareGreaterConstant2", (Object) null);
    }

    public static Object testByteSwappedCompareGreater(FieldObject f, byte byteValue) {
        if (byteValue > f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareGreaterConstant1(FieldObject f) {
        if (byteTestValue1 > f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareGreaterConstant2(FieldObject f) {
        if (byteTestValue2 > f.byteValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteSwappedComparesGreater() {
        FieldObject f = new FieldObject();
        test("testByteSwappedCompareGreater", f, byteTestValue1);
        test("testByteSwappedCompareGreaterConstant1", f);
        test("testByteSwappedCompareGreaterConstant2", f);
    }

    @Test
    public void testByteNullSwappedComparesGreater() {
        test("testByteSwappedCompareGreater", null, byteTestValue1);
    }

    @Test
    public void testByteNullSwappedComparesGreater1() {
        test("testByteSwappedCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testByteNullSwappedComparesGreater2() {
        test("testByteSwappedCompareGreaterConstant2", (Object) null);
    }

    public static Object testByteCompareGreaterEqual(FieldObject f, byte byteValue) {
        if (f.byteValue >= byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareGreaterEqualConstant1(FieldObject f) {
        if (f.byteValue >= byteTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareGreaterEqualConstant2(FieldObject f) {
        if (f.byteValue >= byteTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testByteCompareGreaterEqual", f, byteTestValue1);
        test("testByteCompareGreaterEqualConstant1", f);
        test("testByteCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testByteNullComparesGreaterEqual() {
        test("testByteCompareGreaterEqual", null, byteTestValue1);
    }

    @Test
    public void testByteNullComparesGreaterEqual1() {
        test("testByteCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testByteNullComparesGreaterEqual2() {
        test("testByteCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testByteSwappedCompareGreaterEqual(FieldObject f, byte byteValue) {
        if (byteValue >= f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareGreaterEqualConstant1(FieldObject f) {
        if (byteTestValue1 >= f.byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteSwappedCompareGreaterEqualConstant2(FieldObject f) {
        if (byteTestValue2 >= f.byteValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteSwappedComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testByteSwappedCompareGreaterEqual", f, byteTestValue1);
        test("testByteSwappedCompareGreaterEqualConstant1", f);
        test("testByteSwappedCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testByteNullSwappedComparesGreaterEqual() {
        test("testByteSwappedCompareGreaterEqual", null, byteTestValue1);
    }

    @Test
    public void testByteNullSwappedComparesGreaterEqual1() {
        test("testByteSwappedCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testByteNullSwappedComparesGreaterEqual2() {
        test("testByteSwappedCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testShortCompare(FieldObject f, short shortValue) {
        if (f.shortValue == shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareConstant1(FieldObject f) {
        if (f.shortValue == shortTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareConstant2(FieldObject f) {
        if (f.shortValue == shortTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortCompares() {
        FieldObject f = new FieldObject();
        test("testShortCompare", f, shortTestValue1);
        test("testShortCompareConstant1", f);
        test("testShortCompareConstant2", f);
    }

    @Test
    public void testShortNullCompares() {
        test("testShortCompare", null, shortTestValue1);
    }

    @Test
    public void testShortNullCompares1() {
        test("testShortCompareConstant1", (Object) null);
    }

    @Test
    public void testShortNullCompares2() {
        test("testShortCompareConstant2", (Object) null);
    }

    public static Object testShortCompareLess(FieldObject f, short shortValue) {
        if (f.shortValue < shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareLessConstant1(FieldObject f) {
        if (f.shortValue < shortTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareLessConstant2(FieldObject f) {
        if (f.shortValue < shortTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortComparesLess() {
        FieldObject f = new FieldObject();
        test("testShortCompareLess", f, shortTestValue1);
        test("testShortCompareLessConstant1", f);
        test("testShortCompareLessConstant2", f);
    }

    @Test
    public void testShortNullComparesLess() {
        test("testShortCompareLess", null, shortTestValue1);
    }

    @Test
    public void testShortNullComparesLess1() {
        test("testShortCompareLessConstant1", (Object) null);
    }

    @Test
    public void testShortNullComparesLess2() {
        test("testShortCompareLessConstant2", (Object) null);
    }

    public static Object testShortSwappedCompareLess(FieldObject f, short shortValue) {
        if (shortValue < f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareLessConstant1(FieldObject f) {
        if (shortTestValue1 < f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareLessConstant2(FieldObject f) {
        if (shortTestValue2 < f.shortValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortSwappedComparesLess() {
        FieldObject f = new FieldObject();
        test("testShortSwappedCompareLess", f, shortTestValue1);
        test("testShortSwappedCompareLessConstant1", f);
        test("testShortSwappedCompareLessConstant2", f);
    }

    @Test
    public void testShortNullSwappedComparesLess() {
        test("testShortSwappedCompareLess", null, shortTestValue1);
    }

    @Test
    public void testShortNullSwappedComparesLess1() {
        test("testShortSwappedCompareLessConstant1", (Object) null);
    }

    @Test
    public void testShortNullSwappedComparesLess2() {
        test("testShortSwappedCompareLessConstant2", (Object) null);
    }

    public static Object testShortCompareLessEqual(FieldObject f, short shortValue) {
        if (f.shortValue <= shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareLessEqualConstant1(FieldObject f) {
        if (f.shortValue <= shortTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareLessEqualConstant2(FieldObject f) {
        if (f.shortValue <= shortTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testShortCompareLessEqual", f, shortTestValue1);
        test("testShortCompareLessEqualConstant1", f);
        test("testShortCompareLessEqualConstant2", f);
    }

    @Test
    public void testShortNullComparesLessEqual() {
        test("testShortCompareLessEqual", null, shortTestValue1);
    }

    @Test
    public void testShortNullComparesLessEqual1() {
        test("testShortCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testShortNullComparesLessEqual2() {
        test("testShortCompareLessEqualConstant2", (Object) null);
    }

    public static Object testShortSwappedCompareLessEqual(FieldObject f, short shortValue) {
        if (shortValue <= f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareLessEqualConstant1(FieldObject f) {
        if (shortTestValue1 <= f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareLessEqualConstant2(FieldObject f) {
        if (shortTestValue2 <= f.shortValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortSwappedComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testShortSwappedCompareLessEqual", f, shortTestValue1);
        test("testShortSwappedCompareLessEqualConstant1", f);
        test("testShortSwappedCompareLessEqualConstant2", f);
    }

    @Test
    public void testShortNullSwappedComparesLessEqual() {
        test("testShortSwappedCompareLessEqual", null, shortTestValue1);
    }

    @Test
    public void testShortNullSwappedComparesLessEqual1() {
        test("testShortSwappedCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testShortNullSwappedComparesLessEqual2() {
        test("testShortSwappedCompareLessEqualConstant2", (Object) null);
    }

    public static Object testShortCompareGreater(FieldObject f, short shortValue) {
        if (f.shortValue > shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareGreaterConstant1(FieldObject f) {
        if (f.shortValue > shortTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareGreaterConstant2(FieldObject f) {
        if (f.shortValue > shortTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortComparesGreater() {
        FieldObject f = new FieldObject();
        test("testShortCompareGreater", f, shortTestValue1);
        test("testShortCompareGreaterConstant1", f);
        test("testShortCompareGreaterConstant2", f);
    }

    @Test
    public void testShortNullComparesGreater() {
        test("testShortCompareGreater", null, shortTestValue1);
    }

    @Test
    public void testShortNullComparesGreater1() {
        test("testShortCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testShortNullComparesGreater2() {
        test("testShortCompareGreaterConstant2", (Object) null);
    }

    public static Object testShortSwappedCompareGreater(FieldObject f, short shortValue) {
        if (shortValue > f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareGreaterConstant1(FieldObject f) {
        if (shortTestValue1 > f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareGreaterConstant2(FieldObject f) {
        if (shortTestValue2 > f.shortValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortSwappedComparesGreater() {
        FieldObject f = new FieldObject();
        test("testShortSwappedCompareGreater", f, shortTestValue1);
        test("testShortSwappedCompareGreaterConstant1", f);
        test("testShortSwappedCompareGreaterConstant2", f);
    }

    @Test
    public void testShortNullSwappedComparesGreater() {
        test("testShortSwappedCompareGreater", null, shortTestValue1);
    }

    @Test
    public void testShortNullSwappedComparesGreater1() {
        test("testShortSwappedCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testShortNullSwappedComparesGreater2() {
        test("testShortSwappedCompareGreaterConstant2", (Object) null);
    }

    public static Object testShortCompareGreaterEqual(FieldObject f, short shortValue) {
        if (f.shortValue >= shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareGreaterEqualConstant1(FieldObject f) {
        if (f.shortValue >= shortTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareGreaterEqualConstant2(FieldObject f) {
        if (f.shortValue >= shortTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testShortCompareGreaterEqual", f, shortTestValue1);
        test("testShortCompareGreaterEqualConstant1", f);
        test("testShortCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testShortNullComparesGreaterEqual() {
        test("testShortCompareGreaterEqual", null, shortTestValue1);
    }

    @Test
    public void testShortNullComparesGreaterEqual1() {
        test("testShortCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testShortNullComparesGreaterEqual2() {
        test("testShortCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testShortSwappedCompareGreaterEqual(FieldObject f, short shortValue) {
        if (shortValue >= f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareGreaterEqualConstant1(FieldObject f) {
        if (shortTestValue1 >= f.shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortSwappedCompareGreaterEqualConstant2(FieldObject f) {
        if (shortTestValue2 >= f.shortValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortSwappedComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testShortSwappedCompareGreaterEqual", f, shortTestValue1);
        test("testShortSwappedCompareGreaterEqualConstant1", f);
        test("testShortSwappedCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testShortNullSwappedComparesGreaterEqual() {
        test("testShortSwappedCompareGreaterEqual", null, shortTestValue1);
    }

    @Test
    public void testShortNullSwappedComparesGreaterEqual1() {
        test("testShortSwappedCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testShortNullSwappedComparesGreaterEqual2() {
        test("testShortSwappedCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testCharCompare(FieldObject f, char charValue) {
        if (f.charValue == charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareConstant1(FieldObject f) {
        if (f.charValue == charTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareConstant2(FieldObject f) {
        if (f.charValue == charTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharCompares() {
        FieldObject f = new FieldObject();
        test("testCharCompare", f, charTestValue1);
        test("testCharCompareConstant1", f);
        test("testCharCompareConstant2", f);
    }

    @Test
    public void testCharNullCompares() {
        test("testCharCompare", null, charTestValue1);
    }

    @Test
    public void testCharNullCompares1() {
        test("testCharCompareConstant1", (Object) null);
    }

    @Test
    public void testCharNullCompares2() {
        test("testCharCompareConstant2", (Object) null);
    }

    public static Object testCharCompareLess(FieldObject f, char charValue) {
        if (f.charValue < charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareLessConstant1(FieldObject f) {
        if (f.charValue < charTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareLessConstant2(FieldObject f) {
        if (f.charValue < charTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharComparesLess() {
        FieldObject f = new FieldObject();
        test("testCharCompareLess", f, charTestValue1);
        test("testCharCompareLessConstant1", f);
        test("testCharCompareLessConstant2", f);
    }

    @Test
    public void testCharNullComparesLess() {
        test("testCharCompareLess", null, charTestValue1);
    }

    @Test
    public void testCharNullComparesLess1() {
        test("testCharCompareLessConstant1", (Object) null);
    }

    @Test
    public void testCharNullComparesLess2() {
        test("testCharCompareLessConstant2", (Object) null);
    }

    public static Object testCharSwappedCompareLess(FieldObject f, char charValue) {
        if (charValue < f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareLessConstant1(FieldObject f) {
        if (charTestValue1 < f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareLessConstant2(FieldObject f) {
        if (charTestValue2 < f.charValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharSwappedComparesLess() {
        FieldObject f = new FieldObject();
        test("testCharSwappedCompareLess", f, charTestValue1);
        test("testCharSwappedCompareLessConstant1", f);
        test("testCharSwappedCompareLessConstant2", f);
    }

    @Test
    public void testCharNullSwappedComparesLess() {
        test("testCharSwappedCompareLess", null, charTestValue1);
    }

    @Test
    public void testCharNullSwappedComparesLess1() {
        test("testCharSwappedCompareLessConstant1", (Object) null);
    }

    @Test
    public void testCharNullSwappedComparesLess2() {
        test("testCharSwappedCompareLessConstant2", (Object) null);
    }

    public static Object testCharCompareLessEqual(FieldObject f, char charValue) {
        if (f.charValue <= charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareLessEqualConstant1(FieldObject f) {
        if (f.charValue <= charTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareLessEqualConstant2(FieldObject f) {
        if (f.charValue <= charTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testCharCompareLessEqual", f, charTestValue1);
        test("testCharCompareLessEqualConstant1", f);
        test("testCharCompareLessEqualConstant2", f);
    }

    @Test
    public void testCharNullComparesLessEqual() {
        test("testCharCompareLessEqual", null, charTestValue1);
    }

    @Test
    public void testCharNullComparesLessEqual1() {
        test("testCharCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testCharNullComparesLessEqual2() {
        test("testCharCompareLessEqualConstant2", (Object) null);
    }

    public static Object testCharSwappedCompareLessEqual(FieldObject f, char charValue) {
        if (charValue <= f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareLessEqualConstant1(FieldObject f) {
        if (charTestValue1 <= f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareLessEqualConstant2(FieldObject f) {
        if (charTestValue2 <= f.charValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharSwappedComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testCharSwappedCompareLessEqual", f, charTestValue1);
        test("testCharSwappedCompareLessEqualConstant1", f);
        test("testCharSwappedCompareLessEqualConstant2", f);
    }

    @Test
    public void testCharNullSwappedComparesLessEqual() {
        test("testCharSwappedCompareLessEqual", null, charTestValue1);
    }

    @Test
    public void testCharNullSwappedComparesLessEqual1() {
        test("testCharSwappedCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testCharNullSwappedComparesLessEqual2() {
        test("testCharSwappedCompareLessEqualConstant2", (Object) null);
    }

    public static Object testCharCompareGreater(FieldObject f, char charValue) {
        if (f.charValue > charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareGreaterConstant1(FieldObject f) {
        if (f.charValue > charTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareGreaterConstant2(FieldObject f) {
        if (f.charValue > charTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharComparesGreater() {
        FieldObject f = new FieldObject();
        test("testCharCompareGreater", f, charTestValue1);
        test("testCharCompareGreaterConstant1", f);
        test("testCharCompareGreaterConstant2", f);
    }

    @Test
    public void testCharNullComparesGreater() {
        test("testCharCompareGreater", null, charTestValue1);
    }

    @Test
    public void testCharNullComparesGreater1() {
        test("testCharCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testCharNullComparesGreater2() {
        test("testCharCompareGreaterConstant2", (Object) null);
    }

    public static Object testCharSwappedCompareGreater(FieldObject f, char charValue) {
        if (charValue > f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareGreaterConstant1(FieldObject f) {
        if (charTestValue1 > f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareGreaterConstant2(FieldObject f) {
        if (charTestValue2 > f.charValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharSwappedComparesGreater() {
        FieldObject f = new FieldObject();
        test("testCharSwappedCompareGreater", f, charTestValue1);
        test("testCharSwappedCompareGreaterConstant1", f);
        test("testCharSwappedCompareGreaterConstant2", f);
    }

    @Test
    public void testCharNullSwappedComparesGreater() {
        test("testCharSwappedCompareGreater", null, charTestValue1);
    }

    @Test
    public void testCharNullSwappedComparesGreater1() {
        test("testCharSwappedCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testCharNullSwappedComparesGreater2() {
        test("testCharSwappedCompareGreaterConstant2", (Object) null);
    }

    public static Object testCharCompareGreaterEqual(FieldObject f, char charValue) {
        if (f.charValue >= charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareGreaterEqualConstant1(FieldObject f) {
        if (f.charValue >= charTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareGreaterEqualConstant2(FieldObject f) {
        if (f.charValue >= charTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testCharCompareGreaterEqual", f, charTestValue1);
        test("testCharCompareGreaterEqualConstant1", f);
        test("testCharCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testCharNullComparesGreaterEqual() {
        test("testCharCompareGreaterEqual", null, charTestValue1);
    }

    @Test
    public void testCharNullComparesGreaterEqual1() {
        test("testCharCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testCharNullComparesGreaterEqual2() {
        test("testCharCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testCharSwappedCompareGreaterEqual(FieldObject f, char charValue) {
        if (charValue >= f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareGreaterEqualConstant1(FieldObject f) {
        if (charTestValue1 >= f.charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharSwappedCompareGreaterEqualConstant2(FieldObject f) {
        if (charTestValue2 >= f.charValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharSwappedComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testCharSwappedCompareGreaterEqual", f, charTestValue1);
        test("testCharSwappedCompareGreaterEqualConstant1", f);
        test("testCharSwappedCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testCharNullSwappedComparesGreaterEqual() {
        test("testCharSwappedCompareGreaterEqual", null, charTestValue1);
    }

    @Test
    public void testCharNullSwappedComparesGreaterEqual1() {
        test("testCharSwappedCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testCharNullSwappedComparesGreaterEqual2() {
        test("testCharSwappedCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testIntCompare(FieldObject f, int intValue) {
        if (f.intValue == intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareConstant1(FieldObject f) {
        if (f.intValue == intTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareConstant2(FieldObject f) {
        if (f.intValue == intTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntCompares() {
        FieldObject f = new FieldObject();
        test("testIntCompare", f, intTestValue1);
        test("testIntCompareConstant1", f);
        test("testIntCompareConstant2", f);
    }

    @Test
    public void testIntNullCompares() {
        test("testIntCompare", null, intTestValue1);
    }

    @Test
    public void testIntNullCompares1() {
        test("testIntCompareConstant1", (Object) null);
    }

    @Test
    public void testIntNullCompares2() {
        test("testIntCompareConstant2", (Object) null);
    }

    public static Object testIntCompareLess(FieldObject f, int intValue) {
        if (f.intValue < intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareLessConstant1(FieldObject f) {
        if (f.intValue < intTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareLessConstant2(FieldObject f) {
        if (f.intValue < intTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntComparesLess() {
        FieldObject f = new FieldObject();
        test("testIntCompareLess", f, intTestValue1);
        test("testIntCompareLessConstant1", f);
        test("testIntCompareLessConstant2", f);
    }

    @Test
    public void testIntNullComparesLess() {
        test("testIntCompareLess", null, intTestValue1);
    }

    @Test
    public void testIntNullComparesLess1() {
        test("testIntCompareLessConstant1", (Object) null);
    }

    @Test
    public void testIntNullComparesLess2() {
        test("testIntCompareLessConstant2", (Object) null);
    }

    public static Object testIntSwappedCompareLess(FieldObject f, int intValue) {
        if (intValue < f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareLessConstant1(FieldObject f) {
        if (intTestValue1 < f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareLessConstant2(FieldObject f) {
        if (intTestValue2 < f.intValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntSwappedComparesLess() {
        FieldObject f = new FieldObject();
        test("testIntSwappedCompareLess", f, intTestValue1);
        test("testIntSwappedCompareLessConstant1", f);
        test("testIntSwappedCompareLessConstant2", f);
    }

    @Test
    public void testIntNullSwappedComparesLess() {
        test("testIntSwappedCompareLess", null, intTestValue1);
    }

    @Test
    public void testIntNullSwappedComparesLess1() {
        test("testIntSwappedCompareLessConstant1", (Object) null);
    }

    @Test
    public void testIntNullSwappedComparesLess2() {
        test("testIntSwappedCompareLessConstant2", (Object) null);
    }

    public static Object testIntCompareLessEqual(FieldObject f, int intValue) {
        if (f.intValue <= intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareLessEqualConstant1(FieldObject f) {
        if (f.intValue <= intTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareLessEqualConstant2(FieldObject f) {
        if (f.intValue <= intTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testIntCompareLessEqual", f, intTestValue1);
        test("testIntCompareLessEqualConstant1", f);
        test("testIntCompareLessEqualConstant2", f);
    }

    @Test
    public void testIntNullComparesLessEqual() {
        test("testIntCompareLessEqual", null, intTestValue1);
    }

    @Test
    public void testIntNullComparesLessEqual1() {
        test("testIntCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testIntNullComparesLessEqual2() {
        test("testIntCompareLessEqualConstant2", (Object) null);
    }

    public static Object testIntSwappedCompareLessEqual(FieldObject f, int intValue) {
        if (intValue <= f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareLessEqualConstant1(FieldObject f) {
        if (intTestValue1 <= f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareLessEqualConstant2(FieldObject f) {
        if (intTestValue2 <= f.intValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntSwappedComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testIntSwappedCompareLessEqual", f, intTestValue1);
        test("testIntSwappedCompareLessEqualConstant1", f);
        test("testIntSwappedCompareLessEqualConstant2", f);
    }

    @Test
    public void testIntNullSwappedComparesLessEqual() {
        test("testIntSwappedCompareLessEqual", null, intTestValue1);
    }

    @Test
    public void testIntNullSwappedComparesLessEqual1() {
        test("testIntSwappedCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testIntNullSwappedComparesLessEqual2() {
        test("testIntSwappedCompareLessEqualConstant2", (Object) null);
    }

    public static Object testIntCompareGreater(FieldObject f, int intValue) {
        if (f.intValue > intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareGreaterConstant1(FieldObject f) {
        if (f.intValue > intTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareGreaterConstant2(FieldObject f) {
        if (f.intValue > intTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntComparesGreater() {
        FieldObject f = new FieldObject();
        test("testIntCompareGreater", f, intTestValue1);
        test("testIntCompareGreaterConstant1", f);
        test("testIntCompareGreaterConstant2", f);
    }

    @Test
    public void testIntNullComparesGreater() {
        test("testIntCompareGreater", null, intTestValue1);
    }

    @Test
    public void testIntNullComparesGreater1() {
        test("testIntCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testIntNullComparesGreater2() {
        test("testIntCompareGreaterConstant2", (Object) null);
    }

    public static Object testIntSwappedCompareGreater(FieldObject f, int intValue) {
        if (intValue > f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareGreaterConstant1(FieldObject f) {
        if (intTestValue1 > f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareGreaterConstant2(FieldObject f) {
        if (intTestValue2 > f.intValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntSwappedComparesGreater() {
        FieldObject f = new FieldObject();
        test("testIntSwappedCompareGreater", f, intTestValue1);
        test("testIntSwappedCompareGreaterConstant1", f);
        test("testIntSwappedCompareGreaterConstant2", f);
    }

    @Test
    public void testIntNullSwappedComparesGreater() {
        test("testIntSwappedCompareGreater", null, intTestValue1);
    }

    @Test
    public void testIntNullSwappedComparesGreater1() {
        test("testIntSwappedCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testIntNullSwappedComparesGreater2() {
        test("testIntSwappedCompareGreaterConstant2", (Object) null);
    }

    public static Object testIntCompareGreaterEqual(FieldObject f, int intValue) {
        if (f.intValue >= intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareGreaterEqualConstant1(FieldObject f) {
        if (f.intValue >= intTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareGreaterEqualConstant2(FieldObject f) {
        if (f.intValue >= intTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testIntCompareGreaterEqual", f, intTestValue1);
        test("testIntCompareGreaterEqualConstant1", f);
        test("testIntCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testIntNullComparesGreaterEqual() {
        test("testIntCompareGreaterEqual", null, intTestValue1);
    }

    @Test
    public void testIntNullComparesGreaterEqual1() {
        test("testIntCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testIntNullComparesGreaterEqual2() {
        test("testIntCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testIntSwappedCompareGreaterEqual(FieldObject f, int intValue) {
        if (intValue >= f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareGreaterEqualConstant1(FieldObject f) {
        if (intTestValue1 >= f.intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntSwappedCompareGreaterEqualConstant2(FieldObject f) {
        if (intTestValue2 >= f.intValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntSwappedComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testIntSwappedCompareGreaterEqual", f, intTestValue1);
        test("testIntSwappedCompareGreaterEqualConstant1", f);
        test("testIntSwappedCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testIntNullSwappedComparesGreaterEqual() {
        test("testIntSwappedCompareGreaterEqual", null, intTestValue1);
    }

    @Test
    public void testIntNullSwappedComparesGreaterEqual1() {
        test("testIntSwappedCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testIntNullSwappedComparesGreaterEqual2() {
        test("testIntSwappedCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testFloatCompare(FieldObject f, float floatValue) {
        if (f.floatValue == floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareConstant1(FieldObject f) {
        if (f.floatValue == floatTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareConstant2(FieldObject f) {
        if (f.floatValue == floatTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatCompares() {
        FieldObject f = new FieldObject();
        test("testFloatCompare", f, floatTestValue1);
        test("testFloatCompareConstant1", f);
        test("testFloatCompareConstant2", f);
    }

    @Test
    public void testFloatNullCompares() {
        test("testFloatCompare", null, floatTestValue1);
    }

    @Test
    public void testFloatNullCompares1() {
        test("testFloatCompareConstant1", (Object) null);
    }

    @Test
    public void testFloatNullCompares2() {
        test("testFloatCompareConstant2", (Object) null);
    }

    public static Object testFloatCompareLess(FieldObject f, float floatValue) {
        if (f.floatValue < floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareLessConstant1(FieldObject f) {
        if (f.floatValue < floatTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareLessConstant2(FieldObject f) {
        if (f.floatValue < floatTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatComparesLess() {
        FieldObject f = new FieldObject();
        test("testFloatCompareLess", f, floatTestValue1);
        test("testFloatCompareLessConstant1", f);
        test("testFloatCompareLessConstant2", f);
    }

    @Test
    public void testFloatNullComparesLess() {
        test("testFloatCompareLess", null, floatTestValue1);
    }

    @Test
    public void testFloatNullComparesLess1() {
        test("testFloatCompareLessConstant1", (Object) null);
    }

    @Test
    public void testFloatNullComparesLess2() {
        test("testFloatCompareLessConstant2", (Object) null);
    }

    public static Object testFloatSwappedCompareLess(FieldObject f, float floatValue) {
        if (floatValue < f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareLessConstant1(FieldObject f) {
        if (floatTestValue1 < f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareLessConstant2(FieldObject f) {
        if (floatTestValue2 < f.floatValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatSwappedComparesLess() {
        FieldObject f = new FieldObject();
        test("testFloatSwappedCompareLess", f, floatTestValue1);
        test("testFloatSwappedCompareLessConstant1", f);
        test("testFloatSwappedCompareLessConstant2", f);
    }

    @Test
    public void testFloatNullSwappedComparesLess() {
        test("testFloatSwappedCompareLess", null, floatTestValue1);
    }

    @Test
    public void testFloatNullSwappedComparesLess1() {
        test("testFloatSwappedCompareLessConstant1", (Object) null);
    }

    @Test
    public void testFloatNullSwappedComparesLess2() {
        test("testFloatSwappedCompareLessConstant2", (Object) null);
    }

    public static Object testFloatCompareLessEqual(FieldObject f, float floatValue) {
        if (f.floatValue <= floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareLessEqualConstant1(FieldObject f) {
        if (f.floatValue <= floatTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareLessEqualConstant2(FieldObject f) {
        if (f.floatValue <= floatTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testFloatCompareLessEqual", f, floatTestValue1);
        test("testFloatCompareLessEqualConstant1", f);
        test("testFloatCompareLessEqualConstant2", f);
    }

    @Test
    public void testFloatNullComparesLessEqual() {
        test("testFloatCompareLessEqual", null, floatTestValue1);
    }

    @Test
    public void testFloatNullComparesLessEqual1() {
        test("testFloatCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testFloatNullComparesLessEqual2() {
        test("testFloatCompareLessEqualConstant2", (Object) null);
    }

    public static Object testFloatSwappedCompareLessEqual(FieldObject f, float floatValue) {
        if (floatValue <= f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareLessEqualConstant1(FieldObject f) {
        if (floatTestValue1 <= f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareLessEqualConstant2(FieldObject f) {
        if (floatTestValue2 <= f.floatValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatSwappedComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testFloatSwappedCompareLessEqual", f, floatTestValue1);
        test("testFloatSwappedCompareLessEqualConstant1", f);
        test("testFloatSwappedCompareLessEqualConstant2", f);
    }

    @Test
    public void testFloatNullSwappedComparesLessEqual() {
        test("testFloatSwappedCompareLessEqual", null, floatTestValue1);
    }

    @Test
    public void testFloatNullSwappedComparesLessEqual1() {
        test("testFloatSwappedCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testFloatNullSwappedComparesLessEqual2() {
        test("testFloatSwappedCompareLessEqualConstant2", (Object) null);
    }

    public static Object testFloatCompareGreater(FieldObject f, float floatValue) {
        if (f.floatValue > floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareGreaterConstant1(FieldObject f) {
        if (f.floatValue > floatTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareGreaterConstant2(FieldObject f) {
        if (f.floatValue > floatTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatComparesGreater() {
        FieldObject f = new FieldObject();
        test("testFloatCompareGreater", f, floatTestValue1);
        test("testFloatCompareGreaterConstant1", f);
        test("testFloatCompareGreaterConstant2", f);
    }

    @Test
    public void testFloatNullComparesGreater() {
        test("testFloatCompareGreater", null, floatTestValue1);
    }

    @Test
    public void testFloatNullComparesGreater1() {
        test("testFloatCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testFloatNullComparesGreater2() {
        test("testFloatCompareGreaterConstant2", (Object) null);
    }

    public static Object testFloatSwappedCompareGreater(FieldObject f, float floatValue) {
        if (floatValue > f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareGreaterConstant1(FieldObject f) {
        if (floatTestValue1 > f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareGreaterConstant2(FieldObject f) {
        if (floatTestValue2 > f.floatValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatSwappedComparesGreater() {
        FieldObject f = new FieldObject();
        test("testFloatSwappedCompareGreater", f, floatTestValue1);
        test("testFloatSwappedCompareGreaterConstant1", f);
        test("testFloatSwappedCompareGreaterConstant2", f);
    }

    @Test
    public void testFloatNullSwappedComparesGreater() {
        test("testFloatSwappedCompareGreater", null, floatTestValue1);
    }

    @Test
    public void testFloatNullSwappedComparesGreater1() {
        test("testFloatSwappedCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testFloatNullSwappedComparesGreater2() {
        test("testFloatSwappedCompareGreaterConstant2", (Object) null);
    }

    public static Object testFloatCompareGreaterEqual(FieldObject f, float floatValue) {
        if (f.floatValue >= floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareGreaterEqualConstant1(FieldObject f) {
        if (f.floatValue >= floatTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareGreaterEqualConstant2(FieldObject f) {
        if (f.floatValue >= floatTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testFloatCompareGreaterEqual", f, floatTestValue1);
        test("testFloatCompareGreaterEqualConstant1", f);
        test("testFloatCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testFloatNullComparesGreaterEqual() {
        test("testFloatCompareGreaterEqual", null, floatTestValue1);
    }

    @Test
    public void testFloatNullComparesGreaterEqual1() {
        test("testFloatCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testFloatNullComparesGreaterEqual2() {
        test("testFloatCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testFloatSwappedCompareGreaterEqual(FieldObject f, float floatValue) {
        if (floatValue >= f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareGreaterEqualConstant1(FieldObject f) {
        if (floatTestValue1 >= f.floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatSwappedCompareGreaterEqualConstant2(FieldObject f) {
        if (floatTestValue2 >= f.floatValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatSwappedComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testFloatSwappedCompareGreaterEqual", f, floatTestValue1);
        test("testFloatSwappedCompareGreaterEqualConstant1", f);
        test("testFloatSwappedCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testFloatNullSwappedComparesGreaterEqual() {
        test("testFloatSwappedCompareGreaterEqual", null, floatTestValue1);
    }

    @Test
    public void testFloatNullSwappedComparesGreaterEqual1() {
        test("testFloatSwappedCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testFloatNullSwappedComparesGreaterEqual2() {
        test("testFloatSwappedCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testLongCompare(FieldObject f, long longValue) {
        if (f.longValue == longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareConstant1(FieldObject f) {
        if (f.longValue == longTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareConstant2(FieldObject f) {
        if (f.longValue == longTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongCompares() {
        FieldObject f = new FieldObject();
        test("testLongCompare", f, longTestValue1);
        test("testLongCompareConstant1", f);
        test("testLongCompareConstant2", f);
    }

    @Test
    public void testLongNullCompares() {
        test("testLongCompare", null, longTestValue1);
    }

    @Test
    public void testLongNullCompares1() {
        test("testLongCompareConstant1", (Object) null);
    }

    @Test
    public void testLongNullCompares2() {
        test("testLongCompareConstant2", (Object) null);
    }

    public static Object testLongCompareLess(FieldObject f, long longValue) {
        if (f.longValue < longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareLessConstant1(FieldObject f) {
        if (f.longValue < longTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareLessConstant2(FieldObject f) {
        if (f.longValue < longTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongComparesLess() {
        FieldObject f = new FieldObject();
        test("testLongCompareLess", f, longTestValue1);
        test("testLongCompareLessConstant1", f);
        test("testLongCompareLessConstant2", f);
    }

    @Test
    public void testLongNullComparesLess() {
        test("testLongCompareLess", null, longTestValue1);
    }

    @Test
    public void testLongNullComparesLess1() {
        test("testLongCompareLessConstant1", (Object) null);
    }

    @Test
    public void testLongNullComparesLess2() {
        test("testLongCompareLessConstant2", (Object) null);
    }

    public static Object testLongSwappedCompareLess(FieldObject f, long longValue) {
        if (longValue < f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareLessConstant1(FieldObject f) {
        if (longTestValue1 < f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareLessConstant2(FieldObject f) {
        if (longTestValue2 < f.longValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongSwappedComparesLess() {
        FieldObject f = new FieldObject();
        test("testLongSwappedCompareLess", f, longTestValue1);
        test("testLongSwappedCompareLessConstant1", f);
        test("testLongSwappedCompareLessConstant2", f);
    }

    @Test
    public void testLongNullSwappedComparesLess() {
        test("testLongSwappedCompareLess", null, longTestValue1);
    }

    @Test
    public void testLongNullSwappedComparesLess1() {
        test("testLongSwappedCompareLessConstant1", (Object) null);
    }

    @Test
    public void testLongNullSwappedComparesLess2() {
        test("testLongSwappedCompareLessConstant2", (Object) null);
    }

    public static Object testLongCompareLessEqual(FieldObject f, long longValue) {
        if (f.longValue <= longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareLessEqualConstant1(FieldObject f) {
        if (f.longValue <= longTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareLessEqualConstant2(FieldObject f) {
        if (f.longValue <= longTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testLongCompareLessEqual", f, longTestValue1);
        test("testLongCompareLessEqualConstant1", f);
        test("testLongCompareLessEqualConstant2", f);
    }

    @Test
    public void testLongNullComparesLessEqual() {
        test("testLongCompareLessEqual", null, longTestValue1);
    }

    @Test
    public void testLongNullComparesLessEqual1() {
        test("testLongCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testLongNullComparesLessEqual2() {
        test("testLongCompareLessEqualConstant2", (Object) null);
    }

    public static Object testLongSwappedCompareLessEqual(FieldObject f, long longValue) {
        if (longValue <= f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareLessEqualConstant1(FieldObject f) {
        if (longTestValue1 <= f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareLessEqualConstant2(FieldObject f) {
        if (longTestValue2 <= f.longValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongSwappedComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testLongSwappedCompareLessEqual", f, longTestValue1);
        test("testLongSwappedCompareLessEqualConstant1", f);
        test("testLongSwappedCompareLessEqualConstant2", f);
    }

    @Test
    public void testLongNullSwappedComparesLessEqual() {
        test("testLongSwappedCompareLessEqual", null, longTestValue1);
    }

    @Test
    public void testLongNullSwappedComparesLessEqual1() {
        test("testLongSwappedCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testLongNullSwappedComparesLessEqual2() {
        test("testLongSwappedCompareLessEqualConstant2", (Object) null);
    }

    public static Object testLongCompareGreater(FieldObject f, long longValue) {
        if (f.longValue > longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareGreaterConstant1(FieldObject f) {
        if (f.longValue > longTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareGreaterConstant2(FieldObject f) {
        if (f.longValue > longTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongComparesGreater() {
        FieldObject f = new FieldObject();
        test("testLongCompareGreater", f, longTestValue1);
        test("testLongCompareGreaterConstant1", f);
        test("testLongCompareGreaterConstant2", f);
    }

    @Test
    public void testLongNullComparesGreater() {
        test("testLongCompareGreater", null, longTestValue1);
    }

    @Test
    public void testLongNullComparesGreater1() {
        test("testLongCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testLongNullComparesGreater2() {
        test("testLongCompareGreaterConstant2", (Object) null);
    }

    public static Object testLongSwappedCompareGreater(FieldObject f, long longValue) {
        if (longValue > f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareGreaterConstant1(FieldObject f) {
        if (longTestValue1 > f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareGreaterConstant2(FieldObject f) {
        if (longTestValue2 > f.longValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongSwappedComparesGreater() {
        FieldObject f = new FieldObject();
        test("testLongSwappedCompareGreater", f, longTestValue1);
        test("testLongSwappedCompareGreaterConstant1", f);
        test("testLongSwappedCompareGreaterConstant2", f);
    }

    @Test
    public void testLongNullSwappedComparesGreater() {
        test("testLongSwappedCompareGreater", null, longTestValue1);
    }

    @Test
    public void testLongNullSwappedComparesGreater1() {
        test("testLongSwappedCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testLongNullSwappedComparesGreater2() {
        test("testLongSwappedCompareGreaterConstant2", (Object) null);
    }

    public static Object testLongCompareGreaterEqual(FieldObject f, long longValue) {
        if (f.longValue >= longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareGreaterEqualConstant1(FieldObject f) {
        if (f.longValue >= longTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareGreaterEqualConstant2(FieldObject f) {
        if (f.longValue >= longTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testLongCompareGreaterEqual", f, longTestValue1);
        test("testLongCompareGreaterEqualConstant1", f);
        test("testLongCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testLongNullComparesGreaterEqual() {
        test("testLongCompareGreaterEqual", null, longTestValue1);
    }

    @Test
    public void testLongNullComparesGreaterEqual1() {
        test("testLongCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testLongNullComparesGreaterEqual2() {
        test("testLongCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testLongSwappedCompareGreaterEqual(FieldObject f, long longValue) {
        if (longValue >= f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareGreaterEqualConstant1(FieldObject f) {
        if (longTestValue1 >= f.longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongSwappedCompareGreaterEqualConstant2(FieldObject f) {
        if (longTestValue2 >= f.longValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongSwappedComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testLongSwappedCompareGreaterEqual", f, longTestValue1);
        test("testLongSwappedCompareGreaterEqualConstant1", f);
        test("testLongSwappedCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testLongNullSwappedComparesGreaterEqual() {
        test("testLongSwappedCompareGreaterEqual", null, longTestValue1);
    }

    @Test
    public void testLongNullSwappedComparesGreaterEqual1() {
        test("testLongSwappedCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testLongNullSwappedComparesGreaterEqual2() {
        test("testLongSwappedCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testDoubleCompare(FieldObject f, double doubleValue) {
        if (f.doubleValue == doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareConstant1(FieldObject f) {
        if (f.doubleValue == doubleTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareConstant2(FieldObject f) {
        if (f.doubleValue == doubleTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleCompares() {
        FieldObject f = new FieldObject();
        test("testDoubleCompare", f, doubleTestValue1);
        test("testDoubleCompareConstant1", f);
        test("testDoubleCompareConstant2", f);
    }

    @Test
    public void testDoubleNullCompares() {
        test("testDoubleCompare", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullCompares1() {
        test("testDoubleCompareConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullCompares2() {
        test("testDoubleCompareConstant2", (Object) null);
    }

    public static Object testDoubleCompareLess(FieldObject f, double doubleValue) {
        if (f.doubleValue < doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareLessConstant1(FieldObject f) {
        if (f.doubleValue < doubleTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareLessConstant2(FieldObject f) {
        if (f.doubleValue < doubleTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleComparesLess() {
        FieldObject f = new FieldObject();
        test("testDoubleCompareLess", f, doubleTestValue1);
        test("testDoubleCompareLessConstant1", f);
        test("testDoubleCompareLessConstant2", f);
    }

    @Test
    public void testDoubleNullComparesLess() {
        test("testDoubleCompareLess", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullComparesLess1() {
        test("testDoubleCompareLessConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullComparesLess2() {
        test("testDoubleCompareLessConstant2", (Object) null);
    }

    public static Object testDoubleSwappedCompareLess(FieldObject f, double doubleValue) {
        if (doubleValue < f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareLessConstant1(FieldObject f) {
        if (doubleTestValue1 < f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareLessConstant2(FieldObject f) {
        if (doubleTestValue2 < f.doubleValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleSwappedComparesLess() {
        FieldObject f = new FieldObject();
        test("testDoubleSwappedCompareLess", f, doubleTestValue1);
        test("testDoubleSwappedCompareLessConstant1", f);
        test("testDoubleSwappedCompareLessConstant2", f);
    }

    @Test
    public void testDoubleNullSwappedComparesLess() {
        test("testDoubleSwappedCompareLess", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullSwappedComparesLess1() {
        test("testDoubleSwappedCompareLessConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullSwappedComparesLess2() {
        test("testDoubleSwappedCompareLessConstant2", (Object) null);
    }

    public static Object testDoubleCompareLessEqual(FieldObject f, double doubleValue) {
        if (f.doubleValue <= doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareLessEqualConstant1(FieldObject f) {
        if (f.doubleValue <= doubleTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareLessEqualConstant2(FieldObject f) {
        if (f.doubleValue <= doubleTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testDoubleCompareLessEqual", f, doubleTestValue1);
        test("testDoubleCompareLessEqualConstant1", f);
        test("testDoubleCompareLessEqualConstant2", f);
    }

    @Test
    public void testDoubleNullComparesLessEqual() {
        test("testDoubleCompareLessEqual", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullComparesLessEqual1() {
        test("testDoubleCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullComparesLessEqual2() {
        test("testDoubleCompareLessEqualConstant2", (Object) null);
    }

    public static Object testDoubleSwappedCompareLessEqual(FieldObject f, double doubleValue) {
        if (doubleValue <= f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareLessEqualConstant1(FieldObject f) {
        if (doubleTestValue1 <= f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareLessEqualConstant2(FieldObject f) {
        if (doubleTestValue2 <= f.doubleValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleSwappedComparesLessEqual() {
        FieldObject f = new FieldObject();
        test("testDoubleSwappedCompareLessEqual", f, doubleTestValue1);
        test("testDoubleSwappedCompareLessEqualConstant1", f);
        test("testDoubleSwappedCompareLessEqualConstant2", f);
    }

    @Test
    public void testDoubleNullSwappedComparesLessEqual() {
        test("testDoubleSwappedCompareLessEqual", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullSwappedComparesLessEqual1() {
        test("testDoubleSwappedCompareLessEqualConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullSwappedComparesLessEqual2() {
        test("testDoubleSwappedCompareLessEqualConstant2", (Object) null);
    }

    public static Object testDoubleCompareGreater(FieldObject f, double doubleValue) {
        if (f.doubleValue > doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareGreaterConstant1(FieldObject f) {
        if (f.doubleValue > doubleTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareGreaterConstant2(FieldObject f) {
        if (f.doubleValue > doubleTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleComparesGreater() {
        FieldObject f = new FieldObject();
        test("testDoubleCompareGreater", f, doubleTestValue1);
        test("testDoubleCompareGreaterConstant1", f);
        test("testDoubleCompareGreaterConstant2", f);
    }

    @Test
    public void testDoubleNullComparesGreater() {
        test("testDoubleCompareGreater", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullComparesGreater1() {
        test("testDoubleCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullComparesGreater2() {
        test("testDoubleCompareGreaterConstant2", (Object) null);
    }

    public static Object testDoubleSwappedCompareGreater(FieldObject f, double doubleValue) {
        if (doubleValue > f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareGreaterConstant1(FieldObject f) {
        if (doubleTestValue1 > f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareGreaterConstant2(FieldObject f) {
        if (doubleTestValue2 > f.doubleValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleSwappedComparesGreater() {
        FieldObject f = new FieldObject();
        test("testDoubleSwappedCompareGreater", f, doubleTestValue1);
        test("testDoubleSwappedCompareGreaterConstant1", f);
        test("testDoubleSwappedCompareGreaterConstant2", f);
    }

    @Test
    public void testDoubleNullSwappedComparesGreater() {
        test("testDoubleSwappedCompareGreater", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullSwappedComparesGreater1() {
        test("testDoubleSwappedCompareGreaterConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullSwappedComparesGreater2() {
        test("testDoubleSwappedCompareGreaterConstant2", (Object) null);
    }

    public static Object testDoubleCompareGreaterEqual(FieldObject f, double doubleValue) {
        if (f.doubleValue >= doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareGreaterEqualConstant1(FieldObject f) {
        if (f.doubleValue >= doubleTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareGreaterEqualConstant2(FieldObject f) {
        if (f.doubleValue >= doubleTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testDoubleCompareGreaterEqual", f, doubleTestValue1);
        test("testDoubleCompareGreaterEqualConstant1", f);
        test("testDoubleCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testDoubleNullComparesGreaterEqual() {
        test("testDoubleCompareGreaterEqual", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullComparesGreaterEqual1() {
        test("testDoubleCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullComparesGreaterEqual2() {
        test("testDoubleCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testDoubleSwappedCompareGreaterEqual(FieldObject f, double doubleValue) {
        if (doubleValue >= f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareGreaterEqualConstant1(FieldObject f) {
        if (doubleTestValue1 >= f.doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleSwappedCompareGreaterEqualConstant2(FieldObject f) {
        if (doubleTestValue2 >= f.doubleValue) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleSwappedComparesGreaterEqual() {
        FieldObject f = new FieldObject();
        test("testDoubleSwappedCompareGreaterEqual", f, doubleTestValue1);
        test("testDoubleSwappedCompareGreaterEqualConstant1", f);
        test("testDoubleSwappedCompareGreaterEqualConstant2", f);
    }

    @Test
    public void testDoubleNullSwappedComparesGreaterEqual() {
        test("testDoubleSwappedCompareGreaterEqual", null, doubleTestValue1);
    }

    @Test
    public void testDoubleNullSwappedComparesGreaterEqual1() {
        test("testDoubleSwappedCompareGreaterEqualConstant1", (Object) null);
    }

    @Test
    public void testDoubleNullSwappedComparesGreaterEqual2() {
        test("testDoubleSwappedCompareGreaterEqualConstant2", (Object) null);
    }

    public static Object testObjectCompare(FieldObject f, Object objectValue) {
        if (f.objectValue == objectValue) {
            return f;
        }
        return null;
    }

    public static Object testObjectCompareConstant1(FieldObject f) {
        if (f.objectValue == objectTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testObjectCompareConstant2(FieldObject f) {
        if (f.objectValue == objectTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testObjectCompares() {
        FieldObject f = new FieldObject();
        test("testObjectCompare", f, objectTestValue1);
        test("testObjectCompareConstant1", f);
        test("testObjectCompareConstant2", f);
    }

    @Test
    public void testObjectNullCompares() {
        test("testObjectCompare", null, objectTestValue1);
    }

    @Test
    public void testObjectNullCompares1() {
        test("testObjectCompareConstant1", (Object) null);
    }

    @Test
    public void testObjectNullCompares2() {
        test("testObjectCompareConstant2", (Object) null);
    }

    public static int testByteAdd(FieldObject f, byte byteValue) {
        return f.byteValue + byteValue;
    }

    public static int testByteAddConstant1(FieldObject f) {
        return f.byteValue + byteTestValue1;
    }

    public static int testByteAddConstant2(FieldObject f) {
        return f.byteValue + byteTestValue2;
    }

    @Test
    public void testByteAdds() {
        FieldObject f = new FieldObject();
        test("testByteAdd", f, byteTestValue1);
        test("testByteAddConstant1", f);
        test("testByteAddConstant2", f);
    }

    @Test
    public void testByteNullAdd() {
        test("testByteAdd", null, byteTestValue1);
    }

    public static int testShortAdd(FieldObject f, short shortValue) {
        return f.shortValue + shortValue;
    }

    public static int testShortAddConstant1(FieldObject f) {
        return f.shortValue + shortTestValue1;
    }

    public static int testShortAddConstant2(FieldObject f) {
        return f.shortValue + shortTestValue2;
    }

    @Test
    public void testShortAdds() {
        FieldObject f = new FieldObject();
        test("testShortAdd", f, shortTestValue1);
        test("testShortAddConstant1", f);
        test("testShortAddConstant2", f);
    }

    @Test
    public void testShortNullAdd() {
        test("testShortAdd", null, shortTestValue1);
    }

    public static int testCharAdd(FieldObject f, char charValue) {
        return f.charValue + charValue;
    }

    public static int testCharAddConstant1(FieldObject f) {
        return f.charValue + charTestValue1;
    }

    public static int testCharAddConstant2(FieldObject f) {
        return f.charValue + charTestValue2;
    }

    @Test
    public void testCharAdds() {
        FieldObject f = new FieldObject();
        test("testCharAdd", f, charTestValue1);
        test("testCharAddConstant1", f);
        test("testCharAddConstant2", f);
    }

    @Test
    public void testCharNullAdd() {
        test("testCharAdd", null, charTestValue1);
    }

    public static int testIntAdd(FieldObject f, int intValue) {
        return f.intValue + intValue;
    }

    public static int testIntAddConstant1(FieldObject f) {
        return f.intValue + intTestValue1;
    }

    public static int testIntAddConstant2(FieldObject f) {
        return f.intValue + intTestValue2;
    }

    @Test
    public void testIntAdds() {
        FieldObject f = new FieldObject();
        test("testIntAdd", f, intTestValue1);
        test("testIntAddConstant1", f);
        test("testIntAddConstant2", f);
    }

    @Test
    public void testIntNullAdd() {
        test("testIntAdd", null, intTestValue1);
    }

    public static long testLongAdd(FieldObject f, long longValue) {
        return f.longValue + longValue;
    }

    public static long testLongAddConstant1(FieldObject f) {
        return f.longValue + longTestValue1;
    }

    public static long testLongAddConstant2(FieldObject f) {
        return f.longValue + longTestValue2;
    }

    @Test
    public void testLongAdds() {
        FieldObject f = new FieldObject();
        test("testLongAdd", f, longTestValue1);
        test("testLongAddConstant1", f);
        test("testLongAddConstant2", f);
    }

    @Test
    public void testLongNullAdd() {
        test("testLongAdd", null, longTestValue1);
    }

    public static float testFloatAdd(FieldObject f, float floatValue) {
        return f.floatValue + floatValue;
    }

    public static float testFloatAddConstant1(FieldObject f) {
        return f.floatValue + floatTestValue1;
    }

    public static float testFloatAddConstant2(FieldObject f) {
        return f.floatValue + floatTestValue2;
    }

    @Test
    public void testFloatAdds() {
        FieldObject f = new FieldObject();
        test("testFloatAdd", f, floatTestValue1);
        test("testFloatAddConstant1", f);
        test("testFloatAddConstant2", f);
    }

    @Test
    public void testFloatNullAdd() {
        test("testFloatAdd", null, floatTestValue1);
    }

    public static double testDoubleAdd(FieldObject f, double doubleValue) {
        return f.doubleValue + doubleValue;
    }

    public static double testDoubleAddConstant1(FieldObject f) {
        return f.doubleValue + doubleTestValue1;
    }

    public static double testDoubleAddConstant2(FieldObject f) {
        return f.doubleValue + doubleTestValue2;
    }

    @Test
    public void testDoubleAdds() {
        FieldObject f = new FieldObject();
        test("testDoubleAdd", f, doubleTestValue1);
        test("testDoubleAddConstant1", f);
        test("testDoubleAddConstant2", f);
    }

    @Test
    public void testDoubleNullAdd() {
        test("testDoubleAdd", null, doubleTestValue1);
    }

    public static int testByteSub(FieldObject f, byte byteValue) {
        return f.byteValue - byteValue;
    }

    public static int testByteSubConstant1(FieldObject f) {
        return f.byteValue - byteTestValue1;
    }

    public static int testByteSubConstant2(FieldObject f) {
        return f.byteValue - byteTestValue2;
    }

    @Test
    public void testByteSubs() {
        FieldObject f = new FieldObject();
        test("testByteSub", f, byteTestValue1);
        test("testByteSubConstant1", f);
        test("testByteSubConstant2", f);
    }

    @Test
    public void testByteNullSub() {
        test("testByteSub", null, byteTestValue1);
    }

    public static int testShortSub(FieldObject f, short shortValue) {
        return f.shortValue - shortValue;
    }

    public static int testShortSubConstant1(FieldObject f) {
        return f.shortValue - shortTestValue1;
    }

    public static int testShortSubConstant2(FieldObject f) {
        return f.shortValue - shortTestValue2;
    }

    @Test
    public void testShortSubs() {
        FieldObject f = new FieldObject();
        test("testShortSub", f, shortTestValue1);
        test("testShortSubConstant1", f);
        test("testShortSubConstant2", f);
    }

    @Test
    public void testShortNullSub() {
        test("testShortSub", null, shortTestValue1);
    }

    public static int testCharSub(FieldObject f, char charValue) {
        return f.charValue - charValue;
    }

    public static int testCharSubConstant1(FieldObject f) {
        return f.charValue - charTestValue1;
    }

    public static int testCharSubConstant2(FieldObject f) {
        return f.charValue - charTestValue2;
    }

    @Test
    public void testCharSubs() {
        FieldObject f = new FieldObject();
        test("testCharSub", f, charTestValue1);
        test("testCharSubConstant1", f);
        test("testCharSubConstant2", f);
    }

    @Test
    public void testCharNullSub() {
        test("testCharSub", null, charTestValue1);
    }

    public static int testIntSub(FieldObject f, int intValue) {
        return f.intValue - intValue;
    }

    public static int testIntSubConstant1(FieldObject f) {
        return f.intValue - intTestValue1;
    }

    public static int testIntSubConstant2(FieldObject f) {
        return f.intValue - intTestValue2;
    }

    @Test
    public void testIntSubs() {
        FieldObject f = new FieldObject();
        test("testIntSub", f, intTestValue1);
        test("testIntSubConstant1", f);
        test("testIntSubConstant2", f);
    }

    @Test
    public void testIntNullSub() {
        test("testIntSub", null, intTestValue1);
    }

    public static long testLongSub(FieldObject f, long longValue) {
        return f.longValue - longValue;
    }

    public static long testLongSubConstant1(FieldObject f) {
        return f.longValue - longTestValue1;
    }

    public static long testLongSubConstant2(FieldObject f) {
        return f.longValue - longTestValue2;
    }

    @Test
    public void testLongSubs() {
        FieldObject f = new FieldObject();
        test("testLongSub", f, longTestValue1);
        test("testLongSubConstant1", f);
        test("testLongSubConstant2", f);
    }

    @Test
    public void testLongNullSub() {
        test("testLongSub", null, longTestValue1);
    }

    public static float testFloatSub(FieldObject f, float floatValue) {
        return f.floatValue - floatValue;
    }

    public static float testFloatSubConstant1(FieldObject f) {
        return f.floatValue - floatTestValue1;
    }

    public static float testFloatSubConstant2(FieldObject f) {
        return f.floatValue - floatTestValue2;
    }

    @Test
    public void testFloatSubs() {
        FieldObject f = new FieldObject();
        test("testFloatSub", f, floatTestValue1);
        test("testFloatSubConstant1", f);
        test("testFloatSubConstant2", f);
    }

    @Test
    public void testFloatNullSub() {
        test("testFloatSub", null, floatTestValue1);
    }

    public static double testDoubleSub(FieldObject f, double doubleValue) {
        return f.doubleValue - doubleValue;
    }

    public static double testDoubleSubConstant1(FieldObject f) {
        return f.doubleValue - doubleTestValue1;
    }

    public static double testDoubleSubConstant2(FieldObject f) {
        return f.doubleValue - doubleTestValue2;
    }

    @Test
    public void testDoubleSubs() {
        FieldObject f = new FieldObject();
        test("testDoubleSub", f, doubleTestValue1);
        test("testDoubleSubConstant1", f);
        test("testDoubleSubConstant2", f);
    }

    @Test
    public void testDoubleNullSub() {
        test("testDoubleSub", null, doubleTestValue1);
    }

    public static int testByteMul(FieldObject f, byte byteValue) {
        return f.byteValue * byteValue;
    }

    public static int testByteMulConstant1(FieldObject f) {
        return f.byteValue * byteTestValue1;
    }

    public static int testByteMulConstant2(FieldObject f) {
        return f.byteValue * byteTestValue2;
    }

    @Test
    public void testByteMuls() {
        FieldObject f = new FieldObject();
        test("testByteMul", f, byteTestValue1);
        test("testByteMulConstant1", f);
        test("testByteMulConstant2", f);
    }

    @Test
    public void testByteNullMul() {
        test("testByteMul", null, byteTestValue1);
    }

    public static int testShortMul(FieldObject f, short shortValue) {
        return f.shortValue * shortValue;
    }

    public static int testShortMulConstant1(FieldObject f) {
        return f.shortValue * shortTestValue1;
    }

    public static int testShortMulConstant2(FieldObject f) {
        return f.shortValue * shortTestValue2;
    }

    @Test
    public void testShortMuls() {
        FieldObject f = new FieldObject();
        test("testShortMul", f, shortTestValue1);
        test("testShortMulConstant1", f);
        test("testShortMulConstant2", f);
    }

    @Test
    public void testShortNullMul() {
        test("testShortMul", null, shortTestValue1);
    }

    public static int testCharMul(FieldObject f, char charValue) {
        return f.charValue * charValue;
    }

    public static int testCharMulConstant1(FieldObject f) {
        return f.charValue * charTestValue1;
    }

    public static int testCharMulConstant2(FieldObject f) {
        return f.charValue * charTestValue2;
    }

    @Test
    public void testCharMuls() {
        FieldObject f = new FieldObject();
        test("testCharMul", f, charTestValue1);
        test("testCharMulConstant1", f);
        test("testCharMulConstant2", f);
    }

    @Test
    public void testCharNullMul() {
        test("testCharMul", null, charTestValue1);
    }

    public static int testIntMul(FieldObject f, int intValue) {
        return f.intValue * intValue;
    }

    public static int testIntMulConstant1(FieldObject f) {
        return f.intValue * intTestValue1;
    }

    public static int testIntMulConstant2(FieldObject f) {
        return f.intValue * intTestValue2;
    }

    @Test
    public void testIntMuls() {
        FieldObject f = new FieldObject();
        test("testIntMul", f, intTestValue1);
        test("testIntMulConstant1", f);
        test("testIntMulConstant2", f);
    }

    @Test
    public void testIntNullMul() {
        test("testIntMul", null, intTestValue1);
    }

    public static long testLongMul(FieldObject f, long longValue) {
        return f.longValue * longValue;
    }

    public static long testLongMulConstant1(FieldObject f) {
        return f.longValue * longTestValue1;
    }

    public static long testLongMulConstant2(FieldObject f) {
        return f.longValue * longTestValue2;
    }

    @Test
    public void testLongMuls() {
        FieldObject f = new FieldObject();
        test("testLongMul", f, longTestValue1);
        test("testLongMulConstant1", f);
        test("testLongMulConstant2", f);
    }

    @Test
    public void testLongNullMul() {
        test("testLongMul", null, longTestValue1);
    }

    public static float testFloatMul(FieldObject f, float floatValue) {
        return f.floatValue * floatValue;
    }

    public static float testFloatMulConstant1(FieldObject f) {
        return f.floatValue * floatTestValue1;
    }

    public static float testFloatMulConstant2(FieldObject f) {
        return f.floatValue * floatTestValue2;
    }

    @Test
    public void testFloatMuls() {
        FieldObject f = new FieldObject();
        test("testFloatMul", f, floatTestValue1);
        test("testFloatMulConstant1", f);
        test("testFloatMulConstant2", f);
    }

    @Test
    public void testFloatNullMul() {
        test("testFloatMul", null, floatTestValue1);
    }

    public static double testDoubleMul(FieldObject f, double doubleValue) {
        return f.doubleValue * doubleValue;
    }

    public static double testDoubleMulConstant1(FieldObject f) {
        return f.doubleValue * doubleTestValue1;
    }

    public static double testDoubleMulConstant2(FieldObject f) {
        return f.doubleValue * doubleTestValue2;
    }

    @Test
    public void testDoubleMuls() {
        FieldObject f = new FieldObject();
        test("testDoubleMul", f, doubleTestValue1);
        test("testDoubleMulConstant1", f);
        test("testDoubleMulConstant2", f);
    }

    @Test
    public void testDoubleNullMul() {
        test("testDoubleMul", null, doubleTestValue1);
    }

    public static int testByteDiv(FieldObject f, byte byteValue) {
        return f.byteValue / byteValue;
    }

    @SuppressWarnings("divzero")
    public static int testByteDivConstant1(FieldObject f) {
        return f.byteValue / byteTestValue1;
    }

    public static int testByteDivConstant2(FieldObject f) {
        return f.byteValue / byteTestValue2;
    }

    @Test
    public void testByteDivs() {
        FieldObject f = new FieldObject();
        test("testByteDiv", f, byteTestValue1);
        test("testByteDivConstant1", f);
        test("testByteDivConstant2", f);
    }

    @Test
    public void testByteNullDiv() {
        test("testByteDiv", null, byteTestValue1);
    }

    public static int testShortDiv(FieldObject f, short shortValue) {
        return f.shortValue / shortValue;
    }

    @SuppressWarnings("divzero")
    public static int testShortDivConstant1(FieldObject f) {
        return f.shortValue / shortTestValue1;
    }

    public static int testShortDivConstant2(FieldObject f) {
        return f.shortValue / shortTestValue2;
    }

    @Test
    public void testShortDivs() {
        FieldObject f = new FieldObject();
        test("testShortDiv", f, shortTestValue1);
        test("testShortDivConstant1", f);
        test("testShortDivConstant2", f);
    }

    @Test
    public void testShortNullDiv() {
        test("testShortDiv", null, shortTestValue1);
    }

    public static int testCharDiv(FieldObject f, char charValue) {
        return f.charValue / charValue;
    }

    @SuppressWarnings("divzero")
    public static int testCharDivConstant1(FieldObject f) {
        return f.charValue / charTestValue1;
    }

    public static int testCharDivConstant2(FieldObject f) {
        return f.charValue / charTestValue2;
    }

    @Test
    public void testCharDivs() {
        FieldObject f = new FieldObject();
        test("testCharDiv", f, charTestValue1);
        test("testCharDivConstant1", f);
        test("testCharDivConstant2", f);
    }

    @Test
    public void testCharNullDiv() {
        test("testCharDiv", null, charTestValue1);
    }

    public static int testIntDiv(FieldObject f, int intValue) {
        return f.intValue / intValue;
    }

    @SuppressWarnings("divzero")
    public static int testIntDivConstant1(FieldObject f) {
        return f.intValue / intTestValue1;
    }

    public static int testIntDivConstant2(FieldObject f) {
        return f.intValue / intTestValue2;
    }

    @Test
    public void testIntDivs() {
        FieldObject f = new FieldObject();
        test("testIntDiv", f, intTestValue1);
        test("testIntDivConstant1", f);
        test("testIntDivConstant2", f);
    }

    @Test
    public void testIntNullDiv() {
        test("testIntDiv", null, intTestValue1);
    }

    public static long testLongDiv(FieldObject f, long longValue) {
        return f.longValue / longValue;
    }

    @SuppressWarnings("divzero")
    public static long testLongDivConstant1(FieldObject f) {
        return f.longValue / longTestValue1;
    }

    public static long testLongDivConstant2(FieldObject f) {
        return f.longValue / longTestValue2;
    }

    @Test
    public void testLongDivs() {
        FieldObject f = new FieldObject();
        test("testLongDiv", f, longTestValue1);
        test("testLongDivConstant1", f);
        test("testLongDivConstant2", f);
    }

    @Test
    public void testLongNullDiv() {
        test("testLongDiv", null, longTestValue1);
    }

    public static float testFloatDiv(FieldObject f, float floatValue) {
        return f.floatValue / floatValue;
    }

    public static float testFloatDivConstant1(FieldObject f) {
        return f.floatValue / floatTestValue1;
    }

    public static float testFloatDivConstant2(FieldObject f) {
        return f.floatValue / floatTestValue2;
    }

    @Test
    public void testFloatDivs() {
        FieldObject f = new FieldObject();
        test("testFloatDiv", f, floatTestValue1);
        test("testFloatDivConstant1", f);
        test("testFloatDivConstant2", f);
    }

    @Test
    public void testFloatNullDiv() {
        test("testFloatDiv", null, floatTestValue1);
    }

    public static double testDoubleDiv(FieldObject f, double doubleValue) {
        return f.doubleValue / doubleValue;
    }

    public static double testDoubleDivConstant1(FieldObject f) {
        return f.doubleValue / doubleTestValue1;
    }

    public static double testDoubleDivConstant2(FieldObject f) {
        return f.doubleValue / doubleTestValue2;
    }

    @Test
    public void testDoubleDivs() {
        FieldObject f = new FieldObject();
        test("testDoubleDiv", f, doubleTestValue1);
        test("testDoubleDivConstant1", f);
        test("testDoubleDivConstant2", f);
    }

    @Test
    public void testDoubleNullDiv() {
        test("testDoubleDiv", null, doubleTestValue1);
    }

    public static int testByteOr(FieldObject f, byte byteValue) {
        return f.byteValue | byteValue;
    }

    public static int testByteOrConstant1(FieldObject f) {
        return f.byteValue | byteTestValue1;
    }

    public static int testByteOrConstant2(FieldObject f) {
        return f.byteValue | byteTestValue2;
    }

    @Test
    public void testByteOrs() {
        FieldObject f = new FieldObject();
        test("testByteOr", f, byteTestValue1);
        test("testByteOrConstant1", f);
        test("testByteOrConstant2", f);
    }

    @Test
    public void testByteNullOr() {
        test("testByteOr", null, byteTestValue1);
    }

    public static int testShortOr(FieldObject f, short shortValue) {
        return f.shortValue | shortValue;
    }

    public static int testShortOrConstant1(FieldObject f) {
        return f.shortValue | shortTestValue1;
    }

    public static int testShortOrConstant2(FieldObject f) {
        return f.shortValue | shortTestValue2;
    }

    @Test
    public void testShortOrs() {
        FieldObject f = new FieldObject();
        test("testShortOr", f, shortTestValue1);
        test("testShortOrConstant1", f);
        test("testShortOrConstant2", f);
    }

    @Test
    public void testShortNullOr() {
        test("testShortOr", null, shortTestValue1);
    }

    public static int testCharOr(FieldObject f, char charValue) {
        return f.charValue | charValue;
    }

    public static int testCharOrConstant1(FieldObject f) {
        return f.charValue | charTestValue1;
    }

    public static int testCharOrConstant2(FieldObject f) {
        return f.charValue | charTestValue2;
    }

    @Test
    public void testCharOrs() {
        FieldObject f = new FieldObject();
        test("testCharOr", f, charTestValue1);
        test("testCharOrConstant1", f);
        test("testCharOrConstant2", f);
    }

    @Test
    public void testCharNullOr() {
        test("testCharOr", null, charTestValue1);
    }

    public static int testIntOr(FieldObject f, int intValue) {
        return f.intValue | intValue;
    }

    public static int testIntOrConstant1(FieldObject f) {
        return f.intValue | intTestValue1;
    }

    public static int testIntOrConstant2(FieldObject f) {
        return f.intValue | intTestValue2;
    }

    @Test
    public void testIntOrs() {
        FieldObject f = new FieldObject();
        test("testIntOr", f, intTestValue1);
        test("testIntOrConstant1", f);
        test("testIntOrConstant2", f);
    }

    @Test
    public void testIntNullOr() {
        test("testIntOr", null, intTestValue1);
    }

    public static long testLongOr(FieldObject f, long longValue) {
        return f.longValue | longValue;
    }

    public static long testLongOrConstant1(FieldObject f) {
        return f.longValue | longTestValue1;
    }

    public static long testLongOrConstant2(FieldObject f) {
        return f.longValue | longTestValue2;
    }

    @Test
    public void testLongOrs() {
        FieldObject f = new FieldObject();
        test("testLongOr", f, longTestValue1);
        test("testLongOrConstant1", f);
        test("testLongOrConstant2", f);
    }

    @Test
    public void testLongNullOr() {
        test("testLongOr", null, longTestValue1);
    }

    public static int testByteXor(FieldObject f, byte byteValue) {
        return f.byteValue ^ byteValue;
    }

    public static int testByteXorConstant1(FieldObject f) {
        return f.byteValue ^ byteTestValue1;
    }

    public static int testByteXorConstant2(FieldObject f) {
        return f.byteValue ^ byteTestValue2;
    }

    @Test
    public void testByteXors() {
        FieldObject f = new FieldObject();
        test("testByteXor", f, byteTestValue1);
        test("testByteXorConstant1", f);
        test("testByteXorConstant2", f);
    }

    @Test
    public void testByteNullXor() {
        test("testByteXor", null, byteTestValue1);
    }

    public static int testShortXor(FieldObject f, short shortValue) {
        return f.shortValue ^ shortValue;
    }

    public static int testShortXorConstant1(FieldObject f) {
        return f.shortValue ^ shortTestValue1;
    }

    public static int testShortXorConstant2(FieldObject f) {
        return f.shortValue ^ shortTestValue2;
    }

    @Test
    public void testShortXors() {
        FieldObject f = new FieldObject();
        test("testShortXor", f, shortTestValue1);
        test("testShortXorConstant1", f);
        test("testShortXorConstant2", f);
    }

    @Test
    public void testShortNullXor() {
        test("testShortXor", null, shortTestValue1);
    }

    public static int testCharXor(FieldObject f, char charValue) {
        return f.charValue ^ charValue;
    }

    public static int testCharXorConstant1(FieldObject f) {
        return f.charValue ^ charTestValue1;
    }

    public static int testCharXorConstant2(FieldObject f) {
        return f.charValue ^ charTestValue2;
    }

    @Test
    public void testCharXors() {
        FieldObject f = new FieldObject();
        test("testCharXor", f, charTestValue1);
        test("testCharXorConstant1", f);
        test("testCharXorConstant2", f);
    }

    @Test
    public void testCharNullXor() {
        test("testCharXor", null, charTestValue1);
    }

    public static int testIntXor(FieldObject f, int intValue) {
        return f.intValue ^ intValue;
    }

    public static int testIntXorConstant1(FieldObject f) {
        return f.intValue ^ intTestValue1;
    }

    public static int testIntXorConstant2(FieldObject f) {
        return f.intValue ^ intTestValue2;
    }

    @Test
    public void testIntXors() {
        FieldObject f = new FieldObject();
        test("testIntXor", f, intTestValue1);
        test("testIntXorConstant1", f);
        test("testIntXorConstant2", f);
    }

    @Test
    public void testIntNullXor() {
        test("testIntXor", null, intTestValue1);
    }

    public static long testLongXor(FieldObject f, long longValue) {
        return f.longValue ^ longValue;
    }

    public static long testLongXorConstant1(FieldObject f) {
        return f.longValue ^ longTestValue1;
    }

    public static long testLongXorConstant2(FieldObject f) {
        return f.longValue ^ longTestValue2;
    }

    @Test
    public void testLongXors() {
        FieldObject f = new FieldObject();
        test("testLongXor", f, longTestValue1);
        test("testLongXorConstant1", f);
        test("testLongXorConstant2", f);
    }

    @Test
    public void testLongNullXor() {
        test("testLongXor", null, longTestValue1);
    }

    public static int testByteAnd(FieldObject f, byte byteValue) {
        return f.byteValue & byteValue;
    }

    public static int testByteAndConstant1(FieldObject f) {
        return f.byteValue & byteTestValue1;
    }

    public static int testByteAndConstant2(FieldObject f) {
        return f.byteValue & byteTestValue2;
    }

    @Test
    public void testByteAnds() {
        FieldObject f = new FieldObject();
        test("testByteAnd", f, byteTestValue1);
        test("testByteAndConstant1", f);
        test("testByteAndConstant2", f);
    }

    @Test
    public void testByteNullAnd() {
        test("testByteAnd", null, byteTestValue1);
    }

    public static int testShortAnd(FieldObject f, short shortValue) {
        return f.shortValue & shortValue;
    }

    public static int testShortAndConstant1(FieldObject f) {
        return f.shortValue & shortTestValue1;
    }

    public static int testShortAndConstant2(FieldObject f) {
        return f.shortValue & shortTestValue2;
    }

    @Test
    public void testShortAnds() {
        FieldObject f = new FieldObject();
        test("testShortAnd", f, shortTestValue1);
        test("testShortAndConstant1", f);
        test("testShortAndConstant2", f);
    }

    @Test
    public void testShortNullAnd() {
        test("testShortAnd", null, shortTestValue1);
    }

    public static int testCharAnd(FieldObject f, char charValue) {
        return f.charValue & charValue;
    }

    public static int testCharAndConstant1(FieldObject f) {
        return f.charValue & charTestValue1;
    }

    public static int testCharAndConstant2(FieldObject f) {
        return f.charValue & charTestValue2;
    }

    @Test
    public void testCharAnds() {
        FieldObject f = new FieldObject();
        test("testCharAnd", f, charTestValue1);
        test("testCharAndConstant1", f);
        test("testCharAndConstant2", f);
    }

    @Test
    public void testCharNullAnd() {
        test("testCharAnd", null, charTestValue1);
    }

    public static int testIntAnd(FieldObject f, int intValue) {
        return f.intValue & intValue;
    }

    public static int testIntAndConstant1(FieldObject f) {
        return f.intValue & intTestValue1;
    }

    public static int testIntAndConstant2(FieldObject f) {
        return f.intValue & intTestValue2;
    }

    @Test
    public void testIntAnds() {
        FieldObject f = new FieldObject();
        test("testIntAnd", f, intTestValue1);
        test("testIntAndConstant1", f);
        test("testIntAndConstant2", f);
    }

    @Test
    public void testIntNullAnd() {
        test("testIntAnd", null, intTestValue1);
    }

    public static long testLongAnd(FieldObject f, long longValue) {
        return f.longValue & longValue;
    }

    public static long testLongAndConstant1(FieldObject f) {
        return f.longValue & longTestValue1;
    }

    public static long testLongAndConstant2(FieldObject f) {
        return f.longValue & longTestValue2;
    }

    @Test
    public void testLongAnds() {
        FieldObject f = new FieldObject();
        test("testLongAnd", f, longTestValue1);
        test("testLongAndConstant1", f);
        test("testLongAndConstant2", f);
    }

    @Test
    public void testLongNullAnd() {
        test("testLongAnd", null, longTestValue1);
    }

    public static boolean testIntMask(FieldObject f, int intValue) {
        if ((f.intValue & intValue) != 0) {
            count++;
            return false;
        }
        return true;
    }

    public static boolean testIntMaskConstant1(FieldObject f) {
        return (f.intValue & intTestValue1) != 0;
    }

    public static boolean testIntMaskConstant2(FieldObject f) {
        return (f.intValue & intTestValue2) != 0;
    }

    @Test
    public void testIntMasks() {
        FieldObject f = new FieldObject();
        test("testIntMask", f, intTestValue1);
        test("testIntMaskConstant1", f);
        test("testIntMaskConstant2", f);
    }

    @Test
    public void testIntNullMask() {
        test("testIntMask", null, intTestValue1);
    }

    public static boolean testLongMask(FieldObject f, long longValue) {
        if ((f.longValue & longValue) != 0) {
            count++;
            return false;
        }
        return true;
    }

    public static boolean testLongMaskConstant1(FieldObject f) {
        return (f.longValue & longTestValue1) != 0;
    }

    public static boolean testLongMaskConstant2(FieldObject f) {
        return (f.longValue & longTestValue2) != 0;
    }

    @Test
    public void testLongMasks() {
        FieldObject f = new FieldObject();
        test("testLongMask", f, longTestValue1);
        test("testLongMaskConstant1", f);
        test("testLongMaskConstant2", f);
    }

    @Test
    public void testLongNullMask() {
        test("testLongMask", null, longTestValue1);
    }

    public static int doConvertByteInt(FieldObject f) {
        return f.byteValue;
    }

    @Test
    public void testConvertByteInt() {
        test("doConvertByteInt", maxObject);
        test("doConvertByteInt", (FieldObject) null);
    }

    public static int doConvertShortInt(FieldObject f) {
        return f.shortValue;
    }

    @Test
    public void testConvertShortInt() {
        test("doConvertShortInt", maxObject);
        test("doConvertShortInt", (FieldObject) null);
    }

    public static int doConvertCharInt(FieldObject f) {
        return f.charValue;
    }

    @Test
    public void testConvertCharInt() {
        test("doConvertCharInt", maxObject);
        test("doConvertCharInt", (FieldObject) null);
    }

    public static int doConvertLongInt(FieldObject f) {
        return (int) f.longValue;
    }

    @Test
    public void testConvertLongInt() {
        test("doConvertLongInt", maxObject);
        test("doConvertLongInt", (FieldObject) null);
    }

    public static int doConvertFloatInt(FieldObject f) {
        return (int) f.floatValue;
    }

    @Test
    public void testConvertFloatInt() {
        test("doConvertFloatInt", maxObject);
        test("doConvertFloatInt", (FieldObject) null);
    }

    public static int doConvertDoubleInt(FieldObject f) {
        return (int) f.doubleValue;
    }

    @Test
    public void testConvertDoubleInt() {
        test("doConvertDoubleInt", maxObject);
        test("doConvertDoubleInt", (FieldObject) null);
    }

    public static long doConvertByteLong(FieldObject f) {
        return f.byteValue;
    }

    @Test
    public void testConvertByteLong() {
        test("doConvertByteLong", maxObject);
        test("doConvertByteLong", (FieldObject) null);
    }

    public static long doConvertShortLong(FieldObject f) {
        return f.shortValue;
    }

    @Test
    public void testConvertShortLong() {
        test("doConvertShortLong", maxObject);
        test("doConvertShortLong", (FieldObject) null);
    }

    public static long doConvertCharLong(FieldObject f) {
        return f.charValue;
    }

    @Test
    public void testConvertCharLong() {
        test("doConvertCharLong", maxObject);
        test("doConvertCharLong", (FieldObject) null);
    }

    public static long doConvertIntLong(FieldObject f) {
        return f.intValue;
    }

    @Test
    public void testConvertIntLong() {
        test("doConvertIntLong", maxObject);
        test("doConvertIntLong", (FieldObject) null);
    }

    public static long doConvertFloatLong(FieldObject f) {
        return (long) f.floatValue;
    }

    @Test
    public void testConvertFloatLong() {
        test("doConvertFloatLong", maxObject);
        test("doConvertFloatLong", (FieldObject) null);
    }

    public static long doConvertDoubleLong(FieldObject f) {
        return (long) f.doubleValue;
    }

    @Test
    public void testConvertDoubleLong() {
        test("doConvertDoubleLong", maxObject);
        test("doConvertDoubleLong", (FieldObject) null);
    }

    public static float doConvertByteFloat(FieldObject f) {
        return f.byteValue;
    }

    @Test
    public void testConvertByteFloat() {
        test("doConvertByteFloat", maxObject);
        test("doConvertByteFloat", (FieldObject) null);
    }

    public static float doConvertShortFloat(FieldObject f) {
        return f.shortValue;
    }

    @Test
    public void testConvertShortFloat() {
        test("doConvertShortFloat", maxObject);
        test("doConvertShortFloat", (FieldObject) null);
    }

    public static float doConvertCharFloat(FieldObject f) {
        return f.charValue;
    }

    @Test
    public void testConvertCharFloat() {
        test("doConvertCharFloat", maxObject);
        test("doConvertCharFloat", (FieldObject) null);
    }

    public static float doConvertIntFloat(FieldObject f) {
        return f.intValue;
    }

    @Test
    public void testConvertIntFloat() {
        test("doConvertIntFloat", maxObject);
        test("doConvertIntFloat", (FieldObject) null);
    }

    public static float doConvertLongFloat(FieldObject f) {
        return f.longValue;
    }

    @Test
    public void testConvertLongFloat() {
        test("doConvertLongFloat", maxObject);
        test("doConvertLongFloat", (FieldObject) null);
    }

    public static float doConvertDoubleFloat(FieldObject f) {
        return (float) f.doubleValue;
    }

    @Test
    public void testConvertDoubleFloat() {
        test("doConvertDoubleFloat", maxObject);
        test("doConvertDoubleFloat", (FieldObject) null);
    }

    public static double doConvertByteDouble(FieldObject f) {
        return f.byteValue;
    }

    @Test
    public void testConvertByteDouble() {
        test("doConvertByteDouble", maxObject);
        test("doConvertByteDouble", (FieldObject) null);
    }

    public static double doConvertShortDouble(FieldObject f) {
        return f.shortValue;
    }

    @Test
    public void testConvertShortDouble() {
        test("doConvertShortDouble", maxObject);
        test("doConvertShortDouble", (FieldObject) null);
    }

    public static double doConvertCharDouble(FieldObject f) {
        return f.charValue;
    }

    @Test
    public void testConvertCharDouble() {
        test("doConvertCharDouble", maxObject);
        test("doConvertCharDouble", (FieldObject) null);
    }

    public static double doConvertIntDouble(FieldObject f) {
        return f.intValue;
    }

    @Test
    public void testConvertIntDouble() {
        test("doConvertIntDouble", maxObject);
        test("doConvertIntDouble", (FieldObject) null);
    }

    public static double doConvertLongDouble(FieldObject f) {
        return f.longValue;
    }

    @Test
    public void testConvertLongDouble() {
        test("doConvertLongDouble", maxObject);
        test("doConvertLongDouble", (FieldObject) null);
    }

    public static double doConvertFloatDouble(FieldObject f) {
        return f.floatValue;
    }

    @Test
    public void testConvertFloatDouble() {
        test("doConvertFloatDouble", maxObject);
        test("doConvertFloatDouble", (FieldObject) null);
    }
}

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

/*
 * @test
 * @summary Check the unconditionally exact for constant primitives used in the exhaustiveness check
 * @library /tools/lib/types
 * @modules jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 * @build TypeHarness
 * @compile PrimitiveUnconditionallyExactInExhaustiveSwitches.java
 * @run main PrimitiveUnconditionallyExactInExhaustiveSwitches
 */

public class PrimitiveUnconditionallyExactInExhaustiveSwitches extends TypeHarness {

    PrimitiveUnconditionallyExactInExhaustiveSwitches() {
    }
    public void testByte() {
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MAX_VALUE))), predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (0))),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MIN_VALUE))),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MAX_VALUE))),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (0))),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MIN_VALUE))),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MAX_VALUE))),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MIN_VALUE))),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MAX_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0)),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MIN_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MAX_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0L)),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MIN_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MAX_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((float) 0)),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NaN)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.POSITIVE_INFINITY)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NEGATIVE_INFINITY)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0f)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0f)),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MAX_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((double) 0)),predef.byteType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MIN_VALUE)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NaN)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.POSITIVE_INFINITY)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NEGATIVE_INFINITY)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0d)),predef.byteType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0d)),predef.byteType, true);
    }
    public void testShort() {
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MAX_VALUE))),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (0))),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MIN_VALUE))),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MAX_VALUE))),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (0))),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MIN_VALUE))),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MAX_VALUE))),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MIN_VALUE))),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MAX_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0)),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MIN_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MAX_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0L)),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MIN_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MAX_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((float) 0)),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NaN)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.POSITIVE_INFINITY)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NEGATIVE_INFINITY)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0f)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0f)),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MAX_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((double) 0)),predef.shortType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MIN_VALUE)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NaN)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.POSITIVE_INFINITY)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NEGATIVE_INFINITY)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0d)),predef.shortType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0d)),predef.shortType, true);
    }
    public void testChar() {
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MAX_VALUE))),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (0))),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MIN_VALUE))),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MAX_VALUE))),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (0))),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MIN_VALUE))),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MAX_VALUE))),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MIN_VALUE))),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MAX_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0)),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MIN_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MAX_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0L)),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MIN_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MAX_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((float) 0)),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NaN)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.POSITIVE_INFINITY)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NEGATIVE_INFINITY)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0f)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0f)),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MAX_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((double) 0)),predef.charType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MIN_VALUE)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NaN)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.POSITIVE_INFINITY)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NEGATIVE_INFINITY)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0d)),predef.charType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0d)),predef.charType, true);
    }
    public void testInt() {
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MAX_VALUE))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (0))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MIN_VALUE))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MAX_VALUE))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (0))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MIN_VALUE))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MAX_VALUE))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MIN_VALUE))),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MAX_VALUE)),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0)),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MIN_VALUE)),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MAX_VALUE)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0L)),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MIN_VALUE)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MAX_VALUE)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((float) 0)),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NaN)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.POSITIVE_INFINITY)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NEGATIVE_INFINITY)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0f)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0f)),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MAX_VALUE)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((double) 0)),predef.intType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MIN_VALUE)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NaN)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.POSITIVE_INFINITY)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NEGATIVE_INFINITY)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0d)),predef.intType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0d)),predef.intType, true);
    }
    public void testLong() {
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MAX_VALUE))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (0))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MIN_VALUE))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MAX_VALUE))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (0))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MIN_VALUE))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MAX_VALUE))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MIN_VALUE))),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MAX_VALUE)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0L)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MIN_VALUE)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MAX_VALUE)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MIN_VALUE)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MAX_VALUE)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((float) 0)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NaN)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.POSITIVE_INFINITY)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NEGATIVE_INFINITY)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0f)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0f)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MAX_VALUE)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((double) 0)),predef.longType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MIN_VALUE)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NaN)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.POSITIVE_INFINITY)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NEGATIVE_INFINITY)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0d)),predef.longType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0d)),predef.longType, true);
    }
    public void testFloat() {
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MAX_VALUE))),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((byte) (0)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MIN_VALUE))),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MAX_VALUE))),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (0))),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MIN_VALUE))),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MAX_VALUE))),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MIN_VALUE))),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MAX_VALUE)),predef.floatType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MIN_VALUE)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MAX_VALUE)),predef.floatType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0L)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MIN_VALUE)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MAX_VALUE)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((float) 0)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NaN)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.POSITIVE_INFINITY)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NEGATIVE_INFINITY)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0f)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0f)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MAX_VALUE)),predef.floatType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((double) 0)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MIN_VALUE)),predef.floatType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NaN)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.POSITIVE_INFINITY)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NEGATIVE_INFINITY)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0d)),predef.floatType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0d)),predef.floatType, true);
    }
    public void testDouble() {
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MAX_VALUE))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (0))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((byte) (Byte.MIN_VALUE))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MAX_VALUE))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (0))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((short) (Short.MIN_VALUE))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MAX_VALUE))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((char) (Character.MIN_VALUE))),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MAX_VALUE)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Integer.MIN_VALUE)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MAX_VALUE)),predef.doubleType, false);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((0L)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Long.MIN_VALUE)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MAX_VALUE)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((float) 0)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.MIN_VALUE)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NaN)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.POSITIVE_INFINITY)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Float.NEGATIVE_INFINITY)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0f)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0f)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MAX_VALUE)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant(((double) 0)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.MIN_VALUE)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NaN)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.POSITIVE_INFINITY)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((Double.NEGATIVE_INFINITY)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((-0.0d)),predef.doubleType, true);
        assertIsUnconditionallyExactConstantPrimitives(fac.Constant((+0.0d)),predef.doubleType, true);
    }


    public static void main(String[] args) {
        PrimitiveUnconditionallyExactInExhaustiveSwitches harness = new PrimitiveUnconditionallyExactInExhaustiveSwitches();
        harness.testByte();
        harness.testShort();
        harness.testChar();
        harness.testInt();
        harness.testDouble();
        harness.testLong();
        harness.testFloat();
    }
}

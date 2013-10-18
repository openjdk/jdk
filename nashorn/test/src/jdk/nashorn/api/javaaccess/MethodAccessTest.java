/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.api.javaaccess;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.internal.junit.ArrayAsserts.assertArrayEquals;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @build jdk.nashorn.api.javaaccess.SharedObject jdk.nashorn.api.javaaccess.Person jdk.nashorn.api.javaaccess.MethodAccessTest
 * @run testng/othervm jdk.nashorn.api.javaaccess.MethodAccessTest
 */
public class MethodAccessTest {

    private static ScriptEngine e = null;
    private static SharedObject o = null;

    public static void main(final String[] args) {
        TestNG.main(args);
    }

    @BeforeClass
    public static void setUpClass() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        e = m.getEngineByName("nashorn");
        o = new SharedObject();
        o.setEngine(e);
        e.put("o", o);
        e.eval("var SharedObject = Packages.jdk.nashorn.api.javaaccess.SharedObject;");
        e.eval("var Person = Packages.jdk.nashorn.api.javaaccess.Person;");
    }

    @AfterClass
    public static void tearDownClass() {
        e = null;
        o = null;
    }

    @Test
    public void accessMethodthrowsCheckedException() throws ScriptException {
        e.eval("try {" +
                "    var a = java.lang.Long.parseLong('foo');" +
                "} catch(e) {" +
                "    var isThrown = true;" +
                "    var isNumberException = e instanceof java.lang.NumberFormatException;" +
                "} finally {" +
                "    var isFinalized = true;" +
                "}");
        assertEquals("Exception thrown", true, e.get("isThrown"));
        assertEquals("Finally called", true, e.get("isFinalized"));
        assertEquals("Type is NumberFormatException", true, e.get("isNumberException"));
    }

    @Test
    public void accessMethodthrowsUnCheckedException() throws ScriptException {
        e.eval("try {" +
                "    var a = java.lang.String.valueOf(null);" +
                "} catch(e) {" +
                "    var isThrown = true;" +
                "    var isNumberException = e instanceof java.lang.NullPointerException;" +
                "} finally {" +
                "    var isFinalized = true;" +
                "}");
        assertEquals(true, e.get("isThrown"));
        assertEquals(true, e.get("isFinalized"));
        assertEquals(true, e.get("isNumberException"));
    }

    @Test
    public void accessMethodStartsThread() throws ScriptException {
        e.eval("o.methodStartsThread();");
        assertEquals(false, o.isFinished);
    }

    @Test
    public void accessStaticMethod() throws ScriptException {
        assertEquals(10, e.eval("java.lang.Math.abs(-10);"));
    }

    @Test
    public void accessSynchronousMethod() throws ScriptException {
        e.eval("var v = new java.util.Vector();" + "v.add(10);" + "v.add(20);" + "v.add(30);");
        assertEquals(10, e.eval("v[0]"));
        assertEquals(20, e.eval("v[1]"));
        assertEquals(30, e.eval("v[2]"));
        assertEquals(3, e.eval("v.size()"));
    }

    @Test
    public void accessStaticSynchronousMethod() throws ScriptException {
        e.eval("var locales = java.util.Calendar.getAvailableLocales();");
        final Locale[] locales = (Locale[])e.get("locales");
        assertEquals(locales.length, Calendar.getAvailableLocales().length);
    }

    @Test
    public void accessNativeMethod() throws ScriptException {
        assertEquals(4.0, e.eval("java.lang.StrictMath.log10(10000);"));
    }

    @Test
    public void accessConstructorOfAbstractClass() throws ScriptException {
        e.eval("try {" +
                "    var a = new java.util.AbstractList();" +
                "    print('fail');" +
                "} catch(e) {" +
                "    var isThrown = true;" +
                "}");
        assertEquals(true, e.get("isThrown"));
    }

    @Test
    public void accessMethodVoid() throws ScriptException {
        o.isAccessed = false;
        e.eval("o.voidMethod();");
        assertTrue(o.isAccessed);
    }

    @Test
    public void accessMethodBoolean() throws ScriptException {
        assertEquals(true, e.eval("o.booleanMethod(false);"));
        assertEquals(false, e.eval("o.booleanMethod(true);"));
        assertEquals(false, e.eval("o.booleanMethod('false');"));
        assertEquals(true, e.eval("o.booleanMethod('');"));
        assertEquals(true, e.eval("o.booleanMethod(0);"));
    }

    @Test
    public void accessMethodInt() throws ScriptException {
        assertEquals(0, e.eval("o.intMethod(0);"));
        assertEquals(-200, e.eval("o.intMethod(-100);"));
        assertEquals(0, e.eval("o.intMethod('0');"));
        assertEquals(-200, e.eval("o.intMethod('-100');"));
    }

    @Test
    public void accessMethodLong() throws ScriptException {
        assertEquals((long)0, e.eval("o.longMethod(0);"));
        assertEquals((long)400, e.eval("o.longMethod(200);"));
        assertEquals((long) 0, e.eval("o.longMethod('0');"));
        assertEquals((long) 400, e.eval("o.longMethod('200');"));
    }

    @Test
    public void accessMethodByte() throws ScriptException {
        assertEquals((byte) 0, e.eval("o.byteMethod(0);"));
        assertEquals((byte) 10, e.eval("o.byteMethod(5);"));
        assertEquals((byte) 0, e.eval("o.byteMethod('0');"));
        assertEquals((byte) 10, e.eval("o.byteMethod('5');"));
    }

    @Test
    public void accessMethodShort() throws ScriptException {
        assertEquals((short)0, e.eval("o.shortMethod(0);"));
        assertEquals((short)8000, e.eval("o.shortMethod(4000);"));
        assertEquals((short) 0, e.eval("o.shortMethod('0');"));
        assertEquals((short) 8000, e.eval("o.shortMethod('4000');"));
    }

    @Test
    public void accessMethodChar() throws ScriptException {
        assertEquals('A', e.eval("o.charMethod('a');"));
        assertEquals('Z', e.eval("o.charMethod('z');"));
        assertEquals(o.charMethod((char)0), e.eval("o.charMethod(0);"));
        assertEquals(o.charMethod((char)3150), e.eval("o.charMethod(3150);"));
    }

    @Test
    public void accessMethodFloat() throws ScriptException {
        assertEquals(0.0f, e.eval("o.floatMethod(0.0);"));
        assertEquals(4.2f, e.eval("o.floatMethod(2.1);"));
        assertEquals(0.0f, e.eval("o.floatMethod('0.0');"));
        assertEquals(4.2f, e.eval("o.floatMethod('2.1');"));
    }

    @Test
    public void accessMethodDouble() throws ScriptException {
        assertEquals(0.0, e.eval("o.doubleMethod(0.0);"));
        assertEquals(14.0, e.eval("o.doubleMethod(7.0);"));
        assertEquals(0.0, e.eval("o.doubleMethod('0.0');"));
        assertEquals(14.0, e.eval("o.doubleMethod('7.0');"));
    }

    @Test
    public void accessMethodBooleanBoxing() throws ScriptException {
        assertEquals(Boolean.TRUE, e.eval("o.booleanBoxingMethod(java.lang.Boolean.FALSE);"));
        assertEquals(Boolean.FALSE, e.eval("o.booleanBoxingMethod(java.lang.Boolean.TRUE);"));
        assertEquals(Boolean.TRUE, e.eval("o.booleanBoxingMethod('');"));
        assertEquals(Boolean.FALSE, e.eval("o.booleanBoxingMethod('false');"));
    }

    @Test
    public void accessMethodIntBoxing() throws ScriptException {
        assertEquals(0, e.eval("o.intBoxingMethod(0);"));
        assertEquals(-200, e.eval("o.intBoxingMethod(-100);"));
        assertTrue((int)e.eval("(new java.lang.Integer(2)).compareTo(10.0)") < 0);
    }

    @Test
    public void accessMethodLongBoxing() throws ScriptException {
        assertEquals((long) 0, e.eval("o.longBoxingMethod(0);"));
        assertEquals((long) 400, e.eval("o.longBoxingMethod(200);"));
        assertTrue((int)e.eval("(new java.lang.Long(2)).compareTo(10.0)") < 0);
    }

    @Test
    public void accessMethodByteBoxing() throws ScriptException {
        assertEquals((byte) 0, e.eval("o.byteBoxingMethod(0);"));
        assertEquals((byte) 10, e.eval("o.byteBoxingMethod(5);"));
        assertTrue((int)e.eval("(new java.lang.Byte(2)).compareTo(10.0)") < 0);
    }

    @Test
    public void accessMethodShortBoxing() throws ScriptException {
        assertEquals((short) 0, e.eval("o.shortBoxingMethod(0);"));
        assertEquals((short) 8000, e.eval("o.shortBoxingMethod(4000);"));
        assertTrue((int)e.eval("(new java.lang.Short(2)).compareTo(10.0)") < 0);
    }

    @Test
    public void accessMethodCharBoxing() throws ScriptException {
        assertEquals('A', e.eval("o.charBoxingMethod('a');"));
        assertEquals('Z', e.eval("o.charBoxingMethod('z');"));
        assertTrue((int)e.eval("(new java.lang.Character(2)).compareTo(10)") < 0);
    }

    @Test
    public void accessMethodFloatBoxing() throws ScriptException {
        assertEquals(0.0f, e.eval("o.floatBoxingMethod(0.0);"));
        assertEquals(4.2f, e.eval("o.floatBoxingMethod(2.1);"));
        assertTrue((int)e.eval("(new java.lang.Float(2.0)).compareTo(10.0)") < 0);
    }

    @Test
    public void accessMethodDoubleBoxing() throws ScriptException {
        assertEquals(0.0, e.eval("o.doubleBoxingMethod(0.0);"));
        assertEquals(14.0, e.eval("o.doubleBoxingMethod(7.0);"));
        assertTrue((int)e.eval("(new java.lang.Double(2)).compareTo(10.0)") < 0);
    }

    @Test
    public void accessMethodString() throws ScriptException {
        assertEquals("", e.eval("o.stringMethod('');"));
        assertEquals("abcabc", e.eval("o.stringMethod('abc');"));
    }

    @Test
    public void accessMethodObject() throws ScriptException {
        e.put("so", new Person(5));
        e.eval("var rso = o.objectMethod(so);");
        assertEquals(new Person(10), e.get("rso"));
    }

    @Test
    public void accessMethodBooleanArray() throws ScriptException {
        assertTrue(Arrays.equals(o.booleanArrayMethod(o.publicBooleanArray), (boolean[])e.eval("o.booleanArrayMethod(o.publicBooleanArray);")));
    }

    @Test
    public void accessMethodIntArray() throws ScriptException {
        assertArrayEquals(o.intArrayMethod(o.publicIntArray), (int[])e.eval("o.intArrayMethod(o.publicIntArray);"));
    }

    @Test
    public void accessMethodLongArray() throws ScriptException {
        assertArrayEquals(o.longArrayMethod(o.publicLongArray), (long[])e.eval("o.longArrayMethod(o.publicLongArray);"));
    }

    @Test
    public void accessMethodByteArray() throws ScriptException {
        assertArrayEquals(o.byteArrayMethod(o.publicByteArray), (byte[])e.eval("o.byteArrayMethod(o.publicByteArray);"));
    }

    @Test
    public void accessMethodShortArray() throws ScriptException {
        assertArrayEquals(o.shortArrayMethod(o.publicShortArray), (short[])e.eval("o.shortArrayMethod(o.publicShortArray);"));
    }

    @Test
    public void accessMethodCharArray() throws ScriptException {
        assertArrayEquals(o.charArrayMethod(o.publicCharArray), (char[])e.eval("o.charArrayMethod(o.publicCharArray);"));
    }

    @Test
    public void accessMethodFloatArray() throws ScriptException {
        assertArrayEquals(o.floatArrayMethod(o.publicFloatArray), (float[])e.eval("o.floatArrayMethod(o.publicFloatArray);"), 1e-10f);
    }

    @Test
    public void accessMethodDoubleArray() throws ScriptException {
        assertArrayEquals(o.doubleArrayMethod(o.publicDoubleArray), (double[])e.eval("o.doubleArrayMethod(o.publicDoubleArray);"), 1e-10);
    }

    @Test
    public void accessMethodStringArray() throws ScriptException {
        assertArrayEquals(o.stringArrayMethod(o.publicStringArray), (String[])e.eval("o.stringArrayMethod(o.publicStringArray);"));
    }

    @Test
    public void accessMethodObjectArray() throws ScriptException {
        assertArrayEquals(o.objectArrayMethod(o.publicObjectArray), (Person[])e.eval("o.objectArrayMethod(o.publicObjectArray);"));
    }

    @Test
    public void accessDefaultConstructor() throws ScriptException {
        e.eval("var dc = new Packages.jdk.nashorn.api.javaaccess.Person()");
        assertEquals(new Person(), e.get("dc"));
    }

    @Test
    public void accessCustomConstructor() throws ScriptException {
        e.eval("var cc = new Packages.jdk.nashorn.api.javaaccess.Person(17)");
        assertEquals(new Person(17), e.get("cc"));
    }

    @Test
    public void accessMethod2PrimitiveParams() throws ScriptException {
        assertEquals(o.twoParamMethod(50, 40.0), e.eval("o.twoParamMethod(50,40);"));
    }

    @Test
    public void accessMethod3PrimitiveParams() throws ScriptException {
        assertEquals(o.threeParamMethod((short)10, 20L, 'b'), e.eval("o.threeParamMethod(10,20,'b');"));
    }

    @Test
    public void accessMethod2ObjectParams() throws ScriptException {
        assertArrayEquals(new Person[] { new Person(200), new Person(300) }, (Person[])e.eval("o.twoObjectParamMethod(new Person(300),new Person(200));"));
    }

    @Test
    public void accessMethod3ObjectParams() throws ScriptException {
        assertArrayEquals(new Person[] { new Person(3), new Person(2), new Person(1) }, (Person[])e.eval("o.threeObjectParamMethod(new Person(1),new Person(2),new Person(3));"));
    }

    @Test
    public void accessMethod8ObjectParams() throws ScriptException {
        assertArrayEquals(new Person[] { new Person(8), new Person(7), new Person(6), new Person(5), new Person(4), new Person(3), new Person(2), new Person(1) }, (Person[])e.eval("o.eightObjectParamMethod(new Person(1),new Person(2),new Person(3)," + "new Person(4),new Person(5),new Person(6),new Person(7),new Person(8));"));
    }

    @Test
    public void accessMethod9ObjectParams() throws ScriptException {
        assertArrayEquals(new Person[] { new Person(9), new Person(8), new Person(7), new Person(6), new Person(5), new Person(4), new Person(3), new Person(2), new Person(1) }, (Person[])e.eval("o.nineObjectParamMethod(new Person(1),new Person(2),new Person(3)," + "new Person(4),new Person(5),new Person(6)," + "new Person(7),new Person(8),new Person(9));"));
    }

    @Test
    public void accessMethodObjectEllipsis() throws ScriptException {
        assertArrayEquals(new Person[] { new Person(9), new Person(8), new Person(7), new Person(6), new Person(5), new Person(4), new Person(3), new Person(2), new Person(1) }, (Person[])e.eval("o.methodObjectEllipsis(new Person(1),new Person(2),new Person(3)," + "new Person(4),new Person(5),new Person(6)," + "new Person(7),new Person(8),new Person(9));"));
        assertArrayEquals(new Person[] {}, (Person[])e.eval("o.methodObjectEllipsis()"));
        assertArrayEquals(new Person[] { new Person(9) }, (Person[])e.eval("o.methodObjectEllipsis(new Person(9))"));
    }

    @Test
    public void accessMethodPrimitiveEllipsis() throws ScriptException {
        assertArrayEquals(new Person[] { new Person(1), new Person(3), new Person(2) }, (Person[])e.eval("o.methodPrimitiveEllipsis(1,3,2);"));
        assertArrayEquals(new Person[] {}, (Person[])e.eval("o.methodPrimitiveEllipsis();"));
        assertArrayEquals(o.methodPrimitiveEllipsis(9, 8, 7, 6, 5, 4, 3, 2, 1), (Person[])e.eval("o.methodPrimitiveEllipsis(9,8,7,6,5,4,3,2,1);"));
    }

    @Test
    public void accessMethodMixedEllipsis() throws ScriptException {
        assertArrayEquals(new Object[] { new Person(1), 12, "hello", true }, (Object[])e.eval("o.methodMixedEllipsis(new Person(1),12,'hello',true);"));
        assertArrayEquals(new Object[] {}, (Object[])e.eval("o.methodMixedEllipsis();"));
    }

    @Test
    public void accessMethodObjectWithEllipsis() throws ScriptException {
        assertArrayEquals(new Object[] { "hello", 12, 15, 16 }, (Object[])e.eval("o.methodObjectWithEllipsis('hello',12,15,16);"));
        assertArrayEquals(new Object[] { "hello" }, (Object[])e.eval("o.methodObjectWithEllipsis('hello');"));
    }

    @Test
    public void accessMethodPrimitiveWithEllipsis() throws ScriptException {
        assertArrayEquals(new Object[] { 14, 12L, 15L, 16L }, (Object[])e.eval("o.methodPrimitiveWithEllipsis(14,12,15,16);"));
        assertArrayEquals(new Object[] { 12 }, (Object[])e.eval("o.methodPrimitiveWithEllipsis(12);"));
    }

    @Test
    public void accessMethodMixedWithEllipsis() throws ScriptException {
        assertArrayEquals(new Object[] { "Hello", 10, true, -100500, 80d }, (Object[])e.eval("o.methodMixedWithEllipsis('Hello', 10, true, -100500,80.0);"));
        assertArrayEquals(new Object[] { "Nashorn", 15 }, (Object[])e.eval("o.methodMixedWithEllipsis('Nashorn',15);"));
    }

    @Test
    public void accessMethodOverloaded() throws ScriptException {
        assertEquals(0, e.eval("o.overloadedMethod(0);"));
        assertEquals(2000, e.eval("o.overloadedMethod(1000);"));
        assertEquals(2, e.eval("o.overloadedMethod('10');"));
        assertEquals(7, e.eval("o.overloadedMethod('Nashorn');"));
        assertEquals(4, e.eval("o.overloadedMethod('true');"));
        assertEquals(1, e.eval("o.overloadedMethod(true);"));
        assertEquals(0, e.eval("o.overloadedMethod(false);"));
        assertEquals(44, e.eval("o.overloadedMethod(new Person(22));"));
        assertEquals(0, e.eval("o.overloadedMethod(new Person());"));
    }

    @Test
    public void accessMethodDoubleVSintOverloaded() throws ScriptException {
        assertEquals("double", e.eval("o.overloadedMethodDoubleVSint(0.0);"));
        assertEquals("double", e.eval("o.overloadedMethodDoubleVSint(1000.0);"));
        assertEquals("double", e.eval("o.overloadedMethodDoubleVSint(0.01);"));
        assertEquals("double", e.eval("o.overloadedMethodDoubleVSint(100.02);"));
        assertEquals("int", e.eval("o.overloadedMethodDoubleVSint(0);"));
        assertEquals("int", e.eval("o.overloadedMethodDoubleVSint(1000);"));
    }

    @Test
    public void accessJavaMethodIntFromJSFromJavaFromJS() throws ScriptException {
        e.eval("function secondLevelMethodInt(a) {"
                + "return o.thirdLevelMethodInt(a);"
                + "}");
        assertEquals(50, e.eval("o.firstLevelMethodInt(10);"));
    }

    @Test
    public void accessJavaMethodIntegerFromJSFromJavaFromJS() throws ScriptException {
        e.eval("function secondLevelMethodInteger(a) {"
                + "return o.thirdLevelMethodInteger(a);"
                + "}");
        assertEquals(100, e.eval("o.firstLevelMethodInteger(10);"));
    }

    @Test
    public void accessJavaMethodObjectFromJSFromJavaFromJS() throws ScriptException {
        e.eval("function secondLevelMethodObject(p) {"
                + "return o.thirdLevelMethodObject(p);"
                + "}");
        assertEquals(new Person(100), e.eval("o.firstLevelMethodObject(new Person(10));"));
    }

}

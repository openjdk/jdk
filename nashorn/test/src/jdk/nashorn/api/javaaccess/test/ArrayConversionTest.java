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

package jdk.nashorn.api.javaaccess.test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import java.util.Arrays;
import java.util.List;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ArrayConversionTest {
    private static ScriptEngine e = null;

    public static void main(final String[] args) {
        TestNG.main(args);
    }

    @BeforeClass
    public static void setUpClass() {
        e = new ScriptEngineManager().getEngineByName("nashorn");
    }

    @AfterClass
    public static void tearDownClass() {
        e = null;
    }

    @Test
    public void testIntArrays() throws ScriptException {
        runTest("assertNullIntArray", "null");
        runTest("assertEmptyIntArray", "[]");
        runTest("assertSingle42IntArray", "[42]");
        runTest("assertSingle42IntArray", "['42']");
        runTest("assertIntArrayConversions", "[false, true, NaN, Infinity, -Infinity, 0.4, 0.6, null, undefined, [], {}, [1], [1, 2]]");
    }

    @Test
    public void testIntIntArrays() throws ScriptException {
        runTest("assertNullIntIntArray", "null");
        runTest("assertEmptyIntIntArray", "[]");
        runTest("assertSingleEmptyIntIntArray", "[[]]");
        runTest("assertSingleNullIntIntArray", "[null]");
        runTest("assertLargeIntIntArray", "[[false], [1], [2, 3], [4, 5, 6], ['7', {valueOf: function() { return 8 }}]]");
    }

    @Test
    public void testObjectObjectArrays() throws ScriptException {
        runTest("assertLargeObjectObjectArray", "[[false], [1], ['foo', 42.3], [{x: 17}]]");
    }

    @Test
    public void testBooleanArrays() throws ScriptException {
        runTest("assertBooleanArrayConversions", "[false, true, '', 'false', 0, 1, 0.4, 0.6, {}, [], [false], [true], NaN, Infinity, null, undefined]");
    }

    @Test
    public void testArrayAmbiguity() throws ScriptException {
        runTest("x", "'abc'");
        runTest("x", "['foo', 'bar']");
    }

    @Test
    public void testListArrays() throws ScriptException {
        runTest("assertListArray", "[['foo', 'bar'], ['apple', 'orange']]");
    }

    @Test
    public void testVarArgs() throws ScriptException {
        // Sole NativeArray in vararg position becomes vararg array itself
        runTest("assertVarArg_42_17", "[42, 17]");
        // NativeArray in vararg position becomes an argument if there are more arguments
        runTest("assertVarArg_array_17", "[42], 18");
        // Only NativeArray is converted to vararg array, other objects (e.g. a function) aren't
        runTest("assertVarArg_function", "function() { return 'Hello' }");
    }

    private static void runTest(final String testMethodName, final String argument) throws ScriptException {
        e.eval("Java.type('" + ArrayConversionTest.class.getName() + "')." + testMethodName + "(" + argument + ")");
    }

    public static void assertNullIntArray(final int[] array) {
        assertNull(array);
    }

    public static void assertNullIntIntArray(final int[][] array) {
        assertNull(array);
    }

    public static void assertEmptyIntArray(final int[] array) {
        assertEquals(0, array.length);
    }

    public static void assertSingle42IntArray(final int[] array) {
        assertEquals(1, array.length);
        assertEquals(42, array[0]);
    }


    public static void assertIntArrayConversions(final int[] array) {
        assertEquals(13, array.length);
        assertEquals(0, array[0]); // false
        assertEquals(1, array[1]); // true
        assertEquals(0, array[2]); // NaN
        assertEquals(0, array[3]); // Infinity
        assertEquals(0, array[4]); // -Infinity
        assertEquals(0, array[5]); // 0.4
        assertEquals(0, array[6]); // 0.6 - floor, not round
        assertEquals(0, array[7]); // null
        assertEquals(0, array[8]); // undefined
        assertEquals(0, array[9]); // []
        assertEquals(0, array[10]); // {}
        assertEquals(1, array[11]); // [1]
        assertEquals(0, array[12]); // [1, 2]
    }

    public static void assertEmptyIntIntArray(final int[][] array) {
        assertEquals(0, array.length);
    }

    public static void assertSingleEmptyIntIntArray(final int[][] array) {
        assertEquals(1, array.length);
        assertTrue(Arrays.equals(new int[0], array[0]));
    }

    public static void assertSingleNullIntIntArray(final int[][] array) {
        assertEquals(1, array.length);
        assertNull(null, array[0]);
    }

    public static void assertLargeIntIntArray(final int[][] array) {
        assertEquals(5, array.length);
        assertTrue(Arrays.equals(new int[] { 0 }, array[0]));
        assertTrue(Arrays.equals(new int[] { 1 }, array[1]));
        assertTrue(Arrays.equals(new int[] { 2, 3 }, array[2]));
        assertTrue(Arrays.equals(new int[] { 4, 5, 6 }, array[3]));
        assertTrue(Arrays.equals(new int[] { 7, 8 }, array[4]));
    }

    public static void assertLargeObjectObjectArray(final Object[][] array) throws ScriptException {
        assertEquals(4, array.length);
        assertTrue(Arrays.equals(new Object[] { Boolean.FALSE }, array[0]));
        assertTrue(Arrays.equals(new Object[] { 1 }, array[1]));
        assertTrue(Arrays.equals(new Object[] { "foo", 42.3d }, array[2]));
        assertEquals(1, array[3].length);
        e.getBindings(ScriptContext.ENGINE_SCOPE).put("obj", array[3][0]);
        assertEquals(17, e.eval("obj.x"));
    }

    public static void assertBooleanArrayConversions(final boolean[] array) {
        assertEquals(16, array.length);
        assertFalse(array[0]); // false
        assertTrue(array[1]); // true
        assertFalse(array[2]); // ''
        assertTrue(array[3]); // 'false' (yep, every non-empty string converts to true)
        assertFalse(array[4]); // 0
        assertTrue(array[5]); // 1
        assertTrue(array[6]); // 0.4
        assertTrue(array[7]); // 0.6
        assertTrue(array[8]); // {}
        assertTrue(array[9]); // []
        assertTrue(array[10]); // [false]
        assertTrue(array[11]); // [true]
        assertFalse(array[12]); // NaN
        assertTrue(array[13]); // Infinity
        assertFalse(array[14]); // null
        assertFalse(array[15]); // undefined
    }

    public static void assertListArray(final List<?>[] array) {
        assertEquals(2, array.length);
        assertEquals(Arrays.asList("foo", "bar"), array[0]);
        assertEquals(Arrays.asList("apple", "orange"), array[1]);
    }

    public static void assertVarArg_42_17(final Object... args) {
        assertEquals(2, args.length);
        assertEquals(42, ((Number)args[0]).intValue());
        assertEquals(17, ((Number)args[1]).intValue());
    }

    public static void assertVarArg_array_17(final Object... args) throws ScriptException {
        assertEquals(2, args.length);
        e.getBindings(ScriptContext.ENGINE_SCOPE).put("arr", args[0]);
        assertTrue((Boolean)e.eval("arr instanceof Array && arr.length == 1 && arr[0] == 42"));
        assertEquals(18, ((Number)args[1]).intValue());
    }

    public static void assertVarArg_function(final Object... args) throws ScriptException {
        assertEquals(1, args.length);
        e.getBindings(ScriptContext.ENGINE_SCOPE).put("fn", args[0]);
        assertEquals("Hello", e.eval("fn()"));
    }



    public static void x(final String y) {
        assertEquals("abc", y);
    }
    public static void x(final String[] y) {
        assertTrue(Arrays.equals(new String[] { "foo", "bar"}, y));
    }
}

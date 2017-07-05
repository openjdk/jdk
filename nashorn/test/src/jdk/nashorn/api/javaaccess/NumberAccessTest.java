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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.TestNG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @build jdk.nashorn.api.javaaccess.SharedObject jdk.nashorn.api.javaaccess.Person jdk.nashorn.api.javaaccess.NumberAccessTest
 * @run testng/othervm jdk.nashorn.api.javaaccess.NumberAccessTest
 */
public class NumberAccessTest {

    private static ScriptEngine e = null;
    private static SharedObject o = new SharedObject();

    public static void main(final String[] args) {
        TestNG.main(args);
    }

    @BeforeClass
    public static void setUpClass() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        e = m.getEngineByName("nashorn");
        e.put("o", o);
        e.eval("var SharedObject = Packages.jdk.nashorn.api.javaaccess.SharedObject;");
    }

    // --------------------------------long
    // tests------------------------------------
    @Test
    public void accessFieldLong() throws ScriptException {
        e.eval("var p_long = o.publicLong;");
        assertEquals(o.publicLong, e.get("p_long"));
        e.eval("o.publicLong = 12;");
        assertEquals(12, o.publicLong);
    }

    @Test
    public void accessFieldLongArray() throws ScriptException {
        e.eval("var p_long_array = o.publicLongArray;");
        assertEquals(o.publicLongArray[0], e.eval("o.publicLongArray[0];"));
        assertArrayEquals(o.publicLongArray, (long[])e.get("p_long_array"));
        e.eval("var t_long_arr = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, 3);" +
                "t_long_arr[0] = -189009;" +
                "t_long_arr[1] = 456;" +
                "t_long_arr[2] = 600000001;" +
                "o.publicLongArray = t_long_arr;");
        // e.eval("o.publicIntArray = [-189009,456,600000001];");
        assertArrayEquals(new long[] { -189009, 456, 600000001 }, o.publicLongArray);
        e.eval("o.publicLongArray[0] = 10;");
        assertEquals(10, o.publicLongArray[0]);
    }

    @Test
    public void accessStaticFieldLong() throws ScriptException {
        e.eval("var ps_long = SharedObject.publicStaticLong;");
        assertEquals(SharedObject.publicStaticLong, e.get("ps_long"));
        e.eval("SharedObject.publicStaticLong = 120;");
        assertEquals(120, SharedObject.publicStaticLong);
    }

    @Test
    public void accessStaticFieldLongArray() throws ScriptException {
        e.eval("var ps_long_array = SharedObject.publicStaticLongArray;");
        assertEquals(SharedObject.publicStaticLongArray[0], e.eval("SharedObject.publicStaticLongArray[0];"));
        assertArrayEquals(SharedObject.publicStaticLongArray, (long[])e.get("ps_long_array"));
        e.eval("var ts_long_arr = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, 3);" +
                "ts_long_arr[0] = -189009;" +
                "ts_long_arr[1] = 456;" +
                "ts_long_arr[2] = 600000001;" +
                "SharedObject.publicStaticLongArray = ts_long_arr;");
        // e.eval("o.publicIntArray = [-189009,456,600000001];");
        assertArrayEquals(new long[] { -189009, 456, 600000001 }, SharedObject.publicStaticLongArray);
        e.eval("SharedObject.publicStaticLongArray[0] = 10;");
        assertEquals(10, SharedObject.publicStaticLongArray[0]);
    }

    @Test
    public void accessFinalFieldLong() throws ScriptException {
        e.eval("var pf_long = o.publicFinalLong;");
        assertEquals(o.publicFinalLong, e.get("pf_long"));
        e.eval("o.publicFinalLong = 120;");
        assertEquals(13353333333333333L, o.publicFinalLong);
    }

    @Test
    public void accessFinalFieldLongArray() throws ScriptException {
        e.eval("var pf_long_array = o.publicFinalLongArray;");
        assertEquals(o.publicFinalLongArray[0], e.eval("o.publicFinalLongArray[0];"));
        assertArrayEquals(o.publicFinalLongArray, (long[])e.get("pf_long_array"));
        e.eval("var tf_long_arr = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, 3);" +
                "tf_long_arr[0] = -189009;" +
                "tf_long_arr[1] = 456;" +
                "tf_long_arr[2] = 600000001;" +
                "o.publicFinalLongArray = tf_long_arr;");
        // e.eval("o.publicIntArray = [-189009,456,600000001];");
        assertArrayEquals(new long[] { 1901733333333L, -2247355555L, 3977377777L }, o.publicFinalLongArray);
        e.eval("o.publicFinalLongArray[0] = 10;");
        assertEquals(10, o.publicFinalLongArray[0]);
    }

    @Test
    public void accessStaticFinalFieldLong() throws ScriptException {
        e.eval("var psf_long = SharedObject.publicStaticFinalLong;");
        assertEquals(SharedObject.publicStaticFinalLong, e.get("psf_long"));
        e.eval("SharedObject.publicStaticFinalLong = 120;");
        assertEquals(8333333333333L, SharedObject.publicStaticFinalLong);
    }

    @Test
    public void accessStaticFinalFieldLongArray() throws ScriptException {
        e.eval("var psf_long_array = SharedObject.publicStaticFinalLongArray;");
        assertEquals(SharedObject.publicStaticFinalLongArray[0], e.eval("SharedObject.publicStaticFinalLongArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFinalLongArray, (long[])e.get("psf_long_array"));
        e.eval("var tsf_long_arr = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, 3);" +
                "tsf_long_arr[0] = -189009;" +
                "tsf_long_arr[1] = 456;" +
                "tsf_long_arr[2] = 600000001;" +
                "SharedObject.publicStaticFinalLongArray = tsf_long_arr;");
        // e.eval("o.publicIntArray = [-189009,456,600000001];");
        assertArrayEquals(new long[] { 19017383333L, -2247358L, 39773787L }, SharedObject.publicStaticFinalLongArray);
        e.eval("SharedObject.publicStaticFinalLongArray[0] = 10;");
        assertEquals(10, SharedObject.publicStaticFinalLongArray[0]);
    }

    // --------------------------------int
    // tests------------------------------------
    @Test
    public void accessFieldInt() throws ScriptException {
        e.eval("var p_int = o.publicInt;");
        assertEquals(o.publicInt, e.get("p_int"));
        e.eval("o.publicInt = 14;");
        assertEquals(14, o.publicInt);
    }

    @Test
    public void accessFieldIntArray() throws ScriptException {
        e.eval("var p_int_array = o.publicIntArray;");
        assertEquals(o.publicIntArray[0], e.eval("o.publicIntArray[0];"));
        assertArrayEquals(o.publicIntArray, (int[])e.get("p_int_array"));
        e.eval("var t_int_arr = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 3);" +
                "t_int_arr[0] = 4;" +
                "t_int_arr[1] = 5;" +
                "t_int_arr[2] = 6;" +
                "o.publicIntArray = t_int_arr;");
        assertArrayEquals(new int[] { 4, 5, 6 }, o.publicIntArray);
        e.eval("o.publicIntArray[0] = 100;");
        assertEquals(100, o.publicIntArray[0]);
    }

    @Test
    public void accessStaticFieldInt() throws ScriptException {
        e.eval("var ps_int = SharedObject.publicStaticInt;");
        assertEquals(SharedObject.publicStaticInt, e.get("ps_int"));
        e.eval("SharedObject.publicStaticInt = 140;");
        assertEquals(140, SharedObject.publicStaticInt);
    }

    @Test
    public void accessStaticFieldIntArray() throws ScriptException {
        e.eval("var ps_int_array = SharedObject.publicStaticIntArray;");
        assertEquals(SharedObject.publicStaticIntArray[0], e.eval("SharedObject.publicStaticIntArray[0];"));
        assertArrayEquals(SharedObject.publicStaticIntArray, (int[])e.get("ps_int_array"));
        e.eval("var ts_int_arr = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 3);" +
                "ts_int_arr[0] = 4;" +
                "ts_int_arr[1] = 5;" +
                "ts_int_arr[2] = 6;" +
                "SharedObject.publicStaticIntArray = ts_int_arr;");
        assertArrayEquals(new int[] { 4, 5, 6 }, SharedObject.publicStaticIntArray);
        e.eval("SharedObject.publicStaticIntArray[0] = 100;");
        assertEquals(100, SharedObject.publicStaticIntArray[0]);
    }

    @Test
    public void accessFinalFieldInt() throws ScriptException {
        e.eval("var pf_int = o.publicFinalInt;");
        assertEquals(o.publicFinalInt, e.get("pf_int"));

        e.eval("o.publicFinalInt = 10;");
        assertEquals(20712023, o.publicFinalInt);
    }

    @Test
    public void accessFinalFieldIntArray() throws ScriptException {
        e.eval("var pf_int_array = o.publicFinalIntArray;");
        assertEquals(o.publicFinalIntArray[0], e.eval("o.publicFinalIntArray[0];"));
        assertArrayEquals(o.publicFinalIntArray, (int[])e.get("pf_int_array"));
        e.eval("var tf_int_arr = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 3);" +
                "tf_int_arr[0] = 4;" +
                "tf_int_arr[1] = 5;" +
                "tf_int_arr[2] = 6;" +
                "o.publicFinalIntArray = tf_int_arr;");
        assertArrayEquals(new int[] { 50, 80, 130, 210, 340 }, o.publicFinalIntArray);
        e.eval("o.publicFinalIntArray[0] = 100;");
        assertEquals(100, o.publicFinalIntArray[0]);
    }

    @Test
    public void accessStaticFinalFieldInt() throws ScriptException {
        e.eval("var psf_int = SharedObject.publicStaticFinalInt;");
        assertEquals(SharedObject.publicStaticFinalInt, e.get("psf_int"));
        e.eval("SharedObject.publicStaticFinalInt = 140;");
        assertEquals(207182023, SharedObject.publicStaticFinalInt);
    }

    @Test
    public void accessStaticFinalFieldIntArray() throws ScriptException {
        e.eval("var psf_int_array = SharedObject.publicStaticFinalIntArray;");
        assertEquals(SharedObject.publicStaticFinalIntArray[0], e.eval("SharedObject.publicStaticFinalIntArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFinalIntArray, (int[])e.get("psf_int_array"));
        e.eval("var tsf_int_arr = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 3);" +
                "tsf_int_arr[0] = 4;" +
                "tsf_int_arr[1] = 5;" +
                "tsf_int_arr[2] = 6;" +
                "SharedObject.publicStaticFinalIntArray = tsf_int_arr;");
        assertArrayEquals(new int[] { 1308, 210, 340 }, SharedObject.publicStaticFinalIntArray);
        e.eval("SharedObject.publicStaticFinalIntArray[0] = 100;");
        assertEquals(100, SharedObject.publicStaticFinalIntArray[0]);
    }

    // --------------------------------byte
    // tests------------------------------------
    @Test
    public void accessFieldByte() throws ScriptException {
        e.eval("var p_byte = o.publicByte;");
        assertEquals(o.publicByte, e.get("p_byte"));
        e.eval("o.publicByte = 16;");
        assertEquals(16, o.publicByte);
    }

    @Test
    public void accessFieldByteArray() throws ScriptException {
        e.eval("var p_byte_array = o.publicByteArray;");
        assertEquals(o.publicByteArray[0], e.eval("o.publicByteArray[0];"));
        assertArrayEquals(o.publicByteArray, (byte[])e.get("p_byte_array"));
        e.eval("var t_byte_arr = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 3);" +
                "t_byte_arr[0] = -18;" +
                "t_byte_arr[1] = 56;" +
                "t_byte_arr[2] = 60;" +
                "o.publicByteArray = t_byte_arr;");
        assertArrayEquals(new byte[] { -18, 56, 60 }, o.publicByteArray);
        e.eval("o.publicByteArray[0] = 100;");
        assertEquals(100, o.publicByteArray[0]);
    }

    @Test
    public void accessStaticFieldByte() throws ScriptException {
        e.eval("var ps_byte = SharedObject.publicStaticByte;");
        assertEquals(SharedObject.publicStaticByte, e.get("ps_byte"));
        e.eval("SharedObject.publicStaticByte = 16;");
        assertEquals(16, SharedObject.publicStaticByte);
    }

    @Test
    public void accessStaticFieldByteArray() throws ScriptException {
        e.eval("var ps_byte_array = SharedObject.publicStaticByteArray;");
        assertEquals(SharedObject.publicStaticByteArray[0], e.eval("SharedObject.publicStaticByteArray[0];"));
        assertArrayEquals(SharedObject.publicStaticByteArray, (byte[])e.get("ps_byte_array"));
        e.eval("var ts_byte_arr = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 3);" +
                "ts_byte_arr[0] = -18;" +
                "ts_byte_arr[1] = 56;" +
                "ts_byte_arr[2] = 60;" +
                "SharedObject.publicStaticByteArray = ts_byte_arr;");
        assertArrayEquals(new byte[] { -18, 56, 60 }, SharedObject.publicStaticByteArray);
        e.eval("SharedObject.publicStaticByteArray[0] = -90;");
        assertEquals(-90, SharedObject.publicStaticByteArray[0]);
    }

    @Test
    public void accessFinalFieldByte() throws ScriptException {
        e.eval("var pf_byte = o.publicFinalByte;");
        assertEquals(o.publicFinalByte, e.get("pf_byte"));
        e.eval("o.publicFinalByte = 16;");
        assertEquals(-7, o.publicFinalByte);
    }

    @Test
    public void accessFinalFieldByteArray() throws ScriptException {
        e.eval("var pf_byte_array = o.publicFinalByteArray;");
        assertEquals(o.publicFinalByteArray[0], e.eval("o.publicFinalByteArray[0];"));
        assertArrayEquals(o.publicFinalByteArray, (byte[])e.get("pf_byte_array"));
        e.eval("var tf_byte_arr = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 3);" +
                "tf_byte_arr[0] = -18;" +
                "tf_byte_arr[1] = 56;" +
                "tf_byte_arr[2] = 60;" +
                "o.publicFinalByteArray = tf_byte_arr;");
        assertArrayEquals(new byte[] { 1, 3, 6, 17, -128 }, o.publicFinalByteArray);
        e.eval("o.publicFinalByteArray[0] = -90;");
        assertEquals(-90, o.publicFinalByteArray[0]);
    }

    @Test
    public void accessStaticFinalFieldByte() throws ScriptException {
        e.eval("var psf_byte = SharedObject.publicStaticFinalByte;");
        assertEquals(SharedObject.publicStaticFinalByte, e.get("psf_byte"));
        e.eval("SharedObject.publicStaticFinalByte = 16;");
        assertEquals(-70, SharedObject.publicStaticFinalByte);
    }

    @Test
    public void accessStaticFinalFieldByteArray() throws ScriptException {
        e.eval("var psf_byte_array = SharedObject.publicStaticFinalByteArray;");
        assertEquals(SharedObject.publicStaticFinalByteArray[0], e.eval("SharedObject.publicStaticFinalByteArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFinalByteArray, (byte[])e.get("psf_byte_array"));
        e.eval("var tsf_byte_arr = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 3);" +
                "tsf_byte_arr[0] = -18;" +
                "tsf_byte_arr[1] = 56;" +
                "tsf_byte_arr[2] = 60;" +
                "SharedObject.publicStaticFinalByteArray = tsf_byte_arr;");
        assertArrayEquals(new byte[] { 17, -128, 81 }, SharedObject.publicStaticFinalByteArray);
        e.eval("SharedObject.publicStaticFinalByteArray[0] = -90;");
        assertEquals(-90, SharedObject.publicStaticFinalByteArray[0]);
    }

    // --------------------------------short
    // tests------------------------------------
    @Test
    public void accessFieldShort() throws ScriptException {
        e.eval("var p_short = o.publicShort;");
        assertEquals(o.publicShort, e.get("p_short"));
        e.eval("o.publicShort = 18;");
        assertEquals(18, o.publicShort);
    }

    @Test
    public void accessFieldShortArray() throws ScriptException {
        e.eval("var p_short_array = o.publicShortArray;");
        assertEquals(o.publicShortArray[0], e.eval("o.publicShortArray[0];"));
        assertArrayEquals(o.publicShortArray, (short[])e.get("p_short_array"));
        e.eval("var t_short_arr = java.lang.reflect.Array.newInstance(java.lang.Short.TYPE, 3);" +
                "t_short_arr[0] = 90;" +
                "t_short_arr[1] = 5;" +
                "t_short_arr[2] = -6000;" +
                "o.publicShortArray = t_short_arr;");
        assertArrayEquals(new short[] { 90, 5, -6000 }, o.publicShortArray);
        e.eval("o.publicShortArray[0] = -1000;");
        assertEquals(-1000, o.publicShortArray[0]);
    }

    @Test
    public void accessStaticFieldShort() throws ScriptException {
        e.eval("var ps_short = SharedObject.publicStaticShort;");
        assertEquals(SharedObject.publicStaticShort, e.get("ps_short"));
        e.eval("SharedObject.publicStaticShort = 180;");
        assertEquals(180, SharedObject.publicStaticShort);
    }

    @Test
    public void accessStaticFieldShortArray() throws ScriptException {
        e.eval("var ps_short_array = SharedObject.publicStaticShortArray;");
        assertEquals(SharedObject.publicStaticShortArray[0], e.eval("SharedObject.publicStaticShortArray[0];"));
        assertArrayEquals(SharedObject.publicStaticShortArray, (short[])e.get("ps_short_array"));
        e.eval("var ts_short_arr = java.lang.reflect.Array.newInstance(java.lang.Short.TYPE, 3);" +
                "ts_short_arr[0] = 90;" +
                "ts_short_arr[1] = 5;" +
                "ts_short_arr[2] = -6000;" +
                "SharedObject.publicStaticShortArray = ts_short_arr;");
        assertArrayEquals(new short[] { 90, 5, -6000 }, SharedObject.publicStaticShortArray);
        e.eval("SharedObject.publicStaticShortArray[0] = -1000;");
        assertEquals(-1000, SharedObject.publicStaticShortArray[0]);
    }

    @Test
    public void accessFinalFieldShort() throws ScriptException {
        e.eval("var pf_short = o.publicFinalShort;");
        assertEquals(o.publicFinalShort, e.get("pf_short"));
        e.eval("o.publicFinalShort = 180;");
        assertEquals(31220, o.publicFinalShort);
    }

    @Test
    public void accessFinalFieldShortArray() throws ScriptException {
        e.eval("var pf_short_array = o.publicFinalShortArray;");
        assertEquals(o.publicFinalShortArray[0], e.eval("o.publicFinalShortArray[0];"));
        assertArrayEquals(o.publicFinalShortArray, (short[])e.get("pf_short_array"));
        e.eval("var tf_short_arr = java.lang.reflect.Array.newInstance(java.lang.Short.TYPE, 3);" +
                "tf_short_arr[0] = 90;" +
                "tf_short_arr[1] = 5;" +
                "tf_short_arr[2] = -6000;" +
                "o.publicFinalShortArray = tf_short_arr;");
        assertArrayEquals(new short[] { 12240, 9200, -17289, 1200, 12 }, o.publicFinalShortArray);
        e.eval("o.publicFinalShortArray[0] = -1000;");
        assertEquals(-1000, o.publicFinalShortArray[0]);
    }

    @Test
    public void accessStaticFinalFieldShort() throws ScriptException {
        e.eval("var psf_short = SharedObject.publicStaticFinalShort;");
        assertEquals(SharedObject.publicStaticFinalShort, e.get("psf_short"));
        e.eval("SharedObject.publicStaticFinalShort = 180;");
        assertEquals(8888, SharedObject.publicStaticFinalShort);
    }

    @Test
    public void accessStaticFinalFieldShortArray() throws ScriptException {
        e.eval("var psf_short_array = SharedObject.publicStaticFinalShortArray;");
        assertEquals(SharedObject.publicStaticFinalShortArray[0], e.eval("SharedObject.publicStaticFinalShortArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFinalShortArray, (short[])e.get("psf_short_array"));
        e.eval("var tsf_short_arr = java.lang.reflect.Array.newInstance(java.lang.Short.TYPE, 3);" +
                "tsf_short_arr[0] = 90;" +
                "tsf_short_arr[1] = 5;" +
                "tsf_short_arr[2] = -6000;" +
                "SharedObject.publicStaticFinalShortArray = tsf_short_arr;");
        assertArrayEquals(new short[] { 8240, 9280, -1289, 120, 812 }, SharedObject.publicStaticFinalShortArray);
        e.eval("SharedObject.publicStaticFinalShortArray[0] = -1000;");
        assertEquals(-1000, SharedObject.publicStaticFinalShortArray[0]);
    }

    // --------------------------------char
    // tests------------------------------------
    @Test
    public void accessFieldChar() throws ScriptException {
        e.eval("var p_char = o.publicChar;");
        assertEquals(o.publicChar, e.get("p_char"));
        e.eval("o.publicChar = 'S';");
        assertEquals('S', o.publicChar);
        e.eval("o.publicChar = 10;");
        assertEquals(10, o.publicChar);
        e.eval("try {"
                + "    o.publicChar = 'Big string';" +
                "} catch(e) {" +
                "    var isThrown = true;" +
                "}");
        assertEquals("Exception thrown", true, e.get("isThrown"));
        assertEquals(10, o.publicChar);
    }

    @Test
    public void accessFieldCharArray() throws ScriptException {
        e.eval("var p_char_array = o.publicCharArray;");
        assertEquals(o.publicCharArray[0], e.eval("o.publicCharArray[0];"));
        assertArrayEquals(o.publicCharArray, (char[])e.get("p_char_array"));
        e.eval("var t_char_arr = java.lang.reflect.Array.newInstance(java.lang.Character.TYPE, 3);" +
                "t_char_arr[0] = 'F';" +
                "t_char_arr[1] = 'o';" +
                "t_char_arr[2] = 'o';" +
                "o.publicCharArray = t_char_arr;");
        assertArrayEquals("Foo".toCharArray(), o.publicCharArray);
        e.eval("o.publicCharArray[0] = 'Z';");
        assertEquals('Z', o.publicCharArray[0]);
    }

    @Test
    public void accessStaticFieldChar() throws ScriptException {
        e.eval("var ps_char = SharedObject.publicStaticChar;");
        assertEquals(SharedObject.publicStaticChar, e.get("ps_char"));
        e.eval("SharedObject.publicStaticChar = 'Z';");
        assertEquals('Z', SharedObject.publicStaticChar);
    }

    @Test
    public void accessStaticFieldCharArray() throws ScriptException {
        e.eval("var ps_char_array = SharedObject.publicStaticCharArray;");
        assertEquals(SharedObject.publicStaticCharArray[0], e.eval("SharedObject.publicStaticCharArray[0];"));
        assertArrayEquals(SharedObject.publicStaticCharArray, (char[])e.get("ps_char_array"));
        e.eval("var ts_char_arr = java.lang.reflect.Array.newInstance(java.lang.Character.TYPE, 3);" +
                "ts_char_arr[0] = 'G';" +
                "ts_char_arr[1] = 'o';" +
                "ts_char_arr[2] = 'o';" +
                "SharedObject.publicStaticCharArray = ts_char_arr;");
        assertArrayEquals("Goo".toCharArray(), SharedObject.publicStaticCharArray);
        e.eval("SharedObject.publicStaticCharArray[0] = 'Z';");
        assertEquals('Z', SharedObject.publicStaticCharArray[0]);
    }

    @Test
    public void accessFinalFieldChar() throws ScriptException {
        e.eval("var pf_char = o.publicFinalChar;");
        assertEquals(o.publicFinalChar, e.get("pf_char"));
        e.eval("o.publicFinalChar = 'S';");
        assertEquals('E', o.publicFinalChar);
    }

    @Test
    public void accessFinalCharArray() throws ScriptException {
        e.eval("var pf_char_array = o.publicFinalCharArray;");
        assertEquals(o.publicFinalCharArray[0], e.eval("o.publicFinalCharArray[0];"));
        assertArrayEquals(o.publicFinalCharArray, (char[])e.get("pf_char_array"));
        e.eval("var tf_char_arr = java.lang.reflect.Array.newInstance(java.lang.Character.TYPE, 3);" +
                "tf_char_arr[0] = 'F';" +
                "tf_char_arr[1] = 'o';" +
                "tf_char_arr[2] = 'o';" +
                "o.publicFinalCharArray = tf_char_arr;");
        assertArrayEquals("Nashorn hello".toCharArray(), o.publicFinalCharArray);
        e.eval("o.publicFinalCharArray[0] = 'Z';");
        assertEquals('Z', o.publicFinalCharArray[0]);
    }

    @Test
    public void accessStaticFinalFieldChar() throws ScriptException {
        e.eval("var psf_char = SharedObject.publicStaticFinalChar;");
        assertEquals(SharedObject.publicStaticFinalChar, e.get("psf_char"));
        e.eval("SharedObject.publicStaticFinalChar = 'Z';");
        assertEquals('K', SharedObject.publicStaticFinalChar);
    }

    @Test
    public void accessStaticFinalFieldCharArray() throws ScriptException {
        e.eval("var psf_char_array = SharedObject.publicStaticFinalCharArray;");
        assertEquals(SharedObject.publicStaticFinalCharArray[0], e.eval("SharedObject.publicStaticFinalCharArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFinalCharArray, (char[])e.get("psf_char_array"));
        e.eval("var tsf_char_arr = java.lang.reflect.Array.newInstance(java.lang.Character.TYPE, 3);" +
                "tsf_char_arr[0] = 'Z';" +
                "tsf_char_arr[1] = 'o';" +
                "tsf_char_arr[2] = 'o';" +
                "SharedObject.publicStaticFinalCharArray = tsf_char_arr;");
        assertArrayEquals("StaticString".toCharArray(), SharedObject.publicStaticFinalCharArray);
        e.eval("SharedObject.publicStaticFinalCharArray[0] = 'Z';");
        assertEquals('Z', SharedObject.publicStaticFinalCharArray[0]);
    }

    // --------------------------------float
    // tests------------------------------------
    @Test
    public void accessFieldFloat() throws ScriptException {
        e.eval("var p_float = o.publicFloat;");
        assertEquals(o.publicFloat, e.get("p_float"));
        o.publicFloat = 0.0f / 0.0f;
        assertEquals(true, e.eval("isNaN(o.publicFloat)"));
        o.publicFloat = 1.0f / 0.0f;
        assertEquals(true, e.eval("Number.POSITIVE_INFINITY === o.publicFloat"));
        o.publicFloat = -1.0f / 0.0f;
        assertEquals(true, e.eval("Number.NEGATIVE_INFINITY === o.publicFloat"));
        e.eval("o.publicFloat = 20;");
        assertEquals(20, o.publicFloat, 1e-10);
        e.eval("o.publicFloat = 0.0/0.0;");
        assertTrue(Float.isNaN(o.publicFloat));
        e.eval("o.publicFloat = 1.0/0.0;");
        assertEquals(Float.floatToIntBits(Float.POSITIVE_INFINITY), Float.floatToIntBits(o.publicFloat));
        e.eval("o.publicFloat = -1.0/0.0;");
        assertEquals(Float.NEGATIVE_INFINITY, o.publicFloat, 1e-10);
    }

    @Test
    public void accessFieldFloatArray() throws ScriptException {
        e.eval("var p_float_array = o.publicFloatArray;");
        assertEquals(o.publicFloatArray[0], e.eval("o.publicFloatArray[0];"));
        assertArrayEquals(o.publicFloatArray, (float[])e.get("p_float_array"), 1e-10f);
        e.eval("var t_float_arr = java.lang.reflect.Array.newInstance(java.lang.Float.TYPE, 3);" +
                "t_float_arr[0] = 9.0;" +
                "t_float_arr[1] = 5.12345;" +
                "t_float_arr[2] = -60.03;" +
                "o.publicFloatArray = t_float_arr;");
        assertArrayEquals(new float[] { 9.0f, 5.12345f, -60.03f }, o.publicFloatArray, 1e-10f);
        e.eval("o.publicFloatArray[0] = -513.2;");
        assertArrayEquals(new float[] { -513.2f, 5.12345f, -60.03f }, o.publicFloatArray, 1e-10f);
    }

    @Test
    public void accessStaticFieldFloat() throws ScriptException {
        e.eval("var ps_float = SharedObject.publicStaticFloat;");
        assertEquals(SharedObject.publicStaticFloat, e.get("ps_float"));
        SharedObject.publicStaticFloat = 0.0f / 0.0f;
        assertEquals(true, e.eval("isNaN(SharedObject.publicStaticFloat)"));
        SharedObject.publicStaticFloat = 1.0f / 0.0f;
        assertEquals(true, e.eval("Number.POSITIVE_INFINITY === SharedObject.publicStaticFloat"));
        SharedObject.publicStaticFloat = -1.0f / 0.0f;
        assertEquals(true, e.eval("Number.NEGATIVE_INFINITY === SharedObject.publicStaticFloat"));
        e.eval("SharedObject.publicStaticFloat = 20.0;");
        assertEquals(20.0f, SharedObject.publicStaticFloat, 1e-10);
        e.eval("SharedObject.publicStaticFloat = 0.0/0.0;");
        assertTrue(Float.isNaN(SharedObject.publicStaticFloat));
        e.eval("SharedObject.publicStaticFloat = 1.0/0.0;");
        assertEquals(Float.floatToIntBits(Float.POSITIVE_INFINITY), Float.floatToIntBits(SharedObject.publicStaticFloat));
        e.eval("SharedObject.publicStaticFloat = -1.0/0.0;");
        assertEquals(Float.floatToIntBits(Float.NEGATIVE_INFINITY), Float.floatToIntBits(SharedObject.publicStaticFloat));
    }

    @Test
    public void accessStaticFieldFloatArray() throws ScriptException {
        e.eval("var ps_float_array = SharedObject.publicStaticFloatArray;");
        assertEquals(SharedObject.publicStaticFloatArray[0], e.eval("SharedObject.publicStaticFloatArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFloatArray, (float[])e.get("ps_float_array"), 1e-10f);
        e.eval("var ts_float_arr = java.lang.reflect.Array.newInstance(java.lang.Float.TYPE, 3);" +
                "ts_float_arr[0] = 9.0;" +
                "ts_float_arr[1] = 5.12345;" +
                "ts_float_arr[2] = -60.03;" +
                "SharedObject.publicStaticFloatArray = ts_float_arr;");
        assertArrayEquals(new float[] { 9.0f, 5.12345f, -60.03f }, SharedObject.publicStaticFloatArray, 1e-10f);
        e.eval("SharedObject.publicStaticFloatArray[0] = -513.2;");
        assertArrayEquals(new float[] { -513.2f, 5.12345f, -60.03f }, SharedObject.publicStaticFloatArray, 1e-10f);
    }

    @Test
    public void accessFinalFloat() throws ScriptException {
        e.eval("var pf_float = o.publicFinalFloat;");
        assertEquals(o.publicFinalFloat, e.get("pf_float"));
        e.eval("o.publicFinalFloat = 20.0;");
        assertEquals(7.72e8f, o.publicFinalFloat, 1e-10);
    }

    @Test
    public void accessFinalFloatArray() throws ScriptException {
        e.eval("var pf_float_array = o.publicFinalFloatArray;");
        assertEquals(o.publicFinalFloatArray[0], e.eval("o.publicFinalFloatArray[0];"));
        assertArrayEquals(o.publicFinalFloatArray, (float[])e.get("pf_float_array"), 1e-10f);
        e.eval("var tf_float_arr = java.lang.reflect.Array.newInstance(java.lang.Float.TYPE, 3);" +
                "tf_float_arr[0] = 9.0;" +
                "tf_float_arr[1] = 5.12345;" +
                "tf_float_arr[2] = -60.03;" +
                "o.publicFinalFloatArray = tf_float_arr;");
        assertArrayEquals(new float[] { -131.012f, 189.32f, -31.32e8f, 3.72f }, o.publicFinalFloatArray, 1e-10f);
        e.eval("o.publicFinalFloatArray[0] = -513.2;");
        assertEquals(-513.2f, o.publicFinalFloatArray[0], 1e-10f);
    }

    @Test
    public void accessStaticFinalFieldFloat() throws ScriptException {
        e.eval("var psf_float = SharedObject.publicStaticFinalFloat;");
        assertEquals(SharedObject.publicStaticFinalFloat, e.get("psf_float"));
        e.eval("SharedObject.publicStaticFinalFloat = 20.0;");
        assertEquals(0.72e8f, SharedObject.publicStaticFinalFloat, 1e-10);
    }

    @Test
    public void accessStaticFinalFieldFloatArray() throws ScriptException {
        e.eval("var psf_float_array = SharedObject.publicStaticFinalFloatArray;");
        assertEquals(SharedObject.publicStaticFinalFloatArray[0], e.eval("SharedObject.publicStaticFinalFloatArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFinalFloatArray, (float[])e.get("psf_float_array"), 1e-10f);
        e.eval("var tsf_float_arr = java.lang.reflect.Array.newInstance(java.lang.Float.TYPE, 3);" +
                "tsf_float_arr[0] = 9.0;" +
                "tsf_float_arr[1] = 5.12345;" +
                "tsf_float_arr[2] = -60.03;" +
                "SharedObject.publicStaticFinalFloatArray = tsf_float_arr;");
        assertArrayEquals(new float[] { -8131.012f, 9.32f, -138.32e8f, 0.72f }, SharedObject.publicStaticFinalFloatArray, 1e-10f);
        e.eval("SharedObject.publicStaticFinalFloatArray[0] = -513.2;");
        assertEquals(-513.2f, SharedObject.publicStaticFinalFloatArray[0], 1e-10f);
    }

    // --------------------------------double
    // tests------------------------------------
    @Test
    public void accessFieldDouble() throws ScriptException {
        e.eval("var p_double = o.publicDouble;");
        assertEquals(o.publicDouble, e.get("p_double"));
        o.publicDouble = 0.0 / 0.0;
        assertEquals(true, e.eval("isNaN(o.publicDouble)"));
        o.publicDouble = 1.0 / 0.0;
        assertEquals(true, e.eval("Number.POSITIVE_INFINITY === o.publicDouble"));
        o.publicDouble = -1.0 / 0.0;
        assertEquals(true, e.eval("Number.NEGATIVE_INFINITY === o.publicDouble"));
        e.eval("o.publicDouble = 30;");
        assertEquals(Double.doubleToLongBits(30.0), Double.doubleToLongBits(o.publicDouble));
        e.eval("o.publicDouble = 0.0/0.0;");
        assertTrue(Double.isNaN(o.publicDouble));
        e.eval("o.publicDouble = 1.0/0.0;");
        assertEquals(Double.doubleToLongBits(Double.POSITIVE_INFINITY), Double.doubleToLongBits(o.publicDouble));
        e.eval("o.publicDouble = -1.0/0.0;");
        assertEquals(Double.doubleToLongBits(Double.NEGATIVE_INFINITY), Double.doubleToLongBits(o.publicDouble));
    }

    @Test
    public void accessFieldDoubleArrayRead() throws ScriptException {
        e.eval("var p_double_array = o.publicDoubleArray;");
        assertEquals(o.publicDoubleArray[0], e.eval("o.publicDoubleArray[0];"));
        assertArrayEquals(o.publicDoubleArray, (double[])e.get("p_double_array"), 1e-10);
        e.eval("var t_double_arr = java.lang.reflect.Array.newInstance(java.lang.Double.TYPE, 3);" +
                "t_double_arr[0] = 9e10;" +
                "t_double_arr[1] = 0.677777;" +
                "t_double_arr[2] = -0.0000001;" +
                "o.publicDoubleArray = t_double_arr;");
        assertArrayEquals(new double[] { 9e10, 0.677777, -0.0000001 }, o.publicDoubleArray, 1e-10f);
        e.eval("o.publicDoubleArray[0] = -5.2e10;");
        assertEquals(-5.2e10, o.publicDoubleArray[0], 1e-10f);
    }

    @Test
    public void accessStaticFieldDouble() throws ScriptException {
        e.eval("var ps_double = SharedObject.publicStaticDouble;");
        assertEquals(SharedObject.publicStaticDouble, e.get("ps_double"));
        SharedObject.publicStaticDouble = 0.0 / 0.0;
        assertEquals(true, e.eval("isNaN(SharedObject.publicStaticDouble)"));
        SharedObject.publicStaticDouble = 1.0 / 0.0;
        assertEquals(true, e.eval("Number.POSITIVE_INFINITY === SharedObject.publicStaticDouble"));
        SharedObject.publicStaticDouble = -1.0 / 0.0;
        assertEquals(true, e.eval("Number.NEGATIVE_INFINITY === SharedObject.publicStaticDouble"));
        e.eval("SharedObject.publicStaticDouble = 40.0;");
        assertEquals(Double.doubleToLongBits(40.0), Double.doubleToLongBits(SharedObject.publicStaticDouble));
        e.eval("SharedObject.publicStaticDouble = 0.0/0.0;");
        assertTrue(Double.isNaN(SharedObject.publicStaticDouble));
        e.eval("SharedObject.publicStaticDouble = 1.0/0.0;");
        assertEquals(Double.doubleToLongBits(Double.POSITIVE_INFINITY), Double.doubleToLongBits(SharedObject.publicStaticDouble));
        e.eval("SharedObject.publicStaticDouble = -1.0/0.0;");
        assertEquals(Double.doubleToLongBits(Double.NEGATIVE_INFINITY), Double.doubleToLongBits(SharedObject.publicStaticDouble));
    }

    @Test
    public void accessStaticFieldDoubleArrayRead() throws ScriptException {
        e.eval("var ps_double_array = SharedObject.publicStaticDoubleArray;");
        assertEquals(SharedObject.publicStaticDoubleArray[0], e.eval("SharedObject.publicStaticDoubleArray[0];"));
        assertArrayEquals(SharedObject.publicStaticDoubleArray, (double[])e.get("ps_double_array"), 1e-10);
        e.eval("var ts_double_arr = java.lang.reflect.Array.newInstance(java.lang.Double.TYPE, 3);" +
                "ts_double_arr[0] = 9e10;" +
                "ts_double_arr[1] = 0.677777;" +
                "ts_double_arr[2] = -0.0000001;" +
                "SharedObject.publicStaticDoubleArray = ts_double_arr;");
        assertArrayEquals(new double[] { 9e10, 0.677777, -0.0000001 }, SharedObject.publicStaticDoubleArray, 1e-10f);
        e.eval("SharedObject.publicStaticDoubleArray[0] = -5.2e10;");
        assertEquals(-5.2e10, SharedObject.publicStaticDoubleArray[0], 1e-10f);
    }

    @Test
    public void accessFinalFieldDouble() throws ScriptException {
        e.eval("var pf_double = o.publicFinalDouble;");
        assertEquals(o.publicFinalDouble, e.get("pf_double"));
        e.eval("o.publicFinalDouble = 30.0;");
        assertEquals(Double.doubleToLongBits(1.3412e20), Double.doubleToLongBits(o.publicFinalDouble));
    }

    @Test
    public void accessFinalFieldDoubleArrayRead() throws ScriptException {
        e.eval("var pf_double_array = o.publicFinalDoubleArray;");
        assertEquals(o.publicFinalDoubleArray[0], e.eval("o.publicFinalDoubleArray[0];"));
        assertArrayEquals(o.publicFinalDoubleArray, (double[])e.get("pf_double_array"), 1e-10);
        e.eval("var tf_double_arr = java.lang.reflect.Array.newInstance(java.lang.Double.TYPE, 3);" +
                "tf_double_arr[0] = 9e10;" +
                "tf_double_arr[1] = 0.677777;" +
                "tf_double_arr[2] = -0.0000001;" +
                "o.publicFinalDoubleArray = tf_double_arr;");
        assertArrayEquals(new double[] { 0.725e80, 0.12e10, 8e-3, 1.00077 }, o.publicFinalDoubleArray, 1e-10f);
        e.eval("o.publicFinalDoubleArray[0] = -5.2e10;");
        assertEquals(-5.2e10, o.publicFinalDoubleArray[0], 1e-10f);
    }

    @Test
    public void accessStaticFinalFieldDouble() throws ScriptException {
        e.eval("var psf_double = SharedObject.publicStaticFinalDouble;");
        assertEquals(SharedObject.publicStaticFinalDouble, e.get("psf_double"));
        e.eval("SharedObject.publicStaticFinalDouble = 40.0;");
        assertEquals(Double.doubleToLongBits(1.8e12), Double.doubleToLongBits(SharedObject.publicStaticFinalDouble));
    }

    @Test
    public void accessStaticFinalFieldDoubleArrayRead() throws ScriptException {
        e.eval("var psf_double_array = SharedObject.publicStaticFinalDoubleArray;");
        assertEquals(SharedObject.publicStaticFinalDoubleArray[0], e.eval("SharedObject.publicStaticFinalDoubleArray[0];"));
        assertArrayEquals(SharedObject.publicStaticFinalDoubleArray, (double[])e.get("psf_double_array"), 1e-10);
        e.eval("var tsf_double_arr = java.lang.reflect.Array.newInstance(java.lang.Double.TYPE, 3);" +
                "tsf_double_arr[0] = 9e10;" +
                "tsf_double_arr[1] = 0.677777;" +
                "tsf_double_arr[2] = -0.0000001;" +
                "SharedObject.publicStaticFinalDoubleArray = tsf_double_arr;");
        assertArrayEquals(new double[] { 8.725e80, 0.82e10, 18e-3, 1.08077 }, SharedObject.publicStaticFinalDoubleArray, 1e-10f);
        e.eval("SharedObject.publicStaticFinalDoubleArray[0] = -5.2e10;");
        assertEquals(-5.2e10, SharedObject.publicStaticFinalDoubleArray[0], 1e-10f);
    }

}

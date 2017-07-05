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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @build jdk.nashorn.api.javaaccess.SharedObject jdk.nashorn.api.javaaccess.Person jdk.nashorn.api.javaaccess.NumberBoxingTest
 * @run testng/othervm jdk.nashorn.api.javaaccess.NumberBoxingTest
 */
public class NumberBoxingTest {

    private static ScriptEngine e;
    private static SharedObject o;

    public static void main(final String[] args) {
        TestNG.main(args);
    }

    @BeforeClass
    public static void setUpClass() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        e = m.getEngineByName("nashorn");
        o = new SharedObject();
        e.put("o", o);
        e.eval("var SharedObject = Packages.jdk.nashorn.api.javaaccess.SharedObject;");
    }

    @AfterClass
    public static void tearDownClass() {
        e = null;
        o = null;
    }

    // --------------------------------long
    // tests------------------------------------
    @Test
    public void accessFieldLongBoxing() throws ScriptException {
        e.eval("var p_long = o.publicLongBox;");
        assertEquals(o.publicLongBox, e.get("p_long"));
        e.eval("o.publicLongBox = 12;");
        assertEquals(Long.valueOf(12), o.publicLongBox);
    }

    @Test
    public void accessStaticFieldLongBoxing() throws ScriptException {
        e.eval("var ps_long = SharedObject.publicStaticLongBox;");
        assertEquals(SharedObject.publicStaticLongBox, e.get("ps_long"));
        e.eval("SharedObject.publicStaticLongBox = 120;");
        assertEquals(120L, SharedObject.publicStaticLongBox.longValue());
    }

    @Test
    public void accessFinalFieldLongBoxing() throws ScriptException {
        e.eval("var pf_long = o.publicFinalLongBox;");
        assertEquals(o.publicFinalLongBox, e.get("pf_long"));
        e.eval("o.publicFinalLongBox = 120;");
        assertEquals(Long.valueOf(9377333334L), o.publicFinalLongBox);
    }

    @Test
    public void accessStaticFinalFieldLongBoxing() throws ScriptException {
        e.eval("var psf_long = SharedObject.publicStaticFinalLong;");
        assertEquals(SharedObject.publicStaticFinalLong, e.get("psf_long"));
        e.eval("SharedObject.publicStaticFinalLong = 120;");
        assertEquals(8333333333333L, SharedObject.publicStaticFinalLong);
    }

    // --------------------------------int
    // tests------------------------------------
    @Test
    public void accessFieldIntBoxing() throws ScriptException {
        e.eval("var p_int = o.publicIntBox;");
        assertEquals(o.publicIntBox, e.get("p_int"));
        e.eval("o.publicIntBox = 14;");
        assertEquals(Integer.valueOf(14), o.publicIntBox);
    }

    @Test
    public void accessStaticFieldIntBoxing() throws ScriptException {
        e.eval("var ps_int = SharedObject.publicStaticInt;");
        assertEquals(SharedObject.publicStaticInt, e.get("ps_int"));
        e.eval("SharedObject.publicStaticInt = 140;");
        assertEquals(140, SharedObject.publicStaticInt);
    }

    @Test
    public void accessFinalFieldIntBoxing() throws ScriptException {
        e.eval("var pf_int = o.publicFinalIntBox;");
        assertEquals(o.publicFinalIntBox, e.get("pf_int"));
        e.eval("o.publicFinalIntBox = 10;");
        assertEquals(Integer.valueOf(207512301), o.publicFinalIntBox);
    }

    @Test
    public void accessStaticFinalFieldIntBoxing() throws ScriptException {
        e.eval("var psf_int = SharedObject.publicStaticFinalInt;");
        assertEquals(SharedObject.publicStaticFinalInt, e.get("psf_int"));
        e.eval("SharedObject.publicStaticFinalInt = 140;");
        assertEquals(207182023, SharedObject.publicStaticFinalInt);
    }

    // --------------------------------byte
    // tests------------------------------------
    @Test
    public void accessFieldByteBoxing() throws ScriptException {
        e.eval("var p_byte = o.publicByteBox;");
        assertEqualsDouble(o.publicByteBox, "p_byte");
        e.eval("o.publicByteBox = 16;");
        assertEquals(Byte.valueOf((byte)16), o.publicByteBox);
    }

    @Test
    public void accessStaticFieldByteBoxing() throws ScriptException {
        e.eval("var ps_byte = SharedObject.publicStaticByte;");
        assertEqualsDouble(SharedObject.publicStaticByte, "ps_byte");
        e.eval("SharedObject.publicStaticByte = 16;");
        assertEquals(16, SharedObject.publicStaticByte);
    }

    @Test
    public void accessFinalFieldByteBoxing() throws ScriptException {
        e.eval("var pf_byte = o.publicFinalByteBox;");
        assertEqualsDouble(o.publicFinalByteBox, "pf_byte");
        e.eval("o.publicFinalByteBox = 16;");
        assertEquals(Byte.valueOf((byte)19), o.publicFinalByteBox);
    }

    @Test
    public void accessStaticFinalFieldByteBoxing() throws ScriptException {
        e.eval("var psf_byte = SharedObject.publicStaticFinalByte;");
        assertEqualsDouble(SharedObject.publicStaticFinalByte, "psf_byte");
        e.eval("SharedObject.publicStaticFinalByte = 16;");
        assertEquals(-70, SharedObject.publicStaticFinalByte);
    }

    // --------------------------------short
    // tests------------------------------------
    @Test
    public void accessFieldShortBoxing() throws ScriptException {
        e.eval("var p_short = o.publicShortBox;");
        assertEqualsDouble(o.publicShortBox, "p_short");
        e.eval("o.publicShortBox = 18;");
        assertEquals(Short.valueOf((short)18), o.publicShortBox);
    }

    private static void assertEqualsDouble(final Number n, final String name) {
        assertEquals(n.doubleValue(), ((Number)e.get(name)).doubleValue());
    }

    @Test
    public void accessStaticFieldShortBoxing() throws ScriptException {
        e.eval("var ps_short = SharedObject.publicStaticShort;");
        assertEqualsDouble(SharedObject.publicStaticShort, "ps_short");
        e.eval("SharedObject.publicStaticShort = 180;");
        assertEquals(180, SharedObject.publicStaticShort);
    }

    @Test
    public void accessFinalFieldShortBoxing() throws ScriptException {
        e.eval("var pf_short = o.publicFinalShortBox;");
        assertEqualsDouble(o.publicFinalShortBox, "pf_short");
        e.eval("o.publicFinalShortBox = 180;");
        assertEquals(Short.valueOf((short)-26777), o.publicFinalShortBox);
    }

    @Test
    public void accessStaticFinalFieldShortBoxing() throws ScriptException {
        e.eval("var psf_short = SharedObject.publicStaticFinalShort;");
        assertEqualsDouble(SharedObject.publicStaticFinalShort, "psf_short");
        e.eval("SharedObject.publicStaticFinalShort = 180;");
        assertEquals(8888, SharedObject.publicStaticFinalShort);
    }

    // --------------------------------char
    // tests------------------------------------
    @Test
    public void accessFieldCharBoxing() throws ScriptException {
        e.eval("var p_char = o.publicCharBox;");
        assertEquals(o.publicCharBox, e.get("p_char"));
        e.eval("o.publicCharBox = 'S';");
        assertEquals(Character.valueOf('S'), o.publicCharBox);
        e.eval("try {" +
                "    o.publicCharBox = 'Big string';" +
                "} catch(e) {" +
                "    var isThrown = true;" +
                "}");
        assertEquals("Exception thrown", true, e.get("isThrown"));
        assertEquals(Character.valueOf('S'), o.publicCharBox);
    }

    @Test
    public void accessStaticFieldCharBoxing() throws ScriptException {
        e.eval("var ps_char = SharedObject.publicStaticChar;");
        assertEquals(SharedObject.publicStaticChar, e.get("ps_char"));
        e.eval("SharedObject.publicStaticChar = 'Z';");
        assertEquals('Z', SharedObject.publicStaticChar);
    }

    @Test
    public void accessFinalFieldCharBoxing() throws ScriptException {
        e.eval("var pf_char = o.publicFinalCharBox;");
        assertEquals(o.publicFinalCharBox, e.get("pf_char"));
        e.eval("o.publicFinalCharBox = 'S';");
        assertEquals(Character.valueOf('F'), o.publicFinalCharBox);
    }

    @Test
    public void accessStaticFinalFieldCharBoxing() throws ScriptException {
        e.eval("var psf_char = SharedObject.publicStaticFinalChar;");
        assertEquals(SharedObject.publicStaticFinalChar, e.get("psf_char"));
        e.eval("SharedObject.publicStaticFinalChar = 'Z';");
        assertEquals('K', SharedObject.publicStaticFinalChar);
    }

    // --------------------------------float
    // tests------------------------------------
    @Test
    public void accessFieldFloatBoxing() throws ScriptException {
        e.eval("var p_float = o.publicFloatBox;");
        assertEqualsDouble(o.publicFloatBox, "p_float");
        o.publicFloatBox = 0.0f / 0.0f;
        assertEquals(true, e.eval("isNaN(o.publicFloatBox)"));
        o.publicFloatBox = 1.0f / 0.0f;
        assertEquals(true, e.eval("Number.POSITIVE_INFINITY === o.publicFloatBox"));
        o.publicFloatBox = -1.0f / 0.0f;
        assertEquals(true, e.eval("Number.NEGATIVE_INFINITY === o.publicFloatBox"));
        e.eval("o.publicFloatBox = 20;");
        assertEquals(20, o.publicFloatBox, 1e-10);
        e.eval("o.publicFloatBox = 0.0/0.0;");
        assertTrue(Float.isNaN(o.publicFloatBox));
        e.eval("o.publicFloatBox = 1.0/0.0;");
        assertEquals(Float.floatToIntBits(Float.POSITIVE_INFINITY), Float.floatToIntBits(o.publicFloatBox));
        e.eval("o.publicFloatBox = -1.0/0.0;");
        assertEquals(Float.NEGATIVE_INFINITY, o.publicFloatBox, 1e-10);
    }

    @Test
    public void accessStaticFieldFloatBoxing() throws ScriptException {
        e.eval("var ps_float = SharedObject.publicStaticFloat;");
        assertEqualsDouble(SharedObject.publicStaticFloat, "ps_float");
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
    public void accessFinalFloatBoxing() throws ScriptException {
        e.eval("var pf_float = o.publicFinalFloatBox;");
        assertEqualsDouble(o.publicFinalFloatBox, "pf_float");
        e.eval("o.publicFinalFloatBox = 20.0;");
        assertEquals(1.372e4f, o.publicFinalFloatBox, 1e-10);
    }

    @Test
    public void accessStaticFinalFieldFloatBoxing() throws ScriptException {
        e.eval("var psf_float = SharedObject.publicStaticFinalFloat;");
        assertEqualsDouble(SharedObject.publicStaticFinalFloat, "psf_float");
        e.eval("SharedObject.publicStaticFinalFloat = 20.0;");
        assertEquals(0.72e8f, SharedObject.publicStaticFinalFloat, 1e-10);
    }

    // --------------------------------double
    // tests------------------------------------
    @Test
    public void accessFieldDoubleBoxing() throws ScriptException {
        e.eval("var p_double = o.publicDoubleBox;");
        assertEquals(o.publicDoubleBox, e.get("p_double"));
        o.publicDoubleBox = 0.0 / 0.0;
        assertEquals(true, e.eval("isNaN(o.publicDoubleBox)"));
        o.publicDoubleBox = 1.0 / 0.0;
        assertEquals(true, e.eval("Number.POSITIVE_INFINITY === o.publicDoubleBox"));
        o.publicDoubleBox = -1.0 / 0.0;
        assertEquals(true, e.eval("Number.NEGATIVE_INFINITY === o.publicDoubleBox"));
        e.eval("o.publicDoubleBox = 30;");
        assertEquals(Double.doubleToLongBits(30.0), Double.doubleToLongBits(o.publicDoubleBox));
        e.eval("o.publicDoubleBox = 0.0/0.0;");
        assertTrue(Double.isNaN(o.publicDoubleBox));
        e.eval("o.publicDoubleBox = 1.0/0.0;");
        assertEquals(Double.doubleToLongBits(Double.POSITIVE_INFINITY), Double.doubleToLongBits(o.publicDoubleBox));
        e.eval("o.publicDoubleBox = -1.0/0.0;");
        assertEquals(Double.doubleToLongBits(Double.NEGATIVE_INFINITY), Double.doubleToLongBits(o.publicDoubleBox));
    }

    @Test
    public void accessStaticFieldDoubleBoxing() throws ScriptException {
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
    public void accessFinalFieldDoubleBoxing() throws ScriptException {
        e.eval("var pf_double = o.publicFinalDoubleBox;");
        assertEquals(o.publicFinalDoubleBox, e.get("pf_double"));
        e.eval("o.publicFinalDoubleBox = 30.0;");
        assertEquals(Double.doubleToLongBits(1.412e-12), Double.doubleToLongBits(o.publicFinalDoubleBox));
    }

    @Test
    public void accessStaticFinalFieldDoubleBoxing() throws ScriptException {
        e.eval("var psf_double = SharedObject.publicStaticFinalDouble;");
        assertEquals(SharedObject.publicStaticFinalDouble, e.get("psf_double"));
        e.eval("SharedObject.publicStaticFinalDouble = 40.0;");
        assertEquals(Double.doubleToLongBits(1.8e12), Double.doubleToLongBits(SharedObject.publicStaticFinalDouble));
    }

}

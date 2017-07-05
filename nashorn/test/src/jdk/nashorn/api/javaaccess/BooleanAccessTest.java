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

import java.util.Arrays;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.TestNG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @build jdk.nashorn.api.javaaccess.SharedObject jdk.nashorn.api.javaaccess.Person jdk.nashorn.api.javaaccess.BooleanAccessTest
 * @run testng/othervm jdk.nashorn.api.javaaccess.BooleanAccessTest
 */
public class BooleanAccessTest {

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

    @Test
    public void accessFieldBoolean() throws ScriptException {
        e.eval("var p_boolean = o.publicBoolean;");
        assertEquals(o.publicBoolean, e.get("p_boolean"));
        assertEquals("boolean", e.eval("typeof p_boolean;"));
        e.eval("o.publicBoolean = false;");
        assertEquals(false, o.publicBoolean);
    }

    @Test
    public void accessFieldBooleanArray() throws ScriptException {
        e.eval("var p_boolean_array = o.publicBooleanArray;");
        assertEquals(o.publicBooleanArray[0], e.eval("o.publicBooleanArray[0]"));
        assertTrue(Arrays.equals(o.publicBooleanArray, (boolean[])e.get("p_boolean_array")));
        e.eval("var t_boolean_arr = java.lang.reflect.Array.newInstance(java.lang.Boolean.TYPE, 3);" +
                "t_boolean_arr[0] = true;" +
                "t_boolean_arr[1] = false;" +
                "t_boolean_arr[2] = false;" +
                "o.publicBooleanArray = t_boolean_arr;");
        assertTrue(Arrays.equals(new boolean[] { true, false, false }, o.publicBooleanArray));
        e.eval("o.publicBooleanArray[0] = false;");
        assertEquals(false, o.publicBooleanArray[0]);
    }

    @Test
    public void accessStaticFieldBoolean() throws ScriptException {
        e.eval("var ps_boolean = SharedObject.publicStaticBoolean;");
        assertEquals(SharedObject.publicStaticBoolean, e.get("ps_boolean"));
        assertEquals("boolean", e.eval("typeof ps_boolean;"));
        e.eval("SharedObject.publicStaticBoolean = false;");
        assertEquals(false, SharedObject.publicStaticBoolean);
    }

    @Test
    public void accessStaticFieldBooleanArray() throws ScriptException {
        e.eval("var ps_boolean_array = SharedObject.publicStaticBooleanArray;");
        assertEquals(SharedObject.publicStaticBooleanArray[0], e.eval("SharedObject.publicStaticBooleanArray[0]"));
        assertTrue(Arrays.equals(SharedObject.publicStaticBooleanArray, (boolean[])e.get("ps_boolean_array")));
        e.eval("var ts_boolean_arr = java.lang.reflect.Array.newInstance(java.lang.Boolean.TYPE, 3);" +
                "ts_boolean_arr[0] = true;" +
                "ts_boolean_arr[1] = false;" +
                "ts_boolean_arr[2] = true;" +
                "SharedObject.publicStaticBooleanArray = ts_boolean_arr;");
        assertTrue(Arrays.equals(new boolean[] { true, false, true }, SharedObject.publicStaticBooleanArray));
        e.eval("SharedObject.publicStaticBooleanArray[0] = false;");
        assertEquals(false, SharedObject.publicStaticBooleanArray[0]);
    }

    @Test
    public void accessFinalFieldBoolean() throws ScriptException {
        e.eval("var pf_boolean = o.publicFinalBoolean;");
        assertEquals(o.publicFinalBoolean, e.get("pf_boolean"));
        assertEquals("boolean", e.eval("typeof pf_boolean;"));
        e.eval("o.publicFinalBoolean = false;");
        assertEquals(true, o.publicFinalBoolean);
    }

    @Test
    public void accessFinalFieldBooleanArray() throws ScriptException {
        e.eval("var pf_boolean_array = o.publicFinalBooleanArray;");
        assertEquals(o.publicFinalBooleanArray[0], e.eval("o.publicFinalBooleanArray[0]"));
        assertTrue(Arrays.equals(o.publicFinalBooleanArray, (boolean[])e.get("pf_boolean_array")));
        e.eval("var tf_boolean_arr = java.lang.reflect.Array.newInstance(java.lang.Boolean.TYPE, 3);" +
                "tf_boolean_arr[0] = false;" +
                "tf_boolean_arr[1] = false;" +
                "tf_boolean_arr[2] = true;" +
                "o.publicOFinalbjectArray = tf_boolean_arr;");
        assertTrue(Arrays.equals(new boolean[] { false, false, true, false }, o.publicFinalBooleanArray));
        e.eval("o.publicFinalBooleanArray[0] = true;");
        assertEquals(true, o.publicFinalBooleanArray[0]);
    }

    @Test
    public void accessStaticFinalFieldBoolean() throws ScriptException {
        e.eval("var psf_boolean = SharedObject.publicStaticFinalBoolean;");
        assertEquals(SharedObject.publicStaticFinalBoolean, e.get("psf_boolean"));
        assertEquals("boolean", e.eval("typeof psf_boolean;"));
        e.eval("SharedObject.publicStaticFinalBoolean = false;");
        assertEquals(true, SharedObject.publicStaticFinalBoolean);
    }

    @Test
    public void accessStaticFinalFieldBooleanArray() throws ScriptException {
        e.eval("var psf_boolean_array = SharedObject.publicStaticFinalBooleanArray;");
        assertEquals(SharedObject.publicStaticFinalBooleanArray[0], e.eval("SharedObject.publicStaticFinalBooleanArray[0]"));
        assertTrue(Arrays.equals(SharedObject.publicStaticFinalBooleanArray, (boolean[])e.get("psf_boolean_array")));
        e.eval("var tsf_boolean_arr = java.lang.reflect.Array.newInstance(java.lang.Boolean.TYPE, 3);" +
                "tsf_boolean_arr[0] = false;" +
                "tsf_boolean_arr[1] = true;" +
                "tsf_boolean_arr[2] = false;" +
                "SharedObject.publicStaticFinalBooleanArray = tsf_boolean_arr;");
        assertTrue(Arrays.equals(new boolean[] { false, true, false, false }, SharedObject.publicStaticFinalBooleanArray));
        e.eval("SharedObject.publicStaticFinalBooleanArray[0] = true;");
        assertEquals(true, SharedObject.publicStaticFinalBooleanArray[0]);
    }

    @Test
    public void accessFieldBooleanBoxing() throws ScriptException {
        e.eval("var p_boolean_box = o.publicBooleanBox;");
        assertEquals(o.publicBooleanBox, e.get("p_boolean_box"));
        assertEquals("boolean", e.eval("typeof p_boolean_box;"));
        e.eval("o.publicBooleanBox = false;");
        assertEquals(false, (boolean)o.publicBooleanBox);
    }

    @Test
    public void accessStaticFieldBooleanBoxing() throws ScriptException {
        e.eval("var ps_boolean_box = SharedObject.publicStaticBooleanBox;");
        assertEquals(SharedObject.publicStaticBooleanBox, e.get("ps_boolean_box"));
        assertEquals("boolean", e.eval("typeof ps_boolean_box;"));
        e.eval("SharedObject.publicStaticBooleanBox = false;");
        assertEquals(false, (boolean)SharedObject.publicStaticBooleanBox);
    }

    @Test
    public void accessFinalFieldBooleanBoxing() throws ScriptException {
        e.eval("var pf_boolean_box = o.publicFinalBooleanBox;");
        assertEquals(o.publicFinalBooleanBox, e.get("pf_boolean_box"));
        assertEquals("boolean", e.eval("typeof pf_boolean_box;"));
        e.eval("o.publicFinalBooleanBox = false;");
        assertEquals(true, (boolean)o.publicFinalBooleanBox);
    }

    @Test
    public void accessStaticFinalFieldBooleanBoxing() throws ScriptException {
        e.eval("var psf_boolean_box = SharedObject.publicStaticFinalBooleanBox;");
        assertEquals(SharedObject.publicStaticFinalBooleanBox, e.get("psf_boolean_box"));
        assertEquals("boolean", e.eval("typeof psf_boolean_box;"));
        e.eval("SharedObject.publicStaticFinalBooleanBox = false;");
        assertEquals(true, (boolean)SharedObject.publicStaticFinalBooleanBox);
    }

    @Test
    public void accessVolatileField() throws ScriptException {
        e.eval("var pv_boolean = o.volatileBoolean;");
        assertEquals(o.volatileBoolean, e.get("pv_boolean"));
        assertEquals("boolean", e.eval("typeof pv_boolean;"));
        e.eval("o.volatileBoolean = false;");
        assertEquals(false, o.volatileBoolean);
    }

    @Test
    public void accessTransientField() throws ScriptException {
        e.eval("var pt_boolean = o.transientBoolean;");
        assertEquals(o.transientBoolean, e.get("pt_boolean"));
        assertEquals("boolean", e.eval("typeof pt_boolean;"));
        e.eval("o.transientBoolean = false;");
        assertEquals(false, o.transientBoolean);
    }

}

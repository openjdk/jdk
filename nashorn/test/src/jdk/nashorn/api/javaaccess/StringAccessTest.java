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
import static org.testng.internal.junit.ArrayAsserts.assertArrayEquals;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.TestNG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @build jdk.nashorn.api.javaaccess.SharedObject jdk.nashorn.api.javaaccess.Person jdk.nashorn.api.javaaccess.StringAccessTest
 * @run testng/othervm jdk.nashorn.api.javaaccess.StringAccessTest
 */
public class StringAccessTest {

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
    public void accessFieldString() throws ScriptException {
        e.eval("var p_string = o.publicString;");
        assertEquals(o.publicString, e.get("p_string"));
        assertEquals("string", e.eval("typeof p_string;"));
        e.eval("o.publicString = 'changedString';");
        assertEquals("changedString", o.publicString);
    }

    @Test
    public void accessFieldStringArray() throws ScriptException {
        e.eval("var p_string_array = o.publicStringArray;");
        assertEquals(o.publicStringArray[0], e.eval("o.publicStringArray[0]"));
        assertArrayEquals(o.publicStringArray, (String[])e.get("p_string_array"));
        e.eval("var t_string_arr = java.lang.reflect.Array.newInstance(java.lang.String.class, 3);" +
                "t_string_arr[0] = 'abc';" +
                "t_string_arr[1] = '123';" +
                "t_string_arr[2] = 'xyzzzz';" +
                "o.publicStringArray = t_string_arr;");
        assertArrayEquals(new String[] { "abc", "123", "xyzzzz" }, o.publicStringArray);
        e.eval("o.publicStringArray[0] = 'nashorn';");
        assertEquals("nashorn", o.publicStringArray[0]);
    }

    @Test
    public void accessStaticFieldString() throws ScriptException {
        e.eval("var ps_string = SharedObject.publicStaticString;");
        assertEquals(SharedObject.publicStaticString, e.get("ps_string"));
        assertEquals("string", e.eval("typeof ps_string;"));
        e.eval("SharedObject.publicStaticString = 'changedString';");
        assertEquals("changedString", SharedObject.publicStaticString);
    }

    @Test
    public void accessStaticFieldStringArray() throws ScriptException {
        e.eval("var ps_string_array = SharedObject.publicStaticStringArray;");
        assertEquals(SharedObject.publicStaticStringArray[0], e.eval("SharedObject.publicStaticStringArray[0]"));
        assertArrayEquals(SharedObject.publicStaticStringArray, (String[])e.get("ps_string_array"));
        e.eval("var ts_string_arr = java.lang.reflect.Array.newInstance(java.lang.String.class, 3);" +
                "ts_string_arr[0] = 'abc';" +
                "ts_string_arr[1] = '123';" +
                "ts_string_arr[2] = 'xyzzzz';" +
                "SharedObject.publicStaticStringArray = ts_string_arr;");
        assertArrayEquals(new String[] { "abc", "123", "xyzzzz" }, SharedObject.publicStaticStringArray);
        e.eval("SharedObject.publicStaticStringArray[0] = 'nashorn';");
        assertEquals("nashorn", SharedObject.publicStaticStringArray[0]);
    }

    @Test
    public void accessFinalFieldString() throws ScriptException {
        e.eval("var pf_string = o.publicFinalString;");
        assertEquals(o.publicFinalString, e.get("pf_string"));
        assertEquals("string", e.eval("typeof pf_string;"));
        e.eval("o.publicFinalString = 'changedString';");
        assertEquals("PublicFinalString", o.publicFinalString);
    }

    @Test
    public void accessFinalFieldStringArray() throws ScriptException {
        e.eval("var pf_string_array = o.publicFinalStringArray;");
        assertEquals(o.publicFinalStringArray[0], e.eval("o.publicFinalStringArray[0]"));
        assertArrayEquals(o.publicFinalStringArray, (String[])e.get("pf_string_array"));
        e.eval("var tf_string_arr = java.lang.reflect.Array.newInstance(java.lang.String.class, 3);" +
                "tf_string_arr[0] = 'abc';" +
                "tf_string_arr[1] = '123';" +
                "tf_string_arr[2] = 'xyzzzz';" +
                "o.publicFinalStringArray = tf_string_arr;");
        assertArrayEquals(new String[] { "FinalArrayString[0]", "FinalArrayString[1]", "FinalArrayString[2]", "FinalArrayString[3]" }, o.publicFinalStringArray);
        e.eval("o.publicFinalStringArray[0] = 'nashorn';");
        assertEquals("nashorn", o.publicFinalStringArray[0]);
    }

    @Test
    public void accessStaticFinalFieldString() throws ScriptException {
        e.eval("var psf_string = SharedObject.publicStaticFinalString;");
        assertEquals(SharedObject.publicStaticFinalString, e.get("psf_string"));
        assertEquals("string", e.eval("typeof psf_string;"));
        e.eval("SharedObject.publicStaticFinalString = 'changedString';");
        assertEquals("PublicStaticFinalString", SharedObject.publicStaticFinalString);
    }

    @Test
    public void accessStaticFinalFieldStringArray() throws ScriptException {
        e.eval("var psf_string_array = SharedObject.publicStaticFinalStringArray;");
        assertEquals(SharedObject.publicStaticFinalStringArray[0], e.eval("SharedObject.publicStaticFinalStringArray[0]"));
        assertArrayEquals(SharedObject.publicStaticFinalStringArray, (String[])e.get("psf_string_array"));
        e.eval("var tsf_string_arr = java.lang.reflect.Array.newInstance(java.lang.String.class, 3);" +
                "tsf_string_arr[0] = 'abc';" +
                "tsf_string_arr[1] = '123';" +
                "tsf_string_arr[2] = 'xyzzzz';" +
                "SharedObject.publicStaticFinalStringArray = tsf_string_arr;");
        assertArrayEquals(new String[] { "StaticFinalArrayString[0]",
                    "StaticFinalArrayString[1]",
                    "StaticFinalArrayString[2]",
                    "StaticFinalArrayString[3]" },
                SharedObject.publicStaticFinalStringArray);
        e.eval("SharedObject.publicStaticFinalStringArray[0] = 'nashorn';");
        assertEquals("nashorn", SharedObject.publicStaticFinalStringArray[0]);
    }

}

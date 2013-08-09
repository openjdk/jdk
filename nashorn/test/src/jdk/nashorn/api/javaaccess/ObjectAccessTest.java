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
 * @build jdk.nashorn.api.javaaccess.SharedObject jdk.nashorn.api.javaaccess.Person jdk.nashorn.api.javaaccess.ObjectAccessTest
 * @run testng/othervm jdk.nashorn.api.javaaccess.ObjectAccessTest
 */
public class ObjectAccessTest {

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
        e.eval("var Person = Packages.jdk.nashorn.api.javaaccess.Person;");
    }

    @Test
    public void accessFieldObject() throws ScriptException {
        e.eval("var p_object = o.publicObject;");
        assertEquals(o.publicObject, e.get("p_object"));
        assertEquals("object", e.eval("typeof p_object;"));
        e.eval("o.publicObject = new Person(14);");
        assertEquals(new Person(14), o.publicObject);
    }

    @Test
    public void accessFieldObjectArray() throws ScriptException {
        e.eval("var p_object_array = o.publicObjectArray;");
        assertEquals(o.publicObjectArray[0], e.eval("o.publicObjectArray[0]"));
        assertArrayEquals(o.publicObjectArray, (Object[])e.get("p_object_array"));
        e.eval("var t_object_arr = new (Java.type(\"jdk.nashorn.api.javaaccess.Person[]\"))(3);" +
                "t_object_arr[0] = new Person(100);" +
                "t_object_arr[1] = new Person(120);" +
                "t_object_arr[2] = new Person(140);" +
                "o.publicObjectArray = t_object_arr;");
        assertArrayEquals(new Person[] { new Person(100), new Person(120), new Person(140) }, o.publicObjectArray);
        e.eval("o.publicObjectArray[0] = new Person(10);");
        assertEquals(new Person(10), o.publicObjectArray[0]);
    }

    @Test
    public void accessStaticFieldObject() throws ScriptException {
        e.eval("var ps_object = SharedObject.publicStaticObject;");
        assertEquals(SharedObject.publicStaticObject, e.get("ps_object"));
        assertEquals("object", e.eval("typeof ps_object;"));
        e.eval("SharedObject.publicStaticObject = new Person(16);");
        assertEquals(new Person(16), SharedObject.publicStaticObject);
    }

    @Test
    public void accessStaticFieldObjectArray() throws ScriptException {
        e.eval("var ps_object_array = SharedObject.publicStaticObjectArray;");
        assertEquals(SharedObject.publicStaticObjectArray[0], e.eval("SharedObject.publicStaticObjectArray[0]"));
        assertArrayEquals(SharedObject.publicStaticObjectArray, (Object[])e.get("ps_object_array"));
        e.eval("var ts_object_arr = new (Java.type(\"jdk.nashorn.api.javaaccess.Person[]\"))(3);" +
                "ts_object_arr[0] = new Person(100);" +
                "ts_object_arr[1] = new Person(120);" +
                "ts_object_arr[2] = new Person(140);" +
                "SharedObject.publicStaticObjectArray = ts_object_arr;");
        assertArrayEquals(new Person[] { new Person(100), new Person(120), new Person(140) }, SharedObject.publicStaticObjectArray);
        e.eval("SharedObject.publicStaticObjectArray[0] = new Person(10);");
        assertEquals(new Person(10), SharedObject.publicStaticObjectArray[0]);
    }

    @Test
    public void accessFinalFieldObject() throws ScriptException {
        e.eval("var pf_object = o.publicFinalObject;");
        assertEquals(o.publicFinalObject, e.get("pf_object"));
        assertEquals("object", e.eval("typeof pf_object;"));
        e.eval("o.publicFinalObject = new Person(-999);");
        assertEquals(new Person(1024), o.publicFinalObject);
    }

    @Test
    public void accessFinalFieldObjectArray() throws ScriptException {
        e.eval("var pf_object_array = o.publicFinalObjectArray;");
        assertEquals(o.publicFinalObjectArray[0], e.eval("o.publicFinalObjectArray[0]"));
        assertArrayEquals(o.publicFinalObjectArray, (Object[])e.get("pf_object_array"));
        e.eval("var tf_object_arr = new (Java.type(\"jdk.nashorn.api.javaaccess.Person[]\"))(3);" +
                "tf_object_arr[0] = new Person(100);" +
                "tf_object_arr[1] = new Person(120);" +
                "tf_object_arr[2] = new Person(140);" +
                "o.publicOFinalbjectArray = tf_object_arr;");
        assertArrayEquals(new Person[] { new Person(-900), new Person(1000), new Person(180) }, o.publicFinalObjectArray);
        e.eval("o.publicFinalObjectArray[0] = new Person(10);");
        assertEquals(new Person(10), o.publicFinalObjectArray[0]);
    }

    @Test
    public void accessStaticFinalFieldObject() throws ScriptException {
        e.eval("var psf_object = SharedObject.publicStaticFinalObject;");
        assertEquals(SharedObject.publicStaticFinalObject, e.get("psf_object"));
        assertEquals("object", e.eval("typeof psf_object;"));
        e.eval("SharedObject.publicStaticFinalObject = new Person(6);");
        assertEquals(new Person(2048), SharedObject.publicStaticFinalObject);
    }

    @Test
    public void accessStaticFinalFieldObjectArray() throws ScriptException {
        e.eval("var psf_object_array = SharedObject.publicStaticFinalObjectArray;");
        assertEquals(SharedObject.publicStaticFinalObjectArray[0], e.eval("SharedObject.publicStaticFinalObjectArray[0]"));
        assertArrayEquals(SharedObject.publicStaticFinalObjectArray, (Object[])e.get("psf_object_array"));
        e.eval("var tsf_object_arr = new (Java.type(\"jdk.nashorn.api.javaaccess.Person[]\"))(3);" +
                "tsf_object_arr[0] = new Person(100);" +
                "tsf_object_arr[1] = new Person(120);" +
                "tsf_object_arr[2] = new Person(140);" +
                "SharedObject.publicStaticFinalObjectArray = tsf_object_arr;");
        assertArrayEquals(new Person[] { new Person(-9), new Person(110), new Person(Integer.MAX_VALUE) }, SharedObject.publicStaticFinalObjectArray);
        e.eval("SharedObject.publicStaticFinalObjectArray[0] = new Person(90);");
        assertEquals(new Person(90), SharedObject.publicStaticFinalObjectArray[0]);
    }

}

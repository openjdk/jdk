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

package jdk.nashorn.api.scripting;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.annotations.Test;

/**
 * Tests to check jdk.nashorn.api.scripting.ScriptObjectMirror API.
 */
public class ScriptObjectMirrorTest {

    @SuppressWarnings("unchecked")
    @Test
    public void reflectionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        e.eval("var obj = { x: 344, y: 'nashorn' }");

        int count = 0;
        Map<Object, Object> map = (Map<Object, Object>) e.get("obj");
        assertFalse(map.isEmpty());
        assertTrue(map.keySet().contains("x"));
        assertTrue(map.containsKey("x"));
        assertTrue(map.values().contains("nashorn"));
        assertTrue(map.containsValue("nashorn"));
        for (final Map.Entry<?, ?> ex : map.entrySet()) {
            final Object key = ex.getKey();
            if (key.equals("x")) {
                assertTrue(344 == ((Number) ex.getValue()).doubleValue());
                count++;
            } else if (key.equals("y")) {
                assertEquals(ex.getValue(), "nashorn");
                count++;
            }
        }
        assertEquals(2, count);
        assertEquals(2, map.size());

        // add property
        map.put("z", "hello");
        assertEquals(e.eval("obj.z"), "hello");
        assertEquals(map.get("z"), "hello");
        assertTrue(map.keySet().contains("z"));
        assertTrue(map.containsKey("z"));
        assertTrue(map.values().contains("hello"));
        assertTrue(map.containsValue("hello"));
        assertEquals(map.size(), 3);

        final Map<Object, Object> newMap = new HashMap<>();
        newMap.put("foo", 23.0);
        newMap.put("bar", true);
        map.putAll(newMap);

        assertEquals(e.eval("obj.foo"), 23.0);
        assertEquals(e.eval("obj.bar"), true);

        // remove using map method
        map.remove("foo");
        assertEquals(e.eval("typeof obj.foo"), "undefined");

        count = 0;
        e.eval("var arr = [ true, 'hello' ]");
        map = (Map<Object, Object>) e.get("arr");
        assertFalse(map.isEmpty());
        assertTrue(map.containsKey("length"));
        assertTrue(map.containsValue("hello"));
        for (final Map.Entry<?, ?> ex : map.entrySet()) {
            final Object key = ex.getKey();
            if (key.equals("0")) {
                assertEquals(ex.getValue(), Boolean.TRUE);
                count++;
            } else if (key.equals("1")) {
                assertEquals(ex.getValue(), "hello");
                count++;
            }
        }
        assertEquals(count, 2);
        assertEquals(map.size(), 2);

        // add element
        map.put("2", "world");
        assertEquals(map.get("2"), "world");
        assertEquals(map.size(), 3);

        // remove all
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(e.eval("typeof arr[0]"), "undefined");
        assertEquals(e.eval("typeof arr[1]"), "undefined");
        assertEquals(e.eval("typeof arr[2]"), "undefined");
    }

    @Test
    public void jsobjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var obj = { '1': 'world', func: function() { return this.bar; }, bar: 'hello' }");
            final ScriptObjectMirror obj = (ScriptObjectMirror) e.get("obj");

            // try basic get on existing properties
            if (!obj.getMember("bar").equals("hello")) {
                fail("obj.bar != 'hello'");
            }

            if (!obj.getSlot(1).equals("world")) {
                fail("obj[1] != 'world'");
            }

            if (!obj.callMember("func", new Object[0]).equals("hello")) {
                fail("obj.func() != 'hello'");
            }

            // try setting properties
            obj.setMember("bar", "new-bar");
            obj.setSlot(1, "new-element-1");
            if (!obj.getMember("bar").equals("new-bar")) {
                fail("obj.bar != 'new-bar'");
            }

            if (!obj.getSlot(1).equals("new-element-1")) {
                fail("obj[1] != 'new-element-1'");
            }

            // try adding properties
            obj.setMember("prop", "prop-value");
            obj.setSlot(12, "element-12");
            if (!obj.getMember("prop").equals("prop-value")) {
                fail("obj.prop != 'prop-value'");
            }

            if (!obj.getSlot(12).equals("element-12")) {
                fail("obj[12] != 'element-12'");
            }

            // delete properties
            obj.removeMember("prop");
            if ("prop-value".equals(obj.getMember("prop"))) {
                fail("obj.prop is not deleted!");
            }

            // Simple eval tests
            assertEquals(obj.eval("typeof Object"), "function");
            assertEquals(obj.eval("'nashorn'.substring(3)"), "horn");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void scriptObjectMirrorToStringTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            final Object obj = e.eval("new TypeError('wrong type')");
            assertEquals(obj.toString(), "TypeError: wrong type", "toString returns wrong value");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        try {
            final Object obj = e.eval("function func() { print('hello'); }");
            assertEquals(obj.toString(), "function func() { print('hello'); }", "toString returns wrong value");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }
    }

    @Test
    public void mirrorNewObjectGlobalFunctionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final ScriptEngine e2 = m.getEngineByName("nashorn");

        e.eval("function func() {}");
        e2.put("foo", e.get("func"));
        final ScriptObjectMirror e2global = (ScriptObjectMirror)e2.eval("this");
        final Object newObj = ((ScriptObjectMirror)e2global.getMember("foo")).newObject();
        assertTrue(newObj instanceof ScriptObjectMirror);
    }

    @Test
    public void mirrorNewObjectInstanceFunctionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final ScriptEngine e2 = m.getEngineByName("nashorn");

        e.eval("function func() {}");
        e2.put("func", e.get("func"));
        final ScriptObjectMirror e2obj = (ScriptObjectMirror)e2.eval("({ foo: func })");
        final Object newObj = ((ScriptObjectMirror)e2obj.getMember("foo")).newObject();
        assertTrue(newObj instanceof ScriptObjectMirror);
    }

    @Test
    public void indexPropertiesExternalBufferTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final ScriptObjectMirror obj = (ScriptObjectMirror)e.eval("var obj = {}; obj");
        final ByteBuffer buf = ByteBuffer.allocate(5);
        int i;
        for (i = 0; i < 5; i++) {
            buf.put(i, (byte)(i+10));
        }
        obj.setIndexedPropertiesToExternalArrayData(buf);

        for (i = 0; i < 5; i++) {
            assertEquals((byte)(i+10), ((Number)e.eval("obj[" + i + "]")).byteValue());
        }

        e.eval("for (i = 0; i < 5; i++) obj[i] = 0");
        for (i = 0; i < 5; i++) {
            assertEquals((byte)0, ((Number)e.eval("obj[" + i + "]")).byteValue());
            assertEquals((byte)0, buf.get(i));
        }
    }

    @Test
    public void conversionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final ScriptObjectMirror arr = (ScriptObjectMirror)e.eval("[33, 45, 23]");
        final int[] intArr = arr.to(int[].class);
        assertEquals(intArr[0], 33);
        assertEquals(intArr[1], 45);
        assertEquals(intArr[2], 23);

        final List<?> list = arr.to(List.class);
        assertEquals(list.get(0), 33);
        assertEquals(list.get(1), 45);
        assertEquals(list.get(2), 23);

        ScriptObjectMirror obj = (ScriptObjectMirror)e.eval(
            "({ valueOf: function() { return 42 } })");
        assertEquals(Double.valueOf(42.0), obj.to(Double.class));

        obj = (ScriptObjectMirror)e.eval(
            "({ toString: function() { return 'foo' } })");
        assertEquals("foo", obj.to(String.class));
    }

    // @bug 8044000: Access to undefined property yields "null" instead of "undefined"
    @Test
    public void mapScriptObjectMirrorCallsiteTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName("nashorn");
        final String TEST_SCRIPT = "typeof obj.foo";

        final Bindings global = engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE);
        engine.eval("var obj = java.util.Collections.emptyMap()");
        // this will drive callsite "obj.foo" of TEST_SCRIPT
        // to use "obj instanceof Map" as it's guard
        engine.eval(TEST_SCRIPT, global);
        // redefine 'obj' to be a script object
        engine.eval("obj = {}");

        final Bindings newGlobal = engine.createBindings();
        // transfer 'obj' from default global to new global
        // new global will get a ScriptObjectMirror wrapping 'obj'
        newGlobal.put("obj", global.get("obj"));

        // Every ScriptObjectMirror is a Map! If callsite "obj.foo"
        // does not see the new 'obj' is a ScriptObjectMirror, it'll
        // continue to use Map's get("obj.foo") instead of ScriptObjectMirror's
        // getMember("obj.foo") - thereby getting null instead of undefined
        assertEquals("undefined", engine.eval(TEST_SCRIPT, newGlobal));
    }
}

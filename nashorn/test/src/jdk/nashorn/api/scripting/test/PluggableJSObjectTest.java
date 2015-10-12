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

package jdk.nashorn.api.scripting.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.nio.IntBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.testng.annotations.Test;

/**
 * Tests for pluggable external impls. of jdk.nashorn.api.scripting.JSObject.
 *
 * JDK-8024615: Refactor ScriptObjectMirror and JSObject to support external
 * JSObject implementations.
 */
@SuppressWarnings("javadoc")
public class PluggableJSObjectTest {
    public static class MapWrapperObject extends AbstractJSObject {
        private final HashMap<String, Object> map = new LinkedHashMap<>();

        public HashMap<String, Object> getMap() {
            return map;
        }

        @Override
        public Object getMember(final String name) {
            return map.get(name);
        }

        @Override
        public void setMember(final String name, final Object value) {
            map.put(name, value);
        }

        @Override
        public boolean hasMember(final String name) {
            return map.containsKey(name);
        }

        @Override
        public void removeMember(final String name) {
            map.remove(name);
        }

        @Override
        public Set<String> keySet() {
            return map.keySet();
        }

        @Override
        public Collection<Object> values() {
            return map.values();
        }
    }

    @Test
    // Named property access on a JSObject
    public void namedAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            final MapWrapperObject obj = new MapWrapperObject();
            e.put("obj", obj);
            obj.getMap().put("foo", "bar");

            // property-like access on MapWrapperObject objects
            assertEquals(e.eval("obj.foo"), "bar");
            e.eval("obj.foo = 'hello'");
            assertEquals(e.eval("'foo' in obj"), Boolean.TRUE);
            assertEquals(e.eval("obj.foo"), "hello");
            assertEquals(obj.getMap().get("foo"), "hello");
            e.eval("delete obj.foo");
            assertFalse(obj.getMap().containsKey("foo"));
            assertEquals(e.eval("'foo' in obj"), Boolean.FALSE);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    // @bug 8062030: Nashorn bug retrieving array property after key string concatenation
    @Test
    // ConsString attribute access on a JSObject
    public void consStringTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            final MapWrapperObject obj = new MapWrapperObject();
            e.put("obj", obj);
            e.put("f", "f");
            e.eval("obj[f + 'oo'] = 'bar';");

            assertEquals(obj.getMap().get("foo"), "bar");
            assertEquals(e.eval("obj[f + 'oo']"), "bar");
            assertEquals(e.eval("obj['foo']"), "bar");
            assertEquals(e.eval("f + 'oo' in obj"), Boolean.TRUE);
            assertEquals(e.eval("'foo' in obj"), Boolean.TRUE);
            e.eval("delete obj[f + 'oo']");
            assertFalse(obj.getMap().containsKey("foo"));
            assertEquals(e.eval("obj[f + 'oo']"), null);
            assertEquals(e.eval("obj['foo']"), null);
            assertEquals(e.eval("f + 'oo' in obj"), Boolean.FALSE);
            assertEquals(e.eval("'foo' in obj"), Boolean.FALSE);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    public static class BufferObject extends AbstractJSObject {
        private final IntBuffer buf;

        public BufferObject(final int size) {
            buf = IntBuffer.allocate(size);
        }

        public IntBuffer getBuffer() {
            return buf;
        }

        @Override
        public Object getMember(final String name) {
            return name.equals("length")? buf.capacity() : null;
        }

        @Override
        public boolean hasSlot(final int i) {
            return i > -1 && i < buf.capacity();
        }

        @Override
        public Object getSlot(final int i) {
            return buf.get(i);
        }

        @Override
        public void setSlot(final int i, final Object value) {
            buf.put(i, ((Number)value).intValue());
        }

        @Override
        public boolean isArray() {
            return true;
        }
    }

    @Test
    // array-like indexed access for a JSObject
    public void indexedAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            final BufferObject buf = new BufferObject(2);
            e.put("buf", buf);

            // array-like access on BufferObject objects
            assertEquals(e.eval("buf.length"), buf.getBuffer().capacity());
            e.eval("buf[0] = 23");
            assertEquals(buf.getBuffer().get(0), 23);
            assertEquals(e.eval("buf[0]"), 23);
            assertEquals(e.eval("buf[1]"), 0);
            buf.getBuffer().put(1, 42);
            assertEquals(e.eval("buf[1]"), 42);
            assertEquals(e.eval("Array.isArray(buf)"), Boolean.TRUE);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    public static class Adder extends AbstractJSObject {
        @Override
        public Object call(final Object thiz, final Object... args) {
            double res = 0.0;
            for (final Object arg : args) {
                res += ((Number)arg).doubleValue();
            }
            return res;
        }

        @Override
        public boolean isFunction() {
            return true;
        }
    }

    @Test
    // a callable JSObject
    public void callableJSObjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.put("sum", new Adder());
            // check callability of Adder objects
            assertEquals(e.eval("typeof sum"), "function");
            assertEquals(((Number)e.eval("sum(1, 2, 3, 4, 5)")).intValue(), 15);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    public static class Factory extends AbstractJSObject {
        @SuppressWarnings("unused")
        @Override
        public Object newObject(final Object... args) {
            return new HashMap<Object, Object>();
        }

        @Override
        public boolean isFunction() {
            return true;
        }
    }

    @Test
    // a factory JSObject
    public void factoryJSObjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.put("Factory", new Factory());

            // check new on Factory
            assertEquals(e.eval("typeof Factory"), "function");
            assertEquals(e.eval("typeof new Factory()"), "object");
            assertEquals(e.eval("(new Factory()) instanceof java.util.Map"), Boolean.TRUE);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    // iteration tests
    public void iteratingJSObjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            final MapWrapperObject obj = new MapWrapperObject();
            obj.setMember("foo", "hello");
            obj.setMember("bar", "world");
            e.put("obj", obj);

            // check for..in
            Object val = e.eval("var str = ''; for (i in obj) str += i; str");
            assertEquals(val.toString(), "foobar");

            // check for..each..in
            val = e.eval("var str = ''; for each (i in obj) str += i; str");
            assertEquals(val.toString(), "helloworld");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    // @bug 8137258: JSObjectLinker and BrowserJSObjectLinker should not expose internal JS objects
    @Test
    public void hidingInternalObjectsForJSObjectTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName("nashorn");

        final String code = "function func(obj) { obj.foo = [5, 5]; obj.bar = {} }";
        e.eval(code);

        // call the exposed function but pass user defined JSObject impl as argument
        ((Invocable)e).invokeFunction("func", new AbstractJSObject() {
            @Override
            public void setMember(final String name, final Object value) {
                // make sure that wrapped objects are passed (and not internal impl. objects)
                assertTrue(value.getClass() == ScriptObjectMirror.class);
            }
        });
    }
}

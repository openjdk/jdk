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

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import org.testng.Assert;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;

/**
 * Tests for jsr223 Bindings "scope" (engine, global scopes)
 */
public class ScopeTest {

    @Test
    public void createBindingsTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        Bindings b = e.createBindings();
        b.put("foo", 42.0);
        Object res = null;
        try {
            res = e.eval("foo == 42.0", b);
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        assertEquals(res, Boolean.TRUE);
    }

    @Test
    public void engineScopeTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        Bindings engineScope = e.getBindings(ScriptContext.ENGINE_SCOPE);

        // check few ECMA standard built-in global properties
        assertNotNull(engineScope.get("Object"));
        assertNotNull(engineScope.get("TypeError"));
        assertNotNull(engineScope.get("eval"));

        // can access via ScriptEngine.get as well
        assertNotNull(e.get("Object"));
        assertNotNull(e.get("TypeError"));
        assertNotNull(e.get("eval"));

        // Access by either way should return same object
        assertEquals(engineScope.get("Array"), e.get("Array"));
        assertEquals(engineScope.get("EvalError"), e.get("EvalError"));
        assertEquals(engineScope.get("undefined"), e.get("undefined"));

        // try exposing a new variable from scope
        engineScope.put("myVar", "foo");
        try {
            assertEquals(e.eval("myVar"), "foo");
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        // update "myVar" in script an check the value from scope
        try {
            e.eval("myVar = 'nashorn';");
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        // now check modified value from scope and engine
        assertEquals(engineScope.get("myVar"), "nashorn");
        assertEquals(e.get("myVar"), "nashorn");
    }

    @Test
    public void multiGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        try {
            Object obj1 = e.eval("Object");
            Object obj2 = e.eval("Object", newCtxt);
            Assert.assertNotEquals(obj1, obj2);
            Assert.assertNotNull(obj1);
            Assert.assertNotNull(obj2);
            Assert.assertEquals(obj1.toString(), obj2.toString());

            e.eval("x = 'hello'");
            e.eval("x = 'world'", newCtxt);
            Object x1 = e.getContext().getAttribute("x");
            Object x2 = newCtxt.getAttribute("x");
            Assert.assertNotEquals(x1, x2);
            Assert.assertEquals(x1, "hello");
            Assert.assertEquals(x2, "world");

            x1 = e.eval("x");
            x2 = e.eval("x", newCtxt);
            Assert.assertNotEquals(x1, x2);
            Assert.assertEquals(x1, "hello");
            Assert.assertEquals(x2, "world");

            final ScriptContext origCtxt = e.getContext();
            e.setContext(newCtxt);
            e.eval("y = new Object()");
            e.eval("y = new Object()", origCtxt);

            Object y1 = origCtxt.getAttribute("y");
            Object y2 = newCtxt.getAttribute("y");
            Assert.assertNotEquals(y1, y2);
            Assert.assertNotEquals(e.eval("y"), e.eval("y", origCtxt));
            Assert.assertEquals("[object Object]", y1.toString());
            Assert.assertEquals("[object Object]", y2.toString());
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

    @Test
    public void userEngineScopeBindingsTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.eval("function func() {}");

        final ScriptContext newContext = new SimpleScriptContext();
        newContext.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
        // we are using a new bindings - so it should have 'func' defined
        Object value = e.eval("typeof func", newContext);
        assertTrue(value.equals("undefined"));
    }

    @Test
    public void userEngineScopeBindingsNoLeakTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final ScriptContext newContext = new SimpleScriptContext();
        newContext.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
        e.eval("function foo() {}", newContext);

        // in the default context's ENGINE_SCOPE, 'foo' shouldn't exist
        assertTrue(e.eval("typeof foo").equals("undefined"));
    }

    @Test
    public void userEngineScopeBindingsRetentionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final ScriptContext newContext = new SimpleScriptContext();
        newContext.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
        e.eval("function foo() {}", newContext);

        // definition retained with user's ENGINE_SCOPE Binding
        assertTrue(e.eval("typeof foo", newContext).equals("function"));

        final Bindings oldBindings = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
        // but not in another ENGINE_SCOPE binding
        newContext.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
        assertTrue(e.eval("typeof foo", newContext).equals("undefined"));

        // restore ENGINE_SCOPE and check again
        newContext.setBindings(oldBindings, ScriptContext.ENGINE_SCOPE);
        assertTrue(e.eval("typeof foo", newContext).equals("function"));
    }

    @Test
    // check that engine.js definitions are visible in all new global instances
    public void checkBuiltinsInNewBindingsTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        // check default global instance has engine.js definitions
        final Bindings g = (Bindings) e.eval("this");
        Object value = g.get("__noSuchProperty__");
        assertTrue(value instanceof ScriptObjectMirror && ((ScriptObjectMirror)value).isFunction());
        value = g.get("print");
        assertTrue(value instanceof ScriptObjectMirror && ((ScriptObjectMirror)value).isFunction());

        // check new global instance created has engine.js definitions
        Bindings b = e.createBindings();
        value = b.get("__noSuchProperty__");
        assertTrue(value instanceof ScriptObjectMirror && ((ScriptObjectMirror)value).isFunction());
        value = b.get("print");
        assertTrue(value instanceof ScriptObjectMirror && ((ScriptObjectMirror)value).isFunction());

        // put a mapping into GLOBAL_SCOPE
        final Bindings globalScope = e.getContext().getBindings(ScriptContext.GLOBAL_SCOPE);
        globalScope.put("x", "hello");

        // GLOBAL_SCOPE mapping should be visible from default ScriptContext eval
        assertTrue(e.eval("x").equals("hello"));

        final ScriptContext ctx = new SimpleScriptContext();
        ctx.setBindings(globalScope, ScriptContext.GLOBAL_SCOPE);
        ctx.setBindings(b, ScriptContext.ENGINE_SCOPE);

        // GLOBAL_SCOPE mapping should be visible from non-default ScriptContext eval
        assertTrue(e.eval("x", ctx).equals("hello"));

        // try some arbitray Bindings for ENGINE_SCOPE
        Bindings sb = new SimpleBindings();
        ctx.setBindings(sb, ScriptContext.ENGINE_SCOPE);

        // GLOBAL_SCOPE mapping should be visible from non-default ScriptContext eval
        assertTrue(e.eval("x", ctx).equals("hello"));

        // engine.js builtins are still defined even with arbitrary Bindings
        assertTrue(e.eval("typeof print", ctx).equals("function"));
        assertTrue(e.eval("typeof __noSuchProperty__", ctx).equals("function"));

        // ENGINE_SCOPE definition should 'hide' GLOBAL_SCOPE definition
        sb.put("x", "newX");
        assertTrue(e.eval("x", ctx).equals("newX"));
    }
}

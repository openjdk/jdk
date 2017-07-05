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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for jsr223 Bindings "scope" (engine, global scopes)
 */
@SuppressWarnings("javadoc")
public class ScopeTest {

    @Test
    public void createBindingsTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
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
        final Bindings engineScope = e.getBindings(ScriptContext.ENGINE_SCOPE);

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
            final Object obj1 = e.eval("Object");
            final Object obj2 = e.eval("Object", newCtxt);
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

            final Object y1 = origCtxt.getAttribute("y");
            final Object y2 = newCtxt.getAttribute("y");
            Assert.assertNotEquals(y1, y2);
            final Object yeval1 = e.eval("y");
            final Object yeval2 = e.eval("y", origCtxt);
            Assert.assertNotEquals(yeval1, yeval2);
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
        final Object value = e.eval("typeof func", newContext);
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
        final Bindings b = e.createBindings();
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
        final Bindings sb = new SimpleBindings();
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

    /**
     * Test multi-threaded access to defined global variables for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedVarTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        final String sharedScript = "foo";

        assertEquals(e.eval("var foo = 'original context';", origContext), null);
        assertEquals(e.eval("var foo = 'new context';", newCtxt), null);

        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "new context", 1000));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(e.eval("var foo = 'newer context';", newCtxt), null);
        final Thread t3 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t4 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "newer context", 1000));

        t3.start();
        t4.start();
        t3.join();
        t4.join();

        assertEquals(e.eval(sharedScript), "original context");
        assertEquals(e.eval(sharedScript, newCtxt), "newer context");
    }

    /**
     * Test multi-threaded access to undefined global variables for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedGlobalTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        assertEquals(e.eval("foo = 'original context';", origContext), "original context");
        assertEquals(e.eval("foo = 'new context';", newCtxt), "new context");
        final String sharedScript = "foo";

        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "new context", 1000));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        final Object obj3 = e.eval("delete foo; foo = 'newer context';", newCtxt);
        assertEquals(obj3, "newer context");
        final Thread t3 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t4 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "newer context", 1000));

        t3.start();
        t4.start();
        t3.join();
        t4.join();

        Assert.assertEquals(e.eval(sharedScript), "original context");
        Assert.assertEquals(e.eval(sharedScript, newCtxt), "newer context");
    }

    /**
     * Test multi-threaded access using the postfix ++ operator for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedIncTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        assertEquals(e.eval("var x = 0;", origContext), null);
        assertEquals(e.eval("var x = 2;", newCtxt), null);
        final String sharedScript = "x++;";

        final Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 1000; i++) {
                        assertEquals(e.eval(sharedScript, origContext), (double)i);
                    }
                } catch (final ScriptException se) {
                    fail(se.toString());
                }
            }
        });
        final Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 2; i < 1000; i++) {
                        assertEquals(e.eval(sharedScript, newCtxt), (double)i);
                    }
                } catch (final ScriptException se) {
                    fail(se.toString());
                }
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    /**
     * Test multi-threaded access to primitive prototype properties for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedPrimitiveTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        final Object obj1 = e.eval("String.prototype.foo = 'original context';", origContext);
        final Object obj2 = e.eval("String.prototype.foo = 'new context';", newCtxt);
        assertEquals(obj1, "original context");
        assertEquals(obj2, "new context");
        final String sharedScript = "''.foo";

        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "new context", 1000));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        final Object obj3 = e.eval("delete String.prototype.foo; Object.prototype.foo = 'newer context';", newCtxt);
        assertEquals(obj3, "newer context");
        final Thread t3 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t4 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "newer context", 1000));

        t3.start();
        t4.start();
        t3.join();
        t4.join();

        Assert.assertEquals(e.eval(sharedScript), "original context");
        Assert.assertEquals(e.eval(sharedScript, newCtxt), "newer context");
    }


    /**
     * Test multi-threaded access to prototype user accessor properties for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedAccessorTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        e.eval("Object.defineProperty(Object.prototype, 'foo', { get: function() 'original context' })", origContext);
        e.eval("Object.defineProperty(Object.prototype, 'foo', { get: function() 'new context', configurable: true })", newCtxt);
        final String sharedScript = "({}).foo";

        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "new context", 1000));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        final Object obj3 = e.eval("delete Object.prototype.foo; Object.prototype.foo = 'newer context';", newCtxt);
        assertEquals(obj3, "newer context");
        final Thread t3 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t4 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "newer context", 1000));

        t3.start();
        t4.start();
        t3.join();
        t4.join();
    }

    /**
     * Test multi-threaded access to primitive prototype user accessor properties for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedPrimitiveAccessorTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        e.eval("Object.defineProperty(String.prototype, 'foo', { get: function() 'original context' })", origContext);
        e.eval("Object.defineProperty(String.prototype, 'foo', { get: function() 'new context' })", newCtxt);
        final String sharedScript = "''.foo";

        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "new context", 1000));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        final Object obj3 = e.eval("delete String.prototype.foo; Object.prototype.foo = 'newer context';", newCtxt);
        assertEquals(obj3, "newer context");
        final Thread t3 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t4 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "newer context", 1000));

        t3.start();
        t4.start();
        t3.join();
        t4.join();
    }

    /**
     * Test multi-threaded scope function invocation for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedFunctionTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        e.eval(new URLReader(ScopeTest.class.getResource("resources/func.js")), origContext);
        assertEquals(origContext.getAttribute("scopeVar"), 1);
        assertEquals(e.eval("scopeTest()"), 1);

        e.eval(new URLReader(ScopeTest.class.getResource("resources/func.js")), newCtxt);
        assertEquals(newCtxt.getAttribute("scopeVar"), 1);
        assertEquals(e.eval("scopeTest();", newCtxt), 1);

        assertEquals(e.eval("scopeVar = 3;", newCtxt), 3);
        assertEquals(newCtxt.getAttribute("scopeVar"), 3);


        final Thread t1 = new Thread(new ScriptRunner(e, origContext, "scopeTest()", 1, 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, "scopeTest()", 3, 1000));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

    }

    /**
     * Test multi-threaded access to global getters and setters for shared script classes with multiple globals.
     */
    @Test
    public static void getterSetterTest() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        final String sharedScript = "accessor1";

        e.eval(new URLReader(ScopeTest.class.getResource("resources/gettersetter.js")), origContext);
        assertEquals(e.eval("accessor1 = 1;"), 1);
        assertEquals(e.eval(sharedScript), 1);

        e.eval(new URLReader(ScopeTest.class.getResource("resources/gettersetter.js")), newCtxt);
        assertEquals(e.eval("accessor1 = 2;", newCtxt), 2);
        assertEquals(e.eval(sharedScript, newCtxt), 2);


        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, 1, 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, 2, 1000));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(e.eval(sharedScript), 1);
        assertEquals(e.eval(sharedScript, newCtxt), 2);
        assertEquals(e.eval("v"), 1);
        assertEquals(e.eval("v", newCtxt), 2);
    }

    /**
     * Test multi-threaded access to global getters and setters for shared script classes with multiple globals.
     */
    @Test
    public static void getterSetter2Test() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        final String sharedScript = "accessor2";

        e.eval(new URLReader(ScopeTest.class.getResource("resources/gettersetter.js")), origContext);
        assertEquals(e.eval("accessor2 = 1;"), 1);
        assertEquals(e.eval(sharedScript), 1);

        e.eval(new URLReader(ScopeTest.class.getResource("resources/gettersetter.js")), newCtxt);
        assertEquals(e.eval("accessor2 = 2;", newCtxt), 2);
        assertEquals(e.eval(sharedScript, newCtxt), 2);


        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, 1, 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, 2, 1000));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(e.eval(sharedScript), 1);
        assertEquals(e.eval(sharedScript, newCtxt), 2);
        assertEquals(e.eval("x"), 1);
        assertEquals(e.eval("x", newCtxt), 2);
    }

    // @bug 8058422: Users should be able to overwrite "context" and "engine" variables
    @Test
    public static void contextOverwriteTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = new SimpleBindings();
        b.put("context", "hello");
        b.put("foo", 32);
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        e.setContext(newCtxt);
        assertEquals(e.eval("context"), "hello");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    // @bug 8058422: Users should be able to overwrite "context" and "engine" variables
    @Test
    public static void contextOverwriteInScriptTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.put("foo", 32);

        assertEquals(((Number)e.eval("foo")).intValue(), 32);
        assertEquals(e.eval("context = 'bar'"), "bar");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    // @bug 8058422: Users should be able to overwrite "context" and "engine" variables
    @Test
    public static void engineOverwriteTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Bindings b = new SimpleBindings();
        b.put("engine", "hello");
        b.put("foo", 32);
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        e.setContext(newCtxt);
        assertEquals(e.eval("engine"), "hello");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    // @bug 8058422: Users should be able to overwrite "context" and "engine" variables
    @Test
    public static void engineOverwriteInScriptTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.put("foo", 32);

        assertEquals(((Number)e.eval("foo")).intValue(), 32);
        assertEquals(e.eval("engine = 'bar'"), "bar");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    // @bug 8044750: megamorphic getter for scope objects does not call __noSuchProperty__ hook
    @Test
    public static void testMegamorphicGetInGlobal() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName("nashorn");
        final String script = "foo";
        // "foo" is megamorphic because of different global scopes.
        // Make sure ScriptContext variable search works even after
        // it becomes megamorphic.
        for (int index = 0; index < 25; index++) {
            final Bindings bindings = new SimpleBindings();
            bindings.put("foo", index);
            final Number value = (Number)engine.eval(script, bindings);
            assertEquals(index, value.intValue());
        }
    }

    /**
     * Test "slow" scopes involving {@code with} and {@code eval} statements for shared script classes with multiple globals.
     * @throws ScriptException
     * @throws InterruptedException
     */
    @Test
    public static void testSlowScope() throws ScriptException, InterruptedException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        for (int i = 0; i < 100; i++) {
            final Bindings b = e.createBindings();
            final ScriptContext ctxt = new SimpleScriptContext();
            ctxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

            e.eval(new URLReader(ScopeTest.class.getResource("resources/witheval.js")), ctxt);
            assertEquals(e.eval("a", ctxt), 1);
            assertEquals(b.get("a"), 1);
            assertEquals(e.eval("b", ctxt), 3);
            assertEquals(b.get("b"), 3);
            assertEquals(e.eval("c", ctxt), 10);
            assertEquals(b.get("c"), 10);
        }
    }

    private static class ScriptRunner implements Runnable {

        final ScriptEngine engine;
        final ScriptContext context;
        final String source;
        final Object expected;
        final int iterations;

        ScriptRunner(final ScriptEngine engine, final ScriptContext context, final String source, final Object expected, final int iterations) {
            this.engine = engine;
            this.context = context;
            this.source = source;
            this.expected = expected;
            this.iterations = iterations;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < iterations; i++) {
                    assertEquals(engine.eval(source, context), expected);
                }
            } catch (final ScriptException se) {
                throw new RuntimeException(se);
            }
        }
    }

}

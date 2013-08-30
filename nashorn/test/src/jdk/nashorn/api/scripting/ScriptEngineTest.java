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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for JSR-223 script engine for Nashorn.
 *
 * @test
 * @build jdk.nashorn.api.scripting.Window jdk.nashorn.api.scripting.WindowEventHandler jdk.nashorn.api.scripting.VariableArityTestInterface jdk.nashorn.api.scripting.ScriptEngineTest
 * @run testng jdk.nashorn.api.scripting.ScriptEngineTest
 */
public class ScriptEngineTest {

    private void log(String msg) {
        org.testng.Reporter.log(msg, true);
    }

    @Test
    public void argumentsTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        String[] args = new String[] { "hello", "world" };
        try {
            e.put("arguments", args);
            Object arg0 = e.eval("arguments[0]");
            Object arg1 = e.eval("arguments[1]");
            assertEquals(args[0], arg0);
            assertEquals(args[1], arg1);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void argumentsWithTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        String[] args = new String[] { "hello", "world" };
        try {
            e.put("arguments", args);
            Object arg0 = e.eval("var imports = new JavaImporter(java.io); " +
                    " with(imports) { arguments[0] }");
            Object arg1 = e.eval("var imports = new JavaImporter(java.util, java.io); " +
                    " with(imports) { arguments[1] }");
            assertEquals(args[0], arg0);
            assertEquals(args[1], arg1);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void argumentsEmptyTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            assertEquals(e.eval("arguments instanceof Array"), true);
            assertEquals(e.eval("arguments.length == 0"), true);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void factoryTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        assertNotNull(e);

        final ScriptEngineFactory fac = e.getFactory();

        assertEquals(fac.getLanguageName(), "ECMAScript");
        assertEquals(fac.getParameter(ScriptEngine.NAME), "javascript");
        assertEquals(fac.getLanguageVersion(), "ECMA - 262 Edition 5.1");
        assertEquals(fac.getEngineName(), "Oracle Nashorn");
        assertEquals(fac.getOutputStatement("context"), "print(context)");
        assertEquals(fac.getProgram("print('hello')", "print('world')"), "print('hello');print('world');");
        assertEquals(fac.getParameter(ScriptEngine.NAME), "javascript");

        boolean seenJS = false;
        for (String ext : fac.getExtensions()) {
            if (ext.equals("js")) {
                seenJS = true;
            }
        }

        assertEquals(seenJS, true);
        String str = fac.getMethodCallSyntax("obj", "foo", "x");
        assertEquals(str, "obj.foo(x)");

        boolean seenNashorn = false, seenJavaScript = false, seenECMAScript = false;
        for (String name : fac.getNames()) {
            switch (name) {
                case "nashorn": seenNashorn = true; break;
                case "javascript": seenJavaScript = true; break;
                case "ECMAScript": seenECMAScript = true; break;
            }
        }

        assertTrue(seenNashorn);
        assertTrue(seenJavaScript);
        assertTrue(seenECMAScript);

        boolean seenAppJS = false, seenAppECMA = false, seenTextJS = false, seenTextECMA = false;
        for (String mime : fac.getMimeTypes()) {
            switch (mime) {
                case "application/javascript": seenAppJS = true; break;
                case "application/ecmascript": seenAppECMA = true; break;
                case "text/javascript": seenTextJS = true; break;
                case "text/ecmascript": seenTextECMA = true; break;
            }
        }

        assertTrue(seenAppJS);
        assertTrue(seenAppECMA);
        assertTrue(seenTextJS);
        assertTrue(seenTextECMA);
    }

    @Test
    public void evalTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.put(ScriptEngine.FILENAME, "myfile.js");

        try {
            e.eval("print('hello')");
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }
        try {
            e.eval("print('hello)");
            fail("script exception expected");
        } catch (final ScriptException se) {
            assertEquals(se.getLineNumber(), 1);
            assertEquals(se.getColumnNumber(), 13);
            assertEquals(se.getFileName(), "myfile.js");
            // se.printStackTrace();
        }

        try {
            Object obj = e.eval("34 + 41");
            assertTrue(34.0 + 41.0 == ((Number)obj).doubleValue());
            obj = e.eval("x = 5");
            assertTrue(5.0 == ((Number)obj).doubleValue());
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

    @Test
    public void compileTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        CompiledScript script = null;

        try {
            script = ((Compilable)e).compile("print('hello')");
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }

        try {
            script.eval();
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        // try to compile from a Reader
        try {
            script = ((Compilable)e).compile(new StringReader("print('world')"));
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }

        try {
            script.eval();
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

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
    public void getInterfaceTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Invocable inv = (Invocable)e;

        // try to get interface from global functions
        try {
            e.eval("function run() { print('run'); };");
            final Runnable runnable = inv.getInterface(Runnable.class);
            runnable.run();
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }

        // try interface on specific script object
        try {
            e.eval("var obj = { run: function() { print('run from obj'); } };");
            Object obj = e.get("obj");
            final Runnable runnable = inv.getInterface(obj, Runnable.class);
            runnable.run();
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    public interface Foo {
        public void bar();
    }

    public interface Foo2 extends Foo {
        public void bar2();
    }

    @Test
    public void getInterfaceMissingTest() {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine engine = manager.getEngineByName("nashorn");

        // don't define any function.
        try {
            engine.eval("");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }

        Runnable runnable = ((Invocable)engine).getInterface(Runnable.class);
        if (runnable != null) {
            fail("runnable is not null!");
        }

        // now define "run"
        try {
            engine.eval("function run() { print('this is run function'); }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
        runnable = ((Invocable)engine).getInterface(Runnable.class);
        // should not return null now!
        runnable.run();

        // define only one method of "Foo2"
        try {
            engine.eval("function bar() { print('bar function'); }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }

        Foo2 foo2 = ((Invocable)engine).getInterface(Foo2.class);
        if (foo2 != null) {
            throw new RuntimeException("foo2 is not null!");
        }

        // now define other method of "Foo2"
        try {
            engine.eval("function bar2() { print('bar2 function'); }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
        foo2 = ((Invocable)engine).getInterface(Foo2.class);
        foo2.bar();
        foo2.bar2();
    }

    @Test
    /**
     * Try passing non-interface Class object for interface implementation.
     */
    public void getNonInterfaceGetInterfaceTest() {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine engine = manager.getEngineByName("nashorn");
        try {
            log(Objects.toString(((Invocable)engine).getInterface(Object.class)));
            fail("Should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (! (exp instanceof IllegalArgumentException)) {
                fail("IllegalArgumentException expected, got " + exp);
            }
        }
    }

    @Test
    /**
     * Check that we can get interface out of a script object even after
     * switching to use different ScriptContext.
     */
    public void getInterfaceDifferentContext() {
       ScriptEngineManager m = new ScriptEngineManager();
       ScriptEngine e = m.getEngineByName("nashorn");
       try {
           Object obj = e.eval("({ run: function() { } })");

           // change script context
           ScriptContext ctxt = new SimpleScriptContext();
           ctxt.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
           e.setContext(ctxt);

           Runnable r = ((Invocable)e).getInterface(obj, Runnable.class);
           r.run();
       }catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
       }
    }

    @Test
    /**
     * Check that getInterface on non-script object 'thiz' results in IllegalArgumentException.
     */
    public void getInterfaceNonScriptObjectThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable)e).getInterface(new Object(), Runnable.class);
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (! (exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that getInterface on null 'thiz' results in IllegalArgumentException.
     */
    public void getInterfaceNullThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable)e).getInterface(null, Runnable.class);
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (! (exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that calling getInterface on mirror created by another engine results in IllegalArgumentException.
     */
    public void getInterfaceMixEnginesTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine1 = m.getEngineByName("nashorn");
        final ScriptEngine engine2 = m.getEngineByName("nashorn");

        try {
            Object obj = engine1.eval("({ run: function() {} })");
            // pass object from engine1 to engine2 as 'thiz' for getInterface
            ((Invocable)engine2).getInterface(obj, Runnable.class);
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (! (exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void accessGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.eval("var x = 'hello'");
            assertEquals(e.get("x"), "hello");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void exposeGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.put("y", "foo");
            e.eval("print(y)");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void putGlobalFunctionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        e.put("callable", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "callable was called";
            }
        });

        try {
            e.eval("print(callable.call())");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowAlertTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("print(window.alert)");
            e.eval("window.alert('calling window.alert...')");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowLocationTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("print(window.location)");
            final Object locationValue = e.eval("window.getLocation()");
            assertEquals(locationValue, "http://localhost:8080/window");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowItemTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            final String item1 = (String)e.eval("window.item(65535)");
            assertEquals(item1, "ffff");
            final String item2 = (String)e.eval("window.item(255)");
            assertEquals(item2, "ff");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowEventTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("window.onload = function() { print('window load event fired'); return true }");
            assertTrue((Boolean)e.eval("window.onload.loaded()"));
            final WindowEventHandler handler = window.getOnload();
            assertNotNull(handler);
            assertTrue(handler.loaded());
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void throwTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.put(ScriptEngine.FILENAME, "throwtest.js");

        try {
            e.eval("throw 'foo'");
        } catch (final ScriptException exp) {
            log(exp.getMessage());
            assertEquals(exp.getMessage(), "foo in throwtest.js at line number 1 at column number 0");
            assertEquals(exp.getFileName(), "throwtest.js");
            assertEquals(exp.getLineNumber(), 1);
        }
    }

    @Test
    public void setTimeoutTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Window window = new Window();

        try {
            final Class<?> setTimeoutParamTypes[] = { Window.class, String.class, int.class };
            final Method setTimeout = Window.class.getDeclaredMethod("setTimeout", setTimeoutParamTypes);
            assertNotNull(setTimeout);
            e.put("window", window);
            e.eval("window.setTimeout('foo()', 100)");

            // try to make setTimeout global
            e.put("setTimeout", setTimeout);
            // TODO: java.lang.ClassCastException: required class
            // java.lang.Integer but encountered class java.lang.Double
            // e.eval("setTimeout('foo2()', 200)");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void setWriterTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);

        try {
            e.eval("print('hello world')");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
        // dos2unix - fix line endings if running on windows
        assertEquals(sw.toString().replaceAll("\r", ""), "hello world\n");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void reflectionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        e.eval("var obj = { x: 344, y: 'nashorn' }");

        int count = 0;
        Map<Object, Object> map = (Map<Object, Object>)e.get("obj");
        assertFalse(map.isEmpty());
        assertTrue(map.keySet().contains("x"));
        assertTrue(map.containsKey("x"));
        assertTrue(map.values().contains("nashorn"));
        assertTrue(map.containsValue("nashorn"));
        for (final Map.Entry<?, ?> ex : map.entrySet()) {
            final Object key = ex.getKey();
            if (key.equals("x")) {
                assertTrue(344 == ((Number)ex.getValue()).doubleValue());
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
        map = (Map<Object, Object>)e.get("arr");
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
    public void redefineEchoTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.eval("var echo = {}; if (typeof echo !== 'object') { throw 'echo is a '+typeof echo; }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void invokeMethodTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.eval("var Example = function() { this.hello = function() { return 'Hello World!'; };}; myExample = new Example();");
            final Object obj = e.get("myExample");
            final Object res = ((Invocable)e).invokeMethod(obj, "hello");
            assertEquals(res, "Hello World!");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    /**
     * Check that we can call invokeMethod on an object that we got by evaluating
     * script with different Context set.
     */
    public void invokeMethodDifferentContextTest() {
       ScriptEngineManager m = new ScriptEngineManager();
       ScriptEngine e = m.getEngineByName("nashorn");

       try {
           // define an object with method on it
           Object obj = e.eval("({ hello: function() { return 'Hello World!'; } })");

           final ScriptContext ctxt = new SimpleScriptContext();
           ctxt.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
           e.setContext(ctxt);

           // invoke 'func' on obj - but with current script context changed
           final Object res = ((Invocable)e).invokeMethod(obj, "hello");
           assertEquals(res, "Hello World!");
       } catch (final Exception exp) {
           exp.printStackTrace();
           fail(exp.getMessage());
       }
    }

    @Test
    /**
     * Check that invokeMethod throws NPE on null method name.
     */
    public void invokeMethodNullNameTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            final Object obj = e.eval("({})");
            final Object res = ((Invocable)e).invokeMethod(obj, null);
            fail("should have thrown NPE");
        } catch (final Exception exp) {
            if (! (exp instanceof NullPointerException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that invokeMethod throws NoSuchMethodException on missing method.
     */
    public void invokeMethodMissingTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            final Object obj = e.eval("({})");
            final Object res = ((Invocable)e).invokeMethod(obj, "nonExistentMethod");
            fail("should have thrown NoSuchMethodException");
        } catch (final Exception exp) {
            if (! (exp instanceof NoSuchMethodException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that calling method on non-script object 'thiz' results in IllegalArgumentException.
     */
    public void invokeMethodNonScriptObjectThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable)e).invokeMethod(new Object(), "toString");
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (! (exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that calling method on null 'thiz' results in IllegalArgumentException.
     */
    public void invokeMethodNullThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable)e).invokeMethod(null, "toString");
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (! (exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }


    @Test
    /**
     * Check that calling method on mirror created by another engine results in IllegalArgumentException.
     */
    public void invokeMethodMixEnginesTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine1 = m.getEngineByName("nashorn");
        final ScriptEngine engine2 = m.getEngineByName("nashorn");

        try {
            Object obj = engine1.eval("({ run: function() {} })");
            // pass object from engine1 to engine2 as 'thiz' for invokeMethod
            ((Invocable)engine2).invokeMethod(obj, "run");
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (! (exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void noEnumerablePropertiesTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("for (i in this) { throw 'found property: ' + i }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void noRefErrorForGlobalThisAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("this.foo");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void refErrorForUndeclaredAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("try { print(foo); throw 'no ref error' } catch (e) { if (!(e instanceof ReferenceError)) throw e; }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void typeErrorForGlobalThisCallTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("try { this.foo() } catch(e) { if (! (e instanceof TypeError)) throw 'no type error' }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void refErrorForUndeclaredCallTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("try { foo() } catch(e) { if (! (e instanceof ReferenceError)) throw 'no ref error' }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void jsobjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var obj = { '1': 'world', func: function() { return this.bar; }, bar: 'hello' }");
            JSObject obj = (JSObject) e.get("obj");

            // try basic get on existing properties
            if (! obj.getMember("bar").equals("hello")) {
                fail("obj.bar != 'hello'");
            }

            if (! obj.getSlot(1).equals("world")) {
                fail("obj[1] != 'world'");
            }

            if (! obj.call("func", new Object[0]).equals("hello")) {
                fail("obj.call('func') != 'hello'");
            }

            // try setting properties
            obj.setMember("bar", "new-bar");
            obj.setSlot(1, "new-element-1");
            if (! obj.getMember("bar").equals("new-bar")) {
                fail("obj.bar != 'new-bar'");
            }

            if (! obj.getSlot(1).equals("new-element-1")) {
                fail("obj[1] != 'new-element-1'");
            }

            // try adding properties
            obj.setMember("prop", "prop-value");
            obj.setSlot(12, "element-12");
            if (! obj.getMember("prop").equals("prop-value")) {
                fail("obj.prop != 'prop-value'");
            }

            if (! obj.getSlot(12).equals("element-12")) {
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
    /**
     * check that null function name results in NPE.
     */
    public void invokeFunctionNullNameTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            final Object res = ((Invocable)e).invokeFunction(null);
            fail("should have thrown NPE");
        } catch (final Exception exp) {
            if (! (exp instanceof NullPointerException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that attempt to call missing function results in NoSuchMethodException.
     */
    public void invokeFunctionMissingTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            final Object res = ((Invocable)e).invokeFunction("NonExistentFunc");
            fail("should have thrown NoSuchMethodException");
        } catch (final Exception exp) {
            if (! (exp instanceof NoSuchMethodException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that invokeFunction calls functions only from current context's Bindings.
     */
    public void invokeFunctionDifferentContextTest() {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("nashorn");

        try {
            // define an object with method on it
            Object obj = e.eval("function hello() { return 'Hello World!'; }");
            final ScriptContext ctxt = new SimpleScriptContext();
            ctxt.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
            // change engine's current context
            e.setContext(ctxt);

            ((Invocable)e).invokeFunction("hello"); // no 'hello' in new context!
            fail("should have thrown NoSuchMethodException");
        } catch (final Exception exp) {
            if (! (exp instanceof NoSuchMethodException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void invokeFunctionExceptionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("function func() { throw new TypeError(); }");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        try {
            ((Invocable)e).invokeFunction("func");
            fail("should have thrown exception");
        } catch (final ScriptException se) {
            // ECMA TypeError property wrapped as a ScriptException
            log("got " + se + " as expected");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }
    }

    @Test
    public void invokeMethodExceptionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var sobj = {}; sobj.foo = function func() { throw new TypeError(); }");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        try {
            final Object sobj = e.get("sobj");
            ((Invocable)e).invokeMethod(sobj, "foo");
            fail("should have thrown exception");
        } catch (final ScriptException se) {
            // ECMA TypeError property wrapped as a ScriptException
            log("got " + se + " as expected");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }
    }

    @Test
    public void scriptObjectMirrorToStringTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            Object obj = e.eval("new TypeError('wrong type')");
            assertEquals(obj.toString(), "TypeError: wrong type", "toString returns wrong value");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        try {
            Object obj = e.eval("function func() { print('hello'); }");
            assertEquals(obj.toString(), "function func() { print('hello'); }", "toString returns wrong value");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }
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
    /**
     * Tests whether invocation of a JavaScript method through a variable arity Java method will pass the vararg array.
     * Both non-vararg and vararg JavaScript methods are tested.
     * @throws ScriptException
     */
    public void variableArityInterfaceTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.eval(
            "function test1(i, strings) {" +
            "    return 'i == ' + i + ', strings instanceof java.lang.String[] == ' + (strings instanceof Java.type('java.lang.String[]')) + ', strings == ' + java.util.Arrays.toString(strings)" +
            "}" +
            "function test2() {" +
            "    return 'arguments[0] == ' + arguments[0] + ', arguments[1] instanceof java.lang.String[] == ' + (arguments[1] instanceof Java.type('java.lang.String[]')) + ', arguments[1] == ' + java.util.Arrays.toString(arguments[1])" +
            "}"
        );
        final VariableArityTestInterface itf = ((Invocable)e).getInterface(VariableArityTestInterface.class);
        Assert.assertEquals(itf.test1(42, "a", "b"), "i == 42, strings instanceof java.lang.String[] == true, strings == [a, b]");
        Assert.assertEquals(itf.test2(44, "c", "d", "e"), "arguments[0] == 44, arguments[1] instanceof java.lang.String[] == true, arguments[1] == [c, d, e]");
    }

    @Test
    // check that print function prints arg followed by newline char
    public void printTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);
        try {
            e.eval("print('hello')");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        // dos2unix - fix line endings if running on windows
        assertEquals(sw.toString().replaceAll("\r", ""), "hello\n");
    }

    @Test
    // check that print prints all arguments (more than one)
    public void printManyTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);
        try {
            e.eval("print(34, true, 'hello')");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        // dos2unix - fix line endings if running on windows
        assertEquals(sw.toString().replaceAll("\r", ""), "34 true hello\n");
    }
}

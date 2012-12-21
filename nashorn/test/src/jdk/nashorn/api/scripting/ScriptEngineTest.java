/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.internal.runtime.Version;
import netscape.javascript.JSObject;
import org.testng.TestNG;
import org.testng.annotations.Test;

/**
 * Tests for JSR-223 script engine for Nashorn.
 */
public class ScriptEngineTest {

    public static void main(final String[] args) {
        TestNG.main(args);
    }

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
            assertEquals(true,e.eval("arguments instanceof Array"));
            assertEquals(true, e.eval("arguments.length == 0"));
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

        assertEquals("ECMAScript", fac.getLanguageName());
        assertEquals("javascript", fac.getParameter(ScriptEngine.NAME));
        assertEquals("ECMA - 262 Edition 5.1", fac.getLanguageVersion());
        assertEquals("Oracle Nashorn", fac.getEngineName());
        assertEquals(Version.version(), fac.getEngineVersion());
        assertEquals("print(context)", fac.getOutputStatement("context"));
        assertEquals("javascript", fac.getParameter(ScriptEngine.NAME));

        boolean seenJS = false;
        for (String ext : fac.getExtensions()) {
            if (ext.equals("js")) {
                seenJS = true;
            }
        }

        assertEquals(true, seenJS);
        String str = fac.getMethodCallSyntax("obj", "foo", "x");
        assertEquals("obj.foo(x)", str);

        boolean seenNashorn = false, seenJavaScript = false, seenECMAScript = false;
        for (String name : fac.getNames()) {
            switch (name) {
                case "nashorn": seenNashorn = true; break;
                case "javascript": seenJavaScript = true; break;
                case "ECMAScript": seenECMAScript = true; break;
            }
        }

        assertEquals(true, seenNashorn);
        assertEquals(true, seenJavaScript);
        assertEquals(true, seenECMAScript);

        boolean seenAppJS = false, seenAppECMA = false, seenTextJS = false, seenTextECMA = false;
        for (String mime : fac.getMimeTypes()) {
            switch (mime) {
                case "application/javascript": seenAppJS = true; break;
                case "application/ecmascript": seenAppECMA = true; break;
                case "text/javascript": seenTextJS = true; break;
                case "text/ecmascript": seenTextECMA = true; break;
            }
        }

        assertEquals(true, seenAppJS);
        assertEquals(true, seenAppECMA);
        assertEquals(true, seenTextJS);
        assertEquals(true, seenTextECMA);
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
            assertEquals(1, se.getLineNumber());
            assertEquals(13, se.getColumnNumber());
            assertEquals("myfile.js", se.getFileName());
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

        assertEquals(Boolean.TRUE, res);
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

    @Test
    public void accessGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.eval("var x = 'hello'");
            assertEquals("hello", e.get("x"));
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

    public static void alert(final Object self, final Object msg) {
        System.out.println(msg);
    }

    @Test
    public void exposeFunctionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            final Method alert = ScriptEngineTest.class.getMethod("alert", Object.class, Object.class);
            // expose a Method object as global var.
            e.put("alert", alert);
            // call the global var.
            e.eval("alert('alert! alert!!')");
        } catch (final NoSuchMethodException | SecurityException | ScriptException exp) {
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
            e.eval("print(window.alert)"); // TODO: bug - prints 'undefined'
            e.eval("window.alert('calling window.alert...')");
            // TODO: java.lang.NoSuchMethodException: alert
            // ((Invocable) e).invokeMethod(window, "alert",
            // "invoking window.alert...");
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
            final Object locationValue = ((Invocable)e).invokeMethod(window, "getLocation");
            assertEquals("http://localhost:8080/window", locationValue);
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
            assertEquals("ffff", item1);
            final String item2 = (String)((Invocable)e).invokeMethod(window, "item", 255);
            assertEquals("ff", item2);
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
            assertEquals("foo in throwtest.js at line number 1 at column number 0", exp.getMessage());
            assertEquals("throwtest.js", exp.getFileName());
            assertEquals(1, exp.getLineNumber());
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
        assertEquals(false, map.isEmpty());
        assertEquals(true, map.keySet().contains("x"));
        assertEquals(true, map.containsKey("x"));
        assertEquals(true, map.values().contains("nashorn"));
        assertEquals(true, map.containsValue("nashorn"));
        for (final Map.Entry<?, ?> ex : map.entrySet()) {
            final Object key = ex.getKey();
            if (key.equals("x")) {
                assertTrue(344 == ((Number)ex.getValue()).doubleValue());
                count++;
            } else if (key.equals("y")) {
                assertEquals("nashorn", ex.getValue());
                count++;
            }
        }
        assertEquals(2, count);
        assertEquals(2, map.size());

        // add property
        map.put("z", "hello");
        assertEquals("hello", e.eval("obj.z"));
        assertEquals("hello", map.get("z"));
        assertEquals(true, map.keySet().contains("z"));
        assertEquals(true, map.containsKey("z"));
        assertEquals(true, map.values().contains("hello"));
        assertEquals(true, map.containsValue("hello"));
        assertEquals(3, map.size());

        final Map<Object, Object> newMap = new HashMap<>();
        newMap.put("foo", 23.0);
        newMap.put("bar", true);
        map.putAll(newMap);

        assertEquals(23.0, e.eval("obj.foo"));
        assertEquals(true, e.eval("obj.bar"));

        // remove using map method
        map.remove("foo");
        assertEquals("undefined", e.eval("typeof obj.foo"));

        count = 0;
        e.eval("var arr = [ true, 'hello' ]");
        map = (Map<Object, Object>)e.get("arr");
        assertEquals(false, map.isEmpty());
        assertEquals(true, map.containsKey("length"));
        assertEquals(true, map.containsValue("hello"));
        for (final Map.Entry<?, ?> ex : map.entrySet()) {
            final Object key = ex.getKey();
            if (key.equals("0")) {
                assertEquals(Boolean.TRUE, ex.getValue());
                count++;
            } else if (key.equals("1")) {
                assertEquals("hello", ex.getValue());
                count++;
            }
        }
        assertEquals(2, count);
        assertEquals(2, map.size());

        // add element
        map.put(2, "world");
        assertEquals("world", map.get(2));
        assertEquals(3, map.size());

        // remove all
        map.clear();
        assertEquals(true, map.isEmpty());
        assertEquals("undefined", e.eval("typeof arr[0]"));
        assertEquals("undefined", e.eval("typeof arr[1]"));
        assertEquals("undefined", e.eval("typeof arr[2]"));
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
            assertEquals("Hello World!", res);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void versionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        assertEquals(e.getFactory().getEngineVersion(), Version.version());
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
    public void securityPackagesTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var v = Packages.sun.misc.Unsafe;");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void securityJavaTypeTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var v = Java.type('sun.misc.Unsafe');");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void securityClassForNameTest() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("var v = java.lang.Class.forName('sun.misc.Unsafe');");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void securitySystemExit() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("java.lang.System.exit(0);");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void securitySystemLoadLibrary() {
        if (System.getSecurityManager() == null) {
            // pass vacuously
        }

        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            e.eval("java.lang.System.loadLibrary('foo');");
            fail("should have thrown SecurityException");
        } catch (final Exception exp) {
            if (exp instanceof SecurityException) {
                log("got " + exp + " as expected");
            } else {
                fail(exp.getMessage());
            }
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

        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
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
}

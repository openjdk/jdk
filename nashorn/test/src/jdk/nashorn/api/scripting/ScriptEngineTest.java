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

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
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
    public void compileAndEvalInDiffContextTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName("js");
        final Compilable compilable = (Compilable) engine;
        final CompiledScript compiledScript = compilable.compile("foo");
        final ScriptContext ctxt = new SimpleScriptContext();
        ctxt.setAttribute("foo", "hello", ScriptContext.ENGINE_SCOPE);
        assertEquals(compiledScript.eval(ctxt), "hello");
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
        assertEquals(sw.toString(), println("hello world"));
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

        assertEquals(sw.toString(), println("hello"));
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

        assertEquals(sw.toString(), println("34 true hello"));
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    // Returns String that would be the result of calling PrintWriter.println
    // of the given String. (This is to handle platform specific newline).
    private static String println(final String str) {
        return str + LINE_SEPARATOR;
    }
}

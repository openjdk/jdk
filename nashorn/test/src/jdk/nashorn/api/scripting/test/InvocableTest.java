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
import static org.testng.Assert.fail;
import java.util.Objects;
import java.util.function.Function;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for javax.script.Invocable implementation of nashorn.
 */
@SuppressWarnings("javadoc")
public class InvocableTest {

    private static void log(final String msg) {
        org.testng.Reporter.log(msg, true);
    }

    @Test
    public void invokeMethodTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            e.eval("var Example = function() { this.hello = function() { return 'Hello World!'; };}; myExample = new Example();");
            final Object obj = e.get("myExample");
            final Object res = ((Invocable) e).invokeMethod(obj, "hello");
            assertEquals(res, "Hello World!");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    /**
     * Check that we can call invokeMethod on an object that we got by
     * evaluating script with different Context set.
     */
    public void invokeMethodDifferentContextTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            // define an object with method on it
            final Object obj = e.eval("({ hello: function() { return 'Hello World!'; } })");

            final ScriptContext ctxt = new SimpleScriptContext();
            ctxt.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
            e.setContext(ctxt);

            // invoke 'func' on obj - but with current script context changed
            final Object res = ((Invocable) e).invokeMethod(obj, "hello");
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
            ((Invocable) e).invokeMethod(obj, null);
            fail("should have thrown NPE");
        } catch (final Exception exp) {
            if (!(exp instanceof NullPointerException)) {
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
            ((Invocable) e).invokeMethod(obj, "nonExistentMethod");
            fail("should have thrown NoSuchMethodException");
        } catch (final Exception exp) {
            if (!(exp instanceof NoSuchMethodException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that calling method on non-script object 'thiz' results in
     * IllegalArgumentException.
     */
    public void invokeMethodNonScriptObjectThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable) e).invokeMethod(new Object(), "toString");
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (!(exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that calling method on null 'thiz' results in
     * IllegalArgumentException.
     */
    public void invokeMethodNullThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable) e).invokeMethod(null, "toString");
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (!(exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that calling method on mirror created by another engine results in
     * IllegalArgumentException.
     */
    public void invokeMethodMixEnginesTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine1 = m.getEngineByName("nashorn");
        final ScriptEngine engine2 = m.getEngineByName("nashorn");

        try {
            final Object obj = engine1.eval("({ run: function() {} })");
            // pass object from engine1 to engine2 as 'thiz' for invokeMethod
            ((Invocable) engine2).invokeMethod(obj, "run");
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (!(exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    public void getInterfaceTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Invocable inv = (Invocable) e;

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
            final Object obj = e.get("obj");
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

        Runnable runnable = ((Invocable) engine).getInterface(Runnable.class);
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
        runnable = ((Invocable) engine).getInterface(Runnable.class);
        // should not return null now!
        runnable.run();

        // define only one method of "Foo2"
        try {
            engine.eval("function bar() { print('bar function'); }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }

        Foo2 foo2 = ((Invocable) engine).getInterface(Foo2.class);
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
        foo2 = ((Invocable) engine).getInterface(Foo2.class);
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
            log(Objects.toString(((Invocable) engine).getInterface(Object.class)));
            fail("Should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (!(exp instanceof IllegalArgumentException)) {
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
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        try {
            final Object obj = e.eval("({ run: function() { } })");

            // change script context
            final ScriptContext ctxt = new SimpleScriptContext();
            ctxt.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
            e.setContext(ctxt);

            final Runnable r = ((Invocable) e).getInterface(obj, Runnable.class);
            r.run();
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    /**
     * Check that getInterface on non-script object 'thiz' results in
     * IllegalArgumentException.
     */
    public void getInterfaceNonScriptObjectThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable) e).getInterface(new Object(), Runnable.class);
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (!(exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that getInterface on null 'thiz' results in
     * IllegalArgumentException.
     */
    public void getInterfaceNullThizTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable) e).getInterface(null, Runnable.class);
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (!(exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that calling getInterface on mirror created by another engine
     * results in IllegalArgumentException.
     */
    public void getInterfaceMixEnginesTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine1 = m.getEngineByName("nashorn");
        final ScriptEngine engine2 = m.getEngineByName("nashorn");

        try {
            final Object obj = engine1.eval("({ run: function() {} })");
            // pass object from engine1 to engine2 as 'thiz' for getInterface
            ((Invocable) engine2).getInterface(obj, Runnable.class);
            fail("should have thrown IllegalArgumentException");
        } catch (final Exception exp) {
            if (!(exp instanceof IllegalArgumentException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
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
            ((Invocable)e).invokeFunction(null);
            fail("should have thrown NPE");
        } catch (final Exception exp) {
            if (!(exp instanceof NullPointerException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that attempt to call missing function results in
     * NoSuchMethodException.
     */
    public void invokeFunctionMissingTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            ((Invocable)e).invokeFunction("NonExistentFunc");
            fail("should have thrown NoSuchMethodException");
        } catch (final Exception exp) {
            if (!(exp instanceof NoSuchMethodException)) {
                exp.printStackTrace();
                fail(exp.getMessage());
            }
        }
    }

    @Test
    /**
     * Check that invokeFunction calls functions only from current context's
     * Bindings.
     */
    public void invokeFunctionDifferentContextTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");

        try {
            // define an object with method on it
            e.eval("function hello() { return 'Hello World!'; }");
            final ScriptContext ctxt = new SimpleScriptContext();
            ctxt.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
            // change engine's current context
            e.setContext(ctxt);

            ((Invocable) e).invokeFunction("hello"); // no 'hello' in new context!
            fail("should have thrown NoSuchMethodException");
        } catch (final Exception exp) {
            if (!(exp instanceof NoSuchMethodException)) {
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
            ((Invocable) e).invokeFunction("func");
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
            ((Invocable) e).invokeMethod(sobj, "foo");
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
    /**
     * Tests whether invocation of a JavaScript method through a variable arity
     * Java method will pass the vararg array. Both non-vararg and vararg
     * JavaScript methods are tested.
     *
     * @throws ScriptException
     */
    public void variableArityInterfaceTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        e.eval(
                "function test1(i, strings) {"
                + "    return 'i == ' + i + ', strings instanceof java.lang.String[] == ' + (strings instanceof Java.type('java.lang.String[]')) + ', strings == ' + java.util.Arrays.toString(strings)"
                + "}"
                + "function test2() {"
                + "    return 'arguments[0] == ' + arguments[0] + ', arguments[1] instanceof java.lang.String[] == ' + (arguments[1] instanceof Java.type('java.lang.String[]')) + ', arguments[1] == ' + java.util.Arrays.toString(arguments[1])"
                + "}");
        final VariableArityTestInterface itf = ((Invocable) e).getInterface(VariableArityTestInterface.class);
        Assert.assertEquals(itf.test1(42, "a", "b"), "i == 42, strings instanceof java.lang.String[] == true, strings == [a, b]");
        Assert.assertEquals(itf.test2(44, "c", "d", "e"), "arguments[0] == 44, arguments[1] instanceof java.lang.String[] == true, arguments[1] == [c, d, e]");
    }

    @Test
    public void defaultMethodTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        final Invocable inv = (Invocable) e;

        final Object obj = e.eval("({ apply: function(arg) { return arg.toUpperCase(); }})");
        @SuppressWarnings("unchecked")
        final Function<String, String> func = inv.getInterface(obj, Function.class);
        assertEquals(func.apply("hello"), "HELLO");
    }
}

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.test;

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import static org.testng.Assert.assertEquals;

/**
 * Top-level lexical binding tests.
 *
 * @test
 * @run testng jdk.nashorn.internal.runtime.test.LexicalBindingTest
 */
@SuppressWarnings("javadoc")
public class LexicalBindingTest {

    final static String LANGUAGE_ES6 = "--language=es6";
    final static int NUMBER_OF_CONTEXTS = 40;
    final static int MEGAMORPHIC_LOOP_COUNT = 40;

    /**
     * Test access to global var-declared variables for shared script classes with multiple globals.
     */
    @Test
    public static void megamorphicVarTest() throws ScriptException, InterruptedException {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine e = factory.getScriptEngine();
        final ScriptContext[] contexts = new ScriptContext[NUMBER_OF_CONTEXTS];
        final String sharedScript1 = "foo";
        final String sharedScript2 = "bar = foo; bar";


        for (int i = 0; i < NUMBER_OF_CONTEXTS; i++) {
            final ScriptContext context = contexts[i] = new SimpleScriptContext();
            final Bindings b = e.createBindings();
            context.setBindings(b, ScriptContext.ENGINE_SCOPE);
            assertEquals(e.eval("var foo = '" + i + "'; var bar;", context), null);
        }

        for (int i = 0; i < NUMBER_OF_CONTEXTS; i++) {
            final ScriptContext context = contexts[i];
            assertEquals(e.eval(sharedScript1, context), String.valueOf(i));
            assertEquals(e.eval(sharedScript2, context), String.valueOf(i));
        }
    }

    /**
     * Test access to global lexically declared variables for shared script classes with multiple globals.
     */
    @Test
    public static void megamorphicMultiGlobalLetTest() throws ScriptException, InterruptedException {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine e = factory.getScriptEngine(LANGUAGE_ES6);
        final ScriptContext[] contexts = new ScriptContext[NUMBER_OF_CONTEXTS];
        final String sharedScript1 = "foo";
        final String sharedScript2 = "bar = foo; bar";


        for (int i = 0; i < NUMBER_OF_CONTEXTS; i++) {
            final ScriptContext context = contexts[i] = new SimpleScriptContext();
            final Bindings b = e.createBindings();
            context.setBindings(b, ScriptContext.ENGINE_SCOPE);
            assertEquals(e.eval("let foo = '" + i + "'; let bar; ", context), null);
        }

        for (int i = 0; i < NUMBER_OF_CONTEXTS; i++) {
            final ScriptContext context = contexts[i];
            assertEquals(e.eval(sharedScript1, context), String.valueOf(i));
            assertEquals(e.eval(sharedScript2, context), String.valueOf(i));
        }
    }


    /**
     * Test access to global lexically declared variables for shared script classes with single global.
     */
    @Test
    public static void megamorphicSingleGlobalLetTest() throws ScriptException, InterruptedException {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine e = factory.getScriptEngine(LANGUAGE_ES6);
        final String sharedGetterScript = "foo";
        final String sharedSetterScript = "foo = 1";

        for (int i = 0; i < MEGAMORPHIC_LOOP_COUNT; i++) {
            assertEquals(e.eval(sharedSetterScript), 1);
            assertEquals(e.eval(sharedGetterScript), 1);
            assertEquals(e.eval("delete foo; a" + i + " = 1; foo = " + i + ";"), i);
            assertEquals(e.eval(sharedGetterScript), i);
        }

        assertEquals(e.eval("let foo = 'foo';"), null);
        assertEquals(e.eval(sharedGetterScript), "foo");
        assertEquals(e.eval(sharedSetterScript), 1);
        assertEquals(e.eval(sharedGetterScript), 1);
        assertEquals(e.eval("this.foo"), MEGAMORPHIC_LOOP_COUNT - 1);
    }

    /**
     * Test access to global lexically declared variables for shared script classes with single global.
     */
    @Test
    public static void megamorphicInheritedGlobalLetTest() throws ScriptException, InterruptedException {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine e = factory.getScriptEngine(LANGUAGE_ES6);
        final String sharedGetterScript = "foo";
        final String sharedSetterScript = "foo = 1";

        for (int i = 0; i < MEGAMORPHIC_LOOP_COUNT; i++) {
            assertEquals(e.eval(sharedSetterScript), 1);
            assertEquals(e.eval(sharedGetterScript), 1);
            assertEquals(e.eval("delete foo; a" + i + " = 1; Object.prototype.foo = " + i + ";"), i);
            assertEquals(e.eval(sharedGetterScript), i);
        }

        assertEquals(e.eval("let foo = 'foo';"), null);
        assertEquals(e.eval(sharedGetterScript), "foo");
        assertEquals(e.eval(sharedSetterScript), 1);
        assertEquals(e.eval(sharedGetterScript), 1);
        assertEquals(e.eval("this.foo"), MEGAMORPHIC_LOOP_COUNT - 1);
    }

    /**
     * Test multi-threaded access to global lexically declared variables for shared script classes with multiple globals.
     */
    @Test
    public static void multiThreadedLetTest() throws ScriptException, InterruptedException {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine e = factory.getScriptEngine(LANGUAGE_ES6);
        final Bindings b = e.createBindings();
        final ScriptContext origContext = e.getContext();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        final String sharedScript = "foo";

        assertEquals(e.eval("let foo = 'original context';", origContext), null);
        assertEquals(e.eval("let foo = 'new context';", newCtxt), null);

        final Thread t1 = new Thread(new ScriptRunner(e, origContext, sharedScript, "original context", 1000));
        final Thread t2 = new Thread(new ScriptRunner(e, newCtxt, sharedScript, "new context", 1000));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(e.eval("foo = 'newer context';", newCtxt), "newer context");
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
     * Make sure lexically defined variables are accessible in other scripts.
     */
    @Test
    public void lexicalScopeTest() throws ScriptException {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine e = factory.getScriptEngine(LANGUAGE_ES6);

        e.eval("let x; const y = 'world';");

        assertEquals(e.eval("x = 'hello'"), "hello");
        assertEquals(e.eval("typeof x"), "string");
        assertEquals(e.eval("typeof y"), "string");
        assertEquals(e.eval("x"), "hello");
        assertEquals(e.eval("y"), "world");
        assertEquals(e.eval("typeof this.x"), "undefined");
        assertEquals(e.eval("typeof this.y"), "undefined");
        assertEquals(e.eval("this.x"), null);
        assertEquals(e.eval("this.y"), null);
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

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

package jdk.nashorn.internal.runtime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptEngineTest;
import jdk.nashorn.internal.runtime.options.Options;
import org.testng.annotations.Test;

/**
 * Basic Context API tests.
 */
public class ContextTest {
    // basic context eval test
    @Test
    public void evalTest() {
        final Options options = new Options("");
        final ErrorManager errors = new ErrorManager();
        final Context cx = new Context(options, errors, Thread.currentThread().getContextClassLoader());
        final ScriptObject oldGlobal = Context.getGlobal();
        Context.setGlobal(cx.createGlobal());
        try {
            String code = "22 + 10";
            assertTrue(32.0 == ((Number)(eval(cx, "<evalTest>", code))).doubleValue());

            code = "obj = { js: 'nashorn' }; obj.js";
            assertEquals(eval(cx, "<evalTest2>", code), "nashorn");
        } finally {
            Context.setGlobal(oldGlobal);
        }
    }

    // basic check for JS reflection access - java.util.Map-like access on ScriptObject
    @Test
    public void reflectionTest() {
        final Options options = new Options("");
        final ErrorManager errors = new ErrorManager();
        final Context cx = new Context(options, errors, Thread.currentThread().getContextClassLoader());
        final ScriptObject oldGlobal = Context.getGlobal();
        Context.setGlobal(cx.createGlobal());

        try {
            final String code = "var obj = { x: 344, y: 42 }";
            eval(cx, "<reflectionTest>", code);

            final Object obj = cx.getGlobal().get("obj");

            assertTrue(obj instanceof ScriptObject);

            final ScriptObject sobj = (ScriptObject)obj;
            int count = 0;
            for (final Map.Entry<?, ?> ex : sobj.entrySet()) {
                final Object key = ex.getKey();
                if (key.equals("x")) {
                    assertTrue(ex.getValue() instanceof Number);
                    assertTrue(344.0 == ((Number)ex.getValue()).doubleValue());

                    count++;
                } else if (key.equals("y")) {
                    assertTrue(ex.getValue() instanceof Number);
                    assertTrue(42.0 == ((Number)ex.getValue()).doubleValue());

                    count++;
                }
            }
            assertEquals(count, 2);
            assertEquals(sobj.size(), 2);

            // add property
            sobj.put("zee", "hello");
            assertEquals(sobj.get("zee"), "hello");
            assertEquals(sobj.size(), 3);

        } finally {
            Context.setGlobal(oldGlobal);
        }
    }

    private Object eval(final Context cx, final String name, final String code) {
        final Source source = new Source(name, code);
        final ScriptObject global = Context.getGlobal();
        final ScriptFunction func = cx.compileScript(source, global);
        return func != null ? ScriptRuntime.apply(func, global) : null;
    }

    // Tests for trusted client usage of nashorn script engine factory extension API

    private static class MyClassLoader extends ClassLoader {
        // to check if script engine uses the specified class loader
        private final boolean[] reached = new boolean[1];

        @Override
        protected Class findClass(final String name) throws ClassNotFoundException {
            // flag that it reached here
            reached[0] = true;
            return super.findClass(name);
        }

        public boolean reached() {
            return reached[0];
        }
    };

    // These are for "private" extension API of NashornScriptEngineFactory that
    // accepts a ClassLoader and/or command line options.

    @Test
    public void factoryClassLoaderTest() {
        final ScriptEngineManager sm = new ScriptEngineManager();
        for (ScriptEngineFactory fac : sm.getEngineFactories()) {
            if (fac instanceof NashornScriptEngineFactory) {
                final NashornScriptEngineFactory nfac = (NashornScriptEngineFactory)fac;
                final MyClassLoader loader = new MyClassLoader();
                // set the classloader as app class loader
                final ScriptEngine e = nfac.getScriptEngine(loader);
                try {
                    e.eval("Packages.foo");
                    // check that the class loader was attempted
                    assertTrue(loader.reached(), "did not reach class loader!");
                } catch (final ScriptException se) {
                    se.printStackTrace();
                    fail(se.getMessage());
                }
                return;
            }
        }

        fail("Cannot find nashorn factory!");
    }

    @Test
    public void factoryClassLoaderAndOptionsTest() {
        final ScriptEngineManager sm = new ScriptEngineManager();
        for (ScriptEngineFactory fac : sm.getEngineFactories()) {
            if (fac instanceof NashornScriptEngineFactory) {
                final NashornScriptEngineFactory nfac = (NashornScriptEngineFactory)fac;
                final String[] options = new String[] { "-strict" };
                final MyClassLoader loader = new MyClassLoader();
                // set the classloader as app class loader
                final ScriptEngine e = nfac.getScriptEngine(options, loader);
                try {
                    e.eval("Packages.foo");
                    // check that the class loader was attempted
                    assertTrue(loader.reached(), "did not reach class loader!");
                } catch (final ScriptException se) {
                    se.printStackTrace();
                    fail(se.getMessage());
                }

                try {
                    // strict mode - delete of a var should throw SyntaxError
                    e.eval("var d = 2; delete d;");
                } catch (final ScriptException se) {
                    // check that the error message contains "SyntaxError"
                    assertTrue(se.getMessage().contains("SyntaxError"));
                }

                return;
            }
        }

        fail("Cannot find nashorn factory!");
    }
}

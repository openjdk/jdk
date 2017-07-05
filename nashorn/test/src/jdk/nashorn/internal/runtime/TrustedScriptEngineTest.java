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

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * Tests for trusted client usage of nashorn script engine factory extension API
 */
public class TrustedScriptEngineTest {
    @Test
    public void versionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("nashorn");
        assertEquals(e.getFactory().getEngineVersion(), Version.version());
    }

    private static class MyClassLoader extends ClassLoader {
        // to check if script engine uses the specified class loader
        private final boolean[] reached = new boolean[1];

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
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
        for (final ScriptEngineFactory fac : sm.getEngineFactories()) {
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
        for (final ScriptEngineFactory fac : sm.getEngineFactories()) {
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

    @Test
    public void factoryOptionsTest() {
        final ScriptEngineManager sm = new ScriptEngineManager();
        for (final ScriptEngineFactory fac : sm.getEngineFactories()) {
            if (fac instanceof NashornScriptEngineFactory) {
                final NashornScriptEngineFactory nfac = (NashornScriptEngineFactory)fac;
                // specify --no-syntax-extensions flag
                final String[] options = new String[] { "--no-syntax-extensions" };
                final ScriptEngine e = nfac.getScriptEngine(options);
                try {
                    // try nashorn specific extension
                    e.eval("var f = funtion(x) 2*x;");
                    fail("should have thrown exception!");
                } catch (final ScriptException se) {
                }
                return;
            }
        }

        fail("Cannot find nashorn factory!");
    }

    @Test
    /**
     * Test repeated evals with --loader-per-compile=false
     * We used to get "class redefinition error".
     */
    public void noLoaderPerCompilerTest() {
        final ScriptEngineManager sm = new ScriptEngineManager();
        for (final ScriptEngineFactory fac : sm.getEngineFactories()) {
            if (fac instanceof NashornScriptEngineFactory) {
                final NashornScriptEngineFactory nfac = (NashornScriptEngineFactory)fac;
                final String[] options = new String[] { "--loader-per-compile=false" };
                final ScriptEngine e = nfac.getScriptEngine(options);
                try {
                    e.eval("2 + 3");
                    e.eval("4 + 4");
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
    /**
     * Test that we can use same script name in repeated evals with --loader-per-compile=false
     * We used to get "class redefinition error" as name was derived from script name.
     */
    public void noLoaderPerCompilerWithSameNameTest() {
        final ScriptEngineManager sm = new ScriptEngineManager();
        for (final ScriptEngineFactory fac : sm.getEngineFactories()) {
            if (fac instanceof NashornScriptEngineFactory) {
                final NashornScriptEngineFactory nfac = (NashornScriptEngineFactory)fac;
                final String[] options = new String[] { "--loader-per-compile=false" };
                final ScriptEngine e = nfac.getScriptEngine(options);
                e.put(ScriptEngine.FILENAME, "test.js");
                try {
                    e.eval("2 + 3");
                    e.eval("4 + 4");
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
    public void globalPerEngineTest() throws ScriptException {
        final NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        final String[] options = new String[] { "--global-per-engine" };
        final ScriptEngine e = fac.getScriptEngine(options);

        e.eval("function foo() {}");

        final ScriptContext newCtx = new SimpleScriptContext();
        newCtx.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);

        // all global definitions shared and so 'foo' should be
        // visible in new Bindings as well.
        assertTrue(e.eval("typeof foo", newCtx).equals("function"));

        e.eval("function bar() {}", newCtx);

        // bar should be visible in default context
        assertTrue(e.eval("typeof bar").equals("function"));
    }


    @Test public void nashornSwallowsConstKeyword() throws Exception {
        final NashornScriptEngineFactory f = new NashornScriptEngineFactory();
        final String[] args = new String[] { "--const-as-var" };
        final ScriptEngine engine = f.getScriptEngine(args);

        final Object ret = engine.eval(""
            + "(function() {\n"
            + "  const x = 10;\n"
            + "  return x;\n"
            + "})();"
        );
        assertEquals(ret, 10, "Parsed and executed OK");
    }
}

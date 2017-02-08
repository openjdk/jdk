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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import java.io.File;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.URLReader;
import jdk.nashorn.internal.test.framework.TestFinder;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ClassFilterTest {
    private static final String NASHORN_CODE_CACHE = "nashorn.persistent.code.cache";
    private static final String CLASSFILTER_CODE_CACHE = "build/classfilter_nashorn_code_cache";

    // @Test
    // This test takes too much time for basic "ant clean test" run.
    // Given that "allow-all-java-classes" is equivalent to no java class
    // filter and external tests don't access any java, not sure if this
    // test contributes much. We need faster "ant clean test" cycle for
    // developers.
    public void runExternalJsTest() {
        final String[] paths = new String[]{
                "test/script/basic/compile-octane.js",
                "test/script/basic/jquery.js",
                "test/script/basic/prototype.js",
                "test/script/basic/runsunspider.js",
                "test/script/basic/underscore.js",
                "test/script/basic/yui.js",
                "test/script/basic/run-octane.js"
        };
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        for (final String path : paths) {
            final ScriptEngine engine = factory.getScriptEngine(new String[]{"-scripting"}, getClass().getClassLoader(), getClassFilter());
            try {
                engine.eval(new URLReader(new File(path).toURI().toURL()));
            } catch (final Exception e) {
                fail("Script " + path + " fails with exception :" + e.getMessage());
            }
        }
    }

    @Test
    public void noJavaOptionTest() {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine engine = factory.getScriptEngine(new String[]{"--no-java"}, getClass().getClassLoader(), getClassFilter());
        try {
            engine.eval("var str = Java.type('java.lang.String');");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("Java");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("JavaImporter");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("Packages");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("com");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("edu");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("java");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("javafx");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("javax");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            engine.eval("org");
            fail("TypeError should have been thrown");
        } catch (final ScriptException e) {
            //emtpy
        }
        try {
            assertEquals(engine.eval("Java = this[\"__LINE__\"]; Java === this[\"__LINE__\"]"), Boolean.TRUE);
        } catch (final ScriptException e) {
            fail("Unexpected exception", e);
        }
    }

    @Test
    public void securityTest() {
        if (System.getSecurityManager() == null) {
            return;
        }

        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine engine = factory.getScriptEngine(getClassFilter());
        try {
            engine.eval("var thread = Java.type('sun.misc.Unsafe')");
            fail("SecurityException should have been thrown");
        } catch (final Exception e) {
            //empty
        }
        try {
            engine.eval("var thread = new sun.misc.Unsafe()");
            fail("SecurityException should have been thrown");
        } catch (final Exception e) {
            //empty
        }
        try {
            engine.eval("var thread = Java.extend(sun.misc.Unsafe, {})");
            fail("TypeError should have been thrown");
        } catch (final Exception e) {
            //empty
        }
        try {
            engine.eval("java.lang.System.exit(0)");
            fail("SecurityException should have been thrown");
        } catch (final Exception e) {
            //empty
        }

    }

    @Test
    public void persistentCacheTest() {
        final String oldCodeCache = System.getProperty(NASHORN_CODE_CACHE);
        System.setProperty(NASHORN_CODE_CACHE, CLASSFILTER_CODE_CACHE);
        try {
            persistentCacheTestImpl();
        } finally {
            if (oldCodeCache != null) {
                System.setProperty(NASHORN_CODE_CACHE, oldCodeCache);
            }
        }
    }

    private void persistentCacheTestImpl() {
        final NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        final ScriptEngine engine = factory.getScriptEngine(
              TestFinder.addExplicitOptimisticTypes(new String[]{"--persistent-code-cache", "--optimistic-types=true"}),
                  getClass().getClassLoader(),
                  getClassFilter()
        );
        final String testScript = "var a = Java.type('java.lang.String');" + generateCodeForPersistentStore();
        try {
            engine.eval(testScript);
        } catch (final ScriptException exc) {
            fail(exc.getMessage());
        }
        final ScriptEngine engineSafe = factory.getScriptEngine(
                TestFinder.addExplicitOptimisticTypes(new String[]{"--persistent-code-cache", "--optimistic-types=true"}),
                getClass().getClassLoader(),
                new ClassFilter() {
                    @Override
                    public boolean exposeToScripts(final String s) {
                        return false;
                    }
                }
        );
        try {
            engineSafe.eval(testScript);
            fail("ClassNotFoundException should have been thrown");
        } catch (final Exception exc) {
            if (!(exc.getCause() instanceof ClassNotFoundException)) {
                fail("ClassNotFoundException expected, got " + exc.getClass());
            }
        }
    }

    private static String generateCodeForPersistentStore() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i < 100; i++) {
            stringBuilder.append("function i")
                    .append(i)
                    .append("(y, z) { var x")
                    .append(i)
                    .append(" = ")
                    .append(i)
                    .append(";}");
        }
        return stringBuilder.toString();
    }

    private static ClassFilter getClassFilter() {
        return new ClassFilter() {
            @Override
            public boolean exposeToScripts(final String s) {
                return true;
            }
        };
    }
}

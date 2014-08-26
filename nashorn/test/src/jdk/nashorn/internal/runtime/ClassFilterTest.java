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

package jdk.nashorn.internal.runtime;


import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.URLReader;
import org.testng.annotations.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;

import static org.testng.Assert.fail;

public class ClassFilterTest {

    private final String codeCache = "build/nashorn_code_cache";

    @Test
    public void runExternalJsTest() {
        String[] paths = new String[]{
                "test/script/basic/compile-octane.js",
                "test/script/basic/jquery.js",
                "test/script/basic/prototype.js",
                "test/script/basic/runsunspider.js",
                "test/script/basic/underscore.js",
                "test/script/basic/yui.js",
                "test/script/basic/run-octane.js"
        };
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        for (String path : paths) {
            ScriptEngine engine = factory.getScriptEngine(new String[]{"-scripting"}, getClass().getClassLoader(), getClassFilter());
            try {
                engine.eval(new URLReader(new File(path).toURI().toURL()));
            } catch (Exception e) {
                fail("Script " + path + " fails with exception :" + e.getMessage());
            }
        }
    }

    @Test
    public void noJavaOptionTest() {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        ScriptEngine engine = factory.getScriptEngine(new String[]{"--no-java"}, getClass().getClassLoader(), getClassFilter());
        try {
            engine.eval("var str = Java.type('java.lang.String');");
            fail("TypeError should have been thrown");
        } catch (ScriptException exc) {
        }
    }

    @Test
    public void securityTest() {
        if (System.getSecurityManager() == null) {
            return;
        }

        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        ScriptEngine engine = factory.getScriptEngine(getClassFilter());
        try {
            engine.eval("var thread = Java.type('sun.misc.Unsafe')");
            fail("SecurityException should have been thrown");
        } catch (final Exception exc) {
        }
        try {
            engine.eval("var thread = new sun.misc.Unsafe()");
            fail("SecurityException should have been thrown");
        } catch (final Exception exc) {
        }
        try {
            engine.eval("var thread = Java.extend(sun.misc.Unsafe, {})");
            fail("TypeError should have been thrown");
        } catch (final Exception exc) {
        }
        try {
            engine.eval("java.lang.System.exit(0)");
            fail("SecurityException should have been thrown");
        } catch (final Exception exc) {
        }

    }

    @Test
    public void persistentCacheTest() {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        ScriptEngine engine = factory.getScriptEngine(
                new String[]{"--persistent-code-cache"},
                getClass().getClassLoader(),
                getClassFilter()
        );
        String testScript = "var a = Java.type('java.lang.String');" + generateCodeForPersistentStore();
        try {
            engine.eval(testScript);
        } catch (final ScriptException exc) {
            fail(exc.getMessage());
        }
        ScriptEngine engineSafe = factory.getScriptEngine(
                new String[]{"--persistent-code-cache"},
                getClass().getClassLoader(),
                new ClassFilter() {
                    @Override
                    public boolean exposeToScripts(String s) {
                        return false;
                    }
                }
        );
        try {
            engineSafe.eval(testScript);
            fail("ClassNotFoundException should have been thrown");
        } catch (final Exception exc) {
            if (!(exc.getCause() instanceof ClassNotFoundException)) {
                fail("ClassNotFoundException expected");
            }
        }
    }

    private String generateCodeForPersistentStore() {
        StringBuilder stringBuilder = new StringBuilder();
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

    private ClassFilter getClassFilter() {
        return new ClassFilter() {
            @Override
            public boolean exposeToScripts(String s) {
                return true;
            }
        };
    }
}

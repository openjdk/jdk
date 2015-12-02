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

package jdk.nashorn.api.javaaccess.test;

import static org.testng.AssertJUnit.assertEquals;
import java.util.HashMap;
import java.util.Map;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.JSObject;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @run testng jdk.nashorn.api.javaaccess.test.ConsStringTest
 */
@SuppressWarnings("javadoc")
public class ConsStringTest {
    private static ScriptEngine e = null;

    public static void main(final String[] args) {
        TestNG.main(args);
    }

    @BeforeClass
    public static void setUpClass() {
        e = new ScriptEngineManager().getEngineByName("nashorn");
    }

    @AfterClass
    public static void tearDownClass() {
        e = null;
    }

    @Test
    public void testConsStringFlattening() throws ScriptException {
        final Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        final Map<Object, Object> m = new HashMap<>();
        b.put("m", m);
        e.eval("var x = 'f'; x += 'oo'; var y = 'b'; y += 'ar'; m.put(x, y)");
        assertEquals("bar", m.get("foo"));
    }

    @Test
    public void testConsStringFromMirror() throws ScriptException {
        final Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        //final Map<Object, Object> m = new HashMap<>();
        e.eval("var x = 'f'; x += 'oo'; var obj = {x: x};");
        assertEquals("foo", ((JSObject)b.get("obj")).getMember("x"));
    }

    @Test
    public void testArrayConsString() throws ScriptException {
        final Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        final ArrayHolder h = new ArrayHolder();
        b.put("h", h);
        e.eval("var x = 'f'; x += 'oo'; h.array = [x];");
        assertEquals(1, h.array.length);
        assertEquals("foo", h.array[0]);
    }


    public static class ArrayHolder {
        private Object[] array;

        public void setArray(final Object[] array) {
            this.array = array;
        }

        public Object[] getArray() {
            return array;
        }
    }
}

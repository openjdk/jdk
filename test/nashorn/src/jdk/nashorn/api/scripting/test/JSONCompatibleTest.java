/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @test
 * @run testng jdk.nashorn.api.scripting.test.JSONCompatibleTest
 */
public class JSONCompatibleTest {

    /**
     * Wrap a top-level array as a list.
     */
    @Test
    public void testWrapArray() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        final Object val = engine.eval("Java.asJSONCompatible([1, 2, 3])");
        assertEquals(asList(val), Arrays.asList(1, 2, 3));
    }

    /**
     * Wrap an embedded array as a list.
     */
    @Test
    public void testWrapObjectWithArray() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        final Object val = engine.eval("Java.asJSONCompatible({x: [1, 2, 3]})");
        assertEquals(asList(asMap(val).get("x")), Arrays.asList(1, 2, 3));
    }

    /**
     * Check it all works transitively several more levels down.
     */
    @Test
    public void testDeepWrapping() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        final Object val = engine.eval("Java.asJSONCompatible({x: [1, {y: [2, {z: [3]}]}, [4, 5]]})");
        final Map<String, Object> root = asMap(val);
        final List<Object> x = asList(root.get("x"));
        assertEquals(x.get(0), 1);
        final Map<String, Object> x1 = asMap(x.get(1));
        final List<Object> y = asList(x1.get("y"));
        assertEquals(y.get(0), 2);
        final Map<String, Object> y1 = asMap(y.get(1));
        assertEquals(asList(y1.get("z")), Arrays.asList(3));
        assertEquals(asList(x.get(2)), Arrays.asList(4, 5));
    }

    /**
     * Ensure that the old behaviour (every object is a Map) is unchanged.
     */
    @Test
    public void testNonWrapping() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        final Object val = engine.eval("({x: [1, {y: [2, {z: [3]}]}, [4, 5]]})");
        final Map<String, Object> root = asMap(val);
        final Map<String, Object> x = asMap(root.get("x"));
        assertEquals(x.get("0"), 1);
        final Map<String, Object> x1 = asMap(x.get("1"));
        final Map<String, Object> y = asMap(x1.get("y"));
        assertEquals(y.get("0"), 2);
        final Map<String, Object> y1 = asMap(y.get("1"));
        final Map<String, Object> z = asMap(y1.get("z"));
        assertEquals(z.get("0"), 3);
        final Map<String, Object> x2 = asMap(x.get("2"));
        assertEquals(x2.get("0"), 4);
        assertEquals(x2.get("1"), 5);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(final Object obj) {
        assertJSObject(obj);
        Assert.assertTrue(obj instanceof List);
        return (List)obj;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object obj) {
        assertJSObject(obj);
        Assert.assertTrue(obj instanceof Map);
        return (Map)obj;
    }

    private static void assertJSObject(final Object obj) {
        assertTrue(obj instanceof JSObject);
    }
}

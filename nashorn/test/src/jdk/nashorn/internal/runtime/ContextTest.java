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

import java.util.Map;
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
        final Context cx = new Context(options, errors);
        final ScriptObject oldGlobal = Context.getGlobal();
        Context.setGlobal(cx.createGlobal());
        try {
            String code = "22 + 10";
            assertTrue(32.0 == ((Number)(eval(cx, "<evalTest>", code))).doubleValue());

            code = "obj = { js: 'nashorn' }; obj.js";
            assertEquals("nashorn", eval(cx, "<evalTest2>", code));
        } finally {
            Context.setGlobal(oldGlobal);
        }
    }

    // basic check for JS reflection access
    @Test
    public void reflectionTest() {
        final Options options = new Options("");
        final ErrorManager errors = new ErrorManager();
        final Context cx = new Context(options, errors);
        final ScriptObject oldGlobal = Context.getGlobal();
        Context.setGlobal(cx.createGlobal());

        try {
            final String code = "var obj = { x: 344, y: 42 }";
            eval(cx, "<reflectionTest>", code);

            final Object obj = cx.getGlobal().get("obj");

            assertTrue(obj instanceof Map);

            @SuppressWarnings("unchecked")
            final Map<Object, Object> map = (Map<Object, Object>)obj;
            int count = 0;
            for (final Map.Entry<?, ?> ex : map.entrySet()) {
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
            assertEquals(2, count);
            assertEquals(2, map.size());

            // add property
            map.put("zee", "hello");
            assertEquals("hello", map.get("zee"));
            assertEquals(3, map.size());

        } finally {
            Context.setGlobal(oldGlobal);
        }
    }

    private Object eval(final Context cx, final String name, final String code) {
        final Source source = new Source(name, code);
        final ScriptObject global = Context.getGlobal();
        final ScriptFunction func = cx.compileScript(source, global, cx._strict);
        return func != null ? ScriptRuntime.apply(func, global) : null;
    }
}

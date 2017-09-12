/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.script.*;

import static org.testng.Assert.assertEquals;

/**
 * @bug 8182996
 * @summary Incorrect mapping Long type to JavaScript equivalent
 */
@SuppressWarnings("javadoc")
public class JDK_8182996_Test {

    private ScriptEngine engine;
    Bindings bindings;


    @BeforeClass
    public void setupTest() {
        engine = new ScriptEngineManager().getEngineByName("js");
        bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

        bindings.put("long1", Long.valueOf(1L));
        bindings.put("long2", Long.valueOf(2L));
        bindings.put("long2", Long.valueOf(3L));
    }

    @Test
    public void testType() throws ScriptException {
        assertEquals(engine.eval("typeof long1"), "object");
        assertEquals(engine.eval("typeof long2"), "object");
    }

    @Test
    public void testValue() throws ScriptException {
        assertEquals(engine.eval("long1"), Long.valueOf(1));
        assertEquals(engine.eval("long2"), Long.valueOf(3));
        assertEquals(bindings.get("long1"), Long.valueOf(1));
        assertEquals(bindings.get("long2"), Long.valueOf(3));
    }

}

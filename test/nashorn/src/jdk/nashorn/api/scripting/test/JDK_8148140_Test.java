/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.AbstractJSObject;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @bug 8148140
 * @summary arguments are handled differently in apply for JS functions and AbstractJSObjects
 */
public class JDK_8148140_Test {

    ScriptEngine engine;

    static final String RESULT = "[1, 2, 3]";

    @BeforeClass
    public void setupTest() {
        engine = new ScriptEngineManager().getEngineByName("js");
        engine.put("f", new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }
            @Override
            public Object call(final Object thiz, final Object... args) {
                return Arrays.deepToString(args);
            }
        });
    }

    @Test
    public void testCallF() throws ScriptException {
        assertEquals(RESULT, engine.eval("f(1,2,3)"));
    }

    @Test
    public void testApplyF() throws ScriptException {
        assertEquals(RESULT, engine.eval("Function.prototype.apply.call(f, null, [1,2,3])"));
    }

}

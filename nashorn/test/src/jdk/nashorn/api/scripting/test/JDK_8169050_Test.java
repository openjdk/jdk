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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @bug 8169050
 * @summary underscore_linker.js sample fails after dynalink changes for JDK-8168005
 */
public class JDK_8169050_Test {
    private ScriptEngine engine;

    @BeforeClass
    public void setupTest() {
        engine = new ScriptEngineManager().getEngineByName("js");
    }

    @Test
    public void testUndersoreName() throws ScriptException {
        engine.eval("var S = java.util.stream.Stream, v = 0;");
        // The underscore name 'for_each' exercises pluggable dynalink linker
        engine.eval("S.of(4, 5, 9).for_each(function(x) { v += x })");
        assertEquals(18, ((Number)engine.get("v")).intValue());
    }
}

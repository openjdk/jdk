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

package jdk.nashorn.api.scripting.test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.annotations.Test;

/**
 * Test that we can create multiple, independent script engines and use those
 * independently.
 *
 * @test
 * @run testng jdk.nashorn.api.scripting.test.MultipleEngineTest
 */
@SuppressWarnings("javadoc")
public class MultipleEngineTest {
    @Test
    public void createAndUseManyEngine() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();

        final ScriptEngine e1 = m.getEngineByName("nashorn");
        e1.eval("var  x = 33; print(x);");

        final ScriptEngine e2 = m.getEngineByName("nashorn");
        e2.eval("try { print(x) } catch(e) { print(e); }");
    }
}

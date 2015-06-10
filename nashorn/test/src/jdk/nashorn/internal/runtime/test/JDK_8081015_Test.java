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

package jdk.nashorn.internal.runtime.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Collection;
import java.util.Queue;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.test.models.JDK_8081015_TestModel;
import org.testng.annotations.Test;

/**
 * @bug 8081015
 * @summary Test that native arrays get converted to {@link Queue} and {@link Collection}.
 */
@SuppressWarnings("javadoc")
public class JDK_8081015_Test {
    @Test
    public void testConvertToCollection() throws ScriptException {
        test("receiveCollection");
    }

    @Test
    public void testConvertToDeque() throws ScriptException {
        test("receiveDeque");
    }

    @Test
    public void testConvertToList() throws ScriptException {
        test("receiveList");
    }

    @Test
    public void testConvertToQueue() throws ScriptException {
        test("receiveQueue");
    }

    private static void test(final String methodName) throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        final JDK_8081015_TestModel model = new JDK_8081015_TestModel();
        engine.put("test", model);

        assertNull(model.getLastInvoked());
        engine.eval("test." + methodName + "([1, 2, 3.3, 'foo'])");
        assertEquals(model.getLastInvoked(), methodName );
    }
}

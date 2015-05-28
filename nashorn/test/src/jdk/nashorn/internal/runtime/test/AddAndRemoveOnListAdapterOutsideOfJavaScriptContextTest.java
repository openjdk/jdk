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

import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * @bug 8081204
 * @summary adding and removing elements to a ListAdapter outside of JS context should work.
 */
@SuppressWarnings("javadoc")
public class AddAndRemoveOnListAdapterOutsideOfJavaScriptContextTest {

    @SuppressWarnings("unchecked")
    private static <T> T getListAdapter() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        return (T)engine.eval("Java.to([1, 2, 3, 4], 'java.util.List')");
    }

    @Test
    public void testInvokePush() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        l.addLast(5);
        assertEquals(l.size(), 5);
        assertEquals(l.getLast(), 5);
        assertEquals(l.getFirst(), 1);
    }

    @Test
    public void testPop() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        assertEquals(l.removeLast(), 4);
        assertEquals(l.size(), 3);
        assertEquals(l.getLast(), 3);
    }

    @Test
    public void testUnshift() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        l.addFirst(0);
        assertEquals(l.getFirst(), 0);
        assertEquals(l.getLast(), 4);
        assertEquals(l.size(), 5);
    }

    @Test
    public void testShift() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        l.removeFirst();
        assertEquals(l.getFirst(), 2);
        assertEquals(l.getLast(), 4);
        assertEquals(l.size(), 3);
    }

    @Test
    public void testSpliceAdd() throws ScriptException {
        final List<Object> l = getListAdapter();
        assertEquals(l, Arrays.asList(1, 2, 3, 4));
        l.add(2, "foo");
        assertEquals(l, Arrays.asList(1, 2, "foo", 3, 4));
    }


    @Test
    public void testSpliceRemove() throws ScriptException {
        final List<Object> l = getListAdapter();
        assertEquals(l, Arrays.asList(1, 2, 3, 4));
        l.remove(2);
        assertEquals(l, Arrays.asList(1, 2, 4));
    }
}

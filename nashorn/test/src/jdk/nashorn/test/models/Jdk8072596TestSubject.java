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

package jdk.nashorn.test.models;

import java.util.List;
import java.util.Map;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.ScriptObject;
import org.testng.Assert;

public class Jdk8072596TestSubject {

    public Jdk8072596TestSubject(final Object x) {
        Assert.assertTrue(x instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)x).get("bar"), 0);
    }

    // Test having to wrap some arguments but not others
    public void test1(final String x, final Object y, final ScriptObject w) {
        Assert.assertEquals(x, "true");

        Assert.assertTrue(y instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)y).get("foo"), 1);

        Assert.assertEquals(w.get("bar"), 2);
    }

    // Test having to wrap some arguments but not others, and a vararg array
    public void test2(String x, final Object y, final ScriptObject w, final Object... z) {
        test1(x, y, w);

        Assert.assertEquals(z.length, 2);

        Assert.assertTrue(z[0] instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)z[0]).get("baz"), 3);

        Assert.assertTrue(z[1] instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)z[1]).get("bing"), 4);
    }

    // Test mixed (wrappable and non-wrappable) elements in a vararg array
    public void test3(final Object... z) {
        Assert.assertEquals(z.length, 5);

        Assert.assertEquals(z[0], true);

        Assert.assertTrue(z[1] instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)z[1]).get("foo"), 5);

        Assert.assertEquals(z[2], "hello");

        Assert.assertTrue(z[3] instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)z[3]).getSlot(0), 6);
        Assert.assertEquals(((ScriptObjectMirror)z[3]).getSlot(1), 7);

        Assert.assertEquals(z[4], 8);
    }

    // test wrapping the first argument of a static method
    public static void test4(final Object x) {
        Assert.assertTrue(x instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)x).get("foo"), 9);
    }

    public void testListHasWrappedObject(final List<?> l) {
        Assert.assertEquals(l.size(), 1);
        Assert.assertTrue(l.get(0) instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)l.get(0)).get("foo"), 10);
    }

    public void testArrayHasWrappedObject(final Object[] a) {
        Assert.assertEquals(a.length, 1);
        Assert.assertTrue(a[0] instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)a[0]).get("bar"), 11);
    }

    public void testMapHasWrappedObject(final Map<?, ?> m, final Object key) {
        Assert.assertEquals(m.size(), 1);
        Assert.assertTrue(key instanceof ScriptObjectMirror);
        Assert.assertTrue(m.get(key) instanceof ScriptObjectMirror);
        Assert.assertEquals(((ScriptObjectMirror)m.get(key)).get("bar"), 12);
    }
}

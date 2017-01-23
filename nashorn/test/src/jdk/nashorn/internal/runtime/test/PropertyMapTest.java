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

package jdk.nashorn.internal.runtime.test;

import java.util.Iterator;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for PropertyMap functionality
 *
 * @test
 * @modules jdk.scripting.nashorn/jdk.nashorn.internal.runtime
 * @run testng jdk.nashorn.internal.runtime.test.PropertyMapTest
 */
@SuppressWarnings("javadoc")
public class PropertyMapTest {

    @Test
    public void propertyMapIteratorTest() {
        final ScriptObject scriptObject = new ScriptObject(PropertyMap.newMap()) {};
        Assert.assertFalse(scriptObject.getMap().iterator().hasNext());

        scriptObject.set("a", "a", 0);
        scriptObject.set("b", 3, 0);
        // 3 is a valid array key not stored in property map
        scriptObject.set(3, 1, 0);
        scriptObject.set(6.5, 1.3, 0);
        final Iterator<Object> iterator = scriptObject.getMap().iterator();

        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), "a");
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), "b");
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), "6.5");
        Assert.assertFalse(iterator.hasNext());
    }

}

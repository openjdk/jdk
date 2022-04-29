/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.IdentityHashMap;

/*
 * @test
 * @bug 8178355
 * @summary Use identity-based comparison for IdentityHashMap#remove and #replace
 * @run testng DefaultRemoveReplaceTest
 */
public class DefaultRemoveReplaceTest {
    /** A minimal record that can represent difference in equality and identity. */
    record Box(int i) { }

    private static final Box ONE = new Box(1);
    private static final Box ANOTHER_ONE = new Box(1);
    private static final Box SIX = new Box(6);
    private static final Box THREE = new Box(3);

    static {
        Assert.assertEquals(ANOTHER_ONE, ONE);
        Assert.assertNotSame(ANOTHER_ONE, ONE);
    }

    @DataProvider
    public Object[][] makeMap() {
        var map = new IdentityHashMap<Box, Box>();
        map.put(null, THREE);
        map.put(THREE, ONE);
        map.put(ONE, SIX);
        map.put(ANOTHER_ONE, null);

        Assert.assertEquals(map.size(), 4);

        return new Object[][] {
                { map }
        };
    }

    @Test(dataProvider = "makeMap")
    public void testReplaceUnequal(IdentityHashMap<Box, Box> map) {
        var seven = new Box(7);
        var fakeThree = new Box(3);
        Assert.assertFalse(map.replace(seven, ONE, fakeThree));

        // Make sure false return has no side effects
        Assert.assertFalse(map.containsKey(seven));
        Assert.assertTrue(map.containsValue(ONE));
        Assert.assertFalse(map.containsValue(fakeThree));
        Assert.assertEquals(map.size(), 4);
    }

    @Test(dataProvider = "makeMap")
    public void testReplaceEqualKey(IdentityHashMap<Box, Box> map) {
        var seven = new Box(7);
        var fakeThree = new Box(3);
        Assert.assertFalse(map.replace(fakeThree, ONE, seven));

        // Make sure false return has no side effects
        Assert.assertTrue(map.containsKey(THREE));
        Assert.assertFalse(map.containsKey(fakeThree));
        Assert.assertTrue(map.containsValue(ONE));
        Assert.assertFalse(map.containsValue(seven));
        Assert.assertEquals(map.size(), 4);
    }

    @Test(dataProvider = "makeMap")
    public void testReplaceEqualValue(IdentityHashMap<Box, Box> map) {
        var seven = new Box(7);
        var fakeOne = new Box(1);
        Assert.assertFalse(map.replace(THREE, fakeOne, seven));

        // Make sure false return has no side effects
        Assert.assertTrue(map.containsKey(THREE));
        Assert.assertSame(map.get(THREE), ONE);
        Assert.assertTrue(map.containsValue(ONE));
        Assert.assertFalse(map.containsValue(seven));
        Assert.assertFalse(map.containsValue(fakeOne));
        Assert.assertEquals(map.size(), 4);
    }

    @Test(dataProvider = "makeMap")
    public void testReplaceSuccess(IdentityHashMap<Box, Box> map) {
        var seven = new Box(7);
        Assert.assertTrue(map.replace(ANOTHER_ONE, null, seven));

        // Check aftereffects
        Assert.assertTrue(map.containsKey(ANOTHER_ONE));
        Assert.assertSame(map.get(ANOTHER_ONE), seven);
        Assert.assertTrue(map.containsValue(seven));
        Assert.assertFalse(map.containsValue(null));
        Assert.assertTrue(map.containsValue(seven));
        Assert.assertEquals(map.size(), 4);
    }

    @Test(dataProvider = "makeMap")
    public void testRemoveUnequal(IdentityHashMap<Box, Box> map) {
        Assert.assertFalse(map.remove(THREE, SIX));

        // Make sure false return has no side effects
        Assert.assertTrue(map.containsKey(THREE));
        Assert.assertTrue(map.containsValue(SIX));
        Assert.assertSame(map.get(ONE), SIX);
        Assert.assertSame(map.get(THREE), ONE);
        Assert.assertEquals(map.size(), 4);
    }

    @Test(dataProvider = "makeMap")
    public void testRemoveEqualKey(IdentityHashMap<Box, Box> map) {
        var fakeThree = new Box(3);
        Assert.assertFalse(map.remove(null, fakeThree));

        // Make sure false return has no side effects
        Assert.assertTrue(map.containsKey(null));
        Assert.assertSame(map.get(null), THREE);
        Assert.assertTrue(map.containsValue(THREE));
        Assert.assertFalse(map.containsValue(fakeThree));
        Assert.assertEquals(map.size(), 4);
    }

    @Test(dataProvider = "makeMap")
    public void testRemoveSuccess(IdentityHashMap<Box, Box> map) {
        Assert.assertTrue(map.remove(ONE, SIX));

        // Check aftereffects
        Assert.assertFalse(map.containsKey(ONE));
        Assert.assertFalse(map.containsValue(SIX));
        Assert.assertTrue(map.containsKey(ANOTHER_ONE));
        Assert.assertTrue(map.containsValue(null));
        Assert.assertNull(map.get(ONE));
        Assert.assertNull(map.get(ANOTHER_ONE));
        Assert.assertEquals(map.size(), 3);
    }
}

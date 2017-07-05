/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.nashorn.internal.runtime.ConsString;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * Tests for JSType methods.
 *
 * @test
 * @modules jdk.scripting.nashorn/jdk.nashorn.internal.runtime
 * @run testng jdk.nashorn.internal.runtime.test.ConsStringTest
 */
public class ConsStringTest {

    /**
     * Test toString conversion
     */
    @Test
    public void testConsStringToString() {
        final ConsString cs1 = new ConsString("b", "c");
        final ConsString cs2 = new ConsString("d", "e");
        final ConsString cs3 = new ConsString(cs1, cs2);
        final ConsString cs4 = new ConsString(cs3, "f");
        final ConsString cs5 = new ConsString("a", cs4);
        assertEquals(cs5.toString(), "abcdef");
        assertEquals(cs4.toString(), "bcdef");
        assertEquals(cs3.toString(), "bcde");
        assertEquals(cs2.toString(), "de");
        assertEquals(cs1.toString(), "bc");
        // ConsStrings should be flattened now
        assertEquals(cs1.getComponents()[0], "bc");
        assertEquals(cs1.getComponents()[1], "");
        assertEquals(cs2.getComponents()[0], "de");
        assertEquals(cs2.getComponents()[1], "");
        assertEquals(cs3.getComponents()[0], "bcde");
        assertEquals(cs3.getComponents()[1], "");
        assertEquals(cs4.getComponents()[0], "bcdef");
        assertEquals(cs4.getComponents()[1], "");
        assertEquals(cs5.getComponents()[0], "abcdef");
        assertEquals(cs5.getComponents()[1], "");
    }

    /**
     * Test charAt
     */
    @Test
    public void testConsStringCharAt() {
        final ConsString cs1 = new ConsString("b", "c");
        final ConsString cs2 = new ConsString("d", "e");
        final ConsString cs3 = new ConsString(cs1, cs2);
        final ConsString cs4 = new ConsString(cs3, "f");
        final ConsString cs5 = new ConsString("a", cs4);
        assertEquals(cs1.charAt(1), 'c');
        assertEquals(cs2.charAt(0), 'd');
        assertEquals(cs3.charAt(3), 'e');
        assertEquals(cs4.charAt(1), 'c');
        assertEquals(cs5.charAt(2), 'c');
        // ConsStrings should be flattened now
        assertEquals(cs1.getComponents()[0], "bc");
        assertEquals(cs1.getComponents()[1], "");
        assertEquals(cs2.getComponents()[0], "de");
        assertEquals(cs2.getComponents()[1], "");
        assertEquals(cs3.getComponents()[0], "bcde");
        assertEquals(cs3.getComponents()[1], "");
        assertEquals(cs4.getComponents()[0], "bcdef");
        assertEquals(cs4.getComponents()[1], "");
        assertEquals(cs5.getComponents()[0], "abcdef");
        assertEquals(cs5.getComponents()[1], "");
    }


    /**
     * Test flattening of top-level and internal ConsStrings
     */
    @Test
    public void testConsStringFlattening() {
        final ConsString cs1 = new ConsString("b", "c");
        final ConsString cs2 = new ConsString("d", "e");
        final ConsString cs3 = new ConsString(cs1, cs2);
        final ConsString cs4 = new ConsString(cs3, "f");

        final ConsString cs5 = new ConsString("a", cs4);
        // top-level ConsString should not yet be flattened
        assert(cs5.getComponents()[0] == "a");
        assert(cs5.getComponents()[1] == cs4);
        assertEquals(cs5.toString(), "abcdef");
        // top-level ConsString should be flattened
        assertEquals(cs5.getComponents()[0], "abcdef");
        assertEquals(cs5.getComponents()[1], "");
        // internal ConsString should not yet be flattened after first traversal
        assertEquals(cs4.getComponents()[0], cs3);
        assertEquals(cs4.getComponents()[1], "f");

        final ConsString cs6 = new ConsString("a", cs4);
        // top-level ConsString should not yet be flattened
        assertEquals(cs6.getComponents()[0], "a");
        assertEquals(cs6.getComponents()[1], cs4);
        assertEquals(cs6.toString(), "abcdef");
        // top-level ConsString should be flattened
        assertEquals(cs6.getComponents()[0], "abcdef");
        assertEquals(cs6.getComponents()[1], "");
        // internal ConsString should have been flattened after second traversal
        assertEquals(cs4.getComponents()[0], "bcdef");
        assertEquals(cs4.getComponents()[1], "");
    }
}

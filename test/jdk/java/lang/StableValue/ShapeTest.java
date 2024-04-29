/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic tests for StableArray.Shape implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} ShapeTest.java
 * @compile Util.java
 * @run junit/othervm --enable-preview ShapeTest
 */

import jdk.internal.lang.StableArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ShapeTest {

    @Test
    void empty() {
        StableArray.Shape shape = StableArray.Shape.of();
        assertEquals(0, shape.size());
        assertTrue(shape.isZeroDimensional());
        assertEquals(0, shape.nDimensions());
        var e = assertThrows(IllegalArgumentException.class, () -> shape.dimension(1));
        var msg = e.getMessage();
        assertTrue(msg.contains("1"), msg);
        var e2 = assertThrows(IllegalArgumentException.class, () -> shape.dimension(-1));
        var msg2 = e2.getMessage();
        assertTrue(msg2.contains("-1"), msg2);
        assertEquals("Shape[] (0)", shape.toString());
    }

    @Test
    void one() {
        StableArray.Shape shape = StableArray.Shape.of(42);
        assertEquals(42, shape.size());
        assertFalse(shape.isZeroDimensional());
        assertEquals(1, shape.nDimensions());
        assertEquals(42, shape.dimension(0));
        var e = assertThrows(IllegalArgumentException.class, () -> shape.dimension(2));
        var msg = e.getMessage();
        assertTrue(msg.contains("2"), msg);
        var e2 = assertThrows(IllegalArgumentException.class, () -> shape.dimension(-1));
        var msg2 = e2.getMessage();
        assertTrue(msg2.contains("-1"), msg2);
        assertEquals("Shape[42] (42)", shape.toString());
    }

    @Test
    void two() {
        StableArray.Shape shape = StableArray.Shape.of(42, 13);
        assertEquals(42 * 13, shape.size());
        assertFalse(shape.isZeroDimensional());
        assertEquals(2, shape.nDimensions());
        assertEquals(42, shape.dimension(0));
        assertEquals(13, shape.dimension(1));
        var e = assertThrows(IllegalArgumentException.class, () -> shape.dimension(2));
        var msg = e.getMessage();
        assertTrue(msg.contains("2"), msg);
        var e2 = assertThrows(IllegalArgumentException.class, () -> shape.dimension(-1));
        var msg2 = e2.getMessage();
        assertTrue(msg2.contains("-1"), msg2);
        assertEquals("Shape[42, 13] (" + (42 * 13) + ")", shape.toString());
    }

}

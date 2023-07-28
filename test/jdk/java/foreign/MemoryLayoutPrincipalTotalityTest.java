/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @run testng/othervm MemoryLayoutPrincipalTotalityTest
 */

import org.testng.annotations.*;

import java.lang.foreign.*;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class MemoryLayoutPrincipalTotalityTest {

    // The tests in this class is mostly there to ensure compile-time pattern matching totality.

    @Test
    public void testBasicTotality() {
        MemoryLayout memoryLayout = javaIntMemoryLayout();
        int v0 = switch (memoryLayout) {
            case MemoryLayout ml -> 1;
        };
        assertEquals(v0, 1);
    }

    @Test
    public void testMLRemovedTotality() {
        MemoryLayout memoryLayout = javaIntMemoryLayout();
        var v1 = switch (memoryLayout) {
            case GroupLayout gl -> 0;
            case PaddingLayout pl -> 0; // leaf
            case SequenceLayout sl -> 0; // leaf
            case ValueLayout vl -> 1;
        };
        assertEquals(v1, 1);
    }

    @Test
    public void testMLGLRemovedTotality() {
        MemoryLayout memoryLayout = javaIntMemoryLayout();
        var v2 = switch (memoryLayout) {
            case PaddingLayout pl -> 0; // leaf
            case SequenceLayout sl -> 0; // leaf
            case ValueLayout vl -> 1;
            case StructLayout sl -> 0; // leaf
            case UnionLayout ul -> 0; // leaf
        };
        assertEquals(v2, 1);
    }

    @Test
    public void testMLGLVLRemovedTotality() {
        MemoryLayout memoryLayout = javaIntMemoryLayout();
        var v3 = switch (memoryLayout) {
            case PaddingLayout pl -> 0; // leaf
            case SequenceLayout sl -> 0; // leaf
            case StructLayout sl -> 0; // leaf
            case UnionLayout ul -> 0; // leaf
            case AddressLayout oa -> 0; // leaf
            case OfBoolean ob -> 0; // leaf
            case OfByte ob -> 0; // leaf
            case OfChar oc -> 0; // leaf
            case OfDouble od -> 0; // leaf
            case OfFloat of -> 0; // leaf
            case OfInt oi -> 1; // leaf
            case OfLong ol -> 0; // leaf
            case OfShort os -> 0; // leaf
        };
        assertEquals(v3, 1);
    }

    @Test
    public void testMLVLRemovedTotality() {
        MemoryLayout memoryLayout = javaIntMemoryLayout();
        var v4 = switch (memoryLayout) {
            case GroupLayout gl -> 0;
            case PaddingLayout pl -> 0; // leaf
            case SequenceLayout sl -> 0; // leaf
            case AddressLayout oa -> 0; // leaf
            case OfBoolean ob -> 0; // leaf
            case OfByte ob -> 0; // leaf
            case OfChar oc -> 0; // leaf
            case OfDouble od -> 0; // leaf
            case OfFloat of -> 0; // leaf
            case OfInt oi -> 1; // leaf
            case OfLong ol -> 0; // leaf
            case OfShort os -> 0; // leaf
        };
        assertEquals(v4, 1);
    }

    private static MemoryLayout javaIntMemoryLayout() {
        return JAVA_INT;
    }

}

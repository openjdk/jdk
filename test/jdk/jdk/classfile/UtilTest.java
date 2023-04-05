/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Testing Classfile Util.
 * @run junit UtilTest
 */
import jdk.internal.classfile.impl.Util;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UtilTest
 */
class UtilTest {
    @Test
    void testFindParams() {
        assertEquals(Util.findParams("(IIII)V").cardinality(), 4);
        assertEquals(Util.findParams("([I[I[I[I)V").cardinality(), 4);
        assertEquals(Util.findParams("(IJLFoo;IJ)V").cardinality(), 5);
        assertEquals(Util.findParams("([[[[I)V").cardinality(), 1);
        assertEquals(Util.findParams("([[[[LFoo;)V").cardinality(), 1);
        assertEquals(Util.findParams("([I[LFoo;)V").cardinality(), 2);
        assertEquals(Util.findParams("()V").cardinality(), 0);
    }

    @Test
    void testParameterSlots() {
        assertEquals(Util.parameterSlots("(IIII)V"), 4);
        assertEquals(Util.parameterSlots("([I[I[I[I)V"), 4);
        assertEquals(Util.parameterSlots("(IJLFoo;IJ)V"), 7);
        assertEquals(Util.parameterSlots("([[[[I)V"), 1);
        assertEquals(Util.parameterSlots("([[[[LFoo;)V"), 1);
        assertEquals(Util.parameterSlots("([I[LFoo;)V"), 2);
        assertEquals(Util.parameterSlots("()V"), 0);
        assertEquals(Util.parameterSlots("(I)V"), 1);
        assertEquals(Util.parameterSlots("(S)V"), 1);
        assertEquals(Util.parameterSlots("(C)V"), 1);
        assertEquals(Util.parameterSlots("(B)V"), 1);
        assertEquals(Util.parameterSlots("(Z)V"), 1);
        assertEquals(Util.parameterSlots("(F)V"), 1);
        assertEquals(Util.parameterSlots("(LFoo;)V"), 1);
        assertEquals(Util.parameterSlots("(J)V"), 2);
        assertEquals(Util.parameterSlots("(D)V"), 2);
        assertEquals(Util.parameterSlots("([J)V"), 1);
        assertEquals(Util.parameterSlots("([D)V"), 1);
    }
}

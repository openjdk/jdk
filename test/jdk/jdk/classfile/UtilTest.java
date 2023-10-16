/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing Classfile Util.
 * @run junit UtilTest
 */
import java.lang.constant.MethodTypeDesc;
import jdk.internal.classfile.impl.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UtilTest
 */
class UtilTest {
    @ParameterizedTest
    @ValueSource(classes = {
            Long.class,
            Object.class,
            Util.class,
            Test.class,
    })
    void testDescToBinaryName(Class<?> type) throws ReflectiveOperationException {
        var cd = type.describeConstable().orElseThrow();
        assertEquals(type, Class.forName(Util.toBinaryName(cd)));
        assertEquals(type.getName(), Util.toBinaryName(cd));
    }

    @Test
    void testParameterSlots() {
        assertSlots("(IIII)V", 4);
        assertSlots("([I[I[I[I)V", 4);
        assertSlots("(IJLFoo;IJ)V", 7);
        assertSlots("([[[[I)V", 1);
        assertSlots("([[[[LFoo;)V", 1);
        assertSlots("([I[LFoo;)V", 2);
        assertSlots("()V", 0);
        assertSlots("(I)V", 1);
        assertSlots("(S)V", 1);
        assertSlots("(C)V", 1);
        assertSlots("(B)V", 1);
        assertSlots("(Z)V", 1);
        assertSlots("(F)V", 1);
        assertSlots("(LFoo;)V", 1);
        assertSlots("(J)V", 2);
        assertSlots("(D)V", 2);
        assertSlots("([J)V", 1);
        assertSlots("([D)V", 1);
    }

    private void assertSlots(String methodDesc, int slots) {
        assertEquals(Util.parameterSlots(MethodTypeDesc.ofDescriptor(methodDesc)), slots);
    }
}

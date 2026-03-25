/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test ConstantDesc for value classes
 * @enablePreview
 * @run junit/othervm ValueConstantDesc
 */

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

public class ValueConstantDesc {
    static value class V { }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static Stream<Arguments> testCases() {
        return Stream.of(
                Arguments.of(V.class, "LValueConstantDesc$V;", "ValueConstantDesc$V"),
                Arguments.of(V[].class, "[LValueConstantDesc$V;", "ValueConstantDesc$V[]"),
                Arguments.of(V[][].class, "[[LValueConstantDesc$V;", "ValueConstantDesc$V[][]")
        );
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void classDesc(Class<?> type, String descriptor, String displayName) throws ReflectiveOperationException {
        ClassDesc cd = type.describeConstable().orElseThrow();
        ClassDesc desc = ClassDesc.ofDescriptor(descriptor);

        if (type.isArray()) {
            assertTrue(cd.isArray());
            assertFalse(cd.isClassOrInterface());
        } else {
            assertFalse(cd.isArray());
            assertTrue(cd.isClassOrInterface());
        }
        assertEquals(cd, desc);
        assertEquals(cd.displayName(), displayName);
        assertEquals(cd.descriptorString(), type.descriptorString());

        Class<?> c = cd.resolveConstantDesc(LOOKUP);
        assertTrue(c == type);
    }
}

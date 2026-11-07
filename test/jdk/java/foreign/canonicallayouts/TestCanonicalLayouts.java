/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../ /test/lib
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED TestCanonicalLayouts
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestCanonicalLayouts extends NativeTestHelper {

    static {
        System.loadLibrary("CanonicalLayouts");
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testCanonicalLayout(String name) {
        MemoryLayout layout = LINKER.canonicalLayouts().get(name);
        assertNotNull(layout);
        assertEquals(nativeSizeFor(name), layout.byteSize());
        assertEquals(nativeAlignFor(name), layout.byteAlignment());
        if (layout instanceof ValueLayout.OfIntegral integral) {
            assertEquals(nativeSignednessFor(name), integral.isSigned());
        }
    }

    private boolean nativeSignednessFor(String name) {
        try {
            return (boolean) downcallHandle(
                    escapeName(name) + "_signedness",
                    FunctionDescriptor.of(C_BOOL)
            ).invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private long nativeAlignFor(String name) {
        try {
            return (long) downcallHandle(
                    escapeName(name) + "_align",
                    FunctionDescriptor.of(C_LONG_LONG)
            ).invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private long nativeSizeFor(String name) {
        try {
            return (long) downcallHandle(
                    escapeName(name) + "_size",
                    FunctionDescriptor.of(C_LONG_LONG)
            ).invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static String escapeName(String str) {
        return str.replace(' ', '_').replace("*", "_star");
    }

    public static Stream<String> cases() {
        // As defined by the Linker javadoc
        return Stream.of(
                "bool",
                "char",
                "unsigned char",
                "short",
                "unsigned short",
                "int",
                "unsigned int",
                "long",
                "unsigned long",
                "long long",
                "unsigned long long",
                "float",
                "double",
                "size_t",
                "wchar_t",
                "void*",
                "int8_t",
                "int16_t",
                "int32_t",
                "int64_t",
                "uint8_t",
                "uint16_t",
                "uint32_t",
                "uint64_t"
        );
    }
}


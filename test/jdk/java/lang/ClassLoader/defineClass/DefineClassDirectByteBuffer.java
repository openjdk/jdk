/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test various cases of passing java.nio.ByteBuffers to defineClass().
 * @bug 8365588
 *
 * @library /lib/testlibrary/java/lang
 * @build DefineClassDirectByteBuffer
 * @run junit/othervm --add-opens java.base/java.lang=ALL-UNNAMED -Dmode=Direct DefineClassDirectByteBuffer
 * @run junit/othervm --add-opens java.base/java.lang=ALL-UNNAMED -Dmode=Heap DefineClassDirectByteBuffer
 */

import java.lang.foreign.Arena;
import java.lang.reflect.Method;
import java.nio.*;
import java.nio.channels.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.HexFormat;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class DefineClassDirectByteBuffer {
    // this is for the trusted/biltin classloader
    private static final String mode = System.getProperty("mode", "Direct");
    private static final int CLASSBYTES_OFFSET = 16;

    // -------- untrusted path (custom loader) --------
    static Stream<Arguments> bytebuffers() throws Exception {
        byte[] classBytes = getTestClassBytes();
        byte[] classBytesAtOffset = new byte[classBytes.length + CLASSBYTES_OFFSET];
        System.arraycopy(classBytes, 0, classBytesAtOffset, CLASSBYTES_OFFSET, classBytes.length);

        return Stream.of(
                // WRAPPED_BUFFER
                arguments(ByteBuffer.wrap(classBytes),
                        false),
                arguments(ByteBuffer.wrap(classBytes)
                                .asReadOnlyBuffer(),
                        true),
                arguments(ByteBuffer.wrap(classBytesAtOffset)
                                .position(CLASSBYTES_OFFSET),
                        false),
                arguments(ByteBuffer
                                .wrap(classBytesAtOffset)
                                .position(CLASSBYTES_OFFSET)
                                .asReadOnlyBuffer(),
                        true),
                // ARRAY_BUFFER
                arguments(ByteBuffer.allocate(classBytes.length)
                                .put(classBytes)
                                .flip(),
                        false),
                arguments(ByteBuffer.allocate(classBytes.length)
                                .put(classBytes)
                                .flip()
                                .position(0)
                                .asReadOnlyBuffer(),
                        true),
                arguments(ByteBuffer.allocate(classBytesAtOffset.length)
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET),
                        false),
                arguments(ByteBuffer.allocate(classBytesAtOffset.length)
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET)
                                .asReadOnlyBuffer(),
                        true),
                // DIRECT_BUFFER
                arguments(ByteBuffer.allocateDirect(classBytes.length)
                                .put(classBytes)
                                .flip(),
                        false),
                arguments(ByteBuffer.allocateDirect(classBytes.length)
                                .put(classBytes)
                                .flip()
                                .asReadOnlyBuffer(),
                        false),
                arguments(ByteBuffer.allocateDirect(classBytesAtOffset.length)
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET),
                        false),
                arguments(ByteBuffer.allocateDirect(classBytesAtOffset.length)
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET)
                                .asReadOnlyBuffer(),
                        false),
                // FOREIGN_AUTO_BUFFER
                arguments(Arena.ofAuto()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip(),
                        false),
                arguments(Arena.ofAuto()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip()
                                .asReadOnlyBuffer(),
                        false),
                arguments(Arena.ofAuto()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET),
                        false),
                arguments(Arena.ofAuto()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET)
                                .asReadOnlyBuffer(),
                        false),
                // FOREIGN_CONFINED_BUFFER
                arguments(Arena.ofConfined()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip(),
                        false),
                arguments(Arena.ofConfined()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip()
                                .asReadOnlyBuffer(),
                        false),
                arguments(Arena.ofConfined()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET),
                        false),
                arguments(Arena.ofConfined()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET)
                                .asReadOnlyBuffer(),
                        false),
                // FOREIGN_GLOBAL_BUFFER
                arguments(Arena.global()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip(),
                        false),
                arguments(Arena.global()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip()
                                .asReadOnlyBuffer(),
                        false),
                arguments(Arena.global()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET),
                        false),
                arguments(Arena.global()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET)
                                .asReadOnlyBuffer(),
                        false),
                // FOREIGN_SHARED_BUFFER
                arguments(Arena.ofShared()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip(),
                        false),
                arguments(Arena.ofShared()
                                .allocate(classBytes.length)
                                .asByteBuffer()
                                .put(classBytes)
                                .flip()
                                .asReadOnlyBuffer(),
                        false),
                arguments(Arena.ofShared()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET),
                        false),
                arguments(Arena.ofShared()
                                .allocate(classBytesAtOffset.length)
                                .asByteBuffer()
                                .put(classBytesAtOffset)
                                .flip()
                                .position(CLASSBYTES_OFFSET)
                                .asReadOnlyBuffer(),
                        false),
                // MAPPED_BUFFER:  MapMode.READ_ONLY from READ fc, the bb is readonly
                arguments(getMappedByteBuffer(classBytes, 0), false),
                arguments(getMappedByteBuffer(classBytesAtOffset, CLASSBYTES_OFFSET), false)
        );
    }

    static ByteBuffer getMappedByteBuffer(byte[] classBytes, int offset) throws Exception {
        Path tempDir = Paths.get(System.getProperty("test.classes", "."));
        Files.createDirectories(tempDir);
        Path tempClassFile = tempDir.resolve(
                String.format("DefineClassDirectByteBuffer_Greeting_%d.class", offset));
        Files.write(tempClassFile, classBytes);
        try (FileChannel fc = FileChannel.open(tempClassFile, StandardOpenOption.READ)) {
            return fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size()).position(offset);
        }
    }

    @ParameterizedTest()
    @MethodSource("bytebuffers")
    void testDefineClassWithCustomLoaderByteBuffer(ByteBuffer bb, boolean posAtLimit)
            throws Exception {
        CustomClassLoader loader = new CustomClassLoader();
        Class<?> clazz = loader.defineClassFromByteBuffer(bb);
        assertInvocating(clazz);
        assertEquals(posAtLimit, bb.position() == bb.limit());
    }

    // -------- trusted path (BuiltinClassLoader) --------
    @Test
    void testDefineClassWithBuiltinLoaderByteBuffer() throws Exception {
        var classBytes = getTestClassBytes();
        var builtin = ClassLoader.getPlatformClassLoader();
        var bb = mode.equals("Direct")
                ? ByteBuffer.allocateDirect(classBytes.length).put(classBytes).flip()
                : ByteBuffer.allocate(classBytes.length).put(classBytes).flip();
        // reflectively call protected defineClass(String, ByteBuffer, ProtectionDomain)
        Method m = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, ByteBuffer.class, ProtectionDomain.class
        );
        m.setAccessible(true);
        Class<?> clazz = (Class<?>) m.invoke(builtin, null, bb, null);
        assertInvocating(clazz);
    }

    // -------- shared helpers --------
    private static void assertInvocating(Class<?> clazz) throws Exception {
        var instance = clazz.getDeclaredConstructor().newInstance();
        var m = clazz.getMethod("hello");
        assertEquals("Hello", m.invoke(instance));
    }

    private static class CustomClassLoader extends ClassLoader {
        Class<?> defineClassFromByteBuffer(ByteBuffer bb) throws Exception {
            return defineClass(null, bb, null);
        }
    }

    private static byte[] getTestClassBytes() throws Exception {
        final String source = """
                public class Greeting {
                    public String hello() {
                        return "Hello";
                    }
                }
                """;
        // (externally) compiled content of the above source, represented as hex
        final String classBytesHex = """
                cafebabe0000004600110a000200030700040c000500060100106a617661
                2f6c616e672f4f626a6563740100063c696e69743e010003282956080008
                01000548656c6c6f07000a0100084772656574696e67010004436f646501
                000f4c696e654e756d6265725461626c6501000568656c6c6f0100142829
                4c6a6176612f6c616e672f537472696e673b01000a536f7572636546696c
                6501000d4772656574696e672e6a61766100210009000200000000000200
                01000500060001000b0000001d00010001000000052ab70001b100000001
                000c000000060001000000010001000d000e0001000b0000001b00010001
                000000031207b000000001000c000000060001000000030001000f000000
                020010
                """;
        return HexFormat.of().parseHex(classBytesHex.replaceAll("\n", ""));
    }
}

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

/*
 * @test
 * @summary Testing ClassFile CP Utf8Entry.
 * @run junit Utf8EntryTest
 */
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.function.UnaryOperator;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.classfile.TypeKind.VoidType;

class Utf8EntryTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "ascii",

            "prefix\u0080\u0080\u0080postfix",
            "prefix\u0080\u0080\u0080",
            "\u0080\u0080\u0080postfix",
            "\u0080\u0080\u0080",

            "prefix\u07FF\u07FF\u07FFpostfix",
            "prefix\u07FF\u07FF\u07FF",
            "\u07FF\u07FF\u07FFpostfix",
            "\u07FF\u07FF\u07FF",

            "prefix\u0800\u0800\u0800postfix",
            "prefix\u0800\u0800\u0800",
            "\u0800\u0800\u0800postfix",
            "\u0800\u0800\u0800",

            "prefix\uFFFF\uFFFF\uFFFFpostfix",
            "prefix\uFFFF\uFFFF\uFFFF",
            "\uFFFF\uFFFF\uFFFFpostfix",
            "\uFFFF\uFFFF\uFFFF",
            "\ud83d\ude01"
        }
    )
    void testParse(String s) {
        byte[] classfile = createClassFile(s);

        ClassModel cm = ClassFile.of().parse(classfile);
        StringEntry se = obtainStringEntry(cm.constantPool());

        Utf8Entry utf8Entry = se.utf8();
        // Inflate to byte[] or char[]
        assertTrue(utf8Entry.equalsString(s));

        // Create string
        assertEquals(utf8Entry.stringValue(), s);
    }

    static Stream<UnaryOperator<byte[]>> malformedStringsProvider() {
        List<UnaryOperator<byte[]>> l = new ArrayList<>();

        l.add(withByte(0b1010_0000));
        l.add(withByte(0b1000_0000));

        l.add(withByte(0b1101_0000));
        l.add(withByte(0b1100_0000));
        l.add(withByte(0b1001_0000));
        l.add(withByte(0b1000_0000));

        l.add(withString("#X", s -> {
            byte[] c = new String("\u0080").getBytes(StandardCharsets.UTF_8);

            s[0] = c[0];
            s[1] = (byte) ((c[1] & 0xFF) & 0b0111_1111);

            return s;
        }));
        l.add(withString("#X#", s -> {
            byte[] c = new String("\u0800").getBytes(StandardCharsets.UTF_8);

            s[0] = c[0];
            s[1] = (byte) ((c[1] & 0xFF) & 0b0111_1111);
            s[2] = c[2];

            return s;
        }));
        l.add(withString("##X", s -> {
            byte[] c = new String("\u0800").getBytes(StandardCharsets.UTF_8);

            s[0] = c[0];
            s[1] = c[1];
            s[2] = (byte) ((c[2] & 0xFF) & 0b0111_1111);

            return s;
        }));

        return l.stream();
    }

    static UnaryOperator<byte[]> withByte(int b) {
        return withString(Integer.toBinaryString(b), s -> {
            s[0] = (byte) b;
            return s;
        });
    }

    static UnaryOperator<byte[]> withString(String name, UnaryOperator<byte[]> u) {
        return new UnaryOperator<byte[]>() {
            @Override
            public byte[] apply(byte[] bytes) {
                return u.apply(bytes);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    @ParameterizedTest
    @MethodSource("malformedStringsProvider")
    void testMalformedInput(UnaryOperator<byte[]> f) {
        String marker = "XXXXXXXX";
        byte[] classfile = createClassFile(marker);
        replace(classfile, marker, f);

        ClassModel cm = ClassFile.of().parse(classfile);
        StringEntry se = obtainStringEntry(cm.constantPool());

        assertThrows(RuntimeException.class, () -> {
            String s = se.utf8().stringValue();
        });
    }

    static void replace(byte[] b, String s, UnaryOperator<byte[]> f) {
        replace(b, s.getBytes(StandardCharsets.UTF_8), f);
    }

    static void replace(byte[] b, byte[] s, UnaryOperator<byte[]> f) {
        for (int i = 0; i < b.length - s.length; i++) {
            if (Arrays.equals(b, i, i + s.length, s, 0, s.length)) {
                s = f.apply(s);
                System.arraycopy(s, 0, b, i, s.length);
                return;
            }
        }
        throw new AssertionError();
    }

    static StringEntry obtainStringEntry(ConstantPool cp) {
        for (PoolEntry entry : cp) {
            if (entry instanceof StringEntry se) {
                return se;
            }
        }
        throw new AssertionError();
    }

    static byte[] createClassFile(String s) {
        return ClassFile.of().build(ClassDesc.of("C"),
                               clb -> clb.withMethod("m", MethodTypeDesc.of(CD_void), 0,
                                                     mb -> mb.withCode(cb -> cb.loadConstant(s)
                                                                               .return_())));
    }
}

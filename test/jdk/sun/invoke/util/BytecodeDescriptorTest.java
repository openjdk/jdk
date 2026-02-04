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
 * @bug 8356022
 * @summary Tests for sun.invoke.util.BytecodeDescriptor
 * @library /test/lib
 * @modules java.base/sun.invoke.util
 * @run junit BytecodeDescriptorTest
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Map;

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.invoke.util.BytecodeDescriptor;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeDescriptorTest {

    private static final String FOO_NAME = "dummy.Foo";
    private static final String BAR_NAME = "dummy.Bar";
    private static final String FOO_DESC = "L" + FOO_NAME.replace('.', '/') + ";";
    private static final String BAR_DESC = "L" + BAR_NAME.replace('.', '/') + ";";
    private static final String DOES_NOT_EXIST_DESC = "Ldoes/not/Exist;";
    static Class<?> foo1, foo2, bar1;
    static ClassLoader cl1, cl2;

    @BeforeAll
    static void setup() throws Throwable {
        var fooBytes = ClassFile.of().build(ClassDesc.of(FOO_NAME), _ -> {});
        var barBytes = ClassFile.of().build(ClassDesc.of(BAR_NAME), _ -> {});
        cl1 = new ByteCodeLoader(Map.of(FOO_NAME, fooBytes, BAR_NAME, barBytes), ClassLoader.getSystemClassLoader());
        foo1 = cl1.loadClass(FOO_NAME);
        bar1 = cl1.loadClass(BAR_NAME);
        foo2 = ByteCodeLoader.load(FOO_NAME, fooBytes);
        cl2 = foo2.getClassLoader();

        // Sanity
        assertNotSame(foo1, foo2);
        assertNotSame(cl1, cl2);
        assertSame(cl1, foo1.getClassLoader());
        assertSame(cl1, bar1.getClassLoader());
        assertNotSame(cl1, foo2.getClassLoader());
        assertEquals(FOO_DESC, foo1.descriptorString());
        assertEquals(FOO_DESC, foo2.descriptorString());
        assertEquals(BAR_DESC, bar1.descriptorString());
    }

    @Test
    void testParseClass() throws ReflectiveOperationException {
        assertSame(void.class,     BytecodeDescriptor.parseClass("V", null),                  "void");
        assertSame(int.class,      BytecodeDescriptor.parseClass("I", null),                  "primitive");
        assertSame(long[][].class, BytecodeDescriptor.parseClass("[[J", null),                "array");
        assertSame(Object.class,   BytecodeDescriptor.parseClass("Ljava/lang/Object;", null), "class or interface");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("java.lang.Object", null),    "binary name");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("L[a;", null),                "bad class or interface");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("Ljava.lang.Object;", null),  "bad class or interface");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("java/lang/Object", null),    "internal name");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("L;", null),                  "empty name");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("Lmissing/;", null),          "empty name part");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("L/Missing;", null),          "empty name part");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("[V", null),                  "bad array");
        assertSame(Class.forName("[".repeat(255) + "I"), BytecodeDescriptor.parseClass("[".repeat(255) + "I", null),   "good array");
        assertThrows(IllegalArgumentException.class, () -> BytecodeDescriptor.parseClass("[".repeat(256) + "I", null), "bad array");

        assertSame(foo2, BytecodeDescriptor.parseClass(FOO_DESC, cl2), "class loader");
        assertThrows(TypeNotPresentException.class, () -> BytecodeDescriptor.parseClass(DOES_NOT_EXIST_DESC, null),       "not existent");
        assertThrows(TypeNotPresentException.class, () -> BytecodeDescriptor.parseClass(BAR_DESC, cl2), "cross loader");
    }

    @Test
    void testParseMethod() {
        assertEquals(List.of(void.class),
                     BytecodeDescriptor.parseMethod("()V", null),
                     "no-arg");
        assertEquals(List.of(int.class, Object.class, long[].class, void.class),
                     BytecodeDescriptor.parseMethod("(ILjava/lang/Object;[J)V", null),
                     "sanity");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("()", null),
                     "no return");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("(V)V", null),
                     "bad arg");
        var voidInMsgIAE = assertThrows(IllegalArgumentException.class,
                                        () -> BytecodeDescriptor.parseMethod("([V)I", null),
                                        "bad arg");
        assertTrue(voidInMsgIAE.getMessage().contains("[V"), () -> "missing [V type in: '%s'".formatted(voidInMsgIAE.getMessage()));
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseClass("([".repeat(256) + "I)J", null),
                     "bad arg");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("(Ljava.lang.Object;)V", null),
                     "bad arg");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("(Ljava/lang[Object;)V", null),
                     "bad arg");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("(L;)V", null),
                     "bad arg");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("(Ljava/lang/;)V", null),
                     "bad arg");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("(L/Object;)V", null),
                     "bad arg");

        assertEquals(List.of(foo1, bar1),
                     BytecodeDescriptor.parseMethod("(" + FOO_DESC + ")" + BAR_DESC, cl1),
                     "class loader");
        assertThrows(TypeNotPresentException.class,
                     () -> BytecodeDescriptor.parseMethod("(" + FOO_DESC + ")" + BAR_DESC, cl2),
                     "no bar");
        assertThrows(TypeNotPresentException.class,
                     () -> BytecodeDescriptor.parseMethod("(" + FOO_DESC + "V)V", null),
                     "first encounter TNPE");
        assertThrows(IllegalArgumentException.class,
                     () -> BytecodeDescriptor.parseMethod("(V" + FOO_DESC + ")V", null),
                     "first encounter IAE");
    }

}

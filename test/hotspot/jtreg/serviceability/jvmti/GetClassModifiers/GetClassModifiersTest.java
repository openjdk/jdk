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
 * @summary Test JVMTI GetClassModifiers
 * @modules java.base/jdk.internal.misc
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED ${test.main.class}
 */

import java.lang.classfile.ClassFile;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;
import jdk.internal.misc.PreviewFeatures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class GetClassModifiersTest {

    // member classes to test

    public class C1 {}
    public static class C2 {}
    public final class C3 {}
    public static final class C4 {}
    class C5 {}
    static class C6 {}
    final class C7 {}
    static final class C8 {}

    public abstract class A_C1 {}
    public static abstract class A_C2 {}
    abstract class A_C3 {}
    static abstract class A_C4 {}

    public interface I1 {}
    interface I2 {}

    public @interface A1 {}
    @interface A2 {}

    public enum E1 {}
    enum E2 {}

    public record R1() {}
    record R2() {}

    static Stream<Class<?>> testClasses() {
        // local classes
        class LC {}
        record LR() {}
        enum LE {}

        // anonymous class
        var r = new Runnable() { public void run() {} };
        Class<?> anonClass = r.getClass();

        return Stream.of(
                int.class,
                int[].class,
                void.class,
                Object.class,
                Object[].class,
                Integer.class,
                Integer[].class,
                Void.class,
                GetClassModifiersTest.class,
                GetClassModifiersTest[].class,
                C1.class,
                C2.class,
                C3.class,
                C4.class,
                C5.class,
                C6.class,
                C7.class,
                C8.class,
                C1[].class,
                C2[].class,
                C3[].class,
                C4[].class,
                C5[].class,
                C6[].class,
                C7[].class,
                C8[].class,
                A_C1.class,
                A_C2.class,
                A_C3.class,
                A_C4.class,
                A_C1[].class,
                A_C2[].class,
                A_C3[].class,
                A_C4[].class,
                I1.class,
                I2.class,
                I1[].class,
                I2[].class,
                A1.class,
                A2.class,
                A1[].class,
                A2[].class,
                E1.class,
                E2.class,
                E1[].class,
                E2[].class,
                R1.class,
                R2.class,
                R1[].class,
                R2[].class,
                LC.class,
                LR.class,
                LE.class,
                LC[].class,
                LR[].class,
                LE[].class,
                anonClass,
                anonClass.arrayType()
        );
    }

    static Stream<Class<?>> primitiveClasses() {
        return testClasses().filter(Class::isPrimitive);
    }

    static Stream<Class<?>> arrayClasses() {
        return testClasses().filter(Class::isArray);
    }

    /**
     * Test JVMTI GetClassModifiers implementation. If preview features are enabled then
     * the JVMTI function should return the same modifiers as Class::getModifiers. If
     * preview features are disabled then the JVMTI function should preserve long-standing
     * behavior to return the modifiers with ACC_SUPER set for all calsses except primitive
     * classes.
     */
    @ParameterizedTest
    @MethodSource("testClasses")
    void testClass(Class<?> clazz) throws Exception {
        int expectedModifiers = clazz.getModifiers();
        if (!clazz.isPrimitive() && !PreviewFeatures.isEnabled()) {
            expectedModifiers |= ClassFile.ACC_SUPER;
        }
        assertEquals(expectedModifiers, jvmtiGetClassModifiers(clazz));
    }

    /**
     * Test modifiers specified by JVMTI GetClassModifiers for primitive classes.
     */
    @ParameterizedTest
    @MethodSource("primitiveClasses")
    void testPrimitiveClass(Class<?> clazz) {
        assertTrue(clazz.isPrimitive());
        int mods = jvmtiGetClassModifiers(clazz);
        assertTrue(Modifier.isPublic(mods));
        assertFalse(Modifier.isPrivate(mods));
        assertFalse(Modifier.isProtected(mods));
        assertTrue(Modifier.isAbstract(mods));
        assertTrue(Modifier.isFinal(mods));
        assertFalse(Modifier.isInterface(mods));
    }

    /**
     * Test modifiers specified by JVMTI GetClassModifiers for array classes.
     */
    @ParameterizedTest
    @MethodSource("arrayClasses")
    void testArrayClass(Class<?> clazz) {
        assertTrue(clazz.isArray());
        int mods = jvmtiGetClassModifiers(clazz);
        int componentMods = clazz.getComponentType().getModifiers();
        assertEquals(Modifier.isPublic(componentMods), Modifier.isPublic(mods));
        assertEquals(Modifier.isPrivate(componentMods), Modifier.isPrivate(mods));
        assertEquals(Modifier.isProtected(componentMods), Modifier.isProtected(mods));
        assertTrue(Modifier.isAbstract(mods));
        assertTrue(Modifier.isFinal(mods));
        assertFalse(Modifier.isInterface(mods));
    }

    private int jvmtiGetClassModifiers(Class<?> clazz) {
        return GetClassModifiers.getClassModifiers(clazz);
    }
}

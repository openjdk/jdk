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
 * @summary Test JVMTI GetClassModifiers with value classes
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED ${test.main.class}
 */

import java.lang.classfile.ClassFile;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class GetValueClassModifiersTest {

    // member classes to test

    public abstract value class A_V1 { }
    abstract value class A_V2 { }
    public value class V1 { }
    value class V2 { }
    public value record R_V1() { }
    value record R_V2() { }

    static Stream<Class<?>> testClasses() {
        // local classes
        value class L_V { }
        abstract value class L_A_V { }

        return Stream.of(
                Integer.class,
                Integer[].class,

                A_V1.class,
                A_V1[].class,
                A_V2.class,
                A_V2[].class,
                V1.class,
                V1[].class,
                V2.class,
                V2.class,
                R_V1.class,
                R_V1[].class,
                R_V2.class,
                R_V2[].class,

                // local classes
                L_V.class,
                L_V[].class,
                L_A_V.class,
                L_A_V[].class
        );
    }

    static Stream<Class<?>> valueClasses() {
        return testClasses().filter(Class::isValue);
    }

    static Stream<Class<?>> arrayClasses() {
        return testClasses().filter(Class::isArray);
    }

    @ParameterizedTest
    @MethodSource("valueClasses")
    void testValueClass(Class<?> clazz) {
        assertTrue(clazz.isValue());
        int mods = jvmtiGetClassModifiers(clazz);
        assertEquals(0, (mods & ClassFile.ACC_IDENTITY), "ACC_IDENTITY set");
        assertEquals(clazz.getModifiers(), jvmtiGetClassModifiers(clazz));
    }

    @ParameterizedTest
    @MethodSource("arrayClasses")
    void testArrayClass(Class<?> clazz) {
        assertTrue(clazz.isArray());
        int mods = jvmtiGetClassModifiers(clazz);
        assertEquals(ClassFile.ACC_IDENTITY, (mods & ClassFile.ACC_IDENTITY), "ACC_IDENTITY not set");
        assertEquals(clazz.getModifiers(), jvmtiGetClassModifiers(clazz));
    }

    private int jvmtiGetClassModifiers(Class<?> clazz) {
        return GetClassModifiers.getClassModifiers(clazz);
    }
}

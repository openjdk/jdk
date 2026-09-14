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
 * @summary Test Field::isStrictInit on fields that have ACC_STRICT_INIT set or not set
 *     in a selection of class file versions. ACC_STRICT_INIT is considered not set when
 *     the class file version is not 71.65535.
 * @modules java.base/jdk.internal.misc
 * @run junit/othervm ${test.main.class}
 * @run junit/othervm --enable-preview ${test.main.class}
 */

import java.lang.constant.ClassDesc;
import java.lang.classfile.ClassFile;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.stream.Stream;
import jdk.internal.misc.PreviewFeatures;
import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_IDENTITY;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.ClassFile.ACC_STRICT_INIT;
import static java.lang.classfile.ClassFile.JAVA_8_VERSION;
import static java.lang.classfile.ClassFile.JAVA_25_VERSION;
import static java.lang.classfile.ClassFile.PREVIEW_MINOR_VERSION;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CLASS_INIT_NAME;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class IsStrictInitTest {

    /**
     * Class file versions to test and whether to set the ACC_STRICT_INIT flag on the fields.
     */
    static Stream<Arguments> testCases() {
        var cases = new ArrayList<Arguments>();
        cases.add(Arguments.arguments(JAVA_8_VERSION, 0, false));
        cases.add(Arguments.arguments(JAVA_8_VERSION, 0, true));
        cases.add(Arguments.arguments(JAVA_25_VERSION, 0, false));
        cases.add(Arguments.arguments(JAVA_25_VERSION, 0, true));
        cases.add(Arguments.arguments(ClassFile.latestMajorVersion(), 0, false));
        cases.add(Arguments.arguments(ClassFile.latestMajorVersion(), 0, true));
        if (PreviewFeatures.isEnabled()) {
            cases.add(Arguments.arguments(ClassFile.latestMajorVersion(), PREVIEW_MINOR_VERSION, false));
            cases.add(Arguments.arguments(ClassFile.latestMajorVersion(), PREVIEW_MINOR_VERSION, true));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void testIsStrictInit(int majorVersion, int minorVersion, boolean strictInit) throws Exception {
        byte[] classBytes = buildClass(majorVersion, minorVersion, strictInit);
        Class<?> clazz = MethodHandles.lookup()
                .defineHiddenClass(classBytes, false)
                .lookupClass();

        // ACC_STRICT_INIT flag should be ignored when not 71.65535
        boolean expectStrictInit = strictInit && (minorVersion == PREVIEW_MINOR_VERSION);

        // static field
        Field f1 = clazz.getDeclaredField("aStaticField");
        System.err.format("%s, accessFlags = %s%n", f1, f1.accessFlags());
        assertTrue(Modifier.isStatic(f1.getModifiers()));
        assertEquals(expectStrictInit, f1.isStrictInit());

        // instance field
        Field f2 = clazz.getDeclaredField("aField");
        System.err.format("%s, accessFlags = %s%n", f2, f2.accessFlags());
        assertFalse(Modifier.isStatic(f2.getModifiers()));
        assertEquals(expectStrictInit, f2.isStrictInit());
    }

    /**
     * Returns the class bytes for a class with a final static and final instance field.
     * @param majorVersion major class file version
     * @param minorVersion minor class file version
     * @param strictInit true to set the ACC_STRICT_INIT flag on the fields
     */
    @SuppressWarnings("preview")
    private byte[] buildClass(int majorVersion, int minorVersion, boolean strictInit) {
        ClassDesc generateDesc = ClassDesc.of("TestClass");
        int flags = ACC_FINAL | (strictInit ? ACC_STRICT_INIT : 0);
        return ClassFile.of().build(generateDesc, builder -> builder
                .withVersion(majorVersion, minorVersion)
                .withFlags(ACC_IDENTITY)
                .withField("aField", CD_int, flags)
                .withField("aStaticField", CD_int,  ACC_STATIC | flags)
                .withMethodBody(INIT_NAME, MTD_void, 0, cb -> cb
                        .aload(0)
                        .bipush(42)
                        .putfield(generateDesc, "aField", CD_int)
                        .aload(0)
                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                        .return_())
                .withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, cb -> cb
                        .bipush(73)
                        .putstatic(generateDesc, "aStaticField", CD_int)
                        .return_()));
    }
}

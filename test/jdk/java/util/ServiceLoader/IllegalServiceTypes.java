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

/**
 * @test
 * @bug 8379812
 * @summary ServiceLoader.load methods should throw IAE with illegal service types
 * @run junit ${test.main.class}
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class IllegalServiceTypes {

    /**
     * Generate and return a hidden class.
     */
    static Class<?> genHiddenClass() throws Exception {
        ClassDesc name = ClassDesc.of("S");
        byte[] classBytes = ClassFile.of()
                .build(name, cb -> cb.withSuperclass(ConstantDescs.CD_Object));
        Class<?> svc = MethodHandles.lookup()
                .defineHiddenClass(classBytes, false)
                .lookupClass();
        assertTrue(svc.isHidden());
        return svc;
    }

    static Stream<Class<?>> illegalServiceTypes() throws Exception {
        return Stream.of(
                boolean.class,
                byte.class,
                short.class,
                char.class,
                int.class,
                long.class,
                float.class,
                double.class,
                void.class,
                Object[].class,
                int[].class,
                genHiddenClass()
        );
    }

    @ParameterizedTest
    @MethodSource("illegalServiceTypes")
    void testBadServiceType(Class<?> svc) {
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        ModuleLayer bootLayer = ModuleLayer.boot();
        assertThrows(IllegalArgumentException.class, () -> ServiceLoader.load(svc));
        assertThrows(IllegalArgumentException.class, () -> ServiceLoader.load(svc, scl));
        assertThrows(IllegalArgumentException.class, () -> ServiceLoader.load(bootLayer, svc));
        assertThrows(IllegalArgumentException.class, () -> ServiceLoader.loadInstalled(svc));
    }
}

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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8164714
 * @summary No null check for immediate enclosing instance for VM/reflective
 *          invocation of inner classes for older versions or on request
 *
 * @clean *
 * @compile -XDnullCheckOuterThis=false NoOuterThisNullChecks.java
 * @run junit NoOuterThisNullChecks
 *
 * @clean *
 * @compile --release 17 NoOuterThisNullChecks.java
 * @run junit NoOuterThisNullChecks
 */
class NoOuterThisNullChecks {
    static Stream<Class<?>> testClasses() {
        return Stream.of(NoOuterThis.class, OuterThisField.class);
    }

    @MethodSource("testClasses")
    @ParameterizedTest
    void testNoOuter(Class<?> clz) {
        assertDoesNotThrow(() -> clz.getDeclaredConstructor(NoOuterThisNullChecks.class).newInstance((Object) null));

        MethodHandle mh = assertDoesNotThrow(() -> MethodHandles.lookup().findConstructor(clz, MethodType.methodType(void.class, NoOuterThisNullChecks.class)))
                .asType(MethodType.methodType(Object.class, Object.class));
        assertDoesNotThrow(() -> {
            Object stub = mh.invokeExact((Object) null);
        });
    }

    class NoOuterThis {}
    class OuterThisField {
        @Override
        public String toString() {
            return "outer this = " + NoOuterThisNullChecks.this;
        }
    }
}

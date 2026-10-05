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
 * @bug 8371953 8371960
 * @summary Null checks for AnnotatedElement APIs.
 * @run junit AnnotatedElementNullCheckTest
 */

import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class AnnotatedElementNullCheckTest {

    // Return a stream of instances, each of a different implementation class
    static AnnotatedElement[] implementations() throws Exception {
        var objectHashCodeMethod = Object.class.getMethod("hashCode");
        var annotatedParameterizedType = (AnnotatedParameterizedType) Optional.class
                .getMethod("ifPresent", Consumer.class)
                .getAnnotatedParameterTypes()[0];
        var annotatedGenericArrayType = (AnnotatedArrayType) List.class
                .getMethod("of", Object[].class)
                .getAnnotatedParameterTypes()[0];
        record Rec(int a) {}
        return new AnnotatedElement[] {
                Object.class,
                objectHashCodeMethod,
                System.class.getField("out"),
                Object.class.getConstructor(),
                Object.class.getPackage(),
                Optional.class.getTypeParameters()[0],
                // AnnotatedType (direct)
                objectHashCodeMethod.getAnnotatedReturnType(),
                // AnnotatedParameterizedType
                annotatedParameterizedType,
                // AnnotatedArrayType
                annotatedGenericArrayType,
                // AnnotatedTypeVariable
                annotatedGenericArrayType.getAnnotatedGenericComponentType(),
                // AnnotatedWildcardType
                annotatedParameterizedType.getAnnotatedActualTypeArguments()[0],
                Rec.class.getRecordComponents()[0],
                Object.class.getMethod("equals", Object.class).getParameters()[0],
                Object.class.getModule(),
        };
    }

    @Test
    void ensureImplementationsDistinct() throws Throwable {
        var set = new HashSet<Class<?>>();
        for (var impl : implementations()) {
            var clazz = impl.getClass();
            if (!set.add(clazz)) {
                fail("Duplicate implementation class %s in %s".formatted(clazz, impl));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void nullChecks(AnnotatedElement impl) {
        assertThrows(NullPointerException.class, () -> impl.isAnnotationPresent(null));
        assertThrows(NullPointerException.class, () -> impl.getAnnotation(null));
        assertThrows(NullPointerException.class, () -> impl.getAnnotationsByType(null));
        assertThrows(NullPointerException.class, () -> impl.getDeclaredAnnotation(null));
        assertThrows(NullPointerException.class, () -> impl.getDeclaredAnnotationsByType(null));
    }
}

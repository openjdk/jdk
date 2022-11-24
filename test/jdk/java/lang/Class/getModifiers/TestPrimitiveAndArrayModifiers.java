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

import java.lang.reflect.Modifier;
import java.lang.annotation.*;

/*
 * @test
 * @bug 8296743
 * @summary Verify array classes and primitives have expected modifiers
 */
@ExpectedModifiers(Modifier.PUBLIC | Modifier.FINAL | Modifier.ABSTRACT)
public class TestPrimitiveAndArrayModifiers {

    /*
     * Relevant excerpt of the Class.getModifiers() specification:
     * <p> If the underlying class is an array class:
     * <ul>
     * <li> its {@code public}, {@code private} and {@code protected}
     *      modifiers are the same as those of its component type
     * <li> its {@code final} and {@code abstract} modifiers are always
     *      {@code true}
     * <li> its interface modifier is always {@code false}, even when
     *      the component type is an interface
     * </ul>
     */

    public static void main(String... args) throws Exception {
        testPrimitives();
        testArrays();
    }

    private static void testArrays() {
        Class<?>[] testCases = {
            TestPrimitiveAndArrayModifiers.class,

            PackagePrivateClass.class,
            ProtectedClass.class,
            PrivateClass.class,

            PublicInterface.class,
            PackagePrivateInterface.class,
            ProtectedInterface.class,
            PrivateInterface.class,
        };

        for(var testCase : testCases) {
            int expectedModifiers =
                testCase.getAnnotation(ExpectedModifiers.class).value();
            Class<?> arrayClass = testCase.arrayType();
            int actualModifiers = arrayClass.getModifiers();
            if (expectedModifiers != actualModifiers) {
                throw new RuntimeException("Expected " + Modifier.toString(expectedModifiers) +
                                           "on " + testCase.getCanonicalName() +
                                           ", but got " + Modifier.toString(actualModifiers));
            }
        }
    }

    @ExpectedModifiers(Modifier.FINAL | Modifier.ABSTRACT)
    class PackagePrivateClass {}

    @ExpectedModifiers(Modifier.FINAL | Modifier.ABSTRACT | Modifier.PROTECTED)
    protected class ProtectedClass {}

    @ExpectedModifiers(Modifier.FINAL | Modifier.ABSTRACT | Modifier.PRIVATE)
    private class  PrivateClass {}

    @ExpectedModifiers(Modifier.FINAL | Modifier.ABSTRACT | Modifier.PUBLIC)
    public interface PublicInterface {}

    @ExpectedModifiers(Modifier.FINAL | Modifier.ABSTRACT)
    interface PackagePrivateInterface {}

    @ExpectedModifiers(Modifier.FINAL | Modifier.ABSTRACT | Modifier.PROTECTED)
    protected interface ProtectedInterface {}

    @ExpectedModifiers(Modifier.FINAL | Modifier.ABSTRACT | Modifier.PRIVATE)
    private interface PrivateInterface {}

    /*
     * Relevant excerpt of the Class.getModifiers() specification:
     *
     * If this {@code Class} object represents a primitive type or
     * void, its {@code public}, {@code abstract}, and {@code final}
     * modifiers are always {@code true}.
     */
    private static void testPrimitives() {
        Class<?>[] testCases = {
            void.class,
            boolean.class,
            byte.class,
            short.class,
            char.class,
            int.class,
            float.class,
            long.class,
            double.class,
        };

        for(var testCase : testCases) {
            int actualModifiers = testCase.getModifiers();
            if ((Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL) !=
                actualModifiers) {
                throw new RuntimeException("Bad modifiers " +
                                           Modifier.toString(actualModifiers) +
                                           " on primitive type " + testCase);
            }
        }
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedModifiers {
    int value() default 0;
}

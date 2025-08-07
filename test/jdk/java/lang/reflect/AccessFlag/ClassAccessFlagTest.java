/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266670 8291734 8296743
 * @summary Test expected AccessFlag's on classes.
 */

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import static java.lang.reflect.AccessFlag.*;

/*
 * Class access flags that can directly or indirectly declared in
 * source include:
 * public, private, protected, static, final, interface, abstract,
 * annotation, enum.
 *
 * Additionally, the access flags super and synthetic cannot be
 * explicitly applied.
 *
 * This test is written on top of the facilities of core reflection.
 *
 * Note that core reflection does not offer a supported mechanism to
 * return the Class object created from a module-info.class
 * file. Therefore, this test does not attempt to probe the setting of
 * that access flag.
 */
@ExpectedClassFlags({PUBLIC, FINAL, SUPER})
public final class ClassAccessFlagTest {
    public static void main(String... args) {
        // Top-level and auxiliary classes; i.e. non-inner classes
        Class<?>[] testClasses = {
            ClassAccessFlagTest.class,
            TestInterface.class,
            ExpectedClassFlags.class,
            TestOuterEnum.class
        };
        checkClasses(testClasses);

        // Nested classes of ClassAccessFlagTest
        checkClasses(ClassAccessFlagTest.class.getDeclaredClasses());

        checkPrimitives();
        checkArrays();
    }

    private static void checkClasses(Class<?>[] classes) {
        for (var clazz : classes) {
            checkClass(clazz);
        }
    }

    private static void checkClass(Class<?> clazz) {
        ExpectedClassFlags expected =
            clazz.getAnnotation(ExpectedClassFlags.class);
        if (expected != null) {
            Set<AccessFlag> base = EnumSet.noneOf(AccessFlag.class);
            Collections.addAll(base, expected.value());
            Set<AccessFlag> actual = clazz.accessFlags();
            if (!base.equals(actual)) {
                throw new RuntimeException("On " + clazz +
                                           " expected " + base +
                                           " got " + actual);
            }
        }
    }

    private static void checkPrimitives() {
        final Class<?>[] primitives = {
            byte.class,
            int.class,
            long.class,
            short.class,
            char.class,
            float.class,
            double.class,
            boolean.class,
            void.class // same access flag rules
        };

        var expected = Set.of(PUBLIC,
                              AccessFlag.FINAL,
                              AccessFlag.ABSTRACT);

        for(var primClass : primitives) {
            var accessFlags = primClass.accessFlags();
            if (!accessFlags.equals(expected)) {
                throw new RuntimeException("Unexpected flags on " +
                                           primClass);
            }
        }
    }

    private static boolean containsAny(Set<AccessFlag> input,
                                       Set<AccessFlag> test) {
        var copy = new HashSet<>(input);
        return copy.removeAll(test);
    }

    private static void checkArrays() {
        Class<?>[] accessClasses = {
            PublicInterface.class,
            ProtectedInterface.class,
            PrivateInterface.class,
        };

        for (var accessClass : accessClasses) {
            AccessFlag accessLevel;
            var flags = accessClass.accessFlags();
            if (flags.contains(PUBLIC))
                accessLevel = PUBLIC;
            else if (flags.contains(AccessFlag.PROTECTED))
                accessLevel = AccessFlag.PROTECTED;
            else if (flags.contains(AccessFlag.PRIVATE))
                accessLevel = AccessFlag.PRIVATE;
            else
                accessLevel = null;

            var arrayClass = accessClass.arrayType();
            // Access modifier must match on the array type
            if (accessLevel != null) {
                if (!arrayClass.accessFlags().contains(accessLevel)) {
                    throw new RuntimeException("Mismatched access flags on " +
                                               arrayClass);
                }
            } else {
                if (containsAny(arrayClass.accessFlags(),
                                Set.of(PUBLIC,
                                       AccessFlag.PROTECTED,
                                       AccessFlag.PRIVATE))) {
                    throw new RuntimeException("Unexpected access flags on " +
                                               arrayClass);
                }
            }
        }

    }

    // inner classes and interfaces; possible flags on INNER_CLASS
    // locations:
    // PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, INTERFACE, ABSTRACT,
    // SYNTHETIC, ANNOTATION, ENUM.

    @ExpectedClassFlags({PUBLIC, STATIC, INTERFACE, ABSTRACT})
    public      interface PublicInterface {}
    @ExpectedClassFlags({PROTECTED, STATIC, INTERFACE, ABSTRACT})
    protected   interface ProtectedInterface {}
    @ExpectedClassFlags({PRIVATE, STATIC, INTERFACE, ABSTRACT})
    private     interface PrivateInterface {}
    @ExpectedClassFlags({STATIC, INTERFACE, ABSTRACT})
    /*package*/ interface PackageInterface {}

    @ExpectedClassFlags({FINAL})
    /*package*/ final class TestFinalClass {}

    @ExpectedClassFlags({ABSTRACT})
    /*package*/ abstract class TestAbstractClass {}

    @ExpectedClassFlags({STATIC, INTERFACE, ABSTRACT, ANNOTATION})
    /*package*/ @interface TestMarkerAnnotation {}

    @ExpectedClassFlags({PUBLIC, STATIC, FINAL, ENUM})
    public enum MetaSynVar {
        QUUX;
    }

    // Is there is at least one special enum constant, the enum class
    // itself is implicitly abstract rather than final.
    @ExpectedClassFlags({PROTECTED, STATIC, ABSTRACT, ENUM})
    protected enum MetaSynVar2 {
        WOMBAT{
            @Override
            public int foo() {return 42;}
        };
        public abstract int foo();
    }

    @ExpectedClassFlags({PRIVATE, ABSTRACT})
    private abstract class Foo {}

    @ExpectedClassFlags({STATIC, INTERFACE, ABSTRACT})
    interface StaticTestInterface {}
}

@Retention(RetentionPolicy.RUNTIME)
@ExpectedClassFlags({INTERFACE, ABSTRACT, ANNOTATION})
@interface ExpectedClassFlags {
    AccessFlag[] value();
}

@ExpectedClassFlags({INTERFACE, ABSTRACT})
interface TestInterface {}


@ExpectedClassFlags({FINAL, SUPER, ENUM})
enum TestOuterEnum {
    INSTANCE;
}

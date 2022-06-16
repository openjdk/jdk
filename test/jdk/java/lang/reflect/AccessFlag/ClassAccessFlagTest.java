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
 * @bug 8266670
 * @summary Test expected AccessFlag's on fields.
 */

import java.lang.annotation.*;
import java.lang.reflect.*;

/*
 * Class access flags that can directly or indirectly declared in
 * source include:
 * public, private, protected, static, final, interface, abstract,
 * annotation, enum.
 *
 * Additionally, the access flags super and synthetic cannot be
 * explicitly applied.
 */
public class ClassAccessFlagTest {
    public static void main(String... args) {
        for (var clazz :
                 ClassAccessFlagTest.class.getDeclaredClasses()) {
            checkClass(clazz);
        }
        checkClass(TestInterface.class);
        checkClass(ExpectedClassFlags.class);
    }

    private static void checkClass(Class<?> clazz) {
        ExpectedClassFlags expected =
            clazz.getAnnotation(ExpectedClassFlags.class);
        if (expected != null) {
            String actual = clazz.accessFlags().toString();
            if (!expected.value().equals(actual)) {
                throw new RuntimeException("On " + clazz +
                                           " expected " + expected.value() +
                                           " got " + actual);
            }
        }
    }

    // Classes
    @ExpectedClassFlags("[PUBLIC, STATIC, FINAL, ENUM]")
    public enum MetaSynVar {
        QUUX;
    }

    // Is there is at least one special enum constant, the enum class
    // itself is implicitly abstract rather than final.
    @ExpectedClassFlags("[PROTECTED, STATIC, ABSTRACT, ENUM]")
    protected enum MetaSynVar2 {
        WOMBAT{
            @Override
            public int foo() {return 42;}
        };
        public abstract int foo();
    }

    @ExpectedClassFlags("[PRIVATE, ABSTRACT]")
    private abstract class Foo {}

    @ExpectedClassFlags("[STATIC, INTERFACE, ABSTRACT]")
    interface StaticTestInterface {}
}

@Retention(RetentionPolicy.RUNTIME)
@ExpectedClassFlags("[INTERFACE, ABSTRACT, ANNOTATION]")
@interface ExpectedClassFlags {
    String value();
}

@ExpectedClassFlags("[INTERFACE, ABSTRACT]")
interface TestInterface {}

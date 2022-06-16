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
 * @summary Test expected AccessFlag's on methods
 */

import java.lang.annotation.*;
import java.lang.reflect.*;

/*
 * Method modifiers include:
 * public, private, protected, static, final, synchronized,
 * bridge, varargs, native, abstract, strictfp, synthetic,
 *
 * At a source level, constructors can be modifiers as public,
 * protected, or private.
 *
 * The modifiers bridge and synthetic cannot be applied directly and
 * strictfp can only be applied in older source versions.
 */
public abstract class MethodAccessFlagTest {
    @ExpectedMethodFlags("[PUBLIC, STATIC, VARARGS]")
    public static void main(String... args) {
        for (var ctor :
                 MethodAccessFlagTest.class.getDeclaredConstructors()) {
            checkExecutable(ctor);
        }

        for (var method :
                 MethodAccessFlagTest.class.getDeclaredMethods()) {
            checkExecutable(method);
        }
    }

    private static void checkExecutable(Executable method) {
        ExpectedMethodFlags emf =
            method.getAnnotation(ExpectedMethodFlags.class);
        if (emf != null) {
            String actual = method.accessFlags().toString();
            if (!emf.value().equals(actual)) {
                throw new RuntimeException("On " + method +
                                           " expected " + emf.value() +
                                           " got " + actual);
            }
        }
    }

    // Constructors
    @ExpectedMethodFlags("[PUBLIC]")
    public MethodAccessFlagTest() {}

    @ExpectedMethodFlags("[PROTECTED]")
    protected MethodAccessFlagTest(int i) {super();}

    @ExpectedMethodFlags("[PRIVATE]")
    private MethodAccessFlagTest(String s) {super();}

    // Methods
    @ExpectedMethodFlags("[PROTECTED, SYNCHRONIZED]")
    protected synchronized void m0() {}

    @ExpectedMethodFlags("[PRIVATE]")
    private void m1() {}

    @ExpectedMethodFlags("[ABSTRACT]")
    abstract void m2();

    @ExpectedMethodFlags("[PUBLIC, FINAL]")
    public final void m3() {}

    @ExpectedMethodFlags("[NATIVE]")
    native void m4();

    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExpectedMethodFlags {
        String value();
    }
}

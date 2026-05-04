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
 * @bug 8266670
 * @summary Test expected AccessFlag's on methods and parameters
 * @compile -parameters MethodAccessFlagTest.java
 * @run main MethodAccessFlagTest
 */

// Use -parameters flag to javac to have access flag information about
// parameters preserved in the resulting class file.

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static java.lang.reflect.AccessFlag.*;

/*
 * Method modifiers include:
 * public, private, protected, static, final, synchronized,
 * bridge, varargs, native, abstract, strictfp, synthetic,
 *
 * At a source level, constructors can have modifiers public,
 * protected, or private.
 *
 * The modifiers bridge and synthetic cannot be applied directly and
 * strictfp can only be applied in older source versions.
 *
 * Method parameters can be final, synthetic, and mandated.
 */
public abstract class MethodAccessFlagTest {
    @ExpectedMethodFlags({PUBLIC, STATIC, VARARGS})
    public static void main(String... args) {
        for (var ctor :
                 MethodAccessFlagTest.class.getDeclaredConstructors()) {
            checkExecutable(ctor);
        }

        for (var method :
                 MethodAccessFlagTest.class.getDeclaredMethods()) {
            checkExecutable(method);
        }

        // Hard-code information about parameter modifiers; could be
        // represented as annotations on the class and decoded.
        for (var ctor : NestedClass.class.getConstructors()) {
            for (var parameter : ctor.getParameters()) {
                String expected = null;
                if (parameter.getType() == int.class) {
                    // The explicit int parameter is expected to have
                    // the final flag
                    expected = "[FINAL]";
                } else {
                    // The implicit this$0 parameter is expected to have the
                    // final and mandated flags
                    expected = "[FINAL, MANDATED]";
                }
                checkString(parameter.toString(),
                            parameter.accessFlags().toString(),
                            expected);
            }
        }

        for (var method : BridgeExample.class.getDeclaredMethods()) {
            // Find the two "clone" methods, one implicit and one
            // explicit
            if (!method.getName().equals("clone")) {
                throw new RuntimeException("Unexpected name for " + method);
            }
            String expected = null;
            if (method.getReturnType() == Object.class) {
                expected = "[PUBLIC, BRIDGE, SYNTHETIC]";
            } else {
                expected = "[PUBLIC]";
            }
            checkString(method.toString(),
                        method.accessFlags().toString(),
                        expected);
        }

        // Hard-code information about parameter modifiers; could be
        // represented as annotations on the class and decoded.
        for (var ctor : TestEnum.class.getDeclaredConstructors()) {
            // Each of the two parameters used in javac's enum
            // constructor implementation is synthetic. This may need
            // to be updated if javac's enum constructor generation
            // idiom changes.
            for (var parameter : ctor.getParameters()) {
                checkString(parameter.toString(),
                            parameter.accessFlags().toString(),
                            "[SYNTHETIC]");
            }
        }

    }

    class NestedClass {
        private int i;
        // Implicit leading parameter
        public NestedClass(final int i) {
            this.i = i;
        }
    }

    class BridgeExample implements Cloneable {
        public BridgeExample(){}
        // Triggers generation of a bridge method.
        public BridgeExample clone() {
            return new BridgeExample();
        }
    }

    // Use as a host for a constructor with synthetic parameters
    enum TestEnum {
        INSTANCE;
    }

    private static void checkExecutable(Executable method) {
        ExpectedMethodFlags expected =
            method.getAnnotation(ExpectedMethodFlags.class);
        if (expected != null) {
            Set<AccessFlag> base = EnumSet.noneOf(AccessFlag.class);
            Collections.addAll(base, expected.value());
            Set<AccessFlag> actual = method.accessFlags();
            if (!base.equals(actual)) {
                throw new RuntimeException("On " + method +
                        " expected " + base +
                        " got " + actual);
            }
        }
    }

    private static void checkString(String declaration,
                               String expected,
                               String actual) {
        if (!expected.equals(actual)) {
            throw new RuntimeException("On " + declaration +
                                       " expected " + expected +
                                       " got " + actual);
        }
    }

    // Constructors
    @ExpectedMethodFlags({PUBLIC})
    public MethodAccessFlagTest() {}

    @ExpectedMethodFlags({PROTECTED})
    protected MethodAccessFlagTest(int i) {super();}

    @ExpectedMethodFlags({PRIVATE})
    private MethodAccessFlagTest(String s) {super();}

    // Methods
    @ExpectedMethodFlags({PROTECTED, SYNCHRONIZED})
    protected synchronized void m0() {}

    @ExpectedMethodFlags({PRIVATE})
    private void m1() {}

    @ExpectedMethodFlags({ABSTRACT})
    abstract void m2();

    @ExpectedMethodFlags({PUBLIC, FINAL})
    public final void m3() {}

    @ExpectedMethodFlags({NATIVE})
    native void m4();

    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExpectedMethodFlags {
        AccessFlag[] value();
    }
}

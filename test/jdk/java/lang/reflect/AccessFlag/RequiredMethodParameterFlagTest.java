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

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Parameter;
import java.util.Set;

/*
 * @test
 * @bug 8292275
 * @summary Test required flags on parameters
 * @compile RequiredMethodParameterFlagTest.java
 * @run main RequiredMethodParameterFlagTest
 */
public class RequiredMethodParameterFlagTest {
    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException {
        boolean errors = false;
        Set<AccessFlag> mandated = Set.of(AccessFlag.MANDATED);
        Set<AccessFlag> synthetic = Set.of(AccessFlag.SYNTHETIC);
        // test for implicit parameters
        Parameter[] parameters = Inner.class.getDeclaredConstructors()[0].getParameters();
        errors |= assertFlags(mandated, parameters[0]);
        errors |= assertFlags(Set.of(), parameters[1]);

        parameters = findAnonymous().getDeclaredConstructors()[0].getParameters();
        errors |= assertFlags(mandated, parameters[0]);
        errors |= assertFlags(Set.of(), parameters[1]);

        parameters = MyEnum.class.getDeclaredMethod("valueOf", String.class).getParameters();
        errors |= assertFlags(mandated, parameters[0]);

        parameters = MyRecord.class.getDeclaredConstructors()[0].getParameters();
        errors |= assertFlags(mandated, parameters[0]);
        errors |= assertFlags(mandated, parameters[1]);

        // test for synthetic parameters
        // assuming javac creates two synthetic parameters corresponding to Enum(String name, int ordinal)
        parameters = MyEnum.class.getDeclaredConstructors()[0].getParameters();
        errors |= assertFlags(synthetic, parameters[0]);
        errors |= assertFlags(synthetic, parameters[1]);
        errors |= assertFlags(Set.of(), parameters[2]);
        errors |= assertFlags(Set.of(), parameters[3]);

        if (errors) {
            throw new AssertionError();
        }
    }

    // returns true on error
    private static boolean assertFlags(Set<AccessFlag> flags, Parameter parameter) {
        Set<AccessFlag> accessFlags = parameter.accessFlags();
        if (!accessFlags.containsAll(flags)) {
            System.err.println("Required flags not present");
            System.err.println("Required: " + flags);
            System.err.println("Actual: " + accessFlags);
            return true;
        }
        return false;
    }

    private static Class<?> findAnonymous() {
        try {
            return Class.forName("RequiredMethodParameterFlagTest$1");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Anonymous class missing");
        }
    }

    class Inner {
        public Inner(Inner notMandated) {}
    }

    Inner anonymousInner = this.new Inner(null) {};

    enum MyEnum {
        ;
        MyEnum(String s, int i) {}
    }

    record MyRecord(int a, Object b) {
        MyRecord {}
    }
}

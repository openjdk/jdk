/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8315458 8344706
 * @build Implicit TestImplicitClass
 * @run main TestImplicitClass
 * @summary Basic checks of java.lang.Class support for implicit classes
 */

import java.lang.reflect.Modifier;

public class TestImplicitClass {
    public static void main(String... args) throws Exception {
        Class<?> implicitClass = Class.forName("Implicit");

        /*
         * From JEP 463 JLS changes:
         *
         * "An implicit class compilation unit implicitly declares a class that satisfies the following
         * properties:
         * It is always a top level class.
         * It is never abstract (8.1.1.1).
         * It is never synthetic (13.1.11)
         * It is always final (8.1.1.2).
         * It is always a member of unnamed package (7.4.2) and has package access.
         * Its direct superclass type is always Object (8.1.4).
         * It never has any direct superinterface types (8.1.5).
         */

        check(!(implicitClass.isInterface() ||
                implicitClass.isEnum()      ||
                implicitClass.isRecord()    ||
                implicitClass.isHidden()),
              "Implicit class was not a normal class");

        check(!(implicitClass.isLocalClass()  ||
                implicitClass.isMemberClass() ||
                implicitClass.isPrimitive()   ||
                implicitClass.isArray()),
              "Implicit class was not top-level");

        check("Implicit".equals(implicitClass.getName()),     "Unexpected Class.getName result");
        check("Implicit".equals(implicitClass.getTypeName()), "Unexpected Class.getTypeName result");
        check("Implicit".equals(implicitClass.getSimpleName()),      "Unexpected Class.getSimpleName result");
        check("Implicit".equals(implicitClass.getCanonicalName()),      "Unexpected Class.getCanonicalName result");

        int modifiers = implicitClass.getModifiers();
        check((modifiers & Modifier.ABSTRACT) == 0,    "Implicit class was abstract");
        check((modifiers & Modifier.FINAL) != 0,       "Implicit class was not final");
        check((modifiers & (Modifier.PUBLIC  |
                            Modifier.PRIVATE |
                            Modifier.PROTECTED)) == 0, "Implicit class was not package access");

        check(!implicitClass.isSynthetic(), "Implicit class was synthetic");

        check("".equals(implicitClass.getPackage().getName()), "Implicit class not in an implicit package");

        check(implicitClass.getSuperclass() == Object.class, "Superclass was not java.lang.Object");

        check(implicitClass.getInterfaces().length == 0, "Implicit class had super interfaces");
    }

    private static void check(boolean predicate, String message) {
        if (!predicate) {
            throw new RuntimeException(message);
        }
    }
}

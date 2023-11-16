/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8306112
 * @enablePreview
 * @build Unnamed TestUnnamedClass
 * @run main TestUnnamedClass
 * @summary Basic checks of java.lang.Class support for unnamed classes
 */

import java.lang.reflect.Modifier;

public class TestUnnamedClass {
    public static void main(String... args) throws Exception {
        Class<?> unnamedClass = Class.forName("Unnamed");

        /*
         * From JEP 445 JLS changes:
         *
         * "An unnamed class compilation unit implicitly declares a class that satisfies the following
         * properties:
         * It is always a top level class.
         * It is always an unnamed class (it has no canonical or fully qualified name (6.7)).
         * It is never abstract (8.1.1.1).
         * It is always final (8.1.1.2).
         * It is always a member of an unnamed package (7.4.2) and has package access.
         * Its direct superclass type is always Object (8.1.4).
         * It never has any direct superinterface types (8.1.5).
         */

        check(unnamedClass.isUnnamedClass(), "Unnamed class was isUnnamedClass() false");

        check(!(unnamedClass.isInterface() ||
                unnamedClass.isEnum()      ||
                unnamedClass.isRecord()    ||
                unnamedClass.isHidden()),
              "Unnamed class was not a normal class");

        check(!(unnamedClass.isLocalClass()  ||
                unnamedClass.isMemberClass() ||
                unnamedClass.isPrimitive()   ||
                unnamedClass.isArray()),
              "Unnamed class was not top-level");

        check("Unnamed".equals(unnamedClass.getName()),     "Unexpected Class.getName result");
        check("Unnamed".equals(unnamedClass.getTypeName()), "Unexpected Class.getTypeName result");
        check("".equals(unnamedClass.getSimpleName()),      "Unexpected Class.getSimpleName result");
        check(unnamedClass.getCanonicalName() == null,      "Unexpected non-null Class.getCanonicalName");

        int modifiers = unnamedClass.getModifiers();
        check((modifiers & Modifier.ABSTRACT) == 0,    "Unnamed class was abstract");
        check((modifiers & Modifier.FINAL) != 0,       "Unnamed class was not final");
        check((modifiers & (Modifier.PUBLIC  |
                            Modifier.PRIVATE |
                            Modifier.PROTECTED)) == 0, "Unnamed class was not package access");

        check(unnamedClass.isSynthetic(), "Unnamed class was not synthetic");

        check("".equals(unnamedClass.getPackage().getName()), "Unnamed class not in an unnamed package");

        check(unnamedClass.getSuperclass() == Object.class, "Superclass was not java.lang.Object");

        check(unnamedClass.getInterfaces().length == 0, "Unnamed class had super interfaces");
    }

    private static void check(boolean predicate, String message) {
        if (!predicate) {
            throw new RuntimeException(message);
        }
    }
}

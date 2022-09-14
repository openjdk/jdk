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
 * @bug 8281671
 * @summary Checks on various "getFooName" methods of java.lang.Class
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NameTest {
    public static void main(String... args) {
        testCanonicalName();
        testSimpleName();
    }

    private static void testCanonicalName() {
        class LocalClass {} // Local class; no canonical name
        Object o = new Object() {}; // Anonymous class; no canonical name
        Object[] objectArray = {};

        Map<Class<?>, String> expectedCanonicalName = new HashMap<>();

        expectedCanonicalName.put(LocalClass.class,             null);
        expectedCanonicalName.put(o.getClass(),                 null);

        // If a component type doesn't have a canonical name, neither
        // does an array of that type.
        expectedCanonicalName.put(LocalClass.class.arrayType(), null);
        expectedCanonicalName.put(o.getClass().arrayType(),     null);

        expectedCanonicalName.put(int.class,              "int");
        expectedCanonicalName.put(Object.class,           "java.lang.Object");
        expectedCanonicalName.put(objectArray.getClass(), "java.lang.Object[]");

        for (var entry : expectedCanonicalName.entrySet()) {
            var key = entry.getKey();
            var expectedName = entry.getValue();
            String canonicalName = key.getCanonicalName();
            if (!Objects.equals(canonicalName, expectedName)) {
                System.err.println("Unexpected canonical name '" +
                                   canonicalName + "' found for " +
                                   key + ", expected " + expectedName);
                throw new RuntimeException();
            }
        }
    }

    private static void testSimpleName() {
        class ALocalClass {} // Local class
        Object o = new Object() {}; // Anonymous class, empty simple name
        Object[] objectArray = {};

        Map<Class<?>, String> expectedSimpleName = new HashMap<>();

        expectedSimpleName.put(ALocalClass.class,             "ALocalClass");
        expectedSimpleName.put(o.getClass(),                  "");

        expectedSimpleName.put(ALocalClass.class.arrayType(), "ALocalClass[]");
        expectedSimpleName.put(o.getClass().arrayType(),      "[]");

        expectedSimpleName.put(int.class,                     "int");
        expectedSimpleName.put(Object.class,                  "Object");
        expectedSimpleName.put(objectArray.getClass(),        "Object[]");

        for (var entry : expectedSimpleName.entrySet()) {
            var key = entry.getKey();
            var expectedName = entry.getValue();
            String simpleName = key.getSimpleName();
            if (!Objects.equals(simpleName, expectedName)) {
                System.err.println("Unexpected simple name '" +
                                   simpleName + "' found for " +
                                   key + ", expected " + expectedName);
                throw new RuntimeException();
            }
        }
    }
}

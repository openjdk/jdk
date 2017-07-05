/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6298888 6992705
 * @summary Check Class.toGenericString()
 * @author Joseph D. Darcy
 */

import java.lang.reflect.*;
import java.lang.annotation.*;
import java.util.*;

@ExpectedGenericString("public class GenericStringTest")
public class GenericStringTest {
    public Map<String, Integer>[] mixed = null;
    public Map<String, Integer>[][] mixed2 = null;

    public static void main(String... args) throws ReflectiveOperationException {
        int failures = 0;

        String[][] nested = {{""}};
        int[][]    intArray = {{1}};

        failures += checkToGenericString(int.class, "int");
        failures += checkToGenericString(void.class, "void");
        failures += checkToGenericString(args.getClass(), "java.lang.String[]");
        failures += checkToGenericString(nested.getClass(), "java.lang.String[][]");
        failures += checkToGenericString(intArray.getClass(), "int[][]");
        failures += checkToGenericString(java.util.Map.class, "public abstract interface java.util.Map<K,V>");

        Field f = GenericStringTest.class.getDeclaredField("mixed");
        // The expected value includes "<K,V>" rather than
        // "<...String,...Integer>" since the Class object rather than
        // Type objects is being queried.
        failures += checkToGenericString(f.getType(), "java.util.Map<K,V>[]");
        f = GenericStringTest.class.getDeclaredField("mixed2");
        failures += checkToGenericString(f.getType(), "java.util.Map<K,V>[][]");

        Class<?>[] types = {
            GenericStringTest.class,
            AnInterface.class,
            LocalMap.class,
            AnEnum.class,
            AnotherEnum.class,
        };

        for(Class<?> clazz : types) {
            failures += checkToGenericString(clazz, clazz.getAnnotation(ExpectedGenericString.class).value());
        }

        if (failures > 0) {
            throw new RuntimeException();
        }
    }

    private static int checkToGenericString(Class<?> clazz, String expected) {
        String genericString = clazz.toGenericString();
        if (!genericString.equals(expected)) {
            System.err.printf("Unexpected Class.toGenericString output; expected '%s', got '%s'.%n",
                              expected,
                              genericString);
            return 1;
        } else
            return 0;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedGenericString {
    String value();
}

@ExpectedGenericString("abstract interface AnInterface")
strictfp interface AnInterface {}

@ExpectedGenericString("abstract interface LocalMap<K,V>")
interface LocalMap<K,V> {}

@ExpectedGenericString("final enum AnEnum")
enum AnEnum {
    FOO;
}

@ExpectedGenericString("enum AnotherEnum")
enum AnotherEnum {
    BAR{};
}

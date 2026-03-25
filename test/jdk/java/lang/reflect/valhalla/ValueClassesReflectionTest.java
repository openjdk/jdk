/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8334334
 * @summary reflection test for value classes
 * @enablePreview
 * @compile ValueClassesReflectionTest.java
 * @run testng/othervm ValueClassesReflectionTest
 */

import java.lang.annotation.*;
import java.lang.constant.ClassDesc;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.testng.annotations.*;
import static org.testng.Assert.*;

@Test
public class ValueClassesReflectionTest {
    final static int numberOfFields = 2;

    value class ValueClass {
        private int i = 0;
        private String s = "";
    }
    abstract value class AValueClass {
        private int i = 0;
        private String s = "";
    }
    value record ValueRecord(int i, String s) {}

    class Inner {}

    @DataProvider(name = "valueClasses")
    public Object[][] valueClassesData() {
        return List.of(
                ValueClass.class,
                AValueClass.class,
                ValueRecord.class
        ).stream().map(c -> new Object[] {c}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "valueClasses")
    public void testValueClasses(Class<?> cls) {
        assertTrue(cls.isValue());
        assertTrue(!cls.isIdentity());
        Set<AccessFlag> accessFlagSet = cls.accessFlags();
        assertTrue(!accessFlagSet.contains(AccessFlag.IDENTITY));
    }

    @DataProvider(name = "notValueClasses")
    public Object[][] notSealedClassesData() {
        return List.of(
                Inner.class,
                Object.class,
                Void.class, Void[].class,
                byte[].class, Byte[].class,
                short[].class, Short[].class,
                char[].class, Character[].class,
                int[].class, Integer[].class,
                long[].class, Long[].class,
                float[].class, Float[].class,
                double[].class, Double[].class,
                boolean[].class, Boolean[].class,
                String.class, String[].class
        ).stream().map(c -> new Object[] {c}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "notValueClasses")
    public void testNotValueClasses(Class<?> cls) {
        assertTrue(!cls.isValue(), " failing for class " + cls);
        assertTrue(cls.isIdentity());
    }

    @Test(dataProvider = "valueClasses")
    public void testValueClassReflection(Class<?> valueClass) throws ReflectiveOperationException {
        assertTrue(valueClass.isValue());
        Field[] fields = valueClass.getDeclaredFields();
        assertTrue(fields.length == numberOfFields);
        for (Field field : fields) {
            int mod = field.getModifiers();
            assertTrue((mod & Modifier.STRICT) != 0);
            assertTrue((mod & Modifier.FINAL) != 0);
        }
    }
}

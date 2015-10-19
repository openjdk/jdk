/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @compile TestResolvedJavaField.java FieldUniverse.java TypeUniverse.java TestMetaAccessProvider.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI jdk.vm.ci.runtime.test.TestResolvedJavaField
 */

package jdk.vm.ci.runtime.test;

import static org.junit.Assert.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import jdk.vm.ci.meta.*;

import org.junit.*;

/**
 * Tests for {@link ResolvedJavaField}.
 */
public class TestResolvedJavaField extends FieldUniverse {

    public TestResolvedJavaField() {
    }

    @Test
    public void getModifiersTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            int expected = e.getKey().getModifiers();
            int actual = e.getValue().getModifiers();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void isSyntheticTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            boolean expected = e.getKey().isSynthetic();
            boolean actual = e.getValue().isSynthetic();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void getAnnotationsTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            Annotation[] expected = e.getKey().getAnnotations();
            Annotation[] actual = e.getValue().getAnnotations();
            assertArrayEquals(expected, actual);
        }
    }

    @Test
    public void getAnnotationTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            for (Annotation expected : e.getKey().getAnnotations()) {
                if (expected != null) {
                    Annotation actual = e.getValue().getAnnotation(expected.annotationType());
                    assertEquals(expected, actual);
                }
            }
        }
    }

    @Test
    public void getLocationIdentityTest() {
        for (Map.Entry<Field, ResolvedJavaField> e : fields.entrySet()) {
            LocationIdentity identity = e.getValue().getLocationIdentity();
            assertTrue(identity != null);
        }
    }

    static class ReadConstantValueTestConstants {
        String stringField = "field";
        final String constantStringField = "constantField";

        static final Object CONST1 = new ReadConstantValueTestConstants();
        static final Object CONST2 = null;
        static final Object CONST3 = new String();
    }

    @Test
    public void readConstantValueTest() throws NoSuchFieldException {
        ResolvedJavaField field = metaAccess.lookupJavaField(ReadConstantValueTestConstants.class.getDeclaredField("stringField"));
        List<ConstantValue> receiverConstants = readConstants(ReadConstantValueTestConstants.class);
        for (ConstantValue receiver : receiverConstants) {
            JavaConstant value = constantReflection.readConstantFieldValue(field, receiver.value);
            assertNull(value);
        }

        ResolvedJavaField constField = metaAccess.lookupJavaField(ReadConstantValueTestConstants.class.getDeclaredField("constantStringField"));
        for (ConstantValue receiver : receiverConstants) {
            JavaConstant value = constantReflection.readConstantFieldValue(constField, receiver.value);
            if (value != null) {
                Object expected = "constantField";
                String actual = ((ReadConstantValueTestConstants) receiver.boxed).constantStringField;
                assertTrue(actual + " != " + expected, actual == expected);
            }
        }
    }

    private Method findTestMethod(Method apiMethod) {
        String testName = apiMethod.getName() + "Test";
        for (Method m : getClass().getDeclaredMethods()) {
            if (m.getName().equals(testName) && m.getAnnotation(Test.class) != null) {
                return m;
            }
        }
        return null;
    }

    // @formatter:off
    private static final String[] untestedApiMethods = {
        "getDeclaringClass",
        "isInternal",
        "isFinal"
    };
    // @formatter:on

    /**
     * Ensures that any new methods added to {@link ResolvedJavaMethod} either have a test written
     * for them or are added to {@link #untestedApiMethods}.
     */
    @Test
    public void testCoverage() {
        Set<String> known = new HashSet<>(Arrays.asList(untestedApiMethods));
        for (Method m : ResolvedJavaField.class.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            if (findTestMethod(m) == null) {
                assertTrue("test missing for " + m, known.contains(m.getName()));
            } else {
                assertFalse("test should be removed from untestedApiMethods" + m, known.contains(m.getName()));
            }
        }
    }
}

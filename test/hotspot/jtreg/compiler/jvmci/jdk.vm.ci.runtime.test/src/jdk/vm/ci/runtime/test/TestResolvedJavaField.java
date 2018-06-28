/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.jvmci
 * @library ../../../../../
 * @modules jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          java.base/jdk.internal.misc
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Djvmci.Compiler=null jdk.vm.ci.runtime.test.TestResolvedJavaField
 */

package jdk.vm.ci.runtime.test;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        "getOffset",
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

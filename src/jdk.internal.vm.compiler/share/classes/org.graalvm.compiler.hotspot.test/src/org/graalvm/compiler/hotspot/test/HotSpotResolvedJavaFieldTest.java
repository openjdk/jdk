/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.TRANSIENT;
import static java.lang.reflect.Modifier.VOLATILE;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tests {@link HotSpotResolvedJavaField} functionality.
 */
public class HotSpotResolvedJavaFieldTest extends HotSpotGraalCompilerTest {

    private static final Class<?>[] classesWithInternalFields = {Class.class, ClassLoader.class};

    private static final Method createFieldMethod;
    private static final Field indexField;

    static {
        Method m = null;
        Field f = null;
        try {
            Class<?> typeImpl = Class.forName("jdk.vm.ci.hotspot.HotSpotResolvedObjectTypeImpl");
            m = typeImpl.getDeclaredMethod("createField", JavaType.class, long.class, int.class, int.class);
            Util.setAccessible(m, true);
            Class<?> fieldImpl = Class.forName("jdk.vm.ci.hotspot.HotSpotResolvedJavaFieldImpl");
            f = fieldImpl.getDeclaredField("index");
            Util.setAccessible(f, true);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        createFieldMethod = m;
        indexField = f;
    }

    /**
     * Same as {@code HotSpotModifiers.jvmFieldModifiers()} but works when using a JVMCI version
     * prior to the introduction of that method.
     */
    private int jvmFieldModifiers() {
        GraalHotSpotVMConfig config = runtime().getVMConfig();
        int accEnum = config.getConstant("JVM_ACC_ENUM", Integer.class, 0x4000);
        int accSynthetic = config.getConstant("JVM_ACC_SYNTHETIC", Integer.class, 0x1000);
        return PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | VOLATILE | TRANSIENT | accEnum | accSynthetic;
    }

    /**
     * Tests that {@link HotSpotResolvedJavaField#getModifiers()} only includes the modifiers
     * returned by {@link Field#getModifiers()}. Namely, it must not include
     * {@code HotSpotResolvedJavaField#FIELD_INTERNAL_FLAG}.
     */
    @Test
    public void testModifiersForInternal() {
        for (Class<?> c : classesWithInternalFields) {
            ResolvedJavaType type = getMetaAccess().lookupJavaType(c);
            for (ResolvedJavaField field : type.getInstanceFields(false)) {
                if (field.isInternal()) {
                    Assert.assertEquals(0, ~jvmFieldModifiers() & field.getModifiers());
                }
            }
        }
    }

    /**
     * Tests that {@code HotSpotResolvedObjectTypeImpl#createField(String, JavaType, long, int)}
     * always returns an {@linkplain ResolvedJavaField#equals(Object) equivalent} object for an
     * internal field.
     *
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    @Test
    public void testEquivalenceForInternalFields() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        for (Class<?> c : classesWithInternalFields) {
            ResolvedJavaType type = getMetaAccess().lookupJavaType(c);
            for (ResolvedJavaField field : type.getInstanceFields(false)) {
                if (field.isInternal()) {
                    HotSpotResolvedJavaField expected = (HotSpotResolvedJavaField) field;
                    int index = indexField.getInt(expected);
                    ResolvedJavaField actual = (ResolvedJavaField) createFieldMethod.invoke(type, expected.getType(), expected.getOffset(), expected.getModifiers(), index);
                    Assert.assertEquals(expected, actual);
                }
            }
        }
    }

    @Test
    public void testIsInObject() {
        for (Field f : String.class.getDeclaredFields()) {
            HotSpotResolvedJavaField rf = (HotSpotResolvedJavaField) getMetaAccess().lookupJavaField(f);
            Assert.assertEquals(rf.toString(), rf.isInObject(getConstantReflection().forString("a string")), !rf.isStatic());
        }
    }
}

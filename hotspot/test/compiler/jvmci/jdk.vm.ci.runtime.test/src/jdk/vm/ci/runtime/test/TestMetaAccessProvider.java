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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library ../../../../../
 * @modules jdk.vm.ci/jdk.vm.ci.meta
 *          jdk.vm.ci/jdk.vm.ci.runtime
 * @build jdk.vm.ci.runtime.test.TestMetaAccessProvider
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI jdk.vm.ci.runtime.test.TestMetaAccessProvider
 */

package jdk.vm.ci.runtime.test;

import static jdk.vm.ci.meta.MetaUtil.toInternalName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.junit.Test;

/**
 * Tests for {@link MetaAccessProvider}.
 */
public class TestMetaAccessProvider extends TypeUniverse {

    @Test
    public void lookupJavaTypeTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            assertNotNull(c.toString(), type);
            assertEquals(c.toString(), type.getName(), toInternalName(c.getName()));
            assertEquals(c.toString(), type.getName(), toInternalName(type.toJavaName()));
            assertEquals(c.toString(), c.getName(), type.toClassName());
            if (!type.isArray()) {
                assertEquals(c.toString(), c.getName(), type.toJavaName());
            }
        }
    }

    @Test
    public void lookupJavaMethodTest() {
        for (Class<?> c : classes) {
            for (Method reflect : c.getDeclaredMethods()) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(reflect);
                assertNotNull(method);
                assertTrue(method.getDeclaringClass().equals(metaAccess.lookupJavaType(reflect.getDeclaringClass())));
            }
        }
    }

    @Test
    public void lookupJavaFieldTest() {
        for (Class<?> c : classes) {
            for (Field reflect : c.getDeclaredFields()) {
                ResolvedJavaField field = metaAccess.lookupJavaField(reflect);
                assertNotNull(field);
                assertTrue(field.getDeclaringClass().equals(metaAccess.lookupJavaType(reflect.getDeclaringClass())));
            }
        }
    }

    @Test
    public void lookupJavaTypeConstantTest() {
        for (ConstantValue cv : constants()) {
            JavaConstant c = cv.value;
            if (c.getJavaKind() == JavaKind.Object && !c.isNull()) {
                Object o = cv.boxed;
                ResolvedJavaType type = metaAccess.lookupJavaType(c);
                assertNotNull(type);
                assertTrue(type.equals(metaAccess.lookupJavaType(o.getClass())));
            } else {
                assertEquals(metaAccess.lookupJavaType(c), null);
            }
        }
    }
}

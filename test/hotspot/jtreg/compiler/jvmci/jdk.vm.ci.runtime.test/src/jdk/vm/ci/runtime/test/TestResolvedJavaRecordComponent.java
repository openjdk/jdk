/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *          java.base/java.lang:open
 *          java.base/java.lang.reflect:open
 *          jdk.internal.vm.ci/jdk.vm.ci.meta.annotation
 *          jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.common
 *          java.base/jdk.internal.reflect
 *          java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm
 *          java.base/sun.reflect.annotation
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler jdk.vm.ci.runtime.test.TestResolvedJavaRecordComponent
 */

package jdk.vm.ci.runtime.test;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link ResolvedJavaRecordComponent}.
 */
public class TestResolvedJavaRecordComponent extends FieldUniverse {

    @Test
    public void equalsTest() {
        for (ResolvedJavaRecordComponent f : recordComponents.values()) {
            for (ResolvedJavaRecordComponent that : recordComponents.values()) {
                boolean expect = f == that;
                boolean actual = f.equals(that);
                assertEquals(expect, actual);
            }
        }
    }

    @Test
    public void getDeclaringRecordTest() {
        for (Map.Entry<RecordComponent, ResolvedJavaRecordComponent> e : recordComponents.entrySet()) {
            ResolvedJavaRecordComponent rc = e.getValue();
            ResolvedJavaType actual = rc.getDeclaringRecord();
            ResolvedJavaType expect = metaAccess.lookupJavaType(e.getKey().getDeclaringRecord());
            assertEquals(rc.toString(), expect, actual);
        }
    }

    @Test
    public void getNameTest() {
        for (Map.Entry<RecordComponent, ResolvedJavaRecordComponent> e : recordComponents.entrySet()) {
            ResolvedJavaRecordComponent rc = e.getValue();
            String actual = rc.getName();
            String expect = e.getKey().getName();
            assertEquals(rc.toString(), expect, actual);
        }
    }

    @Test
    public void getAccessorTest() {
        for (Map.Entry<RecordComponent, ResolvedJavaRecordComponent> e : recordComponents.entrySet()) {
            ResolvedJavaRecordComponent rc = e.getValue();
            Method expect = e.getKey().getAccessor();
            ResolvedJavaMethod actual = rc.getAccessor();
            assertEquals(rc.toString(), expect.getName(), actual.getName());
        }
    }

    @Test
    public void getTypeTest() {
        for (Map.Entry<RecordComponent, ResolvedJavaRecordComponent> e : recordComponents.entrySet()) {
            ResolvedJavaRecordComponent rc = e.getValue();
            JavaType actual = rc.getType();
            JavaType expect = metaAccess.lookupJavaType(e.getKey().getType()).resolve(rc.getDeclaringRecord());
            assertEquals(expect, actual);
        }
    }

    @Test
    public void getTypeAnnotationInfoTest() {
        for (RecordComponent rc : recordComponents.keySet()) {
            ResolvedJavaRecordComponent jrc = metaAccess.lookupJavaRecordComponent(rc);
            byte[] rawAnnotations = getFieldValue(recordComponentTypeAnnotations, rc);
            TestResolvedJavaType.checkRawAnnotations(jrc, "getTypeAnnotationInfo", rawAnnotations, jrc.getTypeAnnotationInfo());
        }
    }

    @Test
    public void getDeclaredAnnotationInfoTest() {
        for (RecordComponent rc : recordComponents.keySet()) {
            ResolvedJavaRecordComponent jrc = metaAccess.lookupJavaRecordComponent(rc);
            byte[] rawAnnotations = getFieldValue(recordComponentAnnotations, rc);
            TestResolvedJavaType.checkRawAnnotations(jrc, "getDeclaredAnnotationInfo", rawAnnotations, jrc.getDeclaredAnnotationInfo());
        }
    }

    private static final Field recordComponentAnnotations = lookupField(RecordComponent.class, "annotations");
    private static final Field recordComponentTypeAnnotations = lookupField(RecordComponent.class, "typeAnnotations");

}

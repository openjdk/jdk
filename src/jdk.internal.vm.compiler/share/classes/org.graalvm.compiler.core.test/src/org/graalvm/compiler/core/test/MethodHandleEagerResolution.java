/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import org.junit.Test;

import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.test.AddExports;

// Export needed to open String.value field to reflection by this test
@AddExports("java.base/java.lang")
public final class MethodHandleEagerResolution extends GraalCompilerTest {
    private static final MethodHandle FIELD_HANDLE;

    static {
        Field field;
        try {
            field = String.class.getDeclaredField("value");
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
        field.setAccessible(true);

        try {
            FIELD_HANDLE = MethodHandles.lookup().unreflectGetter(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("unable to initialize field handle", e);
        }
    }

    public static char[] getBackingCharArray(String str) {
        try {
            return (char[]) FIELD_HANDLE.invokeExact(str);
        } catch (Throwable e) {
            throw new IllegalStateException();
        }
    }

    @Test
    public void testFieldInvokeExact() {
        StructuredGraph graph = parseEager("getBackingCharArray", AllowAssumptions.NO);
        assertTrue(graph.getNodes().filter(DeoptimizeNode.class).isEmpty());
    }

}

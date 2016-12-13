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
package org.graalvm.compiler.core.test.ea;

import jdk.vm.ci.meta.JavaConstant;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;

public class UnsafeEATest extends EATestBase {

    public static int zero = 0;

    private static final long fieldOffset1;
    private static final long fieldOffset2;

    static {
        try {
            long localFieldOffset1 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("x"));
            // Make the fields 8 byte aligned (Required for testing setLong on Architectures which
            // does not support unaligned memory access
            if (localFieldOffset1 % 8 == 0) {
                fieldOffset1 = localFieldOffset1;
                fieldOffset2 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("y"));
            } else {
                fieldOffset1 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("y"));
                fieldOffset2 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("z"));
            }
            assert fieldOffset2 == fieldOffset1 + 4;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSimpleInt() {
        testEscapeAnalysis("testSimpleIntSnippet", JavaConstant.forInt(101), false);
    }

    public static int testSimpleIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putInt(x, fieldOffset1, 101);
        return UNSAFE.getInt(x, fieldOffset1);
    }

    @Test
    public void testMaterializedInt() {
        test("testMaterializedIntSnippet");
    }

    public static TestClassInt testMaterializedIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putInt(x, fieldOffset1, 101);
        return x;
    }

    @Test
    public void testSimpleDouble() {
        testEscapeAnalysis("testSimpleDoubleSnippet", JavaConstant.forDouble(10.1), false);
    }

    public static double testSimpleDoubleSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.1);
        return UNSAFE.getDouble(x, fieldOffset1);
    }

    @Test
    public void testMergedDouble() {
        testEscapeAnalysis("testMergedDoubleSnippet", null, false);
        Assert.assertEquals(1, returnNodes.size());
        Assert.assertTrue(returnNodes.get(0).result() instanceof ValuePhiNode);
        PhiNode phi = (PhiNode) returnNodes.get(0).result();
        Assert.assertTrue(phi.valueAt(0) instanceof LoadFieldNode);
        Assert.assertTrue(phi.valueAt(1) instanceof LoadFieldNode);
    }

    public static double testMergedDoubleSnippet(boolean a) {
        TestClassInt x;
        if (a) {
            x = new TestClassInt(0, 0);
            UNSAFE.putDouble(x, fieldOffset1, doubleField);
        } else {
            x = new TestClassInt();
            UNSAFE.putDouble(x, fieldOffset1, doubleField2);
        }
        return UNSAFE.getDouble(x, fieldOffset1);
    }

    @Test
    public void testMaterializedDouble() {
        test("testMaterializedDoubleSnippet");
    }

    public static TestClassInt testMaterializedDoubleSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.1);
        return x;
    }

    @Test
    public void testDeoptDoubleVar() {
        test("testDeoptDoubleVarSnippet");
    }

    public static double doubleField = 10.1e99;
    public static double doubleField2;

    public static TestClassInt testDeoptDoubleVarSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, doubleField);
        doubleField2 = 123;
        try {
            doubleField = ((int) UNSAFE.getDouble(x, fieldOffset1)) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptDoubleConstant() {
        test("testDeoptDoubleConstantSnippet");
    }

    public static TestClassInt testDeoptDoubleConstantSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.123);
        doubleField2 = 123;
        try {
            doubleField = ((int) UNSAFE.getDouble(x, fieldOffset1)) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptLongVar() {
        test("testDeoptLongVarSnippet");
    }

    public static long longField = 0x133443218aaaffffL;
    public static long longField2;

    public static TestClassInt testDeoptLongVarSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putLong(x, fieldOffset1, longField);
        longField2 = 123;
        try {
            longField = UNSAFE.getLong(x, fieldOffset1) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptLongConstant() {
        test("testDeoptLongConstantSnippet");
    }

    public static TestClassInt testDeoptLongConstantSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putLong(x, fieldOffset1, 0x2222222210123L);
        longField2 = 123;
        try {
            longField = UNSAFE.getLong(x, fieldOffset1) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }
}

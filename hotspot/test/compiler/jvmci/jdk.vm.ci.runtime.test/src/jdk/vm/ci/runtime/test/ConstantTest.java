/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @build jdk.vm.ci.runtime.test.ConstantTest
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI jdk.vm.ci.runtime.test.ConstantTest
 */
// * @compile ConstantTest.java FieldUniverse.java TypeUniverse.java TestMetaAccessProvider.java
package jdk.vm.ci.runtime.test;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

import org.junit.Assert;
import org.junit.Test;

public class ConstantTest extends FieldUniverse {

    @Test
    public void testNegativeZero() {
        Assert.assertTrue("Constant for 0.0f must be different from -0.0f", JavaConstant.FLOAT_0 != JavaConstant.forFloat(-0.0F));
        Assert.assertTrue("Constant for 0.0d must be different from -0.0d", JavaConstant.DOUBLE_0 != JavaConstant.forDouble(-0.0d));
    }

    @Test
    public void testNullIsNull() {
        Assert.assertTrue(JavaConstant.NULL_POINTER.isNull());
    }

    @Test
    public void testOne() {
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isNumericInteger() || kind.isNumericFloat()) {
                Assert.assertTrue(JavaConstant.one(kind).getJavaKind() == kind);
            }
        }
        Assert.assertEquals(1, JavaConstant.one(JavaKind.Int).asInt());
        Assert.assertEquals(1L, JavaConstant.one(JavaKind.Long).asLong());
        Assert.assertEquals(1, JavaConstant.one(JavaKind.Byte).asInt());
        Assert.assertEquals(1, JavaConstant.one(JavaKind.Short).asInt());
        Assert.assertEquals(1, JavaConstant.one(JavaKind.Char).asInt());
        Assert.assertTrue(1F == JavaConstant.one(JavaKind.Float).asFloat());
        Assert.assertTrue(1D == JavaConstant.one(JavaKind.Double).asDouble());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalOne() {
        JavaConstant.one(JavaKind.Illegal);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVoidOne() {
        JavaConstant.one(JavaKind.Void);
    }
}

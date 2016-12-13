/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import jdk.vm.ci.meta.JavaKind;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.nodes.type.StampTool;

public class ObjectStampJoinTest extends AbstractObjectStampTest {

    // class A
    // class B extends A
    // class C extends B implements I
    // class D extends A
    // abstract class E extends A
    // interface I

    @Test
    public void testJoin0() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp b = StampFactory.object(getType(B.class));
        Assert.assertEquals(b, join(a, b));
    }

    @Test
    public void testJoin1() {
        Stamp aNonNull = StampFactory.objectNonNull(getType(A.class));
        Stamp b = StampFactory.object(getType(B.class));
        Stamp bNonNull = StampFactory.objectNonNull(getType(B.class));
        Assert.assertEquals(bNonNull, join(aNonNull, b));
    }

    @Test
    public void testJoin2() {
        Stamp aExact = StampFactory.objectNonNull(getType(A.class).asExactReference());
        Stamp b = StampFactory.object(getType(B.class));
        Assert.assertEquals(StampFactory.empty(JavaKind.Object), join(aExact, b));
    }

    @Test
    public void testJoin3() {
        Stamp d = StampFactory.object(getType(D.class));
        Stamp c = StampFactory.object(getType(C.class));
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join(c, d)));
    }

    @Test
    public void testJoin4() {
        Stamp dExactNonNull = StampFactory.objectNonNull(getType(D.class));
        Stamp c = StampFactory.object(getType(C.class));
        Assert.assertEquals(StampFactory.empty(JavaKind.Object), join(c, dExactNonNull));
    }

    @Test
    public void testJoin5() {
        Stamp dExact = StampFactory.object(getType(D.class).asExactReference());
        Stamp c = StampFactory.object(getType(C.class));
        Stamp join = join(c, dExact);
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join));
        Assert.assertNull(StampTool.typeReferenceOrNull(join));
        Assert.assertFalse(StampTool.isExactType(join));
    }

    @Test
    public void testJoin6() {
        Stamp dExactNonNull = StampFactory.objectNonNull(getType(D.class).asExactReference());
        Stamp alwaysNull = StampFactory.alwaysNull();
        Stamp join = join(alwaysNull, dExactNonNull);
        Assert.assertFalse(join.hasValues());
        Assert.assertFalse(StampTool.isPointerAlwaysNull(join));
    }

    @Test
    public void testJoin7() {
        Stamp aExact = StampFactory.object(getType(A.class).asExactReference());
        Stamp e = StampFactory.object(getType(E.class));
        Stamp join = join(aExact, e);
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join));
        Assert.assertNull(StampTool.typeReferenceOrNull(join));
        Assert.assertFalse(StampTool.isExactType(join));
    }

    @Test
    public void testJoin8() {
        Stamp bExact = StampFactory.objectNonNull(getType(B.class).asExactReference());
        Stamp dExact = StampFactory.object(getType(D.class).asExactReference());
        Stamp join = join(bExact, dExact);
        Assert.assertFalse(join.hasValues());
    }

    @Test
    public void testJoin9() {
        Stamp bExact = StampFactory.object(getType(B.class).asExactReference());
        Stamp dExact = StampFactory.object(getType(D.class).asExactReference());
        Stamp join = join(bExact, dExact);
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join));
        Assert.assertNull(StampTool.typeReferenceOrNull(join));
        Assert.assertNull(StampTool.typeReferenceOrNull(join));
    }

    @Test
    public void testJoinInterfaceSimple() {
        // Tests joining of interface
        testJoinInterface(A.class, B.class, I.class);
    }

    @Test
    public void testJoinInterfaceArray() {
        // Tests joining of arrays interface
        testJoinInterface(A[].class, B[].class, I[].class);
    }

    @Test
    public void testJoinInterfaceMultiArray() {
        // Tests joining of multidimensional arrays of interface
        testJoinInterface(A[][].class, B[][].class, I[][].class);
    }

    private void testJoinInterface(Class<?> typeA, Class<?> typeB, Class<?> typeI) {
        testJoinInterface0(typeA, typeI);
        testJoinInterface1(typeA, typeI);
        testJoinInterface2(typeB, typeI);
        testJoinInterface3(typeB, typeI);
    }

    private void testJoinInterface0(Class<?> typeA, Class<?> typeI) {
        Stamp a = StampFactory.object(getType(typeA));
        Stamp i = StampFactory.object(getType(typeI));
        Assert.assertNotSame(StampFactory.empty(JavaKind.Object), join(a, i));
    }

    private void testJoinInterface1(Class<?> typeA, Class<?> typeI) {
        Stamp aNonNull = StampFactory.objectNonNull(getType(typeA));
        Stamp i = StampFactory.object(getType(typeI));
        Stamp join = join(aNonNull, i);
        Assert.assertTrue(join instanceof ObjectStamp);
        Assert.assertTrue(((ObjectStamp) join).nonNull());
    }

    private void testJoinInterface2(Class<?> typeB, Class<?> typeI) {
        Stamp bExact = StampFactory.objectNonNull(getType(typeB).asExactReference());
        Stamp i = StampFactory.object(getType(typeI));
        Stamp join = join(i, bExact);
        Assert.assertEquals(StampFactory.empty(JavaKind.Object), join);
    }

    private void testJoinInterface3(Class<?> typeB, Class<?> typeI) {
        Stamp bExact = StampFactory.objectNonNull(getType(typeB).asExactReference());
        // Create non-trusted reference.
        Stamp i = StampFactory.object(TypeReference.createWithoutAssumptions(getType(typeI).getType()));
        Stamp join = join(i, bExact);
        Assert.assertEquals(bExact, join);
    }

}

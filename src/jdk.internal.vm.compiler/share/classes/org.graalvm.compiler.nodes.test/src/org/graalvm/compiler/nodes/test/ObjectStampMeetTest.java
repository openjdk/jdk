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

public class ObjectStampMeetTest extends AbstractObjectStampTest {

    @Test
    public void testMeet0() {
        check(A.class, B.class, A.class);
    }

    @Test
    public void testMeet1() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp aNonNull = StampFactory.objectNonNull(getType(A.class));
        Stamp b = StampFactory.object(getType(B.class));
        Stamp bNonNull = StampFactory.objectNonNull(getType(B.class));
        Assert.assertEquals(a, meet(aNonNull, b));
        Assert.assertEquals(aNonNull, meet(aNonNull, bNonNull));
    }

    @Test
    public void testMeet2() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp aExact = StampFactory.objectNonNull(getType(A.class).asExactReference());
        Stamp b = StampFactory.object(getType(B.class));
        Assert.assertEquals(a, meet(aExact, b));
    }

    @Test
    public void testMeet3() {
        check(C.class, D.class, A.class);
    }

    @Test
    public void testMeet4() {
        Stamp dExactNonNull = StampFactory.objectNonNull(getType(D.class).asExactReference());
        Stamp cExactNonNull = StampFactory.objectNonNull(getType(C.class).asExactReference());
        Stamp aNonNull = StampFactory.objectNonNull(getType(A.class));
        Assert.assertEquals(aNonNull, meet(cExactNonNull, dExactNonNull));
    }

    @Test
    public void testMeet5() {
        Stamp dExact = StampFactory.object(getType(D.class).asExactReference());
        Stamp c = StampFactory.object(getType(C.class));
        Stamp a = StampFactory.object(getType(A.class));
        Assert.assertEquals(a, meet(dExact, c));
    }

    @Test
    public void testMeet6() {
        Stamp dExactNonNull = StampFactory.objectNonNull(getType(D.class).asExactReference());
        Stamp alwaysNull = StampFactory.alwaysNull();
        Stamp dExact = StampFactory.object(getType(D.class).asExactReference());
        Assert.assertEquals(dExact, meet(dExactNonNull, alwaysNull));
    }

    @Test
    public void testMeet7() {
        Stamp aExact = StampFactory.object(getType(A.class).asExactReference());
        Stamp e = StampFactory.object(getType(E.class));
        Stamp a = StampFactory.object(getType(A.class));
        Assert.assertEquals(a, meet(aExact, e));
    }

    @Test
    public void testMeet8() {
        check(A.class, A.class, A.class);
    }

    @Test
    public void testMeet9() {
        Stamp base1 = StampFactory.object(getType(Base1.class));
        Stamp ord1 = StampFactory.object(getType(ImplOrder1.class));
        Stamp ord2 = StampFactory.object(getType(ImplOrder2.class));
        Assert.assertEquals(base1, meet(ord1, ord2));
    }

    @Test
    public void testMeet10() {
        Stamp base1 = StampFactory.object(getType(Object.class));
        Stamp ord1 = StampFactory.object(getType(Deep1.class));
        Stamp ord2 = StampFactory.object(getType(Deep2.class));
        Assert.assertEquals(base1, meet(ord1, ord2));
    }

    @Test
    public void testMeetInterface0() {
        check(C.class, I.class, I.class);
    }

    @Test
    public void testMeetInterface1() {
        check(I.class, SubI1.class, I.class);
    }

    @Test
    public void testMeetInterface2() {
        check(SubI1.class, SubI2.class, I.class);
    }

    @Test
    public void testMeetInterface3() {
        check(SubI4.class, SubI5.class, SubI3.class);
    }

    @Test
    public void testMeetInterface4() {
        check(SubI4.class, SubI6.class, Object.class);
    }

    private void check(Class<?> a, Class<?> b, Class<?> result) {
        Stamp aStamp = StampFactory.object(getType(a));
        Stamp bStamp = StampFactory.object(getType(b));
        ObjectStamp resultStamp = StampFactory.object(getType(result));
        Assert.assertEquals(resultStamp, meet(aStamp, bStamp));
    }

    @Test
    public void testMeetIllegal1() {
        for (Class<?> clazz : new Class<?>[]{A.class, B.class, C.class, D.class, E.class, I.class, Object.class}) {
            TypeReference type = getType(clazz);
            for (Stamp test : new Stamp[]{StampFactory.object(type), StampFactory.objectNonNull(type), StampFactory.object(type.asExactReference()),
                            StampFactory.objectNonNull(type.asExactReference())}) {
                if (type.getType().isConcrete() || !((ObjectStamp) test).isExactType()) {
                    Assert.assertEquals("meeting empty and " + test, test, meet(StampFactory.empty(JavaKind.Object), test));
                }
            }
        }
    }
}

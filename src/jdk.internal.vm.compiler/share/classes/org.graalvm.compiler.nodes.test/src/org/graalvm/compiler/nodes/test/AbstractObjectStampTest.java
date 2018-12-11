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


package org.graalvm.compiler.nodes.test;

import org.junit.Assert;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.core.test.GraalCompilerTest;

public abstract class AbstractObjectStampTest extends GraalCompilerTest {

    protected static class A {

    }

    protected static class B extends A {

    }

    protected static class C extends B implements I {

    }

    protected static class D extends A {

    }

    protected abstract static class E extends A {

    }

    protected interface I {

    }

    protected interface OtherI {

    }

    protected interface SubI1 extends I {

    }

    protected interface SubI2 extends I {

    }

    protected interface SubI3 extends I {

    }

    protected interface SubI4 extends SubI3, SubI1 {
    }

    protected interface SubI5 extends SubI3, OtherI {

    }

    protected interface SubI6 extends OtherI {

    }

    protected interface Base1 {

    }

    protected interface Base2 {

    }

    protected interface ImplOrder1 extends Base1, Base2 {

    }

    protected interface ImplOrder2 extends Base2, Base1 {

    }

    /* Example of a deep interface hierarchy. */

    protected interface I45 {

    }

    protected interface I46 {

    }

    protected interface I41 extends I45 {

    }

    protected interface I42 extends I45 {

    }

    protected interface I43 extends I46 {

    }

    protected interface I44 extends I46 {

    }

    protected interface I35 extends I41, I42 {

    }

    protected interface I36 extends I43, I44 {

    }

    protected interface I31 extends I35 {

    }

    protected interface I32 extends I35 {

    }

    protected interface I33 extends I36 {

    }

    protected interface I34 extends I36 {

    }

    protected interface I25 extends I31, I32 {

    }

    protected interface I26 extends I33, I34 {

    }

    protected interface I21 extends I25 {

    }

    protected interface I22 extends I25 {

    }

    protected interface I23 extends I26 {

    }

    protected interface I24 extends I26 {

    }

    protected interface I15 extends I21, I22 {

    }

    protected interface I16 extends I23, I24 {

    }

    protected interface I11 extends I15 {

    }

    protected interface I12 extends I15 {

    }

    protected interface I13 extends I16 {

    }

    protected interface I14 extends I16 {

    }

    protected interface I5 extends I11, I12 {

    }

    protected interface I6 extends I13, I14 {

    }

    protected interface I1 extends I5 {

    }

    protected interface I2 extends I5 {

    }

    protected interface I3 extends I6 {

    }

    protected interface I4 extends I6 {

    }

    protected interface Deep1 extends I1, I2 {

    }

    protected interface Deep2 extends I3, I4 {

    }

    /**
     * Joins the two stamps and also asserts that the meet operation is commutative.
     */
    protected static Stamp join(Stamp a, Stamp b) {
        Stamp ab = a.join(b);
        Stamp ba = b.join(a);
        Assert.assertEquals(ab, ba);
        return ab;
    }

    /**
     * Meets the two stamps and also asserts that the meet operation is commutative.
     */
    protected static Stamp meet(Stamp a, Stamp b) {
        Stamp ab = a.meet(b);
        Stamp ba = b.meet(a);
        Assert.assertEquals(ab, ba);
        return ab;
    }

    protected TypeReference getType(Class<?> clazz) {
        return TypeReference.createTrustedWithoutAssumptions(getMetaAccess().lookupJavaType(clazz));
    }
}

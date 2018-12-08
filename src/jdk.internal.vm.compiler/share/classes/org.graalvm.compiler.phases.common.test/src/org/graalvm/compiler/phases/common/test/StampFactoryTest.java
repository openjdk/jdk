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


package org.graalvm.compiler.phases.common.test;

import java.lang.reflect.Method;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.runtime.RuntimeProvider;

public class StampFactoryTest {

    @SuppressWarnings("unused")
    public void test(int a, Object b, double c) {
    }

    @Test
    public void testParameters() throws NoSuchMethodException, SecurityException {
        Method method = StampFactoryTest.class.getMethod("test", Integer.TYPE, Object.class, Double.TYPE);
        MetaAccessProvider metaAccess = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders().getMetaAccess();
        Stamp[] parameterStamps = StampFactory.createParameterStamps(null, metaAccess.lookupJavaMethod(method));
        Stamp[] expected = {StampFactory.objectNonNull(TypeReference.createWithoutAssumptions(metaAccess.lookupJavaType(StampFactoryTest.class))), StampFactory.forKind(JavaKind.Int),
                        StampFactory.object(TypeReference.createWithoutAssumptions(metaAccess.lookupJavaType(Object.class))), StampFactory.forKind(JavaKind.Double)};
        Assert.assertArrayEquals(expected, parameterStamps);
    }
}

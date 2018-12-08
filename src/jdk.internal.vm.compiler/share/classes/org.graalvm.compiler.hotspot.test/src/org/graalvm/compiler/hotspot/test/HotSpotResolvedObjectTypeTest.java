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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Tests {@link HotSpotResolvedJavaMethod} functionality.
 */
public class HotSpotResolvedObjectTypeTest extends HotSpotGraalCompilerTest {

    @Test
    public void testGetSourceFileName() throws Throwable {
        Assert.assertEquals("Object.java", getMetaAccess().lookupJavaType(Object.class).getSourceFileName());
        Assert.assertEquals("HotSpotResolvedObjectTypeTest.java", getMetaAccess().lookupJavaType(this.getClass()).getSourceFileName());
    }

    @Test
    public void testKlassLayoutHelper() {
        Constant klass = ((HotSpotResolvedObjectType) getMetaAccess().lookupJavaType(this.getClass())).klass();
        MemoryAccessProvider memoryAccess = getProviders().getConstantReflection().getMemoryAccessProvider();
        GraalHotSpotVMConfig config = runtime().getVMConfig();
        Constant c = StampFactory.forKind(JavaKind.Int).readConstant(memoryAccess, klass, config.klassLayoutHelperOffset);
        assertTrue(c.toString(), c.getClass() == PrimitiveConstant.class);
        PrimitiveConstant pc = (PrimitiveConstant) c;
        assertTrue(pc.toString(), pc.getJavaKind() == JavaKind.Int);
    }
}

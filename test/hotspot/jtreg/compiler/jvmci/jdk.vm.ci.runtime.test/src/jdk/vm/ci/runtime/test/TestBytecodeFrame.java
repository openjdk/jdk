/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *          jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          java.base/jdk.internal.misc
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler jdk.vm.ci.runtime.test.TestBytecodeFrame
 */

package jdk.vm.ci.runtime.test;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.junit.Test;

import java.util.Map;
import java.util.Iterator;

import org.junit.Assert;

public class TestBytecodeFrame extends MethodUniverse {

    private static void assertEquals(BytecodeFrame f1, BytecodeFrame f2) {
        Assert.assertEquals(f1, f2);
        Assert.assertEquals(f1.hashCode(), f2.hashCode());
    }

    private static void assertNotEquals(BytecodeFrame f1, BytecodeFrame f2) {
        Assert.assertNotEquals(f1, f2);
        Assert.assertNotEquals(f1.hashCode(), f2.hashCode());
    }

    /**
     * Tests the {@link BytecodeFrame#equals} and {@link BytecodeFrame#hashCode}.
     */
    @Test
    public void equalsAndHashcodeTest() {
        Iterator<ResolvedJavaMethod> iter = methods.values().iterator();
        ResolvedJavaMethod m1 = iter.next();
        ResolvedJavaMethod m2 = iter.next();
        ResolvedJavaMethod m3 = iter.next();

        JavaValue[] values = {
            JavaConstant.INT_0,
            JavaConstant.INT_1,
            JavaConstant.INT_2,
            JavaConstant.NULL_POINTER,
        };
        JavaKind[] slotKinds = {
            JavaKind.Int,
            JavaKind.Int,
            JavaKind.Int,
            JavaKind.Object,
        };
        JavaValue[] values2 = {
            JavaConstant.INT_1,
            JavaConstant.INT_2,
            JavaConstant.NULL_POINTER,
            JavaConstant.INT_0,
        };
        JavaKind[] slotKinds2 = {
            JavaKind.Int,
            JavaKind.Int,
            JavaKind.Object,
            JavaKind.Int,
        };

        // The BytecodeFrame objects below will not all pass BytecodeFrame.verifyInvariants
        // but that's fine for simply testing equals and hashCode.
        BytecodeFrame caller = new BytecodeFrame(null, m3, 0, false, true,  values,  slotKinds, 1, 1, 0);
        BytecodeFrame f1 =  new BytecodeFrame(caller, m1, 0, false,  true,  values,  slotKinds,  1, 1, 0);
                                                                                                           // Differing field
        assertNotEquals(f1, new BytecodeFrame(caller, m2, 0, false, true,  values,  slotKinds,  1, 1, 0)); // method
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 1, false, true,  values,  slotKinds,  1, 1, 0)); // bci
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 0, true,  true,  values,  slotKinds,  1, 1, 0)); // rethrowException
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 0, false, false, values,  slotKinds,  1, 1, 0)); // duringCall
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 0, false, true,  values2, slotKinds,  1, 1, 0)); // values
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 0, false, true,  values,  slotKinds2, 1, 1, 0)); // slotKinds
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 0, false, false, values,  slotKinds,  2, 1, 0)); // numLocals
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 0, false, false, values,  slotKinds,  1, 2, 0)); // numStack
        assertNotEquals(f1, new BytecodeFrame(caller, m1, 0, false, false, values,  slotKinds,  1, 1, 1)); // numLocks
        assertEquals(f1, f1);

        BytecodeFrame f2 = new BytecodeFrame(caller, m1, 0, false,  true,  values,  slotKinds, 1, 1, 0);
        assertEquals(f1, f2);
    }
}

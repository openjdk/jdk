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
package org.graalvm.compiler.hotspot.test;

import org.junit.Test;

import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that deoptimization upon exception handling works.
 */
public class ForeignCallDeoptimizeTest extends GraalCompilerTest {

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        ForeignCallsProvider foreignCalls = ((HotSpotProviders) getProviders()).getForeignCalls();

        Plugins ret = super.getDefaultGraphBuilderPlugins();
        ret.getInvocationPlugins().register(new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ForeignCallNode node = new ForeignCallNode(foreignCalls, HotSpotForeignCallsProviderImpl.TEST_DEOPTIMIZE_CALL_INT, arg);
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, ForeignCallDeoptimizeTest.class, "testCallInt", int.class);
        return ret;
    }

    public static int testCallInt(int value) {
        return value;
    }

    public static int testForeignCall(int value) {
        if (testCallInt(value) != value) {
            throw new InternalError();
        }
        return value;
    }

    @Test
    public void test1() {
        test("testForeignCall", 0);
    }

    @Test
    public void test2() {
        test("testForeignCall", -1);
    }
}

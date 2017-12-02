/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.spi.UncheckedInterfaceProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class UncheckedInterfaceProviderTest extends GraalCompilerTest {
    private Runnable interfaceField;
    private Runnable[] interfaceArrayField;

    public void snippet(Runnable interfaceParameter, Runnable[] interfaceArrayParameter) {
        GraalDirectives.blackhole(interfaceParameter);
        GraalDirectives.blackhole(interfaceArrayParameter);
        GraalDirectives.blackhole(interfaceField);
        GraalDirectives.blackhole(interfaceArrayField);
        GraalDirectives.blackhole(interfaceReturn());
        GraalDirectives.blackhole(interfaceArrayReturn());
        GraalDirectives.blackhole(interfaceReturnException());
        GraalDirectives.blackhole(interfaceArrayReturnException());
    }

    public static Runnable interfaceReturn() {
        return new A();
    }

    public static Runnable[] interfaceArrayReturn() {
        return new Runnable[]{new A(), new B(), new C(), new D()};
    }

    public static Runnable interfaceReturnException() {
        return new A();
    }

    public static Runnable[] interfaceArrayReturnException() {
        return new Runnable[]{new A(), new B(), new C(), new D()};
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getName().startsWith("interfaceReturn") || method.getName().startsWith("interfaceArrayReturn")) {
            if (method.getName().equals("Exception")) {
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
            return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }

    @BeforeClass
    public static void setup() {
        interfaceArrayReturn();
    }

    @Test
    public void test() {
        StructuredGraph graph = parseEager("snippet", StructuredGraph.AllowAssumptions.YES);
        for (BlackholeNode b : graph.getNodes().filter(BlackholeNode.class)) {
            Assert.assertThat(b.getValue(), is(instanceOf(UncheckedInterfaceProvider.class)));
            Stamp uncheckedStamp = ((UncheckedInterfaceProvider) b.getValue()).uncheckedStamp();
            String context = b.getValue().toString(Verbosity.Debugger);
            Assert.assertNotNull(context, uncheckedStamp);
            ResolvedJavaType uncheckedType = StampTool.typeOrNull(uncheckedStamp);
            ResolvedJavaType type = StampTool.typeOrNull(b.getValue());
            Assert.assertEquals(context, arrayDepth(type), arrayDepth(uncheckedType));
            Assert.assertTrue(context + ": " + type, type == null || type.getElementalType().isJavaLangObject());
            Assert.assertNotNull(context, uncheckedType);
            Assert.assertTrue(context, uncheckedType.getElementalType().isInterface());
        }
    }

    private static int arrayDepth(ResolvedJavaType type) {
        int depth = 0;
        ResolvedJavaType t = type;
        while (t != null && t.isArray()) {
            depth += 1;
            t = t.getComponentType();
        }
        return depth;
    }

    public static class A implements Runnable {
        @Override
        public void run() {
            // nop
        }
    }

    public static class B implements Runnable {
        @Override
        public void run() {
            // nop
        }
    }

    public static class C implements Runnable {
        @Override
        public void run() {
            // nop
        }
    }

    public static class D implements Runnable {
        @Override
        public void run() {
            // nop
        }
    }
}

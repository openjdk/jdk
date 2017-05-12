/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory.InjectionProvider;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.junit.Test;

public class FoldTest extends ReplacementsTest {

    private static class TestMethod {

        public static int test() {
            return 42;
        }
    }

    static class FoldUtils {

        private int number;

        FoldUtils(int number) {
            this.number = number;
        }

        @Fold
        static int multiply(int a, int b) {
            // we want to test whether @Fold works, so prevent automatic constant folding
            return a * GraalDirectives.opaque(b);
        }

        @Fold
        int getNumber() {
            // we want to test whether @Fold works, so prevent automatic constant folding
            return GraalDirectives.opaque(number);
        }
    }

    @ClassSubstitution(TestMethod.class)
    private static class TestMethodSubstitution {

        private static final FoldUtils utils = new FoldUtils(21);

        @MethodSubstitution
        public static int test() {
            return FoldUtils.multiply(utils.getNumber(), 2);
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        InjectionProvider injection = new NodeIntrinsificationProvider(getMetaAccess(), getSnippetReflection(), getProviders().getForeignCalls(), null);
        new PluginFactory_FoldTest().registerPlugins(invocationPlugins, injection);
        BytecodeProvider replacementBytecodeProvider = getSystemClassLoaderBytecodeProvider();
        Registration r = new Registration(invocationPlugins, TestMethod.class, replacementBytecodeProvider);
        r.registerMethodSubstitution(TestMethodSubstitution.class, "test");
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static int callTest() {
        return TestMethod.test();
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        // check that folding happened correctly
        StartNode start = graph.start();
        assert start.next() instanceof ReturnNode : "expected ReturnNode, got " + start.next();

        ReturnNode ret = (ReturnNode) start.next();
        assert ret.result().isConstant() : "expected ConstantNode, got " + ret.result();
        return true;
    }

    @Test
    public void snippetTest() {
        test("callTest");
    }
}

/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class StringIndexOfCharConstantBindingTest extends StringIndexOfCharTest {

    @Before
    public void checkAMD64() {
        Architecture arch = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget().arch;
        assumeTrue("skipping AMD64 specific test", arch instanceof AMD64);
    }

    Object[] constantArgs;

    public StringIndexOfCharConstantBindingTest(String sourceString, int constantChar, int fromIndex) {
        super(sourceString, constantChar, fromIndex);
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        if (constantArgs != null) {
            ConstantBindingParameterPlugin constantBinding = new ConstantBindingParameterPlugin(constantArgs, this.getMetaAccess(), this.getSnippetReflection());
            conf.getPlugins().appendParameterPlugin(constantBinding);
        }
        return super.editGraphBuilderConfiguration(conf);
    }

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph0, boolean ignoreForceCompile, boolean ignoreInstallAsDefault, OptionValues options) {
        // Force recompile if constant binding should be done
        return super.getCode(installedCodeOwner, graph0,
                        /* forceCompile */ true, /* installAsDefault */ false, options);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        if (this.sourceString.length() < GraalOptions.StringIndexOfLimit.getValue(graph.getOptions()) && this.constantChar < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            StartNode start = graph.start();
            FixedNode next = start.next();
            assertTrue(next instanceof ReturnNode);
            ReturnNode returnNode = (ReturnNode) next;
            assertTrue(returnNode.result().isConstant());
        }
    }

    @Test
    @Override
    public void testStringIndexOfConstant() {
        constantArgs = new Object[3];
        constantArgs[1] = this.sourceString;
        constantArgs[2] = this.constantChar;
        test("testStringIndexOf", this.sourceString, this.constantChar);
    }

    @Test
    @Override
    public void testStringIndexOfConstantOffset() {
        constantArgs = new Object[4];
        constantArgs[1] = this.sourceString;
        constantArgs[2] = this.constantChar;
        constantArgs[3] = this.fromIndex;
        test("testStringIndexOfOffset", this.sourceString, this.constantChar, this.fromIndex);
    }
}

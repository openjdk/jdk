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

package org.graalvm.compiler.hotspot.aarch64;

import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.nodes.calc.FixedBinaryNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.aarch64.AArch64FloatArithmeticSnippets;
import org.graalvm.compiler.replacements.aarch64.AArch64IntegerArithmeticSnippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class AArch64HotSpotLoweringProvider extends DefaultHotSpotLoweringProvider {

    private AArch64IntegerArithmeticSnippets integerArithmeticSnippets;
    private AArch64FloatArithmeticSnippets floatArithmeticSnippets;

    public AArch64HotSpotLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    HotSpotConstantReflectionProvider constantReflection, TargetDescription target) {
        super(runtime, metaAccess, foreignCalls, registers, constantReflection, target);
    }

    @Override
    public void initialize(HotSpotProviders providers, GraalHotSpotVMConfig config) {
        integerArithmeticSnippets = new AArch64IntegerArithmeticSnippets(providers, providers.getSnippetReflection(), providers.getCodeCache().getTarget());
        floatArithmeticSnippets = new AArch64FloatArithmeticSnippets(providers, providers.getSnippetReflection(), providers.getCodeCache().getTarget());
        super.initialize(providers, config);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof FixedBinaryNode) {
            integerArithmeticSnippets.lower((FixedBinaryNode) n, tool);
        } else if (n instanceof RemNode) {
            floatArithmeticSnippets.lower((RemNode) n, tool);
        } else if (n instanceof FloatConvertNode) {
            // AMD64 has custom lowerings for ConvertNodes, HotSpotLoweringProvider does not expect
            // to see a ConvertNode and throws an error, just do nothing here.
        } else {
            super.lower(n, tool);
        }
    }
}

/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode.CheckAll;

import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.meta.HotSpotNodePlugin;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.Suites;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This test exercises the FrameState used for deoptimization in the JVMTI post_on_exceptions path.
 */
public class HotSpotDeoptPostExceptions extends GraalCompilerTest {

    @Override
    @SuppressWarnings("try")
    protected Suites createSuites(OptionValues options) {
        return super.createSuites(new OptionValues(options, HighTier.Options.Inline, false));
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf).withBytecodeExceptionMode(CheckAll);
    }

    static String snippet(Object o) {
        return o.toString();
    }

    @Test
    public void testPost() {
        OptionValues options = new OptionValues(getInitialOptions(), HotSpotNodePlugin.Options.HotSpotPostOnExceptions, true);
        test(options, "snippet", (Object) null);
    }
}

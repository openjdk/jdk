/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import org.graalvm.compiler.hotspot.nodes.profiling.ProfileBranchNode;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileInvokeNode;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.ProfilingPlugin;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class HotSpotProfilingPlugin implements ProfilingPlugin {
    public static class Options {
        @Option(help = "Emit profiling of invokes", type = OptionType.Expert)//
        public static final OptionKey<Boolean> ProfileInvokes = new OptionKey<>(true);
        @Option(help = "Emit profiling of backedges", type = OptionType.Expert)//
        public static final OptionKey<Boolean> ProfileBackedges = new OptionKey<>(true);
    }

    public abstract int invokeNotifyFreqLog(OptionValues options);

    public abstract int invokeInlineeNotifyFreqLog(OptionValues options);

    public abstract int invokeProfilePobabilityLog(OptionValues options);

    public abstract int backedgeNotifyFreqLog(OptionValues options);

    public abstract int backedgeProfilePobabilityLog(OptionValues options);

    @Override
    public boolean shouldProfile(GraphBuilderContext builder, ResolvedJavaMethod method) {
        return !builder.parsingIntrinsic();
    }

    @Override
    public void profileInvoke(GraphBuilderContext builder, ResolvedJavaMethod method, FrameState frameState) {
        assert shouldProfile(builder, method);
        OptionValues options = builder.getOptions();
        if (Options.ProfileInvokes.getValue(options) && !method.isClassInitializer()) {
            ProfileNode p = builder.append(new ProfileInvokeNode(method, invokeNotifyFreqLog(options), invokeProfilePobabilityLog(options)));
            p.setStateBefore(frameState);
        }
    }

    @Override
    public void profileGoto(GraphBuilderContext builder, ResolvedJavaMethod method, int bci, int targetBci, FrameState frameState) {
        assert shouldProfile(builder, method);
        OptionValues options = builder.getOptions();
        if (Options.ProfileBackedges.getValue(options) && targetBci <= bci) {
            ProfileNode p = builder.append(new ProfileBranchNode(method, backedgeNotifyFreqLog(options), backedgeProfilePobabilityLog(options), bci, targetBci));
            p.setStateBefore(frameState);
        }
    }

    @Override
    public void profileIf(GraphBuilderContext builder, ResolvedJavaMethod method, int bci, LogicNode condition, int trueBranchBci, int falseBranchBci, FrameState frameState) {
        assert shouldProfile(builder, method);
        OptionValues options = builder.getOptions();
        if (Options.ProfileBackedges.getValue(options) && (falseBranchBci <= bci || trueBranchBci <= bci)) {
            boolean negate = false;
            int targetBci = trueBranchBci;
            if (falseBranchBci <= bci) {
                assert trueBranchBci > bci;
                negate = true;
                targetBci = falseBranchBci;
            } else {
                assert trueBranchBci <= bci && falseBranchBci > bci;
            }
            ValueNode trueValue = builder.append(ConstantNode.forBoolean(!negate));
            ValueNode falseValue = builder.append(ConstantNode.forBoolean(negate));
            ConditionalNode branchCondition = builder.append(new ConditionalNode(condition, trueValue, falseValue));
            ProfileNode p = builder.append(new ProfileBranchNode(method, backedgeNotifyFreqLog(options), backedgeProfilePobabilityLog(options), branchCondition, bci, targetBci));
            p.setStateBefore(frameState);
        }
    }
}

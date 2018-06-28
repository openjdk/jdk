/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotAOTProfilingPlugin extends HotSpotProfilingPlugin {
    public static class Options {
        @Option(help = "Do profiling and callbacks to tiered runtime", type = OptionType.User)//
        public static final OptionKey<Boolean> TieredAOT = new OptionKey<>(false);
        @Option(help = "Invocation notification frequency", type = OptionType.Expert)//
        public static final OptionKey<Integer> TierAInvokeNotifyFreqLog = new OptionKey<>(13);
        @Option(help = "Inlinee invocation notification frequency (-1 means count, but do not notify)", type = OptionType.Expert)//
        public static final OptionKey<Integer> TierAInvokeInlineeNotifyFreqLog = new OptionKey<>(-1);
        @Option(help = "Invocation profile probability", type = OptionType.Expert)//
        public static final OptionKey<Integer> TierAInvokeProfileProbabilityLog = new OptionKey<>(8);
        @Option(help = "Backedge notification frequency", type = OptionType.Expert)//
        public static final OptionKey<Integer> TierABackedgeNotifyFreqLog = new OptionKey<>(16);
        @Option(help = "Backedge profile probability", type = OptionType.Expert)//
        public static final OptionKey<Integer> TierABackedgeProfileProbabilityLog = new OptionKey<>(12);
    }

    @Override
    public boolean shouldProfile(GraphBuilderContext builder, ResolvedJavaMethod method) {
        return super.shouldProfile(builder, method) && ((HotSpotResolvedObjectType) method.getDeclaringClass()).getFingerprint() != 0;
    }

    @Override
    public int invokeNotifyFreqLog(OptionValues options) {
        return Options.TierAInvokeNotifyFreqLog.getValue(options);
    }

    @Override
    public int invokeInlineeNotifyFreqLog(OptionValues options) {
        return Options.TierAInvokeInlineeNotifyFreqLog.getValue(options);
    }

    @Override
    public int invokeProfilePobabilityLog(OptionValues options) {
        return Options.TierAInvokeProfileProbabilityLog.getValue(options);
    }

    @Override
    public int backedgeNotifyFreqLog(OptionValues options) {
        return Options.TierABackedgeNotifyFreqLog.getValue(options);
    }

    @Override
    public int backedgeProfilePobabilityLog(OptionValues options) {
        return Options.TierABackedgeProfileProbabilityLog.getValue(options);
    }
}

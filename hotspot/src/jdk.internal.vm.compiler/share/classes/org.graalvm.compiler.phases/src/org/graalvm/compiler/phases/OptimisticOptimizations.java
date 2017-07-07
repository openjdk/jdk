/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases;

import java.util.EnumSet;
import java.util.Set;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ProfilingInfo;

public final class OptimisticOptimizations {

    public static final OptimisticOptimizations ALL = new OptimisticOptimizations(EnumSet.allOf(Optimization.class));
    public static final OptimisticOptimizations NONE = new OptimisticOptimizations(EnumSet.noneOf(Optimization.class));

    public enum Optimization {
        RemoveNeverExecutedCode,
        UseTypeCheckedInlining,
        UseTypeCheckHints,
        UseExceptionProbabilityForOperations,
        UseExceptionProbability,
        UseLoopLimitChecks
    }

    private final Set<Optimization> enabledOpts;

    public OptimisticOptimizations(ProfilingInfo info, OptionValues options) {
        this.enabledOpts = EnumSet.noneOf(Optimization.class);

        enabledOpts.add(Optimization.UseExceptionProbabilityForOperations);
        addOptimization(options, info, DeoptimizationReason.UnreachedCode, Optimization.RemoveNeverExecutedCode);
        addOptimization(options, info, DeoptimizationReason.TypeCheckedInliningViolated, Optimization.UseTypeCheckedInlining);
        addOptimization(options, info, DeoptimizationReason.OptimizedTypeCheckViolated, Optimization.UseTypeCheckHints);
        addOptimization(options, info, DeoptimizationReason.NotCompiledExceptionHandler, Optimization.UseExceptionProbability);
        addOptimization(options, info, DeoptimizationReason.LoopLimitCheck, Optimization.UseLoopLimitChecks);
    }

    private void addOptimization(OptionValues options, ProfilingInfo info, DeoptimizationReason deoptReason, Optimization optimization) {
        if (checkDeoptimizations(options, info, deoptReason)) {
            enabledOpts.add(optimization);
        }
    }

    public OptimisticOptimizations remove(Optimization... optimizations) {
        Set<Optimization> newOptimizations = EnumSet.copyOf(enabledOpts);
        for (Optimization o : optimizations) {
            newOptimizations.remove(o);
        }
        return new OptimisticOptimizations(newOptimizations);
    }

    public OptimisticOptimizations add(Optimization... optimizations) {
        Set<Optimization> newOptimizations = EnumSet.copyOf(enabledOpts);
        for (Optimization o : optimizations) {
            newOptimizations.add(o);
        }
        return new OptimisticOptimizations(newOptimizations);
    }

    private OptimisticOptimizations(Set<Optimization> enabledOpts) {
        this.enabledOpts = enabledOpts;
    }

    public boolean removeNeverExecutedCode(OptionValues options) {
        return GraalOptions.RemoveNeverExecutedCode.getValue(options) && enabledOpts.contains(Optimization.RemoveNeverExecutedCode);
    }

    public boolean useTypeCheckHints(OptionValues options) {
        return GraalOptions.UseTypeCheckHints.getValue(options) && enabledOpts.contains(Optimization.UseTypeCheckHints);
    }

    public boolean inlineMonomorphicCalls(OptionValues options) {
        return GraalOptions.InlineMonomorphicCalls.getValue(options) && enabledOpts.contains(Optimization.UseTypeCheckedInlining);
    }

    public boolean inlinePolymorphicCalls(OptionValues options) {
        return GraalOptions.InlinePolymorphicCalls.getValue(options) && enabledOpts.contains(Optimization.UseTypeCheckedInlining);
    }

    public boolean inlineMegamorphicCalls(OptionValues options) {
        return GraalOptions.InlineMegamorphicCalls.getValue(options) && enabledOpts.contains(Optimization.UseTypeCheckedInlining);
    }

    public boolean devirtualizeInvokes(OptionValues options) {
        return GraalOptions.OptDevirtualizeInvokesOptimistically.getValue(options) && enabledOpts.contains(Optimization.UseTypeCheckedInlining);
    }

    public boolean useExceptionProbability(OptionValues options) {
        return GraalOptions.UseExceptionProbability.getValue(options) && enabledOpts.contains(Optimization.UseExceptionProbability);
    }

    public boolean useExceptionProbabilityForOperations() {
        return enabledOpts.contains(Optimization.UseExceptionProbabilityForOperations);
    }

    public boolean useLoopLimitChecks(OptionValues options) {
        return GraalOptions.UseLoopLimitChecks.getValue(options) && enabledOpts.contains(Optimization.UseLoopLimitChecks);
    }

    public boolean lessOptimisticThan(OptimisticOptimizations other) {
        for (Optimization opt : Optimization.values()) {
            if (!enabledOpts.contains(opt) && other.enabledOpts.contains(opt)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkDeoptimizations(OptionValues options, ProfilingInfo profilingInfo, DeoptimizationReason reason) {
        return profilingInfo.getDeoptimizationCount(reason) < GraalOptions.DeoptsToDisableOptimisticOptimization.getValue(options);
    }

    @Override
    public String toString() {
        return enabledOpts.toString();
    }
}

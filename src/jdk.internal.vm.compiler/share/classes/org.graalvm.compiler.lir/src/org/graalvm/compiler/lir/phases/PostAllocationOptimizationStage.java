/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.phases;

import static org.graalvm.compiler.lir.phases.LIRPhase.Options.LIROptimization;

import org.graalvm.compiler.lir.ControlFlowOptimizer;
import org.graalvm.compiler.lir.EdgeMoveOptimizer;
import org.graalvm.compiler.lir.NullCheckOptimizer;
import org.graalvm.compiler.lir.RedundantMoveElimination;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import org.graalvm.compiler.lir.profiling.MethodProfilingPhase;
import org.graalvm.compiler.lir.profiling.MoveProfilingPhase;
import org.graalvm.compiler.options.NestedBooleanOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

public class PostAllocationOptimizationStage extends LIRPhaseSuite<PostAllocationOptimizationContext> {
    public static class Options {
        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptEdgeMoveOptimizer = new NestedBooleanOptionKey(LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptControlFlowOptimizer = new NestedBooleanOptionKey(LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptRedundantMoveElimination = new NestedBooleanOptionKey(LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptNullCheckOptimizer = new NestedBooleanOptionKey(LIROptimization, true);
        @Option(help = "Enables profiling of move types on LIR level. " +
                       "Move types are for example stores (register to stack), " +
                       "constant loads (constant to register) or copies (register to register).", type = OptionType.Debug)
        public static final OptionKey<Boolean> LIRProfileMoves = new OptionKey<>(false);
        @Option(help = "Enables profiling of methods.", type = OptionType.Debug)
        public static final OptionKey<Boolean> LIRProfileMethods = new OptionKey<>(false);
        // @formatter:on
    }

    public PostAllocationOptimizationStage(OptionValues options) {
        if (Options.LIROptEdgeMoveOptimizer.getValue(options)) {
            appendPhase(new EdgeMoveOptimizer());
        }
        if (Options.LIROptControlFlowOptimizer.getValue(options)) {
            appendPhase(new ControlFlowOptimizer());
        }
        if (Options.LIROptRedundantMoveElimination.getValue(options)) {
            appendPhase(new RedundantMoveElimination());
        }
        if (Options.LIROptNullCheckOptimizer.getValue(options)) {
            appendPhase(new NullCheckOptimizer());
        }
        if (Options.LIRProfileMoves.getValue(options)) {
            appendPhase(new MoveProfilingPhase());
        }
        if (Options.LIRProfileMethods.getValue(options)) {
            appendPhase(new MethodProfilingPhase());
        }
    }
}

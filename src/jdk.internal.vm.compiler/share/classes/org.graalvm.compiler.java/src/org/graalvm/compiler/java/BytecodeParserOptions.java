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


package org.graalvm.compiler.java;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionKey;

/**
 * Options related to {@link BytecodeParser}.
 *
 * Note: This must be a top level class to work around for
 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=477597">Eclipse bug 477597</a>.
 */
public class BytecodeParserOptions {
    // @formatter:off
    @Option(help = "The trace level for the bytecode parser. A value of 1 enables instruction tracing " +
                   "and any greater value emits a frame state trace just prior to each instruction trace." +
                   "Instruction tracing output from multiple compiler threads will be interleaved so " +
                   "use of this option make most sense for single threaded compilation. " +
                   "The MethodFilter option can be used to refine tracing to selected methods.", type = OptionType.Debug)
    public static final OptionKey<Integer> TraceBytecodeParserLevel = new OptionKey<>(0);

    @Option(help = "Inlines trivial methods during bytecode parsing.", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineDuringParsing = new OptionKey<>(true);

    @Option(help = "Inlines partial intrinsic exits during bytecode parsing when possible. " +
                   "A partial intrinsic exit is a call within an intrinsic to the method " +
                   "being intrinsified and denotes semantics of the original method that " +
                   "the intrinsic does not support.", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlinePartialIntrinsicExitDuringParsing = new OptionKey<>(true);

    @Option(help = "Inlines intrinsic methods during bytecode parsing.", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineIntrinsicsDuringParsing = new OptionKey<>(true);

    @Option(help = "Traces inlining performed during bytecode parsing.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceInlineDuringParsing = new OptionKey<>(false);

    @Option(help = "Traces use of plugins during bytecode parsing.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceParserPlugins = new OptionKey<>(false);

    @Option(help = "Maximum depth when inlining during bytecode parsing.", type = OptionType.Debug)
    public static final OptionKey<Integer> InlineDuringParsingMaxDepth = new OptionKey<>(10);

    @Option(help = "When creating info points hide the methods of the substitutions.", type = OptionType.Debug)
    public static final OptionKey<Boolean> HideSubstitutionStates = new OptionKey<>(false);

    @Option(help = "Use intrinsics guarded by a virtual dispatch test at indirect call sites.", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseGuardedIntrinsics = new OptionKey<>(true);
    // @formatter:on
}

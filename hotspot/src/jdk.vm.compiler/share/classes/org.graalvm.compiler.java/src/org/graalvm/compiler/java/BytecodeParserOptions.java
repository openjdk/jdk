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
package org.graalvm.compiler.java;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.StableOptionValue;

/**
 * Options related to {@link BytecodeParser}.
 *
 * Note: This must be a top level class to work around for
 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=477597">Eclipse bug 477597</a>.
 */
public class BytecodeParserOptions {
    // @formatter:off
    @Option(help = "The trace level for the bytecode parser used when building a graph from bytecode", type = OptionType.Debug)
    public static final OptionValue<Integer> TraceBytecodeParserLevel = new OptionValue<>(0);

    @Option(help = "Inlines trivial methods during bytecode parsing.", type = OptionType.Expert)
    public static final StableOptionValue<Boolean> InlineDuringParsing = new StableOptionValue<>(true);

    @Option(help = "Inlines intrinsic methods during bytecode parsing.", type = OptionType.Expert)
    public static final StableOptionValue<Boolean> InlineIntrinsicsDuringParsing = new StableOptionValue<>(true);

    @Option(help = "Traces inlining performed during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionValue<Boolean> TraceInlineDuringParsing = new StableOptionValue<>(false);

    @Option(help = "Traces use of plugins during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionValue<Boolean> TraceParserPlugins = new StableOptionValue<>(false);

    @Option(help = "Maximum depth when inlining during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionValue<Integer> InlineDuringParsingMaxDepth = new StableOptionValue<>(10);

    @Option(help = "Dump graphs after non-trivial changes during bytecode parsing.", type = OptionType.Debug)
    public static final StableOptionValue<Boolean> DumpDuringGraphBuilding = new StableOptionValue<>(false);

    @Option(help = "When creating info points hide the methods of the substitutions.", type = OptionType.Debug)
    public static final OptionValue<Boolean> HideSubstitutionStates = new OptionValue<>(false);

    @Option(help = "Use intrinsics guarded by a virtual dispatch test at indirect call sites.", type = OptionType.Debug)
    public static final OptionValue<Boolean> UseGuardedIntrinsics = new OptionValue<>(true);
    // @formatter:on
}

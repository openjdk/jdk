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


package org.graalvm.compiler.core;

import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

/**
 * Options related to {@link GraalCompiler}.
 */
public class GraalCompilerOptions {

    // @formatter:off
    @Option(help = "Print an informational line to the console for each completed compilation.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintCompilation = new OptionKey<>(false);
    @Option(help = "Pattern for method(s) that will trigger an exception when compiled. " +
                   "This option exists to test handling compilation crashes gracefully. " +
                   "See the MethodFilter option for the pattern syntax. A ':Bailout' " +
                   "suffix will raise a bailout exception and a ':PermanentBailout' " +
                   "suffix will raise a permanent bailout exception.", type = OptionType.Debug)
    public static final OptionKey<String> CrashAt = new OptionKey<>(null);
    @Option(help = "Treat compilation bailouts like compilation failures.", type = OptionType.User)
    public static final OptionKey<Boolean> CompilationBailoutAsFailure = new OptionKey<>(false);
    @Option(help = "file:doc-files/CompilationFailureActionHelp.txt", type = OptionType.User)
    public static final EnumOptionKey<ExceptionAction> CompilationFailureAction = new EnumOptionKey<>(ExceptionAction.Silent);
    @Option(help = "The maximum number of compilation failures to handle with the action specified " +
                   "by CompilationFailureAction before changing to a less verbose action. " +
                   "This does not apply to the ExitVM action.", type = OptionType.User)
    public static final OptionKey<Integer> MaxCompilationProblemsPerAction = new OptionKey<>(2);
    @Option(help = "Alias for CompilationFailureAction=ExitVM.", type = OptionType.User)
    public static final OptionKey<Boolean> ExitVMOnException = new OptionKey<>(false);
    // @formatter:on
}

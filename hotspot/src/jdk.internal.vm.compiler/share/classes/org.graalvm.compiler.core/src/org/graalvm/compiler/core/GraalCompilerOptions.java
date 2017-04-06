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
package org.graalvm.compiler.core;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

/**
 * Options related to {@link GraalCompiler}.
 */
public class GraalCompilerOptions {

    // @formatter:off
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<String> PrintFilter = new OptionKey<>(null);
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintCompilation = new OptionKey<>(false);
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintAfterCompilation = new OptionKey<>(false);
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintBailout = new OptionKey<>(false);
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ExitVMOnBailout = new OptionKey<>(false);
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ExitVMOnException = new OptionKey<>(false);
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintStackTraceOnException = new OptionKey<>(false);
    // @formatter:on

}

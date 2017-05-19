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
package org.graalvm.compiler.hotspot.replacements;

import org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.ProfileContext;
import org.graalvm.compiler.options.EnumOptionValue;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

/**
 * Options related to HotSpot snippets in this package.
 *
 * Note: This must be a top level class to work around for
 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=477597">Eclipse bug 477597</a>.
 */
public class HotspotSnippetsOptions {

    // @formatter:off
    @Option(help = "If the probability that a type check will hit one the profiled types (up to " +
                   "TypeCheckMaxHints) is below this value, the type check will be compiled without profiling info", type = OptionType.Expert)
    public static final OptionValue<Double> TypeCheckMinProfileHitProbability = new OptionValue<>(0.5);

    @Option(help = "The maximum number of profiled types that will be used when compiling a profiled type check. " +
                    "Note that TypeCheckMinProfileHitProbability also influences whether profiling info is used in compiled type checks.", type = OptionType.Expert)
    public static final OptionValue<Integer> TypeCheckMaxHints = new OptionValue<>(2);

    @Option(help = "Use a VM runtime call to load and clear the exception object from the thread at the start of a compiled exception handler.", type = OptionType.Debug)
    public static final OptionValue<Boolean> LoadExceptionObjectInVM = new OptionValue<>(false);

    @Option(help = "Enable profiling of allocation sites.", type = OptionType.Debug)
    public static final OptionValue<Boolean> ProfileAllocations = new OptionValue<>(false);

    @Option(help = "Control the naming of the counters when using ProfileAllocations.", type = OptionType.Debug)
    public static final EnumOptionValue<ProfileContext> ProfileAllocationsContext = new EnumOptionValue<>(ProfileContext.AllocatingMethod);

    @Option(help = "Enable profiling of monitor operations.", type = OptionType.Debug)
    public static final OptionValue<Boolean> ProfileMonitors = new OptionValue<>(false);

    @Option(help = "Trace monitor operations on objects whose type contains this substring.", type = OptionType.Debug)
    public static final OptionValue<String> TraceMonitorsTypeFilter = new OptionValue<>(null);

    @Option(help = "Trace monitor operations in methods whose fully qualified name contains this substring.", type = OptionType.Debug)
    public static final OptionValue<String> TraceMonitorsMethodFilter = new OptionValue<>(null);

    @Option(help = "Emit extra code to dynamically check monitor operations are balanced.", type = OptionType.Debug)
    public static final OptionValue<Boolean> VerifyBalancedMonitors = new OptionValue<>(false);
    //@formatter:on
}

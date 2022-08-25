/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.regexes;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;

import static compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexes.END;
import static compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexes.IS_REPLACED;

/**
 * This class provides default regex strings for matches on PrintOptoAssembly. These default regexes replace any usages
 * of placeholder strings from {@link IRNode} in check attributes {@link IR#failOn()} and {@link IR#counts()} if the
 * compile phase {@link CompilePhase#PRINT_OPTO_ASSEMBLY} or {@link CompilePhase#DEFAULT} is found in {@link IR#phase()}.
 * <p>
 *
 * Each new default regex for any node that needs to be matched on PrintOptoAssembly should be defined here together with
 * a mapping for {@link CompilePhase#PRINT_OPTO_ASSEMBLY} and {@link CompilePhase#DEFAULT} added as entry to
 * {@link DefaultRegexes#PLACEHOLDER_TO_REGEX_MAP}.
 * <p>
 *
 * The usage of these regexes for other compile phases is forbidden and will result in a format violation.
 * <p>
 *
 * There are two types of default regexes:
 * <ul>
 *     <li><p>Standalone regexes: Replace the placeholder string from {@link IRNode} directly.</li>
 *     <li><p>Composite regexes: The placeholder string from {@link IRNode} contain an additional "{@code P#}" prefix.
 *                               This placeholder strings expect another user provided string in the constraint list of
 *                               {@link IR#failOn()} and {@link IR#counts()}. They cannot be use as standalone regex.
 *                               Trying to do so will result in a format violation error.</li>
 * </ul>
 *
 * @see IR
 * @see IRNode
 * @see CompilePhase
 * @see DefaultRegexes
 */
public class OptoAssemblyDefaultRegexes {
    public static final String ALLOC = "(.*precise .*\\R((.*(?i:mov|xorl|nop|spill).*|\\s*|.*LGHI.*)\\R)*.*(?i:call,static).*wrapper for: _new_instance_Java" + END;
    public static final String ALLOC_OF = "(.*precise .*" + IS_REPLACED + ":.*\\R((.*(?i:mov|xorl|nop|spill).*|\\s*|.*LGHI.*)\\R)*.*(?i:call,static).*wrapper for: _new_instance_Java" + END;
    public static final String ALLOC_ARRAY = "(.*precise \\[.*\\R((.*(?i:mov|xor|nop|spill).*|\\s*|.*LGHI.*)\\R)*.*(?i:call,static).*wrapper for: _new_array_Java" + END;
    public static final String ALLOC_ARRAY_OF = "(.*precise \\[.*" + IS_REPLACED + ":.*\\R((.*(?i:mov|xorl|nop|spill).*|\\s*|.*LGHI.*)\\R)*.*(?i:call,static).*wrapper for: _new_array_Java" + END;
    public static final String CHECKCAST_ARRAY = "(((?i:cmp|CLFI|CLR).*precise \\[.*:|.*(?i:mov|or).*precise \\[.*:.*\\R.*(cmp|CMP|CLR))" + END;
    public static final String CHECKCAST_ARRAY_OF = "(((?i:cmp|CLFI|CLR).*precise \\[.*" + IS_REPLACED + ":|.*(?i:mov|or).*precise \\[.*" + IS_REPLACED + ":.*\\R.*(cmp|CMP|CLR))" + END;
    // Does not work on s390 (a rule containing this regex will be skipped on s390).
    public static final String CHECKCAST_ARRAYCOPY = "(.*((?i:call_leaf_nofp,runtime)|CALL,\\s?runtime leaf nofp|BCTRL.*.leaf call).*checkcast_arraycopy.*" + END;
    public static final String FIELD_ACCESS = "(.*Field: *" + END;
    public static final String SCOPE_OBJECT = "(.*# ScObj.*" + END;
}

/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework;

import compiler.lib.ir_framework.shared.TestFormatException;

import java.util.EnumMap;

import static compiler.lib.ir_framework.DefaultRegexes.*;

/**
 * This class provides default regex strings that can be used in {@link IR @IR} annotations to specify IR constraints.
 * <p>
 * There are two types of default regexes:
 * <ul>
 *     <li><p>Standalone regexes: Use them directly.</li>
 *     <li><p>Composite regexes: Their names contain "{@code _OF}" and expect another string in a list in
 *            {@link IR#failOn()} and {@link IR#counts()}. They cannot be use as standalone regex and will result in a
 *            {@link TestFormatException} when doing so.</li>
 * </ul>
 *
 * @see IR
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


    public static void initMaps() {
        initMaps(IRNode.ALLOC, ALLOC);
        initMaps(IRNode.ALLOC_OF, ALLOC_OF);
        initMaps(IRNode.ALLOC_ARRAY, ALLOC_ARRAY);
        initMaps(IRNode.ALLOC_ARRAY_OF, ALLOC_ARRAY_OF);
        initMaps(IRNode.CHECKCAST_ARRAY, CHECKCAST_ARRAY);
        initMaps(IRNode.CHECKCAST_ARRAY_OF, CHECKCAST_ARRAY_OF);
        initMaps(IRNode.CHECKCAST_ARRAYCOPY, CHECKCAST_ARRAYCOPY);
        initMaps(IRNode.FIELD_ACCESS, FIELD_ACCESS);
        initMaps(IRNode.SCOPE_OBJECT, SCOPE_OBJECT);
    }

    private static void initMaps(String defaultRegexString, String optoAssemblyString) {
        DEFAULT_TO_PHASE_MAP.put(defaultRegexString, CompilePhase.PRINT_OPTO_ASSEMBLY);
        EnumMap<CompilePhase, String> enumMap = new EnumMap<>(CompilePhase.class);
        enumMap.put(CompilePhase.PRINT_OPTO_ASSEMBLY, optoAssemblyString);
        enumMap.put(CompilePhase.DEFAULT, optoAssemblyString);
        PLACEHOLDER_TO_REGEX_MAP.put(defaultRegexString, enumMap);
    }
}

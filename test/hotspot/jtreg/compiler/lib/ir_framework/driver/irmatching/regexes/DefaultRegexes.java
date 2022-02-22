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
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.*;

/**
 * This class contains shared parts of default regexes and provides maps from placeholder strings to default regex and
 * from default phase to PrintIdeal/PrintOptoAssembly. The placeholder strings can be found in {@link IRNode} while
 * the actual default regexes which replace these placeholders can be found in {@link IdealDefaultRegexes},
 * {@link MachDefaultRegexes}, and {@link OptoAssemblyDefaultRegexes}.
 * <p>
 *
 * @see IR
 * @see IRNode
 * @see IdealDefaultRegexes
 * @see MachDefaultRegexes
 * @see OptoAssemblyDefaultRegexes
 */
public class DefaultRegexes {
    public static final String START = "(\\d+(\\s){2}(";
    public static final String MID = ".*)+(\\s){2}===.*";
    public static final String END = ")";
    public static final String IS_REPLACED = "#IS_REPLACED#"; // Is replaced by an additional user-defined string.
    public static final String STORE_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
    public static final String LOAD_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;

    /**
     * Mapping of {@link IRNode} to either {@link IdealDefaultRegexes}, {@link MachDefaultRegexes} or
     * {@link OptoAssemblyDefaultRegexes}.
     */
    public static final Map<String, Map<CompilePhase, String>> PLACEHOLDER_TO_REGEX_MAP = new HashMap<>();

    /**
     * Mapping of {@link IRNode} to either {@link CompilePhase#PRINT_IDEAL} or {@link CompilePhase#PRINT_OPTO_ASSEMBLY}
     * depending on the output of {@link CompilePhase#getIdealPhases()} and {@link CompilePhase#getMachPhases()} ()},
     * respectively. Could also be queried with user defined strings (i.e. not defined in {@link IRNode}).
     */
    public static final Map<String, CompilePhase> DEFAULT_TO_PHASE_MAP = new HashMap<>();

    static {
        // Initialization of PLACEHOLDER_TO_REGEX and DEFAULT_TO_PHASE_MAP
        IdealDefaultRegexes.initMaps();
        OptoAssemblyDefaultRegexes.initMaps();
        IdealDefaultRegexes.initAdditionalSharedMappings();
    }

    /**
     * What's the default phase for this raw node string? If it matches a {@link IRNode} entry, we can map it to
     * {@link CompilePhase#PRINT_IDEAL} or {@link CompilePhase#PRINT_OPTO_ASSEMBLY}. Otherwise, {@link CompilePhase#DEFAULT}
     * is returned.
     */
    public static CompilePhase getCompilePhaseForIRNode(String rawNodeString) {
        return DEFAULT_TO_PHASE_MAP.getOrDefault(rawNodeString, CompilePhase.DEFAULT);
    }

    /**
     * Return the default node regex of the specified {@code compilePhase} for the provided {@code rawNodeString}.
     * If there is no entry for the war node string or if there is no mapping for the provided compile phase,
     * a format violation is reported.
     */
    public static String getDefaultRegexForIRNode(String rawNodeString, CompilePhase compilePhase) {
        var phaseToRegexMap = DefaultRegexes.PLACEHOLDER_TO_REGEX_MAP.get(rawNodeString);
        TestFormat.checkNoReport(phaseToRegexMap != null,
                                 "Did you mix an IRNode string with an additional string? " +
                                 "Must use a unique IRNode: \"" + rawNodeString + "\". Violation");
        String regex = phaseToRegexMap.get(compilePhase);
        TestFormat.checkNoReport(regex != null,
                                 "IR Node \"" + rawNodeString + "\" defined in class IRNode has no regex " +
                                 "defined for compile phase " + compilePhase + ". If you think it should be supported, " +
                                 "add a mapping to DefaultRegexes.PLACEHOLDER_TO_REGEX_MAP. Violation");
        return regex;
    }
}

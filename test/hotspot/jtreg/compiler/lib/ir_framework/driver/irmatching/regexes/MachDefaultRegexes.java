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

package compiler.lib.ir_framework.driver.irmatching.regexes;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.TestFramework;

import java.util.List;
import java.util.Map;

import static compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexes.*;

/**
 * This class provides default regex strings for matches on all compile phases on the Mach graph after matching (i.e.
 * after the application of optimization on the ideal graph). These default regexes replace any usages of placeholder s
 * trings from {@link IRNode} in check attributes {@link IR#failOn()} and {@link IR#counts()} depending on the specified
 * compile phases in {@link IR#phase()} and if the compile phase is returned in the list {@link CompilePhase#getMachPhases()}.
 * <p>
 *
 * Each new default regex for any node that needs to be matched on the ideal graph should be defined here together with
 * a mapping for which compile phase it can be used (defined with an entry in {@link DefaultRegexes#PLACEHOLDER_TO_REGEX_MAP}).
 * A mach default regexes can never be matched on the PrintIdeal or PrintOptoAssembly flag output.
 * <p>
 *
 * Not all regexes can be applied for all phases. If such an unsupported mapping is used for a compile phase, a format
 * violation is reported.
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
public class MachDefaultRegexes {
    public static final String STORE = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + END;

    public static void initMaps() {
        initAvailableForAllMachPhases(IRNode.LOOP, IdealDefaultRegexes.LOOP);
        initAvailableForAllMachPhases(IRNode.COUNTED_LOOP, IdealDefaultRegexes.COUNTED_LOOP);
        initAvailableForAllMachPhases(IRNode.COUNTED_LOOP_MAIN, IdealDefaultRegexes.COUNTED_LOOP_MAIN);
    }

    private static void initAvailableForAllMachPhases(String defaultRegexString, String idealString) {
        Map<CompilePhase, String> enumMap = PLACEHOLDER_TO_REGEX_MAP.get(defaultRegexString);
        TestFramework.check(enumMap != null, "must be set by IdealDefaultRegexes");
        List<CompilePhase> compilePhases = CompilePhase.getMachPhases();
        updatePlaceholderMap(idealString, compilePhases, enumMap);
    }

}

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

package compiler.lib.ir_framework;

import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.shared.CheckedTestFrameworkException;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;
import jdk.test.lib.Platform;
import sun.hotspot.WhiteBox;

import java.util.*;

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
public class DefaultRegexes {
    public static final String START = "(\\d+(\\s){2}(";
    public static final String MID = ".*)+(\\s){2}===.*";
    public static final String END = ")";
    public static final String IS_REPLACED = "#IS_REPLACED#"; // Is replaced by an additional user-defined string.
    public static final String STORE_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
    public static final String LOAD_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;


    public static final Map<String, Map<CompilePhase, String>> PLACEHOLDER_TO_REGEX_MAP = new HashMap<>();
    public static final Map<String, CompilePhase> DEFAULT_TO_PHASE_MAP = new HashMap<>();

    static {
        IdealDefaultRegexes.initMaps();
        OptoAssemblyDefaultRegexes.initMaps();
    }

    public static String getRegexForIRNode(String node, CompilePhase compilePhase) {
        var phaseToRegexMap = DefaultRegexes.PLACEHOLDER_TO_REGEX_MAP.get(node);
        TestFormat.checkNoReport(phaseToRegexMap != null, "Did you mix a default IRNode regex with an" +
                                                          " additional string? Not a unique default IRNode: \"" + node + "\"");
        String regex = phaseToRegexMap.get(compilePhase);
        TestFormat.checkNoReport(regex != null, "Default regex \"" + node
                                                + "\" not defined for compile phase " + compilePhase);
        return regex;
    }
}

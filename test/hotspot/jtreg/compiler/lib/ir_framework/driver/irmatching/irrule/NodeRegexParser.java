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

package compiler.lib.ir_framework.driver.irmatching.irrule;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.DefaultRegexes;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.parser.NodeRegex;
import compiler.lib.ir_framework.shared.TestFormatException;

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
class NodeRegexParser {

    public static String parseRawNodeString(CompilePhase compilePhase, NodeRegex nodeRegex, String rawNodeString) {
        String parsedNodeString = rawNodeString;
        if (IRNode.isDefaultIRNode(rawNodeString)) {
            parsedNodeString = parseDefaultNode(compilePhase, nodeRegex, rawNodeString);
        }
        return parsedNodeString;
    }

    private static String parseDefaultNode(CompilePhase compilePhase, NodeRegex nodeRegex, String rawNodeString) {
        String parsedNodeString = DefaultRegexes.getRegexForIRNode(rawNodeString, compilePhase);
        if (nodeRegex.isCompositeNode()) {
            parsedNodeString = parsedNodeString.replaceAll(DefaultRegexes.IS_REPLACED, nodeRegex.getUserPostfixString());
        }
        return parsedNodeString;
    }
}

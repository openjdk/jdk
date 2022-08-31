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

package compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.mapping.IRNodeMappings;
import compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexConstants;
import compiler.lib.ir_framework.shared.TestFormat;

/**
 * Base class to parse a raw constraint to replace the placeholder strings from {@link IRNode} by actual default
 * regexes depending on the compilation phase.
 *
 * @see RawConstraint
 */
abstract class RawConstraintParser {
    protected abstract void parse(CompilePhase compilePhase);

    protected ParsedIRNodeInfo parseIRNodeInfo(RawConstraint rawConstraint, CompilePhase compilePhase) {
        String rawNodeString = rawConstraint.getRawNodeString();
        if (compilePhase == CompilePhase.DEFAULT) {
            compilePhase = IRNodeMappings.getDefaultPhaseForIRNode(rawNodeString);
        }
        String regex = parseRegex(rawConstraint, rawConstraint.getRawNodeString(), compilePhase);
        return new ParsedIRNodeInfo(regex, compilePhase);
    }

    private String parseRegex(RawConstraint rawConstraint, String rawNodeString, CompilePhase compilePhase) {
        if (IRNode.isDefaultIRNode(rawNodeString)) {
            String parsedNodeString = IRNodeMappings.getRegexForPhaseOfIRNode(rawNodeString, compilePhase);
            if (rawConstraint.hasCompositeNode()) {
                String userPostfixString = rawConstraint.getUserPostfixString();
                TestFormat.checkNoReport(!userPostfixString.isEmpty(),
                                         "Provided empty string for composite node " + rawNodeString
                                         + " at constraint " + rawConstraint.getConstraintIndex());
                parsedNodeString = parsedNodeString.replaceAll(DefaultRegexConstants.IS_REPLACED, rawConstraint.getUserPostfixString());
            }
            return parsedNodeString;
        }
        return rawNodeString;
    }
    record ParsedIRNodeInfo(String regex, CompilePhase compilePhase) {}
}

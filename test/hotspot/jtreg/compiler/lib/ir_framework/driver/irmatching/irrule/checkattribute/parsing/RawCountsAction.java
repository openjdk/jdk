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

package compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawCountsConstraint;
import compiler.lib.ir_framework.shared.Comparison;
import compiler.lib.ir_framework.shared.ComparisonConstraintParser;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

/**
 * Action that is performed when reading a constraint of a {@link IR#counts}.
 */
class RawCountsAction implements RawConstraintAction {
    @Override
    public RawConstraint createRawConstraint(CheckAttributeStringIterator checkAttributeStringIterator, int constraintIndex) {
        RawIRNode rawIRNode = new RawIRNode(checkAttributeStringIterator);
        Comparison<Integer> comparison = parseCountString(checkAttributeStringIterator, rawIRNode);
        return new RawCountsConstraint(rawIRNode, comparison, constraintIndex);
    }

    private static Comparison<Integer> parseCountString(CheckAttributeStringIterator checkAttributeStringIterator, RawIRNode rawIRNode) {
        String countsString = readCountsString(checkAttributeStringIterator, rawIRNode);
        try {
            return ComparisonConstraintParser.parse(countsString, RawCountsAction::parsePositiveInt);
        } catch (TestFormatException e) {
            String irNodeString = IRNode.getIRNodeAccessString(rawIRNode.irNodePlaceholder());
            throw new TestFormatException(e.getMessage() + ", node " + irNodeString + ", in count string \"" +
                                          countsString + "\"");
        }
    }

    private static String readCountsString(CheckAttributeStringIterator checkAttributeStringIterator, RawIRNode rawIRNode) {
        CheckAttributeString countsString = checkAttributeStringIterator.next();
        TestFormat.checkNoReport(countsString.isValid(), "Missing count for node " +
                                                         IRNode.getIRNodeAccessString(rawIRNode.irNodePlaceholder()));
        return countsString.value();
    }

    public static int parsePositiveInt(String s) {
        int result = Integer.parseInt(s);
        if (result < 0) {
            throw new NumberFormatException("cannot be negative");
        }
        return result;
    }

    @Override
    public CompilePhase defaultPhase(CheckAttributeStringIterator checkAttributeStringIterator) {
        RawIRNode rawIRNode = new RawIRNode(checkAttributeStringIterator);
        readCountsString(checkAttributeStringIterator, rawIRNode);
        return rawIRNode.defaultPhase();
    }
}

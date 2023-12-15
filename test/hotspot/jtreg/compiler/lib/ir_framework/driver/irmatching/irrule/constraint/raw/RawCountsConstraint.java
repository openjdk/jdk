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

package compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw;


import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawIRNode;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;
import compiler.lib.ir_framework.driver.SuccessOnlyConstraintException;
import compiler.lib.ir_framework.shared.Comparison;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

/**
 * This class represents a raw constraint of a {@link IR#counts()} attribute.
 *
 * @see IR#counts()
 */
public class RawCountsConstraint implements RawConstraint {
    private final RawIRNode rawIRNode;
    private final int constraintIndex;
    private final Comparison<Integer> comparison;

    public RawCountsConstraint(RawIRNode rawIRNode, Comparison<Integer> comparison, int constraintIndex) {
        this.rawIRNode = rawIRNode;
        this.constraintIndex = constraintIndex;
        this.comparison = comparison;
    }

    @Override
    public CompilePhase defaultCompilePhase() {
        return rawIRNode.defaultCompilePhase();
    }

    private Comparison.Bound getComparisonBound() {
        switch (comparison.getComparator()) {
            case "<" -> {
                TestFormat.checkNoReport(comparison.getGivenValue() > 0, "Node count comparison \"<" +
                                         comparison.getGivenValue() + "\" is not allowed: always false.");
                TestFormat.checkNoReport(comparison.getGivenValue() > 1, "Node count comparison \"<" +
                                         comparison.getGivenValue() + "\" should be rewritten as \"=0\"");
                return Comparison.Bound.UPPER;
            }
            case "<=" -> {
                TestFormat.checkNoReport(comparison.getGivenValue() >= 0, "Node count comparison \"<=" +
                                         comparison.getGivenValue() + "\" is not allowed: always false.");
                TestFormat.checkNoReport(comparison.getGivenValue() >= 1, "Node count comparison \"<=" +
                                         comparison.getGivenValue() + "\" should be rewritten as \"=0\"");
                return Comparison.Bound.UPPER;
            }
            case "=" -> {
                // "=0" is same as setting upper bound - just like for failOn. But if we compare equals a
                // strictly positive number it is like setting both upper and lower bound (equal).
                return comparison.getGivenValue() > 0 ? Comparison.Bound.EQUAL : Comparison.Bound.UPPER;
            }
            case ">" -> {
                TestFormat.checkNoReport(comparison.getGivenValue() >= 0, "Node count comparison \">" +
                                         comparison.getGivenValue() + "\" is useless, please only use positive numbers.");
                return Comparison.Bound.LOWER;
            }
            case ">=" -> {
                TestFormat.checkNoReport(comparison.getGivenValue() > 0, "Node count comparison \">=" +
                                         comparison.getGivenValue() + "\" is useless, please only use strictly positive numbers with greater-equal.");
                return Comparison.Bound.LOWER;
            }
            case "!=" -> throw new TestFormatException("Not-equal comparator not supported. Please rewrite the rule.");
            default -> throw new TestFormatException("Comparator not handled: " + comparison.getComparator());
        }
    }

    @Override
    public Constraint parse(CompilePhase compilePhase, String compilationOutput, VMInfo vmInfo) {
        TestFramework.check(compilePhase != CompilePhase.DEFAULT, "must not be default");
        try {
            return Constraint.createCounts(rawIRNode.regex(compilePhase, vmInfo, getComparisonBound()), constraintIndex, comparison, compilationOutput);
        } catch (SuccessOnlyConstraintException e) {
            return Constraint.createSuccess();
        }
    }
}

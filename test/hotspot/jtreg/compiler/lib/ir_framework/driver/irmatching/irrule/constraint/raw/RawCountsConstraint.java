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
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraint;
import compiler.lib.ir_framework.shared.Comparison;
import compiler.lib.ir_framework.shared.ComparisonConstraintParser;
import compiler.lib.ir_framework.shared.TestFormatException;

/**
 * This class represents a raw constraint of a {@link IR#counts()} attribute.
 *
 * @see IR#counts()
 */
public class RawCountsConstraint extends RawConstraint {
    private final String countString;

    public RawCountsConstraint(String rawNodeString, String userPostfixString, String countString, int constraintIndex) {
        super(rawNodeString, userPostfixString, constraintIndex);
        this.countString = countString;
    }

    public Constraint parse(CompilePhase compilePhase, IRMethod irMethod) {
        compilePhase = getCompilePhase(compilePhase);
        String regex = parseRegex(compilePhase);
        Comparison<Integer> comparison = parseComparison();
        return new CountsConstraint(regex, constraintIndex, compilePhase, comparison, irMethod.getOutput(compilePhase));
    }

    private Comparison<Integer> parseComparison() {
        try {
            return ComparisonConstraintParser.parse(countString, RawCountsConstraint::parsePositiveInt);
        } catch (TestFormatException e) {
            throw new TestFormatException(e.getMessage() + ", node \"" + rawNodeString
                                          + "\", in count string");
        }
    }

    public static int parsePositiveInt(String s) {
        int result = Integer.parseInt(s);
        if (result < 0) {
            throw new NumberFormatException("cannot be negative");
        }
        return result;
    }
}

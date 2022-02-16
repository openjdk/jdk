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
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOn;

import java.util.ArrayList;
import java.util.List;

/**
 * This class parses raw failOn constraints. It replaces the placeholder strings from {@link IRNode} by actual default
 * regexes. If there is no default regex provided for a compile phase, a format violation is reported.
 * <p>
 * This parser returns a ready to be used {@link FailOn} constraint object to apply IR regex matching on.
 *
 * @see RawConstraint
 * @see FailOn
 */
public class RawFailOnConstraintParser extends RawConstraintParser<Constraint, RawConstraint> {

    private RawFailOnConstraintParser() {}

    /**
     * Returns a new {@link FailOn} object by parsing the provided {@code rawFailOnConstraints} list or null if this
     * list is empty.
     */
    public static FailOn parse(List<RawConstraint> rawFailOnConstraints, CompilePhase compilePhase) {
        if (rawFailOnConstraints.isEmpty()) {
            List<Constraint> constraintResultList = new ArrayList<>();
            new RawFailOnConstraintParser().parseNonEmptyConstraints(constraintResultList, rawFailOnConstraints, compilePhase);
            return new FailOn(constraintResultList, compilePhase);
        }
        return null;
    }

    @Override
    protected Constraint parseRawConstraint(RawConstraint constraintResultList, CompilePhase compilePhase) {
        String rawNodeString = constraintResultList.getRawNodeString();
        String parsedNodeString = parseRawNodeString(compilePhase, constraintResultList, rawNodeString);
        return new Constraint(parsedNodeString, constraintResultList.getConstraintIndex());
    }
}

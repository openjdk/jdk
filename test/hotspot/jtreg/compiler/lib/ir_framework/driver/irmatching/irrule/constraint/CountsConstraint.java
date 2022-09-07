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

package compiler.lib.ir_framework.driver.irmatching.irrule.constraint;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.shared.Comparison;

import java.util.List;

/**
 * This class represents a single counts constraint of a {@link Counts} attribute of an IR rule for a compile phase.
 * It stores a ready to be used regex for a compile phase (i.e. all {@link IRNode} placeholder strings are updated and
 * composite nodes merged) to apply matching on. It additionally provides a comparison object on which the matched node
 * count is compared against a user-specified number.
 * <p>
 *
 * @see Counts
 */
public class CountsConstraint extends Constraint {
    private final Comparison<Integer> comparison;

    public CountsConstraint(String regex, int index, CompilePhase compilePhase, Comparison<Integer> comparison,
                            String compilationOutput) {
        super(regex, index, compilePhase, compilationOutput);
        this.comparison = comparison;
    }

    public Comparison<Integer> getComparison() {
        return comparison;
    }

    @Override
    public ConstraintFailure match() {
        List<String> countsMatches = getMatchedNodes(compilationOutput);
        if (!comparison.compare(countsMatches.size())) {
            return new CountsConstraintFailure(this, countsMatches);
        }
        return null;
    }
}

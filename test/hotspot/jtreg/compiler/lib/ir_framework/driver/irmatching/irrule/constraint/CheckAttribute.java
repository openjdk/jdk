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

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Base class representing a parsed check attribute of an IR rule for a compile phase.
 * <p>
 *
 * Placeholder strings from {@link IRNode} are replaced by default regexes for this compile phase and composite IR nodes
 * are merged together.
 *
 * @see IR
 */
abstract public class CheckAttribute<C extends Constraint> {
    private final List<C> constraints;

    public CheckAttribute(List<C> constraints) {
        this.constraints = constraints;
    }

    public CheckAttributeMatchResult check(String phaseCompilationOutput) {
        CheckAttributeMatchResult matchResult = createMatchResult();
        List<ConstraintFailure> constraintFailures = new ArrayList<>();
        for (C constraint : constraints) {
            checkConstraint(constraintFailures, constraint, phaseCompilationOutput);
        }
        if (!constraintFailures.isEmpty()) {
            matchResult.setFailures(constraintFailures);
        }
        return matchResult;
    }

    abstract protected CheckAttributeMatchResult createMatchResult();

    abstract void checkConstraint(List<ConstraintFailure> constraintFailures, C constraint, String phaseCompilationOutput);

    protected List<String> getMatchedNodes(Constraint constraint, String phaseCompilationOutput) {
        Pattern pattern = Pattern.compile(constraint.getRegex());
        Matcher matcher = pattern.matcher(phaseCompilationOutput);
        return matcher.results().map(MatchResult::group).collect(Collectors.toList());
    }
}

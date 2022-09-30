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
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttribute;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;
import compiler.lib.ir_framework.shared.Comparison;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class represents a single constraint of a {@link CheckAttribute} of an IR rule for a compile phase. It stores a
 * ready to be used regex for a compile phase (i.e. all {@link IRNode} placeholder strings are updated and composite nodes
 * merged) to apply matching on.
 * <p>
 *
 * {@link FailOn} can directly use this class while {@link Counts} need some more information stored with the subclass
 *
 * @see CheckAttribute
 * @see FailOn
 */
public class Constraint implements Matchable {
    private final String regex;
    private final int index; // constraint indices start at 1.
    private final CompilePhase compilePhase;
    protected final String compilationOutput;
    private final ConstraintCheck constraintCheck;

    private Constraint(ConstraintCheck constraintCheck, String regex, int index, CompilePhase compilePhase,
                       String compilationOutput) {
        this.constraintCheck = constraintCheck;
        this.regex = regex;
        this.index = index;
        this.compilePhase = compilePhase;
        this.compilationOutput = compilationOutput;
    }

    public static Constraint createFailOn(String regex, int index, CompilePhase compilePhase, String compilationOutput) {
        return new Constraint(new FailOnConstraintCheck(), regex, index, compilePhase, compilationOutput);
    }

    public static Constraint createCounts(String regex, int index, Comparison<Integer> comparison,
                                          CompilePhase compilePhase, String compilationOutput) {
        return new Constraint(new CountsConstraintCheck(comparison), regex, index, compilePhase, compilationOutput);
    }

    public String regex() {
        return regex;
    }

    public int index() {
        return index;
    }

    public CompilePhase compilePhase() {
        return compilePhase;
    }

    protected List<String> matchNodes(String compilationOutput) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(compilationOutput);
        return matcher.results().map(java.util.regex.MatchResult::group).collect(Collectors.toList());
    }

    @Override
    public MatchResult match() {
        List<String> matchedNodes = matchNodes(compilationOutput);
        return constraintCheck.check(this, matchedNodes);
    }
}

interface ConstraintCheck {
    MatchResult check(Constraint constraint, List<String> matchedNodes);
}

class FailOnConstraintCheck implements ConstraintCheck {
    @Override
    public MatchResult check(Constraint constraint, List<String> matchedNodes) {
        if (!matchedNodes.isEmpty()) {
            return new FailOnConstraintFailure(constraint, matchedNodes);
        }
        return ConstraintSuccess.getInstance();
    }
}

class CountsConstraintCheck implements ConstraintCheck {
    private final Comparison<Integer> comparison;

    CountsConstraintCheck(Comparison<Integer> comparison) {
        this.comparison = comparison;
    }

    @Override
    public MatchResult check(Constraint constraint, List<String> matchedNodes) {
        if (!comparison.compare(matchedNodes.size())) {
            return new CountsConstraintFailure(constraint, matchedNodes, comparison);
        }
        return ConstraintSuccess.getInstance();
    }
}

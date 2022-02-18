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
import compiler.lib.ir_framework.driver.irmatching.Matching;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class representing a parsed check attribute of an IR rule for a compile phase.
 * <p>
 *
 * Placeholder strings from {@link IRNode} are replaced by default regexes for this compile phase and composite IR nodes
 * are merged together.
 *
 * @see IR
 */
abstract public class CheckAttribute<C extends Constraint, R extends CheckAttributeMatchResult> implements Matching {
    private final List<C> constraints;
    protected String compilationOutput;

    public CheckAttribute(List<C> constraints, String compilationOutput) {
        this.constraints = constraints;
        this.compilationOutput = compilationOutput;
    }

    @Override
    public R match() {
        R matchResult = createMatchResult();
        List<ConstraintFailure> constraintFailures = new ArrayList<>();
        for (C constraint : constraints) {
            checkConstraint(constraintFailures, constraint);
        }
        if (!constraintFailures.isEmpty()) {
            matchResult.setFailures(constraintFailures);
        }
        return matchResult;
    }

    abstract protected R createMatchResult();

    abstract void checkConstraint(List<ConstraintFailure> constraintFailures, C constraint);

    protected List<String> getMatchedNodes(Constraint constraint) {
        String regex = constraint.getRegex();
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(compilationOutput);
        List<String> matches = new ArrayList<>();
        if (m.find()) {
            do {
                matches.add(m.group());
            } while (m.find());
        }
        return matches;
    }
}

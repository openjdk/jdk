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
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttribute;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;

import java.util.List;
import java.util.regex.MatchResult;
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
 * {@link CountsConstraint}.
 *
 * @see CheckAttribute
 * @see FailOn
 */
abstract public class Constraint implements Matchable {
    private final String regex;
    private final int index; // constraint indices start at 1.
    private final CompilePhase compilePhase;
    protected final String compilationOutput;

    public Constraint(String regex, int index, CompilePhase compilePhase, String compilationOutput) {
        this.regex = regex;
        this.index = index;
        this.compilePhase = compilePhase;
        this.compilationOutput = compilationOutput;
    }

    public String getRegex() {
        return regex;
    }

    public int getIndex() {
        return index;
    }

    public CompilePhase getCompilePhase() {
        return compilePhase;
    }

    protected List<String> getMatchedNodes(String compilationOutput) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(compilationOutput);
        return matcher.results().map(MatchResult::group).collect(Collectors.toList());
    }

    @Override
    abstract public ConstraintFailure match();
}


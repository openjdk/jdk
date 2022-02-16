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
import compiler.lib.ir_framework.IR;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class representing a failOn attribute of an IR rule.
 *
 * @see IR#failOn()
 */
public class FailOn extends CheckAttribute {
    private final Pattern quickPattern;

    public FailOn(List<Constraint> constraints, CompilePhase compilePhase) {
        super(constraints, compilePhase);
        String patternString = constraints.stream().map(Constraint::getRegex).collect(Collectors.joining("|"));
        this.quickPattern = Pattern.compile(String.join("|", patternString));
    }

    @Override
    public FailOnMatchResult apply(String compilation) {
        FailOnMatchResult result = new FailOnMatchResult();
        Matcher matcher = quickPattern.matcher(compilation);
        if (matcher.find()) {
            result.setFailures(createFailOnFailures(compilation));
        }
        return result;
    }

    private List<RegexFailure> createFailOnFailures(String compilation) {
        List<RegexFailure> regexFailures = new ArrayList<>();
        for (Constraint constraint : constraints) {
            checkNode(regexFailures, compilation, constraint);
        }
        return regexFailures;
    }

    private void checkNode(List<RegexFailure> regexFailures, String compilation, Constraint constraint) {
        String node = constraint.getRegex();
        Pattern p = Pattern.compile(constraint.getRegex());
        Matcher m = p.matcher(compilation);
        if (m.find()) {
            List<String> matches = getMatchedNodes(m);
            regexFailures.add(new FailOnRegexFailure(node, constraint.getIndex(), matches));
        }
    }
}

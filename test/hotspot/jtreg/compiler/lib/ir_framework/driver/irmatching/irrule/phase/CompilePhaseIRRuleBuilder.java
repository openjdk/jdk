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

package compiler.lib.ir_framework.driver.irmatching.irrule.phase;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.CountsAttributeParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.FailOnAttributeParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawCountsConstraint;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Base builder class to create a list of {@link CompilePhaseIRRule} for an IR rule to apply IR matching on.
 *
 * @see CompilePhaseIRRule
 * @see DefaultPhaseIRRule
 */
abstract public class CompilePhaseIRRuleBuilder {
    protected final List<RawConstraint> rawFailOnConstraints;
    protected final List<RawCountsConstraint> rawCountsConstraints;
    protected final IRMethod irMethod;

    public CompilePhaseIRRuleBuilder(List<RawConstraint> rawFailOnConstraints, List<RawCountsConstraint> rawCountsConstraints, IRMethod irMethod) {
        this.rawFailOnConstraints = rawFailOnConstraints;
        this.rawCountsConstraints = rawCountsConstraints;
        this.irMethod = irMethod;
    }

    /**
     * Creates a list of {@link CompilePhaseIRRule} instances. A non-default phase will create one {@link CompilePhaseIRRule}
     * instance while a default phase can create more than one (see {@link DefaultPhaseIRRuleBuilder}.
     */
    public static List<CompilePhaseIRRule> create(IR irAnno, IRMethod irMethod) {
        List<RawConstraint> rawFailOnConstraints = FailOnAttributeParser.parse(irAnno.failOn());
        List<RawCountsConstraint> rawCountsConstraints = CountsAttributeParser.parse(irAnno.counts());
        NormalPhaseIRRuleBuilder normalPhaseIRRuleBuilder =
                new NormalPhaseIRRuleBuilder(rawFailOnConstraints, rawCountsConstraints, irMethod);
        DefaultPhaseIRRuleBuilder defaultPhaseIRRuleBuilder =
                new DefaultPhaseIRRuleBuilder(rawFailOnConstraints, rawCountsConstraints, irMethod);
        List<CompilePhaseIRRule> compilePhaseIRRules = new ArrayList<>();
        for (CompilePhase compilePhase : irAnno.phase()) {
            if (compilePhase != CompilePhase.DEFAULT) {
                compilePhaseIRRules.add(normalPhaseIRRuleBuilder.create(compilePhase));
            } else {
                compilePhaseIRRules.addAll(defaultPhaseIRRuleBuilder.create());
            }
        }
        return compilePhaseIRRules;
    }

    protected FailOn createFailOn(List<Constraint> constraintsList) {
        if (constraintsList != null) {
            return new FailOn(constraintsList);
        } else {
            return null;
        }
    }

    protected Counts createCounts(List<CountsConstraint> constraintList) {
        if (constraintList != null) {
            return new Counts(constraintList);
        } else {
            return null;
        }
    }
}

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

package compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.action.ConstraintAction;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.action.CreateRawConstraintAction;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.action.DefaultPhaseConstraintAction;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class parses the check attribute strings of ({@link IR#failOn()} or {@link IR#counts()}) as found in a
 * {@link IR @IR} annotation without replacing placeholder IR strings, yet. For each constraint (i.e. all consecutive
 * strings in the check attribute that eventually form a constraint) we apply a {@link ConstraintAction} which defines
 * the action that is performed for the constraint.
 *
 * @see IR#failOn()
 * @see IR#counts()
 * @see ConstraintAction
 */
 public class CheckAttributeStrings {
    private final String[] checkAttributeStrings;

    public CheckAttributeStrings(String[] checkAttributeStrings) {
        this.checkAttributeStrings = checkAttributeStrings;
    }

    /**
     * Walk over the check attribute strings as found in the {@link IR} annotation and create {@link RawConstraint}
     * objects for them. Return them in a list.
     */
    public final List<RawConstraint> createRawConstraints(CreateRawConstraintAction createRawConstraintAction) {
        CheckAttributeReader<RawConstraint> reader = new CheckAttributeReader<>(checkAttributeStrings,
                                                                                createRawConstraintAction);
        List<RawConstraint> rawConstraints = new ArrayList<>();
        reader.read(rawConstraints);
        return rawConstraints;
    }

    /**
     * Walk over the check attribute strings as found in the {@link IR annotation} and return the default phase for
     * {@link IRNode} constraints.
     */
    public final Set<CompilePhase> parseDefaultCompilePhases(DefaultPhaseConstraintAction defaultPhaseConstraintAction) {
        Set<CompilePhase> compilePhases = new HashSet<>();
        CheckAttributeReader<CompilePhase> reader = new CheckAttributeReader<>(checkAttributeStrings,
                                                                               defaultPhaseConstraintAction);
        reader.read(compilePhases);
        return compilePhases;
    }
}

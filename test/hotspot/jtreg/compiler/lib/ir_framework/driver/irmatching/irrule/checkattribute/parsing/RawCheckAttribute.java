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
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class to parse a check attribute ({@link IR#failOn()} or {@link IR#counts()}) as found in a {@link IR @IR}
 * annotation without replacing placeholder IR strings, yet. For each raw constraint (i.e. all consecutive strings in
 * the check attribute that eventually form a constraint) we apply a {@link RawConstraintReadAction} which defines how
 * a constraint must be parsed.
 *
 * @see IR#failOn()
 * @see IR#counts()
 * @see RawConstraintReadAction
 */
 public class RawCheckAttribute {
    private final CheckAttributeStringIterator checkAttributeStringIterator;
    private final RawConstraintReadAction rawConstraintReadAction;

    public RawCheckAttribute(String[] checkAttribute, RawConstraintReadAction rawConstraintReadAction) {
        this.checkAttributeStringIterator = new CheckAttributeStringIterator(checkAttribute);
        this.rawConstraintReadAction = rawConstraintReadAction;
    }

    public static RawCheckAttribute createFailOn(IR irAnno) {
        return new RawCheckAttribute(irAnno.failOn(), new RawFailOnReadAction());
    }

    public static RawCheckAttribute createCounts(IR irAnno) {
        return new RawCheckAttribute(irAnno.counts(), new RawCountsReadAction());
    }

    public final List<RawConstraint> parse() {
        int index = 1;
        List<RawConstraint> rawConstraints = new ArrayList<>();
        while (checkAttributeStringIterator.hasNext()) {
            rawConstraints.add(rawConstraintReadAction.createRawConstraint(checkAttributeStringIterator, index++));
        }
        return rawConstraints;
    }

    public final Set<CompilePhase> parseDefaultCompilePhases() {
        Set<CompilePhase> compilePhases = new HashSet<>();
        while (checkAttributeStringIterator.hasNext()) {
            compilePhases.add(rawConstraintReadAction.defaultPhase(checkAttributeStringIterator));
        }
        return compilePhases;
    }
}

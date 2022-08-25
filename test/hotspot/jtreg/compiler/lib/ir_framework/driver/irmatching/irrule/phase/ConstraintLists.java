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

import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraint;

import java.util.List;

class ConstraintLists {
    private final List<Constraint> failOnConstraints;
    private List<CountsConstraint> countsConstraints;

    private ConstraintLists(List<Constraint> failOnConstraints, List<CountsConstraint> countsConstraints) {
        this.failOnConstraints = failOnConstraints;
        this.countsConstraints = countsConstraints;
    }

    public static ConstraintLists createWithFailOnConstraints(List<Constraint> failOnConstraints) {
        return new ConstraintLists(failOnConstraints, null);
    }

    public static ConstraintLists createWithCountsConstraints(List<CountsConstraint> countsConstraints) {
        return new ConstraintLists(null, countsConstraints);
    }

    public List<Constraint> getFailOnConstraints() {
        return failOnConstraints;
    }

    public List<CountsConstraint> getCountsConstraints() {
        return countsConstraints;
    }

    public void setCountsConstraints(List<CountsConstraint> countsConstraints) {
        this.countsConstraints = countsConstraints;
    }
}

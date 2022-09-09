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

package compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.ConstraintFailure;

import java.util.List;

/**
 * This class represents a fully parsed {@link IR#counts()} attribute of an IR rule for a compile phase that is ready
 * to be IR matched on.
 *
 * @see IR#counts()
 * @see CheckAttribute
 */
public class Counts extends CheckAttribute {

    public Counts(List<Constraint> constraints) {
        super(constraints);
    }


    @Override
    public CheckAttributeMatchResult match() {
        CheckAttributeMatchResult checkAttributeMatchResult = new CheckAttributeMatchResult(CheckAttributeType.COUNTS);
        for (Constraint constraint : constraints) {
            ConstraintFailure constraintFailure = constraint.match();
            if (constraintFailure != null) {
                checkAttributeMatchResult.addFailure(constraintFailure);
            }
        }
        return checkAttributeMatchResult;
    }
}

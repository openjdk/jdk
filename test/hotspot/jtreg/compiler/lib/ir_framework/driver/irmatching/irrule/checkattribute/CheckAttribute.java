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
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;

import java.util.List;

/**
 * Base class representing a parsed check attribute ({@link IR#failOn()} or {@link IR#counts()}) of an IR rule for a
 * compile phase. This means that all {@link IRNode} placeholder strings are replaced by compile phase specific regexes
 * and all composite IR nodes are merged to a single regex. The constraints are ready to be IR matched against.
 *
 * @see IR
 */
abstract public class CheckAttribute implements Matchable {
    protected final List<Constraint> constraints;

    public CheckAttribute(List<Constraint> constraints) {
        this.constraints = constraints;
    }

    @Override
    abstract public CheckAttributeMatchResult match();
}

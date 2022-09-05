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

package compiler.lib.ir_framework.driver.irmatching.irmethod;

import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;

import java.util.List;

/**
 * This class represents an IR matching result of an {@link IRMethod}.
 *
 * @see IRMethod
 */
public class IRMethodMatchResult implements Comparable<IRMethodMatchResult>, MatchResult {
    protected final IRMethod irMethod;
    /**
     * List of all IR rule match results which could have been applied on different compile phases.
     */
    private final List<IRRuleMatchResult> irRulesMatchResults;

    public IRMethodMatchResult(IRMethod irMethod, List<IRRuleMatchResult> irRulesMatchResults) {
        this.irMethod = irMethod;
        this.irRulesMatchResults = irRulesMatchResults;
    }

    public int getFailedIRRuleCount() {
        return irRulesMatchResults.size();
    }

    public IRMethod getIRMethod() {
        return irMethod;
    }

    @Override
    public boolean fail() {
        return !irRulesMatchResults.isEmpty();
    }

    /**
     * Comparator method to sort the failed IR methods alphabetically.
     */
    @Override
    public int compareTo(IRMethodMatchResult other) {
        return this.irMethod.getMethod().getName().compareTo(other.irMethod.getMethod().getName());
    }

    @Override
    public void accept(MatchResultVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void acceptChildren(MatchResultVisitor visitor) {
        acceptChildren(visitor, irRulesMatchResults);
    }
}

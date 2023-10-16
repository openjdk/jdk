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

package compiler.lib.ir_framework.driver.irmatching.irrule;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.Compilation;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.MatchableMatcher;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleBuilder;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;

/**
 * This class represents a generic {@link IR @IR} rule of an IR method. It contains a list of compile phase specific
 * IR rule versions where {@link IRNode} placeholder strings of are replaced by regexes associated with the compile phase.
 *
 * @see CompilePhaseIRRule
 * @see IRRuleMatchResult
 */
public class IRRule implements Matchable {
    private final int ruleId;
    private final IR irAnno;
    private final MatchableMatcher matcher;

    public IRRule(int ruleId, IR irAnno, Compilation compilation, VMInfo vmInfo) {
        this.ruleId = ruleId;
        this.irAnno = irAnno;
        this.matcher = new MatchableMatcher(new CompilePhaseIRRuleBuilder(irAnno, compilation).build(vmInfo));
    }

    @Override
    public MatchResult match() {
        return new IRRuleMatchResult(ruleId, irAnno, matcher.match());
    }
}

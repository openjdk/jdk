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

package compiler.lib.ir_framework.driver.irmatching.reporting;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.driver.irmatching.MatchResultVisitor;
import compiler.lib.ir_framework.driver.irmatching.TestClassResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseMatchResult;

import java.util.EnumSet;
import java.util.stream.Collectors;

public class CompilationOutputBuilder implements MatchResultVisitor {

    private final TestClassResult testClassResult;
    private final StringBuilder msg = new StringBuilder();
    private final EnumSet<CompilePhase> failedCompilePhases = EnumSet.noneOf(CompilePhase.class);
    private int reportedMethodCount = 0;

    public CompilationOutputBuilder(TestClassResult testClassResult) {
        this.testClassResult = testClassResult;
    }

    @Override
    public void visitAfter(IRMethodMatchResult irMethodMatchResult) {
        IRMethod irMethod = irMethodMatchResult.getIRMethod();
        msg.append(buildMatchedCompileOutputOfPhases(irMethod));
        failedCompilePhases.clear();
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        appendIRMethodHeader(irMethodMatchResult);
    }

    private void appendIRMethodHeader(IRMethodMatchResult irMethodMatchResult) {
        reportedMethodCount++;
        if (reportedMethodCount > 1) {
            msg.append(System.lineSeparator());
        }
        msg.append(reportedMethodCount).append(") Compilation of \"")
           .append(irMethodMatchResult.getIRMethod().getMethod()).append("\":")
           .append(System.lineSeparator());
    }

    private String buildMatchedCompileOutputOfPhases(IRMethod irMethod) {
        return failedCompilePhases.stream()
                                  .map(irMethod::getOutput)
                                  .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }

    @Override
    public void visit(CompilePhaseMatchResult compilePhaseMatchResult) {
        failedCompilePhases.add(compilePhaseMatchResult.getCompilePhase());
    }


    @Override
    public void visit(NotCompiledResult notCompiledResult) {
        appendIRMethodHeader(notCompiledResult);
        msg.append("<empty>").append(System.lineSeparator());
    }

    public String build() {
        testClassResult.accept(this);
        return msg.toString();
    }
}

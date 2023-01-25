/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.driver.irmatching.parser.TestClassParser;
import compiler.lib.ir_framework.driver.irmatching.report.CompilationOutputBuilder;
import compiler.lib.ir_framework.driver.irmatching.report.FailureMessageBuilder;

/**
 * This class performs IR matching on the prepared {@link TestClass} object parsed by {@link TestClassParser}.
 * All applicable @IR rules are matched with all their defined compilation phases. If there are any IR matching failures,
 * an {@link IRViolationException} is reported which provides a formatted failure message and the compilation outputs
 * of the failed compilation phases.
 */
public class IRMatcher {
    private final Matchable testClass;

    public IRMatcher(Matchable testClass) {
        this.testClass = testClass;
    }

    /**
     * Do an IR matching of all methods with applicable @IR rules prepared with by the {@link TestClassParser}.
     */
    public void match() {
        MatchResult result = testClass.match();
        if (result.fail()) {
            reportFailures(result);
        }
    }

    /**
     * Report all IR violations in a pretty format to the user by throwing an {@link IRViolationException}. This includes
     * an exact description of the failure (method, rule, compile phase, check attribute, and constraint) and the
     * associated compile phase output of the failure.
     */
    private void reportFailures(MatchResult result) {
        String failureMsg = new FailureMessageBuilder(result).build();
        String compilationOutput = new CompilationOutputBuilder(result).build();
        throw new IRViolationException(failureMsg, compilationOutput);
    }
}

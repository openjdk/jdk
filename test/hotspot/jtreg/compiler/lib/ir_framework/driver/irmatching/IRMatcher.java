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

import compiler.lib.ir_framework.driver.irmatching.parser.MethodCompilationParser;
import compiler.lib.ir_framework.driver.irmatching.report.CompilationOutputBuilder;
import compiler.lib.ir_framework.driver.irmatching.report.FailureMessageBuilder;

/**
 * This class performs IR matching on the prepared {@link TestClass} object parsed by {@link MethodCompilationParser}.
 * All applicable @IR rules are matched with all their defined compilation phases. If there are any IR matching failures,
 * an {@link IRViolationException} is reported which provides a formatted failure message and the compilation outputs
 * of the failed compilation phases.
 */
public class IRMatcher {
    public static final String SAFEPOINT_WHILE_PRINTING_MESSAGE = "<!-- safepoint while printing -->";
    private final Matchable testClass;

    public IRMatcher(Matchable testClass) {
        this.testClass = testClass;
    }

    /**
     * Do an IR matching of all methods with applicable @IR rules prepared with by the {@link MethodCompilationParser}.
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
        String compilationOutput =  new CompilationOutputBuilder(result).build();
        throwIfNoSafepointWhilePrinting(failureMsg, compilationOutput);
    }

    /**
     * In some very rare cases, the hotspot_pid* file to IR match on contains "<!-- safepoint while printing -->"
     * (emitted by ttyLocker::break_tty_for_safepoint) which might be the reason for a matching error.
     * Do not throw an exception in this case (i.e. bailout).
     */
    private void throwIfNoSafepointWhilePrinting(String failures, String compilations) {
        if (!compilations.contains(SAFEPOINT_WHILE_PRINTING_MESSAGE)) {
            throw new IRViolationException(failures, compilations);
        } else {
            System.out.println("Found " + SAFEPOINT_WHILE_PRINTING_MESSAGE + ", bail out of IR matching");
        }
    }
}

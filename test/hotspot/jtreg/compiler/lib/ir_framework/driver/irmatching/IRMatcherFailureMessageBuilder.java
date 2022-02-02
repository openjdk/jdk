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

package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;

import java.util.List;

/**
 * Class to build the failure message output of IR matching failures.
 *
 * @see IRMethodMatchResult
 */
class IRMatcherFailureMessageBuilder {

    public static String build(List<IRMethodMatchResult> results) {
        StringBuilder failuresBuilder = new StringBuilder();
        failuresBuilder.append(buildHeaderMessage(results));
        int failureNumber = 1;
        for (IRMethodMatchResult irMethodResult : results) {
            if (irMethodResult.fail()) {
                failuresBuilder.append(buildIRMethodFailureMessage(failureNumber, irMethodResult));
                failureNumber++;
            }
        }
        failuresBuilder.append(buildFooterMessage());
        return failuresBuilder.toString();
    }

    private static String buildHeaderMessage(List<IRMethodMatchResult> results) {
        int failedIRRulesCount = getFailedIRRulesCount(results);
        long failedMethodCount = getFailedMethodCount(results);
        return "One or more @IR rules failed:" + System.lineSeparator() + System.lineSeparator()
               + "Failed IR Rules (" + failedIRRulesCount + ") of Methods (" + failedMethodCount + ")"
               + System.lineSeparator()
               +  "-".repeat(32 + digitCount(failedIRRulesCount) + digitCount(failedMethodCount))
               + System.lineSeparator();
    }

    private static int getFailedIRRulesCount(List<IRMethodMatchResult> results) {
        return results.stream().map(IRMethodMatchResult::getFailedIRRuleCount).reduce(0, Integer::sum);
    }

    private static long getFailedMethodCount(List<IRMethodMatchResult> results) {
        return results.stream().filter(IRMethodMatchResult::fail).count();
    }

    private static int digitCount(long digit) {
        return String.valueOf(digit).length();
    }

    private static String buildIRMethodFailureMessage(int failureNumber, IRMethodMatchResult result) {
        return failureNumber + ")" + result.buildFailureMessage() + System.lineSeparator();
    }

    private static String buildFooterMessage() {
        return ">>> Check stdout for compilation output of the failed methods" + System.lineSeparator() + System.lineSeparator();
    }

}

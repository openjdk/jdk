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

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.IRMethodParser;

import java.util.*;

/**
 * This class parses the hotspot_pid* file of the test VM to match all applicable @IR rules afterwards.
 */
public class IRMatcher {
    public static final String SAFEPOINT_WHILE_PRINTING_MESSAGE = "<!-- safepoint while printing -->";

    public IRMatcher(String hotspotPidFileName, String irEncoding, Class<?> testClass) {
        IRMethodParser irMethodParser = new IRMethodParser(testClass);
        Collection<IRMethod> irMethods = irMethodParser.parse(hotspotPidFileName, irEncoding);
        if (irMethods != null) {
            applyIRRules(irMethods);
        }
    }

    /**
     * Do an IR matching of all methods with applicable @IR rules prepared with by the {@link IRMethodParser}.
     */
    private void applyIRRules(Collection<IRMethod> irMethods) {
        List<IRMethodMatchResult> results = new ArrayList<>();
        irMethods.forEach(irMethod -> applyIRRule(irMethod, results));
        if (!results.isEmpty()) {
            reportFailures(results);
        }
    }

    private void applyIRRule(IRMethod irMethod, List<IRMethodMatchResult> results) {
        if (TestFramework.VERBOSE) {
            printMethodOutput(irMethod);
        }
        IRMethodMatchResult result = irMethod.applyIRRules();
        if (result.fail()) {
            results.add(result);
        }
    }

    private void printMethodOutput(IRMethod irMethod) {
        System.out.println("Output of " + irMethod.getOutput() + ":");
        System.out.println(irMethod.getOutput());
    }

    /**
     * Report all IR violations in a pretty format to the user. Depending on the failed regex, we only report
     * PrintIdeal or PrintOptoAssembly if the match failed there. If there were failures that matched things
     * in both outputs then the entire output is reported. Throws IRViolationException from which the compilation
     * can be read and reported to the stdout separately. The exception message only includes the summary of the
     * failures.
     */
    private void reportFailures(List<IRMethodMatchResult> results) {
        Collections.sort(results); // Alphabetically
        throwIfNoSafepointWhilePrinting(IRMatcherFailureMessageBuilder.build(results),
                                        CompilationOutputBuilder.build(results));
    }

    // In some very rare cases, the VM output to regex match on contains "<!-- safepoint while printing -->"
    // (emitted by ttyLocker::break_tty_for_safepoint) which might be the reason for a matching error.
    // Do not throw an exception in this case (i.e. bailout).
    private void throwIfNoSafepointWhilePrinting(String failures, String compilations) {
        if (!compilations.contains(SAFEPOINT_WHILE_PRINTING_MESSAGE)) {
            throw new IRViolationException(failures, compilations);
        } else {
            System.out.println("Found " + SAFEPOINT_WHILE_PRINTING_MESSAGE + ", bail out of IR matching");
        }
    }
}

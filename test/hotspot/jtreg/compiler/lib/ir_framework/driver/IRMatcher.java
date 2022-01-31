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

package compiler.lib.ir_framework.driver;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.shared.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse the hotspot pid file of the test VM to match all @IR rules.
 */
public class IRMatcher {
    public static final String SAFEPOINT_WHILE_PRINTING_MESSAGE = "<!-- safepoint while printing -->";

    private final Map<String, IRMethod> compilations;
    private final Map<IRMethod, List<String>> fails;

    public IRMatcher(String hotspotPidFileName, String irEncoding, Class<?> testClass) {
        this.fails = new HashMap<>();
        this.compilations = new IREncodingParser(irEncoding, testClass).getCompilations();

        if (!compilations.isEmpty()) {
            HotSpotPidFileParser parser = new HotSpotPidFileParser(compilations, testClass.getCanonicalName());
            parser.parseCompilations(hotspotPidFileName);
            applyRules();
        }
    }

    /**
     * Do an IR matching of all methods with applicable @IR rules fetched during parsing of the hotspot pid file.
     */
    private void applyRules() {
        compilations.values().forEach(this::applyRulesForMethod);
        reportFailuresIfAny();
    }

    private void applyRulesForMethod(IRMethod irMethod) {
        String testOutput = irMethod.getOutput();
        if (testOutput.isEmpty()) {
            String msg = "Method was not compiled. Did you specify any compiler directives preventing a compilation or used a " +
                         "@Run method in STANDALONE mode? In the latter case, make sure to always trigger a C2 compilation " +
                         "by invoking the test enough times.";
            fails.computeIfAbsent(irMethod, k -> new ArrayList<>()).add(msg);
            return;
        }

        if (TestFramework.VERBOSE) {
            System.out.println("Output of " + irMethod.getOutput() + ":");
            System.out.println(testOutput);
        }
        List<MatchResult> matchResults = irMethod.applyIRRules();
        reportFailures(matchResults, irMethod);
    }

    private void reportFailures(List<MatchResult> results, IRMethod irMethod) {
        for (MatchResult result : results) {
            if (result.fail()) {
                String failMessage = "@IR rule " + result.getIRRule().getRuleId() + ": \"" + result.getIRRule().getIRAnno()
                                     + "\"" + System.lineSeparator()
                                     + buildFailureMessage(result);
                fails.computeIfAbsent(irMethod, k -> new ArrayList<>()).add(failMessage);
            }
        }
    }
    private String buildFailureMessage(MatchResult result) {
        StringBuilder failMsg = new StringBuilder();
        if (result.hasFailOnFailures()) {
            failMsg.append("- failOn: Graph contains forbidden nodes:").append(System.lineSeparator());
            failMsg.append(getFormattedFailureMessage(result.getFailOnFailures()));
        }
        if (result.hasCountsFailures()) {
            failMsg.append("- counts: Graph contains wrong number of nodes:").append(System.lineSeparator());
            failMsg.append(getFormattedFailureMessage(result.getCountsFailures()));
        }
        return failMsg.toString();
    }

    private String getFormattedFailureMessage(List<? extends Failure> failures) {
        StringBuilder builder = new StringBuilder();
        for (Failure failure : failures) {
            builder.append(failure.getFormattedFailureMessage());
        }
        return builder.toString();
    }

    /**
     * Report all IR violations in a pretty format to the user. Depending on the failed regex, we only report
     * PrintIdeal or PrintOptoAssembly if the match failed there. If there were failures that matched things
     * in both outputs than the entire output is reported. Throws IRViolationException from which the compilation
     * can be read and reported to the stdout separately. The exception message only includes the summary of the
     * failures.
     */
    private void reportFailuresIfAny() {
//        TestFormat.throwIfAnyFailures();
        if (!fails.isEmpty()) {
            StringBuilder failuresBuilder = new StringBuilder();
            StringBuilder compilationsBuilder = new StringBuilder();
            int failures = 0;
            for (Map.Entry<IRMethod, List<String>> entry : fails.entrySet()) {
                Method method = entry.getKey().getMethod();
                compilationsBuilder.append(">>> Compilation of ").append(method).append(":").append(System.lineSeparator());
                IRMethod irMethod = compilations.get(method.getName());
                String output;
                if (irMethod.usesIdeal() && irMethod.usesOptoAssembly()) {
                    output = irMethod.getOutput();
                } else if (irMethod.usesIdeal()) {
                    output = irMethod.getIdealOutput();
                } else if (irMethod.usesOptoAssembly()) {
                    output = irMethod.getOptoAssemblyOutput();
                } else {
                    output = "<empty>";
                }
                compilationsBuilder.append(output).append(System.lineSeparator()).append(System.lineSeparator());
                List<String> list = entry.getValue();
                failuresBuilder.append("- Method \"").append(method).append("\":").append(System.lineSeparator());
                failures += list.size();
                list.forEach(s -> failuresBuilder.append("  * ")
                                                 .append(s.replace(System.lineSeparator(),
                                                                   System.lineSeparator() + "    ").trim())
                                                 .append(System.lineSeparator()));
                failuresBuilder.append(System.lineSeparator());
            }
            failuresBuilder.insert(0, ("One or more @IR rules failed:" + System.lineSeparator()
                                       + System.lineSeparator() + "Failed IR Rules (" + failures + ")"
                                       + System.lineSeparator()) + "-----------------"
                                       + "-".repeat(String.valueOf(failures).length()) + System.lineSeparator());
            failuresBuilder.append(">>> Check stdout for compilation output of the failed methods")
                           .append(System.lineSeparator()).append(System.lineSeparator());

            // In some very rare cases, the VM output to regex match on contains "<!-- safepoint while printing -->"
            // (emitted by ttyLocker::break_tty_for_safepoint) which might be the reason for a matching error.
            // Do not throw an exception in this case (i.e. bailout).
            String compilations = compilationsBuilder.toString();
            if (!compilations.contains(SAFEPOINT_WHILE_PRINTING_MESSAGE)) {
                throw new IRViolationException(failuresBuilder.toString(), compilations);
            } else {
                System.out.println("Found " + SAFEPOINT_WHILE_PRINTING_MESSAGE + ", bail out of IR matching");
            }
        }
    }
}

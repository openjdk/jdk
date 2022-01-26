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
    private final Map<Method, List<String>> fails;

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
            fails.computeIfAbsent(irMethod.getMethod(), k -> new ArrayList<>()).add(msg);
            return;
        }

        if (TestFramework.VERBOSE) {
            System.out.println("Output of " + irMethod.getOutput() + ":");
            System.out.println(testOutput);
        }
        irMethod.getIRRules().forEach(this::applyIRRule);
    }

    /**
     * Apply a single @IR rule as part of a method.
     */
    private void applyIRRule(IRRule irRule) {
        applyFailOn(irRule);
        try {
            applyCounts(irRule);
        } catch (TestFormatException e) {
            // Logged. Continue to check other rules.
        }
        if (irRule.hasFailures()) {
            // TODO: We can do better instead of using 'fails' like this
            fails.computeIfAbsent(irRule.getMethod(), k -> new ArrayList<>()).add(irRule.getFailureMessage());
        }
    }

    /**
     * Apply the failOn regexes of the @IR rule.
     */
    private void applyFailOn(IRRule irRule) {
        IR irAnno = irRule.getIRAnno();
        if (irAnno.failOn().length != 0) {
            String failOnRegex = String.join("|", IRNode.mergeNodes(irAnno.failOn()));
            Pattern pattern = Pattern.compile(failOnRegex);
            Matcher matcher = pattern.matcher(irRule.getIRMethod().getOutput());
            long matchCount = matcher.results().count();
            if (matchCount > 0) {
                addFailOnFailsForOutput(pattern, matchCount, irRule);
            }
        }
    }

    /**
     * A failOn regex failed. Apply all regexes again to log the exact regex which failed. The failure is later reported
     * to the user.
     */
    private void addFailOnFailsForOutput(Pattern pattern, long matchCount, IRRule irRule) {
        IRMethod irMethod = irRule.getIRMethod();
        long idealCount = pattern.matcher(irMethod.getIdealOutput()).results().count();
        long optoAssemblyCount = pattern.matcher(irMethod.getOptoAssemblyOutput()).results().count();
        if (matchCount != idealCount + optoAssemblyCount || (idealCount != 0 && optoAssemblyCount != 0)) {
            // Report with Ideal and Opto Assembly
            addFailOnFailsForOutput(irMethod.getOutput(), irRule);
            irMethod.needsAllOutput();
        } else if (optoAssemblyCount == 0) {
            // Report with Ideal only
            addFailOnFailsForOutput(irMethod.getIdealOutput(), irRule);
            irMethod.needsIdeal();
        } else {
            // Report with Opto Assembly only
            addFailOnFailsForOutput(irMethod.getOptoAssemblyOutput(), irRule);
            irMethod.needsOptoAssembly();
        }
    }

    /**
     * Apply the regexes to the testOutput and log the failures.
     */
    private void addFailOnFailsForOutput(String testOutput, IRRule irRule) {
        List<String> failOnNodes = IRNode.mergeNodes(irRule.getIRAnno().failOn());
        Pattern pattern;
        Matcher matcher;
        StringBuilder failMsg = new StringBuilder();
        failMsg.append("- failOn: Graph contains forbidden nodes:").append(System.lineSeparator());
        int nodeId = 1;
        for (String nodeRegex : failOnNodes) {
            pattern = Pattern.compile(nodeRegex);
            matcher = pattern.matcher(testOutput);
            long matchCount = matcher.results().count();
            if (matchCount > 0) {
                matcher.reset();
                failMsg.append("    Regex ").append(nodeId).append(": ").append(nodeRegex).append(System.lineSeparator());
                failMsg.append("    Matched forbidden node").append(matchCount > 1 ? "s (" + matchCount + ")" : "")
                       .append(":").append(System.lineSeparator());
                matcher.results().forEach(r -> failMsg.append("      ").append(r.group()).append(System.lineSeparator()));
            }
            nodeId++;
        }
        irRule.appendToFailMsg(failMsg.toString());
    }

    /**
     * Apply the counts regexes of the @IR rule.
     */
    private void applyCounts(IRRule irRule) {
        IR irAnno = irRule.getIRAnno();
        if (irAnno.counts().length != 0) {
            IRMethod irMethod = irRule.getIRMethod();
            String testOutput = irMethod.getOutput();
            int countsId = 1;
            StringBuilder failMsg = new StringBuilder();
            final List<String> nodesWithCount = IRNode.mergeNodes(irAnno.counts());
            for (int i = 0; i < nodesWithCount.size(); i += 2) {
                String node = nodesWithCount.get(i);
                TestFormat.check(i + 1 < nodesWithCount.size(), "Missing count" + getPostfixErrorMsg(irRule, node));
                String countString = nodesWithCount.get(i + 1);
                long givenCount;
                ParsedComparator<Long> parsedComparator = getParsedComparator(irRule, node, countString);
                givenCount = parseExpectedCount(irRule, node, countString, parsedComparator.getNumberString());

                long actualCount = getActualCount(testOutput, node);
                if (!parsedComparator.compare(actualCount, givenCount)) {
                    appendSummary(failMsg, countsId, node, givenCount, actualCount);
                    addCountsFail(failMsg, irMethod, node, actualCount);
                }
                countsId++;
            }
            if (!failMsg.isEmpty()) {
                irRule.appendToFailMsg("- counts: Graph contains wrong number of nodes:").append(System.lineSeparator())
                      .append(failMsg);
            }
        }
    }

    private void appendSummary(StringBuilder failMsg, int countsId, String node, long givenCount, long actualCount) {
        failMsg.append("    Regex ").append(countsId).append(": ").append(node).append(System.lineSeparator());
        failMsg.append("    Expected ").append(givenCount).append(" but found ").append(actualCount);
    }

    private long getActualCount(String testOutput, String node) {
        Pattern pattern = Pattern.compile(node);
        Matcher matcher = pattern.matcher(testOutput);
        return matcher.results().count();
    }

    private long parseExpectedCount(IRRule irRule, String node, String countString, String expectedCountString) {
        try {
            long expectedCount = Long.parseLong(expectedCountString);
            TestFormat.check(expectedCount >= 0, "Provided invalid negative count \"" + countString
                                                 + "\"" + getPostfixErrorMsg(irRule, node));
            return expectedCount;
        } catch (NumberFormatException e) {
            TestFormat.fail("Provided invalid count \"" + countString + "\"" + getPostfixErrorMsg(irRule, node));
            throw new UnreachableCodeException();
        }
    }

    private ParsedComparator<Long> getParsedComparator(IRRule irRule, String node, String countString) {
        try {
            return ParsedComparator.parseComparator(countString);
        } catch (CheckedTestFrameworkException e) {
            TestFormat.fail("Invalid comparator \"" + e.getMessage() + "\" in \"" + countString
                            + "\" for count" + getPostfixErrorMsg(irRule, node));
            throw new UnreachableCodeException();
        }  catch (IndexOutOfBoundsException e) {
            TestFormat.fail("Provided empty value" + getPostfixErrorMsg(irRule, node));
            throw new UnreachableCodeException();
        }
    }

    private String getPostfixErrorMsg(IRRule irRule, String node) {
        return " for IR rule " + (irRule.getRuleId() + 1) + ", node \"" + node + "\" at " + irRule.getMethod();
    }

    /**
     * A counts regex failed. Apply all regexes again to log the exact regex which failed. The failure is later reported
     * to the user.
     */
    private void addCountsFail(StringBuilder failMsg, IRMethod irMethod, String node, long actualCount) {
        if (actualCount > 0) {
            Pattern pattern = Pattern.compile(node);
            Matcher matcher = pattern.matcher(irMethod.getOutput());
            long idealCount = pattern.matcher(irMethod.getIdealOutput()).results().count();
            long optoAssemblyCount = pattern.matcher(irMethod.getOptoAssemblyOutput()).results().count();
            if (actualCount != idealCount + optoAssemblyCount || (idealCount != 0 && optoAssemblyCount != 0)) {
                irMethod.needsAllOutput();
            } else if (optoAssemblyCount == 0) {
                irMethod.needsIdeal();
            } else {
                irMethod.needsOptoAssembly();
            }
            failMsg.append(" node").append(actualCount > 1 ? "s" : "").append(":").append(System.lineSeparator());
            matcher.results().forEach(r -> failMsg.append("      ").append(r.group()).append(System.lineSeparator()));
        } else {
            irMethod.needsAllOutput();
            failMsg.append(" nodes.").append(System.lineSeparator());
        }
    }

    /**
     * Report all IR violations in a pretty format to the user. Depending on the failed regex, we only report
     * PrintIdeal or PrintOptoAssembly if the match failed there. If there were failures that matched things
     * in both outputs than the entire output is reported. Throws IRViolationException from which the compilation
     * can be read and reported to the stdout separately. The exception message only includes the summary of the
     * failures.
     */
    private void reportFailuresIfAny() {
        TestFormat.throwIfAnyFailures();
        if (!fails.isEmpty()) {
            StringBuilder failuresBuilder = new StringBuilder();
            StringBuilder compilationsBuilder = new StringBuilder();
            int failures = 0;
            for (Map.Entry<Method, List<String>> entry : fails.entrySet()) {
                Method method = entry.getKey();
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

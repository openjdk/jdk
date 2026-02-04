/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.HotSpotPidFileParser;
import compiler.lib.ir_framework.driver.network.testvm.java.IRRuleIds;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.test.ApplicableIRRulesPrinter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse the Applicable IR Rules emitted by the Test VM and creating {@link TestMethod} objects for each entry.
 *
 * @see TestMethod
 */
public class ApplicableIRRulesParser {

    private static final boolean PRINT_APPLICABLE_IR_RULES = Boolean.parseBoolean(System.getProperty("PrintApplicableIRRules", "false"));
    private static final Pattern APPLICABLE_IR_RULES_PATTERN =
            Pattern.compile("(?<=" + ApplicableIRRulesPrinter.START + "\r?\n).*\\R([\\s\\S]*)(?=" + ApplicableIRRulesPrinter.END + ")");

    private final Map<String, TestMethod> testMethods;
    private final Class<?> testClass;

    public ApplicableIRRulesParser(Class<?> testClass) {
        this.testClass = testClass;
        this.testMethods = new HashMap<>();
    }

    /**
     * Parse the Applicable IR rules passed as parameter and return a "test name" -> TestMethod map that contains an
     * entry for each method that needs to be IR matched on.
     */
    public TestMethods parse(String applicableIRRules) {
        if (TestFramework.VERBOSE || PRINT_APPLICABLE_IR_RULES) {
            System.out.println("Read Applicable IR Rules from Test VM:");
            System.out.println(applicableIRRules);
        }
        createTestMethodMap(applicableIRRules, testClass);
        // We could have found format errors in @IR annotations. Report them now with an exception.
        TestFormat.throwIfAnyFailures();
        return new TestMethods(testMethods);
    }

    /**
     * Sets up a map testname -> TestMethod map. The TestMethod object will later be filled with the ideal and opto
     * assembly output in {@link HotSpotPidFileParser}.
     */
    private void createTestMethodMap(String applicableIRRules, Class<?> testClass) {
        Map<String, IRRuleIds> irRulesMap = parseApplicableIRRules(applicableIRRules);
        createTestMethodsWithApplicableIRRules(testClass, irRulesMap);
    }

    /**
     * Read the Applicable IR Rules emitted by the Test VM to decide if an @IR rule must be checked for a method.
     */
    private Map<String, IRRuleIds> parseApplicableIRRules(String applicableIRRules) {
        Map<String, IRRuleIds> irRulesMap = new HashMap<>();
        String[] applicableIRRulesLines = getApplicableIRRulesLines(applicableIRRules);
        for (String s : applicableIRRulesLines) {
            String line = s.trim();
            String[] splitLine = line.split(",");
            if (splitLine.length < 2) {
                throw new TestFrameworkException("Invalid Applicable IR Rules format. No comma found: " + splitLine[0]);
            }
            String testName = splitLine[0];
            IRRuleIds irRuleIds = parseIrRulesIds(splitLine);
            irRulesMap.put(testName, irRuleIds);
        }
        return irRulesMap;
    }

    /**
     * Parse the Applicable IR Rules lines without header, explanation line and footer and return them in an array.
     */
    private String[] getApplicableIRRulesLines(String applicableIRRules) {
        Matcher matcher = APPLICABLE_IR_RULES_PATTERN.matcher(applicableIRRules);
        TestFramework.check(matcher.find(), "Did not find Applicable IR Rules in:" +
                System.lineSeparator() + applicableIRRules);
        String lines = matcher.group(1).trim();
        if (lines.isEmpty()) {
            // Nothing to IR match.
            return new String[0];
        }
        return lines.split("\\R");
    }

    /**
     * Parse rule indexes from a single line of the Applicable IR Rules in the format: <method,idx1,idx2,...>
     */
    private IRRuleIds parseIrRulesIds(String[] splitLine) {
        List<Integer> irRuleIds = new ArrayList<>();
        for (int i = 1; i < splitLine.length; i++) {
            try {
                irRuleIds.add(Integer.parseInt(splitLine[i]));
            } catch (NumberFormatException e) {
                throw new TestFrameworkException("Invalid Applicable IR Rules format. No number found: " + splitLine[i]);
            }
        }
        return new IRRuleIds(irRuleIds);
    }

    private void createTestMethodsWithApplicableIRRules(Class<?> testClass, Map<String, IRRuleIds> irRulesMap) {
        for (Method m : testClass.getDeclaredMethods()) {
            IR[] irAnnos = m.getAnnotationsByType(IR.class);
            if (irAnnos.length > 0) {
                // Validation of legal @IR attributes and placement of the annotation was already done in Test VM.
                IRRuleIds irRuleIds = irRulesMap.get(m.getName());
                validateIRRuleIds(m, irAnnos, irRuleIds);
                if (hasAnyApplicableIRRules(irRuleIds)) {
                    testMethods.put(m.getName(), new TestMethod(m, irAnnos, irRuleIds));
                }
            }
        }
    }

    private void validateIRRuleIds(Method m, IR[] irAnnos, IRRuleIds irRuleIds) {
        TestFramework.check(irRuleIds != null, "Should find method name in validIrRulesMap for " + m);
        TestFramework.check(!irRuleIds.isEmpty(), "Did not find any rule indices for " + m);
        TestFramework.check((irRuleIds.first() >= 1 || irRuleIds.first() == ApplicableIRRulesPrinter.NO_RULE_APPLIED)
                            && irRuleIds.last() <= irAnnos.length,
                            "Invalid IR rule index found in validIrRulesMap for " + m);
    }

    /**
     * Does the list of IR rules contain any applicable IR rules for the given conditions?
     */
    private boolean hasAnyApplicableIRRules(IRRuleIds irRuleIds) {
        return irRuleIds.first() != ApplicableIRRulesPrinter.NO_RULE_APPLIED;
    }
}

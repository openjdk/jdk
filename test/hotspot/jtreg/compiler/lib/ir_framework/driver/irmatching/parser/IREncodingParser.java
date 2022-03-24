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

package compiler.lib.ir_framework.driver.irmatching.parser;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.test.IREncodingPrinter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse the IR encoding emitted by the test VM and creating {@link IRMethod} objects for each entry.
 *
 * @see IRMethod
 */
class IREncodingParser {

    private static final boolean PRINT_IR_ENCODING = Boolean.parseBoolean(System.getProperty("PrintIREncoding", "false"));
    private static final Pattern IR_ENCODING_PATTERN =
            Pattern.compile("(?<=" + IREncodingPrinter.START + "\r?\n).*\\R([\\s\\S]*)(?=" + IREncodingPrinter.END + ")");

    private final Map<String, IRMethod> compilations;
    private final Class<?> testClass;

    public IREncodingParser(Class<?> testClass) {
        this.testClass = testClass;
        this.compilations = new HashMap<>();
    }

    public Map<String, IRMethod> parseIRMethods(String irEncoding) {
        if (TestFramework.VERBOSE || PRINT_IR_ENCODING) {
            System.out.println("Read IR encoding from test VM:");
            System.out.println(irEncoding);
        }
        createCompilationsMap(irEncoding, testClass);
        // We could have found format errors in @IR annotations. Report them now with an exception.
        TestFormat.throwIfAnyFailures();
        return compilations;
    }

    /**
     * Sets up a map testname -> IRMethod (containing the PrintIdeal and PrintOptoAssembly output for testname).
     */
    private void createCompilationsMap(String irEncoding, Class<?> testClass) {
        Map<String, int[]> irRulesMap = parseIREncoding(irEncoding);
        createIRMethodsWithEncoding(testClass, irRulesMap);
    }

    /**
     * Read the IR encoding emitted by the test VM to decide if an @IR rule must be checked for a method.
     */
    private Map<String, int[]> parseIREncoding(String irEncoding) {
        Map<String, int[]> irRulesMap = new HashMap<>();
        String[] irEncodingLines = getIREncodingLines(irEncoding);
        for (String s : irEncodingLines) {
            String line = s.trim();
            String[] splitLine = line.split(",");
            if (splitLine.length < 2) {
                throw new TestFrameworkException("Invalid IR match rule encoding. No comma found: " + splitLine[0]);
            }
            String testName = splitLine[0];
            int[] irRulesIdx = getRuleIndexes(splitLine);
            irRulesMap.put(testName, irRulesIdx);
        }
        return irRulesMap;
    }

    /**
     * Parse the IR encoding lines without header, explanation line and footer and return them in an array.
     */
    private String[] getIREncodingLines(String irEncoding) {
        Matcher matcher = IR_ENCODING_PATTERN.matcher(irEncoding);
        TestFramework.check(matcher.find(), "Did not find IR encoding");
        String lines = matcher.group(1).trim();
        if (lines.isEmpty()) {
            // Nothing to IR match.
            return new String[0];
        }
        return lines.split("\\R");
    }

    /**
     * Parse rule indexes from IR encoding line of the format: <method,idx1,idx2,...>
     */
    private int[] getRuleIndexes(String[] splitLine) {
        int[] irRulesIdx = new int[splitLine.length - 1];
        for (int i = 1; i < splitLine.length; i++) {
            try {
                irRulesIdx[i - 1] = Integer.parseInt(splitLine[i]);
            } catch (NumberFormatException e) {
                throw new TestFrameworkException("Invalid IR match rule encoding. No number found: " + splitLine[i]);
            }
        }
        return irRulesIdx;
    }

    private void createIRMethodsWithEncoding(Class<?> testClass, Map<String, int[]> irRulesMap) {
        for (Method m : testClass.getDeclaredMethods()) {
            IR[] irAnnos = m.getAnnotationsByType(IR.class);
            if (irAnnos.length > 0) {
                // Validation of legal @IR attributes and placement of the annotation was already done in Test VM.
                int[] irRuleIds = irRulesMap.get(m.getName());
                validateIRRuleIds(m, irAnnos, irRuleIds);
                if (hasAnyApplicableIRRules(irRuleIds)) {
                    compilations.put(m.getName(), new IRMethod(m, irRuleIds, irAnnos));
                }
            }
        }
    }

    private void validateIRRuleIds(Method m, IR[] irAnnos, int[] ids) {
        TestFramework.check(ids != null, "Should find method name in validIrRulesMap for " + m);
        TestFramework.check(ids.length > 0, "Did not find any rule indices for " + m);
        TestFramework.check((ids[0] >= 1 || ids[0] == IREncodingPrinter.NO_RULE_APPLIED)
                            && ids[ids.length - 1] <= irAnnos.length,
                            "Invalid IR rule index found in validIrRulesMap for " + m);
    }

    /**
     * Does the list of IR rules contain any applicable IR rules for the given conditions?
     */
    private boolean hasAnyApplicableIRRules(int[] irRuleIds) {
        return irRuleIds[0] != IREncodingPrinter.NO_RULE_APPLIED;
    }
}

/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.hotspot.ir_framework;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse the hotspot pid file of the test VM to match all @IR rules.
 */
class IRMatcher {
    private static final boolean PRINT_IR_ENCODING = Boolean.parseBoolean(System.getProperty("PrintIREncoding", "false"));
    private final Map<String, IRMethod> compilations;
    private final Class<?> testClass;
    private final Map<Method, List<String>> fails;
    private final String hotspotPidFileName;
    private IRMethod irMethod; // Current IR method to which rules are applied
    private Method method; // Current method to which rules are applied
    private IR irAnno; // Current IR annotation that is processed.
    private int irRuleIndex; // Current IR rule index;

    public IRMatcher(String hotspotPidFileName, String irEncoding, Class<?> testClass) {
        this.compilations =  new LinkedHashMap<>();
        this.fails = new HashMap<>();
        this.testClass = testClass;
        this.hotspotPidFileName = hotspotPidFileName;
        setupTestMethods(irEncoding);
        if (TestFramework.VERBOSE || PRINT_IR_ENCODING) {
            System.out.println("Read IR encoding from test VM:");
            System.out.println(irEncoding);
        }
        parseHotspotPidFile();
        applyRules();
    }

    /**
     * Sets up a map testname -> IRMethod (containing the PrintIdeal and PrintOptoAssembly output for testname).
     */
    private void setupTestMethods(String irEncoding) {
        Map<String, Integer[]> irRulesMap = parseIREncoding(irEncoding);
        for (Method m : testClass.getDeclaredMethods()) {
            method = m;
            IR[] irAnnos =  m.getAnnotationsByType(IR.class);
            if (irAnnos.length > 0) {
                // Validation of legal @IR attributes and placement of the annotation was already done in Test VM.
                Integer[] ids = irRulesMap.get(m.getName());
                TestFramework.check(ids != null, "Should find method name in validIrRulesMap for " + m);
                TestFramework.check(ids.length > 0, "Did not find any rule indices for " + m);
                TestFramework.check(ids[ids.length - 1] < irAnnos.length, "Invalid IR rule index found in validIrRulesMap for " + m);
                if (ids[0] != IREncodingPrinter.NO_RULE_APPLIED) {
                    // If -1, than there was no matching IR rule for the given conditions.
                    compilations.put(m.getName(), new IRMethod(m, ids, irAnnos));
                }
            }
        }
    }

    /**
     * Read the IR encoding emitted by the test VM to decide if an @IR rule must be checked for a method.
     */
    private Map<String, Integer[]>  parseIREncoding(String irEncoding) {
        Map<String, Integer[]> irRulesMap = new HashMap<>();
        String patternString = "(?<=" + IREncodingPrinter.START + "\\R)[\\s\\S]*(?=" + IREncodingPrinter.END + ")";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(irEncoding);
        TestFramework.check(matcher.find(), "Did not find IR encoding");
        String[] lines = matcher.group(0).split("\\R");

        // Skip first line containing information about the format only
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            String[] splitComma = line.split(",");
            if (splitComma.length < 2) {
                throw new TestFrameworkException("Invalid IR match rule encoding");
            }
            String testName = splitComma[0];
            Integer[] irRulesIdx = new Integer[splitComma.length - 1];
            for (int j = 1; j < splitComma.length; j++) {
                irRulesIdx[j - 1] = Integer.valueOf(splitComma[j]);
            }
            irRulesMap.put(testName, irRulesIdx);
        }
        return irRulesMap;
    }

    /**
     * Parse the hotspot_pid*.log file from the test VM. Read the PrintIdeal and PrintOptoAssembly entries for all
     * methods of the test class that need to be IR matched (according to IR encoding).
     */
    private void parseHotspotPidFile() {
        Map<Integer, String> compileIdMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(new File(System.getProperty("user.dir") + File.separator + hotspotPidFileName)))) {
            String line;
            StringBuilder builder = new StringBuilder();
            boolean append = false;
            String currentMethod = "";
            while ((line = br.readLine()) != null) {
                if (append && line.startsWith("</")) {
                    flushOutput(line, builder, currentMethod);
                    append = false;
                    currentMethod = "";
                    continue;
                } else if (append) {
                    appendLine(builder, line);
                    continue;
                }

                if (maybeTestEntry(line)) {
                    addTestMethodCompileId(compileIdMap, line);
                } else if (isPrintIdealStart(line)) {
                    String methodName = getMethodName(compileIdMap, line);
                    if (methodName != null) {
                        currentMethod = methodName;
                        append = true; // Append all following lines until we hit the closing </ideal> tag.
                    }
                } else if (isPrintOptoAssemblyStart(line)) {
                    String methodName = getMethodName(compileIdMap, line);
                    if (methodName != null) {
                        TestFramework.check(compilations.containsKey(methodName), "Must be second entry of " + methodName);
                        currentMethod = methodName;
                        append = true; // Append all following lines until we hit the closing </opto_assembly> tag.
                    }
                }
            }
        } catch (IOException e) {
            TestFramework.fail("Error while reading " + hotspotPidFileName, e);
        }
    }

    /**
     * Write the input to the IR method and reset the builder.
     */
    private void flushOutput(String line, StringBuilder builder, String currentMethod) {
        TestFramework.check(!currentMethod.isEmpty(), "current method must be set");
        IRMethod irMethod = compilations.get(currentMethod);
        if (line.startsWith("</i")) {
            // PrintIdeal
            irMethod.appendIdealOutput(builder.toString());
        } else {
            // PrintOptoAssembly
            irMethod.appendOptoAssemblyOutput(builder.toString());
        }
        builder.setLength(0);
    }

    /**
     * Only consider non-osr (no "compile_kind") and compilations with C2 (no "level")
     */
    private boolean maybeTestEntry(String line) {
        return line.startsWith("<task_queued") && !line.contains("compile_kind='") && !line.contains("level='");
    }

    /**
     * Need to escape XML special characters.
     */
    private static void appendLine(StringBuilder builder, String line) {
        if (line.contains("&")) {
            line = line.replace("&amp;", "&");
            line = line.replace("&lt;", "<");
            line = line.replace("&gt;", ">");
            line = line.replace("&quot;", "\"");
            line = line.replace("&apos;", "'");
        }
        builder.append(line).append("\n");
    }

    private static int getCompileId(Matcher matcher) {
        int compileId = -1;
        try {
            compileId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            TestRun.fail("Could not parse compile id", e);
        }
        return compileId;
    }

    /**
     * Parse the compile id from this line if it belongs to a method that needs to be IR tested (part of test class
     * and IR encoding from the test VM specifies that this method has @IR rules to be checked).
     */
    private void addTestMethodCompileId(Map<Integer, String> compileIdMap, String line) {
        Pattern pattern = Pattern.compile("compile_id='(\\d+)'.*" + Pattern.quote(testClass.getCanonicalName()) + " (\\S+)");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            // Only care about test class entries. Might have non-class entries as well if user specified additional
            // compile commands. Ignore these.
            String methodName = matcher.group(2);
            if (compilations.containsKey(methodName)) {
                // We only care about methods that we are actually gonna IR match based on IR encoding.
                int compileId = getCompileId(matcher);
                TestRun.check(!methodName.isEmpty(), "method name cannot be empty");
                compileIdMap.put(compileId, methodName);
            }
        }
    }

    /**
     * Make sure that line does not contain compile_kind which is used for OSR compilations which we are not
     * interested in.
     */
    private static boolean isPrintIdealStart(String line) {
        return line.startsWith("<ideal") && !line.contains("compile_kind='");
    }

    /**
     * Make sure that line does not contain compile_kind which is used for OSR compilations which we are not
     * interested in.
     */
    private static boolean isPrintOptoAssemblyStart(String line) {
        return line.startsWith("<opto_assembly") && !line.contains("compile_kind='");
    }

    /**
     * Get method name for this line by looking up the compile id.
     * Returns null if not an interesting method (i.e. from test class).
     */
    private String getMethodName(Map<Integer, String> compileIdMap, String line) {
        Pattern pattern = Pattern.compile("compile_id='(\\d+)'");
        Matcher matcher = pattern.matcher(line);
        TestFramework.check(matcher.find(), "Is " + hotspotPidFileName + " corrupted?");
        int compileId = getCompileId(matcher);
        return compileIdMap.get(compileId);
    }

    /**
     * Do an IR matching of all methods with appliable @IR rules fetched during parsing of the hotspot pid file.
     */
    private void applyRules() {
        compilations.values().forEach(this::applyRulesForMethod);
        reportFailuresIfAny();
    }

    private void applyRulesForMethod(IRMethod irMethod) {
        this.irMethod = irMethod;
        method = irMethod.getMethod();
        String testOutput = irMethod.getOutput();
        if (testOutput.isEmpty()) {
            String msg = "Method was not compiled. Did you specify any compiler directives preventing a compilation or used a " +
                         "@Run method in STANDALONE mode? In the latter case, make sure to always trigger a C2 compilation " +
                         "by invoking the test enough times.";
            fails.computeIfAbsent(method, k -> new ArrayList<>()).add(msg);
            return;
        }

        if (TestFramework.VERBOSE) {
            System.out.println("Output of " + method + ":");
            System.out.println(testOutput);
        }
        for (Integer id : irMethod.getRuleIds()) {
            applyIRRule(id);
        }
    }

    /**
     * Apply a single @IR rule as part of a method.
     */
    private void applyIRRule(Integer id) {
        irAnno = irMethod.getIrAnno(id);
        irRuleIndex = id;
        StringBuilder failMsg = new StringBuilder();
        applyFailOn(failMsg);
        try {
            applyCounts(failMsg);
        } catch (TestFormatException e) {
            // Logged. Continue to check other rules.
        }
        if (!failMsg.isEmpty()) {
            failMsg.insert(0, "@IR rule " + (id + 1) + ": \"" + irAnno + "\"\n");
            fails.computeIfAbsent(method, k -> new ArrayList<>()).add(failMsg.toString());
        }
    }

    /**
     * Apply the failOn regexes of the @IR rule.
     */
    private void applyFailOn(StringBuilder failMsg) {
        if (irAnno.failOn().length != 0) {
            String failOnRegex = String.join("|", IRNode.mergeNodes(irAnno.failOn()));
            Pattern pattern = Pattern.compile(failOnRegex);
            Matcher matcher = pattern.matcher(irMethod.getOutput());
            long matchCount = matcher.results().count();
            if (matchCount > 0) {
                addFailOnFailsForOutput(failMsg, pattern, matchCount);
            }
        }
    }

    /**
     * A failOn regex failed. Apply all regexes again to log the exact regex which failed. The failure is later reported
     * to the user.
     */
    private void addFailOnFailsForOutput(StringBuilder failMsg, Pattern pattern, long matchCount) {
        long idealCount = pattern.matcher(irMethod.getIdealOutput()).results().count();
        long optoAssemblyCount = pattern.matcher(irMethod.getOptoAssemblyOutput()).results().count();
        if (matchCount != idealCount + optoAssemblyCount || (idealCount != 0 && optoAssemblyCount != 0)) {
            // Report with Ideal and Opto Assembly
            addFailOnFailsForOutput(failMsg, irMethod.getOutput());
            irMethod.needsAllOutput();
        } else if (optoAssemblyCount == 0) {
            // Report with Ideal only
            addFailOnFailsForOutput(failMsg, irMethod.getIdealOutput());
            irMethod.needsIdeal();
        } else {
            // Report with Opto Assembly only
            addFailOnFailsForOutput(failMsg, irMethod.getOptoAssemblyOutput());
            irMethod.needsOptoAssembly();
        }
    }

    /**
     * Apply the regexes to the testOutput and log the failures.
     */
    private void addFailOnFailsForOutput(StringBuilder failMsg, String testOutput) {
        List<String> failOnNodes = IRNode.mergeNodes(irAnno.failOn());
        Pattern pattern;
        Matcher matcher;
        failMsg.append("- failOn: Graph contains forbidden nodes:\n");
        int nodeId = 1;
        for (String nodeRegex : failOnNodes) {
            pattern = Pattern.compile(nodeRegex);
            matcher = pattern.matcher(testOutput);
            long matchCount = matcher.results().count();
            if (matchCount > 0) {
                matcher.reset();
                failMsg.append("    Regex ").append(nodeId).append(") ").append(nodeRegex).append("\n");
                failMsg.append("    Matched forbidden node").append(matchCount > 1 ? "s (" + matchCount + ")" : "").append(":\n");
                matcher.results().forEach(r -> failMsg.append("      ").append(r.group()).append("\n"));
            }
            nodeId++;
        }
    }

    /**
     * Apply the counts regexes of the @IR rule.
     */
    private void applyCounts(StringBuilder failMsg) {
        if (irAnno.counts().length != 0) {
            boolean hasFails = false;
            String testOutput = irMethod.getOutput();
            int countsId = 1;
            final List<String> nodesWithCount = IRNode.mergeNodes(irAnno.counts());
            for (int i = 0; i < nodesWithCount.size(); i += 2) {
                String node = nodesWithCount.get(i);
                TestFormat.check(i + 1 < nodesWithCount.size(), "Missing count"  + getPostfixErrorMsg(node));
                String countString = nodesWithCount.get(i + 1);
                long expectedCount;
                ParsedComparator<Long> parsedComparator;
                try {
                    parsedComparator = ParsedComparator.parseComparator(countString);
                    expectedCount = Long.parseLong(parsedComparator.getStrippedString());
                } catch (NumberFormatException e) {
                    TestFormat.fail("Provided invalid count \"" + countString + "\"" + getPostfixErrorMsg(node));
                    return;
                } catch (CheckedTestFrameworkException e) {
                    TestFormat.fail("Invalid comparator \"" + e.getMessage() + "\" in \"" + countString + "\" for count" + getPostfixErrorMsg(node));
                    return;
                } catch (IndexOutOfBoundsException e) {
                    TestFormat.fail("Provided empty value" + getPostfixErrorMsg(node));
                    return;
                }
                TestFormat.check(expectedCount >= 0,"Provided invalid negative count \"" + countString + "\"" + getPostfixErrorMsg(node));

                Pattern pattern = Pattern.compile(node);
                Matcher matcher = pattern.matcher(testOutput);
                long actualCount = matcher.results().count();
                if (!parsedComparator.getPredicate().test(actualCount, expectedCount)) {
                    if (!hasFails) {
                        failMsg.append("- counts: Graph contains wrong number of nodes:\n");
                        hasFails = true;
                    }
                    addCountsFail(failMsg, node, pattern, expectedCount, actualCount, countsId);
                }
                countsId++;
            }
        }
    }

    private String getPostfixErrorMsg(String node) {
        return " for IR rule " + irRuleIndex + ", node \"" + node + "\" at " + method;
    }

    /**
     * A counts regex failed. Apply all regexes again to log the exact regex which failed. The failure is later reported
     * to the user.
     */
    private void addCountsFail(StringBuilder failMsg, String node, Pattern pattern, long expectedCount, long actualCount, int countsId) {
        failMsg.append("    Regex ").append(countsId).append(") ").append(node).append("\n");
        failMsg.append("    Expected ").append(expectedCount).append(" but found ").append(actualCount);

        if (actualCount > 0) {
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
            failMsg.append(" node").append(actualCount > 1 ? "s" : "").append(":\n");
            matcher.results().forEach(r -> failMsg.append("      ").append(r.group()).append("\n"));
        } else {
            irMethod.needsAllOutput();
            failMsg.append(" nodes.\n");
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
        TestFormat.reportIfAnyFailures();
        if (!fails.isEmpty()) {
            StringBuilder failuresBuilder = new StringBuilder();
            StringBuilder compilationsBuilder = new StringBuilder();
            int failures = 0;
            for (Map.Entry<Method, List<String>> entry : fails.entrySet()) {
                Method method = entry.getKey();
                compilationsBuilder.append(">>> Compilation of ").append(method).append(":\n");
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
                compilationsBuilder.append(output).append("\n\n");
                List<String> list = entry.getValue();
                failuresBuilder.append("- Method \"").append(method).append("\":\n");
                failures += list.size();
                list.forEach(s -> failuresBuilder.append("  * ").append(s.replace("\n", "\n    ").trim()).append("\n"));
                failuresBuilder.append("\n");
            }
            failuresBuilder.insert(0, ("\nOne or more @IR rules failed:\n\n" + "Failed IR Rules (" + failures + ")\n")
                              + "-----------------" + "-".repeat(String.valueOf(failures).length()) + "\n");
            failuresBuilder.append(">>> Check stdout for compilation output of the failed methods\n\n");
            throw new IRViolationException(failuresBuilder.toString(), compilationsBuilder.toString());
        }
    }
}

/**
 * Helper class to store information about a method that needs to be IR matched.
 */
class IRMethod {
    final private Method method;
    final private Integer[] ruleIds;
    final private IR[] irAnnos;
    final private StringBuilder outputBuilder;
    private String output;
    private String idealOutput;
    private String optoAssemblyOutput;
    private boolean needsIdeal;
    private boolean needsOptoAssembly;

    public IRMethod(Method method, Integer[] ruleIds, IR[] irAnnos) {
        this.method = method;
        this.ruleIds = ruleIds;
        this.irAnnos = irAnnos;
        this.outputBuilder = new StringBuilder();
        this.output = "";
        this.idealOutput = "";
        this.optoAssemblyOutput = "";
    }

    public Method getMethod() {
        return method;
    }

    public Integer[] getRuleIds() {
        return ruleIds;
    }

    public IR getIrAnno(int idx) {
        return irAnnos[idx];
    }

    /**
     * The Ideal output comes always before the Opto Assembly output. We might parse multiple C2 compilations of this method.
     * Only keep the very last one by overriding 'output'.
     */
    public void appendIdealOutput(String idealOutput) {
        outputBuilder.setLength(0);
        this.idealOutput = "PrintIdeal:\n" + idealOutput;
        outputBuilder.append(this.idealOutput);
    }

    /**
     * The Opto Assembly output comes after the Ideal output. Simply append to 'output'.
     */
    public void appendOptoAssemblyOutput(String optoAssemblyOutput) {
        this.optoAssemblyOutput = "PrintOptoAssembly:\n" + optoAssemblyOutput;
        outputBuilder.append("\n\n").append(this.optoAssemblyOutput);
        output = outputBuilder.toString();
    }

    public String getOutput() {
        return output;
    }

    public String getIdealOutput() {
        return idealOutput;
    }

    public String getOptoAssemblyOutput() {
        return optoAssemblyOutput;
    }

    public void needsAllOutput() {
        needsIdeal();
        needsOptoAssembly();
    }

    public void needsIdeal() {
        needsIdeal = true;
    }

    public boolean usesIdeal() {
        return needsIdeal;
    }

    public void needsOptoAssembly() {
        needsOptoAssembly = true;
    }

    public boolean usesOptoAssembly() {
        return needsOptoAssembly;
    }
}

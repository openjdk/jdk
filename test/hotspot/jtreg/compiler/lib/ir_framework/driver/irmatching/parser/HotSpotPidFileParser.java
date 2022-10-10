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

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.TestClass;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchable;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class to parse the ideal compile phases and PrintOptoAssembly outputs of the test class from the hotspot_pid* file
 * of all methods collected by {@link IREncodingParser}.
 *
 * @see IRMethod
 * @see IREncodingParser
 */
class HotSpotPidFileParser {
    private static final Pattern COMPILE_ID_PATTERN = Pattern.compile("compile_id='(\\d+)'");

    private final Pattern compileIdPatternForTestClass;
    /**
     * "Method name" -> TestMethod map created by {@link IREncodingParser} which contains an entry for each method that
     * needs to be IR matched on.
     */
    private final Map<String, TestMethod> testMethodMap;

    public HotSpotPidFileParser(String testClass, Map<String, TestMethod> testMethodMap) {
        this.compileIdPatternForTestClass = Pattern.compile("compile_id='(\\d+)'.*" + Pattern.quote(testClass) + " (\\S+)");
        this.testMethodMap = testMethodMap;
    }

    /**
     * Parse the hotspot_pid*.log file from the test VM. Read the ideal compile phase and PrintOptoAssembly outputs for
     * all methods defined by the IR encoding.
     */
    public TestClass parse(String hotspotPidFileName) {
        try {
            parseHotSpotFile(hotspotPidFileName);
            SortedSet<IRMethodMatchable> irMethods = testMethodMap.values().stream()
                                                                  .map(TestMethod::createIRMethod)
                                                                  .collect(Collectors.toCollection(TreeSet::new));
            TestFormat.throwIfAnyFailures();
            return new TestClass(irMethods);
        } catch (IOException e) {
            throw new TestFrameworkException("Error while reading " + hotspotPidFileName, e);
        } catch (FileCorruptedException e) {
            throw new TestFrameworkException("Unexpected format of " + hotspotPidFileName, e);
        }
    }

    private void parseHotSpotFile(String hotspotPidFileName) throws IOException {
        Map<Integer, TestMethod> compileIdMap = new HashMap<>();
        try (var reader = Files.newBufferedReader(Paths.get(hotspotPidFileName))) {
            Line line = new Line(reader, compileIdPatternForTestClass);
            BlockOutputReader blockOutputReader = new BlockOutputReader(reader, compileIdPatternForTestClass);
            while (line.readLine()) {
                if (line.isTestClassCompilation()) {
                    parseTestMethodCompileId(compileIdMap, line.getLine());
                } else if (isTestMethodBlockStart(line, compileIdMap)) {
                    processMethodBlock(compileIdMap, line, blockOutputReader);
                }
            }
        }
    }

    private void processMethodBlock(Map<Integer, TestMethod> compileIdMap, Line line, BlockOutputReader blockOutputReader)
            throws IOException {
        Block block = blockOutputReader.readBlock();
        if (block.containsTestClassCompilations()) {
            // Register all test method compilations that could have been emitted during a rare safepoint while
            // dumping the ideal compile phase/PrintOptoAssembly output.
            block.getTestClassCompilations().forEach(l -> parseTestMethodCompileId(compileIdMap, l));
        }
        setIRMethodOutput(block.getOutput(), line, compileIdMap);
    }

    private void parseTestMethodCompileId(Map<Integer, TestMethod> compileIdMap, String line) {
        String methodName = parseMethodName(line);
        if (isTestAnnotatedMethod(methodName)) {
            compileIdMap.put(getCompileId(line), getTestMethod(methodName));
        }
    }

    private String parseMethodName(String line) {
        Matcher matcher = compileIdPatternForTestClass.matcher(line);
        TestFramework.check(matcher.find(), "must find match");
        return matcher.group(2);
    }

    /**
     * Is this a @Test method?
     */
    private boolean isTestAnnotatedMethod(String testMethodName) {
        return testMethodMap.containsKey(testMethodName);
    }

    private TestMethod getTestMethod(String testMethodName) {
        TestMethod testMethod = testMethodMap.get(testMethodName);
        testMethod.clearOutput();
        testMethod.setCompiled();
        return testMethod;
    }

    private int getCompileId(String line) {
        Matcher matcher = COMPILE_ID_PATTERN.matcher(line);
        if (!matcher.find()) {
            throw new FileCorruptedException("Unexpected format found on this line: " + line);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Is this line the start of a PrintIdeal/PrintOptoAssembly output block of a @Test method?
     */
    private boolean isTestMethodBlockStart(Line line, Map<Integer, TestMethod> compileIdMap) {
      return line.isBlockStart() && isTestClassMethodBlock(line, compileIdMap);
    }

    private boolean isTestClassMethodBlock(Line line, Map<Integer, TestMethod> compileIdMap) {
        return compileIdMap.containsKey(getCompileId(line.getLine()));
    }

    private void setIRMethodOutput(String blockOutput, Line blockStartLine, Map<Integer, TestMethod> compileIdMap) {
        TestMethod testMethod = compileIdMap.get(getCompileId(blockStartLine.getLine()));
        setIRMethodOutput(blockOutput, blockStartLine, testMethod);
    }

    private void setIRMethodOutput(String blockOutput, Line blockStartLine, TestMethod testMethod) {
        if (blockStartLine.isPrintIdealStart()) {
            testMethod.setIdealOutput(blockOutput, blockStartLine.getCompilePhase());
        } else {
            testMethod.setOptoAssemblyOutput(blockOutput);
        }
    }
}

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
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse the PrintIdeal and PrintOptoAssembly outputs of the test class from the hotspot_pid* file and add them
 * to the collection of {@link IRMethod} created by {@link IREncodingParser}.
 *
 * @see IRMethod
 * @see IREncodingParser
 */
class HotSpotPidFileParser {
    private static final Pattern COMPILE_ID_PATTERN = Pattern.compile("compile_id='(\\d+)'");

    private final Pattern compileIdPatternForTestClass;
    private Map<String, IRMethod> compilationsMap;

    public HotSpotPidFileParser(String testClass) {
        this.compileIdPatternForTestClass = Pattern.compile("compile_id='(\\d+)'.*" + Pattern.quote(testClass) + " (\\S+)");
    }

    public void setCompilationsMap(Map<String, IRMethod> compilationsMap) {
        this.compilationsMap = compilationsMap;
    }
    /**
     * Parse the hotspot_pid*.log file from the test VM. Read the PrintIdeal and PrintOptoAssembly outputs for all
     * methods of the test class that need to be IR matched (found in compilations map).
     */
    public Collection<IRMethod> parseCompilations(String hotspotPidFileName) {
        try {
            processFileLines(hotspotPidFileName);
            return compilationsMap.values();
        } catch (IOException e) {
            throw new TestFrameworkException("Error while reading " + hotspotPidFileName, e);
        } catch (FileCorruptedException e) {
            throw new TestFrameworkException("Unexpected format of " + hotspotPidFileName, e);
        }
    }

    private void processFileLines(String hotspotPidFileName) throws IOException {
        Map<Integer, IRMethod> compileIdMap = new HashMap<>();
        try (var reader = Files.newBufferedReader(Paths.get(hotspotPidFileName))) {
            Line line = new Line(reader, compileIdPatternForTestClass);
            BlockOutputReader blockOutputReader = new BlockOutputReader(reader);
            while (line.readLine()) {
                if (line.isTestClassCompilation()) {
                    parseTestMethodCompileId(compileIdMap, line.getLine());
                } else if (isTestMethodBlockStart(line, compileIdMap)) {
                    String blockOutput = blockOutputReader.readBlock();
                    setIRMethodOutput(blockOutput, line, compileIdMap);
                }
            }
        }
    }

    private void parseTestMethodCompileId(Map<Integer, IRMethod> compileIdMap, String line) {
        String methodName = parseMethodName(line);
        if (isTestAnnotatedMethod(methodName)) {
            int compileId = getCompileId(line);
            compileIdMap.put(compileId, getIrMethod(methodName));
        }
    }

    private String parseMethodName(String line) {
        Matcher matcher = compileIdPatternForTestClass.matcher(line);
        TestFramework.check(matcher.find(), "must find match");
        return matcher.group(2);
    }

    private boolean isTestAnnotatedMethod(String testMethodName) {
        return compilationsMap.containsKey(testMethodName);
    }

    private IRMethod getIrMethod(String testMethodName) {
        return compilationsMap.get(testMethodName);
    }



    private int getCompileId(String line) {
        Matcher matcher = COMPILE_ID_PATTERN.matcher(line);
        if (!matcher.find()) {
            throw new FileCorruptedException("Unexpected format found on this line: " + line);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private boolean isTestMethodBlockStart(Line line, Map<Integer, IRMethod> compileIdMap) {
      return line.isBlockStart() && isTestClassMethodBlock(line, compileIdMap);
    }

    private boolean isTestClassMethodBlock(Line line, Map<Integer, IRMethod> compileIdMap) {
        return compileIdMap.containsKey(getCompileId(line.getLine()));
    }

    public void setIRMethodOutput(String blockOutput, Line blockStartLine, Map<Integer, IRMethod> compileIdMap) {
        IRMethod irMethod = compileIdMap.get(getCompileId(blockStartLine.getLine()));
        setIRMethodOutput(blockOutput, blockStartLine, irMethod);
    }

    private void setIRMethodOutput(String blockOutput, Line blockStartLine, IRMethod irMethod) {
        if (blockStartLine.isPrintIdealStart()) {
            irMethod.setIdealOutput(blockOutput);
        } else {
            irMethod.setOptoAssemblyOutput(blockOutput);
        }
    }
}

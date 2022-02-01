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

import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HotSpotPidFileParser {
    private static final Pattern COMPILE_ID_PATTERN = Pattern.compile("compile_id='(\\d+)'");

    private final Pattern compileIdPatternForTestClass;
    private final Map<String, IRMethod> compilations;

    public HotSpotPidFileParser(Map<String, IRMethod> compilations, String className) {
        this.compilations = compilations;
        this.compileIdPatternForTestClass = Pattern.compile("compile_id='(\\d+)'.*" + Pattern.quote(className) + " (\\S+)");
    }

    /**
     * Parse the hotspot_pid*.log file from the test VM. Read the PrintIdeal and PrintOptoAssembly entries for all
     * methods of the test class that need to be IR matched (according to IR encoding).
     */
    public void parseCompilations(String hotspotPidFileName) {
        Map<Integer, IRMethod> compileIdMap = new HashMap<>();
        try (var br = Files.newBufferedReader(Paths.get(hotspotPidFileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                IRMethod irMethod = getIRMethodForTestCompilationHeader(line);
                if (isTestCompilationHeader(irMethod)) {
                    addTestMethodCompileId(irMethod, compileIdMap, line);
                } else {
                    Block block = getBlockForBlockHeaderLine(line, compileIdMap);
                    if (isBlockHeader(block)) {
                        block.readBlockLinesForIRMethod(br);
                    }
                }
            }
        } catch (IOException e) {
            throw new TestFrameworkException("Error while reading " + hotspotPidFileName, e);
        } catch (FileCorruptedException e) {
            throw new TestFrameworkException("Unexpected format of " + hotspotPidFileName, e);
        }
    }

    private static boolean isBlockHeader(Block block) {
        return block != null;
    }

    private static boolean isTestCompilationHeader(IRMethod irMethod) {
        return irMethod != null;
    }

    /**
     * Returns a block object if line is a start of an ideal or opto assembly block. Otherwise,
     * return null.
     */
    private Block getBlockForBlockHeaderLine(String line, Map<Integer, IRMethod> compileIdMap) {
        if (isNotBlockStart(line)) {
            return null;
        }
        IRMethod irMethod = compileIdMap.get(getCompileId(line));
        if (irMethod != null) {
            // Is a test method block.
            if (isPrintIdealStart(line)) {
                return new IdealBlock(irMethod);
            } else {
                return new OptoBlock(irMethod);
            }
        }
        return null;
    }

    private boolean isNotBlockStart(String line) {
        return !isPrintIdealStart(line) && !isPrintOptoAssemblyStart(line);
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
     * Is this line a start of a @Test annotated method?
     */
    private IRMethod getIRMethodForTestCompilationHeader(String line) {
        if (isCompilationHeader(line)) {
            Matcher matcher = compileIdPatternForTestClass.matcher(line);
            if (matcher.find()) {
                // Only care about test class entries. Might have non-class entries as well if user specified additional
                // compile commands. Ignore these.
                String methodName = matcher.group(2);
                return compilations.get(methodName);
            }
        }
        return null;
    }

    /**
     * Only consider non-osr (no "compile_kind") and compilations with C2 (no "level")
     */
    private boolean isCompilationHeader(String line) {
        return line.startsWith("<task_queued") && !line.contains("compile_kind='") && !line.contains("level='");
    }

    /**
     * Parse the compile id from this line if it belongs to a method that needs to be IR tested (part of test class
     * and IR encoding from the test VM specifies that this method has @IR rules to be checked).
     */
    private void addTestMethodCompileId(IRMethod irMethod, Map<Integer, IRMethod> compileIdMap, String line) {
        // We only care about methods that we are actually gonna IR match based on IR encoding.
        int compileId = getCompileId(line);
        compileIdMap.put(compileId, irMethod);
    }

    private int getCompileId(String line) {
        Matcher matcher = COMPILE_ID_PATTERN.matcher(line);
        if (!matcher.find()) {
            throw new FileCorruptedException("Unexpected format found on this line: " + line);
        }
        return Integer.parseInt(matcher.group(1));
    }

    static class FileCorruptedException extends RuntimeException {
        public FileCorruptedException(String s) {
            super(s);
        }
    }

    abstract static class Block {
        protected final IRMethod irMethod;

        public Block(IRMethod irMethod) {
            this.irMethod = irMethod;
        }

        protected String readBlockLinesToString(BufferedReader reader) throws IOException {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.startsWith("</")) {
                builder.append(escapeXML(line)).append(System.lineSeparator());
            }
            return builder.toString();
        }

        /**
         * Need to escape XML special characters.
         */
        private static String escapeXML(String line) {
            if (line.contains("&")) {
                line = line.replace("&lt;", "<");
                line = line.replace("&gt;", ">");
                line = line.replace("&quot;", "\"");
                line = line.replace("&apos;", "'");
                line = line.replace("&amp;", "&");
            }
            return line;
        }

        /**
         * Read all lines belonging to this block and store the output in the IR method.
         */
        abstract public void readBlockLinesForIRMethod(BufferedReader br) throws IOException;
    }

    static class IdealBlock extends Block {
        public IdealBlock(IRMethod irMethod) {
            super(irMethod);
        }

        @Override
        public void readBlockLinesForIRMethod(BufferedReader br) throws IOException {
            String lines = readBlockLinesToString(br);
            irMethod.setIdealOutput(lines);
        }
    }

    static class OptoBlock extends Block {
        public OptoBlock(IRMethod irMethod) {
            super(irMethod);
        }

        @Override
        public void readBlockLinesForIRMethod(BufferedReader br) throws IOException {
            String lines = readBlockLinesToString(br);
            irMethod.setOptoAssemblyOutput(lines);
        }
    }

}

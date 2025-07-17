/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser.hotspot;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.TestFramework;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a currently parsed method in {@link HotSpotPidFileParser}. It stores all the different compile
 * phase outputs.
 */
public class LoggedMethod {
    /**
     * Dummy object for methods that we do not need to parse.
     */
    public static final LoggedMethod DONT_CARE = new LoggedMethod();
    private static final Pattern IDEAL_COMPILE_PHASE_PATTERN = Pattern.compile("<ideal.*compile_phase='(.*)'>");

    private final Map<CompilePhase, String> compilationOutput = new LinkedHashMap<>();
    private CompilePhaseBlock compilePhaseBlock;

    public LoggedMethod() {
        this.compilePhaseBlock = CompilePhaseBlock.DONT_CARE;
    }

    public Map<CompilePhase, String> compilationOutput() {
        return compilationOutput;
    }

    public boolean hasActiveBlock() {
        return compilePhaseBlock != CompilePhaseBlock.DONT_CARE;
    }

    public void addLine(String line) {
        if (hasActiveBlock()) {
            compilePhaseBlock.addLine(line);
        }
    }

    public void beginPrintIdealBlock(String line) {
        Matcher matcher = IDEAL_COMPILE_PHASE_PATTERN.matcher(line);
        TestFramework.check(matcher.find(), "must always find \"compile_phase\" in ideal entry in " + line);
        CompilePhase compilePhase = CompilePhase.forName(matcher.group(1));
        beginBlock(compilePhase);
    }

    public void beginPrintOptoAssemblyBlock() {
        beginBlock(CompilePhase.PRINT_OPTO_ASSEMBLY);
    }

    private void beginBlock(CompilePhase compilePhase) {
        if (compilationOutput.containsKey(compilePhase) && !compilePhase.overrideRepeatedPhase()) {
            // We only want to keep the first compilation output for this phase.
            compilePhaseBlock = CompilePhaseBlock.DONT_CARE;
        } else {
            compilePhaseBlock = new CompilePhaseBlock(compilePhase);
        }
    }

    public void terminateBlock() {
        if (hasActiveBlock()) {
            compilationOutput.put(compilePhaseBlock.compilePhase(), compilePhaseBlock.content());
            compilePhaseBlock = CompilePhaseBlock.DONT_CARE;
        }
    }
}

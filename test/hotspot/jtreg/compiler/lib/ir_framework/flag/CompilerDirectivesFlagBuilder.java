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

package compiler.lib.ir_framework.flag;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.test.TestVM;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class uses {@link CompilePhaseCollector} to collect all compile phases in order to write a compile command file
 * with the required compile commands for each method such that the {@link TestVM} only prints the output required by
 * the {@link IRMatcher} to perform IR matching.
 *
 * @see FlagVM
 * @see CompilePhaseCollector
 */
class CompilerDirectivesFlagBuilder {
    private final Map<String, Set<CompilePhase>> methodsToCompilePhasesMap;
    private int directivesCount = 0;
    public CompilerDirectivesFlagBuilder(Class<?> testClass) {
        methodsToCompilePhasesMap = CompilePhaseCollector.collect(testClass);
    }

    public List<String> build() {
        List<String> flags = new ArrayList<>();
        flags.add("-XX:CompilerDirectivesFile=" + FlagVM.TEST_VM_COMPILE_COMMANDS_FILE);
        writeDirectivesFile();
        flags.add("-XX:CompilerDirectivesLimit=" + directivesCount + 1);
        return flags;
    }

    private void writeDirectivesFile() {
        try (var writer = Files.newBufferedWriter(Paths.get(FlagVM.TEST_VM_COMPILE_COMMANDS_FILE))) {
            writer.write("[" + System.lineSeparator());
            writeBody(writer);
            writer.write("]" + System.lineSeparator());
        } catch (IOException e) {
            throw new TestFrameworkException("Error while writing to file " + FlagVM.TEST_VM_COMPILE_COMMANDS_FILE, e);
        }
    }

    private void writeBody(BufferedWriter writer) throws IOException {
        String methodEntries = methodsToCompilePhasesMap.entrySet()
                                                        .stream()
                                                        .map(e -> writeMethodDirectives(e.getKey(), e.getValue()))
                                                        .collect(Collectors.joining("," + System.lineSeparator()));
        writer.write(methodEntries + System.lineSeparator());
    }

    private String writeMethodDirectives(String methodName, Set<CompilePhase> compilePhases) {
        directivesCount++;
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "{", 1);
        appendLine(builder, "match : \"" + methodName + "\",", 2);
        appendLine(builder, "log : true,", 2);
        appendPrintIdeal(compilePhases, builder);
        appendPrintOptoAssembly(compilePhases, builder);
        appendRemainingCompilePhases(compilePhases, builder);
        append(builder, "}", 1);
        return builder.toString();
    }

    private static void appendPrintIdeal(Set<CompilePhase> compilePhases, StringBuilder builder) {
        if (compilePhases.remove(CompilePhase.PRINT_IDEAL)) {
            appendLine(builder, "PrintIdeal : true,", 2);
        }
    }

    private static void appendPrintOptoAssembly(Set<CompilePhase> compilePhases, StringBuilder builder) {
        if (compilePhases.remove(CompilePhase.PRINT_OPTO_ASSEMBLY)) {
            appendLine(builder, "PrintOptoAssembly : true,", 2);
        }
    }

    private static void appendRemainingCompilePhases(Set<CompilePhase> compilePhases, StringBuilder builder) {
        if (!compilePhases.isEmpty()) {
            appendLine(builder, "PrintIdealPhase : \"" + compilePhases
                    .stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(",")) + "\"", 2);
        }
    }

    private static void appendLine(StringBuilder builder, String s, int indentLevel) {
        append(builder, s, indentLevel);
        builder.append(System.lineSeparator());
    }

    private static void append(StringBuilder builder, String s, int indentLevel) {
        builder.append("  ".repeat(indentLevel)).append(s);
    }
}

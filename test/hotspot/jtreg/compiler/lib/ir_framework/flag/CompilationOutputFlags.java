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
import compiler.lib.ir_framework.IR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Class to determine which compile command flags that the test VM requires in order to IR match. This depends on the
 * found compile phases in the {@link IR} annotations of the test class.
 */
class CompilationOutputFlags {
    private final Class<?> testClass;

    public CompilationOutputFlags(Class<?> testClass) {
        this.testClass = testClass;
    }

    /**
     * Return a list of required PrintIdeal/PrintIdealLevel/PrintOptoAssembly command line flags depending on the required
     * phases specified in the IR annotations of the test class.
     */
    public List<String> getFlags() {
        List<String> cmds = new ArrayList<>();
        int[] compilePhaseLevels = getCompilePhaseLevels();
        addDefaultFlags(cmds, compilePhaseLevels);
        addCompilePhaseFlag(cmds, compilePhaseLevels);
        return cmds;
    }

    private int[] getCompilePhaseLevels() {
        return Arrays.stream(testClass.getDeclaredMethods())
                     .flatMap(m -> Stream.of(m.getAnnotationsByType(IR.class))) // Stream<IR>
                     .map(IR::phase) // Stream<CompilationPhase[]>
                     .map(this::replaceEmptyArrayByArrayWithDefault) // Stream<CompilationPhase[]>
                     .flatMap(Arrays::stream) // Stream<CompilePhase>
                     .mapToInt(CompilePhase::getLevel) // Stream<int> of levels
                     .toArray();
    }

    private CompilePhase[] replaceEmptyArrayByArrayWithDefault(CompilePhase[] phase) {
        return phase.length == 0 ? new CompilePhase[] {CompilePhase.DEFAULT} : phase;
    }

    private void addDefaultFlags(List<String> cmds, int[] compilePhaseLevels) {
        if (getMinLevel(compilePhaseLevels) == 0) {
            cmds.add(getCommandForDefaultFlag("PrintIdeal"));
            cmds.add(getCommandForDefaultFlag("PrintOptoAssembly"));
        }
    }

    private int getMinLevel(int[] compilePhaseLevels) {
        return Arrays.stream(compilePhaseLevels).min().orElse(0); // Could have no phases, return level 0 in this case
    }


    private String getCommandForDefaultFlag(String flag) {
        return "-XX:CompileCommand=" + flag + "," + testClass.getCanonicalName() + "::*,true";
    }

    private void addCompilePhaseFlag(List<String> cmds, int[] compilePhaseLevels) {
        int maxLevel = getMaxLevel(compilePhaseLevels); // Could have no phases, return level 0 in this case
        if (maxLevel > 0) {
            cmds.add(getPrintIdealLevelCommand(maxLevel));
        }
    }

    private int getMaxLevel(int[] compilePhaseLevels) {
        return Arrays.stream(compilePhaseLevels).max().orElse(0); // Could have no phases, return level 0 in this case
    }

    private String getPrintIdealLevelCommand(int level) {
        return "-XX:CompileCommand=PrintIdealLevel," + testClass.getCanonicalName() + "::*," + level;
    }
}

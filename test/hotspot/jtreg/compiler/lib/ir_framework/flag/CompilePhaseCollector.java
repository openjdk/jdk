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
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.*;

/**
 * This class collects for each method a set of compile phases that are specified in the {@link IR @IR} annotations.
 * If {@link CompilePhase#DEFAULT} is found, then we try to replace it with {@link CompilePhase#PRINT_IDEAL} or
 * {@link CompilePhase#PRINT_OPTO_ASSEMBLY} depending on wheter all string placeholders specified in {@link IRNode}
 * could be replaced.
 *
 * @see FlagVM
 * @see CompileCommandFileWriter
 */
class CompilePhaseCollector {

    /**
     * Returns a map method_name -> compile_phases_set that can be used by {@link CompileCommandFileWriter}.
     */
    public static Map<String, Set<CompilePhase>> collect(Class<?> testClass) {
        Map<String, Set<CompilePhase>> methodToPhases = new HashMap<>();
        List<Method> irAnnotatedMethods = getIRAnnotatedMethods(testClass);
        try {
            for (Method method : irAnnotatedMethods) {
                methodToPhases.put(testClass.getCanonicalName() + "::" + method.getName(), processIRAnnotations(method));
            }
        } catch (TestFormatException e) {
            // Create default map and let the IR matcher report the format failures later in the driver VM.
            return createDefaultMap(testClass);
        }
        return methodToPhases;
    }

    /**
     * Creates a default map that just contains PrintIdeal and PrintOptoAssembly
     */
    private static Map<String, Set<CompilePhase>> createDefaultMap(Class<?> testClass) {
        Map<String, Set<CompilePhase>> defaultMap = new HashMap<>();
        HashSet<CompilePhase> defaultSet = new HashSet<>();
        defaultSet.add(CompilePhase.PRINT_IDEAL);
        defaultSet.add(CompilePhase.PRINT_OPTO_ASSEMBLY);
        defaultMap.put(testClass.getCanonicalName() + "::*", defaultSet);
        return defaultMap;

    }

    private static Set<CompilePhase> processIRAnnotations(Method method) {
        CompilePhaseSet compilePhaseCollector = new CompilePhaseSet();
        IR[] irAnnos = method.getAnnotationsByType(IR.class);
        int ruleId = 1;
        for (IR irAnno : irAnnos) {
            processCompilePhases(compilePhaseCollector, irAnno, method);
            ruleId++;
        }
        return compilePhaseCollector.getPhases();
    }

    private static void processCompilePhases(CompilePhaseSet compilePhaseCollector, IR irAnno, Method method) {
        CompilePhase[] compilePhases = irAnno.phase();
        for (CompilePhase compilePhase : compilePhases) {
            if (compilePhase == CompilePhase.DEFAULT) {
                compilePhaseCollector.addForDefault(irAnno);
            } else {
                compilePhaseCollector.addCompilePhase(compilePhase);
            }
        }
    }

    private static List<Method> getIRAnnotatedMethods(Class<?> testClass) {
        return Arrays.stream(testClass.getDeclaredMethods()).filter(m -> m.getAnnotationsByType(IR.class).length > 0).toList();
    }
}

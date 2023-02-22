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
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawCheckAttribute;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawCounts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawFailOn;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * This class collects the compile phases of a method by parsing all {@link IR @IR} annotations.
 *
 * @see CompilePhaseCollector
 */
class MethodCompilePhaseCollector {
    private final Set<CompilePhase> compilePhases = new HashSet<>();
    private final Method method;

    public MethodCompilePhaseCollector(Method method) {
        this.method = method;
    }

    public Set<CompilePhase> collect() {
        IR[] irAnnos = method.getAnnotationsByType(IR.class);
        for (IR irAnno : irAnnos) {
            collectCompilePhases(irAnno);
        }
        return compilePhases;
    }

    /**
     * Collect the compile phases for {@code irAnno} by looking at the {@link IR#phase()} attribute. If we find
     * {@link CompilePhase#DEFAULT}, we collect the default compile phases of all IR nodes in each constraint as
     * specified in {@link IRNode}. If we find a user defined IR node (not specified in {@link IRNode}) or a
     * duplicated compile phase, we throw a {@link TestFormatException}.
     */
    public void collectCompilePhases(IR irAnno) {
        for (CompilePhase compilePhase : irAnno.phase()) {
            if (compilePhase == CompilePhase.DEFAULT) {
                addDefaultPhasesForConstraint(new RawFailOn(irAnno.failOn()));
                addDefaultPhasesForConstraint(new RawCounts(irAnno.counts()));
            } else {
                compilePhases.add(compilePhase);
            }
        }
    }

    private void addDefaultPhasesForConstraint(RawCheckAttribute rawCheckAttribute) {
        compilePhases.addAll(rawCheckAttribute.parseDefaultCompilePhases());
    }
}

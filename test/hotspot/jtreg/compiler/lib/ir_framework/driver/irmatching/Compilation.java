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

package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;

import java.util.Map;

/**
 * This class provides information about the compilation output of a compile phase for an {@link IRMethod}.
 */
public class Compilation {
    private final Map<CompilePhase, String> compilationOutputMap;

    public Compilation(Map<CompilePhase, String> compilationOutputMap) {
        this.compilationOutputMap = compilationOutputMap;
    }

    /**
     * Is there a compilation output for {@code compilePhase}?
     */
    public boolean hasOutput(CompilePhase compilePhase) {
        return compilationOutputMap.containsKey(compilePhase);
    }

    /**
     * Get the compilation output for non-default compile phase {@code phase} or an empty string if no output was found
     * in the hotspot_pid* file for this compile phase.
     */
    public String output(CompilePhase compilePhase) {
        TestFramework.check(compilePhase != CompilePhase.DEFAULT, "cannot query for DEFAULT");
        return compilationOutputMap.getOrDefault(compilePhase, "");
    }
}

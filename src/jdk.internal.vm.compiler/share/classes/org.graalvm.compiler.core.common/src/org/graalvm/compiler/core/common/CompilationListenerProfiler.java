/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common;

import org.graalvm.compiler.debug.CompilationListener;
import org.graalvm.compiler.debug.DebugContext.CompilerPhaseScope;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Connects a {@link CompilationListener} to a {@link CompilerProfiler}.
 */
public class CompilationListenerProfiler implements CompilationListener {
    private final int compileId;
    private final CompilerProfiler profiler;

    /**
     * Creates a compilation listener that passes events for a specific compilation identified by
     * {@code compileId} onto {@code profiler}.
     */
    public CompilationListenerProfiler(CompilerProfiler profiler, int compileId) {
        this.profiler = profiler;
        this.compileId = compileId;
    }

    @Override
    public void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, CharSequence message, int bci) {
        profiler.notifyCompilerInlingEvent(compileId, caller, callee, succeeded, message.toString(), bci);
    }

    @Override
    public CompilerPhaseScope enterPhase(CharSequence name, int nesting) {
        long start = profiler.getTicks();
        return new CompilerPhaseScope() {

            @Override
            public void close() {
                profiler.notifyCompilerPhaseEvent(compileId, start, name.toString(), nesting);
            }
        };
    }
}

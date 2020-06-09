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


package org.graalvm.compiler.debug;

import org.graalvm.compiler.debug.DebugContext.CompilerPhaseScope;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Implemented by clients interested in when the compiler starts/ends a {@linkplain #enterPhase
 * phase} or {@linkplain #notifyInlining considers inlining} a method.
 */
public interface CompilationListener {

    /**
     * Notifies this listener that the compiler is starting a compiler phase.
     *
     * @param name the name of the phase
     * @return an object whose {@link CompilerPhaseScope#close()} method will be called when the
     *         phase completes
     */
    CompilerPhaseScope enterPhase(CharSequence name, int nesting);

    /**
     * Notifies this listener when the compiler considers inlining {@code callee} into
     * {@code caller}.
     *
     * @param caller caller method
     * @param callee callee method considered for inlining into {@code caller}
     * @param succeeded true if {@code callee} was inlined into {@code caller}
     * @param message extra information about inlining decision
     * @param bci byte code index of call site
     */
    void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, CharSequence message, int bci);
}

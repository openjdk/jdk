/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.gen;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationIdentifier.Verbosity;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;

import jdk.vm.ci.code.CallingConvention;

public class LIRGenerationResult {

    private final LIR lir;
    private final FrameMapBuilder frameMapBuilder;
    private FrameMap frameMap;
    private final CallingConvention callingConvention;
    /**
     * Records whether the code being generated makes at least one foreign call.
     */
    private boolean hasForeignCall;
    /**
     * Unique identifier of this compilation.
     */
    private final CompilationIdentifier compilationId;

    public LIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, CallingConvention callingConvention) {
        this.lir = lir;
        this.frameMapBuilder = frameMapBuilder;
        this.callingConvention = callingConvention;
        this.compilationId = compilationId;
    }

    /**
     * Returns the incoming calling convention for the parameters of the method that is compiled.
     */
    public CallingConvention getCallingConvention() {
        return callingConvention;
    }

    /**
     * Returns the {@link FrameMapBuilder} for collecting the information to build a
     * {@link FrameMap}.
     *
     * This method can only be used prior calling {@link #buildFrameMap}.
     */
    public final FrameMapBuilder getFrameMapBuilder() {
        assert frameMap == null : "getFrameMapBuilder() can only be used before calling buildFrameMap()!";
        return frameMapBuilder;
    }

    /**
     * Creates a {@link FrameMap} out of the {@link FrameMapBuilder}. This method should only be
     * called once. After calling it, {@link #getFrameMapBuilder()} can no longer be used.
     *
     * @see FrameMapBuilder#buildFrameMap
     */
    public void buildFrameMap() {
        assert frameMap == null : "buildFrameMap() can only be called once!";
        frameMap = frameMapBuilder.buildFrameMap(this);
    }

    /**
     * Returns the {@link FrameMap} associated with this {@link LIRGenerationResult}.
     *
     * This method can only be called after {@link #buildFrameMap}.
     */
    public FrameMap getFrameMap() {
        assert frameMap != null : "getFrameMap() can only be used after calling buildFrameMap()!";
        return frameMap;
    }

    public LIR getLIR() {
        return lir;
    }

    /**
     * Determines whether the code being generated makes at least one foreign call.
     */
    public boolean hasForeignCall() {
        return hasForeignCall;
    }

    public final void setForeignCall(boolean hasForeignCall) {
        this.hasForeignCall = hasForeignCall;
    }

    public String getCompilationUnitName() {
        if (compilationId == null || compilationId == CompilationIdentifier.INVALID_COMPILATION_ID) {
            return "<unknown>";
        }
        return compilationId.toString(Verbosity.NAME);
    }

    /**
     * Returns a unique identifier of the current compilation.
     */
    public CompilationIdentifier getCompilationId() {
        return compilationId;
    }
}

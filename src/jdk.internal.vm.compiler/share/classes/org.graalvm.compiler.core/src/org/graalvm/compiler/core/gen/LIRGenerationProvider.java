/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.gen;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Provides compiler backend-specific generation helpers for the {@link LIRCompilerBackend}.
 */
public interface LIRGenerationProvider {
    LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes);

    LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, RegisterConfig registerConfig, StructuredGraph graph,
                    Object stub);

    NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen);

    /**
     * Creates the object used to fill in the details of a given compilation result.
     */
    CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult,
                    CompilationResultBuilderFactory factory);

    /**
     * Emits the code for a given graph.
     *
     * @param installedCodeOwner the method the compiled code will be associated with once
     *            installed. This argument can be null.
     */
    void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner);
}

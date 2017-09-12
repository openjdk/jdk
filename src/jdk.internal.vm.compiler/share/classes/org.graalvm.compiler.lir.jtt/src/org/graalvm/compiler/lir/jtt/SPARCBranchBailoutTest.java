/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.jtt;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARC;

/**
 * Tests the {@link BailoutException} thrown, when trying to compile huge methods, which have branch
 * displacements which does not fit into 19 bit signed.
 */
public class SPARCBranchBailoutTest extends LIRTest {
    private static class BranchSpec extends LIRTestSpecification {
        private final int n;

        BranchSpec(int n) {
            super();
            this.n = n;
        }

        @Override
        public void generate(LIRGeneratorTool gen, Value a) {
            gen.append(new LargeOp(n));
            setResult(a);
        }
    }

    private static final BranchSpec spec = new BranchSpec(1 << 20);

    @LIRIntrinsic
    public static int branch(@SuppressWarnings("unused") BranchSpec s, int a) {
        return a;
    }

    public static int testBranch(int length) {
        int res = 1;
        if (length > 0) {
            res = branch(spec, 1);
        } else {
            res = branch(spec, 2);
        }
        return GraalDirectives.opaque(res);
    }

    @SuppressWarnings("try")
    @Test
    public void testBailoutOnBranchOverflow() throws Throwable {
        Assume.assumeTrue(getBackend().getTarget().arch instanceof SPARC);
        ResolvedJavaMethod m = getResolvedJavaMethod("testBranch");
        DebugContext debug = getDebugContext();
        try {
            try (Scope s = debug.disable()) {
                StructuredGraph graph = parseEager(m, AllowAssumptions.YES, debug);
                compile(m, graph);
            }
        } catch (GraalError e) {
            Assert.assertEquals(PermanentBailoutException.class, e.getCause().getClass());
        }
    }

    public static class LargeOp extends LIRInstruction {
        private static final LIRInstructionClass<LargeOp> TYPE = LIRInstructionClass.create(LargeOp.class);
        private final int n;

        public LargeOp(int n) {
            super(TYPE);
            this.n = n;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            for (int i = 0; i < n; i++) {
                crb.asm.emitInt(0);
            }
        }
    }
}

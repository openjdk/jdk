/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.hotspot.amd64.test;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.nodes.HotSpotCompressionNode;
import org.graalvm.compiler.hotspot.test.HotSpotGraalCompilerTest;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DataPatchInConstantsTest extends HotSpotGraalCompilerTest {

    @Before
    public void checkAMD64() {
        Assume.assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    private static final Object object = new Object() {

        @Override
        public String toString() {
            return "testObject";
        }
    };

    private static Object loadThroughPatch(Object obj) {
        return obj;
    }

    public static Object oopSnippet() {
        Object patch = loadThroughPatch(object);
        if (object != patch) {
            return "invalid patch";
        }
        System.gc();
        patch = loadThroughPatch(object);
        if (object != patch) {
            return "failed after gc";
        }
        return patch;
    }

    @Test
    public void oopTest() {
        test("oopSnippet");
    }

    private static Object loadThroughCompressedPatch(Object obj) {
        return obj;
    }

    public static Object narrowOopSnippet() {
        Object patch = loadThroughCompressedPatch(object);
        if (object != patch) {
            return "invalid patch";
        }
        System.gc();
        patch = loadThroughCompressedPatch(object);
        if (object != patch) {
            return "failed after gc";
        }
        return patch;
    }

    @Test
    public void narrowOopTest() {
        Assume.assumeTrue("skipping narrow oop data patch test", runtime().getVMConfig().useCompressedOops);
        test("narrowOopSnippet");
    }

    public static Object compareSnippet() {
        Object uncompressed = loadThroughPatch(object);
        Object compressed = loadThroughCompressedPatch(object);
        if (object != uncompressed) {
            return "uncompressed failed";
        }
        if (object != compressed) {
            return "compressed failed";
        }
        if (uncompressed != compressed) {
            return "uncompressed != compressed";
        }
        return object;
    }

    @Test
    public void compareTest() {
        Assume.assumeTrue("skipping narrow oop data patch test", runtime().getVMConfig().useCompressedOops);
        test("compareSnippet");
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, DataPatchInConstantsTest.class);

        r.register1("loadThroughPatch", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.addPush(JavaKind.Object, new LoadThroughPatchNode(arg));
                return true;
            }
        });

        r.register1("loadThroughCompressedPatch", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ValueNode compressed = b.add(HotSpotCompressionNode.compress(arg, runtime().getVMConfig().getOopEncoding()));
                ValueNode patch = b.add(new LoadThroughPatchNode(compressed));
                b.addPush(JavaKind.Object, HotSpotCompressionNode.uncompress(patch, runtime().getVMConfig().getOopEncoding()));
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    @NodeInfo(cycles = CYCLES_2, size = SIZE_1)
    private static final class LoadThroughPatchNode extends FixedWithNextNode implements LIRLowerable {
        public static final NodeClass<LoadThroughPatchNode> TYPE = NodeClass.create(LoadThroughPatchNode.class);

        @Input protected ValueNode input;

        protected LoadThroughPatchNode(ValueNode input) {
            super(TYPE, input.stamp(NodeView.DEFAULT));
            this.input = input;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            assert input.isConstant();

            LIRGeneratorTool gen = generator.getLIRGeneratorTool();
            Variable ret = gen.newVariable(gen.getLIRKind(stamp(NodeView.DEFAULT)));

            gen.append(new LoadThroughPatchOp(input.asConstant(), stamp(NodeView.DEFAULT) instanceof NarrowOopStamp, ret));
            generator.setResult(this, ret);
        }
    }

    private static final class LoadThroughPatchOp extends LIRInstruction {
        public static final LIRInstructionClass<LoadThroughPatchOp> TYPE = LIRInstructionClass.create(LoadThroughPatchOp.class);

        final Constant c;
        final boolean compressed;
        @Def({REG}) AllocatableValue result;

        LoadThroughPatchOp(Constant c, boolean compressed, AllocatableValue result) {
            super(TYPE);
            this.c = c;
            this.compressed = compressed;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(c, compressed ? 4 : 8);
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            if (compressed) {
                asm.movl(asRegister(result), address);
            } else {
                asm.movq(asRegister(result), address);
            }
        }
    }
}

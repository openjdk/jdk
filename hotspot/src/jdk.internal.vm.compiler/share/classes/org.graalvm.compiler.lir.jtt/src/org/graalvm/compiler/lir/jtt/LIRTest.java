/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

/**
 * Base class for LIR tests.
 * <p>
 * It provides facilities to replace methods with {@link LIRTestSpecification arbitrary LIR
 * instructions}.
 */
public abstract class LIRTest extends JTTTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static final class LIRTestNode extends FixedWithNextNode implements LIRLowerable {

        public static final NodeClass<LIRTestNode> TYPE = NodeClass.create(LIRTestNode.class);
        @Input protected ValueNode opsNode;
        @Input protected NodeInputList<ValueNode> values;
        public final SnippetReflectionProvider snippetReflection;

        protected LIRTestNode(SnippetReflectionProvider snippetReflection, JavaKind kind, ValueNode opsNode, ValueNode[] values) {
            super(TYPE, StampFactory.forKind(kind));
            this.opsNode = opsNode;
            this.values = new NodeInputList<>(this, values);
            this.snippetReflection = snippetReflection;
        }

        public NodeInputList<ValueNode> values() {
            return values;
        }

        public ValueNode getLIROpsNode() {
            return opsNode;
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            LIRTestSpecification ops = getLIROperations();
            Stream<Value> v = values().stream().map(node -> gen.operand(node));

            ops.generate(gen.getLIRGeneratorTool(), v.toArray(size -> new Value[size]));
            Value result = ops.getResult();
            if (result != null) {
                gen.setResult(this, result);
            }
        }

        public LIRTestSpecification getLIROperations() {
            assert getLIROpsNode().isConstant();
            LIRTestSpecification spec = snippetReflection.asObject(LIRTestSpecification.class, getLIROpsNode().asJavaConstant());
            return spec;
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static final class LIRValueNode extends FixedWithNextNode implements LIRLowerable {

        public static final NodeClass<LIRValueNode> TYPE = NodeClass.create(LIRValueNode.class);
        @Input protected ValueNode opsNode;
        @Input protected ValueNode name;
        public final SnippetReflectionProvider snippetReflection;

        protected LIRValueNode(SnippetReflectionProvider snippetReflection, JavaKind kind, ValueNode opsNode, ValueNode name) {
            super(TYPE, StampFactory.forKind(kind));
            this.opsNode = opsNode;
            this.name = name;
            this.snippetReflection = snippetReflection;
        }

        public ValueNode getLIROpsNode() {
            return opsNode;
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            LIRTestSpecification spec = getLIROperations();
            Value output = spec.getOutput(getName());
            gen.setResult(this, isVariable(output) ? output : gen.getLIRGeneratorTool().emitMove(output));
        }

        private String getName() {
            assert name.isConstant();
            return snippetReflection.asObject(String.class, name.asJavaConstant());
        }

        private LIRTestSpecification getLIROperations() {
            assert getLIROpsNode().isConstant();
            return snippetReflection.asObject(LIRTestSpecification.class, getLIROpsNode().asJavaConstant());
        }

    }

    private InvocationPlugin lirTestPlugin = new InvocationPlugin() {
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec) {
            JavaKind returnKind = targetMethod.getSignature().getReturnKind();
            LIRTestNode node = new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{});
            addNode(b, returnKind, node);
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0) {
            JavaKind returnKind = targetMethod.getSignature().getReturnKind();
            LIRTestNode node = new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0});
            addNode(b, returnKind, node);
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1) {
            JavaKind returnKind = targetMethod.getSignature().getReturnKind();
            LIRTestNode node = new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0, arg1});
            addNode(b, returnKind, node);
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2) {
            JavaKind returnKind = targetMethod.getSignature().getReturnKind();
            LIRTestNode node = new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0, arg1, arg2});
            addNode(b, returnKind, node);
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
            JavaKind returnKind = targetMethod.getSignature().getReturnKind();
            LIRTestNode node = new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0, arg1, arg2, arg3});
            addNode(b, returnKind, node);
            return true;
        }

        private void addNode(GraphBuilderContext b, JavaKind returnKind, LIRTestNode node) {
            if (returnKind.equals(JavaKind.Void)) {
                b.add(node);
            } else {
                b.addPush(returnKind, node);
            }
        }

    };

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Class<? extends LIRTest> c = getClass();
        for (Method m : c.getMethods()) {
            if (m.getAnnotation(LIRIntrinsic.class) != null) {
                assert Modifier.isStatic(m.getModifiers());
                Class<?>[] p = m.getParameterTypes();
                assert p.length > 0;
                assert LIRTestSpecification.class.isAssignableFrom(p[0]);

                invocationPlugins.register(lirTestPlugin, c, m.getName(), p);
            }
        }
        InvocationPlugin outputPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode name, ValueNode expected) {
                JavaKind returnKind = targetMethod.getSignature().getReturnKind();
                b.addPush(returnKind, new LIRValueNode(getSnippetReflection(), returnKind, spec, name));
                return true;
            }
        };
        invocationPlugins.register(outputPlugin, LIRTest.class, "getOutput", new Class<?>[]{LIRTestSpecification.class, String.class, Object.class});
        invocationPlugins.register(outputPlugin, LIRTest.class, "getOutput", new Class<?>[]{LIRTestSpecification.class, String.class, int.class});
        super.registerInvocationPlugins(invocationPlugins);
    }

    @SuppressWarnings("unused")
    public static byte getOutput(LIRTestSpecification spec, String name, byte expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static short getOutput(LIRTestSpecification spec, String name, short expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static int getOutput(LIRTestSpecification spec, String name, int expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static long getOutput(LIRTestSpecification spec, String name, long expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static float getOutput(LIRTestSpecification spec, String name, float expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static double getOutput(LIRTestSpecification spec, String name, double expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static Object getOutput(LIRTestSpecification spec, String name, Object expected) {
        return expected;
    }

    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.METHOD)
    public static @interface LIRIntrinsic {
    }

}

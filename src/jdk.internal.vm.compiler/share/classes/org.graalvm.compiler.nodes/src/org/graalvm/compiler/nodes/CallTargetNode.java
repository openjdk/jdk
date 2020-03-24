/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Extension;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(allowedUsageTypes = Extension, cycles = CYCLES_0, size = SIZE_0)
public abstract class CallTargetNode extends ValueNode implements LIRLowerable {
    public static final NodeClass<CallTargetNode> TYPE = NodeClass.create(CallTargetNode.class);

    public enum InvokeKind {
        Interface(false),
        Special(true),
        Static(true),
        Virtual(false);

        InvokeKind(boolean direct) {
            this.direct = direct;
        }

        private final boolean direct;

        public boolean hasReceiver() {
            return this != Static;
        }

        public boolean isDirect() {
            return direct;
        }

        public boolean isIndirect() {
            return !direct;
        }

        public boolean isInterface() {
            return this == InvokeKind.Interface;
        }
    }

    @Input protected NodeInputList<ValueNode> arguments;
    protected ResolvedJavaMethod targetMethod;

    /**
     * Receiver type referenced at the interface call site.
     *
     * We need to distinguish the declaring type from the type referenced at the call site. We must
     * use the referenced type as lower type bound when doing CHA since interface calls must throw
     * exception if the receiver type is not a subtype of the reference type.
     *
     * Example:
     *
     * <pre>
     * interface I1 {
     *     void foo();
     * }
     *
     * interface I2 extends I1 {
     * }
     *
     * void bar(I2 o) {
     *     o.foo();
     * }
     * </pre>
     *
     * Here at the call site the declaring type for {@code foo()} is {@code I1}, while the
     * referenced type is {@code I2}. Only receivers of type {@code T} that is {@code T <: I2}
     * should be allowed at the call site. If they are not - an exception should be thrown.
     *
     * Since the interface types are not verified, another way to think about this call site is to
     * rewrite it as follows:
     *
     * <pre>
     * void bar(Object o) {
     *     ((I2) o).foo();
     * }
     * </pre>
     *
     * So, in case the receiver is not a subtype of {@code I2} an exception is thrown.
     */
    protected ResolvedJavaType referencedType;

    protected InvokeKind invokeKind;
    protected final StampPair returnStamp;

    protected CallTargetNode(NodeClass<? extends CallTargetNode> c, ValueNode[] arguments, ResolvedJavaMethod targetMethod, InvokeKind invokeKind, StampPair returnStamp) {
        super(c, StampFactory.forVoid());
        this.targetMethod = targetMethod;
        this.invokeKind = invokeKind;
        this.arguments = new NodeInputList<>(this, arguments);
        this.returnStamp = returnStamp;
    }

    public NodeInputList<ValueNode> arguments() {
        return arguments;
    }

    public static Stamp createReturnStamp(Assumptions assumptions, JavaType returnType) {
        JavaKind kind = returnType.getJavaKind();
        if (kind == JavaKind.Object && returnType instanceof ResolvedJavaType) {
            return StampFactory.object(TypeReference.create(assumptions, (ResolvedJavaType) returnType));
        } else {
            return StampFactory.forKind(kind);
        }
    }

    public StampPair returnStamp() {
        return this.returnStamp;
    }

    /**
     * A human-readable representation of the target, used for debug printing only.
     */
    public abstract String targetName();

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // nop
    }

    public void setTargetMethod(ResolvedJavaMethod targetMethod) {
        this.targetMethod = targetMethod;
    }

    /**
     * Gets the target method for this invocation instruction.
     *
     * @return the target method
     */
    public ResolvedJavaMethod targetMethod() {
        return targetMethod;
    }

    public void setReferencedType(ResolvedJavaType referencedType) {
        this.referencedType = referencedType;
    }

    public ResolvedJavaType referencedType() {
        return referencedType;
    }

    public InvokeKind invokeKind() {
        return invokeKind;
    }

    public void setInvokeKind(InvokeKind kind) {
        this.invokeKind = kind;
    }

    public Invoke invoke() {
        return (Invoke) this.usages().first();
    }
}

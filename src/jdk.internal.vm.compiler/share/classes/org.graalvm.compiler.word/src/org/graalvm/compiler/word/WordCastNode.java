/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.word;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Casts between Word and Object exposed by the {@link Word#fromAddress},
 * {@link Word#objectToTrackedPointer}, {@link Word#objectToUntrackedPointer} and
 * {@link Word#toObject()} operations. It has an impact on the pointer maps for the GC, so it must
 * not be scheduled or optimized away.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class WordCastNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable {

    public static final NodeClass<WordCastNode> TYPE = NodeClass.create(WordCastNode.class);

    @Input ValueNode input;
    public final boolean trackedPointer;

    public static WordCastNode wordToObject(ValueNode input, JavaKind wordKind) {
        assert input.getStackKind() == wordKind;
        return new WordCastNode(StampFactory.object(), input);
    }

    public static WordCastNode wordToObjectNonNull(ValueNode input, JavaKind wordKind) {
        assert input.getStackKind() == wordKind;
        return new WordCastNode(StampFactory.objectNonNull(), input);
    }

    public static WordCastNode addressToWord(ValueNode input, JavaKind wordKind) {
        assert input.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp;
        return new WordCastNode(StampFactory.forKind(wordKind), input);
    }

    public static WordCastNode objectToTrackedPointer(ValueNode input, JavaKind wordKind) {
        assert input.stamp(NodeView.DEFAULT) instanceof ObjectStamp;
        return new WordCastNode(StampFactory.forKind(wordKind), input, true);
    }

    public static WordCastNode objectToUntrackedPointer(ValueNode input, JavaKind wordKind) {
        assert input.stamp(NodeView.DEFAULT) instanceof ObjectStamp;
        return new WordCastNode(StampFactory.forKind(wordKind), input, false);
    }

    protected WordCastNode(Stamp stamp, ValueNode input) {
        this(stamp, input, true);
    }

    protected WordCastNode(Stamp stamp, ValueNode input, boolean trackedPointer) {
        super(TYPE, stamp);
        this.input = input;
        this.trackedPointer = trackedPointer;
    }

    public ValueNode getInput() {
        return input;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            /* If the cast is unused, it can be eliminated. */
            return input;
        }

        assert !stamp(NodeView.DEFAULT).isCompatible(input.stamp(NodeView.DEFAULT));
        if (input.isConstant()) {
            /* Null pointers are uncritical for GC, so they can be constant folded. */
            if (input.asJavaConstant().isNull()) {
                return ConstantNode.forIntegerStamp(stamp(NodeView.DEFAULT), 0);
            } else if (input.asJavaConstant().getJavaKind().isNumericInteger() && input.asJavaConstant().asLong() == 0) {
                return ConstantNode.forConstant(stamp(NodeView.DEFAULT), JavaConstant.NULL_POINTER, tool.getMetaAccess());
            }
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        Value value = generator.operand(input);
        ValueKind<?> kind = generator.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        assert kind.getPlatformKind().getSizeInBytes() == value.getPlatformKind().getSizeInBytes();

        if (trackedPointer && LIRKind.isValue(kind) && !LIRKind.isValue(value)) {
            // just change the PlatformKind, but don't drop reference information
            kind = value.getValueKind().changeType(kind.getPlatformKind());
        }

        if (kind.equals(value.getValueKind()) && !(value instanceof ConstantValue)) {
            generator.setResult(this, value);
        } else {
            AllocatableValue result = generator.getLIRGeneratorTool().newVariable(kind);
            generator.getLIRGeneratorTool().emitMove(result, value);
            generator.setResult(this, result);
        }
    }
}

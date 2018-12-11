/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Read {@code Klass::_layout_helper} and incorporate any useful stamp information based on any type
 * information in {@code klass}.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class KlassLayoutHelperNode extends FloatingNode implements Canonicalizable, Lowerable {

    public static final NodeClass<KlassLayoutHelperNode> TYPE = NodeClass.create(KlassLayoutHelperNode.class);
    @Input protected ValueNode klass;
    protected final GraalHotSpotVMConfig config;

    public KlassLayoutHelperNode(@InjectedNodeParameter GraalHotSpotVMConfig config, ValueNode klass) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.config = config;
        this.klass = klass;
    }

    public static ValueNode create(GraalHotSpotVMConfig config, ValueNode klass, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess) {
        Stamp stamp = StampFactory.forKind(JavaKind.Int);
        return canonical(null, config, klass, stamp, constantReflection, metaAccess);
    }

    @SuppressWarnings("unused")
    public static boolean intrinsify(GraphBuilderContext b, ResolvedJavaMethod method, @InjectedNodeParameter GraalHotSpotVMConfig config, ValueNode klass) {
        ValueNode valueNode = create(config, klass, b.getConstantReflection(), b.getMetaAccess());
        b.push(JavaKind.Int, b.append(valueNode));
        return true;
    }

    @Override
    public boolean inferStamp() {
        if (klass instanceof LoadHubNode) {
            LoadHubNode hub = (LoadHubNode) klass;
            Stamp hubStamp = hub.getValue().stamp(NodeView.DEFAULT);
            if (hubStamp instanceof ObjectStamp) {
                ObjectStamp objectStamp = (ObjectStamp) hubStamp;
                ResolvedJavaType type = objectStamp.type();
                if (type != null && !type.isJavaLangObject()) {
                    if (!type.isArray() && !type.isInterface()) {
                        /*
                         * Definitely some form of instance type.
                         */
                        return updateStamp(StampFactory.forInteger(JavaKind.Int, config.klassLayoutHelperNeutralValue, Integer.MAX_VALUE));
                    }
                    if (type.isArray()) {
                        return updateStamp(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, config.klassLayoutHelperNeutralValue - 1));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        } else {
            return canonical(this, config, klass, stamp(NodeView.DEFAULT), tool.getConstantReflection(), tool.getMetaAccess());
        }
    }

    private static ValueNode canonical(KlassLayoutHelperNode klassLayoutHelperNode, GraalHotSpotVMConfig config, ValueNode klass, Stamp stamp, ConstantReflectionProvider constantReflection,
                    MetaAccessProvider metaAccess) {
        KlassLayoutHelperNode self = klassLayoutHelperNode;
        if (klass.isConstant()) {
            if (!klass.asConstant().isDefaultForKind()) {
                Constant constant = stamp.readConstant(constantReflection.getMemoryAccessProvider(), klass.asConstant(), config.klassLayoutHelperOffset);
                return ConstantNode.forConstant(stamp, constant, metaAccess);
            }
        }
        if (klass instanceof LoadHubNode) {
            LoadHubNode hub = (LoadHubNode) klass;
            Stamp hubStamp = hub.getValue().stamp(NodeView.DEFAULT);
            if (hubStamp instanceof ObjectStamp) {
                ObjectStamp ostamp = (ObjectStamp) hubStamp;
                HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) ostamp.type();
                if (type != null && type.isArray() && !type.getComponentType().isPrimitive()) {
                    // The layout for all object arrays is the same.
                    Constant constant = stamp.readConstant(constantReflection.getMemoryAccessProvider(), type.klass(), config.klassLayoutHelperOffset);
                    return ConstantNode.forConstant(stamp, constant, metaAccess);
                }
            }
        }
        if (self == null) {
            self = new KlassLayoutHelperNode(config, klass);
        }
        return self;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public ValueNode getHub() {
        return klass;
    }
}

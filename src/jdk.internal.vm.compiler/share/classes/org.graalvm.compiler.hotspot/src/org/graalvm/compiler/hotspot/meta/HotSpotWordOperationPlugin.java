/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.hotspot.word.HotSpotOperation.HotspotOpcode.POINTER_EQ;
import static org.graalvm.compiler.hotspot.word.HotSpotOperation.HotspotOpcode.POINTER_NE;
import static org.graalvm.compiler.nodes.ConstantNode.forBoolean;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BridgeMethodUtils;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.nodes.LoadIndexedPointerNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.MetaspacePointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.MethodPointerStamp;
import org.graalvm.compiler.hotspot.word.HotSpotOperation;
import org.graalvm.compiler.hotspot.word.HotSpotOperation.HotspotOpcode;
import org.graalvm.compiler.hotspot.word.PointerCastNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.PointerEqualsNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extends {@link WordOperationPlugin} to handle {@linkplain HotSpotOperation HotSpot word
 * operations}.
 */
public class HotSpotWordOperationPlugin extends WordOperationPlugin {
    HotSpotWordOperationPlugin(SnippetReflectionProvider snippetReflection, WordTypes wordTypes) {
        super(snippetReflection, wordTypes);
    }

    @Override
    protected LoadIndexedNode createLoadIndexedNode(ValueNode array, ValueNode index, GuardingNode boundsCheck) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        Stamp componentStamp = wordTypes.getWordStamp(arrayType.getComponentType());
        if (componentStamp instanceof MetaspacePointerStamp) {
            return new LoadIndexedPointerNode(componentStamp, array, index, boundsCheck);
        } else {
            return super.createLoadIndexedNode(array, index, boundsCheck);
        }
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!wordTypes.isWordOperation(method)) {
            return false;
        }

        HotSpotOperation operation = BridgeMethodUtils.getAnnotation(HotSpotOperation.class, method);
        if (operation == null) {
            processWordOperation(b, args, wordTypes.getWordOperation(method, b.getMethod().getDeclaringClass()));
            return true;
        }
        processHotSpotWordOperation(b, method, args, operation);
        return true;
    }

    protected void processHotSpotWordOperation(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, HotSpotOperation operation) {
        JavaKind returnKind = method.getSignature().getReturnKind();
        switch (operation.opcode()) {
            case POINTER_EQ:
            case POINTER_NE:
                assert args.length == 2;
                HotspotOpcode opcode = operation.opcode();
                ValueNode left = args[0];
                ValueNode right = args[1];
                assert left.stamp(NodeView.DEFAULT) instanceof MetaspacePointerStamp : left + " " + left.stamp(NodeView.DEFAULT);
                assert right.stamp(NodeView.DEFAULT) instanceof MetaspacePointerStamp : right + " " + right.stamp(NodeView.DEFAULT);
                assert opcode == POINTER_EQ || opcode == POINTER_NE;

                PointerEqualsNode comparison = b.add(new PointerEqualsNode(left, right));
                ValueNode eqValue = b.add(forBoolean(opcode == POINTER_EQ));
                ValueNode neValue = b.add(forBoolean(opcode == POINTER_NE));
                b.addPush(returnKind, ConditionalNode.create(comparison, eqValue, neValue, NodeView.DEFAULT));
                break;

            case IS_NULL:
                assert args.length == 1;
                ValueNode pointer = args[0];
                assert pointer.stamp(NodeView.DEFAULT) instanceof MetaspacePointerStamp;

                LogicNode isNull = b.add(IsNullNode.create(pointer));
                b.addPush(returnKind, ConditionalNode.create(isNull, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                break;

            case FROM_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, new PointerCastNode(StampFactory.forKind(wordKind), args[0]));
                break;

            case TO_KLASS_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, new PointerCastNode(KlassPointerStamp.klass(), args[0]));
                break;

            case TO_METHOD_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, new PointerCastNode(MethodPointerStamp.method(), args[0]));
                break;

            case READ_KLASS_POINTER:
                assert args.length == 2 || args.length == 3;
                Stamp readStamp = KlassPointerStamp.klass();
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 2) {
                    location = any();
                } else {
                    assert args[2].isConstant();
                    location = snippetReflection.asObject(LocationIdentity.class, args[2].asJavaConstant());
                }
                ReadNode read = b.add(new ReadNode(address, location, readStamp, BarrierType.NONE));
                b.push(returnKind, read);
                break;

            default:
                throw GraalError.shouldNotReachHere("unknown operation: " + operation.opcode());
        }
    }
}

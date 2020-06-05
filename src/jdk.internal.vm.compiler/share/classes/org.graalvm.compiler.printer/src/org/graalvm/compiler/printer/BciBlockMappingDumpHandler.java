/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.printer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.java.BciBlockMapping.BciBlock;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.graphio.GraphElements;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphOutput.Builder;
import org.graalvm.graphio.GraphStructure;
import org.graalvm.graphio.GraphTypes;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

public class BciBlockMappingDumpHandler implements DebugDumpHandler {
    private static final BlockMappingElements ELEMENTS = new BlockMappingElements();
    private static final BlockMappingTypes TYPES = new BlockMappingTypes();
    private BlockMappingStructure structure;
    private int nextId;

    @Override
    public void dump(DebugContext debug, Object object, String format, Object... arguments) {
        OptionValues options = debug.getOptions();
        if (object instanceof BciBlockMapping && DebugOptions.PrintGraph.getValue(options) != PrintGraphTarget.Disable) {
            try {
                if (structure == null) {
                    structure = new BlockMappingStructure();
                }
                int id = nextId++;
                Builder<BciBlockMapping, BciBlock, ResolvedJavaMethod> builder = GraphOutput.newBuilder(structure).elements(ELEMENTS).types(TYPES).protocolVersion(7, 0);
                GraphOutput<BciBlockMapping, ResolvedJavaMethod> output = debug.buildOutput(builder);
                Map<Object, Object> properties = new HashMap<>();
                properties.put("hasJsrBytecodes", ((BciBlockMapping) object).hasJsrBytecodes);
                // ideally this should collaborate with the graph printer to open/close groups
                output.print((BciBlockMapping) object, properties, id, format, arguments);
                output.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to dump block mapping", e);
            }
        }
    }

    static class BciBlockClass {
        final BciBlock block;

        BciBlockClass(BciBlock block) {
            this.block = block;
        }
    }

    static class BlockEdges {
        final BciBlock block;

        BlockEdges(BciBlock block) {
            this.block = block;
        }
    }

    enum EdgeType {
        Successor,
        JsrSuccessor,
        RetSuccessor
    }

    private static final BlockEdges NO_EDGES = new BlockEdges(null);

    static class BlockMappingStructure implements GraphStructure<BciBlockMapping, BciBlock, BciBlockClass, BlockEdges> {
        private Map<BciBlock, Integer> artificialIds;
        private int nextArtifcialId = 1_000_000;

        @Override
        public BciBlockMapping graph(BciBlockMapping currentGraph, Object obj) {
            return obj instanceof BciBlockMapping ? (BciBlockMapping) obj : null;
        }

        @Override
        public Collection<BciBlock> nodes(BciBlockMapping graph) {
            return collectBlocks(graph);
        }

        private static Collection<BciBlock> collectBlocks(BciBlockMapping graph) {
            if (graph.getStartBlock() == null) {
                return Collections.emptySet();
            }
            HashSet<BciBlock> blocks = new HashSet<>();
            ArrayDeque<BciBlock> workStack = new ArrayDeque<>();
            workStack.push(graph.getStartBlock());
            while (!workStack.isEmpty()) {
                BciBlock block = workStack.pop();
                if (blocks.contains(block)) {
                    continue;
                }
                blocks.add(block);
                for (BciBlock successor : block.getSuccessors()) {
                    workStack.push(successor);
                }
                BciBlock jsrSuccessor = block.getJsrSuccessor();
                if (jsrSuccessor != null) {
                    workStack.push(jsrSuccessor);
                }
                BciBlock retSuccessor = block.getRetSuccessor();
                if (retSuccessor != null) {
                    workStack.push(retSuccessor);
                }
            }
            return blocks;
        }

        @Override
        public int nodesCount(BciBlockMapping graph) {
            return nodes(graph).size();
        }

        @Override
        public int nodeId(BciBlock node) {
            if (artificialIds != null) {
                Integer artificial = artificialIds.get(node);
                if (artificial != null) {
                    return artificial;
                }
            }
            int id = node.getId();
            if (id < 0) {
                if (artificialIds == null) {
                    artificialIds = new HashMap<>();
                }
                id = artificialIds.computeIfAbsent(node, b -> nextArtifcialId++);
            }
            return id;
        }

        @Override
        public boolean nodeHasPredecessor(BciBlock node) {
            return node.getPredecessorCount() > 0;
        }

        @Override
        public void nodeProperties(BciBlockMapping graph, BciBlock node, Map<String, ? super Object> properties) {
            node.getDebugProperties(properties);
        }

        @Override
        public BciBlock node(Object obj) {
            return obj instanceof BciBlock ? (BciBlock) obj : null;
        }

        @Override
        public BciBlockClass nodeClass(Object obj) {
            return obj instanceof BciBlockClass ? (BciBlockClass) obj : null;
        }

        @Override
        public BciBlockClass classForNode(BciBlock node) {
            return new BciBlockClass(node);
        }

        @Override
        public String nameTemplate(BciBlockClass nodeClass) {
            return "[{p#startBci}..{p#endBci}] ({p#assignedId})";
        }

        @Override
        public Object nodeClassType(BciBlockClass nodeClass) {
            return nodeClass.block.getClass();
        }

        @Override
        public BlockEdges portInputs(BciBlockClass nodeClass) {
            return NO_EDGES;
        }

        @Override
        public BlockEdges portOutputs(BciBlockClass nodeClass) {
            return new BlockEdges(nodeClass.block);
        }

        @Override
        public int portSize(BlockEdges port) {
            if (port.block == null) {
                return 0;
            }
            return 1 + (port.block.getJsrSuccessor() != null ? 1 : 0) + (port.block.getRetSuccessor() != null ? 1 : 0);
        }

        @Override
        public boolean edgeDirect(BlockEdges port, int index) {
            return index > 0;
        }

        @Override
        public String edgeName(BlockEdges port, int index) {
            if (index == 0) {
                return "successors";
            } else if (index == 1 && port.block.getJsrSuccessor() != null) {
                return "jsr successor";
            }
            GraalError.guarantee(index <= 2, Integer.toString(index));
            return "ret successor";
        }

        @Override
        public Object edgeType(BlockEdges port, int index) {
            if (index == 0) {
                return EdgeType.Successor;
            } else if (index == 1 && port.block.getJsrSuccessor() != null) {
                return EdgeType.JsrSuccessor;
            }
            GraalError.guarantee(index <= 2, Integer.toString(index));
            return EdgeType.RetSuccessor;
        }

        @Override
        public Collection<? extends BciBlock> edgeNodes(BciBlockMapping graph, BciBlock node, BlockEdges port, int index) {
            if (index == 0) {
                return node.getSuccessors();
            } else if (index == 1 && port.block.getJsrSuccessor() != null) {
                return Collections.singletonList(node.getJsrSuccessor());
            }
            GraalError.guarantee(index <= 2, Integer.toString(index));
            return Collections.singletonList(node.getRetSuccessor());
        }
    }

    static class BlockMappingElements implements GraphElements<ResolvedJavaMethod, Object, Signature, Object> {

        @Override
        public ResolvedJavaMethod method(Object object) {
            if (object instanceof Bytecode) {
                return ((Bytecode) object).getMethod();
            } else if (object instanceof ResolvedJavaMethod) {
                return ((ResolvedJavaMethod) object);
            } else {
                return null;
            }
        }

        @Override
        public byte[] methodCode(ResolvedJavaMethod method) {
            return method.getCode();
        }

        @Override
        public int methodModifiers(ResolvedJavaMethod method) {
            return method.getModifiers();
        }

        @Override
        public Signature methodSignature(ResolvedJavaMethod method) {
            return method.getSignature();
        }

        @Override
        public String methodName(ResolvedJavaMethod method) {
            return method.getName();
        }

        @Override
        public Object methodDeclaringClass(ResolvedJavaMethod method) {
            return method.getDeclaringClass();
        }

        @Override
        public Object field(Object object) {
            return null;
        }

        @Override
        public Signature signature(Object object) {
            if (object instanceof Signature) {
                return (Signature) object;
            }
            return null;
        }

        @Override
        public Object nodeSourcePosition(Object object) {
            return null;
        }

        @Override
        public StackTraceElement methodStackTraceElement(ResolvedJavaMethod method, int bci, Object pos) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public int nodeSourcePositionBCI(Object pos) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public Object nodeSourcePositionCaller(Object pos) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public ResolvedJavaMethod nodeSourcePositionMethod(Object pos) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public int signatureParameterCount(Signature signature) {
            return signature.getParameterCount(false);
        }

        @Override
        public String signatureParameterTypeName(Signature signature, int index) {
            return signature.getParameterType(index, null).getName();
        }

        @Override
        public String signatureReturnTypeName(Signature signature) {
            return signature.getReturnType(null).getName();
        }

        @Override
        public Object fieldDeclaringClass(Object field) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public String fieldName(Object field) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public String fieldTypeName(Object field) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public int fieldModifiers(Object field) {
            throw GraalError.shouldNotReachHere();
        }
    }

    static class BlockMappingTypes implements GraphTypes {
        @Override
        public Class<?> enumClass(Object enumValue) {
            if (enumValue instanceof Enum<?>) {
                return enumValue.getClass();
            }
            return null;
        }

        @Override
        public int enumOrdinal(Object obj) {
            if (obj instanceof Enum<?>) {
                return ((Enum<?>) obj).ordinal();
            }
            return -1;
        }

        @SuppressWarnings("unchecked")
        @Override
        public String[] enumTypeValues(Object clazz) {
            if (clazz instanceof Class<?>) {
                Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) clazz;
                Enum<?>[] constants = enumClass.getEnumConstants();
                if (constants != null) {
                    String[] names = new String[constants.length];
                    for (int i = 0; i < constants.length; i++) {
                        names[i] = constants[i].name();
                    }
                    return names;
                }
            }
            return null;
        }

        @Override
        public String typeName(Object clazz) {
            if (clazz instanceof Class<?>) {
                return ((Class<?>) clazz).getName();
            }
            if (clazz instanceof JavaType) {
                return ((JavaType) clazz).toJavaName();
            }
            return null;
        }
    }
}

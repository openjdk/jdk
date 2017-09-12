/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import static org.graalvm.compiler.core.common.Fields.translateInto;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.graph.Edges.translateInto;
import static org.graalvm.compiler.graph.Graph.isModificationCountsEnabled;
import static org.graalvm.compiler.graph.InputEdges.translateInto;
import static org.graalvm.compiler.graph.Node.WithAllEdges;
import static org.graalvm.compiler.graph.UnsafeAccess.UNSAFE;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.core.common.FieldIntrospection;
import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.core.common.FieldsScanner;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.Edges.Type;
import org.graalvm.compiler.graph.Graph.DuplicationReplacement;
import org.graalvm.compiler.graph.Node.EdgeVisitor;
import org.graalvm.compiler.graph.Node.Input;
import org.graalvm.compiler.graph.Node.OptionalInput;
import org.graalvm.compiler.graph.Node.Successor;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.Equivalence;

/**
 * Metadata for every {@link Node} type. The metadata includes:
 * <ul>
 * <li>The offsets of fields annotated with {@link Input} and {@link Successor} as well as methods
 * for iterating over such fields.</li>
 * <li>The identifier for an {@link IterableNodeType} class.</li>
 * </ul>
 */
public final class NodeClass<T> extends FieldIntrospection<T> {

    // Timers for creation of a NodeClass instance
    private static final TimerKey Init_FieldScanning = DebugContext.timer("NodeClass.Init.FieldScanning");
    private static final TimerKey Init_FieldScanningInner = DebugContext.timer("NodeClass.Init.FieldScanning.Inner");
    private static final TimerKey Init_AnnotationParsing = DebugContext.timer("NodeClass.Init.AnnotationParsing");
    private static final TimerKey Init_Edges = DebugContext.timer("NodeClass.Init.Edges");
    private static final TimerKey Init_Data = DebugContext.timer("NodeClass.Init.Data");
    private static final TimerKey Init_AllowedUsages = DebugContext.timer("NodeClass.Init.AllowedUsages");
    private static final TimerKey Init_IterableIds = DebugContext.timer("NodeClass.Init.IterableIds");

    public static final long MAX_EDGES = 8;
    public static final long MAX_LIST_EDGES = 6;
    public static final long OFFSET_MASK = 0xFC;
    public static final long LIST_MASK = 0x01;
    public static final long NEXT_EDGE = 0x08;

    @SuppressWarnings("try")
    private static <T extends Annotation> T getAnnotationTimed(AnnotatedElement e, Class<T> annotationClass, DebugContext debug) {
        try (DebugCloseable s = Init_AnnotationParsing.start(debug)) {
            return e.getAnnotation(annotationClass);
        }
    }

    /**
     * Gets the {@link NodeClass} associated with a given {@link Class}.
     */
    public static <T> NodeClass<T> create(Class<T> c) {
        assert get(c) == null;
        Class<? super T> superclass = c.getSuperclass();
        NodeClass<? super T> nodeSuperclass = null;
        if (superclass != NODE_CLASS) {
            nodeSuperclass = get(superclass);
        }
        return new NodeClass<>(c, nodeSuperclass);
    }

    @SuppressWarnings("unchecked")
    public static <T> NodeClass<T> get(Class<T> superclass) {
        try {
            Field field = superclass.getDeclaredField("TYPE");
            field.setAccessible(true);
            return (NodeClass<T>) field.get(null);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Class<?> NODE_CLASS = Node.class;
    private static final Class<?> INPUT_LIST_CLASS = NodeInputList.class;
    private static final Class<?> SUCCESSOR_LIST_CLASS = NodeSuccessorList.class;

    private static AtomicInteger nextIterableId = new AtomicInteger();
    private static AtomicInteger nextLeafId = new AtomicInteger();

    private final InputEdges inputs;
    private final SuccessorEdges successors;
    private final NodeClass<? super T> superNodeClass;

    private final boolean canGVN;
    private final int startGVNNumber;
    private final String nameTemplate;
    private final int iterableId;
    private final EnumSet<InputType> allowedUsageTypes;
    private int[] iterableIds;
    private final long inputsIteration;
    private final long successorIteration;

    private static final CounterKey ITERABLE_NODE_TYPES = DebugContext.counter("IterableNodeTypes");

    /**
     * Determines if this node type implements {@link Canonicalizable}.
     */
    private final boolean isCanonicalizable;

    /**
     * Determines if this node type implements {@link BinaryCommutative}.
     */
    private final boolean isCommutative;

    /**
     * Determines if this node type implements {@link Simplifiable}.
     */
    private final boolean isSimplifiable;
    private final boolean isLeafNode;

    private final int leafId;

    public NodeClass(Class<T> clazz, NodeClass<? super T> superNodeClass) {
        this(clazz, superNodeClass, new FieldsScanner.DefaultCalcOffset(), null, 0);
    }

    @SuppressWarnings("try")
    public NodeClass(Class<T> clazz, NodeClass<? super T> superNodeClass, FieldsScanner.CalcOffset calcOffset, int[] presetIterableIds, int presetIterableId) {
        super(clazz);
        DebugContext debug = DebugContext.forCurrentThread();
        this.superNodeClass = superNodeClass;
        assert NODE_CLASS.isAssignableFrom(clazz);

        this.isCanonicalizable = Canonicalizable.class.isAssignableFrom(clazz);
        this.isCommutative = BinaryCommutative.class.isAssignableFrom(clazz);
        if (Canonicalizable.Unary.class.isAssignableFrom(clazz) || Canonicalizable.Binary.class.isAssignableFrom(clazz)) {
            assert Canonicalizable.Unary.class.isAssignableFrom(clazz) ^ Canonicalizable.Binary.class.isAssignableFrom(clazz) : clazz + " should implement either Unary or Binary, not both";
        }

        this.isSimplifiable = Simplifiable.class.isAssignableFrom(clazz);

        NodeFieldsScanner fs = new NodeFieldsScanner(calcOffset, superNodeClass, debug);
        try (DebugCloseable t = Init_FieldScanning.start(debug)) {
            fs.scan(clazz, clazz.getSuperclass(), false);
        }

        try (DebugCloseable t1 = Init_Edges.start(debug)) {
            successors = new SuccessorEdges(fs.directSuccessors, fs.successors);
            successorIteration = computeIterationMask(successors.type(), successors.getDirectCount(), successors.getOffsets());
            inputs = new InputEdges(fs.directInputs, fs.inputs);
            inputsIteration = computeIterationMask(inputs.type(), inputs.getDirectCount(), inputs.getOffsets());
        }
        try (DebugCloseable t1 = Init_Data.start(debug)) {
            data = new Fields(fs.data);
        }

        isLeafNode = inputs.getCount() + successors.getCount() == 0;
        if (isLeafNode) {
            this.leafId = nextLeafId.getAndIncrement();
        } else {
            this.leafId = -1;
        }

        canGVN = Node.ValueNumberable.class.isAssignableFrom(clazz);
        startGVNNumber = clazz.getName().hashCode();

        NodeInfo info = getAnnotationTimed(clazz, NodeInfo.class, debug);
        assert info != null : "Missing NodeInfo annotation on " + clazz;
        if (!info.nameTemplate().isEmpty()) {
            this.nameTemplate = info.nameTemplate();
        } else if (!info.shortName().isEmpty()) {
            this.nameTemplate = info.shortName();
        } else {
            this.nameTemplate = "";
        }

        try (DebugCloseable t1 = Init_AllowedUsages.start(debug)) {
            allowedUsageTypes = superNodeClass == null ? EnumSet.noneOf(InputType.class) : superNodeClass.allowedUsageTypes.clone();
            allowedUsageTypes.addAll(Arrays.asList(info.allowedUsageTypes()));
        }

        if (presetIterableIds != null) {
            this.iterableIds = presetIterableIds;
            this.iterableId = presetIterableId;
        } else if (IterableNodeType.class.isAssignableFrom(clazz)) {
            ITERABLE_NODE_TYPES.increment(debug);
            try (DebugCloseable t1 = Init_IterableIds.start(debug)) {
                this.iterableId = nextIterableId.getAndIncrement();

                NodeClass<?> snc = superNodeClass;
                while (snc != null && IterableNodeType.class.isAssignableFrom(snc.getClazz())) {
                    snc.addIterableId(iterableId);
                    snc = snc.superNodeClass;
                }

                this.iterableIds = new int[]{iterableId};
            }
        } else {
            this.iterableId = Node.NOT_ITERABLE;
            this.iterableIds = null;
        }
        assert verifyIterableIds();

        try (DebugContext.Scope scope = debug.scope("NodeCosts")) {
            /*
             * Note: We do not check for the existence of the node cost annotations during
             * construction as not every node needs to have them set. However if costs are queried,
             * after the construction of the node class, they must be properly set. This is
             * important as we can not trust our cost model if there are unspecified nodes. Nodes
             * that do not need cost annotations are e.g. abstractions like FixedNode or
             * FloatingNode or ValueNode. Sub classes where costs are not specified will ask the
             * superclass for their costs during node class initialization. Therefore getters for
             * cycles and size can omit verification during creation.
             */
            NodeCycles c = info.cycles();
            if (c == NodeCycles.CYCLES_UNSET) {
                cycles = superNodeClass != null ? superNodeClass.cycles : NodeCycles.CYCLES_UNSET;
            } else {
                cycles = c;
            }
            assert cycles != null;
            NodeSize s = info.size();
            if (s == NodeSize.SIZE_UNSET) {
                size = superNodeClass != null ? superNodeClass.size : NodeSize.SIZE_UNSET;
            } else {
                size = s;
            }
            assert size != null;
            debug.log("Node cost for node of type __| %s |_, cycles:%s,size:%s", clazz, cycles, size);
        }
    }

    private final NodeCycles cycles;
    private final NodeSize size;

    public NodeCycles cycles() {
        return cycles;
    }

    public NodeSize size() {
        return size;
    }

    public static long computeIterationMask(Type type, int directCount, long[] offsets) {
        long mask = 0;
        if (offsets.length > NodeClass.MAX_EDGES) {
            throw new GraalError("Exceeded maximum of %d edges (%s)", NodeClass.MAX_EDGES, type);
        }
        if (offsets.length - directCount > NodeClass.MAX_LIST_EDGES) {
            throw new GraalError("Exceeded maximum of %d list edges (%s)", NodeClass.MAX_LIST_EDGES, type);
        }

        for (int i = offsets.length - 1; i >= 0; i--) {
            long offset = offsets[i];
            assert ((offset & 0xFF) == offset) : "field offset too large!";
            mask <<= NodeClass.NEXT_EDGE;
            mask |= offset;
            if (i >= directCount) {
                mask |= 0x3;
            }
        }
        return mask;
    }

    private synchronized void addIterableId(int newIterableId) {
        assert !containsId(newIterableId, iterableIds);
        int[] copy = Arrays.copyOf(iterableIds, iterableIds.length + 1);
        copy[iterableIds.length] = newIterableId;
        iterableIds = copy;
    }

    private boolean verifyIterableIds() {
        NodeClass<?> snc = superNodeClass;
        while (snc != null && IterableNodeType.class.isAssignableFrom(snc.getClazz())) {
            assert containsId(iterableId, snc.iterableIds);
            snc = snc.superNodeClass;
        }
        return true;
    }

    private static boolean containsId(int iterableId, int[] iterableIds) {
        for (int i : iterableIds) {
            if (i == iterableId) {
                return true;
            }
        }
        return false;
    }

    private String shortName;

    public String shortName() {
        if (shortName == null) {
            NodeInfo info = getClazz().getAnnotation(NodeInfo.class);
            if (!info.shortName().isEmpty()) {
                shortName = info.shortName();
            } else {
                String localShortName = getClazz().getSimpleName();
                if (localShortName.endsWith("Node") && !localShortName.equals("StartNode") && !localShortName.equals("EndNode")) {
                    shortName = localShortName.substring(0, localShortName.length() - 4);
                } else {
                    shortName = localShortName;
                }
            }
        }
        return shortName;
    }

    @Override
    public Fields[] getAllFields() {
        return new Fields[]{data, inputs, successors};
    }

    int[] iterableIds() {
        return iterableIds;
    }

    public int iterableId() {
        return iterableId;
    }

    public boolean valueNumberable() {
        return canGVN;
    }

    /**
     * Determines if this node type implements {@link Canonicalizable}.
     */
    public boolean isCanonicalizable() {
        return isCanonicalizable;
    }

    /**
     * Determines if this node type implements {@link BinaryCommutative}.
     */
    public boolean isCommutative() {
        return isCommutative;
    }

    /**
     * Determines if this node type implements {@link Simplifiable}.
     */
    public boolean isSimplifiable() {
        return isSimplifiable;
    }

    static int allocatedNodeIterabledIds() {
        return nextIterableId.get();
    }

    public EnumSet<InputType> getAllowedUsageTypes() {
        return allowedUsageTypes;
    }

    /**
     * Describes a field representing an input or successor edge in a node.
     */
    protected static class EdgeInfo extends FieldsScanner.FieldInfo {

        public EdgeInfo(long offset, String name, Class<?> type, Class<?> declaringClass) {
            super(offset, name, type, declaringClass);
        }

        /**
         * Sorts non-list edges before list edges.
         */
        @Override
        public int compareTo(FieldsScanner.FieldInfo o) {
            if (NodeList.class.isAssignableFrom(o.type)) {
                if (!NodeList.class.isAssignableFrom(type)) {
                    return -1;
                }
            } else {
                if (NodeList.class.isAssignableFrom(type)) {
                    return 1;
                }
            }
            return super.compareTo(o);
        }
    }

    /**
     * Describes a field representing an {@linkplain Type#Inputs input} edge in a node.
     */
    protected static class InputInfo extends EdgeInfo {
        final InputType inputType;
        final boolean optional;

        public InputInfo(long offset, String name, Class<?> type, Class<?> declaringClass, InputType inputType, boolean optional) {
            super(offset, name, type, declaringClass);
            this.inputType = inputType;
            this.optional = optional;
        }

        @Override
        public String toString() {
            return super.toString() + "{inputType=" + inputType + ", optional=" + optional + "}";
        }
    }

    protected static class NodeFieldsScanner extends FieldsScanner {

        public final ArrayList<InputInfo> inputs = new ArrayList<>();
        public final ArrayList<EdgeInfo> successors = new ArrayList<>();
        int directInputs;
        int directSuccessors;
        final DebugContext debug;

        protected NodeFieldsScanner(FieldsScanner.CalcOffset calc, NodeClass<?> superNodeClass, DebugContext debug) {
            super(calc);
            this.debug = debug;
            if (superNodeClass != null) {
                translateInto(superNodeClass.inputs, inputs);
                translateInto(superNodeClass.successors, successors);
                translateInto(superNodeClass.data, data);
                directInputs = superNodeClass.inputs.getDirectCount();
                directSuccessors = superNodeClass.successors.getDirectCount();
            }
        }

        @SuppressWarnings("try")
        @Override
        protected void scanField(Field field, long offset) {
            Input inputAnnotation = getAnnotationTimed(field, Node.Input.class, debug);
            OptionalInput optionalInputAnnotation = getAnnotationTimed(field, Node.OptionalInput.class, debug);
            Successor successorAnnotation = getAnnotationTimed(field, Successor.class, debug);
            try (DebugCloseable s = Init_FieldScanningInner.start(debug)) {
                Class<?> type = field.getType();
                int modifiers = field.getModifiers();

                if (inputAnnotation != null || optionalInputAnnotation != null) {
                    assert successorAnnotation == null : "field cannot be both input and successor";
                    if (INPUT_LIST_CLASS.isAssignableFrom(type)) {
                        // NodeInputList fields should not be final since they are
                        // written (via Unsafe) in clearInputs()
                        GraalError.guarantee(!Modifier.isFinal(modifiers), "NodeInputList input field %s should not be final", field);
                        GraalError.guarantee(!Modifier.isPublic(modifiers), "NodeInputList input field %s should not be public", field);
                    } else {
                        GraalError.guarantee(NODE_CLASS.isAssignableFrom(type) || type.isInterface(), "invalid input type: %s", type);
                        GraalError.guarantee(!Modifier.isFinal(modifiers), "Node input field %s should not be final", field);
                        directInputs++;
                    }
                    InputType inputType;
                    if (inputAnnotation != null) {
                        assert optionalInputAnnotation == null : "inputs can either be optional or non-optional";
                        inputType = inputAnnotation.value();
                    } else {
                        inputType = optionalInputAnnotation.value();
                    }
                    inputs.add(new InputInfo(offset, field.getName(), type, field.getDeclaringClass(), inputType, field.isAnnotationPresent(Node.OptionalInput.class)));
                } else if (successorAnnotation != null) {
                    if (SUCCESSOR_LIST_CLASS.isAssignableFrom(type)) {
                        // NodeSuccessorList fields should not be final since they are
                        // written (via Unsafe) in clearSuccessors()
                        GraalError.guarantee(!Modifier.isFinal(modifiers), "NodeSuccessorList successor field % should not be final", field);
                        GraalError.guarantee(!Modifier.isPublic(modifiers), "NodeSuccessorList successor field %s should not be public", field);
                    } else {
                        GraalError.guarantee(NODE_CLASS.isAssignableFrom(type), "invalid successor type: %s", type);
                        GraalError.guarantee(!Modifier.isFinal(modifiers), "Node successor field %s should not be final", field);
                        directSuccessors++;
                    }
                    successors.add(new EdgeInfo(offset, field.getName(), type, field.getDeclaringClass()));
                } else {
                    GraalError.guarantee(!NODE_CLASS.isAssignableFrom(type) || field.getName().equals("Null"), "suspicious node field: %s", field);
                    GraalError.guarantee(!INPUT_LIST_CLASS.isAssignableFrom(type), "suspicious node input list field: %s", field);
                    GraalError.guarantee(!SUCCESSOR_LIST_CLASS.isAssignableFrom(type), "suspicious node successor list field: %s", field);
                    super.scanField(field, offset);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("NodeClass ").append(getClazz().getSimpleName()).append(" [");
        inputs.appendFields(str);
        str.append("] [");
        successors.appendFields(str);
        str.append("] [");
        data.appendFields(str);
        str.append("]");
        return str.toString();
    }

    private static int deepHashCode0(Object o) {
        if (o == null) {
            return 0;
        } else if (!o.getClass().isArray()) {
            return o.hashCode();
        } else if (o instanceof Object[]) {
            return Arrays.deepHashCode((Object[]) o);
        } else if (o instanceof byte[]) {
            return Arrays.hashCode((byte[]) o);
        } else if (o instanceof short[]) {
            return Arrays.hashCode((short[]) o);
        } else if (o instanceof int[]) {
            return Arrays.hashCode((int[]) o);
        } else if (o instanceof long[]) {
            return Arrays.hashCode((long[]) o);
        } else if (o instanceof char[]) {
            return Arrays.hashCode((char[]) o);
        } else if (o instanceof float[]) {
            return Arrays.hashCode((float[]) o);
        } else if (o instanceof double[]) {
            return Arrays.hashCode((double[]) o);
        } else if (o instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) o);
        } else {
            throw shouldNotReachHere();
        }
    }

    public int valueNumber(Node n) {
        int number = 0;
        if (canGVN) {
            number = startGVNNumber;
            for (int i = 0; i < data.getCount(); ++i) {
                Class<?> type = data.getType(i);
                if (type.isPrimitive()) {
                    if (type == Integer.TYPE) {
                        int intValue = data.getInt(n, i);
                        number += intValue;
                    } else if (type == Long.TYPE) {
                        long longValue = data.getLong(n, i);
                        number += longValue ^ (longValue >>> 32);
                    } else if (type == Boolean.TYPE) {
                        boolean booleanValue = data.getBoolean(n, i);
                        if (booleanValue) {
                            number += 7;
                        }
                    } else if (type == Float.TYPE) {
                        float floatValue = data.getFloat(n, i);
                        number += Float.floatToRawIntBits(floatValue);
                    } else if (type == Double.TYPE) {
                        double doubleValue = data.getDouble(n, i);
                        long longValue = Double.doubleToRawLongBits(doubleValue);
                        number += longValue ^ (longValue >>> 32);
                    } else if (type == Short.TYPE) {
                        short shortValue = data.getShort(n, i);
                        number += shortValue;
                    } else if (type == Character.TYPE) {
                        char charValue = data.getChar(n, i);
                        number += charValue;
                    } else if (type == Byte.TYPE) {
                        byte byteValue = data.getByte(n, i);
                        number += byteValue;
                    } else {
                        assert false : "unhandled property type: " + type;
                    }
                } else {
                    Object o = data.getObject(n, i);
                    number += deepHashCode0(o);
                }
                number *= 13;
            }
        }
        return number;
    }

    private static boolean deepEquals0(Object e1, Object e2) {
        if (e1 == e2) {
            return true;
        } else if (e1 == null || e2 == null) {
            return false;
        } else if (!e1.getClass().isArray() || e1.getClass() != e2.getClass()) {
            return e1.equals(e2);
        } else if (e1 instanceof Object[] && e2 instanceof Object[]) {
            return deepEquals((Object[]) e1, (Object[]) e2);
        } else if (e1 instanceof int[]) {
            return Arrays.equals((int[]) e1, (int[]) e2);
        } else if (e1 instanceof long[]) {
            return Arrays.equals((long[]) e1, (long[]) e2);
        } else if (e1 instanceof byte[]) {
            return Arrays.equals((byte[]) e1, (byte[]) e2);
        } else if (e1 instanceof char[]) {
            return Arrays.equals((char[]) e1, (char[]) e2);
        } else if (e1 instanceof short[]) {
            return Arrays.equals((short[]) e1, (short[]) e2);
        } else if (e1 instanceof float[]) {
            return Arrays.equals((float[]) e1, (float[]) e2);
        } else if (e1 instanceof double[]) {
            return Arrays.equals((double[]) e1, (double[]) e2);
        } else if (e1 instanceof boolean[]) {
            return Arrays.equals((boolean[]) e1, (boolean[]) e2);
        } else {
            throw shouldNotReachHere();
        }
    }

    private static boolean deepEquals(Object[] a1, Object[] a2) {
        int length = a1.length;
        if (a2.length != length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (!deepEquals0(a1[i], a2[i])) {
                return false;
            }
        }
        return true;
    }

    public boolean dataEquals(Node a, Node b) {
        assert a.getClass() == b.getClass();
        for (int i = 0; i < data.getCount(); ++i) {
            Class<?> type = data.getType(i);
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {
                    int aInt = data.getInt(a, i);
                    int bInt = data.getInt(b, i);
                    if (aInt != bInt) {
                        return false;
                    }
                } else if (type == Boolean.TYPE) {
                    boolean aBoolean = data.getBoolean(a, i);
                    boolean bBoolean = data.getBoolean(b, i);
                    if (aBoolean != bBoolean) {
                        return false;
                    }
                } else if (type == Long.TYPE) {
                    long aLong = data.getLong(a, i);
                    long bLong = data.getLong(b, i);
                    if (aLong != bLong) {
                        return false;
                    }
                } else if (type == Float.TYPE) {
                    float aFloat = data.getFloat(a, i);
                    float bFloat = data.getFloat(b, i);
                    if (aFloat != bFloat) {
                        return false;
                    }
                } else if (type == Double.TYPE) {
                    double aDouble = data.getDouble(a, i);
                    double bDouble = data.getDouble(b, i);
                    if (aDouble != bDouble) {
                        return false;
                    }
                } else if (type == Short.TYPE) {
                    short aShort = data.getShort(a, i);
                    short bShort = data.getShort(b, i);
                    if (aShort != bShort) {
                        return false;
                    }
                } else if (type == Character.TYPE) {
                    char aChar = data.getChar(a, i);
                    char bChar = data.getChar(b, i);
                    if (aChar != bChar) {
                        return false;
                    }
                } else if (type == Byte.TYPE) {
                    byte aByte = data.getByte(a, i);
                    byte bByte = data.getByte(b, i);
                    if (aByte != bByte) {
                        return false;
                    }
                } else {
                    assert false : "unhandled type: " + type;
                }
            } else {
                Object objectA = data.getObject(a, i);
                Object objectB = data.getObject(b, i);
                if (objectA != objectB) {
                    if (objectA != null && objectB != null) {
                        if (!deepEquals0(objectA, objectB)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean isValid(Position pos, NodeClass<?> from, Edges fromEdges) {
        if (this == from) {
            return true;
        }
        Edges toEdges = getEdges(fromEdges.type());
        if (pos.getIndex() >= toEdges.getCount()) {
            return false;
        }
        if (pos.getIndex() >= fromEdges.getCount()) {
            return false;
        }
        return toEdges.isSame(fromEdges, pos.getIndex());
    }

    static void updateEdgesInPlace(Node node, InplaceUpdateClosure duplicationReplacement, Edges edges) {
        int index = 0;
        Type curType = edges.type();
        int directCount = edges.getDirectCount();
        final long[] curOffsets = edges.getOffsets();
        while (index < directCount) {
            Node edge = Edges.getNode(node, curOffsets, index);
            if (edge != null) {
                Node newEdge = duplicationReplacement.replacement(edge, curType);
                if (curType == Edges.Type.Inputs) {
                    node.updateUsages(null, newEdge);
                } else {
                    node.updatePredecessor(null, newEdge);
                }
                edges.initializeNode(node, index, newEdge);
            }
            index++;
        }

        while (index < edges.getCount()) {
            NodeList<Node> list = Edges.getNodeList(node, curOffsets, index);
            if (list != null) {
                edges.initializeList(node, index, updateEdgeListCopy(node, list, duplicationReplacement, curType));
            }
            index++;
        }
    }

    void updateInputSuccInPlace(Node node, InplaceUpdateClosure duplicationReplacement) {
        updateEdgesInPlace(node, duplicationReplacement, inputs);
        updateEdgesInPlace(node, duplicationReplacement, successors);
    }

    private static NodeList<Node> updateEdgeListCopy(Node node, NodeList<Node> list, InplaceUpdateClosure duplicationReplacement, Edges.Type type) {
        NodeList<Node> result = type == Edges.Type.Inputs ? new NodeInputList<>(node, list.size()) : new NodeSuccessorList<>(node, list.size());

        for (int i = 0; i < list.count(); ++i) {
            Node oldNode = list.get(i);
            if (oldNode != null) {
                Node newNode = duplicationReplacement.replacement(oldNode, type);
                result.set(i, newNode);
            }
        }
        return result;
    }

    /**
     * Gets the input or successor edges defined by this node class.
     */
    public Edges getEdges(Edges.Type type) {
        return type == Edges.Type.Inputs ? inputs : successors;
    }

    public Edges getInputEdges() {
        return inputs;
    }

    public Edges getSuccessorEdges() {
        return successors;
    }

    /**
     * Returns a newly allocated node for which no subclass-specific constructor has been called.
     */
    @SuppressWarnings("unchecked")
    public Node allocateInstance() {
        try {
            Node node = (Node) UNSAFE.allocateInstance(getJavaClass());
            node.init((NodeClass<? extends Node>) this);
            return node;
        } catch (InstantiationException ex) {
            throw shouldNotReachHere(ex);
        }
    }

    public Class<T> getJavaClass() {
        return getClazz();
    }

    /**
     * The template used to build the {@link Verbosity#Name} version. Variable parts are specified
     * using &#123;i#inputName&#125; or &#123;p#propertyName&#125;. If no
     * {@link NodeInfo#nameTemplate() template} is specified, it uses {@link NodeInfo#shortName()}.
     * If none of the two is specified, it returns an empty string.
     */
    public String getNameTemplate() {
        return nameTemplate;
    }

    interface InplaceUpdateClosure {

        Node replacement(Node node, Edges.Type type);
    }

    static EconomicMap<Node, Node> addGraphDuplicate(final Graph graph, final Graph oldGraph, int estimatedNodeCount, Iterable<? extends Node> nodes, final DuplicationReplacement replacements) {
        final EconomicMap<Node, Node> newNodes;
        int denseThreshold = oldGraph.getNodeCount() + oldGraph.getNodesDeletedSinceLastCompression() >> 4;
        if (estimatedNodeCount > denseThreshold) {
            // Use dense map
            newNodes = new NodeMap<>(oldGraph);
        } else {
            // Use sparse map
            newNodes = EconomicMap.create(Equivalence.IDENTITY);
        }
        createNodeDuplicates(graph, nodes, replacements, newNodes);

        InplaceUpdateClosure replacementClosure = new InplaceUpdateClosure() {

            @Override
            public Node replacement(Node node, Edges.Type type) {
                Node target = newNodes.get(node);
                if (target == null) {
                    Node replacement = node;
                    if (replacements != null) {
                        replacement = replacements.replacement(node);
                    }
                    if (replacement != node) {
                        target = replacement;
                    } else if (node.graph() == graph && type == Edges.Type.Inputs) {
                        // patch to the outer world
                        target = node;
                    }

                }
                return target;
            }

        };

        // re-wire inputs
        for (Node oldNode : nodes) {
            Node node = newNodes.get(oldNode);
            NodeClass<?> nodeClass = node.getNodeClass();
            if (replacements == null || replacements.replacement(oldNode) == oldNode) {
                nodeClass.updateInputSuccInPlace(node, replacementClosure);
            } else {
                transferEdgesDifferentNodeClass(graph, replacements, newNodes, oldNode, node);
            }
        }

        return newNodes;
    }

    private static void createNodeDuplicates(final Graph graph, Iterable<? extends Node> nodes, final DuplicationReplacement replacements, final EconomicMap<Node, Node> newNodes) {
        for (Node node : nodes) {
            if (node != null) {
                assert !node.isDeleted() : "trying to duplicate deleted node: " + node;
                Node replacement = node;
                if (replacements != null) {
                    replacement = replacements.replacement(node);
                }
                if (replacement != node) {
                    assert replacement != null;
                    newNodes.put(node, replacement);
                } else {
                    Node newNode = node.clone(graph, WithAllEdges);
                    assert newNode.getNodeClass().isLeafNode() || newNode.hasNoUsages();
                    assert newNode.getClass() == node.getClass();
                    newNodes.put(node, newNode);
                }
            }
        }
    }

    private static void transferEdgesDifferentNodeClass(final Graph graph, final DuplicationReplacement replacements, final EconomicMap<Node, Node> newNodes, Node oldNode, Node node) {
        transferEdges(graph, replacements, newNodes, oldNode, node, Edges.Type.Inputs);
        transferEdges(graph, replacements, newNodes, oldNode, node, Edges.Type.Successors);
    }

    private static void transferEdges(final Graph graph, final DuplicationReplacement replacements, final EconomicMap<Node, Node> newNodes, Node oldNode, Node node, Edges.Type type) {
        NodeClass<?> nodeClass = node.getNodeClass();
        NodeClass<?> oldNodeClass = oldNode.getNodeClass();
        Edges oldEdges = oldNodeClass.getEdges(type);
        for (Position pos : oldEdges.getPositionsIterable(oldNode)) {
            if (!nodeClass.isValid(pos, oldNodeClass, oldEdges)) {
                continue;
            }
            Node oldEdge = pos.get(oldNode);
            if (oldEdge != null) {
                Node target = newNodes.get(oldEdge);
                if (target == null) {
                    Node replacement = oldEdge;
                    if (replacements != null) {
                        replacement = replacements.replacement(oldEdge);
                    }
                    if (replacement != oldEdge) {
                        target = replacement;
                    } else if (type == Edges.Type.Inputs && oldEdge.graph() == graph) {
                        // patch to the outer world
                        target = oldEdge;
                    }
                }
                pos.set(node, target);
            }
        }
    }

    /**
     * @return true if the node has no inputs and no successors
     */
    public boolean isLeafNode() {
        return isLeafNode;
    }

    public int getLeafId() {
        return this.leafId;
    }

    public NodeClass<? super T> getSuperNodeClass() {
        return superNodeClass;
    }

    public long inputsIteration() {
        return inputsIteration;
    }

    /**
     * An iterator that will iterate over edges.
     *
     * An iterator of this type will not return null values, unless edges are modified concurrently.
     * Concurrent modifications are detected by an assertion on a best-effort basis.
     */
    private static class RawEdgesIterator implements Iterator<Node> {
        protected final Node node;
        protected long mask;
        protected Node nextValue;

        RawEdgesIterator(Node node, long mask) {
            this.node = node;
            this.mask = mask;
        }

        @Override
        public boolean hasNext() {
            Node next = nextValue;
            if (next != null) {
                return true;
            } else {
                nextValue = forward();
                return nextValue != null;
            }
        }

        private Node forward() {
            while (mask != 0) {
                Node next = getInput();
                mask = advanceInput();
                if (next != null) {
                    return next;
                }
            }
            return null;
        }

        @Override
        public Node next() {
            Node next = nextValue;
            if (next == null) {
                next = forward();
                if (next == null) {
                    throw new NoSuchElementException();
                } else {
                    return next;
                }
            } else {
                nextValue = null;
                return next;
            }
        }

        public final long advanceInput() {
            int state = (int) mask & 0x03;
            if (state == 0) {
                // Skip normal field.
                return mask >>> NEXT_EDGE;
            } else if (state == 1) {
                // We are iterating a node list.
                if ((mask & 0xFFFF00) != 0) {
                    // Node list count is non-zero, decrease by 1.
                    return mask - 0x100;
                } else {
                    // Node list is finished => go to next input.
                    return mask >>> 24;
                }
            } else {
                // Need to expand node list.
                NodeList<?> nodeList = Edges.getNodeListUnsafe(node, mask & 0xFC);
                if (nodeList != null) {
                    int size = nodeList.size();
                    if (size != 0) {
                        // Set pointer to upper most index of node list.
                        return ((mask >>> NEXT_EDGE) << 24) | (mask & 0xFD) | ((size - 1) << NEXT_EDGE);
                    }
                }
                // Node list is empty or null => skip.
                return mask >>> NEXT_EDGE;
            }
        }

        public Node getInput() {
            int state = (int) mask & 0x03;
            if (state == 0) {
                return Edges.getNodeUnsafe(node, mask & 0xFC);
            } else if (state == 1) {
                // We are iterating a node list.
                NodeList<?> nodeList = Edges.getNodeListUnsafe(node, mask & 0xFC);
                return nodeList.nodes[nodeList.size() - 1 - (int) ((mask >>> NEXT_EDGE) & 0xFFFF)];
            } else {
                // Node list needs to expand first.
                return null;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public Position nextPosition() {
            return null;
        }
    }

    private static final class RawEdgesWithModCountIterator extends RawEdgesIterator {
        private final int modCount;

        private RawEdgesWithModCountIterator(Node node, long mask) {
            super(node, mask);
            assert isModificationCountsEnabled();
            this.modCount = node.modCount();
        }

        @Override
        public boolean hasNext() {
            try {
                return super.hasNext();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Node next() {
            try {
                return super.next();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Position nextPosition() {
            try {
                return super.nextPosition();
            } finally {
                assert modCount == node.modCount();
            }
        }
    }

    public NodeIterable<Node> getSuccessorIterable(final Node node) {
        long mask = this.successorIteration;
        return new NodeIterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                if (isModificationCountsEnabled()) {
                    return new RawEdgesWithModCountIterator(node, mask);
                } else {
                    return new RawEdgesIterator(node, mask);
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                Iterator<Node> iterator = iterator();
                boolean first = true;
                sb.append("succs=");
                sb.append('[');
                while (iterator.hasNext()) {
                    Node input = iterator.next();
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(input);
                    first = false;
                }
                sb.append(']');
                return sb.toString();
            }
        };
    }

    public NodeIterable<Node> getInputIterable(final Node node) {
        long mask = this.inputsIteration;
        return new NodeIterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                if (isModificationCountsEnabled()) {
                    return new RawEdgesWithModCountIterator(node, mask);
                } else {
                    return new RawEdgesIterator(node, mask);
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                Iterator<Node> iterator = iterator();
                boolean first = true;
                sb.append("inputs=");
                sb.append('[');
                while (iterator.hasNext()) {
                    Node input = iterator.next();
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(input);
                    first = false;
                }
                sb.append(']');
                return sb.toString();
            }
        };
    }

    public boolean equalSuccessors(Node node, Node other) {
        return equalEdges(node, other, successorIteration);
    }

    public boolean equalInputs(Node node, Node other) {
        return equalEdges(node, other, inputsIteration);
    }

    private boolean equalEdges(Node node, Node other, long mask) {
        long myMask = mask;
        assert other.getNodeClass() == this;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Object v1 = Edges.getNodeUnsafe(node, offset);
                Object v2 = Edges.getNodeUnsafe(other, offset);
                if (v1 != v2) {
                    return false;
                }
            } else {
                Object v1 = Edges.getNodeListUnsafe(node, offset);
                Object v2 = Edges.getNodeListUnsafe(other, offset);
                if (!Objects.equals(v1, v2)) {
                    return false;
                }
            }
            myMask >>>= NEXT_EDGE;
        }
        return true;
    }

    public void pushInputs(Node node, NodeStack stack) {
        long myMask = this.inputsIteration;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    stack.push(curNode);
                }
            } else {
                pushAllHelper(stack, node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void pushAllHelper(NodeStack stack, Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    stack.push(curNode);
                }
            }
        }
    }

    public void applySuccessors(Node node, EdgeVisitor consumer) {
        applyEdges(node, consumer, this.successorIteration);
    }

    public void applyInputs(Node node, EdgeVisitor consumer) {
        applyEdges(node, consumer, this.inputsIteration);
    }

    private static void applyEdges(Node node, EdgeVisitor consumer, long mask) {
        long myMask = mask;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    Node newNode = consumer.apply(node, curNode);
                    if (newNode != curNode) {
                        Edges.putNodeUnsafe(node, offset, newNode);
                    }
                }
            } else {
                applyHelper(node, consumer, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void applyHelper(Node node, EdgeVisitor consumer, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    Node newNode = consumer.apply(node, curNode);
                    if (newNode != curNode) {
                        list.initialize(i, newNode);
                    }
                }
            }
        }
    }

    public void unregisterAtSuccessorsAsPredecessor(Node node) {
        long myMask = this.successorIteration;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    node.updatePredecessor(curNode, null);
                    Edges.putNodeUnsafe(node, offset, null);
                }
            } else {
                unregisterAtSuccessorsAsPredecessorHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void unregisterAtSuccessorsAsPredecessorHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    node.updatePredecessor(curNode, null);
                }
            }
            list.clearWithoutUpdate();
        }
    }

    public void registerAtSuccessorsAsPredecessor(Node node) {
        long myMask = this.successorIteration;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    assert curNode.isAlive() : "Successor not alive";
                    node.updatePredecessor(null, curNode);
                }
            } else {
                registerAtSuccessorsAsPredecessorHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void registerAtSuccessorsAsPredecessorHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    assert curNode.isAlive() : "Successor not alive";
                    node.updatePredecessor(null, curNode);
                }
            }
        }
    }

    public boolean replaceFirstInput(Node node, Node key, Node replacement) {
        return replaceFirstEdge(node, key, replacement, this.inputsIteration);
    }

    public boolean replaceFirstSuccessor(Node node, Node key, Node replacement) {
        return replaceFirstEdge(node, key, replacement, this.successorIteration);
    }

    public static boolean replaceFirstEdge(Node node, Node key, Node replacement, long mask) {
        long myMask = mask;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Object curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode == key) {
                    Edges.putNodeUnsafe(node, offset, replacement);
                    return true;
                }
            } else {
                NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
                if (list != null && list.replaceFirst(key, replacement)) {
                    return true;
                }
            }
            myMask >>>= NEXT_EDGE;
        }
        return false;
    }

    public void registerAtInputsAsUsage(Node node) {
        long myMask = this.inputsIteration;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    assert curNode.isAlive() : "Input not alive " + curNode;
                    curNode.addUsage(node);
                }
            } else {
                registerAtInputsAsUsageHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void registerAtInputsAsUsageHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    assert curNode.isAlive() : "Input not alive";
                    curNode.addUsage(node);
                }
            }
        }
    }

    public void unregisterAtInputsAsUsage(Node node) {
        long myMask = this.inputsIteration;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    node.removeThisFromUsages(curNode);
                    if (curNode.hasNoUsages()) {
                        node.maybeNotifyZeroUsages(curNode);
                    }
                    Edges.putNodeUnsafe(node, offset, null);
                }
            } else {
                unregisterAtInputsAsUsageHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void unregisterAtInputsAsUsageHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    node.removeThisFromUsages(curNode);
                    if (curNode.hasNoUsages()) {
                        node.maybeNotifyZeroUsages(curNode);
                    }
                }
            }
            list.clearWithoutUpdate();
        }
    }
}

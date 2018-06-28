/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodes.ConstantNode.forInt;
import static org.graalvm.compiler.nodes.ConstantNode.forIntegerKind;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BridgeMethodUtils;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.calc.Condition.CanonicalizedCondition;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.TypePlugin;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.java.ValueCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.word.Word.Opcode;
import org.graalvm.compiler.word.Word.Operation;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.impl.WordFactoryOperation;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A plugin for calls to {@linkplain Operation word operations}, as well as all other nodes that
 * need special handling for {@link Word} types.
 */
public class WordOperationPlugin implements NodePlugin, TypePlugin, InlineInvokePlugin {
    protected final WordTypes wordTypes;
    protected final JavaKind wordKind;
    protected final SnippetReflectionProvider snippetReflection;

    public WordOperationPlugin(SnippetReflectionProvider snippetReflection, WordTypes wordTypes) {
        this.snippetReflection = snippetReflection;
        this.wordTypes = wordTypes;
        this.wordKind = wordTypes.getWordKind();
    }

    @Override
    public boolean canChangeStackKind(GraphBuilderContext b) {
        return true;
    }

    /**
     * Processes a call to a method if it is annotated as a word operation by adding nodes to the
     * graph being built that implement the denoted operation.
     *
     * @return {@code true} iff {@code method} is annotated with {@link Operation} (and was thus
     *         processed by this method)
     */
    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!wordTypes.isWordOperation(method)) {
            return false;
        }
        processWordOperation(b, args, wordTypes.getWordOperation(method, b.getMethod().getDeclaringClass()));
        return true;
    }

    @Override
    public StampPair interceptType(GraphBuilderTool b, JavaType declaredType, boolean nonNull) {
        Stamp wordStamp = null;
        if (declaredType instanceof ResolvedJavaType) {
            ResolvedJavaType resolved = (ResolvedJavaType) declaredType;
            if (wordTypes.isWord(resolved)) {
                wordStamp = wordTypes.getWordStamp(resolved);
            } else if (resolved.isArray() && wordTypes.isWord(resolved.getElementalType())) {
                TypeReference trusted = TypeReference.createTrustedWithoutAssumptions(resolved);
                wordStamp = StampFactory.object(trusted, nonNull);
            }
        }
        if (wordStamp != null) {
            return StampPair.createSingle(wordStamp);
        } else {
            return null;
        }
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (wordTypes.isWord(invoke.asNode())) {
            invoke.asNode().setStamp(wordTypes.getWordStamp(StampTool.typeOrNull(invoke.asNode())));
        }
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
        StampPair wordStamp = interceptType(b, field.getType(), false);
        if (wordStamp != null) {
            LoadFieldNode loadFieldNode = LoadFieldNode.createOverrideStamp(wordStamp, receiver, field);
            b.addPush(field.getJavaKind(), loadFieldNode);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField staticField) {
        return handleLoadField(b, null, staticField);
    }

    @Override
    public boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        /*
         * There are cases where the array does not have a known type yet, i.e., the type is null.
         * In that case we assume it is not a word type.
         */
        if (arrayType != null && wordTypes.isWord(arrayType.getComponentType())) {
            assert elementKind == JavaKind.Object;
            b.addPush(elementKind, createLoadIndexedNode(array, index, boundsCheck));
            return true;
        }
        return false;
    }

    protected LoadIndexedNode createLoadIndexedNode(ValueNode array, ValueNode index, GuardingNode boundsCheck) {
        return new LoadIndexedNode(null, array, index, boundsCheck, wordKind);
    }

    @Override
    public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        if (field.getJavaKind() == JavaKind.Object) {
            boolean isWordField = wordTypes.isWord(field.getType());
            boolean isWordValue = value.getStackKind() == wordKind;

            if (isWordField && !isWordValue) {
                throw bailout(b, "Cannot store a non-word value into a word field: " + field.format("%H.%n"));
            } else if (!isWordField && isWordValue) {
                throw bailout(b, "Cannot store a word value into a non-word field: " + field.format("%H.%n"));
            }
        }

        /* We never need to intercept the field store. */
        return false;
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        return handleStoreField(b, null, field, value);
    }

    @Override
    public boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, GuardingNode storeCheck, JavaKind elementKind, ValueNode value) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        if (arrayType != null && wordTypes.isWord(arrayType.getComponentType())) {
            assert elementKind == JavaKind.Object;
            if (value.getStackKind() != wordKind) {
                throw bailout(b, "Cannot store a non-word value into a word array: " + arrayType.toJavaName(true));
            }
            GraalError.guarantee(storeCheck == null, "Word array stores are primitive stores and therefore do not require a store check");
            b.add(createStoreIndexedNode(array, index, boundsCheck, value));
            return true;
        }
        if (elementKind == JavaKind.Object && value.getStackKind() == wordKind) {
            throw bailout(b, "Cannot store a word value into a non-word array: " + arrayType.toJavaName(true));
        }
        return false;
    }

    protected StoreIndexedNode createStoreIndexedNode(ValueNode array, ValueNode index, GuardingNode boundsCheck, ValueNode value) {
        return new StoreIndexedNode(array, index, boundsCheck, null, wordKind, value);
    }

    @Override
    public boolean handleCheckCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (!wordTypes.isWord(type)) {
            if (object.getStackKind() != JavaKind.Object) {
                throw bailout(b, "Cannot cast a word value to a non-word type: " + type.toJavaName(true));
            }
            return false;
        }

        if (object.getStackKind() != wordKind) {
            throw bailout(b, "Cannot cast a non-word value to a word type: " + type.toJavaName(true));
        }
        b.push(JavaKind.Object, object);
        return true;
    }

    @Override
    public boolean handleInstanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (wordTypes.isWord(type)) {
            throw bailout(b, "Cannot use instanceof for word a type: " + type.toJavaName(true));
        } else if (object.getStackKind() != JavaKind.Object) {
            throw bailout(b, "Cannot use instanceof on a word value: " + type.toJavaName(true));
        }
        return false;
    }

    protected void processWordOperation(GraphBuilderContext b, ValueNode[] args, ResolvedJavaMethod wordMethod) throws GraalError {
        JavaKind returnKind = wordMethod.getSignature().getReturnKind();
        WordFactoryOperation factoryOperation = BridgeMethodUtils.getAnnotation(WordFactoryOperation.class, wordMethod);
        if (factoryOperation != null) {
            switch (factoryOperation.opcode()) {
                case ZERO:
                    assert args.length == 0;
                    b.addPush(returnKind, forIntegerKind(wordKind, 0L));
                    return;

                case FROM_UNSIGNED:
                    assert args.length == 1;
                    b.push(returnKind, fromUnsigned(b, args[0]));
                    return;

                case FROM_SIGNED:
                    assert args.length == 1;
                    b.push(returnKind, fromSigned(b, args[0]));
                    return;
            }
        }

        Word.Operation operation = BridgeMethodUtils.getAnnotation(Word.Operation.class, wordMethod);
        if (operation == null) {
            throw bailout(b, "Cannot call method on a word value: " + wordMethod.format("%H.%n(%p)"));
        }
        switch (operation.opcode()) {
            case NODE_CLASS:
            case NODE_CLASS_WITH_GUARD:
                assert args.length == 2;
                ValueNode left = args[0];
                ValueNode right = operation.rightOperandIsInt() ? toUnsigned(b, args[1], JavaKind.Int) : fromSigned(b, args[1]);

                b.addPush(returnKind, createBinaryNodeInstance(operation.node(), left, right, operation.opcode() == Opcode.NODE_CLASS_WITH_GUARD));
                break;

            case COMPARISON:
                assert args.length == 2;
                b.push(returnKind, comparisonOp(b, operation.condition(), args[0], fromSigned(b, args[1])));
                break;

            case IS_NULL:
                assert args.length == 1;
                b.push(returnKind, comparisonOp(b, Condition.EQ, args[0], ConstantNode.forIntegerKind(wordKind, 0L)));
                break;

            case IS_NON_NULL:
                assert args.length == 1;
                b.push(returnKind, comparisonOp(b, Condition.NE, args[0], ConstantNode.forIntegerKind(wordKind, 0L)));
                break;

            case NOT:
                assert args.length == 1;
                b.addPush(returnKind, new XorNode(args[0], b.add(forIntegerKind(wordKind, -1))));
                break;

            case READ_POINTER:
            case READ_OBJECT:
            case READ_BARRIERED: {
                assert args.length == 2 || args.length == 3;
                JavaKind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 2) {
                    location = any();
                } else {
                    assert args[2].isConstant();
                    location = snippetReflection.asObject(LocationIdentity.class, args[2].asJavaConstant());
                }
                b.push(returnKind, readOp(b, readKind, address, location, operation.opcode()));
                break;
            }
            case READ_HEAP: {
                assert args.length == 3;
                JavaKind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                BarrierType barrierType = snippetReflection.asObject(BarrierType.class, args[2].asJavaConstant());
                b.push(returnKind, readOp(b, readKind, address, any(), barrierType, true));
                break;
            }
            case WRITE_POINTER:
            case WRITE_OBJECT:
            case WRITE_BARRIERED:
            case INITIALIZE: {
                assert args.length == 3 || args.length == 4;
                JavaKind writeKind = wordTypes.asKind(wordMethod.getSignature().getParameterType(wordMethod.isStatic() ? 2 : 1, wordMethod.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 3) {
                    location = any();
                } else {
                    assert args[3].isConstant();
                    location = snippetReflection.asObject(LocationIdentity.class, args[3].asJavaConstant());
                }
                writeOp(b, writeKind, address, location, args[2], operation.opcode());
                break;
            }

            case TO_RAW_VALUE:
                assert args.length == 1;
                b.push(returnKind, toUnsigned(b, args[0], JavaKind.Long));
                break;

            case OBJECT_TO_TRACKED:
                assert args.length == 1;
                WordCastNode objectToTracked = b.add(WordCastNode.objectToTrackedPointer(args[0], wordKind));
                b.push(returnKind, objectToTracked);
                break;

            case OBJECT_TO_UNTRACKED:
                assert args.length == 1;
                WordCastNode objectToUntracked = b.add(WordCastNode.objectToUntrackedPointer(args[0], wordKind));
                b.push(returnKind, objectToUntracked);
                break;

            case FROM_ADDRESS:
                assert args.length == 1;
                WordCastNode addressToWord = b.add(WordCastNode.addressToWord(args[0], wordKind));
                b.push(returnKind, addressToWord);
                break;

            case TO_OBJECT:
                assert args.length == 1;
                WordCastNode wordToObject = b.add(WordCastNode.wordToObject(args[0], wordKind));
                b.push(returnKind, wordToObject);
                break;

            case TO_OBJECT_NON_NULL:
                assert args.length == 1;
                WordCastNode wordToObjectNonNull = b.add(WordCastNode.wordToObjectNonNull(args[0], wordKind));
                b.push(returnKind, wordToObjectNonNull);
                break;

            case CAS_POINTER:
                assert args.length == 5;
                AddressNode address = makeAddress(b, args[0], args[1]);
                JavaKind valueKind = wordTypes.asKind(wordMethod.getSignature().getParameterType(1, wordMethod.getDeclaringClass()));
                assert valueKind.equals(wordTypes.asKind(wordMethod.getSignature().getParameterType(2, wordMethod.getDeclaringClass()))) : wordMethod.getSignature();
                assert args[4].isConstant() : Arrays.toString(args);
                LocationIdentity location = snippetReflection.asObject(LocationIdentity.class, args[4].asJavaConstant());
                JavaType returnType = wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass());
                b.addPush(returnKind, casOp(valueKind, wordTypes.asKind(returnType), address, location, args[2], args[3]));
                break;
            default:
                throw new GraalError("Unknown opcode: %s", operation.opcode());
        }
    }

    /**
     * Create an instance of a binary node which is used to lower {@link Word} operations. This
     * method is called for all {@link Word} operations which are annotated with @Operation(node =
     * ...) and encapsulates the reflective allocation of the node.
     */
    private static ValueNode createBinaryNodeInstance(Class<? extends ValueNode> nodeClass, ValueNode left, ValueNode right, boolean withGuardingNode) {
        try {
            Class<?>[] parameterTypes = withGuardingNode ? new Class<?>[]{ValueNode.class, ValueNode.class, GuardingNode.class} : new Class<?>[]{ValueNode.class, ValueNode.class};
            Constructor<?> cons = nodeClass.getDeclaredConstructor(parameterTypes);
            Object[] initargs = withGuardingNode ? new Object[]{left, right, null} : new Object[]{left, right};
            return (ValueNode) cons.newInstance(initargs);
        } catch (Throwable ex) {
            throw new GraalError(ex).addContext(nodeClass.getName());
        }
    }

    private ValueNode comparisonOp(GraphBuilderContext graph, Condition condition, ValueNode left, ValueNode right) {
        assert left.getStackKind() == wordKind && right.getStackKind() == wordKind;

        CanonicalizedCondition canonical = condition.canonicalize();

        ValueNode a = canonical.mustMirror() ? right : left;
        ValueNode b = canonical.mustMirror() ? left : right;

        CompareNode comparison;
        if (canonical.getCanonicalCondition() == CanonicalCondition.EQ) {
            comparison = new IntegerEqualsNode(a, b);
        } else if (canonical.getCanonicalCondition() == CanonicalCondition.BT) {
            comparison = new IntegerBelowNode(a, b);
        } else {
            assert canonical.getCanonicalCondition() == CanonicalCondition.LT;
            comparison = new IntegerLessThanNode(a, b);
        }

        ConstantNode trueValue = graph.add(forInt(1));
        ConstantNode falseValue = graph.add(forInt(0));

        if (canonical.mustNegate()) {
            ConstantNode temp = trueValue;
            trueValue = falseValue;
            falseValue = temp;
        }
        return graph.add(new ConditionalNode(graph.add(comparison), trueValue, falseValue));
    }

    protected ValueNode readOp(GraphBuilderContext b, JavaKind readKind, AddressNode address, LocationIdentity location, Opcode op) {
        assert op == Opcode.READ_POINTER || op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED;
        final BarrierType barrier = (op == Opcode.READ_BARRIERED ? BarrierType.PRECISE : BarrierType.NONE);
        final boolean compressible = (op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED);

        return readOp(b, readKind, address, location, barrier, compressible);
    }

    public static ValueNode readOp(GraphBuilderContext b, JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, boolean compressible) {
        /*
         * A JavaReadNode lowered to a ReadNode that will not float. This means it cannot float
         * above an explicit zero check on its base address or any other test that ensures the read
         * is safe.
         */
        JavaReadNode read = b.add(new JavaReadNode(readKind, address, location, barrierType, compressible));
        return read;
    }

    protected void writeOp(GraphBuilderContext b, JavaKind writeKind, AddressNode address, LocationIdentity location, ValueNode value, Opcode op) {
        assert op == Opcode.WRITE_POINTER || op == Opcode.WRITE_OBJECT || op == Opcode.WRITE_BARRIERED || op == Opcode.INITIALIZE;
        final BarrierType barrier = (op == Opcode.WRITE_BARRIERED ? BarrierType.PRECISE : BarrierType.NONE);
        final boolean compressible = (op == Opcode.WRITE_OBJECT || op == Opcode.WRITE_BARRIERED);
        assert op != Opcode.INITIALIZE || location.isInit() : "must use init location for initializing";
        b.add(new JavaWriteNode(writeKind, address, location, value, barrier, compressible));
    }

    protected AbstractCompareAndSwapNode casOp(JavaKind writeKind, JavaKind returnKind, AddressNode address, LocationIdentity location, ValueNode expectedValue, ValueNode newValue) {
        boolean isLogic = returnKind == JavaKind.Boolean;
        assert isLogic || writeKind == returnKind : writeKind + " != " + returnKind;
        AbstractCompareAndSwapNode cas;
        if (isLogic) {
            cas = new LogicCompareAndSwapNode(address, expectedValue, newValue, location);
        } else {
            cas = new ValueCompareAndSwapNode(address, expectedValue, newValue, location);
        }
        return cas;
    }

    public AddressNode makeAddress(GraphBuilderContext b, ValueNode base, ValueNode offset) {
        return b.add(new OffsetAddressNode(base, fromSigned(b, offset)));
    }

    public ValueNode fromUnsigned(GraphBuilderContext b, ValueNode value) {
        return convert(b, value, wordKind, true);
    }

    public ValueNode fromSigned(GraphBuilderContext b, ValueNode value) {
        return convert(b, value, wordKind, false);
    }

    public ValueNode toUnsigned(GraphBuilderContext b, ValueNode value, JavaKind toKind) {
        return convert(b, value, toKind, true);
    }

    public ValueNode convert(GraphBuilderContext b, ValueNode value, JavaKind toKind, boolean unsigned) {
        if (value.getStackKind() == toKind) {
            return value;
        }

        if (toKind == JavaKind.Int) {
            assert value.getStackKind() == JavaKind.Long;
            return b.add(new NarrowNode(value, 32));
        } else {
            assert toKind == JavaKind.Long;
            assert value.getStackKind() == JavaKind.Int;
            if (unsigned) {
                return b.add(new ZeroExtendNode(value, 64));
            } else {
                return b.add(new SignExtendNode(value, 64));
            }
        }
    }

    private static BailoutException bailout(GraphBuilderContext b, String msg) {
        throw b.bailout(msg + "\nat " + b.getCode().asStackTraceElement(b.bci()));
    }
}

/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.foreign.abi;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.Binding.Allocate;
import jdk.internal.foreign.abi.Binding.BoxAddress;
import jdk.internal.foreign.abi.Binding.BufferLoad;
import jdk.internal.foreign.abi.Binding.BufferStore;
import jdk.internal.foreign.abi.Binding.Cast;
import jdk.internal.foreign.abi.Binding.Copy;
import jdk.internal.foreign.abi.Binding.Dup;
import jdk.internal.foreign.abi.Binding.SegmentBase;
import jdk.internal.foreign.abi.Binding.SegmentOffset;
import jdk.internal.foreign.abi.Binding.ShiftLeft;
import jdk.internal.foreign.abi.Binding.ShiftRight;
import jdk.internal.foreign.abi.Binding.VMLoad;
import jdk.internal.foreign.abi.Binding.VMStore;
import sun.security.action.GetBooleanAction;
import sun.security.action.GetPropertyAction;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.ClassFileFormatVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.classfile.ClassFile.*;
import static java.lang.classfile.TypeKind.*;
import static jdk.internal.constant.ConstantUtils.*;

public class BindingSpecializer {
    private static final String DUMP_CLASSES_DIR
        = GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.abi.Specializer.DUMP_CLASSES_DIR");
    private static final boolean PERFORM_VERIFICATION
        = GetBooleanAction.privilegedGetProperty("jdk.internal.foreign.abi.Specializer.PERFORM_VERIFICATION");

    // Bunch of helper constants
    private static final int CLASSFILE_VERSION = ClassFileFormatVersion.latest().major();

    private static final ClassDesc CD_Arena = referenceClassDesc(Arena.class);
    private static final ClassDesc CD_MemorySegment = referenceClassDesc(MemorySegment.class);
    private static final ClassDesc CD_MemorySegment_Scope = referenceClassDesc(MemorySegment.Scope.class);
    private static final ClassDesc CD_SharedUtils = referenceClassDesc(SharedUtils.class);
    private static final ClassDesc CD_AbstractMemorySegmentImpl = referenceClassDesc(AbstractMemorySegmentImpl.class);
    private static final ClassDesc CD_MemorySessionImpl = referenceClassDesc(MemorySessionImpl.class);
    private static final ClassDesc CD_Utils = referenceClassDesc(Utils.class);
    private static final ClassDesc CD_SegmentAllocator = referenceClassDesc(SegmentAllocator.class);
    private static final ClassDesc CD_ValueLayout = referenceClassDesc(ValueLayout.class);
    private static final ClassDesc CD_ValueLayout_OfBoolean = referenceClassDesc(ValueLayout.OfBoolean.class);
    private static final ClassDesc CD_ValueLayout_OfByte = referenceClassDesc(ValueLayout.OfByte.class);
    private static final ClassDesc CD_ValueLayout_OfShort = referenceClassDesc(ValueLayout.OfShort.class);
    private static final ClassDesc CD_ValueLayout_OfChar = referenceClassDesc(ValueLayout.OfChar.class);
    private static final ClassDesc CD_ValueLayout_OfInt = referenceClassDesc(ValueLayout.OfInt.class);
    private static final ClassDesc CD_ValueLayout_OfLong = referenceClassDesc(ValueLayout.OfLong.class);
    private static final ClassDesc CD_ValueLayout_OfFloat = referenceClassDesc(ValueLayout.OfFloat.class);
    private static final ClassDesc CD_ValueLayout_OfDouble = referenceClassDesc(ValueLayout.OfDouble.class);
    private static final ClassDesc CD_AddressLayout = referenceClassDesc(AddressLayout.class);

    private static final MethodTypeDesc MTD_NEW_BOUNDED_ARENA = MethodTypeDesc.of(CD_Arena, CD_long);
    private static final MethodTypeDesc MTD_NEW_EMPTY_ARENA = MethodTypeDesc.of(CD_Arena);
    private static final MethodTypeDesc MTD_SCOPE = MethodTypeDesc.of(CD_MemorySegment_Scope);
    private static final MethodTypeDesc MTD_SESSION_IMPL = MethodTypeDesc.of(CD_MemorySessionImpl);
    private static final MethodTypeDesc MTD_CLOSE = MTD_void;
    private static final MethodTypeDesc MTD_CHECK_NATIVE = MethodTypeDesc.of(CD_void, CD_MemorySegment);
    private static final MethodTypeDesc MTD_UNSAFE_GET_BASE = MethodTypeDesc.of(CD_Object);
    private static final MethodTypeDesc MTD_UNSAFE_GET_OFFSET = MethodTypeDesc.of(CD_long);
    private static final MethodTypeDesc MTD_COPY = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_long, CD_MemorySegment, CD_long, CD_long);
    private static final MethodTypeDesc MTD_LONG_TO_ADDRESS_NO_SCOPE = MethodTypeDesc.of(CD_MemorySegment, CD_long, CD_long, CD_long);
    private static final MethodTypeDesc MTD_LONG_TO_ADDRESS_SCOPE = MethodTypeDesc.of(CD_MemorySegment, CD_long, CD_long, CD_long, CD_MemorySessionImpl);
    private static final MethodTypeDesc MTD_ALLOCATE = MethodTypeDesc.of(CD_MemorySegment, CD_long, CD_long);
    private static final MethodTypeDesc MTD_HANDLE_UNCAUGHT_EXCEPTION = MethodTypeDesc.of(CD_void, CD_Throwable);
    private static final MethodTypeDesc MTD_RELEASE0 = MTD_void;
    private static final MethodTypeDesc MTD_ACQUIRE0 = MTD_void;
    private static final MethodTypeDesc MTD_INTEGER_TO_UNSIGNED_LONG = MethodTypeDesc.of(CD_long, CD_int);
    private static final MethodTypeDesc MTD_SHORT_TO_UNSIGNED_LONG = MethodTypeDesc.of(CD_long, CD_short);
    private static final MethodTypeDesc MTD_BYTE_TO_UNSIGNED_LONG = MethodTypeDesc.of(CD_long, CD_byte);
    private static final MethodTypeDesc MTD_BYTE_TO_BOOLEAN = MethodTypeDesc.of(CD_boolean, CD_byte);

    private static final ConstantDesc CLASS_DATA_DESC = DynamicConstantDesc.of(BSM_CLASS_DATA);

    private static final String CLASS_NAME_DOWNCALL = "jdk/internal/foreign/abi/DowncallStub";
    private static final String CLASS_NAME_UPCALL = "jdk/internal/foreign/abi/UpcallStub";
    private static final String METHOD_NAME = "invoke";

    // Instance fields start here
    private final CodeBuilder cb;
    private final MethodType callerMethodType;
    private final CallingSequence callingSequence;
    private final ABIDescriptor abi;
    private final MethodType leafType;

    private int[] leafArgSlots;
    private int[] scopeSlots;
    private int curScopeLocalIdx = -1;
    private int returnAllocatorIdx = -1;
    private int contextIdx = -1;
    private int returnBufferIdx = -1;
    private int retValIdx = -1;
    private Deque<Class<?>> typeStack;
    private List<Class<?>> leafArgTypes;
    private int paramIndex;
    private long retBufOffset; // for needsReturnBuffer

    private BindingSpecializer(CodeBuilder cb, MethodType callerMethodType,
                               CallingSequence callingSequence, ABIDescriptor abi, MethodType leafType) {
        this.cb = cb;
        this.callerMethodType = callerMethodType;
        this.callingSequence = callingSequence;
        this.abi = abi;
        this.leafType = leafType;
    }

    static MethodHandle specializeDowncall(MethodHandle leafHandle, CallingSequence callingSequence, ABIDescriptor abi) {
        MethodType callerMethodType = callingSequence.callerMethodType();
        if (callingSequence.needsReturnBuffer()) {
            callerMethodType = callerMethodType.dropParameterTypes(0, 1); // Return buffer does not appear in the parameter list
        }
        callerMethodType = callerMethodType.insertParameterTypes(0, SegmentAllocator.class);

        byte[] bytes = specializeHelper(leafHandle.type(), callerMethodType, callingSequence, abi);

        try {
            MethodHandles.Lookup definedClassLookup = MethodHandles.lookup()
                    .defineHiddenClassWithClassData(bytes, leafHandle, false);
            return definedClassLookup.findStatic(definedClassLookup.lookupClass(), METHOD_NAME, callerMethodType);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InternalError("Should not happen", e);
        }
    }

    static MethodHandle specializeUpcall(MethodType targetType, CallingSequence callingSequence, ABIDescriptor abi) {
        MethodType callerMethodType = callingSequence.callerMethodType();
        callerMethodType = callerMethodType.insertParameterTypes(0, MethodHandle.class); // target

        byte[] bytes = specializeHelper(targetType, callerMethodType, callingSequence, abi);

        try {
            // For upcalls, we must initialize the class since the upcall stubs don't have a clinit barrier,
            // and the slow path in the c2i adapter we end up calling can not handle the particular code shape
            // where the caller is an upcall stub.
            MethodHandles.Lookup defineClassLookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
            return defineClassLookup.findStatic(defineClassLookup.lookupClass(), METHOD_NAME, callerMethodType);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InternalError("Should not happen", e);
        }
    }

    private static byte[] specializeHelper(MethodType leafType, MethodType callerMethodType,
                                           CallingSequence callingSequence, ABIDescriptor abi) {
        String className = callingSequence.forDowncall() ? CLASS_NAME_DOWNCALL : CLASS_NAME_UPCALL;
        byte[] bytes = ClassFile.of().build(ClassDesc.ofInternalName(className), clb -> {
            clb.withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER);
            clb.withSuperclass(CD_Object);
            clb.withVersion(CLASSFILE_VERSION, 0);

            clb.withMethodBody(METHOD_NAME, methodTypeDesc(callerMethodType), ACC_PUBLIC | ACC_STATIC,
                    cb -> new BindingSpecializer(cb, callerMethodType, callingSequence, abi, leafType).specialize());
        });

        if (DUMP_CLASSES_DIR != null) {
            String fileName = className + escapeForFileName(callingSequence.functionDesc().toString()) + ".class";
            Path dumpPath = Path.of(DUMP_CLASSES_DIR).resolve(fileName);
            try {
                Files.createDirectories(dumpPath.getParent());
                Files.write(dumpPath, bytes);
            } catch (IOException e) {
                throw new InternalError(e);
            }
        }

        if (PERFORM_VERIFICATION) {
            List<VerifyError> errors = ClassFile.of().verify(bytes);
            if (!errors.isEmpty()) {
                errors.forEach(System.err::println);
                throw new IllegalStateException("Verification error(s)");
            }
        }

        return bytes;
    }

    private static String escapeForFileName(String str) {
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            sb.append(switch (c) {
                case ' ' -> '_';
                case '[', '<' -> '{';
                case ']', '>' -> '}';
                case '/', '\\', ':', '*', '?', '"', '|' -> '!'; // illegal in Windows file names.
                default -> c;
            });
        }
        return sb.toString();
    }

    // binding operand stack manipulation

    private void pushType(Class<?> type) {
        typeStack.push(type);
    }

    private Class<?> popType(Class<?> expected) {
        Class<?> found = typeStack.pop();
        if (!expected.equals(found)) {
            throw new IllegalStateException(
                    String.format("Invalid type on binding operand stack; found %s - expected %s",
                            found.descriptorString(), expected.descriptorString()));
        }
        return found;
    }

    // specialization

    private void specialize() {
        // slots that store the output arguments (passed to the leaf handle)
        leafArgSlots = new int[leafType.parameterCount()];
        for (int i = 0; i < leafType.parameterCount(); i++) {
            leafArgSlots[i] = cb.allocateLocal(TypeKind.from(leafType.parameterType(i)));
        }

        // allocator passed to us for allocating the return MS (downcalls only)
        if (callingSequence.forDowncall()) {
            returnAllocatorIdx = 0; // first param

            // for downcalls we also acquire/release scoped parameters before/after the call
            // create a bunch of locals here to keep track of their scopes (to release later)
            int[] initialScopeSlots = new int[callerMethodType.parameterCount()];
            int numScopes = 0;
            for (int i = 0; i < callerMethodType.parameterCount(); i++) {
                if (shouldAcquire(i)) {
                    int scopeLocal = cb.allocateLocal(ReferenceType);
                    initialScopeSlots[numScopes++] = scopeLocal;
                    cb.loadConstant(null);
                    cb.storeLocal(ReferenceType, scopeLocal); // need to initialize all scope locals here in case an exception occurs
                }
            }
            scopeSlots = Arrays.copyOf(initialScopeSlots, numScopes); // fit to size
            curScopeLocalIdx = 0; // used from emitGetInput
        }

        // create a Binding.Context for this call
        if (callingSequence.allocationSize() != 0) {
            cb.loadConstant(callingSequence.allocationSize());
            cb.invokestatic(CD_SharedUtils, "newBoundedArena", MTD_NEW_BOUNDED_ARENA);
        } else if (callingSequence.forUpcall() && needsSession()) {
            cb.invokestatic(CD_SharedUtils, "newEmptyArena", MTD_NEW_EMPTY_ARENA);
        } else {
            cb.getstatic(CD_SharedUtils, "DUMMY_ARENA", CD_Arena);
        }
        contextIdx = cb.allocateLocal(ReferenceType);
        cb.storeLocal(ReferenceType, contextIdx);

        // in case the call needs a return buffer, allocate it here.
        // for upcalls the VM wrapper stub allocates the buffer.
        if (callingSequence.needsReturnBuffer() && callingSequence.forDowncall()) {
            emitLoadInternalAllocator();
            emitAllocateCall(callingSequence.returnBufferSize(), 1);
            returnBufferIdx = cb.allocateLocal(ReferenceType);
            cb.storeLocal(ReferenceType, returnBufferIdx);
        }

        Label tryStart = cb.newLabel();
        Label tryEnd = cb.newLabel();
        Label catchStart = cb.newLabel();

        cb.labelBinding(tryStart);

        // stack to keep track of types on the bytecode stack between bindings.
        // this is needed to e.g. emit the right DUP instruction,
        // but also used for type checking.
        typeStack = new ArrayDeque<>();
        // leaf arg types are the types of the args passed to the leaf handle.
        // these are collected from VM_STORE instructions for downcalls, and
        // recipe outputs for upcalls (see uses emitSetOutput for both)
        leafArgTypes = new ArrayList<>();
        paramIndex = 1; // +1 to skip SegmentAllocator or MethodHandle
        for (int i = 0; i < callingSequence.argumentBindingsCount(); i++) {
            if (callingSequence.forDowncall()) {
                // for downcalls, recipes have an input value, which we set up here
                if (callingSequence.needsReturnBuffer() && i == 0) {
                    assert returnBufferIdx != -1;
                    cb.loadLocal(ReferenceType, returnBufferIdx);
                    pushType(MemorySegment.class);
                } else {
                    emitGetInput();
                }
            }

            // emit code according to binding recipe
            doBindings(callingSequence.argumentBindings(i));

            if (callingSequence.forUpcall()) {
                // for upcalls, recipes have a result, which we handle here
                if (callingSequence.needsReturnBuffer() && i == 0) {
                    // return buffer ptr is wrapped in a MemorySegment above, but not passed to the leaf handle
                    popType(MemorySegment.class);
                    returnBufferIdx = cb.allocateLocal(ReferenceType);
                    cb.storeLocal(ReferenceType, returnBufferIdx);
                } else {
                    // for upcalls the recipe result is an argument to the leaf handle
                    emitSetOutput(typeStack.pop());
                }
            }
            assert typeStack.isEmpty();
        }

        assert leafArgTypes.equals(leafType.parameterList());

        // load the leaf MethodHandle
        if (callingSequence.forDowncall()) {
            cb.loadConstant(CLASS_DATA_DESC);
        } else {
            cb.loadLocal(ReferenceType, 0); // load target arg
        }
        cb.checkcast(CD_MethodHandle);
        // load all the leaf args
        for (int i = 0; i < leafArgSlots.length; i++) {
            cb.loadLocal(TypeKind.from(leafArgTypes.get(i)), leafArgSlots[i]);
        }
        // call leaf MH
        cb.invokevirtual(CD_MethodHandle, "invokeExact", methodTypeDesc(leafType));

        // for downcalls, store the result of the leaf handle call away, until
        // it is requested by a VM_LOAD in the return recipe.
        if (callingSequence.forDowncall() && leafType.returnType() != void.class) {
            emitSaveReturnValue(leafType.returnType());
        }
        // for upcalls we leave the return value on the stack to be picked up
        // as an input of the return recipe.

        // return value processing
        if (callingSequence.hasReturnBindings()) {
            if (callingSequence.forUpcall()) {
                pushType(leafType.returnType());
            }

            retBufOffset = 0; // offset for reading from return buffer
            doBindings(callingSequence.returnBindings());

            if (callingSequence.forUpcall() && !callingSequence.needsReturnBuffer()) {
                // was VM_STOREd somewhere in the bindings
                emitRestoreReturnValue(callerMethodType.returnType());
            }
            cb.labelBinding(tryEnd);
            // finally
            emitCleanup();

            if (callerMethodType.returnType() == void.class) {
                // The case for upcalls that return by return buffer
                assert typeStack.isEmpty();
                cb.return_();
            } else {
                popType(callerMethodType.returnType());
                assert typeStack.isEmpty();
                cb.return_(TypeKind.from(callerMethodType.returnType()));
            }
        } else {
            assert callerMethodType.returnType() == void.class;
            assert typeStack.isEmpty();
            cb.labelBinding(tryEnd);
            // finally
            emitCleanup();
            cb.return_();
        }

        cb.labelBinding(catchStart);
        // finally
        emitCleanup();
        if (callingSequence.forDowncall()) {
            cb.athrow();
        } else {
            cb.invokestatic(CD_SharedUtils, "handleUncaughtException", MTD_HANDLE_UNCAUGHT_EXCEPTION);
            if (callerMethodType.returnType() != void.class) {
                TypeKind returnTypeKind = TypeKind.from(callerMethodType.returnType());
                emitConstZero(returnTypeKind);
                cb.return_(returnTypeKind);
            } else {
                cb.return_();
            }
        }

        cb.exceptionCatchAll(tryStart, tryEnd, catchStart);
    }

    private boolean needsSession() {
        return callingSequence.argumentBindings()
                .filter(BoxAddress.class::isInstance)
                .map(BoxAddress.class::cast)
                .anyMatch(BoxAddress::needsScope);
    }

    private boolean shouldAcquire(int paramIndex) {
        if (!callingSequence.forDowncall() || // we only acquire in downcalls
                paramIndex == 0) { // the first parameter in a downcall is SegmentAllocator
            return false;
        }

        // if call needs return buffer, the descriptor has an extra leading layout
        int offset = callingSequence.needsReturnBuffer() ? 0 : 1;
        MemoryLayout paramLayout =  callingSequence.functionDesc()
                                              .argumentLayouts()
                                              .get(paramIndex - offset);

        // is this an address layout?
        return paramLayout instanceof AddressLayout;
    }

    private void emitCleanup() {
        emitCloseContext();
        if (callingSequence.forDowncall()) {
            emitReleaseScopes();
        }
    }

    private void doBindings(List<Binding> bindings) {
        for (Binding binding : bindings) {
            switch (binding) {
                case VMStore vmStore             -> emitVMStore(vmStore);
                case VMLoad vmLoad               -> emitVMLoad(vmLoad);
                case BufferStore bufferStore     -> emitBufferStore(bufferStore);
                case BufferLoad bufferLoad       -> emitBufferLoad(bufferLoad);
                case Copy copy                   -> emitCopyBuffer(copy);
                case Allocate allocate           -> emitAllocBuffer(allocate);
                case BoxAddress boxAddress       -> emitBoxAddress(boxAddress);
                case SegmentBase _               -> emitSegmentBase();
                case SegmentOffset segmentOffset -> emitSegmentOffset(segmentOffset);
                case Dup _                       -> emitDupBinding();
                case ShiftLeft shiftLeft         -> emitShiftLeft(shiftLeft);
                case ShiftRight shiftRight       -> emitShiftRight(shiftRight);
                case Cast cast                   -> emitCast(cast);
            }
        }
    }

    private void emitSetOutput(Class<?> storeType) {
        cb.storeLocal(TypeKind.from(storeType), leafArgSlots[leafArgTypes.size()]);
        leafArgTypes.add(storeType);
    }

    private void emitGetInput() {
        Class<?> highLevelType = callerMethodType.parameterType(paramIndex);
        cb.loadLocal(TypeKind.from(highLevelType), cb.parameterSlot(paramIndex));

        if (shouldAcquire(paramIndex)) {
            cb.dup();
            emitAcquireScope();
        }

        pushType(highLevelType);
        paramIndex++;
    }

    private void emitAcquireScope() {
        cb.checkcast(CD_AbstractMemorySegmentImpl);
        cb.invokevirtual(CD_AbstractMemorySegmentImpl, "sessionImpl", MTD_SESSION_IMPL);
        Label skipAcquire = cb.newLabel();
        Label end = cb.newLabel();

        // start with 1 scope to maybe acquire on the stack
        assert curScopeLocalIdx != -1;
        boolean hasOtherScopes = curScopeLocalIdx != 0;
        for (int i = 0; i < curScopeLocalIdx; i++) {
            cb.dup(); // dup for comparison
            cb.loadLocal(ReferenceType, scopeSlots[i]);
            cb.if_acmpeq(skipAcquire);
        }

        // 1 scope to acquire on the stack
        cb.dup();
        int nextScopeLocal = scopeSlots[curScopeLocalIdx++];
        // call acquire first here. So that if it fails, we don't call release
        cb.invokevirtual(CD_MemorySessionImpl, "acquire0", MTD_ACQUIRE0); // call acquire on the other
        cb.storeLocal(ReferenceType, nextScopeLocal); // store off one to release later

        if (hasOtherScopes) { // avoid ASM generating a bunch of nops for the dead code
            cb.goto_(end);

            cb.labelBinding(skipAcquire);
            cb.pop(); // drop scope
        }

        cb.labelBinding(end);
    }

    private void emitReleaseScopes() {
        for (int scopeLocal : scopeSlots) {
            cb.loadLocal(ReferenceType, scopeLocal);
            cb.ifThen(Opcode.IFNONNULL, ifCb -> {
                ifCb.loadLocal(ReferenceType, scopeLocal);
                ifCb.invokevirtual(CD_MemorySessionImpl, "release0", MTD_RELEASE0);
            });
        }
    }

    private void emitSaveReturnValue(Class<?> storeType) {
        TypeKind typeKind = TypeKind.from(storeType);
        retValIdx = cb.allocateLocal(typeKind);
        cb.storeLocal(typeKind, retValIdx);
    }

    private void emitRestoreReturnValue(Class<?> loadType) {
        assert retValIdx != -1;
        cb.loadLocal(TypeKind.from(loadType), retValIdx);
        pushType(loadType);
    }

    private void emitLoadInternalSession() {
        assert contextIdx != -1;
        cb.loadLocal(ReferenceType, contextIdx);
        cb.checkcast(CD_Arena);
        cb.invokeinterface(CD_Arena, "scope", MTD_SCOPE);
        cb.checkcast(CD_MemorySessionImpl);
    }

    private void emitLoadInternalAllocator() {
        assert contextIdx != -1;
        cb.loadLocal(ReferenceType, contextIdx);
    }

    private void emitCloseContext() {
        assert contextIdx != -1;
        cb.loadLocal(ReferenceType, contextIdx);
        cb.checkcast(CD_Arena);
        cb.invokeinterface(CD_Arena, "close", MTD_CLOSE);
    }

    private void emitBoxAddress(BoxAddress boxAddress) {
        popType(long.class);
        cb.loadConstant(boxAddress.size());
        cb.loadConstant(boxAddress.align());
        if (needsSession()) {
            emitLoadInternalSession();
            cb.invokestatic(CD_Utils, "longToAddress", MTD_LONG_TO_ADDRESS_SCOPE);
        } else {
            cb.invokestatic(CD_Utils, "longToAddress", MTD_LONG_TO_ADDRESS_NO_SCOPE);
        }
        pushType(MemorySegment.class);
    }

    private void emitAllocBuffer(Allocate binding) {
        if (callingSequence.forDowncall()) {
            assert returnAllocatorIdx != -1;
            cb.loadLocal(ReferenceType, returnAllocatorIdx);
        } else {
            emitLoadInternalAllocator();
        }
        emitAllocateCall(binding.size(), binding.alignment());
        pushType(MemorySegment.class);
    }

    private void emitBufferStore(BufferStore bufferStore) {
        Class<?> storeType = bufferStore.type();
        TypeKind storeTypeKind = TypeKind.from(storeType);
        long offset = bufferStore.offset();
        int byteWidth = bufferStore.byteWidth();

        popType(storeType);
        popType(MemorySegment.class);

        if (SharedUtils.isPowerOfTwo(byteWidth)) {
            int valueIdx = cb.allocateLocal(storeTypeKind);
            cb.storeLocal(storeTypeKind, valueIdx);

            ClassDesc valueLayoutType = emitLoadLayoutConstant(storeType);
            cb.loadConstant(offset);
            cb.loadLocal(storeTypeKind, valueIdx);
            MethodTypeDesc descriptor = MethodTypeDesc.of(CD_void, valueLayoutType, CD_long, classDesc(storeType));
            cb.invokeinterface(CD_MemorySegment, "set", descriptor);
        } else {
            // long longValue = ((Number) value).longValue();
            if (storeType == int.class) {
                cb.i2l();
            } else {
                assert storeType == long.class; // chunking only for int and long
            }
            int longValueIdx = cb.allocateLocal(LongType);
            cb.storeLocal(LongType, longValueIdx);
            int writeAddrIdx = cb.allocateLocal(ReferenceType);
            cb.storeLocal(ReferenceType, writeAddrIdx);

            int remaining = byteWidth;
            int chunkOffset = 0;
            do {
                int chunkSize = Integer.highestOneBit(remaining); // next power of 2, in bytes
                Class<?> chunkStoreType;
                long mask;
                switch (chunkSize) {
                    case Integer.BYTES -> {
                        chunkStoreType = int.class;
                        mask = 0xFFFF_FFFFL;
                    }
                    case Short.BYTES -> {
                        chunkStoreType = short.class;
                        mask = 0xFFFFL;
                    }
                    case Byte.BYTES -> {
                        chunkStoreType = byte.class;
                        mask = 0xFFL;
                    }
                    default ->
                       throw new IllegalStateException("Unexpected chunk size for chunked write: " + chunkSize);
                }
                //int writeChunk = (int) (((0xFFFF_FFFFL << shiftAmount) & longValue) >>> shiftAmount);
                int shiftAmount = chunkOffset * Byte.SIZE;
                mask = mask << shiftAmount;
                cb.loadLocal(LongType, longValueIdx);
                cb.loadConstant(mask);
                cb.land();
                if (shiftAmount != 0) {
                    cb.loadConstant(shiftAmount);
                    cb.lushr();
                }
                cb.l2i();
                TypeKind chunkStoreTypeKind = TypeKind.from(chunkStoreType);
                int chunkIdx = cb.allocateLocal(chunkStoreTypeKind);
                cb.storeLocal(chunkStoreTypeKind, chunkIdx);
                // chunk done, now write it

                //writeAddress.set(JAVA_SHORT_UNALIGNED, offset, writeChunk);
                cb.loadLocal(ReferenceType, writeAddrIdx);
                ClassDesc valueLayoutType = emitLoadLayoutConstant(chunkStoreType);
                long writeOffset = offset + SharedUtils.pickChunkOffset(chunkOffset, byteWidth, chunkSize);
                cb.loadConstant(writeOffset);
                cb.loadLocal(chunkStoreTypeKind, chunkIdx);
                MethodTypeDesc descriptor = MethodTypeDesc.of(CD_void, valueLayoutType, CD_long, classDesc(chunkStoreType));
                cb.invokeinterface(CD_MemorySegment, "set", descriptor);

                remaining -= chunkSize;
                chunkOffset += chunkSize;
            } while (remaining != 0);
        }
    }

    // VM_STORE and VM_LOAD are emulated, which is different for down/upcalls
    private void emitVMStore(VMStore vmStore) {
        Class<?> storeType = vmStore.type();
        TypeKind storeTypeKind = TypeKind.from(storeType);
        popType(storeType);

        if (callingSequence.forDowncall()) {
            // processing arg
            emitSetOutput(storeType);
        } else {
            // processing return
            if (!callingSequence.needsReturnBuffer()) {
                emitSaveReturnValue(storeType);
            } else {
                int valueIdx = cb.allocateLocal(storeTypeKind);
                cb.storeLocal(storeTypeKind, valueIdx); // store away the stored value, need it later

                assert returnBufferIdx != -1;
                cb.loadLocal(ReferenceType, returnBufferIdx);
                ClassDesc valueLayoutType = emitLoadLayoutConstant(storeType);
                cb.loadConstant(retBufOffset);
                cb.loadLocal(storeTypeKind, valueIdx);
                MethodTypeDesc descriptor = MethodTypeDesc.of(CD_void, valueLayoutType, CD_long, classDesc(storeType));
                cb.invokeinterface(CD_MemorySegment, "set", descriptor);
                retBufOffset += abi.arch.typeSize(vmStore.storage().type());
            }
        }
    }

    private void emitVMLoad(VMLoad vmLoad) {
        Class<?> loadType = vmLoad.type();

        if (callingSequence.forDowncall()) {
            // processing return
            if (!callingSequence.needsReturnBuffer()) {
                emitRestoreReturnValue(loadType);
            } else {
                assert returnBufferIdx != -1;
                cb.loadLocal(ReferenceType, returnBufferIdx);
                ClassDesc valueLayoutType = emitLoadLayoutConstant(loadType);
                cb.loadConstant(retBufOffset);
                MethodTypeDesc descriptor = MethodTypeDesc.of(classDesc(loadType), valueLayoutType, CD_long);
                cb.invokeinterface(CD_MemorySegment, "get", descriptor);
                retBufOffset += abi.arch.typeSize(vmLoad.storage().type());
                pushType(loadType);
            }
        } else {
            // processing arg
            emitGetInput();
        }
    }

    private void emitDupBinding() {
        Class<?> dupType = typeStack.peek();
        emitDup(dupType);
        pushType(dupType);
    }

    private void emitShiftLeft(ShiftLeft shiftLeft) {
        popType(long.class);
        cb.loadConstant(shiftLeft.shiftAmount() * Byte.SIZE);
        cb.lshl();
        pushType(long.class);
    }

    private void emitShiftRight(ShiftRight shiftRight) {
        popType(long.class);
        cb.loadConstant(shiftRight.shiftAmount() * Byte.SIZE);
        cb.lushr();
        pushType(long.class);
    }

    private void emitCast(Cast cast) {
        Class<?> fromType = cast.fromType();
        Class<?> toType = cast.toType();

        popType(fromType);
        switch (cast) {
            case INT_TO_BOOLEAN -> {
                // implement least significant byte non-zero test

                // select first byte
                cb.loadConstant(0xFF);
                cb.iand();

                // convert to boolean
                cb.invokestatic(CD_Utils, "byteToBoolean", MTD_BYTE_TO_BOOLEAN);
            }
            case INT_TO_BYTE -> cb.i2b();
            case INT_TO_CHAR -> cb.i2c();
            case INT_TO_SHORT -> cb.i2s();
            case BYTE_TO_LONG, CHAR_TO_LONG, SHORT_TO_LONG, INT_TO_LONG -> cb.i2l();
            case LONG_TO_BYTE -> { cb.l2i(); cb.i2b(); }
            case LONG_TO_SHORT -> { cb.l2i(); cb.i2s(); }
            case LONG_TO_CHAR -> { cb.l2i(); cb.i2c(); }
            case LONG_TO_INT -> cb.l2i();
            case BOOLEAN_TO_INT, BYTE_TO_INT, CHAR_TO_INT, SHORT_TO_INT -> {
                // no-op in bytecode
            }
            default -> throw new IllegalStateException("Unknown cast: " + cast);
        }
        pushType(toType);
    }

    private void emitSegmentBase() {
        popType(MemorySegment.class);
        cb.checkcast(CD_AbstractMemorySegmentImpl);
        cb.invokevirtual(CD_AbstractMemorySegmentImpl, "unsafeGetBase", MTD_UNSAFE_GET_BASE);
        pushType(Object.class);
    }

    private void emitSegmentOffset(SegmentOffset segmentOffset) {
        popType(MemorySegment.class);

        if (!segmentOffset.allowHeap()) {
            cb.dup();
            cb.invokestatic(CD_SharedUtils, "checkNative", MTD_CHECK_NATIVE);
        }
        cb.checkcast(CD_AbstractMemorySegmentImpl);
        cb.invokevirtual(CD_AbstractMemorySegmentImpl, "unsafeGetOffset", MTD_UNSAFE_GET_OFFSET);

        pushType(long.class);
    }

    private void emitBufferLoad(BufferLoad bufferLoad) {
        Class<?> loadType = bufferLoad.type();
        long offset = bufferLoad.offset();
        int byteWidth = bufferLoad.byteWidth();

        popType(MemorySegment.class);

        if (SharedUtils.isPowerOfTwo(byteWidth)) {
            ClassDesc valueLayoutType = emitLoadLayoutConstant(loadType);
            cb.loadConstant(offset);
            MethodTypeDesc descriptor = MethodTypeDesc.of(classDesc(loadType), valueLayoutType, CD_long);
            cb.invokeinterface(CD_MemorySegment, "get", descriptor);
        } else {
            // chunked
            int readAddrIdx = cb.allocateLocal(ReferenceType);
            cb.storeLocal(ReferenceType, readAddrIdx);

            cb.loadConstant(0L); // result
            int resultIdx = cb.allocateLocal(LongType);
            cb.storeLocal(LongType, resultIdx);

            int remaining = byteWidth;
            int chunkOffset = 0;
            do {
                int chunkSize = Integer.highestOneBit(remaining); // next power of 2
                Class<?> chunkType;
                ClassDesc toULongHolder;
                MethodTypeDesc toULongDescriptor;
                switch (chunkSize) {
                    case Integer.BYTES -> {
                        chunkType = int.class;
                        toULongHolder = CD_Integer;
                        toULongDescriptor = MTD_INTEGER_TO_UNSIGNED_LONG;
                    }
                    case Short.BYTES -> {
                        chunkType = short.class;
                        toULongHolder = CD_Short;
                        toULongDescriptor = MTD_SHORT_TO_UNSIGNED_LONG;
                    }
                    case Byte.BYTES -> {
                        chunkType = byte.class;
                        toULongHolder = CD_Byte;
                        toULongDescriptor = MTD_BYTE_TO_UNSIGNED_LONG;
                    }
                    default ->
                        throw new IllegalStateException("Unexpected chunk size for chunked write: " + chunkSize);
                }
                // read from segment
                cb.loadLocal(ReferenceType, readAddrIdx);
                ClassDesc valueLayoutType = emitLoadLayoutConstant(chunkType);
                MethodTypeDesc descriptor = MethodTypeDesc.of(classDesc(chunkType), valueLayoutType, CD_long);
                long readOffset = offset + SharedUtils.pickChunkOffset(chunkOffset, byteWidth, chunkSize);
                cb.loadConstant(readOffset);
                cb.invokeinterface(CD_MemorySegment, "get", descriptor);
                cb.invokestatic(toULongHolder, "toUnsignedLong", toULongDescriptor);

                // shift to right offset
                int shiftAmount = chunkOffset * Byte.SIZE;
                if (shiftAmount != 0) {
                    cb.loadConstant(shiftAmount);
                    cb.lshl();
                }
                // add to result
                cb.loadLocal(LongType, resultIdx);
                cb.lor();
                cb.storeLocal(LongType, resultIdx);

                remaining -= chunkSize;
                chunkOffset += chunkSize;
            } while (remaining != 0);

            cb.loadLocal(LongType, resultIdx);
            if (loadType == int.class) {
                cb.l2i();
            } else {
                assert loadType == long.class; // should not have chunking for other types
            }
        }

        pushType(loadType);
    }

    private void emitCopyBuffer(Copy copy) {
        long size = copy.size();
        long alignment = copy.alignment();

        popType(MemorySegment.class);

        // operand/srcSegment is on the stack
        // generating a call to:
        //   MemorySegment::copy(MemorySegment srcSegment, long srcOffset, MemorySegment dstSegment, long dstOffset, long bytes)
        cb.loadConstant(0L);
        // create the dstSegment by allocating it. Similar to:
        //   context.allocator().allocate(size, alignment)
        emitLoadInternalAllocator();
        emitAllocateCall(size, alignment);
        cb.dup();
        int storeIdx = cb.allocateLocal(ReferenceType);
        cb.storeLocal(ReferenceType, storeIdx);
        cb.loadConstant(0L);
        cb.loadConstant(size);
        cb.invokestatic(CD_MemorySegment, "copy", MTD_COPY, true);

        cb.loadLocal(ReferenceType, storeIdx);
        pushType(MemorySegment.class);
    }

    private void emitAllocateCall(long size, long alignment) {
        cb.loadConstant(size);
        cb.loadConstant(alignment);
        cb.invokeinterface(CD_SegmentAllocator, "allocate", MTD_ALLOCATE);
    }

    private ClassDesc emitLoadLayoutConstant(Class<?> type) {
        ClassDesc valueLayoutType = valueLayoutTypeFor(type);
        String valueLayoutConstantName = valueLayoutConstantFor(type);
        cb.getstatic(CD_ValueLayout, valueLayoutConstantName, valueLayoutType);
        return valueLayoutType;
    }

    private static String valueLayoutConstantFor(Class<?> type) {
        if (type == boolean.class) {
            return "JAVA_BOOLEAN";
        } else if (type == byte.class) {
            return "JAVA_BYTE";
        } else if (type == short.class) {
            return "JAVA_SHORT_UNALIGNED";
        } else if (type == char.class) {
            return "JAVA_CHAR_UNALIGNED";
        } else if (type == int.class) {
            return "JAVA_INT_UNALIGNED";
        } else if (type == long.class) {
            return "JAVA_LONG_UNALIGNED";
        } else if (type == float.class) {
            return "JAVA_FLOAT_UNALIGNED";
        } else if (type == double.class) {
            return "JAVA_DOUBLE_UNALIGNED";
        } else if (type == MemorySegment.class) {
            return "ADDRESS_UNALIGNED";
        } else {
            throw new IllegalStateException("Unknown type: " + type);
        }
    }

    private static ClassDesc valueLayoutTypeFor(Class<?> type) {
        if (type == boolean.class) {
            return CD_ValueLayout_OfBoolean;
        } else if (type == byte.class) {
            return CD_ValueLayout_OfByte;
        } else if (type == short.class) {
            return CD_ValueLayout_OfShort;
        } else if (type == char.class) {
            return CD_ValueLayout_OfChar;
        } else if (type == int.class) {
            return CD_ValueLayout_OfInt;
        } else if (type == long.class) {
            return CD_ValueLayout_OfLong;
        } else if (type == float.class) {
            return CD_ValueLayout_OfFloat;
        } else if (type == double.class) {
            return CD_ValueLayout_OfDouble;
        } else if (type == MemorySegment.class) {
            return CD_AddressLayout;
        } else {
            throw new IllegalStateException("Unknown type: " + type);
        }
    }

    private void emitDup(Class<?> type) {
        if (type == double.class || type == long.class) {
            cb.dup2();
        } else {
            cb.dup();
        }
    }

    /*
     * Low-level emit helpers.
     */

    private void emitConstZero(TypeKind kind) {
        switch (kind) {
            case BooleanType, ByteType, ShortType, CharType, IntType -> cb.iconst_0();
            case LongType -> cb.lconst_0();
            case FloatType -> cb.fconst_0();
            case DoubleType -> cb.dconst_0();
            case ReferenceType -> cb.aconst_null();
        }
    }
}

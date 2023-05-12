/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.Binding.Allocate;
import jdk.internal.foreign.abi.Binding.BoxAddress;
import jdk.internal.foreign.abi.Binding.BufferLoad;
import jdk.internal.foreign.abi.Binding.BufferStore;
import jdk.internal.foreign.abi.Binding.Cast;
import jdk.internal.foreign.abi.Binding.Copy;
import jdk.internal.foreign.abi.Binding.Dup;
import jdk.internal.foreign.abi.Binding.UnboxAddress;
import jdk.internal.foreign.abi.Binding.VMLoad;
import jdk.internal.foreign.abi.Binding.VMStore;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import sun.security.action.GetBooleanAction;
import sun.security.action.GetPropertyAction;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.constant.ConstantDescs;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
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

import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class BindingSpecializer {
    private static final String DUMP_CLASSES_DIR
        = GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.abi.Specializer.DUMP_CLASSES_DIR");
    private static final boolean PERFORM_VERIFICATION
        = GetBooleanAction.privilegedGetProperty("jdk.internal.foreign.abi.Specializer.PERFORM_VERIFICATION");

    // Bunch of helper constants
    private static final int CLASSFILE_VERSION = ClassFileFormatVersion.latest().major();

    private static final String OBJECT_DESC = Object.class.descriptorString();
    private static final String OBJECT_INTRN = Type.getInternalName(Object.class);

    private static final String VOID_DESC = methodType(void.class).descriptorString();

    private static final String BINDING_CONTEXT_DESC = Binding.Context.class.descriptorString();
    private static final String OF_BOUNDED_ALLOCATOR_DESC = methodType(Binding.Context.class, long.class).descriptorString();
    private static final String OF_SCOPE_DESC = methodType(Binding.Context.class).descriptorString();
    private static final String ALLOCATOR_DESC = methodType(SegmentAllocator.class).descriptorString();
    private static final String SCOPE_DESC = methodType(SegmentScope.class).descriptorString();
    private static final String SESSION_IMPL_DESC = methodType(MemorySessionImpl.class).descriptorString();
    private static final String CLOSE_DESC = VOID_DESC;
    private static final String UNBOX_SEGMENT_DESC = methodType(long.class, MemorySegment.class).descriptorString();
    private static final String COPY_DESC = methodType(void.class, MemorySegment.class, long.class, MemorySegment.class, long.class, long.class).descriptorString();
    private static final String OF_LONG_DESC = methodType(MemorySegment.class, long.class, long.class).descriptorString();
    private static final String OF_LONG_UNCHECKED_DESC = methodType(MemorySegment.class, long.class, long.class, SegmentScope.class).descriptorString();
    private static final String ALLOCATE_DESC = methodType(MemorySegment.class, long.class, long.class).descriptorString();
    private static final String HANDLE_UNCAUGHT_EXCEPTION_DESC = methodType(void.class, Throwable.class).descriptorString();
    private static final String METHOD_HANDLES_INTRN = Type.getInternalName(MethodHandles.class);
    private static final String CLASS_DATA_DESC = methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class).descriptorString();
    private static final String RELEASE0_DESC = VOID_DESC;
    private static final String ACQUIRE0_DESC = VOID_DESC;
    private static final String INTEGER_TO_UNSIGNED_LONG_DESC = MethodType.methodType(long.class, int.class).descriptorString();
    private static final String SHORT_TO_UNSIGNED_LONG_DESC = MethodType.methodType(long.class, short.class).descriptorString();
    private static final String BYTE_TO_UNSIGNED_LONG_DESC = MethodType.methodType(long.class, byte.class).descriptorString();

    private static final Handle BSM_CLASS_DATA = new Handle(
            H_INVOKESTATIC,
            METHOD_HANDLES_INTRN,
            "classData",
            CLASS_DATA_DESC,
            false);
    private static final ConstantDynamic CLASS_DATA_CONDY = new ConstantDynamic(
            ConstantDescs.DEFAULT_NAME,
            OBJECT_DESC,
            BSM_CLASS_DATA);

    private static final String CLASS_NAME_DOWNCALL = "jdk/internal/foreign/abi/DowncallStub";
    private static final String CLASS_NAME_UPCALL = "jdk/internal/foreign/abi/UpcallStub";
    private static final String METHOD_NAME = "invoke";

    private static final String SUPER_NAME = OBJECT_INTRN;

    // Instance fields start here
    private final MethodVisitor mv;
    private final MethodType callerMethodType;
    private final CallingSequence callingSequence;
    private final ABIDescriptor abi;
    private final MethodType leafType;

    private int localIdx = 0;
    private int[] paramIndex2ParamSlot;
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

    private BindingSpecializer(MethodVisitor mv, MethodType callerMethodType, CallingSequence callingSequence, ABIDescriptor abi, MethodType leafType) {
        this.mv = mv;
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
            MethodHandles.Lookup definedClassLookup = MethodHandles.lookup().defineHiddenClassWithClassData(bytes, leafHandle, false);
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
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
        cw.visit(CLASSFILE_VERSION, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, className, null, SUPER_NAME, null);

        String descriptor = callerMethodType.descriptorString();
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, METHOD_NAME, descriptor, null, null);

        new BindingSpecializer(mv, callerMethodType, callingSequence, abi, leafType).specialize();

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
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
            boolean printResults = false; // only print in case of exception
            CheckClassAdapter.verify(new ClassReader(bytes), null, printResults, new PrintWriter(System.err));
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
        // map of parameter indexes to local var table slots
        paramIndex2ParamSlot = new int[callerMethodType.parameterCount()];
        for (int i = 0; i < callerMethodType.parameterCount(); i++) {
            paramIndex2ParamSlot[i] = newLocal(callerMethodType.parameterType(i));
        }

        // slots that store the output arguments (passed to the leaf handle)
        leafArgSlots = new int[leafType.parameterCount()];
        for (int i = 0; i < leafType.parameterCount(); i++) {
            leafArgSlots[i] = newLocal(leafType.parameterType(i));
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
                    int scopeLocal = newLocal(Object.class);
                    initialScopeSlots[numScopes++] = scopeLocal;
                    emitConst(null);
                    emitStore(Object.class, scopeLocal); // need to initialize all scope locals here in case an exception occurs
                }
            }
            scopeSlots = Arrays.copyOf(initialScopeSlots, numScopes); // fit to size
            curScopeLocalIdx = 0; // used from emitGetInput
        }

        // create a Binding.Context for this call
        if (callingSequence.allocationSize() != 0) {
            emitConst(callingSequence.allocationSize());
            emitInvokeStatic(Binding.Context.class, "ofBoundedAllocator", OF_BOUNDED_ALLOCATOR_DESC);
        } else if (callingSequence.forUpcall() && needsSession()) {
            emitInvokeStatic(Binding.Context.class, "ofScope", OF_SCOPE_DESC);
        } else {
            emitGetStatic(Binding.Context.class, "DUMMY", BINDING_CONTEXT_DESC);
        }
        contextIdx = newLocal(Object.class);
        emitStore(Object.class, contextIdx);

        // in case the call needs a return buffer, allocate it here.
        // for upcalls the VM wrapper stub allocates the buffer.
        if (callingSequence.needsReturnBuffer() && callingSequence.forDowncall()) {
            emitLoadInternalAllocator();
            emitAllocateCall(callingSequence.returnBufferSize(), 1);
            returnBufferIdx = newLocal(Object.class);
            emitStore(Object.class, returnBufferIdx);
        }

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchStart = new Label();

        mv.visitLabel(tryStart);

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
                    emitLoad(Object.class, returnBufferIdx);
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
                    returnBufferIdx = newLocal(Object.class);
                    emitStore(Object.class, returnBufferIdx);
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
            mv.visitLdcInsn(CLASS_DATA_CONDY);
        } else {
            emitLoad(Object.class, 0); // load target arg
        }
        emitCheckCast(MethodHandle.class);
        // load all the leaf args
        for (int i = 0; i < leafArgSlots.length; i++) {
            emitLoad(leafArgTypes.get(i), leafArgSlots[i]);
        }
        // call leaf MH
        emitInvokeVirtual(MethodHandle.class, "invokeExact", leafType.descriptorString());

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
            mv.visitLabel(tryEnd);
            // finally
            emitCleanup();

            if (callerMethodType.returnType() == void.class) {
                // The case for upcalls that return by return buffer
                assert typeStack.isEmpty();
                mv.visitInsn(RETURN);
            } else {
                popType(callerMethodType.returnType());
                assert typeStack.isEmpty();
                emitReturn(callerMethodType.returnType());
            }
        } else {
            assert callerMethodType.returnType() == void.class;
            assert typeStack.isEmpty();
            mv.visitLabel(tryEnd);
            // finally
            emitCleanup();
            mv.visitInsn(RETURN);
        }

        mv.visitLabel(catchStart);
        // finally
        emitCleanup();
        if (callingSequence.forDowncall()) {
            mv.visitInsn(ATHROW);
        } else {
           emitInvokeStatic(SharedUtils.class, "handleUncaughtException", HANDLE_UNCAUGHT_EXCEPTION_DESC);
           if (callerMethodType.returnType() != void.class) {
               emitConstZero(callerMethodType.returnType());
               emitReturn(callerMethodType.returnType());
           } else {
               mv.visitInsn(RETURN);
           }
        }

        mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, null);
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
        return paramLayout instanceof ValueLayout.OfAddress;
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
                case VMStore vmStore         -> emitVMStore(vmStore);
                case VMLoad vmLoad           -> emitVMLoad(vmLoad);
                case BufferStore bufferStore -> emitBufferStore(bufferStore);
                case BufferLoad bufferLoad   -> emitBufferLoad(bufferLoad);
                case Copy copy               -> emitCopyBuffer(copy);
                case Allocate allocate       -> emitAllocBuffer(allocate);
                case BoxAddress boxAddress   -> emitBoxAddress(boxAddress);
                case UnboxAddress unused     -> emitUnboxAddress();
                case Dup unused              -> emitDupBinding();
                case Cast cast               -> emitCast(cast);
            }
        }
    }

    private void emitSetOutput(Class<?> storeType) {
        emitStore(storeType, leafArgSlots[leafArgTypes.size()]);
        leafArgTypes.add(storeType);
    }

    private void emitGetInput() {
        Class<?> highLevelType = callerMethodType.parameterType(paramIndex);
        emitLoad(highLevelType, paramIndex2ParamSlot[paramIndex]);

        if (shouldAcquire(paramIndex)) {
            emitDup(Object.class);
            emitAcquireScope();
        }

        pushType(highLevelType);
        paramIndex++;
    }

    private void emitAcquireScope() {
        emitCheckCast(AbstractMemorySegmentImpl.class);
        emitInvokeVirtual(AbstractMemorySegmentImpl.class, "sessionImpl", SESSION_IMPL_DESC);
        Label skipAcquire = new Label();
        Label end = new Label();

        // start with 1 scope to maybe acquire on the stack
        assert curScopeLocalIdx != -1;
        boolean hasOtherScopes = curScopeLocalIdx != 0;
        for (int i = 0; i < curScopeLocalIdx; i++) {
            emitDup(Object.class); // dup for comparison
            emitLoad(Object.class, scopeSlots[i]);
            mv.visitJumpInsn(IF_ACMPEQ, skipAcquire);
        }

        // 1 scope to acquire on the stack
        emitDup(Object.class);
        int nextScopeLocal = scopeSlots[curScopeLocalIdx++];
        // call acquire first here. So that if it fails, we don't call release
        emitInvokeVirtual(MemorySessionImpl.class, "acquire0", ACQUIRE0_DESC); // call acquire on the other
        emitStore(Object.class, nextScopeLocal); // store off one to release later

        if (hasOtherScopes) { // avoid ASM generating a bunch of nops for the dead code
            mv.visitJumpInsn(GOTO, end);

            mv.visitLabel(skipAcquire);
            mv.visitInsn(POP); // drop scope
        }

        mv.visitLabel(end);
    }

    private void emitReleaseScopes() {
        for (int scopeLocal : scopeSlots) {
            Label skipRelease = new Label();

            emitLoad(Object.class, scopeLocal);
            mv.visitJumpInsn(IFNULL, skipRelease);
            emitLoad(Object.class, scopeLocal);
            emitInvokeVirtual(MemorySessionImpl.class, "release0", RELEASE0_DESC);
            mv.visitLabel(skipRelease);
        }
    }

    private void emitSaveReturnValue(Class<?> storeType) {
        retValIdx = newLocal(storeType);
        emitStore(storeType, retValIdx);
    }

    private void emitRestoreReturnValue(Class<?> loadType) {
        assert retValIdx != -1;
        emitLoad(loadType, retValIdx);
        pushType(loadType);
    }

    private int newLocal(Class<?> type) {
        int idx = localIdx;
        localIdx += Type.getType(type).getSize();
        return idx;
    }

    private void emitLoadInternalSession() {
        assert contextIdx != -1;
        emitLoad(Object.class, contextIdx);
        emitInvokeVirtual(Binding.Context.class, "scope", SCOPE_DESC);
    }

    private void emitLoadInternalAllocator() {
        assert contextIdx != -1;
        emitLoad(Object.class, contextIdx);
        emitInvokeVirtual(Binding.Context.class, "allocator", ALLOCATOR_DESC);
    }

    private void emitCloseContext() {
        assert contextIdx != -1;
        emitLoad(Object.class, contextIdx);
        emitInvokeVirtual(Binding.Context.class, "close", CLOSE_DESC);
    }

    private void emitBoxAddress(BoxAddress boxAddress) {
        popType(long.class);
        emitConst(boxAddress.size());
        if (needsSession()) {
            emitLoadInternalSession();
            emitInvokeStatic(NativeMemorySegmentImpl.class, "makeNativeSegmentUnchecked", OF_LONG_UNCHECKED_DESC);
        } else {
            emitInvokeStatic(NativeMemorySegmentImpl.class, "makeNativeSegmentUnchecked", OF_LONG_DESC);
        }
        pushType(MemorySegment.class);
    }

    private void emitAllocBuffer(Allocate binding) {
        if (callingSequence.forDowncall()) {
            assert returnAllocatorIdx != -1;
            emitLoad(Object.class, returnAllocatorIdx);
        } else {
            emitLoadInternalAllocator();
        }
        emitAllocateCall(binding.size(), binding.alignment());
        pushType(MemorySegment.class);
    }

    private void emitBufferStore(BufferStore bufferStore) {
        Class<?> storeType = bufferStore.type();
        long offset = bufferStore.offset();
        int byteWidth = bufferStore.byteWidth();

        popType(storeType);
        popType(MemorySegment.class);

        if (SharedUtils.isPowerOfTwo(byteWidth)) {
            int valueIdx = newLocal(storeType);
            emitStore(storeType, valueIdx);

            Class<?> valueLayoutType = emitLoadLayoutConstant(storeType);
            emitConst(offset);
            emitLoad(storeType, valueIdx);
            String descriptor = methodType(void.class, valueLayoutType, long.class, storeType).descriptorString();
            emitInvokeInterface(MemorySegment.class, "set", descriptor);
        } else {
            // long longValue = ((Number) value).longValue();
            if (storeType == int.class) {
                mv.visitInsn(I2L);
            } else {
                assert storeType == long.class; // chunking only for int and long
            }
            int longValueIdx = newLocal(long.class);
            emitStore(long.class, longValueIdx);
            int writeAddrIdx = newLocal(MemorySegment.class);
            emitStore(MemorySegment.class, writeAddrIdx);

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
                emitLoad(long.class, longValueIdx);
                emitConst(mask);
                mv.visitInsn(LAND);
                if (shiftAmount != 0) {
                    emitConst(shiftAmount);
                    mv.visitInsn(LUSHR);
                }
                mv.visitInsn(L2I);
                int chunkIdx = newLocal(chunkStoreType);
                emitStore(chunkStoreType, chunkIdx);
                // chunk done, now write it

                //writeAddress.set(JAVA_SHORT_UNALIGNED, offset, writeChunk);
                emitLoad(MemorySegment.class, writeAddrIdx);
                Class<?> valueLayoutType = emitLoadLayoutConstant(chunkStoreType);
                long writeOffset = offset + SharedUtils.pickChunkOffset(chunkOffset, byteWidth, chunkSize);
                emitConst(writeOffset);
                emitLoad(chunkStoreType, chunkIdx);
                String descriptor = methodType(void.class, valueLayoutType, long.class, chunkStoreType).descriptorString();
                emitInvokeInterface(MemorySegment.class, "set", descriptor);

                remaining -= chunkSize;
                chunkOffset += chunkSize;
            } while (remaining != 0);
        }
    }

    // VM_STORE and VM_LOAD are emulated, which is different for down/upcalls
    private void emitVMStore(VMStore vmStore) {
        Class<?> storeType = vmStore.type();
        popType(storeType);

        if (callingSequence.forDowncall()) {
            // processing arg
            emitSetOutput(storeType);
        } else {
            // processing return
            if (!callingSequence.needsReturnBuffer()) {
                emitSaveReturnValue(storeType);
            } else {
                int valueIdx = newLocal(storeType);
                emitStore(storeType, valueIdx); // store away the stored value, need it later

                assert returnBufferIdx != -1;
                emitLoad(Object.class, returnBufferIdx);
                Class<?> valueLayoutType = emitLoadLayoutConstant(storeType);
                emitConst(retBufOffset);
                emitLoad(storeType, valueIdx);
                String descriptor = methodType(void.class, valueLayoutType, long.class, storeType).descriptorString();
                emitInvokeInterface(MemorySegment.class, "set", descriptor);
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
                emitLoad(Object.class, returnBufferIdx);
                Class<?> valueLayoutType = emitLoadLayoutConstant(loadType);
                emitConst(retBufOffset);
                String descriptor = methodType(loadType, valueLayoutType, long.class).descriptorString();
                emitInvokeInterface(MemorySegment.class, "get", descriptor);
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

    private void emitCast(Cast cast) {
        Class<?> fromType = cast.fromType();
        Class<?> toType = cast.toType();

        popType(fromType);
        switch (cast) {
            case INT_TO_BOOLEAN -> {
                // implement least significant byte non-zero test

                // select first byte
                emitConst(0xFF);
                mv.visitInsn(IAND);

                // convert to boolean
                emitInvokeStatic(Utils.class, "byteToBoolean", "(B)Z");
            }
            case INT_TO_BYTE -> mv.visitInsn(I2B);
            case INT_TO_CHAR -> mv.visitInsn(I2C);
            case INT_TO_SHORT -> mv.visitInsn(I2S);
            case BOOLEAN_TO_INT, BYTE_TO_INT, CHAR_TO_INT, SHORT_TO_INT -> {
                // no-op in bytecode
            }
            default -> throw new IllegalStateException("Unknown cast: " + cast);
        }
        pushType(toType);
    }

    private void emitUnboxAddress() {
        popType(MemorySegment.class);
        emitInvokeStatic(SharedUtils.class, "unboxSegment", UNBOX_SEGMENT_DESC);
        pushType(long.class);
    }

    private void emitBufferLoad(BufferLoad bufferLoad) {
        Class<?> loadType = bufferLoad.type();
        long offset = bufferLoad.offset();
        int byteWidth = bufferLoad.byteWidth();

        popType(MemorySegment.class);

        if (SharedUtils.isPowerOfTwo(byteWidth)) {
            Class<?> valueLayoutType = emitLoadLayoutConstant(loadType);
            emitConst(offset);
            String descriptor = methodType(loadType, valueLayoutType, long.class).descriptorString();
            emitInvokeInterface(MemorySegment.class, "get", descriptor);
        } else {
            // chunked
            int readAddrIdx = newLocal(MemorySegment.class);
            emitStore(MemorySegment.class, readAddrIdx);

            emitConstZero(long.class); // result
            int resultIdx = newLocal(long.class);
            emitStore(long.class, resultIdx);

            int remaining = byteWidth;
            int chunkOffset = 0;
            do {
                int chunkSize = Integer.highestOneBit(remaining); // next power of 2
                Class<?> chunkType;
                Class<?> toULongHolder;
                String toULongDescriptor;
                switch (chunkSize) {
                    case Integer.BYTES -> {
                        chunkType = int.class;
                        toULongHolder = Integer.class;
                        toULongDescriptor = INTEGER_TO_UNSIGNED_LONG_DESC;
                    }
                    case Short.BYTES -> {
                        chunkType = short.class;
                        toULongHolder = Short.class;
                        toULongDescriptor = SHORT_TO_UNSIGNED_LONG_DESC;
                    }
                    case Byte.BYTES -> {
                        chunkType = byte.class;
                        toULongHolder = Byte.class;
                        toULongDescriptor = BYTE_TO_UNSIGNED_LONG_DESC;
                    }
                    default ->
                        throw new IllegalStateException("Unexpected chunk size for chunked write: " + chunkSize);
                }
                // read from segment
                emitLoad(MemorySegment.class, readAddrIdx);
                Class<?> valueLayoutType = emitLoadLayoutConstant(chunkType);
                String descriptor = methodType(chunkType, valueLayoutType, long.class).descriptorString();
                long readOffset = offset + SharedUtils.pickChunkOffset(chunkOffset, byteWidth, chunkSize);
                emitConst(readOffset);
                emitInvokeInterface(MemorySegment.class, "get", descriptor);
                emitInvokeStatic(toULongHolder, "toUnsignedLong", toULongDescriptor);

                // shift to right offset
                int shiftAmount = chunkOffset * Byte.SIZE;
                if (shiftAmount != 0) {
                    emitConst(shiftAmount);
                    mv.visitInsn(LSHL);
                }
                // add to result
                emitLoad(long.class, resultIdx);
                mv.visitInsn(LOR);
                emitStore(long.class, resultIdx);

                remaining -= chunkSize;
                chunkOffset += chunkSize;
            } while (remaining != 0);

            emitLoad(long.class, resultIdx);
            if (loadType == int.class) {
                mv.visitInsn(L2I);
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
        emitConst(0L);
        // create the dstSegment by allocating it. Similar to:
        //   context.allocator().allocate(size, alignment)
        emitLoadInternalAllocator();
        emitAllocateCall(size, alignment);
        emitDup(Object.class);
        int storeIdx = newLocal(Object.class);
        emitStore(Object.class, storeIdx);
        emitConst(0L);
        emitConst(size);
        emitInvokeStatic(MemorySegment.class, "copy", COPY_DESC);

        emitLoad(Object.class, storeIdx);
        pushType(MemorySegment.class);
    }

    private void emitAllocateCall(long size, long alignment) {
        emitConst(size);
        emitConst(alignment);
        emitInvokeInterface(SegmentAllocator.class, "allocate", ALLOCATE_DESC);
    }

    private Class<?> emitLoadLayoutConstant(Class<?> type) {
        Class<?> valueLayoutType = valueLayoutTypeFor(type);
        String valueLayoutConstantName = valueLayoutConstantFor(type);
        emitGetStatic(ValueLayout.class, valueLayoutConstantName, valueLayoutType.descriptorString());
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

    private static Class<?> valueLayoutTypeFor(Class<?> type) {
        if (type == boolean.class) {
            return ValueLayout.OfBoolean.class;
        } else if (type == byte.class) {
            return ValueLayout.OfByte.class;
        } else if (type == short.class) {
            return ValueLayout.OfShort.class;
        } else if (type == char.class) {
            return ValueLayout.OfChar.class;
        } else if (type == int.class) {
            return ValueLayout.OfInt.class;
        } else if (type == long.class) {
            return ValueLayout.OfLong.class;
        } else if (type == float.class) {
            return ValueLayout.OfFloat.class;
        } else if (type == double.class) {
            return ValueLayout.OfDouble.class;
        } else if (type == MemorySegment.class) {
            return ValueLayout.OfAddress.class;
        } else {
            throw new IllegalStateException("Unknown type: " + type);
        }
    }

    private void emitInvokeStatic(Class<?> owner, String methodName, String descriptor) {
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(owner), methodName, descriptor, owner.isInterface());
    }

    private void emitInvokeInterface(Class<?> owner, String methodName, String descriptor) {
        mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(owner), methodName, descriptor, true);
    }

    private void emitInvokeVirtual(Class<?> owner, String methodName, String descriptor) {
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(owner), methodName, descriptor, false);
    }

    private void emitGetStatic(Class<?> owner, String fieldName, String descriptor) {
        mv.visitFieldInsn(GETSTATIC, Type.getInternalName(owner), fieldName, descriptor);
    }

    private void emitCheckCast(Class<?> cls) {
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(cls));
    }

    private void emitDup(Class<?> type) {
        if (type == double.class || type == long.class) {
            mv.visitInsn(DUP2);
        } else {
            mv.visitInsn(Opcodes.DUP);
        }
    }

    /*
     * Low-level emit helpers.
     */

    private void emitConstZero(Class<?> type) {
        emitConst(switch (Type.getType(type).getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> 0;
            case Type.LONG -> 0L;
            case Type.FLOAT -> 0F;
            case Type.DOUBLE -> 0D;
            case Type.OBJECT -> null;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        });
    }

    private void emitConst(Object con) {
        if (con == null) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            return;
        }
        if (con instanceof Integer i) {
            emitIconstInsn(i);
            return;
        }
        if (con instanceof Byte b) {
            emitIconstInsn(b);
            return;
        }
        if (con instanceof Short s) {
            emitIconstInsn(s);
            return;
        }
        if (con instanceof Character c) {
            emitIconstInsn(c);
            return;
        }
        if (con instanceof Long l) {
            long x = l;
            short sx = (short)x;
            if (x == sx) {
                if (sx >= 0 && sx <= 1) {
                    mv.visitInsn(Opcodes.LCONST_0 + (int) sx);
                } else {
                    emitIconstInsn((int) x);
                    mv.visitInsn(Opcodes.I2L);
                }
                return;
            }
        }
        if (con instanceof Float f) {
            float x = f;
            short sx = (short)x;
            if (x == sx) {
                if (sx >= 0 && sx <= 2) {
                    mv.visitInsn(Opcodes.FCONST_0 + (int) sx);
                } else {
                    emitIconstInsn((int) x);
                    mv.visitInsn(Opcodes.I2F);
                }
                return;
            }
        }
        if (con instanceof Double d) {
            double x = d;
            short sx = (short)x;
            if (x == sx) {
                if (sx >= 0 && sx <= 1) {
                    mv.visitInsn(Opcodes.DCONST_0 + (int) sx);
                } else {
                    emitIconstInsn((int) x);
                    mv.visitInsn(Opcodes.I2D);
                }
                return;
            }
        }
        if (con instanceof Boolean b) {
            emitIconstInsn(b ? 1 : 0);
            return;
        }
        // fall through:
        mv.visitLdcInsn(con);
    }

    private void emitIconstInsn(int cst) {
        if (cst >= -1 && cst <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + cst);
        } else if (cst >= Byte.MIN_VALUE && cst <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, cst);
        } else if (cst >= Short.MIN_VALUE && cst <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, cst);
        } else {
            mv.visitLdcInsn(cst);
        }
    }

    private void emitLoad(Class<?> type, int index) {
        int opcode = Type.getType(type).getOpcode(ILOAD);
        mv.visitVarInsn(opcode, index);
    }

    private void emitStore(Class<?> type, int index) {
        int opcode =  Type.getType(type).getOpcode(ISTORE);
        mv.visitVarInsn(opcode, index);
    }

    private void emitReturn(Class<?> type) {
        int opcode = Type.getType(type).getOpcode(IRETURN);
        mv.visitInsn(opcode);
    }

}

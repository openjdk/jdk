/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.MemoryAddressImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Scoped;
import jdk.internal.misc.VM;
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
import java.lang.foreign.Addressable;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.BiPredicate;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class BindingSpecializer {
    private static final String DUMP_CLASSES_DIR
        = GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.abi.Specializer.DUMP_CLASSES_DIR");
    private static final boolean PERFORM_VERIFICATION
        = GetBooleanAction.privilegedGetProperty("jdk.internal.foreign.abi.Specializer.PERFORM_VERIFICATION");

    // Bunch of helper constants
    private static final int CLASSFILE_VERSION = VM.classFileVersion();

    private static final String OBJECT_DESC = Object.class.descriptorString();
    private static final String OBJECT_INTRN = Type.getInternalName(Object.class);

    private static final String VOID_DESC = methodType(void.class).descriptorString();

    private static final String BINDING_CONTEXT_DESC = Binding.Context.class.descriptorString();
    private static final String OF_BOUNDED_ALLOCATOR_DESC = methodType(Binding.Context.class, long.class).descriptorString();
    private static final String OF_SESSION_DESC = methodType(Binding.Context.class).descriptorString();
    private static final String ALLOCATOR_DESC = methodType(SegmentAllocator.class).descriptorString();
    private static final String SESSION_DESC = methodType(MemorySession.class).descriptorString();
    private static final String SESSION_IMPL_DESC = methodType(MemorySessionImpl.class).descriptorString();
    private static final String CLOSE_DESC = VOID_DESC;
    private static final String ADDRESS_DESC = methodType(MemoryAddress.class).descriptorString();
    private static final String COPY_DESC = methodType(void.class, MemorySegment.class, long.class, MemorySegment.class, long.class, long.class).descriptorString();
    private static final String TO_RAW_LONG_VALUE_DESC = methodType(long.class).descriptorString();
    private static final String OF_LONG_DESC = methodType(MemoryAddress.class, long.class).descriptorString();
    private static final String OF_LONG_UNCHECKED_DESC = methodType(MemorySegment.class, long.class, long.class, MemorySession.class).descriptorString();
    private static final String ALLOCATE_DESC = methodType(MemorySegment.class, long.class, long.class).descriptorString();
    private static final String HANDLE_UNCAUGHT_EXCEPTION_DESC = methodType(void.class, Throwable.class).descriptorString();
    private static final String METHOD_HANDLES_INTRN = Type.getInternalName(MethodHandles.class);
    private static final String CLASS_DATA_DESC = methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class).descriptorString();
    private static final String RELEASE0_DESC = VOID_DESC;
    private static final String ACQUIRE0_DESC = VOID_DESC;

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

    private static final SoftReferenceCache<FunctionDescriptor, MethodHandle> UPCALL_WRAPPER_CACHE = new SoftReferenceCache<>();

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
    private int CONTEXT_IDX = -1;
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

    static MethodHandle specialize(MethodHandle leafHandle, CallingSequence callingSequence, ABIDescriptor abi) {
        if (callingSequence.forUpcall()) {
            MethodHandle wrapper = UPCALL_WRAPPER_CACHE.get(callingSequence.functionDesc(), fd -> specializeUpcall(leafHandle, callingSequence, abi));
            return MethodHandles.insertArguments(wrapper, 0, leafHandle); // lazily customized for leaf handle instances
        } else {
            return specializeDowncall(leafHandle, callingSequence, abi);
        }
    }

    private static MethodHandle specializeDowncall(MethodHandle leafHandle, CallingSequence callingSequence, ABIDescriptor abi) {
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

    private static MethodHandle specializeUpcall(MethodHandle leafHandle, CallingSequence callingSequence, ABIDescriptor abi) {
        MethodType callerMethodType = callingSequence.callerMethodType();
        callerMethodType = callerMethodType.insertParameterTypes(0, MethodHandle.class); // target

        byte[] bytes = specializeHelper(leafHandle.type(), callerMethodType, callingSequence, abi);

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
        return popType(expected, ASSERT_EQUALS);
    }

    private Class<?> popType(Class<?> expected, BiPredicate<Class<?>, Class<?>> typePredicate) {
        Class<?> found;
        if (!typePredicate.test(expected, found = typeStack.pop())) {
            throw new IllegalStateException(
                    String.format("Invalid type on binding operand stack; found %s - expected %s",
                            found.descriptorString(), expected.descriptorString()));
        }
        return found;
    }

    private static final BiPredicate<Class<?>, Class<?>> ASSERT_EQUALS = Class::equals;
    private static final BiPredicate<Class<?>, Class<?>> ASSERT_ASSIGNABLE = Class::isAssignableFrom;

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
                if (shouldAcquire(callerMethodType.parameterType(i))) {
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
            emitInvokeStatic(Binding.Context.class, "ofSession", OF_SESSION_DESC);
        } else {
            emitGetStatic(Binding.Context.class, "DUMMY", BINDING_CONTEXT_DESC);
        }
        CONTEXT_IDX = newLocal(Object.class);
        emitStore(Object.class, CONTEXT_IDX);

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
        return callingSequence.argumentBindings().anyMatch(Binding.ToSegment.class::isInstance);
    }

    private static boolean shouldAcquire(Class<?> type) {
        return type == Addressable.class;
    }

    private void emitCleanup() {
        emitCloseContext();
        if (callingSequence.forDowncall()) {
            emitReleaseScopes();
        }
    }

    private void doBindings(List<Binding> bindings) {
        for (Binding binding : bindings) {
            switch (binding.tag()) {
                case VM_STORE -> emitVMStore((Binding.VMStore) binding);
                case VM_LOAD -> emitVMLoad((Binding.VMLoad) binding);
                case BUFFER_STORE -> emitBufferStore((Binding.BufferStore) binding);
                case BUFFER_LOAD -> emitBufferLoad((Binding.BufferLoad) binding);
                case COPY_BUFFER -> emitCopyBuffer((Binding.Copy) binding);
                case ALLOC_BUFFER -> emitAllocBuffer((Binding.Allocate) binding);
                case BOX_ADDRESS -> emitBoxAddress();
                case UNBOX_ADDRESS -> emitUnboxAddress();
                case TO_SEGMENT -> emitToSegment((Binding.ToSegment) binding);
                case DUP -> emitDupBinding();
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

        if (shouldAcquire(highLevelType)) {
            emitDup(Object.class);
            emitAcquireScope();
        }

        pushType(highLevelType);
        paramIndex++;
    }

    private void emitAcquireScope() {
        emitCheckCast(Scoped.class);
        emitInvokeInterface(Scoped.class, "sessionImpl", SESSION_IMPL_DESC);
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
        emitStore(Object.class, nextScopeLocal); // store off one to release later
        emitInvokeVirtual(MemorySessionImpl.class, "acquire0", ACQUIRE0_DESC); // call acquire on the other

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
        assert CONTEXT_IDX != -1;
        emitLoad(Object.class, CONTEXT_IDX);
        emitInvokeVirtual(Binding.Context.class, "session", SESSION_DESC);
    }

    private void emitLoadInternalAllocator() {
        assert CONTEXT_IDX != -1;
        emitLoad(Object.class, CONTEXT_IDX);
        emitInvokeVirtual(Binding.Context.class, "allocator", ALLOCATOR_DESC);
    }

    private void emitCloseContext() {
        assert CONTEXT_IDX != -1;
        emitLoad(Object.class, CONTEXT_IDX);
        emitInvokeVirtual(Binding.Context.class, "close", CLOSE_DESC);
    }

    private void emitToSegment(Binding.ToSegment binding) {
        long size = binding.size();
        popType(MemoryAddress.class);

        emitToRawLongValue();
        emitConst(size);
        emitLoadInternalSession();
        emitInvokeStatic(MemoryAddressImpl.class, "ofLongUnchecked", OF_LONG_UNCHECKED_DESC);

        pushType(MemorySegment.class);
    }

    private void emitToRawLongValue() {
        emitInvokeInterface(MemoryAddress.class, "toRawLongValue", TO_RAW_LONG_VALUE_DESC);
    }

    private void emitBoxAddress() {
        popType(long.class);
        emitInvokeStatic(MemoryAddress.class, "ofLong", OF_LONG_DESC);
        pushType(MemoryAddress.class);
    }

    private void emitAllocBuffer(Binding.Allocate binding) {
        if (callingSequence.forDowncall()) {
            assert returnAllocatorIdx != -1;
            emitLoad(Object.class, returnAllocatorIdx);
        } else {
            emitLoadInternalAllocator();
        }
        emitAllocateCall(binding.size(), binding.alignment());
        pushType(MemorySegment.class);
    }

    private void emitBufferStore(Binding.BufferStore bufferStore) {
        Class<?> storeType = bufferStore.type();
        long offset = bufferStore.offset();

        popType(storeType);
        popType(MemorySegment.class);
        int valueIdx = newLocal(storeType);
        emitStore(storeType, valueIdx);

        Class<?> valueLayoutType = emitLoadLayoutConstant(storeType);
        emitConst(offset);
        emitLoad(storeType, valueIdx);
        String descriptor = methodType(void.class, valueLayoutType, long.class, storeType).descriptorString();
        emitInvokeInterface(MemorySegment.class, "set", descriptor);
    }


    // VM_STORE and VM_LOAD are emulated, which is different for down/upcalls
    private void emitVMStore(Binding.VMStore vmStore) {
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

    private void emitVMLoad(Binding.VMLoad vmLoad) {
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

    private void emitUnboxAddress() {
        popType(Addressable.class, ASSERT_ASSIGNABLE);
        emitInvokeInterface(Addressable.class, "address", ADDRESS_DESC);
        emitToRawLongValue();
        pushType(long.class);
    }

    private void emitBufferLoad(Binding.BufferLoad bufferLoad) {
        Class<?> loadType = bufferLoad.type();
        long offset = bufferLoad.offset();

        popType(MemorySegment.class);

        Class<?> valueLayoutType = emitLoadLayoutConstant(loadType);
        emitConst(offset);
        String descriptor = methodType(loadType, valueLayoutType, long.class).descriptorString();
        emitInvokeInterface(MemorySegment.class, "get", descriptor);
        pushType(loadType);
    }

    private void emitCopyBuffer(Binding.Copy copy) {
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
        emitGetStatic(BindingSpecializer.Runtime.class, valueLayoutConstantName, valueLayoutType.descriptorString());
        return valueLayoutType;
    }

    private static String valueLayoutConstantFor(Class<?> type) {
        if (type == boolean.class) {
            return "JAVA_BOOLEAN_UNALIGNED";
        } else if (type == byte.class) {
            return "JAVA_BYTE_UNALIGNED";
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
        } else if (type == MemoryAddress.class) {
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
        } else if (type == MemoryAddress.class) {
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

    // constants that are accessed from the generated bytecode
    // see emitLoadLayoutConstant
    static class Runtime {
        // unaligned constants
        static final ValueLayout.OfBoolean JAVA_BOOLEAN_UNALIGNED = JAVA_BOOLEAN;
        static final ValueLayout.OfByte JAVA_BYTE_UNALIGNED = JAVA_BYTE;
        static final ValueLayout.OfShort JAVA_SHORT_UNALIGNED = JAVA_SHORT.withBitAlignment(8);
        static final ValueLayout.OfChar JAVA_CHAR_UNALIGNED = JAVA_CHAR.withBitAlignment(8);
        static final ValueLayout.OfInt JAVA_INT_UNALIGNED = JAVA_INT.withBitAlignment(8);
        static final ValueLayout.OfLong JAVA_LONG_UNALIGNED = JAVA_LONG.withBitAlignment(8);
        static final ValueLayout.OfFloat JAVA_FLOAT_UNALIGNED = JAVA_FLOAT.withBitAlignment(8);
        static final ValueLayout.OfDouble JAVA_DOUBLE_UNALIGNED = JAVA_DOUBLE.withBitAlignment(8);
        static final ValueLayout.OfAddress ADDRESS_UNALIGNED = ADDRESS.withBitAlignment(8);
    }
}

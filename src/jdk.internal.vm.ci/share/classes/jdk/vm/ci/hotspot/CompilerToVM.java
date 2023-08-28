/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.vm.ci.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.Option;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Calls from Java into HotSpot. The behavior of all the methods in this class that take a native
 * pointer as an argument (e.g., {@link #getSymbol(long)}) is undefined if the argument does not
 * denote a valid native object.
 *
 * Note also that some calls pass a raw VM value to avoid a JNI upcall. For example,
 * {@link #getBytecode(HotSpotResolvedJavaMethodImpl, long)} needs the raw {@code Method*} value
 * (stored in {@link HotSpotResolvedJavaMethodImpl#methodHandle}) in the C++ implementation. The
 * {@link HotSpotResolvedJavaMethodImpl} wrapper is still passed as well as it may be the last
 * reference keeping the raw value alive.
 */
final class CompilerToVM {
    /**
     * Initializes the native part of the JVMCI runtime.
     */
    private static native void registerNatives();

    /**
     * These values mirror the equivalent values from {@code Unsafe} but are appropriate for the JVM
     * being compiled against.
     */
    final int ARRAY_BOOLEAN_BASE_OFFSET;
    final int ARRAY_BYTE_BASE_OFFSET;
    final int ARRAY_SHORT_BASE_OFFSET;
    final int ARRAY_CHAR_BASE_OFFSET;
    final int ARRAY_INT_BASE_OFFSET;
    final int ARRAY_LONG_BASE_OFFSET;
    final int ARRAY_FLOAT_BASE_OFFSET;
    final int ARRAY_DOUBLE_BASE_OFFSET;
    final int ARRAY_OBJECT_BASE_OFFSET;
    final int ARRAY_BOOLEAN_INDEX_SCALE;
    final int ARRAY_BYTE_INDEX_SCALE;
    final int ARRAY_SHORT_INDEX_SCALE;
    final int ARRAY_CHAR_INDEX_SCALE;
    final int ARRAY_INT_INDEX_SCALE;
    final int ARRAY_LONG_INDEX_SCALE;
    final int ARRAY_FLOAT_INDEX_SCALE;
    final int ARRAY_DOUBLE_INDEX_SCALE;
    final int ARRAY_OBJECT_INDEX_SCALE;

    @SuppressWarnings("try")
    CompilerToVM() {
        try (InitTimer t = timer("CompilerToVM.registerNatives")) {
            registerNatives();
            ARRAY_BOOLEAN_BASE_OFFSET = arrayBaseOffset(JavaKind.Boolean.getTypeChar());
            ARRAY_BYTE_BASE_OFFSET = arrayBaseOffset(JavaKind.Byte.getTypeChar());
            ARRAY_SHORT_BASE_OFFSET = arrayBaseOffset(JavaKind.Short.getTypeChar());
            ARRAY_CHAR_BASE_OFFSET = arrayBaseOffset(JavaKind.Char.getTypeChar());
            ARRAY_INT_BASE_OFFSET = arrayBaseOffset(JavaKind.Int.getTypeChar());
            ARRAY_LONG_BASE_OFFSET = arrayBaseOffset(JavaKind.Long.getTypeChar());
            ARRAY_FLOAT_BASE_OFFSET = arrayBaseOffset(JavaKind.Float.getTypeChar());
            ARRAY_DOUBLE_BASE_OFFSET = arrayBaseOffset(JavaKind.Double.getTypeChar());
            ARRAY_OBJECT_BASE_OFFSET = arrayBaseOffset(JavaKind.Object.getTypeChar());
            ARRAY_BOOLEAN_INDEX_SCALE = arrayIndexScale(JavaKind.Boolean.getTypeChar());
            ARRAY_BYTE_INDEX_SCALE = arrayIndexScale(JavaKind.Byte.getTypeChar());
            ARRAY_SHORT_INDEX_SCALE = arrayIndexScale(JavaKind.Short.getTypeChar());
            ARRAY_CHAR_INDEX_SCALE = arrayIndexScale(JavaKind.Char.getTypeChar());
            ARRAY_INT_INDEX_SCALE = arrayIndexScale(JavaKind.Int.getTypeChar());
            ARRAY_LONG_INDEX_SCALE = arrayIndexScale(JavaKind.Long.getTypeChar());
            ARRAY_FLOAT_INDEX_SCALE = arrayIndexScale(JavaKind.Float.getTypeChar());
            ARRAY_DOUBLE_INDEX_SCALE = arrayIndexScale(JavaKind.Double.getTypeChar());
            ARRAY_OBJECT_INDEX_SCALE = arrayIndexScale(JavaKind.Object.getTypeChar());
        }
    }

    native int arrayBaseOffset(char typeChar);

    native int arrayIndexScale(char typeChar);

    /**
     * Gets the {@link CompilerToVM} instance associated with the singleton
     * {@link HotSpotJVMCIRuntime} instance.
     */
    public static CompilerToVM compilerToVM() {
        return runtime().getCompilerToVM();
    }

    /**
     * Copies the original bytecode of {@code method} into a new byte array and returns it.
     *
     * @return a new byte array containing the original bytecode of {@code method}
     */
    byte[] getBytecode(HotSpotResolvedJavaMethodImpl method) {
        return getBytecode(method, method.getMethodPointer());
    }

    private native byte[] getBytecode(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Gets the number of entries in {@code method}'s exception handler table or 0 if it has no
     * exception handler table.
     */
    int getExceptionTableLength(HotSpotResolvedJavaMethodImpl method) {
        return getExceptionTableLength(method, method.getMethodPointer());
    }

    private native int getExceptionTableLength(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Gets the address of the first entry in {@code method}'s exception handler table.
     *
     * Each entry is a native object described by these fields:
     *
     * <ul>
     * <li>{@link HotSpotVMConfig#exceptionTableElementSize}</li>
     * <li>{@link HotSpotVMConfig#exceptionTableElementStartPcOffset}</li>
     * <li>{@link HotSpotVMConfig#exceptionTableElementEndPcOffset}</li>
     * <li>{@link HotSpotVMConfig#exceptionTableElementHandlerPcOffset}</li>
     * <li>{@link HotSpotVMConfig#exceptionTableElementCatchTypeIndexOffset}
     * </ul>
     *
     * @return 0 if {@code method} has no exception handlers (i.e.
     *         {@code getExceptionTableLength(method) == 0})
     */
    long getExceptionTableStart(HotSpotResolvedJavaMethodImpl method) {
        return getExceptionTableStart(method, method.getMethodPointer());
    }

    private native long getExceptionTableStart(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Determines whether {@code method} is currently compilable by the JVMCI compiler being used by
     * the VM. This can return false if JVMCI compilation failed earlier for {@code method}, a
     * breakpoint is currently set in {@code method} or {@code method} contains other bytecode
     * features that require special handling by the VM.
     */
    boolean isCompilable(HotSpotResolvedJavaMethodImpl method) {
        return isCompilable(method, method.getMethodPointer());
    }

    private native boolean isCompilable(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Determines if {@code method} is targeted by a VM directive (e.g.,
     * {@code -XX:CompileCommand=dontinline,<pattern>}) or annotation (e.g.,
     * {@code jdk.internal.vm.annotation.DontInline}) that specifies it should not be inlined.
     */
    boolean hasNeverInlineDirective(HotSpotResolvedJavaMethodImpl method) {
        return hasNeverInlineDirective(method, method.getMethodPointer());
    }

    private native boolean hasNeverInlineDirective(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Determines if {@code method} should be inlined at any cost. This could be because:
     * <ul>
     * <li>a CompileOracle directive may forces inlining of this methods</li>
     * <li>an annotation forces inlining of this method</li>
     * </ul>
     */
    boolean shouldInlineMethod(HotSpotResolvedJavaMethodImpl method) {
        return shouldInlineMethod(method, method.getMethodPointer());
    }

    private native boolean shouldInlineMethod(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Used to implement {@link ResolvedJavaType#findUniqueConcreteMethod(ResolvedJavaMethod)}.
     *
     * @param actualHolderType the best known type of receiver
     * @param method the method on which to base the search
     * @return the method result or 0 is there is no unique concrete method for {@code method}
     */
    HotSpotResolvedJavaMethodImpl findUniqueConcreteMethod(HotSpotResolvedObjectTypeImpl actualHolderType, HotSpotResolvedJavaMethodImpl method) {
        return findUniqueConcreteMethod(actualHolderType, actualHolderType.getKlassPointer(), method, method.getMetaspacePointer());
    }

    private native HotSpotResolvedJavaMethodImpl findUniqueConcreteMethod(HotSpotResolvedObjectTypeImpl klass, long klassPointer, HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Gets the implementor for the interface class {@code type}.
     *
     * @return the implementor if there is a single implementor, {@code null} if there is no
     *         implementor, or {@code type} itself if there is more than one implementor
     * @throws IllegalArgumentException if type is not an interface type
     */
    HotSpotResolvedObjectTypeImpl getImplementor(HotSpotResolvedObjectTypeImpl type) {
        return getImplementor(type, type.getKlassPointer());
    }

    private native HotSpotResolvedObjectTypeImpl getImplementor(HotSpotResolvedObjectTypeImpl type, long klassPointer);

    /**
     * Determines if {@code method} is ignored by security stack walks.
     */
    boolean methodIsIgnoredBySecurityStackWalk(HotSpotResolvedJavaMethodImpl method) {
        return methodIsIgnoredBySecurityStackWalk(method, method.getMetaspacePointer());
    }

    private native boolean methodIsIgnoredBySecurityStackWalk(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Converts a name to a type.
     *
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingClass the class loader of this class is used for resolution. Must not be null.
     * @param resolve force resolution to a {@link ResolvedJavaType}. If true, this method will
     *            either return a {@link ResolvedJavaType} or throw an exception
     * @return the type for {@code name} or {@code null} if resolution failed and {@code resolve == false}
     * @throws NoClassDefFoundError if {@code resolve == true} and the resolution failed
     */
    HotSpotResolvedJavaType lookupType(String name, HotSpotResolvedObjectTypeImpl accessingClass, boolean resolve) throws NoClassDefFoundError {
        return lookupType(name, accessingClass, accessingClass.getKlassPointer(), -1, resolve);
    }

    /**
     * Converts a name to a type.
     *
     * @param classLoader the class loader to use for resolution. Must not be {@code null},
     *           {@link ClassLoader#getPlatformClassLoader} or {@link ClassLoader#getSystemClassLoader}
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @return the type for {@code name}
     * @throws NoClassDefFoundError if resolution failed
     */
    HotSpotResolvedJavaType lookupType(ClassLoader classLoader, String name) throws NoClassDefFoundError {
        int accessingClassLoader;
        if (classLoader == null) {
            accessingClassLoader = 0;
        } else if (classLoader == ClassLoader.getPlatformClassLoader()) {
            accessingClassLoader = 1;
        } else if (classLoader == ClassLoader.getSystemClassLoader()) {
            accessingClassLoader = 2;
        } else {
            throw new IllegalArgumentException("Unsupported class loader for lookup: " + classLoader);
        }
        return lookupType(name, null, 0L, accessingClassLoader, true);
    }

    /**
     * @param accessingClassLoader ignored if {@code accessingKlassPointer != 0L}. Otherwise, the supported values are:
     *            0 - boot class loader
     *            1 - {@linkplain ClassLoader#getPlatformClassLoader() platform class loader}
     *            2 - {@linkplain ClassLoader#getSystemClassLoader() system class loader}
     */
    private native HotSpotResolvedJavaType lookupType(String name, HotSpotResolvedObjectTypeImpl accessingClass, long accessingKlassPointer, int accessingClassLoader, boolean resolve) throws NoClassDefFoundError;

    /**
     * Converts {@code javaClass} to a HotSpotResolvedJavaType.
     *
     * Must not be called if {@link Services#IS_IN_NATIVE_IMAGE} is {@code true}.
     */
    native HotSpotResolvedJavaType lookupClass(Class<?> javaClass);

    native HotSpotResolvedJavaType lookupJClass(long jclass);

    /**
     * Resolves the entry at index {@code cpi} in {@code constantPool} to an interned String object.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote an
     * {@code JVM_CONSTANT_String}.
     */
    JavaConstant getUncachedStringInPool(HotSpotConstantPool constantPool, int cpi) {
        return getUncachedStringInPool(constantPool, constantPool.getConstantPoolPointer(), cpi);
    }

    private native JavaConstant getUncachedStringInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int cpi);

    /**
     * Gets the entry at index {@code cpi} in {@code constantPool}, looking in the
     * constant pool cache first.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote one of the following
     * entry types: {@code JVM_CONSTANT_Dynamic}, {@code JVM_CONSTANT_String},
     * {@code JVM_CONSTANT_MethodHandle}, {@code JVM_CONSTANT_MethodHandleInError},
     * {@code JVM_CONSTANT_MethodType} and {@code JVM_CONSTANT_MethodTypeInError}.
     *
     * @param resolve specifies if a resolved entry is expected. If {@code false},
     *                {@code null} is returned for an unresolved entry.
     */
    JavaConstant lookupConstantInPool(HotSpotConstantPool constantPool, int cpi, boolean resolve) {
        return lookupConstantInPool(constantPool, constantPool.getConstantPoolPointer(), cpi, resolve);
    }

    private native JavaConstant lookupConstantInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int cpi, boolean resolve);

    /**
     * Gets the {@code JVM_CONSTANT_NameAndType} index referenced by the {@code rawIndex}.
     * The meaning of {@code rawIndex} is dependent on the given {@opcode}.
     *
     * The behavior of this method is undefined if the class holding the {@code constantPool}
     * has not yet been rewritten, or {@code rawIndex} is not a valid index for
     * this class for the given {@code opcode}
     */
    int lookupNameAndTypeRefIndexInPool(HotSpotConstantPool constantPool, int rawIndex, int opcode) {
        return lookupNameAndTypeRefIndexInPool(constantPool, constantPool.getConstantPoolPointer(), rawIndex, opcode);
    }

    private native int lookupNameAndTypeRefIndexInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int rawIndex, int opcode);

    /**
     * Gets the name of the {@code JVM_CONSTANT_NameAndType} entry in {@code constantPool}
     * referenced by the {@code rawIndex}. The meaning of {@code rawIndex} is dependent
     * on the given {@opcode}.
     *
     * The behavior of this method is undefined if the class holding the {@code constantPool}
     * has not yet been rewritten, or {@code rawIndex} is not a valid index for
     * this class for the given {@code opcode}
     */
    String lookupNameInPool(HotSpotConstantPool constantPool, int rawIndex, int opcode) {
        return lookupNameInPool(constantPool, constantPool.getConstantPoolPointer(), rawIndex, opcode);
    }

    private native String lookupNameInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int rawIndex, int opcode);

    /**
     * Gets the signature of the {@code JVM_CONSTANT_NameAndType} entry in {@code constantPool}
     * referenced by the {@code rawIndex}. The meaning of {@code rawIndex} is dependent
     * on the given {@opcode}.
     *
     * The behavior of this method is undefined if the class holding the {@code constantPool}
     * has not yet been rewritten, or {@code rawIndex} is not a valid index for
     * this class for the given {@code opcode}
     */
    String lookupSignatureInPool(HotSpotConstantPool constantPool, int rawIndex, int opcode) {
        return lookupSignatureInPool(constantPool, constantPool.getConstantPoolPointer(), rawIndex, opcode);
    }

    private native String lookupSignatureInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int rawIndex, int opcode);

    /**
     * Gets the {@code JVM_CONSTANT_Class} index from the entry in {@code constantPool}
     * referenced by the {@code rawIndex}. The meaning of {@code rawIndex} is dependent
     * on the given {@opcode}.
     *
     * The behavior of this method is undefined if the class holding the {@code constantPool}
     * has not yet been rewritten, or {@code rawIndex} is not a valid index for
     * this class for the given {@code opcode}
     */
    int lookupKlassRefIndexInPool(HotSpotConstantPool constantPool, int rawIndex, int opcode) {
        return lookupKlassRefIndexInPool(constantPool, constantPool.getConstantPoolPointer(), rawIndex, opcode);
    }

    private native int lookupKlassRefIndexInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int rawIndex, int opcode);

    /**
     * Looks up a class denoted by the {@code JVM_CONSTANT_Class} entry at index {@code cpi} in
     * {@code constantPool}. This method does not perform any resolution.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote a
     * {@code JVM_CONSTANT_Class} entry.
     *
     * @return the resolved class entry or a String otherwise
     */
    Object lookupKlassInPool(HotSpotConstantPool constantPool, int cpi) {
        return lookupKlassInPool(constantPool, constantPool.getConstantPoolPointer(), cpi);
    }

    private native Object lookupKlassInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int cpi);

    /**
     * Looks up a method denoted by the entry at index {@code cpi} in {@code constantPool}. This
     * method does not perform any resolution.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote an entry representing
     * a method.
     *
     * @param opcode the opcode of the instruction for which the lookup is being performed or
     *            {@code -1}. If non-negative, then resolution checks specific to the bytecode it
     *            denotes are performed if the method is already resolved. Should any of these
     *            checks fail, 0 is returned.
     * @param caller if non-null, do access checks in the context of {@code caller} calling the
     *            looked up method
     * @return the resolved method entry, 0 otherwise
     */
    HotSpotResolvedJavaMethodImpl lookupMethodInPool(HotSpotConstantPool constantPool, int cpi, byte opcode, HotSpotResolvedJavaMethodImpl caller) {
        long callerMethodPointer = caller == null ? 0L : caller.getMethodPointer();
        return lookupMethodInPool(constantPool, constantPool.getConstantPoolPointer(), cpi, opcode, caller, callerMethodPointer);
    }

    private native HotSpotResolvedJavaMethodImpl lookupMethodInPool(HotSpotConstantPool constantPool,
                    long constantPoolPointer,
                    int cpi,
                    byte opcode,
                    HotSpotResolvedJavaMethodImpl caller,
                    long callerMethodPointer);

    /**
     * Converts the encoded indy index operand of an invokedynamic instruction
     * to an index directly into {@code constantPool}.
     *
     * @param resolve if {@true}, then resolve the entry (which may call a bootstrap method)
     * @throws IllegalArgumentException if {@code encoded_indy_index} is not an encoded indy index
     * @return {@code JVM_CONSTANT_InvokeDynamic} constant pool entry index for the invokedynamic
     */
    int decodeIndyIndexToCPIndex(HotSpotConstantPool constantPool, int encoded_indy_index, boolean resolve) {
        return decodeIndyIndexToCPIndex(constantPool, constantPool.getConstantPoolPointer(), encoded_indy_index, resolve);
    }

    private native int decodeIndyIndexToCPIndex(HotSpotConstantPool constantPool, long constantPoolPointer, int encoded_indy_index, boolean resolve);

    /**
     * Converts the {@code rawIndex} operand of a rewritten getfield/putfield/getstatic/putstatic instruction
     * to an index directly into {@code constantPool}.
     *
     * @throws IllegalArgumentException if {@code rawIndex} is out of range.
     * @return {@code JVM_CONSTANT_FieldRef} constant pool entry index for the invokedynamic
     */
    int decodeFieldIndexToCPIndex(HotSpotConstantPool constantPool, int rawIndex) {
        return decodeFieldIndexToCPIndex(constantPool, constantPool.getConstantPoolPointer(), rawIndex);
    }

    private native int decodeFieldIndexToCPIndex(HotSpotConstantPool constantPool, long constantPoolPointer, int rawIndex);

    /**
     * Resolves the details for invoking the bootstrap method associated with the
     * {@code CONSTANT_Dynamic_info} or @{code CONSTANT_InvokeDynamic_info} entry at {@code cpi} in
     * {@code constant pool}.
     *
     * The return value encodes the details in an object array that is described by the pseudo Java
     * object {@code info} below:
     *
     * <pre>
     *     bsm_invocation = [
     *         ResolvedJavaMethod[] method,
     *         String name,
     *         Object type,             // JavaConstant: reference to Class (condy) or MethodType (indy)
     *         Object staticArguments,  // null: no static arguments
     *                                  // JavaConstant: single static argument
     *                                  // JavaConstant[]: multiple static arguments
     *                                  // int[]: static arguments to be resolved via BootstrapCallInfo
     *     ]
     * </pre>
     *
     * @return bootstrap method invocation details as encoded above
     */
    Object[] resolveBootstrapMethod(HotSpotConstantPool constantPool, int cpi) {
        return resolveBootstrapMethod(constantPool, constantPool.getConstantPoolPointer(), cpi);
    }

    private native Object[] resolveBootstrapMethod(HotSpotConstantPool constantPool, long constantPoolPointer, int cpi);

    /**
     * If {@code cpi} denotes an entry representing a signature polymorphic method ({@jvms 2.9}),
     * this method ensures that the type referenced by the entry is loaded and initialized. It
     * {@code cpi} does not denote a signature polymorphic method, this method does nothing.
     */
    void resolveInvokeHandleInPool(HotSpotConstantPool constantPool, int cpi) {
        resolveInvokeHandleInPool(constantPool, constantPool.getConstantPoolPointer(), cpi);
    }

    private native void resolveInvokeHandleInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int cpi);

    /**
     * If {@code cpi} denotes an entry representing a resolved dynamic adapter (see
     * {@link #decodeIndyIndexToCPIndex} and {@link #resolveInvokeHandleInPool}), return the
     * opcode of the instruction for which the resolution was performed ({@code invokedynamic} or
     * {@code invokevirtual}), or {@code -1} otherwise.
     */
    int isResolvedInvokeHandleInPool(HotSpotConstantPool constantPool, int cpi) {
        return isResolvedInvokeHandleInPool(constantPool, constantPool.getConstantPoolPointer(), cpi);
    }

    private native int isResolvedInvokeHandleInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int cpi);

    /**
     * Gets the list of type names (in the format of {@link JavaType#getName()}) denoting the
     * classes that define signature polymorphic methods.
     */
    native String[] getSignaturePolymorphicHolders();

    /**
     * Gets the resolved type denoted by the entry at index {@code cpi} in {@code constantPool}.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote an entry representing
     * a class.
     *
     * @throws LinkageError if resolution failed
     */
    HotSpotResolvedObjectTypeImpl resolveTypeInPool(HotSpotConstantPool constantPool, int cpi) throws LinkageError {
        return resolveTypeInPool(constantPool, constantPool.getConstantPoolPointer(), cpi);
    }

    private native HotSpotResolvedObjectTypeImpl resolveTypeInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int cpi) throws LinkageError;

    /**
     * Looks up and attempts to resolve the {@code JVM_CONSTANT_Field} entry denoted by
     * {@code rawIndex}. For some opcodes, checks are performed that require the
     * {@code method} that contains {@code opcode} to be specified. The values returned in
     * {@code info} are:
     *
     * <pre>
     *     [ aflags,  // fieldDescriptor::access_flags()
     *       offset,  // fieldDescriptor::offset()
     *       index,   // fieldDescriptor::index()
     *       fflags   // fieldDescriptor::field_flags()
     *     ]
     * </pre>
     *
     * The behavior of this method is undefined if {@code rawIndex} is invalid.
     *
     * @param info an array in which the details of the field are returned
     * @return the type defining the field if resolution is successful, null otherwise
     */
    HotSpotResolvedObjectTypeImpl resolveFieldInPool(HotSpotConstantPool constantPool, int rawIndex, HotSpotResolvedJavaMethodImpl method, byte opcode, int[] info) {
        long methodPointer = method != null ? method.getMethodPointer() : 0L;
        return resolveFieldInPool(constantPool, constantPool.getConstantPoolPointer(), rawIndex, method, methodPointer, opcode, info);
    }

    private native HotSpotResolvedObjectTypeImpl resolveFieldInPool(HotSpotConstantPool constantPool, long constantPoolPointer,
                    int rawIndex, HotSpotResolvedJavaMethodImpl method, long methodPointer, byte opcode, int[] info);

    /**
     * Converts {@code cpci} from an index into the cache for {@code constantPool} to an index
     * directly into {@code constantPool}.
     *
     * The behavior of this method is undefined if {@code cpci} is an invalid constant pool cache
     * index.
     */
    int constantPoolRemapInstructionOperandFromCache(HotSpotConstantPool constantPool, int cpci) {
        return constantPoolRemapInstructionOperandFromCache(constantPool, constantPool.getConstantPoolPointer(), cpci);
    }

    private native int constantPoolRemapInstructionOperandFromCache(HotSpotConstantPool constantPool, long constantPoolPointer, int cpci);

    /**
     * Gets the appendix object (if any) associated with the entry identified by {@code which}.
     *
     * @param which if negative, is treated as an encoded indy index for INVOKEDYNAMIC;
     *              Otherwise, it's treated as a constant pool cache index (returned by HotSpotConstantPool::rawIndexToConstantPoolCacheIndex)
     *              for INVOKE{VIRTUAL,SPECIAL,STATIC,INTERFACE}.
     */
    HotSpotObjectConstantImpl lookupAppendixInPool(HotSpotConstantPool constantPool, int which) {
        return lookupAppendixInPool(constantPool, constantPool.getConstantPoolPointer(), which);
    }

    private native HotSpotObjectConstantImpl lookupAppendixInPool(HotSpotConstantPool constantPool, long constantPoolPointer, int which);

    /**
     * Installs the result of a compilation into the code cache.
     *
     * @param compiledCode the result of a compilation
     * @param code the details of the installed CodeBlob are written to this object
     *
     * @return the outcome of the installation which will be one of
     *         {@link HotSpotVMConfig#codeInstallResultOk},
     *         {@link HotSpotVMConfig#codeInstallResultCacheFull},
     *         {@link HotSpotVMConfig#codeInstallResultCodeTooLarge} or
     *         {@link HotSpotVMConfig#codeInstallResultDependenciesFailed}.
     * @throws JVMCIError if there is something wrong with the compiled code or the associated
     *             metadata.
     */
    int installCode(HotSpotCompiledCode compiledCode, InstalledCode code, long failedSpeculationsAddress, byte[] speculations) {
        int codeInstallFlags = getInstallCodeFlags();
        boolean withComments = (codeInstallFlags & 0x0001) != 0;
        boolean withMethods = (codeInstallFlags & 0x0002) != 0;
        boolean withTypeInfo;
        if ((codeInstallFlags & 0x0004) != 0 && HotSpotJVMCIRuntime.Option.CodeSerializationTypeInfo.isDefault) {
            withTypeInfo = true;
        } else {
            withTypeInfo = HotSpotJVMCIRuntime.Option.CodeSerializationTypeInfo.getBoolean();
        }
        try (HotSpotCompiledCodeStream stream = new HotSpotCompiledCodeStream(compiledCode, withTypeInfo, withComments, withMethods)) {
            return installCode0(stream.headChunk, stream.timeNS, withTypeInfo, compiledCode, stream.objectPool, code, failedSpeculationsAddress, speculations);
        }
    }

    native int installCode0(long compiledCodeBuffer,
                    long serializationNS,
                    boolean withTypeInfo,
                    HotSpotCompiledCode compiledCode,
                    Object[] objectPool,
                    InstalledCode code,
                    long failedSpeculationsAddress,
                    byte[] speculations);

    /**
     * Gets flags specifying optional parts of code info. Only if a flag is set, will the
     * corresponding code info being included in the {@linkplain HotSpotCompiledCodeStream
     * serialized code stream}.
     *
     * <ul>
     * <li>0x0001: code comments ({@link HotSpotCompiledCode#comments})</li>
     * <li>0x0002: methods ({@link HotSpotCompiledCode#methods})</li>
     * <li>0x0004: enable {@link Option#CodeSerializationTypeInfo} if it not explicitly specified
     * (i.e., {@link Option#isDefault} is {@code true})</li>
     * </ul>
     */
    private native int getInstallCodeFlags();

    /**
     * Resets all compilation statistics.
     */
    native void resetCompilationStatistics();

    /**
     * Reads the database of VM info. The return value encodes the info in a nested object array
     * that is described by the pseudo Java object {@code info} below:
     *
     * <pre>
     *     info = [
     *         VMField[] vmFields,
     *         [String name, Long size, ...] vmTypeSizes,
     *         [String name, Long value, ...] vmConstants,
     *         [String name, Long value, ...] vmAddresses,
     *         VMFlag[] vmFlags
     *         VMIntrinsicMethod[] vmIntrinsics
     *     ]
     * </pre>
     *
     * @return VM info as encoded above
     */
    native Object[] readConfiguration();

    /**
     * Resolves the implementation of {@code method} for virtual dispatches on objects of dynamic
     * type {@code exactReceiver}. This resolution process only searches "up" the class hierarchy of
     * {@code exactReceiver}.
     *
     * @param exactReceiver the exact receiver type
     * @param caller the caller or context type used to perform access checks
     * @return the link-time resolved method (might be abstract) or {@code null} if it is either a
     *         signature polymorphic method or can not be linked.
     */
    HotSpotResolvedJavaMethodImpl resolveMethod(HotSpotResolvedObjectTypeImpl exactReceiver, HotSpotResolvedJavaMethodImpl method, HotSpotResolvedObjectTypeImpl caller) {
        return resolveMethod(exactReceiver, exactReceiver.getKlassPointer(), method, method.getMethodPointer(), caller, caller.getKlassPointer());
    }

    private native HotSpotResolvedJavaMethodImpl resolveMethod(HotSpotResolvedObjectTypeImpl exactReceiver, long exactReceiverKlass,
                    HotSpotResolvedJavaMethodImpl method, long methodPointer,
                    HotSpotResolvedObjectTypeImpl caller, long callerKlass);

    /**
     * Gets the static initializer of {@code type}.
     *
     * @return {@code null} if {@code type} has no static initializer
     */
    HotSpotResolvedJavaMethodImpl getClassInitializer(HotSpotResolvedObjectTypeImpl type) {
        return getClassInitializer(type, type.getKlassPointer());
    }

    private native HotSpotResolvedJavaMethodImpl getClassInitializer(HotSpotResolvedObjectTypeImpl type, long klassPointer);

    /**
     * Determines if {@code type} or any of its currently loaded subclasses overrides
     * {@code Object.finalize()}.
     */
    boolean hasFinalizableSubclass(HotSpotResolvedObjectTypeImpl type) {
        return hasFinalizableSubclass(type, type.getKlassPointer());
    }

    private native boolean hasFinalizableSubclass(HotSpotResolvedObjectTypeImpl type, long klassPointer);

    /**
     * Gets the method corresponding to {@code executable}.
     */
    native HotSpotResolvedJavaMethodImpl asResolvedJavaMethod(Executable executable);

    /**
     * Gets the maximum absolute offset of a PC relative call to {@code address} from any position
     * in the code cache.
     *
     * @param address an address that may be called from any code in the code cache
     * @return -1 if {@code address == 0}
     */
    native long getMaxCallTargetOffset(long address);

    /**
     * Gets a textual disassembly of {@code codeBlob}.
     *
     * @return a non-zero length string containing a disassembly of {@code codeBlob} or null if
     *         {@code codeBlob} could not be disassembled for some reason
     */
    // The HotSpot disassembler seems not to be thread safe so it's better to synchronize its usage
    synchronized native String disassembleCodeBlob(InstalledCode installedCode);

    /**
     * Gets a stack trace element for {@code method} at bytecode index {@code bci}.
     */
    StackTraceElement getStackTraceElement(HotSpotResolvedJavaMethodImpl method, int bci) {
        return getStackTraceElement(method, method.getMethodPointer(), bci);
    }

    private native StackTraceElement getStackTraceElement(HotSpotResolvedJavaMethodImpl method, long methodPointer, int bci);

    /**
     * Executes some {@code installedCode} with arguments {@code args}.
     *
     * @return the result of executing {@code nmethodMirror}
     * @throws InvalidInstalledCodeException if {@code nmethodMirror} has been invalidated
     */
    native Object executeHotSpotNmethod(Object[] args, HotSpotNmethod nmethodMirror) throws InvalidInstalledCodeException;

    /**
     * Gets the line number table for {@code method}. The line number table is encoded as (bci,
     * source line number) pairs.
     *
     * @return the line number table for {@code method} or null if it doesn't have one
     */
    long[] getLineNumberTable(HotSpotResolvedJavaMethodImpl method) {
        return getLineNumberTable(method, method.getMethodPointer());
    }

    private native long[] getLineNumberTable(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Gets the number of entries in the local variable table for {@code method}.
     *
     * @return the number of entries in the local variable table for {@code method}
     */
    int getLocalVariableTableLength(HotSpotResolvedJavaMethodImpl method) {
        return getLocalVariableTableLength(method, method.getMethodPointer());
    }

    private native int getLocalVariableTableLength(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Gets the address of the first entry in the local variable table for {@code method}.
     *
     * Each entry is a native object described by these fields:
     *
     * <ul>
     * <li>{@link HotSpotVMConfig#localVariableTableElementSize}</li>
     * <li>{@link HotSpotVMConfig#localVariableTableElementLengthOffset}</li>
     * <li>{@link HotSpotVMConfig#localVariableTableElementNameCpIndexOffset}</li>
     * <li>{@link HotSpotVMConfig#localVariableTableElementDescriptorCpIndexOffset}</li>
     * <li>{@link HotSpotVMConfig#localVariableTableElementSlotOffset}
     * <li>{@link HotSpotVMConfig#localVariableTableElementStartBciOffset}
     * </ul>
     *
     * @return 0 if {@code method} does not have a local variable table
     */
    long getLocalVariableTableStart(HotSpotResolvedJavaMethodImpl method) {
        return getLocalVariableTableStart(method, method.getMetaspacePointer());
    }

    private native long getLocalVariableTableStart(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Sets flags on {@code method} indicating that it should never be inlined or compiled by the
     * VM.
     */
    void setNotInlinableOrCompilable(HotSpotResolvedJavaMethodImpl method) {
        setNotInlinableOrCompilable(method, method.getMethodPointer());
    }

    private native void setNotInlinableOrCompilable(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Invalidates the profiling information for {@code method} and (re)initializes it such that
     * profiling restarts upon its next invocation.
     */
    void reprofile(HotSpotResolvedJavaMethodImpl method) {
        reprofile(method, method.getMethodPointer());
    }

    private native void reprofile(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Updates {@code nmethodMirror} such that {@link InvalidInstalledCodeException} will be raised
     * the next time {@code nmethodMirror} is {@linkplain #executeHotSpotNmethod executed}. The
     * {@code nmethod} associated with {@code nmethodMirror} is also made non-entrant and if
     * {@code deoptimize == true} any current activations of the {@code nmethod} are deoptimized.
     */
    native void invalidateHotSpotNmethod(HotSpotNmethod nmethodMirror, boolean deoptimize);

    /**
     * Collects the current values of all JVMCI benchmark counters, summed up over all threads.
     */
    native long[] collectCounters();

    /**
     * Get the current number of counters allocated for use by JVMCI. Should be the same value as
     * the flag {@code JVMCICounterSize}.
     */
    native int getCountersSize();

    /**
     * Attempt to change the size of the counters allocated for JVMCI. This requires a safepoint to
     * safely reallocate the storage but it's advisable to increase the size in reasonable chunks.
     */
    native boolean setCountersSize(int newSize);

    /**
     * Determines if {@code methodData} is mature.
     *
     * @param methodData a {@code MethodData*} value
     */
    native boolean isMature(long methodData);

    /**
     * Generate a unique id to identify the result of the compile.
     */
    int allocateCompileId(HotSpotResolvedJavaMethodImpl method, int entryBCI) {
        return allocateCompileId(method, method.getMethodPointer(), entryBCI);
    }

    private native int allocateCompileId(HotSpotResolvedJavaMethodImpl method, long methodPointer, int entryBCI);

    /**
     * Determines if {@code method} has OSR compiled code identified by {@code entryBCI} for
     * compilation level {@code level}.
     */
    boolean hasCompiledCodeForOSR(HotSpotResolvedJavaMethodImpl method, int entryBCI, int level) {
        return hasCompiledCodeForOSR(method, method.getMethodPointer(), entryBCI, level);
    }

    private native boolean hasCompiledCodeForOSR(HotSpotResolvedJavaMethodImpl method, long methodPoiner, int entryBCI, int level);

    /**
     * Gets the value of {@code symbol} as a String.
     *
     * @param symbol a {@code Symbol*} value
     */
    native String getSymbol(long symbol);

    /**
     * Gets the name for a {@code klass} as it would appear in a signature.
     *
     * @param klass a {@code Klass*} value
     */
    native String getSignatureName(long klass);

    /**
     * @see jdk.vm.ci.code.stack.StackIntrospection#iterateFrames
     */
    native <T> T iterateFrames(ResolvedJavaMethod[] initialMethods, ResolvedJavaMethod[] matchingMethods, int initialSkip, InspectedFrameVisitor<T> visitor);

    /**
     * Materializes all virtual objects within {@code stackFrame} and updates its locals.
     *
     * @param invalidate if {@code true}, the compiled method for the stack frame will be
     *            invalidated
     */
    native void materializeVirtualObjects(HotSpotStackFrameReference stackFrame, boolean invalidate);

    /**
     * Gets the v-table index for interface method {@code method} in the receiver {@code type} or
     * {@link HotSpotVMConfig#invalidVtableIndex} if {@code method} is not in {@code type}'s
     * v-table.
     *
     * @throws InternalError if {@code type} is an interface, {@code method} is not defined by an
     *             interface, {@code type} does not implement the interface defining {@code method}
     *             or class represented by {@code type} is not initialized
     */
    int getVtableIndexForInterfaceMethod(HotSpotResolvedObjectTypeImpl type, HotSpotResolvedJavaMethodImpl method) {
        return getVtableIndexForInterfaceMethod(type, type.getKlassPointer(), method, method.getMethodPointer());
    }

    private native int getVtableIndexForInterfaceMethod(HotSpotResolvedObjectTypeImpl type, long klassPointer, HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Determines if debug info should also be emitted at non-safepoint locations.
     */
    native boolean shouldDebugNonSafepoints();

    /**
     * Writes {@code length} bytes from {@code buffer} to HotSpot's log stream.
     *
     * @param buffer if {@code length <= 8}, then the bytes are encoded in this value in native
     *            endianness order otherwise this is the address of a native memory buffer holding
     *            the bytes
     * @param flush specifies if the log stream should be flushed after writing
     */
    native void writeDebugOutput(long buffer, int length, boolean flush);

    /**
     * Flush HotSpot's log stream.
     */
    native void flushDebugOutput();

    /**
     * Read a HotSpot {@code Method*} value from the memory location described by {@code base} plus
     * {@code displacement} and return the {@link HotSpotResolvedJavaMethodImpl} wrapping it. This
     * method does no checking that the memory location actually contains a valid pointer and may
     * crash the VM if an invalid location is provided. If {@code base == null} is null then
     * {@code displacement} is used by itself. If {@code base} is a
     * {@link HotSpotResolvedJavaMethodImpl}, {@link HotSpotConstantPool} or
     * {@link HotSpotResolvedObjectTypeImpl} then the metaspace pointer is fetched from that object
     * and added to {@code displacement}. Any other non-null object type causes an
     * {@link IllegalArgumentException} to be thrown.
     *
     * @param base an object to read from or null
     * @param displacement
     * @return null or the resolved method for this location
     */
    native HotSpotResolvedJavaMethodImpl getResolvedJavaMethod(HotSpotObjectConstantImpl base, long displacement);

    /**
     * Gets the {@code ConstantPool*} associated with {@code object} and returns a
     * {@link HotSpotConstantPool} wrapping it.
     *
     * @param object a {@link HotSpotResolvedJavaMethodImpl} or
     *            {@link HotSpotResolvedObjectTypeImpl} object
     * @return a {@link HotSpotConstantPool} wrapping the {@code ConstantPool*} associated with
     *         {@code object}
     * @throws NullPointerException if {@code object == null}
     * @throws IllegalArgumentException if {@code object} is neither a
     *             {@link HotSpotResolvedJavaMethodImpl} nor a {@link HotSpotResolvedObjectTypeImpl}
     */
    HotSpotConstantPool getConstantPool(MetaspaceObject object) {
        return getConstantPool(object, object.getMetaspacePointer(), object instanceof HotSpotResolvedJavaType);
    }

    native HotSpotConstantPool getConstantPool(Object object, long klassOrMethod, boolean isKlass);

    /**
     * Read a {@code Klass*} value from the memory location described by {@code base} plus
     * {@code displacement} and return the {@link HotSpotResolvedObjectTypeImpl} wrapping it. This method
     * only performs the read if the memory location is known to contain a valid Klass*.  If
     * {@code base} is a {@link HotSpotConstantPool}, {@link HotSpotMethodData}, {@link HotSpotObjectConstantImpl},
     * or {@link HotSpotResolvedObjectTypeImpl} then the field
     * corresopnding to {@code displacement} is fetched using the appropriate HotSpot accessor. Any
     * other object type or an unexpected displacement causes an {@link IllegalArgumentException} to
     * be thrown.  The set of fields which can be read in this fashion corresponds to the {@link VMField}
     * with type {@code Klass*} that are described in the {@link HotSpotVMConfigStore#getFields()}.
     * Additionally several injected fields in {@link Class} are also handled.
     *
     * @param base an object to read from
     * @param displacement
     * @param compressed true if the location contains a compressed Klass*
     * @return null or the resolved method for this location
     * @throws NullPointerException if {@code base == null}
     */
    private native HotSpotResolvedObjectTypeImpl getResolvedJavaType0(Object base, long displacement, boolean compressed);

    HotSpotResolvedObjectTypeImpl getResolvedJavaType(HotSpotConstantPool base, long displacement) {
        return getResolvedJavaType0(base, displacement, false);
    }

    HotSpotResolvedObjectTypeImpl getResolvedJavaType(HotSpotMethodData base, long displacement) {
        return getResolvedJavaType0(base, displacement, false);
    }

    HotSpotResolvedObjectTypeImpl getResolvedJavaType(HotSpotResolvedObjectTypeImpl base, long displacement, boolean compressed) {
        return getResolvedJavaType0(base, displacement, compressed);
    }

    HotSpotResolvedObjectTypeImpl getResolvedJavaType(HotSpotObjectConstantImpl base, long displacement, boolean compressed) {
        return getResolvedJavaType0(base, displacement, compressed);
    }

    /**
     * Reads a {@code Klass*} from {@code address} (i.e., {@code address} is a {@code Klass**}
     * value) and wraps it in a {@link HotSpotResolvedObjectTypeImpl}. This VM call must be used for
     * any {@code Klass*} value not known to be already wrapped in a
     * {@link HotSpotResolvedObjectTypeImpl}. The VM call is necessary so that the {@code Klass*} is
     * wrapped in a {@code JVMCIKlassHandle} to protect it from the concurrent scanning done by G1.
     */
    HotSpotResolvedObjectTypeImpl getResolvedJavaType(long address) {
        return getResolvedJavaType0(null, address, false);
    }

    /**
     * Return the size of the HotSpot ProfileData* pointed at by {@code position}. If
     * {@code position} is outside the space of the MethodData then an
     * {@link IllegalArgumentException} is thrown. A {@code position} inside the MethodData but that
     * isn't pointing at a valid ProfileData will crash the VM.
     *
     * @param metaspaceMethodData
     * @param position
     * @return the size of the ProfileData item pointed at by {@code position}
     * @throws IllegalArgumentException if an out of range position is given
     */
    native int methodDataProfileDataSize(long metaspaceMethodData, int position);


    native int methodDataExceptionSeen(long metaspaceMethodData, int bci);

    /**
     * Return the amount of native stack required for the interpreter frames represented by
     * {@code frame}. This is used when emitting the stack banging code to ensure that there is
     * enough space for the frames during deoptimization.
     *
     * @param frame
     * @return the number of bytes required for deoptimization of this frame state
     */
    native int interpreterFrameSize(BytecodeFrame frame);

    /**
     * Invokes non-public method {@code java.lang.invoke.LambdaForm.compileToBytecode()} on
     * {@code lambdaForm} (which must be a {@code java.lang.invoke.LambdaForm} instance).
     */
    native void compileToBytecode(HotSpotObjectConstantImpl lambdaForm);

    /**
     * Gets the value of the VM flag named {@code name}.
     *
     * @param name name of a VM option
     * @return {@code this} if the named VM option doesn't exist, a {@link String} or {@code null}
     *         if its type is {@code ccstr} or {@code ccstrlist}, a {@link Double} if its type is
     *         {@code double}, a {@link Boolean} if its type is {@code bool} otherwise a
     *         {@link Long}
     */
    native Object getFlagValue(String name);

    /**
     * @see ResolvedJavaType#getInterfaces()
     */
    HotSpotResolvedObjectTypeImpl[] getInterfaces(HotSpotResolvedObjectTypeImpl klass) {
        return getInterfaces(klass, klass.getKlassPointer());
    }

    native HotSpotResolvedObjectTypeImpl[] getInterfaces(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * @see ResolvedJavaType#getComponentType()
     */
    HotSpotResolvedJavaType getComponentType(HotSpotResolvedObjectTypeImpl klass) {
        return getComponentType(klass, klass.getKlassPointer());
    }

    native HotSpotResolvedJavaType getComponentType(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * Get the array class for the primitive type represented by the {@link JavaKind#getTypeChar()}
     * value in {@code typeChar} or the non-primitive type represented by {@code nonPrimitiveKlass}.
     * This can't be done symbolically since hidden classes can't be looked up by name.
     *
     * Exactly one of {@code primitiveTypeChar} or {@code nonPrimitiveKlass} must be non-zero.
     *
     * @param primitiveTypeChar a {@link JavaKind#getTypeChar()} value for a primitive type
     * @param nonPrimitiveKlass a non-primitive type
     */
    HotSpotResolvedObjectTypeImpl getArrayType(char primitiveTypeChar, HotSpotResolvedObjectTypeImpl nonPrimitiveKlass) {
        long nonPrimitiveKlassPointer = nonPrimitiveKlass != null ? nonPrimitiveKlass.getKlassPointer() : 0L;
        return getArrayType(primitiveTypeChar, nonPrimitiveKlass, nonPrimitiveKlassPointer);
    }

    native HotSpotResolvedObjectTypeImpl getArrayType(char typeChar, HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * Forces initialization of {@code klass}.
     */
    void ensureInitialized(HotSpotResolvedObjectTypeImpl klass) {
        ensureInitialized(klass, klass.getKlassPointer());
    }

    native void ensureInitialized(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * Forces linking of {@code klass}.
     */
    void ensureLinked(HotSpotResolvedObjectTypeImpl klass) {
        ensureLinked(klass, klass.getKlassPointer());
    }

    native void ensureLinked(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * Checks if {@code object} is a String and is an interned string value.
     */
    native boolean isInternedString(HotSpotObjectConstantImpl object);

    /**
     * Gets the {@linkplain System#identityHashCode(Object) identity} has code for the object
     * represented by this constant.
     */
    native int getIdentityHashCode(HotSpotObjectConstantImpl object);

    /**
     * Converts a constant object representing a boxed primitive into a boxed primitive.
     */
    native Object unboxPrimitive(HotSpotObjectConstantImpl object);

    /**
     * Converts a boxed primitive into a JavaConstant representing the same value.
     */
    native HotSpotObjectConstantImpl boxPrimitive(Object source);

    /**
     * Gets the {@link ResolvedJavaMethod}s for all the constructors of {@code klass}.
     */
    ResolvedJavaMethod[] getDeclaredConstructors(HotSpotResolvedObjectTypeImpl klass) {
        return getDeclaredConstructors(klass, klass.getKlassPointer());
    }

    native ResolvedJavaMethod[] getDeclaredConstructors(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * Gets the {@link ResolvedJavaMethod}s for all the non-constructor methods of {@code klass}.
     */
    ResolvedJavaMethod[] getDeclaredMethods(HotSpotResolvedObjectTypeImpl klass) {
        return getDeclaredMethods(klass, klass.getKlassPointer());
    }

    native ResolvedJavaMethod[] getDeclaredMethods(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    HotSpotResolvedObjectTypeImpl.FieldInfo[] getDeclaredFieldsInfo(HotSpotResolvedObjectTypeImpl klass) {
        return getDeclaredFieldsInfo(klass, klass.getKlassPointer());
    }

    native HotSpotResolvedObjectTypeImpl.FieldInfo[] getDeclaredFieldsInfo(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * Reads the current value of a static field of {@code declaringKlass}. Extra sanity checking is
     * performed on the offset and kind of the read being performed.
     *
     * @param declaringKlass the type in which the static field is declared
     * @param offset the offset of the field in the {@link Class} mirror of {@code declaringKlass}
     * @throws IllegalArgumentException if any of the sanity checks fail
     */
    JavaConstant readStaticFieldValue(HotSpotResolvedObjectTypeImpl declaringKlass, long offset, char typeChar) {
        return readStaticFieldValue(declaringKlass, declaringKlass.getKlassPointer(), offset, typeChar);
    }

    native JavaConstant readStaticFieldValue(HotSpotResolvedObjectTypeImpl declaringKlass, long declaringKlassPointer, long offset, char typeChar);

    /**
     * Reads the current value of an instance field. If {@code expectedType} is non-null, then
     * {@code object} is expected to be a subtype of {@code expectedType}. Extra sanity checking is
     * performed on the offset and kind of the read being performed.
     *
     * @param object the object from which the field is to be read. If {@code object} is of type
     *            {@link Class} and {@code offset} is >= the offset of the static field storage in a
     *            {@link Class} instance, then this operation is a static field read.
     * @param expectedType the expected type of {@code object}
     * @throws IllegalArgumentException if any of the sanity checks fail
     */
    JavaConstant readFieldValue(HotSpotObjectConstantImpl object, HotSpotResolvedObjectTypeImpl expectedType, long offset, char typeChar) {
        long expectedTypePointer = expectedType != null ? expectedType.getKlassPointer() : 0L;
        return readFieldValue(object, expectedType, expectedTypePointer, offset, typeChar);
    }

    native JavaConstant readFieldValue(HotSpotObjectConstantImpl object, HotSpotResolvedObjectTypeImpl expectedType, long expectedTypePointer, long offset, char typeChar);

    /**
     * @see ResolvedJavaType#isInstance(JavaConstant)
     */
    boolean isInstance(HotSpotResolvedObjectTypeImpl klass, HotSpotObjectConstantImpl object) {
        return isInstance(klass, klass.getKlassPointer(), object);
    }

    native boolean isInstance(HotSpotResolvedObjectTypeImpl klass, long klassPointer, HotSpotObjectConstantImpl object);

    /**
     * @see ResolvedJavaType#isAssignableFrom(ResolvedJavaType)
     */
    boolean isAssignableFrom(HotSpotResolvedObjectTypeImpl klass, HotSpotResolvedObjectTypeImpl subklass) {
        return isAssignableFrom(klass, klass.getKlassPointer(), subklass, subklass.getKlassPointer());
    }

    native boolean isAssignableFrom(HotSpotResolvedObjectTypeImpl klass, long klassPointer, HotSpotResolvedObjectTypeImpl subklass, long subklassPointer);

    /**
     * @see ConstantReflectionProvider#asJavaType(Constant)
     */
    native HotSpotResolvedJavaType asJavaType(HotSpotObjectConstantImpl object);

    /**
     * Converts a String constant into a String.
     */
    native String asString(HotSpotObjectConstantImpl object);

    /**
     * Compares the contents of {@code xHandle} and {@code yHandle} for pointer equality.
     */
    native boolean equals(HotSpotObjectConstantImpl x, long xHandle, HotSpotObjectConstantImpl y, long yHandle);

    /**
     * Gets a {@link JavaConstant} wrapping the {@link java.lang.Class} mirror for {@code klass}.
     */
    HotSpotObjectConstantImpl getJavaMirror(HotSpotResolvedObjectTypeImpl klass) {
        return getJavaMirror(klass, klass.getKlassPointer());
    }

    native HotSpotObjectConstantImpl getJavaMirror(HotSpotResolvedObjectTypeImpl type, long klassPointer);

    /**
     * Returns the length of the array if {@code object} represents an array or -1 otherwise.
     */
    native int getArrayLength(HotSpotObjectConstantImpl object);

    /**
     * Reads the element at {@code index} if {@code object} is an array. Elements of an object array
     * are returned as {@link JavaConstant}s and primitives are returned as boxed values. The value
     * {@code null} is returned if the {@code index} is out of range or object is not an array.
     */
    native Object readArrayElement(HotSpotObjectConstantImpl object, int index);

    /**
     * @see HotSpotJVMCIRuntime#registerNativeMethods
     */
    native long[] registerNativeMethods(Class<?> clazz);

    /**
     * @see HotSpotJVMCIRuntime#translate(Object)
     */
    native long translate(Object obj, boolean callPostTranslation);

    /**
     * @see HotSpotJVMCIRuntime#unhand(Class, long)
     */
    native Object unhand(long handle);

    /**
     * Updates {@code address} and {@code entryPoint} fields of {@code nmethodMirror} based on the
     * current state of the {@code nmethod} identified by {@code address} and
     * {@code nmethodMirror.compileId} in the code cache.
     */
    native void updateHotSpotNmethod(HotSpotNmethod nmethodMirror);

    /**
     * @see InstalledCode#getCode()
     */
    native byte[] getCode(HotSpotInstalledCode code);

    /**
     * Gets a {@link Executable} corresponding to {@code method}.
     */
    Executable asReflectionExecutable(HotSpotResolvedJavaMethodImpl method) {
        return asReflectionExecutable(method, method.getMethodPointer());
    }

    native Executable asReflectionExecutable(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Gets a {@link Field} denoted by {@code holder} and {@code index}.
     *
     * @param holder the class in which the requested field is declared
     * @param fieldIndex the {@code fieldDescriptor::index()} denoting the field
     */
    Field asReflectionField(HotSpotResolvedObjectTypeImpl holder, int fieldIndex) {
        return asReflectionField(holder, holder.getKlassPointer(), fieldIndex);
    }

    native Field asReflectionField(HotSpotResolvedObjectTypeImpl holder, long holderPointer, int fieldIndex);

    /**
     * @see HotSpotJVMCIRuntime#getIntrinsificationTrustPredicate(Class...)
     */
    boolean isTrustedForIntrinsics(HotSpotResolvedObjectTypeImpl klass) {
        return isTrustedForIntrinsics(klass, klass.getKlassPointer());
    }

    native boolean isTrustedForIntrinsics(HotSpotResolvedObjectTypeImpl klass, long klassPointer);

    /**
     * Releases all oop handles whose referent is null.
     */
    native void releaseClearedOopHandles();

    /**
     * Gets the failed speculations pointed to by {@code *failedSpeculationsAddress}.
     *
     * @param currentFailures the known failures at {@code failedSpeculationsAddress}
     * @return the list of failed speculations with each entry being a single speculation in the
     *         format emitted by {@link HotSpotSpeculationEncoding#toByteArray()}
     */
    native byte[][] getFailedSpeculations(long failedSpeculationsAddress, byte[][] currentFailures);

    /**
     * Gets the address of the {@code MethodData::_failed_speculations} field in the
     * {@code MethodData} associated with {@code method}. This will create and install the
     * {@code MethodData} if it didn't already exist.
     */
    long getFailedSpeculationsAddress(HotSpotResolvedJavaMethodImpl method) {
        return getFailedSpeculationsAddress(method, method.getMethodPointer());
    }

    native long getFailedSpeculationsAddress(HotSpotResolvedJavaMethodImpl method, long methodPointer);

    /**
     * Frees the failed speculations pointed to by {@code *failedSpeculationsAddress}.
     */
    native void releaseFailedSpeculations(long failedSpeculationsAddress);

    /**
     * Adds a speculation to the failed speculations pointed to by
     * {@code *failedSpeculationsAddress}.
     *
     * @return {@code false} if the speculation could not be appended to the list
     */
    native boolean addFailedSpeculation(long failedSpeculationsAddress, byte[] speculation);

    /**
     * @see HotSpotJVMCIRuntime#isCurrentThreadAttached()
     */
    native boolean isCurrentThreadAttached();

    /**
     * @see HotSpotJVMCIRuntime#getCurrentJavaThread()
     */
    native long getCurrentJavaThread();

    /**
     * @param name name of current thread if in a native image otherwise {@code null}
     * @see HotSpotJVMCIRuntime#attachCurrentThread
     */
    native boolean attachCurrentThread(byte[] name, boolean asDaemon, long[] javaVMInfo);

    /**
     * @see HotSpotJVMCIRuntime#detachCurrentThread
     */
    native boolean detachCurrentThread(boolean release);

    /**
     * @see HotSpotJVMCIRuntime#exitHotSpot(int)
     */
    native void callSystemExit(int status);

    /**
     * @see JFR.Ticks#now
     */
    native long ticksNow();

    /**
     * @see HotSpotJVMCIRuntime#setThreadLocalObject(int, Object)
     */
    native void setThreadLocalObject(int id, Object value);

    /**
     * @see HotSpotJVMCIRuntime#getThreadLocalObject(int)
     */
    native Object getThreadLocalObject(int id);

    /**
     * @see HotSpotJVMCIRuntime#setThreadLocalLong(int, long)
     */
    native void setThreadLocalLong(int id, long value);

    /**
     * @see HotSpotJVMCIRuntime#getThreadLocalLong(int)
     */
    native long getThreadLocalLong(int id);

    /**
     * Adds phases in HotSpot JFR.
     *
     * @see JFR.CompilerPhaseEvent#write
     */
    native int registerCompilerPhase(String phaseName);

    /**
     * @see JFR.CompilerPhaseEvent#write
     */
    native void notifyCompilerPhaseEvent(long startTime, int phase, int compileId, int level);

    /**
     * @see JFR.CompilerInliningEvent#write
     */
    void notifyCompilerInliningEvent(int compileId, HotSpotResolvedJavaMethodImpl caller, HotSpotResolvedJavaMethodImpl callee, boolean succeeded, String message, int bci) {
        notifyCompilerInliningEvent(compileId, caller, caller.getMethodPointer(), callee, callee.getMethodPointer(), succeeded, message, bci);
    }

    native void notifyCompilerInliningEvent(int compileId, HotSpotResolvedJavaMethodImpl caller, long callerPointer,
                    HotSpotResolvedJavaMethodImpl callee, long calleePointer, boolean succeeded, String message, int bci);

    /**
     * Gets the serialized annotation info for {@code type} by calling
     * {@code VMSupport.encodeAnnotations} in the HotSpot heap.
     */
    byte[] getEncodedClassAnnotationData(HotSpotResolvedObjectTypeImpl type, ResolvedJavaType[] filter) {
        try (KlassPointers a = new KlassPointers(filter)) {
            return getEncodedClassAnnotationData(type, type.getKlassPointer(),
                            a.types, a.types.length, a.buffer());
        }
    }

    native byte[] getEncodedClassAnnotationData(HotSpotResolvedObjectTypeImpl type, long klassPointer,
                    Object filter, int filterLength, long filterKlassPointers);

    /**
     * Gets the serialized annotation info for {@code method} by calling
     * {@code VMSupport.encodeAnnotations} in the HotSpot heap.
     */
    byte[] getEncodedExecutableAnnotationData(HotSpotResolvedJavaMethodImpl method, ResolvedJavaType[] filter) {
        try (KlassPointers a = new KlassPointers(filter)) {
            return getEncodedExecutableAnnotationData(method, method.getMethodPointer(),
                            a.types, a.types.length, a.buffer());
        }
    }

    native byte[] getEncodedExecutableAnnotationData(HotSpotResolvedJavaMethodImpl method, long methodPointer,
                    Object filter, int filterLength, long filterKlassPointers);

    /**
     * Gets the serialized annotation info for the field denoted by {@code holder} and
     * {@code fieldIndex} by calling {@code VMSupport.encodeAnnotations} in the HotSpot heap.
     */
    byte[] getEncodedFieldAnnotationData(HotSpotResolvedObjectTypeImpl holder, int fieldIndex, ResolvedJavaType[] filter) {
        try (KlassPointers a = new KlassPointers(filter)) {
            return getEncodedFieldAnnotationData(holder, holder.getKlassPointer(), fieldIndex,
                            a.types, a.types.length, a.buffer());
        }
    }

    native byte[] getEncodedFieldAnnotationData(HotSpotResolvedObjectTypeImpl holder, long klassPointer, int fieldIndex,
                    Object filterTypes, int filterLength, long filterKlassPointers);

    /**
     * Helper for passing {@Klass*} values to native code.
     */
    static final class KlassPointers implements AutoCloseable {
        final ResolvedJavaType[] types;
        long pointersArray;
        final Unsafe unsafe = UnsafeAccess.UNSAFE;

        KlassPointers(ResolvedJavaType[] types) {
            this.types = types;
        }

        /**
         * Gets the buffer in which to pass the {@Klass*} values to JNI.
         *
         * @return a {@Klass*} value if {@code types.length == 1} otherwise the address of a native
         *         buffer holding an array of {@Klass*} values
         */
        long buffer() {
            int length = types.length;
            if (length == 1) {
                return ((HotSpotResolvedObjectTypeImpl) types[0]).getKlassPointer();
            } else {
                pointersArray = unsafe.allocateMemory(length * Long.BYTES);
                long pos = pointersArray;
                for (int i = 0; i < types.length; i++) {
                    HotSpotResolvedObjectTypeImpl hsType = (HotSpotResolvedObjectTypeImpl) types[i];
                    unsafe.putLong(pos, hsType.getKlassPointer());
                    pos += Long.BYTES;
                }
            }
            return pointersArray;
        }

        @Override
        public void close() {
            if (types.length != 1 && pointersArray != 0) {
                unsafe.freeMemory(pointersArray);
                pointersArray = 0;
            }
        }
    }
}

/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Calls from Java into HotSpot. The behavior of all the methods in this class that take a native
 * pointer as an argument (e.g., {@link #getSymbol(long)}) is undefined if the argument does not
 * denote a valid native object.
 */
final class CompilerToVM {
    /**
     * Initializes the native part of the JVMCI runtime.
     */
    private static native void registerNatives();

    static {
        initialize();
    }

    @SuppressWarnings("try")
    private static void initialize() {
        try (InitTimer t = timer("CompilerToVM.registerNatives")) {
            registerNatives();
        }
    }

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
    native byte[] getBytecode(HotSpotResolvedJavaMethodImpl method);

    /**
     * Gets the number of entries in {@code method}'s exception handler table or 0 if it has no
     * exception handler table.
     */
    native int getExceptionTableLength(HotSpotResolvedJavaMethodImpl method);

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
    native long getExceptionTableStart(HotSpotResolvedJavaMethodImpl method);

    /**
     * Determines whether {@code method} is currently compilable by the JVMCI compiler being used by
     * the VM. This can return false if JVMCI compilation failed earlier for {@code method}, a
     * breakpoint is currently set in {@code method} or {@code method} contains other bytecode
     * features that require special handling by the VM.
     */
    native boolean isCompilable(HotSpotResolvedJavaMethodImpl method);

    /**
     * Determines if {@code method} is targeted by a VM directive (e.g.,
     * {@code -XX:CompileCommand=dontinline,<pattern>}) or annotation (e.g.,
     * {@code jdk.internal.vm.annotation.DontInline}) that specifies it should not be inlined.
     */
    native boolean hasNeverInlineDirective(HotSpotResolvedJavaMethodImpl method);

    /**
     * Determines if {@code method} should be inlined at any cost. This could be because:
     * <ul>
     * <li>a CompileOracle directive may forces inlining of this methods</li>
     * <li>an annotation forces inlining of this method</li>
     * </ul>
     */
    native boolean shouldInlineMethod(HotSpotResolvedJavaMethodImpl method);

    /**
     * Used to implement {@link ResolvedJavaType#findUniqueConcreteMethod(ResolvedJavaMethod)}.
     *
     * @param method the method on which to base the search
     * @param actualHolderType the best known type of receiver
     * @return the method result or 0 is there is no unique concrete method for {@code method}
     */
    native HotSpotResolvedJavaMethodImpl findUniqueConcreteMethod(HotSpotResolvedObjectTypeImpl actualHolderType, HotSpotResolvedJavaMethodImpl method);

    /**
     * Gets the implementor for the interface class {@code type}.
     *
     * @return the implementor if there is a single implementor, {@code null} if there is no
     *         implementor, or {@code type} itself if there is more than one implementor
     * @throws IllegalArgumentException if type is not an interface type
     */
    native HotSpotResolvedObjectTypeImpl getImplementor(HotSpotResolvedObjectTypeImpl type);

    /**
     * Determines if {@code method} is ignored by security stack walks.
     */
    native boolean methodIsIgnoredBySecurityStackWalk(HotSpotResolvedJavaMethodImpl method);

    /**
     * Converts a name to a type.
     *
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingClass the context of resolution (must not be null)
     * @param resolve force resolution to a {@link ResolvedJavaType}. If true, this method will
     *            either return a {@link ResolvedJavaType} or throw an exception
     * @return the type for {@code name} or 0 if resolution failed and {@code resolve == false}
     * @throws ClassNotFoundException if {@code resolve == true} and the resolution failed
     */
    native HotSpotResolvedObjectTypeImpl lookupType(String name, Class<?> accessingClass, boolean resolve) throws ClassNotFoundException;

    /**
     * Resolves the entry at index {@code cpi} in {@code constantPool} to an object.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote one of the following
     * entry types: {@code JVM_CONSTANT_MethodHandle}, {@code JVM_CONSTANT_MethodHandleInError},
     * {@code JVM_CONSTANT_MethodType} and {@code JVM_CONSTANT_MethodTypeInError}.
     */
    native Object resolveConstantInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * Resolves the entry at index {@code cpi} in {@code constantPool} to an object, looking in the
     * constant pool cache first.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote a
     * {@code JVM_CONSTANT_String} entry.
     */
    native Object resolvePossiblyCachedConstantInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * Gets the {@code JVM_CONSTANT_NameAndType} index from the entry at index {@code cpi} in
     * {@code constantPool}.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote an entry containing a
     * {@code JVM_CONSTANT_NameAndType} index.
     */
    native int lookupNameAndTypeRefIndexInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * Gets the name of the {@code JVM_CONSTANT_NameAndType} entry referenced by another entry
     * denoted by {@code which} in {@code constantPool}.
     *
     * The behavior of this method is undefined if {@code which} does not denote a entry that
     * references a {@code JVM_CONSTANT_NameAndType} entry.
     */
    native String lookupNameInPool(HotSpotConstantPool constantPool, int which);

    /**
     * Gets the signature of the {@code JVM_CONSTANT_NameAndType} entry referenced by another entry
     * denoted by {@code which} in {@code constantPool}.
     *
     * The behavior of this method is undefined if {@code which} does not denote a entry that
     * references a {@code JVM_CONSTANT_NameAndType} entry.
     */
    native String lookupSignatureInPool(HotSpotConstantPool constantPool, int which);

    /**
     * Gets the {@code JVM_CONSTANT_Class} index from the entry at index {@code cpi} in
     * {@code constantPool}.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote an entry containing a
     * {@code JVM_CONSTANT_Class} index.
     */
    native int lookupKlassRefIndexInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * Looks up a class denoted by the {@code JVM_CONSTANT_Class} entry at index {@code cpi} in
     * {@code constantPool}. This method does not perform any resolution.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote a
     * {@code JVM_CONSTANT_Class} entry.
     *
     * @return the resolved class entry or a String otherwise
     */
    native Object lookupKlassInPool(HotSpotConstantPool constantPool, int cpi);

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
     * @return the resolved method entry, 0 otherwise
     */
    native HotSpotResolvedJavaMethodImpl lookupMethodInPool(HotSpotConstantPool constantPool, int cpi, byte opcode);

    // TODO resolving JVM_CONSTANT_Dynamic

    /**
     * Ensures that the type referenced by the specified {@code JVM_CONSTANT_InvokeDynamic} entry at
     * index {@code cpi} in {@code constantPool} is loaded and initialized.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote a
     * {@code JVM_CONSTANT_InvokeDynamic} entry.
     */
    native void resolveInvokeDynamicInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * If {@code cpi} denotes an entry representing a
     * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-2.html#jvms-2.9">signature
     * polymorphic</a> method, this method ensures that the type referenced by the entry is loaded
     * and initialized. It {@code cpi} does not denote a signature polymorphic method, this method
     * does nothing.
     */
    native void resolveInvokeHandleInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * If {@code cpi} denotes an entry representing a resolved dynamic adapter (see
     * {@link #resolveInvokeDynamicInPool} and {@link #resolveInvokeHandleInPool}), return the
     * opcode of the instruction for which the resolution was performed ({@code invokedynamic} or
     * {@code invokevirtual}), or {@code -1} otherwise.
     */
    native int isResolvedInvokeHandleInPool(HotSpotConstantPool constantPool, int cpi);

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
    native HotSpotResolvedObjectTypeImpl resolveTypeInPool(HotSpotConstantPool constantPool, int cpi) throws LinkageError;

    /**
     * Looks up and attempts to resolve the {@code JVM_CONSTANT_Field} entry for at index
     * {@code cpi} in {@code constantPool}. For some opcodes, checks are performed that require the
     * {@code method} that contains {@code opcode} to be specified. The values returned in
     * {@code info} are:
     *
     * <pre>
     *     [ flags,  // fieldDescriptor::access_flags()
     *       offset, // fieldDescriptor::offset()
     *       index   // fieldDescriptor::index()
     *     ]
     * </pre>
     *
     * The behavior of this method is undefined if {@code cpi} does not denote a
     * {@code JVM_CONSTANT_Field} entry.
     *
     * @param info an array in which the details of the field are returned
     * @return the type defining the field if resolution is successful, 0 otherwise
     */
    native HotSpotResolvedObjectTypeImpl resolveFieldInPool(HotSpotConstantPool constantPool, int cpi, HotSpotResolvedJavaMethodImpl method, byte opcode, int[] info);

    /**
     * Converts {@code cpci} from an index into the cache for {@code constantPool} to an index
     * directly into {@code constantPool}.
     *
     * The behavior of this method is undefined if {@code ccpi} is an invalid constant pool cache
     * index.
     */
    native int constantPoolRemapInstructionOperandFromCache(HotSpotConstantPool constantPool, int cpci);

    /**
     * Gets the appendix object (if any) associated with the entry at index {@code cpi} in
     * {@code constantPool}.
     */
    native Object lookupAppendixInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * Installs the result of a compilation into the code cache.
     *
     * @param target the target where this code should be installed
     * @param compiledCode the result of a compilation
     * @param code the details of the installed CodeBlob are written to this object
     * @return the outcome of the installation which will be one of
     *         {@link HotSpotVMConfig#codeInstallResultOk},
     *         {@link HotSpotVMConfig#codeInstallResultCacheFull},
     *         {@link HotSpotVMConfig#codeInstallResultCodeTooLarge},
     *         {@link HotSpotVMConfig#codeInstallResultDependenciesFailed} or
     *         {@link HotSpotVMConfig#codeInstallResultDependenciesInvalid}.
     * @throws JVMCIError if there is something wrong with the compiled code or the associated
     *             metadata.
     */
    native int installCode(TargetDescription target, HotSpotCompiledCode compiledCode, InstalledCode code, HotSpotSpeculationLog speculationLog);

    /**
     * Generates the VM metadata for some compiled code and copies them into {@code metaData}. This
     * method does not install anything into the code cache.
     *
     * @param target the target where this code would be installed
     * @param compiledCode the result of a compilation
     * @param metaData the metadata is written to this object
     * @return the outcome of the installation which will be one of
     *         {@link HotSpotVMConfig#codeInstallResultOk},
     *         {@link HotSpotVMConfig#codeInstallResultCacheFull},
     *         {@link HotSpotVMConfig#codeInstallResultCodeTooLarge},
     *         {@link HotSpotVMConfig#codeInstallResultDependenciesFailed} or
     *         {@link HotSpotVMConfig#codeInstallResultDependenciesInvalid}.
     * @throws JVMCIError if there is something wrong with the compiled code or the metadata
     */
    native int getMetadata(TargetDescription target, HotSpotCompiledCode compiledCode, HotSpotMetaData metaData);

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
     * @param caller the caller or context type used to perform access checks
     * @return the link-time resolved method (might be abstract) or {@code null} if it is either a
     *         signature polymorphic method or can not be linked.
     */
    native HotSpotResolvedJavaMethodImpl resolveMethod(HotSpotResolvedObjectTypeImpl exactReceiver, HotSpotResolvedJavaMethodImpl method, HotSpotResolvedObjectTypeImpl caller);

    /**
     * Gets the static initializer of {@code type}.
     *
     * @return {@code null} if {@code type} has no static initializer
     */
    native HotSpotResolvedJavaMethodImpl getClassInitializer(HotSpotResolvedObjectTypeImpl type);

    /**
     * Determines if {@code type} or any of its currently loaded subclasses overrides
     * {@code Object.finalize()}.
     */
    native boolean hasFinalizableSubclass(HotSpotResolvedObjectTypeImpl type);

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
    native StackTraceElement getStackTraceElement(HotSpotResolvedJavaMethodImpl method, int bci);

    /**
     * Executes some {@code installedCode} with arguments {@code args}.
     *
     * @return the result of executing {@code installedCode}
     * @throws InvalidInstalledCodeException if {@code installedCode} has been invalidated
     */
    native Object executeInstalledCode(Object[] args, InstalledCode installedCode) throws InvalidInstalledCodeException;

    /**
     * Gets the line number table for {@code method}. The line number table is encoded as (bci,
     * source line number) pairs.
     *
     * @return the line number table for {@code method} or null if it doesn't have one
     */
    native long[] getLineNumberTable(HotSpotResolvedJavaMethodImpl method);

    /**
     * Gets the number of entries in the local variable table for {@code method}.
     *
     * @return the number of entries in the local variable table for {@code method}
     */
    native int getLocalVariableTableLength(HotSpotResolvedJavaMethodImpl method);

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
    native long getLocalVariableTableStart(HotSpotResolvedJavaMethodImpl method);

    /**
     * Sets flags on {@code method} indicating that it should never be inlined or compiled by the
     * VM.
     */
    native void setNotInlinableOrCompilable(HotSpotResolvedJavaMethodImpl method);

    /**
     * Invalidates the profiling information for {@code method} and (re)initializes it such that
     * profiling restarts upon its next invocation.
     */
    native void reprofile(HotSpotResolvedJavaMethodImpl method);

    /**
     * Invalidates {@code installedCode} such that {@link InvalidInstalledCodeException} will be
     * raised the next time {@code installedCode} is executed.
     */
    native void invalidateInstalledCode(InstalledCode installedCode);

    /**
     * Collects the current values of all JVMCI benchmark counters, summed up over all threads.
     */
    native long[] collectCounters();

    /**
     * Determines if {@code metaspaceMethodData} is mature.
     */
    native boolean isMature(long metaspaceMethodData);

    /**
     * Generate a unique id to identify the result of the compile.
     */
    native int allocateCompileId(HotSpotResolvedJavaMethodImpl method, int entryBCI);

    /**
     * Determines if {@code method} has OSR compiled code identified by {@code entryBCI} for
     * compilation level {@code level}.
     */
    native boolean hasCompiledCodeForOSR(HotSpotResolvedJavaMethodImpl method, int entryBCI, int level);

    /**
     * Gets the value of {@code metaspaceSymbol} as a String.
     */
    native String getSymbol(long metaspaceSymbol);

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
     * @throws InternalError if {@code type} is an interface or {@code method} is not held by an
     *             interface or class represented by {@code type} is not initialized
     */
    native int getVtableIndexForInterfaceMethod(HotSpotResolvedObjectTypeImpl type, HotSpotResolvedJavaMethodImpl method);

    /**
     * Determines if debug info should also be emitted at non-safepoint locations.
     */
    native boolean shouldDebugNonSafepoints();

    /**
     * Writes {@code length} bytes from {@code bytes} starting at offset {@code offset} to HotSpot's
     * log stream.
     *
     * @throws NullPointerException if {@code bytes == null}
     * @throws IndexOutOfBoundsException if copying would cause access of data outside array bounds
     */
    native void writeDebugOutput(byte[] bytes, int offset, int length);

    /**
     * Flush HotSpot's log stream.
     */
    native void flushDebugOutput();

    /**
     * Read a HotSpot Method* value from the memory location described by {@code base} plus
     * {@code displacement} and return the {@link HotSpotResolvedJavaMethodImpl} wrapping it. This
     * method does no checking that the memory location actually contains a valid pointer and may
     * crash the VM if an invalid location is provided. If the {@code base} is null then
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
    native HotSpotResolvedJavaMethodImpl getResolvedJavaMethod(Object base, long displacement);

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
    native HotSpotConstantPool getConstantPool(Object object);

    /**
     * Read a HotSpot Klass* value from the memory location described by {@code base} plus
     * {@code displacement} and return the {@link HotSpotResolvedObjectTypeImpl} wrapping it. This
     * method does no checking that the memory location actually contains a valid pointer and may
     * crash the VM if an invalid location is provided. If the {@code base} is null then
     * {@code displacement} is used by itself. If {@code base} is a
     * {@link HotSpotResolvedJavaMethodImpl}, {@link HotSpotConstantPool} or
     * {@link HotSpotResolvedObjectTypeImpl} then the metaspace pointer is fetched from that object
     * and added to {@code displacement}. Any other non-null object type causes an
     * {@link IllegalArgumentException} to be thrown.
     *
     * @param base an object to read from or null
     * @param displacement
     * @param compressed true if the location contains a compressed Klass*
     * @return null or the resolved method for this location
     */
    native HotSpotResolvedObjectTypeImpl getResolvedJavaType(Object base, long displacement, boolean compressed);

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

    /**
     * Gets the fingerprint for a given Klass*.
     *
     * @param metaspaceKlass
     * @return the value of the fingerprint (zero for arrays and synthetic classes).
     */
    native long getFingerprint(long metaspaceKlass);

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
    native void compileToBytecode(Object lambdaForm);

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
     * Gets the host class for {@code type}.
     */
    native HotSpotResolvedObjectTypeImpl getHostClass(HotSpotResolvedObjectTypeImpl type);

    /**
     * Gets a {@link Executable} corresponding to {@code method}.
     */
    native Executable asReflectionExecutable(HotSpotResolvedJavaMethodImpl method);

    /**
     * Gets a {@link Field} denoted by {@code holder} and {@code index}.
     *
     * @param holder the class in which the requested field is declared
     * @param fieldIndex the {@code fieldDescriptor::index()} denoting the field
     */
    native Field asReflectionField(HotSpotResolvedObjectTypeImpl holder, int fieldIndex);
}

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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.inittimer.InitTimer.timer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspotvmconfig.HotSpotVMField;
import jdk.vm.ci.inittimer.InitTimer;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

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
     * Gets the number of entries in {@code method}'s exception handler table or 0 if it has not
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
     * Determines if {@code method} can be inlined. A method may not be inlinable for a number of
     * reasons such as:
     * <ul>
     * <li>a CompileOracle directive may prevent inlining or compilation of methods</li>
     * <li>the method may have a bytecode breakpoint set</li>
     * <li>the method may have other bytecode features that require special handling by the VM</li>
     * </ul>
     */
    native boolean canInlineMethod(HotSpotResolvedJavaMethodImpl method);

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
     * @return the implementor if there is a single implementor, 0 if there is no implementor, or
     *         {@code type} itself if there is more than one implementor
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
     * @throws LinkageError if {@code resolve == true} and the resolution failed
     */
    native HotSpotResolvedObjectTypeImpl lookupType(String name, Class<?> accessingClass, boolean resolve);

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

    /**
     * Ensures that the type referenced by the specified {@code JVM_CONSTANT_InvokeDynamic} entry at
     * index {@code cpi} in {@code constantPool} is loaded and initialized.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote a
     * {@code JVM_CONSTANT_InvokeDynamic} entry.
     */
    native void resolveInvokeDynamicInPool(HotSpotConstantPool constantPool, int cpi);

    /**
     * Ensures that the type referenced by the entry for a <a
     * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-2.html#jvms-2.9">signature
     * polymorphic</a> method at index {@code cpi} in {@code constantPool} is loaded and
     * initialized.
     *
     * The behavior of this method is undefined if {@code cpi} does not denote an entry representing
     * a signature polymorphic method.
     */
    native void resolveInvokeHandleInPool(HotSpotConstantPool constantPool, int cpi);

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
     * Looks up and attempts to resolve the {@code JVM_CONSTANT_Field} entry at index {@code cpi} in
     * {@code constantPool}. The values returned in {@code info} are:
     *
     * <pre>
     *     [(int) flags,   // only valid if field is resolved
     *      (int) offset]  // only valid if field is resolved
     * </pre>
     *
     * The behavior of this method is undefined if {@code cpi} does not denote a
     * {@code JVM_CONSTANT_Field} entry.
     *
     * @param info an array in which the details of the field are returned
     * @return the type defining the field if resolution is successful, 0 otherwise
     */
    native HotSpotResolvedObjectTypeImpl resolveFieldInPool(HotSpotConstantPool constantPool, int cpi, byte opcode, long[] info);

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

    public native int getMetadata(TargetDescription target, HotSpotCompiledCode compiledCode, HotSpotMetaData metaData);

    /**
     * Notifies the VM of statistics for a completed compilation.
     *
     * @param id the identifier of the compilation
     * @param method the method compiled
     * @param osr specifies if the compilation was for on-stack-replacement
     * @param processedBytecodes the number of bytecodes processed during the compilation, including
     *            the bytecodes of all inlined methods
     * @param time the amount time spent compiling {@code method}
     * @param timeUnitsPerSecond the granularity of the units for the {@code time} value
     * @param installedCode the nmethod installed as a result of the compilation
     */
    synchronized native void notifyCompilationStatistics(int id, HotSpotResolvedJavaMethodImpl method, boolean osr, int processedBytecodes, long time, long timeUnitsPerSecond,
                    InstalledCode installedCode);

    /**
     * Resets all compilation statistics.
     */
    native void resetCompilationStatistics();

    /**
     * Initializes the fields of {@code config}.
     */
    native long initializeConfiguration(HotSpotVMConfig config);

    /**
     * Resolves the implementation of {@code method} for virtual dispatches on objects of dynamic
     * type {@code exactReceiver}. This resolution process only searches "up" the class hierarchy of
     * {@code exactReceiver}.
     *
     * @param caller the caller or context type used to perform access checks
     * @return the link-time resolved method (might be abstract) or {@code 0} if it can not be
     *         linked
     */
    native HotSpotResolvedJavaMethodImpl resolveMethod(HotSpotResolvedObjectTypeImpl exactReceiver, HotSpotResolvedJavaMethodImpl method, HotSpotResolvedObjectTypeImpl caller);

    /**
     * Gets the static initializer of {@code type}.
     *
     * @return 0 if {@code type} has no static initializer
     */
    native HotSpotResolvedJavaMethodImpl getClassInitializer(HotSpotResolvedObjectTypeImpl type);

    /**
     * Determines if {@code type} or any of its currently loaded subclasses overrides
     * {@code Object.finalize()}.
     */
    native boolean hasFinalizableSubclass(HotSpotResolvedObjectTypeImpl type);

    /**
     * Gets the method corresponding to {@code holder} and slot number {@code slot} (i.e.
     * {@link Method#slot} or {@link Constructor#slot}).
     */
    native HotSpotResolvedJavaMethodImpl getResolvedJavaMethodAtSlot(Class<?> holder, int slot);

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
     * <li>{@link HotSpotVMConfig#localVariableTableElementSignatureCpIndexOffset}
     * <li>{@link HotSpotVMConfig#localVariableTableElementSlotOffset}
     * <li>{@link HotSpotVMConfig#localVariableTableElementStartBciOffset}
     * </ul>
     *
     * @return 0 if {@code method} does not have a local variable table
     */
    native long getLocalVariableTableStart(HotSpotResolvedJavaMethodImpl method);

    /**
     * Reads an object pointer within a VM data structure. That is, any {@link HotSpotVMField} whose
     * {@link HotSpotVMField#type() type} is {@code "oop"} (e.g.,
     * {@code ArrayKlass::_component_mirror}, {@code Klass::_java_mirror},
     * {@code JavaThread::_threadObj}).
     *
     * Note that {@link Unsafe#getObject(Object, long)} cannot be used for this since it does a
     * {@code narrowOop} read if the VM is using compressed oops whereas oops within VM data
     * structures are (currently) always uncompressed.
     *
     * @param address address of an oop field within a VM data structure
     */
    native Object readUncompressedOop(long address);

    /**
     * Determines if {@code method} should not be inlined or compiled.
     */
    native void doNotInlineOrCompile(HotSpotResolvedJavaMethodImpl method);

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
     * Looks for the next Java stack frame matching an entry in {@code methods}.
     *
     * @param frame the starting point of the search, where {@code null} refers to the topmost frame
     * @param methods the methods to look for, where {@code null} means that any frame is returned
     * @return the frame, or {@code null} if the end of the stack was reached during the search
     */
    native HotSpotStackFrameReference getNextStackFrame(HotSpotStackFrameReference frame, ResolvedJavaMethod[] methods, int initialSkip);

    /**
     * Materializes all virtual objects within {@code stackFrame} updates its locals.
     *
     * @param invalidate if {@code true}, the compiled method for the stack frame will be
     *            invalidated.
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
     * Writes {@code length} bytes from {@code bytes} starting at offset {@code offset} to the
     * HotSpot's log stream.
     *
     * @exception NullPointerException if {@code bytes == null}
     * @exception IndexOutOfBoundsException if copying would cause access of data outside array
     *                bounds
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
     * Read a HotSpot ConstantPool* value from the memory location described by {@code base} plus
     * {@code displacement} and return the {@link HotSpotConstantPool} wrapping it. This method does
     * no checking that the memory location actually contains a valid pointer and may crash the VM
     * if an invalid location is provided. If the {@code base} is null then {@code displacement} is
     * used by itself. If {@code base} is a {@link HotSpotResolvedJavaMethodImpl},
     * {@link HotSpotConstantPool} or {@link HotSpotResolvedObjectTypeImpl} then the metaspace
     * pointer is fetched from that object and added to {@code displacement}. Any other non-null
     * object type causes an {@link IllegalArgumentException} to be thrown.
     *
     * @param base an object to read from or null
     * @param displacement
     * @return null or the resolved method for this location
     */
    native HotSpotConstantPool getConstantPool(Object base, long displacement);

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
}

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.SpeculationLog;

/*
 * A simple "proxy" class to get test access to CompilerToVM package-private methods
 */
public class CompilerToVMHelper {
    public static final CompilerToVM CTVM = new CompilerToVM();

    public static byte[] getBytecode(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.getBytecode(method);
    }

    public static int getExceptionTableLength(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.getExceptionTableLength(method);
    }

    public static long getExceptionTableStart(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.getExceptionTableStart(method);
    }

    public static boolean canInlineMethod(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.canInlineMethod(method);
    }

    public static boolean shouldInlineMethod(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.shouldInlineMethod(method);
    }

    public static HotSpotResolvedJavaMethodImpl findUniqueConcreteMethod(
            HotSpotResolvedObjectTypeImpl actualHolderType,
            HotSpotResolvedJavaMethodImpl method) {
        return CTVM.findUniqueConcreteMethod(actualHolderType, method);
    }

    public static HotSpotResolvedObjectTypeImpl getImplementor(HotSpotResolvedObjectTypeImpl type) {
        return CTVM.getImplementor(type);
    }

    public static boolean methodIsIgnoredBySecurityStackWalk(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.methodIsIgnoredBySecurityStackWalk(method);
    }

    public static HotSpotResolvedObjectTypeImpl lookupType(String name,
            Class<?> accessingClass, boolean resolve) {
        return CTVM.lookupType(name, accessingClass, resolve);
    }

    public static Object resolveConstantInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.resolveConstantInPool(constantPool, cpi);
    }

    public static Object resolvePossiblyCachedConstantInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.resolvePossiblyCachedConstantInPool(constantPool, cpi);
    }

    public static int lookupNameAndTypeRefIndexInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.lookupNameAndTypeRefIndexInPool(constantPool, cpi);
    }

    public static String lookupNameInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.lookupNameInPool(constantPool, cpi);
    }

    public static String lookupSignatureInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.lookupSignatureInPool(constantPool, cpi);
    }

    public static int lookupKlassRefIndexInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.lookupKlassRefIndexInPool(constantPool, cpi);
    }

    public static Object lookupKlassInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.lookupKlassInPool(constantPool, cpi);
    }

    public static HotSpotResolvedJavaMethodImpl lookupMethodInPool(
            HotSpotConstantPool constantPool, int cpi, byte opcode) {
        return CTVM.lookupMethodInPool(constantPool, cpi, opcode);
    }

    public static void resolveInvokeDynamicInPool(HotSpotConstantPool constantPool, int cpi) {
        CTVM.resolveInvokeDynamicInPool(constantPool, cpi);
    }

    public static void resolveInvokeHandleInPool(HotSpotConstantPool constantPool, int cpi) {
        CTVM.resolveInvokeHandleInPool(constantPool, cpi);
    }

    public static HotSpotResolvedObjectTypeImpl resolveTypeInPool(
            HotSpotConstantPool constantPool, int cpi) throws LinkageError {
        return CTVM.resolveTypeInPool(constantPool, cpi);
    }

    public static HotSpotResolvedObjectTypeImpl resolveFieldInPool(
            HotSpotConstantPool constantPool, int cpi, byte opcode, long[] info) {
        return CTVM.resolveFieldInPool(constantPool, cpi, opcode, info);
    }

    public static int constantPoolRemapInstructionOperandFromCache(
            HotSpotConstantPool constantPool, int cpci) {
        return CTVM.constantPoolRemapInstructionOperandFromCache(constantPool, cpci);
    }

    public static Object lookupAppendixInPool(HotSpotConstantPool constantPool, int cpi) {
        return CTVM.lookupAppendixInPool(constantPool, cpi);
    }

    public static int installCode(TargetDescription target,
            HotSpotCompiledCode compiledCode, InstalledCode code, SpeculationLog speculationLog) {
        return CTVM.installCode(target, compiledCode, code, speculationLog);
    }

    public static int getMetadata(TargetDescription target,
            HotSpotCompiledCode compiledCode, HotSpotMetaData metaData) {
        return CTVM.getMetadata(target, compiledCode, metaData);
    }

    public static void notifyCompilationStatistics(int id,
            HotSpotResolvedJavaMethodImpl method, boolean osr,
            int processedBytecodes, long time, long timeUnitsPerSecond,
            InstalledCode installedCode) {
        CTVM.notifyCompilationStatistics(id, method, osr, processedBytecodes,
                time, timeUnitsPerSecond, installedCode);
    }

    public static void resetCompilationStatistics() {
        CTVM.resetCompilationStatistics();
    }

    public static long initializeConfiguration() {
        return CTVM.initializeConfiguration();
    }

    public static HotSpotResolvedJavaMethodImpl resolveMethod(
            HotSpotResolvedObjectTypeImpl exactReceiver,
            HotSpotResolvedJavaMethodImpl method,
            HotSpotResolvedObjectTypeImpl caller) {
        return CTVM.resolveMethod(exactReceiver, method, caller);
    }

    public static HotSpotResolvedJavaMethodImpl getClassInitializer(
            HotSpotResolvedObjectTypeImpl type) {
        return CTVM.getClassInitializer(type);
    }

    public static boolean hasFinalizableSubclass(HotSpotResolvedObjectTypeImpl type) {
        return CTVM.hasFinalizableSubclass(type);
    }

    public static HotSpotResolvedJavaMethodImpl getResolvedJavaMethodAtSlot(Class<?> holder,
            int slot) {
        return CTVM.getResolvedJavaMethodAtSlot(holder, slot);
    }

    public static long getMaxCallTargetOffset(long address) {
        return CTVM.getMaxCallTargetOffset(address);
    }

    public static String disassembleCodeBlob(long codeBlob) {
        return CTVM.disassembleCodeBlob(codeBlob);
    }

    public static StackTraceElement getStackTraceElement(
            HotSpotResolvedJavaMethodImpl method, int bci) {
        return CTVM.getStackTraceElement(method, bci);
    }

    public static Object executeInstalledCode(Object[] args,
            InstalledCode installedCode) throws InvalidInstalledCodeException {
        return CTVM.executeInstalledCode(args, installedCode);
    }

    public static long[] getLineNumberTable(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.getLineNumberTable(method);
    }

    public static int getLocalVariableTableLength(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.getLocalVariableTableLength(method);
    }

    public static long getLocalVariableTableStart(HotSpotResolvedJavaMethodImpl method) {
        return CTVM.getLocalVariableTableStart(method);
    }

    public static Object readUncompressedOop(long address) {
        return CTVM.readUncompressedOop(address);
    }

    public static void doNotInlineOrCompile(HotSpotResolvedJavaMethodImpl method) {
        CTVM.doNotInlineOrCompile(method);
    }

    public static void reprofile(HotSpotResolvedJavaMethodImpl method) {
        CTVM.reprofile(method);
    }

    public static void invalidateInstalledCode(InstalledCode installedCode) {
        CTVM.invalidateInstalledCode(installedCode);
    }

    public static long[] collectCounters() {
        return CTVM.collectCounters();
    }

    public static boolean isMature(long metaspaceMethodData) {
        return CTVM.isMature(metaspaceMethodData);
    }

    public static int allocateCompileId(HotSpotResolvedJavaMethodImpl method,
            int entryBCI) {
        return CTVM.allocateCompileId(method, entryBCI);
    }

    public static boolean hasCompiledCodeForOSR(
            HotSpotResolvedJavaMethodImpl method, int entryBCI, int level) {
        return CTVM.hasCompiledCodeForOSR(method, entryBCI, level);
    }

    public static String getSymbol(long metaspaceSymbol) {
        return CTVM.getSymbol(metaspaceSymbol);
    }

    public static HotSpotStackFrameReference getNextStackFrame(
            HotSpotStackFrameReference frame,
            HotSpotResolvedJavaMethodImpl[] methods, int initialSkip) {
        return CTVM.getNextStackFrame(frame, methods, initialSkip);
    }

    public static void materializeVirtualObjects(
            HotSpotStackFrameReference stackFrame, boolean invalidate) {
        CTVM.materializeVirtualObjects(stackFrame, invalidate);
    }

    public static int getVtableIndexForInterfaceMethod(HotSpotResolvedObjectTypeImpl type,
            HotSpotResolvedJavaMethodImpl method) {
        return CTVM.getVtableIndexForInterfaceMethod(type, method);
    }

    public static boolean shouldDebugNonSafepoints() {
        return CTVM.shouldDebugNonSafepoints();
    }

    public static void writeDebugOutput(byte[] bytes, int offset, int length) {
        CTVM.writeDebugOutput(bytes, offset, length);
    }

    public static void flushDebugOutput() {
        CTVM.flushDebugOutput();
    }

    public static HotSpotResolvedJavaMethodImpl getResolvedJavaMethod(Object base,
            long displacement) {
        return CTVM.getResolvedJavaMethod(base, displacement);
    }

    public static HotSpotConstantPool getConstantPool(Object base, long displacement) {
        return CTVM.getConstantPool(base, displacement);
    }

    public static HotSpotResolvedObjectTypeImpl getResolvedJavaType(Object base,
            long displacement, boolean compressed) {
        return CTVM.getResolvedJavaType(base, displacement, compressed);
    }
}

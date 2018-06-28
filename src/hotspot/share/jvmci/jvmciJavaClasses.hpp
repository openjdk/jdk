/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JVMCI_JVMCIJAVACLASSES_HPP
#define SHARE_VM_JVMCI_JVMCIJAVACLASSES_HPP

#include "classfile/systemDictionary.hpp"
#include "oops/access.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/oop.hpp"

class JVMCIJavaClasses : AllStatic {
 public:
  static void compute_offsets(TRAPS);
};

/* This macro defines the structure of the JVMCI classes accessed from VM code.
 * It will generate classes with accessors similar to javaClasses.hpp, but with specializations for oops, Handles and jni handles.
 *
 * The public interface of these classes will look like this:

 * class StackSlot : AllStatic {
 * public:
 *   static Klass* klass();
 *   static jint  index(oop obj);
 *   static jint  index(Handle obj);
 *   static jint  index(jobject obj);
 *   static void set_index(oop obj, jint x);
 *   static void set_index(Handle obj, jint x);
 *   static void set_index(jobject obj, jint x);
 * };
 *
 */

#define COMPILER_CLASSES_DO(start_class, end_class, char_field, int_field, boolean_field, long_field, float_field, oop_field, typeArrayOop_field, objArrayOop_field, static_oop_field, static_objArrayOop_field, static_int_field, static_boolean_field) \
  start_class(Architecture)                                                                                                                                    \
    oop_field(Architecture, wordKind, "Ljdk/vm/ci/meta/PlatformKind;")                                                                                         \
  end_class                                                                                                                                                    \
  start_class(TargetDescription)                                                                                                                               \
    oop_field(TargetDescription, arch, "Ljdk/vm/ci/code/Architecture;")                                                                                        \
  end_class                                                                                                                                                    \
  start_class(HotSpotResolvedObjectTypeImpl)                                                                                                                   \
    oop_field(HotSpotResolvedObjectTypeImpl, javaClass, "Ljava/lang/Class;")                                                                                   \
  end_class                                                                                                                                                    \
  start_class(HotSpotResolvedJavaMethodImpl)                                                                                                                   \
    long_field(HotSpotResolvedJavaMethodImpl, metaspaceMethod)                                                                                                 \
  end_class                                                                                                                                                    \
  start_class(InstalledCode)                                                                                                                                   \
    long_field(InstalledCode, address)                                                                                                                         \
    long_field(InstalledCode, entryPoint)                                                                                                                      \
    long_field(InstalledCode, version)                                                                                                                         \
    oop_field(InstalledCode, name, "Ljava/lang/String;")                                                                                                       \
  end_class                                                                                                                                                    \
  start_class(HotSpotInstalledCode)                                                                                                                            \
    int_field(HotSpotInstalledCode, size)                                                                                                                      \
    long_field(HotSpotInstalledCode, codeStart)                                                                                                                \
    int_field(HotSpotInstalledCode, codeSize)                                                                                                                  \
  end_class                                                                                                                                                    \
  start_class(HotSpotNmethod)                                                                                                                                  \
    boolean_field(HotSpotNmethod, isDefault)                                                                                                                   \
  end_class                                                                                                                                                    \
  start_class(HotSpotCompiledCode)                                                                                                                             \
    oop_field(HotSpotCompiledCode, name, "Ljava/lang/String;")                                                                                                 \
    typeArrayOop_field(HotSpotCompiledCode, targetCode, "[B")                                                                                                  \
    int_field(HotSpotCompiledCode, targetCodeSize)                                                                                                             \
    objArrayOop_field(HotSpotCompiledCode, sites, "[Ljdk/vm/ci/code/site/Site;")                                                                               \
    objArrayOop_field(HotSpotCompiledCode, assumptions, "[Ljdk/vm/ci/meta/Assumptions$Assumption;")                                                            \
    objArrayOop_field(HotSpotCompiledCode, methods, "[Ljdk/vm/ci/meta/ResolvedJavaMethod;")                                                                    \
    objArrayOop_field(HotSpotCompiledCode, comments, "[Ljdk/vm/ci/hotspot/HotSpotCompiledCode$Comment;")                                                       \
    typeArrayOop_field(HotSpotCompiledCode, dataSection, "[B")                                                                                                 \
    int_field(HotSpotCompiledCode, dataSectionAlignment)                                                                                                       \
    objArrayOop_field(HotSpotCompiledCode, dataSectionPatches, "[Ljdk/vm/ci/code/site/DataPatch;")                                                             \
    boolean_field(HotSpotCompiledCode, isImmutablePIC)                                                                                                         \
    int_field(HotSpotCompiledCode, totalFrameSize)                                                                                                             \
    oop_field(HotSpotCompiledCode, deoptRescueSlot, "Ljdk/vm/ci/code/StackSlot;")                                                                              \
  end_class                                                                                                                                                    \
  start_class(HotSpotCompiledCode_Comment)                                                                                                                     \
    oop_field(HotSpotCompiledCode_Comment, text, "Ljava/lang/String;")                                                                                         \
    int_field(HotSpotCompiledCode_Comment, pcOffset)                                                                                                           \
  end_class                                                                                                                                                    \
  start_class(HotSpotCompiledNmethod)                                                                                                                          \
    oop_field(HotSpotCompiledNmethod, method, "Ljdk/vm/ci/hotspot/HotSpotResolvedJavaMethod;")                                                                 \
    oop_field(HotSpotCompiledNmethod, installationFailureMessage, "Ljava/lang/String;")                                                                        \
    int_field(HotSpotCompiledNmethod, entryBCI)                                                                                                                \
    int_field(HotSpotCompiledNmethod, id)                                                                                                                      \
    long_field(HotSpotCompiledNmethod, jvmciEnv)                                                                                                               \
    boolean_field(HotSpotCompiledNmethod, hasUnsafeAccess)                                                                                                     \
  end_class                                                                                                                                                    \
  start_class(HotSpotJVMCIMetaAccessContext)                                                                                                                   \
    static_objArrayOop_field(HotSpotJVMCIMetaAccessContext, allContexts, "[Ljava/lang/ref/WeakReference;")                                                     \
    objArrayOop_field(HotSpotJVMCIMetaAccessContext, metadataRoots, "[Ljava/lang/Object;")                                                                     \
  end_class                                                                                                                                                    \
  start_class(HotSpotForeignCallTarget)                                                                                                                        \
    long_field(HotSpotForeignCallTarget, address)                                                                                                              \
  end_class                                                                                                                                                    \
  start_class(VMField)                                                                                                                                         \
    oop_field(VMField, name, "Ljava/lang/String;")                                                                                                             \
    oop_field(VMField, type, "Ljava/lang/String;")                                                                                                             \
    long_field(VMField, offset)                                                                                                                                \
    long_field(VMField, address)                                                                                                                               \
    oop_field(VMField, value, "Ljava/lang/Object;")                                                                                                            \
  end_class                                                                                                                                                    \
  start_class(VMFlag)                                                                                                                                          \
    oop_field(VMFlag, name, "Ljava/lang/String;")                                                                                                              \
    oop_field(VMFlag, type, "Ljava/lang/String;")                                                                                                              \
    oop_field(VMFlag, value, "Ljava/lang/Object;")                                                                                                             \
  end_class                                                                                                                                                    \
  start_class(VMIntrinsicMethod)                                                                                                                               \
    oop_field(VMIntrinsicMethod, declaringClass, "Ljava/lang/String;")                                                                                         \
    oop_field(VMIntrinsicMethod, name, "Ljava/lang/String;")                                                                                                   \
    oop_field(VMIntrinsicMethod, descriptor, "Ljava/lang/String;")                                                                                             \
    int_field(VMIntrinsicMethod, id)                                                                                                                           \
  end_class                                                                                                                                                    \
  start_class(Assumptions_NoFinalizableSubclass)                                                                                                               \
    oop_field(Assumptions_NoFinalizableSubclass, receiverType, "Ljdk/vm/ci/meta/ResolvedJavaType;")                                                            \
  end_class                                                                                                                                                    \
  start_class(Assumptions_ConcreteSubtype)                                                                                                                     \
    oop_field(Assumptions_ConcreteSubtype, context, "Ljdk/vm/ci/meta/ResolvedJavaType;")                                                                       \
    oop_field(Assumptions_ConcreteSubtype, subtype, "Ljdk/vm/ci/meta/ResolvedJavaType;")                                                                       \
  end_class                                                                                                                                                    \
  start_class(Assumptions_LeafType)                                                                                                                            \
    oop_field(Assumptions_LeafType, context, "Ljdk/vm/ci/meta/ResolvedJavaType;")                                                                              \
  end_class                                                                                                                                                    \
  start_class(Assumptions_ConcreteMethod)                                                                                                                      \
    oop_field(Assumptions_ConcreteMethod, method, "Ljdk/vm/ci/meta/ResolvedJavaMethod;")                                                                       \
    oop_field(Assumptions_ConcreteMethod, context, "Ljdk/vm/ci/meta/ResolvedJavaType;")                                                                        \
    oop_field(Assumptions_ConcreteMethod, impl, "Ljdk/vm/ci/meta/ResolvedJavaMethod;")                                                                         \
  end_class                                                                                                                                                    \
  start_class(Assumptions_CallSiteTargetValue)                                                                                                                 \
    oop_field(Assumptions_CallSiteTargetValue, callSite, "Ljdk/vm/ci/meta/JavaConstant;")                                                                      \
    oop_field(Assumptions_CallSiteTargetValue, methodHandle, "Ljdk/vm/ci/meta/JavaConstant;")                                                                  \
  end_class                                                                                                                                                    \
  start_class(site_Site)                                                                                                                                       \
    int_field(site_Site, pcOffset)                                                                                                                             \
  end_class                                                                                                                                                    \
  start_class(site_Call)                                                                                                                                       \
    oop_field(site_Call, target, "Ljdk/vm/ci/meta/InvokeTarget;")                                                                                              \
    oop_field(site_Call, debugInfo, "Ljdk/vm/ci/code/DebugInfo;")                                                                                              \
  end_class                                                                                                                                                    \
  start_class(site_DataPatch)                                                                                                                                  \
    oop_field(site_DataPatch, reference, "Ljdk/vm/ci/code/site/Reference;")                                                                                    \
  end_class                                                                                                                                                    \
  start_class(site_ConstantReference)                                                                                                                          \
    oop_field(site_ConstantReference, constant, "Ljdk/vm/ci/meta/VMConstant;")                                                                                 \
  end_class                                                                                                                                                    \
  start_class(site_DataSectionReference)                                                                                                                       \
    int_field(site_DataSectionReference, offset)                                                                                                               \
  end_class                                                                                                                                                    \
  start_class(site_InfopointReason)                                                                                                                            \
    static_oop_field(site_InfopointReason, SAFEPOINT, "Ljdk/vm/ci/code/site/InfopointReason;")                                                                 \
    static_oop_field(site_InfopointReason, CALL, "Ljdk/vm/ci/code/site/InfopointReason;")                                                                      \
    static_oop_field(site_InfopointReason, IMPLICIT_EXCEPTION, "Ljdk/vm/ci/code/site/InfopointReason;")                                                        \
  end_class                                                                                                                                                    \
  start_class(site_Infopoint)                                                                                                                                  \
    oop_field(site_Infopoint, debugInfo, "Ljdk/vm/ci/code/DebugInfo;")                                                                                         \
    oop_field(site_Infopoint, reason, "Ljdk/vm/ci/code/site/InfopointReason;")                                                                                 \
  end_class                                                                                                                                                    \
  start_class(site_ExceptionHandler)                                                                                                                           \
    int_field(site_ExceptionHandler, handlerPos)                                                                                                               \
  end_class                                                                                                                                                    \
  start_class(site_Mark)                                                                                                                                       \
    oop_field(site_Mark, id, "Ljava/lang/Object;")                                                                                                             \
  end_class                                                                                                                                                    \
  start_class(HotSpotCompilationRequestResult)                                                                                                                 \
    oop_field(HotSpotCompilationRequestResult, failureMessage, "Ljava/lang/String;")                                                                           \
    boolean_field(HotSpotCompilationRequestResult, retry)                                                                                                      \
    int_field(HotSpotCompilationRequestResult, inlinedBytecodes)                                                                                               \
  end_class                                                                                                                                                    \
  start_class(DebugInfo)                                                                                                                                       \
    oop_field(DebugInfo, bytecodePosition, "Ljdk/vm/ci/code/BytecodePosition;")                                                                                \
    oop_field(DebugInfo, referenceMap, "Ljdk/vm/ci/code/ReferenceMap;")                                                                                        \
    oop_field(DebugInfo, calleeSaveInfo, "Ljdk/vm/ci/code/RegisterSaveLayout;")                                                                                \
    objArrayOop_field(DebugInfo, virtualObjectMapping, "[Ljdk/vm/ci/code/VirtualObject;")                                                                      \
  end_class                                                                                                                                                    \
  start_class(HotSpotReferenceMap)                                                                                                                             \
    objArrayOop_field(HotSpotReferenceMap, objects, "[Ljdk/vm/ci/code/Location;")                                                                              \
    objArrayOop_field(HotSpotReferenceMap, derivedBase, "[Ljdk/vm/ci/code/Location;")                                                                          \
    typeArrayOop_field(HotSpotReferenceMap, sizeInBytes, "[I")                                                                                                 \
    int_field(HotSpotReferenceMap, maxRegisterSize)                                                                                                            \
  end_class                                                                                                                                                    \
  start_class(RegisterSaveLayout)                                                                                                                              \
    objArrayOop_field(RegisterSaveLayout, registers, "[Ljdk/vm/ci/code/Register;")                                                                             \
    typeArrayOop_field(RegisterSaveLayout, slots, "[I")                                                                                                        \
  end_class                                                                                                                                                    \
  start_class(BytecodeFrame)                                                                                                                                   \
    objArrayOop_field(BytecodeFrame, values, "[Ljdk/vm/ci/meta/JavaValue;")                                                                                    \
    objArrayOop_field(BytecodeFrame, slotKinds, "[Ljdk/vm/ci/meta/JavaKind;")                                                                                  \
    int_field(BytecodeFrame, numLocals)                                                                                                                        \
    int_field(BytecodeFrame, numStack)                                                                                                                         \
    int_field(BytecodeFrame, numLocks)                                                                                                                         \
    boolean_field(BytecodeFrame, rethrowException)                                                                                                             \
    boolean_field(BytecodeFrame, duringCall)                                                                                                                   \
    static_int_field(BytecodeFrame, UNKNOWN_BCI)                                                                                                               \
    static_int_field(BytecodeFrame, UNWIND_BCI)                                                                                                                \
    static_int_field(BytecodeFrame, BEFORE_BCI)                                                                                                                \
    static_int_field(BytecodeFrame, AFTER_BCI)                                                                                                                 \
    static_int_field(BytecodeFrame, AFTER_EXCEPTION_BCI)                                                                                                       \
    static_int_field(BytecodeFrame, INVALID_FRAMESTATE_BCI)                                                                                                    \
  end_class                                                                                                                                                    \
  start_class(BytecodePosition)                                                                                                                                \
    oop_field(BytecodePosition, caller, "Ljdk/vm/ci/code/BytecodePosition;")                                                                                   \
    oop_field(BytecodePosition, method, "Ljdk/vm/ci/meta/ResolvedJavaMethod;")                                                                                 \
    int_field(BytecodePosition, bci)                                                                                                                           \
  end_class                                                                                                                                                    \
  start_class(JavaConstant)                                                                                                                                    \
  end_class                                                                                                                                                    \
  start_class(PrimitiveConstant)                                                                                                                               \
    oop_field(PrimitiveConstant, kind, "Ljdk/vm/ci/meta/JavaKind;")                                                                                            \
    long_field(PrimitiveConstant, primitive)                                                                                                                   \
  end_class                                                                                                                                                    \
  start_class(RawConstant)                                                                                                                                     \
    long_field(RawConstant, primitive)                                                                                                                         \
  end_class                                                                                                                                                    \
  start_class(NullConstant)                                                                                                                                    \
  end_class                                                                                                                                                    \
  start_class(HotSpotCompressedNullConstant)                                                                                                                   \
  end_class                                                                                                                                                    \
  start_class(HotSpotObjectConstantImpl)                                                                                                                       \
    oop_field(HotSpotObjectConstantImpl, object, "Ljava/lang/Object;")                                                                                         \
    boolean_field(HotSpotObjectConstantImpl, compressed)                                                                                                       \
  end_class                                                                                                                                                    \
  start_class(HotSpotMetaspaceConstantImpl)                                                                                                                    \
    oop_field(HotSpotMetaspaceConstantImpl, metaspaceObject, "Ljdk/vm/ci/hotspot/MetaspaceWrapperObject;")                                                     \
    boolean_field(HotSpotMetaspaceConstantImpl, compressed)                                                                                                    \
  end_class                                                                                                                                                    \
  start_class(HotSpotSentinelConstant)                                                                                                                         \
  end_class                                                                                                                                                    \
  start_class(JavaKind)                                                                                                                                        \
    char_field(JavaKind, typeChar)                                                                                                                             \
    static_oop_field(JavaKind, Boolean, "Ljdk/vm/ci/meta/JavaKind;")                                                                                           \
    static_oop_field(JavaKind, Byte, "Ljdk/vm/ci/meta/JavaKind;")                                                                                              \
    static_oop_field(JavaKind, Char, "Ljdk/vm/ci/meta/JavaKind;")                                                                                              \
    static_oop_field(JavaKind, Short, "Ljdk/vm/ci/meta/JavaKind;")                                                                                             \
    static_oop_field(JavaKind, Int, "Ljdk/vm/ci/meta/JavaKind;")                                                                                               \
    static_oop_field(JavaKind, Long, "Ljdk/vm/ci/meta/JavaKind;")                                                                                              \
  end_class                                                                                                                                                    \
  start_class(ValueKind)                                                                                                                                       \
    oop_field(ValueKind, platformKind, "Ljdk/vm/ci/meta/PlatformKind;")                                                                                        \
  end_class                                                                                                                                                    \
  start_class(Value)                                                                                                                                           \
    oop_field(Value, valueKind, "Ljdk/vm/ci/meta/ValueKind;")                                                                                                  \
    static_oop_field(Value, ILLEGAL, "Ljdk/vm/ci/meta/AllocatableValue;")                                                                                      \
  end_class                                                                                                                                                    \
  start_class(RegisterValue)                                                                                                                                   \
    oop_field(RegisterValue, reg, "Ljdk/vm/ci/code/Register;")                                                                                                 \
  end_class                                                                                                                                                    \
  start_class(code_Location)                                                                                                                                   \
    oop_field(code_Location, reg, "Ljdk/vm/ci/code/Register;")                                                                                                 \
    int_field(code_Location, offset)                                                                                                                           \
  end_class                                                                                                                                                    \
  start_class(code_Register)                                                                                                                                   \
    int_field(code_Register, number)                                                                                                                           \
    int_field(code_Register, encoding)                                                                                                                         \
  end_class                                                                                                                                                    \
  start_class(StackSlot)                                                                                                                                       \
    int_field(StackSlot, offset)                                                                                                                               \
    boolean_field(StackSlot, addFrameSize)                                                                                                                     \
  end_class                                                                                                                                                    \
  start_class(VirtualObject)                                                                                                                                   \
    int_field(VirtualObject, id)                                                                                                                               \
    oop_field(VirtualObject, type, "Ljdk/vm/ci/meta/ResolvedJavaType;")                                                                                        \
    objArrayOop_field(VirtualObject, values, "[Ljdk/vm/ci/meta/JavaValue;")                                                                                    \
    objArrayOop_field(VirtualObject, slotKinds, "[Ljdk/vm/ci/meta/JavaKind;")                                                                                  \
  end_class                                                                                                                                                    \
  start_class(StackLockValue)                                                                                                                                  \
    oop_field(StackLockValue, owner, "Ljdk/vm/ci/meta/JavaValue;")                                                                                             \
    oop_field(StackLockValue, slot, "Ljdk/vm/ci/meta/AllocatableValue;")                                                                                       \
    boolean_field(StackLockValue, eliminated)                                                                                                                  \
  end_class                                                                                                                                                    \
  start_class(HotSpotSpeculationLog)                                                                                                                           \
    long_field(HotSpotSpeculationLog, lastFailed)                                                                                                              \
  end_class                                                                                                                                                    \
  start_class(HotSpotStackFrameReference)                                                                                                                      \
    oop_field(HotSpotStackFrameReference, compilerToVM, "Ljdk/vm/ci/hotspot/CompilerToVM;")                                                                    \
    boolean_field(HotSpotStackFrameReference, objectsMaterialized)                                                                                             \
    long_field(HotSpotStackFrameReference, stackPointer)                                                                                                       \
    int_field(HotSpotStackFrameReference, frameNumber)                                                                                                         \
    int_field(HotSpotStackFrameReference, bci)                                                                                                                 \
    oop_field(HotSpotStackFrameReference, method, "Ljdk/vm/ci/hotspot/HotSpotResolvedJavaMethod;")                                                             \
    objArrayOop_field(HotSpotStackFrameReference, locals, "[Ljava/lang/Object;")                                                                               \
    typeArrayOop_field(HotSpotStackFrameReference, localIsVirtual, "[Z")                                                                                       \
  end_class                                                                                                                                                    \
  start_class(HotSpotMetaData)                                                                                                                                 \
    typeArrayOop_field(HotSpotMetaData, pcDescBytes, "[B")                                                                                                     \
    typeArrayOop_field(HotSpotMetaData, scopesDescBytes, "[B")                                                                                                 \
    typeArrayOop_field(HotSpotMetaData, relocBytes, "[B")                                                                                                      \
    typeArrayOop_field(HotSpotMetaData, exceptionBytes, "[B")                                                                                                  \
    typeArrayOop_field(HotSpotMetaData, oopMaps, "[B")                                                                                                         \
    objArrayOop_field(HotSpotMetaData, metadata, "[Ljava/lang/Object;")                                                                                        \
  end_class                                                                                                                                                    \
  start_class(HotSpotConstantPool)                                                                                                                             \
    long_field(HotSpotConstantPool, metaspaceConstantPool)                                                                                                     \
  end_class                                                                                                                                                    \
  start_class(HotSpotJVMCIRuntime)                                                                                                                             \
    int_field(HotSpotJVMCIRuntime, compilationLevelAdjustment)                                                                                                 \
  end_class                                                                                                                                                    \
  /* end*/

#define START_CLASS(name)                                                                                                                                      \
class name : AllStatic {                                                                                                                                       \
  private:                                                                                                                                                     \
    friend class JVMCICompiler;                                                                                                                                \
    static void check(oop obj, const char* field_name, int offset);                                                                                            \
    static void compute_offsets(TRAPS);                                                                                                                        \
  public:                                                                                                                                                      \
    static InstanceKlass* klass() { return SystemDictionary::name##_klass(); }

#define END_CLASS };

#define FIELD(name, type, accessor, cast)                                                                                                                         \
    static int _##name##_offset;                                                                                                                                  \
    static type name(oop obj)                   { check(obj, #name, _##name##_offset); return cast obj->accessor(_##name##_offset); }                             \
    static type name(Handle obj)                { check(obj(), #name, _##name##_offset); return cast obj->accessor(_##name##_offset); }                           \
    static type name(jobject obj);                                                                                                                                \
    static void set_##name(oop obj, type x)     { check(obj, #name, _##name##_offset); obj->accessor##_put(_##name##_offset, x); }                                \
    static void set_##name(Handle obj, type x)  { check(obj(), #name, _##name##_offset); obj->accessor##_put(_##name##_offset, x); }                              \
    static void set_##name(jobject obj, type x);                                                                                                                  \

#define EMPTY_CAST
#define CHAR_FIELD(klass, name) FIELD(name, jchar, char_field, EMPTY_CAST)
#define INT_FIELD(klass, name) FIELD(name, jint, int_field, EMPTY_CAST)
#define BOOLEAN_FIELD(klass, name) FIELD(name, jboolean, bool_field, EMPTY_CAST)
#define LONG_FIELD(klass, name) FIELD(name, jlong, long_field, EMPTY_CAST)
#define FLOAT_FIELD(klass, name) FIELD(name, jfloat, float_field, EMPTY_CAST)
#define OOP_FIELD(klass, name, signature) FIELD(name, oop, obj_field, EMPTY_CAST)
#define OBJARRAYOOP_FIELD(klass, name, signature) FIELD(name, objArrayOop, obj_field, (objArrayOop))
#define TYPEARRAYOOP_FIELD(klass, name, signature) FIELD(name, typeArrayOop, obj_field, (typeArrayOop))
#define STATIC_OOP_FIELD(klassName, name, signature) STATIC_OOPISH_FIELD(klassName, name, oop, signature)
#define STATIC_OBJARRAYOOP_FIELD(klassName, name, signature) STATIC_OOPISH_FIELD(klassName, name, objArrayOop, signature)
#define STATIC_OOPISH_FIELD(klassName, name, type, signature)                                                  \
    static int _##name##_offset;                                                                               \
    static type name();                                                                                        \
    static void set_##name(type x);
#define STATIC_PRIMITIVE_FIELD(klassName, name, jtypename)                                                     \
    static int _##name##_offset;                                                                               \
    static jtypename name();                                                                                   \
    static void set_##name(jtypename x);

#define STATIC_INT_FIELD(klassName, name) STATIC_PRIMITIVE_FIELD(klassName, name, jint)
#define STATIC_BOOLEAN_FIELD(klassName, name) STATIC_PRIMITIVE_FIELD(klassName, name, jboolean)

COMPILER_CLASSES_DO(START_CLASS, END_CLASS, CHAR_FIELD, INT_FIELD, BOOLEAN_FIELD, LONG_FIELD, FLOAT_FIELD, OOP_FIELD, TYPEARRAYOOP_FIELD, OBJARRAYOOP_FIELD, STATIC_OOP_FIELD, STATIC_OBJARRAYOOP_FIELD, STATIC_INT_FIELD, STATIC_BOOLEAN_FIELD)
#undef START_CLASS
#undef END_CLASS
#undef FIELD
#undef CHAR_FIELD
#undef INT_FIELD
#undef BOOLEAN_FIELD
#undef LONG_FIELD
#undef FLOAT_FIELD
#undef OOP_FIELD
#undef TYPEARRAYOOP_FIELD
#undef OBJARRAYOOP_FIELD
#undef STATIC_OOPISH_FIELD
#undef STATIC_OOP_FIELD
#undef STATIC_OBJARRAYOOP_FIELD
#undef STATIC_INT_FIELD
#undef STATIC_BOOLEAN_FIELD
#undef STATIC_PRIMITIVE_FIELD
#undef EMPTY_CAST

void compute_offset(int &dest_offset, Klass* klass, const char* name, const char* signature, bool static_field, TRAPS);

#endif // SHARE_VM_JVMCI_JVMCIJAVACLASSES_HPP

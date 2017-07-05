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

#ifndef SHARE_VM_JVMCI_JVMCIJAVACLASSES_HPP
#define SHARE_VM_JVMCI_JVMCIJAVACLASSES_HPP

#include "classfile/systemDictionary.hpp"
#include "oops/instanceMirrorKlass.hpp"

class JVMCIJavaClasses : AllStatic {
 public:
  static void compute_offsets(TRAPS);
};

/* This macro defines the structure of the CompilationResult - classes.
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
    objArrayOop_field(HotSpotCompiledCode, sites, "[Ljdk/vm/ci/code/CompilationResult$Site;")                                                                  \
    objArrayOop_field(HotSpotCompiledCode, exceptionHandlers, "[Ljdk/vm/ci/code/CompilationResult$ExceptionHandler;")                                          \
    objArrayOop_field(HotSpotCompiledCode, comments, "[Ljdk/vm/ci/hotspot/HotSpotCompiledCode$Comment;")                                                       \
    objArrayOop_field(HotSpotCompiledCode, assumptions, "[Ljdk/vm/ci/meta/Assumptions$Assumption;")                                                            \
    typeArrayOop_field(HotSpotCompiledCode, targetCode, "[B")                                                                                                  \
    int_field(HotSpotCompiledCode, targetCodeSize)                                                                                                             \
    typeArrayOop_field(HotSpotCompiledCode, dataSection, "[B")                                                                                                 \
    int_field(HotSpotCompiledCode, dataSectionAlignment)                                                                                                       \
    objArrayOop_field(HotSpotCompiledCode, dataSectionPatches, "[Ljdk/vm/ci/code/CompilationResult$DataPatch;")                                                \
    boolean_field(HotSpotCompiledCode, isImmutablePIC)                                                                                                         \
    int_field(HotSpotCompiledCode, totalFrameSize)                                                                                                             \
    int_field(HotSpotCompiledCode, customStackAreaOffset)                                                                                                      \
    objArrayOop_field(HotSpotCompiledCode, methods, "[Ljdk/vm/ci/meta/ResolvedJavaMethod;")                                                                    \
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
    oop_field(Assumptions_CallSiteTargetValue, callSite, "Ljava/lang/invoke/CallSite;")                                                                        \
    oop_field(Assumptions_CallSiteTargetValue, methodHandle, "Ljava/lang/invoke/MethodHandle;")                                                                \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_Site)                                                                                                                          \
    int_field(CompilationResult_Site, pcOffset)                                                                                                                \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_Call)                                                                                                                          \
    oop_field(CompilationResult_Call, target, "Ljdk/vm/ci/meta/InvokeTarget;")                                                                                 \
    oop_field(CompilationResult_Call, debugInfo, "Ljdk/vm/ci/code/DebugInfo;")                                                                                 \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_DataPatch)                                                                                                                     \
    oop_field(CompilationResult_DataPatch, reference, "Ljdk/vm/ci/code/CompilationResult$Reference;")                                                          \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_ConstantReference)                                                                                                             \
    oop_field(CompilationResult_ConstantReference, constant, "Ljdk/vm/ci/meta/VMConstant;")                                                                    \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_DataSectionReference)                                                                                                          \
    int_field(CompilationResult_DataSectionReference, offset)                                                                                                  \
  end_class                                                                                                                                                    \
  start_class(InfopointReason)                                                                                                                                 \
    static_oop_field(InfopointReason, SAFEPOINT, "Ljdk/vm/ci/code/InfopointReason;")                                                                           \
    static_oop_field(InfopointReason, CALL, "Ljdk/vm/ci/code/InfopointReason;")                                                                                \
    static_oop_field(InfopointReason, IMPLICIT_EXCEPTION, "Ljdk/vm/ci/code/InfopointReason;")                                                                  \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_Infopoint)                                                                                                                     \
    oop_field(CompilationResult_Infopoint, debugInfo, "Ljdk/vm/ci/code/DebugInfo;")                                                                            \
    oop_field(CompilationResult_Infopoint, reason, "Ljdk/vm/ci/code/InfopointReason;")                                                                         \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_ExceptionHandler)                                                                                                              \
    int_field(CompilationResult_ExceptionHandler, handlerPos)                                                                                                  \
  end_class                                                                                                                                                    \
  start_class(CompilationResult_Mark)                                                                                                                          \
    oop_field(CompilationResult_Mark, id, "Ljava/lang/Object;")                                                                                                \
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
    static_int_field(BytecodeFrame, BEFORE_BCI)                                                                                                                \
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
    oop_field(HotSpotMetaspaceConstantImpl, metaspaceObject, "Ljdk/vm/ci/hotspot/MetaspaceWrapperObject;")                                            \
    boolean_field(HotSpotMetaspaceConstantImpl, compressed)                                                                                                    \
  end_class                                                                                                                                                    \
  start_class(HotSpotSentinelConstant)                                                                                                                         \
  end_class                                                                                                                                                    \
  start_class(JavaKind)                                                                                                                                        \
    char_field(JavaKind, typeChar)                                                                                                                             \
    static_oop_field(JavaKind, Boolean, "Ljdk/vm/ci/meta/JavaKind;");                                                                                          \
    static_oop_field(JavaKind, Byte, "Ljdk/vm/ci/meta/JavaKind;");                                                                                             \
    static_oop_field(JavaKind, Char, "Ljdk/vm/ci/meta/JavaKind;");                                                                                             \
    static_oop_field(JavaKind, Short, "Ljdk/vm/ci/meta/JavaKind;");                                                                                            \
    static_oop_field(JavaKind, Int, "Ljdk/vm/ci/meta/JavaKind;");                                                                                              \
    static_oop_field(JavaKind, Long, "Ljdk/vm/ci/meta/JavaKind;");                                                                                             \
  end_class                                                                                                                                                    \
  start_class(LIRKind)                                                                                                                                         \
    oop_field(LIRKind, platformKind, "Ljdk/vm/ci/meta/PlatformKind;")                                                                                          \
    int_field(LIRKind, referenceMask)                                                                                                                          \
  end_class                                                                                                                                                    \
  start_class(Value)                                                                                                                                           \
    oop_field(Value, lirKind, "Ljdk/vm/ci/meta/LIRKind;")                                                                                                      \
    static_oop_field(Value, ILLEGAL, "Ljdk/vm/ci/meta/AllocatableValue;");                                                                                     \
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
    oop_field(HotSpotSpeculationLog, lastFailed, "Ljava/lang/Object;")                                                                                         \
  end_class                                                                                                                                                    \
  start_class(HotSpotStackFrameReference)                                                                                                                      \
    oop_field(HotSpotStackFrameReference, compilerToVM, "Ljdk/vm/ci/hotspot/CompilerToVM;")                                                                    \
    long_field(HotSpotStackFrameReference, stackPointer)                                                                                                       \
    int_field(HotSpotStackFrameReference, frameNumber)                                                                                                         \
    int_field(HotSpotStackFrameReference, bci)                                                                                                                 \
    oop_field(HotSpotStackFrameReference, method, "Ljdk/vm/ci/hotspot/HotSpotResolvedJavaMethod;")                                                             \
    objArrayOop_field(HotSpotStackFrameReference, locals, "[Ljava/lang/Object;")                                                                               \
    typeArrayOop_field(HotSpotStackFrameReference, localIsVirtual, "[Z")                                                                                       \
  end_class                                                                                                                                                    \
  start_class(HotSpotMetaData) \
    typeArrayOop_field(HotSpotMetaData, pcDescBytes, "[B") \
    typeArrayOop_field(HotSpotMetaData, scopesDescBytes, "[B") \
    typeArrayOop_field(HotSpotMetaData, relocBytes, "[B") \
    typeArrayOop_field(HotSpotMetaData, exceptionBytes, "[B") \
    typeArrayOop_field(HotSpotMetaData, oopMaps, "[B") \
    objArrayOop_field(HotSpotMetaData, metadata, "[Ljava/lang/String;") \
  end_class \
  start_class(HotSpotOopMap) \
    int_field(HotSpotOopMap, offset) \
    int_field(HotSpotOopMap, count) \
    typeArrayOop_field(HotSpotOopMap, data, "[B") \
  end_class                                                                                                                                                    \
  start_class(HotSpotConstantPool)                                                                                                                             \
    long_field(HotSpotConstantPool, metaspaceConstantPool)                                                                                                     \
  end_class                                                                                                                                                    \
  start_class(HotSpotJVMCIRuntime)                                                                                                                             \
  objArrayOop_field(HotSpotJVMCIRuntime, trivialPrefixes, "[Ljava/lang/String;")                                                                               \
  end_class                                                                                                                                                    \
  /* end*/

#define START_CLASS(name)                                                                                                                                      \
class name : AllStatic {                                                                                                                                       \
  private:                                                                                                                                                     \
    friend class JVMCICompiler;                                                                                                                                \
    static void check(oop obj, const char* field_name, int offset) {                                                                                           \
        assert(obj != NULL, "NULL field access of %s.%s", #name, field_name);                                                                                  \
        assert(obj->is_a(SystemDictionary::name##_klass()), "wrong class, " #name " expected, found %s", obj->klass()->external_name());                       \
        assert(offset != 0, "must be valid offset");                                                                                                           \
    }                                                                                                                                                          \
    static void compute_offsets(TRAPS);                                                                                                                             \
  public:                                                                                                                                                      \
    static InstanceKlass* klass() { return SystemDictionary::name##_klass(); }

#define END_CLASS };

#define FIELD(name, type, accessor, cast)                                                                                                                         \
    static int _##name##_offset;                                                                                                                                  \
    static type name(oop obj)                   { check(obj, #name, _##name##_offset); return cast obj->accessor(_##name##_offset); }                                               \
    static type name(Handle obj)                { check(obj(), #name, _##name##_offset); return cast obj->accessor(_##name##_offset); }                                             \
    static type name(jobject obj)               { check(JNIHandles::resolve(obj), #name, _##name##_offset); return cast JNIHandles::resolve(obj)->accessor(_##name##_offset); }     \
    static void set_##name(oop obj, type x)     { check(obj, #name, _##name##_offset); obj->accessor##_put(_##name##_offset, x); }                                                  \
    static void set_##name(Handle obj, type x)  { check(obj(), #name, _##name##_offset); obj->accessor##_put(_##name##_offset, x); }                                                \
    static void set_##name(jobject obj, type x) { check(JNIHandles::resolve(obj), #name, _##name##_offset); JNIHandles::resolve(obj)->accessor##_put(_##name##_offset, x); }

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
    static type name() {                                                                                       \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      InstanceKlass* ik = klassName::klass();                                                                  \
      address addr = ik->static_field_addr(_##name##_offset - InstanceMirrorKlass::offset_of_static_fields()); \
      if (UseCompressedOops) {                                                                                 \
        return (type) oopDesc::load_decode_heap_oop((narrowOop *)addr);                                        \
      } else {                                                                                                 \
        return (type) oopDesc::load_decode_heap_oop((oop*)addr);                                               \
      }                                                                                                        \
    }                                                                                                          \
    static void set_##name(type x) {                                                                           \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      assert(klassName::klass() != NULL, "Class not yet loaded: " #klassName);                                 \
      InstanceKlass* ik = klassName::klass();                                                                  \
      address addr = ik->static_field_addr(_##name##_offset - InstanceMirrorKlass::offset_of_static_fields()); \
      if (UseCompressedOops) {                                                                                 \
        oop_store((narrowOop *)addr, x);                                                                       \
      } else {                                                                                                 \
        oop_store((oop*)addr, x);                                                                              \
      }                                                                                                        \
    }
#define STATIC_PRIMITIVE_FIELD(klassName, name, jtypename)                                                     \
    static int _##name##_offset;                                                                               \
    static jtypename name() {                                                                                  \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      InstanceKlass* ik = klassName::klass();                                                                  \
      address addr = ik->static_field_addr(_##name##_offset - InstanceMirrorKlass::offset_of_static_fields()); \
      return *((jtypename *)addr);                                                                             \
    }                                                                                                          \
    static void set_##name(jtypename x) {                                                                      \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      InstanceKlass* ik = klassName::klass();                                                                  \
      address addr = ik->static_field_addr(_##name##_offset - InstanceMirrorKlass::offset_of_static_fields()); \
      *((jtypename *)addr) = x;                                                                                \
    }

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
#undef EMPTY_CAST

void compute_offset(int &dest_offset, Klass* klass, const char* name, const char* signature, bool static_field, TRAPS);

#endif // SHARE_VM_JVMCI_JVMCIJAVACLASSES_HPP

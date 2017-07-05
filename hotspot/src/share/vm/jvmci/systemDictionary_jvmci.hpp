/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JVMCI_SYSTEMDICTIONARY_JVMCI_HPP
#define SHARE_VM_JVMCI_SYSTEMDICTIONARY_JVMCI_HPP

#if !INCLUDE_JVMCI
#define JVMCI_WK_KLASSES_DO(do_klass)
#else
#define JVMCI_WK_KLASSES_DO(do_klass)                                                                                           \
  /* JVMCI classes. These are loaded on-demand. */                                                                              \
  do_klass(HotSpotCompiledCode_klass,                    jdk_vm_ci_hotspot_HotSpotCompiledCode,                 Jvmci) \
  do_klass(HotSpotCompiledCode_Comment_klass,            jdk_vm_ci_hotspot_HotSpotCompiledCode_Comment,         Jvmci) \
  do_klass(HotSpotCompiledNmethod_klass,                 jdk_vm_ci_hotspot_HotSpotCompiledNmethod,              Jvmci) \
  do_klass(HotSpotForeignCallTarget_klass,               jdk_vm_ci_hotspot_HotSpotForeignCallTarget,            Jvmci) \
  do_klass(HotSpotReferenceMap_klass,                    jdk_vm_ci_hotspot_HotSpotReferenceMap,                 Jvmci) \
  do_klass(HotSpotInstalledCode_klass,                   jdk_vm_ci_hotspot_HotSpotInstalledCode,                Jvmci) \
  do_klass(HotSpotNmethod_klass,                         jdk_vm_ci_hotspot_HotSpotNmethod,                      Jvmci) \
  do_klass(HotSpotResolvedJavaMethodImpl_klass,          jdk_vm_ci_hotspot_HotSpotResolvedJavaMethodImpl,       Jvmci) \
  do_klass(HotSpotResolvedObjectTypeImpl_klass,          jdk_vm_ci_hotspot_HotSpotResolvedObjectTypeImpl,       Jvmci) \
  do_klass(HotSpotCompressedNullConstant_klass,          jdk_vm_ci_hotspot_HotSpotCompressedNullConstant,       Jvmci) \
  do_klass(HotSpotObjectConstantImpl_klass,              jdk_vm_ci_hotspot_HotSpotObjectConstantImpl,           Jvmci) \
  do_klass(HotSpotMetaspaceConstantImpl_klass,           jdk_vm_ci_hotspot_HotSpotMetaspaceConstantImpl,        Jvmci) \
  do_klass(HotSpotSentinelConstant_klass,                jdk_vm_ci_hotspot_HotSpotSentinelConstant,             Jvmci) \
  do_klass(HotSpotStackFrameReference_klass,             jdk_vm_ci_hotspot_HotSpotStackFrameReference,          Jvmci) \
  do_klass(HotSpotMetaData_klass,                        jdk_vm_ci_hotspot_HotSpotMetaData,                     Jvmci) \
  do_klass(HotSpotOopMap_klass,                          jdk_vm_ci_hotspot_HotSpotOopMap,                       Jvmci) \
  do_klass(HotSpotConstantPool_klass,                    jdk_vm_ci_hotspot_HotSpotConstantPool,                 Jvmci) \
  do_klass(HotSpotJVMCIMetaAccessContext_klass,          jdk_vm_ci_hotspot_HotSpotJVMCIMetaAccessContext,       Jvmci) \
  do_klass(HotSpotJVMCIRuntime_klass,                    jdk_vm_ci_hotspot_HotSpotJVMCIRuntime,                 Jvmci) \
  do_klass(HotSpotSpeculationLog_klass,                  jdk_vm_ci_hotspot_HotSpotSpeculationLog,               Jvmci) \
  do_klass(HotSpotSymbol_klass,                          jdk_vm_ci_hotspot_HotSpotSymbol,                       Jvmci) \
  do_klass(Assumptions_ConcreteMethod_klass,             jdk_vm_ci_meta_Assumptions_ConcreteMethod,             Jvmci) \
  do_klass(Assumptions_NoFinalizableSubclass_klass,      jdk_vm_ci_meta_Assumptions_NoFinalizableSubclass,      Jvmci) \
  do_klass(Assumptions_ConcreteSubtype_klass,            jdk_vm_ci_meta_Assumptions_ConcreteSubtype,            Jvmci) \
  do_klass(Assumptions_LeafType_klass,                   jdk_vm_ci_meta_Assumptions_LeafType,                   Jvmci) \
  do_klass(Assumptions_CallSiteTargetValue_klass,        jdk_vm_ci_meta_Assumptions_CallSiteTargetValue,        Jvmci) \
  do_klass(Architecture_klass,                           jdk_vm_ci_code_Architecture,                           Jvmci) \
  do_klass(TargetDescription_klass,                      jdk_vm_ci_code_TargetDescription,                      Jvmci) \
  do_klass(BytecodePosition_klass,                       jdk_vm_ci_code_BytecodePosition,                       Jvmci) \
  do_klass(DebugInfo_klass,                              jdk_vm_ci_code_DebugInfo,                              Jvmci) \
  do_klass(RegisterSaveLayout_klass,                     jdk_vm_ci_code_RegisterSaveLayout,                     Jvmci) \
  do_klass(BytecodeFrame_klass,                          jdk_vm_ci_code_BytecodeFrame,                          Jvmci) \
  do_klass(CompilationRequestResult_klass,               jdk_vm_ci_code_CompilationRequestResult,               Jvmci) \
  do_klass(InstalledCode_klass,                          jdk_vm_ci_code_InstalledCode,                          Jvmci) \
  do_klass(code_Location_klass,                          jdk_vm_ci_code_Location,                               Jvmci) \
  do_klass(code_Register_klass,                          jdk_vm_ci_code_Register,                               Jvmci) \
  do_klass(RegisterValue_klass,                          jdk_vm_ci_code_RegisterValue,                          Jvmci) \
  do_klass(StackSlot_klass,                              jdk_vm_ci_code_StackSlot,                              Jvmci) \
  do_klass(StackLockValue_klass,                         jdk_vm_ci_code_StackLockValue,                         Jvmci) \
  do_klass(VirtualObject_klass,                          jdk_vm_ci_code_VirtualObject,                          Jvmci) \
  do_klass(site_Call_klass,                              jdk_vm_ci_code_site_Call,                              Jvmci) \
  do_klass(site_ConstantReference_klass,                 jdk_vm_ci_code_site_ConstantReference,                 Jvmci) \
  do_klass(site_DataPatch_klass,                         jdk_vm_ci_code_site_DataPatch,                         Jvmci) \
  do_klass(site_DataSectionReference_klass,              jdk_vm_ci_code_site_DataSectionReference,              Jvmci) \
  do_klass(site_ExceptionHandler_klass,                  jdk_vm_ci_code_site_ExceptionHandler,                  Jvmci) \
  do_klass(site_Mark_klass,                              jdk_vm_ci_code_site_Mark,                              Jvmci) \
  do_klass(site_Infopoint_klass,                         jdk_vm_ci_code_site_Infopoint,                         Jvmci) \
  do_klass(site_Site_klass,                              jdk_vm_ci_code_site_Site,                              Jvmci) \
  do_klass(site_InfopointReason_klass,                   jdk_vm_ci_code_site_InfopointReason,                   Jvmci) \
  do_klass(JavaConstant_klass,                           jdk_vm_ci_meta_JavaConstant,                           Jvmci) \
  do_klass(PrimitiveConstant_klass,                      jdk_vm_ci_meta_PrimitiveConstant,                      Jvmci) \
  do_klass(RawConstant_klass,                            jdk_vm_ci_meta_RawConstant,                            Jvmci) \
  do_klass(NullConstant_klass,                           jdk_vm_ci_meta_NullConstant,                           Jvmci) \
  do_klass(ExceptionHandler_klass,                       jdk_vm_ci_meta_ExceptionHandler,                       Jvmci) \
  do_klass(JavaKind_klass,                               jdk_vm_ci_meta_JavaKind,                               Jvmci) \
  do_klass(LIRKind_klass,                                jdk_vm_ci_meta_LIRKind,                                Jvmci) \
  do_klass(Value_klass,                                  jdk_vm_ci_meta_Value,                                  Jvmci)
#endif

#endif // SHARE_VM_JVMCI_SYSTEMDICTIONARY_JVMCI_HPP

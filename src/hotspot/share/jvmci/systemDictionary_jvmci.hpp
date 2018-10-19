/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#define JVMCI_WK_KLASSES_DO(do_klass)                                                                      \
  /* JVMCI classes. These are loaded on-demand. */                                                         \
  do_klass(JVMCI_klass,                                  jdk_vm_ci_runtime_JVMCI                          ) \
  do_klass(HotSpotCompiledCode_klass,                    jdk_vm_ci_hotspot_HotSpotCompiledCode            ) \
  do_klass(HotSpotCompiledCode_Comment_klass,            jdk_vm_ci_hotspot_HotSpotCompiledCode_Comment    ) \
  do_klass(HotSpotCompiledNmethod_klass,                 jdk_vm_ci_hotspot_HotSpotCompiledNmethod         ) \
  do_klass(HotSpotForeignCallTarget_klass,               jdk_vm_ci_hotspot_HotSpotForeignCallTarget       ) \
  do_klass(HotSpotReferenceMap_klass,                    jdk_vm_ci_hotspot_HotSpotReferenceMap            ) \
  do_klass(HotSpotInstalledCode_klass,                   jdk_vm_ci_hotspot_HotSpotInstalledCode           ) \
  do_klass(HotSpotNmethod_klass,                         jdk_vm_ci_hotspot_HotSpotNmethod                 ) \
  do_klass(HotSpotResolvedJavaMethodImpl_klass,          jdk_vm_ci_hotspot_HotSpotResolvedJavaMethodImpl  ) \
  do_klass(HotSpotResolvedObjectTypeImpl_klass,          jdk_vm_ci_hotspot_HotSpotResolvedObjectTypeImpl  ) \
  do_klass(HotSpotCompressedNullConstant_klass,          jdk_vm_ci_hotspot_HotSpotCompressedNullConstant  ) \
  do_klass(HotSpotObjectConstantImpl_klass,              jdk_vm_ci_hotspot_HotSpotObjectConstantImpl      ) \
  do_klass(HotSpotMetaspaceConstantImpl_klass,           jdk_vm_ci_hotspot_HotSpotMetaspaceConstantImpl   ) \
  do_klass(HotSpotSentinelConstant_klass,                jdk_vm_ci_hotspot_HotSpotSentinelConstant        ) \
  do_klass(HotSpotStackFrameReference_klass,             jdk_vm_ci_hotspot_HotSpotStackFrameReference     ) \
  do_klass(HotSpotMetaData_klass,                        jdk_vm_ci_hotspot_HotSpotMetaData                ) \
  do_klass(HotSpotConstantPool_klass,                    jdk_vm_ci_hotspot_HotSpotConstantPool            ) \
  do_klass(HotSpotJVMCIMetaAccessContext_klass,          jdk_vm_ci_hotspot_HotSpotJVMCIMetaAccessContext  ) \
  do_klass(HotSpotJVMCIRuntime_klass,                    jdk_vm_ci_hotspot_HotSpotJVMCIRuntime            ) \
  do_klass(HotSpotSpeculationLog_klass,                  jdk_vm_ci_hotspot_HotSpotSpeculationLog          ) \
  do_klass(HotSpotCompilationRequestResult_klass,        jdk_vm_ci_hotspot_HotSpotCompilationRequestResult) \
  do_klass(VMField_klass,                                jdk_vm_ci_hotspot_VMField                        ) \
  do_klass(VMFlag_klass,                                 jdk_vm_ci_hotspot_VMFlag                         ) \
  do_klass(VMIntrinsicMethod_klass,                      jdk_vm_ci_hotspot_VMIntrinsicMethod              ) \
  do_klass(Assumptions_ConcreteMethod_klass,             jdk_vm_ci_meta_Assumptions_ConcreteMethod        ) \
  do_klass(Assumptions_NoFinalizableSubclass_klass,      jdk_vm_ci_meta_Assumptions_NoFinalizableSubclass ) \
  do_klass(Assumptions_ConcreteSubtype_klass,            jdk_vm_ci_meta_Assumptions_ConcreteSubtype       ) \
  do_klass(Assumptions_LeafType_klass,                   jdk_vm_ci_meta_Assumptions_LeafType              ) \
  do_klass(Assumptions_CallSiteTargetValue_klass,        jdk_vm_ci_meta_Assumptions_CallSiteTargetValue   ) \
  do_klass(Architecture_klass,                           jdk_vm_ci_code_Architecture                      ) \
  do_klass(TargetDescription_klass,                      jdk_vm_ci_code_TargetDescription                 ) \
  do_klass(BytecodePosition_klass,                       jdk_vm_ci_code_BytecodePosition                  ) \
  do_klass(DebugInfo_klass,                              jdk_vm_ci_code_DebugInfo                         ) \
  do_klass(RegisterSaveLayout_klass,                     jdk_vm_ci_code_RegisterSaveLayout                ) \
  do_klass(BytecodeFrame_klass,                          jdk_vm_ci_code_BytecodeFrame                     ) \
  do_klass(InstalledCode_klass,                          jdk_vm_ci_code_InstalledCode                     ) \
  do_klass(code_Location_klass,                          jdk_vm_ci_code_Location                          ) \
  do_klass(code_Register_klass,                          jdk_vm_ci_code_Register                          ) \
  do_klass(RegisterValue_klass,                          jdk_vm_ci_code_RegisterValue                     ) \
  do_klass(StackSlot_klass,                              jdk_vm_ci_code_StackSlot                         ) \
  do_klass(StackLockValue_klass,                         jdk_vm_ci_code_StackLockValue                    ) \
  do_klass(VirtualObject_klass,                          jdk_vm_ci_code_VirtualObject                     ) \
  do_klass(site_Call_klass,                              jdk_vm_ci_code_site_Call                         ) \
  do_klass(site_ConstantReference_klass,                 jdk_vm_ci_code_site_ConstantReference            ) \
  do_klass(site_DataPatch_klass,                         jdk_vm_ci_code_site_DataPatch                    ) \
  do_klass(site_DataSectionReference_klass,              jdk_vm_ci_code_site_DataSectionReference         ) \
  do_klass(site_ExceptionHandler_klass,                  jdk_vm_ci_code_site_ExceptionHandler             ) \
  do_klass(site_Mark_klass,                              jdk_vm_ci_code_site_Mark                         ) \
  do_klass(site_Infopoint_klass,                         jdk_vm_ci_code_site_Infopoint                    ) \
  do_klass(site_Site_klass,                              jdk_vm_ci_code_site_Site                         ) \
  do_klass(site_InfopointReason_klass,                   jdk_vm_ci_code_site_InfopointReason              ) \
  do_klass(InspectedFrameVisitor_klass,                  jdk_vm_ci_code_stack_InspectedFrameVisitor       ) \
  do_klass(JavaConstant_klass,                           jdk_vm_ci_meta_JavaConstant                      ) \
  do_klass(PrimitiveConstant_klass,                      jdk_vm_ci_meta_PrimitiveConstant                 ) \
  do_klass(RawConstant_klass,                            jdk_vm_ci_meta_RawConstant                       ) \
  do_klass(NullConstant_klass,                           jdk_vm_ci_meta_NullConstant                      ) \
  do_klass(ExceptionHandler_klass,                       jdk_vm_ci_meta_ExceptionHandler                  ) \
  do_klass(JavaKind_klass,                               jdk_vm_ci_meta_JavaKind                          ) \
  do_klass(ValueKind_klass,                              jdk_vm_ci_meta_ValueKind                         ) \
  do_klass(Value_klass,                                  jdk_vm_ci_meta_Value                             )
#endif

#endif // SHARE_VM_JVMCI_SYSTEMDICTIONARY_JVMCI_HPP

/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"
#include "vmreg_sparc.inline.hpp"

jint CodeInstaller::pd_next_offset(NativeInstruction* inst, jint pc_offset, Handle method, TRAPS) {
  if (inst->is_call() || inst->is_jump()) {
    return pc_offset + NativeCall::instruction_size;
  } else if (inst->is_call_reg()) {
    return pc_offset + NativeCallReg::instruction_size;
  } else if (inst->is_sethi()) {
    return pc_offset + NativeFarCall::instruction_size;
  } else {
    JVMCI_ERROR_0("unsupported type of instruction for call site");
    return 0;
  }
}

void CodeInstaller::pd_patch_OopConstant(int pc_offset, Handle constant, TRAPS) {
  address pc = _instructions->start() + pc_offset;
  Handle obj(THREAD, HotSpotObjectConstantImpl::object(constant));
  jobject value = JNIHandles::make_local(obj());
  if (HotSpotObjectConstantImpl::compressed(constant)) {
    int oop_index = _oop_recorder->find_index(value);
    RelocationHolder rspec = oop_Relocation::spec(oop_index);
    _instructions->relocate(pc, rspec, 1);
  } else {
    NativeMovConstReg* move = nativeMovConstReg_at(pc);
    move->set_data((intptr_t) value);

    // We need two relocations:  one on the sethi and one on the add.
    int oop_index = _oop_recorder->find_index(value);
    RelocationHolder rspec = oop_Relocation::spec(oop_index);
    _instructions->relocate(pc + NativeMovConstReg::sethi_offset, rspec);
    _instructions->relocate(pc + NativeMovConstReg::add_offset, rspec);
  }
}

void CodeInstaller::pd_patch_MetaspaceConstant(int pc_offset, Handle constant, TRAPS) {
  address pc = _instructions->start() + pc_offset;
  if (HotSpotMetaspaceConstantImpl::compressed(constant)) {
    NativeMovConstReg32* move = nativeMovConstReg32_at(pc);
    narrowKlass narrowOop = record_narrow_metadata_reference(_instructions, pc, constant, CHECK);
    move->set_data((intptr_t)narrowOop);
    TRACE_jvmci_3("relocating (narrow metaspace constant) at " PTR_FORMAT "/0x%x", p2i(pc), narrowOop);
  } else {
    NativeMovConstReg* move = nativeMovConstReg_at(pc);
    void* reference = record_metadata_reference(_instructions, pc, constant, CHECK);
    move->set_data((intptr_t)reference);
    TRACE_jvmci_3("relocating (metaspace constant) at " PTR_FORMAT "/" PTR_FORMAT, p2i(pc), p2i(reference));
  }
}

void CodeInstaller::pd_patch_DataSectionReference(int pc_offset, int data_offset, TRAPS) {
  address pc = _instructions->start() + pc_offset;
  NativeInstruction* inst = nativeInstruction_at(pc);
  NativeInstruction* inst1 = nativeInstruction_at(pc + 4);
  if(inst->is_sethi() && inst1->is_nop()) {
      address const_start = _constants->start();
      address dest = _constants->start() + data_offset;
      if(_constants_size > 0) {
        _instructions->relocate(pc + NativeMovConstReg::sethi_offset, internal_word_Relocation::spec((address) dest));
        _instructions->relocate(pc + NativeMovConstReg::add_offset, internal_word_Relocation::spec((address) dest));
      }
      TRACE_jvmci_3("relocating at " PTR_FORMAT " (+%d) with destination at %d", p2i(pc), pc_offset, data_offset);
  }else {
    int const_size = align_up(_constants->end()-_constants->start(), CodeEntryAlignment);
    NativeMovRegMem* load = nativeMovRegMem_at(pc);
    // This offset must match with SPARCLoadConstantTableBaseOp.emitCode
    load->set_offset(- (const_size - data_offset + Assembler::min_simm13()));
    TRACE_jvmci_3("relocating ld at " PTR_FORMAT " (+%d) with destination at %d", p2i(pc), pc_offset, data_offset);
  }
}

void CodeInstaller::pd_relocate_ForeignCall(NativeInstruction* inst, jlong foreign_call_destination, TRAPS) {
  address pc = (address) inst;
  if (inst->is_call()) {
    NativeCall* call = nativeCall_at(pc);
    call->set_destination((address) foreign_call_destination);
    _instructions->relocate(call->instruction_address(), runtime_call_Relocation::spec());
  } else if (inst->is_sethi()) {
    NativeJump* jump = nativeJump_at(pc);
    jump->set_jump_destination((address) foreign_call_destination);
    _instructions->relocate(jump->instruction_address(), runtime_call_Relocation::spec());
  } else {
    JVMCI_ERROR("unknown call or jump instruction at " PTR_FORMAT, p2i(pc));
  }
  TRACE_jvmci_3("relocating (foreign call) at " PTR_FORMAT, p2i(inst));
}

void CodeInstaller::pd_relocate_JavaMethod(CodeBuffer &, Handle hotspot_method, jint pc_offset, TRAPS) {
#ifdef ASSERT
  Method* method = NULL;
  // we need to check, this might also be an unresolved method
  if (hotspot_method->is_a(HotSpotResolvedJavaMethodImpl::klass())) {
    method = getMethodFromHotSpotMethod(hotspot_method());
  }
#endif
  switch (_next_call_type) {
    case INLINE_INVOKE:
      break;
    case INVOKEVIRTUAL:
    case INVOKEINTERFACE: {
      assert(method == NULL || !method->is_static(), "cannot call static method with invokeinterface");
      NativeCall* call = nativeCall_at(_instructions->start() + pc_offset);
      call->set_destination(SharedRuntime::get_resolve_virtual_call_stub());
      _instructions->relocate(call->instruction_address(), virtual_call_Relocation::spec(_invoke_mark_pc));
      break;
    }
    case INVOKESTATIC: {
      assert(method == NULL || method->is_static(), "cannot call non-static method with invokestatic");
      NativeCall* call = nativeCall_at(_instructions->start() + pc_offset);
      call->set_destination(SharedRuntime::get_resolve_static_call_stub());
      _instructions->relocate(call->instruction_address(), relocInfo::static_call_type);
      break;
    }
    case INVOKESPECIAL: {
      assert(method == NULL || !method->is_static(), "cannot call static method with invokespecial");
      NativeCall* call = nativeCall_at(_instructions->start() + pc_offset);
      call->set_destination(SharedRuntime::get_resolve_opt_virtual_call_stub());
      _instructions->relocate(call->instruction_address(), relocInfo::opt_virtual_call_type);
      break;
    }
    default:
      JVMCI_ERROR("invalid _next_call_type value");
      break;
  }
}

void CodeInstaller::pd_relocate_poll(address pc, jint mark, TRAPS) {
  switch (mark) {
    case POLL_NEAR:
      JVMCI_ERROR("unimplemented");
      break;
    case POLL_FAR:
      _instructions->relocate(pc, relocInfo::poll_type);
      break;
    case POLL_RETURN_NEAR:
      JVMCI_ERROR("unimplemented");
      break;
    case POLL_RETURN_FAR:
      _instructions->relocate(pc, relocInfo::poll_return_type);
      break;
    default:
      JVMCI_ERROR("invalid mark value");
      break;
  }
}

// convert JVMCI register indices (as used in oop maps) to HotSpot registers
VMReg CodeInstaller::get_hotspot_reg(jint jvmci_reg, TRAPS) {
  // JVMCI Registers are numbered as follows:
  //   0..31: Thirty-two General Purpose registers (CPU Registers)
  //   32..63: Thirty-two single precision float registers
  //   64..95: Thirty-two double precision float registers
  //   96..111: Sixteen quad precision float registers
  if (jvmci_reg < 32) {
    return as_Register(jvmci_reg)->as_VMReg();
  } else {
    jint floatRegisterNumber;
    if(jvmci_reg < 64) { // Single precision
      floatRegisterNumber = jvmci_reg - 32;
    } else if(jvmci_reg < 96) {
      floatRegisterNumber = 2 * (jvmci_reg - 64);
    } else if(jvmci_reg < 112) {
      floatRegisterNumber = 4 * (jvmci_reg - 96);
    } else {
      JVMCI_ERROR_NULL("invalid register number: %d", jvmci_reg);
    }
    return as_FloatRegister(floatRegisterNumber)->as_VMReg();
  }
}

bool CodeInstaller::is_general_purpose_reg(VMReg hotspotRegister) {
  return !hotspotRegister->is_FloatRegister();
}

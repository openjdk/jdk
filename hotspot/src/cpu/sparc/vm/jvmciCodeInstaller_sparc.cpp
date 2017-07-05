/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/sharedRuntime.hpp"
#include "vmreg_sparc.inline.hpp"

jint CodeInstaller::pd_next_offset(NativeInstruction* inst, jint pc_offset, oop method) {
  if (inst->is_call() || inst->is_jump()) {
    return pc_offset + NativeCall::instruction_size;
  } else if (inst->is_call_reg()) {
    return pc_offset + NativeCallReg::instruction_size;
  } else if (inst->is_sethi()) {
    return pc_offset + NativeFarCall::instruction_size;
  } else {
    fatal("unsupported type of instruction for call site");
    return 0;
  }
}

void CodeInstaller::pd_patch_OopConstant(int pc_offset, Handle& constant) {
  address pc = _instructions->start() + pc_offset;
  Handle obj = HotSpotObjectConstantImpl::object(constant);
  jobject value = JNIHandles::make_local(obj());
  if (HotSpotObjectConstantImpl::compressed(constant)) {
#ifdef _LP64
    int oop_index = _oop_recorder->find_index(value);
    RelocationHolder rspec = oop_Relocation::spec(oop_index);
    _instructions->relocate(pc, rspec, 1);
#else
    fatal("compressed oop on 32bit");
#endif
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

void CodeInstaller::pd_patch_DataSectionReference(int pc_offset, int data_offset) {
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
    int const_size = align_size_up(_constants->end()-_constants->start(), CodeEntryAlignment);
    NativeMovRegMem* load = nativeMovRegMem_at(pc);
    // This offset must match with SPARCLoadConstantTableBaseOp.emitCode
    load->set_offset(- (const_size - data_offset + Assembler::min_simm13()));
    TRACE_jvmci_3("relocating ld at " PTR_FORMAT " (+%d) with destination at %d", p2i(pc), pc_offset, data_offset);
  }
}

void CodeInstaller::pd_relocate_CodeBlob(CodeBlob* cb, NativeInstruction* inst) {
  fatal("CodeInstaller::pd_relocate_CodeBlob - sparc unimp");
}

void CodeInstaller::pd_relocate_ForeignCall(NativeInstruction* inst, jlong foreign_call_destination) {
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
    fatal(err_msg("unknown call or jump instruction at " PTR_FORMAT, p2i(pc)));
  }
  TRACE_jvmci_3("relocating (foreign call) at " PTR_FORMAT, p2i(inst));
}

void CodeInstaller::pd_relocate_JavaMethod(oop hotspot_method, jint pc_offset) {
#ifdef ASSERT
  Method* method = NULL;
  // we need to check, this might also be an unresolved method
  if (hotspot_method->is_a(HotSpotResolvedJavaMethodImpl::klass())) {
    method = getMethodFromHotSpotMethod(hotspot_method);
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
      fatal("invalid _next_call_type value");
      break;
  }
}

void CodeInstaller::pd_relocate_poll(address pc, jint mark) {
  switch (mark) {
    case POLL_NEAR:
      fatal("unimplemented");
      break;
    case POLL_FAR:
      _instructions->relocate(pc, relocInfo::poll_type);
      break;
    case POLL_RETURN_NEAR:
      fatal("unimplemented");
      break;
    case POLL_RETURN_FAR:
      _instructions->relocate(pc, relocInfo::poll_return_type);
      break;
    default:
      fatal("invalid mark value");
      break;
  }
}

// convert JVMCI register indices (as used in oop maps) to HotSpot registers
VMReg CodeInstaller::get_hotspot_reg(jint jvmci_reg) {
  if (jvmci_reg < RegisterImpl::number_of_registers) {
    return as_Register(jvmci_reg)->as_VMReg();
  } else {
    jint floatRegisterNumber = jvmci_reg - RegisterImpl::number_of_registers;
    floatRegisterNumber += MAX2(0, floatRegisterNumber-32); // Beginning with f32, only every second register is going to be addressed
    if (floatRegisterNumber < FloatRegisterImpl::number_of_registers) {
      return as_FloatRegister(floatRegisterNumber)->as_VMReg();
    }
    ShouldNotReachHere();
    return NULL;
  }
}

bool CodeInstaller::is_general_purpose_reg(VMReg hotspotRegister) {
  return !hotspotRegister->is_FloatRegister();
}

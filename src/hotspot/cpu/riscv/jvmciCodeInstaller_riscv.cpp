/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "oops/compressedKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_riscv.inline.hpp"

jint CodeInstaller::pd_next_offset(NativeInstruction* inst, jint pc_offset, JVMCI_TRAPS) {
  address pc = (address) inst;
  if (inst->is_call()) {
    return pc_offset + NativeCall::instruction_size;
  } else if (inst->is_jump()) {
    return pc_offset + NativeJump::instruction_size;
  } else if (inst->is_movptr1()) {
    return pc_offset + NativeMovConstReg::movptr1_instruction_size;
  } else if (inst->is_movptr2()) {
    return pc_offset + NativeMovConstReg::movptr2_instruction_size;
  } else {
    JVMCI_ERROR_0("unsupported type of instruction for call site");
  }
}

void CodeInstaller::pd_patch_OopConstant(int pc_offset, Handle& obj, bool compressed, JVMCI_TRAPS) {
  address pc = _instructions->start() + pc_offset;
  jobject value = JNIHandles::make_local(obj());
  MacroAssembler::patch_oop(pc, cast_from_oop<address>(obj()));
  int oop_index = _oop_recorder->find_index(value);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  _instructions->relocate(pc, rspec);
}

void CodeInstaller::pd_patch_MetaspaceConstant(int pc_offset, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS) {
  address pc = _instructions->start() + pc_offset;
  if (tag == PATCH_NARROW_KLASS) {
    narrowKlass narrowOop = record_narrow_metadata_reference(_instructions, pc, stream, tag, JVMCI_CHECK);
    MacroAssembler::pd_patch_instruction_size(pc, (address) (long) narrowOop);
    JVMCI_event_3("relocating (narrow metaspace constant) at " PTR_FORMAT "/0x%x", p2i(pc), narrowOop);
  } else {
    NativeMovConstReg* move = nativeMovConstReg_at(pc);
    void* reference = record_metadata_reference(_instructions, pc, stream, tag, JVMCI_CHECK);
    move->set_data((intptr_t) reference);
    JVMCI_event_3("relocating (metaspace constant) at " PTR_FORMAT "/" PTR_FORMAT, p2i(pc), p2i(reference));
  }
}

void CodeInstaller::pd_patch_DataSectionReference(int pc_offset, int data_offset, JVMCI_TRAPS) {
  address pc = _instructions->start() + pc_offset;
  address dest = _constants->start() + data_offset;
  _instructions->relocate(pc, section_word_Relocation::spec((address) dest, CodeBuffer::SECT_CONSTS));
  JVMCI_event_3("relocating at " PTR_FORMAT " (+%d) with destination at %d", p2i(pc), pc_offset, data_offset);
}

void CodeInstaller::pd_relocate_ForeignCall(NativeInstruction* inst, jlong foreign_call_destination, JVMCI_TRAPS) {
  address pc = (address) inst;
  if (inst->is_jal()) {
    NativeCall* call = nativeCall_at(pc);
    call->set_destination((address) foreign_call_destination);
    _instructions->relocate(call->instruction_address(), runtime_call_Relocation::spec());
  } else if (inst->is_jump()) {
    NativeJump* jump = nativeJump_at(pc);
    jump->set_jump_destination((address) foreign_call_destination);
    _instructions->relocate(jump->instruction_address(), runtime_call_Relocation::spec());
  } else if (inst->is_movptr()) {
    NativeMovConstReg* movptr = nativeMovConstReg_at(pc);
    movptr->set_data((intptr_t) foreign_call_destination);
    _instructions->relocate(movptr->instruction_address(), runtime_call_Relocation::spec());
  } else {
    JVMCI_ERROR("unknown call or jump instruction at " PTR_FORMAT, p2i(pc));
  }
  JVMCI_event_3("relocating (foreign call) at " PTR_FORMAT, p2i(inst));
}

void CodeInstaller::pd_relocate_JavaMethod(CodeBuffer &cbuf, methodHandle& method, jint pc_offset, JVMCI_TRAPS) {
  Unimplemented();
}

void CodeInstaller::pd_relocate_poll(address pc, jint mark, JVMCI_TRAPS) {
  Unimplemented();
}

// convert JVMCI register indices (as used in oop maps) to HotSpot registers
VMReg CodeInstaller::get_hotspot_reg(jint jvmci_reg, JVMCI_TRAPS) {
  if (jvmci_reg < Register::number_of_registers) {
    return as_Register(jvmci_reg)->as_VMReg();
  } else {
    jint floatRegisterNumber = jvmci_reg - Register::number_of_registers;
    if (floatRegisterNumber >= 0 && floatRegisterNumber < FloatRegister::number_of_registers) {
      return as_FloatRegister(floatRegisterNumber)->as_VMReg();
    }
    JVMCI_ERROR_NULL("invalid register number: %d", jvmci_reg);
  }
}

bool CodeInstaller::is_general_purpose_reg(VMReg hotspotRegister) {
  return !(hotspotRegister->is_FloatRegister() || hotspotRegister->is_VectorRegister());
}

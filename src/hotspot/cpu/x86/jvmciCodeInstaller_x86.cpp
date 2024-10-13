/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "compiler/disassembler.hpp"
#include "oops/compressedKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "asm/register.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/vmreg.hpp"
#include "vmreg_x86.inline.hpp"
#if INCLUDE_ZGC
#include "gc/z/zBarrierSetAssembler.hpp"
#endif

jint CodeInstaller::pd_next_offset(NativeInstruction* inst, jint pc_offset, JVMCI_TRAPS) {
  if (inst->is_call() || inst->is_jump()) {
    assert(NativeCall::instruction_size == (int)NativeJump::instruction_size, "unexpected size");
    return (pc_offset + NativeCall::instruction_size);
  } else if (inst->is_mov_literal64()) {
    // mov+call instruction pair
    jint offset = pc_offset + ((NativeMovConstReg*)inst)->instruction_size();
    u_char* call = (u_char*) (_instructions->start() + offset);
    if (call[0] == Assembler::REX_B) {
      offset += 1; /* prefix byte for extended register R8-R15 */
      call++;
    }
    if (call[0] == Assembler::REX2) {
      offset += 2; /* prefix byte for APX extended GPR register R16-R31 */
      call+=2;
    }
    // Register indirect call.
    assert(call[0] == 0xFF, "expected call");
    offset += 2; /* opcode byte + modrm byte */
    return (offset);
  } else if (inst->is_call_reg()) {
    // the inlined vtable stub contains a "call register" instruction
    return (pc_offset + ((NativeCallReg *) inst)->next_instruction_offset());
  } else if (inst->is_cond_jump()) {
    address pc = (address) (inst);
    return pc_offset + (jint) (Assembler::locate_next_instruction(pc) - pc);
  } else {
    JVMCI_ERROR_0("unsupported type of instruction for call site");
  }
}

void CodeInstaller::pd_patch_OopConstant(int pc_offset, Handle& obj, bool compressed, JVMCI_TRAPS) {
  address pc = _instructions->start() + pc_offset;
  jobject value = JNIHandles::make_local(obj());
  if (compressed) {
#ifdef _LP64
    address operand = Assembler::locate_operand(pc, Assembler::narrow_oop_operand);
    int oop_index = _oop_recorder->find_index(value);
    _instructions->relocate(pc, oop_Relocation::spec(oop_index), Assembler::narrow_oop_operand);
    JVMCI_event_3("relocating (narrow oop constant) at " PTR_FORMAT "/" PTR_FORMAT, p2i(pc), p2i(operand));
#else
    JVMCI_ERROR("compressed oop on 32bit");
#endif
  } else {
    address operand = Assembler::locate_operand(pc, Assembler::imm_operand);
    *((jobject*) operand) = value;
    _instructions->relocate(pc, oop_Relocation::spec_for_immediate(), Assembler::imm_operand);
    JVMCI_event_3("relocating (oop constant) at " PTR_FORMAT "/" PTR_FORMAT, p2i(pc), p2i(operand));
  }
}

void CodeInstaller::pd_patch_MetaspaceConstant(int pc_offset, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS) {
  address pc = _instructions->start() + pc_offset;
  if (tag == PATCH_NARROW_KLASS) {
#ifdef _LP64
    address operand = Assembler::locate_operand(pc, Assembler::narrow_oop_operand);
    *((narrowKlass*) operand) = record_narrow_metadata_reference(_instructions, operand, stream, tag, JVMCI_CHECK);
    JVMCI_event_3("relocating (narrow metaspace constant) at " PTR_FORMAT "/" PTR_FORMAT, p2i(pc), p2i(operand));
#else
    JVMCI_ERROR("compressed Klass* on 32bit");
#endif
  } else {
    address operand = Assembler::locate_operand(pc, Assembler::imm_operand);
    *((void**) operand) = record_metadata_reference(_instructions, operand, stream, tag, JVMCI_CHECK);
    JVMCI_event_3("relocating (metaspace constant) at " PTR_FORMAT "/" PTR_FORMAT, p2i(pc), p2i(operand));
  }
}

void CodeInstaller::pd_patch_DataSectionReference(int pc_offset, int data_offset, JVMCI_TRAPS) {
  address pc = _instructions->start() + pc_offset;

  address operand = Assembler::locate_operand(pc, Assembler::disp32_operand);
  address next_instruction = Assembler::locate_next_instruction(pc);
  address dest = _constants->start() + data_offset;

  long disp = dest - next_instruction;
  assert(disp == (jint) disp, "disp doesn't fit in 32 bits");
  *((jint*) operand) = (jint) disp;

  _instructions->relocate(pc, section_word_Relocation::spec((address) dest, CodeBuffer::SECT_CONSTS), Assembler::disp32_operand);
  JVMCI_event_3("relocating at " PTR_FORMAT "/" PTR_FORMAT " with destination at " PTR_FORMAT " (%d)", p2i(pc), p2i(operand), p2i(dest), data_offset);
}

void CodeInstaller::pd_relocate_ForeignCall(NativeInstruction* inst, jlong foreign_call_destination, JVMCI_TRAPS) {
  address pc = (address) inst;
  if (inst->is_call()) {
    // NOTE: for call without a mov, the offset must fit a 32-bit immediate
    //       see also CompilerToVM.getMaxCallTargetOffset()
    NativeCall* call = nativeCall_at(pc);
    call->set_destination((address) foreign_call_destination);
    _instructions->relocate(call->instruction_address(), runtime_call_Relocation::spec(), Assembler::call32_operand);
  } else if (inst->is_mov_literal64()) {
    NativeMovConstReg* mov = nativeMovConstReg_at(pc);
    mov->set_data((intptr_t) foreign_call_destination);
    _instructions->relocate(mov->instruction_address(), runtime_call_Relocation::spec(), Assembler::imm_operand);
  } else if (inst->is_jump()) {
    NativeJump* jump = nativeJump_at(pc);
    jump->set_jump_destination((address) foreign_call_destination);
    _instructions->relocate(jump->instruction_address(), runtime_call_Relocation::spec(), Assembler::call32_operand);
  } else if (inst->is_cond_jump()) {
    address old_dest = nativeGeneralJump_at(pc)->jump_destination();
    address disp = Assembler::locate_operand(pc, Assembler::call32_operand);
    *(jint*) disp += ((address) foreign_call_destination) - old_dest;
    _instructions->relocate(pc, runtime_call_Relocation::spec(), Assembler::call32_operand);
  } else {
    JVMCI_ERROR("unsupported relocation for foreign call");
  }

  JVMCI_event_3("relocating (foreign call)  at " PTR_FORMAT, p2i(inst));
}

void CodeInstaller::pd_relocate_JavaMethod(CodeBuffer &, methodHandle& method, jint pc_offset, JVMCI_TRAPS) {
  NativeCall* call = nullptr;
  switch (_next_call_type) {
    case INLINE_INVOKE:
      return;
    case INVOKEVIRTUAL:
    case INVOKEINTERFACE: {
      assert(!method->is_static(), "cannot call static method with invokeinterface");

      call = nativeCall_at(_instructions->start() + pc_offset);
      call->set_destination(SharedRuntime::get_resolve_virtual_call_stub());
      _instructions->relocate(call->instruction_address(),
                                             virtual_call_Relocation::spec(_invoke_mark_pc),
                                             Assembler::call32_operand);
      break;
    }
    case INVOKESTATIC: {
      assert(method->is_static(), "cannot call non-static method with invokestatic");

      call = nativeCall_at(_instructions->start() + pc_offset);
      call->set_destination(SharedRuntime::get_resolve_static_call_stub());
      _instructions->relocate(call->instruction_address(),
                                             relocInfo::static_call_type, Assembler::call32_operand);
      break;
    }
    case INVOKESPECIAL: {
      assert(!method->is_static(), "cannot call static method with invokespecial");
      call = nativeCall_at(_instructions->start() + pc_offset);
      call->set_destination(SharedRuntime::get_resolve_opt_virtual_call_stub());
      _instructions->relocate(call->instruction_address(),
                              relocInfo::opt_virtual_call_type, Assembler::call32_operand);
      break;
    }
    default:
      JVMCI_ERROR("invalid _next_call_type value: %d", _next_call_type);
      return;
  }
  if (!call->is_displacement_aligned()) {
    JVMCI_ERROR("unaligned displacement for call at offset %d", pc_offset);
  }
  if (Continuations::enabled()) {
    // Check for proper post_call_nop
    NativePostCallNop* nop = nativePostCallNop_at(call->next_instruction_address());
    if (nop == nullptr) {
      JVMCI_ERROR("missing post call nop at offset %d", pc_offset);
    } else {
      _instructions->relocate(call->next_instruction_address(), relocInfo::post_call_nop_type);
    }
  }
}

bool CodeInstaller::pd_relocate(address pc, jint mark) {
  switch (mark) {
    case POLL_NEAR:
    case POLL_FAR:
      // This is a load from a register so there is no relocatable operand.
      // We just have to ensure that the format is not disp32_operand
      // so that poll_Relocation::fix_relocation_after_move does the right
      // thing (i.e. ignores this relocation record)
      _instructions->relocate(pc, relocInfo::poll_type, Assembler::imm_operand);
      return true;
    case POLL_RETURN_NEAR:
    case POLL_RETURN_FAR:
      // see comment above for POLL_FAR
      _instructions->relocate(pc, relocInfo::poll_return_type, Assembler::imm_operand);
      return true;
#if INCLUDE_ZGC
    case Z_BARRIER_RELOCATION_FORMAT_LOAD_GOOD_BEFORE_SHL:
      _instructions->relocate(pc, barrier_Relocation::spec(), ZBarrierRelocationFormatLoadGoodBeforeShl);
      return true;
    case Z_BARRIER_RELOCATION_FORMAT_LOAD_BAD_AFTER_TEST:
      _instructions->relocate(pc, barrier_Relocation::spec(), ZBarrierRelocationFormatLoadBadAfterTest);
      return true;
    case Z_BARRIER_RELOCATION_FORMAT_MARK_BAD_AFTER_TEST:
      _instructions->relocate(pc, barrier_Relocation::spec(), ZBarrierRelocationFormatMarkBadAfterTest);
      return true;
    case Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_CMP:
      _instructions->relocate(pc, barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodAfterCmp);
      return true;
    case Z_BARRIER_RELOCATION_FORMAT_STORE_BAD_AFTER_TEST:
      _instructions->relocate(pc, barrier_Relocation::spec(), ZBarrierRelocationFormatStoreBadAfterTest);
      return true;
    case Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_OR:
      _instructions->relocate(pc, barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodAfterOr);
      return true;
    case Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_MOV:
      _instructions->relocate(pc, barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodAfterMov);
      return true;
#endif
    default:
      return false;
  }
}

// convert JVMCI register indices (as used in oop maps) to HotSpot registers
VMReg CodeInstaller::get_hotspot_reg(jint jvmci_reg, JVMCI_TRAPS) {
  if (jvmci_reg < Register::number_of_registers) {
    return as_Register(jvmci_reg)->as_VMReg();
  } else {
    jint floatRegisterNumber = jvmci_reg - Register::number_of_registers;
    if (floatRegisterNumber < XMMRegister::number_of_registers) {
      return as_XMMRegister(floatRegisterNumber)->as_VMReg();
    }
    JVMCI_ERROR_NULL("invalid register number: %d", jvmci_reg);
  }
}

bool CodeInstaller::is_general_purpose_reg(VMReg hotspotRegister) {
  return !(hotspotRegister->is_FloatRegister() || hotspotRegister->is_XMMRegister());
}

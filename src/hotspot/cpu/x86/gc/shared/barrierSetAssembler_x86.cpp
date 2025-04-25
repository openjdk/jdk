/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interp_masm.hpp"
#include "memory/universe.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#ifdef COMPILER2
#include "gc/shared/c2/barrierSetC2.hpp"
#endif // COMPILER2

#define __ masm->

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Address src, Register tmp1) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool is_not_null = (decorators & IS_NOT_NULL) != 0;
  bool atomic = (decorators & MO_RELAXED) != 0;

  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (in_heap) {
      if (UseCompressedOops) {
        __ movl(dst, src);
        if (is_not_null) {
          __ decode_heap_oop_not_null(dst);
        } else {
          __ decode_heap_oop(dst);
        }
      } else {
        __ movptr(dst, src);
      }
    } else {
      assert(in_native, "why else?");
      __ movptr(dst, src);
    }
    break;
  }
  case T_BOOLEAN: __ load_unsigned_byte(dst, src);  break;
  case T_BYTE:    __ load_signed_byte(dst, src);    break;
  case T_CHAR:    __ load_unsigned_short(dst, src); break;
  case T_SHORT:   __ load_signed_short(dst, src);   break;
  case T_INT:     __ movl  (dst, src);              break;
  case T_ADDRESS: __ movptr(dst, src);              break;
  case T_FLOAT:
    assert(dst == noreg, "only to ftos");
    __ movflt(xmm0, src);
    break;
  case T_DOUBLE:
    assert(dst == noreg, "only to dtos");
    __ movdbl(xmm0, src);
    break;
  case T_LONG:
    assert(dst == noreg, "only to ltos");
    __ movq(rax, src);
    break;
  default: Unimplemented();
  }
}

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Address dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool is_not_null = (decorators & IS_NOT_NULL) != 0;
  bool atomic = (decorators & MO_RELAXED) != 0;

  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (in_heap) {
      if (val == noreg) {
        assert(!is_not_null, "inconsistent access");
        if (UseCompressedOops) {
          __ movl(dst, NULL_WORD);
        } else {
          __ movslq(dst, NULL_WORD);
        }
      } else {
        if (UseCompressedOops) {
          assert(!dst.uses(val), "not enough registers");
          if (is_not_null) {
            __ encode_heap_oop_not_null(val);
          } else {
            __ encode_heap_oop(val);
          }
          __ movl(dst, val);
        } else {
          __ movptr(dst, val);
        }
      }
    } else {
      assert(in_native, "why else?");
      assert(val != noreg, "not supported");
      __ movptr(dst, val);
    }
    break;
  }
  case T_BOOLEAN:
    __ andl(val, 0x1);  // boolean is true if LSB is 1
    __ movb(dst, val);
    break;
  case T_BYTE:
    __ movb(dst, val);
    break;
  case T_SHORT:
    __ movw(dst, val);
    break;
  case T_CHAR:
    __ movw(dst, val);
    break;
  case T_INT:
    __ movl(dst, val);
    break;
  case T_LONG:
    assert(val == noreg, "only tos");
    __ movq(dst, rax);
    break;
  case T_FLOAT:
    assert(val == noreg, "only tos");
    __ movflt(dst, xmm0);
    break;
  case T_DOUBLE:
    assert(val == noreg, "only tos");
    __ movdbl(dst, xmm0);
    break;
  case T_ADDRESS:
    __ movptr(dst, val);
    break;
  default: Unimplemented();
  }
}

void BarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                       DecoratorSet decorators,
                                       BasicType type,
                                       size_t bytes,
                                       Register dst,
                                       Address src,
                                       Register tmp) {
  assert(bytes <= 8, "can only deal with non-vector registers");
  switch (bytes) {
  case 1:
    __ movb(dst, src);
    break;
  case 2:
    __ movw(dst, src);
    break;
  case 4:
    __ movl(dst, src);
    break;
  case 8:
    __ movq(dst, src);
    break;
  default:
    fatal("Unexpected size");
  }
  if ((decorators & ARRAYCOPY_CHECKCAST) != 0 && UseCompressedOops) {
    __ decode_heap_oop(dst);
  }
}

void BarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        Address dst,
                                        Register src,
                                        Register tmp) {
  if ((decorators & ARRAYCOPY_CHECKCAST) != 0 && UseCompressedOops) {
    __ encode_heap_oop(src);
  }
  assert(bytes <= 8, "can only deal with non-vector registers");
  switch (bytes) {
  case 1:
    __ movb(dst, src);
    break;
  case 2:
    __ movw(dst, src);
    break;
  case 4:
    __ movl(dst, src);
    break;
  case 8:
    __ movq(dst, src);
    break;
  default:
    fatal("Unexpected size");
  }
}

void BarrierSetAssembler::copy_load_at(MacroAssembler* masm,
                                       DecoratorSet decorators,
                                       BasicType type,
                                       size_t bytes,
                                       XMMRegister dst,
                                       Address src,
                                       Register tmp,
                                       XMMRegister xmm_tmp) {
  assert(bytes > 8, "can only deal with vector registers");
  if (bytes == 16) {
    __ movdqu(dst, src);
  } else if (bytes == 32) {
    __ vmovdqu(dst, src);
  } else {
    fatal("No support for >32 bytes copy");
  }
}

void BarrierSetAssembler::copy_store_at(MacroAssembler* masm,
                                        DecoratorSet decorators,
                                        BasicType type,
                                        size_t bytes,
                                        Address dst,
                                        XMMRegister src,
                                        Register tmp1,
                                        Register tmp2,
                                        XMMRegister xmm_tmp) {
  assert(bytes > 8, "can only deal with vector registers");
  if (bytes == 16) {
    __ movdqu(dst, src);
  } else if (bytes == 32) {
    __ vmovdqu(dst, src);
  } else {
    fatal("No support for >32 bytes copy");
  }
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  __ clear_jobject_tag(obj);
  __ movptr(obj, Address(obj, 0));
}

void BarrierSetAssembler::tlab_allocate(MacroAssembler* masm,
                                        Register obj,
                                        Register var_size_in_bytes,
                                        int con_size_in_bytes,
                                        Register t1,
                                        Register t2,
                                        Label& slow_case) {
  assert_different_registers(obj, t1, t2);
  assert_different_registers(obj, var_size_in_bytes, t1);
  Register end = t2;

  const Register thread = r15_thread;

  __ verify_tlab();

  __ movptr(obj, Address(thread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    __ lea(end, Address(obj, con_size_in_bytes));
  } else {
    __ lea(end, Address(obj, var_size_in_bytes, Address::times_1));
  }
  __ cmpptr(end, Address(thread, JavaThread::tlab_end_offset()));
  __ jcc(Assembler::above, slow_case);

  // update the tlab top pointer
  __ movptr(Address(thread, JavaThread::tlab_top_offset()), end);

  // recover var_size_in_bytes if necessary
  if (var_size_in_bytes == end) {
    __ subptr(var_size_in_bytes, obj);
  }
  __ verify_tlab();
}

void BarrierSetAssembler::nmethod_entry_barrier(MacroAssembler* masm, Label* slow_path, Label* continuation) {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  Register thread = r15_thread;
  Address disarmed_addr(thread, in_bytes(bs_nm->thread_disarmed_guard_value_offset()));
  // The immediate is the last 4 bytes, so if we align the start of the cmp
  // instruction to 4 bytes, we know that the second half of it is also 4
  // byte aligned, which means that the immediate will not cross a cache line
  __ align(4);
  uintptr_t before_cmp = (uintptr_t)__ pc();
  __ cmpl_imm32(disarmed_addr, 0);
  uintptr_t after_cmp = (uintptr_t)__ pc();
  guarantee(after_cmp - before_cmp == 8, "Wrong assumed instruction length");

  if (slow_path != nullptr) {
    __ jcc(Assembler::notEqual, *slow_path);
    __ bind(*continuation);
  } else {
    Label done;
    __ jccb(Assembler::equal, done);
    __ call(RuntimeAddress(StubRoutines::method_entry_barrier()));
    __ bind(done);
  }
}

void BarrierSetAssembler::c2i_entry_barrier(MacroAssembler* masm) {
  Label bad_call;
  __ cmpptr(rbx, 0); // rbx contains the incoming method for c2i adapters.
  __ jcc(Assembler::equal, bad_call);

  Register tmp1 = rscratch1;
  Register tmp2 = rscratch2;

  // Pointer chase to the method holder to find out if the method is concurrently unloading.
  Label method_live;
  __ load_method_holder_cld(tmp1, rbx);

   // Is it a strong CLD?
  __ cmpl(Address(tmp1, ClassLoaderData::keep_alive_ref_count_offset()), 0);
  __ jcc(Assembler::greater, method_live);

   // Is it a weak but alive CLD?
  __ movptr(tmp1, Address(tmp1, ClassLoaderData::holder_offset()));
  __ resolve_weak_handle(tmp1, tmp2);
  __ cmpptr(tmp1, 0);
  __ jcc(Assembler::notEqual, method_live);

  __ bind(bad_call);
  __ jump(RuntimeAddress(SharedRuntime::get_handle_wrong_method_stub()));
  __ bind(method_live);
}

void BarrierSetAssembler::check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error) {
  // Check if the oop is in the right area of memory
  __ movptr(tmp1, obj);
  __ movptr(tmp2, (intptr_t) Universe::verify_oop_mask());
  __ andptr(tmp1, tmp2);
  __ movptr(tmp2, (intptr_t) Universe::verify_oop_bits());
  __ cmpptr(tmp1, tmp2);
  __ jcc(Assembler::notZero, error);

  // make sure klass is 'reasonable', which is not zero.
  __ load_klass(obj, obj, tmp1);  // get klass
  __ testptr(obj, obj);
  __ jcc(Assembler::zero, error); // if klass is null it is broken
}

#ifdef COMPILER2

OptoReg::Name BarrierSetAssembler::refine_register(const Node* node, OptoReg::Name opto_reg) {
  if (!OptoReg::is_reg(opto_reg)) {
    return OptoReg::Bad;
  }

  const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);
  if (vm_reg->is_XMMRegister()) {
    opto_reg &= ~15;
    switch (node->ideal_reg()) {
    case Op_VecX:
      opto_reg |= 2;
      break;
    case Op_VecY:
      opto_reg |= 4;
      break;
    case Op_VecZ:
      opto_reg |= 8;
      break;
    default:
      opto_reg |= 1;
      break;
    }
  }

  return opto_reg;
}

// We use the vec_spill_helper from the x86.ad file to avoid reinventing this wheel
extern void vec_spill_helper(C2_MacroAssembler *masm, bool is_load,
                            int stack_offset, int reg, uint ireg, outputStream* st);

#undef __
#define __ _masm->

int SaveLiveRegisters::xmm_compare_register_size(XMMRegisterData* left, XMMRegisterData* right) {
  if (left->_size == right->_size) {
    return 0;
  }

  return (left->_size < right->_size) ? -1 : 1;
}

int SaveLiveRegisters::xmm_slot_size(OptoReg::Name opto_reg) {
  // The low order 4 bytes denote what size of the XMM register is live
  return (opto_reg & 15) << 3;
}

uint SaveLiveRegisters::xmm_ideal_reg_for_size(int reg_size) {
  switch (reg_size) {
  case 8:
    return Op_VecD;
  case 16:
    return Op_VecX;
  case 32:
    return Op_VecY;
  case 64:
    return Op_VecZ;
  default:
    fatal("Invalid register size %d", reg_size);
    return 0;
  }
}

bool SaveLiveRegisters::xmm_needs_vzeroupper() const {
  return _xmm_registers.is_nonempty() && _xmm_registers.at(0)._size > 16;
}

void SaveLiveRegisters::xmm_register_save(const XMMRegisterData& reg_data) {
  const OptoReg::Name opto_reg = OptoReg::as_OptoReg(reg_data._reg->as_VMReg());
  const uint ideal_reg = xmm_ideal_reg_for_size(reg_data._size);
  _spill_offset -= reg_data._size;
  C2_MacroAssembler c2_masm(__ code());
  vec_spill_helper(&c2_masm, false /* is_load */, _spill_offset, opto_reg, ideal_reg, tty);
}

void SaveLiveRegisters::xmm_register_restore(const XMMRegisterData& reg_data) {
  const OptoReg::Name opto_reg = OptoReg::as_OptoReg(reg_data._reg->as_VMReg());
  const uint ideal_reg = xmm_ideal_reg_for_size(reg_data._size);
  C2_MacroAssembler c2_masm(__ code());
  vec_spill_helper(&c2_masm, true /* is_load */, _spill_offset, opto_reg, ideal_reg, tty);
  _spill_offset += reg_data._size;
}

void SaveLiveRegisters::gp_register_save(Register reg) {
  _spill_offset -= 8;
  __ movq(Address(rsp, _spill_offset), reg);
}

void SaveLiveRegisters::opmask_register_save(KRegister reg) {
  _spill_offset -= 8;
  __ kmov(Address(rsp, _spill_offset), reg);
}

void SaveLiveRegisters::gp_register_restore(Register reg) {
  __ movq(reg, Address(rsp, _spill_offset));
  _spill_offset += 8;
}

void SaveLiveRegisters::opmask_register_restore(KRegister reg) {
  __ kmov(reg, Address(rsp, _spill_offset));
  _spill_offset += 8;
}

void SaveLiveRegisters::initialize(BarrierStubC2* stub) {
  // Create mask of caller saved registers that need to
  // be saved/restored if live
  RegMask caller_saved;
  caller_saved.Insert(OptoReg::as_OptoReg(rax->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(rcx->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(rdx->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(rsi->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(rdi->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(r8->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(r9->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(r10->as_VMReg()));
  caller_saved.Insert(OptoReg::as_OptoReg(r11->as_VMReg()));

  if (UseAPX) {
    caller_saved.Insert(OptoReg::as_OptoReg(r16->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r17->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r18->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r19->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r20->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r21->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r22->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r23->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r24->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r25->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r26->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r27->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r28->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r29->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r30->as_VMReg()));
    caller_saved.Insert(OptoReg::as_OptoReg(r31->as_VMReg()));
  }

  int gp_spill_size = 0;
  int opmask_spill_size = 0;
  int xmm_spill_size = 0;

  // Record registers that needs to be saved/restored
  RegMaskIterator rmi(stub->preserve_set());
  while (rmi.has_next()) {
    const OptoReg::Name opto_reg = rmi.next();
    const VMReg vm_reg = OptoReg::as_VMReg(opto_reg);

    if (vm_reg->is_Register()) {
      if (caller_saved.Member(opto_reg)) {
        _gp_registers.append(vm_reg->as_Register());
        gp_spill_size += 8;
      }
    } else if (vm_reg->is_KRegister()) {
      // All opmask registers are caller saved, thus spill the ones
      // which are live.
      if (_opmask_registers.find(vm_reg->as_KRegister()) == -1) {
        _opmask_registers.append(vm_reg->as_KRegister());
        opmask_spill_size += 8;
      }
    } else if (vm_reg->is_XMMRegister()) {
      // We encode in the low order 4 bits of the opto_reg, how large part of the register is live
      const VMReg vm_reg_base = OptoReg::as_VMReg(opto_reg & ~15);
      const int reg_size = xmm_slot_size(opto_reg);
      const XMMRegisterData reg_data = { vm_reg_base->as_XMMRegister(), reg_size };
      const int reg_index = _xmm_registers.find(reg_data);
      if (reg_index == -1) {
        // Not previously appended
        _xmm_registers.append(reg_data);
        xmm_spill_size += reg_size;
      } else {
        // Previously appended, update size
        const int reg_size_prev = _xmm_registers.at(reg_index)._size;
        if (reg_size > reg_size_prev) {
          _xmm_registers.at_put(reg_index, reg_data);
          xmm_spill_size += reg_size - reg_size_prev;
        }
      }
    } else {
      fatal("Unexpected register type");
    }
  }

  // Sort by size, largest first
  _xmm_registers.sort(xmm_compare_register_size);

  // On Windows, the caller reserves stack space for spilling register arguments
  const int arg_spill_size = frame::arg_reg_save_area_bytes;

  // Stack pointer must be 16 bytes aligned for the call
  _spill_offset = _spill_size = align_up(xmm_spill_size + gp_spill_size + opmask_spill_size + arg_spill_size, 16);
}

SaveLiveRegisters::SaveLiveRegisters(MacroAssembler* masm, BarrierStubC2* stub)
  : _masm(masm),
    _gp_registers(),
    _opmask_registers(),
    _xmm_registers(),
    _spill_size(0),
    _spill_offset(0) {

  //
  // Stack layout after registers have been spilled:
  //
  // | ...            | original rsp, 16 bytes aligned
  // ------------------
  // | zmm0 high      |
  // | ...            |
  // | zmm0 low       | 16 bytes aligned
  // | ...            |
  // | ymm1 high      |
  // | ...            |
  // | ymm1 low       | 16 bytes aligned
  // | ...            |
  // | xmmN high      |
  // | ...            |
  // | xmmN low       | 8 bytes aligned
  // | reg0           | 8 bytes aligned
  // | reg1           |
  // | ...            |
  // | regN           | new rsp, if 16 bytes aligned
  // | <padding>      | else new rsp, 16 bytes aligned
  // ------------------
  //

  // Figure out what registers to save/restore
  initialize(stub);

  // Allocate stack space
  if (_spill_size > 0) {
    __ subptr(rsp, _spill_size);
  }

  // Save XMM/YMM/ZMM registers
  for (int i = 0; i < _xmm_registers.length(); i++) {
    xmm_register_save(_xmm_registers.at(i));
  }

  if (xmm_needs_vzeroupper()) {
    __ vzeroupper();
  }

  // Save general purpose registers
  for (int i = 0; i < _gp_registers.length(); i++) {
    gp_register_save(_gp_registers.at(i));
  }

  // Save opmask registers
  for (int i = 0; i < _opmask_registers.length(); i++) {
    opmask_register_save(_opmask_registers.at(i));
  }
}

SaveLiveRegisters::~SaveLiveRegisters() {
  // Restore opmask registers
  for (int i = _opmask_registers.length() - 1; i >= 0; i--) {
    opmask_register_restore(_opmask_registers.at(i));
  }

  // Restore general purpose registers
  for (int i = _gp_registers.length() - 1; i >= 0; i--) {
    gp_register_restore(_gp_registers.at(i));
  }

  __ vzeroupper();

  // Restore XMM/YMM/ZMM registers
  for (int i = _xmm_registers.length() - 1; i >= 0; i--) {
    xmm_register_restore(_xmm_registers.at(i));
  }

  // Free stack space
  if (_spill_size > 0) {
    __ addptr(rsp, _spill_size);
  }
}

#endif // COMPILER2

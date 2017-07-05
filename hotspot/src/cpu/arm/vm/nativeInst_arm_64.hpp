/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ARM_VM_NATIVEINST_ARM_64_HPP
#define CPU_ARM_VM_NATIVEINST_ARM_64_HPP

#include "asm/macroAssembler.hpp"
#include "code/codeCache.hpp"
#include "memory/allocation.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"

// -------------------------------------------------------------------

// Some experimental projects extend the ARM back-end by implementing
// what the front-end usually assumes is a single native instruction
// with a sequence of instructions.
//
// The 'Raw' variants are the low level initial code (usually one
// instruction wide but some of them were already composed
// instructions). They should be used only by the back-end.
//
// The non-raw classes are the front-end entry point, hiding potential
// back-end extensions or the actual instructions size.
class NativeInstruction;

class RawNativeInstruction VALUE_OBJ_CLASS_SPEC {
 public:

  enum ARM_specific {
    instruction_size = Assembler::InstructionSize,
    instruction_size_in_bits = instruction_size * BitsPerByte,
  };

  // illegal instruction used by NativeJump::patch_verified_entry
  static const int zombie_illegal_instruction = 0xd4000542; // hvc #42

  address addr_at(int offset)        const { return (address)this + offset; }
  address instruction_address()      const { return addr_at(0); }
  address next_raw_instruction_address() const { return addr_at(instruction_size); }

  static RawNativeInstruction* at(address address) {
    return (RawNativeInstruction*)address;
  }

  RawNativeInstruction* next_raw() const {
    return at(next_raw_instruction_address());
  }

  int encoding() const {
    return *(int*)this;
  }

  void set_encoding(int value) {
    int old = encoding();
    if (old != value) {
      *(int*)this = value;
      ICache::invalidate_word((address)this);
    }
  }

  bool is_nop()                      const { return encoding() == (int)0xd503201f; }
  bool is_b()                        const { return (encoding() & 0xfc000000) == 0x14000000; } // unconditional branch
  bool is_b_cond()                   const { return (encoding() & 0xff000010) == 0x54000000; } // conditional branch
  bool is_bl()                       const { return (encoding() & 0xfc000000) == 0x94000000; }
  bool is_br()                       const { return (encoding() & 0xfffffc1f) == 0xd61f0000; }
  bool is_blr()                      const { return (encoding() & 0xfffffc1f) == 0xd63f0000; }
  bool is_ldr_literal()              const { return (encoding() & 0xff000000) == 0x58000000; }
  bool is_adr_aligned()              const { return (encoding() & 0xff000000) == 0x10000000; } // adr Xn, <label>, where label is aligned to 4 bytes (address of instruction).
  bool is_adr_aligned_lr()           const { return (encoding() & 0xff00001f) == 0x1000001e; } // adr LR, <label>, where label is aligned to 4 bytes (address of instruction).

  bool is_ldr_str_gp_reg_unsigned_imm()   const { return (encoding() & 0x3f000000) == 0x39000000; } // ldr/str{b, sb, h, sh, _w, sw} Rt, [Rn, #imm]
  bool is_ldr_str_fp_reg_unsigned_imm()   const { return (encoding() & 0x3f000000) == 0x3D000000; } // ldr/str Rt(SIMD), [Rn, #imm]
  bool is_ldr_str_reg_unsigned_imm()      const { return is_ldr_str_gp_reg_unsigned_imm() || is_ldr_str_fp_reg_unsigned_imm(); }

  bool is_stp_preindex()             const { return (encoding() & 0xffc00000) == 0xa9800000; } // stp Xt1, Xt2, [Xn, #imm]!
  bool is_ldp_postindex()            const { return (encoding() & 0xffc00000) == 0xa8c00000; } // ldp Xt1, Xt2, [Xn] #imm
  bool is_mov_sp()                   const { return (encoding() & 0xfffffc00) == 0x91000000; } // mov <Xn|SP>, <Xm|SP>
  bool is_movn()                     const { return (encoding() & 0x7f800000) == 0x12800000; }
  bool is_movz()                     const { return (encoding() & 0x7f800000) == 0x52800000; }
  bool is_movk()                     const { return (encoding() & 0x7f800000) == 0x72800000; }
  bool is_orr_imm()                  const { return (encoding() & 0x7f800000) == 0x32000000; }
  bool is_cmp_rr()                   const { return (encoding() & 0x7fe00000) == 0x6b000000; }
  bool is_csel()                     const { return (encoding() & 0x7fe00000) == 0x1a800000; }
  bool is_sub_shift()                const { return (encoding() & 0x7f200000) == 0x4b000000; } // sub Rd, Rn, shift (Rm, imm)
  bool is_mov()                      const { return (encoding() & 0x7fe0ffe0) == 0x2a0003e0; } // mov Rd, Rm (orr Rd, ZR, shift (Rm, 0))
  bool is_tst()                      const { return (encoding() & 0x7f20001f) == 0x6a00001f; } // tst Rn, shift (Rm, imm) (ands ZR, Rn, shift(Rm, imm))
  bool is_lsr_imm()                  const { return (encoding() & 0x7f807c00) == 0x53007c00; } // lsr Rd, Rn, imm (ubfm Rd, Rn, imm, 31/63)

  bool is_far_jump()                 const { return is_ldr_literal() && next_raw()->is_br(); }
  bool is_fat_call()                 const {
    return
#ifdef COMPILER2
      (is_blr() && next_raw()->is_b()) ||
#endif
      (is_adr_aligned_lr() && next_raw()->is_br());
  }
  bool is_far_call()                 const {
    return is_ldr_literal() && next_raw()->is_fat_call();
  }

  bool is_ic_near_call()             const { return is_adr_aligned_lr() && next_raw()->is_b(); }
  bool is_ic_far_call()              const { return is_adr_aligned_lr() && next_raw()->is_ldr_literal() && next_raw()->next_raw()->is_br(); }
  bool is_ic_call()                  const { return is_ic_near_call() || is_ic_far_call(); }

  bool is_jump()                     const { return is_b() || is_far_jump(); }
  bool is_call()                     const { return is_bl() || is_far_call() || is_ic_call(); }
  bool is_branch()                   const { return is_b() || is_bl(); }

  // c2 doesn't use fixed registers for safepoint poll address
  bool is_safepoint_poll() const {
    return true;
  }

  bool is_save_all_registers(const RawNativeInstruction** next) const {
    const RawNativeInstruction* current = this;

    if (!current->is_stp_preindex()) return false; current = current->next_raw();
    for (int i = 28; i >= 0; i -= 2) {
      if (!current->is_stp_preindex()) return false; current = current->next_raw();
    }

    if (!current->is_adr_aligned())                 return false; current = current->next_raw();
    if (!current->is_ldr_str_gp_reg_unsigned_imm()) return false; current = current->next_raw();
    if (!current->is_ldr_str_gp_reg_unsigned_imm()) return false; current = current->next_raw();

    *next = (RawNativeInstruction*) current;
    return true;
  }

  bool is_restore_all_registers(const RawNativeInstruction** next) const {
    const RawNativeInstruction* current = this;

    for (int i = 0; i <= 28; i += 2) {
      if (!current->is_ldp_postindex()) return false; current = current->next_raw();
    }
    if (!current->is_ldp_postindex()) return false; current = current->next_raw();

    *next = (RawNativeInstruction*) current;
    return true;
  }

  const RawNativeInstruction* skip_bind_literal() const {
    const RawNativeInstruction* current = this;
    if (((uintptr_t)current) % wordSize != 0) {
      assert(current->is_nop(), "should be");
      current = current->next_raw();
    }
    assert(((uintptr_t)current) % wordSize == 0, "should be"); // bound literal should be aligned
    current = current->next_raw()->next_raw();
    return current;
  }

  bool is_stop(const RawNativeInstruction** next) const {
    const RawNativeInstruction* current = this;

    if (!current->is_save_all_registers(&current)) return false;
    if (!current->is_ldr_literal())                return false; current = current->next_raw();
    if (!current->is_mov_sp())                     return false; current = current->next_raw();
    if (!current->is_ldr_literal())                return false; current = current->next_raw();
    if (!current->is_br())                         return false; current = current->next_raw();

    current = current->skip_bind_literal();
    current = current->skip_bind_literal();

    *next = (RawNativeInstruction*) current;
    return true;
  }

  bool is_mov_slow(const RawNativeInstruction** next = NULL) const {
    const RawNativeInstruction* current = this;

    if (current->is_orr_imm()) {
      current = current->next_raw();

    } else if (current->is_movn() || current->is_movz()) {
      current = current->next_raw();
      int movkCount = 0;
      while (current->is_movk()) {
        movkCount++;
        if (movkCount > 3) return false;
        current = current->next_raw();
      }

    } else {
      return false;
    }

    if (next != NULL) {
      *next = (RawNativeInstruction*)current;
    }
    return true;
  }

#ifdef ASSERT
  void skip_verify_heapbase(const RawNativeInstruction** next) const {
    const RawNativeInstruction* current = this;

    if (CheckCompressedOops) {
      if (!current->is_ldr_str_gp_reg_unsigned_imm()) return; current = current->next_raw();
      if (!current->is_stp_preindex())      return; current = current->next_raw();
      // NOTE: temporary workaround, remove with m6-01?
      // skip saving condition flags
      current = current->next_raw();
      current = current->next_raw();

      if (!current->is_mov_slow(&current))  return;
      if (!current->is_cmp_rr())            return; current = current->next_raw();
      if (!current->is_b_cond())            return; current = current->next_raw();
      if (!current->is_stop(&current))      return;

#ifdef COMPILER2
      if (current->is_nop()) current = current->next_raw();
#endif
      // NOTE: temporary workaround, remove with m6-01?
      // skip restoring condition flags
      current = current->next_raw();
      current = current->next_raw();

      if (!current->is_ldp_postindex())     return; current = current->next_raw();
      if (!current->is_ldr_str_gp_reg_unsigned_imm()) return; current = current->next_raw();
    }

    *next = (RawNativeInstruction*) current;
  }
#endif // ASSERT

  bool is_ldr_global_ptr(const RawNativeInstruction** next) const {
    const RawNativeInstruction* current = this;

    if (!current->is_mov_slow(&current))            return false;
    if (!current->is_ldr_str_gp_reg_unsigned_imm()) return false; current = current->next_raw();

    *next = (RawNativeInstruction*) current;
    return true;
  }

  void skip_verify_oop(const RawNativeInstruction** next) const {
    const RawNativeInstruction* current = this;

    if (VerifyOops) {
      if (!current->is_save_all_registers(&current)) return;

      if (current->is_mov()) {
        current = current->next_raw();
      }

      if (!current->is_mov_sp())                        return; current = current->next_raw();
      if (!current->is_ldr_literal())                   return; current = current->next_raw();
      if (!current->is_ldr_global_ptr(&current))        return;
      if (!current->is_blr())                           return; current = current->next_raw();
      if (!current->is_restore_all_registers(&current)) return;
      if (!current->is_b())                             return; current = current->next_raw();

      current = current->skip_bind_literal();
    }

    *next = (RawNativeInstruction*) current;
  }

  void skip_encode_heap_oop(const RawNativeInstruction** next) const {
    const RawNativeInstruction* current = this;

    assert (Universe::heap() != NULL, "java heap should be initialized");
#ifdef ASSERT
    current->skip_verify_heapbase(&current);
#endif // ASSERT
    current->skip_verify_oop(&current);

    if (Universe::narrow_oop_base() == NULL) {
      if (Universe::narrow_oop_shift() != 0) {
        if (!current->is_lsr_imm()) return; current = current->next_raw();
      } else {
        if (current->is_mov()) {
          current = current->next_raw();
        }
      }
    } else {
      if (!current->is_tst())       return; current = current->next_raw();
      if (!current->is_csel())      return; current = current->next_raw();
      if (!current->is_sub_shift()) return; current = current->next_raw();
      if (Universe::narrow_oop_shift() != 0) {
        if (!current->is_lsr_imm())  return; current = current->next_raw();
      }
    }

    *next = (RawNativeInstruction*) current;
  }

  void verify();

  // For unit tests
  static void test() {}

 private:

  void check_bits_range(int bits, int scale, int low_bit) const {
    assert((0 <= low_bit) && (0 < bits) && (low_bit + bits <= instruction_size_in_bits), "invalid bits range");
    assert((0 <= scale) && (scale <= 4), "scale is out of range");
  }

  void set_imm(int imm_encoding, int bits, int low_bit) {
    int imm_mask = right_n_bits(bits) << low_bit;
    assert((imm_encoding & ~imm_mask) == 0, "invalid imm encoding");
    set_encoding((encoding() & ~imm_mask) | imm_encoding);
  }

 protected:

  // Returns signed immediate from [low_bit .. low_bit + bits - 1] bits of this instruction, scaled by given scale.
  int get_signed_imm(int bits, int scale, int low_bit) const {
    check_bits_range(bits, scale, low_bit);
    int high_bits_to_clean = (instruction_size_in_bits - (low_bit + bits));
    return encoding() << high_bits_to_clean >> (high_bits_to_clean + low_bit) << scale;
  }

  // Puts given signed immediate into the [low_bit .. low_bit + bits - 1] bits of this instruction.
  void set_signed_imm(int value, int bits, int scale, int low_bit) {
    set_imm(Assembler::encode_imm(value, bits, scale, low_bit), bits, low_bit);
  }

  // Returns unsigned immediate from [low_bit .. low_bit + bits - 1] bits of this instruction, scaled by given scale.
  int get_unsigned_imm(int bits, int scale, int low_bit) const {
    check_bits_range(bits, scale, low_bit);
    return ((encoding() >> low_bit) & right_n_bits(bits)) << scale;
  }

  // Puts given unsigned immediate into the [low_bit .. low_bit + bits - 1] bits of this instruction.
  void set_unsigned_imm(int value, int bits, int scale, int low_bit) {
    set_imm(Assembler::encode_unsigned_imm(value, bits, scale, low_bit), bits, low_bit);
  }

  int get_signed_offset(int bits, int low_bit) const {
    return get_signed_imm(bits, 2, low_bit);
  }

  void set_signed_offset(int offset, int bits, int low_bit) {
    set_signed_imm(offset, bits, 2, low_bit);
  }
};

inline RawNativeInstruction* rawNativeInstruction_at(address address) {
  RawNativeInstruction* instr = RawNativeInstruction::at(address);
#ifdef ASSERT
  instr->verify();
#endif // ASSERT
  return instr;
}

// -------------------------------------------------------------------

// Load/store register (unsigned scaled immediate)
class NativeMovRegMem: public RawNativeInstruction {
 private:
  int get_offset_scale() const {
    return get_unsigned_imm(2, 0, 30);
  }

 public:
  int offset() const {
    return get_unsigned_imm(12, get_offset_scale(), 10);
  }

  void set_offset(int x);

  void add_offset_in_bytes(int add_offset) {
    set_offset(offset() + add_offset);
  }
};

inline NativeMovRegMem* nativeMovRegMem_at(address address) {
  const RawNativeInstruction* instr = rawNativeInstruction_at(address);

#ifdef COMPILER1
    // NOP required for C1 patching
    if (instr->is_nop()) {
      instr = instr->next_raw();
    }
#endif

  instr->skip_encode_heap_oop(&instr);

  assert(instr->is_ldr_str_reg_unsigned_imm(), "must be");
  return (NativeMovRegMem*)instr;
}

// -------------------------------------------------------------------

class NativeInstruction : public RawNativeInstruction {
public:
  static NativeInstruction* at(address address) {
    return (NativeInstruction*)address;
  }

public:
  // No need to consider indirections while parsing NativeInstruction
  address next_instruction_address() const {
    return next_raw_instruction_address();
  }

  // next() is no longer defined to avoid confusion.
  //
  // The front end and most classes except for those defined in nativeInst_arm
  // or relocInfo_arm should only use next_instruction_address(), skipping
  // over composed instruction and ignoring back-end extensions.
  //
  // The back-end can use next_raw() when it knows the instruction sequence
  // and only wants to skip a single native instruction.
};

inline NativeInstruction* nativeInstruction_at(address address) {
  NativeInstruction* instr = NativeInstruction::at(address);
#ifdef ASSERT
  instr->verify();
#endif // ASSERT
  return instr;
}

// -------------------------------------------------------------------
class NativeInstructionLdrLiteral: public NativeInstruction {
 public:
  address literal_address() {
    address la = instruction_address() + get_signed_offset(19, 5);
    assert(la != instruction_address(), "literal points to instruction");
    return la;
  }

  address after_literal_address() {
    return literal_address() + wordSize;
  }

  void set_literal_address(address addr, address pc) {
    assert(is_ldr_literal(), "must be");
    int opc = (encoding() >> 30) & 0x3;
    assert (opc != 0b01 || addr == pc || ((uintx)addr & 7) == 0, "ldr target should be aligned");
    set_signed_offset(addr - pc, 19, 5);
  }

  void set_literal_address(address addr) {
    set_literal_address(addr, instruction_address());
  }

  address literal_value() {
    return *(address*)literal_address();
  }

  void set_literal_value(address dest) {
    *(address*)literal_address() = dest;
  }
};

inline NativeInstructionLdrLiteral* nativeLdrLiteral_at(address address) {
  assert(nativeInstruction_at(address)->is_ldr_literal(), "must be");
  return (NativeInstructionLdrLiteral*)address;
}

// -------------------------------------------------------------------
// Common class for branch instructions with 26-bit immediate offset: B (unconditional) and BL
class NativeInstructionBranchImm26: public NativeInstruction {
 public:
  address destination(int adj = 0) const {
    return instruction_address() + get_signed_offset(26, 0) + adj;
  }

  void set_destination(address dest) {
    intptr_t offset = (intptr_t)(dest - instruction_address());
    assert((offset & 0x3) == 0, "should be aligned");
    set_signed_offset(offset, 26, 0);
  }
};

inline NativeInstructionBranchImm26* nativeB_at(address address) {
  assert(nativeInstruction_at(address)->is_b(), "must be");
  return (NativeInstructionBranchImm26*)address;
}

inline NativeInstructionBranchImm26* nativeBL_at(address address) {
  assert(nativeInstruction_at(address)->is_bl(), "must be");
  return (NativeInstructionBranchImm26*)address;
}

// -------------------------------------------------------------------
class NativeInstructionAdrLR: public NativeInstruction {
 public:
  // Returns address which is loaded into LR by this instruction.
  address target_lr_value() {
    return instruction_address() + get_signed_offset(19, 5);
  }
};

inline NativeInstructionAdrLR* nativeAdrLR_at(address address) {
  assert(nativeInstruction_at(address)->is_adr_aligned_lr(), "must be");
  return (NativeInstructionAdrLR*)address;
}

// -------------------------------------------------------------------
class RawNativeCall: public NativeInstruction {
 public:

  address return_address() const {
    if (is_bl()) {
      return next_raw_instruction_address();

    } else if (is_far_call()) {
#ifdef COMPILER2
      if (next_raw()->is_blr()) {
        // ldr_literal; blr; ret_addr: b skip_literal;
        return addr_at(2 * instruction_size);
      }
#endif
      assert(next_raw()->is_adr_aligned_lr() && next_raw()->next_raw()->is_br(), "must be");
      return nativeLdrLiteral_at(instruction_address())->after_literal_address();

    } else if (is_ic_call()) {
      return nativeAdrLR_at(instruction_address())->target_lr_value();

    } else {
      ShouldNotReachHere();
      return NULL;
    }
  }

  address destination(int adj = 0) const {
    if (is_bl()) {
      return nativeBL_at(instruction_address())->destination(adj);

    } else if (is_far_call()) {
      return nativeLdrLiteral_at(instruction_address())->literal_value();

    } else if (is_adr_aligned_lr()) {
      RawNativeInstruction *next = next_raw();
      if (next->is_b()) {
        // ic_near_call
        return nativeB_at(next->instruction_address())->destination(adj);
      } else if (next->is_far_jump()) {
        // ic_far_call
        return nativeLdrLiteral_at(next->instruction_address())->literal_value();
      }
    }
    ShouldNotReachHere();
    return NULL;
  }

  void set_destination(address dest) {
    if (is_bl()) {
      nativeBL_at(instruction_address())->set_destination(dest);
      return;
    }
    if (is_far_call()) {
      nativeLdrLiteral_at(instruction_address())->set_literal_value(dest);
      OrderAccess::storeload(); // overkill if caller holds lock?
      return;
    }
    if (is_adr_aligned_lr()) {
      RawNativeInstruction *next = next_raw();
      if (next->is_b()) {
        // ic_near_call
        nativeB_at(next->instruction_address())->set_destination(dest);
        return;
      }
      if (next->is_far_jump()) {
        // ic_far_call
        nativeLdrLiteral_at(next->instruction_address())->set_literal_value(dest);
        OrderAccess::storeload(); // overkill if caller holds lock?
        return;
      }
    }
    ShouldNotReachHere();
  }

  void set_destination_mt_safe(address dest) {
    assert(CodeCache::contains(dest), "call target should be from code cache (required by ic_call and patchable_call)");
    set_destination(dest);
  }

  void verify() {
    assert(RawNativeInstruction::is_call(), "should be");
  }

  void verify_alignment() {
    // Nothing to do on ARM
  }
};

inline RawNativeCall* rawNativeCall_at(address address) {
  RawNativeCall * call = (RawNativeCall*)address;
  call->verify();
  return call;
}

class NativeCall: public RawNativeCall {
 public:

  // NativeCall::next_instruction_address() is used only to define the
  // range where to look for the relocation information. We need not
  // walk over composed instructions (as long as the relocation information
  // is associated to the first instruction).
  address next_instruction_address() const {
    return next_raw_instruction_address();
  }

  static bool is_call_before(address return_address);
};

inline NativeCall* nativeCall_at(address address) {
  NativeCall * call = (NativeCall*)address;
  call->verify();
  return call;
}

NativeCall* nativeCall_before(address return_address);

// -------------------------------------------------------------------
class NativeGeneralJump: public NativeInstruction {
 public:

  address jump_destination() const {
    return nativeB_at(instruction_address())->destination();
  }

  static void replace_mt_safe(address instr_addr, address code_buffer);

  static void insert_unconditional(address code_pos, address entry);

};

inline NativeGeneralJump* nativeGeneralJump_at(address address) {
  assert(nativeInstruction_at(address)->is_b(), "must be");
  return (NativeGeneralJump*)address;
}

// -------------------------------------------------------------------
class RawNativeJump: public NativeInstruction {
 public:

  address jump_destination(int adj = 0) const {
    if (is_b()) {
      address a = nativeB_at(instruction_address())->destination(adj);
      // Jump destination -1 is encoded as a jump to self
      if (a == instruction_address()) {
        return (address)-1;
      }
      return a;
    } else {
      assert(is_far_jump(), "should be");
      return nativeLdrLiteral_at(instruction_address())->literal_value();
    }
  }

  void set_jump_destination(address dest) {
    if (is_b()) {
      // Jump destination -1 is encoded as a jump to self
      if (dest == (address)-1) {
        dest = instruction_address();
      }
      nativeB_at(instruction_address())->set_destination(dest);
    } else {
      assert(is_far_jump(), "should be");
      nativeLdrLiteral_at(instruction_address())->set_literal_value(dest);
    }
  }
};

inline RawNativeJump* rawNativeJump_at(address address) {
  assert(rawNativeInstruction_at(address)->is_jump(), "must be");
  return (RawNativeJump*)address;
}

// -------------------------------------------------------------------
class NativeMovConstReg: public NativeInstruction {

  NativeMovConstReg *adjust() const {
    return (NativeMovConstReg *)adjust(this);
  }

 public:

  static RawNativeInstruction *adjust(const RawNativeInstruction *ni) {
#ifdef COMPILER1
    // NOP required for C1 patching
    if (ni->is_nop()) {
      return ni->next_raw();
    }
#endif
    return (RawNativeInstruction *)ni;
  }

  intptr_t _data() const;
  void set_data(intptr_t x);

  intptr_t data() const {
    return adjust()->_data();
  }

  bool is_pc_relative() {
    return adjust()->is_ldr_literal();
  }

  void _set_pc_relative_offset(address addr, address pc) {
    assert(is_ldr_literal(), "must be");
    nativeLdrLiteral_at(instruction_address())->set_literal_address(addr, pc);
  }

  void set_pc_relative_offset(address addr, address pc) {
    NativeMovConstReg *ni = adjust();
    int dest_adj = ni->instruction_address() - instruction_address();
    ni->_set_pc_relative_offset(addr, pc + dest_adj);
  }

  address _next_instruction_address() const {
#ifdef COMPILER2
    if (is_movz()) {
      // narrow constant
      RawNativeInstruction* ni = next_raw();
      assert(ni->is_movk(), "movz;movk expected");
      return ni->next_raw_instruction_address();
    }
#endif
    assert(is_ldr_literal(), "must be");
    return NativeInstruction::next_raw_instruction_address();
  }

  address next_instruction_address() const {
    return adjust()->_next_instruction_address();
  }
};

inline NativeMovConstReg* nativeMovConstReg_at(address address) {
  RawNativeInstruction* ni = rawNativeInstruction_at(address);

  ni = NativeMovConstReg::adjust(ni);

  assert(ni->is_mov_slow() || ni->is_ldr_literal(), "must be");
  return (NativeMovConstReg*)address;
}

// -------------------------------------------------------------------
class NativeJump: public RawNativeJump {
 public:

  static void check_verified_entry_alignment(address entry, address verified_entry);

  static void patch_verified_entry(address entry, address verified_entry, address dest);
};

inline NativeJump* nativeJump_at(address address) {
  assert(nativeInstruction_at(address)->is_jump(), "must be");
  return (NativeJump*)address;
}

#endif // CPU_ARM_VM_NATIVEINST_ARM_64_HPP

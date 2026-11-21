/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2025, Red Hat Inc. All rights reserved.
 * Copyright 2026 Arm Limited and/or its affiliates.
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

#ifndef CPU_AARCH64_NATIVEINST_AARCH64_HPP
#define CPU_AARCH64_NATIVEINST_AARCH64_HPP

#include "asm/assembler.hpp"
#include "memory/heap.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"
#include "runtime/os.hpp"


// We have interfaces for the following instructions:
// - NativeInstruction
// - - NativeCall
// - - NativeMovConstReg
// - - NativeMovRegMem
// - - NativeJump
// - - - NativeGeneralJump
// - - NativeCallTrampolineStub
// - - NativeMembar
// - - NativeLdSt
// - - NativePostCallNop
// - - NativeDeoptInstruction

// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.

class NativeCall;
template <typename T> class NativeLdSt;

class NativeInstruction {
  friend class Relocation;
  friend bool is_NativeCallTrampolineStub_at(address);
public:
  enum {
    instruction_size = 4
  };

  juint encoding() const {
    return uint_at(0);
  }

  bool is_blr() const {
    // blr(register) or br(register)
    return (encoding() & 0xff9ffc1f) == 0xd61f0000;
  }
  bool is_adr_aligned() const {
    // adr Xn, <label>, where label is aligned to 4 bytes (address of instruction).
    return (encoding() & 0xff000000) == 0x10000000;
  }

  inline bool is_nop() const;
  bool is_jump();
  bool is_general_jump();
  inline bool is_cond_jump();
  bool is_safepoint_poll();
  bool is_movz();
  bool is_movk();
  bool is_stop();

  bool is_udf(uint16_t imm) {
    uint32_t insn = uint_at(0);
    return Instruction_aarch64::extract(insn, 31, 16) == 0 &&
      Instruction_aarch64::extract(insn, 15, 0) == imm;
  }

  enum : uint16_t { udf_stop = 1, udf_deopt };

protected:
  address addr_at(int offset) const { return address(this) + offset; }

  s_char sbyte_at(int offset) const { return *(s_char*)addr_at(offset); }
  u_char ubyte_at(int offset) const { return *(u_char*)addr_at(offset); }
  jint int_at(int offset) const     { return *(jint*)addr_at(offset); }
  juint uint_at(int offset) const   { return *(juint*)addr_at(offset); }
  address ptr_at(int offset) const  { return *(address*)addr_at(offset); }
  oop oop_at(int offset) const      { return *(oop*)addr_at(offset); }

#define MACOS_WX_WRITE MACOS_AARCH64_ONLY(os::thread_wx_enable_write())
  void set_char_at(int offset, char c)     { MACOS_WX_WRITE;  *addr_at(offset) = (u_char)c; }
  void set_int_at(int offset, jint i)      { MACOS_WX_WRITE;  *(jint*)addr_at(offset) = i; }
  void set_uint_at(int offset, juint i)    { MACOS_WX_WRITE;  *(juint*)addr_at(offset) = i; }
  void set_ptr_at(int offset, address ptr) { MACOS_WX_WRITE;  *(address*)addr_at(offset) = ptr; }
  void set_oop_at(int offset, oop o)       { MACOS_WX_WRITE;  *(oop*)addr_at(offset) = o; }
#undef MACOS_WX_WRITE

  void wrote(int offset);

public:

  inline friend NativeInstruction* nativeInstruction_at(address address);

  static bool is_adrp_at(address instr);

  static bool is_load_literal_at(address instr);

  bool is_load_literal() {
    return is_load_literal_at(addr_at(0));
  }

  static bool is_ldr_gpr_literal_at(address instr);

  bool is_ldr_gpr_literal() {
    return is_ldr_gpr_literal_at(addr_at(0));
  }

  static bool is_ldrw_gpr_literal_at(address instr);

  bool is_ldrw_gpr_literal() {
    return is_ldrw_gpr_literal_at(addr_at(0));
  }

  static bool is_ldrw_to_zr(address instr);

  static bool is_call_at(address instr) {
    const uint32_t insn = (*(uint32_t*)instr);
    return (insn >> 26) == 0b100101;
  }

  bool is_call() {
    return is_call_at(addr_at(0));
  }

  static bool maybe_cpool_ref(address instr) {
    return is_adrp_at(instr) || is_load_literal_at(instr);
  }

  bool is_Membar() {
    unsigned int insn = uint_at(0);
    return Instruction_aarch64::extract(insn, 31, 12) == 0b11010101000000110011 &&
      Instruction_aarch64::extract(insn, 7, 0) == 0b10111111;
  }

  template <typename T> bool is_Imm_LdSt() {
    return NativeLdSt<T>::is_ldst_ur_at(addr_at(0)) ||
           NativeLdSt<T>::is_ldst_unsigned_offset_at(addr_at(0));
  }

  static bool is_neon_vector_mov_alias(uint32_t insn) {
    if (Instruction_aarch64::extract(insn, 31, 31) != 0 ||
        Instruction_aarch64::extract(insn, 29, 21) != 0b001110101 ||
        Instruction_aarch64::extract(insn, 15, 10) != 0b000111) {
      return false;
    }
    return Instruction_aarch64::extract(insn, 9, 5) ==
           Instruction_aarch64::extract(insn, 20, 16);
  }

  static bool is_sve_vector_mov_alias(uint32_t insn) {
    if (Instruction_aarch64::extract(insn, 31, 21) != 0b00000100011 ||
        Instruction_aarch64::extract(insn, 15, 10) != 0b001100) {
      return false;
    }
    return Instruction_aarch64::extract(insn, 9, 5) ==
           Instruction_aarch64::extract(insn, 20, 16);
  }

  static uint32_t encode_sve_movprfx(uint32_t dst, uint32_t src) {
    return 0x1082f << 10 | (src << 5) | dst;
  }
};

inline NativeInstruction* nativeInstruction_at(address address) {
  return (NativeInstruction*)address;
}

// The natural type of an AArch64 instruction is uint32_t
inline NativeInstruction* nativeInstruction_at(uint32_t* address) {
  return (NativeInstruction*)address;
}

inline NativeCall* nativeCall_at(address address);
// The NativeCall is an abstraction for accessing/manipulating native
// call instructions (used to manipulate inline caches, primitive &
// DSO calls, etc.).

class NativeCall: public NativeInstruction {
public:
  enum Aarch64_specific_constants {
    instruction_size            =    4,
    instruction_offset          =    0,
    displacement_offset         =    0,
    return_address_offset       =    4
  };

  static int byte_size() { return instruction_size; }
  address instruction_address() const { return addr_at(instruction_offset); }
  address next_instruction_address() const { return addr_at(return_address_offset); }
  int displacement() const { return (int_at(displacement_offset) << 6) >> 4; }
  address displacement_address() const { return addr_at(displacement_offset); }
  address return_address() const { return addr_at(return_address_offset); }
  address raw_destination() const { return instruction_address() + displacement(); }
  address destination() const;

  void set_destination(address dest) {
    int64_t offset = dest - instruction_address();
    juint insn = 0b100101u << 26u;
    assert((offset & 3) == 0, "should be");
    Instruction_aarch64::spatch(reinterpret_cast<address>(&insn), 25, 0, offset >> 2);
    set_uint_at(displacement_offset, insn);
  }

  void verify_alignment() { ; }
  void verify();

  // Creation
  inline friend NativeCall* nativeCall_at(address address);
  inline friend NativeCall* nativeCall_before(address return_address);

  static bool is_call_before(address return_address) {
    return is_call_at(return_address - NativeCall::return_address_offset);
  }

  // MT-safe patching of a call instruction.
  static void insert(address code_pos, address entry);

  static void replace_mt_safe(address instr_addr, address code_buffer);

  // Similar to replace_mt_safe, but just changes the destination.  The
  // important thing is that free-running threads are able to execute
  // this call instruction at all times.  If the call is an immediate BL
  // instruction we can simply rely on atomicity of 32-bit writes to
  // make sure other threads will see no intermediate states.

  // We cannot rely on locks here, since the free-running threads must run at
  // full speed.
  //
  // Used in the runtime linkage of calls; see class CompiledIC.
  // (Cf. 4506997 and 4479829, where threads witnessed garbage displacements.)
  void set_destination_mt_safe(address dest);

  address get_trampoline();
};

inline NativeCall* nativeCall_at(address address) {
  NativeCall* call = (NativeCall*)(address - NativeCall::instruction_offset);
  DEBUG_ONLY(call->verify());
  return call;
}

inline NativeCall* nativeCall_before(address return_address) {
  NativeCall* call = (NativeCall*)(return_address - NativeCall::return_address_offset);
  DEBUG_ONLY(call->verify());
  return call;
}

// An interface for accessing/manipulating native mov reg, imm instructions.
// (used to manipulate inlined 64-bit data calls, etc.)
class NativeMovConstReg: public NativeInstruction {
public:
  enum Aarch64_specific_constants {
    instruction_size            =    3 * 4, // movz, movk, movk.  See movptr().
    instruction_offset          =    0,
    displacement_offset         =    0,
  };

  address instruction_address() const { return addr_at(instruction_offset); }

  address next_instruction_address() const {
    if (nativeInstruction_at(instruction_address())->is_movz())
      // Assume movz, movk, movk
      return addr_at(instruction_size);
    else if (is_adrp_at(instruction_address()))
      return addr_at(2*4);
    else if (is_load_literal_at(instruction_address()))
      return(addr_at(4));
    assert(false, "Unknown instruction in NativeMovConstReg");
    return nullptr;
  }

  intptr_t data() const;
  void set_data(intptr_t x);

  void flush() {
    if (! maybe_cpool_ref(instruction_address())) {
      ICache::invalidate_range(instruction_address(), instruction_size);
    }
  }

  void verify();
  void print();

  // Creation
  inline friend NativeMovConstReg* nativeMovConstReg_at(address address);
  inline friend NativeMovConstReg* nativeMovConstReg_before(address address);
};

inline NativeMovConstReg* nativeMovConstReg_at(address address) {
  NativeMovConstReg* test = (NativeMovConstReg*)(address - NativeMovConstReg::instruction_offset);
  DEBUG_ONLY(test->verify());
  return test;
}

inline NativeMovConstReg* nativeMovConstReg_before(address address) {
  NativeMovConstReg* test = (NativeMovConstReg*)(address - NativeMovConstReg::instruction_size - NativeMovConstReg::instruction_offset);
  DEBUG_ONLY(test->verify());
  return test;
}

// An interface for accessing/manipulating native moves of the form:
//      mov[b/w/l/q] [reg + offset], reg   (instruction_code_reg2mem)
//      mov[b/w/l/q] reg, [reg+offset]     (instruction_code_mem2reg
//      mov[s/z]x[w/b/q] [reg + offset], reg
//      fld_s  [reg+offset]
//      fld_d  [reg+offset]
//      fstp_s [reg + offset]
//      fstp_d [reg + offset]
//      mov_literal64  scratch,<pointer> ; mov[b/w/l/q] 0(scratch),reg | mov[b/w/l/q] reg,0(scratch)
//
// Warning: These routines must be able to handle any instruction sequences
// that are generated as a result of the load/store byte,word,long
// macros.  For example: The load_unsigned_byte instruction generates
// an xor reg,reg inst prior to generating the movb instruction.  This
// class must skip the xor instruction.

class NativeMovRegMem: public NativeInstruction {
  enum AArch64_specific_constants {
    instruction_size            =    4,
    instruction_offset          =    0,
    data_offset                 =    0,
    next_instruction_offset     =    4
  };

public:
  // helper
  int instruction_start() const { return instruction_offset; }

  address instruction_address() const { return addr_at(instruction_offset); }

  int num_bytes_to_end_of_patch() const { return instruction_offset + instruction_size; }

  int offset() const;

  void set_offset(int x);

  void add_offset_in_bytes(int add_offset) {
    set_offset(offset() + add_offset);
  }

  void verify();

private:
  inline friend NativeMovRegMem* nativeMovRegMem_at(address address);
};

inline NativeMovRegMem* nativeMovRegMem_at(address address) {
  NativeMovRegMem* test = (NativeMovRegMem*)(address - NativeMovRegMem::instruction_offset);
  DEBUG_ONLY(test->verify());
  return test;
}

class NativeJump: public NativeInstruction {
public:
  enum AArch64_specific_constants {
    instruction_size            =    4,
    instruction_offset          =    0,
    data_offset                 =    0,
    next_instruction_offset     =    4
  };

  address instruction_address() const { return addr_at(instruction_offset); }
  address next_instruction_address() const { return addr_at(instruction_size); }
  address jump_destination() const;
  void set_jump_destination(address dest);

  // Creation
  inline friend NativeJump* nativeJump_at(address address);

  void verify();

  // Insertion of native jump instruction
  static void insert(address code_pos, address entry);
};

inline NativeJump* nativeJump_at(address address) {
  NativeJump* jump = (NativeJump*)(address - NativeJump::instruction_offset);
  DEBUG_ONLY(jump->verify());
  return jump;
}

class NativeGeneralJump: public NativeJump {
public:
  enum AArch64_specific_constants {
    instruction_size            =    4 * 4,
    instruction_offset          =    0,
    data_offset                 =    0,
    next_instruction_offset     =    4 * 4
  };

  address jump_destination() const;
  void set_jump_destination(address dest);

  static void replace_mt_safe(address instr_addr, address code_buffer);
};

inline NativeGeneralJump* nativeGeneralJump_at(address address) {
  NativeGeneralJump* jump = (NativeGeneralJump*)(address);
  DEBUG_ONLY(jump->verify());
  return jump;
}

inline bool NativeInstruction::is_nop() const{
  uint32_t insn = *(uint32_t*)addr_at(0);
  return insn == 0xd503201f;
}

inline bool NativeInstruction::is_jump() {
  uint32_t insn = *(uint32_t*)addr_at(0);

  if (Instruction_aarch64::extract(insn, 30, 26) == 0b00101) {
    // Unconditional branch (immediate)
    return true;
  } else if (Instruction_aarch64::extract(insn, 31, 25) == 0b0101010) {
    // Conditional branch (immediate)
    return true;
  } else if (Instruction_aarch64::extract(insn, 30, 25) == 0b011010) {
    // Compare & branch (immediate)
    return true;
  } else if (Instruction_aarch64::extract(insn, 30, 25) == 0b011011) {
    // Test & branch (immediate)
    return true;
  } else
    return false;
}

// Call trampoline stubs.
class NativeCallTrampolineStub : public NativeInstruction {
public:

  enum AArch64_specific_constants {
    instruction_size            =    4 * 4,
    instruction_offset          =    0,
    data_offset                 =    2 * 4,
    next_instruction_offset     =    4 * 4
  };

  address destination(nmethod* nm = nullptr) const;
  void set_destination(address new_destination);
  ptrdiff_t destination_offset() const;
};

inline bool is_NativeCallTrampolineStub_at(address addr) {
  // Ensure that the stub is exactly
  //      ldr   xscratch1, L
  //      br    xscratch1
  // L:
  uint32_t* i = (uint32_t*)addr;
  return i[0] == 0x58000048 && i[1] == 0xd61f0100;
}

inline NativeCallTrampolineStub* nativeCallTrampolineStub_at(address addr) {
  assert(is_NativeCallTrampolineStub_at(addr), "no call trampoline found");
  return (NativeCallTrampolineStub*)addr;
}

class NativeMembar : public NativeInstruction {
public:
  unsigned int get_kind() { return Instruction_aarch64::extract(uint_at(0), 11, 8); }
  void set_kind(int order_kind) { Instruction_aarch64::patch(addr_at(0), 11, 8, order_kind); }
};

inline NativeMembar* NativeMembar_at(address addr) {
  assert(nativeInstruction_at(addr)->is_Membar(), "no membar found");
  return (NativeMembar*)addr;
}

template <typename T>
class NativeLdStImpl;

template <>
class NativeLdStImpl<Register> : public NativeInstruction {
protected:
  int32_t size() {
    assert(Instruction_aarch64::extract(uint_at(0), 26, 26) == 0b0, "");
    return Instruction_aarch64::extract(uint_at(0), 31, 30);
  }

public:
  // Check whether instruction is with unscaled offset.
  static bool is_ldst_ur_at(address instr) {
    const uint32_t insn = (*(uint32_t*)instr);
    return (Instruction_aarch64::extract(insn, 29, 21) == 0b111000010 ||
            Instruction_aarch64::extract(insn, 29, 21) == 0b111000000) &&
      Instruction_aarch64::extract(insn, 11, 10) == 0b00;
  }

  static bool is_ldst_unsigned_offset_at(address instr) {
    const uint32_t insn = (*(uint32_t*)instr);
    return Instruction_aarch64::extract(insn, 29, 22) == 0b11100101 ||
           Instruction_aarch64::extract(insn, 29, 22) == 0b11100100;
  }


  Register target() {
    uint32_t r = Instruction_aarch64::extract(uint_at(0), 4, 0);
    return r == 0x1f ? zr : as_Register(r);
  }
};

template <>
class NativeLdStImpl<FloatRegister> : public NativeInstruction {
protected:
  int32_t size() {
    assert(Instruction_aarch64::extract(uint_at(0), 26, 26) == 0b1, "");
    uint32_t is_q = Instruction_aarch64::extract(uint_at(0), 23, 23);
    return is_q == 0b1 ? 4 : Instruction_aarch64::extract(uint_at(0), 31, 30);
  }

public:

  // Check whether instruction is with unscaled offset.
  static bool is_ldst_ur_at(address instr) {
    const uint32_t insn = (*(uint32_t*)instr);
    return Instruction_aarch64::extract(insn, 29, 24) == 0b111100 &&
           Instruction_aarch64::extract(insn, 21, 21) == 0b0 &&
           Instruction_aarch64::extract(insn, 11, 10) == 0b00;
  }

  static bool is_ldst_unsigned_offset_at(address instr) {
    const uint32_t insn = (*(uint32_t*)instr);
    return Instruction_aarch64::extract(insn, 29, 24) == 0b111101;
  }

  FloatRegister target() {
    uint32_t r = Instruction_aarch64::extract(uint_at(0), 4, 0);
    return as_FloatRegister(r);
  }

};

template <typename T>
class NativeLdSt : public NativeLdStImpl<T> {
protected:
  bool is_ldst_ur() {
    return NativeLdSt<T>::is_ldst_ur_at(this->addr_at(0));
  }

  bool is_ldst_unsigned_offset() {
    return NativeLdSt<T>::is_ldst_unsigned_offset_at(this->addr_at(0));
  }

public:
  Register base() {
    uint32_t b = Instruction_aarch64::extract(this->uint_at(0), 9, 5);
    return b == 0x1f ? sp : as_Register(b);
  }
  int64_t offset() {
    if (is_ldst_ur()) {
      return Instruction_aarch64::sextract(this->uint_at(0), 20, 12);
    } else if (is_ldst_unsigned_offset()) {
      return Instruction_aarch64::extract(this->uint_at(0), 21, 10) << this->size();
    } else {
      // others like: pre-index or post-index.
      ShouldNotReachHere();
      return 0;
    }
  }
  size_t size_in_bytes() { return 1ULL << this->size(); }
  bool is_not_pre_post_index() { return is_ldst_ur() || is_ldst_unsigned_offset(); }

  Assembler::AccessDir dir() {
    switch(Instruction_aarch64::extract(this->uint_at(0), 22, 22)) {
      case 0b0: return Assembler::STORE;
      case 0b1: return Assembler::LOAD;
      default: ShouldNotReachHere();
    }
  }
};

template <typename T>
inline NativeLdSt<T>* NativeLdSt_at(address addr) {
  assert(nativeInstruction_at(addr)->is_Imm_LdSt<T>(), "no immediate load/store found");
  return (NativeLdSt<T>*)addr;
}

// A NativePostCallNop takes the form of NOP followed by one instruction slot
// holding metadata represented as an instruction with no side effects.
//
// The NOP is patchable for a deoptimization trap.
//
// The options for the second instruction are:
//  - Inlined form. MOVK or MOVZ with the inlined metadata values:
//    - cb_blocks_offset - 10 bits - represents the offset from the start of the code
//      heap's space allocated for the method in HeapBlock::minimum_alignment() blocks,
//      rounded down to the block size.
//    - oopmap_slot - 9 bits.
//  - Stub form. MOVN with inlined offset from the MOVN instruction to a 32-bit word,
//    containing the metadata values, which is positioned after the main code. The unit
//    of the offset value is 4 bytes, and the offset field takes 18 bits.
//    Both cb_blocks_offset and oopmap_slot take 16 bits each. This option is used only
//    for C2-compiled code.
//
// For the inlined form, the metadata can be marked "empty": cb_blocks_offset and oopmap_slot
// are both set to 0.
class NativePostCallNop: public NativeInstruction {
private:
  static bool is_match_direct_form(uint32_t metadata_slot) {
    // Metadata slot is in form of MOVK/MOVZ XZR, <data>.
    return (metadata_slot & 0xdf80001f) == 0xd280001f;
  }

  static bool is_match_stub_form(uint32_t metadata_slot) {
    // Metadata slot is in form of MOVN XZR, <data>.
    return (metadata_slot & 0xff80001f) == 0x9280001f;
  }

  static bool is_match(uint32_t metadata_slot) {
    return is_match_direct_form(metadata_slot) || is_match_stub_form(metadata_slot);
  }

  int32_t compute_cb_blocks_offset(int32_t cb_offset) {
    constexpr int32_t block_size = HeapBlock::minimum_alignment();
    constexpr int32_t header_size = CodeHeap::header_size();

#ifdef ASSERT
    uintptr_t code_blob_address = (uintptr_t) this - cb_offset;
    uint32_t code_blob_offset_from_segment = code_blob_address % block_size;

    assert(block_size <= CodeCacheSegmentSize, "incorrect block size value");
    assert(header_size == code_blob_offset_from_segment, "unexpected offset value");
#endif

    return (cb_offset + header_size) / block_size;
  }

public:
  enum AArch64_specific_constants {
    // The two parts should be checked separately to prevent out of bounds access in case
    // the return address points to the deopt handler stub code entry point which could be
    // at the end of a page.
    first_check_size = instruction_size
  };

  bool check() const {
    // Check for a NOP followed by a metadata container.
    // This sequence only ever appears in a post-call NOP.
    if (is_nop()) {
      uint32_t metadata_slot = *(uint32_t*)addr_at(first_check_size);
      return is_match(metadata_slot);
    }

    return false;
  }

  bool decode(int32_t& oopmap_slot, int32_t& cb_offset) const {
    address metadata_slot_addr = addr_at(first_check_size);
    const uint32_t metadata_slot = *(uint32_t*)metadata_slot_addr;

    int32_t cb_blocks_offset;

    assert(is_match(metadata_slot), "unexpected format");

    uint32_t field_shift = Instruction_aarch64::extract(metadata_slot, 22, 21);
    uint32_t field_imm = Instruction_aarch64::extract(metadata_slot, 20, 5);
    if (is_match_direct_form(metadata_slot)) {
      uint32_t field_opcode = Instruction_aarch64::extract(metadata_slot, 29, 29) ^ 1;

      uint32_t slot_value = (((field_imm << 2) | field_shift) << 1) | field_opcode;
      cb_blocks_offset = static_cast<int32_t>((slot_value) & 0x3ff);
      oopmap_slot = static_cast<int32_t>((slot_value >> 10) & 0x1ff);

      if (cb_blocks_offset == 0) {
        if (oopmap_slot == 0) {
          return false; // no information stored
        }

        oopmap_slot--;
      }
    } else {
      uint32_t stub_offset = (field_imm << 2) | field_shift;
      stub_offset *= instruction_size;

      address stub_slot = metadata_slot_addr + stub_offset;
      uint32_t stub_slot_value = *(uint32_t*)stub_slot;

      oopmap_slot = stub_slot_value >> 16;
      cb_blocks_offset = stub_slot_value & 0xffff;
    }

    constexpr int32_t block_size = HeapBlock::minimum_alignment();
    constexpr int32_t header_size = CodeHeap::header_size();
    cb_offset = cb_blocks_offset * block_size;
    cb_offset += static_cast<int32_t>((intptr_t) this % block_size);
    cb_offset -= header_size;

    return true;
  }

  bool patch(int32_t oopmap_slot, int32_t cb_offset);
  bool patch_stub(uint32_t* out_stub_slot_value, int32_t oopmap_slot,
                  int32_t cb_offset, address stub_slot);
  void make_deopt();

#ifdef ASSERT
  void patch_precondition(int32_t oopmap_slot, int32_t cb_offset, bool allow_non_empty) {
    uint32_t metadata_slot = *(uint32_t*)addr_at(first_check_size);
    assert(is_match_direct_form(metadata_slot), "unexpected input form");

    int32_t test_oopmap_slot;
    int32_t test_cb_offset;
    if (decode(test_oopmap_slot, test_cb_offset)) {
      assert(allow_non_empty, "unexpected input data");

      // If not empty, it can be a copy of the nmethod, in which case the
      // contents should already match the values requested from this method.
      assert(test_oopmap_slot == oopmap_slot, "oopmap_slot mismatch");
      assert(test_cb_offset == cb_offset, "cb_offset mismatch");
    }
  }

  void verify(int32_t cb_offset, int32_t oopmap_slot) const {
    int32_t test_oopmap_slot;
    int32_t test_cb_offset;
    assert(decode(test_oopmap_slot, test_cb_offset), "unpacking values failed");
    assert(test_oopmap_slot == oopmap_slot, "oopmap_slot mismatch");
    assert(test_cb_offset == cb_offset, "cb_offset mismatch");
  }
#endif
};

inline NativePostCallNop* nativePostCallNop_at(address address) {
  NativePostCallNop* nop = (NativePostCallNop*) address;
  if (nop->check()) {
    return nop;
  }
  return nullptr;
}

inline NativePostCallNop* nativePostCallNop_unsafe_at(address address) {
  NativePostCallNop* nop = (NativePostCallNop*) address;
  assert(nop->check(), "");
  return nop;
}

class NativeDeoptInstruction: public NativeInstruction {
 public:
  enum {
    instruction_size            =    4,
    instruction_offset          =    0,
  };

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(instruction_size); }

  void  verify();

  static bool is_deopt_at(address instr) {
    assert(instr != nullptr, "");
    return nativeInstruction_at(instr)->is_udf(udf_deopt);
  }

  // MT-safe patching
  static void insert(address code_pos);
};

#endif // CPU_AARCH64_NATIVEINST_AARCH64_HPP

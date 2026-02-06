/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2025, Red Hat Inc. All rights reserved.
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
#include "runtime/icache.hpp"
#include "runtime/os.hpp"
#include "runtime/os.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciExceptions.hpp"
#endif


// We have interfaces for the following instructions:
// - NativeInstruction
// - - NativeCall
// - - NativeMovConstReg
// - - NativeMovRegMem
// - - NativeJump
// - - - NativeGeneralJump
// - - NativeIllegalInstruction
// - - NativeCallTrampolineStub
// - - NativeMembar
// - - NativeLdSt
// - - NativePostCallNop
// - - NativeDeoptInstruction

// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.

class NativeCall;

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
  inline bool is_jump_or_nop();
  inline bool is_cond_jump();
  bool is_safepoint_poll();
  bool is_movz();
  bool is_movk();
  bool is_stop();

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
  void set_uint_at(int offset, jint i)     { MACOS_WX_WRITE;  *(juint*)addr_at(offset) = i; }
  void set_ptr_at(int offset, address ptr) { MACOS_WX_WRITE;  *(address*)addr_at(offset) = ptr; }
  void set_oop_at(int offset, oop o)       { MACOS_WX_WRITE;  *(oop*)addr_at(offset) = o; }
#undef MACOS_WX_WRITE

  void wrote(int offset);

public:

  inline friend NativeInstruction* nativeInstruction_at(address address);

  static bool is_adrp_at(address instr);

  static bool is_ldr_literal_at(address instr);

  bool is_ldr_literal() {
    return is_ldr_literal_at(addr_at(0));
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
    return is_adrp_at(instr) || is_ldr_literal_at(instr);
  }

  bool is_Membar() {
    unsigned int insn = uint_at(0);
    return Instruction_aarch64::extract(insn, 31, 12) == 0b11010101000000110011 &&
      Instruction_aarch64::extract(insn, 7, 0) == 0b10111111;
  }

  bool is_Imm_LdSt() {
    unsigned int insn = uint_at(0);
    return Instruction_aarch64::extract(insn, 29, 27) == 0b111 &&
      Instruction_aarch64::extract(insn, 23, 23) == 0b0 &&
      Instruction_aarch64::extract(insn, 26, 25) == 0b00;
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
    int offset = dest - instruction_address();
    unsigned int insn = 0b100101 << 26;
    assert((offset & 3) == 0, "should be");
    offset >>= 2;
    offset &= (1 << 26) - 1; // mask off insn part
    insn |= offset;
    set_int_at(displacement_offset, insn);
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
#if INCLUDE_JVMCI
  void trampoline_jump(CodeBuffer &cbuf, address dest, JVMCI_TRAPS);
#endif
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
    else if (is_ldr_literal_at(instruction_address()))
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

class NativeIllegalInstruction: public NativeInstruction {
public:
  // Insert illegal opcode as specific address
  static void insert(address code_pos);
};

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

inline bool NativeInstruction::is_jump_or_nop() {
  return is_nop() || is_jump();
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

class NativeLdSt : public NativeInstruction {
private:
  int32_t size() { return Instruction_aarch64::extract(uint_at(0), 31, 30); }
  // Check whether instruction is with unscaled offset.
  bool is_ldst_ur() {
    return (Instruction_aarch64::extract(uint_at(0), 29, 21) == 0b111000010 ||
            Instruction_aarch64::extract(uint_at(0), 29, 21) == 0b111000000) &&
      Instruction_aarch64::extract(uint_at(0), 11, 10) == 0b00;
  }
  bool is_ldst_unsigned_offset() {
    return Instruction_aarch64::extract(uint_at(0), 29, 22) == 0b11100101 ||
      Instruction_aarch64::extract(uint_at(0), 29, 22) == 0b11100100;
  }
public:
  Register target() {
    uint32_t r = Instruction_aarch64::extract(uint_at(0), 4, 0);
    return r == 0x1f ? zr : as_Register(r);
  }
  Register base() {
    uint32_t b = Instruction_aarch64::extract(uint_at(0), 9, 5);
    return b == 0x1f ? sp : as_Register(b);
  }
  int64_t offset() {
    if (is_ldst_ur()) {
      return Instruction_aarch64::sextract(uint_at(0), 20, 12);
    } else if (is_ldst_unsigned_offset()) {
      return Instruction_aarch64::extract(uint_at(0), 21, 10) << size();
    } else {
      // others like: pre-index or post-index.
      ShouldNotReachHere();
      return 0;
    }
  }
  size_t size_in_bytes() { return 1ULL << size(); }
  bool is_not_pre_post_index() { return (is_ldst_ur() || is_ldst_unsigned_offset()); }
  bool is_load() {
    assert(Instruction_aarch64::extract(uint_at(0), 23, 22) == 0b01 ||
           Instruction_aarch64::extract(uint_at(0), 23, 22) == 0b00, "must be ldr or str");

    return Instruction_aarch64::extract(uint_at(0), 23, 22) == 0b01;
  }
  bool is_store() {
    assert(Instruction_aarch64::extract(uint_at(0), 23, 22) == 0b01 ||
           Instruction_aarch64::extract(uint_at(0), 23, 22) == 0b00, "must be ldr or str");

    return Instruction_aarch64::extract(uint_at(0), 23, 22) == 0b00;
  }
};

inline NativeLdSt* NativeLdSt_at(address addr) {
  assert(nativeInstruction_at(addr)->is_Imm_LdSt(), "no immediate load/store found");
  return (NativeLdSt*)addr;
}

// A NativePostCallNop takes the form of three instructions:
//     nop; movk zr, lo; movk zr, hi
//
// The nop is patchable for a deoptimization trap. The two movk
// instructions execute as nops but have a 16-bit payload in which we
// can store an offset from the initial nop to the nmethod.

class NativePostCallNop: public NativeInstruction {
private:
  static bool is_movk_to_zr(uint32_t insn) {
    return ((insn & 0xffe0001f) == 0xf280001f);
  }

public:
  enum AArch64_specific_constants {
    // The two parts should be checked separately to prevent out of bounds access in case
    // the return address points to the deopt handler stub code entry point which could be
    // at the end of page.
    first_check_size = instruction_size
  };

  bool check() const {
    // Check the first instruction is NOP.
    if (is_nop()) {
      uint32_t insn = *(uint32_t*)addr_at(first_check_size);
      // Check next instruction is MOVK zr, xx.
      // These instructions only ever appear together in a post-call
      // NOP, so it's unnecessary to check that the third instruction is
      // a MOVK as well.
      return is_movk_to_zr(insn);
    }

    return false;
  }

  bool decode(int32_t& oopmap_slot, int32_t& cb_offset) const {
    uint64_t movk_insns = *(uint64_t*)addr_at(4);
    uint32_t lo = (movk_insns >> 5) & 0xffff;
    uint32_t hi = (movk_insns >> (5 + 32)) & 0xffff;
    uint32_t data = (hi << 16) | lo;
    if (data == 0) {
      return false; // no information encoded
    }
    cb_offset = (data & 0xffffff);
    oopmap_slot = (data >> 24) & 0xff;
    return true; // decoding succeeded
  }

  bool patch(int32_t oopmap_slot, int32_t cb_offset);
  void make_deopt();
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
    uint32_t value = *(uint32_t *) instr;
    return value == 0xd4ade001;
  }

  // MT-safe patching
  static void insert(address code_pos);
};

#endif // CPU_AARCH64_NATIVEINST_AARCH64_HPP

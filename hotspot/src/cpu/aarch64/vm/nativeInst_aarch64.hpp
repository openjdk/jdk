/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_VM_NATIVEINST_AARCH64_HPP
#define CPU_AARCH64_VM_NATIVEINST_AARCH64_HPP

#include "asm/assembler.hpp"
#include "memory/allocation.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"
#include "utilities/top.hpp"

// We have interfaces for the following instructions:
// - NativeInstruction
// - - NativeCall
// - - NativeMovConstReg
// - - NativeMovConstRegPatching
// - - NativeMovRegMem
// - - NativeMovRegMemPatching
// - - NativeJump
// - - NativeIllegalOpCode
// - - NativeGeneralJump
// - - NativeReturn
// - - NativeReturnX (return with argument)
// - - NativePushConst
// - - NativeTstRegMem

// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.

class NativeInstruction VALUE_OBJ_CLASS_SPEC {
  friend class Relocation;
  friend bool is_NativeCallTrampolineStub_at(address);
 public:
  enum { instruction_size = 4 };
  inline bool is_nop();
  inline bool is_illegal();
  inline bool is_return();
  bool is_jump();
  inline bool is_jump_or_nop();
  inline bool is_cond_jump();
  bool is_safepoint_poll();
  inline bool is_mov_literal64();
  bool is_movz();
  bool is_movk();
  bool is_sigill_zombie_not_entrant();

 protected:
  address addr_at(int offset) const    { return address(this) + offset; }

  s_char sbyte_at(int offset) const    { return *(s_char*) addr_at(offset); }
  u_char ubyte_at(int offset) const    { return *(u_char*) addr_at(offset); }

  jint int_at(int offset) const        { return *(jint*) addr_at(offset); }
  juint uint_at(int offset) const      { return *(juint*) addr_at(offset); }

  address ptr_at(int offset) const     { return *(address*) addr_at(offset); }

  oop  oop_at (int offset) const       { return *(oop*) addr_at(offset); }


  void set_char_at(int offset, char c)        { *addr_at(offset) = (u_char)c; }
  void set_int_at(int offset, jint  i)        { *(jint*)addr_at(offset) = i; }
  void set_uint_at(int offset, jint  i)       { *(juint*)addr_at(offset) = i; }
  void set_ptr_at (int offset, address  ptr)  { *(address*) addr_at(offset) = ptr; }
  void set_oop_at (int offset, oop  o)        { *(oop*) addr_at(offset) = o; }

 public:

  // unit test stuff
  static void test() {}                 // override for testing

  inline friend NativeInstruction* nativeInstruction_at(address address);

  static bool is_adrp_at(address instr);
  static bool is_ldr_literal_at(address instr);
  static bool is_ldrw_to_zr(address instr);

  static bool maybe_cpool_ref(address instr) {
    return is_adrp_at(instr) || is_ldr_literal_at(instr);
  }

  bool is_Membar() {
    unsigned int insn = uint_at(0);
    return Instruction_aarch64::extract(insn, 31, 12) == 0b11010101000000110011 &&
      Instruction_aarch64::extract(insn, 7, 0) == 0b10111111;
  }
};

inline NativeInstruction* nativeInstruction_at(address address) {
  return (NativeInstruction*)address;
}

// The natural type of an AArch64 instruction is uint32_t
inline NativeInstruction* nativeInstruction_at(uint32_t *address) {
  return (NativeInstruction*)address;
}

inline NativeCall* nativeCall_at(address address);
// The NativeCall is an abstraction for accessing/manipulating native call imm32/rel32off
// instructions (used to manipulate inline caches, primitive & dll calls, etc.).

class NativeCall: public NativeInstruction {
 public:
  enum Aarch64_specific_constants {
    instruction_size            =    4,
    instruction_offset          =    0,
    displacement_offset         =    0,
    return_address_offset       =    4
  };

  enum { cache_line_size = BytesPerWord };  // conservative estimate!
  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(return_address_offset); }
  int   displacement() const                { return (int_at(displacement_offset) << 6) >> 4; }
  address displacement_address() const      { return addr_at(displacement_offset); }
  address return_address() const            { return addr_at(return_address_offset); }
  address destination() const;

  void  set_destination(address dest)       {
    int offset = dest - instruction_address();
    unsigned int insn = 0b100101 << 26;
    assert((offset & 3) == 0, "should be");
    offset >>= 2;
    offset &= (1 << 26) - 1; // mask off insn part
    insn |= offset;
    set_int_at(displacement_offset, insn);
  }

  void  verify_alignment()                       { ; }
  void  verify();
  void  print();

  // Creation
  inline friend NativeCall* nativeCall_at(address address);
  inline friend NativeCall* nativeCall_before(address return_address);

  static bool is_call_at(address instr) {
    const uint32_t insn = (*(uint32_t*)instr);
    return (insn >> 26) == 0b100101;
  }

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

  // The parameter assert_lock disables the assertion during code generation.
  void set_destination_mt_safe(address dest, bool assert_lock = true);

  address get_trampoline();
};

inline NativeCall* nativeCall_at(address address) {
  NativeCall* call = (NativeCall*)(address - NativeCall::instruction_offset);
#ifdef ASSERT
  call->verify();
#endif
  return call;
}

inline NativeCall* nativeCall_before(address return_address) {
  NativeCall* call = (NativeCall*)(return_address - NativeCall::return_address_offset);
#ifdef ASSERT
  call->verify();
#endif
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

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  {
    if (nativeInstruction_at(instruction_address())->is_movz())
      // Assume movz, movk, movk
      return addr_at(instruction_size);
    else if (is_adrp_at(instruction_address()))
      return addr_at(2*4);
    else if (is_ldr_literal_at(instruction_address()))
      return(addr_at(4));
    assert(false, "Unknown instruction in NativeMovConstReg");
    return NULL;
  }

  intptr_t data() const;
  void  set_data(intptr_t x);

  void flush() {
    if (! maybe_cpool_ref(instruction_address())) {
      ICache::invalidate_range(instruction_address(), instruction_size);
    }
  }

  void  verify();
  void  print();

  // unit test stuff
  static void test() {}

  // Creation
  inline friend NativeMovConstReg* nativeMovConstReg_at(address address);
  inline friend NativeMovConstReg* nativeMovConstReg_before(address address);
};

inline NativeMovConstReg* nativeMovConstReg_at(address address) {
  NativeMovConstReg* test = (NativeMovConstReg*)(address - NativeMovConstReg::instruction_offset);
#ifdef ASSERT
  test->verify();
#endif
  return test;
}

inline NativeMovConstReg* nativeMovConstReg_before(address address) {
  NativeMovConstReg* test = (NativeMovConstReg*)(address - NativeMovConstReg::instruction_size - NativeMovConstReg::instruction_offset);
#ifdef ASSERT
  test->verify();
#endif
  return test;
}

class NativeMovConstRegPatching: public NativeMovConstReg {
 private:
    friend NativeMovConstRegPatching* nativeMovConstRegPatching_at(address address) {
    NativeMovConstRegPatching* test = (NativeMovConstRegPatching*)(address - instruction_offset);
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
    }
};

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
  int instruction_start() const;

  address instruction_address() const;

  address next_instruction_address() const;

  int   offset() const;

  void  set_offset(int x);

  void  add_offset_in_bytes(int add_offset)     { set_offset ( ( offset() + add_offset ) ); }

  void verify();
  void print ();

  // unit test stuff
  static void test() {}

 private:
  inline friend NativeMovRegMem* nativeMovRegMem_at (address address);
};

inline NativeMovRegMem* nativeMovRegMem_at (address address) {
  NativeMovRegMem* test = (NativeMovRegMem*)(address - NativeMovRegMem::instruction_offset);
#ifdef ASSERT
  test->verify();
#endif
  return test;
}

class NativeMovRegMemPatching: public NativeMovRegMem {
 private:
  friend NativeMovRegMemPatching* nativeMovRegMemPatching_at (address address) {Unimplemented(); return 0;  }
};

// An interface for accessing/manipulating native leal instruction of form:
//        leal reg, [reg + offset]

class NativeLoadAddress: public NativeMovRegMem {
  static const bool has_rex = true;
  static const int rex_size = 1;
 public:

  void verify();
  void print ();

  // unit test stuff
  static void test() {}
};

class NativeJump: public NativeInstruction {
 public:
  enum AArch64_specific_constants {
    instruction_size            =    4,
    instruction_offset          =    0,
    data_offset                 =    0,
    next_instruction_offset     =    4
  };

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(instruction_size); }
  address jump_destination() const;
  void set_jump_destination(address dest);

  // Creation
  inline friend NativeJump* nativeJump_at(address address);

  void verify();

  // Unit testing stuff
  static void test() {}

  // Insertion of native jump instruction
  static void insert(address code_pos, address entry);
  // MT-safe insertion of native jump at verified method entry
  static void check_verified_entry_alignment(address entry, address verified_entry);
  static void patch_verified_entry(address entry, address verified_entry, address dest);
};

inline NativeJump* nativeJump_at(address address) {
  NativeJump* jump = (NativeJump*)(address - NativeJump::instruction_offset);
#ifdef ASSERT
  jump->verify();
#endif
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
  static void insert_unconditional(address code_pos, address entry);
  static void replace_mt_safe(address instr_addr, address code_buffer);
  static void verify();
};

inline NativeGeneralJump* nativeGeneralJump_at(address address) {
  NativeGeneralJump* jump = (NativeGeneralJump*)(address);
  debug_only(jump->verify();)
  return jump;
}

class NativePopReg : public NativeInstruction {
 public:
  // Insert a pop instruction
  static void insert(address code_pos, Register reg);
};


class NativeIllegalInstruction: public NativeInstruction {
 public:
  // Insert illegal opcode as specific address
  static void insert(address code_pos);
};

// return instruction that does not pop values of the stack
class NativeReturn: public NativeInstruction {
 public:
};

// return instruction that does pop values of the stack
class NativeReturnX: public NativeInstruction {
 public:
};

// Simple test vs memory
class NativeTstRegMem: public NativeInstruction {
 public:
};

inline bool NativeInstruction::is_nop()         {
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

  address destination(nmethod *nm = NULL) const;
  void set_destination(address new_destination);
  ptrdiff_t destination_offset() const;
};

inline bool is_NativeCallTrampolineStub_at(address addr) {
  // Ensure that the stub is exactly
  //      ldr   xscratch1, L
  //      br    xscratch1
  // L:
  uint32_t *i = (uint32_t *)addr;
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

inline NativeMembar *NativeMembar_at(address addr) {
  assert(nativeInstruction_at(addr)->is_Membar(), "no membar found");
  return (NativeMembar*)addr;
}

#endif // CPU_AARCH64_VM_NATIVEINST_AARCH64_HPP

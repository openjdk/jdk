/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2018, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_NATIVEINST_RISCV_HPP
#define CPU_RISCV_NATIVEINST_RISCV_HPP

#include "asm/assembler.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"

// We have interfaces for the following instructions:
// - NativeInstruction
// - - NativeCall
// - - NativeMovConstReg
// - - NativeMovRegMem
// - - NativeJump
// - - NativeGeneralJump
// - - NativeIllegalInstruction
// - - NativeCallTrampolineStub
// - - NativeMembar

// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.

class NativeCall;

class NativeInstruction {
  friend class Relocation;
  friend bool is_NativeCallTrampolineStub_at(address);
 public:
  enum {
    instruction_size = 4,
    compressed_instruction_size = 2,
  };

  juint encoding() const {
    return uint_at(0);
  }

  bool is_jal()                             const { return is_jal_at(addr_at(0));         }
  bool is_movptr()                          const { return is_movptr_at(addr_at(0));      }
  bool is_call()                            const { return is_call_at(addr_at(0));        }
  bool is_jump()                            const { return is_jump_at(addr_at(0));        }

  static bool is_jal_at(address instr)        { assert_cond(instr != NULL); return extract_opcode(instr) == 0b1101111; }
  static bool is_jalr_at(address instr)       { assert_cond(instr != NULL); return extract_opcode(instr) == 0b1100111 && extract_funct3(instr) == 0b000; }
  static bool is_branch_at(address instr)     { assert_cond(instr != NULL); return extract_opcode(instr) == 0b1100011; }
  static bool is_ld_at(address instr)         { assert_cond(instr != NULL); return is_load_at(instr) && extract_funct3(instr) == 0b011; }
  static bool is_load_at(address instr)       { assert_cond(instr != NULL); return extract_opcode(instr) == 0b0000011; }
  static bool is_float_load_at(address instr) { assert_cond(instr != NULL); return extract_opcode(instr) == 0b0000111; }
  static bool is_auipc_at(address instr)      { assert_cond(instr != NULL); return extract_opcode(instr) == 0b0010111; }
  static bool is_jump_at(address instr)       { assert_cond(instr != NULL); return is_branch_at(instr) || is_jal_at(instr) || is_jalr_at(instr); }
  static bool is_addi_at(address instr)       { assert_cond(instr != NULL); return extract_opcode(instr) == 0b0010011 && extract_funct3(instr) == 0b000; }
  static bool is_addiw_at(address instr)      { assert_cond(instr != NULL); return extract_opcode(instr) == 0b0011011 && extract_funct3(instr) == 0b000; }
  static bool is_lui_at(address instr)        { assert_cond(instr != NULL); return extract_opcode(instr) == 0b0110111; }
  static bool is_slli_shift_at(address instr, uint32_t shift) {
    assert_cond(instr != NULL);
    return (extract_opcode(instr) == 0b0010011 && // opcode field
            extract_funct3(instr) == 0b001 &&     // funct3 field, select the type of operation
            Assembler::extract(Assembler::ld_instr(instr), 25, 20) == shift);    // shamt field
  }

  static Register extract_rs1(address instr);
  static Register extract_rs2(address instr);
  static Register extract_rd(address instr);
  static uint32_t extract_opcode(address instr);
  static uint32_t extract_funct3(address instr);

  // the instruction sequence of movptr is as below:
  //     lui
  //     addi
  //     slli
  //     addi
  //     slli
  //     addi/jalr/load
  static bool check_movptr_data_dependency(address instr) {
    address lui = instr;
    address addi1 = lui + instruction_size;
    address slli1 = addi1 + instruction_size;
    address addi2 = slli1 + instruction_size;
    address slli2 = addi2 + instruction_size;
    address last_instr = slli2 + instruction_size;
    return extract_rs1(addi1) == extract_rd(lui) &&
           extract_rs1(addi1) == extract_rd(addi1) &&
           extract_rs1(slli1) == extract_rd(addi1) &&
           extract_rs1(slli1) == extract_rd(slli1) &&
           extract_rs1(addi2) == extract_rd(slli1) &&
           extract_rs1(addi2) == extract_rd(addi2) &&
           extract_rs1(slli2) == extract_rd(addi2) &&
           extract_rs1(slli2) == extract_rd(slli2) &&
           extract_rs1(last_instr) == extract_rd(slli2);
  }

  // the instruction sequence of li64 is as below:
  //     lui
  //     addi
  //     slli
  //     addi
  //     slli
  //     addi
  //     slli
  //     addi
  static bool check_li64_data_dependency(address instr) {
    address lui = instr;
    address addi1 = lui + instruction_size;
    address slli1 = addi1 + instruction_size;
    address addi2 = slli1 + instruction_size;
    address slli2 = addi2 + instruction_size;
    address addi3 = slli2 + instruction_size;
    address slli3 = addi3 + instruction_size;
    address addi4 = slli3 + instruction_size;
    return extract_rs1(addi1) == extract_rd(lui) &&
           extract_rs1(addi1) == extract_rd(addi1) &&
           extract_rs1(slli1) == extract_rd(addi1) &&
           extract_rs1(slli1) == extract_rd(slli1) &&
           extract_rs1(addi2) == extract_rd(slli1) &&
           extract_rs1(addi2) == extract_rd(addi2) &&
           extract_rs1(slli2) == extract_rd(addi2) &&
           extract_rs1(slli2) == extract_rd(slli2) &&
           extract_rs1(addi3) == extract_rd(slli2) &&
           extract_rs1(addi3) == extract_rd(addi3) &&
           extract_rs1(slli3) == extract_rd(addi3) &&
           extract_rs1(slli3) == extract_rd(slli3) &&
           extract_rs1(addi4) == extract_rd(slli3) &&
           extract_rs1(addi4) == extract_rd(addi4);
  }

  // the instruction sequence of li32 is as below:
  //     lui
  //     addiw
  static bool check_li32_data_dependency(address instr) {
    address lui = instr;
    address addiw = lui + instruction_size;

    return extract_rs1(addiw) == extract_rd(lui) &&
           extract_rs1(addiw) == extract_rd(addiw);
  }

  // the instruction sequence of pc-relative is as below:
  //     auipc
  //     jalr/addi/load/float_load
  static bool check_pc_relative_data_dependency(address instr) {
    address auipc = instr;
    address last_instr = auipc + instruction_size;

    return extract_rs1(last_instr) == extract_rd(auipc);
  }

  // the instruction sequence of load_label is as below:
  //     auipc
  //     load
  static bool check_load_pc_relative_data_dependency(address instr) {
    address auipc = instr;
    address load = auipc + instruction_size;

    return extract_rd(load) == extract_rd(auipc) &&
           extract_rs1(load) == extract_rd(load);
  }

  static bool is_movptr_at(address instr);
  static bool is_li32_at(address instr);
  static bool is_li64_at(address instr);
  static bool is_pc_relative_at(address branch);
  static bool is_load_pc_relative_at(address branch);

  static bool is_call_at(address instr) {
    if (is_jal_at(instr) || is_jalr_at(instr)) {
      return true;
    }
    return false;
  }
  static bool is_lwu_to_zr(address instr);

  inline bool is_nop();
  inline bool is_jump_or_nop();
  bool is_safepoint_poll();
  bool is_sigill_zombie_not_entrant();
  bool is_stop();

 protected:
  address addr_at(int offset) const    { return address(this) + offset; }

  jint int_at(int offset) const        { return (jint)Bytes::get_native_u4(addr_at(offset)); }
  juint uint_at(int offset) const      { return Bytes::get_native_u4(addr_at(offset)); }

  address ptr_at(int offset) const     { return (address)Bytes::get_native_u8(addr_at(offset)); }

  oop  oop_at (int offset) const       { return cast_to_oop(Bytes::get_native_u8(addr_at(offset))); }


  void set_int_at(int offset, jint i)        { Bytes::put_native_u4(addr_at(offset), i); }
  void set_uint_at(int offset, jint i)       { Bytes::put_native_u4(addr_at(offset), i); }
  void set_ptr_at (int offset, address ptr)  { Bytes::put_native_u8(addr_at(offset), (u8)ptr); }
  void set_oop_at (int offset, oop o)        { Bytes::put_native_u8(addr_at(offset), cast_from_oop<u8>(o)); }

 public:

  inline friend NativeInstruction* nativeInstruction_at(address addr);

  static bool maybe_cpool_ref(address instr) {
    return is_auipc_at(instr);
  }

  bool is_membar() {
    return (uint_at(0) & 0x7f) == 0b1111 && extract_funct3(addr_at(0)) == 0;
  }
};

inline NativeInstruction* nativeInstruction_at(address addr) {
  return (NativeInstruction*)addr;
}

// The natural type of an RISCV instruction is uint32_t
inline NativeInstruction* nativeInstruction_at(uint32_t *addr) {
  return (NativeInstruction*)addr;
}

inline NativeCall* nativeCall_at(address addr);
// The NativeCall is an abstraction for accessing/manipulating native
// call instructions (used to manipulate inline caches, primitive &
// DSO calls, etc.).

class NativeCall: public NativeInstruction {
 public:
  enum RISCV_specific_constants {
    instruction_size            =    4,
    instruction_offset          =    0,
    displacement_offset         =    0,
    return_address_offset       =    4
  };

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(return_address_offset); }
  address return_address() const            { return addr_at(return_address_offset); }
  address destination() const;

  void set_destination(address dest) {
    assert(is_jal(), "Should be jal instruction!");
    intptr_t offset = (intptr_t)(dest - instruction_address());
    assert((offset & 0x1) == 0, "bad alignment");
    assert(Assembler::is_simm21(offset), "encoding constraint");
    unsigned int insn = 0b1101111; // jal
    address pInsn = (address)(&insn);
    Assembler::patch(pInsn, 31, 31, (offset >> 20) & 0x1);
    Assembler::patch(pInsn, 30, 21, (offset >> 1) & 0x3ff);
    Assembler::patch(pInsn, 20, 20, (offset >> 11) & 0x1);
    Assembler::patch(pInsn, 19, 12, (offset >> 12) & 0xff);
    Assembler::patch(pInsn, 11, 7, ra->encoding()); // Rd must be x1, need ra
    set_int_at(displacement_offset, insn);
  }

  void verify_alignment() {} // do nothing on riscv
  void verify();
  void print();

  // Creation
  inline friend NativeCall* nativeCall_at(address addr);
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

  // The parameter assert_lock disables the assertion during code generation.
  void set_destination_mt_safe(address dest, bool assert_lock = true);

  address get_trampoline();
};

inline NativeCall* nativeCall_at(address addr) {
  assert_cond(addr != NULL);
  NativeCall* call = (NativeCall*)(addr - NativeCall::instruction_offset);
  DEBUG_ONLY(call->verify());
  return call;
}

inline NativeCall* nativeCall_before(address return_address) {
  assert_cond(return_address != NULL);
  NativeCall* call = (NativeCall*)(return_address - NativeCall::return_address_offset);
  DEBUG_ONLY(call->verify());
  return call;
}

// An interface for accessing/manipulating native mov reg, imm instructions.
// (used to manipulate inlined 64-bit data calls, etc.)
class NativeMovConstReg: public NativeInstruction {
 public:
  enum RISCV_specific_constants {
    movptr_instruction_size             =    6 * NativeInstruction::instruction_size, // lui, addi, slli, addi, slli, addi.  See movptr().
    load_pc_relative_instruction_size   =    2 * NativeInstruction::instruction_size, // auipc, ld
    instruction_offset                  =    0,
    displacement_offset                 =    0
  };

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  {
    // if the instruction at 5 * instruction_size is addi,
    // it means a lui + addi + slli + addi + slli + addi instruction sequence,
    // and the next instruction address should be addr_at(6 * instruction_size).
    // However, when the instruction at 5 * instruction_size isn't addi,
    // the next instruction address should be addr_at(5 * instruction_size)
    if (nativeInstruction_at(instruction_address())->is_movptr()) {
      if (is_addi_at(addr_at(movptr_instruction_size - NativeInstruction::instruction_size))) {
        // Assume: lui, addi, slli, addi, slli, addi
        return addr_at(movptr_instruction_size);
      } else {
        // Assume: lui, addi, slli, addi, slli
        return addr_at(movptr_instruction_size - NativeInstruction::instruction_size);
      }
    } else if (is_load_pc_relative_at(instruction_address())) {
      // Assume: auipc, ld
      return addr_at(load_pc_relative_instruction_size);
    }
    guarantee(false, "Unknown instruction in NativeMovConstReg");
    return NULL;
  }

  intptr_t data() const;
  void set_data(intptr_t x);

  void flush() {
    if (!maybe_cpool_ref(instruction_address())) {
      ICache::invalidate_range(instruction_address(), movptr_instruction_size);
    }
  }

  void verify();
  void print();

  // Creation
  inline friend NativeMovConstReg* nativeMovConstReg_at(address addr);
  inline friend NativeMovConstReg* nativeMovConstReg_before(address addr);
};

inline NativeMovConstReg* nativeMovConstReg_at(address addr) {
  assert_cond(addr != NULL);
  NativeMovConstReg* test = (NativeMovConstReg*)(addr - NativeMovConstReg::instruction_offset);
  DEBUG_ONLY(test->verify());
  return test;
}

inline NativeMovConstReg* nativeMovConstReg_before(address addr) {
  assert_cond(addr != NULL);
  NativeMovConstReg* test = (NativeMovConstReg*)(addr - NativeMovConstReg::instruction_size - NativeMovConstReg::instruction_offset);
  DEBUG_ONLY(test->verify());
  return test;
}

// RISCV should not use C1 runtime patching, but still implement
// NativeMovRegMem to keep some compilers happy.
class NativeMovRegMem: public NativeInstruction {
 public:
  enum RISCV_specific_constants {
    instruction_size            =    NativeInstruction::instruction_size,
    instruction_offset          =    0,
    data_offset                 =    0,
    next_instruction_offset     =    NativeInstruction::instruction_size
  };

  int instruction_start() const { return instruction_offset; }

  address instruction_address() const { return addr_at(instruction_offset); }

  int num_bytes_to_end_of_patch() const { return instruction_offset + instruction_size; }

  int offset() const;

  void set_offset(int x);

  void add_offset_in_bytes(int add_offset) {
    set_offset(offset() + add_offset);
  }

  void verify();
  void print();

 private:
  inline friend NativeMovRegMem* nativeMovRegMem_at(address addr);
};

inline NativeMovRegMem* nativeMovRegMem_at(address addr) {
  NativeMovRegMem* test = (NativeMovRegMem*)(addr - NativeMovRegMem::instruction_offset);
  DEBUG_ONLY(test->verify());
  return test;
}

class NativeJump: public NativeInstruction {
 public:
  enum RISCV_specific_constants {
    instruction_size            =    NativeInstruction::instruction_size,
    instruction_offset          =    0,
    data_offset                 =    0,
    next_instruction_offset     =    NativeInstruction::instruction_size
  };

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(instruction_size); }
  address jump_destination() const;
  void set_jump_destination(address dest);

  // Creation
  inline friend NativeJump* nativeJump_at(address address);

  void verify();

  // Insertion of native jump instruction
  static void insert(address code_pos, address entry);
  // MT-safe insertion of native jump at verified method entry
  static void check_verified_entry_alignment(address entry, address verified_entry);
  static void patch_verified_entry(address entry, address verified_entry, address dest);
};

inline NativeJump* nativeJump_at(address addr) {
  NativeJump* jump = (NativeJump*)(addr - NativeJump::instruction_offset);
  DEBUG_ONLY(jump->verify());
  return jump;
}

class NativeGeneralJump: public NativeJump {
public:
  enum RISCV_specific_constants {
    instruction_size            =    6 * NativeInstruction::instruction_size, // lui, addi, slli, addi, slli, jalr
    instruction_offset          =    0,
    data_offset                 =    0,
    next_instruction_offset     =    6 * NativeInstruction::instruction_size  // lui, addi, slli, addi, slli, jalr
  };

  address jump_destination() const;

  static void insert_unconditional(address code_pos, address entry);
  static void replace_mt_safe(address instr_addr, address code_buffer);
};

inline NativeGeneralJump* nativeGeneralJump_at(address addr) {
  assert_cond(addr != NULL);
  NativeGeneralJump* jump = (NativeGeneralJump*)(addr);
  debug_only(jump->verify();)
  return jump;
}

class NativeIllegalInstruction: public NativeInstruction {
 public:
  // Insert illegal opcode as specific address
  static void insert(address code_pos);
};

inline bool NativeInstruction::is_nop()         {
  uint32_t insn = Assembler::ld_instr(addr_at(0));
  return insn == 0x13;
}

inline bool NativeInstruction::is_jump_or_nop() {
  return is_nop() || is_jump();
}

// Call trampoline stubs.
class NativeCallTrampolineStub : public NativeInstruction {
 public:

  enum RISCV_specific_constants {
    // Refer to function emit_trampoline_stub.
    instruction_size = 3 * NativeInstruction::instruction_size + wordSize, // auipc + ld + jr + target address
    data_offset      = 3 * NativeInstruction::instruction_size,            // auipc + ld + jr
  };

  address destination(nmethod *nm = NULL) const;
  void set_destination(address new_destination);
  ptrdiff_t destination_offset() const;
};

inline bool is_NativeCallTrampolineStub_at(address addr) {
  // Ensure that the stub is exactly
  //      ld   t0, L--->auipc + ld
  //      jr   t0
  // L:

  // judge inst + register + imm
  // 1). check the instructions: auipc + ld + jalr
  // 2). check if auipc[11:7] == t0 and ld[11:7] == t0 and ld[19:15] == t0 && jr[19:15] == t0
  // 3). check if the offset in ld[31:20] equals the data_offset
  assert_cond(addr != NULL);
  const int instr_size = NativeInstruction::instruction_size;
  if (NativeInstruction::is_auipc_at(addr) &&
      NativeInstruction::is_ld_at(addr + instr_size) &&
      NativeInstruction::is_jalr_at(addr + 2 * instr_size) &&
      (NativeInstruction::extract_rd(addr)                    == x5) &&
      (NativeInstruction::extract_rd(addr + instr_size)       == x5) &&
      (NativeInstruction::extract_rs1(addr + instr_size)      == x5) &&
      (NativeInstruction::extract_rs1(addr + 2 * instr_size)  == x5) &&
      (Assembler::extract(Assembler::ld_instr(addr + 4), 31, 20) == NativeCallTrampolineStub::data_offset)) {
    return true;
  }
  return false;
}

inline NativeCallTrampolineStub* nativeCallTrampolineStub_at(address addr) {
  assert_cond(addr != NULL);
  assert(is_NativeCallTrampolineStub_at(addr), "no call trampoline found");
  return (NativeCallTrampolineStub*)addr;
}

class NativeMembar : public NativeInstruction {
public:
  uint32_t get_kind();
  void set_kind(uint32_t order_kind);
};

inline NativeMembar *NativeMembar_at(address addr) {
  assert_cond(addr != NULL);
  assert(nativeInstruction_at(addr)->is_membar(), "no membar found");
  return (NativeMembar*)addr;
}

#endif // CPU_RISCV_NATIVEINST_RISCV_HPP

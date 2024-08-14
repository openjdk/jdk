/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2018, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "macroAssembler_riscv.hpp"
#include "asm/assembler.hpp"
#include "runtime/continuation.hpp"
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
// - - NativePostCallNop
// - - NativeDeoptInstruction

// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.

class NativeCall;

class NativeInstruction {
  friend class Relocation;
 public:
  enum {
    instruction_size = MacroAssembler::instruction_size,
    compressed_instruction_size = MacroAssembler::compressed_instruction_size,
  };

  juint encoding() const {
    return uint_at(0);
  }

  bool is_jal()                             const { return MacroAssembler::is_jal_at(addr_at(0));         }
  bool is_movptr()                          const { return MacroAssembler::is_movptr1_at(addr_at(0)) ||
                                                           MacroAssembler::is_movptr2_at(addr_at(0));     }
  bool is_movptr1()                         const { return MacroAssembler::is_movptr1_at(addr_at(0));     }
  bool is_movptr2()                         const { return MacroAssembler::is_movptr2_at(addr_at(0));     }
  bool is_auipc()                           const { return MacroAssembler::is_auipc_at(addr_at(0));       }
  bool is_jump()                            const { return MacroAssembler::is_jump_at(addr_at(0));        }
  bool is_call()                            const { return is_call_at(addr_at(0));                        }
  static bool is_call_at(address addr);

  bool is_nop() const;
  bool is_jump_or_nop();
  bool is_safepoint_poll();
  bool is_sigill_not_entrant();
  bool is_stop();

 protected:
  address addr_at(int offset) const { return address(this) + offset; }
  jint     int_at(int offset) const { return (jint)      Bytes::get_native_u4(addr_at(offset));  }
  juint   uint_at(int offset) const { return             Bytes::get_native_u4(addr_at(offset));  }
  address  ptr_at(int offset) const { return (address)   Bytes::get_native_u8(addr_at(offset));  }
  oop      oop_at(int offset) const { return cast_to_oop(Bytes::get_native_u8(addr_at(offset))); }


  void  set_int_at(int offset, jint i)      { Bytes::put_native_u4(addr_at(offset), i); }
  void set_uint_at(int offset, jint i)      { Bytes::put_native_u4(addr_at(offset), i); }
  void  set_ptr_at(int offset, address ptr) { Bytes::put_native_u8(addr_at(offset), (u8)ptr); }
  void  set_oop_at(int offset, oop o)       { Bytes::put_native_u8(addr_at(offset), cast_from_oop<u8>(o)); }

  static void     set_data64_at(address dest, uint64_t data) { Bytes::put_native_u8(dest, (u8)data); }
  static uint64_t get_data64_at(address src)                 { return Bytes::get_native_u8(src); }

 public:

  inline friend NativeInstruction* nativeInstruction_at(address addr);

  static bool maybe_cpool_ref(address instr) {
    return MacroAssembler::is_auipc_at(instr);
  }
};

NativeInstruction* nativeInstruction_at(address addr) {
  return (NativeInstruction*)addr;
}

NativeCall* nativeCall_at(address addr);
NativeCall* nativeCall_before(address return_address);

// The NativeCall is an abstraction for accessing/manipulating native
// call instructions (used to manipulate inline caches, primitive &
// DSO calls, etc.).
class NativeCall: private NativeInstruction {
 // private: when common code is using byte_size()
 private:
  enum {
    // Use byte_size() as it can be changed in runtime
    // Since instruction_size exists on NativeInstruction we need
    // to overload and hide it.
    instruction_size = 3 * Assembler::instruction_size // auipc + ld + jalr
  };
 public:

  static int byte_size() {
    if (UseTrampolines) {
      return NativeInstruction::instruction_size; // jal
    } else {
      return 3 * NativeInstruction::instruction_size; // auipc + ld + jalr
    }
  }
  // Creation
  friend NativeCall* nativeCall_at(address addr);
  friend NativeCall* nativeCall_before(address return_address);

  address instruction_address() const;
  address next_instruction_address() const;
  address return_address() const;
  address destination() const;
  address reloc_destination(address orig_address);

  void verify_alignment() {} // do nothing on riscv
  void verify();
  void print();

  void set_destination(address dest);
  bool set_destination_mt_safe(address dest, bool assert_lock = true);
  bool reloc_set_destination(address dest);

  static bool is_at(address addr);
  static bool is_call_before(address return_address);
};

// An interface for accessing/manipulating native mov reg, imm instructions.
// (used to manipulate inlined 64-bit data calls, etc.)
class NativeMovConstReg: public NativeInstruction {
 public:
  enum RISCV_specific_constants {
    movptr1_instruction_size            =    MacroAssembler::movptr1_instruction_size, // lui, addi, slli, addi, slli, addi.  See movptr1().
    movptr2_instruction_size            =    MacroAssembler::movptr2_instruction_size, // lui, lui, slli, add, addi.  See movptr2().
    load_pc_relative_instruction_size   =    MacroAssembler::load_pc_relative_instruction_size // auipc, ld
  };

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  {
    // if the instruction at 5 * instruction_size is addi,
    // it means a lui + addi + slli + addi + slli + addi instruction sequence,
    // and the next instruction address should be addr_at(6 * instruction_size).
    // However, when the instruction at 5 * instruction_size isn't addi,
    // the next instruction address should be addr_at(5 * instruction_size)
    if (MacroAssembler::is_movptr1_at(instruction_address())) {
      if (MacroAssembler::is_addi_at(addr_at(movptr1_instruction_size - NativeInstruction::instruction_size))) {
        // Assume: lui, addi, slli, addi, slli, addi
        return addr_at(movptr1_instruction_size);
      } else {
        // Assume: lui, addi, slli, addi, slli
        return addr_at(movptr1_instruction_size - NativeInstruction::instruction_size);
      }
    } else if (MacroAssembler::is_movptr2_at(instruction_address())) {
      if (MacroAssembler::is_addi_at(addr_at(movptr2_instruction_size - NativeInstruction::instruction_size))) {
        // Assume: lui, lui, slli, add, addi
        return addr_at(movptr2_instruction_size);
      } else {
        // Assume: lui, lui, slli, add
        return addr_at(movptr2_instruction_size - NativeInstruction::instruction_size);
      }
    } else if (MacroAssembler::is_load_pc_relative_at(instruction_address())) {
      // Assume: auipc, ld
      return addr_at(load_pc_relative_instruction_size);
    }
    guarantee(false, "Unknown instruction in NativeMovConstReg");
    return nullptr;
  }

  intptr_t data() const;
  void set_data(intptr_t x);

  void verify();
  void print();

  // Creation
  inline friend NativeMovConstReg* nativeMovConstReg_at(address addr);
  inline friend NativeMovConstReg* nativeMovConstReg_before(address addr);
};

inline NativeMovConstReg* nativeMovConstReg_at(address addr) {
  assert_cond(addr != nullptr);
  NativeMovConstReg* test = (NativeMovConstReg*)(addr);
  DEBUG_ONLY(test->verify());
  return test;
}

inline NativeMovConstReg* nativeMovConstReg_before(address addr) {
  assert_cond(addr != nullptr);
  NativeMovConstReg* test = (NativeMovConstReg*)(addr - NativeMovConstReg::instruction_size);
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
    instruction_size            =    5 * NativeInstruction::instruction_size, // lui, lui, slli, add, jalr
  };

  address jump_destination() const;

  static void insert_unconditional(address code_pos, address entry);
  static void replace_mt_safe(address instr_addr, address code_buffer);
};

inline NativeGeneralJump* nativeGeneralJump_at(address addr) {
  assert_cond(addr != nullptr);
  NativeGeneralJump* jump = (NativeGeneralJump*)(addr);
  debug_only(jump->verify();)
  return jump;
}

class NativeIllegalInstruction: public NativeInstruction {
 public:
  // Insert illegal opcode as specific address
  static void insert(address code_pos);
};

inline bool NativeInstruction::is_nop() const {
  uint32_t insn = Assembler::ld_instr(addr_at(0));
  return insn == 0x13;
}

inline bool NativeInstruction::is_jump_or_nop() {
  return is_nop() || is_jump();
}

// A NativePostCallNop takes the form of three instructions:
//     nop; lui zr, hi20; addiw zr, lo12
//
// The nop is patchable for a deoptimization trap. The lui and addiw
// instructions execute as nops but have a 20/12-bit payload in which we
// can store an offset from the initial nop to the nmethod.
class NativePostCallNop: public NativeInstruction {
public:
  bool check() const {
    // Check for two instructions: nop; lui zr, hi20
    // These instructions only ever appear together in a post-call
    // NOP, so it's unnecessary to check that the third instruction is
    // an addiw as well.
    return is_nop() && MacroAssembler::is_lui_to_zr_at(addr_at(4));
  }
  bool decode(int32_t& oopmap_slot, int32_t& cb_offset) const;
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

  void verify();

  static bool is_deopt_at(address instr) {
    assert(instr != nullptr, "");
    uint32_t value = Assembler::ld_instr(instr);
    // 0xc0201073 encodes CSRRW x0, instret, x0
    return value == 0xc0201073;
  }

  // MT-safe patching
  static void insert(address code_pos);
};

#endif // CPU_RISCV_NATIVEINST_RISCV_HPP

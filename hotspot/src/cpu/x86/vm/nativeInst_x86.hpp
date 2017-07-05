/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

 public:
  enum Intel_specific_constants {
    nop_instruction_code        = 0x90,
    nop_instruction_size        =    1
  };

  bool is_nop()                        { return ubyte_at(0) == nop_instruction_code; }
  bool is_dtrace_trap();
  inline bool is_call();
  inline bool is_illegal();
  inline bool is_return();
  inline bool is_jump();
  inline bool is_cond_jump();
  inline bool is_safepoint_poll();
  inline bool is_mov_literal64();

 protected:
  address addr_at(int offset) const    { return address(this) + offset; }

  s_char sbyte_at(int offset) const    { return *(s_char*) addr_at(offset); }
  u_char ubyte_at(int offset) const    { return *(u_char*) addr_at(offset); }

  jint int_at(int offset) const         { return *(jint*) addr_at(offset); }

  intptr_t ptr_at(int offset) const    { return *(intptr_t*) addr_at(offset); }

  oop  oop_at (int offset) const       { return *(oop*) addr_at(offset); }


  void set_char_at(int offset, char c)        { *addr_at(offset) = (u_char)c; wrote(offset); }
  void set_int_at(int offset, jint  i)        { *(jint*)addr_at(offset) = i;  wrote(offset); }
  void set_ptr_at (int offset, intptr_t  ptr) { *(intptr_t*) addr_at(offset) = ptr;  wrote(offset); }
  void set_oop_at (int offset, oop  o)        { *(oop*) addr_at(offset) = o;  wrote(offset); }

  // This doesn't really do anything on Intel, but it is the place where
  // cache invalidation belongs, generically:
  void wrote(int offset);

 public:

  // unit test stuff
  static void test() {}                 // override for testing

  inline friend NativeInstruction* nativeInstruction_at(address address);
};

inline NativeInstruction* nativeInstruction_at(address address) {
  NativeInstruction* inst = (NativeInstruction*)address;
#ifdef ASSERT
  //inst->verify();
#endif
  return inst;
}

inline NativeCall* nativeCall_at(address address);
// The NativeCall is an abstraction for accessing/manipulating native call imm32/rel32off
// instructions (used to manipulate inline caches, primitive & dll calls, etc.).

class NativeCall: public NativeInstruction {
 public:
  enum Intel_specific_constants {
    instruction_code            = 0xE8,
    instruction_size            =    5,
    instruction_offset          =    0,
    displacement_offset         =    1,
    return_address_offset       =    5
  };

  enum { cache_line_size = BytesPerWord };  // conservative estimate!

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(return_address_offset); }
  int   displacement() const                { return (jint) int_at(displacement_offset); }
  address displacement_address() const      { return addr_at(displacement_offset); }
  address return_address() const            { return addr_at(return_address_offset); }
  address destination() const;
  void  set_destination(address dest)       {
#ifdef AMD64
    assert((labs((intptr_t) dest - (intptr_t) return_address())  &
            0xFFFFFFFF00000000) == 0,
           "must be 32bit offset");
#endif // AMD64
    set_int_at(displacement_offset, dest - return_address());
  }
  void  set_destination_mt_safe(address dest);

  void  verify_alignment() { assert((intptr_t)addr_at(displacement_offset) % BytesPerInt == 0, "must be aligned"); }
  void  verify();
  void  print();

  // Creation
  inline friend NativeCall* nativeCall_at(address address);
  inline friend NativeCall* nativeCall_before(address return_address);

  static bool is_call_at(address instr) {
    return ((*instr) & 0xFF) == NativeCall::instruction_code;
  }

  static bool is_call_before(address return_address) {
    return is_call_at(return_address - NativeCall::return_address_offset);
  }

  static bool is_call_to(address instr, address target) {
    return nativeInstruction_at(instr)->is_call() &&
      nativeCall_at(instr)->destination() == target;
  }

  // MT-safe patching of a call instruction.
  static void insert(address code_pos, address entry);

  static void replace_mt_safe(address instr_addr, address code_buffer);
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

// An interface for accessing/manipulating native mov reg, imm32 instructions.
// (used to manipulate inlined 32bit data dll calls, etc.)
class NativeMovConstReg: public NativeInstruction {
#ifdef AMD64
  static const bool has_rex = true;
  static const int rex_size = 1;
#else
  static const bool has_rex = false;
  static const int rex_size = 0;
#endif // AMD64
 public:
  enum Intel_specific_constants {
    instruction_code            = 0xB8,
    instruction_size            =    1 + rex_size + wordSize,
    instruction_offset          =    0,
    data_offset                 =    1 + rex_size,
    next_instruction_offset     =    instruction_size,
    register_mask               = 0x07
  };

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(next_instruction_offset); }
  intptr_t data() const                     { return ptr_at(data_offset); }
  void  set_data(intptr_t x)                { set_ptr_at(data_offset, x); }

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
 public:
  enum Intel_specific_constants {
    instruction_prefix_wide_lo          = Assembler::REX,
    instruction_prefix_wide_hi          = Assembler::REX_WRXB,
    instruction_code_xor                = 0x33,
    instruction_extended_prefix         = 0x0F,
    instruction_code_mem2reg_movslq     = 0x63,
    instruction_code_mem2reg_movzxb     = 0xB6,
    instruction_code_mem2reg_movsxb     = 0xBE,
    instruction_code_mem2reg_movzxw     = 0xB7,
    instruction_code_mem2reg_movsxw     = 0xBF,
    instruction_operandsize_prefix      = 0x66,
    instruction_code_reg2mem            = 0x89,
    instruction_code_mem2reg            = 0x8b,
    instruction_code_reg2memb           = 0x88,
    instruction_code_mem2regb           = 0x8a,
    instruction_code_float_s            = 0xd9,
    instruction_code_float_d            = 0xdd,
    instruction_code_long_volatile      = 0xdf,
    instruction_code_xmm_ss_prefix      = 0xf3,
    instruction_code_xmm_sd_prefix      = 0xf2,
    instruction_code_xmm_code           = 0x0f,
    instruction_code_xmm_load           = 0x10,
    instruction_code_xmm_store          = 0x11,
    instruction_code_xmm_lpd            = 0x12,

    instruction_size                    = 4,
    instruction_offset                  = 0,
    data_offset                         = 2,
    next_instruction_offset             = 4
  };

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
  friend NativeMovRegMemPatching* nativeMovRegMemPatching_at (address address) {
    NativeMovRegMemPatching* test = (NativeMovRegMemPatching*)(address - instruction_offset);
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }
};



// An interface for accessing/manipulating native leal instruction of form:
//        leal reg, [reg + offset]

class NativeLoadAddress: public NativeMovRegMem {
#ifdef AMD64
  static const bool has_rex = true;
  static const int rex_size = 1;
#else
  static const bool has_rex = false;
  static const int rex_size = 0;
#endif // AMD64
 public:
  enum Intel_specific_constants {
    instruction_prefix_wide             = Assembler::REX_W,
    instruction_prefix_wide_extended    = Assembler::REX_WB,
    lea_instruction_code                = 0x8D,
    mov64_instruction_code              = 0xB8
  };

  void verify();
  void print ();

  // unit test stuff
  static void test() {}

 private:
  friend NativeLoadAddress* nativeLoadAddress_at (address address) {
    NativeLoadAddress* test = (NativeLoadAddress*)(address - instruction_offset);
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }
};

// jump rel32off

class NativeJump: public NativeInstruction {
 public:
  enum Intel_specific_constants {
    instruction_code            = 0xe9,
    instruction_size            =    5,
    instruction_offset          =    0,
    data_offset                 =    1,
    next_instruction_offset     =    5
  };

  address instruction_address() const       { return addr_at(instruction_offset); }
  address next_instruction_address() const  { return addr_at(next_instruction_offset); }
  address jump_destination() const          {
     address dest = (int_at(data_offset)+next_instruction_address());
     // 32bit used to encode unresolved jmp as jmp -1
     // 64bit can't produce this so it used jump to self.
     // Now 32bit and 64bit use jump to self as the unresolved address
     // which the inline cache code (and relocs) know about

     // return -1 if jump to self
    dest = (dest == (address) this) ? (address) -1 : dest;
    return dest;
  }

  void  set_jump_destination(address dest)  {
    intptr_t val = dest - next_instruction_address();
    if (dest == (address) -1) {
      val = -5; // jump to self
    }
#ifdef AMD64
    assert((labs(val)  & 0xFFFFFFFF00000000) == 0 || dest == (address)-1, "must be 32bit offset or -1");
#endif // AMD64
    set_int_at(data_offset, (jint)val);
  }

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

// Handles all kinds of jump on Intel. Long/far, conditional/unconditional
class NativeGeneralJump: public NativeInstruction {
 public:
  enum Intel_specific_constants {
    // Constants does not apply, since the lengths and offsets depends on the actual jump
    // used
    // Instruction codes:
    //   Unconditional jumps: 0xE9    (rel32off), 0xEB (rel8off)
    //   Conditional jumps:   0x0F8x  (rel32off), 0x7x (rel8off)
    unconditional_long_jump  = 0xe9,
    unconditional_short_jump = 0xeb,
    instruction_size = 5
  };

  address instruction_address() const       { return addr_at(0); }
  address jump_destination()    const;

  // Creation
  inline friend NativeGeneralJump* nativeGeneralJump_at(address address);

  // Insertion of native general jump instruction
  static void insert_unconditional(address code_pos, address entry);
  static void replace_mt_safe(address instr_addr, address code_buffer);

  void verify();
};

inline NativeGeneralJump* nativeGeneralJump_at(address address) {
  NativeGeneralJump* jump = (NativeGeneralJump*)(address);
  debug_only(jump->verify();)
  return jump;
}

class NativePopReg : public NativeInstruction {
 public:
  enum Intel_specific_constants {
    instruction_code            = 0x58,
    instruction_size            =    1,
    instruction_offset          =    0,
    data_offset                 =    1,
    next_instruction_offset     =    1
  };

  // Insert a pop instruction
  static void insert(address code_pos, Register reg);
};


class NativeIllegalInstruction: public NativeInstruction {
 public:
  enum Intel_specific_constants {
    instruction_code            = 0x0B0F,    // Real byte order is: 0x0F, 0x0B
    instruction_size            =    2,
    instruction_offset          =    0,
    next_instruction_offset     =    2
  };

  // Insert illegal opcode as specific address
  static void insert(address code_pos);
};

// return instruction that does not pop values of the stack
class NativeReturn: public NativeInstruction {
 public:
  enum Intel_specific_constants {
    instruction_code            = 0xC3,
    instruction_size            =    1,
    instruction_offset          =    0,
    next_instruction_offset     =    1
  };
};

// return instruction that does pop values of the stack
class NativeReturnX: public NativeInstruction {
 public:
  enum Intel_specific_constants {
    instruction_code            = 0xC2,
    instruction_size            =    2,
    instruction_offset          =    0,
    next_instruction_offset     =    2
  };
};

// Simple test vs memory
class NativeTstRegMem: public NativeInstruction {
 public:
  enum Intel_specific_constants {
    instruction_code_memXregl   = 0x85
  };
};

inline bool NativeInstruction::is_illegal()      { return (short)int_at(0) == (short)NativeIllegalInstruction::instruction_code; }
inline bool NativeInstruction::is_call()         { return ubyte_at(0) == NativeCall::instruction_code; }
inline bool NativeInstruction::is_return()       { return ubyte_at(0) == NativeReturn::instruction_code ||
                                                          ubyte_at(0) == NativeReturnX::instruction_code; }
inline bool NativeInstruction::is_jump()         { return ubyte_at(0) == NativeJump::instruction_code ||
                                                          ubyte_at(0) == 0xEB; /* short jump */ }
inline bool NativeInstruction::is_cond_jump()    { return (int_at(0) & 0xF0FF) == 0x800F /* long jump */ ||
                                                          (ubyte_at(0) & 0xF0) == 0x70;  /* short jump */ }
inline bool NativeInstruction::is_safepoint_poll() {
#ifdef AMD64
  if ( ubyte_at(0) == NativeTstRegMem::instruction_code_memXregl &&
       ubyte_at(1) == 0x05 ) { // 00 rax 101
     address fault = addr_at(6) + int_at(2);
     return os::is_poll_address(fault);
  } else {
    return false;
  }
#else
  return ( ubyte_at(0) == NativeMovRegMem::instruction_code_mem2reg ||
           ubyte_at(0) == NativeTstRegMem::instruction_code_memXregl ) &&
           (ubyte_at(1)&0xC7) == 0x05 && /* Mod R/M == disp32 */
           (os::is_poll_address((address)int_at(2)));
#endif // AMD64
}

inline bool NativeInstruction::is_mov_literal64() {
#ifdef AMD64
  return ((ubyte_at(0) == Assembler::REX_W || ubyte_at(0) == Assembler::REX_WB) &&
          (ubyte_at(1) & (0xff ^ NativeMovConstReg::register_mask)) == 0xB8);
#else
  return false;
#endif // AMD64
}

/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_NATIVEINST_SPARC_HPP
#define CPU_SPARC_VM_NATIVEINST_SPARC_HPP

#include "asm/macroAssembler.hpp"
#include "memory/allocation.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"

// We have interface for the following instructions:
// - NativeInstruction
// - - NativeCall
// - - NativeFarCall
// - - NativeMovConstReg
// - - NativeMovConstRegPatching
// - - NativeMovRegMem
// - - NativeJump
// - - NativeGeneralJump
// - - NativeIllegalInstruction
// The base class for different kinds of native instruction abstractions.
// Provides the primitive operations to manipulate code relative to this.
class NativeInstruction VALUE_OBJ_CLASS_SPEC {
  friend class Relocation;

 public:
  enum Sparc_specific_constants {
    nop_instruction_size        =    4
  };

  bool is_nop()                        { return long_at(0) == nop_instruction(); }
  bool is_call()                       { return is_op(long_at(0), Assembler::call_op); }
  bool is_call_reg()                   { return is_op(long_at(0), Assembler::arith_op); }
  bool is_sethi()                      { return (is_op2(long_at(0), Assembler::sethi_op2)
                                          && inv_rd(long_at(0)) != G0); }

  bool sets_cc() {
    // conservative (returns true for some instructions that do not set the
    // the condition code, such as, "save".
    // Does not return true for the deprecated tagged instructions, such as, TADDcc
    int x = long_at(0);
    return (is_op(x, Assembler::arith_op) &&
            (inv_op3(x) & Assembler::cc_bit_op3) == Assembler::cc_bit_op3);
  }
  bool is_illegal();
  bool is_zombie() {
    int x = long_at(0);
    return is_op3(x,
                  Assembler::ldsw_op3,
                  Assembler::ldst_op)
        && Assembler::inv_rs1(x) == G0
        && Assembler::inv_rd(x) == O7;
  }
  bool is_ic_miss_trap();       // Inline-cache uses a trap to detect a miss
  bool is_return() {
    // is it the output of MacroAssembler::ret or MacroAssembler::retl?
    int x = long_at(0);
    const int pc_return_offset = 8; // see frame_sparc.hpp
    return is_op3(x, Assembler::jmpl_op3, Assembler::arith_op)
        && (inv_rs1(x) == I7 || inv_rs1(x) == O7)
        && inv_immed(x) && inv_simm(x, 13) == pc_return_offset
        && inv_rd(x) == G0;
  }
  bool is_int_jump() {
    // is it the output of MacroAssembler::b?
    int x = long_at(0);
    return is_op2(x, Assembler::bp_op2) || is_op2(x, Assembler::br_op2);
  }
  bool is_float_jump() {
    // is it the output of MacroAssembler::fb?
    int x = long_at(0);
    return is_op2(x, Assembler::fbp_op2) || is_op2(x, Assembler::fb_op2);
  }
  bool is_jump() {
    return is_int_jump() || is_float_jump();
  }
  bool is_cond_jump() {
    int x = long_at(0);
    return (is_int_jump() && Assembler::inv_cond(x) != Assembler::always) ||
           (is_float_jump() && Assembler::inv_cond(x) != Assembler::f_always);
  }

  bool is_stack_bang() {
    int x = long_at(0);
    return is_op3(x, Assembler::stw_op3, Assembler::ldst_op) &&
      (inv_rd(x) == G0) && (inv_rs1(x) == SP) && (inv_rs2(x) == G3_scratch);
  }

  bool is_prefetch() {
    int x = long_at(0);
    return is_op3(x, Assembler::prefetch_op3, Assembler::ldst_op);
  }

  bool is_membar() {
    int x = long_at(0);
    return is_op3(x, Assembler::membar_op3, Assembler::arith_op) &&
      (inv_rd(x) == G0) && (inv_rs1(x) == O7);
  }

  bool is_safepoint_poll() {
    int x = long_at(0);
#ifdef _LP64
    return is_op3(x, Assembler::ldx_op3,  Assembler::ldst_op) &&
#else
    return is_op3(x, Assembler::lduw_op3, Assembler::ldst_op) &&
#endif
      (inv_rd(x) == G0) && (inv_immed(x) ? Assembler::inv_simm13(x) == 0 : inv_rs2(x) == G0);
  }

  bool is_zero_test(Register &reg);
  bool is_load_store_with_small_offset(Register reg);

 public:
#ifdef ASSERT
  static int rdpc_instruction()        { return Assembler::op(Assembler::arith_op ) | Assembler::op3(Assembler::rdreg_op3) | Assembler::u_field(5, 18, 14) | Assembler::rd(O7); }
#else
  // Temporary fix: in optimized mode, u_field is a macro for efficiency reasons (see Assembler::u_field) - needs to be fixed
  static int rdpc_instruction()        { return Assembler::op(Assembler::arith_op ) | Assembler::op3(Assembler::rdreg_op3) |            u_field(5, 18, 14) | Assembler::rd(O7); }
#endif
  static int nop_instruction()         { return Assembler::op(Assembler::branch_op) | Assembler::op2(Assembler::sethi_op2); }
  static int illegal_instruction();    // the output of __ breakpoint_trap()
  static int call_instruction(address destination, address pc) { return Assembler::op(Assembler::call_op) | Assembler::wdisp((intptr_t)destination, (intptr_t)pc, 30); }

  static int branch_instruction(Assembler::op2s op2val, Assembler::Condition c, bool a) {
    return Assembler::op(Assembler::branch_op) | Assembler::op2(op2val) | Assembler::annul(a) | Assembler::cond(c);
  }

  static int op3_instruction(Assembler::ops opval, Register rd, Assembler::op3s op3val, Register rs1, int simm13a) {
    return Assembler::op(opval) | Assembler::rd(rd) | Assembler::op3(op3val) | Assembler::rs1(rs1) | Assembler::immed(true) | Assembler::simm(simm13a, 13);
  }

  static int sethi_instruction(Register rd, int imm22a) {
    return Assembler::op(Assembler::branch_op) | Assembler::rd(rd) | Assembler::op2(Assembler::sethi_op2) | Assembler::hi22(imm22a);
  }

 protected:
  address  addr_at(int offset) const    { return address(this) + offset; }
  int      long_at(int offset) const    { return *(int*)addr_at(offset); }
  void set_long_at(int offset, int i);      /* deals with I-cache */
  void set_jlong_at(int offset, jlong i);   /* deals with I-cache */
  void set_addr_at(int offset, address x);  /* deals with I-cache */

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(BytesPerInstWord); }

  static bool is_op( int x, Assembler::ops opval)  {
    return Assembler::inv_op(x) == opval;
  }
  static bool is_op2(int x, Assembler::op2s op2val) {
    return Assembler::inv_op(x) == Assembler::branch_op && Assembler::inv_op2(x) == op2val;
  }
  static bool is_op3(int x, Assembler::op3s op3val, Assembler::ops opval) {
    return Assembler::inv_op(x) == opval && Assembler::inv_op3(x) == op3val;
  }

  // utilities to help subclasses decode:
  static Register inv_rd(  int x ) { return Assembler::inv_rd( x); }
  static Register inv_rs1( int x ) { return Assembler::inv_rs1(x); }
  static Register inv_rs2( int x ) { return Assembler::inv_rs2(x); }

  static bool inv_immed( int x ) { return Assembler::inv_immed(x); }
  static bool inv_annul( int x ) { return (Assembler::annul(true) & x) != 0; }
  static int  inv_cond(  int x ) { return Assembler::inv_cond(x); }

  static int inv_op(  int x ) { return Assembler::inv_op( x); }
  static int inv_op2( int x ) { return Assembler::inv_op2(x); }
  static int inv_op3( int x ) { return Assembler::inv_op3(x); }

  static int inv_simm(    int x, int nbits ) { return Assembler::inv_simm(x, nbits); }
  static intptr_t inv_wdisp(   int x, int nbits ) { return Assembler::inv_wdisp(  x, 0, nbits); }
  static intptr_t inv_wdisp16( int x )            { return Assembler::inv_wdisp16(x, 0); }
  static int branch_destination_offset(int x) { return MacroAssembler::branch_destination(x, 0); }
  static int patch_branch_destination_offset(int dest_offset, int x) {
    return MacroAssembler::patched_branch(dest_offset, x, 0);
  }

  // utility for checking if x is either of 2 small constants
  static bool is_either(int x, int k1, int k2) {
    // return x == k1 || x == k2;
    return (1 << x) & (1 << k1 | 1 << k2);
  }

  // utility for checking overflow of signed instruction fields
  static bool fits_in_simm(int x, int nbits) {
    // cf. Assembler::assert_signed_range()
    // return -(1 << nbits-1) <= x  &&  x < ( 1 << nbits-1),
    return (unsigned)(x + (1 << nbits-1)) < (unsigned)(1 << nbits);
  }

  // set a signed immediate field
  static int set_simm(int insn, int imm, int nbits) {
    return (insn &~ Assembler::simm(-1, nbits)) | Assembler::simm(imm, nbits);
  }

  // set a wdisp field (disp should be the difference of two addresses)
  static int set_wdisp(int insn, intptr_t disp, int nbits) {
    return (insn &~ Assembler::wdisp((intptr_t)-4, (intptr_t)0, nbits)) | Assembler::wdisp(disp, 0, nbits);
  }

  static int set_wdisp16(int insn, intptr_t disp) {
    return (insn &~ Assembler::wdisp16((intptr_t)-4, 0)) | Assembler::wdisp16(disp, 0);
  }

  // get a simm13 field from an arithmetic or memory instruction
  static int get_simm13(int insn) {
    assert(is_either(Assembler::inv_op(insn),
                     Assembler::arith_op, Assembler::ldst_op) &&
            (insn & Assembler::immed(true)), "must have a simm13 field");
    return Assembler::inv_simm(insn, 13);
  }

  // set the simm13 field of an arithmetic or memory instruction
  static bool set_simm13(int insn, int imm) {
    get_simm13(insn);           // tickle the assertion check
    return set_simm(insn, imm, 13);
  }

  // combine the fields of a sethi stream (7 instructions ) and an add, jmp or ld/st
  static intptr_t data64( address pc, int arith_insn ) {
    assert(is_op2(*(unsigned int *)pc, Assembler::sethi_op2), "must be sethi");
    intptr_t hi = (intptr_t)gethi( (unsigned int *)pc );
    intptr_t lo = (intptr_t)get_simm13(arith_insn);
    assert((unsigned)lo < (1 << 10), "offset field of set_metadata must be 10 bits");
    return hi | lo;
  }

  // Regenerate the instruction sequence that performs the 64 bit
  // sethi.  This only does the sethi.  The disp field (bottom 10 bits)
  // must be handled separately.
  static void set_data64_sethi(address instaddr, intptr_t x);
  static void verify_data64_sethi(address instaddr, intptr_t x);

  // combine the fields of a sethi/simm13 pair (simm13 = or, add, jmpl, ld/st)
  static int data32(int sethi_insn, int arith_insn) {
    assert(is_op2(sethi_insn, Assembler::sethi_op2), "must be sethi");
    int hi = Assembler::inv_hi22(sethi_insn);
    int lo = get_simm13(arith_insn);
    assert((unsigned)lo < (1 << 10), "offset field of set_metadata must be 10 bits");
    return hi | lo;
  }

  static int set_data32_sethi(int sethi_insn, int imm) {
    // note that Assembler::hi22 clips the low 10 bits for us
    assert(is_op2(sethi_insn, Assembler::sethi_op2), "must be sethi");
    return (sethi_insn &~ Assembler::hi22(-1)) | Assembler::hi22(imm);
  }

  static int set_data32_simm13(int arith_insn, int imm) {
    get_simm13(arith_insn);             // tickle the assertion check
    int imm10 = Assembler::low10(imm);
    return (arith_insn &~ Assembler::simm(-1, 13)) | Assembler::simm(imm10, 13);
  }

  static int low10(int imm) {
    return Assembler::low10(imm);
  }

  // Perform the inverse of the LP64 Macroassembler::sethi
  // routine.  Extracts the 54 bits of address from the instruction
  // stream. This routine must agree with the sethi routine in
  // assembler_inline_sparc.hpp
  static address gethi( unsigned int *pc ) {
    int i = 0;
    uintptr_t adr;
    // We first start out with the real sethi instruction
    assert(is_op2(*pc, Assembler::sethi_op2), "in gethi - must be sethi");
    adr = (unsigned int)Assembler::inv_hi22( *(pc++) );
    i++;
    while ( i < 7 ) {
       // We're done if we hit a nop
       if ( (int)*pc == nop_instruction() ) break;
       assert ( Assembler::inv_op(*pc) == Assembler::arith_op, "in gethi - must be arith_op" );
       switch  ( Assembler::inv_op3(*pc) ) {
         case Assembler::xor_op3:
           adr ^= (intptr_t)get_simm13( *pc );
           return ( (address)adr );
           break;
         case Assembler::sll_op3:
           adr <<= ( *pc & 0x3f );
           break;
         case Assembler::or_op3:
           adr |= (intptr_t)get_simm13( *pc );
           break;
         default:
           assert ( 0, "in gethi - Should not reach here" );
           break;
       }
       pc++;
       i++;
    }
    return ( (address)adr );
  }

 public:
  void  verify();
  void  print();

  // unit test stuff
  static void test() {}                 // override for testing

  inline friend NativeInstruction* nativeInstruction_at(address address);
};

inline NativeInstruction* nativeInstruction_at(address address) {
    NativeInstruction* inst = (NativeInstruction*)address;
#ifdef ASSERT
      inst->verify();
#endif
    return inst;
}



//-----------------------------------------------------------------------------

// The NativeCall is an abstraction for accessing/manipulating native call imm32 instructions.
// (used to manipulate inline caches, primitive & dll calls, etc.)
inline NativeCall* nativeCall_at(address instr);
inline NativeCall* nativeCall_overwriting_at(address instr,
                                             address destination);
inline NativeCall* nativeCall_before(address return_address);
class NativeCall: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    instruction_size                   = 8,
    return_address_offset              = 8,
    call_displacement_width            = 30,
    displacement_offset                = 0,
    instruction_offset                 = 0
  };
  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(instruction_size); }
  address return_address() const            { return addr_at(return_address_offset); }

  address destination() const               { return inv_wdisp(long_at(0), call_displacement_width) + instruction_address(); }
  address displacement_address() const      { return addr_at(displacement_offset); }
  void  set_destination(address dest)       { set_long_at(0, set_wdisp(long_at(0), dest - instruction_address(), call_displacement_width)); }
  void  set_destination_mt_safe(address dest);

  void  verify_alignment() {} // do nothing on sparc
  void  verify();
  void  print();

  // unit test stuff
  static void  test();

  // Creation
  friend inline NativeCall* nativeCall_at(address instr);
  friend NativeCall* nativeCall_overwriting_at(address instr, address destination = NULL) {
    // insert a "blank" call:
    NativeCall* call = (NativeCall*)instr;
    call->set_long_at(0 * BytesPerInstWord, call_instruction(destination, instr));
    call->set_long_at(1 * BytesPerInstWord, nop_instruction());
    assert(call->addr_at(2 * BytesPerInstWord) - instr == instruction_size, "instruction size");
    // check its structure now:
    assert(nativeCall_at(instr)->destination() == destination, "correct call destination");
    return call;
  }

  friend inline NativeCall* nativeCall_before(address return_address) {
    NativeCall* call = (NativeCall*)(return_address - return_address_offset);
    #ifdef ASSERT
      call->verify();
    #endif
    return call;
  }

  static bool is_call_at(address instr) {
    return nativeInstruction_at(instr)->is_call();
  }

  static bool is_call_before(address instr) {
    return nativeInstruction_at(instr - return_address_offset)->is_call();
  }

  static bool is_call_to(address instr, address target) {
    return nativeInstruction_at(instr)->is_call() &&
      nativeCall_at(instr)->destination() == target;
  }

  // MT-safe patching of a call instruction.
  static void insert(address code_pos, address entry) {
    (void)nativeCall_overwriting_at(code_pos, entry);
  }

  static void replace_mt_safe(address instr_addr, address code_buffer);
};
inline NativeCall* nativeCall_at(address instr) {
  NativeCall* call = (NativeCall*)instr;
#ifdef ASSERT
  call->verify();
#endif
  return call;
}

class NativeCallReg: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    instruction_size      = 8,
    return_address_offset = 8,
    instruction_offset    = 0
  };

  address next_instruction_address() const {
    return addr_at(instruction_size);
  }
};

// The NativeFarCall is an abstraction for accessing/manipulating native call-anywhere
// instructions in the sparcv9 vm.  Used to call native methods which may be loaded
// anywhere in the address space, possibly out of reach of a call instruction.

#ifndef _LP64

// On 32-bit systems, a far call is the same as a near one.
class NativeFarCall;
inline NativeFarCall* nativeFarCall_at(address instr);
class NativeFarCall : public NativeCall {
public:
  friend inline NativeFarCall* nativeFarCall_at(address instr) { return (NativeFarCall*)nativeCall_at(instr); }
  friend NativeFarCall* nativeFarCall_overwriting_at(address instr, address destination = NULL)
                                                        { return (NativeFarCall*)nativeCall_overwriting_at(instr, destination); }
  friend NativeFarCall* nativeFarCall_before(address return_address)
                                                        { return (NativeFarCall*)nativeCall_before(return_address); }
};

#else

// The format of this extended-range call is:
//      jumpl_to addr, lreg
//      == sethi %hi54(addr), O7 ;  jumpl O7, %lo10(addr), O7 ;  <delay>
// That is, it is essentially the same as a NativeJump.
class NativeFarCall;
inline NativeFarCall* nativeFarCall_overwriting_at(address instr, address destination);
inline NativeFarCall* nativeFarCall_at(address instr);
class NativeFarCall: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    // instruction_size includes the delay slot instruction.
    instruction_size                   = 9 * BytesPerInstWord,
    return_address_offset              = 9 * BytesPerInstWord,
    jmpl_offset                        = 7 * BytesPerInstWord,
    displacement_offset                = 0,
    instruction_offset                 = 0
  };
  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(instruction_size); }
  address return_address() const            { return addr_at(return_address_offset); }

  address destination() const {
    return (address) data64(addr_at(0), long_at(jmpl_offset));
  }
  address displacement_address() const      { return addr_at(displacement_offset); }
  void set_destination(address dest);

  bool destination_is_compiled_verified_entry_point();

  void  verify();
  void  print();

  // unit test stuff
  static void  test();

  // Creation
  friend inline NativeFarCall* nativeFarCall_at(address instr) {
    NativeFarCall* call = (NativeFarCall*)instr;
    #ifdef ASSERT
      call->verify();
    #endif
    return call;
  }

  friend inline NativeFarCall* nativeFarCall_overwriting_at(address instr, address destination = NULL) {
    Unimplemented();
    NativeFarCall* call = (NativeFarCall*)instr;
    return call;
  }

  friend NativeFarCall* nativeFarCall_before(address return_address) {
    NativeFarCall* call = (NativeFarCall*)(return_address - return_address_offset);
    #ifdef ASSERT
      call->verify();
    #endif
    return call;
  }

  static bool is_call_at(address instr);

  // MT-safe patching of a call instruction.
  static void insert(address code_pos, address entry) {
    (void)nativeFarCall_overwriting_at(code_pos, entry);
  }
  static void replace_mt_safe(address instr_addr, address code_buffer);
};

#endif // _LP64

// An interface for accessing/manipulating 32 bit native set_metadata imm, reg instructions
// (used to manipulate inlined data references, etc.)
//      set_metadata imm, reg
//      == sethi %hi22(imm), reg ;  add reg, %lo10(imm), reg
class NativeMovConstReg32;
inline NativeMovConstReg32* nativeMovConstReg32_at(address address);
class NativeMovConstReg32: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    sethi_offset           = 0,
    add_offset             = 4,
    instruction_size       = 8
  };

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(instruction_size); }

  // (The [set_]data accessor respects oop_type relocs also.)
  intptr_t data() const;
  void set_data(intptr_t x);

  // report the destination register
  Register destination() { return inv_rd(long_at(sethi_offset)); }

  void  verify();
  void  print();

  // unit test stuff
  static void test();

  // Creation
  friend inline NativeMovConstReg32* nativeMovConstReg32_at(address address) {
    NativeMovConstReg32* test = (NativeMovConstReg32*)address;
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }
};

// An interface for accessing/manipulating native set_metadata imm, reg instructions.
// (used to manipulate inlined data references, etc.)
//      set_metadata imm, reg
//      == sethi %hi22(imm), reg ;  add reg, %lo10(imm), reg
class NativeMovConstReg;
inline NativeMovConstReg* nativeMovConstReg_at(address address);
class NativeMovConstReg: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    sethi_offset           = 0,
#ifdef _LP64
    add_offset             = 7 * BytesPerInstWord,
    instruction_size       = 8 * BytesPerInstWord
#else
    add_offset             = 4,
    instruction_size       = 8
#endif
  };

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(instruction_size); }

  // (The [set_]data accessor respects oop_type relocs also.)
  intptr_t data() const;
  void set_data(intptr_t x);

  // report the destination register
  Register destination() { return inv_rd(long_at(sethi_offset)); }

  void  verify();
  void  print();

  // unit test stuff
  static void test();

  // Creation
  friend inline NativeMovConstReg* nativeMovConstReg_at(address address) {
    NativeMovConstReg* test = (NativeMovConstReg*)address;
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }


  friend NativeMovConstReg* nativeMovConstReg_before(address address) {
    NativeMovConstReg* test = (NativeMovConstReg*)(address - instruction_size);
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }

};


// An interface for accessing/manipulating native set_metadata imm, reg instructions.
// (used to manipulate inlined data references, etc.)
//      set_metadata imm, reg
//      == sethi %hi22(imm), reg; nop; add reg, %lo10(imm), reg
//
// Note that it is identical to NativeMovConstReg with the exception of a nop between the
// sethi and the add.  The nop is required to be in the delay slot of the call instruction
// which overwrites the sethi during patching.
class NativeMovConstRegPatching;
inline NativeMovConstRegPatching* nativeMovConstRegPatching_at(address address);class NativeMovConstRegPatching: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    sethi_offset           = 0,
#ifdef _LP64
    nop_offset             = 7 * BytesPerInstWord,
#else
    nop_offset             = sethi_offset + BytesPerInstWord,
#endif
    add_offset             = nop_offset   + BytesPerInstWord,
    instruction_size       = add_offset   + BytesPerInstWord
  };

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(instruction_size); }

  // (The [set_]data accessor respects oop_type relocs also.)
  int data() const;
  void  set_data(int x);

  // report the destination register
  Register destination() { return inv_rd(long_at(sethi_offset)); }

  void  verify();
  void  print();

  // unit test stuff
  static void test();

  // Creation
  friend inline NativeMovConstRegPatching* nativeMovConstRegPatching_at(address address) {
    NativeMovConstRegPatching* test = (NativeMovConstRegPatching*)address;
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }


  friend NativeMovConstRegPatching* nativeMovConstRegPatching_before(address address) {
    NativeMovConstRegPatching* test = (NativeMovConstRegPatching*)(address - instruction_size);
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }

};


// An interface for accessing/manipulating native memory ops
//      ld* [reg + offset], reg
//      st* reg, [reg + offset]
//      sethi %hi(imm), reg; add reg, %lo(imm), reg; ld* [reg1 + reg], reg2
//      sethi %hi(imm), reg; add reg, %lo(imm), reg; st* reg2, [reg1 + reg]
// Ops covered: {lds,ldu,st}{w,b,h}, {ld,st}{d,x}
//
class NativeMovRegMem;
inline NativeMovRegMem* nativeMovRegMem_at (address address);
class NativeMovRegMem: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    op3_mask_ld = 1 << Assembler::lduw_op3 |
                  1 << Assembler::ldub_op3 |
                  1 << Assembler::lduh_op3 |
                  1 << Assembler::ldd_op3 |
                  1 << Assembler::ldsw_op3 |
                  1 << Assembler::ldsb_op3 |
                  1 << Assembler::ldsh_op3 |
                  1 << Assembler::ldx_op3,
    op3_mask_st = 1 << Assembler::stw_op3 |
                  1 << Assembler::stb_op3 |
                  1 << Assembler::sth_op3 |
                  1 << Assembler::std_op3 |
                  1 << Assembler::stx_op3,
    op3_ldst_int_limit = Assembler::ldf_op3,
    op3_mask_ldf = 1 << (Assembler::ldf_op3  - op3_ldst_int_limit) |
                   1 << (Assembler::lddf_op3 - op3_ldst_int_limit),
    op3_mask_stf = 1 << (Assembler::stf_op3  - op3_ldst_int_limit) |
                   1 << (Assembler::stdf_op3 - op3_ldst_int_limit),

    offset_width    = 13,
    sethi_offset    = 0,
#ifdef _LP64
    add_offset      = 7 * BytesPerInstWord,
#else
    add_offset      = 4,
#endif
    ldst_offset     = add_offset + BytesPerInstWord
  };
  bool is_immediate() const {
    // check if instruction is ld* [reg + offset], reg or st* reg, [reg + offset]
    int i0 = long_at(0);
    return (is_op(i0, Assembler::ldst_op));
  }

  address instruction_address() const           { return addr_at(0); }
  address next_instruction_address() const      {
#ifdef _LP64
    return addr_at(is_immediate() ? 4 : (7 * BytesPerInstWord));
#else
    return addr_at(is_immediate() ? 4 : 12);
#endif
  }
  intptr_t   offset() const                             {
     return is_immediate()? inv_simm(long_at(0), offset_width) :
                            nativeMovConstReg_at(addr_at(0))->data();
  }
  void  set_offset(intptr_t x) {
    if (is_immediate()) {
      guarantee(fits_in_simm(x, offset_width), "data block offset overflow");
      set_long_at(0, set_simm(long_at(0), x, offset_width));
    } else
      nativeMovConstReg_at(addr_at(0))->set_data(x);
  }

  void  add_offset_in_bytes(intptr_t radd_offset)     {
      set_offset (offset() + radd_offset);
  }

  void  copy_instruction_to(address new_instruction_address);

  void verify();
  void print ();

  // unit test stuff
  static void test();

 private:
  friend inline NativeMovRegMem* nativeMovRegMem_at (address address) {
    NativeMovRegMem* test = (NativeMovRegMem*)address;
    #ifdef ASSERT
      test->verify();
    #endif
    return test;
  }
};


// An interface for accessing/manipulating native jumps
//      jump_to addr
//      == sethi %hi22(addr), temp ;  jumpl reg, %lo10(addr), G0 ;  <delay>
//      jumpl_to addr, lreg
//      == sethi %hi22(addr), temp ;  jumpl reg, %lo10(addr), lreg ;  <delay>
class NativeJump;
inline NativeJump* nativeJump_at(address address);
class NativeJump: public NativeInstruction {
 private:
  void guarantee_displacement(int disp, int width) {
    guarantee(fits_in_simm(disp, width + 2), "branch displacement overflow");
  }

 public:
  enum Sparc_specific_constants {
    sethi_offset           = 0,
#ifdef _LP64
    jmpl_offset            = 7 * BytesPerInstWord,
    instruction_size       = 9 * BytesPerInstWord  // includes delay slot
#else
    jmpl_offset            = 1 * BytesPerInstWord,
    instruction_size       = 3 * BytesPerInstWord  // includes delay slot
#endif
  };

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(instruction_size); }

#ifdef _LP64
  address jump_destination() const {
    return (address) data64(instruction_address(), long_at(jmpl_offset));
  }
  void set_jump_destination(address dest) {
    set_data64_sethi( instruction_address(), (intptr_t)dest);
    set_long_at(jmpl_offset,  set_data32_simm13( long_at(jmpl_offset),  (intptr_t)dest));
  }
#else
  address jump_destination() const {
    return (address) data32(long_at(sethi_offset), long_at(jmpl_offset));
  }
  void set_jump_destination(address dest) {
    set_long_at(sethi_offset, set_data32_sethi(  long_at(sethi_offset), (intptr_t)dest));
    set_long_at(jmpl_offset,  set_data32_simm13( long_at(jmpl_offset),  (intptr_t)dest));
  }
#endif

  // Creation
  friend inline NativeJump* nativeJump_at(address address) {
    NativeJump* jump = (NativeJump*)address;
    #ifdef ASSERT
      jump->verify();
    #endif
    return jump;
  }

  void verify();
  void print();

  // Unit testing stuff
  static void test();

  // Insertion of native jump instruction
  static void insert(address code_pos, address entry);
  // MT-safe insertion of native jump at verified method entry
  static void check_verified_entry_alignment(address entry, address verified_entry) {
    // nothing to do for sparc.
  }
  static void patch_verified_entry(address entry, address verified_entry, address dest);
};



// Despite the name, handles only simple branches.
class NativeGeneralJump;
inline NativeGeneralJump* nativeGeneralJump_at(address address);
class NativeGeneralJump: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    instruction_size                   = 8
  };

  address instruction_address() const       { return addr_at(0); }
  address jump_destination()    const       { return addr_at(0) + branch_destination_offset(long_at(0)); }
  void set_jump_destination(address dest) {
    int patched_instr = patch_branch_destination_offset(dest - addr_at(0), long_at(0));
    set_long_at(0, patched_instr);
  }
  NativeInstruction *delay_slot_instr() { return nativeInstruction_at(addr_at(4));}
  void fill_delay_slot(int instr) { set_long_at(4, instr);}
  Assembler::Condition condition() {
    int x = long_at(0);
    return (Assembler::Condition) Assembler::inv_cond(x);
  }

  // Creation
  friend inline NativeGeneralJump* nativeGeneralJump_at(address address) {
    NativeGeneralJump* jump = (NativeGeneralJump*)(address);
#ifdef ASSERT
      jump->verify();
#endif
    return jump;
  }

  // Insertion of native general jump instruction
  static void insert_unconditional(address code_pos, address entry);
  static void replace_mt_safe(address instr_addr, address code_buffer);

  void verify();
};


class NativeIllegalInstruction: public NativeInstruction {
 public:
  enum Sparc_specific_constants {
    instruction_size            =    4
  };

  // Insert illegal opcode as specific address
  static void insert(address code_pos);
};

#endif // CPU_SPARC_VM_NATIVEINST_SPARC_HPP

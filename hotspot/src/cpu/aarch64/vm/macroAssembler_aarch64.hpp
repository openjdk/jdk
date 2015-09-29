/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2015, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_VM_MACROASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_VM_MACROASSEMBLER_AARCH64_HPP

#include "asm/assembler.hpp"

// MacroAssembler extends Assembler by frequently used macros.
//
// Instructions for which a 'better' code sequence exists depending
// on arguments should also go in here.

class MacroAssembler: public Assembler {
  friend class LIR_Assembler;

 public:
  using Assembler::mov;
  using Assembler::movi;

 protected:

  // Support for VM calls
  //
  // This is the base routine called by the different versions of call_VM_leaf. The interpreter
  // may customize this version by overriding it for its purposes (e.g., to save/restore
  // additional registers when doing a VM call).
#ifdef CC_INTERP
  // c++ interpreter never wants to use interp_masm version of call_VM
  #define VIRTUAL
#else
  #define VIRTUAL virtual
#endif

  VIRTUAL void call_VM_leaf_base(
    address entry_point,               // the entry point
    int     number_of_arguments,        // the number of arguments to pop after the call
    Label *retaddr = NULL
  );

  VIRTUAL void call_VM_leaf_base(
    address entry_point,               // the entry point
    int     number_of_arguments,        // the number of arguments to pop after the call
    Label &retaddr) {
    call_VM_leaf_base(entry_point, number_of_arguments, &retaddr);
  }

  // This is the base routine called by the different versions of call_VM. The interpreter
  // may customize this version by overriding it for its purposes (e.g., to save/restore
  // additional registers when doing a VM call).
  //
  // If no java_thread register is specified (noreg) than rthread will be used instead. call_VM_base
  // returns the register which contains the thread upon return. If a thread register has been
  // specified, the return value will correspond to that register. If no last_java_sp is specified
  // (noreg) than rsp will be used instead.
  VIRTUAL void call_VM_base(           // returns the register containing the thread upon return
    Register oop_result,               // where an oop-result ends up if any; use noreg otherwise
    Register java_thread,              // the thread if computed before     ; use noreg otherwise
    Register last_java_sp,             // to set up last_Java_frame in stubs; use noreg otherwise
    address  entry_point,              // the entry point
    int      number_of_arguments,      // the number of arguments (w/o thread) to pop after the call
    bool     check_exceptions          // whether to check for pending exceptions after return
  );

  // These routines should emit JVMTI PopFrame and ForceEarlyReturn handling code.
  // The implementation is only non-empty for the InterpreterMacroAssembler,
  // as only the interpreter handles PopFrame and ForceEarlyReturn requests.
  virtual void check_and_handle_popframe(Register java_thread);
  virtual void check_and_handle_earlyret(Register java_thread);

  void call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions = true);

  // Maximum size of class area in Metaspace when compressed
  uint64_t use_XOR_for_compressed_class_base;

 public:
  MacroAssembler(CodeBuffer* code) : Assembler(code) {
    use_XOR_for_compressed_class_base
      = (operand_valid_for_logical_immediate(false /*is32*/,
                                             (uint64_t)Universe::narrow_klass_base())
         && ((uint64_t)Universe::narrow_klass_base()
             > (1u << log2_intptr(CompressedClassSpaceSize))));
  }

  // Biased locking support
  // lock_reg and obj_reg must be loaded up with the appropriate values.
  // swap_reg is killed.
  // tmp_reg must be supplied and must not be rscratch1 or rscratch2
  // Optional slow case is for implementations (interpreter and C1) which branch to
  // slow case directly. Leaves condition codes set for C2's Fast_Lock node.
  // Returns offset of first potentially-faulting instruction for null
  // check info (currently consumed only by C1). If
  // swap_reg_contains_mark is true then returns -1 as it is assumed
  // the calling code has already passed any potential faults.
  int biased_locking_enter(Register lock_reg, Register obj_reg,
                           Register swap_reg, Register tmp_reg,
                           bool swap_reg_contains_mark,
                           Label& done, Label* slow_case = NULL,
                           BiasedLockingCounters* counters = NULL);
  void biased_locking_exit (Register obj_reg, Register temp_reg, Label& done);


  // Helper functions for statistics gathering.
  // Unconditional atomic increment.
  void atomic_incw(Register counter_addr, Register tmp, Register tmp2);
  void atomic_incw(Address counter_addr, Register tmp1, Register tmp2, Register tmp3) {
    lea(tmp1, counter_addr);
    atomic_incw(tmp1, tmp2, tmp3);
  }
  // Load Effective Address
  void lea(Register r, const Address &a) {
    InstructionMark im(this);
    code_section()->relocate(inst_mark(), a.rspec());
    a.lea(this, r);
  }

  void addmw(Address a, Register incr, Register scratch) {
    ldrw(scratch, a);
    addw(scratch, scratch, incr);
    strw(scratch, a);
  }

  // Add constant to memory word
  void addmw(Address a, int imm, Register scratch) {
    ldrw(scratch, a);
    if (imm > 0)
      addw(scratch, scratch, (unsigned)imm);
    else
      subw(scratch, scratch, (unsigned)-imm);
    strw(scratch, a);
  }

  // Frame creation and destruction shared between JITs.
  void build_frame(int framesize);
  void remove_frame(int framesize);

  virtual void _call_Unimplemented(address call_site) {
    mov(rscratch2, call_site);
    haltsim();
  }

#define call_Unimplemented() _call_Unimplemented((address)__PRETTY_FUNCTION__)

  virtual void notify(int type);

  // aliases defined in AARCH64 spec

  template<class T>
  inline void cmpw(Register Rd, T imm)  { subsw(zr, Rd, imm); }
  inline void cmp(Register Rd, unsigned imm)  { subs(zr, Rd, imm); }

  inline void cmnw(Register Rd, unsigned imm) { addsw(zr, Rd, imm); }
  inline void cmn(Register Rd, unsigned imm) { adds(zr, Rd, imm); }

  void cset(Register Rd, Assembler::Condition cond) {
    csinc(Rd, zr, zr, ~cond);
  }
  void csetw(Register Rd, Assembler::Condition cond) {
    csincw(Rd, zr, zr, ~cond);
  }

  void cneg(Register Rd, Register Rn, Assembler::Condition cond) {
    csneg(Rd, Rn, Rn, ~cond);
  }
  void cnegw(Register Rd, Register Rn, Assembler::Condition cond) {
    csnegw(Rd, Rn, Rn, ~cond);
  }

  inline void movw(Register Rd, Register Rn) {
    if (Rd == sp || Rn == sp) {
      addw(Rd, Rn, 0U);
    } else {
      orrw(Rd, zr, Rn);
    }
  }
  inline void mov(Register Rd, Register Rn) {
    assert(Rd != r31_sp && Rn != r31_sp, "should be");
    if (Rd == Rn) {
    } else if (Rd == sp || Rn == sp) {
      add(Rd, Rn, 0U);
    } else {
      orr(Rd, zr, Rn);
    }
  }

  inline void moviw(Register Rd, unsigned imm) { orrw(Rd, zr, imm); }
  inline void movi(Register Rd, unsigned imm) { orr(Rd, zr, imm); }

  inline void tstw(Register Rd, unsigned imm) { andsw(zr, Rd, imm); }
  inline void tst(Register Rd, unsigned imm) { ands(zr, Rd, imm); }

  inline void bfiw(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    bfmw(Rd, Rn, ((32 - lsb) & 31), (width - 1));
  }
  inline void bfi(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    bfm(Rd, Rn, ((64 - lsb) & 63), (width - 1));
  }

  inline void bfxilw(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    bfmw(Rd, Rn, lsb, (lsb + width - 1));
  }
  inline void bfxil(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    bfm(Rd, Rn, lsb , (lsb + width - 1));
  }

  inline void sbfizw(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    sbfmw(Rd, Rn, ((32 - lsb) & 31), (width - 1));
  }
  inline void sbfiz(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    sbfm(Rd, Rn, ((64 - lsb) & 63), (width - 1));
  }

  inline void sbfxw(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    sbfmw(Rd, Rn, lsb, (lsb + width - 1));
  }
  inline void sbfx(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    sbfm(Rd, Rn, lsb , (lsb + width - 1));
  }

  inline void ubfizw(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    ubfmw(Rd, Rn, ((32 - lsb) & 31), (width - 1));
  }
  inline void ubfiz(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    ubfm(Rd, Rn, ((64 - lsb) & 63), (width - 1));
  }

  inline void ubfxw(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    ubfmw(Rd, Rn, lsb, (lsb + width - 1));
  }
  inline void ubfx(Register Rd, Register Rn, unsigned lsb, unsigned width) {
    ubfm(Rd, Rn, lsb , (lsb + width - 1));
  }

  inline void asrw(Register Rd, Register Rn, unsigned imm) {
    sbfmw(Rd, Rn, imm, 31);
  }

  inline void asr(Register Rd, Register Rn, unsigned imm) {
    sbfm(Rd, Rn, imm, 63);
  }

  inline void lslw(Register Rd, Register Rn, unsigned imm) {
    ubfmw(Rd, Rn, ((32 - imm) & 31), (31 - imm));
  }

  inline void lsl(Register Rd, Register Rn, unsigned imm) {
    ubfm(Rd, Rn, ((64 - imm) & 63), (63 - imm));
  }

  inline void lsrw(Register Rd, Register Rn, unsigned imm) {
    ubfmw(Rd, Rn, imm, 31);
  }

  inline void lsr(Register Rd, Register Rn, unsigned imm) {
    ubfm(Rd, Rn, imm, 63);
  }

  inline void rorw(Register Rd, Register Rn, unsigned imm) {
    extrw(Rd, Rn, Rn, imm);
  }

  inline void ror(Register Rd, Register Rn, unsigned imm) {
    extr(Rd, Rn, Rn, imm);
  }

  inline void sxtbw(Register Rd, Register Rn) {
    sbfmw(Rd, Rn, 0, 7);
  }
  inline void sxthw(Register Rd, Register Rn) {
    sbfmw(Rd, Rn, 0, 15);
  }
  inline void sxtb(Register Rd, Register Rn) {
    sbfm(Rd, Rn, 0, 7);
  }
  inline void sxth(Register Rd, Register Rn) {
    sbfm(Rd, Rn, 0, 15);
  }
  inline void sxtw(Register Rd, Register Rn) {
    sbfm(Rd, Rn, 0, 31);
  }

  inline void uxtbw(Register Rd, Register Rn) {
    ubfmw(Rd, Rn, 0, 7);
  }
  inline void uxthw(Register Rd, Register Rn) {
    ubfmw(Rd, Rn, 0, 15);
  }
  inline void uxtb(Register Rd, Register Rn) {
    ubfm(Rd, Rn, 0, 7);
  }
  inline void uxth(Register Rd, Register Rn) {
    ubfm(Rd, Rn, 0, 15);
  }
  inline void uxtw(Register Rd, Register Rn) {
    ubfm(Rd, Rn, 0, 31);
  }

  inline void cmnw(Register Rn, Register Rm) {
    addsw(zr, Rn, Rm);
  }
  inline void cmn(Register Rn, Register Rm) {
    adds(zr, Rn, Rm);
  }

  inline void cmpw(Register Rn, Register Rm) {
    subsw(zr, Rn, Rm);
  }
  inline void cmp(Register Rn, Register Rm) {
    subs(zr, Rn, Rm);
  }

  inline void negw(Register Rd, Register Rn) {
    subw(Rd, zr, Rn);
  }

  inline void neg(Register Rd, Register Rn) {
    sub(Rd, zr, Rn);
  }

  inline void negsw(Register Rd, Register Rn) {
    subsw(Rd, zr, Rn);
  }

  inline void negs(Register Rd, Register Rn) {
    subs(Rd, zr, Rn);
  }

  inline void cmnw(Register Rn, Register Rm, enum shift_kind kind, unsigned shift = 0) {
    addsw(zr, Rn, Rm, kind, shift);
  }
  inline void cmn(Register Rn, Register Rm, enum shift_kind kind, unsigned shift = 0) {
    adds(zr, Rn, Rm, kind, shift);
  }

  inline void cmpw(Register Rn, Register Rm, enum shift_kind kind, unsigned shift = 0) {
    subsw(zr, Rn, Rm, kind, shift);
  }
  inline void cmp(Register Rn, Register Rm, enum shift_kind kind, unsigned shift = 0) {
    subs(zr, Rn, Rm, kind, shift);
  }

  inline void negw(Register Rd, Register Rn, enum shift_kind kind, unsigned shift = 0) {
    subw(Rd, zr, Rn, kind, shift);
  }

  inline void neg(Register Rd, Register Rn, enum shift_kind kind, unsigned shift = 0) {
    sub(Rd, zr, Rn, kind, shift);
  }

  inline void negsw(Register Rd, Register Rn, enum shift_kind kind, unsigned shift = 0) {
    subsw(Rd, zr, Rn, kind, shift);
  }

  inline void negs(Register Rd, Register Rn, enum shift_kind kind, unsigned shift = 0) {
    subs(Rd, zr, Rn, kind, shift);
  }

  inline void mnegw(Register Rd, Register Rn, Register Rm) {
    msubw(Rd, Rn, Rm, zr);
  }
  inline void mneg(Register Rd, Register Rn, Register Rm) {
    msub(Rd, Rn, Rm, zr);
  }

  inline void mulw(Register Rd, Register Rn, Register Rm) {
    maddw(Rd, Rn, Rm, zr);
  }
  inline void mul(Register Rd, Register Rn, Register Rm) {
    madd(Rd, Rn, Rm, zr);
  }

  inline void smnegl(Register Rd, Register Rn, Register Rm) {
    smsubl(Rd, Rn, Rm, zr);
  }
  inline void smull(Register Rd, Register Rn, Register Rm) {
    smaddl(Rd, Rn, Rm, zr);
  }

  inline void umnegl(Register Rd, Register Rn, Register Rm) {
    umsubl(Rd, Rn, Rm, zr);
  }
  inline void umull(Register Rd, Register Rn, Register Rm) {
    umaddl(Rd, Rn, Rm, zr);
  }

#define WRAP(INSN)                                                            \
  void INSN(Register Rd, Register Rn, Register Rm, Register Ra) {             \
    if ((VM_Version::cpu_cpuFeatures() & VM_Version::CPU_A53MAC) && Ra != zr) \
      nop();                                                                  \
    Assembler::INSN(Rd, Rn, Rm, Ra);                                          \
  }

  WRAP(madd) WRAP(msub) WRAP(maddw) WRAP(msubw)
  WRAP(smaddl) WRAP(smsubl) WRAP(umaddl) WRAP(umsubl)
#undef WRAP


  // macro assembly operations needed for aarch64

  // first two private routines for loading 32 bit or 64 bit constants
private:

  void mov_immediate64(Register dst, u_int64_t imm64);
  void mov_immediate32(Register dst, u_int32_t imm32);

  int push(unsigned int bitset, Register stack);
  int pop(unsigned int bitset, Register stack);

  void mov(Register dst, Address a);

public:
  void push(RegSet regs, Register stack) { if (regs.bits()) push(regs.bits(), stack); }
  void pop(RegSet regs, Register stack) { if (regs.bits()) pop(regs.bits(), stack); }

  // now mov instructions for loading absolute addresses and 32 or
  // 64 bit integers

  inline void mov(Register dst, address addr)
  {
    mov_immediate64(dst, (u_int64_t)addr);
  }

  inline void mov(Register dst, u_int64_t imm64)
  {
    mov_immediate64(dst, imm64);
  }

  inline void movw(Register dst, u_int32_t imm32)
  {
    mov_immediate32(dst, imm32);
  }

  inline void mov(Register dst, long l)
  {
    mov(dst, (u_int64_t)l);
  }

  inline void mov(Register dst, int i)
  {
    mov(dst, (long)i);
  }

  void mov(Register dst, RegisterOrConstant src) {
    if (src.is_register())
      mov(dst, src.as_register());
    else
      mov(dst, src.as_constant());
  }

  void movptr(Register r, uintptr_t imm64);

  void mov(FloatRegister Vd, SIMD_Arrangement T, u_int32_t imm32);

  void mov(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {
    orr(Vd, T, Vn, Vn);
  }

  // macro instructions for accessing and updating floating point
  // status register
  //
  // FPSR : op1 == 011
  //        CRn == 0100
  //        CRm == 0100
  //        op2 == 001

  inline void get_fpsr(Register reg)
  {
    mrs(0b11, 0b0100, 0b0100, 0b001, reg);
  }

  inline void set_fpsr(Register reg)
  {
    msr(0b011, 0b0100, 0b0100, 0b001, reg);
  }

  inline void clear_fpsr()
  {
    msr(0b011, 0b0100, 0b0100, 0b001, zr);
  }

  // idiv variant which deals with MINLONG as dividend and -1 as divisor
  int corrected_idivl(Register result, Register ra, Register rb,
                      bool want_remainder, Register tmp = rscratch1);
  int corrected_idivq(Register result, Register ra, Register rb,
                      bool want_remainder, Register tmp = rscratch1);

  // Support for NULL-checks
  //
  // Generates code that causes a NULL OS exception if the content of reg is NULL.
  // If the accessed location is M[reg + offset] and the offset is known, provide the
  // offset. No explicit code generation is needed if the offset is within a certain
  // range (0 <= offset <= page_size).

  virtual void null_check(Register reg, int offset = -1);
  static bool needs_explicit_null_check(intptr_t offset);

  static address target_addr_for_insn(address insn_addr, unsigned insn);
  static address target_addr_for_insn(address insn_addr) {
    unsigned insn = *(unsigned*)insn_addr;
    return target_addr_for_insn(insn_addr, insn);
  }

  // Required platform-specific helpers for Label::patch_instructions.
  // They _shadow_ the declarations in AbstractAssembler, which are undefined.
  static int pd_patch_instruction_size(address branch, address target);
  static void pd_patch_instruction(address branch, address target) {
    pd_patch_instruction_size(branch, target);
  }
  static address pd_call_destination(address branch) {
    return target_addr_for_insn(branch);
  }
#ifndef PRODUCT
  static void pd_print_patched_instruction(address branch);
#endif

  static int patch_oop(address insn_addr, address o);

  address emit_trampoline_stub(int insts_call_instruction_offset, address target);

  // The following 4 methods return the offset of the appropriate move instruction

  // Support for fast byte/short loading with zero extension (depending on particular CPU)
  int load_unsigned_byte(Register dst, Address src);
  int load_unsigned_short(Register dst, Address src);

  // Support for fast byte/short loading with sign extension (depending on particular CPU)
  int load_signed_byte(Register dst, Address src);
  int load_signed_short(Register dst, Address src);

  int load_signed_byte32(Register dst, Address src);
  int load_signed_short32(Register dst, Address src);

  // Support for sign-extension (hi:lo = extend_sign(lo))
  void extend_sign(Register hi, Register lo);

  // Load and store values by size and signed-ness
  void load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed, Register dst2 = noreg);
  void store_sized_value(Address dst, Register src, size_t size_in_bytes, Register src2 = noreg);

  // Support for inc/dec with optimal instruction selection depending on value

  // x86_64 aliases an unqualified register/address increment and
  // decrement to call incrementq and decrementq but also supports
  // explicitly sized calls to incrementq/decrementq or
  // incrementl/decrementl

  // for aarch64 the proper convention would be to use
  // increment/decrement for 64 bit operatons and
  // incrementw/decrementw for 32 bit operations. so when porting
  // x86_64 code we can leave calls to increment/decrement as is,
  // replace incrementq/decrementq with increment/decrement and
  // replace incrementl/decrementl with incrementw/decrementw.

  // n.b. increment/decrement calls with an Address destination will
  // need to use a scratch register to load the value to be
  // incremented. increment/decrement calls which add or subtract a
  // constant value greater than 2^12 will need to use a 2nd scratch
  // register to hold the constant. so, a register increment/decrement
  // may trash rscratch2 and an address increment/decrement trash
  // rscratch and rscratch2

  void decrementw(Address dst, int value = 1);
  void decrementw(Register reg, int value = 1);

  void decrement(Register reg, int value = 1);
  void decrement(Address dst, int value = 1);

  void incrementw(Address dst, int value = 1);
  void incrementw(Register reg, int value = 1);

  void increment(Register reg, int value = 1);
  void increment(Address dst, int value = 1);


  // Alignment
  void align(int modulus);

  // Stack frame creation/removal
  void enter()
  {
    stp(rfp, lr, Address(pre(sp, -2 * wordSize)));
    mov(rfp, sp);
  }
  void leave()
  {
    mov(sp, rfp);
    ldp(rfp, lr, Address(post(sp, 2 * wordSize)));
  }

  // Support for getting the JavaThread pointer (i.e.; a reference to thread-local information)
  // The pointer will be loaded into the thread register.
  void get_thread(Register thread);


  // Support for VM calls
  //
  // It is imperative that all calls into the VM are handled via the call_VM macros.
  // They make sure that the stack linkage is setup correctly. call_VM's correspond
  // to ENTRY/ENTRY_X entry points while call_VM_leaf's correspond to LEAF entry points.


  void call_VM(Register oop_result,
               address entry_point,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               address entry_point,
               Register arg_1,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               address entry_point,
               Register arg_1, Register arg_2,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               address entry_point,
               Register arg_1, Register arg_2, Register arg_3,
               bool check_exceptions = true);

  // Overloadings with last_Java_sp
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               int number_of_arguments = 0,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               Register arg_1, bool
               check_exceptions = true);
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               Register arg_1, Register arg_2,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               Register arg_1, Register arg_2, Register arg_3,
               bool check_exceptions = true);

  void get_vm_result  (Register oop_result, Register thread);
  void get_vm_result_2(Register metadata_result, Register thread);

  // These always tightly bind to MacroAssembler::call_VM_base
  // bypassing the virtual implementation
  void super_call_VM(Register oop_result, Register last_java_sp, address entry_point, int number_of_arguments = 0, bool check_exceptions = true);
  void super_call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions = true);
  void super_call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, bool check_exceptions = true);
  void super_call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions = true);
  void super_call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, Register arg_4, bool check_exceptions = true);

  void call_VM_leaf(address entry_point,
                    int number_of_arguments = 0);
  void call_VM_leaf(address entry_point,
                    Register arg_1);
  void call_VM_leaf(address entry_point,
                    Register arg_1, Register arg_2);
  void call_VM_leaf(address entry_point,
                    Register arg_1, Register arg_2, Register arg_3);

  // These always tightly bind to MacroAssembler::call_VM_leaf_base
  // bypassing the virtual implementation
  void super_call_VM_leaf(address entry_point);
  void super_call_VM_leaf(address entry_point, Register arg_1);
  void super_call_VM_leaf(address entry_point, Register arg_1, Register arg_2);
  void super_call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3);
  void super_call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3, Register arg_4);

  // last Java Frame (fills frame anchor)
  void set_last_Java_frame(Register last_java_sp,
                           Register last_java_fp,
                           address last_java_pc,
                           Register scratch);

  void set_last_Java_frame(Register last_java_sp,
                           Register last_java_fp,
                           Label &last_java_pc,
                           Register scratch);

  void set_last_Java_frame(Register last_java_sp,
                           Register last_java_fp,
                           Register last_java_pc,
                           Register scratch);

  void reset_last_Java_frame(Register thread, bool clearfp, bool clear_pc);

  // thread in the default location (r15_thread on 64bit)
  void reset_last_Java_frame(bool clear_fp, bool clear_pc);

  // Stores
  void store_check(Register obj);                // store check for obj - register is destroyed afterwards
  void store_check(Register obj, Address dst);   // same as above, dst is exact store location (reg. is destroyed)

#if INCLUDE_ALL_GCS

  void g1_write_barrier_pre(Register obj,
                            Register pre_val,
                            Register thread,
                            Register tmp,
                            bool tosca_live,
                            bool expand_call);

  void g1_write_barrier_post(Register store_addr,
                             Register new_val,
                             Register thread,
                             Register tmp,
                             Register tmp2);

#endif // INCLUDE_ALL_GCS

  // oop manipulations
  void load_klass(Register dst, Register src);
  void store_klass(Register dst, Register src);
  void cmp_klass(Register oop, Register trial_klass, Register tmp);

  void load_heap_oop(Register dst, Address src);

  void load_heap_oop_not_null(Register dst, Address src);
  void store_heap_oop(Address dst, Register src);

  // currently unimplemented
  // Used for storing NULL. All other oop constants should be
  // stored using routines that take a jobject.
  void store_heap_oop_null(Address dst);

  void load_prototype_header(Register dst, Register src);

  void store_klass_gap(Register dst, Register src);

  // This dummy is to prevent a call to store_heap_oop from
  // converting a zero (like NULL) into a Register by giving
  // the compiler two choices it can't resolve

  void store_heap_oop(Address dst, void* dummy);

  void encode_heap_oop(Register d, Register s);
  void encode_heap_oop(Register r) { encode_heap_oop(r, r); }
  void decode_heap_oop(Register d, Register s);
  void decode_heap_oop(Register r) { decode_heap_oop(r, r); }
  void encode_heap_oop_not_null(Register r);
  void decode_heap_oop_not_null(Register r);
  void encode_heap_oop_not_null(Register dst, Register src);
  void decode_heap_oop_not_null(Register dst, Register src);

  void set_narrow_oop(Register dst, jobject obj);

  void encode_klass_not_null(Register r);
  void decode_klass_not_null(Register r);
  void encode_klass_not_null(Register dst, Register src);
  void decode_klass_not_null(Register dst, Register src);

  void set_narrow_klass(Register dst, Klass* k);

  // if heap base register is used - reinit it with the correct value
  void reinit_heapbase();

  DEBUG_ONLY(void verify_heapbase(const char* msg);)

  void push_CPU_state(bool save_vectors = false);
  void pop_CPU_state(bool restore_vectors = false) ;

  // Round up to a power of two
  void round_to(Register reg, int modulus);

  // allocation
  void eden_allocate(
    Register obj,                      // result: pointer to object after successful allocation
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Label&   slow_case                 // continuation point if fast allocation fails
  );
  void tlab_allocate(
    Register obj,                      // result: pointer to object after successful allocation
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Register t2,                       // temp register
    Label&   slow_case                 // continuation point if fast allocation fails
  );
  Register tlab_refill(Label& retry_tlab, Label& try_eden, Label& slow_case); // returns TLS address
  void verify_tlab();

  void incr_allocated_bytes(Register thread,
                            Register var_size_in_bytes, int con_size_in_bytes,
                            Register t1 = noreg);

  // interface method calling
  void lookup_interface_method(Register recv_klass,
                               Register intf_klass,
                               RegisterOrConstant itable_index,
                               Register method_result,
                               Register scan_temp,
                               Label& no_such_interface);

  // virtual method calling
  // n.b. x86 allows RegisterOrConstant for vtable_index
  void lookup_virtual_method(Register recv_klass,
                             RegisterOrConstant vtable_index,
                             Register method_result);

  // Test sub_klass against super_klass, with fast and slow paths.

  // The fast path produces a tri-state answer: yes / no / maybe-slow.
  // One of the three labels can be NULL, meaning take the fall-through.
  // If super_check_offset is -1, the value is loaded up from super_klass.
  // No registers are killed, except temp_reg.
  void check_klass_subtype_fast_path(Register sub_klass,
                                     Register super_klass,
                                     Register temp_reg,
                                     Label* L_success,
                                     Label* L_failure,
                                     Label* L_slow_path,
                RegisterOrConstant super_check_offset = RegisterOrConstant(-1));

  // The rest of the type check; must be wired to a corresponding fast path.
  // It does not repeat the fast path logic, so don't use it standalone.
  // The temp_reg and temp2_reg can be noreg, if no temps are available.
  // Updates the sub's secondary super cache as necessary.
  // If set_cond_codes, condition codes will be Z on success, NZ on failure.
  void check_klass_subtype_slow_path(Register sub_klass,
                                     Register super_klass,
                                     Register temp_reg,
                                     Register temp2_reg,
                                     Label* L_success,
                                     Label* L_failure,
                                     bool set_cond_codes = false);

  // Simplified, combined version, good for typical uses.
  // Falls through on failure.
  void check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register temp_reg,
                           Label& L_success);

  Address argument_address(RegisterOrConstant arg_slot, int extra_slot_offset = 0);


  // Debugging

  // only if +VerifyOops
  void verify_oop(Register reg, const char* s = "broken oop");
  void verify_oop_addr(Address addr, const char * s = "broken oop addr");

// TODO: verify method and klass metadata (compare against vptr?)
  void _verify_method_ptr(Register reg, const char * msg, const char * file, int line) {}
  void _verify_klass_ptr(Register reg, const char * msg, const char * file, int line){}

#define verify_method_ptr(reg) _verify_method_ptr(reg, "broken method " #reg, __FILE__, __LINE__)
#define verify_klass_ptr(reg) _verify_klass_ptr(reg, "broken klass " #reg, __FILE__, __LINE__)

  // only if +VerifyFPU
  void verify_FPU(int stack_depth, const char* s = "illegal FPU state");

  // prints msg, dumps registers and stops execution
  void stop(const char* msg);

  // prints msg and continues
  void warn(const char* msg);

  static void debug64(char* msg, int64_t pc, int64_t regs[]);

  void untested()                                { stop("untested"); }

  void unimplemented(const char* what = "")      { char* b = new char[1024];  jio_snprintf(b, 1024, "unimplemented: %s", what);  stop(b); }

  void should_not_reach_here()                   { stop("should not reach here"); }

  // Stack overflow checking
  void bang_stack_with_offset(int offset) {
    // stack grows down, caller passes positive offset
    assert(offset > 0, "must bang with negative offset");
    mov(rscratch2, -offset);
    str(zr, Address(sp, rscratch2));
  }

  // Writes to stack successive pages until offset reached to check for
  // stack overflow + shadow pages.  Also, clobbers tmp
  void bang_stack_size(Register size, Register tmp);

  virtual RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr,
                                                Register tmp,
                                                int offset);

  // Support for serializing memory accesses between threads
  void serialize_memory(Register thread, Register tmp);

  // Arithmetics

  void addptr(const Address &dst, int32_t src);
  void cmpptr(Register src1, Address src2);

  // Various forms of CAS

  void cmpxchgptr(Register oldv, Register newv, Register addr, Register tmp,
                  Label &suceed, Label *fail);

  void cmpxchgw(Register oldv, Register newv, Register addr, Register tmp,
                  Label &suceed, Label *fail);

  void atomic_add(Register prev, RegisterOrConstant incr, Register addr);
  void atomic_addw(Register prev, RegisterOrConstant incr, Register addr);

  void atomic_xchg(Register prev, Register newv, Register addr);
  void atomic_xchgw(Register prev, Register newv, Register addr);

  void orptr(Address adr, RegisterOrConstant src) {
    ldr(rscratch2, adr);
    if (src.is_register())
      orr(rscratch2, rscratch2, src.as_register());
    else
      orr(rscratch2, rscratch2, src.as_constant());
    str(rscratch2, adr);
  }

  // A generic CAS; success or failure is in the EQ flag.
  template <typename T1, typename T2>
  void cmpxchg(Register addr, Register expected, Register new_val,
               T1 load_insn,
               void (MacroAssembler::*cmp_insn)(Register, Register),
               T2 store_insn,
               Register tmp = rscratch1) {
    Label retry_load, done;
    bind(retry_load);
    (this->*load_insn)(tmp, addr);
    (this->*cmp_insn)(tmp, expected);
    br(Assembler::NE, done);
    (this->*store_insn)(tmp, new_val, addr);
    cbnzw(tmp, retry_load);
    bind(done);
  }

  // Calls

  address trampoline_call(Address entry, CodeBuffer *cbuf = NULL);

  static bool far_branches() {
    return ReservedCodeCacheSize > branch_range;
  }

  // Jumps that can reach anywhere in the code cache.
  // Trashes tmp.
  void far_call(Address entry, CodeBuffer *cbuf = NULL, Register tmp = rscratch1);
  void far_jump(Address entry, CodeBuffer *cbuf = NULL, Register tmp = rscratch1);

  static int far_branch_size() {
    if (far_branches()) {
      return 3 * 4;  // adrp, add, br
    } else {
      return 4;
    }
  }

  // Emit the CompiledIC call idiom
  address ic_call(address entry);

public:

  // Data

  void mov_metadata(Register dst, Metadata* obj);
  Address allocate_metadata_address(Metadata* obj);
  Address constant_oop_address(jobject obj);

  void movoop(Register dst, jobject obj, bool immediate = false);

  // CRC32 code for java.util.zip.CRC32::updateBytes() instrinsic.
  void kernel_crc32(Register crc, Register buf, Register len,
        Register table0, Register table1, Register table2, Register table3,
        Register tmp, Register tmp2, Register tmp3);
  // CRC32 code for java.util.zip.CRC32C::updateBytes() instrinsic.
  void kernel_crc32c(Register crc, Register buf, Register len,
        Register table0, Register table1, Register table2, Register table3,
        Register tmp, Register tmp2, Register tmp3);

#undef VIRTUAL

  // Stack push and pop individual 64 bit registers
  void push(Register src);
  void pop(Register dst);

  // push all registers onto the stack
  void pusha();
  void popa();

  void repne_scan(Register addr, Register value, Register count,
                  Register scratch);
  void repne_scanw(Register addr, Register value, Register count,
                   Register scratch);

  typedef void (MacroAssembler::* add_sub_imm_insn)(Register Rd, Register Rn, unsigned imm);
  typedef void (MacroAssembler::* add_sub_reg_insn)(Register Rd, Register Rn, Register Rm, enum shift_kind kind, unsigned shift);

  // If a constant does not fit in an immediate field, generate some
  // number of MOV instructions and then perform the operation
  void wrap_add_sub_imm_insn(Register Rd, Register Rn, unsigned imm,
                             add_sub_imm_insn insn1,
                             add_sub_reg_insn insn2);
  // Seperate vsn which sets the flags
  void wrap_adds_subs_imm_insn(Register Rd, Register Rn, unsigned imm,
                             add_sub_imm_insn insn1,
                             add_sub_reg_insn insn2);

#define WRAP(INSN)                                                      \
  void INSN(Register Rd, Register Rn, unsigned imm) {                   \
    wrap_add_sub_imm_insn(Rd, Rn, imm, &Assembler::INSN, &Assembler::INSN); \
  }                                                                     \
                                                                        \
  void INSN(Register Rd, Register Rn, Register Rm,                      \
             enum shift_kind kind, unsigned shift = 0) {                \
    Assembler::INSN(Rd, Rn, Rm, kind, shift);                           \
  }                                                                     \
                                                                        \
  void INSN(Register Rd, Register Rn, Register Rm) {                    \
    Assembler::INSN(Rd, Rn, Rm);                                        \
  }                                                                     \
                                                                        \
  void INSN(Register Rd, Register Rn, Register Rm,                      \
           ext::operation option, int amount = 0) {                     \
    Assembler::INSN(Rd, Rn, Rm, option, amount);                        \
  }

  WRAP(add) WRAP(addw) WRAP(sub) WRAP(subw)

#undef WRAP
#define WRAP(INSN)                                                      \
  void INSN(Register Rd, Register Rn, unsigned imm) {                   \
    wrap_adds_subs_imm_insn(Rd, Rn, imm, &Assembler::INSN, &Assembler::INSN); \
  }                                                                     \
                                                                        \
  void INSN(Register Rd, Register Rn, Register Rm,                      \
             enum shift_kind kind, unsigned shift = 0) {                \
    Assembler::INSN(Rd, Rn, Rm, kind, shift);                           \
  }                                                                     \
                                                                        \
  void INSN(Register Rd, Register Rn, Register Rm) {                    \
    Assembler::INSN(Rd, Rn, Rm);                                        \
  }                                                                     \
                                                                        \
  void INSN(Register Rd, Register Rn, Register Rm,                      \
           ext::operation option, int amount = 0) {                     \
    Assembler::INSN(Rd, Rn, Rm, option, amount);                        \
  }

  WRAP(adds) WRAP(addsw) WRAP(subs) WRAP(subsw)

  void add(Register Rd, Register Rn, RegisterOrConstant increment);
  void addw(Register Rd, Register Rn, RegisterOrConstant increment);
  void sub(Register Rd, Register Rn, RegisterOrConstant decrement);
  void subw(Register Rd, Register Rn, RegisterOrConstant decrement);

  void adrp(Register reg1, const Address &dest, unsigned long &byte_offset);

  void tableswitch(Register index, jint lowbound, jint highbound,
                   Label &jumptable, Label &jumptable_end, int stride = 1) {
    adr(rscratch1, jumptable);
    subsw(rscratch2, index, lowbound);
    subsw(zr, rscratch2, highbound - lowbound);
    br(Assembler::HS, jumptable_end);
    add(rscratch1, rscratch1, rscratch2,
        ext::sxtw, exact_log2(stride * Assembler::instruction_size));
    br(rscratch1);
  }

  // Form an address from base + offset in Rd.  Rd may or may not
  // actually be used: you must use the Address that is returned.  It
  // is up to you to ensure that the shift provided matches the size
  // of your data.
  Address form_address(Register Rd, Register base, long byte_offset, int shift);

  // Prolog generator routines to support switch between x86 code and
  // generated ARM code

  // routine to generate an x86 prolog for a stub function which
  // bootstraps into the generated ARM code which directly follows the
  // stub
  //

  public:
  // enum used for aarch64--x86 linkage to define return type of x86 function
  enum ret_type { ret_type_void, ret_type_integral, ret_type_float, ret_type_double};

#ifdef BUILTIN_SIM
  void c_stub_prolog(int gp_arg_count, int fp_arg_count, int ret_type, address *prolog_ptr = NULL);
#else
  void c_stub_prolog(int gp_arg_count, int fp_arg_count, int ret_type) { }
#endif

  // special version of call_VM_leaf_base needed for aarch64 simulator
  // where we need to specify both the gp and fp arg counts and the
  // return type so that the linkage routine from aarch64 to x86 and
  // back knows which aarch64 registers to copy to x86 registers and
  // which x86 result register to copy back to an aarch64 register

  void call_VM_leaf_base1(
    address  entry_point,             // the entry point
    int      number_of_gp_arguments,  // the number of gp reg arguments to pass
    int      number_of_fp_arguments,  // the number of fp reg arguments to pass
    ret_type type,                    // the return type for the call
    Label*   retaddr = NULL
  );

  void ldr_constant(Register dest, const Address &const_addr) {
    if (NearCpool) {
      ldr(dest, const_addr);
    } else {
      unsigned long offset;
      adrp(dest, InternalAddress(const_addr.target()), offset);
      ldr(dest, Address(dest, offset));
    }
  }

  address read_polling_page(Register r, address page, relocInfo::relocType rtype);
  address read_polling_page(Register r, relocInfo::relocType rtype);

  // CRC32 code for java.util.zip.CRC32::updateBytes() instrinsic.
  void update_byte_crc32(Register crc, Register val, Register table);
  void update_word_crc32(Register crc, Register v, Register tmp,
        Register table0, Register table1, Register table2, Register table3,
        bool upper = false);

  void string_compare(Register str1, Register str2,
                      Register cnt1, Register cnt2, Register result,
                      Register tmp1);
  void string_equals(Register str1, Register str2,
                     Register cnt, Register result,
                     Register tmp1);
  void char_arrays_equals(Register ary1, Register ary2,
                          Register result, Register tmp1);
  void encode_iso_array(Register src, Register dst,
                        Register len, Register result,
                        FloatRegister Vtmp1, FloatRegister Vtmp2,
                        FloatRegister Vtmp3, FloatRegister Vtmp4);
  void string_indexof(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      Register tmp1, Register tmp2,
                      Register tmp3, Register tmp4,
                      int int_cnt1, Register result);
private:
  void add2_with_carry(Register final_dest_hi, Register dest_hi, Register dest_lo,
                       Register src1, Register src2);
  void add2_with_carry(Register dest_hi, Register dest_lo, Register src1, Register src2) {
    add2_with_carry(dest_hi, dest_hi, dest_lo, src1, src2);
  }
  void multiply_64_x_64_loop(Register x, Register xstart, Register x_xstart,
                             Register y, Register y_idx, Register z,
                             Register carry, Register product,
                             Register idx, Register kdx);
  void multiply_128_x_128_loop(Register y, Register z,
                               Register carry, Register carry2,
                               Register idx, Register jdx,
                               Register yz_idx1, Register yz_idx2,
                               Register tmp, Register tmp3, Register tmp4,
                               Register tmp7, Register product_hi);
public:
  void multiply_to_len(Register x, Register xlen, Register y, Register ylen, Register z,
                       Register zlen, Register tmp1, Register tmp2, Register tmp3,
                       Register tmp4, Register tmp5, Register tmp6, Register tmp7);
  // ISB may be needed because of a safepoint
  void maybe_isb() { isb(); }

private:
  // Return the effective address r + (r1 << ext) + offset.
  // Uses rscratch2.
  Address offsetted_address(Register r, Register r1, Address::extend ext,
                            int offset, int size);

private:
  // Returns an address on the stack which is reachable with a ldr/str of size
  // Uses rscratch2 if the address is not directly reachable
  Address spill_address(int size, int offset, Register tmp=rscratch2);

public:
  void spill(Register Rx, bool is64, int offset) {
    if (is64) {
      str(Rx, spill_address(8, offset));
    } else {
      strw(Rx, spill_address(4, offset));
    }
  }
  void spill(FloatRegister Vx, SIMD_RegVariant T, int offset) {
    str(Vx, T, spill_address(1 << (int)T, offset));
  }
  void unspill(Register Rx, bool is64, int offset) {
    if (is64) {
      ldr(Rx, spill_address(8, offset));
    } else {
      ldrw(Rx, spill_address(4, offset));
    }
  }
  void unspill(FloatRegister Vx, SIMD_RegVariant T, int offset) {
    ldr(Vx, T, spill_address(1 << (int)T, offset));
  }
  void spill_copy128(int src_offset, int dst_offset,
                     Register tmp1=rscratch1, Register tmp2=rscratch2) {
    if (src_offset < 512 && (src_offset & 7) == 0 &&
        dst_offset < 512 && (dst_offset & 7) == 0) {
      ldp(tmp1, tmp2, Address(sp, src_offset));
      stp(tmp1, tmp2, Address(sp, dst_offset));
    } else {
      unspill(tmp1, true, src_offset);
      spill(tmp1, true, dst_offset);
      unspill(tmp1, true, src_offset+8);
      spill(tmp1, true, dst_offset+8);
    }
  }
};

#ifdef ASSERT
inline bool AbstractAssembler::pd_check_instruction_mark() { return false; }
#endif

/**
 * class SkipIfEqual:
 *
 * Instantiating this class will result in assembly code being output that will
 * jump around any code emitted between the creation of the instance and it's
 * automatic destruction at the end of a scope block, depending on the value of
 * the flag passed to the constructor, which will be checked at run-time.
 */
class SkipIfEqual {
 private:
  MacroAssembler* _masm;
  Label _label;

 public:
   SkipIfEqual(MacroAssembler*, const bool* flag_addr, bool value);
   ~SkipIfEqual();
};

struct tableswitch {
  Register _reg;
  int _insn_index; jint _first_key; jint _last_key;
  Label _after;
  Label _branches;
};

#endif // CPU_AARCH64_VM_MACROASSEMBLER_AARCH64_HPP

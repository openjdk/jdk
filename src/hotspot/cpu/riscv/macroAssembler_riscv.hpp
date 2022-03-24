/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
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

#ifndef CPU_RISCV_MACROASSEMBLER_RISCV_HPP
#define CPU_RISCV_MACROASSEMBLER_RISCV_HPP

#include "asm/assembler.hpp"
#include "metaprogramming/enableIf.hpp"
#include "oops/compressedOops.hpp"
#include "utilities/powerOfTwo.hpp"

// MacroAssembler extends Assembler by frequently used macros.
//
// Instructions for which a 'better' code sequence exists depending
// on arguments should also go in here.

class MacroAssembler: public Assembler {

 public:
  MacroAssembler(CodeBuffer* code) : Assembler(code) {
  }
  virtual ~MacroAssembler() {}

  void safepoint_poll(Label& slow_path, bool at_return, bool acquire, bool in_nmethod);

  // Place a fence.i after code may have been modified due to a safepoint.
  void safepoint_ifence();

  // Alignment
  void align(int modulus, int extra_offset = 0);

  // Stack frame creation/removal
  // Note that SP must be updated to the right place before saving/restoring RA and FP
  // because signal based thread suspend/resume could happen asynchronously.
  void enter() {
    addi(sp, sp, - 2 * wordSize);
    sd(ra, Address(sp, wordSize));
    sd(fp, Address(sp));
    addi(fp, sp, 2 * wordSize);
  }

  void leave() {
    addi(sp, fp, - 2 * wordSize);
    ld(fp, Address(sp));
    ld(ra, Address(sp, wordSize));
    addi(sp, sp, 2 * wordSize);
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
               Register arg_1,
               bool check_exceptions = true);
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

  void get_vm_result(Register oop_result, Register java_thread);
  void get_vm_result_2(Register metadata_result, Register java_thread);

  // These always tightly bind to MacroAssembler::call_VM_leaf_base
  // bypassing the virtual implementation
  void call_VM_leaf(address entry_point,
                    int number_of_arguments = 0);
  void call_VM_leaf(address entry_point,
                    Register arg_0);
  void call_VM_leaf(address entry_point,
                    Register arg_0, Register arg_1);
  void call_VM_leaf(address entry_point,
                    Register arg_0, Register arg_1, Register arg_2);

  // These always tightly bind to MacroAssembler::call_VM_base
  // bypassing the virtual implementation
  void super_call_VM_leaf(address entry_point, Register arg_0);
  void super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1);
  void super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2);
  void super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2, Register arg_3);

  // last Java Frame (fills frame anchor)
  void set_last_Java_frame(Register last_java_sp, Register last_java_fp, address last_java_pc, Register tmp);
  void set_last_Java_frame(Register last_java_sp, Register last_java_fp, Label &last_java_pc, Register tmp);
  void set_last_Java_frame(Register last_java_sp, Register last_java_fp, Register last_java_pc, Register tmp);

  // thread in the default location (xthread)
  void reset_last_Java_frame(bool clear_fp);

  void call_native(address entry_point,
                   Register arg_0);
  void call_native_base(
    address entry_point,                // the entry point
    Label*  retaddr = NULL
  );

  virtual void call_VM_leaf_base(
    address entry_point,                // the entry point
    int     number_of_arguments,        // the number of arguments to pop after the call
    Label*  retaddr = NULL
  );

  virtual void call_VM_leaf_base(
    address entry_point,                // the entry point
    int     number_of_arguments,        // the number of arguments to pop after the call
    Label&  retaddr) {
    call_VM_leaf_base(entry_point, number_of_arguments, &retaddr);
  }

  virtual void call_VM_base(           // returns the register containing the thread upon return
    Register oop_result,               // where an oop-result ends up if any; use noreg otherwise
    Register java_thread,              // the thread if computed before     ; use noreg otherwise
    Register last_java_sp,             // to set up last_Java_frame in stubs; use noreg otherwise
    address  entry_point,              // the entry point
    int      number_of_arguments,      // the number of arguments (w/o thread) to pop after the call
    bool     check_exceptions          // whether to check for pending exceptions after return
  );

  void call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions);

  virtual void check_and_handle_earlyret(Register java_thread);
  virtual void check_and_handle_popframe(Register java_thread);

  void resolve_weak_handle(Register result, Register tmp);
  void resolve_oop_handle(Register result, Register tmp = x15);
  void resolve_jobject(Register value, Register thread, Register tmp);

  void movoop(Register dst, jobject obj, bool immediate = false);
  void mov_metadata(Register dst, Metadata* obj);
  void bang_stack_size(Register size, Register tmp);
  void set_narrow_oop(Register dst, jobject obj);
  void set_narrow_klass(Register dst, Klass* k);

  void load_mirror(Register dst, Register method, Register tmp = x15);
  void access_load_at(BasicType type, DecoratorSet decorators, Register dst,
                      Address src, Register tmp1, Register thread_tmp);
  void access_store_at(BasicType type, DecoratorSet decorators, Address dst,
                       Register src, Register tmp1, Register thread_tmp);
  void load_klass(Register dst, Register src);
  void store_klass(Register dst, Register src);
  void cmp_klass(Register oop, Register trial_klass, Register tmp, Label &L);

  void encode_klass_not_null(Register r);
  void decode_klass_not_null(Register r);
  void encode_klass_not_null(Register dst, Register src, Register tmp = xheapbase);
  void decode_klass_not_null(Register dst, Register src, Register tmp = xheapbase);
  void decode_heap_oop_not_null(Register r);
  void decode_heap_oop_not_null(Register dst, Register src);
  void decode_heap_oop(Register d, Register s);
  void decode_heap_oop(Register r) { decode_heap_oop(r, r); }
  void encode_heap_oop(Register d, Register s);
  void encode_heap_oop(Register r) { encode_heap_oop(r, r); };
  void load_heap_oop(Register dst, Address src, Register tmp1 = noreg,
                     Register thread_tmp = noreg, DecoratorSet decorators = 0);
  void load_heap_oop_not_null(Register dst, Address src, Register tmp1 = noreg,
                              Register thread_tmp = noreg, DecoratorSet decorators = 0);
  void store_heap_oop(Address dst, Register src, Register tmp1 = noreg,
                      Register thread_tmp = noreg, DecoratorSet decorators = 0);

  void store_klass_gap(Register dst, Register src);

  // currently unimplemented
  // Used for storing NULL. All other oop constants should be
  // stored using routines that take a jobject.
  void store_heap_oop_null(Address dst);

  // This dummy is to prevent a call to store_heap_oop from
  // converting a zero (linke NULL) into a Register by giving
  // the compiler two choices it can't resolve

  void store_heap_oop(Address dst, void* dummy);

  // Support for NULL-checks
  //
  // Generates code that causes a NULL OS exception if the content of reg is NULL.
  // If the accessed location is M[reg + offset] and the offset is known, provide the
  // offset. No explicit code generateion is needed if the offset is within a certain
  // range (0 <= offset <= page_size).

  virtual void null_check(Register reg, int offset = -1);
  static bool needs_explicit_null_check(intptr_t offset);
  static bool uses_implicit_null_check(void* address);

  // idiv variant which deals with MINLONG as dividend and -1 as divisor
  int corrected_idivl(Register result, Register rs1, Register rs2,
                      bool want_remainder);
  int corrected_idivq(Register result, Register rs1, Register rs2,
                      bool want_remainder);

  // interface method calling
  void lookup_interface_method(Register recv_klass,
                               Register intf_klass,
                               RegisterOrConstant itable_index,
                               Register method_result,
                               Register scan_tmp,
                               Label& no_such_interface,
                               bool return_method = true);

  // virtual method calling
  // n.n. x86 allows RegisterOrConstant for vtable_index
  void lookup_virtual_method(Register recv_klass,
                             RegisterOrConstant vtable_index,
                             Register method_result);

  // Form an addres from base + offset in Rd. Rd my or may not
  // actually be used: you must use the Address that is returned. It
  // is up to you to ensure that the shift provided mathces the size
  // of your data.
  Address form_address(Register Rd, Register base, long byte_offset);

  // allocation
  void tlab_allocate(
    Register obj,                   // result: pointer to object after successful allocation
    Register var_size_in_bytes,     // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,     // object size in bytes if   known at compile time
    Register tmp1,                  // temp register
    Register tmp2,                  // temp register
    Label&   slow_case,             // continuation point of fast allocation fails
    bool is_far = false
  );

  void eden_allocate(
    Register obj,                   // result: pointer to object after successful allocation
    Register var_size_in_bytes,     // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,     // object size in bytes if   known at compile time
    Register tmp,                   // temp register
    Label&   slow_case,             // continuation point if fast allocation fails
    bool is_far = false
  );

  // Test sub_klass against super_klass, with fast and slow paths.

  // The fast path produces a tri-state answer: yes / no / maybe-slow.
  // One of the three labels can be NULL, meaning take the fall-through.
  // If super_check_offset is -1, the value is loaded up from super_klass.
  // No registers are killed, except tmp_reg
  void check_klass_subtype_fast_path(Register sub_klass,
                                     Register super_klass,
                                     Register tmp_reg,
                                     Label* L_success,
                                     Label* L_failure,
                                     Label* L_slow_path,
                                     Register super_check_offset = noreg);

  // The reset of the type cehck; must be wired to a corresponding fast path.
  // It does not repeat the fast path logic, so don't use it standalone.
  // The tmp1_reg and tmp2_reg can be noreg, if no temps are avaliable.
  // Updates the sub's secondary super cache as necessary.
  void check_klass_subtype_slow_path(Register sub_klass,
                                     Register super_klass,
                                     Register tmp1_reg,
                                     Register tmp2_reg,
                                     Label* L_success,
                                     Label* L_failure);

  void check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register tmp_reg,
                           Label& L_success);

  Address argument_address(RegisterOrConstant arg_slot, int extra_slot_offset = 0);

  // only if +VerifyOops
  void verify_oop(Register reg, const char* s = "broken oop");
  void verify_oop_addr(Address addr, const char* s = "broken oop addr");

  void _verify_method_ptr(Register reg, const char* msg, const char* file, int line) {}
  void _verify_klass_ptr(Register reg, const char* msg, const char* file, int line) {}

#define verify_method_ptr(reg) _verify_method_ptr(reg, "broken method " #reg, __FILE__, __LINE__)
#define verify_klass_ptr(reg) _verify_method_ptr(reg, "broken klass " #reg, __FILE__, __LINE__)

  // A more convenient access to fence for our purposes
  // We used four bit to indicate the read and write bits in the predecessors and successors,
  // and extended i for r, o for w if UseConservativeFence enabled.
  enum Membar_mask_bits {
    StoreStore = 0b0101,               // (pred = ow   + succ =   ow)
    LoadStore  = 0b1001,               // (pred = ir   + succ =   ow)
    StoreLoad  = 0b0110,               // (pred = ow   + succ =   ir)
    LoadLoad   = 0b1010,               // (pred = ir   + succ =   ir)
    AnyAny     = LoadStore | StoreLoad // (pred = iorw + succ = iorw)
  };

  void membar(uint32_t order_constraint);

  static void membar_mask_to_pred_succ(uint32_t order_constraint, uint32_t& predecessor, uint32_t& successor) {
    predecessor = (order_constraint >> 2) & 0x3;
    successor = order_constraint & 0x3;

    // extend rw -> iorw:
    // 01(w) -> 0101(ow)
    // 10(r) -> 1010(ir)
    // 11(rw)-> 1111(iorw)
    if (UseConservativeFence) {
      predecessor |= predecessor << 2;
      successor |= successor << 2;
    }
  }

  static int pred_succ_to_membar_mask(uint32_t predecessor, uint32_t successor) {
    return ((predecessor & 0x3) << 2) | (successor & 0x3);
  }

  // prints msg, dumps registers and stops execution
  void stop(const char* msg);

  static void debug64(char* msg, int64_t pc, int64_t regs[]);

  void unimplemented(const char* what = "");

  void should_not_reach_here() { stop("should not reach here"); }

  static address target_addr_for_insn(address insn_addr);

  // Required platform-specific helpers for Label::patch_instructions.
  // They _shadow_ the declarations in AbstractAssembler, which are undefined.
  static int pd_patch_instruction_size(address branch, address target);
  static void pd_patch_instruction(address branch, address target, const char* file = NULL, int line = 0) {
    pd_patch_instruction_size(branch, target);
  }
  static address pd_call_destination(address branch) {
    return target_addr_for_insn(branch);
  }

  static int patch_oop(address insn_addr, address o);
  address emit_trampoline_stub(int insts_call_instruction_offset, address target);
  void emit_static_call_stub();

  // The following 4 methods return the offset of the appropriate move instruction

  // Support for fast byte/short loading with zero extension (depending on particular CPU)
  int load_unsigned_byte(Register dst, Address src);
  int load_unsigned_short(Register dst, Address src);

  // Support for fast byte/short loading with sign extension (depending on particular CPU)
  int load_signed_byte(Register dst, Address src);
  int load_signed_short(Register dst, Address src);

  // Load and store values by size and signed-ness
  void load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed, Register dst2 = noreg);
  void store_sized_value(Address dst, Register src, size_t size_in_bytes, Register src2 = noreg);

 public:
  // Standard pseudoinstruction
  void nop();
  void mv(Register Rd, Register Rs);
  void notr(Register Rd, Register Rs);
  void neg(Register Rd, Register Rs);
  void negw(Register Rd, Register Rs);
  void sext_w(Register Rd, Register Rs);
  void zext_b(Register Rd, Register Rs);
  void seqz(Register Rd, Register Rs);          // set if = zero
  void snez(Register Rd, Register Rs);          // set if != zero
  void sltz(Register Rd, Register Rs);          // set if < zero
  void sgtz(Register Rd, Register Rs);          // set if > zero

  // Float pseudoinstruction
  void fmv_s(FloatRegister Rd, FloatRegister Rs);
  void fabs_s(FloatRegister Rd, FloatRegister Rs);    // single-precision absolute value
  void fneg_s(FloatRegister Rd, FloatRegister Rs);

  // Double pseudoinstruction
  void fmv_d(FloatRegister Rd, FloatRegister Rs);
  void fabs_d(FloatRegister Rd, FloatRegister Rs);
  void fneg_d(FloatRegister Rd, FloatRegister Rs);

  // Pseudoinstruction for control and status register
  void rdinstret(Register Rd);                  // read instruction-retired counter
  void rdcycle(Register Rd);                    // read cycle counter
  void rdtime(Register Rd);                     // read time
  void csrr(Register Rd, unsigned csr);         // read csr
  void csrw(unsigned csr, Register Rs);         // write csr
  void csrs(unsigned csr, Register Rs);         // set bits in csr
  void csrc(unsigned csr, Register Rs);         // clear bits in csr
  void csrwi(unsigned csr, unsigned imm);
  void csrsi(unsigned csr, unsigned imm);
  void csrci(unsigned csr, unsigned imm);
  void frcsr(Register Rd);                      // read float-point csr
  void fscsr(Register Rd, Register Rs);         // swap float-point csr
  void fscsr(Register Rs);                      // write float-point csr
  void frrm(Register Rd);                       // read float-point rounding mode
  void fsrm(Register Rd, Register Rs);          // swap float-point rounding mode
  void fsrm(Register Rs);                       // write float-point rounding mode
  void fsrmi(Register Rd, unsigned imm);
  void fsrmi(unsigned imm);
  void frflags(Register Rd);                    // read float-point exception flags
  void fsflags(Register Rd, Register Rs);       // swap float-point exception flags
  void fsflags(Register Rs);                    // write float-point exception flags
  void fsflagsi(Register Rd, unsigned imm);
  void fsflagsi(unsigned imm);

  void beqz(Register Rs, const address &dest);
  void bnez(Register Rs, const address &dest);
  void blez(Register Rs, const address &dest);
  void bgez(Register Rs, const address &dest);
  void bltz(Register Rs, const address &dest);
  void bgtz(Register Rs, const address &dest);
  void la(Register Rd, Label &label);
  void la(Register Rd, const address &dest);
  void la(Register Rd, const Address &adr);
  //label
  void beqz(Register Rs, Label &l, bool is_far = false);
  void bnez(Register Rs, Label &l, bool is_far = false);
  void blez(Register Rs, Label &l, bool is_far = false);
  void bgez(Register Rs, Label &l, bool is_far = false);
  void bltz(Register Rs, Label &l, bool is_far = false);
  void bgtz(Register Rs, Label &l, bool is_far = false);
  void float_beq(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void float_bne(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void float_ble(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void float_bge(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void float_blt(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void float_bgt(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void double_beq(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void double_bne(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void double_ble(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void double_bge(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void double_blt(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);
  void double_bgt(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far = false, bool is_unordered = false);

  void push_reg(RegSet regs, Register stack) { if (regs.bits()) { push_reg(regs.bits(), stack); } }
  void pop_reg(RegSet regs, Register stack) { if (regs.bits()) { pop_reg(regs.bits(), stack); } }
  void push_reg(Register Rs);
  void pop_reg(Register Rd);
  int  push_reg(unsigned int bitset, Register stack);
  int  pop_reg(unsigned int bitset, Register stack);
  void push_fp(FloatRegSet regs, Register stack) { if (regs.bits()) push_fp(regs.bits(), stack); }
  void pop_fp(FloatRegSet regs, Register stack) { if (regs.bits()) pop_fp(regs.bits(), stack); }
#ifdef COMPILER2
  void push_vp(VectorRegSet regs, Register stack) { if (regs.bits()) push_vp(regs.bits(), stack); }
  void pop_vp(VectorRegSet regs, Register stack) { if (regs.bits()) pop_vp(regs.bits(), stack); }
#endif // COMPILER2

  // Push and pop everything that might be clobbered by a native
  // runtime call except t0 and t1. (They are always
  // temporary registers, so we don't have to protect them.)
  // Additional registers can be excluded in a passed RegSet.
  void push_call_clobbered_registers_except(RegSet exclude);
  void pop_call_clobbered_registers_except(RegSet exclude);

  void push_call_clobbered_registers() {
    push_call_clobbered_registers_except(RegSet());
  }
  void pop_call_clobbered_registers() {
    pop_call_clobbered_registers_except(RegSet());
  }

  void pusha();
  void popa();
  void push_CPU_state(bool save_vectors = false, int vector_size_in_bytes = 0);
  void pop_CPU_state(bool restore_vectors = false, int vector_size_in_bytes = 0);

  // if heap base register is used - reinit it with the correct value
  void reinit_heapbase();

  void bind(Label& L) {
    Assembler::bind(L);
    // fences across basic blocks should not be merged
    code()->clear_last_insn();
  }

  // mv
  template<typename T, ENABLE_IF(std::is_integral<T>::value)>
  inline void mv(Register Rd, T o) {
    li(Rd, (int64_t)o);
  }

  inline void mvw(Register Rd, int32_t imm32) { mv(Rd, imm32); }

  void mv(Register Rd, Address dest);
  void mv(Register Rd, address addr);
  void mv(Register Rd, RegisterOrConstant src);

  // logic
  void andrw(Register Rd, Register Rs1, Register Rs2);
  void orrw(Register Rd, Register Rs1, Register Rs2);
  void xorrw(Register Rd, Register Rs1, Register Rs2);

  // revb
  void revb_h_h(Register Rd, Register Rs, Register tmp = t0);                           // reverse bytes in halfword in lower 16 bits, sign-extend
  void revb_w_w(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);      // reverse bytes in lower word, sign-extend
  void revb_h_h_u(Register Rd, Register Rs, Register tmp = t0);                         // reverse bytes in halfword in lower 16 bits, zero-extend
  void revb_h_w_u(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);    // reverse bytes in halfwords in lower 32 bits, zero-extend
  void revb_h_helper(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2= t1);  // reverse bytes in upper 16 bits (48:63) and move to lower
  void revb_h(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2= t1);         // reverse bytes in each halfword
  void revb_w(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2= t1);         // reverse bytes in each word
  void revb(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);          // reverse bytes in doubleword

  void ror_imm(Register dst, Register src, uint32_t shift, Register tmp = t0);
  void andi(Register Rd, Register Rn, int64_t imm, Register tmp = t0);
  void orptr(Address adr, RegisterOrConstant src, Register tmp1 = t0, Register tmp2 = t1);

  void cmpxchg_obj_header(Register oldv, Register newv, Register obj, Register tmp, Label &succeed, Label *fail);
  void cmpxchgptr(Register oldv, Register newv, Register addr, Register tmp, Label &succeed, Label *fail);
  void cmpxchg(Register addr, Register expected,
               Register new_val,
               enum operand_size size,
               Assembler::Aqrl acquire, Assembler::Aqrl release,
               Register result, bool result_as_bool = false);
  void cmpxchg_weak(Register addr, Register expected,
                    Register new_val,
                    enum operand_size size,
                    Assembler::Aqrl acquire, Assembler::Aqrl release,
                    Register result);
  void cmpxchg_narrow_value_helper(Register addr, Register expected,
                                   Register new_val,
                                   enum operand_size size,
                                   Register tmp1, Register tmp2, Register tmp3);
  void cmpxchg_narrow_value(Register addr, Register expected,
                            Register new_val,
                            enum operand_size size,
                            Assembler::Aqrl acquire, Assembler::Aqrl release,
                            Register result, bool result_as_bool,
                            Register tmp1, Register tmp2, Register tmp3);
  void weak_cmpxchg_narrow_value(Register addr, Register expected,
                                 Register new_val,
                                 enum operand_size size,
                                 Assembler::Aqrl acquire, Assembler::Aqrl release,
                                 Register result,
                                 Register tmp1, Register tmp2, Register tmp3);

  void atomic_add(Register prev, RegisterOrConstant incr, Register addr);
  void atomic_addw(Register prev, RegisterOrConstant incr, Register addr);
  void atomic_addal(Register prev, RegisterOrConstant incr, Register addr);
  void atomic_addalw(Register prev, RegisterOrConstant incr, Register addr);

  void atomic_xchg(Register prev, Register newv, Register addr);
  void atomic_xchgw(Register prev, Register newv, Register addr);
  void atomic_xchgal(Register prev, Register newv, Register addr);
  void atomic_xchgalw(Register prev, Register newv, Register addr);
  void atomic_xchgwu(Register prev, Register newv, Register addr);
  void atomic_xchgalwu(Register prev, Register newv, Register addr);

  static bool far_branches() {
    return ReservedCodeCacheSize > branch_range;
  }

  // Jumps that can reach anywhere in the code cache.
  // Trashes tmp.
  void far_call(Address entry, CodeBuffer *cbuf = NULL, Register tmp = t0);
  void far_jump(Address entry, CodeBuffer *cbuf = NULL, Register tmp = t0);

  static int far_branch_size() {
    if (far_branches()) {
      return 2 * 4;  // auipc + jalr, see far_call() & far_jump()
    } else {
      return 4;
    }
  }

  void load_byte_map_base(Register reg);

  void bang_stack_with_offset(int offset) {
    // stack grows down, caller passes positive offset
    assert(offset > 0, "must bang with negative offset");
    sub(t0, sp, offset);
    sd(zr, Address(t0));
  }

  void la_patchable(Register reg1, const Address &dest, int32_t &offset);

  virtual void _call_Unimplemented(address call_site) {
    mv(t1, call_site);
  }

  #define call_Unimplemented() _call_Unimplemented((address)__PRETTY_FUNCTION__)

  // Frame creation and destruction shared between JITs.
  void build_frame(int framesize);
  void remove_frame(int framesize);

  void reserved_stack_check();

  void get_polling_page(Register dest, relocInfo::relocType rtype);
  address read_polling_page(Register r, int32_t offset, relocInfo::relocType rtype);

  address trampoline_call(Address entry, CodeBuffer* cbuf = NULL);
  address ic_call(address entry, jint method_index = 0);

  void add_memory_int64(const Address dst, int64_t imm);
  void add_memory_int32(const Address dst, int32_t imm);

  void cmpptr(Register src1, Address src2, Label& equal);

  void clinit_barrier(Register klass, Register tmp, Label* L_fast_path = NULL, Label* L_slow_path = NULL);
  void load_method_holder_cld(Register result, Register method);
  void load_method_holder(Register holder, Register method);

  void compute_index(Register str1, Register trailing_zeros, Register match_mask,
                     Register result, Register char_tmp, Register tmp,
                     bool haystack_isL);
  void compute_match_mask(Register src, Register pattern, Register match_mask,
                          Register mask1, Register mask2);

#ifdef COMPILER2
  void mul_add(Register out, Register in, Register offset,
               Register len, Register k, Register tmp);
  void cad(Register dst, Register src1, Register src2, Register carry);
  void cadc(Register dst, Register src1, Register src2, Register carry);
  void adc(Register dst, Register src1, Register src2, Register carry);
  void add2_with_carry(Register final_dest_hi, Register dest_hi, Register dest_lo,
                       Register src1, Register src2, Register carry);
  void multiply_32_x_32_loop(Register x, Register xstart, Register x_xstart,
                             Register y, Register y_idx, Register z,
                             Register carry, Register product,
                             Register idx, Register kdx);
  void multiply_64_x_64_loop(Register x, Register xstart, Register x_xstart,
                             Register y, Register y_idx, Register z,
                             Register carry, Register product,
                             Register idx, Register kdx);
  void multiply_128_x_128_loop(Register y, Register z,
                               Register carry, Register carry2,
                               Register idx, Register jdx,
                               Register yz_idx1, Register yz_idx2,
                               Register tmp, Register tmp3, Register tmp4,
                               Register tmp6, Register product_hi);
  void multiply_to_len(Register x, Register xlen, Register y, Register ylen,
                       Register z, Register zlen,
                       Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                       Register tmp5, Register tmp6, Register product_hi);
#endif

  void inflate_lo32(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);
  void inflate_hi32(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);

  void ctzc_bit(Register Rd, Register Rs, bool isLL = false, Register tmp1 = t0, Register tmp2 = t1);

  void zero_words(Register base, u_int64_t cnt);
  address zero_words(Register ptr, Register cnt);
  void fill_words(Register base, Register cnt, Register value);
  void zero_memory(Register addr, Register len, Register tmp);

  // shift left by shamt and add
  void shadd(Register Rd, Register Rs1, Register Rs2, Register tmp, int shamt);

  // Here the float instructions with safe deal with some exceptions.
  // e.g. convert from NaN, +Inf, -Inf to int, float, double
  // will trigger exception, we need to deal with these situations
  // to get correct results.
  void fcvt_w_s_safe(Register dst, FloatRegister src, Register tmp = t0);
  void fcvt_l_s_safe(Register dst, FloatRegister src, Register tmp = t0);
  void fcvt_w_d_safe(Register dst, FloatRegister src, Register tmp = t0);
  void fcvt_l_d_safe(Register dst, FloatRegister src, Register tmp = t0);

  // vector load/store unit-stride instructions
  void vlex_v(VectorRegister vd, Register base, Assembler::SEW sew, VectorMask vm = unmasked) {
    switch (sew) {
      case Assembler::e64:
        vle64_v(vd, base, vm);
        break;
      case Assembler::e32:
        vle32_v(vd, base, vm);
        break;
      case Assembler::e16:
        vle16_v(vd, base, vm);
        break;
      case Assembler::e8: // fall through
      default:
        vle8_v(vd, base, vm);
        break;
    }
  }

  void vsex_v(VectorRegister store_data, Register base, Assembler::SEW sew, VectorMask vm = unmasked) {
    switch (sew) {
      case Assembler::e64:
        vse64_v(store_data, base, vm);
        break;
      case Assembler::e32:
        vse32_v(store_data, base, vm);
        break;
      case Assembler::e16:
        vse16_v(store_data, base, vm);
        break;
      case Assembler::e8: // fall through
      default:
        vse8_v(store_data, base, vm);
        break;
    }
  }

  static const int zero_words_block_size;

  void cast_primitive_type(BasicType type, Register Rt) {
    switch (type) {
      case T_BOOLEAN:
        sltu(Rt, zr, Rt);
        break;
      case T_CHAR   :
        zero_extend(Rt, Rt, 16);
        break;
      case T_BYTE   :
        sign_extend(Rt, Rt, 8);
        break;
      case T_SHORT  :
        sign_extend(Rt, Rt, 16);
        break;
      case T_INT    :
        addw(Rt, Rt, zr);
        break;
      case T_LONG   : /* nothing to do */        break;
      case T_VOID   : /* nothing to do */        break;
      case T_FLOAT  : /* nothing to do */        break;
      case T_DOUBLE : /* nothing to do */        break;
      default: ShouldNotReachHere();
    }
  }

  // float cmp with unordered_result
  void float_compare(Register result, FloatRegister Rs1, FloatRegister Rs2, int unordered_result);
  void double_compare(Register result, FloatRegister Rs1, FloatRegister Rs2, int unordered_result);

  // Zero/Sign-extend
  void zero_extend(Register dst, Register src, int bits);
  void sign_extend(Register dst, Register src, int bits);

  // compare src1 and src2 and get -1/0/1 in dst.
  // if [src1 > src2], dst = 1;
  // if [src1 == src2], dst = 0;
  // if [src1 < src2], dst = -1;
  void cmp_l2i(Register dst, Register src1, Register src2, Register tmp = t0);

  int push_fp(unsigned int bitset, Register stack);
  int pop_fp(unsigned int bitset, Register stack);

  int push_vp(unsigned int bitset, Register stack);
  int pop_vp(unsigned int bitset, Register stack);

  // vext
  void vmnot_m(VectorRegister vd, VectorRegister vs);
  void vncvt_x_x_w(VectorRegister vd, VectorRegister vs, VectorMask vm = unmasked);
  void vfneg_v(VectorRegister vd, VectorRegister vs);

private:

#ifdef ASSERT
  // Template short-hand support to clean-up after a failed call to trampoline
  // call generation (see trampoline_call() below), when a set of Labels must
  // be reset (before returning).
  template<typename Label, typename... More>
  void reset_labels(Label& lbl, More&... more) {
    lbl.reset(); reset_labels(more...);
  }
  template<typename Label>
  void reset_labels(Label& lbl) {
    lbl.reset();
  }
#endif
  void repne_scan(Register addr, Register value, Register count, Register tmp);

  // Return true if an address is within the 48-bit RISCV64 address space.
  bool is_valid_riscv64_address(address addr) {
    return ((uintptr_t)addr >> 48) == 0;
  }

  void ld_constant(Register dest, const Address &const_addr) {
    if (NearCpool) {
      ld(dest, const_addr);
    } else {
      int32_t offset = 0;
      la_patchable(dest, InternalAddress(const_addr.target()), offset);
      ld(dest, Address(dest, offset));
    }
  }

  int bitset_to_regs(unsigned int bitset, unsigned char* regs);
  Address add_memory_helper(const Address dst);

  void load_reserved(Register addr, enum operand_size size, Assembler::Aqrl acquire);
  void store_conditional(Register addr, Register new_val, enum operand_size size, Assembler::Aqrl release);

  // Check the current thread doesn't need a cross modify fence.
  void verify_cross_modify_fence_not_required() PRODUCT_RETURN;
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

#endif // CPU_RISCV_MACROASSEMBLER_RISCV_HPP

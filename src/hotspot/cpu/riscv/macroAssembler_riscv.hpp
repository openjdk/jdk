/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2024, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "asm/assembler.inline.hpp"
#include "code/vmreg.hpp"
#include "metaprogramming/enableIf.hpp"
#include "oops/compressedOops.hpp"
#include "utilities/powerOfTwo.hpp"

// MacroAssembler extends Assembler by frequently used macros.
//
// Instructions for which a 'better' code sequence exists depending
// on arguments should also go in here.

class MacroAssembler: public Assembler {

 public:

  MacroAssembler(CodeBuffer* code) : Assembler(code) {}

  void safepoint_poll(Label& slow_path, bool at_return, bool acquire, bool in_nmethod);

  // Alignment
  int align(int modulus, int extra_offset = 0);

  static inline void assert_alignment(address pc, int alignment = MacroAssembler::instruction_size) {
    assert(is_aligned(pc, alignment), "bad alignment");
  }

  // nop
  void post_call_nop();

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
  void set_last_Java_frame(Register last_java_sp, Register last_java_fp, Register last_java_pc);

  // thread in the default location (xthread)
  void reset_last_Java_frame(bool clear_fp);

  virtual void call_VM_leaf_base(
    address entry_point,                // the entry point
    int     number_of_arguments,        // the number of arguments to pop after the call
    Label*  retaddr = nullptr
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

  void resolve_weak_handle(Register result, Register tmp1, Register tmp2);
  void resolve_oop_handle(Register result, Register tmp1, Register tmp2);
  void resolve_jobject(Register value, Register tmp1, Register tmp2);
  void resolve_global_jobject(Register value, Register tmp1, Register tmp2);

  void movoop(Register dst, jobject obj);
  void mov_metadata(Register dst, Metadata* obj);
  void bang_stack_size(Register size, Register tmp);
  void set_narrow_oop(Register dst, jobject obj);
  void set_narrow_klass(Register dst, Klass* k);

  void load_mirror(Register dst, Register method, Register tmp1, Register tmp2);
  void access_load_at(BasicType type, DecoratorSet decorators, Register dst,
                      Address src, Register tmp1, Register tmp2);
  void access_store_at(BasicType type, DecoratorSet decorators, Address dst,
                       Register val, Register tmp1, Register tmp2, Register tmp3);
  void load_klass(Register dst, Register src, Register tmp = t0);
  void load_narrow_klass_compact(Register dst, Register src);
  void store_klass(Register dst, Register src, Register tmp = t0);
  void cmp_klass_compressed(Register oop, Register trial_klass, Register tmp, Label &L, bool equal);

  void encode_klass_not_null(Register r, Register tmp = t0);
  void decode_klass_not_null(Register r, Register tmp = t0);
  void encode_klass_not_null(Register dst, Register src, Register tmp);
  void decode_klass_not_null(Register dst, Register src, Register tmp);
  void decode_heap_oop_not_null(Register r);
  void decode_heap_oop_not_null(Register dst, Register src);
  void decode_heap_oop(Register d, Register s);
  void decode_heap_oop(Register r) { decode_heap_oop(r, r); }
  void encode_heap_oop_not_null(Register r);
  void encode_heap_oop_not_null(Register dst, Register src);
  void encode_heap_oop(Register d, Register s);
  void encode_heap_oop(Register r) { encode_heap_oop(r, r); };
  void load_heap_oop(Register dst, Address src, Register tmp1,
                     Register tmp2, DecoratorSet decorators = 0);
  void load_heap_oop_not_null(Register dst, Address src, Register tmp1,
                              Register tmp2, DecoratorSet decorators = 0);
  void store_heap_oop(Address dst, Register val, Register tmp1,
                      Register tmp2, Register tmp3, DecoratorSet decorators = 0);

  void store_klass_gap(Register dst, Register src);

  // currently unimplemented
  // Used for storing null. All other oop constants should be
  // stored using routines that take a jobject.
  void store_heap_oop_null(Address dst);

  // This dummy is to prevent a call to store_heap_oop from
  // converting a zero (linked null) into a Register by giving
  // the compiler two choices it can't resolve

  void store_heap_oop(Address dst, void* dummy);

  // Support for null-checks
  //
  // Generates code that causes a null OS exception if the content of reg is null.
  // If the accessed location is M[reg + offset] and the offset is known, provide the
  // offset. No explicit code generateion is needed if the offset is within a certain
  // range (0 <= offset <= page_size).

  virtual void null_check(Register reg, int offset = -1);
  static bool needs_explicit_null_check(intptr_t offset);
  static bool uses_implicit_null_check(void* address);

  // idiv variant which deals with MINLONG as dividend and -1 as divisor
  int corrected_idivl(Register result, Register rs1, Register rs2,
                      bool want_remainder, bool is_signed);
  int corrected_idivq(Register result, Register rs1, Register rs2,
                      bool want_remainder, bool is_signed);

  // interface method calling
  void lookup_interface_method(Register recv_klass,
                               Register intf_klass,
                               RegisterOrConstant itable_index,
                               Register method_result,
                               Register scan_tmp,
                               Label& no_such_interface,
                               bool return_method = true);

  void lookup_interface_method_stub(Register recv_klass,
                                    Register holder_klass,
                                    Register resolved_klass,
                                    Register method_result,
                                    Register temp_reg,
                                    Register temp_reg2,
                                    int itable_index,
                                    Label& L_no_such_interface);

  // virtual method calling
  // n.n. x86 allows RegisterOrConstant for vtable_index
  void lookup_virtual_method(Register recv_klass,
                             RegisterOrConstant vtable_index,
                             Register method_result);

  // Form an address from base + offset in Rd. Rd my or may not
  // actually be used: you must use the Address that is returned. It
  // is up to you to ensure that the shift provided matches the size
  // of your data.
  Address form_address(Register Rd, Register base, int64_t byte_offset);

  // Sometimes we get misaligned loads and stores, usually from Unsafe
  // accesses, and these can exceed the offset range.
  Address legitimize_address(Register Rd, const Address &adr) {
    if (adr.getMode() == Address::base_plus_offset) {
      if (!is_simm12(adr.offset())) {
        return form_address(Rd, adr.base(), adr.offset());
      }
    }
    return adr;
  }

  // allocation
  void tlab_allocate(
    Register obj,                   // result: pointer to object after successful allocation
    Register var_size_in_bytes,     // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,     // object size in bytes if   known at compile time
    Register tmp1,                  // temp register
    Register tmp2,                  // temp register
    Label&   slow_case,             // continuation point of fast allocation fails
    bool     is_far = false
  );

  // Test sub_klass against super_klass, with fast and slow paths.

  // The fast path produces a tri-state answer: yes / no / maybe-slow.
  // One of the three labels can be null, meaning take the fall-through.
  // If super_check_offset is -1, the value is loaded up from super_klass.
  // No registers are killed, except tmp_reg
  void check_klass_subtype_fast_path(Register sub_klass,
                                     Register super_klass,
                                     Register tmp_reg,
                                     Label* L_success,
                                     Label* L_failure,
                                     Label* L_slow_path,
                                     Register super_check_offset = noreg);

  // The reset of the type check; must be wired to a corresponding fast path.
  // It does not repeat the fast path logic, so don't use it standalone.
  // The tmp1_reg and tmp2_reg can be noreg, if no temps are available.
  // Updates the sub's secondary super cache as necessary.
  void check_klass_subtype_slow_path(Register sub_klass,
                                     Register super_klass,
                                     Register tmp1_reg,
                                     Register tmp2_reg,
                                     Label* L_success,
                                     Label* L_failure);

  void population_count(Register dst, Register src, Register tmp1, Register tmp2);

  // As above, but with a constant super_klass.
  // The result is in Register result, not the condition codes.
  bool lookup_secondary_supers_table(Register r_sub_klass,
                                     Register r_super_klass,
                                     Register result,
                                     Register tmp1,
                                     Register tmp2,
                                     Register tmp3,
                                     Register tmp4,
                                     u1 super_klass_slot,
                                     bool stub_is_near = false);

  void verify_secondary_supers_table(Register r_sub_klass,
                                     Register r_super_klass,
                                     Register result,
                                     Register tmp1,
                                     Register tmp2,
                                     Register tmp3);

  void lookup_secondary_supers_table_slow_path(Register r_super_klass,
                                               Register r_array_base,
                                               Register r_array_index,
                                               Register r_bitmap,
                                               Register result,
                                               Register tmp1);

  void check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register tmp_reg,
                           Label& L_success);

  Address argument_address(RegisterOrConstant arg_slot, int extra_slot_offset = 0);

  // only if +VerifyOops
  void _verify_oop(Register reg, const char* s, const char* file, int line);
  void _verify_oop_addr(Address addr, const char* s, const char* file, int line);

  void _verify_oop_checked(Register reg, const char* s, const char* file, int line) {
    if (VerifyOops) {
      _verify_oop(reg, s, file, line);
    }
  }
  void _verify_oop_addr_checked(Address reg, const char* s, const char* file, int line) {
    if (VerifyOops) {
      _verify_oop_addr(reg, s, file, line);
    }
  }

  void _verify_method_ptr(Register reg, const char* msg, const char* file, int line) {}
  void _verify_klass_ptr(Register reg, const char* msg, const char* file, int line) {}

#define verify_oop(reg) _verify_oop_checked(reg, "broken oop " #reg, __FILE__, __LINE__)
#define verify_oop_msg(reg, msg) _verify_oop_checked(reg, "broken oop " #reg ", " #msg, __FILE__, __LINE__)
#define verify_oop_addr(addr) _verify_oop_addr_checked(addr, "broken oop addr " #addr, __FILE__, __LINE__)
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

  static void membar_mask_to_pred_succ(uint32_t order_constraint,
                                       uint32_t& predecessor, uint32_t& successor) {
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

  void fence(uint32_t predecessor, uint32_t successor) {
    if (UseZtso) {
      if ((pred_succ_to_membar_mask(predecessor, successor) & StoreLoad) == StoreLoad) {
        // TSO allows for stores to be reordered after loads. When the compiler
        // generates a fence to disallow that, we are required to generate the
        // fence for correctness.
        Assembler::fence(predecessor, successor);
      } else {
        // TSO guarantees other fences already.
      }
    } else {
      // always generate fence for RVWMO
      Assembler::fence(predecessor, successor);
    }
  }

  void cmodx_fence();

  void pause() {
    Assembler::fence(w, 0);
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
  static void pd_patch_instruction(address branch, address target, const char* file = nullptr, int line = 0) {
    pd_patch_instruction_size(branch, target);
  }
  static address pd_call_destination(address branch) {
    return target_addr_for_insn(branch);
  }

  static int patch_oop(address insn_addr, address o);

  static address get_target_of_li32(address insn_addr);
  static int patch_imm_in_li32(address branch, int32_t target);

  // Return whether code is emitted to a scratch blob.
  virtual bool in_scratch_emit_size() {
    return false;
  }

  address emit_reloc_call_address_stub(int insts_call_instruction_offset, address target);
  static int max_reloc_call_address_stub_size();

  void emit_static_call_stub();
  static int static_call_stub_size();

  // The following 4 methods return the offset of the appropriate move instruction

  // Support for fast byte/short loading with zero extension (depending on particular CPU)
  int load_unsigned_byte(Register dst, Address src);
  int load_unsigned_short(Register dst, Address src);

  // Support for fast byte/short loading with sign extension (depending on particular CPU)
  int load_signed_byte(Register dst, Address src);
  int load_signed_short(Register dst, Address src);

  // Load and store values by size and signed-ness
  void load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed);
  void store_sized_value(Address dst, Register src, size_t size_in_bytes);

  // Misaligned loads, will use the best way, according to the AvoidUnalignedAccess flag
  void load_short_misaligned(Register dst, Address src, Register tmp, bool is_signed, int granularity = 1);
  void load_int_misaligned(Register dst, Address src, Register tmp, bool is_signed, int granularity = 1);
  void load_long_misaligned(Register dst, Address src, Register tmp, int granularity = 1);

 public:
  // Standard pseudo instructions
  inline void nop() {
    addi(x0, x0, 0);
  }

  inline void mv(Register Rd, Register Rs) {
    if (Rd != Rs) {
      addi(Rd, Rs, 0);
    }
  }

  inline void notr(Register Rd, Register Rs) {
    if (do_compress_zcb(Rd, Rs) && (Rd == Rs)) {
      c_not(Rd);
    } else {
      xori(Rd, Rs, -1);
    }
  }

  inline void neg(Register Rd, Register Rs) {
    sub(Rd, x0, Rs);
  }

  inline void negw(Register Rd, Register Rs) {
    subw(Rd, x0, Rs);
  }

  inline void sext_w(Register Rd, Register Rs) {
    addiw(Rd, Rs, 0);
  }

  inline void zext_b(Register Rd, Register Rs) {
    if (do_compress_zcb(Rd, Rs) && (Rd == Rs)) {
      c_zext_b(Rd);
    } else {
      andi(Rd, Rs, 0xFF);
    }
  }

  inline void seqz(Register Rd, Register Rs) {
    sltiu(Rd, Rs, 1);
  }

  inline void snez(Register Rd, Register Rs) {
    sltu(Rd, x0, Rs);
  }

  inline void sltz(Register Rd, Register Rs) {
    slt(Rd, Rs, x0);
  }

  inline void sgtz(Register Rd, Register Rs) {
    slt(Rd, x0, Rs);
  }

  // Bit-manipulation extension pseudo instructions
  // zero extend word
  inline void zext_w(Register Rd, Register Rs) {
    assert(UseZba, "must be");
    if (do_compress_zcb(Rd, Rs) && (Rd == Rs)) {
      c_zext_w(Rd);
    } else {
      add_uw(Rd, Rs, zr);
    }
  }

  // Floating-point data-processing pseudo instructions
  inline void fmv_s(FloatRegister Rd, FloatRegister Rs) {
    if (Rd != Rs) {
      fsgnj_s(Rd, Rs, Rs);
    }
  }

  inline void fabs_s(FloatRegister Rd, FloatRegister Rs) {
    fsgnjx_s(Rd, Rs, Rs);
  }

  inline void fneg_s(FloatRegister Rd, FloatRegister Rs) {
    fsgnjn_s(Rd, Rs, Rs);
  }

  inline void fmv_d(FloatRegister Rd, FloatRegister Rs) {
    if (Rd != Rs) {
      fsgnj_d(Rd, Rs, Rs);
    }
  }

  inline void fabs_d(FloatRegister Rd, FloatRegister Rs) {
    fsgnjx_d(Rd, Rs, Rs);
  }

  inline void fneg_d(FloatRegister Rd, FloatRegister Rs) {
    fsgnjn_d(Rd, Rs, Rs);
  }

  // Control and status pseudo instructions
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

  // Restore cpu control state after JNI call
  void restore_cpu_control_state_after_jni(Register tmp);

  // Control transfer pseudo instructions
  void beqz(Register Rs, const address dest);
  void bnez(Register Rs, const address dest);
  void blez(Register Rs, const address dest);
  void bgez(Register Rs, const address dest);
  void bltz(Register Rs, const address dest);
  void bgtz(Register Rs, const address dest);

  void cmov_eq(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_ne(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_le(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_leu(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_ge(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_geu(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_lt(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_ltu(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_gt(Register cmp1, Register cmp2, Register dst, Register src);
  void cmov_gtu(Register cmp1, Register cmp2, Register dst, Register src);

 public:
  // We try to follow risc-v asm menomics.
  // But as we don't layout a reachable GOT,
  // we often need to resort to movptr, li <48imm>.
  // https://github.com/riscv-non-isa/riscv-asm-manual/blob/master/riscv-asm.md

  // Hotspot only use the standard calling convention using x1/ra.
  // The alternative calling convection using x5/t0 is not used.
  // Using x5 as a temp causes the CPU to mispredict returns.

  // JALR, return address stack updates:
  // | rd is x1/x5 | rs1 is x1/x5 | rd=rs1 | RAS action
  // | ----------- | ------------ | ------ |-------------
  // |     No      |      No      |   —    | None
  // |     No      |      Yes     |   —    | Pop
  // |     Yes     |      No      |   —    | Push
  // |     Yes     |      Yes     |   No   | Pop, then push
  // |     Yes     |      Yes     |   Yes  | Push
  //
  // JAL, return address stack updates:
  // | rd is x1/x5 | RAS action
  // | ----------- | ----------
  // |     Yes     | Push
  // |     No      | None
  //
  // JUMPs   uses Rd = x0/zero and Rs = x6/t1 or imm
  // CALLS   uses Rd = x1/ra   and Rs = x6/t1 or imm (or x1/ra*)
  // RETURNS uses Rd = x0/zero and Rs = x1/ra
  // *use of x1/ra should not normally be used, special case only.

  // jump: jal x0, offset
  // For long reach uses temp register for:
  // la + jr
  void j(const address dest, Register temp = t1);
  void j(const Address &dest, Register temp = t1);
  void j(Label &l, Register temp = noreg);

  // jump register: jalr x0, offset(rs)
  void jr(Register Rd, int32_t offset = 0);

  // call: la + jalr x1
  void call(const address dest, Register temp = t1);

  // jalr: jalr x1, offset(rs)
  void jalr(Register Rs, int32_t offset = 0);

  // Emit a runtime call. Only invalidates the tmp register which
  // is used to keep the entry address for jalr/movptr.
  // Uses call() for intra code cache, else movptr + jalr.
  // Clobebrs t1
  void rt_call(address dest, Register tmp = t1);

  // ret: jalr x0, 0(x1)
  inline void ret() {
    Assembler::jalr(x0, x1, 0);
  }

  //label
  void beqz(Register Rs, Label &l, bool is_far = false);
  void bnez(Register Rs, Label &l, bool is_far = false);
  void blez(Register Rs, Label &l, bool is_far = false);
  void bgez(Register Rs, Label &l, bool is_far = false);
  void bltz(Register Rs, Label &l, bool is_far = false);
  void bgtz(Register Rs, Label &l, bool is_far = false);

  void beq (Register Rs1, Register Rs2, Label &L, bool is_far = false);
  void bne (Register Rs1, Register Rs2, Label &L, bool is_far = false);
  void blt (Register Rs1, Register Rs2, Label &L, bool is_far = false);
  void bge (Register Rs1, Register Rs2, Label &L, bool is_far = false);
  void bltu(Register Rs1, Register Rs2, Label &L, bool is_far = false);
  void bgeu(Register Rs1, Register Rs2, Label &L, bool is_far = false);

  void bgt (Register Rs, Register Rt, const address dest);
  void ble (Register Rs, Register Rt, const address dest);
  void bgtu(Register Rs, Register Rt, const address dest);
  void bleu(Register Rs, Register Rt, const address dest);

  void bgt (Register Rs, Register Rt, Label &l, bool is_far = false);
  void ble (Register Rs, Register Rt, Label &l, bool is_far = false);
  void bgtu(Register Rs, Register Rt, Label &l, bool is_far = false);
  void bleu(Register Rs, Register Rt, Label &l, bool is_far = false);

#define INSN_ENTRY_RELOC(result_type, header)                               \
  result_type header {                                                      \
    guarantee(rtype == relocInfo::internal_word_type,                       \
              "only internal_word_type relocs make sense here");            \
    relocate(InternalAddress(dest).rspec());                                \
    IncompressibleRegion ir(this);  /* relocations */

#define INSN(NAME)                                                                                       \
  void NAME(Register Rs1, Register Rs2, const address dest) {                                            \
    assert_cond(dest != nullptr);                                                                        \
    int64_t offset = dest - pc();                                                                        \
    guarantee(is_simm13(offset) && is_even(offset),                                                      \
              "offset is invalid: is_simm_13: %s offset: " INT64_FORMAT,                                 \
              BOOL_TO_STR(is_simm13(offset)), offset);                                                   \
    Assembler::NAME(Rs1, Rs2, offset);                                                                   \
  }                                                                                                      \
  INSN_ENTRY_RELOC(void, NAME(Register Rs1, Register Rs2, address dest, relocInfo::relocType rtype))     \
    NAME(Rs1, Rs2, dest);                                                                                \
  }

  INSN(beq);
  INSN(bne);
  INSN(bge);
  INSN(bgeu);
  INSN(blt);
  INSN(bltu);

#undef INSN

#undef INSN_ENTRY_RELOC

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

private:
  int push_reg(unsigned int bitset, Register stack);
  int pop_reg(unsigned int bitset, Register stack);
  int push_fp(unsigned int bitset, Register stack);
  int pop_fp(unsigned int bitset, Register stack);
#ifdef COMPILER2
  int push_v(unsigned int bitset, Register stack);
  int pop_v(unsigned int bitset, Register stack);
#endif // COMPILER2

  // The signed 20-bit upper imm can materialize at most negative 0xF...F80000000, two G.
  // The following signed 12-bit imm can at max subtract 0x800, two K, from that previously loaded two G.
  bool is_valid_32bit_offset(int64_t x) {
    constexpr int64_t twoG = (2 * G);
    constexpr int64_t twoK = (2 * K);
    return x < (twoG - twoK) && x >= (-twoG - twoK);
  }

  // Ensure that the auipc can reach the destination at x from anywhere within
  // the code cache so that if it is relocated we know it will still reach.
  bool is_32bit_offset_from_codecache(int64_t x) {
    int64_t low  = (int64_t)CodeCache::low_bound();
    int64_t high = (int64_t)CodeCache::high_bound();
    return is_valid_32bit_offset(x - low) && is_valid_32bit_offset(x - high);
  }

public:
  void push_reg(Register Rs);
  void pop_reg(Register Rd);
  void push_reg(RegSet regs, Register stack) { if (regs.bits()) push_reg(regs.bits(), stack); }
  void pop_reg(RegSet regs, Register stack)  { if (regs.bits()) pop_reg(regs.bits(), stack); }
  void push_fp(FloatRegSet regs, Register stack) { if (regs.bits()) push_fp(regs.bits(), stack); }
  void pop_fp(FloatRegSet regs, Register stack)  { if (regs.bits()) pop_fp(regs.bits(), stack); }
#ifdef COMPILER2
  void push_v(VectorRegSet regs, Register stack) { if (regs.bits()) push_v(regs.bits(), stack); }
  void pop_v(VectorRegSet regs, Register stack)  { if (regs.bits()) pop_v(regs.bits(), stack); }
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

  void push_CPU_state(bool save_vectors = false, int vector_size_in_bytes = 0);
  void pop_CPU_state(bool restore_vectors = false, int vector_size_in_bytes = 0);

  void push_cont_fastpath(Register java_thread = xthread);
  void pop_cont_fastpath(Register java_thread = xthread);

  void inc_held_monitor_count(Register tmp);
  void dec_held_monitor_count(Register tmp);

  // if heap base register is used - reinit it with the correct value
  void reinit_heapbase();

  void bind(Label& L) {
    Assembler::bind(L);
    // fences across basic blocks should not be merged
    code()->clear_last_insn();
  }

  typedef void (MacroAssembler::* compare_and_branch_insn)(Register Rs1, Register Rs2, const address dest);
  typedef void (MacroAssembler::* compare_and_branch_label_insn)(Register Rs1, Register Rs2, Label &L, bool is_far);
  typedef void (MacroAssembler::* jal_jalr_insn)(Register Rt, address dest);

  void wrap_label(Register r, Label &L, jal_jalr_insn insn);
  void wrap_label(Register r1, Register r2, Label &L,
                  compare_and_branch_insn insn,
                  compare_and_branch_label_insn neg_insn, bool is_far = false);

  void la(Register Rd, Label &label);
  void la(Register Rd, const address addr);
  void la(Register Rd, const address addr, int32_t &offset);
  void la(Register Rd, const Address &adr);

  void li16u(Register Rd, uint16_t imm);
  void li32(Register Rd, int32_t imm);
  void li  (Register Rd, int64_t imm);  // optimized load immediate

  // mv
  void mv(Register Rd, address addr)                  { li(Rd, (int64_t)addr); }
  void mv(Register Rd, address addr, int32_t &offset) {
    // Split address into a lower 12-bit sign-extended offset and the remainder,
    // so that the offset could be encoded in jalr or load/store instruction.
    offset = ((int32_t)(int64_t)addr << 20) >> 20;
    li(Rd, (int64_t)addr - offset);
  }

  template<typename T, ENABLE_IF(std::is_integral<T>::value)>
  inline void mv(Register Rd, T o)                    { li(Rd, (int64_t)o); }

  void mv(Register Rd, RegisterOrConstant src) {
    if (src.is_register()) {
      mv(Rd, src.as_register());
    } else {
      mv(Rd, src.as_constant());
    }
  }

  // Generates a load of a 48-bit constant which can be
  // patched to any 48-bit constant, i.e. address.
  // If common case supply additional temp register
  // to shorten the instruction sequence.
  void movptr(Register Rd, const Address &addr, Register tmp = noreg);
  void movptr(Register Rd, address addr, Register tmp = noreg);
  void movptr(Register Rd, address addr, int32_t &offset, Register tmp = noreg);

 private:
  void movptr1(Register Rd, uintptr_t addr, int32_t &offset);
  void movptr2(Register Rd, uintptr_t addr, int32_t &offset, Register tmp);
 public:

  // arith
  void add (Register Rd, Register Rn, int64_t increment, Register temp = t0);
  void addw(Register Rd, Register Rn, int32_t increment, Register temp = t0);
  void sub (Register Rd, Register Rn, int64_t decrement, Register temp = t0);
  void subw(Register Rd, Register Rn, int32_t decrement, Register temp = t0);

#define INSN(NAME)                                               \
  inline void NAME(Register Rd, Register Rs1, Register Rs2) {    \
    Assembler::NAME(Rd, Rs1, Rs2);                               \
  }

  INSN(add);
  INSN(addw);
  INSN(sub);
  INSN(subw);

#undef INSN

  // logic
  void andrw(Register Rd, Register Rs1, Register Rs2);
  void orrw(Register Rd, Register Rs1, Register Rs2);
  void xorrw(Register Rd, Register Rs1, Register Rs2);

  // logic with negate
  void andn(Register Rd, Register Rs1, Register Rs2);
  void orn(Register Rd, Register Rs1, Register Rs2);

  // reverse bytes
  void revbw(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2= t1);  // reverse bytes in lower word, sign-extend
  void revb(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);  // reverse bytes in doubleword

  void ror_imm(Register dst, Register src, uint32_t shift, Register tmp = t0);
  void rolw_imm(Register dst, Register src, uint32_t, Register tmp = t0);
  void andi(Register Rd, Register Rn, int64_t imm, Register tmp = t0);
  void orptr(Address adr, RegisterOrConstant src, Register tmp1 = t0, Register tmp2 = t1);

// Load and Store Instructions
#define INSN_ENTRY_RELOC(result_type, header)                               \
  result_type header {                                                      \
    guarantee(rtype == relocInfo::internal_word_type,                       \
              "only internal_word_type relocs make sense here");            \
    relocate(InternalAddress(dest).rspec());                                \
    IncompressibleRegion ir(this);  /* relocations */

#define INSN(NAME)                                                                                 \
  void NAME(Register Rd, address dest) {                                                           \
    assert_cond(dest != nullptr);                                                                  \
    if (CodeCache::contains(dest)) {                                                               \
      int64_t distance = dest - pc();                                                              \
      assert(is_valid_32bit_offset(distance), "Must be");                                          \
      auipc(Rd, (int32_t)distance + 0x800);                                                        \
      Assembler::NAME(Rd, Rd, ((int32_t)distance << 20) >> 20);                                    \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr(Rd, dest, offset);                                                                    \
      Assembler::NAME(Rd, Rd, offset);                                                             \
    }                                                                                              \
  }                                                                                                \
  INSN_ENTRY_RELOC(void, NAME(Register Rd, address dest, relocInfo::relocType rtype))              \
    NAME(Rd, dest);                                                                                \
  }                                                                                                \
  void NAME(Register Rd, const Address &adr, Register temp = t0) {                                 \
    switch (adr.getMode()) {                                                                       \
      case Address::literal: {                                                                     \
        relocate(adr.rspec(), [&] {                                                                \
          NAME(Rd, adr.target());                                                                  \
        });                                                                                        \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset: {                                                            \
        if (is_simm12(adr.offset())) {                                                             \
          Assembler::NAME(Rd, adr.base(), adr.offset());                                           \
        } else {                                                                                   \
          int32_t offset = ((int32_t)adr.offset() << 20) >> 20;                                    \
          if (Rd == adr.base()) {                                                                  \
            la(temp, Address(adr.base(), adr.offset() - offset));                                  \
            Assembler::NAME(Rd, temp, offset);                                                     \
          } else {                                                                                 \
            la(Rd, Address(adr.base(), adr.offset() - offset));                                    \
            Assembler::NAME(Rd, Rd, offset);                                                       \
          }                                                                                        \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }                                                                                                \
  void NAME(Register Rd, Label &L) {                                                               \
    wrap_label(Rd, L, &MacroAssembler::NAME);                                                      \
  }

  INSN(lb);
  INSN(lbu);
  INSN(lh);
  INSN(lhu);
  INSN(lw);
  INSN(lwu);
  INSN(ld);

#undef INSN

#define INSN(NAME)                                                                                 \
  void NAME(FloatRegister Rd, address dest, Register temp = t0) {                                  \
    assert_cond(dest != nullptr);                                                                  \
    if (CodeCache::contains(dest)) {                                                               \
      int64_t distance = dest - pc();                                                              \
      assert(is_valid_32bit_offset(distance), "Must be");                                          \
      auipc(temp, (int32_t)distance + 0x800);                                                      \
      Assembler::NAME(Rd, temp, ((int32_t)distance << 20) >> 20);                                  \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr(temp, dest, offset);                                                                  \
      Assembler::NAME(Rd, temp, offset);                                                           \
    }                                                                                              \
  }                                                                                                \
  INSN_ENTRY_RELOC(void, NAME(FloatRegister Rd, address dest,                                      \
                              relocInfo::relocType rtype, Register temp = t0))                     \
    NAME(Rd, dest, temp);                                                                          \
  }                                                                                                \
  void NAME(FloatRegister Rd, const Address &adr, Register temp = t0) {                            \
    switch (adr.getMode()) {                                                                       \
      case Address::literal: {                                                                     \
        relocate(adr.rspec(), [&] {                                                                \
          NAME(Rd, adr.target(), temp);                                                            \
        });                                                                                        \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset: {                                                            \
        if (is_simm12(adr.offset())) {                                                             \
          Assembler::NAME(Rd, adr.base(), adr.offset());                                           \
        } else {                                                                                   \
          int32_t offset = ((int32_t)adr.offset() << 20) >> 20;                                    \
          la(temp, Address(adr.base(), adr.offset() - offset));                                    \
          Assembler::NAME(Rd, temp, offset);                                                       \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }

  INSN(flw);
  INSN(fld);

#undef INSN

#define INSN(NAME, REGISTER)                                                                       \
  INSN_ENTRY_RELOC(void, NAME(REGISTER Rs, address dest,                                           \
                              relocInfo::relocType rtype, Register temp = t0))                     \
    NAME(Rs, dest, temp);                                                                          \
  }

  INSN(sb,  Register);
  INSN(sh,  Register);
  INSN(sw,  Register);
  INSN(sd,  Register);
  INSN(fsw, FloatRegister);
  INSN(fsd, FloatRegister);

#undef INSN

#define INSN(NAME)                                                                                 \
  void NAME(Register Rs, address dest, Register temp = t0) {                                       \
    assert_cond(dest != nullptr);                                                                  \
    assert_different_registers(Rs, temp);                                                          \
    if (CodeCache::contains(dest)) {                                                               \
      int64_t distance = dest - pc();                                                              \
      assert(is_valid_32bit_offset(distance), "Must be");                                          \
      auipc(temp, (int32_t)distance + 0x800);                                                      \
      Assembler::NAME(Rs, temp, ((int32_t)distance << 20) >> 20);                                  \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr(temp, dest, offset);                                                                  \
      Assembler::NAME(Rs, temp, offset);                                                           \
    }                                                                                              \
  }                                                                                                \
  void NAME(Register Rs, const Address &adr, Register temp = t0) {                                 \
    switch (adr.getMode()) {                                                                       \
      case Address::literal: {                                                                     \
        assert_different_registers(Rs, temp);                                                      \
        relocate(adr.rspec(), [&] {                                                                \
          NAME(Rs, adr.target(), temp);                                                            \
        });                                                                                        \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset: {                                                            \
        if (is_simm12(adr.offset())) {                                                             \
          Assembler::NAME(Rs, adr.base(), adr.offset());                                           \
        } else {                                                                                   \
          assert_different_registers(Rs, temp);                                                    \
          int32_t offset = ((int32_t)adr.offset() << 20) >> 20;                                    \
          la(temp, Address(adr.base(), adr.offset() - offset));                                    \
          Assembler::NAME(Rs, temp, offset);                                                       \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }

  INSN(sb);
  INSN(sh);
  INSN(sw);
  INSN(sd);

#undef INSN

#define INSN(NAME)                                                                                 \
  void NAME(FloatRegister Rs, address dest, Register temp = t0) {                                  \
    assert_cond(dest != nullptr);                                                                  \
    if (CodeCache::contains(dest)) {                                                               \
      int64_t distance = dest - pc();                                                              \
      assert(is_valid_32bit_offset(distance), "Must be");                                          \
      auipc(temp, (int32_t)distance + 0x800);                                                      \
      Assembler::NAME(Rs, temp, ((int32_t)distance << 20) >> 20);                                  \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr(temp, dest, offset);                                                                  \
      Assembler::NAME(Rs, temp, offset);                                                           \
    }                                                                                              \
  }                                                                                                \
  void NAME(FloatRegister Rs, const Address &adr, Register temp = t0) {                            \
    switch (adr.getMode()) {                                                                       \
      case Address::literal: {                                                                     \
        relocate(adr.rspec(), [&] {                                                                \
          NAME(Rs, adr.target(), temp);                                                            \
        });                                                                                        \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset: {                                                            \
        if (is_simm12(adr.offset())) {                                                             \
          Assembler::NAME(Rs, adr.base(), adr.offset());                                           \
        } else {                                                                                   \
          int32_t offset = ((int32_t)adr.offset() << 20) >> 20;                                    \
          la(temp, Address(adr.base(), adr.offset() - offset));                                    \
          Assembler::NAME(Rs, temp, offset);                                                       \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }

  INSN(fsw);
  INSN(fsd);

#undef INSN

#undef INSN_ENTRY_RELOC

  void cmpxchg_obj_header(Register oldv, Register newv, Register obj, Register tmp, Label &succeed, Label *fail);
  void cmpxchgptr(Register oldv, Register newv, Register addr, Register tmp, Label &succeed, Label *fail);
  void cmpxchg(Register addr, Register expected,
               Register new_val,
               enum operand_size size,
               Assembler::Aqrl acquire, Assembler::Aqrl release,
               Register result, bool result_as_bool = false);
  void weak_cmpxchg(Register addr, Register expected,
                    Register new_val,
                    enum operand_size size,
                    Assembler::Aqrl acquire, Assembler::Aqrl release,
                    Register result);
  void cmpxchg_narrow_value_helper(Register addr, Register expected, Register new_val,
                                   enum operand_size size,
                                   Register shift, Register mask, Register aligned_addr);
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

  void atomic_cas(Register prev, Register newv, Register addr, enum operand_size size,
              Assembler::Aqrl acquire = Assembler::relaxed, Assembler::Aqrl release = Assembler::relaxed);

  // Emit a far call/jump. Only invalidates the tmp register which
  // is used to keep the entry address for jalr.
  // The address must be inside the code cache.
  // Supported entry.rspec():
  // - relocInfo::external_word_type
  // - relocInfo::runtime_call_type
  // - relocInfo::none
  // Clobbers t1 default.
  void far_call(const Address &entry, Register tmp = t1);
  void far_jump(const Address &entry, Register tmp = t1);

  static int far_branch_size() {
      return 2 * 4;  // auipc + jalr, see far_call() & far_jump()
  }

  void load_byte_map_base(Register reg);

  void bang_stack_with_offset(int offset) {
    // stack grows down, caller passes positive offset
    assert(offset > 0, "must bang with negative offset");
    sub(t0, sp, offset);
    sd(zr, Address(t0));
  }

  virtual void _call_Unimplemented(address call_site) {
    mv(t1, call_site);
  }

  #define call_Unimplemented() _call_Unimplemented((address)__PRETTY_FUNCTION__)

  // Frame creation and destruction shared between JITs.
  void build_frame(int framesize);
  void remove_frame(int framesize);

  void reserved_stack_check();

  void get_polling_page(Register dest, relocInfo::relocType rtype);
  void read_polling_page(Register r, int32_t offset, relocInfo::relocType rtype);

  // RISCV64 OpenJDK uses three different types of calls:
  //
  //   - far call: auipc reg, pc_relative_offset; jalr ra, reg, offset
  //     The offset has the range [-(2G + 2K), 2G - 2K). Addresses out of the
  //     range in the code cache requires indirect call.
  //     If a jump is needed rather than a call, a far jump 'jalr x0, reg, offset'
  //     can be used instead.
  //     All instructions are embedded at a call site.
  //
  //   - indirect call: movptr + jalr
  //     This can reach anywhere in the address space, but it cannot be patched
  //     while code is running, so it must only be modified at a safepoint.
  //     This form of call is most suitable for targets at fixed addresses,
  //     which will never be patched.
  //
  //   - reloc call:
  //     This too can reach anywhere in the address space but is only available
  //     in C1/C2-generated code (nmethod).
  //
  //     [Main code section]
  //       auipc
  //       ld <address_from_stub_section>
  //       jalr
  //
  //     [Stub section]
  //     address stub:
  //       <64-bit destination address>
  //
  //    To change the destination we simply atomically store the new
  //    address in the stub section.
  //    There is a benign race in that the other thread might observe the old
  //    64-bit destination address before it observes the new address. That does
  //    not matter because the destination method has been invalidated, so there
  //    will be a trap at its start.

  // Emit a reloc call and create a stub to hold the entry point address.
  // Supported entry.rspec():
  // - relocInfo::runtime_call_type
  // - relocInfo::opt_virtual_call_type
  // - relocInfo::static_call_type
  // - relocInfo::virtual_call_type
  //
  // Return: the call PC or nullptr if CodeCache is full.
  address reloc_call(Address entry, Register tmp = t1);

  address ic_call(address entry, jint method_index = 0);
  static int ic_check_size();
  int ic_check(int end_alignment = MacroAssembler::instruction_size);

  // Support for memory inc/dec
  // n.b. increment/decrement calls with an Address destination will
  // need to use a scratch register to load the value to be
  // incremented. increment/decrement calls which add or subtract a
  // constant value other than sign-extended 12-bit immediate will need
  // to use a 2nd scratch register to hold the constant. so, an address
  // increment/decrement may trash both t0 and t1.

  void increment(const Address dst, int64_t value = 1, Register tmp1 = t0, Register tmp2 = t1);
  void incrementw(const Address dst, int32_t value = 1, Register tmp1 = t0, Register tmp2 = t1);

  void decrement(const Address dst, int64_t value = 1, Register tmp1 = t0, Register tmp2 = t1);
  void decrementw(const Address dst, int32_t value = 1, Register tmp1 = t0, Register tmp2 = t1);

  void cmpptr(Register src1, const Address &src2, Label& equal, Register tmp = t0);

  void clinit_barrier(Register klass, Register tmp, Label* L_fast_path = nullptr, Label* L_slow_path = nullptr);
  void load_method_holder_cld(Register result, Register method);
  void load_method_holder(Register holder, Register method);

  void compute_index(Register str1, Register trailing_zeros, Register match_mask,
                     Register result, Register char_tmp, Register tmp,
                     bool haystack_isL);
  void compute_match_mask(Register src, Register pattern, Register match_mask,
                          Register mask1, Register mask2);

  // CRC32 code for java.util.zip.CRC32::updateBytes() intrinsic.
  void kernel_crc32(Register crc, Register buf, Register len,
        Register table0, Register table1, Register table2, Register table3,
        Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register tmp6);
  void update_word_crc32(Register crc, Register v, Register tmp1, Register tmp2, Register tmp3,
        Register table0, Register table1, Register table2, Register table3,
        bool upper);
  void update_byte_crc32(Register crc, Register val, Register table);

#ifdef COMPILER2
  void vector_update_crc32(Register crc, Register buf, Register len,
                           Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5,
                           Register table0, Register table3);
  void kernel_crc32_vclmul_fold(Register crc, Register buf, Register len,
              Register table0, Register table1, Register table2, Register table3,
              Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5);
  void crc32_vclmul_fold_to_16_bytes_vectorsize_32(VectorRegister vx, VectorRegister vy, VectorRegister vt,
                            VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4);
  void kernel_crc32_vclmul_fold_vectorsize_32(Register crc, Register buf, Register len,
                                              Register vclmul_table, Register tmp1, Register tmp2);
  void crc32_vclmul_fold_16_bytes_vectorsize_16(VectorRegister vx, VectorRegister vt,
                      VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4,
                      Register buf, Register tmp, const int STEP);
  void crc32_vclmul_fold_16_bytes_vectorsize_16_2(VectorRegister vx, VectorRegister vy, VectorRegister vt,
                      VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4,
                      Register tmp);
  void crc32_vclmul_fold_16_bytes_vectorsize_16_3(VectorRegister vx, VectorRegister vy, VectorRegister vt,
                      VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4,
                      Register tmp);
  void kernel_crc32_vclmul_fold_vectorsize_16(Register crc, Register buf, Register len,
                                              Register vclmul_table, Register tmp1, Register tmp2);

  void mul_add(Register out, Register in, Register offset,
               Register len, Register k, Register tmp);
  void wide_mul(Register prod_lo, Register prod_hi, Register n, Register m);
  void wide_madd(Register sum_lo, Register sum_hi, Register n,
                 Register m, Register tmp1, Register tmp2);
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
                       Register z, Register tmp0,
                       Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                       Register tmp5, Register tmp6, Register product_hi);

#endif // COMPILER2

  void inflate_lo32(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);
  void inflate_hi32(Register Rd, Register Rs, Register tmp1 = t0, Register tmp2 = t1);

  void ctzc_bit(Register Rd, Register Rs, bool isLL = false, Register tmp1 = t0, Register tmp2 = t1);

  void zero_words(Register base, uint64_t cnt);
  address zero_words(Register ptr, Register cnt);
  void fill_words(Register base, Register cnt, Register value);
  void zero_memory(Register addr, Register len, Register tmp);
  void zero_dcache_blocks(Register base, Register cnt, Register tmp1, Register tmp2);

  // shift left by shamt and add
  void shadd(Register Rd, Register Rs1, Register Rs2, Register tmp, int shamt);

  // test single bit in Rs, result is set to Rd
  void test_bit(Register Rd, Register Rs, uint32_t bit_pos);

  // Here the float instructions with safe deal with some exceptions.
  // e.g. convert from NaN, +Inf, -Inf to int, float, double
  // will trigger exception, we need to deal with these situations
  // to get correct results.
  void fcvt_w_s_safe(Register dst, FloatRegister src, Register tmp = t0);
  void fcvt_l_s_safe(Register dst, FloatRegister src, Register tmp = t0);
  void fcvt_w_d_safe(Register dst, FloatRegister src, Register tmp = t0);
  void fcvt_l_d_safe(Register dst, FloatRegister src, Register tmp = t0);

  void java_round_float(Register dst, FloatRegister src, FloatRegister ftmp);
  void java_round_double(Register dst, FloatRegister src, FloatRegister ftmp);

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

  // vector pseudo instructions
  // rotate vector register left with shift bits, 32-bit version
  inline void vrole32_vi(VectorRegister vd, uint32_t shift, VectorRegister tmp_vr) {
    vsrl_vi(tmp_vr, vd, 32 - shift);
    vsll_vi(vd, vd, shift);
    vor_vv(vd, vd, tmp_vr);
  }

  inline void vl1r_v(VectorRegister vd, Register rs) {
    vl1re8_v(vd, rs);
  }

  inline void vmnot_m(VectorRegister vd, VectorRegister vs) {
    vmnand_mm(vd, vs, vs);
  }

  inline void vncvt_x_x_w(VectorRegister vd, VectorRegister vs, VectorMask vm = unmasked) {
    vnsrl_wx(vd, vs, x0, vm);
  }

  inline void vneg_v(VectorRegister vd, VectorRegister vs, VectorMask vm = unmasked) {
    vrsub_vx(vd, vs, x0, vm);
  }

  inline void vfneg_v(VectorRegister vd, VectorRegister vs, VectorMask vm = unmasked) {
    vfsgnjn_vv(vd, vs, vs, vm);
  }

  inline void vfabs_v(VectorRegister vd, VectorRegister vs, VectorMask vm = unmasked) {
    vfsgnjx_vv(vd, vs, vs, vm);
  }

  inline void vmsgt_vv(VectorRegister vd, VectorRegister vs2, VectorRegister vs1, VectorMask vm = unmasked) {
    vmslt_vv(vd, vs1, vs2, vm);
  }

  inline void vmsgtu_vv(VectorRegister vd, VectorRegister vs2, VectorRegister vs1, VectorMask vm = unmasked) {
    vmsltu_vv(vd, vs1, vs2, vm);
  }

  inline void vmsge_vv(VectorRegister vd, VectorRegister vs2, VectorRegister vs1, VectorMask vm = unmasked) {
    vmsle_vv(vd, vs1, vs2, vm);
  }

  inline void vmsgeu_vv(VectorRegister vd, VectorRegister vs2, VectorRegister vs1, VectorMask vm = unmasked) {
    vmsleu_vv(vd, vs1, vs2, vm);
  }

  inline void vmfgt_vv(VectorRegister vd, VectorRegister vs2, VectorRegister vs1, VectorMask vm = unmasked) {
    vmflt_vv(vd, vs1, vs2, vm);
  }

  inline void vmfge_vv(VectorRegister vd, VectorRegister vs2, VectorRegister vs1, VectorMask vm = unmasked) {
    vmfle_vv(vd, vs1, vs2, vm);
  }

  inline void vmsltu_vi(VectorRegister Vd, VectorRegister Vs2, uint32_t imm, VectorMask vm = unmasked) {
    guarantee(imm >= 1 && imm <= 16, "imm is invalid");
    vmsleu_vi(Vd, Vs2, imm-1, vm);
  }

  inline void vmsgeu_vi(VectorRegister Vd, VectorRegister Vs2, uint32_t imm, VectorMask vm = unmasked) {
    guarantee(imm >= 1 && imm <= 16, "imm is invalid");
    vmsgtu_vi(Vd, Vs2, imm-1, vm);
  }

  // Copy mask register
  inline void vmmv_m(VectorRegister vd, VectorRegister vs) {
    vmand_mm(vd, vs, vs);
  }

  // Clear mask register
  inline void vmclr_m(VectorRegister vd) {
    vmxor_mm(vd, vd, vd);
  }

  // Set mask register
  inline void vmset_m(VectorRegister vd) {
    vmxnor_mm(vd, vd, vd);
  }

  inline void vnot_v(VectorRegister Vd, VectorRegister Vs, VectorMask vm = unmasked) {
    vxor_vi(Vd, Vs, -1, vm);
  }

  static const int zero_words_block_size;

  void cast_primitive_type(BasicType type, Register Rt) {
    switch (type) {
      case T_BOOLEAN:
        sltu(Rt, zr, Rt);
        break;
      case T_CHAR   :
        zext(Rt, Rt, 16);
        break;
      case T_BYTE   :
        sext(Rt, Rt, 8);
        break;
      case T_SHORT  :
        sext(Rt, Rt, 16);
        break;
      case T_INT    :
        sext(Rt, Rt, 32);
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
  void zext(Register dst, Register src, int bits);
  void sext(Register dst, Register src, int bits);

private:
  void cmp_x2i(Register dst, Register src1, Register src2, Register tmp, bool is_signed = true);

public:
  // compare src1 and src2 and get -1/0/1 in dst.
  // if [src1 > src2], dst = 1;
  // if [src1 == src2], dst = 0;
  // if [src1 < src2], dst = -1;
  void cmp_l2i(Register dst, Register src1, Register src2, Register tmp = t0);
  void cmp_ul2i(Register dst, Register src1, Register src2, Register tmp = t0);
  void cmp_uw2i(Register dst, Register src1, Register src2, Register tmp = t0);

  // support for argument shuffling
  void move32_64(VMRegPair src, VMRegPair dst, Register tmp = t0);
  void float_move(VMRegPair src, VMRegPair dst, Register tmp = t0);
  void long_move(VMRegPair src, VMRegPair dst, Register tmp = t0);
  void double_move(VMRegPair src, VMRegPair dst, Register tmp = t0);
  void object_move(OopMap* map,
                   int oop_handle_offset,
                   int framesize_in_slots,
                   VMRegPair src,
                   VMRegPair dst,
                   bool is_receiver,
                   int* receiver_offset);

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

private:

  void repne_scan(Register addr, Register value, Register count, Register tmp);

  int bitset_to_regs(unsigned int bitset, unsigned char* regs);
  Address add_memory_helper(const Address dst, Register tmp);

  void load_reserved(Register dst, Register addr, enum operand_size size, Assembler::Aqrl acquire);
  void store_conditional(Register dst, Register new_val, Register addr, enum operand_size size, Assembler::Aqrl release);

public:
  void lightweight_lock(Register basic_lock, Register obj, Register tmp1, Register tmp2, Register tmp3, Label& slow);
  void lightweight_unlock(Register obj, Register tmp1, Register tmp2, Register tmp3, Label& slow);

public:
  enum {
    // movptr
    movptr1_instruction_size = 6 * instruction_size, // lui, addi, slli, addi, slli, addi.  See movptr1().
    movptr2_instruction_size = 5 * instruction_size, // lui, lui, slli, add, addi.  See movptr2().
    load_pc_relative_instruction_size = 2 * instruction_size // auipc, ld
  };

  static bool is_load_pc_relative_at(address branch);
  static bool is_li16u_at(address instr);

  static bool is_jal_at(address instr)        { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b1101111; }
  static bool is_jalr_at(address instr)       { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b1100111 && extract_funct3(instr) == 0b000; }
  static bool is_branch_at(address instr)     { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b1100011; }
  static bool is_ld_at(address instr)         { assert_cond(instr != nullptr); return is_load_at(instr) && extract_funct3(instr) == 0b011; }
  static bool is_load_at(address instr)       { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b0000011; }
  static bool is_float_load_at(address instr) { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b0000111; }
  static bool is_auipc_at(address instr)      { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b0010111; }
  static bool is_jump_at(address instr)       { assert_cond(instr != nullptr); return is_branch_at(instr) || is_jal_at(instr) || is_jalr_at(instr); }
  static bool is_add_at(address instr)        { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b0110011 && extract_funct3(instr) == 0b000; }
  static bool is_addi_at(address instr)       { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b0010011 && extract_funct3(instr) == 0b000; }
  static bool is_addiw_at(address instr)      { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b0011011 && extract_funct3(instr) == 0b000; }
  static bool is_addiw_to_zr_at(address instr){ assert_cond(instr != nullptr); return is_addiw_at(instr) && extract_rd(instr) == zr; }
  static bool is_lui_at(address instr)        { assert_cond(instr != nullptr); return extract_opcode(instr) == 0b0110111; }
  static bool is_lui_to_zr_at(address instr)  { assert_cond(instr != nullptr); return is_lui_at(instr) && extract_rd(instr) == zr; }

  static bool is_srli_at(address instr) {
    assert_cond(instr != nullptr);
    return extract_opcode(instr) == 0b0010011 &&
           extract_funct3(instr) == 0b101 &&
           Assembler::extract(((unsigned*)instr)[0], 31, 26) == 0b000000;
  }

  static bool is_slli_shift_at(address instr, uint32_t shift) {
    assert_cond(instr != nullptr);
    return (extract_opcode(instr) == 0b0010011 && // opcode field
            extract_funct3(instr) == 0b001 &&     // funct3 field, select the type of operation
            Assembler::extract(Assembler::ld_instr(instr), 25, 20) == shift);    // shamt field
  }

  static bool is_movptr1_at(address instr);
  static bool is_movptr2_at(address instr);

  static bool is_lwu_to_zr(address instr);

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
  static bool check_movptr1_data_dependency(address instr) {
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

  // the instruction sequence of movptr2 is as below:
  //     lui
  //     lui
  //     slli
  //     add
  //     addi/jalr/load
  static bool check_movptr2_data_dependency(address instr) {
    address lui1 = instr;
    address lui2 = lui1 + instruction_size;
    address slli = lui2 + instruction_size;
    address add  = slli + instruction_size;
    address last_instr = add + instruction_size;
    return extract_rd(add) == extract_rd(lui2) &&
           extract_rs1(add) == extract_rd(lui2) &&
           extract_rs2(add) == extract_rd(slli) &&
           extract_rs1(slli) == extract_rd(lui1) &&
           extract_rd(slli) == extract_rd(lui1) &&
           extract_rs1(last_instr) == extract_rd(add);
  }

  // the instruction sequence of li16u is as below:
  //     lui
  //     srli
  static bool check_li16u_data_dependency(address instr) {
    address lui = instr;
    address srli = lui + instruction_size;

    return extract_rs1(srli) == extract_rd(lui) &&
           extract_rs1(srli) == extract_rd(srli);
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

  static bool is_li32_at(address instr);
  static bool is_pc_relative_at(address branch);

  static bool is_membar(address addr) {
    return (Bytes::get_native_u4(addr) & 0x7f) == 0b1111 && extract_funct3(addr) == 0;
  }
  static uint32_t get_membar_kind(address addr);
  static void set_membar_kind(address addr, uint32_t order_kind);
};

#ifdef ASSERT
inline bool AbstractAssembler::pd_check_instruction_mark() { return false; }
#endif

#endif // CPU_RISCV_MACROASSEMBLER_RISCV_HPP

/*
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ARM_VM_MACROASSEMBLER_ARM_HPP
#define CPU_ARM_VM_MACROASSEMBLER_ARM_HPP

#include "code/relocInfo.hpp"
#include "code/relocInfo_ext.hpp"

class BiasedLockingCounters;

// Introduced AddressLiteral and its subclasses to ease portability from
// x86 and avoid relocation issues
class AddressLiteral VALUE_OBJ_CLASS_SPEC {
  RelocationHolder _rspec;
  // Typically we use AddressLiterals we want to use their rval
  // However in some situations we want the lval (effect address) of the item.
  // We provide a special factory for making those lvals.
  bool _is_lval;

  address          _target;

 private:
  static relocInfo::relocType reloc_for_target(address target) {
    // Used for ExternalAddress or when the type is not specified
    // Sometimes ExternalAddress is used for values which aren't
    // exactly addresses, like the card table base.
    // external_word_type can't be used for values in the first page
    // so just skip the reloc in that case.
    return external_word_Relocation::can_be_relocated(target) ? relocInfo::external_word_type : relocInfo::none;
  }

  void set_rspec(relocInfo::relocType rtype);

 protected:
  // creation
  AddressLiteral()
    : _is_lval(false),
      _target(NULL)
  {}

  public:

  AddressLiteral(address target, relocInfo::relocType rtype) {
    _is_lval = false;
    _target = target;
    set_rspec(rtype);
  }

  AddressLiteral(address target, RelocationHolder const& rspec)
    : _rspec(rspec),
      _is_lval(false),
      _target(target)
  {}

  AddressLiteral(address target) {
    _is_lval = false;
    _target = target;
    set_rspec(reloc_for_target(target));
  }

  AddressLiteral addr() {
    AddressLiteral ret = *this;
    ret._is_lval = true;
    return ret;
  }

 private:

  address target() { return _target; }
  bool is_lval() { return _is_lval; }

  relocInfo::relocType reloc() const { return _rspec.type(); }
  const RelocationHolder& rspec() const { return _rspec; }

  friend class Assembler;
  friend class MacroAssembler;
  friend class Address;
  friend class LIR_Assembler;
  friend class InlinedAddress;
};

class ExternalAddress: public AddressLiteral {

  public:

  ExternalAddress(address target) : AddressLiteral(target) {}

};

class InternalAddress: public AddressLiteral {

  public:

  InternalAddress(address target) : AddressLiteral(target, relocInfo::internal_word_type) {}

};

// Inlined constants, for use with ldr_literal / bind_literal
// Note: InlinedInteger not supported (use move_slow(Register,int[,cond]))
class InlinedLiteral: StackObj {
 public:
  Label label; // need to be public for direct access with &
  InlinedLiteral() {
  }
};

class InlinedMetadata: public InlinedLiteral {
 private:
  Metadata *_data;

 public:
  InlinedMetadata(Metadata *data): InlinedLiteral() {
    _data = data;
  }
  Metadata *data() { return _data; }
};

// Currently unused
// class InlinedOop: public InlinedLiteral {
//  private:
//   jobject _jobject;
//
//  public:
//   InlinedOop(jobject target): InlinedLiteral() {
//     _jobject = target;
//   }
//   jobject jobject() { return _jobject; }
// };

class InlinedAddress: public InlinedLiteral {
 private:
  AddressLiteral _literal;

 public:

  InlinedAddress(jobject object): InlinedLiteral(), _literal((address)object, relocInfo::oop_type) {
    ShouldNotReachHere(); // use mov_oop (or implement InlinedOop)
  }

  InlinedAddress(Metadata *data): InlinedLiteral(), _literal((address)data, relocInfo::metadata_type) {
    ShouldNotReachHere(); // use InlinedMetadata or mov_metadata
  }

  InlinedAddress(address target, const RelocationHolder &rspec): InlinedLiteral(), _literal(target, rspec) {
    assert(rspec.type() != relocInfo::oop_type, "Do not use InlinedAddress for oops");
    assert(rspec.type() != relocInfo::metadata_type, "Do not use InlinedAddress for metadatas");
  }

  InlinedAddress(address target, relocInfo::relocType rtype): InlinedLiteral(), _literal(target, rtype) {
    assert(rtype != relocInfo::oop_type, "Do not use InlinedAddress for oops");
    assert(rtype != relocInfo::metadata_type, "Do not use InlinedAddress for metadatas");
  }

  // Note: default is relocInfo::none for InlinedAddress
  InlinedAddress(address target): InlinedLiteral(), _literal(target, relocInfo::none) {
  }

  address target() { return _literal.target(); }

  const RelocationHolder& rspec() const { return _literal.rspec(); }
};

class InlinedString: public InlinedLiteral {
 private:
  const char* _msg;

 public:
  InlinedString(const char* msg): InlinedLiteral() {
    _msg = msg;
  }
  const char* msg() { return _msg; }
};

class MacroAssembler: public Assembler {
protected:

  // Support for VM calls
  //

  // This is the base routine called by the different versions of call_VM_leaf.
  void call_VM_leaf_helper(address entry_point, int number_of_arguments);

  // This is the base routine called by the different versions of call_VM. The interpreter
  // may customize this version by overriding it for its purposes (e.g., to save/restore
  // additional registers when doing a VM call).
  virtual void call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions);

  // These routines should emit JVMTI PopFrame and ForceEarlyReturn handling code.
  // The implementation is only non-empty for the InterpreterMacroAssembler,
  // as only the interpreter handles PopFrame and ForceEarlyReturn requests.
  virtual void check_and_handle_popframe() {}
  virtual void check_and_handle_earlyret() {}

public:

  MacroAssembler(CodeBuffer* code) : Assembler(code) {}

  // By default, we do not need relocation information for non
  // patchable absolute addresses. However, when needed by some
  // extensions, ignore_non_patchable_relocations can be modified,
  // returning false to preserve all relocation information.
  inline bool ignore_non_patchable_relocations() { return true; }

  // Initially added to the Assembler interface as a pure virtual:
  //   RegisterConstant delayed_value(..)
  // for:
  //   6812678 macro assembler needs delayed binding of a few constants (for 6655638)
  // this was subsequently modified to its present name and return type
  virtual RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr, Register tmp, int offset);

#ifdef AARCH64
# define NOT_IMPLEMENTED() unimplemented("NYI at " __FILE__ ":" XSTR(__LINE__))
# define NOT_TESTED()      warn("Not tested at " __FILE__ ":" XSTR(__LINE__))
#endif

  void align(int modulus);

  // Support for VM calls
  //
  // It is imperative that all calls into the VM are handled via the call_VM methods.
  // They make sure that the stack linkage is setup correctly. call_VM's correspond
  // to ENTRY/ENTRY_X entry points while call_VM_leaf's correspond to LEAF entry points.

  void call_VM(Register oop_result, address entry_point, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions = true);

  // The following methods are required by templateTable.cpp,
  // but not used on ARM.
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, int number_of_arguments = 0, bool check_exceptions = true);
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions = true);
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, bool check_exceptions = true);
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions = true);

  // Note: The super_call_VM calls are not used on ARM

  // Raw call, without saving/restoring registers, exception handling, etc.
  // Mainly used from various stubs.
  // Note: if 'save_R9_if_scratched' is true, call_VM may on some
  // platforms save values on the stack. Set it to false (and handle
  // R9 in the callers) if the top of the stack must not be modified
  // by call_VM.
  void call_VM(address entry_point, bool save_R9_if_scratched);

  void call_VM_leaf(address entry_point);
  void call_VM_leaf(address entry_point, Register arg_1);
  void call_VM_leaf(address entry_point, Register arg_1, Register arg_2);
  void call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3);
  void call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3, Register arg_4);

  void get_vm_result(Register oop_result, Register tmp);
  void get_vm_result_2(Register metadata_result, Register tmp);

  // Always sets/resets sp, which default to SP if (last_sp == noreg)
  // Optionally sets/resets fp (use noreg to avoid setting it)
  // Always sets/resets pc on AArch64; optionally sets/resets pc on 32-bit ARM depending on save_last_java_pc flag
  // Note: when saving PC, set_last_Java_frame returns PC's offset in the code section
  //       (for oop_maps offset computation)
  int set_last_Java_frame(Register last_sp, Register last_fp, bool save_last_java_pc, Register tmp);
  void reset_last_Java_frame(Register tmp);
  // status set in set_last_Java_frame for reset_last_Java_frame
  bool _fp_saved;
  bool _pc_saved;

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) __ stop(error)
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#define STOP(error) __ block_comment(error); __ stop(error)
#endif

  void lookup_virtual_method(Register recv_klass,
                             Register vtable_index,
                             Register method_result);

  // Test sub_klass against super_klass, with fast and slow paths.

  // The fast path produces a tri-state answer: yes / no / maybe-slow.
  // One of the three labels can be NULL, meaning take the fall-through.
  // No registers are killed, except temp_regs.
  void check_klass_subtype_fast_path(Register sub_klass,
                                     Register super_klass,
                                     Register temp_reg,
                                     Register temp_reg2,
                                     Label* L_success,
                                     Label* L_failure,
                                     Label* L_slow_path);

  // The rest of the type check; must be wired to a corresponding fast path.
  // It does not repeat the fast path logic, so don't use it standalone.
  // temp_reg3 can be noreg, if no temps are available.
  // Updates the sub's secondary super cache as necessary.
  // If set_cond_codes:
  // - condition codes will be Z on success, NZ on failure.
  // - temp_reg will be 0 on success, non-0 on failure
  void check_klass_subtype_slow_path(Register sub_klass,
                                     Register super_klass,
                                     Register temp_reg,
                                     Register temp_reg2,
                                     Register temp_reg3, // auto assigned if noreg
                                     Label* L_success,
                                     Label* L_failure,
                                     bool set_cond_codes = false);

  // Simplified, combined version, good for typical uses.
  // temp_reg3 can be noreg, if no temps are available. It is used only on slow path.
  // Falls through on failure.
  void check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register temp_reg,
                           Register temp_reg2,
                           Register temp_reg3, // auto assigned on slow path if noreg
                           Label& L_success);

  // Returns address of receiver parameter, using tmp as base register. tmp and params_count can be the same.
  Address receiver_argument_address(Register params_base, Register params_count, Register tmp);

  void _verify_oop(Register reg, const char* s, const char* file, int line);
  void _verify_oop_addr(Address addr, const char * s, const char* file, int line);

  // TODO: verify method and klass metadata (compare against vptr?)
  void _verify_method_ptr(Register reg, const char * msg, const char * file, int line) {}
  void _verify_klass_ptr(Register reg, const char * msg, const char * file, int line) {}

#define verify_oop(reg) _verify_oop(reg, "broken oop " #reg, __FILE__, __LINE__)
#define verify_oop_addr(addr) _verify_oop_addr(addr, "broken oop ", __FILE__, __LINE__)
#define verify_method_ptr(reg) _verify_method_ptr(reg, "broken method " #reg, __FILE__, __LINE__)
#define verify_klass_ptr(reg) _verify_klass_ptr(reg, "broken klass " #reg, __FILE__, __LINE__)

  void null_check(Register reg, Register tmp, int offset = -1);
  inline void null_check(Register reg) { null_check(reg, noreg, -1); } // for C1 lir_null_check

  // Puts address of allocated object into register `obj` and end of allocated object into register `obj_end`.
  void eden_allocate(Register obj, Register obj_end, Register tmp1, Register tmp2,
                     RegisterOrConstant size_expression, Label& slow_case);
  void tlab_allocate(Register obj, Register obj_end, Register tmp1,
                     RegisterOrConstant size_expression, Label& slow_case);

  void tlab_refill(Register top, Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                   Label& try_eden, Label& slow_case);
  void zero_memory(Register start, Register end, Register tmp);

  void incr_allocated_bytes(RegisterOrConstant size_in_bytes, Register tmp);

  static bool needs_explicit_null_check(intptr_t offset);

  void arm_stack_overflow_check(int frame_size_in_bytes, Register tmp);
  void arm_stack_overflow_check(Register Rsize, Register tmp);

  void bang_stack_with_offset(int offset) {
    ShouldNotReachHere();
  }

  // Biased locking support
  // lock_reg and obj_reg must be loaded up with the appropriate values.
  // swap_reg must be supplied.
  // tmp_reg must be supplied.
  // Optional slow case is for implementations (interpreter and C1) which branch to
  // slow case directly. If slow_case is NULL, then leaves condition
  // codes set (for C2's Fast_Lock node) and jumps to done label.
  // Falls through for the fast locking attempt.
  // Returns offset of first potentially-faulting instruction for null
  // check info (currently consumed only by C1). If
  // swap_reg_contains_mark is true then returns -1 as it is assumed
  // the calling code has already passed any potential faults.
  // Notes:
  // - swap_reg and tmp_reg are scratched
  // - Rtemp was (implicitly) scratched and can now be specified as the tmp2
  int biased_locking_enter(Register obj_reg, Register swap_reg, Register tmp_reg,
                           bool swap_reg_contains_mark,
                           Register tmp2,
                           Label& done, Label& slow_case,
                           BiasedLockingCounters* counters = NULL);
  void biased_locking_exit(Register obj_reg, Register temp_reg, Label& done);

  // Building block for CAS cases of biased locking: makes CAS and records statistics.
  // Optional slow_case label is used to transfer control if CAS fails. Otherwise leaves condition codes set.
  void biased_locking_enter_with_cas(Register obj_reg, Register old_mark_reg, Register new_mark_reg,
                                     Register tmp, Label& slow_case, int* counter_addr);

  void resolve_jobject(Register value, Register tmp1, Register tmp2);

#if INCLUDE_ALL_GCS
  // G1 pre-barrier.
  // Blows all volatile registers (R0-R3 on 32-bit ARM, R0-R18 on AArch64, Rtemp, LR).
  // If store_addr != noreg, then previous value is loaded from [store_addr];
  // in such case store_addr and new_val registers are preserved;
  // otherwise pre_val register is preserved.
  void g1_write_barrier_pre(Register store_addr,
                            Register new_val,
                            Register pre_val,
                            Register tmp1,
                            Register tmp2);

  // G1 post-barrier.
  // Blows all volatile registers (R0-R3 on 32-bit ARM, R0-R18 on AArch64, Rtemp, LR).
  void g1_write_barrier_post(Register store_addr,
                             Register new_val,
                             Register tmp1,
                             Register tmp2,
                             Register tmp3);
#endif // INCLUDE_ALL_GCS

#ifndef AARCH64
  void nop() {
    mov(R0, R0);
  }

  void push(Register rd, AsmCondition cond = al) {
    assert(rd != SP, "unpredictable instruction");
    str(rd, Address(SP, -wordSize, pre_indexed), cond);
  }

  void push(RegisterSet reg_set, AsmCondition cond = al) {
    assert(!reg_set.contains(SP), "unpredictable instruction");
    stmdb(SP, reg_set, writeback, cond);
  }

  void pop(Register rd, AsmCondition cond = al) {
    assert(rd != SP, "unpredictable instruction");
    ldr(rd, Address(SP, wordSize, post_indexed), cond);
  }

  void pop(RegisterSet reg_set, AsmCondition cond = al) {
    assert(!reg_set.contains(SP), "unpredictable instruction");
    ldmia(SP, reg_set, writeback, cond);
  }

  void fpushd(FloatRegister fd, AsmCondition cond = al) {
    fstmdbd(SP, FloatRegisterSet(fd), writeback, cond);
  }

  void fpushs(FloatRegister fd, AsmCondition cond = al) {
    fstmdbs(SP, FloatRegisterSet(fd), writeback, cond);
  }

  void fpopd(FloatRegister fd, AsmCondition cond = al) {
    fldmiad(SP, FloatRegisterSet(fd), writeback, cond);
  }

  void fpops(FloatRegister fd, AsmCondition cond = al) {
    fldmias(SP, FloatRegisterSet(fd), writeback, cond);
  }
#endif // !AARCH64

  // Order access primitives
  enum Membar_mask_bits {
    StoreStore = 1 << 3,
    LoadStore  = 1 << 2,
    StoreLoad  = 1 << 1,
    LoadLoad   = 1 << 0
  };

#ifdef AARCH64
  // tmp register is not used on AArch64, this parameter is provided solely for better compatibility with 32-bit ARM
  void membar(Membar_mask_bits order_constraint, Register tmp = noreg);
#else
  void membar(Membar_mask_bits mask,
              Register tmp,
              bool preserve_flags = true,
              Register load_tgt = noreg);
#endif

  void breakpoint(AsmCondition cond = al);
  void stop(const char* msg);
  // prints msg and continues
  void warn(const char* msg);
  void unimplemented(const char* what = "");
  void should_not_reach_here()                   { stop("should not reach here"); }
  static void debug(const char* msg, const intx* registers);

  // Create a walkable frame to help tracking down who called this code.
  // Returns the frame size in words.
  int should_not_call_this() {
    raw_push(FP, LR);
    should_not_reach_here();
    flush();
    return 2; // frame_size_in_words (FP+LR)
  }

  int save_all_registers();
  void restore_all_registers();
  int save_caller_save_registers();
  void restore_caller_save_registers();

  void add_rc(Register dst, Register arg1, RegisterOrConstant arg2);

  // add_slow and mov_slow are used to manipulate offsets larger than 1024,
  // these functions are not expected to handle all possible constants,
  // only those that can really occur during compilation
  void add_slow(Register rd, Register rn, int c);
  void sub_slow(Register rd, Register rn, int c);

#ifdef AARCH64
  static int mov_slow_helper(Register rd, intptr_t c, MacroAssembler* masm /* optional */);
#endif

  void mov_slow(Register rd, intptr_t c NOT_AARCH64_ARG(AsmCondition cond = al));
  void mov_slow(Register rd, const char *string);
  void mov_slow(Register rd, address addr);

  void patchable_mov_oop(Register rd, jobject o, int oop_index) {
    mov_oop(rd, o, oop_index AARCH64_ONLY_ARG(true));
  }
  void mov_oop(Register rd, jobject o, int index = 0
               AARCH64_ONLY_ARG(bool patchable = false)
               NOT_AARCH64_ARG(AsmCondition cond = al));


  void patchable_mov_metadata(Register rd, Metadata* o, int index) {
    mov_metadata(rd, o, index AARCH64_ONLY_ARG(true));
  }
  void mov_metadata(Register rd, Metadata* o, int index = 0 AARCH64_ONLY_ARG(bool patchable = false));

  void mov_float(FloatRegister fd, jfloat c NOT_AARCH64_ARG(AsmCondition cond = al));
  void mov_double(FloatRegister fd, jdouble c NOT_AARCH64_ARG(AsmCondition cond = al));

#ifdef AARCH64
  int mov_pc_to(Register rd) {
    Label L;
    adr(rd, L);
    bind(L);
    return offset();
  }
#endif

  // Note: this variant of mov_address assumes the address moves with
  // the code. Do *not* implement it with non-relocated instructions,
  // unless PC-relative.
#ifdef AARCH64
  void mov_relative_address(Register rd, address addr) {
    adr(rd, addr);
  }
#else
  void mov_relative_address(Register rd, address addr, AsmCondition cond = al) {
    int offset = addr - pc() - 8;
    assert((offset & 3) == 0, "bad alignment");
    if (offset >= 0) {
      assert(AsmOperand::is_rotated_imm(offset), "addr too far");
      add(rd, PC, offset, cond);
    } else {
      assert(AsmOperand::is_rotated_imm(-offset), "addr too far");
      sub(rd, PC, -offset, cond);
    }
  }
#endif // AARCH64

  // Runtime address that may vary from one execution to another. The
  // symbolic_reference describes what the address is, allowing
  // the address to be resolved in a different execution context.
  // Warning: do not implement as a PC relative address.
  void mov_address(Register rd, address addr, symbolic_Relocation::symbolic_reference t) {
    mov_address(rd, addr, RelocationHolder::none);
  }

  // rspec can be RelocationHolder::none (for ignored symbolic_Relocation).
  // In that case, the address is absolute and the generated code need
  // not be relocable.
  void mov_address(Register rd, address addr, RelocationHolder const& rspec) {
    assert(rspec.type() != relocInfo::runtime_call_type, "do not use mov_address for runtime calls");
    assert(rspec.type() != relocInfo::static_call_type, "do not use mov_address for relocable calls");
    if (rspec.type() == relocInfo::none) {
      // absolute address, relocation not needed
      mov_slow(rd, (intptr_t)addr);
      return;
    }
#ifndef AARCH64
    if (VM_Version::supports_movw()) {
      relocate(rspec);
      int c = (int)addr;
      movw(rd, c & 0xffff);
      if ((unsigned int)c >> 16) {
        movt(rd, (unsigned int)c >> 16);
      }
      return;
    }
#endif
    Label skip_literal;
    InlinedAddress addr_literal(addr, rspec);
    ldr_literal(rd, addr_literal);
    b(skip_literal);
    bind_literal(addr_literal);
    // AARCH64 WARNING: because of alignment padding, extra padding
    // may be required to get a consistent size for C2, or rules must
    // overestimate size see MachEpilogNode::size
    bind(skip_literal);
  }

  // Note: Do not define mov_address for a Label
  //
  // Load from addresses potentially within the code are now handled
  // InlinedLiteral subclasses (to allow more flexibility on how the
  // ldr_literal is performed).

  void ldr_literal(Register rd, InlinedAddress& L) {
    assert(L.rspec().type() != relocInfo::runtime_call_type, "avoid ldr_literal for calls");
    assert(L.rspec().type() != relocInfo::static_call_type, "avoid ldr_literal for calls");
    relocate(L.rspec());
#ifdef AARCH64
    ldr(rd, target(L.label));
#else
    ldr(rd, Address(PC, target(L.label) - pc() - 8));
#endif
  }

  void ldr_literal(Register rd, InlinedString& L) {
    const char* msg = L.msg();
    if (code()->consts()->contains((address)msg)) {
      // string address moves with the code
#ifdef AARCH64
      ldr(rd, (address)msg);
#else
      ldr(rd, Address(PC, ((address)msg) - pc() - 8));
#endif
      return;
    }
    // Warning: use external strings with care. They are not relocated
    // if the code moves. If needed, use code_string to move them
    // to the consts section.
#ifdef AARCH64
    ldr(rd, target(L.label));
#else
    ldr(rd, Address(PC, target(L.label) - pc() - 8));
#endif
  }

  void ldr_literal(Register rd, InlinedMetadata& L) {
    // relocation done in the bind_literal for metadatas
#ifdef AARCH64
    ldr(rd, target(L.label));
#else
    ldr(rd, Address(PC, target(L.label) - pc() - 8));
#endif
  }

  void bind_literal(InlinedAddress& L) {
    AARCH64_ONLY(align(wordSize));
    bind(L.label);
    assert(L.rspec().type() != relocInfo::metadata_type, "Must use InlinedMetadata");
    // We currently do not use oop 'bound' literals.
    // If the code evolves and the following assert is triggered,
    // we need to implement InlinedOop (see InlinedMetadata).
    assert(L.rspec().type() != relocInfo::oop_type, "Inlined oops not supported");
    // Note: relocation is handled by relocate calls in ldr_literal
    AbstractAssembler::emit_address((address)L.target());
  }

  void bind_literal(InlinedString& L) {
    const char* msg = L.msg();
    if (code()->consts()->contains((address)msg)) {
      // The Label should not be used; avoid binding it
      // to detect errors.
      return;
    }
    AARCH64_ONLY(align(wordSize));
    bind(L.label);
    AbstractAssembler::emit_address((address)L.msg());
  }

  void bind_literal(InlinedMetadata& L) {
    AARCH64_ONLY(align(wordSize));
    bind(L.label);
    relocate(metadata_Relocation::spec_for_immediate());
    AbstractAssembler::emit_address((address)L.data());
  }

  void load_mirror(Register mirror, Register method, Register tmp);

  // Porting layer between 32-bit ARM and AArch64

#define COMMON_INSTR_1(common_mnemonic, aarch64_mnemonic, arm32_mnemonic, arg_type) \
  void common_mnemonic(arg_type arg) { \
      AARCH64_ONLY(aarch64_mnemonic) NOT_AARCH64(arm32_mnemonic) (arg); \
  }

#define COMMON_INSTR_2(common_mnemonic, aarch64_mnemonic, arm32_mnemonic, arg1_type, arg2_type) \
  void common_mnemonic(arg1_type arg1, arg2_type arg2) { \
      AARCH64_ONLY(aarch64_mnemonic) NOT_AARCH64(arm32_mnemonic) (arg1, arg2); \
  }

#define COMMON_INSTR_3(common_mnemonic, aarch64_mnemonic, arm32_mnemonic, arg1_type, arg2_type, arg3_type) \
  void common_mnemonic(arg1_type arg1, arg2_type arg2, arg3_type arg3) { \
      AARCH64_ONLY(aarch64_mnemonic) NOT_AARCH64(arm32_mnemonic) (arg1, arg2, arg3); \
  }

  COMMON_INSTR_1(jump, br,  bx,  Register)
  COMMON_INSTR_1(call, blr, blx, Register)

  COMMON_INSTR_2(cbz_32,  cbz_w,  cbz,  Register, Label&)
  COMMON_INSTR_2(cbnz_32, cbnz_w, cbnz, Register, Label&)

  COMMON_INSTR_2(ldr_u32, ldr_w,  ldr,  Register, Address)
  COMMON_INSTR_2(ldr_s32, ldrsw,  ldr,  Register, Address)
  COMMON_INSTR_2(str_32,  str_w,  str,  Register, Address)

  COMMON_INSTR_2(mvn_32,  mvn_w,  mvn,  Register, Register)
  COMMON_INSTR_2(cmp_32,  cmp_w,  cmp,  Register, Register)
  COMMON_INSTR_2(neg_32,  neg_w,  neg,  Register, Register)
  COMMON_INSTR_2(clz_32,  clz_w,  clz,  Register, Register)
  COMMON_INSTR_2(rbit_32, rbit_w, rbit, Register, Register)

  COMMON_INSTR_2(cmp_32,  cmp_w,  cmp,  Register, int)
  COMMON_INSTR_2(cmn_32,  cmn_w,  cmn,  Register, int)

  COMMON_INSTR_3(add_32,  add_w,  add,  Register, Register, Register)
  COMMON_INSTR_3(sub_32,  sub_w,  sub,  Register, Register, Register)
  COMMON_INSTR_3(subs_32, subs_w, subs, Register, Register, Register)
  COMMON_INSTR_3(mul_32,  mul_w,  mul,  Register, Register, Register)
  COMMON_INSTR_3(and_32,  andr_w, andr, Register, Register, Register)
  COMMON_INSTR_3(orr_32,  orr_w,  orr,  Register, Register, Register)
  COMMON_INSTR_3(eor_32,  eor_w,  eor,  Register, Register, Register)

  COMMON_INSTR_3(add_32,  add_w,  add,  Register, Register, AsmOperand)
  COMMON_INSTR_3(sub_32,  sub_w,  sub,  Register, Register, AsmOperand)
  COMMON_INSTR_3(orr_32,  orr_w,  orr,  Register, Register, AsmOperand)
  COMMON_INSTR_3(eor_32,  eor_w,  eor,  Register, Register, AsmOperand)
  COMMON_INSTR_3(and_32,  andr_w, andr, Register, Register, AsmOperand)


  COMMON_INSTR_3(add_32,  add_w,  add,  Register, Register, int)
  COMMON_INSTR_3(adds_32, adds_w, adds, Register, Register, int)
  COMMON_INSTR_3(sub_32,  sub_w,  sub,  Register, Register, int)
  COMMON_INSTR_3(subs_32, subs_w, subs, Register, Register, int)

  COMMON_INSTR_2(tst_32,  tst_w,  tst,  Register, unsigned int)
  COMMON_INSTR_2(tst_32,  tst_w,  tst,  Register, AsmOperand)

  COMMON_INSTR_3(and_32,  andr_w, andr, Register, Register, uint)
  COMMON_INSTR_3(orr_32,  orr_w,  orr,  Register, Register, uint)
  COMMON_INSTR_3(eor_32,  eor_w,  eor,  Register, Register, uint)

  COMMON_INSTR_1(cmp_zero_float,  fcmp0_s, fcmpzs, FloatRegister)
  COMMON_INSTR_1(cmp_zero_double, fcmp0_d, fcmpzd, FloatRegister)

  COMMON_INSTR_2(ldr_float,   ldr_s,   flds,   FloatRegister, Address)
  COMMON_INSTR_2(str_float,   str_s,   fsts,   FloatRegister, Address)
  COMMON_INSTR_2(mov_float,   fmov_s,  fcpys,  FloatRegister, FloatRegister)
  COMMON_INSTR_2(neg_float,   fneg_s,  fnegs,  FloatRegister, FloatRegister)
  COMMON_INSTR_2(abs_float,   fabs_s,  fabss,  FloatRegister, FloatRegister)
  COMMON_INSTR_2(sqrt_float,  fsqrt_s, fsqrts, FloatRegister, FloatRegister)
  COMMON_INSTR_2(cmp_float,   fcmp_s,  fcmps,  FloatRegister, FloatRegister)

  COMMON_INSTR_3(add_float,   fadd_s,  fadds,  FloatRegister, FloatRegister, FloatRegister)
  COMMON_INSTR_3(sub_float,   fsub_s,  fsubs,  FloatRegister, FloatRegister, FloatRegister)
  COMMON_INSTR_3(mul_float,   fmul_s,  fmuls,  FloatRegister, FloatRegister, FloatRegister)
  COMMON_INSTR_3(div_float,   fdiv_s,  fdivs,  FloatRegister, FloatRegister, FloatRegister)

  COMMON_INSTR_2(ldr_double,  ldr_d,   fldd,   FloatRegister, Address)
  COMMON_INSTR_2(str_double,  str_d,   fstd,   FloatRegister, Address)
  COMMON_INSTR_2(mov_double,  fmov_d,  fcpyd,  FloatRegister, FloatRegister)
  COMMON_INSTR_2(neg_double,  fneg_d,  fnegd,  FloatRegister, FloatRegister)
  COMMON_INSTR_2(cmp_double,  fcmp_d,  fcmpd,  FloatRegister, FloatRegister)
  COMMON_INSTR_2(abs_double,  fabs_d,  fabsd,  FloatRegister, FloatRegister)
  COMMON_INSTR_2(sqrt_double, fsqrt_d, fsqrtd, FloatRegister, FloatRegister)

  COMMON_INSTR_3(add_double,  fadd_d,  faddd,  FloatRegister, FloatRegister, FloatRegister)
  COMMON_INSTR_3(sub_double,  fsub_d,  fsubd,  FloatRegister, FloatRegister, FloatRegister)
  COMMON_INSTR_3(mul_double,  fmul_d,  fmuld,  FloatRegister, FloatRegister, FloatRegister)
  COMMON_INSTR_3(div_double,  fdiv_d,  fdivd,  FloatRegister, FloatRegister, FloatRegister)

  COMMON_INSTR_2(convert_f2d, fcvt_ds, fcvtds, FloatRegister, FloatRegister)
  COMMON_INSTR_2(convert_d2f, fcvt_sd, fcvtsd, FloatRegister, FloatRegister)

  COMMON_INSTR_2(mov_fpr2gpr_float, fmov_ws, fmrs, Register, FloatRegister)

#undef COMMON_INSTR_1
#undef COMMON_INSTR_2
#undef COMMON_INSTR_3


#ifdef AARCH64

  void mov(Register dst, Register src, AsmCondition cond) {
    if (cond == al) {
      mov(dst, src);
    } else {
      csel(dst, src, dst, cond);
    }
  }

  // Propagate other overloaded "mov" methods from Assembler.
  void mov(Register dst, Register src)    { Assembler::mov(dst, src); }
  void mov(Register rd, int imm)          { Assembler::mov(rd, imm);  }

  void mov(Register dst, int imm, AsmCondition cond) {
    assert(imm == 0 || imm == 1, "");
    if (imm == 0) {
      mov(dst, ZR, cond);
    } else if (imm == 1) {
      csinc(dst, dst, ZR, inverse(cond));
    } else if (imm == -1) {
      csinv(dst, dst, ZR, inverse(cond));
    } else {
      fatal("illegal mov(R%d,%d,cond)", dst->encoding(), imm);
    }
  }

  void movs(Register dst, Register src)    { adds(dst, src, 0); }

#else // AARCH64

  void tbz(Register rt, int bit, Label& L) {
    assert(0 <= bit && bit < BitsPerWord, "bit number is out of range");
    tst(rt, 1 << bit);
    b(L, eq);
  }

  void tbnz(Register rt, int bit, Label& L) {
    assert(0 <= bit && bit < BitsPerWord, "bit number is out of range");
    tst(rt, 1 << bit);
    b(L, ne);
  }

  void cbz(Register rt, Label& L) {
    cmp(rt, 0);
    b(L, eq);
  }

  void cbz(Register rt, address target) {
    cmp(rt, 0);
    b(target, eq);
  }

  void cbnz(Register rt, Label& L) {
    cmp(rt, 0);
    b(L, ne);
  }

  void ret(Register dst = LR) {
    bx(dst);
  }

#endif // AARCH64

  Register zero_register(Register tmp) {
#ifdef AARCH64
    return ZR;
#else
    mov(tmp, 0);
    return tmp;
#endif
  }

  void logical_shift_left(Register dst, Register src, int shift) {
#ifdef AARCH64
    _lsl(dst, src, shift);
#else
    mov(dst, AsmOperand(src, lsl, shift));
#endif
  }

  void logical_shift_left_32(Register dst, Register src, int shift) {
#ifdef AARCH64
    _lsl_w(dst, src, shift);
#else
    mov(dst, AsmOperand(src, lsl, shift));
#endif
  }

  void logical_shift_right(Register dst, Register src, int shift) {
#ifdef AARCH64
    _lsr(dst, src, shift);
#else
    mov(dst, AsmOperand(src, lsr, shift));
#endif
  }

  void arith_shift_right(Register dst, Register src, int shift) {
#ifdef AARCH64
    _asr(dst, src, shift);
#else
    mov(dst, AsmOperand(src, asr, shift));
#endif
  }

  void asr_32(Register dst, Register src, int shift) {
#ifdef AARCH64
    _asr_w(dst, src, shift);
#else
    mov(dst, AsmOperand(src, asr, shift));
#endif
  }

  // If <cond> holds, compares r1 and r2. Otherwise, flags are set so that <cond> does not hold.
  void cond_cmp(Register r1, Register r2, AsmCondition cond) {
#ifdef AARCH64
    ccmp(r1, r2, flags_for_condition(inverse(cond)), cond);
#else
    cmp(r1, r2, cond);
#endif
  }

  // If <cond> holds, compares r and imm. Otherwise, flags are set so that <cond> does not hold.
  void cond_cmp(Register r, int imm, AsmCondition cond) {
#ifdef AARCH64
    ccmp(r, imm, flags_for_condition(inverse(cond)), cond);
#else
    cmp(r, imm, cond);
#endif
  }

  void align_reg(Register dst, Register src, int align) {
    assert (is_power_of_2(align), "should be");
#ifdef AARCH64
    andr(dst, src, ~(uintx)(align-1));
#else
    bic(dst, src, align-1);
#endif
  }

  void prefetch_read(Address addr) {
#ifdef AARCH64
    prfm(pldl1keep, addr);
#else
    pld(addr);
#endif
  }

  void raw_push(Register r1, Register r2) {
#ifdef AARCH64
    stp(r1, r2, Address(SP, -2*wordSize, pre_indexed));
#else
    assert(r1->encoding() < r2->encoding(), "should be ordered");
    push(RegisterSet(r1) | RegisterSet(r2));
#endif
  }

  void raw_pop(Register r1, Register r2) {
#ifdef AARCH64
    ldp(r1, r2, Address(SP, 2*wordSize, post_indexed));
#else
    assert(r1->encoding() < r2->encoding(), "should be ordered");
    pop(RegisterSet(r1) | RegisterSet(r2));
#endif
  }

  void raw_push(Register r1, Register r2, Register r3) {
#ifdef AARCH64
    raw_push(r1, r2);
    raw_push(r3, ZR);
#else
    assert(r1->encoding() < r2->encoding() && r2->encoding() < r3->encoding(), "should be ordered");
    push(RegisterSet(r1) | RegisterSet(r2) | RegisterSet(r3));
#endif
  }

  void raw_pop(Register r1, Register r2, Register r3) {
#ifdef AARCH64
    raw_pop(r3, ZR);
    raw_pop(r1, r2);
#else
    assert(r1->encoding() < r2->encoding() && r2->encoding() < r3->encoding(), "should be ordered");
    pop(RegisterSet(r1) | RegisterSet(r2) | RegisterSet(r3));
#endif
  }

  // Restores registers r1 and r2 previously saved by raw_push(r1, r2, ret_addr) and returns by ret_addr. Clobbers LR.
  void raw_pop_and_ret(Register r1, Register r2) {
#ifdef AARCH64
    raw_pop(r1, r2, LR);
    ret();
#else
    raw_pop(r1, r2, PC);
#endif
  }

  void indirect_jump(Address addr, Register scratch) {
#ifdef AARCH64
    ldr(scratch, addr);
    br(scratch);
#else
    ldr(PC, addr);
#endif
  }

  void indirect_jump(InlinedAddress& literal, Register scratch) {
#ifdef AARCH64
    ldr_literal(scratch, literal);
    br(scratch);
#else
    ldr_literal(PC, literal);
#endif
  }

#ifndef AARCH64
  void neg(Register dst, Register src) {
    rsb(dst, src, 0);
  }
#endif

  void branch_if_negative_32(Register r, Label& L) {
    // Note about branch_if_negative_32() / branch_if_any_negative_32() implementation for AArch64:
    // tbnz is not used instead of tst & b.mi because destination may be out of tbnz range (+-32KB)
    // since these methods are used in LIR_Assembler::emit_arraycopy() to jump to stub entry.
    tst_32(r, r);
    b(L, mi);
  }

  void branch_if_any_negative_32(Register r1, Register r2, Register tmp, Label& L) {
#ifdef AARCH64
    orr_32(tmp, r1, r2);
    tst_32(tmp, tmp);
#else
    orrs(tmp, r1, r2);
#endif
    b(L, mi);
  }

  void branch_if_any_negative_32(Register r1, Register r2, Register r3, Register tmp, Label& L) {
    orr_32(tmp, r1, r2);
#ifdef AARCH64
    orr_32(tmp, tmp, r3);
    tst_32(tmp, tmp);
#else
    orrs(tmp, tmp, r3);
#endif
    b(L, mi);
  }

  void add_ptr_scaled_int32(Register dst, Register r1, Register r2, int shift) {
#ifdef AARCH64
      add(dst, r1, r2, ex_sxtw, shift);
#else
      add(dst, r1, AsmOperand(r2, lsl, shift));
#endif
  }

  void sub_ptr_scaled_int32(Register dst, Register r1, Register r2, int shift) {
#ifdef AARCH64
    sub(dst, r1, r2, ex_sxtw, shift);
#else
    sub(dst, r1, AsmOperand(r2, lsl, shift));
#endif
  }


    // klass oop manipulations if compressed

#ifdef AARCH64
  void load_klass(Register dst_klass, Register src_oop);
#else
  void load_klass(Register dst_klass, Register src_oop, AsmCondition cond = al);
#endif // AARCH64

  void store_klass(Register src_klass, Register dst_oop);

#ifdef AARCH64
  void store_klass_gap(Register dst);
#endif // AARCH64

    // oop manipulations

  void load_heap_oop(Register dst, Address src);
  void store_heap_oop(Register src, Address dst);
  void store_heap_oop(Address dst, Register src) {
    store_heap_oop(src, dst);
  }
  void store_heap_oop_null(Register src, Address dst);

#ifdef AARCH64
  void encode_heap_oop(Register dst, Register src);
  void encode_heap_oop(Register r) {
    encode_heap_oop(r, r);
  }
  void decode_heap_oop(Register dst, Register src);
  void decode_heap_oop(Register r) {
      decode_heap_oop(r, r);
  }

#ifdef COMPILER2
  void encode_heap_oop_not_null(Register dst, Register src);
  void decode_heap_oop_not_null(Register dst, Register src);

  void set_narrow_klass(Register dst, Klass* k);
  void set_narrow_oop(Register dst, jobject obj);
#endif

  void encode_klass_not_null(Register r);
  void encode_klass_not_null(Register dst, Register src);
  void decode_klass_not_null(Register r);
  void decode_klass_not_null(Register dst, Register src);

  void reinit_heapbase();

#ifdef ASSERT
  void verify_heapbase(const char* msg);
#endif // ASSERT

  static int instr_count_for_mov_slow(intptr_t c);
  static int instr_count_for_mov_slow(address addr);
  static int instr_count_for_decode_klass_not_null();
#endif // AARCH64

  void ldr_global_ptr(Register reg, address address_of_global);
  void ldr_global_s32(Register reg, address address_of_global);
  void ldrb_global(Register reg, address address_of_global);

  // address_placeholder_instruction is invalid instruction and is used
  // as placeholder in code for address of label
  enum { address_placeholder_instruction = 0xFFFFFFFF };

  void emit_address(Label& L) {
    assert(!L.is_bound(), "otherwise address will not be patched");
    target(L);       // creates relocation which will be patched later

    assert ((offset() & (wordSize-1)) == 0, "should be aligned by word size");

#ifdef AARCH64
    emit_int32(address_placeholder_instruction);
    emit_int32(address_placeholder_instruction);
#else
    AbstractAssembler::emit_address((address)address_placeholder_instruction);
#endif
  }

  void b(address target, AsmCondition cond = al) {
    Assembler::b(target, cond);                 \
  }
  void b(Label& L, AsmCondition cond = al) {
    // internal jumps
    Assembler::b(target(L), cond);
  }

  void bl(address target NOT_AARCH64_ARG(AsmCondition cond = al)) {
    Assembler::bl(target NOT_AARCH64_ARG(cond));
  }
  void bl(Label& L NOT_AARCH64_ARG(AsmCondition cond = al)) {
    // internal calls
    Assembler::bl(target(L)  NOT_AARCH64_ARG(cond));
  }

#ifndef AARCH64
  void adr(Register dest, Label& L, AsmCondition cond = al) {
    int delta = target(L) - pc() - 8;
    if (delta >= 0) {
      add(dest, PC, delta, cond);
    } else {
      sub(dest, PC, -delta, cond);
    }
  }
#endif // !AARCH64

  // Variable-length jump and calls. We now distinguish only the
  // patchable case from the other cases. Patchable must be
  // distinguised from relocable. Relocable means the generated code
  // containing the jump/call may move. Patchable means that the
  // targeted address may be changed later.

  // Non patchable versions.
  // - used only for relocInfo::runtime_call_type and relocInfo::none
  // - may use relative or absolute format (do not use relocInfo::none
  //   if the generated code may move)
  // - the implementation takes into account switch to THUMB mode if the
  //   destination is a THUMB address
  // - the implementation supports far targets
  //
  // To reduce regression risk, scratch still defaults to noreg on
  // arm32. This results in patchable instructions. However, if
  // patching really matters, the call sites should be modified and
  // use patchable_call or patchable_jump. If patching is not required
  // and if a register can be cloberred, it should be explicitly
  // specified to allow future optimizations.
  void jump(address target,
            relocInfo::relocType rtype = relocInfo::runtime_call_type,
            Register scratch = AARCH64_ONLY(Rtemp) NOT_AARCH64(noreg)
#ifndef AARCH64
            , AsmCondition cond = al
#endif
            );

  void call(address target,
            RelocationHolder rspec
            NOT_AARCH64_ARG(AsmCondition cond = al));

  void call(address target,
            relocInfo::relocType rtype = relocInfo::runtime_call_type
            NOT_AARCH64_ARG(AsmCondition cond = al)) {
    call(target, Relocation::spec_simple(rtype) NOT_AARCH64_ARG(cond));
  }

  void jump(AddressLiteral dest) {
    jump(dest.target(), dest.reloc());
  }
#ifndef AARCH64
  void jump(address dest, relocInfo::relocType rtype, AsmCondition cond) {
    jump(dest, rtype, Rtemp, cond);
  }
#endif

  void call(AddressLiteral dest) {
    call(dest.target(), dest.reloc());
  }

  // Patchable version:
  // - set_destination can be used to atomically change the target
  //
  // The targets for patchable_jump and patchable_call must be in the
  // code cache.
  // [ including possible extensions of the code cache, like AOT code ]
  //
  // To reduce regression risk, scratch still defaults to noreg on
  // arm32. If a register can be cloberred, it should be explicitly
  // specified to allow future optimizations.
  void patchable_jump(address target,
                      relocInfo::relocType rtype = relocInfo::runtime_call_type,
                      Register scratch = AARCH64_ONLY(Rtemp) NOT_AARCH64(noreg)
#ifndef AARCH64
                      , AsmCondition cond = al
#endif
                      );

  // patchable_call may scratch Rtemp
  int patchable_call(address target,
                     RelocationHolder const& rspec,
                     bool c2 = false);

  int patchable_call(address target,
                     relocInfo::relocType rtype,
                     bool c2 = false) {
    return patchable_call(target, Relocation::spec_simple(rtype), c2);
  }

#if defined(AARCH64) && defined(COMPILER2)
  static int call_size(address target, bool far, bool patchable);
#endif

#ifdef AARCH64
  static bool page_reachable_from_cache(address target);
#endif
  static bool _reachable_from_cache(address target);
  static bool _cache_fully_reachable();
  bool cache_fully_reachable();
  bool reachable_from_cache(address target);

  void zero_extend(Register rd, Register rn, int bits);
  void sign_extend(Register rd, Register rn, int bits);

  inline void zap_high_non_significant_bits(Register r) {
#ifdef AARCH64
    if(ZapHighNonSignificantBits) {
      movk(r, 0xBAAD, 48);
      movk(r, 0xF00D, 32);
    }
#endif
  }

#ifndef AARCH64
  void long_move(Register rd_lo, Register rd_hi,
                 Register rn_lo, Register rn_hi,
                 AsmCondition cond = al);
  void long_shift(Register rd_lo, Register rd_hi,
                  Register rn_lo, Register rn_hi,
                  AsmShift shift, Register count);
  void long_shift(Register rd_lo, Register rd_hi,
                  Register rn_lo, Register rn_hi,
                  AsmShift shift, int count);

  void atomic_cas(Register tmpreg1, Register tmpreg2, Register oldval, Register newval, Register base, int offset);
  void atomic_cas_bool(Register oldval, Register newval, Register base, int offset, Register tmpreg);
  void atomic_cas64(Register temp_lo, Register temp_hi, Register temp_result, Register oldval_lo, Register oldval_hi, Register newval_lo, Register newval_hi, Register base, int offset);
#endif // !AARCH64

  void cas_for_lock_acquire(Register oldval, Register newval, Register base, Register tmp, Label &slow_case, bool allow_fallthrough_on_failure = false, bool one_shot = false);
  void cas_for_lock_release(Register oldval, Register newval, Register base, Register tmp, Label &slow_case, bool allow_fallthrough_on_failure = false, bool one_shot = false);

#ifndef PRODUCT
  // Preserves flags and all registers.
  // On SMP the updated value might not be visible to external observers without a sychronization barrier
  void cond_atomic_inc32(AsmCondition cond, int* counter_addr);
#endif // !PRODUCT

  // unconditional non-atomic increment
  void inc_counter(address counter_addr, Register tmpreg1, Register tmpreg2);
  void inc_counter(int* counter_addr, Register tmpreg1, Register tmpreg2) {
    inc_counter((address) counter_addr, tmpreg1, tmpreg2);
  }

  void pd_patch_instruction(address branch, address target);

  // Loading and storing values by size and signed-ness;
  // size must not exceed wordSize (i.e. 8-byte values are not supported on 32-bit ARM);
  // each of these calls generates exactly one load or store instruction,
  // so src can be pre- or post-indexed address.
#ifdef AARCH64
  void load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed);
  void store_sized_value(Register src, Address dst, size_t size_in_bytes);
#else
  // 32-bit ARM variants also support conditional execution
  void load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed, AsmCondition cond = al);
  void store_sized_value(Register src, Address dst, size_t size_in_bytes, AsmCondition cond = al);
#endif

  void lookup_interface_method(Register recv_klass,
                               Register intf_klass,
                               Register itable_index,
                               Register method_result,
                               Register temp_reg1,
                               Register temp_reg2,
                               Label& L_no_such_interface);

  // Compare char[] arrays aligned to 4 bytes.
  void char_arrays_equals(Register ary1, Register ary2,
                          Register limit, Register result,
                          Register chr1, Register chr2, Label& Ldone);


  void floating_cmp(Register dst);

  // improved x86 portability (minimizing source code changes)

  void ldr_literal(Register rd, AddressLiteral addr) {
    relocate(addr.rspec());
#ifdef AARCH64
    ldr(rd, addr.target());
#else
    ldr(rd, Address(PC, addr.target() - pc() - 8));
#endif
  }

  void lea(Register Rd, AddressLiteral addr) {
    // Never dereferenced, as on x86 (lval status ignored)
    mov_address(Rd, addr.target(), addr.rspec());
  }

  void restore_default_fp_mode();

#ifdef COMPILER2
#ifdef AARCH64
  // Code used by cmpFastLock and cmpFastUnlock mach instructions in .ad file.
  void fast_lock(Register obj, Register box, Register scratch, Register scratch2, Register scratch3);
  void fast_unlock(Register obj, Register box, Register scratch, Register scratch2, Register scratch3);
#else
  void fast_lock(Register obj, Register box, Register scratch, Register scratch2);
  void fast_unlock(Register obj, Register box, Register scratch, Register scratch2);
#endif
#endif

#ifdef AARCH64

#define F(mnemonic)                                             \
  void mnemonic(Register rt, address target) {                  \
    Assembler::mnemonic(rt, target);                            \
  }                                                             \
  void mnemonic(Register rt, Label& L) {                        \
    Assembler::mnemonic(rt, target(L));                         \
  }

  F(cbz_w);
  F(cbnz_w);
  F(cbz);
  F(cbnz);

#undef F

#define F(mnemonic)                                             \
  void mnemonic(Register rt, int bit, address target) {         \
    Assembler::mnemonic(rt, bit, target);                       \
  }                                                             \
  void mnemonic(Register rt, int bit, Label& L) {               \
    Assembler::mnemonic(rt, bit, target(L));                    \
  }

  F(tbz);
  F(tbnz);
#undef F

#endif // AARCH64

};


// The purpose of this class is to build several code fragments of the same size
// in order to allow fast table branch.

class FixedSizeCodeBlock VALUE_OBJ_CLASS_SPEC {
public:
  FixedSizeCodeBlock(MacroAssembler* masm, int size_in_instrs, bool enabled);
  ~FixedSizeCodeBlock();

private:
  MacroAssembler* _masm;
  address _start;
  int _size_in_instrs;
  bool _enabled;
};


#endif // CPU_ARM_VM_MACROASSEMBLER_ARM_HPP


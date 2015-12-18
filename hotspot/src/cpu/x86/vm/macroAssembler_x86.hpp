/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_MACROASSEMBLER_X86_HPP
#define CPU_X86_VM_MACROASSEMBLER_X86_HPP

#include "asm/assembler.hpp"
#include "utilities/macros.hpp"
#include "runtime/rtmLocking.hpp"

// MacroAssembler extends Assembler by frequently used macros.
//
// Instructions for which a 'better' code sequence exists depending
// on arguments should also go in here.

class MacroAssembler: public Assembler {
  friend class LIR_Assembler;
  friend class Runtime1;      // as_Address()

 protected:

  Address as_Address(AddressLiteral adr);
  Address as_Address(ArrayAddress adr);

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

#define COMMA ,

  VIRTUAL void call_VM_leaf_base(
    address entry_point,               // the entry point
    int     number_of_arguments        // the number of arguments to pop after the call
  );

  // This is the base routine called by the different versions of call_VM. The interpreter
  // may customize this version by overriding it for its purposes (e.g., to save/restore
  // additional registers when doing a VM call).
  //
  // If no java_thread register is specified (noreg) than rdi will be used instead. call_VM_base
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

  // helpers for FPU flag access
  // tmp is a temporary register, if none is available use noreg
  void save_rax   (Register tmp);
  void restore_rax(Register tmp);

 public:
  MacroAssembler(CodeBuffer* code) : Assembler(code) {}

  // Support for NULL-checks
  //
  // Generates code that causes a NULL OS exception if the content of reg is NULL.
  // If the accessed location is M[reg + offset] and the offset is known, provide the
  // offset. No explicit code generation is needed if the offset is within a certain
  // range (0 <= offset <= page_size).

  void null_check(Register reg, int offset = -1);
  static bool needs_explicit_null_check(intptr_t offset);

  // Required platform-specific helpers for Label::patch_instructions.
  // They _shadow_ the declarations in AbstractAssembler, which are undefined.
  void pd_patch_instruction(address branch, address target) {
    unsigned char op = branch[0];
    assert(op == 0xE8 /* call */ ||
        op == 0xE9 /* jmp */ ||
        op == 0xEB /* short jmp */ ||
        (op & 0xF0) == 0x70 /* short jcc */ ||
        op == 0x0F && (branch[1] & 0xF0) == 0x80 /* jcc */ ||
        op == 0xC7 && branch[1] == 0xF8 /* xbegin */,
        "Invalid opcode at patch point");

    if (op == 0xEB || (op & 0xF0) == 0x70) {
      // short offset operators (jmp and jcc)
      char* disp = (char*) &branch[1];
      int imm8 = target - (address) &disp[1];
      guarantee(this->is8bit(imm8), "Short forward jump exceeds 8-bit offset");
      *disp = imm8;
    } else {
      int* disp = (int*) &branch[(op == 0x0F || op == 0xC7)? 2: 1];
      int imm32 = target - (address) &disp[1];
      *disp = imm32;
    }
  }

  // The following 4 methods return the offset of the appropriate move instruction

  // Support for fast byte/short loading with zero extension (depending on particular CPU)
  int load_unsigned_byte(Register dst, Address src);
  int load_unsigned_short(Register dst, Address src);

  // Support for fast byte/short loading with sign extension (depending on particular CPU)
  int load_signed_byte(Register dst, Address src);
  int load_signed_short(Register dst, Address src);

  // Support for sign-extension (hi:lo = extend_sign(lo))
  void extend_sign(Register hi, Register lo);

  // Load and store values by size and signed-ness
  void load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed, Register dst2 = noreg);
  void store_sized_value(Address dst, Register src, size_t size_in_bytes, Register src2 = noreg);

  // Support for inc/dec with optimal instruction selection depending on value

  void increment(Register reg, int value = 1) { LP64_ONLY(incrementq(reg, value)) NOT_LP64(incrementl(reg, value)) ; }
  void decrement(Register reg, int value = 1) { LP64_ONLY(decrementq(reg, value)) NOT_LP64(decrementl(reg, value)) ; }

  void decrementl(Address dst, int value = 1);
  void decrementl(Register reg, int value = 1);

  void decrementq(Register reg, int value = 1);
  void decrementq(Address dst, int value = 1);

  void incrementl(Address dst, int value = 1);
  void incrementl(Register reg, int value = 1);

  void incrementq(Register reg, int value = 1);
  void incrementq(Address dst, int value = 1);

  // Support optimal SSE move instructions.
  void movflt(XMMRegister dst, XMMRegister src) {
    if (UseXmmRegToRegMoveAll) { movaps(dst, src); return; }
    else                       { movss (dst, src); return; }
  }
  void movflt(XMMRegister dst, Address src) { movss(dst, src); }
  void movflt(XMMRegister dst, AddressLiteral src);
  void movflt(Address dst, XMMRegister src) { movss(dst, src); }

  void movdbl(XMMRegister dst, XMMRegister src) {
    if (UseXmmRegToRegMoveAll) { movapd(dst, src); return; }
    else                       { movsd (dst, src); return; }
  }

  void movdbl(XMMRegister dst, AddressLiteral src);

  void movdbl(XMMRegister dst, Address src) {
    if (UseXmmLoadAndClearUpper) { movsd (dst, src); return; }
    else                         { movlpd(dst, src); return; }
  }
  void movdbl(Address dst, XMMRegister src) { movsd(dst, src); }

  void incrementl(AddressLiteral dst);
  void incrementl(ArrayAddress dst);

  void incrementq(AddressLiteral dst);

  // Alignment
  void align(int modulus);
  void align(int modulus, int target);

  // A 5 byte nop that is safe for patching (see patch_verified_entry)
  void fat_nop();

  // Stack frame creation/removal
  void enter();
  void leave();

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
  void set_last_Java_frame(Register thread,
                           Register last_java_sp,
                           Register last_java_fp,
                           address last_java_pc);

  // thread in the default location (r15_thread on 64bit)
  void set_last_Java_frame(Register last_java_sp,
                           Register last_java_fp,
                           address last_java_pc);

  void reset_last_Java_frame(Register thread, bool clear_fp, bool clear_pc);

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

  // C 'boolean' to Java boolean: x == 0 ? 0 : 1
  void c2bool(Register x);

  // C++ bool manipulation

  void movbool(Register dst, Address src);
  void movbool(Address dst, bool boolconst);
  void movbool(Address dst, Register src);
  void testbool(Register dst);

  // oop manipulations
  void load_klass(Register dst, Register src);
  void store_klass(Register dst, Register src);

  void load_heap_oop(Register dst, Address src);
  void load_heap_oop_not_null(Register dst, Address src);
  void store_heap_oop(Address dst, Register src);
  void cmp_heap_oop(Register src1, Address src2, Register tmp = noreg);

  // Used for storing NULL. All other oop constants should be
  // stored using routines that take a jobject.
  void store_heap_oop_null(Address dst);

  void load_prototype_header(Register dst, Register src);

#ifdef _LP64
  void store_klass_gap(Register dst, Register src);

  // This dummy is to prevent a call to store_heap_oop from
  // converting a zero (like NULL) into a Register by giving
  // the compiler two choices it can't resolve

  void store_heap_oop(Address dst, void* dummy);

  void encode_heap_oop(Register r);
  void decode_heap_oop(Register r);
  void encode_heap_oop_not_null(Register r);
  void decode_heap_oop_not_null(Register r);
  void encode_heap_oop_not_null(Register dst, Register src);
  void decode_heap_oop_not_null(Register dst, Register src);

  void set_narrow_oop(Register dst, jobject obj);
  void set_narrow_oop(Address dst, jobject obj);
  void cmp_narrow_oop(Register dst, jobject obj);
  void cmp_narrow_oop(Address dst, jobject obj);

  void encode_klass_not_null(Register r);
  void decode_klass_not_null(Register r);
  void encode_klass_not_null(Register dst, Register src);
  void decode_klass_not_null(Register dst, Register src);
  void set_narrow_klass(Register dst, Klass* k);
  void set_narrow_klass(Address dst, Klass* k);
  void cmp_narrow_klass(Register dst, Klass* k);
  void cmp_narrow_klass(Address dst, Klass* k);

  // Returns the byte size of the instructions generated by decode_klass_not_null()
  // when compressed klass pointers are being used.
  static int instr_size_for_decode_klass_not_null();

  // if heap base register is used - reinit it with the correct value
  void reinit_heapbase();

  DEBUG_ONLY(void verify_heapbase(const char* msg);)

#endif // _LP64

  // Int division/remainder for Java
  // (as idivl, but checks for special case as described in JVM spec.)
  // returns idivl instruction offset for implicit exception handling
  int corrected_idivl(Register reg);

  // Long division/remainder for Java
  // (as idivq, but checks for special case as described in JVM spec.)
  // returns idivq instruction offset for implicit exception handling
  int corrected_idivq(Register reg);

  void int3();

  // Long operation macros for a 32bit cpu
  // Long negation for Java
  void lneg(Register hi, Register lo);

  // Long multiplication for Java
  // (destroys contents of eax, ebx, ecx and edx)
  void lmul(int x_rsp_offset, int y_rsp_offset); // rdx:rax = x * y

  // Long shifts for Java
  // (semantics as described in JVM spec.)
  void lshl(Register hi, Register lo);                               // hi:lo << (rcx & 0x3f)
  void lshr(Register hi, Register lo, bool sign_extension = false);  // hi:lo >> (rcx & 0x3f)

  // Long compare for Java
  // (semantics as described in JVM spec.)
  void lcmp2int(Register x_hi, Register x_lo, Register y_hi, Register y_lo); // x_hi = lcmp(x, y)


  // misc

  // Sign extension
  void sign_extend_short(Register reg);
  void sign_extend_byte(Register reg);

  // Division by power of 2, rounding towards 0
  void division_with_shift(Register reg, int shift_value);

  // Compares the top-most stack entries on the FPU stack and sets the eflags as follows:
  //
  // CF (corresponds to C0) if x < y
  // PF (corresponds to C2) if unordered
  // ZF (corresponds to C3) if x = y
  //
  // The arguments are in reversed order on the stack (i.e., top of stack is first argument).
  // tmp is a temporary register, if none is available use noreg (only matters for non-P6 code)
  void fcmp(Register tmp);
  // Variant of the above which allows y to be further down the stack
  // and which only pops x and y if specified. If pop_right is
  // specified then pop_left must also be specified.
  void fcmp(Register tmp, int index, bool pop_left, bool pop_right);

  // Floating-point comparison for Java
  // Compares the top-most stack entries on the FPU stack and stores the result in dst.
  // The arguments are in reversed order on the stack (i.e., top of stack is first argument).
  // (semantics as described in JVM spec.)
  void fcmp2int(Register dst, bool unordered_is_less);
  // Variant of the above which allows y to be further down the stack
  // and which only pops x and y if specified. If pop_right is
  // specified then pop_left must also be specified.
  void fcmp2int(Register dst, bool unordered_is_less, int index, bool pop_left, bool pop_right);

  // Floating-point remainder for Java (ST0 = ST0 fremr ST1, ST1 is empty afterwards)
  // tmp is a temporary register, if none is available use noreg
  void fremr(Register tmp);


  // same as fcmp2int, but using SSE2
  void cmpss2int(XMMRegister opr1, XMMRegister opr2, Register dst, bool unordered_is_less);
  void cmpsd2int(XMMRegister opr1, XMMRegister opr2, Register dst, bool unordered_is_less);

  // Inlined sin/cos generator for Java; must not use CPU instruction
  // directly on Intel as it does not have high enough precision
  // outside of the range [-pi/4, pi/4]. Extra argument indicate the
  // number of FPU stack slots in use; all but the topmost will
  // require saving if a slow case is necessary. Assumes argument is
  // on FP TOS; result is on FP TOS.  No cpu registers are changed by
  // this code.
  void trigfunc(char trig, int num_fpu_regs_in_use = 1);

  // branch to L if FPU flag C2 is set/not set
  // tmp is a temporary register, if none is available use noreg
  void jC2 (Register tmp, Label& L);
  void jnC2(Register tmp, Label& L);

  // Pop ST (ffree & fincstp combined)
  void fpop();

  // Load float value from 'address'. If UseSSE >= 1, the value is loaded into
  // register xmm0. Otherwise, the value is loaded onto the FPU stack.
  void load_float(Address src);

  // Store float value to 'address'. If UseSSE >= 1, the value is stored
  // from register xmm0. Otherwise, the value is stored from the FPU stack.
  void store_float(Address dst);

  // Load double value from 'address'. If UseSSE >= 2, the value is loaded into
  // register xmm0. Otherwise, the value is loaded onto the FPU stack.
  void load_double(Address src);

  // Store double value to 'address'. If UseSSE >= 2, the value is stored
  // from register xmm0. Otherwise, the value is stored from the FPU stack.
  void store_double(Address dst);

  // pushes double TOS element of FPU stack on CPU stack; pops from FPU stack
  void push_fTOS();

  // pops double TOS element from CPU stack and pushes on FPU stack
  void pop_fTOS();

  void empty_FPU_stack();

  void push_IU_state();
  void pop_IU_state();

  void push_FPU_state();
  void pop_FPU_state();

  void push_CPU_state();
  void pop_CPU_state();

  // Round up to a power of two
  void round_to(Register reg, int modulus);

  // Callee saved registers handling
  void push_callee_saved_registers();
  void pop_callee_saved_registers();

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

  // method handles (JSR 292)
  Address argument_address(RegisterOrConstant arg_slot, int extra_slot_offset = 0);

  //----
  void set_word_if_not_zero(Register reg); // sets reg to 1 if not zero, otherwise 0

  // Debugging

  // only if +VerifyOops
  // TODO: Make these macros with file and line like sparc version!
  void verify_oop(Register reg, const char* s = "broken oop");
  void verify_oop_addr(Address addr, const char * s = "broken oop addr");

  // TODO: verify method and klass metadata (compare against vptr?)
  void _verify_method_ptr(Register reg, const char * msg, const char * file, int line) {}
  void _verify_klass_ptr(Register reg, const char * msg, const char * file, int line){}

#define verify_method_ptr(reg) _verify_method_ptr(reg, "broken method " #reg, __FILE__, __LINE__)
#define verify_klass_ptr(reg) _verify_klass_ptr(reg, "broken klass " #reg, __FILE__, __LINE__)

  // only if +VerifyFPU
  void verify_FPU(int stack_depth, const char* s = "illegal FPU state");

  // Verify or restore cpu control state after JNI call
  void restore_cpu_control_state_after_jni();

  // prints msg, dumps registers and stops execution
  void stop(const char* msg);

  // prints msg and continues
  void warn(const char* msg);

  // dumps registers and other state
  void print_state();

  static void debug32(int rdi, int rsi, int rbp, int rsp, int rbx, int rdx, int rcx, int rax, int eip, char* msg);
  static void debug64(char* msg, int64_t pc, int64_t regs[]);
  static void print_state32(int rdi, int rsi, int rbp, int rsp, int rbx, int rdx, int rcx, int rax, int eip);
  static void print_state64(int64_t pc, int64_t regs[]);

  void os_breakpoint();

  void untested()                                { stop("untested"); }

  void unimplemented(const char* what = "")      { char* b = new char[1024];  jio_snprintf(b, 1024, "unimplemented: %s", what);  stop(b); }

  void should_not_reach_here()                   { stop("should not reach here"); }

  void print_CPU_state();

  // Stack overflow checking
  void bang_stack_with_offset(int offset) {
    // stack grows down, caller passes positive offset
    assert(offset > 0, "must bang with negative offset");
    movl(Address(rsp, (-offset)), rax);
  }

  // Writes to stack successive pages until offset reached to check for
  // stack overflow + shadow pages.  Also, clobbers tmp
  void bang_stack_size(Register size, Register tmp);

  virtual RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr,
                                                Register tmp,
                                                int offset);

  // Support for serializing memory accesses between threads
  void serialize_memory(Register thread, Register tmp);

  void verify_tlab();

  // Biased locking support
  // lock_reg and obj_reg must be loaded up with the appropriate values.
  // swap_reg must be rax, and is killed.
  // tmp_reg is optional. If it is supplied (i.e., != noreg) it will
  // be killed; if not supplied, push/pop will be used internally to
  // allocate a temporary (inefficient, avoid if possible).
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
#ifdef COMPILER2
  // Code used by cmpFastLock and cmpFastUnlock mach instructions in .ad file.
  // See full desription in macroAssembler_x86.cpp.
  void fast_lock(Register obj, Register box, Register tmp,
                 Register scr, Register cx1, Register cx2,
                 BiasedLockingCounters* counters,
                 RTMLockingCounters* rtm_counters,
                 RTMLockingCounters* stack_rtm_counters,
                 Metadata* method_data,
                 bool use_rtm, bool profile_rtm);
  void fast_unlock(Register obj, Register box, Register tmp, bool use_rtm);
#if INCLUDE_RTM_OPT
  void rtm_counters_update(Register abort_status, Register rtm_counters);
  void branch_on_random_using_rdtsc(Register tmp, Register scr, int count, Label& brLabel);
  void rtm_abort_ratio_calculation(Register tmp, Register rtm_counters_reg,
                                   RTMLockingCounters* rtm_counters,
                                   Metadata* method_data);
  void rtm_profiling(Register abort_status_Reg, Register rtm_counters_Reg,
                     RTMLockingCounters* rtm_counters, Metadata* method_data, bool profile_rtm);
  void rtm_retry_lock_on_abort(Register retry_count, Register abort_status, Label& retryLabel);
  void rtm_retry_lock_on_busy(Register retry_count, Register box, Register tmp, Register scr, Label& retryLabel);
  void rtm_stack_locking(Register obj, Register tmp, Register scr,
                         Register retry_on_abort_count,
                         RTMLockingCounters* stack_rtm_counters,
                         Metadata* method_data, bool profile_rtm,
                         Label& DONE_LABEL, Label& IsInflated);
  void rtm_inflated_locking(Register obj, Register box, Register tmp,
                            Register scr, Register retry_on_busy_count,
                            Register retry_on_abort_count,
                            RTMLockingCounters* rtm_counters,
                            Metadata* method_data, bool profile_rtm,
                            Label& DONE_LABEL);
#endif
#endif

  Condition negate_condition(Condition cond);

  // Instructions that use AddressLiteral operands. These instruction can handle 32bit/64bit
  // operands. In general the names are modified to avoid hiding the instruction in Assembler
  // so that we don't need to implement all the varieties in the Assembler with trivial wrappers
  // here in MacroAssembler. The major exception to this rule is call

  // Arithmetics


  void addptr(Address dst, int32_t src) { LP64_ONLY(addq(dst, src)) NOT_LP64(addl(dst, src)) ; }
  void addptr(Address dst, Register src);

  void addptr(Register dst, Address src) { LP64_ONLY(addq(dst, src)) NOT_LP64(addl(dst, src)); }
  void addptr(Register dst, int32_t src);
  void addptr(Register dst, Register src);
  void addptr(Register dst, RegisterOrConstant src) {
    if (src.is_constant()) addptr(dst, (int) src.as_constant());
    else                   addptr(dst,       src.as_register());
  }

  void andptr(Register dst, int32_t src);
  void andptr(Register src1, Register src2) { LP64_ONLY(andq(src1, src2)) NOT_LP64(andl(src1, src2)) ; }

  void cmp8(AddressLiteral src1, int imm);

  // renamed to drag out the casting of address to int32_t/intptr_t
  void cmp32(Register src1, int32_t imm);

  void cmp32(AddressLiteral src1, int32_t imm);
  // compare reg - mem, or reg - &mem
  void cmp32(Register src1, AddressLiteral src2);

  void cmp32(Register src1, Address src2);

#ifndef _LP64
  void cmpklass(Address dst, Metadata* obj);
  void cmpklass(Register dst, Metadata* obj);
  void cmpoop(Address dst, jobject obj);
  void cmpoop(Register dst, jobject obj);
#endif // _LP64

  // NOTE src2 must be the lval. This is NOT an mem-mem compare
  void cmpptr(Address src1, AddressLiteral src2);

  void cmpptr(Register src1, AddressLiteral src2);

  void cmpptr(Register src1, Register src2) { LP64_ONLY(cmpq(src1, src2)) NOT_LP64(cmpl(src1, src2)) ; }
  void cmpptr(Register src1, Address src2) { LP64_ONLY(cmpq(src1, src2)) NOT_LP64(cmpl(src1, src2)) ; }
  // void cmpptr(Address src1, Register src2) { LP64_ONLY(cmpq(src1, src2)) NOT_LP64(cmpl(src1, src2)) ; }

  void cmpptr(Register src1, int32_t src2) { LP64_ONLY(cmpq(src1, src2)) NOT_LP64(cmpl(src1, src2)) ; }
  void cmpptr(Address src1, int32_t src2) { LP64_ONLY(cmpq(src1, src2)) NOT_LP64(cmpl(src1, src2)) ; }

  // cmp64 to avoild hiding cmpq
  void cmp64(Register src1, AddressLiteral src);

  void cmpxchgptr(Register reg, Address adr);

  void locked_cmpxchgptr(Register reg, AddressLiteral adr);


  void imulptr(Register dst, Register src) { LP64_ONLY(imulq(dst, src)) NOT_LP64(imull(dst, src)); }
  void imulptr(Register dst, Register src, int imm32) { LP64_ONLY(imulq(dst, src, imm32)) NOT_LP64(imull(dst, src, imm32)); }


  void negptr(Register dst) { LP64_ONLY(negq(dst)) NOT_LP64(negl(dst)); }

  void notptr(Register dst) { LP64_ONLY(notq(dst)) NOT_LP64(notl(dst)); }

  void shlptr(Register dst, int32_t shift);
  void shlptr(Register dst) { LP64_ONLY(shlq(dst)) NOT_LP64(shll(dst)); }

  void shrptr(Register dst, int32_t shift);
  void shrptr(Register dst) { LP64_ONLY(shrq(dst)) NOT_LP64(shrl(dst)); }

  void sarptr(Register dst) { LP64_ONLY(sarq(dst)) NOT_LP64(sarl(dst)); }
  void sarptr(Register dst, int32_t src) { LP64_ONLY(sarq(dst, src)) NOT_LP64(sarl(dst, src)); }

  void subptr(Address dst, int32_t src) { LP64_ONLY(subq(dst, src)) NOT_LP64(subl(dst, src)); }

  void subptr(Register dst, Address src) { LP64_ONLY(subq(dst, src)) NOT_LP64(subl(dst, src)); }
  void subptr(Register dst, int32_t src);
  // Force generation of a 4 byte immediate value even if it fits into 8bit
  void subptr_imm32(Register dst, int32_t src);
  void subptr(Register dst, Register src);
  void subptr(Register dst, RegisterOrConstant src) {
    if (src.is_constant()) subptr(dst, (int) src.as_constant());
    else                   subptr(dst,       src.as_register());
  }

  void sbbptr(Address dst, int32_t src) { LP64_ONLY(sbbq(dst, src)) NOT_LP64(sbbl(dst, src)); }
  void sbbptr(Register dst, int32_t src) { LP64_ONLY(sbbq(dst, src)) NOT_LP64(sbbl(dst, src)); }

  void xchgptr(Register src1, Register src2) { LP64_ONLY(xchgq(src1, src2)) NOT_LP64(xchgl(src1, src2)) ; }
  void xchgptr(Register src1, Address src2) { LP64_ONLY(xchgq(src1, src2)) NOT_LP64(xchgl(src1, src2)) ; }

  void xaddptr(Address src1, Register src2) { LP64_ONLY(xaddq(src1, src2)) NOT_LP64(xaddl(src1, src2)) ; }



  // Helper functions for statistics gathering.
  // Conditionally (atomically, on MPs) increments passed counter address, preserving condition codes.
  void cond_inc32(Condition cond, AddressLiteral counter_addr);
  // Unconditional atomic increment.
  void atomic_incl(Address counter_addr);
  void atomic_incl(AddressLiteral counter_addr, Register scr = rscratch1);
#ifdef _LP64
  void atomic_incq(Address counter_addr);
  void atomic_incq(AddressLiteral counter_addr, Register scr = rscratch1);
#endif
  void atomic_incptr(AddressLiteral counter_addr, Register scr = rscratch1) { LP64_ONLY(atomic_incq(counter_addr, scr)) NOT_LP64(atomic_incl(counter_addr, scr)) ; }
  void atomic_incptr(Address counter_addr) { LP64_ONLY(atomic_incq(counter_addr)) NOT_LP64(atomic_incl(counter_addr)) ; }

  void lea(Register dst, AddressLiteral adr);
  void lea(Address dst, AddressLiteral adr);
  void lea(Register dst, Address adr) { Assembler::lea(dst, adr); }

  void leal32(Register dst, Address src) { leal(dst, src); }

  // Import other testl() methods from the parent class or else
  // they will be hidden by the following overriding declaration.
  using Assembler::testl;
  void testl(Register dst, AddressLiteral src);

  void orptr(Register dst, Address src) { LP64_ONLY(orq(dst, src)) NOT_LP64(orl(dst, src)); }
  void orptr(Register dst, Register src) { LP64_ONLY(orq(dst, src)) NOT_LP64(orl(dst, src)); }
  void orptr(Register dst, int32_t src) { LP64_ONLY(orq(dst, src)) NOT_LP64(orl(dst, src)); }
  void orptr(Address dst, int32_t imm32) { LP64_ONLY(orq(dst, imm32)) NOT_LP64(orl(dst, imm32)); }

  void testptr(Register src, int32_t imm32) {  LP64_ONLY(testq(src, imm32)) NOT_LP64(testl(src, imm32)); }
  void testptr(Register src1, Register src2);

  void xorptr(Register dst, Register src) { LP64_ONLY(xorq(dst, src)) NOT_LP64(xorl(dst, src)); }
  void xorptr(Register dst, Address src) { LP64_ONLY(xorq(dst, src)) NOT_LP64(xorl(dst, src)); }

  // Calls

  void call(Label& L, relocInfo::relocType rtype);
  void call(Register entry);

  // NOTE: this call tranfers to the effective address of entry NOT
  // the address contained by entry. This is because this is more natural
  // for jumps/calls.
  void call(AddressLiteral entry);

  // Emit the CompiledIC call idiom
  void ic_call(address entry);

  // Jumps

  // NOTE: these jumps tranfer to the effective address of dst NOT
  // the address contained by dst. This is because this is more natural
  // for jumps/calls.
  void jump(AddressLiteral dst);
  void jump_cc(Condition cc, AddressLiteral dst);

  // 32bit can do a case table jump in one instruction but we no longer allow the base
  // to be installed in the Address class. This jump will tranfers to the address
  // contained in the location described by entry (not the address of entry)
  void jump(ArrayAddress entry);

  // Floating

  void andpd(XMMRegister dst, Address src) { Assembler::andpd(dst, src); }
  void andpd(XMMRegister dst, AddressLiteral src);

  void andps(XMMRegister dst, XMMRegister src) { Assembler::andps(dst, src); }
  void andps(XMMRegister dst, Address src) { Assembler::andps(dst, src); }
  void andps(XMMRegister dst, AddressLiteral src);

  void comiss(XMMRegister dst, XMMRegister src) { Assembler::comiss(dst, src); }
  void comiss(XMMRegister dst, Address src) { Assembler::comiss(dst, src); }
  void comiss(XMMRegister dst, AddressLiteral src);

  void comisd(XMMRegister dst, XMMRegister src) { Assembler::comisd(dst, src); }
  void comisd(XMMRegister dst, Address src) { Assembler::comisd(dst, src); }
  void comisd(XMMRegister dst, AddressLiteral src);

  void fadd_s(Address src)        { Assembler::fadd_s(src); }
  void fadd_s(AddressLiteral src) { Assembler::fadd_s(as_Address(src)); }

  void fldcw(Address src) { Assembler::fldcw(src); }
  void fldcw(AddressLiteral src);

  void fld_s(int index)   { Assembler::fld_s(index); }
  void fld_s(Address src) { Assembler::fld_s(src); }
  void fld_s(AddressLiteral src);

  void fld_d(Address src) { Assembler::fld_d(src); }
  void fld_d(AddressLiteral src);

  void fld_x(Address src) { Assembler::fld_x(src); }
  void fld_x(AddressLiteral src);

  void fmul_s(Address src)        { Assembler::fmul_s(src); }
  void fmul_s(AddressLiteral src) { Assembler::fmul_s(as_Address(src)); }

  void ldmxcsr(Address src) { Assembler::ldmxcsr(src); }
  void ldmxcsr(AddressLiteral src);

  // compute pow(x,y) and exp(x) with x86 instructions. Don't cover
  // all corner cases and may result in NaN and require fallback to a
  // runtime call.
  void fast_pow();
  void fast_exp(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3,
                XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7,
                Register rax, Register rcx, Register rdx, Register tmp);

  void fast_log(XMMRegister xmm0, XMMRegister xmm1, XMMRegister xmm2, XMMRegister xmm3,
                XMMRegister xmm4, XMMRegister xmm5, XMMRegister xmm6, XMMRegister xmm7,
                Register rax, Register rcx, Register rdx, Register tmp1 LP64_ONLY(COMMA Register tmp2));

  void increase_precision();
  void restore_precision();

  // computes pow(x,y). Fallback to runtime call included.
  void pow_with_fallback(int num_fpu_regs_in_use) { pow_or_exp(num_fpu_regs_in_use); }

private:

  // call runtime as a fallback for trig functions and pow/exp.
  void fp_runtime_fallback(address runtime_entry, int nb_args, int num_fpu_regs_in_use);

  // computes 2^(Ylog2X); Ylog2X in ST(0)
  void pow_exp_core_encoding();

  // computes pow(x,y) or exp(x). Fallback to runtime call included.
  void pow_or_exp(int num_fpu_regs_in_use);

  // these are private because users should be doing movflt/movdbl

  void movss(Address dst, XMMRegister src)     { Assembler::movss(dst, src); }
  void movss(XMMRegister dst, XMMRegister src) { Assembler::movss(dst, src); }
  void movss(XMMRegister dst, Address src)     { Assembler::movss(dst, src); }
  void movss(XMMRegister dst, AddressLiteral src);

  void movlpd(XMMRegister dst, Address src)    {Assembler::movlpd(dst, src); }
  void movlpd(XMMRegister dst, AddressLiteral src);

public:

  void addsd(XMMRegister dst, XMMRegister src)    { Assembler::addsd(dst, src); }
  void addsd(XMMRegister dst, Address src)        { Assembler::addsd(dst, src); }
  void addsd(XMMRegister dst, AddressLiteral src);

  void addss(XMMRegister dst, XMMRegister src)    { Assembler::addss(dst, src); }
  void addss(XMMRegister dst, Address src)        { Assembler::addss(dst, src); }
  void addss(XMMRegister dst, AddressLiteral src);

  void divsd(XMMRegister dst, XMMRegister src)    { Assembler::divsd(dst, src); }
  void divsd(XMMRegister dst, Address src)        { Assembler::divsd(dst, src); }
  void divsd(XMMRegister dst, AddressLiteral src);

  void divss(XMMRegister dst, XMMRegister src)    { Assembler::divss(dst, src); }
  void divss(XMMRegister dst, Address src)        { Assembler::divss(dst, src); }
  void divss(XMMRegister dst, AddressLiteral src);

  // Move Unaligned Double Quadword
  void movdqu(Address     dst, XMMRegister src);
  void movdqu(XMMRegister dst, Address src);
  void movdqu(XMMRegister dst, XMMRegister src);
  void movdqu(XMMRegister dst, AddressLiteral src);
  // AVX Unaligned forms
  void vmovdqu(Address     dst, XMMRegister src);
  void vmovdqu(XMMRegister dst, Address src);
  void vmovdqu(XMMRegister dst, XMMRegister src);
  void vmovdqu(XMMRegister dst, AddressLiteral src);

  // Move Aligned Double Quadword
  void movdqa(XMMRegister dst, Address src)       { Assembler::movdqa(dst, src); }
  void movdqa(XMMRegister dst, XMMRegister src)   { Assembler::movdqa(dst, src); }
  void movdqa(XMMRegister dst, AddressLiteral src);

  void movsd(XMMRegister dst, XMMRegister src) { Assembler::movsd(dst, src); }
  void movsd(Address dst, XMMRegister src)     { Assembler::movsd(dst, src); }
  void movsd(XMMRegister dst, Address src)     { Assembler::movsd(dst, src); }
  void movsd(XMMRegister dst, AddressLiteral src);

  void mulpd(XMMRegister dst, XMMRegister src)    { Assembler::mulpd(dst, src); }
  void mulpd(XMMRegister dst, Address src)        { Assembler::mulpd(dst, src); }
  void mulpd(XMMRegister dst, AddressLiteral src);

  void mulsd(XMMRegister dst, XMMRegister src)    { Assembler::mulsd(dst, src); }
  void mulsd(XMMRegister dst, Address src)        { Assembler::mulsd(dst, src); }
  void mulsd(XMMRegister dst, AddressLiteral src);

  void mulss(XMMRegister dst, XMMRegister src)    { Assembler::mulss(dst, src); }
  void mulss(XMMRegister dst, Address src)        { Assembler::mulss(dst, src); }
  void mulss(XMMRegister dst, AddressLiteral src);

  // Carry-Less Multiplication Quadword
  void pclmulldq(XMMRegister dst, XMMRegister src) {
    // 0x00 - multiply lower 64 bits [0:63]
    Assembler::pclmulqdq(dst, src, 0x00);
  }
  void pclmulhdq(XMMRegister dst, XMMRegister src) {
    // 0x11 - multiply upper 64 bits [64:127]
    Assembler::pclmulqdq(dst, src, 0x11);
  }

  void pcmpeqb(XMMRegister dst, XMMRegister src);
  void pcmpeqw(XMMRegister dst, XMMRegister src);

  void pcmpestri(XMMRegister dst, Address src, int imm8);
  void pcmpestri(XMMRegister dst, XMMRegister src, int imm8);

  void pmovzxbw(XMMRegister dst, XMMRegister src);
  void pmovzxbw(XMMRegister dst, Address src);

  void pmovmskb(Register dst, XMMRegister src);

  void ptest(XMMRegister dst, XMMRegister src);

  void sqrtsd(XMMRegister dst, XMMRegister src)    { Assembler::sqrtsd(dst, src); }
  void sqrtsd(XMMRegister dst, Address src)        { Assembler::sqrtsd(dst, src); }
  void sqrtsd(XMMRegister dst, AddressLiteral src);

  void sqrtss(XMMRegister dst, XMMRegister src)    { Assembler::sqrtss(dst, src); }
  void sqrtss(XMMRegister dst, Address src)        { Assembler::sqrtss(dst, src); }
  void sqrtss(XMMRegister dst, AddressLiteral src);

  void subsd(XMMRegister dst, XMMRegister src)    { Assembler::subsd(dst, src); }
  void subsd(XMMRegister dst, Address src)        { Assembler::subsd(dst, src); }
  void subsd(XMMRegister dst, AddressLiteral src);

  void subss(XMMRegister dst, XMMRegister src)    { Assembler::subss(dst, src); }
  void subss(XMMRegister dst, Address src)        { Assembler::subss(dst, src); }
  void subss(XMMRegister dst, AddressLiteral src);

  void ucomiss(XMMRegister dst, XMMRegister src) { Assembler::ucomiss(dst, src); }
  void ucomiss(XMMRegister dst, Address src)     { Assembler::ucomiss(dst, src); }
  void ucomiss(XMMRegister dst, AddressLiteral src);

  void ucomisd(XMMRegister dst, XMMRegister src) { Assembler::ucomisd(dst, src); }
  void ucomisd(XMMRegister dst, Address src)     { Assembler::ucomisd(dst, src); }
  void ucomisd(XMMRegister dst, AddressLiteral src);

  // Bitwise Logical XOR of Packed Double-Precision Floating-Point Values
  void xorpd(XMMRegister dst, XMMRegister src);
  void xorpd(XMMRegister dst, Address src)     { Assembler::xorpd(dst, src); }
  void xorpd(XMMRegister dst, AddressLiteral src);

  // Bitwise Logical XOR of Packed Single-Precision Floating-Point Values
  void xorps(XMMRegister dst, XMMRegister src);
  void xorps(XMMRegister dst, Address src)     { Assembler::xorps(dst, src); }
  void xorps(XMMRegister dst, AddressLiteral src);

  // Shuffle Bytes
  void pshufb(XMMRegister dst, XMMRegister src) { Assembler::pshufb(dst, src); }
  void pshufb(XMMRegister dst, Address src)     { Assembler::pshufb(dst, src); }
  void pshufb(XMMRegister dst, AddressLiteral src);
  // AVX 3-operands instructions

  void vaddsd(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vaddsd(dst, nds, src); }
  void vaddsd(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vaddsd(dst, nds, src); }
  void vaddsd(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vaddss(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vaddss(dst, nds, src); }
  void vaddss(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vaddss(dst, nds, src); }
  void vaddss(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vabsss(XMMRegister dst, XMMRegister nds, XMMRegister src, AddressLiteral negate_field, int vector_len);
  void vabssd(XMMRegister dst, XMMRegister nds, XMMRegister src, AddressLiteral negate_field, int vector_len);

  void vpaddb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpaddb(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  void vpaddw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpaddw(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  void vpbroadcastw(XMMRegister dst, XMMRegister src);

  void vpcmpeqb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpcmpeqw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);

  void vpmovzxbw(XMMRegister dst, Address src, int vector_len);
  void vpmovmskb(Register dst, XMMRegister src);

  void vpmullw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpmullw(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  void vpsubb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpsubb(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  void vpsubw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpsubw(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  void vpsraw(XMMRegister dst, XMMRegister nds, XMMRegister shift, int vector_len);
  void vpsraw(XMMRegister dst, XMMRegister nds, int shift, int vector_len);

  void vpsrlw(XMMRegister dst, XMMRegister nds, XMMRegister shift, int vector_len);
  void vpsrlw(XMMRegister dst, XMMRegister nds, int shift, int vector_len);

  void vpsllw(XMMRegister dst, XMMRegister nds, XMMRegister shift, int vector_len);
  void vpsllw(XMMRegister dst, XMMRegister nds, int shift, int vector_len);

  void vptest(XMMRegister dst, XMMRegister src);

  void punpcklbw(XMMRegister dst, XMMRegister src);
  void punpcklbw(XMMRegister dst, Address src) { Assembler::punpcklbw(dst, src); }

  void pshuflw(XMMRegister dst, XMMRegister src, int mode);
  void pshuflw(XMMRegister dst, Address src, int mode) { Assembler::pshuflw(dst, src, mode); }

  void vandpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) { Assembler::vandpd(dst, nds, src, vector_len); }
  void vandpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len)     { Assembler::vandpd(dst, nds, src, vector_len); }
  void vandpd(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len);

  void vandps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) { Assembler::vandps(dst, nds, src, vector_len); }
  void vandps(XMMRegister dst, XMMRegister nds, Address src, int vector_len)     { Assembler::vandps(dst, nds, src, vector_len); }
  void vandps(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len);

  void vdivsd(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vdivsd(dst, nds, src); }
  void vdivsd(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vdivsd(dst, nds, src); }
  void vdivsd(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vdivss(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vdivss(dst, nds, src); }
  void vdivss(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vdivss(dst, nds, src); }
  void vdivss(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vmulsd(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vmulsd(dst, nds, src); }
  void vmulsd(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vmulsd(dst, nds, src); }
  void vmulsd(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vmulss(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vmulss(dst, nds, src); }
  void vmulss(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vmulss(dst, nds, src); }
  void vmulss(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vsubsd(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vsubsd(dst, nds, src); }
  void vsubsd(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vsubsd(dst, nds, src); }
  void vsubsd(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vsubss(XMMRegister dst, XMMRegister nds, XMMRegister src) { Assembler::vsubss(dst, nds, src); }
  void vsubss(XMMRegister dst, XMMRegister nds, Address src)     { Assembler::vsubss(dst, nds, src); }
  void vsubss(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  void vnegatess(XMMRegister dst, XMMRegister nds, AddressLiteral src);
  void vnegatesd(XMMRegister dst, XMMRegister nds, AddressLiteral src);

  // AVX Vector instructions

  void vxorpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) { Assembler::vxorpd(dst, nds, src, vector_len); }
  void vxorpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) { Assembler::vxorpd(dst, nds, src, vector_len); }
  void vxorpd(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len);

  void vxorps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) { Assembler::vxorps(dst, nds, src, vector_len); }
  void vxorps(XMMRegister dst, XMMRegister nds, Address src, int vector_len) { Assembler::vxorps(dst, nds, src, vector_len); }
  void vxorps(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len);

  void vpxor(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
    if (UseAVX > 1 || (vector_len < 1)) // vpxor 256 bit is available only in AVX2
      Assembler::vpxor(dst, nds, src, vector_len);
    else
      Assembler::vxorpd(dst, nds, src, vector_len);
  }
  void vpxor(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
    if (UseAVX > 1 || (vector_len < 1)) // vpxor 256 bit is available only in AVX2
      Assembler::vpxor(dst, nds, src, vector_len);
    else
      Assembler::vxorpd(dst, nds, src, vector_len);
  }

  // Simple version for AVX2 256bit vectors
  void vpxor(XMMRegister dst, XMMRegister src) { Assembler::vpxor(dst, dst, src, true); }
  void vpxor(XMMRegister dst, Address src) { Assembler::vpxor(dst, dst, src, true); }

  // Move packed integer values from low 128 bit to hign 128 bit in 256 bit vector.
  void vinserti128h(XMMRegister dst, XMMRegister nds, XMMRegister src) {
    if (UseAVX > 1) // vinserti128h is available only in AVX2
      Assembler::vinserti128h(dst, nds, src);
    else
      Assembler::vinsertf128h(dst, nds, src);
  }

  // Carry-Less Multiplication Quadword
  void vpclmulldq(XMMRegister dst, XMMRegister nds, XMMRegister src) {
    // 0x00 - multiply lower 64 bits [0:63]
    Assembler::vpclmulqdq(dst, nds, src, 0x00);
  }
  void vpclmulhdq(XMMRegister dst, XMMRegister nds, XMMRegister src) {
    // 0x11 - multiply upper 64 bits [64:127]
    Assembler::vpclmulqdq(dst, nds, src, 0x11);
  }

  // Data

  void cmov32( Condition cc, Register dst, Address  src);
  void cmov32( Condition cc, Register dst, Register src);

  void cmov(   Condition cc, Register dst, Register src) { cmovptr(cc, dst, src); }

  void cmovptr(Condition cc, Register dst, Address  src) { LP64_ONLY(cmovq(cc, dst, src)) NOT_LP64(cmov32(cc, dst, src)); }
  void cmovptr(Condition cc, Register dst, Register src) { LP64_ONLY(cmovq(cc, dst, src)) NOT_LP64(cmov32(cc, dst, src)); }

  void movoop(Register dst, jobject obj);
  void movoop(Address dst, jobject obj);

  void mov_metadata(Register dst, Metadata* obj);
  void mov_metadata(Address dst, Metadata* obj);

  void movptr(ArrayAddress dst, Register src);
  // can this do an lea?
  void movptr(Register dst, ArrayAddress src);

  void movptr(Register dst, Address src);

#ifdef _LP64
  void movptr(Register dst, AddressLiteral src, Register scratch=rscratch1);
#else
  void movptr(Register dst, AddressLiteral src, Register scratch=noreg); // Scratch reg is ignored in 32-bit
#endif

  void movptr(Register dst, intptr_t src);
  void movptr(Register dst, Register src);
  void movptr(Address dst, intptr_t src);

  void movptr(Address dst, Register src);

  void movptr(Register dst, RegisterOrConstant src) {
    if (src.is_constant()) movptr(dst, src.as_constant());
    else                   movptr(dst, src.as_register());
  }

#ifdef _LP64
  // Generally the next two are only used for moving NULL
  // Although there are situations in initializing the mark word where
  // they could be used. They are dangerous.

  // They only exist on LP64 so that int32_t and intptr_t are not the same
  // and we have ambiguous declarations.

  void movptr(Address dst, int32_t imm32);
  void movptr(Register dst, int32_t imm32);
#endif // _LP64

  // to avoid hiding movl
  void mov32(AddressLiteral dst, Register src);
  void mov32(Register dst, AddressLiteral src);

  // to avoid hiding movb
  void movbyte(ArrayAddress dst, int src);

  // Import other mov() methods from the parent class or else
  // they will be hidden by the following overriding declaration.
  using Assembler::movdl;
  using Assembler::movq;
  void movdl(XMMRegister dst, AddressLiteral src);
  void movq(XMMRegister dst, AddressLiteral src);

  // Can push value or effective address
  void pushptr(AddressLiteral src);

  void pushptr(Address src) { LP64_ONLY(pushq(src)) NOT_LP64(pushl(src)); }
  void popptr(Address src) { LP64_ONLY(popq(src)) NOT_LP64(popl(src)); }

  void pushoop(jobject obj);
  void pushklass(Metadata* obj);

  // sign extend as need a l to ptr sized element
  void movl2ptr(Register dst, Address src) { LP64_ONLY(movslq(dst, src)) NOT_LP64(movl(dst, src)); }
  void movl2ptr(Register dst, Register src) { LP64_ONLY(movslq(dst, src)) NOT_LP64(if (dst != src) movl(dst, src)); }

  // C2 compiled method's prolog code.
  void verified_entry(int framesize, int stack_bang_size, bool fp_mode_24b);

  // clear memory of size 'cnt' qwords, starting at 'base'.
  void clear_mem(Register base, Register cnt, Register rtmp);

#ifdef COMPILER2
  void string_indexof_char(Register str1, Register cnt1, Register ch, Register result,
                           XMMRegister vec1, XMMRegister vec2, XMMRegister vec3, Register tmp);

  // IndexOf strings.
  // Small strings are loaded through stack if they cross page boundary.
  void string_indexof(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      int int_cnt2,  Register result,
                      XMMRegister vec, Register tmp,
                      int ae);

  // IndexOf for constant substrings with size >= 8 elements
  // which don't need to be loaded through stack.
  void string_indexofC8(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      int int_cnt2,  Register result,
                      XMMRegister vec, Register tmp,
                      int ae);

    // Smallest code: we don't need to load through stack,
    // check string tail.

  // helper function for string_compare
  void load_next_elements(Register elem1, Register elem2, Register str1, Register str2,
                          Address::ScaleFactor scale, Address::ScaleFactor scale1,
                          Address::ScaleFactor scale2, Register index, int ae);
  // Compare strings.
  void string_compare(Register str1, Register str2,
                      Register cnt1, Register cnt2, Register result,
                      XMMRegister vec1, int ae);

  // Search for Non-ASCII character (Negative byte value) in a byte array,
  // return true if it has any and false otherwise.
  void has_negatives(Register ary1, Register len,
                     Register result, Register tmp1,
                     XMMRegister vec1, XMMRegister vec2);

  // Compare char[] or byte[] arrays.
  void arrays_equals(bool is_array_equ, Register ary1, Register ary2,
                     Register limit, Register result, Register chr,
                     XMMRegister vec1, XMMRegister vec2, bool is_char);

#endif

  // Fill primitive arrays
  void generate_fill(BasicType t, bool aligned,
                     Register to, Register value, Register count,
                     Register rtmp, XMMRegister xtmp);

  void encode_iso_array(Register src, Register dst, Register len,
                        XMMRegister tmp1, XMMRegister tmp2, XMMRegister tmp3,
                        XMMRegister tmp4, Register tmp5, Register result);

#ifdef _LP64
  void add2_with_carry(Register dest_hi, Register dest_lo, Register src1, Register src2);
  void multiply_64_x_64_loop(Register x, Register xstart, Register x_xstart,
                             Register y, Register y_idx, Register z,
                             Register carry, Register product,
                             Register idx, Register kdx);
  void multiply_add_128_x_128(Register x_xstart, Register y, Register z,
                              Register yz_idx, Register idx,
                              Register carry, Register product, int offset);
  void multiply_128_x_128_bmi2_loop(Register y, Register z,
                                    Register carry, Register carry2,
                                    Register idx, Register jdx,
                                    Register yz_idx1, Register yz_idx2,
                                    Register tmp, Register tmp3, Register tmp4);
  void multiply_128_x_128_loop(Register x_xstart, Register y, Register z,
                               Register yz_idx, Register idx, Register jdx,
                               Register carry, Register product,
                               Register carry2);
  void multiply_to_len(Register x, Register xlen, Register y, Register ylen, Register z, Register zlen,
                       Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5);

  void square_rshift(Register x, Register len, Register z, Register tmp1, Register tmp3,
                     Register tmp4, Register tmp5, Register rdxReg, Register raxReg);
  void multiply_add_64_bmi2(Register sum, Register op1, Register op2, Register carry,
                            Register tmp2);
  void multiply_add_64(Register sum, Register op1, Register op2, Register carry,
                       Register rdxReg, Register raxReg);
  void add_one_64(Register z, Register zlen, Register carry, Register tmp1);
  void lshift_by_1(Register x, Register len, Register z, Register zlen, Register tmp1, Register tmp2,
                       Register tmp3, Register tmp4);
  void square_to_len(Register x, Register len, Register z, Register zlen, Register tmp1, Register tmp2,
                     Register tmp3, Register tmp4, Register tmp5, Register rdxReg, Register raxReg);

  void mul_add_128_x_32_loop(Register out, Register in, Register offset, Register len, Register tmp1,
               Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register rdxReg,
               Register raxReg);
  void mul_add(Register out, Register in, Register offset, Register len, Register k, Register tmp1,
               Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register rdxReg,
               Register raxReg);
#endif

  // CRC32 code for java.util.zip.CRC32::updateBytes() intrinsic.
  void update_byte_crc32(Register crc, Register val, Register table);
  void kernel_crc32(Register crc, Register buf, Register len, Register table, Register tmp);
  // CRC32C code for java.util.zip.CRC32C::updateBytes() intrinsic
  // Note on a naming convention:
  // Prefix w = register only used on a Westmere+ architecture
  // Prefix n = register only used on a Nehalem architecture
#ifdef _LP64
  void crc32c_ipl_alg4(Register in_out, uint32_t n,
                       Register tmp1, Register tmp2, Register tmp3);
#else
  void crc32c_ipl_alg4(Register in_out, uint32_t n,
                       Register tmp1, Register tmp2, Register tmp3,
                       XMMRegister xtmp1, XMMRegister xtmp2);
#endif
  void crc32c_pclmulqdq(XMMRegister w_xtmp1,
                        Register in_out,
                        uint32_t const_or_pre_comp_const_index, bool is_pclmulqdq_supported,
                        XMMRegister w_xtmp2,
                        Register tmp1,
                        Register n_tmp2, Register n_tmp3);
  void crc32c_rec_alt2(uint32_t const_or_pre_comp_const_index_u1, uint32_t const_or_pre_comp_const_index_u2, bool is_pclmulqdq_supported, Register in_out, Register in1, Register in2,
                       XMMRegister w_xtmp1, XMMRegister w_xtmp2, XMMRegister w_xtmp3,
                       Register tmp1, Register tmp2,
                       Register n_tmp3);
  void crc32c_proc_chunk(uint32_t size, uint32_t const_or_pre_comp_const_index_u1, uint32_t const_or_pre_comp_const_index_u2, bool is_pclmulqdq_supported,
                         Register in_out1, Register in_out2, Register in_out3,
                         Register tmp1, Register tmp2, Register tmp3,
                         XMMRegister w_xtmp1, XMMRegister w_xtmp2, XMMRegister w_xtmp3,
                         Register tmp4, Register tmp5,
                         Register n_tmp6);
  void crc32c_ipl_alg2_alt2(Register in_out, Register in1, Register in2,
                            Register tmp1, Register tmp2, Register tmp3,
                            Register tmp4, Register tmp5, Register tmp6,
                            XMMRegister w_xtmp1, XMMRegister w_xtmp2, XMMRegister w_xtmp3,
                            bool is_pclmulqdq_supported);
  // Fold 128-bit data chunk
  void fold_128bit_crc32(XMMRegister xcrc, XMMRegister xK, XMMRegister xtmp, Register buf, int offset);
  void fold_128bit_crc32(XMMRegister xcrc, XMMRegister xK, XMMRegister xtmp, XMMRegister xbuf);
  // Fold 8-bit data
  void fold_8bit_crc32(Register crc, Register table, Register tmp);
  void fold_8bit_crc32(XMMRegister crc, Register table, XMMRegister xtmp, Register tmp);

  // Compress char[] array to byte[].
  void char_array_compress(Register src, Register dst, Register len,
                           XMMRegister tmp1, XMMRegister tmp2, XMMRegister tmp3,
                           XMMRegister tmp4, Register tmp5, Register result);

  // Inflate byte[] array to char[].
  void byte_array_inflate(Register src, Register dst, Register len,
                          XMMRegister tmp1, Register tmp2);

#undef VIRTUAL

};

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

#endif // CPU_X86_VM_MACROASSEMBLER_X86_HPP

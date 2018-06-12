/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_INTERP_MASM_SPARC_HPP
#define CPU_SPARC_VM_INTERP_MASM_SPARC_HPP

#include "asm/macroAssembler.hpp"
#include "interpreter/invocationCounter.hpp"

// This file specializes the assember with interpreter-specific macros

typedef ByteSize (*OffsetFunction)(uint);

REGISTER_DECLARATION(     Register, Otos_i , O0); // tos for ints, etc
REGISTER_DECLARATION(     Register, Otos_l , O0); // for longs
REGISTER_DECLARATION(     Register, Otos_l1, O0); // for 1st part of longs
REGISTER_DECLARATION(     Register, Otos_l2, O1); // for 2nd part of longs
REGISTER_DECLARATION(FloatRegister, Ftos_f , F0); // for floats
REGISTER_DECLARATION(FloatRegister, Ftos_d , F0); // for doubles
REGISTER_DECLARATION(FloatRegister, Ftos_d1, F0); // for 1st part of double
REGISTER_DECLARATION(FloatRegister, Ftos_d2, F1); // for 2nd part of double

class InterpreterMacroAssembler: public MacroAssembler {
 protected:
  // Interpreter specific version of call_VM_base
    virtual void call_VM_leaf_base(
    Register java_thread,
    address  entry_point,
    int      number_of_arguments
  );

  virtual void call_VM_base(
    Register        oop_result,
    Register        java_thread,
    Register        last_java_sp,
    address         entry_point,
    int             number_of_arguments,
    bool            check_exception=true
  );

  // base routine for all dispatches
  void dispatch_base(TosState state, address* table);

 public:
  InterpreterMacroAssembler(CodeBuffer* c)
    : MacroAssembler(c) {}

 virtual void check_and_handle_popframe(Register scratch_reg);
 virtual void check_and_handle_earlyret(Register scratch_reg);

  void jump_to_entry(address entry);

  virtual void load_earlyret_value(TosState state);

  static const Address l_tmp ;
  static const Address d_tmp ;

  // helper routine for frame allocation/deallocation
  // compute the delta by which the caller's SP has to
  // be adjusted to accomodate for the non-argument
  // locals
  void compute_extra_locals_size_in_bytes(Register args_size, Register locals_size, Register delta);

  // dispatch routines
  void dispatch_prolog(TosState state, int step = 0);
  void dispatch_epilog(TosState state, int step = 0);
  void dispatch_only(TosState state);
  void dispatch_normal(TosState state);
  void dispatch_next(TosState state, int step = 0, bool generate_poll = false);
  void dispatch_next_noverify_oop(TosState state, int step = 0);
  void dispatch_via (TosState state, address* table);


  void narrow(Register result);

  // Removes the current activation (incl. unlocking of monitors).
  // Additionally this code is used for earlyReturn in which case we
  // want to skip throwing an exception and installing an exception.
  void remove_activation(TosState state,
                         bool throw_monitor_exception = true,
                         bool install_monitor_exception = true);

 protected:
  void dispatch_Lbyte_code(TosState state, address* table, int bcp_incr = 0, bool verify = true, bool generate_poll = false);

 public:
  // Super call_VM calls - correspond to MacroAssembler::call_VM(_leaf) calls
  void super_call_VM(Register thread_cache,
                     Register oop_result,
                     Register last_java_sp,
                     address entry_point,
                     Register arg_1,
                     Register arg_2,
                     bool check_exception = true);

  void super_call_VM_leaf(Register thread_cache, address entry_point, Register arg_1, Register arg_2);

  // Generate a subtype check: branch to ok_is_subtype if sub_klass is
  // a subtype of super_klass.  Blows registers tmp1, tmp2 and tmp3.
  void gen_subtype_check( Register sub_klass, Register super_klass, Register tmp1, Register tmp2, Register tmp3, Label &ok_is_subtype );

  // helpers for tossing exceptions
  void throw_if_not_1_icc( Condition ok_condition, Label& ok );
  void throw_if_not_1_xcc( Condition ok_condition, Label& ok );
  void throw_if_not_1_x  ( Condition ok_condition, Label& ok ); // chooses icc or xcc based on _LP64

  void throw_if_not_2( address throw_entry_point, Register Rscratch, Label& ok);

  void throw_if_not_icc( Condition ok_condition, address throw_entry_point, Register Rscratch );
  void throw_if_not_xcc( Condition ok_condition, address throw_entry_point, Register Rscratch );
  void throw_if_not_x  ( Condition ok_condition, address throw_entry_point, Register Rscratch );

  // helpers for expression stack

  void pop_i(     Register r = Otos_i);
  void pop_ptr(   Register r = Otos_i, Register scratch = O4);
  void pop_l(     Register r = Otos_l1);
  // G4_scratch and Lscratch are used at call sites!!
  void pop_f(FloatRegister f = Ftos_f,  Register scratch = G1_scratch);
  void pop_d(FloatRegister f = Ftos_d1, Register scratch = G1_scratch);

  void push_i(     Register r = Otos_i);
  void push_ptr(   Register r = Otos_i);
  void push_l(     Register r = Otos_l1);
  void push_f(FloatRegister f = Ftos_f);
  void push_d(FloatRegister f = Ftos_d1);


  void pop (TosState state);           // transition vtos -> state
  void push(TosState state);           // transition state -> vtos
  void empty_expression_stack();       // resets both Lesp and SP

#ifdef ASSERT
  void verify_sp(Register Rsp, Register Rtemp);
  void verify_esp(Register Resp);      // verify that Lesp points to a word in the temp stack
#endif // ASSERT

 public:
  void if_cmp(Condition cc, bool ptr_compare);

  // Load values from bytecode stream:

  enum signedOrNot { Signed, Unsigned };
  enum setCCOrNot  { set_CC,  dont_set_CC };

  void get_2_byte_integer_at_bcp( int         bcp_offset,
                                  Register    Rtmp,
                                  Register    Rdst,
                                  signedOrNot is_signed,
                                  setCCOrNot  should_set_CC = dont_set_CC );

  void get_4_byte_integer_at_bcp( int        bcp_offset,
                                  Register   Rtmp,
                                  Register   Rdst,
                                  setCCOrNot should_set_CC = dont_set_CC );

  // Note: "get_cache_and_index" really means "get the index, use it to get the cache entry, and throw away the index".
  void get_cache_and_index_at_bcp(Register cache, Register tmp, int bcp_offset, size_t index_size = sizeof(u2));
  void get_cache_and_index_and_bytecode_at_bcp(Register cache, Register temp, Register bytecode, int byte_no, int bcp_offset, size_t index_size = sizeof(u2));
  void get_cache_entry_pointer_at_bcp(Register cache, Register tmp, int bcp_offset, size_t index_size = sizeof(u2));
  // Note: This one does not fetch the cache.  The first argument is a temp which may be killed.
  void get_cache_index_at_bcp(Register temp, Register index, int bcp_offset, size_t index_size = sizeof(u2));

  // load cpool->resolved_references(index);
  void load_resolved_reference_at_index(Register result, Register index, Register tmp);

  // load cpool->resolved_klass_at(index)
  void load_resolved_klass_at_offset(Register Rcpool, Register Roffset, Register Rklass);

  // common code

  void field_offset_at(int n, Register tmp, Register dest, Register base);
  int  field_offset_at(Register object, address bcp, int offset);
  void fast_iaaccess(int n, address bcp);
  void fast_iaputfield(address bcp, bool do_store_check );

  void index_check(Register array, Register index, int index_shift, Register tmp, Register res);
  void index_check_without_pop(Register array, Register index, int index_shift, Register tmp, Register res);

  void get_const(Register Rdst);
  void get_constant_pool(Register Rdst);
  void get_constant_pool_cache(Register Rdst);
  void get_cpool_and_tags(Register Rcpool, Register Rtags);
  void is_a(Label& L);

  // Load compiled (i2c) or interpreter entry and call from interpreted
  void call_from_interpreter(Register target, Register scratch, Register Rret);

  // --------------------------------------------------

  void unlock_if_synchronized_method(TosState state, bool throw_monitor_exception = true, bool install_monitor_exception = true);

  void add_monitor_to_stack( bool stack_is_empty,
                             Register Rtemp,
                             Register Rtemp2 );

  // Load/store aligned in _LP64 but unaligned otherwise
  // These only apply to the Interpreter expression stack and locals!
  void  load_unaligned_double(Register r1, int offset,  FloatRegister d);
  void store_unaligned_double(FloatRegister d, Register r1, int offset );

  // Load/store aligned in _LP64 but unaligned otherwise
  void  load_unaligned_long(Register r1, int offset,  Register d);
  void store_unaligned_long(Register d, Register r1, int offset );

  void access_local_int( Register index, Register dst );
  void access_local_ptr( Register index, Register dst );
  void access_local_returnAddress( Register index, Register dst );
  void access_local_long( Register index, Register dst );
  void access_local_float( Register index, FloatRegister dst );
  void access_local_double( Register index, FloatRegister dst );
#ifdef ASSERT
  void check_for_regarea_stomp( Register Rindex, int offset, Register Rlimit, Register Rscratch, Register Rscratch1);
#endif // ASSERT
  void store_local_int( Register index, Register src );
  void store_local_ptr( Register index, Register src );
  void store_local_ptr( int n, Register src );
  void store_local_long( Register index, Register src );
  void store_local_float( Register index, FloatRegister src );
  void store_local_double( Register index, FloatRegister src );

  // Helpers for swap and dup
  void load_ptr(int n, Register val);
  void store_ptr(int n, Register val);

  // Helper for getting receiver in register.
  void load_receiver(Register param_count, Register recv);

  static int top_most_monitor_byte_offset(); // offset in bytes to top of monitor block
  Address top_most_monitor();
  void compute_stack_base( Register Rdest );

  void get_method_counters(Register method, Register Rcounters, Label& skip);
  void increment_invocation_counter( Register Rcounters, Register Rtmp, Register Rtmp2 );
  void increment_backedge_counter( Register Rcounters, Register Rtmp, Register Rtmp2 );
  void test_backedge_count_for_osr(Register backedge_count, Register method_counters, Register branch_bcp, Register Rtmp );

  // Object locking
  void lock_object  (Register lock_reg, Register obj_reg);
  void unlock_object(Register lock_reg);

  // Interpreter profiling operations
  void set_method_data_pointer();
  void set_method_data_pointer_for_bcp();
  void test_method_data_pointer(Label& zero_continue);
  void verify_method_data_pointer();
  void test_invocation_counter_for_mdp(Register invocation_count, Register method_counters, Register Rtmp, Label &profile_continue);

  void set_mdp_data_at(int constant, Register value);
  void increment_mdp_data_at(Address counter, Register bumped_count,
                             bool decrement = false);
  void increment_mdp_data_at(int constant, Register bumped_count,
                             bool decrement = false);
  void increment_mdp_data_at(Register reg, int constant,
                             Register bumped_count, Register scratch2,
                             bool decrement = false);
  void increment_mask_and_jump(Address counter_addr,
                               int increment, Address mask_addr,
                               Register scratch1, Register scratch2,
                               Condition cond, Label *where);
  void set_mdp_flag_at(int flag_constant, Register scratch);
  void test_mdp_data_at(int offset, Register value, Label& not_equal_continue,
                        Register scratch);

  void record_klass_in_profile(Register receiver, Register scratch, bool is_virtual_call);
  void record_klass_in_profile_helper(Register receiver, Register scratch,
                                      Label& done, bool is_virtual_call);
  void record_item_in_profile_helper(Register item,
                                     Register scratch, int start_row, Label& done, int total_rows,
                                     OffsetFunction item_offset_fn, OffsetFunction item_count_offset_fn,
                                     int non_profiled_offset);

  void update_mdp_by_offset(int offset_of_disp, Register scratch);
  void update_mdp_by_offset(Register reg, int offset_of_disp,
                            Register scratch);
  void update_mdp_by_constant(int constant);
  void update_mdp_for_ret(TosState state, Register return_bci);

  void profile_taken_branch(Register scratch, Register bumped_count);
  void profile_not_taken_branch(Register scratch);
  void profile_call(Register scratch);
  void profile_final_call(Register scratch);
  void profile_virtual_call(Register receiver, Register scratch, bool receiver_can_be_null = false);
  void profile_called_method(Register method, Register scratch) NOT_JVMCI_RETURN;
  void profile_ret(TosState state, Register return_bci, Register scratch);
  void profile_null_seen(Register scratch);
  void profile_typecheck(Register klass, Register scratch);
  void profile_typecheck_failed(Register scratch);
  void profile_switch_default(Register scratch);
  void profile_switch_case(Register index,
                           Register scratch1,
                           Register scratch2,
                           Register scratch3);

  void profile_obj_type(Register obj, const Address& mdo_addr, Register tmp);
  void profile_arguments_type(Register callee, Register tmp1, Register tmp2, bool is_virtual);
  void profile_return_type(Register ret, Register tmp1, Register tmp2);
  void profile_parameters_type(Register tmp1, Register tmp2, Register tmp3, Register tmp4);

  // Debugging
  void interp_verify_oop(Register reg, TosState state, const char * file, int line);    // only if +VerifyOops && state == atos
  void verify_oop_or_return_address(Register reg, Register rtmp); // for astore
  void verify_FPU(int stack_depth, TosState state = ftos); // only if +VerifyFPU  && (state == ftos || state == dtos)

  // support for JVMTI/Dtrace
  typedef enum { NotifyJVMTI, SkipNotifyJVMTI } NotifyMethodExitMode;
  void notify_method_entry();
  void notify_method_exit(
    bool save_result, TosState state, NotifyMethodExitMode mode);

  void save_return_value(TosState state, bool is_native_call);
  void restore_return_value(TosState state, bool is_native_call);

};

#endif // CPU_SPARC_VM_INTERP_MASM_SPARC_HPP

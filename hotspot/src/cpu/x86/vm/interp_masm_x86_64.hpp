/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// This file specializes the assember with interpreter-specific macros


class InterpreterMacroAssembler
  : public MacroAssembler {
 protected:
  // Interpreter specific version of call_VM_base
  virtual void call_VM_leaf_base(address entry_point,
                                 int number_of_arguments);

  virtual void call_VM_base(Register oop_result,
                            Register java_thread,
                            Register last_java_sp,
                            address  entry_point,
                            int number_of_arguments,
                            bool check_exceptions);

  virtual void check_and_handle_popframe(Register java_thread);
  virtual void check_and_handle_earlyret(Register java_thread);

  // base routine for all dispatches
  void dispatch_base(TosState state, address* table, bool verifyoop = true);

 public:
  InterpreterMacroAssembler(CodeBuffer* code)
    : MacroAssembler(code)
  {}

  void load_earlyret_value(TosState state);

  // Interpreter-specific registers
  void save_bcp()
  {
    movq(Address(rbp, frame::interpreter_frame_bcx_offset * wordSize), r13);
  }

  void restore_bcp()
  {
    movq(r13, Address(rbp, frame::interpreter_frame_bcx_offset * wordSize));
  }

  void restore_locals()
  {
    movq(r14, Address(rbp, frame::interpreter_frame_locals_offset * wordSize));
  }

  // Helpers for runtime call arguments/results
  void get_method(Register reg)
  {
    movq(reg, Address(rbp, frame::interpreter_frame_method_offset * wordSize));
  }

  void get_constant_pool(Register reg)
  {
    get_method(reg);
    movq(reg, Address(reg, methodOopDesc::constants_offset()));
  }

  void get_constant_pool_cache(Register reg)
  {
    get_constant_pool(reg);
    movq(reg, Address(reg, constantPoolOopDesc::cache_offset_in_bytes()));
  }

  void get_cpool_and_tags(Register cpool, Register tags)
  {
    get_constant_pool(cpool);
    movq(tags, Address(cpool, constantPoolOopDesc::tags_offset_in_bytes()));
  }

  void get_unsigned_2_byte_index_at_bcp(Register reg, int bcp_offset);
  void get_cache_and_index_at_bcp(Register cache, Register index,
                                  int bcp_offset);
  void get_cache_entry_pointer_at_bcp(Register cache, Register tmp,
                                      int bcp_offset);

  void pop_ptr(Register r = rax);
  void pop_i(Register r = rax);
  void pop_l(Register r = rax);
  void pop_f(XMMRegister r = xmm0);
  void pop_d(XMMRegister r = xmm0);
  void push_ptr(Register r = rax);
  void push_i(Register r = rax);
  void push_l(Register r = rax);
  void push_f(XMMRegister r = xmm0);
  void push_d(XMMRegister r = xmm0);

  void pop(TosState state); // transition vtos -> state
  void push(TosState state); // transition state -> vtos

  // Tagged stack support, pop and push both tag and value.
  void pop_ptr(Register r, Register tag);
  void push_ptr(Register r, Register tag);

  DEBUG_ONLY(void verify_stack_tag(frame::Tag t);)

  // Tagged stack helpers for swap and dup
  void load_ptr_and_tag(int n, Register val, Register tag);
  void store_ptr_and_tag(int n, Register val, Register tag);

  // Tagged Local support
  void tag_local(frame::Tag tag, int n);
  void tag_local(Register tag, int n);
  void tag_local(frame::Tag tag, Register idx);
  void tag_local(Register tag, Register idx);

#ifdef ASSERT
  void verify_local_tag(frame::Tag tag, int n);
  void verify_local_tag(frame::Tag tag, Register idx);
#endif // ASSERT

  void empty_expression_stack()
  {
    movq(rsp, Address(rbp, frame::interpreter_frame_monitor_block_top_offset *
                      wordSize));
    // NULL last_sp until next java call
    movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);
  }

  // Super call_VM calls - correspond to MacroAssembler::call_VM(_leaf) calls
  void super_call_VM_leaf(address entry_point);
  void super_call_VM_leaf(address entry_point, Register arg_1);
  void super_call_VM_leaf(address entry_point, Register arg_1, Register arg_2);
  void super_call_VM_leaf(address entry_point,
                          Register arg_1, Register arg_2, Register arg_3);

  // Generate a subtype check: branch to ok_is_subtype if sub_klass is
  // a subtype of super_klass.
  void gen_subtype_check( Register sub_klass, Label &ok_is_subtype );

  // Dispatching
  void dispatch_prolog(TosState state, int step = 0);
  void dispatch_epilog(TosState state, int step = 0);
  // dispatch via ebx (assume ebx is loaded already)
  void dispatch_only(TosState state);
  // dispatch normal table via ebx (assume ebx is loaded already)
  void dispatch_only_normal(TosState state);
  void dispatch_only_noverify(TosState state);
  // load ebx from [esi + step] and dispatch via ebx
  void dispatch_next(TosState state, int step = 0);
  // load ebx from [esi] and dispatch via ebx and table
  void dispatch_via (TosState state, address* table);

  // jump to an invoked target
  void jump_from_interpreted(Register method, Register temp);


  // Returning from interpreted functions
  //
  // Removes the current activation (incl. unlocking of monitors)
  // and sets up the return address.  This code is also used for
  // exception unwindwing. In that case, we do not want to throw
  // IllegalMonitorStateExceptions, since that might get us into an
  // infinite rethrow exception loop.
  // Additionally this code is used for popFrame and earlyReturn.
  // In popFrame case we want to skip throwing an exception,
  // installing an exception, and notifying jvmdi.
  // In earlyReturn case we only want to skip throwing an exception
  // and installing an exception.
  void remove_activation(TosState state, Register ret_addr,
                         bool throw_monitor_exception = true,
                         bool install_monitor_exception = true,
                         bool notify_jvmdi = true);

  // Object locking
  void lock_object  (Register lock_reg);
  void unlock_object(Register lock_reg);

  // Interpreter profiling operations
  void set_method_data_pointer_for_bcp();
  void test_method_data_pointer(Register mdp, Label& zero_continue);
  void verify_method_data_pointer();

  void set_mdp_data_at(Register mdp_in, int constant, Register value);
  void increment_mdp_data_at(Address data, bool decrement = false);
  void increment_mdp_data_at(Register mdp_in, int constant,
                             bool decrement = false);
  void increment_mdp_data_at(Register mdp_in, Register reg, int constant,
                             bool decrement = false);
  void set_mdp_flag_at(Register mdp_in, int flag_constant);
  void test_mdp_data_at(Register mdp_in, int offset, Register value,
                        Register test_value_out,
                        Label& not_equal_continue);

  void record_klass_in_profile(Register receiver, Register mdp,
                               Register reg2);
  void record_klass_in_profile_helper(Register receiver, Register mdp,
                                      Register reg2,
                                      int start_row, Label& done);

  void update_mdp_by_offset(Register mdp_in, int offset_of_offset);
  void update_mdp_by_offset(Register mdp_in, Register reg, int offset_of_disp);
  void update_mdp_by_constant(Register mdp_in, int constant);
  void update_mdp_for_ret(Register return_bci);

  void profile_taken_branch(Register mdp, Register bumped_count);
  void profile_not_taken_branch(Register mdp);
  void profile_call(Register mdp);
  void profile_final_call(Register mdp);
  void profile_virtual_call(Register receiver, Register mdp,
                            Register scratch2);
  void profile_ret(Register return_bci, Register mdp);
  void profile_null_seen(Register mdp);
  void profile_typecheck(Register mdp, Register klass, Register scratch);
  void profile_typecheck_failed(Register mdp);
  void profile_switch_default(Register mdp);
  void profile_switch_case(Register index_in_scratch, Register mdp,
                           Register scratch2);

  // Debugging
  // only if +VerifyOops && state == atos
  void verify_oop(Register reg, TosState state = atos);
  // only if +VerifyFPU  && (state == ftos || state == dtos)
  void verify_FPU(int stack_depth, TosState state = ftos);

  typedef enum { NotifyJVMTI, SkipNotifyJVMTI } NotifyMethodExitMode;

  // support for jvmti/dtrace
  void notify_method_entry();
  void notify_method_exit(TosState state, NotifyMethodExitMode mode);
};

/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "jvm.h"
#include "oops/inlineKlass.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature_cc.hpp"
#ifdef COMPILER2
#include "opto/compile.hpp"
#include "opto/node.hpp"
#endif

void MacroAssembler::skip_unpacked_fields(const GrowableArray<SigEntry>* sig, int& sig_index, VMRegPair* regs_from, int regs_from_count, int& from_index) {
  ScalarizedInlineArgsStream stream(sig, sig_index, regs_from, regs_from_count, from_index);
  VMReg reg;
  BasicType bt;
  while (stream.next(reg, bt)) {}
  sig_index = stream.sig_index();
  from_index = stream.regs_index();
}

bool MacroAssembler::is_reg_in_unpacked_fields(const GrowableArray<SigEntry>* sig, int sig_index, VMReg to, VMRegPair* regs_from, int regs_from_count, int from_index) {
  ScalarizedInlineArgsStream stream(sig, sig_index, regs_from, regs_from_count, from_index);
  VMReg reg;
  BasicType bt;
  while (stream.next(reg, bt)) {
    if (reg == to) {
      return true;
    }
  }
  return false;
}

void MacroAssembler::mark_reg_writable(const VMRegPair* regs, int num_regs, int reg_index, MacroAssembler::RegState* reg_state) {
  assert(0 <= reg_index && reg_index < num_regs, "sanity");
  VMReg from_reg = regs[reg_index].first();
  if (from_reg->is_valid()) {
    assert(from_reg->is_stack(), "reserved entries must be stack");
    reg_state[from_reg->value()] = MacroAssembler::reg_writable;
  }
}

MacroAssembler::RegState* MacroAssembler::init_reg_state(VMRegPair* regs, int num_regs, int sp_inc, int max_stack) {
  int max_reg = VMRegImpl::stack2reg(max_stack)->value();
  MacroAssembler::RegState* reg_state = NEW_RESOURCE_ARRAY(MacroAssembler::RegState, max_reg);

  // Make all writable
  for (int i = 0; i < max_reg; ++i) {
    reg_state[i] = MacroAssembler::reg_writable;
  }
  // Set all source registers/stack slots to readonly to prevent accidental overwriting
  for (int i = 0; i < num_regs; ++i) {
    VMReg reg = regs[i].first();
    if (!reg->is_valid()) continue;
    if (reg->is_stack()) {
      // Update source stack location by adding stack increment
      reg = VMRegImpl::stack2reg(reg->reg2stack() + sp_inc/VMRegImpl::stack_slot_size);
      regs[i] = reg;
    }
    assert(reg->value() >= 0 && reg->value() < max_reg, "reg value out of bounds");
    reg_state[reg->value()] = MacroAssembler::reg_readonly;
  }
  return reg_state;
}

#ifdef COMPILER2
int MacroAssembler::unpack_inline_args(Compile* C, bool receiver_only) {
  assert(C->has_scalarized_args(), "inline type argument scalarization is disabled");
  Method* method = C->method()->get_Method();
  const GrowableArray<SigEntry>* sig = method->adapter()->get_sig_cc();
  assert(sig != nullptr, "must have scalarized signature");

  // Get unscalarized calling convention
  BasicType* sig_bt = NEW_RESOURCE_ARRAY(BasicType, 256);
  int args_passed = 0;
  if (!method->is_static()) {
    sig_bt[args_passed++] = T_OBJECT;
  }
  if (!receiver_only) {
    for (SignatureStream ss(method->signature()); !ss.at_return_type(); ss.next()) {
      BasicType bt = ss.type();
      sig_bt[args_passed++] = bt;
      if (type2size[bt] == 2) {
        sig_bt[args_passed++] = T_VOID;
      }
    }
  } else {
    // Only unpack the receiver, all other arguments are already scalarized
    InstanceKlass* holder = method->method_holder();
    int rec_len = (holder->is_inline_klass() && method->is_scalarized_arg(0)) ? InlineKlass::cast(holder)->extended_sig()->length() : 1;
    // Copy scalarized signature but skip receiver and inline type delimiters
    for (int i = 0; i < sig->length(); i++) {
      if (SigEntry::skip_value_delimiters(sig, i) && rec_len <= 0) {
        sig_bt[args_passed++] = sig->at(i)._bt;
      }
      rec_len--;
    }
  }
  VMRegPair* regs = NEW_RESOURCE_ARRAY(VMRegPair, args_passed);
  int args_on_stack = SharedRuntime::java_calling_convention(sig_bt, regs, args_passed);

  // Get scalarized calling convention
  int args_passed_cc = SigEntry::fill_sig_bt(sig, sig_bt);
  VMRegPair* regs_cc = NEW_RESOURCE_ARRAY(VMRegPair, sig->length());
  int args_on_stack_cc = SharedRuntime::java_calling_convention(sig_bt, regs_cc, args_passed_cc);

  // Check if we need to extend the stack for unpacking
  int sp_inc = 0;
  if (args_on_stack_cc > args_on_stack) {
    sp_inc = extend_stack_for_inline_args(args_on_stack_cc);
  }
  shuffle_inline_args(false, receiver_only, sig,
                      args_passed, args_on_stack, regs,           // from
                      args_passed_cc, args_on_stack_cc, regs_cc,  // to
                      sp_inc, noreg);
  return sp_inc;
}
#endif // COMPILER2

void MacroAssembler::shuffle_inline_args(bool is_packing, bool receiver_only,
                                         const GrowableArray<SigEntry>* sig,
                                         int args_passed, int args_on_stack, VMRegPair* regs,
                                         int args_passed_to, int args_on_stack_to, VMRegPair* regs_to,
                                         int sp_inc, Register val_array) {
  int max_stack = MAX2(args_on_stack + sp_inc/VMRegImpl::stack_slot_size, args_on_stack_to);
  RegState* reg_state = init_reg_state(regs, args_passed, sp_inc, max_stack);

  // Emit code for packing/unpacking inline type arguments
  // We try multiple times and eventually start spilling to resolve (circular) dependencies
  bool done = (args_passed_to == 0);
  for (int i = 0; i < 2*args_passed_to && !done; ++i) {
    done = true;
    bool spill = (i > args_passed_to); // Start spilling?
    // Iterate over all arguments (when unpacking, do in reverse)
    int step = is_packing ? 1 : -1;
    int from_index    = is_packing ? 0 : args_passed      - 1;
    int to_index      = is_packing ? 0 : args_passed_to   - 1;
    int sig_index     = is_packing ? 0 : sig->length()    - 1;
    int sig_index_end = is_packing ? sig->length() : -1;
    int vtarg_index = 0;
    for (; sig_index != sig_index_end; sig_index += step) {
      assert(0 <= sig_index && sig_index < sig->length(), "index out of bounds");
      if (spill) {
        // This call returns true IFF we should keep trying to spill in this round.
        spill = shuffle_inline_args_spill(is_packing, sig, sig_index, regs, from_index, args_passed,
                                          reg_state);
      }
      BasicType bt = sig->at(sig_index)._bt;
      if (SigEntry::skip_value_delimiters(sig, sig_index)) {
        VMReg from_reg = regs[from_index].first();
        if (from_reg->is_valid()) {
          done &= move_helper(from_reg, regs_to[to_index].first(), bt, reg_state);
        } else {
          // halves of T_LONG or T_DOUBLE
          assert(bt == T_VOID, "unexpected basic type");
        }
        to_index += step;
        from_index += step;
      } else if (is_packing) {
        assert(val_array != noreg, "must be");
        VMReg reg_to = regs_to[to_index].first();
        done &= pack_inline_helper(sig, sig_index, vtarg_index,
                                   regs, args_passed, from_index, reg_to,
                                   reg_state, val_array);
        vtarg_index++;
        to_index++;
      } else if (!receiver_only || (from_index == 0 && bt == T_VOID)) {
        VMReg from_reg = regs[from_index].first();
        done &= unpack_inline_helper(sig, sig_index,
                                     from_reg, from_index, regs_to, args_passed_to, to_index,
                                     reg_state);
        if (from_index == -1 && sig_index != 0) {
          // This can happen when we are confusing an empty inline type argument which is
          // not counted in the scalarized signature for the receiver. Just ignore it.
          assert(receiver_only, "sanity");
          from_index = 0;
        }
      }
    }
  }
  guarantee(done, "Could not resolve circular dependency when shuffling inline type arguments");
}

bool MacroAssembler::shuffle_inline_args_spill(bool is_packing, const GrowableArray<SigEntry>* sig, int sig_index,
                                               VMRegPair* regs_from, int from_index, int regs_from_count, RegState* reg_state) {
  VMReg reg;
  if (!is_packing || SigEntry::skip_value_delimiters(sig, sig_index)) {
    reg = regs_from[from_index].first();
    if (!reg->is_valid() || reg_state[reg->value()] != reg_readonly) {
      // Spilling this won't break cycles
      return true;
    }
  } else {
    ScalarizedInlineArgsStream stream(sig, sig_index, regs_from, regs_from_count, from_index);
    VMReg from_reg;
    BasicType bt;
    bool found = false;
    while (stream.next(from_reg, bt)) {
      reg = from_reg;
      assert(from_reg->is_valid(), "must be");
      if (reg_state[from_reg->value()] == reg_readonly) {
        found = true;
        break;
      }
    }
    if (!found) {
      // Spilling fields in this inline type arg won't break cycles
      return true;
    }
  }

  // Spill argument to be able to write the source and resolve circular dependencies
  VMReg spill_reg = spill_reg_for(reg);
  if (reg_state[spill_reg->value()] == reg_readonly) {
    // We have already spilled (in previous round). The spilled register should be consumed by this round.
  } else {
    bool res = move_helper(reg, spill_reg, T_DOUBLE, reg_state);
    assert(res, "Spilling should not fail");
    // Set spill_reg as new source and update state
    reg = spill_reg;
    regs_from[from_index].set1(reg);
    reg_state[reg->value()] = reg_readonly;
  }

  return false; // Do not spill again in this round
}

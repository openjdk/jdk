/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "foreignGlobals.hpp"
#include "classfile/javaClasses.hpp"
#include "memory/resourceArea.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "runtime/jniHandles.inline.hpp"

#define FOREIGN_ABI "jdk/internal/foreign/abi/"

const CallRegs ForeignGlobals::parse_call_regs(jobject jconv) {
  oop conv_oop = JNIHandles::resolve_non_null(jconv);
  objArrayOop arg_regs_oop = jdk_internal_foreign_abi_CallConv::argRegs(conv_oop);
  objArrayOop ret_regs_oop = jdk_internal_foreign_abi_CallConv::retRegs(conv_oop);
  int num_args = arg_regs_oop->length();
  int num_rets = ret_regs_oop->length();
  CallRegs result(num_args, num_rets);

  for (int i = 0; i < num_args; i++) {
    result._arg_regs.push(parse_vmstorage(arg_regs_oop->obj_at(i)));
  }

  for (int i = 0; i < num_rets; i++) {
    result._ret_regs.push(parse_vmstorage(ret_regs_oop->obj_at(i)));
  }

  return result;
}

VMStorage ForeignGlobals::parse_vmstorage(oop storage) {
  jbyte type = jdk_internal_foreign_abi_VMStorage::type(storage);
  jshort segment_mask_or_size = jdk_internal_foreign_abi_VMStorage::segment_mask_or_size(storage);
  jint index_or_offset = jdk_internal_foreign_abi_VMStorage::index_or_offset(storage);

  RegType rType = static_cast<RegType>(type);
  if (rType == VMStorage::stack_type()) {
    return VMStorage::stack_storage(segment_mask_or_size, index_or_offset);
  } else {
    return VMStorage::reg_storage(rType, segment_mask_or_size, index_or_offset);
  }
}

int RegSpiller::compute_spill_area(const GrowableArray<VMStorage>& regs) {
  int result_size = 0;
  for (int i = 0; i < regs.length(); i++) {
    result_size += pd_reg_size(regs.at(i));
  }
  return result_size;
}

void RegSpiller::generate(MacroAssembler* masm, int rsp_offset, bool spill) const {
  assert(rsp_offset != -1, "rsp_offset should be set");
  int offset = rsp_offset;
  for (int i = 0; i < _regs.length(); i++) {
    VMStorage reg = _regs.at(i);
    if (spill) {
      pd_store_reg(masm, offset, reg);
    } else {
      pd_load_reg(masm, offset, reg);
    }
    offset += pd_reg_size(reg);
  }
}

void ArgumentShuffle::print_on(outputStream* os) const {
  os->print_cr("Argument shuffle {");
  for (int i = 0; i < _moves.length(); i++) {
    Move move = _moves.at(i);
    BasicType arg_bt   = move.bt;
    VMStorage from_reg = move.from;
    VMStorage to_reg   = move.to;

    os->print("Move a %s from ", null_safe_string(type2name(arg_bt)));
    from_reg.print_on(os);
    os->print(" to ");
    to_reg.print_on(os);
    os->print_cr("");
  }
  os->print_cr("Stack argument bytes: %d", _out_arg_bytes);
  os->print_cr("}");
}

int NativeCallingConvention::calling_convention(const BasicType* sig_bt, VMStorage* out_regs, int num_args) const {
  int src_pos = 0;
  uint32_t max_stack_offset = 0;
  for (int i = 0; i < num_args; i++) {
    switch (sig_bt[i]) {
      case T_BOOLEAN:
      case T_CHAR:
      case T_BYTE:
      case T_SHORT:
      case T_INT:
      case T_FLOAT: {
        VMStorage reg = _input_regs.at(src_pos++);
        out_regs[i] = reg;
        if (reg.is_stack())
          max_stack_offset = MAX2(max_stack_offset, reg.offset() + reg.stack_size());
        break;
      }
      case T_LONG:
      case T_DOUBLE: {
        assert((i + 1) < num_args && sig_bt[i + 1] == T_VOID, "expecting half");
        VMStorage reg = _input_regs.at(src_pos++);
        out_regs[i] = reg;
        if (reg.is_stack())
          max_stack_offset = MAX2(max_stack_offset, reg.offset() + reg.stack_size());
        break;
      }
      case T_VOID: // Halves of longs and doubles
        assert(i != 0 && (sig_bt[i - 1] == T_LONG || sig_bt[i - 1] == T_DOUBLE), "expecting half");
        out_regs[i] = VMStorage::invalid();
        break;
      default:
        ShouldNotReachHere();
        break;
    }
  }
  return align_up(max_stack_offset, 8);
}

int JavaCallingConvention::calling_convention(const BasicType* sig_bt, VMStorage* regs, int num_args) const {
  VMRegPair* vm_regs = NEW_RESOURCE_ARRAY(VMRegPair, num_args);
  int slots = SharedRuntime::java_calling_convention(sig_bt, vm_regs, num_args);
  for (int i = 0; i < num_args; i++) {
    VMRegPair pair = vm_regs[i];
    // note, we ignore second here. Signature should consist of register-size values. So there should be
    // no need for multi-register pairs.
    //assert(!pair.first()->is_valid() || pair.is_single_reg(), "must be: %s");
    regs[i] = as_VMStorage(pair.first());
  }
  return slots << LogBytesPerInt;
}

class ComputeMoveOrder: public StackObj {
  class MoveOperation: public ResourceObj {
    friend class ComputeMoveOrder;
   private:
    VMStorage        _src;
    VMStorage        _dst;
    bool             _processed;
    MoveOperation*  _next;
    MoveOperation*  _prev;
    BasicType       _bt;

    static int get_id(VMStorage r) {
      assert((r.index_or_offset() & 0xFF000000) == 0, "index or offset too large");
      // assuming mask and size doesn't matter for now
      return ((int) r.type()) | (r.index_or_offset() << 8);
    }

   public:
    MoveOperation(VMStorage src, VMStorage dst, BasicType bt):
      _src(src), _dst(dst), _processed(false), _next(NULL), _prev(NULL), _bt(bt) {}

    int src_id() const          { return get_id(_src); }
    int dst_id() const          { return get_id(_dst); }
    MoveOperation* next() const { return _next; }
    MoveOperation* prev() const { return _prev; }
    void set_processed()        { _processed = true; }
    bool is_processed() const   { return _processed; }

    // insert
    void break_cycle(VMStorage temp_register) {
      // create a new store following the last store
      // to move from the temp_register to the original
      MoveOperation* new_store = new MoveOperation(temp_register, _dst, _bt);

      // break the cycle of links and insert new_store at the end
      // break the reverse link.
      MoveOperation* p = prev();
      assert(p->next() == this, "must be");
      _prev = NULL;
      p->_next = new_store;
      new_store->_prev = p;

      // change the original store to save it's value in the temp.
      _dst = temp_register;
    }

    void link(GrowableArray<MoveOperation*>& killer) {
      // link this store in front the store that it depends on
      MoveOperation* n = killer.at_grow(src_id(), NULL);
      if (n != NULL) {
        assert(_next == NULL && n->_prev == NULL, "shouldn't have been set yet");
        _next = n;
        n->_prev = this;
      }
    }

    Move as_move() {
      return {_bt, _src, _dst};
    }
  };

 private:
  int _total_in_args;
  const VMStorage* _in_regs;
  int _total_out_args;
  const VMStorage* _out_regs;
  const BasicType* _in_sig_bt;
  VMStorage _tmp_vmreg;
  GrowableArray<MoveOperation*> _edges;
  GrowableArray<Move> _moves;

 public:
  ComputeMoveOrder(int total_in_args, const VMStorage* in_regs, int total_out_args, VMStorage* out_regs,
                   const BasicType* in_sig_bt, VMStorage tmp_vmreg) :
      _total_in_args(total_in_args),
      _in_regs(in_regs),
      _total_out_args(total_out_args),
      _out_regs(out_regs),
      _in_sig_bt(in_sig_bt),
      _tmp_vmreg(tmp_vmreg),
      _edges(total_in_args),
      _moves(total_in_args) {
  }

  void compute() {
    assert(_total_out_args >= _total_in_args, "can only add prefix args");
    // Note that total_out_args args can be greater than total_in_args in the case of upcalls.
    // There will be a leading MH receiver arg in the out args in that case.
    //
    // Leading args in the out args will be ignored below because we iterate from the end of
    // the register arrays until !(in_idx >= 0), and total_in_args is smaller.
    //
    // Stub code adds a move for the receiver to j_rarg0 (and potential other prefix args) manually.
    for (int in_idx = _total_in_args - 1, out_idx = _total_out_args - 1; in_idx >= 0; in_idx--, out_idx--) {
      BasicType bt = _in_sig_bt[in_idx];
      assert(bt != T_ARRAY, "array not expected");
      VMStorage in_reg = _in_regs[in_idx];
      VMStorage out_reg = _out_regs[out_idx];

      if (out_reg.is_stack()) {
        // Move operations where the dest is the stack can all be
        // scheduled first since they can't interfere with the other moves.
        // The input and output stack spaces are distinct from each other.
        Move move{bt, in_reg, out_reg};
        _moves.push(move);
      } else if (in_reg == out_reg
                 || bt == T_VOID) {
        // 1. Can skip non-stack identity moves.
        //
        // 2. Upper half of long or double (T_VOID).
        //    Don't need to do anything.
        continue;
      } else {
        _edges.append(new MoveOperation(in_reg, out_reg, bt));
      }
    }
    // Break any cycles in the register moves and emit the in the
    // proper order.
    compute_store_order(_tmp_vmreg);
  }

  // Walk the edges breaking cycles between moves.  The result list
  // can be walked in order to produce the proper set of loads
  void compute_store_order(VMStorage temp_register) {
    // Record which moves kill which values
    // FIXME should be a map
    GrowableArray<MoveOperation*> killer; // essentially a map of register id -> MoveOperation*
    for (int i = 0; i < _edges.length(); i++) {
      MoveOperation* s = _edges.at(i);
      assert(killer.at_grow(s->dst_id(), NULL) == NULL,
             "multiple moves with the same register as destination");
      killer.at_put_grow(s->dst_id(), s, NULL);
    }
    assert(killer.at_grow(MoveOperation::get_id(temp_register), NULL) == NULL,
           "make sure temp isn't in the registers that are killed");

    // create links between loads and stores
    for (int i = 0; i < _edges.length(); i++) {
      _edges.at(i)->link(killer);
    }

    // at this point, all the move operations are chained together
    // in one or more doubly linked lists.  Processing them backwards finds
    // the beginning of the chain, forwards finds the end.  If there's
    // a cycle it can be broken at any point,  so pick an edge and walk
    // backward until the list ends or we end where we started.
    for (int e = 0; e < _edges.length(); e++) {
      MoveOperation* s = _edges.at(e);
      if (!s->is_processed()) {
        MoveOperation* start = s;
        // search for the beginning of the chain or cycle
        while (start->prev() != NULL && start->prev() != s) {
          start = start->prev();
        }
        if (start->prev() == s) {
          start->break_cycle(temp_register);
        }
        // walk the chain forward inserting to store list
        while (start != NULL) {
          _moves.push(start->as_move());

          start->set_processed();
          start = start->next();
        }
      }
    }
  }

public:
  static GrowableArray<Move> compute_move_order(int total_in_args, const VMStorage* in_regs,
                                                int total_out_args, VMStorage* out_regs,
                                                const BasicType* in_sig_bt, VMStorage tmp_vmreg) {
    ComputeMoveOrder cmo(total_in_args, in_regs, total_out_args, out_regs, in_sig_bt, tmp_vmreg);
    cmo.compute();
    return cmo._moves;
  }
};

ArgumentShuffle::ArgumentShuffle(
    BasicType* in_sig_bt,
    int num_in_args,
    BasicType* out_sig_bt,
    int num_out_args,
    const CallingConventionClosure* input_conv,
    const CallingConventionClosure* output_conv,
    VMStorage shuffle_temp) {

  VMStorage* in_regs = NEW_RESOURCE_ARRAY(VMStorage, num_in_args);
  input_conv->calling_convention(in_sig_bt, in_regs, num_in_args);

  VMStorage* out_regs = NEW_RESOURCE_ARRAY(VMStorage, num_out_args);
  _out_arg_bytes = output_conv->calling_convention(out_sig_bt, out_regs, num_out_args);

  _moves = ComputeMoveOrder::compute_move_order(num_in_args, in_regs,
                                                num_out_args, out_regs,
                                                in_sig_bt, shuffle_temp);
}

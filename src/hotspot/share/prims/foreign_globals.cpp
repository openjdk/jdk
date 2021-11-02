/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "foreign_globals.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/resourceArea.hpp"
#include "prims/foreign_globals.inline.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/fieldDescriptor.inline.hpp"

#define FOREIGN_ABI "jdk/internal/foreign/abi/"

static int field_offset(InstanceKlass* cls, const char* fieldname, Symbol* sigsym) {
  TempNewSymbol fieldnamesym = SymbolTable::new_symbol(fieldname, (int)strlen(fieldname));
  fieldDescriptor fd;
  bool success = cls->find_field(fieldnamesym, sigsym, false, &fd);
  assert(success, "Field not found");
  return fd.offset();
}

static InstanceKlass* find_InstanceKlass(const char* name, TRAPS) {
  TempNewSymbol sym = SymbolTable::new_symbol(name, (int)strlen(name));
  Klass* k = SystemDictionary::resolve_or_null(sym, Handle(), Handle(), THREAD);
  assert(k != nullptr, "Can not find class: %s", name);
  return InstanceKlass::cast(k);
}

const ForeignGlobals& ForeignGlobals::instance() {
  static ForeignGlobals globals; // thread-safe lazy init-once (since C++11)
  return globals;
}

const ABIDescriptor ForeignGlobals::parse_abi_descriptor(jobject jabi) {
  return instance().parse_abi_descriptor_impl(jabi);
}
const BufferLayout ForeignGlobals::parse_buffer_layout(jobject jlayout) {
  return instance().parse_buffer_layout_impl(jlayout);
}

const CallRegs ForeignGlobals::parse_call_regs(jobject jconv) {
  return instance().parse_call_regs_impl(jconv);
}

ForeignGlobals::ForeignGlobals() {
  JavaThread* current_thread = JavaThread::current();
  ResourceMark rm(current_thread);

  // ABIDescriptor
  InstanceKlass* k_ABI = find_InstanceKlass(FOREIGN_ABI "ABIDescriptor", current_thread);
  const char* strVMSArrayArray = "[[L" FOREIGN_ABI "VMStorage;";
  Symbol* symVMSArrayArray = SymbolTable::new_symbol(strVMSArrayArray);
  ABI.inputStorage_offset = field_offset(k_ABI, "inputStorage", symVMSArrayArray);
  ABI.outputStorage_offset = field_offset(k_ABI, "outputStorage", symVMSArrayArray);
  ABI.volatileStorage_offset = field_offset(k_ABI, "volatileStorage", symVMSArrayArray);
  ABI.stackAlignment_offset = field_offset(k_ABI, "stackAlignment", vmSymbols::int_signature());
  ABI.shadowSpace_offset = field_offset(k_ABI, "shadowSpace", vmSymbols::int_signature());
  const char* strVMS = "L" FOREIGN_ABI "VMStorage;";
  Symbol* symVMS = SymbolTable::new_symbol(strVMS);
  ABI.targetAddrStorage_offset = field_offset(k_ABI, "targetAddrStorage", symVMS);
  ABI.retBufAddrStorage_offset = field_offset(k_ABI, "retBufAddrStorage", symVMS);

  // VMStorage
  InstanceKlass* k_VMS = find_InstanceKlass(FOREIGN_ABI "VMStorage", current_thread);
  VMS.index_offset = field_offset(k_VMS, "index", vmSymbols::int_signature());
  VMS.type_offset = field_offset(k_VMS, "type", vmSymbols::int_signature());

  // BufferLayout
  InstanceKlass* k_BL = find_InstanceKlass(FOREIGN_ABI "BufferLayout", current_thread);
  BL.size_offset = field_offset(k_BL, "size", vmSymbols::long_signature());
  BL.arguments_next_pc_offset = field_offset(k_BL, "arguments_next_pc", vmSymbols::long_signature());
  BL.stack_args_bytes_offset = field_offset(k_BL, "stack_args_bytes", vmSymbols::long_signature());
  BL.stack_args_offset = field_offset(k_BL, "stack_args", vmSymbols::long_signature());
  BL.input_type_offsets_offset = field_offset(k_BL, "input_type_offsets", vmSymbols::long_array_signature());
  BL.output_type_offsets_offset = field_offset(k_BL, "output_type_offsets", vmSymbols::long_array_signature());

  // CallRegs
  const char* strVMSArray = "[L" FOREIGN_ABI "VMStorage;";
  Symbol* symVMSArray = SymbolTable::new_symbol(strVMSArray);
  InstanceKlass* k_CC = find_InstanceKlass(FOREIGN_ABI "ProgrammableUpcallHandler$CallRegs", current_thread);
  CallConvOffsets.arg_regs_offset = field_offset(k_CC, "argRegs", symVMSArray);
  CallConvOffsets.ret_regs_offset = field_offset(k_CC, "retRegs", symVMSArray);
}

VMReg ForeignGlobals::parse_vmstorage(oop storage) const {
  jint index = storage->int_field(VMS.index_offset);
  jint type = storage->int_field(VMS.type_offset);
  return vmstorage_to_vmreg(type, index);
}

int RegSpiller::compute_spill_area(const VMReg* regs, int num_regs) {
  int result_size = 0;
  for (int i = 0; i < num_regs; i++) {
    result_size += pd_reg_size(regs[i]);
  }
  return result_size;
}

void RegSpiller::generate(MacroAssembler* masm, int rsp_offset, bool spill) const {
  assert(rsp_offset != -1, "rsp_offset should be set");
  int offset = rsp_offset;
  for (int i = 0; i < _num_regs; i++) {
    VMReg reg = _regs[i];
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
    BasicType arg_bt     = move.bt;
    VMRegPair from_vmreg = move.from;
    VMRegPair to_vmreg   = move.to;

    os->print("Move a %s from (", null_safe_string(type2name(arg_bt)));
    from_vmreg.first()->print_on(os);
    os->print(",");
    from_vmreg.second()->print_on(os);
    os->print(") to (");
    to_vmreg.first()->print_on(os);
    os->print(",");
    to_vmreg.second()->print_on(os);
    os->print_cr(")");
  }
  os->print_cr("Stack argument slots: %d", _out_arg_stack_slots);
  os->print_cr("}");
}

int NativeCallConv::calling_convention(BasicType* sig_bt, VMRegPair* out_regs, int num_args) const {
  int src_pos = 0;
  int stk_slots = 0;
  for (int i = 0; i < num_args; i++) {
    switch (sig_bt[i]) {
      case T_BOOLEAN:
      case T_CHAR:
      case T_BYTE:
      case T_SHORT:
      case T_INT:
      case T_FLOAT: {
        assert(src_pos < _input_regs_length, "oob");
        VMReg reg = _input_regs[src_pos++];
        out_regs[i].set1(reg);
        if (reg->is_stack())
          stk_slots += 2;
        break;
      }
      case T_LONG:
      case T_DOUBLE: {
        assert((i + 1) < num_args && sig_bt[i + 1] == T_VOID, "expecting half");
        assert(src_pos < _input_regs_length, "oob");
        VMReg reg = _input_regs[src_pos++];
        out_regs[i].set2(reg);
        if (reg->is_stack())
          stk_slots += 2;
        break;
      }
      case T_VOID: // Halves of longs and doubles
        assert(i != 0 && (sig_bt[i - 1] == T_LONG || sig_bt[i - 1] == T_DOUBLE), "expecting half");
        out_regs[i].set_bad();
        break;
      default:
        ShouldNotReachHere();
        break;
    }
  }
  return stk_slots;
}

// based on ComputeMoveOrder from x86_64 shared runtime code.
// with some changes.
class ForeignCMO: public StackObj {
  class MoveOperation: public ResourceObj {
    friend class ForeignCMO;
   private:
    VMRegPair        _src;
    VMRegPair        _dst;
    bool             _processed;
    MoveOperation*  _next;
    MoveOperation*  _prev;
    BasicType        _bt;

    static int get_id(VMRegPair r) {
      return r.first()->value();
    }

   public:
    MoveOperation(VMRegPair src, VMRegPair dst, BasicType bt):
      _src(src)
    , _dst(dst)
    , _processed(false)
    , _next(NULL)
    , _prev(NULL)
    , _bt(bt) {
    }

    int src_id() const          { return get_id(_src); }
    int dst_id() const          { return get_id(_dst); }
    MoveOperation* next() const { return _next; }
    MoveOperation* prev() const { return _prev; }
    void set_processed()        { _processed = true; }
    bool is_processed() const   { return _processed; }

    // insert
    void break_cycle(VMRegPair temp_register) {
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
  GrowableArray<MoveOperation*> _edges;
  GrowableArray<Move> _moves;

 public:
  ForeignCMO(int total_in_args, const VMRegPair* in_regs, int total_out_args, VMRegPair* out_regs,
             const BasicType* in_sig_bt, VMRegPair tmp_vmreg) : _edges(total_in_args), _moves(total_in_args) {
    assert(total_out_args >= total_in_args, "can only add prefix args");
    // Note that total_out_args args can be greater than total_in_args in the case of upcalls.
    // There will be a leading MH receiver arg in the out args in that case.
    //
    // Leading args in the out args will be ignored below because we iterate from the end of
    // the register arrays until !(in_idx >= 0), and total_in_args is smaller.
    //
    // Stub code adds a move for the receiver to j_rarg0 (and potential other prefix args) manually.
    for (int in_idx = total_in_args - 1, out_idx = total_out_args - 1; in_idx >= 0; in_idx--, out_idx--) {
      BasicType bt = in_sig_bt[in_idx];
      assert(bt != T_ARRAY, "array not expected");
      VMRegPair in_reg = in_regs[in_idx];
      VMRegPair out_reg = out_regs[out_idx];

      if (out_reg.first()->is_stack()) {
        // Move operations where the dest is the stack can all be
        // scheduled first since they can't interfere with the other moves.
        // The input and output stack spaces are distinct from each other.
        Move move{bt, in_reg, out_reg};
        _moves.push(move);
      } else if (in_reg.first() == out_reg.first()
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
    compute_store_order(tmp_vmreg);
  }

  // Walk the edges breaking cycles between moves.  The result list
  // can be walked in order to produce the proper set of loads
  void compute_store_order(VMRegPair temp_register) {
    // Record which moves kill which values
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

  GrowableArray<Move> moves() {
    return _moves;
  }
};

ArgumentShuffle::ArgumentShuffle(
    BasicType* in_sig_bt,
    int num_in_args,
    BasicType* out_sig_bt,
    int num_out_args,
    const CallConvClosure* input_conv,
    const CallConvClosure* output_conv,
    VMReg shuffle_temp) {

  VMRegPair* in_regs = NEW_RESOURCE_ARRAY(VMRegPair, num_in_args);
  input_conv->calling_convention(in_sig_bt, in_regs, num_in_args);

  VMRegPair* out_regs = NEW_RESOURCE_ARRAY(VMRegPair, num_out_args);
  _out_arg_stack_slots = output_conv->calling_convention(out_sig_bt, out_regs, num_out_args);

  VMRegPair tmp_vmreg;
  tmp_vmreg.set2(shuffle_temp);

  // Compute a valid move order, using tmp_vmreg to break any cycles.
  // Note that ForeignCMO ignores the upper half of our VMRegPairs.
  // We are not moving Java values here, only register-sized values,
  // so we shouldn't have to worry about the upper half any ways.
  // This should work fine on 32-bit as well, since we would only be
  // moving 32-bit sized values (i.e. low-level MH shouldn't take any double/long).
  ForeignCMO order(num_in_args, in_regs,
                   num_out_args, out_regs,
                   in_sig_bt, tmp_vmreg);
  _moves = order.moves();
}

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
#include "utilities/resourceHash.hpp"

StubLocations::StubLocations() {
  for (uint32_t i = 0; i < LOCATION_LIMIT; i++) {
    _locs[i] = VMStorage::invalid();
  }
}

void StubLocations::set(uint32_t loc, VMStorage storage) {
  assert(loc < LOCATION_LIMIT, "oob");
  _locs[loc] = storage;
}

void StubLocations::set_frame_data(uint32_t loc, int offset) {
  set(loc, VMStorage(StorageType::FRAME_DATA, 8, offset));
}

VMStorage StubLocations::get(uint32_t loc) const {
  assert(loc < LOCATION_LIMIT, "oob");
  VMStorage storage = _locs[loc];
  assert(storage.is_valid(), "not set");
  return storage;
}

VMStorage StubLocations::get(VMStorage placeholder) const {
  assert(placeholder.type() == StorageType::PLACEHOLDER, "must be");
  return get(placeholder.index());
}

int StubLocations::data_offset(uint32_t loc) const {
  VMStorage storage = get(loc);
  assert(storage.type() == StorageType::FRAME_DATA, "must be");
  return storage.offset();
}

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

  return VMStorage(static_cast<StorageType>(type), segment_mask_or_size, index_or_offset);
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
    VMStorage from_reg = move.from;
    VMStorage to_reg   = move.to;

    os->print("Move from ");
    from_reg.print_on(os);
    os->print(" to ");
    to_reg.print_on(os);
    os->print_cr("");
  }
  os->print_cr("}");
}

int ForeignGlobals::compute_out_arg_bytes(const GrowableArray<VMStorage>& out_regs) {
  uint32_t max_stack_offset = 0;
  for (VMStorage reg : out_regs) {
    if (reg.is_stack())
      max_stack_offset = MAX2(max_stack_offset, reg.offset() + reg.stack_size());
  }
  return align_up(max_stack_offset, 8);
}

int ForeignGlobals::java_calling_convention(const BasicType* signature, int num_args, GrowableArray<VMStorage>& out_regs) {
  VMRegPair* vm_regs = NEW_RESOURCE_ARRAY(VMRegPair, num_args);
  int slots = align_up(SharedRuntime::java_calling_convention(signature, vm_regs, num_args), 2);
  for (int i = 0; i < num_args; i++) {
    VMRegPair pair = vm_regs[i];
    // note, we ignore second here. Signature should consist of register-size values. So there should be
    // no need for multi-register pairs.
    if (signature[i] != T_VOID) {
      out_regs.push(as_VMStorage(pair.first(), signature[i]));
    }
  }
  return slots << LogBytesPerInt;
}

GrowableArray<VMStorage> ForeignGlobals::replace_place_holders(const GrowableArray<VMStorage>& regs, const StubLocations& locs) {
  GrowableArray<VMStorage> result(regs.length());
  for (VMStorage reg : regs) {
    result.push(reg.type() == StorageType::PLACEHOLDER ? locs.get(reg) : reg);
  }
  return result;
}

GrowableArray<VMStorage> ForeignGlobals::upcall_filter_receiver_reg(const GrowableArray<VMStorage>& unfiltered_regs) {
  GrowableArray<VMStorage> out(unfiltered_regs.length() - 1);
  // drop first arg reg
  for (int i = 1; i < unfiltered_regs.length(); i++) {
    out.push(unfiltered_regs.at(i));
  }
  return out;
}

GrowableArray<VMStorage> ForeignGlobals::downcall_filter_offset_regs(const GrowableArray<VMStorage>& regs,
                                                                     BasicType* signature, int num_args,
                                                                     bool& has_objects) {
  GrowableArray<VMStorage> result(regs.length());
  int reg_idx = 0;
  for (int sig_idx = 0; sig_idx < num_args; sig_idx++) {
    if (signature[sig_idx] == T_VOID) {
      continue; // ignore upper halves
    }

    result.push(regs.at(reg_idx++));
    if (signature[sig_idx] == T_OBJECT) {
      has_objects = true;
      sig_idx++; // skip offset
      reg_idx++;
    }
  }
  return result;
}

class ArgumentShuffle::ComputeMoveOrder: public StackObj {
  class MoveOperation;

  // segment_mask_or_size is not taken into account since
  // VMStorages that differ only in mask or size can still
  // conflict
  static inline unsigned hash(const VMStorage& vms) {
    return static_cast<unsigned int>(vms.type()) ^ vms.index_or_offset();
  }
  static inline bool equals(const VMStorage& a, const VMStorage& b) {
    return a.type() == b.type() && a.index_or_offset() == b.index_or_offset();
  }

  using KillerTable = ResourceHashtable<
    VMStorage, MoveOperation*,
    32, // doesn't need to be big. don't have that many argument registers (in known ABIs)
    AnyObj::RESOURCE_AREA,
    mtInternal,
    ComputeMoveOrder::hash,
    ComputeMoveOrder::equals
    >;

  class MoveOperation: public ResourceObj {
    friend class ComputeMoveOrder;
   private:
    VMStorage       _src;
    VMStorage       _dst;
    bool            _processed;
    MoveOperation*  _next;
    MoveOperation*  _prev;

   public:
    MoveOperation(VMStorage src, VMStorage dst):
      _src(src), _dst(dst), _processed(false), _next(nullptr), _prev(nullptr) {}

    const VMStorage& src() const { return _src; }
    const VMStorage& dst() const { return _dst; }
    MoveOperation* next()  const { return _next; }
    MoveOperation* prev()  const { return _prev; }
    void set_processed()         { _processed = true; }
    bool is_processed()    const { return _processed; }

    // insert
    void break_cycle(VMStorage temp_register) {
      // create a new store following the last store
      // to move from the temp_register to the original
      MoveOperation* new_store = new MoveOperation(temp_register, _dst);

      // break the cycle of links and insert new_store at the end
      // break the reverse link.
      MoveOperation* p = prev();
      assert(p->next() == this, "must be");
      _prev = nullptr;
      p->_next = new_store;
      new_store->_prev = p;

      // change the original store to save it's value in the temp.
      _dst = temp_register;
    }

    void link(KillerTable& killer) {
      // link this store in front the store that it depends on
      MoveOperation** n = killer.get(_src);
      if (n != nullptr) {
        MoveOperation* src_killer = *n;
        assert(_next == nullptr && src_killer->_prev == nullptr, "shouldn't have been set yet");
        _next = src_killer;
        src_killer->_prev = this;
      }
    }

    Move as_move() {
      return {_src, _dst};
    }
  };

 private:
  const GrowableArray<VMStorage>& _in_regs;
  const GrowableArray<VMStorage>& _out_regs;
  VMStorage _tmp_vmreg;
  GrowableArray<MoveOperation*> _edges;
  GrowableArray<Move> _moves;

 public:
  ComputeMoveOrder(const GrowableArray<VMStorage>& in_regs,
                   const GrowableArray<VMStorage>& out_regs,
                   VMStorage tmp_vmreg) :
      _in_regs(in_regs),
      _out_regs(out_regs),
      _tmp_vmreg(tmp_vmreg),
      _edges(in_regs.length()),
      _moves(in_regs.length()) {
    assert(in_regs.length() == out_regs.length(),
      "stray registers? %d != %d", in_regs.length(), out_regs.length());
  }

  void compute() {
    for (int i = 0; i < _in_regs.length(); i++) {
      VMStorage in_reg = _in_regs.at(i);
      VMStorage out_reg = _out_regs.at(i);

      if (out_reg.is_stack() || out_reg.is_frame_data()) {
        // Move operations where the dest is the stack can all be
        // scheduled first since they can't interfere with the other moves.
        // The input and output stack spaces are distinct from each other.
        Move move{in_reg, out_reg};
        _moves.push(move);
      } else if (in_reg == out_reg) {
        // Can skip non-stack identity moves.
        continue;
      } else {
        _edges.append(new MoveOperation(in_reg, out_reg));
      }
    }
    // Break any cycles in the register moves and emit the in the
    // proper order.
    compute_store_order();
  }

  // Walk the edges breaking cycles between moves.  The result list
  // can be walked in order to produce the proper set of loads
  void compute_store_order() {
    // Record which moves kill which registers
    KillerTable killer; // a map of VMStorage -> MoveOperation*
    for (int i = 0; i < _edges.length(); i++) {
      MoveOperation* s = _edges.at(i);
      assert(!killer.contains(s->dst()),
             "multiple moves with the same register as destination");
      killer.put(s->dst(), s);
    }
    assert(!killer.contains(_tmp_vmreg),
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
        while (start->prev() != nullptr && start->prev() != s) {
          start = start->prev();
        }
        if (start->prev() == s) {
          start->break_cycle(_tmp_vmreg);
        }
        // walk the chain forward inserting to store list
        while (start != nullptr) {
          _moves.push(start->as_move());

          start->set_processed();
          start = start->next();
        }
      }
    }
  }

public:
  static GrowableArray<Move> compute_move_order(const GrowableArray<VMStorage>& in_regs,
                                                const GrowableArray<VMStorage>& out_regs,
                                                VMStorage tmp_vmreg) {
    ComputeMoveOrder cmo(in_regs, out_regs, tmp_vmreg);
    cmo.compute();
    return cmo._moves;
  }
};

ArgumentShuffle::ArgumentShuffle(
    const GrowableArray<VMStorage>& in_regs,
    const GrowableArray<VMStorage>& out_regs,
    VMStorage shuffle_temp) {
  _moves = ComputeMoveOrder::compute_move_order(in_regs, out_regs, shuffle_temp);
}

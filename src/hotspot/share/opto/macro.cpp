/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciFlatArrayKlass.hpp"
#include "ci/ciInlineKlass.hpp"
#include "ci/ciInstanceKlass.hpp"
#include "compiler/compileLog.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "libadt/vectset.hpp"
#include "memory/universe.hpp"
#include "opto/addnode.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/compile.hpp"
#include "opto/convertnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/inlinetypenode.hpp"
#include "opto/intrinsicnode.hpp"
#include "opto/locknode.hpp"
#include "opto/loopnode.hpp"
#include "opto/macro.hpp"
#include "opto/memnode.hpp"
#include "opto/narrowptrnode.hpp"
#include "opto/node.hpp"
#include "opto/opaquenode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "opto/subtypenode.hpp"
#include "opto/type.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/continuation.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1ThreadLocalData.hpp"
#endif // INCLUDE_G1GC


//
// Replace any references to "oldref" in inputs to "use" with "newref".
// Returns the number of replacements made.
//
int PhaseMacroExpand::replace_input(Node *use, Node *oldref, Node *newref) {
  int nreplacements = 0;
  uint req = use->req();
  for (uint j = 0; j < use->len(); j++) {
    Node *uin = use->in(j);
    if (uin == oldref) {
      if (j < req)
        use->set_req(j, newref);
      else
        use->set_prec(j, newref);
      nreplacements++;
    } else if (j >= req && uin == nullptr) {
      break;
    }
  }
  return nreplacements;
}


Node* PhaseMacroExpand::opt_bits_test(Node* ctrl, Node* region, int edge, Node* word) {
  Node* cmp = word;
  Node* bol = transform_later(new BoolNode(cmp, BoolTest::ne));
  IfNode* iff = new IfNode( ctrl, bol, PROB_MIN, COUNT_UNKNOWN );
  transform_later(iff);

  // Fast path taken.
  Node *fast_taken = transform_later(new IfFalseNode(iff));

  // Fast path not-taken, i.e. slow path
  Node *slow_taken = transform_later(new IfTrueNode(iff));

    region->init_req(edge, fast_taken); // Capture fast-control
    return slow_taken;
}

//--------------------copy_predefined_input_for_runtime_call--------------------
void PhaseMacroExpand::copy_predefined_input_for_runtime_call(Node * ctrl, CallNode* oldcall, CallNode* call) {
  // Set fixed predefined input arguments
  call->init_req( TypeFunc::Control, ctrl );
  call->init_req( TypeFunc::I_O    , oldcall->in( TypeFunc::I_O) );
  call->init_req( TypeFunc::Memory , oldcall->in( TypeFunc::Memory ) ); // ?????
  call->init_req( TypeFunc::ReturnAdr, oldcall->in( TypeFunc::ReturnAdr ) );
  call->init_req( TypeFunc::FramePtr, oldcall->in( TypeFunc::FramePtr ) );
}

//------------------------------make_slow_call---------------------------------
CallNode* PhaseMacroExpand::make_slow_call(CallNode *oldcall, const TypeFunc* slow_call_type,
                                           address slow_call, const char* leaf_name, Node* slow_path,
                                           Node* parm0, Node* parm1, Node* parm2) {

  // Slow-path call
 CallNode *call = leaf_name
   ? (CallNode*)new CallLeafNode      ( slow_call_type, slow_call, leaf_name, TypeRawPtr::BOTTOM )
   : (CallNode*)new CallStaticJavaNode( slow_call_type, slow_call, OptoRuntime::stub_name(slow_call), TypeRawPtr::BOTTOM );

  // Slow path call has no side-effects, uses few values
  copy_predefined_input_for_runtime_call(slow_path, oldcall, call );
  if (parm0 != nullptr)  call->init_req(TypeFunc::Parms+0, parm0);
  if (parm1 != nullptr)  call->init_req(TypeFunc::Parms+1, parm1);
  if (parm2 != nullptr)  call->init_req(TypeFunc::Parms+2, parm2);
  call->copy_call_debug_info(&_igvn, oldcall);
  call->set_cnt(PROB_UNLIKELY_MAG(4));  // Same effect as RC_UNCOMMON.
  _igvn.replace_node(oldcall, call);
  transform_later(call);

  return call;
}

void PhaseMacroExpand::eliminate_gc_barrier(Node* p2x) {
  BarrierSetC2 *bs = BarrierSet::barrier_set()->barrier_set_c2();
  bs->eliminate_gc_barrier(&_igvn, p2x);
#ifndef PRODUCT
  if (PrintOptoStatistics) {
    AtomicAccess::inc(&PhaseMacroExpand::_GC_barriers_removed_counter);
  }
#endif
}

// Search for a memory operation for the specified memory slice.
static Node *scan_mem_chain(Node *mem, int alias_idx, int offset, Node *start_mem, Node *alloc, PhaseGVN *phase) {
  Node *orig_mem = mem;
  Node *alloc_mem = alloc->as_Allocate()->proj_out_or_null(TypeFunc::Memory, /*io_use:*/false);
  assert(alloc_mem != nullptr, "Allocation without a memory projection.");
  const TypeOopPtr *tinst = phase->C->get_adr_type(alias_idx)->isa_oopptr();
  while (true) {
    if (mem == alloc_mem || mem == start_mem ) {
      return mem;  // hit one of our sentinels
    } else if (mem->is_MergeMem()) {
      mem = mem->as_MergeMem()->memory_at(alias_idx);
    } else if (mem->is_Proj() && mem->as_Proj()->_con == TypeFunc::Memory) {
      Node *in = mem->in(0);
      // we can safely skip over safepoints, calls, locks and membars because we
      // already know that the object is safe to eliminate.
      if (in->is_Initialize() && in->as_Initialize()->allocation() == alloc) {
        return in;
      } else if (in->is_Call()) {
        CallNode *call = in->as_Call();
        if (call->may_modify(tinst, phase)) {
          assert(call->is_ArrayCopy(), "ArrayCopy is the only call node that doesn't make allocation escape");
          if (call->as_ArrayCopy()->modifies(offset, offset, phase, false)) {
            return in;
          }
        }
        mem = in->in(TypeFunc::Memory);
      } else if (in->is_MemBar()) {
        ArrayCopyNode* ac = nullptr;
        if (ArrayCopyNode::may_modify(tinst, in->as_MemBar(), phase, ac)) {
          if (ac != nullptr) {
            assert(ac->is_clonebasic(), "Only basic clone is a non escaping clone");
            return ac;
          }
        }
        mem = in->in(TypeFunc::Memory);
      } else if (in->is_LoadFlat() || in->is_StoreFlat()) {
        mem = in->in(TypeFunc::Memory);
      } else {
#ifdef ASSERT
        in->dump();
        mem->dump();
        assert(false, "unexpected projection");
#endif
      }
    } else if (mem->is_Store()) {
      const TypePtr* atype = mem->as_Store()->adr_type();
      int adr_idx = phase->C->get_alias_index(atype);
      if (adr_idx == alias_idx) {
        assert(atype->isa_oopptr(), "address type must be oopptr");
        int adr_offset = atype->flat_offset();
        uint adr_iid = atype->is_oopptr()->instance_id();
        // Array elements references have the same alias_idx
        // but different offset and different instance_id.
        if (adr_offset == offset && adr_iid == alloc->_idx) {
          return mem;
        }
      } else {
        assert(adr_idx == Compile::AliasIdxRaw, "address must match or be raw");
      }
      mem = mem->in(MemNode::Memory);
    } else if (mem->is_ClearArray()) {
      if (!ClearArrayNode::step_through(&mem, alloc->_idx, phase)) {
        // Can not bypass initialization of the instance
        // we are looking.
        DEBUG_ONLY(intptr_t offset;)
        assert(alloc == AllocateNode::Ideal_allocation(mem->in(3), phase, offset), "sanity");
        InitializeNode* init = alloc->as_Allocate()->initialization();
        // We are looking for stored value, return Initialize node
        // or memory edge from Allocate node.
        if (init != nullptr) {
          return init;
        } else {
          return alloc->in(TypeFunc::Memory); // It will produce zero value (see callers).
        }
      }
      // Otherwise skip it (the call updated 'mem' value).
    } else if (mem->Opcode() == Op_SCMemProj) {
      mem = mem->in(0);
      Node* adr = nullptr;
      if (mem->is_LoadStore()) {
        adr = mem->in(MemNode::Address);
      } else {
        assert(mem->Opcode() == Op_EncodeISOArray ||
               mem->Opcode() == Op_StrCompressedCopy, "sanity");
        adr = mem->in(3); // Destination array
      }
      const TypePtr* atype = adr->bottom_type()->is_ptr();
      int adr_idx = phase->C->get_alias_index(atype);
      if (adr_idx == alias_idx) {
        DEBUG_ONLY(mem->dump();)
        assert(false, "Object is not scalar replaceable if a LoadStore node accesses its field");
        return nullptr;
      }
      mem = mem->in(MemNode::Memory);
    } else if (mem->Opcode() == Op_StrInflatedCopy) {
      Node* adr = mem->in(3); // Destination array
      const TypePtr* atype = adr->bottom_type()->is_ptr();
      int adr_idx = phase->C->get_alias_index(atype);
      if (adr_idx == alias_idx) {
        DEBUG_ONLY(mem->dump();)
        assert(false, "Object is not scalar replaceable if a StrInflatedCopy node accesses its field");
        return nullptr;
      }
      mem = mem->in(MemNode::Memory);
    } else {
      return mem;
    }
    assert(mem != orig_mem, "dead memory loop");
  }
}

// Generate loads from source of the arraycopy for fields of
// destination needed at a deoptimization point
Node* PhaseMacroExpand::make_arraycopy_load(ArrayCopyNode* ac, intptr_t offset, Node* ctl, Node* mem, BasicType ft, const Type *ftype, AllocateNode *alloc) {
  BasicType bt = ft;
  const Type *type = ftype;
  if (ft == T_NARROWOOP) {
    bt = T_OBJECT;
    type = ftype->make_oopptr();
  }
  Node* res = nullptr;
  if (ac->is_clonebasic()) {
    assert(ac->in(ArrayCopyNode::Src) != ac->in(ArrayCopyNode::Dest), "clone source equals destination");
    Node* base = ac->in(ArrayCopyNode::Src);
    Node* adr = _igvn.transform(new AddPNode(base, base, _igvn.MakeConX(offset)));
    const TypePtr* adr_type = _igvn.type(base)->is_ptr()->add_offset(offset);
    MergeMemNode* mergemen = _igvn.transform(MergeMemNode::make(mem))->as_MergeMem();
    BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
    res = ArrayCopyNode::load(bs, &_igvn, ctl, mergemen, adr, adr_type, type, bt);
  } else {
    if (ac->modifies(offset, offset, &_igvn, true)) {
      assert(ac->in(ArrayCopyNode::Dest) == alloc->result_cast(), "arraycopy destination should be allocation's result");
      uint shift = exact_log2(type2aelembytes(bt));
      Node* src_pos = ac->in(ArrayCopyNode::SrcPos);
      Node* dest_pos = ac->in(ArrayCopyNode::DestPos);
      const TypeInt* src_pos_t = _igvn.type(src_pos)->is_int();
      const TypeInt* dest_pos_t = _igvn.type(dest_pos)->is_int();

      Node* adr = nullptr;
      Node* base = ac->in(ArrayCopyNode::Src);
      const TypeAryPtr* adr_type = _igvn.type(base)->is_aryptr();
      if (adr_type->is_flat()) {
        shift = adr_type->flat_log_elem_size();
      }
      if (src_pos_t->is_con() && dest_pos_t->is_con()) {
        intptr_t off = ((src_pos_t->get_con() - dest_pos_t->get_con()) << shift) + offset;
        adr = _igvn.transform(new AddPNode(base, base, _igvn.MakeConX(off)));
        adr_type = _igvn.type(adr)->is_aryptr();
        assert(adr_type == _igvn.type(base)->is_aryptr()->add_field_offset_and_offset(off), "incorrect address type");
        if (ac->in(ArrayCopyNode::Src) == ac->in(ArrayCopyNode::Dest)) {
          // Don't emit a new load from src if src == dst but try to get the value from memory instead
          return value_from_mem(ac->in(TypeFunc::Memory), ctl, ft, ftype, adr_type, alloc);
        }
      } else {
        if (ac->in(ArrayCopyNode::Src) == ac->in(ArrayCopyNode::Dest)) {
          // Non constant offset in the array: we can't statically
          // determine the value
          return nullptr;
        }
        Node* diff = _igvn.transform(new SubINode(ac->in(ArrayCopyNode::SrcPos), ac->in(ArrayCopyNode::DestPos)));
#ifdef _LP64
        diff = _igvn.transform(new ConvI2LNode(diff));
#endif
        diff = _igvn.transform(new LShiftXNode(diff, _igvn.intcon(shift)));

        Node* off = _igvn.transform(new AddXNode(_igvn.MakeConX(offset), diff));
        adr = _igvn.transform(new AddPNode(base, base, off));
        // In the case of a flat inline type array, each field has its
        // own slice so we need to extract the field being accessed from
        // the address computation
        adr_type = adr_type->add_field_offset_and_offset(offset)->add_offset(Type::OffsetBot)->is_aryptr();
        adr = _igvn.transform(new CastPPNode(ctl, adr, adr_type));
      }
      MergeMemNode* mergemen = _igvn.transform(MergeMemNode::make(mem))->as_MergeMem();
      BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
      res = ArrayCopyNode::load(bs, &_igvn, ctl, mergemen, adr, adr_type, type, bt);
    }
  }
  if (res != nullptr) {
    if (ftype->isa_narrowoop()) {
      // PhaseMacroExpand::scalar_replacement adds DecodeN nodes
      assert(res->isa_DecodeN(), "should be narrow oop");
      res = _igvn.transform(new EncodePNode(res, ftype));
    }
    return res;
  }
  return nullptr;
}

//
// Given a Memory Phi, compute a value Phi containing the values from stores
// on the input paths.
// Note: this function is recursive, its depth is limited by the "level" argument
// Returns the computed Phi, or null if it cannot compute it.
Node *PhaseMacroExpand::value_from_mem_phi(Node *mem, BasicType ft, const Type *phi_type, const TypeOopPtr *adr_t, AllocateNode *alloc, Node_Stack *value_phis, int level) {
  assert(mem->is_Phi(), "sanity");
  int alias_idx = C->get_alias_index(adr_t);
  int offset = adr_t->flat_offset();
  int instance_id = adr_t->instance_id();

  // Check if an appropriate value phi already exists.
  Node* region = mem->in(0);
  for (DUIterator_Fast kmax, k = region->fast_outs(kmax); k < kmax; k++) {
    Node* phi = region->fast_out(k);
    if (phi->is_Phi() && phi != mem &&
        phi->as_Phi()->is_same_inst_field(phi_type, (int)mem->_idx, instance_id, alias_idx, offset)) {
      return phi;
    }
  }
  // Check if an appropriate new value phi already exists.
  Node* new_phi = value_phis->find(mem->_idx);
  if (new_phi != nullptr)
    return new_phi;

  if (level <= 0) {
    return nullptr; // Give up: phi tree too deep
  }
  Node *start_mem = C->start()->proj_out_or_null(TypeFunc::Memory);
  Node *alloc_mem = alloc->proj_out_or_null(TypeFunc::Memory, /*io_use:*/false);
  assert(alloc_mem != nullptr, "Allocation without a memory projection.");

  uint length = mem->req();
  GrowableArray <Node *> values(length, length, nullptr);

  // create a new Phi for the value
  PhiNode *phi = new PhiNode(mem->in(0), phi_type, nullptr, mem->_idx, instance_id, alias_idx, offset);
  transform_later(phi);
  value_phis->push(phi, mem->_idx);

  for (uint j = 1; j < length; j++) {
    Node *in = mem->in(j);
    if (in == nullptr || in->is_top()) {
      values.at_put(j, in);
    } else {
      Node *val = scan_mem_chain(in, alias_idx, offset, start_mem, alloc, &_igvn);
      if (val == start_mem || val == alloc_mem) {
        // hit a sentinel, return appropriate value
        Node* init_value = value_from_alloc(ft, adr_t, alloc);
        if (init_value == nullptr) {
          return nullptr;
        } else {
          values.at_put(j, init_value);
          continue;
        }
      }
      if (val->is_Initialize()) {
        val = val->as_Initialize()->find_captured_store(offset, type2aelembytes(ft), &_igvn);
      }
      if (val == nullptr) {
        return nullptr;  // can't find a value on this path
      }
      if (val == mem) {
        values.at_put(j, mem);
      } else if (val->is_Store()) {
        Node* n = val->in(MemNode::ValueIn);
        BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
        n = bs->step_over_gc_barrier(n);
        if (is_subword_type(ft)) {
          n = Compile::narrow_value(ft, n, phi_type, &_igvn, true);
        }
        values.at_put(j, n);
      } else if (val->is_Proj() && val->in(0) == alloc) {
        Node* init_value = value_from_alloc(ft, adr_t, alloc);
        if (init_value == nullptr) {
          return nullptr;
        } else {
          values.at_put(j, init_value);
        }
      } else if (val->is_Phi()) {
        val = value_from_mem_phi(val, ft, phi_type, adr_t, alloc, value_phis, level-1);
        if (val == nullptr) {
          return nullptr;
        }
        values.at_put(j, val);
      } else if (val->Opcode() == Op_SCMemProj) {
        assert(val->in(0)->is_LoadStore() ||
               val->in(0)->Opcode() == Op_EncodeISOArray ||
               val->in(0)->Opcode() == Op_StrCompressedCopy, "sanity");
        assert(false, "Object is not scalar replaceable if a LoadStore node accesses its field");
        return nullptr;
      } else if (val->is_ArrayCopy()) {
        Node* res = make_arraycopy_load(val->as_ArrayCopy(), offset, val->in(0), val->in(TypeFunc::Memory), ft, phi_type, alloc);
        if (res == nullptr) {
          return nullptr;
        }
        values.at_put(j, res);
      } else if (val->is_top()) {
        // This indicates that this path into the phi is dead. Top will eventually also propagate into the Region.
        // IGVN will clean this up later.
        values.at_put(j, val);
      } else {
        DEBUG_ONLY( val->dump(); )
        assert(false, "unknown node on this path");
        return nullptr;  // unknown node on this path
      }
    }
  }
  // Set Phi's inputs
  for (uint j = 1; j < length; j++) {
    if (values.at(j) == mem) {
      phi->init_req(j, phi);
    } else {
      phi->init_req(j, values.at(j));
    }
  }
  return phi;
}

// Extract the initial value of a field in an allocation
Node* PhaseMacroExpand::value_from_alloc(BasicType ft, const TypeOopPtr* adr_t, AllocateNode* alloc) {
  Node* init_value = alloc->in(AllocateNode::InitValue);
  if (init_value == nullptr) {
    assert(alloc->in(AllocateNode::RawInitValue) == nullptr, "conflicting InitValue and RawInitValue");
    return _igvn.zerocon(ft);
  }

  const TypeAryPtr* ary_t = adr_t->isa_aryptr();
  assert(ary_t != nullptr, "must be a pointer into an array");

  // If this is not a flat array, then it must be an oop array with elements being init_value
  if (ary_t->is_not_flat()) {
#ifdef ASSERT
    BasicType init_bt = init_value->bottom_type()->basic_type();
    assert(ft == init_bt ||
           (!is_java_primitive(ft) && !is_java_primitive(init_bt) && type2aelembytes(ft, true) == type2aelembytes(init_bt, true)) ||
           (is_subword_type(ft) && init_bt == T_INT),
           "invalid init_value of type %s for field of type %s", type2name(init_bt), type2name(ft));
#endif // ASSERT
    return init_value;
  }

  assert(ary_t->klass_is_exact() && ary_t->is_flat(), "must be an exact flat array");
  assert(ary_t->field_offset().get() != Type::OffsetBot, "unknown offset");
  if (init_value->is_EncodeP()) {
    init_value = init_value->in(1);
  }
  // Cannot look through init_value if it is an oop
  if (!init_value->is_InlineType()) {
    return nullptr;
  }

  ciInlineKlass* vk = init_value->bottom_type()->inline_klass();
  if (ary_t->field_offset().get() == vk->null_marker_offset_in_payload()) {
    init_value = init_value->as_InlineType()->get_null_marker();
  } else {
    init_value = init_value->as_InlineType()->field_value_by_offset(ary_t->field_offset().get() + vk->payload_offset(), true);
  }

  if (ft == T_NARROWOOP) {
    assert(init_value->bottom_type()->isa_ptr(), "must be a pointer");
    init_value = transform_later(new EncodePNode(init_value, init_value->bottom_type()->make_narrowoop()));
  }

#ifdef ASSERT
  BasicType init_bt = init_value->bottom_type()->basic_type();
  assert(ft == init_bt ||
         (!is_java_primitive(ft) && !is_java_primitive(init_bt) && type2aelembytes(ft, true) == type2aelembytes(init_bt, true)) ||
         (is_subword_type(ft) && init_bt == T_INT),
         "invalid init_value of type %s for field of type %s", type2name(init_bt), type2name(ft));
#endif // ASSERT

  return init_value;
}

// Search the last value stored into the object's field.
Node *PhaseMacroExpand::value_from_mem(Node *sfpt_mem, Node *sfpt_ctl, BasicType ft, const Type *ftype, const TypeOopPtr *adr_t, AllocateNode *alloc) {
  assert(adr_t->is_known_instance_field(), "instance required");
  int instance_id = adr_t->instance_id();
  assert((uint)instance_id == alloc->_idx, "wrong allocation");

  int alias_idx = C->get_alias_index(adr_t);
  int offset = adr_t->flat_offset();
  Node *start_mem = C->start()->proj_out_or_null(TypeFunc::Memory);
  Node *alloc_mem = alloc->proj_out_or_null(TypeFunc::Memory, /*io_use:*/false);
  assert(alloc_mem != nullptr, "Allocation without a memory projection.");
  VectorSet visited;

  bool done = sfpt_mem == alloc_mem;
  Node *mem = sfpt_mem;
  while (!done) {
    if (visited.test_set(mem->_idx)) {
      return nullptr;  // found a loop, give up
    }
    mem = scan_mem_chain(mem, alias_idx, offset, start_mem, alloc, &_igvn);
    if (mem == start_mem || mem == alloc_mem) {
      done = true;  // hit a sentinel, return appropriate 0 value
    } else if (mem->is_Initialize()) {
      mem = mem->as_Initialize()->find_captured_store(offset, type2aelembytes(ft), &_igvn);
      if (mem == nullptr) {
        done = true; // Something went wrong.
      } else if (mem->is_Store()) {
        const TypePtr* atype = mem->as_Store()->adr_type();
        assert(C->get_alias_index(atype) == Compile::AliasIdxRaw, "store is correct memory slice");
        done = true;
      }
    } else if (mem->is_Store()) {
      const TypeOopPtr* atype = mem->as_Store()->adr_type()->isa_oopptr();
      assert(atype != nullptr, "address type must be oopptr");
      assert(C->get_alias_index(atype) == alias_idx &&
             atype->is_known_instance_field() && atype->flat_offset() == offset &&
             atype->instance_id() == instance_id, "store is correct memory slice");
      done = true;
    } else if (mem->is_Phi()) {
      // try to find a phi's unique input
      Node *unique_input = nullptr;
      Node *top = C->top();
      for (uint i = 1; i < mem->req(); i++) {
        Node *n = scan_mem_chain(mem->in(i), alias_idx, offset, start_mem, alloc, &_igvn);
        if (n == nullptr || n == top || n == mem) {
          continue;
        } else if (unique_input == nullptr) {
          unique_input = n;
        } else if (unique_input != n) {
          unique_input = top;
          break;
        }
      }
      if (unique_input != nullptr && unique_input != top) {
        mem = unique_input;
      } else {
        done = true;
      }
    } else if (mem->is_ArrayCopy()) {
      done = true;
    } else if (mem->is_top()) {
      // The slice is on a dead path. Returning nullptr would lead to elimination
      // bailout, but we want to prevent that. Just forwarding the top is also legal,
      // and IGVN can just clean things up, and remove whatever receives top.
      return mem;
    } else {
      DEBUG_ONLY( mem->dump(); )
      assert(false, "unexpected node");
    }
  }
  if (mem != nullptr) {
    if (mem == start_mem || mem == alloc_mem) {
      // hit a sentinel, return appropriate value
      return value_from_alloc(ft, adr_t, alloc);
    } else if (mem->is_Store()) {
      Node* n = mem->in(MemNode::ValueIn);
      BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
      n = bs->step_over_gc_barrier(n);
      return n;
    } else if (mem->is_Phi()) {
      // attempt to produce a Phi reflecting the values on the input paths of the Phi
      Node_Stack value_phis(8);
      Node* phi = value_from_mem_phi(mem, ft, ftype, adr_t, alloc, &value_phis, ValueSearchLimit);
      if (phi != nullptr) {
        return phi;
      } else {
        // Kill all new Phis
        while(value_phis.is_nonempty()) {
          Node* n = value_phis.node();
          _igvn.replace_node(n, C->top());
          value_phis.pop();
        }
      }
    } else if (mem->is_ArrayCopy()) {
      Node* ctl = mem->in(0);
      Node* m = mem->in(TypeFunc::Memory);
      if (sfpt_ctl->is_Proj() && sfpt_ctl->as_Proj()->is_uncommon_trap_proj()) {
        // pin the loads in the uncommon trap path
        ctl = sfpt_ctl;
        m = sfpt_mem;
      }
      return make_arraycopy_load(mem->as_ArrayCopy(), offset, ctl, m, ft, ftype, alloc);
    }
  }
  // Something went wrong.
  return nullptr;
}

// Search the last value stored into the inline type's fields (for flat arrays).
Node* PhaseMacroExpand::inline_type_from_mem(ciInlineKlass* vk, const TypeAryPtr* elem_adr_type, int elem_idx, int offset_in_element, bool null_free, AllocateNode* alloc, SafePointNode* sfpt) {
  auto report_failure = [&](int field_offset_in_element) {
#ifndef PRODUCT
    if (PrintEliminateAllocations) {
      ciInlineKlass* elem_klass = elem_adr_type->elem()->inline_klass();
      int offset = field_offset_in_element + elem_klass->payload_offset();
      ciField* flattened_field = elem_klass->get_field_by_offset(offset, false);
      assert(flattened_field != nullptr, "must have a field of type %s at offset %d", elem_klass->name()->as_utf8(), offset);
      tty->print("=== At SafePoint node %d can't find value of field [%s] of array element [%d]", sfpt->_idx, flattened_field->name()->as_utf8(), elem_idx);
      tty->print(", which prevents elimination of: ");
      alloc->dump();
    }
#endif // PRODUCT
  };

  // Create a new InlineTypeNode and retrieve the field values from memory
  InlineTypeNode* vt = InlineTypeNode::make_uninitialized(_igvn, vk, false);
  transform_later(vt);
  if (null_free) {
    vt->set_null_marker(_igvn);
  } else {
    int nm_offset_in_element = offset_in_element + vk->null_marker_offset_in_payload();
    const TypeAryPtr* nm_adr_type = elem_adr_type->with_field_offset(nm_offset_in_element);
    Node* nm_value = value_from_mem(sfpt->memory(), sfpt->control(), T_BOOLEAN, TypeInt::BOOL, nm_adr_type, alloc);
    if (nm_value != nullptr) {
      vt->set_null_marker(_igvn, nm_value);
    } else {
      report_failure(nm_offset_in_element);
      return nullptr;
    }
  }

  for (int i = 0; i < vk->nof_declared_nonstatic_fields(); ++i) {
    ciField* field = vt->field(i);
    ciType* field_type = field->type();
    int field_offset_in_element = offset_in_element + field->offset_in_bytes() - vk->payload_offset();
    Node* field_value = nullptr;
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    if (field->is_flat()) {
      field_value = inline_type_from_mem(field_type->as_inline_klass(), elem_adr_type, elem_idx, field_offset_in_element, field->is_null_free(), alloc, sfpt);
    } else {
      const Type* ft = Type::get_const_type(field_type);
      BasicType bt = type2field[field_type->basic_type()];
      if (UseCompressedOops && !is_java_primitive(bt)) {
        ft = ft->make_narrowoop();
        bt = T_NARROWOOP;
      }
      // Each inline type field has its own memory slice
      const TypeAryPtr* field_adr_type = elem_adr_type->with_field_offset(field_offset_in_element);
      field_value = value_from_mem(sfpt->memory(), sfpt->control(), bt, ft, field_adr_type, alloc);
      if (field_value == nullptr) {
        report_failure(field_offset_in_element);
      } else if (ft->isa_narrowoop()) {
        assert(UseCompressedOops, "unexpected narrow oop");
        if (field_value->is_EncodeP()) {
          field_value = field_value->in(1);
        } else if (!field_value->is_InlineType()) {
          field_value = transform_later(new DecodeNNode(field_value, field_value->get_ptr_type()));
        }
      }
    }
    if (field_value != nullptr) {
      vt->set_field_value(i, field_value);
    } else {
      return nullptr;
    }
  }
  return vt;
}

// Check the possibility of scalar replacement.
bool PhaseMacroExpand::can_eliminate_allocation(PhaseIterGVN* igvn, AllocateNode *alloc, GrowableArray <SafePointNode *>* safepoints) {
  //  Scan the uses of the allocation to check for anything that would
  //  prevent us from eliminating it.
  NOT_PRODUCT( const char* fail_eliminate = nullptr; )
  DEBUG_ONLY( Node* disq_node = nullptr; )
  bool can_eliminate = true;
  bool reduce_merge_precheck = (safepoints == nullptr);

  Unique_Node_List worklist;
  Node* res = alloc->result_cast();
  const TypeOopPtr* res_type = nullptr;
  if (res == nullptr) {
    // All users were eliminated.
  } else if (!res->is_CheckCastPP()) {
    NOT_PRODUCT(fail_eliminate = "Allocation does not have unique CheckCastPP";)
    can_eliminate = false;
  } else {
    worklist.push(res);
    res_type = igvn->type(res)->isa_oopptr();
    if (res_type == nullptr) {
      NOT_PRODUCT(fail_eliminate = "Neither instance or array allocation";)
      can_eliminate = false;
    } else if (!res_type->klass_is_exact()) {
      NOT_PRODUCT(fail_eliminate = "Not an exact type.";)
      can_eliminate = false;
    } else if (res_type->isa_aryptr()) {
      int length = alloc->in(AllocateNode::ALength)->find_int_con(-1);
      if (length < 0) {
        NOT_PRODUCT(fail_eliminate = "Array's size is not constant";)
        can_eliminate = false;
      }
    }
  }

  while (can_eliminate && worklist.size() > 0) {
    BarrierSetC2 *bs = BarrierSet::barrier_set()->barrier_set_c2();
    res = worklist.pop();
    for (DUIterator_Fast jmax, j = res->fast_outs(jmax); j < jmax && can_eliminate; j++) {
      Node* use = res->fast_out(j);

      if (use->is_AddP()) {
        const TypePtr* addp_type = igvn->type(use)->is_ptr();
        int offset = addp_type->offset();

        if (offset == Type::OffsetTop || offset == Type::OffsetBot) {
          NOT_PRODUCT(fail_eliminate = "Undefined field reference";)
          can_eliminate = false;
          break;
        }
        for (DUIterator_Fast kmax, k = use->fast_outs(kmax);
                                   k < kmax && can_eliminate; k++) {
          Node* n = use->fast_out(k);
          if ((n->is_Mem() && n->as_Mem()->is_mismatched_access()) || n->is_LoadFlat() || n->is_StoreFlat()) {
            DEBUG_ONLY(disq_node = n);
            NOT_PRODUCT(fail_eliminate = "Mismatched access");
            can_eliminate = false;
          }
          if (!n->is_Store() && n->Opcode() != Op_CastP2X && !bs->is_gc_pre_barrier_node(n) && !reduce_merge_precheck) {
            DEBUG_ONLY(disq_node = n;)
            if (n->is_Load() || n->is_LoadStore()) {
              NOT_PRODUCT(fail_eliminate = "Field load";)
            } else {
              NOT_PRODUCT(fail_eliminate = "Not store field reference";)
            }
            can_eliminate = false;
          }
        }
      } else if (use->is_ArrayCopy() &&
                 (use->as_ArrayCopy()->is_clonebasic() ||
                  use->as_ArrayCopy()->is_arraycopy_validated() ||
                  use->as_ArrayCopy()->is_copyof_validated() ||
                  use->as_ArrayCopy()->is_copyofrange_validated()) &&
                 use->in(ArrayCopyNode::Dest) == res) {
        // ok to eliminate
      } else if (use->is_SafePoint()) {
        SafePointNode* sfpt = use->as_SafePoint();
        if (sfpt->is_Call() && sfpt->as_Call()->has_non_debug_use(res)) {
          // Object is passed as argument.
          DEBUG_ONLY(disq_node = use;)
          NOT_PRODUCT(fail_eliminate = "Object is passed as argument";)
          can_eliminate = false;
        }
        Node* sfptMem = sfpt->memory();
        if (sfptMem == nullptr || sfptMem->is_top()) {
          DEBUG_ONLY(disq_node = use;)
          NOT_PRODUCT(fail_eliminate = "null or TOP memory";)
          can_eliminate = false;
        } else if (!reduce_merge_precheck) {
          assert(!res->is_Phi() || !res->as_Phi()->can_be_inline_type(), "Inline type allocations should not have safepoint uses");
          safepoints->append_if_missing(sfpt);
        }
      } else if (use->is_InlineType() && use->as_InlineType()->get_oop() == res) {
        // Look at uses
        for (DUIterator_Fast kmax, k = use->fast_outs(kmax); k < kmax; k++) {
          Node* u = use->fast_out(k);
          if (u->is_InlineType()) {
            // Use in flat field can be eliminated
            InlineTypeNode* vt = u->as_InlineType();
            for (uint i = 0; i < vt->field_count(); ++i) {
              if (vt->field_value(i) == use && !vt->field(i)->is_flat()) {
                can_eliminate = false; // Use in non-flat field
                break;
              }
            }
          } else {
            // Add other uses to the worklist to process individually
            worklist.push(use);
          }
        }
      } else if (use->Opcode() == Op_StoreX && use->in(MemNode::Address) == res) {
        // Store to mark word of inline type larval buffer
        assert(res_type->is_inlinetypeptr(), "Unexpected store to mark word");
      } else if (res_type->is_inlinetypeptr() && (use->Opcode() == Op_MemBarRelease || use->Opcode() == Op_MemBarStoreStore)) {
        // Inline type buffer allocations are followed by a membar
      } else if (reduce_merge_precheck &&
                 (use->is_Phi() || use->is_EncodeP() ||
                  use->Opcode() == Op_MemBarRelease ||
                  (UseStoreStoreForCtor && use->Opcode() == Op_MemBarStoreStore))) {
        // Nothing to do
      } else if (use->Opcode() != Op_CastP2X) { // CastP2X is used by card mark
        if (use->is_Phi()) {
          if (use->outcnt() == 1 && use->unique_out()->Opcode() == Op_Return) {
            NOT_PRODUCT(fail_eliminate = "Object is return value";)
          } else {
            NOT_PRODUCT(fail_eliminate = "Object is referenced by Phi";)
          }
          DEBUG_ONLY(disq_node = use;)
        } else {
          if (use->Opcode() == Op_Return) {
            NOT_PRODUCT(fail_eliminate = "Object is return value";)
          } else {
            NOT_PRODUCT(fail_eliminate = "Object is referenced by node";)
          }
          DEBUG_ONLY(disq_node = use;)
        }
        can_eliminate = false;
      } else {
        assert(use->Opcode() == Op_CastP2X, "should be");
        assert(!use->has_out_with(Op_OrL), "should have been removed because oop is never null");
      }
    }
  }

#ifndef PRODUCT
  if (PrintEliminateAllocations && safepoints != nullptr) {
    if (can_eliminate) {
      tty->print("Scalar ");
      if (res == nullptr)
        alloc->dump();
      else
        res->dump();
    } else {
      tty->print("NotScalar (%s)", fail_eliminate);
      if (res == nullptr)
        alloc->dump();
      else
        res->dump();
#ifdef ASSERT
      if (disq_node != nullptr) {
          tty->print("  >>>> ");
          disq_node->dump();
      }
#endif /*ASSERT*/
    }
  }

  if (TraceReduceAllocationMerges && !can_eliminate && reduce_merge_precheck) {
    tty->print_cr("\tCan't eliminate allocation because '%s': ", fail_eliminate != nullptr ? fail_eliminate : "");
    DEBUG_ONLY(if (disq_node != nullptr) disq_node->dump();)
  }
#endif
  return can_eliminate;
}

void PhaseMacroExpand::undo_previous_scalarizations(GrowableArray <SafePointNode *> safepoints_done, AllocateNode* alloc) {
  Node* res = alloc->result_cast();
  int nfields = 0;
  assert(res == nullptr || res->is_CheckCastPP(), "unexpected AllocateNode result");

  if (res != nullptr) {
    const TypeOopPtr* res_type = _igvn.type(res)->isa_oopptr();

    if (res_type->isa_instptr()) {
      // find the fields of the class which will be needed for safepoint debug information
      ciInstanceKlass* iklass = res_type->is_instptr()->instance_klass();
      nfields = iklass->nof_nonstatic_fields();
    } else {
      // find the array's elements which will be needed for safepoint debug information
      nfields = alloc->in(AllocateNode::ALength)->find_int_con(-1);
      assert(nfields >= 0, "must be an array klass.");
    }
  }

  // rollback processed safepoints
  while (safepoints_done.length() > 0) {
    SafePointNode* sfpt_done = safepoints_done.pop();
    // remove any extra entries we added to the safepoint
    uint last = sfpt_done->req() - 1;
    for (int k = 0;  k < nfields; k++) {
      sfpt_done->del_req(last--);
    }
    JVMState *jvms = sfpt_done->jvms();
    jvms->set_endoff(sfpt_done->req());
    // Now make a pass over the debug information replacing any references
    // to SafePointScalarObjectNode with the allocated object.
    int start = jvms->debug_start();
    int end   = jvms->debug_end();
    for (int i = start; i < end; i++) {
      if (sfpt_done->in(i)->is_SafePointScalarObject()) {
        SafePointScalarObjectNode* scobj = sfpt_done->in(i)->as_SafePointScalarObject();
        if (scobj->first_index(jvms) == sfpt_done->req() &&
            scobj->n_fields() == (uint)nfields) {
          assert(scobj->alloc() == alloc, "sanity");
          sfpt_done->set_req(i, res);
        }
      }
    }
    _igvn._worklist.push(sfpt_done);
  }
}

#ifdef ASSERT
  // Verify if a value can be written into a field.
  void verify_type_compatability(const Type* value_type, const Type* field_type) {
    BasicType value_bt = value_type->basic_type();
    BasicType field_bt = field_type->basic_type();

    // Primitive types must match.
    if (is_java_primitive(value_bt) && value_bt == field_bt) { return; }

    // I have been struggling to make a similar assert for non-primitive
    // types. I we can add one in the future. For now, I just let them
    // pass without checks.
    // In particular, I was struggling with a value that came from a call,
    // and had only a non-null check CastPP. There was also a checkcast
    // in the graph to verify the interface, but the corresponding
    // CheckCastPP result was not updated in the stack slot, and so
    // we ended up using the CastPP. That means that the field knows
    // that it should get an oop from an interface, but the value lost
    // that information, and so it is not a subtype.
    // There may be other issues, feel free to investigate further!
    if (!is_java_primitive(value_bt)) { return; }

    tty->print_cr("value not compatible for field: %s vs %s",
                  type2name(value_bt),
                  type2name(field_bt));
    tty->print("value_type: ");
    value_type->dump();
    tty->cr();
    tty->print("field_type: ");
    field_type->dump();
    tty->cr();
    assert(false, "value_type does not fit field_type");
  }
#endif

void PhaseMacroExpand::process_field_value_at_safepoint(const Type* field_type, Node* field_val, SafePointNode* sfpt, Unique_Node_List* value_worklist) {
  if (UseCompressedOops && field_type->isa_narrowoop()) {
    // Enable "DecodeN(EncodeP(Allocate)) --> Allocate" transformation
    // to be able scalar replace the allocation.
    if (field_val->is_EncodeP()) {
      field_val = field_val->in(1);
    } else if (!field_val->is_InlineType()) {
      field_val = transform_later(new DecodeNNode(field_val, field_val->get_ptr_type()));
    }
  }

  // Keep track of inline types to scalarize them later
  if (field_val->is_InlineType()) {
    value_worklist->push(field_val);
  } else if (field_val->is_Phi()) {
    PhiNode* phi = field_val->as_Phi();
    // Eagerly replace inline type phis now since we could be removing an inline type allocation where we must
    // scalarize all its fields in safepoints.
    field_val = phi->try_push_inline_types_down(&_igvn, true);
    if (field_val->is_InlineType()) {
      value_worklist->push(field_val);
    }
  }
  DEBUG_ONLY(verify_type_compatability(field_val->bottom_type(), field_type);)
  sfpt->add_req(field_val);
}

bool PhaseMacroExpand::add_array_elems_to_safepoint(AllocateNode* alloc, const TypeAryPtr* array_type, SafePointNode* sfpt, Unique_Node_List* value_worklist) {
  const Type* elem_type = array_type->elem();
  BasicType basic_elem_type = elem_type->array_element_basic_type();

  intptr_t elem_size;
  uint header_size;
  if (array_type->is_flat()) {
    elem_size = array_type->flat_elem_size();
    header_size = arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT);
  } else {
    elem_size = type2aelembytes(basic_elem_type);
    header_size = arrayOopDesc::base_offset_in_bytes(basic_elem_type);
  }

  int n_elems = alloc->in(AllocateNode::ALength)->get_int();
  for (int elem_idx = 0; elem_idx < n_elems; elem_idx++) {
    intptr_t elem_offset = header_size + elem_idx * elem_size;
    const TypeAryPtr* elem_adr_type = array_type->with_offset(elem_offset);
    Node* elem_val;
    if (array_type->is_flat()) {
      ciInlineKlass* elem_klass = elem_type->inline_klass();
      assert(elem_klass->maybe_flat_in_array(), "must be flat in array");
      elem_val = inline_type_from_mem(elem_klass, elem_adr_type, elem_idx, 0, array_type->is_null_free(), alloc, sfpt);
    } else {
      elem_val = value_from_mem(sfpt->memory(), sfpt->control(), basic_elem_type, elem_type, elem_adr_type, alloc);
#ifndef PRODUCT
      if (PrintEliminateAllocations && elem_val == nullptr) {
        tty->print("=== At SafePoint node %d can't find value of array element [%d]", sfpt->_idx, elem_idx);
        tty->print(", which prevents elimination of: ");
        alloc->dump();
      }
#endif // PRODUCT
    }
    if (elem_val == nullptr) {
      return false;
    }

    process_field_value_at_safepoint(elem_type, elem_val, sfpt, value_worklist);
  }

  return true;
}

// Recursively adds all flattened fields of a type 'iklass' inside 'base' to 'sfpt'.
// 'offset_minus_header' refers to the offset of the payload of 'iklass' inside 'base' minus the
// payload offset of 'iklass'. If 'base' is of type 'iklass' then 'offset_minus_header' == 0.
bool PhaseMacroExpand::add_inst_fields_to_safepoint(ciInstanceKlass* iklass, AllocateNode* alloc, Node* base, int offset_minus_header, SafePointNode* sfpt, Unique_Node_List* value_worklist) {
  const TypeInstPtr* base_type = _igvn.type(base)->is_instptr();
  auto report_failure = [&](int offset) {
#ifndef PRODUCT
    if (PrintEliminateAllocations) {
      ciInstanceKlass* base_klass = base_type->instance_klass();
      ciField* flattened_field = base_klass->get_field_by_offset(offset, false);
      assert(flattened_field != nullptr, "must have a field of type %s at offset %d", base_klass->name()->as_utf8(), offset);
      tty->print("=== At SafePoint node %d can't find value of field: ", sfpt->_idx);
      flattened_field->print();
      int field_idx = C->alias_type(flattened_field)->index();
      tty->print(" (alias_idx=%d)", field_idx);
      tty->print(", which prevents elimination of: ");
      base->dump();
    }
#endif // PRODUCT
  };

  for (int i = 0; i < iklass->nof_declared_nonstatic_fields(); i++) {
    ciField* field = iklass->declared_nonstatic_field_at(i);
    if (field->is_flat()) {
      ciInlineKlass* fvk = field->type()->as_inline_klass();
      int field_offset_minus_header = offset_minus_header + field->offset_in_bytes() - fvk->payload_offset();
      bool success = add_inst_fields_to_safepoint(fvk, alloc, base, field_offset_minus_header, sfpt, value_worklist);
      if (!success) {
        return false;
      }

      // The null marker of a field is added right after we scalarize that field
      if (!field->is_null_free()) {
        int nm_offset = offset_minus_header + field->null_marker_offset();
        Node* null_marker = value_from_mem(sfpt->memory(), sfpt->control(), T_BOOLEAN, TypeInt::BOOL, base_type->with_offset(nm_offset), alloc);
        if (null_marker == nullptr) {
          report_failure(nm_offset);
          return false;
        }
        process_field_value_at_safepoint(TypeInt::BOOL, null_marker, sfpt, value_worklist);
      }

      continue;
    }

    int offset = offset_minus_header + field->offset_in_bytes();
    ciType* elem_type = field->type();
    BasicType basic_elem_type = field->layout_type();

    const Type* field_type;
    if (is_reference_type(basic_elem_type)) {
      if (!elem_type->is_loaded()) {
        field_type = TypeInstPtr::BOTTOM;
      } else {
        field_type = TypeOopPtr::make_from_klass(elem_type->as_klass());
      }
      if (UseCompressedOops) {
        field_type = field_type->make_narrowoop();
        basic_elem_type = T_NARROWOOP;
      }
    } else {
      field_type = Type::get_const_basic_type(basic_elem_type);
    }

    const TypeInstPtr* field_addr_type = base_type->add_offset(offset)->isa_instptr();
    Node* field_val = value_from_mem(sfpt->memory(), sfpt->control(), basic_elem_type, field_type, field_addr_type, alloc);
    if (field_val == nullptr) {
      report_failure(offset);
      return false;
    }
    process_field_value_at_safepoint(field_type, field_val, sfpt, value_worklist);
  }

  return true;
}

SafePointScalarObjectNode* PhaseMacroExpand::create_scalarized_object_description(AllocateNode* alloc, SafePointNode* sfpt,
                                                                                  Unique_Node_List* value_worklist) {
  // Fields of scalar objs are referenced only at the end
  // of regular debuginfo at the last (youngest) JVMS.
  // Record relative start index.
  ciInstanceKlass* iklass    = nullptr;
  const TypeOopPtr* res_type = nullptr;
  int nfields                = 0;
  uint first_ind             = (sfpt->req() - sfpt->jvms()->scloff());
  Node* res                  = alloc->result_cast();

  assert(res == nullptr || res->is_CheckCastPP(), "unexpected AllocateNode result");
  assert(sfpt->jvms() != nullptr, "missed JVMS");
  uint before_sfpt_req = sfpt->req();

  if (res != nullptr) { // Could be null when there are no users
    res_type = _igvn.type(res)->isa_oopptr();

    if (res_type->isa_instptr()) {
      // find the fields of the class which will be needed for safepoint debug information
      iklass = res_type->is_instptr()->instance_klass();
      nfields = iklass->nof_nonstatic_fields();
    } else {
      // find the array's elements which will be needed for safepoint debug information
      nfields = alloc->in(AllocateNode::ALength)->find_int_con(-1);
      assert(nfields >= 0, "must be an array klass.");
    }

    if (res->bottom_type()->is_inlinetypeptr()) {
      // Nullable inline types have a null marker field which is added to the safepoint when scalarizing them (see
      // InlineTypeNode::make_scalar_in_safepoint()). When having circular inline types, we stop scalarizing at depth 1
      // to avoid an endless recursion. Therefore, we do not have a SafePointScalarObjectNode node here, yet.
      // We are about to create a SafePointScalarObjectNode as if this is a normal object. Add an additional int input
      // with value 1 which sets the null marker to true to indicate that the object is always non-null. This input is checked
      // later in PhaseOutput::filLocArray() for inline types.
      sfpt->add_req(_igvn.intcon(1));
    }
  }

  SafePointScalarObjectNode* sobj = new SafePointScalarObjectNode(res_type, alloc, first_ind, sfpt->jvms()->depth(), nfields);
  sobj->init_req(0, C->root());
  transform_later(sobj);

  if (res == nullptr) {
    sfpt->jvms()->set_endoff(sfpt->req());
    return sobj;
  }

  bool success;
  if (iklass == nullptr) {
    success = add_array_elems_to_safepoint(alloc, res_type->is_aryptr(), sfpt, value_worklist);
  } else {
    success = add_inst_fields_to_safepoint(iklass, alloc, res, 0, sfpt, value_worklist);
  }

  // We weren't able to find a value for this field, remove all the fields added to the safepoint
  if (!success) {
    for (uint i = sfpt->req() - 1; i >= before_sfpt_req; i--) {
      sfpt->del_req(i);
    }
    _igvn._worklist.push(sfpt);
    return nullptr;
  }

  sfpt->jvms()->set_endoff(sfpt->req());
  return sobj;
}

// Do scalar replacement.
bool PhaseMacroExpand::scalar_replacement(AllocateNode *alloc, GrowableArray <SafePointNode *>& safepoints) {
  GrowableArray <SafePointNode *> safepoints_done;
  Node* res = alloc->result_cast();
  assert(res == nullptr || res->is_CheckCastPP(), "unexpected AllocateNode result");
  const TypeOopPtr* res_type = nullptr;
  if (res != nullptr) { // Could be null when there are no users
    res_type = _igvn.type(res)->isa_oopptr();
  }

  // Process the safepoint uses
  Unique_Node_List value_worklist;
  while (safepoints.length() > 0) {
    SafePointNode* sfpt = safepoints.pop();
    SafePointScalarObjectNode* sobj = create_scalarized_object_description(alloc, sfpt, &value_worklist);

    if (sobj == nullptr) {
      undo_previous_scalarizations(safepoints_done, alloc);
      return false;
    }

    // Now make a pass over the debug information replacing any references
    // to the allocated object with "sobj"
    JVMState *jvms = sfpt->jvms();
    sfpt->replace_edges_in_range(res, sobj, jvms->debug_start(), jvms->debug_end(), &_igvn);
    _igvn._worklist.push(sfpt);

    // keep it for rollback
    safepoints_done.append_if_missing(sfpt);
  }
  // Scalarize inline types that were added to the safepoint.
  // Don't allow linking a constant oop (if available) for flat array elements
  // because Deoptimization::reassign_flat_array_elements needs field values.
  bool allow_oop = (res_type != nullptr) && !res_type->is_flat();
  for (uint i = 0; i < value_worklist.size(); ++i) {
    InlineTypeNode* vt = value_worklist.at(i)->as_InlineType();
    vt->make_scalar_in_safepoints(&_igvn, allow_oop);
  }
  return true;
}

static void disconnect_projections(MultiNode* n, PhaseIterGVN& igvn) {
  Node* ctl_proj = n->proj_out_or_null(TypeFunc::Control);
  Node* mem_proj = n->proj_out_or_null(TypeFunc::Memory);
  if (ctl_proj != nullptr) {
    igvn.replace_node(ctl_proj, n->in(0));
  }
  if (mem_proj != nullptr) {
    igvn.replace_node(mem_proj, n->in(TypeFunc::Memory));
  }
}

// Process users of eliminated allocation.
void PhaseMacroExpand::process_users_of_allocation(CallNode *alloc, bool inline_alloc) {
  Unique_Node_List worklist;
  Node* res = alloc->result_cast();
  if (res != nullptr) {
    worklist.push(res);
  }
  while (worklist.size() > 0) {
    res = worklist.pop();
    for (DUIterator_Last jmin, j = res->last_outs(jmin); j >= jmin; ) {
      Node *use = res->last_out(j);
      uint oc1 = res->outcnt();

      if (use->is_AddP()) {
        for (DUIterator_Last kmin, k = use->last_outs(kmin); k >= kmin; ) {
          Node *n = use->last_out(k);
          uint oc2 = use->outcnt();
          if (n->is_Store()) {
            for (DUIterator_Fast pmax, p = n->fast_outs(pmax); p < pmax; p++) {
              MemBarNode* mb = n->fast_out(p)->isa_MemBar();
              if (mb != nullptr && mb->req() <= MemBarNode::Precedent && mb->in(MemBarNode::Precedent) == n) {
                // MemBarVolatiles should have been removed by MemBarNode::Ideal() for non-inline allocations
                assert(inline_alloc, "MemBarVolatile should be eliminated for non-escaping object");
                mb->remove(&_igvn);
              }
            }
            _igvn.replace_node(n, n->in(MemNode::Memory));
          } else {
            eliminate_gc_barrier(n);
          }
          k -= (oc2 - use->outcnt());
        }
        _igvn.remove_dead_node(use);
      } else if (use->is_ArrayCopy()) {
        // Disconnect ArrayCopy node
        ArrayCopyNode* ac = use->as_ArrayCopy();
        if (ac->is_clonebasic()) {
          Node* membar_after = ac->proj_out(TypeFunc::Control)->unique_ctrl_out();
          disconnect_projections(ac, _igvn);
          assert(alloc->in(TypeFunc::Memory)->is_Proj() && alloc->in(TypeFunc::Memory)->in(0)->Opcode() == Op_MemBarCPUOrder, "mem barrier expected before allocation");
          Node* membar_before = alloc->in(TypeFunc::Memory)->in(0);
          disconnect_projections(membar_before->as_MemBar(), _igvn);
          if (membar_after->is_MemBar()) {
            disconnect_projections(membar_after->as_MemBar(), _igvn);
          }
        } else {
          assert(ac->is_arraycopy_validated() ||
                 ac->is_copyof_validated() ||
                 ac->is_copyofrange_validated(), "unsupported");
          CallProjections* callprojs = ac->extract_projections(true);

          _igvn.replace_node(callprojs->fallthrough_ioproj, ac->in(TypeFunc::I_O));
          _igvn.replace_node(callprojs->fallthrough_memproj, ac->in(TypeFunc::Memory));
          _igvn.replace_node(callprojs->fallthrough_catchproj, ac->in(TypeFunc::Control));

          // Set control to top. IGVN will remove the remaining projections
          ac->set_req(0, top());
          ac->replace_edge(res, top(), &_igvn);

          // Disconnect src right away: it can help find new
          // opportunities for allocation elimination
          Node* src = ac->in(ArrayCopyNode::Src);
          ac->replace_edge(src, top(), &_igvn);
          // src can be top at this point if src and dest of the
          // arraycopy were the same
          if (src->outcnt() == 0 && !src->is_top()) {
            _igvn.remove_dead_node(src);
          }
        }
        _igvn._worklist.push(ac);
      } else if (use->is_InlineType()) {
        assert(use->as_InlineType()->get_oop() == res, "unexpected inline type ptr use");
        // Cut off oop input and remove known instance id from type
        _igvn.rehash_node_delayed(use);
        use->as_InlineType()->set_oop(_igvn, _igvn.zerocon(T_OBJECT));
        use->as_InlineType()->set_is_buffered(_igvn, false);
        const TypeOopPtr* toop = _igvn.type(use)->is_oopptr()->cast_to_instance_id(TypeOopPtr::InstanceBot);
        _igvn.set_type(use, toop);
        use->as_InlineType()->set_type(toop);
        // Process users
        for (DUIterator_Fast kmax, k = use->fast_outs(kmax); k < kmax; k++) {
          Node* u = use->fast_out(k);
          if (!u->is_InlineType() && !u->is_StoreFlat()) {
            worklist.push(u);
          }
        }
      } else if (use->Opcode() == Op_StoreX && use->in(MemNode::Address) == res) {
        // Store to mark word of inline type larval buffer
        assert(inline_alloc, "Unexpected store to mark word");
        _igvn.replace_node(use, use->in(MemNode::Memory));
      } else if (use->Opcode() == Op_MemBarRelease || use->Opcode() == Op_MemBarStoreStore) {
        // Inline type buffer allocations are followed by a membar
        assert(inline_alloc, "Unexpected MemBarRelease");
        use->as_MemBar()->remove(&_igvn);
      } else {
        eliminate_gc_barrier(use);
      }
      j -= (oc1 - res->outcnt());
    }
    assert(res->outcnt() == 0, "all uses of allocated objects must be deleted");
    _igvn.remove_dead_node(res);
  }

  //
  // Process other users of allocation's projections
  //
  if (_callprojs->resproj[0] != nullptr && _callprojs->resproj[0]->outcnt() != 0) {
    // First disconnect stores captured by Initialize node.
    // If Initialize node is eliminated first in the following code,
    // it will kill such stores and DUIterator_Last will assert.
    for (DUIterator_Fast jmax, j = _callprojs->resproj[0]->fast_outs(jmax);  j < jmax; j++) {
      Node* use = _callprojs->resproj[0]->fast_out(j);
      if (use->is_AddP()) {
        // raw memory addresses used only by the initialization
        _igvn.replace_node(use, C->top());
        --j; --jmax;
      }
    }
    for (DUIterator_Last jmin, j = _callprojs->resproj[0]->last_outs(jmin); j >= jmin; ) {
      Node* use = _callprojs->resproj[0]->last_out(j);
      uint oc1 = _callprojs->resproj[0]->outcnt();
      if (use->is_Initialize()) {
        // Eliminate Initialize node.
        InitializeNode *init = use->as_Initialize();
        Node *ctrl_proj = init->proj_out_or_null(TypeFunc::Control);
        if (ctrl_proj != nullptr) {
          _igvn.replace_node(ctrl_proj, init->in(TypeFunc::Control));
#ifdef ASSERT
          // If the InitializeNode has no memory out, it will die, and tmp will become null
          Node* tmp = init->in(TypeFunc::Control);
          assert(tmp == nullptr || tmp == _callprojs->fallthrough_catchproj, "allocation control projection");
#endif
        }
        Node* mem = init->in(TypeFunc::Memory);
#ifdef ASSERT
        if (init->number_of_projs(TypeFunc::Memory) > 0) {
          if (mem->is_MergeMem()) {
            assert(mem->as_MergeMem()->memory_at(Compile::AliasIdxRaw) == _callprojs->fallthrough_memproj, "allocation memory projection");
          } else {
            assert(mem == _callprojs->fallthrough_memproj, "allocation memory projection");
          }
        }
#endif
        init->replace_mem_projs_by(mem, &_igvn);
        assert(init->outcnt() == 0, "should only have had a control and some memory projections, and we removed them");
      } else if (use->Opcode() == Op_MemBarStoreStore) {
        // Inline type buffer allocations are followed by a membar
        assert(inline_alloc, "Unexpected MemBarStoreStore");
        use->as_MemBar()->remove(&_igvn);
      } else  {
        assert(false, "only Initialize or AddP expected");
      }
      j -= (oc1 - _callprojs->resproj[0]->outcnt());
    }
  }
  if (_callprojs->fallthrough_catchproj != nullptr) {
    _igvn.replace_node(_callprojs->fallthrough_catchproj, alloc->in(TypeFunc::Control));
  }
  if (_callprojs->fallthrough_memproj != nullptr) {
    _igvn.replace_node(_callprojs->fallthrough_memproj, alloc->in(TypeFunc::Memory));
  }
  if (_callprojs->catchall_memproj != nullptr) {
    _igvn.replace_node(_callprojs->catchall_memproj, C->top());
  }
  if (_callprojs->fallthrough_ioproj != nullptr) {
    _igvn.replace_node(_callprojs->fallthrough_ioproj, alloc->in(TypeFunc::I_O));
  }
  if (_callprojs->catchall_ioproj != nullptr) {
    _igvn.replace_node(_callprojs->catchall_ioproj, C->top());
  }
  if (_callprojs->catchall_catchproj != nullptr) {
    _igvn.replace_node(_callprojs->catchall_catchproj, C->top());
  }
}

bool PhaseMacroExpand::eliminate_allocate_node(AllocateNode *alloc) {
  // If reallocation fails during deoptimization we'll pop all
  // interpreter frames for this compiled frame and that won't play
  // nice with JVMTI popframe.
  // We avoid this issue by eager reallocation when the popframe request
  // is received.
  if (!EliminateAllocations) {
    return false;
  }
  Node* klass = alloc->in(AllocateNode::KlassNode);
  const TypeKlassPtr* tklass = _igvn.type(klass)->is_klassptr();

  // Attempt to eliminate inline type buffer allocations
  // regardless of usage and escape/replaceable status.
  bool inline_alloc = tklass->isa_instklassptr() &&
                      tklass->is_instklassptr()->instance_klass()->is_inlinetype();
  if (!alloc->_is_non_escaping && !inline_alloc) {
    return false;
  }
  // Eliminate boxing allocations which are not used
  // regardless scalar replaceable status.
  Node* res = alloc->result_cast();
  bool boxing_alloc = (res == nullptr) && C->eliminate_boxing() &&
                      tklass->isa_instklassptr() &&
                      tklass->is_instklassptr()->instance_klass()->is_box_klass();
  if (!alloc->_is_scalar_replaceable && !boxing_alloc && !inline_alloc) {
    return false;
  }

  _callprojs = alloc->extract_projections(false /*separate_io_proj*/, false /*do_asserts*/);

  GrowableArray <SafePointNode *> safepoints;
  if (!can_eliminate_allocation(&_igvn, alloc, &safepoints)) {
    return false;
  }

  if (!alloc->_is_scalar_replaceable) {
    assert(res == nullptr || inline_alloc, "sanity");
    // We can only eliminate allocation if all debug info references
    // are already replaced with SafePointScalarObject because
    // we can't search for a fields value without instance_id.
    if (safepoints.length() > 0) {
      return false;
    }
  }

  if (!scalar_replacement(alloc, safepoints)) {
    return false;
  }

  CompileLog* log = C->log();
  if (log != nullptr) {
    log->head("eliminate_allocation type='%d'",
              log->identify(tklass->exact_klass()));
    JVMState* p = alloc->jvms();
    while (p != nullptr) {
      log->elem("jvms bci='%d' method='%d'", p->bci(), log->identify(p->method()));
      p = p->caller();
    }
    log->tail("eliminate_allocation");
  }

  process_users_of_allocation(alloc, inline_alloc);

#ifndef PRODUCT
  if (PrintEliminateAllocations) {
    if (alloc->is_AllocateArray())
      tty->print_cr("++++ Eliminated: %d AllocateArray", alloc->_idx);
    else
      tty->print_cr("++++ Eliminated: %d Allocate", alloc->_idx);
  }
#endif

  return true;
}

bool PhaseMacroExpand::eliminate_boxing_node(CallStaticJavaNode *boxing) {
  // EA should remove all uses of non-escaping boxing node.
  if (!C->eliminate_boxing() || boxing->proj_out_or_null(TypeFunc::Parms) != nullptr) {
    return false;
  }

  assert(boxing->result_cast() == nullptr, "unexpected boxing node result");

  _callprojs = boxing->extract_projections(false /*separate_io_proj*/, false /*do_asserts*/);

  const TypeTuple* r = boxing->tf()->range_sig();
  assert(r->cnt() > TypeFunc::Parms, "sanity");
  const TypeInstPtr* t = r->field_at(TypeFunc::Parms)->isa_instptr();
  assert(t != nullptr, "sanity");

  CompileLog* log = C->log();
  if (log != nullptr) {
    log->head("eliminate_boxing type='%d'",
              log->identify(t->instance_klass()));
    JVMState* p = boxing->jvms();
    while (p != nullptr) {
      log->elem("jvms bci='%d' method='%d'", p->bci(), log->identify(p->method()));
      p = p->caller();
    }
    log->tail("eliminate_boxing");
  }

  process_users_of_allocation(boxing);

#ifndef PRODUCT
  if (PrintEliminateAllocations) {
    tty->print("++++ Eliminated: %d ", boxing->_idx);
    boxing->method()->print_short_name(tty);
    tty->cr();
  }
#endif

  return true;
}


Node* PhaseMacroExpand::make_load_raw(Node* ctl, Node* mem, Node* base, int offset, const Type* value_type, BasicType bt) {
  Node* adr = basic_plus_adr(top(), base, offset);
  const TypePtr* adr_type = adr->bottom_type()->is_ptr();
  Node* value = LoadNode::make(_igvn, ctl, mem, adr, adr_type, value_type, bt, MemNode::unordered);
  transform_later(value);
  return value;
}


Node* PhaseMacroExpand::make_store_raw(Node* ctl, Node* mem, Node* base, int offset, Node* value, BasicType bt) {
  Node* adr = basic_plus_adr(top(), base, offset);
  mem = StoreNode::make(_igvn, ctl, mem, adr, nullptr, value, bt, MemNode::unordered);
  transform_later(mem);
  return mem;
}

//=============================================================================
//
//                              A L L O C A T I O N
//
// Allocation attempts to be fast in the case of frequent small objects.
// It breaks down like this:
//
// 1) Size in doublewords is computed.  This is a constant for objects and
// variable for most arrays.  Doubleword units are used to avoid size
// overflow of huge doubleword arrays.  We need doublewords in the end for
// rounding.
//
// 2) Size is checked for being 'too large'.  Too-large allocations will go
// the slow path into the VM.  The slow path can throw any required
// exceptions, and does all the special checks for very large arrays.  The
// size test can constant-fold away for objects.  For objects with
// finalizers it constant-folds the otherway: you always go slow with
// finalizers.
//
// 3) If NOT using TLABs, this is the contended loop-back point.
// Load-Locked the heap top.  If using TLABs normal-load the heap top.
//
// 4) Check that heap top + size*8 < max.  If we fail go the slow ` route.
// NOTE: "top+size*8" cannot wrap the 4Gig line!  Here's why: for largish
// "size*8" we always enter the VM, where "largish" is a constant picked small
// enough that there's always space between the eden max and 4Gig (old space is
// there so it's quite large) and large enough that the cost of entering the VM
// is dwarfed by the cost to initialize the space.
//
// 5) If NOT using TLABs, Store-Conditional the adjusted heap top back
// down.  If contended, repeat at step 3.  If using TLABs normal-store
// adjusted heap top back down; there is no contention.
//
// 6) If !ZeroTLAB then Bulk-clear the object/array.  Fill in klass & mark
// fields.
//
// 7) Merge with the slow-path; cast the raw memory pointer to the correct
// oop flavor.
//
//=============================================================================
// FastAllocateSizeLimit value is in DOUBLEWORDS.
// Allocations bigger than this always go the slow route.
// This value must be small enough that allocation attempts that need to
// trigger exceptions go the slow route.  Also, it must be small enough so
// that heap_top + size_in_bytes does not wrap around the 4Gig limit.
//=============================================================================j//
// %%% Here is an old comment from parseHelper.cpp; is it outdated?
// The allocator will coalesce int->oop copies away.  See comment in
// coalesce.cpp about how this works.  It depends critically on the exact
// code shape produced here, so if you are changing this code shape
// make sure the GC info for the heap-top is correct in and around the
// slow-path call.
//

void PhaseMacroExpand::expand_allocate_common(
            AllocateNode* alloc, // allocation node to be expanded
            Node* length,  // array length for an array allocation
            Node* init_val, // value to initialize the array with
            const TypeFunc* slow_call_type, // Type of slow call
            address slow_call_address,  // Address of slow call
            Node* valid_length_test // whether length is valid or not
    )
{
  Node* ctrl = alloc->in(TypeFunc::Control);
  Node* mem  = alloc->in(TypeFunc::Memory);
  Node* i_o  = alloc->in(TypeFunc::I_O);
  Node* size_in_bytes     = alloc->in(AllocateNode::AllocSize);
  Node* klass_node        = alloc->in(AllocateNode::KlassNode);
  Node* initial_slow_test = alloc->in(AllocateNode::InitialTest);
  assert(ctrl != nullptr, "must have control");

  // We need a Region and corresponding Phi's to merge the slow-path and fast-path results.
  // they will not be used if "always_slow" is set
  enum { slow_result_path = 1, fast_result_path = 2 };
  Node *result_region = nullptr;
  Node *result_phi_rawmem = nullptr;
  Node *result_phi_rawoop = nullptr;
  Node *result_phi_i_o = nullptr;

  // The initial slow comparison is a size check, the comparison
  // we want to do is a BoolTest::gt
  bool expand_fast_path = true;
  int tv = _igvn.find_int_con(initial_slow_test, -1);
  if (tv >= 0) {
    // InitialTest has constant result
    //   0 - can fit in TLAB
    //   1 - always too big or negative
    assert(tv <= 1, "0 or 1 if a constant");
    expand_fast_path = (tv == 0);
    initial_slow_test = nullptr;
  } else {
    initial_slow_test = BoolNode::make_predicate(initial_slow_test, &_igvn);
  }

  if (!UseTLAB) {
    // Force slow-path allocation
    expand_fast_path = false;
    initial_slow_test = nullptr;
  }

  bool allocation_has_use = (alloc->result_cast() != nullptr);
  if (!allocation_has_use) {
    InitializeNode* init = alloc->initialization();
    if (init != nullptr) {
      init->remove(&_igvn);
    }
    if (expand_fast_path && (initial_slow_test == nullptr)) {
      // Remove allocation node and return.
      // Size is a non-negative constant -> no initial check needed -> directly to fast path.
      // Also, no usages -> empty fast path -> no fall out to slow path -> nothing left.
#ifndef PRODUCT
      if (PrintEliminateAllocations) {
        tty->print("NotUsed ");
        Node* res = alloc->proj_out_or_null(TypeFunc::Parms);
        if (res != nullptr) {
          res->dump();
        } else {
          alloc->dump();
        }
      }
#endif
      yank_alloc_node(alloc);
      return;
    }
  }

  enum { too_big_or_final_path = 1, need_gc_path = 2 };
  Node *slow_region = nullptr;
  Node *toobig_false = ctrl;

  // generate the initial test if necessary
  if (initial_slow_test != nullptr ) {
    assert (expand_fast_path, "Only need test if there is a fast path");
    slow_region = new RegionNode(3);

    // Now make the initial failure test.  Usually a too-big test but
    // might be a TRUE for finalizers.
    IfNode *toobig_iff = new IfNode(ctrl, initial_slow_test, PROB_MIN, COUNT_UNKNOWN);
    transform_later(toobig_iff);
    // Plug the failing-too-big test into the slow-path region
    Node* toobig_true = new IfTrueNode(toobig_iff);
    transform_later(toobig_true);
    slow_region    ->init_req( too_big_or_final_path, toobig_true );
    toobig_false = new IfFalseNode(toobig_iff);
    transform_later(toobig_false);
  } else {
    // No initial test, just fall into next case
    assert(allocation_has_use || !expand_fast_path, "Should already have been handled");
    toobig_false = ctrl;
    DEBUG_ONLY(slow_region = NodeSentinel);
  }

  // If we are here there are several possibilities
  // - expand_fast_path is false - then only a slow path is expanded. That's it.
  // no_initial_check means a constant allocation.
  // - If check always evaluates to false -> expand_fast_path is false (see above)
  // - If check always evaluates to true -> directly into fast path (but may bailout to slowpath)
  // if !allocation_has_use the fast path is empty
  // if !allocation_has_use && no_initial_check
  // - Then there are no fastpath that can fall out to slowpath -> no allocation code at all.
  //   removed by yank_alloc_node above.

  Node *slow_mem = mem;  // save the current memory state for slow path
  // generate the fast allocation code unless we know that the initial test will always go slow
  if (expand_fast_path) {
    // Fast path modifies only raw memory.
    if (mem->is_MergeMem()) {
      mem = mem->as_MergeMem()->memory_at(Compile::AliasIdxRaw);
    }

    // allocate the Region and Phi nodes for the result
    result_region = new RegionNode(3);
    result_phi_rawmem = new PhiNode(result_region, Type::MEMORY, TypeRawPtr::BOTTOM);
    result_phi_i_o    = new PhiNode(result_region, Type::ABIO); // I/O is used for Prefetch

    // Grab regular I/O before optional prefetch may change it.
    // Slow-path does no I/O so just set it to the original I/O.
    result_phi_i_o->init_req(slow_result_path, i_o);

    // Name successful fast-path variables
    Node* fast_oop_ctrl;
    Node* fast_oop_rawmem;

    if (allocation_has_use) {
      Node* needgc_ctrl = nullptr;
      result_phi_rawoop = new PhiNode(result_region, TypeRawPtr::BOTTOM);

      intx prefetch_lines = length != nullptr ? AllocatePrefetchLines : AllocateInstancePrefetchLines;
      BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
      Node* fast_oop = bs->obj_allocate(this, mem, toobig_false, size_in_bytes, i_o, needgc_ctrl,
                                        fast_oop_ctrl, fast_oop_rawmem,
                                        prefetch_lines);

      if (initial_slow_test != nullptr) {
        // This completes all paths into the slow merge point
        slow_region->init_req(need_gc_path, needgc_ctrl);
        transform_later(slow_region);
      } else {
        // No initial slow path needed!
        // Just fall from the need-GC path straight into the VM call.
        slow_region = needgc_ctrl;
      }

      InitializeNode* init = alloc->initialization();
      fast_oop_rawmem = initialize_object(alloc,
                                          fast_oop_ctrl, fast_oop_rawmem, fast_oop,
                                          klass_node, length, size_in_bytes);
      expand_initialize_membar(alloc, init, fast_oop_ctrl, fast_oop_rawmem);
      expand_dtrace_alloc_probe(alloc, fast_oop, fast_oop_ctrl, fast_oop_rawmem);

      result_phi_rawoop->init_req(fast_result_path, fast_oop);
    } else {
      assert (initial_slow_test != nullptr, "sanity");
      fast_oop_ctrl   = toobig_false;
      fast_oop_rawmem = mem;
      transform_later(slow_region);
    }

    // Plug in the successful fast-path into the result merge point
    result_region    ->init_req(fast_result_path, fast_oop_ctrl);
    result_phi_i_o   ->init_req(fast_result_path, i_o);
    result_phi_rawmem->init_req(fast_result_path, fast_oop_rawmem);
  } else {
    slow_region = ctrl;
    result_phi_i_o = i_o; // Rename it to use in the following code.
  }

  // Generate slow-path call
  CallNode *call = new CallStaticJavaNode(slow_call_type, slow_call_address,
                               OptoRuntime::stub_name(slow_call_address),
                               TypePtr::BOTTOM);
  call->init_req(TypeFunc::Control,   slow_region);
  call->init_req(TypeFunc::I_O,       top());    // does no i/o
  call->init_req(TypeFunc::Memory,    slow_mem); // may gc ptrs
  call->init_req(TypeFunc::ReturnAdr, alloc->in(TypeFunc::ReturnAdr));
  call->init_req(TypeFunc::FramePtr,  alloc->in(TypeFunc::FramePtr));

  call->init_req(TypeFunc::Parms+0, klass_node);
  if (length != nullptr) {
    call->init_req(TypeFunc::Parms+1, length);
    if (init_val != nullptr) {
      call->init_req(TypeFunc::Parms+2, init_val);
    }
  } else {
    // Let the runtime know if this is a larval allocation
    call->init_req(TypeFunc::Parms+1, _igvn.intcon(alloc->_larval));
  }

  // Copy debug information and adjust JVMState information, then replace
  // allocate node with the call
  call->copy_call_debug_info(&_igvn, alloc);
  // For array allocations, copy the valid length check to the call node so Compile::final_graph_reshaping() can verify
  // that the call has the expected number of CatchProj nodes (in case the allocation always fails and the fallthrough
  // path dies).
  if (valid_length_test != nullptr) {
    call->add_req(valid_length_test);
  }
  if (expand_fast_path) {
    call->set_cnt(PROB_UNLIKELY_MAG(4));  // Same effect as RC_UNCOMMON.
  } else {
    // Hook i_o projection to avoid its elimination during allocation
    // replacement (when only a slow call is generated).
    call->set_req(TypeFunc::I_O, result_phi_i_o);
  }
  _igvn.replace_node(alloc, call);
  transform_later(call);

  // Identify the output projections from the allocate node and
  // adjust any references to them.
  // The control and io projections look like:
  //
  //        v---Proj(ctrl) <-----+   v---CatchProj(ctrl)
  //  Allocate                   Catch
  //        ^---Proj(io) <-------+   ^---CatchProj(io)
  //
  //  We are interested in the CatchProj nodes.
  //
  _callprojs = call->extract_projections(false /*separate_io_proj*/, false /*do_asserts*/);

  // An allocate node has separate memory projections for the uses on
  // the control and i_o paths. Replace the control memory projection with
  // result_phi_rawmem (unless we are only generating a slow call when
  // both memory projections are combined)
  if (expand_fast_path && _callprojs->fallthrough_memproj != nullptr) {
    _igvn.replace_in_uses(_callprojs->fallthrough_memproj, result_phi_rawmem);
  }
  // Now change uses of catchall_memproj to use fallthrough_memproj and delete
  // catchall_memproj so we end up with a call that has only 1 memory projection.
  if (_callprojs->catchall_memproj != nullptr) {
    if (_callprojs->fallthrough_memproj == nullptr) {
      _callprojs->fallthrough_memproj = new ProjNode(call, TypeFunc::Memory);
      transform_later(_callprojs->fallthrough_memproj);
    }
    _igvn.replace_in_uses(_callprojs->catchall_memproj, _callprojs->fallthrough_memproj);
    _igvn.remove_dead_node(_callprojs->catchall_memproj);
  }

  // An allocate node has separate i_o projections for the uses on the control
  // and i_o paths. Always replace the control i_o projection with result i_o
  // otherwise incoming i_o become dead when only a slow call is generated
  // (it is different from memory projections where both projections are
  // combined in such case).
  if (_callprojs->fallthrough_ioproj != nullptr) {
    _igvn.replace_in_uses(_callprojs->fallthrough_ioproj, result_phi_i_o);
  }
  // Now change uses of catchall_ioproj to use fallthrough_ioproj and delete
  // catchall_ioproj so we end up with a call that has only 1 i_o projection.
  if (_callprojs->catchall_ioproj != nullptr) {
    if (_callprojs->fallthrough_ioproj == nullptr) {
      _callprojs->fallthrough_ioproj = new ProjNode(call, TypeFunc::I_O);
      transform_later(_callprojs->fallthrough_ioproj);
    }
    _igvn.replace_in_uses(_callprojs->catchall_ioproj, _callprojs->fallthrough_ioproj);
    _igvn.remove_dead_node(_callprojs->catchall_ioproj);
  }

  // if we generated only a slow call, we are done
  if (!expand_fast_path) {
    // Now we can unhook i_o.
    if (result_phi_i_o->outcnt() > 1) {
      call->set_req(TypeFunc::I_O, top());
    } else {
      assert(result_phi_i_o->unique_ctrl_out() == call, "sanity");
      // Case of new array with negative size known during compilation.
      // AllocateArrayNode::Ideal() optimization disconnect unreachable
      // following code since call to runtime will throw exception.
      // As result there will be no users of i_o after the call.
      // Leave i_o attached to this call to avoid problems in preceding graph.
    }
    return;
  }

  if (_callprojs->fallthrough_catchproj != nullptr) {
    ctrl = _callprojs->fallthrough_catchproj->clone();
    transform_later(ctrl);
    _igvn.replace_node(_callprojs->fallthrough_catchproj, result_region);
  } else {
    ctrl = top();
  }
  Node *slow_result;
  if (_callprojs->resproj[0] == nullptr) {
    // no uses of the allocation result
    slow_result = top();
  } else {
    slow_result = _callprojs->resproj[0]->clone();
    transform_later(slow_result);
    _igvn.replace_node(_callprojs->resproj[0], result_phi_rawoop);
  }

  // Plug slow-path into result merge point
  result_region->init_req( slow_result_path, ctrl);
  transform_later(result_region);
  if (allocation_has_use) {
    result_phi_rawoop->init_req(slow_result_path, slow_result);
    transform_later(result_phi_rawoop);
  }
  result_phi_rawmem->init_req(slow_result_path, _callprojs->fallthrough_memproj);
  transform_later(result_phi_rawmem);
  transform_later(result_phi_i_o);
  // This completes all paths into the result merge point
}

// Remove alloc node that has no uses.
void PhaseMacroExpand::yank_alloc_node(AllocateNode* alloc) {
  Node* ctrl = alloc->in(TypeFunc::Control);
  Node* mem  = alloc->in(TypeFunc::Memory);
  Node* i_o  = alloc->in(TypeFunc::I_O);

  _callprojs = alloc->extract_projections(false /*separate_io_proj*/, false /*do_asserts*/);
  if (_callprojs->resproj[0] != nullptr) {
    for (DUIterator_Fast imax, i = _callprojs->resproj[0]->fast_outs(imax); i < imax; i++) {
      Node* use = _callprojs->resproj[0]->fast_out(i);
      use->isa_MemBar()->remove(&_igvn);
      --imax;
      --i; // back up iterator
    }
    assert(_callprojs->resproj[0]->outcnt() == 0, "all uses must be deleted");
    _igvn.remove_dead_node(_callprojs->resproj[0]);
  }
  if (_callprojs->fallthrough_catchproj != nullptr) {
    _igvn.replace_in_uses(_callprojs->fallthrough_catchproj, ctrl);
    _igvn.remove_dead_node(_callprojs->fallthrough_catchproj);
  }
  if (_callprojs->catchall_catchproj != nullptr) {
    _igvn.rehash_node_delayed(_callprojs->catchall_catchproj);
    _callprojs->catchall_catchproj->set_req(0, top());
  }
  if (_callprojs->fallthrough_proj != nullptr) {
    Node* catchnode = _callprojs->fallthrough_proj->unique_ctrl_out();
    _igvn.remove_dead_node(catchnode);
    _igvn.remove_dead_node(_callprojs->fallthrough_proj);
  }
  if (_callprojs->fallthrough_memproj != nullptr) {
    _igvn.replace_in_uses(_callprojs->fallthrough_memproj, mem);
    _igvn.remove_dead_node(_callprojs->fallthrough_memproj);
  }
  if (_callprojs->fallthrough_ioproj != nullptr) {
    _igvn.replace_in_uses(_callprojs->fallthrough_ioproj, i_o);
    _igvn.remove_dead_node(_callprojs->fallthrough_ioproj);
  }
  if (_callprojs->catchall_memproj != nullptr) {
    _igvn.rehash_node_delayed(_callprojs->catchall_memproj);
    _callprojs->catchall_memproj->set_req(0, top());
  }
  if (_callprojs->catchall_ioproj != nullptr) {
    _igvn.rehash_node_delayed(_callprojs->catchall_ioproj);
    _callprojs->catchall_ioproj->set_req(0, top());
  }
#ifndef PRODUCT
  if (PrintEliminateAllocations) {
    if (alloc->is_AllocateArray()) {
      tty->print_cr("++++ Eliminated: %d AllocateArray", alloc->_idx);
    } else {
      tty->print_cr("++++ Eliminated: %d Allocate", alloc->_idx);
    }
  }
#endif
  _igvn.remove_dead_node(alloc);
}

void PhaseMacroExpand::expand_initialize_membar(AllocateNode* alloc, InitializeNode* init,
                                                Node*& fast_oop_ctrl, Node*& fast_oop_rawmem) {
  // If initialization is performed by an array copy, any required
  // MemBarStoreStore was already added. If the object does not
  // escape no need for a MemBarStoreStore. If the object does not
  // escape in its initializer and memory barrier (MemBarStoreStore or
  // stronger) is already added at exit of initializer, also no need
  // for a MemBarStoreStore. Otherwise we need a MemBarStoreStore
  // so that stores that initialize this object can't be reordered
  // with a subsequent store that makes this object accessible by
  // other threads.
  // Other threads include java threads and JVM internal threads
  // (for example concurrent GC threads). Current concurrent GC
  // implementation: G1 will not scan newly created object,
  // so it's safe to skip storestore barrier when allocation does
  // not escape.
  if (!alloc->does_not_escape_thread() &&
    !alloc->is_allocation_MemBar_redundant() &&
    (init == nullptr || !init->is_complete_with_arraycopy())) {
    if (init == nullptr || init->req() < InitializeNode::RawStores) {
      // No InitializeNode or no stores captured by zeroing
      // elimination. Simply add the MemBarStoreStore after object
      // initialization.
      // What we want is to prevent the compiler and the CPU from re-ordering the stores that initialize this object
      // with subsequent stores to any slice. As a consequence, this MemBar should capture the entire memory state at
      // this point in the IR and produce a new memory state that should cover all slices. However, the Initialize node
      // only captures/produces a partial memory state making it complicated to insert such a MemBar. Because
      // re-ordering by the compiler can't happen by construction (a later Store that publishes the just allocated
      // object reference is indirectly control dependent on the Initialize node), preventing reordering by the CPU is
      // sufficient. For that a MemBar on the raw memory slice is good enough.
      // If init is null, this allocation does have an InitializeNode but this logic can't locate it (see comment in
      // PhaseMacroExpand::initialize_object()).
      MemBarNode* mb = MemBarNode::make(C, Op_MemBarStoreStore, Compile::AliasIdxRaw);
      transform_later(mb);

      mb->init_req(TypeFunc::Memory, fast_oop_rawmem);
      mb->init_req(TypeFunc::Control, fast_oop_ctrl);
      fast_oop_ctrl = new ProjNode(mb, TypeFunc::Control);
      transform_later(fast_oop_ctrl);
      fast_oop_rawmem = new ProjNode(mb, TypeFunc::Memory);
      transform_later(fast_oop_rawmem);
    } else {
      // Add the MemBarStoreStore after the InitializeNode so that
      // all stores performing the initialization that were moved
      // before the InitializeNode happen before the storestore
      // barrier.

      Node* init_ctrl = init->proj_out_or_null(TypeFunc::Control);

      // See comment above that explains why a raw memory MemBar is good enough.
      MemBarNode* mb = MemBarNode::make(C, Op_MemBarStoreStore, Compile::AliasIdxRaw);
      transform_later(mb);

      Node* ctrl = new ProjNode(init, TypeFunc::Control);
      transform_later(ctrl);
      Node* old_raw_mem_proj = nullptr;
      auto find_raw_mem = [&](ProjNode* proj) {
        if (C->get_alias_index(proj->adr_type()) == Compile::AliasIdxRaw) {
          assert(old_raw_mem_proj == nullptr, "only one expected");
          old_raw_mem_proj = proj;
        }
      };
      init->for_each_proj(find_raw_mem, TypeFunc::Memory);
      assert(old_raw_mem_proj != nullptr, "should have found raw mem Proj");
      Node* raw_mem_proj = new ProjNode(init, TypeFunc::Memory);
      transform_later(raw_mem_proj);

      // The MemBarStoreStore depends on control and memory coming
      // from the InitializeNode
      mb->init_req(TypeFunc::Memory, raw_mem_proj);
      mb->init_req(TypeFunc::Control, ctrl);

      ctrl = new ProjNode(mb, TypeFunc::Control);
      transform_later(ctrl);
      Node* mem = new ProjNode(mb, TypeFunc::Memory);
      transform_later(mem);

      // All nodes that depended on the InitializeNode for control
      // and memory must now depend on the MemBarNode that itself
      // depends on the InitializeNode
      if (init_ctrl != nullptr) {
        _igvn.replace_node(init_ctrl, ctrl);
      }
      _igvn.replace_node(old_raw_mem_proj, mem);
    }
  }
}

void PhaseMacroExpand::expand_dtrace_alloc_probe(AllocateNode* alloc, Node* oop,
                                                Node*& ctrl, Node*& rawmem) {
  if (C->env()->dtrace_alloc_probes()) {
    // Slow-path call
    int size = TypeFunc::Parms + 2;
    CallLeafNode *call = new CallLeafNode(OptoRuntime::dtrace_object_alloc_Type(),
                                          CAST_FROM_FN_PTR(address,
                                          static_cast<int (*)(JavaThread*, oopDesc*)>(SharedRuntime::dtrace_object_alloc)),
                                          "dtrace_object_alloc",
                                          TypeRawPtr::BOTTOM);

    // Get base of thread-local storage area
    Node* thread = new ThreadLocalNode();
    transform_later(thread);

    call->init_req(TypeFunc::Parms + 0, thread);
    call->init_req(TypeFunc::Parms + 1, oop);
    call->init_req(TypeFunc::Control, ctrl);
    call->init_req(TypeFunc::I_O    , top()); // does no i/o
    call->init_req(TypeFunc::Memory , rawmem);
    call->init_req(TypeFunc::ReturnAdr, alloc->in(TypeFunc::ReturnAdr));
    call->init_req(TypeFunc::FramePtr, alloc->in(TypeFunc::FramePtr));
    transform_later(call);
    ctrl = new ProjNode(call, TypeFunc::Control);
    transform_later(ctrl);
    rawmem = new ProjNode(call, TypeFunc::Memory);
    transform_later(rawmem);
  }
}

// Helper for PhaseMacroExpand::expand_allocate_common.
// Initializes the newly-allocated storage.
Node* PhaseMacroExpand::initialize_object(AllocateNode* alloc,
                                          Node* control, Node* rawmem, Node* object,
                                          Node* klass_node, Node* length,
                                          Node* size_in_bytes) {
  InitializeNode* init = alloc->initialization();
  // Store the klass & mark bits
  Node* mark_node = alloc->make_ideal_mark(&_igvn, control, rawmem);
  if (!mark_node->is_Con()) {
    transform_later(mark_node);
  }
  rawmem = make_store_raw(control, rawmem, object, oopDesc::mark_offset_in_bytes(), mark_node, TypeX_X->basic_type());

  if (!UseCompactObjectHeaders) {
    rawmem = make_store_raw(control, rawmem, object, oopDesc::klass_offset_in_bytes(), klass_node, T_METADATA);
  }
  int header_size = alloc->minimum_header_size();  // conservatively small

  // Array length
  if (length != nullptr) {         // Arrays need length field
    rawmem = make_store_raw(control, rawmem, object, arrayOopDesc::length_offset_in_bytes(), length, T_INT);
    // conservatively small header size:
    header_size = arrayOopDesc::base_offset_in_bytes(T_BYTE);
    if (_igvn.type(klass_node)->isa_aryklassptr()) {   // we know the exact header size in most cases:
      BasicType elem = _igvn.type(klass_node)->is_klassptr()->as_instance_type()->isa_aryptr()->elem()->array_element_basic_type();
      if (is_reference_type(elem, true)) {
        elem = T_OBJECT;
      }
      header_size = Klass::layout_helper_header_size(Klass::array_layout_helper(elem));
    }
  }

  // Clear the object body, if necessary.
  if (init == nullptr) {
    // The init has somehow disappeared; be cautious and clear everything.
    //
    // This can happen if a node is allocated but an uncommon trap occurs
    // immediately.  In this case, the Initialize gets associated with the
    // trap, and may be placed in a different (outer) loop, if the Allocate
    // is in a loop.  If (this is rare) the inner loop gets unrolled, then
    // there can be two Allocates to one Initialize.  The answer in all these
    // edge cases is safety first.  It is always safe to clear immediately
    // within an Allocate, and then (maybe or maybe not) clear some more later.
    if (!(UseTLAB && ZeroTLAB)) {
      rawmem = ClearArrayNode::clear_memory(control, rawmem, object,
                                            alloc->in(AllocateNode::InitValue),
                                            alloc->in(AllocateNode::RawInitValue),
                                            header_size, size_in_bytes,
                                            true,
                                            &_igvn);
    }
  } else {
    if (!init->is_complete()) {
      // Try to win by zeroing only what the init does not store.
      // We can also try to do some peephole optimizations,
      // such as combining some adjacent subword stores.
      rawmem = init->complete_stores(control, rawmem, object,
                                     header_size, size_in_bytes, &_igvn);
    }
    // We have no more use for this link, since the AllocateNode goes away:
    init->set_req(InitializeNode::RawAddress, top());
    // (If we keep the link, it just confuses the register allocator,
    // who thinks he sees a real use of the address by the membar.)
  }

  return rawmem;
}

// Generate prefetch instructions for next allocations.
Node* PhaseMacroExpand::prefetch_allocation(Node* i_o, Node*& needgc_false,
                                        Node*& contended_phi_rawmem,
                                        Node* old_eden_top, Node* new_eden_top,
                                        intx lines) {
   enum { fall_in_path = 1, pf_path = 2 };
   if (UseTLAB && AllocatePrefetchStyle == 2) {
      // Generate prefetch allocation with watermark check.
      // As an allocation hits the watermark, we will prefetch starting
      // at a "distance" away from watermark.

      Node* pf_region = new RegionNode(3);
      Node* pf_phi_rawmem = new PhiNode(pf_region, Type::MEMORY,
                                                TypeRawPtr::BOTTOM);
      // I/O is used for Prefetch
      Node* pf_phi_abio = new PhiNode(pf_region, Type::ABIO);

      Node* thread = new ThreadLocalNode();
      transform_later(thread);

      Node* eden_pf_adr = new AddPNode(top()/*not oop*/, thread,
                   _igvn.MakeConX(in_bytes(JavaThread::tlab_pf_top_offset())));
      transform_later(eden_pf_adr);

      Node* old_pf_wm = new LoadPNode(needgc_false,
                                   contended_phi_rawmem, eden_pf_adr,
                                   TypeRawPtr::BOTTOM, TypeRawPtr::BOTTOM,
                                   MemNode::unordered);
      transform_later(old_pf_wm);

      // check against new_eden_top
      Node* need_pf_cmp = new CmpPNode(new_eden_top, old_pf_wm);
      transform_later(need_pf_cmp);
      Node* need_pf_bol = new BoolNode(need_pf_cmp, BoolTest::ge);
      transform_later(need_pf_bol);
      IfNode* need_pf_iff = new IfNode(needgc_false, need_pf_bol,
                                       PROB_UNLIKELY_MAG(4), COUNT_UNKNOWN);
      transform_later(need_pf_iff);

      // true node, add prefetchdistance
      Node* need_pf_true = new IfTrueNode(need_pf_iff);
      transform_later(need_pf_true);

      Node* need_pf_false = new IfFalseNode(need_pf_iff);
      transform_later(need_pf_false);

      Node* new_pf_wmt = new AddPNode(top(), old_pf_wm,
                                    _igvn.MakeConX(AllocatePrefetchDistance));
      transform_later(new_pf_wmt);
      new_pf_wmt->set_req(0, need_pf_true);

      Node* store_new_wmt = new StorePNode(need_pf_true,
                                       contended_phi_rawmem, eden_pf_adr,
                                       TypeRawPtr::BOTTOM, new_pf_wmt,
                                       MemNode::unordered);
      transform_later(store_new_wmt);

      // adding prefetches
      pf_phi_abio->init_req(fall_in_path, i_o);

      Node* prefetch_adr;
      Node* prefetch;
      uint step_size = AllocatePrefetchStepSize;
      uint distance = 0;

      for (intx i = 0; i < lines; i++) {
        prefetch_adr = new AddPNode(top(), new_pf_wmt,
                                            _igvn.MakeConX(distance));
        transform_later(prefetch_adr);
        prefetch = new PrefetchAllocationNode(i_o, prefetch_adr);
        transform_later(prefetch);
        distance += step_size;
        i_o = prefetch;
      }
      pf_phi_abio->set_req(pf_path, i_o);

      pf_region->init_req(fall_in_path, need_pf_false);
      pf_region->init_req(pf_path, need_pf_true);

      pf_phi_rawmem->init_req(fall_in_path, contended_phi_rawmem);
      pf_phi_rawmem->init_req(pf_path, store_new_wmt);

      transform_later(pf_region);
      transform_later(pf_phi_rawmem);
      transform_later(pf_phi_abio);

      needgc_false = pf_region;
      contended_phi_rawmem = pf_phi_rawmem;
      i_o = pf_phi_abio;
   } else if (UseTLAB && AllocatePrefetchStyle == 3) {
      // Insert a prefetch instruction for each allocation.
      // This code is used to generate 1 prefetch instruction per cache line.

      // Generate several prefetch instructions.
      uint step_size = AllocatePrefetchStepSize;
      uint distance = AllocatePrefetchDistance;

      // Next cache address.
      Node* cache_adr = new AddPNode(top(), old_eden_top,
                                     _igvn.MakeConX(step_size + distance));
      transform_later(cache_adr);
      cache_adr = new CastP2XNode(needgc_false, cache_adr);
      transform_later(cache_adr);
      // Address is aligned to execute prefetch to the beginning of cache line size
      // (it is important when BIS instruction is used on SPARC as prefetch).
      Node* mask = _igvn.MakeConX(~(intptr_t)(step_size-1));
      cache_adr = new AndXNode(cache_adr, mask);
      transform_later(cache_adr);
      cache_adr = new CastX2PNode(cache_adr);
      transform_later(cache_adr);

      // Prefetch
      Node* prefetch = new PrefetchAllocationNode(contended_phi_rawmem, cache_adr);
      prefetch->set_req(0, needgc_false);
      transform_later(prefetch);
      contended_phi_rawmem = prefetch;
      Node* prefetch_adr;
      distance = step_size;
      for (intx i = 1; i < lines; i++) {
        prefetch_adr = new AddPNode(top(), cache_adr,
                                            _igvn.MakeConX(distance));
        transform_later(prefetch_adr);
        prefetch = new PrefetchAllocationNode(contended_phi_rawmem, prefetch_adr);
        transform_later(prefetch);
        distance += step_size;
        contended_phi_rawmem = prefetch;
      }
   } else if (AllocatePrefetchStyle > 0) {
      // Insert a prefetch for each allocation only on the fast-path
      Node* prefetch_adr;
      Node* prefetch;
      // Generate several prefetch instructions.
      uint step_size = AllocatePrefetchStepSize;
      uint distance = AllocatePrefetchDistance;
      for (intx i = 0; i < lines; i++) {
        prefetch_adr = new AddPNode(top(), new_eden_top,
                                            _igvn.MakeConX(distance));
        transform_later(prefetch_adr);
        prefetch = new PrefetchAllocationNode(i_o, prefetch_adr);
        // Do not let it float too high, since if eden_top == eden_end,
        // both might be null.
        if (i == 0) { // Set control for first prefetch, next follows it
          prefetch->init_req(0, needgc_false);
        }
        transform_later(prefetch);
        distance += step_size;
        i_o = prefetch;
      }
   }
   return i_o;
}


void PhaseMacroExpand::expand_allocate(AllocateNode *alloc) {
  expand_allocate_common(alloc, nullptr, nullptr,
                         OptoRuntime::new_instance_Type(),
                         OptoRuntime::new_instance_Java(), nullptr);
}

void PhaseMacroExpand::expand_allocate_array(AllocateArrayNode *alloc) {
  Node* length = alloc->in(AllocateNode::ALength);
  Node* valid_length_test = alloc->in(AllocateNode::ValidLengthTest);
  InitializeNode* init = alloc->initialization();
  Node* klass_node = alloc->in(AllocateNode::KlassNode);
  Node* init_value = alloc->in(AllocateNode::InitValue);
  const TypeAryKlassPtr* ary_klass_t = _igvn.type(klass_node)->isa_aryklassptr();
  assert(!ary_klass_t || !ary_klass_t->klass_is_exact() || !ary_klass_t->exact_klass()->is_obj_array_klass() ||
         ary_klass_t->is_refined_type(), "Must be a refined array klass");
  const TypeFunc* slow_call_type;
  address slow_call_address;  // Address of slow call
  if (init != nullptr && init->is_complete_with_arraycopy() &&
      ary_klass_t && ary_klass_t->elem()->isa_klassptr() == nullptr) {
    // Don't zero type array during slow allocation in VM since
    // it will be initialized later by arraycopy in compiled code.
    slow_call_address = OptoRuntime::new_array_nozero_Java();
    slow_call_type = OptoRuntime::new_array_nozero_Type();
  } else {
    slow_call_address = OptoRuntime::new_array_Java();
    slow_call_type = OptoRuntime::new_array_Type();

    if (init_value == nullptr) {
      init_value = _igvn.zerocon(T_OBJECT);
    } else if (UseCompressedOops) {
      init_value = transform_later(new DecodeNNode(init_value, init_value->bottom_type()->make_ptr()));
    }
  }
  expand_allocate_common(alloc, length, init_value,
                         slow_call_type,
                         slow_call_address, valid_length_test);
}

//-------------------mark_eliminated_box----------------------------------
//
// During EA obj may point to several objects but after few ideal graph
// transformations (CCP) it may point to only one non escaping object
// (but still using phi), corresponding locks and unlocks will be marked
// for elimination. Later obj could be replaced with a new node (new phi)
// and which does not have escape information. And later after some graph
// reshape other locks and unlocks (which were not marked for elimination
// before) are connected to this new obj (phi) but they still will not be
// marked for elimination since new obj has no escape information.
// Mark all associated (same box and obj) lock and unlock nodes for
// elimination if some of them marked already.
void PhaseMacroExpand::mark_eliminated_box(Node* box, Node* obj) {
  BoxLockNode* oldbox = box->as_BoxLock();
  if (oldbox->is_eliminated()) {
    return; // This BoxLock node was processed already.
  }
  assert(!oldbox->is_unbalanced(), "this should not be called for unbalanced region");
  // New implementation (EliminateNestedLocks) has separate BoxLock
  // node for each locked region so mark all associated locks/unlocks as
  // eliminated even if different objects are referenced in one locked region
  // (for example, OSR compilation of nested loop inside locked scope).
  if (EliminateNestedLocks ||
      oldbox->as_BoxLock()->is_simple_lock_region(nullptr, obj, nullptr)) {
    // Box is used only in one lock region. Mark this box as eliminated.
    oldbox->set_local();      // This verifies correct state of BoxLock
    _igvn.hash_delete(oldbox);
    oldbox->set_eliminated(); // This changes box's hash value
     _igvn.hash_insert(oldbox);

    for (uint i = 0; i < oldbox->outcnt(); i++) {
      Node* u = oldbox->raw_out(i);
      if (u->is_AbstractLock() && !u->as_AbstractLock()->is_non_esc_obj()) {
        AbstractLockNode* alock = u->as_AbstractLock();
        // Check lock's box since box could be referenced by Lock's debug info.
        if (alock->box_node() == oldbox) {
          // Mark eliminated all related locks and unlocks.
#ifdef ASSERT
          alock->log_lock_optimization(C, "eliminate_lock_set_non_esc4");
#endif
          alock->set_non_esc_obj();
        }
      }
    }
    return;
  }

  // Create new "eliminated" BoxLock node and use it in monitor debug info
  // instead of oldbox for the same object.
  BoxLockNode* newbox = oldbox->clone()->as_BoxLock();

  // Note: BoxLock node is marked eliminated only here and it is used
  // to indicate that all associated lock and unlock nodes are marked
  // for elimination.
  newbox->set_local(); // This verifies correct state of BoxLock
  newbox->set_eliminated();
  transform_later(newbox);

  // Replace old box node with new box for all users of the same object.
  for (uint i = 0; i < oldbox->outcnt();) {
    bool next_edge = true;

    Node* u = oldbox->raw_out(i);
    if (u->is_AbstractLock()) {
      AbstractLockNode* alock = u->as_AbstractLock();
      if (alock->box_node() == oldbox && alock->obj_node()->eqv_uncast(obj)) {
        // Replace Box and mark eliminated all related locks and unlocks.
#ifdef ASSERT
        alock->log_lock_optimization(C, "eliminate_lock_set_non_esc5");
#endif
        alock->set_non_esc_obj();
        _igvn.rehash_node_delayed(alock);
        alock->set_box_node(newbox);
        next_edge = false;
      }
    }
    if (u->is_FastLock() && u->as_FastLock()->obj_node()->eqv_uncast(obj)) {
      FastLockNode* flock = u->as_FastLock();
      assert(flock->box_node() == oldbox, "sanity");
      _igvn.rehash_node_delayed(flock);
      flock->set_box_node(newbox);
      next_edge = false;
    }

    // Replace old box in monitor debug info.
    if (u->is_SafePoint() && u->as_SafePoint()->jvms()) {
      SafePointNode* sfn = u->as_SafePoint();
      JVMState* youngest_jvms = sfn->jvms();
      int max_depth = youngest_jvms->depth();
      for (int depth = 1; depth <= max_depth; depth++) {
        JVMState* jvms = youngest_jvms->of_depth(depth);
        int num_mon  = jvms->nof_monitors();
        // Loop over monitors
        for (int idx = 0; idx < num_mon; idx++) {
          Node* obj_node = sfn->monitor_obj(jvms, idx);
          Node* box_node = sfn->monitor_box(jvms, idx);
          if (box_node == oldbox && obj_node->eqv_uncast(obj)) {
            int j = jvms->monitor_box_offset(idx);
            _igvn.replace_input_of(u, j, newbox);
            next_edge = false;
          }
        }
      }
    }
    if (next_edge) i++;
  }
}

//-----------------------mark_eliminated_locking_nodes-----------------------
void PhaseMacroExpand::mark_eliminated_locking_nodes(AbstractLockNode *alock) {
  if (!alock->is_balanced()) {
    return; // Can't do any more elimination for this locking region
  }
  if (EliminateNestedLocks) {
    if (alock->is_nested()) {
       assert(alock->box_node()->as_BoxLock()->is_eliminated(), "sanity");
       return;
    } else if (!alock->is_non_esc_obj()) { // Not eliminated or coarsened
      // Only Lock node has JVMState needed here.
      // Not that preceding claim is documented anywhere else.
      if (alock->jvms() != nullptr) {
        if (alock->as_Lock()->is_nested_lock_region()) {
          // Mark eliminated related nested locks and unlocks.
          Node* obj = alock->obj_node();
          BoxLockNode* box_node = alock->box_node()->as_BoxLock();
          assert(!box_node->is_eliminated(), "should not be marked yet");
          // Note: BoxLock node is marked eliminated only here
          // and it is used to indicate that all associated lock
          // and unlock nodes are marked for elimination.
          box_node->set_eliminated(); // Box's hash is always NO_HASH here
          for (uint i = 0; i < box_node->outcnt(); i++) {
            Node* u = box_node->raw_out(i);
            if (u->is_AbstractLock()) {
              alock = u->as_AbstractLock();
              if (alock->box_node() == box_node) {
                // Verify that this Box is referenced only by related locks.
                assert(alock->obj_node()->eqv_uncast(obj), "");
                // Mark all related locks and unlocks.
#ifdef ASSERT
                alock->log_lock_optimization(C, "eliminate_lock_set_nested");
#endif
                alock->set_nested();
              }
            }
          }
        } else {
#ifdef ASSERT
          alock->log_lock_optimization(C, "eliminate_lock_NOT_nested_lock_region");
          if (C->log() != nullptr)
            alock->as_Lock()->is_nested_lock_region(C); // rerun for debugging output
#endif
        }
      }
      return;
    }
    // Process locks for non escaping object
    assert(alock->is_non_esc_obj(), "");
  } // EliminateNestedLocks

  if (alock->is_non_esc_obj()) { // Lock is used for non escaping object
    // Look for all locks of this object and mark them and
    // corresponding BoxLock nodes as eliminated.
    Node* obj = alock->obj_node();
    for (uint j = 0; j < obj->outcnt(); j++) {
      Node* o = obj->raw_out(j);
      if (o->is_AbstractLock() &&
          o->as_AbstractLock()->obj_node()->eqv_uncast(obj)) {
        alock = o->as_AbstractLock();
        Node* box = alock->box_node();
        // Replace old box node with new eliminated box for all users
        // of the same object and mark related locks as eliminated.
        mark_eliminated_box(box, obj);
      }
    }
  }
}

// we have determined that this lock/unlock can be eliminated, we simply
// eliminate the node without expanding it.
//
// Note:  The membar's associated with the lock/unlock are currently not
//        eliminated.  This should be investigated as a future enhancement.
//
bool PhaseMacroExpand::eliminate_locking_node(AbstractLockNode *alock) {

  if (!alock->is_eliminated()) {
    return false;
  }
#ifdef ASSERT
  if (!alock->is_coarsened()) {
    // Check that new "eliminated" BoxLock node is created.
    BoxLockNode* oldbox = alock->box_node()->as_BoxLock();
    assert(oldbox->is_eliminated(), "should be done already");
  }
#endif

  alock->log_lock_optimization(C, "eliminate_lock");

#ifndef PRODUCT
  if (PrintEliminateLocks) {
    tty->print_cr("++++ Eliminated: %d %s '%s'", alock->_idx, (alock->is_Lock() ? "Lock" : "Unlock"), alock->kind_as_string());
  }
#endif

  Node* mem  = alock->in(TypeFunc::Memory);
  Node* ctrl = alock->in(TypeFunc::Control);
  guarantee(ctrl != nullptr, "missing control projection, cannot replace_node() with null");

  _callprojs = alock->extract_projections(false /*separate_io_proj*/, false /*do_asserts*/);
  // There are 2 projections from the lock.  The lock node will
  // be deleted when its last use is subsumed below.
  assert(alock->outcnt() == 2 &&
         _callprojs->fallthrough_proj != nullptr &&
         _callprojs->fallthrough_memproj != nullptr,
         "Unexpected projections from Lock/Unlock");

  Node* fallthroughproj = _callprojs->fallthrough_proj;
  Node* memproj_fallthrough = _callprojs->fallthrough_memproj;

  // The memory projection from a lock/unlock is RawMem
  // The input to a Lock is merged memory, so extract its RawMem input
  // (unless the MergeMem has been optimized away.)
  if (alock->is_Lock()) {
    // Search for MemBarAcquireLock node and delete it also.
    MemBarNode* membar = fallthroughproj->unique_ctrl_out()->as_MemBar();
    assert(membar != nullptr && membar->Opcode() == Op_MemBarAcquireLock, "");
    Node* ctrlproj = membar->proj_out(TypeFunc::Control);
    Node* memproj = membar->proj_out(TypeFunc::Memory);
    _igvn.replace_node(ctrlproj, fallthroughproj);
    _igvn.replace_node(memproj, memproj_fallthrough);

    // Delete FastLock node also if this Lock node is unique user
    // (a loop peeling may clone a Lock node).
    Node* flock = alock->as_Lock()->fastlock_node();
    if (flock->outcnt() == 1) {
      assert(flock->unique_out() == alock, "sanity");
      _igvn.replace_node(flock, top());
    }
  }

  // Search for MemBarReleaseLock node and delete it also.
  if (alock->is_Unlock() && ctrl->is_Proj() && ctrl->in(0)->is_MemBar()) {
    MemBarNode* membar = ctrl->in(0)->as_MemBar();
    assert(membar->Opcode() == Op_MemBarReleaseLock &&
           mem->is_Proj() && membar == mem->in(0), "");
    _igvn.replace_node(fallthroughproj, ctrl);
    _igvn.replace_node(memproj_fallthrough, mem);
    fallthroughproj = ctrl;
    memproj_fallthrough = mem;
    ctrl = membar->in(TypeFunc::Control);
    mem  = membar->in(TypeFunc::Memory);
  }

  _igvn.replace_node(fallthroughproj, ctrl);
  _igvn.replace_node(memproj_fallthrough, mem);
  return true;
}


//------------------------------expand_lock_node----------------------
void PhaseMacroExpand::expand_lock_node(LockNode *lock) {

  Node* ctrl = lock->in(TypeFunc::Control);
  Node* mem = lock->in(TypeFunc::Memory);
  Node* obj = lock->obj_node();
  Node* box = lock->box_node();
  Node* flock = lock->fastlock_node();

  assert(!box->as_BoxLock()->is_eliminated(), "sanity");

  // Make the merge point
  Node *region;
  Node *mem_phi;
  Node *slow_path;

  region  = new RegionNode(3);
  // create a Phi for the memory state
  mem_phi = new PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);

  // Optimize test; set region slot 2
  slow_path = opt_bits_test(ctrl, region, 2, flock);
  mem_phi->init_req(2, mem);

  // Make slow path call
  CallNode* call = make_slow_call(lock, OptoRuntime::complete_monitor_enter_Type(),
                                  OptoRuntime::complete_monitor_locking_Java(), nullptr, slow_path,
                                  obj, box, nullptr);

  _callprojs = call->extract_projections(false /*separate_io_proj*/, false /*do_asserts*/);

  // Slow path can only throw asynchronous exceptions, which are always
  // de-opted.  So the compiler thinks the slow-call can never throw an
  // exception.  If it DOES throw an exception we would need the debug
  // info removed first (since if it throws there is no monitor).
  assert(_callprojs->fallthrough_ioproj == nullptr && _callprojs->catchall_ioproj == nullptr &&
         _callprojs->catchall_memproj == nullptr && _callprojs->catchall_catchproj == nullptr, "Unexpected projection from Lock");

  // Capture slow path
  // disconnect fall-through projection from call and create a new one
  // hook up users of fall-through projection to region
  Node *slow_ctrl = _callprojs->fallthrough_proj->clone();
  transform_later(slow_ctrl);
  _igvn.hash_delete(_callprojs->fallthrough_proj);
  _callprojs->fallthrough_proj->disconnect_inputs(C);
  region->init_req(1, slow_ctrl);
  // region inputs are now complete
  transform_later(region);
  _igvn.replace_node(_callprojs->fallthrough_proj, region);

  Node *memproj = transform_later(new ProjNode(call, TypeFunc::Memory));

  mem_phi->init_req(1, memproj);

  transform_later(mem_phi);

  _igvn.replace_node(_callprojs->fallthrough_memproj, mem_phi);
}

//------------------------------expand_unlock_node----------------------
void PhaseMacroExpand::expand_unlock_node(UnlockNode *unlock) {

  Node* ctrl = unlock->in(TypeFunc::Control);
  Node* mem = unlock->in(TypeFunc::Memory);
  Node* obj = unlock->obj_node();
  Node* box = unlock->box_node();

  assert(!box->as_BoxLock()->is_eliminated(), "sanity");

  // No need for a null check on unlock

  // Make the merge point
  Node* region = new RegionNode(3);

  FastUnlockNode *funlock = new FastUnlockNode( ctrl, obj, box );
  funlock = transform_later( funlock )->as_FastUnlock();
  // Optimize test; set region slot 2
  Node *slow_path = opt_bits_test(ctrl, region, 2, funlock);
  Node *thread = transform_later(new ThreadLocalNode());

  CallNode *call = make_slow_call((CallNode *) unlock, OptoRuntime::complete_monitor_exit_Type(),
                                  CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_unlocking_C),
                                  "complete_monitor_unlocking_C", slow_path, obj, box, thread);

  _callprojs = call->extract_projections(false /*separate_io_proj*/, false /*do_asserts*/);
  assert(_callprojs->fallthrough_ioproj == nullptr && _callprojs->catchall_ioproj == nullptr &&
         _callprojs->catchall_memproj == nullptr && _callprojs->catchall_catchproj == nullptr, "Unexpected projection from Lock");

  // No exceptions for unlocking
  // Capture slow path
  // disconnect fall-through projection from call and create a new one
  // hook up users of fall-through projection to region
  Node *slow_ctrl = _callprojs->fallthrough_proj->clone();
  transform_later(slow_ctrl);
  _igvn.hash_delete(_callprojs->fallthrough_proj);
  _callprojs->fallthrough_proj->disconnect_inputs(C);
  region->init_req(1, slow_ctrl);
  // region inputs are now complete
  transform_later(region);
  _igvn.replace_node(_callprojs->fallthrough_proj, region);

  if (_callprojs->fallthrough_memproj != nullptr) {
    // create a Phi for the memory state
    Node* mem_phi = new PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);
    Node* memproj = transform_later(new ProjNode(call, TypeFunc::Memory));
    mem_phi->init_req(1, memproj);
    mem_phi->init_req(2, mem);
    transform_later(mem_phi);
    _igvn.replace_node(_callprojs->fallthrough_memproj, mem_phi);
  }
}

// An inline type might be returned from the call but we don't know its
// type. Either we get a buffered inline type (and nothing needs to be done)
// or one of the values being returned is the klass of the inline type
// and we need to allocate an inline type instance of that type and
// initialize it with other values being returned. In that case, we
// first try a fast path allocation and initialize the value with the
// inline klass's pack handler or we fall back to a runtime call.
void PhaseMacroExpand::expand_mh_intrinsic_return(CallStaticJavaNode* call) {
  assert(call->method()->is_method_handle_intrinsic(), "must be a method handle intrinsic call");
  Node* ret = call->proj_out_or_null(TypeFunc::Parms);
  if (ret == nullptr) {
    return;
  }
  const TypeFunc* tf = call->_tf;
  const TypeTuple* domain = OptoRuntime::store_inline_type_fields_Type()->domain_cc();
  const TypeFunc* new_tf = TypeFunc::make(tf->domain_sig(), tf->domain_cc(), tf->range_sig(), domain);
  call->_tf = new_tf;
  // Make sure the change of type is applied before projections are processed by igvn
  _igvn.set_type(call, call->Value(&_igvn));
  _igvn.set_type(ret, ret->Value(&_igvn));

  // Before any new projection is added:
  CallProjections* projs = call->extract_projections(true, true);

  // Create temporary hook nodes that will be replaced below.
  // Add an input to prevent hook nodes from being dead.
  Node* ctl = new Node(call);
  Node* mem = new Node(ctl);
  Node* io = new Node(ctl);
  Node* ex_ctl = new Node(ctl);
  Node* ex_mem = new Node(ctl);
  Node* ex_io = new Node(ctl);
  Node* res = new Node(ctl);

  // Allocate a new buffered inline type only if a new one is not returned
  Node* cast = transform_later(new CastP2XNode(ctl, res));
  Node* mask = MakeConX(0x1);
  Node* masked = transform_later(new AndXNode(cast, mask));
  Node* cmp = transform_later(new CmpXNode(masked, mask));
  Node* bol = transform_later(new BoolNode(cmp, BoolTest::eq));
  IfNode* allocation_iff = new IfNode(ctl, bol, PROB_MAX, COUNT_UNKNOWN);
  transform_later(allocation_iff);
  Node* allocation_ctl = transform_later(new IfTrueNode(allocation_iff));
  Node* no_allocation_ctl = transform_later(new IfFalseNode(allocation_iff));
  Node* no_allocation_res = transform_later(new CheckCastPPNode(no_allocation_ctl, res, TypeInstPtr::BOTTOM));

  // Try to allocate a new buffered inline instance either from TLAB or eden space
  Node* needgc_ctrl = nullptr; // needgc means slowcase, i.e. allocation failed
  CallLeafNoFPNode* handler_call;
  const bool alloc_in_place = UseTLAB;
  if (alloc_in_place) {
    Node* fast_oop_ctrl = nullptr;
    Node* fast_oop_rawmem = nullptr;
    Node* mask2 = MakeConX(-2);
    Node* masked2 = transform_later(new AndXNode(cast, mask2));
    Node* rawklassptr = transform_later(new CastX2PNode(masked2));
    Node* klass_node = transform_later(new CheckCastPPNode(allocation_ctl, rawklassptr, TypeInstKlassPtr::OBJECT_OR_NULL));
    Node* layout_val = make_load_raw(nullptr, mem, klass_node, in_bytes(Klass::layout_helper_offset()), TypeInt::INT, T_INT);
    Node* size_in_bytes = ConvI2X(layout_val);
    BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
    Node* fast_oop = bs->obj_allocate(this, mem, allocation_ctl, size_in_bytes, io, needgc_ctrl,
                                      fast_oop_ctrl, fast_oop_rawmem,
                                      AllocateInstancePrefetchLines);
    // Allocation succeed, initialize buffered inline instance header firstly,
    // and then initialize its fields with an inline class specific handler
    Node* mark_word_node;
    if (UseCompactObjectHeaders) {
      // COH: We need to load the prototype from the klass at runtime since it encodes the klass pointer already.
      mark_word_node = make_load_raw(fast_oop_ctrl, fast_oop_rawmem, klass_node, in_bytes(Klass::prototype_header_offset()), TypeRawPtr::BOTTOM, T_ADDRESS);
    } else {
      // Otherwise, use the static prototype.
      mark_word_node = makecon(TypeRawPtr::make((address)markWord::inline_type_prototype().value()));
    }

    fast_oop_rawmem = make_store_raw(fast_oop_ctrl, fast_oop_rawmem, fast_oop, oopDesc::mark_offset_in_bytes(), mark_word_node, T_ADDRESS);
    if (!UseCompactObjectHeaders) {
      // COH: Everything is encoded in the mark word, so nothing left to do.
      fast_oop_rawmem = make_store_raw(fast_oop_ctrl, fast_oop_rawmem, fast_oop, oopDesc::klass_offset_in_bytes(), klass_node, T_METADATA);
      if (UseCompressedClassPointers) {
        fast_oop_rawmem = make_store_raw(fast_oop_ctrl, fast_oop_rawmem, fast_oop, oopDesc::klass_gap_offset_in_bytes(), intcon(0), T_INT);
      }
    }
    Node* members  = make_load_raw(fast_oop_ctrl, fast_oop_rawmem, klass_node, in_bytes(InlineKlass::adr_members_offset()), TypeRawPtr::BOTTOM, T_ADDRESS);
    Node* pack_handler = make_load_raw(fast_oop_ctrl, fast_oop_rawmem, members, in_bytes(InlineKlass::pack_handler_offset()), TypeRawPtr::BOTTOM, T_ADDRESS);
    handler_call = new CallLeafNoFPNode(OptoRuntime::pack_inline_type_Type(),
                                        nullptr,
                                        "pack handler",
                                        TypeRawPtr::BOTTOM);
    handler_call->init_req(TypeFunc::Control, fast_oop_ctrl);
    handler_call->init_req(TypeFunc::Memory, fast_oop_rawmem);
    handler_call->init_req(TypeFunc::I_O, top());
    handler_call->init_req(TypeFunc::FramePtr, call->in(TypeFunc::FramePtr));
    handler_call->init_req(TypeFunc::ReturnAdr, top());
    handler_call->init_req(TypeFunc::Parms, pack_handler);
    handler_call->init_req(TypeFunc::Parms+1, fast_oop);
  } else {
    needgc_ctrl = allocation_ctl;
  }

  // Allocation failed, fall back to a runtime call
  CallStaticJavaNode* slow_call = new CallStaticJavaNode(OptoRuntime::store_inline_type_fields_Type(),
                                                         StubRoutines::store_inline_type_fields_to_buf(),
                                                         "store_inline_type_fields",
                                                         TypePtr::BOTTOM);
  slow_call->init_req(TypeFunc::Control, needgc_ctrl);
  slow_call->init_req(TypeFunc::Memory, mem);
  slow_call->init_req(TypeFunc::I_O, io);
  slow_call->init_req(TypeFunc::FramePtr, call->in(TypeFunc::FramePtr));
  slow_call->init_req(TypeFunc::ReturnAdr, call->in(TypeFunc::ReturnAdr));
  slow_call->init_req(TypeFunc::Parms, res);

  Node* slow_ctl = transform_later(new ProjNode(slow_call, TypeFunc::Control));
  Node* slow_mem = transform_later(new ProjNode(slow_call, TypeFunc::Memory));
  Node* slow_io = transform_later(new ProjNode(slow_call, TypeFunc::I_O));
  Node* slow_res = transform_later(new ProjNode(slow_call, TypeFunc::Parms));
  Node* slow_catc = transform_later(new CatchNode(slow_ctl, slow_io, 2));
  Node* slow_norm = transform_later(new CatchProjNode(slow_catc, CatchProjNode::fall_through_index, CatchProjNode::no_handler_bci));
  Node* slow_excp = transform_later(new CatchProjNode(slow_catc, CatchProjNode::catch_all_index,    CatchProjNode::no_handler_bci));

  Node* ex_r = new RegionNode(3);
  Node* ex_mem_phi = new PhiNode(ex_r, Type::MEMORY, TypePtr::BOTTOM);
  Node* ex_io_phi = new PhiNode(ex_r, Type::ABIO);
  ex_r->init_req(1, slow_excp);
  ex_mem_phi->init_req(1, slow_mem);
  ex_io_phi->init_req(1, slow_io);
  ex_r->init_req(2, ex_ctl);
  ex_mem_phi->init_req(2, ex_mem);
  ex_io_phi->init_req(2, ex_io);
  transform_later(ex_r);
  transform_later(ex_mem_phi);
  transform_later(ex_io_phi);

  // We don't know how many values are returned. This assumes the
  // worst case, that all available registers are used.
  for (uint i = TypeFunc::Parms+1; i < domain->cnt(); i++) {
    if (domain->field_at(i) == Type::HALF) {
      slow_call->init_req(i, top());
      if (alloc_in_place) {
        handler_call->init_req(i+1, top());
      }
      continue;
    }
    Node* proj = transform_later(new ProjNode(call, i));
    slow_call->init_req(i, proj);
    if (alloc_in_place) {
      handler_call->init_req(i+1, proj);
    }
  }
  // We can safepoint at that new call
  slow_call->copy_call_debug_info(&_igvn, call);
  transform_later(slow_call);
  if (alloc_in_place) {
    transform_later(handler_call);
  }

  Node* fast_ctl = nullptr;
  Node* fast_res = nullptr;
  MergeMemNode* fast_mem = nullptr;
  if (alloc_in_place) {
    fast_ctl = transform_later(new ProjNode(handler_call, TypeFunc::Control));
    Node* rawmem = transform_later(new ProjNode(handler_call, TypeFunc::Memory));
    fast_res = transform_later(new ProjNode(handler_call, TypeFunc::Parms));
    fast_mem = MergeMemNode::make(mem);
    fast_mem->set_memory_at(Compile::AliasIdxRaw, rawmem);
    transform_later(fast_mem);
  }

  Node* r = new RegionNode(alloc_in_place ? 4 : 3);
  Node* mem_phi = new PhiNode(r, Type::MEMORY, TypePtr::BOTTOM);
  Node* io_phi = new PhiNode(r, Type::ABIO);
  Node* res_phi = new PhiNode(r, TypeInstPtr::BOTTOM);
  r->init_req(1, no_allocation_ctl);
  mem_phi->init_req(1, mem);
  io_phi->init_req(1, io);
  res_phi->init_req(1, no_allocation_res);
  r->init_req(2, slow_norm);
  mem_phi->init_req(2, slow_mem);
  io_phi->init_req(2, slow_io);
  res_phi->init_req(2, slow_res);
  if (alloc_in_place) {
    r->init_req(3, fast_ctl);
    mem_phi->init_req(3, fast_mem);
    io_phi->init_req(3, io);
    res_phi->init_req(3, fast_res);
  }
  transform_later(r);
  transform_later(mem_phi);
  transform_later(io_phi);
  transform_later(res_phi);

  // Do not let stores that initialize this buffer be reordered with a subsequent
  // store that would make this buffer accessible by other threads.
  MemBarNode* mb = MemBarNode::make(C, Op_MemBarStoreStore, Compile::AliasIdxBot);
  transform_later(mb);
  mb->init_req(TypeFunc::Memory, mem_phi);
  mb->init_req(TypeFunc::Control, r);
  r = new ProjNode(mb, TypeFunc::Control);
  transform_later(r);
  mem_phi = new ProjNode(mb, TypeFunc::Memory);
  transform_later(mem_phi);

  assert(projs->nb_resproj == 1, "unexpected number of results");
  _igvn.replace_in_uses(projs->fallthrough_catchproj, r);
  _igvn.replace_in_uses(projs->fallthrough_memproj, mem_phi);
  _igvn.replace_in_uses(projs->fallthrough_ioproj, io_phi);
  _igvn.replace_in_uses(projs->resproj[0], res_phi);
  _igvn.replace_in_uses(projs->catchall_catchproj, ex_r);
  _igvn.replace_in_uses(projs->catchall_memproj, ex_mem_phi);
  _igvn.replace_in_uses(projs->catchall_ioproj, ex_io_phi);
  // The CatchNode should not use the ex_io_phi. Re-connect it to the catchall_ioproj.
  Node* cn = projs->fallthrough_catchproj->in(0);
  _igvn.replace_input_of(cn, 1, projs->catchall_ioproj);

  _igvn.replace_node(ctl, projs->fallthrough_catchproj);
  _igvn.replace_node(mem, projs->fallthrough_memproj);
  _igvn.replace_node(io, projs->fallthrough_ioproj);
  _igvn.replace_node(res, projs->resproj[0]);
  _igvn.replace_node(ex_ctl, projs->catchall_catchproj);
  _igvn.replace_node(ex_mem, projs->catchall_memproj);
  _igvn.replace_node(ex_io, projs->catchall_ioproj);
 }

void PhaseMacroExpand::expand_subtypecheck_node(SubTypeCheckNode *check) {
  assert(check->in(SubTypeCheckNode::Control) == nullptr, "should be pinned");
  Node* bol = check->unique_out();
  Node* obj_or_subklass = check->in(SubTypeCheckNode::ObjOrSubKlass);
  Node* superklass = check->in(SubTypeCheckNode::SuperKlass);
  assert(bol->is_Bool() && bol->as_Bool()->_test._test == BoolTest::ne, "unexpected bool node");

  for (DUIterator_Last imin, i = bol->last_outs(imin); i >= imin; --i) {
    Node* iff = bol->last_out(i);
    assert(iff->is_If(), "where's the if?");

    if (iff->in(0)->is_top()) {
      _igvn.replace_input_of(iff, 1, C->top());
      continue;
    }

    IfTrueNode* iftrue = iff->as_If()->true_proj();
    IfFalseNode* iffalse = iff->as_If()->false_proj();
    Node* ctrl = iff->in(0);

    Node* subklass = nullptr;
    if (_igvn.type(obj_or_subklass)->isa_klassptr()) {
      subklass = obj_or_subklass;
    } else {
      Node* k_adr = basic_plus_adr(obj_or_subklass, oopDesc::klass_offset_in_bytes());
      subklass = _igvn.transform(LoadKlassNode::make(_igvn, C->immutable_memory(), k_adr, TypeInstPtr::KLASS, TypeInstKlassPtr::OBJECT));
    }

    Node* not_subtype_ctrl = Phase::gen_subtype_check(subklass, superklass, &ctrl, nullptr, _igvn, check->method(), check->bci());

    _igvn.replace_input_of(iff, 0, C->top());
    _igvn.replace_node(iftrue, not_subtype_ctrl);
    _igvn.replace_node(iffalse, ctrl);
  }
  _igvn.replace_node(check, C->top());
}

// FlatArrayCheckNode (array1 array2 ...) is expanded into:
//
// long mark = array1.mark | array2.mark | ...;
// long locked_bit = markWord::unlocked_value & array1.mark & array2.mark & ...;
// if (locked_bit == 0) {
//   // One array is locked, load prototype header from the klass
//   mark = array1.klass.proto | array2.klass.proto | ...
// }
// if ((mark & markWord::flat_array_bit_in_place) == 0) {
//    ...
// }
void PhaseMacroExpand::expand_flatarraycheck_node(FlatArrayCheckNode* check) {
  bool array_inputs = _igvn.type(check->in(FlatArrayCheckNode::ArrayOrKlass))->isa_oopptr() != nullptr;
  if (array_inputs) {
    Node* mark = MakeConX(0);
    Node* locked_bit = MakeConX(markWord::unlocked_value);
    Node* mem = check->in(FlatArrayCheckNode::Memory);
    for (uint i = FlatArrayCheckNode::ArrayOrKlass; i < check->req(); ++i) {
      Node* ary = check->in(i);
      const TypeOopPtr* t = _igvn.type(ary)->isa_oopptr();
      assert(t != nullptr, "Mixing array and klass inputs");
      assert(!t->is_flat() && !t->is_not_flat(), "Should have been optimized out");
      Node* mark_adr = basic_plus_adr(ary, oopDesc::mark_offset_in_bytes());
      Node* mark_load = _igvn.transform(LoadNode::make(_igvn, nullptr, mem, mark_adr, mark_adr->bottom_type()->is_ptr(), TypeX_X, TypeX_X->basic_type(), MemNode::unordered));
      mark = _igvn.transform(new OrXNode(mark, mark_load));
      locked_bit = _igvn.transform(new AndXNode(locked_bit, mark_load));
    }
    assert(!mark->is_Con(), "Should have been optimized out");
    Node* cmp = _igvn.transform(new CmpXNode(locked_bit, MakeConX(0)));
    Node* is_unlocked = _igvn.transform(new BoolNode(cmp, BoolTest::ne));

    // BoolNode might be shared, replace each if user
    Node* old_bol = check->unique_out();
    assert(old_bol->is_Bool() && old_bol->as_Bool()->_test._test == BoolTest::ne, "unexpected condition");
    for (DUIterator_Last imin, i = old_bol->last_outs(imin); i >= imin; --i) {
      IfNode* old_iff = old_bol->last_out(i)->as_If();
      Node* ctrl = old_iff->in(0);
      RegionNode* region = new RegionNode(3);
      Node* mark_phi = new PhiNode(region, TypeX_X);

      // Check if array is unlocked
      IfNode* iff = _igvn.transform(new IfNode(ctrl, is_unlocked, PROB_MAX, COUNT_UNKNOWN))->as_If();

      // Unlocked: Use bits from mark word
      region->init_req(1, _igvn.transform(new IfTrueNode(iff)));
      mark_phi->init_req(1, mark);

      // Locked: Load prototype header from klass
      ctrl = _igvn.transform(new IfFalseNode(iff));
      Node* proto = MakeConX(0);
      for (uint i = FlatArrayCheckNode::ArrayOrKlass; i < check->req(); ++i) {
        Node* ary = check->in(i);
        // Make loads control dependent to make sure they are only executed if array is locked
        Node* klass_adr = basic_plus_adr(ary, oopDesc::klass_offset_in_bytes());
        Node* klass = _igvn.transform(LoadKlassNode::make(_igvn, C->immutable_memory(), klass_adr, TypeInstPtr::KLASS, TypeInstKlassPtr::OBJECT));
        Node* proto_adr = basic_plus_adr(top(), klass, in_bytes(Klass::prototype_header_offset()));
        Node* proto_load = _igvn.transform(LoadNode::make(_igvn, ctrl, C->immutable_memory(), proto_adr, proto_adr->bottom_type()->is_ptr(), TypeX_X, TypeX_X->basic_type(), MemNode::unordered));
        proto = _igvn.transform(new OrXNode(proto, proto_load));
      }
      region->init_req(2, ctrl);
      mark_phi->init_req(2, proto);

      // Check if flat array bits are set
      Node* mask = MakeConX(markWord::flat_array_bit_in_place);
      Node* masked = _igvn.transform(new AndXNode(_igvn.transform(mark_phi), mask));
      cmp = _igvn.transform(new CmpXNode(masked, MakeConX(0)));
      Node* is_not_flat = _igvn.transform(new BoolNode(cmp, BoolTest::eq));

      ctrl = _igvn.transform(region);
      iff = _igvn.transform(new IfNode(ctrl, is_not_flat, PROB_MAX, COUNT_UNKNOWN))->as_If();
      _igvn.replace_node(old_iff, iff);
    }
    _igvn.replace_node(check, C->top());
  } else {
    // Fall back to layout helper check
    Node* lhs = intcon(0);
    for (uint i = FlatArrayCheckNode::ArrayOrKlass; i < check->req(); ++i) {
      Node* array_or_klass = check->in(i);
      Node* klass = nullptr;
      const TypePtr* t = _igvn.type(array_or_klass)->is_ptr();
      assert(!t->is_flat() && !t->is_not_flat(), "Should have been optimized out");
      if (t->isa_oopptr() != nullptr) {
        Node* klass_adr = basic_plus_adr(array_or_klass, oopDesc::klass_offset_in_bytes());
        klass = transform_later(LoadKlassNode::make(_igvn, C->immutable_memory(), klass_adr, TypeInstPtr::KLASS, TypeInstKlassPtr::OBJECT));
      } else {
        assert(t->isa_klassptr(), "Unexpected input type");
        klass = array_or_klass;
      }
      Node* lh_addr = basic_plus_adr(top(), klass, in_bytes(Klass::layout_helper_offset()));
      Node* lh_val = _igvn.transform(LoadNode::make(_igvn, nullptr, C->immutable_memory(), lh_addr, lh_addr->bottom_type()->is_ptr(), TypeInt::INT, T_INT, MemNode::unordered));
      lhs = _igvn.transform(new OrINode(lhs, lh_val));
    }
    Node* masked = transform_later(new AndINode(lhs, intcon(Klass::_lh_array_tag_flat_value_bit_inplace)));
    Node* cmp = transform_later(new CmpINode(masked, intcon(0)));
    Node* bol = transform_later(new BoolNode(cmp, BoolTest::eq));
    Node* m2b = transform_later(new Conv2BNode(masked));
    // The matcher expects the input to If/CMove nodes to be produced by a Bool(CmpI..)
    // pattern, but the input to other potential users (e.g. Phi) to be some
    // other pattern (e.g. a Conv2B node, possibly idealized as a CMoveI).
    Node* old_bol = check->unique_out();
    for (DUIterator_Last imin, i = old_bol->last_outs(imin); i >= imin; --i) {
      Node* user = old_bol->last_out(i);
      for (uint j = 0; j < user->req(); j++) {
        Node* n = user->in(j);
        if (n == old_bol) {
          _igvn.replace_input_of(user, j, (user->is_If() || user->is_CMove()) ? bol : m2b);
        }
      }
    }
    _igvn.replace_node(check, C->top());
  }
}

// Perform refining of strip mined loop nodes in the macro nodes list.
void PhaseMacroExpand::refine_strip_mined_loop_macro_nodes() {
   for (int i = C->macro_count(); i > 0; i--) {
    Node* n = C->macro_node(i - 1);
    if (n->is_OuterStripMinedLoop()) {
      n->as_OuterStripMinedLoop()->adjust_strip_mined_loop(&_igvn);
    }
  }
}

//---------------------------eliminate_macro_nodes----------------------
// Eliminate scalar replaced allocations and associated locks.
void PhaseMacroExpand::eliminate_macro_nodes(bool eliminate_locks) {
  if (C->macro_count() == 0) {
    return;
  }

  if (StressMacroElimination) {
    C->shuffle_macro_nodes();
  }
  NOT_PRODUCT(int membar_before = count_MemBar(C);)

  int iteration = 0;
  while (C->macro_count() > 0) {
    if (iteration++ > 100) {
      assert(false, "Too slow convergence of macro elimination");
      break;
    }

    // Postpone lock elimination to after EA when most allocations are eliminated
    // because they might block lock elimination if their escape state isn't
    // determined yet and we only got one chance at eliminating the lock.
    if (eliminate_locks) {
      // Before elimination may re-mark (change to Nested or NonEscObj)
      // all associated (same box and obj) lock and unlock nodes.
      int cnt = C->macro_count();
      for (int i=0; i < cnt; i++) {
        Node *n = C->macro_node(i);
        if (n->is_AbstractLock()) { // Lock and Unlock nodes
          mark_eliminated_locking_nodes(n->as_AbstractLock());
        }
      }
      // Re-marking may break consistency of Coarsened locks.
      if (!C->coarsened_locks_consistent()) {
        return; // recompile without Coarsened locks if broken
      } else {
        // After coarsened locks are eliminated locking regions
        // become unbalanced. We should not execute any more
        // locks elimination optimizations on them.
        C->mark_unbalanced_boxes();
      }
    }

    bool progress = false;
    for (int i = C->macro_count(); i > 0; i = MIN2(i - 1, C->macro_count())) { // more than 1 element can be eliminated at once
      Node* n = C->macro_node(i - 1);
      bool success = false;
      DEBUG_ONLY(int old_macro_count = C->macro_count();)
      switch (n->class_id()) {
      case Node::Class_Allocate:
      case Node::Class_AllocateArray:
        success = eliminate_allocate_node(n->as_Allocate());
#ifndef PRODUCT
        if (success && PrintOptoStatistics) {
          AtomicAccess::inc(&PhaseMacroExpand::_objs_scalar_replaced_counter);
        }
#endif
        break;
      case Node::Class_CallStaticJava: {
        CallStaticJavaNode* call = n->as_CallStaticJava();
        if (!call->method()->is_method_handle_intrinsic()) {
          success = eliminate_boxing_node(n->as_CallStaticJava());
        }
        break;
      }
      case Node::Class_Lock:
      case Node::Class_Unlock:
        if (eliminate_locks) {
          success = eliminate_locking_node(n->as_AbstractLock());
#ifndef PRODUCT
          if (success && PrintOptoStatistics) {
            AtomicAccess::inc(&PhaseMacroExpand::_monitor_objects_removed_counter);
          }
#endif
        }
        break;
      case Node::Class_ArrayCopy:
        break;
      case Node::Class_OuterStripMinedLoop:
        break;
      case Node::Class_SubTypeCheck:
        break;
      case Node::Class_Opaque1:
        break;
      case Node::Class_FlatArrayCheck:
        break;
      default:
        assert(n->Opcode() == Op_LoopLimit ||
               n->Opcode() == Op_ModD ||
               n->Opcode() == Op_ModF ||
               n->is_OpaqueConstantBool()    ||
               n->is_OpaqueInitializedAssertionPredicate() ||
               n->Opcode() == Op_MaxL      ||
               n->Opcode() == Op_MinL      ||
               BarrierSet::barrier_set()->barrier_set_c2()->is_gc_barrier_node(n),
               "unknown node type in macro list");
      }
      assert(success == (C->macro_count() < old_macro_count), "elimination reduces macro count");
      progress = progress || success;
      if (success) {
        C->print_method(PHASE_AFTER_MACRO_ELIMINATION_STEP, 5, n);
      }
    }

    // Ensure the graph after PhaseMacroExpand::eliminate_macro_nodes is canonical (no igvn
    // transformation is pending). If an allocation is used only in safepoints, elimination of
    // other macro nodes can remove all these safepoints, allowing the allocation to be removed.
    // Hence after igvn we retry removing macro nodes if some progress that has been made in this
    // iteration.
    _igvn.set_delay_transform(false);
    _igvn.optimize();
    if (C->failing()) {
      return;
    }
    _igvn.set_delay_transform(true);

    if (!progress) {
      break;
    }
  }
#ifndef PRODUCT
  if (PrintOptoStatistics) {
    int membar_after = count_MemBar(C);
    AtomicAccess::add(&PhaseMacroExpand::_memory_barriers_removed_counter, membar_before - membar_after);
  }
#endif
}

void PhaseMacroExpand::eliminate_opaque_looplimit_macro_nodes() {
  if (C->macro_count() == 0) {
    return;
  }
  refine_strip_mined_loop_macro_nodes();
  // Eliminate Opaque and LoopLimit nodes. Do it after all loop optimizations.
  bool progress = true;
  while (progress) {
    progress = false;
    for (int i = C->macro_count(); i > 0; i--) {
      Node* n = C->macro_node(i-1);
      bool success = false;
      DEBUG_ONLY(int old_macro_count = C->macro_count();)
      if (n->Opcode() == Op_LoopLimit) {
        // Remove it from macro list and put on IGVN worklist to optimize.
        C->remove_macro_node(n);
        _igvn._worklist.push(n);
        success = true;
      } else if (n->Opcode() == Op_CallStaticJava) {
        CallStaticJavaNode* call = n->as_CallStaticJava();
        if (!call->method()->is_method_handle_intrinsic()) {
          // Remove it from macro list and put on IGVN worklist to optimize.
          C->remove_macro_node(n);
          _igvn._worklist.push(n);
          success = true;
        }
      } else if (n->is_Opaque1()) {
        _igvn.replace_node(n, n->in(1));
        success = true;
      } else if (n->is_OpaqueConstantBool()) {
        // Tests with OpaqueConstantBool nodes are implicitly known. Replace the node with true/false. In debug builds,
        // we leave the test in the graph to have an additional sanity check at runtime. If the test fails (i.e. a bug),
        // we will execute a Halt node.
#ifdef ASSERT
        _igvn.replace_node(n, n->in(1));
#else
        _igvn.replace_node(n, _igvn.intcon(n->as_OpaqueConstantBool()->constant()));
#endif
        success = true;
      } else if (n->is_OpaqueInitializedAssertionPredicate()) {
          // Initialized Assertion Predicates must always evaluate to true. Therefore, we get rid of them in product
          // builds as they are useless. In debug builds we keep them as additional verification code. Even though
          // loop opts are already over, we want to keep Initialized Assertion Predicates alive as long as possible to
          // enable folding of dead control paths within which cast nodes become top after due to impossible types -
          // even after loop opts are over. Therefore, we delay the removal of these opaque nodes until now.
#ifdef ASSERT
        _igvn.replace_node(n, n->in(1));
#else
        _igvn.replace_node(n, _igvn.intcon(1));
#endif // ASSERT
      } else if (n->Opcode() == Op_OuterStripMinedLoop) {
        C->remove_macro_node(n);
        success = true;
      } else if (n->Opcode() == Op_MaxL) {
        // Since MaxL and MinL are not implemented in the backend, we expand them to
        // a CMoveL construct now. At least until here, the type could be computed
        // precisely. CMoveL is not so smart, but we can give it at least the best
        // type we know abouot n now.
        Node* repl = MinMaxNode::signed_max(n->in(1), n->in(2), _igvn.type(n), _igvn);
        _igvn.replace_node(n, repl);
        success = true;
      } else if (n->Opcode() == Op_MinL) {
        Node* repl = MinMaxNode::signed_min(n->in(1), n->in(2), _igvn.type(n), _igvn);
        _igvn.replace_node(n, repl);
        success = true;
      }
      assert(!success || (C->macro_count() == (old_macro_count - 1)), "elimination must have deleted one node from macro list");
      progress = progress || success;
      if (success) {
        C->print_method(PHASE_AFTER_MACRO_ELIMINATION_STEP, 5, n);
      }
    }
  }
}

//------------------------------expand_macro_nodes----------------------
//  Returns true if a failure occurred.
bool PhaseMacroExpand::expand_macro_nodes() {
  if (StressMacroExpansion) {
    C->shuffle_macro_nodes();
  }

  // Clean up the graph so we're less likely to hit the maximum node
  // limit
  _igvn.set_delay_transform(false);
  _igvn.optimize();
  if (C->failing())  return true;
  _igvn.set_delay_transform(true);


  // Because we run IGVN after each expansion, some macro nodes may go
  // dead and be removed from the list as we iterate over it. Move
  // Allocate nodes (processed in a second pass) at the beginning of
  // the list and then iterate from the last element of the list until
  // an Allocate node is seen. This is robust to random deletion in
  // the list due to nodes going dead.
  C->sort_macro_nodes();

  // expand arraycopy "macro" nodes first
  // For ReduceBulkZeroing, we must first process all arraycopy nodes
  // before the allocate nodes are expanded.
  while (C->macro_count() > 0) {
    int macro_count = C->macro_count();
    Node * n = C->macro_node(macro_count-1);
    assert(n->is_macro(), "only macro nodes expected here");
    if (_igvn.type(n) == Type::TOP || (n->in(0) != nullptr && n->in(0)->is_top())) {
      // node is unreachable, so don't try to expand it
      C->remove_macro_node(n);
      continue;
    }
    if (n->is_Allocate()) {
      break;
    }
    // Make sure expansion will not cause node limit to be exceeded.
    // Worst case is a macro node gets expanded into about 200 nodes.
    // Allow 50% more for optimization.
    if (C->check_node_count(300, "out of nodes before macro expansion")) {
      return true;
    }

    DEBUG_ONLY(int old_macro_count = C->macro_count();)
    switch (n->class_id()) {
    case Node::Class_Lock:
      expand_lock_node(n->as_Lock());
      break;
    case Node::Class_Unlock:
      expand_unlock_node(n->as_Unlock());
      break;
    case Node::Class_ArrayCopy:
      expand_arraycopy_node(n->as_ArrayCopy());
      break;
    case Node::Class_SubTypeCheck:
      expand_subtypecheck_node(n->as_SubTypeCheck());
      break;
    case Node::Class_CallStaticJava:
      expand_mh_intrinsic_return(n->as_CallStaticJava());
      C->remove_macro_node(n);
      break;
    case Node::Class_FlatArrayCheck:
      expand_flatarraycheck_node(n->as_FlatArrayCheck());
      break;
    default:
      switch (n->Opcode()) {
      case Op_ModD:
      case Op_ModF: {
        CallNode* mod_macro = n->as_Call();
        CallNode* call = new CallLeafPureNode(mod_macro->tf(), mod_macro->entry_point(), mod_macro->_name);
        call->init_req(TypeFunc::Control, mod_macro->in(TypeFunc::Control));
        call->init_req(TypeFunc::I_O, C->top());
        call->init_req(TypeFunc::Memory, C->top());
        call->init_req(TypeFunc::ReturnAdr, C->top());
        call->init_req(TypeFunc::FramePtr, C->top());
        for (unsigned int i = 0; i < mod_macro->tf()->domain_cc()->cnt() - TypeFunc::Parms; i++) {
          call->init_req(TypeFunc::Parms + i, mod_macro->in(TypeFunc::Parms + i));
        }
        _igvn.replace_node(mod_macro, call);
        transform_later(call);
        break;
      }
      default:
        assert(false, "unknown node type in macro list");
      }
    }
    assert(C->macro_count() == (old_macro_count - 1), "expansion must have deleted one node from macro list");
    if (C->failing())  return true;
    C->print_method(PHASE_AFTER_MACRO_EXPANSION_STEP, 5, n);

    // Clean up the graph so we're less likely to hit the maximum node
    // limit
    _igvn.set_delay_transform(false);
    _igvn.optimize();
    if (C->failing())  return true;
    _igvn.set_delay_transform(true);
  }

  // All nodes except Allocate nodes are expanded now. There could be
  // new optimization opportunities (such as folding newly created
  // load from a just allocated object). Run IGVN.

  // expand "macro" nodes
  // nodes are removed from the macro list as they are processed
  while (C->macro_count() > 0) {
    int macro_count = C->macro_count();
    Node * n = C->macro_node(macro_count-1);
    assert(n->is_macro(), "only macro nodes expected here");
    if (_igvn.type(n) == Type::TOP || (n->in(0) != nullptr && n->in(0)->is_top())) {
      // node is unreachable, so don't try to expand it
      C->remove_macro_node(n);
      continue;
    }
    // Make sure expansion will not cause node limit to be exceeded.
    // Worst case is a macro node gets expanded into about 200 nodes.
    // Allow 50% more for optimization.
    if (C->check_node_count(300, "out of nodes before macro expansion")) {
      return true;
    }
    switch (n->class_id()) {
    case Node::Class_Allocate:
      expand_allocate(n->as_Allocate());
      break;
    case Node::Class_AllocateArray:
      expand_allocate_array(n->as_AllocateArray());
      break;
    default:
      assert(false, "unknown node type in macro list");
    }
    assert(C->macro_count() < macro_count, "must have deleted a node from macro list");
    if (C->failing())  return true;
    C->print_method(PHASE_AFTER_MACRO_EXPANSION_STEP, 5, n);

    // Clean up the graph so we're less likely to hit the maximum node
    // limit
    _igvn.set_delay_transform(false);
    _igvn.optimize();
    if (C->failing())  return true;
    _igvn.set_delay_transform(true);
  }

  _igvn.set_delay_transform(false);
  return false;
}

#ifndef PRODUCT
int PhaseMacroExpand::_objs_scalar_replaced_counter = 0;
int PhaseMacroExpand::_monitor_objects_removed_counter = 0;
int PhaseMacroExpand::_GC_barriers_removed_counter = 0;
int PhaseMacroExpand::_memory_barriers_removed_counter = 0;

void PhaseMacroExpand::print_statistics() {
  tty->print("Objects scalar replaced = %d, ", AtomicAccess::load(&_objs_scalar_replaced_counter));
  tty->print("Monitor objects removed = %d, ", AtomicAccess::load(&_monitor_objects_removed_counter));
  tty->print("GC barriers removed = %d, ", AtomicAccess::load(&_GC_barriers_removed_counter));
  tty->print_cr("Memory barriers removed = %d", AtomicAccess::load(&_memory_barriers_removed_counter));
}

int PhaseMacroExpand::count_MemBar(Compile *C) {
  if (!PrintOptoStatistics) {
    return 0;
  }
  Unique_Node_List ideal_nodes;
  int total = 0;
  ideal_nodes.map(C->live_nodes(), nullptr);
  ideal_nodes.push(C->root());
  for (uint next = 0; next < ideal_nodes.size(); ++next) {
    Node* n = ideal_nodes.at(next);
    if (n->is_MemBar()) {
      total++;
    }
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* m = n->fast_out(i);
      ideal_nodes.push(m);
    }
  }
  return total;
}
#endif

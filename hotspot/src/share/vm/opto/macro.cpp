/*
 * Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_macro.cpp.incl"


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
    } else if (j >= req && uin == NULL) {
      break;
    }
  }
  return nreplacements;
}

void PhaseMacroExpand::copy_call_debug_info(CallNode *oldcall, CallNode * newcall) {
  // Copy debug information and adjust JVMState information
  uint old_dbg_start = oldcall->tf()->domain()->cnt();
  uint new_dbg_start = newcall->tf()->domain()->cnt();
  int jvms_adj  = new_dbg_start - old_dbg_start;
  assert (new_dbg_start == newcall->req(), "argument count mismatch");

  Dict* sosn_map = new Dict(cmpkey,hashkey);
  for (uint i = old_dbg_start; i < oldcall->req(); i++) {
    Node* old_in = oldcall->in(i);
    // Clone old SafePointScalarObjectNodes, adjusting their field contents.
    if (old_in != NULL && old_in->is_SafePointScalarObject()) {
      SafePointScalarObjectNode* old_sosn = old_in->as_SafePointScalarObject();
      uint old_unique = C->unique();
      Node* new_in = old_sosn->clone(jvms_adj, sosn_map);
      if (old_unique != C->unique()) {
        new_in->set_req(0, newcall->in(0)); // reset control edge
        new_in = transform_later(new_in); // Register new node.
      }
      old_in = new_in;
    }
    newcall->add_req(old_in);
  }

  newcall->set_jvms(oldcall->jvms());
  for (JVMState *jvms = newcall->jvms(); jvms != NULL; jvms = jvms->caller()) {
    jvms->set_map(newcall);
    jvms->set_locoff(jvms->locoff()+jvms_adj);
    jvms->set_stkoff(jvms->stkoff()+jvms_adj);
    jvms->set_monoff(jvms->monoff()+jvms_adj);
    jvms->set_scloff(jvms->scloff()+jvms_adj);
    jvms->set_endoff(jvms->endoff()+jvms_adj);
  }
}

Node* PhaseMacroExpand::opt_bits_test(Node* ctrl, Node* region, int edge, Node* word, int mask, int bits, bool return_fast_path) {
  Node* cmp;
  if (mask != 0) {
    Node* and_node = transform_later(new (C, 3) AndXNode(word, MakeConX(mask)));
    cmp = transform_later(new (C, 3) CmpXNode(and_node, MakeConX(bits)));
  } else {
    cmp = word;
  }
  Node* bol = transform_later(new (C, 2) BoolNode(cmp, BoolTest::ne));
  IfNode* iff = new (C, 2) IfNode( ctrl, bol, PROB_MIN, COUNT_UNKNOWN );
  transform_later(iff);

  // Fast path taken.
  Node *fast_taken = transform_later( new (C, 1) IfFalseNode(iff) );

  // Fast path not-taken, i.e. slow path
  Node *slow_taken = transform_later( new (C, 1) IfTrueNode(iff) );

  if (return_fast_path) {
    region->init_req(edge, slow_taken); // Capture slow-control
    return fast_taken;
  } else {
    region->init_req(edge, fast_taken); // Capture fast-control
    return slow_taken;
  }
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
CallNode* PhaseMacroExpand::make_slow_call(CallNode *oldcall, const TypeFunc* slow_call_type, address slow_call, const char* leaf_name, Node* slow_path, Node* parm0, Node* parm1) {

  // Slow-path call
  int size = slow_call_type->domain()->cnt();
 CallNode *call = leaf_name
   ? (CallNode*)new (C, size) CallLeafNode      ( slow_call_type, slow_call, leaf_name, TypeRawPtr::BOTTOM )
   : (CallNode*)new (C, size) CallStaticJavaNode( slow_call_type, slow_call, OptoRuntime::stub_name(slow_call), oldcall->jvms()->bci(), TypeRawPtr::BOTTOM );

  // Slow path call has no side-effects, uses few values
  copy_predefined_input_for_runtime_call(slow_path, oldcall, call );
  if (parm0 != NULL)  call->init_req(TypeFunc::Parms+0, parm0);
  if (parm1 != NULL)  call->init_req(TypeFunc::Parms+1, parm1);
  copy_call_debug_info(oldcall, call);
  call->set_cnt(PROB_UNLIKELY_MAG(4));  // Same effect as RC_UNCOMMON.
  _igvn.hash_delete(oldcall);
  _igvn.subsume_node(oldcall, call);
  transform_later(call);

  return call;
}

void PhaseMacroExpand::extract_call_projections(CallNode *call) {
  _fallthroughproj = NULL;
  _fallthroughcatchproj = NULL;
  _ioproj_fallthrough = NULL;
  _ioproj_catchall = NULL;
  _catchallcatchproj = NULL;
  _memproj_fallthrough = NULL;
  _memproj_catchall = NULL;
  _resproj = NULL;
  for (DUIterator_Fast imax, i = call->fast_outs(imax); i < imax; i++) {
    ProjNode *pn = call->fast_out(i)->as_Proj();
    switch (pn->_con) {
      case TypeFunc::Control:
      {
        // For Control (fallthrough) and I_O (catch_all_index) we have CatchProj -> Catch -> Proj
        _fallthroughproj = pn;
        DUIterator_Fast jmax, j = pn->fast_outs(jmax);
        const Node *cn = pn->fast_out(j);
        if (cn->is_Catch()) {
          ProjNode *cpn = NULL;
          for (DUIterator_Fast kmax, k = cn->fast_outs(kmax); k < kmax; k++) {
            cpn = cn->fast_out(k)->as_Proj();
            assert(cpn->is_CatchProj(), "must be a CatchProjNode");
            if (cpn->_con == CatchProjNode::fall_through_index)
              _fallthroughcatchproj = cpn;
            else {
              assert(cpn->_con == CatchProjNode::catch_all_index, "must be correct index.");
              _catchallcatchproj = cpn;
            }
          }
        }
        break;
      }
      case TypeFunc::I_O:
        if (pn->_is_io_use)
          _ioproj_catchall = pn;
        else
          _ioproj_fallthrough = pn;
        break;
      case TypeFunc::Memory:
        if (pn->_is_io_use)
          _memproj_catchall = pn;
        else
          _memproj_fallthrough = pn;
        break;
      case TypeFunc::Parms:
        _resproj = pn;
        break;
      default:
        assert(false, "unexpected projection from allocation node.");
    }
  }

}

// Eliminate a card mark sequence.  p2x is a ConvP2XNode
void PhaseMacroExpand::eliminate_card_mark(Node* p2x) {
  assert(p2x->Opcode() == Op_CastP2X, "ConvP2XNode required");
  if (!UseG1GC) {
    // vanilla/CMS post barrier
    Node *shift = p2x->unique_out();
    Node *addp = shift->unique_out();
    for (DUIterator_Last jmin, j = addp->last_outs(jmin); j >= jmin; --j) {
      Node *st = addp->last_out(j);
      assert(st->is_Store(), "store required");
      _igvn.replace_node(st, st->in(MemNode::Memory));
    }
  } else {
    // G1 pre/post barriers
    assert(p2x->outcnt() == 2, "expects 2 users: Xor and URShift nodes");
    // It could be only one user, URShift node, in Object.clone() instrinsic
    // but the new allocation is passed to arraycopy stub and it could not
    // be scalar replaced. So we don't check the case.

    // Remove G1 post barrier.

    // Search for CastP2X->Xor->URShift->Cmp path which
    // checks if the store done to a different from the value's region.
    // And replace Cmp with #0 (false) to collapse G1 post barrier.
    Node* xorx = NULL;
    for (DUIterator_Fast imax, i = p2x->fast_outs(imax); i < imax; i++) {
      Node* u = p2x->fast_out(i);
      if (u->Opcode() == Op_XorX) {
        xorx = u;
        break;
      }
    }
    assert(xorx != NULL, "missing G1 post barrier");
    Node* shift = xorx->unique_out();
    Node* cmpx = shift->unique_out();
    assert(cmpx->is_Cmp() && cmpx->unique_out()->is_Bool() &&
    cmpx->unique_out()->as_Bool()->_test._test == BoolTest::ne,
    "missing region check in G1 post barrier");
    _igvn.replace_node(cmpx, makecon(TypeInt::CC_EQ));

    // Remove G1 pre barrier.

    // Search "if (marking != 0)" check and set it to "false".
    Node* this_region = p2x->in(0);
    assert(this_region != NULL, "");
    // There is no G1 pre barrier if previous stored value is NULL
    // (for example, after initialization).
    if (this_region->is_Region() && this_region->req() == 3) {
      int ind = 1;
      if (!this_region->in(ind)->is_IfFalse()) {
        ind = 2;
      }
      if (this_region->in(ind)->is_IfFalse()) {
        Node* bol = this_region->in(ind)->in(0)->in(1);
        assert(bol->is_Bool(), "");
        cmpx = bol->in(1);
        if (bol->as_Bool()->_test._test == BoolTest::ne &&
            cmpx->is_Cmp() && cmpx->in(2) == intcon(0) &&
            cmpx->in(1)->is_Load()) {
          Node* adr = cmpx->in(1)->as_Load()->in(MemNode::Address);
          const int marking_offset = in_bytes(JavaThread::satb_mark_queue_offset() +
                                              PtrQueue::byte_offset_of_active());
          if (adr->is_AddP() && adr->in(AddPNode::Base) == top() &&
              adr->in(AddPNode::Address)->Opcode() == Op_ThreadLocal &&
              adr->in(AddPNode::Offset) == MakeConX(marking_offset)) {
            _igvn.replace_node(cmpx, makecon(TypeInt::CC_EQ));
          }
        }
      }
    }
    // Now CastP2X can be removed since it is used only on dead path
    // which currently still alive until igvn optimize it.
    assert(p2x->unique_out()->Opcode() == Op_URShiftX, "");
    _igvn.replace_node(p2x, top());
  }
}

// Search for a memory operation for the specified memory slice.
static Node *scan_mem_chain(Node *mem, int alias_idx, int offset, Node *start_mem, Node *alloc, PhaseGVN *phase) {
  Node *orig_mem = mem;
  Node *alloc_mem = alloc->in(TypeFunc::Memory);
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
        if (!call->may_modify(tinst, phase)) {
          mem = call->in(TypeFunc::Memory);
        }
        mem = in->in(TypeFunc::Memory);
      } else if (in->is_MemBar()) {
        mem = in->in(TypeFunc::Memory);
      } else {
        assert(false, "unexpected projection");
      }
    } else if (mem->is_Store()) {
      const TypePtr* atype = mem->as_Store()->adr_type();
      int adr_idx = Compile::current()->get_alias_index(atype);
      if (adr_idx == alias_idx) {
        assert(atype->isa_oopptr(), "address type must be oopptr");
        int adr_offset = atype->offset();
        uint adr_iid = atype->is_oopptr()->instance_id();
        // Array elements references have the same alias_idx
        // but different offset and different instance_id.
        if (adr_offset == offset && adr_iid == alloc->_idx)
          return mem;
      } else {
        assert(adr_idx == Compile::AliasIdxRaw, "address must match or be raw");
      }
      mem = mem->in(MemNode::Memory);
    } else if (mem->is_ClearArray()) {
      if (!ClearArrayNode::step_through(&mem, alloc->_idx, phase)) {
        // Can not bypass initialization of the instance
        // we are looking.
        debug_only(intptr_t offset;)
        assert(alloc == AllocateNode::Ideal_allocation(mem->in(3), phase, offset), "sanity");
        InitializeNode* init = alloc->as_Allocate()->initialization();
        // We are looking for stored value, return Initialize node
        // or memory edge from Allocate node.
        if (init != NULL)
          return init;
        else
          return alloc->in(TypeFunc::Memory); // It will produce zero value (see callers).
      }
      // Otherwise skip it (the call updated 'mem' value).
    } else if (mem->Opcode() == Op_SCMemProj) {
      assert(mem->in(0)->is_LoadStore(), "sanity");
      const TypePtr* atype = mem->in(0)->in(MemNode::Address)->bottom_type()->is_ptr();
      int adr_idx = Compile::current()->get_alias_index(atype);
      if (adr_idx == alias_idx) {
        assert(false, "Object is not scalar replaceable if a LoadStore node access its field");
        return NULL;
      }
      mem = mem->in(0)->in(MemNode::Memory);
    } else {
      return mem;
    }
    assert(mem != orig_mem, "dead memory loop");
  }
}

//
// Given a Memory Phi, compute a value Phi containing the values from stores
// on the input paths.
// Note: this function is recursive, its depth is limied by the "level" argument
// Returns the computed Phi, or NULL if it cannot compute it.
Node *PhaseMacroExpand::value_from_mem_phi(Node *mem, BasicType ft, const Type *phi_type, const TypeOopPtr *adr_t, Node *alloc, Node_Stack *value_phis, int level) {
  assert(mem->is_Phi(), "sanity");
  int alias_idx = C->get_alias_index(adr_t);
  int offset = adr_t->offset();
  int instance_id = adr_t->instance_id();

  // Check if an appropriate value phi already exists.
  Node* region = mem->in(0);
  for (DUIterator_Fast kmax, k = region->fast_outs(kmax); k < kmax; k++) {
    Node* phi = region->fast_out(k);
    if (phi->is_Phi() && phi != mem &&
        phi->as_Phi()->is_same_inst_field(phi_type, instance_id, alias_idx, offset)) {
      return phi;
    }
  }
  // Check if an appropriate new value phi already exists.
  Node* new_phi = NULL;
  uint size = value_phis->size();
  for (uint i=0; i < size; i++) {
    if ( mem->_idx == value_phis->index_at(i) ) {
      return value_phis->node_at(i);
    }
  }

  if (level <= 0) {
    return NULL; // Give up: phi tree too deep
  }
  Node *start_mem = C->start()->proj_out(TypeFunc::Memory);
  Node *alloc_mem = alloc->in(TypeFunc::Memory);

  uint length = mem->req();
  GrowableArray <Node *> values(length, length, NULL);

  // create a new Phi for the value
  PhiNode *phi = new (C, length) PhiNode(mem->in(0), phi_type, NULL, instance_id, alias_idx, offset);
  transform_later(phi);
  value_phis->push(phi, mem->_idx);

  for (uint j = 1; j < length; j++) {
    Node *in = mem->in(j);
    if (in == NULL || in->is_top()) {
      values.at_put(j, in);
    } else  {
      Node *val = scan_mem_chain(in, alias_idx, offset, start_mem, alloc, &_igvn);
      if (val == start_mem || val == alloc_mem) {
        // hit a sentinel, return appropriate 0 value
        values.at_put(j, _igvn.zerocon(ft));
        continue;
      }
      if (val->is_Initialize()) {
        val = val->as_Initialize()->find_captured_store(offset, type2aelembytes(ft), &_igvn);
      }
      if (val == NULL) {
        return NULL;  // can't find a value on this path
      }
      if (val == mem) {
        values.at_put(j, mem);
      } else if (val->is_Store()) {
        values.at_put(j, val->in(MemNode::ValueIn));
      } else if(val->is_Proj() && val->in(0) == alloc) {
        values.at_put(j, _igvn.zerocon(ft));
      } else if (val->is_Phi()) {
        val = value_from_mem_phi(val, ft, phi_type, adr_t, alloc, value_phis, level-1);
        if (val == NULL) {
          return NULL;
        }
        values.at_put(j, val);
      } else if (val->Opcode() == Op_SCMemProj) {
        assert(val->in(0)->is_LoadStore(), "sanity");
        assert(false, "Object is not scalar replaceable if a LoadStore node access its field");
        return NULL;
      } else {
#ifdef ASSERT
        val->dump();
        assert(false, "unknown node on this path");
#endif
        return NULL;  // unknown node on this path
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

// Search the last value stored into the object's field.
Node *PhaseMacroExpand::value_from_mem(Node *sfpt_mem, BasicType ft, const Type *ftype, const TypeOopPtr *adr_t, Node *alloc) {
  assert(adr_t->is_known_instance_field(), "instance required");
  int instance_id = adr_t->instance_id();
  assert((uint)instance_id == alloc->_idx, "wrong allocation");

  int alias_idx = C->get_alias_index(adr_t);
  int offset = adr_t->offset();
  Node *start_mem = C->start()->proj_out(TypeFunc::Memory);
  Node *alloc_ctrl = alloc->in(TypeFunc::Control);
  Node *alloc_mem = alloc->in(TypeFunc::Memory);
  Arena *a = Thread::current()->resource_area();
  VectorSet visited(a);


  bool done = sfpt_mem == alloc_mem;
  Node *mem = sfpt_mem;
  while (!done) {
    if (visited.test_set(mem->_idx)) {
      return NULL;  // found a loop, give up
    }
    mem = scan_mem_chain(mem, alias_idx, offset, start_mem, alloc, &_igvn);
    if (mem == start_mem || mem == alloc_mem) {
      done = true;  // hit a sentinel, return appropriate 0 value
    } else if (mem->is_Initialize()) {
      mem = mem->as_Initialize()->find_captured_store(offset, type2aelembytes(ft), &_igvn);
      if (mem == NULL) {
        done = true; // Something go wrong.
      } else if (mem->is_Store()) {
        const TypePtr* atype = mem->as_Store()->adr_type();
        assert(C->get_alias_index(atype) == Compile::AliasIdxRaw, "store is correct memory slice");
        done = true;
      }
    } else if (mem->is_Store()) {
      const TypeOopPtr* atype = mem->as_Store()->adr_type()->isa_oopptr();
      assert(atype != NULL, "address type must be oopptr");
      assert(C->get_alias_index(atype) == alias_idx &&
             atype->is_known_instance_field() && atype->offset() == offset &&
             atype->instance_id() == instance_id, "store is correct memory slice");
      done = true;
    } else if (mem->is_Phi()) {
      // try to find a phi's unique input
      Node *unique_input = NULL;
      Node *top = C->top();
      for (uint i = 1; i < mem->req(); i++) {
        Node *n = scan_mem_chain(mem->in(i), alias_idx, offset, start_mem, alloc, &_igvn);
        if (n == NULL || n == top || n == mem) {
          continue;
        } else if (unique_input == NULL) {
          unique_input = n;
        } else if (unique_input != n) {
          unique_input = top;
          break;
        }
      }
      if (unique_input != NULL && unique_input != top) {
        mem = unique_input;
      } else {
        done = true;
      }
    } else {
      assert(false, "unexpected node");
    }
  }
  if (mem != NULL) {
    if (mem == start_mem || mem == alloc_mem) {
      // hit a sentinel, return appropriate 0 value
      return _igvn.zerocon(ft);
    } else if (mem->is_Store()) {
      return mem->in(MemNode::ValueIn);
    } else if (mem->is_Phi()) {
      // attempt to produce a Phi reflecting the values on the input paths of the Phi
      Node_Stack value_phis(a, 8);
      Node * phi = value_from_mem_phi(mem, ft, ftype, adr_t, alloc, &value_phis, ValueSearchLimit);
      if (phi != NULL) {
        return phi;
      } else {
        // Kill all new Phis
        while(value_phis.is_nonempty()) {
          Node* n = value_phis.node();
          _igvn.hash_delete(n);
          _igvn.subsume_node(n, C->top());
          value_phis.pop();
        }
      }
    }
  }
  // Something go wrong.
  return NULL;
}

// Check the possibility of scalar replacement.
bool PhaseMacroExpand::can_eliminate_allocation(AllocateNode *alloc, GrowableArray <SafePointNode *>& safepoints) {
  //  Scan the uses of the allocation to check for anything that would
  //  prevent us from eliminating it.
  NOT_PRODUCT( const char* fail_eliminate = NULL; )
  DEBUG_ONLY( Node* disq_node = NULL; )
  bool  can_eliminate = true;

  Node* res = alloc->result_cast();
  const TypeOopPtr* res_type = NULL;
  if (res == NULL) {
    // All users were eliminated.
  } else if (!res->is_CheckCastPP()) {
    alloc->_is_scalar_replaceable = false;  // don't try again
    NOT_PRODUCT(fail_eliminate = "Allocation does not have unique CheckCastPP";)
    can_eliminate = false;
  } else {
    res_type = _igvn.type(res)->isa_oopptr();
    if (res_type == NULL) {
      NOT_PRODUCT(fail_eliminate = "Neither instance or array allocation";)
      can_eliminate = false;
    } else if (res_type->isa_aryptr()) {
      int length = alloc->in(AllocateNode::ALength)->find_int_con(-1);
      if (length < 0) {
        NOT_PRODUCT(fail_eliminate = "Array's size is not constant";)
        can_eliminate = false;
      }
    }
  }

  if (can_eliminate && res != NULL) {
    for (DUIterator_Fast jmax, j = res->fast_outs(jmax);
                               j < jmax && can_eliminate; j++) {
      Node* use = res->fast_out(j);

      if (use->is_AddP()) {
        const TypePtr* addp_type = _igvn.type(use)->is_ptr();
        int offset = addp_type->offset();

        if (offset == Type::OffsetTop || offset == Type::OffsetBot) {
          NOT_PRODUCT(fail_eliminate = "Undefined field referrence";)
          can_eliminate = false;
          break;
        }
        for (DUIterator_Fast kmax, k = use->fast_outs(kmax);
                                   k < kmax && can_eliminate; k++) {
          Node* n = use->fast_out(k);
          if (!n->is_Store() && n->Opcode() != Op_CastP2X) {
            DEBUG_ONLY(disq_node = n;)
            if (n->is_Load() || n->is_LoadStore()) {
              NOT_PRODUCT(fail_eliminate = "Field load";)
            } else {
              NOT_PRODUCT(fail_eliminate = "Not store field referrence";)
            }
            can_eliminate = false;
          }
        }
      } else if (use->is_SafePoint()) {
        SafePointNode* sfpt = use->as_SafePoint();
        if (sfpt->is_Call() && sfpt->as_Call()->has_non_debug_use(res)) {
          // Object is passed as argument.
          DEBUG_ONLY(disq_node = use;)
          NOT_PRODUCT(fail_eliminate = "Object is passed as argument";)
          can_eliminate = false;
        }
        Node* sfptMem = sfpt->memory();
        if (sfptMem == NULL || sfptMem->is_top()) {
          DEBUG_ONLY(disq_node = use;)
          NOT_PRODUCT(fail_eliminate = "NULL or TOP memory";)
          can_eliminate = false;
        } else {
          safepoints.append_if_missing(sfpt);
        }
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
          }else {
            NOT_PRODUCT(fail_eliminate = "Object is referenced by node";)
          }
          DEBUG_ONLY(disq_node = use;)
        }
        can_eliminate = false;
      }
    }
  }

#ifndef PRODUCT
  if (PrintEliminateAllocations) {
    if (can_eliminate) {
      tty->print("Scalar ");
      if (res == NULL)
        alloc->dump();
      else
        res->dump();
    } else {
      tty->print("NotScalar (%s)", fail_eliminate);
      if (res == NULL)
        alloc->dump();
      else
        res->dump();
#ifdef ASSERT
      if (disq_node != NULL) {
          tty->print("  >>>> ");
          disq_node->dump();
      }
#endif /*ASSERT*/
    }
  }
#endif
  return can_eliminate;
}

// Do scalar replacement.
bool PhaseMacroExpand::scalar_replacement(AllocateNode *alloc, GrowableArray <SafePointNode *>& safepoints) {
  GrowableArray <SafePointNode *> safepoints_done;

  ciKlass* klass = NULL;
  ciInstanceKlass* iklass = NULL;
  int nfields = 0;
  int array_base;
  int element_size;
  BasicType basic_elem_type;
  ciType* elem_type;

  Node* res = alloc->result_cast();
  const TypeOopPtr* res_type = NULL;
  if (res != NULL) { // Could be NULL when there are no users
    res_type = _igvn.type(res)->isa_oopptr();
  }

  if (res != NULL) {
    klass = res_type->klass();
    if (res_type->isa_instptr()) {
      // find the fields of the class which will be needed for safepoint debug information
      assert(klass->is_instance_klass(), "must be an instance klass.");
      iklass = klass->as_instance_klass();
      nfields = iklass->nof_nonstatic_fields();
    } else {
      // find the array's elements which will be needed for safepoint debug information
      nfields = alloc->in(AllocateNode::ALength)->find_int_con(-1);
      assert(klass->is_array_klass() && nfields >= 0, "must be an array klass.");
      elem_type = klass->as_array_klass()->element_type();
      basic_elem_type = elem_type->basic_type();
      array_base = arrayOopDesc::base_offset_in_bytes(basic_elem_type);
      element_size = type2aelembytes(basic_elem_type);
    }
  }
  //
  // Process the safepoint uses
  //
  while (safepoints.length() > 0) {
    SafePointNode* sfpt = safepoints.pop();
    Node* mem = sfpt->memory();
    uint first_ind = sfpt->req();
    SafePointScalarObjectNode* sobj = new (C, 1) SafePointScalarObjectNode(res_type,
#ifdef ASSERT
                                                 alloc,
#endif
                                                 first_ind, nfields);
    sobj->init_req(0, sfpt->in(TypeFunc::Control));
    transform_later(sobj);

    // Scan object's fields adding an input to the safepoint for each field.
    for (int j = 0; j < nfields; j++) {
      intptr_t offset;
      ciField* field = NULL;
      if (iklass != NULL) {
        field = iklass->nonstatic_field_at(j);
        offset = field->offset();
        elem_type = field->type();
        basic_elem_type = field->layout_type();
      } else {
        offset = array_base + j * (intptr_t)element_size;
      }

      const Type *field_type;
      // The next code is taken from Parse::do_get_xxx().
      if (basic_elem_type == T_OBJECT || basic_elem_type == T_ARRAY) {
        if (!elem_type->is_loaded()) {
          field_type = TypeInstPtr::BOTTOM;
        } else if (field != NULL && field->is_constant()) {
          // This can happen if the constant oop is non-perm.
          ciObject* con = field->constant_value().as_object();
          // Do not "join" in the previous type; it doesn't add value,
          // and may yield a vacuous result if the field is of interface type.
          field_type = TypeOopPtr::make_from_constant(con)->isa_oopptr();
          assert(field_type != NULL, "field singleton type must be consistent");
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

      const TypeOopPtr *field_addr_type = res_type->add_offset(offset)->isa_oopptr();

      Node *field_val = value_from_mem(mem, basic_elem_type, field_type, field_addr_type, alloc);
      if (field_val == NULL) {
        // we weren't able to find a value for this field,
        // give up on eliminating this allocation
        alloc->_is_scalar_replaceable = false;  // don't try again
        // remove any extra entries we added to the safepoint
        uint last = sfpt->req() - 1;
        for (int k = 0;  k < j; k++) {
          sfpt->del_req(last--);
        }
        // rollback processed safepoints
        while (safepoints_done.length() > 0) {
          SafePointNode* sfpt_done = safepoints_done.pop();
          // remove any extra entries we added to the safepoint
          last = sfpt_done->req() - 1;
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
              if (scobj->first_index() == sfpt_done->req() &&
                  scobj->n_fields() == (uint)nfields) {
                assert(scobj->alloc() == alloc, "sanity");
                sfpt_done->set_req(i, res);
              }
            }
          }
        }
#ifndef PRODUCT
        if (PrintEliminateAllocations) {
          if (field != NULL) {
            tty->print("=== At SafePoint node %d can't find value of Field: ",
                       sfpt->_idx);
            field->print();
            int field_idx = C->get_alias_index(field_addr_type);
            tty->print(" (alias_idx=%d)", field_idx);
          } else { // Array's element
            tty->print("=== At SafePoint node %d can't find value of array element [%d]",
                       sfpt->_idx, j);
          }
          tty->print(", which prevents elimination of: ");
          if (res == NULL)
            alloc->dump();
          else
            res->dump();
        }
#endif
        return false;
      }
      if (UseCompressedOops && field_type->isa_narrowoop()) {
        // Enable "DecodeN(EncodeP(Allocate)) --> Allocate" transformation
        // to be able scalar replace the allocation.
        if (field_val->is_EncodeP()) {
          field_val = field_val->in(1);
        } else {
          field_val = transform_later(new (C, 2) DecodeNNode(field_val, field_val->bottom_type()->make_ptr()));
        }
      }
      sfpt->add_req(field_val);
    }
    JVMState *jvms = sfpt->jvms();
    jvms->set_endoff(sfpt->req());
    // Now make a pass over the debug information replacing any references
    // to the allocated object with "sobj"
    int start = jvms->debug_start();
    int end   = jvms->debug_end();
    for (int i = start; i < end; i++) {
      if (sfpt->in(i) == res) {
        sfpt->set_req(i, sobj);
      }
    }
    safepoints_done.append_if_missing(sfpt); // keep it for rollback
  }
  return true;
}

// Process users of eliminated allocation.
void PhaseMacroExpand::process_users_of_allocation(AllocateNode *alloc) {
  Node* res = alloc->result_cast();
  if (res != NULL) {
    for (DUIterator_Last jmin, j = res->last_outs(jmin); j >= jmin; ) {
      Node *use = res->last_out(j);
      uint oc1 = res->outcnt();

      if (use->is_AddP()) {
        for (DUIterator_Last kmin, k = use->last_outs(kmin); k >= kmin; ) {
          Node *n = use->last_out(k);
          uint oc2 = use->outcnt();
          if (n->is_Store()) {
#ifdef ASSERT
            // Verify that there is no dependent MemBarVolatile nodes,
            // they should be removed during IGVN, see MemBarNode::Ideal().
            for (DUIterator_Fast pmax, p = n->fast_outs(pmax);
                                       p < pmax; p++) {
              Node* mb = n->fast_out(p);
              assert(mb->is_Initialize() || !mb->is_MemBar() ||
                     mb->req() <= MemBarNode::Precedent ||
                     mb->in(MemBarNode::Precedent) != n,
                     "MemBarVolatile should be eliminated for non-escaping object");
            }
#endif
            _igvn.replace_node(n, n->in(MemNode::Memory));
          } else {
            eliminate_card_mark(n);
          }
          k -= (oc2 - use->outcnt());
        }
      } else {
        eliminate_card_mark(use);
      }
      j -= (oc1 - res->outcnt());
    }
    assert(res->outcnt() == 0, "all uses of allocated objects must be deleted");
    _igvn.remove_dead_node(res);
  }

  //
  // Process other users of allocation's projections
  //
  if (_resproj != NULL && _resproj->outcnt() != 0) {
    for (DUIterator_Last jmin, j = _resproj->last_outs(jmin); j >= jmin; ) {
      Node *use = _resproj->last_out(j);
      uint oc1 = _resproj->outcnt();
      if (use->is_Initialize()) {
        // Eliminate Initialize node.
        InitializeNode *init = use->as_Initialize();
        assert(init->outcnt() <= 2, "only a control and memory projection expected");
        Node *ctrl_proj = init->proj_out(TypeFunc::Control);
        if (ctrl_proj != NULL) {
           assert(init->in(TypeFunc::Control) == _fallthroughcatchproj, "allocation control projection");
          _igvn.replace_node(ctrl_proj, _fallthroughcatchproj);
        }
        Node *mem_proj = init->proj_out(TypeFunc::Memory);
        if (mem_proj != NULL) {
          Node *mem = init->in(TypeFunc::Memory);
#ifdef ASSERT
          if (mem->is_MergeMem()) {
            assert(mem->in(TypeFunc::Memory) == _memproj_fallthrough, "allocation memory projection");
          } else {
            assert(mem == _memproj_fallthrough, "allocation memory projection");
          }
#endif
          _igvn.replace_node(mem_proj, mem);
        }
      } else if (use->is_AddP()) {
        // raw memory addresses used only by the initialization
        _igvn.replace_node(use, C->top());
      } else  {
        assert(false, "only Initialize or AddP expected");
      }
      j -= (oc1 - _resproj->outcnt());
    }
  }
  if (_fallthroughcatchproj != NULL) {
    _igvn.replace_node(_fallthroughcatchproj, alloc->in(TypeFunc::Control));
  }
  if (_memproj_fallthrough != NULL) {
    _igvn.replace_node(_memproj_fallthrough, alloc->in(TypeFunc::Memory));
  }
  if (_memproj_catchall != NULL) {
    _igvn.replace_node(_memproj_catchall, C->top());
  }
  if (_ioproj_fallthrough != NULL) {
    _igvn.replace_node(_ioproj_fallthrough, alloc->in(TypeFunc::I_O));
  }
  if (_ioproj_catchall != NULL) {
    _igvn.replace_node(_ioproj_catchall, C->top());
  }
  if (_catchallcatchproj != NULL) {
    _igvn.replace_node(_catchallcatchproj, C->top());
  }
}

bool PhaseMacroExpand::eliminate_allocate_node(AllocateNode *alloc) {

  if (!EliminateAllocations || !alloc->_is_scalar_replaceable) {
    return false;
  }

  extract_call_projections(alloc);

  GrowableArray <SafePointNode *> safepoints;
  if (!can_eliminate_allocation(alloc, safepoints)) {
    return false;
  }

  if (!scalar_replacement(alloc, safepoints)) {
    return false;
  }

  CompileLog* log = C->log();
  if (log != NULL) {
    Node* klass = alloc->in(AllocateNode::KlassNode);
    const TypeKlassPtr* tklass = _igvn.type(klass)->is_klassptr();
    log->head("eliminate_allocation type='%d'",
              log->identify(tklass->klass()));
    JVMState* p = alloc->jvms();
    while (p != NULL) {
      log->elem("jvms bci='%d' method='%d'", p->bci(), log->identify(p->method()));
      p = p->caller();
    }
    log->tail("eliminate_allocation");
  }

  process_users_of_allocation(alloc);

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


//---------------------------set_eden_pointers-------------------------
void PhaseMacroExpand::set_eden_pointers(Node* &eden_top_adr, Node* &eden_end_adr) {
  if (UseTLAB) {                // Private allocation: load from TLS
    Node* thread = transform_later(new (C, 1) ThreadLocalNode());
    int tlab_top_offset = in_bytes(JavaThread::tlab_top_offset());
    int tlab_end_offset = in_bytes(JavaThread::tlab_end_offset());
    eden_top_adr = basic_plus_adr(top()/*not oop*/, thread, tlab_top_offset);
    eden_end_adr = basic_plus_adr(top()/*not oop*/, thread, tlab_end_offset);
  } else {                      // Shared allocation: load from globals
    CollectedHeap* ch = Universe::heap();
    address top_adr = (address)ch->top_addr();
    address end_adr = (address)ch->end_addr();
    eden_top_adr = makecon(TypeRawPtr::make(top_adr));
    eden_end_adr = basic_plus_adr(eden_top_adr, end_adr - top_adr);
  }
}


Node* PhaseMacroExpand::make_load(Node* ctl, Node* mem, Node* base, int offset, const Type* value_type, BasicType bt) {
  Node* adr = basic_plus_adr(base, offset);
  const TypePtr* adr_type = adr->bottom_type()->is_ptr();
  Node* value = LoadNode::make(_igvn, ctl, mem, adr, adr_type, value_type, bt);
  transform_later(value);
  return value;
}


Node* PhaseMacroExpand::make_store(Node* ctl, Node* mem, Node* base, int offset, Node* value, BasicType bt) {
  Node* adr = basic_plus_adr(base, offset);
  mem = StoreNode::make(_igvn, ctl, mem, adr, NULL, value, bt);
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
            const TypeFunc* slow_call_type, // Type of slow call
            address slow_call_address  // Address of slow call
    )
{

  Node* ctrl = alloc->in(TypeFunc::Control);
  Node* mem  = alloc->in(TypeFunc::Memory);
  Node* i_o  = alloc->in(TypeFunc::I_O);
  Node* size_in_bytes     = alloc->in(AllocateNode::AllocSize);
  Node* klass_node        = alloc->in(AllocateNode::KlassNode);
  Node* initial_slow_test = alloc->in(AllocateNode::InitialTest);

  assert(ctrl != NULL, "must have control");
  // We need a Region and corresponding Phi's to merge the slow-path and fast-path results.
  // they will not be used if "always_slow" is set
  enum { slow_result_path = 1, fast_result_path = 2 };
  Node *result_region;
  Node *result_phi_rawmem;
  Node *result_phi_rawoop;
  Node *result_phi_i_o;

  // The initial slow comparison is a size check, the comparison
  // we want to do is a BoolTest::gt
  bool always_slow = false;
  int tv = _igvn.find_int_con(initial_slow_test, -1);
  if (tv >= 0) {
    always_slow = (tv == 1);
    initial_slow_test = NULL;
  } else {
    initial_slow_test = BoolNode::make_predicate(initial_slow_test, &_igvn);
  }

  if (C->env()->dtrace_alloc_probes() ||
      !UseTLAB && (!Universe::heap()->supports_inline_contig_alloc() ||
                   (UseConcMarkSweepGC && CMSIncrementalMode))) {
    // Force slow-path allocation
    always_slow = true;
    initial_slow_test = NULL;
  }


  enum { too_big_or_final_path = 1, need_gc_path = 2 };
  Node *slow_region = NULL;
  Node *toobig_false = ctrl;

  assert (initial_slow_test == NULL || !always_slow, "arguments must be consistent");
  // generate the initial test if necessary
  if (initial_slow_test != NULL ) {
    slow_region = new (C, 3) RegionNode(3);

    // Now make the initial failure test.  Usually a too-big test but
    // might be a TRUE for finalizers or a fancy class check for
    // newInstance0.
    IfNode *toobig_iff = new (C, 2) IfNode(ctrl, initial_slow_test, PROB_MIN, COUNT_UNKNOWN);
    transform_later(toobig_iff);
    // Plug the failing-too-big test into the slow-path region
    Node *toobig_true = new (C, 1) IfTrueNode( toobig_iff );
    transform_later(toobig_true);
    slow_region    ->init_req( too_big_or_final_path, toobig_true );
    toobig_false = new (C, 1) IfFalseNode( toobig_iff );
    transform_later(toobig_false);
  } else {         // No initial test, just fall into next case
    toobig_false = ctrl;
    debug_only(slow_region = NodeSentinel);
  }

  Node *slow_mem = mem;  // save the current memory state for slow path
  // generate the fast allocation code unless we know that the initial test will always go slow
  if (!always_slow) {
    // Fast path modifies only raw memory.
    if (mem->is_MergeMem()) {
      mem = mem->as_MergeMem()->memory_at(Compile::AliasIdxRaw);
    }

    Node* eden_top_adr;
    Node* eden_end_adr;

    set_eden_pointers(eden_top_adr, eden_end_adr);

    // Load Eden::end.  Loop invariant and hoisted.
    //
    // Note: We set the control input on "eden_end" and "old_eden_top" when using
    //       a TLAB to work around a bug where these values were being moved across
    //       a safepoint.  These are not oops, so they cannot be include in the oop
    //       map, but the can be changed by a GC.   The proper way to fix this would
    //       be to set the raw memory state when generating a  SafepointNode.  However
    //       this will require extensive changes to the loop optimization in order to
    //       prevent a degradation of the optimization.
    //       See comment in memnode.hpp, around line 227 in class LoadPNode.
    Node *eden_end = make_load(ctrl, mem, eden_end_adr, 0, TypeRawPtr::BOTTOM, T_ADDRESS);

    // allocate the Region and Phi nodes for the result
    result_region = new (C, 3) RegionNode(3);
    result_phi_rawmem = new (C, 3) PhiNode( result_region, Type::MEMORY, TypeRawPtr::BOTTOM );
    result_phi_rawoop = new (C, 3) PhiNode( result_region, TypeRawPtr::BOTTOM );
    result_phi_i_o    = new (C, 3) PhiNode( result_region, Type::ABIO ); // I/O is used for Prefetch

    // We need a Region for the loop-back contended case.
    enum { fall_in_path = 1, contended_loopback_path = 2 };
    Node *contended_region;
    Node *contended_phi_rawmem;
    if( UseTLAB ) {
      contended_region = toobig_false;
      contended_phi_rawmem = mem;
    } else {
      contended_region = new (C, 3) RegionNode(3);
      contended_phi_rawmem = new (C, 3) PhiNode( contended_region, Type::MEMORY, TypeRawPtr::BOTTOM);
      // Now handle the passing-too-big test.  We fall into the contended
      // loop-back merge point.
      contended_region    ->init_req( fall_in_path, toobig_false );
      contended_phi_rawmem->init_req( fall_in_path, mem );
      transform_later(contended_region);
      transform_later(contended_phi_rawmem);
    }

    // Load(-locked) the heap top.
    // See note above concerning the control input when using a TLAB
    Node *old_eden_top = UseTLAB
      ? new (C, 3) LoadPNode     ( ctrl, contended_phi_rawmem, eden_top_adr, TypeRawPtr::BOTTOM, TypeRawPtr::BOTTOM )
      : new (C, 3) LoadPLockedNode( contended_region, contended_phi_rawmem, eden_top_adr );

    transform_later(old_eden_top);
    // Add to heap top to get a new heap top
    Node *new_eden_top = new (C, 4) AddPNode( top(), old_eden_top, size_in_bytes );
    transform_later(new_eden_top);
    // Check for needing a GC; compare against heap end
    Node *needgc_cmp = new (C, 3) CmpPNode( new_eden_top, eden_end );
    transform_later(needgc_cmp);
    Node *needgc_bol = new (C, 2) BoolNode( needgc_cmp, BoolTest::ge );
    transform_later(needgc_bol);
    IfNode *needgc_iff = new (C, 2) IfNode(contended_region, needgc_bol, PROB_UNLIKELY_MAG(4), COUNT_UNKNOWN );
    transform_later(needgc_iff);

    // Plug the failing-heap-space-need-gc test into the slow-path region
    Node *needgc_true = new (C, 1) IfTrueNode( needgc_iff );
    transform_later(needgc_true);
    if( initial_slow_test ) {
      slow_region    ->init_req( need_gc_path, needgc_true );
      // This completes all paths into the slow merge point
      transform_later(slow_region);
    } else {                      // No initial slow path needed!
      // Just fall from the need-GC path straight into the VM call.
      slow_region    = needgc_true;
    }
    // No need for a GC.  Setup for the Store-Conditional
    Node *needgc_false = new (C, 1) IfFalseNode( needgc_iff );
    transform_later(needgc_false);

    // Grab regular I/O before optional prefetch may change it.
    // Slow-path does no I/O so just set it to the original I/O.
    result_phi_i_o->init_req( slow_result_path, i_o );

    i_o = prefetch_allocation(i_o, needgc_false, contended_phi_rawmem,
                              old_eden_top, new_eden_top, length);

    // Store (-conditional) the modified eden top back down.
    // StorePConditional produces flags for a test PLUS a modified raw
    // memory state.
    Node *store_eden_top;
    Node *fast_oop_ctrl;
    if( UseTLAB ) {
      store_eden_top = new (C, 4) StorePNode( needgc_false, contended_phi_rawmem, eden_top_adr, TypeRawPtr::BOTTOM, new_eden_top );
      transform_later(store_eden_top);
      fast_oop_ctrl = needgc_false; // No contention, so this is the fast path
    } else {
      store_eden_top = new (C, 5) StorePConditionalNode( needgc_false, contended_phi_rawmem, eden_top_adr, new_eden_top, old_eden_top );
      transform_later(store_eden_top);
      Node *contention_check = new (C, 2) BoolNode( store_eden_top, BoolTest::ne );
      transform_later(contention_check);
      store_eden_top = new (C, 1) SCMemProjNode(store_eden_top);
      transform_later(store_eden_top);

      // If not using TLABs, check to see if there was contention.
      IfNode *contention_iff = new (C, 2) IfNode ( needgc_false, contention_check, PROB_MIN, COUNT_UNKNOWN );
      transform_later(contention_iff);
      Node *contention_true = new (C, 1) IfTrueNode( contention_iff );
      transform_later(contention_true);
      // If contention, loopback and try again.
      contended_region->init_req( contended_loopback_path, contention_true );
      contended_phi_rawmem->init_req( contended_loopback_path, store_eden_top );

      // Fast-path succeeded with no contention!
      Node *contention_false = new (C, 1) IfFalseNode( contention_iff );
      transform_later(contention_false);
      fast_oop_ctrl = contention_false;
    }

    // Rename successful fast-path variables to make meaning more obvious
    Node* fast_oop        = old_eden_top;
    Node* fast_oop_rawmem = store_eden_top;
    fast_oop_rawmem = initialize_object(alloc,
                                        fast_oop_ctrl, fast_oop_rawmem, fast_oop,
                                        klass_node, length, size_in_bytes);

    if (C->env()->dtrace_extended_probes()) {
      // Slow-path call
      int size = TypeFunc::Parms + 2;
      CallLeafNode *call = new (C, size) CallLeafNode(OptoRuntime::dtrace_object_alloc_Type(),
                                                      CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc_base),
                                                      "dtrace_object_alloc",
                                                      TypeRawPtr::BOTTOM);

      // Get base of thread-local storage area
      Node* thread = new (C, 1) ThreadLocalNode();
      transform_later(thread);

      call->init_req(TypeFunc::Parms+0, thread);
      call->init_req(TypeFunc::Parms+1, fast_oop);
      call->init_req( TypeFunc::Control, fast_oop_ctrl );
      call->init_req( TypeFunc::I_O    , top() )        ;   // does no i/o
      call->init_req( TypeFunc::Memory , fast_oop_rawmem );
      call->init_req( TypeFunc::ReturnAdr, alloc->in(TypeFunc::ReturnAdr) );
      call->init_req( TypeFunc::FramePtr, alloc->in(TypeFunc::FramePtr) );
      transform_later(call);
      fast_oop_ctrl = new (C, 1) ProjNode(call,TypeFunc::Control);
      transform_later(fast_oop_ctrl);
      fast_oop_rawmem = new (C, 1) ProjNode(call,TypeFunc::Memory);
      transform_later(fast_oop_rawmem);
    }

    // Plug in the successful fast-path into the result merge point
    result_region    ->init_req( fast_result_path, fast_oop_ctrl );
    result_phi_rawoop->init_req( fast_result_path, fast_oop );
    result_phi_i_o   ->init_req( fast_result_path, i_o );
    result_phi_rawmem->init_req( fast_result_path, fast_oop_rawmem );
  } else {
    slow_region = ctrl;
  }

  // Generate slow-path call
  CallNode *call = new (C, slow_call_type->domain()->cnt())
    CallStaticJavaNode(slow_call_type, slow_call_address,
                       OptoRuntime::stub_name(slow_call_address),
                       alloc->jvms()->bci(),
                       TypePtr::BOTTOM);
  call->init_req( TypeFunc::Control, slow_region );
  call->init_req( TypeFunc::I_O    , top() )     ;   // does no i/o
  call->init_req( TypeFunc::Memory , slow_mem ); // may gc ptrs
  call->init_req( TypeFunc::ReturnAdr, alloc->in(TypeFunc::ReturnAdr) );
  call->init_req( TypeFunc::FramePtr, alloc->in(TypeFunc::FramePtr) );

  call->init_req(TypeFunc::Parms+0, klass_node);
  if (length != NULL) {
    call->init_req(TypeFunc::Parms+1, length);
  }

  // Copy debug information and adjust JVMState information, then replace
  // allocate node with the call
  copy_call_debug_info((CallNode *) alloc,  call);
  if (!always_slow) {
    call->set_cnt(PROB_UNLIKELY_MAG(4));  // Same effect as RC_UNCOMMON.
  }
  _igvn.hash_delete(alloc);
  _igvn.subsume_node(alloc, call);
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
  extract_call_projections(call);

  // An allocate node has separate memory projections for the uses on the control and i_o paths
  // Replace uses of the control memory projection with result_phi_rawmem (unless we are only generating a slow call)
  if (!always_slow && _memproj_fallthrough != NULL) {
    for (DUIterator_Fast imax, i = _memproj_fallthrough->fast_outs(imax); i < imax; i++) {
      Node *use = _memproj_fallthrough->fast_out(i);
      _igvn.hash_delete(use);
      imax -= replace_input(use, _memproj_fallthrough, result_phi_rawmem);
      _igvn._worklist.push(use);
      // back up iterator
      --i;
    }
  }
  // Now change uses of _memproj_catchall to use _memproj_fallthrough and delete _memproj_catchall so
  // we end up with a call that has only 1 memory projection
  if (_memproj_catchall != NULL ) {
    if (_memproj_fallthrough == NULL) {
      _memproj_fallthrough = new (C, 1) ProjNode(call, TypeFunc::Memory);
      transform_later(_memproj_fallthrough);
    }
    for (DUIterator_Fast imax, i = _memproj_catchall->fast_outs(imax); i < imax; i++) {
      Node *use = _memproj_catchall->fast_out(i);
      _igvn.hash_delete(use);
      imax -= replace_input(use, _memproj_catchall, _memproj_fallthrough);
      _igvn._worklist.push(use);
      // back up iterator
      --i;
    }
  }

  // An allocate node has separate i_o projections for the uses on the control and i_o paths
  // Replace uses of the control i_o projection with result_phi_i_o (unless we are only generating a slow call)
  if (_ioproj_fallthrough == NULL) {
    _ioproj_fallthrough = new (C, 1) ProjNode(call, TypeFunc::I_O);
    transform_later(_ioproj_fallthrough);
  } else if (!always_slow) {
    for (DUIterator_Fast imax, i = _ioproj_fallthrough->fast_outs(imax); i < imax; i++) {
      Node *use = _ioproj_fallthrough->fast_out(i);

      _igvn.hash_delete(use);
      imax -= replace_input(use, _ioproj_fallthrough, result_phi_i_o);
      _igvn._worklist.push(use);
      // back up iterator
      --i;
    }
  }
  // Now change uses of _ioproj_catchall to use _ioproj_fallthrough and delete _ioproj_catchall so
  // we end up with a call that has only 1 control projection
  if (_ioproj_catchall != NULL ) {
    for (DUIterator_Fast imax, i = _ioproj_catchall->fast_outs(imax); i < imax; i++) {
      Node *use = _ioproj_catchall->fast_out(i);
      _igvn.hash_delete(use);
      imax -= replace_input(use, _ioproj_catchall, _ioproj_fallthrough);
      _igvn._worklist.push(use);
      // back up iterator
      --i;
    }
  }

  // if we generated only a slow call, we are done
  if (always_slow)
    return;


  if (_fallthroughcatchproj != NULL) {
    ctrl = _fallthroughcatchproj->clone();
    transform_later(ctrl);
    _igvn.replace_node(_fallthroughcatchproj, result_region);
  } else {
    ctrl = top();
  }
  Node *slow_result;
  if (_resproj == NULL) {
    // no uses of the allocation result
    slow_result = top();
  } else {
    slow_result = _resproj->clone();
    transform_later(slow_result);
    _igvn.replace_node(_resproj, result_phi_rawoop);
  }

  // Plug slow-path into result merge point
  result_region    ->init_req( slow_result_path, ctrl );
  result_phi_rawoop->init_req( slow_result_path, slow_result);
  result_phi_rawmem->init_req( slow_result_path, _memproj_fallthrough );
  transform_later(result_region);
  transform_later(result_phi_rawoop);
  transform_later(result_phi_rawmem);
  transform_later(result_phi_i_o);
  // This completes all paths into the result merge point
}


// Helper for PhaseMacroExpand::expand_allocate_common.
// Initializes the newly-allocated storage.
Node*
PhaseMacroExpand::initialize_object(AllocateNode* alloc,
                                    Node* control, Node* rawmem, Node* object,
                                    Node* klass_node, Node* length,
                                    Node* size_in_bytes) {
  InitializeNode* init = alloc->initialization();
  // Store the klass & mark bits
  Node* mark_node = NULL;
  // For now only enable fast locking for non-array types
  if (UseBiasedLocking && (length == NULL)) {
    mark_node = make_load(NULL, rawmem, klass_node, Klass::prototype_header_offset_in_bytes() + sizeof(oopDesc), TypeRawPtr::BOTTOM, T_ADDRESS);
  } else {
    mark_node = makecon(TypeRawPtr::make((address)markOopDesc::prototype()));
  }
  rawmem = make_store(control, rawmem, object, oopDesc::mark_offset_in_bytes(), mark_node, T_ADDRESS);

  rawmem = make_store(control, rawmem, object, oopDesc::klass_offset_in_bytes(), klass_node, T_OBJECT);
  int header_size = alloc->minimum_header_size();  // conservatively small

  // Array length
  if (length != NULL) {         // Arrays need length field
    rawmem = make_store(control, rawmem, object, arrayOopDesc::length_offset_in_bytes(), length, T_INT);
    // conservatively small header size:
    header_size = arrayOopDesc::base_offset_in_bytes(T_BYTE);
    ciKlass* k = _igvn.type(klass_node)->is_klassptr()->klass();
    if (k->is_array_klass())    // we know the exact header size in most cases:
      header_size = Klass::layout_helper_header_size(k->layout_helper());
  }

  // Clear the object body, if necessary.
  if (init == NULL) {
    // The init has somehow disappeared; be cautious and clear everything.
    //
    // This can happen if a node is allocated but an uncommon trap occurs
    // immediately.  In this case, the Initialize gets associated with the
    // trap, and may be placed in a different (outer) loop, if the Allocate
    // is in a loop.  If (this is rare) the inner loop gets unrolled, then
    // there can be two Allocates to one Initialize.  The answer in all these
    // edge cases is safety first.  It is always safe to clear immediately
    // within an Allocate, and then (maybe or maybe not) clear some more later.
    if (!ZeroTLAB)
      rawmem = ClearArrayNode::clear_memory(control, rawmem, object,
                                            header_size, size_in_bytes,
                                            &_igvn);
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
                                        Node* length) {
   enum { fall_in_path = 1, pf_path = 2 };
   if( UseTLAB && AllocatePrefetchStyle == 2 ) {
      // Generate prefetch allocation with watermark check.
      // As an allocation hits the watermark, we will prefetch starting
      // at a "distance" away from watermark.

      Node *pf_region = new (C, 3) RegionNode(3);
      Node *pf_phi_rawmem = new (C, 3) PhiNode( pf_region, Type::MEMORY,
                                                TypeRawPtr::BOTTOM );
      // I/O is used for Prefetch
      Node *pf_phi_abio = new (C, 3) PhiNode( pf_region, Type::ABIO );

      Node *thread = new (C, 1) ThreadLocalNode();
      transform_later(thread);

      Node *eden_pf_adr = new (C, 4) AddPNode( top()/*not oop*/, thread,
                   _igvn.MakeConX(in_bytes(JavaThread::tlab_pf_top_offset())) );
      transform_later(eden_pf_adr);

      Node *old_pf_wm = new (C, 3) LoadPNode( needgc_false,
                                   contended_phi_rawmem, eden_pf_adr,
                                   TypeRawPtr::BOTTOM, TypeRawPtr::BOTTOM );
      transform_later(old_pf_wm);

      // check against new_eden_top
      Node *need_pf_cmp = new (C, 3) CmpPNode( new_eden_top, old_pf_wm );
      transform_later(need_pf_cmp);
      Node *need_pf_bol = new (C, 2) BoolNode( need_pf_cmp, BoolTest::ge );
      transform_later(need_pf_bol);
      IfNode *need_pf_iff = new (C, 2) IfNode( needgc_false, need_pf_bol,
                                       PROB_UNLIKELY_MAG(4), COUNT_UNKNOWN );
      transform_later(need_pf_iff);

      // true node, add prefetchdistance
      Node *need_pf_true = new (C, 1) IfTrueNode( need_pf_iff );
      transform_later(need_pf_true);

      Node *need_pf_false = new (C, 1) IfFalseNode( need_pf_iff );
      transform_later(need_pf_false);

      Node *new_pf_wmt = new (C, 4) AddPNode( top(), old_pf_wm,
                                    _igvn.MakeConX(AllocatePrefetchDistance) );
      transform_later(new_pf_wmt );
      new_pf_wmt->set_req(0, need_pf_true);

      Node *store_new_wmt = new (C, 4) StorePNode( need_pf_true,
                                       contended_phi_rawmem, eden_pf_adr,
                                       TypeRawPtr::BOTTOM, new_pf_wmt );
      transform_later(store_new_wmt);

      // adding prefetches
      pf_phi_abio->init_req( fall_in_path, i_o );

      Node *prefetch_adr;
      Node *prefetch;
      uint lines = AllocatePrefetchDistance / AllocatePrefetchStepSize;
      uint step_size = AllocatePrefetchStepSize;
      uint distance = 0;

      for ( uint i = 0; i < lines; i++ ) {
        prefetch_adr = new (C, 4) AddPNode( old_pf_wm, new_pf_wmt,
                                            _igvn.MakeConX(distance) );
        transform_later(prefetch_adr);
        prefetch = new (C, 3) PrefetchWriteNode( i_o, prefetch_adr );
        transform_later(prefetch);
        distance += step_size;
        i_o = prefetch;
      }
      pf_phi_abio->set_req( pf_path, i_o );

      pf_region->init_req( fall_in_path, need_pf_false );
      pf_region->init_req( pf_path, need_pf_true );

      pf_phi_rawmem->init_req( fall_in_path, contended_phi_rawmem );
      pf_phi_rawmem->init_req( pf_path, store_new_wmt );

      transform_later(pf_region);
      transform_later(pf_phi_rawmem);
      transform_later(pf_phi_abio);

      needgc_false = pf_region;
      contended_phi_rawmem = pf_phi_rawmem;
      i_o = pf_phi_abio;
   } else if( UseTLAB && AllocatePrefetchStyle == 3 ) {
      // Insert a prefetch for each allocation only on the fast-path
      Node *pf_region = new (C, 3) RegionNode(3);
      Node *pf_phi_rawmem = new (C, 3) PhiNode( pf_region, Type::MEMORY,
                                                TypeRawPtr::BOTTOM );

      // Generate several prefetch instructions only for arrays.
      uint lines = (length != NULL) ? AllocatePrefetchLines : 1;
      uint step_size = AllocatePrefetchStepSize;
      uint distance = AllocatePrefetchDistance;

      // Next cache address.
      Node *cache_adr = new (C, 4) AddPNode(old_eden_top, old_eden_top,
                                            _igvn.MakeConX(distance));
      transform_later(cache_adr);
      cache_adr = new (C, 2) CastP2XNode(needgc_false, cache_adr);
      transform_later(cache_adr);
      Node* mask = _igvn.MakeConX(~(intptr_t)(step_size-1));
      cache_adr = new (C, 3) AndXNode(cache_adr, mask);
      transform_later(cache_adr);
      cache_adr = new (C, 2) CastX2PNode(cache_adr);
      transform_later(cache_adr);

      // Prefetch
      Node *prefetch = new (C, 3) PrefetchWriteNode( contended_phi_rawmem, cache_adr );
      prefetch->set_req(0, needgc_false);
      transform_later(prefetch);
      contended_phi_rawmem = prefetch;
      Node *prefetch_adr;
      distance = step_size;
      for ( uint i = 1; i < lines; i++ ) {
        prefetch_adr = new (C, 4) AddPNode( cache_adr, cache_adr,
                                            _igvn.MakeConX(distance) );
        transform_later(prefetch_adr);
        prefetch = new (C, 3) PrefetchWriteNode( contended_phi_rawmem, prefetch_adr );
        transform_later(prefetch);
        distance += step_size;
        contended_phi_rawmem = prefetch;
      }
   } else if( AllocatePrefetchStyle > 0 ) {
      // Insert a prefetch for each allocation only on the fast-path
      Node *prefetch_adr;
      Node *prefetch;
      // Generate several prefetch instructions only for arrays.
      uint lines = (length != NULL) ? AllocatePrefetchLines : 1;
      uint step_size = AllocatePrefetchStepSize;
      uint distance = AllocatePrefetchDistance;
      for ( uint i = 0; i < lines; i++ ) {
        prefetch_adr = new (C, 4) AddPNode( old_eden_top, new_eden_top,
                                            _igvn.MakeConX(distance) );
        transform_later(prefetch_adr);
        prefetch = new (C, 3) PrefetchWriteNode( i_o, prefetch_adr );
        // Do not let it float too high, since if eden_top == eden_end,
        // both might be null.
        if( i == 0 ) { // Set control for first prefetch, next follows it
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
  expand_allocate_common(alloc, NULL,
                         OptoRuntime::new_instance_Type(),
                         OptoRuntime::new_instance_Java());
}

void PhaseMacroExpand::expand_allocate_array(AllocateArrayNode *alloc) {
  Node* length = alloc->in(AllocateNode::ALength);
  expand_allocate_common(alloc, length,
                         OptoRuntime::new_array_Type(),
                         OptoRuntime::new_array_Java());
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
  if (alock->is_Lock() && !alock->is_coarsened()) {
      // Create new "eliminated" BoxLock node and use it
      // in monitor debug info for the same object.
      BoxLockNode* oldbox = alock->box_node()->as_BoxLock();
      Node* obj = alock->obj_node();
      if (!oldbox->is_eliminated()) {
        BoxLockNode* newbox = oldbox->clone()->as_BoxLock();
        newbox->set_eliminated();
        transform_later(newbox);
        // Replace old box node with new box for all users
        // of the same object.
        for (uint i = 0; i < oldbox->outcnt();) {

          bool next_edge = true;
          Node* u = oldbox->raw_out(i);
          if (u == alock) {
            i++;
            continue; // It will be removed below
          }
          if (u->is_Lock() &&
              u->as_Lock()->obj_node() == obj &&
              // oldbox could be referenced in debug info also
              u->as_Lock()->box_node() == oldbox) {
            assert(u->as_Lock()->is_eliminated(), "sanity");
            _igvn.hash_delete(u);
            u->set_req(TypeFunc::Parms + 1, newbox);
            next_edge = false;
#ifdef ASSERT
          } else if (u->is_Unlock() && u->as_Unlock()->obj_node() == obj) {
            assert(u->as_Unlock()->is_eliminated(), "sanity");
#endif
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
                if (box_node == oldbox && obj_node == obj) {
                  int j = jvms->monitor_box_offset(idx);
                  _igvn.hash_delete(u);
                  u->set_req(j, newbox);
                  next_edge = false;
                }
              } // for (int idx = 0;
            } // for (int depth = 1;
          } // if (u->is_SafePoint()
          if (next_edge) i++;
        } // for (uint i = 0; i < oldbox->outcnt();)
      } // if (!oldbox->is_eliminated())
  } // if (alock->is_Lock() && !lock->is_coarsened())

  CompileLog* log = C->log();
  if (log != NULL) {
    log->head("eliminate_lock lock='%d'",
              alock->is_Lock());
    JVMState* p = alock->jvms();
    while (p != NULL) {
      log->elem("jvms bci='%d' method='%d'", p->bci(), log->identify(p->method()));
      p = p->caller();
    }
    log->tail("eliminate_lock");
  }

  #ifndef PRODUCT
  if (PrintEliminateLocks) {
    if (alock->is_Lock()) {
      tty->print_cr("++++ Eliminating: %d Lock", alock->_idx);
    } else {
      tty->print_cr("++++ Eliminating: %d Unlock", alock->_idx);
    }
  }
  #endif

  Node* mem  = alock->in(TypeFunc::Memory);
  Node* ctrl = alock->in(TypeFunc::Control);

  extract_call_projections(alock);
  // There are 2 projections from the lock.  The lock node will
  // be deleted when its last use is subsumed below.
  assert(alock->outcnt() == 2 &&
         _fallthroughproj != NULL &&
         _memproj_fallthrough != NULL,
         "Unexpected projections from Lock/Unlock");

  Node* fallthroughproj = _fallthroughproj;
  Node* memproj_fallthrough = _memproj_fallthrough;

  // The memory projection from a lock/unlock is RawMem
  // The input to a Lock is merged memory, so extract its RawMem input
  // (unless the MergeMem has been optimized away.)
  if (alock->is_Lock()) {
    // Seach for MemBarAcquire node and delete it also.
    MemBarNode* membar = fallthroughproj->unique_ctrl_out()->as_MemBar();
    assert(membar != NULL && membar->Opcode() == Op_MemBarAcquire, "");
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

  // Seach for MemBarRelease node and delete it also.
  if (alock->is_Unlock() && ctrl != NULL && ctrl->is_Proj() &&
      ctrl->in(0)->is_MemBar()) {
    MemBarNode* membar = ctrl->in(0)->as_MemBar();
    assert(membar->Opcode() == Op_MemBarRelease &&
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

  // Make the merge point
  Node *region;
  Node *mem_phi;
  Node *slow_path;

  if (UseOptoBiasInlining) {
    /*
     *  See the full description in MacroAssembler::biased_locking_enter().
     *
     *  if( (mark_word & biased_lock_mask) == biased_lock_pattern ) {
     *    // The object is biased.
     *    proto_node = klass->prototype_header;
     *    o_node = thread | proto_node;
     *    x_node = o_node ^ mark_word;
     *    if( (x_node & ~age_mask) == 0 ) { // Biased to the current thread ?
     *      // Done.
     *    } else {
     *      if( (x_node & biased_lock_mask) != 0 ) {
     *        // The klass's prototype header is no longer biased.
     *        cas(&mark_word, mark_word, proto_node)
     *        goto cas_lock;
     *      } else {
     *        // The klass's prototype header is still biased.
     *        if( (x_node & epoch_mask) != 0 ) { // Expired epoch?
     *          old = mark_word;
     *          new = o_node;
     *        } else {
     *          // Different thread or anonymous biased.
     *          old = mark_word & (epoch_mask | age_mask | biased_lock_mask);
     *          new = thread | old;
     *        }
     *        // Try to rebias.
     *        if( cas(&mark_word, old, new) == 0 ) {
     *          // Done.
     *        } else {
     *          goto slow_path; // Failed.
     *        }
     *      }
     *    }
     *  } else {
     *    // The object is not biased.
     *    cas_lock:
     *    if( FastLock(obj) == 0 ) {
     *      // Done.
     *    } else {
     *      slow_path:
     *      OptoRuntime::complete_monitor_locking_Java(obj);
     *    }
     *  }
     */

    region  = new (C, 5) RegionNode(5);
    // create a Phi for the memory state
    mem_phi = new (C, 5) PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);

    Node* fast_lock_region  = new (C, 3) RegionNode(3);
    Node* fast_lock_mem_phi = new (C, 3) PhiNode( fast_lock_region, Type::MEMORY, TypeRawPtr::BOTTOM);

    // First, check mark word for the biased lock pattern.
    Node* mark_node = make_load(ctrl, mem, obj, oopDesc::mark_offset_in_bytes(), TypeX_X, TypeX_X->basic_type());

    // Get fast path - mark word has the biased lock pattern.
    ctrl = opt_bits_test(ctrl, fast_lock_region, 1, mark_node,
                         markOopDesc::biased_lock_mask_in_place,
                         markOopDesc::biased_lock_pattern, true);
    // fast_lock_region->in(1) is set to slow path.
    fast_lock_mem_phi->init_req(1, mem);

    // Now check that the lock is biased to the current thread and has
    // the same epoch and bias as Klass::_prototype_header.

    // Special-case a fresh allocation to avoid building nodes:
    Node* klass_node = AllocateNode::Ideal_klass(obj, &_igvn);
    if (klass_node == NULL) {
      Node* k_adr = basic_plus_adr(obj, oopDesc::klass_offset_in_bytes());
      klass_node = transform_later( LoadKlassNode::make(_igvn, mem, k_adr, _igvn.type(k_adr)->is_ptr()) );
#ifdef _LP64
      if (UseCompressedOops && klass_node->is_DecodeN()) {
        assert(klass_node->in(1)->Opcode() == Op_LoadNKlass, "sanity");
        klass_node->in(1)->init_req(0, ctrl);
      } else
#endif
      klass_node->init_req(0, ctrl);
    }
    Node *proto_node = make_load(ctrl, mem, klass_node, Klass::prototype_header_offset_in_bytes() + sizeof(oopDesc), TypeX_X, TypeX_X->basic_type());

    Node* thread = transform_later(new (C, 1) ThreadLocalNode());
    Node* cast_thread = transform_later(new (C, 2) CastP2XNode(ctrl, thread));
    Node* o_node = transform_later(new (C, 3) OrXNode(cast_thread, proto_node));
    Node* x_node = transform_later(new (C, 3) XorXNode(o_node, mark_node));

    // Get slow path - mark word does NOT match the value.
    Node* not_biased_ctrl =  opt_bits_test(ctrl, region, 3, x_node,
                                      (~markOopDesc::age_mask_in_place), 0);
    // region->in(3) is set to fast path - the object is biased to the current thread.
    mem_phi->init_req(3, mem);


    // Mark word does NOT match the value (thread | Klass::_prototype_header).


    // First, check biased pattern.
    // Get fast path - _prototype_header has the same biased lock pattern.
    ctrl =  opt_bits_test(not_biased_ctrl, fast_lock_region, 2, x_node,
                          markOopDesc::biased_lock_mask_in_place, 0, true);

    not_biased_ctrl = fast_lock_region->in(2); // Slow path
    // fast_lock_region->in(2) - the prototype header is no longer biased
    // and we have to revoke the bias on this object.
    // We are going to try to reset the mark of this object to the prototype
    // value and fall through to the CAS-based locking scheme.
    Node* adr = basic_plus_adr(obj, oopDesc::mark_offset_in_bytes());
    Node* cas = new (C, 5) StoreXConditionalNode(not_biased_ctrl, mem, adr,
                                                 proto_node, mark_node);
    transform_later(cas);
    Node* proj = transform_later( new (C, 1) SCMemProjNode(cas));
    fast_lock_mem_phi->init_req(2, proj);


    // Second, check epoch bits.
    Node* rebiased_region  = new (C, 3) RegionNode(3);
    Node* old_phi = new (C, 3) PhiNode( rebiased_region, TypeX_X);
    Node* new_phi = new (C, 3) PhiNode( rebiased_region, TypeX_X);

    // Get slow path - mark word does NOT match epoch bits.
    Node* epoch_ctrl =  opt_bits_test(ctrl, rebiased_region, 1, x_node,
                                      markOopDesc::epoch_mask_in_place, 0);
    // The epoch of the current bias is not valid, attempt to rebias the object
    // toward the current thread.
    rebiased_region->init_req(2, epoch_ctrl);
    old_phi->init_req(2, mark_node);
    new_phi->init_req(2, o_node);

    // rebiased_region->in(1) is set to fast path.
    // The epoch of the current bias is still valid but we know
    // nothing about the owner; it might be set or it might be clear.
    Node* cmask   = MakeConX(markOopDesc::biased_lock_mask_in_place |
                             markOopDesc::age_mask_in_place |
                             markOopDesc::epoch_mask_in_place);
    Node* old = transform_later(new (C, 3) AndXNode(mark_node, cmask));
    cast_thread = transform_later(new (C, 2) CastP2XNode(ctrl, thread));
    Node* new_mark = transform_later(new (C, 3) OrXNode(cast_thread, old));
    old_phi->init_req(1, old);
    new_phi->init_req(1, new_mark);

    transform_later(rebiased_region);
    transform_later(old_phi);
    transform_later(new_phi);

    // Try to acquire the bias of the object using an atomic operation.
    // If this fails we will go in to the runtime to revoke the object's bias.
    cas = new (C, 5) StoreXConditionalNode(rebiased_region, mem, adr,
                                           new_phi, old_phi);
    transform_later(cas);
    proj = transform_later( new (C, 1) SCMemProjNode(cas));

    // Get slow path - Failed to CAS.
    not_biased_ctrl = opt_bits_test(rebiased_region, region, 4, cas, 0, 0);
    mem_phi->init_req(4, proj);
    // region->in(4) is set to fast path - the object is rebiased to the current thread.

    // Failed to CAS.
    slow_path  = new (C, 3) RegionNode(3);
    Node *slow_mem = new (C, 3) PhiNode( slow_path, Type::MEMORY, TypeRawPtr::BOTTOM);

    slow_path->init_req(1, not_biased_ctrl); // Capture slow-control
    slow_mem->init_req(1, proj);

    // Call CAS-based locking scheme (FastLock node).

    transform_later(fast_lock_region);
    transform_later(fast_lock_mem_phi);

    // Get slow path - FastLock failed to lock the object.
    ctrl = opt_bits_test(fast_lock_region, region, 2, flock, 0, 0);
    mem_phi->init_req(2, fast_lock_mem_phi);
    // region->in(2) is set to fast path - the object is locked to the current thread.

    slow_path->init_req(2, ctrl); // Capture slow-control
    slow_mem->init_req(2, fast_lock_mem_phi);

    transform_later(slow_path);
    transform_later(slow_mem);
    // Reset lock's memory edge.
    lock->set_req(TypeFunc::Memory, slow_mem);

  } else {
    region  = new (C, 3) RegionNode(3);
    // create a Phi for the memory state
    mem_phi = new (C, 3) PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);

    // Optimize test; set region slot 2
    slow_path = opt_bits_test(ctrl, region, 2, flock, 0, 0);
    mem_phi->init_req(2, mem);
  }

  // Make slow path call
  CallNode *call = make_slow_call( (CallNode *) lock, OptoRuntime::complete_monitor_enter_Type(), OptoRuntime::complete_monitor_locking_Java(), NULL, slow_path, obj, box );

  extract_call_projections(call);

  // Slow path can only throw asynchronous exceptions, which are always
  // de-opted.  So the compiler thinks the slow-call can never throw an
  // exception.  If it DOES throw an exception we would need the debug
  // info removed first (since if it throws there is no monitor).
  assert ( _ioproj_fallthrough == NULL && _ioproj_catchall == NULL &&
           _memproj_catchall == NULL && _catchallcatchproj == NULL, "Unexpected projection from Lock");

  // Capture slow path
  // disconnect fall-through projection from call and create a new one
  // hook up users of fall-through projection to region
  Node *slow_ctrl = _fallthroughproj->clone();
  transform_later(slow_ctrl);
  _igvn.hash_delete(_fallthroughproj);
  _fallthroughproj->disconnect_inputs(NULL);
  region->init_req(1, slow_ctrl);
  // region inputs are now complete
  transform_later(region);
  _igvn.replace_node(_fallthroughproj, region);

  Node *memproj = transform_later( new(C, 1) ProjNode(call, TypeFunc::Memory) );
  mem_phi->init_req(1, memproj );
  transform_later(mem_phi);
  _igvn.replace_node(_memproj_fallthrough, mem_phi);
}

//------------------------------expand_unlock_node----------------------
void PhaseMacroExpand::expand_unlock_node(UnlockNode *unlock) {

  Node* ctrl = unlock->in(TypeFunc::Control);
  Node* mem = unlock->in(TypeFunc::Memory);
  Node* obj = unlock->obj_node();
  Node* box = unlock->box_node();

  // No need for a null check on unlock

  // Make the merge point
  Node *region;
  Node *mem_phi;

  if (UseOptoBiasInlining) {
    // Check for biased locking unlock case, which is a no-op.
    // See the full description in MacroAssembler::biased_locking_exit().
    region  = new (C, 4) RegionNode(4);
    // create a Phi for the memory state
    mem_phi = new (C, 4) PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);
    mem_phi->init_req(3, mem);

    Node* mark_node = make_load(ctrl, mem, obj, oopDesc::mark_offset_in_bytes(), TypeX_X, TypeX_X->basic_type());
    ctrl = opt_bits_test(ctrl, region, 3, mark_node,
                         markOopDesc::biased_lock_mask_in_place,
                         markOopDesc::biased_lock_pattern);
  } else {
    region  = new (C, 3) RegionNode(3);
    // create a Phi for the memory state
    mem_phi = new (C, 3) PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);
  }

  FastUnlockNode *funlock = new (C, 3) FastUnlockNode( ctrl, obj, box );
  funlock = transform_later( funlock )->as_FastUnlock();
  // Optimize test; set region slot 2
  Node *slow_path = opt_bits_test(ctrl, region, 2, funlock, 0, 0);

  CallNode *call = make_slow_call( (CallNode *) unlock, OptoRuntime::complete_monitor_exit_Type(), CAST_FROM_FN_PTR(address, SharedRuntime::complete_monitor_unlocking_C), "complete_monitor_unlocking_C", slow_path, obj, box );

  extract_call_projections(call);

  assert ( _ioproj_fallthrough == NULL && _ioproj_catchall == NULL &&
           _memproj_catchall == NULL && _catchallcatchproj == NULL, "Unexpected projection from Lock");

  // No exceptions for unlocking
  // Capture slow path
  // disconnect fall-through projection from call and create a new one
  // hook up users of fall-through projection to region
  Node *slow_ctrl = _fallthroughproj->clone();
  transform_later(slow_ctrl);
  _igvn.hash_delete(_fallthroughproj);
  _fallthroughproj->disconnect_inputs(NULL);
  region->init_req(1, slow_ctrl);
  // region inputs are now complete
  transform_later(region);
  _igvn.replace_node(_fallthroughproj, region);

  Node *memproj = transform_later( new(C, 1) ProjNode(call, TypeFunc::Memory) );
  mem_phi->init_req(1, memproj );
  mem_phi->init_req(2, mem);
  transform_later(mem_phi);
  _igvn.replace_node(_memproj_fallthrough, mem_phi);
}

//------------------------------expand_macro_nodes----------------------
//  Returns true if a failure occurred.
bool PhaseMacroExpand::expand_macro_nodes() {
  if (C->macro_count() == 0)
    return false;
  // First, attempt to eliminate locks
  bool progress = true;
  while (progress) {
    progress = false;
    for (int i = C->macro_count(); i > 0; i--) {
      Node * n = C->macro_node(i-1);
      bool success = false;
      debug_only(int old_macro_count = C->macro_count(););
      if (n->is_AbstractLock()) {
        success = eliminate_locking_node(n->as_AbstractLock());
      } else if (n->Opcode() == Op_Opaque1 || n->Opcode() == Op_Opaque2) {
        _igvn.replace_node(n, n->in(1));
        success = true;
      }
      assert(success == (C->macro_count() < old_macro_count), "elimination reduces macro count");
      progress = progress || success;
    }
  }
  // Next, attempt to eliminate allocations
  progress = true;
  while (progress) {
    progress = false;
    for (int i = C->macro_count(); i > 0; i--) {
      Node * n = C->macro_node(i-1);
      bool success = false;
      debug_only(int old_macro_count = C->macro_count(););
      switch (n->class_id()) {
      case Node::Class_Allocate:
      case Node::Class_AllocateArray:
        success = eliminate_allocate_node(n->as_Allocate());
        break;
      case Node::Class_Lock:
      case Node::Class_Unlock:
        assert(!n->as_AbstractLock()->is_eliminated(), "sanity");
        break;
      default:
        assert(false, "unknown node type in macro list");
      }
      assert(success == (C->macro_count() < old_macro_count), "elimination reduces macro count");
      progress = progress || success;
    }
  }
  // Make sure expansion will not cause node limit to be exceeded.
  // Worst case is a macro node gets expanded into about 50 nodes.
  // Allow 50% more for optimization.
  if (C->check_node_count(C->macro_count() * 75, "out of nodes before macro expansion" ) )
    return true;

  // expand "macro" nodes
  // nodes are removed from the macro list as they are processed
  while (C->macro_count() > 0) {
    int macro_count = C->macro_count();
    Node * n = C->macro_node(macro_count-1);
    assert(n->is_macro(), "only macro nodes expected here");
    if (_igvn.type(n) == Type::TOP || n->in(0)->is_top() ) {
      // node is unreachable, so don't try to expand it
      C->remove_macro_node(n);
      continue;
    }
    switch (n->class_id()) {
    case Node::Class_Allocate:
      expand_allocate(n->as_Allocate());
      break;
    case Node::Class_AllocateArray:
      expand_allocate_array(n->as_AllocateArray());
      break;
    case Node::Class_Lock:
      expand_lock_node(n->as_Lock());
      break;
    case Node::Class_Unlock:
      expand_unlock_node(n->as_Unlock());
      break;
    default:
      assert(false, "unknown node type in macro list");
    }
    assert(C->macro_count() < macro_count, "must have deleted a node from macro list");
    if (C->failing())  return true;
  }

  _igvn.set_delay_transform(false);
  _igvn.optimize();
  return false;
}

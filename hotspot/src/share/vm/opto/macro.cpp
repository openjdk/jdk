/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
    if (old_in->is_SafePointScalarObject()) {
      SafePointScalarObjectNode* old_sosn = old_in->as_SafePointScalarObject();
      uint old_unique = C->unique();
      Node* new_in = old_sosn->clone(jvms_adj, sosn_map);
      if (old_unique != C->unique()) {
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

Node* PhaseMacroExpand::opt_iff(Node* region, Node* iff) {
  IfNode *opt_iff = transform_later(iff)->as_If();

  // Fast path taken; set region slot 2
  Node *fast_taken = transform_later( new (C, 1) IfFalseNode(opt_iff) );
  region->init_req(2,fast_taken); // Capture fast-control

  // Fast path not-taken, i.e. slow path
  Node *slow_taken = transform_later( new (C, 1) IfTrueNode(opt_iff) );
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
  const TypePtr* adr_type = TypeRawPtr::BOTTOM;
  Node* value = LoadNode::make(C, ctl, mem, adr, adr_type, value_type, bt);
  transform_later(value);
  return value;
}


Node* PhaseMacroExpand::make_store(Node* ctl, Node* mem, Node* base, int offset, Node* value, BasicType bt) {
  Node* adr = basic_plus_adr(base, offset);
  mem = StoreNode::make(C, ctl, mem, adr, NULL, value, bt);
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

  Node* eden_top_adr;
  Node* eden_end_adr;
  set_eden_pointers(eden_top_adr, eden_end_adr);

  uint raw_idx = C->get_alias_index(TypeRawPtr::BOTTOM);
  assert(ctrl != NULL, "must have control");

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
  Node* eden_end = make_load(ctrl, mem, eden_end_adr, 0, TypeRawPtr::BOTTOM, T_ADDRESS);

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

  if (DTraceAllocProbes) {
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

    if (ExtendedDTraceProbes) {
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

  mem = result_phi_rawmem;

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
    _igvn.hash_delete(_fallthroughcatchproj);
    _igvn.subsume_node(_fallthroughcatchproj, result_region);
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
    _igvn.hash_delete(_resproj);
    _igvn.subsume_node(_resproj, result_phi_rawoop);
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
    header_size = sizeof(arrayOopDesc);
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
   if( UseTLAB && AllocatePrefetchStyle == 2 ) {
      // Generate prefetch allocation with watermark check.
      // As an allocation hits the watermark, we will prefetch starting
      // at a "distance" away from watermark.
      enum { fall_in_path = 1, pf_path = 2 };

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
void PhaseMacroExpand::eliminate_locking_node(AbstractLockNode *alock) {
  Node* mem = alock->in(TypeFunc::Memory);

  // The memory projection from a lock/unlock is RawMem
  // The input to a Lock is merged memory, so extract its RawMem input
  // (unless the MergeMem has been optimized away.)
  if (alock->is_Lock()) {
    if (mem->is_MergeMem())
      mem = mem->as_MergeMem()->in(Compile::AliasIdxRaw);
  }

  extract_call_projections(alock);
  // There are 2 projections from the lock.  The lock node will
  // be deleted when its last use is subsumed below.
  assert(alock->outcnt() == 2 && _fallthroughproj != NULL &&
          _memproj_fallthrough != NULL, "Unexpected projections from Lock/Unlock");
  _igvn.hash_delete(_fallthroughproj);
  _igvn.subsume_node(_fallthroughproj, alock->in(TypeFunc::Control));
  _igvn.hash_delete(_memproj_fallthrough);
  _igvn.subsume_node(_memproj_fallthrough, mem);
  return;
}


//------------------------------expand_lock_node----------------------
void PhaseMacroExpand::expand_lock_node(LockNode *lock) {

  Node* ctrl = lock->in(TypeFunc::Control);
  Node* mem = lock->in(TypeFunc::Memory);
  Node* obj = lock->obj_node();
  Node* box = lock->box_node();
  Node *flock = lock->fastlock_node();

  if (lock->is_eliminated()) {
    eliminate_locking_node(lock);
    return;
  }

  // Make the merge point
  Node *region = new (C, 3) RegionNode(3);

  Node *bol = transform_later(new (C, 2) BoolNode(flock,BoolTest::ne));
  Node *iff = new (C, 2) IfNode( ctrl, bol, PROB_MIN, COUNT_UNKNOWN );
  // Optimize test; set region slot 2
  Node *slow_path = opt_iff(region,iff);

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
  _igvn.subsume_node(_fallthroughproj, region);

  // create a Phi for the memory state
  Node *mem_phi = new (C, 3) PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);
  Node *memproj = transform_later( new (C, 1) ProjNode(call, TypeFunc::Memory) );
  mem_phi->init_req(1, memproj );
  mem_phi->init_req(2, mem);
  transform_later(mem_phi);
    _igvn.hash_delete(_memproj_fallthrough);
  _igvn.subsume_node(_memproj_fallthrough, mem_phi);


}

//------------------------------expand_unlock_node----------------------
void PhaseMacroExpand::expand_unlock_node(UnlockNode *unlock) {

  Node *ctrl = unlock->in(TypeFunc::Control);
  Node* mem = unlock->in(TypeFunc::Memory);
  Node* obj = unlock->obj_node();
  Node* box = unlock->box_node();


  if (unlock->is_eliminated()) {
    eliminate_locking_node(unlock);
    return;
  }

  // No need for a null check on unlock

  // Make the merge point
  RegionNode *region = new (C, 3) RegionNode(3);

  FastUnlockNode *funlock = new (C, 3) FastUnlockNode( ctrl, obj, box );
  funlock = transform_later( funlock )->as_FastUnlock();
  Node *bol = transform_later(new (C, 2) BoolNode(funlock,BoolTest::ne));
  Node *iff = new (C, 2) IfNode( ctrl, bol, PROB_MIN, COUNT_UNKNOWN );
  // Optimize test; set region slot 2
  Node *slow_path = opt_iff(region,iff);

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
  _igvn.subsume_node(_fallthroughproj, region);

  // create a Phi for the memory state
  Node *mem_phi = new (C, 3) PhiNode( region, Type::MEMORY, TypeRawPtr::BOTTOM);
  Node *memproj = transform_later( new(C, 1) ProjNode(call, TypeFunc::Memory) );
  mem_phi->init_req(1, memproj );
  mem_phi->init_req(2, mem);
  transform_later(mem_phi);
    _igvn.hash_delete(_memproj_fallthrough);
  _igvn.subsume_node(_memproj_fallthrough, mem_phi);


}

//------------------------------expand_macro_nodes----------------------
//  Returns true if a failure occurred.
bool PhaseMacroExpand::expand_macro_nodes() {
  if (C->macro_count() == 0)
    return false;
  // Make sure expansion will not cause node limit to be exceeded.  Worst case is a
  // macro node gets expanded into about 50 nodes.  Allow 50% more for optimization
  if (C->check_node_count(C->macro_count() * 75, "out of nodes before macro expansion" ) )
    return true;
  // expand "macro" nodes
  // nodes are removed from the macro list as they are processed
  while (C->macro_count() > 0) {
    Node * n = C->macro_node(0);
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
    if (C->failing())  return true;
  }
  _igvn.optimize();
  return false;
}

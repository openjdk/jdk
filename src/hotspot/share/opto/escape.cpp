/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "ci/bcEscapeAnalyzer.hpp"
#include "compiler/compileLog.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "libadt/vectset.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "opto/c2compiler.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/compile.hpp"
#include "opto/escape.hpp"
#include "opto/macro.hpp"
#include "opto/locknode.hpp"
#include "opto/phaseX.hpp"
#include "opto/movenode.hpp"
#include "opto/rootnode.hpp"
#include "utilities/macros.hpp"

ConnectionGraph::ConnectionGraph(Compile * C, PhaseIterGVN *igvn, int invocation) :
  // If ReduceAllocationMerges is enabled we might call split_through_phi during
  // split_unique_types and that will create additional nodes that need to be
  // pushed to the ConnectionGraph. The code below bumps the initial capacity of
  // _nodes by 10% to account for these additional nodes. If capacity is exceeded
  // the array will be reallocated.
  _nodes(C->comp_arena(), C->do_reduce_allocation_merges() ? C->unique()*1.10 : C->unique(), C->unique(), nullptr),
  _in_worklist(C->comp_arena()),
  _next_pidx(0),
  _collecting(true),
  _verify(false),
  _compile(C),
  _igvn(igvn),
  _invocation(invocation),
  _build_iterations(0),
  _build_time(0.),
  _node_map(C->comp_arena()) {
  // Add unknown java object.
  add_java_object(C->top(), PointsToNode::GlobalEscape);
  phantom_obj = ptnode_adr(C->top()->_idx)->as_JavaObject();
  set_not_scalar_replaceable(phantom_obj NOT_PRODUCT(COMMA "Phantom object"));
  // Add ConP and ConN null oop nodes
  Node* oop_null = igvn->zerocon(T_OBJECT);
  assert(oop_null->_idx < nodes_size(), "should be created already");
  add_java_object(oop_null, PointsToNode::NoEscape);
  null_obj = ptnode_adr(oop_null->_idx)->as_JavaObject();
  set_not_scalar_replaceable(null_obj NOT_PRODUCT(COMMA "Null object"));
  if (UseCompressedOops) {
    Node* noop_null = igvn->zerocon(T_NARROWOOP);
    assert(noop_null->_idx < nodes_size(), "should be created already");
    map_ideal_node(noop_null, null_obj);
  }
}

bool ConnectionGraph::has_candidates(Compile *C) {
  // EA brings benefits only when the code has allocations and/or locks which
  // are represented by ideal Macro nodes.
  int cnt = C->macro_count();
  for (int i = 0; i < cnt; i++) {
    Node *n = C->macro_node(i);
    if (n->is_Allocate()) {
      return true;
    }
    if (n->is_Lock()) {
      Node* obj = n->as_Lock()->obj_node()->uncast();
      if (!(obj->is_Parm() || obj->is_Con())) {
        return true;
      }
    }
    if (n->is_CallStaticJava() &&
        n->as_CallStaticJava()->is_boxing_method()) {
      return true;
    }
  }
  return false;
}

void ConnectionGraph::do_analysis(Compile *C, PhaseIterGVN *igvn) {
  Compile::TracePhase tp("escapeAnalysis", &Phase::timers[Phase::_t_escapeAnalysis]);
  ResourceMark rm;

  // Add ConP and ConN null oop nodes before ConnectionGraph construction
  // to create space for them in ConnectionGraph::_nodes[].
  Node* oop_null = igvn->zerocon(T_OBJECT);
  Node* noop_null = igvn->zerocon(T_NARROWOOP);
  int invocation = 0;
  if (C->congraph() != nullptr) {
    invocation = C->congraph()->_invocation + 1;
  }
  ConnectionGraph* congraph = new(C->comp_arena()) ConnectionGraph(C, igvn, invocation);
  // Perform escape analysis
  if (congraph->compute_escape()) {
    // There are non escaping objects.
    C->set_congraph(congraph);
  }
  // Cleanup.
  if (oop_null->outcnt() == 0) {
    igvn->hash_delete(oop_null);
  }
  if (noop_null->outcnt() == 0) {
    igvn->hash_delete(noop_null);
  }
}

bool ConnectionGraph::compute_escape() {
  Compile* C = _compile;
  PhaseGVN* igvn = _igvn;

  // Worklists used by EA.
  Unique_Node_List delayed_worklist;
  Unique_Node_List reducible_merges;
  GrowableArray<Node*> alloc_worklist;
  GrowableArray<Node*> ptr_cmp_worklist;
  GrowableArray<MemBarStoreStoreNode*> storestore_worklist;
  GrowableArray<ArrayCopyNode*>  arraycopy_worklist;
  GrowableArray<PointsToNode*>   ptnodes_worklist;
  GrowableArray<JavaObjectNode*> java_objects_worklist;
  GrowableArray<JavaObjectNode*> non_escaped_allocs_worklist;
  GrowableArray<FieldNode*>      oop_fields_worklist;
  GrowableArray<SafePointNode*>  sfn_worklist;
  GrowableArray<MergeMemNode*>   mergemem_worklist;
  DEBUG_ONLY( GrowableArray<Node*> addp_worklist; )

  { Compile::TracePhase tp("connectionGraph", &Phase::timers[Phase::_t_connectionGraph]);

  // 1. Populate Connection Graph (CG) with PointsTo nodes.
  ideal_nodes.map(C->live_nodes(), nullptr);  // preallocate space
  // Initialize worklist
  if (C->root() != nullptr) {
    ideal_nodes.push(C->root());
  }
  // Processed ideal nodes are unique on ideal_nodes list
  // but several ideal nodes are mapped to the phantom_obj.
  // To avoid duplicated entries on the following worklists
  // add the phantom_obj only once to them.
  ptnodes_worklist.append(phantom_obj);
  java_objects_worklist.append(phantom_obj);
  for( uint next = 0; next < ideal_nodes.size(); ++next ) {
    Node* n = ideal_nodes.at(next);
    // Create PointsTo nodes and add them to Connection Graph. Called
    // only once per ideal node since ideal_nodes is Unique_Node list.
    add_node_to_connection_graph(n, &delayed_worklist);
    PointsToNode* ptn = ptnode_adr(n->_idx);
    if (ptn != nullptr && ptn != phantom_obj) {
      ptnodes_worklist.append(ptn);
      if (ptn->is_JavaObject()) {
        java_objects_worklist.append(ptn->as_JavaObject());
        if ((n->is_Allocate() || n->is_CallStaticJava()) &&
            (ptn->escape_state() < PointsToNode::GlobalEscape)) {
          // Only allocations and java static calls results are interesting.
          non_escaped_allocs_worklist.append(ptn->as_JavaObject());
        }
      } else if (ptn->is_Field() && ptn->as_Field()->is_oop()) {
        oop_fields_worklist.append(ptn->as_Field());
      }
    }
    // Collect some interesting nodes for further use.
    switch (n->Opcode()) {
      case Op_MergeMem:
        // Collect all MergeMem nodes to add memory slices for
        // scalar replaceable objects in split_unique_types().
        mergemem_worklist.append(n->as_MergeMem());
        break;
      case Op_CmpP:
      case Op_CmpN:
        // Collect compare pointers nodes.
        if (OptimizePtrCompare) {
          ptr_cmp_worklist.append(n);
        }
        break;
      case Op_MemBarStoreStore:
        // Collect all MemBarStoreStore nodes so that depending on the
        // escape status of the associated Allocate node some of them
        // may be eliminated.
        storestore_worklist.append(n->as_MemBarStoreStore());
        break;
      case Op_MemBarRelease:
        if (n->req() > MemBarNode::Precedent) {
          record_for_optimizer(n);
        }
        break;
#ifdef ASSERT
      case Op_AddP:
        // Collect address nodes for graph verification.
        addp_worklist.append(n);
        break;
#endif
      case Op_ArrayCopy:
        // Keep a list of ArrayCopy nodes so if one of its input is non
        // escaping, we can record a unique type
        arraycopy_worklist.append(n->as_ArrayCopy());
        break;
      default:
        // not interested now, ignore...
        break;
    }
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* m = n->fast_out(i);   // Get user
      ideal_nodes.push(m);
    }
    if (n->is_SafePoint()) {
      sfn_worklist.append(n->as_SafePoint());
    }
  }

#ifndef PRODUCT
  if (_compile->directive()->TraceEscapeAnalysisOption) {
    tty->print("+++++ Initial worklist for ");
    _compile->method()->print_name();
    tty->print_cr(" (ea_inv=%d)", _invocation);
    for (int i = 0; i < ptnodes_worklist.length(); i++) {
      PointsToNode* ptn = ptnodes_worklist.at(i);
      ptn->dump();
    }
    tty->print_cr("+++++ Calculating escape states and scalar replaceability");
  }
#endif

  if (non_escaped_allocs_worklist.length() == 0) {
    _collecting = false;
    NOT_PRODUCT(escape_state_statistics(java_objects_worklist);)
    return false; // Nothing to do.
  }
  // Add final simple edges to graph.
  while(delayed_worklist.size() > 0) {
    Node* n = delayed_worklist.pop();
    add_final_edges(n);
  }

#ifdef ASSERT
  if (VerifyConnectionGraph) {
    // Verify that no new simple edges could be created and all
    // local vars has edges.
    _verify = true;
    int ptnodes_length = ptnodes_worklist.length();
    for (int next = 0; next < ptnodes_length; ++next) {
      PointsToNode* ptn = ptnodes_worklist.at(next);
      add_final_edges(ptn->ideal_node());
      if (ptn->is_LocalVar() && ptn->edge_count() == 0) {
        ptn->dump();
        assert(ptn->as_LocalVar()->edge_count() > 0, "sanity");
      }
    }
    _verify = false;
  }
#endif
  // Bytecode analyzer BCEscapeAnalyzer, used for Call nodes
  // processing, calls to CI to resolve symbols (types, fields, methods)
  // referenced in bytecode. During symbol resolution VM may throw
  // an exception which CI cleans and converts to compilation failure.
  if (C->failing()) {
    NOT_PRODUCT(escape_state_statistics(java_objects_worklist);)
    return false;
  }

  // 2. Finish Graph construction by propagating references to all
  //    java objects through graph.
  if (!complete_connection_graph(ptnodes_worklist, non_escaped_allocs_worklist,
                                 java_objects_worklist, oop_fields_worklist)) {
    // All objects escaped or hit time or iterations limits.
    _collecting = false;
    NOT_PRODUCT(escape_state_statistics(java_objects_worklist);)
    return false;
  }

  // 3. Adjust scalar_replaceable state of nonescaping objects and push
  //    scalar replaceable allocations on alloc_worklist for processing
  //    in split_unique_types().
  GrowableArray<JavaObjectNode*> jobj_worklist;
  int non_escaped_length = non_escaped_allocs_worklist.length();
  bool found_nsr_alloc = false;
  for (int next = 0; next < non_escaped_length; next++) {
    JavaObjectNode* ptn = non_escaped_allocs_worklist.at(next);
    bool noescape = (ptn->escape_state() == PointsToNode::NoEscape);
    Node* n = ptn->ideal_node();
    if (n->is_Allocate()) {
      n->as_Allocate()->_is_non_escaping = noescape;
    }
    if (noescape && ptn->scalar_replaceable()) {
      adjust_scalar_replaceable_state(ptn, reducible_merges);
      if (ptn->scalar_replaceable()) {
        jobj_worklist.push(ptn);
      } else {
        found_nsr_alloc = true;
      }
    }
  }

  // Propagate NSR (Not Scalar Replaceable) state.
  if (found_nsr_alloc) {
    find_scalar_replaceable_allocs(jobj_worklist);
  }

  // alloc_worklist will be processed in reverse push order.
  // Therefore the reducible Phis will be processed for last and that's what we
  // want because by then the scalarizable inputs of the merge will already have
  // an unique instance type.
  for (uint i = 0; i < reducible_merges.size(); i++ ) {
    Node* n = reducible_merges.at(i);
    alloc_worklist.append(n);
  }

  for (int next = 0; next < jobj_worklist.length(); ++next) {
    JavaObjectNode* jobj = jobj_worklist.at(next);
    if (jobj->scalar_replaceable()) {
      alloc_worklist.append(jobj->ideal_node());
    }
  }

#ifdef ASSERT
  if (VerifyConnectionGraph) {
    // Verify that graph is complete - no new edges could be added or needed.
    verify_connection_graph(ptnodes_worklist, non_escaped_allocs_worklist,
                            java_objects_worklist, addp_worklist);
  }
  assert(C->unique() == nodes_size(), "no new ideal nodes should be added during ConnectionGraph build");
  assert(null_obj->escape_state() == PointsToNode::NoEscape &&
         null_obj->edge_count() == 0 &&
         !null_obj->arraycopy_src() &&
         !null_obj->arraycopy_dst(), "sanity");
#endif

  _collecting = false;

  } // TracePhase t3("connectionGraph")

  // 4. Optimize ideal graph based on EA information.
  bool has_non_escaping_obj = (non_escaped_allocs_worklist.length() > 0);
  if (has_non_escaping_obj) {
    optimize_ideal_graph(ptr_cmp_worklist, storestore_worklist);
  }

#ifndef PRODUCT
  if (PrintEscapeAnalysis) {
    dump(ptnodes_worklist); // Dump ConnectionGraph
  }
#endif

#ifdef ASSERT
  if (VerifyConnectionGraph) {
    int alloc_length = alloc_worklist.length();
    for (int next = 0; next < alloc_length; ++next) {
      Node* n = alloc_worklist.at(next);
      PointsToNode* ptn = ptnode_adr(n->_idx);
      assert(ptn->escape_state() == PointsToNode::NoEscape && ptn->scalar_replaceable(), "sanity");
    }
  }

  if (VerifyReduceAllocationMerges) {
    for (uint i = 0; i < reducible_merges.size(); i++ ) {
      Node* n = reducible_merges.at(i);
      if (!can_reduce_phi(n->as_Phi())) {
        TraceReduceAllocationMerges = true;
        n->dump(2);
        n->dump(-2);
        assert(can_reduce_phi(n->as_Phi()), "Sanity: previous reducible Phi is no longer reducible before SUT.");
      }
    }
  }
#endif

  // 5. Separate memory graph for scalar replaceable allcations.
  bool has_scalar_replaceable_candidates = (alloc_worklist.length() > 0);
  if (has_scalar_replaceable_candidates && EliminateAllocations) {
    assert(C->do_aliasing(), "Aliasing should be enabled");
    // Now use the escape information to create unique types for
    // scalar replaceable objects.
    split_unique_types(alloc_worklist, arraycopy_worklist, mergemem_worklist, reducible_merges);
    if (C->failing()) {
      NOT_PRODUCT(escape_state_statistics(java_objects_worklist);)
      return false;
    }
    C->print_method(PHASE_AFTER_EA, 2);

#ifdef ASSERT
  } else if (Verbose && (PrintEscapeAnalysis || PrintEliminateAllocations)) {
    tty->print("=== No allocations eliminated for ");
    C->method()->print_short_name();
    if (!EliminateAllocations) {
      tty->print(" since EliminateAllocations is off ===");
    } else if(!has_scalar_replaceable_candidates) {
      tty->print(" since there are no scalar replaceable candidates ===");
    }
    tty->cr();
#endif
  }

  // 6. Remove reducible allocation merges from ideal graph
  if (reducible_merges.size() > 0) {
    bool delay = _igvn->delay_transform();
    _igvn->set_delay_transform(true);
    for (uint i = 0; i < reducible_merges.size(); i++ ) {
      Node* n = reducible_merges.at(i);
      reduce_phi(n->as_Phi());
      if (C->failing()) {
        NOT_PRODUCT(escape_state_statistics(java_objects_worklist);)
        return false;
      }
    }
    _igvn->set_delay_transform(delay);
  }

  // Annotate at safepoints if they have <= ArgEscape objects in their scope and at
  // java calls if they pass ArgEscape objects as parameters.
  if (has_non_escaping_obj &&
      (C->env()->should_retain_local_variables() ||
       C->env()->jvmti_can_get_owned_monitor_info() ||
       C->env()->jvmti_can_walk_any_space() ||
       DeoptimizeObjectsALot)) {
    int sfn_length = sfn_worklist.length();
    for (int next = 0; next < sfn_length; next++) {
      SafePointNode* sfn = sfn_worklist.at(next);
      sfn->set_has_ea_local_in_scope(has_ea_local_in_scope(sfn));
      if (sfn->is_CallJava()) {
        CallJavaNode* call = sfn->as_CallJava();
        call->set_arg_escape(has_arg_escape(call));
      }
    }
  }

  NOT_PRODUCT(escape_state_statistics(java_objects_worklist);)
  return has_non_escaping_obj;
}

// Check if it's profitable to reduce the Phi passed as parameter.  Returns true
// if at least one scalar replaceable allocation participates in the merge and
// no input to the Phi is nullable.
bool ConnectionGraph::can_reduce_phi_check_inputs(PhiNode* ophi) const {
  // Check if there is a scalar replaceable allocate in the Phi
  bool found_sr_allocate = false;

  for (uint i = 1; i < ophi->req(); i++) {
    // Right now we can't restore a "null" pointer during deoptimization
    const Type* inp_t = _igvn->type(ophi->in(i));
    if (inp_t == nullptr || inp_t->make_oopptr() == nullptr || inp_t->make_oopptr()->maybe_null()) {
      NOT_PRODUCT(if (TraceReduceAllocationMerges) tty->print_cr("Can NOT reduce Phi %d on invocation %d. Input %d is nullable.", ophi->_idx, _invocation, i);)
      return false;
    }

    // We are looking for at least one SR object in the merge
    JavaObjectNode* ptn = unique_java_object(ophi->in(i));
    if (ptn != nullptr && ptn->scalar_replaceable()) {
      assert(ptn->ideal_node() != nullptr && ptn->ideal_node()->is_Allocate(), "sanity");
      AllocateNode* alloc = ptn->ideal_node()->as_Allocate();

      if (PhaseMacroExpand::can_eliminate_allocation(_igvn, alloc, nullptr)) {
        found_sr_allocate = true;
      } else {
        ptn->set_scalar_replaceable(false);
      }
    }
  }

  NOT_PRODUCT(if (TraceReduceAllocationMerges && !found_sr_allocate) tty->print_cr("Can NOT reduce Phi %d on invocation %d. No SR Allocate as input.", ophi->_idx, _invocation);)
  return found_sr_allocate;
}

// Check if we are able to untangle the merge. Right now we only reduce Phis
// which are only used as debug information.
bool ConnectionGraph::can_reduce_phi_check_users(PhiNode* ophi) const {
  for (DUIterator_Fast imax, i = ophi->fast_outs(imax); i < imax; i++) {
    Node* use = ophi->fast_out(i);

    if (use->is_SafePoint()) {
      if (use->is_Call() && use->as_Call()->has_non_debug_use(ophi)) {
        NOT_PRODUCT(if (TraceReduceAllocationMerges) tty->print_cr("Can NOT reduce Phi %d on invocation %d. Call has non_debug_use().", ophi->_idx, _invocation);)
        return false;
      }
    } else if (use->is_AddP()) {
      Node* addp = use;
      for (DUIterator_Fast jmax, j = addp->fast_outs(jmax); j < jmax; j++) {
        Node* use_use = addp->fast_out(j);
        if (!use_use->is_Load() || !use_use->as_Load()->can_split_through_phi_base(_igvn)) {
          NOT_PRODUCT(if (TraceReduceAllocationMerges) tty->print_cr("Can NOT reduce Phi %d on invocation %d. AddP user isn't a [splittable] Load(): %s", ophi->_idx, _invocation, use_use->Name());)
          return false;
        }
      }
    } else {
      NOT_PRODUCT(if (TraceReduceAllocationMerges) tty->print_cr("Can NOT reduce Phi %d on invocation %d. One of the uses is: %d %s", ophi->_idx, _invocation, use->_idx, use->Name());)
      return false;
    }
  }

  return true;
}

// Returns true if: 1) It's profitable to reduce the merge, and 2) The Phi is
// only used in some certain code shapes. Check comments in
// 'can_reduce_phi_inputs' and 'can_reduce_phi_users' for more
// details.
bool ConnectionGraph::can_reduce_phi(PhiNode* ophi) const {
  // If there was an error attempting to reduce allocation merges for this
  // method we might have disabled the compilation and be retrying with RAM
  // disabled.
  // If EliminateAllocations is False, there is no point in reducing merges.
  if (!_compile->do_reduce_allocation_merges()) {
    return false;
  }

  const Type* phi_t = _igvn->type(ophi);
  if (phi_t == nullptr || phi_t->make_ptr() == nullptr ||
                          phi_t->make_ptr()->isa_instptr() == nullptr ||
                          !phi_t->make_ptr()->isa_instptr()->klass_is_exact()) {
    NOT_PRODUCT(if (TraceReduceAllocationMerges) { tty->print_cr("Can NOT reduce Phi %d during invocation %d because it's nullable.", ophi->_idx, _invocation); })
    return false;
  }

  if (!can_reduce_phi_check_inputs(ophi) || !can_reduce_phi_check_users(ophi)) {
    return false;
  }

  NOT_PRODUCT(if (TraceReduceAllocationMerges) { tty->print_cr("Can reduce Phi %d during invocation %d: ", ophi->_idx, _invocation); })
  return true;
}

void ConnectionGraph::reduce_phi_on_field_access(PhiNode* ophi, GrowableArray<Node *>  &alloc_worklist) {
  // We'll pass this to 'split_through_phi' so that it'll do the split even
  // though the load doesn't have an unique instance type.
  bool ignore_missing_instance_id = true;

#ifdef ASSERT
  if (VerifyReduceAllocationMerges && !can_reduce_phi(ophi)) {
    TraceReduceAllocationMerges = true;
    ophi->dump(2);
    ophi->dump(-2);
    assert(can_reduce_phi(ophi), "Sanity: previous reducible Phi is no longer reducible inside reduce_phi_on_field_access.");
  }
#endif

  // Iterate over Phi outputs looking for an AddP
  for (int j = ophi->outcnt()-1; j >= 0;) {
    Node* previous_addp = ophi->raw_out(j);
    if (previous_addp->is_AddP()) {
      // All AddPs are present in the connection graph
      FieldNode* fn = ptnode_adr(previous_addp->_idx)->as_Field();

      // Iterate over AddP looking for a Load
      for (int k = previous_addp->outcnt()-1; k >= 0;) {
        Node* previous_load = previous_addp->raw_out(k);
        if (previous_load->is_Load()) {
          Node* data_phi = previous_load->as_Load()->split_through_phi(_igvn, ignore_missing_instance_id);
          _igvn->replace_node(previous_load, data_phi);
          assert(data_phi != nullptr, "Output of split_through_phi is null.");
          assert(data_phi != previous_load, "Output of split_through_phi is same as input.");
          assert(data_phi->is_Phi(), "Return of split_through_phi should be a Phi.");

          // Push the newly created AddP on alloc_worklist and patch
          // the connection graph. Note that the changes in the CG below
          // won't affect the ES of objects since the new nodes have the
          // same status as the old ones.
          for (uint i = 1; i < data_phi->req(); i++) {
            Node* new_load = data_phi->in(i);
            if (new_load->is_Load()) {
              Node* new_addp = new_load->in(MemNode::Address);
              Node* base = get_addp_base(new_addp);

              // The base might not be something that we can create an unique
              // type for. If that's the case we are done with that input.
              PointsToNode* jobj_ptn = unique_java_object(base);
              if (jobj_ptn == nullptr || !jobj_ptn->scalar_replaceable()) {
                continue;
              }

              // Push to alloc_worklist since the base has an unique_type
              alloc_worklist.append_if_missing(new_addp);

              // Now let's add the node to the connection graph
              _nodes.at_grow(new_addp->_idx, nullptr);
              add_field(new_addp, fn->escape_state(), fn->offset());
              add_base(ptnode_adr(new_addp->_idx)->as_Field(), ptnode_adr(base->_idx));

              // If the load doesn't load an object then it won't be
              // part of the connection graph
              PointsToNode* curr_load_ptn = ptnode_adr(previous_load->_idx);
              if (curr_load_ptn != nullptr) {
                _nodes.at_grow(new_load->_idx, nullptr);
                add_local_var(new_load, curr_load_ptn->escape_state());
                add_edge(ptnode_adr(new_load->_idx), ptnode_adr(new_addp->_idx)->as_Field());
              }
            }
          }
        }
        k = MIN2(--k, (int)previous_addp->outcnt()-1);
      }

      // Remove the old AddP from the processing list because it's dead now
      alloc_worklist.remove_if_existing(previous_addp);
      _igvn->remove_globally_dead_node(previous_addp);
    }
    j = MIN2(--j, (int)ophi->outcnt()-1);
  }

#ifdef ASSERT
  if (VerifyReduceAllocationMerges) {
    for (uint j = 0; j < ophi->outcnt(); j++) {
      Node* use = ophi->raw_out(j);
      if (!use->is_SafePoint()) {
        ophi->dump(2);
        ophi->dump(-2);
        assert(false, "Should be a SafePoint.");
      }
    }
  }
#endif
}

// This method will create a SafePointScalarObjectNode for each combination of
// scalar replaceable allocation in 'ophi' and SafePoint node in 'safepoints'.
// The method will create a SafePointScalarMERGEnode for each combination of
// 'ophi' and SafePoint node in 'safepoints'.
// Each SafePointScalarMergeNode created here may describe multiple scalar
// replaced objects - check detailed description in SafePointScalarMergeNode
// class header.
//
// This method will set entries in the Phi that are scalar replaceable to 'null'.
void ConnectionGraph::reduce_phi_on_safepoints(PhiNode* ophi, Unique_Node_List* safepoints) {
  Node* minus_one           = _igvn->register_new_node_with_optimizer(ConINode::make(-1));
  Node* selector            = _igvn->register_new_node_with_optimizer(PhiNode::make(ophi->region(), minus_one, TypeInt::INT));
  Node* null_ptr            = _igvn->makecon(TypePtr::NULL_PTR);
  const TypeOopPtr* merge_t = _igvn->type(ophi)->make_oopptr();
  uint number_of_sr_objects = 0;
  PhaseMacroExpand mexp(*_igvn);

  _igvn->hash_delete(ophi);

  // Fill in the 'selector' Phi. If index 'i' of the selector is:
  // -> a '-1' constant, the i'th input of the original Phi is NSR.
  // -> a 'x' constant >=0, the i'th input of of original Phi will be SR and the
  //    info about the scalarized object will be at index x of
  //    ObjectMergeValue::possible_objects
  for (uint i = 1; i < ophi->req(); i++) {
    Node* base          = ophi->in(i);
    JavaObjectNode* ptn = unique_java_object(base);

    if (ptn != nullptr && ptn->scalar_replaceable()) {
      Node* sr_obj_idx = _igvn->register_new_node_with_optimizer(ConINode::make(number_of_sr_objects));
      selector->set_req(i, sr_obj_idx);
      number_of_sr_objects++;
    }
  }

  // Update the debug information of all safepoints in turn
  for (uint spi = 0; spi < safepoints->size(); spi++) {
    SafePointNode* sfpt = safepoints->at(spi)->as_SafePoint();
    JVMState *jvms      = sfpt->jvms();
    uint merge_idx      = (sfpt->req() - jvms->scloff());
    int debug_start     = jvms->debug_start();

    SafePointScalarMergeNode* smerge = new SafePointScalarMergeNode(merge_t, merge_idx);
    smerge->init_req(0, _compile->root());
    _igvn->register_new_node_with_optimizer(smerge);

    // The next two inputs are:
    //  (1) A copy of the original pointer to NSR objects.
    //  (2) A selector, used to decide if we need to rematerialize an object
    //      or use the pointer to a NSR object.
    // See more details of these fields in the declaration of SafePointScalarMergeNode
    sfpt->add_req(ophi);
    sfpt->add_req(selector);

    for (uint i = 1; i < ophi->req(); i++) {
      Node* base          = ophi->in(i);
      JavaObjectNode* ptn = unique_java_object(base);

      // If the base is not scalar replaceable we don't need to register information about
      // it at this time.
      if (ptn == nullptr || !ptn->scalar_replaceable()) {
        continue;
      }

      AllocateNode* alloc = ptn->ideal_node()->as_Allocate();
      SafePointScalarObjectNode* sobj = mexp.create_scalarized_object_description(alloc, sfpt);
      if (sobj == nullptr) {
        _compile->record_failure(C2Compiler::retry_no_reduce_allocation_merges());
        return;
      }

      // Now make a pass over the debug information replacing any references
      // to the allocated object with "sobj"
      Node* ccpp = alloc->result_cast();
      sfpt->replace_edges_in_range(ccpp, sobj, debug_start, jvms->debug_end(), _igvn);

      // Register the scalarized object as a candidate for reallocation
      smerge->add_req(sobj);
    }

    // Replaces debug information references to "ophi" in "sfpt" with references to "smerge"
    sfpt->replace_edges_in_range(ophi, smerge, debug_start, jvms->debug_end(), _igvn);

    // The call to 'replace_edges_in_range' above might have removed the
    // reference to ophi that we need at _merge_pointer_idx. The line below make
    // sure the reference is maintained.
    sfpt->set_req(smerge->merge_pointer_idx(jvms), ophi);
    _igvn->_worklist.push(sfpt);
  }

  // Now we can change ophi since we don't need to know the types
  // of the input allocations anymore.
  const Type* new_t = merge_t->meet(TypePtr::NULL_PTR);
  Node* new_phi = _igvn->register_new_node_with_optimizer(PhiNode::make(ophi->region(), null_ptr, new_t));
  for (uint i = 1; i < ophi->req(); i++) {
    Node* base          = ophi->in(i);
    JavaObjectNode* ptn = unique_java_object(base);

    if (ptn != nullptr && ptn->scalar_replaceable()) {
      new_phi->set_req(i, null_ptr);
    } else {
      new_phi->set_req(i, ophi->in(i));
    }
  }

  _igvn->replace_node(ophi, new_phi);
  _igvn->hash_insert(ophi);
  _igvn->_worklist.push(ophi);
}

void ConnectionGraph::reduce_phi(PhiNode* ophi) {
  Unique_Node_List safepoints;

  for (uint i = 0; i < ophi->outcnt(); i++) {
    Node* use = ophi->raw_out(i);

    // All SafePoint nodes using the same Phi node use the same debug
    // information (regarding the Phi). Furthermore, reducing the Phi used by a
    // SafePoint requires changing the Phi. Therefore, I collect all safepoints
    // and patch them all at once later.
    if (use->is_SafePoint()) {
      safepoints.push(use->as_SafePoint());
    } else {
#ifdef ASSERT
      ophi->dump(-3);
      assert(false, "Unexpected user of reducible Phi %d -> %d:%s", ophi->_idx, use->_idx, use->Name());
#endif
      _compile->record_failure(C2Compiler::retry_no_reduce_allocation_merges());
      return;
    }
  }

  if (safepoints.size() > 0) {
    reduce_phi_on_safepoints(ophi, &safepoints);
  }
}

void ConnectionGraph::verify_ram_nodes(Compile* C, Node* root) {
  if (!C->do_reduce_allocation_merges()) return;

  Unique_Node_List ideal_nodes;
  ideal_nodes.map(C->live_nodes(), nullptr);  // preallocate space
  ideal_nodes.push(root);

  for (uint next = 0; next < ideal_nodes.size(); ++next) {
    Node* n = ideal_nodes.at(next);

    if (n->is_SafePointScalarMerge()) {
      SafePointScalarMergeNode* merge = n->as_SafePointScalarMerge();

      // Validate inputs of merge
      for (uint i = 1; i < merge->req(); i++) {
        if (merge->in(i) != nullptr && !merge->in(i)->is_top() && !merge->in(i)->is_SafePointScalarObject()) {
          assert(false, "SafePointScalarMerge inputs should be null/top or SafePointScalarObject.");
          C->record_failure(C2Compiler::retry_no_reduce_allocation_merges());
        }
      }

      // Validate users of merge
      for (DUIterator_Fast imax, i = merge->fast_outs(imax); i < imax; i++) {
        Node* sfpt = merge->fast_out(i);
        if (sfpt->is_SafePoint()) {
          int merge_idx = merge->merge_pointer_idx(sfpt->as_SafePoint()->jvms());

          if (sfpt->in(merge_idx) != nullptr && sfpt->in(merge_idx)->is_SafePointScalarMerge()) {
            assert(false, "SafePointScalarMerge nodes can't be nested.");
            C->record_failure(C2Compiler::retry_no_reduce_allocation_merges());
          }
        } else {
          assert(false, "Only safepoints can use SafePointScalarMerge nodes.");
          C->record_failure(C2Compiler::retry_no_reduce_allocation_merges());
        }
      }
    }

    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* m = n->fast_out(i);
      ideal_nodes.push(m);
    }
  }
}

// Returns true if there is an object in the scope of sfn that does not escape globally.
bool ConnectionGraph::has_ea_local_in_scope(SafePointNode* sfn) {
  Compile* C = _compile;
  for (JVMState* jvms = sfn->jvms(); jvms != nullptr; jvms = jvms->caller()) {
    if (C->env()->should_retain_local_variables() || C->env()->jvmti_can_walk_any_space() ||
        DeoptimizeObjectsALot) {
      // Jvmti agents can access locals. Must provide info about local objects at runtime.
      int num_locs = jvms->loc_size();
      for (int idx = 0; idx < num_locs; idx++) {
        Node* l = sfn->local(jvms, idx);
        if (not_global_escape(l)) {
          return true;
        }
      }
    }
    if (C->env()->jvmti_can_get_owned_monitor_info() ||
        C->env()->jvmti_can_walk_any_space() || DeoptimizeObjectsALot) {
      // Jvmti agents can read monitors. Must provide info about locked objects at runtime.
      int num_mon = jvms->nof_monitors();
      for (int idx = 0; idx < num_mon; idx++) {
        Node* m = sfn->monitor_obj(jvms, idx);
        if (m != nullptr && not_global_escape(m)) {
          return true;
        }
      }
    }
  }
  return false;
}

// Returns true if at least one of the arguments to the call is an object
// that does not escape globally.
bool ConnectionGraph::has_arg_escape(CallJavaNode* call) {
  if (call->method() != nullptr) {
    uint max_idx = TypeFunc::Parms + call->method()->arg_size();
    for (uint idx = TypeFunc::Parms; idx < max_idx; idx++) {
      Node* p = call->in(idx);
      if (not_global_escape(p)) {
        return true;
      }
    }
  } else {
    const char* name = call->as_CallStaticJava()->_name;
    assert(name != nullptr, "no name");
    // no arg escapes through uncommon traps
    if (strcmp(name, "uncommon_trap") != 0) {
      // process_call_arguments() assumes that all arguments escape globally
      const TypeTuple* d = call->tf()->domain();
      for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
        const Type* at = d->field_at(i);
        if (at->isa_oopptr() != nullptr) {
          return true;
        }
      }
    }
  }
  return false;
}



// Utility function for nodes that load an object
void ConnectionGraph::add_objload_to_connection_graph(Node *n, Unique_Node_List *delayed_worklist) {
  // Using isa_ptr() instead of isa_oopptr() for LoadP and Phi because
  // ThreadLocal has RawPtr type.
  const Type* t = _igvn->type(n);
  if (t->make_ptr() != nullptr) {
    Node* adr = n->in(MemNode::Address);
#ifdef ASSERT
    if (!adr->is_AddP()) {
      assert(_igvn->type(adr)->isa_rawptr(), "sanity");
    } else {
      assert((ptnode_adr(adr->_idx) == nullptr ||
              ptnode_adr(adr->_idx)->as_Field()->is_oop()), "sanity");
    }
#endif
    add_local_var_and_edge(n, PointsToNode::NoEscape,
                           adr, delayed_worklist);
  }
}

// Populate Connection Graph with PointsTo nodes and create simple
// connection graph edges.
void ConnectionGraph::add_node_to_connection_graph(Node *n, Unique_Node_List *delayed_worklist) {
  assert(!_verify, "this method should not be called for verification");
  PhaseGVN* igvn = _igvn;
  uint n_idx = n->_idx;
  PointsToNode* n_ptn = ptnode_adr(n_idx);
  if (n_ptn != nullptr) {
    return; // No need to redefine PointsTo node during first iteration.
  }
  int opcode = n->Opcode();
  bool gc_handled = BarrierSet::barrier_set()->barrier_set_c2()->escape_add_to_con_graph(this, igvn, delayed_worklist, n, opcode);
  if (gc_handled) {
    return; // Ignore node if already handled by GC.
  }

  if (n->is_Call()) {
    // Arguments to allocation and locking don't escape.
    if (n->is_AbstractLock()) {
      // Put Lock and Unlock nodes on IGVN worklist to process them during
      // first IGVN optimization when escape information is still available.
      record_for_optimizer(n);
    } else if (n->is_Allocate()) {
      add_call_node(n->as_Call());
      record_for_optimizer(n);
    } else {
      if (n->is_CallStaticJava()) {
        const char* name = n->as_CallStaticJava()->_name;
        if (name != nullptr && strcmp(name, "uncommon_trap") == 0) {
          return; // Skip uncommon traps
        }
      }
      // Don't mark as processed since call's arguments have to be processed.
      delayed_worklist->push(n);
      // Check if a call returns an object.
      if ((n->as_Call()->returns_pointer() &&
           n->as_Call()->proj_out_or_null(TypeFunc::Parms) != nullptr) ||
          (n->is_CallStaticJava() &&
           n->as_CallStaticJava()->is_boxing_method())) {
        add_call_node(n->as_Call());
      }
    }
    return;
  }
  // Put this check here to process call arguments since some call nodes
  // point to phantom_obj.
  if (n_ptn == phantom_obj || n_ptn == null_obj) {
    return; // Skip predefined nodes.
  }
  switch (opcode) {
    case Op_AddP: {
      Node* base = get_addp_base(n);
      PointsToNode* ptn_base = ptnode_adr(base->_idx);
      // Field nodes are created for all field types. They are used in
      // adjust_scalar_replaceable_state() and split_unique_types().
      // Note, non-oop fields will have only base edges in Connection
      // Graph because such fields are not used for oop loads and stores.
      int offset = address_offset(n, igvn);
      add_field(n, PointsToNode::NoEscape, offset);
      if (ptn_base == nullptr) {
        delayed_worklist->push(n); // Process it later.
      } else {
        n_ptn = ptnode_adr(n_idx);
        add_base(n_ptn->as_Field(), ptn_base);
      }
      break;
    }
    case Op_CastX2P: {
      map_ideal_node(n, phantom_obj);
      break;
    }
    case Op_CastPP:
    case Op_CheckCastPP:
    case Op_EncodeP:
    case Op_DecodeN:
    case Op_EncodePKlass:
    case Op_DecodeNKlass: {
      add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(1), delayed_worklist);
      break;
    }
    case Op_CMoveP: {
      add_local_var(n, PointsToNode::NoEscape);
      // Do not add edges during first iteration because some could be
      // not defined yet.
      delayed_worklist->push(n);
      break;
    }
    case Op_ConP:
    case Op_ConN:
    case Op_ConNKlass: {
      // assume all oop constants globally escape except for null
      PointsToNode::EscapeState es;
      const Type* t = igvn->type(n);
      if (t == TypePtr::NULL_PTR || t == TypeNarrowOop::NULL_PTR) {
        es = PointsToNode::NoEscape;
      } else {
        es = PointsToNode::GlobalEscape;
      }
      PointsToNode* ptn_con = add_java_object(n, es);
      set_not_scalar_replaceable(ptn_con NOT_PRODUCT(COMMA "Constant pointer"));
      break;
    }
    case Op_CreateEx: {
      // assume that all exception objects globally escape
      map_ideal_node(n, phantom_obj);
      break;
    }
    case Op_LoadKlass:
    case Op_LoadNKlass: {
      // Unknown class is loaded
      map_ideal_node(n, phantom_obj);
      break;
    }
    case Op_LoadP:
    case Op_LoadN: {
      add_objload_to_connection_graph(n, delayed_worklist);
      break;
    }
    case Op_Parm: {
      map_ideal_node(n, phantom_obj);
      break;
    }
    case Op_PartialSubtypeCheck: {
      // Produces Null or notNull and is used in only in CmpP so
      // phantom_obj could be used.
      map_ideal_node(n, phantom_obj); // Result is unknown
      break;
    }
    case Op_Phi: {
      // Using isa_ptr() instead of isa_oopptr() for LoadP and Phi because
      // ThreadLocal has RawPtr type.
      const Type* t = n->as_Phi()->type();
      if (t->make_ptr() != nullptr) {
        add_local_var(n, PointsToNode::NoEscape);
        // Do not add edges during first iteration because some could be
        // not defined yet.
        delayed_worklist->push(n);
      }
      break;
    }
    case Op_Proj: {
      // we are only interested in the oop result projection from a call
      if (n->as_Proj()->_con == TypeFunc::Parms && n->in(0)->is_Call() &&
          n->in(0)->as_Call()->returns_pointer()) {
        add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(0), delayed_worklist);
      }
      break;
    }
    case Op_Rethrow: // Exception object escapes
    case Op_Return: {
      if (n->req() > TypeFunc::Parms &&
          igvn->type(n->in(TypeFunc::Parms))->isa_oopptr()) {
        // Treat Return value as LocalVar with GlobalEscape escape state.
        add_local_var_and_edge(n, PointsToNode::GlobalEscape, n->in(TypeFunc::Parms), delayed_worklist);
      }
      break;
    }
    case Op_CompareAndExchangeP:
    case Op_CompareAndExchangeN:
    case Op_GetAndSetP:
    case Op_GetAndSetN: {
      add_objload_to_connection_graph(n, delayed_worklist);
      // fall-through
    }
    case Op_StoreP:
    case Op_StoreN:
    case Op_StoreNKlass:
    case Op_WeakCompareAndSwapP:
    case Op_WeakCompareAndSwapN:
    case Op_CompareAndSwapP:
    case Op_CompareAndSwapN: {
      add_to_congraph_unsafe_access(n, opcode, delayed_worklist);
      break;
    }
    case Op_AryEq:
    case Op_CountPositives:
    case Op_StrComp:
    case Op_StrEquals:
    case Op_StrIndexOf:
    case Op_StrIndexOfChar:
    case Op_StrInflatedCopy:
    case Op_StrCompressedCopy:
    case Op_VectorizedHashCode:
    case Op_EncodeISOArray: {
      add_local_var(n, PointsToNode::ArgEscape);
      delayed_worklist->push(n); // Process it later.
      break;
    }
    case Op_ThreadLocal: {
      PointsToNode* ptn_thr = add_java_object(n, PointsToNode::ArgEscape);
      set_not_scalar_replaceable(ptn_thr NOT_PRODUCT(COMMA "Constant pointer"));
      break;
    }
    case Op_Blackhole: {
      // All blackhole pointer arguments are globally escaping.
      // Only do this if there is at least one pointer argument.
      // Do not add edges during first iteration because some could be
      // not defined yet, defer to final step.
      for (uint i = 0; i < n->req(); i++) {
        Node* in = n->in(i);
        if (in != nullptr) {
          const Type* at = _igvn->type(in);
          if (!at->isa_ptr()) continue;

          add_local_var(n, PointsToNode::GlobalEscape);
          delayed_worklist->push(n);
          break;
        }
      }
      break;
    }
    default:
      ; // Do nothing for nodes not related to EA.
  }
  return;
}

// Add final simple edges to graph.
void ConnectionGraph::add_final_edges(Node *n) {
  PointsToNode* n_ptn = ptnode_adr(n->_idx);
#ifdef ASSERT
  if (_verify && n_ptn->is_JavaObject())
    return; // This method does not change graph for JavaObject.
#endif

  if (n->is_Call()) {
    process_call_arguments(n->as_Call());
    return;
  }
  assert(n->is_Store() || n->is_LoadStore() ||
         ((n_ptn != nullptr) && (n_ptn->ideal_node() != nullptr)),
         "node should be registered already");
  int opcode = n->Opcode();
  bool gc_handled = BarrierSet::barrier_set()->barrier_set_c2()->escape_add_final_edges(this, _igvn, n, opcode);
  if (gc_handled) {
    return; // Ignore node if already handled by GC.
  }
  switch (opcode) {
    case Op_AddP: {
      Node* base = get_addp_base(n);
      PointsToNode* ptn_base = ptnode_adr(base->_idx);
      assert(ptn_base != nullptr, "field's base should be registered");
      add_base(n_ptn->as_Field(), ptn_base);
      break;
    }
    case Op_CastPP:
    case Op_CheckCastPP:
    case Op_EncodeP:
    case Op_DecodeN:
    case Op_EncodePKlass:
    case Op_DecodeNKlass: {
      add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(1), nullptr);
      break;
    }
    case Op_CMoveP: {
      for (uint i = CMoveNode::IfFalse; i < n->req(); i++) {
        Node* in = n->in(i);
        if (in == nullptr) {
          continue;  // ignore null
        }
        Node* uncast_in = in->uncast();
        if (uncast_in->is_top() || uncast_in == n) {
          continue;  // ignore top or inputs which go back this node
        }
        PointsToNode* ptn = ptnode_adr(in->_idx);
        assert(ptn != nullptr, "node should be registered");
        add_edge(n_ptn, ptn);
      }
      break;
    }
    case Op_LoadP:
    case Op_LoadN: {
      // Using isa_ptr() instead of isa_oopptr() for LoadP and Phi because
      // ThreadLocal has RawPtr type.
      assert(_igvn->type(n)->make_ptr() != nullptr, "Unexpected node type");
      add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(MemNode::Address), nullptr);
      break;
    }
    case Op_Phi: {
      // Using isa_ptr() instead of isa_oopptr() for LoadP and Phi because
      // ThreadLocal has RawPtr type.
      assert(n->as_Phi()->type()->make_ptr() != nullptr, "Unexpected node type");
      for (uint i = 1; i < n->req(); i++) {
        Node* in = n->in(i);
        if (in == nullptr) {
          continue;  // ignore null
        }
        Node* uncast_in = in->uncast();
        if (uncast_in->is_top() || uncast_in == n) {
          continue;  // ignore top or inputs which go back this node
        }
        PointsToNode* ptn = ptnode_adr(in->_idx);
        assert(ptn != nullptr, "node should be registered");
        add_edge(n_ptn, ptn);
      }
      break;
    }
    case Op_Proj: {
      // we are only interested in the oop result projection from a call
      assert(n->as_Proj()->_con == TypeFunc::Parms && n->in(0)->is_Call() &&
             n->in(0)->as_Call()->returns_pointer(), "Unexpected node type");
      add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(0), nullptr);
      break;
    }
    case Op_Rethrow: // Exception object escapes
    case Op_Return: {
      assert(n->req() > TypeFunc::Parms && _igvn->type(n->in(TypeFunc::Parms))->isa_oopptr(),
             "Unexpected node type");
      // Treat Return value as LocalVar with GlobalEscape escape state.
      add_local_var_and_edge(n, PointsToNode::GlobalEscape, n->in(TypeFunc::Parms), nullptr);
      break;
    }
    case Op_CompareAndExchangeP:
    case Op_CompareAndExchangeN:
    case Op_GetAndSetP:
    case Op_GetAndSetN:{
      assert(_igvn->type(n)->make_ptr() != nullptr, "Unexpected node type");
      add_local_var_and_edge(n, PointsToNode::NoEscape, n->in(MemNode::Address), nullptr);
      // fall-through
    }
    case Op_CompareAndSwapP:
    case Op_CompareAndSwapN:
    case Op_WeakCompareAndSwapP:
    case Op_WeakCompareAndSwapN:
    case Op_StoreP:
    case Op_StoreN:
    case Op_StoreNKlass:{
      add_final_edges_unsafe_access(n, opcode);
      break;
    }
    case Op_VectorizedHashCode:
    case Op_AryEq:
    case Op_CountPositives:
    case Op_StrComp:
    case Op_StrEquals:
    case Op_StrIndexOf:
    case Op_StrIndexOfChar:
    case Op_StrInflatedCopy:
    case Op_StrCompressedCopy:
    case Op_EncodeISOArray: {
      // char[]/byte[] arrays passed to string intrinsic do not escape but
      // they are not scalar replaceable. Adjust escape state for them.
      // Start from in(2) edge since in(1) is memory edge.
      for (uint i = 2; i < n->req(); i++) {
        Node* adr = n->in(i);
        const Type* at = _igvn->type(adr);
        if (!adr->is_top() && at->isa_ptr()) {
          assert(at == Type::TOP || at == TypePtr::NULL_PTR ||
                 at->isa_ptr() != nullptr, "expecting a pointer");
          if (adr->is_AddP()) {
            adr = get_addp_base(adr);
          }
          PointsToNode* ptn = ptnode_adr(adr->_idx);
          assert(ptn != nullptr, "node should be registered");
          add_edge(n_ptn, ptn);
        }
      }
      break;
    }
    case Op_Blackhole: {
      // All blackhole pointer arguments are globally escaping.
      for (uint i = 0; i < n->req(); i++) {
        Node* in = n->in(i);
        if (in != nullptr) {
          const Type* at = _igvn->type(in);
          if (!at->isa_ptr()) continue;

          if (in->is_AddP()) {
            in = get_addp_base(in);
          }

          PointsToNode* ptn = ptnode_adr(in->_idx);
          assert(ptn != nullptr, "should be defined already");
          set_escape_state(ptn, PointsToNode::GlobalEscape NOT_PRODUCT(COMMA "blackhole"));
          add_edge(n_ptn, ptn);
        }
      }
      break;
    }
    default: {
      // This method should be called only for EA specific nodes which may
      // miss some edges when they were created.
#ifdef ASSERT
      n->dump(1);
#endif
      guarantee(false, "unknown node");
    }
  }
  return;
}

void ConnectionGraph::add_to_congraph_unsafe_access(Node* n, uint opcode, Unique_Node_List* delayed_worklist) {
  Node* adr = n->in(MemNode::Address);
  const Type* adr_type = _igvn->type(adr);
  adr_type = adr_type->make_ptr();
  if (adr_type == nullptr) {
    return; // skip dead nodes
  }
  if (adr_type->isa_oopptr()
      || ((opcode == Op_StoreP || opcode == Op_StoreN || opcode == Op_StoreNKlass)
          && adr_type == TypeRawPtr::NOTNULL
          && is_captured_store_address(adr))) {
    delayed_worklist->push(n); // Process it later.
#ifdef ASSERT
    assert (adr->is_AddP(), "expecting an AddP");
    if (adr_type == TypeRawPtr::NOTNULL) {
      // Verify a raw address for a store captured by Initialize node.
      int offs = (int) _igvn->find_intptr_t_con(adr->in(AddPNode::Offset), Type::OffsetBot);
      assert(offs != Type::OffsetBot, "offset must be a constant");
    }
#endif
  } else {
    // Ignore copy the displaced header to the BoxNode (OSR compilation).
    if (adr->is_BoxLock()) {
      return;
    }
    // Stored value escapes in unsafe access.
    if ((opcode == Op_StoreP) && adr_type->isa_rawptr()) {
      delayed_worklist->push(n); // Process unsafe access later.
      return;
    }
#ifdef ASSERT
    n->dump(1);
    assert(false, "not unsafe");
#endif
  }
}

bool ConnectionGraph::add_final_edges_unsafe_access(Node* n, uint opcode) {
  Node* adr = n->in(MemNode::Address);
  const Type *adr_type = _igvn->type(adr);
  adr_type = adr_type->make_ptr();
#ifdef ASSERT
  if (adr_type == nullptr) {
    n->dump(1);
    assert(adr_type != nullptr, "dead node should not be on list");
    return true;
  }
#endif

  if (adr_type->isa_oopptr()
      || ((opcode == Op_StoreP || opcode == Op_StoreN || opcode == Op_StoreNKlass)
           && adr_type == TypeRawPtr::NOTNULL
           && is_captured_store_address(adr))) {
    // Point Address to Value
    PointsToNode* adr_ptn = ptnode_adr(adr->_idx);
    assert(adr_ptn != nullptr &&
           adr_ptn->as_Field()->is_oop(), "node should be registered");
    Node* val = n->in(MemNode::ValueIn);
    PointsToNode* ptn = ptnode_adr(val->_idx);
    assert(ptn != nullptr, "node should be registered");
    add_edge(adr_ptn, ptn);
    return true;
  } else if ((opcode == Op_StoreP) && adr_type->isa_rawptr()) {
    // Stored value escapes in unsafe access.
    Node* val = n->in(MemNode::ValueIn);
    PointsToNode* ptn = ptnode_adr(val->_idx);
    assert(ptn != nullptr, "node should be registered");
    set_escape_state(ptn, PointsToNode::GlobalEscape NOT_PRODUCT(COMMA "stored at raw address"));
    // Add edge to object for unsafe access with offset.
    PointsToNode* adr_ptn = ptnode_adr(adr->_idx);
    assert(adr_ptn != nullptr, "node should be registered");
    if (adr_ptn->is_Field()) {
      assert(adr_ptn->as_Field()->is_oop(), "should be oop field");
      add_edge(adr_ptn, ptn);
    }
    return true;
  }
#ifdef ASSERT
  n->dump(1);
  assert(false, "not unsafe");
#endif
  return false;
}

void ConnectionGraph::add_call_node(CallNode* call) {
  assert(call->returns_pointer(), "only for call which returns pointer");
  uint call_idx = call->_idx;
  if (call->is_Allocate()) {
    Node* k = call->in(AllocateNode::KlassNode);
    const TypeKlassPtr* kt = k->bottom_type()->isa_klassptr();
    assert(kt != nullptr, "TypeKlassPtr  required.");
    PointsToNode::EscapeState es = PointsToNode::NoEscape;
    bool scalar_replaceable = true;
    NOT_PRODUCT(const char* nsr_reason = "");
    if (call->is_AllocateArray()) {
      if (!kt->isa_aryklassptr()) { // StressReflectiveCode
        es = PointsToNode::GlobalEscape;
      } else {
        int length = call->in(AllocateNode::ALength)->find_int_con(-1);
        if (length < 0) {
          // Not scalar replaceable if the length is not constant.
          scalar_replaceable = false;
          NOT_PRODUCT(nsr_reason = "has a non-constant length");
        } else if (length > EliminateAllocationArraySizeLimit) {
          // Not scalar replaceable if the length is too big.
          scalar_replaceable = false;
          NOT_PRODUCT(nsr_reason = "has a length that is too big");
        }
      }
    } else {  // Allocate instance
      if (!kt->isa_instklassptr()) { // StressReflectiveCode
        es = PointsToNode::GlobalEscape;
      } else {
        const TypeInstKlassPtr* ikt = kt->is_instklassptr();
        ciInstanceKlass* ik = ikt->klass_is_exact() ? ikt->exact_klass()->as_instance_klass() : ikt->instance_klass();
        if (ik->is_subclass_of(_compile->env()->Thread_klass()) ||
            ik->is_subclass_of(_compile->env()->Reference_klass()) ||
            !ik->can_be_instantiated() ||
            ik->has_finalizer()) {
          es = PointsToNode::GlobalEscape;
        } else {
          int nfields = ik->as_instance_klass()->nof_nonstatic_fields();
          if (nfields > EliminateAllocationFieldsLimit) {
            // Not scalar replaceable if there are too many fields.
            scalar_replaceable = false;
            NOT_PRODUCT(nsr_reason = "has too many fields");
          }
        }
      }
    }
    add_java_object(call, es);
    PointsToNode* ptn = ptnode_adr(call_idx);
    if (!scalar_replaceable && ptn->scalar_replaceable()) {
      set_not_scalar_replaceable(ptn NOT_PRODUCT(COMMA nsr_reason));
    }
  } else if (call->is_CallStaticJava()) {
    // Call nodes could be different types:
    //
    // 1. CallDynamicJavaNode (what happened during call is unknown):
    //
    //    - mapped to GlobalEscape JavaObject node if oop is returned;
    //
    //    - all oop arguments are escaping globally;
    //
    // 2. CallStaticJavaNode (execute bytecode analysis if possible):
    //
    //    - the same as CallDynamicJavaNode if can't do bytecode analysis;
    //
    //    - mapped to GlobalEscape JavaObject node if unknown oop is returned;
    //    - mapped to NoEscape JavaObject node if non-escaping object allocated
    //      during call is returned;
    //    - mapped to ArgEscape LocalVar node pointed to object arguments
    //      which are returned and does not escape during call;
    //
    //    - oop arguments escaping status is defined by bytecode analysis;
    //
    // For a static call, we know exactly what method is being called.
    // Use bytecode estimator to record whether the call's return value escapes.
    ciMethod* meth = call->as_CallJava()->method();
    if (meth == nullptr) {
      const char* name = call->as_CallStaticJava()->_name;
      assert(strncmp(name, "_multianewarray", 15) == 0, "TODO: add failed case check");
      // Returns a newly allocated non-escaped object.
      add_java_object(call, PointsToNode::NoEscape);
      set_not_scalar_replaceable(ptnode_adr(call_idx) NOT_PRODUCT(COMMA "is result of multinewarray"));
    } else if (meth->is_boxing_method()) {
      // Returns boxing object
      PointsToNode::EscapeState es;
      vmIntrinsics::ID intr = meth->intrinsic_id();
      if (intr == vmIntrinsics::_floatValue || intr == vmIntrinsics::_doubleValue) {
        // It does not escape if object is always allocated.
        es = PointsToNode::NoEscape;
      } else {
        // It escapes globally if object could be loaded from cache.
        es = PointsToNode::GlobalEscape;
      }
      add_java_object(call, es);
      if (es == PointsToNode::GlobalEscape) {
        set_not_scalar_replaceable(ptnode_adr(call->_idx) NOT_PRODUCT(COMMA "object can be loaded from boxing cache"));
      }
    } else {
      BCEscapeAnalyzer* call_analyzer = meth->get_bcea();
      call_analyzer->copy_dependencies(_compile->dependencies());
      if (call_analyzer->is_return_allocated()) {
        // Returns a newly allocated non-escaped object, simply
        // update dependency information.
        // Mark it as NoEscape so that objects referenced by
        // it's fields will be marked as NoEscape at least.
        add_java_object(call, PointsToNode::NoEscape);
        set_not_scalar_replaceable(ptnode_adr(call_idx) NOT_PRODUCT(COMMA "is result of call"));
      } else {
        // Determine whether any arguments are returned.
        const TypeTuple* d = call->tf()->domain();
        bool ret_arg = false;
        for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
          if (d->field_at(i)->isa_ptr() != nullptr &&
              call_analyzer->is_arg_returned(i - TypeFunc::Parms)) {
            ret_arg = true;
            break;
          }
        }
        if (ret_arg) {
          add_local_var(call, PointsToNode::ArgEscape);
        } else {
          // Returns unknown object.
          map_ideal_node(call, phantom_obj);
        }
      }
    }
  } else {
    // An other type of call, assume the worst case:
    // returned value is unknown and globally escapes.
    assert(call->Opcode() == Op_CallDynamicJava, "add failed case check");
    map_ideal_node(call, phantom_obj);
  }
}

void ConnectionGraph::process_call_arguments(CallNode *call) {
    bool is_arraycopy = false;
    switch (call->Opcode()) {
#ifdef ASSERT
    case Op_Allocate:
    case Op_AllocateArray:
    case Op_Lock:
    case Op_Unlock:
      assert(false, "should be done already");
      break;
#endif
    case Op_ArrayCopy:
    case Op_CallLeafNoFP:
      // Most array copies are ArrayCopy nodes at this point but there
      // are still a few direct calls to the copy subroutines (See
      // PhaseStringOpts::copy_string())
      is_arraycopy = (call->Opcode() == Op_ArrayCopy) ||
        call->as_CallLeaf()->is_call_to_arraycopystub();
      // fall through
    case Op_CallLeafVector:
    case Op_CallLeaf: {
      // Stub calls, objects do not escape but they are not scale replaceable.
      // Adjust escape state for outgoing arguments.
      const TypeTuple * d = call->tf()->domain();
      bool src_has_oops = false;
      for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
        const Type* at = d->field_at(i);
        Node *arg = call->in(i);
        if (arg == nullptr) {
          continue;
        }
        const Type *aat = _igvn->type(arg);
        if (arg->is_top() || !at->isa_ptr() || !aat->isa_ptr()) {
          continue;
        }
        if (arg->is_AddP()) {
          //
          // The inline_native_clone() case when the arraycopy stub is called
          // after the allocation before Initialize and CheckCastPP nodes.
          // Or normal arraycopy for object arrays case.
          //
          // Set AddP's base (Allocate) as not scalar replaceable since
          // pointer to the base (with offset) is passed as argument.
          //
          arg = get_addp_base(arg);
        }
        PointsToNode* arg_ptn = ptnode_adr(arg->_idx);
        assert(arg_ptn != nullptr, "should be registered");
        PointsToNode::EscapeState arg_esc = arg_ptn->escape_state();
        if (is_arraycopy || arg_esc < PointsToNode::ArgEscape) {
          assert(aat == Type::TOP || aat == TypePtr::NULL_PTR ||
                 aat->isa_ptr() != nullptr, "expecting an Ptr");
          bool arg_has_oops = aat->isa_oopptr() &&
                              (aat->isa_instptr() ||
                               (aat->isa_aryptr() && (aat->isa_aryptr()->elem() == Type::BOTTOM || aat->isa_aryptr()->elem()->make_oopptr() != nullptr)));
          if (i == TypeFunc::Parms) {
            src_has_oops = arg_has_oops;
          }
          //
          // src or dst could be j.l.Object when other is basic type array:
          //
          //   arraycopy(char[],0,Object*,0,size);
          //   arraycopy(Object*,0,char[],0,size);
          //
          // Don't add edges in such cases.
          //
          bool arg_is_arraycopy_dest = src_has_oops && is_arraycopy &&
                                       arg_has_oops && (i > TypeFunc::Parms);
#ifdef ASSERT
          if (!(is_arraycopy ||
                BarrierSet::barrier_set()->barrier_set_c2()->is_gc_barrier_node(call) ||
                (call->as_CallLeaf()->_name != nullptr &&
                 (strcmp(call->as_CallLeaf()->_name, "updateBytesCRC32") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "updateBytesCRC32C") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "updateBytesAdler32") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "aescrypt_encryptBlock") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "aescrypt_decryptBlock") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "cipherBlockChaining_encryptAESCrypt") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "cipherBlockChaining_decryptAESCrypt") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "electronicCodeBook_encryptAESCrypt") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "electronicCodeBook_decryptAESCrypt") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "counterMode_AESCrypt") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "galoisCounterMode_AESCrypt") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "poly1305_processBlocks") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "ghash_processBlocks") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "chacha20Block") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "encodeBlock") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "decodeBlock") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "md5_implCompress") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "md5_implCompressMB") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha1_implCompress") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha1_implCompressMB") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha256_implCompress") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha256_implCompressMB") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha512_implCompress") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha512_implCompressMB") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha3_implCompress") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "sha3_implCompressMB") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "multiplyToLen") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "squareToLen") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "mulAdd") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "montgomery_multiply") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "montgomery_square") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "bigIntegerRightShiftWorker") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "bigIntegerLeftShiftWorker") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "vectorizedMismatch") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "arraysort_stub") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "array_partition_stub") == 0 ||
                  strcmp(call->as_CallLeaf()->_name, "get_class_id_intrinsic") == 0)
                 ))) {
            call->dump();
            fatal("EA unexpected CallLeaf %s", call->as_CallLeaf()->_name);
          }
#endif
          // Always process arraycopy's destination object since
          // we need to add all possible edges to references in
          // source object.
          if (arg_esc >= PointsToNode::ArgEscape &&
              !arg_is_arraycopy_dest) {
            continue;
          }
          PointsToNode::EscapeState es = PointsToNode::ArgEscape;
          if (call->is_ArrayCopy()) {
            ArrayCopyNode* ac = call->as_ArrayCopy();
            if (ac->is_clonebasic() ||
                ac->is_arraycopy_validated() ||
                ac->is_copyof_validated() ||
                ac->is_copyofrange_validated()) {
              es = PointsToNode::NoEscape;
            }
          }
          set_escape_state(arg_ptn, es NOT_PRODUCT(COMMA trace_arg_escape_message(call)));
          if (arg_is_arraycopy_dest) {
            Node* src = call->in(TypeFunc::Parms);
            if (src->is_AddP()) {
              src = get_addp_base(src);
            }
            PointsToNode* src_ptn = ptnode_adr(src->_idx);
            assert(src_ptn != nullptr, "should be registered");
            if (arg_ptn != src_ptn) {
              // Special arraycopy edge:
              // A destination object's field can't have the source object
              // as base since objects escape states are not related.
              // Only escape state of destination object's fields affects
              // escape state of fields in source object.
              add_arraycopy(call, es, src_ptn, arg_ptn);
            }
          }
        }
      }
      break;
    }
    case Op_CallStaticJava: {
      // For a static call, we know exactly what method is being called.
      // Use bytecode estimator to record the call's escape affects
#ifdef ASSERT
      const char* name = call->as_CallStaticJava()->_name;
      assert((name == nullptr || strcmp(name, "uncommon_trap") != 0), "normal calls only");
#endif
      ciMethod* meth = call->as_CallJava()->method();
      if ((meth != nullptr) && meth->is_boxing_method()) {
        break; // Boxing methods do not modify any oops.
      }
      BCEscapeAnalyzer* call_analyzer = (meth !=nullptr) ? meth->get_bcea() : nullptr;
      // fall-through if not a Java method or no analyzer information
      if (call_analyzer != nullptr) {
        PointsToNode* call_ptn = ptnode_adr(call->_idx);
        const TypeTuple* d = call->tf()->domain();
        for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
          const Type* at = d->field_at(i);
          int k = i - TypeFunc::Parms;
          Node* arg = call->in(i);
          PointsToNode* arg_ptn = ptnode_adr(arg->_idx);
          if (at->isa_ptr() != nullptr &&
              call_analyzer->is_arg_returned(k)) {
            // The call returns arguments.
            if (call_ptn != nullptr) { // Is call's result used?
              assert(call_ptn->is_LocalVar(), "node should be registered");
              assert(arg_ptn != nullptr, "node should be registered");
              add_edge(call_ptn, arg_ptn);
            }
          }
          if (at->isa_oopptr() != nullptr &&
              arg_ptn->escape_state() < PointsToNode::GlobalEscape) {
            if (!call_analyzer->is_arg_stack(k)) {
              // The argument global escapes
              set_escape_state(arg_ptn, PointsToNode::GlobalEscape NOT_PRODUCT(COMMA trace_arg_escape_message(call)));
            } else {
              set_escape_state(arg_ptn, PointsToNode::ArgEscape NOT_PRODUCT(COMMA trace_arg_escape_message(call)));
              if (!call_analyzer->is_arg_local(k)) {
                // The argument itself doesn't escape, but any fields might
                set_fields_escape_state(arg_ptn, PointsToNode::GlobalEscape NOT_PRODUCT(COMMA trace_arg_escape_message(call)));
              }
            }
          }
        }
        if (call_ptn != nullptr && call_ptn->is_LocalVar()) {
          // The call returns arguments.
          assert(call_ptn->edge_count() > 0, "sanity");
          if (!call_analyzer->is_return_local()) {
            // Returns also unknown object.
            add_edge(call_ptn, phantom_obj);
          }
        }
        break;
      }
    }
    default: {
      // Fall-through here if not a Java method or no analyzer information
      // or some other type of call, assume the worst case: all arguments
      // globally escape.
      const TypeTuple* d = call->tf()->domain();
      for (uint i = TypeFunc::Parms; i < d->cnt(); i++) {
        const Type* at = d->field_at(i);
        if (at->isa_oopptr() != nullptr) {
          Node* arg = call->in(i);
          if (arg->is_AddP()) {
            arg = get_addp_base(arg);
          }
          assert(ptnode_adr(arg->_idx) != nullptr, "should be defined already");
          set_escape_state(ptnode_adr(arg->_idx), PointsToNode::GlobalEscape NOT_PRODUCT(COMMA trace_arg_escape_message(call)));
        }
      }
    }
  }
}


// Finish Graph construction.
bool ConnectionGraph::complete_connection_graph(
                         GrowableArray<PointsToNode*>&   ptnodes_worklist,
                         GrowableArray<JavaObjectNode*>& non_escaped_allocs_worklist,
                         GrowableArray<JavaObjectNode*>& java_objects_worklist,
                         GrowableArray<FieldNode*>&      oop_fields_worklist) {
  // Normally only 1-3 passes needed to build Connection Graph depending
  // on graph complexity. Observed 8 passes in jvm2008 compiler.compiler.
  // Set limit to 20 to catch situation when something did go wrong and
  // bailout Escape Analysis.
  // Also limit build time to 20 sec (60 in debug VM), EscapeAnalysisTimeout flag.
#define GRAPH_BUILD_ITER_LIMIT 20

  // Propagate GlobalEscape and ArgEscape escape states and check that
  // we still have non-escaping objects. The method pushs on _worklist
  // Field nodes which reference phantom_object.
  if (!find_non_escaped_objects(ptnodes_worklist, non_escaped_allocs_worklist)) {
    return false; // Nothing to do.
  }
  // Now propagate references to all JavaObject nodes.
  int java_objects_length = java_objects_worklist.length();
  elapsedTimer build_time;
  build_time.start();
  elapsedTimer time;
  bool timeout = false;
  int new_edges = 1;
  int iterations = 0;
  do {
    while ((new_edges > 0) &&
           (iterations++ < GRAPH_BUILD_ITER_LIMIT)) {
      double start_time = time.seconds();
      time.start();
      new_edges = 0;
      // Propagate references to phantom_object for nodes pushed on _worklist
      // by find_non_escaped_objects() and find_field_value().
      new_edges += add_java_object_edges(phantom_obj, false);
      for (int next = 0; next < java_objects_length; ++next) {
        JavaObjectNode* ptn = java_objects_worklist.at(next);
        new_edges += add_java_object_edges(ptn, true);

#define SAMPLE_SIZE 4
        if ((next % SAMPLE_SIZE) == 0) {
          // Each 4 iterations calculate how much time it will take
          // to complete graph construction.
          time.stop();
          // Poll for requests from shutdown mechanism to quiesce compiler
          // because Connection graph construction may take long time.
          CompileBroker::maybe_block();
          double stop_time = time.seconds();
          double time_per_iter = (stop_time - start_time) / (double)SAMPLE_SIZE;
          double time_until_end = time_per_iter * (double)(java_objects_length - next);
          if ((start_time + time_until_end) >= EscapeAnalysisTimeout) {
            timeout = true;
            break; // Timeout
          }
          start_time = stop_time;
          time.start();
        }
#undef SAMPLE_SIZE

      }
      if (timeout) break;
      if (new_edges > 0) {
        // Update escape states on each iteration if graph was updated.
        if (!find_non_escaped_objects(ptnodes_worklist, non_escaped_allocs_worklist)) {
          return false; // Nothing to do.
        }
      }
      time.stop();
      if (time.seconds() >= EscapeAnalysisTimeout) {
        timeout = true;
        break;
      }
    }
    if ((iterations < GRAPH_BUILD_ITER_LIMIT) && !timeout) {
      time.start();
      // Find fields which have unknown value.
      int fields_length = oop_fields_worklist.length();
      for (int next = 0; next < fields_length; next++) {
        FieldNode* field = oop_fields_worklist.at(next);
        if (field->edge_count() == 0) {
          new_edges += find_field_value(field);
          // This code may added new edges to phantom_object.
          // Need an other cycle to propagate references to phantom_object.
        }
      }
      time.stop();
      if (time.seconds() >= EscapeAnalysisTimeout) {
        timeout = true;
        break;
      }
    } else {
      new_edges = 0; // Bailout
    }
  } while (new_edges > 0);

  build_time.stop();
  _build_time = build_time.seconds();
  _build_iterations = iterations;

  // Bailout if passed limits.
  if ((iterations >= GRAPH_BUILD_ITER_LIMIT) || timeout) {
    Compile* C = _compile;
    if (C->log() != nullptr) {
      C->log()->begin_elem("connectionGraph_bailout reason='reached ");
      C->log()->text("%s", timeout ? "time" : "iterations");
      C->log()->end_elem(" limit'");
    }
    assert(ExitEscapeAnalysisOnTimeout, "infinite EA connection graph build during invocation %d (%f sec, %d iterations) with %d nodes and worklist size %d",
           _invocation, _build_time, _build_iterations, nodes_size(), ptnodes_worklist.length());
    // Possible infinite build_connection_graph loop,
    // bailout (no changes to ideal graph were made).
    return false;
  }

#undef GRAPH_BUILD_ITER_LIMIT

  // Find fields initialized by null for non-escaping Allocations.
  int non_escaped_length = non_escaped_allocs_worklist.length();
  for (int next = 0; next < non_escaped_length; next++) {
    JavaObjectNode* ptn = non_escaped_allocs_worklist.at(next);
    PointsToNode::EscapeState es = ptn->escape_state();
    assert(es <= PointsToNode::ArgEscape, "sanity");
    if (es == PointsToNode::NoEscape) {
      if (find_init_values_null(ptn, _igvn) > 0) {
        // Adding references to null object does not change escape states
        // since it does not escape. Also no fields are added to null object.
        add_java_object_edges(null_obj, false);
      }
    }
    Node* n = ptn->ideal_node();
    if (n->is_Allocate()) {
      // The object allocated by this Allocate node will never be
      // seen by an other thread. Mark it so that when it is
      // expanded no MemBarStoreStore is added.
      InitializeNode* ini = n->as_Allocate()->initialization();
      if (ini != nullptr)
        ini->set_does_not_escape();
    }
  }
  return true; // Finished graph construction.
}

// Propagate GlobalEscape and ArgEscape escape states to all nodes
// and check that we still have non-escaping java objects.
bool ConnectionGraph::find_non_escaped_objects(GrowableArray<PointsToNode*>& ptnodes_worklist,
                                               GrowableArray<JavaObjectNode*>& non_escaped_allocs_worklist) {
  GrowableArray<PointsToNode*> escape_worklist;
  // First, put all nodes with GlobalEscape and ArgEscape states on worklist.
  int ptnodes_length = ptnodes_worklist.length();
  for (int next = 0; next < ptnodes_length; ++next) {
    PointsToNode* ptn = ptnodes_worklist.at(next);
    if (ptn->escape_state() >= PointsToNode::ArgEscape ||
        ptn->fields_escape_state() >= PointsToNode::ArgEscape) {
      escape_worklist.push(ptn);
    }
  }
  // Set escape states to referenced nodes (edges list).
  while (escape_worklist.length() > 0) {
    PointsToNode* ptn = escape_worklist.pop();
    PointsToNode::EscapeState es  = ptn->escape_state();
    PointsToNode::EscapeState field_es = ptn->fields_escape_state();
    if (ptn->is_Field() && ptn->as_Field()->is_oop() &&
        es >= PointsToNode::ArgEscape) {
      // GlobalEscape or ArgEscape state of field means it has unknown value.
      if (add_edge(ptn, phantom_obj)) {
        // New edge was added
        add_field_uses_to_worklist(ptn->as_Field());
      }
    }
    for (EdgeIterator i(ptn); i.has_next(); i.next()) {
      PointsToNode* e = i.get();
      if (e->is_Arraycopy()) {
        assert(ptn->arraycopy_dst(), "sanity");
        // Propagate only fields escape state through arraycopy edge.
        if (e->fields_escape_state() < field_es) {
          set_fields_escape_state(e, field_es NOT_PRODUCT(COMMA trace_propagate_message(ptn)));
          escape_worklist.push(e);
        }
      } else if (es >= field_es) {
        // fields_escape_state is also set to 'es' if it is less than 'es'.
        if (e->escape_state() < es) {
          set_escape_state(e, es NOT_PRODUCT(COMMA trace_propagate_message(ptn)));
          escape_worklist.push(e);
        }
      } else {
        // Propagate field escape state.
        bool es_changed = false;
        if (e->fields_escape_state() < field_es) {
          set_fields_escape_state(e, field_es NOT_PRODUCT(COMMA trace_propagate_message(ptn)));
          es_changed = true;
        }
        if ((e->escape_state() < field_es) &&
            e->is_Field() && ptn->is_JavaObject() &&
            e->as_Field()->is_oop()) {
          // Change escape state of referenced fields.
          set_escape_state(e, field_es NOT_PRODUCT(COMMA trace_propagate_message(ptn)));
          es_changed = true;
        } else if (e->escape_state() < es) {
          set_escape_state(e, es NOT_PRODUCT(COMMA trace_propagate_message(ptn)));
          es_changed = true;
        }
        if (es_changed) {
          escape_worklist.push(e);
        }
      }
    }
  }
  // Remove escaped objects from non_escaped list.
  for (int next = non_escaped_allocs_worklist.length()-1; next >= 0 ; --next) {
    JavaObjectNode* ptn = non_escaped_allocs_worklist.at(next);
    if (ptn->escape_state() >= PointsToNode::GlobalEscape) {
      non_escaped_allocs_worklist.delete_at(next);
    }
    if (ptn->escape_state() == PointsToNode::NoEscape) {
      // Find fields in non-escaped allocations which have unknown value.
      find_init_values_phantom(ptn);
    }
  }
  return (non_escaped_allocs_worklist.length() > 0);
}

// Add all references to JavaObject node by walking over all uses.
int ConnectionGraph::add_java_object_edges(JavaObjectNode* jobj, bool populate_worklist) {
  int new_edges = 0;
  if (populate_worklist) {
    // Populate _worklist by uses of jobj's uses.
    for (UseIterator i(jobj); i.has_next(); i.next()) {
      PointsToNode* use = i.get();
      if (use->is_Arraycopy()) {
        continue;
      }
      add_uses_to_worklist(use);
      if (use->is_Field() && use->as_Field()->is_oop()) {
        // Put on worklist all field's uses (loads) and
        // related field nodes (same base and offset).
        add_field_uses_to_worklist(use->as_Field());
      }
    }
  }
  for (int l = 0; l < _worklist.length(); l++) {
    PointsToNode* use = _worklist.at(l);
    if (PointsToNode::is_base_use(use)) {
      // Add reference from jobj to field and from field to jobj (field's base).
      use = PointsToNode::get_use_node(use)->as_Field();
      if (add_base(use->as_Field(), jobj)) {
        new_edges++;
      }
      continue;
    }
    assert(!use->is_JavaObject(), "sanity");
    if (use->is_Arraycopy()) {
      if (jobj == null_obj) { // null object does not have field edges
        continue;
      }
      // Added edge from Arraycopy node to arraycopy's source java object
      if (add_edge(use, jobj)) {
        jobj->set_arraycopy_src();
        new_edges++;
      }
      // and stop here.
      continue;
    }
    if (!add_edge(use, jobj)) {
      continue; // No new edge added, there was such edge already.
    }
    new_edges++;
    if (use->is_LocalVar()) {
      add_uses_to_worklist(use);
      if (use->arraycopy_dst()) {
        for (EdgeIterator i(use); i.has_next(); i.next()) {
          PointsToNode* e = i.get();
          if (e->is_Arraycopy()) {
            if (jobj == null_obj) { // null object does not have field edges
              continue;
            }
            // Add edge from arraycopy's destination java object to Arraycopy node.
            if (add_edge(jobj, e)) {
              new_edges++;
              jobj->set_arraycopy_dst();
            }
          }
        }
      }
    } else {
      // Added new edge to stored in field values.
      // Put on worklist all field's uses (loads) and
      // related field nodes (same base and offset).
      add_field_uses_to_worklist(use->as_Field());
    }
  }
  _worklist.clear();
  _in_worklist.reset();
  return new_edges;
}

// Put on worklist all related field nodes.
void ConnectionGraph::add_field_uses_to_worklist(FieldNode* field) {
  assert(field->is_oop(), "sanity");
  int offset = field->offset();
  add_uses_to_worklist(field);
  // Loop over all bases of this field and push on worklist Field nodes
  // with the same offset and base (since they may reference the same field).
  for (BaseIterator i(field); i.has_next(); i.next()) {
    PointsToNode* base = i.get();
    add_fields_to_worklist(field, base);
    // Check if the base was source object of arraycopy and go over arraycopy's
    // destination objects since values stored to a field of source object are
    // accessible by uses (loads) of fields of destination objects.
    if (base->arraycopy_src()) {
      for (UseIterator j(base); j.has_next(); j.next()) {
        PointsToNode* arycp = j.get();
        if (arycp->is_Arraycopy()) {
          for (UseIterator k(arycp); k.has_next(); k.next()) {
            PointsToNode* abase = k.get();
            if (abase->arraycopy_dst() && abase != base) {
              // Look for the same arraycopy reference.
              add_fields_to_worklist(field, abase);
            }
          }
        }
      }
    }
  }
}

// Put on worklist all related field nodes.
void ConnectionGraph::add_fields_to_worklist(FieldNode* field, PointsToNode* base) {
  int offset = field->offset();
  if (base->is_LocalVar()) {
    for (UseIterator j(base); j.has_next(); j.next()) {
      PointsToNode* f = j.get();
      if (PointsToNode::is_base_use(f)) { // Field
        f = PointsToNode::get_use_node(f);
        if (f == field || !f->as_Field()->is_oop()) {
          continue;
        }
        int offs = f->as_Field()->offset();
        if (offs == offset || offset == Type::OffsetBot || offs == Type::OffsetBot) {
          add_to_worklist(f);
        }
      }
    }
  } else {
    assert(base->is_JavaObject(), "sanity");
    if (// Skip phantom_object since it is only used to indicate that
        // this field's content globally escapes.
        (base != phantom_obj) &&
        // null object node does not have fields.
        (base != null_obj)) {
      for (EdgeIterator i(base); i.has_next(); i.next()) {
        PointsToNode* f = i.get();
        // Skip arraycopy edge since store to destination object field
        // does not update value in source object field.
        if (f->is_Arraycopy()) {
          assert(base->arraycopy_dst(), "sanity");
          continue;
        }
        if (f == field || !f->as_Field()->is_oop()) {
          continue;
        }
        int offs = f->as_Field()->offset();
        if (offs == offset || offset == Type::OffsetBot || offs == Type::OffsetBot) {
          add_to_worklist(f);
        }
      }
    }
  }
}

// Find fields which have unknown value.
int ConnectionGraph::find_field_value(FieldNode* field) {
  // Escaped fields should have init value already.
  assert(field->escape_state() == PointsToNode::NoEscape, "sanity");
  int new_edges = 0;
  for (BaseIterator i(field); i.has_next(); i.next()) {
    PointsToNode* base = i.get();
    if (base->is_JavaObject()) {
      // Skip Allocate's fields which will be processed later.
      if (base->ideal_node()->is_Allocate()) {
        return 0;
      }
      assert(base == null_obj, "only null ptr base expected here");
    }
  }
  if (add_edge(field, phantom_obj)) {
    // New edge was added
    new_edges++;
    add_field_uses_to_worklist(field);
  }
  return new_edges;
}

// Find fields initializing values for allocations.
int ConnectionGraph::find_init_values_phantom(JavaObjectNode* pta) {
  assert(pta->escape_state() == PointsToNode::NoEscape, "Not escaped Allocate nodes only");
  Node* alloc = pta->ideal_node();

  // Do nothing for Allocate nodes since its fields values are
  // "known" unless they are initialized by arraycopy/clone.
  if (alloc->is_Allocate() && !pta->arraycopy_dst()) {
    return 0;
  }
  assert(pta->arraycopy_dst() || alloc->as_CallStaticJava(), "sanity");
#ifdef ASSERT
  if (!pta->arraycopy_dst() && alloc->as_CallStaticJava()->method() == nullptr) {
    const char* name = alloc->as_CallStaticJava()->_name;
    assert(strncmp(name, "_multianewarray", 15) == 0, "sanity");
  }
#endif
  // Non-escaped allocation returned from Java or runtime call have unknown values in fields.
  int new_edges = 0;
  for (EdgeIterator i(pta); i.has_next(); i.next()) {
    PointsToNode* field = i.get();
    if (field->is_Field() && field->as_Field()->is_oop()) {
      if (add_edge(field, phantom_obj)) {
        // New edge was added
        new_edges++;
        add_field_uses_to_worklist(field->as_Field());
      }
    }
  }
  return new_edges;
}

// Find fields initializing values for allocations.
int ConnectionGraph::find_init_values_null(JavaObjectNode* pta, PhaseValues* phase) {
  assert(pta->escape_state() == PointsToNode::NoEscape, "Not escaped Allocate nodes only");
  Node* alloc = pta->ideal_node();
  // Do nothing for Call nodes since its fields values are unknown.
  if (!alloc->is_Allocate()) {
    return 0;
  }
  InitializeNode* ini = alloc->as_Allocate()->initialization();
  bool visited_bottom_offset = false;
  GrowableArray<int> offsets_worklist;
  int new_edges = 0;

  // Check if an oop field's initializing value is recorded and add
  // a corresponding null if field's value if it is not recorded.
  // Connection Graph does not record a default initialization by null
  // captured by Initialize node.
  //
  for (EdgeIterator i(pta); i.has_next(); i.next()) {
    PointsToNode* field = i.get(); // Field (AddP)
    if (!field->is_Field() || !field->as_Field()->is_oop()) {
      continue; // Not oop field
    }
    int offset = field->as_Field()->offset();
    if (offset == Type::OffsetBot) {
      if (!visited_bottom_offset) {
        // OffsetBot is used to reference array's element,
        // always add reference to null to all Field nodes since we don't
        // known which element is referenced.
        if (add_edge(field, null_obj)) {
          // New edge was added
          new_edges++;
          add_field_uses_to_worklist(field->as_Field());
          visited_bottom_offset = true;
        }
      }
    } else {
      // Check only oop fields.
      const Type* adr_type = field->ideal_node()->as_AddP()->bottom_type();
      if (adr_type->isa_rawptr()) {
#ifdef ASSERT
        // Raw pointers are used for initializing stores so skip it
        // since it should be recorded already
        Node* base = get_addp_base(field->ideal_node());
        assert(adr_type->isa_rawptr() && is_captured_store_address(field->ideal_node()), "unexpected pointer type");
#endif
        continue;
      }
      if (!offsets_worklist.contains(offset)) {
        offsets_worklist.append(offset);
        Node* value = nullptr;
        if (ini != nullptr) {
          // StoreP::memory_type() == T_ADDRESS
          BasicType ft = UseCompressedOops ? T_NARROWOOP : T_ADDRESS;
          Node* store = ini->find_captured_store(offset, type2aelembytes(ft, true), phase);
          // Make sure initializing store has the same type as this AddP.
          // This AddP may reference non existing field because it is on a
          // dead branch of bimorphic call which is not eliminated yet.
          if (store != nullptr && store->is_Store() &&
              store->as_Store()->memory_type() == ft) {
            value = store->in(MemNode::ValueIn);
#ifdef ASSERT
            if (VerifyConnectionGraph) {
              // Verify that AddP already points to all objects the value points to.
              PointsToNode* val = ptnode_adr(value->_idx);
              assert((val != nullptr), "should be processed already");
              PointsToNode* missed_obj = nullptr;
              if (val->is_JavaObject()) {
                if (!field->points_to(val->as_JavaObject())) {
                  missed_obj = val;
                }
              } else {
                if (!val->is_LocalVar() || (val->edge_count() == 0)) {
                  tty->print_cr("----------init store has invalid value -----");
                  store->dump();
                  val->dump();
                  assert(val->is_LocalVar() && (val->edge_count() > 0), "should be processed already");
                }
                for (EdgeIterator j(val); j.has_next(); j.next()) {
                  PointsToNode* obj = j.get();
                  if (obj->is_JavaObject()) {
                    if (!field->points_to(obj->as_JavaObject())) {
                      missed_obj = obj;
                      break;
                    }
                  }
                }
              }
              if (missed_obj != nullptr) {
                tty->print_cr("----------field---------------------------------");
                field->dump();
                tty->print_cr("----------missed referernce to object-----------");
                missed_obj->dump();
                tty->print_cr("----------object referernced by init store -----");
                store->dump();
                val->dump();
                assert(!field->points_to(missed_obj->as_JavaObject()), "missed JavaObject reference");
              }
            }
#endif
          } else {
            // There could be initializing stores which follow allocation.
            // For example, a volatile field store is not collected
            // by Initialize node.
            //
            // Need to check for dependent loads to separate such stores from
            // stores which follow loads. For now, add initial value null so
            // that compare pointers optimization works correctly.
          }
        }
        if (value == nullptr) {
          // A field's initializing value was not recorded. Add null.
          if (add_edge(field, null_obj)) {
            // New edge was added
            new_edges++;
            add_field_uses_to_worklist(field->as_Field());
          }
        }
      }
    }
  }
  return new_edges;
}

// Adjust scalar_replaceable state after Connection Graph is built.
void ConnectionGraph::adjust_scalar_replaceable_state(JavaObjectNode* jobj, Unique_Node_List &reducible_merges) {
  // A Phi 'x' is a _candidate_ to be reducible if 'can_reduce_phi(x)'
  // returns true. If one of the constraints in this method set 'jobj' to NSR
  // then the candidate Phi is discarded. If the Phi has another SR 'jobj' as
  // input, 'adjust_scalar_replaceable_state' will eventually be called with
  // that other object and the Phi will become a reducible Phi.
  // There could be multiple merges involving the same jobj.
  Unique_Node_List candidates;

  // Search for non-escaping objects which are not scalar replaceable
  // and mark them to propagate the state to referenced objects.

  for (UseIterator i(jobj); i.has_next(); i.next()) {
    PointsToNode* use = i.get();
    if (use->is_Arraycopy()) {
      continue;
    }
    if (use->is_Field()) {
      FieldNode* field = use->as_Field();
      assert(field->is_oop() && field->scalar_replaceable(), "sanity");
      // 1. An object is not scalar replaceable if the field into which it is
      // stored has unknown offset (stored into unknown element of an array).
      if (field->offset() == Type::OffsetBot) {
        set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "is stored at unknown offset"));
        return;
      }
      for (BaseIterator i(field); i.has_next(); i.next()) {
        PointsToNode* base = i.get();
        // 2. An object is not scalar replaceable if the field into which it is
        // stored has multiple bases one of which is null.
        if ((base == null_obj) && (field->base_count() > 1)) {
          set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "is stored into field with potentially null base"));
          return;
        }
        // 2.5. An object is not scalar replaceable if the field into which it is
        // stored has NSR base.
        if (!base->scalar_replaceable()) {
          set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "is stored into field with NSR base"));
          return;
        }
      }
    }
    assert(use->is_Field() || use->is_LocalVar(), "sanity");
    // 3. An object is not scalar replaceable if it is merged with other objects
    // and we can't remove the merge
    for (EdgeIterator j(use); j.has_next(); j.next()) {
      PointsToNode* ptn = j.get();
      if (ptn->is_JavaObject() && ptn != jobj) {
        Node* use_n = use->ideal_node();

        // If it's already a candidate or confirmed reducible merge we can skip verification
        if (candidates.member(use_n)) {
          continue;
        } else if (reducible_merges.member(use_n)) {
          candidates.push(use_n);
          continue;
        }

        if (use_n->is_Phi() && can_reduce_phi(use_n->as_Phi())) {
          candidates.push(use_n);
        } else {
          // Mark all objects as NSR if we can't remove the merge
          set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA trace_merged_message(ptn)));
          set_not_scalar_replaceable(ptn NOT_PRODUCT(COMMA trace_merged_message(jobj)));
        }
      }
    }
    if (!jobj->scalar_replaceable()) {
      return;
    }
  }

  for (EdgeIterator j(jobj); j.has_next(); j.next()) {
    if (j.get()->is_Arraycopy()) {
      continue;
    }

    // Non-escaping object node should point only to field nodes.
    FieldNode* field = j.get()->as_Field();
    int offset = field->as_Field()->offset();

    // 4. An object is not scalar replaceable if it has a field with unknown
    // offset (array's element is accessed in loop).
    if (offset == Type::OffsetBot) {
      set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "has field with unknown offset"));
      return;
    }
    // 5. Currently an object is not scalar replaceable if a LoadStore node
    // access its field since the field value is unknown after it.
    //
    Node* n = field->ideal_node();

    // Test for an unsafe access that was parsed as maybe off heap
    // (with a CheckCastPP to raw memory).
    assert(n->is_AddP(), "expect an address computation");
    if (n->in(AddPNode::Base)->is_top() &&
        n->in(AddPNode::Address)->Opcode() == Op_CheckCastPP) {
      assert(n->in(AddPNode::Address)->bottom_type()->isa_rawptr(), "raw address so raw cast expected");
      assert(_igvn->type(n->in(AddPNode::Address)->in(1))->isa_oopptr(), "cast pattern at unsafe access expected");
      set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "is used as base of mixed unsafe access"));
      return;
    }

    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* u = n->fast_out(i);
      if (u->is_LoadStore() || (u->is_Mem() && u->as_Mem()->is_mismatched_access())) {
        set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "is used in LoadStore or mismatched access"));
        return;
      }
    }

    // 6. Or the address may point to more then one object. This may produce
    // the false positive result (set not scalar replaceable)
    // since the flow-insensitive escape analysis can't separate
    // the case when stores overwrite the field's value from the case
    // when stores happened on different control branches.
    //
    // Note: it will disable scalar replacement in some cases:
    //
    //    Point p[] = new Point[1];
    //    p[0] = new Point(); // Will be not scalar replaced
    //
    // but it will save us from incorrect optimizations in next cases:
    //
    //    Point p[] = new Point[1];
    //    if ( x ) p[0] = new Point(); // Will be not scalar replaced
    //
    if (field->base_count() > 1 && candidates.size() == 0) {
      for (BaseIterator i(field); i.has_next(); i.next()) {
        PointsToNode* base = i.get();
        // Don't take into account LocalVar nodes which
        // may point to only one object which should be also
        // this field's base by now.
        if (base->is_JavaObject() && base != jobj) {
          // Mark all bases.
          set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "may point to more than one object"));
          set_not_scalar_replaceable(base NOT_PRODUCT(COMMA "may point to more than one object"));
        }
      }

      if (!jobj->scalar_replaceable()) {
        return;
      }
    }
  }

  // The candidate is truly a reducible merge only if none of the other
  // constraints ruled it as NSR. There could be multiple merges involving the
  // same jobj.
  assert(jobj->scalar_replaceable(), "sanity");
  for (uint i = 0; i < candidates.size(); i++ ) {
    Node* candidate = candidates.at(i);
    reducible_merges.push(candidate);
  }
}

// Propagate NSR (Not scalar replaceable) state.
void ConnectionGraph::find_scalar_replaceable_allocs(GrowableArray<JavaObjectNode*>& jobj_worklist) {
  int jobj_length = jobj_worklist.length();
  bool found_nsr_alloc = true;
  while (found_nsr_alloc) {
    found_nsr_alloc = false;
    for (int next = 0; next < jobj_length; ++next) {
      JavaObjectNode* jobj = jobj_worklist.at(next);
      for (UseIterator i(jobj); (jobj->scalar_replaceable() && i.has_next()); i.next()) {
        PointsToNode* use = i.get();
        if (use->is_Field()) {
          FieldNode* field = use->as_Field();
          assert(field->is_oop() && field->scalar_replaceable(), "sanity");
          assert(field->offset() != Type::OffsetBot, "sanity");
          for (BaseIterator i(field); i.has_next(); i.next()) {
            PointsToNode* base = i.get();
            // An object is not scalar replaceable if the field into which
            // it is stored has NSR base.
            if ((base != null_obj) && !base->scalar_replaceable()) {
              set_not_scalar_replaceable(jobj NOT_PRODUCT(COMMA "is stored into field with NSR base"));
              found_nsr_alloc = true;
              break;
            }
          }
        }
      }
    }
  }
}

#ifdef ASSERT
void ConnectionGraph::verify_connection_graph(
                         GrowableArray<PointsToNode*>&   ptnodes_worklist,
                         GrowableArray<JavaObjectNode*>& non_escaped_allocs_worklist,
                         GrowableArray<JavaObjectNode*>& java_objects_worklist,
                         GrowableArray<Node*>& addp_worklist) {
  // Verify that graph is complete - no new edges could be added.
  int java_objects_length = java_objects_worklist.length();
  int non_escaped_length  = non_escaped_allocs_worklist.length();
  int new_edges = 0;
  for (int next = 0; next < java_objects_length; ++next) {
    JavaObjectNode* ptn = java_objects_worklist.at(next);
    new_edges += add_java_object_edges(ptn, true);
  }
  assert(new_edges == 0, "graph was not complete");
  // Verify that escape state is final.
  int length = non_escaped_allocs_worklist.length();
  find_non_escaped_objects(ptnodes_worklist, non_escaped_allocs_worklist);
  assert((non_escaped_length == non_escaped_allocs_worklist.length()) &&
         (non_escaped_length == length) &&
         (_worklist.length() == 0), "escape state was not final");

  // Verify fields information.
  int addp_length = addp_worklist.length();
  for (int next = 0; next < addp_length; ++next ) {
    Node* n = addp_worklist.at(next);
    FieldNode* field = ptnode_adr(n->_idx)->as_Field();
    if (field->is_oop()) {
      // Verify that field has all bases
      Node* base = get_addp_base(n);
      PointsToNode* ptn = ptnode_adr(base->_idx);
      if (ptn->is_JavaObject()) {
        assert(field->has_base(ptn->as_JavaObject()), "sanity");
      } else {
        assert(ptn->is_LocalVar(), "sanity");
        for (EdgeIterator i(ptn); i.has_next(); i.next()) {
          PointsToNode* e = i.get();
          if (e->is_JavaObject()) {
            assert(field->has_base(e->as_JavaObject()), "sanity");
          }
        }
      }
      // Verify that all fields have initializing values.
      if (field->edge_count() == 0) {
        tty->print_cr("----------field does not have references----------");
        field->dump();
        for (BaseIterator i(field); i.has_next(); i.next()) {
          PointsToNode* base = i.get();
          tty->print_cr("----------field has next base---------------------");
          base->dump();
          if (base->is_JavaObject() && (base != phantom_obj) && (base != null_obj)) {
            tty->print_cr("----------base has fields-------------------------");
            for (EdgeIterator j(base); j.has_next(); j.next()) {
              j.get()->dump();
            }
            tty->print_cr("----------base has references---------------------");
            for (UseIterator j(base); j.has_next(); j.next()) {
              j.get()->dump();
            }
          }
        }
        for (UseIterator i(field); i.has_next(); i.next()) {
          i.get()->dump();
        }
        assert(field->edge_count() > 0, "sanity");
      }
    }
  }
}
#endif

// Optimize ideal graph.
void ConnectionGraph::optimize_ideal_graph(GrowableArray<Node*>& ptr_cmp_worklist,
                                           GrowableArray<MemBarStoreStoreNode*>& storestore_worklist) {
  Compile* C = _compile;
  PhaseIterGVN* igvn = _igvn;
  if (EliminateLocks) {
    // Mark locks before changing ideal graph.
    int cnt = C->macro_count();
    for (int i = 0; i < cnt; i++) {
      Node *n = C->macro_node(i);
      if (n->is_AbstractLock()) { // Lock and Unlock nodes
        AbstractLockNode* alock = n->as_AbstractLock();
        if (!alock->is_non_esc_obj()) {
          if (can_eliminate_lock(alock)) {
            assert(!alock->is_eliminated() || alock->is_coarsened(), "sanity");
            // The lock could be marked eliminated by lock coarsening
            // code during first IGVN before EA. Replace coarsened flag
            // to eliminate all associated locks/unlocks.
#ifdef ASSERT
            alock->log_lock_optimization(C, "eliminate_lock_set_non_esc3");
#endif
            alock->set_non_esc_obj();
          }
        }
      }
    }
  }

  if (OptimizePtrCompare) {
    for (int i = 0; i < ptr_cmp_worklist.length(); i++) {
      Node *n = ptr_cmp_worklist.at(i);
      const TypeInt* tcmp = optimize_ptr_compare(n);
      if (tcmp->singleton()) {
        Node* cmp = igvn->makecon(tcmp);
#ifndef PRODUCT
        if (PrintOptimizePtrCompare) {
          tty->print_cr("++++ Replaced: %d %s(%d,%d) --> %s", n->_idx, (n->Opcode() == Op_CmpP ? "CmpP" : "CmpN"), n->in(1)->_idx, n->in(2)->_idx, (tcmp == TypeInt::CC_EQ ? "EQ" : "NotEQ"));
          if (Verbose) {
            n->dump(1);
          }
        }
#endif
        igvn->replace_node(n, cmp);
      }
    }
  }

  // For MemBarStoreStore nodes added in library_call.cpp, check
  // escape status of associated AllocateNode and optimize out
  // MemBarStoreStore node if the allocated object never escapes.
  for (int i = 0; i < storestore_worklist.length(); i++) {
    Node* storestore = storestore_worklist.at(i);
    Node* alloc = storestore->in(MemBarNode::Precedent)->in(0);
    if (alloc->is_Allocate() && not_global_escape(alloc)) {
      MemBarNode* mb = MemBarNode::make(C, Op_MemBarCPUOrder, Compile::AliasIdxBot);
      mb->init_req(TypeFunc::Memory,  storestore->in(TypeFunc::Memory));
      mb->init_req(TypeFunc::Control, storestore->in(TypeFunc::Control));
      igvn->register_new_node_with_optimizer(mb);
      igvn->replace_node(storestore, mb);
    }
  }
}

// Optimize objects compare.
const TypeInt* ConnectionGraph::optimize_ptr_compare(Node* n) {
  assert(OptimizePtrCompare, "sanity");
  assert(n->Opcode() == Op_CmpN || n->Opcode() == Op_CmpP, "must be");
  const TypeInt* EQ = TypeInt::CC_EQ; // [0] == ZERO
  const TypeInt* NE = TypeInt::CC_GT; // [1] == ONE
  const TypeInt* UNKNOWN = TypeInt::CC;    // [-1, 0,1]

  PointsToNode* ptn1 = ptnode_adr(n->in(1)->_idx);
  PointsToNode* ptn2 = ptnode_adr(n->in(2)->_idx);
  JavaObjectNode* jobj1 = unique_java_object(n->in(1));
  JavaObjectNode* jobj2 = unique_java_object(n->in(2));
  assert(ptn1->is_JavaObject() || ptn1->is_LocalVar(), "sanity");
  assert(ptn2->is_JavaObject() || ptn2->is_LocalVar(), "sanity");

  // Check simple cases first.
  if (jobj1 != nullptr) {
    if (jobj1->escape_state() == PointsToNode::NoEscape) {
      if (jobj1 == jobj2) {
        // Comparing the same not escaping object.
        return EQ;
      }
      Node* obj = jobj1->ideal_node();
      // Comparing not escaping allocation.
      if ((obj->is_Allocate() || obj->is_CallStaticJava()) &&
          !ptn2->points_to(jobj1)) {
        return NE; // This includes nullness check.
      }
    }
  }
  if (jobj2 != nullptr) {
    if (jobj2->escape_state() == PointsToNode::NoEscape) {
      Node* obj = jobj2->ideal_node();
      // Comparing not escaping allocation.
      if ((obj->is_Allocate() || obj->is_CallStaticJava()) &&
          !ptn1->points_to(jobj2)) {
        return NE; // This includes nullness check.
      }
    }
  }
  if (jobj1 != nullptr && jobj1 != phantom_obj &&
      jobj2 != nullptr && jobj2 != phantom_obj &&
      jobj1->ideal_node()->is_Con() &&
      jobj2->ideal_node()->is_Con()) {
    // Klass or String constants compare. Need to be careful with
    // compressed pointers - compare types of ConN and ConP instead of nodes.
    const Type* t1 = jobj1->ideal_node()->get_ptr_type();
    const Type* t2 = jobj2->ideal_node()->get_ptr_type();
    if (t1->make_ptr() == t2->make_ptr()) {
      return EQ;
    } else {
      return NE;
    }
  }
  if (ptn1->meet(ptn2)) {
    return UNKNOWN; // Sets are not disjoint
  }

  // Sets are disjoint.
  bool set1_has_unknown_ptr = ptn1->points_to(phantom_obj);
  bool set2_has_unknown_ptr = ptn2->points_to(phantom_obj);
  bool set1_has_null_ptr    = ptn1->points_to(null_obj);
  bool set2_has_null_ptr    = ptn2->points_to(null_obj);
  if ((set1_has_unknown_ptr && set2_has_null_ptr) ||
      (set2_has_unknown_ptr && set1_has_null_ptr)) {
    // Check nullness of unknown object.
    return UNKNOWN;
  }

  // Disjointness by itself is not sufficient since
  // alias analysis is not complete for escaped objects.
  // Disjoint sets are definitely unrelated only when
  // at least one set has only not escaping allocations.
  if (!set1_has_unknown_ptr && !set1_has_null_ptr) {
    if (ptn1->non_escaping_allocation()) {
      return NE;
    }
  }
  if (!set2_has_unknown_ptr && !set2_has_null_ptr) {
    if (ptn2->non_escaping_allocation()) {
      return NE;
    }
  }
  return UNKNOWN;
}

// Connection Graph construction functions.

void ConnectionGraph::add_local_var(Node *n, PointsToNode::EscapeState es) {
  PointsToNode* ptadr = _nodes.at(n->_idx);
  if (ptadr != nullptr) {
    assert(ptadr->is_LocalVar() && ptadr->ideal_node() == n, "sanity");
    return;
  }
  Compile* C = _compile;
  ptadr = new (C->comp_arena()) LocalVarNode(this, n, es);
  map_ideal_node(n, ptadr);
}

PointsToNode* ConnectionGraph::add_java_object(Node *n, PointsToNode::EscapeState es) {
  PointsToNode* ptadr = _nodes.at(n->_idx);
  if (ptadr != nullptr) {
    assert(ptadr->is_JavaObject() && ptadr->ideal_node() == n, "sanity");
    return ptadr;
  }
  Compile* C = _compile;
  ptadr = new (C->comp_arena()) JavaObjectNode(this, n, es);
  map_ideal_node(n, ptadr);
  return ptadr;
}

void ConnectionGraph::add_field(Node *n, PointsToNode::EscapeState es, int offset) {
  PointsToNode* ptadr = _nodes.at(n->_idx);
  if (ptadr != nullptr) {
    assert(ptadr->is_Field() && ptadr->ideal_node() == n, "sanity");
    return;
  }
  bool unsafe = false;
  bool is_oop = is_oop_field(n, offset, &unsafe);
  if (unsafe) {
    es = PointsToNode::GlobalEscape;
  }
  Compile* C = _compile;
  FieldNode* field = new (C->comp_arena()) FieldNode(this, n, es, offset, is_oop);
  map_ideal_node(n, field);
}

void ConnectionGraph::add_arraycopy(Node *n, PointsToNode::EscapeState es,
                                    PointsToNode* src, PointsToNode* dst) {
  assert(!src->is_Field() && !dst->is_Field(), "only for JavaObject and LocalVar");
  assert((src != null_obj) && (dst != null_obj), "not for ConP null");
  PointsToNode* ptadr = _nodes.at(n->_idx);
  if (ptadr != nullptr) {
    assert(ptadr->is_Arraycopy() && ptadr->ideal_node() == n, "sanity");
    return;
  }
  Compile* C = _compile;
  ptadr = new (C->comp_arena()) ArraycopyNode(this, n, es);
  map_ideal_node(n, ptadr);
  // Add edge from arraycopy node to source object.
  (void)add_edge(ptadr, src);
  src->set_arraycopy_src();
  // Add edge from destination object to arraycopy node.
  (void)add_edge(dst, ptadr);
  dst->set_arraycopy_dst();
}

bool ConnectionGraph::is_oop_field(Node* n, int offset, bool* unsafe) {
  const Type* adr_type = n->as_AddP()->bottom_type();
  BasicType bt = T_INT;
  if (offset == Type::OffsetBot) {
    // Check only oop fields.
    if (!adr_type->isa_aryptr() ||
        adr_type->isa_aryptr()->elem() == Type::BOTTOM ||
        adr_type->isa_aryptr()->elem()->make_oopptr() != nullptr) {
      // OffsetBot is used to reference array's element. Ignore first AddP.
      if (find_second_addp(n, n->in(AddPNode::Base)) == nullptr) {
        bt = T_OBJECT;
      }
    }
  } else if (offset != oopDesc::klass_offset_in_bytes()) {
    if (adr_type->isa_instptr()) {
      ciField* field = _compile->alias_type(adr_type->isa_instptr())->field();
      if (field != nullptr) {
        bt = field->layout_type();
      } else {
        // Check for unsafe oop field access
        if (n->has_out_with(Op_StoreP, Op_LoadP, Op_StoreN, Op_LoadN) ||
            n->has_out_with(Op_GetAndSetP, Op_GetAndSetN, Op_CompareAndExchangeP, Op_CompareAndExchangeN) ||
            n->has_out_with(Op_CompareAndSwapP, Op_CompareAndSwapN, Op_WeakCompareAndSwapP, Op_WeakCompareAndSwapN) ||
            BarrierSet::barrier_set()->barrier_set_c2()->escape_has_out_with_unsafe_object(n)) {
          bt = T_OBJECT;
          (*unsafe) = true;
        }
      }
    } else if (adr_type->isa_aryptr()) {
      if (offset == arrayOopDesc::length_offset_in_bytes()) {
        // Ignore array length load.
      } else if (find_second_addp(n, n->in(AddPNode::Base)) != nullptr) {
        // Ignore first AddP.
      } else {
        const Type* elemtype = adr_type->isa_aryptr()->elem();
        bt = elemtype->array_element_basic_type();
      }
    } else if (adr_type->isa_rawptr() || adr_type->isa_klassptr()) {
      // Allocation initialization, ThreadLocal field access, unsafe access
      if (n->has_out_with(Op_StoreP, Op_LoadP, Op_StoreN, Op_LoadN) ||
          n->has_out_with(Op_GetAndSetP, Op_GetAndSetN, Op_CompareAndExchangeP, Op_CompareAndExchangeN) ||
          n->has_out_with(Op_CompareAndSwapP, Op_CompareAndSwapN, Op_WeakCompareAndSwapP, Op_WeakCompareAndSwapN) ||
          BarrierSet::barrier_set()->barrier_set_c2()->escape_has_out_with_unsafe_object(n)) {
        bt = T_OBJECT;
      }
    }
  }
  // Note: T_NARROWOOP is not classed as a real reference type
  return (is_reference_type(bt) || bt == T_NARROWOOP);
}

// Returns unique pointed java object or null.
JavaObjectNode* ConnectionGraph::unique_java_object(Node *n) const {
  // If the node was created after the escape computation we can't answer.
  uint idx = n->_idx;
  if (idx >= nodes_size()) {
    return nullptr;
  }
  PointsToNode* ptn = ptnode_adr(idx);
  if (ptn == nullptr) {
    return nullptr;
  }
  if (ptn->is_JavaObject()) {
    return ptn->as_JavaObject();
  }
  assert(ptn->is_LocalVar(), "sanity");
  // Check all java objects it points to.
  JavaObjectNode* jobj = nullptr;
  for (EdgeIterator i(ptn); i.has_next(); i.next()) {
    PointsToNode* e = i.get();
    if (e->is_JavaObject()) {
      if (jobj == nullptr) {
        jobj = e->as_JavaObject();
      } else if (jobj != e) {
        return nullptr;
      }
    }
  }
  return jobj;
}

// Return true if this node points only to non-escaping allocations.
bool PointsToNode::non_escaping_allocation() {
  if (is_JavaObject()) {
    Node* n = ideal_node();
    if (n->is_Allocate() || n->is_CallStaticJava()) {
      return (escape_state() == PointsToNode::NoEscape);
    } else {
      return false;
    }
  }
  assert(is_LocalVar(), "sanity");
  // Check all java objects it points to.
  for (EdgeIterator i(this); i.has_next(); i.next()) {
    PointsToNode* e = i.get();
    if (e->is_JavaObject()) {
      Node* n = e->ideal_node();
      if ((e->escape_state() != PointsToNode::NoEscape) ||
          !(n->is_Allocate() || n->is_CallStaticJava())) {
        return false;
      }
    }
  }
  return true;
}

// Return true if we know the node does not escape globally.
bool ConnectionGraph::not_global_escape(Node *n) {
  assert(!_collecting, "should not call during graph construction");
  // If the node was created after the escape computation we can't answer.
  uint idx = n->_idx;
  if (idx >= nodes_size()) {
    return false;
  }
  PointsToNode* ptn = ptnode_adr(idx);
  if (ptn == nullptr) {
    return false; // not in congraph (e.g. ConI)
  }
  PointsToNode::EscapeState es = ptn->escape_state();
  // If we have already computed a value, return it.
  if (es >= PointsToNode::GlobalEscape) {
    return false;
  }
  if (ptn->is_JavaObject()) {
    return true; // (es < PointsToNode::GlobalEscape);
  }
  assert(ptn->is_LocalVar(), "sanity");
  // Check all java objects it points to.
  for (EdgeIterator i(ptn); i.has_next(); i.next()) {
    if (i.get()->escape_state() >= PointsToNode::GlobalEscape) {
      return false;
    }
  }
  return true;
}

// Return true if locked object does not escape globally
// and locked code region (identified by BoxLockNode) is balanced:
// all compiled code paths have corresponding Lock/Unlock pairs.
bool ConnectionGraph::can_eliminate_lock(AbstractLockNode* alock) {
  BoxLockNode* box = alock->box_node()->as_BoxLock();
  if (!box->is_unbalanced() && not_global_escape(alock->obj_node())) {
    if (EliminateNestedLocks) {
      // We can mark whole locking region as Local only when only
      // one object is used for locking.
      box->set_local();
    }
    return true;
  }
  return false;
}

// Helper functions

// Return true if this node points to specified node or nodes it points to.
bool PointsToNode::points_to(JavaObjectNode* ptn) const {
  if (is_JavaObject()) {
    return (this == ptn);
  }
  assert(is_LocalVar() || is_Field(), "sanity");
  for (EdgeIterator i(this); i.has_next(); i.next()) {
    if (i.get() == ptn) {
      return true;
    }
  }
  return false;
}

// Return true if one node points to an other.
bool PointsToNode::meet(PointsToNode* ptn) {
  if (this == ptn) {
    return true;
  } else if (ptn->is_JavaObject()) {
    return this->points_to(ptn->as_JavaObject());
  } else if (this->is_JavaObject()) {
    return ptn->points_to(this->as_JavaObject());
  }
  assert(this->is_LocalVar() && ptn->is_LocalVar(), "sanity");
  int ptn_count =  ptn->edge_count();
  for (EdgeIterator i(this); i.has_next(); i.next()) {
    PointsToNode* this_e = i.get();
    for (int j = 0; j < ptn_count; j++) {
      if (this_e == ptn->edge(j)) {
        return true;
      }
    }
  }
  return false;
}

#ifdef ASSERT
// Return true if bases point to this java object.
bool FieldNode::has_base(JavaObjectNode* jobj) const {
  for (BaseIterator i(this); i.has_next(); i.next()) {
    if (i.get() == jobj) {
      return true;
    }
  }
  return false;
}
#endif

bool ConnectionGraph::is_captured_store_address(Node* addp) {
  // Handle simple case first.
  assert(_igvn->type(addp)->isa_oopptr() == nullptr, "should be raw access");
  if (addp->in(AddPNode::Address)->is_Proj() && addp->in(AddPNode::Address)->in(0)->is_Allocate()) {
    return true;
  } else if (addp->in(AddPNode::Address)->is_Phi()) {
    for (DUIterator_Fast imax, i = addp->fast_outs(imax); i < imax; i++) {
      Node* addp_use = addp->fast_out(i);
      if (addp_use->is_Store()) {
        for (DUIterator_Fast jmax, j = addp_use->fast_outs(jmax); j < jmax; j++) {
          if (addp_use->fast_out(j)->is_Initialize()) {
            return true;
          }
        }
      }
    }
  }
  return false;
}

int ConnectionGraph::address_offset(Node* adr, PhaseValues* phase) {
  const Type *adr_type = phase->type(adr);
  if (adr->is_AddP() && adr_type->isa_oopptr() == nullptr && is_captured_store_address(adr)) {
    // We are computing a raw address for a store captured by an Initialize
    // compute an appropriate address type. AddP cases #3 and #5 (see below).
    int offs = (int)phase->find_intptr_t_con(adr->in(AddPNode::Offset), Type::OffsetBot);
    assert(offs != Type::OffsetBot ||
           adr->in(AddPNode::Address)->in(0)->is_AllocateArray(),
           "offset must be a constant or it is initialization of array");
    return offs;
  }
  const TypePtr *t_ptr = adr_type->isa_ptr();
  assert(t_ptr != nullptr, "must be a pointer type");
  return t_ptr->offset();
}

Node* ConnectionGraph::get_addp_base(Node *addp) {
  assert(addp->is_AddP(), "must be AddP");
  //
  // AddP cases for Base and Address inputs:
  // case #1. Direct object's field reference:
  //     Allocate
  //       |
  //     Proj #5 ( oop result )
  //       |
  //     CheckCastPP (cast to instance type)
  //      | |
  //     AddP  ( base == address )
  //
  // case #2. Indirect object's field reference:
  //      Phi
  //       |
  //     CastPP (cast to instance type)
  //      | |
  //     AddP  ( base == address )
  //
  // case #3. Raw object's field reference for Initialize node:
  //      Allocate
  //        |
  //      Proj #5 ( oop result )
  //  top   |
  //     \  |
  //     AddP  ( base == top )
  //
  // case #4. Array's element reference:
  //   {CheckCastPP | CastPP}
  //     |  | |
  //     |  AddP ( array's element offset )
  //     |  |
  //     AddP ( array's offset )
  //
  // case #5. Raw object's field reference for arraycopy stub call:
  //          The inline_native_clone() case when the arraycopy stub is called
  //          after the allocation before Initialize and CheckCastPP nodes.
  //      Allocate
  //        |
  //      Proj #5 ( oop result )
  //       | |
  //       AddP  ( base == address )
  //
  // case #6. Constant Pool, ThreadLocal, CastX2P or
  //          Raw object's field reference:
  //      {ConP, ThreadLocal, CastX2P, raw Load}
  //  top   |
  //     \  |
  //     AddP  ( base == top )
  //
  // case #7. Klass's field reference.
  //      LoadKlass
  //       | |
  //       AddP  ( base == address )
  //
  // case #8. narrow Klass's field reference.
  //      LoadNKlass
  //       |
  //      DecodeN
  //       | |
  //       AddP  ( base == address )
  //
  // case #9. Mixed unsafe access
  //    {instance}
  //        |
  //      CheckCastPP (raw)
  //  top   |
  //     \  |
  //     AddP  ( base == top )
  //
  Node *base = addp->in(AddPNode::Base);
  if (base->uncast()->is_top()) { // The AddP case #3 and #6 and #9.
    base = addp->in(AddPNode::Address);
    while (base->is_AddP()) {
      // Case #6 (unsafe access) may have several chained AddP nodes.
      assert(base->in(AddPNode::Base)->uncast()->is_top(), "expected unsafe access address only");
      base = base->in(AddPNode::Address);
    }
    if (base->Opcode() == Op_CheckCastPP &&
        base->bottom_type()->isa_rawptr() &&
        _igvn->type(base->in(1))->isa_oopptr()) {
      base = base->in(1); // Case #9
    } else {
      Node* uncast_base = base->uncast();
      int opcode = uncast_base->Opcode();
      assert(opcode == Op_ConP || opcode == Op_ThreadLocal ||
             opcode == Op_CastX2P || uncast_base->is_DecodeNarrowPtr() ||
             (uncast_base->is_Mem() && (uncast_base->bottom_type()->isa_rawptr() != nullptr)) ||
             is_captured_store_address(addp), "sanity");
    }
  }
  return base;
}

Node* ConnectionGraph::find_second_addp(Node* addp, Node* n) {
  assert(addp->is_AddP() && addp->outcnt() > 0, "Don't process dead nodes");
  Node* addp2 = addp->raw_out(0);
  if (addp->outcnt() == 1 && addp2->is_AddP() &&
      addp2->in(AddPNode::Base) == n &&
      addp2->in(AddPNode::Address) == addp) {
    assert(addp->in(AddPNode::Base) == n, "expecting the same base");
    //
    // Find array's offset to push it on worklist first and
    // as result process an array's element offset first (pushed second)
    // to avoid CastPP for the array's offset.
    // Otherwise the inserted CastPP (LocalVar) will point to what
    // the AddP (Field) points to. Which would be wrong since
    // the algorithm expects the CastPP has the same point as
    // as AddP's base CheckCastPP (LocalVar).
    //
    //    ArrayAllocation
    //     |
    //    CheckCastPP
    //     |
    //    memProj (from ArrayAllocation CheckCastPP)
    //     |  ||
    //     |  ||   Int (element index)
    //     |  ||    |   ConI (log(element size))
    //     |  ||    |   /
    //     |  ||   LShift
    //     |  ||  /
    //     |  AddP (array's element offset)
    //     |  |
    //     |  | ConI (array's offset: #12(32-bits) or #24(64-bits))
    //     | / /
    //     AddP (array's offset)
    //      |
    //     Load/Store (memory operation on array's element)
    //
    return addp2;
  }
  return nullptr;
}

//
// Adjust the type and inputs of an AddP which computes the
// address of a field of an instance
//
bool ConnectionGraph::split_AddP(Node *addp, Node *base) {
  PhaseGVN* igvn = _igvn;
  const TypeOopPtr *base_t = igvn->type(base)->isa_oopptr();
  assert(base_t != nullptr && base_t->is_known_instance(), "expecting instance oopptr");
  const TypeOopPtr *t = igvn->type(addp)->isa_oopptr();
  if (t == nullptr) {
    // We are computing a raw address for a store captured by an Initialize
    // compute an appropriate address type (cases #3 and #5).
    assert(igvn->type(addp) == TypeRawPtr::NOTNULL, "must be raw pointer");
    assert(addp->in(AddPNode::Address)->is_Proj(), "base of raw address must be result projection from allocation");
    intptr_t offs = (int)igvn->find_intptr_t_con(addp->in(AddPNode::Offset), Type::OffsetBot);
    assert(offs != Type::OffsetBot, "offset must be a constant");
    t = base_t->add_offset(offs)->is_oopptr();
  }
  int inst_id =  base_t->instance_id();
  assert(!t->is_known_instance() || t->instance_id() == inst_id,
                             "old type must be non-instance or match new type");

  // The type 't' could be subclass of 'base_t'.
  // As result t->offset() could be large then base_t's size and it will
  // cause the failure in add_offset() with narrow oops since TypeOopPtr()
  // constructor verifies correctness of the offset.
  //
  // It could happened on subclass's branch (from the type profiling
  // inlining) which was not eliminated during parsing since the exactness
  // of the allocation type was not propagated to the subclass type check.
  //
  // Or the type 't' could be not related to 'base_t' at all.
  // It could happened when CHA type is different from MDO type on a dead path
  // (for example, from instanceof check) which is not collapsed during parsing.
  //
  // Do nothing for such AddP node and don't process its users since
  // this code branch will go away.
  //
  if (!t->is_known_instance() &&
      !base_t->maybe_java_subtype_of(t)) {
     return false; // bail out
  }
  const TypeOopPtr *tinst = base_t->add_offset(t->offset())->is_oopptr();
  // Do NOT remove the next line: ensure a new alias index is allocated
  // for the instance type. Note: C++ will not remove it since the call
  // has side effect.
  int alias_idx = _compile->get_alias_index(tinst);
  igvn->set_type(addp, tinst);
  // record the allocation in the node map
  set_map(addp, get_map(base->_idx));
  // Set addp's Base and Address to 'base'.
  Node *abase = addp->in(AddPNode::Base);
  Node *adr   = addp->in(AddPNode::Address);
  if (adr->is_Proj() && adr->in(0)->is_Allocate() &&
      adr->in(0)->_idx == (uint)inst_id) {
    // Skip AddP cases #3 and #5.
  } else {
    assert(!abase->is_top(), "sanity"); // AddP case #3
    if (abase != base) {
      igvn->hash_delete(addp);
      addp->set_req(AddPNode::Base, base);
      if (abase == adr) {
        addp->set_req(AddPNode::Address, base);
      } else {
        // AddP case #4 (adr is array's element offset AddP node)
#ifdef ASSERT
        const TypeOopPtr *atype = igvn->type(adr)->isa_oopptr();
        assert(adr->is_AddP() && atype != nullptr &&
               atype->instance_id() == inst_id, "array's element offset should be processed first");
#endif
      }
      igvn->hash_insert(addp);
    }
  }
  // Put on IGVN worklist since at least addp's type was changed above.
  record_for_optimizer(addp);
  return true;
}

//
// Create a new version of orig_phi if necessary. Returns either the newly
// created phi or an existing phi.  Sets create_new to indicate whether a new
// phi was created.  Cache the last newly created phi in the node map.
//
PhiNode *ConnectionGraph::create_split_phi(PhiNode *orig_phi, int alias_idx, GrowableArray<PhiNode *>  &orig_phi_worklist, bool &new_created) {
  Compile *C = _compile;
  PhaseGVN* igvn = _igvn;
  new_created = false;
  int phi_alias_idx = C->get_alias_index(orig_phi->adr_type());
  // nothing to do if orig_phi is bottom memory or matches alias_idx
  if (phi_alias_idx == alias_idx) {
    return orig_phi;
  }
  // Have we recently created a Phi for this alias index?
  PhiNode *result = get_map_phi(orig_phi->_idx);
  if (result != nullptr && C->get_alias_index(result->adr_type()) == alias_idx) {
    return result;
  }
  // Previous check may fail when the same wide memory Phi was split into Phis
  // for different memory slices. Search all Phis for this region.
  if (result != nullptr) {
    Node* region = orig_phi->in(0);
    for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
      Node* phi = region->fast_out(i);
      if (phi->is_Phi() &&
          C->get_alias_index(phi->as_Phi()->adr_type()) == alias_idx) {
        assert(phi->_idx >= nodes_size(), "only new Phi per instance memory slice");
        return phi->as_Phi();
      }
    }
  }
  if (C->live_nodes() + 2*NodeLimitFudgeFactor > C->max_node_limit()) {
    if (C->do_escape_analysis() == true && !C->failing()) {
      // Retry compilation without escape analysis.
      // If this is the first failure, the sentinel string will "stick"
      // to the Compile object, and the C2Compiler will see it and retry.
      C->record_failure(_invocation > 0 ? C2Compiler::retry_no_iterative_escape_analysis() : C2Compiler::retry_no_escape_analysis());
    }
    return nullptr;
  }
  orig_phi_worklist.append_if_missing(orig_phi);
  const TypePtr *atype = C->get_adr_type(alias_idx);
  result = PhiNode::make(orig_phi->in(0), nullptr, Type::MEMORY, atype);
  C->copy_node_notes_to(result, orig_phi);
  igvn->set_type(result, result->bottom_type());
  record_for_optimizer(result);
  set_map(orig_phi, result);
  new_created = true;
  return result;
}

//
// Return a new version of Memory Phi "orig_phi" with the inputs having the
// specified alias index.
//
PhiNode *ConnectionGraph::split_memory_phi(PhiNode *orig_phi, int alias_idx, GrowableArray<PhiNode *>  &orig_phi_worklist) {
  assert(alias_idx != Compile::AliasIdxBot, "can't split out bottom memory");
  Compile *C = _compile;
  PhaseGVN* igvn = _igvn;
  bool new_phi_created;
  PhiNode *result = create_split_phi(orig_phi, alias_idx, orig_phi_worklist, new_phi_created);
  if (!new_phi_created) {
    return result;
  }
  GrowableArray<PhiNode *>  phi_list;
  GrowableArray<uint>  cur_input;
  PhiNode *phi = orig_phi;
  uint idx = 1;
  bool finished = false;
  while(!finished) {
    while (idx < phi->req()) {
      Node *mem = find_inst_mem(phi->in(idx), alias_idx, orig_phi_worklist);
      if (mem != nullptr && mem->is_Phi()) {
        PhiNode *newphi = create_split_phi(mem->as_Phi(), alias_idx, orig_phi_worklist, new_phi_created);
        if (new_phi_created) {
          // found an phi for which we created a new split, push current one on worklist and begin
          // processing new one
          phi_list.push(phi);
          cur_input.push(idx);
          phi = mem->as_Phi();
          result = newphi;
          idx = 1;
          continue;
        } else {
          mem = newphi;
        }
      }
      if (C->failing()) {
        return nullptr;
      }
      result->set_req(idx++, mem);
    }
#ifdef ASSERT
    // verify that the new Phi has an input for each input of the original
    assert( phi->req() == result->req(), "must have same number of inputs.");
    assert( result->in(0) != nullptr && result->in(0) == phi->in(0), "regions must match");
#endif
    // Check if all new phi's inputs have specified alias index.
    // Otherwise use old phi.
    for (uint i = 1; i < phi->req(); i++) {
      Node* in = result->in(i);
      assert((phi->in(i) == nullptr) == (in == nullptr), "inputs must correspond.");
    }
    // we have finished processing a Phi, see if there are any more to do
    finished = (phi_list.length() == 0 );
    if (!finished) {
      phi = phi_list.pop();
      idx = cur_input.pop();
      PhiNode *prev_result = get_map_phi(phi->_idx);
      prev_result->set_req(idx++, result);
      result = prev_result;
    }
  }
  return result;
}

//
// The next methods are derived from methods in MemNode.
//
Node* ConnectionGraph::step_through_mergemem(MergeMemNode *mmem, int alias_idx, const TypeOopPtr *toop) {
  Node *mem = mmem;
  // TypeOopPtr::NOTNULL+any is an OOP with unknown offset - generally
  // means an array I have not precisely typed yet.  Do not do any
  // alias stuff with it any time soon.
  if (toop->base() != Type::AnyPtr &&
      !(toop->isa_instptr() &&
        toop->is_instptr()->instance_klass()->is_java_lang_Object() &&
        toop->offset() == Type::OffsetBot)) {
    mem = mmem->memory_at(alias_idx);
    // Update input if it is progress over what we have now
  }
  return mem;
}

//
// Move memory users to their memory slices.
//
void ConnectionGraph::move_inst_mem(Node* n, GrowableArray<PhiNode *>  &orig_phis) {
  Compile* C = _compile;
  PhaseGVN* igvn = _igvn;
  const TypePtr* tp = igvn->type(n->in(MemNode::Address))->isa_ptr();
  assert(tp != nullptr, "ptr type");
  int alias_idx = C->get_alias_index(tp);
  int general_idx = C->get_general_index(alias_idx);

  // Move users first
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* use = n->fast_out(i);
    if (use->is_MergeMem()) {
      MergeMemNode* mmem = use->as_MergeMem();
      assert(n == mmem->memory_at(alias_idx), "should be on instance memory slice");
      if (n != mmem->memory_at(general_idx) || alias_idx == general_idx) {
        continue; // Nothing to do
      }
      // Replace previous general reference to mem node.
      uint orig_uniq = C->unique();
      Node* m = find_inst_mem(n, general_idx, orig_phis);
      assert(orig_uniq == C->unique(), "no new nodes");
      mmem->set_memory_at(general_idx, m);
      --imax;
      --i;
    } else if (use->is_MemBar()) {
      assert(!use->is_Initialize(), "initializing stores should not be moved");
      if (use->req() > MemBarNode::Precedent &&
          use->in(MemBarNode::Precedent) == n) {
        // Don't move related membars.
        record_for_optimizer(use);
        continue;
      }
      tp = use->as_MemBar()->adr_type()->isa_ptr();
      if ((tp != nullptr && C->get_alias_index(tp) == alias_idx) ||
          alias_idx == general_idx) {
        continue; // Nothing to do
      }
      // Move to general memory slice.
      uint orig_uniq = C->unique();
      Node* m = find_inst_mem(n, general_idx, orig_phis);
      assert(orig_uniq == C->unique(), "no new nodes");
      igvn->hash_delete(use);
      imax -= use->replace_edge(n, m, igvn);
      igvn->hash_insert(use);
      record_for_optimizer(use);
      --i;
#ifdef ASSERT
    } else if (use->is_Mem()) {
      if (use->Opcode() == Op_StoreCM && use->in(MemNode::OopStore) == n) {
        // Don't move related cardmark.
        continue;
      }
      // Memory nodes should have new memory input.
      tp = igvn->type(use->in(MemNode::Address))->isa_ptr();
      assert(tp != nullptr, "ptr type");
      int idx = C->get_alias_index(tp);
      assert(get_map(use->_idx) != nullptr || idx == alias_idx,
             "Following memory nodes should have new memory input or be on the same memory slice");
    } else if (use->is_Phi()) {
      // Phi nodes should be split and moved already.
      tp = use->as_Phi()->adr_type()->isa_ptr();
      assert(tp != nullptr, "ptr type");
      int idx = C->get_alias_index(tp);
      assert(idx == alias_idx, "Following Phi nodes should be on the same memory slice");
    } else {
      use->dump();
      assert(false, "should not be here");
#endif
    }
  }
}

//
// Search memory chain of "mem" to find a MemNode whose address
// is the specified alias index.
//
Node* ConnectionGraph::find_inst_mem(Node *orig_mem, int alias_idx, GrowableArray<PhiNode *>  &orig_phis) {
  if (orig_mem == nullptr) {
    return orig_mem;
  }
  Compile* C = _compile;
  PhaseGVN* igvn = _igvn;
  const TypeOopPtr *toop = C->get_adr_type(alias_idx)->isa_oopptr();
  bool is_instance = (toop != nullptr) && toop->is_known_instance();
  Node *start_mem = C->start()->proj_out_or_null(TypeFunc::Memory);
  Node *prev = nullptr;
  Node *result = orig_mem;
  while (prev != result) {
    prev = result;
    if (result == start_mem) {
      break;  // hit one of our sentinels
    }
    if (result->is_Mem()) {
      const Type *at = igvn->type(result->in(MemNode::Address));
      if (at == Type::TOP) {
        break; // Dead
      }
      assert (at->isa_ptr() != nullptr, "pointer type required.");
      int idx = C->get_alias_index(at->is_ptr());
      if (idx == alias_idx) {
        break; // Found
      }
      if (!is_instance && (at->isa_oopptr() == nullptr ||
                           !at->is_oopptr()->is_known_instance())) {
        break; // Do not skip store to general memory slice.
      }
      result = result->in(MemNode::Memory);
    }
    if (!is_instance) {
      continue;  // don't search further for non-instance types
    }
    // skip over a call which does not affect this memory slice
    if (result->is_Proj() && result->as_Proj()->_con == TypeFunc::Memory) {
      Node *proj_in = result->in(0);
      if (proj_in->is_Allocate() && proj_in->_idx == (uint)toop->instance_id()) {
        break;  // hit one of our sentinels
      } else if (proj_in->is_Call()) {
        // ArrayCopy node processed here as well
        CallNode *call = proj_in->as_Call();
        if (!call->may_modify(toop, igvn)) {
          result = call->in(TypeFunc::Memory);
        }
      } else if (proj_in->is_Initialize()) {
        AllocateNode* alloc = proj_in->as_Initialize()->allocation();
        // Stop if this is the initialization for the object instance which
        // which contains this memory slice, otherwise skip over it.
        if (alloc == nullptr || alloc->_idx != (uint)toop->instance_id()) {
          result = proj_in->in(TypeFunc::Memory);
        }
      } else if (proj_in->is_MemBar()) {
        // Check if there is an array copy for a clone
        // Step over GC barrier when ReduceInitialCardMarks is disabled
        BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
        Node* control_proj_ac = bs->step_over_gc_barrier(proj_in->in(0));

        if (control_proj_ac->is_Proj() && control_proj_ac->in(0)->is_ArrayCopy()) {
          // Stop if it is a clone
          ArrayCopyNode* ac = control_proj_ac->in(0)->as_ArrayCopy();
          if (ac->may_modify(toop, igvn)) {
            break;
          }
        }
        result = proj_in->in(TypeFunc::Memory);
      }
    } else if (result->is_MergeMem()) {
      MergeMemNode *mmem = result->as_MergeMem();
      result = step_through_mergemem(mmem, alias_idx, toop);
      if (result == mmem->base_memory()) {
        // Didn't find instance memory, search through general slice recursively.
        result = mmem->memory_at(C->get_general_index(alias_idx));
        result = find_inst_mem(result, alias_idx, orig_phis);
        if (C->failing()) {
          return nullptr;
        }
        mmem->set_memory_at(alias_idx, result);
      }
    } else if (result->is_Phi() &&
               C->get_alias_index(result->as_Phi()->adr_type()) != alias_idx) {
      Node *un = result->as_Phi()->unique_input(igvn);
      if (un != nullptr) {
        orig_phis.append_if_missing(result->as_Phi());
        result = un;
      } else {
        break;
      }
    } else if (result->is_ClearArray()) {
      if (!ClearArrayNode::step_through(&result, (uint)toop->instance_id(), igvn)) {
        // Can not bypass initialization of the instance
        // we are looking for.
        break;
      }
      // Otherwise skip it (the call updated 'result' value).
    } else if (result->Opcode() == Op_SCMemProj) {
      Node* mem = result->in(0);
      Node* adr = nullptr;
      if (mem->is_LoadStore()) {
        adr = mem->in(MemNode::Address);
      } else {
        assert(mem->Opcode() == Op_EncodeISOArray ||
               mem->Opcode() == Op_StrCompressedCopy, "sanity");
        adr = mem->in(3); // Memory edge corresponds to destination array
      }
      const Type *at = igvn->type(adr);
      if (at != Type::TOP) {
        assert(at->isa_ptr() != nullptr, "pointer type required.");
        int idx = C->get_alias_index(at->is_ptr());
        if (idx == alias_idx) {
          // Assert in debug mode
          assert(false, "Object is not scalar replaceable if a LoadStore node accesses its field");
          break; // In product mode return SCMemProj node
        }
      }
      result = mem->in(MemNode::Memory);
    } else if (result->Opcode() == Op_StrInflatedCopy) {
      Node* adr = result->in(3); // Memory edge corresponds to destination array
      const Type *at = igvn->type(adr);
      if (at != Type::TOP) {
        assert(at->isa_ptr() != nullptr, "pointer type required.");
        int idx = C->get_alias_index(at->is_ptr());
        if (idx == alias_idx) {
          // Assert in debug mode
          assert(false, "Object is not scalar replaceable if a StrInflatedCopy node accesses its field");
          break; // In product mode return SCMemProj node
        }
      }
      result = result->in(MemNode::Memory);
    }
  }
  if (result->is_Phi()) {
    PhiNode *mphi = result->as_Phi();
    assert(mphi->bottom_type() == Type::MEMORY, "memory phi required");
    const TypePtr *t = mphi->adr_type();
    if (!is_instance) {
      // Push all non-instance Phis on the orig_phis worklist to update inputs
      // during Phase 4 if needed.
      orig_phis.append_if_missing(mphi);
    } else if (C->get_alias_index(t) != alias_idx) {
      // Create a new Phi with the specified alias index type.
      result = split_memory_phi(mphi, alias_idx, orig_phis);
    }
  }
  // the result is either MemNode, PhiNode, InitializeNode.
  return result;
}

//
//  Convert the types of non-escaped object to instance types where possible,
//  propagate the new type information through the graph, and update memory
//  edges and MergeMem inputs to reflect the new type.
//
//  We start with allocations (and calls which may be allocations)  on alloc_worklist.
//  The processing is done in 4 phases:
//
//  Phase 1:  Process possible allocations from alloc_worklist.  Create instance
//            types for the CheckCastPP for allocations where possible.
//            Propagate the new types through users as follows:
//               casts and Phi:  push users on alloc_worklist
//               AddP:  cast Base and Address inputs to the instance type
//                      push any AddP users on alloc_worklist and push any memnode
//                      users onto memnode_worklist.
//  Phase 2:  Process MemNode's from memnode_worklist. compute new address type and
//            search the Memory chain for a store with the appropriate type
//            address type.  If a Phi is found, create a new version with
//            the appropriate memory slices from each of the Phi inputs.
//            For stores, process the users as follows:
//               MemNode:  push on memnode_worklist
//               MergeMem: push on mergemem_worklist
//  Phase 3:  Process MergeMem nodes from mergemem_worklist.  Walk each memory slice
//            moving the first node encountered of each  instance type to the
//            the input corresponding to its alias index.
//            appropriate memory slice.
//  Phase 4:  Update the inputs of non-instance memory Phis and the Memory input of memnodes.
//
// In the following example, the CheckCastPP nodes are the cast of allocation
// results and the allocation of node 29 is non-escaped and eligible to be an
// instance type.
//
// We start with:
//
//     7 Parm #memory
//    10  ConI  "12"
//    19  CheckCastPP   "Foo"
//    20  AddP  _ 19 19 10  Foo+12  alias_index=4
//    29  CheckCastPP   "Foo"
//    30  AddP  _ 29 29 10  Foo+12  alias_index=4
//
//    40  StoreP  25   7  20   ... alias_index=4
//    50  StoreP  35  40  30   ... alias_index=4
//    60  StoreP  45  50  20   ... alias_index=4
//    70  LoadP    _  60  30   ... alias_index=4
//    80  Phi     75  50  60   Memory alias_index=4
//    90  LoadP    _  80  30   ... alias_index=4
//   100  LoadP    _  80  20   ... alias_index=4
//
//
// Phase 1 creates an instance type for node 29 assigning it an instance id of 24
// and creating a new alias index for node 30.  This gives:
//
//     7 Parm #memory
//    10  ConI  "12"
//    19  CheckCastPP   "Foo"
//    20  AddP  _ 19 19 10  Foo+12  alias_index=4
//    29  CheckCastPP   "Foo"  iid=24
//    30  AddP  _ 29 29 10  Foo+12  alias_index=6  iid=24
//
//    40  StoreP  25   7  20   ... alias_index=4
//    50  StoreP  35  40  30   ... alias_index=6
//    60  StoreP  45  50  20   ... alias_index=4
//    70  LoadP    _  60  30   ... alias_index=6
//    80  Phi     75  50  60   Memory alias_index=4
//    90  LoadP    _  80  30   ... alias_index=6
//   100  LoadP    _  80  20   ... alias_index=4
//
// In phase 2, new memory inputs are computed for the loads and stores,
// And a new version of the phi is created.  In phase 4, the inputs to
// node 80 are updated and then the memory nodes are updated with the
// values computed in phase 2.  This results in:
//
//     7 Parm #memory
//    10  ConI  "12"
//    19  CheckCastPP   "Foo"
//    20  AddP  _ 19 19 10  Foo+12  alias_index=4
//    29  CheckCastPP   "Foo"  iid=24
//    30  AddP  _ 29 29 10  Foo+12  alias_index=6  iid=24
//
//    40  StoreP  25  7   20   ... alias_index=4
//    50  StoreP  35  7   30   ... alias_index=6
//    60  StoreP  45  40  20   ... alias_index=4
//    70  LoadP    _  50  30   ... alias_index=6
//    80  Phi     75  40  60   Memory alias_index=4
//   120  Phi     75  50  50   Memory alias_index=6
//    90  LoadP    _ 120  30   ... alias_index=6
//   100  LoadP    _  80  20   ... alias_index=4
//
void ConnectionGraph::split_unique_types(GrowableArray<Node *>  &alloc_worklist,
                                         GrowableArray<ArrayCopyNode*> &arraycopy_worklist,
                                         GrowableArray<MergeMemNode*> &mergemem_worklist,
                                         Unique_Node_List &reducible_merges) {
  DEBUG_ONLY(Unique_Node_List reduced_merges;)
  GrowableArray<Node *>  memnode_worklist;
  GrowableArray<PhiNode *>  orig_phis;
  PhaseIterGVN  *igvn = _igvn;
  uint new_index_start = (uint) _compile->num_alias_types();
  VectorSet visited;
  ideal_nodes.clear(); // Reset for use with set_map/get_map.
  uint unique_old = _compile->unique();

  //  Phase 1:  Process possible allocations from alloc_worklist.
  //  Create instance types for the CheckCastPP for allocations where possible.
  //
  // (Note: don't forget to change the order of the second AddP node on
  //  the alloc_worklist if the order of the worklist processing is changed,
  //  see the comment in find_second_addp().)
  //
  while (alloc_worklist.length() != 0) {
    Node *n = alloc_worklist.pop();
    uint ni = n->_idx;
    if (n->is_Call()) {
      CallNode *alloc = n->as_Call();
      // copy escape information to call node
      PointsToNode* ptn = ptnode_adr(alloc->_idx);
      PointsToNode::EscapeState es = ptn->escape_state();
      // We have an allocation or call which returns a Java object,
      // see if it is non-escaped.
      if (es != PointsToNode::NoEscape || !ptn->scalar_replaceable()) {
        continue;
      }
      // Find CheckCastPP for the allocate or for the return value of a call
      n = alloc->result_cast();
      if (n == nullptr) {            // No uses except Initialize node
        if (alloc->is_Allocate()) {
          // Set the scalar_replaceable flag for allocation
          // so it could be eliminated if it has no uses.
          alloc->as_Allocate()->_is_scalar_replaceable = true;
        }
        continue;
      }
      if (!n->is_CheckCastPP()) { // not unique CheckCastPP.
        // we could reach here for allocate case if one init is associated with many allocs.
        if (alloc->is_Allocate()) {
          alloc->as_Allocate()->_is_scalar_replaceable = false;
        }
        continue;
      }

      // The inline code for Object.clone() casts the allocation result to
      // java.lang.Object and then to the actual type of the allocated
      // object. Detect this case and use the second cast.
      // Also detect j.l.reflect.Array.newInstance(jobject, jint) case when
      // the allocation result is cast to java.lang.Object and then
      // to the actual Array type.
      if (alloc->is_Allocate() && n->as_Type()->type() == TypeInstPtr::NOTNULL
          && (alloc->is_AllocateArray() ||
              igvn->type(alloc->in(AllocateNode::KlassNode)) != TypeInstKlassPtr::OBJECT)) {
        Node *cast2 = nullptr;
        for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
          Node *use = n->fast_out(i);
          if (use->is_CheckCastPP()) {
            cast2 = use;
            break;
          }
        }
        if (cast2 != nullptr) {
          n = cast2;
        } else {
          // Non-scalar replaceable if the allocation type is unknown statically
          // (reflection allocation), the object can't be restored during
          // deoptimization without precise type.
          continue;
        }
      }

      const TypeOopPtr *t = igvn->type(n)->isa_oopptr();
      if (t == nullptr) {
        continue;  // not a TypeOopPtr
      }
      if (!t->klass_is_exact()) {
        continue; // not an unique type
      }
      if (alloc->is_Allocate()) {
        // Set the scalar_replaceable flag for allocation
        // so it could be eliminated.
        alloc->as_Allocate()->_is_scalar_replaceable = true;
      }
      set_escape_state(ptnode_adr(n->_idx), es NOT_PRODUCT(COMMA trace_propagate_message(ptn))); // CheckCastPP escape state
      // in order for an object to be scalar-replaceable, it must be:
      //   - a direct allocation (not a call returning an object)
      //   - non-escaping
      //   - eligible to be a unique type
      //   - not determined to be ineligible by escape analysis
      set_map(alloc, n);
      set_map(n, alloc);
      const TypeOopPtr* tinst = t->cast_to_instance_id(ni);
      igvn->hash_delete(n);
      igvn->set_type(n,  tinst);
      n->raise_bottom_type(tinst);
      igvn->hash_insert(n);
      record_for_optimizer(n);
      // Allocate an alias index for the header fields. Accesses to
      // the header emitted during macro expansion wouldn't have
      // correct memory state otherwise.
      _compile->get_alias_index(tinst->add_offset(oopDesc::mark_offset_in_bytes()));
      _compile->get_alias_index(tinst->add_offset(oopDesc::klass_offset_in_bytes()));
      if (alloc->is_Allocate() && (t->isa_instptr() || t->isa_aryptr())) {

        // First, put on the worklist all Field edges from Connection Graph
        // which is more accurate than putting immediate users from Ideal Graph.
        for (EdgeIterator e(ptn); e.has_next(); e.next()) {
          PointsToNode* tgt = e.get();
          if (tgt->is_Arraycopy()) {
            continue;
          }
          Node* use = tgt->ideal_node();
          assert(tgt->is_Field() && use->is_AddP(),
                 "only AddP nodes are Field edges in CG");
          if (use->outcnt() > 0) { // Don't process dead nodes
            Node* addp2 = find_second_addp(use, use->in(AddPNode::Base));
            if (addp2 != nullptr) {
              assert(alloc->is_AllocateArray(),"array allocation was expected");
              alloc_worklist.append_if_missing(addp2);
            }
            alloc_worklist.append_if_missing(use);
          }
        }

        // An allocation may have an Initialize which has raw stores. Scan
        // the users of the raw allocation result and push AddP users
        // on alloc_worklist.
        Node *raw_result = alloc->proj_out_or_null(TypeFunc::Parms);
        assert (raw_result != nullptr, "must have an allocation result");
        for (DUIterator_Fast imax, i = raw_result->fast_outs(imax); i < imax; i++) {
          Node *use = raw_result->fast_out(i);
          if (use->is_AddP() && use->outcnt() > 0) { // Don't process dead nodes
            Node* addp2 = find_second_addp(use, raw_result);
            if (addp2 != nullptr) {
              assert(alloc->is_AllocateArray(),"array allocation was expected");
              alloc_worklist.append_if_missing(addp2);
            }
            alloc_worklist.append_if_missing(use);
          } else if (use->is_MemBar()) {
            memnode_worklist.append_if_missing(use);
          }
        }
      }
    } else if (n->is_AddP()) {
      Node* addp_base = get_addp_base(n);
      if (addp_base != nullptr && reducible_merges.member(addp_base)) {
        // This AddP will go away when we reduce the the Phi
        continue;
      }
      JavaObjectNode* jobj = unique_java_object(addp_base);
      if (jobj == nullptr || jobj == phantom_obj) {
#ifdef ASSERT
        ptnode_adr(get_addp_base(n)->_idx)->dump();
        ptnode_adr(n->_idx)->dump();
        assert(jobj != nullptr && jobj != phantom_obj, "escaped allocation");
#endif
        _compile->record_failure(_invocation > 0 ? C2Compiler::retry_no_iterative_escape_analysis() : C2Compiler::retry_no_escape_analysis());
        return;
      }
      Node *base = get_map(jobj->idx());  // CheckCastPP node
      if (!split_AddP(n, base)) continue; // wrong type from dead path
    } else if (n->is_Phi() ||
               n->is_CheckCastPP() ||
               n->is_EncodeP() ||
               n->is_DecodeN() ||
               (n->is_ConstraintCast() && n->Opcode() == Op_CastPP)) {
      if (visited.test_set(n->_idx)) {
        assert(n->is_Phi(), "loops only through Phi's");
        continue;  // already processed
      }
      // Reducible Phi's will be removed from the graph after split_unique_types finishes
      if (reducible_merges.member(n)) {
        // Split loads through phi
        reduce_phi_on_field_access(n->as_Phi(), alloc_worklist);
#ifdef ASSERT
        if (VerifyReduceAllocationMerges) {
          reduced_merges.push(n);
        }
#endif
        continue;
      }
      JavaObjectNode* jobj = unique_java_object(n);
      if (jobj == nullptr || jobj == phantom_obj) {
#ifdef ASSERT
        ptnode_adr(n->_idx)->dump();
        assert(jobj != nullptr && jobj != phantom_obj, "escaped allocation");
#endif
        _compile->record_failure(_invocation > 0 ? C2Compiler::retry_no_iterative_escape_analysis() : C2Compiler::retry_no_escape_analysis());
        return;
      } else {
        Node *val = get_map(jobj->idx());   // CheckCastPP node
        TypeNode *tn = n->as_Type();
        const TypeOopPtr* tinst = igvn->type(val)->isa_oopptr();
        assert(tinst != nullptr && tinst->is_known_instance() &&
               tinst->instance_id() == jobj->idx() , "instance type expected.");

        const Type *tn_type = igvn->type(tn);
        const TypeOopPtr *tn_t;
        if (tn_type->isa_narrowoop()) {
          tn_t = tn_type->make_ptr()->isa_oopptr();
        } else {
          tn_t = tn_type->isa_oopptr();
        }
        if (tn_t != nullptr && tinst->maybe_java_subtype_of(tn_t)) {
          if (tn_type->isa_narrowoop()) {
            tn_type = tinst->make_narrowoop();
          } else {
            tn_type = tinst;
          }
          igvn->hash_delete(tn);
          igvn->set_type(tn, tn_type);
          tn->set_type(tn_type);
          igvn->hash_insert(tn);
          record_for_optimizer(n);
        } else {
          assert(tn_type == TypePtr::NULL_PTR ||
                 (tn_t != nullptr && !tinst->maybe_java_subtype_of(tn_t)),
                 "unexpected type");
          continue; // Skip dead path with different type
        }
      }
    } else {
      debug_only(n->dump();)
      assert(false, "EA: unexpected node");
      continue;
    }
    // push allocation's users on appropriate worklist
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node *use = n->fast_out(i);
      if(use->is_Mem() && use->in(MemNode::Address) == n) {
        // Load/store to instance's field
        memnode_worklist.append_if_missing(use);
      } else if (use->is_MemBar()) {
        if (use->in(TypeFunc::Memory) == n) { // Ignore precedent edge
          memnode_worklist.append_if_missing(use);
        }
      } else if (use->is_AddP() && use->outcnt() > 0) { // No dead nodes
        Node* addp2 = find_second_addp(use, n);
        if (addp2 != nullptr) {
          alloc_worklist.append_if_missing(addp2);
        }
        alloc_worklist.append_if_missing(use);
      } else if (use->is_Phi() ||
                 use->is_CheckCastPP() ||
                 use->is_EncodeNarrowPtr() ||
                 use->is_DecodeNarrowPtr() ||
                 (use->is_ConstraintCast() && use->Opcode() == Op_CastPP)) {
        alloc_worklist.append_if_missing(use);
#ifdef ASSERT
      } else if (use->is_Mem()) {
        assert(use->in(MemNode::Address) != n, "EA: missing allocation reference path");
      } else if (use->is_MergeMem()) {
        assert(mergemem_worklist.contains(use->as_MergeMem()), "EA: missing MergeMem node in the worklist");
      } else if (use->is_SafePoint()) {
        // Look for MergeMem nodes for calls which reference unique allocation
        // (through CheckCastPP nodes) even for debug info.
        Node* m = use->in(TypeFunc::Memory);
        if (m->is_MergeMem()) {
          assert(mergemem_worklist.contains(m->as_MergeMem()), "EA: missing MergeMem node in the worklist");
        }
      } else if (use->Opcode() == Op_EncodeISOArray) {
        if (use->in(MemNode::Memory) == n || use->in(3) == n) {
          // EncodeISOArray overwrites destination array
          memnode_worklist.append_if_missing(use);
        }
      } else {
        uint op = use->Opcode();
        if ((op == Op_StrCompressedCopy || op == Op_StrInflatedCopy) &&
            (use->in(MemNode::Memory) == n)) {
          // They overwrite memory edge corresponding to destination array,
          memnode_worklist.append_if_missing(use);
        } else if (!(op == Op_CmpP || op == Op_Conv2B ||
              op == Op_CastP2X || op == Op_StoreCM ||
              op == Op_FastLock || op == Op_AryEq ||
              op == Op_StrComp || op == Op_CountPositives ||
              op == Op_StrCompressedCopy || op == Op_StrInflatedCopy ||
              op == Op_StrEquals || op == Op_VectorizedHashCode ||
              op == Op_StrIndexOf || op == Op_StrIndexOfChar ||
              op == Op_SubTypeCheck ||
              BarrierSet::barrier_set()->barrier_set_c2()->is_gc_barrier_node(use))) {
          n->dump();
          use->dump();
          assert(false, "EA: missing allocation reference path");
        }
#endif
      }
    }

  }

#ifdef ASSERT
  if (VerifyReduceAllocationMerges) {
    // At this point reducible Phis shouldn't have AddP users anymore; only SafePoints.
    for (uint i = 0; i < reducible_merges.size(); i++) {
      Node* phi = reducible_merges.at(i);

      if (!reduced_merges.member(phi)) {
        phi->dump(2);
        phi->dump(-2);
        assert(false, "This reducible merge wasn't reduced.");
      }

      for (DUIterator_Fast jmax, j = phi->fast_outs(jmax); j < jmax; j++) {
        Node* use = phi->fast_out(j);
        if (!use->is_SafePoint()) {
          phi->dump(2);
          phi->dump(-2);
          assert(false, "Unexpected user of reducible Phi -> %d:%s:%d", use->_idx, use->Name(), use->outcnt());
        }
      }
    }
  }
#endif

  // Go over all ArrayCopy nodes and if one of the inputs has a unique
  // type, record it in the ArrayCopy node so we know what memory this
  // node uses/modified.
  for (int next = 0; next < arraycopy_worklist.length(); next++) {
    ArrayCopyNode* ac = arraycopy_worklist.at(next);
    Node* dest = ac->in(ArrayCopyNode::Dest);
    if (dest->is_AddP()) {
      dest = get_addp_base(dest);
    }
    JavaObjectNode* jobj = unique_java_object(dest);
    if (jobj != nullptr) {
      Node *base = get_map(jobj->idx());
      if (base != nullptr) {
        const TypeOopPtr *base_t = _igvn->type(base)->isa_oopptr();
        ac->_dest_type = base_t;
      }
    }
    Node* src = ac->in(ArrayCopyNode::Src);
    if (src->is_AddP()) {
      src = get_addp_base(src);
    }
    jobj = unique_java_object(src);
    if (jobj != nullptr) {
      Node* base = get_map(jobj->idx());
      if (base != nullptr) {
        const TypeOopPtr *base_t = _igvn->type(base)->isa_oopptr();
        ac->_src_type = base_t;
      }
    }
  }

  // New alias types were created in split_AddP().
  uint new_index_end = (uint) _compile->num_alias_types();

  //  Phase 2:  Process MemNode's from memnode_worklist. compute new address type and
  //            compute new values for Memory inputs  (the Memory inputs are not
  //            actually updated until phase 4.)
  if (memnode_worklist.length() == 0)
    return;  // nothing to do
  while (memnode_worklist.length() != 0) {
    Node *n = memnode_worklist.pop();
    if (visited.test_set(n->_idx)) {
      continue;
    }
    if (n->is_Phi() || n->is_ClearArray()) {
      // we don't need to do anything, but the users must be pushed
    } else if (n->is_MemBar()) { // Initialize, MemBar nodes
      // we don't need to do anything, but the users must be pushed
      n = n->as_MemBar()->proj_out_or_null(TypeFunc::Memory);
      if (n == nullptr) {
        continue;
      }
    } else if (n->is_CallLeaf()) {
      // Runtime calls with narrow memory input (no MergeMem node)
      // get the memory projection
      n = n->as_Call()->proj_out_or_null(TypeFunc::Memory);
      if (n == nullptr) {
        continue;
      }
    } else if (n->Opcode() == Op_StrCompressedCopy ||
               n->Opcode() == Op_EncodeISOArray) {
      // get the memory projection
      n = n->find_out_with(Op_SCMemProj);
      assert(n != nullptr && n->Opcode() == Op_SCMemProj, "memory projection required");
    } else {
      assert(n->is_Mem(), "memory node required.");
      Node *addr = n->in(MemNode::Address);
      const Type *addr_t = igvn->type(addr);
      if (addr_t == Type::TOP) {
        continue;
      }
      assert (addr_t->isa_ptr() != nullptr, "pointer type required.");
      int alias_idx = _compile->get_alias_index(addr_t->is_ptr());
      assert ((uint)alias_idx < new_index_end, "wrong alias index");
      Node *mem = find_inst_mem(n->in(MemNode::Memory), alias_idx, orig_phis);
      if (_compile->failing()) {
        return;
      }
      if (mem != n->in(MemNode::Memory)) {
        // We delay the memory edge update since we need old one in
        // MergeMem code below when instances memory slices are separated.
        set_map(n, mem);
      }
      if (n->is_Load()) {
        continue;  // don't push users
      } else if (n->is_LoadStore()) {
        // get the memory projection
        n = n->find_out_with(Op_SCMemProj);
        assert(n != nullptr && n->Opcode() == Op_SCMemProj, "memory projection required");
      }
    }
    // push user on appropriate worklist
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node *use = n->fast_out(i);
      if (use->is_Phi() || use->is_ClearArray()) {
        memnode_worklist.append_if_missing(use);
      } else if (use->is_Mem() && use->in(MemNode::Memory) == n) {
        if (use->Opcode() == Op_StoreCM) { // Ignore cardmark stores
          continue;
        }
        memnode_worklist.append_if_missing(use);
      } else if (use->is_MemBar() || use->is_CallLeaf()) {
        if (use->in(TypeFunc::Memory) == n) { // Ignore precedent edge
          memnode_worklist.append_if_missing(use);
        }
#ifdef ASSERT
      } else if(use->is_Mem()) {
        assert(use->in(MemNode::Memory) != n, "EA: missing memory path");
      } else if (use->is_MergeMem()) {
        assert(mergemem_worklist.contains(use->as_MergeMem()), "EA: missing MergeMem node in the worklist");
      } else if (use->Opcode() == Op_EncodeISOArray) {
        if (use->in(MemNode::Memory) == n || use->in(3) == n) {
          // EncodeISOArray overwrites destination array
          memnode_worklist.append_if_missing(use);
        }
      } else {
        uint op = use->Opcode();
        if ((use->in(MemNode::Memory) == n) &&
            (op == Op_StrCompressedCopy || op == Op_StrInflatedCopy)) {
          // They overwrite memory edge corresponding to destination array,
          memnode_worklist.append_if_missing(use);
        } else if (!(BarrierSet::barrier_set()->barrier_set_c2()->is_gc_barrier_node(use) ||
              op == Op_AryEq || op == Op_StrComp || op == Op_CountPositives ||
              op == Op_StrCompressedCopy || op == Op_StrInflatedCopy || op == Op_VectorizedHashCode ||
              op == Op_StrEquals || op == Op_StrIndexOf || op == Op_StrIndexOfChar)) {
          n->dump();
          use->dump();
          assert(false, "EA: missing memory path");
        }
#endif
      }
    }
  }

  //  Phase 3:  Process MergeMem nodes from mergemem_worklist.
  //            Walk each memory slice moving the first node encountered of each
  //            instance type to the input corresponding to its alias index.
  uint length = mergemem_worklist.length();
  for( uint next = 0; next < length; ++next ) {
    MergeMemNode* nmm = mergemem_worklist.at(next);
    assert(!visited.test_set(nmm->_idx), "should not be visited before");
    // Note: we don't want to use MergeMemStream here because we only want to
    // scan inputs which exist at the start, not ones we add during processing.
    // Note 2: MergeMem may already contains instance memory slices added
    // during find_inst_mem() call when memory nodes were processed above.
    igvn->hash_delete(nmm);
    uint nslices = MIN2(nmm->req(), new_index_start);
    for (uint i = Compile::AliasIdxRaw+1; i < nslices; i++) {
      Node* mem = nmm->in(i);
      Node* cur = nullptr;
      if (mem == nullptr || mem->is_top()) {
        continue;
      }
      // First, update mergemem by moving memory nodes to corresponding slices
      // if their type became more precise since this mergemem was created.
      while (mem->is_Mem()) {
        const Type *at = igvn->type(mem->in(MemNode::Address));
        if (at != Type::TOP) {
          assert (at->isa_ptr() != nullptr, "pointer type required.");
          uint idx = (uint)_compile->get_alias_index(at->is_ptr());
          if (idx == i) {
            if (cur == nullptr) {
              cur = mem;
            }
          } else {
            if (idx >= nmm->req() || nmm->is_empty_memory(nmm->in(idx))) {
              nmm->set_memory_at(idx, mem);
            }
          }
        }
        mem = mem->in(MemNode::Memory);
      }
      nmm->set_memory_at(i, (cur != nullptr) ? cur : mem);
      // Find any instance of the current type if we haven't encountered
      // already a memory slice of the instance along the memory chain.
      for (uint ni = new_index_start; ni < new_index_end; ni++) {
        if((uint)_compile->get_general_index(ni) == i) {
          Node *m = (ni >= nmm->req()) ? nmm->empty_memory() : nmm->in(ni);
          if (nmm->is_empty_memory(m)) {
            Node* result = find_inst_mem(mem, ni, orig_phis);
            if (_compile->failing()) {
              return;
            }
            nmm->set_memory_at(ni, result);
          }
        }
      }
    }
    // Find the rest of instances values
    for (uint ni = new_index_start; ni < new_index_end; ni++) {
      const TypeOopPtr *tinst = _compile->get_adr_type(ni)->isa_oopptr();
      Node* result = step_through_mergemem(nmm, ni, tinst);
      if (result == nmm->base_memory()) {
        // Didn't find instance memory, search through general slice recursively.
        result = nmm->memory_at(_compile->get_general_index(ni));
        result = find_inst_mem(result, ni, orig_phis);
        if (_compile->failing()) {
          return;
        }
        nmm->set_memory_at(ni, result);
      }
    }
    igvn->hash_insert(nmm);
    record_for_optimizer(nmm);
  }

  //  Phase 4:  Update the inputs of non-instance memory Phis and
  //            the Memory input of memnodes
  // First update the inputs of any non-instance Phi's from
  // which we split out an instance Phi.  Note we don't have
  // to recursively process Phi's encountered on the input memory
  // chains as is done in split_memory_phi() since they  will
  // also be processed here.
  for (int j = 0; j < orig_phis.length(); j++) {
    PhiNode *phi = orig_phis.at(j);
    int alias_idx = _compile->get_alias_index(phi->adr_type());
    igvn->hash_delete(phi);
    for (uint i = 1; i < phi->req(); i++) {
      Node *mem = phi->in(i);
      Node *new_mem = find_inst_mem(mem, alias_idx, orig_phis);
      if (_compile->failing()) {
        return;
      }
      if (mem != new_mem) {
        phi->set_req(i, new_mem);
      }
    }
    igvn->hash_insert(phi);
    record_for_optimizer(phi);
  }

  // Update the memory inputs of MemNodes with the value we computed
  // in Phase 2 and move stores memory users to corresponding memory slices.
  // Disable memory split verification code until the fix for 6984348.
  // Currently it produces false negative results since it does not cover all cases.
#if 0 // ifdef ASSERT
  visited.Reset();
  Node_Stack old_mems(arena, _compile->unique() >> 2);
#endif
  for (uint i = 0; i < ideal_nodes.size(); i++) {
    Node*    n = ideal_nodes.at(i);
    Node* nmem = get_map(n->_idx);
    assert(nmem != nullptr, "sanity");
    if (n->is_Mem()) {
#if 0 // ifdef ASSERT
      Node* old_mem = n->in(MemNode::Memory);
      if (!visited.test_set(old_mem->_idx)) {
        old_mems.push(old_mem, old_mem->outcnt());
      }
#endif
      assert(n->in(MemNode::Memory) != nmem, "sanity");
      if (!n->is_Load()) {
        // Move memory users of a store first.
        move_inst_mem(n, orig_phis);
      }
      // Now update memory input
      igvn->hash_delete(n);
      n->set_req(MemNode::Memory, nmem);
      igvn->hash_insert(n);
      record_for_optimizer(n);
    } else {
      assert(n->is_Allocate() || n->is_CheckCastPP() ||
             n->is_AddP() || n->is_Phi(), "unknown node used for set_map()");
    }
  }
#if 0 // ifdef ASSERT
  // Verify that memory was split correctly
  while (old_mems.is_nonempty()) {
    Node* old_mem = old_mems.node();
    uint  old_cnt = old_mems.index();
    old_mems.pop();
    assert(old_cnt == old_mem->outcnt(), "old mem could be lost");
  }
#endif
}

#ifndef PRODUCT
int ConnectionGraph::_no_escape_counter = 0;
int ConnectionGraph::_arg_escape_counter = 0;
int ConnectionGraph::_global_escape_counter = 0;

static const char *node_type_names[] = {
  "UnknownType",
  "JavaObject",
  "LocalVar",
  "Field",
  "Arraycopy"
};

static const char *esc_names[] = {
  "UnknownEscape",
  "NoEscape",
  "ArgEscape",
  "GlobalEscape"
};

void PointsToNode::dump_header(bool print_state, outputStream* out) const {
  NodeType nt = node_type();
  out->print("%s(%d) ", node_type_names[(int) nt], _pidx);
  if (print_state) {
    EscapeState es = escape_state();
    EscapeState fields_es = fields_escape_state();
    out->print("%s(%s) ", esc_names[(int)es], esc_names[(int)fields_es]);
    if (nt == PointsToNode::JavaObject && !this->scalar_replaceable()) {
      out->print("NSR ");
    }
  }
}

void PointsToNode::dump(bool print_state, outputStream* out, bool newline) const {
  dump_header(print_state, out);
  if (is_Field()) {
    FieldNode* f = (FieldNode*)this;
    if (f->is_oop()) {
      out->print("oop ");
    }
    if (f->offset() > 0) {
      out->print("+%d ", f->offset());
    }
    out->print("(");
    for (BaseIterator i(f); i.has_next(); i.next()) {
      PointsToNode* b = i.get();
      out->print(" %d%s", b->idx(),(b->is_JavaObject() ? "P" : ""));
    }
    out->print(" )");
  }
  out->print("[");
  for (EdgeIterator i(this); i.has_next(); i.next()) {
    PointsToNode* e = i.get();
    out->print(" %d%s%s", e->idx(),(e->is_JavaObject() ? "P" : (e->is_Field() ? "F" : "")), e->is_Arraycopy() ? "cp" : "");
  }
  out->print(" [");
  for (UseIterator i(this); i.has_next(); i.next()) {
    PointsToNode* u = i.get();
    bool is_base = false;
    if (PointsToNode::is_base_use(u)) {
      is_base = true;
      u = PointsToNode::get_use_node(u)->as_Field();
    }
    out->print(" %d%s%s", u->idx(), is_base ? "b" : "", u->is_Arraycopy() ? "cp" : "");
  }
  out->print(" ]]  ");
  if (_node == nullptr) {
    out->print("<null>%s", newline ? "\n" : "");
  } else {
    _node->dump(newline ? "\n" : "", false, out);
  }
}

void ConnectionGraph::dump(GrowableArray<PointsToNode*>& ptnodes_worklist) {
  bool first = true;
  int ptnodes_length = ptnodes_worklist.length();
  for (int i = 0; i < ptnodes_length; i++) {
    PointsToNode *ptn = ptnodes_worklist.at(i);
    if (ptn == nullptr || !ptn->is_JavaObject()) {
      continue;
    }
    PointsToNode::EscapeState es = ptn->escape_state();
    if ((es != PointsToNode::NoEscape) && !Verbose) {
      continue;
    }
    Node* n = ptn->ideal_node();
    if (n->is_Allocate() || (n->is_CallStaticJava() &&
                             n->as_CallStaticJava()->is_boxing_method())) {
      if (first) {
        tty->cr();
        tty->print("======== Connection graph for ");
        _compile->method()->print_short_name();
        tty->cr();
        tty->print_cr("invocation #%d: %d iterations and %f sec to build connection graph with %d nodes and worklist size %d",
                      _invocation, _build_iterations, _build_time, nodes_size(), ptnodes_worklist.length());
        tty->cr();
        first = false;
      }
      ptn->dump();
      // Print all locals and fields which reference this allocation
      for (UseIterator j(ptn); j.has_next(); j.next()) {
        PointsToNode* use = j.get();
        if (use->is_LocalVar()) {
          use->dump(Verbose);
        } else if (Verbose) {
          use->dump();
        }
      }
      tty->cr();
    }
  }
}

void ConnectionGraph::print_statistics() {
  tty->print_cr("No escape = %d, Arg escape = %d, Global escape = %d", Atomic::load(&_no_escape_counter), Atomic::load(&_arg_escape_counter), Atomic::load(&_global_escape_counter));
}

void ConnectionGraph::escape_state_statistics(GrowableArray<JavaObjectNode*>& java_objects_worklist) {
  if (!PrintOptoStatistics || (_invocation > 0)) { // Collect data only for the first invocation
    return;
  }
  for (int next = 0; next < java_objects_worklist.length(); ++next) {
    JavaObjectNode* ptn = java_objects_worklist.at(next);
    if (ptn->ideal_node()->is_Allocate()) {
      if (ptn->escape_state() == PointsToNode::NoEscape) {
        Atomic::inc(&ConnectionGraph::_no_escape_counter);
      } else if (ptn->escape_state() == PointsToNode::ArgEscape) {
        Atomic::inc(&ConnectionGraph::_arg_escape_counter);
      } else if (ptn->escape_state() == PointsToNode::GlobalEscape) {
        Atomic::inc(&ConnectionGraph::_global_escape_counter);
      } else {
        assert(false, "Unexpected Escape State");
      }
    }
  }
}

void ConnectionGraph::trace_es_update_helper(PointsToNode* ptn, PointsToNode::EscapeState es, bool fields, const char* reason) const {
  if (_compile->directive()->TraceEscapeAnalysisOption) {
    assert(ptn != nullptr, "should not be null");
    assert(reason != nullptr, "should not be null");
    ptn->dump_header(true);
    PointsToNode::EscapeState new_es = fields ? ptn->escape_state() : es;
    PointsToNode::EscapeState new_fields_es = fields ? es : ptn->fields_escape_state();
    tty->print_cr("-> %s(%s) %s", esc_names[(int)new_es], esc_names[(int)new_fields_es], reason);
  }
}

const char* ConnectionGraph::trace_propagate_message(PointsToNode* from) const {
  if (_compile->directive()->TraceEscapeAnalysisOption) {
    stringStream ss;
    ss.print("propagated from: ");
    from->dump(true, &ss, false);
    return ss.as_string();
  } else {
    return nullptr;
  }
}

const char* ConnectionGraph::trace_arg_escape_message(CallNode* call) const {
  if (_compile->directive()->TraceEscapeAnalysisOption) {
    stringStream ss;
    ss.print("escapes as arg to:");
    call->dump("", false, &ss);
    return ss.as_string();
  } else {
    return nullptr;
  }
}

const char* ConnectionGraph::trace_merged_message(PointsToNode* other) const {
  if (_compile->directive()->TraceEscapeAnalysisOption) {
    stringStream ss;
    ss.print("is merged with other object: ");
    other->dump_header(true, &ss);
    return ss.as_string();
  } else {
    return nullptr;
  }
}

#endif

void ConnectionGraph::record_for_optimizer(Node *n) {
  _igvn->_worklist.push(n);
  _igvn->add_users_to_worklist(n);
}

/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_MACRO_HPP
#define SHARE_VM_OPTO_MACRO_HPP

#include "opto/phase.hpp"

class  AllocateNode;
class  AllocateArrayNode;
class  CallNode;
class  Node;
class  PhaseIterGVN;

class PhaseMacroExpand : public Phase {
private:
  PhaseIterGVN &_igvn;

  // Helper methods roughly modelled after GraphKit:
  Node* top()                   const { return C->top(); }
  Node* intcon(jint con)        const { return _igvn.intcon(con); }
  Node* longcon(jlong con)      const { return _igvn.longcon(con); }
  Node* makecon(const Type *t)  const { return _igvn.makecon(t); }
  Node* basic_plus_adr(Node* base, int offset) {
    return (offset == 0)? base: basic_plus_adr(base, MakeConX(offset));
  }
  Node* basic_plus_adr(Node* base, Node* ptr, int offset) {
    return (offset == 0)? ptr: basic_plus_adr(base, ptr, MakeConX(offset));
  }
  Node* basic_plus_adr(Node* base, Node* offset) {
    return basic_plus_adr(base, base, offset);
  }
  Node* basic_plus_adr(Node* base, Node* ptr, Node* offset) {
    Node* adr = new (C, 4) AddPNode(base, ptr, offset);
    return transform_later(adr);
  }
  Node* transform_later(Node* n) {
    // equivalent to _gvn.transform in GraphKit, Ideal, etc.
    _igvn.register_new_node_with_optimizer(n);
    return n;
  }
  void set_eden_pointers(Node* &eden_top_adr, Node* &eden_end_adr);
  Node* make_load( Node* ctl, Node* mem, Node* base, int offset,
                   const Type* value_type, BasicType bt);
  Node* make_store(Node* ctl, Node* mem, Node* base, int offset,
                   Node* value, BasicType bt);

  // projections extracted from a call node
  ProjNode *_fallthroughproj;
  ProjNode *_fallthroughcatchproj;
  ProjNode *_ioproj_fallthrough;
  ProjNode *_ioproj_catchall;
  ProjNode *_catchallcatchproj;
  ProjNode *_memproj_fallthrough;
  ProjNode *_memproj_catchall;
  ProjNode *_resproj;


  void expand_allocate(AllocateNode *alloc);
  void expand_allocate_array(AllocateArrayNode *alloc);
  void expand_allocate_common(AllocateNode* alloc,
                              Node* length,
                              const TypeFunc* slow_call_type,
                              address slow_call_address);
  Node *value_from_mem(Node *mem, BasicType ft, const Type *ftype, const TypeOopPtr *adr_t, Node *alloc);
  Node *value_from_mem_phi(Node *mem, BasicType ft, const Type *ftype, const TypeOopPtr *adr_t, Node *alloc, Node_Stack *value_phis, int level);

  bool eliminate_allocate_node(AllocateNode *alloc);
  bool can_eliminate_allocation(AllocateNode *alloc, GrowableArray <SafePointNode *>& safepoints);
  bool scalar_replacement(AllocateNode *alloc, GrowableArray <SafePointNode *>& safepoints_done);
  void process_users_of_allocation(AllocateNode *alloc);

  void eliminate_card_mark(Node *cm);
  bool eliminate_locking_node(AbstractLockNode *alock);
  void expand_lock_node(LockNode *lock);
  void expand_unlock_node(UnlockNode *unlock);

  int replace_input(Node *use, Node *oldref, Node *newref);
  void copy_call_debug_info(CallNode *oldcall, CallNode * newcall);
  Node* opt_bits_test(Node* ctrl, Node* region, int edge, Node* word, int mask, int bits, bool return_fast_path = false);
  void copy_predefined_input_for_runtime_call(Node * ctrl, CallNode* oldcall, CallNode* call);
  CallNode* make_slow_call(CallNode *oldcall, const TypeFunc* slow_call_type, address slow_call,
                       const char* leaf_name, Node* slow_path, Node* parm0, Node* parm1);
  void extract_call_projections(CallNode *call);

  Node* initialize_object(AllocateNode* alloc,
                          Node* control, Node* rawmem, Node* object,
                          Node* klass_node, Node* length,
                          Node* size_in_bytes);

  Node* prefetch_allocation(Node* i_o,
                            Node*& needgc_false, Node*& contended_phi_rawmem,
                            Node* old_eden_top, Node* new_eden_top,
                            Node* length);

public:
  PhaseMacroExpand(PhaseIterGVN &igvn) : Phase(Macro_Expand), _igvn(igvn) {
    _igvn.set_delay_transform(true);
  }
  bool expand_macro_nodes();

};

#endif // SHARE_VM_OPTO_MACRO_HPP

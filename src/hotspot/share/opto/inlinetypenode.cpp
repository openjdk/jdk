/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciInlineKlass.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "gc/shared/gc_globals.hpp"
#include "oops/accessDecorators.hpp"
#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/compile.hpp"
#include "opto/convertnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/inlinetypenode.hpp"
#include "opto/memnode.hpp"
#include "opto/movenode.hpp"
#include "opto/multnode.hpp"
#include "opto/narrowptrnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/rootnode.hpp"
#include "opto/type.hpp"
#include "utilities/globalDefinitions.hpp"

// Clones the inline type to handle control flow merges involving multiple inline types.
// The inputs are replaced by PhiNodes to represent the merged values for the given region.
// init_with_top: input of phis above the returned InlineTypeNode are initialized to top.
InlineTypeNode* InlineTypeNode::clone_with_phis(PhaseGVN* gvn, Node* region, SafePointNode* map, bool is_non_null, bool init_with_top) {
  InlineTypeNode* vt = clone_if_required(gvn, map);
  const Type* t = Type::get_const_type(inline_klass());
  gvn->set_type(vt, t);
  vt->as_InlineType()->set_type(t);

  Node* const top = gvn->C->top();

  // Create a PhiNode for merging the oop values
  PhiNode* oop = PhiNode::make(region, init_with_top ? top : vt->get_oop(), t);
  gvn->set_type(oop, t);
  gvn->record_for_igvn(oop);
  vt->set_oop(*gvn, oop);

  // Create a PhiNode for merging the is_buffered values
  t = Type::get_const_basic_type(T_BOOLEAN);
  Node* is_buffered_node = PhiNode::make(region, init_with_top ? top : vt->get_is_buffered(), t);
  gvn->set_type(is_buffered_node, t);
  gvn->record_for_igvn(is_buffered_node);
  vt->set_req(IsBuffered, is_buffered_node);

  // Create a PhiNode for merging the null_marker values
  Node* null_marker_node;
  if (is_non_null) {
    null_marker_node = gvn->intcon(1);
  } else {
    t = Type::get_const_basic_type(T_BOOLEAN);
    null_marker_node = PhiNode::make(region, init_with_top ? top : vt->get_null_marker(), t);
    gvn->set_type(null_marker_node, t);
    gvn->record_for_igvn(null_marker_node);
  }
  vt->set_req(NullMarker, null_marker_node);

  // Create a PhiNode each for merging the field values
  for (uint i = 0; i < vt->field_count(); ++i) {
    ciType* type = vt->field(i)->type();
    Node*  value = vt->field_value(i);
    // We limit scalarization for inline types with circular fields and can therefore observe nodes
    // of the same type but with different scalarization depth during GVN. To avoid inconsistencies
    // during merging, make sure that we only create Phis for fields that are guaranteed to be scalarized.
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    bool no_circularity = !gvn->C->has_circular_inline_type() || field->is_flat();
    if (type->is_inlinetype() && no_circularity) {
      // Handle inline type fields recursively
      value = value->as_InlineType()->clone_with_phis(gvn, region, map);
    } else {
      t = Type::get_const_type(type);
      value = PhiNode::make(region, init_with_top ? top : value, t);
      gvn->set_type(value, t);
      gvn->record_for_igvn(value);
    }
    vt->set_field_value(i, value);
  }
  gvn->record_for_igvn(vt);
  return vt;
}

// Checks if the inputs of the InlineTypeNode were replaced by PhiNodes
// for the given region (see InlineTypeNode::clone_with_phis).
bool InlineTypeNode::has_phi_inputs(Node* region) {
  // Check oop input
  bool result = get_oop()->is_Phi() && get_oop()->as_Phi()->region() == region;
#ifdef ASSERT
  if (result) {
    // Check all field value inputs for consistency
    for (uint i = 0; i < field_count(); ++i) {
      Node* n = field_value(i);
      if (n->is_InlineType()) {
        assert(n->as_InlineType()->has_phi_inputs(region), "inconsistent phi inputs");
      } else {
        assert(n->is_Phi() && n->as_Phi()->region() == region, "inconsistent phi inputs");
      }
    }
  }
#endif
  return result;
}

// Merges 'this' with 'other' by updating the input PhiNodes added by 'clone_with_phis'
InlineTypeNode* InlineTypeNode::merge_with(PhaseGVN* gvn, const InlineTypeNode* other, int phi_index, bool transform) {
  assert(inline_klass() == other->inline_klass(), "Merging incompatible types");

  // Merge oop inputs
  PhiNode* phi = get_oop()->as_Phi();
  phi->set_req(phi_index, other->get_oop());
  if (transform) {
    set_oop(*gvn, gvn->transform(phi));
  }

  // Merge is_buffered inputs
  phi = get_is_buffered()->as_Phi();
  phi->set_req(phi_index, other->get_is_buffered());
  if (transform) {
    set_req(IsBuffered, gvn->transform(phi));
  }

  // Merge null_marker inputs
  Node* null_marker = get_null_marker();
  if (null_marker->is_Phi()) {
    phi = null_marker->as_Phi();
    phi->set_req(phi_index, other->get_null_marker());
    if (transform) {
      set_req(NullMarker, gvn->transform(phi));
    }
  } else {
    assert(null_marker->find_int_con(0) == 1, "only with a non null inline type");
  }

  // Merge field values
  for (uint i = 0; i < field_count(); ++i) {
    Node* val1 =        field_value(i);
    Node* val2 = other->field_value(i);
    if (val1->is_InlineType()) {
      if (val2->is_Phi()) {
        val2 = gvn->transform(val2);
      }
      if (val2->is_top()) {
        // The path where 'other' is used is dying. Therefore, we do not need to process the merge with 'other' further.
        // The phi inputs of 'this' at 'phi_index' will eventually be removed.
        break;
      }
      val1->as_InlineType()->merge_with(gvn, val2->as_InlineType(), phi_index, transform);
    } else {
      assert(val1->is_Phi(), "must be a phi node");
      val1->set_req(phi_index, val2);
    }
    if (transform) {
      set_field_value(i, gvn->transform(val1));
    }
  }
  return this;
}

// Adds a new merge path to an inline type node with phi inputs
void InlineTypeNode::add_new_path(Node* region) {
  assert(has_phi_inputs(region), "must have phi inputs");

  PhiNode* phi = get_oop()->as_Phi();
  phi->add_req(nullptr);
  assert(phi->req() == region->req(), "must be same size as region");

  phi = get_is_buffered()->as_Phi();
  phi->add_req(nullptr);
  assert(phi->req() == region->req(), "must be same size as region");

  phi = get_null_marker()->as_Phi();
  phi->add_req(nullptr);
  assert(phi->req() == region->req(), "must be same size as region");

  for (uint i = 0; i < field_count(); ++i) {
    Node* val = field_value(i);
    if (val->is_InlineType()) {
      val->as_InlineType()->add_new_path(region);
    } else {
      val->as_Phi()->add_req(nullptr);
      assert(val->req() == region->req(), "must be same size as region");
    }
  }
}

Node* InlineTypeNode::field_value(uint index) const {
  assert(index < field_count(), "index out of bounds");
  return in(Values + index);
}

// Get the value of the field at the given offset.
// If 'recursive' is true, flat inline type fields will be resolved recursively.
Node* InlineTypeNode::field_value_by_offset(int offset, bool recursive) const {
  // Find the declared field which contains the field we are looking for
  int index = inline_klass()->field_index_by_offset(offset);
  Node* value = field_value(index);
  assert(value != nullptr, "field value not found");
  ciField* field = this->field(index);
  assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");

  if (!recursive || !field->is_flat() || value->is_top()) {
    assert(offset == field->offset_in_bytes(), "offset mismatch");
    return value;
  }

  // Flat inline type field
  InlineTypeNode* vt = value->as_InlineType();
  assert(field->is_flat(), "must be flat");
  if (offset == field->null_marker_offset()) {
    return vt->get_null_marker();
  } else {
    int sub_offset = offset - field->offset_in_bytes(); // Offset of the flattened field inside the declared field
    sub_offset += vt->inline_klass()->payload_offset(); // Add header size
    return vt->field_value_by_offset(sub_offset, recursive);
  }
}

void InlineTypeNode::set_field_value(uint index, Node* value) {
  assert(index < field_count(), "index out of bounds");
  set_req(Values + index, value);
}

void InlineTypeNode::set_field_value_by_offset(int offset, Node* value) {
  set_field_value(field_index(offset), value);
}

uint InlineTypeNode::field_index(int offset) const {
  uint i = 0;
  for (; i < field_count() && field(i)->offset_in_bytes() != offset; i++) { }
  assert(i < field_count(), "field not found");
  return i;
}

ciField* InlineTypeNode::field(uint index) const {
  assert(index < field_count(), "index out of bounds");
  return inline_klass()->declared_nonstatic_field_at(index);
}

uint InlineTypeNode::add_fields_to_safepoint(Unique_Node_List& worklist, SafePointNode* sfpt) {
  uint cnt = 0;
  for (uint i = 0; i < field_count(); ++i) {
    Node* value = field_value(i);
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    if (field->is_flat()) {
      InlineTypeNode* vt = value->as_InlineType();
      cnt += vt->add_fields_to_safepoint(worklist, sfpt);
      if (!field->is_null_free()) {
        // The null marker of a flat field is added right after we scalarize that field
        sfpt->add_req(vt->get_null_marker());
        cnt++;
      }
      continue;
    }
    if (value->is_InlineType()) {
      // Add inline type to the worklist to process later
      worklist.push(value);
    }
    sfpt->add_req(value);
    cnt++;
  }
  return cnt;
}

void InlineTypeNode::make_scalar_in_safepoint(PhaseIterGVN* igvn, Unique_Node_List& worklist, SafePointNode* sfpt) {
  JVMState* jvms = sfpt->jvms();
  assert(jvms != nullptr, "missing JVMS");
  uint first_ind = (sfpt->req() - jvms->scloff());

  // Iterate over the inline type fields in order of increasing offset and add the
  // field values to the safepoint. Nullable inline types have an null marker field that
  // needs to be checked before using the field values.
  sfpt->add_req(get_null_marker());
  uint nfields = add_fields_to_safepoint(worklist, sfpt);
  jvms->set_endoff(sfpt->req());
  // Replace safepoint edge by SafePointScalarObjectNode
  SafePointScalarObjectNode* sobj = new SafePointScalarObjectNode(type()->isa_instptr(),
                                                                  nullptr,
                                                                  first_ind,
                                                                  sfpt->jvms()->depth(),
                                                                  nfields);
  sobj->init_req(0, igvn->C->root());
  sobj = igvn->transform(sobj)->as_SafePointScalarObject();
  igvn->rehash_node_delayed(sfpt);
  for (uint i = jvms->debug_start(); i < jvms->debug_end(); i++) {
    Node* debug = sfpt->in(i);
    if (debug != nullptr && debug->uncast() == this) {
      sfpt->set_req(i, sobj);
    }
  }
}

void InlineTypeNode::make_scalar_in_safepoints(PhaseIterGVN* igvn, bool allow_oop) {
  // If the inline type has a constant or loaded oop, use the oop instead of scalarization
  // in the safepoint to avoid keeping field loads live just for the debug info.
  Node* oop = get_oop();
  bool use_oop = false;
  if (allow_oop && is_allocated(igvn) && oop->is_Phi()) {
    Unique_Node_List worklist;
    VectorSet visited;
    visited.set(oop->_idx);
    worklist.push(oop);
    use_oop = true;
    while (worklist.size() > 0 && use_oop) {
      Node* n = worklist.pop();
      for (uint i = 1; i < n->req(); i++) {
        Node* in = n->in(i);
        if (in->is_Phi() && !visited.test_set(in->_idx)) {
          worklist.push(in);
        } else if (!(in->is_Con() || in->is_Parm())) {
          use_oop = false;
          break;
        }
      }
    }
  } else {
    use_oop = allow_oop && is_allocated(igvn) &&
              (oop->is_Con() || oop->is_Parm() || oop->is_Load() || (oop->isa_DecodeN() && oop->in(1)->is_Load()));
  }

  ResourceMark rm;
  Unique_Node_List safepoints;
  Unique_Node_List vt_worklist;
  Unique_Node_List worklist;
  worklist.push(this);
  while (worklist.size() > 0) {
    Node* n = worklist.pop();
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* use = n->fast_out(i);
      if (use->is_SafePoint() && !use->is_CallLeaf() && (!use->is_Call() || use->as_Call()->has_debug_use(n))) {
        safepoints.push(use);
      } else if (use->is_ConstraintCast()) {
        worklist.push(use);
      }
    }
  }

  // Process all safepoint uses and scalarize inline type
  while (safepoints.size() > 0) {
    SafePointNode* sfpt = safepoints.pop()->as_SafePoint();
    if (use_oop) {
      for (uint i = sfpt->jvms()->debug_start(); i < sfpt->jvms()->debug_end(); i++) {
        Node* debug = sfpt->in(i);
        if (debug != nullptr && debug->uncast() == this) {
          sfpt->set_req(i, get_oop());
        }
      }
      igvn->rehash_node_delayed(sfpt);
    } else {
      make_scalar_in_safepoint(igvn, vt_worklist, sfpt);
    }
  }
  // Now scalarize non-flat fields
  for (uint i = 0; i < vt_worklist.size(); ++i) {
    InlineTypeNode* vt = vt_worklist.at(i)->isa_InlineType();
    vt->make_scalar_in_safepoints(igvn);
  }
  if (outcnt() == 0) {
    igvn->record_for_igvn(this);
  }
}

// We limit scalarization for inline types with circular fields and can therefore observe nodes
// of the same type but with different scalarization depth during GVN. This method adjusts the
// scalarization depth to avoid inconsistencies during merging.
InlineTypeNode* InlineTypeNode::adjust_scalarization_depth(GraphKit* kit) {
  if (!kit->C->has_circular_inline_type()) {
    return this;
  }
  GrowableArray<ciType*> visited;
  visited.push(inline_klass());
  return adjust_scalarization_depth_impl(kit, visited);
}

InlineTypeNode* InlineTypeNode::adjust_scalarization_depth_impl(GraphKit* kit, GrowableArray<ciType*>& visited) {
  InlineTypeNode* val = this;
  for (uint i = 0; i < field_count(); ++i) {
    Node* value = field_value(i);
    Node* new_value = value;
    ciField* field = this->field(i);
    ciType* ft = field->type();
    if (value->is_InlineType()) {
      assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
      if (!field->is_flat() && visited.contains(ft)) {
        new_value = value->as_InlineType()->buffer(kit)->get_oop();
      } else {
        int old_len = visited.length();
        visited.push(ft);
        new_value = value->as_InlineType()->adjust_scalarization_depth_impl(kit, visited);
        visited.trunc_to(old_len);
      }
    } else if (ft->is_inlinetype() && !visited.contains(ft)) {
      int old_len = visited.length();
      visited.push(ft);
      new_value = make_from_oop_impl(kit, value, ft->as_inline_klass(), visited);
      visited.trunc_to(old_len);
    }
    if (value != new_value) {
      if (val == this) {
        val = clone_if_required(&kit->gvn(), kit->map());
      }
      val->set_field_value(i, new_value);
    }
  }
  return (val == this) ? this : kit->gvn().transform(val)->as_InlineType();
}

void InlineTypeNode::load(GraphKit* kit, Node* base, Node* ptr, bool immutable_memory, bool trust_null_free_oop, DecoratorSet decorators, GrowableArray<ciType*>& visited) {
  // Initialize the inline type by loading its field values from
  // memory and adding the values as input edges to the node.
  ciInlineKlass* vk = inline_klass();
  for (uint i = 0; i < field_count(); ++i) {
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    int field_off = field->offset_in_bytes() - vk->payload_offset();
    Node* field_ptr = kit->basic_plus_adr(base, ptr, field_off);
    Node* value = nullptr;
    ciType* ft = field->type();
    bool field_null_free = field->is_null_free();
    if (field->is_flat()) {
      // Recursively load the flat inline type field
      ciInlineKlass* fvk = ft->as_inline_klass();
      bool atomic = field->is_atomic();

      int old_len = visited.length();
      visited.push(ft);
      value = make_from_flat_impl(kit, fvk, base, field_ptr, atomic, immutable_memory,
                                  field_null_free, trust_null_free_oop && field_null_free, decorators, visited);
      visited.trunc_to(old_len);
    } else {
      // Load field value from memory
      BasicType bt = type2field[ft->basic_type()];
      assert(is_java_primitive(bt) || field_ptr->bottom_type()->is_ptr_to_narrowoop() == UseCompressedOops, "inconsistent");
      const Type* val_type = Type::get_const_type(ft);
      if (trust_null_free_oop && field_null_free) {
        val_type = val_type->join_speculative(TypePtr::NOTNULL);
      }
      const TypePtr* field_ptr_type = (decorators & C2_MISMATCHED) == 0 ? kit->gvn().type(field_ptr)->is_ptr() : TypeRawPtr::BOTTOM;
      value = kit->access_load_at(base, field_ptr, field_ptr_type, val_type, bt, decorators);
      // Loading a non-flattened inline type from memory
      if (visited.contains(ft)) {
        kit->C->set_has_circular_inline_type(true);
      } else if (ft->is_inlinetype()) {
        int old_len = visited.length();
        visited.push(ft);
        value = make_from_oop_impl(kit, value, ft->as_inline_klass(), visited);
        visited.trunc_to(old_len);
      }
    }
    set_field_value(i, value);
  }
}

void InlineTypeNode::store_flat(GraphKit* kit, Node* base, Node* ptr, bool atomic, bool immutable_memory, bool null_free, DecoratorSet decorators) {
  ciInlineKlass* vk = inline_klass();
  bool do_atomic = atomic;
  // With immutable memory, a non-atomic load and an atomic load are the same
  if (immutable_memory) {
    do_atomic = false;
  }
  // If there is only one flattened field, a non-atomic load and an atomic load are the same
  if (vk->is_naturally_atomic(null_free)) {
    do_atomic = false;
  }

  if (!do_atomic) {
    if (!null_free) {
      int nm_offset = vk->null_marker_offset_in_payload();
      Node* nm_ptr = kit->basic_plus_adr(base, ptr, nm_offset);
      const TypePtr* nm_ptr_type = (decorators & C2_MISMATCHED) == 0 ? kit->gvn().type(nm_ptr)->is_ptr() : TypeRawPtr::BOTTOM;
      kit->access_store_at(base, nm_ptr, nm_ptr_type, get_null_marker(), TypeInt::BOOL, T_BOOLEAN, decorators);
    }
    store(kit, base, ptr, immutable_memory, decorators);
    return;
  }

  StoreFlatNode::store(kit, base, ptr, this, null_free, decorators);
}

void InlineTypeNode::store_flat_array(GraphKit* kit, Node* base, Node* idx) {
  PhaseGVN& gvn = kit->gvn();
  DecoratorSet decorators = IN_HEAP | IS_ARRAY | MO_UNORDERED;
  kit->C->set_flat_accesses();
  ciInlineKlass* vk = inline_klass();
  assert(vk->maybe_flat_in_array(), "element type %s cannot be flat in array", vk->name()->as_utf8());

  RegionNode* region = new RegionNode(4);
  gvn.set_type(region, Type::CONTROL);
  kit->record_for_igvn(region);

  Node* input_memory_state = kit->reset_memory();
  kit->set_all_memory(input_memory_state);

  PhiNode* mem = PhiNode::make(region, input_memory_state, Type::MEMORY, TypePtr::BOTTOM);
  gvn.set_type(mem, Type::MEMORY);
  kit->record_for_igvn(mem);

  PhiNode* io = PhiNode::make(region, kit->i_o(), Type::ABIO);
  gvn.set_type(io, Type::ABIO);
  kit->record_for_igvn(io);

  Node* bol_null_free = kit->null_free_array_test(base); // Argument evaluation order is undefined in C++ and since this sets control, it needs to come first
  IfNode* iff_null_free = kit->create_and_map_if(kit->control(), bol_null_free, PROB_FAIR, COUNT_UNKNOWN);

  // Nullable
  kit->set_control(kit->IfFalse(iff_null_free));
  if (!kit->stopped()) {
    assert(vk->has_nullable_atomic_layout(), "element type %s does not have a nullable flat layout", vk->name()->as_utf8());
    kit->set_all_memory(input_memory_state);
    Node* cast = kit->cast_to_flat_array_exact(base, vk, false, true);
    Node* ptr = kit->array_element_address(cast, idx, T_FLAT_ELEMENT);
    store_flat(kit, cast, ptr, true, false, false, decorators);

    region->init_req(1, kit->control());
    mem->set_req(1, kit->reset_memory());
    io->set_req(1, kit->i_o());
  }

  // Null-free
  kit->set_control(kit->IfTrue(iff_null_free));
  if (!kit->stopped()) {
    kit->set_all_memory(input_memory_state);

    Node* bol_atomic = kit->null_free_atomic_array_test(base, vk);
    IfNode* iff_atomic = kit->create_and_map_if(kit->control(), bol_atomic, PROB_FAIR, COUNT_UNKNOWN);

    // Atomic
    kit->set_control(kit->IfTrue(iff_atomic));
    if (!kit->stopped()) {
      assert(vk->has_null_free_atomic_layout(), "element type %s does not have a null-free atomic flat layout", vk->name()->as_utf8());
      kit->set_all_memory(input_memory_state);
      Node* cast = kit->cast_to_flat_array_exact(base, vk, true, true);
      Node* ptr = kit->array_element_address(cast, idx, T_FLAT_ELEMENT);
      store_flat(kit, cast, ptr, true, false, true, decorators);

      region->init_req(2, kit->control());
      mem->set_req(2, kit->reset_memory());
      io->set_req(2, kit->i_o());
    }

    // Non-atomic
    kit->set_control(kit->IfFalse(iff_atomic));
    if (!kit->stopped()) {
      assert(vk->has_null_free_non_atomic_layout(), "element type %s does not have a null-free non-atomic flat layout", vk->name()->as_utf8());
      kit->set_all_memory(input_memory_state);
      Node* cast = kit->cast_to_flat_array_exact(base, vk, true, false);
      Node* ptr = kit->array_element_address(cast, idx, T_FLAT_ELEMENT);
      store_flat(kit, cast, ptr, false, false, true, decorators);

      region->init_req(3, kit->control());
      mem->set_req(3, kit->reset_memory());
      io->set_req(3, kit->i_o());
    }
  }

  kit->set_control(gvn.transform(region));
  kit->set_all_memory(gvn.transform(mem));
  kit->set_i_o(gvn.transform(io));
}

void InlineTypeNode::store(GraphKit* kit, Node* base, Node* ptr, bool immutable_memory, DecoratorSet decorators) const {
  // Write field values to memory
  ciInlineKlass* vk = inline_klass();
  for (uint i = 0; i < field_count(); ++i) {
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    int field_off = field->offset_in_bytes() - vk->payload_offset();
    Node* field_val = field_value(i);
    bool field_null_free = field->is_null_free();
    ciType* ft = field->type();
    Node* field_ptr = kit->basic_plus_adr(base, ptr, field_off);
    if (field->is_flat()) {
      // Recursively store the flat inline type field
      ciInlineKlass* fvk = ft->as_inline_klass();
      bool atomic = field->is_atomic();

      field_val->as_InlineType()->store_flat(kit, base, field_ptr, atomic, immutable_memory, field_null_free, decorators);
    } else {
      // Store field value to memory
      BasicType bt = type2field[ft->basic_type()];
      const TypePtr* field_ptr_type = (decorators & C2_MISMATCHED) == 0 ? kit->gvn().type(field_ptr)->is_ptr() : TypeRawPtr::BOTTOM;
      const Type* val_type = Type::get_const_type(ft);
      kit->access_store_at(base, field_ptr, field_ptr_type, field_val, val_type, bt, decorators);
    }
  }
}

// Adds a check between val1 and val2. Jumps to 'region' if check passes and optionally sets the corresponding phi input to false.
static void acmp_val_guard(PhaseIterGVN* igvn, RegionNode* region, Node* phi, Node** ctrl, BasicType bt, BoolTest::mask test, Node* val1, Node* val2) {
  Node* cmp = nullptr;
  switch (bt) {
  case T_FLOAT:
    val1 = igvn->register_new_node_with_optimizer(new MoveF2INode(val1));
    val2 = igvn->register_new_node_with_optimizer(new MoveF2INode(val2));
    // Fall-through to the int case
  case T_BOOLEAN:
  case T_CHAR:
  case T_BYTE:
  case T_SHORT:
  case T_INT:
    cmp = igvn->register_new_node_with_optimizer(new CmpINode(val1, val2));
    break;
  case T_DOUBLE:
    val1 = igvn->register_new_node_with_optimizer(new MoveD2LNode(val1));
    val2 = igvn->register_new_node_with_optimizer(new MoveD2LNode(val2));
    // Fall-through to the long case
  case T_LONG:
    cmp = igvn->register_new_node_with_optimizer(new CmpLNode(val1, val2));
    break;
  default:
    assert(is_reference_type(bt), "must be");
    cmp = igvn->register_new_node_with_optimizer(new CmpPNode(val1, val2));
  }
  Node* bol = igvn->register_new_node_with_optimizer(new BoolNode(cmp, test));
  IfNode* iff = igvn->register_new_node_with_optimizer(new IfNode(*ctrl, bol, PROB_MAX, COUNT_UNKNOWN))->as_If();
  Node* if_f = igvn->register_new_node_with_optimizer(new IfFalseNode(iff));
  Node* if_t = igvn->register_new_node_with_optimizer(new IfTrueNode(iff));

  region->add_req(if_t);
  if (phi != nullptr) {
    phi->add_req(igvn->intcon(0));
  }
  *ctrl = if_f;
}

// Check if a substitutability check between 'this' and 'other' can be implemented in IR
bool InlineTypeNode::can_emit_substitutability_check(Node* other) const {
  if (other != nullptr && other->is_InlineType() && bottom_type() != other->bottom_type()) {
    // Different types, this is dead code because there's a check above that guarantees this.
    return false;
  }
  for (uint i = 0; i < field_count(); i++) {
    ciType* ft = field(i)->type();
    Node* fv = field_value(i);
    if (ft->is_inlinetype() && fv->is_InlineType()) {
      // Check recursively
      if (!fv->as_InlineType()->can_emit_substitutability_check(nullptr)){
        return false;
      }
    } else if (ft->can_be_inline_klass()) {
      // Comparing this field might require (another) substitutability check, bail out
      return false;
    }
  }
  return true;
}

// Emit IR to check substitutability between 'this' (left operand) and the value object referred to by 'other' (right operand).
// Parse-time checks guarantee that both operands have the same type. If 'other' is not an InlineTypeNode, we need to emit loads for the field values.
void InlineTypeNode::check_substitutability(PhaseIterGVN* igvn, RegionNode* region, Node* phi, Node** ctrl, Node* mem, Node* base, Node* other, bool flat) const {
  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  DecoratorSet decorators = IN_HEAP | MO_UNORDERED | C2_READ_ACCESS | C2_CONTROL_DEPENDENT_LOAD;
  MergeMemNode* local_mem = igvn->register_new_node_with_optimizer(MergeMemNode::make(mem))->as_MergeMem();

  ciInlineKlass* vk = inline_klass();
  for (uint i = 0; i < field_count(); i++) {
    ciField* field = this->field(i);
    int field_off = field->offset_in_bytes();
    if (flat) {
      // Flat access, no header
      field_off -= vk->payload_offset();
    }
    Node* this_field = field_value(i);
    ciType* ft = field->type();
    BasicType bt = ft->basic_type();

    Node* other_base = base;
    Node* other_field = other;

    // Get field value of the other operand
    if (other->is_InlineType()) {
      other_field = other->as_InlineType()->field_value(i);
      other_base = nullptr;
    } else {
      // 'other' is an oop, compute address of the field
      other_field = igvn->register_new_node_with_optimizer(new AddPNode(base, other, igvn->MakeConX(field_off)));
      if (field->is_flat()) {
        // Flat field, load is handled recursively below
        assert(this_field->is_InlineType(), "inconsistent field value");
      } else {
        // Non-flat field, load the field value and update the base because we are now operating on a different object
        assert(is_java_primitive(bt) || other_field->bottom_type()->is_ptr_to_narrowoop() == UseCompressedOops, "inconsistent field type");
        C2AccessValuePtr addr(other_field, other_field->bottom_type()->is_ptr());
        C2OptAccess access(*igvn, *ctrl, local_mem, decorators, bt, base, addr);
        other_field = bs->load_at(access, Type::get_const_type(ft));
        other_base = other_field;
      }
    }

    if (this_field->is_InlineType()) {
      RegionNode* done_region = new RegionNode(1);
      ciField* field = this->field(i);
      assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
      if (!field->is_null_free()) {
        // Nullable field, check null marker before accessing the fields
        if (field->is_flat()) {
          // Flat field, check embedded null marker
          Node* null_marker = nullptr;
          if (other_field->is_InlineType()) {
            // TODO 8350865 Should we add an IGVN optimization to fold null marker loads from InlineTypeNodes?
            null_marker = other_field->as_InlineType()->get_null_marker();
          } else {
            Node* nm_offset = igvn->MakeConX(ft->as_inline_klass()->null_marker_offset_in_payload());
            Node* nm_adr = igvn->register_new_node_with_optimizer(new AddPNode(base, other_field, nm_offset));
            C2AccessValuePtr addr(nm_adr, nm_adr->bottom_type()->is_ptr());
            C2OptAccess access(*igvn, *ctrl, local_mem, decorators, T_BOOLEAN, base, addr);
            null_marker = bs->load_at(access, TypeInt::BOOL);
          }
          // Return false if null markers are not equal
          acmp_val_guard(igvn, region, phi, ctrl, T_INT, BoolTest::ne, this_field->as_InlineType()->get_null_marker(), null_marker);

          // Null markers are equal. If both operands are null, skip the comparison of the fields.
          acmp_val_guard(igvn, done_region, nullptr, ctrl, T_INT, BoolTest::eq, this_field->as_InlineType()->get_null_marker(), igvn->intcon(0));
        } else {
          // Non-flat field, check if oop is null

          // Check if 'this' is null
          RegionNode* not_null_region = new RegionNode(1);
          acmp_val_guard(igvn, not_null_region, nullptr, ctrl, T_INT, BoolTest::ne, this_field->as_InlineType()->get_null_marker(), igvn->intcon(0));

          // 'this' is null. If 'other' is non-null, return false.
          acmp_val_guard(igvn, region, phi, ctrl, T_OBJECT, BoolTest::ne, other_field, igvn->zerocon(T_OBJECT));

          // Both are null, skip comparing the fields
          done_region->add_req(*ctrl);

          // 'this' is not null. If 'other' is null, return false.
          *ctrl = igvn->register_new_node_with_optimizer(not_null_region);
          acmp_val_guard(igvn, region, phi, ctrl, T_OBJECT, BoolTest::eq, other_field, igvn->zerocon(T_OBJECT));
        }
      }
      // Both operands are non-null, compare all the fields recursively
      this_field->as_InlineType()->check_substitutability(igvn, region, phi, ctrl, mem, other_base, other_field, field->is_flat());

      done_region->add_req(*ctrl);
      *ctrl = igvn->register_new_node_with_optimizer(done_region);
    } else {
      assert(!ft->can_be_inline_klass(), "Needs substitutability test");
      acmp_val_guard(igvn, region, phi, ctrl, bt, BoolTest::ne, this_field, other_field);
    }
  }
}

InlineTypeNode* InlineTypeNode::buffer(GraphKit* kit, bool safe_for_replace) {
  if (kit->gvn().find_int_con(get_is_buffered(), 0) == 1) {
    // Already buffered
    return this;
  }

  // Check if inline type is already buffered
  Node* not_buffered_ctl = kit->top();
  Node* not_null_oop = kit->null_check_oop(get_oop(), &not_buffered_ctl, /* never_see_null = */ false, safe_for_replace);
  if (not_buffered_ctl->is_top()) {
    // Already buffered
    InlineTypeNode* vt = clone_if_required(&kit->gvn(), kit->map(), safe_for_replace);
    vt->set_is_buffered(kit->gvn());
    vt = kit->gvn().transform(vt)->as_InlineType();
    if (safe_for_replace) {
      kit->replace_in_map(this, vt);
    }
    return vt;
  }
  Node* buffered_ctl = kit->control();
  kit->set_control(not_buffered_ctl);

  // Inline type is not buffered, check if it is null.
  Node* null_ctl = kit->top();
  kit->null_check_common(get_null_marker(), T_INT, false, &null_ctl);
  bool null_free = null_ctl->is_top();

  RegionNode* region = new RegionNode(4);
  PhiNode* oop = PhiNode::make(region, not_null_oop, type()->join_speculative(null_free ? TypePtr::NOTNULL : TypePtr::BOTTOM));

  // InlineType is already buffered
  region->init_req(1, buffered_ctl);
  oop->init_req(1, not_null_oop);

  // InlineType is null
  region->init_req(2, null_ctl);
  oop->init_req(2, kit->gvn().zerocon(T_OBJECT));

  PhiNode* io  = PhiNode::make(region, kit->i_o(), Type::ABIO);
  PhiNode* mem = PhiNode::make(region, kit->merged_memory(), Type::MEMORY, TypePtr::BOTTOM);

  if (!kit->stopped()) {
    assert(!is_allocated(&kit->gvn()), "already buffered");
    PreserveJVMState pjvms(kit);
    ciInlineKlass* vk = inline_klass();
    // Allocate and initialize buffer, re-execute on deoptimization.
    kit->jvms()->set_bci(kit->bci());
    kit->jvms()->set_should_reexecute(true);
    kit->kill_dead_locals();
    Node* klass_node = kit->makecon(TypeKlassPtr::make(vk));
    Node* alloc_oop  = kit->new_instance(klass_node, nullptr, nullptr, /* deoptimize_on_exception */ true, this);
    Node* payload_alloc_oop = kit->basic_plus_adr(alloc_oop, vk->payload_offset());
    store(kit, alloc_oop, payload_alloc_oop, true, IN_HEAP | MO_UNORDERED | C2_TIGHTLY_COUPLED_ALLOC);

    // Do not let stores that initialize this buffer be reordered with a subsequent
    // store that would make this buffer accessible by other threads.
    AllocateNode* alloc = AllocateNode::Ideal_allocation(alloc_oop);
    assert(alloc != nullptr, "must have an allocation node");
    kit->insert_mem_bar(Op_MemBarStoreStore, alloc->proj_out_or_null(AllocateNode::RawAddress));
    oop->init_req(3, alloc_oop);
    region->init_req(3, kit->control());
    io    ->init_req(3, kit->i_o());
    mem   ->init_req(3, kit->merged_memory());
  }

  // Update GraphKit
  kit->set_control(kit->gvn().transform(region));
  kit->set_i_o(kit->gvn().transform(io));
  kit->set_all_memory(kit->gvn().transform(mem));
  kit->record_for_igvn(region);
  kit->record_for_igvn(oop);
  kit->record_for_igvn(io);
  kit->record_for_igvn(mem);

  // Use cloned InlineTypeNode to propagate oop from now on
  Node* res_oop = kit->gvn().transform(oop);
  InlineTypeNode* vt = clone_if_required(&kit->gvn(), kit->map(), safe_for_replace);
  vt->set_oop(kit->gvn(), res_oop);
  vt->set_is_buffered(kit->gvn());
  vt = kit->gvn().transform(vt)->as_InlineType();
  kit->record_for_igvn(vt);
  if (safe_for_replace) {
    kit->replace_in_map(this, vt);
  }
  // InlineTypeNode::remove_redundant_allocations piggybacks on split if.
  // Make sure it gets a chance to remove this allocation.
  kit->C->set_has_split_ifs(true);
  return vt;
}

bool InlineTypeNode::is_allocated(PhaseGVN* phase) const {
  if (phase->find_int_con(get_is_buffered(), 0) == 1) {
    return true;
  }
  Node* oop = get_oop();
  const Type* oop_type = (phase != nullptr) ? phase->type(oop) : oop->bottom_type();
  return !oop_type->maybe_null();
}

static void replace_proj(Compile* C, CallNode* call, uint& proj_idx, Node* value, BasicType bt) {
  ProjNode* pn = call->proj_out_or_null(proj_idx);
  if (pn != nullptr) {
    C->gvn_replace_by(pn, value);
    C->initial_gvn()->hash_delete(pn);
    pn->set_req(0, C->top());
  }
  proj_idx += type2size[bt];
}

// When a call returns multiple values, it has several result
// projections, one per field. Replacing the result of the call by an
// inline type node (after late inlining) requires that for each result
// projection, we find the corresponding inline type field.
void InlineTypeNode::replace_call_results(GraphKit* kit, CallNode* call, Compile* C) {
  uint proj_idx = TypeFunc::Parms;
  // Replace oop projection
  replace_proj(C, call, proj_idx, get_oop(), T_OBJECT);
  // Replace field projections
  replace_field_projs(C, call, proj_idx);
  // Replace null_marker projection
  replace_proj(C, call, proj_idx, get_null_marker(), T_BOOLEAN);
  assert(proj_idx == call->tf()->range_cc()->cnt(), "missed a projection");
}

void InlineTypeNode::replace_field_projs(Compile* C, CallNode* call, uint& proj_idx) {
  for (uint i = 0; i < field_count(); ++i) {
    Node* value = field_value(i);
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    if (field->is_flat()) {
      InlineTypeNode* vt = value->as_InlineType();
      // Replace field projections for flat field
      vt->replace_field_projs(C, call, proj_idx);
      if (!field->is_null_free()) {
        // Replace null_marker projection for nullable field
        replace_proj(C, call, proj_idx, vt->get_null_marker(), T_BOOLEAN);
      }
      continue;
    }
    // Replace projection for field value
    replace_proj(C, call, proj_idx, value, field->type()->basic_type());
  }
}

InlineTypeNode* InlineTypeNode::allocate_fields(GraphKit* kit) {
  InlineTypeNode* vt = clone_if_required(&kit->gvn(), kit->map());
  for (uint i = 0; i < field_count(); i++) {
    Node* value = field_value(i);
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
     if (field->is_flat()) {
       // Flat inline type field
       vt->set_field_value(i, value->as_InlineType()->allocate_fields(kit));
     } else if (value->is_InlineType()) {
       // Non-flat inline type field
       vt->set_field_value(i, value->as_InlineType()->buffer(kit));
     }
  }
  vt = kit->gvn().transform(vt)->as_InlineType();
  kit->replace_in_map(this, vt);
  return vt;
}

// Replace a buffer allocation by a dominating allocation
static void replace_allocation(PhaseIterGVN* igvn, Node* res, Node* dom) {
  // Remove initializing stores and GC barriers
  for (DUIterator_Fast imax, i = res->fast_outs(imax); i < imax; i++) {
    Node* use = res->fast_out(i);
    if (use->is_AddP()) {
      for (DUIterator_Fast jmax, j = use->fast_outs(jmax); j < jmax; j++) {
        Node* store = use->fast_out(j)->isa_Store();
        if (store != nullptr) {
          igvn->rehash_node_delayed(store);
          igvn->replace_in_uses(store, store->in(MemNode::Memory));
        }
      }
    } else if (use->Opcode() == Op_CastP2X) {
      if (UseG1GC && use->find_out_with(Op_XorX)->in(1) != use) {
        // The G1 pre-barrier uses a CastP2X both for the pointer of the object
        // we store into, as well as the value we are storing. Skip if this is a
        // barrier for storing 'res' into another object.
        continue;
      }
      BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
      bs->eliminate_gc_barrier(igvn, use);
      --i; --imax;
    }
  }
  igvn->replace_node(res, dom);
}

Node* InlineTypeNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* oop = get_oop();
  Node* is_buffered = get_is_buffered();

  if (oop->isa_InlineType() && !phase->type(oop)->maybe_null()) {
    InlineTypeNode* vtptr = oop->as_InlineType();
    set_oop(*phase, vtptr->get_oop());
    set_is_buffered(*phase);
    set_null_marker(*phase);
    for (uint i = Values; i < vtptr->req(); ++i) {
      set_req(i, vtptr->in(i));
    }
    return this;
  }

  // Use base oop if fields are loaded from memory, don't do so if base is the CheckCastPP of an
  // allocation because the only case we load from a naked CheckCastPP is when we exit a
  // constructor of an inline type and we want to relinquish the larval oop there. This has a
  // couple of benefits:
  // - The allocation is likely to be elided earlier if it is not an input of an InlineTypeNode.
  // - The InlineTypeNode without an allocation input is more likely to be GVN-ed. This may emerge
  //   when we try to clone a value object.
  // - The buffering, if needed, is delayed until it is required. This new allocation, since it is
  //   created from an InlineTypeNode, is recognized as not having a unique identity and in the
  //   future, we can move them around more freely such as hoisting out of loops. This is not true
  //   for the old allocation since larval value objects do have unique identities.
  Node* base = is_loaded(phase);
  if (base != nullptr && !base->is_InlineType() && !phase->type(base)->maybe_null() && phase->C->allow_macro_nodes() && AllocateNode::Ideal_allocation(base) == nullptr) {
    if (oop != base || phase->type(is_buffered) != TypeInt::ONE) {
      set_oop(*phase, base);
      set_is_buffered(*phase);
      return this;
    }
  }

  if (can_reshape) {
    PhaseIterGVN* igvn = phase->is_IterGVN();
    if (is_allocated(phase)) {
      // Search for and remove re-allocations of this inline type. Ignore scalar replaceable ones,
      // they will be removed anyway and changing the memory chain will confuse other optimizations.
      // This can happen with late inlining when we first allocate an inline type argument
      // but later decide to inline the call after the callee code also triggered allocation.
      for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
        AllocateNode* alloc = fast_out(i)->isa_Allocate();
        if (alloc != nullptr && alloc->in(AllocateNode::InlineType) == this && !alloc->_is_scalar_replaceable) {
          // Found a re-allocation
          Node* res = alloc->result_cast();
          if (res != nullptr && res->is_CheckCastPP()) {
            // Replace allocation by oop and unlink AllocateNode
            replace_allocation(igvn, res, oop);
            igvn->replace_input_of(alloc, AllocateNode::InlineType, igvn->C->top());
            --i; --imax;
          }
        }
      }
    }
  }

  return nullptr;
}

InlineTypeNode* InlineTypeNode::make_uninitialized(PhaseGVN& gvn, ciInlineKlass* vk, bool null_free) {
  // Create a new InlineTypeNode with uninitialized values and nullptr oop
  InlineTypeNode* vt = new InlineTypeNode(vk, gvn.zerocon(T_OBJECT), null_free);
  vt->set_is_buffered(gvn, false);
  vt->set_null_marker(gvn);
  return vt;
}

InlineTypeNode* InlineTypeNode::make_all_zero(PhaseGVN& gvn, ciInlineKlass* vk) {
  GrowableArray<ciType*> visited;
  visited.push(vk);
  return make_all_zero_impl(gvn, vk, visited);
}

InlineTypeNode* InlineTypeNode::make_all_zero_impl(PhaseGVN& gvn, ciInlineKlass* vk, GrowableArray<ciType*>& visited) {
  // Create a new InlineTypeNode initialized with all zero
  InlineTypeNode* vt = new InlineTypeNode(vk, gvn.zerocon(T_OBJECT), /* null_free= */ true);
  vt->set_is_buffered(gvn, false);
  vt->set_null_marker(gvn);
  for (uint i = 0; i < vt->field_count(); ++i) {
    ciField* field = vt->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    ciType* ft = field->type();
    Node* value = gvn.zerocon(ft->basic_type());
    if (!field->is_flat() && visited.contains(ft)) {
      gvn.C->set_has_circular_inline_type(true);
    } else if (ft->is_inlinetype()) {
      int old_len = visited.length();
      visited.push(ft);
      ciInlineKlass* vk = ft->as_inline_klass();
      if (field->is_null_free()) {
        value = make_all_zero_impl(gvn, vk, visited);
      } else {
        value = make_null_impl(gvn, vk, visited);
      }
      visited.trunc_to(old_len);
    }
    vt->set_field_value(i, value);
  }
  vt = gvn.transform(vt)->as_InlineType();
  assert(vt->is_all_zero(&gvn), "must be the all-zero inline type");
  return vt;
}

bool InlineTypeNode::is_all_zero(PhaseGVN* gvn, bool flat) const {
  const TypeInt* tinit = gvn->type(get_null_marker())->isa_int();
  if (tinit == nullptr || !tinit->is_con(1)) {
    return false; // May be null
  }
  for (uint i = 0; i < field_count(); ++i) {
    Node* value = field_value(i);
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    if (field->is_null_free()) {
      // Null-free value class field must have the all-zero value. If 'flat' is set,
      // reject non-flat fields because they need to be initialized with an oop to a buffer.
      if (!value->is_InlineType() || !value->as_InlineType()->is_all_zero(gvn) || (flat && !field->is_flat())) {
        return false;
      }
      continue;
    } else if (value->is_InlineType()) {
      // Nullable value class field must be null
      tinit = gvn->type(value->as_InlineType()->get_null_marker())->isa_int();
      if (tinit != nullptr && tinit->is_con(0)) {
        continue;
      }
      return false;
    } else if (!gvn->type(value)->is_zero_type()) {
      return false;
    }
  }
  return true;
}

InlineTypeNode* InlineTypeNode::make_from_oop(GraphKit* kit, Node* oop, ciInlineKlass* vk) {
  GrowableArray<ciType*> visited;
  visited.push(vk);
  return make_from_oop_impl(kit, oop, vk, visited);
}

InlineTypeNode* InlineTypeNode::make_from_oop_impl(GraphKit* kit, Node* oop, ciInlineKlass* vk, GrowableArray<ciType*>& visited) {
  PhaseGVN& gvn = kit->gvn();

  // Create and initialize an InlineTypeNode by loading all field
  // values from a heap-allocated version and also save the oop.
  InlineTypeNode* vt = nullptr;

  if (oop->isa_InlineType()) {
    return oop->as_InlineType();
  }

  if (gvn.type(oop)->maybe_null()) {
    // Add a null check because the oop may be null
    Node* null_ctl = kit->top();
    Node* not_null_oop = kit->null_check_oop(oop, &null_ctl);
    if (kit->stopped()) {
      // Constant null
      kit->set_control(null_ctl);
      vt = make_null_impl(gvn, vk, visited);
      kit->record_for_igvn(vt);
      return vt;
    }
    vt = new InlineTypeNode(vk, not_null_oop, /* null_free= */ false);
    vt->set_is_buffered(gvn);
    vt->set_null_marker(gvn);
    Node* payload_ptr = kit->basic_plus_adr(not_null_oop, vk->payload_offset());
    vt->load(kit, not_null_oop, payload_ptr, true, true, IN_HEAP | MO_UNORDERED, visited);

    if (null_ctl != kit->top()) {
      InlineTypeNode* null_vt = make_null_impl(gvn, vk, visited);
      Node* region = new RegionNode(3);
      region->init_req(1, kit->control());
      region->init_req(2, null_ctl);
      vt = vt->clone_with_phis(&gvn, region, kit->map());
      vt->merge_with(&gvn, null_vt, 2, true);
      vt->set_oop(gvn, oop);
      kit->set_control(gvn.transform(region));
    }
  } else {
    // Oop can never be null
    vt = new InlineTypeNode(vk, oop, /* null_free= */ true);
    Node* init_ctl = kit->control();
    vt->set_is_buffered(gvn);
    vt->set_null_marker(gvn);
    Node* payload_ptr = kit->basic_plus_adr(oop, vk->payload_offset());
    vt->load(kit, oop, payload_ptr, true, true, IN_HEAP | MO_UNORDERED, visited);
// TODO 8284443
//    assert(!null_free || vt->as_InlineType()->is_all_zero(&gvn) || init_ctl != kit->control() || !gvn.type(oop)->is_inlinetypeptr() || oop->is_Con() || oop->Opcode() == Op_InlineType ||
//           AllocateNode::Ideal_allocation(oop, &gvn) != nullptr || vt->as_InlineType()->is_loaded(&gvn) == oop, "inline type should be loaded");
  }
  assert(vt->is_allocated(&gvn), "inline type should be allocated");
  kit->record_for_igvn(vt);
  return gvn.transform(vt)->as_InlineType();
}

InlineTypeNode* InlineTypeNode::make_from_flat(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* ptr,
                                               bool atomic, bool immutable_memory, bool null_free, DecoratorSet decorators) {
  GrowableArray<ciType*> visited;
  visited.push(vk);
  return make_from_flat_impl(kit, vk, base, ptr, atomic, immutable_memory, null_free, null_free, decorators, visited);
}

// GraphKit wrapper for the 'make_from_flat' method
InlineTypeNode* InlineTypeNode::make_from_flat_impl(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* ptr, bool atomic, bool immutable_memory,
                                                    bool null_free, bool trust_null_free_oop, DecoratorSet decorators, GrowableArray<ciType*>& visited) {
  assert(null_free || !trust_null_free_oop, "cannot trust null-free oop when the holder object is not null-free");
  PhaseGVN& gvn = kit->gvn();
  bool do_atomic = atomic;
  // With immutable memory, a non-atomic load and an atomic load are the same
  if (immutable_memory) {
    do_atomic = false;
  }
  // If there is only one flattened field, a non-atomic load and an atomic load are the same
  if (vk->is_naturally_atomic(null_free)) {
    do_atomic = false;
  }

  if (!do_atomic) {
    InlineTypeNode* vt = make_uninitialized(kit->gvn(), vk, null_free);
    if (!null_free) {
      int nm_offset = vk->null_marker_offset_in_payload();
      Node* nm_ptr = kit->basic_plus_adr(base, ptr, nm_offset);
      const TypePtr* nm_ptr_type = (decorators & C2_MISMATCHED) == 0 ? gvn.type(nm_ptr)->is_ptr() : TypeRawPtr::BOTTOM;
      Node* nm_value = kit->access_load_at(base, nm_ptr, nm_ptr_type, TypeInt::BOOL, T_BOOLEAN, decorators);
      vt->set_req(NullMarker, nm_value);
    }

    vt->load(kit, base, ptr, immutable_memory, trust_null_free_oop, decorators, visited);
    return gvn.transform(vt)->as_InlineType();
  }

  assert(!immutable_memory, "immutable memory does not need explicit atomic access");
  return LoadFlatNode::load(kit, vk, base, ptr, null_free, trust_null_free_oop, decorators);
}

InlineTypeNode* InlineTypeNode::make_from_flat_array(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* idx) {
  assert(vk->maybe_flat_in_array(), "element type %s cannot be flat in array", vk->name()->as_utf8());
  PhaseGVN& gvn = kit->gvn();
  DecoratorSet decorators = IN_HEAP | IS_ARRAY | MO_UNORDERED | C2_CONTROL_DEPENDENT_LOAD;
  kit->C->set_flat_accesses();
  InlineTypeNode* vt_nullable = nullptr;
  InlineTypeNode* vt_null_free = nullptr;
  InlineTypeNode* vt_non_atomic = nullptr;

  RegionNode* region = new RegionNode(4);
  gvn.set_type(region, Type::CONTROL);
  kit->record_for_igvn(region);

  Node* input_memory_state = kit->reset_memory();
  kit->set_all_memory(input_memory_state);

  PhiNode* mem = PhiNode::make(region, input_memory_state, Type::MEMORY, TypePtr::BOTTOM);
  gvn.set_type(mem, Type::MEMORY);
  kit->record_for_igvn(mem);

  PhiNode* io = PhiNode::make(region, kit->i_o(), Type::ABIO);
  gvn.set_type(io, Type::ABIO);
  kit->record_for_igvn(io);

  Node* bol_null_free = kit->null_free_array_test(base); // Argument evaluation order is undefined in C++ and since this sets control, it needs to come first
  IfNode* iff_null_free = kit->create_and_map_if(kit->control(), bol_null_free, PROB_FAIR, COUNT_UNKNOWN);

  // Nullable
  kit->set_control(kit->IfFalse(iff_null_free));
  if (!kit->stopped()) {
    assert(vk->has_nullable_atomic_layout(), "element type %s does not have a nullable flat layout", vk->name()->as_utf8());
    kit->set_all_memory(input_memory_state);
    Node* cast = kit->cast_to_flat_array_exact(base, vk, false, true);
    Node* ptr = kit->array_element_address(cast, idx, T_FLAT_ELEMENT);
    vt_nullable = InlineTypeNode::make_from_flat(kit, vk, cast, ptr, true, false, false, decorators);

    region->init_req(1, kit->control());
    mem->set_req(1, kit->reset_memory());
    io->set_req(1, kit->i_o());
  }

  // Null-free
  kit->set_control(kit->IfTrue(iff_null_free));
  if (!kit->stopped()) {
    kit->set_all_memory(input_memory_state);

    Node* bol_atomic = kit->null_free_atomic_array_test(base, vk);
    IfNode* iff_atomic = kit->create_and_map_if(kit->control(), bol_atomic, PROB_FAIR, COUNT_UNKNOWN);

    // Atomic
    kit->set_control(kit->IfTrue(iff_atomic));
    if (!kit->stopped()) {
      assert(vk->has_null_free_atomic_layout(), "element type %s does not have a null-free atomic flat layout", vk->name()->as_utf8());
      kit->set_all_memory(input_memory_state);
      Node* cast = kit->cast_to_flat_array_exact(base, vk, true, true);
      Node* ptr = kit->array_element_address(cast, idx, T_FLAT_ELEMENT);
      vt_null_free = InlineTypeNode::make_from_flat(kit, vk, cast, ptr, true, false, true, decorators);

      region->init_req(2, kit->control());
      mem->set_req(2, kit->reset_memory());
      io->set_req(2, kit->i_o());
    }

    // Non-Atomic
    kit->set_control(kit->IfFalse(iff_atomic));
    if (!kit->stopped()) {
      assert(vk->has_null_free_non_atomic_layout(), "element type %s does not have a null-free non-atomic flat layout", vk->name()->as_utf8());
      kit->set_all_memory(input_memory_state);
      Node* cast = kit->cast_to_flat_array_exact(base, vk, true, false);
      Node* ptr = kit->array_element_address(cast, idx, T_FLAT_ELEMENT);
      vt_non_atomic = InlineTypeNode::make_from_flat(kit, vk, cast, ptr, false, false, true, decorators);

      region->init_req(3, kit->control());
      mem->set_req(3, kit->reset_memory());
      io->set_req(3, kit->i_o());
    }
  }

  InlineTypeNode* vt = nullptr;
  if (vt_nullable == nullptr && vt_null_free == nullptr && vt_non_atomic == nullptr) {
    // All paths are dead
    vt = make_null(gvn, vk);
  } else if (vt_nullable == nullptr && vt_null_free == nullptr) {
    vt = vt_non_atomic;
  } else if (vt_nullable == nullptr && vt_non_atomic == nullptr) {
    vt = vt_null_free;
  } else if (vt_null_free == nullptr && vt_non_atomic == nullptr) {
    vt = vt_nullable;
  }
  if (vt != nullptr) {
    kit->set_control(kit->gvn().transform(region));
    kit->set_all_memory(kit->gvn().transform(mem));
    kit->set_i_o(kit->gvn().transform(io));
    return vt;
  }

  InlineTypeNode* zero = InlineTypeNode::make_null(gvn, vk);
  vt = zero->clone_with_phis(&gvn, region);
  if (vt_nullable != nullptr) {
    vt = vt->merge_with(&gvn, vt_nullable, 1, false);
  }
  if (vt_null_free != nullptr) {
    vt = vt->merge_with(&gvn, vt_null_free, 2, false);
  }
  if (vt_non_atomic != nullptr) {
    vt = vt->merge_with(&gvn, vt_non_atomic, 3, false);
  }

  kit->set_control(kit->gvn().transform(region));
  kit->set_all_memory(kit->gvn().transform(mem));
  kit->set_i_o(kit->gvn().transform(io));
  return gvn.transform(vt)->as_InlineType();
}

InlineTypeNode* InlineTypeNode::make_from_multi(GraphKit* kit, MultiNode* multi, ciInlineKlass* vk, uint& base_input, bool in, bool null_free) {
  InlineTypeNode* vt = make_uninitialized(kit->gvn(), vk, null_free);
  if (!in) {
    // Keep track of the oop. The returned inline type might already be buffered.
    Node* oop = kit->gvn().transform(new ProjNode(multi, base_input++));
    vt->set_oop(kit->gvn(), oop);
  }
  GrowableArray<ciType*> visited;
  visited.push(vk);
  vt->initialize_fields(kit, multi, base_input, in, null_free, nullptr, visited);
  return kit->gvn().transform(vt)->as_InlineType();
}

Node* InlineTypeNode::is_loaded(PhaseGVN* phase, ciInlineKlass* vk, Node* base, int holder_offset) {
  if (vk == nullptr) {
    vk = inline_klass();
  }
  for (uint i = 0; i < field_count(); ++i) {
    ciField* field = this->field(i);
    int offset = holder_offset + field->offset_in_bytes();
    Node* value = field_value(i);
    if (value->is_InlineType()) {
      assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
      InlineTypeNode* vt = value->as_InlineType();
      if (vt->type()->inline_klass()->is_empty()) {
        continue;
      } else if (field->is_flat() && vt->is_InlineType()) {
        // Check inline type field load recursively
        base = vt->as_InlineType()->is_loaded(phase, vk, base, offset - vt->type()->inline_klass()->payload_offset());
        if (base == nullptr) {
          return nullptr;
        }
        continue;
      } else {
        value = vt->get_oop();
        if (value->Opcode() == Op_CastPP) {
          // Skip CastPP
          value = value->in(1);
        }
      }
    }
    if (value->isa_DecodeN()) {
      // Skip DecodeN
      value = value->in(1);
    }
    if (value->isa_Load()) {
      // Check if base and offset of field load matches inline type layout
      intptr_t loffset = 0;
      Node* lbase = AddPNode::Ideal_base_and_offset(value->in(MemNode::Address), phase, loffset);
      if (lbase == nullptr || (lbase != base && base != nullptr) || loffset != offset) {
        return nullptr;
      } else if (base == nullptr) {
        // Set base and check if pointer type matches
        base = lbase;
        const TypeInstPtr* vtptr = phase->type(base)->isa_instptr();
        if (vtptr == nullptr || !vtptr->instance_klass()->equals(vk)) {
          return nullptr;
        }
      }
    } else {
      return nullptr;
    }
  }
  return base;
}

Node* InlineTypeNode::tagged_klass(ciInlineKlass* vk, PhaseGVN& gvn) {
  const TypeKlassPtr* tk = TypeKlassPtr::make(vk);
  intptr_t bits = tk->get_con();
  set_nth_bit(bits, 0);
  return gvn.longcon((jlong)bits);
}

void InlineTypeNode::pass_fields(GraphKit* kit, Node* n, uint& base_input, bool in, bool null_free) {
  if (!null_free && in) {
    n->init_req(base_input++, get_null_marker());
  }
  for (uint i = 0; i < field_count(); i++) {
    Node* arg = field_value(i);
    ciField* field = this->field(i);
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    if (field->is_flat()) {
      // Flat inline type field
      arg->as_InlineType()->pass_fields(kit, n, base_input, in);
      if (!field->is_null_free()) {
        assert(field->null_marker_offset() != -1, "inconsistency");
        n->init_req(base_input++, arg->as_InlineType()->get_null_marker());
      }
    } else {
      if (arg->is_InlineType()) {
        // Non-flat inline type field
        InlineTypeNode* vt = arg->as_InlineType();
        assert(n->Opcode() != Op_Return || vt->is_allocated(&kit->gvn()), "inline type field should be allocated on return");
        arg = vt->buffer(kit);
      }
      // Initialize call/return arguments
      n->init_req(base_input++, arg);
      if (field->type()->size() == 2) {
        n->init_req(base_input++, kit->top());
      }
    }
  }
  // The last argument is used to pass the null marker to compiled code and not required here.
  if (!null_free && !in) {
    n->init_req(base_input++, kit->top());
  }
}

void InlineTypeNode::initialize_fields(GraphKit* kit, MultiNode* multi, uint& base_input, bool in, bool no_null_marker, Node* null_check_region, GrowableArray<ciType*>& visited) {
  PhaseGVN& gvn = kit->gvn();
  Node* null_marker = nullptr;
  if (!no_null_marker) {
    // Nullable inline type
    if (in) {
      // Set null marker
      if (multi->is_Start()) {
        null_marker = gvn.transform(new ParmNode(multi->as_Start(), base_input));
      } else {
        null_marker = multi->as_Call()->in(base_input);
      }
      set_req(NullMarker, null_marker);
      base_input++;
    }
    // Add a null check to make subsequent loads dependent on
    assert(null_check_region == nullptr, "already set");
    if (null_marker == nullptr) {
      // Will only be initialized below, use dummy node for now
      null_marker = new Node(1);
      null_marker->init_req(0, kit->control()); // Add an input to prevent dummy from being dead
      gvn.set_type_bottom(null_marker);
    }
    Node* null_ctrl = kit->top();
    kit->null_check_common(null_marker, T_INT, false, &null_ctrl);
    Node* non_null_ctrl = kit->control();
    null_check_region = new RegionNode(3);
    null_check_region->init_req(1, non_null_ctrl);
    null_check_region->init_req(2, null_ctrl);
    null_check_region = gvn.transform(null_check_region);
    kit->set_control(null_check_region);
  }

  for (uint i = 0; i < field_count(); ++i) {
    ciField* field = this->field(i);
    ciType* type = field->type();
    Node* parm = nullptr;
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    if (field->is_flat()) {
      // Flat inline type field
      InlineTypeNode* vt = make_uninitialized(gvn, type->as_inline_klass(), field->is_null_free());
      vt->initialize_fields(kit, multi, base_input, in, true, null_check_region, visited);
      if (!field->is_null_free()) {
        assert(field->null_marker_offset() != -1, "inconsistency");
        Node* null_marker = nullptr;
        if (multi->is_Start()) {
          null_marker = gvn.transform(new ParmNode(multi->as_Start(), base_input));
        } else if (in) {
          null_marker = multi->as_Call()->in(base_input);
        } else {
          null_marker = gvn.transform(new ProjNode(multi->as_Call(), base_input));
        }
        vt->set_req(NullMarker, null_marker);
        base_input++;
      }
      parm = gvn.transform(vt);
    } else {
      if (multi->is_Start()) {
        assert(in, "return from start?");
        parm = gvn.transform(new ParmNode(multi->as_Start(), base_input));
      } else if (in) {
        parm = multi->as_Call()->in(base_input);
      } else {
        parm = gvn.transform(new ProjNode(multi->as_Call(), base_input));
      }
      // Non-flat inline type field
      if (type->is_inlinetype()) {
        if (null_check_region != nullptr) {
          // We limit scalarization for inline types with circular fields and can therefore observe nodes
          // of the same type but with different scalarization depth during GVN. To avoid inconsistencies
          // during merging, make sure that we only create Phis for fields that are guaranteed to be scalarized.
          if (parm->is_InlineType() && kit->C->has_circular_inline_type()) {
            parm = parm->as_InlineType()->get_oop();
          }
          // Holder is nullable, set field to nullptr if holder is nullptr to avoid loading from uninitialized memory
          parm = PhiNode::make(null_check_region, parm, TypeInstPtr::make(TypePtr::BotPTR, type->as_inline_klass()));
          parm->set_req(2, kit->zerocon(T_OBJECT));
          parm = gvn.transform(parm);
        }
        if (visited.contains(type)) {
          kit->C->set_has_circular_inline_type(true);
        } else if (!parm->is_InlineType()) {
          int old_len = visited.length();
          visited.push(type);
          parm = make_from_oop_impl(kit, parm, type->as_inline_klass(), visited);
          visited.trunc_to(old_len);
        }
      }
      base_input += type->size();
    }
    assert(parm != nullptr, "should never be null");
    assert(field_value(i) == nullptr, "already set");
    set_field_value(i, parm);
    gvn.record_for_igvn(parm);
  }
  // The last argument is used to pass the null marker to compiled code
  if (!no_null_marker && !in) {
    Node* cmp = null_marker->raw_out(0);
    null_marker = gvn.transform(new ProjNode(multi->as_Call(), base_input));
    set_req(NullMarker, null_marker);
    gvn.hash_delete(cmp);
    cmp->set_req(1, null_marker);
    gvn.hash_find_insert(cmp);
    gvn.record_for_igvn(cmp);
    base_input++;
  }
}

// Search for multiple allocations of this inline type and try to replace them by dominating allocations.
// Equivalent InlineTypeNodes are merged by GVN, so we just need to search for AllocateNode users to find redundant allocations.
void InlineTypeNode::remove_redundant_allocations(PhaseIdealLoop* phase) {
  PhaseIterGVN* igvn = &phase->igvn();
  // Search for allocations of this inline type. Ignore scalar replaceable ones, they
  // will be removed anyway and changing the memory chain will confuse other optimizations.
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    AllocateNode* alloc = fast_out(i)->isa_Allocate();
    if (alloc != nullptr && alloc->in(AllocateNode::InlineType) == this && !alloc->_is_scalar_replaceable) {
      Node* res = alloc->result_cast();
      if (res == nullptr || !res->is_CheckCastPP()) {
        break; // No unique CheckCastPP
      }
      // Search for a dominating allocation of the same inline type
      Node* res_dom = res;
      for (DUIterator_Fast jmax, j = fast_outs(jmax); j < jmax; j++) {
        AllocateNode* alloc_other = fast_out(j)->isa_Allocate();
        if (alloc_other != nullptr && alloc_other->in(AllocateNode::InlineType) == this && !alloc_other->_is_scalar_replaceable) {
          Node* res_other = alloc_other->result_cast();
          if (res_other != nullptr && res_other->is_CheckCastPP() && res_other != res_dom &&
              phase->is_dominator(res_other->in(0), res_dom->in(0))) {
            res_dom = res_other;
          }
        }
      }
      if (res_dom != res) {
        // Replace allocation by dominating one.
        replace_allocation(igvn, res, res_dom);
        // The result of the dominated allocation is now unused and will be removed
        // later in PhaseMacroExpand::eliminate_allocate_node to not confuse loop opts.
        igvn->_worklist.push(alloc);
      }
    }
  }
}

InlineTypeNode* InlineTypeNode::make_null(PhaseGVN& gvn, ciInlineKlass* vk, bool transform) {
  GrowableArray<ciType*> visited;
  visited.push(vk);
  return make_null_impl(gvn, vk, visited, transform);
}

InlineTypeNode* InlineTypeNode::make_null_impl(PhaseGVN& gvn, ciInlineKlass* vk, GrowableArray<ciType*>& visited, bool transform) {
  InlineTypeNode* vt = new InlineTypeNode(vk, gvn.zerocon(T_OBJECT), /* null_free= */ false);
  vt->set_is_buffered(gvn);
  vt->set_null_marker(gvn, gvn.intcon(0));
  for (uint i = 0; i < vt->field_count(); i++) {
    ciField* field = vt->field(i);
    ciType* ft = field->type();
    Node* value = gvn.zerocon(ft->basic_type());
    assert(!field->is_flat() || field->type()->is_inlinetype(), "must be an inline type");
    if (!field->is_flat() && visited.contains(ft)) {
      gvn.C->set_has_circular_inline_type(true);
    } else if (ft->is_inlinetype()) {
      int old_len = visited.length();
      visited.push(ft);
      value = make_null_impl(gvn, ft->as_inline_klass(), visited);
      visited.trunc_to(old_len);
    }
    vt->set_field_value(i, value);
  }
  return transform ? gvn.transform(vt)->as_InlineType() : vt;
}

InlineTypeNode* InlineTypeNode::clone_if_required(PhaseGVN* gvn, SafePointNode* map, bool safe_for_replace) {
  if (!safe_for_replace || (map == nullptr && outcnt() != 0)) {
    return clone()->as_InlineType();
  }
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    if (fast_out(i) != map) {
      return clone()->as_InlineType();
    }
  }
  gvn->hash_delete(this);
  return this;
}

const Type* InlineTypeNode::Value(PhaseGVN* phase) const {
  Node* oop = get_oop();
  const Type* toop = phase->type(oop);
#ifdef ASSERT
  if (oop->is_Con() && toop->is_zero_type() && _type->isa_oopptr()->is_known_instance()) {
    // We are not allocated (anymore) and should therefore not have an instance id
    dump(1);
    assert(false, "Unbuffered inline type should not have known instance id");
  }
#endif
  if (toop == Type::TOP) {
    return Type::TOP;
  }
  const Type* t = toop->filter_speculative(_type);
  if (t->singleton()) {
    // Don't replace InlineType by a constant
    t = _type;
  }
  const Type* tinit = phase->type(in(NullMarker));
  if (tinit == Type::TOP) {
    return Type::TOP;
  }
  if (tinit->isa_int() && tinit->is_int()->is_con(1)) {
    t = t->join_speculative(TypePtr::NOTNULL);
  }
  return t;
}

InlineTypeNode* LoadFlatNode::load(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* ptr, bool null_free, bool trust_null_free_oop, DecoratorSet decorators) {
  int output_type_size = vk->nof_nonstatic_fields() + (null_free ? 0 : 1);
  const Type** output_types = TypeTuple::fields(output_type_size);
  collect_field_types(vk, output_types + TypeFunc::Parms, 0, output_type_size, null_free, trust_null_free_oop);
  const TypeTuple* type = TypeTuple::make(output_type_size + TypeFunc::Parms, output_types);

  LoadFlatNode* load = new LoadFlatNode(vk, type, null_free, decorators);
  load->init_req(TypeFunc::Control, kit->control());
  load->init_req(TypeFunc::I_O, kit->top());
  load->init_req(TypeFunc::Memory, kit->reset_memory());
  load->init_req(TypeFunc::FramePtr, kit->frameptr());
  load->init_req(TypeFunc::ReturnAdr, kit->top());

  load->init_req(TypeFunc::Parms, base);
  load->init_req(TypeFunc::Parms + 1, ptr);
  kit->kill_dead_locals();
  kit->add_safepoint_edges(load);
  load = kit->gvn().transform(load)->as_LoadFlat();
  kit->record_for_igvn(load);

  kit->set_control(kit->gvn().transform(new ProjNode(load, TypeFunc::Control)));
  kit->set_all_memory(kit->gvn().transform(new ProjNode(load, TypeFunc::Memory)));
  return load->collect_projs(kit, vk, TypeFunc::Parms, null_free);
}

bool LoadFlatNode::expand_constant(PhaseIterGVN& igvn, ciInstance* inst) const {
  precond(inst != nullptr);
  assert(igvn.delay_transform(), "transformation must be delayed");
  if ((_decorators & C2_MISMATCHED) != 0) {
    return false;
  }

  GraphKit kit(this, igvn);
  for (int i = 0; i < _vk->nof_nonstatic_fields(); i++) {
    ProjNode* proj_out = proj_out_or_null(TypeFunc::Parms + i);
    if (proj_out == nullptr) {
      continue;
    }

    ciField* field = _vk->nonstatic_field_at(i);
    BasicType bt = field->type()->basic_type();
    if (inst == nullptr) {
      Node* cst_node = igvn.zerocon(bt);
      igvn.replace_node(proj_out, cst_node);
    } else {
      bool is_unsigned_load = bt == T_BOOLEAN || bt == T_CHAR;
      const Type* cst_type = Type::make_constant_from_field(field, inst, bt, is_unsigned_load);
      Node* cst_node = igvn.makecon(cst_type);
      igvn.replace_node(proj_out, cst_node);
    }
  }

  if (!_null_free) {
    ProjNode* proj_out = proj_out_or_null(TypeFunc::Parms + _vk->nof_nonstatic_fields());
    if (proj_out != nullptr) {
      igvn.replace_node(proj_out, igvn.intcon(1));
    }
  }

  Node* old_ctrl = proj_out_or_null(TypeFunc::Control);
  if (old_ctrl != nullptr) {
    igvn.replace_node(old_ctrl, kit.control());
  }
  Node* old_mem = proj_out_or_null(TypeFunc::Memory);
  Node* new_mem = kit.reset_memory();
  if (old_mem != nullptr) {
    igvn.replace_node(old_mem, new_mem);
  }
  return true;
}

bool LoadFlatNode::expand_non_atomic(PhaseIterGVN& igvn) {
  assert(igvn.delay_transform(), "transformation must be delayed");
  if ((_decorators & C2_MISMATCHED) != 0) {
    return false;
  }

  GraphKit kit(this, igvn);
  Node* base = this->base();
  Node* ptr = this->ptr();

  for (int i = 0; i < _vk->nof_nonstatic_fields(); i++) {
    ProjNode* proj_out = proj_out_or_null(TypeFunc::Parms + i);
    if (proj_out == nullptr) {
      continue;
    }

    ciField* field = _vk->nonstatic_field_at(i);
    Node* field_ptr = kit.basic_plus_adr(base, ptr, field->offset_in_bytes() - _vk->payload_offset());
    const TypePtr* field_ptr_type = field_ptr->Value(&igvn)->is_ptr();
    igvn.set_type(field_ptr, field_ptr_type);

    Node* field_value = kit.access_load_at(base, field_ptr, field_ptr_type, igvn.type(proj_out), field->type()->basic_type(), _decorators);
    igvn.replace_node(proj_out, field_value);
  }

  if (!_null_free) {
    ProjNode* proj_out = proj_out_or_null(TypeFunc::Parms + _vk->nof_nonstatic_fields());
    if (proj_out != nullptr) {
      Node* null_marker_ptr = kit.basic_plus_adr(base, ptr, _vk->null_marker_offset_in_payload());
      const TypePtr* null_marker_ptr_type = null_marker_ptr->Value(&igvn)->is_ptr();
      igvn.set_type(null_marker_ptr, null_marker_ptr_type);
      Node* null_marker_value = kit.access_load_at(base, null_marker_ptr, null_marker_ptr_type, TypeInt::BOOL, T_BOOLEAN, _decorators);
      igvn.replace_node(proj_out, null_marker_value);
    }
  }

  Node* old_ctrl = proj_out_or_null(TypeFunc::Control);
  if (old_ctrl != nullptr) {
    igvn.replace_node(old_ctrl, kit.control());
  }
  Node* old_mem = proj_out_or_null(TypeFunc::Memory);
  Node* new_mem = kit.reset_memory();
  if (old_mem != nullptr) {
    igvn.replace_node(old_mem, new_mem);
  }
  return true;
}

void LoadFlatNode::expand_atomic(PhaseIterGVN& igvn) {
  assert(igvn.delay_transform(), "transformation must be delayed");
  GraphKit kit(this, igvn);
  Node* base = this->base();
  Node* ptr = this->ptr();

  BasicType payload_bt = _vk->atomic_size_to_basic_type(_null_free);
  kit.insert_mem_bar(Op_MemBarCPUOrder);
  Node* payload = kit.access_load_at(base, ptr, TypeRawPtr::BOTTOM, Type::get_const_basic_type(payload_bt), payload_bt,
                                     _decorators | C2_MISMATCHED | C2_CONTROL_DEPENDENT_LOAD | C2_UNKNOWN_CONTROL_LOAD, kit.control());
  kit.insert_mem_bar(Op_MemBarCPUOrder);

  Node* old_ctrl = proj_out_or_null(TypeFunc::Control);
  if (old_ctrl != nullptr) {
    igvn.replace_node(old_ctrl, kit.control());
  }
  Node* old_mem = proj_out_or_null(TypeFunc::Memory);
  Node* new_mem = kit.reset_memory();
  if (old_mem != nullptr) {
    igvn.replace_node(old_mem, new_mem);
  }

  expand_projs_atomic(igvn, kit.control(), payload);
}

void LoadFlatNode::collect_field_types(ciInlineKlass* vk, const Type** field_types, int idx, int limit, bool null_free, bool trust_null_free_oop) {
  assert(null_free || !trust_null_free_oop, "cannot trust null-free oop when the holder object is not null-free");
  for (int i = 0; i < vk->nof_declared_nonstatic_fields(); i++) {
    ciField* field = vk->declared_nonstatic_field_at(i);
    if (field->is_flat()) {
      ciInlineKlass* field_klass = field->type()->as_inline_klass();
      collect_field_types(field_klass, field_types, idx, limit, field->is_null_free(), trust_null_free_oop && field->is_null_free());
      idx += field_klass->nof_nonstatic_fields() + (field->is_null_free() ? 0 : 1);
      continue;
    }

    const Type* field_type = Type::get_const_type(field->type());
    if (trust_null_free_oop && field->is_null_free()) {
      field_type = field_type->filter(TypePtr::NOTNULL);
    }

    assert(idx >= 0 && idx < limit, "field type out of bounds, %d - %d", idx, limit);
    field_types[idx] = field_type;
    idx++;
  }

  if (!null_free) {
    assert(idx >= 0 && idx < limit, "field type out of bounds, %d - %d", idx, limit);
    field_types[idx] = TypeInt::BOOL;
  }
}

// Create an InlineTypeNode from a LoadFlatNode with its fields being extracted from the
// LoadFlatNode
InlineTypeNode* LoadFlatNode::collect_projs(GraphKit* kit, ciInlineKlass* vk, int proj_con, bool null_free) {
  PhaseGVN& gvn = kit->gvn();
  InlineTypeNode* res = InlineTypeNode::make_uninitialized(gvn, vk, null_free);
  for (int i = 0; i < vk->nof_declared_nonstatic_fields(); i++) {
    ciField* field = vk->declared_nonstatic_field_at(i);
    Node* field_value;
    if (field->is_flat()) {
      ciInlineKlass* field_klass = field->type()->as_inline_klass();
      field_value = collect_projs(kit, field_klass, proj_con, field->is_null_free());
      proj_con += field_klass->nof_nonstatic_fields() + (field->is_null_free() ? 0 : 1);
    } else {
      field_value = gvn.transform(new ProjNode(this, proj_con));
      if (field->type()->is_inlinetype()) {
        field_value = InlineTypeNode::make_from_oop(kit, field_value, field->type()->as_inline_klass());
      }
      proj_con++;
    }
    res->set_field_value(i, field_value);
  }

  if (null_free) {
    res->set_null_marker(gvn);
  } else {
    res->set_null_marker(gvn, gvn.transform(new ProjNode(this, proj_con)));
  }
  return gvn.transform(res)->as_InlineType();
}

// Extract the values of the flattened fields from the loaded payload
void LoadFlatNode::expand_projs_atomic(PhaseIterGVN& igvn, Node* ctrl, Node* payload) {
  BasicType payload_bt = _vk->atomic_size_to_basic_type(_null_free);
  for (int i = 0; i < _vk->nof_nonstatic_fields(); i++) {
    ProjNode* proj_out = proj_out_or_null(TypeFunc::Parms + i);
    if (proj_out == nullptr) {
      continue;
    }

    ciField* field = _vk->nonstatic_field_at(i);
    int field_offset = field->offset_in_bytes() - _vk->payload_offset();
    const Type* field_type = igvn.type(proj_out);
    Node* field_value = get_payload_value(igvn, ctrl, payload_bt, payload, field_type, field->type()->basic_type(), field_offset);
    igvn.replace_node(proj_out, field_value);
  }

  if (!_null_free) {
    ProjNode* proj_out = proj_out_or_null(TypeFunc::Parms + _vk->nof_nonstatic_fields());
    if (proj_out == nullptr) {
      return;
    }

    int null_marker_offset = _vk->null_marker_offset_in_payload();
    Node* null_marker_value = get_payload_value(igvn, ctrl, payload_bt, payload, TypeInt::BOOL, T_BOOLEAN, null_marker_offset);
    igvn.replace_node(proj_out, null_marker_value);
  }
}

Node* LoadFlatNode::get_payload_value(PhaseIterGVN& igvn, Node* ctrl, BasicType payload_bt, Node* payload, const Type* value_type, BasicType value_bt, int offset) {
  assert((offset + type2aelembytes(value_bt)) <= type2aelembytes(payload_bt), "Value does not fit into payload");
  Node* value = nullptr;
  // Shift to the right position in the long value
  Node* shift_val = igvn.intcon(offset << LogBitsPerByte);
  if (payload_bt == T_LONG) {
    value = igvn.transform(new URShiftLNode(payload, shift_val));
    value = igvn.transform(new ConvL2INode(value));
  } else {
    value = igvn.transform(new URShiftINode(payload, shift_val));
  }

  if (value_bt == T_INT) {
    return value;
  } else if (!is_java_primitive(value_bt)) {
    assert(UseCompressedOops && payload_bt == T_LONG, "Naturally atomic");
    value = igvn.transform(new CastI2NNode(ctrl, value, value_type->make_narrowoop()));
    value = igvn.transform(new DecodeNNode(value, value_type));

    // Similar to CheckCastPP nodes with raw input, CastI2N nodes require special handling in 'PhaseCFG::schedule_late' to ensure the
    // register allocator does not move the CastI2N below a safepoint. This is necessary to avoid having the raw pointer span a safepoint,
    // making it opaque to the GC. Unlike CheckCastPPs, which need extra handling in 'Scheduling::ComputeRegisterAntidependencies' due to
    // scalarization, CastI2N nodes are always used by a load if scalarization happens which inherently keeps them pinned above the safepoint.
    return value;
  } else {
    // Make sure to zero unused bits in the 32-bit value
    return Compile::narrow_value(value_bt, value, nullptr, &igvn, true);
  }
}

void StoreFlatNode::store(GraphKit* kit, Node* base, Node* ptr, InlineTypeNode* value, bool null_free, DecoratorSet decorators) {
  value = value->allocate_fields(kit);
  StoreFlatNode* store = new StoreFlatNode(null_free, decorators);
  store->init_req(TypeFunc::Control, kit->control());
  store->init_req(TypeFunc::I_O, kit->top());
  store->init_req(TypeFunc::Memory, kit->reset_memory());
  store->init_req(TypeFunc::FramePtr, kit->frameptr());
  store->init_req(TypeFunc::ReturnAdr, kit->top());

  store->init_req(TypeFunc::Parms, base);
  store->init_req(TypeFunc::Parms + 1, ptr);
  store->init_req(TypeFunc::Parms + 2, value);
  kit->kill_dead_locals();
  kit->add_safepoint_edges(store);
  store = kit->gvn().transform(store)->as_StoreFlat();
  kit->record_for_igvn(store);

  kit->set_control(kit->gvn().transform(new ProjNode(store, TypeFunc::Control)));
  kit->set_all_memory(kit->gvn().transform(new ProjNode(store, TypeFunc::Memory)));
}

bool StoreFlatNode::expand_non_atomic(PhaseIterGVN& igvn) {
  assert(igvn.delay_transform(), "transformation must be delayed");
  if ((_decorators & C2_MISMATCHED) != 0) {
    return false;
  }

  GraphKit kit(this, igvn);
  Node* base = this->base();
  Node* ptr = this->ptr();
  InlineTypeNode* value = this->value();

  ciInlineKlass* vk = igvn.type(value)->inline_klass();
  for (int i = 0; i < vk->nof_nonstatic_fields(); i++) {
    ciField* field = vk->nonstatic_field_at(i);
    Node* field_ptr = kit.basic_plus_adr(base, ptr, field->offset_in_bytes() - vk->payload_offset());
    const TypePtr* field_ptr_type = field_ptr->Value(&igvn)->is_ptr();
    igvn.set_type(field_ptr, field_ptr_type);
    Node* field_value = value->field_value_by_offset(field->offset_in_bytes(), true);
    Node* store = kit.access_store_at(base, field_ptr, field_ptr_type, field_value, igvn.type(field_value), field->type()->basic_type(), _decorators);
  }

  if (!_null_free) {
    Node* null_marker_ptr = kit.basic_plus_adr(base, ptr, vk->null_marker_offset_in_payload());
    const TypePtr* null_marker_ptr_type = null_marker_ptr->Value(&igvn)->is_ptr();
    igvn.set_type(null_marker_ptr, null_marker_ptr_type);
    Node* null_marker_value = value->get_null_marker();
    Node* store = kit.access_store_at(base, null_marker_ptr, null_marker_ptr_type, null_marker_value, TypeInt::BOOL, T_BOOLEAN, _decorators);
  }

  Node* old_ctrl = proj_out_or_null(TypeFunc::Control);
  if (old_ctrl != nullptr) {
    igvn.replace_node(old_ctrl, kit.control());
  }
  Node* old_mem = proj_out_or_null(TypeFunc::Memory);
  Node* new_mem = kit.reset_memory();
  if (old_mem != nullptr) {
    igvn.replace_node(old_mem, new_mem);
  }
  return true;
}

void StoreFlatNode::expand_atomic(PhaseIterGVN& igvn) {
  // Convert to a payload value <= 64-bit and write atomically.
  // The payload might contain at most two oop fields that must be narrow because otherwise they would be 64-bit
  // in size and would then be written by a "normal" oop store. If the payload contains oops, its size is always
  // 64-bit because the next smaller (power-of-two) size would be 32-bit which could only hold one narrow oop that
  // would then be written by a normal narrow oop store. These properties are asserted in 'convert_to_payload'.
  assert(igvn.delay_transform(), "transformation must be delayed");
  GraphKit kit(this, igvn);
  Node* base = this->base();
  Node* ptr = this->ptr();
  InlineTypeNode* value = this->value();

  int oop_off_1 = -1;
  int oop_off_2 = -1;
  Node* payload = convert_to_payload(igvn, kit.control(), value, _null_free, oop_off_1, oop_off_2);

  ciInlineKlass* vk = igvn.type(value)->inline_klass();
  assert(oop_off_1 == -1 || oop_off_1 == 0 || oop_off_1 == 4, "invalid layout for %s, first oop at offset %d", vk->name()->as_utf8(), oop_off_1);
  assert(oop_off_2 == -1 || oop_off_2 == 4, "invalid layout for %s, second oop at offset %d", vk->name()->as_utf8(), oop_off_2);
  BasicType payload_bt = vk->atomic_size_to_basic_type(_null_free);
  kit.insert_mem_bar(Op_MemBarCPUOrder);
  if (!UseG1GC || oop_off_1 == -1) {
    // No oop fields or no late barrier expansion. Emit an atomic store of the payload and add GC barriers if needed.
    assert(oop_off_2 == -1 || !UseG1GC, "sanity");
    // ZGC does not support compressed oops, so only one oop can be in the payload which is written by a "normal" oop store.
    assert((oop_off_1 == -1 && oop_off_2 == -1) || !UseZGC, "ZGC does not support embedded oops in flat fields");
    kit.access_store_at(base, ptr, TypeRawPtr::BOTTOM, payload, Type::get_const_basic_type(payload_bt), payload_bt, _decorators | C2_MISMATCHED, true, value);
  } else {
    // Contains oops and requires late barrier expansion. Emit a special store node that allows to emit GC barriers in the backend.
    assert(UseG1GC, "Unexpected GC");
    assert(payload_bt == T_LONG, "Unexpected payload type");
    // If one oop, set the offset (if no offset is set, two oops are assumed by the backend)
    Node* oop_offset = (oop_off_2 == -1) ? igvn.intcon(oop_off_1) : nullptr;
    Node* mem = kit.reset_memory();
    kit.set_all_memory(mem);
    Node* store = igvn.transform(new StoreLSpecialNode(kit.control(), mem, ptr, TypeRawPtr::BOTTOM, payload, oop_offset, MemNode::unordered));
    kit.set_memory(store, TypeRawPtr::BOTTOM);
  }
  kit.insert_mem_bar(Op_MemBarCPUOrder);

  Node* old_ctrl = proj_out_or_null(TypeFunc::Control);
  if (old_ctrl != nullptr) {
    igvn.replace_node(old_ctrl, kit.control());
  }
  Node* old_mem = proj_out_or_null(TypeFunc::Memory);
  Node* new_mem = kit.reset_memory();
  if (old_mem != nullptr) {
    igvn.replace_node(old_mem, new_mem);
  }
}

// Convert the field values to a payload value of type 'bt'
Node* StoreFlatNode::convert_to_payload(PhaseIterGVN& igvn, Node* ctrl, InlineTypeNode* value, bool null_free, int& oop_off_1, int& oop_off_2) {
  ciInlineKlass* vk = igvn.type(value)->inline_klass();
  BasicType payload_bt = vk->atomic_size_to_basic_type(null_free);
  Node* payload = igvn.zerocon(payload_bt);
  if (!null_free) {
    // Set the null marker
    payload = set_payload_value(igvn, payload_bt, payload, T_BOOLEAN, value->get_null_marker(), vk->null_marker_offset_in_payload());
  }

  // Iterate over the fields and add their values to the payload
  for (int i = 0; i < vk->nof_nonstatic_fields(); i++) {
    ciField* field = vk->nonstatic_field_at(i);
    Node* field_value = value->field_value_by_offset(field->offset_in_bytes(), true);
    ciType* field_klass = field->type();
    BasicType field_bt = field_klass->basic_type();
    int field_offset_in_payload = field->offset_in_bytes() - vk->payload_offset();
    if (!field_klass->is_primitive_type()) {
      // Narrow oop field
      assert(UseCompressedOops && payload_bt == T_LONG, "Naturally atomic");
      if (oop_off_1 == -1) {
        oop_off_1 = field_offset_in_payload;
      } else {
        assert(oop_off_2 == -1, "already set");
        oop_off_2 = field_offset_in_payload;
      }

      const Type* val_type = Type::get_const_type(field_klass)->make_narrowoop();
      if (field_value->is_InlineType()) {
        assert(field_value->as_InlineType()->is_allocated(&igvn), "must be allocated");
      }

      field_value = igvn.transform(new EncodePNode(field_value, val_type));
      field_value = igvn.transform(new CastP2XNode(ctrl, field_value));
      field_value = igvn.transform(new ConvL2INode(field_value));
      field_bt = T_INT;
    }
    payload = set_payload_value(igvn, payload_bt, payload, field_bt, field_value, field_offset_in_payload);
  }

  return payload;
}

Node* StoreFlatNode::set_payload_value(PhaseIterGVN& igvn, BasicType payload_bt, Node* payload, BasicType val_bt, Node* value, int offset) {
  assert((offset + type2aelembytes(val_bt)) <= type2aelembytes(payload_bt), "Value does not fit into payload");

  // Make sure to zero unused bits in the 32-bit value
  if (val_bt == T_BYTE || val_bt == T_BOOLEAN) {
    value = igvn.transform(new AndINode(value, igvn.intcon(0xFF)));
  } else if (val_bt == T_CHAR || val_bt == T_SHORT) {
    value = igvn.transform(new AndINode(value, igvn.intcon(0xFFFF)));
  } else if (val_bt == T_FLOAT) {
    value = igvn.transform(new MoveF2INode(value));
  } else {
    assert(val_bt == T_INT, "Unsupported type: %s", type2name(val_bt));
  }

  Node* shift_val = igvn.intcon(offset << LogBitsPerByte);
  if (payload_bt == T_LONG) {
    // Convert to long and remove the sign bit (the backend will fold this and emit a zero extend i2l)
    value = igvn.transform(new ConvI2LNode(value));
    value = igvn.transform(new AndLNode(value, igvn.longcon(0xFFFFFFFF)));

    Node* shift_value = igvn.transform(new LShiftLNode(value, shift_val));
    payload = new OrLNode(shift_value, payload);
  } else {
    Node* shift_value = igvn.transform(new LShiftINode(value, shift_val));
    payload = new OrINode(shift_value, payload);
  }
  return igvn.transform(payload);
}

const Type* LoadFlatNode::Value(PhaseGVN* phase) const {
  if (phase->type(in(TypeFunc::Control)) == Type::TOP || phase->type(in(TypeFunc::Memory)) == Type::TOP ||
      phase->type(base()) == Type::TOP || phase->type(ptr()) == Type::TOP) {
    return Type::TOP;
  }
  return bottom_type();
}

const Type* StoreFlatNode::Value(PhaseGVN* phase) const {
  if (phase->type(in(TypeFunc::Control)) == Type::TOP || phase->type(in(TypeFunc::Memory)) == Type::TOP ||
      phase->type(base()) == Type::TOP || phase->type(ptr()) == Type::TOP || phase->type(value()) == Type::TOP) {
    return Type::TOP;
  }
  return bottom_type();
}

/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_INLINETYPENODE_HPP
#define SHARE_VM_OPTO_INLINETYPENODE_HPP

#include "ci/ciInlineKlass.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "oops/accessDecorators.hpp"
#include "opto/callnode.hpp"
#include "opto/compile.hpp"
#include "opto/loopnode.hpp"
#include "opto/multnode.hpp"
#include "opto/node.hpp"

class GraphKit;

//------------------------------InlineTypeNode-------------------------------------
// Node representing an inline type in C2 IR
class InlineTypeNode : public TypeNode {
private:
  InlineTypeNode(ciInlineKlass* vk, Node* oop, bool null_free)
      : TypeNode(TypeInstPtr::make(null_free ? TypePtr::NotNull : TypePtr::BotPTR, vk), Values + vk->nof_declared_nonstatic_fields()) {
    init_class_id(Class_InlineType);
    init_req(Oop, oop);
    Compile::current()->add_inline_type(this);
  }

  enum { Control,    // Control input.
         Oop,        // Oop to heap allocated buffer.
         IsBuffered, // True if inline type is heap allocated (or nullptr), false otherwise.
         NullMarker, // Needs to be checked before using the field values.
                     // 0 => InlineType is null
                     // 1 => InlineType is non-null
                     // Can be dynamic value, not necessarily statically known
         Values      // Nodes corresponding to values of the inline type's fields.
                     // Nodes are connected in increasing order of the index of the field they correspond to.
  };

  // Get the klass defining the field layout of the inline type
  ciInlineKlass* inline_klass() const { return type()->inline_klass(); }

  void make_scalar_in_safepoint(PhaseIterGVN* igvn, Unique_Node_List& worklist, SafePointNode* sfpt);
  uint add_fields_to_safepoint(Unique_Node_List& worklist, SafePointNode* sfpt);

  // Checks if the inline type is loaded from memory and if so returns the oop
  Node* is_loaded(PhaseGVN* phase, ciInlineKlass* vk = nullptr, Node* base = nullptr, int holder_offset = 0);

  // Initialize the inline type fields with the inputs or outputs of a MultiNode
  void initialize_fields(GraphKit* kit, MultiNode* multi, uint& base_input, bool in, bool no_null_marker, Node* null_check_region, GrowableArray<ciType*>& visited);
  // Initialize the inline type by loading its field values from memory
  void load(GraphKit* kit, Node* base, Node* ptr, bool immutable_memory, bool trust_null_free_oop, DecoratorSet decorators, GrowableArray<ciType*>& visited);
  // Store the field values to memory
  void store(GraphKit* kit, Node* base, Node* ptr, bool immutable_memory, DecoratorSet decorators) const;

  InlineTypeNode* adjust_scalarization_depth_impl(GraphKit* kit, GrowableArray<ciType*>& visited);

  static InlineTypeNode* make_all_zero_impl(PhaseGVN& gvn, ciInlineKlass* vk, GrowableArray<ciType*>& visited);
  static InlineTypeNode* make_from_oop_impl(GraphKit* kit, Node* oop, ciInlineKlass* vk, GrowableArray<ciType*>& visited);
  static InlineTypeNode* make_null_impl(PhaseGVN& gvn, ciInlineKlass* vk, GrowableArray<ciType*>& visited, bool transform = true);
  static InlineTypeNode* make_from_flat_impl(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* ptr, bool atomic, bool immutable_memory,
                                             bool null_free, bool trust_null_free_oop, DecoratorSet decorators, GrowableArray<ciType*>& visited);

public:
  // Create with all-zero field values
  static InlineTypeNode* make_all_zero(PhaseGVN& gvn, ciInlineKlass* vk);
  // Create uninitialized
  static InlineTypeNode* make_uninitialized(PhaseGVN& gvn, ciInlineKlass* vk, bool null_free = true);
  // Create and initialize by loading the field values from an oop
  static InlineTypeNode* make_from_oop(GraphKit* kit, Node* oop, ciInlineKlass* vk);
  // Create and initialize by loading the field values from a flat field or array
  static InlineTypeNode* make_from_flat(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* ptr,
                                        bool atomic, bool immutable_memory, bool null_free, DecoratorSet decorators);
  static InlineTypeNode* make_from_flat_array(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* idx);
  // Create and initialize with the inputs or outputs of a MultiNode (method entry or call)
  static InlineTypeNode* make_from_multi(GraphKit* kit, MultiNode* multi, ciInlineKlass* vk, uint& base_input, bool in, bool null_free = true);
  // Create with null field values
  static InlineTypeNode* make_null(PhaseGVN& gvn, ciInlineKlass* vk, bool transform = true);

  // Support for control flow merges
  bool has_phi_inputs(Node* region);
  InlineTypeNode* clone_with_phis(PhaseGVN* gvn, Node* region, SafePointNode* map = nullptr, bool is_non_null = false, bool init_with_top = false);
  InlineTypeNode* merge_with(PhaseGVN* gvn, const InlineTypeNode* other, int phi_index, bool transform);
  void add_new_path(Node* region);

  // Get oop for heap allocated inline type (may be TypePtr::NULL_PTR)
  Node* get_oop() const    { return in(Oop); }
  void  set_oop(PhaseGVN& gvn, Node* oop) { set_req_X(Oop, oop, &gvn); }
  Node* get_null_marker() const { return in(NullMarker); }
  void  set_null_marker(PhaseGVN& gvn, Node* init) { set_req_X(NullMarker, init, &gvn); }
  void  set_null_marker(PhaseGVN& gvn) { set_null_marker(gvn, gvn.intcon(1)); }
  Node* get_is_buffered() const { return in(IsBuffered); }
  void  set_is_buffered(PhaseGVN& gvn, bool buffered = true) { set_req_X(IsBuffered, gvn.intcon(buffered ? 1 : 0), &gvn); }

  // Checks if the inline type fields are all set to zero
  bool is_all_zero(PhaseGVN* gvn, bool flat = false) const;

  // Inline type fields
  uint          field_count() const { return req() - Values; }
  ciField*      field(uint index) const;
  Node*         field_value(uint index) const;
  Node*         field_value_by_offset(int offset, bool recursive) const;
  void      set_field_value(uint index, Node* value);
  void      set_field_value_by_offset(int offset, Node* value);
  uint          field_index(int offset) const;

  // Replace InlineTypeNodes in debug info at safepoints with SafePointScalarObjectNodes
  void make_scalar_in_safepoints(PhaseIterGVN* igvn, bool allow_oop = true);

  // Store the inline type as a flat (headerless) representation
  void store_flat(GraphKit* kit, Node* base, Node* ptr, bool atomic, bool immutable_memory, bool null_free, DecoratorSet decorators);
  // Store the inline type as a flat (headerless) representation into an array
  void store_flat_array(GraphKit* kit, Node* base, Node* idx);
  // Make sure that inline type is fully scalarized
  InlineTypeNode* adjust_scalarization_depth(GraphKit* kit);

  // Implementation of the substitutability check for acmp
  bool can_emit_substitutability_check(Node* other) const;
  void check_substitutability(PhaseIterGVN* igvn, RegionNode* region, Node* phi, Node** ctrl, Node* mem, Node* base, Node* other, bool flat = false) const;

  // Allocates the inline type (if not yet allocated)
  InlineTypeNode* buffer(GraphKit* kit, bool safe_for_replace = true);
  bool is_allocated(PhaseGVN* phase) const;

  void replace_call_results(GraphKit* kit, CallNode* call, Compile* C);
  void replace_field_projs(Compile* C, CallNode* call, uint& proj_idx);

  // Allocate all non-flat inline type fields
  InlineTypeNode* allocate_fields(GraphKit* kit);

  Node* tagged_klass(PhaseGVN& gvn) {
    return tagged_klass(inline_klass(), gvn);
  }
  static Node* tagged_klass(ciInlineKlass* vk, PhaseGVN& gvn);
  // Pass inline type as fields at a call or return
  void pass_fields(GraphKit* kit, Node* n, uint& base_input, bool in, bool null_free = true);

  // Allocation optimizations
  void remove_redundant_allocations(PhaseIdealLoop* phase);

  InlineTypeNode* clone_if_required(PhaseGVN* gvn, SafePointNode* map, bool safe_for_replace = true);

  virtual const Type* Value(PhaseGVN* phase) const;

  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

  virtual int Opcode() const;
};

// Load from a flat element, the node produces 1 Proj output for each flattened field of the flat
// element. The order of the Proj node is the same as that of _vk->_nonstatic_fields, and the null
// marker if existing will be the last Proj output. This node acts as if the load happens
// atomically and will be expanded to loading the whole payload and extracting the flattened fields
// from the loaded payload. In special cases, such as when the object from which this load reads
// does not escape, this node can be expanded to multiple loads from each flattened field.
// This node allows us to replace its results with the value from a matching store because the
// payload value cannot be directly propagated if it contains oops. This effect, in turn, allows
// objects with atomic flat fields to be scalar replaced.
class LoadFlatNode final : public SafePointNode {
private:
  ciInlineKlass* _vk;
  const TypeTuple* _type;
  bool _null_free;
  DecoratorSet _decorators;

public:
  static InlineTypeNode* load(GraphKit* kit, ciInlineKlass* vk, Node* base, Node* ptr, bool null_free, bool trust_null_free_oop, DecoratorSet decorators);
  Node* base() const { return in(TypeFunc::Parms); }
  Node* ptr() const { return in(TypeFunc::Parms + 1); }
  bool expand_constant(PhaseIterGVN& igvn, ciInstance* inst) const;
  bool expand_non_atomic(PhaseIterGVN& igvn);
  void expand_atomic(PhaseIterGVN& igvn);

private:
  LoadFlatNode(ciInlineKlass* vk, const TypeTuple* type, bool null_free, DecoratorSet decorators)
    : SafePointNode(TypeFunc::Parms + 2, nullptr, TypePtr::BOTTOM), _vk(vk), _type(type), _null_free(null_free), _decorators(decorators) {
    init_class_id(Class_LoadFlat);
    Compile::current()->add_flat_access(this);
  }

  virtual int Opcode() const override;
  virtual const Type* bottom_type() const override { return _type; }
  virtual uint size_of() const override { return sizeof(LoadFlatNode); }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape) override { return nullptr; }
  virtual Node* Identity(PhaseGVN* phase) override { return this; }
  virtual const Type* Value(PhaseGVN* phase) const override;

  static void collect_field_types(ciInlineKlass* vk, const Type** field_types, int idx, int limit, bool null_free, bool trust_null_free_oop);
  InlineTypeNode* collect_projs(GraphKit* kit, ciInlineKlass* vk, int proj_con, bool null_free);
  void expand_projs_atomic(PhaseIterGVN& gvn, Node* ctrl, Node* payload);
  static Node* get_payload_value(PhaseIterGVN& igvn, Node* ctrl, BasicType payload_bt, Node* payload, const Type* value_type, BasicType value_bt, int offset);
};

// Store an InlineTypeNode to a flat element, the store acts as if it is atomic. Similar to
// LoadFlatNode, this node is expanded to storing a payload created from the field values of the
// InlineTypeNode, and under special circumstances, when there is no racing access to the field,
// this node can be expanded to multiple stores to each flattened field.
// The purposes of this node complement those of LoadFlatNode.
class StoreFlatNode final : public SafePointNode {
private:
  bool _null_free;
  DecoratorSet _decorators;

public:
  static void store(GraphKit* kit, Node* base, Node* ptr, InlineTypeNode* value, bool null_free, DecoratorSet decorators);
  Node* base() const { return in(TypeFunc::Parms); }
  Node* ptr() const { return in(TypeFunc::Parms + 1); }
  InlineTypeNode* value() const { return in(TypeFunc::Parms + 2)->as_InlineType(); }
  bool expand_non_atomic(PhaseIterGVN& igvn);
  void expand_atomic(PhaseIterGVN& igvn);

private:
  StoreFlatNode(bool null_free, DecoratorSet decorators)
    : SafePointNode(TypeFunc::Parms + 3, nullptr, TypePtr::BOTTOM), _null_free(null_free), _decorators(decorators) {
    init_class_id(Class_StoreFlat);
    Compile::current()->add_flat_access(this);
  }

  virtual int Opcode() const override;
  virtual const Type* bottom_type() const override { return TypeTuple::MEMBAR; }
  virtual uint size_of() const override { return sizeof(StoreFlatNode); }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape) override { return nullptr; }
  virtual Node* Identity(PhaseGVN* phase) override { return this; }
  virtual const Type* Value(PhaseGVN* phase) const override;

  static Node* convert_to_payload(PhaseIterGVN& igvn, Node* ctrl, InlineTypeNode* value, bool null_free, int& oop_off_1, int& oop_off_2);
  static Node* set_payload_value(PhaseIterGVN& igvn, BasicType payload_bt, Node* payload, BasicType val_bt, Node* value, int offset);
};

#endif // SHARE_VM_OPTO_INLINETYPENODE_HPP

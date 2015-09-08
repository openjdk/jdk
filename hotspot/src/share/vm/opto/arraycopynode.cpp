/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/arraycopynode.hpp"
#include "opto/graphKit.hpp"

ArrayCopyNode::ArrayCopyNode(Compile* C, bool alloc_tightly_coupled)
  : CallNode(arraycopy_type(), NULL, TypeRawPtr::BOTTOM),
    _alloc_tightly_coupled(alloc_tightly_coupled),
    _kind(None),
    _arguments_validated(false),
    _src_type(TypeOopPtr::BOTTOM),
    _dest_type(TypeOopPtr::BOTTOM) {
  init_class_id(Class_ArrayCopy);
  init_flags(Flag_is_macro);
  C->add_macro_node(this);
}

uint ArrayCopyNode::size_of() const { return sizeof(*this); }

ArrayCopyNode* ArrayCopyNode::make(GraphKit* kit, bool may_throw,
                                   Node* src, Node* src_offset,
                                   Node* dest, Node* dest_offset,
                                   Node* length,
                                   bool alloc_tightly_coupled,
                                   Node* src_klass, Node* dest_klass,
                                   Node* src_length, Node* dest_length) {

  ArrayCopyNode* ac = new ArrayCopyNode(kit->C, alloc_tightly_coupled);
  Node* prev_mem = kit->set_predefined_input_for_runtime_call(ac);

  ac->init_req(ArrayCopyNode::Src, src);
  ac->init_req(ArrayCopyNode::SrcPos, src_offset);
  ac->init_req(ArrayCopyNode::Dest, dest);
  ac->init_req(ArrayCopyNode::DestPos, dest_offset);
  ac->init_req(ArrayCopyNode::Length, length);
  ac->init_req(ArrayCopyNode::SrcLen, src_length);
  ac->init_req(ArrayCopyNode::DestLen, dest_length);
  ac->init_req(ArrayCopyNode::SrcKlass, src_klass);
  ac->init_req(ArrayCopyNode::DestKlass, dest_klass);

  if (may_throw) {
    ac->set_req(TypeFunc::I_O , kit->i_o());
    kit->add_safepoint_edges(ac, false);
  }

  return ac;
}

void ArrayCopyNode::connect_outputs(GraphKit* kit) {
  kit->set_all_memory_call(this, true);
  kit->set_control(kit->gvn().transform(new ProjNode(this,TypeFunc::Control)));
  kit->set_i_o(kit->gvn().transform(new ProjNode(this, TypeFunc::I_O)));
  kit->make_slow_call_ex(this, kit->env()->Throwable_klass(), true);
  kit->set_all_memory_call(this);
}

#ifndef PRODUCT
const char* ArrayCopyNode::_kind_names[] = {"arraycopy", "arraycopy, validated arguments", "clone", "oop array clone", "CopyOf", "CopyOfRange"};

void ArrayCopyNode::dump_spec(outputStream *st) const {
  CallNode::dump_spec(st);
  st->print(" (%s%s)", _kind_names[_kind], _alloc_tightly_coupled ? ", tightly coupled allocation" : "");
}

void ArrayCopyNode::dump_compact_spec(outputStream* st) const {
  st->print("%s%s", _kind_names[_kind], _alloc_tightly_coupled ? ",tight" : "");
}
#endif

intptr_t ArrayCopyNode::get_length_if_constant(PhaseGVN *phase) const {
  // check that length is constant
  Node* length = in(ArrayCopyNode::Length);
  const Type* length_type = phase->type(length);

  if (length_type == Type::TOP) {
    return -1;
  }

  assert(is_clonebasic() || is_arraycopy() || is_copyof() || is_copyofrange(), "unexpected array copy type");

  return is_clonebasic() ? length->find_intptr_t_con(-1) : length->find_int_con(-1);
}

int ArrayCopyNode::get_count(PhaseGVN *phase) const {
  Node* src = in(ArrayCopyNode::Src);
  const Type* src_type = phase->type(src);

  if (is_clonebasic()) {
    if (src_type->isa_instptr()) {
      const TypeInstPtr* inst_src = src_type->is_instptr();
      ciInstanceKlass* ik = inst_src->klass()->as_instance_klass();
      // ciInstanceKlass::nof_nonstatic_fields() doesn't take injected
      // fields into account. They are rare anyway so easier to simply
      // skip instances with injected fields.
      if ((!inst_src->klass_is_exact() && (ik->is_interface() || ik->has_subklass())) || ik->has_injected_fields()) {
        return -1;
      }
      int nb_fields = ik->nof_nonstatic_fields();
      return nb_fields;
    } else {
      const TypeAryPtr* ary_src = src_type->isa_aryptr();
      assert (ary_src != NULL, "not an array or instance?");
      // clone passes a length as a rounded number of longs. If we're
      // cloning an array we'll do it element by element. If the
      // length input to ArrayCopyNode is constant, length of input
      // array must be too.

      assert((get_length_if_constant(phase) == -1) == !ary_src->size()->is_con() ||
             phase->is_IterGVN(), "inconsistent");

      if (ary_src->size()->is_con()) {
        return ary_src->size()->get_con();
      }
      return -1;
    }
  }

  return get_length_if_constant(phase);
}

Node* ArrayCopyNode::try_clone_instance(PhaseGVN *phase, bool can_reshape, int count) {
  if (!is_clonebasic()) {
    return NULL;
  }

  Node* src = in(ArrayCopyNode::Src);
  Node* dest = in(ArrayCopyNode::Dest);
  Node* ctl = in(TypeFunc::Control);
  Node* in_mem = in(TypeFunc::Memory);

  const Type* src_type = phase->type(src);

  assert(src->is_AddP(), "should be base + off");
  assert(dest->is_AddP(), "should be base + off");
  Node* base_src = src->in(AddPNode::Base);
  Node* base_dest = dest->in(AddPNode::Base);

  MergeMemNode* mem = MergeMemNode::make(in_mem);

  const TypeInstPtr* inst_src = src_type->isa_instptr();

  if (inst_src == NULL) {
    return NULL;
  }

  if (!inst_src->klass_is_exact()) {
    ciInstanceKlass* ik = inst_src->klass()->as_instance_klass();
    assert(!ik->is_interface() && !ik->has_subklass(), "inconsistent klass hierarchy");
    phase->C->dependencies()->assert_leaf_type(ik);
  }

  ciInstanceKlass* ik = inst_src->klass()->as_instance_klass();
  assert(ik->nof_nonstatic_fields() <= ArrayCopyLoadStoreMaxElem, "too many fields");

  for (int i = 0; i < count; i++) {
    ciField* field = ik->nonstatic_field_at(i);
    int fieldidx = phase->C->alias_type(field)->index();
    const TypePtr* adr_type = phase->C->alias_type(field)->adr_type();
    Node* off = phase->MakeConX(field->offset());
    Node* next_src = phase->transform(new AddPNode(base_src,base_src,off));
    Node* next_dest = phase->transform(new AddPNode(base_dest,base_dest,off));
    BasicType bt = field->layout_type();

    const Type *type;
    if (bt == T_OBJECT) {
      if (!field->type()->is_loaded()) {
        type = TypeInstPtr::BOTTOM;
      } else {
        ciType* field_klass = field->type();
        type = TypeOopPtr::make_from_klass(field_klass->as_klass());
      }
    } else {
      type = Type::get_const_basic_type(bt);
    }

    Node* v = LoadNode::make(*phase, ctl, mem->memory_at(fieldidx), next_src, adr_type, type, bt, MemNode::unordered);
    v = phase->transform(v);
    Node* s = StoreNode::make(*phase, ctl, mem->memory_at(fieldidx), next_dest, adr_type, v, bt, MemNode::unordered);
    s = phase->transform(s);
    mem->set_memory_at(fieldidx, s);
  }

  if (!finish_transform(phase, can_reshape, ctl, mem)) {
    return NULL;
  }

  return mem;
}

bool ArrayCopyNode::prepare_array_copy(PhaseGVN *phase, bool can_reshape,
                                       Node*& adr_src,
                                       Node*& base_src,
                                       Node*& adr_dest,
                                       Node*& base_dest,
                                       BasicType& copy_type,
                                       const Type*& value_type,
                                       bool& disjoint_bases) {
  Node* src = in(ArrayCopyNode::Src);
  Node* dest = in(ArrayCopyNode::Dest);
  const Type* src_type = phase->type(src);
  const TypeAryPtr* ary_src = src_type->isa_aryptr();

  if (is_arraycopy() || is_copyofrange() || is_copyof()) {
    const Type* dest_type = phase->type(dest);
    const TypeAryPtr* ary_dest = dest_type->isa_aryptr();
    Node* src_offset = in(ArrayCopyNode::SrcPos);
    Node* dest_offset = in(ArrayCopyNode::DestPos);

    // newly allocated object is guaranteed to not overlap with source object
    disjoint_bases = is_alloc_tightly_coupled();

    if (ary_src  == NULL || ary_src->klass()  == NULL ||
        ary_dest == NULL || ary_dest->klass() == NULL) {
      // We don't know if arguments are arrays
      return false;
    }

    BasicType src_elem  = ary_src->klass()->as_array_klass()->element_type()->basic_type();
    BasicType dest_elem = ary_dest->klass()->as_array_klass()->element_type()->basic_type();
    if (src_elem  == T_ARRAY)  src_elem  = T_OBJECT;
    if (dest_elem == T_ARRAY)  dest_elem = T_OBJECT;

    if (src_elem != dest_elem || dest_elem == T_VOID) {
      // We don't know if arguments are arrays of the same type
      return false;
    }

    if (dest_elem == T_OBJECT && (!is_alloc_tightly_coupled() || !GraphKit::use_ReduceInitialCardMarks())) {
      // It's an object array copy but we can't emit the card marking
      // that is needed
      return false;
    }

    value_type = ary_src->elem();

    base_src = src;
    base_dest = dest;

    uint shift  = exact_log2(type2aelembytes(dest_elem));
    uint header = arrayOopDesc::base_offset_in_bytes(dest_elem);

    adr_src = src;
    adr_dest = dest;

    src_offset = Compile::conv_I2X_index(phase, src_offset, ary_src->size());
    dest_offset = Compile::conv_I2X_index(phase, dest_offset, ary_dest->size());

    Node* src_scale = phase->transform(new LShiftXNode(src_offset, phase->intcon(shift)));
    Node* dest_scale = phase->transform(new LShiftXNode(dest_offset, phase->intcon(shift)));

    adr_src = phase->transform(new AddPNode(base_src, adr_src, src_scale));
    adr_dest = phase->transform(new AddPNode(base_dest, adr_dest, dest_scale));

    adr_src = new AddPNode(base_src, adr_src, phase->MakeConX(header));
    adr_dest = new AddPNode(base_dest, adr_dest, phase->MakeConX(header));

    adr_src = phase->transform(adr_src);
    adr_dest = phase->transform(adr_dest);

    copy_type = dest_elem;
  } else {
    assert (is_clonebasic(), "should be");

    disjoint_bases = true;
    assert(src->is_AddP(), "should be base + off");
    assert(dest->is_AddP(), "should be base + off");
    adr_src = src;
    base_src = src->in(AddPNode::Base);
    adr_dest = dest;
    base_dest = dest->in(AddPNode::Base);

    assert(phase->type(src->in(AddPNode::Offset))->is_intptr_t()->get_con() == phase->type(dest->in(AddPNode::Offset))->is_intptr_t()->get_con(), "same start offset?");
    BasicType elem = ary_src->klass()->as_array_klass()->element_type()->basic_type();
    if (elem == T_ARRAY)  elem = T_OBJECT;

    int diff = arrayOopDesc::base_offset_in_bytes(elem) - phase->type(src->in(AddPNode::Offset))->is_intptr_t()->get_con();
    assert(diff >= 0, "clone should not start after 1st array element");
    if (diff > 0) {
      adr_src = phase->transform(new AddPNode(base_src, adr_src, phase->MakeConX(diff)));
      adr_dest = phase->transform(new AddPNode(base_dest, adr_dest, phase->MakeConX(diff)));
    }

    copy_type = elem;
    value_type = ary_src->elem();
  }
  return true;
}

const TypePtr* ArrayCopyNode::get_address_type(PhaseGVN *phase, Node* n) {
  const Type* at = phase->type(n);
  assert(at != Type::TOP, "unexpected type");
  const TypePtr* atp = at->isa_ptr();
  // adjust atp to be the correct array element address type
  atp = atp->add_offset(Type::OffsetBot);
  return atp;
}

void ArrayCopyNode::array_copy_test_overlap(PhaseGVN *phase, bool can_reshape, bool disjoint_bases, int count, Node*& forward_ctl, Node*& backward_ctl) {
  Node* ctl = in(TypeFunc::Control);
  if (!disjoint_bases && count > 1) {
    Node* src_offset = in(ArrayCopyNode::SrcPos);
    Node* dest_offset = in(ArrayCopyNode::DestPos);
    assert(src_offset != NULL && dest_offset != NULL, "should be");
    Node* cmp = phase->transform(new CmpINode(src_offset, dest_offset));
    Node *bol = phase->transform(new BoolNode(cmp, BoolTest::lt));
    IfNode *iff = new IfNode(ctl, bol, PROB_FAIR, COUNT_UNKNOWN);

    phase->transform(iff);

    forward_ctl = phase->transform(new IfFalseNode(iff));
    backward_ctl = phase->transform(new IfTrueNode(iff));
  } else {
    forward_ctl = ctl;
  }
}

Node* ArrayCopyNode::array_copy_forward(PhaseGVN *phase,
                                        bool can_reshape,
                                        Node* forward_ctl,
                                        Node* start_mem_src,
                                        Node* start_mem_dest,
                                        const TypePtr* atp_src,
                                        const TypePtr* atp_dest,
                                        Node* adr_src,
                                        Node* base_src,
                                        Node* adr_dest,
                                        Node* base_dest,
                                        BasicType copy_type,
                                        const Type* value_type,
                                        int count) {
  Node* mem = phase->C->top();
  if (!forward_ctl->is_top()) {
    // copy forward
    mem = start_mem_dest;

    if (count > 0) {
      Node* v = LoadNode::make(*phase, forward_ctl, start_mem_src, adr_src, atp_src, value_type, copy_type, MemNode::unordered);
      v = phase->transform(v);
      mem = StoreNode::make(*phase, forward_ctl, mem, adr_dest, atp_dest, v, copy_type, MemNode::unordered);
      mem = phase->transform(mem);
      for (int i = 1; i < count; i++) {
        Node* off  = phase->MakeConX(type2aelembytes(copy_type) * i);
        Node* next_src = phase->transform(new AddPNode(base_src,adr_src,off));
        Node* next_dest = phase->transform(new AddPNode(base_dest,adr_dest,off));
        v = LoadNode::make(*phase, forward_ctl, mem, next_src, atp_src, value_type, copy_type, MemNode::unordered);
        v = phase->transform(v);
        mem = StoreNode::make(*phase, forward_ctl,mem,next_dest,atp_dest,v, copy_type, MemNode::unordered);
        mem = phase->transform(mem);
      }
    } else if(can_reshape) {
      PhaseIterGVN* igvn = phase->is_IterGVN();
      igvn->_worklist.push(adr_src);
      igvn->_worklist.push(adr_dest);
    }
  }
  return mem;
}

Node* ArrayCopyNode::array_copy_backward(PhaseGVN *phase,
                                         bool can_reshape,
                                         Node* backward_ctl,
                                         Node* start_mem_src,
                                         Node* start_mem_dest,
                                         const TypePtr* atp_src,
                                         const TypePtr* atp_dest,
                                         Node* adr_src,
                                         Node* base_src,
                                         Node* adr_dest,
                                         Node* base_dest,
                                         BasicType copy_type,
                                         const Type* value_type,
                                         int count) {
  Node* mem = phase->C->top();
  if (!backward_ctl->is_top()) {
    // copy backward
    mem = start_mem_dest;

    if (count > 0) {
      for (int i = count-1; i >= 1; i--) {
        Node* off  = phase->MakeConX(type2aelembytes(copy_type) * i);
        Node* next_src = phase->transform(new AddPNode(base_src,adr_src,off));
        Node* next_dest = phase->transform(new AddPNode(base_dest,adr_dest,off));
        Node* v = LoadNode::make(*phase, backward_ctl, mem, next_src, atp_src, value_type, copy_type, MemNode::unordered);
        v = phase->transform(v);
        mem = StoreNode::make(*phase, backward_ctl,mem,next_dest,atp_dest,v, copy_type, MemNode::unordered);
        mem = phase->transform(mem);
      }
      Node* v = LoadNode::make(*phase, backward_ctl, mem, adr_src, atp_src, value_type, copy_type, MemNode::unordered);
      v = phase->transform(v);
      mem = StoreNode::make(*phase, backward_ctl, mem, adr_dest, atp_dest, v, copy_type, MemNode::unordered);
      mem = phase->transform(mem);
    } else if(can_reshape) {
      PhaseIterGVN* igvn = phase->is_IterGVN();
      igvn->_worklist.push(adr_src);
      igvn->_worklist.push(adr_dest);
    }
  }
  return mem;
}

bool ArrayCopyNode::finish_transform(PhaseGVN *phase, bool can_reshape,
                                     Node* ctl, Node *mem) {
  if (can_reshape) {
    PhaseIterGVN* igvn = phase->is_IterGVN();
    igvn->set_delay_transform(false);
    if (is_clonebasic()) {
      Node* out_mem = proj_out(TypeFunc::Memory);

      if (out_mem->outcnt() != 1 || !out_mem->raw_out(0)->is_MergeMem() ||
          out_mem->raw_out(0)->outcnt() != 1 || !out_mem->raw_out(0)->raw_out(0)->is_MemBar()) {
        assert(!GraphKit::use_ReduceInitialCardMarks(), "can only happen with card marking");
        return false;
      }

      igvn->replace_node(out_mem->raw_out(0), mem);

      Node* out_ctl = proj_out(TypeFunc::Control);
      igvn->replace_node(out_ctl, ctl);
    } else {
      // replace fallthrough projections of the ArrayCopyNode by the
      // new memory, control and the input IO.
      CallProjections callprojs;
      extract_projections(&callprojs, true, false);

      if (callprojs.fallthrough_ioproj != NULL) {
        igvn->replace_node(callprojs.fallthrough_ioproj, in(TypeFunc::I_O));
      }
      if (callprojs.fallthrough_memproj != NULL) {
        igvn->replace_node(callprojs.fallthrough_memproj, mem);
      }
      if (callprojs.fallthrough_catchproj != NULL) {
        igvn->replace_node(callprojs.fallthrough_catchproj, ctl);
      }

      // The ArrayCopyNode is not disconnected. It still has the
      // projections for the exception case. Replace current
      // ArrayCopyNode with a dummy new one with a top() control so
      // that this part of the graph stays consistent but is
      // eventually removed.

      set_req(0, phase->C->top());
      remove_dead_region(phase, can_reshape);
    }
  } else {
    if (in(TypeFunc::Control) != ctl) {
      // we can't return new memory and control from Ideal at parse time
      assert(!is_clonebasic(), "added control for clone?");
      return false;
    }
  }
  return true;
}


Node *ArrayCopyNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (remove_dead_region(phase, can_reshape))  return this;

  if (StressArrayCopyMacroNode && !can_reshape) {
    phase->record_for_igvn(this);
    return NULL;
  }

  // See if it's a small array copy and we can inline it as
  // loads/stores
  // Here we can only do:
  // - arraycopy if all arguments were validated before and we don't
  // need card marking
  // - clone for which we don't need to do card marking

  if (!is_clonebasic() && !is_arraycopy_validated() &&
      !is_copyofrange_validated() && !is_copyof_validated()) {
    return NULL;
  }

  assert(in(TypeFunc::Control) != NULL &&
         in(TypeFunc::Memory) != NULL &&
         in(ArrayCopyNode::Src) != NULL &&
         in(ArrayCopyNode::Dest) != NULL &&
         in(ArrayCopyNode::Length) != NULL &&
         ((in(ArrayCopyNode::SrcPos) != NULL && in(ArrayCopyNode::DestPos) != NULL) ||
          is_clonebasic()), "broken inputs");

  if (in(TypeFunc::Control)->is_top() ||
      in(TypeFunc::Memory)->is_top() ||
      phase->type(in(ArrayCopyNode::Src)) == Type::TOP ||
      phase->type(in(ArrayCopyNode::Dest)) == Type::TOP ||
      (in(ArrayCopyNode::SrcPos) != NULL && in(ArrayCopyNode::SrcPos)->is_top()) ||
      (in(ArrayCopyNode::DestPos) != NULL && in(ArrayCopyNode::DestPos)->is_top())) {
    return NULL;
  }

  int count = get_count(phase);

  if (count < 0 || count > ArrayCopyLoadStoreMaxElem) {
    return NULL;
  }

  Node* mem = try_clone_instance(phase, can_reshape, count);
  if (mem != NULL) {
    return mem;
  }

  Node* adr_src = NULL;
  Node* base_src = NULL;
  Node* adr_dest = NULL;
  Node* base_dest = NULL;
  BasicType copy_type = T_ILLEGAL;
  const Type* value_type = NULL;
  bool disjoint_bases = false;

  if (!prepare_array_copy(phase, can_reshape,
                          adr_src, base_src, adr_dest, base_dest,
                          copy_type, value_type, disjoint_bases)) {
    return NULL;
  }

  Node* src = in(ArrayCopyNode::Src);
  Node* dest = in(ArrayCopyNode::Dest);
  const TypePtr* atp_src = get_address_type(phase, src);
  const TypePtr* atp_dest = get_address_type(phase, dest);
  uint alias_idx_src = phase->C->get_alias_index(atp_src);
  uint alias_idx_dest = phase->C->get_alias_index(atp_dest);

  Node *in_mem = in(TypeFunc::Memory);
  Node *start_mem_src = in_mem;
  Node *start_mem_dest = in_mem;
  if (in_mem->is_MergeMem()) {
    start_mem_src = in_mem->as_MergeMem()->memory_at(alias_idx_src);
    start_mem_dest = in_mem->as_MergeMem()->memory_at(alias_idx_dest);
  }


  if (can_reshape) {
    assert(!phase->is_IterGVN()->delay_transform(), "cannot delay transforms");
    phase->is_IterGVN()->set_delay_transform(true);
  }

  Node* backward_ctl = phase->C->top();
  Node* forward_ctl = phase->C->top();
  array_copy_test_overlap(phase, can_reshape, disjoint_bases, count, forward_ctl, backward_ctl);

  Node* forward_mem = array_copy_forward(phase, can_reshape, forward_ctl,
                                         start_mem_src, start_mem_dest,
                                         atp_src, atp_dest,
                                         adr_src, base_src, adr_dest, base_dest,
                                         copy_type, value_type, count);

  Node* backward_mem = array_copy_backward(phase, can_reshape, backward_ctl,
                                           start_mem_src, start_mem_dest,
                                           atp_src, atp_dest,
                                           adr_src, base_src, adr_dest, base_dest,
                                           copy_type, value_type, count);

  Node* ctl = NULL;
  if (!forward_ctl->is_top() && !backward_ctl->is_top()) {
    ctl = new RegionNode(3);
    mem = new PhiNode(ctl, Type::MEMORY, atp_dest);
    ctl->init_req(1, forward_ctl);
    mem->init_req(1, forward_mem);
    ctl->init_req(2, backward_ctl);
    mem->init_req(2, backward_mem);
    ctl = phase->transform(ctl);
    mem = phase->transform(mem);
  } else if (!forward_ctl->is_top()) {
    ctl = forward_ctl;
    mem = forward_mem;
  } else {
    assert(!backward_ctl->is_top(), "no copy?");
    ctl = backward_ctl;
    mem = backward_mem;
  }

  if (can_reshape) {
    assert(phase->is_IterGVN()->delay_transform(), "should be delaying transforms");
    phase->is_IterGVN()->set_delay_transform(false);
  }

  MergeMemNode* out_mem = MergeMemNode::make(in_mem);
  out_mem->set_memory_at(alias_idx_dest, mem);
  mem = out_mem;

  if (!finish_transform(phase, can_reshape, ctl, mem)) {
    return NULL;
  }

  return mem;
}

bool ArrayCopyNode::may_modify(const TypeOopPtr *t_oop, PhaseTransform *phase) {
  Node* dest = in(ArrayCopyNode::Dest);
  if (dest->is_top()) {
    return false;
  }
  const TypeOopPtr* dest_t = phase->type(dest)->is_oopptr();
  assert(!dest_t->is_known_instance() || _dest_type->is_known_instance(), "result of EA not recorded");
  assert(in(ArrayCopyNode::Src)->is_top() || !phase->type(in(ArrayCopyNode::Src))->is_oopptr()->is_known_instance() ||
         _src_type->is_known_instance(), "result of EA not recorded");

  if (_dest_type != TypeOopPtr::BOTTOM || t_oop->is_known_instance()) {
    assert(_dest_type == TypeOopPtr::BOTTOM || _dest_type->is_known_instance(), "result of EA is known instance");
    return t_oop->instance_id() == _dest_type->instance_id();
  }

  return CallNode::may_modify_arraycopy_helper(dest_t, t_oop, phase);
}

bool ArrayCopyNode::may_modify_helper(const TypeOopPtr *t_oop, Node* n, PhaseTransform *phase) {
  if (n->is_Proj()) {
    n = n->in(0);
    if (n->is_Call() && n->as_Call()->may_modify(t_oop, phase)) {
      return true;
    }
  }
  return false;
}

bool ArrayCopyNode::may_modify(const TypeOopPtr *t_oop, MemBarNode* mb, PhaseTransform *phase) {
  Node* mem = mb->in(TypeFunc::Memory);

  if (mem->is_MergeMem()) {
    Node* n = mem->as_MergeMem()->memory_at(Compile::AliasIdxRaw);
    if (may_modify_helper(t_oop, n, phase)) {
      return true;
    } else if (n->is_Phi()) {
      for (uint i = 1; i < n->req(); i++) {
        if (n->in(i) != NULL) {
          if (may_modify_helper(t_oop, n->in(i), phase)) {
            return true;
          }
        }
      }
    }
  }

  return false;
}

// Does this array copy modify offsets between offset_lo and offset_hi
// in the destination array
// if must_modify is false, return true if the copy could write
// between offset_lo and offset_hi
// if must_modify is true, return true if the copy is guaranteed to
// write between offset_lo and offset_hi
bool ArrayCopyNode::modifies(intptr_t offset_lo, intptr_t offset_hi, PhaseTransform* phase, bool must_modify) {
  assert(_kind == ArrayCopy || _kind == CopyOf || _kind == CopyOfRange, "only for real array copies");

  Node* dest = in(ArrayCopyNode::Dest);
  Node* src_pos = in(ArrayCopyNode::SrcPos);
  Node* dest_pos = in(ArrayCopyNode::DestPos);
  Node* len = in(ArrayCopyNode::Length);

  const TypeInt *dest_pos_t = phase->type(dest_pos)->isa_int();
  const TypeInt *len_t = phase->type(len)->isa_int();
  const TypeAryPtr* ary_t = phase->type(dest)->isa_aryptr();

  if (dest_pos_t != NULL && len_t != NULL && ary_t != NULL) {
    BasicType ary_elem = ary_t->klass()->as_array_klass()->element_type()->basic_type();
    uint header = arrayOopDesc::base_offset_in_bytes(ary_elem);
    uint elemsize = type2aelembytes(ary_elem);

    intptr_t dest_pos_plus_len_lo = (((intptr_t)dest_pos_t->_lo) + len_t->_lo) * elemsize + header;
    intptr_t dest_pos_plus_len_hi = (((intptr_t)dest_pos_t->_hi) + len_t->_hi) * elemsize + header;
    intptr_t dest_pos_lo = ((intptr_t)dest_pos_t->_lo) * elemsize + header;
    intptr_t dest_pos_hi = ((intptr_t)dest_pos_t->_hi) * elemsize + header;

    if (must_modify) {
      if (offset_lo >= dest_pos_hi && offset_hi < dest_pos_plus_len_lo) {
        return true;
      }
    } else {
      if (offset_hi >= dest_pos_lo && offset_lo < dest_pos_plus_len_hi) {
        return true;
      }
    }
  }
  return false;
}

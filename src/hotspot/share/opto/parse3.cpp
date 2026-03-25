/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciInstanceKlass.hpp"
#include "compiler/compileLog.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/universe.hpp"
#include "oops/accessDecorators.hpp"
#include "oops/flatArrayKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/inlinetypenode.hpp"
#include "opto/memnode.hpp"
#include "opto/parse.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/handles.inline.hpp"

//=============================================================================
// Helper methods for _get* and _put* bytecodes
//=============================================================================

void Parse::do_field_access(bool is_get, bool is_field) {
  bool will_link;
  ciField* field = iter().get_field(will_link);
  assert(will_link, "getfield: typeflow responsibility");

  if (is_field == field->is_static()) {
    // Interpreter will throw java_lang_IncompatibleClassChangeError
    // Check this before allowing <clinit> methods to access static fields
    uncommon_trap(Deoptimization::Reason_unhandled,
                  Deoptimization::Action_none);
    return;
  }

  // Deoptimize on putfield writes to call site target field outside of CallSite ctor.
  ciInstanceKlass* field_holder = field->holder();
  if (!is_get && field->is_call_site_target() &&
      !(method()->holder() == field_holder && method()->is_object_constructor())) {
    uncommon_trap(Deoptimization::Reason_unhandled,
                  Deoptimization::Action_reinterpret,
                  nullptr, "put to call site target field");
    return;
  }

  if (C->needs_clinit_barrier(field, method())) {
    clinit_barrier(field_holder, method());
    if (stopped())  return;
  }

  assert(field->will_link(method(), bc()), "getfield: typeflow responsibility");

  // Note:  We do not check for an unloaded field type here any more.

  // Generate code for the object pointer.
  Node* obj;
  if (is_field) {
    int obj_depth = is_get ? 0 : field->type()->size();
    obj = null_check(peek(obj_depth));
    // Compile-time detect of null-exception?
    if (stopped())  return;

#ifdef ASSERT
    const TypeInstPtr *tjp = TypeInstPtr::make(TypePtr::NotNull, iter().get_declared_field_holder());
    assert(_gvn.type(obj)->higher_equal(tjp), "cast_up is no longer needed");
#endif

    if (is_get) {
      do_get_xxx(obj, field);
    } else {
      do_put_xxx(obj, field, is_field);
      if (stopped()) {
        return;
      }
      (void) pop();  // pop receiver after putting
    }
  } else {
    const TypeInstPtr* tip = TypeInstPtr::make(field_holder->java_mirror());
    obj = _gvn.makecon(tip);
    if (is_get) {
      do_get_xxx(obj, field);
    } else {
      do_put_xxx(obj, field, is_field);
    }
  }
}

void Parse::do_get_xxx(Node* obj, ciField* field) {
  BasicType bt = field->layout_type();
  // Does this field have a constant value?  If so, just push the value.
  if (field->is_constant() && !field->is_flat() &&
      // Keep consistent with types found by ciTypeFlow: for an
      // unloaded field type, ciTypeFlow::StateVector::do_getstatic()
      // speculates the field is null. The code in the rest of this
      // method does the same. We must not bypass it and use a non
      // null constant here.
      (bt != T_OBJECT || field->type()->is_loaded())) {
    // final or stable field
    Node* con = make_constant_from_field(field, obj);
    if (con != nullptr) {
      if (!field->is_static()) {
        pop();
      }
      push_node(field->layout_type(), con);
      return;
    }
  }

  if (obj->is_InlineType()) {
    assert(!field->is_static(), "must not be a static field");
    InlineTypeNode* vt = obj->as_InlineType();
    Node* value = vt->field_value_by_offset(field->offset_in_bytes(), false);
    const Type* value_type = _gvn.type(value);
    if (value->is_InlineType()) {
      value = value->as_InlineType()->adjust_scalarization_depth(this);
    } else if (value_type->is_inlinetypeptr()) {
      value = InlineTypeNode::make_from_oop(this, value, value_type->inline_klass());
    }
    pop();
    push_node(field->layout_type(), value);
    return;
  }

  ciType* field_klass = field->type();
  field_klass = improve_abstract_inline_type_klass(field_klass);
  int offset = field->offset_in_bytes();
  bool must_assert_null = false;
  Node* adr = basic_plus_adr(obj, obj, offset);
  assert(C->get_alias_index(C->alias_type(field)->adr_type()) == C->get_alias_index(_gvn.type(adr)->isa_ptr()),
         "slice of address and input slice don't match");

  Node* ld = nullptr;
  if (field->is_null_free() && field_klass->as_inline_klass()->is_empty()) {
    // Loading from a field of an empty inline type. Just return the default instance.
    ld = InlineTypeNode::make_all_zero(_gvn, field_klass->as_inline_klass());
  } else if (field->is_flat()) {
    // Loading from a flat inline type field.
    ciInlineKlass* vk = field->type()->as_inline_klass();
    bool is_immutable = field->is_final() && field->is_strict();
    bool atomic = field->is_atomic();
    ld = InlineTypeNode::make_from_flat(this, field_klass->as_inline_klass(), obj, adr, atomic, is_immutable, field->is_null_free(), IN_HEAP | MO_UNORDERED);
  } else {
    // Build the resultant type of the load
    const Type* type;
    if (is_reference_type(bt)) {
      if (!field_klass->is_loaded()) {
        type = TypeInstPtr::BOTTOM;
        must_assert_null = true;
      } else if (field->is_static_constant()) {
        // This can happen if the constant oop is non-perm.
        ciObject* con = field->constant_value().as_object();
        // Do not "join" in the previous type; it doesn't add value,
        // and may yield a vacuous result if the field is of interface type.
        if (con->is_null_object()) {
          type = TypePtr::NULL_PTR;
        } else {
          type = TypeOopPtr::make_from_constant(con)->isa_oopptr();
        }
        assert(type != nullptr, "field singleton type must be consistent");
      } else {
        type = TypeOopPtr::make_from_klass(field_klass->as_klass());
        if (field->is_null_free()) {
          type = type->join_speculative(TypePtr::NOTNULL);
        }
      }
    } else {
      type = Type::get_const_basic_type(bt);
    }

    const TypePtr* adr_type = C->alias_type(field)->adr_type();
    DecoratorSet decorators = IN_HEAP;
    decorators |= field->is_volatile() ? MO_SEQ_CST : MO_UNORDERED;
    ld = access_load_at(obj, adr, adr_type, type, bt, decorators);
    if (field_klass->is_inlinetype()) {
      // Load a non-flattened inline type from memory
      ld = InlineTypeNode::make_from_oop(this, ld, field_klass->as_inline_klass());
    }
  }

  // Adjust Java stack
  if (!field->is_static()) {
    pop();
  }
  if (type2size[bt] == 1) {
    push(ld);
  } else {
    push_pair(ld);
  }

  if (must_assert_null) {
    // Do not take a trap here.  It's possible that the program
    // will never load the field's class, and will happily see
    // null values in this field forever.  Don't stumble into a
    // trap for such a program, or we might get a long series
    // of useless recompilations.  (Or, we might load a class
    // which should not be loaded.)  If we ever see a non-null
    // value, we will then trap and recompile.  (The trap will
    // not need to mention the class index, since the class will
    // already have been loaded if we ever see a non-null value.)
    // uncommon_trap(iter().get_field_signature_index());
    if (PrintOpto && (Verbose || WizardMode)) {
      method()->print_name(); tty->print_cr(" asserting nullness of field at bci: %d", bci());
    }
    if (C->log() != nullptr) {
      C->log()->elem("assert_null reason='field' klass='%d'",
                     C->log()->identify(field_klass));
    }
    // If there is going to be a trap, put it at the next bytecode:
    set_bci(iter().next_bci());
    null_assert(peek());
    set_bci(iter().cur_bci()); // put it back
  }
}

// If the field klass is an abstract value klass (for which we do not know the layout, yet), it could have a unique
// concrete sub klass for which we have a fixed layout. This allows us to use InlineTypeNodes instead.
ciType* Parse::improve_abstract_inline_type_klass(ciType* field_klass) {
  Dependencies* dependencies = C->dependencies();
  if (UseUniqueSubclasses && dependencies != nullptr && field_klass->is_instance_klass()) {
    ciInstanceKlass* instance_klass = field_klass->as_instance_klass();
    if (instance_klass->is_loaded() && instance_klass->is_abstract_value_klass()) {
      ciInstanceKlass* sub_klass = instance_klass->unique_concrete_subklass();
      if (sub_klass != nullptr && sub_klass != field_klass) {
        field_klass = sub_klass;
        dependencies->assert_abstract_with_unique_concrete_subtype(instance_klass, sub_klass);
      }
    }
  }
  return field_klass;
}

void Parse::do_put_xxx(Node* obj, ciField* field, bool is_field) {
  bool is_vol = field->is_volatile();
  int offset = field->offset_in_bytes();

  BasicType bt = field->layout_type();
  Node* val = type2size[bt] == 1 ? pop() : pop_pair();
  if (field->is_null_free()) {
    PreserveReexecuteState preexecs(this);
    jvms()->set_should_reexecute(true);
    inc_sp(1);
    val = null_check(val);
    if (stopped()) {
      return;
    }
  }

  Node* adr = basic_plus_adr(obj, obj, offset);

  // We cannot store into a non-larval object, so obj must not be an InlineTypeNode
  assert(!obj->is_InlineType(), "InlineTypeNodes are non-larval value objects");
  if (field->is_null_free() && field->type()->as_inline_klass()->is_empty() && (!method()->is_object_constructor() || field->is_flat())) {
    // Storing to a field of an empty, null-free inline type that is already initialized. Ignore.
    return;
  } else if (field->is_flat()) {
    // Storing to a flat inline type field.
    ciInlineKlass* vk = field->type()->as_inline_klass();
    if (!val->is_InlineType()) {
      assert(gvn().type(val) == TypePtr::NULL_PTR, "Unexpected value");
      val = InlineTypeNode::make_null(gvn(), vk);
    }
    inc_sp(1);
    bool is_immutable = field->is_final() && field->is_strict();
    bool atomic = field->is_atomic();
    val->as_InlineType()->store_flat(this, obj, adr, atomic, is_immutable, field->is_null_free(), IN_HEAP | MO_UNORDERED);
    dec_sp(1);
  } else {
    // Store the value.
    const Type* field_type;
    if (!field->type()->is_loaded()) {
      field_type = TypeInstPtr::BOTTOM;
    } else {
      if (is_reference_type(bt)) {
        field_type = TypeOopPtr::make_from_klass(field->type()->as_klass());
      } else {
        field_type = Type::BOTTOM;
      }
    }

    const TypePtr* adr_type = C->alias_type(field)->adr_type();
    assert(C->get_alias_index(adr_type) == C->get_alias_index(_gvn.type(adr)->isa_ptr()),
           "slice of address and input slice don't match");
    DecoratorSet decorators = IN_HEAP;
    decorators |= is_vol ? MO_SEQ_CST : MO_UNORDERED;
    inc_sp(1);
    access_store_at(obj, adr, adr_type, val, field_type, bt, decorators);
    dec_sp(1);
  }

  if (is_field) {
    // Remember we wrote a volatile field.
    // For not multiple copy atomic cpu (ppc64) a barrier should be issued
    // in constructors which have such stores. See do_exits() in parse1.cpp.
    if (is_vol) {
      set_wrote_volatile(true);
    }
    set_wrote_fields(true);

    // If the field is final, the rules of Java say we are in <init> or <clinit>.
    // If the field is @Stable, we can be in any method, but we only care about
    // constructors at this point.
    //
    // Note the presence of writes to final/@Stable non-static fields, so that we
    // can insert a memory barrier later on to keep the writes from floating
    // out of the constructor.
    if (field->is_final() || field->is_stable()) {
      if (field->is_final()) {
        set_wrote_final(true);
      }
      if (field->is_stable()) {
        set_wrote_stable(true);
      }
      if (AllocateNode::Ideal_allocation(obj) != nullptr) {
        // Preserve allocation ptr to create precedent edge to it in membar
        // generated on exit from constructor.
        set_alloc_with_final_or_stable(obj);
      }
    }
  }
}

//=============================================================================

void Parse::do_newarray() {
  bool will_link;
  ciKlass* klass = iter().get_klass(will_link);

  // Uncommon Trap when class that array contains is not loaded
  // we need the loaded class for the rest of graph; do not
  // initialize the container class (see Java spec)!!!
  assert(will_link, "newarray: typeflow responsibility");

  ciArrayKlass* array_klass = ciArrayKlass::make(klass);

  // Check that array_klass object is loaded
  if (!array_klass->is_loaded()) {
    // Generate uncommon_trap for unloaded array_class
    uncommon_trap(Deoptimization::Reason_unloaded,
                  Deoptimization::Action_reinterpret,
                  array_klass);
    return;
  } else if (array_klass->element_klass() != nullptr &&
             array_klass->element_klass()->is_inlinetype() &&
             !array_klass->element_klass()->as_inline_klass()->is_initialized()) {
    uncommon_trap(Deoptimization::Reason_uninitialized,
                  Deoptimization::Action_reinterpret,
                  nullptr);
    return;
  }

  kill_dead_locals();

  const TypeAryKlassPtr* array_klass_type = TypeAryKlassPtr::make(array_klass, Type::trust_interfaces);
  array_klass_type = array_klass_type->cast_to_refined_array_klass_ptr();
  Node* count_val = pop();
  Node* obj = new_array(makecon(array_klass_type), count_val, 1);
  push(obj);
}


void Parse::do_newarray(BasicType elem_type) {
  kill_dead_locals();

  Node*   count_val = pop();
  const TypeKlassPtr* array_klass = TypeKlassPtr::make(ciTypeArrayKlass::make(elem_type));
  Node*   obj = new_array(makecon(array_klass), count_val, 1);
  // Push resultant oop onto stack
  push(obj);
}

// Expand simple expressions like new int[3][5] and new Object[2][nonConLen].
// Also handle the degenerate 1-dimensional case of anewarray.
Node* Parse::expand_multianewarray(ciArrayKlass* array_klass, Node* *lengths, int ndimensions, int nargs) {
  Node* length = lengths[0];
  assert(length != nullptr, "");
  const TypeAryKlassPtr* array_klass_type = TypeAryKlassPtr::make(array_klass, Type::trust_interfaces);
  array_klass_type = array_klass_type->cast_to_refined_array_klass_ptr();
  Node* array = new_array(makecon(array_klass_type), length, nargs);
  if (ndimensions > 1) {
    jint length_con = find_int_con(length, -1);
    guarantee(length_con >= 0, "non-constant multianewarray");
    ciArrayKlass* array_klass_1 = array_klass->as_obj_array_klass()->element_klass()->as_array_klass();
    const TypePtr* adr_type = TypeAryPtr::OOPS;
    const TypeOopPtr*    elemtype = _gvn.type(array)->is_aryptr()->elem()->make_oopptr();
    const intptr_t header   = arrayOopDesc::base_offset_in_bytes(T_OBJECT);
    for (jint i = 0; i < length_con; i++) {
      Node*    elem   = expand_multianewarray(array_klass_1, &lengths[1], ndimensions-1, nargs);
      intptr_t offset = header + ((intptr_t)i << LogBytesPerHeapOop);
      Node*    eaddr  = basic_plus_adr(array, offset);
      access_store_at(array, eaddr, adr_type, elem, elemtype, T_OBJECT, IN_HEAP | IS_ARRAY);
    }
  }
  return array;
}

void Parse::do_multianewarray() {
  int ndimensions = iter().get_dimensions();

  // the m-dimensional array
  bool will_link;
  ciArrayKlass* array_klass = iter().get_klass(will_link)->as_array_klass();
  assert(will_link, "multianewarray: typeflow responsibility");

  // Note:  Array classes are always initialized; no is_initialized check.

  kill_dead_locals();

  // get the lengths from the stack (first dimension is on top)
  Node** length = NEW_RESOURCE_ARRAY(Node*, ndimensions + 1);
  length[ndimensions] = nullptr;  // terminating null for make_runtime_call
  int j;
  ciKlass* elem_klass = array_klass;
  for (j = ndimensions-1; j >= 0; j--) {
    length[j] = pop();
    elem_klass = elem_klass->as_array_klass()->element_klass();
  }
  if (elem_klass != nullptr && elem_klass->is_inlinetype() && !elem_klass->as_inline_klass()->is_initialized()) {
    inc_sp(ndimensions);
    uncommon_trap(Deoptimization::Reason_uninitialized,
                  Deoptimization::Action_reinterpret,
                  nullptr);
    return;
  }

  // The original expression was of this form: new T[length0][length1]...
  // It is often the case that the lengths are small (except the last).
  // If that happens, use the fast 1-d creator a constant number of times.
  const int expand_limit = MIN2((int)MultiArrayExpandLimit, 100);
  int64_t expand_count = 1;        // count of allocations in the expansion
  int64_t expand_fanout = 1;       // running total fanout
  for (j = 0; j < ndimensions-1; j++) {
    int dim_con = find_int_con(length[j], -1);
    // To prevent overflow, we use 64-bit values.  Alternatively,
    // we could clamp dim_con like so:
    // dim_con = MIN2(dim_con, expand_limit);
    expand_fanout *= dim_con;
    expand_count  += expand_fanout; // count the level-J sub-arrays
    if (dim_con <= 0
        || dim_con > expand_limit
        || expand_count > expand_limit) {
      expand_count = 0;
      break;
    }
  }

  // Can use multianewarray instead of [a]newarray if only one dimension,
  // or if all non-final dimensions are small constants.
  if (ndimensions == 1 || (1 <= expand_count && expand_count <= expand_limit)) {
    Node* obj = nullptr;
    // Set the original stack and the reexecute bit for the interpreter
    // to reexecute the multianewarray bytecode if deoptimization happens.
    // Do it unconditionally even for one dimension multianewarray.
    // Note: the reexecute bit will be set in GraphKit::add_safepoint_edges()
    // when AllocateArray node for newarray is created.
    { PreserveReexecuteState preexecs(this);
      inc_sp(ndimensions);
      // Pass 0 as nargs since uncommon trap code does not need to restore stack.
      obj = expand_multianewarray(array_klass, &length[0], ndimensions, 0);
    } //original reexecute and sp are set back here
    push(obj);
    return;
  }

  address fun = nullptr;
  switch (ndimensions) {
  case 1: ShouldNotReachHere(); break;
  case 2: fun = OptoRuntime::multianewarray2_Java(); break;
  case 3: fun = OptoRuntime::multianewarray3_Java(); break;
  case 4: fun = OptoRuntime::multianewarray4_Java(); break;
  case 5: fun = OptoRuntime::multianewarray5_Java(); break;
  };
  Node* c = nullptr;

  if (fun != nullptr) {
    c = make_runtime_call(RC_NO_LEAF | RC_NO_IO,
                          OptoRuntime::multianewarray_Type(ndimensions),
                          fun, nullptr, TypeRawPtr::BOTTOM,
                          makecon(TypeKlassPtr::make(array_klass, Type::trust_interfaces)),
                          length[0], length[1], length[2],
                          (ndimensions > 2) ? length[3] : nullptr,
                          (ndimensions > 3) ? length[4] : nullptr);
  } else {
    // Create a java array for dimension sizes
    Node* dims = nullptr;
    { PreserveReexecuteState preexecs(this);
      inc_sp(ndimensions);
      Node* dims_array_klass = makecon(TypeKlassPtr::make(ciArrayKlass::make(ciType::make(T_INT))));
      dims = new_array(dims_array_klass, intcon(ndimensions), 0);

      // Fill-in it with values
      for (j = 0; j < ndimensions; j++) {
        Node *dims_elem = array_element_address(dims, intcon(j), T_INT);
        store_to_memory(control(), dims_elem, length[j], T_INT, MemNode::unordered);
      }
    }

    c = make_runtime_call(RC_NO_LEAF | RC_NO_IO,
                          OptoRuntime::multianewarrayN_Type(),
                          OptoRuntime::multianewarrayN_Java(), nullptr, TypeRawPtr::BOTTOM,
                          makecon(TypeKlassPtr::make(array_klass, Type::trust_interfaces)),
                          dims);
  }
  make_slow_call_ex(c, env()->Throwable_klass(), false);

  Node* res = _gvn.transform(new ProjNode(c, TypeFunc::Parms));

  const Type* type = TypeOopPtr::make_from_klass_raw(array_klass, Type::trust_interfaces);

  // Improve the type:  We know it's not null, exact, and of a given length.
  type = type->is_ptr()->cast_to_ptr_type(TypePtr::NotNull);
  type = type->is_aryptr()->cast_to_exactness(true);

  const TypeInt* ltype = _gvn.find_int_type(length[0]);
  if (ltype != nullptr)
    type = type->is_aryptr()->cast_to_size(ltype);

    // We cannot sharpen the nested sub-arrays, since the top level is mutable.

  Node* cast = _gvn.transform( new CheckCastPPNode(control(), res, type) );
  push(cast);

  // Possible improvements:
  // - Make a fast path for small multi-arrays.  (W/ implicit init. loops.)
  // - Issue CastII against length[*] values, to TypeInt::POS.
}

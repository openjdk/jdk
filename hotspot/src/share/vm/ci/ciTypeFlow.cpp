/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_ciTypeFlow.cpp.incl"

// ciTypeFlow::JsrSet
//
// A JsrSet represents some set of JsrRecords.  This class
// is used to record a set of all jsr routines which we permit
// execution to return (ret) from.
//
// During abstract interpretation, JsrSets are used to determine
// whether two paths which reach a given block are unique, and
// should be cloned apart, or are compatible, and should merge
// together.

// ------------------------------------------------------------------
// ciTypeFlow::JsrSet::JsrSet
ciTypeFlow::JsrSet::JsrSet(Arena* arena, int default_len) {
  if (arena != NULL) {
    // Allocate growable array in Arena.
    _set = new (arena) GrowableArray<JsrRecord*>(arena, default_len, 0, NULL);
  } else {
    // Allocate growable array in current ResourceArea.
    _set = new GrowableArray<JsrRecord*>(4, 0, NULL, false);
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::JsrSet::copy_into
void ciTypeFlow::JsrSet::copy_into(JsrSet* jsrs) {
  int len = size();
  jsrs->_set->clear();
  for (int i = 0; i < len; i++) {
    jsrs->_set->append(_set->at(i));
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::JsrSet::is_compatible_with
//
// !!!! MISGIVINGS ABOUT THIS... disregard
//
// Is this JsrSet compatible with some other JsrSet?
//
// In set-theoretic terms, a JsrSet can be viewed as a partial function
// from entry addresses to return addresses.  Two JsrSets A and B are
// compatible iff
//
//   For any x,
//   A(x) defined and B(x) defined implies A(x) == B(x)
//
// Less formally, two JsrSets are compatible when they have identical
// return addresses for any entry addresses they share in common.
bool ciTypeFlow::JsrSet::is_compatible_with(JsrSet* other) {
  // Walk through both sets in parallel.  If the same entry address
  // appears in both sets, then the return address must match for
  // the sets to be compatible.
  int size1 = size();
  int size2 = other->size();

  // Special case.  If nothing is on the jsr stack, then there can
  // be no ret.
  if (size2 == 0) {
    return true;
  } else if (size1 != size2) {
    return false;
  } else {
    for (int i = 0; i < size1; i++) {
      JsrRecord* record1 = record_at(i);
      JsrRecord* record2 = other->record_at(i);
      if (record1->entry_address() != record2->entry_address() ||
          record1->return_address() != record2->return_address()) {
        return false;
      }
    }
    return true;
  }

#if 0
  int pos1 = 0;
  int pos2 = 0;
  int size1 = size();
  int size2 = other->size();
  while (pos1 < size1 && pos2 < size2) {
    JsrRecord* record1 = record_at(pos1);
    JsrRecord* record2 = other->record_at(pos2);
    int entry1 = record1->entry_address();
    int entry2 = record2->entry_address();
    if (entry1 < entry2) {
      pos1++;
    } else if (entry1 > entry2) {
      pos2++;
    } else {
      if (record1->return_address() == record2->return_address()) {
        pos1++;
        pos2++;
      } else {
        // These two JsrSets are incompatible.
        return false;
      }
    }
  }
  // The two JsrSets agree.
  return true;
#endif
}

// ------------------------------------------------------------------
// ciTypeFlow::JsrSet::insert_jsr_record
//
// Insert the given JsrRecord into the JsrSet, maintaining the order
// of the set and replacing any element with the same entry address.
void ciTypeFlow::JsrSet::insert_jsr_record(JsrRecord* record) {
  int len = size();
  int entry = record->entry_address();
  int pos = 0;
  for ( ; pos < len; pos++) {
    JsrRecord* current = record_at(pos);
    if (entry == current->entry_address()) {
      // Stomp over this entry.
      _set->at_put(pos, record);
      assert(size() == len, "must be same size");
      return;
    } else if (entry < current->entry_address()) {
      break;
    }
  }

  // Insert the record into the list.
  JsrRecord* swap = record;
  JsrRecord* temp = NULL;
  for ( ; pos < len; pos++) {
    temp = _set->at(pos);
    _set->at_put(pos, swap);
    swap = temp;
  }
  _set->append(swap);
  assert(size() == len+1, "must be larger");
}

// ------------------------------------------------------------------
// ciTypeFlow::JsrSet::remove_jsr_record
//
// Remove the JsrRecord with the given return address from the JsrSet.
void ciTypeFlow::JsrSet::remove_jsr_record(int return_address) {
  int len = size();
  for (int i = 0; i < len; i++) {
    if (record_at(i)->return_address() == return_address) {
      // We have found the proper entry.  Remove it from the
      // JsrSet and exit.
      for (int j = i+1; j < len ; j++) {
        _set->at_put(j-1, _set->at(j));
      }
      _set->trunc_to(len-1);
      assert(size() == len-1, "must be smaller");
      return;
    }
  }
  assert(false, "verify: returning from invalid subroutine");
}

// ------------------------------------------------------------------
// ciTypeFlow::JsrSet::apply_control
//
// Apply the effect of a control-flow bytecode on the JsrSet.  The
// only bytecodes that modify the JsrSet are jsr and ret.
void ciTypeFlow::JsrSet::apply_control(ciTypeFlow* analyzer,
                                       ciBytecodeStream* str,
                                       ciTypeFlow::StateVector* state) {
  Bytecodes::Code code = str->cur_bc();
  if (code == Bytecodes::_jsr) {
    JsrRecord* record =
      analyzer->make_jsr_record(str->get_dest(), str->next_bci());
    insert_jsr_record(record);
  } else if (code == Bytecodes::_jsr_w) {
    JsrRecord* record =
      analyzer->make_jsr_record(str->get_far_dest(), str->next_bci());
    insert_jsr_record(record);
  } else if (code == Bytecodes::_ret) {
    Cell local = state->local(str->get_index());
    ciType* return_address = state->type_at(local);
    assert(return_address->is_return_address(), "verify: wrong type");
    if (size() == 0) {
      // Ret-state underflow:  Hit a ret w/o any previous jsrs.  Bail out.
      // This can happen when a loop is inside a finally clause (4614060).
      analyzer->record_failure("OSR in finally clause");
      return;
    }
    remove_jsr_record(return_address->as_return_address()->bci());
  }
}

#ifndef PRODUCT
// ------------------------------------------------------------------
// ciTypeFlow::JsrSet::print_on
void ciTypeFlow::JsrSet::print_on(outputStream* st) const {
  st->print("{ ");
  int num_elements = size();
  if (num_elements > 0) {
    int i = 0;
    for( ; i < num_elements - 1; i++) {
      _set->at(i)->print_on(st);
      st->print(", ");
    }
    _set->at(i)->print_on(st);
    st->print(" ");
  }
  st->print("}");
}
#endif

// ciTypeFlow::StateVector
//
// A StateVector summarizes the type information at some point in
// the program.

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::type_meet
//
// Meet two types.
//
// The semi-lattice of types use by this analysis are modeled on those
// of the verifier.  The lattice is as follows:
//
//        top_type() >= all non-extremal types >= bottom_type
//                             and
//   Every primitive type is comparable only with itself.  The meet of
//   reference types is determined by their kind: instance class,
//   interface, or array class.  The meet of two types of the same
//   kind is their least common ancestor.  The meet of two types of
//   different kinds is always java.lang.Object.
ciType* ciTypeFlow::StateVector::type_meet_internal(ciType* t1, ciType* t2, ciTypeFlow* analyzer) {
  assert(t1 != t2, "checked in caller");
  if (t1->equals(top_type())) {
    return t2;
  } else if (t2->equals(top_type())) {
    return t1;
  } else if (t1->is_primitive_type() || t2->is_primitive_type()) {
    // Special case null_type.  null_type meet any reference type T
    // is T.  null_type meet null_type is null_type.
    if (t1->equals(null_type())) {
      if (!t2->is_primitive_type() || t2->equals(null_type())) {
        return t2;
      }
    } else if (t2->equals(null_type())) {
      if (!t1->is_primitive_type()) {
        return t1;
      }
    }

    // At least one of the two types is a non-top primitive type.
    // The other type is not equal to it.  Fall to bottom.
    return bottom_type();
  } else {
    // Both types are non-top non-primitive types.  That is,
    // both types are either instanceKlasses or arrayKlasses.
    ciKlass* object_klass = analyzer->env()->Object_klass();
    ciKlass* k1 = t1->as_klass();
    ciKlass* k2 = t2->as_klass();
    if (k1->equals(object_klass) || k2->equals(object_klass)) {
      return object_klass;
    } else if (!k1->is_loaded() || !k2->is_loaded()) {
      // Unloaded classes fall to java.lang.Object at a merge.
      return object_klass;
    } else if (k1->is_interface() != k2->is_interface()) {
      // When an interface meets a non-interface, we get Object;
      // This is what the verifier does.
      return object_klass;
    } else if (k1->is_array_klass() || k2->is_array_klass()) {
      // When an array meets a non-array, we get Object.
      // When objArray meets typeArray, we also get Object.
      // And when typeArray meets different typeArray, we again get Object.
      // But when objArray meets objArray, we look carefully at element types.
      if (k1->is_obj_array_klass() && k2->is_obj_array_klass()) {
        // Meet the element types, then construct the corresponding array type.
        ciKlass* elem1 = k1->as_obj_array_klass()->element_klass();
        ciKlass* elem2 = k2->as_obj_array_klass()->element_klass();
        ciKlass* elem  = type_meet_internal(elem1, elem2, analyzer)->as_klass();
        // Do an easy shortcut if one type is a super of the other.
        if (elem == elem1) {
          assert(k1 == ciObjArrayKlass::make(elem), "shortcut is OK");
          return k1;
        } else if (elem == elem2) {
          assert(k2 == ciObjArrayKlass::make(elem), "shortcut is OK");
          return k2;
        } else {
          return ciObjArrayKlass::make(elem);
        }
      } else {
        return object_klass;
      }
    } else {
      // Must be two plain old instance klasses.
      assert(k1->is_instance_klass(), "previous cases handle non-instances");
      assert(k2->is_instance_klass(), "previous cases handle non-instances");
      return k1->least_common_ancestor(k2);
    }
  }
}


// ------------------------------------------------------------------
// ciTypeFlow::StateVector::StateVector
//
// Build a new state vector
ciTypeFlow::StateVector::StateVector(ciTypeFlow* analyzer) {
  _outer = analyzer;
  _stack_size = -1;
  _monitor_count = -1;
  // Allocate the _types array
  int max_cells = analyzer->max_cells();
  _types = (ciType**)analyzer->arena()->Amalloc(sizeof(ciType*) * max_cells);
  for (int i=0; i<max_cells; i++) {
    _types[i] = top_type();
  }
  _trap_bci = -1;
  _trap_index = 0;
}

// ------------------------------------------------------------------
// ciTypeFlow::get_start_state
//
// Set this vector to the method entry state.
const ciTypeFlow::StateVector* ciTypeFlow::get_start_state() {
  StateVector* state = new StateVector(this);
  if (is_osr_flow()) {
    ciTypeFlow* non_osr_flow = method()->get_flow_analysis();
    if (non_osr_flow->failing()) {
      record_failure(non_osr_flow->failure_reason());
      return NULL;
    }
    JsrSet* jsrs = new JsrSet(NULL, 16);
    Block* non_osr_block = non_osr_flow->existing_block_at(start_bci(), jsrs);
    if (non_osr_block == NULL) {
      record_failure("cannot reach OSR point");
      return NULL;
    }
    // load up the non-OSR state at this point
    non_osr_block->copy_state_into(state);
    int non_osr_start = non_osr_block->start();
    if (non_osr_start != start_bci()) {
      // must flow forward from it
      if (CITraceTypeFlow) {
        tty->print_cr(">> Interpreting pre-OSR block %d:", non_osr_start);
      }
      Block* block = block_at(non_osr_start, jsrs);
      assert(block->limit() == start_bci(), "must flow forward to start");
      flow_block(block, state, jsrs);
    }
    return state;
    // Note:  The code below would be an incorrect for an OSR flow,
    // even if it were possible for an OSR entry point to be at bci zero.
  }
  // "Push" the method signature into the first few locals.
  state->set_stack_size(-max_locals());
  if (!method()->is_static()) {
    state->push(method()->holder());
    assert(state->tos() == state->local(0), "");
  }
  for (ciSignatureStream str(method()->signature());
       !str.at_return_type();
       str.next()) {
    state->push_translate(str.type());
  }
  // Set the rest of the locals to bottom.
  Cell cell = state->next_cell(state->tos());
  state->set_stack_size(0);
  int limit = state->limit_cell();
  for (; cell < limit; cell = state->next_cell(cell)) {
    state->set_type_at(cell, state->bottom_type());
  }
  // Lock an object, if necessary.
  state->set_monitor_count(method()->is_synchronized() ? 1 : 0);
  return state;
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::copy_into
//
// Copy our value into some other StateVector
void ciTypeFlow::StateVector::copy_into(ciTypeFlow::StateVector* copy)
const {
  copy->set_stack_size(stack_size());
  copy->set_monitor_count(monitor_count());
  Cell limit = limit_cell();
  for (Cell c = start_cell(); c < limit; c = next_cell(c)) {
    copy->set_type_at(c, type_at(c));
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::meet
//
// Meets this StateVector with another, destructively modifying this
// one.  Returns true if any modification takes place.
bool ciTypeFlow::StateVector::meet(const ciTypeFlow::StateVector* incoming) {
  if (monitor_count() == -1) {
    set_monitor_count(incoming->monitor_count());
  }
  assert(monitor_count() == incoming->monitor_count(), "monitors must match");

  if (stack_size() == -1) {
    set_stack_size(incoming->stack_size());
    Cell limit = limit_cell();
    #ifdef ASSERT
    { for (Cell c = start_cell(); c < limit; c = next_cell(c)) {
        assert(type_at(c) == top_type(), "");
    } }
    #endif
    // Make a simple copy of the incoming state.
    for (Cell c = start_cell(); c < limit; c = next_cell(c)) {
      set_type_at(c, incoming->type_at(c));
    }
    return true;  // it is always different the first time
  }
#ifdef ASSERT
  if (stack_size() != incoming->stack_size()) {
    _outer->method()->print_codes();
    tty->print_cr("!!!! Stack size conflict");
    tty->print_cr("Current state:");
    print_on(tty);
    tty->print_cr("Incoming state:");
    ((StateVector*)incoming)->print_on(tty);
  }
#endif
  assert(stack_size() == incoming->stack_size(), "sanity");

  bool different = false;
  Cell limit = limit_cell();
  for (Cell c = start_cell(); c < limit; c = next_cell(c)) {
    ciType* t1 = type_at(c);
    ciType* t2 = incoming->type_at(c);
    if (!t1->equals(t2)) {
      ciType* new_type = type_meet(t1, t2);
      if (!t1->equals(new_type)) {
        set_type_at(c, new_type);
        different = true;
      }
    }
  }
  return different;
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::meet_exception
//
// Meets this StateVector with another, destructively modifying this
// one.  The incoming state is coming via an exception.  Returns true
// if any modification takes place.
bool ciTypeFlow::StateVector::meet_exception(ciInstanceKlass* exc,
                                     const ciTypeFlow::StateVector* incoming) {
  if (monitor_count() == -1) {
    set_monitor_count(incoming->monitor_count());
  }
  assert(monitor_count() == incoming->monitor_count(), "monitors must match");

  if (stack_size() == -1) {
    set_stack_size(1);
  }

  assert(stack_size() ==  1, "must have one-element stack");

  bool different = false;

  // Meet locals from incoming array.
  Cell limit = local(_outer->max_locals()-1);
  for (Cell c = start_cell(); c <= limit; c = next_cell(c)) {
    ciType* t1 = type_at(c);
    ciType* t2 = incoming->type_at(c);
    if (!t1->equals(t2)) {
      ciType* new_type = type_meet(t1, t2);
      if (!t1->equals(new_type)) {
        set_type_at(c, new_type);
        different = true;
      }
    }
  }

  // Handle stack separately.  When an exception occurs, the
  // only stack entry is the exception instance.
  ciType* tos_type = type_at_tos();
  if (!tos_type->equals(exc)) {
    ciType* new_type = type_meet(tos_type, exc);
    if (!tos_type->equals(new_type)) {
      set_type_at_tos(new_type);
      different = true;
    }
  }

  return different;
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::push_translate
void ciTypeFlow::StateVector::push_translate(ciType* type) {
  BasicType basic_type = type->basic_type();
  if (basic_type == T_BOOLEAN || basic_type == T_CHAR ||
      basic_type == T_BYTE    || basic_type == T_SHORT) {
    push_int();
  } else {
    push(type);
    if (type->is_two_word()) {
      push(half_type(type));
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_aaload
void ciTypeFlow::StateVector::do_aaload(ciBytecodeStream* str) {
  pop_int();
  ciObjArrayKlass* array_klass = pop_objArray();
  if (array_klass == NULL) {
    // Did aaload on a null reference; push a null and ignore the exception.
    // This instruction will never continue normally.  All we have to do
    // is report a value that will meet correctly with any downstream
    // reference types on paths that will truly be executed.  This null type
    // meets with any reference type to yield that same reference type.
    // (The compiler will generate an unconditonal exception here.)
    push(null_type());
    return;
  }
  if (!array_klass->is_loaded()) {
    // Only fails for some -Xcomp runs
    trap(str, array_klass,
         Deoptimization::make_trap_request
         (Deoptimization::Reason_unloaded,
          Deoptimization::Action_reinterpret));
    return;
  }
  ciKlass* element_klass = array_klass->element_klass();
  if (!element_klass->is_loaded() && element_klass->is_instance_klass()) {
    Untested("unloaded array element class in ciTypeFlow");
    trap(str, element_klass,
         Deoptimization::make_trap_request
         (Deoptimization::Reason_unloaded,
          Deoptimization::Action_reinterpret));
  } else {
    push_object(element_klass);
  }
}


// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_checkcast
void ciTypeFlow::StateVector::do_checkcast(ciBytecodeStream* str) {
  bool will_link;
  ciKlass* klass = str->get_klass(will_link);
  if (!will_link) {
    // VM's interpreter will not load 'klass' if object is NULL.
    // Type flow after this block may still be needed in two situations:
    // 1) C2 uses do_null_assert() and continues compilation for later blocks
    // 2) C2 does an OSR compile in a later block (see bug 4778368).
    pop_object();
    do_null_assert(klass);
  } else {
    pop_object();
    push_object(klass);
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_getfield
void ciTypeFlow::StateVector::do_getfield(ciBytecodeStream* str) {
  // could add assert here for type of object.
  pop_object();
  do_getstatic(str);
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_getstatic
void ciTypeFlow::StateVector::do_getstatic(ciBytecodeStream* str) {
  bool will_link;
  ciField* field = str->get_field(will_link);
  if (!will_link) {
    trap(str, field->holder(), str->get_field_holder_index());
  } else {
    ciType* field_type = field->type();
    if (!field_type->is_loaded()) {
      // Normally, we need the field's type to be loaded if we are to
      // do anything interesting with its value.
      // We used to do this:  trap(str, str->get_field_signature_index());
      //
      // There is one good reason not to trap here.  Execution can
      // get past this "getfield" or "getstatic" if the value of
      // the field is null.  As long as the value is null, the class
      // does not need to be loaded!  The compiler must assume that
      // the value of the unloaded class reference is null; if the code
      // ever sees a non-null value, loading has occurred.
      //
      // This actually happens often enough to be annoying.  If the
      // compiler throws an uncommon trap at this bytecode, you can
      // get an endless loop of recompilations, when all the code
      // needs to do is load a series of null values.  Also, a trap
      // here can make an OSR entry point unreachable, triggering the
      // assert on non_osr_block in ciTypeFlow::get_start_state.
      // (See bug 4379915.)
      do_null_assert(field_type->as_klass());
    } else {
      push_translate(field_type);
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_invoke
void ciTypeFlow::StateVector::do_invoke(ciBytecodeStream* str,
                                        bool has_receiver) {
  bool will_link;
  ciMethod* method = str->get_method(will_link);
  if (!will_link) {
    // We weren't able to find the method.
    ciKlass* unloaded_holder = method->holder();
    trap(str, unloaded_holder, str->get_method_holder_index());
  } else {
    ciSignature* signature = method->signature();
    ciSignatureStream sigstr(signature);
    int arg_size = signature->size();
    int stack_base = stack_size() - arg_size;
    int i = 0;
    for( ; !sigstr.at_return_type(); sigstr.next()) {
      ciType* type = sigstr.type();
      ciType* stack_type = type_at(stack(stack_base + i++));
      // Do I want to check this type?
      // assert(stack_type->is_subtype_of(type), "bad type for field value");
      if (type->is_two_word()) {
        ciType* stack_type2 = type_at(stack(stack_base + i++));
        assert(stack_type2->equals(half_type(type)), "must be 2nd half");
      }
    }
    assert(arg_size == i, "must match");
    for (int j = 0; j < arg_size; j++) {
      pop();
    }
    if (has_receiver) {
      // Check this?
      pop_object();
    }
    assert(!sigstr.is_done(), "must have return type");
    ciType* return_type = sigstr.type();
    if (!return_type->is_void()) {
      if (!return_type->is_loaded()) {
        // As in do_getstatic(), generally speaking, we need the return type to
        // be loaded if we are to do anything interesting with its value.
        // We used to do this:  trap(str, str->get_method_signature_index());
        //
        // We do not trap here since execution can get past this invoke if
        // the return value is null.  As long as the value is null, the class
        // does not need to be loaded!  The compiler must assume that
        // the value of the unloaded class reference is null; if the code
        // ever sees a non-null value, loading has occurred.
        //
        // See do_getstatic() for similar explanation, as well as bug 4684993.
        do_null_assert(return_type->as_klass());
      } else {
        push_translate(return_type);
      }
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_jsr
void ciTypeFlow::StateVector::do_jsr(ciBytecodeStream* str) {
  push(ciReturnAddress::make(str->next_bci()));
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_ldc
void ciTypeFlow::StateVector::do_ldc(ciBytecodeStream* str) {
  ciConstant con = str->get_constant();
  BasicType basic_type = con.basic_type();
  if (basic_type == T_ILLEGAL) {
    // OutOfMemoryError in the CI while loading constant
    push_null();
    outer()->record_failure("ldc did not link");
    return;
  }
  if (basic_type == T_OBJECT || basic_type == T_ARRAY) {
    ciObject* obj = con.as_object();
    if (obj->is_null_object()) {
      push_null();
    } else if (obj->is_klass()) {
      // The type of ldc <class> is java.lang.Class
      push_object(outer()->env()->Class_klass());
    } else {
      push_object(obj->klass());
    }
  } else {
    push_translate(ciType::make(basic_type));
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_multianewarray
void ciTypeFlow::StateVector::do_multianewarray(ciBytecodeStream* str) {
  int dimensions = str->get_dimensions();
  bool will_link;
  ciArrayKlass* array_klass = str->get_klass(will_link)->as_array_klass();
  if (!will_link) {
    trap(str, array_klass, str->get_klass_index());
  } else {
    for (int i = 0; i < dimensions; i++) {
      pop_int();
    }
    push_object(array_klass);
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_new
void ciTypeFlow::StateVector::do_new(ciBytecodeStream* str) {
  bool will_link;
  ciKlass* klass = str->get_klass(will_link);
  if (!will_link) {
    trap(str, klass, str->get_klass_index());
  } else {
    push_object(klass);
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_newarray
void ciTypeFlow::StateVector::do_newarray(ciBytecodeStream* str) {
  pop_int();
  ciKlass* klass = ciTypeArrayKlass::make((BasicType)str->get_index());
  push_object(klass);
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_putfield
void ciTypeFlow::StateVector::do_putfield(ciBytecodeStream* str) {
  do_putstatic(str);
  if (_trap_bci != -1)  return;  // unloaded field holder, etc.
  // could add assert here for type of object.
  pop_object();
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_putstatic
void ciTypeFlow::StateVector::do_putstatic(ciBytecodeStream* str) {
  bool will_link;
  ciField* field = str->get_field(will_link);
  if (!will_link) {
    trap(str, field->holder(), str->get_field_holder_index());
  } else {
    ciType* field_type = field->type();
    ciType* type = pop_value();
    // Do I want to check this type?
    //      assert(type->is_subtype_of(field_type), "bad type for field value");
    if (field_type->is_two_word()) {
      ciType* type2 = pop_value();
      assert(type2->is_two_word(), "must be 2nd half");
      assert(type == half_type(type2), "must be 2nd half");
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_ret
void ciTypeFlow::StateVector::do_ret(ciBytecodeStream* str) {
  Cell index = local(str->get_index());

  ciType* address = type_at(index);
  assert(address->is_return_address(), "bad return address");
  set_type_at(index, bottom_type());
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::trap
//
// Stop interpretation of this path with a trap.
void ciTypeFlow::StateVector::trap(ciBytecodeStream* str, ciKlass* klass, int index) {
  _trap_bci = str->cur_bci();
  _trap_index = index;

  // Log information about this trap:
  CompileLog* log = outer()->env()->log();
  if (log != NULL) {
    int mid = log->identify(outer()->method());
    int kid = (klass == NULL)? -1: log->identify(klass);
    log->begin_elem("uncommon_trap method='%d' bci='%d'", mid, str->cur_bci());
    char buf[100];
    log->print(" %s", Deoptimization::format_trap_request(buf, sizeof(buf),
                                                          index));
    if (kid >= 0)
      log->print(" klass='%d'", kid);
    log->end_elem();
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::do_null_assert
// Corresponds to graphKit::do_null_assert.
void ciTypeFlow::StateVector::do_null_assert(ciKlass* unloaded_klass) {
  if (unloaded_klass->is_loaded()) {
    // We failed to link, but we can still compute with this class,
    // since it is loaded somewhere.  The compiler will uncommon_trap
    // if the object is not null, but the typeflow pass can not assume
    // that the object will be null, otherwise it may incorrectly tell
    // the parser that an object is known to be null. 4761344, 4807707
    push_object(unloaded_klass);
  } else {
    // The class is not loaded anywhere.  It is safe to model the
    // null in the typestates, because we can compile in a null check
    // which will deoptimize us if someone manages to load the
    // class later.
    push_null();
  }
}


// ------------------------------------------------------------------
// ciTypeFlow::StateVector::apply_one_bytecode
//
// Apply the effect of one bytecode to this StateVector
bool ciTypeFlow::StateVector::apply_one_bytecode(ciBytecodeStream* str) {
  _trap_bci = -1;
  _trap_index = 0;

  if (CITraceTypeFlow) {
    tty->print_cr(">> Interpreting bytecode %d:%s", str->cur_bci(),
                  Bytecodes::name(str->cur_bc()));
  }

  switch(str->cur_bc()) {
  case Bytecodes::_aaload: do_aaload(str);                       break;

  case Bytecodes::_aastore:
    {
      pop_object();
      pop_int();
      pop_objArray();
      break;
    }
  case Bytecodes::_aconst_null:
    {
      push_null();
      break;
    }
  case Bytecodes::_aload:   load_local_object(str->get_index());    break;
  case Bytecodes::_aload_0: load_local_object(0);                   break;
  case Bytecodes::_aload_1: load_local_object(1);                   break;
  case Bytecodes::_aload_2: load_local_object(2);                   break;
  case Bytecodes::_aload_3: load_local_object(3);                   break;

  case Bytecodes::_anewarray:
    {
      pop_int();
      bool will_link;
      ciKlass* element_klass = str->get_klass(will_link);
      if (!will_link) {
        trap(str, element_klass, str->get_klass_index());
      } else {
        push_object(ciObjArrayKlass::make(element_klass));
      }
      break;
    }
  case Bytecodes::_areturn:
  case Bytecodes::_ifnonnull:
  case Bytecodes::_ifnull:
    {
      pop_object();
      break;
    }
  case Bytecodes::_monitorenter:
    {
      pop_object();
      set_monitor_count(monitor_count() + 1);
      break;
    }
  case Bytecodes::_monitorexit:
    {
      pop_object();
      assert(monitor_count() > 0, "must be a monitor to exit from");
      set_monitor_count(monitor_count() - 1);
      break;
    }
  case Bytecodes::_arraylength:
    {
      pop_array();
      push_int();
      break;
    }
  case Bytecodes::_astore:   store_local_object(str->get_index());  break;
  case Bytecodes::_astore_0: store_local_object(0);                 break;
  case Bytecodes::_astore_1: store_local_object(1);                 break;
  case Bytecodes::_astore_2: store_local_object(2);                 break;
  case Bytecodes::_astore_3: store_local_object(3);                 break;

  case Bytecodes::_athrow:
    {
      NEEDS_CLEANUP;
      pop_object();
      break;
    }
  case Bytecodes::_baload:
  case Bytecodes::_caload:
  case Bytecodes::_iaload:
  case Bytecodes::_saload:
    {
      pop_int();
      ciTypeArrayKlass* array_klass = pop_typeArray();
      // Put assert here for right type?
      push_int();
      break;
    }
  case Bytecodes::_bastore:
  case Bytecodes::_castore:
  case Bytecodes::_iastore:
  case Bytecodes::_sastore:
    {
      pop_int();
      pop_int();
      pop_typeArray();
      // assert here?
      break;
    }
  case Bytecodes::_bipush:
  case Bytecodes::_iconst_m1:
  case Bytecodes::_iconst_0:
  case Bytecodes::_iconst_1:
  case Bytecodes::_iconst_2:
  case Bytecodes::_iconst_3:
  case Bytecodes::_iconst_4:
  case Bytecodes::_iconst_5:
  case Bytecodes::_sipush:
    {
      push_int();
      break;
    }
  case Bytecodes::_checkcast: do_checkcast(str);                  break;

  case Bytecodes::_d2f:
    {
      pop_double();
      push_float();
      break;
    }
  case Bytecodes::_d2i:
    {
      pop_double();
      push_int();
      break;
    }
  case Bytecodes::_d2l:
    {
      pop_double();
      push_long();
      break;
    }
  case Bytecodes::_dadd:
  case Bytecodes::_ddiv:
  case Bytecodes::_dmul:
  case Bytecodes::_drem:
  case Bytecodes::_dsub:
    {
      pop_double();
      pop_double();
      push_double();
      break;
    }
  case Bytecodes::_daload:
    {
      pop_int();
      ciTypeArrayKlass* array_klass = pop_typeArray();
      // Put assert here for right type?
      push_double();
      break;
    }
  case Bytecodes::_dastore:
    {
      pop_double();
      pop_int();
      pop_typeArray();
      // assert here?
      break;
    }
  case Bytecodes::_dcmpg:
  case Bytecodes::_dcmpl:
    {
      pop_double();
      pop_double();
      push_int();
      break;
    }
  case Bytecodes::_dconst_0:
  case Bytecodes::_dconst_1:
    {
      push_double();
      break;
    }
  case Bytecodes::_dload:   load_local_double(str->get_index());    break;
  case Bytecodes::_dload_0: load_local_double(0);                   break;
  case Bytecodes::_dload_1: load_local_double(1);                   break;
  case Bytecodes::_dload_2: load_local_double(2);                   break;
  case Bytecodes::_dload_3: load_local_double(3);                   break;

  case Bytecodes::_dneg:
    {
      pop_double();
      push_double();
      break;
    }
  case Bytecodes::_dreturn:
    {
      pop_double();
      break;
    }
  case Bytecodes::_dstore:   store_local_double(str->get_index());  break;
  case Bytecodes::_dstore_0: store_local_double(0);                 break;
  case Bytecodes::_dstore_1: store_local_double(1);                 break;
  case Bytecodes::_dstore_2: store_local_double(2);                 break;
  case Bytecodes::_dstore_3: store_local_double(3);                 break;

  case Bytecodes::_dup:
    {
      push(type_at_tos());
      break;
    }
  case Bytecodes::_dup_x1:
    {
      ciType* value1 = pop_value();
      ciType* value2 = pop_value();
      push(value1);
      push(value2);
      push(value1);
      break;
    }
  case Bytecodes::_dup_x2:
    {
      ciType* value1 = pop_value();
      ciType* value2 = pop_value();
      ciType* value3 = pop_value();
      push(value1);
      push(value3);
      push(value2);
      push(value1);
      break;
    }
  case Bytecodes::_dup2:
    {
      ciType* value1 = pop_value();
      ciType* value2 = pop_value();
      push(value2);
      push(value1);
      push(value2);
      push(value1);
      break;
    }
  case Bytecodes::_dup2_x1:
    {
      ciType* value1 = pop_value();
      ciType* value2 = pop_value();
      ciType* value3 = pop_value();
      push(value2);
      push(value1);
      push(value3);
      push(value2);
      push(value1);
      break;
    }
  case Bytecodes::_dup2_x2:
    {
      ciType* value1 = pop_value();
      ciType* value2 = pop_value();
      ciType* value3 = pop_value();
      ciType* value4 = pop_value();
      push(value2);
      push(value1);
      push(value4);
      push(value3);
      push(value2);
      push(value1);
      break;
    }
  case Bytecodes::_f2d:
    {
      pop_float();
      push_double();
      break;
    }
  case Bytecodes::_f2i:
    {
      pop_float();
      push_int();
      break;
    }
  case Bytecodes::_f2l:
    {
      pop_float();
      push_long();
      break;
    }
  case Bytecodes::_fadd:
  case Bytecodes::_fdiv:
  case Bytecodes::_fmul:
  case Bytecodes::_frem:
  case Bytecodes::_fsub:
    {
      pop_float();
      pop_float();
      push_float();
      break;
    }
  case Bytecodes::_faload:
    {
      pop_int();
      ciTypeArrayKlass* array_klass = pop_typeArray();
      // Put assert here.
      push_float();
      break;
    }
  case Bytecodes::_fastore:
    {
      pop_float();
      pop_int();
      ciTypeArrayKlass* array_klass = pop_typeArray();
      // Put assert here.
      break;
    }
  case Bytecodes::_fcmpg:
  case Bytecodes::_fcmpl:
    {
      pop_float();
      pop_float();
      push_int();
      break;
    }
  case Bytecodes::_fconst_0:
  case Bytecodes::_fconst_1:
  case Bytecodes::_fconst_2:
    {
      push_float();
      break;
    }
  case Bytecodes::_fload:   load_local_float(str->get_index());     break;
  case Bytecodes::_fload_0: load_local_float(0);                    break;
  case Bytecodes::_fload_1: load_local_float(1);                    break;
  case Bytecodes::_fload_2: load_local_float(2);                    break;
  case Bytecodes::_fload_3: load_local_float(3);                    break;

  case Bytecodes::_fneg:
    {
      pop_float();
      push_float();
      break;
    }
  case Bytecodes::_freturn:
    {
      pop_float();
      break;
    }
  case Bytecodes::_fstore:    store_local_float(str->get_index());   break;
  case Bytecodes::_fstore_0:  store_local_float(0);                  break;
  case Bytecodes::_fstore_1:  store_local_float(1);                  break;
  case Bytecodes::_fstore_2:  store_local_float(2);                  break;
  case Bytecodes::_fstore_3:  store_local_float(3);                  break;

  case Bytecodes::_getfield:  do_getfield(str);                      break;
  case Bytecodes::_getstatic: do_getstatic(str);                     break;

  case Bytecodes::_goto:
  case Bytecodes::_goto_w:
  case Bytecodes::_nop:
  case Bytecodes::_return:
    {
      // do nothing.
      break;
    }
  case Bytecodes::_i2b:
  case Bytecodes::_i2c:
  case Bytecodes::_i2s:
  case Bytecodes::_ineg:
    {
      pop_int();
      push_int();
      break;
    }
  case Bytecodes::_i2d:
    {
      pop_int();
      push_double();
      break;
    }
  case Bytecodes::_i2f:
    {
      pop_int();
      push_float();
      break;
    }
  case Bytecodes::_i2l:
    {
      pop_int();
      push_long();
      break;
    }
  case Bytecodes::_iadd:
  case Bytecodes::_iand:
  case Bytecodes::_idiv:
  case Bytecodes::_imul:
  case Bytecodes::_ior:
  case Bytecodes::_irem:
  case Bytecodes::_ishl:
  case Bytecodes::_ishr:
  case Bytecodes::_isub:
  case Bytecodes::_iushr:
  case Bytecodes::_ixor:
    {
      pop_int();
      pop_int();
      push_int();
      break;
    }
  case Bytecodes::_if_acmpeq:
  case Bytecodes::_if_acmpne:
    {
      pop_object();
      pop_object();
      break;
    }
  case Bytecodes::_if_icmpeq:
  case Bytecodes::_if_icmpge:
  case Bytecodes::_if_icmpgt:
  case Bytecodes::_if_icmple:
  case Bytecodes::_if_icmplt:
  case Bytecodes::_if_icmpne:
    {
      pop_int();
      pop_int();
      break;
    }
  case Bytecodes::_ifeq:
  case Bytecodes::_ifle:
  case Bytecodes::_iflt:
  case Bytecodes::_ifge:
  case Bytecodes::_ifgt:
  case Bytecodes::_ifne:
  case Bytecodes::_ireturn:
  case Bytecodes::_lookupswitch:
  case Bytecodes::_tableswitch:
    {
      pop_int();
      break;
    }
  case Bytecodes::_iinc:
    {
      check_int(local(str->get_index()));
      break;
    }
  case Bytecodes::_iload:   load_local_int(str->get_index()); break;
  case Bytecodes::_iload_0: load_local_int(0);                      break;
  case Bytecodes::_iload_1: load_local_int(1);                      break;
  case Bytecodes::_iload_2: load_local_int(2);                      break;
  case Bytecodes::_iload_3: load_local_int(3);                      break;

  case Bytecodes::_instanceof:
    {
      // Check for uncommon trap:
      do_checkcast(str);
      pop_object();
      push_int();
      break;
    }
  case Bytecodes::_invokeinterface: do_invoke(str, true);           break;
  case Bytecodes::_invokespecial:   do_invoke(str, true);           break;
  case Bytecodes::_invokestatic:    do_invoke(str, false);          break;

  case Bytecodes::_invokevirtual:   do_invoke(str, true);           break;

  case Bytecodes::_istore:   store_local_int(str->get_index());     break;
  case Bytecodes::_istore_0: store_local_int(0);                    break;
  case Bytecodes::_istore_1: store_local_int(1);                    break;
  case Bytecodes::_istore_2: store_local_int(2);                    break;
  case Bytecodes::_istore_3: store_local_int(3);                    break;

  case Bytecodes::_jsr:
  case Bytecodes::_jsr_w: do_jsr(str);                              break;

  case Bytecodes::_l2d:
    {
      pop_long();
      push_double();
      break;
    }
  case Bytecodes::_l2f:
    {
      pop_long();
      push_float();
      break;
    }
  case Bytecodes::_l2i:
    {
      pop_long();
      push_int();
      break;
    }
  case Bytecodes::_ladd:
  case Bytecodes::_land:
  case Bytecodes::_ldiv:
  case Bytecodes::_lmul:
  case Bytecodes::_lor:
  case Bytecodes::_lrem:
  case Bytecodes::_lsub:
  case Bytecodes::_lxor:
    {
      pop_long();
      pop_long();
      push_long();
      break;
    }
  case Bytecodes::_laload:
    {
      pop_int();
      ciTypeArrayKlass* array_klass = pop_typeArray();
      // Put assert here for right type?
      push_long();
      break;
    }
  case Bytecodes::_lastore:
    {
      pop_long();
      pop_int();
      pop_typeArray();
      // assert here?
      break;
    }
  case Bytecodes::_lcmp:
    {
      pop_long();
      pop_long();
      push_int();
      break;
    }
  case Bytecodes::_lconst_0:
  case Bytecodes::_lconst_1:
    {
      push_long();
      break;
    }
  case Bytecodes::_ldc:
  case Bytecodes::_ldc_w:
  case Bytecodes::_ldc2_w:
    {
      do_ldc(str);
      break;
    }

  case Bytecodes::_lload:   load_local_long(str->get_index());      break;
  case Bytecodes::_lload_0: load_local_long(0);                     break;
  case Bytecodes::_lload_1: load_local_long(1);                     break;
  case Bytecodes::_lload_2: load_local_long(2);                     break;
  case Bytecodes::_lload_3: load_local_long(3);                     break;

  case Bytecodes::_lneg:
    {
      pop_long();
      push_long();
      break;
    }
  case Bytecodes::_lreturn:
    {
      pop_long();
      break;
    }
  case Bytecodes::_lshl:
  case Bytecodes::_lshr:
  case Bytecodes::_lushr:
    {
      pop_int();
      pop_long();
      push_long();
      break;
    }
  case Bytecodes::_lstore:   store_local_long(str->get_index());    break;
  case Bytecodes::_lstore_0: store_local_long(0);                   break;
  case Bytecodes::_lstore_1: store_local_long(1);                   break;
  case Bytecodes::_lstore_2: store_local_long(2);                   break;
  case Bytecodes::_lstore_3: store_local_long(3);                   break;

  case Bytecodes::_multianewarray: do_multianewarray(str);          break;

  case Bytecodes::_new:      do_new(str);                           break;

  case Bytecodes::_newarray: do_newarray(str);                      break;

  case Bytecodes::_pop:
    {
      pop();
      break;
    }
  case Bytecodes::_pop2:
    {
      pop();
      pop();
      break;
    }

  case Bytecodes::_putfield:       do_putfield(str);                 break;
  case Bytecodes::_putstatic:      do_putstatic(str);                break;

  case Bytecodes::_ret: do_ret(str);                                 break;

  case Bytecodes::_swap:
    {
      ciType* value1 = pop_value();
      ciType* value2 = pop_value();
      push(value1);
      push(value2);
      break;
    }
  case Bytecodes::_wide:
  default:
    {
      // The iterator should skip this.
      ShouldNotReachHere();
      break;
    }
  }

  if (CITraceTypeFlow) {
    print_on(tty);
  }

  return (_trap_bci != -1);
}

#ifndef PRODUCT
// ------------------------------------------------------------------
// ciTypeFlow::StateVector::print_cell_on
void ciTypeFlow::StateVector::print_cell_on(outputStream* st, Cell c) const {
  ciType* type = type_at(c);
  if (type == top_type()) {
    st->print("top");
  } else if (type == bottom_type()) {
    st->print("bottom");
  } else if (type == null_type()) {
    st->print("null");
  } else if (type == long2_type()) {
    st->print("long2");
  } else if (type == double2_type()) {
    st->print("double2");
  } else if (is_int(type)) {
    st->print("int");
  } else if (is_long(type)) {
    st->print("long");
  } else if (is_float(type)) {
    st->print("float");
  } else if (is_double(type)) {
    st->print("double");
  } else if (type->is_return_address()) {
    st->print("address(%d)", type->as_return_address()->bci());
  } else {
    if (type->is_klass()) {
      type->as_klass()->name()->print_symbol_on(st);
    } else {
      st->print("UNEXPECTED TYPE");
      type->print();
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::StateVector::print_on
void ciTypeFlow::StateVector::print_on(outputStream* st) const {
  int num_locals   = _outer->max_locals();
  int num_stack    = stack_size();
  int num_monitors = monitor_count();
  st->print_cr("  State : locals %d, stack %d, monitors %d", num_locals, num_stack, num_monitors);
  if (num_stack >= 0) {
    int i;
    for (i = 0; i < num_locals; i++) {
      st->print("    local %2d : ", i);
      print_cell_on(st, local(i));
      st->cr();
    }
    for (i = 0; i < num_stack; i++) {
      st->print("    stack %2d : ", i);
      print_cell_on(st, stack(i));
      st->cr();
    }
  }
}
#endif

// ciTypeFlow::Block
//
// A basic block.

// ------------------------------------------------------------------
// ciTypeFlow::Block::Block
ciTypeFlow::Block::Block(ciTypeFlow* outer,
                         ciBlock *ciblk,
                         ciTypeFlow::JsrSet* jsrs) {
  _ciblock = ciblk;
  _exceptions = NULL;
  _exc_klasses = NULL;
  _successors = NULL;
  _state = new (outer->arena()) StateVector(outer);
  JsrSet* new_jsrs =
    new (outer->arena()) JsrSet(outer->arena(), jsrs->size());
  jsrs->copy_into(new_jsrs);
  _jsrs = new_jsrs;
  _next = NULL;
  _on_work_list = false;
  _pre_order = -1; assert(!has_pre_order(), "");
  _private_copy = false;
  _trap_bci = -1;
  _trap_index = 0;

  if (CITraceTypeFlow) {
    tty->print_cr(">> Created new block");
    print_on(tty);
  }

  assert(this->outer() == outer, "outer link set up");
  assert(!outer->have_block_count(), "must not have mapped blocks yet");
}

// ------------------------------------------------------------------
// ciTypeFlow::Block::clone_loop_head
//
ciTypeFlow::Block*
ciTypeFlow::Block::clone_loop_head(ciTypeFlow* analyzer,
                                   int branch_bci,
                                   ciTypeFlow::Block* target,
                                   ciTypeFlow::JsrSet* jsrs) {
  // Loop optimizations are not performed on Tier1 compiles. Do nothing.
  if (analyzer->env()->comp_level() < CompLevel_full_optimization) {
    return target;
  }

  // The current block ends with a branch.
  //
  // If the target block appears to be the test-clause of a for loop, and
  // it is not too large, and it has not yet been cloned, clone it.
  // The pre-existing copy becomes the private clone used only by
  // the initial iteration of the loop.  (We know we are simulating
  // the initial iteration right now, since we have never calculated
  // successors before for this block.)

  if (branch_bci <= start()
      && (target->limit() - target->start()) <= CICloneLoopTestLimit
      && target->private_copy_count() == 0) {
    // Setting the private_copy bit ensures that the target block cannot be
    // reached by any other paths, such as fall-in from the loop body.
    // The private copy will be accessible only on successor lists
    // created up to this point.
    target->set_private_copy(true);
    if (CITraceTypeFlow) {
      tty->print(">> Cloning a test-clause block ");
      print_value_on(tty);
      tty->cr();
    }
    // If the target is the current block, then later on a new copy of the
    // target block will be created when its bytecodes are reached by
    // an alternate path. (This is the case for loops with the loop
    // head at the bci-wise bottom of the loop, as with pre-1.4.2 javac.)
    //
    // Otherwise, duplicate the target block now and use it immediately.
    // (The case for loops with the loop head at the bci-wise top of the
    // loop, as with 1.4.2 javac.)
    //
    // In either case, the new copy of the block will remain public.
    if (target != this) {
      target = analyzer->block_at(branch_bci, jsrs);
    }
  }
  return target;
}

// ------------------------------------------------------------------
// ciTypeFlow::Block::successors
//
// Get the successors for this Block.
GrowableArray<ciTypeFlow::Block*>*
ciTypeFlow::Block::successors(ciBytecodeStream* str,
                              ciTypeFlow::StateVector* state,
                              ciTypeFlow::JsrSet* jsrs) {
  if (_successors == NULL) {
    if (CITraceTypeFlow) {
      tty->print(">> Computing successors for block ");
      print_value_on(tty);
      tty->cr();
    }

    ciTypeFlow* analyzer = outer();
    Arena* arena = analyzer->arena();
    Block* block = NULL;
    bool has_successor = !has_trap() &&
                         (control() != ciBlock::fall_through_bci || limit() < analyzer->code_size());
    if (!has_successor) {
      _successors =
        new (arena) GrowableArray<Block*>(arena, 1, 0, NULL);
      // No successors
    } else if (control() == ciBlock::fall_through_bci) {
      assert(str->cur_bci() == limit(), "bad block end");
      // This block simply falls through to the next.
      _successors =
        new (arena) GrowableArray<Block*>(arena, 1, 0, NULL);

      Block* block = analyzer->block_at(limit(), _jsrs);
      assert(_successors->length() == FALL_THROUGH, "");
      _successors->append(block);
    } else {
      int current_bci = str->cur_bci();
      int next_bci = str->next_bci();
      int branch_bci = -1;
      Block* target = NULL;
      assert(str->next_bci() == limit(), "bad block end");
      // This block is not a simple fall-though.  Interpret
      // the current bytecode to find our successors.
      switch (str->cur_bc()) {
      case Bytecodes::_ifeq:         case Bytecodes::_ifne:
      case Bytecodes::_iflt:         case Bytecodes::_ifge:
      case Bytecodes::_ifgt:         case Bytecodes::_ifle:
      case Bytecodes::_if_icmpeq:    case Bytecodes::_if_icmpne:
      case Bytecodes::_if_icmplt:    case Bytecodes::_if_icmpge:
      case Bytecodes::_if_icmpgt:    case Bytecodes::_if_icmple:
      case Bytecodes::_if_acmpeq:    case Bytecodes::_if_acmpne:
      case Bytecodes::_ifnull:       case Bytecodes::_ifnonnull:
        // Our successors are the branch target and the next bci.
        branch_bci = str->get_dest();
        clone_loop_head(analyzer, branch_bci, this, jsrs);
        _successors =
          new (arena) GrowableArray<Block*>(arena, 2, 0, NULL);
        assert(_successors->length() == IF_NOT_TAKEN, "");
        _successors->append(analyzer->block_at(next_bci, jsrs));
        assert(_successors->length() == IF_TAKEN, "");
        _successors->append(analyzer->block_at(branch_bci, jsrs));
        break;

      case Bytecodes::_goto:
        branch_bci = str->get_dest();
        _successors =
          new (arena) GrowableArray<Block*>(arena, 1, 0, NULL);
        assert(_successors->length() == GOTO_TARGET, "");
        target = analyzer->block_at(branch_bci, jsrs);
        // If the target block has not been visited yet, and looks like
        // a two-way branch, attempt to clone it if it is a loop head.
        if (target->_successors != NULL
            && target->_successors->length() == (IF_TAKEN + 1)) {
          target = clone_loop_head(analyzer, branch_bci, target, jsrs);
        }
        _successors->append(target);
        break;

      case Bytecodes::_jsr:
        branch_bci = str->get_dest();
        _successors =
          new (arena) GrowableArray<Block*>(arena, 1, 0, NULL);
        assert(_successors->length() == GOTO_TARGET, "");
        _successors->append(analyzer->block_at(branch_bci, jsrs));
        break;

      case Bytecodes::_goto_w:
      case Bytecodes::_jsr_w:
        _successors =
          new (arena) GrowableArray<Block*>(arena, 1, 0, NULL);
        assert(_successors->length() == GOTO_TARGET, "");
        _successors->append(analyzer->block_at(str->get_far_dest(), jsrs));
        break;

      case Bytecodes::_tableswitch:  {
        Bytecode_tableswitch *tableswitch =
          Bytecode_tableswitch_at(str->cur_bcp());

        int len = tableswitch->length();
        _successors =
          new (arena) GrowableArray<Block*>(arena, len+1, 0, NULL);
        int bci = current_bci + tableswitch->default_offset();
        Block* block = analyzer->block_at(bci, jsrs);
        assert(_successors->length() == SWITCH_DEFAULT, "");
        _successors->append(block);
        while (--len >= 0) {
          int bci = current_bci + tableswitch->dest_offset_at(len);
          block = analyzer->block_at(bci, jsrs);
          assert(_successors->length() >= SWITCH_CASES, "");
          _successors->append_if_missing(block);
        }
        break;
      }

      case Bytecodes::_lookupswitch: {
        Bytecode_lookupswitch *lookupswitch =
          Bytecode_lookupswitch_at(str->cur_bcp());

        int npairs = lookupswitch->number_of_pairs();
        _successors =
          new (arena) GrowableArray<Block*>(arena, npairs+1, 0, NULL);
        int bci = current_bci + lookupswitch->default_offset();
        Block* block = analyzer->block_at(bci, jsrs);
        assert(_successors->length() == SWITCH_DEFAULT, "");
        _successors->append(block);
        while(--npairs >= 0) {
          LookupswitchPair *pair = lookupswitch->pair_at(npairs);
          int bci = current_bci + pair->offset();
          Block* block = analyzer->block_at(bci, jsrs);
          assert(_successors->length() >= SWITCH_CASES, "");
          _successors->append_if_missing(block);
        }
        break;
      }

      case Bytecodes::_athrow:     case Bytecodes::_ireturn:
      case Bytecodes::_lreturn:    case Bytecodes::_freturn:
      case Bytecodes::_dreturn:    case Bytecodes::_areturn:
      case Bytecodes::_return:
        _successors =
          new (arena) GrowableArray<Block*>(arena, 1, 0, NULL);
        // No successors
        break;

      case Bytecodes::_ret: {
        _successors =
          new (arena) GrowableArray<Block*>(arena, 1, 0, NULL);

        Cell local = state->local(str->get_index());
        ciType* return_address = state->type_at(local);
        assert(return_address->is_return_address(), "verify: wrong type");
        int bci = return_address->as_return_address()->bci();
        assert(_successors->length() == GOTO_TARGET, "");
        _successors->append(analyzer->block_at(bci, jsrs));
        break;
      }

      case Bytecodes::_wide:
      default:
        ShouldNotReachHere();
        break;
      }
    }
  }
  return _successors;
}

// ------------------------------------------------------------------
// ciTypeFlow::Block:compute_exceptions
//
// Compute the exceptional successors and types for this Block.
void ciTypeFlow::Block::compute_exceptions() {
  assert(_exceptions == NULL && _exc_klasses == NULL, "repeat");

  if (CITraceTypeFlow) {
    tty->print(">> Computing exceptions for block ");
    print_value_on(tty);
    tty->cr();
  }

  ciTypeFlow* analyzer = outer();
  Arena* arena = analyzer->arena();

  // Any bci in the block will do.
  ciExceptionHandlerStream str(analyzer->method(), start());

  // Allocate our growable arrays.
  int exc_count = str.count();
  _exceptions = new (arena) GrowableArray<Block*>(arena, exc_count, 0, NULL);
  _exc_klasses = new (arena) GrowableArray<ciInstanceKlass*>(arena, exc_count,
                                                             0, NULL);

  for ( ; !str.is_done(); str.next()) {
    ciExceptionHandler* handler = str.handler();
    int bci = handler->handler_bci();
    ciInstanceKlass* klass = NULL;
    if (bci == -1) {
      // There is no catch all.  It is possible to exit the method.
      break;
    }
    if (handler->is_catch_all()) {
      klass = analyzer->env()->Throwable_klass();
    } else {
      klass = handler->catch_klass();
    }
    _exceptions->append(analyzer->block_at(bci, _jsrs));
    _exc_klasses->append(klass);
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::Block::is_simpler_than
//
// A relation used to order our work list.  We work on a block earlier
// if it has a smaller jsr stack or it occurs earlier in the program
// text.
//
// Note: maybe we should redo this functionality to make blocks
// which correspond to exceptions lower priority.
bool ciTypeFlow::Block::is_simpler_than(ciTypeFlow::Block* other) {
  if (other == NULL) {
    return true;
  } else {
    int size1 = _jsrs->size();
    int size2 = other->_jsrs->size();
    if (size1 < size2) {
      return true;
    } else if (size2 < size1) {
      return false;
    } else {
#if 0
      if (size1 > 0) {
        int r1 = _jsrs->record_at(0)->return_address();
        int r2 = _jsrs->record_at(0)->return_address();
        if (r1 < r2) {
          return true;
        } else if (r2 < r1) {
          return false;
        } else {
          int e1 = _jsrs->record_at(0)->return_address();
          int e2 = _jsrs->record_at(0)->return_address();
          if (e1 < e2) {
            return true;
          } else if (e2 < e1) {
            return false;
          }
        }
      }
#endif
      return (start() <= other->start());
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::Block::set_private_copy
// Use this only to make a pre-existing public block into a private copy.
void ciTypeFlow::Block::set_private_copy(bool z) {
  assert(z || (z == is_private_copy()), "cannot make a private copy public");
  _private_copy = z;
}

#ifndef PRODUCT
// ------------------------------------------------------------------
// ciTypeFlow::Block::print_value_on
void ciTypeFlow::Block::print_value_on(outputStream* st) const {
  if (has_pre_order())  st->print("#%-2d ", pre_order());
  st->print("[%d - %d)", start(), limit());
  if (_jsrs->size() > 0) { st->print("/");  _jsrs->print_on(st); }
  if (is_private_copy())  st->print("/private_copy");
}

// ------------------------------------------------------------------
// ciTypeFlow::Block::print_on
void ciTypeFlow::Block::print_on(outputStream* st) const {
  if ((Verbose || WizardMode)) {
    outer()->method()->print_codes_on(start(), limit(), st);
  }
  st->print_cr("  ====================================================  ");
  st->print ("  ");
  print_value_on(st);
  st->cr();
  _state->print_on(st);
  if (_successors == NULL) {
    st->print_cr("  No successor information");
  } else {
    int num_successors = _successors->length();
    st->print_cr("  Successors : %d", num_successors);
    for (int i = 0; i < num_successors; i++) {
      Block* successor = _successors->at(i);
      st->print("    ");
      successor->print_value_on(st);
      st->cr();
    }
  }
  if (_exceptions == NULL) {
    st->print_cr("  No exception information");
  } else {
    int num_exceptions = _exceptions->length();
    st->print_cr("  Exceptions : %d", num_exceptions);
    for (int i = 0; i < num_exceptions; i++) {
      Block* exc_succ = _exceptions->at(i);
      ciInstanceKlass* exc_klass = _exc_klasses->at(i);
      st->print("    ");
      exc_succ->print_value_on(st);
      st->print(" -- ");
      exc_klass->name()->print_symbol_on(st);
      st->cr();
    }
  }
  if (has_trap()) {
    st->print_cr("  Traps on %d with trap index %d", trap_bci(), trap_index());
  }
  st->print_cr("  ====================================================  ");
}
#endif

// ciTypeFlow
//
// This is a pass over the bytecodes which computes the following:
//   basic block structure
//   interpreter type-states (a la the verifier)

// ------------------------------------------------------------------
// ciTypeFlow::ciTypeFlow
ciTypeFlow::ciTypeFlow(ciEnv* env, ciMethod* method, int osr_bci) {
  _env = env;
  _method = method;
  _methodBlocks = method->get_method_blocks();
  _max_locals = method->max_locals();
  _max_stack = method->max_stack();
  _code_size = method->code_size();
  _osr_bci = osr_bci;
  _failure_reason = NULL;
  assert(start_bci() >= 0 && start_bci() < code_size() , "correct osr_bci argument");

  _work_list = NULL;
  _next_pre_order = 0;

  _ciblock_count = _methodBlocks->num_blocks();
  _idx_to_blocklist = NEW_ARENA_ARRAY(arena(), GrowableArray<Block*>*, _ciblock_count);
  for (int i = 0; i < _ciblock_count; i++) {
    _idx_to_blocklist[i] = NULL;
  }
  _block_map = NULL;  // until all blocks are seen
  _jsr_count = 0;
  _jsr_records = NULL;
}

// ------------------------------------------------------------------
// ciTypeFlow::work_list_next
//
// Get the next basic block from our work list.
ciTypeFlow::Block* ciTypeFlow::work_list_next() {
  assert(!work_list_empty(), "work list must not be empty");
  Block* next_block = _work_list;
  _work_list = next_block->next();
  next_block->set_next(NULL);
  next_block->set_on_work_list(false);
  if (!next_block->has_pre_order()) {
    // Assign "pre_order" as each new block is taken from the work list.
    // This number may be used by following phases to order block visits.
    assert(!have_block_count(), "must not have mapped blocks yet")
    next_block->set_pre_order(_next_pre_order++);
  }
  return next_block;
}

// ------------------------------------------------------------------
// ciTypeFlow::add_to_work_list
//
// Add a basic block to our work list.
void ciTypeFlow::add_to_work_list(ciTypeFlow::Block* block) {
  assert(!block->is_on_work_list(), "must not already be on work list");

  if (CITraceTypeFlow) {
    tty->print(">> Adding block%s ", block->has_pre_order() ? " (again)" : "");
    block->print_value_on(tty);
    tty->print_cr(" to the work list : ");
  }

  block->set_on_work_list(true);
  if (block->is_simpler_than(_work_list)) {
    block->set_next(_work_list);
    _work_list = block;
  } else {
    Block *temp = _work_list;
    while (!block->is_simpler_than(temp->next())) {
      if (CITraceTypeFlow) {
        tty->print(".");
      }
      temp = temp->next();
    }
    block->set_next(temp->next());
    temp->set_next(block);
  }
  if (CITraceTypeFlow) {
    tty->cr();
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::block_at
//
// Return the block beginning at bci which has a JsrSet compatible
// with jsrs.
ciTypeFlow::Block* ciTypeFlow::block_at(int bci, ciTypeFlow::JsrSet* jsrs, CreateOption option) {
  // First find the right ciBlock.
  if (CITraceTypeFlow) {
    tty->print(">> Requesting block for %d/", bci);
    jsrs->print_on(tty);
    tty->cr();
  }

  ciBlock* ciblk = _methodBlocks->block_containing(bci);
  assert(ciblk->start_bci() == bci, "bad ciBlock boundaries");
  Block* block = get_block_for(ciblk->index(), jsrs, option);

  assert(block == NULL? (option == no_create): block->is_private_copy() == (option == create_private_copy), "create option consistent with result");

  if (CITraceTypeFlow) {
    if (block != NULL) {
      tty->print(">> Found block ");
      block->print_value_on(tty);
      tty->cr();
    } else {
      tty->print_cr(">> No such block.");
    }
  }

  return block;
}

// ------------------------------------------------------------------
// ciTypeFlow::make_jsr_record
//
// Make a JsrRecord for a given (entry, return) pair, if such a record
// does not already exist.
ciTypeFlow::JsrRecord* ciTypeFlow::make_jsr_record(int entry_address,
                                                   int return_address) {
  if (_jsr_records == NULL) {
    _jsr_records = new (arena()) GrowableArray<JsrRecord*>(arena(),
                                                           _jsr_count,
                                                           0,
                                                           NULL);
  }
  JsrRecord* record = NULL;
  int len = _jsr_records->length();
  for (int i = 0; i < len; i++) {
    JsrRecord* record = _jsr_records->at(i);
    if (record->entry_address() == entry_address &&
        record->return_address() == return_address) {
      return record;
    }
  }

  record = new (arena()) JsrRecord(entry_address, return_address);
  _jsr_records->append(record);
  return record;
}

// ------------------------------------------------------------------
// ciTypeFlow::flow_exceptions
//
// Merge the current state into all exceptional successors at the
// current point in the code.
void ciTypeFlow::flow_exceptions(GrowableArray<ciTypeFlow::Block*>* exceptions,
                                 GrowableArray<ciInstanceKlass*>* exc_klasses,
                                 ciTypeFlow::StateVector* state) {
  int len = exceptions->length();
  assert(exc_klasses->length() == len, "must have same length");
  for (int i = 0; i < len; i++) {
    Block* block = exceptions->at(i);
    ciInstanceKlass* exception_klass = exc_klasses->at(i);

    if (!exception_klass->is_loaded()) {
      // Do not compile any code for unloaded exception types.
      // Following compiler passes are responsible for doing this also.
      continue;
    }

    if (block->meet_exception(exception_klass, state)) {
      // Block was modified.  Add it to the work list.
      if (!block->is_on_work_list()) {
        add_to_work_list(block);
      }
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::flow_successors
//
// Merge the current state into all successors at the current point
// in the code.
void ciTypeFlow::flow_successors(GrowableArray<ciTypeFlow::Block*>* successors,
                                 ciTypeFlow::StateVector* state) {
  int len = successors->length();
  for (int i = 0; i < len; i++) {
    Block* block = successors->at(i);
    if (block->meet(state)) {
      // Block was modified.  Add it to the work list.
      if (!block->is_on_work_list()) {
        add_to_work_list(block);
      }
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::can_trap
//
// Tells if a given instruction is able to generate an exception edge.
bool ciTypeFlow::can_trap(ciBytecodeStream& str) {
  // Cf. GenerateOopMap::do_exception_edge.
  if (!Bytecodes::can_trap(str.cur_bc()))  return false;

  switch (str.cur_bc()) {
    case Bytecodes::_ldc:
    case Bytecodes::_ldc_w:
    case Bytecodes::_ldc2_w:
    case Bytecodes::_aload_0:
      // These bytecodes can trap for rewriting.  We need to assume that
      // they do not throw exceptions to make the monitor analysis work.
      return false;

    case Bytecodes::_ireturn:
    case Bytecodes::_lreturn:
    case Bytecodes::_freturn:
    case Bytecodes::_dreturn:
    case Bytecodes::_areturn:
    case Bytecodes::_return:
      // We can assume the monitor stack is empty in this analysis.
      return false;

    case Bytecodes::_monitorexit:
      // We can assume monitors are matched in this analysis.
      return false;
  }

  return true;
}


// ------------------------------------------------------------------
// ciTypeFlow::flow_block
//
// Interpret the effects of the bytecodes on the incoming state
// vector of a basic block.  Push the changed state to succeeding
// basic blocks.
void ciTypeFlow::flow_block(ciTypeFlow::Block* block,
                            ciTypeFlow::StateVector* state,
                            ciTypeFlow::JsrSet* jsrs) {
  if (CITraceTypeFlow) {
    tty->print("\n>> ANALYZING BLOCK : ");
    tty->cr();
    block->print_on(tty);
  }
  assert(block->has_pre_order(), "pre-order is assigned before 1st flow");

  int start = block->start();
  int limit = block->limit();
  int control = block->control();
  if (control != ciBlock::fall_through_bci) {
    limit = control;
  }

  // Grab the state from the current block.
  block->copy_state_into(state);

  GrowableArray<Block*>*           exceptions = block->exceptions();
  GrowableArray<ciInstanceKlass*>* exc_klasses = block->exc_klasses();
  bool has_exceptions = exceptions->length() > 0;

  ciBytecodeStream str(method());
  str.reset_to_bci(start);
  Bytecodes::Code code;
  while ((code = str.next()) != ciBytecodeStream::EOBC() &&
         str.cur_bci() < limit) {
    // Check for exceptional control flow from this point.
    if (has_exceptions && can_trap(str)) {
      flow_exceptions(exceptions, exc_klasses, state);
    }
    // Apply the effects of the current bytecode to our state.
    bool res = state->apply_one_bytecode(&str);

    // Watch for bailouts.
    if (failing())  return;

    if (res) {

      // We have encountered a trap.  Record it in this block.
      block->set_trap(state->trap_bci(), state->trap_index());

      if (CITraceTypeFlow) {
        tty->print_cr(">> Found trap");
        block->print_on(tty);
      }

      // Record (no) successors.
      block->successors(&str, state, jsrs);

      // Discontinue interpretation of this Block.
      return;
    }
  }

  GrowableArray<Block*>* successors = NULL;
  if (control != ciBlock::fall_through_bci) {
    // Check for exceptional control flow from this point.
    if (has_exceptions && can_trap(str)) {
      flow_exceptions(exceptions, exc_klasses, state);
    }

    // Fix the JsrSet to reflect effect of the bytecode.
    block->copy_jsrs_into(jsrs);
    jsrs->apply_control(this, &str, state);

    // Find successor edges based on old state and new JsrSet.
    successors = block->successors(&str, state, jsrs);

    // Apply the control changes to the state.
    state->apply_one_bytecode(&str);
  } else {
    // Fall through control
    successors = block->successors(&str, NULL, NULL);
  }

  // Pass our state to successors.
  flow_successors(successors, state);
}

// ------------------------------------------------------------------
// ciTypeFlow::flow_types
//
// Perform the type flow analysis, creating and cloning Blocks as
// necessary.
void ciTypeFlow::flow_types() {
  ResourceMark rm;
  StateVector* temp_vector = new StateVector(this);
  JsrSet* temp_set = new JsrSet(NULL, 16);

  // Create the method entry block.
  Block* block = block_at(start_bci(), temp_set);
  block->set_pre_order(_next_pre_order++);
  assert(block->is_start(), "start block must have order #0");

  // Load the initial state into it.
  const StateVector* start_state = get_start_state();
  if (failing())  return;
  block->meet(start_state);
  add_to_work_list(block);

  // Trickle away.
  while (!work_list_empty()) {
    Block* block = work_list_next();
    flow_block(block, temp_vector, temp_set);


    // NodeCountCutoff is the number of nodes at which the parser
    // will bail out.  Probably if we already have lots of BBs,
    // the parser will generate at least twice that many nodes and bail out.
    // Therefore, this is a conservatively large limit at which to
    // bail out in the pre-parse typeflow pass.
    int block_limit = MaxNodeLimit / 2;

    if (_next_pre_order >= block_limit) {
      // Too many basic blocks.  Bail out.
      //
      // This can happen when try/finally constructs are nested to depth N,
      // and there is O(2**N) cloning of jsr bodies.  See bug 4697245!
      record_failure("too many basic blocks");
      return;
    }

    // Watch for bailouts.
    if (failing())  return;
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::map_blocks
//
// Create the block map, which indexes blocks in pre_order.
void ciTypeFlow::map_blocks() {
  assert(_block_map == NULL, "single initialization");
  int pre_order_limit = _next_pre_order;
  _block_map = NEW_ARENA_ARRAY(arena(), Block*, pre_order_limit);
  assert(pre_order_limit == block_count(), "");
  int po;
  for (po = 0; po < pre_order_limit; po++) {
    debug_only(_block_map[po] = NULL);
  }
  ciMethodBlocks *mblks = _methodBlocks;
  ciBlock* current = NULL;
  int limit_bci = code_size();
  for (int bci = 0; bci < limit_bci; bci++) {
    ciBlock* ciblk = mblks->block_containing(bci);
    if (ciblk != NULL && ciblk != current) {
      current = ciblk;
      int curidx = ciblk->index();
      int block_count = (_idx_to_blocklist[curidx] == NULL) ? 0 : _idx_to_blocklist[curidx]->length();
      for (int i = 0; i < block_count; i++) {
        Block* block = _idx_to_blocklist[curidx]->at(i);
        if (!block->has_pre_order())  continue;
        int po = block->pre_order();
        assert(_block_map[po] == NULL, "unique ref to block");
        assert(0 <= po && po < pre_order_limit, "");
        _block_map[po] = block;
      }
    }
  }
  for (po = 0; po < pre_order_limit; po++) {
    assert(_block_map[po] != NULL, "must not drop any blocks");
    Block* block = _block_map[po];
    // Remove dead blocks from successor lists:
    for (int e = 0; e <= 1; e++) {
      GrowableArray<Block*>* l = e? block->exceptions(): block->successors();
      for (int i = 0; i < l->length(); i++) {
        Block* s = l->at(i);
        if (!s->has_pre_order()) {
          if (CITraceTypeFlow) {
            tty->print("Removing dead %s successor of #%d: ", (e? "exceptional":  "normal"), block->pre_order());
            s->print_value_on(tty);
            tty->cr();
          }
          l->remove(s);
          --i;
        }
      }
    }
  }
}

// ------------------------------------------------------------------
// ciTypeFlow::get_block_for
//
// Find a block with this ciBlock which has a compatible JsrSet.
// If no such block exists, create it, unless the option is no_create.
// If the option is create_private_copy, always create a fresh private copy.
ciTypeFlow::Block* ciTypeFlow::get_block_for(int ciBlockIndex, ciTypeFlow::JsrSet* jsrs, CreateOption option) {
  Arena* a = arena();
  GrowableArray<Block*>* blocks = _idx_to_blocklist[ciBlockIndex];
  if (blocks == NULL) {
    // Query only?
    if (option == no_create)  return NULL;

    // Allocate the growable array.
    blocks = new (a) GrowableArray<Block*>(a, 4, 0, NULL);
    _idx_to_blocklist[ciBlockIndex] = blocks;
  }

  if (option != create_private_copy) {
    int len = blocks->length();
    for (int i = 0; i < len; i++) {
      Block* block = blocks->at(i);
      if (!block->is_private_copy() && block->is_compatible_with(jsrs)) {
        return block;
      }
    }
  }

  // Query only?
  if (option == no_create)  return NULL;

  // We did not find a compatible block.  Create one.
  Block* new_block = new (a) Block(this, _methodBlocks->block(ciBlockIndex), jsrs);
  if (option == create_private_copy)  new_block->set_private_copy(true);
  blocks->append(new_block);
  return new_block;
}

// ------------------------------------------------------------------
// ciTypeFlow::private_copy_count
//
int ciTypeFlow::private_copy_count(int ciBlockIndex, ciTypeFlow::JsrSet* jsrs) const {
  GrowableArray<Block*>* blocks = _idx_to_blocklist[ciBlockIndex];

  if (blocks == NULL) {
    return 0;
  }

  int count = 0;
  int len = blocks->length();
  for (int i = 0; i < len; i++) {
    Block* block = blocks->at(i);
    if (block->is_private_copy() && block->is_compatible_with(jsrs)) {
      count++;
    }
  }

  return count;
}

// ------------------------------------------------------------------
// ciTypeFlow::do_flow
//
// Perform type inference flow analysis.
void ciTypeFlow::do_flow() {
  if (CITraceTypeFlow) {
    tty->print_cr("\nPerforming flow analysis on method");
    method()->print();
    if (is_osr_flow())  tty->print(" at OSR bci %d", start_bci());
    tty->cr();
    method()->print_codes();
  }
  if (CITraceTypeFlow) {
    tty->print_cr("Initial CI Blocks");
    print_on(tty);
  }
  flow_types();
  // Watch for bailouts.
  if (failing()) {
    return;
  }
  if (CIPrintTypeFlow || CITraceTypeFlow) {
    print_on(tty);
  }
  map_blocks();
}

// ------------------------------------------------------------------
// ciTypeFlow::record_failure()
// The ciTypeFlow object keeps track of failure reasons separately from the ciEnv.
// This is required because there is not a 1-1 relation between the ciEnv and
// the TypeFlow passes within a compilation task.  For example, if the compiler
// is considering inlining a method, it will request a TypeFlow.  If that fails,
// the compilation as a whole may continue without the inlining.  Some TypeFlow
// requests are not optional; if they fail the requestor is responsible for
// copying the failure reason up to the ciEnv.  (See Parse::Parse.)
void ciTypeFlow::record_failure(const char* reason) {
  if (env()->log() != NULL) {
    env()->log()->elem("failure reason='%s' phase='typeflow'", reason);
  }
  if (_failure_reason == NULL) {
    // Record the first failure reason.
    _failure_reason = reason;
  }
}

#ifndef PRODUCT
// ------------------------------------------------------------------
// ciTypeFlow::print_on
void ciTypeFlow::print_on(outputStream* st) const {
  // Walk through CI blocks
  st->print_cr("********************************************************");
  st->print   ("TypeFlow for ");
  method()->name()->print_symbol_on(st);
  int limit_bci = code_size();
  st->print_cr("  %d bytes", limit_bci);
  ciMethodBlocks  *mblks = _methodBlocks;
  ciBlock* current = NULL;
  for (int bci = 0; bci < limit_bci; bci++) {
    ciBlock* blk = mblks->block_containing(bci);
    if (blk != NULL && blk != current) {
      current = blk;
      current->print_on(st);

      GrowableArray<Block*>* blocks = _idx_to_blocklist[blk->index()];
      int num_blocks = (blocks == NULL) ? 0 : blocks->length();

      if (num_blocks == 0) {
        st->print_cr("  No Blocks");
      } else {
        for (int i = 0; i < num_blocks; i++) {
          Block* block = blocks->at(i);
          block->print_on(st);
        }
      }
      st->print_cr("--------------------------------------------------------");
      st->cr();
    }
  }
  st->print_cr("********************************************************");
  st->cr();
}
#endif

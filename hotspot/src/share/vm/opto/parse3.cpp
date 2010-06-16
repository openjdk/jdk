/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "incls/_parse3.cpp.incl"

//=============================================================================
// Helper methods for _get* and _put* bytecodes
//=============================================================================
bool Parse::static_field_ok_in_clinit(ciField *field, ciMethod *method) {
  // Could be the field_holder's <clinit> method, or <clinit> for a subklass.
  // Better to check now than to Deoptimize as soon as we execute
  assert( field->is_static(), "Only check if field is static");
  // is_being_initialized() is too generous.  It allows access to statics
  // by threads that are not running the <clinit> before the <clinit> finishes.
  // return field->holder()->is_being_initialized();

  // The following restriction is correct but conservative.
  // It is also desirable to allow compilation of methods called from <clinit>
  // but this generated code will need to be made safe for execution by
  // other threads, or the transition from interpreted to compiled code would
  // need to be guarded.
  ciInstanceKlass *field_holder = field->holder();

  bool access_OK = false;
  if (method->holder()->is_subclass_of(field_holder)) {
    if (method->is_static()) {
      if (method->name() == ciSymbol::class_initializer_name()) {
        // OK to access static fields inside initializer
        access_OK = true;
      }
    } else {
      if (method->name() == ciSymbol::object_initializer_name()) {
        // It's also OK to access static fields inside a constructor,
        // because any thread calling the constructor must first have
        // synchronized on the class by executing a '_new' bytecode.
        access_OK = true;
      }
    }
  }

  return access_OK;

}


void Parse::do_field_access(bool is_get, bool is_field) {
  bool will_link;
  ciField* field = iter().get_field(will_link);
  assert(will_link, "getfield: typeflow responsibility");

  ciInstanceKlass* field_holder = field->holder();

  if (is_field == field->is_static()) {
    // Interpreter will throw java_lang_IncompatibleClassChangeError
    // Check this before allowing <clinit> methods to access static fields
    uncommon_trap(Deoptimization::Reason_unhandled,
                  Deoptimization::Action_none);
    return;
  }

  if (!is_field && !field_holder->is_initialized()) {
    if (!static_field_ok_in_clinit(field, method())) {
      uncommon_trap(Deoptimization::Reason_uninitialized,
                    Deoptimization::Action_reinterpret,
                    NULL, "!static_field_ok_in_clinit");
      return;
    }
  }

  assert(field->will_link(method()->holder(), bc()), "getfield: typeflow responsibility");

  // Note:  We do not check for an unloaded field type here any more.

  // Generate code for the object pointer.
  Node* obj;
  if (is_field) {
    int obj_depth = is_get ? 0 : field->type()->size();
    obj = do_null_check(peek(obj_depth), T_OBJECT);
    // Compile-time detect of null-exception?
    if (stopped())  return;

    const TypeInstPtr *tjp = TypeInstPtr::make(TypePtr::NotNull, iter().get_declared_field_holder());
    assert(_gvn.type(obj)->higher_equal(tjp), "cast_up is no longer needed");

    if (is_get) {
      --_sp;  // pop receiver before getting
      do_get_xxx(tjp, obj, field, is_field);
    } else {
      do_put_xxx(tjp, obj, field, is_field);
      --_sp;  // pop receiver after putting
    }
  } else {
    const TypeKlassPtr* tkp = TypeKlassPtr::make(field_holder);
    obj = _gvn.makecon(tkp);
    if (is_get) {
      do_get_xxx(tkp, obj, field, is_field);
    } else {
      do_put_xxx(tkp, obj, field, is_field);
    }
  }
}


void Parse::do_get_xxx(const TypePtr* obj_type, Node* obj, ciField* field, bool is_field) {
  // Does this field have a constant value?  If so, just push the value.
  if (field->is_constant()) {
    if (field->is_static()) {
      // final static field
      if (push_constant(field->constant_value()))
        return;
    }
    else {
      // final non-static field of a trusted class ({java,sun}.dyn
      // classes).
      if (obj->is_Con()) {
        const TypeOopPtr* oop_ptr = obj->bottom_type()->isa_oopptr();
        ciObject* constant_oop = oop_ptr->const_oop();
        ciConstant constant = field->constant_value_of(constant_oop);

        if (push_constant(constant, true))
          return;
      }
    }
  }

  ciType* field_klass = field->type();
  bool is_vol = field->is_volatile();

  // Compute address and memory type.
  int offset = field->offset_in_bytes();
  const TypePtr* adr_type = C->alias_type(field)->adr_type();
  Node *adr = basic_plus_adr(obj, obj, offset);
  BasicType bt = field->layout_type();

  // Build the resultant type of the load
  const Type *type;

  bool must_assert_null = false;

  if( bt == T_OBJECT ) {
    if (!field->type()->is_loaded()) {
      type = TypeInstPtr::BOTTOM;
      must_assert_null = true;
    } else if (field->is_constant() && field->is_static()) {
      // This can happen if the constant oop is non-perm.
      ciObject* con = field->constant_value().as_object();
      // Do not "join" in the previous type; it doesn't add value,
      // and may yield a vacuous result if the field is of interface type.
      type = TypeOopPtr::make_from_constant(con)->isa_oopptr();
      assert(type != NULL, "field singleton type must be consistent");
    } else {
      type = TypeOopPtr::make_from_klass(field_klass->as_klass());
    }
  } else {
    type = Type::get_const_basic_type(bt);
  }
  // Build the load.
  Node* ld = make_load(NULL, adr, type, bt, adr_type, is_vol);

  // Adjust Java stack
  if (type2size[bt] == 1)
    push(ld);
  else
    push_pair(ld);

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
#ifndef PRODUCT
    if (PrintOpto && (Verbose || WizardMode)) {
      method()->print_name(); tty->print_cr(" asserting nullness of field at bci: %d", bci());
    }
#endif
    if (C->log() != NULL) {
      C->log()->elem("assert_null reason='field' klass='%d'",
                     C->log()->identify(field->type()));
    }
    // If there is going to be a trap, put it at the next bytecode:
    set_bci(iter().next_bci());
    do_null_assert(peek(), T_OBJECT);
    set_bci(iter().cur_bci()); // put it back
  }

  // If reference is volatile, prevent following memory ops from
  // floating up past the volatile read.  Also prevents commoning
  // another volatile read.
  if (field->is_volatile()) {
    // Memory barrier includes bogus read of value to force load BEFORE membar
    insert_mem_bar(Op_MemBarAcquire, ld);
  }
}

void Parse::do_put_xxx(const TypePtr* obj_type, Node* obj, ciField* field, bool is_field) {
  bool is_vol = field->is_volatile();
  // If reference is volatile, prevent following memory ops from
  // floating down past the volatile write.  Also prevents commoning
  // another volatile read.
  if (is_vol)  insert_mem_bar(Op_MemBarRelease);

  // Compute address and memory type.
  int offset = field->offset_in_bytes();
  const TypePtr* adr_type = C->alias_type(field)->adr_type();
  Node* adr = basic_plus_adr(obj, obj, offset);
  BasicType bt = field->layout_type();
  // Value to be stored
  Node* val = type2size[bt] == 1 ? pop() : pop_pair();
  // Round doubles before storing
  if (bt == T_DOUBLE)  val = dstore_rounding(val);

  // Store the value.
  Node* store;
  if (bt == T_OBJECT) {
    const TypeOopPtr* field_type;
    if (!field->type()->is_loaded()) {
      field_type = TypeInstPtr::BOTTOM;
    } else {
      field_type = TypeOopPtr::make_from_klass(field->type()->as_klass());
    }
    store = store_oop_to_object( control(), obj, adr, adr_type, val, field_type, bt);
  } else {
    store = store_to_memory( control(), adr, val, bt, adr_type, is_vol );
  }

  // If reference is volatile, prevent following volatiles ops from
  // floating up before the volatile write.
  if (is_vol) {
    // First place the specific membar for THIS volatile index. This first
    // membar is dependent on the store, keeping any other membars generated
    // below from floating up past the store.
    int adr_idx = C->get_alias_index(adr_type);
    insert_mem_bar_volatile(Op_MemBarVolatile, adr_idx, store);

    // Now place a membar for AliasIdxBot for the unknown yet-to-be-parsed
    // volatile alias indices. Skip this if the membar is redundant.
    if (adr_idx != Compile::AliasIdxBot) {
      insert_mem_bar_volatile(Op_MemBarVolatile, Compile::AliasIdxBot, store);
    }

    // Finally, place alias-index-specific membars for each volatile index
    // that isn't the adr_idx membar. Typically there's only 1 or 2.
    for( int i = Compile::AliasIdxRaw; i < C->num_alias_types(); i++ ) {
      if (i != adr_idx && C->alias_type(i)->is_volatile()) {
        insert_mem_bar_volatile(Op_MemBarVolatile, i, store);
      }
    }
  }

  // If the field is final, the rules of Java say we are in <init> or <clinit>.
  // Note the presence of writes to final non-static fields, so that we
  // can insert a memory barrier later on to keep the writes from floating
  // out of the constructor.
  if (is_field && field->is_final()) {
    set_wrote_final(true);
  }
}


bool Parse::push_constant(ciConstant constant, bool require_constant) {
  switch (constant.basic_type()) {
  case T_BOOLEAN:  push( intcon(constant.as_boolean()) ); break;
  case T_INT:      push( intcon(constant.as_int())     ); break;
  case T_CHAR:     push( intcon(constant.as_char())    ); break;
  case T_BYTE:     push( intcon(constant.as_byte())    ); break;
  case T_SHORT:    push( intcon(constant.as_short())   ); break;
  case T_FLOAT:    push( makecon(TypeF::make(constant.as_float())) );  break;
  case T_DOUBLE:   push_pair( makecon(TypeD::make(constant.as_double())) );  break;
  case T_LONG:     push_pair( longcon(constant.as_long()) ); break;
  case T_ARRAY:
  case T_OBJECT: {
    // cases:
    //   can_be_constant    = (oop not scavengable || ScavengeRootsInCode != 0)
    //   should_be_constant = (oop not scavengable || ScavengeRootsInCode >= 2)
    // An oop is not scavengable if it is in the perm gen.
    ciObject* oop_constant = constant.as_object();
    if (oop_constant->is_null_object()) {
      push( zerocon(T_OBJECT) );
      break;
    } else if (require_constant || oop_constant->should_be_constant()) {
      push( makecon(TypeOopPtr::make_from_constant(oop_constant, require_constant)) );
      break;
    } else {
      // we cannot inline the oop, but we can use it later to narrow a type
      return false;
    }
  }
  case T_ILLEGAL: {
    // Invalid ciConstant returned due to OutOfMemoryError in the CI
    assert(C->env()->failing(), "otherwise should not see this");
    // These always occur because of object types; we are going to
    // bail out anyway, so make the stack depths match up
    push( zerocon(T_OBJECT) );
    return false;
  }
  default:
    ShouldNotReachHere();
    return false;
  }

  // success
  return true;
}



//=============================================================================
void Parse::do_anewarray() {
  bool will_link;
  ciKlass* klass = iter().get_klass(will_link);

  // Uncommon Trap when class that array contains is not loaded
  // we need the loaded class for the rest of graph; do not
  // initialize the container class (see Java spec)!!!
  assert(will_link, "anewarray: typeflow responsibility");

  ciObjArrayKlass* array_klass = ciObjArrayKlass::make(klass);
  // Check that array_klass object is loaded
  if (!array_klass->is_loaded()) {
    // Generate uncommon_trap for unloaded array_class
    uncommon_trap(Deoptimization::Reason_unloaded,
                  Deoptimization::Action_reinterpret,
                  array_klass);
    return;
  }

  kill_dead_locals();

  const TypeKlassPtr* array_klass_type = TypeKlassPtr::make(array_klass);
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
  assert(length != NULL, "");
  Node* array = new_array(makecon(TypeKlassPtr::make(array_klass)), length, nargs);
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
      store_oop_to_array(control(), array, eaddr, adr_type, elem, elemtype, T_OBJECT);
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

  enum { MAX_DIMENSION = 5 };
  if (ndimensions > MAX_DIMENSION || ndimensions <= 0) {
    uncommon_trap(Deoptimization::Reason_unhandled,
                  Deoptimization::Action_none);
    return;
  }

  kill_dead_locals();

  // get the lengths from the stack (first dimension is on top)
  Node* length[MAX_DIMENSION+1];
  length[ndimensions] = NULL;  // terminating null for make_runtime_call
  int j;
  for (j = ndimensions-1; j >= 0 ; j--) length[j] = pop();

  // The original expression was of this form: new T[length0][length1]...
  // It is often the case that the lengths are small (except the last).
  // If that happens, use the fast 1-d creator a constant number of times.
  const jint expand_limit = MIN2((juint)MultiArrayExpandLimit, (juint)100);
  jint expand_count = 1;        // count of allocations in the expansion
  jint expand_fanout = 1;       // running total fanout
  for (j = 0; j < ndimensions-1; j++) {
    jint dim_con = find_int_con(length[j], -1);
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
    Node* obj = NULL;
    // Set the original stack and the reexecute bit for the interpreter
    // to reexecute the multianewarray bytecode if deoptimization happens.
    // Do it unconditionally even for one dimension multianewarray.
    // Note: the reexecute bit will be set in GraphKit::add_safepoint_edges()
    // when AllocateArray node for newarray is created.
    { PreserveReexecuteState preexecs(this);
      _sp += ndimensions;
      // Pass 0 as nargs since uncommon trap code does not need to restore stack.
      obj = expand_multianewarray(array_klass, &length[0], ndimensions, 0);
    } //original reexecute and sp are set back here
    push(obj);
    return;
  }

  address fun = NULL;
  switch (ndimensions) {
  //case 1: Actually, there is no case 1.  It's handled by new_array.
  case 2: fun = OptoRuntime::multianewarray2_Java(); break;
  case 3: fun = OptoRuntime::multianewarray3_Java(); break;
  case 4: fun = OptoRuntime::multianewarray4_Java(); break;
  case 5: fun = OptoRuntime::multianewarray5_Java(); break;
  default: ShouldNotReachHere();
  };

  Node* c = make_runtime_call(RC_NO_LEAF | RC_NO_IO,
                              OptoRuntime::multianewarray_Type(ndimensions),
                              fun, NULL, TypeRawPtr::BOTTOM,
                              makecon(TypeKlassPtr::make(array_klass)),
                              length[0], length[1], length[2],
                              length[3], length[4]);
  Node* res = _gvn.transform(new (C, 1) ProjNode(c, TypeFunc::Parms));

  const Type* type = TypeOopPtr::make_from_klass_raw(array_klass);

  // Improve the type:  We know it's not null, exact, and of a given length.
  type = type->is_ptr()->cast_to_ptr_type(TypePtr::NotNull);
  type = type->is_aryptr()->cast_to_exactness(true);

  const TypeInt* ltype = _gvn.find_int_type(length[0]);
  if (ltype != NULL)
    type = type->is_aryptr()->cast_to_size(ltype);

  // We cannot sharpen the nested sub-arrays, since the top level is mutable.

  Node* cast = _gvn.transform( new (C, 2) CheckCastPPNode(control(), res, type) );
  push(cast);

  // Possible improvements:
  // - Make a fast path for small multi-arrays.  (W/ implicit init. loops.)
  // - Issue CastII against length[*] values, to TypeInt::POS.
}

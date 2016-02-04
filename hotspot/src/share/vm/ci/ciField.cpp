/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciField.hpp"
#include "ci/ciInstanceKlass.hpp"
#include "ci/ciUtilities.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.hpp"

// ciField
//
// This class represents the result of a field lookup in the VM.
// The lookup may not succeed, in which case the information in
// the ciField will be incomplete.

// The ciObjectFactory cannot create circular data structures in one query.
// To avoid vicious circularities, we initialize ciField::_type to NULL
// for reference types and derive it lazily from the ciField::_signature.
// Primitive types are eagerly initialized, and basic layout queries
// can succeed without initialization, using only the BasicType of the field.

// Notes on bootstrapping and shared CI objects:  A field is shared if and
// only if it is (a) non-static and (b) declared by a shared instance klass.
// This allows non-static field lists to be cached on shared types.
// Because the _type field is lazily initialized, however, there is a
// special restriction that a shared field cannot cache an unshared type.
// This puts a small performance penalty on shared fields with unshared
// types, such as StackTraceElement[] Throwable.stackTrace.
// (Throwable is shared because ClassCastException is shared, but
// StackTraceElement is not presently shared.)

// It is not a vicious circularity for a ciField to recursively create
// the ciSymbols necessary to represent its name and signature.
// Therefore, these items are created eagerly, and the name and signature
// of a shared field are themselves shared symbols.  This somewhat
// pollutes the set of shared CI objects:  It grows from 50 to 93 items,
// with all of the additional 43 being uninteresting shared ciSymbols.
// This adds at most one step to the binary search, an amount which
// decreases for complex compilation tasks.

// ------------------------------------------------------------------
// ciField::ciField
ciField::ciField(ciInstanceKlass* klass, int index): _known_to_link_with_put(NULL), _known_to_link_with_get(NULL) {
  ASSERT_IN_VM;
  CompilerThread *thread = CompilerThread::current();

  assert(ciObjectFactory::is_initialized(), "not a shared field");

  assert(klass->get_instanceKlass()->is_linked(), "must be linked before using its constant-pool");

  constantPoolHandle cpool(thread, klass->get_instanceKlass()->constants());

  // Get the field's name, signature, and type.
  Symbol* name  = cpool->name_ref_at(index);
  _name = ciEnv::current(thread)->get_symbol(name);

  int nt_index = cpool->name_and_type_ref_index_at(index);
  int sig_index = cpool->signature_ref_index_at(nt_index);
  Symbol* signature = cpool->symbol_at(sig_index);
  _signature = ciEnv::current(thread)->get_symbol(signature);

  BasicType field_type = FieldType::basic_type(signature);

  // If the field is a pointer type, get the klass of the
  // field.
  if (field_type == T_OBJECT || field_type == T_ARRAY) {
    bool ignore;
    // This is not really a class reference; the index always refers to the
    // field's type signature, as a symbol.  Linkage checks do not apply.
    _type = ciEnv::current(thread)->get_klass_by_index(cpool, sig_index, ignore, klass);
  } else {
    _type = ciType::make(field_type);
  }

  _name = (ciSymbol*)ciEnv::current(thread)->get_symbol(name);

  // Get the field's declared holder.
  //
  // Note: we actually create a ciInstanceKlass for this klass,
  // even though we may not need to.
  int holder_index = cpool->klass_ref_index_at(index);
  bool holder_is_accessible;

  ciKlass* generic_declared_holder = ciEnv::current(thread)->get_klass_by_index(cpool, holder_index,
                                                                                holder_is_accessible,
                                                                                klass);

  if (generic_declared_holder->is_array_klass()) {
    // If the declared holder of the field is an array class, assume that
    // the canonical holder of that field is java.lang.Object. Arrays
    // do not have fields; java.lang.Object is the only supertype of an
    // array type that can declare fields and is therefore the canonical
    // holder of the array type.
    //
    // Furthermore, the compilers assume that java.lang.Object does not
    // have any fields. Therefore, the field is not looked up. Instead,
    // the method returns partial information that will trigger special
    // handling in ciField::will_link and will result in a
    // java.lang.NoSuchFieldError exception being thrown by the compiled
    // code (the expected behavior in this case).
    _holder = ciEnv::current(thread)->Object_klass();
    _offset = -1;
    _is_constant = false;
    return;
  }

  ciInstanceKlass* declared_holder = generic_declared_holder->as_instance_klass();

  // The declared holder of this field may not have been loaded.
  // Bail out with partial field information.
  if (!holder_is_accessible) {
    // _type has already been set.
    // The default values for _flags and _constant_value will suffice.
    // We need values for _holder, _offset,  and _is_constant,
    _holder = declared_holder;
    _offset = -1;
    _is_constant = false;
    return;
  }

  InstanceKlass* loaded_decl_holder = declared_holder->get_instanceKlass();

  // Perform the field lookup.
  fieldDescriptor field_desc;
  Klass* canonical_holder =
    loaded_decl_holder->find_field(name, signature, &field_desc);
  if (canonical_holder == NULL) {
    // Field lookup failed.  Will be detected by will_link.
    _holder = declared_holder;
    _offset = -1;
    _is_constant = false;
    return;
  }

  // Access check based on declared_holder. canonical_holder should not be used
  // to check access because it can erroneously succeed. If this check fails,
  // propagate the declared holder to will_link() which in turn will bail out
  // compilation for this field access.
  if (!Reflection::verify_field_access(klass->get_Klass(), declared_holder->get_Klass(), canonical_holder, field_desc.access_flags(), true)) {
    _holder = declared_holder;
    _offset = -1;
    _is_constant = false;
    return;
  }

  assert(canonical_holder == field_desc.field_holder(), "just checking");
  initialize_from(&field_desc);
}

ciField::ciField(fieldDescriptor *fd): _known_to_link_with_put(NULL), _known_to_link_with_get(NULL) {
  ASSERT_IN_VM;

  // Get the field's name, signature, and type.
  ciEnv* env = CURRENT_ENV;
  _name = env->get_symbol(fd->name());
  _signature = env->get_symbol(fd->signature());

  BasicType field_type = fd->field_type();

  // If the field is a pointer type, get the klass of the
  // field.
  if (field_type == T_OBJECT || field_type == T_ARRAY) {
    _type = NULL;  // must call compute_type on first access
  } else {
    _type = ciType::make(field_type);
  }

  initialize_from(fd);

  // Either (a) it is marked shared, or else (b) we are done bootstrapping.
  assert(is_shared() || ciObjectFactory::is_initialized(),
         "bootstrap classes must not create & cache unshared fields");
}

static bool trust_final_non_static_fields(ciInstanceKlass* holder) {
  if (holder == NULL)
    return false;
  if (holder->name() == ciSymbol::java_lang_System())
    // Never trust strangely unstable finals:  System.out, etc.
    return false;
  // Even if general trusting is disabled, trust system-built closures in these packages.
  if (holder->is_in_package("java/lang/invoke") || holder->is_in_package("sun/invoke"))
    return true;
  // Trust VM anonymous classes. They are private API (sun.misc.Unsafe) and can't be serialized,
  // so there is no hacking of finals going on with them.
  if (holder->is_anonymous())
    return true;
  // Trust Atomic*FieldUpdaters: they are very important for performance, and make up one
  // more reason not to use Unsafe, if their final fields are trusted. See more in JDK-8140483.
  if (holder->name() == ciSymbol::java_util_concurrent_atomic_AtomicIntegerFieldUpdater_Impl() ||
      holder->name() == ciSymbol::java_util_concurrent_atomic_AtomicLongFieldUpdater_CASUpdater() ||
      holder->name() == ciSymbol::java_util_concurrent_atomic_AtomicLongFieldUpdater_LockedUpdater() ||
      holder->name() == ciSymbol::java_util_concurrent_atomic_AtomicReferenceFieldUpdater_Impl()) {
    return true;
  }
  return TrustFinalNonStaticFields;
}

void ciField::initialize_from(fieldDescriptor* fd) {
  // Get the flags, offset, and canonical holder of the field.
  _flags = ciFlags(fd->access_flags());
  _offset = fd->offset();
  _holder = CURRENT_ENV->get_instance_klass(fd->field_holder());

  // Check to see if the field is constant.
  bool is_final = this->is_final();
  bool is_stable = FoldStableValues && this->is_stable();
  if (_holder->is_initialized() && (is_final || is_stable)) {
    if (!this->is_static()) {
      // A field can be constant if it's a final static field or if
      // it's a final non-static field of a trusted class (classes in
      // java.lang.invoke and sun.invoke packages and subpackages).
      if (is_stable || trust_final_non_static_fields(_holder)) {
        _is_constant = true;
        return;
      }
      _is_constant = false;
      return;
    }

    // This field just may be constant.  The only case where it will
    // not be constant is when the field is a *special* static&final field
    // whose value may change.  The three examples are java.lang.System.in,
    // java.lang.System.out, and java.lang.System.err.

    KlassHandle k = _holder->get_Klass();
    assert( SystemDictionary::System_klass() != NULL, "Check once per vm");
    if( k() == SystemDictionary::System_klass() ) {
      // Check offsets for case 2: System.in, System.out, or System.err
      if( _offset == java_lang_System::in_offset_in_bytes()  ||
          _offset == java_lang_System::out_offset_in_bytes() ||
          _offset == java_lang_System::err_offset_in_bytes() ) {
        _is_constant = false;
        return;
      }
    }

    Handle mirror = k->java_mirror();

    switch(type()->basic_type()) {
    case T_BYTE:
      _constant_value = ciConstant(type()->basic_type(), mirror->byte_field(_offset));
      break;
    case T_CHAR:
      _constant_value = ciConstant(type()->basic_type(), mirror->char_field(_offset));
      break;
    case T_SHORT:
      _constant_value = ciConstant(type()->basic_type(), mirror->short_field(_offset));
      break;
    case T_BOOLEAN:
      _constant_value = ciConstant(type()->basic_type(), mirror->bool_field(_offset));
      break;
    case T_INT:
      _constant_value = ciConstant(type()->basic_type(), mirror->int_field(_offset));
      break;
    case T_FLOAT:
      _constant_value = ciConstant(mirror->float_field(_offset));
      break;
    case T_DOUBLE:
      _constant_value = ciConstant(mirror->double_field(_offset));
      break;
    case T_LONG:
      _constant_value = ciConstant(mirror->long_field(_offset));
      break;
    case T_OBJECT:
    case T_ARRAY:
      {
        oop o = mirror->obj_field(_offset);

        // A field will be "constant" if it is known always to be
        // a non-null reference to an instance of a particular class,
        // or to a particular array.  This can happen even if the instance
        // or array is not perm.  In such a case, an "unloaded" ciArray
        // or ciInstance is created.  The compiler may be able to use
        // information about the object's class (which is exact) or length.

        if (o == NULL) {
          _constant_value = ciConstant(type()->basic_type(), ciNullObject::make());
        } else {
          _constant_value = ciConstant(type()->basic_type(), CURRENT_ENV->get_object(o));
          assert(_constant_value.as_object() == CURRENT_ENV->get_object(o), "check interning");
        }
      }
    }
    if (is_stable && _constant_value.is_null_or_zero()) {
      // It is not a constant after all; treat it as uninitialized.
      _is_constant = false;
    } else {
      _is_constant = true;
    }
  } else {
    _is_constant = false;
  }
}

// ------------------------------------------------------------------
// ciField::compute_type
//
// Lazily compute the type, if it is an instance klass.
ciType* ciField::compute_type() {
  GUARDED_VM_ENTRY(return compute_type_impl();)
}

ciType* ciField::compute_type_impl() {
  ciKlass* type = CURRENT_ENV->get_klass_by_name_impl(_holder, constantPoolHandle(), _signature, false);
  if (!type->is_primitive_type() && is_shared()) {
    // We must not cache a pointer to an unshared type, in a shared field.
    bool type_is_also_shared = false;
    if (type->is_type_array_klass()) {
      type_is_also_shared = true;  // int[] etc. are explicitly bootstrapped
    } else if (type->is_instance_klass()) {
      type_is_also_shared = type->as_instance_klass()->is_shared();
    } else {
      // Currently there is no 'shared' query for array types.
      type_is_also_shared = !ciObjectFactory::is_initialized();
    }
    if (!type_is_also_shared)
      return type;              // Bummer.
  }
  _type = type;
  return type;
}


// ------------------------------------------------------------------
// ciField::will_link
//
// Can a specific access to this field be made without causing
// link errors?
bool ciField::will_link(ciInstanceKlass* accessing_klass,
                        Bytecodes::Code bc) {
  VM_ENTRY_MARK;
  assert(bc == Bytecodes::_getstatic || bc == Bytecodes::_putstatic ||
         bc == Bytecodes::_getfield  || bc == Bytecodes::_putfield,
         "unexpected bytecode");

  if (_offset == -1) {
    // at creation we couldn't link to our holder so we need to
    // maintain that stance, otherwise there's no safe way to use this
    // ciField.
    return false;
  }

  // Check for static/nonstatic mismatch
  bool is_static = (bc == Bytecodes::_getstatic || bc == Bytecodes::_putstatic);
  if (is_static != this->is_static()) {
    return false;
  }

  // Get and put can have different accessibility rules
  bool is_put    = (bc == Bytecodes::_putfield  || bc == Bytecodes::_putstatic);
  if (is_put) {
    if (_known_to_link_with_put == accessing_klass) {
      return true;
    }
  } else {
    if (_known_to_link_with_get == accessing_klass) {
      return true;
    }
  }

  LinkInfo link_info(_holder->get_instanceKlass(),
                     _name->get_symbol(), _signature->get_symbol(),
                     accessing_klass->get_Klass());
  fieldDescriptor result;
  LinkResolver::resolve_field(result, link_info, bc, false, KILL_COMPILE_ON_FATAL_(false));

  // update the hit-cache, unless there is a problem with memory scoping:
  if (accessing_klass->is_shared() || !is_shared()) {
    if (is_put) {
      _known_to_link_with_put = accessing_klass;
    } else {
      _known_to_link_with_get = accessing_klass;
    }
  }

  return true;
}

// ------------------------------------------------------------------
// ciField::print
void ciField::print() {
  tty->print("<ciField name=");
  _holder->print_name();
  tty->print(".");
  _name->print_symbol();
  tty->print(" signature=");
  _signature->print_symbol();
  tty->print(" offset=%d type=", _offset);
  if (_type != NULL)
    _type->print_name();
  else
    tty->print("(reference)");
  tty->print(" flags=%04x", flags().as_int());
  tty->print(" is_constant=%s", bool_to_str(_is_constant));
  if (_is_constant && is_static()) {
    tty->print(" constant_value=");
    _constant_value.print();
  }
  tty->print(">");
}

// ------------------------------------------------------------------
// ciField::print_name_on
//
// Print the name of this field
void ciField::print_name_on(outputStream* st) {
  name()->print_symbol_on(st);
}

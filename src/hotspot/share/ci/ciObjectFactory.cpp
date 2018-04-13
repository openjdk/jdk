/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciCallSite.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciInstanceKlass.hpp"
#include "ci/ciMemberName.hpp"
#include "ci/ciMethod.hpp"
#include "ci/ciMethodData.hpp"
#include "ci/ciMethodHandle.hpp"
#include "ci/ciMethodType.hpp"
#include "ci/ciNullObject.hpp"
#include "ci/ciObjArray.hpp"
#include "ci/ciObjArrayKlass.hpp"
#include "ci/ciObject.hpp"
#include "ci/ciObjectFactory.hpp"
#include "ci/ciSymbol.hpp"
#include "ci/ciTypeArray.hpp"
#include "ci/ciTypeArrayKlass.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldType.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/macros.hpp"

// ciObjectFactory
//
// This class handles requests for the creation of new instances
// of ciObject and its subclasses.  It contains a caching mechanism
// which ensures that for each oop, at most one ciObject is created.
// This invariant allows more efficient implementation of ciObject.
//
// Implementation note: the oop->ciObject mapping is represented as
// a table stored in an array.  Even though objects are moved
// by the garbage collector, the compactor preserves their relative
// order; address comparison of oops (in perm space) is safe so long
// as we prohibit GC during our comparisons.  We currently use binary
// search to find the oop in the table, and inserting a new oop
// into the table may be costly.  If this cost ends up being
// problematic the underlying data structure can be switched to some
// sort of balanced binary tree.

GrowableArray<ciMetadata*>* ciObjectFactory::_shared_ci_metadata = NULL;
ciSymbol*                 ciObjectFactory::_shared_ci_symbols[vmSymbols::SID_LIMIT];
int                       ciObjectFactory::_shared_ident_limit = 0;
volatile bool             ciObjectFactory::_initialized = false;


// ------------------------------------------------------------------
// ciObjectFactory::ciObjectFactory
ciObjectFactory::ciObjectFactory(Arena* arena,
                                 int expected_size) {

  for (int i = 0; i < NON_PERM_BUCKETS; i++) {
    _non_perm_bucket[i] = NULL;
  }
  _non_perm_count = 0;

  _next_ident = _shared_ident_limit;
  _arena = arena;
  _ci_metadata = new (arena) GrowableArray<ciMetadata*>(arena, expected_size, 0, NULL);

  // If the shared ci objects exist append them to this factory's objects

  if (_shared_ci_metadata != NULL) {
    _ci_metadata->appendAll(_shared_ci_metadata);
  }

  _unloaded_methods = new (arena) GrowableArray<ciMethod*>(arena, 4, 0, NULL);
  _unloaded_klasses = new (arena) GrowableArray<ciKlass*>(arena, 8, 0, NULL);
  _unloaded_instances = new (arena) GrowableArray<ciInstance*>(arena, 4, 0, NULL);
  _return_addresses =
    new (arena) GrowableArray<ciReturnAddress*>(arena, 8, 0, NULL);

  _symbols = new (arena) GrowableArray<ciSymbol*>(arena, 100, 0, NULL);
}

// ------------------------------------------------------------------
// ciObjectFactory::ciObjectFactory
void ciObjectFactory::initialize() {
  ASSERT_IN_VM;
  JavaThread* thread = JavaThread::current();
  HandleMark  handle_mark(thread);

  // This Arena is long lived and exists in the resource mark of the
  // compiler thread that initializes the initial ciObjectFactory which
  // creates the shared ciObjects that all later ciObjectFactories use.
  Arena* arena = new (mtCompiler) Arena(mtCompiler);
  ciEnv initial(arena);
  ciEnv* env = ciEnv::current();
  env->_factory->init_shared_objects();

  _initialized = true;

}

void ciObjectFactory::init_shared_objects() {

  _next_ident = 1;  // start numbering CI objects at 1

  {
    // Create the shared symbols, but not in _shared_ci_metadata.
    int i;
    for (i = vmSymbols::FIRST_SID; i < vmSymbols::SID_LIMIT; i++) {
      Symbol* vmsym = vmSymbols::symbol_at((vmSymbols::SID) i);
      assert(vmSymbols::find_sid(vmsym) == i, "1-1 mapping");
      ciSymbol* sym = new (_arena) ciSymbol(vmsym, (vmSymbols::SID) i);
      init_ident_of(sym);
      _shared_ci_symbols[i] = sym;
    }
#ifdef ASSERT
    for (i = vmSymbols::FIRST_SID; i < vmSymbols::SID_LIMIT; i++) {
      Symbol* vmsym = vmSymbols::symbol_at((vmSymbols::SID) i);
      ciSymbol* sym = vm_symbol_at((vmSymbols::SID) i);
      assert(sym->get_symbol() == vmsym, "oop must match");
    }
    assert(ciSymbol::void_class_signature()->get_symbol() == vmSymbols::void_class_signature(), "spot check");
#endif
  }

  _ci_metadata = new (_arena) GrowableArray<ciMetadata*>(_arena, 64, 0, NULL);

  for (int i = T_BOOLEAN; i <= T_CONFLICT; i++) {
    BasicType t = (BasicType)i;
    if (type2name(t) != NULL && t != T_OBJECT && t != T_ARRAY && t != T_NARROWOOP && t != T_NARROWKLASS) {
      ciType::_basic_types[t] = new (_arena) ciType(t);
      init_ident_of(ciType::_basic_types[t]);
    }
  }

  ciEnv::_null_object_instance = new (_arena) ciNullObject();
  init_ident_of(ciEnv::_null_object_instance);

#define WK_KLASS_DEFN(name, ignore_s, opt)                              \
  if (SystemDictionary::name() != NULL) \
    ciEnv::_##name = get_metadata(SystemDictionary::name())->as_instance_klass();

  WK_KLASSES_DO(WK_KLASS_DEFN)
#undef WK_KLASS_DEFN

  for (int len = -1; len != _ci_metadata->length(); ) {
    len = _ci_metadata->length();
    for (int i2 = 0; i2 < len; i2++) {
      ciMetadata* obj = _ci_metadata->at(i2);
      assert (obj->is_metadata(), "what else would it be?");
      if (obj->is_loaded() && obj->is_instance_klass()) {
        obj->as_instance_klass()->compute_nonstatic_fields();
      }
    }
  }

  ciEnv::_unloaded_cisymbol = ciObjectFactory::get_symbol(vmSymbols::dummy_symbol());
  // Create dummy InstanceKlass and ObjArrayKlass object and assign them idents
  ciEnv::_unloaded_ciinstance_klass = new (_arena) ciInstanceKlass(ciEnv::_unloaded_cisymbol, NULL, NULL);
  init_ident_of(ciEnv::_unloaded_ciinstance_klass);
  ciEnv::_unloaded_ciobjarrayklass = new (_arena) ciObjArrayKlass(ciEnv::_unloaded_cisymbol, ciEnv::_unloaded_ciinstance_klass, 1);
  init_ident_of(ciEnv::_unloaded_ciobjarrayklass);
  assert(ciEnv::_unloaded_ciobjarrayklass->is_obj_array_klass(), "just checking");

  get_metadata(Universe::boolArrayKlassObj());
  get_metadata(Universe::charArrayKlassObj());
  get_metadata(Universe::singleArrayKlassObj());
  get_metadata(Universe::doubleArrayKlassObj());
  get_metadata(Universe::byteArrayKlassObj());
  get_metadata(Universe::shortArrayKlassObj());
  get_metadata(Universe::intArrayKlassObj());
  get_metadata(Universe::longArrayKlassObj());



  assert(_non_perm_count == 0, "no shared non-perm objects");

  // The shared_ident_limit is the first ident number that will
  // be used for non-shared objects.  That is, numbers less than
  // this limit are permanently assigned to shared CI objects,
  // while the higher numbers are recycled afresh by each new ciEnv.

  _shared_ident_limit = _next_ident;
  _shared_ci_metadata = _ci_metadata;
}


ciSymbol* ciObjectFactory::get_symbol(Symbol* key) {
  vmSymbols::SID sid = vmSymbols::find_sid(key);
  if (sid != vmSymbols::NO_SID) {
    // do not pollute the main cache with it
    return vm_symbol_at(sid);
  }

  assert(vmSymbols::find_sid(key) == vmSymbols::NO_SID, "");
  ciSymbol* s = new (arena()) ciSymbol(key, vmSymbols::NO_SID);
  _symbols->push(s);
  return s;
}

// Decrement the refcount when done on symbols referenced by this compilation.
void ciObjectFactory::remove_symbols() {
  for (int i = 0; i < _symbols->length(); i++) {
    ciSymbol* s = _symbols->at(i);
    s->get_symbol()->decrement_refcount();
  }
  // Since _symbols is resource allocated we're not allowed to delete it
  // but it'll go away just the same.
}

// ------------------------------------------------------------------
// ciObjectFactory::get
//
// Get the ciObject corresponding to some oop.  If the ciObject has
// already been created, it is returned.  Otherwise, a new ciObject
// is created.
ciObject* ciObjectFactory::get(oop key) {
  ASSERT_IN_VM;

  assert(Universe::heap()->is_in_reserved(key), "must be");

  NonPermObject* &bucket = find_non_perm(key);
  if (bucket != NULL) {
    return bucket->object();
  }

  // The ciObject does not yet exist.  Create it and insert it
  // into the cache.
  Handle keyHandle(Thread::current(), key);
  ciObject* new_object = create_new_object(keyHandle());
  assert(oopDesc::equals(keyHandle(), new_object->get_oop()), "must be properly recorded");
  init_ident_of(new_object);
  assert(Universe::heap()->is_in_reserved(new_object->get_oop()), "must be");

  // Not a perm-space object.
  insert_non_perm(bucket, keyHandle(), new_object);
  return new_object;
}

int ciObjectFactory::metadata_compare(Metadata* const& key, ciMetadata* const& elt) {
  Metadata* value = elt->constant_encoding();
  if (key < value)      return -1;
  else if (key > value) return 1;
  else                  return 0;
}

// ------------------------------------------------------------------
// ciObjectFactory::get_metadata
//
// Get the ciMetadata corresponding to some Metadata. If the ciMetadata has
// already been created, it is returned. Otherwise, a new ciMetadata
// is created.
ciMetadata* ciObjectFactory::get_metadata(Metadata* key) {
  ASSERT_IN_VM;

#ifdef ASSERT
  if (CIObjectFactoryVerify) {
    Metadata* last = NULL;
    for (int j = 0; j< _ci_metadata->length(); j++) {
      Metadata* o = _ci_metadata->at(j)->constant_encoding();
      assert(last < o, "out of order");
      last = o;
    }
  }
#endif // ASSERT
  int len = _ci_metadata->length();
  bool found = false;
  int index = _ci_metadata->find_sorted<Metadata*, ciObjectFactory::metadata_compare>(key, found);
#ifdef ASSERT
  if (CIObjectFactoryVerify) {
    for (int i=0; i<_ci_metadata->length(); i++) {
      if (_ci_metadata->at(i)->constant_encoding() == key) {
        assert(index == i, " bad lookup");
      }
    }
  }
#endif

  if (!found) {
    // The ciMetadata does not yet exist. Create it and insert it
    // into the cache.
    ciMetadata* new_object = create_new_metadata(key);
    init_ident_of(new_object);
    assert(new_object->is_metadata(), "must be");

    if (len != _ci_metadata->length()) {
      // creating the new object has recursively entered new objects
      // into the table.  We need to recompute our index.
      index = _ci_metadata->find_sorted<Metadata*, ciObjectFactory::metadata_compare>(key, found);
    }
    assert(!found, "no double insert");
    _ci_metadata->insert_before(index, new_object);
    return new_object;
  }
  return _ci_metadata->at(index)->as_metadata();
}

// ------------------------------------------------------------------
// ciObjectFactory::create_new_object
//
// Create a new ciObject from an oop.
//
// Implementation note: this functionality could be virtual behavior
// of the oop itself.  For now, we explicitly marshal the object.
ciObject* ciObjectFactory::create_new_object(oop o) {
  EXCEPTION_CONTEXT;

  if (o->is_instance()) {
    instanceHandle h_i(THREAD, (instanceOop)o);
    if (java_lang_invoke_CallSite::is_instance(o))
      return new (arena()) ciCallSite(h_i);
    else if (java_lang_invoke_MemberName::is_instance(o))
      return new (arena()) ciMemberName(h_i);
    else if (java_lang_invoke_MethodHandle::is_instance(o))
      return new (arena()) ciMethodHandle(h_i);
    else if (java_lang_invoke_MethodType::is_instance(o))
      return new (arena()) ciMethodType(h_i);
    else
      return new (arena()) ciInstance(h_i);
  } else if (o->is_objArray()) {
    objArrayHandle h_oa(THREAD, (objArrayOop)o);
    return new (arena()) ciObjArray(h_oa);
  } else if (o->is_typeArray()) {
    typeArrayHandle h_ta(THREAD, (typeArrayOop)o);
    return new (arena()) ciTypeArray(h_ta);
  }

  // The oop is of some type not supported by the compiler interface.
  ShouldNotReachHere();
  return NULL;
}

// ------------------------------------------------------------------
// ciObjectFactory::create_new_metadata
//
// Create a new ciMetadata from a Metadata*.
//
// Implementation note: in order to keep Metadata live, an auxiliary ciObject
// is used, which points to it's holder.
ciMetadata* ciObjectFactory::create_new_metadata(Metadata* o) {
  EXCEPTION_CONTEXT;

  if (o->is_klass()) {
    Klass* k = (Klass*)o;
    if (k->is_instance_klass()) {
      return new (arena()) ciInstanceKlass(k);
    } else if (k->is_objArray_klass()) {
      return new (arena()) ciObjArrayKlass(k);
    } else if (k->is_typeArray_klass()) {
      return new (arena()) ciTypeArrayKlass(k);
    }
  } else if (o->is_method()) {
    methodHandle h_m(THREAD, (Method*)o);
    ciEnv *env = CURRENT_THREAD_ENV;
    ciInstanceKlass* holder = env->get_instance_klass(h_m()->method_holder());
    return new (arena()) ciMethod(h_m, holder);
  } else if (o->is_methodData()) {
    // Hold methodHandle alive - might not be necessary ???
    methodHandle h_m(THREAD, ((MethodData*)o)->method());
    return new (arena()) ciMethodData((MethodData*)o);
  }

  // The Metadata* is of some type not supported by the compiler interface.
  ShouldNotReachHere();
  return NULL;
}

//------------------------------------------------------------------
// ciObjectFactory::get_unloaded_method
//
// Get the ciMethod representing an unloaded/unfound method.
//
// Implementation note: unloaded methods are currently stored in
// an unordered array, requiring a linear-time lookup for each
// unloaded method.  This may need to change.
ciMethod* ciObjectFactory::get_unloaded_method(ciInstanceKlass* holder,
                                               ciSymbol*        name,
                                               ciSymbol*        signature,
                                               ciInstanceKlass* accessor) {
  ciSignature* that = NULL;
  for (int i = 0; i < _unloaded_methods->length(); i++) {
    ciMethod* entry = _unloaded_methods->at(i);
    if (entry->holder()->equals(holder) &&
        entry->name()->equals(name) &&
        entry->signature()->as_symbol()->equals(signature)) {
      // Short-circuit slow resolve.
      if (entry->signature()->accessing_klass() == accessor) {
        // We've found a match.
        return entry;
      } else {
        // Lazily create ciSignature
        if (that == NULL)  that = new (arena()) ciSignature(accessor, constantPoolHandle(), signature);
        if (entry->signature()->equals(that)) {
          // We've found a match.
          return entry;
        }
      }
    }
  }

  // This is a new unloaded method.  Create it and stick it in
  // the cache.
  ciMethod* new_method = new (arena()) ciMethod(holder, name, signature, accessor);

  init_ident_of(new_method);
  _unloaded_methods->append(new_method);

  return new_method;
}

//------------------------------------------------------------------
// ciObjectFactory::get_unloaded_klass
//
// Get a ciKlass representing an unloaded klass.
//
// Implementation note: unloaded klasses are currently stored in
// an unordered array, requiring a linear-time lookup for each
// unloaded klass.  This may need to change.
ciKlass* ciObjectFactory::get_unloaded_klass(ciKlass* accessing_klass,
                                             ciSymbol* name,
                                             bool create_if_not_found) {
  EXCEPTION_CONTEXT;
  oop loader = NULL;
  oop domain = NULL;
  if (accessing_klass != NULL) {
    loader = accessing_klass->loader();
    domain = accessing_klass->protection_domain();
  }
  for (int i=0; i<_unloaded_klasses->length(); i++) {
    ciKlass* entry = _unloaded_klasses->at(i);
    if (entry->name()->equals(name) &&
        oopDesc::equals(entry->loader(), loader) &&
        oopDesc::equals(entry->protection_domain(), domain)) {
      // We've found a match.
      return entry;
    }
  }

  if (!create_if_not_found)
    return NULL;

  // This is a new unloaded klass.  Create it and stick it in
  // the cache.
  ciKlass* new_klass = NULL;

  // Two cases: this is an unloaded ObjArrayKlass or an
  // unloaded InstanceKlass.  Deal with both.
  if (name->byte_at(0) == '[') {
    // Decompose the name.'
    FieldArrayInfo fd;
    BasicType element_type = FieldType::get_array_info(name->get_symbol(),
                                                       fd, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
      CURRENT_THREAD_ENV->record_out_of_memory_failure();
      return ciEnv::_unloaded_ciobjarrayklass;
    }
    int dimension = fd.dimension();
    assert(element_type != T_ARRAY, "unsuccessful decomposition");
    ciKlass* element_klass = NULL;
    if (element_type == T_OBJECT) {
      ciEnv *env = CURRENT_THREAD_ENV;
      ciSymbol* ci_name = env->get_symbol(fd.object_key());
      element_klass =
        env->get_klass_by_name(accessing_klass, ci_name, false)->as_instance_klass();
    } else {
      assert(dimension > 1, "one dimensional type arrays are always loaded.");

      // The type array itself takes care of one of the dimensions.
      dimension--;

      // The element klass is a TypeArrayKlass.
      element_klass = ciTypeArrayKlass::make(element_type);
    }
    new_klass = new (arena()) ciObjArrayKlass(name, element_klass, dimension);
  } else {
    jobject loader_handle = NULL;
    jobject domain_handle = NULL;
    if (accessing_klass != NULL) {
      loader_handle = accessing_klass->loader_handle();
      domain_handle = accessing_klass->protection_domain_handle();
    }
    new_klass = new (arena()) ciInstanceKlass(name, loader_handle, domain_handle);
  }
  init_ident_of(new_klass);
  _unloaded_klasses->append(new_klass);

  return new_klass;
}


//------------------------------------------------------------------
// ciObjectFactory::get_unloaded_instance
//
// Get a ciInstance representing an as-yet undetermined instance of a given class.
//
ciInstance* ciObjectFactory::get_unloaded_instance(ciInstanceKlass* instance_klass) {
  for (int i=0; i<_unloaded_instances->length(); i++) {
    ciInstance* entry = _unloaded_instances->at(i);
    if (entry->klass()->equals(instance_klass)) {
      // We've found a match.
      return entry;
    }
  }

  // This is a new unloaded instance.  Create it and stick it in
  // the cache.
  ciInstance* new_instance = new (arena()) ciInstance(instance_klass);

  init_ident_of(new_instance);
  _unloaded_instances->append(new_instance);

  // make sure it looks the way we want:
  assert(!new_instance->is_loaded(), "");
  assert(new_instance->klass() == instance_klass, "");

  return new_instance;
}


//------------------------------------------------------------------
// ciObjectFactory::get_unloaded_klass_mirror
//
// Get a ciInstance representing an unresolved klass mirror.
//
// Currently, this ignores the parameters and returns a unique unloaded instance.
ciInstance* ciObjectFactory::get_unloaded_klass_mirror(ciKlass*  type) {
  assert(ciEnv::_Class_klass != NULL, "");
  return get_unloaded_instance(ciEnv::_Class_klass->as_instance_klass());
}

//------------------------------------------------------------------
// ciObjectFactory::get_unloaded_method_handle_constant
//
// Get a ciInstance representing an unresolved method handle constant.
//
// Currently, this ignores the parameters and returns a unique unloaded instance.
ciInstance* ciObjectFactory::get_unloaded_method_handle_constant(ciKlass*  holder,
                                                                 ciSymbol* name,
                                                                 ciSymbol* signature,
                                                                 int       ref_kind) {
  if (ciEnv::_MethodHandle_klass == NULL)  return NULL;
  return get_unloaded_instance(ciEnv::_MethodHandle_klass->as_instance_klass());
}

//------------------------------------------------------------------
// ciObjectFactory::get_unloaded_method_type_constant
//
// Get a ciInstance representing an unresolved method type constant.
//
// Currently, this ignores the parameters and returns a unique unloaded instance.
ciInstance* ciObjectFactory::get_unloaded_method_type_constant(ciSymbol* signature) {
  if (ciEnv::_MethodType_klass == NULL)  return NULL;
  return get_unloaded_instance(ciEnv::_MethodType_klass->as_instance_klass());
}

ciInstance* ciObjectFactory::get_unloaded_object_constant() {
  if (ciEnv::_Object_klass == NULL)  return NULL;
  return get_unloaded_instance(ciEnv::_Object_klass->as_instance_klass());
}

//------------------------------------------------------------------
// ciObjectFactory::get_empty_methodData
//
// Get the ciMethodData representing the methodData for a method with
// none.
ciMethodData* ciObjectFactory::get_empty_methodData() {
  ciMethodData* new_methodData = new (arena()) ciMethodData();
  init_ident_of(new_methodData);
  return new_methodData;
}

//------------------------------------------------------------------
// ciObjectFactory::get_return_address
//
// Get a ciReturnAddress for a specified bci.
ciReturnAddress* ciObjectFactory::get_return_address(int bci) {
  for (int i=0; i<_return_addresses->length(); i++) {
    ciReturnAddress* entry = _return_addresses->at(i);
    if (entry->bci() == bci) {
      // We've found a match.
      return entry;
    }
  }

  ciReturnAddress* new_ret_addr = new (arena()) ciReturnAddress(bci);
  init_ident_of(new_ret_addr);
  _return_addresses->append(new_ret_addr);
  return new_ret_addr;
}

// ------------------------------------------------------------------
// ciObjectFactory::init_ident_of
void ciObjectFactory::init_ident_of(ciBaseObject* obj) {
  obj->set_ident(_next_ident++);
}

static ciObjectFactory::NonPermObject* emptyBucket = NULL;

// ------------------------------------------------------------------
// ciObjectFactory::find_non_perm
//
// Use a small hash table, hashed on the klass of the key.
// If there is no entry in the cache corresponding to this oop, return
// the null tail of the bucket into which the oop should be inserted.
ciObjectFactory::NonPermObject* &ciObjectFactory::find_non_perm(oop key) {
  assert(Universe::heap()->is_in_reserved(key), "must be");
  ciMetadata* klass = get_metadata(key->klass());
  NonPermObject* *bp = &_non_perm_bucket[(unsigned) klass->hash() % NON_PERM_BUCKETS];
  for (NonPermObject* p; (p = (*bp)) != NULL; bp = &p->next()) {
    if (is_equal(p, key))  break;
  }
  return (*bp);
}



// ------------------------------------------------------------------
// Code for for NonPermObject
//
inline ciObjectFactory::NonPermObject::NonPermObject(ciObjectFactory::NonPermObject* &bucket, oop key, ciObject* object) {
  assert(ciObjectFactory::is_initialized(), "");
  _object = object;
  _next = bucket;
  bucket = this;
}



// ------------------------------------------------------------------
// ciObjectFactory::insert_non_perm
//
// Insert a ciObject into the non-perm table.
void ciObjectFactory::insert_non_perm(ciObjectFactory::NonPermObject* &where, oop key, ciObject* obj) {
  assert(Universe::heap()->is_in_reserved_or_null(key), "must be");
  assert(&where != &emptyBucket, "must not try to fill empty bucket");
  NonPermObject* p = new (arena()) NonPermObject(where, key, obj);
  assert(where == p && is_equal(p, key) && p->object() == obj, "entry must match");
  assert(find_non_perm(key) == p, "must find the same spot");
  ++_non_perm_count;
}

// ------------------------------------------------------------------
// ciObjectFactory::vm_symbol_at
// Get the ciSymbol corresponding to some index in vmSymbols.
ciSymbol* ciObjectFactory::vm_symbol_at(int index) {
  assert(index >= vmSymbols::FIRST_SID && index < vmSymbols::SID_LIMIT, "oob");
  return _shared_ci_symbols[index];
}

// ------------------------------------------------------------------
// ciObjectFactory::metadata_do
void ciObjectFactory::metadata_do(void f(Metadata*)) {
  if (_ci_metadata == NULL) return;
  for (int j = 0; j< _ci_metadata->length(); j++) {
    Metadata* o = _ci_metadata->at(j)->constant_encoding();
    f(o);
  }
}

// ------------------------------------------------------------------
// ciObjectFactory::print_contents_impl
void ciObjectFactory::print_contents_impl() {
  int len = _ci_metadata->length();
  tty->print_cr("ciObjectFactory (%d) meta data contents:", len);
  for (int i=0; i<len; i++) {
    _ci_metadata->at(i)->print();
    tty->cr();
  }
}

// ------------------------------------------------------------------
// ciObjectFactory::print_contents
void ciObjectFactory::print_contents() {
  print();
  tty->cr();
  GUARDED_VM_ENTRY(print_contents_impl();)
}

// ------------------------------------------------------------------
// ciObjectFactory::print
//
// Print debugging information about the object factory
void ciObjectFactory::print() {
  tty->print("<ciObjectFactory oops=%d metadata=%d unloaded_methods=%d unloaded_instances=%d unloaded_klasses=%d>",
             _non_perm_count, _ci_metadata->length(), _unloaded_methods->length(),
             _unloaded_instances->length(),
             _unloaded_klasses->length());
}

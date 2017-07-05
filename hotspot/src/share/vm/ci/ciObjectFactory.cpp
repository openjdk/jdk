/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_ciObjectFactory.cpp.incl"

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

GrowableArray<ciObject*>* ciObjectFactory::_shared_ci_objects = NULL;
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
  _ci_objects = new (arena) GrowableArray<ciObject*>(arena, expected_size, 0, NULL);

  // If the shared ci objects exist append them to this factory's objects

  if (_shared_ci_objects != NULL) {
    _ci_objects->appendAll(_shared_ci_objects);
  }

  _unloaded_methods = new (arena) GrowableArray<ciMethod*>(arena, 4, 0, NULL);
  _unloaded_klasses = new (arena) GrowableArray<ciKlass*>(arena, 8, 0, NULL);
  _return_addresses =
    new (arena) GrowableArray<ciReturnAddress*>(arena, 8, 0, NULL);
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
  Arena* arena = new Arena();
  ciEnv initial(arena);
  ciEnv* env = ciEnv::current();
  env->_factory->init_shared_objects();

  _initialized = true;

}

void ciObjectFactory::init_shared_objects() {

  _next_ident = 1;  // start numbering CI objects at 1

  {
    // Create the shared symbols, but not in _shared_ci_objects.
    int i;
    for (i = vmSymbols::FIRST_SID; i < vmSymbols::SID_LIMIT; i++) {
      symbolHandle sym_handle = vmSymbolHandles::symbol_handle_at((vmSymbols::SID) i);
      assert(vmSymbols::find_sid(sym_handle()) == i, "1-1 mapping");
      ciSymbol* sym = new (_arena) ciSymbol(sym_handle, (vmSymbols::SID) i);
      init_ident_of(sym);
      _shared_ci_symbols[i] = sym;
    }
#ifdef ASSERT
    for (i = vmSymbols::FIRST_SID; i < vmSymbols::SID_LIMIT; i++) {
      symbolHandle sym_handle = vmSymbolHandles::symbol_handle_at((vmSymbols::SID) i);
      ciSymbol* sym = vm_symbol_at((vmSymbols::SID) i);
      assert(sym->get_oop() == sym_handle(), "oop must match");
    }
    assert(ciSymbol::void_class_signature()->get_oop() == vmSymbols::void_class_signature(), "spot check");
#endif
  }

  _ci_objects = new (_arena) GrowableArray<ciObject*>(_arena, 64, 0, NULL);

  for (int i = T_BOOLEAN; i <= T_CONFLICT; i++) {
    BasicType t = (BasicType)i;
    if (type2name(t) != NULL && t != T_OBJECT && t != T_ARRAY && t != T_NARROWOOP) {
      ciType::_basic_types[t] = new (_arena) ciType(t);
      init_ident_of(ciType::_basic_types[t]);
    }
  }

  ciEnv::_null_object_instance = new (_arena) ciNullObject();
  init_ident_of(ciEnv::_null_object_instance);
  ciEnv::_method_klass_instance =
    get(Universe::methodKlassObj())->as_method_klass();
  ciEnv::_symbol_klass_instance =
    get(Universe::symbolKlassObj())->as_symbol_klass();
  ciEnv::_klass_klass_instance =
    get(Universe::klassKlassObj())->as_klass_klass();
  ciEnv::_instance_klass_klass_instance =
    get(Universe::instanceKlassKlassObj())
      ->as_instance_klass_klass();
  ciEnv::_type_array_klass_klass_instance =
    get(Universe::typeArrayKlassKlassObj())
      ->as_type_array_klass_klass();
  ciEnv::_obj_array_klass_klass_instance =
    get(Universe::objArrayKlassKlassObj())
      ->as_obj_array_klass_klass();

#define WK_KLASS_DEFN(name, ignore_s, opt)                              \
  if (SystemDictionary::name() != NULL) \
    ciEnv::_##name = get(SystemDictionary::name())->as_instance_klass();

  WK_KLASSES_DO(WK_KLASS_DEFN)
#undef WK_KLASS_DEFN

  for (int len = -1; len != _ci_objects->length(); ) {
    len = _ci_objects->length();
    for (int i2 = 0; i2 < len; i2++) {
      ciObject* obj = _ci_objects->at(i2);
      if (obj->is_loaded() && obj->is_instance_klass()) {
        obj->as_instance_klass()->compute_nonstatic_fields();
      }
    }
  }

  ciEnv::_unloaded_cisymbol = (ciSymbol*) ciObjectFactory::get(vmSymbols::dummy_symbol_oop());
  // Create dummy instanceKlass and objArrayKlass object and assign them idents
  ciEnv::_unloaded_ciinstance_klass = new (_arena) ciInstanceKlass(ciEnv::_unloaded_cisymbol, NULL, NULL);
  init_ident_of(ciEnv::_unloaded_ciinstance_klass);
  ciEnv::_unloaded_ciobjarrayklass = new (_arena) ciObjArrayKlass(ciEnv::_unloaded_cisymbol, ciEnv::_unloaded_ciinstance_klass, 1);
  init_ident_of(ciEnv::_unloaded_ciobjarrayklass);
  assert(ciEnv::_unloaded_ciobjarrayklass->is_obj_array_klass(), "just checking");

  get(Universe::boolArrayKlassObj());
  get(Universe::charArrayKlassObj());
  get(Universe::singleArrayKlassObj());
  get(Universe::doubleArrayKlassObj());
  get(Universe::byteArrayKlassObj());
  get(Universe::shortArrayKlassObj());
  get(Universe::intArrayKlassObj());
  get(Universe::longArrayKlassObj());



  assert(_non_perm_count == 0, "no shared non-perm objects");

  // The shared_ident_limit is the first ident number that will
  // be used for non-shared objects.  That is, numbers less than
  // this limit are permanently assigned to shared CI objects,
  // while the higher numbers are recycled afresh by each new ciEnv.

  _shared_ident_limit = _next_ident;
  _shared_ci_objects = _ci_objects;
}

// ------------------------------------------------------------------
// ciObjectFactory::get
//
// Get the ciObject corresponding to some oop.  If the ciObject has
// already been created, it is returned.  Otherwise, a new ciObject
// is created.
ciObject* ciObjectFactory::get(oop key) {
  ASSERT_IN_VM;

#ifdef ASSERT
  if (CIObjectFactoryVerify) {
    oop last = NULL;
    for (int j = 0; j< _ci_objects->length(); j++) {
      oop o = _ci_objects->at(j)->get_oop();
      assert(last < o, "out of order");
      last = o;
    }
  }
#endif // ASSERT
  int len = _ci_objects->length();
  int index = find(key, _ci_objects);
#ifdef ASSERT
  if (CIObjectFactoryVerify) {
    for (int i=0; i<_ci_objects->length(); i++) {
      if (_ci_objects->at(i)->get_oop() == key) {
        assert(index == i, " bad lookup");
      }
    }
  }
#endif
  if (!is_found_at(index, key, _ci_objects)) {
    // Check in the non-perm area before putting it in the list.
    NonPermObject* &bucket = find_non_perm(key);
    if (bucket != NULL) {
      return bucket->object();
    }

    // Check in the shared symbol area before putting it in the list.
    if (key->is_symbol()) {
      vmSymbols::SID sid = vmSymbols::find_sid((symbolOop)key);
      if (sid != vmSymbols::NO_SID) {
        // do not pollute the main cache with it
        return vm_symbol_at(sid);
      }
    }

    // The ciObject does not yet exist.  Create it and insert it
    // into the cache.
    Handle keyHandle(key);
    ciObject* new_object = create_new_object(keyHandle());
    assert(keyHandle() == new_object->get_oop(), "must be properly recorded");
    init_ident_of(new_object);
    if (!new_object->is_perm()) {
      // Not a perm-space object.
      insert_non_perm(bucket, keyHandle(), new_object);
      return new_object;
    }
    if (len != _ci_objects->length()) {
      // creating the new object has recursively entered new objects
      // into the table.  We need to recompute our index.
      index = find(keyHandle(), _ci_objects);
    }
    assert(!is_found_at(index, keyHandle(), _ci_objects), "no double insert");
    insert(index, new_object, _ci_objects);
    return new_object;
  }
  return _ci_objects->at(index);
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

  if (o->is_symbol()) {
    symbolHandle h_o(THREAD, (symbolOop)o);
    assert(vmSymbols::find_sid(h_o()) == vmSymbols::NO_SID, "");
    return new (arena()) ciSymbol(h_o, vmSymbols::NO_SID);
  } else if (o->is_klass()) {
    KlassHandle h_k(THREAD, (klassOop)o);
    Klass* k = ((klassOop)o)->klass_part();
    if (k->oop_is_instance()) {
      return new (arena()) ciInstanceKlass(h_k);
    } else if (k->oop_is_objArray()) {
      return new (arena()) ciObjArrayKlass(h_k);
    } else if (k->oop_is_typeArray()) {
      return new (arena()) ciTypeArrayKlass(h_k);
    } else if (k->oop_is_method()) {
      return new (arena()) ciMethodKlass(h_k);
    } else if (k->oop_is_symbol()) {
      return new (arena()) ciSymbolKlass(h_k);
    } else if (k->oop_is_klass()) {
      if (k->oop_is_objArrayKlass()) {
        return new (arena()) ciObjArrayKlassKlass(h_k);
      } else if (k->oop_is_typeArrayKlass()) {
        return new (arena()) ciTypeArrayKlassKlass(h_k);
      } else if (k->oop_is_instanceKlass()) {
        return new (arena()) ciInstanceKlassKlass(h_k);
      } else {
        assert(o == Universe::klassKlassObj(), "bad klassKlass");
        return new (arena()) ciKlassKlass(h_k);
      }
    }
  } else if (o->is_method()) {
    methodHandle h_m(THREAD, (methodOop)o);
    return new (arena()) ciMethod(h_m);
  } else if (o->is_methodData()) {
    methodDataHandle h_md(THREAD, (methodDataOop)o);
    return new (arena()) ciMethodData(h_md);
  } else if (o->is_instance()) {
    instanceHandle h_i(THREAD, (instanceOop)o);
    if (java_dyn_CallSite::is_instance(o))
      return new (arena()) ciCallSite(h_i);
    else if (java_dyn_MethodHandle::is_instance(o))
      return new (arena()) ciMethodHandle(h_i);
    else
      return new (arena()) ciInstance(h_i);
  } else if (o->is_objArray()) {
    objArrayHandle h_oa(THREAD, (objArrayOop)o);
    return new (arena()) ciObjArray(h_oa);
  } else if (o->is_typeArray()) {
    typeArrayHandle h_ta(THREAD, (typeArrayOop)o);
    return new (arena()) ciTypeArray(h_ta);
  } else if (o->is_constantPoolCache()) {
    constantPoolCacheHandle h_cpc(THREAD, (constantPoolCacheOop) o);
    return new (arena()) ciCPCache(h_cpc);
  }

  // The oop is of some type not supported by the compiler interface.
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
                                               ciSymbol*        signature) {
  for (int i=0; i<_unloaded_methods->length(); i++) {
    ciMethod* entry = _unloaded_methods->at(i);
    if (entry->holder()->equals(holder) &&
        entry->name()->equals(name) &&
        entry->signature()->as_symbol()->equals(signature)) {
      // We've found a match.
      return entry;
    }
  }

  // This is a new unloaded method.  Create it and stick it in
  // the cache.
  ciMethod* new_method = new (arena()) ciMethod(holder, name, signature);

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
        entry->loader() == loader &&
        entry->protection_domain() == domain) {
      // We've found a match.
      return entry;
    }
  }

  if (!create_if_not_found)
    return NULL;

  // This is a new unloaded klass.  Create it and stick it in
  // the cache.
  ciKlass* new_klass = NULL;

  // Two cases: this is an unloaded objArrayKlass or an
  // unloaded instanceKlass.  Deal with both.
  if (name->byte_at(0) == '[') {
    // Decompose the name.'
    jint dimension = 0;
    symbolOop element_name = NULL;
    BasicType element_type= FieldType::get_array_info(name->get_symbolOop(),
                                                      &dimension,
                                                      &element_name,
                                                      THREAD);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
      CURRENT_THREAD_ENV->record_out_of_memory_failure();
      return ciEnv::_unloaded_ciobjarrayklass;
    }
    assert(element_type != T_ARRAY, "unsuccessful decomposition");
    ciKlass* element_klass = NULL;
    if (element_type == T_OBJECT) {
      ciEnv *env = CURRENT_THREAD_ENV;
      ciSymbol* ci_name = env->get_object(element_name)->as_symbol();
      element_klass =
        env->get_klass_by_name(accessing_klass, ci_name, false)->as_instance_klass();
    } else {
      assert(dimension > 1, "one dimensional type arrays are always loaded.");

      // The type array itself takes care of one of the dimensions.
      dimension--;

      // The element klass is a typeArrayKlass.
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
void ciObjectFactory::init_ident_of(ciObject* obj) {
  obj->set_ident(_next_ident++);
}


// ------------------------------------------------------------------
// ciObjectFactory::find
//
// Use binary search to find the position of this oop in the cache.
// If there is no entry in the cache corresponding to this oop, return
// the position at which the oop should be inserted.
int ciObjectFactory::find(oop key, GrowableArray<ciObject*>* objects) {
  int min = 0;
  int max = objects->length()-1;

  // print_contents();

  while (max >= min) {
    int mid = (max + min) / 2;
    oop value = objects->at(mid)->get_oop();
    if (value < key) {
      min = mid + 1;
    } else if (value > key) {
      max = mid - 1;
    } else {
      return mid;
    }
  }
  return min;
}

// ------------------------------------------------------------------
// ciObjectFactory::is_found_at
//
// Verify that the binary seach found the given key.
bool ciObjectFactory::is_found_at(int index, oop key, GrowableArray<ciObject*>* objects) {
  return (index < objects->length() &&
          objects->at(index)->get_oop() == key);
}


// ------------------------------------------------------------------
// ciObjectFactory::insert
//
// Insert a ciObject into the table at some index.
void ciObjectFactory::insert(int index, ciObject* obj, GrowableArray<ciObject*>* objects) {
  int len = objects->length();
  if (len == index) {
    objects->append(obj);
  } else {
    objects->append(objects->at(len-1));
    int pos;
    for (pos = len-2; pos >= index; pos--) {
      objects->at_put(pos+1,objects->at(pos));
    }
    objects->at_put(index, obj);
  }
#ifdef ASSERT
  if (CIObjectFactoryVerify) {
    oop last = NULL;
    for (int j = 0; j< objects->length(); j++) {
      oop o = objects->at(j)->get_oop();
      assert(last < o, "out of order");
      last = o;
    }
  }
#endif // ASSERT
}

static ciObjectFactory::NonPermObject* emptyBucket = NULL;

// ------------------------------------------------------------------
// ciObjectFactory::find_non_perm
//
// Use a small hash table, hashed on the klass of the key.
// If there is no entry in the cache corresponding to this oop, return
// the null tail of the bucket into which the oop should be inserted.
ciObjectFactory::NonPermObject* &ciObjectFactory::find_non_perm(oop key) {
  // Be careful:  is_perm might change from false to true.
  // Thus, there might be a matching perm object in the table.
  // If there is, this probe must find it.
  if (key->is_perm() && _non_perm_count == 0) {
    return emptyBucket;
  } else if (key->is_instance()) {
    if (key->klass() == SystemDictionary::Class_klass()) {
      // class mirror instances are always perm
      return emptyBucket;
    }
    // fall through to probe
  } else if (key->is_array()) {
    // fall through to probe
  } else {
    // not an array or instance
    return emptyBucket;
  }

  ciObject* klass = get(key->klass());
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
// ciObjectFactory::print_contents_impl
void ciObjectFactory::print_contents_impl() {
  int len = _ci_objects->length();
  tty->print_cr("ciObjectFactory (%d) oop contents:", len);
  for (int i=0; i<len; i++) {
    _ci_objects->at(i)->print();
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
  tty->print("<ciObjectFactory oops=%d unloaded_methods=%d unloaded_klasses=%d>",
             _ci_objects->length(), _unloaded_methods->length(),
             _unloaded_klasses->length());
}

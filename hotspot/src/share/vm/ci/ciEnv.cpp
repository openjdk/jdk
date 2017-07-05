/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "incls/_ciEnv.cpp.incl"

// ciEnv
//
// This class is the top level broker for requests from the compiler
// to the VM.

ciObject*              ciEnv::_null_object_instance;
ciMethodKlass*         ciEnv::_method_klass_instance;
ciSymbolKlass*         ciEnv::_symbol_klass_instance;
ciKlassKlass*          ciEnv::_klass_klass_instance;
ciInstanceKlassKlass*  ciEnv::_instance_klass_klass_instance;
ciTypeArrayKlassKlass* ciEnv::_type_array_klass_klass_instance;
ciObjArrayKlassKlass*  ciEnv::_obj_array_klass_klass_instance;

#define WK_KLASS_DEFN(name, ignore_s, ignore_o) ciInstanceKlass* ciEnv::_##name = NULL;
WK_KLASSES_DO(WK_KLASS_DEFN)
#undef WK_KLASS_DEFN

ciSymbol*        ciEnv::_unloaded_cisymbol = NULL;
ciInstanceKlass* ciEnv::_unloaded_ciinstance_klass = NULL;
ciObjArrayKlass* ciEnv::_unloaded_ciobjarrayklass = NULL;

jobject ciEnv::_ArrayIndexOutOfBoundsException_handle = NULL;
jobject ciEnv::_ArrayStoreException_handle = NULL;
jobject ciEnv::_ClassCastException_handle = NULL;

#ifndef PRODUCT
static bool firstEnv = true;
#endif /* PRODUCT */

// ------------------------------------------------------------------
// ciEnv::ciEnv
ciEnv::ciEnv(CompileTask* task, int system_dictionary_modification_counter) {
  VM_ENTRY_MARK;

  // Set up ciEnv::current immediately, for the sake of ciObjectFactory, etc.
  thread->set_env(this);
  assert(ciEnv::current() == this, "sanity");

  _oop_recorder = NULL;
  _debug_info = NULL;
  _dependencies = NULL;
  _failure_reason = NULL;
  _compilable = MethodCompilable;
  _break_at_compile = false;
  _compiler_data = NULL;
#ifndef PRODUCT
  assert(!firstEnv, "not initialized properly");
#endif /* !PRODUCT */

  _system_dictionary_modification_counter = system_dictionary_modification_counter;
  _num_inlined_bytecodes = 0;
  assert(task == NULL || thread->task() == task, "sanity");
  _task = task;
  _log = NULL;

  // Temporary buffer for creating symbols and such.
  _name_buffer = NULL;
  _name_buffer_len = 0;

  _arena   = &_ciEnv_arena;
  _factory = new (_arena) ciObjectFactory(_arena, 128);

  // Preload commonly referenced system ciObjects.

  // During VM initialization, these instances have not yet been created.
  // Assertions ensure that these instances are not accessed before
  // their initialization.

  assert(Universe::is_fully_initialized(), "should be complete");

  oop o = Universe::null_ptr_exception_instance();
  assert(o != NULL, "should have been initialized");
  _NullPointerException_instance = get_object(o)->as_instance();
  o = Universe::arithmetic_exception_instance();
  assert(o != NULL, "should have been initialized");
  _ArithmeticException_instance = get_object(o)->as_instance();

  _ArrayIndexOutOfBoundsException_instance = NULL;
  _ArrayStoreException_instance = NULL;
  _ClassCastException_instance = NULL;
  _the_null_string = NULL;
  _the_min_jint_string = NULL;
}

ciEnv::ciEnv(Arena* arena) {
  ASSERT_IN_VM;

  // Set up ciEnv::current immediately, for the sake of ciObjectFactory, etc.
  CompilerThread* current_thread = CompilerThread::current();
  assert(current_thread->env() == NULL, "must be");
  current_thread->set_env(this);
  assert(ciEnv::current() == this, "sanity");

  _oop_recorder = NULL;
  _debug_info = NULL;
  _dependencies = NULL;
  _failure_reason = NULL;
  _compilable = MethodCompilable_never;
  _break_at_compile = false;
  _compiler_data = NULL;
#ifndef PRODUCT
  assert(firstEnv, "must be first");
  firstEnv = false;
#endif /* !PRODUCT */

  _system_dictionary_modification_counter = 0;
  _num_inlined_bytecodes = 0;
  _task = NULL;
  _log = NULL;

  // Temporary buffer for creating symbols and such.
  _name_buffer = NULL;
  _name_buffer_len = 0;

  _arena   = arena;
  _factory = new (_arena) ciObjectFactory(_arena, 128);

  // Preload commonly referenced system ciObjects.

  // During VM initialization, these instances have not yet been created.
  // Assertions ensure that these instances are not accessed before
  // their initialization.

  assert(Universe::is_fully_initialized(), "must be");

  oop o = Universe::null_ptr_exception_instance();
  assert(o != NULL, "should have been initialized");
  _NullPointerException_instance = get_object(o)->as_instance();
  o = Universe::arithmetic_exception_instance();
  assert(o != NULL, "should have been initialized");
  _ArithmeticException_instance = get_object(o)->as_instance();

  _ArrayIndexOutOfBoundsException_instance = NULL;
  _ArrayStoreException_instance = NULL;
  _ClassCastException_instance = NULL;
  _the_null_string = NULL;
  _the_min_jint_string = NULL;
}

ciEnv::~ciEnv() {
  CompilerThread* current_thread = CompilerThread::current();
  current_thread->set_env(NULL);
}

// ------------------------------------------------------------------
// Cache Jvmti state
void ciEnv::cache_jvmti_state() {
  VM_ENTRY_MARK;
  // Get Jvmti capabilities under lock to get consistant values.
  MutexLocker mu(JvmtiThreadState_lock);
  _jvmti_can_hotswap_or_post_breakpoint = JvmtiExport::can_hotswap_or_post_breakpoint();
  _jvmti_can_access_local_variables     = JvmtiExport::can_access_local_variables();
  _jvmti_can_post_on_exceptions         = JvmtiExport::can_post_on_exceptions();
}

// ------------------------------------------------------------------
// Cache DTrace flags
void ciEnv::cache_dtrace_flags() {
  // Need lock?
  _dtrace_extended_probes = ExtendedDTraceProbes;
  if (_dtrace_extended_probes) {
    _dtrace_monitor_probes  = true;
    _dtrace_method_probes   = true;
    _dtrace_alloc_probes    = true;
  } else {
    _dtrace_monitor_probes  = DTraceMonitorProbes;
    _dtrace_method_probes   = DTraceMethodProbes;
    _dtrace_alloc_probes    = DTraceAllocProbes;
  }
}

// ------------------------------------------------------------------
// helper for lazy exception creation
ciInstance* ciEnv::get_or_create_exception(jobject& handle, symbolHandle name) {
  VM_ENTRY_MARK;
  if (handle == NULL) {
    // Cf. universe.cpp, creation of Universe::_null_ptr_exception_instance.
    klassOop k = SystemDictionary::find(name, Handle(), Handle(), THREAD);
    jobject objh = NULL;
    if (!HAS_PENDING_EXCEPTION && k != NULL) {
      oop obj = instanceKlass::cast(k)->allocate_permanent_instance(THREAD);
      if (!HAS_PENDING_EXCEPTION)
        objh = JNIHandles::make_global(obj);
    }
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
    } else {
      handle = objh;
    }
  }
  oop obj = JNIHandles::resolve(handle);
  return obj == NULL? NULL: get_object(obj)->as_instance();
}

// ------------------------------------------------------------------
// ciEnv::ArrayIndexOutOfBoundsException_instance, etc.
ciInstance* ciEnv::ArrayIndexOutOfBoundsException_instance() {
  if (_ArrayIndexOutOfBoundsException_instance == NULL) {
    _ArrayIndexOutOfBoundsException_instance
          = get_or_create_exception(_ArrayIndexOutOfBoundsException_handle,
          vmSymbolHandles::java_lang_ArrayIndexOutOfBoundsException());
  }
  return _ArrayIndexOutOfBoundsException_instance;
}
ciInstance* ciEnv::ArrayStoreException_instance() {
  if (_ArrayStoreException_instance == NULL) {
    _ArrayStoreException_instance
          = get_or_create_exception(_ArrayStoreException_handle,
          vmSymbolHandles::java_lang_ArrayStoreException());
  }
  return _ArrayStoreException_instance;
}
ciInstance* ciEnv::ClassCastException_instance() {
  if (_ClassCastException_instance == NULL) {
    _ClassCastException_instance
          = get_or_create_exception(_ClassCastException_handle,
          vmSymbolHandles::java_lang_ClassCastException());
  }
  return _ClassCastException_instance;
}

ciInstance* ciEnv::the_null_string() {
  if (_the_null_string == NULL) {
    VM_ENTRY_MARK;
    _the_null_string = get_object(Universe::the_null_string())->as_instance();
  }
  return _the_null_string;
}

ciInstance* ciEnv::the_min_jint_string() {
  if (_the_min_jint_string == NULL) {
    VM_ENTRY_MARK;
    _the_min_jint_string = get_object(Universe::the_min_jint_string())->as_instance();
  }
  return _the_min_jint_string;
}

// ------------------------------------------------------------------
// ciEnv::get_method_from_handle
ciMethod* ciEnv::get_method_from_handle(jobject method) {
  VM_ENTRY_MARK;
  return get_object(JNIHandles::resolve(method))->as_method();
}

// ------------------------------------------------------------------
// ciEnv::make_array
ciArray* ciEnv::make_system_array(GrowableArray<ciObject*>* objects) {
  VM_ENTRY_MARK;
  int length = objects->length();
  objArrayOop a = oopFactory::new_system_objArray(length, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    record_out_of_memory_failure();
    return NULL;
  }
  for (int i = 0; i < length; i++) {
    a->obj_at_put(i, objects->at(i)->get_oop());
  }
  assert(a->is_perm(), "");
  return get_object(a)->as_array();
}


// ------------------------------------------------------------------
// ciEnv::array_element_offset_in_bytes
int ciEnv::array_element_offset_in_bytes(ciArray* a_h, ciObject* o_h) {
  VM_ENTRY_MARK;
  objArrayOop a = (objArrayOop)a_h->get_oop();
  assert(a->is_objArray(), "");
  int length = a->length();
  oop o = o_h->get_oop();
  for (int i = 0; i < length; i++) {
    if (a->obj_at(i) == o)  return i;
  }
  return -1;
}


// ------------------------------------------------------------------
// ciEnv::check_klass_accessiblity
//
// Note: the logic of this method should mirror the logic of
// constantPoolOopDesc::verify_constant_pool_resolve.
bool ciEnv::check_klass_accessibility(ciKlass* accessing_klass,
                                      klassOop resolved_klass) {
  if (accessing_klass == NULL || !accessing_klass->is_loaded()) {
    return true;
  }
  if (accessing_klass->is_obj_array()) {
    accessing_klass = accessing_klass->as_obj_array_klass()->base_element_klass();
  }
  if (!accessing_klass->is_instance_klass()) {
    return true;
  }

  if (resolved_klass->klass_part()->oop_is_objArray()) {
    // Find the element klass, if this is an array.
    resolved_klass = objArrayKlass::cast(resolved_klass)->bottom_klass();
  }
  if (resolved_klass->klass_part()->oop_is_instance()) {
    return Reflection::verify_class_access(accessing_klass->get_klassOop(),
                                           resolved_klass,
                                           true);
  }
  return true;
}

// ------------------------------------------------------------------
// ciEnv::get_klass_by_name_impl
ciKlass* ciEnv::get_klass_by_name_impl(ciKlass* accessing_klass,
                                       ciSymbol* name,
                                       bool require_local) {
  ASSERT_IN_VM;
  EXCEPTION_CONTEXT;

  // Now we need to check the SystemDictionary
  symbolHandle sym(THREAD, name->get_symbolOop());
  if (sym->byte_at(0) == 'L' &&
    sym->byte_at(sym->utf8_length()-1) == ';') {
    // This is a name from a signature.  Strip off the trimmings.
    sym = oopFactory::new_symbol_handle(sym->as_utf8()+1,
                                        sym->utf8_length()-2,
                                        KILL_COMPILE_ON_FATAL_(_unloaded_ciinstance_klass));
    name = get_object(sym())->as_symbol();
  }

  // Check for prior unloaded klass.  The SystemDictionary's answers
  // can vary over time but the compiler needs consistency.
  ciKlass* unloaded_klass = check_get_unloaded_klass(accessing_klass, name);
  if (unloaded_klass != NULL) {
    if (require_local)  return NULL;
    return unloaded_klass;
  }

  Handle loader(THREAD, (oop)NULL);
  Handle domain(THREAD, (oop)NULL);
  if (accessing_klass != NULL) {
    loader = Handle(THREAD, accessing_klass->loader());
    domain = Handle(THREAD, accessing_klass->protection_domain());
  }

  // setup up the proper type to return on OOM
  ciKlass* fail_type;
  if (sym->byte_at(0) == '[') {
    fail_type = _unloaded_ciobjarrayklass;
  } else {
    fail_type = _unloaded_ciinstance_klass;
  }
  klassOop found_klass;
  if (!require_local) {
    found_klass =
      SystemDictionary::find_constrained_instance_or_array_klass(sym, loader,
                                                                 KILL_COMPILE_ON_FATAL_(fail_type));
  } else {
    found_klass =
      SystemDictionary::find_instance_or_array_klass(sym, loader, domain,
                                                     KILL_COMPILE_ON_FATAL_(fail_type));
  }

  // If we fail to find an array klass, look again for its element type.
  // The element type may be available either locally or via constraints.
  // In either case, if we can find the element type in the system dictionary,
  // we must build an array type around it.  The CI requires array klasses
  // to be loaded if their element klasses are loaded, except when memory
  // is exhausted.
  if (sym->byte_at(0) == '[' &&
      (sym->byte_at(1) == '[' || sym->byte_at(1) == 'L')) {
    // We have an unloaded array.
    // Build it on the fly if the element class exists.
    symbolOop elem_sym = oopFactory::new_symbol(sym->as_utf8()+1,
                                                sym->utf8_length()-1,
                                                KILL_COMPILE_ON_FATAL_(fail_type));
    // Get element ciKlass recursively.
    ciKlass* elem_klass =
      get_klass_by_name_impl(accessing_klass,
                             get_object(elem_sym)->as_symbol(),
                             require_local);
    if (elem_klass != NULL && elem_klass->is_loaded()) {
      // Now make an array for it
      return ciObjArrayKlass::make_impl(elem_klass);
    }
  }

  if (found_klass != NULL) {
    // Found it.  Build a CI handle.
    return get_object(found_klass)->as_klass();
  }

  if (require_local)  return NULL;
  // Not yet loaded into the VM, or not governed by loader constraints.
  // Make a CI representative for it.
  return get_unloaded_klass(accessing_klass, name);
}

// ------------------------------------------------------------------
// ciEnv::get_klass_by_name
ciKlass* ciEnv::get_klass_by_name(ciKlass* accessing_klass,
                                  ciSymbol* klass_name,
                                  bool require_local) {
  GUARDED_VM_ENTRY(return get_klass_by_name_impl(accessing_klass,
                                                 klass_name,
                                                 require_local);)
}

// ------------------------------------------------------------------
// ciEnv::get_klass_by_index_impl
//
// Implementation of get_klass_by_index.
ciKlass* ciEnv::get_klass_by_index_impl(constantPoolHandle cpool,
                                        int index,
                                        bool& is_accessible,
                                        ciInstanceKlass* accessor) {
  EXCEPTION_CONTEXT;
  KlassHandle klass (THREAD, constantPoolOopDesc::klass_at_if_loaded(cpool, index));
  symbolHandle klass_name;
  if (klass.is_null()) {
    // The klass has not been inserted into the constant pool.
    // Try to look it up by name.
    {
      // We have to lock the cpool to keep the oop from being resolved
      // while we are accessing it.
      ObjectLocker ol(cpool, THREAD);

      constantTag tag = cpool->tag_at(index);
      if (tag.is_klass()) {
        // The klass has been inserted into the constant pool
        // very recently.
        klass = KlassHandle(THREAD, cpool->resolved_klass_at(index));
      } else if (tag.is_symbol()) {
        klass_name = symbolHandle(THREAD, cpool->symbol_at(index));
      } else {
        assert(cpool->tag_at(index).is_unresolved_klass(), "wrong tag");
        klass_name = symbolHandle(THREAD, cpool->unresolved_klass_at(index));
      }
    }
  }

  if (klass.is_null()) {
    // Not found in constant pool.  Use the name to do the lookup.
    ciKlass* k = get_klass_by_name_impl(accessor,
                                        get_object(klass_name())->as_symbol(),
                                        false);
    // Calculate accessibility the hard way.
    if (!k->is_loaded()) {
      is_accessible = false;
    } else if (k->loader() != accessor->loader() &&
               get_klass_by_name_impl(accessor, k->name(), true) == NULL) {
      // Loaded only remotely.  Not linked yet.
      is_accessible = false;
    } else {
      // Linked locally, and we must also check public/private, etc.
      is_accessible = check_klass_accessibility(accessor, k->get_klassOop());
    }
    return k;
  }

  // Check for prior unloaded klass.  The SystemDictionary's answers
  // can vary over time but the compiler needs consistency.
  ciSymbol* name = get_object(klass()->klass_part()->name())->as_symbol();
  ciKlass* unloaded_klass = check_get_unloaded_klass(accessor, name);
  if (unloaded_klass != NULL) {
    is_accessible = false;
    return unloaded_klass;
  }

  // It is known to be accessible, since it was found in the constant pool.
  is_accessible = true;
  return get_object(klass())->as_klass();
}

// ------------------------------------------------------------------
// ciEnv::get_klass_by_index
//
// Get a klass from the constant pool.
ciKlass* ciEnv::get_klass_by_index(constantPoolHandle cpool,
                                   int index,
                                   bool& is_accessible,
                                   ciInstanceKlass* accessor) {
  GUARDED_VM_ENTRY(return get_klass_by_index_impl(cpool, index, is_accessible, accessor);)
}

// ------------------------------------------------------------------
// ciEnv::get_constant_by_index_impl
//
// Implementation of get_constant_by_index().
ciConstant ciEnv::get_constant_by_index_impl(constantPoolHandle cpool,
                                             int index,
                                             ciInstanceKlass* accessor) {
  EXCEPTION_CONTEXT;
  constantTag tag = cpool->tag_at(index);
  if (tag.is_int()) {
    return ciConstant(T_INT, (jint)cpool->int_at(index));
  } else if (tag.is_long()) {
    return ciConstant((jlong)cpool->long_at(index));
  } else if (tag.is_float()) {
    return ciConstant((jfloat)cpool->float_at(index));
  } else if (tag.is_double()) {
    return ciConstant((jdouble)cpool->double_at(index));
  } else if (tag.is_string() || tag.is_unresolved_string()) {
    oop string = NULL;
    if (cpool->is_pseudo_string_at(index)) {
      string = cpool->pseudo_string_at(index);
    } else {
      string = cpool->string_at(index, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        record_out_of_memory_failure();
        return ciConstant();
      }
    }
    ciObject* constant = get_object(string);
    assert (constant->is_instance(), "must be an instance, or not? ");
    return ciConstant(T_OBJECT, constant);
  } else if (tag.is_klass() || tag.is_unresolved_klass()) {
    // 4881222: allow ldc to take a class type
    bool ignore;
    ciKlass* klass = get_klass_by_index_impl(cpool, index, ignore, accessor);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
      record_out_of_memory_failure();
      return ciConstant();
    }
    assert (klass->is_instance_klass() || klass->is_array_klass(),
            "must be an instance or array klass ");
    return ciConstant(T_OBJECT, klass);
  } else if (tag.is_object()) {
    oop obj = cpool->object_at(index);
    assert(obj->is_instance(), "must be an instance");
    ciObject* ciobj = get_object(obj);
    return ciConstant(T_OBJECT, ciobj);
  } else {
    ShouldNotReachHere();
    return ciConstant();
  }
}

// ------------------------------------------------------------------
// ciEnv::is_unresolved_string_impl
//
// Implementation of is_unresolved_string().
bool ciEnv::is_unresolved_string_impl(instanceKlass* accessor, int index) const {
  EXCEPTION_CONTEXT;
  assert(accessor->is_linked(), "must be linked before accessing constant pool");
  constantPoolOop cpool = accessor->constants();
  constantTag tag = cpool->tag_at(index);
  return tag.is_unresolved_string();
}

// ------------------------------------------------------------------
// ciEnv::is_unresolved_klass_impl
//
// Implementation of is_unresolved_klass().
bool ciEnv::is_unresolved_klass_impl(instanceKlass* accessor, int index) const {
  EXCEPTION_CONTEXT;
  assert(accessor->is_linked(), "must be linked before accessing constant pool");
  constantPoolOop cpool = accessor->constants();
  constantTag tag = cpool->tag_at(index);
  return tag.is_unresolved_klass();
}

// ------------------------------------------------------------------
// ciEnv::get_constant_by_index
//
// Pull a constant out of the constant pool.  How appropriate.
//
// Implementation note: this query is currently in no way cached.
ciConstant ciEnv::get_constant_by_index(constantPoolHandle cpool,
                                        int index,
                                        ciInstanceKlass* accessor) {
  GUARDED_VM_ENTRY(return get_constant_by_index_impl(cpool, index, accessor);)
}

// ------------------------------------------------------------------
// ciEnv::is_unresolved_string
//
// Check constant pool
//
// Implementation note: this query is currently in no way cached.
bool ciEnv::is_unresolved_string(ciInstanceKlass* accessor,
                                 int index) const {
  GUARDED_VM_ENTRY(return is_unresolved_string_impl(accessor->get_instanceKlass(), index); )
}

// ------------------------------------------------------------------
// ciEnv::is_unresolved_klass
//
// Check constant pool
//
// Implementation note: this query is currently in no way cached.
bool ciEnv::is_unresolved_klass(ciInstanceKlass* accessor,
                                int index) const {
  GUARDED_VM_ENTRY(return is_unresolved_klass_impl(accessor->get_instanceKlass(), index); )
}

// ------------------------------------------------------------------
// ciEnv::get_field_by_index_impl
//
// Implementation of get_field_by_index.
//
// Implementation note: the results of field lookups are cached
// in the accessor klass.
ciField* ciEnv::get_field_by_index_impl(ciInstanceKlass* accessor,
                                        int index) {
  ciConstantPoolCache* cache = accessor->field_cache();
  if (cache == NULL) {
    ciField* field = new (arena()) ciField(accessor, index);
    return field;
  } else {
    ciField* field = (ciField*)cache->get(index);
    if (field == NULL) {
      field = new (arena()) ciField(accessor, index);
      cache->insert(index, field);
    }
    return field;
  }
}

// ------------------------------------------------------------------
// ciEnv::get_field_by_index
//
// Get a field by index from a klass's constant pool.
ciField* ciEnv::get_field_by_index(ciInstanceKlass* accessor,
                                   int index) {
  GUARDED_VM_ENTRY(return get_field_by_index_impl(accessor, index);)
}

// ------------------------------------------------------------------
// ciEnv::lookup_method
//
// Perform an appropriate method lookup based on accessor, holder,
// name, signature, and bytecode.
methodOop ciEnv::lookup_method(instanceKlass*  accessor,
                               instanceKlass*  holder,
                               symbolOop       name,
                               symbolOop       sig,
                               Bytecodes::Code bc) {
  EXCEPTION_CONTEXT;
  KlassHandle h_accessor(THREAD, accessor);
  KlassHandle h_holder(THREAD, holder);
  symbolHandle h_name(THREAD, name);
  symbolHandle h_sig(THREAD, sig);
  LinkResolver::check_klass_accessability(h_accessor, h_holder, KILL_COMPILE_ON_FATAL_(NULL));
  methodHandle dest_method;
  switch (bc) {
  case Bytecodes::_invokestatic:
    dest_method =
      LinkResolver::resolve_static_call_or_null(h_holder, h_name, h_sig, h_accessor);
    break;
  case Bytecodes::_invokespecial:
    dest_method =
      LinkResolver::resolve_special_call_or_null(h_holder, h_name, h_sig, h_accessor);
    break;
  case Bytecodes::_invokeinterface:
    dest_method =
      LinkResolver::linktime_resolve_interface_method_or_null(h_holder, h_name, h_sig,
                                                              h_accessor, true);
    break;
  case Bytecodes::_invokevirtual:
    dest_method =
      LinkResolver::linktime_resolve_virtual_method_or_null(h_holder, h_name, h_sig,
                                                            h_accessor, true);
    break;
  default: ShouldNotReachHere();
  }

  return dest_method();
}


// ------------------------------------------------------------------
// ciEnv::get_method_by_index_impl
ciMethod* ciEnv::get_method_by_index_impl(constantPoolHandle cpool,
                                          int index, Bytecodes::Code bc,
                                          ciInstanceKlass* accessor) {
  int holder_index = cpool->klass_ref_index_at(index);
  bool holder_is_accessible;
  ciKlass* holder = get_klass_by_index_impl(cpool, holder_index, holder_is_accessible, accessor);
  ciInstanceKlass* declared_holder = get_instance_klass_for_declared_method_holder(holder);

  // Get the method's name and signature.
  symbolOop name_sym = cpool->name_ref_at(index);
  symbolOop sig_sym  = cpool->signature_ref_at(index);

  if (holder_is_accessible) { // Our declared holder is loaded.
    instanceKlass* lookup = declared_holder->get_instanceKlass();
    methodOop m = lookup_method(accessor->get_instanceKlass(), lookup, name_sym, sig_sym, bc);
    if (m != NULL) {
      // We found the method.
      return get_object(m)->as_method();
    }
  }

  // Either the declared holder was not loaded, or the method could
  // not be found.  Create a dummy ciMethod to represent the failed
  // lookup.

  return get_unloaded_method(declared_holder,
                             get_object(name_sym)->as_symbol(),
                             get_object(sig_sym)->as_symbol());
}


// ------------------------------------------------------------------
// ciEnv::get_fake_invokedynamic_method_impl
ciMethod* ciEnv::get_fake_invokedynamic_method_impl(constantPoolHandle cpool,
                                                    int index, Bytecodes::Code bc) {
  // Compare the following logic with InterpreterRuntime::resolve_invokedynamic.
  assert(bc == Bytecodes::_invokedynamic, "must be invokedynamic");

  bool is_resolved = cpool->cache()->main_entry_at(index)->is_resolved(bc);
  if (is_resolved && (oop) cpool->cache()->secondary_entry_at(index)->f1() == NULL)
    // FIXME: code generation could allow for null (unlinked) call site
    is_resolved = false;

  // Call site might not be resolved yet.  We could create a real invoker method from the
  // compiler, but it is simpler to stop the code path here with an unlinked method.
  if (!is_resolved) {
    ciInstanceKlass* mh_klass = get_object(SystemDictionary::MethodHandle_klass())->as_instance_klass();
    ciSymbol*        sig_sym  = get_object(cpool->signature_ref_at(index))->as_symbol();
    return get_unloaded_method(mh_klass, ciSymbol::invokeExact_name(), sig_sym);
  }

  // Get the invoker methodOop from the constant pool.
  intptr_t f2_value = cpool->cache()->main_entry_at(index)->f2();
  methodOop signature_invoker = methodOop(f2_value);
  assert(signature_invoker != NULL && signature_invoker->is_method() && signature_invoker->is_method_handle_invoke(),
         "correct result from LinkResolver::resolve_invokedynamic");

  return get_object(signature_invoker)->as_method();
}


// ------------------------------------------------------------------
// ciEnv::get_instance_klass_for_declared_method_holder
ciInstanceKlass* ciEnv::get_instance_klass_for_declared_method_holder(ciKlass* method_holder) {
  // For the case of <array>.clone(), the method holder can be a ciArrayKlass
  // instead of a ciInstanceKlass.  For that case simply pretend that the
  // declared holder is Object.clone since that's where the call will bottom out.
  // A more correct fix would trickle out through many interfaces in CI,
  // requiring ciInstanceKlass* to become ciKlass* and many more places would
  // require checks to make sure the expected type was found.  Given that this
  // only occurs for clone() the more extensive fix seems like overkill so
  // instead we simply smear the array type into Object.
  if (method_holder->is_instance_klass()) {
    return method_holder->as_instance_klass();
  } else if (method_holder->is_array_klass()) {
    return current()->Object_klass();
  } else {
    ShouldNotReachHere();
  }
  return NULL;
}


// ------------------------------------------------------------------
// ciEnv::get_method_by_index
ciMethod* ciEnv::get_method_by_index(constantPoolHandle cpool,
                                     int index, Bytecodes::Code bc,
                                     ciInstanceKlass* accessor) {
  if (bc == Bytecodes::_invokedynamic) {
    GUARDED_VM_ENTRY(return get_fake_invokedynamic_method_impl(cpool, index, bc);)
  } else {
    GUARDED_VM_ENTRY(return get_method_by_index_impl(cpool, index, bc, accessor);)
  }
}


// ------------------------------------------------------------------
// ciEnv::name_buffer
char *ciEnv::name_buffer(int req_len) {
  if (_name_buffer_len < req_len) {
    if (_name_buffer == NULL) {
      _name_buffer = (char*)arena()->Amalloc(sizeof(char)*req_len);
      _name_buffer_len = req_len;
    } else {
      _name_buffer =
        (char*)arena()->Arealloc(_name_buffer, _name_buffer_len, req_len);
      _name_buffer_len = req_len;
    }
  }
  return _name_buffer;
}

// ------------------------------------------------------------------
// ciEnv::is_in_vm
bool ciEnv::is_in_vm() {
  return JavaThread::current()->thread_state() == _thread_in_vm;
}

bool ciEnv::system_dictionary_modification_counter_changed() {
  return _system_dictionary_modification_counter != SystemDictionary::number_of_modifications();
}

// ------------------------------------------------------------------
// ciEnv::check_for_system_dictionary_modification
// Check for changes to the system dictionary during compilation
// class loads, evolution, breakpoints
void ciEnv::check_for_system_dictionary_modification(ciMethod* target) {
  if (failing())  return;  // no need for further checks

  // Dependencies must be checked when the system dictionary changes.
  // If logging is enabled all violated dependences will be recorded in
  // the log.  In debug mode check dependencies even if the system
  // dictionary hasn't changed to verify that no invalid dependencies
  // were inserted.  Any violated dependences in this case are dumped to
  // the tty.

  bool counter_changed = system_dictionary_modification_counter_changed();
  bool test_deps = counter_changed;
  DEBUG_ONLY(test_deps = true);
  if (!test_deps)  return;

  bool print_failures = false;
  DEBUG_ONLY(print_failures = !counter_changed);

  bool keep_going = (print_failures || xtty != NULL);

  int violated = 0;

  for (Dependencies::DepStream deps(dependencies()); deps.next(); ) {
    klassOop witness = deps.check_dependency();
    if (witness != NULL) {
      ++violated;
      if (print_failures)  deps.print_dependency(witness, /*verbose=*/ true);
      // If there's no log and we're not sanity-checking, we're done.
      if (!keep_going)     break;
    }
  }

  if (violated != 0) {
    assert(counter_changed, "failed dependencies, but counter didn't change");
    record_failure("concurrent class loading");
  }
}

// ------------------------------------------------------------------
// ciEnv::register_method
void ciEnv::register_method(ciMethod* target,
                            int entry_bci,
                            CodeOffsets* offsets,
                            int orig_pc_offset,
                            CodeBuffer* code_buffer,
                            int frame_words,
                            OopMapSet* oop_map_set,
                            ExceptionHandlerTable* handler_table,
                            ImplicitExceptionTable* inc_table,
                            AbstractCompiler* compiler,
                            int comp_level,
                            bool has_debug_info,
                            bool has_unsafe_access) {
  VM_ENTRY_MARK;
  nmethod* nm = NULL;
  {
    // To prevent compile queue updates.
    MutexLocker locker(MethodCompileQueue_lock, THREAD);

    // Prevent SystemDictionary::add_to_hierarchy from running
    // and invalidating our dependencies until we install this method.
    MutexLocker ml(Compile_lock);

    // Change in Jvmti state may invalidate compilation.
    if (!failing() &&
        ( (!jvmti_can_hotswap_or_post_breakpoint() &&
           JvmtiExport::can_hotswap_or_post_breakpoint()) ||
          (!jvmti_can_access_local_variables() &&
           JvmtiExport::can_access_local_variables()) ||
          (!jvmti_can_post_on_exceptions() &&
           JvmtiExport::can_post_on_exceptions()) )) {
      record_failure("Jvmti state change invalidated dependencies");
    }

    // Change in DTrace flags may invalidate compilation.
    if (!failing() &&
        ( (!dtrace_extended_probes() && ExtendedDTraceProbes) ||
          (!dtrace_method_probes() && DTraceMethodProbes) ||
          (!dtrace_alloc_probes() && DTraceAllocProbes) )) {
      record_failure("DTrace flags change invalidated dependencies");
    }

    if (!failing()) {
      if (log() != NULL) {
        // Log the dependencies which this compilation declares.
        dependencies()->log_all_dependencies();
      }

      // Encode the dependencies now, so we can check them right away.
      dependencies()->encode_content_bytes();

      // Check for {class loads, evolution, breakpoints} during compilation
      check_for_system_dictionary_modification(target);
    }

    methodHandle method(THREAD, target->get_methodOop());

    if (failing()) {
      // While not a true deoptimization, it is a preemptive decompile.
      methodDataOop mdo = method()->method_data();
      if (mdo != NULL) {
        mdo->inc_decompile_count();
      }

      // All buffers in the CodeBuffer are allocated in the CodeCache.
      // If the code buffer is created on each compile attempt
      // as in C2, then it must be freed.
      code_buffer->free_blob();
      return;
    }

    assert(offsets->value(CodeOffsets::Deopt) != -1, "must have deopt entry");
    assert(offsets->value(CodeOffsets::Exceptions) != -1, "must have exception entry");

    nm =  nmethod::new_nmethod(method,
                               compile_id(),
                               entry_bci,
                               offsets,
                               orig_pc_offset,
                               debug_info(), dependencies(), code_buffer,
                               frame_words, oop_map_set,
                               handler_table, inc_table,
                               compiler, comp_level);

    // Free codeBlobs
    code_buffer->free_blob();

    // stress test 6243940 by immediately making the method
    // non-entrant behind the system's back. This has serious
    // side effects on the code cache and is not meant for
    // general stress testing
    if (nm != NULL && StressNonEntrant) {
      MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);
      NativeJump::patch_verified_entry(nm->entry_point(), nm->verified_entry_point(),
                  SharedRuntime::get_handle_wrong_method_stub());
    }

    if (nm == NULL) {
      // The CodeCache is full.  Print out warning and disable compilation.
      record_failure("code cache is full");
      {
        MutexUnlocker ml(Compile_lock);
        MutexUnlocker locker(MethodCompileQueue_lock);
        CompileBroker::handle_full_code_cache();
      }
    } else {
      NOT_PRODUCT(nm->set_has_debug_info(has_debug_info); )
      nm->set_has_unsafe_access(has_unsafe_access);

      // Record successful registration.
      // (Put nm into the task handle *before* publishing to the Java heap.)
      if (task() != NULL)  task()->set_code(nm);

      if (entry_bci == InvocationEntryBci) {
#ifdef TIERED
        // If there is an old version we're done with it
        nmethod* old = method->code();
        if (TraceMethodReplacement && old != NULL) {
          ResourceMark rm;
          char *method_name = method->name_and_sig_as_C_string();
          tty->print_cr("Replacing method %s", method_name);
        }
        if (old != NULL ) {
          old->make_not_entrant();
        }
#endif // TIERED
        if (TraceNMethodInstalls ) {
          ResourceMark rm;
          char *method_name = method->name_and_sig_as_C_string();
          ttyLocker ttyl;
          tty->print_cr("Installing method (%d) %s ",
                        comp_level,
                        method_name);
        }
        // Allow the code to be executed
        method->set_code(method, nm);
      } else {
        if (TraceNMethodInstalls ) {
          ResourceMark rm;
          char *method_name = method->name_and_sig_as_C_string();
          ttyLocker ttyl;
          tty->print_cr("Installing osr method (%d) %s @ %d",
                        comp_level,
                        method_name,
                        entry_bci);
        }
        instanceKlass::cast(method->method_holder())->add_osr_nmethod(nm);

      }
    }
  }
  // JVMTI -- compiled method notification (must be done outside lock)
  if (nm != NULL) {
    nm->post_compiled_method_load_event();
  }

}


// ------------------------------------------------------------------
// ciEnv::find_system_klass
ciKlass* ciEnv::find_system_klass(ciSymbol* klass_name) {
  VM_ENTRY_MARK;
  return get_klass_by_name_impl(NULL, klass_name, false);
}

// ------------------------------------------------------------------
// ciEnv::comp_level
int ciEnv::comp_level() {
  if (task() == NULL)  return CompLevel_full_optimization;
  return task()->comp_level();
}

// ------------------------------------------------------------------
// ciEnv::compile_id
uint ciEnv::compile_id() {
  if (task() == NULL)  return 0;
  return task()->compile_id();
}

// ------------------------------------------------------------------
// ciEnv::notice_inlined_method()
void ciEnv::notice_inlined_method(ciMethod* method) {
  _num_inlined_bytecodes += method->code_size();
}

// ------------------------------------------------------------------
// ciEnv::num_inlined_bytecodes()
int ciEnv::num_inlined_bytecodes() const {
  return _num_inlined_bytecodes;
}

// ------------------------------------------------------------------
// ciEnv::record_failure()
void ciEnv::record_failure(const char* reason) {
  if (log() != NULL) {
    log()->elem("failure reason='%s'", reason);
  }
  if (_failure_reason == NULL) {
    // Record the first failure reason.
    _failure_reason = reason;
  }
}

// ------------------------------------------------------------------
// ciEnv::record_method_not_compilable()
void ciEnv::record_method_not_compilable(const char* reason, bool all_tiers) {
  int new_compilable =
    all_tiers ? MethodCompilable_never : MethodCompilable_not_at_tier ;

  // Only note transitions to a worse state
  if (new_compilable > _compilable) {
    if (log() != NULL) {
      if (all_tiers) {
        log()->elem("method_not_compilable");
      } else {
        log()->elem("method_not_compilable_at_tier");
      }
    }
    _compilable = new_compilable;

    // Reset failure reason; this one is more important.
    _failure_reason = NULL;
    record_failure(reason);
  }
}

// ------------------------------------------------------------------
// ciEnv::record_out_of_memory_failure()
void ciEnv::record_out_of_memory_failure() {
  // If memory is low, we stop compiling methods.
  record_method_not_compilable("out of memory");
}

/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/dependencies.hpp"
#include "gc/shared/cardTableModRefBS.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/generation.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/space.hpp"
#include "interpreter/interpreter.hpp"
#include "logging/log.hpp"
#include "memory/filemap.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "memory/universe.inline.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/commandLineFlagConstraintList.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timer.hpp"
#include "runtime/vm_operations.hpp"
#include "services/memoryService.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/preserveException.hpp"
#if INCLUDE_ALL_GCS
#include "gc/cms/cmsCollectorPolicy.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/shared/adaptiveSizePolicy.hpp"
#endif // INCLUDE_ALL_GCS
#if INCLUDE_CDS
#include "classfile/sharedClassUtil.hpp"
#endif

// Known objects
Klass* Universe::_boolArrayKlassObj                 = NULL;
Klass* Universe::_byteArrayKlassObj                 = NULL;
Klass* Universe::_charArrayKlassObj                 = NULL;
Klass* Universe::_intArrayKlassObj                  = NULL;
Klass* Universe::_shortArrayKlassObj                = NULL;
Klass* Universe::_longArrayKlassObj                 = NULL;
Klass* Universe::_singleArrayKlassObj               = NULL;
Klass* Universe::_doubleArrayKlassObj               = NULL;
Klass* Universe::_typeArrayKlassObjs[T_VOID+1]      = { NULL /*, NULL...*/ };
Klass* Universe::_objectArrayKlassObj               = NULL;
oop Universe::_int_mirror                             = NULL;
oop Universe::_float_mirror                           = NULL;
oop Universe::_double_mirror                          = NULL;
oop Universe::_byte_mirror                            = NULL;
oop Universe::_bool_mirror                            = NULL;
oop Universe::_char_mirror                            = NULL;
oop Universe::_long_mirror                            = NULL;
oop Universe::_short_mirror                           = NULL;
oop Universe::_void_mirror                            = NULL;
oop Universe::_mirrors[T_VOID+1]                      = { NULL /*, NULL...*/ };
oop Universe::_main_thread_group                      = NULL;
oop Universe::_system_thread_group                    = NULL;
objArrayOop Universe::_the_empty_class_klass_array    = NULL;
Array<Klass*>* Universe::_the_array_interfaces_array = NULL;
oop Universe::_the_null_string                        = NULL;
oop Universe::_the_min_jint_string                   = NULL;
LatestMethodCache* Universe::_finalizer_register_cache = NULL;
LatestMethodCache* Universe::_loader_addClass_cache    = NULL;
LatestMethodCache* Universe::_pd_implies_cache         = NULL;
LatestMethodCache* Universe::_throw_illegal_access_error_cache = NULL;
LatestMethodCache* Universe::_do_stack_walk_cache     = NULL;
oop Universe::_out_of_memory_error_java_heap          = NULL;
oop Universe::_out_of_memory_error_metaspace          = NULL;
oop Universe::_out_of_memory_error_class_metaspace    = NULL;
oop Universe::_out_of_memory_error_array_size         = NULL;
oop Universe::_out_of_memory_error_gc_overhead_limit  = NULL;
oop Universe::_out_of_memory_error_realloc_objects    = NULL;
oop Universe::_delayed_stack_overflow_error_message   = NULL;
objArrayOop Universe::_preallocated_out_of_memory_error_array = NULL;
volatile jint Universe::_preallocated_out_of_memory_error_avail_count = 0;
bool Universe::_verify_in_progress                    = false;
long Universe::verify_flags                           = Universe::Verify_All;
oop Universe::_null_ptr_exception_instance            = NULL;
oop Universe::_arithmetic_exception_instance          = NULL;
oop Universe::_virtual_machine_error_instance         = NULL;
oop Universe::_vm_exception                           = NULL;
oop Universe::_allocation_context_notification_obj    = NULL;

Array<int>* Universe::_the_empty_int_array            = NULL;
Array<u2>* Universe::_the_empty_short_array           = NULL;
Array<Klass*>* Universe::_the_empty_klass_array     = NULL;
Array<Method*>* Universe::_the_empty_method_array   = NULL;

// These variables are guarded by FullGCALot_lock.
debug_only(objArrayOop Universe::_fullgc_alot_dummy_array = NULL;)
debug_only(int Universe::_fullgc_alot_dummy_next      = 0;)

// Heap
int             Universe::_verify_count = 0;

// Oop verification (see MacroAssembler::verify_oop)
uintptr_t       Universe::_verify_oop_mask = 0;
uintptr_t       Universe::_verify_oop_bits = (uintptr_t) -1;

int             Universe::_base_vtable_size = 0;
bool            Universe::_bootstrapping = false;
bool            Universe::_fully_initialized = false;

size_t          Universe::_heap_capacity_at_last_gc;
size_t          Universe::_heap_used_at_last_gc = 0;

CollectedHeap*  Universe::_collectedHeap = NULL;

NarrowPtrStruct Universe::_narrow_oop = { NULL, 0, true };
NarrowPtrStruct Universe::_narrow_klass = { NULL, 0, true };
address Universe::_narrow_ptrs_base;

void Universe::basic_type_classes_do(void f(Klass*)) {
  f(boolArrayKlassObj());
  f(byteArrayKlassObj());
  f(charArrayKlassObj());
  f(intArrayKlassObj());
  f(shortArrayKlassObj());
  f(longArrayKlassObj());
  f(singleArrayKlassObj());
  f(doubleArrayKlassObj());
}

void Universe::oops_do(OopClosure* f, bool do_all) {

  f->do_oop((oop*) &_int_mirror);
  f->do_oop((oop*) &_float_mirror);
  f->do_oop((oop*) &_double_mirror);
  f->do_oop((oop*) &_byte_mirror);
  f->do_oop((oop*) &_bool_mirror);
  f->do_oop((oop*) &_char_mirror);
  f->do_oop((oop*) &_long_mirror);
  f->do_oop((oop*) &_short_mirror);
  f->do_oop((oop*) &_void_mirror);

  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    f->do_oop((oop*) &_mirrors[i]);
  }
  assert(_mirrors[0] == NULL && _mirrors[T_BOOLEAN - 1] == NULL, "checking");

  f->do_oop((oop*)&_the_empty_class_klass_array);
  f->do_oop((oop*)&_the_null_string);
  f->do_oop((oop*)&_the_min_jint_string);
  f->do_oop((oop*)&_out_of_memory_error_java_heap);
  f->do_oop((oop*)&_out_of_memory_error_metaspace);
  f->do_oop((oop*)&_out_of_memory_error_class_metaspace);
  f->do_oop((oop*)&_out_of_memory_error_array_size);
  f->do_oop((oop*)&_out_of_memory_error_gc_overhead_limit);
  f->do_oop((oop*)&_out_of_memory_error_realloc_objects);
  f->do_oop((oop*)&_delayed_stack_overflow_error_message);
  f->do_oop((oop*)&_preallocated_out_of_memory_error_array);
  f->do_oop((oop*)&_null_ptr_exception_instance);
  f->do_oop((oop*)&_arithmetic_exception_instance);
  f->do_oop((oop*)&_virtual_machine_error_instance);
  f->do_oop((oop*)&_main_thread_group);
  f->do_oop((oop*)&_system_thread_group);
  f->do_oop((oop*)&_vm_exception);
  f->do_oop((oop*)&_allocation_context_notification_obj);
  debug_only(f->do_oop((oop*)&_fullgc_alot_dummy_array);)
}

// Serialize metadata in and out of CDS archive, not oops.
void Universe::serialize(SerializeClosure* f, bool do_all) {

  f->do_ptr((void**)&_boolArrayKlassObj);
  f->do_ptr((void**)&_byteArrayKlassObj);
  f->do_ptr((void**)&_charArrayKlassObj);
  f->do_ptr((void**)&_intArrayKlassObj);
  f->do_ptr((void**)&_shortArrayKlassObj);
  f->do_ptr((void**)&_longArrayKlassObj);
  f->do_ptr((void**)&_singleArrayKlassObj);
  f->do_ptr((void**)&_doubleArrayKlassObj);
  f->do_ptr((void**)&_objectArrayKlassObj);

  {
    for (int i = 0; i < T_VOID+1; i++) {
      if (_typeArrayKlassObjs[i] != NULL) {
        assert(i >= T_BOOLEAN, "checking");
        f->do_ptr((void**)&_typeArrayKlassObjs[i]);
      } else if (do_all) {
        f->do_ptr((void**)&_typeArrayKlassObjs[i]);
      }
    }
  }

  f->do_ptr((void**)&_the_array_interfaces_array);
  f->do_ptr((void**)&_the_empty_int_array);
  f->do_ptr((void**)&_the_empty_short_array);
  f->do_ptr((void**)&_the_empty_method_array);
  f->do_ptr((void**)&_the_empty_klass_array);
  _finalizer_register_cache->serialize(f);
  _loader_addClass_cache->serialize(f);
  _pd_implies_cache->serialize(f);
  _throw_illegal_access_error_cache->serialize(f);
  _do_stack_walk_cache->serialize(f);
}

void Universe::check_alignment(uintx size, uintx alignment, const char* name) {
  if (size < alignment || size % alignment != 0) {
    vm_exit_during_initialization(
      err_msg("Size of %s (" UINTX_FORMAT " bytes) must be aligned to " UINTX_FORMAT " bytes", name, size, alignment));
  }
}

void initialize_basic_type_klass(Klass* k, TRAPS) {
  Klass* ok = SystemDictionary::Object_klass();
  if (UseSharedSpaces) {
    ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
    assert(k->super() == ok, "u3");
    k->restore_unshareable_info(loader_data, Handle(), CHECK);
  } else {
    k->initialize_supers(ok, CHECK);
  }
  k->append_to_sibling_list();
}

void Universe::genesis(TRAPS) {
  ResourceMark rm;

  { FlagSetting fs(_bootstrapping, true);

    { MutexLocker mc(Compile_lock);

      // determine base vtable size; without that we cannot create the array klasses
      compute_base_vtable_size();

      if (!UseSharedSpaces) {
        _boolArrayKlassObj      = TypeArrayKlass::create_klass(T_BOOLEAN, sizeof(jboolean), CHECK);
        _charArrayKlassObj      = TypeArrayKlass::create_klass(T_CHAR,    sizeof(jchar),    CHECK);
        _singleArrayKlassObj    = TypeArrayKlass::create_klass(T_FLOAT,   sizeof(jfloat),   CHECK);
        _doubleArrayKlassObj    = TypeArrayKlass::create_klass(T_DOUBLE,  sizeof(jdouble),  CHECK);
        _byteArrayKlassObj      = TypeArrayKlass::create_klass(T_BYTE,    sizeof(jbyte),    CHECK);
        _shortArrayKlassObj     = TypeArrayKlass::create_klass(T_SHORT,   sizeof(jshort),   CHECK);
        _intArrayKlassObj       = TypeArrayKlass::create_klass(T_INT,     sizeof(jint),     CHECK);
        _longArrayKlassObj      = TypeArrayKlass::create_klass(T_LONG,    sizeof(jlong),    CHECK);

        _typeArrayKlassObjs[T_BOOLEAN] = _boolArrayKlassObj;
        _typeArrayKlassObjs[T_CHAR]    = _charArrayKlassObj;
        _typeArrayKlassObjs[T_FLOAT]   = _singleArrayKlassObj;
        _typeArrayKlassObjs[T_DOUBLE]  = _doubleArrayKlassObj;
        _typeArrayKlassObjs[T_BYTE]    = _byteArrayKlassObj;
        _typeArrayKlassObjs[T_SHORT]   = _shortArrayKlassObj;
        _typeArrayKlassObjs[T_INT]     = _intArrayKlassObj;
        _typeArrayKlassObjs[T_LONG]    = _longArrayKlassObj;

        ClassLoaderData* null_cld = ClassLoaderData::the_null_class_loader_data();

        _the_array_interfaces_array = MetadataFactory::new_array<Klass*>(null_cld, 2, NULL, CHECK);
        _the_empty_int_array        = MetadataFactory::new_array<int>(null_cld, 0, CHECK);
        _the_empty_short_array      = MetadataFactory::new_array<u2>(null_cld, 0, CHECK);
        _the_empty_method_array     = MetadataFactory::new_array<Method*>(null_cld, 0, CHECK);
        _the_empty_klass_array      = MetadataFactory::new_array<Klass*>(null_cld, 0, CHECK);
      }
    }

    vmSymbols::initialize(CHECK);

    SystemDictionary::initialize(CHECK);

    Klass* ok = SystemDictionary::Object_klass();

    _the_null_string            = StringTable::intern("null", CHECK);
    _the_min_jint_string       = StringTable::intern("-2147483648", CHECK);

    if (UseSharedSpaces) {
      // Verify shared interfaces array.
      assert(_the_array_interfaces_array->at(0) ==
             SystemDictionary::Cloneable_klass(), "u3");
      assert(_the_array_interfaces_array->at(1) ==
             SystemDictionary::Serializable_klass(), "u3");
      MetaspaceShared::fixup_shared_string_regions();
    } else {
      // Set up shared interfaces array.  (Do this before supers are set up.)
      _the_array_interfaces_array->at_put(0, SystemDictionary::Cloneable_klass());
      _the_array_interfaces_array->at_put(1, SystemDictionary::Serializable_klass());
    }

    initialize_basic_type_klass(boolArrayKlassObj(), CHECK);
    initialize_basic_type_klass(charArrayKlassObj(), CHECK);
    initialize_basic_type_klass(singleArrayKlassObj(), CHECK);
    initialize_basic_type_klass(doubleArrayKlassObj(), CHECK);
    initialize_basic_type_klass(byteArrayKlassObj(), CHECK);
    initialize_basic_type_klass(shortArrayKlassObj(), CHECK);
    initialize_basic_type_klass(intArrayKlassObj(), CHECK);
    initialize_basic_type_klass(longArrayKlassObj(), CHECK);
  } // end of core bootstrapping

  // Maybe this could be lifted up now that object array can be initialized
  // during the bootstrapping.

  // OLD
  // Initialize _objectArrayKlass after core bootstraping to make
  // sure the super class is set up properly for _objectArrayKlass.
  // ---
  // NEW
  // Since some of the old system object arrays have been converted to
  // ordinary object arrays, _objectArrayKlass will be loaded when
  // SystemDictionary::initialize(CHECK); is run. See the extra check
  // for Object_klass_loaded in objArrayKlassKlass::allocate_objArray_klass_impl.
  _objectArrayKlassObj = InstanceKlass::
    cast(SystemDictionary::Object_klass())->array_klass(1, CHECK);
  // OLD
  // Add the class to the class hierarchy manually to make sure that
  // its vtable is initialized after core bootstrapping is completed.
  // ---
  // New
  // Have already been initialized.
  _objectArrayKlassObj->append_to_sibling_list();

  #ifdef ASSERT
  if (FullGCALot) {
    // Allocate an array of dummy objects.
    // We'd like these to be at the bottom of the old generation,
    // so that when we free one and then collect,
    // (almost) the whole heap moves
    // and we find out if we actually update all the oops correctly.
    // But we can't allocate directly in the old generation,
    // so we allocate wherever, and hope that the first collection
    // moves these objects to the bottom of the old generation.
    // We can allocate directly in the permanent generation, so we do.
    int size;
    if (UseConcMarkSweepGC) {
      warning("Using +FullGCALot with concurrent mark sweep gc "
              "will not force all objects to relocate");
      size = FullGCALotDummies;
    } else {
      size = FullGCALotDummies * 2;
    }
    objArrayOop    naked_array = oopFactory::new_objArray(SystemDictionary::Object_klass(), size, CHECK);
    objArrayHandle dummy_array(THREAD, naked_array);
    int i = 0;
    while (i < size) {
        // Allocate dummy in old generation
      oop dummy = SystemDictionary::Object_klass()->allocate_instance(CHECK);
      dummy_array->obj_at_put(i++, dummy);
    }
    {
      // Only modify the global variable inside the mutex.
      // If we had a race to here, the other dummy_array instances
      // and their elements just get dropped on the floor, which is fine.
      MutexLocker ml(FullGCALot_lock);
      if (_fullgc_alot_dummy_array == NULL) {
        _fullgc_alot_dummy_array = dummy_array();
      }
    }
    assert(i == _fullgc_alot_dummy_array->length(), "just checking");
  }
  #endif

  // Initialize dependency array for null class loader
  ClassLoaderData::the_null_class_loader_data()->init_dependencies(CHECK);

}

// CDS support for patching vtables in metadata in the shared archive.
// All types inherited from Metadata have vtables, but not types inherited
// from MetaspaceObj, because the latter does not have virtual functions.
// If the metadata type has a vtable, it cannot be shared in the read-only
// section of the CDS archive, because the vtable pointer is patched.
static inline void add_vtable(void** list, int* n, void* o, int count) {
  guarantee((*n) < count, "vtable list too small");
  void* vtable = dereference_vptr(o);
  assert(*(void**)(vtable) != NULL, "invalid vtable");
  list[(*n)++] = vtable;
}

void Universe::init_self_patching_vtbl_list(void** list, int count) {
  int n = 0;
  { InstanceKlass o;          add_vtable(list, &n, &o, count); }
  { InstanceClassLoaderKlass o; add_vtable(list, &n, &o, count); }
  { InstanceMirrorKlass o;    add_vtable(list, &n, &o, count); }
  { InstanceRefKlass o;       add_vtable(list, &n, &o, count); }
  { TypeArrayKlass o;         add_vtable(list, &n, &o, count); }
  { ObjArrayKlass o;          add_vtable(list, &n, &o, count); }
  { Method o;                 add_vtable(list, &n, &o, count); }
  { ConstantPool o;           add_vtable(list, &n, &o, count); }
}

void Universe::initialize_basic_type_mirrors(TRAPS) {
    assert(_int_mirror==NULL, "basic type mirrors already initialized");
    _int_mirror     =
      java_lang_Class::create_basic_type_mirror("int",    T_INT, CHECK);
    _float_mirror   =
      java_lang_Class::create_basic_type_mirror("float",  T_FLOAT,   CHECK);
    _double_mirror  =
      java_lang_Class::create_basic_type_mirror("double", T_DOUBLE,  CHECK);
    _byte_mirror    =
      java_lang_Class::create_basic_type_mirror("byte",   T_BYTE, CHECK);
    _bool_mirror    =
      java_lang_Class::create_basic_type_mirror("boolean",T_BOOLEAN, CHECK);
    _char_mirror    =
      java_lang_Class::create_basic_type_mirror("char",   T_CHAR, CHECK);
    _long_mirror    =
      java_lang_Class::create_basic_type_mirror("long",   T_LONG, CHECK);
    _short_mirror   =
      java_lang_Class::create_basic_type_mirror("short",  T_SHORT,   CHECK);
    _void_mirror    =
      java_lang_Class::create_basic_type_mirror("void",   T_VOID, CHECK);

    _mirrors[T_INT]     = _int_mirror;
    _mirrors[T_FLOAT]   = _float_mirror;
    _mirrors[T_DOUBLE]  = _double_mirror;
    _mirrors[T_BYTE]    = _byte_mirror;
    _mirrors[T_BOOLEAN] = _bool_mirror;
    _mirrors[T_CHAR]    = _char_mirror;
    _mirrors[T_LONG]    = _long_mirror;
    _mirrors[T_SHORT]   = _short_mirror;
    _mirrors[T_VOID]    = _void_mirror;
  //_mirrors[T_OBJECT]  = _object_klass->java_mirror();
  //_mirrors[T_ARRAY]   = _object_klass->java_mirror();
}

void Universe::fixup_mirrors(TRAPS) {
  // Bootstrap problem: all classes gets a mirror (java.lang.Class instance) assigned eagerly,
  // but we cannot do that for classes created before java.lang.Class is loaded. Here we simply
  // walk over permanent objects created so far (mostly classes) and fixup their mirrors. Note
  // that the number of objects allocated at this point is very small.
  assert(SystemDictionary::Class_klass_loaded(), "java.lang.Class should be loaded");
  HandleMark hm(THREAD);
  // Cache the start of the static fields
  InstanceMirrorKlass::init_offset_of_static_fields();

  GrowableArray <Klass*>* list = java_lang_Class::fixup_mirror_list();
  int list_length = list->length();
  for (int i = 0; i < list_length; i++) {
    Klass* k = list->at(i);
    assert(k->is_klass(), "List should only hold classes");
    EXCEPTION_MARK;
    KlassHandle kh(THREAD, k);
    java_lang_Class::fixup_mirror(kh, CATCH);
}
  delete java_lang_Class::fixup_mirror_list();
  java_lang_Class::set_fixup_mirror_list(NULL);
}

static bool has_run_finalizers_on_exit = false;

void Universe::run_finalizers_on_exit() {
  if (has_run_finalizers_on_exit) return;
  has_run_finalizers_on_exit = true;

  // Called on VM exit. This ought to be run in a separate thread.
  log_trace(ref)("Callback to run finalizers on exit");
  {
    PRESERVE_EXCEPTION_MARK;
    KlassHandle finalizer_klass(THREAD, SystemDictionary::Finalizer_klass());
    JavaValue result(T_VOID);
    JavaCalls::call_static(
      &result,
      finalizer_klass,
      vmSymbols::run_finalizers_on_exit_name(),
      vmSymbols::void_method_signature(),
      THREAD
    );
    // Ignore any pending exceptions
    CLEAR_PENDING_EXCEPTION;
  }
}


// initialize_vtable could cause gc if
// 1) we specified true to initialize_vtable and
// 2) this ran after gc was enabled
// In case those ever change we use handles for oops
void Universe::reinitialize_vtable_of(KlassHandle k_h, TRAPS) {
  // init vtable of k and all subclasses
  Klass* ko = k_h();
  klassVtable* vt = ko->vtable();
  if (vt) vt->initialize_vtable(false, CHECK);
  if (ko->is_instance_klass()) {
    for (KlassHandle s_h(THREAD, ko->subklass());
         s_h() != NULL;
         s_h = KlassHandle(THREAD, s_h()->next_sibling())) {
      reinitialize_vtable_of(s_h, CHECK);
    }
  }
}


void initialize_itable_for_klass(Klass* k, TRAPS) {
  InstanceKlass::cast(k)->itable()->initialize_itable(false, CHECK);
}


void Universe::reinitialize_itables(TRAPS) {
  SystemDictionary::classes_do(initialize_itable_for_klass, CHECK);

}


bool Universe::on_page_boundary(void* addr) {
  return ((uintptr_t) addr) % os::vm_page_size() == 0;
}


bool Universe::should_fill_in_stack_trace(Handle throwable) {
  // never attempt to fill in the stack trace of preallocated errors that do not have
  // backtrace. These errors are kept alive forever and may be "re-used" when all
  // preallocated errors with backtrace have been consumed. Also need to avoid
  // a potential loop which could happen if an out of memory occurs when attempting
  // to allocate the backtrace.
  return ((throwable() != Universe::_out_of_memory_error_java_heap) &&
          (throwable() != Universe::_out_of_memory_error_metaspace)  &&
          (throwable() != Universe::_out_of_memory_error_class_metaspace)  &&
          (throwable() != Universe::_out_of_memory_error_array_size) &&
          (throwable() != Universe::_out_of_memory_error_gc_overhead_limit) &&
          (throwable() != Universe::_out_of_memory_error_realloc_objects));
}


oop Universe::gen_out_of_memory_error(oop default_err) {
  // generate an out of memory error:
  // - if there is a preallocated error with backtrace available then return it wth
  //   a filled in stack trace.
  // - if there are no preallocated errors with backtrace available then return
  //   an error without backtrace.
  int next;
  if (_preallocated_out_of_memory_error_avail_count > 0) {
    next = (int)Atomic::add(-1, &_preallocated_out_of_memory_error_avail_count);
    assert(next < (int)PreallocatedOutOfMemoryErrorCount, "avail count is corrupt");
  } else {
    next = -1;
  }
  if (next < 0) {
    // all preallocated errors have been used.
    // return default
    return default_err;
  } else {
    // get the error object at the slot and set set it to NULL so that the
    // array isn't keeping it alive anymore.
    oop exc = preallocated_out_of_memory_errors()->obj_at(next);
    assert(exc != NULL, "slot has been used already");
    preallocated_out_of_memory_errors()->obj_at_put(next, NULL);

    // use the message from the default error
    oop msg = java_lang_Throwable::message(default_err);
    assert(msg != NULL, "no message");
    java_lang_Throwable::set_message(exc, msg);

    // populate the stack trace and return it.
    java_lang_Throwable::fill_in_stack_trace_of_preallocated_backtrace(exc);
    return exc;
  }
}

intptr_t Universe::_non_oop_bits = 0;

void* Universe::non_oop_word() {
  // Neither the high bits nor the low bits of this value is allowed
  // to look like (respectively) the high or low bits of a real oop.
  //
  // High and low are CPU-specific notions, but low always includes
  // the low-order bit.  Since oops are always aligned at least mod 4,
  // setting the low-order bit will ensure that the low half of the
  // word will never look like that of a real oop.
  //
  // Using the OS-supplied non-memory-address word (usually 0 or -1)
  // will take care of the high bits, however many there are.

  if (_non_oop_bits == 0) {
    _non_oop_bits = (intptr_t)os::non_memory_address_word() | 1;
  }

  return (void*)_non_oop_bits;
}

jint universe_init() {
  assert(!Universe::_fully_initialized, "called after initialize_vtables");
  guarantee(1 << LogHeapWordSize == sizeof(HeapWord),
         "LogHeapWordSize is incorrect.");
  guarantee(sizeof(oop) >= sizeof(HeapWord), "HeapWord larger than oop?");
  guarantee(sizeof(oop) % sizeof(HeapWord) == 0,
            "oop size is not not a multiple of HeapWord size");
  TraceTime timer("Genesis", TraceStartupTime);
  JavaClasses::compute_hard_coded_offsets();

  jint status = Universe::initialize_heap();
  if (status != JNI_OK) {
    return status;
  }

  Metaspace::global_initialize();

  // Checks 'AfterMemoryInit' constraints.
  if (!CommandLineFlagConstraintList::check_constraints(CommandLineFlagConstraint::AfterMemoryInit)) {
    return JNI_EINVAL;
  }

  // Create memory for metadata.  Must be after initializing heap for
  // DumpSharedSpaces.
  ClassLoaderData::init_null_class_loader_data();

  // We have a heap so create the Method* caches before
  // Metaspace::initialize_shared_spaces() tries to populate them.
  Universe::_finalizer_register_cache = new LatestMethodCache();
  Universe::_loader_addClass_cache    = new LatestMethodCache();
  Universe::_pd_implies_cache         = new LatestMethodCache();
  Universe::_throw_illegal_access_error_cache = new LatestMethodCache();
  Universe::_do_stack_walk_cache = new LatestMethodCache();

  if (UseSharedSpaces) {
    // Read the data structures supporting the shared spaces (shared
    // system dictionary, symbol table, etc.).  After that, access to
    // the file (other than the mapped regions) is no longer needed, and
    // the file is closed. Closing the file does not affect the
    // currently mapped regions.
    MetaspaceShared::initialize_shared_spaces();
    StringTable::create_table();
  } else {
    SymbolTable::create_table();
    StringTable::create_table();
    ClassLoader::create_package_info_table();

    if (DumpSharedSpaces) {
      MetaspaceShared::prepare_for_dumping();
    }
  }
  if (strlen(VerifySubSet) > 0) {
    Universe::initialize_verify_flags();
  }

  return JNI_OK;
}

CollectedHeap* Universe::create_heap() {
  assert(_collectedHeap == NULL, "Heap already created");
#if !INCLUDE_ALL_GCS
  if (UseParallelGC) {
    fatal("UseParallelGC not supported in this VM.");
  } else if (UseG1GC) {
    fatal("UseG1GC not supported in this VM.");
  } else if (UseConcMarkSweepGC) {
    fatal("UseConcMarkSweepGC not supported in this VM.");
#else
  if (UseParallelGC) {
    return Universe::create_heap_with_policy<ParallelScavengeHeap, GenerationSizer>();
  } else if (UseG1GC) {
    return Universe::create_heap_with_policy<G1CollectedHeap, G1CollectorPolicy>();
  } else if (UseConcMarkSweepGC) {
    return Universe::create_heap_with_policy<GenCollectedHeap, ConcurrentMarkSweepPolicy>();
#endif
  } else if (UseSerialGC) {
    return Universe::create_heap_with_policy<GenCollectedHeap, MarkSweepPolicy>();
  }

  ShouldNotReachHere();
  return NULL;
}

// Choose the heap base address and oop encoding mode
// when compressed oops are used:
// Unscaled  - Use 32-bits oops without encoding when
//     NarrowOopHeapBaseMin + heap_size < 4Gb
// ZeroBased - Use zero based compressed oops with encoding when
//     NarrowOopHeapBaseMin + heap_size < 32Gb
// HeapBased - Use compressed oops with heap base + encoding.

jint Universe::initialize_heap() {
  jint status = JNI_ERR;

  _collectedHeap = create_heap_ext();
  if (_collectedHeap == NULL) {
    _collectedHeap = create_heap();
  }

  status = _collectedHeap->initialize();
  if (status != JNI_OK) {
    return status;
  }
  log_info(gc)("Using %s", _collectedHeap->name());

  ThreadLocalAllocBuffer::set_max_size(Universe::heap()->max_tlab_size());

#ifdef _LP64
  if (UseCompressedOops) {
    // Subtract a page because something can get allocated at heap base.
    // This also makes implicit null checking work, because the
    // memory+1 page below heap_base needs to cause a signal.
    // See needs_explicit_null_check.
    // Only set the heap base for compressed oops because it indicates
    // compressed oops for pstack code.
    if ((uint64_t)Universe::heap()->reserved_region().end() > UnscaledOopHeapMax) {
      // Didn't reserve heap below 4Gb.  Must shift.
      Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
    }
    if ((uint64_t)Universe::heap()->reserved_region().end() <= OopEncodingHeapMax) {
      // Did reserve heap below 32Gb. Can use base == 0;
      Universe::set_narrow_oop_base(0);
    }

    Universe::set_narrow_ptrs_base(Universe::narrow_oop_base());

    if (PrintCompressedOopsMode || (PrintMiscellaneous && Verbose)) {
      Universe::print_compressed_oops_mode(tty);
    }

    // Tell tests in which mode we run.
    Arguments::PropertyList_add(new SystemProperty("java.vm.compressedOopsMode",
                                                   narrow_oop_mode_to_string(narrow_oop_mode()),
                                                   false));
  }
  // Universe::narrow_oop_base() is one page below the heap.
  assert((intptr_t)Universe::narrow_oop_base() <= (intptr_t)(Universe::heap()->base() -
         os::vm_page_size()) ||
         Universe::narrow_oop_base() == NULL, "invalid value");
  assert(Universe::narrow_oop_shift() == LogMinObjAlignmentInBytes ||
         Universe::narrow_oop_shift() == 0, "invalid value");
#endif

  // We will never reach the CATCH below since Exceptions::_throw will cause
  // the VM to exit if an exception is thrown during initialization

  if (UseTLAB) {
    assert(Universe::heap()->supports_tlab_allocation(),
           "Should support thread-local allocation buffers");
    ThreadLocalAllocBuffer::startup_initialization();
  }
  return JNI_OK;
}

void Universe::print_compressed_oops_mode(outputStream* st) {
  st->print("heap address: " PTR_FORMAT ", size: " SIZE_FORMAT " MB",
              p2i(Universe::heap()->base()), Universe::heap()->reserved_region().byte_size()/M);

  st->print(", Compressed Oops mode: %s", narrow_oop_mode_to_string(narrow_oop_mode()));

  if (Universe::narrow_oop_base() != 0) {
    st->print(": " PTR_FORMAT, p2i(Universe::narrow_oop_base()));
  }

  if (Universe::narrow_oop_shift() != 0) {
    st->print(", Oop shift amount: %d", Universe::narrow_oop_shift());
  }

  if (!Universe::narrow_oop_use_implicit_null_checks()) {
    st->print(", no protected page in front of the heap");
  }
  st->cr();
}

ReservedSpace Universe::reserve_heap(size_t heap_size, size_t alignment) {

  assert(alignment <= Arguments::conservative_max_heap_alignment(),
         "actual alignment " SIZE_FORMAT " must be within maximum heap alignment " SIZE_FORMAT,
         alignment, Arguments::conservative_max_heap_alignment());

  size_t total_reserved = align_size_up(heap_size, alignment);
  assert(!UseCompressedOops || (total_reserved <= (OopEncodingHeapMax - os::vm_page_size())),
      "heap size is too big for compressed oops");

  bool use_large_pages = UseLargePages && is_size_aligned(alignment, os::large_page_size());
  assert(!UseLargePages
      || UseParallelGC
      || use_large_pages, "Wrong alignment to use large pages");

  // Now create the space.
  ReservedHeapSpace total_rs(total_reserved, alignment, use_large_pages);

  if (total_rs.is_reserved()) {
    assert((total_reserved == total_rs.size()) && ((uintptr_t)total_rs.base() % alignment == 0),
           "must be exactly of required size and alignment");
    // We are good.

    if (UseCompressedOops) {
      // Universe::initialize_heap() will reset this to NULL if unscaled
      // or zero-based narrow oops are actually used.
      // Else heap start and base MUST differ, so that NULL can be encoded nonambigous.
      Universe::set_narrow_oop_base((address)total_rs.compressed_oop_base());
    }

    return total_rs;
  }

  vm_exit_during_initialization(
    err_msg("Could not reserve enough space for " SIZE_FORMAT "KB object heap",
            total_reserved/K));

  // satisfy compiler
  ShouldNotReachHere();
  return ReservedHeapSpace(0, 0, false);
}


// It's the caller's responsibility to ensure glitch-freedom
// (if required).
void Universe::update_heap_info_at_gc() {
  _heap_capacity_at_last_gc = heap()->capacity();
  _heap_used_at_last_gc     = heap()->used();
}


const char* Universe::narrow_oop_mode_to_string(Universe::NARROW_OOP_MODE mode) {
  switch (mode) {
    case UnscaledNarrowOop:
      return "32-bit";
    case ZeroBasedNarrowOop:
      return "Zero based";
    case DisjointBaseNarrowOop:
      return "Non-zero disjoint base";
    case HeapBasedNarrowOop:
      return "Non-zero based";
  }

  ShouldNotReachHere();
  return "";
}


Universe::NARROW_OOP_MODE Universe::narrow_oop_mode() {
  if (narrow_oop_base_disjoint()) {
    return DisjointBaseNarrowOop;
  }

  if (narrow_oop_base() != 0) {
    return HeapBasedNarrowOop;
  }

  if (narrow_oop_shift() != 0) {
    return ZeroBasedNarrowOop;
  }

  return UnscaledNarrowOop;
}


void universe2_init() {
  EXCEPTION_MARK;
  Universe::genesis(CATCH);
}


bool universe_post_init() {
  assert(!is_init_completed(), "Error: initialization not yet completed!");
  Universe::_fully_initialized = true;
  EXCEPTION_MARK;
  { ResourceMark rm;
    Interpreter::initialize();      // needed for interpreter entry points
    if (!UseSharedSpaces) {
      HandleMark hm(THREAD);
      KlassHandle ok_h(THREAD, SystemDictionary::Object_klass());
      Universe::reinitialize_vtable_of(ok_h, CHECK_false);
      Universe::reinitialize_itables(CHECK_false);
    }
  }

  HandleMark hm(THREAD);
  Klass* k;
  instanceKlassHandle k_h;
    // Setup preallocated empty java.lang.Class array
    Universe::_the_empty_class_klass_array = oopFactory::new_objArray(SystemDictionary::Class_klass(), 0, CHECK_false);

    // Setup preallocated OutOfMemoryError errors
    k = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_OutOfMemoryError(), true, CHECK_false);
    k_h = instanceKlassHandle(THREAD, k);
    Universe::_out_of_memory_error_java_heap = k_h->allocate_instance(CHECK_false);
    Universe::_out_of_memory_error_metaspace = k_h->allocate_instance(CHECK_false);
    Universe::_out_of_memory_error_class_metaspace = k_h->allocate_instance(CHECK_false);
    Universe::_out_of_memory_error_array_size = k_h->allocate_instance(CHECK_false);
    Universe::_out_of_memory_error_gc_overhead_limit =
      k_h->allocate_instance(CHECK_false);
    Universe::_out_of_memory_error_realloc_objects = k_h->allocate_instance(CHECK_false);

    // Setup preallocated cause message for delayed StackOverflowError
    if (StackReservedPages > 0) {
      Universe::_delayed_stack_overflow_error_message =
        java_lang_String::create_oop_from_str("Delayed StackOverflowError due to ReservedStackAccess annotated method", CHECK_false);
    }

    // Setup preallocated NullPointerException
    // (this is currently used for a cheap & dirty solution in compiler exception handling)
    k = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_NullPointerException(), true, CHECK_false);
    Universe::_null_ptr_exception_instance = InstanceKlass::cast(k)->allocate_instance(CHECK_false);
    // Setup preallocated ArithmeticException
    // (this is currently used for a cheap & dirty solution in compiler exception handling)
    k = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_ArithmeticException(), true, CHECK_false);
    Universe::_arithmetic_exception_instance = InstanceKlass::cast(k)->allocate_instance(CHECK_false);
    // Virtual Machine Error for when we get into a situation we can't resolve
    k = SystemDictionary::resolve_or_fail(
      vmSymbols::java_lang_VirtualMachineError(), true, CHECK_false);
    bool linked = InstanceKlass::cast(k)->link_class_or_fail(CHECK_false);
    if (!linked) {
      tty->print_cr("Unable to link/verify VirtualMachineError class");
      return false; // initialization failed
    }
    Universe::_virtual_machine_error_instance =
      InstanceKlass::cast(k)->allocate_instance(CHECK_false);

    Universe::_vm_exception = InstanceKlass::cast(k)->allocate_instance(CHECK_false);

  if (!DumpSharedSpaces) {
    // These are the only Java fields that are currently set during shared space dumping.
    // We prefer to not handle this generally, so we always reinitialize these detail messages.
    Handle msg = java_lang_String::create_from_str("Java heap space", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_java_heap, msg());

    msg = java_lang_String::create_from_str("Metaspace", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_metaspace, msg());
    msg = java_lang_String::create_from_str("Compressed class space", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_class_metaspace, msg());

    msg = java_lang_String::create_from_str("Requested array size exceeds VM limit", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_array_size, msg());

    msg = java_lang_String::create_from_str("GC overhead limit exceeded", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_gc_overhead_limit, msg());

    msg = java_lang_String::create_from_str("Java heap space: failed reallocation of scalar replaced objects", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_realloc_objects, msg());

    msg = java_lang_String::create_from_str("/ by zero", CHECK_false);
    java_lang_Throwable::set_message(Universe::_arithmetic_exception_instance, msg());

    // Setup the array of errors that have preallocated backtrace
    k = Universe::_out_of_memory_error_java_heap->klass();
    assert(k->name() == vmSymbols::java_lang_OutOfMemoryError(), "should be out of memory error");
    k_h = instanceKlassHandle(THREAD, k);

    int len = (StackTraceInThrowable) ? (int)PreallocatedOutOfMemoryErrorCount : 0;
    Universe::_preallocated_out_of_memory_error_array = oopFactory::new_objArray(k_h(), len, CHECK_false);
    for (int i=0; i<len; i++) {
      oop err = k_h->allocate_instance(CHECK_false);
      Handle err_h = Handle(THREAD, err);
      java_lang_Throwable::allocate_backtrace(err_h, CHECK_false);
      Universe::preallocated_out_of_memory_errors()->obj_at_put(i, err_h());
    }
    Universe::_preallocated_out_of_memory_error_avail_count = (jint)len;
  }


  // Setup static method for registering finalizers
  // The finalizer klass must be linked before looking up the method, in
  // case it needs to get rewritten.
  SystemDictionary::Finalizer_klass()->link_class(CHECK_false);
  Method* m = SystemDictionary::Finalizer_klass()->find_method(
                                  vmSymbols::register_method_name(),
                                  vmSymbols::register_method_signature());
  if (m == NULL || !m->is_static()) {
    tty->print_cr("Unable to link/verify Finalizer.register method");
    return false; // initialization failed (cannot throw exception yet)
  }
  Universe::_finalizer_register_cache->init(
    SystemDictionary::Finalizer_klass(), m);

  SystemDictionary::internal_Unsafe_klass()->link_class(CHECK_false);
  m = SystemDictionary::internal_Unsafe_klass()->find_method(
                                  vmSymbols::throwIllegalAccessError_name(),
                                  vmSymbols::void_method_signature());
  if (m != NULL && !m->is_static()) {
    // Note null is okay; this method is used in itables, and if it is null,
    // then AbstractMethodError is thrown instead.
    tty->print_cr("Unable to link/verify Unsafe.throwIllegalAccessError method");
    return false; // initialization failed (cannot throw exception yet)
  }
  Universe::_throw_illegal_access_error_cache->init(
    SystemDictionary::internal_Unsafe_klass(), m);

  // Setup method for registering loaded classes in class loader vector
  SystemDictionary::ClassLoader_klass()->link_class(CHECK_false);
  m = SystemDictionary::ClassLoader_klass()->find_method(vmSymbols::addClass_name(), vmSymbols::class_void_signature());
  if (m == NULL || m->is_static()) {
    tty->print_cr("Unable to link/verify ClassLoader.addClass method");
    return false; // initialization failed (cannot throw exception yet)
  }
  Universe::_loader_addClass_cache->init(
    SystemDictionary::ClassLoader_klass(), m);

  // Setup method for checking protection domain
  SystemDictionary::ProtectionDomain_klass()->link_class(CHECK_false);
  m = SystemDictionary::ProtectionDomain_klass()->
            find_method(vmSymbols::impliesCreateAccessControlContext_name(),
                        vmSymbols::void_boolean_signature());
  // Allow NULL which should only happen with bootstrapping.
  if (m != NULL) {
    if (m->is_static()) {
      // NoSuchMethodException doesn't actually work because it tries to run the
      // <init> function before java_lang_Class is linked. Print error and exit.
      tty->print_cr("ProtectionDomain.impliesCreateAccessControlContext() has the wrong linkage");
      return false; // initialization failed
    }
    Universe::_pd_implies_cache->init(
      SystemDictionary::ProtectionDomain_klass(), m);
  }

  // Setup method for stack walking
  InstanceKlass::cast(SystemDictionary::AbstractStackWalker_klass())->link_class(CHECK_false);
  m = InstanceKlass::cast(SystemDictionary::AbstractStackWalker_klass())->
            find_method(vmSymbols::doStackWalk_name(),
                        vmSymbols::doStackWalk_signature());
  // Allow NULL which should only happen with bootstrapping.
  if (m != NULL) {
    Universe::_do_stack_walk_cache->init(
      SystemDictionary::AbstractStackWalker_klass(), m);
  }

  // This needs to be done before the first scavenge/gc, since
  // it's an input to soft ref clearing policy.
  {
    MutexLocker x(Heap_lock);
    Universe::update_heap_info_at_gc();
  }

  // ("weak") refs processing infrastructure initialization
  Universe::heap()->post_initialize();

  // Initialize performance counters for metaspaces
  MetaspaceCounters::initialize_performance_counters();
  CompressedClassSpaceCounters::initialize_performance_counters();

  MemoryService::add_metaspace_memory_pools();

  MemoryService::set_universe_heap(Universe::heap());
#if INCLUDE_CDS
  SharedClassUtil::initialize(CHECK_false);
#endif
  return true;
}


void Universe::compute_base_vtable_size() {
  _base_vtable_size = ClassLoader::compute_Object_vtable();
}

void Universe::print_on(outputStream* st) {
  st->print_cr("Heap");
  heap()->print_on(st);
}

void Universe::print_heap_at_SIGBREAK() {
  if (PrintHeapAtSIGBREAK) {
    MutexLocker hl(Heap_lock);
    print_on(tty);
    tty->cr();
    tty->flush();
  }
}

void Universe::print_heap_before_gc() {
  LogHandle(gc, heap) log;
  if (log.is_trace()) {
    log.trace("Heap before GC invocations=%u (full %u):", heap()->total_collections(), heap()->total_full_collections());
    ResourceMark rm;
    heap()->print_on(log.trace_stream());
  }
}

void Universe::print_heap_after_gc() {
  LogHandle(gc, heap) log;
  if (log.is_trace()) {
    log.trace("Heap after GC invocations=%u (full %u):", heap()->total_collections(), heap()->total_full_collections());
    ResourceMark rm;
    heap()->print_on(log.trace_stream());
  }
}

void Universe::initialize_verify_flags() {
  verify_flags = 0;
  const char delimiter[] = " ,";

  size_t length = strlen(VerifySubSet);
  char* subset_list = NEW_C_HEAP_ARRAY(char, length + 1, mtInternal);
  strncpy(subset_list, VerifySubSet, length + 1);

  char* token = strtok(subset_list, delimiter);
  while (token != NULL) {
    if (strcmp(token, "threads") == 0) {
      verify_flags |= Verify_Threads;
    } else if (strcmp(token, "heap") == 0) {
      verify_flags |= Verify_Heap;
    } else if (strcmp(token, "symbol_table") == 0) {
      verify_flags |= Verify_SymbolTable;
    } else if (strcmp(token, "string_table") == 0) {
      verify_flags |= Verify_StringTable;
    } else if (strcmp(token, "codecache") == 0) {
      verify_flags |= Verify_CodeCache;
    } else if (strcmp(token, "dictionary") == 0) {
      verify_flags |= Verify_SystemDictionary;
    } else if (strcmp(token, "classloader_data_graph") == 0) {
      verify_flags |= Verify_ClassLoaderDataGraph;
    } else if (strcmp(token, "metaspace") == 0) {
      verify_flags |= Verify_MetaspaceAux;
    } else if (strcmp(token, "jni_handles") == 0) {
      verify_flags |= Verify_JNIHandles;
    } else if (strcmp(token, "c-heap") == 0) {
      verify_flags |= Verify_CHeap;
    } else if (strcmp(token, "codecache_oops") == 0) {
      verify_flags |= Verify_CodeCacheOops;
    } else {
      vm_exit_during_initialization(err_msg("VerifySubSet: \'%s\' memory sub-system is unknown, please correct it", token));
    }
    token = strtok(NULL, delimiter);
  }
  FREE_C_HEAP_ARRAY(char, subset_list);
}

bool Universe::should_verify_subset(uint subset) {
  if (verify_flags & subset) {
    return true;
  }
  return false;
}

void Universe::verify(VerifyOption option, const char* prefix) {
  // The use of _verify_in_progress is a temporary work around for
  // 6320749.  Don't bother with a creating a class to set and clear
  // it since it is only used in this method and the control flow is
  // straight forward.
  _verify_in_progress = true;

  COMPILER2_PRESENT(
    assert(!DerivedPointerTable::is_active(),
         "DPT should not be active during verification "
         "(of thread stacks below)");
  )

  ResourceMark rm;
  HandleMark hm;  // Handles created during verification can be zapped
  _verify_count++;

  FormatBuffer<> title("Verifying %s", prefix);
  GCTraceTime(Info, gc, verify) tm(title.buffer());
  if (should_verify_subset(Verify_Threads)) {
    log_debug(gc, verify)("Threads");
    Threads::verify();
  }
  if (should_verify_subset(Verify_Heap)) {
    log_debug(gc, verify)("Heap");
    heap()->verify(option);
  }
  if (should_verify_subset(Verify_SymbolTable)) {
    log_debug(gc, verify)("SymbolTable");
    SymbolTable::verify();
  }
  if (should_verify_subset(Verify_StringTable)) {
    log_debug(gc, verify)("StringTable");
    StringTable::verify();
  }
  if (should_verify_subset(Verify_CodeCache)) {
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    log_debug(gc, verify)("CodeCache");
    CodeCache::verify();
  }
  }
  if (should_verify_subset(Verify_SystemDictionary)) {
    log_debug(gc, verify)("SystemDictionary");
    SystemDictionary::verify();
  }
#ifndef PRODUCT
  if (should_verify_subset(Verify_ClassLoaderDataGraph)) {
    log_debug(gc, verify)("ClassLoaderDataGraph");
    ClassLoaderDataGraph::verify();
  }
#endif
  if (should_verify_subset(Verify_MetaspaceAux)) {
    log_debug(gc, verify)("MetaspaceAux");
    MetaspaceAux::verify_free_chunks();
  }
  if (should_verify_subset(Verify_JNIHandles)) {
    log_debug(gc, verify)("JNIHandles");
    JNIHandles::verify();
  }
  if (should_verify_subset(Verify_CHeap)) {
    log_debug(gc, verify)("C-heap");
    os::check_heap();
  }
  if (should_verify_subset(Verify_CodeCacheOops)) {
    log_debug(gc, verify)("CodeCache Oops");
    CodeCache::verify_oops();
  }

  _verify_in_progress = false;
}


#ifndef PRODUCT
void Universe::calculate_verify_data(HeapWord* low_boundary, HeapWord* high_boundary) {
  assert(low_boundary < high_boundary, "bad interval");

  // decide which low-order bits we require to be clear:
  size_t alignSize = MinObjAlignmentInBytes;
  size_t min_object_size = CollectedHeap::min_fill_size();

  // make an inclusive limit:
  uintptr_t max = (uintptr_t)high_boundary - min_object_size*wordSize;
  uintptr_t min = (uintptr_t)low_boundary;
  assert(min < max, "bad interval");
  uintptr_t diff = max ^ min;

  // throw away enough low-order bits to make the diff vanish
  uintptr_t mask = (uintptr_t)(-1);
  while ((mask & diff) != 0)
    mask <<= 1;
  uintptr_t bits = (min & mask);
  assert(bits == (max & mask), "correct mask");
  // check an intermediate value between min and max, just to make sure:
  assert(bits == ((min + (max-min)/2) & mask), "correct mask");

  // require address alignment, too:
  mask |= (alignSize - 1);

  if (!(_verify_oop_mask == 0 && _verify_oop_bits == (uintptr_t)-1)) {
    assert(_verify_oop_mask == mask && _verify_oop_bits == bits, "mask stability");
  }
  _verify_oop_mask = mask;
  _verify_oop_bits = bits;
}

// Oop verification (see MacroAssembler::verify_oop)

uintptr_t Universe::verify_oop_mask() {
  MemRegion m = heap()->reserved_region();
  calculate_verify_data(m.start(), m.end());
  return _verify_oop_mask;
}

uintptr_t Universe::verify_oop_bits() {
  MemRegion m = heap()->reserved_region();
  calculate_verify_data(m.start(), m.end());
  return _verify_oop_bits;
}

uintptr_t Universe::verify_mark_mask() {
  return markOopDesc::lock_mask_in_place;
}

uintptr_t Universe::verify_mark_bits() {
  intptr_t mask = verify_mark_mask();
  intptr_t bits = (intptr_t)markOopDesc::prototype();
  assert((bits & ~mask) == 0, "no stray header bits");
  return bits;
}
#endif // PRODUCT


void Universe::compute_verify_oop_data() {
  verify_oop_mask();
  verify_oop_bits();
  verify_mark_mask();
  verify_mark_bits();
}


void LatestMethodCache::init(Klass* k, Method* m) {
  if (!UseSharedSpaces) {
    _klass = k;
  }
#ifndef PRODUCT
  else {
    // sharing initilization should have already set up _klass
    assert(_klass != NULL, "just checking");
  }
#endif

  _method_idnum = m->method_idnum();
  assert(_method_idnum >= 0, "sanity check");
}


Method* LatestMethodCache::get_method() {
  if (klass() == NULL) return NULL;
  InstanceKlass* ik = InstanceKlass::cast(klass());
  Method* m = ik->method_with_idnum(method_idnum());
  assert(m != NULL, "sanity check");
  return m;
}


#ifdef ASSERT
// Release dummy object(s) at bottom of heap
bool Universe::release_fullgc_alot_dummy() {
  MutexLocker ml(FullGCALot_lock);
  if (_fullgc_alot_dummy_array != NULL) {
    if (_fullgc_alot_dummy_next >= _fullgc_alot_dummy_array->length()) {
      // No more dummies to release, release entire array instead
      _fullgc_alot_dummy_array = NULL;
      return false;
    }
    if (!UseConcMarkSweepGC) {
      // Release dummy at bottom of old generation
      _fullgc_alot_dummy_array->obj_at_put(_fullgc_alot_dummy_next++, NULL);
    }
    // Release dummy at bottom of permanent generation
    _fullgc_alot_dummy_array->obj_at_put(_fullgc_alot_dummy_next++, NULL);
  }
  return true;
}

#endif // ASSERT

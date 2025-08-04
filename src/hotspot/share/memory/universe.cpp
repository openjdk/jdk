/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveHeapLoader.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeBehaviours.hpp"
#include "code/codeCache.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/gcConfig.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceCounters.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/jmethodIDTable.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/objLayout.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/flags/jvmFlagLimit.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/threads.hpp"
#include "runtime/timerTrace.hpp"
#include "sanitizers/leak.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#include "utilities/autoRestore.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/preserveException.hpp"

// A helper class for caching a Method* when the user of the cache
// only cares about the latest version of the Method*. This cache safely
// interacts with the RedefineClasses API.
class LatestMethodCache {
  // We save the InstanceKlass* and the idnum of Method* in order to get
  // the current Method*.
  InstanceKlass*        _klass;
  int                   _method_idnum;

 public:
  LatestMethodCache()   { _klass = nullptr; _method_idnum = -1; }

  void init(JavaThread* current, InstanceKlass* ik, const char* method,
            Symbol* signature, bool is_static);
  Method* get_method();
};

static LatestMethodCache _finalizer_register_cache;         // Finalizer.register()
static LatestMethodCache _loader_addClass_cache;            // ClassLoader.addClass()
static LatestMethodCache _throw_illegal_access_error_cache; // Unsafe.throwIllegalAccessError()
static LatestMethodCache _throw_no_such_method_error_cache; // Unsafe.throwNoSuchMethodError()
static LatestMethodCache _do_stack_walk_cache;              // AbstractStackWalker.doStackWalk()

// Known objects
TypeArrayKlass* Universe::_typeArrayKlasses[T_LONG+1] = { nullptr /*, nullptr...*/ };
ObjArrayKlass* Universe::_objectArrayKlass            = nullptr;
Klass* Universe::_fillerArrayKlass                    = nullptr;
OopHandle Universe::_basic_type_mirrors[T_VOID+1];
#if INCLUDE_CDS_JAVA_HEAP
int Universe::_archived_basic_type_mirror_indices[T_VOID+1];
#endif

OopHandle Universe::_main_thread_group;
OopHandle Universe::_system_thread_group;
OopHandle Universe::_the_empty_class_array;
OopHandle Universe::_the_null_string;
OopHandle Universe::_the_min_jint_string;

OopHandle Universe::_the_null_sentinel;

// _out_of_memory_errors is an objArray
enum OutOfMemoryInstance { _oom_java_heap,
                           _oom_c_heap,
                           _oom_metaspace,
                           _oom_class_metaspace,
                           _oom_array_size,
                           _oom_gc_overhead_limit,
                           _oom_realloc_objects,
                           _oom_count };

OopHandle Universe::_out_of_memory_errors;
OopHandle Universe:: _class_init_stack_overflow_error;
OopHandle Universe::_delayed_stack_overflow_error_message;
OopHandle Universe::_preallocated_out_of_memory_error_array;
volatile jint Universe::_preallocated_out_of_memory_error_avail_count = 0;

// Message details for OOME objects, preallocate these objects since they could be
// used when throwing OOME, we should try to avoid further allocation in such case
OopHandle Universe::_msg_metaspace;
OopHandle Universe::_msg_class_metaspace;

OopHandle Universe::_reference_pending_list;

Array<Klass*>* Universe::_the_array_interfaces_array = nullptr;

long Universe::verify_flags                           = Universe::Verify_All;

Array<int>* Universe::_the_empty_int_array            = nullptr;
Array<u2>* Universe::_the_empty_short_array           = nullptr;
Array<Klass*>* Universe::_the_empty_klass_array     = nullptr;
Array<InstanceKlass*>* Universe::_the_empty_instance_klass_array  = nullptr;
Array<Method*>* Universe::_the_empty_method_array   = nullptr;

uintx Universe::_the_array_interfaces_bitmap = 0;
uintx Universe::_the_empty_klass_bitmap      = 0;

// These variables are guarded by FullGCALot_lock.
DEBUG_ONLY(OopHandle Universe::_fullgc_alot_dummy_array;)
DEBUG_ONLY(int Universe::_fullgc_alot_dummy_next = 0;)

// Heap
int             Universe::_verify_count = 0;

// Oop verification (see MacroAssembler::verify_oop)
uintptr_t       Universe::_verify_oop_mask = 0;
uintptr_t       Universe::_verify_oop_bits = (uintptr_t) -1;

int             Universe::_base_vtable_size = 0;
bool            Universe::_bootstrapping = false;
bool            Universe::_module_initialized = false;
bool            Universe::_fully_initialized = false;

OopStorage*     Universe::_vm_weak = nullptr;
OopStorage*     Universe::_vm_global = nullptr;

CollectedHeap*  Universe::_collectedHeap = nullptr;

// These are the exceptions that are always created and are guatanteed to exist.
// If possible, they can be stored as CDS archived objects to speed up AOT code.
class BuiltinException {
  OopHandle _instance;
  CDS_JAVA_HEAP_ONLY(int _archived_root_index;)

public:
  BuiltinException() : _instance() {
    CDS_JAVA_HEAP_ONLY(_archived_root_index = 0);
  }

  void init_if_empty(Symbol* symbol, TRAPS) {
    if (_instance.is_empty()) {
      Klass* k = SystemDictionary::resolve_or_fail(symbol, true, CHECK);
      oop obj = InstanceKlass::cast(k)->allocate_instance(CHECK);
      _instance = OopHandle(Universe::vm_global(), obj);
    }
  }

  oop instance() {
    return _instance.resolve();
  }

#if INCLUDE_CDS_JAVA_HEAP
  void store_in_cds() {
    _archived_root_index = HeapShared::archive_exception_instance(instance());
  }

  void load_from_cds() {
    if (_archived_root_index >= 0) {
      oop obj = HeapShared::get_root(_archived_root_index);
      assert(obj != nullptr, "must be");
      _instance = OopHandle(Universe::vm_global(), obj);
    }
  }

  void serialize(SerializeClosure *f) {
    f->do_int(&_archived_root_index);
  }
#endif
};

static BuiltinException _null_ptr_exception;
static BuiltinException _arithmetic_exception;
static BuiltinException _internal_error;
static BuiltinException _array_index_out_of_bounds_exception;
static BuiltinException _array_store_exception;
static BuiltinException _class_cast_exception;

objArrayOop Universe::the_empty_class_array ()  {
  return (objArrayOop)_the_empty_class_array.resolve();
}

oop Universe::main_thread_group()                 { return _main_thread_group.resolve(); }
void Universe::set_main_thread_group(oop group)   { _main_thread_group = OopHandle(vm_global(), group); }

oop Universe::system_thread_group()               { return _system_thread_group.resolve(); }
void Universe::set_system_thread_group(oop group) { _system_thread_group = OopHandle(vm_global(), group); }

oop Universe::the_null_string()                   { return _the_null_string.resolve(); }
oop Universe::the_min_jint_string()               { return _the_min_jint_string.resolve(); }

oop Universe::null_ptr_exception_instance()       { return _null_ptr_exception.instance(); }
oop Universe::arithmetic_exception_instance()     { return _arithmetic_exception.instance(); }
oop Universe::internal_error_instance()           { return _internal_error.instance(); }
oop Universe::array_index_out_of_bounds_exception_instance() { return _array_index_out_of_bounds_exception.instance(); }
oop Universe::array_store_exception_instance()    { return _array_store_exception.instance(); }
oop Universe::class_cast_exception_instance()     { return _class_cast_exception.instance(); }

oop Universe::the_null_sentinel()                 { return _the_null_sentinel.resolve(); }

oop Universe::int_mirror()                        { return check_mirror(_basic_type_mirrors[T_INT].resolve()); }
oop Universe::float_mirror()                      { return check_mirror(_basic_type_mirrors[T_FLOAT].resolve()); }
oop Universe::double_mirror()                     { return check_mirror(_basic_type_mirrors[T_DOUBLE].resolve()); }
oop Universe::byte_mirror()                       { return check_mirror(_basic_type_mirrors[T_BYTE].resolve()); }
oop Universe::bool_mirror()                       { return check_mirror(_basic_type_mirrors[T_BOOLEAN].resolve()); }
oop Universe::char_mirror()                       { return check_mirror(_basic_type_mirrors[T_CHAR].resolve()); }
oop Universe::long_mirror()                       { return check_mirror(_basic_type_mirrors[T_LONG].resolve()); }
oop Universe::short_mirror()                      { return check_mirror(_basic_type_mirrors[T_SHORT].resolve()); }
oop Universe::void_mirror()                       { return check_mirror(_basic_type_mirrors[T_VOID].resolve()); }

oop Universe::java_mirror(BasicType t) {
  assert((uint)t < T_VOID+1, "range check");
  assert(!is_reference_type(t), "sanity");
  return check_mirror(_basic_type_mirrors[t].resolve());
}

void Universe::basic_type_classes_do(KlassClosure *closure) {
  for (int i = T_BOOLEAN; i < T_LONG+1; i++) {
    closure->do_klass(_typeArrayKlasses[i]);
  }
  // We don't do the following because it will confuse JVMTI.
  // _fillerArrayKlass is used only by GC, which doesn't need to see
  // this klass from basic_type_classes_do().
  //
  // closure->do_klass(_fillerArrayKlass);
}

void Universe::metaspace_pointers_do(MetaspaceClosure* it) {
  it->push(&_fillerArrayKlass);
  for (int i = 0; i < T_LONG+1; i++) {
    it->push(&_typeArrayKlasses[i]);
  }
  it->push(&_objectArrayKlass);

  it->push(&_the_empty_int_array);
  it->push(&_the_empty_short_array);
  it->push(&_the_empty_klass_array);
  it->push(&_the_empty_instance_klass_array);
  it->push(&_the_empty_method_array);
  it->push(&_the_array_interfaces_array);
}

#if INCLUDE_CDS_JAVA_HEAP
void Universe::set_archived_basic_type_mirror_index(BasicType t, int index) {
  assert(CDSConfig::is_dumping_heap(), "sanity");
  assert(!is_reference_type(t), "sanity");
  _archived_basic_type_mirror_indices[t] = index;
}

void Universe::archive_exception_instances() {
  _null_ptr_exception.store_in_cds();
  _arithmetic_exception.store_in_cds();
  _internal_error.store_in_cds();
  _array_index_out_of_bounds_exception.store_in_cds();
  _array_store_exception.store_in_cds();
  _class_cast_exception.store_in_cds();
}

void Universe::load_archived_object_instances() {
  if (ArchiveHeapLoader::is_in_use()) {
    for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
      int index = _archived_basic_type_mirror_indices[i];
      if (!is_reference_type((BasicType)i) && index >= 0) {
        oop mirror_oop = HeapShared::get_root(index);
        assert(mirror_oop != nullptr, "must be");
        _basic_type_mirrors[i] = OopHandle(vm_global(), mirror_oop);
      }
    }

    _null_ptr_exception.load_from_cds();
    _arithmetic_exception.load_from_cds();
    _internal_error.load_from_cds();
    _array_index_out_of_bounds_exception.load_from_cds();
    _array_store_exception.load_from_cds();
    _class_cast_exception.load_from_cds();
  }
}
#endif

void Universe::serialize(SerializeClosure* f) {

#if INCLUDE_CDS_JAVA_HEAP
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    f->do_int(&_archived_basic_type_mirror_indices[i]);
    // if f->reading(): We can't call HeapShared::get_root() yet, as the heap
    // contents may need to be relocated. _basic_type_mirrors[i] will be
    // updated later in Universe::load_archived_object_instances().
  }
  _null_ptr_exception.serialize(f);
  _arithmetic_exception.serialize(f);
  _internal_error.serialize(f);
  _array_index_out_of_bounds_exception.serialize(f);
  _array_store_exception.serialize(f);
  _class_cast_exception.serialize(f);
#endif

  f->do_ptr(&_fillerArrayKlass);
  for (int i = 0; i < T_LONG+1; i++) {
    f->do_ptr(&_typeArrayKlasses[i]);
  }

  f->do_ptr(&_objectArrayKlass);
  f->do_ptr(&_the_array_interfaces_array);
  f->do_ptr(&_the_empty_int_array);
  f->do_ptr(&_the_empty_short_array);
  f->do_ptr(&_the_empty_method_array);
  f->do_ptr(&_the_empty_klass_array);
  f->do_ptr(&_the_empty_instance_klass_array);
}


void Universe::check_alignment(uintx size, uintx alignment, const char* name) {
  if (size < alignment || size % alignment != 0) {
    vm_exit_during_initialization(
      err_msg("Size of %s (%zu bytes) must be aligned to %zu bytes", name, size, alignment));
  }
}

static void initialize_basic_type_klass(Klass* k, TRAPS) {
  Klass* ok = vmClasses::Object_klass();
#if INCLUDE_CDS
  if (CDSConfig::is_using_archive()) {
    ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
    assert(k->super() == ok, "u3");
    if (k->is_instance_klass()) {
      InstanceKlass::cast(k)->restore_unshareable_info(loader_data, Handle(), nullptr, CHECK);
    } else {
      ArrayKlass::cast(k)->restore_unshareable_info(loader_data, Handle(), CHECK);
    }
  } else
#endif
  {
    k->initialize_supers(ok, nullptr, CHECK);
  }
  k->append_to_sibling_list();
}

void Universe::genesis(TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark   hm(THREAD);

  // Explicit null checks are needed if these offsets are not smaller than the page size
  if (UseCompactObjectHeaders) {
    assert(oopDesc::mark_offset_in_bytes() < static_cast<intptr_t>(os::vm_page_size()),
           "Mark offset is expected to be less than the page size");
  } else {
    assert(oopDesc::klass_offset_in_bytes() < static_cast<intptr_t>(os::vm_page_size()),
           "Klass offset is expected to be less than the page size");
  }
  assert(arrayOopDesc::length_offset_in_bytes() < static_cast<intptr_t>(os::vm_page_size()),
         "Array length offset is expected to be less than the page size");

  { AutoModifyRestore<bool> temporarily(_bootstrapping, true);

    java_lang_Class::allocate_fixup_lists();

    // determine base vtable size; without that we cannot create the array klasses
    compute_base_vtable_size();

    if (!CDSConfig::is_using_archive()) {
      // Initialization of the fillerArrayKlass must come before regular
      // int-TypeArrayKlass so that the int-Array mirror points to the
      // int-TypeArrayKlass.
      _fillerArrayKlass = TypeArrayKlass::create_klass(T_INT, "[Ljdk/internal/vm/FillerElement;", CHECK);
      for (int i = T_BOOLEAN; i < T_LONG+1; i++) {
        _typeArrayKlasses[i] = TypeArrayKlass::create_klass((BasicType)i, CHECK);
      }

      ClassLoaderData* null_cld = ClassLoaderData::the_null_class_loader_data();

      _the_array_interfaces_array     = MetadataFactory::new_array<Klass*>(null_cld, 2, nullptr, CHECK);
      _the_empty_int_array            = MetadataFactory::new_array<int>(null_cld, 0, CHECK);
      _the_empty_short_array          = MetadataFactory::new_array<u2>(null_cld, 0, CHECK);
      _the_empty_method_array         = MetadataFactory::new_array<Method*>(null_cld, 0, CHECK);
      _the_empty_klass_array          = MetadataFactory::new_array<Klass*>(null_cld, 0, CHECK);
      _the_empty_instance_klass_array = MetadataFactory::new_array<InstanceKlass*>(null_cld, 0, CHECK);
    }

    vmSymbols::initialize();

    // Initialize table for matching jmethodID, before SystemDictionary.
    JmethodIDTable::initialize();

    SystemDictionary::initialize(CHECK);

    // Create string constants
    oop s = StringTable::intern("null", CHECK);
    _the_null_string = OopHandle(vm_global(), s);
    s = StringTable::intern("-2147483648", CHECK);
    _the_min_jint_string = OopHandle(vm_global(), s);

#if INCLUDE_CDS
    if (CDSConfig::is_using_archive()) {
      // Verify shared interfaces array.
      assert(_the_array_interfaces_array->at(0) ==
             vmClasses::Cloneable_klass(), "u3");
      assert(_the_array_interfaces_array->at(1) ==
             vmClasses::Serializable_klass(), "u3");
    } else
#endif
    {
      // Set up shared interfaces array.  (Do this before supers are set up.)
      _the_array_interfaces_array->at_put(0, vmClasses::Cloneable_klass());
      _the_array_interfaces_array->at_put(1, vmClasses::Serializable_klass());
    }

    _the_array_interfaces_bitmap = Klass::compute_secondary_supers_bitmap(_the_array_interfaces_array);
    _the_empty_klass_bitmap      = Klass::compute_secondary_supers_bitmap(_the_empty_klass_array);

    initialize_basic_type_klass(_fillerArrayKlass, CHECK);

    initialize_basic_type_klass(boolArrayKlass(), CHECK);
    initialize_basic_type_klass(charArrayKlass(), CHECK);
    initialize_basic_type_klass(floatArrayKlass(), CHECK);
    initialize_basic_type_klass(doubleArrayKlass(), CHECK);
    initialize_basic_type_klass(byteArrayKlass(), CHECK);
    initialize_basic_type_klass(shortArrayKlass(), CHECK);
    initialize_basic_type_klass(intArrayKlass(), CHECK);
    initialize_basic_type_klass(longArrayKlass(), CHECK);

    assert(_fillerArrayKlass != intArrayKlass(),
           "Internal filler array klass should be different to int array Klass");
  } // end of core bootstrapping

  {
    Handle tns = java_lang_String::create_from_str("<null_sentinel>", CHECK);
    _the_null_sentinel = OopHandle(vm_global(), tns());
  }

  // Create a handle for reference_pending_list
  _reference_pending_list = OopHandle(vm_global(), nullptr);

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
  {
    Klass* oak = vmClasses::Object_klass()->array_klass(CHECK);
    _objectArrayKlass = ObjArrayKlass::cast(oak);
  }
  // OLD
  // Add the class to the class hierarchy manually to make sure that
  // its vtable is initialized after core bootstrapping is completed.
  // ---
  // New
  // Have already been initialized.
  _objectArrayKlass->append_to_sibling_list();

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
    int size = FullGCALotDummies * 2;

    objArrayOop    naked_array = oopFactory::new_objArray(vmClasses::Object_klass(), size, CHECK);
    objArrayHandle dummy_array(THREAD, naked_array);
    int i = 0;
    while (i < size) {
        // Allocate dummy in old generation
      oop dummy = vmClasses::Object_klass()->allocate_instance(CHECK);
      dummy_array->obj_at_put(i++, dummy);
    }
    {
      // Only modify the global variable inside the mutex.
      // If we had a race to here, the other dummy_array instances
      // and their elements just get dropped on the floor, which is fine.
      MutexLocker ml(THREAD, FullGCALot_lock);
      if (_fullgc_alot_dummy_array.is_empty()) {
        _fullgc_alot_dummy_array = OopHandle(vm_global(), dummy_array());
      }
    }
    assert(i == ((objArrayOop)_fullgc_alot_dummy_array.resolve())->length(), "just checking");
  }
  #endif
}

void Universe::initialize_basic_type_mirrors(TRAPS) {
#if INCLUDE_CDS_JAVA_HEAP
    if (CDSConfig::is_using_archive() &&
        ArchiveHeapLoader::is_in_use() &&
        _basic_type_mirrors[T_INT].resolve() != nullptr) {
      assert(ArchiveHeapLoader::can_use(), "Sanity");

      // check that all basic type mirrors are mapped also
      for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
        if (!is_reference_type((BasicType)i)) {
          oop m = _basic_type_mirrors[i].resolve();
          assert(m != nullptr, "archived mirrors should not be null");
        }
      }
    } else
      // _basic_type_mirrors[T_INT], etc, are null if archived heap is not mapped.
#endif
    {
      for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
        BasicType bt = (BasicType)i;
        if (!is_reference_type(bt)) {
          oop m = java_lang_Class::create_basic_type_mirror(type2name(bt), bt, CHECK);
          _basic_type_mirrors[i] = OopHandle(vm_global(), m);
        }
        CDS_JAVA_HEAP_ONLY(_archived_basic_type_mirror_indices[i] = -1);
      }
    }
    if (CDSConfig::is_dumping_heap()) {
      HeapShared::init_scratch_objects_for_basic_type_mirrors(CHECK);
    }
}

void Universe::fixup_mirrors(TRAPS) {
  // Bootstrap problem: all classes gets a mirror (java.lang.Class instance) assigned eagerly,
  // but we cannot do that for classes created before java.lang.Class is loaded. Here we simply
  // walk over permanent objects created so far (mostly classes) and fixup their mirrors. Note
  // that the number of objects allocated at this point is very small.
  assert(vmClasses::Class_klass_loaded(), "java.lang.Class should be loaded");
  HandleMark hm(THREAD);

  if (!CDSConfig::is_using_archive()) {
    // Cache the start of the static fields
    InstanceMirrorKlass::init_offset_of_static_fields();
  }

  GrowableArray <Klass*>* list = java_lang_Class::fixup_mirror_list();
  int list_length = list->length();
  for (int i = 0; i < list_length; i++) {
    Klass* k = list->at(i);
    assert(k->is_klass(), "List should only hold classes");
    java_lang_Class::fixup_mirror(k, CATCH);
  }
  delete java_lang_Class::fixup_mirror_list();
  java_lang_Class::set_fixup_mirror_list(nullptr);
}

#define assert_pll_locked(test) \
  assert(Heap_lock->test(), "Reference pending list access requires lock")

#define assert_pll_ownership() assert_pll_locked(owned_by_self)

oop Universe::reference_pending_list() {
  if (Thread::current()->is_VM_thread()) {
    assert_pll_locked(is_locked);
  } else {
    assert_pll_ownership();
  }
  return _reference_pending_list.resolve();
}

void Universe::clear_reference_pending_list() {
  assert_pll_ownership();
  _reference_pending_list.replace(nullptr);
}

bool Universe::has_reference_pending_list() {
  assert_pll_ownership();
  return _reference_pending_list.peek() != nullptr;
}

oop Universe::swap_reference_pending_list(oop list) {
  assert_pll_locked(is_locked);
  return _reference_pending_list.xchg(list);
}

#undef assert_pll_locked
#undef assert_pll_ownership

static void reinitialize_vtables() {
  // The vtables are initialized by starting at java.lang.Object and
  // initializing through the subclass links, so that the super
  // classes are always initialized first.
  for (ClassHierarchyIterator iter(vmClasses::Object_klass()); !iter.done(); iter.next()) {
    Klass* sub = iter.klass();
    sub->vtable().initialize_vtable();
  }
}

static void reinitialize_itables() {

  class ReinitTableClosure : public KlassClosure {
   public:
    void do_klass(Klass* k) {
      if (k->is_instance_klass()) {
         InstanceKlass::cast(k)->itable().initialize_itable();
      }
    }
  };

  MutexLocker mcld(ClassLoaderDataGraph_lock);
  ReinitTableClosure cl;
  ClassLoaderDataGraph::classes_do(&cl);
}

bool Universe::on_page_boundary(void* addr) {
  return is_aligned(addr, os::vm_page_size());
}

// the array of preallocated errors with backtraces
objArrayOop Universe::preallocated_out_of_memory_errors() {
  return (objArrayOop)_preallocated_out_of_memory_error_array.resolve();
}

objArrayOop Universe::out_of_memory_errors() { return (objArrayOop)_out_of_memory_errors.resolve(); }

oop Universe::out_of_memory_error_java_heap() {
  return gen_out_of_memory_error(out_of_memory_errors()->obj_at(_oom_java_heap));
}

oop Universe::out_of_memory_error_java_heap_without_backtrace() {
  return out_of_memory_errors()->obj_at(_oom_java_heap);
}

oop Universe::out_of_memory_error_c_heap() {
  return gen_out_of_memory_error(out_of_memory_errors()->obj_at(_oom_c_heap));
}

oop Universe::out_of_memory_error_metaspace() {
  return gen_out_of_memory_error(out_of_memory_errors()->obj_at(_oom_metaspace));
}

oop Universe::out_of_memory_error_class_metaspace() {
  return gen_out_of_memory_error(out_of_memory_errors()->obj_at(_oom_class_metaspace));
}

oop Universe::out_of_memory_error_array_size() {
  return gen_out_of_memory_error(out_of_memory_errors()->obj_at(_oom_array_size));
}

oop Universe::out_of_memory_error_gc_overhead_limit() {
  return gen_out_of_memory_error(out_of_memory_errors()->obj_at(_oom_gc_overhead_limit));
}

oop Universe::out_of_memory_error_realloc_objects() {
  return gen_out_of_memory_error(out_of_memory_errors()->obj_at(_oom_realloc_objects));
}

oop Universe::class_init_out_of_memory_error()         { return out_of_memory_errors()->obj_at(_oom_java_heap); }
oop Universe::class_init_stack_overflow_error()        { return _class_init_stack_overflow_error.resolve(); }
oop Universe::delayed_stack_overflow_error_message()   { return _delayed_stack_overflow_error_message.resolve(); }


bool Universe::should_fill_in_stack_trace(Handle throwable) {
  // never attempt to fill in the stack trace of preallocated errors that do not have
  // backtrace. These errors are kept alive forever and may be "re-used" when all
  // preallocated errors with backtrace have been consumed. Also need to avoid
  // a potential loop which could happen if an out of memory occurs when attempting
  // to allocate the backtrace.
  objArrayOop preallocated_oom = out_of_memory_errors();
  for (int i = 0; i < _oom_count; i++) {
    if (throwable() == preallocated_oom->obj_at(i)) {
      return false;
    }
  }
  return true;
}


oop Universe::gen_out_of_memory_error(oop default_err) {
  // generate an out of memory error:
  // - if there is a preallocated error and stack traces are available
  //   (j.l.Throwable is initialized), then return the preallocated
  //   error with a filled in stack trace, and with the message
  //   provided by the default error.
  // - otherwise, return the default error, without a stack trace.
  int next;
  if ((_preallocated_out_of_memory_error_avail_count > 0) &&
      vmClasses::Throwable_klass()->is_initialized()) {
    next = (int)Atomic::add(&_preallocated_out_of_memory_error_avail_count, -1);
    assert(next < (int)PreallocatedOutOfMemoryErrorCount, "avail count is corrupt");
  } else {
    next = -1;
  }
  if (next < 0) {
    // all preallocated errors have been used.
    // return default
    return default_err;
  } else {
    JavaThread* current = JavaThread::current();
    Handle default_err_h(current, default_err);
    // get the error object at the slot and set set it to null so that the
    // array isn't keeping it alive anymore.
    Handle exc(current, preallocated_out_of_memory_errors()->obj_at(next));
    assert(exc() != nullptr, "slot has been used already");
    preallocated_out_of_memory_errors()->obj_at_put(next, nullptr);

    // use the message from the default error
    oop msg = java_lang_Throwable::message(default_err_h());
    assert(msg != nullptr, "no message");
    java_lang_Throwable::set_message(exc(), msg);

    // populate the stack trace and return it.
    java_lang_Throwable::fill_in_stack_trace_of_preallocated_backtrace(exc);
    return exc();
  }
}

bool Universe::is_out_of_memory_error_metaspace(oop ex_obj) {
  return java_lang_Throwable::message(ex_obj) == _msg_metaspace.resolve();
}

bool Universe::is_out_of_memory_error_class_metaspace(oop ex_obj) {
  return java_lang_Throwable::message(ex_obj) == _msg_class_metaspace.resolve();
}

// Setup preallocated OutOfMemoryError errors
void Universe::create_preallocated_out_of_memory_errors(TRAPS) {
  InstanceKlass* ik = vmClasses::OutOfMemoryError_klass();
  objArrayOop oa = oopFactory::new_objArray(ik, _oom_count, CHECK);
  objArrayHandle oom_array(THREAD, oa);

  for (int i = 0; i < _oom_count; i++) {
    oop oom_obj = ik->allocate_instance(CHECK);
    oom_array->obj_at_put(i, oom_obj);
  }
  _out_of_memory_errors = OopHandle(vm_global(), oom_array());

  Handle msg = java_lang_String::create_from_str("Java heap space", CHECK);
  java_lang_Throwable::set_message(oom_array->obj_at(_oom_java_heap), msg());

  msg = java_lang_String::create_from_str("C heap space", CHECK);
  java_lang_Throwable::set_message(oom_array->obj_at(_oom_c_heap), msg());

  msg = java_lang_String::create_from_str("Metaspace", CHECK);
  _msg_metaspace = OopHandle(vm_global(), msg());
  java_lang_Throwable::set_message(oom_array->obj_at(_oom_metaspace), msg());

  msg = java_lang_String::create_from_str("Compressed class space", CHECK);
  _msg_class_metaspace = OopHandle(vm_global(), msg());
  java_lang_Throwable::set_message(oom_array->obj_at(_oom_class_metaspace), msg());

  msg = java_lang_String::create_from_str("Requested array size exceeds VM limit", CHECK);
  java_lang_Throwable::set_message(oom_array->obj_at(_oom_array_size), msg());

  msg = java_lang_String::create_from_str("GC overhead limit exceeded", CHECK);
  java_lang_Throwable::set_message(oom_array->obj_at(_oom_gc_overhead_limit), msg());

  msg = java_lang_String::create_from_str("Java heap space: failed reallocation of scalar replaced objects", CHECK);
  java_lang_Throwable::set_message(oom_array->obj_at(_oom_realloc_objects), msg());

  // Setup the array of errors that have preallocated backtrace
  int len = (StackTraceInThrowable) ? (int)PreallocatedOutOfMemoryErrorCount : 0;
  objArrayOop instance = oopFactory::new_objArray(ik, len, CHECK);
  _preallocated_out_of_memory_error_array = OopHandle(vm_global(), instance);
  objArrayHandle preallocated_oom_array(THREAD, instance);

  for (int i=0; i<len; i++) {
    oop err = ik->allocate_instance(CHECK);
    Handle err_h(THREAD, err);
    java_lang_Throwable::allocate_backtrace(err_h, CHECK);
    preallocated_oom_array->obj_at_put(i, err_h());
  }
  _preallocated_out_of_memory_error_avail_count = (jint)len;
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

bool Universe::contains_non_oop_word(void* p) {
  return *(void**)p == non_oop_word();
}

static void initialize_global_behaviours() {
  DefaultICProtectionBehaviour* protection_behavior = new DefaultICProtectionBehaviour();
  // Ignore leak of DefaultICProtectionBehaviour. It is overriden by some GC implementations and the
  // pointer is leaked once.
  LSAN_IGNORE_OBJECT(protection_behavior);
  CompiledICProtectionBehaviour::set_current(protection_behavior);
}

jint universe_init() {
  assert(!Universe::_fully_initialized, "called after initialize_vtables");
  guarantee(1 << LogHeapWordSize == sizeof(HeapWord),
         "LogHeapWordSize is incorrect.");
  guarantee(sizeof(oop) >= sizeof(HeapWord), "HeapWord larger than oop?");
  guarantee(sizeof(oop) % sizeof(HeapWord) == 0,
            "oop size is not not a multiple of HeapWord size");

  TraceTime timer("Genesis", TRACETIME_LOG(Info, startuptime));

  initialize_global_behaviours();

  GCLogPrecious::initialize();

  // Initialize CPUTimeCounters object, which must be done before creation of the heap.
  CPUTimeCounters::initialize();

  ObjLayout::initialize();

#ifdef _LP64
  MetaspaceShared::adjust_heap_sizes_for_dumping();
#endif // _LP64

  GCConfig::arguments()->initialize_heap_sizes();

  jint status = Universe::initialize_heap();
  if (status != JNI_OK) {
    return status;
  }

  Universe::initialize_tlab();

  Metaspace::global_initialize();

  // Initialize performance counters for metaspaces
  MetaspaceCounters::initialize_performance_counters();

  // Checks 'AfterMemoryInit' constraints.
  if (!JVMFlagLimit::check_all_constraints(JVMFlagConstraintPhase::AfterMemoryInit)) {
    return JNI_EINVAL;
  }

#if INCLUDE_CDS
  if (CDSConfig::is_using_archive()) {
    // Read the data structures supporting the shared spaces (shared
    // system dictionary, symbol table, etc.)
    MetaspaceShared::initialize_shared_spaces();
  }
#endif

  ClassLoaderData::init_null_class_loader_data();

#if INCLUDE_CDS
#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_using_full_module_graph()) {
    ClassLoaderDataShared::restore_archived_entries_for_null_class_loader_data();
  }
#endif // INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_archive()) {
    CDSConfig::prepare_for_dumping();
  }
#endif

  SymbolTable::create_table();
  StringTable::create_table();

  if (strlen(VerifySubSet) > 0) {
    Universe::initialize_verify_flags();
  }

  ResolvedMethodTable::create_table();

  return JNI_OK;
}

jint Universe::initialize_heap() {
  assert(_collectedHeap == nullptr, "Heap already created");
  _collectedHeap = GCConfig::arguments()->create_heap();

  log_info(gc)("Using %s", _collectedHeap->name());
  return _collectedHeap->initialize();
}

void Universe::initialize_tlab() {
  ThreadLocalAllocBuffer::set_max_size(Universe::heap()->max_tlab_size());
  PLAB::startup_initialization();
  if (UseTLAB) {
    ThreadLocalAllocBuffer::startup_initialization();
  }
}

ReservedHeapSpace Universe::reserve_heap(size_t heap_size, size_t alignment) {

  assert(alignment <= Arguments::conservative_max_heap_alignment(),
         "actual alignment %zu must be within maximum heap alignment %zu",
         alignment, Arguments::conservative_max_heap_alignment());
  assert(is_aligned(heap_size, alignment), "precondition");

  size_t total_reserved = heap_size;
  assert(!UseCompressedOops || (total_reserved <= (OopEncodingHeapMax - os::vm_page_size())),
      "heap size is too big for compressed oops");

  size_t page_size = os::vm_page_size();
  if (UseLargePages && is_aligned(alignment, os::large_page_size())) {
    page_size = os::large_page_size();
  } else {
    // Parallel is the only collector that might opt out of using large pages
    // for the heap.
    assert(!UseLargePages || UseParallelGC , "Wrong alignment to use large pages");
  }

  // Now create the space.
  ReservedHeapSpace rhs = HeapReserver::reserve(total_reserved, alignment, page_size, AllocateHeapAt);

  if (!rhs.is_reserved()) {
    vm_exit_during_initialization(
      err_msg("Could not reserve enough space for %zu KB object heap",
              total_reserved/K));
  }

  assert(total_reserved == rhs.size(),    "must be exactly of required size");
  assert(is_aligned(rhs.base(),alignment),"must be exactly of required alignment");

  assert(markWord::encode_pointer_as_mark(rhs.base()).decode_pointer() == rhs.base(),
      "area must be distinguishable from marks for mark-sweep");
  assert(markWord::encode_pointer_as_mark(&rhs.base()[rhs.size()]).decode_pointer() ==
      &rhs.base()[rhs.size()],
      "area must be distinguishable from marks for mark-sweep");

  // We are good.

  if (AllocateHeapAt != nullptr) {
    log_info(gc,heap)("Successfully allocated Java heap at location %s", AllocateHeapAt);
  }

  if (UseCompressedOops) {
    CompressedOops::initialize(rhs);
  }

  Universe::calculate_verify_data((HeapWord*)rhs.base(), (HeapWord*)rhs.end());

  return rhs;
}

OopStorage* Universe::vm_weak() {
  return Universe::_vm_weak;
}

OopStorage* Universe::vm_global() {
  return Universe::_vm_global;
}

void Universe::oopstorage_init() {
  Universe::_vm_global = OopStorageSet::create_strong("VM Global", mtInternal);
  Universe::_vm_weak = OopStorageSet::create_weak("VM Weak", mtInternal);
}

void universe_oopstorage_init() {
  Universe::oopstorage_init();
}

void LatestMethodCache::init(JavaThread* current, InstanceKlass* ik,
                             const char* method, Symbol* signature, bool is_static)
{
  TempNewSymbol name = SymbolTable::new_symbol(method);
  Method* m = nullptr;
  // The klass must be linked before looking up the method.
  if (!ik->link_class_or_fail(current) ||
      ((m = ik->find_method(name, signature)) == nullptr) ||
      is_static != m->is_static()) {
    ResourceMark rm(current);
    // NoSuchMethodException doesn't actually work because it tries to run the
    // <init> function before java_lang_Class is linked. Print error and exit.
    vm_exit_during_initialization(err_msg("Unable to link/verify %s.%s method",
                                 ik->name()->as_C_string(), method));
  }

  _klass = ik;
  _method_idnum = m->method_idnum();
  assert(_method_idnum >= 0, "sanity check");
}

Method* LatestMethodCache::get_method() {
  if (_klass == nullptr) {
    return nullptr;
  } else {
    Method* m = _klass->method_with_idnum(_method_idnum);
    assert(m != nullptr, "sanity check");
    return m;
  }
}

Method* Universe::finalizer_register_method()     { return _finalizer_register_cache.get_method(); }
Method* Universe::loader_addClass_method()        { return _loader_addClass_cache.get_method(); }
Method* Universe::throw_illegal_access_error()    { return _throw_illegal_access_error_cache.get_method(); }
Method* Universe::throw_no_such_method_error()    { return _throw_no_such_method_error_cache.get_method(); }
Method* Universe::do_stack_walk_method()          { return _do_stack_walk_cache.get_method(); }

void Universe::initialize_known_methods(JavaThread* current) {
  // Set up static method for registering finalizers
  _finalizer_register_cache.init(current,
                          vmClasses::Finalizer_klass(),
                          "register",
                          vmSymbols::object_void_signature(), true);

  _throw_illegal_access_error_cache.init(current,
                          vmClasses::internal_Unsafe_klass(),
                          "throwIllegalAccessError",
                          vmSymbols::void_method_signature(), true);

  _throw_no_such_method_error_cache.init(current,
                          vmClasses::internal_Unsafe_klass(),
                          "throwNoSuchMethodError",
                          vmSymbols::void_method_signature(), true);

  // Set up method for registering loaded classes in class loader vector
  _loader_addClass_cache.init(current,
                          vmClasses::ClassLoader_klass(),
                          "addClass",
                          vmSymbols::class_void_signature(), false);

  // Set up method for stack walking
  _do_stack_walk_cache.init(current,
                          vmClasses::AbstractStackWalker_klass(),
                          "doStackWalk",
                          vmSymbols::doStackWalk_signature(), false);
}

void universe2_init() {
  EXCEPTION_MARK;
  Universe::genesis(CATCH);
}

// Set after initialization of the module runtime, call_initModuleRuntime
void universe_post_module_init() {
  Universe::_module_initialized = true;
}

bool universe_post_init() {
  assert(!is_init_completed(), "Error: initialization not yet completed!");
  Universe::_fully_initialized = true;
  EXCEPTION_MARK;
  if (!CDSConfig::is_using_archive()) {
    reinitialize_vtables();
    reinitialize_itables();
  }

  HandleMark hm(THREAD);
  // Setup preallocated empty java.lang.Class array for Method reflection.

  objArrayOop the_empty_class_array = oopFactory::new_objArray(vmClasses::Class_klass(), 0, CHECK_false);
  Universe::_the_empty_class_array = OopHandle(Universe::vm_global(), the_empty_class_array);

  // Setup preallocated OutOfMemoryError errors
  Universe::create_preallocated_out_of_memory_errors(CHECK_false);

  oop instance;
  // Setup preallocated cause message for delayed StackOverflowError
  if (StackReservedPages > 0) {
    instance = java_lang_String::create_oop_from_str("Delayed StackOverflowError due to ReservedStackAccess annotated method", CHECK_false);
    Universe::_delayed_stack_overflow_error_message = OopHandle(Universe::vm_global(), instance);
  }

  // Setup preallocated exceptions used for a cheap & dirty solution in compiler exception handling
  _null_ptr_exception.init_if_empty(vmSymbols::java_lang_NullPointerException(), CHECK_false);
  _arithmetic_exception.init_if_empty(vmSymbols::java_lang_ArithmeticException(), CHECK_false);
  _array_index_out_of_bounds_exception.init_if_empty(vmSymbols::java_lang_ArrayIndexOutOfBoundsException(), CHECK_false);
  _array_store_exception.init_if_empty(vmSymbols::java_lang_ArrayStoreException(), CHECK_false);
  _class_cast_exception.init_if_empty(vmSymbols::java_lang_ClassCastException(), CHECK_false);

  // Virtual Machine Error for when we get into a situation we can't resolve
  Klass* k = vmClasses::InternalError_klass();
  bool linked = InstanceKlass::cast(k)->link_class_or_fail(CHECK_false);
  if (!linked) {
     tty->print_cr("Unable to link/verify InternalError class");
     return false; // initialization failed
  }
  _internal_error.init_if_empty(vmSymbols::java_lang_InternalError(), CHECK_false);

  Handle msg = java_lang_String::create_from_str("/ by zero", CHECK_false);
  java_lang_Throwable::set_message(Universe::arithmetic_exception_instance(), msg());

  // Setup preallocated StackOverflowError for use with class initialization failure
  k = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_StackOverflowError(), true, CHECK_false);
  instance = InstanceKlass::cast(k)->allocate_instance(CHECK_false);
  Universe::_class_init_stack_overflow_error = OopHandle(Universe::vm_global(), instance);

  Universe::initialize_known_methods(THREAD);

  // This needs to be done before the first scavenge/gc, since
  // it's an input to soft ref clearing policy.
  {
    MutexLocker x(THREAD, Heap_lock);
    Universe::heap()->update_capacity_and_used_at_gc();
  }

  // ("weak") refs processing infrastructure initialization
  Universe::heap()->post_initialize();

  MemoryService::add_metaspace_memory_pools();

  MemoryService::set_universe_heap(Universe::heap());
#if INCLUDE_CDS
  MetaspaceShared::post_initialize(CHECK_false);
#endif
  return true;
}


void Universe::compute_base_vtable_size() {
  _base_vtable_size = ClassLoader::compute_Object_vtable();
}

void Universe::print_on(outputStream* st) {
  GCMutexLocker hl(Heap_lock); // Heap_lock might be locked by caller thread.
  st->print_cr("Heap");

  StreamIndentor si(st, 1);
  heap()->print_heap_on(st);
  MetaspaceUtils::print_on(st);
}

void Universe::print_heap_at_SIGBREAK() {
  if (PrintHeapAtSIGBREAK) {
    print_on(tty);
    tty->cr();
    tty->flush();
  }
}

void Universe::initialize_verify_flags() {
  verify_flags = 0;
  const char delimiter[] = " ,";

  size_t length = strlen(VerifySubSet);
  char* subset_list = NEW_C_HEAP_ARRAY(char, length + 1, mtInternal);
  strncpy(subset_list, VerifySubSet, length + 1);
  char* save_ptr;

  char* token = strtok_r(subset_list, delimiter, &save_ptr);
  while (token != nullptr) {
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
      verify_flags |= Verify_MetaspaceUtils;
    } else if (strcmp(token, "jni_handles") == 0) {
      verify_flags |= Verify_JNIHandles;
    } else if (strcmp(token, "codecache_oops") == 0) {
      verify_flags |= Verify_CodeCacheOops;
    } else if (strcmp(token, "resolved_method_table") == 0) {
      verify_flags |= Verify_ResolvedMethodTable;
    } else if (strcmp(token, "stringdedup") == 0) {
      verify_flags |= Verify_StringDedup;
    } else {
      vm_exit_during_initialization(err_msg("VerifySubSet: \'%s\' memory sub-system is unknown, please correct it", token));
    }
    token = strtok_r(nullptr, delimiter, &save_ptr);
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
  COMPILER2_PRESENT(
    assert(!DerivedPointerTable::is_active(),
         "DPT should not be active during verification "
         "(of thread stacks below)");
  )

  Thread* thread = Thread::current();
  ResourceMark rm(thread);
  HandleMark hm(thread);  // Handles created during verification can be zapped
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
    log_debug(gc, verify)("CodeCache");
    CodeCache::verify();
  }
  if (should_verify_subset(Verify_SystemDictionary)) {
    log_debug(gc, verify)("SystemDictionary");
    SystemDictionary::verify();
  }
  if (should_verify_subset(Verify_ClassLoaderDataGraph)) {
    log_debug(gc, verify)("ClassLoaderDataGraph");
    ClassLoaderDataGraph::verify();
  }
  if (should_verify_subset(Verify_MetaspaceUtils)) {
    log_debug(gc, verify)("MetaspaceUtils");
    DEBUG_ONLY(MetaspaceUtils::verify();)
  }
  if (should_verify_subset(Verify_JNIHandles)) {
    log_debug(gc, verify)("JNIHandles");
    JNIHandles::verify();
  }
  if (should_verify_subset(Verify_CodeCacheOops)) {
    log_debug(gc, verify)("CodeCache Oops");
    CodeCache::verify_oops();
  }
  if (should_verify_subset(Verify_ResolvedMethodTable)) {
    log_debug(gc, verify)("ResolvedMethodTable Oops");
    ResolvedMethodTable::verify();
  }
  if (should_verify_subset(Verify_StringDedup)) {
    log_debug(gc, verify)("String Deduplication");
    StringDedup::verify();
  }
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

void Universe::set_verify_data(uintptr_t mask, uintptr_t bits) {
  _verify_oop_mask = mask;
  _verify_oop_bits = bits;
}

// Oop verification (see MacroAssembler::verify_oop)

uintptr_t Universe::verify_oop_mask() {
  return _verify_oop_mask;
}

uintptr_t Universe::verify_oop_bits() {
  return _verify_oop_bits;
}

uintptr_t Universe::verify_mark_mask() {
  return markWord::lock_mask_in_place;
}

uintptr_t Universe::verify_mark_bits() {
  intptr_t mask = verify_mark_mask();
  intptr_t bits = (intptr_t)markWord::prototype().value();
  assert((bits & ~mask) == 0, "no stray header bits");
  return bits;
}
#endif // PRODUCT

#ifdef ASSERT
// Release dummy object(s) at bottom of heap
bool Universe::release_fullgc_alot_dummy() {
  MutexLocker ml(FullGCALot_lock);
  objArrayOop fullgc_alot_dummy_array = (objArrayOop)_fullgc_alot_dummy_array.resolve();
  if (fullgc_alot_dummy_array != nullptr) {
    if (_fullgc_alot_dummy_next >= fullgc_alot_dummy_array->length()) {
      // No more dummies to release, release entire array instead
      _fullgc_alot_dummy_array.release(Universe::vm_global());
      _fullgc_alot_dummy_array = OopHandle(); // null out OopStorage pointer.
      return false;
    }

    // Release dummy at bottom of old generation
    fullgc_alot_dummy_array->obj_at_put(_fullgc_alot_dummy_next++, nullptr);
  }
  return true;
}

bool Universe::is_stw_gc_active() {
  return heap()->is_stw_gc_active();
}

bool Universe::is_in_heap(const void* p) {
  return heap()->is_in(p);
}

#endif // ASSERT

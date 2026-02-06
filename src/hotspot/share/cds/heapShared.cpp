/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotArtifactFinder.hpp"
#include "cds/aotClassInitializer.hpp"
#include "cds/aotClassLocation.hpp"
#include "cds/aotLogging.hpp"
#include "cds/aotMappedHeapLoader.hpp"
#include "cds/aotMappedHeapWriter.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/aotOopChecker.hpp"
#include "cds/aotReferenceObjSupport.hpp"
#include "cds/aotStreamedHeapLoader.hpp"
#include "cds/aotStreamedHeapWriter.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cdsEnumKlass.hpp"
#include "cds/cdsHeapVerifier.hpp"
#include "cds/heapShared.inline.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/modules.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/init.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/copy.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#endif

#if INCLUDE_CDS_JAVA_HEAP

struct ArchivableStaticFieldInfo {
  const char* klass_name;
  const char* field_name;
  InstanceKlass* klass;
  int offset;
  BasicType type;

  ArchivableStaticFieldInfo(const char* k, const char* f)
  : klass_name(k), field_name(f), klass(nullptr), offset(0), type(T_ILLEGAL) {}

  bool valid() {
    return klass_name != nullptr;
  }
};

// Anything that goes in the header must be thoroughly purged from uninitialized memory
// as it will be written to disk. Therefore, the constructors memset the memory to 0.
// This is not the prettiest thing, but we need to know every byte is initialized,
// including potential padding between fields.

ArchiveMappedHeapHeader::ArchiveMappedHeapHeader(size_t ptrmap_start_pos,
                                                 size_t oopmap_start_pos,
                                                 HeapRootSegments root_segments) {
  memset((char*)this, 0, sizeof(*this));
  _ptrmap_start_pos = ptrmap_start_pos;
  _oopmap_start_pos = oopmap_start_pos;
  _root_segments = root_segments;
}

ArchiveMappedHeapHeader::ArchiveMappedHeapHeader() {
  memset((char*)this, 0, sizeof(*this));
}

ArchiveMappedHeapHeader ArchiveMappedHeapInfo::create_header() {
  return ArchiveMappedHeapHeader{_ptrmap_start_pos,
                                 _oopmap_start_pos,
                                 _root_segments};
}

ArchiveStreamedHeapHeader::ArchiveStreamedHeapHeader(size_t forwarding_offset,
                                                     size_t roots_offset,
                                                     size_t num_roots,
                                                     size_t root_highest_object_index_table_offset,
                                                     size_t num_archived_objects) {
  memset((char*)this, 0, sizeof(*this));
  _forwarding_offset = forwarding_offset;
  _roots_offset = roots_offset;
  _num_roots = num_roots;
  _root_highest_object_index_table_offset = root_highest_object_index_table_offset;
  _num_archived_objects = num_archived_objects;
}

ArchiveStreamedHeapHeader::ArchiveStreamedHeapHeader() {
  memset((char*)this, 0, sizeof(*this));
}

ArchiveStreamedHeapHeader ArchiveStreamedHeapInfo::create_header() {
  return ArchiveStreamedHeapHeader{_forwarding_offset,
                                   _roots_offset,
                                   _num_roots,
                                   _root_highest_object_index_table_offset,
                                   _num_archived_objects};
}

HeapArchiveMode HeapShared::_heap_load_mode = HeapArchiveMode::_uninitialized;
HeapArchiveMode HeapShared::_heap_write_mode = HeapArchiveMode::_uninitialized;

size_t HeapShared::_alloc_count[HeapShared::ALLOC_STAT_SLOTS];
size_t HeapShared::_alloc_size[HeapShared::ALLOC_STAT_SLOTS];
size_t HeapShared::_total_obj_count;
size_t HeapShared::_total_obj_size;

#ifndef PRODUCT
#define ARCHIVE_TEST_FIELD_NAME "archivedObjects"
static Array<char>* _archived_ArchiveHeapTestClass = nullptr;
static const char* _test_class_name = nullptr;
static Klass* _test_class = nullptr;
static const ArchivedKlassSubGraphInfoRecord* _test_class_record = nullptr;
#endif


//
// If you add new entries to the following tables, you should know what you're doing!
//

static ArchivableStaticFieldInfo archive_subgraph_entry_fields[] = {
  {"java/lang/Integer$IntegerCache",              "archivedCache"},
  {"java/lang/Long$LongCache",                    "archivedCache"},
  {"java/lang/Byte$ByteCache",                    "archivedCache"},
  {"java/lang/Short$ShortCache",                  "archivedCache"},
  {"java/lang/Character$CharacterCache",          "archivedCache"},
  {"java/util/jar/Attributes$Name",               "KNOWN_NAMES"},
  {"sun/util/locale/BaseLocale",                  "constantBaseLocales"},
  {"jdk/internal/module/ArchivedModuleGraph",     "archivedModuleGraph"},
  {"java/util/ImmutableCollections",              "archivedObjects"},
  {"java/lang/ModuleLayer",                       "EMPTY_LAYER"},
  {"java/lang/module/Configuration",              "EMPTY_CONFIGURATION"},
  {"jdk/internal/math/FDBigInteger",              "archivedCaches"},

#ifndef PRODUCT
  {nullptr, nullptr}, // Extra slot for -XX:ArchiveHeapTestClass
#endif
  {nullptr, nullptr},
};

// full module graph
static ArchivableStaticFieldInfo fmg_archive_subgraph_entry_fields[] = {
  {"jdk/internal/loader/ArchivedClassLoaders",    "archivedClassLoaders"},
  {ARCHIVED_BOOT_LAYER_CLASS,                     ARCHIVED_BOOT_LAYER_FIELD},
  {"java/lang/Module$ArchivedData",               "archivedData"},
  {nullptr, nullptr},
};

KlassSubGraphInfo* HeapShared::_dump_time_special_subgraph;
ArchivedKlassSubGraphInfoRecord* HeapShared::_run_time_special_subgraph;
GrowableArrayCHeap<oop, mtClassShared>* HeapShared::_pending_roots = nullptr;
OopHandle HeapShared::_scratch_basic_type_mirrors[T_VOID+1];
MetaspaceObjToOopHandleTable* HeapShared::_scratch_objects_table = nullptr;

static bool is_subgraph_root_class_of(ArchivableStaticFieldInfo fields[], InstanceKlass* ik) {
  for (int i = 0; fields[i].valid(); i++) {
    if (fields[i].klass == ik) {
      return true;
    }
  }
  return false;
}

bool HeapShared::is_subgraph_root_class(InstanceKlass* ik) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");
  if (CDSConfig::is_dumping_klass_subgraphs()) {
    // Legacy CDS archive support (to be deprecated)
    return is_subgraph_root_class_of(archive_subgraph_entry_fields, ik) ||
           is_subgraph_root_class_of(fmg_archive_subgraph_entry_fields, ik);
  } else {
    return false;
  }
}

oop HeapShared::CachedOopInfo::orig_referrer() const {
  return _orig_referrer.resolve();
}

unsigned HeapShared::oop_hash(oop const& p) {
  assert(SafepointSynchronize::is_at_safepoint() ||
         JavaThread::current()->is_in_no_safepoint_scope(), "sanity");
  // Do not call p->identity_hash() as that will update the
  // object header.
  return primitive_hash(cast_from_oop<intptr_t>(p));
}

unsigned int HeapShared::oop_handle_hash_raw(const OopHandle& oh) {
  return oop_hash(oh.resolve());
}

unsigned int HeapShared::oop_handle_hash(const OopHandle& oh) {
  oop o = oh.resolve();
  if (o == nullptr) {
    return 0;
  } else {
    return o->identity_hash();
  }
}

bool HeapShared::oop_handle_equals(const OopHandle& a, const OopHandle& b) {
  return a.resolve() == b.resolve();
}

static void reset_states(oop obj, TRAPS) {
  Handle h_obj(THREAD, obj);
  InstanceKlass* klass = InstanceKlass::cast(obj->klass());
  TempNewSymbol method_name = SymbolTable::new_symbol("resetArchivedStates");
  Symbol* method_sig = vmSymbols::void_method_signature();

  while (klass != nullptr) {
    Method* method = klass->find_method(method_name, method_sig);
    if (method != nullptr) {
      assert(method->is_private(), "must be");
      if (log_is_enabled(Debug, aot)) {
        ResourceMark rm(THREAD);
        log_debug(aot)("  calling %s", method->name_and_sig_as_C_string());
      }
      JavaValue result(T_VOID);
      JavaCalls::call_special(&result, h_obj, klass,
                              method_name, method_sig, CHECK);
    }
    klass = klass->super();
  }
}

void HeapShared::reset_archived_object_states(TRAPS) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");
  log_debug(aot)("Resetting platform loader");
  reset_states(SystemDictionary::java_platform_loader(), CHECK);
  log_debug(aot)("Resetting system loader");
  reset_states(SystemDictionary::java_system_loader(), CHECK);

  // Clean up jdk.internal.loader.ClassLoaders::bootLoader(), which is not
  // directly used for class loading, but rather is used by the core library
  // to keep track of resources, etc, loaded by the null class loader.
  //
  // Note, this object is non-null, and is not the same as
  // ClassLoaderData::the_null_class_loader_data()->class_loader(),
  // which is null.
  log_debug(aot)("Resetting boot loader");
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         vmClasses::jdk_internal_loader_ClassLoaders_klass(),
                         vmSymbols::bootLoader_name(),
                         vmSymbols::void_BuiltinClassLoader_signature(),
                         CHECK);
  Handle boot_loader(THREAD, result.get_oop());
  reset_states(boot_loader(), CHECK);
}

HeapShared::ArchivedObjectCache* HeapShared::_archived_object_cache = nullptr;

bool HeapShared::is_archived_heap_in_use() {
  if (HeapShared::is_loading()) {
    if (HeapShared::is_loading_streaming_mode()) {
      return AOTStreamedHeapLoader::is_in_use();
    } else {
      return AOTMappedHeapLoader::is_in_use();
    }
  }

  return false;
}

bool HeapShared::can_use_archived_heap() {
  FileMapInfo* static_mapinfo = FileMapInfo::current_info();
  if (static_mapinfo == nullptr) {
    return false;
  }
  if (!static_mapinfo->has_heap_region()) {
    return false;
  }
  if (!static_mapinfo->object_streaming_mode() &&
      !Universe::heap()->can_load_archived_objects() &&
      !UseG1GC) {
    // Incompatible object format
    return false;
  }

  return true;
}

bool HeapShared::is_too_large_to_archive(size_t size) {
  if (HeapShared::is_writing_streaming_mode()) {
    return false;
  } else {
    return AOTMappedHeapWriter::is_too_large_to_archive(size);
  }
}

bool HeapShared::is_too_large_to_archive(oop obj) {
  if (HeapShared::is_writing_streaming_mode()) {
    return false;
  } else {
    return AOTMappedHeapWriter::is_too_large_to_archive(obj);
  }
}

bool HeapShared::is_string_too_large_to_archive(oop string) {
  typeArrayOop value = java_lang_String::value_no_keepalive(string);
  return is_too_large_to_archive(value);
}

void HeapShared::initialize_loading_mode(HeapArchiveMode mode) {
  assert(_heap_load_mode == HeapArchiveMode::_uninitialized, "already set?");
  assert(mode != HeapArchiveMode::_uninitialized, "sanity");
  _heap_load_mode = mode;
};

void HeapShared::initialize_writing_mode() {
  assert(!FLAG_IS_ERGO(AOTStreamableObjects), "Should not have been ergonomically set yet");

  if (!CDSConfig::is_dumping_archive()) {
    // We use FLAG_IS_CMDLINE below because we are specifically looking to warn
    // a user that explicitly sets the flag on the command line for a JVM that is
    // not dumping an archive.
    if (FLAG_IS_CMDLINE(AOTStreamableObjects)) {
      log_warning(cds)("-XX:%cAOTStreamableObjects was specified, "
                       "AOTStreamableObjects is only used for writing "
                       "the AOT cache.",
                       AOTStreamableObjects ? '+' : '-');
    }
  }

  // The below checks use !FLAG_IS_DEFAULT instead of FLAG_IS_CMDLINE
  // because the one step AOT cache creation transfers the AOTStreamableObjects
  // flag value from the training JVM to the assembly JVM using an environment
  // variable that sets the flag as ERGO in the assembly JVM.
  if (FLAG_IS_DEFAULT(AOTStreamableObjects)) {
    // By default, the value of AOTStreamableObjects should match !UseCompressedOops.
    FLAG_SET_DEFAULT(AOTStreamableObjects, !UseCompressedOops);
  } else if (!AOTStreamableObjects && UseZGC) {
    // Never write mapped heap with ZGC
    if (CDSConfig::is_dumping_archive()) {
      log_warning(cds)("Heap archiving without streaming not supported for -XX:+UseZGC");
    }
    FLAG_SET_ERGO(AOTStreamableObjects, true);
  }

  if (CDSConfig::is_dumping_archive()) {
    // Select default mode
    assert(_heap_write_mode == HeapArchiveMode::_uninitialized, "already initialized?");
    _heap_write_mode = AOTStreamableObjects ? HeapArchiveMode::_streaming : HeapArchiveMode::_mapping;
  }
}

void HeapShared::initialize_streaming() {
  assert(is_loading_streaming_mode(), "shouldn't call this");
  if (can_use_archived_heap()) {
    AOTStreamedHeapLoader::initialize();
  }
}

void HeapShared::enable_gc() {
  if (AOTStreamedHeapLoader::is_in_use()) {
    AOTStreamedHeapLoader::enable_gc();
  }
}

void HeapShared::materialize_thread_object() {
  if (AOTStreamedHeapLoader::is_in_use()) {
    AOTStreamedHeapLoader::materialize_thread_object();
  }
}

void HeapShared::add_to_dumped_interned_strings(oop string) {
  assert(HeapShared::is_writing_mapping_mode(), "Only used by this mode");
  AOTMappedHeapWriter::add_to_dumped_interned_strings(string);
  bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, string);
  assert(success, "shared strings array must not point to arrays or strings that are too large to archive");
}

void HeapShared::finalize_initialization(FileMapInfo* static_mapinfo) {
  if (HeapShared::is_loading()) {
    if (HeapShared::is_loading_streaming_mode()) {
      // Heap initialization can be done only after vtables are initialized by ReadClosure.
      AOTStreamedHeapLoader::finish_initialization(static_mapinfo);
    } else {
      // Finish up archived heap initialization. These must be
      // done after ReadClosure.
      AOTMappedHeapLoader::finish_initialization(static_mapinfo);
    }
  }
}

HeapShared::CachedOopInfo* HeapShared::get_cached_oop_info(oop obj) {
  OopHandle oh(Universe::vm_global(), obj);
  CachedOopInfo* result = _archived_object_cache->get(oh);
  oh.release(Universe::vm_global());
  return result;
}

bool HeapShared::has_been_archived(oop obj) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");
  return get_cached_oop_info(obj) != nullptr;
}

int HeapShared::append_root(oop obj) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");
  if (obj != nullptr) {
    assert(has_been_archived(obj), "must be");
  }
  // No GC should happen since we aren't scanning _pending_roots.
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");

  return _pending_roots->append(obj);
}

oop HeapShared::get_root(int index, bool clear) {
  assert(index >= 0, "sanity");
  assert(is_archived_heap_in_use(), "getting roots into heap that is not used");

  oop result;
  if (HeapShared::is_loading_streaming_mode()) {
    result = AOTStreamedHeapLoader::get_root(index);
  } else {
    assert(HeapShared::is_loading_mapping_mode(), "must be");
    result = AOTMappedHeapLoader::get_root(index);
  }

  if (clear) {
    clear_root(index);
  }

  return result;
}

void HeapShared::finish_materialize_objects() {
  if (AOTStreamedHeapLoader::is_in_use()) {
    AOTStreamedHeapLoader::finish_materialize_objects();
  }
}

void HeapShared::clear_root(int index) {
  assert(index >= 0, "sanity");
  assert(CDSConfig::is_using_archive(), "must be");
  if (is_archived_heap_in_use()) {
    if (log_is_enabled(Debug, aot, heap)) {
      log_debug(aot, heap)("Clearing root %d: was %zu", index, p2i(get_root(index, false /* clear */)));
    }
    if (HeapShared::is_loading_streaming_mode()) {
      AOTStreamedHeapLoader::clear_root(index);
    } else {
      assert(HeapShared::is_loading_mapping_mode(), "must be");
      AOTMappedHeapLoader::clear_root(index);
    }
  }
}

bool HeapShared::archive_object(oop obj, oop referrer, KlassSubGraphInfo* subgraph_info) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");

  assert(!obj->is_stackChunk(), "do not archive stack chunks");
  if (has_been_archived(obj)) {
    return true;
  }

  if (is_too_large_to_archive(obj)) {
    log_debug(aot, heap)("Cannot archive, object (" PTR_FORMAT ") is too large: %zu",
                         p2i(obj), obj->size());
    debug_trace();
    return false;
  }

  AOTOopChecker::check(obj); // Make sure contents of this oop are safe.
  count_allocation(obj->size());

  if (HeapShared::is_writing_streaming_mode()) {
    AOTStreamedHeapWriter::add_source_obj(obj);
  } else {
    AOTMappedHeapWriter::add_source_obj(obj);
  }

  OopHandle oh(Universe::vm_global(), obj);
  CachedOopInfo info = make_cached_oop_info(obj, referrer);
  archived_object_cache()->put_when_absent(oh, info);
  archived_object_cache()->maybe_grow();

  Klass* k = obj->klass();
  if (k->is_instance_klass()) {
    // Whenever we see a non-array Java object of type X, we mark X to be aot-initialized.
    // This ensures that during the production run, whenever Java code sees a cached object
    // of type X, we know that X is already initialized. (see TODO comment below ...)

    if (InstanceKlass::cast(k)->is_enum_subclass()
        // We can't rerun <clinit> of enum classes (see cdsEnumKlass.cpp) so
        // we must store them as AOT-initialized.
        || (subgraph_info == _dump_time_special_subgraph))
        // TODO: we do this only for the special subgraph for now. Extending this to
        // other subgraphs would require more refactoring of the core library (such as
        // move some initialization logic into runtimeSetup()).
        //
        // For the other subgraphs, we have a weaker mechanism to ensure that
        // all classes in a subgraph are initialized before the subgraph is programmatically
        // returned from jdk.internal.misc.CDS::initializeFromArchive().
        // See HeapShared::initialize_from_archived_subgraph().
    {
      AOTArtifactFinder::add_aot_inited_class(InstanceKlass::cast(k));
    }

    if (java_lang_Class::is_instance(obj)) {
      Klass* mirror_k = java_lang_Class::as_Klass(obj);
      if (mirror_k != nullptr) {
        AOTArtifactFinder::add_cached_class(mirror_k);
      }
    } else if (java_lang_invoke_ResolvedMethodName::is_instance(obj)) {
      Method* m = java_lang_invoke_ResolvedMethodName::vmtarget(obj);
      if (m != nullptr) {
        if (RegeneratedClasses::has_been_regenerated(m)) {
          m = RegeneratedClasses::get_regenerated_object(m);
        }
        InstanceKlass* method_holder = m->method_holder();
        AOTArtifactFinder::add_cached_class(method_holder);
      }
    }
  }

  if (log_is_enabled(Debug, aot, heap)) {
    ResourceMark rm;
    LogTarget(Debug, aot, heap) log;
    LogStream out(log);
    out.print("Archived heap object " PTR_FORMAT " : %s ",
              p2i(obj), obj->klass()->external_name());
    if (java_lang_Class::is_instance(obj)) {
      Klass* k = java_lang_Class::as_Klass(obj);
      if (k != nullptr) {
        out.print("%s", k->external_name());
      } else {
        out.print("primitive");
      }
    }
    out.cr();
  }

  return true;
}

class MetaspaceObjToOopHandleTable: public HashTable<MetaspaceObj*, OopHandle,
    36137, // prime number
    AnyObj::C_HEAP,
    mtClassShared> {
public:
  oop get_oop(MetaspaceObj* ptr) {
    MutexLocker ml(ScratchObjects_lock, Mutex::_no_safepoint_check_flag);
    OopHandle* handle = get(ptr);
    if (handle != nullptr) {
      return handle->resolve();
    } else {
      return nullptr;
    }
  }
  void set_oop(MetaspaceObj* ptr, oop o) {
    MutexLocker ml(ScratchObjects_lock, Mutex::_no_safepoint_check_flag);
    OopHandle handle(Universe::vm_global(), o);
    put_when_absent(ptr, handle);
  }
  void remove_oop(MetaspaceObj* ptr) {
    MutexLocker ml(ScratchObjects_lock, Mutex::_no_safepoint_check_flag);
    OopHandle* handle = get(ptr);
    if (handle != nullptr) {
      handle->release(Universe::vm_global());
      remove(ptr);
    }
  }
};

void HeapShared::add_scratch_resolved_references(ConstantPool* src, objArrayOop dest) {
  if (CDSConfig::is_dumping_preimage_static_archive() && scratch_resolved_references(src) != nullptr) {
    // We are in AOT training run. The class has been redefined and we are giving it a new resolved_reference.
    // Ignore it, as this class will be excluded from the AOT config.
    return;
  }
  if (SystemDictionaryShared::is_builtin_loader(src->pool_holder()->class_loader_data())) {
    _scratch_objects_table->set_oop(src, dest);
  }
}

objArrayOop HeapShared::scratch_resolved_references(ConstantPool* src) {
  return (objArrayOop)_scratch_objects_table->get_oop(src);
}

 void HeapShared::init_dumping() {
   _scratch_objects_table = new (mtClass)MetaspaceObjToOopHandleTable();
   _pending_roots = new GrowableArrayCHeap<oop, mtClassShared>(500);
}

void HeapShared::init_scratch_objects_for_basic_type_mirrors(TRAPS) {
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    BasicType bt = (BasicType)i;
    if (!is_reference_type(bt)) {
      oop m = java_lang_Class::create_basic_type_mirror(type2name(bt), bt, CHECK);
      _scratch_basic_type_mirrors[i] = OopHandle(Universe::vm_global(), m);
    }
  }
}

// Given java_mirror that represents a (primitive or reference) type T,
// return the "scratch" version that represents the same type T. Note
// that java_mirror will be returned if the mirror is already a scratch mirror.
//
// See java_lang_Class::create_scratch_mirror() for more info.
oop HeapShared::scratch_java_mirror(oop java_mirror) {
  assert(java_lang_Class::is_instance(java_mirror), "must be");

  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    BasicType bt = (BasicType)i;
    if (!is_reference_type(bt)) {
      if (_scratch_basic_type_mirrors[i].resolve() == java_mirror) {
        return java_mirror;
      }
    }
  }

  if (java_lang_Class::is_primitive(java_mirror)) {
    return scratch_java_mirror(java_lang_Class::as_BasicType(java_mirror));
  } else {
    return scratch_java_mirror(java_lang_Class::as_Klass(java_mirror));
  }
}

oop HeapShared::scratch_java_mirror(BasicType t) {
  assert((uint)t < T_VOID+1, "range check");
  assert(!is_reference_type(t), "sanity");
  return _scratch_basic_type_mirrors[t].resolve();
}

oop HeapShared::scratch_java_mirror(Klass* k) {
  return _scratch_objects_table->get_oop(k);
}

void HeapShared::set_scratch_java_mirror(Klass* k, oop mirror) {
  _scratch_objects_table->set_oop(k, mirror);
}

void HeapShared::remove_scratch_objects(Klass* k) {
  // Klass is being deallocated. Java mirror can still be alive, and it should not
  // point to dead klass. We need to break the link from mirror to the Klass.
  // See how InstanceKlass::deallocate_contents does it for normal mirrors.
  oop mirror = _scratch_objects_table->get_oop(k);
  if (mirror != nullptr) {
    java_lang_Class::set_klass(mirror, nullptr);
  }
  _scratch_objects_table->remove_oop(k);
  if (k->is_instance_klass()) {
    _scratch_objects_table->remove(InstanceKlass::cast(k)->constants());
  }
}

//TODO: we eventually want a more direct test for these kinds of things.
//For example the JVM could record some bit of context from the creation
//of the klass, such as who called the hidden class factory.  Using
//string compares on names is fragile and will break as soon as somebody
//changes the names in the JDK code.  See discussion in JDK-8342481 for
//related ideas about marking AOT-related classes.
bool HeapShared::is_lambda_form_klass(InstanceKlass* ik) {
  return ik->is_hidden() &&
    (ik->name()->starts_with("java/lang/invoke/LambdaForm$MH+") ||
     ik->name()->starts_with("java/lang/invoke/LambdaForm$DMH+") ||
     ik->name()->starts_with("java/lang/invoke/LambdaForm$BMH+") ||
     ik->name()->starts_with("java/lang/invoke/LambdaForm$VH+"));
}

bool HeapShared::is_lambda_proxy_klass(InstanceKlass* ik) {
  return ik->is_hidden() && (ik->name()->index_of_at(0, "$$Lambda+", 9) > 0);
}

bool HeapShared::is_string_concat_klass(InstanceKlass* ik) {
  return ik->is_hidden() && ik->name()->starts_with("java/lang/String$$StringConcat");
}

bool HeapShared::is_archivable_hidden_klass(InstanceKlass* ik) {
  return CDSConfig::is_dumping_method_handles() &&
    (is_lambda_form_klass(ik) || is_lambda_proxy_klass(ik) || is_string_concat_klass(ik));
}


void HeapShared::copy_and_rescan_aot_inited_mirror(InstanceKlass* ik) {
  ik->set_has_aot_initialized_mirror();

  oop orig_mirror;
  if (RegeneratedClasses::is_regenerated_object(ik)) {
    InstanceKlass* orig_ik = RegeneratedClasses::get_original_object(ik);
    precond(orig_ik->is_initialized());
    orig_mirror = orig_ik->java_mirror();
  } else {
    precond(ik->is_initialized());
    orig_mirror = ik->java_mirror();
  }

  oop m = scratch_java_mirror(ik);
  int nfields = 0;
  for (JavaFieldStream fs(ik); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static()) {
      fieldDescriptor& fd = fs.field_descriptor();
      int offset = fd.offset();
      switch (fd.field_type()) {
      case T_OBJECT:
      case T_ARRAY:
        {
          oop field_obj = orig_mirror->obj_field(offset);
          if (offset == java_lang_Class::reflection_data_offset()) {
            // Class::reflectData use SoftReference, which cannot be archived. Set it
            // to null and it will be recreated at runtime.
            field_obj = nullptr;
          }
          m->obj_field_put(offset, field_obj);
          if (field_obj != nullptr) {
            bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, field_obj);
            assert(success, "sanity");
          }
        }
        break;
      case T_BOOLEAN:
        m->bool_field_put(offset, orig_mirror->bool_field(offset));
        break;
      case T_BYTE:
        m->byte_field_put(offset, orig_mirror->byte_field(offset));
        break;
      case T_SHORT:
        m->short_field_put(offset, orig_mirror->short_field(offset));
        break;
      case T_CHAR:
        m->char_field_put(offset, orig_mirror->char_field(offset));
        break;
      case T_INT:
        m->int_field_put(offset, orig_mirror->int_field(offset));
        break;
      case T_LONG:
        m->long_field_put(offset, orig_mirror->long_field(offset));
        break;
      case T_FLOAT:
        m->float_field_put(offset, orig_mirror->float_field(offset));
        break;
      case T_DOUBLE:
        m->double_field_put(offset, orig_mirror->double_field(offset));
        break;
      default:
        ShouldNotReachHere();
      }
      nfields ++;
    }
  }

  oop class_data = java_lang_Class::class_data(orig_mirror);
  java_lang_Class::set_class_data(m, class_data);
  if (class_data != nullptr) {
    bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, class_data);
    assert(success, "sanity");
  }

  if (log_is_enabled(Debug, aot, init)) {
    ResourceMark rm;
    log_debug(aot, init)("copied %3d field(s) in aot-initialized mirror %s%s%s", nfields, ik->external_name(),
                         ik->is_hidden() ? " (hidden)" : "",
                         ik->is_enum_subclass() ? " (enum)" : "");
  }
}

void HeapShared::copy_java_mirror(oop orig_mirror, oop scratch_m) {
  // We need to retain the identity_hash, because it may have been used by some hashtables
  // in the shared heap.
  if (!orig_mirror->fast_no_hash_check()) {
    intptr_t src_hash = orig_mirror->identity_hash();
    if (UseCompactObjectHeaders) {
      narrowKlass nk = CompressedKlassPointers::encode(orig_mirror->klass());
      scratch_m->set_mark(markWord::prototype().set_narrow_klass(nk).copy_set_hash(src_hash));
    } else {
      scratch_m->set_mark(markWord::prototype().copy_set_hash(src_hash));
    }
    assert(scratch_m->mark().is_unlocked(), "sanity");

    DEBUG_ONLY(intptr_t archived_hash = scratch_m->identity_hash());
    assert(src_hash == archived_hash, "Different hash codes: original " INTPTR_FORMAT ", archived " INTPTR_FORMAT, src_hash, archived_hash);
  }

  if (CDSConfig::is_dumping_aot_linked_classes()) {
    java_lang_Class::set_module(scratch_m, java_lang_Class::module(orig_mirror));
    java_lang_Class::set_protection_domain(scratch_m, java_lang_Class::protection_domain(orig_mirror));
  }
}

static objArrayOop get_archived_resolved_references(InstanceKlass* src_ik) {
  if (SystemDictionaryShared::is_builtin_loader(src_ik->class_loader_data())) {
    objArrayOop rr = src_ik->constants()->resolved_references_or_null();
    if (rr != nullptr && !HeapShared::is_too_large_to_archive(rr)) {
      return HeapShared::scratch_resolved_references(src_ik->constants());
    }
  }
  return nullptr;
}

int HeapShared::archive_exception_instance(oop exception) {
  bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, exception);
  assert(success, "sanity");
  return append_root(exception);
}

void HeapShared::get_pointer_info(oop src_obj, bool& has_oop_pointers, bool& has_native_pointers) {
  OopHandle oh(&src_obj);
  CachedOopInfo* info = archived_object_cache()->get(oh);
  assert(info != nullptr, "must be");
  has_oop_pointers = info->has_oop_pointers();
  has_native_pointers = info->has_native_pointers();
}

void HeapShared::set_has_native_pointers(oop src_obj) {
  OopHandle oh(&src_obj);
  CachedOopInfo* info = archived_object_cache()->get(oh);
  assert(info != nullptr, "must be");
  info->set_has_native_pointers();
}

// Between start_scanning_for_oops() and end_scanning_for_oops(), we discover all Java heap objects that
// should be stored in the AOT cache. The scanning is coordinated by AOTArtifactFinder.
void HeapShared::start_scanning_for_oops() {
  {
    NoSafepointVerifier nsv;

    // The special subgraph doesn't belong to any class. We use Object_klass() here just
    // for convenience.
    _dump_time_special_subgraph = init_subgraph_info(vmClasses::Object_klass(), false);

    // Cache for recording where the archived objects are copied to
    create_archived_object_cache();

    if (HeapShared::is_writing_mapping_mode() && (UseG1GC || UseCompressedOops)) {
      aot_log_info(aot)("Heap range = [" PTR_FORMAT " - "  PTR_FORMAT "]",
                    UseCompressedOops ? p2i(CompressedOops::begin()) :
                                        p2i((address)G1CollectedHeap::heap()->reserved().start()),
                    UseCompressedOops ? p2i(CompressedOops::end()) :
                                        p2i((address)G1CollectedHeap::heap()->reserved().end()));
    }

    archive_subgraphs();
  }

  init_seen_objects_table();
  Universe::archive_exception_instances();
}

void HeapShared::end_scanning_for_oops() {
  if (is_writing_mapping_mode()) {
    StringTable::init_shared_table();
  }
  delete_seen_objects_table();
}

void HeapShared::write_heap(ArchiveMappedHeapInfo* mapped_heap_info, ArchiveStreamedHeapInfo* streamed_heap_info) {
  {
    NoSafepointVerifier nsv;
    CDSHeapVerifier::verify();
    check_special_subgraph_classes();
  }

  if (HeapShared::is_writing_mapping_mode()) {
    StringTable::write_shared_table();
    AOTMappedHeapWriter::write(_pending_roots, mapped_heap_info);
  } else {
    assert(HeapShared::is_writing_streaming_mode(), "are there more modes?");
    AOTStreamedHeapWriter::write(_pending_roots, streamed_heap_info);
  }

  ArchiveBuilder::OtherROAllocMark mark;
  write_subgraph_info_table();
}

void HeapShared::scan_java_mirror(oop orig_mirror) {
  oop m = scratch_java_mirror(orig_mirror);
  if (m != nullptr) { // nullptr if for custom class loader
    copy_java_mirror(orig_mirror, m);
    bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, m);
    assert(success, "sanity");
  }
}

void HeapShared::scan_java_class(Klass* orig_k) {
  scan_java_mirror(orig_k->java_mirror());

  if (orig_k->is_instance_klass()) {
    InstanceKlass* orig_ik = InstanceKlass::cast(orig_k);
    orig_ik->constants()->prepare_resolved_references_for_archiving();
    objArrayOop rr = get_archived_resolved_references(orig_ik);
    if (rr != nullptr) {
      bool success = HeapShared::archive_reachable_objects_from(1, _dump_time_special_subgraph, rr);
      assert(success, "must be");
    }
  }
}

void HeapShared::archive_subgraphs() {
  assert(CDSConfig::is_dumping_heap(), "must be");

  if (CDSConfig::is_dumping_klass_subgraphs()) {
    archive_object_subgraphs(archive_subgraph_entry_fields,
                             false /* is_full_module_graph */);
    if (CDSConfig::is_dumping_full_module_graph()) {
      archive_object_subgraphs(fmg_archive_subgraph_entry_fields,
                               true /* is_full_module_graph */);
    }
  }
}

//
// Subgraph archiving support
//
HeapShared::DumpTimeKlassSubGraphInfoTable* HeapShared::_dump_time_subgraph_info_table = nullptr;
HeapShared::RunTimeKlassSubGraphInfoTable   HeapShared::_run_time_subgraph_info_table;

// Get the subgraph_info for Klass k. A new subgraph_info is created if
// there is no existing one for k. The subgraph_info records the "buffered"
// address of the class.
KlassSubGraphInfo* HeapShared::init_subgraph_info(Klass* k, bool is_full_module_graph) {
  assert(CDSConfig::is_dumping_heap(), "dump time only");
  bool created;
  KlassSubGraphInfo* info =
    _dump_time_subgraph_info_table->put_if_absent(k, KlassSubGraphInfo(k, is_full_module_graph),
                                                  &created);
  assert(created, "must not initialize twice");
  return info;
}

KlassSubGraphInfo* HeapShared::get_subgraph_info(Klass* k) {
  assert(CDSConfig::is_dumping_heap(), "dump time only");
  KlassSubGraphInfo* info = _dump_time_subgraph_info_table->get(k);
  assert(info != nullptr, "must have been initialized");
  return info;
}

// Add an entry field to the current KlassSubGraphInfo.
void KlassSubGraphInfo::add_subgraph_entry_field(int static_field_offset, oop v) {
  assert(CDSConfig::is_dumping_heap(), "dump time only");
  if (_subgraph_entry_fields == nullptr) {
    _subgraph_entry_fields =
      new (mtClass) GrowableArray<int>(10, mtClass);
  }
  _subgraph_entry_fields->append(static_field_offset);
  _subgraph_entry_fields->append(HeapShared::append_root(v));
}

// Add the Klass* for an object in the current KlassSubGraphInfo's subgraphs.
// Only objects of boot classes can be included in sub-graph.
void KlassSubGraphInfo::add_subgraph_object_klass(Klass* orig_k) {
  assert(CDSConfig::is_dumping_heap(), "dump time only");

  if (_subgraph_object_klasses == nullptr) {
    _subgraph_object_klasses =
      new (mtClass) GrowableArray<Klass*>(50, mtClass);
  }

  if (_k == orig_k) {
    // Don't add the Klass containing the sub-graph to it's own klass
    // initialization list.
    return;
  }

  if (orig_k->is_instance_klass()) {
#ifdef ASSERT
    InstanceKlass* ik = InstanceKlass::cast(orig_k);
    if (CDSConfig::is_dumping_method_handles()) {
      // -XX:AOTInitTestClass must be used carefully in regression tests to
      // include only classes that are safe to aot-initialize.
      assert(ik->class_loader() == nullptr ||
             HeapShared::is_lambda_proxy_klass(ik) ||
             AOTClassInitializer::has_test_class(),
            "we can archive only instances of boot classes or lambda proxy classes");
    } else {
      assert(ik->class_loader() == nullptr, "must be boot class");
    }
#endif
    // vmClasses::xxx_klass() are not updated, need to check
    // the original Klass*
    if (orig_k == vmClasses::String_klass() ||
        orig_k == vmClasses::Object_klass()) {
      // Initialized early during VM initialization. No need to be added
      // to the sub-graph object class list.
      return;
    }
    check_allowed_klass(InstanceKlass::cast(orig_k));
  } else if (orig_k->is_objArray_klass()) {
    Klass* abk = ObjArrayKlass::cast(orig_k)->bottom_klass();
    if (abk->is_instance_klass()) {
      assert(InstanceKlass::cast(abk)->defined_by_boot_loader(),
            "must be boot class");
      check_allowed_klass(InstanceKlass::cast(ObjArrayKlass::cast(orig_k)->bottom_klass()));
    }
    if (orig_k == Universe::objectArrayKlass()) {
      // Initialized early during Universe::genesis. No need to be added
      // to the list.
      return;
    }
  } else {
    assert(orig_k->is_typeArray_klass(), "must be");
    // Primitive type arrays are created early during Universe::genesis.
    return;
  }

  if (log_is_enabled(Debug, aot, heap)) {
    if (!_subgraph_object_klasses->contains(orig_k)) {
      ResourceMark rm;
      log_debug(aot, heap)("Adding klass %s", orig_k->external_name());
    }
  }

  _subgraph_object_klasses->append_if_missing(orig_k);
  _has_non_early_klasses |= is_non_early_klass(orig_k);
}

void KlassSubGraphInfo::check_allowed_klass(InstanceKlass* ik) {
#ifndef PRODUCT
  if (AOTClassInitializer::has_test_class()) {
    // The tests can cache arbitrary types of objects.
    return;
  }
#endif

  if (ik->module()->name() == vmSymbols::java_base()) {
    assert(ik->package() != nullptr, "classes in java.base cannot be in unnamed package");
    return;
  }

  const char* lambda_msg = "";
  if (CDSConfig::is_dumping_method_handles()) {
    lambda_msg = ", or a lambda proxy class";
    if (HeapShared::is_lambda_proxy_klass(ik) &&
        (ik->class_loader() == nullptr ||
         ik->class_loader() == SystemDictionary::java_platform_loader() ||
         ik->class_loader() == SystemDictionary::java_system_loader())) {
      return;
    }
  }

#ifndef PRODUCT
  if (!ik->module()->is_named() && ik->package() == nullptr && ArchiveHeapTestClass != nullptr) {
    // This class is loaded by ArchiveHeapTestClass
    return;
  }
  const char* testcls_msg = ", or a test class in an unnamed package of an unnamed module";
#else
  const char* testcls_msg = "";
#endif

  ResourceMark rm;
  log_error(aot, heap)("Class %s not allowed in archive heap. Must be in java.base%s%s",
                       ik->external_name(), lambda_msg, testcls_msg);
  AOTMetaspace::unrecoverable_writing_error();
}

bool KlassSubGraphInfo::is_non_early_klass(Klass* k) {
  if (k->is_objArray_klass()) {
    k = ObjArrayKlass::cast(k)->bottom_klass();
  }
  if (k->is_instance_klass()) {
    if (!SystemDictionaryShared::is_early_klass(InstanceKlass::cast(k))) {
      ResourceMark rm;
      log_info(aot, heap)("non-early: %s", k->external_name());
      return true;
    } else {
      return false;
    }
  } else {
    return false;
  }
}

// Initialize an archived subgraph_info_record from the given KlassSubGraphInfo.
void ArchivedKlassSubGraphInfoRecord::init(KlassSubGraphInfo* info) {
  _k = ArchiveBuilder::get_buffered_klass(info->klass());
  _entry_field_records = nullptr;
  _subgraph_object_klasses = nullptr;
  _is_full_module_graph = info->is_full_module_graph();

  if (_is_full_module_graph) {
    // Consider all classes referenced by the full module graph as early -- we will be
    // allocating objects of these classes during JVMTI early phase, so they cannot
    // be processed by (non-early) JVMTI ClassFileLoadHook
    _has_non_early_klasses = false;
  } else {
    _has_non_early_klasses = info->has_non_early_klasses();
  }

  if (_has_non_early_klasses) {
    ResourceMark rm;
    log_info(aot, heap)(
          "Subgraph of klass %s has non-early klasses and cannot be used when JVMTI ClassFileLoadHook is enabled",
          _k->external_name());
  }

  // populate the entry fields
  GrowableArray<int>* entry_fields = info->subgraph_entry_fields();
  if (entry_fields != nullptr) {
    int num_entry_fields = entry_fields->length();
    assert(num_entry_fields % 2 == 0, "sanity");
    _entry_field_records =
      ArchiveBuilder::new_ro_array<int>(num_entry_fields);
    for (int i = 0 ; i < num_entry_fields; i++) {
      _entry_field_records->at_put(i, entry_fields->at(i));
    }
  }

  // <recorded_klasses> has the Klasses of all the objects that are referenced by this subgraph.
  // Copy those that need to be explicitly initialized into <_subgraph_object_klasses>.
  GrowableArray<Klass*>* recorded_klasses = info->subgraph_object_klasses();
  if (recorded_klasses != nullptr) {
    // AOT-inited classes are automatically marked as "initialized" during bootstrap. When
    // programmatically loading a subgraph, we only need to explicitly initialize the classes
    // that are not aot-inited.
    int num_to_copy = 0;
    for (int i = 0; i < recorded_klasses->length(); i++) {
      Klass* subgraph_k = ArchiveBuilder::get_buffered_klass(recorded_klasses->at(i));
      if (!subgraph_k->has_aot_initialized_mirror()) {
        num_to_copy ++;
      }
    }

    _subgraph_object_klasses = ArchiveBuilder::new_ro_array<Klass*>(num_to_copy);
    bool is_special = (_k == ArchiveBuilder::get_buffered_klass(vmClasses::Object_klass()));
    for (int i = 0, n = 0; i < recorded_klasses->length(); i++) {
      Klass* subgraph_k = ArchiveBuilder::get_buffered_klass(recorded_klasses->at(i));
      if (subgraph_k->has_aot_initialized_mirror()) {
        continue;
      }
      if (log_is_enabled(Info, aot, heap)) {
        ResourceMark rm;
        const char* owner_name =  is_special ? "<special>" : _k->external_name();
        if (subgraph_k->is_instance_klass()) {
          InstanceKlass* src_ik = InstanceKlass::cast(ArchiveBuilder::current()->get_source_addr(subgraph_k));
        }
        log_info(aot, heap)(
          "Archived object klass %s (%2d) => %s",
          owner_name, n, subgraph_k->external_name());
      }
      _subgraph_object_klasses->at_put(n, subgraph_k);
      ArchivePtrMarker::mark_pointer(_subgraph_object_klasses->adr_at(n));
      n++;
    }
  }

  ArchivePtrMarker::mark_pointer(&_k);
  ArchivePtrMarker::mark_pointer(&_entry_field_records);
  ArchivePtrMarker::mark_pointer(&_subgraph_object_klasses);
}

class HeapShared::CopyKlassSubGraphInfoToArchive : StackObj {
  CompactHashtableWriter* _writer;
public:
  CopyKlassSubGraphInfoToArchive(CompactHashtableWriter* writer) : _writer(writer) {}

  bool do_entry(Klass* klass, KlassSubGraphInfo& info) {
    if (info.subgraph_object_klasses() != nullptr || info.subgraph_entry_fields() != nullptr) {
      ArchivedKlassSubGraphInfoRecord* record = HeapShared::archive_subgraph_info(&info);
      Klass* buffered_k = ArchiveBuilder::get_buffered_klass(klass);
      unsigned int hash = SystemDictionaryShared::hash_for_shared_dictionary((address)buffered_k);
      u4 delta = ArchiveBuilder::current()->any_to_offset_u4(record);
      _writer->add(hash, delta);
    }
    return true; // keep on iterating
  }
};

ArchivedKlassSubGraphInfoRecord* HeapShared::archive_subgraph_info(KlassSubGraphInfo* info) {
  ArchivedKlassSubGraphInfoRecord* record =
      (ArchivedKlassSubGraphInfoRecord*)ArchiveBuilder::ro_region_alloc(sizeof(ArchivedKlassSubGraphInfoRecord));
  record->init(info);
  if (info ==  _dump_time_special_subgraph) {
    _run_time_special_subgraph = record;
  }
  return record;
}

// Build the records of archived subgraph infos, which include:
// - Entry points to all subgraphs from the containing class mirror. The entry
//   points are static fields in the mirror. For each entry point, the field
//   offset, and value are recorded in the sub-graph
//   info. The value is stored back to the corresponding field at runtime.
// - A list of klasses that need to be loaded/initialized before archived
//   java object sub-graph can be accessed at runtime.
void HeapShared::write_subgraph_info_table() {
  // Allocate the contents of the hashtable(s) inside the RO region of the CDS archive.
  DumpTimeKlassSubGraphInfoTable* d_table = _dump_time_subgraph_info_table;
  CompactHashtableStats stats;

  _run_time_subgraph_info_table.reset();

  CompactHashtableWriter writer(d_table->number_of_entries(), &stats);
  CopyKlassSubGraphInfoToArchive copy(&writer);
  d_table->iterate(&copy);
  writer.dump(&_run_time_subgraph_info_table, "subgraphs");

#ifndef PRODUCT
  if (ArchiveHeapTestClass != nullptr) {
    size_t len = strlen(ArchiveHeapTestClass) + 1;
    Array<char>* array = ArchiveBuilder::new_ro_array<char>((int)len);
    strncpy(array->adr_at(0), ArchiveHeapTestClass, len);
    _archived_ArchiveHeapTestClass = array;
  }
#endif
  if (log_is_enabled(Info, aot, heap)) {
    print_stats();
  }
}

void HeapShared::serialize_tables(SerializeClosure* soc) {

#ifndef PRODUCT
  soc->do_ptr(&_archived_ArchiveHeapTestClass);
  if (soc->reading() && _archived_ArchiveHeapTestClass != nullptr) {
    _test_class_name = _archived_ArchiveHeapTestClass->adr_at(0);
    setup_test_class(_test_class_name);
  }
#endif

  _run_time_subgraph_info_table.serialize_header(soc);
  soc->do_ptr(&_run_time_special_subgraph);
}

static void verify_the_heap(Klass* k, const char* which) {
  if (VerifyArchivedFields > 0) {
    ResourceMark rm;
    log_info(aot, heap)("Verify heap %s initializing static field(s) in %s",
                        which, k->external_name());

    if (VerifyArchivedFields == 1) {
      VM_Verify verify_op;
      VMThread::execute(&verify_op);
    } else if (VerifyArchivedFields == 2 && is_init_completed()) {
      // At this time, the oop->klass() of some archived objects in the heap may not
      // have been loaded into the system dictionary yet. Nevertheless, oop->klass() should
      // have enough information (object size, oop maps, etc) so that a GC can be safely
      // performed.
      //
      // -XX:VerifyArchivedFields=2 force a GC to happen in such an early stage
      // to check for GC safety.
      log_info(aot, heap)("Trigger GC %s initializing static field(s) in %s",
                          which, k->external_name());
      FlagSetting fs1(VerifyBeforeGC, true);
      FlagSetting fs2(VerifyDuringGC, true);
      FlagSetting fs3(VerifyAfterGC,  true);
      Universe::heap()->collect(GCCause::_java_lang_system_gc);
    }
  }
}

// Before GC can execute, we must ensure that all oops reachable from HeapShared::roots()
// have a valid klass. I.e., oopDesc::klass() must have already been resolved.
//
// Note: if a ArchivedKlassSubGraphInfoRecord contains non-early classes, and JVMTI
// ClassFileLoadHook is enabled, it's possible for this class to be dynamically replaced. In
// this case, we will not load the ArchivedKlassSubGraphInfoRecord and will clear its roots.
void HeapShared::resolve_classes(JavaThread* current) {
  assert(CDSConfig::is_using_archive(), "runtime only!");
  if (CDSConfig::is_using_klass_subgraphs()) {
    resolve_classes_for_subgraphs(current, archive_subgraph_entry_fields);
    resolve_classes_for_subgraphs(current, fmg_archive_subgraph_entry_fields);
  }
}

void HeapShared::resolve_classes_for_subgraphs(JavaThread* current, ArchivableStaticFieldInfo fields[]) {
  for (int i = 0; fields[i].valid(); i++) {
    ArchivableStaticFieldInfo* info = &fields[i];
    TempNewSymbol klass_name = SymbolTable::new_symbol(info->klass_name);
    InstanceKlass* k = SystemDictionaryShared::find_builtin_class(klass_name);
    assert(k != nullptr && k->defined_by_boot_loader(), "sanity");
    resolve_classes_for_subgraph_of(current, k);
  }
}

void HeapShared::resolve_classes_for_subgraph_of(JavaThread* current, Klass* k) {
  JavaThread* THREAD = current;
  ExceptionMark em(THREAD);
  const ArchivedKlassSubGraphInfoRecord* record =
   resolve_or_init_classes_for_subgraph_of(k, /*do_init=*/false, THREAD);
  if (HAS_PENDING_EXCEPTION) {
   CLEAR_PENDING_EXCEPTION;
  }
  if (record == nullptr) {
   clear_archived_roots_of(k);
  }
}

void HeapShared::initialize_java_lang_invoke(TRAPS) {
  if (CDSConfig::is_using_aot_linked_classes() || CDSConfig::is_dumping_method_handles()) {
    resolve_or_init("java/lang/invoke/Invokers$Holder", true, CHECK);
    resolve_or_init("java/lang/invoke/MethodHandle", true, CHECK);
    resolve_or_init("java/lang/invoke/MethodHandleNatives", true, CHECK);
    resolve_or_init("java/lang/invoke/DirectMethodHandle$Holder", true, CHECK);
    resolve_or_init("java/lang/invoke/DelegatingMethodHandle$Holder", true, CHECK);
    resolve_or_init("java/lang/invoke/LambdaForm$Holder", true, CHECK);
    resolve_or_init("java/lang/invoke/BoundMethodHandle$Species_L", true, CHECK);
  }
}

// Initialize the InstanceKlasses of objects that are reachable from the following roots:
//   - interned strings
//   - Klass::java_mirror() -- including aot-initialized mirrors such as those of Enum klasses.
//   - ConstantPool::resolved_references()
//   - Universe::<xxx>_exception_instance()
//
// For example, if this enum class is initialized at AOT cache assembly time:
//
//    enum Fruit {
//       APPLE, ORANGE, BANANA;
//       static final Set<Fruit> HAVE_SEEDS = new HashSet<>(Arrays.asList(APPLE, ORANGE));
//   }
//
// the aot-initialized mirror of Fruit has a static field that references HashSet, which
// should be initialized before any Java code can access the Fruit class. Note that
// HashSet itself doesn't necessary need to be an aot-initialized class.
void HeapShared::init_classes_for_special_subgraph(Handle class_loader, TRAPS) {
  if (!is_archived_heap_in_use()) {
    return;
  }

  assert( _run_time_special_subgraph != nullptr, "must be");
  Array<Klass*>* klasses = _run_time_special_subgraph->subgraph_object_klasses();
  if (klasses != nullptr) {
    for (int pass = 0; pass < 2; pass ++) {
      for (int i = 0; i < klasses->length(); i++) {
        Klass* k = klasses->at(i);
        if (k->class_loader_data() == nullptr) {
          // This class is not yet loaded. We will initialize it in a later phase.
          // For example, we have loaded only AOTLinkedClassCategory::BOOT1 classes
          // but k is part of AOTLinkedClassCategory::BOOT2.
          continue;
        }
        if (k->class_loader() == class_loader()) {
          if (pass == 0) {
            if (k->is_instance_klass()) {
              InstanceKlass::cast(k)->link_class(CHECK);
            }
          } else {
            resolve_or_init(k, /*do_init*/true, CHECK);
          }
        }
      }
    }
  }
}

void HeapShared::initialize_from_archived_subgraph(JavaThread* current, Klass* k) {
  JavaThread* THREAD = current;
  if (!CDSConfig::is_using_klass_subgraphs()) {
    return; // nothing to do
  }

  if (k->name()->equals("jdk/internal/module/ArchivedModuleGraph") &&
      !CDSConfig::is_using_optimized_module_handling() &&
      // archive was created with --module-path
      AOTClassLocationConfig::runtime()->num_module_paths() > 0) {
    // ArchivedModuleGraph was created with a --module-path that's different than the runtime --module-path.
    // Thus, it might contain references to modules that do not exist at runtime. We cannot use it.
    log_info(aot, heap)("Skip initializing ArchivedModuleGraph subgraph: is_using_optimized_module_handling=%s num_module_paths=%d",
                        BOOL_TO_STR(CDSConfig::is_using_optimized_module_handling()),
                        AOTClassLocationConfig::runtime()->num_module_paths());
    return;
  }

  ExceptionMark em(THREAD);
  const ArchivedKlassSubGraphInfoRecord* record =
    resolve_or_init_classes_for_subgraph_of(k, /*do_init=*/true, THREAD);

  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    // None of the field value will be set if there was an exception when initializing the classes.
    // The java code will not see any of the archived objects in the
    // subgraphs referenced from k in this case.
    return;
  }

  if (record != nullptr) {
    init_archived_fields_for(k, record);
  }
}

const ArchivedKlassSubGraphInfoRecord*
HeapShared::resolve_or_init_classes_for_subgraph_of(Klass* k, bool do_init, TRAPS) {
  assert(!CDSConfig::is_dumping_heap(), "Should not be called when dumping heap");

  if (!k->in_aot_cache()) {
    return nullptr;
  }
  unsigned int hash = SystemDictionaryShared::hash_for_shared_dictionary_quick(k);
  const ArchivedKlassSubGraphInfoRecord* record = _run_time_subgraph_info_table.lookup(k, hash, 0);

#ifndef PRODUCT
  if (_test_class_name != nullptr && k->name()->equals(_test_class_name) && record != nullptr) {
    _test_class = k;
    _test_class_record = record;
  }
#endif

  // Initialize from archived data. Currently this is done only
  // during VM initialization time. No lock is needed.
  if (record == nullptr) {
    if (log_is_enabled(Info, aot, heap)) {
      ResourceMark rm(THREAD);
      log_info(aot, heap)("subgraph %s is not recorded",
                          k->external_name());
    }
    return nullptr;
  } else {
    if (record->is_full_module_graph() && !CDSConfig::is_using_full_module_graph()) {
      if (log_is_enabled(Info, aot, heap)) {
        ResourceMark rm(THREAD);
        log_info(aot, heap)("subgraph %s cannot be used because full module graph is disabled",
                            k->external_name());
      }
      return nullptr;
    }

    if (record->has_non_early_klasses() && JvmtiExport::should_post_class_file_load_hook()) {
      if (log_is_enabled(Info, aot, heap)) {
        ResourceMark rm(THREAD);
        log_info(aot, heap)("subgraph %s cannot be used because JVMTI ClassFileLoadHook is enabled",
                            k->external_name());
      }
      return nullptr;
    }

    if (log_is_enabled(Info, aot, heap)) {
      ResourceMark rm;
      log_info(aot, heap)("%s subgraph %s ", do_init ? "init" : "resolve", k->external_name());
    }

    resolve_or_init(k, do_init, CHECK_NULL);

    // Load/link/initialize the klasses of the objects in the subgraph.
    // nullptr class loader is used.
    Array<Klass*>* klasses = record->subgraph_object_klasses();
    if (klasses != nullptr) {
      for (int i = 0; i < klasses->length(); i++) {
        Klass* klass = klasses->at(i);
        if (!klass->in_aot_cache()) {
          return nullptr;
        }
        resolve_or_init(klass, do_init, CHECK_NULL);
      }
    }
  }

  return record;
}

void HeapShared::resolve_or_init(const char* klass_name, bool do_init, TRAPS) {
  TempNewSymbol klass_name_sym =  SymbolTable::new_symbol(klass_name);
  InstanceKlass* k = SystemDictionaryShared::find_builtin_class(klass_name_sym);
  if (k == nullptr) {
    return;
  }
  assert(k->defined_by_boot_loader(), "sanity");
  resolve_or_init(k, false, CHECK);
  if (do_init) {
    resolve_or_init(k, true, CHECK);
  }
}

void HeapShared::resolve_or_init(Klass* k, bool do_init, TRAPS) {
  if (!do_init) {
    if (k->class_loader_data() == nullptr) {
      Klass* resolved_k = SystemDictionary::resolve_or_null(k->name(), CHECK);
      assert(resolved_k == k, "classes used by archived heap must not be replaced by JVMTI ClassFileLoadHook");
    }
  } else {
    assert(k->class_loader_data() != nullptr, "must have been resolved by HeapShared::resolve_classes");
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      ik->initialize(CHECK);
    } else if (k->is_objArray_klass()) {
      ObjArrayKlass* oak = ObjArrayKlass::cast(k);
      oak->initialize(CHECK);
    }
  }
}

void HeapShared::init_archived_fields_for(Klass* k, const ArchivedKlassSubGraphInfoRecord* record) {
  verify_the_heap(k, "before");

  Array<int>* entry_field_records = record->entry_field_records();
  if (entry_field_records != nullptr) {
    int efr_len = entry_field_records->length();
    assert(efr_len % 2 == 0, "sanity");
    for (int i = 0; i < efr_len; i += 2) {
      int field_offset = entry_field_records->at(i);
      int root_index = entry_field_records->at(i+1);
      // Load the subgraph entry fields from the record and store them back to
      // the corresponding fields within the mirror.
      oop v = get_root(root_index, /*clear=*/true);
      oop m = k->java_mirror();
      if (k->has_aot_initialized_mirror()) {
        assert(v == m->obj_field(field_offset), "must be aot-initialized");
      } else {
        m->obj_field_put(field_offset, v);
      }
      log_debug(aot, heap)("  " PTR_FORMAT " init field @ %2d = " PTR_FORMAT, p2i(k), field_offset, p2i(v));
    }

    // Done. Java code can see the archived sub-graphs referenced from k's
    // mirror after this point.
    if (log_is_enabled(Info, aot, heap)) {
      ResourceMark rm;
      log_info(aot, heap)("initialize_from_archived_subgraph %s " PTR_FORMAT "%s%s",
                          k->external_name(), p2i(k), JvmtiExport::is_early_phase() ? " (early)" : "",
                          k->has_aot_initialized_mirror() ? " (aot-inited)" : "");
    }
  }

  verify_the_heap(k, "after ");
}

void HeapShared::clear_archived_roots_of(Klass* k) {
  unsigned int hash = SystemDictionaryShared::hash_for_shared_dictionary_quick(k);
  const ArchivedKlassSubGraphInfoRecord* record = _run_time_subgraph_info_table.lookup(k, hash, 0);
  if (record != nullptr) {
    Array<int>* entry_field_records = record->entry_field_records();
    if (entry_field_records != nullptr) {
      int efr_len = entry_field_records->length();
      assert(efr_len % 2 == 0, "sanity");
      for (int i = 0; i < efr_len; i += 2) {
        int root_index = entry_field_records->at(i+1);
        clear_root(root_index);
      }
    }
  }
}

// Push all oop fields (or oop array elemenets in case of an objArray) in
// _referencing_obj onto the _stack.
class HeapShared::OopFieldPusher: public BasicOopIterateClosure {
  PendingOopStack* _stack;
  GrowableArray<oop> _found_oop_fields;
  int _level;
  bool _record_klasses_only;
  KlassSubGraphInfo* _subgraph_info;
  oop _referencing_obj;
  bool _is_java_lang_ref;
 public:
  OopFieldPusher(PendingOopStack* stack,
                 int level,
                 bool record_klasses_only,
                 KlassSubGraphInfo* subgraph_info,
                 oop orig) :
    _stack(stack),
    _found_oop_fields(),
    _level(level),
    _record_klasses_only(record_klasses_only),
    _subgraph_info(subgraph_info),
    _referencing_obj(orig) {
    _is_java_lang_ref = AOTReferenceObjSupport::check_if_ref_obj(orig);
  }
  void do_oop(narrowOop *p) { OopFieldPusher::do_oop_work(p); }
  void do_oop(      oop *p) { OopFieldPusher::do_oop_work(p); }

  ~OopFieldPusher() {
    while (_found_oop_fields.length() > 0) {
      // This produces the exact same traversal order as the previous version
      // of OopFieldPusher that recurses on the C stack -- a depth-first search,
      // walking the oop fields in _referencing_obj by ascending field offsets.
      oop obj = _found_oop_fields.pop();
      _stack->push(PendingOop(obj, _referencing_obj, _level + 1));
    }
  }

 protected:
  template <class T> void do_oop_work(T *p) {
    int field_offset = pointer_delta_as_int((char*)p, cast_from_oop<char*>(_referencing_obj));
    oop obj = HeapAccess<ON_UNKNOWN_OOP_REF>::oop_load_at(_referencing_obj, field_offset);
    if (obj != nullptr) {
      if (_is_java_lang_ref && AOTReferenceObjSupport::skip_field(field_offset)) {
        // Do not follow these fields. They will be cleared to null.
        return;
      }

      if (!_record_klasses_only && log_is_enabled(Debug, aot, heap)) {
        ResourceMark rm;
        log_debug(aot, heap)("(%d) %s[%d] ==> " PTR_FORMAT " size %zu %s", _level,
                             _referencing_obj->klass()->external_name(), field_offset,
                             p2i(obj), obj->size() * HeapWordSize, obj->klass()->external_name());
        if (log_is_enabled(Trace, aot, heap)) {
          LogTarget(Trace, aot, heap) log;
          LogStream out(log);
          obj->print_on(&out);
        }
      }

      _found_oop_fields.push(obj);
    }
  }

 public:
  oop referencing_obj()                       { return _referencing_obj;      }
  KlassSubGraphInfo* subgraph_info()          { return _subgraph_info;        }
};

// Checks if an oop has any non-null oop fields
class PointsToOopsChecker : public BasicOopIterateClosure {
  bool _result;

  template <class T> void check(T *p) {
    _result |= (HeapAccess<>::oop_load(p) != nullptr);
  }

public:
  PointsToOopsChecker() : _result(false) {}
  void do_oop(narrowOop *p) { check(p); }
  void do_oop(      oop *p) { check(p); }
  bool result() { return _result; }
};

HeapShared::CachedOopInfo HeapShared::make_cached_oop_info(oop obj, oop referrer) {
  PointsToOopsChecker points_to_oops_checker;
  obj->oop_iterate(&points_to_oops_checker);
  return CachedOopInfo(OopHandle(Universe::vm_global(), referrer), points_to_oops_checker.result());
}

void HeapShared::init_box_classes(TRAPS) {
  if (is_archived_heap_in_use()) {
    vmClasses::Boolean_klass()->initialize(CHECK);
    vmClasses::Character_klass()->initialize(CHECK);
    vmClasses::Float_klass()->initialize(CHECK);
    vmClasses::Double_klass()->initialize(CHECK);
    vmClasses::Byte_klass()->initialize(CHECK);
    vmClasses::Short_klass()->initialize(CHECK);
    vmClasses::Integer_klass()->initialize(CHECK);
    vmClasses::Long_klass()->initialize(CHECK);
    vmClasses::Void_klass()->initialize(CHECK);
  }
}

// (1) If orig_obj has not been archived yet, archive it.
// (2) If orig_obj has not been seen yet (since start_recording_subgraph() was called),
//     trace all  objects that are reachable from it, and make sure these objects are archived.
// (3) Record the klasses of all objects that are reachable from orig_obj (including those that
//     were already archived when this function is called)
bool HeapShared::archive_reachable_objects_from(int level,
                                                KlassSubGraphInfo* subgraph_info,
                                                oop orig_obj) {
  assert(orig_obj != nullptr, "must be");
  PendingOopStack stack;
  stack.push(PendingOop(orig_obj, nullptr, level));

  while (stack.length() > 0) {
    PendingOop po = stack.pop();
    _object_being_archived = po;
    bool status = walk_one_object(&stack, po.level(), subgraph_info, po.obj(), po.referrer());
    _object_being_archived = PendingOop();

    if (!status) {
      // Don't archive a subgraph root that's too big. For archives static fields, that's OK
      // as the Java code will take care of initializing this field dynamically.
      assert(level == 1, "VM should have exited with unarchivable objects for _level > 1");
      return false;
    }
  }

  return true;
}

bool HeapShared::walk_one_object(PendingOopStack* stack, int level, KlassSubGraphInfo* subgraph_info,
                                 oop orig_obj, oop referrer) {
  assert(orig_obj != nullptr, "must be");
  if (!JavaClasses::is_supported_for_archiving(orig_obj)) {
    // This object has injected fields that cannot be supported easily, so we disallow them for now.
    // If you get an error here, you probably made a change in the JDK library that has added
    // these objects that are referenced (directly or indirectly) by static fields.
    ResourceMark rm;
    log_error(aot, heap)("Cannot archive object " PTR_FORMAT " of class %s", p2i(orig_obj), orig_obj->klass()->external_name());
    debug_trace();
    AOTMetaspace::unrecoverable_writing_error();
  }

  if (log_is_enabled(Debug, aot, heap) && java_lang_Class::is_instance(orig_obj)) {
    ResourceMark rm;
    LogTarget(Debug, aot, heap) log;
    LogStream out(log);
    out.print("Found java mirror " PTR_FORMAT " ", p2i(orig_obj));
    Klass* k = java_lang_Class::as_Klass(orig_obj);
    if (k != nullptr) {
      out.print("%s", k->external_name());
    } else {
      out.print("primitive");
    }
    out.print_cr("; scratch mirror = "  PTR_FORMAT,
                 p2i(scratch_java_mirror(orig_obj)));
  }

  if (java_lang_Class::is_instance(orig_obj)) {
    Klass* k = java_lang_Class::as_Klass(orig_obj);
    if (RegeneratedClasses::has_been_regenerated(k)) {
      orig_obj = RegeneratedClasses::get_regenerated_object(k)->java_mirror();
    }
  }

  if (CDSConfig::is_dumping_aot_linked_classes()) {
    if (java_lang_Class::is_instance(orig_obj)) {
      orig_obj = scratch_java_mirror(orig_obj);
      assert(orig_obj != nullptr, "must be archived");
    }
  } else if (java_lang_Class::is_instance(orig_obj) && subgraph_info != _dump_time_special_subgraph) {
    // Without CDSConfig::is_dumping_aot_linked_classes(), we only allow archived objects to
    // point to the mirrors of (1) j.l.Object, (2) primitive classes, and (3) box classes. These are initialized
    // very early by HeapShared::init_box_classes().
    if (orig_obj == vmClasses::Object_klass()->java_mirror()
        || java_lang_Class::is_primitive(orig_obj)
        || orig_obj == vmClasses::Boolean_klass()->java_mirror()
        || orig_obj == vmClasses::Character_klass()->java_mirror()
        || orig_obj == vmClasses::Float_klass()->java_mirror()
        || orig_obj == vmClasses::Double_klass()->java_mirror()
        || orig_obj == vmClasses::Byte_klass()->java_mirror()
        || orig_obj == vmClasses::Short_klass()->java_mirror()
        || orig_obj == vmClasses::Integer_klass()->java_mirror()
        || orig_obj == vmClasses::Long_klass()->java_mirror()
        || orig_obj == vmClasses::Void_klass()->java_mirror()) {
      orig_obj = scratch_java_mirror(orig_obj);
      assert(orig_obj != nullptr, "must be archived");
    } else {
      // If you get an error here, you probably made a change in the JDK library that has added a Class
      // object that is referenced (directly or indirectly) by an ArchivableStaticFieldInfo
      // defined at the top of this file.
      log_error(aot, heap)("(%d) Unknown java.lang.Class object is in the archived sub-graph", level);
      debug_trace();
      AOTMetaspace::unrecoverable_writing_error();
    }
  }

  if (has_been_seen_during_subgraph_recording(orig_obj)) {
    // orig_obj has already been archived and traced. Nothing more to do.
    return true;
  } else {
    set_has_been_seen_during_subgraph_recording(orig_obj);
  }

  bool already_archived = has_been_archived(orig_obj);
  bool record_klasses_only = already_archived;
  if (!already_archived) {
    ++_num_new_archived_objs;
    if (!archive_object(orig_obj, referrer, subgraph_info)) {
      // Skip archiving the sub-graph referenced from the current entry field.
      ResourceMark rm;
      log_error(aot, heap)(
        "Cannot archive the sub-graph referenced from %s object ("
        PTR_FORMAT ") size %zu, skipped.",
        orig_obj->klass()->external_name(), p2i(orig_obj), orig_obj->size() * HeapWordSize);
      if (level == 1) {
        // Don't archive a subgraph root that's too big. For archives static fields, that's OK
        // as the Java code will take care of initializing this field dynamically.
        return false;
      } else {
        // We don't know how to handle an object that has been archived, but some of its reachable
        // objects cannot be archived. Bail out for now. We might need to fix this in the future if
        // we have a real use case.
        AOTMetaspace::unrecoverable_writing_error();
      }
    }
  }

  Klass *orig_k = orig_obj->klass();
  subgraph_info->add_subgraph_object_klass(orig_k);

  {
    // Find all the oops that are referenced by orig_obj, push them onto the stack
    // so we can work on them next.
    ResourceMark rm;
    OopFieldPusher pusher(stack, level, record_klasses_only, subgraph_info, orig_obj);
    orig_obj->oop_iterate(&pusher);
  }

  if (CDSConfig::is_dumping_aot_linked_classes()) {
    // The enum klasses are archived with aot-initialized mirror.
    // See AOTClassInitializer::can_archive_initialized_mirror().
  } else {
    // This is legacy support for enum classes before JEP 483 -- we cannot rerun
    // the enum's <clinit> in the production run, so special handling is needed.
    if (CDSEnumKlass::is_enum_obj(orig_obj)) {
      CDSEnumKlass::handle_enum_obj(level + 1, subgraph_info, orig_obj);
    }
  }

  return true;
}

//
// Start from the given static field in a java mirror and archive the
// complete sub-graph of java heap objects that are reached directly
// or indirectly from the starting object by following references.
// Sub-graph archiving restrictions (current):
//
// - All classes of objects in the archived sub-graph (including the
//   entry class) must be boot class only.
// - No java.lang.Class instance (java mirror) can be included inside
//   an archived sub-graph. Mirror can only be the sub-graph entry object.
//
// The Java heap object sub-graph archiving process (see OopFieldPusher):
//
// 1) Java object sub-graph archiving starts from a given static field
// within a Class instance (java mirror). If the static field is a
// reference field and points to a non-null java object, proceed to
// the next step.
//
// 2) Archives the referenced java object. If an archived copy of the
// current object already exists, updates the pointer in the archived
// copy of the referencing object to point to the current archived object.
// Otherwise, proceed to the next step.
//
// 3) Follows all references within the current java object and recursively
// archive the sub-graph of objects starting from each reference.
//
// 4) Updates the pointer in the archived copy of referencing object to
// point to the current archived object.
//
// 5) The Klass of the current java object is added to the list of Klasses
// for loading and initializing before any object in the archived graph can
// be accessed at runtime.
//
void HeapShared::archive_reachable_objects_from_static_field(InstanceKlass *k,
                                                             const char* klass_name,
                                                             int field_offset,
                                                             const char* field_name) {
  precond(CDSConfig::is_dumping_klass_subgraphs());
  assert(k->defined_by_boot_loader(), "must be boot class");

  oop m = k->java_mirror();

  KlassSubGraphInfo* subgraph_info = get_subgraph_info(k);
  oop f = m->obj_field(field_offset);

  log_debug(aot, heap)("Start archiving from: %s::%s (" PTR_FORMAT ")", klass_name, field_name, p2i(f));

  if (!CompressedOops::is_null(f)) {
    if (log_is_enabled(Trace, aot, heap)) {
      LogTarget(Trace, aot, heap) log;
      LogStream out(log);
      f->print_on(&out);
    }

    bool success = archive_reachable_objects_from(1, subgraph_info, f);
    if (!success) {
      log_error(aot, heap)("Archiving failed %s::%s (some reachable objects cannot be archived)",
                           klass_name, field_name);
    } else {
      // Note: the field value is not preserved in the archived mirror.
      // Record the field as a new subGraph entry point. The recorded
      // information is restored from the archive at runtime.
      subgraph_info->add_subgraph_entry_field(field_offset, f);
      log_info(aot, heap)("Archived field %s::%s => " PTR_FORMAT, klass_name, field_name, p2i(f));
    }
  } else {
    // The field contains null, we still need to record the entry point,
    // so it can be restored at runtime.
    subgraph_info->add_subgraph_entry_field(field_offset, nullptr);
  }
}

#ifndef PRODUCT
class VerifySharedOopClosure: public BasicOopIterateClosure {
 public:
  void do_oop(narrowOop *p) { VerifySharedOopClosure::do_oop_work(p); }
  void do_oop(      oop *p) { VerifySharedOopClosure::do_oop_work(p); }

 protected:
  template <class T> void do_oop_work(T *p) {
    oop obj = HeapAccess<>::oop_load(p);
    if (obj != nullptr) {
      HeapShared::verify_reachable_objects_from(obj);
    }
  }
};

void HeapShared::verify_subgraph_from_static_field(InstanceKlass* k, int field_offset) {
  precond(CDSConfig::is_dumping_klass_subgraphs());
  assert(k->defined_by_boot_loader(), "must be boot class");

  oop m = k->java_mirror();
  oop f = m->obj_field(field_offset);
  if (!CompressedOops::is_null(f)) {
    verify_subgraph_from(f);
  }
}

void HeapShared::verify_subgraph_from(oop orig_obj) {
  if (!has_been_archived(orig_obj)) {
    // It's OK for the root of a subgraph to be not archived. See comments in
    // archive_reachable_objects_from().
    return;
  }

  // Verify that all objects reachable from orig_obj are archived.
  init_seen_objects_table();
  verify_reachable_objects_from(orig_obj);
  delete_seen_objects_table();
}

void HeapShared::verify_reachable_objects_from(oop obj) {
  _num_total_verifications ++;
  if (java_lang_Class::is_instance(obj)) {
    obj = scratch_java_mirror(obj);
    assert(obj != nullptr, "must be");
  }
  if (!has_been_seen_during_subgraph_recording(obj)) {
    set_has_been_seen_during_subgraph_recording(obj);
    assert(has_been_archived(obj), "must be");
    VerifySharedOopClosure walker;
    obj->oop_iterate(&walker);
  }
}
#endif

void HeapShared::check_special_subgraph_classes() {
  if (CDSConfig::is_dumping_aot_linked_classes()) {
    // We can have aot-initialized classes (such as Enums) that can reference objects
    // of arbitrary types. Currently, we trust the JEP 483 implementation to only
    // aot-initialize classes that are "safe".
    //
    // TODO: we need an automatic tool that checks the safety of aot-initialized
    // classes (when we extend the set of aot-initialized classes beyond JEP 483)
    return;
  } else {
    // In this case, the special subgraph should contain a few specific types
    GrowableArray<Klass*>* klasses = _dump_time_special_subgraph->subgraph_object_klasses();
    int num = klasses->length();
    for (int i = 0; i < num; i++) {
      Klass* subgraph_k = klasses->at(i);
      Symbol* name = subgraph_k->name();
      if (subgraph_k->is_instance_klass() &&
          name != vmSymbols::java_lang_Class() &&
          name != vmSymbols::java_lang_String() &&
          name != vmSymbols::java_lang_ArithmeticException() &&
          name != vmSymbols::java_lang_ArrayIndexOutOfBoundsException() &&
          name != vmSymbols::java_lang_ArrayStoreException() &&
          name != vmSymbols::java_lang_ClassCastException() &&
          name != vmSymbols::java_lang_InternalError() &&
          name != vmSymbols::java_lang_NullPointerException() &&
          name != vmSymbols::jdk_internal_vm_PreemptedException()) {
        ResourceMark rm;
        fatal("special subgraph cannot have objects of type %s", subgraph_k->external_name());
      }
    }
  }
}

HeapShared::SeenObjectsTable* HeapShared::_seen_objects_table = nullptr;
HeapShared::PendingOop HeapShared::_object_being_archived;
size_t HeapShared::_num_new_walked_objs;
size_t HeapShared::_num_new_archived_objs;
size_t HeapShared::_num_old_recorded_klasses;

size_t HeapShared::_num_total_subgraph_recordings = 0;
size_t HeapShared::_num_total_walked_objs = 0;
size_t HeapShared::_num_total_archived_objs = 0;
size_t HeapShared::_num_total_recorded_klasses = 0;
size_t HeapShared::_num_total_verifications = 0;

bool HeapShared::has_been_seen_during_subgraph_recording(oop obj) {
  return _seen_objects_table->get(obj) != nullptr;
}

void HeapShared::set_has_been_seen_during_subgraph_recording(oop obj) {
  assert(!has_been_seen_during_subgraph_recording(obj), "sanity");
  _seen_objects_table->put_when_absent(obj, true);
  _seen_objects_table->maybe_grow();
  ++ _num_new_walked_objs;
}

void HeapShared::start_recording_subgraph(InstanceKlass *k, const char* class_name, bool is_full_module_graph) {
  log_info(aot, heap)("Start recording subgraph(s) for archived fields in %s", class_name);
  init_subgraph_info(k, is_full_module_graph);
  init_seen_objects_table();
  _num_new_walked_objs = 0;
  _num_new_archived_objs = 0;
  _num_old_recorded_klasses = get_subgraph_info(k)->num_subgraph_object_klasses();
}

void HeapShared::done_recording_subgraph(InstanceKlass *k, const char* class_name) {
  size_t num_new_recorded_klasses = get_subgraph_info(k)->num_subgraph_object_klasses() -
    _num_old_recorded_klasses;
  log_info(aot, heap)("Done recording subgraph(s) for archived fields in %s: "
                      "walked %zu objs, archived %zu new objs, recorded %zu classes",
                      class_name, _num_new_walked_objs, _num_new_archived_objs,
                      num_new_recorded_klasses);

  delete_seen_objects_table();

  _num_total_subgraph_recordings ++;
  _num_total_walked_objs      += _num_new_walked_objs;
  _num_total_archived_objs    += _num_new_archived_objs;
  _num_total_recorded_klasses +=  num_new_recorded_klasses;
}

class ArchivableStaticFieldFinder: public FieldClosure {
  InstanceKlass* _ik;
  Symbol* _field_name;
  bool _found;
  int _offset;
public:
  ArchivableStaticFieldFinder(InstanceKlass* ik, Symbol* field_name) :
    _ik(ik), _field_name(field_name), _found(false), _offset(-1) {}

  virtual void do_field(fieldDescriptor* fd) {
    if (fd->name() == _field_name) {
      assert(!_found, "fields can never be overloaded");
      if (is_reference_type(fd->field_type())) {
        _found = true;
        _offset = fd->offset();
      }
    }
  }
  bool found()     { return _found;  }
  int offset()     { return _offset; }
};

void HeapShared::init_subgraph_entry_fields(ArchivableStaticFieldInfo fields[],
                                            TRAPS) {
  for (int i = 0; fields[i].valid(); i++) {
    ArchivableStaticFieldInfo* info = &fields[i];
    TempNewSymbol klass_name =  SymbolTable::new_symbol(info->klass_name);
    TempNewSymbol field_name =  SymbolTable::new_symbol(info->field_name);
    ResourceMark rm; // for stringStream::as_string() etc.

#ifndef PRODUCT
    bool is_test_class = (ArchiveHeapTestClass != nullptr) && (strcmp(info->klass_name, ArchiveHeapTestClass) == 0);
    const char* test_class_name = ArchiveHeapTestClass;
#else
    bool is_test_class = false;
    const char* test_class_name = ""; // avoid C++ printf checks warnings.
#endif

    if (is_test_class) {
      log_warning(aot)("Loading ArchiveHeapTestClass %s ...", test_class_name);
    }

    Klass* k = SystemDictionary::resolve_or_fail(klass_name, true, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
      stringStream st;
      st.print("Fail to initialize archive heap: %s cannot be loaded by the boot loader", info->klass_name);
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), st.as_string());
    }

    if (!k->is_instance_klass()) {
      stringStream st;
      st.print("Fail to initialize archive heap: %s is not an instance class", info->klass_name);
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), st.as_string());
    }

    InstanceKlass* ik = InstanceKlass::cast(k);
    assert(InstanceKlass::cast(ik)->defined_by_boot_loader(),
           "Only support boot classes");

    if (is_test_class) {
      if (ik->module()->is_named()) {
        // We don't want ArchiveHeapTestClass to be abused to easily load/initialize arbitrary
        // core-lib classes. You need to at least append to the bootclasspath.
        stringStream st;
        st.print("ArchiveHeapTestClass %s is not in unnamed module", test_class_name);
        THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), st.as_string());
      }

      if (ik->package() != nullptr) {
        // This restriction makes HeapShared::is_a_test_class_in_unnamed_module() easy.
        stringStream st;
        st.print("ArchiveHeapTestClass %s is not in unnamed package", test_class_name);
        THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), st.as_string());
      }
    } else {
      if (ik->module()->name() != vmSymbols::java_base()) {
        // We don't want to deal with cases when a module is unavailable at runtime.
        // FUTURE -- load from archived heap only when module graph has not changed
        //           between dump and runtime.
        stringStream st;
        st.print("%s is not in java.base module", info->klass_name);
        THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), st.as_string());
      }
    }

    if (is_test_class) {
      log_warning(aot)("Initializing ArchiveHeapTestClass %s ...", test_class_name);
    }
    ik->initialize(CHECK);

    ArchivableStaticFieldFinder finder(ik, field_name);
    ik->do_local_static_fields(&finder);
    if (!finder.found()) {
      stringStream st;
      st.print("Unable to find the static T_OBJECT field %s::%s", info->klass_name, info->field_name);
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), st.as_string());
    }

    info->klass = ik;
    info->offset = finder.offset();
  }
}

void HeapShared::init_subgraph_entry_fields(TRAPS) {
  assert(CDSConfig::is_dumping_heap(), "must be");
  _dump_time_subgraph_info_table = new (mtClass)DumpTimeKlassSubGraphInfoTable();
  if (CDSConfig::is_dumping_klass_subgraphs()) {
    init_subgraph_entry_fields(archive_subgraph_entry_fields, CHECK);
    if (CDSConfig::is_dumping_full_module_graph()) {
      init_subgraph_entry_fields(fmg_archive_subgraph_entry_fields, CHECK);
    }
  }
}

#ifndef PRODUCT
void HeapShared::setup_test_class(const char* test_class_name) {
  ArchivableStaticFieldInfo* p = archive_subgraph_entry_fields;
  int num_slots = sizeof(archive_subgraph_entry_fields) / sizeof(ArchivableStaticFieldInfo);
  assert(p[num_slots - 2].klass_name == nullptr, "must have empty slot that's patched below");
  assert(p[num_slots - 1].klass_name == nullptr, "must have empty slot that marks the end of the list");

  if (test_class_name != nullptr) {
    p[num_slots - 2].klass_name = test_class_name;
    p[num_slots - 2].field_name = ARCHIVE_TEST_FIELD_NAME;
  }
}

// See if ik is one of the test classes that are pulled in by -XX:ArchiveHeapTestClass
// during runtime. This may be called before the module system is initialized so
// we cannot rely on InstanceKlass::module(), etc.
bool HeapShared::is_a_test_class_in_unnamed_module(Klass* ik) {
  if (_test_class != nullptr) {
    if (ik == _test_class) {
      return true;
    }
    Array<Klass*>* klasses = _test_class_record->subgraph_object_klasses();
    if (klasses == nullptr) {
      return false;
    }

    for (int i = 0; i < klasses->length(); i++) {
      Klass* k = klasses->at(i);
      if (k == ik) {
        Symbol* name;
        if (k->is_instance_klass()) {
          name = InstanceKlass::cast(k)->name();
        } else if (k->is_objArray_klass()) {
          Klass* bk = ObjArrayKlass::cast(k)->bottom_klass();
          if (!bk->is_instance_klass()) {
            return false;
          }
          name = bk->name();
        } else {
          return false;
        }

        // See KlassSubGraphInfo::check_allowed_klass() - we only allow test classes
        // to be:
        //   (A) java.base classes (which must not be in the unnamed module)
        //   (B) test classes which must be in the unnamed package of the unnamed module.
        // So if we see a '/' character in the class name, it must be in (A);
        // otherwise it must be in (B).
        if (name->index_of_at(0, "/", 1)  >= 0) {
          return false; // (A)
        }

        return true; // (B)
      }
    }
  }

  return false;
}

void HeapShared::initialize_test_class_from_archive(JavaThread* current) {
  Klass* k = _test_class;
  if (k != nullptr && is_archived_heap_in_use()) {
    JavaThread* THREAD = current;
    ExceptionMark em(THREAD);
    const ArchivedKlassSubGraphInfoRecord* record =
      resolve_or_init_classes_for_subgraph_of(k, /*do_init=*/false, THREAD);

    // The _test_class is in the unnamed module, so it can't call CDS.initializeFromArchive()
    // from its <clinit> method. So we set up its "archivedObjects" field first, before
    // calling its <clinit>. This is not strictly clean, but it's a convenient way to write unit
    // test cases (see test/hotspot/jtreg/runtime/cds/appcds/cacheObject/ArchiveHeapTestClass.java).
    if (record != nullptr) {
      init_archived_fields_for(k, record);
    }
    resolve_or_init_classes_for_subgraph_of(k, /*do_init=*/true, THREAD);
  }
}
#endif

void HeapShared::init_for_dumping(TRAPS) {
  if (CDSConfig::is_dumping_heap()) {
    setup_test_class(ArchiveHeapTestClass);
    init_subgraph_entry_fields(CHECK);
  }
}

void HeapShared::init_heap_writer() {
  if (HeapShared::is_writing_streaming_mode()) {
    AOTStreamedHeapWriter::init();
  } else {
    AOTMappedHeapWriter::init();
  }
}

void HeapShared::archive_object_subgraphs(ArchivableStaticFieldInfo fields[],
                                          bool is_full_module_graph) {
  _num_total_subgraph_recordings = 0;
  _num_total_walked_objs = 0;
  _num_total_archived_objs = 0;
  _num_total_recorded_klasses = 0;
  _num_total_verifications = 0;

  // For each class X that has one or more archived fields:
  // [1] Dump the subgraph of each archived field
  // [2] Create a list of all the class of the objects that can be reached
  //     by any of these static fields.
  //     At runtime, these classes are initialized before X's archived fields
  //     are restored by HeapShared::initialize_from_archived_subgraph().
  for (int i = 0; fields[i].valid(); ) {
    ArchivableStaticFieldInfo* info = &fields[i];
    const char* klass_name = info->klass_name;
    start_recording_subgraph(info->klass, klass_name, is_full_module_graph);

    // If you have specified consecutive fields of the same klass in
    // fields[], these will be archived in the same
    // {start_recording_subgraph ... done_recording_subgraph} pass to
    // save time.
    for (; fields[i].valid(); i++) {
      ArchivableStaticFieldInfo* f = &fields[i];
      if (f->klass_name != klass_name) {
        break;
      }

      archive_reachable_objects_from_static_field(f->klass, f->klass_name,
                                                  f->offset, f->field_name);
    }
    done_recording_subgraph(info->klass, klass_name);
  }

  log_info(aot, heap)("Archived subgraph records = %zu",
                      _num_total_subgraph_recordings);
  log_info(aot, heap)("  Walked %zu objects", _num_total_walked_objs);
  log_info(aot, heap)("  Archived %zu objects", _num_total_archived_objs);
  log_info(aot, heap)("  Recorded %zu klasses", _num_total_recorded_klasses);

#ifndef PRODUCT
  for (int i = 0; fields[i].valid(); i++) {
    ArchivableStaticFieldInfo* f = &fields[i];
    verify_subgraph_from_static_field(f->klass, f->offset);
  }
  log_info(aot, heap)("  Verified %zu references", _num_total_verifications);
#endif
}

bool HeapShared::is_dumped_interned_string(oop o) {
  if (is_writing_mapping_mode()) {
    return AOTMappedHeapWriter::is_dumped_interned_string(o);
  } else {
    return AOTStreamedHeapWriter::is_dumped_interned_string(o);
  }
}

// These tables should be used only within the CDS safepoint, so
// delete them before we exit the safepoint. Otherwise the table will
// contain bad oops after a GC.
void HeapShared::delete_tables_with_raw_oops() {
  assert(_seen_objects_table == nullptr, "should have been deleted");

  if (is_writing_mapping_mode()) {
    AOTMappedHeapWriter::delete_tables_with_raw_oops();
  } else {
    assert(is_writing_streaming_mode(), "what other mode?");
    AOTStreamedHeapWriter::delete_tables_with_raw_oops();
  }
}

void HeapShared::debug_trace() {
  ResourceMark rm;
  oop referrer = _object_being_archived.referrer();
  if (referrer != nullptr) {
    LogStream ls(Log(aot, heap)::error());
    ls.print_cr("Reference trace");
    CDSHeapVerifier::trace_to_root(&ls, referrer);
  }
}

#ifndef PRODUCT
// At dump-time, find the location of all the non-null oop pointers in an archived heap
// region. This way we can quickly relocate all the pointers without using
// BasicOopIterateClosure at runtime.
class FindEmbeddedNonNullPointers: public BasicOopIterateClosure {
  void* _start;
  BitMap *_oopmap;
  size_t _num_total_oops;
  size_t _num_null_oops;
 public:
  FindEmbeddedNonNullPointers(void* start, BitMap* oopmap)
    : _start(start), _oopmap(oopmap), _num_total_oops(0),  _num_null_oops(0) {}

  virtual void do_oop(narrowOop* p) {
    assert(UseCompressedOops, "sanity");
    _num_total_oops ++;
    narrowOop v = *p;
    if (!CompressedOops::is_null(v)) {
      size_t idx = p - (narrowOop*)_start;
      _oopmap->set_bit(idx);
    } else {
      _num_null_oops ++;
    }
  }
  virtual void do_oop(oop* p) {
    assert(!UseCompressedOops, "sanity");
    _num_total_oops ++;
    if ((*p) != nullptr) {
      size_t idx = p - (oop*)_start;
      _oopmap->set_bit(idx);
    } else {
      _num_null_oops ++;
    }
  }
  size_t num_total_oops() const { return _num_total_oops; }
  size_t num_null_oops()  const { return _num_null_oops; }
};
#endif

void HeapShared::count_allocation(size_t size) {
  _total_obj_count ++;
  _total_obj_size += size;
  for (int i = 0; i < ALLOC_STAT_SLOTS; i++) {
    if (size <= (size_t(1) << i)) {
      _alloc_count[i] ++;
      _alloc_size[i] += size;
      return;
    }
  }
}

static double avg_size(size_t size, size_t count) {
  double avg = 0;
  if (count > 0) {
    avg = double(size * HeapWordSize) / double(count);
  }
  return avg;
}

void HeapShared::print_stats() {
  size_t huge_count = _total_obj_count;
  size_t huge_size = _total_obj_size;

  for (int i = 0; i < ALLOC_STAT_SLOTS; i++) {
    size_t byte_size_limit = (size_t(1) << i) * HeapWordSize;
    size_t count = _alloc_count[i];
    size_t size = _alloc_size[i];
    log_info(aot, heap)("%8zu objects are <= %-6zu"
                        " bytes (total %8zu bytes, avg %8.1f bytes)",
                        count, byte_size_limit, size * HeapWordSize, avg_size(size, count));
    huge_count -= count;
    huge_size -= size;
  }

  log_info(aot, heap)("%8zu huge  objects               (total %8zu bytes"
                      ", avg %8.1f bytes)",
                      huge_count, huge_size * HeapWordSize,
                      avg_size(huge_size, huge_count));
  log_info(aot, heap)("%8zu total objects               (total %8zu bytes"
                      ", avg %8.1f bytes)",
                      _total_obj_count, _total_obj_size * HeapWordSize,
                      avg_size(_total_obj_size, _total_obj_count));
}

bool HeapShared::is_metadata_field(oop src_obj, int offset) {
  bool result = false;
  do_metadata_offsets(src_obj, [&](int metadata_offset) {
    if (metadata_offset == offset) {
      result = true;
    }
  });
  return result;
}

void HeapShared::remap_dumped_metadata(oop src_obj, address archived_object) {
  do_metadata_offsets(src_obj, [&](int offset) {
    Metadata** buffered_field_addr = (Metadata**)(archived_object + offset);
    Metadata* native_ptr = *buffered_field_addr;

    if (native_ptr == nullptr) {
      return;
    }

    if (RegeneratedClasses::has_been_regenerated(native_ptr)) {
      native_ptr = RegeneratedClasses::get_regenerated_object(native_ptr);
    }

    address buffered_native_ptr = ArchiveBuilder::current()->get_buffered_addr((address)native_ptr);
    address requested_native_ptr = ArchiveBuilder::current()->to_requested(buffered_native_ptr);
    *buffered_field_addr = (Metadata*)requested_native_ptr;
  });
}

bool HeapShared::is_archived_boot_layer_available(JavaThread* current) {
  TempNewSymbol klass_name = SymbolTable::new_symbol(ARCHIVED_BOOT_LAYER_CLASS);
  InstanceKlass* k = SystemDictionary::find_instance_klass(current, klass_name, Handle());
  if (k == nullptr) {
    return false;
  } else {
    TempNewSymbol field_name = SymbolTable::new_symbol(ARCHIVED_BOOT_LAYER_FIELD);
    TempNewSymbol field_signature = SymbolTable::new_symbol("Ljdk/internal/module/ArchivedBootLayer;");
    fieldDescriptor fd;
    if (k->find_field(field_name, field_signature, true, &fd) != nullptr) {
      oop m = k->java_mirror();
      oop f = m->obj_field(fd.offset());
      if (CompressedOops::is_null(f)) {
        return false;
      }
    } else {
      return false;
    }
  }
  return true;
}

#endif // INCLUDE_CDS_JAVA_HEAP

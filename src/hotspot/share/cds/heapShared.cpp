/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassInitializer.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapLoader.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cdsEnumKlass.hpp"
#include "cds/cdsHeapVerifier.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderExt.hpp"
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
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
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

bool HeapShared::_disable_writing = false;
DumpedInternedStrings *HeapShared::_dumped_interned_strings = nullptr;

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
GrowableArrayCHeap<OopHandle, mtClassShared>* HeapShared::_root_segments;
int HeapShared::_root_segment_max_size_elems;
OopHandle HeapShared::_scratch_basic_type_mirrors[T_VOID+1];
MetaspaceObjToOopHandleTable* HeapShared::_scratch_java_mirror_table = nullptr;
MetaspaceObjToOopHandleTable* HeapShared::_scratch_references_table = nullptr;

static bool is_subgraph_root_class_of(ArchivableStaticFieldInfo fields[], InstanceKlass* ik) {
  for (int i = 0; fields[i].valid(); i++) {
    if (fields[i].klass == ik) {
      return true;
    }
  }
  return false;
}

bool HeapShared::is_subgraph_root_class(InstanceKlass* ik) {
  return is_subgraph_root_class_of(archive_subgraph_entry_fields, ik) ||
         is_subgraph_root_class_of(fmg_archive_subgraph_entry_fields, ik);
}

unsigned HeapShared::oop_hash(oop const& p) {
  // Do not call p->identity_hash() as that will update the
  // object header.
  return primitive_hash(cast_from_oop<intptr_t>(p));
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
      if (log_is_enabled(Debug, cds)) {
        ResourceMark rm(THREAD);
        log_debug(cds)("  calling %s", method->name_and_sig_as_C_string());
      }
      JavaValue result(T_VOID);
      JavaCalls::call_special(&result, h_obj, klass,
                              method_name, method_sig, CHECK);
    }
    klass = klass->java_super();
  }
}

void HeapShared::reset_archived_object_states(TRAPS) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");
  log_debug(cds)("Resetting platform loader");
  reset_states(SystemDictionary::java_platform_loader(), CHECK);
  log_debug(cds)("Resetting system loader");
  reset_states(SystemDictionary::java_system_loader(), CHECK);

  // Clean up jdk.internal.loader.ClassLoaders::bootLoader(), which is not
  // directly used for class loading, but rather is used by the core library
  // to keep track of resources, etc, loaded by the null class loader.
  //
  // Note, this object is non-null, and is not the same as
  // ClassLoaderData::the_null_class_loader_data()->class_loader(),
  // which is null.
  log_debug(cds)("Resetting boot loader");
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

bool HeapShared::has_been_archived(oop obj) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");
  return archived_object_cache()->get(obj) != nullptr;
}

int HeapShared::append_root(oop obj) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");

  // No GC should happen since we aren't scanning _pending_roots.
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");

  if (_pending_roots == nullptr) {
    _pending_roots = new GrowableArrayCHeap<oop, mtClassShared>(500);
  }

  return _pending_roots->append(obj);
}

objArrayOop HeapShared::root_segment(int segment_idx) {
  if (CDSConfig::is_dumping_heap()) {
    assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");
    if (!HeapShared::can_write()) {
      return nullptr;
    }
  } else {
    assert(CDSConfig::is_using_archive(), "must be");
  }

  objArrayOop segment = (objArrayOop)_root_segments->at(segment_idx).resolve();
  assert(segment != nullptr, "should have been initialized");
  return segment;
}

void HeapShared::get_segment_indexes(int idx, int& seg_idx, int& int_idx) {
  assert(_root_segment_max_size_elems > 0, "sanity");

  // Try to avoid divisions for the common case.
  if (idx < _root_segment_max_size_elems) {
    seg_idx = 0;
    int_idx = idx;
  } else {
    seg_idx = idx / _root_segment_max_size_elems;
    int_idx = idx % _root_segment_max_size_elems;
  }

  assert(idx == seg_idx * _root_segment_max_size_elems + int_idx,
         "sanity: %d index maps to %d segment and %d internal", idx, seg_idx, int_idx);
}

// Returns an objArray that contains all the roots of the archived objects
oop HeapShared::get_root(int index, bool clear) {
  assert(index >= 0, "sanity");
  assert(!CDSConfig::is_dumping_heap() && CDSConfig::is_using_archive(), "runtime only");
  assert(!_root_segments->is_empty(), "must have loaded shared heap");
  int seg_idx, int_idx;
  get_segment_indexes(index, seg_idx, int_idx);
  oop result = root_segment(seg_idx)->obj_at(int_idx);
  if (clear) {
    clear_root(index);
  }
  return result;
}

void HeapShared::clear_root(int index) {
  assert(index >= 0, "sanity");
  assert(CDSConfig::is_using_archive(), "must be");
  if (ArchiveHeapLoader::is_in_use()) {
    int seg_idx, int_idx;
    get_segment_indexes(index, seg_idx, int_idx);
    if (log_is_enabled(Debug, cds, heap)) {
      oop old = root_segment(seg_idx)->obj_at(int_idx);
      log_debug(cds, heap)("Clearing root %d: was " PTR_FORMAT, index, p2i(old));
    }
    root_segment(seg_idx)->obj_at_put(int_idx, nullptr);
  }
}

bool HeapShared::archive_object(oop obj) {
  assert(CDSConfig::is_dumping_heap(), "dump-time only");

  assert(!obj->is_stackChunk(), "do not archive stack chunks");
  if (has_been_archived(obj)) {
    return true;
  }

  if (ArchiveHeapWriter::is_too_large_to_archive(obj->size())) {
    log_debug(cds, heap)("Cannot archive, object (" PTR_FORMAT ") is too large: " SIZE_FORMAT,
                         p2i(obj), obj->size());
    debug_trace();
    return false;
  } else {
    count_allocation(obj->size());
    ArchiveHeapWriter::add_source_obj(obj);
    CachedOopInfo info = make_cached_oop_info(obj);
    archived_object_cache()->put_when_absent(obj, info);
    archived_object_cache()->maybe_grow();
    mark_native_pointers(obj);

    if (log_is_enabled(Debug, cds, heap)) {
      ResourceMark rm;
      LogTarget(Debug, cds, heap) log;
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

    if (java_lang_Module::is_instance(obj) && Modules::check_archived_module_oop(obj)) {
      Modules::update_oops_in_archived_module(obj, append_root(obj));
    }

    return true;
  }
}

class MetaspaceObjToOopHandleTable: public ResourceHashtable<MetaspaceObj*, OopHandle,
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
    bool is_new = put(ptr, handle);
    assert(is_new, "cannot set twice");
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
  _scratch_references_table->set_oop(src, dest);
}

objArrayOop HeapShared::scratch_resolved_references(ConstantPool* src) {
  return (objArrayOop)_scratch_references_table->get_oop(src);
}

void HeapShared::init_scratch_objects(TRAPS) {
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    BasicType bt = (BasicType)i;
    if (!is_reference_type(bt)) {
      oop m = java_lang_Class::create_basic_type_mirror(type2name(bt), bt, CHECK);
      _scratch_basic_type_mirrors[i] = OopHandle(Universe::vm_global(), m);
    }
  }
  _scratch_java_mirror_table = new (mtClass)MetaspaceObjToOopHandleTable();
  _scratch_references_table = new (mtClass)MetaspaceObjToOopHandleTable();
}

// Given java_mirror that represents a (primitive or reference) type T,
// return the "scratch" version that represents the same type T.
// Note that if java_mirror will be returned if it's already a
// scratch mirror.
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
  return _scratch_java_mirror_table->get_oop(k);
}

void HeapShared::set_scratch_java_mirror(Klass* k, oop mirror) {
  _scratch_java_mirror_table->set_oop(k, mirror);
}

void HeapShared::remove_scratch_objects(Klass* k) {
  // Klass is being deallocated. Java mirror can still be alive, and it should not
  // point to dead klass. We need to break the link from mirror to the Klass.
  // See how InstanceKlass::deallocate_contents does it for normal mirrors.
  oop mirror = _scratch_java_mirror_table->get_oop(k);
  if (mirror != nullptr) {
    java_lang_Class::set_klass(mirror, nullptr);
  }
  _scratch_java_mirror_table->remove_oop(k);
  if (k->is_instance_klass()) {
    _scratch_references_table->remove(InstanceKlass::cast(k)->constants());
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
  return CDSConfig::is_dumping_invokedynamic() &&
    (is_lambda_form_klass(ik) || is_lambda_proxy_klass(ik) || is_string_concat_klass(ik));
}

void HeapShared::copy_aot_initialized_mirror(Klass* orig_k, oop orig_mirror, oop m) {
  assert(orig_k->is_instance_klass(), "sanity");
  InstanceKlass* ik = InstanceKlass::cast(orig_k);
  InstanceKlass* buffered_ik = ArchiveBuilder::current()->get_buffered_addr(ik);

  assert(ik->is_initialized(), "must be");

  int nfields = 0;
  for (JavaFieldStream fs(ik); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static()) {
      fieldDescriptor& fd = fs.field_descriptor();
      int offset = fd.offset();
      switch (fd.field_type()) {
      case T_OBJECT:
      case T_ARRAY:
        m->obj_field_put(offset, orig_mirror->obj_field(offset));
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

  java_lang_Class::set_class_data(m, java_lang_Class::class_data(orig_mirror));

  // Class::reflectData use SoftReference, which cannot be archived. Set it
  // to null and it will be recreated at runtime.
  java_lang_Class::set_reflection_data(m, nullptr);

  if (log_is_enabled(Info, cds, init)) {
    ResourceMark rm;
    log_debug(cds, init)("copied %3d field(s) in aot-initialized mirror %s%s", nfields, ik->external_name(),
                         ik->is_hidden() ? " (hidden)" : "");
  }
}

static void copy_java_mirror_hashcode(oop orig_mirror, oop scratch_m) {
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
}

static objArrayOop get_archived_resolved_references(InstanceKlass* src_ik) {
  InstanceKlass* buffered_ik = ArchiveBuilder::current()->get_buffered_addr(src_ik);
  if (buffered_ik->is_shared_boot_class() ||
      buffered_ik->is_shared_platform_class() ||
      buffered_ik->is_shared_app_class()) {
    objArrayOop rr = src_ik->constants()->resolved_references_or_null();
    if (rr != nullptr && !ArchiveHeapWriter::is_too_large_to_archive(rr)) {
      return HeapShared::scratch_resolved_references(src_ik->constants());
    }
  }
  return nullptr;
}

void HeapShared::archive_java_mirrors() {
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    BasicType bt = (BasicType)i;
    if (!is_reference_type(bt)) {
      oop orig_mirror = Universe::java_mirror(bt);
      oop m = _scratch_basic_type_mirrors[i].resolve();
      assert(m != nullptr, "sanity");
      copy_java_mirror_hashcode(orig_mirror, m);
      bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, m);
      assert(success, "sanity");

      log_trace(cds, heap, mirror)(
        "Archived %s mirror object from " PTR_FORMAT,
        type2name(bt), p2i(m));

      Universe::set_archived_basic_type_mirror_index(bt, append_root(m));
    }
  }

  GrowableArray<Klass*>* klasses = ArchiveBuilder::current()->klasses();
  assert(klasses != nullptr, "sanity");

  for (int i = 0; i < klasses->length(); i++) {
    Klass* orig_k = klasses->at(i);
    oop orig_mirror = orig_k->java_mirror();
    oop m = scratch_java_mirror(orig_k);
    if (m != nullptr) {
      copy_java_mirror_hashcode(orig_mirror, m);
    }
  }

  for (int i = 0; i < klasses->length(); i++) {
    Klass* orig_k = klasses->at(i);
    oop orig_mirror = orig_k->java_mirror();
    oop m = scratch_java_mirror(orig_k);
    if (m != nullptr) {
      Klass* buffered_k = ArchiveBuilder::get_buffered_klass(orig_k);
      bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, m);
      guarantee(success, "scratch mirrors must point to only archivable objects");
      buffered_k->set_archived_java_mirror(append_root(m));
      ResourceMark rm;
      log_trace(cds, heap, mirror)(
        "Archived %s mirror object from " PTR_FORMAT,
        buffered_k->external_name(), p2i(m));

      // archive the resolved_referenes array
      if (buffered_k->is_instance_klass()) {
        InstanceKlass* ik = InstanceKlass::cast(buffered_k);
        objArrayOop rr = get_archived_resolved_references(InstanceKlass::cast(orig_k));
        if (rr != nullptr) {
          bool success = HeapShared::archive_reachable_objects_from(1, _dump_time_special_subgraph, rr);
          assert(success, "must be");
          int root_index = append_root(rr);
          ik->constants()->cache()->set_archived_references(root_index);
        }
      }
    }
  }
}

void HeapShared::archive_strings() {
  oop shared_strings_array = StringTable::init_shared_table(_dumped_interned_strings);
  bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, shared_strings_array);
  // We must succeed because:
  // - _dumped_interned_strings do not contain any large strings.
  // - StringTable::init_shared_table() doesn't create any large arrays.
  assert(success, "shared strings array must not point to arrays or strings that are too large to archive");
  StringTable::set_shared_strings_array_index(append_root(shared_strings_array));
}

int HeapShared::archive_exception_instance(oop exception) {
  bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, exception);
  assert(success, "sanity");
  return append_root(exception);
}

void HeapShared::mark_native_pointers(oop orig_obj) {
  if (java_lang_Class::is_instance(orig_obj)) {
    ArchiveHeapWriter::mark_native_pointer(orig_obj, java_lang_Class::klass_offset());
    ArchiveHeapWriter::mark_native_pointer(orig_obj, java_lang_Class::array_klass_offset());
  } else if (java_lang_invoke_ResolvedMethodName::is_instance(orig_obj)) {
    ArchiveHeapWriter::mark_native_pointer(orig_obj, java_lang_invoke_ResolvedMethodName::vmtarget_offset());
  }
}

void HeapShared::get_pointer_info(oop src_obj, bool& has_oop_pointers, bool& has_native_pointers) {
  CachedOopInfo* info = archived_object_cache()->get(src_obj);
  assert(info != nullptr, "must be");
  has_oop_pointers = info->has_oop_pointers();
  has_native_pointers = info->has_native_pointers();
}

void HeapShared::set_has_native_pointers(oop src_obj) {
  CachedOopInfo* info = archived_object_cache()->get(src_obj);
  assert(info != nullptr, "must be");
  info->set_has_native_pointers();
}

void HeapShared::start_finding_required_hidden_classes() {
  if (!CDSConfig::is_dumping_invokedynamic()) {
    return;
  }
  NoSafepointVerifier nsv;

  init_seen_objects_table();

  // We first scan the objects that are known to be archived (from the archive_subgraph
  // tables)
  find_required_hidden_classes_helper(archive_subgraph_entry_fields);
  if (CDSConfig::is_dumping_full_module_graph()) {
    find_required_hidden_classes_helper(fmg_archive_subgraph_entry_fields);
  }

  // Later, SystemDictionaryShared::find_all_archivable_classes_impl() will start
  // scanning the constant pools of all classes that it decides to archive.
}

void HeapShared::end_finding_required_hidden_classes() {
  if (!CDSConfig::is_dumping_invokedynamic()) {
    return;
  }
  NoSafepointVerifier nsv;

  delete_seen_objects_table();
}

void HeapShared::find_required_hidden_classes_helper(ArchivableStaticFieldInfo fields[]) {
  if (!CDSConfig::is_dumping_heap()) {
    return;
  }
  for (int i = 0; fields[i].valid(); i++) {
    ArchivableStaticFieldInfo* f = &fields[i];
    InstanceKlass* k = f->klass;
    oop m = k->java_mirror();
    oop o = m->obj_field(f->offset);
    if (o != nullptr) {
      find_required_hidden_classes_in_object(o);
    }
  }
}

class HeapShared::FindRequiredHiddenClassesOopClosure: public BasicOopIterateClosure {
  GrowableArray<oop> _stack;
  template <class T> void do_oop_work(T *p) {
    // Recurse on a GrowableArray to avoid overflowing the C stack.
    oop o = RawAccess<>::oop_load(p);
    if (o != nullptr) {
      _stack.append(o);
    }
  }

 public:

  void do_oop(narrowOop *p) { FindRequiredHiddenClassesOopClosure::do_oop_work(p); }
  void do_oop(      oop *p) { FindRequiredHiddenClassesOopClosure::do_oop_work(p); }

  FindRequiredHiddenClassesOopClosure(oop o) {
    _stack.append(o);
  }
  oop pop() {
    if (_stack.length() == 0) {
      return nullptr;
    } else {
      return _stack.pop();
    }
  }
};

static void mark_required_if_hidden_class(Klass* k) {
  if (k != nullptr && k->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(k);
    if (ik->is_hidden()) {
      SystemDictionaryShared::mark_required_hidden_class(ik);
    }
  }
}


void HeapShared::find_required_hidden_classes_in_object(oop root) {
  ResourceMark rm;
  FindRequiredHiddenClassesOopClosure c(root);
  oop o;
  while ((o = c.pop()) != nullptr) {
    if (!has_been_seen_during_subgraph_recording(o)) {
      set_has_been_seen_during_subgraph_recording(o);

      // Mark the klass of this object
      mark_required_if_hidden_class(o->klass());

      // For special objects, mark the klass that they contain information about.
      // - a Class that refers to an hidden class
      // - a ResolvedMethodName that refers to a method declared in a hidden class
      if (java_lang_Class::is_instance(o)) {
        mark_required_if_hidden_class(java_lang_Class::as_Klass(o));
      } else if (java_lang_invoke_ResolvedMethodName::is_instance(o)) {
        Method* m = java_lang_invoke_ResolvedMethodName::vmtarget(o);
        if (m != nullptr) {
          mark_required_if_hidden_class(m->method_holder());
        }
      }

      o->oop_iterate(&c);
    }
  }
}

void HeapShared::archive_objects(ArchiveHeapInfo *heap_info) {
  {
    NoSafepointVerifier nsv;

    // The special subgraph doesn't belong to any class. We use Object_klass() here just
    // for convenience.
    _dump_time_special_subgraph = init_subgraph_info(vmClasses::Object_klass(), false);

    // Cache for recording where the archived objects are copied to
    create_archived_object_cache();

    if (UseCompressedOops || UseG1GC) {
      log_info(cds)("Heap range = [" PTR_FORMAT " - "  PTR_FORMAT "]",
                    UseCompressedOops ? p2i(CompressedOops::begin()) :
                                        p2i((address)G1CollectedHeap::heap()->reserved().start()),
                    UseCompressedOops ? p2i(CompressedOops::end()) :
                                        p2i((address)G1CollectedHeap::heap()->reserved().end()));
    }
    copy_objects();

    CDSHeapVerifier::verify();
    check_special_subgraph_classes();
  }

  ArchiveHeapWriter::write(_pending_roots, heap_info);
}

void HeapShared::copy_interned_strings() {
  init_seen_objects_table();

  auto copier = [&] (oop s, bool value_ignored) {
    assert(s != nullptr, "sanity");
    assert(!ArchiveHeapWriter::is_string_too_large_to_archive(s), "large strings must have been filtered");
    bool success = archive_reachable_objects_from(1, _dump_time_special_subgraph, s);
    assert(success, "must be");
    // Prevent string deduplication from changing the value field to
    // something not in the archive.
    java_lang_String::set_deduplication_forbidden(s);
  };
  _dumped_interned_strings->iterate_all(copier);

  delete_seen_objects_table();
}

void HeapShared::copy_special_subgraph() {
  copy_interned_strings();

  init_seen_objects_table();
  {
    archive_java_mirrors();
    archive_strings();
    Universe::archive_exception_instances();
  }
  delete_seen_objects_table();
}

void HeapShared::prepare_resolved_references() {
  GrowableArray<Klass*>* klasses = ArchiveBuilder::current()->klasses();
  for (int i = 0; i < klasses->length(); i++) {
    Klass* src_k = klasses->at(i);
    if (src_k->is_instance_klass()) {
      InstanceKlass* buffered_ik = ArchiveBuilder::current()->get_buffered_addr(InstanceKlass::cast(src_k));
      buffered_ik->constants()->prepare_resolved_references_for_archiving();
    }
  }
}

void HeapShared::copy_objects() {
  assert(HeapShared::can_write(), "must be");

  prepare_resolved_references();
  find_all_aot_initialized_classes();
  copy_special_subgraph();

  archive_object_subgraphs(archive_subgraph_entry_fields,
                           false /* is_full_module_graph */);

  if (CDSConfig::is_dumping_full_module_graph()) {
    archive_object_subgraphs(fmg_archive_subgraph_entry_fields,
                             true /* is_full_module_graph */);
    Modules::verify_archived_modules();
  }
}

// Closure used by HeapShared::scan_for_aot_initialized_classes() to look for all objects
// that are reachable from a given root.
class HeapShared::AOTInitializedClassScanner : public BasicOopIterateClosure {
  bool _made_progress;

  template <class T> void check(T *p) {
    oop obj = HeapAccess<>::oop_load(p);
    if (!java_lang_Class::is_instance(obj)) {
      // Don't scan the mirrors, as we may see an orig_mirror while scanning
      // the object graph, .... TODO more info
      _made_progress |= HeapShared::scan_for_aot_initialized_classes(obj);
    }
  }

public:
  AOTInitializedClassScanner() : _made_progress(false) {}
  void do_oop(narrowOop *p) { check(p); }
  void do_oop(      oop *p) { check(p); }
  bool made_progress() { return _made_progress; }
};

// If <buffered_ik> has been initialized during the assembly phase, mark its
// has_aot_initialized_mirror bit. And then do the same for all supertypes of
// <buffered_ik>.
//
// Note: a super interface <intf> of <buffered_ik> may not have been initialized, if
// <intf> has not declared any default methods.
//
// Note: this function doesn not call InstanceKlass::initialize() -- we are inside
// a safepoint.
//
// Returns true if one or more classes have been newly marked.
static bool mark_for_aot_initialization(InstanceKlass* buffered_ik) {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity");
  assert(ArchiveBuilder::current()->is_in_buffer_space(buffered_ik), "sanity");

  if (buffered_ik->has_aot_initialized_mirror()) { // already marked
    return false;
  }

  bool made_progress = false;
  if (buffered_ik->is_initialized()) {
    if (log_is_enabled(Info, cds, init)) {
      ResourceMark rm;
      log_info(cds, init)("Mark class for aot-init: %s", buffered_ik->external_name());
    }

    InstanceKlass* src_ik = ArchiveBuilder::current()->get_source_addr(buffered_ik);

    // If we get here with a "wild" user class, which may have
    // uncontrolled <clinit> code, exit with an error.  Obviously
    // filtering logic upstream needs to detect APP classes and not mark
    // them for aot-init in the first place, but this will be the final
    // firewall.

#ifndef PRODUCT
    // ArchiveHeapTestClass is used for a very small number of internal regression
    // tests (non-product builds only). It may initialize some unexpected classes.
    if (ArchiveHeapTestClass == nullptr)
#endif
    {
      if (!src_ik->in_javabase_module()) {
        // Class/interface types in the boot loader may have been initialized as side effects
        // of JVM bootstrap code, so they are fine. But we need to check all other classes.
        if (buffered_ik->is_interface()) {
          // This probably means a bug in AOTConstantPoolResolver.::is_indy_resolution_deterministic()
          guarantee(!buffered_ik->interface_needs_clinit_execution_as_super(),
                    "should not have initialized an interface whose <clinit> might have unpredictable side effects");
        } else {
          // "normal" classes
          guarantee(HeapShared::is_archivable_hidden_klass(buffered_ik),
                    "should not have initialized any non-interface, non-hidden classes outside of java.base");
        }
      }
    }

    buffered_ik->set_has_aot_initialized_mirror();
    if (AOTClassInitializer::is_runtime_setup_required(src_ik)) {
      buffered_ik->set_is_runtime_setup_required();
    }
    made_progress = true;

    InstanceKlass* super = buffered_ik->java_super();
    if (super != nullptr) {
      mark_for_aot_initialization(super);
    }

    Array<InstanceKlass*>* interfaces = buffered_ik->transitive_interfaces();
    for (int i = 0; i < interfaces->length(); i++) {
      InstanceKlass* intf = interfaces->at(i);
      mark_for_aot_initialization(intf);
      if (!intf->is_initialized()) {
        assert(!intf->interface_needs_clinit_execution_as_super(/*also_check_supers*/false), "sanity");
        assert(!intf->has_aot_initialized_mirror(), "must not be marked");
      }
    }
  }

  return made_progress;
}

void HeapShared::find_all_aot_initialized_classes() {
  if (!CDSConfig::is_dumping_aot_linked_classes()) {
    return;
  }

  init_seen_objects_table();
  find_all_aot_initialized_classes_helper();
  delete_seen_objects_table();
}

// Recursively find all class that should be aot-initialized:
// - the class has at least one instance that can be reachable from the special subgraph; or
// - the class is hard-coded in AOTClassInitializer::can_archive_initialized_mirror()
void HeapShared::find_all_aot_initialized_classes_helper() {
  GrowableArray<Klass*>* klasses = ArchiveBuilder::current()->klasses();
  assert(klasses != nullptr, "sanity");

  // First scan all resolved constant pools references.
  for (int i = 0; i < klasses->length(); i++) {
    Klass* src_k = klasses->at(i);
    if (src_k->is_instance_klass()) {
      InstanceKlass* src_ik = InstanceKlass::cast(src_k);
      InstanceKlass* buffered_ik = ArchiveBuilder::current()->get_buffered_addr(src_ik);
      objArrayOop rr = get_archived_resolved_references(src_ik);
      if (rr != nullptr) {
        objArrayOop scratch_rr = scratch_resolved_references(src_ik->constants());
        for (int i = 0; i < scratch_rr->length(); i++) {
          scan_for_aot_initialized_classes(scratch_rr->obj_at(i));
        }
      }

      // If a class is hard-coded to be aot-initialize, mark it as such.
      if (AOTClassInitializer::can_archive_initialized_mirror(src_ik)) {
        mark_for_aot_initialization(buffered_ik);
      }
    }
  }

  // These objects also belong to the special subgraph
  scan_for_aot_initialized_classes(Universe::null_ptr_exception_instance());
  scan_for_aot_initialized_classes(Universe::arithmetic_exception_instance());
  scan_for_aot_initialized_classes(Universe::internal_error_instance());
  scan_for_aot_initialized_classes(Universe::array_index_out_of_bounds_exception_instance());
  scan_for_aot_initialized_classes(Universe::array_store_exception_instance());
  scan_for_aot_initialized_classes(Universe::class_cast_exception_instance());

  bool made_progress;
  do {
    // In each pass, we copy the scratch mirrors of the classes that were marked
    // as aot-init in the previous pass. We then scan these mirrors, which may
    // mark more classes. Keep iterating until no more progress can be made.
    made_progress = false;
    for (int i = 0; i < klasses->length(); i++) {
      Klass* orig_k = klasses->at(i);
      if (orig_k->is_instance_klass()) {
        InstanceKlass* orig_ik = InstanceKlass::cast(orig_k);
        if (ArchiveBuilder::current()->get_buffered_addr(orig_ik)->has_aot_initialized_mirror()) {
          oop orig_mirror = orig_ik->java_mirror();
          oop scratch_mirror = scratch_java_mirror(orig_k);
          if (!has_been_seen_during_subgraph_recording(scratch_mirror)) {
            // Scan scratch_mirror instead of orig_mirror (which has fields like ClassLoader that
            // are not archived).
            copy_aot_initialized_mirror(orig_k, orig_mirror, scratch_mirror);
            made_progress |= scan_for_aot_initialized_classes(scratch_mirror);
          }
        }
      }
    }
  } while (made_progress);
}

bool HeapShared::scan_for_aot_initialized_classes(oop obj) {
  if (obj == nullptr || has_been_seen_during_subgraph_recording(obj)) {
    return false;
  }
  set_has_been_seen_during_subgraph_recording(obj);

  bool made_progress = false;
  Klass* k = obj->klass();
  if (k->is_instance_klass()) {
    InstanceKlass* orig_ik = InstanceKlass::cast(k);
    InstanceKlass* buffered_ik = ArchiveBuilder::current()->get_buffered_addr(orig_ik);
    made_progress = mark_for_aot_initialization(buffered_ik);
  }

  AOTInitializedClassScanner scanner;
  obj->oop_iterate(&scanner);
  made_progress |= scanner.made_progress();
  return made_progress;
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
  Klass* buffered_k = ArchiveBuilder::get_buffered_klass(k);
  KlassSubGraphInfo* info =
    _dump_time_subgraph_info_table->put_if_absent(k, KlassSubGraphInfo(buffered_k, is_full_module_graph),
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
  Klass* buffered_k = ArchiveBuilder::get_buffered_klass(orig_k);

  if (_subgraph_object_klasses == nullptr) {
    _subgraph_object_klasses =
      new (mtClass) GrowableArray<Klass*>(50, mtClass);
  }

  assert(ArchiveBuilder::current()->is_in_buffer_space(buffered_k), "must be a shared class");

  if (_k == buffered_k) {
    // Don't add the Klass containing the sub-graph to it's own klass
    // initialization list.
    return;
  }

  if (buffered_k->is_instance_klass()) {
    if (CDSConfig::is_dumping_invokedynamic()) {
      assert(InstanceKlass::cast(buffered_k)->is_shared_boot_class() ||
             HeapShared::is_lambda_proxy_klass(InstanceKlass::cast(buffered_k)),
            "we can archive only instances of boot classes or lambda proxy classes");
    } else {
      assert(InstanceKlass::cast(buffered_k)->is_shared_boot_class(),
             "must be boot class");
    }
    // vmClasses::xxx_klass() are not updated, need to check
    // the original Klass*
    if (orig_k == vmClasses::String_klass() ||
        orig_k == vmClasses::Object_klass()) {
      // Initialized early during VM initialization. No need to be added
      // to the sub-graph object class list.
      return;
    }
    if (buffered_k->has_aot_initialized_mirror()) {
      // No need to add to the runtime-init list.
      return;
    }
    check_allowed_klass(InstanceKlass::cast(orig_k));
  } else if (buffered_k->is_objArray_klass()) {
    Klass* abk = ObjArrayKlass::cast(buffered_k)->bottom_klass();
    if (abk->is_instance_klass()) {
      assert(InstanceKlass::cast(abk)->is_shared_boot_class(),
            "must be boot class");
      check_allowed_klass(InstanceKlass::cast(ObjArrayKlass::cast(orig_k)->bottom_klass()));
    }
    if (buffered_k == Universe::objectArrayKlass()) {
      // Initialized early during Universe::genesis. No need to be added
      // to the list.
      return;
    }
  } else {
    assert(buffered_k->is_typeArray_klass(), "must be");
    // Primitive type arrays are created early during Universe::genesis.
    return;
  }

  if (log_is_enabled(Debug, cds, heap)) {
    if (!_subgraph_object_klasses->contains(buffered_k)) {
      ResourceMark rm;
      log_debug(cds, heap)("Adding klass %s", orig_k->external_name());
    }
  }

  _subgraph_object_klasses->append_if_missing(buffered_k);
  _has_non_early_klasses |= is_non_early_klass(orig_k);
}

void KlassSubGraphInfo::check_allowed_klass(InstanceKlass* ik) {
  if (ik->module()->name() == vmSymbols::java_base()) {
    assert(ik->package() != nullptr, "classes in java.base cannot be in unnamed package");
    return;
  }

  const char* lambda_msg = "";
  if (CDSConfig::is_dumping_invokedynamic()) {
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
  log_error(cds, heap)("Class %s not allowed in archive heap. Must be in java.base%s%s",
                       ik->external_name(), lambda_msg, testcls_msg);
  MetaspaceShared::unrecoverable_writing_error();
}

bool KlassSubGraphInfo::is_non_early_klass(Klass* k) {
  if (k->is_objArray_klass()) {
    k = ObjArrayKlass::cast(k)->bottom_klass();
  }
  if (k->is_instance_klass()) {
    if (!SystemDictionaryShared::is_early_klass(InstanceKlass::cast(k))) {
      ResourceMark rm;
      log_info(cds, heap)("non-early: %s", k->external_name());
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
  _k = info->klass();
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
    log_info(cds, heap)(
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

  // the Klasses of the objects in the sub-graphs
  GrowableArray<Klass*>* subgraph_object_klasses = info->subgraph_object_klasses();
  if (subgraph_object_klasses != nullptr) {
    int num_subgraphs_klasses = subgraph_object_klasses->length();
    _subgraph_object_klasses =
      ArchiveBuilder::new_ro_array<Klass*>(num_subgraphs_klasses);
    bool is_special = (_k == ArchiveBuilder::get_buffered_klass(vmClasses::Object_klass()));
    for (int i = 0; i < num_subgraphs_klasses; i++) {
      Klass* subgraph_k = subgraph_object_klasses->at(i);
      if (log_is_enabled(Info, cds, heap)) {
        ResourceMark rm;
        const char* owner_name =  is_special ? "<special>" : _k->external_name();
        if (subgraph_k->is_instance_klass()) {
          InstanceKlass* src_ik = InstanceKlass::cast(ArchiveBuilder::current()->get_source_addr(subgraph_k));
        }
        log_info(cds, heap)(
          "Archived object klass %s (%2d) => %s",
          owner_name, i, subgraph_k->external_name());
      }
      _subgraph_object_klasses->at_put(i, subgraph_k);
      ArchivePtrMarker::mark_pointer(_subgraph_object_klasses->adr_at(i));
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

  CompactHashtableWriter writer(d_table->_count, &stats);
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
  if (log_is_enabled(Info, cds, heap)) {
    print_stats();
  }
}

void HeapShared::add_root_segment(objArrayOop segment_oop) {
  assert(segment_oop != nullptr, "must be");
  assert(ArchiveHeapLoader::is_in_use(), "must be");
  if (_root_segments == nullptr) {
    _root_segments = new GrowableArrayCHeap<OopHandle, mtClassShared>(10);
  }
  _root_segments->push(OopHandle(Universe::vm_global(), segment_oop));
}

void HeapShared::init_root_segment_sizes(int max_size_elems) {
  _root_segment_max_size_elems = max_size_elems;
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
    log_info(cds, heap)("Verify heap %s initializing static field(s) in %s",
                        which, k->external_name());

    VM_Verify verify_op;
    VMThread::execute(&verify_op);

    if (VerifyArchivedFields > 1 && is_init_completed()) {
      // At this time, the oop->klass() of some archived objects in the heap may not
      // have been loaded into the system dictionary yet. Nevertheless, oop->klass() should
      // have enough information (object size, oop maps, etc) so that a GC can be safely
      // performed.
      //
      // -XX:VerifyArchivedFields=2 force a GC to happen in such an early stage
      // to check for GC safety.
      log_info(cds, heap)("Trigger GC %s initializing static field(s) in %s",
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
  if (!ArchiveHeapLoader::is_in_use()) {
    return; // nothing to do
  }
  resolve_classes_for_subgraphs(current, archive_subgraph_entry_fields);
  resolve_classes_for_subgraphs(current, fmg_archive_subgraph_entry_fields);
}

void HeapShared::resolve_classes_for_subgraphs(JavaThread* current, ArchivableStaticFieldInfo fields[]) {
  for (int i = 0; fields[i].valid(); i++) {
    ArchivableStaticFieldInfo* info = &fields[i];
    TempNewSymbol klass_name = SymbolTable::new_symbol(info->klass_name);
    InstanceKlass* k = SystemDictionaryShared::find_builtin_class(klass_name);
    assert(k != nullptr && k->is_shared_boot_class(), "sanity");
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
  if (CDSConfig::is_loading_invokedynamic() || CDSConfig::is_dumping_invokedynamic()) {
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
  if (!ArchiveHeapLoader::is_in_use()) {
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
  if (!ArchiveHeapLoader::is_in_use()) {
    return; // nothing to do
  }

  if (k->name()->equals("jdk/internal/module/ArchivedModuleGraph") &&
      !CDSConfig::is_using_optimized_module_handling() &&
      // archive was created with --module-path
      ClassLoaderExt::num_module_paths() > 0) {
    // ArchivedModuleGraph was created with a --module-path that's different than the runtime --module-path.
    // Thus, it might contain references to modules that do not exist at runtime. We cannot use it.
    log_info(cds, heap)("Skip initializing ArchivedModuleGraph subgraph: is_using_optimized_module_handling=%s num_module_paths=%d",
                        BOOL_TO_STR(CDSConfig::is_using_optimized_module_handling()), ClassLoaderExt::num_module_paths());
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

  if (!k->is_shared()) {
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
    if (log_is_enabled(Info, cds, heap)) {
      ResourceMark rm(THREAD);
      log_info(cds, heap)("subgraph %s is not recorded",
                          k->external_name());
    }
    return nullptr;
  } else {
    if (record->is_full_module_graph() && !CDSConfig::is_using_full_module_graph()) {
      if (log_is_enabled(Info, cds, heap)) {
        ResourceMark rm(THREAD);
        log_info(cds, heap)("subgraph %s cannot be used because full module graph is disabled",
                            k->external_name());
      }
      return nullptr;
    }

    if (record->has_non_early_klasses() && JvmtiExport::should_post_class_file_load_hook()) {
      if (log_is_enabled(Info, cds, heap)) {
        ResourceMark rm(THREAD);
        log_info(cds, heap)("subgraph %s cannot be used because JVMTI ClassFileLoadHook is enabled",
                            k->external_name());
      }
      return nullptr;
    }

    if (log_is_enabled(Info, cds, heap)) {
      ResourceMark rm;
      log_info(cds, heap)("%s subgraph %s ", do_init ? "init" : "resolve", k->external_name());
    }

    resolve_or_init(k, do_init, CHECK_NULL);

    // Load/link/initialize the klasses of the objects in the subgraph.
    // nullptr class loader is used.
    Array<Klass*>* klasses = record->subgraph_object_klasses();
    if (klasses != nullptr) {
      for (int i = 0; i < klasses->length(); i++) {
        Klass* klass = klasses->at(i);
        if (!klass->is_shared()) {
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
  assert(k->is_shared_boot_class(), "sanity");
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

  // Load the subgraph entry fields from the record and store them back to
  // the corresponding fields within the mirror.
  oop m = k->java_mirror();
  Array<int>* entry_field_records = record->entry_field_records();
  if (entry_field_records != nullptr) {
    int efr_len = entry_field_records->length();
    assert(efr_len % 2 == 0, "sanity");
    for (int i = 0; i < efr_len; i += 2) {
      int field_offset = entry_field_records->at(i);
      int root_index = entry_field_records->at(i+1);
      oop v = get_root(root_index, /*clear=*/true);
      if (k->has_aot_initialized_mirror()) {
        assert(v == m->obj_field(field_offset), "must be aot-initialized");
      } else {
        m->obj_field_put(field_offset, v);
      }
      log_debug(cds, heap)("  " PTR_FORMAT " init field @ %2d = " PTR_FORMAT, p2i(k), field_offset, p2i(v));
    }

    // Done. Java code can see the archived sub-graphs referenced from k's
    // mirror after this point.
    if (log_is_enabled(Info, cds, heap)) {
      ResourceMark rm;
      log_info(cds, heap)("initialize_from_archived_subgraph %s " PTR_FORMAT "%s%s",
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

class WalkOopAndArchiveClosure: public BasicOopIterateClosure {
  int _level;
  bool _record_klasses_only;
  KlassSubGraphInfo* _subgraph_info;
  oop _referencing_obj;

  // The following are for maintaining a stack for determining
  // CachedOopInfo::_referrer
  static WalkOopAndArchiveClosure* _current;
  WalkOopAndArchiveClosure* _last;
 public:
  WalkOopAndArchiveClosure(int level,
                           bool record_klasses_only,
                           KlassSubGraphInfo* subgraph_info,
                           oop orig) :
    _level(level),
    _record_klasses_only(record_klasses_only),
    _subgraph_info(subgraph_info),
    _referencing_obj(orig) {
    _last = _current;
    _current = this;
  }
  ~WalkOopAndArchiveClosure() {
    _current = _last;
  }
  void do_oop(narrowOop *p) { WalkOopAndArchiveClosure::do_oop_work(p); }
  void do_oop(      oop *p) { WalkOopAndArchiveClosure::do_oop_work(p); }

 protected:
  template <class T> void do_oop_work(T *p) {
    oop obj = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(obj)) {
      size_t field_delta = pointer_delta(p, _referencing_obj, sizeof(char));

      if (!_record_klasses_only && log_is_enabled(Debug, cds, heap)) {
        ResourceMark rm;
        log_debug(cds, heap)("(%d) %s[" SIZE_FORMAT "] ==> " PTR_FORMAT " size " SIZE_FORMAT " %s", _level,
                             _referencing_obj->klass()->external_name(), field_delta,
                             p2i(obj), obj->size() * HeapWordSize, obj->klass()->external_name());
        if (log_is_enabled(Trace, cds, heap)) {
          LogTarget(Trace, cds, heap) log;
          LogStream out(log);
          obj->print_on(&out);
        }
      }

      bool success = HeapShared::archive_reachable_objects_from(
          _level + 1, _subgraph_info, obj);
      assert(success, "VM should have exited with unarchivable objects for _level > 1");
    }
  }

 public:
  static WalkOopAndArchiveClosure* current()  { return _current;              }
  oop referencing_obj()                       { return _referencing_obj;      }
  KlassSubGraphInfo* subgraph_info()          { return _subgraph_info;        }
};

WalkOopAndArchiveClosure* WalkOopAndArchiveClosure::_current = nullptr;

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

HeapShared::CachedOopInfo HeapShared::make_cached_oop_info(oop obj) {
  WalkOopAndArchiveClosure* walker = WalkOopAndArchiveClosure::current();
  oop referrer = (walker == nullptr) ? nullptr : walker->referencing_obj();
  PointsToOopsChecker points_to_oops_checker;
  obj->oop_iterate(&points_to_oops_checker);
  return CachedOopInfo(referrer, points_to_oops_checker.result());
}

void HeapShared::init_box_classes(TRAPS) {
  if (ArchiveHeapLoader::is_in_use()) {
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
// (3) Record the klasses of all orig_obj and all reachable objects.
bool HeapShared::archive_reachable_objects_from(int level,
                                                KlassSubGraphInfo* subgraph_info,
                                                oop orig_obj) {
  assert(orig_obj != nullptr, "must be");

  if (!JavaClasses::is_supported_for_archiving(orig_obj)) {
    // This object has injected fields that cannot be supported easily, so we disallow them for now.
    // If you get an error here, you probably made a change in the JDK library that has added
    // these objects that are referenced (directly or indirectly) by static fields.
    ResourceMark rm;
    log_error(cds, heap)("Cannot archive object " PTR_FORMAT " of class %s", p2i(orig_obj), orig_obj->klass()->external_name());
    debug_trace();
    MetaspaceShared::unrecoverable_writing_error();
  }

  if (log_is_enabled(Debug, cds, heap) && java_lang_Class::is_instance(orig_obj)) {
    ResourceMark rm;
    LogTarget(Debug, cds, heap) log;
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

  if (CDSConfig::is_initing_classes_at_dump_time()) {
    if (java_lang_Class::is_instance(orig_obj)) {
      orig_obj = scratch_java_mirror(orig_obj);
      assert(orig_obj != nullptr, "must be archived");
    }
  } else if (java_lang_Class::is_instance(orig_obj) && subgraph_info != _dump_time_special_subgraph) {
    // Without CDSConfig::is_initing_classes_at_dump_time(), we only allow archived objects to
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
      log_error(cds, heap)("(%d) Unknown java.lang.Class object is in the archived sub-graph", level);
      debug_trace();
      MetaspaceShared::unrecoverable_writing_error();
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
    if (!archive_object(orig_obj)) {
      // Skip archiving the sub-graph referenced from the current entry field.
      ResourceMark rm;
      log_error(cds, heap)(
        "Cannot archive the sub-graph referenced from %s object ("
        PTR_FORMAT ") size " SIZE_FORMAT ", skipped.",
        orig_obj->klass()->external_name(), p2i(orig_obj), orig_obj->size() * HeapWordSize);
      if (level == 1) {
        // Don't archive a subgraph root that's too big. For archives static fields, that's OK
        // as the Java code will take care of initializing this field dynamically.
        return false;
      } else {
        // We don't know how to handle an object that has been archived, but some of its reachable
        // objects cannot be archived. Bail out for now. We might need to fix this in the future if
        // we have a real use case.
        MetaspaceShared::unrecoverable_writing_error();
      }
    }
  }

  Klass *orig_k = orig_obj->klass();
  subgraph_info->add_subgraph_object_klass(orig_k);

  WalkOopAndArchiveClosure walker(level, record_klasses_only, subgraph_info, orig_obj);
  orig_obj->oop_iterate(&walker);

  if (CDSConfig::is_initing_classes_at_dump_time()) {
    // The enum klasses are archived with aot-initialized mirror.
    // See AOTClassInitializer::can_archive_initialized_mirror().
  } else {
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
// The Java heap object sub-graph archiving process (see
// WalkOopAndArchiveClosure):
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
  assert(CDSConfig::is_dumping_heap(), "dump time only");
  assert(k->is_shared_boot_class(), "must be boot class");

  oop m = k->java_mirror();

  KlassSubGraphInfo* subgraph_info = get_subgraph_info(k);
  oop f = m->obj_field(field_offset);

  log_debug(cds, heap)("Start archiving from: %s::%s (" PTR_FORMAT ")", klass_name, field_name, p2i(f));

  if (!CompressedOops::is_null(f)) {
    if (log_is_enabled(Trace, cds, heap)) {
      LogTarget(Trace, cds, heap) log;
      LogStream out(log);
      f->print_on(&out);
    }

    bool success = archive_reachable_objects_from(1, subgraph_info, f);
    if (!success) {
      log_error(cds, heap)("Archiving failed %s::%s (some reachable objects cannot be archived)",
                           klass_name, field_name);
    } else {
      // Note: the field value is not preserved in the archived mirror.
      // Record the field as a new subGraph entry point. The recorded
      // information is restored from the archive at runtime.
      subgraph_info->add_subgraph_entry_field(field_offset, f);
      log_info(cds, heap)("Archived field %s::%s => " PTR_FORMAT, klass_name, field_name, p2i(f));
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
    oop obj = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(obj)) {
      HeapShared::verify_reachable_objects_from(obj);
    }
  }
};

void HeapShared::verify_subgraph_from_static_field(InstanceKlass* k, int field_offset) {
  assert(CDSConfig::is_dumping_heap(), "dump time only");
  assert(k->is_shared_boot_class(), "must be boot class");

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
  if (CDSConfig::is_initing_classes_at_dump_time()) {
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
      Symbol* name = ArchiveBuilder::current()->get_source_addr(subgraph_k->name());
      if (subgraph_k->is_instance_klass() &&
          name != vmSymbols::java_lang_Class() &&
          name != vmSymbols::java_lang_String() &&
          name != vmSymbols::java_lang_ArithmeticException() &&
          name != vmSymbols::java_lang_ArrayIndexOutOfBoundsException() &&
          name != vmSymbols::java_lang_ArrayStoreException() &&
          name != vmSymbols::java_lang_ClassCastException() &&
          name != vmSymbols::java_lang_InternalError() &&
          name != vmSymbols::java_lang_NullPointerException()) {
        ResourceMark rm;
        fatal("special subgraph cannot have objects of type %s", subgraph_k->external_name());
      }
    }
  }
}

HeapShared::SeenObjectsTable* HeapShared::_seen_objects_table = nullptr;
int HeapShared::_num_new_walked_objs;
int HeapShared::_num_new_archived_objs;
int HeapShared::_num_old_recorded_klasses;

int HeapShared::_num_total_subgraph_recordings = 0;
int HeapShared::_num_total_walked_objs = 0;
int HeapShared::_num_total_archived_objs = 0;
int HeapShared::_num_total_recorded_klasses = 0;
int HeapShared::_num_total_verifications = 0;

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
  log_info(cds, heap)("Start recording subgraph(s) for archived fields in %s", class_name);
  init_subgraph_info(k, is_full_module_graph);
  init_seen_objects_table();
  _num_new_walked_objs = 0;
  _num_new_archived_objs = 0;
  _num_old_recorded_klasses = get_subgraph_info(k)->num_subgraph_object_klasses();
}

void HeapShared::done_recording_subgraph(InstanceKlass *k, const char* class_name) {
  int num_new_recorded_klasses = get_subgraph_info(k)->num_subgraph_object_klasses() -
    _num_old_recorded_klasses;
  log_info(cds, heap)("Done recording subgraph(s) for archived fields in %s: "
                      "walked %d objs, archived %d new objs, recorded %d classes",
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
      log_warning(cds)("Loading ArchiveHeapTestClass %s ...", test_class_name);
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
    assert(InstanceKlass::cast(ik)->is_shared_boot_class(),
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
      log_warning(cds)("Initializing ArchiveHeapTestClass %s ...", test_class_name);
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
  assert(HeapShared::can_write(), "must be");
  _dump_time_subgraph_info_table = new (mtClass)DumpTimeKlassSubGraphInfoTable();
  init_subgraph_entry_fields(archive_subgraph_entry_fields, CHECK);
  if (CDSConfig::is_dumping_full_module_graph()) {
    init_subgraph_entry_fields(fmg_archive_subgraph_entry_fields, CHECK);
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
  if (k != nullptr && ArchiveHeapLoader::is_in_use()) {
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
  if (HeapShared::can_write()) {
    setup_test_class(ArchiveHeapTestClass);
    _dumped_interned_strings = new (mtClass)DumpedInternedStrings(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE);
    init_subgraph_entry_fields(CHECK);
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

  log_info(cds, heap)("Archived subgraph records = %d",
                      _num_total_subgraph_recordings);
  log_info(cds, heap)("  Walked %d objects", _num_total_walked_objs);
  log_info(cds, heap)("  Archived %d objects", _num_total_archived_objs);
  log_info(cds, heap)("  Recorded %d klasses", _num_total_recorded_klasses);

#ifndef PRODUCT
  for (int i = 0; fields[i].valid(); i++) {
    ArchivableStaticFieldInfo* f = &fields[i];
    verify_subgraph_from_static_field(f->klass, f->offset);
  }
  log_info(cds, heap)("  Verified %d references", _num_total_verifications);
#endif
}

// Not all the strings in the global StringTable are dumped into the archive, because
// some of those strings may be only referenced by classes that are excluded from
// the archive. We need to explicitly mark the strings that are:
//   [1] used by classes that WILL be archived;
//   [2] included in the SharedArchiveConfigFile.
void HeapShared::add_to_dumped_interned_strings(oop string) {
  assert_at_safepoint(); // DumpedInternedStrings uses raw oops
  assert(!ArchiveHeapWriter::is_string_too_large_to_archive(string), "must be");
  bool created;
  _dumped_interned_strings->put_if_absent(string, true, &created);
  if (created) {
    _dumped_interned_strings->maybe_grow();
  }
}

void HeapShared::debug_trace() {
  ResourceMark rm;
  WalkOopAndArchiveClosure* walker = WalkOopAndArchiveClosure::current();
  if (walker != nullptr) {
    LogStream ls(Log(cds, heap)::error());
    CDSHeapVerifier::trace_to_root(&ls, walker->referencing_obj());
  }
}

#ifndef PRODUCT
// At dump-time, find the location of all the non-null oop pointers in an archived heap
// region. This way we can quickly relocate all the pointers without using
// BasicOopIterateClosure at runtime.
class FindEmbeddedNonNullPointers: public BasicOopIterateClosure {
  void* _start;
  BitMap *_oopmap;
  int _num_total_oops;
  int _num_null_oops;
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
  int num_total_oops() const { return _num_total_oops; }
  int num_null_oops()  const { return _num_null_oops; }
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
    log_info(cds, heap)(SIZE_FORMAT_W(8) " objects are <= " SIZE_FORMAT_W(-6)
                        " bytes (total " SIZE_FORMAT_W(8) " bytes, avg %8.1f bytes)",
                        count, byte_size_limit, size * HeapWordSize, avg_size(size, count));
    huge_count -= count;
    huge_size -= size;
  }

  log_info(cds, heap)(SIZE_FORMAT_W(8) " huge  objects               (total "  SIZE_FORMAT_W(8) " bytes"
                      ", avg %8.1f bytes)",
                      huge_count, huge_size * HeapWordSize,
                      avg_size(huge_size, huge_count));
  log_info(cds, heap)(SIZE_FORMAT_W(8) " total objects               (total "  SIZE_FORMAT_W(8) " bytes"
                      ", avg %8.1f bytes)",
                      _total_obj_count, _total_obj_size * HeapWordSize,
                      avg_size(_total_obj_size, _total_obj_count));
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

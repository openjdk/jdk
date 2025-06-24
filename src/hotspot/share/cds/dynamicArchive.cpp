/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassLinker.hpp"
#include "cds/aotClassLocation.hpp"
#include "cds/aotLogging.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/archiveUtils.inline.hpp"
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/lambdaFormInvokers.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "cds/metaspaceShared.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/gc_globals.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"


class DynamicArchiveBuilder : public ArchiveBuilder {
  const char* _archive_name;
public:
  DynamicArchiveBuilder(const char* archive_name) : _archive_name(archive_name) {}
  void mark_pointer(address* ptr_loc) {
    ArchivePtrMarker::mark_pointer(ptr_loc);
  }

  static int dynamic_dump_method_comparator(Method* a, Method* b) {
    Symbol* a_name = a->name();
    Symbol* b_name = b->name();

    if (a_name == b_name) {
      return 0;
    }

    u4 a_offset = ArchiveBuilder::current()->any_to_offset_u4(a_name);
    u4 b_offset = ArchiveBuilder::current()->any_to_offset_u4(b_name);

    if (a_offset < b_offset) {
      return -1;
    } else {
      assert(a_offset > b_offset, "must be");
      return 1;
    }
  }

public:
  DynamicArchiveHeader *_header;

  void init_header();
  void release_header();
  void post_dump();
  void sort_methods();
  void sort_methods(InstanceKlass* ik) const;
  void remark_pointers_for_instance_klass(InstanceKlass* k, bool should_mark) const;
  void write_archive(char* serialized_data, AOTClassLocationConfig* cl_config);
  void gather_array_klasses();

public:
  // Do this before and after the archive dump to see if any corruption
  // is caused by dynamic dumping.
  void verify_universe(const char* info) {
    if (VerifyBeforeExit) {
      log_info(aot)("Verify %s", info);
      // Among other things, this ensures that Eden top is correct.
      Universe::heap()->prepare_for_verify();
      Universe::verify(info);
    }
  }

  void doit() {
    verify_universe("Before CDS dynamic dump");
    DEBUG_ONLY(SystemDictionaryShared::NoClassLoadingMark nclm);

    // Block concurrent class unloading from changing the _dumptime_table
    MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);

    if (SystemDictionaryShared::is_dumptime_table_empty()) {
      log_warning(cds, dynamic)("There is no class to be included in the dynamic archive.");
      return;
    }

    log_info(cds, dynamic)("CDS dynamic dump: clinit = " JLONG_FORMAT "ms)",
                           ClassLoader::class_init_time_ms());

    init_header();
    gather_source_objs();
    gather_array_klasses();
    reserve_buffer();

    log_info(cds, dynamic)("Copying %d klasses and %d symbols",
                           klasses()->length(), symbols()->length());
    dump_rw_metadata();
    dump_ro_metadata();
    relocate_metaspaceobj_embedded_pointers();

    sort_methods();

    log_info(aot)("Make classes shareable");
    make_klasses_shareable();

    char* serialized_data;
    AOTClassLocationConfig* cl_config;
    {
      // Write the symbol table and system dictionaries to the RO space.
      // Note that these tables still point to the *original* objects, so
      // they would need to call DynamicArchive::original_to_target() to
      // get the correct addresses.
      assert(current_dump_region() == ro_region(), "Must be RO space");
      SymbolTable::write_to_archive(symbols());

      ArchiveBuilder::OtherROAllocMark mark;
      SystemDictionaryShared::write_to_archive(false);
      cl_config = AOTClassLocationConfig::dumptime()->write_to_archive();
      DynamicArchive::dump_array_klasses();
      AOTClassLinker::write_to_archive();

      serialized_data = ro_region()->top();
      WriteClosure wc(ro_region());
      ArchiveBuilder::serialize_dynamic_archivable_items(&wc);
    }

    if (CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
      log_info(aot)("Adjust lambda proxy class dictionary");
      LambdaProxyClassDictionary::adjust_dumptime_table();
    }

    relocate_to_requested();

    write_archive(serialized_data, cl_config);
    release_header();
    DynamicArchive::post_dump();

    post_dump();

    verify_universe("After CDS dynamic dump");
  }

  virtual void iterate_roots(MetaspaceClosure* it) {
    AOTArtifactFinder::all_cached_classes_do(it);
    SystemDictionaryShared::dumptime_classes_do(it);
    iterate_primitive_array_klasses(it);
  }

  void iterate_primitive_array_klasses(MetaspaceClosure* it) {
    for (int i = T_BOOLEAN; i <= T_LONG; i++) {
      assert(is_java_primitive((BasicType)i), "sanity");
      Klass* k = Universe::typeArrayKlass((BasicType)i);  // this give you "[I", etc
      assert(MetaspaceShared::is_shared_static((void*)k),
        "one-dimensional primitive array should be in static archive");
      ArrayKlass* ak = ArrayKlass::cast(k);
      while (ak != nullptr && ak->is_shared()) {
        Klass* next_k = ak->array_klass_or_null();
        if (next_k != nullptr) {
          ak = ArrayKlass::cast(next_k);
        } else {
          ak = nullptr;
        }
      }
      if (ak != nullptr) {
        assert(ak->dimension() > 1, "sanity");
        // this is the lowest dimension that's not in the static archive
        it->push(&ak);
      }
    }
  }
};

void DynamicArchiveBuilder::init_header() {
  FileMapInfo* mapinfo = new FileMapInfo(_archive_name, false);
  assert(FileMapInfo::dynamic_info() == mapinfo, "must be");
  FileMapInfo* base_info = FileMapInfo::current_info();
  // header only be available after populate_header
  mapinfo->populate_header(base_info->core_region_alignment());
  _header = mapinfo->dynamic_header();

  _header->set_base_header_crc(base_info->crc());
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    _header->set_base_region_crc(i, base_info->region_crc(i));
  }
}

void DynamicArchiveBuilder::release_header() {
  // We temporarily allocated a dynamic FileMapInfo for dumping, which makes it appear we
  // have mapped a dynamic archive, but we actually have not. We are in a safepoint now.
  // Let's free it so that if class loading happens after we leave the safepoint, nothing
  // bad will happen.
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  FileMapInfo *mapinfo = FileMapInfo::dynamic_info();
  assert(mapinfo != nullptr && _header == mapinfo->dynamic_header(), "must be");
  delete mapinfo;
  assert(!DynamicArchive::is_mapped(), "must be");
  _header = nullptr;
}

void DynamicArchiveBuilder::post_dump() {
  ArchivePtrMarker::reset_map_and_vs();
  AOTClassLinker::dispose();
}

void DynamicArchiveBuilder::sort_methods() {
  InstanceKlass::disable_method_binary_search();
  for (int i = 0; i < klasses()->length(); i++) {
    Klass* k = get_buffered_addr(klasses()->at(i));
    if (k->is_instance_klass()) {
      sort_methods(InstanceKlass::cast(k));
    }
  }
}

// The address order of the copied Symbols may be different than when the original
// klasses were created. Re-sort all the tables. See Method::sort_methods().
void DynamicArchiveBuilder::sort_methods(InstanceKlass* ik) const {
  assert(ik != nullptr, "DynamicArchiveBuilder currently doesn't support dumping the base archive");
  if (MetaspaceShared::is_in_shared_metaspace(ik)) {
    // We have reached a supertype that's already in the base archive
    return;
  }
  assert(is_in_buffer_space(ik), "method sorting must be done on buffered class, not original class");
  if (ik->java_mirror() == nullptr) {
    // null mirror means this class has already been visited and methods are already sorted
    return;
  }
  ik->remove_java_mirror();

  if (log_is_enabled(Debug, cds, dynamic)) {
    ResourceMark rm;
    log_debug(cds, dynamic)("sorting methods for " PTR_FORMAT " (" PTR_FORMAT ") %s",
                            p2i(ik), p2i(to_requested(ik)), ik->external_name());
  }

  // Method sorting may re-layout the [iv]tables, which would change the offset(s)
  // of the locations in an InstanceKlass that would contain pointers. Let's clear
  // all the existing pointer marking bits, and re-mark the pointers after sorting.
  remark_pointers_for_instance_klass(ik, false);

  // Make sure all supertypes have been sorted
  sort_methods(ik->java_super());
  Array<InstanceKlass*>* interfaces = ik->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    sort_methods(interfaces->at(i));
  }

#ifdef ASSERT
  if (ik->methods() != nullptr) {
    for (int m = 0; m < ik->methods()->length(); m++) {
      Symbol* name = ik->methods()->at(m)->name();
      assert(MetaspaceShared::is_in_shared_metaspace(name) || is_in_buffer_space(name), "must be");
    }
  }
  if (ik->default_methods() != nullptr) {
    for (int m = 0; m < ik->default_methods()->length(); m++) {
      Symbol* name = ik->default_methods()->at(m)->name();
      assert(MetaspaceShared::is_in_shared_metaspace(name) || is_in_buffer_space(name), "must be");
    }
  }
#endif

  Method::sort_methods(ik->methods(), /*set_idnums=*/true, dynamic_dump_method_comparator);
  if (ik->default_methods() != nullptr) {
    Method::sort_methods(ik->default_methods(), /*set_idnums=*/false, dynamic_dump_method_comparator);
  }
  if (ik->is_linked()) {
    // If the class has already been linked, we must relayout the i/v tables, whose order depends
    // on the method sorting order.
    // If the class is unlinked, we cannot layout the i/v tables yet. This is OK, as the
    // i/v tables will be initialized at runtime after bytecode verification.
    ik->vtable().initialize_vtable();
    ik->itable().initialize_itable();
  }

  // Set all the pointer marking bits after sorting.
  remark_pointers_for_instance_klass(ik, true);
}

template<bool should_mark>
class PointerRemarker: public MetaspaceClosure {
public:
  virtual bool do_ref(Ref* ref, bool read_only) {
    if (should_mark) {
      ArchivePtrMarker::mark_pointer(ref->addr());
    } else {
      ArchivePtrMarker::clear_pointer(ref->addr());
    }
    return false; // don't recurse
  }
};

void DynamicArchiveBuilder::remark_pointers_for_instance_klass(InstanceKlass* k, bool should_mark) const {
  if (should_mark) {
    PointerRemarker<true> marker;
    k->metaspace_pointers_do(&marker);
    marker.finish();
  } else {
    PointerRemarker<false> marker;
    k->metaspace_pointers_do(&marker);
    marker.finish();
  }
}

void DynamicArchiveBuilder::write_archive(char* serialized_data, AOTClassLocationConfig* cl_config) {
  _header->set_class_location_config(cl_config);
  _header->set_serialized_data(serialized_data);

  FileMapInfo* dynamic_info = FileMapInfo::dynamic_info();
  assert(dynamic_info != nullptr, "Sanity");

  dynamic_info->open_as_output();
  ArchiveHeapInfo no_heap_for_dynamic_dump;
  ArchiveBuilder::write_archive(dynamic_info, &no_heap_for_dynamic_dump);

  address base = _requested_dynamic_archive_bottom;
  address top  = _requested_dynamic_archive_top;
  size_t file_size = pointer_delta(top, base, sizeof(char));

  log_info(cds, dynamic)("Written dynamic archive " PTR_FORMAT " - " PTR_FORMAT
                         " [" UINT32_FORMAT " bytes header, %zu bytes total]",
                         p2i(base), p2i(top), _header->header_size(), file_size);

  log_info(cds, dynamic)("%d klasses; %d symbols", klasses()->length(), symbols()->length());
}

void DynamicArchiveBuilder::gather_array_klasses() {
  for (int i = 0; i < klasses()->length(); i++) {
    if (klasses()->at(i)->is_objArray_klass()) {
      ObjArrayKlass* oak = ObjArrayKlass::cast(klasses()->at(i));
      Klass* elem = oak->element_klass();
      if (MetaspaceShared::is_shared_static(elem)) {
        // Only capture the array klass whose element_klass is in the static archive.
        // During run time, setup (see DynamicArchive::setup_array_klasses()) is needed
        // so that the element_klass can find its array klasses from the dynamic archive.
        DynamicArchive::append_array_klass(oak);
      } else {
        // The element_klass and its array klasses are in the same archive.
        assert(!MetaspaceShared::is_shared_static(oak),
          "we should not gather klasses that are already in the static archive");
      }
    }
  }
  log_debug(aot)("Total array klasses gathered for dynamic archive: %d", DynamicArchive::num_array_klasses());
}

class VM_PopulateDynamicDumpSharedSpace: public VM_Heap_Sync_Operation {
  DynamicArchiveBuilder _builder;
public:
  VM_PopulateDynamicDumpSharedSpace(const char* archive_name)
  : VM_Heap_Sync_Operation(), _builder(archive_name) {}
  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit() {
    ResourceMark rm;
    if (AllowArchivingWithJavaAgent) {
      aot_log_warning(aot)("This %s was created with AllowArchivingWithJavaAgent. It should be used "
                       "for testing purposes only and should not be used in a production environment",
                       CDSConfig::type_of_archive_being_loaded());
    }
    AOTClassLocationConfig::dumptime_check_nonempty_dirs();
    _builder.doit();
  }
  ~VM_PopulateDynamicDumpSharedSpace() {
    RegeneratedClasses::cleanup();
  }
};

// _array_klasses and _dynamic_archive_array_klasses only hold the array klasses
// which have element klass in the static archive.
GrowableArray<ObjArrayKlass*>* DynamicArchive::_array_klasses = nullptr;
Array<ObjArrayKlass*>* DynamicArchive::_dynamic_archive_array_klasses = nullptr;

void DynamicArchive::append_array_klass(ObjArrayKlass* ak) {
  if (_array_klasses == nullptr) {
    _array_klasses = new (mtClassShared) GrowableArray<ObjArrayKlass*>(50, mtClassShared);
  }
  _array_klasses->append(ak);
}

void DynamicArchive::dump_array_klasses() {
  assert(CDSConfig::is_dumping_dynamic_archive(), "sanity");
  if (_array_klasses != nullptr) {
    ArchiveBuilder* builder = ArchiveBuilder::current();
    int num_array_klasses = _array_klasses->length();
    _dynamic_archive_array_klasses =
        ArchiveBuilder::new_ro_array<ObjArrayKlass*>(num_array_klasses);
    for (int i = 0; i < num_array_klasses; i++) {
      builder->write_pointer_in_buffer(_dynamic_archive_array_klasses->adr_at(i), _array_klasses->at(i));
    }
  }
}

void DynamicArchive::setup_array_klasses() {
  if (_dynamic_archive_array_klasses != nullptr) {
    for (int i = 0; i < _dynamic_archive_array_klasses->length(); i++) {
      ObjArrayKlass* oak = _dynamic_archive_array_klasses->at(i);
      assert(!oak->is_typeArray_klass(), "all type array classes must be in static archive");

      Klass* elm = oak->element_klass();
      assert(MetaspaceShared::is_shared_static((void*)elm), "must be");

      if (elm->is_instance_klass()) {
        assert(InstanceKlass::cast(elm)->array_klasses() == nullptr, "must be");
        InstanceKlass::cast(elm)->set_array_klasses(oak);
      } else {
        assert(elm->is_array_klass(), "sanity");
        assert(ArrayKlass::cast(elm)->higher_dimension() == nullptr, "must be");
        ArrayKlass::cast(elm)->set_higher_dimension(oak);
      }
    }
    log_debug(aot)("Total array klasses read from dynamic archive: %d", _dynamic_archive_array_klasses->length());
  }
}

void DynamicArchive::serialize_array_klasses(SerializeClosure* soc) {
  soc->do_ptr(&_dynamic_archive_array_klasses);
}

void DynamicArchive::make_array_klasses_shareable() {
  if (_array_klasses != nullptr) {
    int num_array_klasses = _array_klasses->length();
    for (int i = 0; i < num_array_klasses; i++) {
      ObjArrayKlass* k = ArchiveBuilder::current()->get_buffered_addr(_array_klasses->at(i));
      k->remove_unshareable_info();
    }
  }
}

void DynamicArchive::post_dump() {
  if (_array_klasses != nullptr) {
    delete _array_klasses;
    _array_klasses = nullptr;
  }
}

int DynamicArchive::num_array_klasses() {
  return _array_klasses != nullptr ? _array_klasses->length() : 0;
}

void DynamicArchive::dump_impl(bool jcmd_request, const char* archive_name, TRAPS) {
  MetaspaceShared::link_shared_classes(CHECK);
  if (!jcmd_request && CDSConfig::is_dumping_regenerated_lambdaform_invokers()) {
    LambdaFormInvokers::regenerate_holder_classes(CHECK);
  }

  VM_PopulateDynamicDumpSharedSpace op(archive_name);
  VMThread::execute(&op);
}

void DynamicArchive::dump_at_exit(JavaThread* current) {
  ExceptionMark em(current);
  ResourceMark rm(current);
  CDSConfig::DumperThreadMark dumper_thread_mark(current);

  const char* archive_name = CDSConfig::output_archive_path();
  if (!CDSConfig::is_dumping_dynamic_archive() || archive_name == nullptr) {
    return;
  }

  log_info(cds, dynamic)("Preparing for dynamic dump at exit in thread %s", current->name());

  JavaThread* THREAD = current; // For TRAPS processing related to link_shared_classes
  dump_impl(/*jcmd_request=*/false, archive_name, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    // One of the prepatory steps failed
    oop ex = current->pending_exception();
    aot_log_error(aot)("Dynamic dump has failed");
    aot_log_error(aot)("%s: %s", ex->klass()->external_name(),
                   java_lang_String::as_utf8_string(java_lang_Throwable::message(ex)));
    CLEAR_PENDING_EXCEPTION;
    CDSConfig::disable_dumping_dynamic_archive();  // Just for good measure
  }
}

// This is called by "jcmd VM.cds dynamic_dump"
void DynamicArchive::dump_for_jcmd(const char* archive_name, TRAPS) {
  CDSConfig::DumperThreadMark dumper_thread_mark(THREAD);
  assert(CDSConfig::is_using_archive() && RecordDynamicDumpInfo, "already checked in arguments.cpp");
  assert(ArchiveClassesAtExit == nullptr, "already checked in arguments.cpp");
  assert(CDSConfig::is_dumping_dynamic_archive(), "already checked by check_for_dynamic_dump() during VM startup");
  dump_impl(/*jcmd_request=*/true, archive_name, CHECK);
}

bool DynamicArchive::validate(FileMapInfo* dynamic_info) {
  assert(!dynamic_info->is_static(), "must be");
  // Check if the recorded base archive matches with the current one
  FileMapInfo* base_info = FileMapInfo::current_info();
  DynamicArchiveHeader* dynamic_header = dynamic_info->dynamic_header();

  // Check the header crc
  if (dynamic_header->base_header_crc() != base_info->crc()) {
    aot_log_warning(aot)("Dynamic archive cannot be used: static archive header checksum verification failed.");
    return false;
  }

  // Check each space's crc
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    if (dynamic_header->base_region_crc(i) != base_info->region_crc(i)) {
      aot_log_warning(aot)("Dynamic archive cannot be used: static archive region #%d checksum verification failed.", i);
      return false;
    }
  }

  return true;
}

void DynamicArchiveHeader::print(outputStream* st) {
  ResourceMark rm;

  st->print_cr("- base_header_crc:                0x%08x", base_header_crc());
  for (int i = 0; i < NUM_CDS_REGIONS; i++) {
    st->print_cr("- base_region_crc[%d]:             0x%08x", i, base_region_crc(i));
  }
}

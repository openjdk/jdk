/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/gc_globals.hpp"
#include "logging/log.hpp"
#include "memory/archiveBuilder.hpp"
#include "memory/archiveUtils.inline.hpp"
#include "memory/dynamicArchive.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"


class DynamicArchiveBuilder : public ArchiveBuilder {
public:

  static size_t reserve_alignment() {
    return os::vm_allocation_granularity();
  }

public:
  void mark_pointer(address* ptr_loc) {
    ArchivePtrMarker::mark_pointer(ptr_loc);
  }

  template <typename T> T get_dumped_addr(T obj) {
    return (T)ArchiveBuilder::get_dumped_addr((address)obj);
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
  void sort_methods();
  void sort_methods(InstanceKlass* ik) const;
  void remark_pointers_for_instance_klass(InstanceKlass* k, bool should_mark) const;
  void write_archive(char* serialized_data);

public:
  DynamicArchiveBuilder() : ArchiveBuilder(MetaspaceShared::misc_code_dump_space(),
                                           MetaspaceShared::read_write_dump_space(),
                                           MetaspaceShared::read_only_dump_space()) {
  }

  void start_dump_space(DumpRegion* next) {
    address bottom = _last_verified_top;
    address top = (address)(current_dump_space()->top());
    _other_region_used_bytes += size_t(top - bottom);

    MetaspaceShared::pack_dump_space(current_dump_space(), next, MetaspaceShared::shared_rs());
    _current_dump_space = next;
    _num_dump_regions_used ++;

    _last_verified_top = (address)(current_dump_space()->top());
  }

  void verify_estimate_size(size_t estimate, const char* which) {
    address bottom = _last_verified_top;
    address top = (address)(current_dump_space()->top());
    size_t used = size_t(top - bottom) + _other_region_used_bytes;
    int diff = int(estimate) - int(used);

    log_info(cds)("%s estimate = " SIZE_FORMAT " used = " SIZE_FORMAT "; diff = %d bytes", which, estimate, used, diff);
    assert(diff >= 0, "Estimate is too small");

    _last_verified_top = top;
    _other_region_used_bytes = 0;
  }

  // Do this before and after the archive dump to see if any corruption
  // is caused by dynamic dumping.
  void verify_universe(const char* info) {
    if (VerifyBeforeExit) {
      log_info(cds)("Verify %s", info);
      // Among other things, this ensures that Eden top is correct.
      Universe::heap()->prepare_for_verify();
      Universe::verify(info);
    }
  }

  void doit() {
    SystemDictionaryShared::start_dumping();

    verify_universe("Before CDS dynamic dump");
    DEBUG_ONLY(SystemDictionaryShared::NoClassLoadingMark nclm);
    SystemDictionaryShared::check_excluded_classes();

    gather_klasses_and_symbols();

    // mc space starts ...
    reserve_buffer();
    init_header();

    allocate_method_trampolines();
    verify_estimate_size(_estimated_trampoline_bytes, "Trampolines");

    gather_source_objs();
    // rw space starts ...
    start_dump_space(MetaspaceShared::read_write_dump_space());

    log_info(cds, dynamic)("Copying %d klasses and %d symbols",
                           klasses()->length(), symbols()->length());

    dump_rw_region();

    // ro space starts ...
    DumpRegion* ro_space = MetaspaceShared::read_only_dump_space();
    start_dump_space(ro_space);
    dump_ro_region();
    relocate_metaspaceobj_embedded_pointers();
    relocate_roots();

    verify_estimate_size(_estimated_metaspaceobj_bytes, "MetaspaceObjs");

    char* serialized_data;
    {
      // Write the symbol table and system dictionaries to the RO space.
      // Note that these tables still point to the *original* objects, so
      // they would need to call DynamicArchive::original_to_target() to
      // get the correct addresses.
      assert(current_dump_space() == ro_space, "Must be RO space");
      SymbolTable::write_to_archive(symbols());
      SystemDictionaryShared::write_to_archive(false);

      serialized_data = ro_space->top();
      WriteClosure wc(ro_space);
      SymbolTable::serialize_shared_table_header(&wc, false);
      SystemDictionaryShared::serialize_dictionary_headers(&wc, false);
    }

    verify_estimate_size(_estimated_hashtable_bytes, "Hashtables");

    update_method_trampolines();
    sort_methods();

    log_info(cds)("Make classes shareable");
    make_klasses_shareable();

    log_info(cds)("Adjust lambda proxy class dictionary");
    SystemDictionaryShared::adjust_lambda_proxy_class_dictionary();

    relocate_to_requested();

    write_archive(serialized_data);
    release_header();

    assert(_num_dump_regions_used == _total_dump_regions, "must be");
    verify_universe("After CDS dynamic dump");
  }

  virtual void iterate_roots(MetaspaceClosure* it, bool is_relocating_pointers) {
    FileMapInfo::metaspace_pointers_do(it);
    SystemDictionaryShared::dumptime_classes_do(it);
  }
};

void DynamicArchiveBuilder::init_header() {
  FileMapInfo* mapinfo = new FileMapInfo(false);
  assert(FileMapInfo::dynamic_info() == mapinfo, "must be");
  _header = mapinfo->dynamic_header();

  Thread* THREAD = Thread::current();
  FileMapInfo* base_info = FileMapInfo::current_info();
  _header->set_base_header_crc(base_info->crc());
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    _header->set_base_region_crc(i, base_info->space_crc(i));
  }
  _header->populate(base_info, os::vm_allocation_granularity());
}

void DynamicArchiveBuilder::release_header() {
  // We temporarily allocated a dynamic FileMapInfo for dumping, which makes it appear we
  // have mapped a dynamic archive, but we actually have not. We are in a safepoint now.
  // Let's free it so that if class loading happens after we leave the safepoint, nothing
  // bad will happen.
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  FileMapInfo *mapinfo = FileMapInfo::dynamic_info();
  assert(mapinfo != NULL && _header == mapinfo->dynamic_header(), "must be");
  delete mapinfo;
  assert(!DynamicArchive::is_mapped(), "must be");
  _header = NULL;
}

void DynamicArchiveBuilder::sort_methods() {
  InstanceKlass::disable_method_binary_search();
  for (int i = 0; i < klasses()->length(); i++) {
    Klass* k = klasses()->at(i);
    if (k->is_instance_klass()) {
      sort_methods(InstanceKlass::cast(k));
    }
  }
}

// The address order of the copied Symbols may be different than when the original
// klasses were created. Re-sort all the tables. See Method::sort_methods().
void DynamicArchiveBuilder::sort_methods(InstanceKlass* ik) const {
  assert(ik != NULL, "DynamicArchiveBuilder currently doesn't support dumping the base archive");
  if (MetaspaceShared::is_in_shared_metaspace(ik)) {
    // We have reached a supertype that's already in the base archive
    return;
  }

  if (ik->java_mirror() == NULL) {
    // NULL mirror means this class has already been visited and methods are already sorted
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
  if (ik->methods() != NULL) {
    for (int m = 0; m < ik->methods()->length(); m++) {
      Symbol* name = ik->methods()->at(m)->name();
      assert(MetaspaceShared::is_in_shared_metaspace(name) || is_in_buffer_space(name), "must be");
    }
  }
  if (ik->default_methods() != NULL) {
    for (int m = 0; m < ik->default_methods()->length(); m++) {
      Symbol* name = ik->default_methods()->at(m)->name();
      assert(MetaspaceShared::is_in_shared_metaspace(name) || is_in_buffer_space(name), "must be");
    }
  }
#endif

  Thread* THREAD = Thread::current();
  Method::sort_methods(ik->methods(), /*set_idnums=*/true, dynamic_dump_method_comparator);
  if (ik->default_methods() != NULL) {
    Method::sort_methods(ik->default_methods(), /*set_idnums=*/false, dynamic_dump_method_comparator);
  }
  ik->vtable().initialize_vtable(true, THREAD); assert(!HAS_PENDING_EXCEPTION, "cannot fail");
  ik->itable().initialize_itable(true, THREAD); assert(!HAS_PENDING_EXCEPTION, "cannot fail");

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

void DynamicArchiveBuilder::write_archive(char* serialized_data) {
  int num_klasses = klasses()->length();
  int num_symbols = symbols()->length();

  Array<u8>* table = FileMapInfo::saved_shared_path_table().table();
  SharedPathTable runtime_table(table, FileMapInfo::shared_path_table().size());
  _header->set_shared_path_table(runtime_table);
  _header->set_serialized_data(serialized_data);

  FileMapInfo* dynamic_info = FileMapInfo::dynamic_info();
  assert(dynamic_info != NULL, "Sanity");

  // Now write the archived data including the file offsets.
  const char* archive_name = Arguments::GetSharedDynamicArchivePath();
  dynamic_info->open_for_write(archive_name);
  size_t bitmap_size_in_bytes;
  char* bitmap = MetaspaceShared::write_core_archive_regions(dynamic_info, NULL, NULL, bitmap_size_in_bytes);
  dynamic_info->set_requested_base((char*)MetaspaceShared::requested_base_address());
  dynamic_info->set_header_crc(dynamic_info->compute_header_crc());
  dynamic_info->write_header();
  dynamic_info->close();

  write_cds_map_to_log(dynamic_info, NULL, NULL,
                       bitmap, bitmap_size_in_bytes);
  FREE_C_HEAP_ARRAY(char, bitmap);

  address base = _requested_dynamic_archive_bottom;
  address top  = _requested_dynamic_archive_top;
  size_t file_size = pointer_delta(top, base, sizeof(char));

  log_info(cds, dynamic)("Written dynamic archive " PTR_FORMAT " - " PTR_FORMAT
                         " [" SIZE_FORMAT " bytes header, " SIZE_FORMAT " bytes total]",
                         p2i(base), p2i(top), _header->header_size(), file_size);

  log_info(cds, dynamic)("%d klasses; %d symbols", num_klasses, num_symbols);
}

class VM_PopulateDynamicDumpSharedSpace: public VM_GC_Sync_Operation {
  DynamicArchiveBuilder* _builder;
public:
  VM_PopulateDynamicDumpSharedSpace(DynamicArchiveBuilder* builder) : VM_GC_Sync_Operation(), _builder(builder) {}
  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit() {
    ResourceMark rm;
    if (SystemDictionaryShared::empty_dumptime_table()) {
      log_warning(cds, dynamic)("There is no class to be included in the dynamic archive.");
      return;
    }
    if (AllowArchivingWithJavaAgent) {
      warning("This archive was created with AllowArchivingWithJavaAgent. It should be used "
              "for testing purposes only and should not be used in a production environment");
    }
    FileMapInfo::check_nonempty_dir_in_shared_path_table();

    _builder->doit();
  }
};


void DynamicArchive::dump() {
  if (Arguments::GetSharedDynamicArchivePath() == NULL) {
    log_warning(cds, dynamic)("SharedDynamicArchivePath is not specified");
    return;
  }

  DynamicArchiveBuilder builder;
  VM_PopulateDynamicDumpSharedSpace op(&builder);
  VMThread::execute(&op);
}

bool DynamicArchive::validate(FileMapInfo* dynamic_info) {
  assert(!dynamic_info->is_static(), "must be");
  // Check if the recorded base archive matches with the current one
  FileMapInfo* base_info = FileMapInfo::current_info();
  DynamicArchiveHeader* dynamic_header = dynamic_info->dynamic_header();

  // Check the header crc
  if (dynamic_header->base_header_crc() != base_info->crc()) {
    FileMapInfo::fail_continue("Dynamic archive cannot be used: static archive header checksum verification failed.");
    return false;
  }

  // Check each space's crc
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    if (dynamic_header->base_region_crc(i) != base_info->space_crc(i)) {
      FileMapInfo::fail_continue("Dynamic archive cannot be used: static archive region #%d checksum verification failed.", i);
      return false;
    }
  }

  return true;
}

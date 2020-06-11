/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classListParser.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/softRefPolicy.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodes.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "memory/archiveUtils.inline.hpp"
#include "memory/dynamicArchive.hpp"
#include "memory/filemap.hpp"
#include "memory/heapShared.inline.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/signature.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/ostream.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/hashtable.inline.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#endif

ReservedSpace MetaspaceShared::_shared_rs;
VirtualSpace MetaspaceShared::_shared_vs;
ReservedSpace MetaspaceShared::_symbol_rs;
VirtualSpace MetaspaceShared::_symbol_vs;
MetaspaceSharedStats MetaspaceShared::_stats;
bool MetaspaceShared::_has_error_classes;
bool MetaspaceShared::_archive_loading_failed = false;
bool MetaspaceShared::_remapped_readwrite = false;
address MetaspaceShared::_i2i_entry_code_buffers = NULL;
size_t MetaspaceShared::_i2i_entry_code_buffers_size = 0;
void* MetaspaceShared::_shared_metaspace_static_top = NULL;
intx MetaspaceShared::_relocation_delta;
char* MetaspaceShared::_requested_base_address;
bool MetaspaceShared::_use_optimized_module_handling = true;

// The CDS archive is divided into the following regions:
//     mc  - misc code (the method entry trampolines, c++ vtables)
//     rw  - read-write metadata
//     ro  - read-only metadata and read-only tables
//
//     ca0 - closed archive heap space #0
//     ca1 - closed archive heap space #1 (may be empty)
//     oa0 - open archive heap space #0
//     oa1 - open archive heap space #1 (may be empty)
//
// The mc, rw, and ro regions are linearly allocated, starting from
// SharedBaseAddress, in the order of mc->rw->ro. The size of these 3 regions
// are page-aligned, and there's no gap between any consecutive regions.
//
// These 3 regions are populated in the following steps:
// [1] All classes are loaded in MetaspaceShared::preload_classes(). All metadata are
//     temporarily allocated outside of the shared regions. Only the method entry
//     trampolines are written into the mc region.
// [2] C++ vtables are copied into the mc region.
// [3] ArchiveCompactor copies RW metadata into the rw region.
// [4] ArchiveCompactor copies RO metadata into the ro region.
// [5] SymbolTable, StringTable, SystemDictionary, and a few other read-only data
//     are copied into the ro region as read-only tables.
//
// The s0/s1 and oa0/oa1 regions are populated inside HeapShared::archive_java_heap_objects.
// Their layout is independent of the other 4 regions.

char* DumpRegion::expand_top_to(char* newtop) {
  assert(is_allocatable(), "must be initialized and not packed");
  assert(newtop >= _top, "must not grow backwards");
  if (newtop > _end) {
    MetaspaceShared::report_out_of_space(_name, newtop - _top);
    ShouldNotReachHere();
  }

  if (_rs == MetaspaceShared::shared_rs()) {
    uintx delta;
    if (DynamicDumpSharedSpaces) {
      delta = DynamicArchive::object_delta_uintx(newtop);
    } else {
      delta = MetaspaceShared::object_delta_uintx(newtop);
    }
    if (delta > MAX_SHARED_DELTA) {
      // This is just a sanity check and should not appear in any real world usage. This
      // happens only if you allocate more than 2GB of shared objects and would require
      // millions of shared classes.
      vm_exit_during_initialization("Out of memory in the CDS archive",
                                    "Please reduce the number of shared classes.");
    }
  }

  MetaspaceShared::commit_to(_rs, _vs, newtop);
  _top = newtop;
  return _top;
}

char* DumpRegion::allocate(size_t num_bytes, size_t alignment) {
  char* p = (char*)align_up(_top, alignment);
  char* newtop = p + align_up(num_bytes, alignment);
  expand_top_to(newtop);
  memset(p, 0, newtop - p);
  return p;
}

void DumpRegion::append_intptr_t(intptr_t n, bool need_to_mark) {
  assert(is_aligned(_top, sizeof(intptr_t)), "bad alignment");
  intptr_t *p = (intptr_t*)_top;
  char* newtop = _top + sizeof(intptr_t);
  expand_top_to(newtop);
  *p = n;
  if (need_to_mark) {
    ArchivePtrMarker::mark_pointer(p);
  }
}

void DumpRegion::print(size_t total_bytes) const {
  log_debug(cds)("%-3s space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [%5.1f%% used] at " INTPTR_FORMAT,
                 _name, used(), percent_of(used(), total_bytes), reserved(), percent_of(used(), reserved()),
                 p2i(_base + MetaspaceShared::final_delta()));
}

void DumpRegion::print_out_of_space_msg(const char* failing_region, size_t needed_bytes) {
  log_error(cds)("[%-8s] " PTR_FORMAT " - " PTR_FORMAT " capacity =%9d, allocated =%9d",
                 _name, p2i(_base), p2i(_top), int(_end - _base), int(_top - _base));
  if (strcmp(_name, failing_region) == 0) {
    log_error(cds)(" required = %d", int(needed_bytes));
  }
}

void DumpRegion::init(ReservedSpace* rs, VirtualSpace* vs) {
  _rs = rs;
  _vs = vs;
  // Start with 0 committed bytes. The memory will be committed as needed by
  // MetaspaceShared::commit_to().
  if (!_vs->initialize(*_rs, 0)) {
    fatal("Unable to allocate memory for shared space");
  }
  _base = _top = _rs->base();
  _end = _rs->end();
}

void DumpRegion::pack(DumpRegion* next) {
  assert(!is_packed(), "sanity");
  _end = (char*)align_up(_top, MetaspaceShared::reserved_space_alignment());
  _is_packed = true;
  if (next != NULL) {
    next->_rs = _rs;
    next->_vs = _vs;
    next->_base = next->_top = this->_end;
    next->_end = _rs->end();
  }
}

static DumpRegion _mc_region("mc"), _ro_region("ro"), _rw_region("rw"), _symbol_region("symbols");
static size_t _total_closed_archive_region_size = 0, _total_open_archive_region_size = 0;

void MetaspaceShared::init_shared_dump_space(DumpRegion* first_space) {
  first_space->init(&_shared_rs, &_shared_vs);
}

DumpRegion* MetaspaceShared::misc_code_dump_space() {
  return &_mc_region;
}

DumpRegion* MetaspaceShared::read_write_dump_space() {
  return &_rw_region;
}

DumpRegion* MetaspaceShared::read_only_dump_space() {
  return &_ro_region;
}

void MetaspaceShared::pack_dump_space(DumpRegion* current, DumpRegion* next,
                                      ReservedSpace* rs) {
  current->pack(next);
}

char* MetaspaceShared::symbol_space_alloc(size_t num_bytes) {
  return _symbol_region.allocate(num_bytes);
}

char* MetaspaceShared::misc_code_space_alloc(size_t num_bytes) {
  return _mc_region.allocate(num_bytes);
}

char* MetaspaceShared::read_only_space_alloc(size_t num_bytes) {
  return _ro_region.allocate(num_bytes);
}

size_t MetaspaceShared::reserved_space_alignment() { return os::vm_allocation_granularity(); }

static bool shared_base_valid(char* shared_base) {
#ifdef _LP64
  return CompressedKlassPointers::is_valid_base((address)shared_base);
#else
  return true;
#endif
}

static bool shared_base_too_high(char* shared_base, size_t cds_total) {
  if (SharedBaseAddress != 0 && shared_base < (char*)SharedBaseAddress) {
    // SharedBaseAddress is very high (e.g., 0xffffffffffffff00) so
    // align_up(SharedBaseAddress, MetaspaceShared::reserved_space_alignment()) has wrapped around.
    return true;
  }
  if (max_uintx - uintx(shared_base) < uintx(cds_total)) {
    // The end of the archive will wrap around
    return true;
  }

  return false;
}

static char* compute_shared_base(size_t cds_total) {
  char* shared_base = (char*)align_up((char*)SharedBaseAddress, MetaspaceShared::reserved_space_alignment());
  const char* err = NULL;
  if (shared_base_too_high(shared_base, cds_total)) {
    err = "too high";
  } else if (!shared_base_valid(shared_base)) {
    err = "invalid for this platform";
  }
  if (err) {
    log_warning(cds)("SharedBaseAddress (" INTPTR_FORMAT ") is %s. Reverted to " INTPTR_FORMAT,
                     p2i((void*)SharedBaseAddress), err,
                     p2i((void*)Arguments::default_SharedBaseAddress()));
    SharedBaseAddress = Arguments::default_SharedBaseAddress();
    shared_base = (char*)align_up((char*)SharedBaseAddress, MetaspaceShared::reserved_space_alignment());
  }
  assert(!shared_base_too_high(shared_base, cds_total) && shared_base_valid(shared_base), "Sanity");
  return shared_base;
}

void MetaspaceShared::initialize_dumptime_shared_and_meta_spaces() {
  assert(DumpSharedSpaces, "should be called for dump time only");

  const size_t reserve_alignment = MetaspaceShared::reserved_space_alignment();

#ifdef _LP64
  // On 64-bit VM we reserve a 4G range and, if UseCompressedClassPointers=1,
  //  will use that to house both the archives and the ccs. See below for
  //  details.
  const uint64_t UnscaledClassSpaceMax = (uint64_t(max_juint) + 1);
  const size_t cds_total = align_down(UnscaledClassSpaceMax, reserve_alignment);
#else
  // We don't support archives larger than 256MB on 32-bit due to limited
  //  virtual address space.
  size_t cds_total = align_down(256*M, reserve_alignment);
#endif

  char* shared_base = compute_shared_base(cds_total);
  _requested_base_address = shared_base;

  // Whether to use SharedBaseAddress as attach address.
  bool use_requested_base = true;

  if (shared_base == NULL) {
    use_requested_base = false;
  }

  if (ArchiveRelocationMode == 1) {
    log_info(cds)("ArchiveRelocationMode == 1: always allocate class space at an alternative address");
    use_requested_base = false;
  }

  // First try to reserve the space at the specified SharedBaseAddress.
  assert(!_shared_rs.is_reserved(), "must be");
  if (use_requested_base) {
    _shared_rs = ReservedSpace(cds_total, reserve_alignment,
                               false /* large */, (char*)shared_base);
    if (_shared_rs.is_reserved()) {
      assert(_shared_rs.base() == shared_base, "should match");
    } else {
      log_info(cds)("dumptime space reservation: failed to map at "
                    "SharedBaseAddress " PTR_FORMAT, p2i(shared_base));
    }
  }
  if (!_shared_rs.is_reserved()) {
    // Get a reserved space anywhere if attaching at the SharedBaseAddress
    //  fails:
    if (UseCompressedClassPointers) {
      // If we need to reserve class space as well, let the platform handle
      //  the reservation.
      LP64_ONLY(_shared_rs =
                Metaspace::reserve_address_space_for_compressed_classes(cds_total);)
      NOT_LP64(ShouldNotReachHere();)
    } else {
      // anywhere is fine.
      _shared_rs = ReservedSpace(cds_total, reserve_alignment,
                                 false /* large */, (char*)NULL);
    }
  }

  if (!_shared_rs.is_reserved()) {
    vm_exit_during_initialization("Unable to reserve memory for shared space",
                                  err_msg(SIZE_FORMAT " bytes.", cds_total));
  }

#ifdef _LP64

  if (UseCompressedClassPointers) {

    assert(CompressedKlassPointers::is_valid_base((address)_shared_rs.base()), "Sanity");

    // On 64-bit VM, if UseCompressedClassPointers=1, the compressed class space
    //  must be allocated near the cds such as that the compressed Klass pointer
    //  encoding can be used to en/decode pointers from both cds and ccs. Since
    //  Metaspace cannot do this (it knows nothing about cds), we do it for
    //  Metaspace here and pass it the space to use for ccs.
    //
    // We do this by reserving space for the ccs behind the archives. Note
    //  however that ccs follows a different alignment
    //  (Metaspace::reserve_alignment), so there may be a gap between ccs and
    //  cds.
    // We use a similar layout at runtime, see reserve_address_space_for_archives().
    //
    //                              +-- SharedBaseAddress (default = 0x800000000)
    //                              v
    // +-..---------+---------+ ... +----+----+----+--------+-----------------+
    // |    Heap    | Archive |     | MC | RW | RO | [gap]  |    class space  |
    // +-..---------+---------+ ... +----+----+----+--------+-----------------+
    // |<--   MaxHeapSize  -->|     |<-- UnscaledClassSpaceMax = 4GB -->|
    //
    // Note: ccs must follow the archives, and the archives must start at the
    //  encoding base. However, the exact placement of ccs does not matter as
    //  long as it it resides in the encoding range of CompressedKlassPointers
    //  and comes after the archive.
    //
    // We do this by splitting up the allocated 4G into 3G of archive space,
    //  followed by 1G for the ccs:
    // + The upper 1 GB is used as the "temporary compressed class space"
    //   -- preload_classes() will store Klasses into this space.
    // + The lower 3 GB is used for the archive -- when preload_classes()
    //   is done, ArchiveCompactor will copy the class metadata into this
    //   space, first the RW parts, then the RO parts.

    // Starting address of ccs must be aligned to Metaspace::reserve_alignment()...
    size_t class_space_size = align_down(_shared_rs.size() / 4, Metaspace::reserve_alignment());
    address class_space_start = (address)align_down(_shared_rs.end() - class_space_size, Metaspace::reserve_alignment());
    size_t archive_size = class_space_start - (address)_shared_rs.base();

    ReservedSpace tmp_class_space = _shared_rs.last_part(archive_size);
    _shared_rs = _shared_rs.first_part(archive_size);

    // ... as does the size of ccs.
    tmp_class_space = tmp_class_space.first_part(class_space_size);
    CompressedClassSpaceSize = class_space_size;

    // Let Metaspace initialize ccs
    Metaspace::initialize_class_space(tmp_class_space);

    // and set up CompressedKlassPointers encoding.
    CompressedKlassPointers::initialize((address)_shared_rs.base(), cds_total);

    log_info(cds)("narrow_klass_base = " PTR_FORMAT ", narrow_klass_shift = %d",
                  p2i(CompressedKlassPointers::base()), CompressedKlassPointers::shift());

    log_info(cds)("Allocated temporary class space: " SIZE_FORMAT " bytes at " PTR_FORMAT,
                  CompressedClassSpaceSize, p2i(tmp_class_space.base()));

    assert(_shared_rs.end() == tmp_class_space.base() &&
           is_aligned(_shared_rs.base(), MetaspaceShared::reserved_space_alignment()) &&
           is_aligned(tmp_class_space.base(), Metaspace::reserve_alignment()) &&
           is_aligned(tmp_class_space.size(), Metaspace::reserve_alignment()), "Sanity");
  }

#endif

  init_shared_dump_space(&_mc_region);
  SharedBaseAddress = (size_t)_shared_rs.base();
  log_info(cds)("Allocated shared space: " SIZE_FORMAT " bytes at " PTR_FORMAT,
                _shared_rs.size(), p2i(_shared_rs.base()));

  // We don't want any valid object to be at the very bottom of the archive.
  // See ArchivePtrMarker::mark_pointer().
  MetaspaceShared::misc_code_space_alloc(16);

  size_t symbol_rs_size = LP64_ONLY(3 * G) NOT_LP64(128 * M);
  _symbol_rs = ReservedSpace(symbol_rs_size);
  if (!_symbol_rs.is_reserved()) {
    vm_exit_during_initialization("Unable to reserve memory for symbols",
                                  err_msg(SIZE_FORMAT " bytes.", symbol_rs_size));
  }
  _symbol_region.init(&_symbol_rs, &_symbol_vs);
}

// Called by universe_post_init()
void MetaspaceShared::post_initialize(TRAPS) {
  if (UseSharedSpaces) {
    int size = FileMapInfo::get_number_of_shared_paths();
    if (size > 0) {
      SystemDictionaryShared::allocate_shared_data_arrays(size, THREAD);
      if (!DynamicDumpSharedSpaces) {
        FileMapInfo* info;
        if (FileMapInfo::dynamic_info() == NULL) {
          info = FileMapInfo::current_info();
        } else {
          info = FileMapInfo::dynamic_info();
        }
        ClassLoaderExt::init_paths_start_index(info->app_class_paths_start_index());
        ClassLoaderExt::init_app_module_paths_start_index(info->app_module_paths_start_index());
      }
    }
  }
}

static GrowableArray<Handle>* _extra_interned_strings = NULL;

void MetaspaceShared::read_extra_data(const char* filename, TRAPS) {
  _extra_interned_strings = new (ResourceObj::C_HEAP, mtInternal)GrowableArray<Handle>(10000, true);

  HashtableTextDump reader(filename);
  reader.check_version("VERSION: 1.0");

  while (reader.remain() > 0) {
    int utf8_length;
    int prefix_type = reader.scan_prefix(&utf8_length);
    ResourceMark rm(THREAD);
    if (utf8_length == 0x7fffffff) {
      // buf_len will overflown 32-bit value.
      vm_exit_during_initialization(err_msg("string length too large: %d", utf8_length));
    }
    int buf_len = utf8_length+1;
    char* utf8_buffer = NEW_RESOURCE_ARRAY(char, buf_len);
    reader.get_utf8(utf8_buffer, utf8_length);
    utf8_buffer[utf8_length] = '\0';

    if (prefix_type == HashtableTextDump::SymbolPrefix) {
      SymbolTable::new_permanent_symbol(utf8_buffer);
    } else{
      assert(prefix_type == HashtableTextDump::StringPrefix, "Sanity");
      oop s = StringTable::intern(utf8_buffer, THREAD);

      if (HAS_PENDING_EXCEPTION) {
        log_warning(cds, heap)("[line %d] extra interned string allocation failed; size too large: %d",
                               reader.last_line_no(), utf8_length);
        CLEAR_PENDING_EXCEPTION;
      } else {
#if INCLUDE_G1GC
        if (UseG1GC) {
          typeArrayOop body = java_lang_String::value(s);
          const HeapRegion* hr = G1CollectedHeap::heap()->heap_region_containing(body);
          if (hr->is_humongous()) {
            // Don't keep it alive, so it will be GC'ed before we dump the strings, in order
            // to maximize free heap space and minimize fragmentation.
            log_warning(cds, heap)("[line %d] extra interned string ignored; size too large: %d",
                                reader.last_line_no(), utf8_length);
            continue;
          }
        }
#endif
        // Interned strings are GC'ed if there are no references to it, so let's
        // add a reference to keep this string alive.
        assert(s != NULL, "must succeed");
        Handle h(THREAD, s);
        _extra_interned_strings->append(h);
      }
    }
  }
}

void MetaspaceShared::commit_to(ReservedSpace* rs, VirtualSpace* vs, char* newtop) {
  Arguments::assert_is_dumping_archive();
  char* base = rs->base();
  size_t need_committed_size = newtop - base;
  size_t has_committed_size = vs->committed_size();
  if (need_committed_size < has_committed_size) {
    return;
  }

  size_t min_bytes = need_committed_size - has_committed_size;
  size_t preferred_bytes = 1 * M;
  size_t uncommitted = vs->reserved_size() - has_committed_size;

  size_t commit =MAX2(min_bytes, preferred_bytes);
  commit = MIN2(commit, uncommitted);
  assert(commit <= uncommitted, "sanity");

  bool result = vs->expand_by(commit, false);
  if (rs == &_shared_rs) {
    ArchivePtrMarker::expand_ptr_end((address*)vs->high());
  }

  if (!result) {
    vm_exit_during_initialization(err_msg("Failed to expand shared space to " SIZE_FORMAT " bytes",
                                          need_committed_size));
  }

  assert(rs == &_shared_rs || rs == &_symbol_rs, "must be");
  const char* which = (rs == &_shared_rs) ? "shared" : "symbol";
  log_debug(cds)("Expanding %s spaces by " SIZE_FORMAT_W(7) " bytes [total " SIZE_FORMAT_W(9)  " bytes ending at %p]",
                 which, commit, vs->actual_committed_size(), vs->high());
}

void MetaspaceShared::initialize_ptr_marker(CHeapBitMap* ptrmap) {
  ArchivePtrMarker::initialize(ptrmap, (address*)_shared_vs.low(), (address*)_shared_vs.high());
}

// Read/write a data stream for restoring/preserving metadata pointers and
// miscellaneous data from/to the shared archive file.

void MetaspaceShared::serialize(SerializeClosure* soc) {
  int tag = 0;
  soc->do_tag(--tag);

  // Verify the sizes of various metadata in the system.
  soc->do_tag(sizeof(Method));
  soc->do_tag(sizeof(ConstMethod));
  soc->do_tag(arrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(ConstantPool));
  soc->do_tag(sizeof(ConstantPoolCache));
  soc->do_tag(objArrayOopDesc::base_offset_in_bytes());
  soc->do_tag(typeArrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(Symbol));

  // Dump/restore miscellaneous metadata.
  JavaClasses::serialize_offsets(soc);
  Universe::serialize(soc);
  soc->do_tag(--tag);

  // Dump/restore references to commonly used names and signatures.
  vmSymbols::serialize(soc);
  soc->do_tag(--tag);

  // Dump/restore the symbol/string/subgraph_info tables
  SymbolTable::serialize_shared_table_header(soc);
  StringTable::serialize_shared_table_header(soc);
  HeapShared::serialize_subgraph_info_table_header(soc);
  SystemDictionaryShared::serialize_dictionary_headers(soc);

  InstanceMirrorKlass::serialize_offsets(soc);

  // Dump/restore well known classes (pointers)
  SystemDictionaryShared::serialize_well_known_klasses(soc);
  soc->do_tag(--tag);

  serialize_cloned_cpp_vtptrs(soc);
  soc->do_tag(--tag);

  soc->do_tag(666);
}

address MetaspaceShared::i2i_entry_code_buffers(size_t total_size) {
  if (DumpSharedSpaces) {
    if (_i2i_entry_code_buffers == NULL) {
      _i2i_entry_code_buffers = (address)misc_code_space_alloc(total_size);
      _i2i_entry_code_buffers_size = total_size;
    }
  } else if (UseSharedSpaces) {
    assert(_i2i_entry_code_buffers != NULL, "must already been initialized");
  } else {
    return NULL;
  }

  assert(_i2i_entry_code_buffers_size == total_size, "must not change");
  return _i2i_entry_code_buffers;
}

uintx MetaspaceShared::object_delta_uintx(void* obj) {
  Arguments::assert_is_dumping_archive();
  if (DumpSharedSpaces) {
    assert(shared_rs()->contains(obj), "must be");
  } else {
    assert(is_in_shared_metaspace(obj) || DynamicArchive::is_in_target_space(obj), "must be");
  }
  address base_address = address(SharedBaseAddress);
  uintx deltax = address(obj) - base_address;
  return deltax;
}

// Global object for holding classes that have been loaded.  Since this
// is run at a safepoint just before exit, this is the entire set of classes.
static GrowableArray<Klass*>* _global_klass_objects;

static int global_klass_compare(Klass** a, Klass **b) {
  return a[0]->name()->fast_compare(b[0]->name());
}

GrowableArray<Klass*>* MetaspaceShared::collected_klasses() {
  return _global_klass_objects;
}

static void collect_array_classes(Klass* k) {
  _global_klass_objects->append_if_missing(k);
  if (k->is_array_klass()) {
    // Add in the array classes too
    ArrayKlass* ak = ArrayKlass::cast(k);
    Klass* h = ak->higher_dimension();
    if (h != NULL) {
      h->array_klasses_do(collect_array_classes);
    }
  }
}

class CollectClassesClosure : public KlassClosure {
  void do_klass(Klass* k) {
    if (k->is_instance_klass() &&
        SystemDictionaryShared::is_excluded_class(InstanceKlass::cast(k))) {
      // Don't add to the _global_klass_objects
    } else {
      _global_klass_objects->append_if_missing(k);
    }
    if (k->is_array_klass()) {
      // Add in the array classes too
      ArrayKlass* ak = ArrayKlass::cast(k);
      Klass* h = ak->higher_dimension();
      if (h != NULL) {
        h->array_klasses_do(collect_array_classes);
      }
    }
  }
};

static void remove_unshareable_in_classes() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (!k->is_objArray_klass()) {
      // InstanceKlass and TypeArrayKlass will in turn call remove_unshareable_info
      // on their array classes.
      assert(k->is_instance_klass() || k->is_typeArray_klass(), "must be");
      k->remove_unshareable_info();
    }
  }
}

static void remove_java_mirror_in_classes() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (!k->is_objArray_klass()) {
      // InstanceKlass and TypeArrayKlass will in turn call remove_unshareable_info
      // on their array classes.
      assert(k->is_instance_klass() || k->is_typeArray_klass(), "must be");
      k->remove_java_mirror();
    }
  }
}

static void clear_basic_type_mirrors() {
  assert(!HeapShared::is_heap_object_archiving_allowed(), "Sanity");
  Universe::set_int_mirror(NULL);
  Universe::set_float_mirror(NULL);
  Universe::set_double_mirror(NULL);
  Universe::set_byte_mirror(NULL);
  Universe::set_bool_mirror(NULL);
  Universe::set_char_mirror(NULL);
  Universe::set_long_mirror(NULL);
  Universe::set_short_mirror(NULL);
  Universe::set_void_mirror(NULL);
}

static void rewrite_nofast_bytecode(const methodHandle& method) {
  BytecodeStream bcs(method);
  while (!bcs.is_last_bytecode()) {
    Bytecodes::Code opcode = bcs.next();
    switch (opcode) {
    case Bytecodes::_getfield:      *bcs.bcp() = Bytecodes::_nofast_getfield;      break;
    case Bytecodes::_putfield:      *bcs.bcp() = Bytecodes::_nofast_putfield;      break;
    case Bytecodes::_aload_0:       *bcs.bcp() = Bytecodes::_nofast_aload_0;       break;
    case Bytecodes::_iload: {
      if (!bcs.is_wide()) {
        *bcs.bcp() = Bytecodes::_nofast_iload;
      }
      break;
    }
    default: break;
    }
  }
}

// Walk all methods in the class list to ensure that they won't be modified at
// run time. This includes:
// [1] Rewrite all bytecodes as needed, so that the ConstMethod* will not be modified
//     at run time by RewriteBytecodes/RewriteFrequentPairs
// [2] Assign a fingerprint, so one doesn't need to be assigned at run-time.
static void rewrite_nofast_bytecodes_and_calculate_fingerprints(Thread* thread) {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      MetaspaceShared::rewrite_nofast_bytecodes_and_calculate_fingerprints(thread, ik);
    }
  }
}

void MetaspaceShared::rewrite_nofast_bytecodes_and_calculate_fingerprints(Thread* thread, InstanceKlass* ik) {
  for (int i = 0; i < ik->methods()->length(); i++) {
    methodHandle m(thread, ik->methods()->at(i));
    rewrite_nofast_bytecode(m);
    Fingerprinter fp(m);
    // The side effect of this call sets method's fingerprint field.
    fp.fingerprint();
  }
}

// Objects of the Metadata types (such as Klass and ConstantPool) have C++ vtables.
// (In GCC this is the field <Type>::_vptr, i.e., first word in the object.)
//
// Addresses of the vtables and the methods may be different across JVM runs,
// if libjvm.so is dynamically loaded at a different base address.
//
// To ensure that the Metadata objects in the CDS archive always have the correct vtable:
//
// + at dump time:  we redirect the _vptr to point to our own vtables inside
//                  the CDS image
// + at run time:   we clone the actual contents of the vtables from libjvm.so
//                  into our own tables.

// Currently, the archive contain ONLY the following types of objects that have C++ vtables.
#define CPP_VTABLE_PATCH_TYPES_DO(f) \
  f(ConstantPool) \
  f(InstanceKlass) \
  f(InstanceClassLoaderKlass) \
  f(InstanceMirrorKlass) \
  f(InstanceRefKlass) \
  f(Method) \
  f(ObjArrayKlass) \
  f(TypeArrayKlass)

class CppVtableInfo {
  intptr_t _vtable_size;
  intptr_t _cloned_vtable[1];
public:
  static int num_slots(int vtable_size) {
    return 1 + vtable_size; // Need to add the space occupied by _vtable_size;
  }
  int vtable_size()           { return int(uintx(_vtable_size)); }
  void set_vtable_size(int n) { _vtable_size = intptr_t(n); }
  intptr_t* cloned_vtable()   { return &_cloned_vtable[0]; }
  void zero()                 { memset(_cloned_vtable, 0, sizeof(intptr_t) * vtable_size()); }
  // Returns the address of the next CppVtableInfo that can be placed immediately after this CppVtableInfo
  static size_t byte_size(int vtable_size) {
    CppVtableInfo i;
    return pointer_delta(&i._cloned_vtable[vtable_size], &i, sizeof(u1));
  }
};

template <class T> class CppVtableCloner : public T {
  static intptr_t* vtable_of(Metadata& m) {
    return *((intptr_t**)&m);
  }
  static CppVtableInfo* _info;

  static int get_vtable_length(const char* name);

public:
  // Allocate and initialize the C++ vtable, starting from top, but do not go past end.
  static intptr_t* allocate(const char* name);

  // Clone the vtable to ...
  static intptr_t* clone_vtable(const char* name, CppVtableInfo* info);

  static void zero_vtable_clone() {
    assert(DumpSharedSpaces, "dump-time only");
    _info->zero();
  }

  static bool is_valid_shared_object(const T* obj) {
    intptr_t* vptr = *(intptr_t**)obj;
    return vptr == _info->cloned_vtable();
  }
};

template <class T> CppVtableInfo* CppVtableCloner<T>::_info = NULL;

template <class T>
intptr_t* CppVtableCloner<T>::allocate(const char* name) {
  assert(is_aligned(_mc_region.top(), sizeof(intptr_t)), "bad alignment");
  int n = get_vtable_length(name);
  _info = (CppVtableInfo*)_mc_region.allocate(CppVtableInfo::byte_size(n), sizeof(intptr_t));
  _info->set_vtable_size(n);

  intptr_t* p = clone_vtable(name, _info);
  assert((char*)p == _mc_region.top(), "must be");

  return _info->cloned_vtable();
}

template <class T>
intptr_t* CppVtableCloner<T>::clone_vtable(const char* name, CppVtableInfo* info) {
  if (!DumpSharedSpaces) {
    assert(_info == 0, "_info is initialized only at dump time");
    _info = info; // Remember it -- it will be used by MetaspaceShared::is_valid_shared_method()
  }
  T tmp; // Allocate temporary dummy metadata object to get to the original vtable.
  int n = info->vtable_size();
  intptr_t* srcvtable = vtable_of(tmp);
  intptr_t* dstvtable = info->cloned_vtable();

  // We already checked (and, if necessary, adjusted n) when the vtables were allocated, so we are
  // safe to do memcpy.
  log_debug(cds, vtables)("Copying %3d vtable entries for %s", n, name);
  memcpy(dstvtable, srcvtable, sizeof(intptr_t) * n);
  return dstvtable + n;
}

// To determine the size of the vtable for each type, we use the following
// trick by declaring 2 subclasses:
//
//   class CppVtableTesterA: public InstanceKlass {virtual int   last_virtual_method() {return 1;}    };
//   class CppVtableTesterB: public InstanceKlass {virtual void* last_virtual_method() {return NULL}; };
//
// CppVtableTesterA and CppVtableTesterB's vtables have the following properties:
// - Their size (N+1) is exactly one more than the size of InstanceKlass's vtable (N)
// - The first N entries have are exactly the same as in InstanceKlass's vtable.
// - Their last entry is different.
//
// So to determine the value of N, we just walk CppVtableTesterA and CppVtableTesterB's tables
// and find the first entry that's different.
//
// This works on all C++ compilers supported by Oracle, but you may need to tweak it for more
// esoteric compilers.

template <class T> class CppVtableTesterB: public T {
public:
  virtual int last_virtual_method() {return 1;}
};

template <class T> class CppVtableTesterA : public T {
public:
  virtual void* last_virtual_method() {
    // Make this different than CppVtableTesterB::last_virtual_method so the C++
    // compiler/linker won't alias the two functions.
    return NULL;
  }
};

template <class T>
int CppVtableCloner<T>::get_vtable_length(const char* name) {
  CppVtableTesterA<T> a;
  CppVtableTesterB<T> b;

  intptr_t* avtable = vtable_of(a);
  intptr_t* bvtable = vtable_of(b);

  // Start at slot 1, because slot 0 may be RTTI (on Solaris/Sparc)
  int vtable_len = 1;
  for (; ; vtable_len++) {
    if (avtable[vtable_len] != bvtable[vtable_len]) {
      break;
    }
  }
  log_debug(cds, vtables)("Found   %3d vtable entries for %s", vtable_len, name);

  return vtable_len;
}

#define ALLOC_CPP_VTABLE_CLONE(c) \
  _cloned_cpp_vtptrs[c##_Kind] = CppVtableCloner<c>::allocate(#c); \
  ArchivePtrMarker::mark_pointer(&_cloned_cpp_vtptrs[c##_Kind]);

#define CLONE_CPP_VTABLE(c) \
  p = CppVtableCloner<c>::clone_vtable(#c, (CppVtableInfo*)p);

#define ZERO_CPP_VTABLE(c) \
 CppVtableCloner<c>::zero_vtable_clone();

//------------------------------ for DynamicDumpSharedSpaces - start
#define DECLARE_CLONED_VTABLE_KIND(c) c ## _Kind,

enum {
  // E.g., ConstantPool_Kind == 0, InstanceKlass == 1, etc.
  CPP_VTABLE_PATCH_TYPES_DO(DECLARE_CLONED_VTABLE_KIND)
  _num_cloned_vtable_kinds
};

// This is the index of all the cloned vtables. E.g., for
//     ConstantPool* cp = ....; // an archived constant pool
//     InstanceKlass* ik = ....;// an archived class
// the following holds true:
//     _cloned_cpp_vtptrs[ConstantPool_Kind]  == ((intptr_t**)cp)[0]
//     _cloned_cpp_vtptrs[InstanceKlass_Kind] == ((intptr_t**)ik)[0]
static intptr_t** _cloned_cpp_vtptrs = NULL;

void MetaspaceShared::allocate_cloned_cpp_vtptrs() {
  assert(DumpSharedSpaces, "must");
  size_t vtptrs_bytes = _num_cloned_vtable_kinds * sizeof(intptr_t*);
  _cloned_cpp_vtptrs = (intptr_t**)_mc_region.allocate(vtptrs_bytes, sizeof(intptr_t*));
}

void MetaspaceShared::serialize_cloned_cpp_vtptrs(SerializeClosure* soc) {
  soc->do_ptr((void**)&_cloned_cpp_vtptrs);
}

intptr_t* MetaspaceShared::fix_cpp_vtable_for_dynamic_archive(MetaspaceObj::Type msotype, address obj) {
  Arguments::assert_is_dumping_archive();
  int kind = -1;
  switch (msotype) {
  case MetaspaceObj::SymbolType:
  case MetaspaceObj::TypeArrayU1Type:
  case MetaspaceObj::TypeArrayU2Type:
  case MetaspaceObj::TypeArrayU4Type:
  case MetaspaceObj::TypeArrayU8Type:
  case MetaspaceObj::TypeArrayOtherType:
  case MetaspaceObj::ConstMethodType:
  case MetaspaceObj::ConstantPoolCacheType:
  case MetaspaceObj::AnnotationsType:
  case MetaspaceObj::MethodCountersType:
  case MetaspaceObj::RecordComponentType:
    // These have no vtables.
    break;
  case MetaspaceObj::ClassType:
    {
      Klass* k = (Klass*)obj;
      assert(k->is_klass(), "must be");
      if (k->is_instance_klass()) {
        InstanceKlass* ik = InstanceKlass::cast(k);
        if (ik->is_class_loader_instance_klass()) {
          kind = InstanceClassLoaderKlass_Kind;
        } else if (ik->is_reference_instance_klass()) {
          kind = InstanceRefKlass_Kind;
        } else if (ik->is_mirror_instance_klass()) {
          kind = InstanceMirrorKlass_Kind;
        } else {
          kind = InstanceKlass_Kind;
        }
      } else if (k->is_typeArray_klass()) {
        kind = TypeArrayKlass_Kind;
      } else {
        assert(k->is_objArray_klass(), "must be");
        kind = ObjArrayKlass_Kind;
      }
    }
    break;

  case MetaspaceObj::MethodType:
    {
      Method* m = (Method*)obj;
      assert(m->is_method(), "must be");
      kind = Method_Kind;
    }
    break;

  case MetaspaceObj::MethodDataType:
    // We don't archive MethodData <-- should have been removed in removed_unsharable_info
    ShouldNotReachHere();
    break;

  case MetaspaceObj::ConstantPoolType:
    {
      ConstantPool *cp = (ConstantPool*)obj;
      assert(cp->is_constantPool(), "must be");
      kind = ConstantPool_Kind;
    }
    break;

  default:
    ShouldNotReachHere();
  }

  if (kind >= 0) {
    assert(kind < _num_cloned_vtable_kinds, "must be");
    return _cloned_cpp_vtptrs[kind];
  } else {
    return NULL;
  }
}

//------------------------------ for DynamicDumpSharedSpaces - end

// This can be called at both dump time and run time:
// - clone the contents of the c++ vtables into the space
//   allocated by allocate_cpp_vtable_clones()
void MetaspaceShared::clone_cpp_vtables(intptr_t* p) {
  assert(DumpSharedSpaces || UseSharedSpaces, "sanity");
  CPP_VTABLE_PATCH_TYPES_DO(CLONE_CPP_VTABLE);
}

void MetaspaceShared::zero_cpp_vtable_clones_for_writing() {
  assert(DumpSharedSpaces, "dump-time only");
  CPP_VTABLE_PATCH_TYPES_DO(ZERO_CPP_VTABLE);
}

// Allocate and initialize the C++ vtables, starting from top, but do not go past end.
char* MetaspaceShared::allocate_cpp_vtable_clones() {
  char* cloned_vtables = _mc_region.top(); // This is the beginning of all the cloned vtables

  assert(DumpSharedSpaces, "dump-time only");
  // Layout (each slot is a intptr_t):
  //   [number of slots in the first vtable = n1]
  //   [ <n1> slots for the first vtable]
  //   [number of slots in the first second = n2]
  //   [ <n2> slots for the second vtable]
  //   ...
  // The order of the vtables is the same as the CPP_VTAB_PATCH_TYPES_DO macro.
  CPP_VTABLE_PATCH_TYPES_DO(ALLOC_CPP_VTABLE_CLONE);

  return cloned_vtables;
}

bool MetaspaceShared::is_valid_shared_method(const Method* m) {
  assert(is_in_shared_metaspace(m), "must be");
  return CppVtableCloner<Method>::is_valid_shared_object(m);
}

void WriteClosure::do_oop(oop* o) {
  if (*o == NULL) {
    _dump_region->append_intptr_t(0);
  } else {
    assert(HeapShared::is_heap_object_archiving_allowed(),
           "Archiving heap object is not allowed");
    _dump_region->append_intptr_t(
      (intptr_t)CompressedOops::encode_not_null(*o));
  }
}

void WriteClosure::do_region(u_char* start, size_t size) {
  assert((intptr_t)start % sizeof(intptr_t) == 0, "bad alignment");
  assert(size % sizeof(intptr_t) == 0, "bad size");
  do_tag((int)size);
  while (size > 0) {
    _dump_region->append_intptr_t(*(intptr_t*)start, true);
    start += sizeof(intptr_t);
    size -= sizeof(intptr_t);
  }
}

// This is for dumping detailed statistics for the allocations
// in the shared spaces.
class DumpAllocStats : public ResourceObj {
public:

  // Here's poor man's enum inheritance
#define SHAREDSPACE_OBJ_TYPES_DO(f) \
  METASPACE_OBJ_TYPES_DO(f) \
  f(SymbolHashentry) \
  f(SymbolBucket) \
  f(StringHashentry) \
  f(StringBucket) \
  f(Other)

  enum Type {
    // Types are MetaspaceObj::ClassType, MetaspaceObj::SymbolType, etc
    SHAREDSPACE_OBJ_TYPES_DO(METASPACE_OBJ_TYPE_DECLARE)
    _number_of_types
  };

  static const char * type_name(Type type) {
    switch(type) {
    SHAREDSPACE_OBJ_TYPES_DO(METASPACE_OBJ_TYPE_NAME_CASE)
    default:
      ShouldNotReachHere();
      return NULL;
    }
  }

public:
  enum { RO = 0, RW = 1 };

  int _counts[2][_number_of_types];
  int _bytes [2][_number_of_types];

  DumpAllocStats() {
    memset(_counts, 0, sizeof(_counts));
    memset(_bytes,  0, sizeof(_bytes));
  };

  void record(MetaspaceObj::Type type, int byte_size, bool read_only) {
    assert(int(type) >= 0 && type < MetaspaceObj::_number_of_types, "sanity");
    int which = (read_only) ? RO : RW;
    _counts[which][type] ++;
    _bytes [which][type] += byte_size;
  }

  void record_other_type(int byte_size, bool read_only) {
    int which = (read_only) ? RO : RW;
    _bytes [which][OtherType] += byte_size;
  }
  void print_stats(int ro_all, int rw_all, int mc_all);
};

void DumpAllocStats::print_stats(int ro_all, int rw_all, int mc_all) {
  // Calculate size of data that was not allocated by Metaspace::allocate()
  MetaspaceSharedStats *stats = MetaspaceShared::stats();

  // symbols
  _counts[RO][SymbolHashentryType] = stats->symbol.hashentry_count;
  _bytes [RO][SymbolHashentryType] = stats->symbol.hashentry_bytes;

  _counts[RO][SymbolBucketType] = stats->symbol.bucket_count;
  _bytes [RO][SymbolBucketType] = stats->symbol.bucket_bytes;

  // strings
  _counts[RO][StringHashentryType] = stats->string.hashentry_count;
  _bytes [RO][StringHashentryType] = stats->string.hashentry_bytes;

  _counts[RO][StringBucketType] = stats->string.bucket_count;
  _bytes [RO][StringBucketType] = stats->string.bucket_bytes;

  // TODO: count things like dictionary, vtable, etc
  _bytes[RW][OtherType] += mc_all;
  rw_all += mc_all; // mc is mapped Read/Write

  // prevent divide-by-zero
  if (ro_all < 1) {
    ro_all = 1;
  }
  if (rw_all < 1) {
    rw_all = 1;
  }

  int all_ro_count = 0;
  int all_ro_bytes = 0;
  int all_rw_count = 0;
  int all_rw_bytes = 0;

// To make fmt_stats be a syntactic constant (for format warnings), use #define.
#define fmt_stats "%-20s: %8d %10d %5.1f | %8d %10d %5.1f | %8d %10d %5.1f"
  const char *sep = "--------------------+---------------------------+---------------------------+--------------------------";
  const char *hdr = "                        ro_cnt   ro_bytes     % |   rw_cnt   rw_bytes     % |  all_cnt  all_bytes     %";

  LogMessage(cds) msg;

  msg.debug("Detailed metadata info (excluding st regions; rw stats include mc regions):");
  msg.debug("%s", hdr);
  msg.debug("%s", sep);
  for (int type = 0; type < int(_number_of_types); type ++) {
    const char *name = type_name((Type)type);
    int ro_count = _counts[RO][type];
    int ro_bytes = _bytes [RO][type];
    int rw_count = _counts[RW][type];
    int rw_bytes = _bytes [RW][type];
    int count = ro_count + rw_count;
    int bytes = ro_bytes + rw_bytes;

    double ro_perc = percent_of(ro_bytes, ro_all);
    double rw_perc = percent_of(rw_bytes, rw_all);
    double perc    = percent_of(bytes, ro_all + rw_all);

    msg.debug(fmt_stats, name,
                         ro_count, ro_bytes, ro_perc,
                         rw_count, rw_bytes, rw_perc,
                         count, bytes, perc);

    all_ro_count += ro_count;
    all_ro_bytes += ro_bytes;
    all_rw_count += rw_count;
    all_rw_bytes += rw_bytes;
  }

  int all_count = all_ro_count + all_rw_count;
  int all_bytes = all_ro_bytes + all_rw_bytes;

  double all_ro_perc = percent_of(all_ro_bytes, ro_all);
  double all_rw_perc = percent_of(all_rw_bytes, rw_all);
  double all_perc    = percent_of(all_bytes, ro_all + rw_all);

  msg.debug("%s", sep);
  msg.debug(fmt_stats, "Total",
                       all_ro_count, all_ro_bytes, all_ro_perc,
                       all_rw_count, all_rw_bytes, all_rw_perc,
                       all_count, all_bytes, all_perc);

  assert(all_ro_bytes == ro_all, "everything should have been counted");
  assert(all_rw_bytes == rw_all, "everything should have been counted");

#undef fmt_stats
}

// Populate the shared space.

class VM_PopulateDumpSharedSpace: public VM_Operation {
private:
  GrowableArray<MemRegion> *_closed_archive_heap_regions;
  GrowableArray<MemRegion> *_open_archive_heap_regions;

  GrowableArray<ArchiveHeapOopmapInfo> *_closed_archive_heap_oopmaps;
  GrowableArray<ArchiveHeapOopmapInfo> *_open_archive_heap_oopmaps;

  void dump_java_heap_objects() NOT_CDS_JAVA_HEAP_RETURN;
  void dump_archive_heap_oopmaps() NOT_CDS_JAVA_HEAP_RETURN;
  void dump_archive_heap_oopmaps(GrowableArray<MemRegion>* regions,
                                 GrowableArray<ArchiveHeapOopmapInfo>* oopmaps);
  void dump_symbols();
  char* dump_read_only_tables();
  void print_class_stats();
  void print_region_stats(FileMapInfo* map_info);
  void print_bitmap_region_stats(size_t size, size_t total_size);
  void print_heap_region_stats(GrowableArray<MemRegion> *heap_mem,
                               const char *name, size_t total_size);
  void relocate_to_requested_base_address(CHeapBitMap* ptrmap);

public:

  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit();   // outline because gdb sucks
  bool allow_nested_vm_operations() const { return true; }
}; // class VM_PopulateDumpSharedSpace

class SortedSymbolClosure: public SymbolClosure {
  GrowableArray<Symbol*> _symbols;
  virtual void do_symbol(Symbol** sym) {
    assert((*sym)->is_permanent(), "archived symbols must be permanent");
    _symbols.append(*sym);
  }
  static int compare_symbols_by_address(Symbol** a, Symbol** b) {
    if (a[0] < b[0]) {
      return -1;
    } else if (a[0] == b[0]) {
      ResourceMark rm;
      log_warning(cds)("Duplicated symbol %s unexpected", (*a)->as_C_string());
      return 0;
    } else {
      return 1;
    }
  }

public:
  SortedSymbolClosure() {
    SymbolTable::symbols_do(this);
    _symbols.sort(compare_symbols_by_address);
  }
  GrowableArray<Symbol*>* get_sorted_symbols() {
    return &_symbols;
  }
};

// ArchiveCompactor --
//
// This class is the central piece of shared archive compaction -- all metaspace data are
// initially allocated outside of the shared regions. ArchiveCompactor copies the
// metaspace data into their final location in the shared regions.

class ArchiveCompactor : AllStatic {
  static const int INITIAL_TABLE_SIZE = 8087;
  static const int MAX_TABLE_SIZE     = 1000000;

  static DumpAllocStats* _alloc_stats;
  static SortedSymbolClosure* _ssc;

  typedef KVHashtable<address, address, mtInternal> RelocationTable;
  static RelocationTable* _new_loc_table;

public:
  static void initialize() {
    _alloc_stats = new(ResourceObj::C_HEAP, mtInternal)DumpAllocStats;
    _new_loc_table = new RelocationTable(INITIAL_TABLE_SIZE);
  }
  static DumpAllocStats* alloc_stats() {
    return _alloc_stats;
  }

  // Use this when you allocate space with MetaspaceShare::read_only_space_alloc()
  // outside of ArchiveCompactor::allocate(). These are usually for misc tables
  // that are allocated in the RO space.
  class OtherROAllocMark {
    char* _oldtop;
  public:
    OtherROAllocMark() {
      _oldtop = _ro_region.top();
    }
    ~OtherROAllocMark() {
      char* newtop = _ro_region.top();
      ArchiveCompactor::alloc_stats()->record_other_type(int(newtop - _oldtop), true);
    }
  };

  static void allocate(MetaspaceClosure::Ref* ref, bool read_only) {
    address obj = ref->obj();
    int bytes = ref->size() * BytesPerWord;
    char* p;
    size_t alignment = BytesPerWord;
    char* oldtop;
    char* newtop;

    if (read_only) {
      oldtop = _ro_region.top();
      p = _ro_region.allocate(bytes, alignment);
      newtop = _ro_region.top();
    } else {
      oldtop = _rw_region.top();
      if (ref->msotype() == MetaspaceObj::ClassType) {
        // Save a pointer immediate in front of an InstanceKlass, so
        // we can do a quick lookup from InstanceKlass* -> RunTimeSharedClassInfo*
        // without building another hashtable. See RunTimeSharedClassInfo::get_for()
        // in systemDictionaryShared.cpp.
        Klass* klass = (Klass*)obj;
        if (klass->is_instance_klass()) {
          SystemDictionaryShared::validate_before_archiving(InstanceKlass::cast(klass));
          _rw_region.allocate(sizeof(address), BytesPerWord);
        }
      }
      p = _rw_region.allocate(bytes, alignment);
      newtop = _rw_region.top();
    }
    memcpy(p, obj, bytes);

    intptr_t* cloned_vtable = MetaspaceShared::fix_cpp_vtable_for_dynamic_archive(ref->msotype(), (address)p);
    if (cloned_vtable != NULL) {
      *(address*)p = (address)cloned_vtable;
      ArchivePtrMarker::mark_pointer((address*)p);
    }

    assert(_new_loc_table->lookup(obj) == NULL, "each object can be relocated at most once");
    _new_loc_table->add(obj, (address)p);
    log_trace(cds)("Copy: " PTR_FORMAT " ==> " PTR_FORMAT " %d", p2i(obj), p2i(p), bytes);
    if (_new_loc_table->maybe_grow(MAX_TABLE_SIZE)) {
      log_info(cds, hashtables)("Expanded _new_loc_table to %d", _new_loc_table->table_size());
    }
    _alloc_stats->record(ref->msotype(), int(newtop - oldtop), read_only);
  }

  static address get_new_loc(MetaspaceClosure::Ref* ref) {
    address* pp = _new_loc_table->lookup(ref->obj());
    assert(pp != NULL, "must be");
    return *pp;
  }

private:
  // Makes a shallow copy of visited MetaspaceObj's
  class ShallowCopier: public UniqueMetaspaceClosure {
    bool _read_only;
  public:
    ShallowCopier(bool read_only) : _read_only(read_only) {}

    virtual bool do_unique_ref(Ref* ref, bool read_only) {
      if (read_only == _read_only) {
        allocate(ref, read_only);
      }
      return true; // recurse into ref.obj()
    }
  };

  // Relocate embedded pointers within a MetaspaceObj's shallow copy
  class ShallowCopyEmbeddedRefRelocator: public UniqueMetaspaceClosure {
  public:
    virtual bool do_unique_ref(Ref* ref, bool read_only) {
      address new_loc = get_new_loc(ref);
      RefRelocator refer;
      ref->metaspace_pointers_do_at(&refer, new_loc);
      return true; // recurse into ref.obj()
    }
    virtual void push_special(SpecialRef type, Ref* ref, intptr_t* p) {
      assert(type == _method_entry_ref, "only special type allowed for now");
      address obj = ref->obj();
      address new_obj = get_new_loc(ref);
      size_t offset = pointer_delta(p, obj,  sizeof(u1));
      intptr_t* new_p = (intptr_t*)(new_obj + offset);
      assert(*p == *new_p, "must be a copy");
      ArchivePtrMarker::mark_pointer((address*)new_p);
    }
  };

  // Relocate a reference to point to its shallow copy
  class RefRelocator: public MetaspaceClosure {
  public:
    virtual bool do_ref(Ref* ref, bool read_only) {
      if (ref->not_null()) {
        ref->update(get_new_loc(ref));
        ArchivePtrMarker::mark_pointer(ref->addr());
      }
      return false; // Do not recurse.
    }
  };

#ifdef ASSERT
  class IsRefInArchiveChecker: public MetaspaceClosure {
  public:
    virtual bool do_ref(Ref* ref, bool read_only) {
      if (ref->not_null()) {
        char* obj = (char*)ref->obj();
        assert(_ro_region.contains(obj) || _rw_region.contains(obj),
               "must be relocated to point to CDS archive");
      }
      return false; // Do not recurse.
    }
  };
#endif

public:
  static void copy_and_compact() {
    ResourceMark rm;
    SortedSymbolClosure the_ssc; // StackObj
    _ssc = &the_ssc;

    log_info(cds)("Scanning all metaspace objects ... ");
    {
      // allocate and shallow-copy RW objects, immediately following the MC region
      log_info(cds)("Allocating RW objects ... ");
      _mc_region.pack(&_rw_region);

      ResourceMark rm;
      ShallowCopier rw_copier(false);
      iterate_roots(&rw_copier);
    }
    {
      // allocate and shallow-copy of RO object, immediately following the RW region
      log_info(cds)("Allocating RO objects ... ");
      _rw_region.pack(&_ro_region);

      ResourceMark rm;
      ShallowCopier ro_copier(true);
      iterate_roots(&ro_copier);
    }
    {
      log_info(cds)("Relocating embedded pointers ... ");
      ResourceMark rm;
      ShallowCopyEmbeddedRefRelocator emb_reloc;
      iterate_roots(&emb_reloc);
    }
    {
      log_info(cds)("Relocating external roots ... ");
      ResourceMark rm;
      RefRelocator ext_reloc;
      iterate_roots(&ext_reloc);
    }
    {
      log_info(cds)("Fixing symbol identity hash ... ");
      os::init_random(0x12345678);
      GrowableArray<Symbol*>* symbols = _ssc->get_sorted_symbols();
      for (int i=0; i<symbols->length(); i++) {
        symbols->at(i)->update_identity_hash();
      }
    }
#ifdef ASSERT
    {
      log_info(cds)("Verifying external roots ... ");
      ResourceMark rm;
      IsRefInArchiveChecker checker;
      iterate_roots(&checker);
    }
#endif


    // cleanup
    _ssc = NULL;
  }

  // We must relocate the System::_well_known_klasses only after we have copied the
  // java objects in during dump_java_heap_objects(): during the object copy, we operate on
  // old objects which assert that their klass is the original klass.
  static void relocate_well_known_klasses() {
    {
      log_info(cds)("Relocating SystemDictionary::_well_known_klasses[] ... ");
      ResourceMark rm;
      RefRelocator ext_reloc;
      SystemDictionary::well_known_klasses_do(&ext_reloc);
    }
    // NOTE: after this point, we shouldn't have any globals that can reach the old
    // objects.

    // We cannot use any of the objects in the heap anymore (except for the
    // shared strings) because their headers no longer point to valid Klasses.
  }

  static void iterate_roots(MetaspaceClosure* it) {
    // To ensure deterministic contents in the archive, we just need to ensure that
    // we iterate the MetsapceObjs in a deterministic order. It doesn't matter where
    // the MetsapceObjs are located originally, as they are copied sequentially into
    // the archive during the iteration.
    //
    // The only issue here is that the symbol table and the system directories may be
    // randomly ordered, so we copy the symbols and klasses into two arrays and sort
    // them deterministically.
    //
    // During -Xshare:dump, the order of Symbol creation is strictly determined by
    // the SharedClassListFile (class loading is done in a single thread and the JIT
    // is disabled). Also, Symbols are allocated in monotonically increasing addresses
    // (see Symbol::operator new(size_t, int)). So if we iterate the Symbols by
    // ascending address order, we ensure that all Symbols are copied into deterministic
    // locations in the archive.
    GrowableArray<Symbol*>* symbols = _ssc->get_sorted_symbols();
    for (int i=0; i<symbols->length(); i++) {
      it->push(symbols->adr_at(i));
    }
    if (_global_klass_objects != NULL) {
      // Need to fix up the pointers
      for (int i = 0; i < _global_klass_objects->length(); i++) {
        // NOTE -- this requires that the vtable is NOT yet patched, or else we are hosed.
        it->push(_global_klass_objects->adr_at(i));
      }
    }
    FileMapInfo::metaspace_pointers_do(it, false);
    SystemDictionaryShared::dumptime_classes_do(it);
    Universe::metaspace_pointers_do(it);
    SymbolTable::metaspace_pointers_do(it);
    vmSymbols::metaspace_pointers_do(it);

    it->finish();
  }

  static Klass* get_relocated_klass(Klass* orig_klass) {
    assert(DumpSharedSpaces, "dump time only");
    address* pp = _new_loc_table->lookup((address)orig_klass);
    assert(pp != NULL, "must be");
    Klass* klass = (Klass*)(*pp);
    assert(klass->is_klass(), "must be");
    return klass;
  }
};

DumpAllocStats* ArchiveCompactor::_alloc_stats;
SortedSymbolClosure* ArchiveCompactor::_ssc;
ArchiveCompactor::RelocationTable* ArchiveCompactor::_new_loc_table;

void VM_PopulateDumpSharedSpace::dump_symbols() {
  log_info(cds)("Dumping symbol table ...");

  NOT_PRODUCT(SymbolTable::verify());
  SymbolTable::write_to_archive();
}

char* VM_PopulateDumpSharedSpace::dump_read_only_tables() {
  ArchiveCompactor::OtherROAllocMark mark;

  log_info(cds)("Removing java_mirror ... ");
  if (!HeapShared::is_heap_object_archiving_allowed()) {
    clear_basic_type_mirrors();
  }
  remove_java_mirror_in_classes();
  log_info(cds)("done. ");

  SystemDictionaryShared::write_to_archive();

  // Write the other data to the output array.
  char* start = _ro_region.top();
  WriteClosure wc(&_ro_region);
  MetaspaceShared::serialize(&wc);

  // Write the bitmaps for patching the archive heap regions
  _closed_archive_heap_oopmaps = NULL;
  _open_archive_heap_oopmaps = NULL;
  dump_archive_heap_oopmaps();

  return start;
}

void VM_PopulateDumpSharedSpace::print_class_stats() {
  log_info(cds)("Number of classes %d", _global_klass_objects->length());
  {
    int num_type_array = 0, num_obj_array = 0, num_inst = 0;
    for (int i = 0; i < _global_klass_objects->length(); i++) {
      Klass* k = _global_klass_objects->at(i);
      if (k->is_instance_klass()) {
        num_inst ++;
      } else if (k->is_objArray_klass()) {
        num_obj_array ++;
      } else {
        assert(k->is_typeArray_klass(), "sanity");
        num_type_array ++;
      }
    }
    log_info(cds)("    instance classes   = %5d", num_inst);
    log_info(cds)("    obj array classes  = %5d", num_obj_array);
    log_info(cds)("    type array classes = %5d", num_type_array);
  }
}

void VM_PopulateDumpSharedSpace::relocate_to_requested_base_address(CHeapBitMap* ptrmap) {
  intx addr_delta = MetaspaceShared::final_delta();
  if (addr_delta == 0) {
    ArchivePtrMarker::compact((address)SharedBaseAddress, (address)_ro_region.top());
  } else {
    // We are not able to reserve space at MetaspaceShared::requested_base_address() (due to ASLR).
    // This means that the current content of the archive is based on a random
    // address. Let's relocate all the pointers, so that it can be mapped to
    // MetaspaceShared::requested_base_address() without runtime relocation.
    //
    // Note: both the base and dynamic archive are written with
    // FileMapHeader::_requested_base_address == MetaspaceShared::requested_base_address()

    // Patch all pointers that are marked by ptrmap within this region,
    // where we have just dumped all the metaspace data.
    address patch_base = (address)SharedBaseAddress;
    address patch_end  = (address)_ro_region.top();
    size_t size = patch_end - patch_base;

    // the current value of the pointers to be patched must be within this
    // range (i.e., must point to valid metaspace objects)
    address valid_old_base = patch_base;
    address valid_old_end  = patch_end;

    // after patching, the pointers must point inside this range
    // (the requested location of the archive, as mapped at runtime).
    address valid_new_base = (address)MetaspaceShared::requested_base_address();
    address valid_new_end  = valid_new_base + size;

    log_debug(cds)("Relocating archive from [" INTPTR_FORMAT " - " INTPTR_FORMAT " ] to "
                   "[" INTPTR_FORMAT " - " INTPTR_FORMAT " ]", p2i(patch_base), p2i(patch_end),
                   p2i(valid_new_base), p2i(valid_new_end));

    SharedDataRelocator<true> patcher((address*)patch_base, (address*)patch_end, valid_old_base, valid_old_end,
                                      valid_new_base, valid_new_end, addr_delta, ptrmap);
    ptrmap->iterate(&patcher);
    ArchivePtrMarker::compact(patcher.max_non_null_offset());
  }
}

void VM_PopulateDumpSharedSpace::doit() {
  CHeapBitMap ptrmap;
  MetaspaceShared::initialize_ptr_marker(&ptrmap);

  // We should no longer allocate anything from the metaspace, so that:
  //
  // (1) Metaspace::allocate might trigger GC if we have run out of
  //     committed metaspace, but we can't GC because we're running
  //     in the VM thread.
  // (2) ArchiveCompactor needs to work with a stable set of MetaspaceObjs.
  Metaspace::freeze();
  DEBUG_ONLY(SystemDictionaryShared::NoClassLoadingMark nclm);

  Thread* THREAD = VMThread::vm_thread();

  FileMapInfo::check_nonempty_dir_in_shared_path_table();

  NOT_PRODUCT(SystemDictionary::verify();)
  // The following guarantee is meant to ensure that no loader constraints
  // exist yet, since the constraints table is not shared.  This becomes
  // more important now that we don't re-initialize vtables/itables for
  // shared classes at runtime, where constraints were previously created.
  guarantee(SystemDictionary::constraints()->number_of_entries() == 0,
            "loader constraints are not saved");
  guarantee(SystemDictionary::placeholders()->number_of_entries() == 0,
          "placeholders are not saved");

  // At this point, many classes have been loaded.
  // Gather systemDictionary classes in a global array and do everything to
  // that so we don't have to walk the SystemDictionary again.
  SystemDictionaryShared::check_excluded_classes();
  _global_klass_objects = new GrowableArray<Klass*>(1000);
  CollectClassesClosure collect_classes;
  ClassLoaderDataGraph::loaded_classes_do(&collect_classes);
  _global_klass_objects->sort(global_klass_compare);

  print_class_stats();

  // Ensure the ConstMethods won't be modified at run-time
  log_info(cds)("Updating ConstMethods ... ");
  rewrite_nofast_bytecodes_and_calculate_fingerprints(THREAD);
  log_info(cds)("done. ");

  // Remove all references outside the metadata
  log_info(cds)("Removing unshareable information ... ");
  remove_unshareable_in_classes();
  log_info(cds)("done. ");

  MetaspaceShared::allocate_cloned_cpp_vtptrs();
  char* cloned_vtables = _mc_region.top();
  MetaspaceShared::allocate_cpp_vtable_clones();

  ArchiveCompactor::initialize();
  ArchiveCompactor::copy_and_compact();

  dump_symbols();

  // Dump supported java heap objects
  _closed_archive_heap_regions = NULL;
  _open_archive_heap_regions = NULL;
  dump_java_heap_objects();

  ArchiveCompactor::relocate_well_known_klasses();

  char* serialized_data = dump_read_only_tables();
  _ro_region.pack();

  // The vtable clones contain addresses of the current process.
  // We don't want to write these addresses into the archive. Same for i2i buffer.
  MetaspaceShared::zero_cpp_vtable_clones_for_writing();
  memset(MetaspaceShared::i2i_entry_code_buffers(), 0,
         MetaspaceShared::i2i_entry_code_buffers_size());

  // relocate the data so that it can be mapped to MetaspaceShared::requested_base_address()
  // without runtime relocation.
  relocate_to_requested_base_address(&ptrmap);

  // Create and write the archive file that maps the shared spaces.

  FileMapInfo* mapinfo = new FileMapInfo(true);
  mapinfo->populate_header(os::vm_allocation_granularity());
  mapinfo->set_serialized_data(serialized_data);
  mapinfo->set_cloned_vtables(cloned_vtables);
  mapinfo->set_i2i_entry_code_buffers(MetaspaceShared::i2i_entry_code_buffers(),
                                      MetaspaceShared::i2i_entry_code_buffers_size());
  mapinfo->open_for_write();
  MetaspaceShared::write_core_archive_regions(mapinfo, _closed_archive_heap_oopmaps, _open_archive_heap_oopmaps);
  _total_closed_archive_region_size = mapinfo->write_archive_heap_regions(
                                        _closed_archive_heap_regions,
                                        _closed_archive_heap_oopmaps,
                                        MetaspaceShared::first_closed_archive_heap_region,
                                        MetaspaceShared::max_closed_archive_heap_region);
  _total_open_archive_region_size = mapinfo->write_archive_heap_regions(
                                        _open_archive_heap_regions,
                                        _open_archive_heap_oopmaps,
                                        MetaspaceShared::first_open_archive_heap_region,
                                        MetaspaceShared::max_open_archive_heap_region);

  mapinfo->set_final_requested_base((char*)MetaspaceShared::requested_base_address());
  mapinfo->set_header_crc(mapinfo->compute_header_crc());
  mapinfo->write_header();
  print_region_stats(mapinfo);
  mapinfo->close();

  if (log_is_enabled(Info, cds)) {
    ArchiveCompactor::alloc_stats()->print_stats(int(_ro_region.used()), int(_rw_region.used()),
                                                 int(_mc_region.used()));
  }

  if (PrintSystemDictionaryAtExit) {
    SystemDictionary::print();
  }

  if (AllowArchivingWithJavaAgent) {
    warning("This archive was created with AllowArchivingWithJavaAgent. It should be used "
            "for testing purposes only and should not be used in a production environment");
  }

  // There may be other pending VM operations that operate on the InstanceKlasses,
  // which will fail because InstanceKlasses::remove_unshareable_info()
  // has been called. Forget these operations and exit the VM directly.
  vm_direct_exit(0);
}

void VM_PopulateDumpSharedSpace::print_region_stats(FileMapInfo *map_info) {
  // Print statistics of all the regions
  const size_t bitmap_used = map_info->space_at(MetaspaceShared::bm)->used();
  const size_t bitmap_reserved = map_info->space_at(MetaspaceShared::bm)->used_aligned();
  const size_t total_reserved = _ro_region.reserved()  + _rw_region.reserved() +
                                _mc_region.reserved()  +
                                bitmap_reserved +
                                _total_closed_archive_region_size +
                                _total_open_archive_region_size;
  const size_t total_bytes = _ro_region.used()  + _rw_region.used() +
                             _mc_region.used()  +
                             bitmap_used +
                             _total_closed_archive_region_size +
                             _total_open_archive_region_size;
  const double total_u_perc = percent_of(total_bytes, total_reserved);

  _mc_region.print(total_reserved);
  _rw_region.print(total_reserved);
  _ro_region.print(total_reserved);
  print_bitmap_region_stats(bitmap_used, total_reserved);
  print_heap_region_stats(_closed_archive_heap_regions, "ca", total_reserved);
  print_heap_region_stats(_open_archive_heap_regions, "oa", total_reserved);

  log_debug(cds)("total    : " SIZE_FORMAT_W(9) " [100.0%% of total] out of " SIZE_FORMAT_W(9) " bytes [%5.1f%% used]",
                 total_bytes, total_reserved, total_u_perc);
}

void VM_PopulateDumpSharedSpace::print_bitmap_region_stats(size_t size, size_t total_size) {
  log_debug(cds)("bm  space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [100.0%% used]",
                 size, size/double(total_size)*100.0, size);
}

void VM_PopulateDumpSharedSpace::print_heap_region_stats(GrowableArray<MemRegion> *heap_mem,
                                                         const char *name, size_t total_size) {
  int arr_len = heap_mem == NULL ? 0 : heap_mem->length();
  for (int i = 0; i < arr_len; i++) {
      char* start = (char*)heap_mem->at(i).start();
      size_t size = heap_mem->at(i).byte_size();
      char* top = start + size;
      log_debug(cds)("%s%d space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [100.0%% used] at " INTPTR_FORMAT,
                     name, i, size, size/double(total_size)*100.0, size, p2i(start));

  }
}

void MetaspaceShared::write_core_archive_regions(FileMapInfo* mapinfo,
                                                 GrowableArray<ArchiveHeapOopmapInfo>* closed_oopmaps,
                                                 GrowableArray<ArchiveHeapOopmapInfo>* open_oopmaps) {
  // Make sure NUM_CDS_REGIONS (exported in cds.h) agrees with
  // MetaspaceShared::n_regions (internal to hotspot).
  assert(NUM_CDS_REGIONS == MetaspaceShared::n_regions, "sanity");

  // mc contains the trampoline code for method entries, which are patched at run time,
  // so it needs to be read/write.
  write_region(mapinfo, mc, &_mc_region, /*read_only=*/false,/*allow_exec=*/true);
  write_region(mapinfo, rw, &_rw_region, /*read_only=*/false,/*allow_exec=*/false);
  write_region(mapinfo, ro, &_ro_region, /*read_only=*/true, /*allow_exec=*/false);
  mapinfo->write_bitmap_region(ArchivePtrMarker::ptrmap(), closed_oopmaps, open_oopmaps);
}

void MetaspaceShared::write_region(FileMapInfo* mapinfo, int region_idx, DumpRegion* dump_region, bool read_only,  bool allow_exec) {
  mapinfo->write_region(region_idx, dump_region->base(), dump_region->used(), read_only, allow_exec);
}

// Update a Java object to point its Klass* to the new location after
// shared archive has been compacted.
void MetaspaceShared::relocate_klass_ptr(oop o) {
  assert(DumpSharedSpaces, "sanity");
  Klass* k = ArchiveCompactor::get_relocated_klass(o->klass());
  o->set_klass(k);
}

Klass* MetaspaceShared::get_relocated_klass(Klass *k, bool is_final) {
  assert(DumpSharedSpaces, "sanity");
  k = ArchiveCompactor::get_relocated_klass(k);
  if (is_final) {
    k = (Klass*)(address(k) + final_delta());
  }
  return k;
}

class LinkSharedClassesClosure : public KlassClosure {
  Thread* THREAD;
  bool    _made_progress;
 public:
  LinkSharedClassesClosure(Thread* thread) : THREAD(thread), _made_progress(false) {}

  void reset()               { _made_progress = false; }
  bool made_progress() const { return _made_progress; }

  void do_klass(Klass* k) {
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      // For dynamic CDS dump, only link classes loaded by the builtin class loaders.
      bool do_linking = DumpSharedSpaces ? true : !ik->is_shared_unregistered_class();
      if (do_linking) {
        // Link the class to cause the bytecodes to be rewritten and the
        // cpcache to be created. Class verification is done according
        // to -Xverify setting.
        _made_progress |= MetaspaceShared::try_link_class(ik, THREAD);
        guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");

        if (DumpSharedSpaces) {
          // The following function is used to resolve all Strings in the statically
          // dumped classes to archive all the Strings. The archive heap is not supported
          // for the dynamic archive.
          ik->constants()->resolve_class_constants(THREAD);
        }
      }
    }
  }
};

void MetaspaceShared::link_and_cleanup_shared_classes(TRAPS) {
  // We need to iterate because verification may cause additional classes
  // to be loaded.
  LinkSharedClassesClosure link_closure(THREAD);
  do {
    link_closure.reset();
    ClassLoaderDataGraph::unlocked_loaded_classes_do(&link_closure);
    guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");
  } while (link_closure.made_progress());
}

void MetaspaceShared::prepare_for_dumping() {
  Arguments::check_unsupported_dumping_properties();
  ClassLoader::initialize_shared_path();
}

// Preload classes from a list, populate the shared spaces and dump to a
// file.
void MetaspaceShared::preload_and_dump(TRAPS) {
  { TraceTime timer("Dump Shared Spaces", TRACETIME_LOG(Info, startuptime));
    ResourceMark rm(THREAD);
    char class_list_path_str[JVM_MAXPATHLEN];
    // Preload classes to be shared.
    const char* class_list_path;
    if (SharedClassListFile == NULL) {
      // Construct the path to the class list (in jre/lib)
      // Walk up two directories from the location of the VM and
      // optionally tack on "lib" (depending on platform)
      os::jvm_path(class_list_path_str, sizeof(class_list_path_str));
      for (int i = 0; i < 3; i++) {
        char *end = strrchr(class_list_path_str, *os::file_separator());
        if (end != NULL) *end = '\0';
      }
      int class_list_path_len = (int)strlen(class_list_path_str);
      if (class_list_path_len >= 3) {
        if (strcmp(class_list_path_str + class_list_path_len - 3, "lib") != 0) {
          if (class_list_path_len < JVM_MAXPATHLEN - 4) {
            jio_snprintf(class_list_path_str + class_list_path_len,
                         sizeof(class_list_path_str) - class_list_path_len,
                         "%slib", os::file_separator());
            class_list_path_len += 4;
          }
        }
      }
      if (class_list_path_len < JVM_MAXPATHLEN - 10) {
        jio_snprintf(class_list_path_str + class_list_path_len,
                     sizeof(class_list_path_str) - class_list_path_len,
                     "%sclasslist", os::file_separator());
      }
      class_list_path = class_list_path_str;
    } else {
      class_list_path = SharedClassListFile;
    }

    log_info(cds)("Loading classes to share ...");
    _has_error_classes = false;
    int class_count = preload_classes(class_list_path, THREAD);
    if (ExtraSharedClassListFile) {
      class_count += preload_classes(ExtraSharedClassListFile, THREAD);
    }
    log_info(cds)("Loading classes to share: done.");

    log_info(cds)("Shared spaces: preloaded %d classes", class_count);

    if (SharedArchiveConfigFile) {
      log_info(cds)("Reading extra data from %s ...", SharedArchiveConfigFile);
      read_extra_data(SharedArchiveConfigFile, THREAD);
    }
    log_info(cds)("Reading extra data: done.");

    HeapShared::init_subgraph_entry_fields(THREAD);

    // Rewrite and link classes
    log_info(cds)("Rewriting and linking classes ...");

    // Link any classes which got missed. This would happen if we have loaded classes that
    // were not explicitly specified in the classlist. E.g., if an interface implemented by class K
    // fails verification, all other interfaces that were not specified in the classlist but
    // are implemented by K are not verified.
    link_and_cleanup_shared_classes(CATCH);
    log_info(cds)("Rewriting and linking classes: done");

    if (HeapShared::is_heap_object_archiving_allowed()) {
      // Avoid fragmentation while archiving heap objects.
      Universe::heap()->soft_ref_policy()->set_should_clear_all_soft_refs(true);
      Universe::heap()->collect(GCCause::_archive_time_gc);
      Universe::heap()->soft_ref_policy()->set_should_clear_all_soft_refs(false);
    }

    VM_PopulateDumpSharedSpace op;
    VMThread::execute(&op);
  }
}


int MetaspaceShared::preload_classes(const char* class_list_path, TRAPS) {
  ClassListParser parser(class_list_path);
  int class_count = 0;

  while (parser.parse_one_line()) {
    Klass* klass = parser.load_current_class(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      if (klass == NULL &&
          (PENDING_EXCEPTION->klass()->name() == vmSymbols::java_lang_ClassNotFoundException())) {
        // print a warning only when the pending exception is class not found
        log_warning(cds)("Preload Warning: Cannot find %s", parser.current_class_name());
      }
      CLEAR_PENDING_EXCEPTION;
    }
    if (klass != NULL) {
      if (log_is_enabled(Trace, cds)) {
        ResourceMark rm(THREAD);
        log_trace(cds)("Shared spaces preloaded: %s", klass->external_name());
      }

      if (klass->is_instance_klass()) {
        InstanceKlass* ik = InstanceKlass::cast(klass);

        // Link the class to cause the bytecodes to be rewritten and the
        // cpcache to be created. The linking is done as soon as classes
        // are loaded in order that the related data structures (klass and
        // cpCache) are located together.
        try_link_class(ik, THREAD);
        guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");
      }

      class_count++;
    }
  }

  return class_count;
}

// Returns true if the class's status has changed
bool MetaspaceShared::try_link_class(InstanceKlass* ik, TRAPS) {
  Arguments::assert_is_dumping_archive();
  if (ik->init_state() < InstanceKlass::linked &&
      !SystemDictionaryShared::has_class_failed_verification(ik)) {
    bool saved = BytecodeVerificationLocal;
    if (ik->is_shared_unregistered_class() && ik->class_loader() == NULL) {
      // The verification decision is based on BytecodeVerificationRemote
      // for non-system classes. Since we are using the NULL classloader
      // to load non-system classes for customized class loaders during dumping,
      // we need to temporarily change BytecodeVerificationLocal to be the same as
      // BytecodeVerificationRemote. Note this can cause the parent system
      // classes also being verified. The extra overhead is acceptable during
      // dumping.
      BytecodeVerificationLocal = BytecodeVerificationRemote;
    }
    ik->link_class(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      ResourceMark rm(THREAD);
      log_warning(cds)("Preload Warning: Verification failed for %s",
                    ik->external_name());
      CLEAR_PENDING_EXCEPTION;
      SystemDictionaryShared::set_class_has_failed_verification(ik);
      _has_error_classes = true;
    }
    BytecodeVerificationLocal = saved;
    return true;
  } else {
    return false;
  }
}

#if INCLUDE_CDS_JAVA_HEAP
void VM_PopulateDumpSharedSpace::dump_java_heap_objects() {
  // The closed and open archive heap space has maximum two regions.
  // See FileMapInfo::write_archive_heap_regions() for details.
  _closed_archive_heap_regions = new GrowableArray<MemRegion>(2);
  _open_archive_heap_regions = new GrowableArray<MemRegion>(2);
  HeapShared::archive_java_heap_objects(_closed_archive_heap_regions,
                                        _open_archive_heap_regions);
  ArchiveCompactor::OtherROAllocMark mark;
  HeapShared::write_subgraph_info_table();
}

void VM_PopulateDumpSharedSpace::dump_archive_heap_oopmaps() {
  if (HeapShared::is_heap_object_archiving_allowed()) {
    _closed_archive_heap_oopmaps = new GrowableArray<ArchiveHeapOopmapInfo>(2);
    dump_archive_heap_oopmaps(_closed_archive_heap_regions, _closed_archive_heap_oopmaps);

    _open_archive_heap_oopmaps = new GrowableArray<ArchiveHeapOopmapInfo>(2);
    dump_archive_heap_oopmaps(_open_archive_heap_regions, _open_archive_heap_oopmaps);
  }
}

void VM_PopulateDumpSharedSpace::dump_archive_heap_oopmaps(GrowableArray<MemRegion>* regions,
                                                           GrowableArray<ArchiveHeapOopmapInfo>* oopmaps) {
  for (int i=0; i<regions->length(); i++) {
    ResourceBitMap oopmap = HeapShared::calculate_oopmap(regions->at(i));
    size_t size_in_bits = oopmap.size();
    size_t size_in_bytes = oopmap.size_in_bytes();
    uintptr_t* buffer = (uintptr_t*)NEW_C_HEAP_ARRAY(char, size_in_bytes, mtInternal);
    oopmap.write_to(buffer, size_in_bytes);
    log_info(cds, heap)("Oopmap = " INTPTR_FORMAT " (" SIZE_FORMAT_W(6) " bytes) for heap region "
                        INTPTR_FORMAT " (" SIZE_FORMAT_W(8) " bytes)",
                        p2i(buffer), size_in_bytes,
                        p2i(regions->at(i).start()), regions->at(i).byte_size());

    ArchiveHeapOopmapInfo info;
    info._oopmap = (address)buffer;
    info._oopmap_size_in_bits = size_in_bits;
    info._oopmap_size_in_bytes = size_in_bytes;
    oopmaps->append(info);
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP

void ReadClosure::do_ptr(void** p) {
  assert(*p == NULL, "initializing previous initialized pointer.");
  intptr_t obj = nextPtr();
  assert((intptr_t)obj >= 0 || (intptr_t)obj < -100,
         "hit tag while initializing ptrs.");
  *p = (void*)obj;
}

void ReadClosure::do_u4(u4* p) {
  intptr_t obj = nextPtr();
  *p = (u4)(uintx(obj));
}

void ReadClosure::do_bool(bool* p) {
  intptr_t obj = nextPtr();
  *p = (bool)(uintx(obj));
}

void ReadClosure::do_tag(int tag) {
  int old_tag;
  old_tag = (int)(intptr_t)nextPtr();
  // do_int(&old_tag);
  assert(tag == old_tag, "old tag doesn't match");
  FileMapInfo::assert_mark(tag == old_tag);
}

void ReadClosure::do_oop(oop *p) {
  narrowOop o = (narrowOop)nextPtr();
  if (o == 0 || !HeapShared::open_archive_heap_region_mapped()) {
    p = NULL;
  } else {
    assert(HeapShared::is_heap_object_archiving_allowed(),
           "Archived heap object is not allowed");
    assert(HeapShared::open_archive_heap_region_mapped(),
           "Open archive heap region is not mapped");
    *p = HeapShared::decode_from_archive(o);
  }
}

void ReadClosure::do_region(u_char* start, size_t size) {
  assert((intptr_t)start % sizeof(intptr_t) == 0, "bad alignment");
  assert(size % sizeof(intptr_t) == 0, "bad size");
  do_tag((int)size);
  while (size > 0) {
    *(intptr_t*)start = nextPtr();
    start += sizeof(intptr_t);
    size -= sizeof(intptr_t);
  }
}

void MetaspaceShared::set_shared_metaspace_range(void* base, void *static_top, void* top) {
  assert(base <= static_top && static_top <= top, "must be");
  _shared_metaspace_static_top = static_top;
  MetaspaceObj::set_shared_metaspace_range(base, top);
}

// Return true if given address is in the misc data region
bool MetaspaceShared::is_in_shared_region(const void* p, int idx) {
  return UseSharedSpaces && FileMapInfo::current_info()->is_in_shared_region(p, idx);
}

bool MetaspaceShared::is_in_trampoline_frame(address addr) {
  if (UseSharedSpaces && is_in_shared_region(addr, MetaspaceShared::mc)) {
    return true;
  }
  return false;
}

bool MetaspaceShared::is_shared_dynamic(void* p) {
  if ((p < MetaspaceObj::shared_metaspace_top()) &&
      (p >= _shared_metaspace_static_top)) {
    return true;
  } else {
    return false;
  }
}

void MetaspaceShared::initialize_runtime_shared_and_meta_spaces() {
  assert(UseSharedSpaces, "Must be called when UseSharedSpaces is enabled");
  MapArchiveResult result = MAP_ARCHIVE_OTHER_FAILURE;

  FileMapInfo* static_mapinfo = open_static_archive();
  FileMapInfo* dynamic_mapinfo = NULL;

  if (static_mapinfo != NULL) {
    dynamic_mapinfo = open_dynamic_archive();

    // First try to map at the requested address
    result = map_archives(static_mapinfo, dynamic_mapinfo, true);
    if (result == MAP_ARCHIVE_MMAP_FAILURE) {
      // Mapping has failed (probably due to ASLR). Let's map at an address chosen
      // by the OS.
      log_info(cds)("Try to map archive(s) at an alternative address");
      result = map_archives(static_mapinfo, dynamic_mapinfo, false);
    }
  }

  if (result == MAP_ARCHIVE_SUCCESS) {
    bool dynamic_mapped = (dynamic_mapinfo != NULL && dynamic_mapinfo->is_mapped());
    char* cds_base = static_mapinfo->mapped_base();
    char* cds_end =  dynamic_mapped ? dynamic_mapinfo->mapped_end() : static_mapinfo->mapped_end();
    set_shared_metaspace_range(cds_base, static_mapinfo->mapped_end(), cds_end);
    _relocation_delta = static_mapinfo->relocation_delta();
    if (dynamic_mapped) {
      FileMapInfo::set_shared_path_table(dynamic_mapinfo);
    } else {
      FileMapInfo::set_shared_path_table(static_mapinfo);
    }
    _requested_base_address = static_mapinfo->requested_base_address();
  } else {
    set_shared_metaspace_range(NULL, NULL, NULL);
    UseSharedSpaces = false;
    FileMapInfo::fail_continue("Unable to map shared spaces");
    if (PrintSharedArchiveAndExit) {
      vm_exit_during_initialization("Unable to use shared archive.");
    }
  }

  if (static_mapinfo != NULL && !static_mapinfo->is_mapped()) {
    delete static_mapinfo;
  }
  if (dynamic_mapinfo != NULL && !dynamic_mapinfo->is_mapped()) {
    delete dynamic_mapinfo;
  }
}

FileMapInfo* MetaspaceShared::open_static_archive() {
  FileMapInfo* mapinfo = new FileMapInfo(true);
  if (!mapinfo->initialize()) {
    delete(mapinfo);
    return NULL;
  }
  return mapinfo;
}

FileMapInfo* MetaspaceShared::open_dynamic_archive() {
  if (DynamicDumpSharedSpaces) {
    return NULL;
  }
  if (Arguments::GetSharedDynamicArchivePath() == NULL) {
    return NULL;
  }

  FileMapInfo* mapinfo = new FileMapInfo(false);
  if (!mapinfo->initialize()) {
    delete(mapinfo);
    return NULL;
  }
  return mapinfo;
}

// use_requested_addr:
//  true  = map at FileMapHeader::_requested_base_address
//  false = map at an alternative address picked by OS.
MapArchiveResult MetaspaceShared::map_archives(FileMapInfo* static_mapinfo, FileMapInfo* dynamic_mapinfo,
                                               bool use_requested_addr) {
  if (use_requested_addr && static_mapinfo->requested_base_address() == NULL) {
    log_info(cds)("Archive(s) were created with -XX:SharedBaseAddress=0. Always map at os-selected address.");
    return MAP_ARCHIVE_MMAP_FAILURE;
  }

  PRODUCT_ONLY(if (ArchiveRelocationMode == 1 && use_requested_addr) {
      // For product build only -- this is for benchmarking the cost of doing relocation.
      // For debug builds, the check is done below, after reserving the space, for better test coverage
      // (see comment below).
      log_info(cds)("ArchiveRelocationMode == 1: always map archive(s) at an alternative address");
      return MAP_ARCHIVE_MMAP_FAILURE;
    });

  if (ArchiveRelocationMode == 2 && !use_requested_addr) {
    log_info(cds)("ArchiveRelocationMode == 2: never map archive(s) at an alternative address");
    return MAP_ARCHIVE_MMAP_FAILURE;
  };

  if (dynamic_mapinfo != NULL) {
    // Ensure that the OS won't be able to allocate new memory spaces between the two
    // archives, or else it would mess up the simple comparision in MetaspaceObj::is_shared().
    assert(static_mapinfo->mapping_end_offset() == dynamic_mapinfo->mapping_base_offset(), "no gap");
  }

  ReservedSpace archive_space_rs, class_space_rs;
  MapArchiveResult result = MAP_ARCHIVE_OTHER_FAILURE;
  char* mapped_base_address = reserve_address_space_for_archives(static_mapinfo, dynamic_mapinfo,
                                                                 use_requested_addr, archive_space_rs,
                                                                 class_space_rs);
  if (mapped_base_address == NULL) {
    result = MAP_ARCHIVE_MMAP_FAILURE;
    log_debug(cds)("Failed to reserve spaces (use_requested_addr=%u)", (unsigned)use_requested_addr);
  } else {

#ifdef ASSERT
    // Some sanity checks after reserving address spaces for archives
    //  and class space.
    assert(archive_space_rs.is_reserved(), "Sanity");
    if (Metaspace::using_class_space()) {
      // Class space must closely follow the archive space. Both spaces
      //  must be aligned correctly.
      assert(class_space_rs.is_reserved(),
             "A class space should have been reserved");
      assert(class_space_rs.base() >= archive_space_rs.end(),
             "class space should follow the cds archive space");
      assert(is_aligned(archive_space_rs.base(),
                        MetaspaceShared::reserved_space_alignment()),
             "Archive space misaligned");
      assert(is_aligned(class_space_rs.base(),
                        Metaspace::reserve_alignment()),
             "class space misaligned");
    }
#endif // ASSERT

    log_debug(cds)("Reserved archive_space_rs     [" INTPTR_FORMAT " - " INTPTR_FORMAT "] (" SIZE_FORMAT ") bytes",
                   p2i(archive_space_rs.base()), p2i(archive_space_rs.end()), archive_space_rs.size());
    log_debug(cds)("Reserved class_space_rs [" INTPTR_FORMAT " - " INTPTR_FORMAT "] (" SIZE_FORMAT ") bytes",
                   p2i(class_space_rs.base()), p2i(class_space_rs.end()), class_space_rs.size());

    if (MetaspaceShared::use_windows_memory_mapping()) {
      // We have now reserved address space for the archives, and will map in
      //  the archive files into this space.
      //
      // Special handling for Windows: on Windows we cannot map a file view
      //  into an existing memory mapping. So, we unmap the address range we
      //  just reserved again, which will make it available for mapping the
      //  archives.
      // Reserving this range has not been for naught however since it makes
      //  us reasonably sure the address range is available.
      //
      // But still it may fail, since between unmapping the range and mapping
      //  in the archive someone else may grab the address space. Therefore
      //  there is a fallback in FileMap::map_region() where we just read in
      //  the archive files sequentially instead of mapping it in. We couple
      //  this with use_requested_addr, since we're going to patch all the
      //  pointers anyway so there's no benefit to mmap.
      if (use_requested_addr) {
        log_info(cds)("Windows mmap workaround: releasing archive space.");
        archive_space_rs.release();
      }
    }
    MapArchiveResult static_result = map_archive(static_mapinfo, mapped_base_address, archive_space_rs);
    MapArchiveResult dynamic_result = (static_result == MAP_ARCHIVE_SUCCESS) ?
                                     map_archive(dynamic_mapinfo, mapped_base_address, archive_space_rs) : MAP_ARCHIVE_OTHER_FAILURE;

    DEBUG_ONLY(if (ArchiveRelocationMode == 1 && use_requested_addr) {
      // This is for simulating mmap failures at the requested address. In
      //  debug builds, we do it here (after all archives have possibly been
      //  mapped), so we can thoroughly test the code for failure handling
      //  (releasing all allocated resource, etc).
      log_info(cds)("ArchiveRelocationMode == 1: always map archive(s) at an alternative address");
      if (static_result == MAP_ARCHIVE_SUCCESS) {
        static_result = MAP_ARCHIVE_MMAP_FAILURE;
      }
      if (dynamic_result == MAP_ARCHIVE_SUCCESS) {
        dynamic_result = MAP_ARCHIVE_MMAP_FAILURE;
      }
    });

    if (static_result == MAP_ARCHIVE_SUCCESS) {
      if (dynamic_result == MAP_ARCHIVE_SUCCESS) {
        result = MAP_ARCHIVE_SUCCESS;
      } else if (dynamic_result == MAP_ARCHIVE_OTHER_FAILURE) {
        assert(dynamic_mapinfo != NULL && !dynamic_mapinfo->is_mapped(), "must have failed");
        // No need to retry mapping the dynamic archive again, as it will never succeed
        // (bad file, etc) -- just keep the base archive.
        log_warning(cds, dynamic)("Unable to use shared archive. The top archive failed to load: %s",
                                  dynamic_mapinfo->full_path());
        result = MAP_ARCHIVE_SUCCESS;
        // TODO, we can give the unused space for the dynamic archive to class_space_rs, but there's no
        // easy API to do that right now.
      } else {
        result = MAP_ARCHIVE_MMAP_FAILURE;
      }
    } else if (static_result == MAP_ARCHIVE_OTHER_FAILURE) {
      result = MAP_ARCHIVE_OTHER_FAILURE;
    } else {
      result = MAP_ARCHIVE_MMAP_FAILURE;
    }
  }

  if (result == MAP_ARCHIVE_SUCCESS) {
    SharedBaseAddress = (size_t)mapped_base_address;
    LP64_ONLY({
        if (Metaspace::using_class_space()) {
          // Set up ccs in metaspace.
          Metaspace::initialize_class_space(class_space_rs);

          // Set up compressed Klass pointer encoding: the encoding range must
          //  cover both archive and class space.
          address cds_base = (address)static_mapinfo->mapped_base();
          address ccs_end = (address)class_space_rs.end();
          CompressedKlassPointers::initialize(cds_base, ccs_end - cds_base);

          // map_heap_regions() compares the current narrow oop and klass encodings
          // with the archived ones, so it must be done after all encodings are determined.
          static_mapinfo->map_heap_regions();
        }
      });
    log_info(cds)("Using optimized module handling %s", MetaspaceShared::use_optimized_module_handling() ? "enabled" : "disabled");
  } else {
    unmap_archive(static_mapinfo);
    unmap_archive(dynamic_mapinfo);
    release_reserved_spaces(archive_space_rs, class_space_rs);
  }

  return result;
}


// This will reserve two address spaces suitable to house Klass structures, one
//  for the cds archives (static archive and optionally dynamic archive) and
//  optionally one move for ccs.
//
// Since both spaces must fall within the compressed class pointer encoding
//  range, they are allocated close to each other.
//
// Space for archives will be reserved first, followed by a potential gap,
//  followed by the space for ccs:
//
// +-- Base address             A        B                     End
// |                            |        |                      |
// v                            v        v                      v
// +-------------+--------------+        +----------------------+
// | static arc  | [dyn. arch]  | [gap]  | compr. class space   |
// +-------------+--------------+        +----------------------+
//
// (The gap may result from different alignment requirements between metaspace
//  and CDS)
//
// If UseCompressedClassPointers is disabled, only one address space will be
//  reserved:
//
// +-- Base address             End
// |                            |
// v                            v
// +-------------+--------------+
// | static arc  | [dyn. arch]  |
// +-------------+--------------+
//
// Base address: If use_archive_base_addr address is true, the Base address is
//  determined by the address stored in the static archive. If
//  use_archive_base_addr address is false, this base address is determined
//  by the platform.
//
// If UseCompressedClassPointers=1, the range encompassing both spaces will be
//  suitable to en/decode narrow Klass pointers: the base will be valid for
//  encoding, the range [Base, End) not surpass KlassEncodingMetaspaceMax.
//
// Return:
//
// - On success:
//    - archive_space_rs will be reserved and large enough to host static and
//      if needed dynamic archive: [Base, A).
//      archive_space_rs.base and size will be aligned to CDS reserve
//      granularity.
//    - class_space_rs: If UseCompressedClassPointers=1, class_space_rs will
//      be reserved. Its start address will be aligned to metaspace reserve
//      alignment, which may differ from CDS alignment. It will follow the cds
//      archive space, close enough such that narrow class pointer encoding
//      covers both spaces.
//      If UseCompressedClassPointers=0, class_space_rs remains unreserved.
// - On error: NULL is returned and the spaces remain unreserved.
char* MetaspaceShared::reserve_address_space_for_archives(FileMapInfo* static_mapinfo,
                                                          FileMapInfo* dynamic_mapinfo,
                                                          bool use_archive_base_addr,
                                                          ReservedSpace& archive_space_rs,
                                                          ReservedSpace& class_space_rs) {

  address const base_address = (address) (use_archive_base_addr ? static_mapinfo->requested_base_address() : NULL);
  const size_t archive_space_alignment = MetaspaceShared::reserved_space_alignment();

  // Size and requested location of the archive_space_rs (for both static and dynamic archives)
  assert(static_mapinfo->mapping_base_offset() == 0, "Must be");
  size_t archive_end_offset  = (dynamic_mapinfo == NULL) ? static_mapinfo->mapping_end_offset() : dynamic_mapinfo->mapping_end_offset();
  size_t archive_space_size = align_up(archive_end_offset, archive_space_alignment);

  // If a base address is given, it must have valid alignment and be suitable as encoding base.
  if (base_address != NULL) {
    assert(is_aligned(base_address, archive_space_alignment),
           "Archive base address invalid: " PTR_FORMAT ".", p2i(base_address));
    if (Metaspace::using_class_space()) {
      assert(CompressedKlassPointers::is_valid_base(base_address),
             "Archive base address invalid: " PTR_FORMAT ".", p2i(base_address));
    }
  }

  if (!Metaspace::using_class_space()) {
    // Get the simple case out of the way first:
    // no compressed class space, simple allocation.
    archive_space_rs = ReservedSpace(archive_space_size, archive_space_alignment,
                                     false /* bool large */, (char*)base_address);
    if (archive_space_rs.is_reserved()) {
      assert(base_address == NULL ||
             (address)archive_space_rs.base() == base_address, "Sanity");
      // Register archive space with NMT.
      MemTracker::record_virtual_memory_type(archive_space_rs.base(), mtClassShared);
      return archive_space_rs.base();
    }
    return NULL;
  }

#ifdef _LP64

  // Complex case: two spaces adjacent to each other, both to be addressable
  //  with narrow class pointers.
  // We reserve the whole range spanning both spaces, then split that range up.

  const size_t class_space_alignment = Metaspace::reserve_alignment();

  // To simplify matters, lets assume that metaspace alignment will always be
  //  equal or a multiple of archive alignment.
  assert(is_power_of_2(class_space_alignment) &&
                       is_power_of_2(archive_space_alignment) &&
                       class_space_alignment >= archive_space_alignment,
                       "Sanity");

  const size_t class_space_size = CompressedClassSpaceSize;
  assert(CompressedClassSpaceSize > 0 &&
         is_aligned(CompressedClassSpaceSize, class_space_alignment),
         "CompressedClassSpaceSize malformed: "
         SIZE_FORMAT, CompressedClassSpaceSize);

  const size_t ccs_begin_offset = align_up(archive_space_size,
                                           class_space_alignment);
  const size_t gap_size = ccs_begin_offset - archive_space_size;

  const size_t total_range_size =
      align_up(archive_space_size + gap_size + class_space_size,
               os::vm_allocation_granularity());

  ReservedSpace total_rs;
  if (base_address != NULL) {
    // Reserve at the given archive base address, or not at all.
    total_rs = ReservedSpace(total_range_size, archive_space_alignment,
                             false /* bool large */, (char*) base_address);
  } else {
    // Reserve at any address, but leave it up to the platform to choose a good one.
    total_rs = Metaspace::reserve_address_space_for_compressed_classes(total_range_size);
  }

  if (!total_rs.is_reserved()) {
    return NULL;
  }

  // Paranoid checks:
  assert(base_address == NULL || (address)total_rs.base() == base_address,
         "Sanity (" PTR_FORMAT " vs " PTR_FORMAT ")", p2i(base_address), p2i(total_rs.base()));
  assert(is_aligned(total_rs.base(), archive_space_alignment), "Sanity");
  assert(total_rs.size() == total_range_size, "Sanity");
  assert(CompressedKlassPointers::is_valid_base((address)total_rs.base()), "Sanity");

  // Now split up the space into ccs and cds archive. For simplicity, just leave
  //  the gap reserved at the end of the archive space.
  archive_space_rs = total_rs.first_part(ccs_begin_offset,
                                         (size_t)os::vm_allocation_granularity(),
                                         /*split=*/true);
  class_space_rs = total_rs.last_part(ccs_begin_offset);

  assert(is_aligned(archive_space_rs.base(), archive_space_alignment), "Sanity");
  assert(is_aligned(archive_space_rs.size(), archive_space_alignment), "Sanity");
  assert(is_aligned(class_space_rs.base(), class_space_alignment), "Sanity");
  assert(is_aligned(class_space_rs.size(), class_space_alignment), "Sanity");

  // NMT: fix up the space tags
  MemTracker::record_virtual_memory_type(archive_space_rs.base(), mtClassShared);
  MemTracker::record_virtual_memory_type(class_space_rs.base(), mtClass);

  return archive_space_rs.base();

#else
  ShouldNotReachHere();
  return NULL;
#endif

}

void MetaspaceShared::release_reserved_spaces(ReservedSpace& archive_space_rs,
                                              ReservedSpace& class_space_rs) {
  if (archive_space_rs.is_reserved()) {
    log_debug(cds)("Released shared space (archive) " INTPTR_FORMAT, p2i(archive_space_rs.base()));
    archive_space_rs.release();
  }
  if (class_space_rs.is_reserved()) {
    log_debug(cds)("Released shared space (classes) " INTPTR_FORMAT, p2i(class_space_rs.base()));
    class_space_rs.release();
  }
}

static int archive_regions[]  = {MetaspaceShared::mc,
                                 MetaspaceShared::rw,
                                 MetaspaceShared::ro};
static int archive_regions_count  = 3;

MapArchiveResult MetaspaceShared::map_archive(FileMapInfo* mapinfo, char* mapped_base_address, ReservedSpace rs) {
  assert(UseSharedSpaces, "must be runtime");
  if (mapinfo == NULL) {
    return MAP_ARCHIVE_SUCCESS; // The dynamic archive has not been specified. No error has happened -- trivially succeeded.
  }

  mapinfo->set_is_mapped(false);

  if (mapinfo->alignment() != (size_t)os::vm_allocation_granularity()) {
    log_error(cds)("Unable to map CDS archive -- os::vm_allocation_granularity() expected: " SIZE_FORMAT
                   " actual: %d", mapinfo->alignment(), os::vm_allocation_granularity());
    return MAP_ARCHIVE_OTHER_FAILURE;
  }

  MapArchiveResult result =
    mapinfo->map_regions(archive_regions, archive_regions_count, mapped_base_address, rs);

  if (result != MAP_ARCHIVE_SUCCESS) {
    unmap_archive(mapinfo);
    return result;
  }

  if (!mapinfo->validate_shared_path_table()) {
    unmap_archive(mapinfo);
    return MAP_ARCHIVE_OTHER_FAILURE;
  }

  mapinfo->set_is_mapped(true);
  return MAP_ARCHIVE_SUCCESS;
}

void MetaspaceShared::unmap_archive(FileMapInfo* mapinfo) {
  assert(UseSharedSpaces, "must be runtime");
  if (mapinfo != NULL) {
    mapinfo->unmap_regions(archive_regions, archive_regions_count);
    mapinfo->set_is_mapped(false);
  }
}

// Read the miscellaneous data from the shared file, and
// serialize it out to its various destinations.

void MetaspaceShared::initialize_shared_spaces() {
  FileMapInfo *static_mapinfo = FileMapInfo::current_info();
  _i2i_entry_code_buffers = static_mapinfo->i2i_entry_code_buffers();
  _i2i_entry_code_buffers_size = static_mapinfo->i2i_entry_code_buffers_size();
  char* buffer = static_mapinfo->cloned_vtables();
  clone_cpp_vtables((intptr_t*)buffer);

  // Verify various attributes of the archive, plus initialize the
  // shared string/symbol tables
  buffer = static_mapinfo->serialized_data();
  intptr_t* array = (intptr_t*)buffer;
  ReadClosure rc(&array);
  serialize(&rc);

  // Initialize the run-time symbol table.
  SymbolTable::create_table();

  static_mapinfo->patch_archived_heap_embedded_pointers();

  // Close the mapinfo file
  static_mapinfo->close();

  static_mapinfo->unmap_region(MetaspaceShared::bm);

  FileMapInfo *dynamic_mapinfo = FileMapInfo::dynamic_info();
  if (dynamic_mapinfo != NULL) {
    intptr_t* buffer = (intptr_t*)dynamic_mapinfo->serialized_data();
    ReadClosure rc(&buffer);
    SymbolTable::serialize_shared_table_header(&rc, false);
    SystemDictionaryShared::serialize_dictionary_headers(&rc, false);
    dynamic_mapinfo->close();
  }

  if (PrintSharedArchiveAndExit) {
    if (PrintSharedDictionary) {
      tty->print_cr("\nShared classes:\n");
      SystemDictionaryShared::print_on(tty);
    }
    if (FileMapInfo::current_info() == NULL || _archive_loading_failed) {
      tty->print_cr("archive is invalid");
      vm_exit(1);
    } else {
      tty->print_cr("archive is valid");
      vm_exit(0);
    }
  }
}

// JVM/TI RedefineClasses() support:
bool MetaspaceShared::remap_shared_readonly_as_readwrite() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  if (UseSharedSpaces) {
    // remap the shared readonly space to shared readwrite, private
    FileMapInfo* mapinfo = FileMapInfo::current_info();
    if (!mapinfo->remap_shared_readonly_as_readwrite()) {
      return false;
    }
    if (FileMapInfo::dynamic_info() != NULL) {
      mapinfo = FileMapInfo::dynamic_info();
      if (!mapinfo->remap_shared_readonly_as_readwrite()) {
        return false;
      }
    }
    _remapped_readwrite = true;
  }
  return true;
}

void MetaspaceShared::report_out_of_space(const char* name, size_t needed_bytes) {
  // This is highly unlikely to happen on 64-bits because we have reserved a 4GB space.
  // On 32-bit we reserve only 256MB so you could run out of space with 100,000 classes
  // or so.
  _mc_region.print_out_of_space_msg(name, needed_bytes);
  _rw_region.print_out_of_space_msg(name, needed_bytes);
  _ro_region.print_out_of_space_msg(name, needed_bytes);

  vm_exit_during_initialization(err_msg("Unable to allocate from '%s' region", name),
                                "Please reduce the number of shared classes.");
}

// This is used to relocate the pointers so that the base archive can be mapped at
// MetaspaceShared::requested_base_address() without runtime relocation.
intx MetaspaceShared::final_delta() {
  return intx(MetaspaceShared::requested_base_address())  // We want the base archive to be mapped to here at runtime
       - intx(SharedBaseAddress);                         // .. but the base archive is mapped at here at dump time
}

void MetaspaceShared::print_on(outputStream* st) {
  if (UseSharedSpaces || DumpSharedSpaces) {
    st->print("CDS archive(s) mapped at: ");
    address base;
    address top;
    if (UseSharedSpaces) { // Runtime
      base = (address)MetaspaceObj::shared_metaspace_base();
      address static_top = (address)_shared_metaspace_static_top;
      top = (address)MetaspaceObj::shared_metaspace_top();
      st->print("[" PTR_FORMAT "-" PTR_FORMAT "-" PTR_FORMAT "), ", p2i(base), p2i(static_top), p2i(top));
    } else if (DumpSharedSpaces) { // Dump Time
      base = (address)_shared_rs.base();
      top = (address)_shared_rs.end();
      st->print("[" PTR_FORMAT "-" PTR_FORMAT "), ", p2i(base), p2i(top));
    }
    st->print("size " SIZE_FORMAT ", ", top - base);
    st->print("SharedBaseAddress: " PTR_FORMAT ", ArchiveRelocationMode: %d.", SharedBaseAddress, (int)ArchiveRelocationMode);
  } else {
    st->print("CDS disabled.");
  }
  st->cr();
}






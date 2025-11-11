/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "asm/macroAssembler.hpp"
#include "cds/aotCacheAccess.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/javaAssertions.hpp"
#include "code/aotCodeCache.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/gcConfig.hpp"
#include "logging/logStream.hpp"
#include "memory/memoryReserver.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubInfo.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/copy.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif
#if INCLUDE_G1GC
#include "gc/g1/g1BarrierSetRuntime.hpp"
#endif
#if INCLUDE_SHENANDOAHGC
#include "gc/shenandoah/shenandoahRuntime.hpp"
#endif
#if INCLUDE_ZGC
#include "gc/z/zBarrierSetRuntime.hpp"
#endif

#include <errno.h>
#include <sys/stat.h>

const char* aot_code_entry_kind_name[] = {
#define DECL_KIND_STRING(kind) XSTR(kind),
  DO_AOTCODEENTRY_KIND(DECL_KIND_STRING)
#undef DECL_KIND_STRING
};

static void report_load_failure() {
  if (AbortVMOnAOTCodeFailure) {
    vm_exit_during_initialization("Unable to use AOT Code Cache.", nullptr);
  }
  log_info(aot, codecache, init)("Unable to use AOT Code Cache.");
  AOTCodeCache::disable_caching();
}

static void report_store_failure() {
  if (AbortVMOnAOTCodeFailure) {
    tty->print_cr("Unable to create AOT Code Cache.");
    vm_abort(false);
  }
  log_info(aot, codecache, exit)("Unable to create AOT Code Cache.");
  AOTCodeCache::disable_caching();
}

// The sequence of AOT code caching flags and parametters settings.
//
// 1. The initial AOT code caching flags setting is done
// during call to CDSConfig::check_vm_args_consistency().
//
// 2. The earliest AOT code state check done in compilationPolicy_init()
// where we set number of compiler threads for AOT assembly phase.
//
// 3. We determine presence of AOT code in AOT Cache in
// AOTMetaspace::open_static_archive() which is calles
// after compilationPolicy_init() but before codeCache_init().
//
// 4. AOTCodeCache::initialize() is called during universe_init()
// and does final AOT state and flags settings.
//
// 5. Finally AOTCodeCache::init2() is called after universe_init()
// when all GC settings are finalized.

// Next methods determine which action we do with AOT code depending
// on phase of AOT process: assembly or production.

bool AOTCodeCache::is_dumping_adapter() {
  return AOTAdapterCaching && is_on_for_dump();
}

bool AOTCodeCache::is_using_adapter()   {
  return AOTAdapterCaching && is_on_for_use();
}

bool AOTCodeCache::is_dumping_stub() {
  return AOTStubCaching && is_on_for_dump();
}

bool AOTCodeCache::is_using_stub()   {
  return AOTStubCaching && is_on_for_use();
}

// Next methods could be called regardless AOT code cache status.
// Initially they are called during flags parsing and finilized
// in AOTCodeCache::initialize().
void AOTCodeCache::enable_caching() {
  FLAG_SET_ERGO_IF_DEFAULT(AOTStubCaching, true);
  FLAG_SET_ERGO_IF_DEFAULT(AOTAdapterCaching, true);
}

void AOTCodeCache::disable_caching() {
  FLAG_SET_ERGO(AOTStubCaching, false);
  FLAG_SET_ERGO(AOTAdapterCaching, false);
}

bool AOTCodeCache::is_caching_enabled() {
  return AOTStubCaching || AOTAdapterCaching;
}

static uint32_t encode_id(AOTCodeEntry::Kind kind, int id) {
  assert(AOTCodeEntry::is_valid_entry_kind(kind), "invalid AOTCodeEntry kind %d", (int)kind);
  // There can be a conflict of id between an Adapter and *Blob, but that should not cause any functional issue
  // becasue both id and kind are used to find an entry, and that combination should be unique
  if (kind == AOTCodeEntry::Adapter) {
    return id;
  } else if (kind == AOTCodeEntry::SharedBlob) {
    assert(StubInfo::is_shared(static_cast<BlobId>(id)), "not a shared blob id %d", id);
    return id;
  } else if (kind == AOTCodeEntry::C1Blob) {
    assert(StubInfo::is_c1(static_cast<BlobId>(id)), "not a c1 blob id %d", id);
    return id;
  } else if (kind == AOTCodeEntry::C2Blob) {
    assert(StubInfo::is_c2(static_cast<BlobId>(id)), "not a c2 blob id %d", id);
    return id;
  } else {
    // kind must be AOTCodeEntry::StubGenBlob
    assert(StubInfo::is_stubgen(static_cast<BlobId>(id)), "not a stubgen blob id %d", id);
    return id;
  }
}

static uint _max_aot_code_size = 0;
uint AOTCodeCache::max_aot_code_size() {
  return _max_aot_code_size;
}

// It is called from AOTMetaspace::initialize_shared_spaces()
// which is called from universe_init().
// At this point all AOT class linking seetings are finilized
// and AOT cache is open so we can map AOT code region.
void AOTCodeCache::initialize() {
#if defined(ZERO) || !(defined(AMD64) || defined(AARCH64))
  log_info(aot, codecache, init)("AOT Code Cache is not supported on this platform.");
  disable_caching();
  return;
#else
  if (FLAG_IS_DEFAULT(AOTCache)) {
    log_info(aot, codecache, init)("AOT Code Cache is not used: AOTCache is not specified.");
    disable_caching();
    return; // AOTCache must be specified to dump and use AOT code
  }

  // Disable stubs caching until JDK-8357398 is fixed.
  // FLAG_SET_ERGO(AOTStubCaching, false);

  if (VerifyOops) {
    // Disable AOT stubs caching when VerifyOops flag is on.
    // Verify oops code generated a lot of C strings which overflow
    // AOT C string table (which has fixed size).
    // AOT C string table will be reworked later to handle such cases.
    //
    // Note: AOT adapters are not affected - they don't have oop operations.
    log_info(aot, codecache, init)("AOT Stubs Caching is not supported with VerifyOops.");
    FLAG_SET_ERGO(AOTStubCaching, false);
  }

  bool is_dumping = false;
  bool is_using   = false;
  if (CDSConfig::is_dumping_final_static_archive() && CDSConfig::is_dumping_aot_linked_classes()) {
    is_dumping = true;
    enable_caching();
    is_dumping = is_caching_enabled();
  } else if (CDSConfig::is_using_archive() && CDSConfig::is_using_aot_linked_classes()) {
    enable_caching();
    is_using = is_caching_enabled();
  } else {
    log_info(aot, codecache, init)("AOT Code Cache is not used: AOT Class Linking is not used.");
    disable_caching();
    return; // nothing to do
  }
  if (!(is_dumping || is_using)) {
    disable_caching();
    return; // AOT code caching disabled on command line
  }
  _max_aot_code_size = AOTCodeMaxSize;
  if (!FLAG_IS_DEFAULT(AOTCodeMaxSize)) {
    if (!is_aligned(AOTCodeMaxSize, os::vm_allocation_granularity())) {
      _max_aot_code_size = align_up(AOTCodeMaxSize, os::vm_allocation_granularity());
      log_debug(aot,codecache,init)("Max AOT Code Cache size is aligned up to %uK", (int)(max_aot_code_size()/K));
    }
  }
  size_t aot_code_size = is_using ? AOTCacheAccess::get_aot_code_region_size() : 0;
  if (is_using && aot_code_size == 0) {
    log_info(aot, codecache, init)("AOT Code Cache is empty");
    disable_caching();
    return;
  }
  if (!open_cache(is_dumping, is_using)) {
    if (is_using) {
      report_load_failure();
    } else {
      report_store_failure();
    }
    return;
  }
  if (is_dumping) {
    FLAG_SET_DEFAULT(ForceUnreachable, true);
  }
  FLAG_SET_DEFAULT(DelayCompilerStubsGeneration, false);
#endif // defined(AMD64) || defined(AARCH64)
}

static AOTCodeCache*  opened_cache = nullptr; // Use this until we verify the cache
AOTCodeCache* AOTCodeCache::_cache = nullptr;
DEBUG_ONLY( bool AOTCodeCache::_passed_init2 = false; )

// It is called after universe_init() when all GC settings are finalized.
void AOTCodeCache::init2() {
  DEBUG_ONLY( _passed_init2 = true; )
  if (opened_cache == nullptr) {
    return;
  }
  if (!opened_cache->verify_config()) {
    delete opened_cache;
    opened_cache = nullptr;
    report_load_failure();
    return;
  }

  // initialize the table of external routines so we can save
  // generated code blobs that reference them
  AOTCodeAddressTable* table = opened_cache->_table;
  assert(table != nullptr, "should be initialized already");
  table->init_extrs();

  // Now cache and address table are ready for AOT code generation
  _cache = opened_cache;
}

bool AOTCodeCache::open_cache(bool is_dumping, bool is_using) {
  opened_cache = new AOTCodeCache(is_dumping, is_using);
  if (opened_cache->failed()) {
    delete opened_cache;
    opened_cache = nullptr;
    return false;
  }
  return true;
}

// Called after continuations_init() when continuation stub callouts
// have been initialized
void AOTCodeCache::init3() {
  if (opened_cache == nullptr) {
    return;
  }
  // initialize external routines for continuations so we can save
  // generated continuation blob that references them
  AOTCodeAddressTable* table = opened_cache->_table;
  assert(table != nullptr, "should be initialized already");
  table->init_extrs2();
}

void AOTCodeCache::close() {
  if (is_on()) {
    delete _cache; // Free memory
    _cache = nullptr;
    opened_cache = nullptr;
  }
}

#define DATA_ALIGNMENT HeapWordSize

AOTCodeCache::AOTCodeCache(bool is_dumping, bool is_using) :
  _load_header(nullptr),
  _load_buffer(nullptr),
  _store_buffer(nullptr),
  _C_store_buffer(nullptr),
  _write_position(0),
  _load_size(0),
  _store_size(0),
  _for_use(is_using),
  _for_dump(is_dumping),
  _closing(false),
  _failed(false),
  _lookup_failed(false),
  _table(nullptr),
  _load_entries(nullptr),
  _search_entries(nullptr),
  _store_entries(nullptr),
  _C_strings_buf(nullptr),
  _store_entries_cnt(0)
{
  // Read header at the begining of cache
  if (_for_use) {
    // Read cache
    size_t load_size = AOTCacheAccess::get_aot_code_region_size();
    ReservedSpace rs = MemoryReserver::reserve(load_size, mtCode);
    if (!rs.is_reserved()) {
      log_warning(aot, codecache, init)("Failed to reserved %u bytes of memory for mapping AOT code region into AOT Code Cache", (uint)load_size);
      set_failed();
      return;
    }
    if (!AOTCacheAccess::map_aot_code_region(rs)) {
      log_warning(aot, codecache, init)("Failed to read/mmap cached code region into AOT Code Cache");
      set_failed();
      return;
    }

    _load_size = (uint)load_size;
    _load_buffer = (char*)rs.base();
    assert(is_aligned(_load_buffer, DATA_ALIGNMENT), "load_buffer is not aligned");
    log_debug(aot, codecache, init)("Mapped %u bytes at address " INTPTR_FORMAT " at AOT Code Cache", _load_size, p2i(_load_buffer));

    _load_header = (Header*)addr(0);
    if (!_load_header->verify(_load_size)) {
      set_failed();
      return;
    }
    log_info (aot, codecache, init)("Loaded %u AOT code entries from AOT Code Cache", _load_header->entries_count());
    log_debug(aot, codecache, init)("  Adapters:  total=%u", _load_header->adapters_count());
    log_debug(aot, codecache, init)("  Shared Blobs: total=%u", _load_header->shared_blobs_count());
    log_debug(aot, codecache, init)("  C1 Blobs: total=%u", _load_header->C1_blobs_count());
    log_debug(aot, codecache, init)("  C2 Blobs: total=%u", _load_header->C2_blobs_count());
    log_debug(aot, codecache, init)("  AOT code cache size: %u bytes", _load_header->cache_size());

    // Read strings
    load_strings();
  }
  if (_for_dump) {
    _C_store_buffer = NEW_C_HEAP_ARRAY(char, max_aot_code_size() + DATA_ALIGNMENT, mtCode);
    _store_buffer = align_up(_C_store_buffer, DATA_ALIGNMENT);
    // Entries allocated at the end of buffer in reverse (as on stack).
    _store_entries = (AOTCodeEntry*)align_up(_C_store_buffer + max_aot_code_size(), DATA_ALIGNMENT);
    log_debug(aot, codecache, init)("Allocated store buffer at address " INTPTR_FORMAT " of size %u", p2i(_store_buffer), max_aot_code_size());
  }
  _table = new AOTCodeAddressTable();
}

void AOTCodeCache::add_stub_entries(StubId stub_id, address start, GrowableArray<address> *entries, int begin_idx) {
  EntryId entry_id = StubInfo::entry_base(stub_id);
  add_stub_entry(entry_id, start);
  // skip past first entry
  entry_id = StubInfo::next_in_stub(stub_id, entry_id);
  // now check for any more entries
  int count = StubInfo::entry_count(stub_id) - 1;
  assert(start != nullptr, "invalid start address for stub %s", StubInfo::name(stub_id));
  assert(entries == nullptr || begin_idx + count <= entries->length(), "sanity");
  // write any extra entries
  for (int i = 0; i < count; i++) {
    assert(entry_id != EntryId::NO_ENTRYID, "not enough entries for stub %s", StubInfo::name(stub_id));
    address a = entries->at(begin_idx + i);
    add_stub_entry(entry_id, a);
    entry_id = StubInfo::next_in_stub(stub_id, entry_id);
  }
  assert(entry_id == EntryId::NO_ENTRYID, "too many entries for stub %s", StubInfo::name(stub_id));
}

void AOTCodeCache::add_stub_entry(EntryId entry_id, address a) {
  if (a != nullptr) {
    if (_table != nullptr) {
      log_trace(aot, codecache, stubs)("Publishing stub entry %s at address " INTPTR_FORMAT, StubInfo::name(entry_id), p2i(a));
      return _table->add_stub_entry(entry_id, a);
    }
  }
}

void AOTCodeCache::set_shared_stubs_complete() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->set_shared_stubs_complete();
  }
}

void AOTCodeCache::set_c1_stubs_complete() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->set_c1_stubs_complete();
  }
}

void AOTCodeCache::set_c2_stubs_complete() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->set_c2_stubs_complete();
  }
}

void AOTCodeCache::set_stubgen_stubs_complete() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->set_stubgen_stubs_complete();
  }
}

AOTCodeCache::~AOTCodeCache() {
  if (_closing) {
    return; // Already closed
  }
  // Stop any further access to cache.
  _closing = true;

  MutexLocker ml(Compile_lock);
  if (for_dump()) { // Finalize cache
    finish_write();
  }
  _load_buffer = nullptr;
  if (_C_store_buffer != nullptr) {
    FREE_C_HEAP_ARRAY(char, _C_store_buffer);
    _C_store_buffer = nullptr;
    _store_buffer = nullptr;
  }
  if (_table != nullptr) {
    MutexLocker ml(AOTCodeCStrings_lock, Mutex::_no_safepoint_check_flag);
    delete _table;
    _table = nullptr;
  }
}

void AOTCodeCache::Config::record() {
  _flags = 0;
#ifdef ASSERT
  _flags |= debugVM;
#endif
  if (UseCompressedOops) {
    _flags |= compressedOops;
  }
  if (UseCompressedClassPointers) {
    _flags |= compressedClassPointers;
  }
  if (UseTLAB) {
    _flags |= useTLAB;
  }
  if (JavaAssertions::systemClassDefault()) {
    _flags |= systemClassAssertions;
  }
  if (JavaAssertions::userClassDefault()) {
    _flags |= userClassAssertions;
  }
  if (EnableContended) {
    _flags |= enableContendedPadding;
  }
  if (RestrictContended) {
    _flags |= restrictContendedPadding;
  }
  _compressedOopShift    = CompressedOops::shift();
  _compressedOopBase     = CompressedOops::base();
  _compressedKlassShift  = CompressedKlassPointers::shift();
  _contendedPaddingWidth = ContendedPaddingWidth;
  _gc                    = (uint)Universe::heap()->kind();
}

bool AOTCodeCache::Config::verify() const {
  // First checks affect all cached AOT code
#ifdef ASSERT
  if ((_flags & debugVM) == 0) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created by product VM, it can't be used by debug VM");
    return false;
  }
#else
  if ((_flags & debugVM) != 0) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created by debug VM, it can't be used by product VM");
    return false;
  }
#endif

  CollectedHeap::Name aot_gc = (CollectedHeap::Name)_gc;
  if (aot_gc != Universe::heap()->kind()) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with different GC: %s vs current %s", GCConfig::hs_err_name(aot_gc), GCConfig::hs_err_name());
    return false;
  }

  if (((_flags & compressedClassPointers) != 0) != UseCompressedClassPointers) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with UseCompressedClassPointers = %s", UseCompressedClassPointers ? "false" : "true");
    return false;
  }
  if (_compressedKlassShift != (uint)CompressedKlassPointers::shift()) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with CompressedKlassPointers::shift() = %d vs current %d", _compressedKlassShift, CompressedKlassPointers::shift());
    return false;
  }

  // The following checks do not affect AOT adapters caching

  if (((_flags & compressedOops) != 0) != UseCompressedOops) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with UseCompressedOops = %s", UseCompressedOops ? "false" : "true");
    AOTStubCaching = false;
  }
  if (_compressedOopShift != (uint)CompressedOops::shift()) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with different CompressedOops::shift(): %d vs current %d", _compressedOopShift, CompressedOops::shift());
    AOTStubCaching = false;
  }

  // This should be the last check as it only disables AOTStubCaching
  if ((_compressedOopBase == nullptr || CompressedOops::base() == nullptr) && (_compressedOopBase != CompressedOops::base())) {
    log_debug(aot, codecache, init)("AOTStubCaching is disabled: incompatible CompressedOops::base(): %p vs current %p", _compressedOopBase, CompressedOops::base());
    AOTStubCaching = false;
  }

  return true;
}

bool AOTCodeCache::Header::verify(uint load_size) const {
  if (_version != AOT_CODE_VERSION) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: different AOT Code version %d vs %d recorded in AOT Code header", AOT_CODE_VERSION, _version);
    return false;
  }
  if (load_size < _cache_size) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: AOT Code Cache size %d < %d recorded in AOT Code header", load_size, _cache_size);
    return false;
  }
  return true;
}

AOTCodeCache* AOTCodeCache::open_for_use() {
  if (AOTCodeCache::is_on_for_use()) {
    return AOTCodeCache::cache();
  }
  return nullptr;
}

AOTCodeCache* AOTCodeCache::open_for_dump() {
  if (AOTCodeCache::is_on_for_dump()) {
    AOTCodeCache* cache = AOTCodeCache::cache();
    cache->clear_lookup_failed(); // Reset bit
    return cache;
  }
  return nullptr;
}

void copy_bytes(const char* from, address to, uint size) {
  assert((int)size > 0, "sanity");
  memcpy(to, from, size);
  log_trace(aot, codecache)("Copied %d bytes from " INTPTR_FORMAT " to " INTPTR_FORMAT, size, p2i(from), p2i(to));
}

AOTCodeReader::AOTCodeReader(AOTCodeCache* cache, AOTCodeEntry* entry) {
  _cache = cache;
  _entry = entry;
  _load_buffer = cache->cache_buffer();
  _read_position = 0;
  _lookup_failed = false;
}

void AOTCodeReader::set_read_position(uint pos) {
  if (pos == _read_position) {
    return;
  }
  assert(pos < _cache->load_size(), "offset:%d >= file size:%d", pos, _cache->load_size());
  _read_position = pos;
}

bool AOTCodeCache::set_write_position(uint pos) {
  if (pos == _write_position) {
    return true;
  }
  if (_store_size < _write_position) {
    _store_size = _write_position; // Adjust during write
  }
  assert(pos < _store_size, "offset:%d >= file size:%d", pos, _store_size);
  _write_position = pos;
  return true;
}

static char align_buffer[256] = { 0 };

bool AOTCodeCache::align_write() {
  // We are not executing code from cache - we copy it by bytes first.
  // No need for big alignment (or at all).
  uint padding = DATA_ALIGNMENT - (_write_position & (DATA_ALIGNMENT - 1));
  if (padding == DATA_ALIGNMENT) {
    return true;
  }
  uint n = write_bytes((const void*)&align_buffer, padding);
  if (n != padding) {
    return false;
  }
  log_trace(aot, codecache)("Adjust write alignment in AOT Code Cache");
  return true;
}

// Check to see if AOT code cache has required space to store "nbytes" of data
address AOTCodeCache::reserve_bytes(uint nbytes) {
  assert(for_dump(), "Code Cache file is not created");
  uint new_position = _write_position + nbytes;
  if (new_position >= (uint)((char*)_store_entries - _store_buffer)) {
    log_warning(aot,codecache)("Failed to ensure %d bytes at offset %d in AOT Code Cache. Increase AOTCodeMaxSize.",
                               nbytes, _write_position);
    set_failed();
    report_store_failure();
    return nullptr;
  }
  address buffer = (address)(_store_buffer + _write_position);
  log_trace(aot, codecache)("Reserved %d bytes at offset %d in AOT Code Cache", nbytes, _write_position);
  _write_position += nbytes;
  if (_store_size < _write_position) {
    _store_size = _write_position;
  }
  return buffer;
}

uint AOTCodeCache::write_bytes(const void* buffer, uint nbytes) {
  assert(for_dump(), "Code Cache file is not created");
  if (nbytes == 0) {
    return 0;
  }
  uint new_position = _write_position + nbytes;
  if (new_position >= (uint)((char*)_store_entries - _store_buffer)) {
    log_warning(aot, codecache)("Failed to write %d bytes at offset %d to AOT Code Cache. Increase AOTCodeMaxSize.",
                                nbytes, _write_position);
    set_failed();
    report_store_failure();
    return 0;
  }
  copy_bytes((const char* )buffer, (address)(_store_buffer + _write_position), nbytes);
  log_trace(aot, codecache)("Wrote %d bytes at offset %d to AOT Code Cache", nbytes, _write_position);
  _write_position += nbytes;
  if (_store_size < _write_position) {
    _store_size = _write_position;
  }
  return nbytes;
}

void* AOTCodeEntry::operator new(size_t x, AOTCodeCache* cache) {
  return (void*)(cache->add_entry());
}

static bool check_entry(AOTCodeEntry::Kind kind, uint id, AOTCodeEntry* entry) {
  if (entry->kind() == kind) {
    assert(entry->id() == id, "sanity");
    return true; // Found
  }
  return false;
}

AOTCodeEntry* AOTCodeCache::find_entry(AOTCodeEntry::Kind kind, uint id) {
  assert(_for_use, "sanity");
  uint count = _load_header->entries_count();
  if (_load_entries == nullptr) {
    // Read it
    _search_entries = (uint*)addr(_load_header->entries_offset()); // [id, index]
    _load_entries = (AOTCodeEntry*)(_search_entries + 2 * count);
    log_debug(aot, codecache, init)("Read %d entries table at offset %d from AOT Code Cache", count, _load_header->entries_offset());
  }
  // Binary search
  int l = 0;
  int h = count - 1;
  while (l <= h) {
    int mid = (l + h) >> 1;
    int ix = mid * 2;
    uint is = _search_entries[ix];
    if (is == id) {
      int index = _search_entries[ix + 1];
      AOTCodeEntry* entry = &(_load_entries[index]);
      if (check_entry(kind, id, entry)) {
        return entry; // Found
      }
      // Linear search around to handle id collission
      for (int i = mid - 1; i >= l; i--) { // search back
        ix = i * 2;
        is = _search_entries[ix];
        if (is != id) {
          break;
        }
        index = _search_entries[ix + 1];
        AOTCodeEntry* entry = &(_load_entries[index]);
        if (check_entry(kind, id, entry)) {
          return entry; // Found
        }
      }
      for (int i = mid + 1; i <= h; i++) { // search forward
        ix = i * 2;
        is = _search_entries[ix];
        if (is != id) {
          break;
        }
        index = _search_entries[ix + 1];
        AOTCodeEntry* entry = &(_load_entries[index]);
        if (check_entry(kind, id, entry)) {
          return entry; // Found
        }
      }
      break; // Not found match
    } else if (is < id) {
      l = mid + 1;
    } else {
      h = mid - 1;
    }
  }
  return nullptr;
}

extern "C" {
  static int uint_cmp(const void *i, const void *j) {
    uint a = *(uint *)i;
    uint b = *(uint *)j;
    return a > b ? 1 : a < b ? -1 : 0;
  }
}

bool AOTCodeCache::finish_write() {
  if (!align_write()) {
    return false;
  }
  uint strings_offset = _write_position;
  int strings_count = store_strings();
  if (strings_count < 0) {
    return false;
  }
  if (!align_write()) {
    return false;
  }
  uint strings_size = _write_position - strings_offset;

  uint entries_count = 0; // Number of entrant (useful) code entries
  uint entries_offset = _write_position;

  uint store_count = _store_entries_cnt;
  if (store_count > 0) {
    uint header_size = (uint)align_up(sizeof(AOTCodeCache::Header),  DATA_ALIGNMENT);
    uint code_count = store_count;
    uint search_count = code_count * 2;
    uint search_size = search_count * sizeof(uint);
    uint entries_size = (uint)align_up(code_count * sizeof(AOTCodeEntry), DATA_ALIGNMENT); // In bytes
    // _write_position includes size of code and strings
    uint code_alignment = code_count * DATA_ALIGNMENT; // We align_up code size when storing it.
    uint total_size = header_size + _write_position + code_alignment + search_size + entries_size;
    assert(total_size < max_aot_code_size(), "AOT Code size (" UINT32_FORMAT " bytes) is greater than AOTCodeMaxSize(" UINT32_FORMAT " bytes).", total_size, max_aot_code_size());

    // Create ordered search table for entries [id, index];
    uint* search = NEW_C_HEAP_ARRAY(uint, search_count, mtCode);
    // Allocate in AOT Cache buffer
    char* buffer = (char *)AOTCacheAccess::allocate_aot_code_region(total_size + DATA_ALIGNMENT);
    char* start = align_up(buffer, DATA_ALIGNMENT);
    char* current = start + header_size; // Skip header

    AOTCodeEntry* entries_address = _store_entries; // Pointer to latest entry
    uint adapters_count = 0;
    uint shared_blobs_count = 0;
    uint stubgen_blobs_count = 0;
    uint C1_blobs_count = 0;
    uint C2_blobs_count = 0;
    uint max_size = 0;
    // AOTCodeEntry entries were allocated in reverse in store buffer.
    // Process them in reverse order to cache first code first.
    for (int i = store_count - 1; i >= 0; i--) {
      entries_address[i].set_next(nullptr); // clear pointers before storing data
      uint size = align_up(entries_address[i].size(), DATA_ALIGNMENT);
      if (size > max_size) {
        max_size = size;
      }
      copy_bytes((_store_buffer + entries_address[i].offset()), (address)current, size);
      entries_address[i].set_offset(current - start); // New offset
      current += size;
      uint n = write_bytes(&(entries_address[i]), sizeof(AOTCodeEntry));
      if (n != sizeof(AOTCodeEntry)) {
        FREE_C_HEAP_ARRAY(uint, search);
        return false;
      }
      search[entries_count*2 + 0] = entries_address[i].id();
      search[entries_count*2 + 1] = entries_count;
      entries_count++;
      AOTCodeEntry::Kind kind = entries_address[i].kind();
      if (kind == AOTCodeEntry::Adapter) {
        adapters_count++;
      } else if (kind == AOTCodeEntry::SharedBlob) {
        shared_blobs_count++;
      } else if (kind == AOTCodeEntry::StubGenBlob) {
        stubgen_blobs_count++;
      } else if (kind == AOTCodeEntry::C1Blob) {
        C1_blobs_count++;
      } else if (kind == AOTCodeEntry::C2Blob) {
        C2_blobs_count++;
      }
    }
    if (entries_count == 0) {
      log_info(aot, codecache, exit)("AOT Code Cache was not created: no entires");
      FREE_C_HEAP_ARRAY(uint, search);
      return true; // Nothing to write
    }
    assert(entries_count <= store_count, "%d > %d", entries_count, store_count);
    // Write strings
    if (strings_count > 0) {
      copy_bytes((_store_buffer + strings_offset), (address)current, strings_size);
      strings_offset = (current - start); // New offset
      current += strings_size;
    }

    uint new_entries_offset = (current - start); // New offset
    // Sort and store search table
    qsort(search, entries_count, 2*sizeof(uint), uint_cmp);
    search_size = 2 * entries_count * sizeof(uint);
    copy_bytes((const char*)search, (address)current, search_size);
    FREE_C_HEAP_ARRAY(uint, search);
    current += search_size;

    // Write entries
    entries_size = entries_count * sizeof(AOTCodeEntry); // New size
    copy_bytes((_store_buffer + entries_offset), (address)current, entries_size);
    current += entries_size;
    uint size = (current - start);
    assert(size <= total_size, "%d > %d", size , total_size);

    log_debug(aot, codecache, exit)("  Adapters:  total=%u", adapters_count);
    log_debug(aot, codecache, exit)("  Shared Blobs:  total=%d", shared_blobs_count);
    log_debug(aot, codecache, exit)("  StubGen Blobs:  total=%d", stubgen_blobs_count);
    log_debug(aot, codecache, exit)("  C1 Blobs:      total=%d", C1_blobs_count);
    log_debug(aot, codecache, exit)("  C2 Blobs:      total=%d", C2_blobs_count);
    log_debug(aot, codecache, exit)("  AOT code cache size: %u bytes, max entry's size: %u bytes", size, max_size);

    // Finalize header
    AOTCodeCache::Header* header = (AOTCodeCache::Header*)start;
    header->init(size, (uint)strings_count, strings_offset,
                 entries_count, new_entries_offset,
                 adapters_count, shared_blobs_count,
                 stubgen_blobs_count, C1_blobs_count,
                 C2_blobs_count);

    log_info(aot, codecache, exit)("Wrote %d AOT code entries to AOT Code Cache", entries_count);
  }
  return true;
}

//------------------Store/Load AOT code ----------------------

bool AOTCodeCache::store_code_blob(CodeBlob& blob, AOTCodeEntry::Kind entry_kind, uint id, const char* name, AOTStubData* stub_data, CodeBuffer* code_buffer) {
  assert(AOTCodeEntry::is_valid_entry_kind(entry_kind), "invalid entry_kind %d", entry_kind);

  // we only expect stub data and a code buffer for a multi stub blob
  assert(AOTCodeEntry::is_multi_stub_blob(entry_kind) == (stub_data != nullptr),
         "entry_kind %d does not match stub_data pointer %p",
         entry_kind, stub_data);

  assert((stub_data == nullptr) == (code_buffer == nullptr),
         "stub data and code buffer must both be null or both non null");

  // If this is a stub and the cache is on for either load or dump we
  // need to insert the stub entries into the AOTCacheAddressTable so
  // that relocs which refer to entries defined by this blob get
  // translated correctly.
  //
  // Entry insertion needs to be be done up front before writing the
  // blob because some blobs rely on internal daisy-chain references
  // from one entry to another.
  //
  // Entry insertion also needs to be done even if the cache is open
  // for use but not for dump. This may be needed when an archived
  // blob omits some entries -- either because of a config change or a
  // load failure -- with the result that the entries end up being
  // generated. These generated entry addresses may be needed to
  // resolve references from subsequently loaded blobs (for either
  // stubs or nmethods).

  if (is_on() && AOTCodeEntry::is_blob(entry_kind)) {
    publish_stub_addresses(blob, (BlobId)id, stub_data);
  }

  AOTCodeCache* cache = open_for_dump();
  if (cache == nullptr) {
    return false;
  }
  if (AOTCodeEntry::is_adapter(entry_kind) && !is_dumping_adapter()) {
    return false;
  }
  if (AOTCodeEntry::is_blob(entry_kind) && !is_dumping_stub()) {
    return false;
  }
  log_debug(aot, codecache, stubs)("Writing blob '%s' (id=%u, kind=%s) to AOT Code Cache", name, id, aot_code_entry_kind_name[entry_kind]);

#ifdef ASSERT
  LogStreamHandle(Trace, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    blob.print_on(&log);
  }
#endif
  // we need to take a lock to prevent race between compiler threads generating AOT code
  // and the main thread generating adapter
  MutexLocker ml(Compile_lock);
  if (!is_on()) {
    return false; // AOT code cache was already dumped and closed.
  }
  if (!cache->align_write()) {
    return false;
  }
  uint entry_position = cache->_write_position;

  // Write name
  uint name_offset = cache->_write_position - entry_position;
  uint name_size = (uint)strlen(name) + 1; // Includes '/0'
  uint n = cache->write_bytes(name, name_size);
  if (n != name_size) {
    return false;
  }

  // Write CodeBlob
  if (!cache->align_write()) {
    return false;
  }
  uint blob_offset = cache->_write_position - entry_position;
  address archive_buffer = cache->reserve_bytes(blob.size());
  if (archive_buffer == nullptr) {
    return false;
  }
  CodeBlob::archive_blob(&blob, archive_buffer);

  // For a relocatable code blob its relocations are linked from the
  // blob. However, for a non-relocatable (stubgen) blob we only have
  // transient relocations attached to the code buffer that are added
  // in order to support AOT-load time patching. in either case, we
  // need to explicitly save these relocs when storing the blob to the
  // archive so we can then reload them and reattach them to either
  // the blob or to a code buffer when we reload the blob into a
  // production JVM.
  //
  // Either way we are then in a position to iterate over the relocs
  // and AOT patch the ones that refer to code that may move between
  // assembly and production time. We also need to save and restore
  // AOT address table indexes for the target addresses of affected
  // relocs. That happens below.

  int reloc_count;
  address reloc_data;
  if (AOTCodeEntry::is_multi_stub_blob(entry_kind)) {
    CodeSection* cs = code_buffer->code_section(CodeBuffer::SECT_INSTS);
    reloc_count = (cs->has_locs() ? cs->locs_count() : 0);
    reloc_data = (reloc_count > 0 ? (address)cs->locs_start() : nullptr);
  } else {
    reloc_count = blob.relocation_size() / sizeof(relocInfo);
    reloc_data = (address)blob.relocation_begin();
  }
  n = cache->write_bytes(&reloc_count, sizeof(int));
  if (n != sizeof(int)) {
    return false;
  }
  if (AOTCodeEntry::is_multi_stub_blob(entry_kind)) {
    // align to heap word size before writing the relocs so we can
    // install them into a code buffer when they get restored
    if (!cache->align_write()) {
      return false;
    }
  }
  uint reloc_data_size = (uint)(reloc_count * sizeof(relocInfo));
  n = cache->write_bytes(reloc_data, reloc_data_size);
  if (n != reloc_data_size) {
    return false;
  }

  bool has_oop_maps = false;
  if (blob.oop_maps() != nullptr) {
    if (!cache->write_oop_map_set(blob)) {
      return false;
    }
    has_oop_maps = true;
  }

#ifndef PRODUCT
  // Write asm remarks
  if (!cache->write_asm_remarks(blob)) {
    return false;
  }
  if (!cache->write_dbg_strings(blob)) {
    return false;
  }
#endif /* PRODUCT */

  // In the case of a multi-stub blob we need to write start, end,
  // secondary entries and extras. For any other blob entry addresses
  // beyond the blob start will be stored in the blob as offsets.
  if (stub_data != nullptr) {
    if (!cache->write_stub_data(blob, stub_data)) {
      return false;
    }
  }

  // now we have added all the other data we can write the AOT
  // relocations

  bool write_ok;
  if (AOTCodeEntry::is_multi_stub_blob(entry_kind)) {
    CodeSection* cs = code_buffer->code_section(CodeBuffer::SECT_INSTS);
    RelocIterator iter(cs);
    write_ok = cache->write_relocations(blob, iter);
  } else {
    RelocIterator iter(&blob);
    write_ok = cache->write_relocations(blob, iter);
  }

  if (!write_ok) {
    if (!cache->failed()) {
      // We may miss an address in AOT table - skip this code blob.
      cache->set_write_position(entry_position);
    }
    return false;
  }

  uint entry_size = cache->_write_position - entry_position;

  AOTCodeEntry* entry = new(cache) AOTCodeEntry(entry_kind, encode_id(entry_kind, id),
                                                entry_position, entry_size, name_offset, name_size,
                                                blob_offset, has_oop_maps, blob.content_begin());
  log_debug(aot, codecache, stubs)("Wrote code blob '%s' (id=%u, kind=%s) to AOT Code Cache", name, id, aot_code_entry_kind_name[entry_kind]);
  return true;
}

bool AOTCodeCache::store_code_blob(CodeBlob& blob, AOTCodeEntry::Kind entry_kind, uint id, const char* name) {
  assert(!AOTCodeEntry::is_blob(entry_kind),
         "wrong entry kind for numeric id %d", id);
  return store_code_blob(blob, entry_kind, (uint)id, name, nullptr, nullptr);
}

bool AOTCodeCache::store_code_blob(CodeBlob& blob, AOTCodeEntry::Kind entry_kind, BlobId id) {
  assert(AOTCodeEntry::is_single_stub_blob(entry_kind),
         "wrong entry kind for blob id %s", StubInfo::name(id));
  return store_code_blob(blob, entry_kind, (uint)id, StubInfo::name(id), nullptr, nullptr);
}

bool AOTCodeCache::store_code_blob(CodeBlob& blob, AOTCodeEntry::Kind entry_kind, BlobId id, AOTStubData* stub_data, CodeBuffer* code_buffer) {
  assert(AOTCodeEntry::is_multi_stub_blob(entry_kind),
         "wrong entry kind for multi stub blob id %s", StubInfo::name(id));
  return store_code_blob(blob, entry_kind, (uint)id, StubInfo::name(id), stub_data, code_buffer);
}

bool AOTCodeCache::write_stub_data(CodeBlob &blob, AOTStubData *stub_data) {
  BlobId blob_id = stub_data->blob_id();
  StubId stub_id = StubInfo::stub_base(blob_id);
  address blob_base = blob.code_begin();
  int stub_cnt = StubInfo::stub_count(blob_id);
  int n;

  LogStreamHandle(Trace, aot, codecache, stubs) log;

  if (log.is_enabled()) {
    log.print_cr("======== Stub data starts at offset %d", _write_position);
  }

  for (int i = 0; i < stub_cnt; i++, stub_id = StubInfo::next_in_blob(blob_id, stub_id)) {
    // for each stub we find in the ranges list we write an int
    // sequence <stubid,start,end,N,offset1, ... offsetN> where
    //
    // - start_pos is the stub start address encoded as a code section offset
    //
    // - end is the stub end address encoded as an offset from start
    //
    // - N counts the number of stub-local entries/extras
    //
    // - offseti is a stub-local entry/extra address encoded as len for
    // a null address otherwise as an offset in range [1,len-1]

    StubAddrRange& range = stub_data->get_range(i);
    GrowableArray<address>& addresses = stub_data->address_array();
    int base = range.start_index();
    if (base >= 0) {
      n = write_bytes(&stub_id, sizeof(StubId));
      if (n != sizeof(StubId)) {
        return false;
      }
      address start = addresses.at(base);
      assert (blob_base <= start, "sanity");
      uint offset = (uint)(start - blob_base);
      n = write_bytes(&offset, sizeof(uint));
      if (n != sizeof(int)) {
        return false;
      }
      address end = addresses.at(base + 1);
      assert (start < end, "sanity");
      offset = (uint)(end - start);
      n = write_bytes(&offset, sizeof(uint));
      if (n != sizeof(int)) {
        return false;
      }
      // write number of secondary and extra entries
      int count =  range.count() - 2;
      n = write_bytes(&count, sizeof(int));
      if (n != sizeof(int)) {
        return false;
      }
      for (int j = 0; j < count; j++) {
        address next = addresses.at(base + 2 + j);
        if (next != nullptr) {
          // n.b. This maps next == end to the stub length which
          // means we will reconstitute the address as nullptr. That
          // happens when we have a handler range covers the end of
          // a stub and needs to be handled specially by the client
          // that restores the extras.
          assert(start <= next && next <= end, "sanity");
          offset = (uint)(next - start);
        } else {
          // this can happen when a stub is not generated or an
          // extra is the common handler target
          offset = (uint)(end - start);
        }
        n = write_bytes(&offset, sizeof(uint));
        if (n != sizeof(int)) {
          return false;
        }
      }
      if (log.is_enabled()) {
        log.print_cr("======== wrote stub %s and %d addresses up to offset %d",
                     StubInfo::name(stub_id), range.count(), _write_position);
      }
    }
  }
  // we should have exhausted all stub ids in the blob
  assert(stub_id == StubId::NO_STUBID, "sanity");
  // write NO_STUBID as an end marker
  n = write_bytes(&stub_id, sizeof(StubId));
  if (n != sizeof(StubId)) {
    return false;
  }

  if (log.is_enabled()) {
    log.print_cr("======== Stub data ends at offset %d", _write_position);
  }

  return true;
}

CodeBlob* AOTCodeCache::load_code_blob(AOTCodeEntry::Kind entry_kind, uint id, const char* name, AOTStubData* stub_data) {
  AOTCodeCache* cache = open_for_use();
  if (cache == nullptr) {
    return nullptr;
  }
  assert(AOTCodeEntry::is_valid_entry_kind(entry_kind), "invalid entry_kind %d", entry_kind);

  assert(AOTCodeEntry::is_multi_stub_blob(entry_kind) == (stub_data != nullptr),
         "entry_kind %d does not match stub_data pointer %p",
         entry_kind, stub_data);

  if (AOTCodeEntry::is_adapter(entry_kind) && !is_using_adapter()) {
    return nullptr;
  }
  if (AOTCodeEntry::is_blob(entry_kind) && !is_using_stub()) {
    return nullptr;
  }
  log_debug(aot, codecache, stubs)("Reading blob '%s' (id=%u, kind=%s) from AOT Code Cache", name, id, aot_code_entry_kind_name[entry_kind]);

  AOTCodeEntry* entry = cache->find_entry(entry_kind, encode_id(entry_kind, id));
  if (entry == nullptr) {
    return nullptr;
  }
  AOTCodeReader reader(cache, entry);
  CodeBlob* blob = reader.compile_code_blob(name, entry_kind, id, stub_data);

  log_debug(aot, codecache, stubs)("%sRead blob '%s' (id=%u, kind=%s) from AOT Code Cache",
                                   (blob == nullptr? "Failed to " : ""), name, id, aot_code_entry_kind_name[entry_kind]);
  return blob;
}

CodeBlob* AOTCodeCache::load_code_blob(AOTCodeEntry::Kind entry_kind, uint id, const char* name) {
  assert(!AOTCodeEntry::is_blob(entry_kind),
         "wrong entry kind for numeric id %d", id);
  return load_code_blob(entry_kind, (uint)id, name, nullptr);
}

CodeBlob* AOTCodeCache::load_code_blob(AOTCodeEntry::Kind entry_kind, BlobId id) {
  assert(AOTCodeEntry::is_single_stub_blob(entry_kind),
         "wrong entry kind for blob id %s", StubInfo::name(id));
  return load_code_blob(entry_kind, (uint)id, StubInfo::name(id), nullptr);
}

CodeBlob* AOTCodeCache::load_code_blob(AOTCodeEntry::Kind entry_kind, BlobId id, AOTStubData* stub_data) {
  assert(AOTCodeEntry::is_multi_stub_blob(entry_kind),
         "wrong entry kind for blob id %s", StubInfo::name(id));
  return load_code_blob(entry_kind, (uint)id, StubInfo::name(id), stub_data);
}

CodeBlob* AOTCodeReader::compile_code_blob(const char* name, AOTCodeEntry::Kind entry_kind, int id, AOTStubData* stub_data) {
  uint entry_position = _entry->offset();

  // Read name
  uint name_offset = entry_position + _entry->name_offset();
  uint name_size = _entry->name_size(); // Includes '/0'
  const char* stored_name = addr(name_offset);

  if (strncmp(stored_name, name, (name_size - 1)) != 0) {
    log_warning(aot, codecache, stubs)("Saved blob's name '%s' is different from the expected name '%s'",
                                       stored_name, name);
    set_lookup_failed(); // Skip this blob
    return nullptr;
  }

  // Read archived code blob and related info
  uint offset = entry_position + _entry->blob_offset();
  CodeBlob* archived_blob = (CodeBlob*)addr(offset);
  offset += archived_blob->size();

  int reloc_count = *(int*)addr(offset); offset += sizeof(int);
  if (AOTCodeEntry::is_multi_stub_blob(entry_kind)) {
    // position of relocs will have been aligned to heap word size so
    // we can install them into a code buffer
    offset = align_up(offset, DATA_ALIGNMENT);
  }
  address reloc_data = (address)addr(offset);
  offset += reloc_count * sizeof(relocInfo);
  set_read_position(offset);

  ImmutableOopMapSet* oop_maps = nullptr;
  if (_entry->has_oop_maps()) {
    oop_maps = read_oop_map_set();
  }

  // Note that for a non-relocatable blob reloc_data will not be
  // restored into the blob. We fix that later.

  CodeBlob* code_blob = CodeBlob::create(archived_blob,
                                         stored_name,
                                         reloc_data,
                                         oop_maps);
  if (code_blob == nullptr) { // no space left in CodeCache
    return nullptr;
  }

#ifndef PRODUCT
  code_blob->asm_remarks().init();
  read_asm_remarks(code_blob->asm_remarks());
  code_blob->dbg_strings().init();
  read_dbg_strings(code_blob->dbg_strings());
#endif // PRODUCT

  if (AOTCodeEntry::is_blob(entry_kind)) {
    BlobId blob_id = static_cast<BlobId>(id);
    if (StubInfo::is_stubgen(blob_id)) {
      assert(stub_data != nullptr, "sanity");
      read_stub_data(code_blob, stub_data);
    }
    // publish entries found either in stub_data or as offsets in blob
    AOTCodeCache::publish_stub_addresses(*code_blob, blob_id, stub_data);
  }

  // Now that all the entry points are in the address table we can
  // read all the extra reloc info and fix up any addresses that need
  // patching to adjust for a new location in a new JVM. We can be
  // sure to correctly update all runtime references, including
  // cross-linked stubs that are internally daisy-chained. If
  // relocation fails and we have to re-generate any of the stubs then
  // the entry points for newly generated stubs will get updated,
  // ensuring that any other stubs or nmethods we need to relocate
  // will use the correct address.

  // if we have a relocatable code blob then the relocs are already
  // attached to the blob and we can iterate over it to find the ones
  // we need to patch. With a non-relocatable code blob we need to
  // wrap it with a CodeBuffer and then reattach the relocs to the
  // code buffer.

  if (AOTCodeEntry::is_multi_stub_blob(entry_kind)) {
    // the blob doesn't have any proper runtime relocs but we can
    // reinstate the AOT-load time relocs we saved from the code
    // buffer that generated this blob in a new code buffer and use
    // the latter to iterate over them
    CodeBuffer code_buffer(code_blob);
    relocInfo* locs = (relocInfo*)reloc_data;
    code_buffer.insts()->initialize_shared_locs(locs, reloc_count);
    code_buffer.insts()->set_locs_end(locs + reloc_count);
    CodeSection *cs = code_buffer.code_section(CodeBuffer::SECT_INSTS);
    RelocIterator reloc_iter(cs);
    fix_relocations(code_blob, reloc_iter);
  } else {
    // the AOT-load time relocs will be in the blob's restored relocs
    RelocIterator reloc_iter(code_blob);
    fix_relocations(code_blob, reloc_iter);
  }

#ifdef ASSERT
  LogStreamHandle(Trace, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    code_blob->print_on(&log);
  }
#endif
  return code_blob;
}

void AOTCodeReader::read_stub_data(CodeBlob* code_blob, AOTStubData* stub_data) {
  GrowableArray<address>& addresses = stub_data->address_array();
  // Read the list of stub ids and associated start, end, secondary
  // and extra addresses and install them in the stub data.
  //
  // Also insert all start and secondary addresses into the AOTCache
  // address table so we correctly relocate this blob and any followng
  // blobs/nmethods.
  //
  // n.b. if an error occurs and we need to regenerate any of these
  // stubs the address table will be updated as a side-effect of
  // regeneration.

  address blob_base = code_blob->code_begin();
  uint blob_size = (uint)(code_blob->code_end() - blob_base);
  int offset = read_position();
  LogStreamHandle(Trace, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    log.print_cr("======== Stub data starts at offset %d", offset);
  }
  // read stub and entries until we see NO_STUBID
  StubId stub_id = *(StubId*)addr(offset); offset += sizeof(StubId);
  // we ought to have at least one saved stub in the blob
  assert(stub_id != StubId::NO_STUBID, "blob %s contains no stubs!", StubInfo::name(stub_data->blob_id()));
  while (stub_id != StubId::NO_STUBID) {
    assert(StubInfo::blob(stub_id) == stub_data->blob_id(), "sanity");
    int idx = StubInfo::stubgen_offset_in_blob(stub_data->blob_id(), stub_id);
    StubAddrRange& range = stub_data->get_range(idx);
    // we should only see a stub once
    assert(range.start_index() < 0, "repeated entry for stub %s", StubInfo::name(stub_id));
    int address_base = addresses.length();
    // start is an offset from the blob base
    uint start = *(uint*)addr(offset); offset += sizeof(uint);
    assert(start < blob_size, "stub %s start offset %d exceeds buffer length %d", StubInfo::name(stub_id), start, blob_size);
    address stub_start = blob_base + start;
    addresses.append(stub_start);
    // end is an offset from the stub start
    uint end = *(uint*)addr(offset); offset += sizeof(uint);
    assert(start + end <= blob_size, "stub %s end offset %d exceeds remaining buffer length %d", StubInfo::name(stub_id), end, blob_size - start);
    addresses.append(stub_start + end);
    // read count of secondary entries plus extras
    int entries_count = *(int*)addr(offset); offset += sizeof(int);
    assert(entries_count >= (StubInfo::entry_count(stub_id) - 1), "not enough entries for %s", StubInfo::name(stub_id));
    for (int i = 0; i < entries_count; i++) {
      // entry offset is an offset from the stub start less than or
      // equal to end
      uint entry = *(uint*)addr(offset); offset += sizeof(uint);
      assert(entry <= end, "stub %s entry offset %d lies beyond stub end %d", StubInfo::name(stub_id), entry, end);
      if (entry < end) {
        addresses.append(stub_start + entry);
      } else {
        // entry offset == end encodes a nullptr
        addresses.append(nullptr);
      }
    }
    if (log.is_enabled()) {
      log.print_cr("======== read stub %s and %d addresses up to offset %d",
                   StubInfo::name(stub_id),  2 + entries_count, offset);
    }
    range.init_entry(address_base, 2 + entries_count);
    // move on to next stub or NO_STUBID
    stub_id = *(StubId*)addr(offset); offset += sizeof(StubId);
  }
  if (log.is_enabled()) {
    log.print_cr("======== Stub data ends at offset %d", offset);
  }

  set_read_position(offset);
}

void AOTCodeCache::publish_external_addresses(GrowableArray<address>& addresses) {
  DEBUG_ONLY( _passed_init2 = true; )
  if (opened_cache == nullptr) {
    return;
  }

  cache()->_table->add_external_addresses(addresses);
}

void AOTCodeCache::publish_stub_addresses(CodeBlob &code_blob, BlobId blob_id, AOTStubData *stub_data) {
  if (stub_data != nullptr) {
    // register all entries in stub
    assert(StubInfo::stub_count(blob_id) > 1,
           "multiple stub data provided for single stub blob %s",
           StubInfo::name(blob_id));
    assert(blob_id == stub_data->blob_id(),
           "blob id %s does not match id in stub data %s",
           StubInfo::name(blob_id),
           StubInfo::name(stub_data->blob_id()));
    // iterate over all stubs in the blob
    StubId stub_id = StubInfo::stub_base(blob_id);
    int stub_cnt = StubInfo::stub_count(blob_id);
    GrowableArray<address>& addresses = stub_data->address_array();
    for (int i = 0; i < stub_cnt; i++) {
      assert(stub_id != StubId::NO_STUBID, "sanity");
      StubAddrRange& range = stub_data->get_range(i);
      int base = range.start_index();
      if (base >= 0) {
        cache()->add_stub_entries(stub_id, addresses.at(base), &addresses, base + 2);
      }
      stub_id = StubInfo::next_in_blob(blob_id, stub_id);
    }
    // we should have exhausted all stub ids in the blob
    assert(stub_id == StubId::NO_STUBID, "sanity");
  } else {
    // register entry or entries for a single stub blob
    StubId stub_id = StubInfo::stub_base(blob_id);
    assert(StubInfo::stub_count(blob_id) == 1,
           "multiple stub blob %s provided without stub data",
           StubInfo::name(blob_id));
    address start = code_blob.code_begin();
    if (StubInfo::entry_count(stub_id) == 1) {
      assert(!code_blob.is_deoptimization_stub(), "expecting multiple entries for stub %s", StubInfo::name(stub_id));
      // register the blob base address as the only entry
      cache()->add_stub_entries(stub_id, start);
    } else {
      assert(code_blob.is_deoptimization_stub(), "only expecting one entry for stub %s", StubInfo::name(stub_id));
      DeoptimizationBlob *deopt_blob = code_blob.as_deoptimization_blob();
      assert(deopt_blob->unpack() == start, "unexpected offset 0x%lx for deopt stub entry", deopt_blob->unpack() - start);
      GrowableArray<address> addresses;
      addresses.append(deopt_blob->unpack_with_exception());
      addresses.append(deopt_blob->unpack_with_reexecution());
      addresses.append(deopt_blob->unpack_with_exception_in_tls());
#if INCLUDE_JVMCI
      addresses.append(deopt_blob->uncommon_trap());
      addresses.append(deopt_blob->implicit_exception_uncommon_trap());
#endif // INCLUDE_JVMCI
      cache()->add_stub_entries(stub_id, start, &addresses, 0);
    }
  }
}

  // ------------ process code and data --------------

// Can't use -1. It is valid value for jump to iteself destination
// used by static call stub: see NativeJump::jump_destination().
#define BAD_ADDRESS_ID -2

bool AOTCodeCache::write_relocations(CodeBlob& code_blob, RelocIterator& iter) {
  GrowableArray<uint> reloc_data;
  LogStreamHandle(Trace, aot, codecache, reloc) log;
  while (iter.next()) {
    int idx = reloc_data.append(0); // default value
    switch (iter.type()) {
      case relocInfo::none:
        break;
      case relocInfo::runtime_call_type: {
        // Record offset of runtime destination
        CallRelocation* r = (CallRelocation*)iter.reloc();
        address dest = r->destination();
        if (dest == r->addr()) { // possible call via trampoline on Aarch64
          dest = (address)-1;    // do nothing in this case when loading this relocation
        }
        int id = _table->id_for_address(dest, iter, &code_blob);
        if (id == BAD_ADDRESS_ID) {
          return false;
        }
        reloc_data.at_put(idx, id);
        break;
      }
      case relocInfo::runtime_call_w_cp_type:
        log_debug(aot, codecache, reloc)("runtime_call_w_cp_type relocation is not implemented");
        return false;
      case relocInfo::external_word_type: {
        // Record offset of runtime target
        address target = ((external_word_Relocation*)iter.reloc())->target();
        int id = _table->id_for_address(target, iter, &code_blob);
        if (id == BAD_ADDRESS_ID) {
          return false;
        }
        reloc_data.at_put(idx, id);
        break;
      }
      case relocInfo::internal_word_type:
        break;
      case relocInfo::section_word_type:
        break;
      case relocInfo::post_call_nop_type:
        break;
      default:
        log_debug(aot, codecache, reloc)("relocation %d unimplemented", (int)iter.type());
        return false;
        break;
    }
    if (log.is_enabled()) {
      iter.print_current_on(&log);
    }
  }

  // Write additional relocation data: uint per relocation
  // Write the count first
  int count = reloc_data.length();
  write_bytes(&count, sizeof(int));
  if (log.is_enabled()) {
    log.print_cr("======== extra relocations count=%d", count);
    log.print(   "  {");
  }
  bool first = true;
  for (GrowableArrayIterator<uint> iter = reloc_data.begin();
       iter != reloc_data.end(); ++iter) {
    uint value = *iter;
    int n = write_bytes(&value, sizeof(uint));
    if (n != sizeof(uint)) {
      return false;
    }
    if (log.is_enabled()) {
      if (first) {
        first = false;
        log.print("%d", value);
      } else {
        log.print(", %d", value);
      }
    }
  }
  log.print_cr("}");
  return true;
}

void AOTCodeReader::fix_relocations(CodeBlob *code_blob, RelocIterator& iter) {
  uint offset = read_position();
  int reloc_count = *(int*)addr(offset);
  offset += sizeof(int);
  uint* reloc_data = (uint*)addr(offset);
  offset += (reloc_count * sizeof(uint));
  set_read_position(offset);

  LogStreamHandle(Trace, aot, codecache, reloc) log;
  if (log.is_enabled()) {
    log.print_cr("======== extra relocations count=%d", reloc_count);
  }
  if (log.is_enabled()) {
    log.print("  {");
    for(int i = 0; i < reloc_count; i++) {
      if (i == 0) {
        log.print("%d", reloc_data[i]);
      } else {
        log.print(", %d", reloc_data[i]);
      }
    }
    log.print_cr("}");
  }

  int j = 0;
  while (iter.next()) {
    switch (iter.type()) {
      case relocInfo::none:
        break;
      case relocInfo::runtime_call_type: {
        address dest = _cache->address_for_id(reloc_data[j]);
        if (dest != (address)-1) {
          ((CallRelocation*)iter.reloc())->set_destination(dest);
        }
        break;
      }
      case relocInfo::runtime_call_w_cp_type:
        // this relocation should not be in cache (see write_relocations)
        assert(false, "runtime_call_w_cp_type relocation is not implemented");
        break;
      case relocInfo::external_word_type: {
        address target = _cache->address_for_id(reloc_data[j]);
        // Add external address to global table
        int index = ExternalsRecorder::find_index(target);
        // Update index in relocation
        Relocation::add_jint(iter.data(), index);
        external_word_Relocation* reloc = (external_word_Relocation*)iter.reloc();
        assert(reloc->target() == target, "sanity");
        reloc->set_value(target); // Patch address in the code
        break;
      }
      case relocInfo::internal_word_type: {
        internal_word_Relocation* r = (internal_word_Relocation*)iter.reloc();
        r->fix_relocation_after_aot_load(aot_code_entry()->dumptime_content_start_addr(), code_blob->content_begin());
        break;
      }
      case relocInfo::section_word_type: {
        section_word_Relocation* r = (section_word_Relocation*)iter.reloc();
        r->fix_relocation_after_aot_load(aot_code_entry()->dumptime_content_start_addr(), code_blob->content_begin());
        break;
      }
      case relocInfo::post_call_nop_type:
        break;
      default:
        assert(false,"relocation %d unimplemented", (int)iter.type());
        break;
    }
    if (log.is_enabled()) {
      iter.print_current_on(&log);
    }
    j++;
  }
  assert(j == reloc_count, "sanity");
}

bool AOTCodeCache::write_oop_map_set(CodeBlob& cb) {
  ImmutableOopMapSet* oopmaps = cb.oop_maps();
  int oopmaps_size = oopmaps->nr_of_bytes();
  if (!write_bytes(&oopmaps_size, sizeof(int))) {
    return false;
  }
  uint n = write_bytes(oopmaps, oopmaps->nr_of_bytes());
  if (n != (uint)oopmaps->nr_of_bytes()) {
    return false;
  }
  return true;
}

ImmutableOopMapSet* AOTCodeReader::read_oop_map_set() {
  uint offset = read_position();
  int size = *(int *)addr(offset);
  offset += sizeof(int);
  ImmutableOopMapSet* oopmaps = (ImmutableOopMapSet *)addr(offset);
  offset += size;
  set_read_position(offset);
  return oopmaps;
}

#ifndef PRODUCT
bool AOTCodeCache::write_asm_remarks(CodeBlob& cb) {
  // Write asm remarks
  uint* count_ptr = (uint *)reserve_bytes(sizeof(uint));
  if (count_ptr == nullptr) {
    return false;
  }
  uint count = 0;
  bool result = cb.asm_remarks().iterate([&] (uint offset, const char* str) -> bool {
    log_trace(aot, codecache, stubs)("asm remark offset=%d, str='%s'", offset, str);
    uint n = write_bytes(&offset, sizeof(uint));
    if (n != sizeof(uint)) {
      return false;
    }
    const char* cstr = add_C_string(str);
    int id = _table->id_for_C_string((address)cstr);
    assert(id != -1, "asm remark string '%s' not found in AOTCodeAddressTable", str);
    n = write_bytes(&id, sizeof(int));
    if (n != sizeof(int)) {
      return false;
    }
    count += 1;
    return true;
  });
  *count_ptr = count;
  return result;
}

void AOTCodeReader::read_asm_remarks(AsmRemarks& asm_remarks) {
  // Read asm remarks
  uint offset = read_position();
  uint count = *(uint *)addr(offset);
  offset += sizeof(uint);
  for (uint i = 0; i < count; i++) {
    uint remark_offset = *(uint *)addr(offset);
    offset += sizeof(uint);
    int remark_string_id = *(uint *)addr(offset);
    offset += sizeof(int);
    const char* remark = (const char*)_cache->address_for_C_string(remark_string_id);
    asm_remarks.insert(remark_offset, remark);
  }
  set_read_position(offset);
}

bool AOTCodeCache::write_dbg_strings(CodeBlob& cb) {
  // Write dbg strings
  uint* count_ptr = (uint *)reserve_bytes(sizeof(uint));
  if (count_ptr == nullptr) {
    return false;
  }
  uint count = 0;
  bool result = cb.dbg_strings().iterate([&] (const char* str) -> bool {
    log_trace(aot, codecache, stubs)("dbg string=%s", str);
    const char* cstr = add_C_string(str);
    int id = _table->id_for_C_string((address)cstr);
    assert(id != -1, "db string '%s' not found in AOTCodeAddressTable", str);
    uint n = write_bytes(&id, sizeof(int));
    if (n != sizeof(int)) {
      return false;
    }
    count += 1;
    return true;
  });
  *count_ptr = count;
  return result;
}

void AOTCodeReader::read_dbg_strings(DbgStrings& dbg_strings) {
  // Read dbg strings
  uint offset = read_position();
  uint count = *(uint *)addr(offset);
  offset += sizeof(uint);
  for (uint i = 0; i < count; i++) {
    int string_id = *(uint *)addr(offset);
    offset += sizeof(int);
    const char* str = (const char*)_cache->address_for_C_string(string_id);
    dbg_strings.insert(str);
  }
  set_read_position(offset);
}
#endif // PRODUCT

//======================= AOTCodeAddressTable ===============

// address table ids for generated routine entry adresses, external
// addresses and C string addresses are partitioned into positive
// integer ranges defined by the following positive base and max
// values i.e. [_extrs_base, _extrs_base + _extrs_max -1],
// [_stubs_base, _stubs_base + _stubs_max -1], [_c_str_base,
// _c_str_base + _c_str_max -1],

#define _extrs_max 200
#define _stubs_max static_cast<int>(EntryId::NUM_ENTRYIDS)

#define _extrs_base 0
#define _stubs_base (_extrs_base + _extrs_max)
#define _all_max    (_stubs_base + _stubs_max)

// setter for external addresses and string addresses inserts new
// addresses in the order they are encountered them which must remain
// th esame across an assembly run and subsequent production run

#define SET_ADDRESS(type, addr)                           \
  {                                                       \
    type##_addr[type##_length++] = (address) (addr);      \
    assert(type##_length <= type##_max, "increase size"); \
  }

// setter for stub entry addresses inserts them using the stub entry
// id as an index

#define SET_ENTRY_ADDRESS(type, addr, entry_id)           \
  {                                                       \
    int idx = static_cast<int>(entry);                    \
    _stub_addr[idx] = (address) (addr);                   \
  }

static bool initializing_extrs = false;

void AOTCodeAddressTable::init_extrs() {
  if (_extrs_complete || initializing_extrs) return; // Done already

  initializing_extrs = true;
  _extrs_addr = NEW_C_HEAP_ARRAY(address, _extrs_max, mtCode);

  _extrs_length = 0;

  {
    // Required by initial stubs
    SET_ADDRESS(_extrs, SharedRuntime::exception_handler_for_return_address); // used by forward_exception
#if defined(AMD64) || defined(AARCH64) || defined(RISCV64)
    SET_ADDRESS(_extrs, MacroAssembler::debug64);  // used by many eg forward_exception, call_stub
#endif // defined(AMD64) || defined(AARCH64) || defined(RISCV64)
#if defined(AMD64)
    SET_ADDRESS(_extrs, StubRoutines::x86::addr_mxcsr_std()); // used by call_stub
    SET_ADDRESS(_extrs, StubRoutines::x86::addr_mxcsr_rz()); // used by libmFmod
#endif
    SET_ADDRESS(_extrs, CompressedOops::base_addr()); // used by call_stub
    SET_ADDRESS(_extrs, Thread::current); // used by call_stub
    SET_ADDRESS(_extrs, SharedRuntime::throw_StackOverflowError);
    SET_ADDRESS(_extrs, SharedRuntime::throw_delayed_StackOverflowError);
  }

  // Record addresses of VM runtime methods
  SET_ADDRESS(_extrs, SharedRuntime::fixup_callers_callsite);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method_abstract);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method_ic_miss);
#if defined(AARCH64) && !defined(ZERO)
  SET_ADDRESS(_extrs, JavaThread::aarch64_get_thread_helper);
#endif

#ifndef PRODUCT
  SET_ADDRESS(_extrs, &SharedRuntime::_jbyte_array_copy_ctr); // used by arraycopy stub on arm32 and x86_64
  SET_ADDRESS(_extrs, &SharedRuntime::_jshort_array_copy_ctr); // used by arraycopy stub
  SET_ADDRESS(_extrs, &SharedRuntime::_jint_array_copy_ctr); // used by arraycopy stub
  SET_ADDRESS(_extrs, &SharedRuntime::_jlong_array_copy_ctr); // used by arraycopy stub
  SET_ADDRESS(_extrs, &SharedRuntime::_oop_array_copy_ctr); // used by arraycopy stub
  SET_ADDRESS(_extrs, &SharedRuntime::_checkcast_array_copy_ctr); // used by arraycopy stub
  SET_ADDRESS(_extrs, &SharedRuntime::_unsafe_array_copy_ctr); // used by arraycopy stub
  SET_ADDRESS(_extrs, &SharedRuntime::_generic_array_copy_ctr); // used by arraycopy stub
  SET_ADDRESS(_extrs, &SharedRuntime::_unsafe_set_memory_ctr); // used by arraycopy stub
#endif /* PRODUCT */

  SET_ADDRESS(_extrs, SharedRuntime::enable_stack_reserved_zone);

#ifdef AMD64
  SET_ADDRESS(_extrs, SharedRuntime::montgomery_multiply);
  SET_ADDRESS(_extrs, SharedRuntime::montgomery_square);
#endif // AMD64

  SET_ADDRESS(_extrs, SharedRuntime::d2f);
  SET_ADDRESS(_extrs, SharedRuntime::d2i);
  SET_ADDRESS(_extrs, SharedRuntime::d2l);
  SET_ADDRESS(_extrs, SharedRuntime::dcos);
  SET_ADDRESS(_extrs, SharedRuntime::dexp);
  SET_ADDRESS(_extrs, SharedRuntime::dlog);
  SET_ADDRESS(_extrs, SharedRuntime::dlog10);
  SET_ADDRESS(_extrs, SharedRuntime::dpow);
  SET_ADDRESS(_extrs, SharedRuntime::drem);
  SET_ADDRESS(_extrs, SharedRuntime::dsin);
  SET_ADDRESS(_extrs, SharedRuntime::dtan);
  SET_ADDRESS(_extrs, SharedRuntime::f2i);
  SET_ADDRESS(_extrs, SharedRuntime::f2l);
  SET_ADDRESS(_extrs, SharedRuntime::frem);
  SET_ADDRESS(_extrs, SharedRuntime::l2d);
  SET_ADDRESS(_extrs, SharedRuntime::l2f);
  SET_ADDRESS(_extrs, SharedRuntime::ldiv);
  SET_ADDRESS(_extrs, SharedRuntime::lmul);
  SET_ADDRESS(_extrs, SharedRuntime::lrem);

#if INCLUDE_JVMTI
  SET_ADDRESS(_extrs, &JvmtiExport::_should_notify_object_alloc);
#endif /* INCLUDE_JVMTI */

  SET_ADDRESS(_extrs, SafepointSynchronize::handle_polling_page_exception);

  SET_ADDRESS(_extrs, ThreadIdentifier::unsafe_offset());
  SET_ADDRESS(_extrs, Thread::current);

  SET_ADDRESS(_extrs, os::javaTimeMillis);
  SET_ADDRESS(_extrs, os::javaTimeNanos);
#ifndef PRODUCT
  SET_ADDRESS(_extrs, os::breakpoint);
#endif

#if INCLUDE_JVMTI
  SET_ADDRESS(_extrs, &JvmtiVTMSTransitionDisabler::_VTMS_notify_jvmti_events);
#endif /* INCLUDE_JVMTI */
  SET_ADDRESS(_extrs, StubRoutines::crc_table_addr());
#if defined(AARCH64)
  SET_ADDRESS(_extrs, JavaThread::aarch64_get_thread_helper);
#endif
#ifndef PRODUCT
  SET_ADDRESS(_extrs, &SharedRuntime::_partial_subtype_ctr);
  SET_ADDRESS(_extrs, JavaThread::verify_cross_modify_fence_failure);
#endif

#if defined(AMD64) || defined(AARCH64) || defined(RISCV64)
  SET_ADDRESS(_extrs, MacroAssembler::debug64);
#endif
#if defined(AMD64)
  SET_ADDRESS(_extrs, StubRoutines::x86::arrays_hashcode_powers_of_31());
#endif

#ifdef X86
  SET_ADDRESS(_extrs, LIR_Assembler::float_signmask_pool);
  SET_ADDRESS(_extrs, LIR_Assembler::double_signmask_pool);
  SET_ADDRESS(_extrs, LIR_Assembler::float_signflip_pool);
  SET_ADDRESS(_extrs, LIR_Assembler::double_signflip_pool);
#endif

  SET_ADDRESS(_extrs, JfrIntrinsicSupport::write_checkpoint);
  SET_ADDRESS(_extrs, JfrIntrinsicSupport::return_lease);


  SET_ADDRESS(_extrs, UpcallLinker::handle_uncaught_exception); // used by upcall_stub_exception_handler

  {
    // Required by Shared blobs
    SET_ADDRESS(_extrs, Deoptimization::fetch_unroll_info);
    SET_ADDRESS(_extrs, Deoptimization::unpack_frames);
    SET_ADDRESS(_extrs, SafepointSynchronize::handle_polling_page_exception);
    SET_ADDRESS(_extrs, SharedRuntime::resolve_opt_virtual_call_C);
    SET_ADDRESS(_extrs, SharedRuntime::resolve_virtual_call_C);
    SET_ADDRESS(_extrs, SharedRuntime::resolve_static_call_C);
    SET_ADDRESS(_extrs, SharedRuntime::throw_StackOverflowError);
    SET_ADDRESS(_extrs, SharedRuntime::throw_delayed_StackOverflowError);
    SET_ADDRESS(_extrs, SharedRuntime::throw_AbstractMethodError);
    SET_ADDRESS(_extrs, SharedRuntime::throw_IncompatibleClassChangeError);
    SET_ADDRESS(_extrs, SharedRuntime::throw_NullPointerException_at_call);
  }

#ifdef COMPILER1
  {
    // Required by C1 blobs
    SET_ADDRESS(_extrs, static_cast<int (*)(oopDesc*)>(SharedRuntime::dtrace_object_alloc));
    SET_ADDRESS(_extrs, SharedRuntime::exception_handler_for_return_address);
    SET_ADDRESS(_extrs, SharedRuntime::register_finalizer);
    SET_ADDRESS(_extrs, Runtime1::is_instance_of);
    SET_ADDRESS(_extrs, Runtime1::exception_handler_for_pc);
    SET_ADDRESS(_extrs, Runtime1::check_abort_on_vm_exception);
    SET_ADDRESS(_extrs, Runtime1::new_instance);
    SET_ADDRESS(_extrs, Runtime1::counter_overflow);
    SET_ADDRESS(_extrs, Runtime1::new_type_array);
    SET_ADDRESS(_extrs, Runtime1::new_object_array);
    SET_ADDRESS(_extrs, Runtime1::new_multi_array);
    SET_ADDRESS(_extrs, Runtime1::throw_range_check_exception);
    SET_ADDRESS(_extrs, Runtime1::throw_index_exception);
    SET_ADDRESS(_extrs, Runtime1::throw_div0_exception);
    SET_ADDRESS(_extrs, Runtime1::throw_null_pointer_exception);
    SET_ADDRESS(_extrs, Runtime1::throw_array_store_exception);
    SET_ADDRESS(_extrs, Runtime1::throw_class_cast_exception);
    SET_ADDRESS(_extrs, Runtime1::throw_incompatible_class_change_error);
    SET_ADDRESS(_extrs, Runtime1::is_instance_of);
    SET_ADDRESS(_extrs, Runtime1::monitorenter);
    SET_ADDRESS(_extrs, Runtime1::monitorexit);
    SET_ADDRESS(_extrs, Runtime1::deoptimize);
    SET_ADDRESS(_extrs, Runtime1::access_field_patching);
    SET_ADDRESS(_extrs, Runtime1::move_klass_patching);
    SET_ADDRESS(_extrs, Runtime1::move_mirror_patching);
    SET_ADDRESS(_extrs, Runtime1::move_appendix_patching);
    SET_ADDRESS(_extrs, Runtime1::predicate_failed_trap);
    SET_ADDRESS(_extrs, Runtime1::unimplemented_entry);
    SET_ADDRESS(_extrs, Thread::current);
    SET_ADDRESS(_extrs, CompressedKlassPointers::base_addr());
#ifndef PRODUCT
    SET_ADDRESS(_extrs, os::breakpoint);
#endif
  }
#endif

#ifdef COMPILER2
  {
    // Required by C2 blobs
    SET_ADDRESS(_extrs, Deoptimization::uncommon_trap);
    SET_ADDRESS(_extrs, OptoRuntime::handle_exception_C);
    SET_ADDRESS(_extrs, OptoRuntime::new_instance_C);
    SET_ADDRESS(_extrs, OptoRuntime::new_array_C);
    SET_ADDRESS(_extrs, OptoRuntime::new_array_nozero_C);
    SET_ADDRESS(_extrs, OptoRuntime::multianewarray2_C);
    SET_ADDRESS(_extrs, OptoRuntime::multianewarray3_C);
    SET_ADDRESS(_extrs, OptoRuntime::multianewarray4_C);
    SET_ADDRESS(_extrs, OptoRuntime::multianewarray5_C);
    SET_ADDRESS(_extrs, OptoRuntime::multianewarrayN_C);
#if INCLUDE_JVMTI
    SET_ADDRESS(_extrs, SharedRuntime::notify_jvmti_vthread_start);
    SET_ADDRESS(_extrs, SharedRuntime::notify_jvmti_vthread_end);
    SET_ADDRESS(_extrs, SharedRuntime::notify_jvmti_vthread_mount);
    SET_ADDRESS(_extrs, SharedRuntime::notify_jvmti_vthread_unmount);
#endif
    SET_ADDRESS(_extrs, OptoRuntime::complete_monitor_locking_C);
    SET_ADDRESS(_extrs, OptoRuntime::monitor_notify_C);
    SET_ADDRESS(_extrs, OptoRuntime::monitor_notifyAll_C);
    SET_ADDRESS(_extrs, OptoRuntime::rethrow_C);
    SET_ADDRESS(_extrs, OptoRuntime::slow_arraycopy_C);
    SET_ADDRESS(_extrs, OptoRuntime::register_finalizer_C);
#if defined(AARCH64)
    SET_ADDRESS(_extrs, JavaThread::verify_cross_modify_fence_failure);
#endif // AARCH64
  }
#endif // COMPILER2

#if INCLUDE_G1GC
  SET_ADDRESS(_extrs, G1BarrierSetRuntime::write_ref_field_pre_entry);
  SET_ADDRESS(_extrs, G1BarrierSetRuntime::write_ref_array_pre_narrow_oop_entry); // used by arraycopy stubs
  SET_ADDRESS(_extrs, G1BarrierSetRuntime::write_ref_array_pre_oop_entry); // used by arraycopy stubs
  SET_ADDRESS(_extrs, G1BarrierSetRuntime::write_ref_array_post_entry); // used by arraycopy stubs
  SET_ADDRESS(_extrs, BarrierSetNMethod::nmethod_stub_entry_barrier); // used by method_entry_barrier

#endif
#if INCLUDE_SHENANDOAHGC
  SET_ADDRESS(_extrs, ShenandoahRuntime::write_barrier_pre);
  SET_ADDRESS(_extrs, ShenandoahRuntime::load_reference_barrier_phantom);
  SET_ADDRESS(_extrs, ShenandoahRuntime::load_reference_barrier_phantom_narrow);
#endif
#if INCLUDE_ZGC
  SET_ADDRESS(_extrs, ZBarrierSetRuntime::load_barrier_on_phantom_oop_field_preloaded_addr());
#if defined(AMD64)
  SET_ADDRESS(_extrs, &ZPointerLoadShift);
#endif
#endif
#ifndef ZERO
#if defined(AMD64) || defined(AARCH64) || defined(RISCV64)
  SET_ADDRESS(_extrs, MacroAssembler::debug64);
#endif
#endif // ZERO

  log_debug(aot, codecache, init)("External addresses opened and recorded");
  // allocate storage for stub entries
  _stubs_addr = NEW_C_HEAP_ARRAY(address, _stubs_max, mtCode);
  log_debug(aot, codecache, init)("Stub addresses opened");
}

void AOTCodeAddressTable::init_extrs2() {
  assert(initializing_extrs && !_extrs_complete,
         "invalid sequence for init_extrs2");

  {
  SET_ADDRESS(_extrs, Continuation::prepare_thaw); // used by cont_thaw
  SET_ADDRESS(_extrs, Continuation::thaw_entry()); // used by cont_thaw
  SET_ADDRESS(_extrs, ContinuationEntry::thaw_call_pc_address()); // used by cont_preempt_stub
  }
  _extrs_complete = true;
  initializing_extrs = false;
  log_debug(aot, codecache, init)("External addresses recorded and closed");
}

void AOTCodeAddressTable::add_external_addresses(GrowableArray<address>& addresses) {
  assert(initializing_extrs && !_extrs_complete,
         "invalid sequence for add_external_addresses");
  for (int i = 0; i < addresses.length(); i++) {
    SET_ADDRESS(_extrs, addresses.at(i));
  }
  log_debug(aot, codecache, init)("External addresses recorded");
}

void AOTCodeAddressTable::add_stub_entry(EntryId entry_id, address a) {
  assert(_extrs_complete || initializing_extrs,
         "recording stub entry address before external addresses complete");
  assert(!(StubInfo::is_shared(StubInfo::stub(entry_id)) && _shared_stubs_complete), "too late to add shared entry");
  assert(!(StubInfo::is_stubgen(StubInfo::stub(entry_id)) && _stubgen_stubs_complete), "too late to add stubgen entry");
  assert(!(StubInfo::is_c1(StubInfo::stub(entry_id)) && _c1_stubs_complete), "too late to add c1 entry");
  assert(!(StubInfo::is_c2(StubInfo::stub(entry_id)) && _c2_stubs_complete), "too late to add c2 entry");
  log_debug(aot, stubs)("Recording address 0x%p for %s entry %s", a, StubInfo::name(StubInfo::stubgroup(entry_id)), StubInfo::name(entry_id));
  int idx = static_cast<int>(entry_id);
  _stubs_addr[idx] = a;
}

void AOTCodeAddressTable::set_shared_stubs_complete() {
  assert(!_shared_stubs_complete, "repeated close for shared stubs!");
  _shared_stubs_complete = true;
  log_debug(aot, codecache, init)("Shared stubs closed");
}

void AOTCodeAddressTable::set_c1_stubs_complete() {
  assert(!_c1_stubs_complete, "repeated close for c1 stubs!");
  _c2_stubs_complete = true;
  log_debug(aot, codecache, init)("C1 stubs closed");
}

void AOTCodeAddressTable::set_c2_stubs_complete() {
  assert(!_c2_stubs_complete, "repeated close for c2 stubs!");
  _c2_stubs_complete = true;
  log_debug(aot, codecache, init)("C2 stubs closed");
}

void AOTCodeAddressTable::set_stubgen_stubs_complete() {
  assert(!_stubgen_stubs_complete, "repeated close for stubgen stubs!");
  _stubgen_stubs_complete = true;
  log_debug(aot, codecache, init)("StubGen stubs closed");
}

AOTCodeAddressTable::~AOTCodeAddressTable() {
  if (_extrs_addr != nullptr) {
    FREE_C_HEAP_ARRAY(address, _extrs_addr);
  }
  if (_stubs_addr != nullptr) {
    FREE_C_HEAP_ARRAY(address, _stubs_addr);
  }
}

#ifdef PRODUCT
#define MAX_STR_COUNT 200
#else
#define MAX_STR_COUNT 500
#endif
#define _c_str_max  MAX_STR_COUNT
static const int _c_str_base = _all_max;

static const char* _C_strings_in[MAX_STR_COUNT] = {nullptr}; // Incoming strings
static const char* _C_strings[MAX_STR_COUNT]    = {nullptr}; // Our duplicates
static int _C_strings_count = 0;
static int _C_strings_s[MAX_STR_COUNT] = {0};
static int _C_strings_id[MAX_STR_COUNT] = {0};
static int _C_strings_used = 0;

void AOTCodeCache::load_strings() {
  uint strings_count  = _load_header->strings_count();
  if (strings_count == 0) {
    return;
  }
  uint strings_offset = _load_header->strings_offset();
  uint* string_lengths = (uint*)addr(strings_offset);
  strings_offset += (strings_count * sizeof(uint));
  uint strings_size = _load_header->entries_offset() - strings_offset;
  // We have to keep cached strings longer than _cache buffer
  // because they are refernced from compiled code which may
  // still be executed on VM exit after _cache is freed.
  char* p = NEW_C_HEAP_ARRAY(char, strings_size+1, mtCode);
  memcpy(p, addr(strings_offset), strings_size);
  _C_strings_buf = p;
  assert(strings_count <= MAX_STR_COUNT, "sanity");
  for (uint i = 0; i < strings_count; i++) {
    _C_strings[i] = p;
    uint len = string_lengths[i];
    _C_strings_s[i] = i;
    _C_strings_id[i] = i;
    p += len;
  }
  assert((uint)(p - _C_strings_buf) <= strings_size, "(" INTPTR_FORMAT " - " INTPTR_FORMAT ") = %d > %d ", p2i(p), p2i(_C_strings_buf), (uint)(p - _C_strings_buf), strings_size);
  _C_strings_count = strings_count;
  _C_strings_used  = strings_count;
  log_debug(aot, codecache, init)("  Loaded %d C strings of total length %d at offset %d from AOT Code Cache", _C_strings_count, strings_size, strings_offset);
}

int AOTCodeCache::store_strings() {
  if (_C_strings_used > 0) {
    MutexLocker ml(AOTCodeCStrings_lock, Mutex::_no_safepoint_check_flag);
    uint offset = _write_position;
    uint length = 0;
    uint* lengths = (uint *)reserve_bytes(sizeof(uint) * _C_strings_used);
    if (lengths == nullptr) {
      return -1;
    }
    for (int i = 0; i < _C_strings_used; i++) {
      const char* str = _C_strings[_C_strings_s[i]];
      uint len = (uint)strlen(str) + 1;
      length += len;
      assert(len < 1000, "big string: %s", str);
      lengths[i] = len;
      uint n = write_bytes(str, len);
      if (n != len) {
        return -1;
      }
    }
    log_debug(aot, codecache, exit)("  Wrote %d C strings of total length %d at offset %d to AOT Code Cache",
                                   _C_strings_used, length, offset);
  }
  return _C_strings_used;
}

const char* AOTCodeCache::add_C_string(const char* str) {
  if (is_on_for_dump() && str != nullptr) {
    MutexLocker ml(AOTCodeCStrings_lock, Mutex::_no_safepoint_check_flag);
    AOTCodeAddressTable* table = addr_table();
    if (table != nullptr) {
      return table->add_C_string(str);
    }
  }
  return str;
}

const char* AOTCodeAddressTable::add_C_string(const char* str) {
  if (_extrs_complete || initializing_extrs) {
    // Check previous strings address
    for (int i = 0; i < _C_strings_count; i++) {
      if (_C_strings_in[i] == str) {
        return _C_strings[i]; // Found previous one - return our duplicate
      } else if (strcmp(_C_strings[i], str) == 0) {
        return _C_strings[i];
      }
    }
    // Add new one
    if (_C_strings_count < MAX_STR_COUNT) {
      // Passed in string can be freed and used space become inaccessible.
      // Keep original address but duplicate string for future compare.
      _C_strings_id[_C_strings_count] = -1; // Init
      _C_strings_in[_C_strings_count] = str;
      const char* dup = os::strdup(str);
      _C_strings[_C_strings_count++] = dup;
      log_trace(aot, codecache, stringtable)("add_C_string: [%d] " INTPTR_FORMAT " '%s'", _C_strings_count, p2i(dup), dup);
      return dup;
    } else {
      assert(false, "Number of C strings >= MAX_STR_COUNT");
    }
  }
  return str;
}

int AOTCodeAddressTable::id_for_C_string(address str) {
  if (str == nullptr) {
    return -1;
  }
  MutexLocker ml(AOTCodeCStrings_lock, Mutex::_no_safepoint_check_flag);
  for (int i = 0; i < _C_strings_count; i++) {
    if (_C_strings[i] == (const char*)str) { // found
      int id = _C_strings_id[i];
      if (id >= 0) {
        assert(id < _C_strings_used, "%d >= %d", id , _C_strings_used);
        return id; // Found recorded
      }
      // Not found in recorded, add new
      id = _C_strings_used++;
      _C_strings_s[id] = i;
      _C_strings_id[i] = id;
      return id;
    }
  }
  return -1;
}

address AOTCodeAddressTable::address_for_C_string(int idx) {
  assert(idx < _C_strings_count, "sanity");
  return (address)_C_strings[idx];
}

static int search_address(address addr, address* table, uint length) {
  for (int i = 0; i < (int)length; i++) {
    if (table[i] == addr) {
      return i;
    }
  }
  return BAD_ADDRESS_ID;
}

address AOTCodeAddressTable::address_for_id(int idx) {
  assert(_extrs_complete || initializing_extrs, "AOT Code Cache VM runtime addresses table is not complete");
  if (idx == -1) {
    return (address)-1;
  }
  uint id = (uint)idx;
  // special case for symbols based relative to os::init
  if (id > (_c_str_base + _c_str_max)) {
    return (address)os::init + idx;
  }
  if (idx < 0) {
    fatal("Incorrect id %d for AOT Code Cache addresses table", id);
    return nullptr;
  }
  // no need to compare unsigned id against 0
  if (/* id >= _extrs_base && */ id < _extrs_length) {
    return _extrs_addr[id - _extrs_base];
  }
  if (id >= _stubs_base && id < _c_str_base) {
    return _stubs_addr[id - _stubs_base];
  }
  if (id >= _c_str_base && id < (_c_str_base + (uint)_C_strings_count)) {
    return address_for_C_string(id - _c_str_base);
  }
  fatal("Incorrect id %d for AOT Code Cache addresses table", id);
  return nullptr;
}

int AOTCodeAddressTable::id_for_address(address addr, RelocIterator reloc, CodeBlob* code_blob) {
  assert(_extrs_complete || initializing_extrs, "AOT Code Cache VM runtime addresses table is not complete");
  int id = -1;
  if (addr == (address)-1) { // Static call stub has jump to itself
    return id;
  }
  // Seach for C string
  id = id_for_C_string(addr);
  if (id >= 0) {
    return id + _c_str_base;
  }
  if (StubRoutines::contains(addr) || CodeCache::find_blob(addr) != nullptr) {
    // Search for a matching stub entry
    id = search_address(addr, _stubs_addr, _stubs_max);
    if (id < 0) {
      StubCodeDesc* desc = StubCodeDesc::desc_for(addr);
      if (desc == nullptr) {
        desc = StubCodeDesc::desc_for(addr + frame::pc_return_offset);
      }
      const char* sub_name = (desc != nullptr) ? desc->name() : "<unknown>";
      assert(false, "Address " INTPTR_FORMAT " for Stub:%s is missing in AOT Code Cache addresses table", p2i(addr), sub_name);
    } else {
      return id + _stubs_base;
    }
  } else {
    // Search in runtime functions
    id = search_address(addr, _extrs_addr, _extrs_length);
    if (id < 0) {
      ResourceMark rm;
      const int buflen = 1024;
      char* func_name = NEW_RESOURCE_ARRAY(char, buflen);
      int offset = 0;
      if (os::dll_address_to_function_name(addr, func_name, buflen, &offset)) {
        if (offset > 0) {
          // Could be address of C string
          uint dist = (uint)pointer_delta(addr, (address)os::init, 1);
          log_debug(aot, codecache)("Address " INTPTR_FORMAT " (offset %d) for runtime target '%s' is missing in AOT Code Cache addresses table",
                                    p2i(addr), dist, (const char*)addr);
          assert(dist > (uint)(_all_max + MAX_STR_COUNT), "change encoding of distance");
          return dist;
        }
#ifdef ASSERT
        reloc.print_current_on(tty);
        code_blob->print_on(tty);
        code_blob->print_code_on(tty);
        assert(false, "Address " INTPTR_FORMAT " for runtime target '%s+%d' is missing in AOT Code Cache addresses table", p2i(addr), func_name, offset);
#endif
      } else {
#ifdef ASSERT
        reloc.print_current_on(tty);
        code_blob->print_on(tty);
        code_blob->print_code_on(tty);
        os::find(addr, tty);
        assert(false, "Address " INTPTR_FORMAT " for <unknown>/('%s') is missing in AOT Code Cache addresses table", p2i(addr), (const char*)addr);
#endif
      }
    } else {
      return _extrs_base + id;
    }
  }
  return id;
}

// This is called after initialize() but before init2()
// and _cache is not set yet.
void AOTCodeCache::print_on(outputStream* st) {
  if (opened_cache != nullptr && opened_cache->for_use()) {
    st->print_cr("\nAOT Code Cache");
    uint count = opened_cache->_load_header->entries_count();
    uint* search_entries = (uint*)opened_cache->addr(opened_cache->_load_header->entries_offset()); // [id, index]
    AOTCodeEntry* load_entries = (AOTCodeEntry*)(search_entries + 2 * count);

    for (uint i = 0; i < count; i++) {
      // Use search_entries[] to order ouput
      int index = search_entries[2*i + 1];
      AOTCodeEntry* entry = &(load_entries[index]);

      uint entry_position = entry->offset();
      uint name_offset = entry->name_offset() + entry_position;
      const char* saved_name = opened_cache->addr(name_offset);

      st->print_cr("%4u: %10s idx:%4u Id:%u size=%u '%s'",
                   i, aot_code_entry_kind_name[entry->kind()], index, entry->id(), entry->size(), saved_name);
    }
  }
}

// methods for managing entries in multi-stub blobs


AOTStubData::AOTStubData(BlobId blob_id) :
  _blob_id(blob_id),
  _cached_blob(nullptr),
  _stub_cnt(0),
  _ranges(nullptr),
  _current(StubId::NO_STUBID),
  _current_idx(-1),
  _flags(0) {
  assert(StubInfo::is_stubgen(blob_id),
         "AOTStubData expects a multi-stub blob not %s",
         StubInfo::name(blob_id));

  // we cannot save or restore preuniversestubs because the cache
  // cannot be accessed before initialising the universe
  if (blob_id == BlobId::stubgen_preuniverse_id) {
    // invalidate any attempt to use this
    _flags |= INVALID;
    return;
  }
  if (AOTCodeCache::is_on()) {
    // allow update of stub entry addresses
    _flags |= OPEN;
    if (AOTCodeCache::is_using_stub()) {
      // allow stub loading
      _flags |= USING;
    }
    if (AOTCodeCache::is_dumping_stub()) {
      // allow stub saving
      _flags |= DUMPING;
    }
    // we need to track all the blob's entries
    _stub_cnt = StubInfo::stub_count(_blob_id);
    _ranges = NEW_C_HEAP_ARRAY(StubAddrRange, _stub_cnt, mtCode);
    for (int i = 0; i < _stub_cnt; i++) {
      _ranges[i].default_init();
    }
  }
}

bool AOTStubData::load_code_blob() {
  assert(is_using(), "should not call");
  assert(!is_invalid() && _cached_blob == nullptr, "repeated init");
  _cached_blob = AOTCodeCache::load_code_blob(AOTCodeEntry::StubGenBlob,
                                              _blob_id,
                                              this);
  if (_cached_blob == nullptr) {
    set_invalid();
    return false;
  } else {
    return true;
  }
}

bool AOTStubData::store_code_blob(CodeBlob& new_blob, CodeBuffer *code_buffer) {
  assert(is_dumping(), "should not call");
  assert(_cached_blob == nullptr, "should not be loading and storing!");
  if (!AOTCodeCache::store_code_blob(new_blob,
                                     AOTCodeEntry::StubGenBlob,
                                     _blob_id, this, code_buffer)) {
    set_invalid();
    return false;
  } else {
    return true;
  }
}

bool AOTStubData::find_archive_data(StubId stub_id) {
  assert(StubInfo::blob(stub_id) == _blob_id, "sanity check");
  if (is_invalid()) {
    return false;
  }
  int idx = StubInfo::stubgen_offset_in_blob(_blob_id, stub_id);
  assert(idx >= 0 && idx < _stub_cnt, "invalid index %d for stub count %d", idx, _stub_cnt);
  // ensure we have a valid associated range
  StubAddrRange range = _ranges[idx];
  int start_index = range.start_index();
  if (start_index < 0) {
    _current = StubId::NO_STUBID;
#ifdef DEBUG
    // reset index so we can idenitfy which ones we failed to find
    range.init_entry(-2, 0);
#endif
    return false;
  }
  _current = stub_id;
  _current_idx = idx;
  return true;
}

void AOTStubData::load_archive_data(StubId stub_id, address& start, address& end, GrowableArray<address>* entries, GrowableArray<address>* extras) {
  assert(StubInfo::blob(stub_id) == _blob_id, "sanity check");
  assert(_current == stub_id && stub_id != StubId::NO_STUBID, "sanity check");
  assert(!is_invalid(), "should not load stubs when archive data is invalid");
  assert(_current_idx >= 0, "sanity");
  StubAddrRange& range = _ranges[_current_idx];
  int base = range.start_index();
  int count = range.count();
  assert(base >= 0, "sanity");
  assert(count >= 2, "sanity");
  // first two saved addresses are start and end
  start = _address_array.at(base);
  end = _address_array.at(base + 1);
  assert(start != nullptr, "failed to load start address of stub %s", StubInfo::name(stub_id));
  assert(end != nullptr, "failed to load end address of stub %s", StubInfo::name(stub_id));
  assert(start < end, "start address %p should be less than end %p address for stub %s", start, end, StubInfo::name(stub_id));

  int entry_count = StubInfo::entry_count(stub_id);
  // the address count must at least include the stub start, end
  // and secondary addresses
  assert(count >= entry_count + 1, "stub %s requires %d saved addresses but only has %d", StubInfo::name(stub_id), entry_count + 1, count);

  // caller must retrieve secondary entries if and only if they exist
  assert((entry_count == 1) == (entries == nullptr), "trying to retrieve wrong number of entries for stub %s", StubInfo::name(stub_id));
  int index = 2;
  if (entries != nullptr) {
    assert(entries->length() == 0, "non-empty array when retrieving entries for stub %s!", StubInfo::name(stub_id));
    while (index < entry_count + 1) {
      address entry = _address_array.at(base + index++);
      assert(entry == nullptr || (start < entry && entry < end), "entry address %p not in range (%p, %p) for stub %s", entry, start, end, StubInfo::name(stub_id));
      entries->append(entry);
    }
  }
  // caller must retrieve extras if and only if they exist
  assert((index < count) == (extras != nullptr), "trying to retrieve wrong number of extras for stub %s", StubInfo::name(stub_id));
  if (extras != nullptr) {
    assert(extras->length() == 0, "non-empty array when retrieving extras for stub %s!", StubInfo::name(stub_id));
    while (index < count) {
      address extra = _address_array.at(base + index++);
      assert(extra == nullptr || (start <= extra && extra < end), "extra address %p not in range (%p, %p) for stub %s", extra, start, end, StubInfo::name(stub_id));
      extras->append(extra);
    }
  }
}

void AOTStubData::store_archive_data(StubId stub_id, address start, address end, GrowableArray<address>* entries, GrowableArray<address>* extras) {
  assert(StubInfo::blob(stub_id) == _blob_id, "sanity check");
  assert(start != nullptr, "start address cannot be null");
  assert(end != nullptr, "end address cannot be null");
  assert(start < end, "start address %p should be less than end %p address for stub %s", start, end, StubInfo::name(stub_id));
  _current = stub_id;
  _current_idx = StubInfo::stubgen_offset_in_blob(_blob_id, stub_id);
  StubAddrRange& range = _ranges[_current_idx];
  assert(range.start_index() == -1, "sanity");
  int base = _address_array.length();
  assert(base >= 0, "sanity");
  // first two saved addresses are start and end
  _address_array.append(start);
  _address_array.append(end);
  // caller must save secondary entries if and only if they exist
  assert((StubInfo::entry_count(stub_id) == 1) == (entries == nullptr), "trying to save wrong number of entries for stub %s", StubInfo::name(stub_id));
  if (entries != nullptr) {
    assert(entries->length() == StubInfo::entry_count(stub_id) - 1, "incorrect entry count %d when saving entries for stub %s!", entries->length(), StubInfo::name(stub_id));
    for (int i = 0; i < entries->length(); i++) {
      address entry = entries->at(i);
      assert(entry == nullptr || (start < entry && entry < end), "entry address %p not in range (%p, %p) for stub %s", entry, start, end, StubInfo::name(stub_id));
      _address_array.append(entry);
    }
  }
  // caller may wish to save extra addresses
  if (extras != nullptr) {
    for (int i = 0; i < extras->length(); i++) {
      address extra = extras->at(i);
      // handler range end may be end -- it gets restored as nullptr
      assert(extra == nullptr || (start <= extra && extra <= end), "extra address %p not in range (%p, %p) for stub %s", extra, start, end, StubInfo::name(stub_id));
      _address_array.append(extra);
    }
  }
  range.init_entry(base, _address_array.length() - base);
}

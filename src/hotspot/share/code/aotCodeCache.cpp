/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcConfig.hpp"
#include "logging/logStream.hpp"
#include "memory/memoryReserver.hpp"
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
  } else {
    // kind must be AOTCodeEntry::C2Blob
    assert(StubInfo::is_c2(static_cast<BlobId>(id)), "not a c2 blob id %d", id);
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
  FLAG_SET_ERGO(AOTStubCaching, false);

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

void AOTCodeCache::init_early_stubs_table() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->init_early_stubs();
  }
}

void AOTCodeCache::init_shared_blobs_table() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->init_shared_blobs();
  }
}

void AOTCodeCache::init_early_c1_table() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->init_early_c1();
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

void AOTCodeCache::Config::record(uint cpu_features_offset) {
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
  _cpu_features_offset   = cpu_features_offset;
}

bool AOTCodeCache::Config::verify_cpu_features(AOTCodeCache* cache) const {
  LogStreamHandle(Debug, aot, codecache, init) log;
  uint offset = _cpu_features_offset;
  uint cpu_features_size = *(uint *)cache->addr(offset);
  assert(cpu_features_size == (uint)VM_Version::cpu_features_size(), "must be");
  offset += sizeof(uint);

  void* cached_cpu_features_buffer = (void *)cache->addr(offset);
  if (log.is_enabled()) {
    ResourceMark rm; // required for stringStream::as_string()
    stringStream ss;
    VM_Version::get_cpu_features_name(cached_cpu_features_buffer, ss);
    log.print_cr("CPU features recorded in AOTCodeCache: %s", ss.as_string());
  }

  if (VM_Version::supports_features(cached_cpu_features_buffer)) {
    if (log.is_enabled()) {
      ResourceMark rm; // required for stringStream::as_string()
      stringStream ss;
      char* runtime_cpu_features = NEW_RESOURCE_ARRAY(char, VM_Version::cpu_features_size());
      VM_Version::store_cpu_features(runtime_cpu_features);
      VM_Version::get_missing_features_name(runtime_cpu_features, cached_cpu_features_buffer, ss);
      if (!ss.is_empty()) {
        log.print_cr("Additional runtime CPU features: %s", ss.as_string());
      }
    }
  } else {
    if (log.is_enabled()) {
      ResourceMark rm; // required for stringStream::as_string()
      stringStream ss;
      char* runtime_cpu_features = NEW_RESOURCE_ARRAY(char, VM_Version::cpu_features_size());
      VM_Version::store_cpu_features(runtime_cpu_features);
      VM_Version::get_missing_features_name(cached_cpu_features_buffer, runtime_cpu_features, ss);
      log.print_cr("AOT Code Cache disabled: required cpu features are missing: %s", ss.as_string());
    }
    return false;
  }
  return true;
}

bool AOTCodeCache::Config::verify(AOTCodeCache* cache) const {
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

  if (!verify_cpu_features(cache)) {
    return false;
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

void AOTCodeCache::store_cpu_features(char*& buffer, uint buffer_size) {
  uint* size_ptr = (uint *)buffer;
  *size_ptr = buffer_size;
  buffer += sizeof(uint);

  VM_Version::store_cpu_features(buffer);
  log_debug(aot, codecache, exit)("CPU features recorded in AOTCodeCache: %s", VM_Version::features_string());
  buffer += buffer_size;
  buffer = align_up(buffer, DATA_ALIGNMENT);
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
    uint header_size = (uint)align_up(sizeof(AOTCodeCache::Header), DATA_ALIGNMENT);
    uint code_count = store_count;
    uint search_count = code_count * 2;
    uint search_size = search_count * sizeof(uint);
    uint entries_size = (uint)align_up(code_count * sizeof(AOTCodeEntry), DATA_ALIGNMENT); // In bytes
    // _write_position includes size of code and strings
    uint code_alignment = code_count * DATA_ALIGNMENT; // We align_up code size when storing it.
    uint cpu_features_size = VM_Version::cpu_features_size();
    uint total_cpu_features_size = sizeof(uint) + cpu_features_size; // sizeof(uint) to store cpu_features_size
    uint total_size = header_size + _write_position + code_alignment + search_size + entries_size +
                      align_up(total_cpu_features_size, DATA_ALIGNMENT);
    assert(total_size < max_aot_code_size(), "AOT Code size (" UINT32_FORMAT " bytes) is greater than AOTCodeMaxSize(" UINT32_FORMAT " bytes).", total_size, max_aot_code_size());

    // Allocate in AOT Cache buffer
    char* buffer = (char *)AOTCacheAccess::allocate_aot_code_region(total_size + DATA_ALIGNMENT);
    char* start = align_up(buffer, DATA_ALIGNMENT);
    char* current = start + header_size; // Skip header

    uint cpu_features_offset = current - start;
    store_cpu_features(current, cpu_features_size);
    assert(is_aligned(current, DATA_ALIGNMENT), "sanity check");
    assert(current < start + total_size, "sanity check");

    // Create ordered search table for entries [id, index];
    uint* search = NEW_C_HEAP_ARRAY(uint, search_count, mtCode);

    AOTCodeEntry* entries_address = _store_entries; // Pointer to latest entry
    uint adapters_count = 0;
    uint shared_blobs_count = 0;
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
    log_debug(aot, codecache, exit)("  C1 Blobs:      total=%d", C1_blobs_count);
    log_debug(aot, codecache, exit)("  C2 Blobs:      total=%d", C2_blobs_count);
    log_debug(aot, codecache, exit)("  AOT code cache size: %u bytes, max entry's size: %u bytes", size, max_size);

    // Finalize header
    AOTCodeCache::Header* header = (AOTCodeCache::Header*)start;
    header->init(size, (uint)strings_count, strings_offset,
                 entries_count, new_entries_offset,
                 adapters_count, shared_blobs_count,
                 C1_blobs_count, C2_blobs_count, cpu_features_offset);

    log_info(aot, codecache, exit)("Wrote %d AOT code entries to AOT Code Cache", entries_count);
  }
  return true;
}

//------------------Store/Load AOT code ----------------------

bool AOTCodeCache::store_code_blob(CodeBlob& blob, AOTCodeEntry::Kind entry_kind, uint id, const char* name) {
  AOTCodeCache* cache = open_for_dump();
  if (cache == nullptr) {
    return false;
  }
  assert(AOTCodeEntry::is_valid_entry_kind(entry_kind), "invalid entry_kind %d", entry_kind);

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

  uint reloc_data_size = blob.relocation_size();
  n = cache->write_bytes((address)blob.relocation_begin(), reloc_data_size);
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

  if (!cache->write_relocations(blob)) {
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

bool AOTCodeCache::store_code_blob(CodeBlob& blob, AOTCodeEntry::Kind entry_kind, BlobId id) {
  assert(AOTCodeEntry::is_blob(entry_kind),
         "wrong entry kind for blob id %s", StubInfo::name(id));
  return store_code_blob(blob, entry_kind, (uint)id, StubInfo::name(id));
}

CodeBlob* AOTCodeCache::load_code_blob(AOTCodeEntry::Kind entry_kind, uint id, const char* name) {
  AOTCodeCache* cache = open_for_use();
  if (cache == nullptr) {
    return nullptr;
  }
  assert(AOTCodeEntry::is_valid_entry_kind(entry_kind), "invalid entry_kind %d", entry_kind);

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
  CodeBlob* blob = reader.compile_code_blob(name);

  log_debug(aot, codecache, stubs)("%sRead blob '%s' (id=%u, kind=%s) from AOT Code Cache",
                                   (blob == nullptr? "Failed to " : ""), name, id, aot_code_entry_kind_name[entry_kind]);
  return blob;
}

CodeBlob* AOTCodeCache::load_code_blob(AOTCodeEntry::Kind entry_kind, BlobId id) {
  assert(AOTCodeEntry::is_blob(entry_kind),
         "wrong entry kind for blob id %s", StubInfo::name(id));
  return load_code_blob(entry_kind, (uint)id, StubInfo::name(id));
}

CodeBlob* AOTCodeReader::compile_code_blob(const char* name) {
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

  // Read archived code blob
  uint offset = entry_position + _entry->blob_offset();
  CodeBlob* archived_blob = (CodeBlob*)addr(offset);
  offset += archived_blob->size();

  address reloc_data = (address)addr(offset);
  offset += archived_blob->relocation_size();
  set_read_position(offset);

  ImmutableOopMapSet* oop_maps = nullptr;
  if (_entry->has_oop_maps()) {
    oop_maps = read_oop_map_set();
  }

  CodeBlob* code_blob = CodeBlob::create(archived_blob,
                                         stored_name,
                                         reloc_data,
                                         oop_maps
                                        );
  if (code_blob == nullptr) { // no space left in CodeCache
    return nullptr;
  }

#ifndef PRODUCT
  code_blob->asm_remarks().init();
  read_asm_remarks(code_blob->asm_remarks());
  code_blob->dbg_strings().init();
  read_dbg_strings(code_blob->dbg_strings());
#endif // PRODUCT

  fix_relocations(code_blob);

#ifdef ASSERT
  LogStreamHandle(Trace, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    code_blob->print_on(&log);
  }
#endif
  return code_blob;
}

// ------------ process code and data --------------

// Can't use -1. It is valid value for jump to iteself destination
// used by static call stub: see NativeJump::jump_destination().
#define BAD_ADDRESS_ID -2

bool AOTCodeCache::write_relocations(CodeBlob& code_blob) {
  GrowableArray<uint> reloc_data;
  RelocIterator iter(&code_blob);
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
  for (GrowableArrayIterator<uint> iter = reloc_data.begin();
       iter != reloc_data.end(); ++iter) {
    uint value = *iter;
    int n = write_bytes(&value, sizeof(uint));
    if (n != sizeof(uint)) {
      return false;
    }
  }
  return true;
}

void AOTCodeReader::fix_relocations(CodeBlob* code_blob) {
  LogStreamHandle(Trace, aot, reloc) log;
  uint offset = read_position();
  int count = *(int*)addr(offset);
  offset += sizeof(int);
  if (log.is_enabled()) {
    log.print_cr("======== extra relocations count=%d", count);
  }
  uint* reloc_data = (uint*)addr(offset);
  offset += (count * sizeof(uint));
  set_read_position(offset);

  RelocIterator iter(code_blob);
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
  assert(j == count, "sanity");
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

// address table ids for generated routines, external addresses and C
// string addresses are partitioned into positive integer ranges
// defined by the following positive base and max values
// i.e. [_extrs_base, _extrs_base + _extrs_max -1],
//      [_blobs_base, _blobs_base + _blobs_max -1],
//      ...
//      [_c_str_base, _c_str_base + _c_str_max -1],

#define _extrs_max 100
#define _stubs_max 3

#define _shared_blobs_max 20
#define _C1_blobs_max 10
#define _blobs_max (_shared_blobs_max+_C1_blobs_max)
#define _all_max (_extrs_max+_stubs_max+_blobs_max)

#define _extrs_base 0
#define _stubs_base (_extrs_base + _extrs_max)
#define _shared_blobs_base (_stubs_base + _stubs_max)
#define _C1_blobs_base (_shared_blobs_base + _shared_blobs_max)
#define _blobs_end  (_shared_blobs_base + _blobs_max)

#define SET_ADDRESS(type, addr)                           \
  {                                                       \
    type##_addr[type##_length++] = (address) (addr);      \
    assert(type##_length <= type##_max, "increase size"); \
  }

static bool initializing_extrs = false;

void AOTCodeAddressTable::init_extrs() {
  if (_extrs_complete || initializing_extrs) return; // Done already

  assert(_blobs_end <= _all_max, "AOTCodeAddress table ranges need adjusting");

  initializing_extrs = true;
  _extrs_addr = NEW_C_HEAP_ARRAY(address, _extrs_max, mtCode);

  _extrs_length = 0;

  // Record addresses of VM runtime methods
  SET_ADDRESS(_extrs, SharedRuntime::fixup_callers_callsite);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method_abstract);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method_ic_miss);
#if defined(AARCH64) && !defined(ZERO)
  SET_ADDRESS(_extrs, JavaThread::aarch64_get_thread_helper);
#endif
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
    SET_ADDRESS(_extrs, OptoRuntime::complete_monitor_locking_C);
    SET_ADDRESS(_extrs, OptoRuntime::monitor_notify_C);
    SET_ADDRESS(_extrs, OptoRuntime::monitor_notifyAll_C);
    SET_ADDRESS(_extrs, OptoRuntime::rethrow_C);
    SET_ADDRESS(_extrs, OptoRuntime::slow_arraycopy_C);
    SET_ADDRESS(_extrs, OptoRuntime::register_finalizer_C);
    SET_ADDRESS(_extrs, OptoRuntime::vthread_end_first_transition_C);
    SET_ADDRESS(_extrs, OptoRuntime::vthread_start_final_transition_C);
    SET_ADDRESS(_extrs, OptoRuntime::vthread_start_transition_C);
    SET_ADDRESS(_extrs, OptoRuntime::vthread_end_transition_C);
#if defined(AARCH64)
    SET_ADDRESS(_extrs, JavaThread::verify_cross_modify_fence_failure);
#endif // AARCH64
  }
#endif // COMPILER2

#if INCLUDE_G1GC
  SET_ADDRESS(_extrs, G1BarrierSetRuntime::write_ref_field_pre_entry);
#endif
#if INCLUDE_SHENANDOAHGC
  SET_ADDRESS(_extrs, ShenandoahRuntime::write_barrier_pre);
  SET_ADDRESS(_extrs, ShenandoahRuntime::load_reference_barrier_phantom);
  SET_ADDRESS(_extrs, ShenandoahRuntime::load_reference_barrier_phantom_narrow);
#endif
#if INCLUDE_ZGC
  SET_ADDRESS(_extrs, ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr());
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

  _extrs_complete = true;
  log_debug(aot, codecache, init)("External addresses recorded");
}

static bool initializing_early_stubs = false;

void AOTCodeAddressTable::init_early_stubs() {
  if (_complete || initializing_early_stubs) return; // Done already
  initializing_early_stubs = true;
  _stubs_addr = NEW_C_HEAP_ARRAY(address, _stubs_max, mtCode);
  _stubs_length = 0;
  SET_ADDRESS(_stubs, StubRoutines::forward_exception_entry());

  {
    // Required by C1 blobs
#if defined(AMD64) && !defined(ZERO)
    SET_ADDRESS(_stubs, StubRoutines::x86::double_sign_flip());
    SET_ADDRESS(_stubs, StubRoutines::x86::d2l_fixup());
#endif // AMD64
  }

  _early_stubs_complete = true;
  log_info(aot, codecache, init)("Early stubs recorded");
}

static bool initializing_shared_blobs = false;

void AOTCodeAddressTable::init_shared_blobs() {
  if (_complete || initializing_shared_blobs) return; // Done already
  initializing_shared_blobs = true;
  address* blobs_addr = NEW_C_HEAP_ARRAY(address, _blobs_max, mtCode);

  // Divide _shared_blobs_addr array to chunks because they could be initialized in parrallel
  _shared_blobs_addr = blobs_addr;
  _C1_blobs_addr = _shared_blobs_addr + _shared_blobs_max;

  _shared_blobs_length = 0;
  _C1_blobs_length = 0;

  // clear the address table
  memset(blobs_addr, 0, sizeof(address)* _blobs_max);

  // Record addresses of generated code blobs
  SET_ADDRESS(_shared_blobs, SharedRuntime::get_handle_wrong_method_stub());
  SET_ADDRESS(_shared_blobs, SharedRuntime::get_ic_miss_stub());
  SET_ADDRESS(_shared_blobs, SharedRuntime::deopt_blob()->unpack());
  SET_ADDRESS(_shared_blobs, SharedRuntime::deopt_blob()->unpack_with_exception());
  SET_ADDRESS(_shared_blobs, SharedRuntime::deopt_blob()->unpack_with_reexecution());
  SET_ADDRESS(_shared_blobs, SharedRuntime::deopt_blob()->unpack_with_exception_in_tls());
#if INCLUDE_JVMCI
  if (EnableJVMCI) {
    SET_ADDRESS(_shared_blobs, SharedRuntime::deopt_blob()->uncommon_trap());
    SET_ADDRESS(_shared_blobs, SharedRuntime::deopt_blob()->implicit_exception_uncommon_trap());
  }
#endif

  _shared_blobs_complete = true;
  log_debug(aot, codecache, init)("Early shared blobs recorded");
  _complete = true;
}

void AOTCodeAddressTable::init_early_c1() {
#ifdef COMPILER1
  // Runtime1 Blobs
  StubId id = StubInfo::stub_base(StubGroup::C1);
  // include forward_exception in range we publish
  StubId limit = StubInfo::next(StubId::c1_forward_exception_id);
  for (; id != limit; id = StubInfo::next(id)) {
    if (Runtime1::blob_for(id) == nullptr) {
      log_info(aot, codecache, init)("C1 blob %s is missing", Runtime1::name_for(id));
      continue;
    }
    if (Runtime1::entry_for(id) == nullptr) {
      log_info(aot, codecache, init)("C1 blob %s is missing entry", Runtime1::name_for(id));
      continue;
    }
    address entry = Runtime1::entry_for(id);
    SET_ADDRESS(_C1_blobs, entry);
  }
#endif // COMPILER1
  assert(_C1_blobs_length <= _C1_blobs_max, "increase _C1_blobs_max to %d", _C1_blobs_length);
  _early_c1_complete = true;
}

#undef SET_ADDRESS

AOTCodeAddressTable::~AOTCodeAddressTable() {
  if (_extrs_addr != nullptr) {
    FREE_C_HEAP_ARRAY(address, _extrs_addr);
  }
  if (_stubs_addr != nullptr) {
    FREE_C_HEAP_ARRAY(address, _stubs_addr);
  }
  if (_shared_blobs_addr != nullptr) {
    FREE_C_HEAP_ARRAY(address, _shared_blobs_addr);
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
  if (_extrs_complete) {
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
  assert(_extrs_complete, "AOT Code Cache VM runtime addresses table is not complete");
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
  if (id >= _stubs_base && id < _stubs_base + _stubs_length) {
    return _stubs_addr[id - _stubs_base];
  }
  if (id >= _shared_blobs_base && id < _shared_blobs_base + _shared_blobs_length) {
    return _shared_blobs_addr[id - _shared_blobs_base];
  }
  if (id >= _C1_blobs_base && id < _C1_blobs_base + _C1_blobs_length) {
    return _C1_blobs_addr[id - _C1_blobs_base];
  }
  if (id >= _c_str_base && id < (_c_str_base + (uint)_C_strings_count)) {
    return address_for_C_string(id - _c_str_base);
  }
  fatal("Incorrect id %d for AOT Code Cache addresses table", id);
  return nullptr;
}

int AOTCodeAddressTable::id_for_address(address addr, RelocIterator reloc, CodeBlob* code_blob) {
  assert(_extrs_complete, "AOT Code Cache VM runtime addresses table is not complete");
  int id = -1;
  if (addr == (address)-1) { // Static call stub has jump to itself
    return id;
  }
  // Seach for C string
  id = id_for_C_string(addr);
  if (id >= 0) {
    return id + _c_str_base;
  }
  if (StubRoutines::contains(addr)) {
    // Search in stubs
    id = search_address(addr, _stubs_addr, _stubs_length);
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
    CodeBlob* cb = CodeCache::find_blob(addr);
    if (cb != nullptr) {
      // Search in code blobs
      int id_base = _shared_blobs_base;
      id = search_address(addr, _shared_blobs_addr, _blobs_max);
      if (id < 0) {
        assert(false, "Address " INTPTR_FORMAT " for Blob:%s is missing in AOT Code Cache addresses table", p2i(addr), cb->name());
      } else {
        return id_base + id;
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

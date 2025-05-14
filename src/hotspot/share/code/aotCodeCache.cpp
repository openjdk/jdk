/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/javaAssertions.hpp"
#include "code/aotCodeCache.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/gcConfig.hpp"
#include "logging/logStream.hpp"
#include "memory/memoryReserver.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/copy.hpp"
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

#include <sys/stat.h>
#include <errno.h>

static void report_load_failure() {
  if (AbortVMOnAOTCodeFailure) {
    vm_exit_during_initialization("Unable to use AOT Code Cache.", nullptr);
  }
  log_info(aot, codecache, init)("Unable to use AOT Code Cache.");
  AOTAdapterCaching = false;
}

static void report_store_failure() {
  if (AbortVMOnAOTCodeFailure) {
    tty->print_cr("Unable to create AOT Code Cache.");
    vm_abort(false);
  }
  log_info(aot, codecache, exit)("Unable to create AOT Code Cache.");
  AOTAdapterCaching = false;
}

bool AOTCodeCache::is_dumping_adapters() {
  return AOTAdapterCaching && is_on_for_dump();
}

bool AOTCodeCache::is_using_adapters()   {
  return AOTAdapterCaching && is_on_for_use();
}

static uint _max_aot_code_size = 0;
uint AOTCodeCache::max_aot_code_size() {
  return _max_aot_code_size;
}

void AOTCodeCache::initialize() {
#if defined(ZERO) || !(defined(AMD64) || defined(AARCH64))
  log_info(aot, codecache, init)("AOT Code Cache is not supported on this platform.");
  AOTAdapterCaching = false;
  return;
#else
  if (FLAG_IS_DEFAULT(AOTCache)) {
    log_info(aot, codecache, init)("AOT Code Cache is not used: AOTCache is not specified.");
    AOTAdapterCaching = false;
    return; // AOTCache must be specified to dump and use AOT code
  }

  bool is_dumping = false;
  bool is_using   = false;
  if (CDSConfig::is_dumping_final_static_archive() && CDSConfig::is_dumping_aot_linked_classes()) {
    FLAG_SET_ERGO_IF_DEFAULT(AOTAdapterCaching, true);
    is_dumping = true;
  } else if (CDSConfig::is_using_archive() && CDSConfig::is_using_aot_linked_classes()) {
    FLAG_SET_ERGO_IF_DEFAULT(AOTAdapterCaching, true);
    is_using = true;
  } else {
    log_info(aot, codecache, init)("AOT Code Cache is not used: AOT Class Linking is not used.");
    return; // nothing to do
  }
  if (!AOTAdapterCaching) {
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

void AOTCodeCache::init2() {
  if (!is_on()) {
    return;
  }
  if (!verify_vm_config()) {
    close();
    report_load_failure();
  }
  // initialize the table of external routines so we can save
  // generated code blobs that reference them
  init_extrs_table();
}

AOTCodeCache* AOTCodeCache::_cache = nullptr;

bool AOTCodeCache::open_cache(bool is_dumping, bool is_using) {
  AOTCodeCache* cache = new AOTCodeCache(is_dumping, is_using);
  if (cache->failed()) {
    delete cache;
    _cache = nullptr;
    return false;
  }
  _cache = cache;
  return true;
}

void AOTCodeCache::close() {
  if (is_on()) {
    delete _cache; // Free memory
    _cache = nullptr;
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
    if (!_load_header->verify_config(_load_size)) {
      set_failed();
      return;
    }
    log_info (aot, codecache, init)("Loaded %u AOT code entries from AOT Code Cache", _load_header->entries_count());
    log_debug(aot, codecache, init)("  Adapters:  total=%u", _load_header->adapters_count());
    log_debug(aot, codecache, init)("  All Blobs: total=%u", _load_header->blobs_count());
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

void AOTCodeCache::init_extrs_table() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->init_extrs();
  }
}

void AOTCodeCache::init_shared_blobs_table() {
  AOTCodeAddressTable* table = addr_table();
  if (table != nullptr) {
    table->init_shared_blobs();
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
  _compressedKlassShift  = CompressedKlassPointers::shift();
  _contendedPaddingWidth = ContendedPaddingWidth;
  _objectAlignment       = ObjectAlignmentInBytes;
  _gc                    = (uint)Universe::heap()->kind();
}

bool AOTCodeCache::Config::verify() const {
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

  if (((_flags & compressedOops) != 0) != UseCompressedOops) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with UseCompressedOops = %s", UseCompressedOops ? "false" : "true");
    return false;
  }
  if (((_flags & compressedClassPointers) != 0) != UseCompressedClassPointers) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with UseCompressedClassPointers = %s", UseCompressedClassPointers ? "false" : "true");
    return false;
  }

  if (((_flags & systemClassAssertions) != 0) != JavaAssertions::systemClassDefault()) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with JavaAssertions::systemClassDefault() = %s", JavaAssertions::systemClassDefault() ? "disabled" : "enabled");
    return false;
  }
  if (((_flags & userClassAssertions) != 0) != JavaAssertions::userClassDefault()) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with JavaAssertions::userClassDefault() = %s", JavaAssertions::userClassDefault() ? "disabled" : "enabled");
    return false;
  }

  if (((_flags & enableContendedPadding) != 0) != EnableContended) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with EnableContended = %s", EnableContended ? "false" : "true");
    return false;
  }
  if (((_flags & restrictContendedPadding) != 0) != RestrictContended) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with RestrictContended = %s", RestrictContended ? "false" : "true");
    return false;
  }
  if (_compressedOopShift != (uint)CompressedOops::shift()) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with CompressedOops::shift() = %d vs current %d", _compressedOopShift, CompressedOops::shift());
    return false;
  }
  if (_compressedKlassShift != (uint)CompressedKlassPointers::shift()) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with CompressedKlassPointers::shift() = %d vs current %d", _compressedKlassShift, CompressedKlassPointers::shift());
    return false;
  }
  if (_contendedPaddingWidth != (uint)ContendedPaddingWidth) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with ContendedPaddingWidth = %d vs current %d", _contendedPaddingWidth, ContendedPaddingWidth);
    return false;
  }
  if (_objectAlignment != (uint)ObjectAlignmentInBytes) {
    log_debug(aot, codecache, init)("AOT Code Cache disabled: it was created with ObjectAlignmentInBytes = %d vs current %d", _objectAlignment, ObjectAlignmentInBytes);
    return false;
  }
  return true;
}

bool AOTCodeCache::Header::verify_config(uint load_size) const {
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
  assert(size > 0, "sanity");
  bool by_words = true;
  if ((size > 2 * HeapWordSize) && (((intptr_t)from | (intptr_t)to) & (HeapWordSize - 1)) == 0) {
    // Use wordwise copies if possible:
    Copy::disjoint_words((HeapWord*)from,
                         (HeapWord*)to,
                         ((size_t)size + HeapWordSize-1) / HeapWordSize);
  } else {
    by_words = false;
    Copy::conjoint_jbytes(from, to, (size_t)size);
  }
  log_trace(aot, codecache)("Copied %d bytes as %s from " INTPTR_FORMAT " to " INTPTR_FORMAT, size, (by_words ? "HeapWord" : "bytes"), p2i(from), p2i(to));
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
    uint blobs_count = 0;
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
      } else if (kind == AOTCodeEntry::Blob) {
        blobs_count++;
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
    log_debug(aot, codecache, exit)("  All Blobs: total=%u", blobs_count);
    log_debug(aot, codecache, exit)("  AOT code cache size: %u bytes, max entry's size: %u bytes", size, max_size);

    // Finalize header
    AOTCodeCache::Header* header = (AOTCodeCache::Header*)start;
    header->init(size, (uint)strings_count, strings_offset,
                 entries_count, new_entries_offset,
                 adapters_count, blobs_count);

    log_info(aot, codecache, exit)("Wrote %d AOT code entries to AOT Code Cache", entries_count);
  }
  return true;
}

//------------------Store/Load AOT code ----------------------

bool AOTCodeCache::store_code_blob(CodeBlob& blob, AOTCodeEntry::Kind entry_kind, uint id, const char* name, int entry_offset_count, int* entry_offsets) {
  AOTCodeCache* cache = open_for_dump();
  if (cache == nullptr) {
    return false;
  }
  assert(AOTCodeEntry::is_valid_entry_kind(entry_kind), "invalid entry_kind %d", entry_kind);

  if ((entry_kind == AOTCodeEntry::Adapter) && !AOTAdapterCaching) {
    return false;
  }
  log_debug(aot, codecache, stubs)("Writing blob '%s' to AOT Code Cache", name);

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

  if (!cache->write_relocations(blob)) {
    return false;
  }

  // Write entries offsets
  n = cache->write_bytes(&entry_offset_count, sizeof(int));
  if (n != sizeof(int)) {
    return false;
  }
  for (int i = 0; i < entry_offset_count; i++) {
    uint32_t off = (uint32_t)entry_offsets[i];
    n = cache->write_bytes(&off, sizeof(uint32_t));
    if (n != sizeof(uint32_t)) {
      return false;
    }
  }
  uint entry_size = cache->_write_position - entry_position;
  AOTCodeEntry* entry = new(cache) AOTCodeEntry(entry_kind, id,
                                                entry_position, entry_size, name_offset, name_size,
                                                blob_offset, has_oop_maps, blob.content_begin());
  log_debug(aot, codecache, stubs)("Wrote code blob '%s(id=%d)' to AOT Code Cache", name, id);
  return true;
}

CodeBlob* AOTCodeCache::load_code_blob(AOTCodeEntry::Kind entry_kind, uint id, const char* name, int entry_offset_count, int* entry_offsets) {
  AOTCodeCache* cache = open_for_use();
  if (cache == nullptr) {
    return nullptr;
  }
  assert(AOTCodeEntry::is_valid_entry_kind(entry_kind), "invalid entry_kind %d", entry_kind);

  if ((entry_kind == AOTCodeEntry::Adapter) && !AOTAdapterCaching) {
    return nullptr;
  }
  log_debug(aot, codecache, stubs)("Reading blob '%s' from AOT Code Cache", name);

  AOTCodeEntry* entry = cache->find_entry(entry_kind, id);
  if (entry == nullptr) {
    return nullptr;
  }
  AOTCodeReader reader(cache, entry);
  return reader.compile_code_blob(name, entry_offset_count, entry_offsets);
}

CodeBlob* AOTCodeReader::compile_code_blob(const char* name, int entry_offset_count, int* entry_offsets) {
  uint entry_position = _entry->offset();

  // Read name
  uint name_offset = entry_position + _entry->name_offset();
  uint name_size = _entry->name_size(); // Includes '/0'
  const char* stored_name = addr(name_offset);

  if (strncmp(stored_name, name, (name_size - 1)) != 0) {
    log_warning(aot, codecache, stubs)("Saved blob's name '%s' is different from the expected name '%s'",
                                       stored_name, name);
    ((AOTCodeCache*)_cache)->set_failed();
    report_load_failure();
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

  CodeBlob* code_blob = CodeBlob::create(archived_blob, stored_name, reloc_data, oop_maps);
  if (code_blob == nullptr) { // no space left in CodeCache
    return nullptr;
  }

  fix_relocations(code_blob);

  // Read entries offsets
  offset = read_position();
  int stored_count = *(int*)addr(offset);
  assert(stored_count == entry_offset_count, "entry offset count mismatch, count in AOT code cache=%d, expected=%d", stored_count, entry_offset_count);
  offset += sizeof(int);
  set_read_position(offset);
  for (int i = 0; i < stored_count; i++) {
    uint32_t off = *(uint32_t*)addr(offset);
    offset += sizeof(uint32_t);
    const char* entry_name = (_entry->kind() == AOTCodeEntry::Adapter) ? AdapterHandlerEntry::entry_name(i) : "";
    log_trace(aot, codecache, stubs)("Reading adapter '%s:%s' (0x%x) offset: 0x%x from AOT Code Cache",
                                      stored_name, entry_name, _entry->id(), off);
    entry_offsets[i] = off;
  }

  log_debug(aot, codecache, stubs)("Read blob '%s' from AOT Code Cache", name);
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
        reloc_data.at_put(idx, _table->id_for_address(dest, iter, &code_blob));
        break;
      }
      case relocInfo::runtime_call_w_cp_type:
        fatal("runtime_call_w_cp_type unimplemented");
        break;
      case relocInfo::external_word_type: {
        // Record offset of runtime target
        address target = ((external_word_Relocation*)iter.reloc())->target();
        reloc_data.at_put(idx, _table->id_for_address(target, iter, &code_blob));
        break;
      }
      case relocInfo::internal_word_type:
        break;
      case relocInfo::section_word_type:
        break;
      default:
        fatal("relocation %d unimplemented", (int)iter.type());
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
        fatal("runtime_call_w_cp_type unimplemented");
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
      default:
        fatal("relocation %d unimplemented", (int)iter.type());
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

//======================= AOTCodeAddressTable ===============

// address table ids for generated routines, external addresses and C
// string addresses are partitioned into positive integer ranges
// defined by the following positive base and max values
// i.e. [_extrs_base, _extrs_base + _extrs_max -1],
//      [_blobs_base, _blobs_base + _blobs_max -1],
//      ...
//      [_c_str_base, _c_str_base + _c_str_max -1],
#define _extrs_max 13
#define _blobs_max 10
#define _all_max   23

#define _extrs_base 0
#define _blobs_base (_extrs_base + _extrs_max)
#define _blobs_end  (_blobs_base + _blobs_max)

#if (_blobs_end > _all_max)
#error AOTCodeAddress table ranges need adjusting
#endif

#define SET_ADDRESS(type, addr)                           \
  {                                                       \
    type##_addr[type##_length++] = (address) (addr);      \
    assert(type##_length <= type##_max, "increase size"); \
  }

static bool initializing_extrs = false;

void AOTCodeAddressTable::init_extrs() {
  if (_extrs_complete || initializing_extrs) return; // Done already
  initializing_extrs = true;
  _extrs_addr = NEW_C_HEAP_ARRAY(address, _extrs_max, mtCode);

  _extrs_length = 0;

  // Recored addresses of VM runtime methods
  SET_ADDRESS(_extrs, SharedRuntime::fixup_callers_callsite);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method_abstract);
  SET_ADDRESS(_extrs, SharedRuntime::handle_wrong_method_ic_miss);
#if INCLUDE_G1GC
  SET_ADDRESS(_extrs, G1BarrierSetRuntime::write_ref_field_post_entry);
  SET_ADDRESS(_extrs, G1BarrierSetRuntime::write_ref_field_pre_entry);
#endif
#if INCLUDE_SHENANDOAHGC
  SET_ADDRESS(_extrs, ShenandoahRuntime::write_ref_field_pre);
  SET_ADDRESS(_extrs, ShenandoahRuntime::load_reference_barrier_phantom);
  SET_ADDRESS(_extrs, ShenandoahRuntime::load_reference_barrier_phantom_narrow);
#endif
#if INCLUDE_ZGC
  SET_ADDRESS(_extrs, ZBarrierSetRuntime::load_barrier_on_phantom_oop_field_preloaded_addr());
#if defined(AMD64)
  SET_ADDRESS(_extrs, &ZPointerLoadShift);
#endif
#endif
#ifdef COMPILER2
  SET_ADDRESS(_extrs, OptoRuntime::handle_exception_C);
#endif
#ifndef ZERO
#if defined(AMD64) || defined(AARCH64) || defined(RISCV64)
  SET_ADDRESS(_extrs, MacroAssembler::debug64);
#endif
#endif // ZERO

  _extrs_complete = true;
  log_debug(aot, codecache, init)("External addresses recorded");
}

static bool initializing_shared_blobs = false;

void AOTCodeAddressTable::init_shared_blobs() {
  if (_complete || initializing_shared_blobs) return; // Done already
  initializing_shared_blobs = true;
  _blobs_addr = NEW_C_HEAP_ARRAY(address, _blobs_max, mtCode);

  _blobs_length = 0;       // for shared blobs

  // Recored addresses of generated code blobs
  SET_ADDRESS(_blobs, SharedRuntime::get_handle_wrong_method_stub());
  SET_ADDRESS(_blobs, SharedRuntime::get_ic_miss_stub());

  _shared_blobs_complete = true;
  log_debug(aot, codecache, init)("Early shared blobs recorded");
  _complete = true;
}

#undef SET_ADDRESS

AOTCodeAddressTable::~AOTCodeAddressTable() {
  if (_extrs_addr != nullptr) {
    FREE_C_HEAP_ARRAY(address, _extrs_addr);
  }
  if (_blobs_addr != nullptr) {
    FREE_C_HEAP_ARRAY(address, _blobs_addr);
  }
}

#ifdef PRODUCT
#define MAX_STR_COUNT 200
#else
#define MAX_STR_COUNT 500
#endif
#define _c_str_max  MAX_STR_COUNT
#define _c_str_base _all_max

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
    return _cache->_table->add_C_string(str);
  }
  return str;
}

const char* AOTCodeAddressTable::add_C_string(const char* str) {
  if (_extrs_complete) {
    LogStreamHandle(Trace, aot, codecache, stringtable) log; // ctor outside lock
    MutexLocker ml(AOTCodeCStrings_lock, Mutex::_no_safepoint_check_flag);
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
      if (log.is_enabled()) {
        log.print_cr("add_C_string: [%d] " INTPTR_FORMAT " '%s'", _C_strings_count, p2i(dup), dup);
      }
      return dup;
    } else {
      fatal("Number of C strings >= MAX_STR_COUNT");
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
  return -1;
}

address AOTCodeAddressTable::address_for_id(int idx) {
  if (!_extrs_complete) {
    fatal("AOT Code Cache VM runtime addresses table is not complete");
  }
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
  }
  // no need to compare unsigned id against 0
  if (/* id >= _extrs_base && */ id < _extrs_length) {
    return _extrs_addr[id - _extrs_base];
  }
  if (id >= _blobs_base && id < _blobs_base + _blobs_length) {
    return _blobs_addr[id - _blobs_base];
  }
  if (id >= _c_str_base && id < (_c_str_base + (uint)_C_strings_count)) {
    return address_for_C_string(id - _c_str_base);
  }
  fatal("Incorrect id %d for AOT Code Cache addresses table", id);
  return nullptr;
}

int AOTCodeAddressTable::id_for_address(address addr, RelocIterator reloc, CodeBlob* code_blob) {
  if (!_extrs_complete) {
    fatal("AOT Code Cache VM runtime addresses table is not complete");
  }
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
    StubCodeDesc* desc = StubCodeDesc::desc_for(addr);
    if (desc == nullptr) {
      desc = StubCodeDesc::desc_for(addr + frame::pc_return_offset);
    }
    const char* sub_name = (desc != nullptr) ? desc->name() : "<unknown>";
    fatal("Address " INTPTR_FORMAT " for Stub:%s is missing in AOT Code Cache addresses table", p2i(addr), sub_name);
  } else {
    CodeBlob* cb = CodeCache::find_blob(addr);
    if (cb != nullptr) {
      // Search in code blobs
      int id_base = _blobs_base;
      id = search_address(addr, _blobs_addr, _blobs_length);
      if (id < 0) {
        fatal("Address " INTPTR_FORMAT " for Blob:%s is missing in AOT Code Cache addresses table", p2i(addr), cb->name());
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
          reloc.print_current_on(tty);
          code_blob->print_on(tty);
          code_blob->print_code_on(tty);
          fatal("Address " INTPTR_FORMAT " for runtime target '%s+%d' is missing in AOT Code Cache addresses table", p2i(addr), func_name, offset);
        } else {
          reloc.print_current_on(tty);
          code_blob->print_on(tty);
          code_blob->print_code_on(tty);
          os::find(addr, tty);
          fatal("Address " INTPTR_FORMAT " for <unknown>/('%s') is missing in AOT Code Cache addresses table", p2i(addr), (const char*)addr);
        }
      } else {
        return _extrs_base + id;
      }
    }
  }
  return id;
}

void AOTCodeCache::print_on(outputStream* st) {
  AOTCodeCache* cache = open_for_use();
  if (cache != nullptr) {
    uint count = cache->_load_header->entries_count();
    uint* search_entries = (uint*)cache->addr(cache->_load_header->entries_offset()); // [id, index]
    AOTCodeEntry* load_entries = (AOTCodeEntry*)(search_entries + 2 * count);

    for (uint i = 0; i < count; i++) {
      // Use search_entries[] to order ouput
      int index = search_entries[2*i + 1];
      AOTCodeEntry* entry = &(load_entries[index]);

      uint entry_position = entry->offset();
      uint name_offset = entry->name_offset() + entry_position;
      const char* saved_name = cache->addr(name_offset);

      st->print_cr("%4u: entry_idx:%4u Kind:%u Id:%u size=%u '%s'",
                   i, index, entry->kind(), entry->id(), entry->size(), saved_name);
    }
  } else {
    st->print_cr("failed to map code cache");
  }
}


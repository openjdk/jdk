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

#include "cds/cds_globals.hpp"
#include "cds/cdsAccess.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/javaAssertions.hpp"
#include "code/aotCodeCache.hpp"
#include "code/codeCache.hpp"
#include "gc/g1/g1BarrierSetRuntime.hpp"
#include "gc/shared/gcConfig.hpp"
#include "logging/logStream.hpp"
#include "memory/memoryReserver.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif
/*
#include "asm/macroAssembler.hpp"
#include "cds/cdsAccess.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "ci/ciConstant.hpp"
#include "ci/ciEnv.hpp"
#include "ci/ciField.hpp"
#include "ci/ciMethod.hpp"
#include "ci/ciMethodData.hpp"
#include "ci/ciObject.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "classfile/javaAssertions.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmIntrinsics.hpp"
#include "code/aotCodeCache.hpp"
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "code/oopRecorder.inline.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compilationPolicy.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileTask.hpp"
#include "gc/g1/g1BarrierSetRuntime.hpp"
#include "gc/shared/gcConfig.hpp"
#include "logging/log.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/universe.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/trainingData.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/atomic.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/threadIdentifier.hpp"
#include "utilities/ostream.hpp"
#include "utilities/spinYield.hpp"
*/

#include <sys/stat.h>
#include <errno.h>

static void exit_vm_on_load_failure() {
  if (AbortVMOnAOTCodeFailure) {
    vm_exit_during_initialization("Unable to use AOT Code Cache.", nullptr);
  }
  LoadAOTCode  = false;
}

static void exit_vm_on_store_failure() {
  if (AbortVMOnAOTCodeFailure) {
    tty->print_cr("Unable to create AOT Code Cache.");
    vm_abort(false);
  }
  LoadAOTCode  = false;
  StoreAOTCode = false;
}

uint AOTCodeCache::max_aot_code_size() {
  return (uint)AOTCodeMaxSize;
}

void AOTCodeCache::initialize() {
  if (LoadAOTCode && (!CDSConfig::is_using_archive() ||
                       CDSAccess::get_aot_code_size() == 0)) {
    if (!CDSConfig::is_using_archive()) {
      log_warning(aot, codecache, init)("AOT Cache is not used");
    }
    if (CDSAccess::get_aot_code_size() == 0) {
      log_warning(aot, codecache, init)("AOT Code Cache is empty");
    }
    exit_vm_on_load_failure();
    return;
  }
  if (StoreAOTCode && !CDSConfig::is_dumping_final_static_archive()) {
    log_warning(aot, codecache, init)("AOT Cache is not used");
    exit_vm_on_store_failure();
    return;
  }
  if (LoadAOTCode && StoreAOTCode) {
    log_warning(aot, codecache, init)("Incremental updates to AOT Code Cache is not supported");
    exit_vm_on_store_failure();
    return;
  }
  if (LoadAOTCode || StoreAOTCode) {
    if (!open_cache()) {
      if (LoadAOTCode) {
        exit_vm_on_load_failure();
      } else {
        exit_vm_on_store_failure();
      }
      return;
    }
    if (StoreAOTCode) {
      FLAG_SET_DEFAULT(ForceUnreachable, true);
    }
    FLAG_SET_DEFAULT(DelayCompilerStubsGeneration, false);
  }
}

void AOTCodeCache::init2() {
  if (!is_on()) {
    return;
  }
  if (!verify_vm_config()) {
    close();
    exit_vm_on_load_failure();
  }
  // initialize the table of external routines so we can save
  // generated code blobs that reference them
  init_extrs_table();
}

AOTCodeCache* AOTCodeCache::_cache = nullptr;

bool AOTCodeCache::open_cache() {
  AOTCodeCache* cache = new AOTCodeCache();
  if (cache->failed()) {
    delete cache;
    _cache = nullptr;
    return false;
  }
  _cache = cache;
  return true;
}

void AOTCodeCache::close() {
log_info(aot, codecache, exit)("Storing AOT Code: %s", is_on() ? "on" : "off");
  if (is_on()) {
    delete _cache; // Free memory
    _cache = nullptr;
  }
}

#define DATA_ALIGNMENT HeapWordSize

AOTCodeCache::AOTCodeCache() :
  _load_header(nullptr),
  _load_buffer(nullptr),
  _store_buffer(nullptr),
  _C_store_buffer(nullptr),
  _write_position(0),
  _load_size(0),
  _store_size(0),
  _for_read (LoadAOTCode),
  _for_write(StoreAOTCode),
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
  if (_for_read) {
    // Read cache
    size_t load_size = CDSAccess::get_aot_code_size();
    ReservedSpace rs = MemoryReserver::reserve(load_size, mtCode);
    if (!rs.is_reserved()) {
      log_warning(aot, codecache, init)("Failed to reserved %u bytes of memory for mapping AOT code region into AOT Code Cache", (uint)load_size);
      set_failed();
      return;
    }
    if (!CDSAccess::map_aot_code(rs)) {
      log_warning(aot, codecache, init)("Failed to read/mmap cached code region into AOT Code Cache");
      set_failed();
      return;
    }

    _load_size = (uint)load_size;
    _load_buffer = (char*)rs.base();
    assert(is_aligned(_load_buffer, DATA_ALIGNMENT), "load_buffer is not aligned");
    log_info(aot, codecache, init)("Mapped %u bytes at address " INTPTR_FORMAT " at AOT Code Cache", _load_size, p2i(_load_buffer));

    _load_header = (Header*)addr(0);
    if (!_load_header->verify_config(_load_size)) {
      set_failed();
      return;
    }
    log_info(aot, codecache, init)("Read header from AOT Code Cache");
    // Read strings
    load_strings();
  }
  if (_for_write) {
    _C_store_buffer = NEW_C_HEAP_ARRAY(char, max_aot_code_size() + DATA_ALIGNMENT, mtCode);
    _store_buffer = align_up(_C_store_buffer, DATA_ALIGNMENT);
    // Entries allocated at the end of buffer in reverse (as on stack).
    _store_entries = (AOTCodeEntry*)align_up(_C_store_buffer + max_aot_code_size(), DATA_ALIGNMENT);
    log_info(aot, codecache, init)("Allocated store buffer at address " INTPTR_FORMAT " of size %d", p2i(_store_buffer), max_aot_code_size());
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
  if (for_write()) { // Finalize cache
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
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created by product VM, it can't be used by debug VM");
    return false;
  }
#else
  if ((_flags & debugVM) != 0) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created by debug VM, it can't be used by product VM");
    return false;
  }
#endif

  CollectedHeap::Name aot_gc = (CollectedHeap::Name)_gc;
  if (aot_gc != Universe::heap()->kind()) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with different GC: %s vs current %s", GCConfig::hs_err_name(aot_gc), GCConfig::hs_err_name());
    return false;
  }

  if (((_flags & compressedOops) != 0) != UseCompressedOops) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with UseCompressedOops = %s", UseCompressedOops ? "false" : "true");
    return false;
  }
  if (((_flags & compressedClassPointers) != 0) != UseCompressedClassPointers) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with UseCompressedClassPointers = %s", UseCompressedClassPointers ? "false" : "true");
    return false;
  }

  if (((_flags & systemClassAssertions) != 0) != JavaAssertions::systemClassDefault()) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with JavaAssertions::systemClassDefault() = %s", JavaAssertions::systemClassDefault() ? "disabled" : "enabled");
    return false;
  }
  if (((_flags & userClassAssertions) != 0) != JavaAssertions::userClassDefault()) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with JavaAssertions::userClassDefault() = %s", JavaAssertions::userClassDefault() ? "disabled" : "enabled");
    return false;
  }

  if (((_flags & enableContendedPadding) != 0) != EnableContended) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with EnableContended = %s", EnableContended ? "false" : "true");
    return false;
  }
  if (((_flags & restrictContendedPadding) != 0) != RestrictContended) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with RestrictContended = %s", RestrictContended ? "false" : "true");
    return false;
  }
  if (_compressedOopShift != (uint)CompressedOops::shift()) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with CompressedOops::shift() = %d vs current %d", _compressedOopShift, CompressedOops::shift());
    return false;
  }
  if (_compressedKlassShift != (uint)CompressedKlassPointers::shift()) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with CompressedKlassPointers::shift() = %d vs current %d", _compressedKlassShift, CompressedKlassPointers::shift());
    return false;
  }
  if (_contendedPaddingWidth != (uint)ContendedPaddingWidth) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with ContendedPaddingWidth = %d vs current %d", _contendedPaddingWidth, ContendedPaddingWidth);
    return false;
  }
  if (_objectAlignment != (uint)ObjectAlignmentInBytes) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: it was created with ObjectAlignmentInBytes = %d vs current %d", _objectAlignment, ObjectAlignmentInBytes);
    return false;
  }
  return true;
}

bool AOTCodeCache::Header::verify_config(uint load_size) const {
  if (_version != AOT_CODE_VERSION) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: different AOT Code version %d vs %d recorded in AOT Cache", AOT_CODE_VERSION, _version);
    return false;
  }
  if (load_size < _cache_size) {
    log_warning(aot, codecache, init)("Disable AOT Code Cache: AOT Code Cache size %d < %d recorded in AOT Code header", load_size, _cache_size);
    return false;
  }
  return true;
}

AOTCodeCache* AOTCodeCache::open_for_read() {
  if (AOTCodeCache::is_on_for_read()) {
    return AOTCodeCache::cache();
  }
  return nullptr;
}

AOTCodeCache* AOTCodeCache::open_for_write() {
  if (AOTCodeCache::is_on_for_write()) {
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

uint AOTCodeCache::write_bytes(const void* buffer, uint nbytes) {
  assert(for_write(), "Code Cache file is not created");
  if (nbytes == 0) {
    return 0;
  }
  uint new_position = _write_position + nbytes;
  if (new_position >= (uint)((char*)_store_entries - _store_buffer)) {
    log_warning(aot, codecache)("Failed to write %d bytes at offset %d to AOT Code Cache. Increase CachedCodeMaxSize.",
                     nbytes, _write_position);
    set_failed();
    exit_vm_on_store_failure();
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
  assert(_for_read, "sanity");
  uint count = _load_header->entries_count();
  if (_load_entries == nullptr) {
    // Read it
    _search_entries = (uint*)addr(_load_header->entries_offset()); // [id, index]
    _load_entries = (AOTCodeEntry*)(_search_entries + 2 * count);
    log_info(aot, codecache, init)("Read %d entries table at offset %d from AOT Code Cache", count, _load_header->entries_offset());
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
    assert(total_size < max_aot_code_size(), "AOT Code size (" UINT32_FORMAT " bytes) is greater than CachedCodeMaxSize (" UINT32_FORMAT " bytes).", total_size, max_aot_code_size());

    // Create ordered search table for entries [id, index];
    uint* search = NEW_C_HEAP_ARRAY(uint, search_count, mtCode);
    // Allocate in AOT Cache buffer
    char* buffer = (char *)CDSAccess::allocate_aot_code(total_size + DATA_ALIGNMENT);
    char* start = align_up(buffer, DATA_ALIGNMENT);
    char* current = start + header_size; // Skip header

    AOTCodeEntry* entries_address = _store_entries; // Pointer to latest entry
    uint adapters_count = 0;
    uint total_blobs_count = 0;
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
        FREE_C_HEAP_ARRAY(char, buffer);
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
        total_blobs_count++;
      }
    }
    if (entries_count == 0) {
      log_info(aot, codecache, exit)("AOT Code Cache was not created: no entires");
      FREE_C_HEAP_ARRAY(char, buffer);
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
    log_info(aot, codecache, exit)("Wrote %d AOTCodeEntry entries (%d max size) to AOT Code Cache", entries_count, max_size);
    log_info(aot, codecache, exit)("  Adapters:  total=%d", adapters_count);
    log_info(aot, codecache, exit)("  All Blobs: total=%d", total_blobs_count);

    uint size = (current - start);
    assert(size <= total_size, "%d > %d", size , total_size);

    // Finalize header
    AOTCodeCache::Header* header = (AOTCodeCache::Header*)start;
    header->init(size, (uint)strings_count, strings_offset,
                 entries_count, new_entries_offset);
    log_info(aot, codecache, exit)("Wrote %d bytes to AOT Code Cache", size);
  }
  return true;
}

//------------------Store/Load AOT code ----------------------

bool AOTCodeCache::store_exception_blob(CodeBuffer* buffer, int pc_offset) {
  AOTCodeCache* cache = open_for_write();
  if (cache == nullptr) {
    return false;
  }
  log_info(aot, codecache, stubs)("Writing blob '%s' to AOT Code Cache", buffer->name());

#ifdef ASSERT
  LogStreamHandle(Debug, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    buffer->print_on(&log);
    buffer->decode();
  }
#endif
  // we need to take a lock to prevent race between compiler threads generating AOT code
  // and the main thread generating adapter
  MutexLocker ml(Compile_lock);
  if (!cache->align_write()) {
    return false;
  }
  uint entry_position = cache->_write_position;

  // Write pc_offset
  uint n = cache->write_bytes(&pc_offset, sizeof(int));
  if (n != sizeof(int)) {
    return false;
  }

  // Write name
  const char* name = buffer->name();
  uint name_offset = cache->_write_position - entry_position;
  uint name_size = (uint)strlen(name) + 1; // Includes '/0'
  n = cache->write_bytes(name, name_size);
  if (n != name_size) {
    return false;
  }

  // Write code section
  if (!cache->align_write()) {
    return false;
  }
  uint code_offset = cache->_write_position - entry_position;
  uint code_size = 0;
  if (!cache->write_code(buffer, code_size)) {
    return false;
  }
  // Write relocInfo array
  uint reloc_offset = cache->_write_position - entry_position;
  uint reloc_size = 0;
  if (!cache->write_relocations(buffer, reloc_size)) {
    return false;
  }

  uint entry_size = cache->_write_position - entry_position;
  AOTCodeEntry* entry = new(cache) AOTCodeEntry(AOTCodeEntry::Blob, (uint32_t)999,
                                                entry_position, entry_size, name_offset, name_size,
                                                code_offset, code_size, reloc_offset, reloc_size);
  log_info(aot, codecache, stubs)("Wrote blob '%s' to AOT Code Cache", name);
  return true;
}

bool AOTCodeCache::load_exception_blob(CodeBuffer* buffer, int* pc_offset) {
  AOTCodeCache* cache = open_for_read();
  if (cache == nullptr) {
    return false;
  }
  log_info(aot, codecache, stubs)("Reading blob from AOT Code Cache");

#ifdef ASSERT
  LogStreamHandle(Debug, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    buffer->print_on(&log);
  }
#endif
  AOTCodeEntry* entry = cache->find_entry(AOTCodeEntry::Blob, 999);
  if (entry == nullptr) {
    return false;
  }
  AOTCodeReader reader(cache, entry);
  return reader.compile_blob(buffer, pc_offset);
}

bool AOTCodeReader::compile_blob(CodeBuffer* buffer, int* pc_offset) {
  uint entry_position = _entry->offset();

  // Read pc_offset
  *pc_offset = *(int*)addr(entry_position);

  // Read name
  uint name_offset = entry_position + _entry->name_offset();
  uint name_size = _entry->name_size(); // Includes '/0'
  const char* name = addr(name_offset);

  log_info(aot, codecache, stubs)("Reading blob '%s' with pc_offset %d from AOT Code Cache",
                                  name, *pc_offset);

  if (strncmp(buffer->name(), name, (name_size - 1)) != 0) {
    log_warning(aot, codecache, stubs)("Saved blob's name '%s' is different from '%s'",
                                       name, buffer->name());
    ((AOTCodeCache*)_cache)->set_failed();
    exit_vm_on_load_failure();
    return false;
  }

  // Create fake original CodeBuffer
  CodeBuffer orig_buffer(name);

  // Read code
  uint code_offset = entry_position + _entry->code_offset();
  if (!read_code(buffer, &orig_buffer, code_offset)) {
    return false;
  }

  // Read relocations
  uint reloc_offset = entry_position + _entry->reloc_offset();
  set_read_position(reloc_offset);
  if (!read_relocations(buffer, &orig_buffer)) {
    return false;
  }

  log_info(aot, codecache, stubs)("Read blob '%s' from AOT Code Cache", name);
#ifdef ASSERT
  LogStreamHandle(Debug, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    buffer->print_on(&log);
    buffer->decode();
  }
#endif
  return true;
}

bool AOTCodeCache::store_adapter(CodeBuffer* buffer, uint32_t id, const char* name, uint32_t *entry_offset) {
  assert(CDSConfig::is_dumping_adapters(), "must be");
  AOTCodeCache* cache = open_for_write();
  if (cache == nullptr) {
    return false;
  }
  log_info(aot, codecache, stubs)("Writing adapter '%s' (0x%x) to AOT Code Cache", name, id);

#ifdef ASSERT
  LogStreamHandle(Debug, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    buffer->print_on(&log);
    buffer->decode();
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
  // Write code section
  if (!cache->align_write()) {
    return false;
  }
  uint code_offset = cache->_write_position - entry_position;
  uint code_size = 0;
  if (!cache->write_code(buffer, code_size)) {
    return false;
  }
  // Write relocInfo array
  uint reloc_offset = cache->_write_position - entry_position;
  uint reloc_size = 0;
  if (!cache->write_relocations(buffer, reloc_size)) {
    return false;
  }

  // Write entries offsets
  int extras_count = AdapterHandlerEntry::ENTRIES_COUNT;
  n = cache->write_bytes(&extras_count, sizeof(int));
  if (n != sizeof(int)) {
    return false;
  }
  for (int i = 0; i < extras_count; i++) {
    uint32_t off = entry_offset[i];
    const char* entry_name = AdapterHandlerEntry::entry_name(i);
    log_debug(aot, codecache, stubs)("Writing adapter '%s:%s' (0x%x) offset: 0x%x to AOT Code Cache",
                                      name, entry_name, id, off);
    n = cache->write_bytes(&off, sizeof(uint32_t));
    if (n != sizeof(uint32_t)) {
      return false;
    }
  }
  uint entry_size = cache->_write_position - entry_position;
  AOTCodeEntry* entry = new (cache) AOTCodeEntry(AOTCodeEntry::Adapter, id,
                                                 entry_position, entry_size, name_offset, name_size,
                                                 code_offset, code_size, reloc_offset, reloc_size);
  log_info(aot, codecache, stubs)("Wrote adapter '%s' (0x%x) to AOT Code Cache", name, id);
  return true;
}

bool AOTCodeCache::load_adapter(CodeBuffer* buffer, uint32_t id, const char* name, uint32_t offsets[4]) {
  AOTCodeCache* cache = open_for_read();
  if (cache == nullptr) {
    return false;
  }
  log_info(aot, codecache, stubs)("Looking up adapter %s (0x%x) in AOT Code Cache", name, id);

#ifdef ASSERT
  LogStreamHandle(Debug, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    buffer->print_on(&log);
  }
#endif
  AOTCodeEntry* entry = cache->find_entry(AOTCodeEntry::Adapter, id);
  if (entry == nullptr) {
    return false;
  }
  AOTCodeReader reader(cache, entry);
  return reader.compile_adapter(buffer, name, offsets);
}

bool AOTCodeReader::compile_adapter(CodeBuffer* buffer, const char* name, uint32_t offsets[4]) {
  uint entry_position = _entry->offset();

  // Read name
  uint name_offset = entry_position + _entry->name_offset();
  uint name_size = _entry->name_size(); // Includes '/0'
  const char* stored_name = addr(name_offset);

  log_info(aot, codecache, stubs)("Reading adapter '%s' from AOT Code Cache", name);

  if (strncmp(stored_name, name, (name_size - 1)) != 0) {
    log_warning(aot, codecache, stubs)("Saved adapter's name '%s' is different from '%s'",
                                       stored_name, name);
    // n.b. this is not fatal -- we have just seen a hash id clash
    // so no need to call cache->set_failed()
    return false;
  }

  // Create fake original CodeBuffer
  CodeBuffer orig_buffer(name);

  // Read code
  uint code_offset = entry_position + _entry->code_offset();
  if (!read_code(buffer, &orig_buffer, code_offset)) {
    return false;
  }

  // Read relocations
  uint reloc_offset = entry_position + _entry->reloc_offset();
  set_read_position(reloc_offset);
  if (!read_relocations(buffer, &orig_buffer)) {
    return false;
  }

  // Read entries offsets
  uint offset = read_position();
  int offsets_count = *(int*)addr(offset);
  offset += sizeof(int);
  assert(offsets_count == AdapterHandlerEntry::ENTRIES_COUNT, "wrong caller expectations");
  set_read_position(offset);
  for (int i = 0; i < offsets_count; i++) {
    uint32_t off = *(uint32_t*)addr(offset);
    offset += sizeof(uint32_t);
    const char* entry_name = AdapterHandlerEntry::entry_name(i);
    log_debug(aot, codecache, stubs)("Reading adapter '%s:%s' (0x%x) offset: 0x%x from AOT Code Cache",
                                      stored_name, entry_name, _entry->id(), off);
    offsets[i] = off;
  }
  log_debug(aot, codecache, stubs)("Read adapter '%s' (0x%x) from AOT Code Cache",
                                   stored_name, _entry->id());
#ifdef ASSERT
  LogStreamHandle(Debug, aot, codecache, stubs) log;
  if (log.is_enabled()) {
    FlagSetting fs(PrintRelocations, true);
    buffer->print_on(&log);
    buffer->decode();
  }
#endif
  return true;
}

// ------------ process code and data --------------

bool AOTCodeCache::write_relocations(CodeBuffer* buffer, uint& all_reloc_size) {
  uint all_reloc_count = 0;
  for (int i = 0; i < (int)CodeBuffer::SECT_LIMIT; i++) {
    CodeSection* cs = buffer->code_section(i);
    uint reloc_count = cs->has_locs() ? cs->locs_count() : 0;
    all_reloc_count += reloc_count;
  }
  all_reloc_size = all_reloc_count * sizeof(relocInfo);
  bool success = true;
  uint* reloc_data = NEW_C_HEAP_ARRAY(uint, all_reloc_count, mtCode);
  for (int i = 0; i < (int)CodeBuffer::SECT_LIMIT; i++) {
    CodeSection* cs = buffer->code_section(i);
    int reloc_count = cs->has_locs() ? cs->locs_count() : 0;
    uint n = write_bytes(&reloc_count, sizeof(int));
    if (n != sizeof(int)) {
      success = false;
      break;
    }
    if (reloc_count == 0) {
      continue;
    }
    // Write _locs_point (as offset from start)
    int locs_point_off = cs->locs_point_off();
    n = write_bytes(&locs_point_off, sizeof(int));
    if (n != sizeof(int)) {
      success = false;
      break;
    }
    relocInfo* reloc_start = cs->locs_start();
    uint reloc_size      = reloc_count * sizeof(relocInfo);
    n = write_bytes(reloc_start, reloc_size);
    if (n != reloc_size) {
      success = false;
      break;
    }
    LogStreamHandle(Info, aot, codecache, reloc) log;
    if (log.is_enabled()) {
      log.print_cr("======== write code section %d relocations [%d]:", i, reloc_count);
    }
    // Collect additional data
    RelocIterator iter(cs);
    bool has_immediate = false;
    int j = 0;
    while (iter.next()) {
      reloc_data[j] = 0; // initialize
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
          reloc_data[j] = _table->id_for_address(dest, iter, buffer);
          break;
        }
        case relocInfo::runtime_call_w_cp_type:
          fatal("runtime_call_w_cp_type unimplemented");
          break;
        case relocInfo::external_word_type: {
          // Record offset of runtime target
          address target = ((external_word_Relocation*)iter.reloc())->target();
          reloc_data[j] = _table->id_for_address(target, iter, buffer);
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
      j++;
    }
    assert(j <= (int)reloc_count, "sanity");
    // Write additional relocation data: uint per relocation
    uint data_size = reloc_count * sizeof(uint);
    n = write_bytes(reloc_data, data_size);
    if (n != data_size) {
      success = false;
      break;
    }
  } // for(i < SECT_LIMIT)
  FREE_C_HEAP_ARRAY(uint, reloc_data);
  return success;
}

// Repair the pc relative information in the code after load
bool AOTCodeReader::read_relocations(CodeBuffer* buffer, CodeBuffer* orig_buffer) {
  bool success = true;
  for (int i = 0; i < (int)CodeBuffer::SECT_LIMIT; i++) {
    uint code_offset = read_position();
    int reloc_count = *(int*)addr(code_offset);
    code_offset += sizeof(int);
    if (reloc_count == 0) {
      set_read_position(code_offset);
      continue;
    }
    // Read _locs_point (as offset from start)
    int locs_point_off = *(int*)addr(code_offset);
    code_offset += sizeof(int);
    uint reloc_size = reloc_count * sizeof(relocInfo);
    CodeSection* cs  = buffer->code_section(i);
    if (cs->locs_capacity() < reloc_count) {
      cs->expand_locs(reloc_count);
    }
    relocInfo* reloc_start = cs->locs_start();
    copy_bytes(addr(code_offset), (address)reloc_start, reloc_size);
    code_offset += reloc_size;
    cs->set_locs_end(reloc_start + reloc_count);
    cs->set_locs_point(cs->start() + locs_point_off);

    // Read additional relocation data: uint per relocation
    uint  data_size  = reloc_count * sizeof(uint);
    uint* reloc_data = (uint*)addr(code_offset);
    code_offset += data_size;
    set_read_position(code_offset);
    LogStreamHandle(Info, aot, codecache, reloc) log;
    if (log.is_enabled()) {
      log.print_cr("======== read code section %d relocations [%d]:", i, reloc_count);
    }
    RelocIterator iter(cs);
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
          iter.reloc()->fix_relocation_after_move(orig_buffer, buffer);
          break;
        }
        case relocInfo::internal_word_type:
          iter.reloc()->fix_relocation_after_move(orig_buffer, buffer);
          break;
        case relocInfo::section_word_type:
          iter.reloc()->fix_relocation_after_move(orig_buffer, buffer);
          break;
        default:
          fatal("relocation %d unimplemented", (int)iter.type());
          break;
      }
      if (success && log.is_enabled()) {
        iter.print_current_on(&log);
      }
      j++;
    }
    assert(j <= (int)reloc_count, "sanity");
  }
  return success;
}

bool AOTCodeCache::write_code(CodeBuffer* buffer, uint& code_size) {
  assert(_write_position == align_up(_write_position, DATA_ALIGNMENT), "%d not aligned to %d", _write_position, DATA_ALIGNMENT);
  //assert(buffer->blob() != nullptr, "sanity");
  uint code_offset = _write_position;
  uint cb_total_size = (uint)buffer->total_content_size();
  // Write information about Code sections first.
  AOTCodeSection aot_cs[CodeBuffer::SECT_LIMIT];
  uint aot_cs_size = (uint)(sizeof(AOTCodeSection) * CodeBuffer::SECT_LIMIT);
  uint offset = align_up(aot_cs_size, DATA_ALIGNMENT);
  uint total_size = 0;
  for (int i = 0; i < (int)CodeBuffer::SECT_LIMIT; i++) {
    const CodeSection* cs = buffer->code_section(i);
    assert(cs->mark() == nullptr, "CodeSection::_mark is not implemented");
    uint cs_size = (uint)cs->size();
    aot_cs[i]._size = cs_size;
    aot_cs[i]._origin_address = (cs_size == 0) ? nullptr : cs->start();
    aot_cs[i]._offset = (cs_size == 0) ? 0 : (offset + total_size);
    assert(cs->mark() == nullptr, "CodeSection::_mark is not implemented");
    total_size += align_up(cs_size, DATA_ALIGNMENT);
  }
  uint n = write_bytes(aot_cs, aot_cs_size);
  if (n != aot_cs_size) {
    return false;
  }
  if (!align_write()) {
    return false;
  }
  assert(_write_position == (code_offset + offset), "%d  != (%d + %d)", _write_position, code_offset, offset);
  for (int i = 0; i < (int)CodeBuffer::SECT_LIMIT; i++) {
    const CodeSection* cs = buffer->code_section(i);
    uint cs_size = (uint)cs->size();
    if (cs_size == 0) {
      continue;  // skip trivial section
    }
    assert((_write_position - code_offset) == aot_cs[i]._offset, "%d != %d", _write_position, aot_cs[i]._offset);
    // Write code
    n = write_bytes(cs->start(), cs_size);
    if (n != cs_size) {
      return false;
    }
    if (!align_write()) {
      return false;
    }
  }
  assert((_write_position - code_offset) == (offset + total_size), "(%d - %d) != (%d + %d)", _write_position, code_offset, offset, total_size);
  code_size = total_size;
  return true;
}

bool AOTCodeReader::read_code(CodeBuffer* buffer, CodeBuffer* orig_buffer, uint code_offset) {
  assert(code_offset == align_up(code_offset, DATA_ALIGNMENT), "%d not aligned to %d", code_offset, DATA_ALIGNMENT);
  AOTCodeSection* aot_cs = (AOTCodeSection*)addr(code_offset);
  for (int i = 0; i < (int)CodeBuffer::SECT_LIMIT; i++) {
    CodeSection* cs = buffer->code_section(i);
    // Read original section size and address.
    uint orig_size = aot_cs[i]._size;
    log_debug(aot, codecache)("======== read code section %d [%d]:", i, orig_size);
    uint orig_size_align = align_up(orig_size, DATA_ALIGNMENT);
    if (i != (int)CodeBuffer::SECT_INSTS) {
      buffer->initialize_section_size(cs, orig_size_align);
    }
    if (orig_size_align > (uint)cs->capacity()) { // Will not fit
      log_info(aot, codecache)("original code section %d size %d > current capacity %d",
                                i, orig_size, cs->capacity());
      return false;
    }
    if (orig_size == 0) {
      assert(cs->size() == 0, "should match");
      continue;  // skip trivial section
    }
    address orig_start = aot_cs[i]._origin_address;

    // Populate fake original buffer (no code allocation in CodeCache).
    // It is used for relocations to calculate sections addesses delta.
    CodeSection* orig_cs = orig_buffer->code_section(i);
    assert(!orig_cs->is_allocated(), "This %d section should not be set", i);
    orig_cs->initialize(orig_start, orig_size);

    // Load code to new buffer.
    address code_start = cs->start();
    copy_bytes(addr(aot_cs[i]._offset + code_offset), code_start, orig_size_align);
    cs->set_end(code_start + orig_size);
  }

  return true;
}

//======================= AOTCodeAddressTable ===============

// address table ids for generated routines, external addresses and C
// string addresses are partitioned into positive integer ranges
// defined by the following positive base and max values
// i.e. [_extrs_base, _extrs_base + _extrs_max -1],
//      [_blobs_base, _blobs_base + _blobs_max -1],
//      ...
//      [_c_str_base, _c_str_base + _c_str_max -1],
#define _extrs_max 10
#define _blobs_max 10
#define _all_max   20

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
#ifdef COMPILER2
  SET_ADDRESS(_extrs, OptoRuntime::handle_exception_C);
#endif
#ifndef ZERO
#if defined(AMD64) || defined(AARCH64) || defined(RISCV64)
  SET_ADDRESS(_extrs, MacroAssembler::debug64);
#endif
#endif // ZERO

  _extrs_complete = true;
  log_info(aot, codecache, init)("External addresses recorded");
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
  log_info(aot, codecache, init)("Early shared blobs recorded");
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

static const char* _C_strings[MAX_STR_COUNT] = {nullptr};
static int _C_strings_count = 0;
static int _C_strings_s[MAX_STR_COUNT] = {0};
static int _C_strings_id[MAX_STR_COUNT] = {0};
static int _C_strings_len[MAX_STR_COUNT] = {0};
static int _C_strings_hash[MAX_STR_COUNT] = {0};
static int _C_strings_used = 0;

void AOTCodeCache::load_strings() {
  uint strings_count  = _load_header->strings_count();
  if (strings_count == 0) {
    return;
  }
  uint strings_offset = _load_header->strings_offset();
  uint strings_size   = _load_header->entries_offset() - strings_offset;
  uint data_size = (uint)(strings_count * sizeof(uint));
  uint* sizes = (uint*)addr(strings_offset);
  uint* hashs = (uint*)addr(strings_offset + data_size);
  strings_size -= 2 * data_size;
  // We have to keep cached strings longer than _cache buffer
  // because they are refernced from compiled code which may
  // still be executed on VM exit after _cache is freed.
  char* p = NEW_C_HEAP_ARRAY(char, strings_size+1, mtCode);
  memcpy(p, addr(strings_offset + 2 * data_size), strings_size);
  _C_strings_buf = p;
  assert(strings_count <= MAX_STR_COUNT, "sanity");
  for (uint i = 0; i < strings_count; i++) {
    _C_strings[i] = p;
    uint len = sizes[i];
    _C_strings_s[i] = i;
    _C_strings_id[i] = i;
    _C_strings_len[i] = len;
    _C_strings_hash[i] = hashs[i];
    p += len;
  }
  assert((uint)(p - _C_strings_buf) <= strings_size, "(" INTPTR_FORMAT " - " INTPTR_FORMAT ") = %d > %d ", p2i(p), p2i(_C_strings_buf), (uint)(p - _C_strings_buf), strings_size);
  _C_strings_count = strings_count;
  _C_strings_used  = strings_count;
  log_info(aot, codecache, init)("Load %d C strings at offset %d from AOT Code Cache", _C_strings_count, strings_offset);
}

int AOTCodeCache::store_strings() {
  uint offset = _write_position;
  uint length = 0;
  if (_C_strings_used > 0) {
    // Write sizes first
    for (int i = 0; i < _C_strings_used; i++) {
      uint len = _C_strings_len[i] + 1; // Include 0
      length += len;
      assert(len < 1000, "big string: %s", _C_strings[i]);
      uint n = write_bytes(&len, sizeof(uint));
      if (n != sizeof(uint)) {
        return -1;
      }
    }
    // Write hashs
    for (int i = 0; i < _C_strings_used; i++) {
      uint n = write_bytes(&(_C_strings_hash[i]), sizeof(uint));
      if (n != sizeof(uint)) {
        return -1;
      }
    }
    for (int i = 0; i < _C_strings_used; i++) {
      uint len = _C_strings_len[i] + 1; // Include 0
      uint n = write_bytes(_C_strings[_C_strings_s[i]], len);
      if (n != len) {
        return -1;
      }
    }
    log_info(aot, codecache, exit)("Wrote %d C strings of total length %d at offset %d to AOT Code Cache",
                                   _C_strings_used, length, offset);
  }
  return _C_strings_used;
}

void AOTCodeCache::add_C_string(const char* str) {
  if (is_on_for_write()) {
    _cache->_table->add_C_string(str);
  }
}

void AOTCodeAddressTable::add_C_string(const char* str) {
  if (str != nullptr && _extrs_complete) {
    // Check previous strings address
    for (int i = 0; i < _C_strings_count; i++) {
      if (_C_strings[i] == str) {
        return; // Found existing one
      }
    }
    // Add new one
    if (_C_strings_count < MAX_STR_COUNT) {
      log_trace(aot, codecache)("add_C_string: [%d] " INTPTR_FORMAT " %s", _C_strings_count, p2i(str), str);
      _C_strings_id[_C_strings_count] = -1; // Init
      _C_strings[_C_strings_count++] = str;
    }
  }
}

int AOTCodeAddressTable::id_for_C_string(address str) {
  for (int i = 0; i < _C_strings_count; i++) {
    if (_C_strings[i] == (const char*)str) { // found
      int id = _C_strings_id[i];
      if (id >= 0) {
        assert(id < _C_strings_used, "%d >= %d", id , _C_strings_used);
        return id; // Found recorded
      }
      // Search for the same string content
      int len = (int)strlen((const char*)str);
      int hash = java_lang_String::hash_code((const jbyte*)str, len);
      for (int j = 0; j < _C_strings_used; j++) {
        if ((_C_strings_len[j] == len) && (_C_strings_hash[j] == hash)) {
          _C_strings_id[i] = j; // Found match
          return j;
        }
      }
      // Not found in recorded, add new
      id = _C_strings_used++;
      _C_strings_s[id] = i;
      _C_strings_id[i] = id;
      _C_strings_len[id] = len;
      _C_strings_hash[id] = hash;
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
    fatal("Incorrect id %d for SCA table", id);
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

int AOTCodeAddressTable::id_for_address(address addr, RelocIterator reloc, CodeBuffer* buffer) {
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
            log_info(aot, codecache)("Address " INTPTR_FORMAT " (offset %d) for runtime target '%s' is missing in AOT Code Cache addresses table",
                          p2i(addr), dist, (const char*)addr);
            assert(dist > (uint)(_all_max + MAX_STR_COUNT), "change encoding of distance");
            return dist;
          }
          fatal("Address " INTPTR_FORMAT " for runtime target '%s+%d' is missing in AOT Code Cache addresses table", p2i(addr), func_name, offset);
        } else {
          os::print_location(tty, p2i(addr), true);
          reloc.print_current_on(tty);
#ifndef PRODUCT
          buffer->print_on(tty);
          buffer->decode();
#endif // !PRODUCT
          fatal("Address " INTPTR_FORMAT " for <unknown> is missing in AOT Code Cache addresses table", p2i(addr));
        }
      } else {
        return _extrs_base + id;
      }
    }
  }
  return id;
}

void AOTCodeCache::print_on(outputStream* st) {
  AOTCodeCache* cache = open_for_read();
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

      st->print_cr("%4u: %4u: K%u I%u size=%u code_size=%u reloc_size=%u '%s'",
                   i, index, entry->kind(), entry->id(), entry->size(), entry->code_size(), entry->reloc_size(), saved_name);
      st->print_raw("         ");
    }
  } else {
    st->print_cr("failed to map code cache");
  }
}


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

#ifndef SHARE_CODE_AOTCODECACHE_HPP
#define SHARE_CODE_AOTCODECACHE_HPP

#include "runtime/stubInfo.hpp"

/*
 * AOT Code Cache collects code from Code Cache and corresponding metadata
 * during application training run.
 * In following "production" runs this code and data can be loaded into
 * Code Cache skipping its generation.
 */

class CodeBuffer;
class RelocIterator;
class AOTCodeCache;
class AdapterBlob;
class ExceptionBlob;
class ImmutableOopMapSet;
class AsmRemarks;
class DbgStrings;

enum class vmIntrinsicID : int;
enum CompLevel : signed char;

#define DO_AOTCODEENTRY_KIND(Fn) \
  Fn(None) \
  Fn(Adapter) \
  Fn(SharedBlob) \
  Fn(C1Blob) \
  Fn(C2Blob) \

// Descriptor of AOT Code Cache's entry
class AOTCodeEntry {
public:
  enum Kind : s1 {
#define DECL_KIND_ENUM(kind) kind,
    DO_AOTCODEENTRY_KIND(DECL_KIND_ENUM)
#undef DECL_KIND_ENUM
    Kind_count
  };

private:
  AOTCodeEntry* _next;
  Kind   _kind;
  uint   _id;          // Adapter's id, vmIntrinsic::ID for stub or name's hash for nmethod
  uint   _offset;      // Offset to entry
  uint   _size;        // Entry size
  uint   _name_offset; // Code blob name
  uint   _name_size;
  uint   _blob_offset; // Start of code in cache
  bool   _has_oop_maps;
  address _dumptime_content_start_addr; // CodeBlob::content_begin() at dump time; used for applying relocations

public:
  AOTCodeEntry(Kind kind,         uint id,
               uint offset,       uint size,
               uint name_offset,  uint name_size,
               uint blob_offset,  bool has_oop_maps,
               address dumptime_content_start_addr) {
    _next         = nullptr;
    _kind         = kind;
    _id           = id;
    _offset       = offset;
    _size         = size;
    _name_offset  = name_offset;
    _name_size    = name_size;
    _blob_offset  = blob_offset;
    _has_oop_maps = has_oop_maps;
    _dumptime_content_start_addr = dumptime_content_start_addr;
  }
  void* operator new(size_t x, AOTCodeCache* cache);
  // Delete is a NOP
  void operator delete( void *ptr ) {}

  AOTCodeEntry* next()        const { return _next; }
  void set_next(AOTCodeEntry* next) { _next = next; }

  Kind kind()         const { return _kind; }
  uint id()           const { return _id; }

  uint offset()       const { return _offset; }
  void set_offset(uint off) { _offset = off; }

  uint size()         const { return _size; }
  uint name_offset()  const { return _name_offset; }
  uint name_size()    const { return _name_size; }
  uint blob_offset()  const { return _blob_offset; }
  bool has_oop_maps() const { return _has_oop_maps; }
  address dumptime_content_start_addr() const { return _dumptime_content_start_addr; }

  static bool is_valid_entry_kind(Kind kind) { return kind > None && kind < Kind_count; }
  static bool is_blob(Kind kind) { return kind == SharedBlob || kind == C1Blob || kind == C2Blob; }
  static bool is_adapter(Kind kind) { return kind == Adapter; }
};

// Addresses of stubs, blobs and runtime finctions called from compiled code.
class AOTCodeAddressTable : public CHeapObj<mtCode> {
private:
  address* _extrs_addr;
  address* _stubs_addr;
  address* _shared_blobs_addr;
  address* _C1_blobs_addr;
  uint     _extrs_length;
  uint     _stubs_length;
  uint     _shared_blobs_length;
  uint     _C1_blobs_length;

  bool _extrs_complete;
  bool _early_stubs_complete;
  bool _shared_blobs_complete;
  bool _early_c1_complete;
  bool _complete;

public:
  AOTCodeAddressTable() :
    _extrs_addr(nullptr),
    _stubs_addr(nullptr),
    _shared_blobs_addr(nullptr),
    _C1_blobs_addr(nullptr),
    _extrs_length(0),
    _stubs_length(0),
    _shared_blobs_length(0),
    _C1_blobs_length(0),
    _extrs_complete(false),
    _early_stubs_complete(false),
    _shared_blobs_complete(false),
    _early_c1_complete(false),
    _complete(false)
  { }
  ~AOTCodeAddressTable();
  void init_extrs();
  void init_early_stubs();
  void init_shared_blobs();
  void init_early_c1();
  const char* add_C_string(const char* str);
  int  id_for_C_string(address str);
  address address_for_C_string(int idx);
  int  id_for_address(address addr, RelocIterator iter, CodeBlob* code_blob);
  address address_for_id(int id);
};

class AOTCodeCache : public CHeapObj<mtCode> {

// Classes used to describe AOT code cache.
protected:
  class Config {
    address _compressedOopBase;
    uint _compressedOopShift;
    uint _compressedKlassShift;
    uint _contendedPaddingWidth;
    uint _gc;
    enum Flags {
      none                     = 0,
      debugVM                  = 1,
      compressedOops           = 2,
      compressedClassPointers  = 4,
      useTLAB                  = 8,
      systemClassAssertions    = 16,
      userClassAssertions      = 32,
      enableContendedPadding   = 64,
      restrictContendedPadding = 128
    };
    uint _flags;
    uint _cpu_features_offset; // offset in the cache where cpu features are stored

  public:
    void record(uint cpu_features_offset);
    bool verify_cpu_features(AOTCodeCache* cache) const;
    bool verify(AOTCodeCache* cache) const;
  };

  class Header : public CHeapObj<mtCode> {
  private:
    enum {
      AOT_CODE_VERSION = 1
    };
    uint   _version;         // AOT code version (should match when reading code cache)
    uint   _cache_size;      // cache size in bytes
    uint   _strings_count;   // number of recorded C strings
    uint   _strings_offset;  // offset to recorded C strings
    uint   _entries_count;   // number of recorded entries
    uint   _entries_offset;  // offset of AOTCodeEntry array describing entries
    uint   _adapters_count;
    uint   _shared_blobs_count;
    uint   _C1_blobs_count;
    uint   _C2_blobs_count;
    Config _config; // must be the last element as there is trailing data stored immediately after Config

  public:
    void init(uint cache_size,
              uint strings_count,  uint strings_offset,
              uint entries_count,  uint entries_offset,
              uint adapters_count, uint shared_blobs_count,
              uint C1_blobs_count, uint C2_blobs_count,
              uint cpu_features_offset) {
      _version        = AOT_CODE_VERSION;
      _cache_size     = cache_size;
      _strings_count  = strings_count;
      _strings_offset = strings_offset;
      _entries_count  = entries_count;
      _entries_offset = entries_offset;
      _adapters_count = adapters_count;
      _shared_blobs_count = shared_blobs_count;
      _C1_blobs_count = C1_blobs_count;
      _C2_blobs_count = C2_blobs_count;
      _config.record(cpu_features_offset);
    }


    uint cache_size()     const { return _cache_size; }
    uint strings_count()  const { return _strings_count; }
    uint strings_offset() const { return _strings_offset; }
    uint entries_count()  const { return _entries_count; }
    uint entries_offset() const { return _entries_offset; }
    uint adapters_count() const { return _adapters_count; }
    uint shared_blobs_count()    const { return _shared_blobs_count; }
    uint C1_blobs_count() const { return _C1_blobs_count; }
    uint C2_blobs_count() const { return _C2_blobs_count; }

    bool verify(uint load_size)  const;
    bool verify_config(AOTCodeCache* cache) const { // Called after Universe initialized
      return _config.verify(cache);
    }
  };

// Continue with AOTCodeCache class definition.
private:
  Header* _load_header;
  char*   _load_buffer;    // Aligned buffer for loading cached code
  char*   _store_buffer;   // Aligned buffer for storing cached code
  char*   _C_store_buffer; // Original unaligned buffer

  uint   _write_position;  // Position in _store_buffer
  uint   _load_size;       // Used when reading cache
  uint   _store_size;      // Used when writing cache
  bool   _for_use;         // AOT cache is open for using AOT code
  bool   _for_dump;        // AOT cache is open for dumping AOT code
  bool   _closing;         // Closing cache file
  bool   _failed;          // Failed read/write to/from cache (cache is broken?)
  bool   _lookup_failed;   // Failed to lookup for info (skip only this code load)

  AOTCodeAddressTable* _table;

  AOTCodeEntry* _load_entries;   // Used when reading cache
  uint*         _search_entries; // sorted by ID table [id, index]
  AOTCodeEntry* _store_entries;  // Used when writing cache
  const char*   _C_strings_buf;  // Loaded buffer for _C_strings[] table
  uint          _store_entries_cnt;

  static AOTCodeCache* open_for_use();
  static AOTCodeCache* open_for_dump();

  bool set_write_position(uint pos);
  bool align_write();
  address reserve_bytes(uint nbytes);
  uint write_bytes(const void* buffer, uint nbytes);
  const char* addr(uint offset) const { return _load_buffer + offset; }
  static AOTCodeAddressTable* addr_table() {
    return is_on() && (cache()->_table != nullptr) ? cache()->_table : nullptr;
  }

  void set_lookup_failed()     { _lookup_failed = true; }
  void clear_lookup_failed()   { _lookup_failed = false; }
  bool lookup_failed()   const { return _lookup_failed; }

public:
  AOTCodeCache(bool is_dumping, bool is_using);
  ~AOTCodeCache();

  const char* cache_buffer() const { return _load_buffer; }
  bool failed() const { return _failed; }
  void set_failed()   { _failed = true; }

  static uint max_aot_code_size();

  uint load_size() const { return _load_size; }
  uint write_position() const { return _write_position; }

  void load_strings();
  int store_strings();

  static void init_early_stubs_table() NOT_CDS_RETURN;
  static void init_shared_blobs_table() NOT_CDS_RETURN;
  static void init_early_c1_table() NOT_CDS_RETURN;

  address address_for_C_string(int idx) const { return _table->address_for_C_string(idx); }
  address address_for_id(int id) const { return _table->address_for_id(id); }

  bool for_use()  const { return _for_use  && !_failed; }
  bool for_dump() const { return _for_dump && !_failed; }

  bool closing()          const { return _closing; }

  AOTCodeEntry* add_entry() {
    _store_entries_cnt++;
    _store_entries -= 1;
    return _store_entries;
  }

  AOTCodeEntry* find_entry(AOTCodeEntry::Kind kind, uint id);

  void store_cpu_features(char*& buffer, uint buffer_size);

  bool finish_write();

  bool write_relocations(CodeBlob& code_blob);
  bool write_oop_map_set(CodeBlob& cb);
#ifndef PRODUCT
  bool write_asm_remarks(CodeBlob& cb);
  bool write_dbg_strings(CodeBlob& cb);
#endif // PRODUCT

  // save and restore API for non-enumerable code blobs
  static bool store_code_blob(CodeBlob& blob,
                              AOTCodeEntry::Kind entry_kind,
                              uint id, const char* name) NOT_CDS_RETURN_(false);

  static CodeBlob* load_code_blob(AOTCodeEntry::Kind kind,
                                  uint id, const char* name) NOT_CDS_RETURN_(nullptr);

  // save and restore API for enumerable code blobs
  static bool store_code_blob(CodeBlob& blob,
                              AOTCodeEntry::Kind entry_kind,
                              BlobId id) NOT_CDS_RETURN_(false);

  static CodeBlob* load_code_blob(AOTCodeEntry::Kind kind,
                                  BlobId id) NOT_CDS_RETURN_(nullptr);

  static uint store_entries_cnt() {
    if (is_on_for_dump()) {
      return cache()->_store_entries_cnt;
    }
    return -1;
  }

// Static access

private:
  static AOTCodeCache* _cache;
  DEBUG_ONLY( static bool _passed_init2; )

  static bool open_cache(bool is_dumping, bool is_using);
  bool verify_config() {
    if (for_use()) {
      return _load_header->verify_config(this);
    }
    return true;
  }
public:
  static AOTCodeCache* cache() { assert(_passed_init2, "Too early to ask"); return _cache; }
  static void initialize() NOT_CDS_RETURN;
  static void init2() NOT_CDS_RETURN;
  static void close() NOT_CDS_RETURN;
  static bool is_on() CDS_ONLY({ return cache() != nullptr && !_cache->closing(); }) NOT_CDS_RETURN_(false);
  static bool is_on_for_use()  CDS_ONLY({ return is_on() && _cache->for_use(); }) NOT_CDS_RETURN_(false);
  static bool is_on_for_dump() CDS_ONLY({ return is_on() && _cache->for_dump(); }) NOT_CDS_RETURN_(false);
  static bool is_dumping_stub() NOT_CDS_RETURN_(false);
  static bool is_dumping_adapter() NOT_CDS_RETURN_(false);
  static bool is_using_stub() NOT_CDS_RETURN_(false);
  static bool is_using_adapter() NOT_CDS_RETURN_(false);
  static void enable_caching() NOT_CDS_RETURN;
  static void disable_caching() NOT_CDS_RETURN;
  static bool is_caching_enabled() NOT_CDS_RETURN_(false);

  static const char* add_C_string(const char* str) NOT_CDS_RETURN_(str);

  static void print_on(outputStream* st) NOT_CDS_RETURN;
};

// Concurent AOT code reader
class AOTCodeReader {
private:
  const AOTCodeCache*  _cache;
  const AOTCodeEntry*  _entry;
  const char*          _load_buffer; // Loaded cached code buffer
  uint  _read_position;              // Position in _load_buffer
  uint  read_position() const { return _read_position; }
  void  set_read_position(uint pos);
  const char* addr(uint offset) const { return _load_buffer + offset; }

  bool _lookup_failed;       // Failed to lookup for info (skip only this code load)
  void set_lookup_failed()     { _lookup_failed = true; }
  void clear_lookup_failed()   { _lookup_failed = false; }
  bool lookup_failed()   const { return _lookup_failed; }

  AOTCodeEntry* aot_code_entry() { return (AOTCodeEntry*)_entry; }
public:
  AOTCodeReader(AOTCodeCache* cache, AOTCodeEntry* entry);

  CodeBlob* compile_code_blob(const char* name);

  ImmutableOopMapSet* read_oop_map_set();

  void fix_relocations(CodeBlob* code_blob);
#ifndef PRODUCT
  void read_asm_remarks(AsmRemarks& asm_remarks);
  void read_dbg_strings(DbgStrings& dbg_strings);
#endif // PRODUCT
};

#endif // SHARE_CODE_AOTCODECACHE_HPP

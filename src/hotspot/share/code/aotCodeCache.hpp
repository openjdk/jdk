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

#ifndef SHARE_CODE_AOTCODECACHE_HPP
#define SHARE_CODE_AOTCODECACHE_HPP

/*
 * AOT Code Cache collects code from Code Cache and corresponding metadata
 * during application training run.
 * In following "production" runs this code and data can me loaded into
 * Code Cache skipping its generation.
 */

class CodeBuffer;
class RelocIterator;
class AOTCodeCache;

enum class vmIntrinsicID : int;
enum CompLevel : signed char;

// Descriptor of AOT Code Cache's entry
class AOTCodeEntry {
public:
  enum Kind {
    None    = 0,
    Adapter = 1,
    Blob    = 2
  };

private:
  AOTCodeEntry* _next;
  Kind   _kind;        //
  uint   _id;          // vmIntrinsic::ID for stub or name's hash for nmethod

  uint   _offset;      // Offset to entry
  uint   _size;        // Entry size
  uint   _name_offset; // Code blob name
  uint   _name_size;
  uint   _code_offset; // Start of code in cache
  uint   _code_size;   // Total size of all code sections
  uint   _reloc_offset;// Relocations
  uint   _reloc_size;  // Max size of relocations per code section

public:
  AOTCodeEntry(Kind kind,         uint id,
               uint offset,       uint size,
               uint name_offset,  uint name_size,
               uint code_offset,  uint code_size,
               uint reloc_offset, uint reloc_size) {
    _next         = nullptr;
    _kind         = kind;
    _id           = id;

    _offset       = offset;
    _size         = size;
    _name_offset  = name_offset;
    _name_size    = name_size;
    _code_offset  = code_offset;
    _code_size    = code_size;
    _reloc_offset = reloc_offset;
    _reloc_size   = reloc_size;
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
  uint code_offset()  const { return _code_offset; }
  uint code_size()    const { return _code_size; }
  uint reloc_offset() const { return _reloc_offset; }
  uint reloc_size()   const { return _reloc_size; }
};

// Addresses of stubs, blobs and runtime finctions called from compiled code.
class AOTCodeAddressTable : public CHeapObj<mtCode> {
private:
  address* _extrs_addr;
  address* _blobs_addr;
  uint     _extrs_length;
  uint     _blobs_length;

  bool _extrs_complete;
  bool _shared_blobs_complete;
  bool _complete;

public:
  AOTCodeAddressTable() :
    _extrs_addr(nullptr),
    _blobs_addr(nullptr),
    _extrs_length(0),
    _blobs_length(0),
    _extrs_complete(false),
    _shared_blobs_complete(false),
    _complete(false)
  { }
  ~AOTCodeAddressTable();
  void init_extrs();
  void init_shared_blobs();
  void add_C_string(const char* str);
  int  id_for_C_string(address str);
  address address_for_C_string(int idx);
  int  id_for_address(address addr, RelocIterator iter, CodeBuffer* buffer);
  address address_for_id(int id);
};

struct AOTCodeSection {
public:
  address _origin_address;
  uint    _size;
  uint    _offset;
};

class AOTCodeCache : public CHeapObj<mtCode> {

// Classes used to describe AOT code cache.
protected:
  class Config {
    uint _compressedOopShift;
    uint _compressedKlassShift;
    uint _contendedPaddingWidth;
    uint _objectAlignment;
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

  public:
    void record();
    bool verify() const;
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
    Config _config;

public:
    void init(uint cache_size,
              uint strings_count, uint strings_offset,
              uint entries_count, uint entries_offset) {
      _version        = AOT_CODE_VERSION;
      _cache_size     = cache_size;
      _strings_count  = strings_count;
      _strings_offset = strings_offset;
      _entries_count  = entries_count;
      _entries_offset = entries_offset;

      _config.record();
    }


    uint cache_size()     const { return _cache_size; }
    uint strings_count()  const { return _strings_count; }
    uint strings_offset() const { return _strings_offset; }
    uint entries_count()  const { return _entries_count; }
    uint entries_offset() const { return _entries_offset; }

    bool verify_config(uint load_size)  const;
    bool verify_vm_config() const { // Called after Universe initialized
      return _config.verify();
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
  bool   _for_read;        // Open for read
  bool   _for_write;       // Open for write
  bool   _closing;         // Closing cache file
  bool   _failed;          // Failed read/write to/from cache (cache is broken?)
  bool   _lookup_failed;   // Failed to lookup for info (skip only this code load)

  AOTCodeAddressTable* _table;

  AOTCodeEntry* _load_entries;   // Used when reading cache
  uint*         _search_entries; // sorted by ID table [id, index]
  AOTCodeEntry* _store_entries;  // Used when writing cache
  const char*   _C_strings_buf;  // Loaded buffer for _C_strings[] table
  uint          _store_entries_cnt;

  static AOTCodeCache* open_for_read();
  static AOTCodeCache* open_for_write();

  bool set_write_position(uint pos);
  bool align_write();
  uint write_bytes(const void* buffer, uint nbytes);
  const char* addr(uint offset) const { return _load_buffer + offset; }
  static AOTCodeAddressTable* addr_table() {
    return is_on() && (cache()->_table != nullptr) ? cache()->_table : nullptr;
  }

  void set_lookup_failed()     { _lookup_failed = true; }
  void clear_lookup_failed()   { _lookup_failed = false; }
  bool lookup_failed()   const { return _lookup_failed; }

public:
  AOTCodeCache();
  ~AOTCodeCache();

  const char* cache_buffer() const { return _load_buffer; }
  bool failed() const { return _failed; }
  void set_failed()   { _failed = true; }

  static uint max_aot_code_size();

  uint load_size() const { return _load_size; }
  uint write_position() const { return _write_position; }

  void load_strings();
  int store_strings();

  static void init_extrs_table() NOT_CDS_RETURN;
  static void init_shared_blobs_table() NOT_CDS_RETURN;

  address address_for_id(int id) const { return _table->address_for_id(id); }

  bool for_read()  const { return _for_read  && !_failed; }
  bool for_write() const { return _for_write && !_failed; }

  bool closing()          const { return _closing; }

  AOTCodeEntry* add_entry() {
    _store_entries_cnt++;
    _store_entries -= 1;
    return _store_entries;
  }

  AOTCodeEntry* find_entry(AOTCodeEntry::Kind kind, uint id);

  bool finish_write();

  bool write_code(CodeBuffer* buffer, uint& code_size);
  bool write_relocations(CodeBuffer* buffer, uint& reloc_size);

  static bool load_exception_blob(CodeBuffer* buffer, int* pc_offset) NOT_CDS_RETURN_(false);
  static bool store_exception_blob(CodeBuffer* buffer, int pc_offset) NOT_CDS_RETURN_(false);

  static bool load_adapter(CodeBuffer* buffer, uint32_t id, const char* basic_sig, uint32_t *entry_offset) NOT_CDS_RETURN_(false);
  static bool store_adapter(CodeBuffer* buffer, uint32_t id, const char* basic_sig, uint32_t *entry_offset) NOT_CDS_RETURN_(false);

  static uint store_entries_cnt() {
    if (is_on_for_write()) {
      return cache()->_store_entries_cnt;
    }
    return -1;
  }

// Static access

private:
  static AOTCodeCache*  _cache;

  static bool open_cache();
  static bool verify_vm_config() {
    if (is_on_for_read()) {
      return _cache->_load_header->verify_vm_config();
    }
    return true;
  }
public:
  static AOTCodeCache* cache() { return _cache; }
  static void initialize() NOT_CDS_RETURN;
  static void init2() NOT_CDS_RETURN;
  static void close() NOT_CDS_RETURN;
  static bool is_on() CDS_ONLY({ return _cache != nullptr && !_cache->closing(); }) NOT_CDS_RETURN_(false);
  static bool is_on_for_read()  { return is_on() && _cache->for_read(); }
  static bool is_on_for_write() { return is_on() && _cache->for_write(); }

  static void add_C_string(const char* str) NOT_CDS_RETURN;

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

public:
  AOTCodeReader(AOTCodeCache* cache, AOTCodeEntry* entry);

  bool compile_blob(CodeBuffer* buffer, int* pc_offset);
  bool compile_adapter(CodeBuffer* buffer, const char* name, uint32_t offsets[4]);

  bool read_code(CodeBuffer* buffer, CodeBuffer* orig_buffer, uint code_offset);
  bool read_relocations(CodeBuffer* buffer, CodeBuffer* orig_buffer);
};
#endif // SHARE_CODE_AOTCODECACHE_HPP

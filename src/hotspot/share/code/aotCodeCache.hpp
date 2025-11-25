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
class AOTCodeReader;
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
  Fn(StubGenBlob) \

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
  static bool is_blob(Kind kind) { return kind == SharedBlob || kind == C1Blob || kind == C2Blob || kind == StubGenBlob; }
  static bool is_single_stub_blob(Kind kind) { return kind == SharedBlob || kind == C1Blob || kind == C2Blob || kind == StubGenBlob; }
  static bool is_multi_stub_blob(Kind kind) { return kind == StubGenBlob; }
  static bool is_adapter(Kind kind) { return kind == Adapter; }
};

// Addresses of stubs, blobs and runtime finctions called from compiled code.
class AOTCodeAddressTable : public CHeapObj<mtCode> {
private:
  address* _extrs_addr;
  address* _stubs_addr;
  uint     _extrs_length;

  bool _extrs_complete;
  bool _shared_stubs_complete;
  bool _c1_stubs_complete;
  bool _c2_stubs_complete;
  bool _stubgen_stubs_complete;
  bool _complete;

public:
  AOTCodeAddressTable() :
    _extrs_addr(nullptr),
    _stubs_addr(nullptr),
    _extrs_length(0),
    _extrs_complete(false),
    _shared_stubs_complete(false),
    _c1_stubs_complete(false),
    _c2_stubs_complete(false),
    _stubgen_stubs_complete(false),
    _complete(false)
  { }
  ~AOTCodeAddressTable();
  void init_extrs();
  void init_extrs2();
  void add_stub_entry(EntryId entry_id, address entry);
  void add_external_addresses(GrowableArray<address>& addresses) NOT_CDS_RETURN;
  void set_shared_stubs_complete();
  void set_c1_stubs_complete();
  void set_c2_stubs_complete();
  void set_stubgen_stubs_complete();
  const char* add_C_string(const char* str);
  int  id_for_C_string(address str);
  address address_for_C_string(int idx);
  int  id_for_address(address addr, RelocIterator iter, CodeBlob* code_blob);
  address address_for_id(int id);
};

// Auxiliary class used by AOTStubData to locate addresses owned by a
// stub in the _address_array.

class StubAddrRange {
private:
  // Index of the first address owned by a stub or -1 if none present
  int _start_index;
  // Total number of addresses owned by a stub, including in order:
  // start address for stub code and first entry, (exclusive) end
  // address for stub code, all secondary entry addresses, any
  // auxiliary addresses
  uint _naddr;
 public:
  StubAddrRange() : _start_index(-1), _naddr(0) {}
  int start_index() { return _start_index; }
  int count() { return _naddr; }

  void default_init() {
    _start_index = -1;
    _naddr = 0;
  }

  void init_entry(int start_index, int naddr) {
    _start_index = start_index;
    _naddr = naddr;
  }
};

// class used to save and restore details of stubs embedded in a
// multi-stub (StubGen) blob

class AOTStubData : public StackObj {
  friend class AOTCodeCache;
  friend class AOTCodeReader;
private:
  BlobId _blob_id; // must be a stubgen blob id
  // whatever buffer blob was successfully loaded from the AOT cache
  // following a call to load_code_blob or nullptr
  CodeBlob *_cached_blob;
  // Array of addresses owned by stubs. Each stub appends addresses to
  // this array as a block, whether at the end of generation or at the
  // end of restoration from the cache. The first two addresses in
  // each block are the "start" and "end2 address of the stub. Any
  // other visible addresses located within the range [start,end)
  // follow, either extra entries, data addresses or SEGV-protected
  // subrange start, end and handler addresses. In the special case
  // that the SEGV handler address is the (external) common address
  // handler the array will hold value nullptr.
  GrowableArray<address> _address_array;
  // count of how many stubs exist in the current blob (not all of
  // which may actually be generated)
  int _stub_cnt;
  // array identifying range of entries in _address_array for each stub
  // indexed by offset of stub in blob
  StubAddrRange* _ranges;
  // id of last looked up stub or NO_STUBID if lookup not attempted or failed
  StubId _current;
  // offset of _current in blob or -1 if lookup not attempted or failed
  int _current_idx;

  // flags indicating whether the AOT code cache is open and, if so,
  // whether we are loading or storing stubs.the first of those
  // cases whether we have encountered any invalid stubs or failed to
  // find a stub that was being generated
  enum Flags {
    OPEN    = 1 << 0,            // cache is open for use
    USING   = 1 << 1,            // open and loading stubs
    DUMPING = 1 << 2,            // open and storing stubs
    INVALID = 1 << 3,            // found invalid stub when loading
  };

  uint32_t _flags;

  void set_invalid() { _flags |= INVALID; }

  StubAddrRange& get_range(int idx) const { return _ranges[idx]; }
  GrowableArray<address>& address_array() { return _address_array; }
  // accessor for entry/auxiliary addresses defaults to start entry
public:
  AOTStubData(BlobId blob_id) NOT_CDS({});

  ~AOTStubData()    CDS_ONLY({FREE_C_HEAP_ARRAY(StubAddrRange, _ranges);}) NOT_CDS({})

    bool is_open()  CDS_ONLY({ return (_flags & OPEN) != 0; }) NOT_CDS_RETURN_(false);
  bool is_using()   CDS_ONLY({ return (_flags & USING) != 0; }) NOT_CDS_RETURN_(false);
  bool is_dumping() CDS_ONLY({ return (_flags & DUMPING) != 0; }) NOT_CDS_RETURN_(false);
  bool is_aot()     CDS_ONLY({ return is_using() || is_dumping(); }) NOT_CDS_RETURN_(false);
  bool is_invalid() CDS_ONLY({ return (_flags & INVALID) != 0; }) NOT_CDS_RETURN_(false);

  BlobId blob_id() { return _blob_id; }
  StubId current_stub_id() { return _current; }
  //
  bool load_code_blob() NOT_CDS_RETURN_(true);
  bool store_code_blob(CodeBlob& new_blob, CodeBuffer *code_buffer) NOT_CDS_RETURN_(true);
  // determine whether a stub is available in the AOT cache
  bool find_archive_data(StubId stub_id) NOT_CDS_RETURN_(false);
  // retrieve stub entry data if it we are using archived stubs and
  // the stub has been found in an AOT-restored blob or store stub
  // entry data if we are saving archived stubs and the stub has just
  // been successfully generated into the current blob.
  //
  // start and end identify the inclusive start and exclusive end
  // address for stub code and must lie in the current blob's code
  // range. Stubs presented via this interface must declare at least
  // one entry and start is always taken to be the first entry.
  //
  // Optional arrays entries and extras present other addresses of
  // interest all of which must either lie in the interval (start,
  // end) or be nullptr (verified by load and store methods).
  //
  // entries lists secondary entries for the stub each of which must
  // match a corresponding entry declaration for the stub (entry count
  // verified by load and store methods). Null entry addresses are
  // allowed when an architecture does not require a specific entry
  // but may not vary from one run to the next. If the cache is in use
  // at a store (for loading or saving code) then non-null entry
  // addresses are entered into the AOT cache stub address table
  // allowing references to them from other stubs or nmethods to be
  // relocated.
  //
  // extras lists other non-entry stub addresses of interest such as
  // memory protection ranges and associated handler addresses. These
  // do do not need to be declared as entries and their number and
  // meaning may vary according to the architecture.
  void load_archive_data(StubId stub_id, address& start, address& end, GrowableArray<address>* entries = nullptr, GrowableArray<address>* extras = nullptr) NOT_CDS_RETURN;
  void store_archive_data(StubId stub_id, address start, address end, GrowableArray<address>* entries = nullptr, GrowableArray<address>* extras = nullptr) NOT_CDS_RETURN;

  const AOTStubData* as_const() { return (const AOTStubData*)this; }
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
    uint   _adapters_count;
    uint   _shared_blobs_count;
    uint   _stubgen_blobs_count;
    uint   _C1_blobs_count;
    uint   _C2_blobs_count;
    Config _config;

  public:
    void init(uint cache_size,
              uint strings_count,       uint strings_offset,
              uint entries_count,       uint entries_offset,
              uint adapters_count,      uint shared_blobs_count,
              uint stubgen_blobs_count, uint C1_blobs_count,
              uint C2_blobs_count) {
      _version        = AOT_CODE_VERSION;
      _cache_size     = cache_size;
      _strings_count  = strings_count;
      _strings_offset = strings_offset;
      _entries_count  = entries_count;
      _entries_offset = entries_offset;
      _adapters_count = adapters_count;
      _shared_blobs_count = shared_blobs_count;
      _stubgen_blobs_count = stubgen_blobs_count;
      _C1_blobs_count = C1_blobs_count;
      _C2_blobs_count = C2_blobs_count;
      _config.record();
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
    bool verify_config() const { // Called after Universe initialized
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

  void add_stub_entry(EntryId entry_id, address entry) NOT_CDS_RETURN;
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

  static void set_shared_stubs_complete() NOT_CDS_RETURN;
  static void set_c1_stubs_complete() NOT_CDS_RETURN ;
  static void set_c2_stubs_complete() NOT_CDS_RETURN;
  static void set_stubgen_stubs_complete() NOT_CDS_RETURN;

  void add_stub_entries(StubId stub_id, address start, GrowableArray<address> *entries = nullptr, int offset = -1) NOT_CDS_RETURN;

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

  bool finish_write();

  bool write_relocations(CodeBlob& code_blob, RelocIterator& iter);
  bool write_oop_map_set(CodeBlob& cb);
  bool write_stub_data(CodeBlob& blob, AOTStubData *stub_data);
#ifndef PRODUCT
  bool write_asm_remarks(CodeBlob& cb);
  bool write_dbg_strings(CodeBlob& cb);
#endif // PRODUCT

private:
  // internal private API to save and restore blobs
  static bool store_code_blob(CodeBlob& blob,
                              AOTCodeEntry::Kind entry_kind,
                              uint id,
                              const char* name,
                              AOTStubData* stub_data,
                              CodeBuffer* code_buffer) NOT_CDS_RETURN_(false);

  static CodeBlob* load_code_blob(AOTCodeEntry::Kind kind,
                                  uint id,
                                  const char* name,
                                  AOTStubData* stub_data) NOT_CDS_RETURN_(nullptr);

public:
  // save and restore API for non-enumerable code blobs
  static bool store_code_blob(CodeBlob& blob,
                              AOTCodeEntry::Kind entry_kind,
                              uint id,
                              const char* name) NOT_CDS_RETURN_(false);

  static CodeBlob* load_code_blob(AOTCodeEntry::Kind kind,
                                  uint id, const char* name) NOT_CDS_RETURN_(nullptr);

  // save and restore API for enumerable code blobs

  // API for single-stub blobs
  static bool store_code_blob(CodeBlob& blob,
                              AOTCodeEntry::Kind entry_kind,
                              BlobId id) NOT_CDS_RETURN_(false);

  static CodeBlob* load_code_blob(AOTCodeEntry::Kind kind,
                                  BlobId id) NOT_CDS_RETURN_(nullptr);

  // API for multi-stub blobs -- for use by class StubGenerator.

  static bool store_code_blob(CodeBlob& blob,
                              AOTCodeEntry::Kind kind,
                              BlobId id,
                              AOTStubData* stub_data,
                              CodeBuffer *code_buffer) NOT_CDS_RETURN_(false);

  static CodeBlob* load_code_blob(AOTCodeEntry::Kind kind,
                                  BlobId id,
                                  AOTStubData* stub_data) NOT_CDS_RETURN_(nullptr);

  static void publish_external_addresses(GrowableArray<address>& addresses) NOT_CDS_RETURN;
  // publish all entries for a code blob in code cache address table
  static void publish_stub_addresses(CodeBlob &code_blob, BlobId id, AOTStubData *stub_data) NOT_CDS_RETURN;

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
      return _load_header->verify_config();
    }
    return true;
  }
public:
  // marker used where an address offset needs to be stored for later
  // retrieval and the address turns out to be null
  static const uint NULL_ADDRESS_MARKER = UINT_MAX;

  static AOTCodeCache* cache() { assert(_passed_init2, "Too early to ask"); return _cache; }
  static void initialize() NOT_CDS_RETURN;
  static void init2() NOT_CDS_RETURN;
  static void init3() NOT_CDS_RETURN;
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
  AOTCodeCache*  _cache;
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

  CodeBlob* compile_code_blob(const char* name, AOTCodeEntry::Kind entry_kind, int id, AOTStubData* stub_data = nullptr);

  ImmutableOopMapSet* read_oop_map_set();
  void read_stub_data(CodeBlob* code_blob, AOTStubData *stub_data);
  void publish_stub_addresses(CodeBlob &code_blob, BlobId id, AOTStubData *stub_data);


  void fix_relocations(CodeBlob* code_blob, RelocIterator& iter);
#ifndef PRODUCT
  void read_asm_remarks(AsmRemarks& asm_remarks);
  void read_dbg_strings(DbgStrings& dbg_strings);
#endif // PRODUCT
};

#endif // SHARE_CODE_AOTCODECACHE_HPP

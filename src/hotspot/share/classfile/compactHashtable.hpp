/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_COMPACTHASHTABLE_HPP
#define SHARE_CLASSFILE_COMPACTHASHTABLE_HPP

#include "cds/aotCompressedPointers.hpp"
#include "cds/cds_globals.hpp"
#include "oops/array.hpp"
#include "oops/symbol.hpp"
#include "runtime/globals.hpp"
#include "utilities/growableArray.hpp"


template <
  typename K,
  typename V,
  V (*DECODE)(address base_address, u4 encoded_value),
  bool (*EQUALS)(V value, K key, int len)
  >
class CompactHashtable;
class NumberSeq;
class SimpleCompactHashtable;
class SerializeClosure;

// Stats for symbol tables in the CDS archive
class CompactHashtableStats {
public:
  int hashentry_count;
  int hashentry_bytes;
  int bucket_count;
  int bucket_bytes;

  CompactHashtableStats() :
    hashentry_count(0), hashentry_bytes(0),
    bucket_count(0), bucket_bytes(0) {}
};

#if INCLUDE_CDS
/////////////////////////////////////////////////////////////////////////
//
// The compact hash table writer. Used at dump time for writing out
// the compact table to the shared archive.
//
// At dump time, the CompactHashtableWriter obtains all entries from
// a table (the table could be in any form of a collection of <hash, encoded_value> pair)
// and adds them to a new temporary hash table (_buckets). The hash
// table size (number of buckets) is calculated using
// '(num_entries + bucket_size - 1) / bucket_size'. The default bucket
// size is 4 and can be changed by -XX:SharedSymbolTableBucketSize option.
// 4 is chosen because it produces smaller sized bucket on average for
// faster lookup. It also has relatively small number of empty buckets and
// good distribution of the entries.
//
// We use a simple hash function (hash % num_bucket) for the table.
// The new table is compacted when written out. Please see comments
// above the CompactHashtable class for the table layout detail. The bucket
// offsets are written to the archive as part of the compact table. The
// bucket offset is encoded in the low 30-bit (0-29) and the bucket type
// (regular or value_only) are encoded in bit[31, 30]. For buckets with more
// than one entry, both hash and encoded_value are written to the
// table. For buckets with only one entry, only the encoded_value is written
// to the table and the buckets are tagged as value_only in their type bits.
// Buckets without entry are skipped from the table. Their offsets are
// still written out for faster lookup.
//
class CompactHashtableWriter: public StackObj {
public:
  class Entry {
    unsigned int _hash;
    u4 _encoded_value;

  public:
    Entry() {}
    Entry(unsigned int hash, u4 encoded_value) : _hash(hash), _encoded_value(encoded_value) {}

    u4 encoded_value() {
      return _encoded_value;
    }
    unsigned int hash() {
      return _hash;
    }

    bool operator==(const CompactHashtableWriter::Entry& other) {
      return (_encoded_value == other._encoded_value && _hash == other._hash);
    }
  }; // class CompactHashtableWriter::Entry

private:
  int _num_entries_written;
  int _num_buckets;
  int _num_empty_buckets;
  int _num_value_only_buckets;
  int _num_other_buckets;
  GrowableArray<Entry>** _buckets;
  CompactHashtableStats* _stats;
  Array<u4>* _compact_buckets;
  Array<u4>* _compact_entries;

public:
  // This is called at dump-time only
  CompactHashtableWriter(int num_entries, CompactHashtableStats* stats);
  ~CompactHashtableWriter();

  void add(unsigned int hash, u4 encoded_value);
  void add(unsigned int hash, AOTCompressedPointers::narrowPtr encoded_value) {
    add(hash, cast_to_u4(encoded_value));
  }
  void dump(SimpleCompactHashtable *cht, const char* table_name);

private:
  void allocate_table();
  void dump_table(NumberSeq* summary);
  static int calculate_num_buckets(int num_entries) {
    int num_buckets = num_entries / SharedSymbolTableBucketSize;
    // calculation of num_buckets can result in zero buckets, we need at least one
    return (num_buckets < 1) ? 1 : num_buckets;
  }
};
#endif // INCLUDE_CDS

#define REGULAR_BUCKET_TYPE       0
#define VALUE_ONLY_BUCKET_TYPE    1
#define TABLEEND_BUCKET_TYPE      3
#define BUCKET_OFFSET_MASK        0x3FFFFFFF
#define BUCKET_OFFSET(info)       ((info) & BUCKET_OFFSET_MASK)
#define BUCKET_TYPE_SHIFT         30
#define BUCKET_TYPE(info)         (((info) & ~BUCKET_OFFSET_MASK) >> BUCKET_TYPE_SHIFT)
#define BUCKET_INFO(offset, type) (((type) << BUCKET_TYPE_SHIFT) | ((offset) & BUCKET_OFFSET_MASK))

/////////////////////////////////////////////////////////////////////////////
//
// CompactHashtable is used to store the CDS archive's tables.
// A table could be in any form of a collection of <hash, encoded_value> pair.
//
// Because these tables are read-only (no entries can be added/deleted) at run-time
// and tend to have large number of entries, we try to minimize the footprint
// cost per entry.
//
// The CompactHashtable is split into two arrays
//
//   u4 buckets[num_buckets+1]; // bit[31,30]: type; bit[29-0]: offset
//   u4 entries[<variable size>]
//
// The size of buckets[] is 'num_buckets + 1'. Each entry of
// buckets[] is a 32-bit encoding of the bucket type and bucket offset,
// with the type in the left-most 2-bit and offset in the remaining 30-bit.
//
// There are three types of buckets: regular, value_only, and table_end.
//  . The regular buckets have '00' in their highest 2-bit.
//  . The value_only buckets have '01' in their highest 2-bit.
//  . There is only a single table_end bucket that marks the end of buckets[].
//    It has '11' in its highest 2-bit.
//
// For regular buckets, each entry is 8 bytes in the entries[]:
//   u4 hash;          // entry hash
//   u4 encoded_value; // A 32-bit encoding of the template type V. The template parameter DECODE
//                     // converts this to type V. Many CompactHashtables encode a pointer as a 32-bit offset, where
//                     //   V entry = (V)(base_address + offset)
//                     // see StringTable, SymbolTable and AdapterHandlerLibrary for examples
//
// For value_only buckets, each entry has only the 4-byte 'encoded_value' in the entries[].
//
// The single table_end bucket has no corresponding entry.
//
// The number of entries in bucket <i> can be calculated like this:
//      my_offset   = _buckets[i]   & 0x3fffffff; // mask off top 2-bit
//      next_offset = _buckets[i+1] & 0x3fffffff
//  For REGULAR_BUCKET_TYPE
//      num_entries = (next_offset - my_offset) / 8;
//  For VALUE_ONLY_BUCKET_TYPE
//      num_entries = (next_offset - my_offset) / 4;
//
// If bucket <i> is empty, we have my_offset == next_offset. Empty buckets are
// always encoded as regular buckets.
//
// In the following example:
//   - Bucket #0 is a REGULAR_BUCKET_TYPE with two entries
//   - Bucket #1 is a VALUE_ONLY_BUCKET_TYPE with one entry.
//   - Bucket #2 is a REGULAR_BUCKET_TYPE with zero entries.
//
// buckets[0, 4, 5(empty), 5, ...., N(table_end)]
//         |  |  |         |        |
//         |  |  +---+-----+        |
//         |  |      |              |
//         |  +----+ +              |
//         v       v v              v
// entries[H,O,H,O,O,H,O,H,O........]
//
// See CompactHashtable::lookup() for how the table is searched at runtime.
// See CompactHashtableWriter::dump() for how the table is written at CDS
// dump time.
//
class SimpleCompactHashtable {
protected:
  address  _base_address;
  u4  _bucket_count;
  u4  _entry_count;
  u4* _buckets;
  u4* _entries;

public:
  SimpleCompactHashtable() :
    _base_address(nullptr),
    _bucket_count(0),
    _entry_count(0),
    _buckets(nullptr),
    _entries(nullptr)
  {}

  void reset() {
    _base_address = nullptr;
    _bucket_count = 0;
    _entry_count = 0;
    _buckets = nullptr;
    _entries = nullptr;
  }

  void init(address base_address, u4 entry_count, u4 bucket_count, u4* buckets, u4* entries);

  // Read/Write the table's header from/to the CDS archive
  void serialize_header(SerializeClosure* soc) NOT_CDS_RETURN;

  inline bool empty() const {
    return (_entry_count == 0);
  }

  inline size_t entry_count() const {
    return _entry_count;
  }
};

template <
  typename K,
  typename V,
  V (*DECODE)(address base_address, u4 encoded_value),
  bool (*EQUALS)(V value, K key, int len)
  >
class CompactHashtable : public SimpleCompactHashtable {

  V decode(u4 encoded_value) const {
    return DECODE(_base_address, encoded_value);
  }

public:
  // Lookup a value V from the compact table using key K
  inline V lookup(K key, unsigned int hash, int len) const {
    if (_entry_count > 0) {
      int index = hash % _bucket_count;
      u4 bucket_info = _buckets[index];
      u4 bucket_offset = BUCKET_OFFSET(bucket_info);
      int bucket_type = BUCKET_TYPE(bucket_info);
      u4* entry = _entries + bucket_offset;

      if (bucket_type == VALUE_ONLY_BUCKET_TYPE) {
        V value = decode(entry[0]);
        if (EQUALS(value, key, len)) {
          return value;
        }
      } else {
        // This is a regular bucket, which has more than one
        // entries. Each entry is a (hash, value) pair.
        // Seek until the end of the bucket.
        u4* entry_max = _entries + BUCKET_OFFSET(_buckets[index + 1]);
        while (entry < entry_max) {
          unsigned int h = (unsigned int)(entry[0]);
          if (h == hash) {
            V value = decode(entry[1]);
            if (EQUALS(value, key, len)) {
              return value;
            }
          }
          entry += 2;
        }
      }
    }
    return nullptr;
  }

  // Iterate through the values in the table, stopping when do_value() returns false.
  template <class ITER>
  inline void iterate(ITER* iter) const { iterate([&](V v) { iter->do_value(v); }); }

  template<typename Function>
  inline void iterate(const Function& function) const { // lambda enabled API
    iterate(const_cast<Function&>(function));
  }

  // Iterate through the values in the table, stopping when the lambda returns false.
  template<typename Function>
  inline void iterate(Function& function) const { // lambda enabled API
    for (u4 i = 0; i < _bucket_count; i++) {
      u4 bucket_info = _buckets[i];
      u4 bucket_offset = BUCKET_OFFSET(bucket_info);
      int bucket_type = BUCKET_TYPE(bucket_info);
      u4* entry = _entries + bucket_offset;

      if (bucket_type == VALUE_ONLY_BUCKET_TYPE) {
        if (!function(decode(entry[0]))) {
          return;
        }
      } else {
        u4* entry_max = _entries + BUCKET_OFFSET(_buckets[i + 1]);
        while (entry < entry_max) {
          if (!function(decode(entry[1]))) {
            return;
          }
          entry += 2;
        }
      }
    }
  }

  // Unconditionally iterate through all the values in the table
  template <class ITER>
  inline void iterate_all(ITER* iter) const { iterate_all([&](V v) { iter->do_value(v); }); }

  // Unconditionally iterate through all the values in the table using lambda
  template<typename Function>
  void iterate_all(Function function) const { // lambda enabled API
    auto wrapper = [&] (V v) {
      function(v);
      return true;
    };
    iterate(wrapper);
  }

  void print_table_statistics(outputStream* st, const char* name) {
    st->print_cr("%s statistics:", name);
    int total_entries = 0;
    int max_bucket = 0;
    for (u4 i = 0; i < _bucket_count; i++) {
      u4 bucket_info = _buckets[i];
      int bucket_type = BUCKET_TYPE(bucket_info);
      int bucket_size;

      if (bucket_type == VALUE_ONLY_BUCKET_TYPE) {
        bucket_size = 1;
      } else {
        bucket_size = (BUCKET_OFFSET(_buckets[i + 1]) - BUCKET_OFFSET(bucket_info)) / 2;
      }
      total_entries += bucket_size;
      if (max_bucket < bucket_size) {
        max_bucket = bucket_size;
      }
    }
    st->print_cr("Number of buckets       : %9d", _bucket_count);
    st->print_cr("Number of entries       : %9d", total_entries);
    st->print_cr("Maximum bucket size     : %9d", max_bucket);
  }
};

////////////////////////////////////////////////////////////////////////
//
// OffsetCompactHashtable -- This is used to store many types of objects
// in the CDS archive. On 64-bit platforms, we save space by using a 32-bit
// narrowPtr from the CDS base address.

template <typename V>
inline V read_value_from_compact_hashtable(address base_address, u4 narrowp) {
  return AOTCompressedPointers::decode_not_null<V>(cast_from_u4(narrowp), base_address);
}

template <
  typename K,
  typename V,
  bool (*EQUALS)(V value, K key, int len)
  >
class OffsetCompactHashtable : public CompactHashtable<
    K, V, read_value_from_compact_hashtable<V>, EQUALS> {
};


////////////////////////////////////////////////////////////////////////
//
// Read/Write the contents of a hashtable textual dump (created by
// SymbolTable::dump and StringTable::dump).
// Because the dump file may be big (hundred of MB in extreme cases),
// we use mmap for fast access when reading it.
//
class HashtableTextDump {
  int _fd;
  const char* _base;
  const char* _p;
  const char* _end;
  const char* _filename;
  size_t      _size;
  int         _prefix_type;
  int         _line_no;
public:
  HashtableTextDump(const char* filename);
  ~HashtableTextDump();

  enum {
    SymbolPrefix = 1 << 0,
    StringPrefix = 1 << 1,
    Unknown = 1 << 2
  };

  void quit(const char* err, const char* msg);

  inline int remain() {
    return (int)(_end - _p);
  }
  int last_line_no() {
    return _line_no - 1;
  }

  void corrupted(const char *p, const char *msg);

  inline void corrupted_if(bool cond, const char *msg) {
    if (cond) {
      corrupted(_p, msg);
    }
  }

  bool skip_newline();
  int skip(char must_be_char);
  void skip_past(char c);
  void check_version(const char* ver);

  inline void get_num(char delim, int *num) {
    const char* p   = _p;
    const char* end = _end;
    u8 n = 0;

    while (p < end) {
      char c = *p++;
      if ('0' <= c && c <= '9') {
        n = n * 10 + (c - '0');
        if (n > (u8)INT_MAX) {
          corrupted(_p, "Num overflow");
        }
      } else if (c == delim) {
        _p = p;
        *num = (int)n;
        return;
      } else {
        // Not [0-9], not 'delim'
        corrupted(_p, "Unrecognized format");;
      }
    }

    corrupted(_end, "Incorrect format");
    ShouldNotReachHere();
  }

  void scan_prefix_type();
  int scan_prefix(int* utf8_length);
  int scan_string_prefix();
  int scan_symbol_prefix();

  int unescape(const char* from, const char* end, int count);
  void get_utf8(char* utf8_buffer, int utf8_length);
  static void put_utf8(outputStream* st, const char* utf8_string, size_t utf8_length);
};

#endif // SHARE_CLASSFILE_COMPACTHASHTABLE_HPP

/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_COMPACTHASHTABLE_HPP
#define SHARE_VM_CLASSFILE_COMPACTHASHTABLE_HPP

#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/symbol.hpp"
#include "services/diagnosticCommand.hpp"
#include "utilities/hashtable.hpp"

class NumberSeq;

// Stats for symbol tables in the CDS archive
class CompactHashtableStats VALUE_OBJ_CLASS_SPEC {
public:
  int hashentry_count;
  int hashentry_bytes;
  int bucket_count;
  int bucket_bytes;
};

/////////////////////////////////////////////////////////////////////////
//
// The compact hash table writer. Used at dump time for writing out
// the compact table to the shared archive.
//
// At dump time, the CompactHashtableWriter obtains all entries from the
// symbol table and adds them to a new temporary hash table. The hash
// table size (number of buckets) is calculated using
// '(num_entries + bucket_size - 1) / bucket_size'. The default bucket
// size is 4 and can be changed by -XX:SharedSymbolTableBucketSize option.
// 4 is chosen because it produces smaller sized bucket on average for
// faster lookup. It also has relatively small number of empty buckets and
// good distribution of the entries.
//
// We use a simple hash function (symbol_hash % num_bucket) for the table.
// The new table is compacted when written out. Please see comments
// above the CompactHashtable class for the table layout detail. The bucket
// offsets are written to the archive as part of the compact table. The
// bucket offset is encoded in the low 30-bit (0-29) and the bucket type
// (regular or compact) are encoded in bit[31, 30]. For buckets with more
// than one entry, both symbol hash and symbol offset are written to the
// table. For buckets with only one entry, only the symbol offset is written
// to the table and the buckets are tagged as compact in their type bits.
// Buckets without entry are skipped from the table. Their offsets are
// still written out for faster lookup.
//
class CompactHashtableWriter: public StackObj {
public:
  class Entry: public CHeapObj<mtSymbol> {
    Entry* _next;
    unsigned int _hash;
    void* _literal;

  public:
    Entry(unsigned int hash, Symbol *symbol) : _next(NULL), _hash(hash), _literal(symbol) {}

    void *value() {
      return _literal;
    }
    Symbol *symbol() {
      return (Symbol*)_literal;
    }
    unsigned int hash() {
      return _hash;
    }
    Entry *next()           {return _next;}
    void set_next(Entry *p) {_next = p;}
  }; // class CompactHashtableWriter::Entry

private:
  static int number_of_buckets(int num_entries);

  const char* _table_name;
  int _num_entries;
  int _num_buckets;
  juint* _bucket_sizes;
  Entry** _buckets;
  int _required_bytes;
  CompactHashtableStats* _stats;

public:
  // This is called at dump-time only
  CompactHashtableWriter(const char* table_name, int num_entries, CompactHashtableStats* stats);
  ~CompactHashtableWriter();

  int get_required_bytes() {
    return _required_bytes;
  }

  void add(unsigned int hash, Symbol* symbol) {
    add(hash, new Entry(hash, symbol));
  }

private:
  void add(unsigned int hash, Entry* entry);
  juint* dump_table(juint* p, juint** first_bucket, NumberSeq* summary);
  juint* dump_buckets(juint* table, juint* p, NumberSeq* summary);

public:
  void dump(char** top, char* end);
};

#define REGULAR_BUCKET_TYPE       0
#define COMPACT_BUCKET_TYPE       1
#define TABLEEND_BUCKET_TYPE      3
#define BUCKET_OFFSET_MASK        0x3FFFFFFF
#define BUCKET_OFFSET(info)       ((info) & BUCKET_OFFSET_MASK)
#define BUCKET_TYPE_SHIFT         30
#define BUCKET_TYPE(info)         (((info) & ~BUCKET_OFFSET_MASK) >> BUCKET_TYPE_SHIFT)
#define BUCKET_INFO(offset, type) (((type) << BUCKET_TYPE_SHIFT) | ((offset) & BUCKET_OFFSET_MASK))

/////////////////////////////////////////////////////////////////////////////
//
// CompactHashtable is used to stored the CDS archive's symbol table. Used
// at runtime only to access the compact table from the archive.
//
// Because these tables are read-only (no entries can be added/deleted) at run-time
// and tend to have large number of entries, we try to minimize the footprint
// cost per entry.
//
// Layout of compact symbol table in the shared archive:
//
//   uintx base_address;
//   juint num_symbols;
//   juint num_buckets;
//   juint bucket_infos[num_buckets+1]; // bit[31,30]: type; bit[29-0]: offset
//   juint table[]
//
// -----------------------------------
// | base_address  | num_symbols     |
// |---------------------------------|
// | num_buckets   | bucket_info0    |
// |---------------------------------|
// | bucket_info1  | bucket_info2    |
// | bucket_info3    ...             |
// | ....          | table_end_info  |
// |---------------------------------|
// | entry0                          |
// | entry1                          |
// | entry2                          |
// |                                 |
// | ...                             |
// -----------------------------------
//
// The size of the bucket_info table is 'num_buckets + 1'. Each entry of the
// bucket_info table is a 32-bit encoding of the bucket type and bucket offset,
// with the type in the left-most 2-bit and offset in the remaining 30-bit.
// The last entry is a special type. It contains the offset of the last
// bucket end. We use that information when traversing the compact table.
//
// There are two types of buckets, regular buckets and compact buckets. The
// compact buckets have '01' in their highest 2-bit, and regular buckets have
// '00' in their highest 2-bit.
//
// For normal buckets, each symbol's entry is 8 bytes in the table[]:
//   juint hash;    /* symbol hash */
//   juint offset;  /* Symbol* sym = (Symbol*)(base_address + offset) */
//
// For compact buckets, each entry has only the 4-byte 'offset' in the table[].
//
// See CompactHashtable::lookup() for how the table is searched at runtime.
// See CompactHashtableWriter::dump() for how the table is written at CDS
// dump time.
//
template <class T, class N> class CompactHashtable VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
  uintx  _base_address;
  juint  _entry_count;
  juint  _bucket_count;
  juint  _table_end_offset;
  juint* _buckets;

  inline bool equals(T entry, const char* name, int len) {
    if (entry->equals(name, len)) {
      assert(entry->refcount() == -1, "must be shared");
      return true;
    } else {
      return false;
    }
  }

public:
  CompactHashtable() {
    _entry_count = 0;
    _bucket_count = 0;
    _table_end_offset = 0;
    _buckets = 0;
  }
  const char* init(const char *buffer);

  // Lookup an entry from the compact table
  inline T lookup(const N* name, unsigned int hash, int len) {
    if (_entry_count > 0) {
      assert(!DumpSharedSpaces, "run-time only");
      int index = hash % _bucket_count;
      juint bucket_info = _buckets[index];
      juint bucket_offset = BUCKET_OFFSET(bucket_info);
      int   bucket_type = BUCKET_TYPE(bucket_info);
      juint* bucket = _buckets + bucket_offset;
      juint* bucket_end = _buckets;

      if (bucket_type == COMPACT_BUCKET_TYPE) {
        // the compact bucket has one entry with symbol offset only
        T entry = (T)((void*)(_base_address + bucket[0]));
        if (equals(entry, name, len)) {
          return entry;
        }
      } else {
        // This is a regular bucket, which has more than one
        // entries. Each entry is a pair of symbol (hash, offset).
        // Seek until the end of the bucket.
        bucket_end += BUCKET_OFFSET(_buckets[index + 1]);
        while (bucket < bucket_end) {
          unsigned int h = (unsigned int)(bucket[0]);
          if (h == hash) {
            juint offset = bucket[1];
            T entry = (T)((void*)(_base_address + offset));
            if (equals(entry, name, len)) {
              return entry;
            }
          }
          bucket += 2;
        }
      }
    }
    return NULL;
  }

  // iterate over symbols
  void symbols_do(SymbolClosure *cl);
};

////////////////////////////////////////////////////////////////////////
//
// Read/Write the contents of a hashtable textual dump (created by
// SymbolTable::dump).
// Because the dump file may be big (hundred of MB in extreme cases),
// we use mmap for fast access when reading it.
//
class HashtableTextDump VALUE_OBJ_CLASS_SPEC {
  int _fd;
  const char* _base;
  const char* _p;
  const char* _end;
  const char* _filename;
  size_t      _size;
public:
  HashtableTextDump(const char* filename);
  ~HashtableTextDump();

  void quit(const char* err, const char* msg);

  inline int remain() {
    return (int)(_end - _p);
  }

  void corrupted(const char *p);

  inline void corrupted_if(bool cond) {
    if (cond) {
      corrupted(_p);
    }
  }

  bool skip_newline();
  int skip(char must_be_char);
  void skip_past(char c);
  void check_version(const char* ver);

  inline int get_num(char delim) {
    const char* p   = _p;
    const char* end = _end;
    int num = 0;

    while (p < end) {
      char c = *p ++;
      if ('0' <= c && c <= '9') {
        num = num * 10 + (c - '0');
      } else if (c == delim) {
        _p = p;
        return num;
      } else {
        corrupted(p-1);
      }
    }
    corrupted(_end);
    ShouldNotReachHere();
    return 0;
  }

  int scan_prefix();
  int scan_prefix2();

  jchar unescape(const char* from, const char* end, int count);
  void get_utf8(char* utf8_buffer, int utf8_length);
  static void put_utf8(outputStream* st, const char* utf8_string, int utf8_length);
};

///////////////////////////////////////////////////////////////////////
//
// jcmd command support for symbol table and string table dumping:
//   VM.symboltable -verbose: for dumping the symbol table
//   VM.stringtable -verbose: for dumping the string table
//
class VM_DumpHashtable : public VM_Operation {
private:
  outputStream* _out;
  int _which;
  bool _verbose;
public:
  enum {
    DumpSymbols = 1 << 0,
    DumpStrings = 1 << 1,
    DumpSysDict = 1 << 2  // not implemented yet
  };
  VM_DumpHashtable(outputStream* out, int which, bool verbose) {
    _out = out;
    _which = which;
    _verbose = verbose;
  }

  virtual VMOp_Type type() const { return VMOp_DumpHashtable; }

  virtual void doit() {
    switch (_which) {
    case DumpSymbols:
      SymbolTable::dump(_out, _verbose);
      break;
    case DumpStrings:
      StringTable::dump(_out, _verbose);
      break;
    default:
      ShouldNotReachHere();
    }
  }
};

class SymboltableDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _verbose;
public:
  SymboltableDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "VM.symboltable";
  }
  static const char* description() {
    return "Dump symbol table.";
  }
  static const char* impact() {
    return "Medium: Depends on Java content.";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class StringtableDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _verbose;
public:
  StringtableDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "VM.stringtable";
  }
  static const char* description() {
    return "Dump string table.";
  }
  static const char* impact() {
    return "Medium: Depends on Java content.";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

#endif // SHARE_VM_CLASSFILE_COMPACTHASHTABLE_HPP

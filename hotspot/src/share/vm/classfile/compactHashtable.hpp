/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/symbol.hpp"
#include "services/diagnosticCommand.hpp"
#include "utilities/hashtable.hpp"

template <class T, class N> class CompactHashtable;
class NumberSeq;
class SimpleCompactHashtable;
class SerializeClosure;

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
// symbol/string table and adds them to a new temporary hash table. The hash
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
// (regular or compact) are encoded in bit[31, 30]. For buckets with more
// than one entry, both hash and entry offset are written to the
// table. For buckets with only one entry, only the entry offset is written
// to the table and the buckets are tagged as compact in their type bits.
// Buckets without entry are skipped from the table. Their offsets are
// still written out for faster lookup.
//
class CompactHashtableWriter: public StackObj {
public:
  class Entry VALUE_OBJ_CLASS_SPEC {
    unsigned int _hash;
    u4 _value;

  public:
    Entry() {}
    Entry(unsigned int hash, u4 val) : _hash(hash), _value(val) {}

    u4 value() {
      return _value;
    }
    unsigned int hash() {
      return _hash;
    }

    bool operator==(const CompactHashtableWriter::Entry& other) {
      return (_value == other._value && _hash == other._hash);
    }
  }; // class CompactHashtableWriter::Entry

private:
  int _num_entries;
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
  CompactHashtableWriter(int num_buckets, CompactHashtableStats* stats);
  ~CompactHashtableWriter();

  void add(unsigned int hash, u4 value);
  void add(u4 value) {
    add((unsigned int)value, value);
  }

private:
  void allocate_table();
  void dump_table(NumberSeq* summary);

public:
  void dump(SimpleCompactHashtable *cht, const char* table_name);
  const char* table_name();
};

class CompactSymbolTableWriter: public CompactHashtableWriter {
public:
  CompactSymbolTableWriter(int num_buckets, CompactHashtableStats* stats) :
    CompactHashtableWriter(num_buckets, stats) {}
  void add(unsigned int hash, Symbol *symbol);
  void dump(CompactHashtable<Symbol*, char> *cht);
};

class CompactStringTableWriter: public CompactHashtableWriter {
public:
  CompactStringTableWriter(int num_entries, CompactHashtableStats* stats) :
    CompactHashtableWriter(num_entries, stats) {}
  void add(unsigned int hash, oop string);
  void dump(CompactHashtable<oop, char> *cht);
};

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
// CompactHashtable is used to stored the CDS archive's symbol/string table. Used
// at runtime only to access the compact table from the archive.
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
// The last entry is a special type. It contains the end of the last
// bucket.
//
// There are two types of buckets, regular buckets and value_only buckets. The
// value_only buckets have '01' in their highest 2-bit, and regular buckets have
// '00' in their highest 2-bit.
//
// For normal buckets, each entry is 8 bytes in the entries[]:
//   u4 hash;    /* symbol/string hash */
//   union {
//     u4 offset;  /* Symbol* sym = (Symbol*)(base_address + offset) */
//     narrowOop str; /* String narrowOop encoding */
//   }
//
//
// For value_only buckets, each entry has only the 4-byte 'offset' in the entries[].
//
// Example -- note that the second bucket is a VALUE_ONLY_BUCKET_TYPE so the hash code
//            is skipped.
// buckets[0, 4, 5, ....]
//         |  |  |
//         |  |  +---+
//         |  |      |
//         |  +----+ |
//         v       v v
// entries[H,O,H,O,O,H,O,H,O.....]
//
// See CompactHashtable::lookup() for how the table is searched at runtime.
// See CompactHashtableWriter::dump() for how the table is written at CDS
// dump time.
//
class SimpleCompactHashtable VALUE_OBJ_CLASS_SPEC {
protected:
  address  _base_address;
  u4  _bucket_count;
  u4  _entry_count;
  u4* _buckets;
  u4* _entries;

public:
  SimpleCompactHashtable() {
    _entry_count = 0;
    _bucket_count = 0;
    _buckets = 0;
    _entries = 0;
  }

  void reset() {
    _bucket_count = 0;
    _entry_count = 0;
    _buckets = 0;
    _entries = 0;
  }

  void init(address base_address, u4 entry_count, u4 bucket_count, u4* buckets, u4* entries) {
    _base_address = base_address;
    _bucket_count = bucket_count;
    _entry_count = entry_count;
    _buckets = buckets;
    _entries = entries;
  }

  template <class I> inline void iterate(const I& iterator);

  bool exists(u4 value);

  // For reading from/writing to the CDS archive
  void serialize(SerializeClosure* soc);
};

template <class T, class N> class CompactHashtable : public SimpleCompactHashtable {
  friend class VMStructs;

public:
  enum CompactHashtableType {
    _symbol_table = 0,
    _string_table = 1
  };

private:
  u4 _type;

  inline Symbol* decode_entry(CompactHashtable<Symbol*, char>* const t,
                              u4 offset, const char* name, int len);

  inline oop decode_entry(CompactHashtable<oop, char>* const t,
                          u4 offset, const char* name, int len);
public:
  CompactHashtable() : SimpleCompactHashtable() {}

  void set_type(CompactHashtableType type) {
    _type = (u4)type;
  }

  // Lookup an entry from the compact table
  inline T lookup(const N* name, unsigned int hash, int len);

  // iterate over symbols
  void symbols_do(SymbolClosure *cl);

  // iterate over strings
  void oops_do(OopClosure* f);

  // For reading from/writing to the CDS archive
  void serialize(SerializeClosure* soc);
};

////////////////////////////////////////////////////////////////////////
//
// Read/Write the contents of a hashtable textual dump (created by
// SymbolTable::dump and StringTable::dump).
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

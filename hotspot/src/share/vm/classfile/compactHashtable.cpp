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

#include "precompiled.hpp"
#include "classfile/compactHashtable.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceShared.hpp"
#include "prims/jvm.h"
#include "utilities/numberSeq.hpp"
#include <sys/stat.h>

/////////////////////////////////////////////////////
//
// The compact hash table writer implementations
//
CompactHashtableWriter::CompactHashtableWriter(int num_buckets,
                                               CompactHashtableStats* stats) {
  assert(DumpSharedSpaces, "dump-time only");
  assert(num_buckets > 0, "no buckets");
  _num_buckets = num_buckets;
  _num_entries = 0;
  _buckets = NEW_C_HEAP_ARRAY(GrowableArray<Entry>*, _num_buckets, mtSymbol);
  for (int i=0; i<_num_buckets; i++) {
    _buckets[i] = new (ResourceObj::C_HEAP, mtSymbol) GrowableArray<Entry>(0, true, mtSymbol);
  }

  stats->bucket_count = _num_buckets;
  stats->bucket_bytes = (_num_buckets + 1) * (sizeof(u4));
  _stats = stats;
  _compact_buckets = NULL;
  _compact_entries = NULL;
  _num_empty_buckets = 0;
  _num_value_only_buckets = 0;
  _num_other_buckets = 0;
}

CompactHashtableWriter::~CompactHashtableWriter() {
  for (int index = 0; index < _num_buckets; index++) {
    GrowableArray<Entry>* bucket = _buckets[index];
    delete bucket;
  }

  FREE_C_HEAP_ARRAY(GrowableArray<Entry>*, _buckets);
}

// Add a symbol entry to the temporary hash table
void CompactHashtableWriter::add(unsigned int hash, u4 value) {
  int index = hash % _num_buckets;
  _buckets[index]->append_if_missing(Entry(hash, value));
  _num_entries++;
}

void CompactHashtableWriter::allocate_table() {
  int entries_space = 0;
  for (int index = 0; index < _num_buckets; index++) {
    GrowableArray<Entry>* bucket = _buckets[index];
    int bucket_size = bucket->length();
    if (bucket_size == 1) {
      entries_space++;
    } else {
      entries_space += 2 * bucket_size;
    }
  }

  if (entries_space & ~BUCKET_OFFSET_MASK) {
    vm_exit_during_initialization("CompactHashtableWriter::allocate_table: Overflow! "
                                  "Too many entries.");
  }

  Thread* THREAD = VMThread::vm_thread();
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  _compact_buckets = MetadataFactory::new_array<u4>(loader_data, _num_buckets + 1, THREAD);
  _compact_entries = MetadataFactory::new_array<u4>(loader_data, entries_space, THREAD);

  _stats->hashentry_count = _num_entries;
  _stats->hashentry_bytes = entries_space * sizeof(u4);
}

// Write the compact table's buckets
void CompactHashtableWriter::dump_table(NumberSeq* summary) {
  u4 offset = 0;
  for (int index = 0; index < _num_buckets; index++) {
    GrowableArray<Entry>* bucket = _buckets[index];
    int bucket_size = bucket->length();
    if (bucket_size == 1) {
      // bucket with one entry is compacted and only has the symbol offset
      _compact_buckets->at_put(index, BUCKET_INFO(offset, VALUE_ONLY_BUCKET_TYPE));

      Entry ent = bucket->at(0);
      _compact_entries->at_put(offset++, ent.value());
      _num_value_only_buckets++;
    } else {
      // regular bucket, each entry is a symbol (hash, offset) pair
      _compact_buckets->at_put(index, BUCKET_INFO(offset, REGULAR_BUCKET_TYPE));

      for (int i=0; i<bucket_size; i++) {
        Entry ent = bucket->at(i);
        _compact_entries->at_put(offset++, u4(ent.hash())); // write entry hash
        _compact_entries->at_put(offset++, ent.value());
      }
      if (bucket_size == 0) {
        _num_empty_buckets++;
      } else {
        _num_other_buckets++;
      }
    }
    summary->add(bucket_size);
  }

  // Mark the end of the buckets
  _compact_buckets->at_put(_num_buckets, BUCKET_INFO(offset, TABLEEND_BUCKET_TYPE));
  assert(offset == (u4)_compact_entries->length(), "sanity");
}


// Write the compact table
void CompactHashtableWriter::dump(SimpleCompactHashtable *cht, const char* table_name) {
  NumberSeq summary;
  allocate_table();
  dump_table(&summary);

  int table_bytes = _stats->bucket_bytes + _stats->hashentry_bytes;
  address base_address = address(MetaspaceShared::shared_rs()->base());
  cht->init(base_address,  _num_entries, _num_buckets,
            _compact_buckets->data(), _compact_entries->data());

  if (PrintSharedSpaces) {
    double avg_cost = 0.0;
    if (_num_entries > 0) {
      avg_cost = double(table_bytes)/double(_num_entries);
    }
    tty->print_cr("Shared %s table stats -------- base: " PTR_FORMAT,
                  table_name, (intptr_t)base_address);
    tty->print_cr("Number of entries       : %9d", _num_entries);
    tty->print_cr("Total bytes used        : %9d", table_bytes);
    tty->print_cr("Average bytes per entry : %9.3f", avg_cost);
    tty->print_cr("Average bucket size     : %9.3f", summary.avg());
    tty->print_cr("Variance of bucket size : %9.3f", summary.variance());
    tty->print_cr("Std. dev. of bucket size: %9.3f", summary.sd());
    tty->print_cr("Empty buckets           : %9d", _num_empty_buckets);
    tty->print_cr("Value_Only buckets      : %9d", _num_value_only_buckets);
    tty->print_cr("Other buckets           : %9d", _num_other_buckets);
  }
}

/////////////////////////////////////////////////////////////
//
// Customization for dumping Symbol and String tables

void CompactSymbolTableWriter::add(unsigned int hash, Symbol *symbol) {
  address base_address = address(MetaspaceShared::shared_rs()->base());
  uintx max_delta = uintx(MetaspaceShared::shared_rs()->size());
  assert(max_delta <= MAX_SHARED_DELTA, "range check");

  uintx deltax = address(symbol) - base_address;
  assert(deltax < max_delta, "range check");
  u4 delta = u4(deltax);

  CompactHashtableWriter::add(hash, delta);
}

void CompactStringTableWriter::add(unsigned int hash, oop string) {
  CompactHashtableWriter::add(hash, oopDesc::encode_heap_oop(string));
}

void CompactSymbolTableWriter::dump(CompactHashtable<Symbol*, char> *cht) {
  CompactHashtableWriter::dump(cht, "symbol");
}

void CompactStringTableWriter::dump(CompactHashtable<oop, char> *cht) {
  CompactHashtableWriter::dump(cht, "string");
}

/////////////////////////////////////////////////////////////
//
// The CompactHashtable implementation
//

void SimpleCompactHashtable::serialize(SerializeClosure* soc) {
  soc->do_ptr((void**)&_base_address);
  soc->do_u4(&_entry_count);
  soc->do_u4(&_bucket_count);
  soc->do_ptr((void**)&_buckets);
  soc->do_ptr((void**)&_entries);
}

bool SimpleCompactHashtable::exists(u4 value) {
  assert(!DumpSharedSpaces, "run-time only");

  if (_entry_count == 0) {
    return false;
  }

  unsigned int hash = (unsigned int)value;
  int index = hash % _bucket_count;
  u4 bucket_info = _buckets[index];
  u4 bucket_offset = BUCKET_OFFSET(bucket_info);
  int bucket_type = BUCKET_TYPE(bucket_info);
  u4* entry = _entries + bucket_offset;

  if (bucket_type == VALUE_ONLY_BUCKET_TYPE) {
    return (entry[0] == value);
  } else {
    u4*entry_max = _entries + BUCKET_OFFSET(_buckets[index + 1]);
    while (entry <entry_max) {
      if (entry[1] == value) {
        return true;
      }
      entry += 2;
    }
    return false;
  }
}

template <class I>
inline void SimpleCompactHashtable::iterate(const I& iterator) {
  assert(!DumpSharedSpaces, "run-time only");
  for (u4 i = 0; i < _bucket_count; i++) {
    u4 bucket_info = _buckets[i];
    u4 bucket_offset = BUCKET_OFFSET(bucket_info);
    int bucket_type = BUCKET_TYPE(bucket_info);
    u4* entry = _entries + bucket_offset;

    if (bucket_type == VALUE_ONLY_BUCKET_TYPE) {
      iterator.do_value(_base_address, entry[0]);
    } else {
      u4*entry_max = _entries + BUCKET_OFFSET(_buckets[i + 1]);
      while (entry < entry_max) {
        iterator.do_value(_base_address, entry[1]);
        entry += 2;
      }
    }
  }
}

template <class T, class N> void CompactHashtable<T, N>::serialize(SerializeClosure* soc) {
  SimpleCompactHashtable::serialize(soc);
  soc->do_u4(&_type);
}

class CompactHashtable_SymbolIterator {
  SymbolClosure* const _closure;
public:
  CompactHashtable_SymbolIterator(SymbolClosure *cl) : _closure(cl) {}
  inline void do_value(address base_address, u4 offset) const {
    Symbol* sym = (Symbol*)((void*)(base_address + offset));
    _closure->do_symbol(&sym);
  }
};

template <class T, class N> void CompactHashtable<T, N>::symbols_do(SymbolClosure *cl) {
  CompactHashtable_SymbolIterator iterator(cl);
  iterate(iterator);
}

class CompactHashtable_OopIterator {
  OopClosure* const _closure;
public:
  CompactHashtable_OopIterator(OopClosure *cl) : _closure(cl) {}
  inline void do_value(address base_address, u4 offset) const {
    narrowOop o = (narrowOop)offset;
    _closure->do_oop(&o);
  }
};

template <class T, class N> void CompactHashtable<T, N>::oops_do(OopClosure* cl) {
  assert(_type == _string_table || _bucket_count == 0, "sanity");
  CompactHashtable_OopIterator iterator(cl);
  iterate(iterator);
}

// Explicitly instantiate these types
template class CompactHashtable<Symbol*, char>;
template class CompactHashtable<oop, char>;

#ifndef O_BINARY       // if defined (Win32) use binary files.
#define O_BINARY 0     // otherwise do nothing.
#endif

////////////////////////////////////////////////////////
//
// HashtableTextDump
//
HashtableTextDump::HashtableTextDump(const char* filename) : _fd(-1) {
  struct stat st;
  if (os::stat(filename, &st) != 0) {
    quit("Unable to get hashtable dump file size", filename);
  }
  _size = st.st_size;
  _fd = open(filename, O_RDONLY | O_BINARY, 0);
  if (_fd < 0) {
    quit("Unable to open hashtable dump file", filename);
  }
  _base = os::map_memory(_fd, filename, 0, NULL, _size, true, false);
  if (_base == NULL) {
    quit("Unable to map hashtable dump file", filename);
  }
  _p = _base;
  _end = _base + st.st_size;
  _filename = filename;
  _prefix_type = Unknown;
  _line_no = 1;
}

HashtableTextDump::~HashtableTextDump() {
  os::unmap_memory((char*)_base, _size);
  if (_fd >= 0) {
    close(_fd);
  }
}

void HashtableTextDump::quit(const char* err, const char* msg) {
  vm_exit_during_initialization(err, msg);
}

void HashtableTextDump::corrupted(const char *p, const char* msg) {
  char info[100];
  jio_snprintf(info, sizeof(info),
               "%s. Corrupted at line %d (file pos %d)",
               msg, _line_no, (int)(p - _base));
  quit(info, _filename);
}

bool HashtableTextDump::skip_newline() {
  if (_p[0] == '\r' && _p[1] == '\n') {
    _p += 2;
  } else if (_p[0] == '\n') {
    _p += 1;
  } else {
    corrupted(_p, "Unexpected character");
  }
  _line_no++;
  return true;
}

int HashtableTextDump::skip(char must_be_char) {
  corrupted_if(remain() < 1, "Truncated");
  corrupted_if(*_p++ != must_be_char, "Unexpected character");
  return 0;
}

void HashtableTextDump::skip_past(char c) {
  for (;;) {
    corrupted_if(remain() < 1, "Truncated");
    if (*_p++ == c) {
      return;
    }
  }
}

void HashtableTextDump::check_version(const char* ver) {
  int len = (int)strlen(ver);
  corrupted_if(remain() < len, "Truncated");
  if (strncmp(_p, ver, len) != 0) {
    quit("wrong version of hashtable dump file", _filename);
  }
  _p += len;
  skip_newline();
}

void HashtableTextDump::scan_prefix_type() {
  _p++;
  if (strncmp(_p, "SECTION: String", 15) == 0) {
    _p += 15;
    _prefix_type = StringPrefix;
  } else if (strncmp(_p, "SECTION: Symbol", 15) == 0) {
    _p += 15;
    _prefix_type = SymbolPrefix;
  } else {
    _prefix_type = Unknown;
  }
  skip_newline();
}

int HashtableTextDump::scan_prefix(int* utf8_length) {
  if (*_p == '@') {
    scan_prefix_type();
  }

  switch (_prefix_type) {
  case SymbolPrefix:
    *utf8_length = scan_symbol_prefix(); break;
  case StringPrefix:
    *utf8_length = scan_string_prefix(); break;
  default:
    tty->print_cr("Shared input data type: Unknown.");
    corrupted(_p, "Unknown data type");
  }

  return _prefix_type;
}

int HashtableTextDump::scan_string_prefix() {
  // Expect /[0-9]+: /
  int utf8_length = 0;
  get_num(':', &utf8_length);
  if (*_p != ' ') {
    corrupted(_p, "Wrong prefix format for string");
  }
  _p++;
  return utf8_length;
}

int HashtableTextDump::scan_symbol_prefix() {
  // Expect /[0-9]+ (-|)[0-9]+: /
  int utf8_length = 0;
  get_num(' ', &utf8_length);
  if (*_p == '-') {
    _p++;
  }
  int ref_num;
  get_num(':', &ref_num);
  if (*_p != ' ') {
    corrupted(_p, "Wrong prefix format for symbol");
  }
  _p++;
  return utf8_length;
}

jchar HashtableTextDump::unescape(const char* from, const char* end, int count) {
  jchar value = 0;

  corrupted_if(from + count > end, "Truncated");

  for (int i=0; i<count; i++) {
    char c = *from++;
    switch (c) {
    case '0': case '1': case '2': case '3': case '4':
    case '5': case '6': case '7': case '8': case '9':
      value = (value << 4) + c - '0';
      break;
    case 'a': case 'b': case 'c':
    case 'd': case 'e': case 'f':
      value = (value << 4) + 10 + c - 'a';
      break;
    case 'A': case 'B': case 'C':
    case 'D': case 'E': case 'F':
      value = (value << 4) + 10 + c - 'A';
      break;
    default:
      ShouldNotReachHere();
    }
  }
  return value;
}

void HashtableTextDump::get_utf8(char* utf8_buffer, int utf8_length) {
  // cache in local vars
  const char* from = _p;
  const char* end = _end;
  char* to = utf8_buffer;
  int n = utf8_length;

  for (; n > 0 && from < end; n--) {
    if (*from != '\\') {
      *to++ = *from++;
    } else {
      corrupted_if(from + 2 > end, "Truncated");
      char c = from[1];
      from += 2;
      switch (c) {
      case 'x':
        {
          jchar value = unescape(from, end, 2);
          from += 2;
          assert(value <= 0xff, "sanity");
          *to++ = (char)(value & 0xff);
        }
        break;
      case 't':  *to++ = '\t'; break;
      case 'n':  *to++ = '\n'; break;
      case 'r':  *to++ = '\r'; break;
      case '\\': *to++ = '\\'; break;
      default:
        corrupted(_p, "Unsupported character");
      }
    }
  }
  corrupted_if(n > 0, "Truncated"); // expected more chars but file has ended
  _p = from;
  skip_newline();
}

// NOTE: the content is NOT the same as
// UTF8::as_quoted_ascii(const char* utf8_str, int utf8_length, char* buf, int buflen).
// We want to escape \r\n\t so that output [1] is more readable; [2] can be more easily
// parsed by scripts; [3] quickly processed by HashtableTextDump::get_utf8()
void HashtableTextDump::put_utf8(outputStream* st, const char* utf8_string, int utf8_length) {
  const char *c = utf8_string;
  const char *end = c + utf8_length;
  for (; c < end; c++) {
    switch (*c) {
    case '\t': st->print("\\t"); break;
    case '\r': st->print("\\r"); break;
    case '\n': st->print("\\n"); break;
    case '\\': st->print("\\\\"); break;
    default:
      if (isprint(*c)) {
        st->print("%c", *c);
      } else {
        st->print("\\x%02x", ((unsigned int)*c) & 0xff);
      }
    }
  }
}

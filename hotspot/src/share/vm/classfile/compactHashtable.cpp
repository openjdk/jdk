/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "memory/metaspaceShared.hpp"
#include "utilities/numberSeq.hpp"
#include <sys/stat.h>

/////////////////////////////////////////////////////
//
// The compact hash table writer implementations
//
CompactHashtableWriter::CompactHashtableWriter(const char* table_name,
                                               int num_entries,
                                               CompactHashtableStats* stats) {
  assert(DumpSharedSpaces, "dump-time only");
  _table_name = table_name;
  _num_entries = num_entries;
  _num_buckets = number_of_buckets(_num_entries);
  _buckets = NEW_C_HEAP_ARRAY(Entry*, _num_buckets, mtSymbol);
  memset(_buckets, 0, sizeof(Entry*) * _num_buckets);

  /* bucket sizes table */
  _bucket_sizes = NEW_C_HEAP_ARRAY(juint, _num_buckets, mtSymbol);
  memset(_bucket_sizes, 0, sizeof(juint) * _num_buckets);

  stats->hashentry_count = _num_entries;
  // Compact buckets' entries will have only the 4-byte offset, but
  // we don't know how many there will be at this point. So use a
  // conservative estimate here. The size is adjusted later when we
  // write out the buckets.
  stats->hashentry_bytes = _num_entries * 8;
  stats->bucket_count    = _num_buckets;
  stats->bucket_bytes    = (_num_buckets + 1) * (sizeof(juint));
  _stats = stats;

  // See compactHashtable.hpp for table layout
  _required_bytes = sizeof(juint) * 2; // _base_address, written as 2 juints
  _required_bytes+= sizeof(juint) +    // num_entries
                    sizeof(juint) +    // num_buckets
                    stats->hashentry_bytes +
                    stats->bucket_bytes;
}

CompactHashtableWriter::~CompactHashtableWriter() {
  for (int index = 0; index < _num_buckets; index++) {
    Entry* next = NULL;
    for (Entry* tent = _buckets[index]; tent; tent = next) {
      next = tent->next();
      delete tent;
    }
  }

  FREE_C_HEAP_ARRAY(juint, _bucket_sizes);
  FREE_C_HEAP_ARRAY(Entry*, _buckets);
}

// Calculate the number of buckets in the temporary hash table
int CompactHashtableWriter::number_of_buckets(int num_entries) {
  const int buksize = (int)SharedSymbolTableBucketSize;
  int num_buckets = (num_entries + buksize - 1) / buksize;
  num_buckets = (num_buckets + 1) & (~0x01);

  return num_buckets;
}

// Add a symbol entry to the temporary hash table
void CompactHashtableWriter::add(unsigned int hash, Entry* entry) {
  int index = hash % _num_buckets;
  entry->set_next(_buckets[index]);
  _buckets[index] = entry;
  _bucket_sizes[index] ++;
}

// Write the compact table's bucket infos
juint* CompactHashtableWriter::dump_table(juint* p, juint** first_bucket,
                                          NumberSeq* summary) {
  int index;
  juint* compact_table = p;
  // Find the start of the buckets, skip the compact_bucket_infos table
  // and the table end offset.
  juint offset = _num_buckets + 1;
  *first_bucket = compact_table + offset;

  for (index = 0; index < _num_buckets; index++) {
    int bucket_size = _bucket_sizes[index];
    if (bucket_size == 1) {
      // bucket with one entry is compacted and only has the symbol offset
      compact_table[index] = BUCKET_INFO(offset, COMPACT_BUCKET_TYPE);
      offset += bucket_size; // each entry contains symbol offset only
    } else {
      // regular bucket, each entry is a symbol (hash, offset) pair
      compact_table[index] = BUCKET_INFO(offset, REGULAR_BUCKET_TYPE);
      offset += bucket_size * 2; // each hash entry is 2 juints
    }
    if (offset & ~BUCKET_OFFSET_MASK) {
      vm_exit_during_initialization("CompactHashtableWriter::dump_table: Overflow! "
                                    "Too many symbols.");
    }
    summary->add(bucket_size);
  }
  // Mark the end of the table
  compact_table[_num_buckets] = BUCKET_INFO(offset, TABLEEND_BUCKET_TYPE);

  return compact_table;
}

// Write the compact table's entries
juint* CompactHashtableWriter::dump_buckets(juint* compact_table, juint* p,
                                            NumberSeq* summary) {
  uintx base_address = uintx(MetaspaceShared::shared_rs()->base());
  uintx max_delta    = uintx(MetaspaceShared::shared_rs()->size());
  assert(max_delta <= 0x7fffffff, "range check");
  int num_compact_buckets = 0;

  assert(p != NULL, "sanity");
  for (int index = 0; index < _num_buckets; index++) {
    juint count = 0;
    int bucket_size = _bucket_sizes[index];
    int bucket_type = BUCKET_TYPE(compact_table[index]);

    if (bucket_size == 1) {
      assert(bucket_type == COMPACT_BUCKET_TYPE, "Bad bucket type");
      num_compact_buckets ++;
    }
    for (Entry* tent = _buckets[index]; tent;
         tent = tent->next()) {
      if (bucket_type == REGULAR_BUCKET_TYPE) {
        *p++ = juint(tent->hash()); // write symbol hash
      }
      uintx deltax = uintx(tent->value()) - base_address;
      assert(deltax < max_delta, "range check");
      juint delta = juint(deltax);
      *p++ = delta; // write symbol offset
      count ++;
    }
    assert(count == _bucket_sizes[index], "sanity");
  }

  // Adjust the hashentry_bytes in CompactHashtableStats. Each compact
  // bucket saves 4-byte.
  _stats->hashentry_bytes -= num_compact_buckets * 4;

  return p;
}

// Write the compact table
void CompactHashtableWriter::dump(char** top, char* end) {
  NumberSeq summary;
  char* old_top = *top;
  juint* p = (juint*)(*top);

  uintx base_address = uintx(MetaspaceShared::shared_rs()->base());

  *p++ = high(base_address);
  *p++ = low (base_address); // base address
  *p++ = _num_entries;  // number of entries in the table
  *p++ = _num_buckets;  // number of buckets in the table

  juint* first_bucket = NULL;
  juint* compact_table = dump_table(p, &first_bucket, &summary);
  juint* bucket_end = dump_buckets(compact_table, first_bucket, &summary);

  assert(bucket_end <= (juint*)end, "cannot write past end");
  *top = (char*)bucket_end;

  if (PrintSharedSpaces) {
    double avg_cost = 0.0;
    if (_num_entries > 0) {
      avg_cost = double(_required_bytes)/double(_num_entries);
    }
    tty->print_cr("Shared %s table stats -------- base: " PTR_FORMAT, _table_name, (intptr_t)base_address);
    tty->print_cr("Number of entries       : %9d", _num_entries);
    tty->print_cr("Total bytes used        : %9d", (int)((*top) - old_top));
    tty->print_cr("Average bytes per entry : %9.3f", avg_cost);
    tty->print_cr("Average bucket size     : %9.3f", summary.avg());
    tty->print_cr("Variance of bucket size : %9.3f", summary.variance());
    tty->print_cr("Std. dev. of bucket size: %9.3f", summary.sd());
    tty->print_cr("Maximum bucket size     : %9d", (int)summary.maximum());
  }
}

/////////////////////////////////////////////////////////////
//
// The CompactHashtable implementation
//
template <class T, class N> const char* CompactHashtable<T, N>::init(const char* buffer) {
  assert(!DumpSharedSpaces, "run-time only");
  juint*p = (juint*)buffer;
  juint upper = *p++;
  juint lower = *p++;
  _base_address = uintx(jlong_from(upper, lower));
  _entry_count = *p++;
  _bucket_count = *p++;
  _buckets = p;
  _table_end_offset = BUCKET_OFFSET(p[_bucket_count]); // located at the end of the bucket_info table

  juint *end = _buckets + _table_end_offset;
  return (const char*)end;
}

// Explicitly instantiate these types
template class CompactHashtable<Symbol*, char>;

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

void HashtableTextDump::corrupted(const char *p) {
  char info[60];
  sprintf(info, "corrupted at pos %d", (int)(p - _base));
  quit(info, _filename);
}

bool HashtableTextDump::skip_newline() {
  if (_p[0] == '\r' && _p[1] == '\n') {
    _p += 2;
  } else if (_p[0] == '\n') {
    _p += 1;
  } else {
    corrupted(_p);
  }
  return true;
}

int HashtableTextDump::skip(char must_be_char) {
  corrupted_if(remain() < 1);
  corrupted_if(*_p++ != must_be_char);
  return 0;
}

void HashtableTextDump::skip_past(char c) {
  for (;;) {
    corrupted_if(remain() < 1);
    if (*_p++ == c) {
      return;
    }
  }
}

void HashtableTextDump::check_version(const char* ver) {
  int len = (int)strlen(ver);
  corrupted_if(remain() < len);
  if (strncmp(_p, ver, len) != 0) {
    quit("wrong version of hashtable dump file", _filename);
  }
  _p += len;
  skip_newline();
}


int HashtableTextDump::scan_prefix() {
  // Expect /[0-9]+: /
  int utf8_length = get_num(':');
  if (*_p != ' ') {
    corrupted(_p);
  }
  _p++;
  return utf8_length;
}

int HashtableTextDump::scan_prefix2() {
  // Expect /[0-9]+ (-|)[0-9]+: /
  int utf8_length = get_num(' ');
  if (*_p == '-') {
    _p++;
  }
  (void)get_num(':');
  if (*_p != ' ') {
    corrupted(_p);
  }
  _p++;
  return utf8_length;
}

jchar HashtableTextDump::unescape(const char* from, const char* end, int count) {
  jchar value = 0;

  corrupted_if(from + count > end);

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
      corrupted_if(from + 2 > end);
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
        ShouldNotReachHere();
      }
    }
  }
  corrupted_if(n > 0); // expected more chars but file has ended
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

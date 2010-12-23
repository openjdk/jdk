/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/filemap.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/hashtable.inline.hpp"


// Closure for serializing initialization data in from a data area
// (oop_array) read from the shared file.

class ReadClosure : public SerializeOopClosure {
private:
  oop** _oop_array;

  inline oop nextOop() {
    return *(*_oop_array)++;
  }

public:
  ReadClosure(oop** oop_array) { _oop_array = oop_array; }

  void do_oop(oop* p) {
    assert(SharedSkipVerify || *p == NULL || *p == Universe::klassKlassObj(),
           "initializing previously initialized oop.");
    oop obj = nextOop();
    assert(SharedSkipVerify || (intptr_t)obj >= 0 || (intptr_t)obj < -100,
           "hit tag while initializing oops.");
    assert(SharedSkipVerify || obj->is_oop_or_null(), "invalid oop");
    *p = obj;
  }

  void do_oop(narrowOop* p) { ShouldNotReachHere(); }

  void do_ptr(void** p) {
    assert(*p == NULL, "initializing previous initialized pointer.");
    void* obj = nextOop();
    assert((intptr_t)obj >= 0 || (intptr_t)obj < -100,
           "hit tag while initializing ptrs.");
    *p = obj;
  }

  void do_ptr(HeapWord** p) { do_ptr((void **) p); }

  void do_int(int* p) {
    *p = (int)(intptr_t)nextOop();
  }

  void do_size_t(size_t* p) {
    // Assumes that size_t and pointers are the same size.
    *p = (size_t)nextOop();
  }

  void do_tag(int tag) {
    int old_tag;
    do_int(&old_tag);
    FileMapInfo::assert_mark(tag == old_tag);
  }

  void do_region(u_char* start, size_t size) {
    assert((intptr_t)start % sizeof(oop) == 0, "bad alignment");
    assert(size % sizeof(oop) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      *(oop*)start = nextOop();
      start += sizeof(oop);
      size -= sizeof(oop);
    }
  }

  bool reading() const { return true; }
};


// Read the oop and miscellaneous data from the shared file, and
// serialize it out to its various destinations.

void CompactingPermGenGen::initialize_oops() {
  FileMapInfo *mapinfo = FileMapInfo::current_info();

  char* buffer = mapinfo->region_base(md);

  // Skip over (reserve space for) a list of addresses of C++ vtables
  // for Klass objects.  They get filled in later.

  // Skip over (reserve space for) dummy C++ vtables Klass objects.
  // They are used as is.

  void** vtbl_list = (void**)buffer;
  buffer += vtbl_list_size * sizeof(void*);
  intptr_t vtable_size = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  buffer += vtable_size;

  // Create the symbol table using the bucket array at this spot in the
  // misc data space.  Since the symbol table is often modified, this
  // region (of mapped pages) will be copy-on-write.

  int symbolTableLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  int number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  SymbolTable::create_table((HashtableBucket*)buffer, symbolTableLen,
                            number_of_entries);
  buffer += symbolTableLen;

  // Create the string table using the bucket array at this spot in the
  // misc data space.  Since the string table is often modified, this
  // region (of mapped pages) will be copy-on-write.

  int stringTableLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  StringTable::create_table((HashtableBucket*)buffer, stringTableLen,
                            number_of_entries);
  buffer += stringTableLen;

  // Create the shared dictionary using the bucket array at this spot in
  // the misc data space.  Since the shared dictionary table is never
  // modified, this region (of mapped pages) will be (effectively, if
  // not explicitly) read-only.

  int sharedDictionaryLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  SystemDictionary::set_shared_dictionary((HashtableBucket*)buffer,
                                          sharedDictionaryLen,
                                          number_of_entries);
  buffer += sharedDictionaryLen;

  // Create the package info table using the bucket array at this spot in
  // the misc data space.  Since the package info table is never
  // modified, this region (of mapped pages) will be (effectively, if
  // not explicitly) read-only.

  int pkgInfoLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  ClassLoader::create_package_info_table((HashtableBucket*)buffer, pkgInfoLen,
                                         number_of_entries);
  buffer += pkgInfoLen;
  ClassLoader::verify();

  // The following data in the shared misc data region are the linked
  // list elements (HashtableEntry objects) for the symbol table, string
  // table, and shared dictionary.  The heap objects refered to by the
  // symbol table, string table, and shared dictionary are permanent and
  // unmovable.  Since new entries added to the string and symbol tables
  // are always added at the beginning of the linked lists, THESE LINKED
  // LIST ELEMENTS ARE READ-ONLY.

  int len = *(intptr_t*)buffer; // skip over symbol table entries
  buffer += sizeof(intptr_t);
  buffer += len;

  len = *(intptr_t*)buffer;     // skip over string table entries
  buffer += sizeof(intptr_t);
  buffer += len;

  len = *(intptr_t*)buffer;     // skip over shared dictionary entries
  buffer += sizeof(intptr_t);
  buffer += len;

  len = *(intptr_t*)buffer;     // skip over package info table entries
  buffer += sizeof(intptr_t);
  buffer += len;

  len = *(intptr_t*)buffer;     // skip over package info table char[] arrays.
  buffer += sizeof(intptr_t);
  buffer += len;

  oop* oop_array = (oop*)buffer;
  ReadClosure rc(&oop_array);
  serialize_oops(&rc);
}

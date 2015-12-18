/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_COMPACTHASHTABLE_INLINE_HPP
#define SHARE_VM_CLASSFILE_COMPACTHASHTABLE_INLINE_HPP

#include "classfile/compactHashtable.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"

template <class T, class N>
inline Symbol* CompactHashtable<T, N>::lookup_entry(CompactHashtable<Symbol*, char>* const t,
                                             juint* addr, const char* name, int len) {
  Symbol* sym = (Symbol*)((void*)(_base_address + *addr));
  if (sym->equals(name, len)) {
    assert(sym->refcount() == -1, "must be shared");
    return sym;
  }

  return NULL;
}

template <class T, class N>
inline oop CompactHashtable<T, N>::lookup_entry(CompactHashtable<oop, char>* const t,
                                                juint* addr, const char* name, int len) {
  narrowOop obj = (narrowOop)(*addr);
  oop string = oopDesc::decode_heap_oop(obj);
  if (java_lang_String::equals(string, (jchar*)name, len)) {
    return string;
  }

  return NULL;
}

template <class T, class N>
inline T CompactHashtable<T,N>::lookup(const N* name, unsigned int hash, int len) {
  if (_entry_count > 0) {
    assert(!DumpSharedSpaces, "run-time only");
    int index = hash % _bucket_count;
    juint bucket_info = _buckets[index];
    juint bucket_offset = BUCKET_OFFSET(bucket_info);
    int   bucket_type = BUCKET_TYPE(bucket_info);
    juint* bucket = _buckets + bucket_offset;
    juint* bucket_end = _buckets;

    if (bucket_type == COMPACT_BUCKET_TYPE) {
      // the compact bucket has one entry with entry offset only
      T res = lookup_entry(this, &bucket[0], name, len);
      if (res != NULL) {
        return res;
      }
    } else {
      // This is a regular bucket, which has more than one
      // entries. Each entry is a pair of entry (hash, offset).
      // Seek until the end of the bucket.
      bucket_end += BUCKET_OFFSET(_buckets[index + 1]);
      while (bucket < bucket_end) {
        unsigned int h = (unsigned int)(bucket[0]);
        if (h == hash) {
          T res = lookup_entry(this, &bucket[1], name, len);
          if (res != NULL) {
            return res;
          }
        }
        bucket += 2;
      }
    }
  }
  return NULL;
}

inline void CompactHashtableWriter::add(unsigned int hash, Symbol* symbol) {
  add(hash, new Entry(hash, symbol));
}

inline void CompactHashtableWriter::add(unsigned int hash, oop string) {
  add(hash, new Entry(hash, string));
}


#endif // SHARE_VM_CLASSFILE_COMPACTHASHTABLE_INLINE_HPP

/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.hpp"

template <class T, class N>
inline Symbol* CompactHashtable<T, N>::decode_entry(CompactHashtable<Symbol*, char>* const t,
                                                    u4 offset, const char* name, int len) {
  Symbol* sym = (Symbol*)(_base_address + offset);
  if (sym->equals(name, len)) {
    assert(sym->refcount() == -1, "must be shared");
    return sym;
  }

  return NULL;
}

template <class T, class N>
inline oop CompactHashtable<T, N>::decode_entry(CompactHashtable<oop, char>* const t,
                                                u4 offset, const char* name, int len) {
  narrowOop obj = (narrowOop)offset;
  oop string = CompressedOops::decode(obj);
  if (java_lang_String::equals(string, (jchar*)name, len)) {
    return string;
  }

  return NULL;
}

template <class T, class N>
inline T CompactHashtable<T,N>::lookup(const N* name, unsigned int hash, int len) {
  if (_entry_count > 0) {
    int index = hash % _bucket_count;
    u4 bucket_info = _buckets[index];
    u4 bucket_offset = BUCKET_OFFSET(bucket_info);
    int bucket_type = BUCKET_TYPE(bucket_info);
    u4* entry = _entries + bucket_offset;

    if (bucket_type == VALUE_ONLY_BUCKET_TYPE) {
      T res = decode_entry(this, entry[0], name, len);
      if (res != NULL) {
        return res;
      }
    } else {
      // This is a regular bucket, which has more than one
      // entries. Each entry is a pair of entry (hash, offset).
      // Seek until the end of the bucket.
      u4* entry_max = _entries + BUCKET_OFFSET(_buckets[index + 1]);
      while (entry < entry_max) {
        unsigned int h = (unsigned int)(entry[0]);
        if (h == hash) {
          T res = decode_entry(this, entry[1], name, len);
          if (res != NULL) {
            return res;
          }
        }
        entry += 2;
      }
    }
  }
  return NULL;
}

#endif // SHARE_VM_CLASSFILE_COMPACTHASHTABLE_INLINE_HPP

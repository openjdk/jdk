/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_PROTECTIONDOMAINCACHE_HPP
#define SHARE_VM_CLASSFILE_PROTECTIONDOMAINCACHE_HPP

#include "oops/oop.hpp"
#include "memory/iterator.hpp"
#include "utilities/hashtable.hpp"

// This class caches the approved protection domains that can access loaded classes.
// Dictionary entry pd_set point to entries in this hashtable.   Please refer
// to dictionary.hpp pd_set for more information about how protection domain entries
// are used.
// This table is walked during GC, rather than the entire system dictionary
class ProtectionDomainCacheEntry : public HashtableEntry<oop, mtClass> {
  friend class VMStructs;
 public:
  oop protection_domain() { return literal(); }

  ProtectionDomainCacheEntry* next() {
    return (ProtectionDomainCacheEntry*)HashtableEntry<oop, mtClass>::next();
  }

  ProtectionDomainCacheEntry** next_addr() {
    return (ProtectionDomainCacheEntry**)HashtableEntry<oop, mtClass>::next_addr();
  }

  void oops_do(OopClosure* f) {
    f->do_oop(literal_addr());
  }

  void print() PRODUCT_RETURN;
  void verify();
};

// The ProtectionDomainCacheTable contains all protection domain oops. The system
// dictionary entries reference its entries instead of having references to oops
// directly.
// This is used to speed up system dictionary iteration: the oops in the
// protection domain are the only ones referring the Java heap. So when there is
// need to update these, instead of going over every entry of the system dictionary,
// we only need to iterate over this set.
// The amount of different protection domains used is typically magnitudes smaller
// than the number of system dictionary entries (loaded classes).
class ProtectionDomainCacheTable : public Hashtable<oop, mtClass> {
  friend class VMStructs;
private:
  ProtectionDomainCacheEntry* bucket(int i) {
    return (ProtectionDomainCacheEntry*) Hashtable<oop, mtClass>::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  ProtectionDomainCacheEntry** bucket_addr(int i) {
    return (ProtectionDomainCacheEntry**) Hashtable<oop, mtClass>::bucket_addr(i);
  }

  ProtectionDomainCacheEntry* new_entry(unsigned int hash, Handle protection_domain) {
    ProtectionDomainCacheEntry* entry = (ProtectionDomainCacheEntry*) Hashtable<oop, mtClass>::new_entry(hash, protection_domain());
    return entry;
  }

  static unsigned int compute_hash(Handle protection_domain);

  int index_for(Handle protection_domain);
  ProtectionDomainCacheEntry* add_entry(int index, unsigned int hash, Handle protection_domain);
  ProtectionDomainCacheEntry* find_entry(int index, Handle protection_domain);

public:
  ProtectionDomainCacheTable(int table_size);
  ProtectionDomainCacheEntry* get(Handle protection_domain);

  void unlink(BoolObjectClosure* cl);

  // GC support
  void oops_do(OopClosure* f);

  void print() PRODUCT_RETURN;
  void verify();
};


class ProtectionDomainEntry :public CHeapObj<mtClass> {
  friend class VMStructs;
 public:
  ProtectionDomainEntry* _next;
  ProtectionDomainCacheEntry* _pd_cache;

  ProtectionDomainEntry(ProtectionDomainCacheEntry* pd_cache, ProtectionDomainEntry* next) {
    _pd_cache = pd_cache;
    _next     = next;
  }

  ProtectionDomainEntry* next() { return _next; }
  oop protection_domain() { return _pd_cache->protection_domain(); }
};
#endif // SHARE_VM_CLASSFILE_PROTECTIONDOMAINCACHE_HPP

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

#include "precompiled.hpp"
#include "classfile/protectionDomainCache.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/hashtable.inline.hpp"

unsigned int ProtectionDomainCacheTable::compute_hash(Handle protection_domain) {
  // Identity hash can safepoint, so keep protection domain in a Handle.
  return (unsigned int)(protection_domain->identity_hash());
}

int ProtectionDomainCacheTable::index_for(Handle protection_domain) {
  return hash_to_index(compute_hash(protection_domain));
}

ProtectionDomainCacheTable::ProtectionDomainCacheTable(int table_size)
  : Hashtable<oop, mtClass>(table_size, sizeof(ProtectionDomainCacheEntry))
{
}

void ProtectionDomainCacheTable::unlink(BoolObjectClosure* is_alive) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  for (int i = 0; i < table_size(); ++i) {
    ProtectionDomainCacheEntry** p = bucket_addr(i);
    ProtectionDomainCacheEntry* entry = bucket(i);
    while (entry != NULL) {
      if (is_alive->do_object_b(entry->literal())) {
        p = entry->next_addr();
      } else {
        if (log_is_enabled(Debug, protectiondomain)) {
          outputStream* log = Log(protectiondomain)::debug_stream();
          log->print("protection domain unlinked: ");
          entry->literal()->print_value_on(log);
          log->cr();
        }
        *p = entry->next();
        free_entry(entry);
      }
      entry = *p;
    }
  }
}

void ProtectionDomainCacheTable::oops_do(OopClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (ProtectionDomainCacheEntry* probe = bucket(index);
                                     probe != NULL;
                                     probe = probe->next()) {
      probe->oops_do(f);
    }
  }
}

#ifndef PRODUCT
void ProtectionDomainCacheTable::print() {
  tty->print_cr("Protection domain cache table (table_size=%d, classes=%d)",
                table_size(), number_of_entries());
  for (int index = 0; index < table_size(); index++) {
    for (ProtectionDomainCacheEntry* probe = bucket(index);
                                     probe != NULL;
                                     probe = probe->next()) {
      probe->print();
    }
  }
}

void ProtectionDomainCacheEntry::print() {
  tty->print_cr("entry " PTR_FORMAT " value " PTR_FORMAT " next " PTR_FORMAT,
                p2i(this), p2i(literal()), p2i(next()));
}
#endif

void ProtectionDomainCacheTable::verify() {
  verify_table<ProtectionDomainCacheEntry>("Protection Domain Table");
}

void ProtectionDomainCacheEntry::verify() {
  guarantee(literal()->is_oop(), "must be an oop");
}

ProtectionDomainCacheEntry* ProtectionDomainCacheTable::get(Handle protection_domain) {
  unsigned int hash = compute_hash(protection_domain);
  int index = hash_to_index(hash);

  ProtectionDomainCacheEntry* entry = find_entry(index, protection_domain);
  if (entry == NULL) {
    entry = add_entry(index, hash, protection_domain);
  }
  return entry;
}

ProtectionDomainCacheEntry* ProtectionDomainCacheTable::find_entry(int index, Handle protection_domain) {
  for (ProtectionDomainCacheEntry* e = bucket(index); e != NULL; e = e->next()) {
    if (e->protection_domain() == protection_domain()) {
      return e;
    }
  }

  return NULL;
}

ProtectionDomainCacheEntry* ProtectionDomainCacheTable::add_entry(int index, unsigned int hash, Handle protection_domain) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(index == index_for(protection_domain), "incorrect index?");
  assert(find_entry(index, protection_domain) == NULL, "no double entry");

  ProtectionDomainCacheEntry* p = new_entry(hash, protection_domain);
  Hashtable<oop, mtClass>::add_entry(index, p);
  return p;
}

/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_PROTECTIONDOMAINCACHE_HPP
#define SHARE_CLASSFILE_PROTECTIONDOMAINCACHE_HPP

#include "oops/oop.hpp"
#include "oops/weakHandle.hpp"
#include "runtime/atomic.hpp"

// The ProtectionDomainCacheTable maps all java.security.ProtectionDomain objects that are
// registered by DictionaryEntry::add_to_package_access_cache() to a unique WeakHandle.
// The amount of different protection domains used is typically magnitudes smaller
// than the number of system dictionary entries (loaded classes).
class ProtectionDomainCacheTable : public AllStatic {

  static bool _dead_entries;
  static int _total_oops_removed;

public:
  static void initialize();
  static unsigned int compute_hash(const WeakHandle& protection_domain);
  static bool equals(const WeakHandle& protection_domain1, const WeakHandle& protection_domain2);

  static WeakHandle add_if_absent(Handle protection_domain);
  static void unlink();

  static void print_on(outputStream* st);
  static void verify();

  static bool has_work() { return _dead_entries; }
  static void trigger_cleanup();

  static int removed_entries_count() { return _total_oops_removed; };
  static int number_of_entries();
  static void print_table_statistics(outputStream* st);
};


// This describes the linked list protection domain for each DictionaryEntry in its package_access_cache.
class ProtectionDomainEntry :public CHeapObj<mtClass> {
  WeakHandle _object;
  ProtectionDomainEntry* volatile _next;
 public:

  ProtectionDomainEntry(WeakHandle obj,
                        ProtectionDomainEntry* head) : _object(obj), _next(head) {}

  ProtectionDomainEntry* next_acquire() { return Atomic::load_acquire(&_next); }
  void release_set_next(ProtectionDomainEntry* entry) { Atomic::release_store(&_next, entry); }
  oop object_no_keepalive();
};
#endif // SHARE_CLASSFILE_PROTECTIONDOMAINCACHE_HPP

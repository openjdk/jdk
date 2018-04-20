/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_PRIMS_RESOLVEDMETHOD_HPP
#define SHARE_VM_PRIMS_RESOLVEDMETHOD_HPP

#include "oops/symbol.hpp"
#include "oops/weakHandle.hpp"
#include "utilities/hashtable.hpp"

// Hashtable to record Method* used in ResolvedMethods, via. ResolvedMethod oops.
// This is needed for redefinition to replace Method* with redefined versions.

// Entry in a ResolvedMethodTable, mapping a ClassLoaderWeakHandle for a single oop of
// java_lang_invoke_ResolvedMethodName which holds JVM Method* in vmtarget.

class ResolvedMethodEntry : public HashtableEntry<ClassLoaderWeakHandle, mtClass> {
 public:
  ResolvedMethodEntry* next() const {
    return (ResolvedMethodEntry*)HashtableEntry<ClassLoaderWeakHandle, mtClass>::next();
  }

  ResolvedMethodEntry** next_addr() {
    return (ResolvedMethodEntry**)HashtableEntry<ClassLoaderWeakHandle, mtClass>::next_addr();
  }

  oop object();
  oop object_no_keepalive();

  void print_on(outputStream* st) const;
};

class ResolvedMethodTable : public Hashtable<ClassLoaderWeakHandle, mtClass> {
  enum Constants {
    _table_size  = 1007
  };

  static int _oops_removed;
  static int _oops_counted;

  static ResolvedMethodTable* _the_table;
private:
  ResolvedMethodEntry* bucket(int i) {
    return (ResolvedMethodEntry*) Hashtable<ClassLoaderWeakHandle, mtClass>::bucket(i);
  }

  ResolvedMethodEntry** bucket_addr(int i) {
    return (ResolvedMethodEntry**) Hashtable<ClassLoaderWeakHandle, mtClass>::bucket_addr(i);
  }

  unsigned int compute_hash(Method* method);

  // need not be locked; no state change
  oop lookup(int index, unsigned int hash, Method* method);
  oop lookup(Method* method);

  // must be done under ResolvedMethodTable_lock
  oop basic_add(Method* method, Handle rmethod_name);

public:
  ResolvedMethodTable();

  static void create_table() {
    assert(_the_table == NULL, "One symbol table allowed.");
    _the_table = new ResolvedMethodTable();
  }

  // Called from java_lang_invoke_ResolvedMethodName
  static oop find_method(Method* method);
  static oop add_method(Handle rmethod_name);

#if INCLUDE_JVMTI
  // It is called at safepoint only for RedefineClasses
  static void adjust_method_entries(bool * trace_name_printed);
#endif // INCLUDE_JVMTI

  // Cleanup cleared entries
  static void unlink();

#ifndef PRODUCT
  void print();
#endif
  void verify();
};

#endif // SHARE_VM_PRIMS_RESOLVEDMETHOD_HPP

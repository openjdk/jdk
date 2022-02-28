/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARED_CDS_CDSHEAPVERIFIER_HPP
#define SHARED_CDS_CDSHEAPVERIFIER_HPP

#include "cds/heapShared.hpp"
#include "memory/iterator.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

class InstanceKlass;
class Symbol;

#if INCLUDE_CDS_JAVA_HEAP

class CDSHeapVerifier : public KlassClosure {
  class CheckStaticFields;
  class TraceFields;

  int _archived_objs;
  int _problems;

  struct StaticFieldInfo {
    InstanceKlass* _holder;
    Symbol* _name;
  };

  ResourceHashtable<oop, StaticFieldInfo,
      15889, // prime number
      ResourceObj::C_HEAP,
      mtClassShared,
      HeapShared::oop_hash> _table;

  GrowableArray<const char**> _exclusions;

  void add_exclusion(const char** excl) {
    _exclusions.append(excl);
  }
  void add_static_obj_field(InstanceKlass* ik, oop field, Symbol* name);

  const char** find_exclusion(InstanceKlass* ik) {
    for (int i = 0; i < _exclusions.length(); i++) {
      const char** excl = _exclusions.at(i);
      if (ik->name()->equals(excl[0])) {
        return &excl[1];
      }
    }
    return NULL;
  }
  int trace_to_root(oop orig_obj, oop orig_field, HeapShared::CachedOopInfo* p);

  CDSHeapVerifier();
  ~CDSHeapVerifier();

public:

  // Overrides KlassClosure::do_klass()
  virtual void do_klass(Klass* k);

  // For ResourceHashtable::iterate()
  inline bool do_entry(oop& orig_obj, HeapShared::CachedOopInfo& value);

  static void verify() NOT_DEBUG_RETURN;
};

#endif // INCLUDE_CDS_JAVA_HEAP
#endif // SHARED_CDS_CDSHEAPVERIFIER_HPP

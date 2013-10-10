/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "memory/cardTableRS.hpp"
#include "memory/genRemSet.hpp"

// This kind of "BarrierSet" allows a "CollectedHeap" to detect and
// enumerate ref fields that have been modified (since the last
// enumeration.)

uintx GenRemSet::max_alignment_constraint(Name nm) {
  assert(nm == GenRemSet::CardTable, "Unrecognized GenRemSet type.");
  return CardTableRS::ct_max_alignment_constraint();
}

class HasAccumulatedModifiedOopsClosure : public KlassClosure {
  bool _found;
 public:
  HasAccumulatedModifiedOopsClosure() : _found(false) {}
  void do_klass(Klass* klass) {
    if (_found) {
      return;
    }

    if (klass->has_accumulated_modified_oops()) {
      _found = true;
    }
  }
  bool found() {
    return _found;
  }
};

bool KlassRemSet::mod_union_is_clear() {
  HasAccumulatedModifiedOopsClosure closure;
  ClassLoaderDataGraph::classes_do(&closure);

  return !closure.found();
}


class ClearKlassModUnionClosure : public KlassClosure {
 public:
  void do_klass(Klass* klass) {
    if (klass->has_accumulated_modified_oops()) {
      klass->clear_accumulated_modified_oops();
    }
  }
};

void KlassRemSet::clear_mod_union() {
  ClearKlassModUnionClosure closure;
  ClassLoaderDataGraph::classes_do(&closure);
}

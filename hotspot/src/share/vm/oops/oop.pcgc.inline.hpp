/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OOP_PCGC_INLINE_HPP
#define SHARE_VM_OOPS_OOP_PCGC_INLINE_HPP

#include "runtime/atomic.inline.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/parNew/parNewGeneration.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/psCompactionManager.hpp"
#include "gc_implementation/parallelScavenge/psParallelCompact.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"
#endif // INCLUDE_ALL_GCS

inline void oopDesc::update_contents(ParCompactionManager* cm) {
  // The klass field must be updated before anything else
  // can be done.
  DEBUG_ONLY(Klass* original_klass = klass());

  Klass* new_klass = klass();
  if (!new_klass->oop_is_typeArray()) {
    // It might contain oops beyond the header, so take the virtual call.
    new_klass->oop_update_pointers(cm, this);
  }
  // Else skip it.  The TypeArrayKlass in the header never needs scavenging.
}

inline void oopDesc::follow_contents(ParCompactionManager* cm) {
  assert (PSParallelCompact::mark_bitmap()->is_marked(this),
    "should be marked");
  klass()->oop_follow_contents(cm, this);
}

#endif // SHARE_VM_OOPS_OOP_PCGC_INLINE_HPP

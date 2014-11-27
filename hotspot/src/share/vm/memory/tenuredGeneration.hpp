/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_TENUREDGENERATION_HPP
#define SHARE_VM_MEMORY_TENUREDGENERATION_HPP

#include "gc_implementation/shared/cSpaceCounters.hpp"
#include "gc_implementation/shared/gcStats.hpp"
#include "gc_implementation/shared/generationCounters.hpp"
#include "memory/generation.hpp"
#include "utilities/macros.hpp"

// TenuredGeneration models the heap containing old (promoted/tenured) objects.

class TenuredGeneration: public OneContigSpaceCardGeneration {
  friend class VMStructs;
 protected:
  GenerationCounters*   _gen_counters;
  CSpaceCounters*       _space_counters;

 public:
  TenuredGeneration(ReservedSpace rs, size_t initial_byte_size, int level,
                    GenRemSet* remset);

  Generation::Name kind() { return Generation::MarkSweepCompact; }

  // Printing
  const char* name() const { return "tenured generation"; }
  const char* short_name() const { return "Tenured"; }

  // Does a "full" (forced) collection invoked on this generation collect
  // all younger generations as well? Note that this is a
  // hack to allow the collection of the younger gen first if the flag is
  // set.
  virtual bool full_collects_younger_generations() const {
    return !ScavengeBeforeFullGC;
  }

  virtual void gc_prologue(bool full);
  bool should_collect(bool   full,
                      size_t word_size,
                      bool   is_tlab);

  virtual void compute_new_size();

  // Performance Counter support
  void update_counters();

  // Statistics

  virtual void update_gc_stats(int level, bool full);

  virtual bool promotion_attempt_is_safe(size_t max_promoted_in_bytes) const;
};

#endif // SHARE_VM_MEMORY_TENUREDGENERATION_HPP

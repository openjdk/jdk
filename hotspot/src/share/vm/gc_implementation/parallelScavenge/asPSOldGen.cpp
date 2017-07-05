/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/parallelScavenge/asPSOldGen.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/psAdaptiveSizePolicy.hpp"
#include "gc_implementation/parallelScavenge/psMarkSweepDecorator.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"

// Whereas PSOldGen takes the maximum size of the generation
// (which doesn't change in the case of PSOldGen) as a parameter,
// ASPSOldGen takes the upper limit on the size of
// the generation as a parameter.  In ASPSOldGen the
// maximum size of the generation can change as the boundary
// moves.  The "maximum size of the generation" is still a valid
// concept since the generation can grow and shrink within that
// maximum.  There are lots of useful checks that use that
// maximum.  In PSOldGen the method max_gen_size() returns
// _max_gen_size (as set by the PSOldGen constructor).  This
// is how it always worked.  In ASPSOldGen max_gen_size()
// returned the size of the reserved space for the generation.
// That can change as the boundary moves.  Below the limit of
// the size of the generation is passed to the PSOldGen constructor
// for "_max_gen_size" (have to pass something) but it is not used later.
//
ASPSOldGen::ASPSOldGen(size_t initial_size,
                       size_t min_size,
                       size_t size_limit,
                       const char* gen_name,
                       int level) :
  PSOldGen(initial_size, min_size, size_limit, gen_name, level),
  _gen_size_limit(size_limit)
{}

ASPSOldGen::ASPSOldGen(PSVirtualSpace* vs,
                       size_t initial_size,
                       size_t min_size,
                       size_t size_limit,
                       const char* gen_name,
                       int level) :
  PSOldGen(initial_size, min_size, size_limit, gen_name, level),
  _gen_size_limit(size_limit)
{
  _virtual_space = vs;
}

void ASPSOldGen::initialize_work(const char* perf_data_name, int level) {
  PSOldGen::initialize_work(perf_data_name, level);

  // The old gen can grow to gen_size_limit().  _reserve reflects only
  // the current maximum that can be committed.
  assert(_reserved.byte_size() <= gen_size_limit(), "Consistency check");

  initialize_performance_counters(perf_data_name, level);
}

void ASPSOldGen::reset_after_change() {
  _reserved = MemRegion((HeapWord*)virtual_space()->low_boundary(),
                        (HeapWord*)virtual_space()->high_boundary());
  post_resize();
}


size_t ASPSOldGen::available_for_expansion() {
  assert(virtual_space()->is_aligned(gen_size_limit()), "not aligned");
  assert(gen_size_limit() >= virtual_space()->committed_size(), "bad gen size");

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  size_t result =  gen_size_limit() - virtual_space()->committed_size();
  size_t result_aligned = align_size_down(result, heap->generation_alignment());
  return result_aligned;
}

size_t ASPSOldGen::available_for_contraction() {
  size_t uncommitted_bytes = virtual_space()->uncommitted_size();
  if (uncommitted_bytes != 0) {
    return uncommitted_bytes;
  }

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  const size_t gen_alignment = heap->generation_alignment();
  PSAdaptiveSizePolicy* policy = heap->size_policy();
  const size_t working_size =
    used_in_bytes() + (size_t) policy->avg_promoted()->padded_average();
  const size_t working_aligned = align_size_up(working_size, gen_alignment);
  const size_t working_or_min = MAX2(working_aligned, min_gen_size());
  if (working_or_min > reserved().byte_size()) {
    // If the used or minimum gen size (aligned up) is greater
    // than the total reserved size, then the space available
    // for contraction should (after proper alignment) be 0
    return 0;
  }
  const size_t max_contraction =
    reserved().byte_size() - working_or_min;

  // Use the "increment" fraction instead of the "decrement" fraction
  // to allow the other gen to expand more aggressively.  The
  // "decrement" fraction is conservative because its intent is to
  // only reduce the footprint.

  size_t result = policy->promo_increment_aligned_down(max_contraction);
  // Also adjust for inter-generational alignment
  size_t result_aligned = align_size_down(result, gen_alignment);
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("\nASPSOldGen::available_for_contraction:"
      " %d K / 0x%x", result_aligned/K, result_aligned);
    gclog_or_tty->print_cr(" reserved().byte_size() %d K / 0x%x ",
      reserved().byte_size()/K, reserved().byte_size());
    size_t working_promoted = (size_t) policy->avg_promoted()->padded_average();
    gclog_or_tty->print_cr(" padded promoted %d K / 0x%x",
      working_promoted/K, working_promoted);
    gclog_or_tty->print_cr(" used %d K / 0x%x",
      used_in_bytes()/K, used_in_bytes());
    gclog_or_tty->print_cr(" min_gen_size() %d K / 0x%x",
      min_gen_size()/K, min_gen_size());
    gclog_or_tty->print_cr(" max_contraction %d K / 0x%x",
      max_contraction/K, max_contraction);
    gclog_or_tty->print_cr("    without alignment %d K / 0x%x",
      policy->promo_increment(max_contraction)/K,
      policy->promo_increment(max_contraction));
    gclog_or_tty->print_cr(" alignment 0x%x", gen_alignment);
  }
  assert(result_aligned <= max_contraction, "arithmetic is wrong");
  return result_aligned;
}

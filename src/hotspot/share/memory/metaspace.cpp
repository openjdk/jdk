/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "aot/aotLoader.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/filemap.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/printCLDMetaspaceInfoClosure.hpp"
#include "memory/metaspace/spaceManager.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/metaspaceTracer.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "runtime/atomic.hpp"
#include "runtime/init.hpp"
#include "services/memTracker.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/vmError.hpp"


using namespace metaspace;

MetaWord* last_allocated = 0;

size_t Metaspace::_compressed_class_space_size;
const MetaspaceTracer* Metaspace::_tracer = NULL;

DEBUG_ONLY(bool Metaspace::_frozen = false;)

static const char* space_type_name(Metaspace::MetaspaceType t) {
  const char* s = NULL;
  switch (t) {
    case Metaspace::StandardMetaspaceType: s = "Standard"; break;
    case Metaspace::BootMetaspaceType: s = "Boot"; break;
    case Metaspace::ClassMirrorHolderMetaspaceType: s = "ClassMirrorHolder"; break;
    case Metaspace::ReflectionMetaspaceType: s = "Reflection"; break;
    default: ShouldNotReachHere();
  }
  return s;
}

volatile size_t MetaspaceGC::_capacity_until_GC = 0;
uint MetaspaceGC::_shrink_factor = 0;

// BlockFreelist methods

// VirtualSpaceNode methods

// MetaspaceGC methods

// VM_CollectForMetadataAllocation is the vm operation used to GC.
// Within the VM operation after the GC the attempt to allocate the metadata
// should succeed.  If the GC did not free enough space for the metaspace
// allocation, the HWM is increased so that another virtualspace will be
// allocated for the metadata.  With perm gen the increase in the perm
// gen had bounds, MinMetaspaceExpansion and MaxMetaspaceExpansion.  The
// metaspace policy uses those as the small and large steps for the HWM.
//
// After the GC the compute_new_size() for MetaspaceGC is called to
// resize the capacity of the metaspaces.  The current implementation
// is based on the flags MinMetaspaceFreeRatio and MaxMetaspaceFreeRatio used
// to resize the Java heap by some GC's.  New flags can be implemented
// if really needed.  MinMetaspaceFreeRatio is used to calculate how much
// free space is desirable in the metaspace capacity to decide how much
// to increase the HWM.  MaxMetaspaceFreeRatio is used to decide how much
// free space is desirable in the metaspace capacity before decreasing
// the HWM.

// Calculate the amount to increase the high water mark (HWM).
// Increase by a minimum amount (MinMetaspaceExpansion) so that
// another expansion is not requested too soon.  If that is not
// enough to satisfy the allocation, increase by MaxMetaspaceExpansion.
// If that is still not enough, expand by the size of the allocation
// plus some.
size_t MetaspaceGC::delta_capacity_until_GC(size_t bytes) {
  size_t min_delta = MinMetaspaceExpansion;
  size_t max_delta = MaxMetaspaceExpansion;
  size_t delta = align_up(bytes, Metaspace::commit_alignment());

  if (delta <= min_delta) {
    delta = min_delta;
  } else if (delta <= max_delta) {
    // Don't want to hit the high water mark on the next
    // allocation so make the delta greater than just enough
    // for this allocation.
    delta = max_delta;
  } else {
    // This allocation is large but the next ones are probably not
    // so increase by the minimum.
    delta = delta + min_delta;
  }

  assert_is_aligned(delta, Metaspace::commit_alignment());

  return delta;
}

size_t MetaspaceGC::capacity_until_GC() {
  size_t value = Atomic::load_acquire(&_capacity_until_GC);
  assert(value >= MetaspaceSize, "Not initialized properly?");
  return value;
}

// Try to increase the _capacity_until_GC limit counter by v bytes.
// Returns true if it succeeded. It may fail if either another thread
// concurrently increased the limit or the new limit would be larger
// than MaxMetaspaceSize.
// On success, optionally returns new and old metaspace capacity in
// new_cap_until_GC and old_cap_until_GC respectively.
// On error, optionally sets can_retry to indicate whether if there is
// actually enough space remaining to satisfy the request.
bool MetaspaceGC::inc_capacity_until_GC(size_t v, size_t* new_cap_until_GC, size_t* old_cap_until_GC, bool* can_retry) {
  assert_is_aligned(v, Metaspace::commit_alignment());

  size_t old_capacity_until_GC = _capacity_until_GC;
  size_t new_value = old_capacity_until_GC + v;

  if (new_value < old_capacity_until_GC) {
    // The addition wrapped around, set new_value to aligned max value.
    new_value = align_down(max_uintx, Metaspace::commit_alignment());
  }

  if (new_value > MaxMetaspaceSize) {
    if (can_retry != NULL) {
      *can_retry = false;
    }
    return false;
  }

  if (can_retry != NULL) {
    *can_retry = true;
  }
  size_t prev_value = Atomic::cmpxchg(&_capacity_until_GC, old_capacity_until_GC, new_value);

  if (old_capacity_until_GC != prev_value) {
    return false;
  }

  if (new_cap_until_GC != NULL) {
    *new_cap_until_GC = new_value;
  }
  if (old_cap_until_GC != NULL) {
    *old_cap_until_GC = old_capacity_until_GC;
  }
  return true;
}

size_t MetaspaceGC::dec_capacity_until_GC(size_t v) {
  assert_is_aligned(v, Metaspace::commit_alignment());

  return Atomic::sub(&_capacity_until_GC, v);
}

void MetaspaceGC::initialize() {
  // Set the high-water mark to MaxMetapaceSize during VM initializaton since
  // we can't do a GC during initialization.
  _capacity_until_GC = MaxMetaspaceSize;
}

void MetaspaceGC::post_initialize() {
  // Reset the high-water mark once the VM initialization is done.
  _capacity_until_GC = MAX2(MetaspaceUtils::committed_bytes(), MetaspaceSize);
}

bool MetaspaceGC::can_expand(size_t word_size, bool is_class) {
  // Check if the compressed class space is full.
  if (is_class && Metaspace::using_class_space()) {
    size_t class_committed = MetaspaceUtils::committed_bytes(Metaspace::ClassType);
    if (class_committed + word_size * BytesPerWord > CompressedClassSpaceSize) {
      log_trace(gc, metaspace, freelist)("Cannot expand %s metaspace by " SIZE_FORMAT " words (CompressedClassSpaceSize = " SIZE_FORMAT " words)",
                (is_class ? "class" : "non-class"), word_size, CompressedClassSpaceSize / sizeof(MetaWord));
      return false;
    }
  }

  // Check if the user has imposed a limit on the metaspace memory.
  size_t committed_bytes = MetaspaceUtils::committed_bytes();
  if (committed_bytes + word_size * BytesPerWord > MaxMetaspaceSize) {
    log_trace(gc, metaspace, freelist)("Cannot expand %s metaspace by " SIZE_FORMAT " words (MaxMetaspaceSize = " SIZE_FORMAT " words)",
              (is_class ? "class" : "non-class"), word_size, MaxMetaspaceSize / sizeof(MetaWord));
    return false;
  }

  return true;
}

size_t MetaspaceGC::allowed_expansion() {
  size_t committed_bytes = MetaspaceUtils::committed_bytes();
  size_t capacity_until_gc = capacity_until_GC();

  assert(capacity_until_gc >= committed_bytes,
         "capacity_until_gc: " SIZE_FORMAT " < committed_bytes: " SIZE_FORMAT,
         capacity_until_gc, committed_bytes);

  size_t left_until_max  = MaxMetaspaceSize - committed_bytes;
  size_t left_until_GC = capacity_until_gc - committed_bytes;
  size_t left_to_commit = MIN2(left_until_GC, left_until_max);
  log_trace(gc, metaspace, freelist)("allowed expansion words: " SIZE_FORMAT
            " (left_until_max: " SIZE_FORMAT ", left_until_GC: " SIZE_FORMAT ".",
            left_to_commit / BytesPerWord, left_until_max / BytesPerWord, left_until_GC / BytesPerWord);

  return left_to_commit / BytesPerWord;
}

void MetaspaceGC::compute_new_size() {
  assert(_shrink_factor <= 100, "invalid shrink factor");
  uint current_shrink_factor = _shrink_factor;
  _shrink_factor = 0;

  // Using committed_bytes() for used_after_gc is an overestimation, since the
  // chunk free lists are included in committed_bytes() and the memory in an
  // un-fragmented chunk free list is available for future allocations.
  // However, if the chunk free lists becomes fragmented, then the memory may
  // not be available for future allocations and the memory is therefore "in use".
  // Including the chunk free lists in the definition of "in use" is therefore
  // necessary. Not including the chunk free lists can cause capacity_until_GC to
  // shrink below committed_bytes() and this has caused serious bugs in the past.
  const size_t used_after_gc = MetaspaceUtils::committed_bytes();
  const size_t capacity_until_GC = MetaspaceGC::capacity_until_GC();

  const double minimum_free_percentage = MinMetaspaceFreeRatio / 100.0;
  const double maximum_used_percentage = 1.0 - minimum_free_percentage;

  const double min_tmp = used_after_gc / maximum_used_percentage;
  size_t minimum_desired_capacity =
    (size_t)MIN2(min_tmp, double(MaxMetaspaceSize));
  // Don't shrink less than the initial generation size
  minimum_desired_capacity = MAX2(minimum_desired_capacity,
                                  MetaspaceSize);

  log_trace(gc, metaspace)("MetaspaceGC::compute_new_size: ");
  log_trace(gc, metaspace)("    minimum_free_percentage: %6.2f  maximum_used_percentage: %6.2f",
                           minimum_free_percentage, maximum_used_percentage);
  log_trace(gc, metaspace)("     used_after_gc       : %6.1fKB", used_after_gc / (double) K);


  size_t shrink_bytes = 0;
  if (capacity_until_GC < minimum_desired_capacity) {
    // If we have less capacity below the metaspace HWM, then
    // increment the HWM.
    size_t expand_bytes = minimum_desired_capacity - capacity_until_GC;
    expand_bytes = align_up(expand_bytes, Metaspace::commit_alignment());
    // Don't expand unless it's significant
    if (expand_bytes >= MinMetaspaceExpansion) {
      size_t new_capacity_until_GC = 0;
      bool succeeded = MetaspaceGC::inc_capacity_until_GC(expand_bytes, &new_capacity_until_GC);
      assert(succeeded, "Should always succesfully increment HWM when at safepoint");

      Metaspace::tracer()->report_gc_threshold(capacity_until_GC,
                                               new_capacity_until_GC,
                                               MetaspaceGCThresholdUpdater::ComputeNewSize);
      log_trace(gc, metaspace)("    expanding:  minimum_desired_capacity: %6.1fKB  expand_bytes: %6.1fKB  MinMetaspaceExpansion: %6.1fKB  new metaspace HWM:  %6.1fKB",
                               minimum_desired_capacity / (double) K,
                               expand_bytes / (double) K,
                               MinMetaspaceExpansion / (double) K,
                               new_capacity_until_GC / (double) K);
    }
    return;
  }

  // No expansion, now see if we want to shrink
  // We would never want to shrink more than this
  assert(capacity_until_GC >= minimum_desired_capacity,
         SIZE_FORMAT " >= " SIZE_FORMAT,
         capacity_until_GC, minimum_desired_capacity);
  size_t max_shrink_bytes = capacity_until_GC - minimum_desired_capacity;

  // Should shrinking be considered?
  if (MaxMetaspaceFreeRatio < 100) {
    const double maximum_free_percentage = MaxMetaspaceFreeRatio / 100.0;
    const double minimum_used_percentage = 1.0 - maximum_free_percentage;
    const double max_tmp = used_after_gc / minimum_used_percentage;
    size_t maximum_desired_capacity = (size_t)MIN2(max_tmp, double(MaxMetaspaceSize));
    maximum_desired_capacity = MAX2(maximum_desired_capacity,
                                    MetaspaceSize);
    log_trace(gc, metaspace)("    maximum_free_percentage: %6.2f  minimum_used_percentage: %6.2f",
                             maximum_free_percentage, minimum_used_percentage);
    log_trace(gc, metaspace)("    minimum_desired_capacity: %6.1fKB  maximum_desired_capacity: %6.1fKB",
                             minimum_desired_capacity / (double) K, maximum_desired_capacity / (double) K);

    assert(minimum_desired_capacity <= maximum_desired_capacity,
           "sanity check");

    if (capacity_until_GC > maximum_desired_capacity) {
      // Capacity too large, compute shrinking size
      shrink_bytes = capacity_until_GC - maximum_desired_capacity;
      // We don't want shrink all the way back to initSize if people call
      // System.gc(), because some programs do that between "phases" and then
      // we'd just have to grow the heap up again for the next phase.  So we
      // damp the shrinking: 0% on the first call, 10% on the second call, 40%
      // on the third call, and 100% by the fourth call.  But if we recompute
      // size without shrinking, it goes back to 0%.
      shrink_bytes = shrink_bytes / 100 * current_shrink_factor;

      shrink_bytes = align_down(shrink_bytes, Metaspace::commit_alignment());

      assert(shrink_bytes <= max_shrink_bytes,
             "invalid shrink size " SIZE_FORMAT " not <= " SIZE_FORMAT,
             shrink_bytes, max_shrink_bytes);
      if (current_shrink_factor == 0) {
        _shrink_factor = 10;
      } else {
        _shrink_factor = MIN2(current_shrink_factor * 4, (uint) 100);
      }
      log_trace(gc, metaspace)("    shrinking:  initThreshold: %.1fK  maximum_desired_capacity: %.1fK",
                               MetaspaceSize / (double) K, maximum_desired_capacity / (double) K);
      log_trace(gc, metaspace)("    shrink_bytes: %.1fK  current_shrink_factor: %d  new shrink factor: %d  MinMetaspaceExpansion: %.1fK",
                               shrink_bytes / (double) K, current_shrink_factor, _shrink_factor, MinMetaspaceExpansion / (double) K);
    }
  }

  // Don't shrink unless it's significant
  if (shrink_bytes >= MinMetaspaceExpansion &&
      ((capacity_until_GC - shrink_bytes) >= MetaspaceSize)) {
    size_t new_capacity_until_GC = MetaspaceGC::dec_capacity_until_GC(shrink_bytes);
    Metaspace::tracer()->report_gc_threshold(capacity_until_GC,
                                             new_capacity_until_GC,
                                             MetaspaceGCThresholdUpdater::ComputeNewSize);
  }
}

// MetaspaceUtils
size_t MetaspaceUtils::_capacity_words [Metaspace:: MetadataTypeCount] = {0, 0};
size_t MetaspaceUtils::_overhead_words [Metaspace:: MetadataTypeCount] = {0, 0};
volatile size_t MetaspaceUtils::_used_words [Metaspace:: MetadataTypeCount] = {0, 0};

// Collect used metaspace statistics. This involves walking the CLDG. The resulting
// output will be the accumulated values for all live metaspaces.
// Note: method does not do any locking.
void MetaspaceUtils::collect_statistics(ClassLoaderMetaspaceStatistics* out) {
  out->reset();
  ClassLoaderDataGraphMetaspaceIterator iter;
   while (iter.repeat()) {
     ClassLoaderMetaspace* msp = iter.get_next();
     if (msp != NULL) {
       msp->add_to_statistics(out);
     }
   }
}

size_t MetaspaceUtils::free_in_vs_bytes(Metaspace::MetadataType mdtype) {
  VirtualSpaceList* list = Metaspace::get_space_list(mdtype);
  return list == NULL ? 0 : list->free_bytes();
}

size_t MetaspaceUtils::free_in_vs_bytes() {
  return free_in_vs_bytes(Metaspace::ClassType) + free_in_vs_bytes(Metaspace::NonClassType);
}

static void inc_stat_nonatomically(size_t* pstat, size_t words) {
  assert_lock_strong(MetaspaceExpand_lock);
  (*pstat) += words;
}

static void dec_stat_nonatomically(size_t* pstat, size_t words) {
  assert_lock_strong(MetaspaceExpand_lock);
  const size_t size_now = *pstat;
  assert(size_now >= words, "About to decrement counter below zero "
         "(current value: " SIZE_FORMAT ", decrement value: " SIZE_FORMAT ".",
         size_now, words);
  *pstat = size_now - words;
}

static void inc_stat_atomically(volatile size_t* pstat, size_t words) {
  Atomic::add(pstat, words);
}

static void dec_stat_atomically(volatile size_t* pstat, size_t words) {
  const size_t size_now = *pstat;
  assert(size_now >= words, "About to decrement counter below zero "
         "(current value: " SIZE_FORMAT ", decrement value: " SIZE_FORMAT ".",
         size_now, words);
  Atomic::sub(pstat, words);
}

void MetaspaceUtils::dec_capacity(Metaspace::MetadataType mdtype, size_t words) {
  dec_stat_nonatomically(&_capacity_words[mdtype], words);
}
void MetaspaceUtils::inc_capacity(Metaspace::MetadataType mdtype, size_t words) {
  inc_stat_nonatomically(&_capacity_words[mdtype], words);
}
void MetaspaceUtils::dec_used(Metaspace::MetadataType mdtype, size_t words) {
  dec_stat_atomically(&_used_words[mdtype], words);
}
void MetaspaceUtils::inc_used(Metaspace::MetadataType mdtype, size_t words) {
  inc_stat_atomically(&_used_words[mdtype], words);
}
void MetaspaceUtils::dec_overhead(Metaspace::MetadataType mdtype, size_t words) {
  dec_stat_nonatomically(&_overhead_words[mdtype], words);
}
void MetaspaceUtils::inc_overhead(Metaspace::MetadataType mdtype, size_t words) {
  inc_stat_nonatomically(&_overhead_words[mdtype], words);
}

size_t MetaspaceUtils::reserved_bytes(Metaspace::MetadataType mdtype) {
  VirtualSpaceList* list = Metaspace::get_space_list(mdtype);
  return list == NULL ? 0 : list->reserved_bytes();
}

size_t MetaspaceUtils::committed_bytes(Metaspace::MetadataType mdtype) {
  VirtualSpaceList* list = Metaspace::get_space_list(mdtype);
  return list == NULL ? 0 : list->committed_bytes();
}

size_t MetaspaceUtils::min_chunk_size_words() { return Metaspace::first_chunk_word_size(); }

size_t MetaspaceUtils::free_chunks_total_words(Metaspace::MetadataType mdtype) {
  ChunkManager* chunk_manager = Metaspace::get_chunk_manager(mdtype);
  if (chunk_manager == NULL) {
    return 0;
  }
  return chunk_manager->free_chunks_total_words();
}

size_t MetaspaceUtils::free_chunks_total_bytes(Metaspace::MetadataType mdtype) {
  return free_chunks_total_words(mdtype) * BytesPerWord;
}

size_t MetaspaceUtils::free_chunks_total_words() {
  return free_chunks_total_words(Metaspace::ClassType) +
         free_chunks_total_words(Metaspace::NonClassType);
}

size_t MetaspaceUtils::free_chunks_total_bytes() {
  return free_chunks_total_words() * BytesPerWord;
}

bool MetaspaceUtils::has_chunk_free_list(Metaspace::MetadataType mdtype) {
  return Metaspace::get_chunk_manager(mdtype) != NULL;
}

MetaspaceChunkFreeListSummary MetaspaceUtils::chunk_free_list_summary(Metaspace::MetadataType mdtype) {
  if (!has_chunk_free_list(mdtype)) {
    return MetaspaceChunkFreeListSummary();
  }

  const ChunkManager* cm = Metaspace::get_chunk_manager(mdtype);
  return cm->chunk_free_list_summary();
}

void MetaspaceUtils::print_metaspace_change(const metaspace::MetaspaceSizesSnapshot& pre_meta_values) {
  const metaspace::MetaspaceSizesSnapshot meta_values;

  if (Metaspace::using_class_space()) {
    log_info(gc, metaspace)(HEAP_CHANGE_FORMAT" "
                            HEAP_CHANGE_FORMAT" "
                            HEAP_CHANGE_FORMAT,
                            HEAP_CHANGE_FORMAT_ARGS("Metaspace",
                                                    pre_meta_values.used(),
                                                    pre_meta_values.committed(),
                                                    meta_values.used(),
                                                    meta_values.committed()),
                            HEAP_CHANGE_FORMAT_ARGS("NonClass",
                                                    pre_meta_values.non_class_used(),
                                                    pre_meta_values.non_class_committed(),
                                                    meta_values.non_class_used(),
                                                    meta_values.non_class_committed()),
                            HEAP_CHANGE_FORMAT_ARGS("Class",
                                                    pre_meta_values.class_used(),
                                                    pre_meta_values.class_committed(),
                                                    meta_values.class_used(),
                                                    meta_values.class_committed()));
  } else {
    log_info(gc, metaspace)(HEAP_CHANGE_FORMAT,
                            HEAP_CHANGE_FORMAT_ARGS("Metaspace",
                                                    pre_meta_values.used(),
                                                    pre_meta_values.committed(),
                                                    meta_values.used(),
                                                    meta_values.committed()));
  }
}

void MetaspaceUtils::print_on(outputStream* out) {
  Metaspace::MetadataType nct = Metaspace::NonClassType;

  out->print_cr(" Metaspace       "
                "used "      SIZE_FORMAT "K, "
                "capacity "  SIZE_FORMAT "K, "
                "committed " SIZE_FORMAT "K, "
                "reserved "  SIZE_FORMAT "K",
                used_bytes()/K,
                capacity_bytes()/K,
                committed_bytes()/K,
                reserved_bytes()/K);

  if (Metaspace::using_class_space()) {
    Metaspace::MetadataType ct = Metaspace::ClassType;
    out->print_cr("  class space    "
                  "used "      SIZE_FORMAT "K, "
                  "capacity "  SIZE_FORMAT "K, "
                  "committed " SIZE_FORMAT "K, "
                  "reserved "  SIZE_FORMAT "K",
                  used_bytes(ct)/K,
                  capacity_bytes(ct)/K,
                  committed_bytes(ct)/K,
                  reserved_bytes(ct)/K);
  }
}


void MetaspaceUtils::print_vs(outputStream* out, size_t scale) {
  const size_t reserved_nonclass_words = reserved_bytes(Metaspace::NonClassType) / sizeof(MetaWord);
  const size_t committed_nonclass_words = committed_bytes(Metaspace::NonClassType) / sizeof(MetaWord);
  {
    if (Metaspace::using_class_space()) {
      out->print("  Non-class space:  ");
    }
    print_scaled_words(out, reserved_nonclass_words, scale, 7);
    out->print(" reserved, ");
    print_scaled_words_and_percentage(out, committed_nonclass_words, reserved_nonclass_words, scale, 7);
    out->print_cr(" committed ");

    if (Metaspace::using_class_space()) {
      const size_t reserved_class_words = reserved_bytes(Metaspace::ClassType) / sizeof(MetaWord);
      const size_t committed_class_words = committed_bytes(Metaspace::ClassType) / sizeof(MetaWord);
      out->print("      Class space:  ");
      print_scaled_words(out, reserved_class_words, scale, 7);
      out->print(" reserved, ");
      print_scaled_words_and_percentage(out, committed_class_words, reserved_class_words, scale, 7);
      out->print_cr(" committed ");

      const size_t reserved_words = reserved_nonclass_words + reserved_class_words;
      const size_t committed_words = committed_nonclass_words + committed_class_words;
      out->print("             Both:  ");
      print_scaled_words(out, reserved_words, scale, 7);
      out->print(" reserved, ");
      print_scaled_words_and_percentage(out, committed_words, reserved_words, scale, 7);
      out->print_cr(" committed ");
    }
  }
}

static void print_basic_switches(outputStream* out, size_t scale) {
  out->print("MaxMetaspaceSize: ");
  if (MaxMetaspaceSize >= (max_uintx) - (2 * os::vm_page_size())) {
    // aka "very big". Default is max_uintx, but due to rounding in arg parsing the real
    // value is smaller.
    out->print("unlimited");
  } else {
    print_human_readable_size(out, MaxMetaspaceSize, scale);
  }
  out->cr();
  if (Metaspace::using_class_space()) {
    out->print("CompressedClassSpaceSize: ");
    print_human_readable_size(out, CompressedClassSpaceSize, scale);
  }
  out->cr();
}

// This will print out a basic metaspace usage report but
// unlike print_report() is guaranteed not to lock or to walk the CLDG.
void MetaspaceUtils::print_basic_report(outputStream* out, size_t scale) {

  if (!Metaspace::initialized()) {
    out->print_cr("Metaspace not yet initialized.");
    return;
  }

  out->cr();
  out->print_cr("Usage:");

  if (Metaspace::using_class_space()) {
    out->print("  Non-class:  ");
  }

  // In its most basic form, we do not require walking the CLDG. Instead, just print the running totals from
  // MetaspaceUtils.
  const size_t cap_nc = MetaspaceUtils::capacity_words(Metaspace::NonClassType);
  const size_t overhead_nc = MetaspaceUtils::overhead_words(Metaspace::NonClassType);
  const size_t used_nc = MetaspaceUtils::used_words(Metaspace::NonClassType);
  const size_t free_and_waste_nc = cap_nc - overhead_nc - used_nc;

  print_scaled_words(out, cap_nc, scale, 5);
  out->print(" capacity, ");
  print_scaled_words_and_percentage(out, used_nc, cap_nc, scale, 5);
  out->print(" used, ");
  print_scaled_words_and_percentage(out, free_and_waste_nc, cap_nc, scale, 5);
  out->print(" free+waste, ");
  print_scaled_words_and_percentage(out, overhead_nc, cap_nc, scale, 5);
  out->print(" overhead. ");
  out->cr();

  if (Metaspace::using_class_space()) {
    const size_t cap_c = MetaspaceUtils::capacity_words(Metaspace::ClassType);
    const size_t overhead_c = MetaspaceUtils::overhead_words(Metaspace::ClassType);
    const size_t used_c = MetaspaceUtils::used_words(Metaspace::ClassType);
    const size_t free_and_waste_c = cap_c - overhead_c - used_c;
    out->print("      Class:  ");
    print_scaled_words(out, cap_c, scale, 5);
    out->print(" capacity, ");
    print_scaled_words_and_percentage(out, used_c, cap_c, scale, 5);
    out->print(" used, ");
    print_scaled_words_and_percentage(out, free_and_waste_c, cap_c, scale, 5);
    out->print(" free+waste, ");
    print_scaled_words_and_percentage(out, overhead_c, cap_c, scale, 5);
    out->print(" overhead. ");
    out->cr();

    out->print("       Both:  ");
    const size_t cap = cap_nc + cap_c;

    print_scaled_words(out, cap, scale, 5);
    out->print(" capacity, ");
    print_scaled_words_and_percentage(out, used_nc + used_c, cap, scale, 5);
    out->print(" used, ");
    print_scaled_words_and_percentage(out, free_and_waste_nc + free_and_waste_c, cap, scale, 5);
    out->print(" free+waste, ");
    print_scaled_words_and_percentage(out, overhead_nc + overhead_c, cap, scale, 5);
    out->print(" overhead. ");
    out->cr();
  }

  out->cr();
  out->print_cr("Virtual space:");

  print_vs(out, scale);

  out->cr();
  out->print_cr("Chunk freelists:");

  if (Metaspace::using_class_space()) {
    out->print("   Non-Class:  ");
  }
  print_human_readable_size(out, Metaspace::chunk_manager_metadata()->free_chunks_total_bytes(), scale);
  out->cr();
  if (Metaspace::using_class_space()) {
    out->print("       Class:  ");
    print_human_readable_size(out, Metaspace::chunk_manager_class()->free_chunks_total_bytes(), scale);
    out->cr();
    out->print("        Both:  ");
    print_human_readable_size(out, Metaspace::chunk_manager_class()->free_chunks_total_bytes() +
                              Metaspace::chunk_manager_metadata()->free_chunks_total_bytes(), scale);
    out->cr();
  }

  out->cr();

  // Print basic settings
  print_basic_switches(out, scale);

  out->cr();

}

void MetaspaceUtils::print_report(outputStream* out, size_t scale, int flags) {

  if (!Metaspace::initialized()) {
    out->print_cr("Metaspace not yet initialized.");
    return;
  }

  const bool print_loaders = (flags & rf_show_loaders) > 0;
  const bool print_classes = (flags & rf_show_classes) > 0;
  const bool print_by_chunktype = (flags & rf_break_down_by_chunktype) > 0;
  const bool print_by_spacetype = (flags & rf_break_down_by_spacetype) > 0;

  // Some report options require walking the class loader data graph.
  PrintCLDMetaspaceInfoClosure cl(out, scale, print_loaders, print_classes, print_by_chunktype);
  if (print_loaders) {
    out->cr();
    out->print_cr("Usage per loader:");
    out->cr();
  }

  ClassLoaderDataGraph::loaded_cld_do(&cl); // collect data and optionally print

  // Print totals, broken up by space type.
  if (print_by_spacetype) {
    out->cr();
    out->print_cr("Usage per space type:");
    out->cr();
    for (int space_type = (int)Metaspace::ZeroMetaspaceType;
         space_type < (int)Metaspace::MetaspaceTypeCount; space_type ++)
    {
      uintx num_loaders = cl._num_loaders_by_spacetype[space_type];
      uintx num_classes = cl._num_classes_by_spacetype[space_type];
      out->print("%s - " UINTX_FORMAT " %s",
        space_type_name((Metaspace::MetaspaceType)space_type),
        num_loaders, loaders_plural(num_loaders));
      if (num_classes > 0) {
        out->print(", ");
        print_number_of_classes(out, num_classes, cl._num_classes_shared_by_spacetype[space_type]);
        out->print(":");
        cl._stats_by_spacetype[space_type].print_on(out, scale, print_by_chunktype);
      } else {
        out->print(".");
        out->cr();
      }
      out->cr();
    }
  }

  // Print totals for in-use data:
  out->cr();
  {
    uintx num_loaders = cl._num_loaders;
    out->print("Total Usage - " UINTX_FORMAT " %s, ",
      num_loaders, loaders_plural(num_loaders));
    print_number_of_classes(out, cl._num_classes, cl._num_classes_shared);
    out->print(":");
    cl._stats_total.print_on(out, scale, print_by_chunktype);
    out->cr();
  }

  // -- Print Virtual space.
  out->cr();
  out->print_cr("Virtual space:");

  print_vs(out, scale);

  // -- Print VirtualSpaceList details.
  if ((flags & rf_show_vslist) > 0) {
    out->cr();
    out->print_cr("Virtual space list%s:", Metaspace::using_class_space() ? "s" : "");

    if (Metaspace::using_class_space()) {
      out->print_cr("   Non-Class:");
    }
    Metaspace::space_list()->print_on(out, scale);
    if (Metaspace::using_class_space()) {
      out->print_cr("       Class:");
      Metaspace::class_space_list()->print_on(out, scale);
    }
  }
  out->cr();

  // -- Print VirtualSpaceList map.
  if ((flags & rf_show_vsmap) > 0) {
    out->cr();
    out->print_cr("Virtual space map:");

    if (Metaspace::using_class_space()) {
      out->print_cr("   Non-Class:");
    }
    Metaspace::space_list()->print_map(out);
    if (Metaspace::using_class_space()) {
      out->print_cr("       Class:");
      Metaspace::class_space_list()->print_map(out);
    }
  }
  out->cr();

  // -- Print Freelists (ChunkManager) details
  out->cr();
  out->print_cr("Chunk freelist%s:", Metaspace::using_class_space() ? "s" : "");

  ChunkManagerStatistics non_class_cm_stat;
  Metaspace::chunk_manager_metadata()->collect_statistics(&non_class_cm_stat);

  if (Metaspace::using_class_space()) {
    out->print_cr("   Non-Class:");
  }
  non_class_cm_stat.print_on(out, scale);

  if (Metaspace::using_class_space()) {
    ChunkManagerStatistics class_cm_stat;
    Metaspace::chunk_manager_class()->collect_statistics(&class_cm_stat);
    out->print_cr("       Class:");
    class_cm_stat.print_on(out, scale);
  }

  // As a convenience, print a summary of common waste.
  out->cr();
  out->print("Waste ");
  // For all wastages, print percentages from total. As total use the total size of memory committed for metaspace.
  const size_t committed_words = committed_bytes() / BytesPerWord;

  out->print("(percentages refer to total committed size ");
  print_scaled_words(out, committed_words, scale);
  out->print_cr("):");

  // Print space committed but not yet used by any class loader
  const size_t unused_words_in_vs = MetaspaceUtils::free_in_vs_bytes() / BytesPerWord;
  out->print("              Committed unused: ");
  print_scaled_words_and_percentage(out, unused_words_in_vs, committed_words, scale, 6);
  out->cr();

  // Print waste for in-use chunks.
  UsedChunksStatistics ucs_nonclass = cl._stats_total.nonclass_sm_stats().totals();
  UsedChunksStatistics ucs_class = cl._stats_total.class_sm_stats().totals();
  UsedChunksStatistics ucs_all;
  ucs_all.add(ucs_nonclass);
  ucs_all.add(ucs_class);

  out->print("        Waste in chunks in use: ");
  print_scaled_words_and_percentage(out, ucs_all.waste(), committed_words, scale, 6);
  out->cr();
  out->print("         Free in chunks in use: ");
  print_scaled_words_and_percentage(out, ucs_all.free(), committed_words, scale, 6);
  out->cr();
  out->print("     Overhead in chunks in use: ");
  print_scaled_words_and_percentage(out, ucs_all.overhead(), committed_words, scale, 6);
  out->cr();

  // Print waste in free chunks.
  const size_t total_capacity_in_free_chunks =
      Metaspace::chunk_manager_metadata()->free_chunks_total_words() +
     (Metaspace::using_class_space() ? Metaspace::chunk_manager_class()->free_chunks_total_words() : 0);
  out->print("                In free chunks: ");
  print_scaled_words_and_percentage(out, total_capacity_in_free_chunks, committed_words, scale, 6);
  out->cr();

  // Print waste in deallocated blocks.
  const uintx free_blocks_num =
      cl._stats_total.nonclass_sm_stats().free_blocks_num() +
      cl._stats_total.class_sm_stats().free_blocks_num();
  const size_t free_blocks_cap_words =
      cl._stats_total.nonclass_sm_stats().free_blocks_cap_words() +
      cl._stats_total.class_sm_stats().free_blocks_cap_words();
  out->print("Deallocated from chunks in use: ");
  print_scaled_words_and_percentage(out, free_blocks_cap_words, committed_words, scale, 6);
  out->print(" (" UINTX_FORMAT " blocks)", free_blocks_num);
  out->cr();

  // Print total waste.
  const size_t total_waste = ucs_all.waste() + ucs_all.free() + ucs_all.overhead() + total_capacity_in_free_chunks
      + free_blocks_cap_words + unused_words_in_vs;
  out->print("                       -total-: ");
  print_scaled_words_and_percentage(out, total_waste, committed_words, scale, 6);
  out->cr();

  // Print internal statistics
#ifdef ASSERT
  out->cr();
  out->cr();
  out->print_cr("Internal statistics:");
  out->cr();
  out->print_cr("Number of allocations: " UINTX_FORMAT ".", g_internal_statistics.num_allocs);
  out->print_cr("Number of space births: " UINTX_FORMAT ".", g_internal_statistics.num_metaspace_births);
  out->print_cr("Number of space deaths: " UINTX_FORMAT ".", g_internal_statistics.num_metaspace_deaths);
  out->print_cr("Number of virtual space node births: " UINTX_FORMAT ".", g_internal_statistics.num_vsnodes_created);
  out->print_cr("Number of virtual space node deaths: " UINTX_FORMAT ".", g_internal_statistics.num_vsnodes_purged);
  out->print_cr("Number of times virtual space nodes were expanded: " UINTX_FORMAT ".", g_internal_statistics.num_committed_space_expanded);
  out->print_cr("Number of deallocations: " UINTX_FORMAT " (" UINTX_FORMAT " external).", g_internal_statistics.num_deallocs, g_internal_statistics.num_external_deallocs);
  out->print_cr("Allocations from deallocated blocks: " UINTX_FORMAT ".", g_internal_statistics.num_allocs_from_deallocated_blocks);
  out->print_cr("Number of chunks added to freelist: " UINTX_FORMAT ".",
                g_internal_statistics.num_chunks_added_to_freelist);
  out->print_cr("Number of chunks removed from freelist: " UINTX_FORMAT ".",
                g_internal_statistics.num_chunks_removed_from_freelist);
  out->print_cr("Number of chunk merges: " UINTX_FORMAT ", split-ups: " UINTX_FORMAT ".",
                g_internal_statistics.num_chunk_merges, g_internal_statistics.num_chunk_splits);

  out->cr();
#endif

  // Print some interesting settings
  out->cr();
  out->cr();
  print_basic_switches(out, scale);

  out->cr();
  out->print("InitialBootClassLoaderMetaspaceSize: ");
  print_human_readable_size(out, InitialBootClassLoaderMetaspaceSize, scale);

  out->cr();
  out->cr();

} // MetaspaceUtils::print_report()

// Prints an ASCII representation of the given space.
void MetaspaceUtils::print_metaspace_map(outputStream* out, Metaspace::MetadataType mdtype) {
  MutexLocker cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  const bool for_class = mdtype == Metaspace::ClassType ? true : false;
  VirtualSpaceList* const vsl = for_class ? Metaspace::class_space_list() : Metaspace::space_list();
  if (vsl != NULL) {
    if (for_class) {
      if (!Metaspace::using_class_space()) {
        out->print_cr("No Class Space.");
        return;
      }
      out->print_raw("---- Metaspace Map (Class Space) ----");
    } else {
      out->print_raw("---- Metaspace Map (Non-Class Space) ----");
    }
    // Print legend:
    out->cr();
    out->print_cr("Chunk Types (uppercase chunks are in use): x-specialized, s-small, m-medium, h-humongous.");
    out->cr();
    VirtualSpaceList* const vsl = for_class ? Metaspace::class_space_list() : Metaspace::space_list();
    vsl->print_map(out);
    out->cr();
  }
}

void MetaspaceUtils::verify_free_chunks() {
#ifdef ASSERT
  Metaspace::chunk_manager_metadata()->verify(false);
  if (Metaspace::using_class_space()) {
    Metaspace::chunk_manager_class()->verify(false);
  }
#endif
}

void MetaspaceUtils::verify_metrics() {
#ifdef ASSERT
  // Please note: there are time windows where the internal counters are out of sync with
  // reality. For example, when a newly created ClassLoaderMetaspace creates its first chunk -
  // the ClassLoaderMetaspace is not yet attached to its ClassLoaderData object and hence will
  // not be counted when iterating the CLDG. So be careful when you call this method.
  ClassLoaderMetaspaceStatistics total_stat;
  collect_statistics(&total_stat);
  UsedChunksStatistics nonclass_chunk_stat = total_stat.nonclass_sm_stats().totals();
  UsedChunksStatistics class_chunk_stat = total_stat.class_sm_stats().totals();

  bool mismatch = false;
  for (int i = 0; i < Metaspace::MetadataTypeCount; i ++) {
    Metaspace::MetadataType mdtype = (Metaspace::MetadataType)i;
    UsedChunksStatistics chunk_stat = total_stat.sm_stats(mdtype).totals();
    if (capacity_words(mdtype) != chunk_stat.cap() ||
        used_words(mdtype) != chunk_stat.used() ||
        overhead_words(mdtype) != chunk_stat.overhead()) {
      mismatch = true;
      tty->print_cr("MetaspaceUtils::verify_metrics: counter mismatch for mdtype=%u:", mdtype);
      tty->print_cr("Expected cap " SIZE_FORMAT ", used " SIZE_FORMAT ", overhead " SIZE_FORMAT ".",
                    capacity_words(mdtype), used_words(mdtype), overhead_words(mdtype));
      tty->print_cr("Got cap " SIZE_FORMAT ", used " SIZE_FORMAT ", overhead " SIZE_FORMAT ".",
                    chunk_stat.cap(), chunk_stat.used(), chunk_stat.overhead());
      tty->flush();
    }
  }
  assert(mismatch == false, "MetaspaceUtils::verify_metrics: counter mismatch.");
#endif
}

// Metaspace methods

size_t Metaspace::_first_chunk_word_size = 0;
size_t Metaspace::_first_class_chunk_word_size = 0;

size_t Metaspace::_commit_alignment = 0;
size_t Metaspace::_reserve_alignment = 0;

VirtualSpaceList* Metaspace::_space_list = NULL;
VirtualSpaceList* Metaspace::_class_space_list = NULL;

ChunkManager* Metaspace::_chunk_manager_metadata = NULL;
ChunkManager* Metaspace::_chunk_manager_class = NULL;

bool Metaspace::_initialized = false;

#define VIRTUALSPACEMULTIPLIER 2

#ifdef _LP64

void Metaspace::print_compressed_class_space(outputStream* st) {
  if (_class_space_list != NULL) {
    address base = (address)_class_space_list->current_virtual_space()->bottom();
    address top = base + compressed_class_space_size();
    st->print("Compressed class space mapped at: " PTR_FORMAT "-" PTR_FORMAT ", size: " SIZE_FORMAT,
               p2i(base), p2i(top), top - base);
    st->cr();
  }
}

// Given a prereserved space, use that to set up the compressed class space list.
void Metaspace::initialize_class_space(ReservedSpace rs) {
  assert(using_class_space(), "Must be using class space");
  assert(_class_space_list == NULL && _chunk_manager_class == NULL, "Only call once");

  assert(rs.size() == CompressedClassSpaceSize, SIZE_FORMAT " != " SIZE_FORMAT,
         rs.size(), CompressedClassSpaceSize);
  assert(is_aligned(rs.base(), Metaspace::reserve_alignment()) &&
         is_aligned(rs.size(), Metaspace::reserve_alignment()),
         "wrong alignment");

  _class_space_list = new VirtualSpaceList(rs);
  _chunk_manager_class = new ChunkManager(true/*is_class*/);

  // This does currently not work because rs may be the result of a split
  // operation and NMT seems not to be able to handle splits.
  // Will be fixed with JDK-8243535.
  // MemTracker::record_virtual_memory_type((address)rs.base(), mtClass);

  if (!_class_space_list->initialization_succeeded()) {
    vm_exit_during_initialization("Failed to setup compressed class space virtual space list.");
  }

}

// Reserve a range of memory at an address suitable for en/decoding narrow
// Klass pointers (see: CompressedClassPointers::is_valid_base()).
// The returned address shall both be suitable as a compressed class pointers
//  base, and aligned to Metaspace::reserve_alignment (which is equal to or a
//  multiple of allocation granularity).
// On error, returns an unreserved space.
ReservedSpace Metaspace::reserve_address_space_for_compressed_classes(size_t size) {

#ifdef AARCH64
  const size_t alignment = Metaspace::reserve_alignment();

  // AArch64: Try to align metaspace so that we can decode a compressed
  // klass with a single MOVK instruction. We can do this iff the
  // compressed class base is a multiple of 4G.
  // Additionally, above 32G, ensure the lower LogKlassAlignmentInBytes bits
  // of the upper 32-bits of the address are zero so we can handle a shift
  // when decoding.

  static const struct {
    address from;
    address to;
    size_t increment;
  } search_ranges[] = {
    {  (address)(4*G),   (address)(32*G),   4*G, },
    {  (address)(32*G),  (address)(1024*G), (4 << LogKlassAlignmentInBytes) * G },
    {  NULL, NULL, 0 }
  };

  for (int i = 0; search_ranges[i].from != NULL; i ++) {
    address a = search_ranges[i].from;
    assert(CompressedKlassPointers::is_valid_base(a), "Sanity");
    while (a < search_ranges[i].to) {
      ReservedSpace rs(size, Metaspace::reserve_alignment(),
                       false /*large_pages*/, (char*)a);
      if (rs.is_reserved()) {
        assert(a == (address)rs.base(), "Sanity");
        return rs;
      }
      a +=  search_ranges[i].increment;
    }
  }

  // Note: on AARCH64, if the code above does not find any good placement, we
  // have no recourse. We return an empty space and the VM will exit.
  return ReservedSpace();
#else
  // Default implementation: Just reserve anywhere.
  return ReservedSpace(size, Metaspace::reserve_alignment(), false, (char*)NULL);
#endif // AARCH64
}

#endif // _LP64


void Metaspace::ergo_initialize() {
  if (DumpSharedSpaces) {
    // Using large pages when dumping the shared archive is currently not implemented.
    FLAG_SET_ERGO(UseLargePagesInMetaspace, false);
  }

  size_t page_size = os::vm_page_size();
  if (UseLargePages && UseLargePagesInMetaspace) {
    page_size = os::large_page_size();
  }

  _commit_alignment  = page_size;
  _reserve_alignment = MAX2(page_size, (size_t)os::vm_allocation_granularity());

  // The upcoming Metaspace rewrite will impose a higher alignment granularity.
  // To prepare for that and to catch/prevent any misuse of Metaspace alignment
  // which may creep in, up the alignment a bit.
  if (_reserve_alignment == 4 * K) {
    _reserve_alignment *= 4;
  }

  // Do not use FLAG_SET_ERGO to update MaxMetaspaceSize, since this will
  // override if MaxMetaspaceSize was set on the command line or not.
  // This information is needed later to conform to the specification of the
  // java.lang.management.MemoryUsage API.
  //
  // Ideally, we would be able to set the default value of MaxMetaspaceSize in
  // globals.hpp to the aligned value, but this is not possible, since the
  // alignment depends on other flags being parsed.
  MaxMetaspaceSize = align_down_bounded(MaxMetaspaceSize, _reserve_alignment);

  if (MetaspaceSize > MaxMetaspaceSize) {
    MetaspaceSize = MaxMetaspaceSize;
  }

  MetaspaceSize = align_down_bounded(MetaspaceSize, _commit_alignment);

  assert(MetaspaceSize <= MaxMetaspaceSize, "MetaspaceSize should be limited by MaxMetaspaceSize");

  MinMetaspaceExpansion = align_down_bounded(MinMetaspaceExpansion, _commit_alignment);
  MaxMetaspaceExpansion = align_down_bounded(MaxMetaspaceExpansion, _commit_alignment);

  CompressedClassSpaceSize = align_down_bounded(CompressedClassSpaceSize, _reserve_alignment);

  // Initial virtual space size will be calculated at global_initialize()
  size_t min_metaspace_sz =
      VIRTUALSPACEMULTIPLIER * InitialBootClassLoaderMetaspaceSize;
  if (UseCompressedClassPointers) {
    if ((min_metaspace_sz + CompressedClassSpaceSize) >  MaxMetaspaceSize) {
      if (min_metaspace_sz >= MaxMetaspaceSize) {
        vm_exit_during_initialization("MaxMetaspaceSize is too small.");
      } else {
        FLAG_SET_ERGO(CompressedClassSpaceSize,
                      MaxMetaspaceSize - min_metaspace_sz);
      }
    }
  } else if (min_metaspace_sz >= MaxMetaspaceSize) {
    FLAG_SET_ERGO(InitialBootClassLoaderMetaspaceSize,
                  min_metaspace_sz);
  }

  set_compressed_class_space_size(CompressedClassSpaceSize);
}

void Metaspace::global_initialize() {
  MetaspaceGC::initialize();

  // If UseCompressedClassPointers=1, we have two cases:
  // a) if CDS is active (either dump time or runtime), it will create the ccs
  //    for us, initialize it and set up CompressedKlassPointers encoding.
  //    Class space will be reserved above the mapped archives.
  // b) if CDS is not active, we will create the ccs on our own. It will be
  //    placed above the java heap, since we assume it has been placed in low
  //    address regions. We may rethink this (see JDK-8244943). Failing that,
  //    it will be placed anywhere.

#if INCLUDE_CDS
  // case (a)
  if (DumpSharedSpaces) {
    MetaspaceShared::initialize_dumptime_shared_and_meta_spaces();
  } else if (UseSharedSpaces) {
    // If any of the archived space fails to map, UseSharedSpaces
    // is reset to false.
    MetaspaceShared::initialize_runtime_shared_and_meta_spaces();
  }

  if (DynamicDumpSharedSpaces && !UseSharedSpaces) {
    vm_exit_during_initialization("DynamicDumpSharedSpaces is unsupported when base CDS archive is not loaded", NULL);
  }
#endif // INCLUDE_CDS

#ifdef _LP64

  if (using_class_space() && !class_space_is_initialized()) {
    assert(!UseSharedSpaces && !DumpSharedSpaces, "CDS should be off at this point");

    // case (b)
    ReservedSpace rs;

    // If UseCompressedOops=1, java heap may have been placed in coops-friendly
    //  territory already (lower address regions), so we attempt to place ccs
    //  right above the java heap.
    // If UseCompressedOops=0, the heap has been placed anywhere - probably in
    //  high memory regions. In that case, try to place ccs at the lowest allowed
    //  mapping address.
    address base = UseCompressedOops ? CompressedOops::end() : (address)HeapBaseMinAddress;
    base = align_up(base, Metaspace::reserve_alignment());

    const size_t size = align_up(CompressedClassSpaceSize, Metaspace::reserve_alignment());
    if (base != NULL) {
      if (CompressedKlassPointers::is_valid_base(base)) {
        rs = ReservedSpace(size, Metaspace::reserve_alignment(),
                           false /* large */, (char*)base);
      }
    }

    // ...failing that, reserve anywhere, but let platform do optimized placement:
    if (!rs.is_reserved()) {
      rs = Metaspace::reserve_address_space_for_compressed_classes(size);
    }

    // ...failing that, give up.
    if (!rs.is_reserved()) {
      vm_exit_during_initialization(
          err_msg("Could not allocate compressed class space: " SIZE_FORMAT " bytes",
                   compressed_class_space_size()));
    }

    // Initialize space
    Metaspace::initialize_class_space(rs);

    // Set up compressed class pointer encoding.
    CompressedKlassPointers::initialize((address)rs.base(), rs.size());
  }

#endif

  // Initialize these before initializing the VirtualSpaceList
  _first_chunk_word_size = InitialBootClassLoaderMetaspaceSize / BytesPerWord;
  _first_chunk_word_size = align_word_size_up(_first_chunk_word_size);
  // Make the first class chunk bigger than a medium chunk so it's not put
  // on the medium chunk list.   The next chunk will be small and progress
  // from there.  This size calculated by -version.
  _first_class_chunk_word_size = MIN2((size_t)MediumChunk*6,
                                     (CompressedClassSpaceSize/BytesPerWord)*2);
  _first_class_chunk_word_size = align_word_size_up(_first_class_chunk_word_size);
  // Arbitrarily set the initial virtual space to a multiple
  // of the boot class loader size.
  size_t word_size = VIRTUALSPACEMULTIPLIER * _first_chunk_word_size;
  word_size = align_up(word_size, Metaspace::reserve_alignment_words());

  // Initialize the list of virtual spaces.
  _space_list = new VirtualSpaceList(word_size);
  _chunk_manager_metadata = new ChunkManager(false/*metaspace*/);

  if (!_space_list->initialization_succeeded()) {
    vm_exit_during_initialization("Unable to setup metadata virtual space list.", NULL);
  }

  _tracer = new MetaspaceTracer();

  _initialized = true;

#ifdef _LP64
  if (UseCompressedClassPointers) {
    // Note: "cds" would be a better fit but keep this for backward compatibility.
    LogTarget(Info, gc, metaspace) lt;
    if (lt.is_enabled()) {
      ResourceMark rm;
      LogStream ls(lt);
      CDS_ONLY(MetaspaceShared::print_on(&ls);)
      Metaspace::print_compressed_class_space(&ls);
      CompressedKlassPointers::print_mode(&ls);
    }
  }
#endif

}

void Metaspace::post_initialize() {
  MetaspaceGC::post_initialize();
}

void Metaspace::verify_global_initialization() {
  assert(space_list() != NULL, "Metadata VirtualSpaceList has not been initialized");
  assert(chunk_manager_metadata() != NULL, "Metadata ChunkManager has not been initialized");

  if (using_class_space()) {
    assert(class_space_list() != NULL, "Class VirtualSpaceList has not been initialized");
    assert(chunk_manager_class() != NULL, "Class ChunkManager has not been initialized");
  }
}

size_t Metaspace::align_word_size_up(size_t word_size) {
  size_t byte_size = word_size * wordSize;
  return ReservedSpace::allocation_align_size_up(byte_size) / wordSize;
}

MetaWord* Metaspace::allocate(ClassLoaderData* loader_data, size_t word_size,
                              MetaspaceObj::Type type, TRAPS) {
  assert(!_frozen, "sanity");
  assert(!(DumpSharedSpaces && THREAD->is_VM_thread()), "sanity");

  if (HAS_PENDING_EXCEPTION) {
    assert(false, "Should not allocate with exception pending");
    return NULL;  // caller does a CHECK_NULL too
  }

  assert(loader_data != NULL, "Should never pass around a NULL loader_data. "
        "ClassLoaderData::the_null_class_loader_data() should have been used.");

  MetadataType mdtype = (type == MetaspaceObj::ClassType) ? ClassType : NonClassType;

  // Try to allocate metadata.
  MetaWord* result = loader_data->metaspace_non_null()->allocate(word_size, mdtype);

  if (result == NULL) {
    tracer()->report_metaspace_allocation_failure(loader_data, word_size, type, mdtype);

    // Allocation failed.
    if (is_init_completed()) {
      // Only start a GC if the bootstrapping has completed.
      // Try to clean out some heap memory and retry. This can prevent premature
      // expansion of the metaspace.
      result = Universe::heap()->satisfy_failed_metadata_allocation(loader_data, word_size, mdtype);
    }
  }

  if (result == NULL) {
    if (DumpSharedSpaces) {
      // CDS dumping keeps loading classes, so if we hit an OOM we probably will keep hitting OOM.
      // We should abort to avoid generating a potentially bad archive.
      vm_exit_during_cds_dumping(err_msg("Failed allocating metaspace object type %s of size " SIZE_FORMAT ". CDS dump aborted.",
          MetaspaceObj::type_name(type), word_size * BytesPerWord),
        err_msg("Please increase MaxMetaspaceSize (currently " SIZE_FORMAT " bytes).", MaxMetaspaceSize));
    }
    report_metadata_oome(loader_data, word_size, type, mdtype, THREAD);
    assert(HAS_PENDING_EXCEPTION, "sanity");
    return NULL;
  }

  // Zero initialize.
  Copy::fill_to_words((HeapWord*)result, word_size, 0);

  return result;
}

void Metaspace::report_metadata_oome(ClassLoaderData* loader_data, size_t word_size, MetaspaceObj::Type type, MetadataType mdtype, TRAPS) {
  tracer()->report_metadata_oom(loader_data, word_size, type, mdtype);

  // If result is still null, we are out of memory.
  Log(gc, metaspace, freelist, oom) log;
  if (log.is_info()) {
    log.info("Metaspace (%s) allocation failed for size " SIZE_FORMAT,
             is_class_space_allocation(mdtype) ? "class" : "data", word_size);
    ResourceMark rm;
    if (log.is_debug()) {
      if (loader_data->metaspace_or_null() != NULL) {
        LogStream ls(log.debug());
        loader_data->print_value_on(&ls);
      }
    }
    LogStream ls(log.info());
    // In case of an OOM, log out a short but still useful report.
    MetaspaceUtils::print_basic_report(&ls, 0);
  }

  bool out_of_compressed_class_space = false;
  if (is_class_space_allocation(mdtype)) {
    ClassLoaderMetaspace* metaspace = loader_data->metaspace_non_null();
    out_of_compressed_class_space =
      MetaspaceUtils::committed_bytes(Metaspace::ClassType) +
      (metaspace->class_chunk_size(word_size) * BytesPerWord) >
      CompressedClassSpaceSize;
  }

  // -XX:+HeapDumpOnOutOfMemoryError and -XX:OnOutOfMemoryError support
  const char* space_string = out_of_compressed_class_space ?
    "Compressed class space" : "Metaspace";

  report_java_out_of_memory(space_string);

  if (JvmtiExport::should_post_resource_exhausted()) {
    JvmtiExport::post_resource_exhausted(
        JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR,
        space_string);
  }

  if (!is_init_completed()) {
    vm_exit_during_initialization("OutOfMemoryError", space_string);
  }

  if (out_of_compressed_class_space) {
    THROW_OOP(Universe::out_of_memory_error_class_metaspace());
  } else {
    THROW_OOP(Universe::out_of_memory_error_metaspace());
  }
}

const char* Metaspace::metadata_type_name(Metaspace::MetadataType mdtype) {
  switch (mdtype) {
    case Metaspace::ClassType: return "Class";
    case Metaspace::NonClassType: return "Metadata";
    default:
      assert(false, "Got bad mdtype: %d", (int) mdtype);
      return NULL;
  }
}

void Metaspace::purge(MetadataType mdtype) {
  get_space_list(mdtype)->purge(get_chunk_manager(mdtype));
}

void Metaspace::purge() {
  MutexLocker cl(MetaspaceExpand_lock,
                 Mutex::_no_safepoint_check_flag);
  purge(NonClassType);
  if (using_class_space()) {
    purge(ClassType);
  }
}

bool Metaspace::contains(const void* ptr) {
  if (MetaspaceShared::is_in_shared_metaspace(ptr)) {
    return true;
  }
  return contains_non_shared(ptr);
}

bool Metaspace::contains_non_shared(const void* ptr) {
  if (using_class_space() && get_space_list(ClassType)->contains(ptr)) {
     return true;
  }

  return get_space_list(NonClassType)->contains(ptr);
}

// ClassLoaderMetaspace

ClassLoaderMetaspace::ClassLoaderMetaspace(Mutex* lock, Metaspace::MetaspaceType type)
  : _space_type(type)
  , _lock(lock)
  , _vsm(NULL)
  , _class_vsm(NULL)
{
  initialize(lock, type);
}

ClassLoaderMetaspace::~ClassLoaderMetaspace() {
  Metaspace::assert_not_frozen();
  DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_metaspace_deaths));
  delete _vsm;
  if (Metaspace::using_class_space()) {
    delete _class_vsm;
  }
}

void ClassLoaderMetaspace::initialize_first_chunk(Metaspace::MetaspaceType type, Metaspace::MetadataType mdtype) {
  Metachunk* chunk = get_initialization_chunk(type, mdtype);
  if (chunk != NULL) {
    // Add to this manager's list of chunks in use and make it the current_chunk().
    get_space_manager(mdtype)->add_chunk(chunk, true);
  }
}

Metachunk* ClassLoaderMetaspace::get_initialization_chunk(Metaspace::MetaspaceType type, Metaspace::MetadataType mdtype) {
  size_t chunk_word_size = get_space_manager(mdtype)->get_initial_chunk_size(type);

  // Get a chunk from the chunk freelist
  Metachunk* chunk = Metaspace::get_chunk_manager(mdtype)->chunk_freelist_allocate(chunk_word_size);

  if (chunk == NULL) {
    chunk = Metaspace::get_space_list(mdtype)->get_new_chunk(chunk_word_size,
                                                  get_space_manager(mdtype)->medium_chunk_bunch());
  }

  return chunk;
}

void ClassLoaderMetaspace::initialize(Mutex* lock, Metaspace::MetaspaceType type) {
  Metaspace::verify_global_initialization();

  DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_metaspace_births));

  // Allocate SpaceManager for metadata objects.
  _vsm = new SpaceManager(Metaspace::NonClassType, type, lock);

  if (Metaspace::using_class_space()) {
    // Allocate SpaceManager for classes.
    _class_vsm = new SpaceManager(Metaspace::ClassType, type, lock);
  }

  MutexLocker cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  // Allocate chunk for metadata objects
  initialize_first_chunk(type, Metaspace::NonClassType);

  // Allocate chunk for class metadata objects
  if (Metaspace::using_class_space()) {
    initialize_first_chunk(type, Metaspace::ClassType);
  }
}

MetaWord* ClassLoaderMetaspace::allocate(size_t word_size, Metaspace::MetadataType mdtype) {
  Metaspace::assert_not_frozen();

  DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_allocs));

  // Don't use class_vsm() unless UseCompressedClassPointers is true.
  if (Metaspace::is_class_space_allocation(mdtype)) {
    return  class_vsm()->allocate(word_size);
  } else {
    return  vsm()->allocate(word_size);
  }
}

MetaWord* ClassLoaderMetaspace::expand_and_allocate(size_t word_size, Metaspace::MetadataType mdtype) {
  Metaspace::assert_not_frozen();
  size_t delta_bytes = MetaspaceGC::delta_capacity_until_GC(word_size * BytesPerWord);
  assert(delta_bytes > 0, "Must be");

  size_t before = 0;
  size_t after = 0;
  bool can_retry = true;
  MetaWord* res;
  bool incremented;

  // Each thread increments the HWM at most once. Even if the thread fails to increment
  // the HWM, an allocation is still attempted. This is because another thread must then
  // have incremented the HWM and therefore the allocation might still succeed.
  do {
    incremented = MetaspaceGC::inc_capacity_until_GC(delta_bytes, &after, &before, &can_retry);
    res = allocate(word_size, mdtype);
  } while (!incremented && res == NULL && can_retry);

  if (incremented) {
    Metaspace::tracer()->report_gc_threshold(before, after,
                                  MetaspaceGCThresholdUpdater::ExpandAndAllocate);
    log_trace(gc, metaspace)("Increase capacity to GC from " SIZE_FORMAT " to " SIZE_FORMAT, before, after);
  }

  return res;
}

size_t ClassLoaderMetaspace::allocated_blocks_bytes() const {
  return (vsm()->used_words() +
      (Metaspace::using_class_space() ? class_vsm()->used_words() : 0)) * BytesPerWord;
}

size_t ClassLoaderMetaspace::allocated_chunks_bytes() const {
  return (vsm()->capacity_words() +
      (Metaspace::using_class_space() ? class_vsm()->capacity_words() : 0)) * BytesPerWord;
}

void ClassLoaderMetaspace::deallocate(MetaWord* ptr, size_t word_size, bool is_class) {
  Metaspace::assert_not_frozen();
  assert(!SafepointSynchronize::is_at_safepoint()
         || Thread::current()->is_VM_thread(), "should be the VM thread");

  DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_external_deallocs));

  MutexLocker ml(vsm()->lock(), Mutex::_no_safepoint_check_flag);

  if (is_class && Metaspace::using_class_space()) {
    class_vsm()->deallocate(ptr, word_size);
  } else {
    vsm()->deallocate(ptr, word_size);
  }
}

size_t ClassLoaderMetaspace::class_chunk_size(size_t word_size) {
  assert(Metaspace::using_class_space(), "Has to use class space");
  return class_vsm()->calc_chunk_size(word_size);
}

void ClassLoaderMetaspace::print_on(outputStream* out) const {
  // Print both class virtual space counts and metaspace.
  if (Verbose) {
    vsm()->print_on(out);
    if (Metaspace::using_class_space()) {
      class_vsm()->print_on(out);
    }
  }
}

void ClassLoaderMetaspace::verify() {
  vsm()->verify();
  if (Metaspace::using_class_space()) {
    class_vsm()->verify();
  }
}

void ClassLoaderMetaspace::add_to_statistics_locked(ClassLoaderMetaspaceStatistics* out) const {
  assert_lock_strong(lock());
  vsm()->add_to_statistics_locked(&out->nonclass_sm_stats());
  if (Metaspace::using_class_space()) {
    class_vsm()->add_to_statistics_locked(&out->class_sm_stats());
  }
}

void ClassLoaderMetaspace::add_to_statistics(ClassLoaderMetaspaceStatistics* out) const {
  MutexLocker cl(lock(), Mutex::_no_safepoint_check_flag);
  add_to_statistics_locked(out);
}

/////////////// Unit tests ///////////////

struct chunkmanager_statistics_t {
  int num_specialized_chunks;
  int num_small_chunks;
  int num_medium_chunks;
  int num_humongous_chunks;
};

extern void test_metaspace_retrieve_chunkmanager_statistics(Metaspace::MetadataType mdType, chunkmanager_statistics_t* out) {
  ChunkManager* const chunk_manager = Metaspace::get_chunk_manager(mdType);
  ChunkManagerStatistics stat;
  chunk_manager->collect_statistics(&stat);
  out->num_specialized_chunks = (int)stat.chunk_stats(SpecializedIndex).num();
  out->num_small_chunks = (int)stat.chunk_stats(SmallIndex).num();
  out->num_medium_chunks = (int)stat.chunk_stats(MediumIndex).num();
  out->num_humongous_chunks = (int)stat.chunk_stats(HumongousIndex).num();
}

struct chunk_geometry_t {
  size_t specialized_chunk_word_size;
  size_t small_chunk_word_size;
  size_t medium_chunk_word_size;
};

extern void test_metaspace_retrieve_chunk_geometry(Metaspace::MetadataType mdType, chunk_geometry_t* out) {
  if (mdType == Metaspace::NonClassType) {
    out->specialized_chunk_word_size = SpecializedChunk;
    out->small_chunk_word_size = SmallChunk;
    out->medium_chunk_word_size = MediumChunk;
  } else {
    out->specialized_chunk_word_size = ClassSpecializedChunk;
    out->small_chunk_word_size = ClassSmallChunk;
    out->medium_chunk_word_size = ClassMediumChunk;
  }
}

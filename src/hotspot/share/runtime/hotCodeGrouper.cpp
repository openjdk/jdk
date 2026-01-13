/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifdef COMPILER2

#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/hotCodeGrouper.hpp"
#include "runtime/hotCodeSampler.hpp"
#include "runtime/java.hpp"
#include "runtime/javaThread.inline.hpp"

// Initalize static variables
bool      HotCodeGrouper::_is_initialized = false;
int       HotCodeGrouper::_new_c2_nmethods_count = 0;
int       HotCodeGrouper::_total_c2_nmethods_count = 0;
CodeHeap* HotCodeGrouper::hot_code_heap = nullptr;

void HotCodeGrouper::initialize() {
  if (!HotCodeHeap) {
    return; // No hot code heap, no need for nmethod grouping
  }

  assert(CompilerConfig::is_c2_enabled(), "HotCodeGrouper requires C2 enabled");
  assert(NMethodRelocation, "HotCodeGrouper requires NMethodRelocation enabled");
  assert(HotCodeHeapSize > 0, "HotCodeHeapSize must be non-zero to use HotCodeGrouper");

  // Find HotCodeHeap
  const GrowableArray<CodeHeap*>* heaps = CodeCache::nmethod_heaps();
  for (GrowableArrayIterator<CodeHeap*> heap = heaps->begin(); heap != heaps->end(); ++heap) {
    if ((*heap)->accepts(CodeBlobType::MethodHot)) {
      hot_code_heap = *heap;
      break;
    }
  }

  // If the hot code heap is not found return and remain uninitialized
  if (hot_code_heap == nullptr) {
    return;
  }

  NonJavaThread* nmethod_grouper_thread = new HotCodeGrouper();
  if (os::create_thread(nmethod_grouper_thread, os::os_thread)) {
    os::start_thread(nmethod_grouper_thread);
  } else {
    vm_exit_during_initialization("Failed to create C2 nmethod grouper thread");
  }
  _is_initialized = true;
}

static inline bool steady_nmethod_count(int new_nmethods_count, int total_nmethods_count) {
  if (total_nmethods_count <= 0) {
    log_trace(hotcodegrouper)("C2 nmethod count not steady. Total C2 nmethods %d <= 0", total_nmethods_count);
    return false;
  }

  const double ratio_new = (double)new_nmethods_count / total_nmethods_count;
  bool is_steady_nmethod_count = ratio_new < HotCodeSteadyThreshold;

  log_info(hotcodegrouper)("C2 nmethod count %s", is_steady_nmethod_count ? "steady" : "not steady");
  log_trace(hotcodegrouper)("\t- New: %d. Total: %d. Ratio: %f. Threshold: %f", new_nmethods_count, total_nmethods_count, ratio_new, HotCodeSteadyThreshold);

  return is_steady_nmethod_count;
}

bool HotCodeGrouper::hot_heap_has_space(size_t size) {
  assert(hot_code_heap != nullptr, "must initialize HotCodeGrouper");
  return hot_code_heap->unallocated_capacity() > size;
}

void HotCodeGrouper::run() {
  while (true) {
    os::naked_sleep(HotCodeIntervalSeconds * 1000);

    ResourceMark rm;

    { // Acquire CodeCache_lock and check if c2 nmethod count is steady
      MutexLocker ml_CodeCache_lock(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      bool is_steady_nmethod_count = steady_nmethod_count(_new_c2_nmethods_count, _total_c2_nmethods_count);
      _new_c2_nmethods_count = 0;

      if (!is_steady_nmethod_count) {
        continue;
      }
    }

    // Sample application and group hot nmethods
    ThreadSampler sampler;
    sampler.do_sampling();
    do_grouping(sampler);
  }
}

void HotCodeGrouper::do_grouping(ThreadSampler& sampler) {
  while (sampler.has_candidates()) {

    double ratio_from_hot = sampler.get_hot_sample_ratio();
    log_trace(hotcodegrouper)("Ratio of samples from hot code heap: %f", ratio_from_hot);
    if (ratio_from_hot > HotCodeSampleRatio) {
      log_info(hotcodegrouper)("Ratio of samples from hot nmethods (%f) over threshold (%f). Done grouping", ratio_from_hot, HotCodeSampleRatio);
      break;
    }

    nmethod* candidate = sampler.get_candidate();

    MutexLocker ml_Compile_lock(Compile_lock);
    MutexLocker ml_CompiledIC_lock(CompiledIC_lock, Mutex::_no_safepoint_check_flag);
    MutexLocker ml_CodeCache_lock(CodeCache_lock, Mutex::_no_safepoint_check_flag);

    // Verify that address still points to CodeBlob
    CodeBlob* blob = CodeCache::find_blob(candidate);
    if (blob == nullptr) {
      continue;
    }

    // Verify that nmethod address is still valid and not in hot code heap
    nmethod* nm = blob->as_nmethod_or_null();
    if (nm != candidate || !nm->is_in_use() || !nm->is_compiled_by_c2() || CodeCache::get_code_blob_type(nm) == CodeBlobType::MethodHot) {
      continue;
    }

    if (!hot_heap_has_space(nm->size())) {
      log_info(hotcodegrouper)("Not enough space in HotCodeHeap (%ld bytes) to relocate nm (%d bytes). Bailing out",
        hot_code_heap->unallocated_capacity(), nm->size());
      return;
    }

    { // Relocate caller
      CompiledICLocker ic_locker(nm);
      if (nm->relocate(CodeBlobType::MethodHot) != nullptr) {
        sampler.update_sample_count(nm);
      }
    }

    // Loop over relocations to relocate callees
    RelocIterator relocIter(nm);
    while (relocIter.next()) {

      // Check is a call
      Relocation* reloc = relocIter.reloc();
      if(!reloc->is_call()) {
        continue;
      }

      // Find the call destination address
      address dest = ((CallRelocation*) reloc)->destination();

      // Check if the destination is to something in the code cache
      if (!CodeCache::contains(dest)) {
        continue;
      }

      // Check if destination is a CodeBlob
      CodeBlob* dest_blob = CodeCache::find_blob(dest);
      if (dest_blob == nullptr) {
        continue;
      }

      // Check if the destination is an nmethod
      nmethod* dest_nm = dest_blob->as_nmethod_or_null();
      if (dest_nm == nullptr || dest_nm->method() == nullptr) {
        continue;
      }

      // Retrieve the latest nmethod for the destination's Method.
      // Due to relocation or recompilation, the destination may not yet reference
      // the Method’s most up to date nmethod.
      nmethod* actual_dest_nm = dest_nm->method()->code();

      // Check is valid
      if (actual_dest_nm == nullptr || !actual_dest_nm->is_in_use() || !actual_dest_nm->is_compiled_by_c2() || CodeCache::get_code_blob_type(actual_dest_nm) == CodeBlobType::MethodHot) {
        continue;
      }

      if (!hot_heap_has_space(actual_dest_nm->size())) {
        log_info(hotcodegrouper)("Not enough space in HotCodeHeap (%ld bytes) to relocate nm (%d bytes). Bailing out",
          hot_code_heap->unallocated_capacity(), actual_dest_nm->size());
        return;
      }

      { // Relocate callee
        CompiledICLocker ic_locker(actual_dest_nm);
        if (actual_dest_nm->relocate(CodeBlobType::MethodHot) != nullptr) {
          sampler.update_sample_count(actual_dest_nm);
        }
      }
    }
  }
}

void HotCodeGrouper::unregister_nmethod(nmethod* nm) {
  assert_lock_strong(CodeCache_lock);
  if (!_is_initialized) {
    return;
  }

  if (!nm->is_compiled_by_c2()) {
    return;
  }

  if (CodeCache::get_code_blob_type(nm) == CodeBlobType::MethodHot) {
    // Nmethods in the hot code heap do not count towards total C2 nmethods.
    return;
  }

  // CodeCache_lock is held, so we can safely decrement the count.
  _total_c2_nmethods_count--;
}

void HotCodeGrouper::register_nmethod(nmethod* nm) {
  assert_lock_strong(CodeCache_lock);
  if (!_is_initialized) {
    return;
  }

  if (!nm->is_compiled_by_c2()) {
    return; // Only C2 nmethods are relocated to HotCodeHeap.
  }

  if (CodeCache::get_code_blob_type(nm) == CodeBlobType::MethodHot) {
    // Nmethods in the hot code heap do not count towards total C2 nmethods.
    return;
  }

  // CodeCache_lock is held, so we can safely increment the count.
  _new_c2_nmethods_count++;
  _total_c2_nmethods_count++;
}
#endif // COMPILER2

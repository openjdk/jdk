/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_CMS_CMSHEAP_HPP
#define SHARE_GC_CMS_CMSHEAP_HPP

#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/cms/parNewGeneration.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/oopStorageParState.hpp"
#include "utilities/growableArray.hpp"

class CLDClosure;
class GenCollectorPolicy;
class GCMemoryManager;
class MemoryPool;
class OopsInGenClosure;
class outputStream;
class StrongRootsScope;
class ThreadClosure;
class WorkGang;

class CMSHeap : public GenCollectedHeap {
public:
  CMSHeap(GenCollectorPolicy *policy);

  // Returns JNI_OK on success
  virtual jint initialize();
  virtual CardTableRS* create_rem_set(const MemRegion& reserved_region);

  // Convenience function to be used in situations where the heap type can be
  // asserted to be this type.
  static CMSHeap* heap();

  virtual Name kind() const {
    return CollectedHeap::CMS;
  }

  virtual const char* name() const {
    return "Concurrent Mark Sweep";
  }

  WorkGang* workers() const { return _workers; }

  virtual void print_gc_threads_on(outputStream* st) const;
  virtual void gc_threads_do(ThreadClosure* tc) const;
  virtual void print_on_error(outputStream* st) const;

  // Perform a full collection of the heap; intended for use in implementing
  // "System.gc". This implies as full a collection as the CollectedHeap
  // supports. Caller does not hold the Heap_lock on entry.
  void collect(GCCause::Cause cause);

  void stop();
  void safepoint_synchronize_begin();
  void safepoint_synchronize_end();

  virtual GrowableArray<GCMemoryManager*> memory_managers();
  virtual GrowableArray<MemoryPool*> memory_pools();

  // If "young_gen_as_roots" is false, younger generations are
  // not scanned as roots; in this case, the caller must be arranging to
  // scan the younger generations itself.  (For example, a generation might
  // explicitly mark reachable objects in younger generations, to avoid
  // excess storage retention.)
  void cms_process_roots(StrongRootsScope* scope,
                         bool young_gen_as_roots,
                         ScanningOption so,
                         bool only_strong_roots,
                         OopsInGenClosure* root_closure,
                         CLDClosure* cld_closure);

  GCMemoryManager* old_manager() const { return _old_manager; }

  ParNewGeneration* young_gen() const {
    assert(_young_gen->kind() == Generation::ParNew, "Wrong generation type");
    return static_cast<ParNewGeneration*>(_young_gen);
  }

  ConcurrentMarkSweepGeneration* old_gen() const {
    assert(_old_gen->kind() == Generation::ConcurrentMarkSweep, "Wrong generation kind");
    return static_cast<ConcurrentMarkSweepGeneration*>(_old_gen);
  }

  // Apply "cur->do_oop" or "older->do_oop" to all the oops in objects
  // allocated since the last call to save_marks in the young generation.
  // The "cur" closure is applied to references in the younger generation
  // at "level", and the "older" closure to older generations.
  template <typename OopClosureType1, typename OopClosureType2>
  void oop_since_save_marks_iterate(OopClosureType1* cur,
                                    OopClosureType2* older);

private:
  WorkGang* _workers;
  MemoryPool* _eden_pool;
  MemoryPool* _survivor_pool;
  MemoryPool* _old_pool;

  virtual void gc_prologue(bool full);
  virtual void gc_epilogue(bool full);

  virtual void initialize_serviceability();

  // Accessor for memory state verification support
  NOT_PRODUCT(
    virtual size_t skip_header_HeapWords() { return CMSCollector::skip_header_HeapWords(); }
  )

  // Returns success or failure.
  bool create_cms_collector();

  // In support of ExplicitGCInvokesConcurrent functionality
  bool should_do_concurrent_full_gc(GCCause::Cause cause);

  void collect_mostly_concurrent(GCCause::Cause cause);
};

#endif // SHARE_GC_CMS_CMSHEAP_HPP

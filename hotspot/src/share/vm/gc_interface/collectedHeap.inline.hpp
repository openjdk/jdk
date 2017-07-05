/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_INTERFACE_COLLECTEDHEAP_INLINE_HPP
#define SHARE_VM_GC_INTERFACE_COLLECTEDHEAP_INLINE_HPP

#include "gc_interface/collectedHeap.hpp"
#include "memory/threadLocalAllocBuffer.inline.hpp"
#include "memory/universe.hpp"
#include "oops/arrayOop.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/thread.hpp"
#include "services/lowMemoryDetector.hpp"
#include "utilities/copy.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "thread_windows.inline.hpp"
#endif

// Inline allocation implementations.

void CollectedHeap::post_allocation_setup_common(KlassHandle klass,
                                                 HeapWord* obj,
                                                 size_t size) {
  post_allocation_setup_no_klass_install(klass, obj, size);
  post_allocation_install_obj_klass(klass, oop(obj), (int) size);
}

void CollectedHeap::post_allocation_setup_no_klass_install(KlassHandle klass,
                                                           HeapWord* objPtr,
                                                           size_t size) {
  oop obj = (oop)objPtr;

  assert(obj != NULL, "NULL object pointer");
  if (UseBiasedLocking && (klass() != NULL)) {
    obj->set_mark(klass->prototype_header());
  } else {
    // May be bootstrapping
    obj->set_mark(markOopDesc::prototype());
  }
}

void CollectedHeap::post_allocation_install_obj_klass(KlassHandle klass,
                                                   oop obj,
                                                   int size) {
  // These asserts are kind of complicated because of klassKlass
  // and the beginning of the world.
  assert(klass() != NULL || !Universe::is_fully_initialized(), "NULL klass");
  assert(klass() == NULL || klass()->is_klass(), "not a klass");
  assert(klass() == NULL || klass()->klass_part() != NULL, "not a klass");
  assert(obj != NULL, "NULL object pointer");
  obj->set_klass(klass());
  assert(!Universe::is_fully_initialized() || obj->blueprint() != NULL,
         "missing blueprint");
}

// Support for jvmti and dtrace
inline void post_allocation_notify(KlassHandle klass, oop obj) {
  // support low memory notifications (no-op if not enabled)
  LowMemoryDetector::detect_low_memory_for_collected_pools();

  // support for JVMTI VMObjectAlloc event (no-op if not enabled)
  JvmtiExport::vm_object_alloc_event_collector(obj);

  if (DTraceAllocProbes) {
    // support for Dtrace object alloc event (no-op most of the time)
    if (klass() != NULL && klass()->klass_part()->name() != NULL) {
      SharedRuntime::dtrace_object_alloc(obj);
    }
  }
}

void CollectedHeap::post_allocation_setup_obj(KlassHandle klass,
                                              HeapWord* obj,
                                              size_t size) {
  post_allocation_setup_common(klass, obj, size);
  assert(Universe::is_bootstrapping() ||
         !((oop)obj)->blueprint()->oop_is_array(), "must not be an array");
  // notify jvmti and dtrace
  post_allocation_notify(klass, (oop)obj);
}

void CollectedHeap::post_allocation_setup_array(KlassHandle klass,
                                                HeapWord* obj,
                                                size_t size,
                                                int length) {
  // Set array length before setting the _klass field
  // in post_allocation_setup_common() because the klass field
  // indicates that the object is parsable by concurrent GC.
  assert(length >= 0, "length should be non-negative");
  ((arrayOop)obj)->set_length(length);
  post_allocation_setup_common(klass, obj, size);
  assert(((oop)obj)->blueprint()->oop_is_array(), "must be an array");
  // notify jvmti and dtrace (must be after length is set for dtrace)
  post_allocation_notify(klass, (oop)obj);
}

HeapWord* CollectedHeap::common_mem_allocate_noinit(size_t size, bool is_noref, TRAPS) {

  // Clear unhandled oops for memory allocation.  Memory allocation might
  // not take out a lock if from tlab, so clear here.
  CHECK_UNHANDLED_OOPS_ONLY(THREAD->clear_unhandled_oops();)

  if (HAS_PENDING_EXCEPTION) {
    NOT_PRODUCT(guarantee(false, "Should not allocate with exception pending"));
    return NULL;  // caller does a CHECK_0 too
  }

  // We may want to update this, is_noref objects might not be allocated in TLABs.
  HeapWord* result = NULL;
  if (UseTLAB) {
    result = CollectedHeap::allocate_from_tlab(THREAD, size);
    if (result != NULL) {
      assert(!HAS_PENDING_EXCEPTION,
             "Unexpected exception, will result in uninitialized storage");
      return result;
    }
  }
  bool gc_overhead_limit_was_exceeded = false;
  result = Universe::heap()->mem_allocate(size,
                                          is_noref,
                                          false,
                                          &gc_overhead_limit_was_exceeded);
  if (result != NULL) {
    NOT_PRODUCT(Universe::heap()->
      check_for_non_bad_heap_word_value(result, size));
    assert(!HAS_PENDING_EXCEPTION,
           "Unexpected exception, will result in uninitialized storage");
    return result;
  }


  if (!gc_overhead_limit_was_exceeded) {
    // -XX:+HeapDumpOnOutOfMemoryError and -XX:OnOutOfMemoryError support
    report_java_out_of_memory("Java heap space");

    if (JvmtiExport::should_post_resource_exhausted()) {
      JvmtiExport::post_resource_exhausted(
        JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR | JVMTI_RESOURCE_EXHAUSTED_JAVA_HEAP,
        "Java heap space");
    }

    THROW_OOP_0(Universe::out_of_memory_error_java_heap());
  } else {
    // -XX:+HeapDumpOnOutOfMemoryError and -XX:OnOutOfMemoryError support
    report_java_out_of_memory("GC overhead limit exceeded");

    if (JvmtiExport::should_post_resource_exhausted()) {
      JvmtiExport::post_resource_exhausted(
        JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR | JVMTI_RESOURCE_EXHAUSTED_JAVA_HEAP,
        "GC overhead limit exceeded");
    }

    THROW_OOP_0(Universe::out_of_memory_error_gc_overhead_limit());
  }
}

HeapWord* CollectedHeap::common_mem_allocate_init(size_t size, bool is_noref, TRAPS) {
  HeapWord* obj = common_mem_allocate_noinit(size, is_noref, CHECK_NULL);
  init_obj(obj, size);
  return obj;
}

// Need to investigate, do we really want to throw OOM exception here?
HeapWord* CollectedHeap::common_permanent_mem_allocate_noinit(size_t size, TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    NOT_PRODUCT(guarantee(false, "Should not allocate with exception pending"));
    return NULL;  // caller does a CHECK_NULL too
  }

#ifdef ASSERT
  if (CIFireOOMAt > 0 && THREAD->is_Compiler_thread() &&
      ++_fire_out_of_memory_count >= CIFireOOMAt) {
    // For testing of OOM handling in the CI throw an OOM and see how
    // it does.  Historically improper handling of these has resulted
    // in crashes which we really don't want to have in the CI.
    THROW_OOP_0(Universe::out_of_memory_error_perm_gen());
  }
#endif

  HeapWord* result = Universe::heap()->permanent_mem_allocate(size);
  if (result != NULL) {
    NOT_PRODUCT(Universe::heap()->
      check_for_non_bad_heap_word_value(result, size));
    assert(!HAS_PENDING_EXCEPTION,
           "Unexpected exception, will result in uninitialized storage");
    return result;
  }
  // -XX:+HeapDumpOnOutOfMemoryError and -XX:OnOutOfMemoryError support
  report_java_out_of_memory("PermGen space");

  if (JvmtiExport::should_post_resource_exhausted()) {
    JvmtiExport::post_resource_exhausted(
        JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR,
        "PermGen space");
  }

  THROW_OOP_0(Universe::out_of_memory_error_perm_gen());
}

HeapWord* CollectedHeap::common_permanent_mem_allocate_init(size_t size, TRAPS) {
  HeapWord* obj = common_permanent_mem_allocate_noinit(size, CHECK_NULL);
  init_obj(obj, size);
  return obj;
}

HeapWord* CollectedHeap::allocate_from_tlab(Thread* thread, size_t size) {
  assert(UseTLAB, "should use UseTLAB");

  HeapWord* obj = thread->tlab().allocate(size);
  if (obj != NULL) {
    return obj;
  }
  // Otherwise...
  return allocate_from_tlab_slow(thread, size);
}

void CollectedHeap::init_obj(HeapWord* obj, size_t size) {
  assert(obj != NULL, "cannot initialize NULL object");
  const size_t hs = oopDesc::header_size();
  assert(size >= hs, "unexpected object size");
  ((oop)obj)->set_klass_gap(0);
  Copy::fill_to_aligned_words(obj + hs, size - hs);
}

oop CollectedHeap::obj_allocate(KlassHandle klass, int size, TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = common_mem_allocate_init(size, false, CHECK_NULL);
  post_allocation_setup_obj(klass, obj, size);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

oop CollectedHeap::array_allocate(KlassHandle klass,
                                  int size,
                                  int length,
                                  TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = common_mem_allocate_init(size, false, CHECK_NULL);
  post_allocation_setup_array(klass, obj, size, length);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

oop CollectedHeap::large_typearray_allocate(KlassHandle klass,
                                            int size,
                                            int length,
                                            TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = common_mem_allocate_init(size, true, CHECK_NULL);
  post_allocation_setup_array(klass, obj, size, length);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

oop CollectedHeap::permanent_obj_allocate(KlassHandle klass, int size, TRAPS) {
  oop obj = permanent_obj_allocate_no_klass_install(klass, size, CHECK_NULL);
  post_allocation_install_obj_klass(klass, obj, size);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value((HeapWord*) obj,
                                                              size));
  return obj;
}

oop CollectedHeap::permanent_obj_allocate_no_klass_install(KlassHandle klass,
                                                           int size,
                                                           TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = common_permanent_mem_allocate_init(size, CHECK_NULL);
  post_allocation_setup_no_klass_install(klass, obj, size);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

oop CollectedHeap::permanent_array_allocate(KlassHandle klass,
                                            int size,
                                            int length,
                                            TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = common_permanent_mem_allocate_init(size, CHECK_NULL);
  post_allocation_setup_array(klass, obj, size, length);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

// Returns "TRUE" if "p" is a method oop in the
// current heap with high probability. NOTE: The main
// current consumers of this interface are Forte::
// and ThreadProfiler::. In these cases, the
// interpreter frame from which "p" came, may be
// under construction when sampled asynchronously, so
// the clients want to check that it represents a
// valid method before using it. Nonetheless since
// the clients do not typically lock out GC, the
// predicate is_valid_method() is not stable, so
// it is possible that by the time "p" is used, it
// is no longer valid.
inline bool CollectedHeap::is_valid_method(oop p) const {
  return
    p != NULL &&

    // Check whether it is aligned at a HeapWord boundary.
    Space::is_aligned(p) &&

    // Check whether "method" is in the allocated part of the
    // permanent generation -- this needs to be checked before
    // p->klass() below to avoid a SEGV (but see below
    // for a potential window of vulnerability).
    is_permanent((void*)p) &&

    // See if GC is active; however, there is still an
    // apparently unavoidable window after this call
    // and before the client of this interface uses "p".
    // If the client chooses not to lock out GC, then
    // it's a risk the client must accept.
    !is_gc_active() &&

    // Check that p is a methodOop.
    p->klass() == Universe::methodKlassObj();
}


#ifndef PRODUCT

inline bool
CollectedHeap::promotion_should_fail(volatile size_t* count) {
  // Access to count is not atomic; the value does not have to be exact.
  if (PromotionFailureALot) {
    const size_t gc_num = total_collections();
    const size_t elapsed_gcs = gc_num - _promotion_failure_alot_gc_number;
    if (elapsed_gcs >= PromotionFailureALotInterval) {
      // Test for unsigned arithmetic wrap-around.
      if (++*count >= PromotionFailureALotCount) {
        *count = 0;
        return true;
      }
    }
  }
  return false;
}

inline bool CollectedHeap::promotion_should_fail() {
  return promotion_should_fail(&_promotion_failure_alot_count);
}

inline void CollectedHeap::reset_promotion_should_fail(volatile size_t* count) {
  if (PromotionFailureALot) {
    _promotion_failure_alot_gc_number = total_collections();
    *count = 0;
  }
}

inline void CollectedHeap::reset_promotion_should_fail() {
  reset_promotion_should_fail(&_promotion_failure_alot_count);
}
#endif  // #ifndef PRODUCT

#endif // SHARE_VM_GC_INTERFACE_COLLECTEDHEAP_INLINE_HPP

/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_COLLECTEDHEAP_INLINE_HPP
#define SHARE_VM_GC_SHARED_COLLECTEDHEAP_INLINE_HPP

#include "classfile/javaClasses.hpp"
#include "gc/shared/allocTracer.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/threadLocalAllocBuffer.inline.hpp"
#include "memory/universe.hpp"
#include "oops/arrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "services/lowMemoryDetector.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"

// Inline allocation implementations.

void CollectedHeap::post_allocation_setup_common(Klass* klass,
                                                 HeapWord* obj_ptr) {
  post_allocation_setup_no_klass_install(klass, obj_ptr);
  oop obj = (oop)obj_ptr;
#if (INCLUDE_G1GC || INCLUDE_CMSGC)
  // Need a release store to ensure array/class length, mark word, and
  // object zeroing are visible before setting the klass non-NULL, for
  // concurrent collectors.
  obj->release_set_klass(klass);
#else
  obj->set_klass(klass);
#endif
}

void CollectedHeap::post_allocation_setup_no_klass_install(Klass* klass,
                                                           HeapWord* obj_ptr) {
  oop obj = (oop)obj_ptr;

  assert(obj != NULL, "NULL object pointer");
  if (UseBiasedLocking && (klass != NULL)) {
    obj->set_mark_raw(klass->prototype_header());
  } else {
    // May be bootstrapping
    obj->set_mark_raw(markOopDesc::prototype());
  }
}

// Support for jvmti and dtrace
inline void post_allocation_notify(Klass* klass, oop obj, int size) {
  // support low memory notifications (no-op if not enabled)
  LowMemoryDetector::detect_low_memory_for_collected_pools();

  // support for JVMTI VMObjectAlloc event (no-op if not enabled)
  JvmtiExport::vm_object_alloc_event_collector(obj);

  if (DTraceAllocProbes) {
    // support for Dtrace object alloc event (no-op most of the time)
    if (klass != NULL && klass->name() != NULL) {
      SharedRuntime::dtrace_object_alloc(obj, size);
    }
  }
}

void CollectedHeap::post_allocation_setup_obj(Klass* klass,
                                              HeapWord* obj_ptr,
                                              int size) {
  post_allocation_setup_common(klass, obj_ptr);
  oop obj = (oop)obj_ptr;
  assert(Universe::is_bootstrapping() ||
         !obj->is_array(), "must not be an array");
  // notify jvmti and dtrace
  post_allocation_notify(klass, obj, size);
}

void CollectedHeap::post_allocation_setup_class(Klass* klass,
                                                HeapWord* obj_ptr,
                                                int size) {
  // Set oop_size field before setting the _klass field because a
  // non-NULL _klass field indicates that the object is parsable by
  // concurrent GC.
  oop new_cls = (oop)obj_ptr;
  assert(size > 0, "oop_size must be positive.");
  java_lang_Class::set_oop_size(new_cls, size);
  post_allocation_setup_common(klass, obj_ptr);
  assert(Universe::is_bootstrapping() ||
         !new_cls->is_array(), "must not be an array");
  // notify jvmti and dtrace
  post_allocation_notify(klass, new_cls, size);
}

void CollectedHeap::post_allocation_setup_array(Klass* klass,
                                                HeapWord* obj_ptr,
                                                int length) {
  // Set array length before setting the _klass field because a
  // non-NULL klass field indicates that the object is parsable by
  // concurrent GC.
  assert(length >= 0, "length should be non-negative");
  ((arrayOop)obj_ptr)->set_length(length);
  post_allocation_setup_common(klass, obj_ptr);
  oop new_obj = (oop)obj_ptr;
  assert(new_obj->is_array(), "must be an array");
  // notify jvmti and dtrace (must be after length is set for dtrace)
  post_allocation_notify(klass, new_obj, new_obj->size());
}

HeapWord* CollectedHeap::common_mem_allocate_noinit(Klass* klass, size_t size, TRAPS) {

  // Clear unhandled oops for memory allocation.  Memory allocation might
  // not take out a lock if from tlab, so clear here.
  CHECK_UNHANDLED_OOPS_ONLY(THREAD->clear_unhandled_oops();)

  if (HAS_PENDING_EXCEPTION) {
    NOT_PRODUCT(guarantee(false, "Should not allocate with exception pending"));
    return NULL;  // caller does a CHECK_0 too
  }

  bool gc_overhead_limit_was_exceeded = false;
  CollectedHeap* heap = Universe::heap();
  HeapWord* result = heap->obj_allocate_raw(klass, size, &gc_overhead_limit_was_exceeded, THREAD);

  if (result != NULL) {
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

HeapWord* CollectedHeap::common_mem_allocate_init(Klass* klass, size_t size, TRAPS) {
  HeapWord* obj = common_mem_allocate_noinit(klass, size, CHECK_NULL);
  init_obj(obj, size);
  return obj;
}

HeapWord* CollectedHeap::allocate_from_tlab(Klass* klass, size_t size, TRAPS) {
  assert(UseTLAB, "should use UseTLAB");

  HeapWord* obj = THREAD->tlab().allocate(size);
  if (obj != NULL) {
    return obj;
  }
  // Otherwise...
  obj = allocate_from_tlab_slow(klass, size, THREAD);
  assert(obj == NULL || !HAS_PENDING_EXCEPTION,
         "Unexpected exception, will result in uninitialized storage");
  return obj;
}

HeapWord* CollectedHeap::allocate_outside_tlab(Klass* klass, size_t size,
                                               bool* gc_overhead_limit_was_exceeded, TRAPS) {
  HeapWord* result = Universe::heap()->mem_allocate(size, gc_overhead_limit_was_exceeded);
  if (result == NULL) {
    return result;
  }

  NOT_PRODUCT(Universe::heap()->check_for_non_bad_heap_word_value(result, size));
  assert(!HAS_PENDING_EXCEPTION,
         "Unexpected exception, will result in uninitialized storage");
  size_t size_in_bytes = size * HeapWordSize;
  THREAD->incr_allocated_bytes(size_in_bytes);

  AllocTracer::send_allocation_outside_tlab(klass, result, size_in_bytes, THREAD);

  if (ThreadHeapSampler::enabled()) {
    THREAD->heap_sampler().check_for_sampling(result, size_in_bytes);
  }

  return result;
}

void CollectedHeap::init_obj(HeapWord* obj, size_t size) {
  assert(obj != NULL, "cannot initialize NULL object");
  const size_t hs = oopDesc::header_size();
  assert(size >= hs, "unexpected object size");
  ((oop)obj)->set_klass_gap(0);
  Copy::fill_to_aligned_words(obj + hs, size - hs);
}

HeapWord* CollectedHeap::common_allocate_memory(Klass* klass, int size,
                                                void (*post_setup)(Klass*, HeapWord*, int),
                                                int size_for_post, bool init_memory,
                                                TRAPS) {
  HeapWord* obj;
  if (init_memory) {
    obj = common_mem_allocate_init(klass, size, CHECK_NULL);
  } else {
    obj = common_mem_allocate_noinit(klass, size, CHECK_NULL);
  }
  post_setup(klass, obj, size_for_post);
  return obj;
}

HeapWord* CollectedHeap::allocate_memory(Klass* klass, int size,
                                         void (*post_setup)(Klass*, HeapWord*, int),
                                         int size_for_post, bool init_memory,
                                         TRAPS) {
  HeapWord* obj;

  assert(JavaThread::current()->heap_sampler().add_sampling_collector(),
         "Should never return false.");

  if (JvmtiExport::should_post_sampled_object_alloc()) {
    HandleMark hm(THREAD);
    Handle obj_h;
    {
      JvmtiSampledObjectAllocEventCollector collector;
      obj = common_allocate_memory(klass, size, post_setup, size_for_post,
                                   init_memory, CHECK_NULL);
      // If we want to be sampling, protect the allocated object with a Handle
      // before doing the callback. The callback is done in the destructor of
      // the JvmtiSampledObjectAllocEventCollector.
      obj_h = Handle(THREAD, (oop) obj);
    }
    obj = (HeapWord*) obj_h();
  } else {
    obj = common_allocate_memory(klass, size, post_setup, size_for_post,
                                 init_memory, CHECK_NULL);
  }

  assert(JavaThread::current()->heap_sampler().remove_sampling_collector(),
         "Should never return false.");
  return obj;
}

oop CollectedHeap::obj_allocate(Klass* klass, int size, TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = allocate_memory(klass, size, post_allocation_setup_obj,
                                  size, true, CHECK_NULL);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

oop CollectedHeap::class_allocate(Klass* klass, int size, TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = allocate_memory(klass, size, post_allocation_setup_class,
                                  size, true, CHECK_NULL);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

oop CollectedHeap::array_allocate(Klass* klass,
                                  int size,
                                  int length,
                                  TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");
  HeapWord* obj = allocate_memory(klass, size, post_allocation_setup_array,
                                  length, true, CHECK_NULL);
  NOT_PRODUCT(Universe::heap()->check_for_bad_heap_word_value(obj, size));
  return (oop)obj;
}

oop CollectedHeap::array_allocate_nozero(Klass* klass,
                                         int size,
                                         int length,
                                         TRAPS) {
  debug_only(check_for_valid_allocation_state());
  assert(!Universe::heap()->is_gc_active(), "Allocation during gc not allowed");
  assert(size >= 0, "int won't convert to size_t");

  HeapWord* obj = allocate_memory(klass, size, post_allocation_setup_array,
                                  length, false, CHECK_NULL);
#ifndef PRODUCT
  const size_t hs = oopDesc::header_size()+1;
  Universe::heap()->check_for_non_bad_heap_word_value(obj+hs, size-hs);
#endif
  return (oop)obj;
}

inline HeapWord* CollectedHeap::align_allocation_or_fail(HeapWord* addr,
                                                         HeapWord* end,
                                                         unsigned short alignment_in_bytes) {
  if (alignment_in_bytes <= ObjectAlignmentInBytes) {
    return addr;
  }

  assert(is_aligned(addr, HeapWordSize),
         "Address " PTR_FORMAT " is not properly aligned.", p2i(addr));
  assert(is_aligned(alignment_in_bytes, HeapWordSize),
         "Alignment size %u is incorrect.", alignment_in_bytes);

  HeapWord* new_addr = align_up(addr, alignment_in_bytes);
  size_t padding = pointer_delta(new_addr, addr);

  if (padding == 0) {
    return addr;
  }

  if (padding < CollectedHeap::min_fill_size()) {
    padding += alignment_in_bytes / HeapWordSize;
    assert(padding >= CollectedHeap::min_fill_size(),
           "alignment_in_bytes %u is expect to be larger "
           "than the minimum object size", alignment_in_bytes);
    new_addr = addr + padding;
  }

  assert(new_addr > addr, "Unexpected arithmetic overflow "
         PTR_FORMAT " not greater than " PTR_FORMAT, p2i(new_addr), p2i(addr));
  if(new_addr < end) {
    CollectedHeap::fill_with_object(addr, padding);
    return new_addr;
  } else {
    return NULL;
  }
}

#endif // SHARE_VM_GC_SHARED_COLLECTEDHEAP_INLINE_HPP

/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "code/codeHeapState.hpp"
#include "code/compiledIC.hpp"
#include "code/dependencies.hpp"
#include "code/dependencyContext.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "compiler/compilationPolicy.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "jfr/jfrEvents.hpp"
#include "jvm_io.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/icache.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/vmThread.hpp"
#include "sanitizers/leak.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#include "utilities/vmError.hpp"
#include "utilities/xmlstream.hpp"
#ifdef COMPILER1
#include "c1/c1_Compilation.hpp"
#include "c1/c1_Compiler.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2compiler.hpp"
#include "opto/compile.hpp"
#include "opto/node.hpp"
#endif

// Helper class for printing in CodeCache
class CodeBlob_sizes {
 private:
  int count;
  int total_size;
  int header_size;
  int code_size;
  int stub_size;
  int relocation_size;
  int scopes_oop_size;
  int scopes_metadata_size;
  int scopes_data_size;
  int scopes_pcs_size;

 public:
  CodeBlob_sizes() {
    count            = 0;
    total_size       = 0;
    header_size      = 0;
    code_size        = 0;
    stub_size        = 0;
    relocation_size  = 0;
    scopes_oop_size  = 0;
    scopes_metadata_size  = 0;
    scopes_data_size = 0;
    scopes_pcs_size  = 0;
  }

  int total() const                              { return total_size; }
  bool is_empty() const                          { return count == 0; }

  void print(const char* title) const {
    if (is_empty()) {
      tty->print_cr(" #%d %s = %dK",
                    count,
                    title,
                    total()                 / (int)K);
    } else {
      tty->print_cr(" #%d %s = %dK (hdr %dK %d%%, loc %dK %d%%, code %dK %d%%, stub %dK %d%%, [oops %dK %d%%, metadata %dK %d%%, data %dK %d%%, pcs %dK %d%%])",
                    count,
                    title,
                    total()                 / (int)K,
                    header_size             / (int)K,
                    header_size             * 100 / total_size,
                    relocation_size         / (int)K,
                    relocation_size         * 100 / total_size,
                    code_size               / (int)K,
                    code_size               * 100 / total_size,
                    stub_size               / (int)K,
                    stub_size               * 100 / total_size,
                    scopes_oop_size         / (int)K,
                    scopes_oop_size         * 100 / total_size,
                    scopes_metadata_size    / (int)K,
                    scopes_metadata_size    * 100 / total_size,
                    scopes_data_size        / (int)K,
                    scopes_data_size        * 100 / total_size,
                    scopes_pcs_size         / (int)K,
                    scopes_pcs_size         * 100 / total_size);
    }
  }

  void add(CodeBlob* cb) {
    count++;
    total_size       += cb->size();
    header_size      += cb->header_size();
    relocation_size  += cb->relocation_size();
    if (cb->is_nmethod()) {
      nmethod* nm = cb->as_nmethod_or_null();
      code_size        += nm->insts_size();
      stub_size        += nm->stub_size();

      scopes_oop_size  += nm->oops_size();
      scopes_metadata_size  += nm->metadata_size();
      scopes_data_size += nm->scopes_data_size();
      scopes_pcs_size  += nm->scopes_pcs_size();
    } else {
      code_size        += cb->code_size();
    }
  }
};

// Iterate over all CodeHeaps
#define FOR_ALL_HEAPS(heap) for (GrowableArrayIterator<CodeHeap*> heap = _heaps->begin(); heap != _heaps->end(); ++heap)
#define FOR_ALL_ALLOCABLE_HEAPS(heap) for (GrowableArrayIterator<CodeHeap*> heap = _allocable_heaps->begin(); heap != _allocable_heaps->end(); ++heap)

// Iterate over all CodeBlobs (cb) on the given CodeHeap
#define FOR_ALL_BLOBS(cb, heap) for (CodeBlob* cb = first_blob(heap); cb != nullptr; cb = next_blob(heap, cb))

address CodeCache::_low_bound = 0;
address CodeCache::_high_bound = 0;
volatile int CodeCache::_number_of_nmethods_with_dependencies = 0;
ExceptionCache* volatile CodeCache::_exception_cache_purge_list = nullptr;

// Initialize arrays of CodeHeap subsets
GrowableArray<CodeHeap*>* CodeCache::_heaps = new(mtCode) GrowableArray<CodeHeap*> (static_cast<int>(CodeBlobType::All), mtCode);
GrowableArray<CodeHeap*>* CodeCache::_nmethod_heaps = new(mtCode) GrowableArray<CodeHeap*> (static_cast<int>(CodeBlobType::All), mtCode);
GrowableArray<CodeHeap*>* CodeCache::_allocable_heaps = new(mtCode) GrowableArray<CodeHeap*> (static_cast<int>(CodeBlobType::All), mtCode);

static void check_min_size(const char* codeheap, size_t size, size_t required_size) {
  if (size < required_size) {
    log_debug(codecache)("Code heap (%s) size " SIZE_FORMAT "K below required minimal size " SIZE_FORMAT "K",
                         codeheap, size/K, required_size/K);
    err_msg title("Not enough space in %s to run VM", codeheap);
    err_msg message(SIZE_FORMAT "K < " SIZE_FORMAT "K", size/K, required_size/K);
    vm_exit_during_initialization(title, message);
  }
}

struct CodeHeapInfo {
  size_t size;
  bool set;
  bool enabled;
};

static void set_size_of_unset_code_heap(CodeHeapInfo* heap, size_t available_size, size_t used_size, size_t min_size) {
  assert(!heap->set, "sanity");
  heap->size = (available_size > (used_size + min_size)) ? (available_size - used_size) : min_size;
}

void CodeCache::initialize_heaps() {

  CodeHeapInfo non_nmethod = {NonNMethodCodeHeapSize, FLAG_IS_CMDLINE(NonNMethodCodeHeapSize), true};
  CodeHeapInfo profiled = {ProfiledCodeHeapSize, FLAG_IS_CMDLINE(ProfiledCodeHeapSize), true};
  CodeHeapInfo non_profiled = {NonProfiledCodeHeapSize, FLAG_IS_CMDLINE(NonProfiledCodeHeapSize), true};

  const bool cache_size_set   = FLAG_IS_CMDLINE(ReservedCodeCacheSize);
  const size_t ps             = page_size(false, 8);
  const size_t min_size       = MAX2(os::vm_allocation_granularity(), ps);
  const size_t min_cache_size = CodeCacheMinimumUseSpace DEBUG_ONLY(* 3); // Make sure we have enough space for VM internal code
  size_t cache_size           = align_up(ReservedCodeCacheSize, min_size);

  // Prerequisites
  if (!heap_available(CodeBlobType::MethodProfiled)) {
    // For compatibility reasons, disabled tiered compilation overrides
    // segment size even if it is set explicitly.
    non_profiled.size += profiled.size;
    // Profiled code heap is not available, forcibly set size to 0
    profiled.size = 0;
    profiled.set = true;
    profiled.enabled = false;
  }

  assert(heap_available(CodeBlobType::MethodNonProfiled), "MethodNonProfiled heap is always available for segmented code heap");

  size_t compiler_buffer_size = 0;
  COMPILER1_PRESENT(compiler_buffer_size += CompilationPolicy::c1_count() * Compiler::code_buffer_size());
  COMPILER2_PRESENT(compiler_buffer_size += CompilationPolicy::c2_count() * C2Compiler::initial_code_buffer_size());

  if (!non_nmethod.set) {
    non_nmethod.size += compiler_buffer_size;
  }

  if (!profiled.set && !non_profiled.set) {
    non_profiled.size = profiled.size = (cache_size > non_nmethod.size + 2 * min_size) ?
                                        (cache_size - non_nmethod.size) / 2 : min_size;
  }

  if (profiled.set && !non_profiled.set) {
    set_size_of_unset_code_heap(&non_profiled, cache_size, non_nmethod.size + profiled.size, min_size);
  }

  if (!profiled.set && non_profiled.set) {
    set_size_of_unset_code_heap(&profiled, cache_size, non_nmethod.size + non_profiled.size, min_size);
  }

  // Compatibility.
  size_t non_nmethod_min_size = min_cache_size + compiler_buffer_size;
  if (!non_nmethod.set && profiled.set && non_profiled.set) {
    set_size_of_unset_code_heap(&non_nmethod, cache_size, profiled.size + non_profiled.size, non_nmethod_min_size);
  }

  size_t total = non_nmethod.size + profiled.size + non_profiled.size;
  if (total != cache_size && !cache_size_set) {
    log_info(codecache)("ReservedCodeCache size " SIZE_FORMAT "K changed to total segments size NonNMethod "
                        SIZE_FORMAT "K NonProfiled " SIZE_FORMAT "K Profiled " SIZE_FORMAT "K = " SIZE_FORMAT "K",
                        cache_size/K, non_nmethod.size/K, non_profiled.size/K, profiled.size/K, total/K);
    // Adjust ReservedCodeCacheSize as necessary because it was not set explicitly
    cache_size = total;
  }

  log_debug(codecache)("Initializing code heaps ReservedCodeCache " SIZE_FORMAT "K NonNMethod " SIZE_FORMAT "K"
                       " NonProfiled " SIZE_FORMAT "K Profiled " SIZE_FORMAT "K",
                       cache_size/K, non_nmethod.size/K, non_profiled.size/K, profiled.size/K);

  // Validation
  // Check minimal required sizes
  check_min_size("non-nmethod code heap", non_nmethod.size, non_nmethod_min_size);
  if (profiled.enabled) {
    check_min_size("profiled code heap", profiled.size, min_size);
  }
  if (non_profiled.enabled) { // non_profiled.enabled is always ON for segmented code heap, leave it checked for clarity
    check_min_size("non-profiled code heap", non_profiled.size, min_size);
  }
  if (cache_size_set) {
    check_min_size("reserved code cache", cache_size, min_cache_size);
  }

  // ReservedCodeCacheSize was set explicitly, so report an error and abort if it doesn't match the segment sizes
  if (total != cache_size && cache_size_set) {
    err_msg message("NonNMethodCodeHeapSize (" SIZE_FORMAT "K)", non_nmethod.size/K);
    if (profiled.enabled) {
      message.append(" + ProfiledCodeHeapSize (" SIZE_FORMAT "K)", profiled.size/K);
    }
    if (non_profiled.enabled) {
      message.append(" + NonProfiledCodeHeapSize (" SIZE_FORMAT "K)", non_profiled.size/K);
    }
    message.append(" = " SIZE_FORMAT "K", total/K);
    message.append((total > cache_size) ? " is greater than " : " is less than ");
    message.append("ReservedCodeCacheSize (" SIZE_FORMAT "K).", cache_size/K);

    vm_exit_during_initialization("Invalid code heap sizes", message);
  }

  // Compatibility. Print warning if using large pages but not able to use the size given
  if (UseLargePages) {
    const size_t lg_ps = page_size(false, 1);
    if (ps < lg_ps) {
      log_warning(codecache)("Code cache size too small for " PROPERFMT " pages. "
                             "Reverting to smaller page size (" PROPERFMT ").",
                             PROPERFMTARGS(lg_ps), PROPERFMTARGS(ps));
    }
  }

  // Note: if large page support is enabled, min_size is at least the large
  // page size. This ensures that the code cache is covered by large pages.
  non_profiled.size += non_nmethod.size & alignment_mask(min_size);
  non_profiled.size += profiled.size & alignment_mask(min_size);
  non_nmethod.size = align_down(non_nmethod.size, min_size);
  profiled.size = align_down(profiled.size, min_size);
  non_profiled.size = align_down(non_profiled.size, min_size);

  FLAG_SET_ERGO(NonNMethodCodeHeapSize, non_nmethod.size);
  FLAG_SET_ERGO(ProfiledCodeHeapSize, profiled.size);
  FLAG_SET_ERGO(NonProfiledCodeHeapSize, non_profiled.size);
  FLAG_SET_ERGO(ReservedCodeCacheSize, cache_size);

  ReservedCodeSpace rs = reserve_heap_memory(cache_size, ps);

  // Register CodeHeaps with LSan as we sometimes embed pointers to malloc memory.
  LSAN_REGISTER_ROOT_REGION(rs.base(), rs.size());

  size_t offset = 0;
  if (profiled.enabled) {
    ReservedSpace profiled_space = rs.partition(offset, profiled.size);
    offset += profiled.size;
    // Tier 2 and tier 3 (profiled) methods
    add_heap(profiled_space, "CodeHeap 'profiled nmethods'", CodeBlobType::MethodProfiled);
  }

  ReservedSpace non_method_space = rs.partition(offset, non_nmethod.size);
  offset += non_nmethod.size;
  // Non-nmethods (stubs, adapters, ...)
  add_heap(non_method_space, "CodeHeap 'non-nmethods'", CodeBlobType::NonNMethod);

  if (non_profiled.enabled) {
    ReservedSpace non_profiled_space  = rs.partition(offset, non_profiled.size);
    // Tier 1 and tier 4 (non-profiled) methods and native methods
    add_heap(non_profiled_space, "CodeHeap 'non-profiled nmethods'", CodeBlobType::MethodNonProfiled);
  }
}

size_t CodeCache::page_size(bool aligned, size_t min_pages) {
  return aligned ? os::page_size_for_region_aligned(ReservedCodeCacheSize, min_pages) :
                   os::page_size_for_region_unaligned(ReservedCodeCacheSize, min_pages);
}

ReservedCodeSpace CodeCache::reserve_heap_memory(size_t size, size_t rs_ps) {
  // Align and reserve space for code cache
  const size_t rs_align = MAX2(rs_ps, os::vm_allocation_granularity());
  const size_t rs_size = align_up(size, rs_align);
  ReservedCodeSpace rs(rs_size, rs_align, rs_ps);
  if (!rs.is_reserved()) {
    vm_exit_during_initialization(err_msg("Could not reserve enough space for code cache (" SIZE_FORMAT "K)",
                                          rs_size/K));
  }

  // Initialize bounds
  _low_bound = (address)rs.base();
  _high_bound = _low_bound + rs.size();
  return rs;
}

// Heaps available for allocation
bool CodeCache::heap_available(CodeBlobType code_blob_type) {
  if (!SegmentedCodeCache) {
    // No segmentation: use a single code heap
    return (code_blob_type == CodeBlobType::All);
  } else if (CompilerConfig::is_interpreter_only()) {
    // Interpreter only: we don't need any method code heaps
    return (code_blob_type == CodeBlobType::NonNMethod);
  } else if (CompilerConfig::is_c1_profiling()) {
    // Tiered compilation: use all code heaps
    return (code_blob_type < CodeBlobType::All);
  } else {
    // No TieredCompilation: we only need the non-nmethod and non-profiled code heap
    return (code_blob_type == CodeBlobType::NonNMethod) ||
           (code_blob_type == CodeBlobType::MethodNonProfiled);
  }
}

const char* CodeCache::get_code_heap_flag_name(CodeBlobType code_blob_type) {
  switch(code_blob_type) {
  case CodeBlobType::NonNMethod:
    return "NonNMethodCodeHeapSize";
    break;
  case CodeBlobType::MethodNonProfiled:
    return "NonProfiledCodeHeapSize";
    break;
  case CodeBlobType::MethodProfiled:
    return "ProfiledCodeHeapSize";
    break;
  default:
    ShouldNotReachHere();
    return nullptr;
  }
}

int CodeCache::code_heap_compare(CodeHeap* const &lhs, CodeHeap* const &rhs) {
  if (lhs->code_blob_type() == rhs->code_blob_type()) {
    return (lhs > rhs) ? 1 : ((lhs < rhs) ? -1 : 0);
  } else {
    return static_cast<int>(lhs->code_blob_type()) - static_cast<int>(rhs->code_blob_type());
  }
}

void CodeCache::add_heap(CodeHeap* heap) {
  assert(!Universe::is_fully_initialized(), "late heap addition?");

  _heaps->insert_sorted<code_heap_compare>(heap);

  CodeBlobType type = heap->code_blob_type();
  if (code_blob_type_accepts_nmethod(type)) {
    _nmethod_heaps->insert_sorted<code_heap_compare>(heap);
  }
  if (code_blob_type_accepts_allocable(type)) {
    _allocable_heaps->insert_sorted<code_heap_compare>(heap);
  }
}

void CodeCache::add_heap(ReservedSpace rs, const char* name, CodeBlobType code_blob_type) {
  // Check if heap is needed
  if (!heap_available(code_blob_type)) {
    return;
  }

  // Create CodeHeap
  CodeHeap* heap = new CodeHeap(name, code_blob_type);
  add_heap(heap);

  // Reserve Space
  size_t size_initial = MIN2((size_t)InitialCodeCacheSize, rs.size());
  size_initial = align_up(size_initial, os::vm_page_size());
  if (!heap->reserve(rs, size_initial, CodeCacheSegmentSize)) {
    vm_exit_during_initialization(err_msg("Could not reserve enough space in %s (" SIZE_FORMAT "K)",
                                          heap->name(), size_initial/K));
  }

  // Register the CodeHeap
  MemoryService::add_code_heap_memory_pool(heap, name);
}

CodeHeap* CodeCache::get_code_heap_containing(void* start) {
  FOR_ALL_HEAPS(heap) {
    if ((*heap)->contains(start)) {
      return *heap;
    }
  }
  return nullptr;
}

CodeHeap* CodeCache::get_code_heap(const void* cb) {
  assert(cb != nullptr, "CodeBlob is null");
  FOR_ALL_HEAPS(heap) {
    if ((*heap)->contains(cb)) {
      return *heap;
    }
  }
  ShouldNotReachHere();
  return nullptr;
}

CodeHeap* CodeCache::get_code_heap(CodeBlobType code_blob_type) {
  FOR_ALL_HEAPS(heap) {
    if ((*heap)->accepts(code_blob_type)) {
      return *heap;
    }
  }
  return nullptr;
}

CodeBlob* CodeCache::first_blob(CodeHeap* heap) {
  assert_locked_or_safepoint(CodeCache_lock);
  assert(heap != nullptr, "heap is null");
  return (CodeBlob*)heap->first();
}

CodeBlob* CodeCache::first_blob(CodeBlobType code_blob_type) {
  if (heap_available(code_blob_type)) {
    return first_blob(get_code_heap(code_blob_type));
  } else {
    return nullptr;
  }
}

CodeBlob* CodeCache::next_blob(CodeHeap* heap, CodeBlob* cb) {
  assert_locked_or_safepoint(CodeCache_lock);
  assert(heap != nullptr, "heap is null");
  return (CodeBlob*)heap->next(cb);
}

/**
 * Do not seize the CodeCache lock here--if the caller has not
 * already done so, we are going to lose bigtime, since the code
 * cache will contain a garbage CodeBlob until the caller can
 * run the constructor for the CodeBlob subclass he is busy
 * instantiating.
 */
CodeBlob* CodeCache::allocate(uint size, CodeBlobType code_blob_type, bool handle_alloc_failure, CodeBlobType orig_code_blob_type) {
  assert_locked_or_safepoint(CodeCache_lock);
  assert(size > 0, "Code cache allocation request must be > 0");
  if (size == 0) {
    return nullptr;
  }
  CodeBlob* cb = nullptr;

  // Get CodeHeap for the given CodeBlobType
  CodeHeap* heap = get_code_heap(code_blob_type);
  assert(heap != nullptr, "heap is null");

  while (true) {
    cb = (CodeBlob*)heap->allocate(size);
    if (cb != nullptr) break;
    if (!heap->expand_by(CodeCacheExpansionSize)) {
      // Save original type for error reporting
      if (orig_code_blob_type == CodeBlobType::All) {
        orig_code_blob_type = code_blob_type;
      }
      // Expansion failed
      if (SegmentedCodeCache) {
        // Fallback solution: Try to store code in another code heap.
        // NonNMethod -> MethodNonProfiled -> MethodProfiled (-> MethodNonProfiled)
        CodeBlobType type = code_blob_type;
        switch (type) {
        case CodeBlobType::NonNMethod:
          type = CodeBlobType::MethodNonProfiled;
          break;
        case CodeBlobType::MethodNonProfiled:
          type = CodeBlobType::MethodProfiled;
          break;
        case CodeBlobType::MethodProfiled:
          // Avoid loop if we already tried that code heap
          if (type == orig_code_blob_type) {
            type = CodeBlobType::MethodNonProfiled;
          }
          break;
        default:
          break;
        }
        if (type != code_blob_type && type != orig_code_blob_type && heap_available(type)) {
          if (PrintCodeCacheExtension) {
            tty->print_cr("Extension of %s failed. Trying to allocate in %s.",
                          heap->name(), get_code_heap(type)->name());
          }
          return allocate(size, type, handle_alloc_failure, orig_code_blob_type);
        }
      }
      if (handle_alloc_failure) {
        MutexUnlocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
        CompileBroker::handle_full_code_cache(orig_code_blob_type);
      }
      return nullptr;
    } else {
      OrderAccess::release(); // ensure heap expansion is visible to an asynchronous observer (e.g. CodeHeapPool::get_memory_usage())
    }
    if (PrintCodeCacheExtension) {
      ResourceMark rm;
      if (_nmethod_heaps->length() >= 1) {
        tty->print("%s", heap->name());
      } else {
        tty->print("CodeCache");
      }
      tty->print_cr(" extended to [" INTPTR_FORMAT ", " INTPTR_FORMAT "] (" SSIZE_FORMAT " bytes)",
                    (intptr_t)heap->low_boundary(), (intptr_t)heap->high(),
                    (address)heap->high() - (address)heap->low_boundary());
    }
  }
  print_trace("allocation", cb, size);
  return cb;
}

void CodeCache::free(CodeBlob* cb) {
  assert_locked_or_safepoint(CodeCache_lock);
  CodeHeap* heap = get_code_heap(cb);
  print_trace("free", cb);
  if (cb->is_nmethod()) {
    heap->set_nmethod_count(heap->nmethod_count() - 1);
    if (((nmethod *)cb)->has_dependencies()) {
      Atomic::dec(&_number_of_nmethods_with_dependencies);
    }
  }
  if (cb->is_adapter_blob()) {
    heap->set_adapter_count(heap->adapter_count() - 1);
  }

  cb->~CodeBlob();
  // Get heap for given CodeBlob and deallocate
  heap->deallocate(cb);

  assert(heap->blob_count() >= 0, "sanity check");
}

void CodeCache::free_unused_tail(CodeBlob* cb, size_t used) {
  assert_locked_or_safepoint(CodeCache_lock);
  guarantee(cb->is_buffer_blob() && strncmp("Interpreter", cb->name(), 11) == 0, "Only possible for interpreter!");
  print_trace("free_unused_tail", cb);

  // We also have to account for the extra space (i.e. header) used by the CodeBlob
  // which provides the memory (see BufferBlob::create() in codeBlob.cpp).
  used += CodeBlob::align_code_offset(cb->header_size());

  // Get heap for given CodeBlob and deallocate its unused tail
  get_code_heap(cb)->deallocate_tail(cb, used);
  // Adjust the sizes of the CodeBlob
  cb->adjust_size(used);
}

void CodeCache::commit(CodeBlob* cb) {
  // this is called by nmethod::nmethod, which must already own CodeCache_lock
  assert_locked_or_safepoint(CodeCache_lock);
  CodeHeap* heap = get_code_heap(cb);
  if (cb->is_nmethod()) {
    heap->set_nmethod_count(heap->nmethod_count() + 1);
    if (((nmethod *)cb)->has_dependencies()) {
      Atomic::inc(&_number_of_nmethods_with_dependencies);
    }
  }
  if (cb->is_adapter_blob()) {
    heap->set_adapter_count(heap->adapter_count() + 1);
  }
}

bool CodeCache::contains(void *p) {
  // S390 uses contains() in current_frame(), which is used before
  // code cache initialization if NativeMemoryTracking=detail is set.
  S390_ONLY(if (_heaps == nullptr) return false;)
  // It should be ok to call contains without holding a lock.
  FOR_ALL_HEAPS(heap) {
    if ((*heap)->contains(p)) {
      return true;
    }
  }
  return false;
}

bool CodeCache::contains(nmethod *nm) {
  return contains((void *)nm);
}

// This method is safe to call without holding the CodeCache_lock. It only depends on the _segmap to contain
// valid indices, which it will always do, as long as the CodeBlob is not in the process of being recycled.
CodeBlob* CodeCache::find_blob(void* start) {
  // NMT can walk the stack before code cache is created
  if (_heaps != nullptr) {
    CodeHeap* heap = get_code_heap_containing(start);
    if (heap != nullptr) {
      return heap->find_blob(start);
    }
  }
  return nullptr;
}

nmethod* CodeCache::find_nmethod(void* start) {
  CodeBlob* cb = find_blob(start);
  assert(cb == nullptr || cb->is_nmethod(), "did not find an nmethod");
  return (nmethod*)cb;
}

void CodeCache::blobs_do(void f(CodeBlob* nm)) {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_HEAPS(heap) {
    FOR_ALL_BLOBS(cb, *heap) {
      f(cb);
    }
  }
}

void CodeCache::nmethods_do(void f(nmethod* nm)) {
  assert_locked_or_safepoint(CodeCache_lock);
  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    f(iter.method());
  }
}

void CodeCache::nmethods_do(NMethodClosure* cl) {
  assert_locked_or_safepoint(CodeCache_lock);
  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    cl->do_nmethod(iter.method());
  }
}

void CodeCache::metadata_do(MetadataClosure* f) {
  assert_locked_or_safepoint(CodeCache_lock);
  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    iter.method()->metadata_do(f);
  }
}

// Calculate the number of GCs after which an nmethod is expected to have been
// used in order to not be classed as cold.
void CodeCache::update_cold_gc_count() {
  if (!MethodFlushing || !UseCodeCacheFlushing || NmethodSweepActivity == 0) {
    // No aging
    return;
  }

  size_t last_used = _last_unloading_used;
  double last_time = _last_unloading_time;

  double time = os::elapsedTime();

  size_t free = unallocated_capacity();
  size_t max = max_capacity();
  size_t used = max - free;
  double gc_interval = time - last_time;

  _unloading_threshold_gc_requested = false;
  _last_unloading_time = time;
  _last_unloading_used = used;

  if (last_time == 0.0) {
    // The first GC doesn't have enough information to make good
    // decisions, so just keep everything afloat
    log_info(codecache)("Unknown code cache pressure; don't age code");
    return;
  }

  if (gc_interval <= 0.0 || last_used >= used) {
    // Dodge corner cases where there is no pressure or negative pressure
    // on the code cache. Just don't unload when this happens.
    _cold_gc_count = INT_MAX;
    log_info(codecache)("No code cache pressure; don't age code");
    return;
  }

  double allocation_rate = (used - last_used) / gc_interval;

  _unloading_allocation_rates.add(allocation_rate);
  _unloading_gc_intervals.add(gc_interval);

  size_t aggressive_sweeping_free_threshold = StartAggressiveSweepingAt / 100.0 * max;
  if (free < aggressive_sweeping_free_threshold) {
    // We are already in the red zone; be very aggressive to avoid disaster
    // But not more aggressive than 2. This ensures that an nmethod must
    // have been unused at least between two GCs to be considered cold still.
    _cold_gc_count = 2;
    log_info(codecache)("Code cache critically low; use aggressive aging");
    return;
  }

  // The code cache has an expected time for cold nmethods to "time out"
  // when they have not been used. The time for nmethods to time out
  // depends on how long we expect we can keep allocating code until
  // aggressive sweeping starts, based on sampled allocation rates.
  double average_gc_interval = _unloading_gc_intervals.avg();
  double average_allocation_rate = _unloading_allocation_rates.avg();
  double time_to_aggressive = ((double)(free - aggressive_sweeping_free_threshold)) / average_allocation_rate;
  double cold_timeout = time_to_aggressive / NmethodSweepActivity;

  // Convert time to GC cycles, and crop at INT_MAX. The reason for
  // that is that the _cold_gc_count will be added to an epoch number
  // and that addition must not overflow, or we can crash the VM.
  // But not more aggressive than 2. This ensures that an nmethod must
  // have been unused at least between two GCs to be considered cold still.
  _cold_gc_count = MAX2(MIN2((uint64_t)(cold_timeout / average_gc_interval), (uint64_t)INT_MAX), (uint64_t)2);

  double used_ratio = double(used) / double(max);
  double last_used_ratio = double(last_used) / double(max);
  log_info(codecache)("Allocation rate: %.3f KB/s, time to aggressive unloading: %.3f s, cold timeout: %.3f s, cold gc count: " UINT64_FORMAT
                      ", used: %.3f MB (%.3f%%), last used: %.3f MB (%.3f%%), gc interval: %.3f s",
                      average_allocation_rate / K, time_to_aggressive, cold_timeout, _cold_gc_count,
                      double(used) / M, used_ratio * 100.0, double(last_used) / M, last_used_ratio * 100.0, average_gc_interval);

}

uint64_t CodeCache::cold_gc_count() {
  return _cold_gc_count;
}

void CodeCache::gc_on_allocation() {
  if (!is_init_completed()) {
    // Let's not heuristically trigger GCs before the JVM is ready for GCs, no matter what
    return;
  }

  size_t free = unallocated_capacity();
  size_t max = max_capacity();
  size_t used = max - free;
  double free_ratio = double(free) / double(max);
  if (free_ratio <= StartAggressiveSweepingAt / 100.0)  {
    // In case the GC is concurrent, we make sure only one thread requests the GC.
    if (Atomic::cmpxchg(&_unloading_threshold_gc_requested, false, true) == false) {
      log_info(codecache)("Triggering aggressive GC due to having only %.3f%% free memory", free_ratio * 100.0);
      Universe::heap()->collect(GCCause::_codecache_GC_aggressive);
    }
    return;
  }

  size_t last_used = _last_unloading_used;
  if (last_used >= used) {
    // No increase since last GC; no need to sweep yet
    return;
  }
  size_t allocated_since_last = used - last_used;
  double allocated_since_last_ratio = double(allocated_since_last) / double(max);
  double threshold = SweeperThreshold / 100.0;
  double used_ratio = double(used) / double(max);
  double last_used_ratio = double(last_used) / double(max);
  if (used_ratio > threshold) {
    // After threshold is reached, scale it by free_ratio so that more aggressive
    // GC is triggered as we approach code cache exhaustion
    threshold *= free_ratio;
  }
  // If code cache has been allocated without any GC at all, let's make sure
  // it is eventually invoked to avoid trouble.
  if (allocated_since_last_ratio > threshold) {
    // In case the GC is concurrent, we make sure only one thread requests the GC.
    if (Atomic::cmpxchg(&_unloading_threshold_gc_requested, false, true) == false) {
      log_info(codecache)("Triggering threshold (%.3f%%) GC due to allocating %.3f%% since last unloading (%.3f%% used -> %.3f%% used)",
                          threshold * 100.0, allocated_since_last_ratio * 100.0, last_used_ratio * 100.0, used_ratio * 100.0);
      Universe::heap()->collect(GCCause::_codecache_GC_threshold);
    }
  }
}

// We initialize the _gc_epoch to 2, because previous_completed_gc_marking_cycle
// subtracts the value by 2, and the type is unsigned. We don't want underflow.
//
// Odd values mean that marking is in progress, and even values mean that no
// marking is currently active.
uint64_t CodeCache::_gc_epoch = 2;

// How many GCs after an nmethod has not been used, do we consider it cold?
uint64_t CodeCache::_cold_gc_count = INT_MAX;

double CodeCache::_last_unloading_time = 0.0;
size_t CodeCache::_last_unloading_used = 0;
volatile bool CodeCache::_unloading_threshold_gc_requested = false;
TruncatedSeq CodeCache::_unloading_gc_intervals(10 /* samples */);
TruncatedSeq CodeCache::_unloading_allocation_rates(10 /* samples */);

uint64_t CodeCache::gc_epoch() {
  return _gc_epoch;
}

bool CodeCache::is_gc_marking_cycle_active() {
  // Odd means that marking is active
  return (_gc_epoch % 2) == 1;
}

uint64_t CodeCache::previous_completed_gc_marking_cycle() {
  if (is_gc_marking_cycle_active()) {
    return _gc_epoch - 2;
  } else {
    return _gc_epoch - 1;
  }
}

void CodeCache::on_gc_marking_cycle_start() {
  assert(!is_gc_marking_cycle_active(), "Previous marking cycle never ended");
  ++_gc_epoch;
}

// Once started the code cache marking cycle must only be finished after marking of
// the java heap is complete. Otherwise nmethods could appear to be not on stack even
// if they have frames in continuation StackChunks that were not yet visited.
void CodeCache::on_gc_marking_cycle_finish() {
  assert(is_gc_marking_cycle_active(), "Marking cycle started before last one finished");
  ++_gc_epoch;
  update_cold_gc_count();
}

void CodeCache::arm_all_nmethods() {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm != nullptr) {
    bs_nm->arm_all_nmethods();
  }
}

// Mark nmethods for unloading if they contain otherwise unreachable oops.
void CodeCache::do_unloading(bool unloading_occurred) {
  assert_locked_or_safepoint(CodeCache_lock);
  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    iter.method()->do_unloading(unloading_occurred);
  }
}

void CodeCache::verify_clean_inline_caches() {
#ifdef ASSERT
  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    nm->verify_clean_inline_caches();
    nm->verify();
  }
#endif
}

// Defer freeing of concurrently cleaned ExceptionCache entries until
// after a global handshake operation.
void CodeCache::release_exception_cache(ExceptionCache* entry) {
  if (SafepointSynchronize::is_at_safepoint()) {
    delete entry;
  } else {
    for (;;) {
      ExceptionCache* purge_list_head = Atomic::load(&_exception_cache_purge_list);
      entry->set_purge_list_next(purge_list_head);
      if (Atomic::cmpxchg(&_exception_cache_purge_list, purge_list_head, entry) == purge_list_head) {
        break;
      }
    }
  }
}

// Delete exception caches that have been concurrently unlinked,
// followed by a global handshake operation.
void CodeCache::purge_exception_caches() {
  ExceptionCache* curr = _exception_cache_purge_list;
  while (curr != nullptr) {
    ExceptionCache* next = curr->purge_list_next();
    delete curr;
    curr = next;
  }
  _exception_cache_purge_list = nullptr;
}

// Restart compiler if possible and required..
void CodeCache::maybe_restart_compiler(size_t freed_memory) {

  // Try to start the compiler again if we freed any memory
  if (!CompileBroker::should_compile_new_jobs() && freed_memory != 0) {
    CompileBroker::set_should_compile_new_jobs(CompileBroker::run_compilation);
    log_info(codecache)("Restarting compiler");
    EventJITRestart event;
    event.set_freedMemory(freed_memory);
    event.set_codeCacheMaxCapacity(CodeCache::max_capacity());
    event.commit();
  }
}

uint8_t CodeCache::_unloading_cycle = 1;

void CodeCache::increment_unloading_cycle() {
  // 2-bit value (see IsUnloadingState in nmethod.cpp for details)
  // 0 is reserved for new methods.
  _unloading_cycle = (_unloading_cycle + 1) % 4;
  if (_unloading_cycle == 0) {
    _unloading_cycle = 1;
  }
}

CodeCache::UnlinkingScope::UnlinkingScope(BoolObjectClosure* is_alive)
  : _is_unloading_behaviour(is_alive)
{
  _saved_behaviour = IsUnloadingBehaviour::current();
  IsUnloadingBehaviour::set_current(&_is_unloading_behaviour);
  increment_unloading_cycle();
  DependencyContext::cleaning_start();
}

CodeCache::UnlinkingScope::~UnlinkingScope() {
  IsUnloadingBehaviour::set_current(_saved_behaviour);
  DependencyContext::cleaning_end();
}

void CodeCache::verify_oops() {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  VerifyOopClosure voc;
  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    nm->oops_do(&voc);
    nm->verify_oop_relocations();
  }
}

int CodeCache::blob_count(CodeBlobType code_blob_type) {
  CodeHeap* heap = get_code_heap(code_blob_type);
  return (heap != nullptr) ? heap->blob_count() : 0;
}

int CodeCache::blob_count() {
  int count = 0;
  FOR_ALL_HEAPS(heap) {
    count += (*heap)->blob_count();
  }
  return count;
}

int CodeCache::nmethod_count(CodeBlobType code_blob_type) {
  CodeHeap* heap = get_code_heap(code_blob_type);
  return (heap != nullptr) ? heap->nmethod_count() : 0;
}

int CodeCache::nmethod_count() {
  int count = 0;
  for (CodeHeap* heap : *_nmethod_heaps) {
    count += heap->nmethod_count();
  }
  return count;
}

int CodeCache::adapter_count(CodeBlobType code_blob_type) {
  CodeHeap* heap = get_code_heap(code_blob_type);
  return (heap != nullptr) ? heap->adapter_count() : 0;
}

int CodeCache::adapter_count() {
  int count = 0;
  FOR_ALL_HEAPS(heap) {
    count += (*heap)->adapter_count();
  }
  return count;
}

address CodeCache::low_bound(CodeBlobType code_blob_type) {
  CodeHeap* heap = get_code_heap(code_blob_type);
  return (heap != nullptr) ? (address)heap->low_boundary() : nullptr;
}

address CodeCache::high_bound(CodeBlobType code_blob_type) {
  CodeHeap* heap = get_code_heap(code_blob_type);
  return (heap != nullptr) ? (address)heap->high_boundary() : nullptr;
}

size_t CodeCache::capacity() {
  size_t cap = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    cap += (*heap)->capacity();
  }
  return cap;
}

size_t CodeCache::unallocated_capacity(CodeBlobType code_blob_type) {
  CodeHeap* heap = get_code_heap(code_blob_type);
  return (heap != nullptr) ? heap->unallocated_capacity() : 0;
}

size_t CodeCache::unallocated_capacity() {
  size_t unallocated_cap = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    unallocated_cap += (*heap)->unallocated_capacity();
  }
  return unallocated_cap;
}

size_t CodeCache::max_capacity() {
  size_t max_cap = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    max_cap += (*heap)->max_capacity();
  }
  return max_cap;
}

bool CodeCache::is_non_nmethod(address addr) {
  CodeHeap* blob = get_code_heap(CodeBlobType::NonNMethod);
  return blob->contains(addr);
}

size_t CodeCache::max_distance_to_non_nmethod() {
  if (!SegmentedCodeCache) {
    return ReservedCodeCacheSize;
  } else {
    CodeHeap* blob = get_code_heap(CodeBlobType::NonNMethod);
    // the max distance is minimized by placing the NonNMethod segment
    // in between MethodProfiled and MethodNonProfiled segments
    size_t dist1 = (size_t)blob->high() - (size_t)_low_bound;
    size_t dist2 = (size_t)_high_bound - (size_t)blob->low();
    return dist1 > dist2 ? dist1 : dist2;
  }
}

// Returns the reverse free ratio. E.g., if 25% (1/4) of the code cache
// is free, reverse_free_ratio() returns 4.
// Since code heap for each type of code blobs falls forward to the next
// type of code heap, return the reverse free ratio for the entire
// code cache.
double CodeCache::reverse_free_ratio() {
  double unallocated = MAX2((double)unallocated_capacity(), 1.0); // Avoid division by 0;
  double max = (double)max_capacity();
  double result = max / unallocated;
  assert (max >= unallocated, "Must be");
  assert (result >= 1.0, "reverse_free_ratio must be at least 1. It is %f", result);
  return result;
}

size_t CodeCache::bytes_allocated_in_freelists() {
  size_t allocated_bytes = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    allocated_bytes += (*heap)->allocated_in_freelist();
  }
  return allocated_bytes;
}

int CodeCache::allocated_segments() {
  int number_of_segments = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    number_of_segments += (*heap)->allocated_segments();
  }
  return number_of_segments;
}

size_t CodeCache::freelists_length() {
  size_t length = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    length += (*heap)->freelist_length();
  }
  return length;
}

void icache_init();

void CodeCache::initialize() {
  assert(CodeCacheSegmentSize >= (uintx)CodeEntryAlignment, "CodeCacheSegmentSize must be large enough to align entry points");
#ifdef COMPILER2
  assert(CodeCacheSegmentSize >= (uintx)OptoLoopAlignment,  "CodeCacheSegmentSize must be large enough to align inner loops");
#endif
  assert(CodeCacheSegmentSize >= sizeof(jdouble),    "CodeCacheSegmentSize must be large enough to align constants");
  // This was originally just a check of the alignment, causing failure, instead, round
  // the code cache to the page size.  In particular, Solaris is moving to a larger
  // default page size.
  CodeCacheExpansionSize = align_up(CodeCacheExpansionSize, os::vm_page_size());

  if (SegmentedCodeCache) {
    // Use multiple code heaps
    initialize_heaps();
  } else {
    // Use a single code heap
    FLAG_SET_ERGO(NonNMethodCodeHeapSize, (uintx)os::vm_page_size());
    FLAG_SET_ERGO(ProfiledCodeHeapSize, 0);
    FLAG_SET_ERGO(NonProfiledCodeHeapSize, 0);

    // If InitialCodeCacheSize is equal to ReservedCodeCacheSize, then it's more likely
    // users want to use the largest available page.
    const size_t min_pages = (InitialCodeCacheSize == ReservedCodeCacheSize) ? 1 : 8;
    ReservedCodeSpace rs = reserve_heap_memory(ReservedCodeCacheSize, page_size(false, min_pages));
    // Register CodeHeaps with LSan as we sometimes embed pointers to malloc memory.
    LSAN_REGISTER_ROOT_REGION(rs.base(), rs.size());
    add_heap(rs, "CodeCache", CodeBlobType::All);
  }

  // Initialize ICache flush mechanism
  // This service is needed for os::register_code_area
  icache_init();

  // Give OS a chance to register generated code area.
  // This is used on Windows 64 bit platforms to register
  // Structured Exception Handlers for our generated code.
  os::register_code_area((char*)low_bound(), (char*)high_bound());
}

void codeCache_init() {
  CodeCache::initialize();
}

//------------------------------------------------------------------------------------------------

bool CodeCache::has_nmethods_with_dependencies() {
  return Atomic::load_acquire(&_number_of_nmethods_with_dependencies) != 0;
}

void CodeCache::clear_inline_caches() {
  assert_locked_or_safepoint(CodeCache_lock);
  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    iter.method()->clear_inline_caches();
  }
}

// Only used by whitebox API
void CodeCache::cleanup_inline_caches_whitebox() {
  assert_locked_or_safepoint(CodeCache_lock);
  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    iter.method()->cleanup_inline_caches_whitebox();
  }
}

// Keeps track of time spent for checking dependencies
NOT_PRODUCT(static elapsedTimer dependentCheckTime;)

#ifndef PRODUCT
// Check if any of live methods dependencies have been invalidated.
// (this is expensive!)
static void check_live_nmethods_dependencies(DepChange& changes) {
  // Checked dependencies are allocated into this ResourceMark
  ResourceMark rm;

  // Turn off dependency tracing while actually testing dependencies.
  FlagSetting fs(Dependencies::_verify_in_progress, true);

  typedef ResourceHashtable<DependencySignature, int, 11027,
                            AnyObj::RESOURCE_AREA, mtInternal,
                            &DependencySignature::hash,
                            &DependencySignature::equals> DepTable;

  DepTable* table = new DepTable();

  // Iterate over live nmethods and check dependencies of all nmethods that are not
  // marked for deoptimization. A particular dependency is only checked once.
  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    // Only notify for live nmethods
    if (!nm->is_marked_for_deoptimization()) {
      for (Dependencies::DepStream deps(nm); deps.next(); ) {
        // Construct abstraction of a dependency.
        DependencySignature* current_sig = new DependencySignature(deps);

        // Determine if dependency is already checked. table->put(...) returns
        // 'true' if the dependency is added (i.e., was not in the hashtable).
        if (table->put(*current_sig, 1)) {
          if (deps.check_dependency() != nullptr) {
            // Dependency checking failed. Print out information about the failed
            // dependency and finally fail with an assert. We can fail here, since
            // dependency checking is never done in a product build.
            tty->print_cr("Failed dependency:");
            changes.print();
            nm->print();
            nm->print_dependencies_on(tty);
            assert(false, "Should have been marked for deoptimization");
          }
        }
      }
    }
  }
}
#endif

void CodeCache::mark_for_deoptimization(DeoptimizationScope* deopt_scope, KlassDepChange& changes) {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

  // search the hierarchy looking for nmethods which are affected by the loading of this class

  // then search the interfaces this class implements looking for nmethods
  // which might be dependent of the fact that an interface only had one
  // implementor.
  // nmethod::check_all_dependencies works only correctly, if no safepoint
  // can happen
  NoSafepointVerifier nsv;
  for (DepChange::ContextStream str(changes, nsv); str.next(); ) {
    InstanceKlass* d = str.klass();
    d->mark_dependent_nmethods(deopt_scope, changes);
  }

#ifndef PRODUCT
  if (VerifyDependencies) {
    // Object pointers are used as unique identifiers for dependency arguments. This
    // is only possible if no safepoint, i.e., GC occurs during the verification code.
    dependentCheckTime.start();
    check_live_nmethods_dependencies(changes);
    dependentCheckTime.stop();
  }
#endif
}

#if INCLUDE_JVMTI
// RedefineClasses support for saving nmethods that are dependent on "old" methods.
// We don't really expect this table to grow very large.  If it does, it can become a hashtable.
static GrowableArray<nmethod*>* old_nmethod_table = nullptr;

static void add_to_old_table(nmethod* c) {
  if (old_nmethod_table == nullptr) {
    old_nmethod_table = new (mtCode) GrowableArray<nmethod*>(100, mtCode);
  }
  old_nmethod_table->push(c);
}

static void reset_old_method_table() {
  if (old_nmethod_table != nullptr) {
    delete old_nmethod_table;
    old_nmethod_table = nullptr;
  }
}

// Remove this method when flushed.
void CodeCache::unregister_old_nmethod(nmethod* c) {
  assert_lock_strong(CodeCache_lock);
  if (old_nmethod_table != nullptr) {
    int index = old_nmethod_table->find(c);
    if (index != -1) {
      old_nmethod_table->delete_at(index);
    }
  }
}

void CodeCache::old_nmethods_do(MetadataClosure* f) {
  // Walk old method table and mark those on stack.
  int length = 0;
  if (old_nmethod_table != nullptr) {
    length = old_nmethod_table->length();
    for (int i = 0; i < length; i++) {
      // Walk all methods saved on the last pass.  Concurrent class unloading may
      // also be looking at this method's metadata, so don't delete it yet if
      // it is marked as unloaded.
      old_nmethod_table->at(i)->metadata_do(f);
    }
  }
  log_debug(redefine, class, nmethod)("Walked %d nmethods for mark_on_stack", length);
}

// Walk compiled methods and mark dependent methods for deoptimization.
void CodeCache::mark_dependents_for_evol_deoptimization(DeoptimizationScope* deopt_scope) {
  assert(SafepointSynchronize::is_at_safepoint(), "Can only do this at a safepoint!");
  // Each redefinition creates a new set of nmethods that have references to "old" Methods
  // So delete old method table and create a new one.
  reset_old_method_table();

  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    nmethod* nm = iter.method();
    // Walk all alive nmethods to check for old Methods.
    // This includes methods whose inline caches point to old methods, so
    // inline cache clearing is unnecessary.
    if (nm->has_evol_metadata()) {
      deopt_scope->mark(nm);
      add_to_old_table(nm);
    }
  }
}

void CodeCache::mark_all_nmethods_for_evol_deoptimization(DeoptimizationScope* deopt_scope) {
  assert(SafepointSynchronize::is_at_safepoint(), "Can only do this at a safepoint!");
  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    nmethod* nm = iter.method();
    if (!nm->method()->is_method_handle_intrinsic()) {
      if (nm->can_be_deoptimized()) {
        deopt_scope->mark(nm);
      }
      if (nm->has_evol_metadata()) {
        add_to_old_table(nm);
      }
    }
  }
}

#endif // INCLUDE_JVMTI

void CodeCache::mark_directives_matches(bool top_only) {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  Thread *thread = Thread::current();
  HandleMark hm(thread);

  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    methodHandle mh(thread, nm->method());
    if (DirectivesStack::hasMatchingDirectives(mh, top_only)) {
      ResourceMark rm;
      log_trace(codecache)("Mark because of matching directives %s", mh->external_name());
      mh->set_has_matching_directives();
    }
  }
}

void CodeCache::recompile_marked_directives_matches() {
  Thread *thread = Thread::current();
  HandleMark hm(thread);

  // Try the max level and let the directives be applied during the compilation.
  int comp_level = CompilationPolicy::highest_compile_level();
  RelaxedNMethodIterator iter(RelaxedNMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    methodHandle mh(thread, nm->method());
    if (mh->has_matching_directives()) {
      ResourceMark rm;
      mh->clear_directive_flags();
      bool deopt = false;

      if (!nm->is_osr_method()) {
        log_trace(codecache)("Recompile to level %d because of matching directives %s",
                             comp_level, mh->external_name());
        nmethod * comp_nm = CompileBroker::compile_method(mh, InvocationEntryBci, comp_level,
                                                          methodHandle(), 0,
                                                          CompileTask::Reason_DirectivesChanged,
                                                          (JavaThread*)thread);
        if (comp_nm == nullptr) {
          log_trace(codecache)("Recompilation to level %d failed, deoptimize %s",
                               comp_level, mh->external_name());
          deopt = true;
        }
      } else {
        log_trace(codecache)("Deoptimize OSR %s", mh->external_name());
        deopt = true;
      }
      // For some reason the method cannot be compiled by C2, e.g. the new directives forbid it.
      // Deoptimize the method and let the usual hotspot logic do the rest.
      if (deopt) {
        if (!nm->has_been_deoptimized() && nm->can_be_deoptimized()) {
          nm->make_not_entrant();
          nm->make_deoptimized();
        }
      }
      gc_on_allocation(); // Flush unused methods from CodeCache if required.
    }
  }
}

// Mark methods for deopt (if safe or possible).
void CodeCache::mark_all_nmethods_for_deoptimization(DeoptimizationScope* deopt_scope) {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    if (!nm->is_native_method()) {
      deopt_scope->mark(nm);
    }
  }
}

void CodeCache::mark_for_deoptimization(DeoptimizationScope* deopt_scope, Method* dependee) {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

  NMethodIterator iter(NMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    if (nm->is_dependent_on_method(dependee)) {
      deopt_scope->mark(nm);
    }
  }
}

void CodeCache::make_marked_nmethods_deoptimized() {
  RelaxedNMethodIterator iter(RelaxedNMethodIterator::not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    if (nm->is_marked_for_deoptimization() && !nm->has_been_deoptimized() && nm->can_be_deoptimized()) {
      nm->make_not_entrant();
      nm->make_deoptimized();
    }
  }
}

// Marks compiled methods dependent on dependee.
void CodeCache::mark_dependents_on(DeoptimizationScope* deopt_scope, InstanceKlass* dependee) {
  assert_lock_strong(Compile_lock);

  if (!has_nmethods_with_dependencies()) {
    return;
  }

  if (dependee->is_linked()) {
    // Class initialization state change.
    KlassInitDepChange changes(dependee);
    mark_for_deoptimization(deopt_scope, changes);
  } else {
    // New class is loaded.
    NewKlassDepChange changes(dependee);
    mark_for_deoptimization(deopt_scope, changes);
  }
}

// Marks compiled methods dependent on dependee
void CodeCache::mark_dependents_on_method_for_breakpoint(const methodHandle& m_h) {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");

  DeoptimizationScope deopt_scope;
  // Compute the dependent nmethods
  mark_for_deoptimization(&deopt_scope, m_h());
  deopt_scope.deoptimize_marked();
}

void CodeCache::verify() {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_HEAPS(heap) {
    (*heap)->verify();
    FOR_ALL_BLOBS(cb, *heap) {
      cb->verify();
    }
  }
}

// A CodeHeap is full. Print out warning and report event.
PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
void CodeCache::report_codemem_full(CodeBlobType code_blob_type, bool print) {
  // Get nmethod heap for the given CodeBlobType and build CodeCacheFull event
  CodeHeap* heap = get_code_heap(code_blob_type);
  assert(heap != nullptr, "heap is null");

  int full_count = heap->report_full();

  if ((full_count == 1) || print) {
    // Not yet reported for this heap, report
    if (SegmentedCodeCache) {
      ResourceMark rm;
      stringStream msg1_stream, msg2_stream;
      msg1_stream.print("%s is full. Compiler has been disabled.",
                        get_code_heap_name(code_blob_type));
      msg2_stream.print("Try increasing the code heap size using -XX:%s=",
                 get_code_heap_flag_name(code_blob_type));
      const char *msg1 = msg1_stream.as_string();
      const char *msg2 = msg2_stream.as_string();

      log_warning(codecache)("%s", msg1);
      log_warning(codecache)("%s", msg2);
      warning("%s", msg1);
      warning("%s", msg2);
    } else {
      const char *msg1 = "CodeCache is full. Compiler has been disabled.";
      const char *msg2 = "Try increasing the code cache size using -XX:ReservedCodeCacheSize=";

      log_warning(codecache)("%s", msg1);
      log_warning(codecache)("%s", msg2);
      warning("%s", msg1);
      warning("%s", msg2);
    }
    stringStream s;
    // Dump code cache into a buffer before locking the tty.
    {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      print_summary(&s);
    }
    {
      ttyLocker ttyl;
      tty->print("%s", s.freeze());
    }

    if (full_count == 1) {
      if (PrintCodeHeapAnalytics) {
        CompileBroker::print_heapinfo(tty, "all", 4096); // details, may be a lot!
      }
    }
  }

  EventCodeCacheFull event;
  if (event.should_commit()) {
    event.set_codeBlobType((u1)code_blob_type);
    event.set_startAddress((u8)heap->low_boundary());
    event.set_commitedTopAddress((u8)heap->high());
    event.set_reservedTopAddress((u8)heap->high_boundary());
    event.set_entryCount(heap->blob_count());
    event.set_methodCount(heap->nmethod_count());
    event.set_adaptorCount(heap->adapter_count());
    event.set_unallocatedCapacity(heap->unallocated_capacity());
    event.set_fullCount(heap->full_count());
    event.set_codeCacheMaxCapacity(CodeCache::max_capacity());
    event.commit();
  }
}
PRAGMA_DIAG_POP

void CodeCache::print_memory_overhead() {
  size_t wasted_bytes = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
      CodeHeap* curr_heap = *heap;
      for (CodeBlob* cb = (CodeBlob*)curr_heap->first(); cb != nullptr; cb = (CodeBlob*)curr_heap->next(cb)) {
        HeapBlock* heap_block = ((HeapBlock*)cb) - 1;
        wasted_bytes += heap_block->length() * CodeCacheSegmentSize - cb->size();
      }
  }
  // Print bytes that are allocated in the freelist
  ttyLocker ttl;
  tty->print_cr("Number of elements in freelist: " SSIZE_FORMAT,       freelists_length());
  tty->print_cr("Allocated in freelist:          " SSIZE_FORMAT "kB",  bytes_allocated_in_freelists()/K);
  tty->print_cr("Unused bytes in CodeBlobs:      " SSIZE_FORMAT "kB",  (wasted_bytes/K));
  tty->print_cr("Segment map size:               " SSIZE_FORMAT "kB",  allocated_segments()/K); // 1 byte per segment
}

//------------------------------------------------------------------------------------------------
// Non-product version

#ifndef PRODUCT

void CodeCache::print_trace(const char* event, CodeBlob* cb, uint size) {
  if (PrintCodeCache2) {  // Need to add a new flag
    ResourceMark rm;
    if (size == 0) {
      int s = cb->size();
      assert(s >= 0, "CodeBlob size is negative: %d", s);
      size = (uint) s;
    }
    tty->print_cr("CodeCache %s:  addr: " INTPTR_FORMAT ", size: 0x%x", event, p2i(cb), size);
  }
}

void CodeCache::print_internals() {
  int nmethodCount = 0;
  int runtimeStubCount = 0;
  int adapterCount = 0;
  int deoptimizationStubCount = 0;
  int uncommonTrapStubCount = 0;
  int bufferBlobCount = 0;
  int total = 0;
  int nmethodNotEntrant = 0;
  int nmethodJava = 0;
  int nmethodNative = 0;
  int max_nm_size = 0;
  ResourceMark rm;

  int i = 0;
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    if ((_nmethod_heaps->length() >= 1) && Verbose) {
      tty->print_cr("-- %s --", (*heap)->name());
    }
    FOR_ALL_BLOBS(cb, *heap) {
      total++;
      if (cb->is_nmethod()) {
        nmethod* nm = (nmethod*)cb;

        if (Verbose && nm->method() != nullptr) {
          ResourceMark rm;
          char *method_name = nm->method()->name_and_sig_as_C_string();
          tty->print("%s", method_name);
          if(nm->is_not_entrant()) { tty->print_cr(" not-entrant"); }
        }

        nmethodCount++;

        if(nm->is_not_entrant()) { nmethodNotEntrant++; }
        if(nm->method() != nullptr && nm->is_native_method()) { nmethodNative++; }

        if(nm->method() != nullptr && nm->is_java_method()) {
          nmethodJava++;
          max_nm_size = MAX2(max_nm_size, nm->size());
        }
      } else if (cb->is_runtime_stub()) {
        runtimeStubCount++;
      } else if (cb->is_deoptimization_stub()) {
        deoptimizationStubCount++;
      } else if (cb->is_uncommon_trap_stub()) {
        uncommonTrapStubCount++;
      } else if (cb->is_adapter_blob()) {
        adapterCount++;
      } else if (cb->is_buffer_blob()) {
        bufferBlobCount++;
      }
    }
  }

  int bucketSize = 512;
  int bucketLimit = max_nm_size / bucketSize + 1;
  int *buckets = NEW_C_HEAP_ARRAY(int, bucketLimit, mtCode);
  memset(buckets, 0, sizeof(int) * bucketLimit);

  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    nmethod* nm = iter.method();
    if(nm->method() != nullptr && nm->is_java_method()) {
      buckets[nm->size() / bucketSize]++;
    }
  }

  tty->print_cr("Code Cache Entries (total of %d)",total);
  tty->print_cr("-------------------------------------------------");
  tty->print_cr("nmethods: %d",nmethodCount);
  tty->print_cr("\tnot_entrant: %d",nmethodNotEntrant);
  tty->print_cr("\tjava: %d",nmethodJava);
  tty->print_cr("\tnative: %d",nmethodNative);
  tty->print_cr("runtime_stubs: %d",runtimeStubCount);
  tty->print_cr("adapters: %d",adapterCount);
  tty->print_cr("buffer blobs: %d",bufferBlobCount);
  tty->print_cr("deoptimization_stubs: %d",deoptimizationStubCount);
  tty->print_cr("uncommon_traps: %d",uncommonTrapStubCount);
  tty->print_cr("\nnmethod size distribution");
  tty->print_cr("-------------------------------------------------");

  for(int i=0; i<bucketLimit; i++) {
    if(buckets[i] != 0) {
      tty->print("%d - %d bytes",i*bucketSize,(i+1)*bucketSize);
      tty->fill_to(40);
      tty->print_cr("%d",buckets[i]);
    }
  }

  FREE_C_HEAP_ARRAY(int, buckets);
  print_memory_overhead();
}

#endif // !PRODUCT

void CodeCache::print() {
  print_summary(tty);

#ifndef PRODUCT
  if (!Verbose) return;

  CodeBlob_sizes live[CompLevel_full_optimization + 1];
  CodeBlob_sizes runtimeStub;
  CodeBlob_sizes uncommonTrapStub;
  CodeBlob_sizes deoptimizationStub;
  CodeBlob_sizes adapter;
  CodeBlob_sizes bufferBlob;
  CodeBlob_sizes other;

  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    FOR_ALL_BLOBS(cb, *heap) {
      if (cb->is_nmethod()) {
        const int level = cb->as_nmethod()->comp_level();
        assert(0 <= level && level <= CompLevel_full_optimization, "Invalid compilation level");
        live[level].add(cb);
      } else if (cb->is_runtime_stub()) {
        runtimeStub.add(cb);
      } else if (cb->is_deoptimization_stub()) {
        deoptimizationStub.add(cb);
      } else if (cb->is_uncommon_trap_stub()) {
        uncommonTrapStub.add(cb);
      } else if (cb->is_adapter_blob()) {
        adapter.add(cb);
      } else if (cb->is_buffer_blob()) {
        bufferBlob.add(cb);
      } else {
        other.add(cb);
      }
    }
  }

  tty->print_cr("nmethod dependency checking time %fs", dependentCheckTime.seconds());

  tty->print_cr("nmethod blobs per compilation level:");
  for (int i = 0; i <= CompLevel_full_optimization; i++) {
    const char *level_name;
    switch (i) {
    case CompLevel_none:              level_name = "none";              break;
    case CompLevel_simple:            level_name = "simple";            break;
    case CompLevel_limited_profile:   level_name = "limited profile";   break;
    case CompLevel_full_profile:      level_name = "full profile";      break;
    case CompLevel_full_optimization: level_name = "full optimization"; break;
    default: assert(false, "invalid compilation level");
    }
    tty->print_cr("%s:", level_name);
    live[i].print("live");
  }

  struct {
    const char* name;
    const CodeBlob_sizes* sizes;
  } non_nmethod_blobs[] = {
    { "runtime",        &runtimeStub },
    { "uncommon trap",  &uncommonTrapStub },
    { "deoptimization", &deoptimizationStub },
    { "adapter",        &adapter },
    { "buffer blob",    &bufferBlob },
    { "other",          &other },
  };
  tty->print_cr("Non-nmethod blobs:");
  for (auto& blob: non_nmethod_blobs) {
    blob.sizes->print(blob.name);
  }

  if (WizardMode) {
     // print the oop_map usage
    int code_size = 0;
    int number_of_blobs = 0;
    int number_of_oop_maps = 0;
    int map_size = 0;
    FOR_ALL_ALLOCABLE_HEAPS(heap) {
      FOR_ALL_BLOBS(cb, *heap) {
        number_of_blobs++;
        code_size += cb->code_size();
        ImmutableOopMapSet* set = cb->oop_maps();
        if (set != nullptr) {
          number_of_oop_maps += set->count();
          map_size           += set->nr_of_bytes();
        }
      }
    }
    tty->print_cr("OopMaps");
    tty->print_cr("  #blobs    = %d", number_of_blobs);
    tty->print_cr("  code size = %d", code_size);
    tty->print_cr("  #oop_maps = %d", number_of_oop_maps);
    tty->print_cr("  map size  = %d", map_size);
  }

#endif // !PRODUCT
}

void CodeCache::print_summary(outputStream* st, bool detailed) {
  int full_count = 0;
  julong total_used = 0;
  julong total_max_used = 0;
  julong total_free = 0;
  julong total_size = 0;
  FOR_ALL_HEAPS(heap_iterator) {
    CodeHeap* heap = (*heap_iterator);
    size_t total = (heap->high_boundary() - heap->low_boundary());
    if (_heaps->length() >= 1) {
      st->print("%s:", heap->name());
    } else {
      st->print("CodeCache:");
    }
    size_t size = total/K;
    size_t used = (total - heap->unallocated_capacity())/K;
    size_t max_used = heap->max_allocated_capacity()/K;
    size_t free = heap->unallocated_capacity()/K;
    total_size += size;
    total_used += used;
    total_max_used += max_used;
    total_free += free;
    st->print_cr(" size=" SIZE_FORMAT "Kb used=" SIZE_FORMAT
                 "Kb max_used=" SIZE_FORMAT "Kb free=" SIZE_FORMAT "Kb",
                 size, used, max_used, free);

    if (detailed) {
      st->print_cr(" bounds [" INTPTR_FORMAT ", " INTPTR_FORMAT ", " INTPTR_FORMAT "]",
                   p2i(heap->low_boundary()),
                   p2i(heap->high()),
                   p2i(heap->high_boundary()));

      full_count += get_codemem_full_count(heap->code_blob_type());
    }
  }

  if (detailed) {
    if (SegmentedCodeCache) {
      st->print("CodeCache:");
      st->print_cr(" size=" JULONG_FORMAT "Kb, used=" JULONG_FORMAT
                   "Kb, max_used=" JULONG_FORMAT "Kb, free=" JULONG_FORMAT "Kb",
                   total_size, total_used, total_max_used, total_free);
    }
    st->print_cr(" total_blobs=" UINT32_FORMAT ", nmethods=" UINT32_FORMAT
                 ", adapters=" UINT32_FORMAT ", full_count=" UINT32_FORMAT,
                 blob_count(), nmethod_count(), adapter_count(), full_count);
    st->print_cr("Compilation: %s, stopped_count=%d, restarted_count=%d",
                 CompileBroker::should_compile_new_jobs() ?
                 "enabled" : Arguments::mode() == Arguments::_int ?
                 "disabled (interpreter mode)" :
                 "disabled (not enough contiguous free space left)",
                 CompileBroker::get_total_compiler_stopped_count(),
                 CompileBroker::get_total_compiler_restarted_count());
  }
}

void CodeCache::print_codelist(outputStream* st) {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

  NMethodIterator iter(NMethodIterator::not_unloading);
  while (iter.next()) {
    nmethod* nm = iter.method();
    ResourceMark rm;
    char* method_name = nm->method()->name_and_sig_as_C_string();
    st->print_cr("%d %d %d %s [" INTPTR_FORMAT ", " INTPTR_FORMAT " - " INTPTR_FORMAT "]",
                 nm->compile_id(), nm->comp_level(), nm->get_state(),
                 method_name,
                 (intptr_t)nm->header_begin(), (intptr_t)nm->code_begin(), (intptr_t)nm->code_end());
  }
}

void CodeCache::print_layout(outputStream* st) {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  ResourceMark rm;
  print_summary(st, true);
}

void CodeCache::log_state(outputStream* st) {
  st->print(" total_blobs='" UINT32_FORMAT "' nmethods='" UINT32_FORMAT "'"
            " adapters='" UINT32_FORMAT "' free_code_cache='" SIZE_FORMAT "'",
            blob_count(), nmethod_count(), adapter_count(),
            unallocated_capacity());
}

#ifdef LINUX
void CodeCache::write_perf_map(const char* filename) {
  MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

  // Perf expects to find the map file at /tmp/perf-<pid>.map
  // if the file name is not specified.
  char fname[32];
  if (filename == nullptr) {
    jio_snprintf(fname, sizeof(fname), "/tmp/perf-%d.map", os::current_process_id());
    filename = fname;
  }

  fileStream fs(filename, "w");
  if (!fs.is_open()) {
    log_warning(codecache)("Failed to create %s for perf map", filename);
    return;
  }

  AllCodeBlobsIterator iter(AllCodeBlobsIterator::not_unloading);
  while (iter.next()) {
    CodeBlob *cb = iter.method();
    ResourceMark rm;
    const char* method_name =
      cb->is_nmethod() ? cb->as_nmethod()->method()->external_name()
                       : cb->name();
    fs.print_cr(INTPTR_FORMAT " " INTPTR_FORMAT " %s",
                (intptr_t)cb->code_begin(), (intptr_t)cb->code_size(),
                method_name);
  }
}
#endif // LINUX

//---<  BEGIN  >--- CodeHeap State Analytics.

void CodeCache::aggregate(outputStream *out, size_t granularity) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::aggregate(out, (*heap), granularity);
  }
}

void CodeCache::discard(outputStream *out) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::discard(out, (*heap));
  }
}

void CodeCache::print_usedSpace(outputStream *out) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::print_usedSpace(out, (*heap));
  }
}

void CodeCache::print_freeSpace(outputStream *out) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::print_freeSpace(out, (*heap));
  }
}

void CodeCache::print_count(outputStream *out) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::print_count(out, (*heap));
  }
}

void CodeCache::print_space(outputStream *out) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::print_space(out, (*heap));
  }
}

void CodeCache::print_age(outputStream *out) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::print_age(out, (*heap));
  }
}

void CodeCache::print_names(outputStream *out) {
  FOR_ALL_ALLOCABLE_HEAPS(heap) {
    CodeHeapState::print_names(out, (*heap));
  }
}
//---<  END  >--- CodeHeap State Analytics.

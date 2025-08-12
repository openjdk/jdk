/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/metaspaceCriticalAllocation.hpp"
#include "memory/universe.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"

class MetadataAllocationRequest {
  ClassLoaderData* const        _loader_data;
  const size_t                  _word_size;
  const Metaspace::MetadataType _type;
  MetadataAllocationRequest*    _next;
  MetaWord*                     _result;
  bool                          _is_processed;

public:
  MetadataAllocationRequest(ClassLoaderData* loader_data,
                            size_t word_size,
                            Metaspace::MetadataType type)
    : _loader_data(loader_data),
      _word_size(word_size),
      _type(type),
      _next(nullptr),
      _result(nullptr),
      _is_processed(false) {
    MetaspaceCriticalAllocation::add(this);
  }

  ~MetadataAllocationRequest() {
    MetaspaceCriticalAllocation::remove(this);
  }

  ClassLoaderData*           loader_data() const   { return _loader_data; }
  size_t                     word_size() const     { return _word_size; }
  Metaspace::MetadataType    type() const          { return _type; }
  MetadataAllocationRequest* next() const          { return _next; }
  MetaWord*                  result() const        { return _result; }
  bool                       is_processed() const  { return _is_processed; }

  void set_next(MetadataAllocationRequest* next) { _next = next; }
  void set_result(MetaWord* result) {
    _result = result;
    _is_processed = true;
  }
};

volatile bool MetaspaceCriticalAllocation::_has_critical_allocation = false;
MetadataAllocationRequest* MetaspaceCriticalAllocation::_requests_head = nullptr;
MetadataAllocationRequest* MetaspaceCriticalAllocation::_requests_tail = nullptr;

void MetaspaceCriticalAllocation::add(MetadataAllocationRequest* request) {
  MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
  log_info(metaspace)("Requesting critical metaspace allocation; almost out of memory");
  Atomic::store(&_has_critical_allocation, true);
  // This is called by the request constructor to insert the request into the
  // global list.  The request's destructor will remove the request from the
  // list.  gcc13 has a false positive warning about the local request being
  // added to the global list because it doesn't relate those operations.
  PRAGMA_DIAG_PUSH
  PRAGMA_DANGLING_POINTER_IGNORED
  if (_requests_head == nullptr) {
    _requests_head = _requests_tail = request;
  } else {
    _requests_tail->set_next(request);
    _requests_tail = request;
  }
  PRAGMA_DIAG_POP
}

void MetaspaceCriticalAllocation::unlink(MetadataAllocationRequest* curr, MetadataAllocationRequest* prev) {
  if (_requests_head == curr) {
    _requests_head = curr->next();
  }
  if (_requests_tail == curr) {
    _requests_tail = prev;
  }
  if (prev != nullptr) {
    prev->set_next(curr->next());
  }
}

void MetaspaceCriticalAllocation::remove(MetadataAllocationRequest* request) {
  MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
  MetadataAllocationRequest* prev = nullptr;
  for (MetadataAllocationRequest* curr = _requests_head; curr != nullptr; curr = curr->next()) {
    if (curr == request) {
      unlink(curr, prev);
      break;
    } else {
      prev = curr;
    }
  }
}

bool MetaspaceCriticalAllocation::try_allocate_critical(MetadataAllocationRequest* request) {
  // This function uses an optimized scheme to limit the number of triggered
  // GCs. The idea is that only one request in the list is responsible for
  // triggering a GC, and later requests will try to piggy-back on that
  // request.
  //
  // For this to work, it is important that we can tell which requests were
  // seen by the GC's call to process(), and which requests were added after
  // last proccess() call. The property '_is_processed' tells this. Because the
  // logic below relies on that property, it is important that the GC calls
  // process() even when the GC didn't unload any classes.
  //
  // Note that process() leaves the requests in the queue, so that threads
  // in wait_for_purge, which had their requests processed, but didn't get any
  // memory can exit that function and trigger a new GC as a last effort to get
  // memory before throwing an OOME.
  //
  // Requests that have been processed once, will not trigger new GCs, we
  // therefore filter them out when we determine if the current 'request'
  // needs to trigger a GC, or if there are earlier requests that will
  // trigger a GC.

  {
    MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
    auto is_first_unprocessed = [&]() {
      for (MetadataAllocationRequest* curr = _requests_head; curr != nullptr; curr = curr->next()) {
        if (!curr->is_processed()) {
          // curr is the first not satisfied request
          return curr == request;
        }
      }

      return false;
    };

    if (is_first_unprocessed()) {
      // The first non-processed request takes ownership of triggering the GC
      // on behalf of itself, and all trailing requests in the list.
      return false;
    }
  }

  // Try to ride on a previous GC and hope for early satisfaction
  wait_for_purge(request);
  return request->result() != nullptr;
}

void MetaspaceCriticalAllocation::wait_for_purge(MetadataAllocationRequest* request) {
  ThreadBlockInVM tbivm(JavaThread::current());
  MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
  for (;;) {
    if (request->is_processed()) {
      // The GC has procesed this request during the purge.
      // Return and check the result, and potentially call a last-effort GC.
      break;
    }
    MetaspaceCritical_lock->wait_without_safepoint_check();
  }
}

void MetaspaceCriticalAllocation::block_if_concurrent_purge() {
  if (Atomic::load(&_has_critical_allocation)) {
    // If there is a concurrent Metaspace::purge() operation, we will block here,
    // to make sure critical allocations get precedence and don't get starved.
    MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
  }
}

void MetaspaceCriticalAllocation::process() {
  assert_lock_strong(MetaspaceCritical_lock);
  bool all_satisfied = true;
  for (MetadataAllocationRequest* curr = _requests_head; curr != nullptr; curr = curr->next()) {
    if (curr->result() != nullptr) {
      // Don't satisfy twice (can still be processed twice)
      continue;
    }
    // Try to allocate metadata.
    MetaWord* result = curr->loader_data()->metaspace_non_null()->allocate(curr->word_size(), curr->type());
    if (result == nullptr) {
      result = curr->loader_data()->metaspace_non_null()->expand_and_allocate(curr->word_size(), curr->type());
    }
    if (result == nullptr) {
      all_satisfied = false;
    }
    curr->set_result(result);
  }
  if (all_satisfied) {
    Atomic::store(&_has_critical_allocation, false);
  }
  MetaspaceCritical_lock->notify_all();
}

MetaWord* MetaspaceCriticalAllocation::allocate(ClassLoaderData* loader_data, size_t word_size, Metaspace::MetadataType type) {
  MetadataAllocationRequest request(loader_data, word_size, type);

  if (try_allocate_critical(&request)) {
    // Try to allocate on a previous concurrent GC if there was one, and return if successful
    return request.result();
  }

  // Always perform a synchronous full GC before bailing
  Universe::heap()->collect(GCCause::_metadata_GC_clear_soft_refs);

  // Return the result, be that success or failure
  return request.result();
}

/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/metaspaceCriticalAllocation.hpp"
#include "memory/universe.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"

class MetadataAllocationRequest {
  ClassLoaderData*           _loader_data;
  size_t                     _word_size;
  Metaspace::MetadataType    _type;
  MetadataAllocationRequest* _next;
  MetaWord*                  _result;
  bool                       _has_result;

public:
  MetadataAllocationRequest(ClassLoaderData* loader_data,
                            size_t word_size,
                            Metaspace::MetadataType type)
    : _loader_data(loader_data),
      _word_size(word_size),
      _type(type),
      _next(NULL),
      _result(NULL),
      _has_result(false) {
    MetaspaceCriticalAllocation::add(this);
  }

  ~MetadataAllocationRequest() {
    MetaspaceCriticalAllocation::remove(this);
  }

  ClassLoaderData*           loader_data() const { return _loader_data; }
  size_t                     word_size() const   { return _word_size; }
  Metaspace::MetadataType    type() const        { return _type; }
  MetadataAllocationRequest* next() const        { return _next; }
  MetaWord*                  result() const      { return _result; }
  bool                       has_result() const  { return _has_result; }

  void set_next(MetadataAllocationRequest* next) { _next = next; }
  void set_result(MetaWord* result) {
    _result = result;
    _has_result = true;
  }
};

volatile bool MetaspaceCriticalAllocation::_has_critical_allocation = false;
MetadataAllocationRequest* MetaspaceCriticalAllocation::_requests_head = NULL;
MetadataAllocationRequest* MetaspaceCriticalAllocation::_requests_tail = NULL;

void MetaspaceCriticalAllocation::add(MetadataAllocationRequest* request) {
  MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
  log_info(metaspace)("Requesting critical metaspace allocation; almost out of memory");
  Atomic::store(&_has_critical_allocation, true);
  if (_requests_head == NULL) {
    _requests_head = _requests_tail = request;
  } else {
    _requests_tail->set_next(request);
    _requests_tail = request;
  }
}

void MetaspaceCriticalAllocation::unlink(MetadataAllocationRequest* curr, MetadataAllocationRequest* prev) {
  if (_requests_head == curr) {
    _requests_head = curr->next();
  }
  if (_requests_tail == curr) {
    _requests_tail = prev;
  }
  if (prev != NULL) {
    prev->set_next(curr->next());
  }
}

void MetaspaceCriticalAllocation::remove(MetadataAllocationRequest* request) {
  MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
  MetadataAllocationRequest* prev = NULL;
  for (MetadataAllocationRequest* curr = _requests_head; curr != NULL; curr = curr->next()) {
    if (curr == request) {
      unlink(curr, prev);
      break;
    } else {
      prev = curr;
    }
  }
}

bool MetaspaceCriticalAllocation::try_allocate_critical(MetadataAllocationRequest* request) {
  MutexLocker ml(MetaspaceCritical_lock, Mutex::_no_safepoint_check_flag);
  if (_requests_head == request) {
    // The first request can't opportunistically ride on a previous GC
    return false;
  }
  // Try to ride on a previous GC and hope for early satisfaction
  wait_for_purge(request);
  return request->result() != NULL;
}

void MetaspaceCriticalAllocation::wait_for_purge(MetadataAllocationRequest* request) {
  while (!request->has_result()) {
    ThreadBlockInVM tbivm(JavaThread::current());
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

void MetaspaceCriticalAllocation::satisfy() {
  assert_lock_strong(MetaspaceCritical_lock);
  bool all_satisfied = true;
  for (MetadataAllocationRequest* curr = _requests_head; curr != NULL; curr = curr->next()) {
    if (curr->result() != NULL) {
      // Don't satisfy twice
      continue;
    }
    // Try to allocate metadata.
    MetaWord* result = curr->loader_data()->metaspace_non_null()->allocate(curr->word_size(), curr->type());
    if (result == NULL) {
      result = curr->loader_data()->metaspace_non_null()->expand_and_allocate(curr->word_size(), curr->type());
    }
    if (result == NULL) {
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

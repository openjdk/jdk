/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAdaptiveHeap.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zMemoryWorker.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "logging/log.hpp"
#include "logging/logHandle.hpp"
#include "runtime/init.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/rbTree.inline.hpp"

RBTreeOrdering ZMemoryWorker::ZHeatingRequestTreeComparator::cmp(zoffset first, zoffset second) {
  if (first < second) {
    // Start before second
    return RBTreeOrdering::LT;
  }

  if (first > second) {
    // Start after second
    return RBTreeOrdering::GT;
  }

  // Same position
  return RBTreeOrdering::EQ;
}

enum class ZMemoryWorkerMode {
  Uninitialized,
  Commit,
  Uncommit,
  UncommitTargetless,
  Heat,
  Wait,
};

static const char* memory_worker_completed_mode_name(ZMemoryWorkerMode mode) {
  switch (mode) {
  case ZMemoryWorkerMode::Commit:
    return "Committed";
  case ZMemoryWorkerMode::Uncommit:
  case ZMemoryWorkerMode::UncommitTargetless:
    return "Uncommitted";
  case ZMemoryWorkerMode::Heat:
    return "Heated";
  case ZMemoryWorkerMode::Wait:
  case ZMemoryWorkerMode::Uninitialized:
    break;
  }

  DEBUG_ONLY(ShouldNotReachHere();)
  return "Unknown";
}

static const char* memory_worker_start_mode_name(ZMemoryWorkerMode mode) {
  switch (mode) {
  case ZMemoryWorkerMode::Commit:
    return "Commit";
  case ZMemoryWorkerMode::Uncommit:
  case ZMemoryWorkerMode::UncommitTargetless:
    return "Uncommit";
  case ZMemoryWorkerMode::Heat:
    return "Heat";
  case ZMemoryWorkerMode::Wait:
  case ZMemoryWorkerMode::Uninitialized:
    break;
  }

  DEBUG_ONLY(ShouldNotReachHere();)
  return "Unknown";
}

bool ZMemoryWorker::is_enabled() {
  return ZAdaptiveHeapSizing || ZMemoryHeating;
}

ZMemoryWorker::ZMemoryWorker(uint32_t id, ZPartition* partition)
  : _id(id),
    _partition(partition),
    _lock(),
    _heating_requests(),
    _heating_request_bytes(0),
    _target_commit_capacity(0),
    _target_uncommit_capacity(0),
    _targetless_uncommit(false),
    _uncommit_request_time(),
    _stop(false),
    _currently_heating() {
  if (!is_enabled()) {
    // Disabled, do not start
    _stop = true;
    return;
  }
  set_name("ZMemoryWorker#%u", _id);
  create_and_start();

  if (ZNUMA::is_enabled()) {
    // If NUMA is enabled we set the affinity of the thread to CPUs associated
    // with the partition that the ZMemoryWorker will work on.
    os::numa_set_thread_affinity(this, ZNUMA::numa_id_to_node(_id));
  }
}

ZMemoryWorker::~ZMemoryWorker() {}

bool ZMemoryWorker::is_stop_requested() {
  return _stop;
}

size_t ZMemoryWorker::commit_granule(size_t target_capacity) {
  const size_t smallest_granule = ZGranuleSize;
  const size_t largest_granule = MAX2(ZPageSizeMediumMax, smallest_granule);

  return clamp(align_up(target_capacity / 128, ZGranuleSize), smallest_granule, largest_granule);
}

size_t ZMemoryWorker::uncommit_granule(size_t target_capacity) {
  const size_t smallest_granule = ZGranuleSize;
  const size_t largest_granule = MAX2(ZPageSizeMediumMax, smallest_granule);

  return clamp(align_up(target_capacity / 128, ZGranuleSize), smallest_granule, largest_granule);
}

bool ZMemoryWorker::has_heating_request() {
  assert((_heating_request_bytes == 0) == (_heating_requests.size() == 0),
         "_heating_request_bytes: %zu, _heating_requests.size(): %zu",
         _heating_request_bytes, _heating_requests.size());
  return _heating_request_bytes != 0;
}

void ZMemoryWorker::verify_heating_requests() {
#ifdef ASSERT
  // Don't verify the tree if it's "too" large
  if (_heating_requests.size() > 10) {
    return;
  }

  const ZHeatingRequestNode* prev = nullptr;
  size_t total_size = 0;

  _heating_requests.visit_range_in_order(zoffset(0), zoffset(ZAddressOffsetMax), [&](const ZHeatingRequestNode* node) {
    // If there is a previous node, make sure that it is not adjacent to this
    // node. Adjacent nodes should be merged when inserted to the tree.
    if (prev != nullptr) {
      assert(prev->key() + prev->val() != node->key(), "Should be merged. "
             "[" PTR_FORMAT ", " PTR_FORMAT "] [" PTR_FORMAT ", " PTR_FORMAT "]",
             untype(prev->key()), prev->val(), untype(node->key()), node->val());
    }
    total_size += node->val();
    prev = node;
    return true;
  });

  assert(total_size == _heating_request_bytes, "%zu == %zu", total_size, _heating_request_bytes);
#endif // ASSERT
}

void ZMemoryWorker::set_targetless_uncommit(Ticks now) {
  precond(ZUncommit);

  _targetless_uncommit = true;
  if (_uncommit_request_time == Ticks()) {
    _uncommit_request_time = now;
  }
}

void ZMemoryWorker::set_targeted_uncommit(size_t requested_capacity, Ticks now) {
  precond(ZUncommit);

  _target_uncommit_capacity = requested_capacity;
  if (_uncommit_request_time == Ticks()) {
    _uncommit_request_time = now;
  }
}

void ZMemoryWorker::clear_targetless_uncommit() {
  _targetless_uncommit = false;

  if (_target_uncommit_capacity == 0u) {
    // No more uncommit request
    _uncommit_request_time = Ticks();
  }
}

void ZMemoryWorker::clear_targeted_uncommit() {
  _target_uncommit_capacity = 0u;

  if (!_targetless_uncommit) {
    // No more uncommit request
    _uncommit_request_time = Ticks();
  }
}

void ZMemoryWorker::stop_heap_resizing() {
  precond(ZAdaptiveHeapSizing);

  // Remove requests to resize the capacity
  ZLocker<ZConditionLock> locker(&_lock);
  _target_commit_capacity = 0u;
  clear_targeted_uncommit();
}

void ZMemoryWorker::stop_shrink_capacity_granule() {
  precond(ZAdaptiveHeapSizing);

  if (!ZUncommit) {
    // Uncommit turned off.
    return;
  }

  // Remove requests to resize the capacity
  ZLocker<ZConditionLock> locker(&_lock);
  clear_targetless_uncommit();
}

void ZMemoryWorker::request_grow_capacity(size_t requested_capacity) {
  precond(ZAdaptiveHeapSizing);

  ZLocker<ZConditionLock> locker(&_lock);
  _target_commit_capacity = requested_capacity;
  clear_targeted_uncommit();

  _lock.notify_all();
}

void ZMemoryWorker::request_shrink_capacity(size_t requested_capacity) {
  precond(ZAdaptiveHeapSizing);

  if (!ZUncommit) {
    // Uncommit turned off.
    return;
  }

  const Ticks now = Ticks::now();

  ZLocker<ZConditionLock> locker(&_lock);
  set_targeted_uncommit(requested_capacity, now);
  _target_commit_capacity = 0u;

  _lock.notify_all();
}

void ZMemoryWorker::request_shrink_capacity_granule() {
  precond(ZAdaptiveHeapSizing);

  if (!ZUncommit) {
    // Uncommit turned off.
    return;
  }

  const Ticks now = Ticks::now();

  ZLocker<ZConditionLock> locker(&_lock);
  set_targetless_uncommit(now);

  _lock.notify_all();
}

static constexpr uint64_t TargetUncommitMinDelay = 10;

static bool has_uncommit_matured(Ticks now, Ticks since, uint64_t uncommit_delay) {
  precond(now != Ticks());
  precond(since != Ticks());

  return (now - since).milliseconds() >= uncommit_delay;
}

static uint64_t remaining_uncommit_wait_duration(Ticks since, uint64_t uncommit_delay) {
  precond(since != Ticks());

  const uint64_t elapsed = (Ticks::now() - since).milliseconds();
  return elapsed >= uncommit_delay ? 0 : uncommit_delay - elapsed;
}

static uint64_t targeted_uncommit_delay(uint64_t uncommit_delay) {
  return MIN2(TargetUncommitMinDelay, uncommit_delay);
}

static bool has_targetless_uncommit_matured(Ticks now, Ticks since, uint64_t uncommit_delay) {
  // Short term shrinking as requested by the director should shrink according
  // to the memory pressure based uncommit delay schedule.
  return has_uncommit_matured(now, since, uncommit_delay);
}

static bool has_targeted_uncommit_matured(Ticks now, Ticks since, uint64_t uncommit_delay) {
  precond(ZUncommit);

  // Long term shrinking as requested by a GC should shrink fairly quickly
  // regardless of memory pressure.
  const uint64_t targeted_delay = targeted_uncommit_delay(uncommit_delay);
  return has_uncommit_matured(now, since, targeted_delay);
}

void ZMemoryWorker::remove_heating_request_range(const ZVirtualMemory& vmem) {
  ZArray<ZHeatingRequestNode*> to_remove;

  const zoffset start_inclusive = vmem.start();
  const zoffset end_inclusive = vmem.start() + (vmem.size() - ZGranuleSize);
  _heating_requests.visit_range_in_order(start_inclusive, end_inclusive, [&](const ZHeatingRequestNode* node) {
    // Const cast the node, we only use it to modify the tree after
    // visit_range_in_order is completed.
    to_remove.push(const_cast<ZHeatingRequestNode*>(node));
    return true;
  });

  for (ZHeatingRequestNode* node: to_remove) {
    const ZVirtualMemory to_remove(node->key(), node->val());
    assert(vmem.contains(to_remove),
           "[" PTR_FORMAT ", " PTR_FORMAT "] [" PTR_FORMAT ", " PTR_FORMAT "]",
           untype(vmem.start()), untype(vmem.end()),
           untype(to_remove.start()), untype(to_remove.end()));
    _heating_requests.remove(node);
    _heating_request_bytes -= to_remove.size();
  }
}

void ZMemoryWorker::register_heating_request(const ZVirtualMemory& vmem) {
  precond(ZMemoryHeating);

  ZLocker<ZConditionLock> locker(&_lock);
  if (_stop) {
    // Don't add more requests during termination
    return;
  }

  if (!has_heating_request() && _currently_heating.is_null()) {
    // There is currently no heating going on, notify the memory worker.
    _lock.notify_all();
  }

  // Verification
  verify_heating_requests();
  ZOnScopeExit on_scope_exit([&]() { verify_heating_requests(); });

  assert(_currently_heating.is_null() || !vmem.overlaps(_currently_heating),
         "[" PTR_FORMAT ", " PTR_FORMAT "] [" PTR_FORMAT ", " PTR_FORMAT "]",
         untype(vmem.start()), untype(vmem.end()),
         untype(_currently_heating.start()), untype(_currently_heating.end()));

  const ZHeatingRequestTree::Cursor insert_cursor = _heating_requests.cursor(vmem.start());
  assert(!insert_cursor.found(), "must not be in tree");

  // Account for the new size upfront
  _heating_request_bytes += vmem.size();

  const ZHeatingRequestTree::Cursor prev_cursor = _heating_requests.prev(insert_cursor);
  const ZHeatingRequestTree::Cursor next_cursor = _heating_requests.next(insert_cursor);

  const bool extends_left = prev_cursor.valid() && prev_cursor.found() &&
                            zoffset_end(prev_cursor.node()->key() + prev_cursor.node()->val()) == vmem.start();

  const bool extends_right = next_cursor.valid() && next_cursor.found() &&
                             zoffset_end(next_cursor.node()->key()) == vmem.end();

  if (extends_left && extends_right) {
    const ZVirtualMemory left_vmem(prev_cursor.node()->key(), prev_cursor.node()->val());
    const ZVirtualMemory right_vmem(next_cursor.node()->key(), next_cursor.node()->val());
    assert(left_vmem.adjacent_to(vmem), "must be");
    assert(vmem.adjacent_to(right_vmem), "must be");

    const size_t new_size = left_vmem.size() + vmem.size() + right_vmem.size();
    // Update the left node's size
    prev_cursor.node()->set_val(new_size);

    // And remove the right node
    _heating_requests.remove(next_cursor.node());

    return;
  }

  if (extends_left) {
    const ZVirtualMemory left_vmem(prev_cursor.node()->key(), prev_cursor.node()->val());
    assert(left_vmem.adjacent_to(vmem), "must be");

    const size_t new_size = left_vmem.size() + vmem.size();
    prev_cursor.node()->set_val(new_size);

    return;
  }

  if (extends_right) {
    const ZVirtualMemory right_vmem(next_cursor.node()->key(), next_cursor.node()->val());
    assert(vmem.adjacent_to(right_vmem), "must be");

    ZVirtualMemory new_vmem = vmem;
    new_vmem.grow_from_back(right_vmem.size());

    // Right now we don't have a way to change the key of an inserted CHeap-allocated
    // node, so we have to remove the right node and insert a new node with the updated
    // key and size.
    _heating_requests.remove(next_cursor.node());

    ZHeatingRequestNode* const new_node = _heating_requests.allocate_node(new_vmem.start(), new_vmem.size());
    _heating_requests.insert(new_node->key(), new_node);

    return;
  }

  // The request does not extend another vmem to the left or right
  ZHeatingRequestNode* const new_node = _heating_requests.allocate_node(vmem.start(), vmem.size());
  _heating_requests.insert(new_node->key(), new_node);
}

ZVirtualMemory ZMemoryWorker::pop_heating_request() {
  assert(has_heating_request(), "precondition");

  // Verification
  verify_heating_requests();
  ZOnScopeExit on_scope_exit([&]() { verify_heating_requests(); });

  const size_t max_heating = 16 * ZGranuleSize;

  ZHeatingRequestNode* const node = _heating_requests.leftmost();
  const size_t size = node->val();

  // If the node is small enough, we remove it entirely from the tree. Otherwise,
  // we update the node's size in-place without removing and inserting it.

  if (size <= max_heating) {
    const ZVirtualMemory vmem(node->key(), size);
    _heating_requests.remove(node);
    _heating_request_bytes -= size;

    return vmem;
  }

  const size_t size_remainder = size - max_heating;
  const ZVirtualMemory vmem(node->key() + size_remainder, max_heating);

  // Update the value of the node to the new size
  node->set_val(size_remainder);
  _heating_request_bytes -= max_heating;

  return vmem;
}

void ZMemoryWorker::remove_heating_request(const ZVirtualMemory& vmem) {
  precond(ZMemoryHeating);

  ZLocker<ZConditionLock> locker(&_lock);

  // Verification
  verify_heating_requests();
  ZOnScopeExit on_scope_exit([&]() { verify_heating_requests(); });

  while (!_currently_heating.is_null() && vmem.overlaps(_currently_heating)) {
    // Wait while currently heating overlaps with this vmem
    _lock.wait();
  }

  const uintptr_t vmem_start = (uintptr_t)vmem.start();
  const uintptr_t vmem_end = vmem_start + vmem.size();

  // Cut off overlap on the left
  if (vmem.start() > zoffset(0)) {
    ZHeatingRequestNode* const left_node = _heating_requests.closest_leq(vmem.start() - ZGranuleSize);
    if (left_node != nullptr) {
      const uintptr_t left_start = (uintptr_t)left_node->key();
      const size_t left_size = left_node->val();
      const uintptr_t left_end = left_start + left_size;

      if (left_end > vmem_start) {
        // There is an intersection on the left side
        const size_t left_leading = vmem_start - left_start;
        left_node->set_val(left_leading);

        if (left_end > vmem_end) {
          // Intersection continues to the right side
          const size_t left_trailing = left_end - vmem_end;
          ZHeatingRequestNode* const new_node = _heating_requests.allocate_node(zoffset(vmem_end), left_trailing);
          _heating_requests.insert(zoffset(vmem_end), new_node);
          _heating_request_bytes -= vmem.size();
        } else {
          _heating_request_bytes -= left_size - left_leading;
        }
      }
    }
  }

  // Cut off overlap on the right
  ZHeatingRequestNode* const right_node = _heating_requests.closest_leq(vmem.start() + vmem.size());
  if (right_node != nullptr) {
    const uintptr_t right_start = (uintptr_t)right_node->key();
    const size_t right_size = right_node->val();
    const uintptr_t right_end = right_start + right_size;

    if (right_start < vmem_end && right_end > vmem_end) {
      // There is an intersection on the right side
      _heating_requests.remove(right_node);
      const size_t right_trailing = right_end - vmem_end;
      ZHeatingRequestNode* const new_node = _heating_requests.allocate_node(zoffset(vmem_end), right_trailing);
      _heating_requests.insert(zoffset(vmem_end), new_node);
      _heating_request_bytes -= right_size - right_trailing;
    }
  }

  remove_heating_request_range(vmem);
}

size_t ZMemoryWorker::process_heating_request() {
  ZVirtualMemory vmem;
  {
    ZLocker<ZConditionLock> locker(&_lock);
    if (!has_heating_request()) {
      // Unmapping removed the request; bail
      return 0;
    }

    assert(has_heating_request(), "who else processed it?");

    vmem = pop_heating_request();

    assert(_currently_heating.is_null(), "must be");
    _currently_heating = vmem;
  }

  _partition->heat_memory(vmem);

  {
    ZLocker<ZConditionLock> locker(&_lock);
    _currently_heating = {};
    _lock.notify_all();
  }

  return vmem.size();
}

bool ZMemoryWorker::await_start() {
  for (;;) {
    if (_stop) {
      return false;
    }

    if (is_init_completed()) {
      return true;
    }

    // Don't start working until JVM is bootstrapped
    _lock.wait();
  }
}

class ZMemoryWorker::ZMemoryWorkerState {
private:
  using Mode = ZMemoryWorkerMode;
  ZMemoryWorker* const _worker;
  Ticks _init_time;
  Ticks _update_time;
  size_t _processed;
  size_t _current_target_capacity;
  size_t _init_target_capacity;
  size_t _target_capacity;
  uint64_t _uncommit_delay;
  Mode _mode;

  bool try_commit() {
    precond(ZAdaptiveHeapSizing);
    precond(_mode == Mode::Commit);

    const bool targetless_uncommit = _worker->_targetless_uncommit;

    if (targetless_uncommit && has_targetless_uncommit_matured(_update_time, _worker->_uncommit_request_time, _uncommit_delay)) {
      const size_t min_capacity = _worker->_partition->_static_min_capacity;
      if (_current_target_capacity > min_capacity) {
        // This might cause _current_target_capacity to undershoot the current capacity.
        // So we might not uncommit the whole uncommit granule in this target capacity truncation.
        // We allow this discrepancy.
        const size_t uncommit_size = _worker->uncommit_granule(_current_target_capacity);
        const size_t shrink_amount = MIN2(_current_target_capacity - min_capacity, uncommit_size);

        // Shrink heuristic max
        _worker->_partition->_page_allocator->shrink_heuristic_max(shrink_amount);
        log_debug(gc, heap)("Memory Worker (%d) Prevented Commit: %zuM(%.0f%%)",
                            _worker->_id, shrink_amount / M,
                            percent_of(shrink_amount, _current_target_capacity));

        assert(_worker->_target_commit_capacity == _current_target_capacity,
               "Should not have released the lock since update targets");
        _current_target_capacity -= shrink_amount;
        _worker->_target_commit_capacity -= shrink_amount;
        _worker->_uncommit_request_time = _update_time;
      }
    }

    const size_t commit_size = _worker->commit_granule(_current_target_capacity);
    const size_t processed = [&]() {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
      return _worker->_partition->increase_and_commit_capacity(commit_size, _current_target_capacity);
    }();

    _processed += processed;

    return processed > 0;
  }

  bool try_uncommit() {
    precond(ZAdaptiveHeapSizing);
    precond(_mode == Mode::Uncommit);
    precond(ZUncommit);

    const size_t uncommit_size = _worker->uncommit_granule(_current_target_capacity);
    const size_t processed = [&]() {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
      return _worker->uncommit(uncommit_size);
    }();

    _processed += processed;

    return processed > 0;
  }

  bool try_targetless_uncommit() {
    precond(ZAdaptiveHeapSizing);
    precond(_mode == Mode::UncommitTargetless);
    precond(ZUncommit);
    precond(_target_capacity == 0);
    precond(_init_target_capacity == 0);

    const size_t uncommit_size = _worker->uncommit_granule(_current_target_capacity);
    const size_t processed = [&]() {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
      return _worker->uncommit(uncommit_size);
    }();

    _processed += processed;

    return processed > 0;
  }

  bool try_heat() {
    if (!_worker->has_heating_request()) {
      return false;
    }
    precond(ZMemoryHeating);

    const size_t processed = [&]() {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
      return _worker->process_heating_request();
    }();

    _processed += processed;

    return processed > 0;
  }

public:
  explicit ZMemoryWorkerState(ZMemoryWorker* worker)
    : _worker(worker),
      _init_time(),
      _update_time(),
      _processed(0),
      _current_target_capacity(0),
      _init_target_capacity(0),
      _target_capacity(0),
      _uncommit_delay(0),
      _mode(Mode::Uninitialized) {}

  ~ZMemoryWorkerState() {
    postcond(_mode != Mode::Uninitialized);

    if (_processed > 0) {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
      const char* const mode_string = memory_worker_completed_mode_name(_mode);
      const auto elapsed = Ticks::now() - _init_time;
      const size_t processed_mb = _processed / M;
      const double process_rate_mb_per_sec = double(processed_mb) / elapsed.seconds();

      if (_mode == Mode::UncommitTargetless) {
        log_info(gc, heap)("Memory Worker (%d) %s: %zuM in %.3fms (%.2f MiB/s)",
                           _worker->_id, mode_string, processed_mb,
                           elapsed.seconds() * MILLIUNITS, process_rate_mb_per_sec);
      } else {
        LogTargetHandle lt = [&]() -> LogTargetHandle {
          if (_mode == ZMemoryWorkerMode::Heat) {
            return LogTarget(Debug, gc, heap)();
          } else {
            return LogTarget(Info, gc, heap)();
          }
        }();

        const double percent_of_init_target = percent_of(_processed, _init_target_capacity);
        lt.print("Memory Worker (%d) %s: %zuM(%.0f%%) in %.3fms (%.2f MiB/s)",
                 _worker->_id, mode_string, processed_mb, percent_of_init_target,
                 elapsed.seconds() * MILLIUNITS, process_rate_mb_per_sec);
      }
    }
  }

  void select_mode() {
    precond(_mode == Mode::Uninitialized);

    {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
      _init_time = Ticks::now();
      if (ZAdaptiveHeapSizing) {
        const size_t uncommit_size = _worker->uncommit_granule(_current_target_capacity);
        _uncommit_delay = ZAdaptiveHeap::uncommit_delay(uncommit_size);
      }
    }

    const size_t target_commit_capacity = _worker->_target_commit_capacity;
    const size_t target_uncommit_capacity = _worker->_target_uncommit_capacity;
    const size_t capacity = _worker->_partition->capacity();
    const size_t min_capacity = _worker->_partition->_static_min_capacity;

    if (target_commit_capacity > 0 && capacity < target_commit_capacity) {
      // First priority is committing memory

      postcond(ZAdaptiveHeapSizing);
      postcond(target_uncommit_capacity == 0);

      _mode = Mode::Commit;
      _init_target_capacity = target_commit_capacity;
    } else if (target_uncommit_capacity > 0 && capacity > target_uncommit_capacity) {
      // Second priority is uncommitting memory

      postcond(ZAdaptiveHeapSizing);
      postcond(ZUncommit);
      postcond(target_commit_capacity == 0);
      postcond(target_uncommit_capacity >= min_capacity);

      _mode = Mode::Uncommit;
      _init_target_capacity = target_uncommit_capacity;
    } else {
      // Third priority is uncommitting memory if a targetless uncommit matured
      // or heating memory if available.
      const bool targetless_uncommit_matured = _worker->_targetless_uncommit && ZUncommit &&
          has_targetless_uncommit_matured(_init_time, _worker->_uncommit_request_time, _uncommit_delay);

      if (targetless_uncommit_matured && capacity != min_capacity) {
        postcond(ZAdaptiveHeapSizing);
        _mode = Mode::UncommitTargetless;
      } else if (_worker->has_heating_request()) {
        _mode = Mode::Heat;
        _init_target_capacity = _worker->_heating_request_bytes;
      } else {
        _mode = Mode::Wait;
      }

      // Ensure that we have no other targets
      _worker->_target_commit_capacity = 0u;
      _worker->clear_targeted_uncommit();
    }

    LogTarget(Debug, gc, heap) lt;

    if (_init_target_capacity > 0 && lt.is_enabled()) {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);

      const size_t capacity = _worker->_partition->capacity();
      const char* const mode_string = memory_worker_start_mode_name(_mode);

      lt.print("Memory Worker (%d) Start %s, Target: %zuM(%.0f%%)",
               _worker->_id, mode_string, _init_target_capacity / M,
               percent_of(_init_target_capacity, capacity));
    }

    postcond(_mode != Mode::Uninitialized);
  }

  bool update_targets() {
    precond(_mode != Mode::Uninitialized);

    if (_mode == Mode::Wait) {
      // Go directly to await
      return true;
    }

    {
      ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
      _update_time = Ticks::now();
      if (ZAdaptiveHeapSizing) {
        const size_t uncommit_size = _worker->uncommit_granule(_current_target_capacity);
        _uncommit_delay = ZAdaptiveHeap::uncommit_delay(uncommit_size);
      }
    }

    const bool targetless_uncommit = _worker->_targetless_uncommit;
    const size_t capacity = _worker->_partition->capacity();
    const size_t target_commit_capacity = _worker->_target_commit_capacity;
    const size_t target_uncommit_capacity = _worker->_target_uncommit_capacity;

    switch (_mode) {
    case Mode::Commit: {
      _current_target_capacity = target_commit_capacity;
      return capacity < target_commit_capacity;
    }
    case Mode::Uncommit: {
      _current_target_capacity = target_uncommit_capacity;
      return target_uncommit_capacity != 0 && capacity > target_uncommit_capacity;
    }
    case Mode::UncommitTargetless: {
      postcond(_init_target_capacity == 0);
      postcond(_current_target_capacity == 0);
      return target_commit_capacity == 0 && target_uncommit_capacity == 0 && targetless_uncommit;
    }
    case Mode::Heat: {
      if (target_commit_capacity != 0 || target_uncommit_capacity != 0) {
        // We have more important work to do
        return false;
      }

      return _processed < _init_target_capacity;
    }
    default:
      return false;
    }
  }

  bool try_work() {
    switch (_mode) {
    case Mode::Commit: {
      return try_commit();
    }
    case Mode::Uncommit: {
      if (!has_targeted_uncommit_matured(_update_time, _worker->_uncommit_request_time, _uncommit_delay)) {
        // Wait for the uncommit request to mature
        return true;
      }

      if (try_uncommit()) {
        // Set new uncommit request time
        _worker->_uncommit_request_time = _update_time;
        return true;
      }

      // Failed to uncommit
      // Reset the uncommit target
      _worker->clear_targeted_uncommit();

      return false;
    }
    case Mode::UncommitTargetless: {
      if (!has_targetless_uncommit_matured(_update_time, _worker->_uncommit_request_time, _uncommit_delay)) {
        // Wait for the uncommit request to mature
        return true;
      }

      if (try_targetless_uncommit()) {
        // Set new uncommit request time
        _worker->_uncommit_request_time = _update_time;
        return true;
      }

      // Failed to uncommit
      // Stop targetless uncommit
      _worker->clear_targetless_uncommit();

      return false;
    }
    case Mode::Heat: {
      return try_heat();
    }
    case Mode::Wait: {
      // Go directly to await
      return true;
    }
    default:
      ShouldNotReachHere();
    }
  }

  bool await_work() {
    switch (_mode) {
    case Mode::Commit: {
      // Commit immediately unless we have reached capacity
      return _worker->_partition->capacity() < _current_target_capacity;
    }
    case Mode::Uncommit: {
      precond(_current_target_capacity != 0);

      const auto get_wait_duration = [&]() -> uint64_t {
        // Read the _uncommit_request_time before releasing the lock
        const Ticks request_time = _worker->_uncommit_request_time;

        ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
        const size_t uncommit_size = _worker->uncommit_granule(_current_target_capacity);
        const uint64_t targeted_delay = targeted_uncommit_delay(ZAdaptiveHeap::uncommit_delay(uncommit_size));
        return remaining_uncommit_wait_duration(request_time, targeted_delay);
      };

      const auto is_uncommit_done = [&]() {
        const size_t capacity = _worker->_partition->capacity();
        const size_t target_uncommit_capacity = _worker->_target_uncommit_capacity;
        return target_uncommit_capacity == 0 || capacity <= target_uncommit_capacity;
      };

      for (;;) {
        // Is there more work?
        if (_worker->is_stop_requested() || is_uncommit_done()) {
          return false;
        }

        const uint64_t wait_duration = get_wait_duration();

        // Is there more work?
        if (_worker->is_stop_requested() || is_uncommit_done()) {
          return false;
        }

        if (wait_duration == 0) {
          // We are done waiting
          return true;
        }

        // Wait for uncommit
        _worker->_lock.wait(wait_duration);
      }

      return true;
    }
    case Mode::UncommitTargetless: {
      // Never await targetless uncommit
      return false;
    }
    case Mode::Heat: {
      // Heat immediately if there are more requests
      return _worker->has_heating_request();
    }
    case Mode::Wait: {
      const auto get_targetless_uncommit_wait_duration = [&]() -> uint64_t {
        // Read the _uncommit_request_time before releasing the lock
        const Ticks request_time = _worker->_uncommit_request_time;

        ZUnlocker<ZConditionLock> unlocker(&_worker->_lock);
        const size_t uncommit_size = _worker->uncommit_granule(_current_target_capacity);
        const uint64_t uncommit_delay = ZAdaptiveHeap::uncommit_delay(uncommit_size);
        return remaining_uncommit_wait_duration(request_time, uncommit_delay);
      };

      const auto check_for_work_non_targetless_uncommit = [&]() {
        if (_worker->_target_commit_capacity > 0) {
          // There is commit work to be done
          return true;
        }

        if (_worker->_target_uncommit_capacity > 0) {
          // There is uncommit work to be done
          return true;
        }

        if (_worker->_heating_request_bytes > 0) {
          // There is heating work to be done
          return true;
        }

        return false;
      };

      for (;;) {
        if (check_for_work_non_targetless_uncommit() || _worker->is_stop_requested()) {
          return false;
        }

        // If the capacity is already minimal, wait until something changes externally.
        const size_t capacity = _worker->_partition->capacity();
        const size_t min_capacity = _worker->_partition->_static_min_capacity;

        if (_worker->_targetless_uncommit && capacity != min_capacity) {
          // There might be targetless uncommit work to be done
          const uint64_t wait_duration = get_targetless_uncommit_wait_duration();

          if (check_for_work_non_targetless_uncommit() || wait_duration == 0 || _worker->is_stop_requested()) {
          // There is targetless work to be done or the targetless uncommit has matured
            return false;
          }

          // Wait for notification or targetless maturity
          _worker->_lock.wait(wait_duration);
        } else {
          // Wait for more work
          _worker->_lock.wait();
        }
      }
    }
    default:
      ShouldNotReachHere();
    }
  }
};

class ZMemoryWorkerTimer {
private:
  jlong _start;

public:
  ZMemoryWorkerTimer()
    : _start(os::current_thread_cpu_time(true /* user + sys */)) {}

  ~ZMemoryWorkerTimer() {
    jlong elapsed = os::current_thread_cpu_time(true /* user + sys */) - _start;
    ZStatMemoryWorkers::add_accumulated_vtime(double(elapsed) / NANOSECS_PER_SEC);
  }
};

void ZMemoryWorker::run_thread() {
  // We always hold the lock, except when interacting with the OS, or awaiting
  // more work.
  ZLocker<ZConditionLock> locker(&_lock);

  // Wait until started
  if (!await_start()) {
    // GC initialization failed
    return;
  }

  while (!_stop) {
    ZMemoryWorkerState worker_state(this);

    worker_state.select_mode();

    while (!_stop) {
      ZMemoryWorkerTimer timer;

      if (!worker_state.update_targets()) {
        break;
      }

      if (!worker_state.try_work()) {
        break;
      }

      if (!worker_state.await_work()) {
        break;
      }
    }
  }
}

size_t ZMemoryWorker::uncommit(size_t to_uncommit) {
  ZArray<ZVirtualMemory> flushed_vmems;
  size_t flushed = 0;

  {
    // We need to join the suspendible thread set while manipulating capacity
    // and used, to make sure GC safepoints will have a consistent view.
    SuspendibleThreadSetJoiner sts_joiner;
    ZLocker<ZLock> locker(&_partition->_page_allocator->_lock);

    ZMappedCache& cache = _partition->_cache;

    // Never uncommit below min capacity
    const size_t retain = MAX2(_partition->_used, _partition->_static_min_capacity);
    const size_t release = _partition->_capacity - retain;
    const size_t flush = MIN2(release, to_uncommit);

    // Flush memory from the mapped cache for uncommit
    flushed = cache.remove_for_uncommit(flush, &flushed_vmems);
    if (flushed == 0) {
      // Nothing flushed
      return 0;
    }

    // Record flushed memory as claimed and how much we've flushed for this partition
    _partition->increase_claimed(flushed);
  }

  // Unmap and uncommit flushed memory
  for (const ZVirtualMemory vmem : flushed_vmems) {
    _partition->unmap_virtual(vmem);
    _partition->uncommit_physical(vmem);
    _partition->free_physical(vmem);
    _partition->free_virtual(vmem);
  }

  {
    SuspendibleThreadSetJoiner sts_joiner;
    ZLocker<ZLock> locker(&_partition->_page_allocator->_lock);

    // Adjust claimed and capacity to reflect the uncommit
    _partition->decrease_claimed(flushed);
    _partition->decrease_capacity(flushed);
  }

  if (flushed > 0) {
    _partition->_page_allocator->truncate_heuristic_max_after_capacity_decrease(ZPageAllocator::TruncationReason::Uncommit);
  }

  return flushed;
}

void ZMemoryWorker::terminate() {
  ZLocker<ZConditionLock> locker(&_lock);
  _stop = true;
  _lock.notify_all();

  _heating_requests.remove_all();
  _heating_request_bytes = 0;

  while (!_currently_heating.is_null()) {
    // Trying to unmap what's currently being heated; calm down!
    _lock.wait();
  }
}

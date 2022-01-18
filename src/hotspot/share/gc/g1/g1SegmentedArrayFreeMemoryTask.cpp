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
#include "ci/ciUtilities.hpp"
#include "gc/g1/g1CardSetMemory.inline.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1SegmentedArrayFreeMemoryTask.hpp"
#include "gc/g1/g1_globals.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "runtime/os.hpp"

template<MEMFLAGS flag, typename Configuration>
constexpr const char* G1SegmentedArrayFreeMemoryTask<flag, Configuration>::_state_names[];

template<MEMFLAGS flag, typename Configuration>
const char* G1SegmentedArrayFreeMemoryTask<flag, Configuration>::get_state_name(State value) const {
  return _state_names[static_cast<std::underlying_type_t<State>>(value)];
}

template<MEMFLAGS flag, typename Configuration>
bool G1SegmentedArrayFreeMemoryTask<flag, Configuration>::deadline_exceeded(jlong deadline) {
  return os::elapsed_counter() >= deadline;
}

static size_t keep_size(size_t free, size_t used, double percent) {
  size_t to_keep = used * percent;
  return MIN2(free, to_keep);
}

template<MEMFLAGS flag, typename Configuration>
bool G1SegmentedArrayFreeMemoryTask<flag, Configuration>::calculate_return_infos(jlong deadline) {
  // Ignore the deadline in this step as it is very short.

  G1SegmentedArrayMemoryStats used = _total_used;
  G1SegmentedArrayMemoryStats free = G1SegmentedArrayFreePool<flag, Configuration>::free_list_sizes();

  _return_info = new G1ReturnMemoryProcessorSet(NUM);
  for (uint i = 0; i < NUM; i++) {
    size_t return_to_vm_size = keep_size(free._num_mem_sizes[i],
                                         used._num_mem_sizes[i],
                                         G1RemSetFreeMemoryKeepExcessRatio);
    log_trace(gc, task)("Segmented Array Free Memory: Type %s: Free: %zu (%zu) "
                        "Used: %zu Keep: %zu",
                        Configuration::mem_object_type_name_str(i),
                        free._num_mem_sizes[i], free._num_segments[i],
                        used._num_mem_sizes[i], return_to_vm_size);

    _return_info->append(new G1ReturnMemoryProcessor(return_to_vm_size));
  }

  G1SegmentedArrayFreePool<flag, Configuration>::update_unlink_processors(_return_info);
  return false;
}

template<MEMFLAGS flag, typename Configuration>
bool G1SegmentedArrayFreeMemoryTask<flag, Configuration>::return_memory_to_vm(jlong deadline) {
  for (int i = 0; i < _return_info->length(); i++) {
    G1ReturnMemoryProcessor* info = _return_info->at(i);
    if (!info->finished_return_to_vm()) {
      if (info->return_to_vm(deadline)) {
        return true;
      }
    }
  }
  return false;
}

template<MEMFLAGS flag, typename Configuration>
bool G1SegmentedArrayFreeMemoryTask<flag, Configuration>::return_memory_to_os(jlong deadline) {
  for (int i = 0; i < _return_info->length(); i++) {
    G1ReturnMemoryProcessor* info = _return_info->at(i);
    if (!info->finished_return_to_os()) {
      if (info->return_to_os(deadline)) {
        return true;
      }
    }
  }
  return false;
}

template<MEMFLAGS flag, typename Configuration>
bool G1SegmentedArrayFreeMemoryTask<flag, Configuration>::cleanup_return_infos() {
  for (int i = 0; i < _return_info->length(); i++) {
     G1ReturnMemoryProcessor* info = _return_info->at(i);
     delete info;
  }
  delete _return_info;

  _return_info = nullptr;
  return false;
}

template<MEMFLAGS flag, typename Configuration>
bool G1SegmentedArrayFreeMemoryTask<flag, Configuration>::free_excess_segmented_array_memory() {
  jlong start = os::elapsed_counter();
  jlong end = start +
              (os::elapsed_frequency() / 1000) * G1RemSetFreeMemoryStepDurationMillis;

  log_trace(gc, task)("Segmented Array Free Memory: Step start %1.3f end %1.3f",
                      TimeHelper::counter_to_millis(start), TimeHelper::counter_to_millis(end));

  State next_state;

  do {
    switch (_state) {
      case State::CalculateUsed: {
        if (calculate_return_infos(end)) {
          next_state = _state;
          return true;
        }
        next_state = State::ReturnToVM;
        break;
      }
      case State::ReturnToVM: {
        if (return_memory_to_vm(end)) {
          next_state = _state;
          return true;
        }
        next_state = State::ReturnToOS;
        break;
      }
      case State::ReturnToOS: {
        if (return_memory_to_os(end)) {
          next_state = _state;
          return true;
        }
        next_state = State::Cleanup;
        break;
      }
      case State::Cleanup: {
        cleanup_return_infos();
        next_state = State::Inactive;
        break;
      }
      default:
        log_error(gc, task)("Should not try to free excess segmented array memory in %s state", get_state_name(_state));
        ShouldNotReachHere();
        break;
    }

    set_state(next_state);
  } while (_state != State::Inactive && !deadline_exceeded(end));

  log_trace(gc, task)("Segmented Array Free Memory: Step took %1.3fms, done %s",
                      TimeHelper::counter_to_millis(os::elapsed_counter() - start),
                      bool_to_str(_state == State::CalculateUsed));

  return is_active();
}

template<MEMFLAGS flag, typename Configuration>
void G1SegmentedArrayFreeMemoryTask<flag, Configuration>::set_state(State new_state) {
  log_trace(gc, task)("Segmented Array Free Memory: State change from %s to %s",
                      get_state_name(_state),
                      get_state_name(new_state));
  _state = new_state;
}

template<MEMFLAGS flag, typename Configuration>
bool G1SegmentedArrayFreeMemoryTask<flag, Configuration>::is_active() const {
  return _state != State::Inactive;
}

template<MEMFLAGS flag, typename Configuration>
jlong G1SegmentedArrayFreeMemoryTask<flag, Configuration>::reschedule_delay_ms() const {
  return G1RemSetFreeMemoryRescheduleDelayMillis;
}

template<MEMFLAGS flag, typename Configuration>
G1SegmentedArrayFreeMemoryTask<flag, Configuration>::G1SegmentedArrayFreeMemoryTask(const char* name) :
  G1ServiceTask(name), _state(State::CalculateUsed), _return_info(nullptr) { }

template<MEMFLAGS flag, typename Configuration>
void G1SegmentedArrayFreeMemoryTask<flag, Configuration>::execute() {
  SuspendibleThreadSetJoiner sts;

  if (free_excess_segmented_array_memory()) {
    schedule(reschedule_delay_ms());
  }
}

template<MEMFLAGS flag, typename Configuration>
void G1SegmentedArrayFreeMemoryTask<flag, Configuration>::notify_new_stats(G1SegmentedArrayMemoryStats* young_gen_stats,
                                                                           G1SegmentedArrayMemoryStats* collection_set_candidate_stats) {
  assert_at_safepoint_on_vm_thread();

  _total_used = *young_gen_stats;
  _total_used.add(*collection_set_candidate_stats);

  if (!is_active()) {
    set_state(State::CalculateUsed);
    G1CollectedHeap::heap()->service_thread()->schedule_task(this, 0);
  }
}

template class G1SegmentedArrayFreeMemoryTask<mtGCCardSet, G1CardSetConfiguration>;

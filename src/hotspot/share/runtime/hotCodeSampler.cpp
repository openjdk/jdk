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
#include "code/nmethod.hpp"
#include "logging/log.hpp"
#include "runtime/hotCodeSampler.hpp"
#include "runtime/javaThread.inline.hpp"

#if INCLUDE_JFR
#include "jfr/utilities/jfrTryLock.hpp"

using SuspendedThreadTaskTryLock = JfrMutexTryLock;
#endif

static inline uint64_t nmethod_id(nmethod* nm, int compile_id) {
  uint64_t offset = (uint64_t)((uintptr_t)nm - (uintptr_t)CodeCache::low_bound());
  guarantee(offset < (uint64_t)4*G, "code cache offset overflow");
  return (offset << 32) | (uint32_t)compile_id;
}

uint32_t Candidates::nmethod_compile_id(uint64_t nm_id) {
  return nm_id & 0xffffffffU;
}

nmethod* Candidates::nmethod_from_id(uint64_t nm_id) {
  return (nmethod*)((uintptr_t)(nm_id >> 32) + CodeCache::low_bound());
}

bool ThreadSampler::sample_all_java_threads() {
  // Collect samples for each JavaThread
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    if (jt->is_hidden_from_external_view() ||
        jt->in_deopt_handler() ||
        (jt->thread_state() != _thread_in_native && jt->thread_state() != _thread_in_Java)) {
      continue;
    }

    GetPCTask task(jt);
    {
#if INCLUDE_JFR
      SuspendedThreadTaskTryLock try_lock(SuspendedThreadTask_lock);
      if (!try_lock.acquired()) {
        log_debug(hotcode)("Suspend lock held by JFR sampler; stopping this sampling round, will retry after %u seconds", HotCodeIntervalSeconds);
        return false;
      }
#endif
      task.run();
    }

    address pc = task.pc();
    if (pc == nullptr) {
      continue;
    }

    CodeBlob* cb = CodeCache::find_blob(pc);
    if (cb == nullptr || !cb->is_nmethod()) {
      continue;
    }

    nmethod* nm = cb->as_nmethod();
    int compile_id = nm->compile_id();
    CodeBlobType code_blob_type = CodeCache::get_code_blob_type(nm);

    if (code_blob_type == CodeBlobType::MethodHot) {
      _hot_sample_count++;
      continue;
    }

    if (code_blob_type != CodeBlobType::MethodNonProfiled) {
      continue;
    }

    _non_profiled_sample_count++;

    bool created = false;
    int* count = _samples.put_if_absent(nmethod_id(nm, compile_id), 0, &created);
    (*count)++;
    if (created) {
      _samples.maybe_grow();
    }
  }
  return true;
}

Candidates::Candidates(ThreadSampler& sampler)
  : _hot_sample_count(sampler.hot_sample_count()),
    _non_profiled_sample_count(sampler.non_profiled_sample_count()) {
  auto func = [&](uint64_t nm_id, int count) {
    _candidates.append(Pair<uint64_t, int>(nm_id, count));
  };
  sampler.iterate_samples(func);

  log_info(hotcode)("Generated candidate list from %d samples corresponding to %d nmethods", _non_profiled_sample_count + _hot_sample_count, _candidates.length());
}

void Candidates::move_samples_to_hot(int count) {
  _hot_sample_count += count;
  _non_profiled_sample_count -= count;
}

void Candidates::sort() {
  _candidates.sort(
    [](Pair<uint64_t, int>* a, Pair<uint64_t, int>* b) {
      if (a->second > b->second) return 1;
      if (a->second < b->second) return -1;
      return 0;
    }
  );
}

bool Candidates::has_candidates() {
  return !_candidates.is_empty();
}

Pair<uint64_t, int> Candidates::get_candidate() {
  assert(has_candidates(), "must not be empty");
  return _candidates.pop();
}

double Candidates::get_hot_sample_percent() {
  if (_hot_sample_count + _non_profiled_sample_count == 0) {
    return 0;
  }

  return 100.0 * _hot_sample_count / (_hot_sample_count + _non_profiled_sample_count);
}

#endif // COMPILER2

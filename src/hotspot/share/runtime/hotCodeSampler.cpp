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

    // We can dereference the nmethod pointer here because we are sampling from a JavaThread and
    // the code blob cannot be purged while the thread does not reach a safepoint.
    assert(Thread::current()->is_Java_thread(), "ThreadSampler should only be called from a JavaThread");
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
    Pair<nmethod*, int>* sampled_nm = _samples.put_if_absent(compile_id, Pair(nm, 0), &created);
    sampled_nm->second++;
    if (created) {
      _samples.maybe_grow();
    }
  }
  return true;
}

#define NMETHOD_PTR(sampled_nm) (sampled_nm.first)
#define SAMPLE_COUNT(sampled_nm) (sampled_nm.second)

Candidates::Candidates(ThreadSampler& sampler)
  : _hot_sample_count(sampler.hot_sample_count()),
    _non_profiled_sample_count(sampler.non_profiled_sample_count()) {
  auto func = [&](int compile_id, const Pair<nmethod*, int> sampled_nm) {
    _candidates.append(Candidate(NMETHOD_PTR(sampled_nm), compile_id, SAMPLE_COUNT(sampled_nm)));
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
    [](Candidate* a, Candidate* b) {
      if (a->get_sample_count() > b->get_sample_count()) return 1;
      if (a->get_sample_count() < b->get_sample_count()) return -1;
      return 0;
    }
  );
}

bool Candidates::has_candidates() {
  return !_candidates.is_empty();
}

Candidate Candidates::get_candidate() {
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

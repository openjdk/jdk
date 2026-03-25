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
#include "logging/log.hpp"
#include "runtime/hotCodeSampler.hpp"
#include "runtime/javaThread.inline.hpp"

void ThreadSampler::sample_all_java_threads() {
  uint64_t start_time = os::javaTimeMillis();

  // Collect samples for each JavaThread
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    if (jt->is_hidden_from_external_view() ||
        jt->in_deopt_handler() ||
        (jt->thread_state() != _thread_in_native && jt->thread_state() != _thread_in_Java)) {
      continue;
    }

    GetPCTask task(jt);
    task.run();
    address pc = task.pc();
    if (pc == nullptr) {
      continue;
    }

    if (CodeCache::contains(pc)) {
      nmethod* nm = CodeCache::find_blob(pc)->as_nmethod_or_null();
      if (nm != nullptr) {
        bool created = false;
        int *count = _samples.put_if_absent(nm, 0, &created);
        (*count)++;
        if (created) {
          _samples.maybe_grow();
        }
      }
    }
  }
}

Candidates::Candidates(ThreadSampler& sampler)
  : _hot_sample_count(0), _non_profiled_sample_count(0) {
  auto func = [&](nmethod* nm, int count) {
    if (CodeCache::get_code_blob_type(nm) == CodeBlobType::MethodNonProfiled) {
      _candidates.append(Pair<nmethod*, int>(nm, count));
      add_non_profiled_sample_count(count);
    } else if (CodeCache::get_code_blob_type(nm) == CodeBlobType::MethodHot) {
      add_hot_sample_count(count);
    }
  };
  sampler.iterate_samples(func);

  log_info(hotcode)("Generated candidate list from %d samples corresponding to %d nmethods", _non_profiled_sample_count + _hot_sample_count, _candidates.length());
}

void Candidates::add_candidate(nmethod* nm, int count) {
  _candidates.append(Pair<nmethod*, int>(nm, count));
}

void Candidates::add_hot_sample_count(int count) {
  _hot_sample_count += count;
}

void Candidates::add_non_profiled_sample_count(int count) {
  _non_profiled_sample_count += count;
}

void Candidates::sort() {
  _candidates.sort(
    [](Pair<nmethod*, int>* a, Pair<nmethod*, int>* b) {
      if (a->second > b->second) return 1;
      if (a->second < b->second) return -1;
      return 0;
    }
  );
}

bool Candidates::has_candidates() {
  return !_candidates.is_empty();
}

nmethod* Candidates::get_candidate() {
  assert(has_candidates(), "must not be empty");
  Pair<nmethod*, int> candidate = _candidates.pop();

  _hot_sample_count += candidate.second;
  _non_profiled_sample_count -= candidate.second;

  return candidate.first;
}

double Candidates::get_hot_sample_percent() {
  if (_hot_sample_count + _non_profiled_sample_count == 0) {
    return 0;
  }

  return 100.0 * _hot_sample_count / (_hot_sample_count + _non_profiled_sample_count);
}

#endif // COMPILER2

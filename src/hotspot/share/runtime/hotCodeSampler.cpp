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

#include "runtime/hotCodeSampler.hpp"

ThreadSampler* ThreadSampler::_current_sampler = nullptr;

void ThreadSampler::do_sampling() {
  log_info(hotcodegrouper)("Sampling...");

  int total_samples = 0;

  uint64_t start_time = os::javaTimeMillis();

  while (true) {
    { // Collect sample for each JavaThread
      MutexLocker ml(Threads_lock);

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

        total_samples++;

        if (!Interpreter::contains(pc) && CodeCache::contains(pc)) {
          nmethod* nm = CodeCache::find_blob(pc)->as_nmethod_or_null();
          if (nm != nullptr) {
            bool created = false;
            int *count = _samples.put_if_absent(nm, 0, &created);
            (*count)++;
            if (created) {
              _samples.maybe_grow();
            }
            nm->mark_as_maybe_on_stack();
          }
        }
      }
    }

    // Check if we have sampled long enough
    if (os::javaTimeMillis() - start_time > HotCodeSampleSeconds * 1000) {
      log_info(hotcodegrouper)("Profiling complete: collected %d samples corresponding to %d nmethods", total_samples, _samples.number_of_entries());
      generate_sorted_candidate_list();
      return;
    }

    os::naked_sleep(rand_sampling_period_ms());
  }
}

void ThreadSampler::generate_sorted_candidate_list() {
  assert(_sorted_candidate_list.is_empty(), "should only generate once");

  // Add every C2 nmethod not in hot code heap to array
  auto func = [&](nmethod* nm, uint64_t count) {
    if (CodeCache::get_code_blob_type(nm) == CodeBlobType::MethodNonProfiled) {
      _non_profiled_sample_count += count;
      _sorted_candidate_list.append(nm);
    } else if (CodeCache::get_code_blob_type(nm) == CodeBlobType::MethodHot) {
      _hot_sample_count += count;
    }
  };
  _samples.iterate_all(func);

  // Sort nmethods by increasing sample count
  assert(_current_sampler == nullptr, "not thread safe");
  _current_sampler = this;
  _sorted_candidate_list.sort(
    [](nmethod** a, nmethod** b) {
      ThreadSampler* self = get_current_sampler();
      if (*(self->_samples.get(*a)) > *(self->_samples.get(*b))) return 1;
      if (*(self->_samples.get(*a)) < *(self->_samples.get(*b))) return -1;
      return 0;
    }
  );
  _current_sampler = nullptr;
}

#endif // COMPILER2

/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/taskqueue.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/stack.inline.hpp"

#if TASKQUEUE_STATS
const char * const TaskQueueStats::_names[last_stat_id] = {
  "push", "pop", "pop-slow",
  "st-attempt", "st-empty", "st-ctdd", "st-success", "st-ctdd-max", "st-biasdrop",
  "ovflw-push", "ovflw-max"
};

TaskQueueStats & TaskQueueStats::operator +=(const TaskQueueStats & addend)
{
  for (unsigned int i = 0; i < last_stat_id; ++i) {
    _stats[i] += addend._stats[i];
  }
  return *this;
}

void TaskQueueStats::print_header(unsigned int line, outputStream* const stream,
                                  unsigned int width)
{
  // Use a width w: 1 <= w <= max_width
  const unsigned int max_width = 40;
  const unsigned int w = clamp(width, 1u, max_width);

  if (line == 0) { // spaces equal in width to the header
    const unsigned int hdr_width = w * last_stat_id + last_stat_id - 1;
    stream->print("%*s", hdr_width, " ");
  } else if (line == 1) { // labels
    stream->print("%*s", w, _names[0]);
    for (unsigned int i = 1; i < last_stat_id; ++i) {
      stream->print(" %*s", w, _names[i]);
    }
  } else if (line == 2) { // dashed lines
    char dashes[max_width + 1];
    memset(dashes, '-', w);
    dashes[w] = '\0';
    stream->print("%s", dashes);
    for (unsigned int i = 1; i < last_stat_id; ++i) {
      stream->print(" %s", dashes);
    }
  }
}

void TaskQueueStats::print(outputStream* stream, unsigned int width) const
{
  stream->print("%*zu", width, _stats[0]);
  for (unsigned int i = 1; i < last_stat_id; ++i) {
    stream->print(" %*zu", width, _stats[i]);
  }
  #undef FMT
}

#ifdef ASSERT
// Invariants which should hold after a TaskQueue has been emptied and is
// quiescent; they do not hold at arbitrary times.
void TaskQueueStats::verify() const
{
  assert(get(push) == get(pop) + get(steal_success),
         "push=%zu pop=%zu steal=%zu",
         get(push), get(pop), get(steal_success));
  assert(get(pop_slow) <= get(pop),
         "pop_slow=%zu pop=%zu",
         get(pop_slow), get(pop));
  assert(get(steal_empty) <= get(steal_attempt),
         "steal_empty=%zu steal_attempt=%zu",
         get(steal_empty), get(steal_attempt));
  assert(get(steal_contended) <= get(steal_attempt),
         "steal_contended=%zu steal_attempt=%zu",
         get(steal_contended), get(steal_attempt));
  assert(get(steal_success) <= get(steal_attempt),
         "steal_success=%zu steal_attempt=%zu",
         get(steal_success), get(steal_attempt));
  assert(get(steal_empty) + get(steal_contended) + get(steal_success) == get(steal_attempt),
         "steal_empty=%zu steal_contended=%zu steal_success=%zu steal_attempt=%zu",
         get(steal_empty), get(steal_contended), get(steal_success), get(steal_attempt));
  assert(get(overflow) == 0 || get(push) != 0,
         "overflow=%zu push=%zu",
         get(overflow), get(push));
  assert(get(overflow_max_len) == 0 || get(overflow) != 0,
         "overflow_max_len=%zu overflow=%zu",
         get(overflow_max_len), get(overflow));
}
#endif // ASSERT
#endif // TASKQUEUE_STATS

#ifdef ASSERT
bool ObjArrayTask::is_valid() const {
  return _obj != nullptr && _obj->is_objArray() && _index >= 0 &&
      _index < objArrayOop(_obj)->length();
}
#endif // ASSERT
